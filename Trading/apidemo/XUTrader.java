package apidemo;

import TradeType.FutureTrade;
import TradeType.TradeBlock;
import auxiliary.SimpleBar;
import client.*;
import controller.ApiConnection;
import controller.ApiController;
import graph.DisplayGranularity;
import graph.GraphBarGen;
import handler.HistoricalHandler;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static utility.Utility.*;

//import controller.ApiController.ITopMktDataHandler;

public final class XUTrader extends JPanel implements HistoricalHandler, ApiController.IDeepMktDataHandler,
        ApiController.ITradeReportHandler, ApiController.IOrderHandler, ApiController.ILiveOrderHandler
        , ApiController.IPositionHandler, ApiController.IConnectionHandler {

    static ApiController apcon;

    //new ApiController(new XUConnectionHandler(),
    //new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());

    // ApiController apcon = new ApiController(new IConnectionHandler.DefaultConnectionHandler()
    // ,new ApiConnection.ILogger.DefaultLogger(),new ApiConnection.ILogger.DefaultLogger());

    private final static Contract frontFut = utility.Utility.getFrontFutContract();
    private final static Contract backFut = utility.Utility.getBackFutContract();

    @SuppressWarnings("unused")
    private static Predicate<? super Map.Entry<FutType, ?>> graphPred = e -> true;
    private static volatile Contract activeFuture = frontFut;
    public static volatile DisplayGranularity gran = DisplayGranularity._1MDATA;
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

    @SuppressWarnings("unused")
    static volatile EnumMap<FutType, Double> previousCloseMap = new EnumMap<>(FutType.class);

    private static JTextArea outputArea = new JTextArea(20, 1);
    //private AtomicInteger orderIdNo;
    private static List<JLabel> bidLabelList = new ArrayList<>();
    private static List<JLabel> askLabelList = new ArrayList<>();
    private static Map<String, Double> bidPriceList = new HashMap<>();
    private static Map<String, Double> offerPriceList = new HashMap<>();
    private ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);

    //public static NavigableMap<LocalTime, IBTrade> tradesMapFront = new ConcurrentSkipListMap<>();
    //public static NavigableMap<LocalTime, IBTrade> tradesMapBack = new ConcurrentSkipListMap<>();
    public static EnumMap<FutType, NavigableMap<LocalTime, TradeBlock>> tradesMap = new EnumMap<>(FutType.class);

    private GraphBarGen xuGraph = new GraphBarGen();

    public static volatile EnumMap<FutType, NavigableMap<LocalTime, SimpleBar>> futData = new EnumMap<>(FutType.class);
    //static volatile NavigableMap<LocalTime, SimpleBar> xuFrontData = new ConcurrentSkipListMap<>();
    //static volatile NavigableMap<LocalTime, SimpleBar> xuBackData = new ConcurrentSkipListMap<>();

//    public static volatile int netPositionFront;
//    public static volatile int netBoughtPositionFront;
//    public static volatile int netSoldPositionFront;

//    public static volatile int netPositionBack;
//    public static volatile int netBoughtPositionBack;
//    public static volatile int netSoldPositionBack;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    public static volatile EnumMap<FutType, Integer> currentPosMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> botMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> soldMap = new EnumMap<>(FutType.class);


    public static volatile boolean showTrades = false;
    static volatile boolean connectionStatus = false;
    static volatile JLabel connectionLabel = new JLabel();
    private static volatile AtomicInteger connectionID = new AtomicInteger(100);
    //private static double todayOpen;
    private static EnumMap<FutType, Double> futOpenMap = new EnumMap<>(FutType.class);

//    public XUTrader(AtomicInteger orderIdNo, LayoutManager lm) {
//        super(lm);
//        this.orderIdNo = orderIdNo;
//    }

//    public XUTrader(AtomicInteger orderIdNo) {
//        this.orderIdNo = orderIdNo;
//    }

    public XUTrader getThis() {
        return this;
    }

