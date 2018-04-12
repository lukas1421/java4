package apidemo;

import TradeType.FutureTrade;
import TradeType.MATrade;
import TradeType.TradeBlock;
import auxiliary.SimpleBar;
import client.*;
import controller.ApiConnection;
import controller.ApiController;
import graph.DisplayGranularity;
import graph.GraphXuTrader;
import handler.HistoricalHandler;
import handler.XUOvernightTradeExecHandler;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static apidemo.TradingConstants.FUT_COLLECTION_TIME;
import static apidemo.TradingConstants.ftseIndex;
import static utility.Utility.*;

//import java.util.concurrent.atomic.AtomicInteger;

public final class XUTrader extends JPanel implements HistoricalHandler, ApiController.IDeepMktDataHandler,
        ApiController.ITradeReportHandler, ApiController.IOrderHandler, ApiController.ILiveOrderHandler
        , ApiController.IPositionHandler, ApiController.IConnectionHandler {

    static ApiController apcon;

    //overnight trades
    private static final int maxOvernightTrades = 5;
    private static AtomicInteger overnightTradesDone = new AtomicInteger(0);
    private static final double maxOvernightDeltaChgUSD = 50000.0;

    // ma trades
    private static volatile int currentMAPeriod = 60;
    private static AtomicBoolean lastTradeLong = new AtomicBoolean(true);
    private static LocalDateTime lastTradeTime = LocalDateTime.now();
    private static AtomicInteger cumuMATrades = new AtomicInteger(0);


    //display
    public static volatile Predicate<LocalDateTime> displayPred = e -> true;

    //new ApiController(new XUConnectionHandler(),
    //new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
    //ApiController apcon = new ApiController(new IConnectionHandler.DefaultConnectionHandler()
    // ,new ApiConnection.ILogger.DefaultLogger(),new ApiConnection.ILogger.DefaultLogger());

    private final static Contract frontFut = utility.Utility.getFrontFutContract();
    private final static Contract backFut = utility.Utility.getBackFutContract();

    @SuppressWarnings("unused")
    private static Predicate<? super Map.Entry<FutType, ?>> graphPred = e -> true;
    static volatile Contract activeFuture = frontFut;
    public static volatile DisplayGranularity gran = DisplayGranularity._1MDATA;

    public static volatile Map<Double, Double> activeFutLiveOrder = new HashMap<>();

    //List<Integer> orderList = new LinkedList<>();
    //AtomicInteger orderInitial = new AtomicInteger(3000001);
//    private static volatile double currentBidFront;
//    private static volatile double currentAskFront;
//    private static volatile double currentPriceFront;
//    static volatile double currentBidBack;
//    static volatile double currentAskBack;
//    static volatile double currentPriceBack;

    public static volatile EnumMap<FutType, Double> bidMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Double> askMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Double> futPriceMap = new EnumMap<>(FutType.class);
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
            d.width = 1900;
            return d;
        }
    };

    public static AtomicInteger graphWidth = new AtomicInteger(3);

    public static volatile EnumMap<FutType, NavigableMap<LocalDateTime, SimpleBar>> futData = new EnumMap<>(FutType.class);

    //@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    public static volatile EnumMap<FutType, Integer> currentPosMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> botMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> soldMap = new EnumMap<>(FutType.class);

    public static volatile boolean showTrades = false;
    static volatile boolean connectionStatus = false;
    static volatile JLabel connectionLabel = new JLabel();

    //private static volatile AtomicInteger connectionID = new AtomicInteger(100);

    public XUTrader getThis() {
        return this;
    }

