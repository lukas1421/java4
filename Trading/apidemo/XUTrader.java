package apidemo;

import TradeType.FutureTrade;
import TradeType.MAIdea;
import TradeType.TradeBlock;
import auxiliary.SimpleBar;
import client.*;
import controller.ApiConnection;
import controller.ApiController;
import graph.DisplayGranularity;
import graph.GraphXuTrader;
import handler.HistoricalHandler;
import handler.InventoryOrderHandler;
import handler.XUOvernightTradeExecHandler;
import sound.EmbeddedSoundPlayer;
import util.AutoOrderType;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static apidemo.TradingConstants.FUT_COLLECTION_TIME;
import static apidemo.TradingConstants.ftseIndex;
import static apidemo.XuTraderHelper.*;
import static java.lang.System.out;
import static util.AutoOrderType.*;
import static utility.Utility.*;

public final class XUTrader extends JPanel implements HistoricalHandler, ApiController.IDeepMktDataHandler,
        ApiController.ITradeReportHandler, ApiController.IOrderHandler, ApiController.ILiveOrderHandler
        , ApiController.IPositionHandler, ApiController.IConnectionHandler {

    static volatile Set<String> uniqueTradeKeySet = new HashSet<>();
    static ApiController apcon;
    static XUTraderRoll traderRoll;

    //global
    private static AtomicBoolean musicOn = new AtomicBoolean(false);
    private static volatile MASentiment sentiment = MASentiment.Directionless;
    private static LocalDateTime lastTradeTime = LocalDateTime.now();
    static final int MAX_FUT_LIMIT = 20;
    static volatile AtomicBoolean canLongGlobal = new AtomicBoolean(true);
    static volatile AtomicBoolean canShortGlobal = new AtomicBoolean(true);
    public static volatile AtomicInteger autoTradeID = new AtomicInteger(100);
    public static volatile NavigableMap<Integer, OrderAugmented> globalIdOrderMap = new ConcurrentSkipListMap<>();
    private static final int UP_PERC = 90;
    private static final int DOWN_PERC = 10;
    //flatten drift trader
    private static final double FLATTEN_THRESH = 200000.0;
    private static final double DELTA_HIGH_LIMIT = 300000.0;
    private static final double DELTA_LOW_LIMIT = -300000.0;

    private static final double ABS_DELTA_TARGET = 100000.0;
    private static final double BULLISH_DELTA_TARGET = 200000.0;
    private static final double BEARISH_DELTA_TARGET = -100000.0;
    public static volatile Eagerness flattenEagerness = Eagerness.Passive;


    //perc trader
    private static volatile AtomicBoolean percentileTradeOn = new AtomicBoolean(false);
    private static final int MAX_PERC_TRADES = 5;

    //inventory market making
    private static volatile Semaphore inventorySemaphore = new Semaphore(1);
    private static volatile CyclicBarrier inventoryBarrier = new CyclicBarrier(2, () -> {
        outputToAutoLog(str(LocalTime.now(), "inventory barrier reached 2",
                "Trading Cycle Ends"));
    });
    private static final double margin = 2.5;
    private static final int INV_TRADE_QUANTITY = 1;
    private static AtomicBoolean inventoryTraderOn = new AtomicBoolean(false);

    //pd market making
    private static volatile Semaphore pdSemaphore = new Semaphore(2);
    private static volatile CyclicBarrier pdBarrier = new CyclicBarrier(2, () -> {
        outputToAutoLog(str(LocalTime.now(), " PD barrier reached 2, Trading cycle ends"));
    });
    private static final int PD_ORDER_QUANTITY = 1;
    private static AtomicBoolean pdTraderOn = new AtomicBoolean(false);

    //overnight trades
    private static final AtomicBoolean overnightTradeOn = new AtomicBoolean(true);
    private static final int maxOvernightTrades = 10;
    private static AtomicInteger overnightClosingOrders = new AtomicInteger(0);
    private static AtomicInteger overnightTradesDone = new AtomicInteger(0);
    private static final double maxOvernightDeltaChgUSD = 50000.0;
    private static final double OVERNIGHT_MAX_DELTA = 500000.0;
    private static final double OVERNIGHT_MIN_DELTA = -500000.0;

    //ma
    private static AtomicBoolean MATraderStatus = new AtomicBoolean(true);
    private static volatile int currentMAPeriod = 60;
    private static Direction currentDirection = Direction.Flat;
    private static final int DEFAULT_SIZE = 1;
    private static LocalDateTime lastMATradeTime = LocalDateTime.now();
    private static LocalDateTime lastMAOrderTime = sessionOpenT();
    private static final int MAX_MA_SIGNALS_PER_SESSION = 10;
    private static AtomicInteger maSignals = new AtomicInteger(0); //session transient
    private static volatile NavigableMap<LocalDateTime, Order> maOrderMap = new ConcurrentSkipListMap<>();
    private static volatile TreeSet<MAIdea> maIdeasSet = new TreeSet<>(Comparator.comparing(MAIdea::getIdeaTime));
    private static volatile long timeBtwnMAOrders = 5;
    private static final double PD_UP_THRESH = 0.003;
    private static final double PD_DOWN_THRESH = -0.003;

    //open/fast trading
    static LocalDateTime lastOpenTradeTime = LocalDateTime.now();
    private static LocalDateTime lastFastOrderTime = LocalDateTime.now();
    private static AtomicInteger fastTradeSignals = new AtomicInteger(0);
    private static NavigableMap<LocalDateTime, Order> fastOrderMap = new ConcurrentSkipListMap<>();
    private static final long MAX_OPEN_TRADE_ORDERS = 10;

    //direction makeup trade
    private static final int MIN_LAST_IDEA_LAPSE_TIME = 5;

    //music
    private EmbeddedSoundPlayer soundPlayer = new EmbeddedSoundPlayer();

    //detailed MA
    private static AtomicBoolean detailedPrint = new AtomicBoolean(false);


    //display
    public static volatile Predicate<LocalDateTime> displayPred = e -> true;

    private final static Contract frontFut = utility.Utility.getFrontFutContract();
    private final static Contract backFut = utility.Utility.getBackFutContract();

    @SuppressWarnings("unused")
    private static Predicate<? super Map.Entry<FutType, ?>> graphPred = e -> true;
    public static volatile Contract activeFuture = gettingActiveContract();

    public static volatile DisplayGranularity gran = DisplayGranularity._5MDATA;
    private static volatile Map<Double, Double> activeFutLiveOrder = new HashMap<>();
    public static volatile Map<Integer, Order> activeFutLiveIDOrderMap = new HashMap<>();
    public static volatile EnumMap<FutType, Double> bidMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Double> askMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Double> futPriceMap = new EnumMap<>(FutType.class);
    private static volatile NavigableMap<LocalDateTime, Double> activeLastMinuteMap = new ConcurrentSkipListMap<>();
    private static EnumMap<FutType, Double> futOpenMap = new EnumMap<>(FutType.class);
    public static EnumMap<FutType, Double> futPrevCloseMap = new EnumMap<>(FutType.class);

    private static JTextArea outputArea = new JTextArea(20, 1);
    private static List<JLabel> bidLabelList = new ArrayList<>();
    private static List<JLabel> askLabelList = new ArrayList<>();
    private static Map<String, Double> bidPriceList = new HashMap<>();
    private static Map<String, Double> offerPriceList = new HashMap<>();
    private ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);
    private ScheduledExecutorService ses2 = Executors.newScheduledThreadPool(10);
    public static EnumMap<FutType, NavigableMap<LocalDateTime, TradeBlock>> tradesMap = new EnumMap<>(FutType.class);
    public static EnumMap<FutType, NavigableMap<LocalDateTime, TradeBlock>> overnightTradesMap = new EnumMap<>(FutType.class);

    private GraphXuTrader xuGraph = new GraphXuTrader() {
        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = 250;
            d.width = (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth();
            return d;
        }
    };
    public static AtomicInteger graphWidth = new AtomicInteger(3);
    public static volatile EnumMap<FutType, NavigableMap<LocalDateTime, SimpleBar>> futData = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> currentPosMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> botMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> soldMap = new EnumMap<>(FutType.class);

    public static volatile boolean showTrades = false;
    static volatile boolean connectionStatus = false;
    static volatile JLabel connectionLabel = new JLabel();

    public static void updateLastMinuteMap(LocalDateTime ldt, double freshPrice) {
        activeLastMinuteMap.entrySet().removeIf(e -> e.getKey().isBefore(ldt.minusMinutes(3)));
        activeLastMinuteMap.put(ldt, freshPrice);

        if (activeLastMinuteMap.size() > 1) {
            double lastV = activeLastMinuteMap.lastEntry().getValue();
            double secLastV = activeLastMinuteMap.lowerEntry(activeLastMinuteMap.lastKey()).getValue();
            long milliLapsed = ChronoUnit.MILLIS.between(activeLastMinuteMap.lowerKey(activeLastMinuteMap.lastKey()),
                    activeLastMinuteMap.lastKey());

        } else {
            out.println(str(" last minute map ", activeLastMinuteMap));
        }
    }

    private static double getBullishTarget() {
        double target;
        if (futureAMSession().test(LocalTime.now())) {
            target = BULLISH_DELTA_TARGET / 2;
        } else {
            target = (sentiment == MASentiment.Bullish ? BULLISH_DELTA_TARGET : BULLISH_DELTA_TARGET / 2);
        }
        if ((LocalDate.now().getDayOfWeek() == DayOfWeek.FRIDAY)) {
            return target / 2;
        } else if (LocalDate.now().getDayOfWeek() == DayOfWeek.TUESDAY) {
            return target * 2;
        }
        return target;

    }

    private static double getBearishTarget() {
        double target;
        if (futurePMSession().test(LocalTime.now())) {
            target = BULLISH_DELTA_TARGET / 4;
        } else {
            target = sentiment == MASentiment.Bearish ? BEARISH_DELTA_TARGET : 0.0;
        }
        if (LocalDate.now().getDayOfWeek() == DayOfWeek.FRIDAY) {
            return target / 2;
        } else if (LocalDate.now().getDayOfWeek() == DayOfWeek.TUESDAY) {
            return Math.max(0.0, target * 2);
        }
        return target;
    }

    private static double getDeltaHighLimit() {
        double limit;
        if (futureAMSession().test(LocalTime.now())) {
            limit = DELTA_HIGH_LIMIT / 2;
        } else {
            limit = sentiment == MASentiment.Bullish ? DELTA_HIGH_LIMIT : 0.0;
        }
        if (LocalDate.now().getDayOfWeek() == DayOfWeek.FRIDAY) {
            return limit / 2;
        } else if (LocalDate.now().getDayOfWeek() == DayOfWeek.TUESDAY) {
            return limit * 2;
        }
        return limit;
    }

    private static double getDeltaLowLimit() {
        double limit;
        if (futurePMSession().test(LocalTime.now())) {
            limit = 0;
        } else {
            limit = sentiment == MASentiment.Bearish ? DELTA_LOW_LIMIT : 0.0;
        }
        if (LocalDate.now().getDayOfWeek() == DayOfWeek.FRIDAY) {
            return limit / 2;
        } else if (LocalDate.now().getDayOfWeek() == DayOfWeek.TUESDAY) {
            return Math.max(0.0, limit * 2);
        }
        return limit;
    }

    private static void maTradeAnalysis() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sessionBeginLDT = sessionOpenT();
        maIdeasSet = new TreeSet<>(Comparator.comparing(MAIdea::getIdeaTime));

        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        if (price5.size() <= 1) return;

        NavigableMap<LocalDateTime, Double> sma = XuTraderHelper.getMAGen(price5, currentMAPeriod);
        double lastPrice = price5.lastEntry().getValue().getClose();
        double pd = getPD(lastPrice);

        sma.forEach((lt, ma) -> {
            if (lt.isAfter(sessionBeginLDT) && !lt.equals(price5.firstKey()) && price5.containsKey(lt)) {
                SimpleBar sb = price5.get(lt);
                SimpleBar sbPrevious = price5.lowerEntry(lt).getValue();
                if (bullishTouchMet(sbPrevious, sb, ma)) {
                    maIdeasSet.add(new MAIdea(lt, ma, +1,
                            str("prev, last, ma ", sbPrevious.getOpen(), sbPrevious.getClose(), sb.getOpen(), r(ma))));
                } else if (bearishTouchMet(sbPrevious, sb, ma)) {
                    maIdeasSet.add(new MAIdea(lt, ma, -1,
                            str("prev, last, ma ", sbPrevious.getOpen(), sbPrevious.getClose(), sb.getOpen(), r(ma))));
                }
            }
        });

        // make up for the last MA trade wihch was missing due to in restriction period
        if (maIdeasSet.size() > 0) {
            if (maIdeasSet.last().getIdeaTime().isAfter(lastMAOrderTime)
                    && now.isAfter(lastMAOrderTime.plusMinutes(timeBtwnMAOrders))
                    && maIdeasSet.last().getIdeaTime().isAfter(lastTradeTime)) {

                MAIdea lastIdea = maIdeasSet.last();
                outputToAutoLog(str(now, "MAKE UP TRADE ", lastIdea));
                if (lastIdea.getIdeaSize() > 0 && canLongGlobal.get() && pd < PD_UP_THRESH) {
                    Order o = placeBidLimit(roundToXUPricePassive(lastIdea.getIdeaPrice(), Direction.Long), 1);
                    //apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler());
//                    outputOrderToAutoLog(str(now, " MakeUp BUY || BIDDING @ ", o.toString(), "SMA",
//                            lastIdea));
                    //maOrderMap.put(now, o);
                    //maSignals.incrementAndGet();
                    //lastMAOrderTime = now;
                } else if (lastIdea.getIdeaSize() < 0 && canShortGlobal.get() && pd > PD_DOWN_THRESH) {
                    Order o = placeOfferLimit(roundToXUPricePassive(lastIdea.getIdeaPrice(), Direction.Short), 1);
                    //apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler());
//                    outputOrderToAutoLog(str(now, " MakeUp SELL || OFFERING @ ", o.toString(), "SMA", lastIdea));
                    //maOrderMap.put(now, o);
                    //maSignals.incrementAndGet();
                    //lastMAOrderTime = now;
                }
            }
        }
    }

    public XUTrader getThis() {
        return this;
    }

    XUTrader(ApiController ap) {
        out.println(str(" ****** front fut ******* ", frontFut));
        out.println(str(" ****** back fut ******* ", backFut));


        for (FutType f : FutType.values()) {
            futData.put(f, new ConcurrentSkipListMap<>());
            tradesMap.put(f, new ConcurrentSkipListMap<>());
            overnightTradesMap.put(f, new ConcurrentSkipListMap<>());
            futOpenMap.put(f, 0.0);
            futPrevCloseMap.put(f, 0.0);
        }

        apcon = ap;
        traderRoll = new XUTraderRoll(ap);

        JLabel currTimeLabel = new JLabel(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        currTimeLabel.setFont(currTimeLabel.getFont().deriveFont(30F));

        JButton bidLimitButton = new JButton("Buy Limit");

        bidLimitButton.addActionListener(l -> {
            out.println(" buying limit ");
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimit(bidMap.get(ibContractToFutType(activeFuture)), 1.0);
            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o, AutoOrderType.ON_BID));
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), " Bidding Limit ", globalIdOrderMap.get(id)));
        });

        JButton offerLimitButton = new JButton("Sell Limit");

        offerLimitButton.addActionListener(l -> {
            pr(" selling limit ");
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimit(askMap.get(ibContractToFutType(activeFuture)), 1.0);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o, "offer limit", AutoOrderType.ON_OFFER));
            outputOrderToAutoLog(str(o.orderId(), " Offer Limit ", globalIdOrderMap.get(id)));
        });

        JButton buyOfferButton = new JButton(" Buy Now");
        buyOfferButton.addActionListener(l -> {
            out.println(" buy offer ");
            int id = autoTradeID.incrementAndGet();
            Order o = buyAtOffer(askMap.get(ibContractToFutType(activeFuture)), 1.0);
            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o, "lift offer",
                    AutoOrderType.LIFT_OFFER));
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), " Lift Offer ", globalIdOrderMap.get(id)));
        });

        JButton sellBidButton = new JButton(" Sell Now");
        sellBidButton.addActionListener(l -> {
            pr(" sell bid ");
            int id = autoTradeID.incrementAndGet();
            Order o = sellAtBid(bidMap.get(ibContractToFutType(activeFuture)), 1.0);
            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o, "hit bid", AutoOrderType.HIT_BID));
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), " Hitting bid ", globalIdOrderMap.get(id)));
        });

        JButton toggleMusicButton = new JButton("停乐");
        toggleMusicButton.addActionListener(l -> soundPlayer.stopIfPlaying());

        JButton detailedButton = new JButton("Detailed:" + detailedPrint.get());
        detailedButton.addActionListener(l -> {
            detailedPrint.set(!detailedPrint.get());
            detailedButton.setText(" Detailed: " + detailedPrint.get());
        });

        JButton maTraderStatusButton = new JButton("MA Trader: " + (MATraderStatus.get() ? "ON" : "OFF"));
        maTraderStatusButton.addActionListener(l -> {
            MATraderStatus.set(!MATraderStatus.get());
            outputToAutoLog(" MA Trade set to " + MATraderStatus.get());
            maTraderStatusButton.setText("MA Trader " + (MATraderStatus.get() ? "ON" : "OFF"));
        });

        JButton musicPlayableButton = new JButton("Music: " + (musicOn.get() ? "ON" : "OFF"));
        musicPlayableButton.addActionListener(l -> {
            musicOn.set(!musicOn.get());
            musicPlayableButton.setText("Music:" + (musicOn.get() ? "ON" : "OFF"));
        });

        JButton inventoryTraderButton = new JButton("Inv Trader: " + (inventoryTraderOn.get() ? "ON" : "OFF"));
        inventoryTraderButton.addActionListener(l -> {
            inventoryTraderOn.set(!inventoryTraderOn.get());
            outputToAutoLog(" inv trader set to " + inventoryTraderOn.get());
            inventoryTraderButton.setText("Inv Trader: " + (inventoryTraderOn.get() ? "ON" : "OFF"));
        });

        JButton percTraderButton = new JButton("Perc Trader: " + (percentileTradeOn.get() ? "ON" : "OFF"));
        percTraderButton.addActionListener(l -> {
            percentileTradeOn.set(!percentileTradeOn.get());
            outputToAutoLog(" percentile trader set to " + percentileTradeOn.get());
            percTraderButton.setText("Perc Trader: " + (percentileTradeOn.get() ? "ON" : "OFF"));
        });

        JButton pdTraderButton = new JButton("PD Trader: " + (pdTraderOn.get() ? "ON" : "OFF"));
        pdTraderButton.addActionListener(l -> {
            pdTraderOn.set(!pdTraderOn.get());
            outputToAutoLog(" PD Trader set to " + pdTraderOn.get());
            pdTraderButton.setText("PD Trader: " + (pdTraderOn.get() ? "ON" : "OFF"));
        });

        JButton rollButton = new JButton("Roll");
        rollButton.addActionListener(l -> {
            CompletableFuture.runAsync(() -> {
                XUTraderRoll.resetLatch();
                XUTraderRoll.getContractDetails();
                try {
                    XUTraderRoll.latch.await();
                    pr("xu trader roll wait finished, short rolling ", LocalTime.now());
                    traderRoll.shortRoll(0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        });

        JButton getPositionButton = new JButton(" get pos ");
        getPositionButton.addActionListener(l -> apcon.reqPositions(this));

        JButton level2Button = new JButton("level2");
        level2Button.addActionListener(l -> requestLevel2Data());

        JButton refreshButton = new JButton("Refresh");

        refreshButton.addActionListener(l -> {
            String time = (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).getSecond() != 0)
                    ? (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()) :
                    (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString() + ":00");

            currTimeLabel.setText(time);
            xuGraph.fillInGraph(futData.get(ibContractToFutType(activeFuture)));
            xuGraph.fillTradesMap(XUTrader.tradesMap.get(ibContractToFutType(activeFuture)));
            xuGraph.setName(ibContractToSymbol(activeFuture));
            xuGraph.setFut(ibContractToFutType(activeFuture));
            xuGraph.setPrevClose(futPrevCloseMap.get(ibContractToFutType(activeFuture)));
            xuGraph.refresh();
            apcon.reqPositions(getThis());
            repaint();
        });

        JButton computeButton = new JButton("Compute");
        computeButton.addActionListener(l -> {
            try {
                getAPICon().reqXUDataArray();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (ses.isShutdown()) {
                ses = Executors.newScheduledThreadPool(10);
            }

            ses.scheduleAtFixedRate(() -> {
                LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
                String time = now.toString() + ((now.getSecond() != 0) ? "" : ":00");

                apcon.reqPositions(getThis());
                activeFutLiveOrder = new HashMap<>();
                activeFutLiveIDOrderMap = new HashMap<>();

                //getting all live orders
                apcon.reqLiveOrders(getThis());

                SwingUtilities.invokeLater(() -> {
                    currTimeLabel.setText(time);
                    xuGraph.fillInGraph(futData.get(ibContractToFutType(activeFuture)));
                    xuGraph.fillTradesMap(tradesMap.get(ibContractToFutType(activeFuture)));
                    xuGraph.setName(ibContractToSymbol(activeFuture));
                    xuGraph.setFut(ibContractToFutType(activeFuture));
                    xuGraph.setPrevClose(futPrevCloseMap.get(ibContractToFutType(activeFuture)));
                    xuGraph.refresh();
                    repaint();
                });
            }, 0, 1, TimeUnit.SECONDS);

            ses.scheduleAtFixedRate(() -> {
                observeMATouch();
                maTradeAnalysis();
                requestExecHistory();
            }, 0, 1, TimeUnit.MINUTES);

            ses.scheduleAtFixedRate(() -> {
                out.println(" printing all inventory orders ");

                globalIdOrderMap.entrySet().stream().filter(e -> isInventoryTrade().test(e.getValue().getTradeType()))
                        .forEach(e -> pr(str("real order ID", e.getValue().getOrder().orderId(), e.getValue())));

                long invOrderCount = globalIdOrderMap.entrySet().stream()
                        .filter(e -> isInventoryTrade().test(e.getValue().getTradeType())).count();

                outputToAutoLog(" inventory orders count " + invOrderCount);

                if (invOrderCount >= 1) {
                    OrderAugmented o = globalIdOrderMap.entrySet().stream()
                            .filter(e -> isInventoryTrade().test(e.getValue().getTradeType()))
                            .max(Comparator.comparing(e -> e.getValue().getOrderTime())).map(Map.Entry::getValue)
                            .orElseThrow(() -> new IllegalStateException(" nothing in last inventory order "));

                    pr("invOrderCount >=1 : last order ", o);
                    pr("last order T ", o.getOrderTime(), "status", o.getStatus()
                            , " Cancel wait time ", cancelWaitTime(LocalTime.now()));

                    if (o.getStatus() != OrderStatus.Filled &&
                            timeDiffinMinutes(o.getOrderTime(), LocalDateTime.now()) >= cancelWaitTime(LocalTime.now())) {

                        globalIdOrderMap.entrySet().stream()
                                .filter(e -> isInventoryTrade().test(e.getValue().getTradeType()))
                                .skip(invOrderCount - 1).peek(e -> out.println(str("last order ", e.getValue())))
                                .forEach(e -> {
                                    if (e.getValue().getStatus() != OrderStatus.Cancelled) {
                                        apcon.cancelOrder(e.getValue().getOrder().orderId());
                                        e.getValue().setFinalActionTime(LocalDateTime.now());
                                        e.getValue().setStatus(OrderStatus.Cancelled);
                                    } else {
                                        out.println(str(e.getValue().getOrder().orderId(), "already cancelled"));
                                    }
                                });
                        pr(str(LocalTime.now(), " killing last unfilled orders"));
                        pr(" releasing inv barrier + semaphore ");
                        inventoryBarrier.reset(); //resetting inventory barrier
                        inventorySemaphore = new Semaphore(1); // release inv semaphore
                    }
                }
            }, 0, 1, TimeUnit.MINUTES);
        });

        JButton stopComputeButton = new JButton("Stop Processing");
        stopComputeButton.addActionListener(l -> ses2.shutdown());

        JButton execButton = new JButton("Exec");
        execButton.addActionListener(l -> requestExecHistory());

        JButton processTradesButton = new JButton("Process");
        processTradesButton.addActionListener(l -> ses2.scheduleAtFixedRate(() -> {
            SwingUtilities.invokeLater(() -> {
                XUTrader.clearLog();
                XUTrader.updateLog("**************************************************************");
            });
            XUTrader.processTradeMapActive();
        }, 0, 10, TimeUnit.SECONDS));

        JButton overnightButton = new JButton("Overnight: " + (overnightTradeOn.get() ? "ON" : "OFF"));
        overnightButton.addActionListener(l -> {
            overnightTradeOn.set(!overnightTradeOn.get());
            overnightButton.setText("Overnight: " + (overnightTradeOn.get() ? "ON" : "OFF"));
            ses2.scheduleAtFixedRate(this::overnightTrader, 0, 1, TimeUnit.MINUTES);
        });

        JButton maAnalysisButton = new JButton(" MA Analysis ");
        maAnalysisButton.addActionListener(l -> maTradeAnalysis());

        JButton getData = new JButton("Data");
        getData.addActionListener(l -> loadXU());

        JButton graphButton = new JButton("graph");
        graphButton.addActionListener(l -> {
            xuGraph.setNavigableMap(futData.get(ibContractToFutType(activeFuture)), displayPred);
            xuGraph.refresh();
            repaint();
        });

        JToggleButton showTodayOnly = new JToggleButton(" Today Only ");
        showTodayOnly.addActionListener(l -> {
            if (showTodayOnly.isSelected()) {
                displayPred = e -> e.toLocalDate().equals(LocalDate.now())
                        && e.toLocalTime().isAfter(LocalTime.of(8, 59));
            } else {
                displayPred = e -> true;
            }
        });

        JToggleButton showGraphButton = new JToggleButton("Show Trades");
        showGraphButton.addActionListener(l -> showTrades = showGraphButton.isSelected());

        JButton cancelAllOrdersButton = new JButton("Cancel Orders");
        cancelAllOrdersButton.addActionListener(l -> {
            apcon.cancelAllOrders();
            inventorySemaphore = new Semaphore(1);
            inventoryBarrier.reset();
            pdSemaphore = new Semaphore(1);
            pdBarrier.reset();
            activeFutLiveOrder = new HashMap<>();
            activeFutLiveIDOrderMap = new HashMap<>();
            globalIdOrderMap.entrySet().stream().filter(e -> isInventoryTrade().test(e.getValue().getTradeType()))
                    .filter(e -> e.getValue().getStatus() != OrderStatus.Filled)
                    .forEach(e -> {
                        pr("cancelling ", e.getValue());
                        e.getValue().setFinalActionTime(LocalDateTime.now());
                        e.getValue().setStatus(OrderStatus.Cancelled);
                    });
            SwingUtilities.invokeLater(xuGraph::repaint);
        });

        JButton reqLiveOrdersButton = new JButton(" Live Orders ");
        reqLiveOrdersButton.addActionListener(l -> {
            activeFutLiveOrder = new HashMap<>();
            apcon.reqLiveOrders(getThis());
        });

        xuGraph.setAutoscrolls(false);
        JScrollPane chartScroll = new JScrollPane(xuGraph, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1200;
                return d;
            }
        };

        JRadioButton frontFutButton = new JRadioButton("Front");
        frontFutButton.addActionListener(l -> {
            graphPred = e -> e.getKey().equals(FutType.FrontFut);
            activeFuture = frontFut;
        });
        frontFutButton.setSelected(activeFuture.lastTradeDateOrContractMonth().equalsIgnoreCase(
                TradingConstants.A50_FRONT_EXPIRY));

        JRadioButton backFutButton = new JRadioButton("Back");
        backFutButton.addActionListener(l -> {
            graphPred = e -> e.getKey().equals(FutType.BackFut);
            activeFuture = backFut;
        });

        backFutButton.setSelected(activeFuture.lastTradeDateOrContractMonth().equalsIgnoreCase(
                TradingConstants.A50_BACK_EXPIRY));

        JRadioButton _1mButton = new JRadioButton("1m");
        _1mButton.addActionListener(l -> gran = DisplayGranularity._1MDATA);

        JRadioButton _5mButton = new JRadioButton("5m");
        _5mButton.addActionListener(l -> gran = DisplayGranularity._5MDATA);
        _5mButton.setSelected(true);

        ButtonGroup frontBackGroup = new ButtonGroup();
        frontBackGroup.add(frontFutButton);
        frontBackGroup.add(backFutButton);

        ButtonGroup dispGranGroup = new ButtonGroup();
        dispGranGroup.add(_1mButton);
        dispGranGroup.add(_5mButton);

        JLabel widthLabel = new JLabel("Width");
        widthLabel.setOpaque(true);
        widthLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        widthLabel.setFont(widthLabel.getFont().deriveFont(15F));

        JButton graphWidthUp = new JButton("UP ");
        graphWidthUp.addActionListener(l -> {
            graphWidth.incrementAndGet();
            SwingUtilities.invokeLater(xuGraph::refresh);
        });

        JButton graphWidthDown = new JButton("Down ");
        graphWidthDown.addActionListener(l -> {
            graphWidth.set(Math.max(1, graphWidth.decrementAndGet()));
            SwingUtilities.invokeLater(xuGraph::refresh);
        });

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        JPanel controlPanel1 = new JPanel();
        JPanel controlPanel2 = new JPanel();
        controlPanel1.add(currTimeLabel);
        controlPanel1.add(bidLimitButton);
        controlPanel1.add(offerLimitButton);
        controlPanel1.add(buyOfferButton);
        controlPanel1.add(sellBidButton);
        controlPanel1.add(toggleMusicButton);
        controlPanel1.add(detailedButton);
        controlPanel1.add(maTraderStatusButton);
        controlPanel1.add(overnightButton);
        controlPanel1.add(musicPlayableButton);
        controlPanel1.add(inventoryTraderButton);
        controlPanel1.add(percTraderButton);
        controlPanel1.add(pdTraderButton);
        controlPanel1.add(rollButton);

        controlPanel2.add(getPositionButton);
        controlPanel2.add(level2Button);
        controlPanel2.add(refreshButton);
        controlPanel2.add(computeButton);
        controlPanel2.add(execButton);
        controlPanel2.add(processTradesButton);
        controlPanel2.add(getData);
        controlPanel2.add(graphButton);
        controlPanel2.add(showGraphButton);
        controlPanel2.add(showTodayOnly);
        controlPanel2.add(connectionLabel);
        controlPanel2.add(cancelAllOrdersButton);
        controlPanel2.add(reqLiveOrdersButton);

        controlPanel2.add(frontFutButton);
        controlPanel2.add(backFutButton);
        controlPanel2.add(_1mButton);
        controlPanel2.add(_5mButton);
        controlPanel2.add(widthLabel);
        controlPanel2.add(graphWidthUp);
        controlPanel2.add(graphWidthDown);
        controlPanel2.add(stopComputeButton);
        controlPanel2.add(maAnalysisButton);

        JLabel bid1 = new JLabel("1");
        bidLabelList.add(bid1);
        bid1.setName("bid1");
        JLabel bid2 = new JLabel("2");
        bidLabelList.add(bid2);
        bid2.setName("bid2");
        JLabel bid3 = new JLabel("3");
        bidLabelList.add(bid3);
        bid3.setName("bid3");
        JLabel bid4 = new JLabel("4");
        bidLabelList.add(bid4);
        bid4.setName("bid4");
        JLabel bid5 = new JLabel("5");
        bidLabelList.add(bid5);
        bid5.setName("bid5");
        JLabel ask1 = new JLabel("1");
        askLabelList.add(ask1);
        ask1.setName("ask1");
        JLabel ask2 = new JLabel("2");
        askLabelList.add(ask2);
        ask2.setName("ask2");
        JLabel ask3 = new JLabel("3");
        askLabelList.add(ask3);
        ask3.setName("ask3");
        JLabel ask4 = new JLabel("4");
        askLabelList.add(ask4);
        ask4.setName("ask4");
        JLabel ask5 = new JLabel("5");
        askLabelList.add(ask5);
        ask5.setName("ask5");

        bidLabelList.forEach(l -> {
            l.setOpaque(true);
            l.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            l.setFont(l.getFont().deriveFont(30F));
            l.setHorizontalAlignment(SwingConstants.CENTER);
            l.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && !e.isConsumed()) {
                        double bidPrice = bidPriceList.getOrDefault(l.getName(), 0.0);

                        if (checkIfOrderPriceMakeSense(bidPrice) && futMarketOpen(LocalTime.now())) {
                            int id = autoTradeID.incrementAndGet();
                            Order o = placeBidLimit(bidPrice, 1.0);
                            apcon.placeOrModifyOrder(activeFuture, o, getThis());
                            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o,
                                    AutoOrderType.ON_BID));
                            outputOrderToAutoLog(str(o.orderId(), " MANUAL BID || bid price ", bidPrice,
                                    " Checking order ", checkIfOrderPriceMakeSense(bidPrice)));
                        } else {
                            throw new IllegalArgumentException("price out of bound");
                        }
                    }
                }
            });
        });

        askLabelList.forEach(l -> {
            l.setOpaque(true);
            l.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            l.setFont(l.getFont().deriveFont(30F));
            l.setHorizontalAlignment(SwingConstants.CENTER);
            l.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && !e.isConsumed()) {
                        double offerPrice = offerPriceList.get(l.getName());

                        if (checkIfOrderPriceMakeSense(offerPrice) && futMarketOpen(LocalTime.now())) {
                            int id = autoTradeID.incrementAndGet();
                            Order o = placeOfferLimit(offerPrice, 1.0);
                            apcon.placeOrModifyOrder(activeFuture, o, getThis());
                            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o,
                                    AutoOrderType.ON_OFFER));
                            outputOrderToAutoLog(str(o.orderId(), " MANUAL OFFER||offer price "
                                    , offerPrice, " Checking order ", checkIfOrderPriceMakeSense(offerPrice)));
                        } else {
                            throw new IllegalArgumentException("price out of bound");
                        }
                    }
                }
            });
        });

        JPanel deepPanel = new JPanel();
        deepPanel.setLayout(new GridLayout(5, 2));

        for (JLabel j : Arrays.asList(bid1, ask1, bid2, ask2, bid3, ask3, bid4, ask4, bid5, ask5)) {
            deepPanel.add(j);
        }
        JScrollPane outputPanel = new JScrollPane(outputArea);
        controlPanel1.setLayout(new FlowLayout());
        add(controlPanel1);
        add(controlPanel2);
        add(deepPanel);
        add(outputPanel);
        add(chartScroll);
    }

    private static Contract gettingActiveContract() {
        long daysUntilFrontExp = ChronoUnit.DAYS.between(LocalDate.now(),
                LocalDate.parse(TradingConstants.A50_FRONT_EXPIRY, DateTimeFormatter.ofPattern("yyyyMMdd")));

        pr(" **********  days until expiry **********", daysUntilFrontExp);
        if (daysUntilFrontExp <= 1) {
            pr(" using back fut ");
            return backFut;
        } else {
            pr(" using front fut ");
            return frontFut;
        }
    }

    static double getExpiringDelta() {
        return currentPosMap.entrySet().stream()
                .mapToDouble(e -> {
                    if (e.getKey() == FutType.FrontFut &&
                            LocalDate.parse(TradingConstants.A50_FRONT_EXPIRY, DateTimeFormatter.ofPattern("yyyyMMdd"))
                                    .equals(LocalDate.now())) {
                        return e.getValue() * futPriceMap.getOrDefault(e.getKey(), SinaStock.FTSE_OPEN)
                                * ChinaPosition.fxMap.getOrDefault(e.getKey().getTicker(), 1.0);
                    } else {
                        return 0.0;
                    }
                }).sum();
    }

    static double getFutDelta() {
        return currentPosMap.entrySet().stream()
                .mapToDouble(e -> {
                    if (e.getKey() == FutType.FrontFut &&
                            LocalDate.parse(TradingConstants.A50_FRONT_EXPIRY, DateTimeFormatter.ofPattern("yyyyMMdd"))
                                    .equals(LocalDate.now())
                            && LocalTime.now().isAfter(LocalTime.of(15, 0))) {
                        return 0.0;
                    }
                    return e.getValue() * futPriceMap.getOrDefault(e.getKey(), SinaStock.FTSE_OPEN)
                            * ChinaPosition.fxMap.getOrDefault(e.getKey().getTicker(), 1.0);
                }).sum();
    }

    private static int cancelWaitTime(LocalTime t) {
        if (futureAMSession().test(t) || futurePMSession().test(t)) {
            return 10;
        } else {
            return 60;
        }
    }

    public static OrderAugmented findOrderByTWSID(int twsId) {
        for (Map.Entry<Integer, OrderAugmented> e : globalIdOrderMap.entrySet()) {
            if (e.getValue().getOrder().orderId() == twsId) {
                return e.getValue();
            }
        }
        return new OrderAugmented();
    }

    /**
     * Determine order size by taking into account the trade time, PD, proposed direction and percentile
     */
    private static int determinePDPercFactor(LocalTime t, double pd, Direction dir, int perc) {
        int factor = 1;
        if (perc > UP_PERC && dir == Direction.Long) {
            return 0;
        } else if (perc < DOWN_PERC && dir == Direction.Short) {
            return 0;
        }

        if (pd > PD_UP_THRESH || perc > UP_PERC) {
            factor = dir == Direction.Long ? 1 : 2;
        } else if (pd < PD_DOWN_THRESH || perc < DOWN_PERC) {
            factor = dir == Direction.Long ? 2 : 1;
        } else {
            if (futureAMSession().test(t)) {
                factor = (dir == Direction.Long ? 1 : 2);
            } else if (futurePMSession().test(t)) {
                factor = (dir == Direction.Long ? 2 : 1);
            }
        }
        outputToAutoLog(str(" Determining Order Size ||T PERC PD DIR -> FACTOR, FINAL SIZE ", t, perc
                , r10000(pd), dir, factor, factor));

        return factor;
    }

    private static int determineTimeDiffFactor() {
        if (maOrderMap.size() == 0) return 1;
        return Math.max(1, Math.min(1,
                (int) Math.floor(timeDiffinMinutes(maOrderMap.lastEntry().getKey(),
                        LocalDateTime.now()) / 60d)));
    }

    private static Predicate<AutoOrderType> isInventoryTrade() {
        return e -> e.equals(AutoOrderType.INVENTORY_CLOSE) || e.equals(AutoOrderType.INVENTORY_OPEN);
    }

    /**
     * if touched, play music
     */
    private void observeMATouch() {
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        if (price5.size() < 2) return;
        LocalTime lastKey = price5.lastEntry().getKey().toLocalTime();
        LocalTime secLastKey = price5.lowerKey(price5.lastKey()).toLocalTime();

        SimpleBar lastBar = price5.lastEntry().getValue();
        SimpleBar secLastBar = price5.lowerEntry(price5.lastEntry().getKey()).getValue();
        NavigableMap<LocalDateTime, Double> sma;
        sma = XuTraderHelper.getMAGen(price5, 60);

        double pd = getPD(lastBar.getClose());
        int percentile = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));
        soundPlayer.stopIfPlaying();
        if (sma.size() > 0) {
            String msg = str("**Observing MA**"
                    , "||T:", LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
                    , "||MA:", r(sma.lastEntry().getValue())
                    , "||Last:", lastKey, lastBar
                    , "||2nd:", secLastKey, secLastBar
                    , "||Index:", r(getIndexPrice())
                    , "||PD:", r(pd)
                    , "||Curr Dir", currentDirection
                    , "||P%", percentile
                    , "||Last Trade T", lastMATradeTime.truncatedTo(ChronoUnit.MINUTES)
                    , "||Last Order T ", lastMAOrderTime.truncatedTo(ChronoUnit.MINUTES)
                    , "||WaitT Orders: ", timeBtwnMAOrders
                    , "||Earliest Next Order: ", lastMAOrderTime.plusMinutes(timeBtwnMAOrders)
                    , "#: ", maSignals.get()
                    , "||Orders: ", maOrderMap.toString()
                    , "|| Can LONG? ", canLongGlobal
                    , "|| Can SHORT? ", canShortGlobal);

            outputToAutoLog(msg);
            double lastMA = sma.lastEntry().getValue();
            if (touchConditionMet(secLastBar, lastBar, lastMA)) {
                outputToAutoLog(" sec last bar touched MA, playing clip ");
                if (musicOn.get()) {
                    soundPlayer.playClip();
                }
            } else {
                outputToAutoLog(" no touch ");
            }
        }
    }

    // used for flatten long or short
    private static int sizeToFlatten(double price, double fx, double currDelta) {
        int candidate = 1;
        if (currDelta > getBullishTarget()) {
            candidate = (int) Math.floor((currDelta - getBullishTarget()) / (price * fx));
        } else if (currDelta < getBearishTarget()) {
            candidate = (int) Math.floor((getBearishTarget() - currDelta) / (price * fx));
        }
        return Math.max(1, Math.min(candidate, 3));
    }

    //adjust delta here.
    private static int getPercTraderSize(double price, double fx, Direction d, double currDelta) {
        int candidate = 0;
        if (sentiment == MASentiment.Directionless || d == Direction.Flat) return 1;
        double target = (sentiment == MASentiment.Bullish ? getBullishTarget() : getBearishTarget());

        if (d == Direction.Long) {
            if (currDelta < target) {
                candidate = (int) Math.floor((target - currDelta) / (price * fx));
            }
        } else if (d == Direction.Short) {
            if (currDelta > target) {
                candidate = (int) Math.floor((currDelta - target) / (price * fx));
            }
        }
        pr("GET PERC SIZE: price", price, "fx", fx, "senti", sentiment, "dir", d, "currDel", currDelta,
                "bull bear targets", getBullishTarget(), getBearishTarget(), "candidate ", candidate);
        LocalTime now = LocalTime.now();
        //am trade size is forced to be 1
        //pm trade size can be 3 at a time.
        int maxSize = futurePMSession().test(now) ? (d == Direction.Long ? 2 : 1) :
                futureAMSession().test(now) ? (d == Direction.Long ? 1 : 2) : 1;
        return Math.max(0, Math.min(candidate, maxSize));
    }

    /**
     * percentileTrader
     */
    public static synchronized void percentileTrader(LocalDateTime nowMilli, double freshPrice) {
        // run every 15 minutes
        int perc = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));
        double pd = getPD(freshPrice);
        double fx = ChinaPosition.fxMap.getOrDefault("SGXA50", 0.0);
        if (perc == 0) {
            pr(" perc 0 suspicious ", futData.get(ibContractToFutType(activeFuture)));
        }
        if (futData.get(ibContractToFutType(activeFuture)).size() == 0) return;

        long accSize = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getTradeType() == PERC_ACC)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToInt(e -> e.getValue().getOrder().getTotalSize())
                .sum();

        long deccSize = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getTradeType() == AutoOrderType.PERC_DECC)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToInt(e -> e.getValue().getOrder().getTotalSize())
                .sum();

        long netPercTrades = accSize - deccSize;

        long percOrdersTotal = globalIdOrderMap.entrySet().stream().filter(e -> isPercTrade().test(e.getValue().getTradeType()))
                .count();

        LocalDateTime lastPercTradeT = globalIdOrderMap.entrySet().stream()
                .filter(e -> isPercTrade().test(e.getValue().getTradeType()))
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .map(e -> e.getValue().getOrderTime())
                .orElse(sessionOpenT());

        LocalDateTime lastPercOrderT = globalIdOrderMap.entrySet().stream()
                .filter(e -> isPercTrade().test(e.getValue().getTradeType()))
                .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .map(e -> e.getValue().getOrderTime())
                .orElse(sessionOpenT());

        double avgAccprice = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getTradeType() == PERC_ACC)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().lmtPrice()).average().orElse(0.0);

        double avgDeccprice = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getTradeType() == AutoOrderType.PERC_DECC)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().lmtPrice()).average().orElse(0.0);

        int unfilledPercOrdersCount = (int) globalIdOrderMap.entrySet().stream()
                .filter(e -> isPercTrade().test(e.getValue().getTradeType()))
                .filter(e -> e.getValue().getStatus() != OrderStatus.Filled &&
                        e.getValue().getStatus() != OrderStatus.Inactive &&
                        e.getValue().getStatus() != OrderStatus.Cancelled &&
                        e.getValue().getStatus() != OrderStatus.ApiCancelled)
                .peek(e -> pr(e.getValue())).count();

        if (unfilledPercOrdersCount != 0) {
            pr(" unfilled perc orders count: ", unfilledPercOrdersCount, " manual cancel required ");
            return;
        }

        int minBetweenPercOrders = percOrdersTotal == 0 ? 0 : 10;
        double currDelta = ChinaPosition.getNetPtfDelta();
        double currStockDelta = ChinaPosition.getStockPtfDelta();

        pr("perc Trader status?", percentileTradeOn.get() ? "ON" : "OFF",
                nowMilli.toLocalTime().truncatedTo(ChronoUnit.SECONDS),
                "p%:", perc, "CurrDelta: ", r(currDelta), "pd", r10000(pd),
                "BullBear target : ", getBullishTarget(), getBearishTarget(),
                "acc#, decc#, net#", accSize, deccSize, netPercTrades
                , "accAvg, DecAvg,", avgAccprice, avgDeccprice,
                "OrderT TradeT,next tradeT",
                lastPercOrderT.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                lastPercTradeT.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                lastPercOrderT.plusMinutes(minBetweenPercOrders).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        );

        //******************************************************************************************//
        //if (!(now.isAfter(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(15, 0)))) return;
        if (!percentileTradeOn.get()) return;
        if (currStockDelta == 0) {
            pr(" stock delta 0 , returning ");
            return;
        }
        //*****************************************************************************************//
        if (timeDiffinMinutes(lastPercOrderT, nowMilli) >= minBetweenPercOrders) {
            if (perc < DOWN_PERC) {
                if (currDelta < getBullishTarget()) {
                    int id = autoTradeID.incrementAndGet();
                    int buySize = getPercTraderSize(freshPrice, fx, Direction.Long, currDelta);
                    if (buySize > 0) {
                        Order o = placeBidLimit(freshPrice, buySize);
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Perc bid", PERC_ACC));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        outputOrderToAutoLog(str(o.orderId(), "perc bid",
                                globalIdOrderMap.get(id), " perc ", perc));
                    } else {
                        pr("perc buy size not tradable " + buySize);
                        //throw new IllegalStateException(" perc buy size <= 0 "+ buySize);
                    }
                } else {
                    pr(" perc: delta above bullish target ");
                }
            } else if (perc > UP_PERC) {
                if (currDelta > getBearishTarget()) {
                    int id = autoTradeID.incrementAndGet();
                    int sellSize = getPercTraderSize(freshPrice, fx, Direction.Short, currDelta);
                    if (sellSize > 0) {
                        Order o = placeOfferLimit(freshPrice, sellSize);
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Perc offer",
                                AutoOrderType.PERC_DECC));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        outputOrderToAutoLog(str(o.orderId(), "perc offer", globalIdOrderMap.get(id), "perc", perc));
                    } else {
                        pr("perc sell size not tradable " + sellSize);
                        //throw new IllegalStateException(" perc sell size < 0 ");
                    }
                } else {
                    pr("perc: delta below bearish target ");
                }
            }