//    public static ApiController getStandAloneApicon() {
//        return new ApiController(new XUConnectionHandler(), new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
//    }

    XUTrader(ApiController ap) {
//        frontFut.symbol("XINA50");
//        frontFut.exchange("SGX");
//        frontFut.currency("USD");
//        frontFut.lastTradeDateOrContractMonth(TradingConstants.GLOBALA50FRONTEXPIRY);
//        frontFut.secType(Types.SecType.FUT);

        for (FutType f : FutType.values()) {
            futData.put(f, new ConcurrentSkipListMap<>());
            tradesMap.put(f, new ConcurrentSkipListMap<>());
            futOpenMap.put(f, 0.0);
        }

//        futData.put("SGXA50", new ConcurrentSkipListMap<>());
//        futData.put("SGXA50BM", new ConcurrentSkipListMap<>());
//        tradesMap.put("SGXA50", new ConcurrentSkipListMap<>());
//        tradesMap.put("SGXA50BM", new ConcurrentSkipListMap<>());
//        futOpenMap.put("SGXA50", 0.0);
//        futOpenMap.put("SGXA50BM", 0.0);

        apcon = ap;
        JLabel currTimeLabel = new JLabel(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        currTimeLabel.setFont(currTimeLabel.getFont().deriveFont(30F));

        JButton bidLimitButton = new JButton("Buy Limit");

        bidLimitButton.addActionListener(l -> {
            System.out.println(" buying limit ");
            apcon.placeOrModifyOrder(activeFuture, placeBidLimit(bidMap.get(ibContractToFutType(activeFuture))), this);
        });

        JButton offerLimitButton = new JButton("Sell Limit");

        offerLimitButton.addActionListener(l -> {
            System.out.println(" selling limit ");
            apcon.placeOrModifyOrder(activeFuture, placeOfferLimit(askMap.get(ibContractToFutType(activeFuture))), this);
        });

        JButton buyOfferButton = new JButton(" Buy Now");
        buyOfferButton.addActionListener(l -> {
            System.out.println(" buy offer ");
            apcon.placeOrModifyOrder(activeFuture, buyAtOffer(askMap.get(ibContractToFutType(activeFuture))), this);
        });

        JButton sellBidButton = new JButton(" Sell Now");
        sellBidButton.addActionListener(l -> {
            System.out.println(" sell bid ");
            apcon.placeOrModifyOrder(activeFuture, sellAtBid(bidMap.get(ibContractToFutType(activeFuture))), this);
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
//            try {
//                getAPICon().reqXUDataArray(new FrontFutReceiver(), new BackFutReceiver());
//            } catch (InterruptedException ex) {
//                ex.printStackTrace();
//            }

            String time = (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).getSecond() != 0)
                    ? (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()) : (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString() + ":00");
            currTimeLabel.setText(time);
            //xuGraph.fillInGraph(xuFrontData);
            xuGraph.fillInGraph(futData.get(ibContractToFutType(activeFuture)));
            xuGraph.setName(ibContractToSymbol(activeFuture));
            xuGraph.setFut(ibContractToFutType(activeFuture));

            xuGraph.refresh();
            apcon.reqPositions(getThis());
            repaint();

        });

        JButton computeButton = new JButton("Compute");
        computeButton.addActionListener(l -> {
            try {
                //new FrontFutReceiver(), new BackFutReceiver()
                getAPICon().reqXUDataArray();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            if (ses.isShutdown()) {
                ses = Executors.newScheduledThreadPool(10);
            }
            ses.scheduleAtFixedRate(() -> {
                String time = (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).getSecond() != 0)
                        ? (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()) : (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString() + ":00");
                currTimeLabel.setText(time);
                //xuGraph.fillInGraph(xuFrontData);
                xuGraph.fillInGraph(futData.get(ibContractToFutType(activeFuture)));
                xuGraph.setName(ibContractToSymbol(activeFuture));
                xuGraph.setFut(ibContractToFutType(activeFuture));
                xuGraph.refresh();
                apcon.reqPositions(getThis());
                repaint();
            }, 0, 1, TimeUnit.SECONDS);
        });

        JButton stopComputeButton = new JButton("Stop Compute");
        stopComputeButton.addActionListener(l -> {
            ses.shutdown();
            System.out.println(" executor status is " + ses.isShutdown());
        });


        JButton execButton = new JButton("Exec");

        execButton.addActionListener(l -> {
            System.out.println(" getting exec details");
            requestExecHistory();
            //XUTrader.processTradeMapActive();
            //XUTrader.
        });

        JButton processTradesButton = new JButton("Process");

        processTradesButton.addActionListener(l -> ses.scheduleAtFixedRate(() -> {
            SwingUtilities.invokeLater(() -> {
                XUTrader.clearLog();
                XUTrader.updateLog("**************************************************************");
            });

            XUTrader.processTradeMapActive();

        }, 0, 10, TimeUnit.SECONDS));

        JButton connect7496 = new JButton("Connect 7496");

        connect7496.addActionListener(l -> {
            System.out.println(" trying to connect 7496");
            //apcon.disconnect();

            try {
                apcon.connect("127.0.0.1", 7496, connectionID.incrementAndGet(), "");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            //apcon.client().reqIds(-1);
        });

        JButton connect4001 = new JButton("Connect 4001");

        connect4001.addActionListener(l -> {
            System.out.println(" trying to connect 4001");
            try {
                apcon.connect("127.0.0.1", 4001, connectionID.incrementAndGet(), "");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            //apcon.client().reqIds(-1);

        });

        JButton getData = new JButton("Data");
        getData.addActionListener(l -> {
            System.out.println(" getting data ");
            loadXU();
        });

        JButton graphButton = new JButton("graph");
        graphButton.addActionListener(l -> {
            System.out.println(" graphing ");
            xuGraph.setNavigableMap(futData.get(ibContractToFutType(activeFuture)));
            xuGraph.refresh();
            repaint();
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

        //JLabel connectionStatusLabel = new JLabel(Boolean.toString(connectionStatus));

//        JButton disconnectButton = new JButton("Disconnect");
//        disconnectButton.addActionListener(l -> {
//            System.out.println(" -------------------disconnect button clicked-----------------------------------");
//            apcon.disconnect();
//        });

        JButton cancelAllOrdersButton = new JButton("Cancel Orders");
        cancelAllOrdersButton.addActionListener(l -> {
            apcon.cancelAllOrders();
        });


        JScrollPane chartScroll = new JScrollPane(xuGraph) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1700;
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


        // setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
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
        controlPanel2.add(stopComputeButton);
        controlPanel2.add(execButton);
        controlPanel2.add(processTradesButton);
        controlPanel2.add(connect7496);
        controlPanel2.add(connect4001);
        controlPanel2.add(getData);
        controlPanel2.add(graphButton);
        controlPanel2.add(showGraphButton);
        controlPanel2.add(connectionLabel);
        //controlPanel2.add(connectionStatusLabel);
//        controlPanel2.add(disconnectButton);
        controlPanel2.add(cancelAllOrdersButton);

        controlPanel2.add(frontFutButton);
        controlPanel2.add(backFutButton);
        controlPanel2.add(_1mButton);
        controlPanel2.add(_5mButton);

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
                        double bidPrice = bidPriceList.get(l.getName());
                        System.out.println(" bid price " + bidPrice + " check if order price makes sense " + checkIfOrderPriceMakeSense(bidPrice));
                        if (checkIfOrderPriceMakeSense(bidPrice) && marketOpen(LocalTime.now())) {
                            apcon.placeOrModifyOrder(activeFuture, placeBidLimit(bidPrice), getThis());
                        } else {
                            throw new IllegalArgumentException("fuck that price out of bound");
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
                            apcon.placeOrModifyOrder(activeFuture, placeOfferLimit(offerPrice), getThis());
                        } else {
                            throw new IllegalArgumentException("fuck that price out of bound");
                        }
                    }
                }
            });
        });

        JPanel deepPanel = new JPanel();
        //deepPanel.setPreferredSize(new Dimension(100,100));
        deepPanel.setLayout(new GridLayout(5, 2));
        deepPanel.add(bid1);
        deepPanel.add(ask1);
        deepPanel.add(bid2);
        deepPanel.add(ask2);
        deepPanel.add(bid3);
        deepPanel.add(ask3);
        deepPanel.add(bid4);
        deepPanel.add(ask4);
        deepPanel.add(bid5);
        deepPanel.add(ask5);

        JScrollPane outputPanel = new JScrollPane(outputArea);

        JPanel graphPanel = new JPanel();
        graphPanel.add(chartScroll);

        controlPanel1.setLayout(new FlowLayout());
