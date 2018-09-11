package apidemo;

import TradeType.FutureTrade;
import TradeType.TradeBlock;
import auxiliary.SimpleBar;
import client.*;
import controller.ApiController;
import graph.DisplayGranularity;
import graph.GraphXuTrader;
import handler.HistoricalHandler;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static apidemo.ChinaData.priceMapBar;
import static apidemo.ChinaData.priceMapBarDetail;
import static apidemo.ChinaDataYesterday.ma20Map;
import static apidemo.ChinaOption.getATMVol;
import static apidemo.ChinaPosition.*;
import static apidemo.ChinaStock.*;
import static apidemo.TradingConstants.*;
import static apidemo.XuTraderHelper.*;
import static java.time.temporal.ChronoUnit.*;
import static util.AutoOrderType.*;
import static utility.Utility.*;

public final class XUTrader extends JPanel implements HistoricalHandler, ApiController.IDeepMktDataHandler,
        ApiController.ITradeReportHandler, ApiController.IOrderHandler, ApiController.ILiveOrderHandler
        , ApiController.IPositionHandler, ApiController.IConnectionHandler {

    private static JButton refreshButton;
    private static JButton computeButton;
    private static JButton processTradesButton;
    private static JButton graphButton;
    private static JToggleButton showTradesButton;

    private static MASentiment _20DayMA = MASentiment.Directionless;
    public static volatile double currentIBNAV = 0.0;

    private static volatile Set<String> uniqueTradeKeySet = new HashSet<>();
    static ApiController apcon;
    private static XUTraderRoll traderRoll;
    private static final int MAX_ORDER_SIZE = 4;

    //global
    private static AtomicBoolean globalTradingOn = new AtomicBoolean(false);
    private static AtomicBoolean musicOn = new AtomicBoolean(false);
    private static volatile MASentiment sentiment = MASentiment.Directionless;
    //static final int MAX_FUT_LIMIT = 20;
    //static volatile AtomicBoolean canLongGlobal = new AtomicBoolean(true);
    //static volatile AtomicBoolean canShortGlobal = new AtomicBoolean(true);
    static volatile AtomicInteger autoTradeID = new AtomicInteger(100);
    public static volatile NavigableMap<Integer, OrderAugmented> globalIdOrderMap = new ConcurrentSkipListMap<>();
    private static final int HI_PERC = 95;
    private static final int LO_PERC = 5;
    private static final int HI_PERC_WIDE = 80;
    private static final int LO_PERC_WIDE = 20;

    //fut prev close deviation
    private static volatile Direction futOpenDevDirection = Direction.Flat;
    private static volatile AtomicBoolean manualfutOpenDevDirection = new AtomicBoolean(false);
    private static volatile EnumMap<FutType, Double> fut5amClose = new EnumMap<>(FutType.class);

    //fut hilo trader
    private static volatile Direction futHiLoDirection = Direction.Flat;

    //flatten drift trader
    public static volatile Eagerness flattenEagerness = Eagerness.Passive;

    private static final int ORDER_WAIT_TIME = 60;

    private static final double DELTA_HARD_HI_LIMIT = 1000000.0;
    private static final double DELTA_HARD_LO_LIMIT = -1000000.0;

    private static final double BULL_BASE_DELTA = 500000;
    private static final double BEAR_BASE_DELTA = -500000;
    private static final double PMCHY_DELTA = 3000000;


    public static volatile int _1_min_ma_short = 10;
    public static volatile int _1_min_ma_long = 20;
    public static volatile int _5_min_ma_short = 5;
    public static volatile int _5_min_ma_long = 10;

    // pmchy limits
    private static final double PMCHY_HI = 20;
    private static final double PMCHY_LO = -20;

    //vol
    private static LocalDate expiryToGet = ChinaOption.frontExpiry;
    private static volatile AtomicBoolean manualFutHiloDirection = new AtomicBoolean(false);


    //china open/firsttick
    private static volatile Direction indexHiLoDirection = Direction.Flat;
    private static volatile AtomicBoolean manualIndexHiloDirection = new AtomicBoolean(false);
    private static volatile AtomicBoolean manualAccuOn = new AtomicBoolean(false);
    private static final long HILO_ACCU_MAX_SIZE = 2;
    private static final LocalTime HILO_ACCU_DEADLINE = ltof(9, 40);
    private static final long FT_ACCU_MAX_SIZE = 2;

    //pm hilo
    private static volatile Direction indexPmHiLoDirection = Direction.Flat;
    private static volatile AtomicBoolean manualPMHiloDirection = new AtomicBoolean(false);

    //pm dev trader
    private static volatile Direction indexPmDevDirection = Direction.Flat;
    private static volatile AtomicBoolean manualPMDevDirection = new AtomicBoolean(false);


    //index open deviation
    private static volatile Direction openDeviationDirection = Direction.Flat;
    private static volatile AtomicBoolean manualOpenDeviationOn = new AtomicBoolean(false);
    private static final long MAX_OPEN_DEV_SIZE = 6;
    private static volatile int OPENDEV_BASE_SIZE = 1;


    //hilo
    //private static volatile int HILO_BASE_SIZE = 1;

    //size
    private static final int CONSERVATIVE_SIZE = 1;
    private static final int AGGRESSIVE_SIZE = 3;

    //inventory market making
    private static volatile Semaphore inventorySemaphore = new Semaphore(1);
    private static volatile CyclicBarrier inventoryBarrier = new CyclicBarrier(2, () -> {
        outputToAutoLog(str(LocalTime.now(), "inventory barrier reached 2",
                "Trading Cycle Ends"));
    });

    //pd market making
    private static volatile Semaphore pdSemaphore = new Semaphore(2);
    private static volatile CyclicBarrier pdBarrier = new CyclicBarrier(2, () -> {
        outputToAutoLog(str(LocalTime.now(), " PD barrier reached 2, Trading cycle ends"));
    });

    //ma
    private static volatile int shortMAPeriod = 60;
    private static volatile int longMAPeriod = 80;

    //open/fast trading
    private static LocalDateTime lastFastOrderTime = LocalDateTime.now();
    private static AtomicInteger fastTradeSignals = new AtomicInteger(0);
    private static NavigableMap<LocalDateTime, Order> fastOrderMap = new ConcurrentSkipListMap<>();
    private static final long MAX_OPEN_TRADE_ORDERS = 10;

    //music
    private static EmbeddedSoundPlayer soundPlayer = new EmbeddedSoundPlayer();

    //buy sell only
    private static volatile AtomicBoolean noMoreSell = new AtomicBoolean(false);
    private static volatile AtomicBoolean noMoreBuy = new AtomicBoolean(false);

    //detailed UNCON_MA
    static AtomicBoolean detailedPrint = new AtomicBoolean(true);

    //display
    public static volatile Predicate<LocalDateTime> displayPred = e -> true;
    private final static Contract frontFut = utility.Utility.getFrontFutContract();
    private final static Contract backFut = utility.Utility.getBackFutContract();

    @SuppressWarnings("unused")
    private static Predicate<? super Map.Entry<FutType, ?>> graphPred = e -> true;
    public static volatile Contract activeFutureCt = gettingActiveContract();

    public static volatile DisplayGranularity gran = DisplayGranularity._5MDATA;
    private static volatile Map<Double, Double> activeFutLiveOrder = new HashMap<>();
    public static volatile Map<Integer, Order> activeFutLiveIDOrderMap = new HashMap<>();
    public static volatile EnumMap<FutType, Double> bidMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Double> askMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Double> futPriceMap = new EnumMap<>(FutType.class);
    static volatile NavigableMap<LocalDateTime, Double> activeLastMinuteMap = new ConcurrentSkipListMap<>();
    private static EnumMap<FutType, Double> futOpenMap = new EnumMap<>(FutType.class);
    public static EnumMap<FutType, Double> futPrevClose3pmMap = new EnumMap<>(FutType.class);

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
        pr(str(" ****** front fut ******* ", frontFut.symbol(), frontFut.lastTradeDateOrContractMonth()));
        pr(str(" ****** back fut ******* ", backFut.symbol(), backFut.lastTradeDateOrContractMonth()));

        for (FutType f : FutType.values()) {
            futData.put(f, new ConcurrentSkipListMap<>());
            tradesMap.put(f, new ConcurrentSkipListMap<>());
            overnightTradesMap.put(f, new ConcurrentSkipListMap<>());
            futOpenMap.put(f, 0.0);
            futPrevClose3pmMap.put(f, 0.0);
        }

        apcon = ap;
        traderRoll = new XUTraderRoll(ap);

        JLabel currTimeLabel = new JLabel(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        currTimeLabel.setFont(currTimeLabel.getFont().deriveFont(30F));

        JButton bidLimitButton = new JButton("Buy Limit");

        bidLimitButton.addActionListener(l -> {
            pr(" buying limit ");
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimit(bidMap.get(ibContractToFutType(activeFutureCt)), 1.0);
            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o, AutoOrderType.ON_BID));
            apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), " Bidding Limit ", globalIdOrderMap.get(id)));
        });

        JButton offerLimitButton = new JButton("Sell Limit");

        offerLimitButton.addActionListener(l -> {
            pr(" selling limit ");
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimit(askMap.get(ibContractToFutType(activeFutureCt)), 1.0);
            apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o, "offer limit", AutoOrderType.ON_OFFER));
            outputOrderToAutoLog(str(o.orderId(), " Offer Limit ", globalIdOrderMap.get(id)));
        });

        JButton buyOfferButton = new JButton(" Buy Now");
        buyOfferButton.addActionListener(l -> {
            pr(" buy offer ");
            int id = autoTradeID.incrementAndGet();
            Order o = buyAtOffer(askMap.get(ibContractToFutType(activeFutureCt)), 1.0);
            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o, "lift offer",
                    AutoOrderType.LIFT_OFFER));
            apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), " Lift Offer ", globalIdOrderMap.get(id)));
        });

        JButton sellBidButton = new JButton(" Sell Now");
        sellBidButton.addActionListener(l -> {
            pr(" sell bid ");
            int id = autoTradeID.incrementAndGet();
            Order o = sellAtBid(bidMap.get(ibContractToFutType(activeFutureCt)), 1.0);
            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o, "hit bid", AutoOrderType.HIT_BID));
            apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), " Hitting bid ", globalIdOrderMap.get(id)));
        });

        JButton toggleMusicButton = new JButton("停乐");
        toggleMusicButton.addActionListener(l -> soundPlayer.stopIfPlaying());

        JButton detailedButton = new JButton("Detailed:" + detailedPrint.get());
        detailedButton.addActionListener(l -> {
            detailedPrint.set(!detailedPrint.get());
            detailedButton.setText(" Detailed: " + detailedPrint.get());
        });


        JButton musicPlayableButton = new JButton("Music: " + (musicOn.get() ? "ON" : "OFF"));
        musicPlayableButton.addActionListener(l -> {
            musicOn.set(!musicOn.get());
            musicPlayableButton.setText("Music:" + (musicOn.get() ? "ON" : "OFF"));
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
        getPositionButton.addActionListener(l -> {
            for (FutType f : FutType.values()) {
                currentPosMap.put(f, 0);
            }
            apcon.reqPositions(this);
        });

        JButton level2Button = new JButton("level2");
        level2Button.addActionListener(l -> requestLevel2Data());

        refreshButton = new JButton("Refresh");

        refreshButton.addActionListener(l -> {
            String time = (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).getSecond() != 0)
                    ? (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()) :
                    (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString() + ":00");
            currTimeLabel.setText(time);
            xuGraph.fillInGraph(futData.get(ibContractToFutType(activeFutureCt)));
            xuGraph.fillTradesMap(XUTrader.tradesMap.get(ibContractToFutType(activeFutureCt)));
            xuGraph.setName(ibContractToSymbol(activeFutureCt));
            xuGraph.setFut(ibContractToFutType(activeFutureCt));
            xuGraph.setPrevClose(futPrevClose3pmMap.get(ibContractToFutType(activeFutureCt)));
            xuGraph.refresh();
            for (FutType f : FutType.values()) {
                currentPosMap.put(f, 0);
            }
            apcon.reqPositions(getThis());
            repaint();
        });

        computeButton = new JButton("Compute");
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

                for (FutType f : FutType.values()) {
                    currentPosMap.put(f, 0);
                }
                apcon.reqPositions(getThis());
                activeFutLiveOrder = new HashMap<>();
                activeFutLiveIDOrderMap = new HashMap<>();

                apcon.reqLiveOrders(getThis());

                SwingUtilities.invokeLater(() -> {
                    FutType f = ibContractToFutType(activeFutureCt);
                    currTimeLabel.setText(time);
                    xuGraph.fillInGraph(trimDataFromYtd(futData.get(f)));
                    xuGraph.fillTradesMap(tradesMap.get(f));
                    xuGraph.setName(ibContractToSymbol(activeFutureCt));
                    xuGraph.setFut(f);
                    xuGraph.setPrevClose(futPrevClose3pmMap.get(f));
                    xuGraph.refresh();
                    repaint();
                });
            }, 0, 1, TimeUnit.SECONDS);

            ses.scheduleAtFixedRate(() -> {
                observeMATouch();
                requestExecHistory();
            }, 0, 1, TimeUnit.MINUTES);

//            ses.scheduleAtFixedRate(() -> {
//                globalIdOrderMap.entrySet().stream().filter(e -> isInventoryTrade().test(e.getValue().getOrderType()))
//                        .forEach(e -> pr(str("real order ID", e.getValue().getOrder().orderId(), e.getValue())));
//
//                long invOrderCount = globalIdOrderMap.entrySet().stream()
//                        .filter(e -> isInventoryTrade().test(e.getValue().getOrderType())).count();
//                if (invOrderCount >= 1) {
//                    OrderAugmented o = globalIdOrderMap.entrySet().stream()
//                            .filter(e -> isInventoryTrade().test(e.getValue().getOrderType()))
//                            .max(Comparator.comparing(e -> e.getValue().getOrderTime())).map(Map.Entry::getValue)
//                            .orElseThrow(() -> new IllegalStateException(" nothing in last inventory order "));
//
//                    pr("invOrderCount >=1 : last order ", o);
//                    pr("last order T ", o.getOrderTime(), "status", o.getAugmentedOrderStatus()
//                            , " Cancel wait time ", cancelWaitTime(LocalTime.now()));
//
//                    if (o.getAugmentedOrderStatus() != OrderStatus.Filled &&
//                            timeDiffinMinutes(o.getOrderTime(), LocalDateTime.now()) >= cancelWaitTime(LocalTime.now())) {
//
//                        globalIdOrderMap.entrySet().stream()
//                                .filter(e -> isInventoryTrade().test(e.getValue().getOrderType()))
//                                .skip(invOrderCount - 1).peek(e -> pr(str("last order ", e.getValue())))
//                                .forEach(e -> {
//                                    if (e.getValue().getAugmentedOrderStatus() != OrderStatus.Cancelled) {
//                                        apcon.cancelOrder(e.getValue().getOrder().orderId());
//                                        e.getValue().setFinalActionTime(LocalDateTime.now());
//                                        e.getValue().setAugmentedOrderStatus(OrderStatus.Cancelled);
//                                    } else {
//                                        pr(str(e.getValue().getOrder().orderId(), "already cancelled"));
//                                    }
//                                });
//                        pr(str(LocalTime.now(), " killing last unfilled orders"));
//                        pr(" releasing inv barrier + semaphore ");
//                        inventoryBarrier.reset(); //resetting inventory barrier
//                        inventorySemaphore = new Semaphore(1); // release inv semaphore
//                    }
//                }
//            }, 0, 1, TimeUnit.MINUTES);
        });

        JButton stopComputeButton = new JButton("Stop Processing");
        stopComputeButton.addActionListener(l -> ses2.shutdown());

        JButton execButton = new JButton("Exec");
        execButton.addActionListener(l -> requestExecHistory());

        processTradesButton = new JButton("Process");
        processTradesButton.addActionListener(l -> ses2.scheduleAtFixedRate(() -> {
            SwingUtilities.invokeLater(() -> {
                XUTrader.clearLog();
                XUTrader.updateLog("**************************************************************");
            });
            XUTrader.computeTradeMapActive();
        }, 0, 10, TimeUnit.SECONDS));

        JButton getData = new JButton("Data");
        getData.addActionListener(l -> loadXU());

        graphButton = new JButton("graph");
        graphButton.addActionListener(l -> {
            xuGraph.setNavigableMap(futData.get(ibContractToFutType(activeFutureCt)), displayPred);
            xuGraph.refresh();
            repaint();
        });

        JToggleButton showTodayOnly = new JToggleButton(" Today Only ");
        showTodayOnly.addActionListener(l -> {
            if (showTodayOnly.isSelected()) {
                displayPred = e -> e.toLocalDate().equals(LocalDate.now())
                        && e.toLocalTime().isAfter(ltof(8, 59));
            } else {
                displayPred = e -> true;
            }
        });

        showTradesButton = new JToggleButton("Show Trades");
        showTradesButton.addActionListener(l -> showTrades.set(showTradesButton.isSelected()));

        JButton cancelAllOrdersButton = new JButton("Cancel Orders");
        cancelAllOrdersButton.addActionListener(l -> {
            apcon.cancelAllOrders();
            activeFutLiveOrder = new HashMap<>();
            activeFutLiveIDOrderMap = new HashMap<>();
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
            activeFutureCt = frontFut;
        });

        frontFutButton.setSelected(activeFutureCt.lastTradeDateOrContractMonth().equalsIgnoreCase(
                TradingConstants.A50_FRONT_EXPIRY));

        JRadioButton backFutButton = new JRadioButton("Back");
        backFutButton.addActionListener(l -> {
            graphPred = e -> e.getKey().equals(FutType.BackFut);
            activeFutureCt = backFut;
        });

        backFutButton.setSelected(activeFutureCt.lastTradeDateOrContractMonth().equalsIgnoreCase(
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
        controlPanel2.add(showTradesButton);
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
                            apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o,
                                    AutoOrderType.ON_BID));