//    public static ApiController getStandAloneApicon() {
//        return new ApiController(new XUConnectionHandler(), new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
//    }

    XUTrader(ApiController ap) {

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
            System.out.println(" buying limit ");
            apcon.placeOrModifyOrder(activeFuture, placeBidLimit(bidMap.get(ibContractToFutType(activeFuture)), 1.0), this);
        });

        JButton offerLimitButton = new JButton("Sell Limit");

        offerLimitButton.addActionListener(l -> {
            System.out.println(" selling limit ");
            apcon.placeOrModifyOrder(activeFuture, placeOfferLimit(askMap.get(ibContractToFutType(activeFuture)), 1.0), this);
        });

        JButton buyOfferButton = new JButton(" Buy Now");
        buyOfferButton.addActionListener(l -> {
            System.out.println(" buy offer ");
            apcon.placeOrModifyOrder(activeFuture, buyAtOffer(askMap.get(ibContractToFutType(activeFuture)), 1.0)
                    , this);
        });

        JButton sellBidButton = new JButton(" Sell Now");
        sellBidButton.addActionListener(l -> {
            System.out.println(" sell bid ");
            apcon.placeOrModifyOrder(activeFuture, sellAtBid(bidMap.get(ibContractToFutType(activeFuture)), 1.0), this);
        });

        JButton getPositionButton = new JButton(" get pos ");
        getPositionButton.addActionListener(l -> {
            System.out.println(" getting pos ");
            apcon.reqPositions(this);
        });

        JButton level2Button = new JButton("level2");
        level2Button.addActionListener(l -> {
            System.out.println(" getting level 2 button pressed");
            requestLevel2Data();
        });

        JButton refreshButton = new JButton("Refresh");

        refreshButton.addActionListener(l -> {
            String time = (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).getSecond() != 0)
                    ? (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()) :
                    (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString() + ":00");

            currTimeLabel.setText(time);
            //xuGraph.fillInGraph(xuFrontData);
            xuGraph.fillInGraph(futData.get(ibContractToFutType(activeFuture)));
            xuGraph.fillTradesMap(XUTrader.tradesMap.get(ibContractToFutType(activeFuture)));
            xuGraph.setName(ibContractToSymbol(activeFuture));
            xuGraph.setFut(ibContractToFutType(activeFuture));
            xuGraph.setPrevClose(futPrevCloseMap.get(ibContractToFutType(activeFuture)));
            xuGraph.refresh();
            apcon.reqPositions(getThis());
            repaint();
            //xuGraph.computeMAStrategyForAll();
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
                String time = (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).getSecond() != 0)
                        ? (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString())
                        : (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString() + ":00");

                apcon.reqPositions(getThis());
                activeFutLiveOrder = new HashMap<>();
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
        });

        JButton stopComputeButton = new JButton("Stop Processing");
        stopComputeButton.addActionListener(l -> {
            ses2.shutdown();
            System.out.println(" executor status is " + ses.isShutdown());
        });


        JButton execButton = new JButton("Exec");

        execButton.addActionListener(l -> {
            System.out.println(" getting exec details");
            requestExecHistory();
        });

        JButton processTradesButton = new JButton("Process");

        processTradesButton.addActionListener(l -> ses2.scheduleAtFixedRate(() -> {
            SwingUtilities.invokeLater(() -> {
                XUTrader.clearLog();
                XUTrader.updateLog("**************************************************************");
            });
            XUTrader.processTradeMapActive();
        }, 0, 10, TimeUnit.SECONDS));

        JButton overnightButton = new JButton(" Overnight ");
        overnightButton.addActionListener(l -> ses2.scheduleAtFixedRate(this::overnightTrader, 0, 1, TimeUnit.MINUTES));

        JButton maTraderButton = new JButton(" MA Trader ");
        maTraderButton.addActionListener(l -> ses2.scheduleAtFixedRate(this::movingAverageTrader, 0, 1, TimeUnit.MINUTES));

        //JButton connect7496 = new JButton("Connect 7496");
