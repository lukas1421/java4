package apidemo;

import TradeType.FutureTrade;
import TradeType.TradeBlock;
import auxiliary.SimpleBar;
import client.*;
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
import java.util.stream.Collectors;

import static apidemo.ChinaData.priceMapBar;
import static apidemo.ChinaDataYesterday.ma20Map;
import static apidemo.ChinaPosition.*;
import static apidemo.ChinaStock.*;
import static apidemo.TradingConstants.*;
import static apidemo.XuTraderHelper.*;
import static java.time.temporal.ChronoUnit.MINUTES;
import static util.AutoOrderType.*;
import static utility.Utility.*;

public final class XUTrader extends JPanel implements HistoricalHandler, ApiController.IDeepMktDataHandler,
        ApiController.ITradeReportHandler, ApiController.IOrderHandler, ApiController.ILiveOrderHandler
        , ApiController.IPositionHandler, ApiController.IConnectionHandler {

    private static MASentiment _20DayMA = MASentiment.Directionless;
    public static volatile double currentIBNAV = 0.0;

    static final LocalDateTime ENGINE_START_TIME = XuTraderHelper.getEngineStartTime();
    private static volatile Set<String> uniqueTradeKeySet = new HashSet<>();
    static ApiController apcon;
    private static XUTraderRoll traderRoll;

    private static final int ORDER_WAIT_TIME = 15;
    private static final double DELTA_HARD_HI_LIMIT = 1000000.0;
    private static final double DELTA_HARD_LO_LIMIT = -1000000.0;

    //global
    private static AtomicBoolean globalTradingOn = new AtomicBoolean(false);
    private static AtomicBoolean musicOn = new AtomicBoolean(false);
    private static volatile MASentiment sentiment = MASentiment.Directionless;
    static final int MAX_FUT_LIMIT = 20;
    static volatile AtomicBoolean canLongGlobal = new AtomicBoolean(true);
    static volatile AtomicBoolean canShortGlobal = new AtomicBoolean(true);
    static volatile AtomicInteger autoTradeID = new AtomicInteger(100);
    public static volatile NavigableMap<Integer, OrderAugmented> globalIdOrderMap = new ConcurrentSkipListMap<>();
    private static final int UP_PERC = 95;
    private static final int DOWN_PERC = 5;
    private static final int UP_PERC_WIDE = 80;
    private static final int DOWN_PERC_WIDE = 20;

    //flatten drift trader
    private static final double FLATTEN_THRESH = 200000.0;
    private static final double DELTA_HIGH_LIMIT = 1000000.0;
    private static final double DELTA_LOW_LIMIT = -200000.0;

    private static final double BULLISH_DELTA_TARGET = 1000000.0;
    private static final double BEARISH_DELTA_TARGET = -200000.0;
    public static volatile Eagerness flattenEagerness = Eagerness.Passive;

    //UNCON_MA periods
//    private static final int MA5 = 5;
//    private static final int MA10 = 10;
//    private static final int MA20 = 20;
//    private static final int MA60 = 60;
//    private static final int MA80 = 80;

    public static volatile int _1_min_ma_short = 10;
    public static volatile int _1_min_ma_long = 20;
    public static volatile int _5_min_ma_short = 5;
    public static volatile int _5_min_ma_long = 10;

    //size
    public static final int CONSERVATIVE_SIZE = 1;
    public static final int AGGRESSIVE_SIZE = 3;


    //perc trader
    private static volatile AtomicBoolean percentileTradeOn = new AtomicBoolean(false);

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

    //index ma
    private static AtomicBoolean indexMAStatus = new AtomicBoolean(false);

    //ma
    private static AtomicBoolean MATraderStatus = new AtomicBoolean(true);
    private static volatile int shortMAPeriod = 60;
    private static volatile int longMAPeriod = 80;

    private static Direction currentDirection = Direction.Flat;
    private static final double PD_UP_THRESH = 0.003;
    private static final double PD_DOWN_THRESH = -0.003;

    //open/fast trading
    private static LocalDateTime lastFastOrderTime = LocalDateTime.now();
    private static AtomicInteger fastTradeSignals = new AtomicInteger(0);
    private static NavigableMap<LocalDateTime, Order> fastOrderMap = new ConcurrentSkipListMap<>();
    private static final long MAX_OPEN_TRADE_ORDERS = 10;

    //music
    private static EmbeddedSoundPlayer soundPlayer = new EmbeddedSoundPlayer();

    //detailed UNCON_MA
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
    public static volatile EnumMap<FutType, NavigableMap<LocalDateTime, SimpleBar>> futData
            = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> currentPosMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> botMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> soldMap = new EnumMap<>(FutType.class);

    public static volatile AtomicBoolean showTrades = new AtomicBoolean(false);
    static volatile boolean connectionStatus = false;
    static volatile JLabel connectionLabel = new JLabel();

    XUTrader(ApiController ap) {
        pr(str(" ****** front fut ******* ", frontFut));
        pr(str(" ****** back fut ******* ", backFut));

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
            pr(" buying limit ");
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
            pr(" buy offer ");
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

//        JButton maTraderStatusButton = new JButton("MA Trader: " + (MATraderStatus.get() ? "ON" : "OFF"));
//        maTraderStatusButton.addActionListener(l -> {
//            MATraderStatus.set(!MATraderStatus.get());
//            outputToAutoLog(" MA Trade set to " + MATraderStatus.get());
//            maTraderStatusButton.setText("MA Trader " + (MATraderStatus.get() ? "ON" : "OFF"));
//        });

//        JButton indexMAStatusButton = new JButton("IndexMA " + (indexMAStatus.get() ? "ON" : "OFF"));
//        indexMAStatusButton.addActionListener(l -> {
//            indexMAStatus.set(!indexMAStatus.get());
//            outputToAutoLog(" Index UNCON_MA set to " + indexMAStatus.get());
//            indexMAStatusButton.setText(" Index UNCON_MA " + (indexMAStatus.get() ? "ON" : "OFF"));
//        });


        JButton musicPlayableButton = new JButton("Music: " + (musicOn.get() ? "ON" : "OFF"));
        musicPlayableButton.addActionListener(l -> {
            musicOn.set(!musicOn.get());
            musicPlayableButton.setText("Music:" + (musicOn.get() ? "ON" : "OFF"));
        });

//        JButton inventoryTraderButton = new JButton("Inv Trader: " + (inventoryTraderOn.get() ? "ON" : "OFF"));
//        inventoryTraderButton.addActionListener(l -> {
//            inventoryTraderOn.set(!inventoryTraderOn.get());
//            outputToAutoLog(" inv trader set to " + inventoryTraderOn.get());
//            inventoryTraderButton.setText("Inv Trader: " + (inventoryTraderOn.get() ? "ON" : "OFF"));
//        });

//        JButton percTraderButton = new JButton("Perc Trader: " + (percentileTradeOn.get() ? "ON" : "OFF"));
//        percTraderButton.addActionListener(l -> {
//            percentileTradeOn.set(!percentileTradeOn.get());
//            outputToAutoLog(" percentile trader set to " + percentileTradeOn.get());
//            percTraderButton.setText("Perc Trader: " + (percentileTradeOn.get() ? "ON" : "OFF"));
//        });

//        JButton pdTraderButton = new JButton("PD Trader: " + (pdTraderOn.get() ? "ON" : "OFF"));
//        pdTraderButton.addActionListener(l -> {
//            pdTraderOn.set(!pdTraderOn.get());
//            outputToAutoLog(" PD Trader set to " + pdTraderOn.get());
//            pdTraderButton.setText("PD Trader: " + (pdTraderOn.get() ? "ON" : "OFF"));
//        });

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

        JToggleButton globalTradingButton = new JToggleButton("Trading:" + (globalTradingOn.get() ? "ON" : "OFF"));
        globalTradingButton.setSelected(false);
        globalTradingButton.addActionListener(l -> {
            globalTradingOn.set(globalTradingButton.isSelected());
            globalTradingButton.setText("Trading:" + (globalTradingOn.get() ? "ON" : "OFF"));
            pr(" global trading set to " + (globalTradingOn.get() ? "ON" : "OFF"));
        });

        JButton computeMAButton = new JButton("ComputeMA");
        computeMAButton.addActionListener(l -> computeMAStrategy());

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
                //maTradeAnalysis();
                requestExecHistory();
            }, 0, 1, TimeUnit.MINUTES);

            ses.scheduleAtFixedRate(() -> {
                //pr(" printing all inventory orders ");

                globalIdOrderMap.entrySet().stream().filter(e -> isInventoryTrade().test(e.getValue().getOrderType()))
                        .forEach(e -> pr(str("real order ID", e.getValue().getOrder().orderId(), e.getValue())));

                long invOrderCount = globalIdOrderMap.entrySet().stream()
                        .filter(e -> isInventoryTrade().test(e.getValue().getOrderType())).count();
                //outputToAutoLog(" inventory orders count " + invOrderCount);
                if (invOrderCount >= 1) {
                    OrderAugmented o = globalIdOrderMap.entrySet().stream()
                            .filter(e -> isInventoryTrade().test(e.getValue().getOrderType()))
                            .max(Comparator.comparing(e -> e.getValue().getOrderTime())).map(Map.Entry::getValue)
                            .orElseThrow(() -> new IllegalStateException(" nothing in last inventory order "));

                    pr("invOrderCount >=1 : last order ", o);
                    pr("last order T ", o.getOrderTime(), "status", o.getStatus()
                            , " Cancel wait time ", cancelWaitTime(LocalTime.now()));

                    if (o.getStatus() != OrderStatus.Filled &&
                            timeDiffinMinutes(o.getOrderTime(), LocalDateTime.now()) >= cancelWaitTime(LocalTime.now())) {

                        globalIdOrderMap.entrySet().stream()
                                .filter(e -> isInventoryTrade().test(e.getValue().getOrderType()))
                                .skip(invOrderCount - 1).peek(e -> pr(str("last order ", e.getValue())))
                                .forEach(e -> {
                                    if (e.getValue().getStatus() != OrderStatus.Cancelled) {
                                        apcon.cancelOrder(e.getValue().getOrder().orderId());
                                        e.getValue().setFinalActionTime(LocalDateTime.now());
                                        e.getValue().setStatus(OrderStatus.Cancelled);
                                    } else {
                                        pr(str(e.getValue().getOrder().orderId(), "already cancelled"));
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
            XUTrader.computeTradeMapActive();
        }, 0, 10, TimeUnit.SECONDS));

//        JButton overnightButton = new JButton("Overnight: " + (overnightTradeOn.get() ? "ON" : "OFF"));
//        overnightButton.addActionListener(l -> {
//            //overnightTradeOn.set(!overnightTradeOn.get());
//            //overnightButton.setText("Overnight: " + (overnightTradeOn.get() ? "ON" : "OFF"));
//            //ses2.scheduleAtFixedRate(this::overnightTrader, 0, 1, TimeUnit.MINUTES);
//        });

//        JButton maAnalysisButton = new JButton(" UNCON_MA Analysis ");
//        maAnalysisButton.addActionListener(l -> maTradeAnalysis());

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
        showGraphButton.addActionListener(l -> showTrades.set(showGraphButton.isSelected()));

        JButton cancelAllOrdersButton = new JButton("Cancel Orders");
        cancelAllOrdersButton.addActionListener(l -> {
            apcon.cancelAllOrders();
            inventorySemaphore = new Semaphore(1);
            inventoryBarrier.reset();
            pdSemaphore = new Semaphore(1);
            pdBarrier.reset();
            activeFutLiveOrder = new HashMap<>();
            activeFutLiveIDOrderMap = new HashMap<>();
            globalIdOrderMap.entrySet().stream().filter(e -> isInventoryTrade().test(e.getValue().getOrderType()))
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
        //controlPanel1.add(maTraderStatusButton);
        //controlPanel1.add(indexMAStatusButton);
        //controlPanel1.add(overnightButton);
        controlPanel1.add(musicPlayableButton);
        //controlPanel1.add(inventoryTraderButton);
        //controlPanel1.add(percTraderButton);
        //controlPanel1.add(dayTraderButton);
        //controlPanel1.add(pdTraderButton);
        //controlPanel1.add(trimDeltaButton);
        controlPanel1.add(rollButton);
        controlPanel1.add(globalTradingButton);
        controlPanel1.add(computeMAButton);

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
        //controlPanel2.add(maAnalysisButton);

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

    static void set20DayBullBear() {
        String ticker = "sh000001";
        if (ma20Map.getOrDefault(ticker, 0.0) == 0.0 || priceMap.getOrDefault(ticker, 0.0) == 0.0) {
            _20DayMA = MASentiment.Directionless;
        } else if (priceMap.get(ticker) < ma20Map.get(ticker)) {
            _20DayMA = MASentiment.Bearish;
        } else if (priceMap.get(ticker) > ma20Map.get(ticker)) {
            _20DayMA = MASentiment.Bullish;
        }
//        pr(" 20 dma ", _20DayMA, " ma20 ", ma20Map.getOrDefault(ticker, 0.0),
//                " price ", priceMap.getOrDefault(ticker, 0.0));
    }

    public static void processMain(LocalDateTime ldt, double price) {
        if (!globalTradingOn.get()) {
            pr(" global trading off ");
            return;
        }

        XUTrader.trimTrader(ldt, price);

        double currDelta = getNetPtfDelta();
        boolean maxAfterMin = checkf10maxAftermint(INDEX_000016);
        boolean maxAbovePrev = checkf10MaxAbovePrev(INDEX_000016);
        NavigableMap<LocalDateTime, SimpleBar> futdata = futData.get(ibContractToFutType(activeFuture));
        int pmChgY = getPercentileChgFut(futdata, futdata.firstKey().toLocalDate());
        int pmChg = getPercentileChgFut(futdata, getTradeDate(futdata.lastKey()));

        if (!(currDelta > DELTA_HARD_LO_LIMIT && currDelta < DELTA_HARD_HI_LIMIT)) {
            pr(" curr delta is outside range ");
            return;
        }

        if (detailedPrint.get()) {
            pr("20DayMA", _20DayMA, "maxAfterMin: ", maxAfterMin, "maxAbovePrev", maxAbovePrev,
                    "pmchgy", pmChgY, "pmch ", pmChg, "delta range ", getBearishTarget(), getBullishTarget());
        }

        if (isOvernight(ldt.toLocalTime())) {
            percentileMATrader(ldt, price, pmChg);
        } else {
            percentileMATrader(ldt, price, pmChgY);
        }

        if (pmChgY < 0) {
            //slowCoverTrader(ldt, price);
            if (maxAfterMin && maxAbovePrev) {
                //slowCoverTrader(ldt, price);
                //fastCoverTrader(ldt, price);
            }
        } else if (pmChgY > 0) {
//            if (!(maxAfterMin && maxAbovePrev)) {
//                amHedgeTrader(ldt, price);
//            } else {
//                percentileTrader(ldt, price);
//            }
        }

        overnightTrader(ldt, price);
    }

    private static void amHedgeTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        double currDelta = getNetPtfDelta();
        NavigableMap<LocalDateTime, SimpleBar> futdata = futData.get(ibContractToFutType(activeFuture));

        LocalDate tDate = checkTimeRangeBool(lt, 23, 59, 5, 0) ?
                nowMilli.toLocalDate().minusDays(1L) : nowMilli.toLocalDate();

        NavigableMap<LocalDateTime, SimpleBar> todayPriceMap =
                futdata.entrySet().stream().filter(e -> e.getKey()
                        .isAfter(LocalDateTime.of(tDate, LocalTime.of(8, 59))))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                                ConcurrentSkipListMap::new));

        LocalDateTime lastAMHedgeOrderTime = getLastOrderTime(AM_HEDGE);

        int todayPerc = getPercentileForLast(todayPriceMap);

        if (todayPerc > UP_PERC && currDelta > 0.0 &&
                MINUTES.between(lastAMHedgeOrderTime, nowMilli) >= ORDER_WAIT_TIME
                && nowMilli.toLocalTime().isBefore(LocalTime.of(13, 0))) {

            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimit(freshPrice, 1);
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, AM_HEDGE));
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "AM hedge", globalIdOrderMap.get(id)));
        }
    }

    private static boolean checkf10maxAftermint(String name) {
        if (!priceMapBar.containsKey(name) || priceMapBar.get(name).size() < 2) {
            return false;
        } else if (priceMapBar.get(name).lastKey().isBefore(LocalTime.of(9, 40))) {
            return false;
        } else {
            LocalTime maxT = priceMapBar.get(name).entrySet().stream()
                    .filter(e -> checkTimeRangeBool(e.getKey(), 9, 29, 9, 41))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(Map.Entry::getKey).orElse(LocalTime.MIN);

            LocalTime minT = priceMapBar.get(name).entrySet().stream()
                    .filter(e -> checkTimeRangeBool(e.getKey(), 9, 29, 9, 41))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow()))
                    .map(Map.Entry::getKey).orElse(LocalTime.MAX);

            if (detailedPrint.get()) {
                pr(name, "checkf10maxAftermint", maxT, minT);
            }

            return maxT.isAfter(minT);
        }
    }

    private static boolean checkf10MaxAbovePrev(String name) {
        if (!closeMap.containsKey(name) || closeMap.get(name) == 0.0) {
            return false;
        } else {
            double f10max = priceMapBar.get(name).entrySet().stream()
                    .filter(e -> checkTimeRangeBool(e.getKey(), 9, 30, 9, 41))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(e -> e.getValue().getHigh()).orElse(0.0);
            if (detailedPrint.get()) {
                pr(name, "checkf10max ", f10max, "close", closeMap.get(name));
            }
            return f10max > closeMap.get(name);
        }
    }

    private static int getPercentileChgYFut() {
        NavigableMap<LocalDateTime, SimpleBar> futdata = futData.get(ibContractToFutType(activeFuture));
        if (futdata.size() <= 2 || futdata.firstKey().toLocalDate().equals(futdata.lastKey().toLocalDate())) {
            return 0;
        } else {
            LocalDate prevDate = futdata.firstKey().toLocalDate();

            double prevMax = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(prevDate))
                    .filter(e -> checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(e -> e.getValue().getHigh()).orElse(0.0);

            double prevMin = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(prevDate))
                    .filter(e -> checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow()))
                    .map(e -> e.getValue().getLow()).orElse(0.0);

            double prevClose = futdata.floorEntry(LocalDateTime.of(prevDate, LocalTime.of(15, 0)))
                    .getValue().getClose();

            double pmOpen = futdata.floorEntry(LocalDateTime.of(prevDate, LocalTime.of(13, 0)))
                    .getValue().getOpen();

            if (prevMax == 0.0 || prevMin == 0.0 || prevClose == 0.0 || pmOpen == 0.0) {
                return 0;
            } else {
                return (int) Math.round(100d * (prevClose - pmOpen) / (prevMax - prevMin));
            }
        }
    }

    private static int getPercentileChgFut(NavigableMap<LocalDateTime, SimpleBar> futdata, LocalDate dt) {
        //NavigableMap<LocalDateTime, SimpleBar> futdata = futData.get(ibContractToFutType(activeFuture));
        if (futdata.size() <= 2 || futdata.firstKey().toLocalDate().equals(futdata.lastKey().toLocalDate())) {
            return 0;
        } else if (futdata.lastKey().isAfter(LocalDateTime.of(dt, LocalTime.of(13, 0)))) {
            //LocalDate prevDate = futdata.firstKey().toLocalDate();

            double prevMax = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(e -> e.getValue().getHigh()).orElse(0.0);

            double prevMin = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow()))
                    .map(e -> e.getValue().getLow()).orElse(0.0);

            double prevClose = futdata.floorEntry(LocalDateTime.of(dt, LocalTime.of(15, 0)))
                    .getValue().getClose();

            double pmOpen = futdata.floorEntry(LocalDateTime.of(dt, LocalTime.of(13, 0)))
                    .getValue().getOpen();

            if (prevMax == 0.0 || prevMin == 0.0 || prevClose == 0.0 || pmOpen == 0.0) {
                return 0;
            } else {
                return (int) Math.round(100d * (prevClose - pmOpen) / (prevMax - prevMin));
            }
        }
        return 0;
    }


    private static void updateLastMinuteMap(LocalDateTime ldt, double freshPrice) {
        activeLastMinuteMap.entrySet().removeIf(e -> e.getKey().isBefore(ldt.minusMinutes(3)));
        activeLastMinuteMap.put(ldt, freshPrice);

        if (activeLastMinuteMap.size() > 1) {
            double lastV = activeLastMinuteMap.lastEntry().getValue();
            double secLastV = activeLastMinuteMap.lowerEntry(activeLastMinuteMap.lastKey()).getValue();
            long milliLapsed = ChronoUnit.MILLIS.between(activeLastMinuteMap.lowerKey(activeLastMinuteMap.lastKey()),
                    activeLastMinuteMap.lastKey());
        } else {
            pr(str(" last minute map ", activeLastMinuteMap));
        }
    }

    private static double getBullishTarget() {
        return 500000;
    }

    private static double getBearishTarget() {
        return -500000;
    }

    private static double getDeltaHighLimit() {
        return 500000;
    }

    private static double getDeltaLowLimit() {
        return -500000;
    }

    public XUTrader getThis() {
        return this;
    }

    private static Contract gettingActiveContract() {

        long daysUntilFrontExp = ChronoUnit.DAYS.between(LocalDate.now(),
                LocalDate.parse(TradingConstants.A50_FRONT_EXPIRY, DateTimeFormatter.ofPattern("yyyyMMdd")));

        //return frontFut;
        pr(" **********  days until expiry **********", daysUntilFrontExp);
        if (daysUntilFrontExp <= 1) {
            pr(" using back fut ");
            return backFut;
        } else {
            pr(" using front fut ");
            return frontFut;
        }
    }

    private static double getExpiringDelta() {
        return currentPosMap.entrySet().stream()
                .mapToDouble(e -> {
                    if (e.getKey() == FutType.FrontFut &&
                            LocalDate.parse(TradingConstants.A50_FRONT_EXPIRY, DateTimeFormatter.ofPattern("yyyyMMdd"))
                                    .equals(LocalDate.now())) {
                        return e.getValue() * futPriceMap.getOrDefault(e.getKey(), SinaStock.FTSE_OPEN)
                                * ChinaPosition.fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey().getTicker(),
                                "CNY"), 1.0);
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
                                    .equals(LocalDate.now()) && LocalTime.now().isAfter(LocalTime.of(15, 0))) {
                        return 0.0;
                    }
                    return e.getValue() * futPriceMap.getOrDefault(e.getKey(), SinaStock.FTSE_OPEN)
                            * ChinaPosition.fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey().getTicker()
                            , "CNY"), 1.0);
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


    private static Predicate<AutoOrderType> isInventoryTrade() {
        return e -> e.equals(INVENTORY_CLOSE) || e.equals(INVENTORY_OPEN);
    }

    /**
     * if touched, play music
     */
    private void observeMATouch() {
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        if (price5.size() < 2) return;

        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(price5, shortMAPeriod);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(price5, longMAPeriod);

        double pd = getPD(price5.lastEntry().getValue().getClose());
        int percentile = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));
        soundPlayer.stopIfPlaying();
        if (smaShort.size() > 0) {
            String msg = str("**Observing MA**"
                    , "20day", _20DayMA
                    , "||T:", LocalTime.now().truncatedTo(MINUTES)
                    , "||MA Short:", r(smaShort.lastEntry().getValue())
                    , "||MA Long:", r(smaLong.lastEntry().getValue())
                    , "||Index:", r(getIndexPrice())
                    , "||PD:", r10000(pd)
                    , "||2 Day P%", percentile);

            outputToAutoLog(msg);
        }
    }

    private static int getSizeForDelta(double price, double fx, double delta) {
        return (int) Math.floor(Math.abs(delta) / (fx * price));
    }

    /**
     * slow cover trader (unconditional, not like UNCON_MA)
     *
     * @param nowMilli   time now
     * @param freshPrice price now
     */
    private static synchronized void slowCoverTrader(LocalDateTime nowMilli, double freshPrice) {
        checkCancelTrades(SLOW_COVER, nowMilli, ORDER_WAIT_TIME);

        LocalTime lt = nowMilli.toLocalTime();
        double currDelta = getNetPtfDelta();
        double futDelta = getFutDelta();
        //int size = getSizeForDelta(freshPrice, fxMap.get("USD"), futDelta / 4);

        if (!(lt.isAfter(LocalTime.of(9, 29)) && lt.isBefore(LocalTime.of(15, 0)))) {
            pr(" day trader: not in time range, return ", nowMilli.toLocalTime());
            return;
        }

        NavigableMap<LocalDateTime, SimpleBar> futdata = futData.get(ibContractToFutType(activeFuture));
        int perc = getPercentileForLast(futdata);
        LocalDateTime lastSlowCoverTime = getLastOrderTime(SLOW_COVER);

        if (futdata.size() < 2 || futdata.firstKey().toLocalDate().equals(futdata.lastKey().toLocalDate())) {
            return;
        }

        pr(" slow cover trader: ", "last order time ", lastSlowCoverTime, "p% ", perc);

        if (MINUTES.between(lastSlowCoverTime, nowMilli) >= ORDER_WAIT_TIME / 3) {
            if (perc < DOWN_PERC && currDelta < DELTA_HARD_HI_LIMIT) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, AutoOrderType.SLOW_COVER));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "day cover", globalIdOrderMap.get(id), " todayP% ", perc));
            }
        }
    }


    /**
     * percentileTrader
     */
    private static synchronized void percentileTrader(LocalDateTime nowMilli, double freshPrice) {
        // run every 15 minutes
        int perc = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));
        double pd = getPD(freshPrice);
        double fx = ChinaPosition.fxMap.getOrDefault("USD", 0.0);

        NavigableMap<LocalDateTime, SimpleBar> futdata = futData.get(ibContractToFutType(activeFuture));

        if (futdata.size() < 2 || futdata.firstKey().toLocalDate().equals(futdata.lastKey().toLocalDate())) {
            pr(" perc trader doesnt contain 2 days ");
            return;
        }

        long accSize = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == PERC_ACC)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToInt(e -> e.getValue().getOrder().getTotalSize()).sum();

        long deccSize = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == AutoOrderType.PERC_DECC)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToInt(e -> e.getValue().getOrder().getTotalSize()).sum();

        long netPercTrades = accSize - deccSize;

        long percOrdersTotal = globalIdOrderMap.entrySet().stream()
                .filter(e -> isPercTrade().test(e.getValue().getOrderType())).count();

        LocalDateTime lastPercTradeT = globalIdOrderMap.entrySet().stream()
                .filter(e -> isPercTrade().test(e.getValue().getOrderType()))
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .map(e -> e.getValue().getOrderTime())
                .orElse(sessionOpenT());

        LocalDateTime lastPercOrderT = globalIdOrderMap.entrySet().stream()
                .filter(e -> isPercTrade().test(e.getValue().getOrderType()))
                .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .map(e -> e.getValue().getOrderTime())
                .orElse(sessionOpenT());

        double avgAccprice = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == PERC_ACC)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().lmtPrice()).average().orElse(0.0);

        double avgDeccprice = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == AutoOrderType.PERC_DECC)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().lmtPrice()).average().orElse(0.0);

        int unfilledPercOrdersCount = (int) globalIdOrderMap.entrySet().stream()
                .filter(e -> isPercTrade().test(e.getValue().getOrderType()))
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
        double currDelta = getNetPtfDelta();

        if (detailedPrint.get()) {
            pr("perc Trader status?", percentileTradeOn.get() ? "ON" : "OFF",
                    nowMilli.toLocalTime().truncatedTo(ChronoUnit.SECONDS),
                    "p%:", perc, "pd", r10000(pd), "BullBear target : ", getBullishTarget(), getBearishTarget(),
                    "acc#, decc#, net#", accSize, deccSize, netPercTrades
                    , "accAvg, DecAvg,", avgAccprice, avgDeccprice,
                    "OrderT TradeT,next tradeT",
                    lastPercOrderT.toLocalTime().truncatedTo(MINUTES),
                    lastPercTradeT.toLocalTime().truncatedTo(MINUTES),
                    lastPercOrderT.plusMinutes(minBetweenPercOrders).toLocalTime().truncatedTo(MINUTES)
            );
        }

        //******************************************************************************************//
        //if (!(now.isAfter(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(15, 0)))) return;
        if (!percentileTradeOn.get()) return;

        //********************************************z*********************************************//
        if (timeDiffinMinutes(lastPercOrderT, nowMilli) >= minBetweenPercOrders) {
            if (perc < DOWN_PERC) {
                if (currDelta < getBullishTarget()) {
                    int id = autoTradeID.incrementAndGet();
                    int buySize = 1;
                    if (buySize > 0) {
                        Order o = placeBidLimit(freshPrice, buySize);
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Perc bid", PERC_ACC));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        outputOrderToAutoLog(str(o.orderId(), "perc bid",
                                globalIdOrderMap.get(id), " perc ", perc));
                    } else {
                        pr("perc buy size not tradable " + buySize);
                    }
                } else {
                    pr(" perc: delta above bullish target ");
                }
            } else if (perc > UP_PERC) {
                if (currDelta > getBearishTarget()) {
                    int id = autoTradeID.incrementAndGet();
                    int sellSize = 1;
                    if (sellSize > 0) {
                        Order o = placeOfferLimit(freshPrice, sellSize);
                        globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Perc offer",
                                AutoOrderType.PERC_DECC));
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                        outputOrderToAutoLog(str(o.orderId(), "perc offer", globalIdOrderMap.get(id), "perc", perc));
                    } else {
                        pr("perc sell size not tradable " + sellSize);
                    }
                } else {
                    pr("perc: delta below bearish target ");
                }
            }
        }
    }

    /**
     * only cover if first 10 maxT> first 10 minT
     *
     * @param nowMilli   time now in milli
     * @param freshPrice price
     */
    private static synchronized void fastCoverTrader(LocalDateTime nowMilli, double freshPrice) {
        NavigableMap<LocalDateTime, SimpleBar> index = convertToLDT(priceMapBar.get(FTSE_INDEX), nowMilli.toLocalDate()
                , e -> !checkTimeRangeBool(e, 11, 30, 12, 59));
        int indexTPerc = getPercentileForLast(index);
        LocalDateTime lastCoverOrderT = getLastOrderTime(FAST_COVER);
        checkCancelTrades(FAST_COVER, nowMilli, ORDER_WAIT_TIME);

        if (indexTPerc < DOWN_PERC && MINUTES.between(lastCoverOrderT, nowMilli) > ORDER_WAIT_TIME) {
            int id = autoTradeID.incrementAndGet();
            int size = 1;
            Order o = placeBidLimit(freshPrice, size); //Math.min(size, MAX_FUT_LIMIT)
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FAST_COVER));
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "cover short follow PMY<0, p%", indexTPerc,
                    globalIdOrderMap.get(id)));
        }
    }

    private static LocalDateTime getLastOrderTime(AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .map(e -> e.getValue().getOrderTime())
                .orElse(sessionOpenT());
    }

    /**
     * trading based on index MA
     *
     * @param nowMilli   time in milliseconds
     * @param freshPrice last price
     */
    private static synchronized void percentileMATrader(LocalDateTime nowMilli, double freshPrice, int pmPercChg) {
        LocalTime lt = nowMilli.toLocalTime();
        String anchorIndex = FTSE_INDEX;
        double currDelta = getNetPtfDelta();
        double fx = ChinaPosition.fxMap.getOrDefault("USD", 0.0);
        NavigableMap<LocalDateTime, SimpleBar> index = convertToLDT(priceMapBar.get(anchorIndex), nowMilli.toLocalDate()
                , e -> !isStockNoonBreak(e));
        int shorterMA = 10;
        int longerMA = 20;
        int maSize = AGGRESSIVE_SIZE;

        if (isOvernight(lt) || isStockNoonBreak(lt)) {
            anchorIndex = "Future";
            index = futData.get(ibContractToFutType(activeFuture));
        } else if (checkTimeRangeBool(lt, 9, 30, 10, 0)) {
            shorterMA = 1;
            longerMA = 5;
            maSize = 1;
        }

        checkCancelTrades(PERC_MA, nowMilli, ORDER_WAIT_TIME * 2);
        LocalDate tTrade = getTradeDate(nowMilli);

        int _2dayPerc = getPercentileForLast(index);
        int todayPerc = getPercentileForLastPred(index, e -> e.getKey().toLocalDate().equals(tTrade));
        LocalDateTime lastIndexMAOrder = getLastOrderTime(PERC_MA);

        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(index, shorterMA);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(index, longerMA);

        if (smaShort.size() <= 2 || smaLong.size() <= 2) {
            pr(" smashort size long size not enough ");
            return;
        }

        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry((smaLong.lastKey())).getValue();

        if (detailedPrint.get()) {
            pr("*perc MA Time: ", nowMilli.toLocalTime(), "||T perc: ", todayPerc, "||2D p%", _2dayPerc
                    , "pmch: ", pmPercChg);
            pr("Anchor / short long MA: ", anchorIndex, shorterMA, longerMA);
            pr(" ma cross last : ", r(maShortLast), r(maLongLast), r(maShortLast - maLongLast));
            pr(" ma cross 2nd last : ", r(maShortSecLast), r(maLongSecLast), r(maShortSecLast - maLongSecLast));
            boolean bull = maShortLast > maLongLast && maShortSecLast <= maLongSecLast;
            boolean bear = maShortLast < maLongLast && maShortSecLast >= maLongSecLast;
            pr(" bull/bear cross ", bull, bear);
            pr(" current PD ", r10000(getPD(freshPrice)));
            if (bull || bear) {
                if (checkTimeRangeBool(lt, 9, 30, 15, 0)) {
                    soundPlayer.playClip();
                }
            }
        }

        if (MINUTES.between(lastIndexMAOrder, nowMilli) >= ORDER_WAIT_TIME) {
            if (maShortLast > maLongLast && maShortSecLast <= maLongSecLast && _2dayPerc < DOWN_PERC_WIDE
                    && pmPercChg < 0 && currDelta + maSize * freshPrice * fx < getBullishTarget()) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, maSize);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, PERC_MA));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "perc MA buy", globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), " anchor ", anchorIndex, "perc", todayPerc, "2d Perc ",
                        _2dayPerc, "delta chg ", maSize * freshPrice * fx));
            } else if (maShortLast < maLongLast && maShortSecLast >= maLongSecLast && _2dayPerc > UP_PERC_WIDE
                    && pmPercChg > 0 && currDelta - maSize * freshPrice * fx > getBearishTarget()) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, maSize);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, PERC_MA));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "perc MA sell", globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), " anchor ", anchorIndex, "perc", todayPerc, "2d Perc ",
                        _2dayPerc, "delta chg ", -1 * maSize * freshPrice * fx));
            }
        }
    }

    /**
     * Auto trading based on Moving Avg, no percentile
     */
    private static synchronized void unconditionalMATrader(LocalDateTime nowMilli, double freshPrice) {
        NavigableMap<LocalDateTime, SimpleBar> price1 = convertToLDT(priceMapBar.get(FTSE_INDEX),
                nowMilli.toLocalDate(), e -> !checkTimeRangeBool(e, 11, 30, 12, 59));
        //futData.get(ibContractToFutType(activeFuture));
        LocalTime lt = nowMilli.toLocalTime();
        if (checkTimeRangeBool(lt, 11, 29, 13, 0)) {
            return;
        }
        if (price1.size() <= 2) return;
        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(price1, _1_min_ma_short);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(price1, _1_min_ma_long);
        int uncon_size = CONSERVATIVE_SIZE;
        if (smaShort.size() < 2 || smaLong.size() < 2) {
            return;
        }
        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry(smaLong.lastKey()).getValue();
        sentiment = maShortLast > maLongLast ? MASentiment.Bullish : MASentiment.Bearish;
        double indexPrice = (priceMapBar.containsKey(FTSE_INDEX) &&
                priceMapBar.get(FTSE_INDEX).size() > 0) ?
                priceMapBar.get(FTSE_INDEX).lastEntry().getValue().getClose() : SinaStock.FTSE_OPEN;

        double pd = (indexPrice != 0.0 && freshPrice != 0.0) ? (freshPrice / indexPrice - 1) : 0.0;

        LocalDateTime lastUnconMAOrderTime = getLastOrderTime(UNCON_MA);

        if (timeDiffinMinutes(lastUnconMAOrderTime, nowMilli) >= ORDER_WAIT_TIME) {
            if (maShortLast > maLongLast && maShortSecLast <= maLongSecLast) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, uncon_size);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "BUY UNCON_MA", UNCON_MA));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "BUY UNCON_MA ", globalIdOrderMap.get(id)));
            } else if (maShortLast < maLongLast && maShortSecLast >= maLongSecLast) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, uncon_size);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "SELL UNCON_MA", UNCON_MA));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "SELL UNCON_MA", globalIdOrderMap.get(id)));
            }

            if (detailedPrint.get()) {
                String outputMsg = str("UNCON_MA || ",
                        " Periods ShortLong", _1_min_ma_short, _1_min_ma_long,
                        "|last shortlong:", r(maShortLast), r(maLongLast),
                        "|secLast shortlong:", r(maShortSecLast), r(maLongSecLast),
                        "|PD", r10000(pd), "|Index", r(indexPrice),
                        "|Last Order Time:", lastUnconMAOrderTime.truncatedTo(MINUTES));
                pr(outputMsg);
            }
        }
    }

    /**
     * overnight close trading
     */
    private static void overnightTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        if (futureAMSession().test(LocalTime.now()) || futurePMSession().test(LocalTime.now())) return;
        double currDelta = getNetPtfDelta();

        LocalDate TDate = getTradeDate(nowMilli);

        double indexPrice = (priceMapBar.containsKey(FTSE_INDEX) &&
                priceMapBar.get(FTSE_INDEX).size() > 0) ?
                priceMapBar.get(FTSE_INDEX).lastEntry().getValue().getClose() : SinaStock.FTSE_OPEN;

        NavigableMap<LocalDateTime, SimpleBar> futPriceMap = futData.get(ibContractToFutType(activeFuture));

        int pmPercChg = getPMPercChg(futPriceMap, TDate);
        int currPerc = getPercentileForLast(futPriceMap);

        LocalDateTime lastOrderTime = getLastOrderTime(OVERNIGHT);

        if (checkTimeRangeBool(lt, 3, 0, 5, 0)
                && MINUTES.between(lastOrderTime, nowMilli) > ORDER_WAIT_TIME) {
            if (currDelta > getDeltaLowLimit() && currPerc > UP_PERC && pmPercChg > 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Overnight Short", OVERNIGHT));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "O/N sell @ ", freshPrice, " curr p% ", currPerc));
            } else if (currDelta < getDeltaHighLimit() && currPerc < DOWN_PERC && pmPercChg < 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Overnight Long", OVERNIGHT));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "O/N buy @ ", freshPrice,
                        "perc: ", currPerc, "pmPercChg", pmPercChg));
            }
        } else {
            outputToAutoLog(" outside tradable time slot");
        }
        String outputString = str("||O/N||", nowMilli.format(DateTimeFormatter.ofPattern("M-d H:mm")),
                "||curr P%: ", currPerc,
                "||curr P: ", futPriceMap.lastEntry().getValue().getClose(),
                "||index: ", Math.round(100d * indexPrice) / 100d,
                "||BID ASK ", bidMap.getOrDefault(ibContractToFutType(activeFuture), 0.0),
                askMap.getOrDefault(ibContractToFutType(activeFuture), 0.0),
                "pmchy", pmPercChg);

        outputToAutoLog(outputString);
        requestOvernightExecHistory();
    }


    /**
     * Last hour direction more clear - trade
     *
     * @param nowMilli   timeNow
     * @param freshPrice Latest fut price
     */
    private static void lastHourMATrader(LocalDateTime nowMilli, double freshPrice, double pmPercYChg) {
        LocalTime lt = nowMilli.toLocalTime();
        String anchorIndex = FTSE_INDEX;
        NavigableMap<LocalDateTime, SimpleBar> index = convertToLDT(priceMapBar.get(anchorIndex), nowMilli.toLocalDate()
                , e -> !checkTimeRangeBool(e, 11, 30, 12, 59));
        int shorterMA = 5;
        int longerMA = 10;
        LocalDateTime lastHourMAOrderTime = getLastOrderTime(LAST_HOUR_MA);
        int waitTime = 1;

        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(index, shorterMA);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(index, longerMA);

        if (smaShort.size() <= 2 || smaLong.size() <= 2) {
            pr(" smashort size long size not enough ");
            return;
        }

        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry((smaLong.lastKey())).getValue();

        //only trade UNCON_MA in the last hour, no perc required. Big order wait time.
        if (checkTimeRangeBool(lt, 14, 0, 15, 0)) {
            if (MINUTES.between(lastHourMAOrderTime, nowMilli) >= waitTime) {
                if (maShortLast > maLongLast && maShortSecLast <= maLongSecLast && pmPercYChg < 0) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeBidLimit(freshPrice, 1);
                    globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, LAST_HOUR_MA));
                    apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                    outputOrderToAutoLog(str(o.orderId(), "last hr UNCON_MA buy", globalIdOrderMap.get(id)
                            , "Last shortlong ", r(maShortLast), r(maLongLast), "SecLast Shortlong",
                            r(maShortSecLast), r(maLongSecLast)));
                } else if (maShortLast < maLongLast && maShortSecLast >= maLongSecLast && pmPercYChg > 0) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeOfferLimit(freshPrice, 1);
                    globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, LAST_HOUR_MA));
                    apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                    outputOrderToAutoLog(str(o.orderId(), "last hr UNCON_MA sell", globalIdOrderMap.get(id)
                            , "Last shortlong ", r(maShortLast), r(maLongLast), "SecLast Shortlong",
                            r(maShortSecLast), r(maLongSecLast)));
                }
            }
        }
    }

    private static void checkCancelTrades(AutoOrderType type, LocalDateTime nowMilli, int timeLimit) {
        long trades = globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type).count();

        if (trades != 0) {
            OrderStatus lastOrdStatus = globalIdOrderMap.entrySet().stream()
                    .filter(e -> e.getValue().getOrderType() == type)
                    .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                    .map(e -> e.getValue().getStatus()).orElse(OrderStatus.Unknown);

            LocalDateTime lastOTime = getLastOrderTime(type);

            if (lastOrdStatus != OrderStatus.Filled && lastOrdStatus != OrderStatus.Cancelled
                    && lastOrdStatus != OrderStatus.ApiCancelled) {

                if (MINUTES.between(lastOTime, nowMilli) > timeLimit) {
                    globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type)
                            .forEach(e -> {
                                if (e.getValue().getStatus() == OrderStatus.Submitted) {
                                    apcon.cancelOrder(e.getValue().getOrder().orderId());
                                    e.getValue().setFinalActionTime(LocalDateTime.now());
                                    e.getValue().setStatus(OrderStatus.Cancelled);
                                }
                            });
                    outputOrderToAutoLog(nowMilli + " cancelling orders trader for type " + type);
                }
            }
        }
    }

    private static synchronized void trimTrader(LocalDateTime nowMilli, double freshPrice) {
        if (checkTimeRangeBool(nowMilli.toLocalTime(), 5, 0, 15, 0)) {
            return;
        }

        double netDelta = getNetPtfDelta();
        LocalDateTime lastTrimOrderT = getLastOrderTime(TRIM);

        checkCancelTrades(TRIM, nowMilli, ORDER_WAIT_TIME);

        pr("trim trader delta/target", r(netDelta), getBullishTarget(), getBearishTarget(), "last order T ",
                lastTrimOrderT.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                , "next order T", lastTrimOrderT.plusMinutes(ORDER_WAIT_TIME)
                        .toLocalTime().truncatedTo(ChronoUnit.MINUTES));

        if (MINUTES.between(lastTrimOrderT, nowMilli) >= ORDER_WAIT_TIME) {
            if (netDelta > DELTA_HARD_HI_LIMIT) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, TRIM));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "trim sell", globalIdOrderMap.get(id)));
            } else if (netDelta < DELTA_HARD_LO_LIMIT) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, TRIM));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "trim buy", globalIdOrderMap.get(id)));
            }
        }
    }


    /**
     * PD trader
     */
    public static synchronized void pdTrader(LocalDateTime nowMilli, double freshPrice) {
        int perc = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));
        double pd = getPD(freshPrice);
        int pdPerc = getPDPercentile();
        double currDelta = getNetPtfDelta();

        pr(nowMilli.toLocalTime().truncatedTo(MINUTES),
                " pd trader ", " barrier# ", pdBarrier.getNumberWaiting(), " PD sem#: ",
                pdSemaphore.availablePermits(), "pd", r10000(pd), "pd P%", pdPerc);

        if (!pdTraderOn.get() || nowMilli.toLocalTime().isBefore(LocalTime.of(9, 30))) {
            pr(" QUITTING PD, local time before 9 30 ");
            return;
        }
        // print all pd trades
        globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == AutoOrderType.PD_OPEN ||
                e.getValue().getOrderType() == AutoOrderType.PD_CLOSE).forEach(Utility::pr);

        if (pdBarrier.getNumberWaiting() == 2) {
            outputToAutoLog(str(LocalDateTime.now().truncatedTo(MINUTES), " resetting PD barrier" +
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
            globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == PD_OPEN)
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
                            .truncatedTo(MINUTES), buyO, "PD Buy close", PD_CLOSE));
                    apcon.placeOrModifyOrder(activeFuture, buyO, new InventoryOrderHandler(idBuyClose, pdBarrier));
                    outputOrderToAutoLog(str(buyO.orderId(), " PD Buy Close ", globalIdOrderMap.get(idBuyClose)));
                }
            });
        }
    }

    private static int getPDPercentile() {
        LocalDate d = (LocalTime.now().isBefore(LocalTime.of(5, 0))) ? LocalDate.now().minusDays(1) : LocalDate.now();
        int candidate = 50;
        NavigableMap<LocalDateTime, Double> dpMap = new ConcurrentSkipListMap<>();
        futData.get(ibContractToFutType(activeFuture)).entrySet().stream().filter(e -> e.getKey()
                .isAfter(LocalDateTime.of(d, LocalTime.of(9, 29)))).forEach(e -> {
            if (priceMapBar.get(FTSE_INDEX).size() > 0 && priceMapBar.get(FTSE_INDEX).
                    firstKey().isBefore(e.getKey().toLocalTime())) {
                double index = priceMapBar.get(FTSE_INDEX).floorEntry(e.getKey().toLocalTime())
                        .getValue().getClose();
                dpMap.put(e.getKey(), r10000((e.getValue().getClose() / index - 1)));
            }
        });

        if (detailedPrint.get()) {
            if (dpMap.size() > 0) {
                pr(" PD last: ", dpMap.lastEntry(),
                        " max: ", dpMap.entrySet().stream().mapToDouble(Map.Entry::getValue).max().orElse(0.0),
                        " min: ", dpMap.entrySet().stream().mapToDouble(Map.Entry::getValue).min().orElse(0.0),
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
        double fx = ChinaPosition.fxMap.get("USD");
        double currDelta = getNetPtfDelta();
        pr(" In Flattening aggressively ");
        if (currDelta < getBullishTarget() && currDelta > getBearishTarget()) {
            pr(" flatten aggressively no need, delta in line");
            return;
        }

        if (currDelta > getBullishTarget()) { //no sell at discount or at bottom
            int id = autoTradeID.incrementAndGet();
            double candidatePrice = bidMap.get(ibContractToFutType(activeFuture));
            Order o = placeOfferLimitTIF(candidatePrice, 1, Types.TimeInForce.IOC);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FLATTEN_AGGRESSIVE));
            outputOrderToAutoLog(str(o.orderId(), " AGGRESSIVE Sell Flatten ", globalIdOrderMap.get(id)));
        } else if (currDelta < getBearishTarget()) { // no buy at premium or at top
            int id = autoTradeID.incrementAndGet();
            double candidatePrice = askMap.get(ibContractToFutType(activeFuture));
            Order o = placeBidLimitTIF(candidatePrice, 1, Types.TimeInForce.IOC);
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
        double currDelta = getNetPtfDelta();
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
        double fx = ChinaPosition.fxMap.getOrDefault("USD", 1.0);

        SimpleBar lastBar = new SimpleBar(price5.lastEntry().getValue());
        double prevPrice = activeLastMinuteMap.size() <= 2 ? freshPrice : activeLastMinuteMap
                .lowerEntry(nowMilli).getValue();

        lastBar.add(freshPrice);
        NavigableMap<LocalDateTime, Double> sma = getMAGen(price5, shortMAPeriod);
        double maLast = sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;

        if (currDelta > getBullishTarget() && prevPrice > maLast && freshPrice <= maLast
                && perc > UP_PERC && pd > PD_DOWN_THRESH) { //no sell at discount or at bottom
            int id = autoTradeID.incrementAndGet();

            double candidatePrice = (flattenEagerness == Eagerness.Passive) ? freshPrice :
                    bidMap.get(ibContractToFutType(activeFuture));

            Order o = placeOfferLimitTIF(candidatePrice, 1, Types.TimeInForce.IOC);
            apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Sell", FLATTEN));
            outputOrderToAutoLog(str(o.orderId(), " Sell Flatten ", globalIdOrderMap.get(id)));
        } else if (currDelta < getBearishTarget() && prevPrice < maLast && freshPrice >= maLast
                && perc < DOWN_PERC && pd < PD_UP_THRESH) { // no buy at premium or at top
            int id = autoTradeID.incrementAndGet();

            double candidatePrice = (flattenEagerness == Eagerness.Passive) ? freshPrice :
                    askMap.get(ibContractToFutType(activeFuture));

            Order o = placeBidLimitTIF(candidatePrice, 1, Types.TimeInForce.IOC);
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
        sma = getMAGen(price5, shortMAPeriod);

        if (detailedPrint.get()) {
            pr(str(" Detailed UNCON_MA ON", "pd", r10000(pd),
                    " freshPrice", freshPrice,
                    " prev Price", prevPrice,
                    " last bar", lastBar,
                    " SMA", r(sma.lastEntry().getValue()))); //"activeLastMap: ", activeLastMinuteMap)
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
                    outputOrderToAutoLog(str(o.orderId(), nowMilli.truncatedTo(MINUTES),
                            "FAST ORDER || BIDDING @ ", o.toString(), "SMA",
                            "||Fresh: ", freshPrice,
                            "||Prev: ", prevPrice,
                            "||UNCON_MA LAST: ", r(maLast),
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
                    globalIdOrderMap.put(id, new OrderAugmented(nowMilli.truncatedTo(MINUTES), o,
                            "Fast Trade Offer", FAST));
                    apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                    fastTradeSignals.incrementAndGet();
                    lastFastOrderTime = LocalDateTime.now();
                    fastOrderMap.put(nowMilli, o);
                    outputOrderToAutoLog(str(o.orderId(), nowMilli.truncatedTo(MINUTES),
                            "FAST ORDER || OFFERING @ ", o.toString(), "SMA",
                            "||Fresh: ", freshPrice, "||Prev", prevPrice,
                            "||UNCON_MA Last: ", r(maLast),
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
        double currDelta = getNetPtfDelta();

        int currPos = currentPosMap.getOrDefault(ibContractToFutType(activeFuture), 0);

        if (Math.abs(currPos) > MAX_FUT_LIMIT) {
            pr(" quitting inventory trade, pos:", currPos, ": exceeding fut limit ");
            return;
        }

        if (currDelta > getBullishTarget() || currDelta < getBearishTarget()) {
            pr(" quitting inventory trade", r(currDelta), ": exceeding Delta limit ");
            return;
        }

        if (!inventoryTraderOn.get() || inventorySemaphore.availablePermits() == 0) {
            pr(" quitting inventory trade: semaphore #:", inventorySemaphore.availablePermits());
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
            globalIdOrderMap.put(idSell, new OrderAugmented(LocalDateTime.now().truncatedTo(MINUTES)
                    , sellO, "Inv Sell Close", INVENTORY_CLOSE));
            apcon.placeOrModifyOrder(activeFuture, sellO, new InventoryOrderHandler(idSell, latchSell, inventoryBarrier));
            outputOrderToAutoLog(str(sellO.orderId(), "Inv Sell Close ", globalIdOrderMap.get(idSell)));
            try {
                latchSell.await();
                if (inventoryBarrier.getNumberWaiting() == 2) {
                    outputToAutoLog(str(LocalDateTime.now().truncatedTo(MINUTES)
                            , " resetting inventory barrier "));
                    inventoryBarrier.reset();
                    pr(" reset inventory barrier ");
                }
                inventorySemaphore.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if (perc > UP_PERC) {
            try {
                inventorySemaphore.acquire();
                pr(" acquired semaphore, now left: " + inventorySemaphore.availablePermits());
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
                pr(" BEFORE latchSell.await ");
                latchSell.await();
                pr(" AFTER latchSell.await ");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            pr(" Sold, now putting in buy signal ");
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
                    pr(" reset inventory barrier ");
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

        NavigableMap<LocalDateTime, Double> sma = getMAGen(price5, shortMAPeriod);
        return sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;
    }

    public static void flattenDriftTrader(LocalDateTime t, double freshPrice) {
        double currentDelta = getNetPtfDelta();
        double malast = getCurrentMA();
        double fx = ChinaPosition.fxMap.getOrDefault("USD", 0.0);
        double pd = getPD(freshPrice);
        int unfilled_trades = (int) globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() ==
                FLATTEN || e.getValue().getOrderType() == DRIFT)
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
        pr(str("CHECKING PRICE || bid ask price ",
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

    public static void updateLog(String s) {
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
                    pr(" today open is for " + name + " " + open);
                }
                futData.get(FutType.get(name)).put(ldt, new SimpleBar(open, high, low, close));
            }
        } else {
            pr(str(date, open, high, low, close));
        }
    }

    @Override
    public void actionUponFinish(String name) {
        pr(" printing fut data " + name + " " + futData.get(FutType.get(name)).lastEntry());
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

        if (contract.symbol().equals("XINA50")) {
            FutType f = ibContractToFutType(contract);

            if (uniqueTradeKeySet.contains(tradeKey)) {
                pr(" duplicate trade key ", tradeKey);
                return;
            } else {
                uniqueTradeKeySet.add(tradeKey);
            }

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
    }

    @Override
    public void tradeReportEnd() {
        //pr("printing all tradesmap all ", tradesMap);
        if (tradesMap.get(ibContractToFutType(activeFuture)).size() > 0) {
            currentDirection = tradesMap.get(ibContractToFutType(activeFuture)).lastEntry().getValue().getSizeAll() > 0 ?
                    Direction.Long : Direction.Short;
            //lastTradeTime = tradesMap.get(ibContractToFutType(activeFuture)).lastEntry().getKey();
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
//        if (detailedPrint.get()) {
//            pr(" open order ", contract.symbol(), contract.lastTradeDateOrContractMonth()
//                    , ibContractToSymbol(contract), order, orderState);
//        }

        activeFutLiveIDOrderMap.put(order.orderId(), order);

        if (ibContractToSymbol(contract).equals(ibContractToSymbol(activeFuture))) {
            double sign = order.action().equals(Types.Action.BUY) ? 1 : -1;
            if (!activeFutLiveOrder.containsKey(order.lmtPrice())) {
                activeFutLiveOrder.put(order.lmtPrice(), sign * order.totalQuantity());
            } else {
                activeFutLiveOrder.put(order.lmtPrice(),
                        activeFutLiveOrder.get(order.lmtPrice()) + sign * order.totalQuantity());
            }
        } else {
            pr(" contract not equal to activefuture ");
        }
    }

    @Override
    public void openOrderEnd() {
//        if (detailedPrint.get()) {
//            pr(" open order ended");
//            pr("activeFutLiveIDOrderMap", activeFutLiveIDOrderMap);
//            pr("activeFutLiveOrder", activeFutLiveOrder);
//        }
    }

    @Override
    public void orderStatus(int orderId, OrderStatus status, int filled, int remaining,
                            double avgFillPrice, long permId, int parentId,
                            double lastFillPrice, int clientId, String whyHeld) {
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
        if (ticker.startsWith("SGXA50") && position != 0.0) {
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
        pr("connected in XUconnectionhandler");
        connectionStatus = true;
        connectionLabel.setText(Boolean.toString(XUTrader.connectionStatus));
        apcon.setConnectionStatus(true);
    }

    @Override
    public void disconnected() {
        pr("disconnected in XUConnectionHandler");
        connectionStatus = false;
        connectionLabel.setText(Boolean.toString(connectionStatus));
    }

    @Override
    public void accountList(ArrayList<String> list) {
        pr(" account list is " + list);
    }

    @Override
    public void error(Exception e) {
        pr(" error in XUConnectionHandler");
        e.printStackTrace();
    }

    @Override
    public void message(int id, int errorCode, String errorMsg) {
        pr(" error ID " + id + " error code " + errorCode + " errormsg " + errorMsg);
    }

    @Override
    public void show(String string) {
        pr(" show string " + string);
    }

    private void requestLevel2Data() {
        apcon.reqDeepMktData(activeFuture, 10, this);
    }

    private void requestExecHistory() {
        uniqueTradeKeySet = new HashSet<>();
        tradesMap.replaceAll((k, v) -> new ConcurrentSkipListMap<>());
        apcon.reqExecutions(new ExecutionFilter(), this);
    }

    private static void requestOvernightExecHistory() {
        overnightTradesMap.replaceAll((k, v) -> new ConcurrentSkipListMap<>());
        apcon.reqExecutions(new ExecutionFilter(), XUOvernightTradeExecHandler.DefaultOvernightHandler);
    }

    //private void requestXUData() {
    //        getAPICon().reqXUDataArray();
    //    }

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

    private static void computeTradeMapActive() {
        //pr(" compute trade map active ", ibContractToFutType(activeFuture));
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
        double mtmPnl = (currentPosMap.getOrDefault(f, 0) - unitsBought - unitsSold) * (futPriceMap.getOrDefault(f, 0.0)
                - futPrevCloseMap.getOrDefault(f, 0.0));

        SwingUtilities.invokeLater(() -> {
            updateLog(" Expiry " + activeFuture.lastTradeDateOrContractMonth());
            updateLog(" NAV: " + currentIBNAV);
            updateLog(" P " + futPriceMap.getOrDefault(f, 0.0));
            updateLog(" Close " + futPrevCloseMap.getOrDefault(f, 0.0));
            updateLog(" Open " + futOpenMap.getOrDefault(f, 0.0));
            updateLog(" Chg " + (Math.round(10000d * (futPriceMap.getOrDefault(f, 0.0) /
                    futPrevCloseMap.getOrDefault(f, Double.MAX_VALUE) - 1)) / 100d) + " %");
            updateLog(" Open Pos " + (currentPosMap.getOrDefault(f, 0) - unitsBought - unitsSold));
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
                    + currentPosMap.getOrDefault(f, 0) + " Delta " + r(getNetPtfDelta()) +
                    " Stock Delta " + r(ChinaPosition.getStockPtfDelta()) + " Fut Delta " + r(XUTrader.getFutDelta())
                    + "HK Delta " + r(ChinaPosition.getStockPtfDeltaCustom(e -> isHKStock(e.getKey())))
                    + " China Delta " + r(ChinaPosition.getStockPtfDeltaCustom(e -> isChinaStock(e.getKey()))));
            updateLog(" expiring delta " + getExpiringDelta());
        });
    }
}