//        add(controlPanel,BorderLayout.NORTH);
//        add(deepPanel, BorderLayout.CENTER);
//        add(jp, BorderLayout.EAST);
//        add(graphPanel,BorderLayout.SOUTH);
        //add(controlPanel);


        add(controlPanel1);
        add(controlPanel2);
        add(deepPanel);
        add(outputPanel);
        add(graphPanel);


    }

//    private static void setNetPositionFront(int p) {
//        netPositionFront = p;
//    }

    private void loadXU() {
        //apcon.reqHistoricalData(frontFut, TOOL_TIP_TEXT_KEY, ERROR, Types.DurationUnit.SECOND, Types.BarSize._1_secs, Types.WhatToShow.TRADES, true, handler);
        System.out.println(" getting XU data ");
        apcon.getSGXA50Historical2(30000, this);
    }

    private boolean checkIfOrderPriceMakeSense(double p) {

        //String activeTicker = ibContractToSymbol(activeFuture);
        FutType f = ibContractToFutType(activeFuture);

        System.out.println(" current ask bid price " + askMap.get(f) + " " + bidMap.get(f) + " " + futPriceMap.get(f) + " ");
        return !(p == 0.0) && !(bidMap.getOrDefault(f, 0.0) == 0.0)
                && !(askMap.getOrDefault(f, 0.0) == 0.0) && !(futPriceMap.getOrDefault(f, 0.0) == 0.0)
                && (Math.abs(p / futPriceMap.getOrDefault(f, 0.0) - 1) < 0.02);
    }

    private boolean marketOpen(LocalTime t) {
        return t.isAfter(LocalTime.of(8, 59));
    }

    //    @Override
