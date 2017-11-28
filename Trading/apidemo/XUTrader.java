package apidemo;

import TradeType.IBTrade;
import auxiliary.SimpleBar;
import client.*;
import controller.ApiConnection;
import controller.ApiController;
import controller.ApiController.ITopMktDataHandler;
import graph.GraphBarGen;
import handler.HistoricalHandler;
import handler.LiveHandler;
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

import static utility.Utility.getStr;

public final class XUTrader extends JPanel implements HistoricalHandler, ApiController.IDeepMktDataHandler,
        ApiController.ITradeReportHandler, ApiController.IOrderHandler, ApiController.ILiveOrderHandler
        , ApiController.IPositionHandler, ApiController.IConnectionHandler {

    static ApiController apcon;

    //new ApiController(new XUConnectionHandler(),
    //new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());

    // ApiController apcon = new ApiController(new IConnectionHandler.DefaultConnectionHandler()
    // ,new ApiConnection.ILogger.DefaultLogger(),new ApiConnection.ILogger.DefaultLogger());

    private final Contract frontFut = utility.Utility.getFrontFutContract();
    private final Contract backFut = utility.Utility.getBackFutContract();

    //List<Integer> orderList = new LinkedList<>();
    //AtomicInteger orderInitial = new AtomicInteger(3000001);
    static volatile double currentBidFront;
    static volatile double currentAskFront;
    static volatile double currentPriceFront;

    static volatile double currentBidBack;
    static volatile double currentAskBack;
    static volatile double currentPriceBack;

    private static JTextArea outputArea = new JTextArea(20, 1);
    private AtomicInteger orderIdNo;
    private static List<JLabel> bidLabelList = new ArrayList<>();
    private static List<JLabel> askLabelList = new ArrayList<>();
    private static Map<String, Double> bidPriceList = new HashMap<>();
    private static Map<String, Double> offerPriceList = new HashMap<>();
    private ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);

    public static NavigableMap<LocalTime, IBTrade> tradesMapFront = new ConcurrentSkipListMap<>();
    public static NavigableMap<LocalTime, IBTrade> tradesMapBack = new ConcurrentSkipListMap<>();
    public static Map<String, NavigableMap<LocalTime, IBTrade>> tradesMap = new HashMap<>();

    private GraphBarGen xuGraph = new GraphBarGen();

    static volatile Map<String, NavigableMap<LocalTime, SimpleBar>> futData = new HashMap<>();
    static volatile NavigableMap<LocalTime, SimpleBar> xuFrontData = new ConcurrentSkipListMap<>();
    static volatile NavigableMap<LocalTime, SimpleBar> xuBackData = new ConcurrentSkipListMap<>();

    public static volatile int netPositionFront;
    public static volatile int netBoughtPositionFront;
    public static volatile int netSoldPositionFront;

    public static volatile int netPositionBack;
    public static volatile int netBoughtPositionBack;
    public static volatile int netSoldPositionBack;

    public static volatile Map<String, Integer> currentPosMap = new HashMap<>();


    public static volatile boolean showTrades = false;
    static volatile boolean connectionStatus = false;
    static volatile JLabel connectionLabel = new JLabel();
    private static volatile AtomicInteger connectionID = new AtomicInteger(100);
    private static double todayOpen;

    public XUTrader(AtomicInteger orderIdNo, LayoutManager lm) {
        super(lm);
        this.orderIdNo = orderIdNo;
    }

    public XUTrader(AtomicInteger orderIdNo) {
        this.orderIdNo = orderIdNo;
    }

    public XUTrader getThis() {
        return this;
    }

    public static ApiController getStandAloneApicon() {
        return new ApiController(new XUConnectionHandler() , new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
    }

    XUTrader(ApiController ap) {
//        frontFut.symbol("XINA50");
//        frontFut.exchange("SGX");
//        frontFut.currency("USD");
//        frontFut.lastTradeDateOrContractMonth(TradingConstants.GLOBALA50FRONTEXPIRY);
//        frontFut.secType(Types.SecType.FUT);

        futData.put("SGXA50", new ConcurrentSkipListMap<>());
        futData.put("SGXA50BM", new ConcurrentSkipListMap<>());

        apcon = ap;
        JLabel currTimeLabel = new JLabel(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        currTimeLabel.setFont(currTimeLabel.getFont().deriveFont(30F));

        JButton bidLimitButton = new JButton("Buy Limit");

        bidLimitButton.addActionListener(l -> {
            System.out.println(" buying limit ");
            apcon.placeOrModifyOrder(frontFut, placeBidLimit(currentBidFront), this);
        });

        JButton offerLimitButton = new JButton("Sell Limit");

        offerLimitButton.addActionListener(l -> {
            System.out.println(" selling limit ");
            apcon.placeOrModifyOrder(frontFut, placeOfferLimit(currentAskFront), this);
        });

        JButton buyOfferButton = new JButton(" Buy Now");
        buyOfferButton.addActionListener(l -> {
            System.out.println(" buy offer ");
            apcon.placeOrModifyOrder(frontFut, buyAtOffer(currentAskFront), this);
        });

        JButton sellBidButton = new JButton(" Sell Now");
        sellBidButton.addActionListener(l -> {
            System.out.println(" sell bid ");
            apcon.placeOrModifyOrder(frontFut, sellAtBid(currentBidFront), this);
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
            try {
                getAPICon().reqXUDataArray(new FrontFutReceiver(), new BackFutReceiver());
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

            ses.scheduleAtFixedRate(() -> {
                String time = (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).getSecond() != 0)
                        ? (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()) : (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString() + ":00");
                currTimeLabel.setText(time);
                xuGraph.fillInGraph(xuFrontData);
                xuGraph.refresh();
                apcon.reqPositions(this);
                repaint();
            }, 0, 1, TimeUnit.SECONDS);
        });

        JButton execButton = new JButton("Exec");

        execButton.addActionListener(l -> {
            System.out.println(" getting exec details");
            requestExecHistory();
            //XUTrader.processTradeMap();
            //XUTrader.
        });

        JButton processTradesButton = new JButton("Process");

        processTradesButton.addActionListener(l -> {
            ses.scheduleAtFixedRate(() -> {
                XUTrader.clearLog();
                XUTrader.updateLog("**************************************************************");
                XUTrader.processTradeMap();
            }, 0, 1, TimeUnit.SECONDS);
        });

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
            xuGraph.setNavigableMap(xuFrontData);
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
        JLabel connectionStatusLabel = new JLabel(Boolean.toString(connectionStatus));

        JButton disconnectButton = new JButton("Disconnect");
        disconnectButton.addActionListener(l -> {
            System.out.println(" -------------------disconnect button clicked-----------------------------------");
            apcon.disconnect();
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
        frontFutButton.addActionListener(l->{

        });
        JRadioButton backFutButton = new JRadioButton("Back");
        backFutButton.addActionListener(l->{

        });

        ButtonGroup frontBackGroup = new ButtonGroup();
        frontBackGroup.add(frontFutButton);
        frontBackGroup.add(backFutButton);


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
        controlPanel2.add(execButton);
        controlPanel2.add(processTradesButton);
        controlPanel2.add(connect7496);
        controlPanel2.add(connect4001);
        controlPanel2.add(getData);
        controlPanel2.add(graphButton);
        controlPanel2.add(showGraphButton);
        controlPanel2.add(connectionLabel);
        //controlPanel2.add(connectionStatusLabel);
        controlPanel2.add(disconnectButton);

        controlPanel2.add(frontFutButton);
        controlPanel2.add(backFutButton);


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
                            apcon.placeOrModifyOrder(frontFut, placeBidLimit(bidPrice), getThis());
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
                            apcon.placeOrModifyOrder(frontFut, placeOfferLimit(offerPrice), getThis());
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

    private static void setNetPositionFront(int p) {
        netPositionFront = p;
    }

    private void loadXU() {
        //apcon.reqHistoricalData(frontFut, TOOL_TIP_TEXT_KEY, ERROR, Types.DurationUnit.SECOND, Types.BarSize._1_secs, Types.WhatToShow.TRADES, true, handler);
        System.out.println(" getting XU data ");
        apcon.getSGXA50Historical2(30000, this);
    }

    private boolean checkIfOrderPriceMakeSense(double p) {

        System.out.println(" current ask bid price " + currentAskFront + " " + currentBidFront + " " + currentPriceFront + " ");
        if (p == 0.0) {
            return false;
        } else {
            if (currentAskFront == 0.0 || currentBidFront == 0.0 || currentPriceFront == 0.0) {
                return false;
            } else {
                return (Math.abs(p / currentPriceFront - 1) < 0.02);
            }
        }
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
        //orderIdNo = new AtomicInteger();
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
            System.out.println(" date is " + date);
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

            if (ld.equals(currDate) && ((lt.isAfter(LocalTime.of(8, 59)) && lt.isBefore(LocalTime.of(11, 31)))
                    || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {

                if (lt.equals(LocalTime.of(9, 0))) {
                    todayOpen = open;
                    System.out.println(" today open is " + todayOpen);
                }

                //SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                System.out.println(Utility.getStrCheckNull(dt, open, high, low, close));
                xuFrontData.put(lt, new SimpleBar(open, high, low, close));
            }
        } else {
            System.out.println(getStr(date, open, high, low, close));
        }
    }

    @Override
    public void actionUponFinish(String name) {

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

        System.out.println(" exec " + execution.side() + "　" + execution.time() + " " + execution.cumQty()
                + " " + execution.price() + " " + execution.orderRef() + " " + execution.orderId() + " " + execution.permId() + " "
                + execution.shares());

        int sign = (execution.side().equals("BOT")) ? 1 : -1;
        //LocalTime lt =
        System.out.println(LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss")));
        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));

        if (ldt.getDayOfMonth() == LocalDateTime.now().getDayOfMonth()) {
            if (XUTrader.tradesMapFront.containsKey(ldt.toLocalTime())) {
                XUTrader.tradesMapFront.get(ldt.toLocalTime()).merge(new IBTrade(execution.price(), sign * execution.cumQty()));
            } else {
                XUTrader.tradesMapFront.put(ldt.toLocalTime(), new IBTrade(execution.price(), sign * execution.cumQty()));
            }

            //System.out.println(" printing all trades");
            //XUTrader.tradesMapFront.entrySet().stream().forEach(System.out::println);
            //XUTrader.processTradeMap();
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
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
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
    public void orderStatus(int orderId, OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
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
        currentPosMap.put(ticker, (int)position);
        switch(ticker) {
            case "SGXA50":
                XUTrader.setNetPositionFront((int) position);
        }

        SwingUtilities.invokeLater(() -> {


            if (contract.symbol().equals("XINA50")) {
//                XUTrader.updateLog(" account " + account + "\n");
//                XUTrader.updateLog(" contract " + contract.symbol()+ "\n");
//                XUTrader.updateLog(" Exchange " + contract.primaryExch()+ "\n");
//                XUTrader.updateLog(" Local symbol " + contract.localSymbol()+ "\n");
//                XUTrader.updateLog(" Last trade date " + contract.lastTradeDateOrContractMonth()+ "\n");
//                XUTrader.updateLog(" currency " + contract.currency()+ "\n");
//                XUTrader.updateLog(" pos " + position + "\n");
                XUTrader.setNetPositionFront((int) position);
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




    public static class GeneralReceiver implements LiveHandler {
        @Override
        public void handlePrice(TickType tt, String name, double price, LocalTime t) {
            switch(tt) {
                case BID:
                case ASK:
                case LAST:
                    if (futData.get(name).containsKey(t)) {
                        futData.get(name).get(t).add(price);
                    } else {
                        futData.get(name).put(t, new SimpleBar(price));
                    }
                    break;
                    //futData.get(name).put(t, price);

            }
        }

        @Override
        public void handleVol(String name, double vol, LocalTime t) {

        }
    }


    static class FrontFutReceiver implements ITopMktDataHandler {

        @SuppressWarnings("Duplicates")
        @Override
        public void tickPrice(TickType tickType, double price, int canAutoExecute) {

            LocalTime lt = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
            switch (tickType) {
                case BID:
                    currentBidFront = price;
                    //System.out.println(" current bid " + currentBidFront);
                    break;
                case ASK:
                    currentAskFront = price;
                    //System.out.println(" current ask " + currentAskFront);
                    break;
                case LAST:
                    currentPriceFront = price;
                    //System.out.println(" current price " + currentPriceFront);
                    if (xuFrontData.containsKey(lt)) {
                        xuFrontData.get(lt).add(price);
                    } else {
                        xuFrontData.put(lt, new SimpleBar(price));
                    }
                    break;
            }
        }

        @Override
        public void tickSize(TickType tickType, int size) {
        }

        @Override
        public void tickString(TickType tickType, String value) {
        }

        @Override
        public void tickSnapshotEnd() {
        }

        @Override
        public void marketDataType(Types.MktDataType marketDataType) {
        }
    }

    static class BackFutReceiver implements ITopMktDataHandler {

        @SuppressWarnings("Duplicates")
        @Override
        public void tickPrice(TickType tickType, double price, int canAutoExecute) {

            LocalTime lt = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
            switch (tickType) {
                case BID:
                    currentBidBack = price;
                    //System.out.println(" current bid " + currentBidFront);
                    break;
                case ASK:
                    currentAskBack = price;
                    //System.out.println(" current ask " + currentAskFront);
                    break;
                case LAST:
                    currentPriceBack = price;
                    //System.out.println(" current price " + currentPriceFront);
                    if (xuBackData.containsKey(lt)) {
                        xuBackData.get(lt).add(price);
                    } else {
                        xuBackData.put(lt, new SimpleBar(price));
                    }
                    break;
            }
        }

        @Override
        public void tickSize(TickType tickType, int size) {
        }

        @Override
        public void tickString(TickType tickType, String value) {
        }

        @Override
        public void tickSnapshotEnd() {
        }

        @Override
        public void marketDataType(Types.MktDataType marketDataType) {
        }
    }


    private void requestLevel2Data() {
        //apcon.reqDeepMktData(frontFut, 10, new XULevel2Handler());
        apcon.reqDeepMktData(frontFut, 10, this);
    }

    private void requestExecHistory() {
        System.out.println(" requesting exec history ");
        XUTrader.tradesMapFront = new ConcurrentSkipListMap<>();
        apcon.reqExecutions(new ExecutionFilter(), this);
    }

    private void requestXUData() {
        try {
            getAPICon().reqXUDataArray(new FrontFutReceiver(), new BackFutReceiver());
        } catch (InterruptedException ex) {
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

    private static void processTradeMap() {

        //average buy cost
        //System.out.println(" processing -------------------------------------------------");
        int unitsBought = XUTrader.tradesMapFront.entrySet().stream().filter(e -> e.getValue().getSize() > 0).mapToInt(e -> e.getValue().getSize()).sum();
        int unitsSold = XUTrader.tradesMapFront.entrySet().stream().filter(e -> e.getValue().getSize() < 0).mapToInt(e -> e.getValue().getSize()).sum();

        netBoughtPositionFront = unitsBought;
        netSoldPositionFront = unitsSold;

        double avgBuy = Math.round(100d * (XUTrader.tradesMapFront.entrySet().stream().filter(e -> e.getValue().getSize() > 0)
                .mapToDouble(e -> e.getValue().getCost()).sum() / unitsBought)) / 100d;

        double avgSell = Math.round(100d * (XUTrader.tradesMapFront.entrySet().stream().filter(e -> e.getValue().getSize() < 0)
                .mapToDouble(e -> e.getValue().getCost()).sum() / unitsSold)) / 100d;

        double buyTradePnl = Math.round(100d * (XUTrader.currentPriceFront - avgBuy) * unitsBought) / 100d;
        double sellTradePnl = Math.round(100d * (XUTrader.currentPriceFront - avgSell) * unitsSold) / 100d;
        double netTradePnl = buyTradePnl + sellTradePnl;
        double netTotalCommissions = (unitsBought - unitsSold) * 1.505d;
        double mtmPnl = (netPositionFront - unitsBought - unitsSold) * (XUTrader.currentPriceFront - todayOpen);
        double previousCloseOverride = 0;

        XUTrader.updateLog(" P " + XUTrader.currentPriceFront);
        XUTrader.updateLog("Open " + todayOpen);
        XUTrader.updateLog(" Chg " + (Math.round(10000d * (XUTrader.currentPriceFront / todayOpen - 1)) / 100d) + " %");
        XUTrader.updateLog("Open Pos " + (netPositionFront - unitsBought - unitsSold));
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
    }

    private static void processTradeMapGen(Predicate<? super Map.Entry<String, ?>> pred) {


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