//                            outputOrderToAutoLog(str(o.orderId(), " MANUAL BID || bid price ", bidPrice,
//                                    " Checking order ", checkIfOrderPriceMakeSense(bidPrice)));
                            outputOrderToAutoLog(str(o.orderId(), " Manual Bid Limit ", l.getName(),
                                    globalIdOrderMap.get(id)));
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
                            apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                            globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o,
                                    AutoOrderType.ON_OFFER));
//                            outputOrderToAutoLog(str(o.orderId(), " MANUAL OFFER||offer price "
//                                    , offerPrice, " Checking order ", checkIfOrderPriceMakeSense(offerPrice)));
                            outputOrderToAutoLog(str(o.orderId(), " Manual Offer Limit ",
                                    l.getName(), globalIdOrderMap.get(id)));

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

    void openingProcess() {
        pr(" xu opening process ");
        apcon.reqPositions(this);
        apcon.reqDeepMktData(activeFutureCt, 10, this);
        refreshButton.doClick();
        computeButton.doClick();
        requestExecHistory();
        //processTradesButton.doClick();
        loadXU();
        graphButton.doClick();
        showTradesButton.doClick();
    }

    void openingRefresh() {
        //refreshButton.doClick();
        processTradesButton.doClick();
    }


    static void set20DayBullBear() {
        String ticker = "sh000016";
        if (ma20Map.getOrDefault(ticker, 0.0) == 0.0 || priceMap.getOrDefault(ticker, 0.0) == 0.0) {
            _20DayMA = MASentiment.Directionless;
        } else if (priceMap.get(ticker) < ma20Map.get(ticker)) {
            _20DayMA = MASentiment.Bearish;
        } else if (priceMap.get(ticker) > ma20Map.get(ticker)) {
            _20DayMA = MASentiment.Bullish;
        }
    }

    private static String get20DayBullBear() {
        String ticker = "sh000001";
        return str(_20DayMA, "Price: ", priceMap.getOrDefault(ticker, 0.0), "MA20: ",
                ma20Map.getOrDefault(ticker, 0.0));
    }

    private static int getRecentPmCh(LocalTime lt, String index) {
        //pr(" getting pmchy yest/today", pmchyMap.getOrDefault(index, 0), getPmchToday(ltof, index));
        if (lt.isAfter(ltof(5, 0)) && lt.isBefore(ltof(15, 0))) {
            return getPmchY(lt, index);
        } else {
            return getPmchToday(lt, index);
        }
    }

    private static int getPmchY(LocalTime lt, String index) {
        //pr(" getting pmchy yest/today", pmchyMap.getOrDefault(index, 0), getPmchToday(ltof, index));
        return pmchyMap.getOrDefault(index, 0);
    }

    private static int getPmchToday(LocalTime t, String ticker) {
        if (t.isAfter(ltof(5, 0)) && t.isBefore(ltof(13, 0))) {
            return 0;
        }
        if (priceMapBar.containsKey(ticker) && priceMapBar.get(ticker).size() > 0 &&
                priceMapBar.get(ticker).lastKey().isAfter(ltof(13, 0))) {
            double maxV = priceMapBar.get(ticker).entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max()
                    .orElse(0.0);
            double minV = priceMapBar.get(ticker).entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min()
                    .orElse(0.0);
            double pmStart = priceMapBar.get(ticker).ceilingEntry(ltof(13, 0)).getValue().getOpen();
            double last = priceMapBar.get(ticker).floorEntry(ltof(15, 5)).getValue().getClose();

            if (maxV != minV && maxV != 0.0 && minV != 0.0 && pmStart != 0.0 && last != 0.0) {
                return (int) Math.round(100d * (last - pmStart) / (maxV - minV));
            } else {
                return 0;
            }
        }
        return 0;
    }

    private static int getRecentClosePerc(LocalTime lt, String index) {
        if (lt.isBefore(ltof(15, 0))) {
            return getClosePercY(lt, index);
        } else {
            return getPreCloseLastPercToday(lt, index);
        }
    }

    private static int getClosePercY(LocalTime lt, String index) {
        return closePercYMap.getOrDefault(index, 0);
    }

    private static int getPreCloseLastPercToday(LocalTime t, String ticker) {
        if (priceMapBar.containsKey(ticker) && priceMapBar.get(ticker).size() > 0
                && priceMapBar.get(ticker).firstKey().isBefore(ltof(15, 0))
                && t.isAfter(ltof(9, 30))) {
            double maxV = priceMapBar.get(ticker).entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max()
                    .orElse(0.0);
            double minV = priceMapBar.get(ticker).entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min()
                    .orElse(0.0);
            double last = priceMapBar.get(ticker).floorEntry(ltof(15, 0)).getValue().getClose();
            if (maxV != minV && maxV != 0.0 && minV != 0.0 && last != 0.0) {
                return (int) Math.round(100d * (last - minV) / (maxV - minV));
            } else {
                return 50;
            }
        }
        return 50;
    }

    public static void processMain(LocalDateTime ldt, double price) {
        LocalTime lt = ldt.toLocalTime();
        double currDelta = getNetPtfDelta();
        boolean maxAfterMin = checkf10maxAftermint(INDEX_000016);
        boolean maxAbovePrev = checkf10MaxAbovePrev(INDEX_000016);

        NavigableMap<LocalDateTime, SimpleBar> futdata = trimDataFromYtd(futData.get(ibContractToFutType(activeFutureCt)));
        //int pmChgY = getPercentileChgFut(futdata, getPrevTradingDate(futdata));
        int pmChgY = getRecentPmCh(ldt.toLocalTime(), INDEX_000001);
        int closePercY = getRecentClosePerc(ldt.toLocalTime(), INDEX_000001);
        int openPercY = getOpenPercentile(futdata, getPrevTradingDate(futdata));
        //int pmChg = getPercentileChgFut(futdata, getTradeDate(futdata.lastKey()));
        int pmChg = getPmchToday(ldt.toLocalTime(), INDEX_000001);
        int lastPerc = getPreCloseLastPercToday(ldt.toLocalTime(), INDEX_000001);

        if (Math.abs(currDelta) > 2000000d) {
            if (currDelta > 2000000d) {
                noMoreBuy.set(true);
            } else if (currDelta < -2000000d) {
                noMoreSell.set(true);
            }
        } else {
            noMoreBuy.set(false);
            noMoreSell.set(false);
        }

        if (detailedPrint.get() && lt.getSecond() < 10) {
            pr(lt.truncatedTo(ChronoUnit.SECONDS),
                    "||20DayMA ", _20DayMA, "||maxT>MinT: ", maxAfterMin, "||max>PrevC", maxAbovePrev,
                    "closeY", closePercY, "openPercY", openPercY, "pmchgy", pmChgY,
                    "pmch", pmChg, "lastP", lastPerc, "delta range", getBearishTarget(), getBullishTarget()
                    , "currDelta ", Math.round(currDelta), "a50 hilo dir: ", indexHiLoDirection
                    , "no more buy/sell", noMoreBuy.get(), noMoreSell.get());
        }

        if (!globalTradingOn.get()) {
            if (detailedPrint.get()) {
                pr(" global trading off ");
            }
            return;
        }

        if (isStockNoonBreak(ldt.toLocalTime())) {
            return;
        }

        futOpenDeviationTrader(ldt, price); // all day
        futOpenTrader(ldt, price, pmChgY); // until 9:30
        futHiloTrader(ldt, price); // until 10
        //futDayMATrader(ldt, price);
        //futFastMATrader(ldt, price);
        closeLiqTrader(ldt, price); // after 14:55
        percentileMATrader(ldt, price, pmChgY); // all day


        //futHiloAccu(ldt, price);
        //futPCProfitTaker(ldt, price);
        //indexFirstTickTrader(ldt, price);
        //indexOpenDeviationTrader(ldt, price, pmChgY);
        //indexHiLoTrader(ldt, price, pmChgY);
        //firstTickMAProfitTaker(ldt, price);
        //closeProfitTaker(ldt, price);

        if (!(currDelta > DELTA_HARD_LO_LIMIT && currDelta < DELTA_HARD_HI_LIMIT)) {
            //pr(" curr delta is outside range ");
            return;
        }
        testTrader(ldt, price);
        overnightTrader(ldt, price);
    }


    private static boolean checkf10maxAftermint(String name) {
        if (!priceMapBar.containsKey(name) || priceMapBar.get(name).size() < 2) {
            return false;
        } else if (priceMapBar.get(name).lastKey().isBefore(ltof(9, 40))) {
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

            if (detailedPrint.get() && LocalTime.now().isBefore(ltof(10, 0))) {
                pr(name, "checkf10:max min", maxT, minT);
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
            if (detailedPrint.get() && LocalTime.now().isBefore(ltof(10, 0))) {
                pr(name, "checkf10max ", f10max, "close", closeMap.get(name)
                        , "f10max>close", f10max > closeMap.get(name));
            }
            return f10max > closeMap.get(name);
        }
    }

    private static int getPercentileChgYFut() {
        NavigableMap<LocalDateTime, SimpleBar> futdata = futData.get(ibContractToFutType(activeFutureCt));
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

            double prevClose = futdata.floorEntry(LocalDateTime.of(prevDate, ltof(15, 0)))
                    .getValue().getClose();

            double pmOpen = futdata.floorEntry(LocalDateTime.of(prevDate, ltof(13, 0)))
                    .getValue().getOpen();

            if (prevMax == 0.0 || prevMin == 0.0 || prevClose == 0.0 || pmOpen == 0.0) {
                return 0;
            } else {
                return (int) Math.round(100d * (prevClose - pmOpen) / (prevMax - prevMin));
            }
        }
    }

    private static int getPercentileChgFut(NavigableMap<LocalDateTime, SimpleBar> futdata, LocalDate dt) {
        if (futdata.size() <= 2) {
            return 0;
        } else if (futdata.lastKey().isAfter(LocalDateTime.of(dt, ltof(13, 0)))) {
            double prevMax = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(e -> e.getValue().getHigh()).orElse(0.0);

            double prevMin = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow()))
                    .map(e -> e.getValue().getLow()).orElse(0.0);

            double prevClose = futdata.floorEntry(LocalDateTime.of(dt, ltof(15, 0)))
                    .getValue().getClose();

            double pmOpen = futdata.floorEntry(LocalDateTime.of(dt, ltof(13, 0)))
                    .getValue().getOpen();

            if (prevMax == 0.0 || prevMin == 0.0 || prevClose == 0.0 || pmOpen == 0.0) {
                return 0;
            } else {

//                pr("getPercChgFut " +
//                        "localdate , max, min, pmO, prevC ", dt, prevMax, prevMin, pmOpen, prevClose);

                return (int) Math.round(100d * (prevClose - pmOpen) / (prevMax - prevMin));
            }
        }
        return 0;
    }

    private static int getOpenPercentile(NavigableMap<LocalDateTime, SimpleBar> futdata, LocalDate dt) {
        if (futdata.size() <= 2) {
            return 0;
        } else if (futdata.firstKey().isBefore(LocalDateTime.of(dt, ltof(9, 31)))) {
            double prevOpen = futdata.ceilingEntry(LocalDateTime.of(dt, ltof(9, 30)))
                    .getValue().getOpen();

            double prevMax = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .max(Comparator.comparingDouble(e -> e.getValue().getHigh()))
                    .map(e -> e.getValue().getHigh()).orElse(0.0);

            double prevMin = futdata.entrySet().stream().filter(e -> e.getKey().toLocalDate().equals(dt))
                    .filter(e -> checkTimeRangeBool(e.getKey().toLocalTime(), 9, 29, 15, 0))
                    .min(Comparator.comparingDouble(e -> e.getValue().getLow()))
                    .map(e -> e.getValue().getLow()).orElse(0.0);

            if (prevMax == 0.0 || prevMin == 0.0 || prevOpen == 0.0) {
                return 0;
            } else {
                return (int) Math.round(100d * (prevOpen - prevMin) / (prevMax - prevMin));
            }
        }
        return 0;
    }


    static void updateLastMinuteMap(LocalDateTime ldt, double freshPrice) {
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
        switch (_20DayMA) {
            case Bullish:
                return 500000;
            case Bearish:
                return 0.0;
        }
        return 0.0;
    }

    private static double getBearishTarget() {
        switch (_20DayMA) {
            case Bullish:
                return 0.0;
            case Bearish:
                return -500000;
        }
        return 0.0;
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
                    if ((e.getKey() == FutType.PreviousFut &&
                            LocalDate.parse(TradingConstants.getFutLastExpiry(), DateTimeFormatter.ofPattern("yyyyMMdd"))
                                    .equals(LocalDate.now()) && LocalTime.now().isAfter(ltof(15, 0)))
                            || (e.getKey() == FutType.FrontFut &&
                            LocalDate.parse(TradingConstants.A50_FRONT_EXPIRY, DateTimeFormatter.ofPattern("yyyyMMdd"))
                                    .equals(LocalDate.now()) && LocalTime.now().isBefore(ltof(15, 0)))) {
//                        pr(" get expiring delta ", e.getValue(), futPriceMap.getOrDefault(e.getKey(),
//                                SinaStock.FTSE_OPEN), ChinaPosition.fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey().getTicker(),
//                                "CNY"), 1.0));
                        return e.getValue() * futPriceMap.getOrDefault(e.getKey(), SinaStock.FTSE_OPEN)
                                * ChinaPosition.fxMap.getOrDefault(currencyMap.getOrDefault(e.getKey().getTicker(),
                                "CNY"), 1.0);
                    } else {
                        return 0.0;
                    }
                }).sum();
    }

    static double getFutDelta() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate frontMonthExpiryDate = LocalDate.parse(TradingConstants.A50_FRONT_EXPIRY,
                DateTimeFormatter.ofPattern("yyyyMMdd"));
        return currentPosMap.entrySet().stream()
                .mapToDouble(e -> {
                    double factor = 1.0;
                    if (e.getKey() == FutType.PreviousFut) {
                        return 0.0;
                    } else if (e.getKey() == FutType.FrontFut && frontMonthExpiryDate
                            .equals(now.toLocalDate()) && now.toLocalTime().isAfter(ltof(15, 0))) {
                        return 0.0;
                    }

                    if (now.isAfter(LocalDateTime.of(frontMonthExpiryDate.minusDays(3L), ltof(15, 0)))) {
                        factor = HOURS.between(LocalDateTime.of(frontMonthExpiryDate.minusDays(2L),
                                ltof(15, 0))
                                , LocalDateTime.of(frontMonthExpiryDate, ltof(15, 0))) / 72d;
                    }
                    return Math.max(0.0, factor) * e.getValue() * futPriceMap.getOrDefault(e.getKey(), SinaStock.FTSE_OPEN)
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
     * only output upon a cross
     */
    private void observeMATouch() {
        NavigableMap<LocalDateTime, SimpleBar> futprice1m = (futData.get(ibContractToFutType(activeFutureCt)));

        if (futprice1m.size() < 2) return;

        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(futprice1m, 10);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(futprice1m, 20);

        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry((smaLong.lastKey())).getValue();

        double pd = getPD(futprice1m.lastEntry().getValue().getClose());
        int percentile = getPercentileForLast(futData.get(ibContractToFutType(activeFutureCt)));
        soundPlayer.stopIfPlaying();
        if (smaShort.size() > 0) {
            String msg = str("**MA CROSS**"
                    , "20day", get20DayBullBear()
                    , "||T:", LocalTime.now().truncatedTo(MINUTES)
                    , "||Last Short Long:", r(maShortLast), r(maLongLast)
                    , "||seclast shortlong:", r(maShortSecLast), r(maLongSecLast)
                    , "||Index:", r(getIndexPrice())
                    , "||PD:", r10000(pd)
                    , "||2 Day P%", percentile);

            if (maShortLast > maLongLast && maShortSecLast <= maLongSecLast) {
                outputToAutoLog(" bullish cross ");
                outputToAutoLog(msg);
            } else if (maShortLast < maLongLast && maShortSecLast >= maLongSecLast) {
                outputToAutoLog(" bearish cross ");
                outputToAutoLog(msg);
            }
        }
    }

    /**
     * fut hilo trader
     *
     * @param nowMilli   time
     * @param freshPrice futprice
     */
    private static void futHiloTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        String futSymbol = ibContractToSymbol(activeFutureCt);
        FutType futType = ibContractToFutType(activeFutureCt);
        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(futType);
        int baseSize = 1;

        if (lt.isAfter(ltof(10, 0))) {
            return;
        }

        if (priceMapBarDetail.get(futSymbol).size() <= 1) {
            return;
        }

        double bid = bidMap.get(futType);
        double offer = askMap.get(futType);

        long futHiloOrdersNum = getOrderSizeForTradeType(FUT_HILO);
        LocalDateTime lastFutHiloTime = getLastOrderTime(FUT_HILO);

        if (futHiloOrdersNum >= MAX_ORDER_SIZE) {
            return;
        }

        long milliBtwnLastTwoOrders = lastTwoOrderMilliDiff(FUT_HILO);
        long tSinceLastOrder = tSincePrevOrderMilli(FUT_HILO, nowMilli);

        int waitTimeSec = 60 * 10;

        NavigableMap<LocalTime, Double> futPrice = priceMapBarDetail.get(futSymbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(8, 58)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));


        double futOpen = futPrice.firstEntry().getValue();
        double futLast = futPrice.lastEntry().getValue();
        LocalTime lastKey = futPrice.lastKey();

        double maxP = futPrice.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minP = futPrice.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalTime maxTPre10 = getFirstMaxTPred(futPrice, t -> t.isBefore(ltof(10, 0)));
        LocalTime minTPre10 = getFirstMinTPred(futPrice, t -> t.isBefore(ltof(10, 0)));

        LocalTime maxT = getFirstMaxTPred(futPrice, t -> t.isAfter(ltof(8, 59, 0)));
        LocalTime minT = getFirstMinTPred(futPrice, t -> t.isAfter(ltof(8, 59, 0)));

        if (!manualFutHiloDirection.get()) {
            if (lt.isBefore(ltof(9, 0, 0))) {
                manualFutHiloDirection.set(true);
            } else {
                if (maxT.isAfter(minT)) {
                    futHiLoDirection = Direction.Long;
                    manualFutHiloDirection.set(true);
                } else if (minT.isAfter(maxT)) {
                    futHiLoDirection = Direction.Short;
                    manualFutHiloDirection.set(true);
                } else {
                    futHiLoDirection = Direction.Flat;
                }
            }
        }

        double last = futPrice.lastEntry().getValue();

        if (detailedPrint.get()) {
            pr("futHilo dir:", futHiLoDirection, "#", futHiloOrdersNum, "max min"
                    , maxP, minP, "open/last ", futOpen, futLast, "maxT, minT", maxT, minT);
        }

        int buySize = baseSize * ((futHiloOrdersNum == 0 || futHiloOrdersNum == 5) ? 1 : 2);
        int sellSize = baseSize * ((futHiloOrdersNum == 0 || futHiloOrdersNum == 5) ? 1 : 2);

        if (lt.isAfter(ltof(8, 59)) &&
                (SECONDS.between(lastFutHiloTime, nowMilli) >= waitTimeSec || futHiLoDirection == Direction.Flat)) {
            if (!noMoreBuy.get() && (last > maxP || maxT.isAfter(minT)) && futHiLoDirection != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(offer, buySize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_HILO));
                apcon.placeOrModifyOrder(activeFutureCt, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "fut hilo buy #:", futHiloOrdersNum,
                        globalIdOrderMap.get(id), " max min last dir", r(maxP), r(minP), r(last), futHiLoDirection,
                        "|bid ask spread", bid, offer, Math.round(10000d * (offer / bid - 1)), "bp",
                        "last freshprice ", last, freshPrice, "pre10:maxT minT ", maxTPre10, minTPre10));
                futHiLoDirection = Direction.Long;
            } else if (!noMoreSell.get() && (last < minP || minT.isAfter(maxT)) && futHiLoDirection != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(bid, sellSize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_HILO));
                apcon.placeOrModifyOrder(activeFutureCt, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "fut hilo sell #:", futHiloOrdersNum,
                        globalIdOrderMap.get(id), "max min last dir", r(maxP), r(minP), r(last), futHiLoDirection,
                        "|bid ask spread", bid, offer, Math.round(10000d * (offer / bid - 1)), "bp",
                        "last freshprice", last, freshPrice, "pre10:maxT minT ", maxTPre10, minTPre10));
                futHiLoDirection = Direction.Short;
            }
        }
    }

    /**
     * fut hi lo accumulator
     *
     * @param nowMilli   time
     * @param freshPrice futprice
     */

    private static void futHiloAccu(LocalDateTime nowMilli, double freshPrice) {
        String futSymbol = ibContractToSymbol(activeFutureCt);
        NavigableMap<LocalTime, Double> futPrice = priceMapBarDetail.get(futSymbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(8, 59)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));

        LocalDateTime lastOrderTime = getLastOrderTime(FUT_HILO_ACCU);
        LocalTime maxTPre10 = getFirstMaxTPred(futPrice, t -> t.isBefore(ltof(10, 0)));
        LocalTime minTPre10 = getFirstMaxTPred(futPrice, t -> t.isBefore(ltof(10, 0)));

        int todayPerc = getPercentileForDoubleX(futPrice, freshPrice);

        if (SECONDS.between(lastOrderTime, nowMilli) >= 900) {
            if (!noMoreBuy.get() && maxTPre10.isAfter(minTPre10) && todayPerc < LO_PERC_WIDE) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, 1, Types.TimeInForce.DAY);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_HILO_ACCU));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "fut hilo accu", globalIdOrderMap.get(id)));
            } else if (!noMoreSell.get() && maxTPre10.isBefore(minTPre10) && todayPerc > HI_PERC_WIDE) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, 1, Types.TimeInForce.DAY);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_HILO_ACCU));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "fut hilo decu", globalIdOrderMap.get(id)));
            }
        }
    }


    static void futHiloKnockoutTrader(LocalDateTime nowMilli, double freshPrice) {
        FutType ft = ibContractToFutType(activeFutureCt);
        double bid = bidMap.get(ft);
        double offer = askMap.get(ft);
        long futHiloOrdersNum = getOrderSizeForTradeType(FUT_HILO);
        long futKOOrdersNum = getOrderSizeForTradeType(FUT_KO);

        if (futHiloOrdersNum == 0) {
            return;
        }

        if (futHiLoDirection == Direction.Flat) {
            return;
        }

        OrderStatus lastHiLoOrderStatus = getLastOrderStatusForType(FUT_HILO);
        if (lastHiLoOrderStatus != OrderStatus.Filled) {
            return;
        }

        OrderStatus lastKOOrderStatus = getLastOrderStatusForType(FUT_KO);
        if (futKOOrdersNum != 0 && lastKOOrderStatus != OrderStatus.Filled) {
            //getMoreAggressiveFill(FUT_KO, bid, offer);
            cancelOrdersByType(FUT_KO);
            return;
        }

        double previousHiloLimit = globalIdOrderMap.entrySet().stream().filter(e -> e.getValue()
                .getOrderType() == FUT_HILO).max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .filter(e -> e.getValue().getAugmentedOrderStatus() == OrderStatus.Filled)
                .map(e -> e.getValue().getOrder().lmtPrice()).orElse(0.0);

        double prevSize = globalIdOrderMap.entrySet().stream().filter(e -> e.getValue()
                .getOrderType() == FUT_HILO).max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .filter(e -> e.getValue().getAugmentedOrderStatus() == OrderStatus.Filled)
                .map(e -> e.getValue().getOrder().totalQuantity()).orElse(0.0);

        if (previousHiloLimit != 0.0 && prevSize != 0.0) {
            if (futHiLoDirection == Direction.Long) {
                if (freshPrice < previousHiloLimit) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeOfferLimitTIF(freshPrice, prevSize, Types.TimeInForce.DAY);
                    globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_KO));
                    apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                    outputOrderToAutoLog(str(o.orderId(), "fut hilo buy KO", globalIdOrderMap.get(id)));
                    futHiLoDirection = Direction.Flat;
                }
            } else if (futHiLoDirection == Direction.Short) {
                if (freshPrice > previousHiloLimit) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeBidLimitTIF(freshPrice, prevSize, Types.TimeInForce.DAY);
                    globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_KO));
                    apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                    outputOrderToAutoLog(str(o.orderId(), "fut hilo sell KO", globalIdOrderMap.get(id)));
                    futHiLoDirection = Direction.Flat;
                }
            }
        }
    }


    /**
     * take profits of PC trades
     *
     * @param nowMilli   time
     * @param freshPrice last futures price
     */
    private static void futPCProfitTaker(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        String futSymbol = ibContractToSymbol(activeFutureCt);
        FutType futType = ibContractToFutType(activeFutureCt);

        double pcSignedQ = getOrderTotalSignedQForTypeFilled(FUT_OPEN_DEVI);
        double ptSignedQ = getOrderTotalSignedQForTypeFilled(FUT_PC_PROFIT_TAKER);
        LocalDateTime lastPTTime = getLastOrderTime(FUT_PC_PROFIT_TAKER);
        LocalDateTime lastPCTime = getLastOrderTime(FUT_OPEN_DEVI);
        int percentile = getPercentileForDouble(priceMapBarDetail.get(futSymbol));
        double lastFut = priceMapBarDetail.get(futSymbol).lastEntry().getValue();
        double pc = fut5amClose.get(futType);

        pr(" fut PC profit taker ", "pcQ, ptakeQ,", pcSignedQ, ptSignedQ, "perc", percentile,
                "last fut/pc", lastFut, pc);

        if (SECONDS.between(lastPTTime, nowMilli) > 60 * 15 && SECONDS.between(lastPCTime, nowMilli) > 60 * 5) {
            if (!noMoreBuy.get() && percentile < 1 && futOpenDevDirection == Direction.Short
                    && pcSignedQ < 0 && (pcSignedQ + ptSignedQ) < 0 && lastFut < pc) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, 1, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_PC_PROFIT_TAKER));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "fut PC PT buy", globalIdOrderMap.get(id)));
            } else if (!noMoreSell.get() && percentile > 99 && futOpenDevDirection == Direction.Long
                    && pcSignedQ > 0 && (pcSignedQ + ptSignedQ) > 0 && lastFut > pc) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, 1, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_PC_PROFIT_TAKER));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "fut PC PT sell", globalIdOrderMap.get(id)));
            }
        }
    }

    /**
     * fut pc deviation trader, trade delta based on position relative to last close at 4:44am
     *
     * @param nowMilli   time
     * @param freshPrice fut price
     */
    private static void futOpenDeviationTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        String futSymbol = ibContractToSymbol(activeFutureCt);
        FutType futtype = ibContractToFutType(activeFutureCt);
        if (fut5amClose.getOrDefault(futtype, 0.0) == 0.0) {
            return;
        }

        double prevClose = fut5amClose.get(futtype);

        Map.Entry<LocalTime, Double> firstEntry = priceMapBarDetail.get(futSymbol).firstEntry();
        double lastFut = priceMapBarDetail.get(futSymbol).lastEntry().getValue();

        LocalDateTime lastFutOpenOrderTime = getLastOrderTime(FUT_OPEN_DEVI);
        long milliLast2 = lastTwoOrderMilliDiff(FUT_OPEN_DEVI);
        long numOrders = getOrderSizeForTradeType(FUT_OPEN_DEVI);
        int waitTimeSec = (milliLast2 < 60000) ? 300 : 10;
        double futOpen = firstEntry.getValue();

        if (detailedPrint.get()) {
            pr(lt.truncatedTo(ChronoUnit.SECONDS),
                    "fut open devi: ", "#:", numOrders, "PC: ", prevClose, "first En", firstEntry,
                    "last entry", lastFut, "fresh:", freshPrice, "dir:", futOpenDevDirection, " last order T: ",
                    lastFutOpenOrderTime.toLocalTime(), "secSinceLastT", SECONDS.between(lastFutOpenOrderTime, nowMilli),
                    "manual PC dir:", manualfutOpenDevDirection.get());
        }

        if (numOrders >= 6) {
            return;
        }

        if (!manualfutOpenDevDirection.get()) {
            if (lt.isBefore(ltof(9, 0, 0))) {
                manualfutOpenDevDirection.set(true);
            } else {
                if (freshPrice > futOpen) {
                    futOpenDevDirection = Direction.Long;
                    manualfutOpenDevDirection.set(true);
                } else if (freshPrice < futOpen) {
                    futOpenDevDirection = Direction.Short;
                    manualfutOpenDevDirection.set(true);
                } else {
                    futOpenDevDirection = Direction.Flat;
                }
            }
        }

        int baseSize = 1;
        int buySize = baseSize * ((numOrders == 0 || numOrders == 5) ? 1 : 1);
        int sellSize = baseSize * ((numOrders == 0 || numOrders == 5) ? 1 : 1);
        double buyPrice = Math.min(freshPrice, roundToXUPriceAggressive(futOpen, Direction.Long));
        double sellPrice = Math.max(freshPrice, roundToXUPriceAggressive(futOpen, Direction.Short));

        if (SECONDS.between(lastFutOpenOrderTime, nowMilli) > waitTimeSec) {
            if (!noMoreBuy.get() && freshPrice > futOpen && futOpenDevDirection != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(buyPrice, buySize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_OPEN_DEVI));
                apcon.placeOrModifyOrder(activeFutureCt, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "fut Open dev BUY #:", numOrders, globalIdOrderMap.get(id),
                        "open, last ", futOpen, freshPrice));
                futOpenDevDirection = Direction.Long;
            } else if (!noMoreSell.get() && freshPrice < futOpen && futOpenDevDirection != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(sellPrice, sellSize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_OPEN_DEVI));
                apcon.placeOrModifyOrder(activeFutureCt, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "fut Open dev SELL #:", numOrders, globalIdOrderMap.get(id),
                        "open, last ", futOpen, freshPrice));
                futOpenDevDirection = Direction.Short;
            }
        }
    }

    /**
     * fut day ma trader
     *
     * @param nowMilli   now
     * @param freshPrice price
     */
    private static void futDayMATrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        String futSymbol = ibContractToSymbol(activeFutureCt);
        FutType f = ibContractToFutType(activeFutureCt);

        NavigableMap<LocalTime, Double> futPrice = priceMapBarDetail.get(futSymbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(8, 59, 0)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));
        int shorterMA = 100;
        int longerMA = 200;
        long numOrder = getOrderSizeForTradeType(FUT_DAY_MA);
        double totalFilledNonMAOrderSize = getTotalFilledOrderSignedQPred(isNotMA());
        double totalFutMASignedQ = getOrderTotalSignedQForTypeFilled(FUT_DAY_MA);

        LocalDateTime lastOrderTime = getLastOrderTime(FUT_DAY_MA);

        if (numOrder > 20) {
            if (detailedPrint.get()) {
                pr(" fut day ma trader exceeding size 20");
            }
            return;
        }

        Types.Action lastAction = getLastAction(FUT_DAY_MA);
        int perc = getPercentileForDouble(futPrice);

        NavigableMap<LocalTime, Double> smaShort = getMAGenDouble(futPrice, shorterMA);
        NavigableMap<LocalTime, Double> smaLong = getMAGenDouble(futPrice, longerMA);

        if (smaShort.size() <= 2 || smaLong.size() <= 2) {
            return;
        }

        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry((smaLong.lastKey())).getValue();


        if (perc < 10 && !noMoreBuy.get() && maShortLast > maLongLast && maShortSecLast <= maLongSecLast
                && lastAction != Types.Action.BUY && (totalFilledNonMAOrderSize < 0 &&
                (totalFilledNonMAOrderSize + totalFutMASignedQ < 0))) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimit(freshPrice, 1);
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_DAY_MA));
            apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "fut day MA buy #:", numOrder, "perc", perc
                    , "last:shortlong", maShortLast, maLongLast, "secLast:SL", maShortSecLast, maLongSecLast,
                    "other size, MA size ", totalFilledNonMAOrderSize, totalFutMASignedQ,
                    "last action ", lastAction, "last order T:", lastOrderTime));
        } else if (perc > 90 && !noMoreSell.get() && maShortLast < maLongLast && maShortSecLast >= maLongSecLast
                && lastAction != Types.Action.SELL && (totalFilledNonMAOrderSize > 0 &&
                (totalFilledNonMAOrderSize + totalFutMASignedQ > 0))) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimit(freshPrice, 1);
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_DAY_MA));
            apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "fut day MA sell #:", numOrder, "perc", perc
                    , "last:shortlong", maShortLast, maLongLast, "secLast:SL", maShortSecLast, maLongSecLast,
                    "other size, MA size ", totalFilledNonMAOrderSize, totalFutMASignedQ,
                    "last action ", lastAction, "last order T", lastOrderTime));
        }
    }

    /**
     * fut fast ma trader
     *
     * @param nowMilli   now
     * @param freshPrice price
     */
    private static void futFastMATrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        String futSymbol = ibContractToSymbol(activeFutureCt);
        FutType f = ibContractToFutType(activeFutureCt);

        NavigableMap<LocalTime, Double> futPrice = priceMapBarDetail.get(futSymbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(8, 59, 0)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));
        int shorterMA = 100;
        int longerMA = 200;
        long numOrder = getOrderSizeForTradeType(FUT_FAST_MA);

        double totalFilledNonMAOrderSize = getTotalFilledOrderSignedQPred(isNotMA());
        double totalFutFastMASignedQ = getOrderTotalSignedQForTypeFilled(FUT_FAST_MA);

        LocalTime lastKey = futPrice.lastKey();
        int perc = getPercentileForDoublePred(futPrice, e -> e.isAfter(lastKey.minusMinutes(15)));

        NavigableMap<LocalTime, Double> smaShort = getMAGenDouble(futPrice, shorterMA);
        NavigableMap<LocalTime, Double> smaLong = getMAGenDouble(futPrice, longerMA);


        Types.Action lastAction = getLastAction(FUT_FAST_MA);
        LocalDateTime lastOrderTime = getLastOrderTime(FUT_FAST_MA);

        if (numOrder > 10) {
            if (detailedPrint.get()) {
                pr(" fut fast ma trader exceeding size 20");
            }
            return;
        }


        if (smaShort.size() <= 2 || smaLong.size() <= 2) {
            return;
        }

        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry((smaLong.lastKey())).getValue();

        if (perc < 10 && !noMoreBuy.get() && maShortLast > maLongLast && maShortSecLast <= maLongSecLast &&
                lastAction != Types.Action.BUY && (totalFilledNonMAOrderSize < 0 &&
                (totalFilledNonMAOrderSize + totalFutFastMASignedQ < 0))) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimit(freshPrice, 1);
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_FAST_MA));
            apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "fut fast MA buy #:", numOrder, "perc", perc
                    , "lastSL", maShortLast, maLongLast, "secLast:SL:", maShortSecLast, maLongSecLast,
                    "other size, MA size ", totalFilledNonMAOrderSize, totalFutFastMASignedQ,
                    "last action ", lastAction, "last Action T:", lastOrderTime));
        } else if (perc > 90 && !noMoreSell.get() && maShortLast < maLongLast && maShortSecLast >= maLongSecLast &&
                lastAction != Types.Action.SELL && (totalFilledNonMAOrderSize > 0 &&
                (totalFilledNonMAOrderSize + totalFutFastMASignedQ > 0))) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimit(freshPrice, 1);
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_FAST_MA));
            apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
            outputOrderToAutoLog(str(o.orderId(), "fut fast MA sell #:", numOrder, "perc", perc
                    , "last:SL", maShortLast, maLongLast, "secLast:SL", maShortSecLast, maLongSecLast,
                    "other size, MA size ", totalFilledNonMAOrderSize, totalFutFastMASignedQ,
                    "last action ", lastAction, "last action T:", lastOrderTime));
        }
    }


    /**
     * fut trades at fut open at 9am
     *
     * @param nowMilli   time now
     * @param freshPrice fut price
     * @param pmchy      ytd pm change in percentile
     */
    private static void futOpenTrader(LocalDateTime nowMilli, double freshPrice, int pmchy) {
        LocalTime lt = nowMilli.toLocalTime();
        String futSymbol = ibContractToSymbol(activeFutureCt);
        FutType f = ibContractToFutType(activeFutureCt);
        double bidPrice = bidMap.get(f);
        double askPrice = askMap.get(f);
        double deltaTgt = getDeltaTarget(nowMilli, pmchy);
        double currDelta = getNetPtfDelta();

        if (lt.isBefore(ltof(8, 59)) || lt.isAfter(ltof(9, 29))) {
            checkCancelOrders(FUT_OPEN, nowMilli, 5);
            return;
        }

        if (priceMapBarDetail.get(futSymbol).size() <= 1) {
            return;
        }

        long futOpenOrdersNum = getOrderSizeForTradeType(FUT_OPEN);

        NavigableMap<LocalTime, Double> futPrice = priceMapBarDetail.get(futSymbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(8, 59, 0)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));

        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(f);
        int _2dayPerc = getPercentileForLast(fut);

        pr("fut open trader " + futPrice);
        pr(" curDelta/delta target ", currDelta, deltaTgt);
        LocalTime lastKey = futPrice.lastKey();

        double maxP = futPrice.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minP = futPrice.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        double last = futPrice.lastEntry().getValue();

        LocalDateTime lastOpenTime = getLastOrderTime(FUT_OPEN);

        if (SECONDS.between(lastOpenTime, nowMilli) >= 60) {
            if (!noMoreBuy.get() && last > maxP && _2dayPerc < 50 && (_2dayPerc < LO_PERC_WIDE || pmchy < PMCHY_LO)
                    && currDelta < deltaTgt && maxP != 0.0 && minP != 0.0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, 1, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_OPEN));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "fut open buy #:", futOpenOrdersNum,
                        globalIdOrderMap.get(id), " max min last fresh 2dp% pmchy ", r(maxP), r(minP), r(last), freshPrice
                        , _2dayPerc, pmchy, "bid ask ", bidPrice, askPrice, "currDelta/tgt:", r(currDelta), r(deltaTgt)));
            } else if (!noMoreSell.get() && last < minP && _2dayPerc > HI_PERC_WIDE && pmchy > PMCHY_HI
                    && currDelta > deltaTgt && maxP != 0.0 && minP != 0.0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, 1, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_OPEN));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "fut open sell #:", futOpenOrdersNum,
                        globalIdOrderMap.get(id), "max min last fresh 2dp% ", r(maxP), r(minP), r(last), freshPrice
                        , _2dayPerc, pmchy, "bid ask ", bidPrice, askPrice, "currDelta/tgt:", r(currDelta), r(deltaTgt)));
            }
        }
    }

    /**
     * trades based on ftse first tick
     *
     * @param nowMilli  time
     * @param indexLast fut price
     */
    static void indexFirstTickTrader(LocalDateTime nowMilli, double indexLast) {
        LocalTime lt = nowMilli.toLocalTime();
        int pmchy = getRecentPmCh(lt, INDEX_000001);
        FutType f = ibContractToFutType(activeFutureCt);
        double bidPrice = bidMap.get(f);
        double askPrice = askMap.get(f);
        double freshPrice = futPriceMap.get(f);

        if (lt.isBefore(ltof(9, 28)) || lt.isAfter(ltof(9, 35))) {
            checkCancelOrders(INDEX_FIRST_TICK, nowMilli, ORDER_WAIT_TIME);
            return;
        }

        if (priceMapBarDetail.get(FTSE_INDEX).size() <= 1) {
            return;
        }

        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(f);
        int _2dayPerc = getPercentileForLast(fut);

        if (detailedPrint.get()) {
            pr(" detailed ftse index ", priceMapBarDetail.get(FTSE_INDEX));
        }

        double open = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(ltof(9, 28)).getValue();

        double ftick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getValue)
                .orElse(open);

        LocalTime firstTickTime = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getKey)
                .orElse(LocalTime.MIN);

        LocalDateTime lastFTickTime = getLastOrderTime(INDEX_FIRST_TICK);
        int buySize = 3;
        int sellSize = 2;

        if (detailedPrint.get()) {
            pr("indexFirstTickTrader:open/last/ft/ftTime", r(open), r(indexLast), r(ftick), firstTickTime);
        }

        if (MINUTES.between(lastFTickTime, nowMilli) >= 10) {
            if (!noMoreBuy.get() && ftick > open && _2dayPerc < 50 && (_2dayPerc < LO_PERC_WIDE || pmchy < PMCHY_LO)) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, buySize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_FIRST_TICK));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "1st tick buy", globalIdOrderMap.get(id),
                        "open/FT/T", r(open), r(ftick), firstTickTime, " bid ask ", bidPrice, askPrice));
            } else if (!noMoreSell.get() && ftick < open && _2dayPerc > 50 && pmchy > PMCHY_LO
                    && (_2dayPerc > HI_PERC_WIDE || pmchy > PMCHY_HI)) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, sellSize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_FIRST_TICK));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "1st tick sell", globalIdOrderMap.get(id),
                        "open/FT/T", r(open), r(ftick), firstTickTime, " bid ask ", bidPrice, askPrice));
            }
        }
    }

    /**
     * open deviation - buy if above open and sell if below, no cares for pmchy and percentile, shud always trade
     *
     * @param nowMilli  time now
     * @param lastIndex last index price
     */
    static void indexOpenDeviationTrader(LocalDateTime nowMilli, double lastIndex) {
        LocalTime lt = nowMilli.toLocalTime();
        FutType f = ibContractToFutType(activeFutureCt);
        double freshPrice = futPriceMap.get(f);
        double atmVol = getATMVol(expiryToGet);
        OrderStatus lastStatus = getLastOrderStatusForType(INDEX_OPEN_DEVI);

        if (lt.isBefore(ltof(9, 29, 0)) || lt.isAfter(ltof(12, 0))) {
            return;
        }

        if (priceMapBarDetail.get(FTSE_INDEX).size() <= 1) {
            return;
        }

        double openIndex = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(ltof(9, 28, 0)).getValue();

        double firstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - openIndex) > 0.01).findFirst().map(Map.Entry::getValue)
                .orElse(openIndex);

        if (!manualOpenDeviationOn.get()) {
            if (lt.isBefore(ltof(9, 30, 0))) {
                manualOpenDeviationOn.set(true);
            } else {
                if (lastIndex > openIndex) {
                    openDeviationDirection = Direction.Long;
                    manualOpenDeviationOn.set(true);
                } else if (lastIndex < openIndex) {
                    openDeviationDirection = Direction.Short;
                    manualOpenDeviationOn.set(true);
                } else {
                    openDeviationDirection = Direction.Flat;
                }
            }
        }

        long numOrdersOpenDev = getOrderSizeForTradeType(INDEX_OPEN_DEVI);
        LocalDateTime lastOpenDevTradeTime = getLastOrderTime(INDEX_OPEN_DEVI);

        long milliBtwnLastTwoOrders = lastTwoOrderMilliDiff(INDEX_OPEN_DEVI);
        long tSinceLastOrder = tSincePrevOrderMilli(INDEX_OPEN_DEVI, nowMilli);

        int waitTimeInSeconds = (milliBtwnLastTwoOrders < 60000) ? 300 : 10;

        int baseSize = getWeekdayBaseSize(nowMilli.getDayOfWeek());
        int buySize = baseSize * ((numOrdersOpenDev == 0 || numOrdersOpenDev == 5) ? 1 : 1);
        int sellSize = baseSize * ((numOrdersOpenDev == 0 || numOrdersOpenDev == 5) ? 1 : 1);

        if (detailedPrint.get()) {
            if (lt.isBefore(ltof(9, 40)) || lt.getSecond() > 50) {
                pr(" open dev #:", numOrdersOpenDev, lt.truncatedTo(SECONDS),
                        "lastIndex/fut/pd", r(lastIndex), (freshPrice)
                        , Math.round(10000d * (freshPrice / lastIndex - 1)) + "bps",
                        "open:", r(openIndex), "ft", r(firstTick),
                        "IDX chg:", r10000(lastIndex / openIndex - 1) + "bps",
                        "openDevDir/vol ", openDeviationDirection, Math.round(atmVol * 10000d) / 100d + "v",
                        "IDX chg: ", r(lastIndex - openIndex), "prevT:", lastOpenDevTradeTime,
                        "wait(s):", waitTimeInSeconds, "last status ", lastStatus, "noBuy", noMoreBuy.get(),
                        "noSell", noMoreSell.get(), "manual opendev set", manualOpenDeviationOn.get());
            }
        }