//    public void handleHist(String name, LocalDate ld, double open, double high, double low, double close) {
//        LocalDate currDate = LocalDate.now();
//        if (ld.equals(currDate) && ((lt.isAfter(LocalTime.of(8, 59)) && lt.isBefore(LocalTime.of(11, 31)))
//                || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
//
//            if (lt.equals(LocalTime.of(9, 0))) {
//                todayOpen = open;
//                System.out.println(" today open is " + todayOpen);
//            }
//
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
//            System.out.println(ChinaStockHelper.getStrCheckNull(dt, open, high, low, close));
//            xuFrontData.put(lt, new SimpleBar(open, high, low, close));
//        }
//
//    }
//    @Override
//    public void actionUponFinish(String name) {
//    }
//    public static void handleSGX50HistData(String date, double open, double high, double low, double close, int volume) {
//
//        LocalDate currDate = LocalDate.now();
//
//        if (!date.startsWith("finished")) {
//            System.out.println(" date is " + date);
//            Date dt = new Date(Long.parseLong(date) * 1000);
//            Calendar cal = Calendar.getInstance();
//            cal.setTime(dt);
//            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
//            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
//
//            if (ld.equals(currDate) && ((lt.isAfter(LocalTime.of(8, 59)) && lt.isBefore(LocalTime.of(11, 31)))
//                    || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
//
//                if (lt.equals(LocalTime.of(9, 0))) {
//                    todayOpen = open;
//                    System.out.println(" today open is " + todayOpen);
//                }
//
//                //SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
//                System.out.println(Utility.getStrCheckNull(dt, open, high, low, close));
//                xuFrontData.put(lt, new SimpleBar(open, high, low, close));
//            }
//        } else {
//            System.out.println(getStr(date, open, high, low, close));
//        }
//    }

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

    private Order buyAtOffer(double p) {
        System.out.println(" buy at offer " + p);
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(1);
        o.outsideRth(true);
        return o;
    }

    private Order sellAtBid(double p) {
        System.out.println(" sell at bid " + p);

        Order o = new Order();
        o.action(Types.Action.SELL);
        //o.auxPrice(0.0);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(1);
        o.outsideRth(true);
        //o.orderId(orderIdNo.incrementAndGet());
        return o;
    }

    private Order placeBidLimit(double p) {
        System.out.println(" place bid limit " + p);
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(1);
        o.outsideRth(true);
        o.tif(Types.TimeInForce.GTC);
        return o;
    }

    private Order placeOfferLimit(double p) {
        System.out.println(" place offer limit " + p);
        Order o = new Order();
        o.action(Types.Action.SELL);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(1);
        o.tif(Types.TimeInForce.GTC);
        o.outsideRth(true);
        return o;
    }

    private static void updateLog(String s) {
        outputArea.append(s);
        outputArea.append("\n");
    }

    private static void clearLog() {
        outputArea.setText("");
    }