//        connect7496.addActionListener(l -> {
//            System.out.println(" trying to connect 7496");
//            try {
//                apcon.connect("127.0.0.1", 7496, connectionID.incrementAndGet(), "");
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//        });
//        JButton connect4001 = new JButton("Connect 4001");
//        connect4001.addActionListener(l -> {
//            System.out.println(" trying to connect 4001");
//            try {
//                apcon.connect("127.0.0.1", 4001, connectionID.incrementAndGet(), "");
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//        });

        JButton getData = new JButton("Data");
        getData.addActionListener(l -> {
            System.out.println(" getting data ");
            loadXU();
        });

        JButton graphButton = new JButton("graph");
        graphButton.addActionListener(l -> {
            System.out.println(" graphing ");
            xuGraph.setNavigableMap(futData.get(ibContractToFutType(activeFuture)), displayPred);
            xuGraph.refresh();
            repaint();
        });

        JToggleButton showTodayOnly = new JToggleButton(" Today Only ");
        showTodayOnly.addActionListener(l -> {
            if (showTodayOnly.isSelected()) {
                displayPred = e -> e.toLocalDate().equals(LocalDate.now()) && e.toLocalTime().isAfter(LocalTime.of(8, 59));
                showTodayOnly.setText(" Show All ");
            } else {
                displayPred = e -> true;
                showTodayOnly.setText(" Show Today ");
            }
        });

        JToggleButton showGraphButton = new JToggleButton("Show Trades");
        showGraphButton.addActionListener(l -> {
            if (showGraphButton.isSelected()) {
                showTrades = true;
                System.out.println(" show trade is " + showTrades);
            } else {
                showTrades = false;
                System.out.println(" show trade is " + showTrades);
            }
        });


        JButton cancelAllOrdersButton = new JButton("Cancel Orders");
        cancelAllOrdersButton.addActionListener(l -> {
            apcon.cancelAllOrders();
            activeFutLiveOrder = new HashMap<>();
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
        _1mButton.setSelected(true);

        JRadioButton _5mButton = new JRadioButton("5m");
        _5mButton.addActionListener(l -> gran = DisplayGranularity._5MDATA);

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

        JButton graphWidthUp = new JButton(" UP ");
        graphWidthUp.addActionListener(l -> {
            graphWidth.incrementAndGet();
            SwingUtilities.invokeLater(xuGraph::refresh);
        });

        JButton graphWidthDown = new JButton(" Down ");
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
        controlPanel2.add(getPositionButton);
        controlPanel2.add(level2Button);
        controlPanel2.add(refreshButton);
        controlPanel2.add(computeButton);
        controlPanel2.add(execButton);
        controlPanel2.add(processTradesButton);
        //controlPanel2.add(connect7496);
        //controlPanel2.add(connect4001);
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
        controlPanel2.add(overnightButton);
        controlPanel2.add(maTraderButton);

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
                        System.out.println(" double clicked buy " + l.getName());
                        double bidPrice = bidPriceList.getOrDefault(l.getName(), 0.0);
                        System.out.println(" bid price " + bidPrice + " check if order price makes sense "
                                + checkIfOrderPriceMakeSense(bidPrice));
                        if (checkIfOrderPriceMakeSense(bidPrice) && marketOpen(LocalTime.now())) {
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
                        System.out.println(" offer  price list " + offerPriceList.toString());

                        if (checkIfOrderPriceMakeSense(offerPrice) && marketOpen(LocalTime.now())) {
                            apcon.placeOrModifyOrder(activeFuture, placeOfferLimit(offerPrice, 1.0), getThis());
                        } else {
                            throw new IllegalArgumentException("price out of bound");
                        }
                    }
                }
            });
        });

        JPanel deepPanel = new JPanel();
        //deepPanel.setPreferredSize(new Dimension(100,100));
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


    private void movingAverageTrader() {
        //start with 5 min candles, 60 min lines
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        SimpleBar lastBar = price5.lastEntry().getValue();
        NavigableMap<LocalDateTime, Double> sma;

        AtomicBoolean currentLong = new AtomicBoolean(true);
        List<MATrade> maTrades = new LinkedList<>();

        sma = XuTraderHelper.getMAGen(price5, currentMAPeriod);
        double maLast = sma.lastEntry().getValue();

        if (lastBar.includes(maLast)) {
            if (maLast > lastBar.getOpen()) {
                if (ChronoUnit.MINUTES.between(lastTradeTime, price5.lastEntry().getKey()) > 10) {
                    if (!lastTradeLong.get()) {
                        apcon.placeOrModifyOrder(activeFuture, placeBidLimit(bidMap.get(ibContractToFutType(activeFuture)),
                                2.0), this);
                        lastTradeLong.set(true);
                        lastTradeTime = LocalDateTime.now();
                    }
                } else {
                    currentMAPeriod = XuTraderHelper.getUntouchedMAPeriod(price5, lastTradeTime);
                }
            } else if (maLast < lastBar.getOpen()) {
                if (ChronoUnit.MINUTES.between(lastTradeTime, price5.lastEntry().getKey()) > 10) {
                    if (lastTradeLong.get()) {
                        apcon.placeOrModifyOrder(activeFuture, placeOfferLimit(askMap.get(ibContractToFutType(activeFuture)),
                                2.0), this);
                        lastTradeLong.set(false);
                        lastTradeTime = LocalDateTime.now();
                    }
                } else {
                    currentMAPeriod = XuTraderHelper.getUntouchedMAPeriod(price5, lastTradeTime);
                }
            }
        }

        // analysis
        sma.forEach((lt, ma) -> {
            if (price5.containsKey(lt) && price5.get(lt).includes(ma)) {
                SimpleBar sb = price5.get(lt);
                System.out.println(getStr(" crossed @ ", lt, ma));
                if (ma > sb.getOpen()) {
                    if (!currentLong.get()) {
                        if (maTrades.size() > 0) {
                            LocalDateTime lastTradeTime = ((LinkedList<MATrade>) maTrades).peekLast().getTradeTime();
                            long minSinceLastTrade = ChronoUnit.MINUTES.between(lastTradeTime, lt);
                            System.out.println(" Mins since last trade is " + minSinceLastTrade);
                            if (minSinceLastTrade <= 10) {
                                int untouchedMAPeriod = XuTraderHelper.getUntouchedMAPeriod(price5, lastTradeTime);
                                System.out.println(lt + " untouched period is " + untouchedMAPeriod);
                                currentMAPeriod = untouchedMAPeriod;
                            }
                        }

                        maTrades.add(new MATrade(lt, ma, maTrades.size() == 0 ? 1 : 2));
                        currentLong.set(true);
                        System.out.println(" long ");
                    }
                } else {
                    //maTradeSet.add(new MATrade(lt, ma, -1));
                    if (currentLong.get()) {
                        if (maTrades.size() > 0) {
                            LocalDateTime lastTradeTime = ((LinkedList<MATrade>) maTrades).peekLast().getTradeTime();
                            long minSinceLastTrade = ChronoUnit.MINUTES.between(lastTradeTime, lt);
                            System.out.println(" Mins since last trade is " + minSinceLastTrade);

                            if (minSinceLastTrade <= 10) {
                                int untouchedMAPeriod = XuTraderHelper.getUntouchedMAPeriod(price5, lastTradeTime);
                                System.out.println(lt + " untouched period is " + untouchedMAPeriod);
                                currentMAPeriod = untouchedMAPeriod;
                            }
                        }
                        maTrades.add(new MATrade(lt, ma, maTrades.size() == 0 ? -1 : -2));
                        currentLong.set(false);
                        System.out.println(" short ");
                    }
                }
            }
        });
    }

    private static void maTradeAnalysis() {

    }


    // overnight close trading
    private void overnightTrader() {

        System.out.println(" outputting options");
        LocalDateTime now = LocalDateTime.now();

        LocalDate TDate = now.toLocalTime().isAfter(LocalTime.of(0, 0))
                && now.toLocalTime().isBefore(LocalTime.of(5, 0)) ? LocalDate.now().minusDays(1L)
                : LocalDate.now();

        int absLotsTraded = 0;
        double netTradedDelta = 0.0;

        if (XUTrader.overnightTradesMap.get(ibContractToFutType(activeFuture)).size() > 0) {
            absLotsTraded = XUTrader.overnightTradesMap.get(ibContractToFutType(activeFuture))
                    .entrySet().stream().mapToInt(e -> e.getValue().getSizeAllAbs()).sum();
            netTradedDelta = XUTrader.overnightTradesMap.get(ibContractToFutType(activeFuture))
                    .entrySet().stream().mapToDouble(e -> e.getValue().getDeltaAll()).sum();
        }


        double indexPrice = (ChinaData.priceMapBar.containsKey(ftseIndex) &&
                ChinaData.priceMapBar.get(ftseIndex).size() > 0) ?
                ChinaData.priceMapBar.get(ftseIndex).lastEntry().getValue().getClose() : 0.0;

        NavigableMap<LocalDateTime, SimpleBar> futPriceMap = futData.get(ibContractToFutType(activeFuture));

        LocalDateTime ytdCloseTime = LocalDateTime.of(TDate, LocalTime.of(15, 0));

        NavigableMap<LocalDateTime, SimpleBar> filteredPriceMap = futPriceMap.entrySet().stream()
                .filter(e -> e.getKey().isAfter(ytdCloseTime))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));

        int currPercentile = getPercentileForLast(filteredPriceMap);

        double currentPrice = 0.0;
        double pd = 0.0;

        if (futPriceMap.size() > 0) {
            currentPrice = futPriceMap.lastEntry().getValue().getClose();
            pd = indexPrice != 0.0 ? Math.round(10000d * (currentPrice / indexPrice - 1)) / 10000d : 0.0;
        }

        if (absLotsTraded < maxOvernightTrades
                && Math.abs(netTradedDelta) < maxOvernightDeltaChgUSD) {

            if (now.toLocalTime().isBefore(LocalTime.of(5, 0)) &&
                    now.toLocalTime().truncatedTo(ChronoUnit.MINUTES).isAfter(LocalTime.of(4, 40))) {

                if (currPercentile > 90 && pd > 0.01) {
                    double candidatePrice = askMap.getOrDefault(ibContractToFutType((activeFuture)), 0.0);

                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        outputToOvernightLog(getStr(now, " placing sell order @ ", candidatePrice,
                                " curr percentile ", currPercentile, " current pd ", pd));
                        apcon.placeOrModifyOrder(activeFuture, placeOfferLimit(candidatePrice, 1.0),
                                this);
                    }
                } else if (currPercentile < 10 && pd < -0.01) {
                    double candidatePrice = bidMap.getOrDefault(ibContractToFutType(activeFuture), 0.0);

                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        outputToOvernightLog(getStr(now, " placing buy order @ ", candidatePrice,
                                " curr percentile ", currPercentile, " current pd ", pd));
                        apcon.placeOrModifyOrder(activeFuture, placeBidLimit(candidatePrice, 1.0)
                                , this);
                    }
                } else {
                    outputToOvernightLog(getStr(now, " nothing done "));
                    System.out.println(" nothing done ");
                }
            } else {
                System.out.println(" outside tradable time slot");
            }
        } else {
            outputToOvernightLog(getStr(now, " trades or delta exceeded MAX "));
            //System.out.println(" trades exceeded max number allowed ");
        }

        String outputString = getStr("overnight", now.format(DateTimeFormatter.ofPattern("M-d H:mm:ss")),
                " overnight trade done", absLotsTraded, "current percentile ", currPercentile, " PD: ", pd, "current price ",
                filteredPriceMap.lastEntry().getValue().getClose(), " index ", Math.round(100d * indexPrice) / 100d,
                "bid ", bidMap.getOrDefault(ibContractToFutType(activeFuture), 0.0),
                " ask ", askMap.getOrDefault(ibContractToFutType(activeFuture), 0.0));

        System.out.println(outputString);
        outputToOvernightLog(outputString);
        requestOvernightExecHistory();
    }

    private static void outputToOvernightLog(String s) {
        File output = new File(TradingConstants.GLOBALPATH + "overnightLog.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            out.append(s);
            out.newLine();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    private <T extends Temporal> int getPercentileForLast(NavigableMap<T, SimpleBar> map) {
        if (map.size() > 0) {
            double max = map.entrySet().stream().mapToDouble(e -> e.getValue().getHigh()).max().orElse(0.0);
            double min = map.entrySet().stream().mapToDouble(e -> e.getValue().getLow()).min().orElse(0.0);
            double last = map.lastEntry().getValue().getClose();
            System.out.println(getStr(" getPercentileForLast max min last ", max, min, last));
            return (int) Math.round(100d * ((last - min) / (max - min)));
        }
        return 50;
    }

    private void loadXU() {
        System.out.println(" getting XU data ");
        apcon.getSGXA50Historical2(30000, this);
    }

    private boolean checkIfOrderPriceMakeSense(double p) {
        FutType f = ibContractToFutType(activeFuture);
        System.out.println(" current ask bid price " + askMap.get(f) + " " + bidMap.get(f) + " " + futPriceMap.get(f) + " ");
        return !(p == 0.0) && !(bidMap.getOrDefault(f, 0.0) == 0.0)
                && !(askMap.getOrDefault(f, 0.0) == 0.0) && !(futPriceMap.getOrDefault(f, 0.0) == 0.0)
                && (Math.abs(p / futPriceMap.getOrDefault(f, 0.0) - 1) < 0.02);
    }

    private boolean marketOpen(LocalTime t) {
        return t.isAfter(LocalTime.of(8, 59));
    }

    private void connectToTWS() {
        System.out.println(" trying to connect");
        try {
            apcon.connect("127.0.0.1", 7496, 101, "");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        apcon.client().reqIds(-1);
    }

    private static ApiController getAPICon() {
        return apcon;
    }

    private Order buyAtOffer(double p, double quantity) {
        System.out.println(" buy at offer " + p);
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        return o;
    }

    private Order sellAtBid(double p, double quantity) {
        System.out.println(" sell at bid " + p);

        Order o = new Order();
        o.action(Types.Action.SELL);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        return o;
    }

    private Order placeBidLimit(double p, double quantity) {
        System.out.println(" place bid limit " + p);
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.outsideRth(true);
        o.tif(Types.TimeInForce.GTC);
        return o;
    }

    private Order placeOfferLimit(double p, double quantity) {
        System.out.println(" place offer limit " + p);
        Order o = new Order();
        o.action(Types.Action.SELL);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(quantity);
        o.tif(Types.TimeInForce.GTC);
        o.outsideRth(true);
        return o;
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

        LocalDate currDate = LocalDate.now();

        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            LocalDateTime ldt = LocalDateTime.of(ld, lt);
            //System.out.println(" handle hist in xu trader ld lt  " + LocalDateTime.of(ld, lt));
            //System.out.println(getStr("name date open high low close ", name, date, open, high, low, close));

            if (!ld.equals(currDate) && lt.equals(LocalTime.of(14, 59))) {
                futPrevCloseMap.put(FutType.get(name), close);
            }

//            if (!ld.equals(currDate) && lt.isBefore(LocalTime.of(17, 1))) {
//                //System.out.println(getStr(" fut close map (open close) ", FutType.get(name), lt, open, close));
//                futPrevCloseMap.put(FutType.get(name), open);
//            }

//            if (ld.equals(currDate) && ((lt.isAfter(LocalTime.of(8, 59)) && lt.isBefore(LocalTime.of(11, 31)))
//                    || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(23, 59))))) {

            int daysToGoBack;
            if (currDate.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
                daysToGoBack = 4;
            } else {
                daysToGoBack = 2;
            }

            if (ldt.toLocalDate().isAfter(currDate.minusDays(daysToGoBack)) && FUT_COLLECTION_TIME.test(ldt)) {

                if (lt.equals(LocalTime.of(9, 0))) {
                    futOpenMap.put(FutType.get(name), open);
                    System.out.println(" today open is for " + name + " " + open);
                }
                futData.get(FutType.get(name)).put(ldt, new SimpleBar(open, high, low, close));
            }
        } else {
            System.out.println(getStr(date, open, high, low, close));
        }
    }

    @Override
    public void actionUponFinish(String name) {
        System.out.println(" printing fut data " + name + " " + futData.get(FutType.get(name)).lastEntry());
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
        //System.out.println( tradeKey );
        //System.out.println( contract.toString() );
        //System.out.println(" exec " + execution.side() + " "+ execution.cumQty() + "　" + execution.time() + " " + execution.price()  + " "+ execution.execId());
        //String ticker = ibContractToSymbol(contract);

        FutType f = ibContractToFutType(contract);
        System.out.println(" exec " + execution.side() + "　" + execution.time() + " " + execution.cumQty()
                + " " + execution.price() + " " + execution.orderRef() + " " + execution.orderId() + " " + execution.permId() + " "
                + execution.shares());
        int sign = (execution.side().equals("BOT")) ? 1 : -1;

        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));

        //if (ldt.getDayOfMonth() == LocalDateTime.now().getDayOfMonth()) {
        if (ldt.isAfter(LocalDateTime.of(LocalDateTime.now().toLocalDate().minusDays(1L), LocalTime.of(15, 0)))) {
            if (XUTrader.tradesMap.get(f).containsKey(ldt)) {
                XUTrader.tradesMap.get(f).get(ldt)
                        .addTrade(new FutureTrade(execution.price(), sign * execution.cumQty()));
            } else {
                XUTrader.tradesMap.get(f).put(ldt,
                        new TradeBlock(new FutureTrade(execution.price(), sign * execution.cumQty())));
            }
//            if (XUTrader.tradesMapFront.containsKey(ldt.toLocalTime())) {
//                XUTrader.tradesMapFront.get(ldt.toLocalTime()).addTrade(new IBTrade(execution.price(), sign * execution.cumQty()));
//            } else {
//                XUTrader.tradesMapFront.put(ldt.toLocalTime(), new IBTrade(execution.price(), sign * execution.cumQty()));
//            }
            //System.out.println(" printing all trades");
            //XUTrader.tradesMapFront.entrySet().stream().forEach(System.out::println);
            //XUTrader.processTradeMapActive();
        }
    }

    @Override
    public void tradeReportEnd() {
        System.out.println(" trade report end printing");
        XUTrader.tradesMap.get(ibContractToFutType(activeFuture)).entrySet().forEach(System.out::println);
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
    }


    //ApiController.IOrderHandler
    @Override
    public void orderState(OrderState orderState) {
        //System.out.println(" order state is " + orderState);
        //XUTrader.updateLog(orderState.toString());
    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId,
                            int parentId, double lastFillPrice, int clientId, String whyHeld) {

        XUTrader.updateLog(Utility.getStr(" status filled remaining avgFillPrice ",
                status, filled, remaining, avgFillPrice));

        if (status.equals(OrderStatus.Filled)) {
            XUTrader.createDialog(Utility.getStr(" status filled remaining avgFillPrice ",
                    status, filled, remaining, avgFillPrice));
        }
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        XUTrader.updateLog(" handle error code " + errorCode + " message " + errorMsg);
    }

    //live order handler
    @Override
    public void openOrder(Contract contract, Order order, OrderState orderState) {
        if (ibContractToSymbol(contract).equals(ibContractToSymbol(activeFuture))) {
            double sign = order.action().equals(Types.Action.BUY) ? 1 : -1;
            if (!activeFutLiveOrder.containsKey(order.lmtPrice())) {
                activeFutLiveOrder.put(order.lmtPrice(), sign * order.totalQuantity());
            } else {
                activeFutLiveOrder.put(order.lmtPrice(),
                        activeFutLiveOrder.get(order.lmtPrice()) + sign * order.totalQuantity());
            }
        } else {
            System.out.println(" contract not equal to activefuture ");
        }

        //System.out.println(getStr(ibContractToSymbol(contract), order.toString(), orderState.toString()));
        //XUTrader.updateLog(getStr(ibContractToSymbol(contract), order.toString(), orderState.toString()));
    }

    @Override
    public void openOrderEnd() {
        //System.out.println(" open order ends ");
        //System.out.println(" active fut order map " + activeFutLiveOrder);
    }

    @Override
    public void orderStatus(int orderId, OrderStatus status, int filled, int remaining,
                            double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {

        System.out.println(" in order status ");

        XUTrader.updateLog(Utility.getStr(" status filled remaining avgFillPrice ",
                status, filled, remaining, avgFillPrice));

        if (status.equals(OrderStatus.Filled)) {
            XUTrader.createDialog(Utility.getStr(" status filled remaining avgFillPrice ",
                    status, filled, remaining, avgFillPrice));
        }
    }

    @Override
    public void handle(int orderId, int errorCode, String errorMsg) {
        System.out.println(" Xutrader handle ");
        XUTrader.updateLog(" handle error code " + errorCode + " message " + errorMsg);
    }

    // position
    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        //System.out.println (" proper handling here XXXX ");
        String ticker = utility.Utility.ibContractToSymbol(contract);
        if (contract.symbol().equals("XINA50") && position != 0.0) {
            FutType f = ibContractToFutType(contract);
            //System.out.println(getStr(" in position xutrader ", ticker, position, avgCost));
            currentPosMap.put(f, (int) position);
        }

        SwingUtilities.invokeLater(() -> {
            if (ticker.equals("SGXA50")) {
//                XUTrader.updateLog(" account " + account + "\n");
//                XUTrader.updateLog(" contract " + contract.symbol()+ "\n");
//                XUTrader.updateLog(" Exchange " + contract.primaryExch()+ "\n");
//                XUTrader.updateLog(" Local symbol " + contract.localSymbol()+ "\n");
//                XUTrader.updateLog(" Last trade date " + contract.lastTradeDateOrContractMonth()+ "\n");
//                XUTrader.updateLog(" currency " + contract.currency()+ "\n");
//                XUTrader.updateLog(" pos " + position + "\n");
                //XUTrader.setNetPositionFront((int) position);
//                XUTrader.updateLog(" cost "+ avgCost+ "\n");
//                XUTrader.updateLog("__________________________________________");
                XUTrader.outputArea.repaint();
            }
        });
    }

    @Override
    public void positionEnd() {
        //System.out.println( " position request ends XXXXX ");
    }


    // connection
    @Override
    public void connected() {
        System.out.println("connected in XUconnectionhandler");
        XUTrader.connectionStatus = true;
        XUTrader.connectionLabel.setText(Boolean.toString(XUTrader.connectionStatus));
        XUTrader.apcon.setConnectionStatus(true);
    }

    @Override
    public void disconnected() {
        System.out.println("disconnected in XUConnectionHandler");
        XUTrader.connectionStatus = false;
        XUTrader.connectionLabel.setText(Boolean.toString(XUTrader.connectionStatus));
    }

    @Override
    public void accountList(ArrayList<String> list) {
        System.out.println(" account list is " + list);
    }

    @Override
    public void error(Exception e) {
        System.out.println(" error in XUConnectionHandler");
        e.printStackTrace();
    }

    @Override
    public void message(int id, int errorCode, String errorMsg) {
        System.out.println(" error ID " + id + " error code " + errorCode + " errormsg " + errorMsg);
    }

    @Override
    public void show(String string) {
        System.out.println(" show string " + string);
    }


    private void requestLevel2Data() {
        //apcon.reqDeepMktData(frontFut, 10, new XULevel2Handler());
        apcon.reqDeepMktData(activeFuture, 10, this);
    }

    private void requestExecHistory() {
        System.out.println(" requesting exec history ");
        tradesMap.replaceAll((k, v) -> new ConcurrentSkipListMap<>());
        apcon.reqExecutions(new ExecutionFilter(), this);
    }

    private void requestOvernightExecHistory() {
        System.out.println(" requesting overnight exec history ");
        overnightTradesMap.replaceAll((k, v) -> new ConcurrentSkipListMap<>());
        apcon.reqExecutions(new ExecutionFilter(), XUOvernightTradeExecHandler.DefaultOvernightHandler);
    }


    private void requestXUData() {
        getAPICon().reqXUDataArray();
    }

    private static void createDialog(String msg) {
        JDialog jd = new JDialog();
        jd.setFocusableWindowState(false);
        jd.setSize(new Dimension(700, 200));

        JLabel j1 = new JLabel(msg);
        j1.setPreferredSize(new Dimension(300, 60));

        j1.setFont(j1.getFont().deriveFont(25F));
        j1.setForeground(Color.red);
        j1.setHorizontalAlignment(SwingConstants.CENTER);
        jd.getContentPane().add(j1, BorderLayout.NORTH);

        jd.getContentPane().add(new JLabel(msg), BorderLayout.CENTER);
        jd.setAlwaysOnTop(false);
        jd.getContentPane().setLayout(new BorderLayout());
        jd.setVisible(true);
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

        int unitsBought = tradesMap.get(f).entrySet().stream().filter(e -> e.getValue().getSizeAll() > 0)
                .mapToInt(e -> e.getValue().getSizeAll()).sum();

        int unitsSold = tradesMap.get(f).entrySet().stream().filter(e -> e.getValue().getSizeAll() < 0)
                .mapToInt(e -> e.getValue().getSizeAll()).sum();

        botMap.put(f, unitsBought);
        soldMap.put(f, unitsSold);

        double avgBuy = Math.abs(Math.round(100d * (tradesMap.get(f).entrySet().stream().filter(e -> e.getValue().getSizeAll() > 0)
                .mapToDouble(e -> e.getValue().getCostBasisAll("")).sum() / unitsBought)) / 100d);

        double avgSell = Math.abs(Math.round(100d * (tradesMap.get(f).entrySet().stream().filter(e -> e.getValue().getSizeAll() < 0)
                .mapToDouble(e -> e.getValue().getCostBasisAll("")).sum() / unitsSold)) / 100d);

        double buyTradePnl = Math.round(100d * (futPriceMap.get(f) - avgBuy) * unitsBought) / 100d;

        double sellTradePnl = Math.round(100d * (futPriceMap.get(f) - avgSell) * unitsSold) / 100d;

        double netTradePnl = buyTradePnl + sellTradePnl;

        double netTotalCommissions = Math.round(100d * ((unitsBought - unitsSold) * 1.505d)) / 100d;

        double mtmPnl = (currentPosMap.get(f) - unitsBought - unitsSold) * (futPriceMap.get(f) - futPrevCloseMap.get(f));

        SwingUtilities.invokeLater(() -> {
            XUTrader.updateLog(" P " + futPriceMap.get(f));
            XUTrader.updateLog(" Close " + futPrevCloseMap.get(f));
            XUTrader.updateLog(" Open " + futOpenMap.get(f));
            XUTrader.updateLog(" Chg " + (Math.round(10000d * (futPriceMap.get(f) / futPrevCloseMap.get(f) - 1)) / 100d) + " %");
            XUTrader.updateLog(" Open Pos " + (currentPosMap.get(f) - unitsBought - unitsSold));
            XUTrader.updateLog(" MTM " + mtmPnl);
            XUTrader.updateLog(" units bot " + unitsBought);
            XUTrader.updateLog(" avg buy " + avgBuy);
            XUTrader.updateLog(" units sold " + unitsSold);
            XUTrader.updateLog(" avg sell " + avgSell);
            XUTrader.updateLog(" buy pnl " + buyTradePnl);
            XUTrader.updateLog(" sell pnl " + sellTradePnl);
            XUTrader.updateLog(" net pnl " + netTradePnl);
            XUTrader.updateLog(" net commission " + netTotalCommissions);
            //XUTrader.updateLog(" net pnl after comm " + (netTradePnl - netTotalCommissions));
            XUTrader.updateLog(" MTM+Trade " + (netTradePnl + mtmPnl));
        });
    }

    public static void main(String[] args) {
        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1000, 1000));

        ApiController ap = new ApiController(new XUConnectionHandler(),
                new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());

        XUTrader xutrader = new XUTrader(ap);
        jf.add(xutrader);
        jf.setLayout(new FlowLayout());
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        jf.setVisible(true);

        CompletableFuture.runAsync(xutrader::connectToTWS).thenRun(() -> {
            CompletableFuture.runAsync(() -> XUTrader.getAPICon().client().reqCurrentTime());
            CompletableFuture.runAsync(xutrader::requestXUData);
        });
    }
}