//        if (numOrdersOpenDev > 0 && lastStatus != OrderStatus.Filled) {
//            if (detailedPrint.get()) {
//                pr(" open order last not filled, status ", lastStatus);
//            }
//            return;
//        }

        if (numOrdersOpenDev >= MAX_ORDER_SIZE) {
            return;
        }
        String msg = "";
        double buyPrice;
        double sellPrice;

        if (numOrdersOpenDev < 2) {
            buyPrice = freshPrice;
            sellPrice = freshPrice;
        } else {
            buyPrice = Math.min(freshPrice, roundToXUPriceAggressive(openIndex, Direction.Long));
            sellPrice = Math.max(freshPrice, roundToXUPriceAggressive(openIndex, Direction.Short));
        }

        msg = " conservative on all trades ";

        if (SECONDS.between(lastOpenDevTradeTime, nowMilli) > waitTimeInSeconds) {
            if (!noMoreBuy.get() && lastIndex > openIndex && openDeviationDirection != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(buyPrice, buySize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_OPEN_DEVI));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "index open dev BUY #:", numOrdersOpenDev
                        , globalIdOrderMap.get(id), "buy limit:", buyPrice,
                        "indexLast/fut/pd/Base Size", r(lastIndex), freshPrice, baseSize,
                        Math.round(10000d * (freshPrice / lastIndex - 1)), "bp",
                        "open/ft/last/openDevDir/vol", r(openIndex), r(firstTick), r(lastIndex),
                        openDeviationDirection, Math.round(atmVol * 10000d) / 100d + "v",
                        "IDX chg: ", r10000(lastIndex / openIndex - 1),
                        "wait/last2Diff/tSinceLast:", waitTimeInSeconds, milliBtwnLastTwoOrders, tSinceLastOrder,
                        "msg:", msg));
                openDeviationDirection = Direction.Long;
            } else if (!noMoreSell.get() && lastIndex < openIndex && openDeviationDirection != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(sellPrice, sellSize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_OPEN_DEVI));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "index open dev SELL #:", numOrdersOpenDev,
                        globalIdOrderMap.get(id), "sell limit: ", sellPrice,
                        "indexLast/fut/pd/Base Size", r(lastIndex), freshPrice, baseSize,
                        Math.round(10000d * (freshPrice / lastIndex - 1)), "bp",
                        "open/ft/last/openDevDir/vol", r(openIndex), r(firstTick), r(lastIndex),
                        openDeviationDirection, Math.round(atmVol * 10000d) / 100d + "v",
                        "IDX chg: ", r10000(lastIndex / openIndex - 1),
                        "waitT/last2Diff/tSinceLast:", waitTimeInSeconds, milliBtwnLastTwoOrders, tSinceLastOrder,
                        "msg:", msg));
                openDeviationDirection = Direction.Short;
            }
        }
    }

    private static int getWeekdayBaseSize(DayOfWeek w) {
        switch (w) {
            case MONDAY:
                return 2;
            case TUESDAY:
                return 2;
            case WEDNESDAY:
                return 1;
            case THURSDAY:
                return 1;
            case FRIDAY:
                return 1;
        }
        return 0;
    }

    /**
     * pm deviation trader
     *
     * @param nowMilli  time
     * @param indexLast index last
     */
    static void indexPmOpenDeviationTrader(LocalDateTime nowMilli, double indexLast) {
        LocalTime lt = nowMilli.toLocalTime();
        FutType f = ibContractToFutType(activeFutureCt);
        double freshPrice = futPriceMap.get(f);
        OrderStatus lastPMDevStatus = getLastOrderStatusForType(INDEX_PM_OPEN_DEVI);
        int PM_DEVI_BASE = getWeekdayBaseSize(nowMilli.getDayOfWeek());
        double bidPrice = bidMap.get(f);
        double askPrice = askMap.get(f);

        if (!checkTimeRangeBool(lt, 12, 58, 15, 0)) {
            return;
        }

        long numPMDeviOrders = getOrderSizeForTradeType(INDEX_PM_OPEN_DEVI);
        long milliBtwnLastTwo = lastTwoOrderMilliDiff(INDEX_PM_OPEN_DEVI);

        int pmDevWaitSec = (milliBtwnLastTwo < 60000) ? 300 : 10;

        if (numPMDeviOrders >= 6) {
            if (detailedPrint.get()) {
                pr(" pm dev exceed max");
            }
            return;
        }

//        if (numPMDeviOrders > 0 && lastPMDevStatus != OrderStatus.Filled) {
//            if (detailedPrint.get()) {
//                pr(" pm devi order last not filled, status: ", lastPMDevStatus);
//            }
//            return;
//        }

        double pmOpen = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(ltof(12, 58)).getValue();
        if (!manualPMDevDirection.get()) {
            if (lt.isBefore(ltof(13, 0, 0))) {
                manualPMDevDirection.set(true);
            } else {
                if (indexLast > pmOpen) {
                    indexPmDevDirection = Direction.Long;
                    manualPMDevDirection.set(true);
                } else if (indexLast < pmOpen) {
                    indexPmDevDirection = Direction.Short;
                    manualPMDevDirection.set(true);
                } else {
                    indexPmDevDirection = Direction.Flat;
                }
            }
        }

        LocalDateTime lastPMDevTradeTime = getLastOrderTime(INDEX_PM_OPEN_DEVI);

        int buyQ = PM_DEVI_BASE * ((numPMDeviOrders == 0 || numPMDeviOrders == 5) ? 1 : 1);
        int sellQ = PM_DEVI_BASE * ((numPMDeviOrders == 0 || numPMDeviOrders == 5) ? 1 : 1);

        double buyPrice;
        double sellPrice;
        if (numPMDeviOrders < 2) {
            buyPrice = freshPrice;
            sellPrice = freshPrice;
        } else {
            buyPrice = Math.min(freshPrice, roundToXUPriceAggressive(pmOpen, Direction.Long));
            sellPrice = Math.max(freshPrice, roundToXUPriceAggressive(pmOpen, Direction.Short));
        }

        if (SECONDS.between(lastPMDevTradeTime, nowMilli) > pmDevWaitSec) {
            if (!noMoreBuy.get() && indexLast > pmOpen && indexPmDevDirection != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(buyPrice, buyQ, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_PM_OPEN_DEVI));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "index pm open dev BUY #:", numPMDeviOrders
                        , globalIdOrderMap.get(id), "pm open ", r(pmOpen), "index, price, pd ",
                        r(indexLast), freshPrice, Math.round(10000d * (freshPrice / indexLast - 1)), "bp",
                        "bid ask ", bidPrice, askPrice));
                indexPmDevDirection = Direction.Long;
            } else if (!noMoreSell.get() && indexLast < pmOpen && indexPmDevDirection != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(sellPrice, sellQ, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_PM_OPEN_DEVI));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "index pm open dev SELL #:", numPMDeviOrders
                        , globalIdOrderMap.get(id), "pm open ", r(pmOpen), "index, price, pd ",
                        r(indexLast), freshPrice, Math.round(10000d * (freshPrice / indexLast - 1)), "bp",
                        "bid ask ", bidPrice, askPrice));
                indexPmDevDirection = Direction.Short;
            }
        }
    }

    /**
     * pm hilo trader
     *
     * @param nowMilli  time
     * @param indexLast last ftse index value
     */
    static void indexPmHiLoTrader(LocalDateTime nowMilli, double indexLast) {
        LocalTime lt = nowMilli.toLocalTime();
        FutType f = ibContractToFutType(activeFutureCt);
        double freshPrice = futPriceMap.get(f);
        OrderStatus lastStatus = getLastOrderStatusForType(INDEX_PM_HILO);
        int PM_HILO_BASE = getWeekdayBaseSize(nowMilli.getDayOfWeek()) + 2;
        double bidPrice = bidMap.get(f);
        double askPrice = askMap.get(f);

        if (!checkTimeRangeBool(lt, 12, 58, 14, 0)) {
            return;
        }

        long numPMHiloOrders = getOrderSizeForTradeType(INDEX_PM_HILO);

        if (numPMHiloOrders >= MAX_ORDER_SIZE) {
            if (detailedPrint.get()) {
                pr(" pm hilo exceed max ", MAX_ORDER_SIZE);
            }
            return;
        }

        if (numPMHiloOrders > 0 && lastStatus != OrderStatus.Filled) {
            pr(" pm order last not filled ");
            return;
        }

        LocalDateTime lastPMHiLoTradeTime = getLastOrderTime(INDEX_PM_HILO);
        long milliBtwnLastTwoOrders = lastTwoOrderMilliDiff(INDEX_PM_HILO);

        int pmHiloWaitTimeSeconds = (milliBtwnLastTwoOrders < 60000) ? 300 : 10;

        int buyQ = PM_HILO_BASE * ((numPMHiloOrders == 0 || numPMHiloOrders == (MAX_ORDER_SIZE - 1)) ? 1 : 2);
        int sellQ = PM_HILO_BASE * ((numPMHiloOrders == 0 || numPMHiloOrders == (MAX_ORDER_SIZE - 1)) ? 1 : 2);

        long tBtwnLast2Trades = lastTwoOrderMilliDiff(INDEX_PM_HILO);
        long tSinceLastTrade = tSincePrevOrderMilli(INDEX_PM_HILO, nowMilli);

        double pmOpen = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(ltof(12, 58)).getValue();

        double pmFirstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(12, 58, 0)))
                .filter(e -> Math.abs(e.getValue() - pmOpen) > 0.01).findFirst().map(Map.Entry::getValue)
                .orElse(pmOpen);

        LocalTime pmFirstTickTime = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(12, 58, 0)))
                .filter(e -> Math.abs(e.getValue() - pmOpen) > 0.01).findFirst().map(Map.Entry::getKey)
                .orElse(LocalTime.MIN);

        LocalTime lastKey = priceMapBarDetail.get(FTSE_INDEX).lastKey();

        double pmMaxSoFar = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(12, 58))
                        && e.getKey().isBefore(lastKey)).mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double pmMinSoFar = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(12, 58)) &&
                        e.getKey().isBefore(lastKey)).mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalTime pmMaxT = getFirstMaxTPred(priceMapBarDetail.get(FTSE_INDEX), t -> t.isAfter(ltof(12, 58)));
        LocalTime pmMinT = getFirstMinTPred(priceMapBarDetail.get(FTSE_INDEX), t -> t.isAfter(ltof(12, 58)));

        if (!manualPMHiloDirection.get()) {
            if (lt.isBefore(ltof(13, 0))) {
                manualPMHiloDirection.set(true);
            } else {
                if (pmMaxT.isAfter(pmMinT)) {
                    indexPmHiLoDirection = Direction.Long;
                    manualPMHiloDirection.set(true);
                } else if (pmMaxT.isBefore(pmMinT)) {
                    indexPmHiLoDirection = Direction.Short;
                    manualPMHiloDirection.set(true);
                } else {
                    indexPmHiLoDirection = Direction.Flat;
                }
            }
        }

        if (detailedPrint.get()) {
            pr(lt.truncatedTo(ChronoUnit.SECONDS)
                    , "index pm hilo trader: pmOpen, pmFT, T, dir: ", r(pmOpen), r(pmFirstTick), pmFirstTickTime
                    , indexPmHiLoDirection, "max min maxT minT ", r(pmMaxSoFar), r(pmMinSoFar), pmMaxT, pmMinT,
                    "milliBtwnLastTwo", milliBtwnLastTwoOrders);
        }

        double buyPrice;
        double sellPrice;

        //guanrantee fill
        if (numPMHiloOrders < 2) {
            buyPrice = freshPrice;
            sellPrice = freshPrice;
        } else {
            buyPrice = freshPrice;
            sellPrice = freshPrice;
        }

        if (SECONDS.between(lastPMHiLoTradeTime, nowMilli) >= pmHiloWaitTimeSeconds && pmMaxSoFar != 0.0 && pmMinSoFar != 0.0) {
            if (!noMoreBuy.get() && (indexLast > pmMaxSoFar || pmMaxT.isAfter(pmMinT))
                    && indexPmHiLoDirection != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(buyPrice, buyQ, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_PM_HILO));
                //apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                apcon.placeOrModifyOrder(activeFutureCt, o, new GuaranteeOrderHandler(id, apcon));

                outputOrderToAutoLog(str(o.orderId(), "index pm hilo BUY #:", numPMHiloOrders,
                        globalIdOrderMap.get(id), "buy limit: ", buyPrice, "indexLast/fut/pd: ", r(indexLast),
                        freshPrice, Math.round(10000d * (freshPrice / indexLast - 1)), "bp",
                        "pmOpen/ft/time/direction ", r(pmOpen), r(pmFirstTick), pmFirstTickTime, indexPmHiLoDirection,
                        "waitT, lastTwoTDiff, tSinceLast ", pmHiloWaitTimeSeconds, tBtwnLast2Trades, tSinceLastTrade,
                        "pm:max/min", r(pmMaxSoFar), r(pmMinSoFar), "pmMaxT,pmMinT", pmMaxT, pmMinT,
                        "bid ask", bidPrice, askPrice, Math.round(10000d * (askPrice / bidPrice - 1)), "bp"));

                indexPmHiLoDirection = Direction.Long;
            } else if (!noMoreSell.get() && (indexLast < pmMinSoFar || pmMinT.isAfter(pmMaxT))
                    && indexPmHiLoDirection != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(sellPrice, sellQ, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_PM_HILO));
                //apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                apcon.placeOrModifyOrder(activeFutureCt, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "index pm hilo SELL #:", numPMHiloOrders,
                        globalIdOrderMap.get(id), "sell limit: ", sellPrice, "indexLast/fut/pd: ", r(indexLast),
                        freshPrice, Math.round(10000d * (freshPrice / indexLast - 1)), "bp",
                        "pmOpen/ft/time/direction ", r(pmOpen), r(pmFirstTick), pmFirstTickTime, indexPmHiLoDirection,
                        "waitT, lastTwoTDiff, tSinceLast ", pmHiloWaitTimeSeconds, tBtwnLast2Trades, tSinceLastTrade,
                        "pm:max/min", r(pmMaxSoFar), r(pmMinSoFar), "pmMaxT,pmMinT", pmMaxT, pmMinT,
                        "bid ask", bidPrice, askPrice, Math.round(10000d * (askPrice / bidPrice - 1)), "bp"));
                indexPmHiLoDirection = Direction.Short;
            }
        }
    }

    private static LocalTime ltof(int h, int m) {
        return LocalTime.of(h, m);
    }

    private static LocalTime ltof(int h, int m, int s) {
        return LocalTime.of(h, m, s);
    }

    /**
     * liquidate all positions at close
     *
     * @param nowMilli   time
     * @param freshPrice price
     */
    private static void closeLiqTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime liqStartTime = ltof(14, 55);
        LocalTime liqEndTime = ltof(15, 30);

        long liqWaitSecs = 60;

        if (nowMilli.toLocalTime().isBefore(liqStartTime) || nowMilli.toLocalTime().isAfter(liqEndTime)) {
            return;
        }
        FutType activeFt = ibContractToFutType(activeFutureCt);
        LocalDateTime lastOrderTime = getLastOrderTime(CLOSE_LIQ);
        OrderStatus lastOrderStatus = getLastOrderStatusForType(CLOSE_LIQ);
        long numOrderCloseLiq = getOrderSizeForTradeType(CLOSE_LIQ);

        int pos = currentPosMap.getOrDefault(activeFt, 0);
        int absPos = Math.abs(pos);
        int size = Math.min(4, absPos <= 2 ? absPos : Math.floorDiv(absPos, 2));

        if (absPos <= 2) {
            return;
        }

        if (numOrderCloseLiq > 0.0 && lastOrderStatus != OrderStatus.Filled) {
            checkCancelOrders(CLOSE_LIQ, nowMilli, 5);
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > liqWaitSecs) {
            if (pos < 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, size, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, CLOSE_LIQ));
                apcon.placeOrModifyOrder(activeFutureCt, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), " close liq buy", "#:", numOrderCloseLiq,
                        globalIdOrderMap.get(id), "last order time", lastOrderTime, "currPos", pos, "size", size));
            } else if (pos > 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, size, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, CLOSE_LIQ));
                apcon.placeOrModifyOrder(activeFutureCt, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), " close liq sell", "#:", numOrderCloseLiq
                        , globalIdOrderMap.get(id), "last order time", lastOrderTime, "currPos", pos, "size", size));
            }
        }
    }

    /**
     * hilo trader
     *
     * @param nowMilli   now
     * @param freshPrice fut
     */
    static void futTentativeHiloTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        FutType f = ibContractToFutType(activeFutureCt);
        String futSymbol = ibContractToSymbol(activeFutureCt);
        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(f);
        NavigableMap<LocalTime, Double> futPrice = priceMapBarDetail.get(futSymbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(8, 59, 0)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));
        int perc = getPercentileForDouble(futPrice);


        double buyPrice = freshPrice;
        double sellPrice = freshPrice;
        double buyQ = 1.0;
        double sellQ = 1.0;
        long numOrders = getOrderSizeForTradeType(FUT_TENTA);

        OrderStatus lastStatus = getLastOrderStatusForType(FUT_TENTA);
        Types.Action lastAction = getLastAction(FUT_TENTA);
        double lastLimit = getLastPrice(FUT_TENTA);

        LocalDateTime lastFutTentaTime = getLastOrderTime(FUT_TENTA);
        LocalDateTime lastFutTentaCoverTime = getLastOrderTime(FUT_TENTA_COVER);

        if (lastStatus == OrderStatus.Filled && SECONDS.between(lastFutTentaTime, nowMilli) > 60) {
            if (lastAction == Types.Action.SELL && freshPrice > lastLimit) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(buyPrice, buyQ, Types.TimeInForce.DAY);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_TENTA_COVER));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), " fut tenta cover: BUY SHORT #:", numOrders));
            } else if (lastAction == Types.Action.BUY && freshPrice < lastLimit) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(buyPrice, buyQ, Types.TimeInForce.DAY);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_TENTA_COVER));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), " fut tenta cover: SELL LONG #:", numOrders));
            }
        } else {
            if (SECONDS.between(lastFutTentaTime, nowMilli) > 300) {
                if (perc < 20) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeBidLimitTIF(buyPrice, buyQ, Types.TimeInForce.DAY);
                    globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_TENTA));
                    apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                    outputOrderToAutoLog(str(o.orderId(), " fut tenta buy #:", numOrders));
                } else if (perc > 80) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeOfferLimitTIF(sellPrice, sellQ, Types.TimeInForce.DAY);
                    globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_TENTA));
                    apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                    outputOrderToAutoLog(str(o.orderId(), " fut tenta sell #:", numOrders));
                }
            }
        }
    }


    /**
     * ftse break high low trader
     *
     * @param nowMilli  time
     * @param indexLast last index
     */
    static void indexHiLoTrader(LocalDateTime nowMilli, double indexLast) {
        LocalTime lt = nowMilli.toLocalTime();
        int pmchy = getRecentPmCh(lt, INDEX_000001);
        FutType f = ibContractToFutType(activeFutureCt);
        double freshPrice = futPriceMap.get(f);
        OrderStatus lastStatus = getLastOrderStatusForType(INDEX_HILO);
        int baseSize = getWeekdayBaseSize(nowMilli.getDayOfWeek()) + 1;

        if (lt.isBefore(ltof(9, 29)) || lt.isAfter(ltof(10, 0))) {
            return;
        }

        if (priceMapBarDetail.get(FTSE_INDEX).size() <= 1) {
            return;
        }

        long numOrders = getOrderSizeForTradeType(INDEX_HILO);
        LocalDateTime lastHiLoOrderTime = getLastOrderTime(INDEX_HILO);
        long milliLastTwoOrder = lastTwoOrderMilliDiff(INDEX_HILO);
        int buyQ = baseSize * ((numOrders == 0 || numOrders == 5) ? 1 : 2);
        int sellQ = baseSize * (numOrders == 0 || numOrders == 5 ? 1 : 2);

        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(f);
        int _2dayPerc = getPercentileForLast(fut);

        int hiloWaitTimeSeconds = (milliLastTwoOrder < 60000) ? 300 : 10;

        double open = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(ltof(9, 28, 0)).getValue();
        int openPerc = getPercentileForDoubleX(priceMapBarDetail.get(FTSE_INDEX), open);

        double firstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getValue)
                .orElse(open);

        LocalTime firstTickTime = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getKey)
                .orElse(LocalTime.MIN);

        long tBtwnLast2Trades = lastTwoOrderMilliDiff(INDEX_HILO);
        long tSinceLastTrade = tSincePrevOrderMilli(INDEX_HILO, nowMilli);

        if (numOrders >= MAX_ORDER_SIZE) {
            if (detailedPrint.get()) {
                pr(" china hilo exceed max");
            }
            return;
        }

        if (numOrders > 0L && lastStatus != OrderStatus.Filled) {
            if (detailedPrint.get()) {
                pr(lt, " last hilo order not filled ");
            }
            return;
        }

        double buyPrice = Math.min(freshPrice, roundToXUPriceAggressive(indexLast, Direction.Long));
        double sellPrice = Math.max(freshPrice, roundToXUPriceAggressive(indexLast, Direction.Short));

        LocalTime lastKey = priceMapBarDetail.get(FTSE_INDEX).lastKey();

        double maxSoFar = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 28))
                        && e.getKey().isBefore(lastKey)).mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minSoFar = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 28)) &&
                        e.getKey().isBefore(lastKey)).mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalTime maxT = getFirstMaxTPred(priceMapBarDetail.get(FTSE_INDEX), e -> e.isAfter(ltof(9, 28)));
        LocalTime minT = getFirstMinTPred(priceMapBarDetail.get(FTSE_INDEX), e -> e.isAfter(ltof(9, 28)));

        if (!manualIndexHiloDirection.get()) {
            if (lt.isBefore(ltof(9, 30))) {
                manualIndexHiloDirection.set(true);
            } else {
                if (maxT.isAfter(minT)) {
                    indexHiLoDirection = Direction.Long;
                    manualIndexHiloDirection.set(true);
                } else if (minT.isAfter(maxT)) {
                    indexHiLoDirection = Direction.Short;
                    manualIndexHiloDirection.set(true);
                } else {
                    indexHiLoDirection = Direction.Flat;
                }
            }
        }

        if (SECONDS.between(lastHiLoOrderTime, nowMilli) >= hiloWaitTimeSeconds && maxSoFar != 0.0 && minSoFar != 0.0) {
            if (!noMoreBuy.get() && (indexLast > maxSoFar || maxT.isAfter(minT)) && indexHiLoDirection != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(buyPrice, buyQ);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_HILO));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "index hilo buy #:", numOrders,
                        globalIdOrderMap.get(id), "buy limit: ", buyPrice,
                        "indexLast/fut/pd/Base#:", r(indexLast), freshPrice, baseSize,
                        Math.round(10000d * (freshPrice / indexLast - 1)), " bp",
                        "open/ft/time/direction ", r(open), r(firstTick), firstTickTime, indexHiLoDirection,
                        "waitT, lastTwoTDiff, tSinceLast ", hiloWaitTimeSeconds, tBtwnLast2Trades, tSinceLastTrade,
                        "max/min/2dp%/pmchy/openp% ", r(maxSoFar), r(minSoFar), _2dayPerc, pmchy, openPerc,
                        "max min t", maxT, minT));
                indexHiLoDirection = Direction.Long;
            } else if (!noMoreSell.get() && (indexLast < minSoFar || minT.isAfter(maxT)) && indexHiLoDirection != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(sellPrice, sellQ);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_HILO));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "index hilo sell #:", numOrders,
                        globalIdOrderMap.get(id), "sell limit: ", sellPrice,
                        "indexLast/fut/pd/Base#:", r(indexLast), freshPrice, baseSize,
                        Math.round(10000d * (freshPrice / indexLast - 1)), " bp",
                        "open/ft/time/direction ", r(open), r(firstTick), firstTickTime, indexHiLoDirection,
                        "waitT, lastTwoTDiff, tSinceLast ", hiloWaitTimeSeconds, tBtwnLast2Trades, tSinceLastTrade,
                        "max/min/2dp%/pmchy/openP%", r(maxSoFar), r(minSoFar), _2dayPerc, pmchy, openPerc,
                        "max min t", maxT, minT));
                indexHiLoDirection = Direction.Short;
            }
        }
    }

    /**
     * In addition to china hilo, this trades in the same direction as hilo
     *
     * @param nowMilli   time now
     * @param indexPrice price
     */

    static void indexHiloAccumulator(LocalDateTime nowMilli, double indexPrice) {
        if (!manualAccuOn.get() || nowMilli.toLocalTime().isAfter(HILO_ACCU_DEADLINE)
                || indexHiLoDirection == Direction.Flat) {
            return;
        }
        FutType f = ibContractToFutType(activeFutureCt);
        double freshPrice = futPriceMap.get(f);
        LocalTime lt = nowMilli.toLocalTime();
        LocalDateTime lastHiLoAccuTradeTime = getLastOrderTime(INDEX_HILO_ACCU);

        long numOrders = getOrderSizeForTradeType(INDEX_HILO_ACCU);
        double hiloAccuTotalOrderQ = getOrderTotalSignedQForType(INDEX_HILO_ACCU);
        double hiloTotalOrderQ = getOrderTotalSignedQForType(INDEX_HILO);

        checkCancelOrders(INDEX_HILO_ACCU, nowMilli, ORDER_WAIT_TIME);

        int todayPerc = getPercentileForDouble(priceMapBarDetail.get(FTSE_INDEX));

        if (MINUTES.between(lastHiLoAccuTradeTime, nowMilli) >= ORDER_WAIT_TIME &&
                Math.abs(hiloAccuTotalOrderQ) <= HILO_ACCU_MAX_SIZE && Math.abs(hiloTotalOrderQ) > 0.0) {

            if (!noMoreBuy.get() && todayPerc < 1 && indexHiLoDirection == Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                int buyQ = 1;
                if (lt.isAfter(ltof(13, 0)) && lt.isBefore(ltof(15, 0))) {
                    buyQ = 2;
                }
                Order o = placeBidLimit(freshPrice, buyQ);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_HILO_ACCU));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "hilo accu buy", "#:", numOrders,
                        globalIdOrderMap.get(id), " accu#, hilo#", hiloAccuTotalOrderQ, hiloTotalOrderQ));
            } else if (!noMoreSell.get() && todayPerc > 99 && indexHiLoDirection == Direction.Short
                    && lt.isAfter(ltof(14, 50))) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INDEX_HILO_ACCU));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "hilo decu sell", "#:", numOrders,
                        globalIdOrderMap.get(id), " decu#, hilo#", hiloAccuTotalOrderQ, hiloTotalOrderQ));
            }
        }
    }


    /**
     * first tick ma profit taker
     *
     * @param nowMilli  time
     * @param indexLast price
     */
    static void firstTickMAProfitTaker(LocalDateTime nowMilli, double indexLast) {
        LocalTime lt = nowMilli.toLocalTime();
        double freshPrice = XUTrader.futPriceMap.get(ibContractToFutType(activeFutureCt));
        int pmchy = getRecentPmCh(lt, INDEX_000001);
        if (!checkTimeRangeBool(lt, 9, 29, 15, 0)) {
            return;
        }
        if (priceMapBarDetail.get(FTSE_INDEX).size() < 2) {
            return;
        }

        double firstTickTotalQ = getTotalFilledSignedQForType(INDEX_FIRST_TICK);
        double ftProfitTakeQ = getTotalFilledSignedQForType(FTICK_TAKE_PROFIT);

        double open = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(ltof(9, 29, 0)).getValue();

        double firstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01)
                .findFirst().map(Map.Entry::getValue).orElse(0.0);

        if (firstTickTotalQ == 0.0 || Math.abs(ftProfitTakeQ) >= Math.abs(firstTickTotalQ)) {
            pr("first tick Q, profitTaker Q, open, ft ",
                    firstTickTotalQ, ftProfitTakeQ, r(open), r(firstTick));
            return;
        }

        NavigableMap<LocalDateTime, SimpleBar> index = convertToLDT(priceMapBar.get(FTSE_INDEX), nowMilli.toLocalDate()
                , e -> !isStockNoonBreak(e));

        int shorterMA = 2;
        int longerMA = 5;

        checkCancelOrders(FTICK_TAKE_PROFIT, nowMilli, ORDER_WAIT_TIME * 2);
        int todayPerc = getPercentileForDouble(priceMapBarDetail.get(FTSE_INDEX));

        LocalDateTime lastProfitTakerOrder = getLastOrderTime(FTICK_TAKE_PROFIT);

        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(index, shorterMA);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(index, longerMA);

        if (smaShort.size() <= 2 || smaLong.size() <= 2) {
            //pr("1stTick profit taker:  smashort size long size not enough ");
            return;
        }

        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry((smaLong.lastKey())).getValue();

        if (MINUTES.between(lastProfitTakerOrder, nowMilli) >= ORDER_WAIT_TIME) {
            if (!noMoreBuy.get() && maShortLast > maLongLast && maShortSecLast <= maLongSecLast
                    && todayPerc < LO_PERC_WIDE && firstTick < open) {

                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FTICK_TAKE_PROFIT));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "ftick MA cover", globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), "|perc", todayPerc));

            } else if (!noMoreSell.get() && maShortLast < maLongLast && maShortSecLast >= maLongSecLast
                    && todayPerc > HI_PERC_WIDE && firstTick > open && lt.isAfter(ltof(14, 50))
                    && pmchy > PMCHY_LO) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FTICK_TAKE_PROFIT));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "ftick MA sellback", globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), "|perc", todayPerc));
            }
        }
    }

    /**
     * taking profit at close
     *
     * @param nowMilli  time now
     * @param indexLast last index price
     */
    static void closeProfitTaker(LocalDateTime nowMilli, double indexLast) {
        LocalTime lt = nowMilli.toLocalTime();
        //double freshPrice = futPriceMap.get(FutType.FrontFut);
        double freshPrice = XUTrader.futPriceMap.get(ibContractToFutType(activeFutureCt));
        double currDelta = getNetPtfDelta();
        double deltaTgt = getDeltaTarget(nowMilli, getRecentPmCh(lt, INDEX_000001));

        if (lt.isBefore(ltof(14, 50)) || lt.isAfter(ltof(15, 5))) {
            return;
        }

        if (priceMapBarDetail.get(FTSE_INDEX).size() < 2) {
            return;
        }

        int todayPerc = getPercentileForDouble(priceMapBarDetail.get(FTSE_INDEX));
        double open = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(ltof(9, 29, 0)).getValue();
        double firstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getValue).orElse(0.0);

        LocalDateTime lastCloseProfitTaker = getLastOrderTime(CLOSE_TAKE_PROFIT);

        if (MINUTES.between(lastCloseProfitTaker, nowMilli) >= ORDER_WAIT_TIME) {
            if (!noMoreBuy.get() && todayPerc < 5 && firstTick < open && currDelta < deltaTgt) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, CONSERVATIVE_SIZE);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, CLOSE_TAKE_PROFIT));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "close profit taking COVER", globalIdOrderMap.get(id)
                        , "curDel, deltaTarget", currDelta, deltaTgt));
            } else if (!noMoreSell.get() && todayPerc > 99 && firstTick > open && currDelta > deltaTgt
                    && lt.isAfter(ltof(14, 50))) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, CONSERVATIVE_SIZE);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, CLOSE_TAKE_PROFIT));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "close profit taking SELL", globalIdOrderMap.get(id)
                        , "curDel, deltaTarget", currDelta, deltaTgt));
            }
        }
    }

    /**
     * accumulate based on intraday
     *
     * @param nowMilli  time
     * @param indexLast last index
     */
    static void intraday1stTickAccumulator(LocalDateTime nowMilli, double indexLast) {
        LocalTime lt = nowMilli.toLocalTime();
        int pmchy = getRecentPmCh(lt, INDEX_000001);
        if (lt.isBefore(ltof(9, 40)) || lt.isAfter(ltof(15, 0))) {
            return;
        }

        FutType f = ibContractToFutType(activeFutureCt);

        if (priceMapBarDetail.get(FTSE_INDEX).size() <= 1) {
            return;
        }

        double freshPrice = futPriceMap.get(f);
        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(f);
        int _2dayFutPerc = getPercentileForLast(fut);

        double open = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(ltof(9, 29, 0)).getValue();

        double firstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(ltof(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getValue).orElse(0.0);

        LocalDateTime lastOpenTime = getLastOrderTime(INTRADAY_FIRSTTICK_ACCU);
        double firstTickSignedQuant = getOrderTotalSignedQForType(INDEX_FIRST_TICK);
        double ftAccuSignedQuant = getOrderTotalSignedQForType(INTRADAY_FIRSTTICK_ACCU);
        //pr(" intraday first tick accu: open, firstTick, futP% ", r(open), r(firstTick), _2dayFutPerc);

        if (firstTickSignedQuant == 0.0 || Math.abs(ftAccuSignedQuant) >= FT_ACCU_MAX_SIZE) {
            return;
        }

        if (MINUTES.between(lastOpenTime, nowMilli) >= ORDER_WAIT_TIME) {
            if (!noMoreBuy.get() && firstTick > open && _2dayFutPerc < 20 && (_2dayFutPerc < 1 || pmchy < PMCHY_LO)
                    && indexLast < open && lt.isBefore(ltof(14, 0))) {
                int buyQ = 1;
                if (lt.isAfter(ltof(13, 0))) {
                    buyQ = 2;
                }
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, buyQ);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INTRADAY_FIRSTTICK_ACCU));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "intraday ft accu",
                        globalIdOrderMap.get(id), "open first futP%", open, firstTick, _2dayFutPerc,
                        "ft size ", firstTickSignedQuant));
            } else if (!noMoreSell.get() && firstTick < open && indexLast > open &&
                    _2dayFutPerc > 99 && pmchy > PMCHY_HI && lt.isAfter(ltof(14, 50))) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INTRADAY_FIRSTTICK_ACCU));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "intraday ft decu",
                        globalIdOrderMap.get(id), "open first futP% ", open, firstTick, _2dayFutPerc,
                        "ft size ", firstTickSignedQuant));
            }
        }
    }

    /**
     * intraday ma
     *
     * @param nowMilli  time
     * @param indexLast index last price
     */
    static synchronized void intradayMAProfitTaker(LocalDateTime nowMilli, double indexLast) {
        LocalTime t = nowMilli.toLocalTime();
        FutType f = ibContractToFutType(activeFutureCt);
        double freshPrice = futPriceMap.get(f);
        int pmChgY = getRecentPmCh(t, INDEX_000001);

        if (!checkTimeRangeBool(t, 9, 29, 15, 0)) {
            return;
        }

        int shorterMA = 5;
        int longerMA = 10;

        int buySize;
        int sellSize;

        //checkCancelOrders(INTRADAY_MA, nowMilli, 30);
        LocalDate tTrade = getTradeDate(nowMilli);

        double totalFilledNonMAOrderSize = getTotalFilledOrderSignedQPred(isNotMA());
        double totalMASignedQ = getOrderTotalSignedQForTypeFilled(INTRADAY_MA);
        long numOrders = getOrderSizeForTradeType(INTRADAY_MA);

        NavigableMap<LocalDateTime, SimpleBar> index = convertToLDT(priceMapBar.get(FTSE_INDEX), nowMilli.toLocalDate()
                , e -> !isStockNoonBreak(e));

        int todayPerc = getPercentileForDouble(priceMapBarDetail.get(FTSE_INDEX));
        LocalDateTime lastIndexMAOrder = getLastOrderTime(INTRADAY_MA);

        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(index, shorterMA);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(index, longerMA);

        if (smaShort.size() <= 2 || smaLong.size() <= 2) {
            //pr(" smashort size long size not enough ");
            return;
        }

        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry((smaLong.lastKey())).getValue();

        long maWaitTime = 15;
        if (MINUTES.between(lastIndexMAOrder, nowMilli) >= maWaitTime) {
            if (!noMoreBuy.get() && maShortLast > maLongLast && maShortSecLast <= maLongSecLast
                    && todayPerc < LO_PERC_WIDE && ((totalFilledNonMAOrderSize < 0
                    && totalMASignedQ + totalFilledNonMAOrderSize < 0) || totalMASignedQ < 0)) {
                int id = autoTradeID.incrementAndGet();
                buySize = (int) Math.min(Math.round((Math.abs(totalMASignedQ + totalFilledNonMAOrderSize) / 2)), 3);
                Order o = placeBidLimitTIF(freshPrice, buySize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INTRADAY_MA));
                apcon.placeOrModifyOrder(activeFutureCt, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "intraday MA BUY#:", numOrders, globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), "|perc", todayPerc, "pmchg ", pmChgY
                        , " others total:", totalFilledNonMAOrderSize, "MA total:", totalMASignedQ));
            } else if (!noMoreSell.get() && maShortLast < maLongLast && maShortSecLast >= maLongSecLast &&
                    todayPerc > HI_PERC_WIDE
                    && ((totalFilledNonMAOrderSize > 0 && totalMASignedQ + totalFilledNonMAOrderSize > 0) ||
                    totalMASignedQ > 0)) {
                sellSize = (int) Math.min(Math.round((totalMASignedQ + totalFilledNonMAOrderSize) / 2), 3);
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, sellSize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INTRADAY_MA));
                apcon.placeOrModifyOrder(activeFutureCt, o, new GuaranteeOrderHandler(id, apcon));
                outputOrderToAutoLog(str(o.orderId(), "intraday MA SELL#:", numOrders, globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), "|perc", todayPerc, "pmchg ", pmChgY
                        , " others total:", totalFilledNonMAOrderSize, "MA total:", totalMASignedQ));
            }
        }
    }

    private static double getDeltaTarget(LocalDateTime nowMilli, int pmchy) {
        double baseDelta = _20DayMA == MASentiment.Bearish ? BEAR_BASE_DELTA : BULL_BASE_DELTA;
        double pmchgDelta = (pmchy < -20 ? 1 : (pmchy > 20 ? -0.5 : 0)) * PMCHY_DELTA * Math.abs(pmchy) / 100.0;
        double weekdayDelta = getWeekdayDeltaAdjustmentLdt(nowMilli);
        return baseDelta + pmchgDelta + weekdayDelta;
    }


    /**
     * trading based on index MA
     *
     * @param nowMilli   time in milliseconds
     * @param freshPrice last price
     */
    private static synchronized void percentileMATrader(LocalDateTime nowMilli, double freshPrice, int pmchy) {
        LocalTime lt = nowMilli.toLocalTime();
        String anchorIndex = FTSE_INDEX;
        double currDelta = getNetPtfDelta();
        double totalSizeTradedOtherOrders = getTotalFilledOrderSignedQPred(isNotMA());
        double totalMASignedQ = getOrderTotalSignedQForType(PERC_MA);
        long numOrders = getOrderSizeForTradeType(PERC_MA);
        FutType ft = ibContractToFutType(activeFutureCt);

        NavigableMap<LocalDateTime, SimpleBar> index = convertToLDT(priceMapBar.get(anchorIndex), nowMilli.toLocalDate()
                , e -> !isStockNoonBreak(e));

        NavigableMap<LocalDateTime, SimpleBar> fut = trimDataFromYtd(futData.get(ft));
        int _2dayPerc = getPercentileForLast(fut);

        int shorterMA = 5;
        int longerMA = 10;
        int buySize;

        double baseDelta = _20DayMA == MASentiment.Bearish ? BEAR_BASE_DELTA : BULL_BASE_DELTA;
        double pmchgDelta = (pmchy < -20 ? 1 : (pmchy > 20 ? -0.5 : 0)) * PMCHY_DELTA * Math.abs(pmchy) / 100.0;
        double weekdayDelta = getWeekdayDeltaAdjustmentLdt(nowMilli);
        double deltaTarget = baseDelta + pmchgDelta + weekdayDelta;

        if (isStockNoonBreak(lt) || isOvernight(lt)) {
            return;
        }

        checkCancelOrders(PERC_MA, nowMilli, 30);

        int todayPerc = getPercentileForLastPred(fut,
                e -> e.getKey().isAfter(LocalDateTime.of(getTradeDate(nowMilli), ltof(8, 59))));

        LocalDateTime lastIndexMAOrder = getLastOrderTime(PERC_MA);

        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(index, shorterMA);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(index, longerMA);

        if (smaShort.size() <= 2 || smaLong.size() <= 2) {
            //pr(" smashort size long size not enough ");
            return;
        }

        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry((smaLong.lastKey())).getValue();
        double avgBuy = getAvgFilledBuyPriceForOrderType(PERC_MA);
        double avgSell = getAvgFilledSellPriceForOrderType(PERC_MA);


        if (detailedPrint.get() && lt.getSecond() < 10) {
            pr("*perc MA Time: ", nowMilli.toLocalTime().truncatedTo(ChronoUnit.SECONDS), "next T:",
                    lastIndexMAOrder.plusMinutes(ORDER_WAIT_TIME),
                    "||1D p%: ", todayPerc, "||2D p%", _2dayPerc, "pmchY: ", pmchy);
            //pr("Anchor / short long MA: ", anchorIndex, shorterMA, longerMA);
            //pr(" ma cross last : ", r(maShortLast), r(maLongLast), r(maShortLast - maLongLast));
            //pr(" ma cross 2nd last : ", r(maShortSecLast), r(maLongSecLast), r(maShortSecLast - maLongSecLast));
            boolean bull = maShortLast > maLongLast && maShortSecLast <= maLongSecLast;
            boolean bear = maShortLast < maLongLast && maShortSecLast >= maLongSecLast;
            pr(" bull/bear cross ", bull, bear, " current PD ", Math.round(10000d * getPD(freshPrice)));
            pr("delta base,pm,weekday,target:", baseDelta, pmchgDelta, weekdayDelta, deltaTarget);
        }

        if (MINUTES.between(lastIndexMAOrder, nowMilli) >= ORDER_WAIT_TIME) {
            if (!noMoreBuy.get() && maShortLast > maLongLast && maShortSecLast <= maLongSecLast
                    && _2dayPerc < LO_PERC_WIDE && currDelta < deltaTarget && (freshPrice < avgBuy || avgBuy == 0.0)
                    && (totalSizeTradedOtherOrders < 0
                    && totalMASignedQ + totalSizeTradedOtherOrders < 0)) {
                int id = autoTradeID.incrementAndGet();
                buySize = 1;
                Order o = placeBidLimit(freshPrice, buySize);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, PERC_MA));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "perc MA buy", "#:", numOrders, globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast shortlong",
                        r(maShortSecLast), r(maLongSecLast), "|anchor ", anchorIndex, "|perc", todayPerc, "|2d Perc ",
                        _2dayPerc, "pmChg", pmchy, "|delta Base pmchg weekday target ",
                        baseDelta, pmchgDelta, weekdayDelta, deltaTarget, "avg buy sell ", avgBuy, avgSell));
            } else if (!noMoreSell.get() && maShortLast < maLongLast && maShortSecLast >= maLongSecLast
                    && _2dayPerc > HI_PERC_WIDE && currDelta > deltaTarget && (freshPrice > avgSell || avgSell == 0.0)
                    && (totalSizeTradedOtherOrders > 0
                    && totalMASignedQ + totalSizeTradedOtherOrders > 0) && lt.isAfter(ltof(14, 50))) {

                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, CONSERVATIVE_SIZE);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, PERC_MA));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "perc MA sell", "#:", numOrders, globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), " anchor ", anchorIndex, "perc", todayPerc, "2d Perc ",
                        _2dayPerc, "pmChg", pmchy, "|delta Base pmchg weekday target ",
                        baseDelta, pmchgDelta, weekdayDelta, deltaTarget, "avg buy sell ", avgBuy, avgSell));
            }
        }
    }

    private static void testTrader(LocalDateTime nowMilli, double freshPrice) {
        long numTestOrders = getOrderSizeForTradeType(TEST);
        pr("num test orders ", numTestOrders);
        FutType f = ibContractToFutType(activeFutureCt);
        double bid = bidMap.get(f);
        double ask = askMap.get(f);

        if (numTestOrders < 1) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice + 10.0, 1, Types.TimeInForce.IOC);
            globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Test ", TEST));
            apcon.placeOrModifyOrder(activeFutureCt, o, new GuaranteeOrderHandler(id, apcon));
            outputOrderToAutoLog(str(o.orderId(), "Test trade ", freshPrice, "bid ask", bid, ask));
        }
    }

    /**
     * overnight close trading
     */
    private static void overnightTrader(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        if (!isOvernight(nowMilli.toLocalTime())) {
            return;
        }
        double currDelta = getNetPtfDelta();
        LocalDate TDate = getTradeDate(nowMilli);
        double indexPrice = (priceMapBar.containsKey(FTSE_INDEX) &&
                priceMapBar.get(FTSE_INDEX).size() > 0) ?
                priceMapBar.get(FTSE_INDEX).lastEntry().getValue().getClose() : SinaStock.FTSE_OPEN;

        NavigableMap<LocalDateTime, SimpleBar> futPriceMap = futData.get(ibContractToFutType(activeFutureCt));

        int pmPercChg = getPMPercChg(futPriceMap, TDate);
        int currPerc = getPercentileForLast(futPriceMap);

        LocalDateTime lastOrderTime = getLastOrderTime(OVERNIGHT);

        if (checkTimeRangeBool(lt, 3, 0, 5, 0)
                && MINUTES.between(lastOrderTime, nowMilli) > ORDER_WAIT_TIME) {
            if (currDelta > getBearishTarget() && currPerc > HI_PERC && pmPercChg > PMCHY_HI) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, CONSERVATIVE_SIZE);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Overnight Short", OVERNIGHT));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "O/N sell @ ", freshPrice, "curr p%", currPerc,
                        "pmPercChg ", pmPercChg));
            } else if (currDelta < getBullishTarget() && currPerc < LO_PERC && pmPercChg < PMCHY_LO) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, CONSERVATIVE_SIZE);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Overnight Long", OVERNIGHT));
                apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "O/N buy @ ", freshPrice, "curr p%", currPerc,
                        "pmPercChg", pmPercChg));
            }
        }

        String outputString = str("||O/N||", nowMilli.format(DateTimeFormatter.ofPattern("M-d H:mm")),
                "||curr P%: ", currPerc,
                "||curr P: ", futPriceMap.lastEntry().getValue().getClose(),
                "||index: ", r(indexPrice),
                "||BID ASK ", bidMap.getOrDefault(ibContractToFutType(activeFutureCt), 0.0),
                askMap.getOrDefault(ibContractToFutType(activeFutureCt), 0.0),
                "pmPercChg", pmPercChg);

        outputToAutoLog(outputString);
        requestOvernightExecHistory();
    }

    private static void cancelOrdersByType(AutoOrderType type) {
        globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getAugmentedOrderStatus() != OrderStatus.Filled)
                .forEach(e -> {
                    apcon.cancelOrder(e.getValue().getOrder().orderId());
                    e.getValue().setAugmentedOrderStatus(OrderStatus.Cancelled);
                });
    }

    private static void getMoreAggressiveFill(AutoOrderType type, double bid, double ask) {
        globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getAugmentedOrderStatus() != OrderStatus.Filled)
                .forEach(e -> {
                    Order o = e.getValue().getOrder();
                    if (o.action() == Types.Action.BUY) {
                        o.lmtPrice(ask);
                    } else if (o.action() == Types.Action.SELL) {
                        o.lmtPrice(bid);
                    }
                    e.getValue().setMsg(str(" more aggressive price:", o.action(),
                            o.action() == Types.Action.BUY ? ask : bid));
                    apcon.placeOrModifyOrder(activeFutureCt, o, new DefaultOrderHandler());
                    e.getValue().setAugmentedOrderStatus(OrderStatus.Cancelled);
                });
    }

    /**
     * cancel order of type
     *
     * @param type             type of trade to cancel
     * @param nowMilli         time now
     * @param timeLimitMinutes how long to wait
     */
    private static void checkCancelOrders(AutoOrderType type, LocalDateTime nowMilli, int timeLimitMinutes) {
        long ordersNum = globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type).count();

        if (ordersNum != 0) {
            OrderStatus lastOrdStatus = globalIdOrderMap.entrySet().stream()
                    .filter(e -> e.getValue().getOrderType() == type)
                    .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                    .map(e -> e.getValue().getAugmentedOrderStatus()).orElse(OrderStatus.Unknown);

            LocalDateTime lastOTime = getLastOrderTime(type);

            if (lastOrdStatus != OrderStatus.Filled && lastOrdStatus != OrderStatus.Cancelled
                    && lastOrdStatus != OrderStatus.ApiCancelled && lastOrdStatus != OrderStatus.PendingCancel) {

                if (MINUTES.between(lastOTime, nowMilli) > timeLimitMinutes) {
                    globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type)
                            .forEach(e -> {
                                if (e.getValue().getAugmentedOrderStatus() == OrderStatus.Submitted ||
                                        e.getValue().getAugmentedOrderStatus() == OrderStatus.Created) {
                                    apcon.cancelOrder(e.getValue().getOrder().orderId());
                                    e.getValue().setFinalActionTime(LocalDateTime.now());
                                    e.getValue().setAugmentedOrderStatus(OrderStatus.Cancelled);
                                }
                            });

                    String orderList = globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type)
                            .map(e -> str(e.getKey(), e.getValue())).collect(Collectors.joining(","));

                    outputOrderToAutoLog(str(nowMilli + " cancelling orders trader for type " + type,
                            "printing all orders ", orderList));
                }
            }
        }
    }

    private static double getWeekdayDeltaAdjustmentLdt(LocalDateTime ldt) {
        LocalTime lt = ldt.toLocalTime();
        switch (ldt.getDayOfWeek()) {
            case MONDAY:
                if (lt.isAfter(ltof(15, 0))) {
                    return 1000000;
                } else {
                    return 100000;
                }
            case TUESDAY:
                if (lt.isBefore(ltof(15, 0))) {
                    return 1000000;
                }
            case WEDNESDAY:
                if (lt.isAfter(ltof(15, 0))) {
                    return -100000;
                }
            case THURSDAY:
                if (lt.isBefore(ltof(15, 0))) {
                    return -100000;
                }
        }
        return 0.0;
    }

    private static double getCurrentMA() {
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFutureCt)));
        if (price5.size() <= 2) return 0.0;

        NavigableMap<LocalDateTime, Double> sma = getMAGen(price5, shortMAPeriod);
        return sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;
    }

    private static LocalDateTime getLastOrderTime(AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .map(e -> e.getValue().getOrderTime())
                .orElse(sessionOpenT());
    }

    private static long lastTwoOrderMilliDiff(AutoOrderType type) {
        long numOrders = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type).count();
        if (numOrders < 2) {
            return Long.MAX_VALUE;
        } else {
            LocalDateTime last = globalIdOrderMap.entrySet().stream()
                    .filter(e -> e.getValue().getOrderType() == type)
                    .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                    .map(e -> e.getValue().getOrderTime()).orElseThrow(() -> new IllegalArgumentException("no"));
            LocalDateTime secLast = globalIdOrderMap.entrySet().stream()
                    .filter(e -> e.getValue().getOrderType() == type)
                    .map(e -> e.getValue().getOrderTime())
                    .filter(e -> e.isBefore(last))
                    .max(Comparator.comparing(Function.identity())).orElseThrow(() -> new IllegalArgumentException("no"));
            return ChronoUnit.MILLIS.between(secLast, last);
        }
    }

    private static long tSincePrevOrderMilli(AutoOrderType type, LocalDateTime nowMilli) {
        long numOrders = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type).count();
        if (numOrders == 0) {
            return Long.MAX_VALUE;
        } else {
            LocalDateTime last = globalIdOrderMap.entrySet().stream()
                    .filter(e -> e.getValue().getOrderType() == type)
                    .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                    .map(e -> e.getValue().getOrderTime()).orElseThrow(() -> new IllegalArgumentException("no"));
            return ChronoUnit.MILLIS.between(last, nowMilli);
        }
    }


    private static double getAvgFilledBuyPriceForOrderType(AutoOrderType type) {
        double botUnits = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.BUY)
                .filter(e -> e.getValue().getAugmentedOrderStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity()).sum();

        if (botUnits == 0.0) {
            return 0.0;
        }

        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.BUY)
                .filter(e -> e.getValue().getAugmentedOrderStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity() * e.getValue().getOrder().lmtPrice())
                .sum() / botUnits;
    }

    private static double getAvgFilledSellPriceForOrderType(AutoOrderType type) {
        double soldUnits = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.SELL)
                .filter(e -> e.getValue().getAugmentedOrderStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity()).sum();

        if (soldUnits == 0.0) {
            return 0.0;
        }
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.SELL)
                .filter(e -> e.getValue().getAugmentedOrderStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity() * e.getValue().getOrder().lmtPrice())
                .sum() / soldUnits;
    }

    private static long getOrderSizeForTradeType(AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .count();
    }

    static Types.Action getLastAction(AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .map(e -> e.getValue().getOrder().action())
                .orElse(Types.Action.NOACTION);
    }

    static double getLastPrice(AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .map(e -> e.getValue().getOrder().lmtPrice())
                .orElse(0.0);
    }

    private static double getOrderTotalSignedQForType(AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .mapToDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())
                .sum();
    }

    //filled order type
    private static double getOrderTotalSignedQForTypeFilled(AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> (e.getValue().getAugmentedOrderStatus() == OrderStatus.Filled))
                .mapToDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())
                .sum();
    }

    //filled + pred
    private static double getTotalFilledOrderSignedQPred(Predicate<AutoOrderType> p) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> (e.getValue().getAugmentedOrderStatus() == OrderStatus.Filled))
                .filter(e -> p.test(e.getValue().getOrderType()))
                .mapToDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())
                .sum();
    }

    private static Predicate<AutoOrderType> isNotMA() {
        return type -> type != INTRADAY_MA && type != PERC_MA && type != FUT_DAY_MA
                && type != FUT_FAST_MA;
    }
    //**********************************************Trade types **********************************************

    private void loadXU() {
        pr("in loadXU");
        ChinaMain.GLOBAL_REQ_ID.addAndGet(5);
        apcon.getSGXA50Historical2(ChinaMain.GLOBAL_REQ_ID.get(), this);
    }

    private static boolean checkIfOrderPriceMakeSense(double p) {
        FutType f = ibContractToFutType(activeFutureCt);
        pr(str("CHECKING PRICE || bid ask price ",
                bidMap.get(f), askMap.get(f), futPriceMap.get(f)));
        return (p != 0.0)
                && (bidMap.getOrDefault(f, 0.0) != 0.0)
                && (askMap.getOrDefault(f, 0.0) != 0.0)
                && (futPriceMap.getOrDefault(f, 0.0) != 0.0)
                && Math.abs(askMap.get(f) - bidMap.get(f)) < 10;
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
            LocalTime lt = ltof(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            LocalDateTime ldt = LocalDateTime.of(ld, lt);
            if (!ld.equals(currDate) && lt.equals(ltof(14, 59))) {
                futPrevClose3pmMap.put(FutType.get(name), close);
            }
//            if (name.equals("SGXA50") && ltof.isAfter(ltof(3, 0)) && ltof.isBefore(ltof(5, 0))) {
//                pr(" handle hist ", name, ldt, open, high, low, close);
//            }

            if (lt.equals(ltof(4, 44))) {
                pr(" filling fut am close ", name, ldt, close);
                XUTrader.fut5amClose.put(FutType.get(name), close);
            }


            int daysToGoBack = currDate.getDayOfWeek().equals(DayOfWeek.MONDAY) ? 4 : 2;
            if (ldt.toLocalDate().isAfter(currDate.minusDays(daysToGoBack)) && FUT_COLLECTION_TIME.test(ldt)) {

                if (lt.equals(ltof(9, 0))) {
                    futOpenMap.put(FutType.get(name), open);
                    pr(ld, " :open is for " + name + " " + open);
                }

                futData.get(FutType.get(name)).put(ldt, new SimpleBar(open, high, low, close));

                //if (priceMapBarDetail.containsKey(name) && ldt.toLocalDate().equals(LocalDate.now())
                //&& ldt.toLocalTime().isAfter(ltof(8, 59))) {
                //priceMapBarDetail.get(name).put(ldt.toLocalTime(), close);
                //}
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
        if (tradesMap.get(ibContractToFutType(activeFutureCt)).size() > 0) {
//            currentDirection = tradesMap.get(ibContractToFutType(activeFutureCt)).lastEntry().getValue().getSizeAll() > 0 ?
//                    Direction.Long : Direction.Short;
        }
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
    }

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
        activeFutLiveIDOrderMap.put(order.orderId(), order);
        if (ibContractToSymbol(contract).equals(ibContractToSymbol(activeFutureCt))) {
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

        if (ticker.startsWith("SGXA50")) {
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

        apcon.reqDeepMktData(activeFutureCt, 10, this);

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
        //pr(" compute trade map active ", ibContractToFutType(activeFutureCt));
        FutType f = ibContractToFutType(activeFutureCt);
        LocalTime lt = LocalTime.now();

        Predicate<LocalDateTime> dateP = t -> t.isAfter(LocalDateTime.of(getTradeDate(LocalDateTime.now()),
                ltof(8, 59)));
        int unitsBought = tradesMap.get(f).entrySet().stream().filter(e -> dateP.test(e.getKey()))
                .mapToInt(e -> e.getValue().getSizeBot()).sum();
        int unitsSold = tradesMap.get(f).entrySet().stream().filter(e -> dateP.test(e.getKey()))
                .mapToInt(e -> e.getValue().getSizeSold()).sum();

        botMap.put(f, unitsBought);
        soldMap.put(f, unitsSold);

        double avgBuy = Math.abs(Math.round(100d * (tradesMap.get(f).entrySet().stream().filter(e -> dateP.test(e.getKey()))
                .mapToDouble(e -> e.getValue().getCostBasisAllPositive("")).sum() / unitsBought)) / 100d);
        double avgSell = Math.abs(Math.round(100d * (tradesMap.get(f).entrySet().stream().filter(e -> dateP.test(e.getKey()))
                .mapToDouble(e -> e.getValue().getCostBasisAllNegative("")).sum() / unitsSold)) / 100d);
        double buyTradePnl = Math.round(100d * (futPriceMap.get(f) - avgBuy) * unitsBought) / 100d;
        double sellTradePnl = Math.round(100d * (futPriceMap.get(f) - avgSell) * unitsSold) / 100d;
        double netTradePnl = buyTradePnl + sellTradePnl;
        double netTotalCommissions = Math.round(100d * ((unitsBought - unitsSold) * 1.505d)) / 100d;
        double mtmPnl = (currentPosMap.getOrDefault(f, 0) - unitsBought - unitsSold) *
                (futPriceMap.getOrDefault(f, 0.0) - futPrevClose3pmMap.getOrDefault(f, 0.0));

        NavigableMap<LocalDateTime, SimpleBar> futdata = trimDataFromYtd(futData.get(f));

        //int pmChgY = getPercentileChgFut(futdata, futdata.firstKey().toLocalDate());
        int pmChgY = getRecentPmCh(LocalTime.now(), INDEX_000001);
        //int closePercY = getClosingPercentile(futdata, futdata.firstKey().toLocalDate());
        int closePercY = getRecentClosePerc(LocalTime.now(), INDEX_000001);
        int openPercY = getOpenPercentile(futdata, futdata.firstKey().toLocalDate());
        //int pmChg = getPercentileChgFut(futdata, getTradeDate(futdata.lastKey()));
        int pmChg = getPmchToday(lt, INDEX_000001);
        int indexPercLast = getPreCloseLastPercToday(lt, INDEX_000001);

        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(f);
        int _2dayFutPerc = getPercentileForLast(fut);
        int _1dayFutPerc = getPercentileForLastPred(futdata, e -> e.getKey().isAfter(
                LocalDateTime.of(getTradeDate(LocalDateTime.now()), ltof(8, 59))));

        Map<AutoOrderType, Double> quantitySumByOrder = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getAugmentedOrderStatus() == OrderStatus.Filled)
                .collect(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType(),
                        Collectors.summingDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())));

        Map<AutoOrderType, Long> numTradesByOrder = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getAugmentedOrderStatus() == OrderStatus.Filled)
                .collect(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType(),
                        Collectors.counting()));

        String pnlString = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getAugmentedOrderStatus() == OrderStatus.Filled)
                .collect(Collectors.collectingAndThen(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType()
                        , Collectors.summingDouble(e -> e.getValue().getPnl(futPriceMap.get(f)))),
                        e -> e.entrySet().stream().map(e1 -> str("|||", e1.getKey(),
                                "#:", numTradesByOrder.getOrDefault(e1.getKey(), 0L),
                                "Tot Q: ", quantitySumByOrder.getOrDefault(e1.getKey(), 0d), r(e1.getValue())))
                                .collect(Collectors.joining(","))));

        SwingUtilities.invokeLater(() -> {
            updateLog(" Expiry " + activeFutureCt.lastTradeDateOrContractMonth());
            updateLog(" NAV: " + currentIBNAV);
            updateLog(" P " + futPriceMap.getOrDefault(f, 0.0));
            updateLog(str(" Close3pm ", futPrevClose3pmMap.getOrDefault(f, 0.0),
                    " Close4am ", fut5amClose.getOrDefault(f, 0.0)));
            updateLog(" Open " + futOpenMap.getOrDefault(f, 0.0));
            updateLog(" Chg " + (Math.round(10000d * (futPriceMap.getOrDefault(f, 0.0) /
                    futPrevClose3pmMap.getOrDefault(f, Double.MAX_VALUE) - 1)) / 100d) + " %");
            updateLog(" Open Pos " + (currentPosMap.getOrDefault(f, 0) - unitsBought - unitsSold));
            updateLog(" MTM " + mtmPnl);
            updateLog(" units bot " + unitsBought);
            updateLog(" avg buy " + avgBuy);
            updateLog(" units sold " + unitsSold);
            updateLog(" avg sell " + avgSell);
            updateLog(" buy pnl " + buyTradePnl);
            updateLog(" sell pnl " + sellTradePnl);
            //breakdown of pnl
            updateLog(" net pnl " + r(netTradePnl) + " breakdown: " + pnlString);
            updateLog(" net commission " + netTotalCommissions);
            updateLog(" MTM + Trade " + r(netTradePnl + mtmPnl));
            updateLog("pos:" + currentPosMap.getOrDefault(f, 0) + " Delta " + r(getNetPtfDelta()) +
                    " Stock Delta " + r(ChinaPosition.getStockPtfDelta()) + " Fut Delta " + r(getFutDelta())
                    + "HK Delta " + r(ChinaPosition.getStockPtfDeltaCustom(e -> isHKStock(e.getKey())))
                    + " China Delta " + r(ChinaPosition.getStockPtfDeltaCustom(e -> isChinaStock(e.getKey()))));
            updateLog(str("2D fut p%:", _2dayFutPerc, "1D fut p%", _1dayFutPerc, "1D idx p%", indexPercLast,
                    "pmChgY:", pmChgY, "openY:", openPercY, "closeY:", closePercY, "pmChg", pmChg,
                    "||Index pmchy", getRecentPmCh(LocalTime.now(), INDEX_000001)));
            updateLog(" expiring delta " + getExpiringDelta());
        });
    }
}

