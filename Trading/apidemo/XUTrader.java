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

    public static ApiController apcon;

    //global
    private static AtomicBoolean musicOn = new AtomicBoolean(true);
    private static volatile MASentiment sentiment = MASentiment.Directionless;
    private static LocalDateTime lastTradeTime = LocalDateTime.now();
    static final int MAX_FUT_LIMIT = 20;
    static volatile AtomicBoolean canLongGlobal = new AtomicBoolean(true);
    static volatile AtomicBoolean canShortGlobal = new AtomicBoolean(true);
    private static volatile AtomicInteger autoTradeID = new AtomicInteger(100);
    public static volatile NavigableMap<Integer, OrderAugmented> globalIdOrderMap = new ConcurrentSkipListMap<>();

    //flatten drift trader
    private static final double FLATTEN_THRESH = 200000.0;
    private static final double DELTA_HIGH_LIMIT = 300000.0;
    private static final double DELTA_LOW_LIMIT = -300000.0;

    private static final double ABS_DELTA_TARGET = 100000.0;
    private static final double BULLISH_DELTA_TARGET = 100000.0;
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
    private static final int inv_trade_quantity = 1;
    private static AtomicBoolean inventoryTraderOn = new AtomicBoolean(false);

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
    private static AtomicBoolean detailedMA = new AtomicBoolean(false);

    //display
    public static volatile Predicate<LocalDateTime> displayPred = e -> true;

    private final static Contract frontFut = utility.Utility.getFrontFutContract();
    private final static Contract backFut = utility.Utility.getBackFutContract();

    @SuppressWarnings("unused")
    private static Predicate<? super Map.Entry<FutType, ?>> graphPred = e -> true;
    static volatile Contract activeFuture = frontFut;
    public static volatile DisplayGranularity gran = DisplayGranularity._5MDATA;
    public static volatile Map<Double, Double> activeFutLiveOrder = new HashMap<>();
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