class XUConnectionHandler implements ApiController.IConnectionHandler {
    @Override
    public void connected() {
        System.out.println("connected in XUconnectionhandler");
        XUTrader.connectionStatus = true;
        XUTrader.connectionLabel.setText(Boolean.toString(XUTrader.connectionStatus));
        XUTrader.apcon.setConnectionStatus(true);
    }

    @Override
    public void disconnected() {
        System.out.println("disconnected in XUConnectionHandler");
        XUTrader.connectionStatus = false;
        XUTrader.connectionLabel.setText(Boolean.toString(XUTrader.connectionStatus));
    }

    @Override
    public void accountList(ArrayList<String> list) {
        System.out.println(" account list is " + list);
    }

    @Override
    public void error(Exception e) {
        System.out.println(" error in XUConnectionHandler");
        e.printStackTrace();
    }

    @Override
    public void message(int id, int errorCode, String errorMsg) {
        System.out.println(" error ID " + id + " error code " + errorCode + " errormsg " + errorMsg);
    }

    @Override
    public void show(String string) {
        System.out.println(" show string " + string);
    }
}

//class XUTradeDefaultHandler implements ApiController.ITradeReportHandler {
//    @Override
//    public void tradeReport(String tradeKey, Contract contract, Execution execution) {
//        //System.out.println( tradeKey );
//        //System.out.println( contract.toString() );
//        //System.out.println(" exec " + execution.side() + " "+ execution.cumQty() + "　" + execution.time() + " " + execution.price()  + " "+ execution.execId());
//
//        System.out.println(" exec " + execution.side() + "　" + execution.time() + " " + execution.cumQty()
//                + " " + execution.price() + " " + execution.orderRef() + " " + execution.orderId() + " " + execution.permId() + " "
//                + execution.shares());
//        int sign = (execution.side().equals("BOT")) ? 1 : -1;
//        //LocalTime lt =
//        System.out.println(LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss")));
//        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));
//
//        if (ldt.getDayOfMonth() == LocalDateTime.now().getDayOfMonth()) {
//            if (XUTrader.tradesMapFront.containsKey(ldt.toLocalTime())) {
//                XUTrader.tradesMapFront.get(ldt.toLocalTime()).addTrade(new IBTrade(execution.price(), sign * execution.cumQty()));
//            } else {
//                XUTrader.tradesMapFront.put(ldt.toLocalTime(), new IBTrade(execution.price(), sign * execution.cumQty()));
//            }
//
//            //System.out.println(" printing all trades");
//            //XUTrader.tradesMapFront.entrySet().stream().forEach(System.out::println);
//            //XUTrader.processTradeMap();
//        }
//    }
//    @Override
//    public void tradeReportEnd() {
//        System.out.println(" trade report end ");
//    }
//    @Override
//    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
////        System.out.println(" commion report "  + commissionReport.m_commission);
////        System.out.println(" realized pnl  "  + commissionReport.m_realizedPNL);
////        System.out.println(" yield  "  + commissionReport.m_yield);
////        System.out.println("  redemption date "  + commissionReport.m_yieldRedemptionDate);
////
////        XUTrader.netTotalCommissions += Math.round(100d*commissionReport.m_commission)/100d;
//        //System.out.println(" net total com so far " + XUTrader.netTotalCommissions);
//    }
//
//}
//class XUPositionHandler implements ApiController.IPositionHandler {
//
//    @Override
//    public void position(String account, Contract contract, double position, double avgCost) {
//        //System.out.println (" proper handling here XXXX ");
//        SwingUtilities.invokeLater(() -> {
//            if (contract.symbol().equals("XINA50")) {
////                XUTrader.updateLog(" account " + account + "\n");
////                XUTrader.updateLog(" contract " + contract.symbol()+ "\n");
////                XUTrader.updateLog(" Exchange " + contract.primaryExch()+ "\n");
////                XUTrader.updateLog(" Local symbol " + contract.localSymbol()+ "\n");
////                XUTrader.updateLog(" Last trade date " + contract.lastTradeDateOrContractMonth()+ "\n");
////                XUTrader.updateLog(" currency " + contract.currency()+ "\n");
////                XUTrader.updateLog(" pos " + position + "\n");
//                XUTrader.setNetPosition((int) position);
////                XUTrader.updateLog(" cost "+ avgCost+ "\n");
////                XUTrader.updateLog("__________________________________________");
//                XUTrader.outputArea.repaint();
//            }
//        });
//    }
//
//    @Override
//    public void positionEnd() {
//        //System.out.println( " position request ends XXXXX ");
//    }
//}

