//package apidemo;
//
//import TradeType.IBTrade;
//import auxiliary.SimpleBar;
//import client.Contract;
//import graph.GraphBarGen;
//import handler.HistoricalHandler;
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.MouseAdapter;
//import java.awt.event.MouseEvent;
//import java.time.LocalTime;
//import java.time.temporal.ChronoUnit;
//import java.util.*;
//import java.util.List;
//import java.util.concurrent.ConcurrentSkipListMap;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class XUTraderEmbedded  extends JPanel implements HistoricalHandler {
//    private final Contract front = utility.Utility.getFrontFutContract();
//    private final Contract back = utility.Utility.getBackFutContract();
//
//    //AtomicInteger orderInitial = new AtomicInteger(3000001);
//    static volatile double currentBid;
//    static volatile double currentAsk;
//    static volatile double currentPrice;
//    static JTextArea outputArea = new JTextArea(20, 1);
//    private AtomicInteger orderIdNo;
//    static List<JLabel> bidLabelList = new ArrayList<>();
//    static List<JLabel> askLabelList = new ArrayList<>();
//    static Map<String, Double> bidPriceList = new HashMap<>();
//    static Map<String, Double> offerPriceList = new HashMap<>();
//    ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);
//    List<Integer> orderList = new LinkedList<>();
//
//    public static Map<LocalTime, IBTrade> tradesMap = new ConcurrentSkipListMap<>();
//
//    static volatile double netTotalCommissions = 0.0;
//
//    GraphBarGen xuGraph = new GraphBarGen();
//
//    public static NavigableMap<LocalTime, SimpleBar> xuFrontData = new ConcurrentSkipListMap<>();
//
//    public static volatile int netPositionFront;
//    public static volatile int netBoughtPositionFront;
//    public static volatile int netSoldPositionFront;
//    public static volatile boolean showTrades = false;
//    static volatile boolean connectionStatus = false;
//    static volatile JLabel connectionLabel = new JLabel();
//    static volatile AtomicInteger connectionID = new AtomicInteger(100);
//    static double todayOpen;
//    static double previousCloseOverride;
//
//    public XUTraderEmbedded() {
//        JLabel currTimeLabel = new JLabel(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
//        currTimeLabel.setFont(currTimeLabel.getFont().deriveFont(30F));
//
//        JButton bidLimitButton = new JButton("Buy Limit");
//
//        bidLimitButton.addActionListener(l -> {
//            System.out.println(" buying limit ");
//            apcon.placeOrModifyOrder(ct, placeBidLimit(currentBid), new XUOrderHandler());
//        });
//
//        JButton offerLimitButton = new JButton("Sell Limit");
//
//        offerLimitButton.addActionListener(l -> {
//            System.out.println(" selling limit ");
//            apcon.placeOrModifyOrder(ct, placeOfferLimit(currentAsk), new XUOrderHandler());
//        });
//
//        JButton buyOfferButton = new JButton(" Buy Now");
//        buyOfferButton.addActionListener(l -> {
//            System.out.println(" buy offer ");
//            apcon.placeOrModifyOrder(ct, buyAtOffer(currentAsk), new XUOrderHandler());
//        });
//
//        JButton sellBidButton = new JButton(" Sell Now");
//        sellBidButton.addActionListener(l -> {
//            System.out.println(" sell bid ");
//            apcon.placeOrModifyOrder(ct, sellAtBid(currentBid), new XUOrderHandler());
//        });
//
//        JButton getPositionButton = new JButton(" get pos ");
//        getPositionButton.addActionListener(l -> {
//            System.out.println(" getting pos ");
//            apcon.reqPositions(new XUPositionHandler());
//        });
//
//        JButton level2Button = new JButton("level2");
//        level2Button.addActionListener(l -> {
//            System.out.println(" getting level 2 button pressed");
//            requestLevel2Data();
//        });
//
//        JButton refreshButton = new JButton("Refresh");
//
//        refreshButton.addActionListener(l -> {
//            ses.scheduleAtFixedRate(() -> {
//                String time = (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).getSecond() != 0)
//                        ? (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString()) : (LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString() + ":00");
//                currTimeLabel.setText(time);
//                xuGraph.fillInGraph(xuFrontData);
//                xuGraph.refresh();
//                apcon.reqPositions(new XUPositionHandler());
//                repaint();
//            }, 0, 1, TimeUnit.SECONDS);
//        });
//
//        JButton execButton = new JButton("Exec");
//
//        execButton.addActionListener(l -> {
//            System.out.println(" getting exec details");
//            requestExecHistory();
//            //XUTrader.processTradeMap();
//            //XUTrader.
//        });
//
//        JButton processTradesButton = new JButton("Process");
//
//        processTradesButton.addActionListener(l -> {
//            ses.scheduleAtFixedRate(() -> {
//                XUTrader.clearLog();
//                XUTrader.updateLog("**************************************************************");
//                XUTrader.processTradeMap();
//            }, 0, 1, TimeUnit.SECONDS);
//        });
//
//        JButton connect7496 = new JButton("Connect 7496");
//
//        connect7496.addActionListener(l -> {
//            System.out.println(" trying to connect 7496");
//            //apcon.disconnect();
//
//            try {
//                apcon.connect("127.0.0.1", 7496, connectionID.incrementAndGet(), "");
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//            //apcon.client().reqIds(-1);
//        });
//
//        JButton connect4001 = new JButton("Connect 4001");
//
//        connect4001.addActionListener(l -> {
//            System.out.println(" trying to connect 4001");
//            try {
//                apcon.connect("127.0.0.1", 4001, connectionID.incrementAndGet(), "");
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//            //apcon.client().reqIds(-1);
//
//        });
//
//        JButton getData = new JButton("Data");
//        getData.addActionListener(l -> {
//            System.out.println(" getting data ");
//            loadXU();
//        });
//
//        JButton graphButton = new JButton("graph");
//        graphButton.addActionListener(l -> {
//            System.out.println(" graphing ");
//            xuGraph.setNavigableMap(xuFrontData);
//            xuGraph.refresh();
//            repaint();
//        });
//
//        JToggleButton showGraphButton = new JToggleButton("Show Trades");
//        showGraphButton.addActionListener(l -> {
//            if (showGraphButton.isSelected()) {
//                showTrades = true;
//                System.out.println(" show trade is " + showTrades);
//            } else {
//                showTrades = false;
//                System.out.println(" show trade is " + showTrades);
//            }
//        });
//        JLabel connectionStatusLabel = new JLabel(Boolean.toString(connectionStatus));
//
//        JButton disconnectButton = new JButton("Disconnect");
//        disconnectButton.addActionListener(l -> {
//            System.out.println(" -------------------disconnect button clicked-----------------------------------");
//            apcon.disconnect();
//        });
//
//        JScrollPane chartScroll = new JScrollPane(xuGraph) {
//            @Override
//            public Dimension getPreferredSize() {
//                Dimension d = super.getPreferredSize();
//                d.height = 300;
//                d.width = 1700;
//                return d;
//            }
//        };
//
//        // setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
//        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
//
//        JPanel controlPanel1 = new JPanel();
//        JPanel controlPanel2 = new JPanel();
//        controlPanel1.add(currTimeLabel);
//        controlPanel1.add(bidLimitButton);
//        controlPanel1.add(offerLimitButton);
//        controlPanel1.add(buyOfferButton);
//        controlPanel1.add(sellBidButton);
//
//        controlPanel2.add(getPositionButton);
//        controlPanel2.add(level2Button);
//        controlPanel2.add(refreshButton);
//        controlPanel2.add(execButton);
//        controlPanel2.add(processTradesButton);
//        controlPanel2.add(connect7496);
//        controlPanel2.add(connect4001);
//        controlPanel2.add(getData);
//        controlPanel2.add(graphButton);
//        controlPanel2.add(showGraphButton);
//        controlPanel2.add(connectionLabel);
//        //controlPanel2.add(connectionStatusLabel);
//        controlPanel2.add(disconnectButton);
//
//        JLabel bid1 = new JLabel("1");
//        bidLabelList.add(bid1);
//        bid1.setName("bid1");
//        JLabel bid2 = new JLabel("2");
//        bidLabelList.add(bid2);
//        bid2.setName("bid2");
//        JLabel bid3 = new JLabel("3");
//        bidLabelList.add(bid3);
//        bid3.setName("bid3");
//        JLabel bid4 = new JLabel("4");
//        bidLabelList.add(bid4);
//        bid4.setName("bid4");
//        JLabel bid5 = new JLabel("5");
//        bidLabelList.add(bid5);
//        bid5.setName("bid5");
//
//        JLabel ask1 = new JLabel("1");
//        askLabelList.add(ask1);
//        ask1.setName("ask1");
//        JLabel ask2 = new JLabel("2");
//        askLabelList.add(ask2);
//        ask2.setName("ask2");
//        JLabel ask3 = new JLabel("3");
//        askLabelList.add(ask3);
//        ask3.setName("ask3");
//        JLabel ask4 = new JLabel("4");
//        askLabelList.add(ask4);
//        ask4.setName("ask4");
//        JLabel ask5 = new JLabel("5");
//        askLabelList.add(ask5);
//        ask5.setName("ask5");
//
//        bidLabelList.forEach(l -> {
//            l.setOpaque(true);
//            l.setBorder(BorderFactory.createLineBorder(Color.BLACK));
//            l.setFont(l.getFont().deriveFont(30F));
//            l.setHorizontalAlignment(SwingConstants.CENTER);
//            l.addMouseListener(new MouseAdapter() {
//                @Override
//                public void mouseClicked(MouseEvent e) {
//                    if (e.getClickCount() == 2 && !e.isConsumed()) {
//                        System.out.println(" double clicked buy " + l.getName());
//                        double bidPrice = bidPriceList.get(l.getName());
////                        if (checkIfOrderPriceMakeSense(bidPrice) && marketOpen(LocalTime.now())) {
////                            apcon.placeOrModifyOrder(ct, placeBidLimit(bidPrice), new XUOrderHandler());
////                        } else {
////                            throw new IllegalArgumentException("fuck that price out of bound");
////                        }
//                    }
//                }
//            });
//        });
//
//        askLabelList.forEach(l -> {
//            l.setOpaque(true);
//            l.setBorder(BorderFactory.createLineBorder(Color.BLACK));
//            l.setFont(l.getFont().deriveFont(30F));
//            l.setHorizontalAlignment(SwingConstants.CENTER);
//
//            l.addMouseListener(new MouseAdapter() {
//                @Override
//                public void mouseClicked(MouseEvent e) {
//                    if (e.getClickCount() == 2 && !e.isConsumed()) {
//                        double offerPrice = offerPriceList.get(l.getName());
//                        System.out.println(" offer  price list " + offerPriceList.toString());
//
//                        if (checkIfOrderPriceMakeSense(offerPrice) && marketOpen(LocalTime.now())) {
//                            apcon.placeOrModifyOrder(ct, placeOfferLimit(offerPrice), new XUOrderHandler());
//                        } else {
//                            throw new IllegalArgumentException("fuck that price out of bound");
//                        }
//                    }
//                }
//            });
//        });
//
//        JPanel deepPanel = new JPanel();
//        //deepPanel.setPreferredSize(new Dimension(100,100));
//        deepPanel.setLayout(new GridLayout(5, 2));
//        deepPanel.add(bid1);
//        deepPanel.add(ask1);
//        deepPanel.add(bid2);
//        deepPanel.add(ask2);
//        deepPanel.add(bid3);
//        deepPanel.add(ask3);
//        deepPanel.add(bid4);
//        deepPanel.add(ask4);
//        deepPanel.add(bid5);
//        deepPanel.add(ask5);
//
//        JScrollPane outputPanel = new JScrollPane(outputArea);
//
//        JPanel graphPanel = new JPanel();
//        graphPanel.add(chartScroll);
//
//        controlPanel1.setLayout(new FlowLayout());
////        add(controlPanel,BorderLayout.NORTH);
////        add(deepPanel, BorderLayout.CENTER);
////        add(jp, BorderLayout.EAST);
////        add(graphPanel,BorderLayout.SOUTH);
//        //add(controlPanel);
//        add(controlPanel1);
//        add(controlPanel2);
//        add(deepPanel);
//        add(outputPanel);
//        add(graphPanel);
//
//
//    }
//
//    @SuppressWarnings("Duplicates")
//    private boolean checkIfOrderPriceMakeSense(double p) {
//        if (p == 0.0) {
//            return false;
//        } else {
//            if (currentAsk == 0.0 || currentBid == 0.0 || currentPrice == 0.0) {
//                return false;
//            } else {
//                return (Math.abs(p / currentPrice - 1) < 0.02);
//            }
//        }
//    }
//
//    @Override
//    public void handleHist(String name, String date, double open, double high, double low, double close) {
//
//    }
//
//    @Override
//    public void actionUponFinish(String name) {
//
//    }
//}