//    int getCurrentPosition() {
//        return 0;
//    }
//
//    void repaintThis() {
//        repaint();
//    }

    @Override
    public void handleHist(String name, String date, double open, double high, double low, double close) {

        LocalDate currDate = LocalDate.now();


        if (!date.startsWith("finished")) {

            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            //System.out.println(" handle hist in xu trader ld lt  " + LocalDateTime.of(ld, lt));
            //System.out.println(getStr("name date open high low close ", name, date, open, high, low, close));

            if (ld.equals(currDate) && ((lt.isAfter(LocalTime.of(8, 59)) && lt.isBefore(LocalTime.of(11, 31)))
                    || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(18, 1))))) {

                if (lt.equals(LocalTime.of(9, 0))) {
                    futOpenMap.put(FutType.get(name), open);
                    //todayOpen = open;
                    System.out.println(" today open is for " + name + " " + open);
                }

                //SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                //System.out.println(Utility.getStrCheckNull(dt, open, high, low, close));
                futData.get(FutType.get(name)).put(lt, new SimpleBar(open, high, low, close));
                //xuFrontData.put(lt, new SimpleBar(open, high, low, close));

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
    public void updateMktDepth(int position, String marketMaker, Types.DeepType operation, Types.DeepSide side, double price, int size) {
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
        //System.out.println(LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss")));

        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));

        if (ldt.getDayOfMonth() == LocalDateTime.now().getDayOfMonth()) {
            if (XUTrader.tradesMap.get(f).containsKey(ldt.toLocalTime())) {
                XUTrader.tradesMap.get(f).get(ldt.toLocalTime())
                        .merge(new FutureTrade(execution.price(), sign * execution.cumQty()));
            } else {
                XUTrader.tradesMap.get(f).put(ldt.toLocalTime(),
                        new TradeBlock(new FutureTrade(execution.price(), sign * execution.cumQty())));
            }
//            if (XUTrader.tradesMapFront.containsKey(ldt.toLocalTime())) {
//                XUTrader.tradesMapFront.get(ldt.toLocalTime()).merge(new IBTrade(execution.price(), sign * execution.cumQty()));
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
        System.out.println(" trade report end ");
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
//        System.out.println(" commion report "  + commissionReport.m_commission);
//        System.out.println(" realized pnl  "  + commissionReport.m_realizedPNL);
//        System.out.println(" yield  "  + commissionReport.m_yield);
//        System.out.println("  redemption date "  + commissionReport.m_yieldRedemptionDate);
//
//        XUTrader.netTotalCommissions += Math.round(100d*commissionReport.m_commission)/100d;
        //System.out.println(" net total com so far " + XUTrader.netTotalCommissions);
    }


    //ApiController.IOrderHandler
    @Override
    public void orderState(OrderState orderState) {
        XUTrader.updateLog(orderState.toString());
    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId,
                            double lastFillPrice, int clientId, String whyHeld) {

        XUTrader.updateLog(Utility.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));

        if (status.equals(OrderStatus.Filled)) {
            XUTrader.createDialog(Utility.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));
        }
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        XUTrader.updateLog(" handle error code " + errorCode + " message " + errorMsg);
    }

    //live order handler
    @Override
    public void openOrder(Contract contract, Order order, OrderState orderState) {
        XUTrader.updateLog(getStr(contract.toString(), order.toString(), orderState.toString()));
    }

    @Override
    public void openOrderEnd() {
    }

    @Override
    public void orderStatus(int orderId, OrderStatus status, int filled, int remaining,
                            double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {

        XUTrader.updateLog(Utility.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));

        if (status.equals(OrderStatus.Filled)) {
            XUTrader.createDialog(Utility.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));
        }
    }

    @Override
    public void handle(int orderId, int errorCode, String errorMsg) {
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
//        switch (ticker) {
//            case "SGXA50":
//                currentPosMap.put(ticker, (int) position);
//                //XUTrader.setNetPositionFront((int) position);
//        }

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
        //XUTrader.tradesMapFront = new ConcurrentSkipListMap<>();
        tradesMap.replaceAll((k, v) -> new ConcurrentSkipListMap<>());
        apcon.reqExecutions(new ExecutionFilter(), this);
    }

    private void requestXUData() {
        try {
            //new FrontFutReceiver(), new BackFutReceiver()
            getAPICon().reqXUDataArray();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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

        //average buy cost
        //System.out.println(" processing -------------------------------------------------");
        //String ticker = ibContractToSymbol(activeFuture);
        FutType f = ibContractToFutType(activeFuture);

        int unitsBought = tradesMap.get(f).entrySet().stream().filter(e -> e.getValue().getSizeAll() > 0)
                .mapToInt(e -> e.getValue().getSizeAll()).sum();
        int unitsSold = tradesMap.get(f).entrySet().stream().filter(e -> e.getValue().getSizeAll() < 0)
                .mapToInt(e -> e.getValue().getSizeAll()).sum();

        botMap.put(f, unitsBought);
        soldMap.put(f, unitsSold);

        //pos
        double avgBuy = Math.abs(Math.round(100d * (tradesMap.get(f).entrySet().stream().filter(e -> e.getValue().getSizeAll() > 0)
                .mapToDouble(e -> e.getValue().getCostBasisAll("")).sum() / unitsBought)) / 100d);

        //pos
        double avgSell = Math.abs(Math.round(100d * (tradesMap.get(f).entrySet().stream().filter(e -> e.getValue().getSizeAll() < 0)
                .mapToDouble(e -> e.getValue().getCostBasisAll("")).sum() / unitsSold)) / 100d);

        double buyTradePnl = Math.round(100d * (futPriceMap.get(f) - avgBuy) * unitsBought) / 100d;
        double sellTradePnl = Math.round(100d * (futPriceMap.get(f) - avgSell) * unitsSold) / 100d;

        double netTradePnl = buyTradePnl + sellTradePnl;
        double netTotalCommissions = (unitsBought - unitsSold) * 1.505d;
        double mtmPnl = (currentPosMap.get(f) - unitsBought - unitsSold) * (futPriceMap.get(f) - futOpenMap.get(f));
        //double previousCloseOverride = 0;

        SwingUtilities.invokeLater(()-> {
            XUTrader.updateLog(" P " + futPriceMap.get(f));
            XUTrader.updateLog("Open " + futOpenMap.get(f));
            XUTrader.updateLog(" Chg " + (Math.round(10000d * (futPriceMap.get(f) / futOpenMap.get(f) - 1)) / 100d) + " %");
            XUTrader.updateLog("Open Pos " + (currentPosMap.get(f) - unitsBought - unitsSold));
            XUTrader.updateLog("MTM " + mtmPnl);
            XUTrader.updateLog(" units bot " + unitsBought);
            XUTrader.updateLog(" avg buy " + avgBuy);
            XUTrader.updateLog(" units sold " + unitsSold);
            XUTrader.updateLog(" avg sell " + avgSell);
            XUTrader.updateLog(" buy pnl " + buyTradePnl);
            XUTrader.updateLog(" sell pnl " + sellTradePnl);
            XUTrader.updateLog(" net pnl " + netTradePnl);
            XUTrader.updateLog(" net commision " + netTotalCommissions);
            XUTrader.updateLog(" net pnl after comm " + (netTradePnl - netTotalCommissions));
            XUTrader.updateLog(" MTM+Trade " + (netTradePnl - netTotalCommissions + mtmPnl));
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
//
//    @Override
//    public void tradeReport(String tradeKey, Contract contract, Execution execution) {
//        //System.out.println( tradeKey );
//        //System.out.println( contract.toString() );
//        //System.out.println(" exec " + execution.side() + " "+ execution.cumQty() + "　" + execution.time() + " " + execution.price()  + " "+ execution.execId());
//
//        System.out.println(" exec " + execution.side() + "　" + execution.time() + " " + execution.cumQty()
//                + " " + execution.price() + " " + execution.orderRef() + " " + execution.orderId() + " " + execution.permId() + " "
//                + execution.shares());
//
//        int sign = (execution.side().equals("BOT")) ? 1 : -1;
//        //LocalTime lt =
//        System.out.println(LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss")));
//        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));
//
//        if (ldt.getDayOfMonth() == LocalDateTime.now().getDayOfMonth()) {
//            if (XUTrader.tradesMapFront.containsKey(ldt.toLocalTime())) {
//                XUTrader.tradesMapFront.get(ldt.toLocalTime()).merge(new IBTrade(execution.price(), sign * execution.cumQty()));
//            } else {
//                XUTrader.tradesMapFront.put(ldt.toLocalTime(), new IBTrade(execution.price(), sign * execution.cumQty()));
//            }
//
//            //System.out.println(" printing all trades");
//            //XUTrader.tradesMapFront.entrySet().stream().forEach(System.out::println);
//            //XUTrader.processTradeMap();
//        }
//    }
//
//    @Override
//    public void tradeReportEnd() {
//        System.out.println(" trade report end ");
//    }
//
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
