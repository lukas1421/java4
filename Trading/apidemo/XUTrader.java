package apidemo;

import static apidemo.ChinaStockHelper.getStr;

import TradeType.IBTrade;
import client.CommissionReport;
import client.Contract;
import client.Execution;
import client.ExecutionFilter;
import client.Order;
import client.OrderState;
import client.OrderStatus;
import client.OrderType;
import client.TickType;
import client.Types;
import controller.ApiConnection;
import controller.ApiController;
import controller.ApiController.ITopMktDataHandler;
import graph.GraphBarGen;
import handler.HistoricalHandler;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public final class XUTrader extends JPanel implements HistoricalHandler {

    static ApiController apcon = new ApiController(new XUConnectionHandler(),
            new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());

    // ApiController apcon = new ApiController(new IConnectionHandler.DefaultConnectionHandler(),new ApiConnection.ILogger.DefaultLogger(),new ApiConnection.ILogger.DefaultLogger());
    public final Contract ct = new Contract();
    List<Integer> orderList = new LinkedList<>();
    //AtomicInteger orderInitial = new AtomicInteger(3000001);
    static volatile double currentBid;
    static volatile double currentAsk;
    static volatile double currentPrice;
    static JTextArea outputArea = new JTextArea(20, 1);
    AtomicInteger orderIdNo;
    static List<JLabel> bidLabelList = new ArrayList<>();
    static List<JLabel> askLabelList = new ArrayList<>();
    static Map<String, Double> bidPriceList = new HashMap<>();
    static Map<String, Double> offerPriceList = new HashMap<>();
    ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);

    public static Map<LocalTime, IBTrade> tradesMap = new ConcurrentSkipListMap<>();

    static volatile double netTotalCommissions = 0.0;

    GraphBarGen xuGraph = new GraphBarGen();

    public static NavigableMap<LocalTime, SimpleBar> xuData = new ConcurrentSkipListMap<>();

    public static volatile int netPosition;
    public static volatile int netBoughtPosition;
    public static volatile int netSoldPosition;
    public static volatile boolean showTrades = false;
    static volatile boolean connectionStatus = false;
    static volatile JLabel connectionLabel = new JLabel();
    static volatile AtomicInteger connectionID = new AtomicInteger(100);
    static double todayOpen;
    static double previousCloseOverride;

    public XUTrader(AtomicInteger orderIdNo, LayoutManager lm) {
        super(lm);
        this.orderIdNo = orderIdNo;
    }

    public XUTrader(AtomicInteger orderIdNo) {
        this.orderIdNo = orderIdNo;
    }

    public XUTrader() {
        ct.symbol("XINA50");
        ct.exchange("SGX");
        ct.currency("USD");
        ct.lastTradeDateOrContractMonth(apidemo.ChinaMain.GLOBALA50EXPIRY);
        ct.secType(Types.SecType.FUT);

        JLabel currTimeLabel = new JLabel(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        currTimeLabel.setFont(currTimeLabel.getFont().deriveFont(30F));

        JButton bidLimitButton = new JButton("Buy Limit");

        bidLimitButton.addActionListener(l -> {
            System.out.println(" buying limit ");
            apcon.placeOrModifyOrder(ct, placeBidLimit(currentBid), new XUOrderHandler());
        });

        JButton offerLimitButton = new JButton("Sell Limit");

        offerLimitButton.addActionListener(l -> {
            System.out.println(" selling limit ");
            apcon.placeOrModifyOrder(ct, placeOfferLimit(currentAsk), new XUOrderHandler());
        });

        JButton buyOfferButton = new JButton(" Buy Now");
        buyOfferButton.addActionListener(l -> {
            System.out.println(" buy offer ");
            apcon.placeOrModifyOrder(ct, buyAtOffer(currentAsk), new XUOrderHandler());
        });

        JButton sellBidButton = new JButton(" Sell Now");
        sellBidButton.addActionListener(l -> {
            System.out.println(" sell bid ");
            apcon.placeOrModifyOrder(ct, sellAtBid(currentBid), new XUOrderHandler());
        });

        JButton getPositionButton = new JButton(" get pos ");
        getPositionButton.addActionListener(l -> {
            System.out.println(" getting pos ");
            apcon.reqPositions(new XUPositionHandler());
        });

        JButton level2Button = new JButton("level2");
        level2Button.addActionListener(l -> {
            System.out.println(" getting level 2 button pressed");
            requestLevel2Data();
        });

        JButton refreshButton = new JButton("Refresh");

        refreshButton.addActionListener(l -> {
            ses.scheduleAtFixedRate(() -> {
                String time = (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).getSecond() != 0)
                        ? (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()) : (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString() + ":00");
                currTimeLabel.setText(time);
                xuGraph.fillInGraph(xuData);
                xuGraph.refresh();
                apcon.reqPositions(new XUPositionHandler());
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
            xuGraph.setNavigableMap(xuData);
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
                        if (checkIfOrderPriceMakeSense(bidPrice) && marketOpen(LocalTime.now())) {
                            apcon.placeOrModifyOrder(ct, placeBidLimit(bidPrice), new XUOrderHandler());
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
                            apcon.placeOrModifyOrder(ct, placeOfferLimit(offerPrice), new XUOrderHandler());
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

    static void setNetPosition(int p) {
        netPosition = p;
    }

    void loadXU() {
        //apcon.reqHistoricalData(ct, TOOL_TIP_TEXT_KEY, ERROR, Types.DurationUnit.SECOND, Types.BarSize._1_secs, Types.WhatToShow.TRADES, true, handler);
        System.out.println(" getting XU data ");
        apcon.getSGXA50Historical2(30000, this);
    }

    boolean checkIfOrderPriceMakeSense(double p) {
        if (p == 0.0) {
            return false;
        } else {
            if (currentAsk == 0.0 || currentBid == 0.0 || currentPrice == 0.0) {
                return false;
            } else {
                return (Math.abs(p / currentPrice - 1) < 0.02);
            }
        }
    }

    boolean marketOpen(LocalTime t) {
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
//            xuData.put(lt, new SimpleBar(open, high, low, close));
//        }
//
//    }
//    @Override
//    public void actionUponFinish(String name) {
//    }
    public static void handleSGX50HistData(String date, double open, double high, double low, double close, int volume) {

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
                System.out.println(ChinaStockHelper.getStrCheckNull(dt, open, high, low, close));
                xuData.put(lt, new SimpleBar(open, high, low, close));
            }
        } else {
            System.out.println(getStr(date, open, high, low, close));
        }
    }

    void connectToTWS(int port) {
        System.out.println(" trying to connect");
        try {
            apcon.connect("127.0.0.1", port, 101, "");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        apcon.client().reqIds(-1);
        //orderIdNo = new AtomicInteger();
    }

    static ApiController getAPICon() {
        return apcon;
    }

    Order buyAtOffer(double p) {
        System.out.println(" buy at offer " + p);
        Order o = new Order();
        o.action(Types.Action.BUY);
        o.lmtPrice(p);
        o.orderType(OrderType.LMT);
        o.totalQuantity(1);
        o.outsideRth(true);
        return o;
    }

    Order sellAtBid(double p) {
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

    Order placeBidLimit(double p) {
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

    Order placeOfferLimit(double p) {
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

    static void updateLog(String s) {
        outputArea.append(s);
        outputArea.append("\n");
    }

    static void clearLog() {
        outputArea.setText("");
    }

    int getCurrentPosition() {
        return 0;
    }

    void repaintThis() {
        repaint();
    }

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
                System.out.println(ChinaStockHelper.getStrCheckNull(dt, open, high, low, close));
                xuData.put(lt, new SimpleBar(open, high, low, close));
            }
        } else {
            System.out.println(getStr(date, open, high, low, close));
        }
    }

    @Override
    public void actionUponFinish(String name) {

    }

    static class XuPriceReceiver implements ITopMktDataHandler {

        @Override
        public void tickPrice(TickType tickType, double price, int canAutoExecute) {

            LocalTime lt = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
            switch (tickType) {
                case BID:
                    currentBid = price;
                    //System.out.println(" current bid " + currentBid);
                    break;
                case ASK:
                    currentAsk = price;
                    //System.out.println(" current ask " + currentAsk);
                    break;
                case LAST:
                    currentPrice = price;
                    //System.out.println(" current price " + currentPrice);
                    if (xuData.containsKey(lt)) {
                        xuData.get(lt).add(price);
                    } else {
                        xuData.put(lt, new SimpleBar(price));
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

    void requestLevel2Data() {
        apcon.reqDeepMktData(ct, 10, new XULevel2Handler());
    }

    void requestExecHistory() {
        System.out.println(" requesting exec history ");
        XUTrader.tradesMap = new ConcurrentSkipListMap<>();
        apcon.reqExecutions(new ExecutionFilter(), new TradeDefaultHandler());
    }

    void requestXUData() {
        try {
            getAPICon().reqXUDataArray(new XuPriceReceiver());
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    static void createDialog(String msg) {
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

    static void processTradeMap() {

        //average buy cost
        //System.out.println(" processing -------------------------------------------------");
        int unitsBought = XUTrader.tradesMap.entrySet().stream().filter(e -> e.getValue().getSize() > 0).collect(Collectors.summingInt(e -> e.getValue().getSize()));
        int unitsSold = XUTrader.tradesMap.entrySet().stream().filter(e -> e.getValue().getSize() < 0).collect(Collectors.summingInt(e -> e.getValue().getSize()));

        netBoughtPosition = unitsBought;
        netSoldPosition = unitsSold;

        double avgBuy = Math.round(100d * (XUTrader.tradesMap.entrySet().stream().filter(e -> e.getValue().getSize() > 0).collect(Collectors.summingDouble(e -> e.getValue().getCost()))
                / unitsBought)) / 100d;

        double avgSell = Math.round(100d * (XUTrader.tradesMap.entrySet().stream().filter(e -> e.getValue().getSize() < 0).collect(Collectors.summingDouble(e -> e.getValue().getCost()))
                / unitsSold)) / 100d;

        double buyTradePnl = Math.round(100d * (XUTrader.currentPrice - avgBuy) * unitsBought) / 100d;
        double sellTradePnl = Math.round(100d * (XUTrader.currentPrice - avgSell) * unitsSold) / 100d;
        double netTradePnl = buyTradePnl + sellTradePnl;
        netTotalCommissions = (unitsBought - unitsSold) * 1.505d;
        double mtmPnl = (netPosition - unitsBought - unitsSold) * (XUTrader.currentPrice - todayOpen);
        previousCloseOverride = 0;
        if (previousCloseOverride != 0) {
            mtmPnl = (netPosition - unitsBought - unitsSold) * (XUTrader.currentPrice - previousCloseOverride);
        }

        XUTrader.updateLog(" P " + XUTrader.currentPrice);
        XUTrader.updateLog("Open " + todayOpen);
        XUTrader.updateLog(" Chg " + (Math.round(10000d * (XUTrader.currentPrice / todayOpen - 1)) / 100d) + " %");
        XUTrader.updateLog("Open Pos " + (netPosition - unitsBought - unitsSold));
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

    public static void main(String[] args) {

        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1000, 1000));

        XUTrader xutrader = new XUTrader();
        jf.add(xutrader);
        jf.setLayout(new FlowLayout());
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        jf.setVisible(true);

        CompletableFuture.runAsync(() -> {
            //xutrader.connectToTWS(4001);
            xutrader.connectToTWS(7496);
        }).thenRun(() -> {
            CompletableFuture.runAsync(() -> {
                XUTrader.getAPICon().client().reqCurrentTime();
            });
            CompletableFuture.runAsync(() -> {
                xutrader.requestXUData();
            });
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

class TradeDefaultHandler implements ApiController.ITradeReportHandler {

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
            if (XUTrader.tradesMap.containsKey(ldt.toLocalTime())) {
                XUTrader.tradesMap.get(ldt.toLocalTime()).merge(new IBTrade(execution.price(), sign * execution.cumQty()));
            } else {
                XUTrader.tradesMap.put(ldt.toLocalTime(), new IBTrade(execution.price(), sign * execution.cumQty()));
            }

            //System.out.println(" printing all trades");
            //XUTrader.tradesMap.entrySet().stream().forEach(System.out::println);
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

}

class XUPositionHandler implements ApiController.IPositionHandler {

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        //System.out.println (" proper handling here XXXX ");
        SwingUtilities.invokeLater(() -> {
            if (contract.symbol().equals("XINA50")) {
//                XUTrader.updateLog(" account " + account + "\n");
//                XUTrader.updateLog(" contract " + contract.symbol()+ "\n");
//                XUTrader.updateLog(" Exchange " + contract.primaryExch()+ "\n");
//                XUTrader.updateLog(" Local symbol " + contract.localSymbol()+ "\n");
//                XUTrader.updateLog(" Last trade date " + contract.lastTradeDateOrContractMonth()+ "\n");
//                XUTrader.updateLog(" currency " + contract.currency()+ "\n");
//                XUTrader.updateLog(" pos " + position + "\n");
                XUTrader.setNetPosition((int) position);
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
}

class XUOrderHandler implements ApiController.IOrderHandler {

    @Override
    public void orderState(OrderState orderState) {
        XUTrader.updateLog(orderState.toString());
    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        XUTrader.updateLog(ChinaStockHelper.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));

        if (status.equals(OrderStatus.Filled)) {
            XUTrader.createDialog(ChinaStockHelper.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));
        }
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        XUTrader.updateLog(" handle error code " + errorCode + " message " + errorMsg);
    }
}

class XULiveOrderHandler implements ApiController.ILiveOrderHandler {

    @Override
    public void openOrder(Contract contract, Order order, OrderState orderState) {
        XUTrader.updateLog(getStr(contract.toString(), order.toString(), orderState.toString()));
    }

    @Override
    public void openOrderEnd() {
    }

    @Override
    public void orderStatus(int orderId, OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        XUTrader.updateLog(ChinaStockHelper.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));

        if (status.equals(OrderStatus.Filled)) {
            XUTrader.createDialog(ChinaStockHelper.getStr(" status filled remaining avgFillPrice ", status, filled, remaining, avgFillPrice));
        }
    }

    @Override
    public void handle(int orderId, int errorCode, String errorMsg) {
        XUTrader.updateLog(" handle error code " + errorCode + " message " + errorMsg);
    }
}

class XULevel2Handler implements ApiController.IDeepMktDataHandler {

    @Override
    public void updateMktDepth(int position, String marketMaker, Types.DeepType operation, Types.DeepSide side, double price, int size) {
        //System.out.println(" updating market depth method");
        //System.out.println( ChinaStockHelper.getStrCheckNull(" position marketMaker operation side price size ", position, marketMaker, operation, side, price, size));

        SwingUtilities.invokeLater(() -> {
            if (side.equals(Types.DeepSide.BUY)) {
                XUTrader.bidLabelList.get(position).setText(ChinaStockHelper.getStrCheckNull(price, "            ", size));
                XUTrader.bidPriceList.put("bid" + Integer.toString(position + 1), price);
            } else {
                XUTrader.askLabelList.get(position).setText(ChinaStockHelper.getStrCheckNull(price, "            ", size));
                XUTrader.offerPriceList.put("ask" + Integer.toString(position + 1), price);
            }
        });
    }
}