//            else {
//                if (currDelta > getDeltaHighLimit() && pd > PD_DOWN_THRESH && perc > 50) {
//                    if (freshPrice > avgAccprice || accSize == 0) {
//                        int id = autoTradeID.incrementAndGet();
//                        Order o = placeOfferLimit(freshPrice, 1);
//                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Perc offer COVER", PERC_DECC));
//                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
//                        outputOrderToAutoLog(str(o.orderId(), "perc offer,COVER", globalIdOrderMap.get(id)
//                                , "perc: ", perc));
//                    }
//                } else if (currDelta < getDeltaLowLimit() && pd < PD_UP_THRESH && perc < 50) {
//                    if (freshPrice < avgDeccprice || deccSize == 0) {
//                        int id = autoTradeID.incrementAndGet();
//                        Order o = placeBidLimit(freshPrice, 1);
//                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Perc bid COVER", PERC_ACC));
//                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
//                        outputOrderToAutoLog(str(o.orderId(), "perc bid, COVER", globalIdOrderMap.get(id),
//                                "perc: ", perc));
//                    }
//                }
//            }
        }
    }

    /**
     * PD trader
     */
    public static synchronized void pdTrader(LocalDateTime nowMilli, double freshPrice) {
        int perc = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));
        double pd = getPD(freshPrice);
        int pdPerc = getPDPercentile();
        double currDelta = ChinaPosition.getNetPtfDelta();

        pr(nowMilli.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                " pd trader ", " barrier# ", pdBarrier.getNumberWaiting(), " PD sem#: ",
                pdSemaphore.availablePermits(), "pd", r10000(pd), "pd P%", pdPerc);

        if (nowMilli.toLocalTime().isBefore(LocalTime.of(9, 30))) {
            pr(" QUITTING PD, local time before 9 30 ");
            return;
        }

        if (currDelta > getDeltaHighLimit() || currDelta < getDeltaLowLimit()) {
            pr("PD Trader: outside delta limit ");
            return;
        }

        if (!pdTraderOn.get()) {
            pr("pd trader off");
            return;
        }

        // print all pd trades
        globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getTradeType() == AutoOrderType.PD_OPEN ||
                e.getValue().getTradeType() == AutoOrderType.PD_CLOSE)
                .forEach(Utility::pr);

        if (pdBarrier.getNumberWaiting() == 2) {
            outputToAutoLog(str(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES), " resetting PD barrier" +
                    ": barrier waiting: 2 ", "resetting semaphore "));
            pdBarrier.reset();
            pdSemaphore = new Semaphore(2);
        }

        if (pdBarrier.getNumberWaiting() == 0 && pdSemaphore.availablePermits() == 2) {
            if (perc < DOWN_PERC && pdPerc < DOWN_PERC && pd < PD_DOWN_THRESH) {
                try {
                    pdSemaphore.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, PD_ORDER_QUANTITY);
                apcon.placeOrModifyOrder(activeFuture, o, new InventoryOrderHandler(id, pdBarrier));
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, PD_OPEN));
                outputOrderToAutoLog(str(o.orderId(), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                        , " PD buy open ", globalIdOrderMap.get(id)));

            } else if (perc > UP_PERC && pdPerc > UP_PERC && pd > PD_UP_THRESH) {
                try {
                    pdSemaphore.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, 1);
                apcon.placeOrModifyOrder(activeFuture, o, new InventoryOrderHandler(id, pdBarrier));
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, PD_OPEN));
                outputOrderToAutoLog(str(o.orderId(), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                        , " PD sell open ", globalIdOrderMap.get(id)));
            }
        } else if (pdBarrier.getNumberWaiting() == 1 && pdSemaphore.availablePermits() == 1) {
            globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getTradeType() == AutoOrderType.PD_OPEN)
                    .max(Comparator.comparing(e -> e.getValue().getOrderTime())).ifPresent(e -> {
                pr("last pd trade ", e.getValue());
                double q = e.getValue().getOrder().totalQuantity();
                double limit = e.getValue().getOrder().lmtPrice();

                if (q > 0 && pdPerc > 70 && freshPrice > limit && pd > 0.0) {
                    try {
                        pdSemaphore.acquire();
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    int idSellClose = autoTradeID.incrementAndGet();
                    Order sellO = placeOfferLimit(freshPrice, 1);
                    apcon.placeOrModifyOrder(activeFuture, sellO, new InventoryOrderHandler(idSellClose, pdBarrier));
                    globalIdOrderMap.put(idSellClose, new OrderAugmented(nowMilli, sellO, PD_CLOSE));
                    outputOrderToAutoLog(str(sellO.orderId(), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                            , " PD sell Close ", globalIdOrderMap.get(idSellClose)));
                } else if (q < 0 && pdPerc < 30 && freshPrice < limit && pd < 0.0) {
                    try {
                        pdSemaphore.acquire();
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    int idBuyClose = autoTradeID.incrementAndGet();
                    Order buyO = placeBidLimit(freshPrice, PD_ORDER_QUANTITY);
                    globalIdOrderMap.put(idBuyClose, new OrderAugmented(LocalDateTime.now()
                            .truncatedTo(ChronoUnit.MINUTES), buyO, "PD Buy close", PD_CLOSE));
                    apcon.placeOrModifyOrder(activeFuture, buyO, new InventoryOrderHandler(idBuyClose, pdBarrier));
                    outputOrderToAutoLog(str(buyO.orderId(), " PD Buy Close ", globalIdOrderMap.get(idBuyClose)));
                }
            });
        }
    }

    private static int getPDPercentile() {
        // one day only so far
        LocalDate d = (LocalTime.now().isBefore(LocalTime.of(5, 0))) ? LocalDate.now().minusDays(1) : LocalDate.now();
        int candidate = 50;
        NavigableMap<LocalDateTime, Double> dpMap = new ConcurrentSkipListMap<>();
        futData.get(ibContractToFutType(activeFuture)).entrySet().stream().filter(e -> e.getKey()
                .isAfter(LocalDateTime.of(d, LocalTime.of(9, 29)))).forEach(e -> {
            if (ChinaData.priceMapBar.get(ftseIndex).size() > 0 && ChinaData.priceMapBar.get(ftseIndex).
                    firstKey().isBefore(e.getKey().toLocalTime())) {
                double index = ChinaData.priceMapBar.get(ftseIndex).floorEntry(e.getKey().toLocalTime())
                        .getValue().getClose();
                dpMap.put(e.getKey(), r10000((e.getValue().getClose() / index - 1)));
            }
        });

        if (detailedPrint.get()) {
            if (dpMap.size() > 0) {
                pr("PD last: ", dpMap.lastEntry(),
                        "max: ", dpMap.entrySet().stream().mapToDouble(Map.Entry::getValue).max().orElse(0.0),
                        "min: ", dpMap.entrySet().stream().mapToDouble(Map.Entry::getValue).min().orElse(0.0),
                        " map: ", dpMap);
            }
        }
        candidate = XuTraderHelper.getPercentileForDouble(dpMap);
        return candidate;
    }

    /**
     * flatten delta aggressively at bid/offer
     */
    public static synchronized void flattenAggressively() {
        LocalDateTime nowMilli = LocalDateTime.now();
        double fx = ChinaPosition.fxMap.get("SGXA50");
        double currDelta = ChinaPosition.getNetPtfDelta();
        pr(" In Flattening aggressively ");
        if (currDelta < getBullishTarget() && currDelta > getBearishTarget()) {
            pr(" flatten aggressively no need, delta in line");
            return;
        }

        if (currDelta > getBullishTarget()) { //no sell at discount or at bottom
            int id = autoTradeID.incrementAndGet();
            double candidatePrice = bidMap.get(ibContractToFutType(activeFuture));
            Order o = placeOfferLimitTIF(candidatePrice, sizeToFlatten(candidatePrice, fx, currDelta),
                    Types.TimeInForce.IOC);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FLATTEN_AGGRESSIVE));
            outputOrderToAutoLog(str(o.orderId(), " AGGRESSIVE Sell Flatten ", globalIdOrderMap.get(id)));
        } else if (currDelta < getBearishTarget()) { // no buy at premium or at top
            int id = autoTradeID.incrementAndGet();
            double candidatePrice = askMap.get(ibContractToFutType(activeFuture));
            Order o = placeBidLimitTIF(candidatePrice, sizeToFlatten(candidatePrice, fx, currDelta), Types.TimeInForce.IOC);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FLATTEN_AGGRESSIVE));
            outputOrderToAutoLog(str(o.orderId(), " AGGRESSIVE Buy Flatten ", globalIdOrderMap.get(id)));
        }
    }

    /**
     * Once touch the MA, flatten position into bull bear range
     *
     * @param nowMilli   time in millisecs
     * @param freshPrice most recent price
     */
    public static synchronized void flattenTrader(LocalDateTime nowMilli, double freshPrice) {
        double currDelta = ChinaPosition.getNetPtfDelta();
        int perc = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));
        double pd = getPD(freshPrice);

        if (currDelta < getBullishTarget() && currDelta > getBearishTarget()) {
            pr(" Flatten trader: no need to flatten", r(currDelta));
            return;
        }

        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        if (price5.size() <= 1 || activeLastMinuteMap.size() <= 1) {
            pr(" Flatten Trader: active map size < 1, return");
            return;
        }
        double fx = ChinaPosition.fxMap.getOrDefault("SGXA50", 1.0);

        SimpleBar lastBar = new SimpleBar(price5.lastEntry().getValue());
        double prevPrice = activeLastMinuteMap.size() <= 2 ? freshPrice : activeLastMinuteMap
                .lowerEntry(nowMilli).getValue();
        lastBar.add(freshPrice);
        NavigableMap<LocalDateTime, Double> sma = getMAGen(price5, currentMAPeriod);
        double maLast = sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;

        pr(nowMilli, " Flatten Trader Delta: "
                , r(currDelta), "prev Price ", prevPrice, " price ", freshPrice, " ma ", r(maLast)
                , "Crossed?: ", (prevPrice > maLast && freshPrice <= maLast) ||
                        (prevPrice < maLast && freshPrice >= maLast));

        if (currDelta > getBullishTarget() && prevPrice > maLast && freshPrice <= maLast
                && perc > UP_PERC && pd > PD_DOWN_THRESH) { //no sell at discount or at bottom
            int id = autoTradeID.incrementAndGet();

            double candidatePrice = (flattenEagerness == Eagerness.Passive) ? freshPrice :
                    bidMap.get(ibContractToFutType(activeFuture));

            Order o = placeOfferLimitTIF(candidatePrice, sizeToFlatten(freshPrice, fx, currDelta),
                    Types.TimeInForce.IOC);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Sell", FLATTEN));
            outputOrderToAutoLog(str(o.orderId(), " Sell Flatten ", globalIdOrderMap.get(id)));
        } else if (currDelta < getBearishTarget() && prevPrice < maLast && freshPrice >= maLast
                && perc < DOWN_PERC && pd < PD_UP_THRESH) { // no buy at premium or at top
            int id = autoTradeID.incrementAndGet();

            double candidatePrice = (flattenEagerness == Eagerness.Passive) ? freshPrice :
                    askMap.get(ibContractToFutType(activeFuture));

            Order o = placeBidLimitTIF(candidatePrice, sizeToFlatten(freshPrice, fx, currDelta), Types.TimeInForce.IOC);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "BUY", FLATTEN));
            outputOrderToAutoLog(str(o.orderId(), " Buy Flatten ", globalIdOrderMap.get(id)));
        }
    }


    /**
     * open trading from 9 to 9:40 /  fast trader
     */
    public static void fastTrader(LocalDateTime nowMilli, double freshPrice) {
        if (!(nowMilli.toLocalTime().isAfter(LocalTime.of(8, 59)) &&
                nowMilli.toLocalTime().isBefore(LocalTime.of(15, 0)))) {
            return;
        }
        int currPos = currentPosMap.getOrDefault(ibContractToFutType(activeFuture), 0);
        setLongShortTradability(currPos);

        int default_sec_btwn_fast_orders = nowMilli.toLocalTime().isBefore(LocalTime.of(9, 40)) ? 5 : 10;
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        if (price5.size() <= 1 || activeLastMinuteMap.size() <= 1) return;
        double pd = getPD(freshPrice);

        SimpleBar lastBar = new SimpleBar(price5.lastEntry().getValue());
        double prevPrice = activeLastMinuteMap.size() <= 2 ? freshPrice : activeLastMinuteMap.lowerEntry(nowMilli).getValue();
        lastBar.add(freshPrice);
        NavigableMap<LocalDateTime, Double> sma;
        sma = getMAGen(price5, currentMAPeriod);

        if (detailedPrint.get()) {
            pr(str(" Detailed MA ON", "pd", r10000(pd),
                    "freshPrice", freshPrice,
                    "prev Price", prevPrice,
                    "last bar", lastBar,
                    "SMA", r(sma.lastEntry().getValue()))); //"activeLastMap: ", activeLastMinuteMap)
        }

        double maLast = sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;
        //sentiment = freshPrice > maLast ? MASentiment.Bullish : MASentiment.Bearish;
        double candidatePrice;
        long numOrdersThisSession = fastOrderMap.entrySet().stream().filter(e -> e.getKey().isAfter(sessionOpenT())).count();
        long secBtwnOpenOrders = numOrdersThisSession == 0 ? 0 :
                default_sec_btwn_fast_orders *
                        Math.max(1, Math.round(Math.pow(2, Math.min(13, numOrdersThisSession - 1))));

        int timeDiffFactor = numOrdersThisSession == 0 ? 1 :
                Math.max(1, Math.min(2,
                        (int) Math.floor(timeDiffInSeconds(fastOrderMap.lastEntry().getKey(), nowMilli) / 60d)));

        if (timeDiffInSeconds(lastFastOrderTime, nowMilli) >= secBtwnOpenOrders &&
                numOrdersThisSession <= MAX_OPEN_TRADE_ORDERS) {
            if (prevPrice < maLast && freshPrice >= maLast && canLongGlobal.get() && pd < PD_UP_THRESH) {
                candidatePrice = roundToXUPriceVeryPassive(maLast, Direction.Long, numOrdersThisSession);
                if (checkIfOrderPriceMakeSense(candidatePrice)) {
                    Order o = placeBidLimit(candidatePrice, trimProposedPosition(1, currPos));
                    int id = autoTradeID.incrementAndGet();
                    globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Fast Trade Bid", FAST));
                    apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                    fastTradeSignals.incrementAndGet();
                    lastFastOrderTime = LocalDateTime.now();
                    fastOrderMap.put(nowMilli, o);
                    outputOrderToAutoLog(str(o.orderId(), nowMilli.truncatedTo(ChronoUnit.MINUTES),
                            "FAST ORDER || BIDDING @ ", o.toString(), "SMA",
                            "||Fresh: ", freshPrice,
                            "||Prev: ", prevPrice,
                            "||MA LAST: ", r(maLast),
                            "||Last Open Order T", lastFastOrderTime,
                            "||secToWait", secBtwnOpenOrders,
                            " #: ", numOrdersThisSession,
                            "||Default Sec Btwn orders ", default_sec_btwn_fast_orders,
                            "||This bar: ", lastBar,
                            "||PD: ", r10000(pd)));
                }
            }
            if (prevPrice > maLast && freshPrice <= maLast && canShortGlobal.get() && pd > PD_DOWN_THRESH) {
                candidatePrice = roundToXUPriceVeryPassive(maLast, Direction.Short, numOrdersThisSession);
                if (checkIfOrderPriceMakeSense(candidatePrice)) {
                    Order o = placeOfferLimit(candidatePrice, trimProposedPosition(1, currPos));
                    int id = autoTradeID.incrementAndGet();
                    globalIdOrderMap.put(id, new OrderAugmented(nowMilli.truncatedTo(ChronoUnit.MINUTES), o,
                            "Fast Trade Offer", FAST));
                    apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                    fastTradeSignals.incrementAndGet();
                    lastFastOrderTime = LocalDateTime.now();
                    fastOrderMap.put(nowMilli, o);
                    outputOrderToAutoLog(str(o.orderId(), nowMilli.truncatedTo(ChronoUnit.MINUTES),
                            "FAST ORDER || OFFERING @ ", o.toString(), "SMA",
                            "||Fresh: ", freshPrice, "||Prev", prevPrice,
                            "||MA Last: ", r(maLast),
                            "||Last Open Order T", lastFastOrderTime,
                            "||secToWait ", secBtwnOpenOrders,
                            "||#: ", fastTradeSignals.get(),
                            "||Default Sec Btwn orders ", default_sec_btwn_fast_orders,
                            "||This bar: ", lastBar,
                            "||PD: ", r10000(pd)));
                }
            }
        }
    }

    /**
     * Auto trading based on Moving Avg
     */
    public static synchronized void MATrader(LocalDateTime nowMilli, double freshPrice) {
        if (!MATraderStatus.get()) {
            pr("MA trader off");
            return;
        }
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        if (price5.size() <= 2) return;
        int currPos = currentPosMap.getOrDefault(ibContractToFutType(activeFuture), 0);
        setLongShortTradability(currPos);

        SimpleBar lastBar = new SimpleBar(price5.lastEntry().getValue());
        LocalTime lastBarTime = price5.lastEntry().getKey().toLocalTime();
        SimpleBar secLastBar = new SimpleBar(price5.lowerEntry(price5.lastKey()).getValue());
        LocalTime secLastBarTime = price5.lowerEntry(price5.lastKey()).getKey().toLocalTime();

        lastBar.add(freshPrice);
        NavigableMap<LocalDateTime, Double> sma;
        sma = getMAGen(price5, currentMAPeriod);
        double maLast = sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;
        sentiment = freshPrice > maLast ? MASentiment.Bullish : MASentiment.Bearish;

        pr("sentiment/fresh /malast ", sentiment, freshPrice, r(maLast));

        double currDelta = ChinaPosition.getNetPtfDelta();

        int numTrades = 0;
        double candidatePrice = 0.0;
        String priceType;
        int percentile = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));

        double indexPrice = (ChinaData.priceMapBar.containsKey(ftseIndex) &&
                ChinaData.priceMapBar.get(ftseIndex).size() > 0) ?
                ChinaData.priceMapBar.get(ftseIndex).lastEntry().getValue().getClose() : SinaStock.FTSE_OPEN;
        double pd = (indexPrice != 0.0 && freshPrice != 0.0) ? (freshPrice / indexPrice - 1) : 0.0;
        double fx = ChinaPosition.fxMap.getOrDefault("SGXA50", 1.0);

        if (tradesMap.get(ibContractToFutType(activeFuture)).size() > 0) {
            lastMATradeTime = tradesMap.get(ibContractToFutType(activeFuture)).lastKey();
            numTrades = tradesMap.get(ibContractToFutType(activeFuture))
                    .entrySet().stream().filter(e -> e.getKey().isAfter(sessionOpenT()))
                    .mapToInt(e -> e.getValue().getSizeAllAbs()).sum();
        }

