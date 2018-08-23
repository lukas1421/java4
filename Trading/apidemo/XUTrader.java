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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static apidemo.ChinaData.priceMapBar;
import static apidemo.ChinaData.priceMapBarDetail;
import static apidemo.ChinaDataYesterday.ma20Map;
import static apidemo.ChinaPosition.*;
import static apidemo.ChinaStock.*;
import static apidemo.TradingConstants.*;
import static apidemo.XuTraderHelper.*;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static util.AutoOrderType.*;
import static utility.Utility.*;

public final class XUTrader extends JPanel implements HistoricalHandler, ApiController.IDeepMktDataHandler,
        ApiController.ITradeReportHandler, ApiController.IOrderHandler, ApiController.ILiveOrderHandler
        , ApiController.IPositionHandler, ApiController.IConnectionHandler {

    private static MASentiment _20DayMA = MASentiment.Directionless;
    public static volatile double currentIBNAV = 0.0;

    private static volatile Set<String> uniqueTradeKeySet = new HashSet<>();
    static ApiController apcon;
    private static XUTraderRoll traderRoll;

    //global
    private static AtomicBoolean globalTradingOn = new AtomicBoolean(false);
    private static AtomicBoolean musicOn = new AtomicBoolean(false);
    private static volatile MASentiment sentiment = MASentiment.Directionless;
    //static final int MAX_FUT_LIMIT = 20;
    //static volatile AtomicBoolean canLongGlobal = new AtomicBoolean(true);
    //static volatile AtomicBoolean canShortGlobal = new AtomicBoolean(true);
    static volatile AtomicInteger autoTradeID = new AtomicInteger(100);
    public static volatile NavigableMap<Integer, OrderAugmented> globalIdOrderMap = new ConcurrentSkipListMap<>();
    private static final int UP_PERC = 95;
    private static final int DOWN_PERC = 5;
    private static final int UP_PERC_WIDE = 80;
    private static final int DOWN_PERC_WIDE = 20;

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


    //china open/firsttick
    private static volatile Direction a50HiLoDirection = Direction.Flat;
    private static volatile AtomicBoolean manualSetDirection = new AtomicBoolean(false);
    private static volatile AtomicBoolean manualAccuOn = new AtomicBoolean(false);
    private static final long HILO_ACCU_MAX_SIZE = 2;
    private static final LocalTime HILO_ACCU_DEADLINE = LocalTime.of(9, 40);
    private static final long FIRSTTICK_ACCU_MAX_SIZE = 2;

    //open deviation
    private static volatile Direction openDeviationDirection = Direction.Flat;
    private static volatile AtomicBoolean manualOpenDeviationOn = new AtomicBoolean(false);
    private static final long PREFERRED_OPEN_DEV_SIZE = 5;
    private static final long MAX_OPEN_DEV_SIZE = 6;


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

    private static Direction currentDirection = Direction.Flat;
    //private static final double PD_UP_THRESH = 0.003;
    //private static final double PD_DOWN_THRESH = -0.003;

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

                apcon.reqLiveOrders(getThis());

                SwingUtilities.invokeLater(() -> {
                    currTimeLabel.setText(time);
                    xuGraph.fillInGraph(trimDataFromYtd(futData.get(ibContractToFutType(activeFuture))));
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
                requestExecHistory();
            }, 0, 1, TimeUnit.MINUTES);

            ses.scheduleAtFixedRate(() -> {
                globalIdOrderMap.entrySet().stream().filter(e -> isInventoryTrade().test(e.getValue().getOrderType()))
                        .forEach(e -> pr(str("real order ID", e.getValue().getOrder().orderId(), e.getValue())));

                long invOrderCount = globalIdOrderMap.entrySet().stream()
                        .filter(e -> isInventoryTrade().test(e.getValue().getOrderType())).count();
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
    }

    private static String get20DayBullBear() {
        String ticker = "sh000001";
        return str(_20DayMA, "Price: ", priceMap.getOrDefault(ticker, 0.0), "MA20: ",
                ma20Map.getOrDefault(ticker, 0.0));
    }

    public static int getPmchy() {
        NavigableMap<LocalDateTime, SimpleBar> futdata = trimDataFromYtd(futData.get(ibContractToFutType(activeFuture)));
        return getPercentileChgFut(futdata, getPrevTradingDate(futdata));

    }

    public static void processMain(LocalDateTime ldt, double price) {

        double currDelta = getNetPtfDelta();
        boolean maxAfterMin = checkf10maxAftermint(INDEX_000016);
        boolean maxAbovePrev = checkf10MaxAbovePrev(INDEX_000016);

        NavigableMap<LocalDateTime, SimpleBar> futdata = trimDataFromYtd(futData.get(ibContractToFutType(activeFuture)));
        int pmChgY = getPercentileChgFut(futdata, getPrevTradingDate(futdata));

        int closePercY = getClosingPercentile(futdata, getPrevTradingDate(futdata));
        int openPercY = getOpenPercentile(futdata, getPrevTradingDate(futdata));
        int pmChg = getPercentileChgFut(futdata, getTradeDate(futdata.lastKey()));
        int lastPerc = getClosingPercentile(futdata, getTradeDate(futdata.lastKey()));

        if (detailedPrint.get()) {
            pr("||20DayMA ", _20DayMA, "||maxT>MinT: ", maxAfterMin, "||max>PrevC", maxAbovePrev,
                    "closeY", closePercY, "openPercY", openPercY, "pmchgy", pmChgY,
                    "pmch ", pmChg, "lastP", lastPerc, "delta range ", getBearishTarget(), getBullishTarget()
                    , "currDelta ", Math.round(currDelta), " hilo direction: ", a50HiLoDirection);
        }

        if (Math.abs(currDelta) > 2000000d) {
            if (detailedPrint.get()) {
                pr("delta too big, exceeding 2mm ");
            }
            if (currDelta > 2000000d) {
                noMoreBuy.set(true);
            } else if (currDelta < -2000000d) {
                noMoreSell.set(true);
            }
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

        futOpenTrader(ldt, price, pmChgY);
        //firstTickTrader(ldt, price);
        //openDeviationTrader(ldt, price, pmChgY);
        //chinaHiLoTrader(ldt, price, pmChgY);
        chinaHiloAccumulator(ldt, price);
        intraday1stTickAccumulator(ldt, price, pmChgY);

        firstTickMAProfitTaker(ldt, price);
        closeProfitTaker(ldt, price);

        intradayMATrader(ldt, price, pmChgY);
        percentileMATrader(ldt, price, pmChgY);

        if (!(currDelta > DELTA_HARD_LO_LIMIT && currDelta < DELTA_HARD_HI_LIMIT)) {
            pr(" curr delta is outside range ");
            return;
        }

        overnightTrader(ldt, price);
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
        if (futdata.size() <= 2) {
            return 0;
        } else if (futdata.lastKey().isAfter(LocalDateTime.of(dt, LocalTime.of(13, 0)))) {
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

//                pr("getPercChgFut " +
//                        "localdate , max, min, pmO, prevC ", dt, prevMax, prevMin, pmOpen, prevClose);

                return (int) Math.round(100d * (prevClose - pmOpen) / (prevMax - prevMin));
            }
        }
        return 0;
    }

    private static int getClosingPercentile(NavigableMap<LocalDateTime, SimpleBar> futdata, LocalDate dt) {
        if (futdata.size() <= 2) {
            return 0;
        } else if (futdata.lastKey().isAfter(LocalDateTime.of(dt, LocalTime.of(13, 0)))) {

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
            if (prevMax == 0.0 || prevMin == 0.0 || prevClose == 0.0) {
                return 0;
            } else {
                return (int) Math.round(100d * (prevClose - prevMin) / (prevMax - prevMin));
            }
        }
        return 0;
    }

    private static int getOpenPercentile(NavigableMap<LocalDateTime, SimpleBar> futdata, LocalDate dt) {
        if (futdata.size() <= 2) {
            return 0;
        } else if (futdata.firstKey().isBefore(LocalDateTime.of(dt, LocalTime.of(9, 31)))) {
            double prevOpen = futdata.ceilingEntry(LocalDateTime.of(dt, LocalTime.of(9, 30)))
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
                                    .equals(LocalDate.now()) && LocalTime.now().isAfter(LocalTime.of(15, 0)))
                            || (e.getKey() == FutType.FrontFut &&
                            LocalDate.parse(TradingConstants.A50_FRONT_EXPIRY, DateTimeFormatter.ofPattern("yyyyMMdd"))
                                    .equals(LocalDate.now()) && LocalTime.now().isBefore(LocalTime.of(15, 0)))) {
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
        return currentPosMap.entrySet().stream()
                .mapToDouble(e -> {
                    if (e.getKey() == FutType.PreviousFut) {
                        return 0.0;
                    } else if (e.getKey() == FutType.FrontFut &&
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
     * only output upon a cross
     */
    private void observeMATouch() {
        NavigableMap<LocalDateTime, SimpleBar> futprice1m = (futData.get(ibContractToFutType(activeFuture)));

        if (futprice1m.size() < 2) return;

        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(futprice1m, 10);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(futprice1m, 20);

        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry((smaLong.lastKey())).getValue();

        double pd = getPD(futprice1m.lastEntry().getValue().getClose());
        int percentile = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));
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
     * fut trades at fut open at 9am
     *
     * @param nowMilli   time now
     * @param freshPrice fut price
     * @param pmchy      ytd pm change in percentile
     */
    private static void futOpenTrader(LocalDateTime nowMilli, double freshPrice, int pmchy) {
        LocalTime lt = nowMilli.toLocalTime();
        double currentBid = bidMap.get(FutType.FrontFut);
        double currentAsk = askMap.get(FutType.FrontFut);

        if (lt.isBefore(LocalTime.of(8, 59)) || lt.isAfter(LocalTime.of(9, 29))) {
            checkCancelOrders(FUT_OPEN, nowMilli, ORDER_WAIT_TIME * 2);
            return;
        }

        if (priceMapBarDetail.get("SGXA50").size() <= 1) {
            return;
        }

        long futOpenOrdersNum = getOrderSizeForTradeType(FUT_OPEN);

        NavigableMap<LocalTime, Double> futPrice =
                priceMapBarDetail.get("SGXA50").entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LocalTime.of(8, 59)))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (a, b) -> a, ConcurrentSkipListMap::new));

        NavigableMap<LocalDateTime, SimpleBar> fut =
                futData.get(ibContractToFutType(activeFuture));
        int _2dayPerc = getPercentileForLast(fut);

        pr("fut open trader " + futPrice);

        LocalTime lastKey = futPrice.lastKey();

        double maxP = futPrice.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minP = futPrice.entrySet().stream().filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        double last = futPrice.lastEntry().getValue();

        LocalDateTime lastOpenTime = getLastOrderTime(FUT_OPEN);

        if (SECONDS.between(lastOpenTime, nowMilli) >= 45) {
            if (!noMoreBuy.get() && last > maxP && _2dayPerc < 50 && (_2dayPerc < DOWN_PERC_WIDE || pmchy < PMCHY_LO)) {
                int id = autoTradeID.incrementAndGet();
                Order o;
                if (futOpenOrdersNum == 0L && lt.isBefore(LocalTime.of(9, 0, 10))) {
                    o = placeBidLimitTIF(freshPrice + 2.5, 1, Types.TimeInForce.IOC);
                } else {
                    o = placeBidLimit(freshPrice, 1);
                }
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_OPEN));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "fut open buy",
                        globalIdOrderMap.get(id), " max min last 2dp% pmchy ", r(maxP), r(minP), r(last), _2dayPerc,
                        pmchy, "bid ask ", currentBid, currentAsk));
            } else if (!noMoreSell.get() && last < minP && _2dayPerc > 50 && (_2dayPerc > UP_PERC_WIDE || pmchy > PMCHY_HI)) {
                int id = autoTradeID.incrementAndGet();
                Order o;
                if (futOpenOrdersNum == 0L && lt.isBefore(LocalTime.of(9, 0, 10))) {
                    o = placeOfferLimitTIF(freshPrice - 2.5, 1, Types.TimeInForce.IOC);
                } else {
                    o = placeOfferLimit(freshPrice, 1);
                }
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FUT_OPEN));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "fut open sell",
                        globalIdOrderMap.get(id), "max min last 2dp% pmchy ", r(maxP), r(minP), r(last)
                        , _2dayPerc, pmchy, "bid ask ", currentBid, currentAsk));
            }
        }
    }

    /**
     * trades based on ftse first tick
     *
     * @param nowMilli  time
     * @param indexLast fut price
     */
    static void firstTickTrader(LocalDateTime nowMilli, double indexLast) {
        int pmchy = getPmchy();
        LocalTime lt = nowMilli.toLocalTime();
        double bidNow = bidMap.getOrDefault(FutType.FrontFut, 0.0);
        double askNow = askMap.getOrDefault(FutType.FrontFut, 0.0);

        double freshPrice = XUTrader.futPriceMap.get(FutType.FrontFut);

        if (lt.isBefore(LocalTime.of(9, 28)) || lt.isAfter(LocalTime.of(9, 35))) {
            checkCancelOrders(FIRST_TICK, nowMilli, ORDER_WAIT_TIME);
            return;
        }

        if (priceMapBarDetail.get(FTSE_INDEX).size() <= 1) {
            return;
        }

        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(ibContractToFutType(activeFuture));

        int _2dayPerc = getPercentileForLast(fut);

        pr(" detailed ftse index ", priceMapBarDetail.get(FTSE_INDEX));

        double open = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(LocalTime.of(9, 28)).getValue();

        //double ftick1 = priceMapBarDetail.get(FTSE_INDEX).lastEntry().getValue();

        double ftick2 = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getValue)
                .orElse(open);

        LocalTime firstTickTime = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getKey)
                .orElse(LocalTime.MIN);

        LocalDateTime lastFTickTime = getLastOrderTime(FIRST_TICK);

        int buySize = 3;
        int sellSize = 1;

        pr("firstTickTrader:: open / last / ft / ftTime", r(open), r(indexLast), r(ftick2), firstTickTime);

        if (MINUTES.between(lastFTickTime, nowMilli) >= 10) {
            if (!noMoreBuy.get() && ftick2 > open && _2dayPerc < 50 &&
                    (_2dayPerc < DOWN_PERC_WIDE || pmchy < PMCHY_LO)) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice + 2.5, buySize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FIRST_TICK));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "1st tick buy", globalIdOrderMap.get(id),
                        "open ftick 1ttime", r(open), r(ftick2), firstTickTime, " bid ask ", bidNow, askNow));
            } else if (!noMoreSell.get() && ftick2 < open && _2dayPerc > 50 &&
                    (_2dayPerc > UP_PERC_WIDE || pmchy > PMCHY_HI)) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice - 2.5, sellSize, Types.TimeInForce.IOC);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FIRST_TICK));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "1st tick sell", globalIdOrderMap.get(id),
                        "open ftick 1ttime", open, ftick2, firstTickTime, " bid ask ", bidNow, askNow));
            }
        }
    }

    /**
     * open deviation - buy if above open and sell if below, no cares for pmchy and percentile, shud always trade
     *
     * @param nowMilli  time now
     * @param lastIndex last index price
     */
    static void openDeviationTrader(LocalDateTime nowMilli, double lastIndex) {
        LocalTime lt = nowMilli.toLocalTime();

        double freshPrice = XUTrader.futPriceMap.get(FutType.FrontFut);

        double atmVol = ChinaOption.getATMVol(ChinaOption.backExpiry);

        if (lt.isBefore(LocalTime.of(9, 29, 0)) || lt.isAfter(LocalTime.of(15, 0))) {
            return;
        }

        if (priceMapBarDetail.get(FTSE_INDEX).size() <= 1) {
            return;
        }

        double openIndex = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(LocalTime.of(9, 28, 0)).getValue();
        //double lastIndex = priceMapBarDetail.get(FTSE_INDEX).lastEntry().getValue();

        double firstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - openIndex) > 0.01).findFirst().map(Map.Entry::getValue)
                .orElse(openIndex);

        if (!manualOpenDeviationOn.get()) {
            if (lt.isBefore(LocalTime.of(9, 30, 0))) {
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

        long numOrdersOpenDev = getOrderSizeForTradeType(OPEN_DEVIATION);
        LocalDateTime lastOpenDevTradeTime = getLastOrderTime(OPEN_DEVIATION);
        int buySize = 1;
        int sellSize = 1;

        pr(" open dev: numOrder ", lt.truncatedTo(ChronoUnit.SECONDS), numOrdersOpenDev,
                "open:", r(openIndex), "ft", r(firstTick), "lastIndex", r(lastIndex), "fut", freshPrice,
                "chg:", r10000(lastIndex / openIndex - 1),
                "fut/pd", r(freshPrice), r10000(freshPrice / lastIndex - 1),
                "openDevDir/vol ", openDeviationDirection, Math.round(atmVol * 10000d) / 100d + "v",
                " IDX chg: ", r(lastIndex - openIndex));

        if (numOrdersOpenDev > MAX_OPEN_DEV_SIZE) {
            return;
        }

        if (SECONDS.between(lastOpenDevTradeTime, nowMilli) >= 60) {
            if (!noMoreBuy.get() && lastIndex > openIndex && openDeviationDirection != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, buySize);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, OPEN_DEVIATION));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "open deviation buy", globalIdOrderMap.get(id),
                        "open/ft/last/openDevDir/vol", r(openIndex), r(firstTick), r(lastIndex),
                        "IDX chg: ", r10000(lastIndex / openIndex - 1), "fut pd", freshPrice,
                        r10000(freshPrice / lastIndex - 1), openDeviationDirection, atmVol));
                openDeviationDirection = Direction.Long;
            } else if (!noMoreSell.get() && lastIndex < openIndex && openDeviationDirection != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, sellSize);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, OPEN_DEVIATION));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "open deviation sell", globalIdOrderMap.get(id),
                        "open/ft/last/openDevDir/vol", r(openIndex), r(firstTick), r(lastIndex),
                        "IDX chg: ", r10000(lastIndex / openIndex - 1), "fut pd", freshPrice,
                        r10000(freshPrice / lastIndex - 1), openDeviationDirection, atmVol));
                openDeviationDirection = Direction.Short;
            }
        }
    }

    /**
     * ftse break high low trader
     *
     * @param nowMilli  time
     * @param indexLast last index
     */
    static void chinaHiLoTrader(LocalDateTime nowMilli, double indexLast) {
        LocalTime lt = nowMilli.toLocalTime();
        int pmchy = getPmchy();
        double freshPrice = futPriceMap.get(FutType.FrontFut);

        if (lt.isBefore(LocalTime.of(9, 29)) || lt.isAfter(LocalTime.of(15, 0))) {
            return;
        }

        if (priceMapBarDetail.get(FTSE_INDEX).size() <= 1) {
            return;
        }

        long numOrders = getOrderSizeForTradeType(CHINA_HILO);

        LocalDateTime lastHiLoTradeTime = getLastOrderTime(CHINA_HILO);

        int buySize = 1;
        int sellSize = 1;

        if (numOrders > 5) {
            if (detailedPrint.get()) {
                pr(" china open trades exceed max ");
            }
            return;
        }

        if (numOrders > 2 || MINUTES.between(lastHiLoTradeTime, nowMilli) <= 60) {
            buySize = 1;
            sellSize = 1;
        }

        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(ibContractToFutType(activeFuture));

        int _2dayPerc = getPercentileForLast(fut);

        if (_2dayPerc > UP_PERC_WIDE || pmchy > 0) {
            sellSize = 3;
        } else if (_2dayPerc < DOWN_PERC_WIDE || pmchy < 0) {
            buySize = 3;
        }

        double open = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(LocalTime.of(9, 28)).getValue();

        double firstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getValue)
                .orElse(open);

        LocalTime firstTickTime = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getKey)
                .orElse(LocalTime.MIN);

        if (!manualSetDirection.get()) {
            //double last = priceMapBarDetail.get(FTSE_INDEX).lastEntry().getValue();
            if (indexLast > open) {
                a50HiLoDirection = Direction.Long;
                manualSetDirection.set(true);
            } else if (indexLast < open) {
                a50HiLoDirection = Direction.Short;
                manualSetDirection.set(true);
            } else {
                a50HiLoDirection = Direction.Flat;
            }
        }

        //double indexLast = priceMapBarDetail.get(FTSE_INDEX).lastEntry().getValue();
        LocalTime lastKey = priceMapBarDetail.get(FTSE_INDEX).lastKey();

        double maxSoFar = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 28))
                        && e.getKey().isBefore(lastKey)).mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minSoFar = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 28)) &&
                        e.getKey().isBefore(lastKey)).mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        if (SECONDS.between(lastHiLoTradeTime, nowMilli) >= 60) {
            if (!noMoreBuy.get() && indexLast > maxSoFar && a50HiLoDirection == Direction.Short) {
                if (lt.isAfter(LocalTime.of(9, 40)) && _2dayPerc > DOWN_PERC_WIDE && pmchy > PMCHY_LO) {
                    return;
                }
                String msg = "";
                if (lt.isBefore(LocalTime.of(9, 40)) && _2dayPerc > DOWN_PERC_WIDE && pmchy > PMCHY_LO) {
                    msg = "cover short";
                }

                buySize = (_2dayPerc < DOWN_PERC_WIDE || pmchy < PMCHY_LO) ? 2 : 1;

                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, buySize);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, CHINA_HILO));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "china hilo buy", globalIdOrderMap.get(id),
                        "open/1tk/time/direction ", r(open), r(firstTick), firstTickTime, a50HiLoDirection,
                        "indexLast, max, min, 2dp pmchy msg ", r(indexLast), r(maxSoFar), r(minSoFar), _2dayPerc, pmchy, msg));
                a50HiLoDirection = Direction.Long;
            } else if (!noMoreSell.get() && indexLast < minSoFar && a50HiLoDirection == Direction.Long &&
                    (_2dayPerc > UP_PERC_WIDE || pmchy > PMCHY_HI)) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, sellSize);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, CHINA_HILO));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "china hilo sell", globalIdOrderMap.get(id),
                        "open/1tk/time/direction ", r(open), r(firstTick), firstTickTime, a50HiLoDirection,
                        " lastV, max, min 2dp pmchy ", r(indexLast), r(maxSoFar), r(minSoFar), _2dayPerc, pmchy));
                a50HiLoDirection = Direction.Short;
            }
        }

    }

    /**
     * In addition to china hilo, this trades in the same direction as hilo
     *
     * @param nowMilli   time now
     * @param freshPrice price
     */

    private static void chinaHiloAccumulator(LocalDateTime nowMilli, double freshPrice) {

        if (!manualAccuOn.get() || nowMilli.toLocalTime().isAfter(HILO_ACCU_DEADLINE)
                || a50HiLoDirection == Direction.Flat) {
            return;
        }

        LocalDateTime lastHiLoAccuTradeTime = getLastOrderTime(CHINA_HILO_ACCU);

        double hiloAccuTotalOrderQ = getOrderTotalSignedQForType(CHINA_HILO_ACCU);
        double hiloTotalOrderQ = getOrderTotalSignedQForType(CHINA_HILO);

        checkCancelOrders(CHINA_HILO_ACCU, nowMilli, ORDER_WAIT_TIME);

        int todayPerc = getPercentileForLast(priceMapBar.get(FTSE_INDEX));

        if (SECONDS.between(lastHiLoAccuTradeTime, nowMilli) >= 60 &&
                Math.abs(hiloAccuTotalOrderQ) <= HILO_ACCU_MAX_SIZE && Math.abs(hiloTotalOrderQ) > 0.0) {

            if (!noMoreBuy.get() && todayPerc < 1 && a50HiLoDirection == Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, CHINA_HILO_ACCU));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "hilo accu buy", globalIdOrderMap.get(id),
                        " accu #, hilo #", hiloAccuTotalOrderQ, hiloTotalOrderQ));
            } else if (!noMoreSell.get() && todayPerc > 99 && a50HiLoDirection == Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, CHINA_HILO_ACCU));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "hilo decu sell", globalIdOrderMap.get(id),
                        " decu #, hilo #", hiloAccuTotalOrderQ, hiloTotalOrderQ));
            }
        }
    }


    private static void firstTickMAProfitTaker(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        if (!checkTimeRangeBool(lt, 9, 29, 15, 0)) {
            return;
        }
        if (priceMapBarDetail.get(FTSE_INDEX).size() < 2) {
            return;
        }

        double firstTickTotalQ = getTotalFilledSignedQForType(FIRST_TICK);

        double ftProfitTakeQ = getTotalFilledSignedQForType(FTICK_TAKE_PROFIT);

        double open = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(LocalTime.of(9, 29, 0)).getValue();

        double firstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getValue).orElse(0.0);

        if (firstTickTotalQ == 0.0 || Math.abs(ftProfitTakeQ) >= Math.abs(firstTickTotalQ)) {
            pr("first tick Q, profitTaker Q, open, firsttick ",
                    firstTickTotalQ, ftProfitTakeQ, r(open), r(firstTick));
            return;
        }

        NavigableMap<LocalDateTime, SimpleBar> index = convertToLDT(priceMapBar.get(FTSE_INDEX), nowMilli.toLocalDate()
                , e -> !isStockNoonBreak(e));

        int shorterMA = 2;
        int longerMA = 5;

        checkCancelOrders(FTICK_TAKE_PROFIT, nowMilli, ORDER_WAIT_TIME * 2);
        int todayPerc = getPercentileForLast(priceMapBar.get(FTSE_INDEX));

        LocalDateTime lastProfitTakerOrder = getLastOrderTime(FTICK_TAKE_PROFIT);

        NavigableMap<LocalDateTime, Double> smaShort = getMAGen(index, shorterMA);
        NavigableMap<LocalDateTime, Double> smaLong = getMAGen(index, longerMA);

        if (smaShort.size() <= 2 || smaLong.size() <= 2) {
            pr("1stTick profit taker:  smashort size long size not enough ");
            return;
        }

        double maShortLast = smaShort.lastEntry().getValue();
        double maShortSecLast = smaShort.lowerEntry(smaShort.lastKey()).getValue();
        double maLongLast = smaLong.lastEntry().getValue();
        double maLongSecLast = smaLong.lowerEntry((smaLong.lastKey())).getValue();

        if (MINUTES.between(lastProfitTakerOrder, nowMilli) >= ORDER_WAIT_TIME * 2) {
            if (!noMoreBuy.get() && maShortLast > maLongLast && maShortSecLast <= maLongSecLast
                    && todayPerc < DOWN_PERC_WIDE && firstTick < open) {

                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FTICK_TAKE_PROFIT));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "ftick MA cover", globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), "|perc", todayPerc));

            } else if (!noMoreSell.get() && maShortLast < maLongLast && maShortSecLast >= maLongSecLast
                    && todayPerc > UP_PERC_WIDE && firstTick > open && lt.isBefore(LocalTime.of(11, 30))) {

                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, FTICK_TAKE_PROFIT));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "ftick MA sellback", globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), "|perc", todayPerc));
            }
        }
    }

    private static void closeProfitTaker(LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();

        if (lt.isBefore(LocalTime.of(14, 55)) || lt.isAfter(LocalTime.of(15, 5))) {
            return;
        }

        if (priceMapBarDetail.get(FTSE_INDEX).size() < 2) {
            return;
        }

        int todayPerc = getPercentileForLast(priceMapBar.get(FTSE_INDEX));
        double open = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(LocalTime.of(9, 29, 0)).getValue();
        double firstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getValue).orElse(0.0);

        LocalDateTime lastCloseProfitTaker = getLastOrderTime(CLOSE_TAKE_PROFIT);

        if (MINUTES.between(lastCloseProfitTaker, nowMilli) >= ORDER_WAIT_TIME) {
            if (!noMoreBuy.get() && todayPerc < 10 && firstTick < open) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, CONSERVATIVE_SIZE);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, CLOSE_TAKE_PROFIT));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "close profit taking COVER", globalIdOrderMap.get(id)));
            } else if (!noMoreSell.get() && todayPerc > 90 && firstTick > open) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, CONSERVATIVE_SIZE);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, CLOSE_TAKE_PROFIT));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "close profit taking SELL", globalIdOrderMap.get(id)));
            }
        }
    }

    /**
     * accumulate based on intraday
     *
     * @param nowMilli   time
     * @param freshPrice fut price
     * @param pmchy      ytd pm change in perc
     */
    private static void intraday1stTickAccumulator(LocalDateTime nowMilli, double freshPrice, int pmchy) {
        LocalTime lt = nowMilli.toLocalTime();
        if (lt.isBefore(LocalTime.of(9, 40)) || lt.isAfter(LocalTime.of(15, 0))) {
            return;
        }

        if (priceMapBarDetail.get(FTSE_INDEX).size() <= 1) {
            return;
        }

        NavigableMap<LocalDateTime, SimpleBar> fut = futData.get(ibContractToFutType(activeFuture));
        int _2dayFutPerc = getPercentileForLast(fut);

        double open = priceMapBarDetail.get(FTSE_INDEX).ceilingEntry(LocalTime.of(9, 29, 0)).getValue();

        double firstTick = priceMapBarDetail.get(FTSE_INDEX).entrySet().stream()
                .filter(e -> e.getKey().isAfter(LocalTime.of(9, 29, 0)))
                .filter(e -> Math.abs(e.getValue() - open) > 0.01).findFirst().map(Map.Entry::getValue).orElse(0.0);

        LocalDateTime lastOpenTime = getLastOrderTime(INTRADAY_FIRSTTICK_ACCU);

        double firstTickSignedQuant = getOrderTotalSignedQForType(FIRST_TICK);

        double ftAccuSignedQuant = getOrderTotalSignedQForType(INTRADAY_FIRSTTICK_ACCU);

        pr(" intraday first tick accu: open, firstTick, futP% ", r(open), r(firstTick), _2dayFutPerc);

        if (MINUTES.between(lastOpenTime, nowMilli) >= ORDER_WAIT_TIME * 2 && Math.abs(firstTickSignedQuant) > 0.0
                && Math.abs(ftAccuSignedQuant) <= FIRSTTICK_ACCU_MAX_SIZE) {
            if (!noMoreBuy.get() && firstTick > open && _2dayFutPerc < 20 && (_2dayFutPerc < 5 || pmchy < PMCHY_LO)
                    && lt.isBefore(LocalTime.of(13, 30))) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INTRADAY_FIRSTTICK_ACCU));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "intraday first tick buy",
                        globalIdOrderMap.get(id), "open first futP%", open, firstTick, _2dayFutPerc,
                        "first tick size ", firstTickSignedQuant));
            } else if (!noMoreSell.get() && firstTick < open && _2dayFutPerc > 80 &&
                    (_2dayFutPerc > 95 || pmchy > PMCHY_HI) && lt.isBefore(LocalTime.of(11, 30))) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, 1);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INTRADAY_FIRSTTICK_ACCU));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "intraday first tick sell",
                        globalIdOrderMap.get(id), "open first futP% ", open, firstTick, _2dayFutPerc,
                        "first tick size ", firstTickSignedQuant));
            }
        }
    }

    /**
     * intraday ma -
     *
     * @param nowMilli   time
     * @param freshPrice fut price
     * @param pmChgY     last pm chg in perc
     */
    private static synchronized void intradayMATrader(LocalDateTime nowMilli, double freshPrice, int pmChgY) {

        LocalTime lt = nowMilli.toLocalTime();

        if (!checkTimeRangeBool(lt, 9, 29, 15, 0)) {
            return;
        }

        NavigableMap<LocalDateTime, SimpleBar> index = convertToLDT(priceMapBar.get(FTSE_INDEX), nowMilli.toLocalDate()
                , e -> !isStockNoonBreak(e));

        int shorterMA = 5;
        int longerMA = 10;

        int buySize = 1;
        int sellSize = 1;

        checkCancelOrders(INTRADAY_MA, nowMilli, ORDER_WAIT_TIME * 2);
        LocalDate tTrade = getTradeDate(nowMilli);

        int todayPerc = getPercentileForLastPred(index, e -> e.getKey().toLocalDate().equals(tTrade));
        LocalDateTime lastIndexMAOrder = getLastOrderTime(INTRADAY_MA);

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

        if (MINUTES.between(lastIndexMAOrder, nowMilli) >= ORDER_WAIT_TIME * 1.5) {
            if (!noMoreBuy.get() && maShortLast > maLongLast && maShortSecLast <= maLongSecLast
                    && todayPerc < DOWN_PERC_WIDE && pmChgY < 0 && (todayPerc < 5 || pmChgY < PMCHY_LO)) {

                if (lt.isAfter(LocalTime.of(13, 0))) {
                    buySize = 2;
                }

                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, buySize);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INTRADAY_MA));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "intraday MA buy", globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), "|perc", todayPerc));

            } else if (!noMoreSell.get() &&
                    maShortLast < maLongLast && maShortSecLast >= maLongSecLast && todayPerc > UP_PERC_WIDE
                    && pmChgY > 0 && (todayPerc > 95 || pmChgY > PMCHY_HI) && lt.isBefore(LocalTime.of(11, 30))) {

                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, sellSize);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, INTRADAY_MA));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "intraday MA sell", globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), "|perc", todayPerc));
            }
        }
    }

    private static LocalDateTime getLastOrderTime(AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                .map(e -> e.getValue().getOrderTime())
                .orElse(sessionOpenT());
    }

    private static double getAvgFilledBuyPriceForOrderType(AutoOrderType type) {
        double botUnits = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.BUY)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity()).sum();

        if (botUnits == 0.0) {
            return 0.0;
        }

        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.BUY)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity() * e.getValue().getOrder().lmtPrice())
                .sum() / botUnits;
    }

    private static double getAvgFilledSellPriceForOrderType(AutoOrderType type) {
        double soldUnits = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.SELL)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity()).sum();

        if (soldUnits == 0.0) {
            return 0.0;
        }
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .filter(e -> e.getValue().getOrder().action() == Types.Action.SELL)
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .mapToDouble(e -> e.getValue().getOrder().totalQuantity() * e.getValue().getOrder().lmtPrice())
                .sum() / soldUnits;
    }

    private static long getOrderSizeForTradeType(AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .count();
    }

    private static double getOrderTotalSignedQForType(AutoOrderType type) {
        return globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getOrderType() == type)
                .mapToDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())
                .sum();
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

        NavigableMap<LocalDateTime, SimpleBar> index = convertToLDT(priceMapBar.get(anchorIndex), nowMilli.toLocalDate()
                , e -> !isStockNoonBreak(e));

        NavigableMap<LocalDateTime, SimpleBar> fut = trimDataFromYtd(futData.get(ibContractToFutType(activeFuture)));
        int _2dayPerc = getPercentileForLast(fut);

        int shorterMA = 5;
        int longerMA = 10;
        int buySize;
        int sellSize;

        double baseDelta = _20DayMA == MASentiment.Bearish ? BEAR_BASE_DELTA : BULL_BASE_DELTA;
        double pmchgDelta = (pmchy < -20 ? 1 : (pmchy > 20 ? -0.5 : 0)) * PMCHY_DELTA * Math.abs(pmchy) / 100.0;
        double weekdayDelta = getWeekdayDeltaAdjustmentLdt(nowMilli);
        double deltaTarget = baseDelta + pmchgDelta + weekdayDelta;

        if (isStockNoonBreak(lt)) {
            return;
        } else if (isOvernight(lt)) {
            anchorIndex = "Future";
            index = futData.get(ibContractToFutType(activeFuture));
        } else if (checkTimeRangeBool(lt, 9, 30, 10, 0)) {
            shorterMA = 1;
            longerMA = 5;
        }

        checkCancelOrders(PERC_MA, nowMilli, ORDER_WAIT_TIME);

        int todayPerc = getPercentileForLastPred(fut,
                e -> e.getKey().isAfter(LocalDateTime.of(getTradeDate(nowMilli), LocalTime.of(8, 59))));

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

        double avgBuy = getAvgFilledBuyPriceForOrderType(PERC_MA);
        double avgSell = getAvgFilledSellPriceForOrderType(PERC_MA);

        if (detailedPrint.get()) {
            pr("*perc MA Time: ", nowMilli.toLocalTime().truncatedTo(ChronoUnit.SECONDS), "next T:",
                    lastIndexMAOrder.plusMinutes(ORDER_WAIT_TIME),
                    "||1D p%: ", todayPerc, "||2D p%", _2dayPerc, "pmchY: ", pmchy);
            //pr("Anchor / short long MA: ", anchorIndex, shorterMA, longerMA);
            //pr(" ma cross last : ", r(maShortLast), r(maLongLast), r(maShortLast - maLongLast));
            //pr(" ma cross 2nd last : ", r(maShortSecLast), r(maLongSecLast), r(maShortSecLast - maLongSecLast));
            boolean bull = maShortLast > maLongLast && maShortSecLast <= maLongSecLast;
            boolean bear = maShortLast < maLongLast && maShortSecLast >= maLongSecLast;
            pr(" bull/bear cross ", bull, bear);
            pr(" current PD ", r10000(getPD(freshPrice)));
            pr("delta base,pm,weekday,target:", baseDelta, pmchgDelta, weekdayDelta, deltaTarget);
            pr("perc avg buy sell ", avgBuy, avgSell);
        }

        if (MINUTES.between(lastIndexMAOrder, nowMilli) >= ORDER_WAIT_TIME) {
            if (!noMoreBuy.get() && maShortLast > maLongLast && maShortSecLast <= maLongSecLast
                    && _2dayPerc < DOWN_PERC_WIDE && currDelta < deltaTarget && (freshPrice < avgBuy || avgBuy == 0.0)) {
                int id = autoTradeID.incrementAndGet();
                if (pmchy < -20 || (checkTimeRangeBool(lt, 13, 0, 15, 0))) {
                    buySize = 4;
                } else {
                    buySize = CONSERVATIVE_SIZE;
                }
                Order o = placeBidLimit(freshPrice, buySize);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, PERC_MA));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "perc MA buy", globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast shortlong",
                        r(maShortSecLast), r(maLongSecLast), "|anchor ", anchorIndex, "|perc", todayPerc, "|2d Perc ",
                        _2dayPerc, "pmChg", pmchy, "|delta Base pmchg weekday target ",
                        baseDelta, pmchgDelta, weekdayDelta, deltaTarget, "avg buy sell ", avgBuy, avgSell));
            } else if (!noMoreSell.get() && maShortLast < maLongLast && maShortSecLast >= maLongSecLast
                    && _2dayPerc > UP_PERC_WIDE && currDelta > deltaTarget && (freshPrice > avgSell || avgSell == 0.0)) {
                int id = autoTradeID.incrementAndGet();
                if (pmchy > 20) {
                    sellSize = 2;
                } else {
                    sellSize = CONSERVATIVE_SIZE;
                }
                Order o = placeOfferLimit(freshPrice, sellSize);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, PERC_MA));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "perc MA sell", globalIdOrderMap.get(id)
                        , "Last shortlong ", r(maShortLast), r(maLongLast), "2ndLast Shortlong",
                        r(maShortSecLast), r(maLongSecLast), " anchor ", anchorIndex, "perc", todayPerc, "2d Perc ",
                        _2dayPerc, "pmChg", pmchy, "|delta Base pmchg weekday target ",
                        baseDelta, pmchgDelta, weekdayDelta, deltaTarget, "avg buy sell ", avgBuy, avgSell));
            }
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

        NavigableMap<LocalDateTime, SimpleBar> futPriceMap = futData.get(ibContractToFutType(activeFuture));

        int pmPercChg = getPMPercChg(futPriceMap, TDate);
        int currPerc = getPercentileForLast(futPriceMap);

        LocalDateTime lastOrderTime = getLastOrderTime(OVERNIGHT);

        if (checkTimeRangeBool(lt, 3, 0, 5, 0)
                && MINUTES.between(lastOrderTime, nowMilli) > ORDER_WAIT_TIME) {
            if (currDelta > getBearishTarget() && currPerc > UP_PERC && pmPercChg > PMCHY_HI) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimit(freshPrice, CONSERVATIVE_SIZE);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Overnight Short", OVERNIGHT));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "O/N sell @ ", freshPrice, " curr p% ", currPerc,
                        "pmPercChg ", pmPercChg));
            } else if (currDelta < getBullishTarget() && currPerc < DOWN_PERC && pmPercChg < PMCHY_LO) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimit(freshPrice, CONSERVATIVE_SIZE);
                globalIdOrderMap.put(id, new OrderAugmented(nowMilli, o, "Overnight Long", OVERNIGHT));
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler(id));
                outputOrderToAutoLog(str(o.orderId(), "O/N buy @ ", freshPrice,
                        "perc: ", currPerc, "pmPercChg", pmPercChg));
            }
        }

        String outputString = str("||O/N||", nowMilli.format(DateTimeFormatter.ofPattern("M-d H:mm")),
                "||curr P%: ", currPerc,
                "||curr P: ", futPriceMap.lastEntry().getValue().getClose(),
                "||index: ", r(indexPrice),
                "||BID ASK ", bidMap.getOrDefault(ibContractToFutType(activeFuture), 0.0),
                askMap.getOrDefault(ibContractToFutType(activeFuture), 0.0),
                "pmPercChg", pmPercChg);

        outputToAutoLog(outputString);
        requestOvernightExecHistory();
    }


    /**
     * cancel order of type
     *
     * @param type      type of trade to cancel
     * @param nowMilli  time now
     * @param timeLimit how long to wait
     */
    private static void checkCancelOrders(AutoOrderType type, LocalDateTime nowMilli, int timeLimit) {
        long ordersNum = globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type).count();

        if (ordersNum != 0) {
            OrderStatus lastOrdStatus = globalIdOrderMap.entrySet().stream()
                    .filter(e -> e.getValue().getOrderType() == type)
                    .max(Comparator.comparing(e -> e.getValue().getOrderTime()))
                    .map(e -> e.getValue().getStatus()).orElse(OrderStatus.Unknown);

            LocalDateTime lastOTime = getLastOrderTime(type);

            if (lastOrdStatus != OrderStatus.Filled && lastOrdStatus != OrderStatus.Cancelled
                    && lastOrdStatus != OrderStatus.ApiCancelled && lastOrdStatus != OrderStatus.PendingCancel) {

                if (MINUTES.between(lastOTime, nowMilli) > timeLimit) {
                    globalIdOrderMap.entrySet().stream().filter(e -> e.getValue().getOrderType() == type)
                            .forEach(e -> {
                                if (e.getValue().getStatus() == OrderStatus.Submitted ||
                                        e.getValue().getStatus() == OrderStatus.Created) {
                                    apcon.cancelOrder(e.getValue().getOrder().orderId());
                                    e.getValue().setFinalActionTime(LocalDateTime.now());
                                    e.getValue().setStatus(OrderStatus.Cancelled);
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
                if (lt.isAfter(LocalTime.of(15, 0))) {
                    return 1000000;
                } else {
                    return 100000;
                }
            case TUESDAY:
                if (lt.isBefore(LocalTime.of(15, 0))) {
                    return 1000000;
                }
            case WEDNESDAY:
                return -100000;
        }
        return 0.0;
    }

    private static int getPDPercentile() {
        LocalDate d = (LocalTime.now().isBefore(LocalTime.of(5, 0))) ? LocalDate.now().minusDays(1) :
                LocalDate.now();
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

    private static double getCurrentMA() {
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        if (price5.size() <= 2) return 0.0;

        NavigableMap<LocalDateTime, Double> sma = getMAGen(price5, shortMAPeriod);
        return sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;
    }

    //**********************************************Trade types **********************************************

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

                if (priceMapBarDetail.containsKey(name) && ldt.toLocalDate().equals(LocalDate.now())
                        && ldt.toLocalTime().isAfter(LocalTime.of(8, 59))) {
                    priceMapBarDetail.get(name).put(ldt.toLocalTime(), close);
                }

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
        if (tradesMap.get(ibContractToFutType(activeFuture)).size() > 0) {
            currentDirection = tradesMap.get(ibContractToFutType(activeFuture)).lastEntry().getValue().getSizeAll() > 0 ?
                    Direction.Long : Direction.Short;
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

        NavigableMap<LocalDateTime, SimpleBar> futdata =
                trimDataFromYtd(futData.get(ibContractToFutType(activeFuture)));

        int pmChgY = getPercentileChgFut(futdata, futdata.firstKey().toLocalDate());
        int closePercY = getClosingPercentile(futdata, futdata.firstKey().toLocalDate());
        int openPercY = getOpenPercentile(futdata, futdata.firstKey().toLocalDate());
        int pmChg = getPercentileChgFut(futdata, getTradeDate(futdata.lastKey()));
        int percLast = getPercentileForLast(futdata);
        int todayPerc = getPercentileForLastPred(futdata, e -> e.getKey().toLocalDate()
                .equals(getTradeDate(LocalDateTime.now())));

        Map<AutoOrderType, Double> quantitySumByOrder = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .collect(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType(),
                        Collectors.summingDouble(e1 -> e1.getValue().getOrder().signedTotalQuantity())));

        Map<AutoOrderType, Long> numTradesByOrder = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .collect(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType(),
                        Collectors.counting()));

        String pnlString = globalIdOrderMap.entrySet().stream()
                .filter(e -> e.getValue().getStatus() == OrderStatus.Filled)
                .collect(Collectors.collectingAndThen(Collectors.groupingByConcurrent(e -> e.getValue().getOrderType()
                        , Collectors.summingDouble(e -> e.getValue().getPnl(futPriceMap.get(f)))),
                        e -> e.entrySet().stream().map(e1 -> str(" ||| ", e1.getKey(),
                                "# Trades: ", numTradesByOrder.getOrDefault(e1.getKey(), 0L),
                                "Tot Q: ", quantitySumByOrder.getOrDefault(e1.getKey(), 0d), r(e1.getValue())))
                                .collect(Collectors.joining(","))));

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
            //breakdown of pnl
            updateLog(" net pnl " + r(netTradePnl) + " breakdown: " + pnlString);
            updateLog(" net commission " + netTotalCommissions);
            updateLog(" MTM + Trade " + r(netTradePnl + mtmPnl));
            updateLog("pos "
                    + currentPosMap.getOrDefault(f, 0) + " Delta " + r(getNetPtfDelta()) +
                    " Stock Delta " + r(ChinaPosition.getStockPtfDelta()) + " Fut Delta " + r(XUTrader.getFutDelta())
                    + "HK Delta " + r(ChinaPosition.getStockPtfDeltaCustom(e -> isHKStock(e.getKey())))
                    + " China Delta " + r(ChinaPosition.getStockPtfDeltaCustom(e -> isChinaStock(e.getKey()))));
            updateLog(str("2D p%:", percLast, "1D p%", todayPerc,
                    "pmChgY:", pmChgY, "openY:", openPercY, "closeY:", closePercY, "pmChg", pmChg));
            updateLog(" expiring delta " + getExpiringDelta());
        });
    }
}