//            out.println(str(" last minute map ", "map size # ",
//                    activeLastMinuteMap.size()
//                    , activeLastMinuteMap.lowerEntry(activeLastMinuteMap.lastKey()).getKey().toLocalTime()
//                            .truncatedTo(ChronoUnit.SECONDS)
//                    , activeLastMinuteMap.lowerEntry(activeLastMinuteMap.lastKey()).getValue()
//                    , activeLastMinuteMap.lastEntry().getKey().toLocalTime()
//                            .truncatedTo(ChronoUnit.SECONDS)
//                    , activeLastMinuteMap.lastEntry().getValue()
//                    , " Lapsed: ", milliLapsed
//                    , lastV == secLastV ? "FLAT" : (lastV < secLastV ? "DOWN" : "UP")
//                    , (lastV - secLastV)));
        } else {
            out.println(str(" last minute map ", activeLastMinuteMap));
        }
    }

    public static double getBullishTarget() {
        if (LocalTime.now().isAfter(LocalTime.of(8, 59)) && LocalTime.now().isBefore(LocalTime.of(12, 0))) {
            return BULLISH_DELTA_TARGET / 2;
        }
        return BULLISH_DELTA_TARGET;

    }

    public static double getBearishTarget() {
        if (LocalTime.now().isAfter(LocalTime.of(13, 0)) && LocalTime.now().isBefore(LocalTime.of(15, 1))) {
            return 0;
        }
        return BEARISH_DELTA_TARGET;

    }

    public static double getDeltaHighLimit() {
        if (LocalTime.now().isAfter(LocalTime.of(8, 59)) && LocalTime.now().isBefore(LocalTime.of(12, 0))) {
            return DELTA_HIGH_LIMIT / 2;
        }
        return DELTA_HIGH_LIMIT;
    }

    public static double getDeltaLowLimit() {
        if (LocalTime.now().isAfter(LocalTime.of(13, 0)) && LocalTime.now().isBefore(LocalTime.of(15, 1))) {
            return 0;
        }
        return DELTA_LOW_LIMIT;
    }


    private static void maTradeAnalysis() {
        //System.out.println(" ma trade analysis ");
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
            //System.out.println(" MA analysis: ");
            //maIdeasSet.forEach(System.out::println);
            //System.out.println(" compute MA margin ");
            //computeMAProfit(maIdeasSet, lastPrice);

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
            //computed from maAnalysis, persistent through sessions.
//            long maSignalsPersist = maIdeasSet.stream().filter(e -> e.getIdeaTime().isAfter(sessionBeginLDT)).count();
//            String maOutput = (str("MA signals persist || BeginT: ", sessionBeginLDT,
//                    "||Last Order Time: ", lastMAOrderTime, "||Signal #: ", maSignalsPersist, "||list: ", maIdeasSet));
//            outputToAutoLog(maOutput);
        }
    }

    public XUTrader getThis() {
        return this;
    }

    XUTrader(ApiController ap) {
        out.println(str(" front fut ", frontFut));
        out.println(str(" back fut ", backFut));

        for (FutType f : FutType.values()) {
            futData.put(f, new ConcurrentSkipListMap<>());
            tradesMap.put(f, new ConcurrentSkipListMap<>());
            overnightTradesMap.put(f, new ConcurrentSkipListMap<>());
            futOpenMap.put(f, 0.0);
            futPrevCloseMap.put(f, 0.0);
        }

        apcon = ap;
        JLabel currTimeLabel = new JLabel(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        currTimeLabel.setFont(currTimeLabel.getFont().deriveFont(30F));

        JButton bidLimitButton = new JButton("Buy Limit");

        bidLimitButton.addActionListener(l -> {
            out.println(" buying limit ");
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimit(bidMap.get(ibContractToFutType(activeFuture)), 1.0);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES), o.orderId(), " Bidding Limit ",
                    globalIdOrderMap.get(id)));
        });

        JButton offerLimitButton = new JButton("Sell Limit");

        offerLimitButton.addActionListener(l -> {
            out.println(" selling limit ");
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimit(askMap.get(ibContractToFutType(activeFuture)), 1.0);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES), o.orderId(), " Offer Limit ",
                    globalIdOrderMap.get(id)));
        });

        JButton buyOfferButton = new JButton(" Buy Now");
        buyOfferButton.addActionListener(l -> {
            out.println(" buy offer ");
            int id = autoTradeID.incrementAndGet();
            Order o = buyAtOffer(askMap.get(ibContractToFutType(activeFuture)), 1.0);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES), o.orderId(), " Lift Offer ",
                    globalIdOrderMap.get(id)));
        });

        JButton sellBidButton = new JButton(" Sell Now");
        sellBidButton.addActionListener(l -> {
            out.println(" sell bid ");
            int id = autoTradeID.incrementAndGet();
            Order o = sellAtBid(bidMap.get(ibContractToFutType(activeFuture)), 1.0);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                    , o.orderId(), " Hitting bid ", globalIdOrderMap.get(id)));
        });

        JButton toggleMusicButton = new JButton("停乐");
        toggleMusicButton.addActionListener(l -> soundPlayer.stopIfPlaying());

        JButton detailedMAButton = new JButton("Detailed MA: False");
        detailedMAButton.addActionListener(l -> {
            detailedMA.set(!detailedMA.get());
            detailedMAButton.setText(" Detailed MA: " + detailedMA.get());
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

        JButton inventoryTraderButton = new JButton("Inventory Trader: " + (inventoryTraderOn.get() ? "ON" : "OFF"));
        inventoryTraderButton.addActionListener(l -> {
            inventoryTraderOn.set(!inventoryTraderOn.get());
            outputToAutoLog(" inventory trader set to " + inventoryTraderOn.get());
            inventoryTraderButton.setText("Inventory Trader: " + (inventoryTraderOn.get() ? "ON" : "OFF"));
        });

        JButton percTraderButton = new JButton("Perc Trader: " + (percentileTradeOn.get() ? "ON" : "OFF"));
        percTraderButton.addActionListener(l -> {
            percentileTradeOn.set(!percentileTradeOn.get());
            outputToAutoLog(" percentile trader set to " + percentileTradeOn.get());
            percTraderButton.setText("Perc Trader: " + (percentileTradeOn.get() ? "ON" : "OFF"));
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

                globalIdOrderMap.entrySet().stream()
                        .filter(e -> isInventoryTrade().test(e.getValue().getTradeType()))
                        .forEach(e -> pr(str("real order ID"
                                , e.getValue().getOrder().orderId(), e.getValue())));

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
                            timeDiffinMinutes(o.getOrderTime(), LocalDateTime.now())
                                    >= cancelWaitTime(LocalTime.now())) {

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

//            ses.scheduleAtFixedRate(() -> {
//                ChinaPosition.getNetPtfDelta();
//                ChinaPosition.getStockPtfDelta();
//                //pr("fut delta ", getFutDelta());
//            }, 0, 10, TimeUnit.SECONDS);
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
                displayPred = e -> e.toLocalDate().equals(LocalDate.now()) && e.toLocalTime().isAfter(LocalTime.of(8, 59));
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
        frontFutButton.setSelected(true);
        JRadioButton backFutButton = new JRadioButton("Back");
        backFutButton.addActionListener(l -> {
            graphPred = e -> e.getKey().equals(FutType.BackFut);
            activeFuture = backFut;
        });

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
        controlPanel1.add(detailedMAButton);
        controlPanel1.add(maTraderStatusButton);
        controlPanel1.add(overnightButton);
        controlPanel1.add(musicPlayableButton);
        controlPanel1.add(inventoryTraderButton);
        controlPanel1.add(percTraderButton);

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
                        outputOrderToAutoLog(str(" MANUAL BID || bid price ", bidPrice, " Checking order ",
                                checkIfOrderPriceMakeSense(bidPrice)));
                        if (checkIfOrderPriceMakeSense(bidPrice) && futMarketOpen(LocalTime.now())) {
                            apcon.placeOrModifyOrder(activeFuture, placeBidLimit(bidPrice, 1.0), getThis());
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
                        outputOrderToAutoLog(str(" MANUAL OFFER || offer price ", offerPrice, " Checking order ",
                                checkIfOrderPriceMakeSense(offerPrice)));
                        if (checkIfOrderPriceMakeSense(offerPrice) && futMarketOpen(LocalTime.now())) {
                            apcon.placeOrModifyOrder(activeFuture, placeOfferLimit(offerPrice, 1.0), getThis());
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

    static double getFutDelta() {
        return currentPosMap.entrySet().stream()
                .mapToDouble(e -> e.getValue() * futPriceMap.getOrDefault(e.getKey(), SinaStock.FTSE_OPEN)
                        * ChinaPosition.fxMap.getOrDefault(e.getKey().getTicker(), 1.0))
                .sum();
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
        if (perc > 80 && dir == Direction.Long) {
            return 0;
        } else if (perc < 20 && dir == Direction.Short) {
            return 0;
        }

        if (pd > PD_UP_THRESH || perc > 80) {
            factor = dir == Direction.Long ? 1 : 2;
        } else if (pd < PD_DOWN_THRESH || perc < 20) {
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
    private static int getPercTraderSize(double price, double fx, MASentiment senti, Direction d, double currDelta) {
        int candidate = 1;
        if (senti == MASentiment.Directionless || d == Direction.Flat) return 1;
        double target = (senti == MASentiment.Bullish ? getBullishTarget() : getBearishTarget());

        if (d == Direction.Long) {
            if (currDelta < target) {
                candidate = (int) Math.floor((target - currDelta) / (price * fx));
            }
        } else if (d == Direction.Short) {
            if (currDelta > target) {
                candidate = (int) Math.floor((currDelta - target) / (price * fx));
            }
        }
        pr("price", price, "fx", fx, "senti", senti, "dir", d, "currDel", currDelta,
                "bull bear targets", getBullishTarget(), getBearishTarget(), "candidate ", candidate);
        return Math.max(0, Math.min(candidate, 3));
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
            System.out.println(" perc 0 suspicious " + futData.get(ibContractToFutType(activeFuture)));
        }
        if (futData.get(ibContractToFutType(activeFuture)).size() == 0) return;

        LocalTime now = nowMilli.toLocalTime();

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

        System.out.println(str("perc Trader status?", percentileTradeOn.get() ? "ON" : "OFF",
                "T: ", nowMilli.toLocalTime().truncatedTo(ChronoUnit.SECONDS),
                "perc: ", perc,
                "accSize, deccSize, netSize", accSize, deccSize, netPercTrades,
                "OrderT Trade T,next tradeT",
                lastPercOrderT.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                lastPercTradeT.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                lastPercOrderT.plusMinutes(minBetweenPercOrders).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                , "accAvg, DecAvg,", avgAccprice, avgDeccprice, "CurrDelta: ", r(currDelta), "pd", r10000(pd)));

        //******************************************************************************************//
        //if (!(now.isAfter(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(15, 0)))) return;
        if (!percentileTradeOn.get()) return;
        if (currStockDelta == 0) {
            pr(" stock delta 0 , returning ");
            return;
        }
        //*****************************************************************************************//
        if (timeDiffinMinutes(lastPercOrderT, nowMilli) >= minBetweenPercOrders) {
            if (perc < 30) {
                if (currDelta < getBullishTarget()) {
                    int id = autoTradeID.incrementAndGet();
                    int buySize = getPercTraderSize(freshPrice, fx, sentiment, Direction.Long, currDelta
                    );
                    if (buySize > 0) {
                        Order o = placeBidLimit(freshPrice, buySize);
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Perc bid",
                                PERC_ACC));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        outputOrderToAutoLog(str(o.orderId(), "perc bid", globalIdOrderMap.get(id), " perc ", perc));
                    } else {
                        throw new IllegalStateException(" perc buy size < 0 ");
                    }
                } else {
                    outputToAutoLog(" perc: delta above bullish target ");
                }
            } else if (perc > 70) {
                if (currDelta > getBearishTarget()) {
                    int id = autoTradeID.incrementAndGet();
                    int sellSize = getPercTraderSize(freshPrice, fx, sentiment, Direction.Short, currDelta);
                    if (sellSize > 0) {
                        Order o = placeOfferLimit(freshPrice, sellSize);
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Perc offer",
                                AutoOrderType.PERC_DECC));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        outputOrderToAutoLog(str(o.orderId(), "perc offer", globalIdOrderMap.get(id), "perc", perc));
                    } else {
                        throw new IllegalStateException(" perc sell size < 0 ");
                    }
                } else {
                    outputToAutoLog("perc: delta below bearish target ");
                }
            } else {
                if (currDelta > getDeltaHighLimit() && pd > PD_DOWN_THRESH && perc > 50) {
                    if (freshPrice > avgAccprice || accSize == 0) {
                        int id = autoTradeID.incrementAndGet();
                        Order o = placeOfferLimit(freshPrice, 1);
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Perc offer COVER", PERC_DECC));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        outputOrderToAutoLog(str(o.orderId(), "perc offer,COVER", globalIdOrderMap.get(id)
                                , "perc: ", perc));
                    }
                } else if (currDelta < getDeltaLowLimit() && pd < PD_UP_THRESH && perc < 50) {
                    if (freshPrice < avgDeccprice || deccSize == 0) {
                        int id = autoTradeID.incrementAndGet();
                        Order o = placeBidLimit(freshPrice, 1);
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Perc bid COVER", PERC_ACC));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        outputOrderToAutoLog(str(o.orderId(), "perc bid, COVER", globalIdOrderMap.get(id),
                                "perc: ", perc));
                    }
                }
            }
        }
    }


    /**
     * flatten delta aggressively at bid/offer
     */
    public static synchronized void flattenAggressively() {
        LocalDateTime nowMilli = LocalDateTime.now();
        double fx = ChinaPosition.fxMap.get("SGXA50");
        double currDelta = ChinaPosition.getNetPtfDelta();
        pr(" In Flattening aggressively ");
        if (currDelta < BULLISH_DELTA_TARGET && currDelta > BEARISH_DELTA_TARGET) {
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

        if (currDelta < BULLISH_DELTA_TARGET && currDelta > BEARISH_DELTA_TARGET) {
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

        if (currDelta > BULLISH_DELTA_TARGET && prevPrice > maLast && freshPrice <= maLast
                && perc > 70 && pd > PD_DOWN_THRESH) { //no sell at discount or at bottom
            int id = autoTradeID.incrementAndGet();

            double candidatePrice = (flattenEagerness == Eagerness.Passive) ? freshPrice :
                    bidMap.get(ibContractToFutType(activeFuture));

            Order o = placeOfferLimitTIF(candidatePrice, sizeToFlatten(freshPrice, fx, currDelta),
                    Types.TimeInForce.IOC);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Sell", FLATTEN));
            outputOrderToAutoLog(str(o.orderId(), " Sell Flatten ", globalIdOrderMap.get(id)));
        } else if (currDelta < BEARISH_DELTA_TARGET && prevPrice < maLast && freshPrice >= maLast
                && perc < 30 && pd < PD_UP_THRESH) { // no buy at premium or at top
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

        if (detailedMA.get()) {
            out.println(str(" Detailed MA ON",
                    "pd", Math.round(10000d * pd) / 10000d,
                    "freshPrice", freshPrice,
                    "prev Price", prevPrice,
                    "last bar", lastBar,
                    "SMA", r(sma.lastEntry().getValue()))); //"activeLastMap: ", activeLastMinuteMap)
        }

        double maLast = sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;
        sentiment = freshPrice > maLast ? MASentiment.Bullish : MASentiment.Bearish;
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
                    outputOrderToAutoLog(str(nowMilli.truncatedTo(ChronoUnit.MINUTES), id, o.orderId(),
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
                    outputOrderToAutoLog(str(nowMilli.truncatedTo(ChronoUnit.MINUTES), id, o.orderId(),
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
        double currDelta = ChinaPosition.getNetPtfDelta();

        int numTrades = 0;
        double candidatePrice = 0.0;
        String priceType;
        int percentile = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));

        double indexPrice = (ChinaData.priceMapBar.containsKey(ftseIndex) &&
                ChinaData.priceMapBar.get(ftseIndex).size() > 0) ?
                ChinaData.priceMapBar.get(ftseIndex).lastEntry().getValue().getClose() : SinaStock.FTSE_OPEN;
        double pd = (indexPrice != 0.0 && freshPrice != 0.0) ? (freshPrice / indexPrice - 1) : 0.0;

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
                        && currDelta < getDeltaHighLimit() && percentile < 30) {
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

                    Order o = placeBidLimit(candidatePrice, trimProposedPosition(
                            determinePDPercFactor(nowMilli.toLocalTime(), pd, Direction.Long,
                                    percentile) * determineTimeDiffFactor(), currPos));
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        //apcon.cancelAllOrders();
                        maOrderMap.put(nowMilli, o);
                        maSignals.incrementAndGet();
                        int id = autoTradeID.incrementAndGet();
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "MA Trade bid", MA));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        lastMAOrderTime = LocalDateTime.now();
                        currentDirection = Direction.Long;
                        outputOrderToAutoLog(str(nowMilli.truncatedTo(ChronoUnit.MINUTES)
                                , id, "MA ORDER || bidding @ ", o.toString(), "type", priceType));
                    }
                } else if (bearishTouchMet(secLastBar, lastBar, maLast) && canShortGlobal.get()
                        && currDelta > getDeltaLowLimit() && percentile > 70) {
                    if (currentDirection == Direction.Short || pd < PD_DOWN_THRESH) {
                        candidatePrice = roundToXUPricePassive(maLast, Direction.Short);
                        priceType = (pd < PD_DOWN_THRESH ? "pd < " + PD_DOWN_THRESH : "currDir is short(same)") + " SELL @ MA";
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
                    Order o = placeOfferLimit(candidatePrice,
                            trimProposedPosition(determinePDPercFactor(nowMilli.toLocalTime(), pd,
                                    Direction.Short, percentile) * determineTimeDiffFactor(), currPos));
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        maOrderMap.put(nowMilli, o);
                        maSignals.incrementAndGet();
                        //apcon.cancelAllOrders();
                        int id = autoTradeID.incrementAndGet();
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "MA Trade offer", MA));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        lastMAOrderTime = LocalDateTime.now();
                        currentDirection = Direction.Short;
                        outputOrderToAutoLog(str(nowMilli.truncatedTo(ChronoUnit.MINUTES)
                                , id, "MA ORDER || offering @ ", o.toString(), priceType));
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

//        NavigableMap<LocalDateTime, SimpleBar> filteredPriceMap = futPriceMap.entrySet().stream()
//                .filter(e -> e.getKey().isAfter(ytdCloseTime))
//                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
//                        (a, b) -> a, ConcurrentSkipListMap::new));

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
                        && currPercentile > 70) {
                    double candidatePrice = askMap.getOrDefault(ibContractToFutType((activeFuture)), 0.0);
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        int id = autoTradeID.incrementAndGet();
                        Order o = placeOfferLimit(candidatePrice, trimProposedPosition(
                                1, currPos));
                        globalIdOrderMap.put(id, new OrderAugmented(now, o,
                                "Overnight Short", OVERNIGHT));
                        outputOrderToAutoLog(str(now, id, "O/N placing sell order @ ", candidatePrice,
                                " curr p% ", currPercentile, "curr PD: ", pd));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        overnightClosingOrders.incrementAndGet();
                    }
                } else if (pd < PD_DOWN_THRESH && canLongGlobal.get() && currDelta < getDeltaHighLimit()
                        && currPercentile < 30) {
                    double candidatePrice = bidMap.getOrDefault(ibContractToFutType(activeFuture), 0.0);
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        int id = autoTradeID.incrementAndGet();
                        Order o = placeBidLimit(candidatePrice, trimProposedPosition(1, currPos));
                        globalIdOrderMap.put(id, new OrderAugmented(now, o, "Overnight Long", OVERNIGHT));
                        outputOrderToAutoLog(str(now, id, "O/N placing buy order @ ", candidatePrice,
                                " curr p% ", currPercentile, " curr PD: ", pd));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
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
                activeLastMinuteMap.size() < 2 ? "No trade last min " : (freshPrice -
                        activeLastMinuteMap.lowerEntry(t).getValue()),
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

        if (perc < 30) {
            try {
                inventorySemaphore.acquire();
                pr(" acquired semaphore now left:" + inventorySemaphore.availablePermits());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            CountDownLatch latchBuy = new CountDownLatch(1);
            int idBuy = autoTradeID.incrementAndGet();
            Order buyO = placeBidLimit(freshPrice - margin, inv_trade_quantity);
            globalIdOrderMap.put(idBuy, new OrderAugmented(LocalDateTime.now()
                    , buyO, "Inventory Buy Open", INVENTORY_OPEN));
            apcon.placeOrModifyOrder(activeFuture, buyO, new InventoryOrderHandler(idBuy, latchBuy, inventoryBarrier));
            outputOrderToAutoLog(str(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                    , idBuy, buyO.orderId(), "Inventory Buy Open ", globalIdOrderMap.get(idBuy)));

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
            Order sellO = placeOfferLimit(freshPrice + margin, inv_trade_quantity);
            globalIdOrderMap.put(idSell, new OrderAugmented(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                    , sellO, "Inventory Sell Close", INVENTORY_CLOSE));
            apcon.placeOrModifyOrder(activeFuture, sellO, new InventoryOrderHandler(idSell, latchSell, inventoryBarrier));
            outputOrderToAutoLog(str(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                    , idSell, sellO.orderId(), "Inventory Sell Close ", globalIdOrderMap.get(idSell)));

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
        } else if (perc > 70) {
            try {
                inventorySemaphore.acquire();
                out.println(" acquired semaphore, now left: " + inventorySemaphore.availablePermits());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            CountDownLatch latchSell = new CountDownLatch(1);
            int idSell = autoTradeID.incrementAndGet();
            Order sellO = placeOfferLimit(freshPrice + margin, inv_trade_quantity);
            globalIdOrderMap.put(idSell,
                    new OrderAugmented(LocalDateTime.now(), sellO, "Inventory Sell Open", INVENTORY_OPEN));
            apcon.placeOrModifyOrder(activeFuture, sellO, new InventoryOrderHandler(idSell, latchSell, inventoryBarrier));
            outputOrderToAutoLog(str(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES), idSell, sellO.orderId()
                    , "Inventory Sell Open", globalIdOrderMap.get(idSell)));

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
            Order buyO = placeBidLimit(freshPrice - margin, inv_trade_quantity);
            globalIdOrderMap.put(idBuy, new OrderAugmented(LocalDateTime.now(), buyO, "Inventory Buy Close",
                    INVENTORY_CLOSE));
            apcon.placeOrModifyOrder(activeFuture, buyO, new InventoryOrderHandler(idBuy, latchBuy, inventoryBarrier));
            outputOrderToAutoLog(str(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES), idBuy, buyO.orderId()
                    , "Inv Buy Close", globalIdOrderMap.get(idBuy)));

            try {
                latchBuy.await();
                if (inventoryBarrier.getNumberWaiting() == 2) {
                    inventoryBarrier.reset();
                    out.println(" reset inventory barrier ");
                }
                inventorySemaphore.release();
                out.println(" released inventory semaphore, now "
                        + inventorySemaphore.availablePermits());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        out.println(" exiting inventory order checking not stuck ");
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

        System.out.println(str("FLATTEN DRIFT TRADER current delta is ", currentDelta,
                " ma fx fresh", malast, fx, freshPrice, "sentiment ", sentiment, "unfilled ", unfilled_trades));

        if (currentDelta == 0.0 || malast == 0.0 || fx == 0.0) return;

        if (sentiment == MASentiment.Bullish && pd <= 0.0) {
            if (currentDelta < 0.0 && Math.abs(currentDelta) > FLATTEN_THRESH) {
                int size = (int) Math.ceil(Math.abs(currentDelta / (fx * freshPrice)));
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(roundToXUPricePassive(malast, Direction.Long), size);
                globalIdOrderMap.put(id, new OrderAugmented(t, o, "Bull: Buy to Flatten ", FLATTEN));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(t, id, o.orderId(), "Bull: Buy to Flatten "));
            } else {
                if (currentDelta < BULLISH_DELTA_TARGET) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeBidLimit(roundToXUPricePassive(malast, Direction.Long), 1.0);
                    globalIdOrderMap.put(id, new OrderAugmented(t, o, "Bullish: DRIFT BUY 1", AutoOrderType.DRIFT));
                    apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                    outputOrderToAutoLog(str(t, id, o.orderId(), "Bullish: DRIFT BUY 1"));
                }
            }
        } else if (sentiment == MASentiment.Bearish && pd >= 0.0) {
            if (currentDelta > 0.0 && Math.abs(currentDelta) > FLATTEN_THRESH) {
                int size = (int) Math.ceil(Math.abs(currentDelta / (fx * freshPrice)));
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(roundToXUPricePassive(malast, Direction.Short), size);
                globalIdOrderMap.put(id, new OrderAugmented(t, o, "Bearish: Sell to Flatten", FLATTEN));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(t, id, o.orderId(), "Bearish: Sell to Flatten"));
            } else { //drift
                if (currentDelta > BEARISH_DELTA_TARGET) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeOfferLimit(roundToXUPricePassive(malast, Direction.Short), 1.0);
                    globalIdOrderMap.put(id, new OrderAugmented(t, o, "Bearish: DRIFT SELL", DRIFT));
                    apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                    outputOrderToAutoLog(str(t, id, o.orderId(), "Bearish: DRIFT SELL"));
                }
            }
        }
    }

    public static boolean orderMakingMoney(Order o, double currPrice) {
        return o.lmtPrice() > currPrice && (o.totalQuantity() > 0);
    }

    private void loadXU() {
        pr("in loadXU");
        ChinaMain.GLOBAL_REQ_ID.addAndGet(5);
        apcon.getSGXA50Historical2(ChinaMain.GLOBAL_REQ_ID.get(), this);
        //apcon.getSGXA50Historical2(30000, this);
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

        if (contract.symbol().equalsIgnoreCase("SGXA50")) {
            pr(str(" exec ", execution.side(), execution.time(), execution.cumQty()
                    , execution.price(), execution.orderRef(), execution.orderId(),
                    execution.permId(), execution.shares()));
        }
        int sign = (execution.side().equals("BOT")) ? 1 : -1;
        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));

        int daysToGoBack = LocalDate.now().getDayOfWeek().equals(DayOfWeek.MONDAY) ? 4 : 2;
        if (ldt.toLocalDate().isAfter(LocalDate.now().minusDays(daysToGoBack))) {
            if (tradesMap.get(f).containsKey(ldt)) {
                tradesMap.get(f).get(ldt).addTrade(new FutureTrade(execution.price(), (int) Math.round(sign * execution.shares())));
            } else {
                tradesMap.get(f).put(ldt,
                        new TradeBlock(new FutureTrade(execution.price(), (int) Math.round(sign * execution.shares()))));
            }
        }
    }

    @Override
    public void tradeReportEnd() {
        System.out.println(" printing trades map " + tradesMap.get(ibContractToFutType(activeFuture)));
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
        updateLog(str(" status filled remaining avgFillPrice ",
                status, filled, remaining, avgFillPrice));
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
    }

    @Override
    public void orderStatus(int orderId, OrderStatus status, int filled, int remaining,
                            double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        out.println(" in order status ");
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
            updateLog(" MTM+Trade " + r(netTradePnl + mtmPnl));
            updateLog("pos " + currentPosMap.getOrDefault(f, 0) + " Delta " + r(ChinaPosition.getNetPtfDelta()));
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