//        timeBtwnMAOrders = maOrderMap.size() == 0 ? 0 :
//                Math.max(5, Math.round(5 * Math.pow(2, Math.min(7, maOrderMap.size() - 1))));
        long numOrdersThisSession = maOrderMap.entrySet().stream().filter(e -> e.getKey().isAfter(sessionOpenT())).count();
        timeBtwnMAOrders = numOrdersThisSession == 0 ? 0 : Math.max(5, Math.round(5 * (numOrdersThisSession - 1)));
        if (timeDiffinMinutes(lastMAOrderTime, nowMilli) >= timeBtwnMAOrders
                && maSignals.get() <= MAX_MA_SIGNALS_PER_SESSION) {
            if (touchConditionMet(secLastBar, lastBar, maLast)) {
                if (bullishTouchMet(secLastBar, lastBar, maLast) && canLongGlobal.get()
                        && currDelta + fx * freshPrice < getBullishTarget() && percentile < DOWN_PERC) {
                    if (currentDirection == Direction.Long || pd > PD_UP_THRESH) {
                        candidatePrice = roundToXUPricePassive(maLast, Direction.Long);
                        priceType = (pd > PD_UP_THRESH ? "pd > " + PD_UP_THRESH : "already Long") + " BUY @ MA";
                    } else if (pd < PD_DOWN_THRESH) {
                        candidatePrice = askMap.getOrDefault(ibContractToFutType(activeFuture), 0.0);
                        priceType = "pd < " + PD_DOWN_THRESH + " -> LIFT OFFER";
                    } else if (maSignals.get() == 0) {
                        candidatePrice = bidMap.getOrDefault(ibContractToFutType(activeFuture), 0.0);
                        priceType = "maSignals = 0 -> BUY BID";
                    } else {
                        candidatePrice = roundToXUPricePassive(lastBar.getOpen(), Direction.Long);
                        priceType = " maSignals > 0 -> BUY @ LAST BAR OPEN";
                    }

//                    trimProposedPosition(
//                            determinePDPercFactor(nowMilli.toLocalTime(), pd, Direction.Long,
//                                    percentile) * determineTimeDiffFactor(), currPos)
                    Order o = placeBidLimit(candidatePrice, 1);
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        //apcon.cancelAllOrders();
                        maOrderMap.put(nowMilli, o);
                        maSignals.incrementAndGet();
                        int id = autoTradeID.incrementAndGet();
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "MA Trade bid", MA));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        lastMAOrderTime = LocalDateTime.now();
                        currentDirection = Direction.Long;
                        outputOrderToAutoLog(str(o.orderId(), "MA ORDER || bidding @ ", o.toString(), priceType,
                                globalIdOrderMap.get(id)));
                    }
                } else if (bearishTouchMet(secLastBar, lastBar, maLast) && canShortGlobal.get()
                        && currDelta - fx * freshPrice > getBearishTarget() && percentile > UP_PERC) {
                    if (currentDirection == Direction.Short || pd < PD_DOWN_THRESH) {
                        candidatePrice = roundToXUPricePassive(maLast, Direction.Short);
                        priceType = (pd < PD_DOWN_THRESH ? "pd < " + PD_DOWN_THRESH : "currDir is short(same)")
                                + " SELL @ MA";
                    } else if (pd > PD_UP_THRESH) {
                        candidatePrice = bidMap.get(ibContractToFutType(activeFuture));
                        priceType = "pd > " + PD_UP_THRESH + " -> SELL @ BID";
                    } else if (maSignals.get() == 0) {
                        candidatePrice = askMap.get(ibContractToFutType(activeFuture));
                        priceType = "SELL: maSignals =  -> SELL @ OFFER";
                    } else {
                        candidatePrice = roundToXUPricePassive(lastBar.getOpen(), Direction.Short);
                        priceType = "SELL: maSignals > 0 -> SELL @ last Bar OPEN";
                    }
//                    trimProposedPosition(determinePDPercFactor(nowMilli.toLocalTime(), pd,
//                            Direction.Short, percentile) * determineTimeDiffFactor(), currPos)
                    Order o = placeOfferLimit(candidatePrice, 1);
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        maOrderMap.put(nowMilli, o);
                        maSignals.incrementAndGet();
                        //apcon.cancelAllOrders();
                        int id = autoTradeID.incrementAndGet();
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "MA Trade offer", MA));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        lastMAOrderTime = LocalDateTime.now();
                        currentDirection = Direction.Short;
                        outputOrderToAutoLog(str(o.orderId(), "MA ORDER || offering @ ", o.toString(), priceType
                                , globalIdOrderMap.get(id)));
                    }
                }
                if (candidatePrice != 0.0) {
                    String outputMsg = str("MA TRIGGER CONDITION|| ",
                            nowMilli.truncatedTo(ChronoUnit.MINUTES),
                            autoTradeID.get(),
                            "|SMA:", r(maLast),
                            "|PD", r10000(pd), "|Index", r(indexPrice),
                            "||Last Bar: ", lastBarTime, lastBar,
                            "|Sec last Bar", secLastBarTime, secLastBar,
                            "|Perc: ", percentile,
                            "#: ", maSignals.get(),
                            maOrderMap.entrySet().stream().filter(e -> e.getKey().isAfter(sessionOpenT())).toString(),
                            "|Last Trade Time:", lastMATradeTime.truncatedTo(ChronoUnit.MINUTES),
                            "|Last Order Time:", lastMAOrderTime.truncatedTo(ChronoUnit.MINUTES),
                            "|Order Wait T:", timeBtwnMAOrders);
                    outputToAutoLog(outputMsg);
                }
            }
        }
    }

    /**
     * overnight close trading
     */
    private void overnightTrader() {
        if (!overnightTradeOn.get()) return;
        if (futureAMSession().test(LocalTime.now()) || futurePMSession().test(LocalTime.now())) return;
        double currDelta = ChinaPosition.getNetPtfDelta();
        int currPos = currentPosMap.getOrDefault(ibContractToFutType(activeFuture), 0);
        setLongShortTradability(currPos);

        LocalDateTime now = LocalDateTime.now();
        LocalDate TDate = now.toLocalTime().isAfter(LocalTime.of(0, 0))
                && now.toLocalTime().isBefore(LocalTime.of(5, 0)) ? LocalDate.now().minusDays(1L) : LocalDate.now();

        int absLotsTraded = 0;
        double netTradedDelta = 0.0;

        if (overnightTradesMap.get(ibContractToFutType(activeFuture)).size() > 0) {
            absLotsTraded = overnightTradesMap.get(ibContractToFutType(activeFuture))
                    .entrySet().stream().mapToInt(e -> e.getValue().getSizeAllAbs()).sum();
            netTradedDelta = overnightTradesMap.get(ibContractToFutType(activeFuture))
                    .entrySet().stream().mapToDouble(e -> e.getValue().getDeltaAll()).sum();
        }

        double indexPrice = (ChinaData.priceMapBar.containsKey(ftseIndex) &&
                ChinaData.priceMapBar.get(ftseIndex).size() > 0) ?
                ChinaData.priceMapBar.get(ftseIndex).lastEntry().getValue().getClose() : SinaStock.FTSE_OPEN;
        NavigableMap<LocalDateTime, SimpleBar> futPriceMap = futData.get(ibContractToFutType(activeFuture));
        LocalDateTime ytdCloseTime = LocalDateTime.of(TDate, LocalTime.of(15, 0));

        int currPercentile = getPercentileForLast(futPriceMap);
        double currentFut;
        double pd = 0.0;

        if (futPriceMap.size() > 0) {
            currentFut = futPriceMap.lastEntry().getValue().getClose();
            pd = (indexPrice != 0.0 ? r10000(currentFut / indexPrice - 1) : 0.0);
        }

        if (absLotsTraded <= maxOvernightTrades && overnightClosingOrders.get() <= 5) {
            if (now.toLocalTime().isBefore(LocalTime.of(5, 0)) &&
                    now.toLocalTime().isAfter(LocalTime.of(4, 40))) {
                if (pd > PD_UP_THRESH && canShortGlobal.get() && currDelta > getDeltaLowLimit()
                        && currPercentile > UP_PERC) {
                    double candidatePrice = askMap.getOrDefault(ibContractToFutType((activeFuture)), 0.0);
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        int id = autoTradeID.incrementAndGet();
                        Order o = placeOfferLimit(candidatePrice, trimProposedPosition(
                                1, currPos));
                        globalIdOrderMap.put(id, new OrderAugmented(now, o, "Overnight Short", OVERNIGHT));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        outputOrderToAutoLog(str(o.orderId(), "O/N placing sell order @ ", candidatePrice,
                                " curr p% ", currPercentile, "curr PD: ", pd));

                        overnightClosingOrders.incrementAndGet();
                    }
                } else if (pd < PD_DOWN_THRESH && canLongGlobal.get() && currDelta < getDeltaHighLimit()
                        && currPercentile < DOWN_PERC) {
                    double candidatePrice = bidMap.getOrDefault(ibContractToFutType(activeFuture), 0.0);
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        int id = autoTradeID.incrementAndGet();
                        Order o = placeBidLimit(candidatePrice, trimProposedPosition(1, currPos));
                        globalIdOrderMap.put(id, new OrderAugmented(now, o, "Overnight Long", OVERNIGHT));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        outputOrderToAutoLog(str(o.orderId(), "O/N placing buy order @ ", candidatePrice,
                                "perc: ", currPercentile, "PD: ", pd));
                        overnightClosingOrders.incrementAndGet();
                    }
                } else {
                    outputToAutoLog(str(now, " nothing done "));
                }
            } else {
                outputToAutoLog(" outside tradable time slot");
            }
        } else {
            outputToAutoLog(str(now, " trades or delta exceeded MAX "));
        }

        String outputString = str("||O/N||", now.format(DateTimeFormatter.ofPattern("M-d H:mm:ss")),
                "||O/N trades done", absLotsTraded, "", "||O/N Delta: ", netTradedDelta,
                "||current percentile ", currPercentile, "||PD: ", pd,
                "||curr P: ", futPriceMap.lastEntry().getValue().getClose(),
                "||index: ", Math.round(100d * indexPrice) / 100d,
                "||BID ASK ", bidMap.getOrDefault(ibContractToFutType(activeFuture), 0.0),
                askMap.getOrDefault(ibContractToFutType(activeFuture), 0.0),
                "||Can Long? ", canLongGlobal,
                "|| Can Short? ", canShortGlobal);

        outputToAutoLog(outputString);
        requestOvernightExecHistory();
    }

    /**
     * inventory trader
     *
     * @param t          localtime
     * @param freshPrice price
     */
    public static void inventoryTrader(LocalDateTime t, double freshPrice) {
        int perc = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));
        if (perc == 0) {
            pr(" perc 0 suspicious ", futData.get(ibContractToFutType(activeFuture)));
        }
        double currDelta = ChinaPosition.getNetPtfDelta();
        pr("Inventory trade: ", inventoryTraderOn.get() ? "ON" : "OFF",
                t.truncatedTo(ChronoUnit.SECONDS),
                "senti: ", sentiment, "perc ", perc,
                "inventory barrier waiting #: ", inventoryBarrier.getNumberWaiting(),
                " semaphore permits: ", inventorySemaphore.availablePermits(),
                freshPrice, " chg: ",
                activeLastMinuteMap.size() < 2 ? "No trade last min " :
                        (freshPrice - activeLastMinuteMap.lowerEntry(t).getValue()),
                "Delta: ", r(currDelta));

        int currPos = currentPosMap.getOrDefault(ibContractToFutType(activeFuture), 0);

        if (Math.abs(currPos) > MAX_FUT_LIMIT) {
            pr(" quitting inventory trade, pos:", currPos, ": exceeding fut limit ");
            return;
        }

        if (currDelta > getBullishTarget() || currDelta < getBearishTarget()) {
            pr(" quitting inventory trade", r(currDelta), ": exceeding Delta limit ");
            return;
        }

        if (inventorySemaphore.availablePermits() == 0) {
            pr(" quitting inventory trade: semaphore #:", inventorySemaphore.availablePermits());
            return;
        }

        if (!inventoryTraderOn.get()) {
            pr(" quitting inventory trade: inventory off ");
            return;
        }

        if (inventoryBarrier.getNumberWaiting() != 0) {
            pr(" quitting inventory trade: barrier# waiting: ", inventoryBarrier.getNumberWaiting());
            return;
        }

        if (perc < DOWN_PERC) {
            try {
                inventorySemaphore.acquire();
                pr(" acquired semaphore now left:" + inventorySemaphore.availablePermits());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            CountDownLatch latchBuy = new CountDownLatch(1);
            int idBuy = autoTradeID.incrementAndGet();
            Order buyO = placeBidLimit(freshPrice - margin, INV_TRADE_QUANTITY);
            globalIdOrderMap.put(idBuy, new OrderAugmented(LocalDateTime.now()
                    , buyO, "Inv Buy Open", INVENTORY_OPEN));
            apcon.placeOrModifyOrder(activeFuture, buyO, new InventoryOrderHandler(idBuy, latchBuy, inventoryBarrier));
            outputOrderToAutoLog(str(buyO.orderId(), "Inv Buy Open ", globalIdOrderMap.get(idBuy)));

            try {
                pr(" BEFORE latchBuy.await ");
                latchBuy.await();
                pr(" AFTER latchBuy.await");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //buying done, now sell
            pr(" Bot, now putting in sell signal ");
            CountDownLatch latchSell = new CountDownLatch(1);
            int idSell = autoTradeID.incrementAndGet();
            Order sellO = placeOfferLimit(freshPrice + margin, INV_TRADE_QUANTITY);
            globalIdOrderMap.put(idSell, new OrderAugmented(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                    , sellO, "Inv Sell Close", INVENTORY_CLOSE));
            apcon.placeOrModifyOrder(activeFuture, sellO, new InventoryOrderHandler(idSell, latchSell, inventoryBarrier));
            outputOrderToAutoLog(str(sellO.orderId(), "Inv Sell Close ", globalIdOrderMap.get(idSell)));
            try {
                latchSell.await();
                if (inventoryBarrier.getNumberWaiting() == 2) {
                    outputToAutoLog(str(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                            , " resetting inventory barrier "));
                    inventoryBarrier.reset();
                    out.println(" reset inventory barrier ");
                }
                inventorySemaphore.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if (perc > UP_PERC) {
            try {
                inventorySemaphore.acquire();
                out.println(" acquired semaphore, now left: " + inventorySemaphore.availablePermits());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            CountDownLatch latchSell = new CountDownLatch(1);
            int idSell = autoTradeID.incrementAndGet();
            Order sellO = placeOfferLimit(freshPrice + margin, INV_TRADE_QUANTITY);
            globalIdOrderMap.put(idSell,
                    new OrderAugmented(LocalDateTime.now(), sellO, " Sell Open", INVENTORY_OPEN));
            apcon.placeOrModifyOrder(activeFuture, sellO, new InventoryOrderHandler(idSell, latchSell, inventoryBarrier));
            outputOrderToAutoLog(str(sellO.orderId(), "Inv Sell Open", globalIdOrderMap.get(idSell)));

            try {
                out.println(" BEFORE latchSell.await ");
                latchSell.await();
                out.println(" AFTER latchSell.await ");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            out.println(" Sold, now putting in buy signal ");
            CountDownLatch latchBuy = new CountDownLatch(1);
            int idBuy = autoTradeID.incrementAndGet();
            Order buyO = placeBidLimit(freshPrice - margin, INV_TRADE_QUANTITY);
            globalIdOrderMap.put(idBuy, new OrderAugmented(LocalDateTime.now(), buyO, " Buy Close", INVENTORY_CLOSE));
            apcon.placeOrModifyOrder(activeFuture, buyO, new InventoryOrderHandler(idBuy, latchBuy, inventoryBarrier));
            outputOrderToAutoLog(str(buyO.orderId(), "Inv Buy Close", globalIdOrderMap.get(idBuy)));

            try {
                latchBuy.await();
                if (inventoryBarrier.getNumberWaiting() == 2) {
                    inventoryBarrier.reset();
                    out.println(" reset inventory barrier ");
                }
                inventorySemaphore.release();
                pr(" released inventory semaphore, now ", inventorySemaphore.availablePermits());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        pr(" exiting inventory order checking not stuck ");
    }

    private static double getCurrentMA() {
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        if (price5.size() <= 2) return 0.0;

        NavigableMap<LocalDateTime, Double> sma = getMAGen(price5, currentMAPeriod);
        return sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;
    }

    public static void flattenDriftTrader(LocalDateTime t, double freshPrice) {
        double currentDelta = ChinaPosition.getNetPtfDelta();
        double malast = getCurrentMA();
        double fx = ChinaPosition.fxMap.getOrDefault("SGXA50", 0.0);
        double pd = getPD(freshPrice);
        int unfilled_trades = (int) globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getTradeType() ==
                FLATTEN || e.getValue().getTradeType() == DRIFT)
                .filter(e -> e.getValue().getStatus() != OrderStatus.Filled).count();

        pr(str("FLATTEN DRIFT TRADER current delta is ", currentDelta,
                " ma fx fresh", malast, fx, freshPrice, "sentiment ", sentiment, "unfilled ", unfilled_trades));

        if (currentDelta == 0.0 || malast == 0.0 || fx == 0.0) return;

        if (sentiment == MASentiment.Bullish && pd <= 0.0) {
            if (currentDelta < 0.0 && Math.abs(currentDelta) > FLATTEN_THRESH) {
                int size = (int) Math.ceil(Math.abs(currentDelta / (fx * freshPrice)));
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(roundToXUPricePassive(malast, Direction.Long), size);
                globalIdOrderMap.put(id, new OrderAugmented(t, o, "Bull: Buy to Flatten ", FLATTEN));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "Bull: Buy to Flatten ", globalIdOrderMap.get(id)));
            } else {
                if (currentDelta < getBullishTarget()) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeBidLimit(roundToXUPricePassive(malast, Direction.Long), 1.0);
                    globalIdOrderMap.put(id, new OrderAugmented(t, o, "Bullish: DRIFT BUY 1", AutoOrderType.DRIFT));
                    apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                    outputOrderToAutoLog(str(o.orderId(), "Bullish: DRIFT BUY 1", globalIdOrderMap.get(id)));
                }
            }
        } else if (sentiment == MASentiment.Bearish && pd >= 0.0) {
            if (currentDelta > 0.0 && Math.abs(currentDelta) > FLATTEN_THRESH) {
                int size = (int) Math.ceil(Math.abs(currentDelta / (fx * freshPrice)));
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(roundToXUPricePassive(malast, Direction.Short), size);
                globalIdOrderMap.put(id, new OrderAugmented(t, o, "Bearish: Sell to Flatten", FLATTEN));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "Bearish: Sell to Flatten", globalIdOrderMap.get(id)));
            } else { //drift
                if (currentDelta > getBearishTarget()) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeOfferLimit(roundToXUPricePassive(malast, Direction.Short), 1.0);
                    globalIdOrderMap.put(id, new OrderAugmented(t, o, "Bearish: DRIFT SELL", DRIFT));
                    apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                    outputOrderToAutoLog(str(o.orderId(), "Bearish: DRIFT SELL", globalIdOrderMap.get(id)));
                }
            }
        }
    }

    private void loadXU() {
        pr("in loadXU");
        ChinaMain.GLOBAL_REQ_ID.addAndGet(5);
        apcon.getSGXA50Historical2(ChinaMain.GLOBAL_REQ_ID.get(), this);
    }

    private static boolean checkIfOrderPriceMakeSense(double p) {
        FutType f = ibContractToFutType(activeFuture);
        out.println(str("CHECKING PRICE || bid ask price ",
                bidMap.get(f), askMap.get(f), futPriceMap.get(f)));
        return (p != 0.0)
                && (bidMap.getOrDefault(f, 0.0) != 0.0)
                && (askMap.getOrDefault(f, 0.0) != 0.0)
                && (futPriceMap.getOrDefault(f, 0.0) != 0.0)
                && Math.abs(askMap.get(f) - bidMap.get(f)) < 10;
    }

    private boolean futMarketOpen(LocalTime t) {
        return !(t.isAfter(LocalTime.of(5, 0)) && t.isBefore(LocalTime.of(9, 0)));
    }

    private static ApiController getAPICon() {
        return apcon;
    }

    private static void updateLog(String s) {
        outputArea.append(s);
        outputArea.append("\n");
        SwingUtilities.invokeLater(() -> outputArea.repaint());
    }

    private static void clearLog() {
        outputArea.setText("");
    }

    @Override
    public void handleHist(String name, String date, double open, double high, double low, double close) {
        //pr("handle hist ", name, date, open, close);
        LocalDate currDate = LocalDate.now();
        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            LocalDateTime ldt = LocalDateTime.of(ld, lt);
            if (!ld.equals(currDate) && lt.equals(LocalTime.of(14, 59))) {
                futPrevCloseMap.put(FutType.get(name), close);
            }

            int daysToGoBack = currDate.getDayOfWeek().equals(DayOfWeek.MONDAY) ? 4 : 2;
            if (ldt.toLocalDate().isAfter(currDate.minusDays(daysToGoBack)) && FUT_COLLECTION_TIME.test(ldt)) {
                if (lt.equals(LocalTime.of(9, 0))) {
                    futOpenMap.put(FutType.get(name), open);
                    out.println(" today open is for " + name + " " + open);
                }
                futData.get(FutType.get(name)).put(ldt, new SimpleBar(open, high, low, close));
            }
        } else {
            out.println(str(date, open, high, low, close));
        }
    }

    @Override
    public void actionUponFinish(String name) {
        out.println(" printing fut data " + name + " " + futData.get(FutType.get(name)).lastEntry());
    }

    @Override
    public void updateMktDepth(int position, String marketMaker, Types.DeepType operation, Types.DeepSide side,
                               double price, int size) {
        SwingUtilities.invokeLater(() -> {
            if (side.equals(Types.DeepSide.BUY)) {
                XUTrader.bidLabelList.get(position).setText(Utility.getStrCheckNull(price, "            ", size));
                XUTrader.bidPriceList.put("bid" + Integer.toString(position + 1), price);
            } else {
                XUTrader.askLabelList.get(position).setText(Utility.getStrCheckNull(price, "            ", size));
                XUTrader.offerPriceList.put("ask" + Integer.toString(position + 1), price);
            }
        });
    }

    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {
        FutType f = ibContractToFutType(contract);

        if (uniqueTradeKeySet.contains(tradeKey)) {
            pr(" duplicate trade key ", tradeKey);
            return;
        } else {
            //pr("adding trade key ", tradeKey);
            uniqueTradeKeySet.add(tradeKey);
        }

//        pr(" trade report tradekey contract , exec ", tradeKey, contract.symbol(),
//                contract.lastTradeDateOrContractMonth(), execution.side(), execution.time(), execution.shares(),
//                execution.price(), execution.orderId());

//        if (contract.symbol().equalsIgnoreCase("XINA50")) {
//            pr(str(" exec ", execution.side(), execution.time(), execution.cumQty()
//                    , execution.price(), execution.orderRef(), execution.orderId(),
//                    execution.permId(), execution.shares()));
//        }

        int sign = (execution.side().equals("BOT")) ? 1 : -1;
        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));

        int daysToGoBack = LocalDate.now().getDayOfWeek().equals(DayOfWeek.MONDAY) ? 4 : 2;
        if (ldt.toLocalDate().isAfter(LocalDate.now().minusDays(daysToGoBack))) {
            if (tradesMap.get(f).containsKey(ldt)) {
                tradesMap.get(f).get(ldt).addTrade(new FutureTrade(execution.price(),
                        (int) Math.round(sign * execution.shares())));
            } else {
                tradesMap.get(f).put(ldt,
                        new TradeBlock(new FutureTrade(execution.price(), (int) Math.round(sign * execution.shares()))));
            }
        }
    }

    @Override
    public void tradeReportEnd() {
        //pr("printing all tradesmap all ", tradesMap);
        //System.out.println(" printing trades map " + tradesMap.get(ibContractToFutType(activeFuture)));
        if (tradesMap.get(ibContractToFutType(activeFuture)).size() > 0) {
            currentDirection = tradesMap.get(ibContractToFutType(activeFuture)).lastEntry().getValue().getSizeAll() > 0 ?
                    Direction.Long : Direction.Short;
            lastTradeTime = tradesMap.get(ibContractToFutType(activeFuture)).lastEntry().getKey();
        }
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
    }

    //ApiController.IOrderHandler
    @Override
    public void orderState(OrderState orderState) {

    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId,
                            int parentId, double lastFillPrice, int clientId, String whyHeld) {
        updateLog(str(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));
        if (status.equals(OrderStatus.Filled)) {
            createDialog(str(" status filled remaining avgFillPrice ",
                    status, filled, remaining, avgFillPrice));
        }
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        updateLog(" handle error code " + errorCode + " message " + errorMsg);
    }

    @Override
    public void openOrder(Contract contract, Order order, OrderState orderState) {
        //pr(" open order ", contract, order, orderState);

        activeFutLiveIDOrderMap.put(order.orderId(), order);
        //globalIdOrderMap.put(autoTradeID.incrementAndGet(),)

        if (ibContractToSymbol(contract).equals(ibContractToSymbol(activeFuture))) {
            double sign = order.action().equals(Types.Action.BUY) ? 1 : -1;
            if (!activeFutLiveOrder.containsKey(order.lmtPrice())) {
                activeFutLiveOrder.put(order.lmtPrice(), sign * order.totalQuantity());
            } else {
                activeFutLiveOrder.put(order.lmtPrice(),
                        activeFutLiveOrder.get(order.lmtPrice()) + sign * order.totalQuantity());
            }
        } else {
            out.println(" contract not equal to activefuture ");
        }
    }

    @Override
    public void openOrderEnd() {
        //pr(" open order eneded");
    }

    @Override
    public void orderStatus(int orderId, OrderStatus status, int filled, int remaining,
                            double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        pr(" in order status ");
        updateLog(Utility.str(" status filled remaining avgFillPrice ",
                status, filled, remaining, avgFillPrice));

        if (status.equals(OrderStatus.Filled)) {
            createDialog(Utility.str(" status filled remaining avgFillPrice ",
                    status, filled, remaining, avgFillPrice));
        }
    }

    @Override
    public void handle(int orderId, int errorCode, String errorMsg) {
        if (errorCode != 504 || LocalTime.now().getSecond() < 5) {
            updateLog(" handle error code " + errorCode + " message " + errorMsg);
        }
    }

    // position
    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        String ticker = utility.Utility.ibContractToSymbol(contract);
        if (contract.symbol().equals("XINA50") && position != 0.0) {
            FutType f = ibContractToFutType(contract);
            currentPosMap.put(f, (int) position);
        }

        SwingUtilities.invokeLater(() -> {
            if (ticker.equals("SGXA50")) {
                XUTrader.outputArea.repaint();
            }
        });
    }

    @Override
    public void positionEnd() {
    }

    // connection
    @Override
    public void connected() {
        out.println("connected in XUconnectionhandler");
        connectionStatus = true;
        connectionLabel.setText(Boolean.toString(XUTrader.connectionStatus));
        apcon.setConnectionStatus(true);
    }

    @Override
    public void disconnected() {
        out.println("disconnected in XUConnectionHandler");
        connectionStatus = false;
        connectionLabel.setText(Boolean.toString(connectionStatus));
    }

    @Override
    public void accountList(ArrayList<String> list) {
        out.println(" account list is " + list);
    }

    @Override
    public void error(Exception e) {
        out.println(" error in XUConnectionHandler");
        e.printStackTrace();
    }

    @Override
    public void message(int id, int errorCode, String errorMsg) {
        out.println(" error ID " + id + " error code " + errorCode + " errormsg " + errorMsg);
    }

    @Override
    public void show(String string) {
        out.println(" show string " + string);
    }

    private void requestLevel2Data() {
        apcon.reqDeepMktData(activeFuture, 10, this);
    }

    private void requestExecHistory() {
        uniqueTradeKeySet = new HashSet<>();
        tradesMap.replaceAll((k, v) -> new ConcurrentSkipListMap<>());
        apcon.reqExecutions(new ExecutionFilter(), this);
    }

    private void requestOvernightExecHistory() {
        overnightTradesMap.replaceAll((k, v) -> new ConcurrentSkipListMap<>());
        apcon.reqExecutions(new ExecutionFilter(), XUOvernightTradeExecHandler.DefaultOvernightHandler);
    }

    private void requestXUData() {
        getAPICon().reqXUDataArray();
    }

    @SuppressWarnings("unused")
    static double getNetPnlForAllFuts() {
        return Arrays.stream(FutType.values()).mapToDouble(XUTrader::getNetPnlFor1Fut).sum();
    }

    private static double getNetPnlFor1Fut(FutType f) {
        if (tradesMap.containsKey(f) && tradesMap.get(f).size() > 0) {
            return tradesMap.get(f).entrySet().stream()
                    .mapToDouble(e -> e.getValue().getSizeAll() * futPriceMap.getOrDefault(f, 0.0)
                            + e.getValue().getCostBasisAll(f.getTicker())).sum();
        }
        return 0.0;
    }

    private static void processTradeMapActive() {
        FutType f = ibContractToFutType(activeFuture);
        int unitsBought = tradesMap.get(f).entrySet().stream().mapToInt(e -> e.getValue().getSizeBot()).sum();
        int unitsSold = tradesMap.get(f).entrySet().stream().mapToInt(e -> e.getValue().getSizeSold()).sum();

        botMap.put(f, unitsBought);
        soldMap.put(f, unitsSold);

        double avgBuy = Math.abs(Math.round(100d * (tradesMap.get(f).entrySet().stream()
                .mapToDouble(e -> e.getValue().getCostBasisAllPositive("")).sum() / unitsBought)) / 100d);
        double avgSell = Math.abs(Math.round(100d * (tradesMap.get(f).entrySet().stream()
                .mapToDouble(e -> e.getValue().getCostBasisAllNegative("")).sum() / unitsSold)) / 100d);
        double buyTradePnl = Math.round(100d * (futPriceMap.get(f) - avgBuy) * unitsBought) / 100d;
        double sellTradePnl = Math.round(100d * (futPriceMap.get(f) - avgSell) * unitsSold) / 100d;
        double netTradePnl = buyTradePnl + sellTradePnl;
        double netTotalCommissions = Math.round(100d * ((unitsBought - unitsSold) * 1.505d)) / 100d;
        double mtmPnl = (currentPosMap.get(f) - unitsBought - unitsSold) * (futPriceMap.get(f) - futPrevCloseMap.get(f));
        SwingUtilities.invokeLater(() -> {
            updateLog(" P " + futPriceMap.get(f));
            updateLog(" Close " + futPrevCloseMap.get(f));
            updateLog(" Open " + futOpenMap.get(f));
            updateLog(" Chg " + (Math.round(10000d * (futPriceMap.get(f) / futPrevCloseMap.get(f) - 1)) / 100d) + " %");
            updateLog(" Open Pos " + (currentPosMap.get(f) - unitsBought - unitsSold));
            updateLog(" MTM " + mtmPnl);
            updateLog(" units bot " + unitsBought);
            updateLog(" avg buy " + avgBuy);
            updateLog(" units sold " + unitsSold);
            updateLog(" avg sell " + avgSell);
            updateLog(" buy pnl " + buyTradePnl);
            updateLog(" sell pnl " + sellTradePnl);
            updateLog(" net pnl " + r(netTradePnl));
            updateLog(" net commission " + netTotalCommissions);
            updateLog(" MTM + Trade " + r(netTradePnl + mtmPnl));
            updateLog("pos "
                    + currentPosMap.getOrDefault(f, 0) + " Delta " + r(ChinaPosition.getNetPtfDelta()) +
                    " Stock Delta " + r(ChinaPosition.getStockPtfDelta()) + " Fut Delta " + r(XUTrader.getFutDelta()));
            updateLog(" expiring delta " + getExpiringDelta());
        });
    }

    public static void main(String[] args) {
        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1000, 1000));

        ApiController ap = new ApiController(new XuTraderHelper.XUConnectionHandler(),
                new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());

        ap.connect("127.0.0.1", TradingConstants.PORT_IBAPI, 0, "");

        ap.client().reqAccountSummary(5, "All", "AccountType, NetLiquidation" +
                ",TotalCashValue,SettledCash,");

    }
}