//class XUOrderHandler implements ApiController.IOrderHandler {
//
//    @Override
//    public void orderState(OrderState orderState) {
//        XUTrader.updateLog(orderState.toString());
//    }
//
//    @Override
//    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
//        XUTrader.updateLog(Utility.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));
//
//        if (status.equals(OrderStatus.Filled)) {
//            XUTrader.createDialog(Utility.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));
//        }
//    }
//
//    @Override
//    public void handle(int errorCode, String errorMsg) {
//        XUTrader.updateLog(" handle error code " + errorCode + " message " + errorMsg);
//    }
//}

//class XULiveOrderHandler implements ApiController.ILiveOrderHandler {
//
//    @Override
//    public void openOrder(Contract contract, Order order, OrderState orderState) {
//        XUTrader.updateLog(getStr(contract.toString(), order.toString(), orderState.toString()));
//    }
//
//    @Override
//    public void openOrderEnd() {
//    }
//
//    @Override
//    public void orderStatus(int orderId, OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
//        XUTrader.updateLog(Utility.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));
//
//        if (status.equals(OrderStatus.Filled)) {
//            XUTrader.createDialog(Utility.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));
//        }
//    }
//
//    @Override
//    public void handle(int orderId, int errorCode, String errorMsg) {
//        XUTrader.updateLog(" handle error code " + errorCode + " message " + errorMsg);
//    }
//}

//class XULevel2Handler implements ApiController.IDeepMktDataHandler {
//
//    @Override
//    public void updateMktDepth(int position, String marketMaker, Types.DeepType operation, Types.DeepSide side, double price, int size) {
//        //System.out.println(" updating market depth method");
//        //System.out.println( ChinaStockHelper.getStrCheckNull(" position marketMaker operation side price size ", position, marketMaker, operation, side, price, size));
//
//        SwingUtilities.invokeLater(() -> {
//            if (side.equals(Types.DeepSide.BUY)) {
//                XUTrader.bidLabelList.get(position).setText(Utility.getStrCheckNull(price, "            ", size));
//                XUTrader.bidPriceList.put("bid" + Integer.toString(position + 1), price);
//            } else {
//                XUTrader.askLabelList.get(position).setText(Utility.getStrCheckNull(price, "            ", size));
//                XUTrader.offerPriceList.put("ask" + Integer.toString(position + 1), price);
//            }
//        });
//    }
//}
