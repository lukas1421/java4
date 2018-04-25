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
import handler.XUOvernightTradeExecHandler;
import sound.EmbeddedSoundPlayer;
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

import static apidemo.TradingConstants.FUT_COLLECTION_TIME;
import static apidemo.TradingConstants.ftseIndex;
import static apidemo.XuTraderHelper.*;
import static utility.Utility.*;

public final class XUTrader extends JPanel implements HistoricalHandler, ApiController.IDeepMktDataHandler,
        ApiController.ITradeReportHandler, ApiController.IOrderHandler, ApiController.ILiveOrderHandler
        , ApiController.IPositionHandler, ApiController.IConnectionHandler {

    static ApiController apcon;

    //global
    private static volatile MASentiment sentiment = MASentiment.Directionless;

    //overnight trades
    private static final int maxOvernightTrades = 10;
    private static AtomicInteger overnightClosingOrders = new AtomicInteger(0);
    private static AtomicInteger overnightTradesDone = new AtomicInteger(0);
    private static final double maxOvernightDeltaChgUSD = 50000.0;
    private static final double OVERNIGHT_MAX_DELTA = 500000.0;
    private static final double OVERNIGHT_MIN_DELTA = -500000.0;

    //ma
    public static AtomicBoolean MATraderStatus = new AtomicBoolean(true);
    private static volatile int currentMAPeriod = 60;
    private static Direction currentDirection = Direction.Flat;
    private static final int DEFAULT_SIZE = 1;
    private static LocalDateTime lastMATradeTime = LocalDateTime.now();
    private static LocalDateTime lastMAOrderTime = getSessionOpenTime();
    private static final int MAX_MA_SIGNALS_PER_SESSION = 5;
    private static AtomicInteger maSignals = new AtomicInteger(0); //session transient
    private static volatile NavigableMap<LocalDateTime, Order> maOrderMap = new ConcurrentSkipListMap<>();
    private static volatile TreeSet<MAIdea> maIdeasList = new TreeSet<>(Comparator.comparing(MAIdea::getIdeaTime));
    private static volatile long timeBtwnMAOrders = 5;
    private static final double PD_UP_THRESH = 0.003;
    private static final double PD_DOWN_THRESH = -0.003;


    //open trading
    static LocalDateTime lastOpenTradeTime = LocalDateTime.now();
    private static LocalDateTime lastOpenOrderTime = LocalDateTime.now();
    private static AtomicInteger openTradeSignals = new AtomicInteger(0);
    private static final long MAX_OPEN_TRADE_ORDERS = 10;

    //direction makeup trade
    private static final int MIN_LAST_TWO_APART_TIME = 5;

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

    public static volatile EnumMap<FutType, Integer> currentPosMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> botMap = new EnumMap<>(FutType.class);
    public static volatile EnumMap<FutType, Integer> soldMap = new EnumMap<>(FutType.class);

    public static volatile boolean showTrades = false;
    static volatile boolean connectionStatus = false;
    static volatile JLabel connectionLabel = new JLabel();

    private static void maTradeAnalysis() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sessionBeginLDT = XuTraderHelper.getSessionOpenTime();
        maIdeasList = new TreeSet<>();
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        NavigableMap<LocalDateTime, Double> sma = XuTraderHelper.getMAGen(price5, currentMAPeriod);
        if (price5.size() == 0) {
            return;
        }
        double lastPrice = price5.lastEntry().getValue().getClose();

        sma.forEach((lt, ma) -> {
            if (lt.isAfter(sessionBeginLDT) && !lt.equals(price5.firstKey()) && price5.containsKey(lt)) {
                SimpleBar sb = price5.get(lt);
                SimpleBar sbPrevious = price5.lowerEntry(lt).getValue();
                if (sbPrevious.strictIncludes(ma)) {
                    if (sb.getOpen() > ma && sbPrevious.getBarReturn() > 0.0) {
                        maIdeasList.add(new MAIdea(lt, ma, +1));
                    } else if (sb.getOpen() < ma && sbPrevious.getBarReturn() < 0.0) {
                        maIdeasList.add(new MAIdea(lt, ma, -1));
                    }
                }
            }
        });
        System.out.println(" MA analysis: ");
        maIdeasList.forEach(System.out::println);
        System.out.println(" compute MA profit ");
        computeMAProfit(maIdeasList, lastPrice);

        boolean lastTwoMAIdeasFarApart = true;

        if (maIdeasList.size() >= 2) {
            LocalDateTime lastIdeaTime = maIdeasList.last().getIdeaTime();
            LocalDateTime secLastIdeaTime = Objects.requireNonNull(maIdeasList.lower(maIdeasList.last())).getIdeaTime();
            long diffInMin = timeDiffinMinutes(secLastIdeaTime, lastIdeaTime);
            lastTwoMAIdeasFarApart = diffInMin > MIN_LAST_TWO_APART_TIME;
        }

        // make up for the last MA trade wihch was missing due to in restriction period
        if (maIdeasList.last().getIdeaTime().isAfter(lastMATradeTime)
                && now.isAfter(lastMAOrderTime.plusMinutes(timeBtwnMAOrders))
                && lastTwoMAIdeasFarApart) {
            MAIdea lastIdea = maIdeasList.last();
            outputToAutoLog(getStr(now, "LAST MA Trade MAKE UP ", lastIdea));
            if (lastIdea.getIdeaSize() > 0) {
                Order o = placeBidLimit(roundToXUTradablePrice(lastIdea.getIdeaPrice(), Direction.Long), 1);
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler());
                outputOrderToAutoLog(getStr(now, " MakeUp BUY || BIDDING @ ", o.toString(), "SMA",
                        lastIdea));
            } else {
                Order o = placeOfferLimit(roundToXUTradablePrice(lastIdea.getIdeaPrice(), Direction.Short), 1);
                apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler());
                outputOrderToAutoLog(getStr(now, " MakeUp SELL || OFFERING @ ", o.toString(), "SMA",
                        lastIdea));
            }
        }

        //computed from maAnalysis, persistent through sessions.
        long maSignalsPersist = maIdeasList.stream().filter(e -> e.getIdeaTime().isAfter(sessionBeginLDT)).count();
        String maOutput = (getStr("MA signals persist || BeginT: ", sessionBeginLDT,
                "||Last Order Time: ", lastMAOrderTime, "||Signal #: ", maSignalsPersist, "||list: ", maIdeasList));
        outputToAutoLog(maOutput);
    }

    public XUTrader getThis() {
        return this;
    }

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
            apcon.placeOrModifyOrder(activeFuture,
                    placeBidLimit(bidMap.get(ibContractToFutType(activeFuture)), 1.0), this);
        });

        JButton offerLimitButton = new JButton("Sell Limit");

        offerLimitButton.addActionListener(l -> {
            System.out.println(" selling limit ");
            apcon.placeOrModifyOrder(activeFuture,
                    placeOfferLimit(askMap.get(ibContractToFutType(activeFuture)), 1.0), this);
        });

        JButton buyOfferButton = new JButton(" Buy Now");
        buyOfferButton.addActionListener(l -> {
            System.out.println(" buy offer ");
            apcon.placeOrModifyOrder(activeFuture,
                    buyAtOffer(askMap.get(ibContractToFutType(activeFuture)), 1.0), this);
        });

        JButton sellBidButton = new JButton(" Sell Now");
        sellBidButton.addActionListener(l -> {
            System.out.println(" sell bid ");
            apcon.placeOrModifyOrder(activeFuture,
                    sellAtBid(bidMap.get(ibContractToFutType(activeFuture)), 1.0), this);
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
            maTraderStatusButton.setText("MA Trader " + (MATraderStatus.get() ? "ON" : "OFF"));
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

            ses.scheduleAtFixedRate(() -> {
                observeMATouch();
                maTradeAnalysis();
            }, 0, 1, TimeUnit.MINUTES);
            ses.scheduleAtFixedRate(() -> {
                outputToAutoLog(getStr("CANCELLING ALL ORDERS ", LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)));
                apcon.cancelAllOrders();
            }, 0, 60, TimeUnit.MINUTES);
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

        JButton overnightButton = new JButton(" Overnight ");
        overnightButton.addActionListener(l -> ses2.scheduleAtFixedRate(this::overnightTrader, 0, 1, TimeUnit.MINUTES));

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
        controlPanel2.add(overnightButton);
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
                        System.out.println(" double clicked buy " + l.getName());
                        double bidPrice = bidPriceList.getOrDefault(l.getName(), 0.0);
                        System.out.println(" bid price " + bidPrice + " check if order price makes sense "
                                + checkIfOrderPriceMakeSense(bidPrice));
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
                        System.out.println(" offer  price list " + offerPriceList.toString());
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

    /*
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
            if (t.isAfter(LocalTime.of(8, 59)) && t.isBefore(LocalTime.of(9, 40))) {
                System.out.println(" No sizing in open trading ");
            } else if (futureAMSession().test(t)) {
                factor = dir == Direction.Long ? 1 : 2;
            } else if (futurePMSession().test(t)) {
                factor = dir == Direction.Long ? 2 : 1;
            }
        }
        outputToAutoLog(getStr(" Determining Order Size ||T PERC PD DIRECTION -> FACTOR, FINAL SIZE ", t, perc
                , pd, dir, factor, factor));

        return factor;
    }

    private static int determineTimeDiffFactor() {
        if (maOrderMap.size() == 0) return 1;
        return Math.max(1, Math.min(2,
                (int) Math.floor(timeDiffinMinutes(maOrderMap.lastEntry().getKey(), LocalDateTime.now()) / 60d)));
    }

    /**
     * if touched, play music
     */
    private void observeMATouch() {
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        LocalTime lastKey = price5.lastEntry().getKey().toLocalTime();
        if (price5.size() < 2) {
            return;
        }
        SimpleBar lastBar = price5.lastEntry().getValue();
        SimpleBar secLastBar = price5.lowerEntry(price5.lastEntry().getKey()).getValue();
        NavigableMap<LocalDateTime, Double> sma;
        sma = XuTraderHelper.getMAGen(price5, 60);
        double indexPrice = (ChinaData.priceMapBar.containsKey(ftseIndex) &&
                ChinaData.priceMapBar.get(ftseIndex).size() > 0) ?
                ChinaData.priceMapBar.get(ftseIndex).lastEntry().getValue().getClose() : 0.0;
        double pd = (indexPrice != 0.0) ? (lastBar.getClose() / indexPrice - 1) : 0.0;
        int percentile = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));
        soundPlayer.stopIfPlaying();
        if (sma.size() > 0) {
            String msg = getStr("**Observing MA**",
                    " ||Time: ", LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
                    , "||SMA60 ", Math.round(100d * sma.lastEntry().getValue()) / 100d
                    , "||Last Key Bar", lastKey, lastBar.toString(), "#: ", maSignals.get()
                    , "||Last Trade T", lastMATradeTime.truncatedTo(ChronoUnit.MINUTES)
                    , "||Last Order T ", lastMAOrderTime.truncatedTo(ChronoUnit.MINUTES)
                    , "||WaitT Orders: ", timeBtwnMAOrders
                    , "||Earliest Next Order: ", lastMAOrderTime.plusMinutes(timeBtwnMAOrders)
                    , "||Orders: ", maOrderMap.toString()
                    , "||Index: ", Math.round(100d * indexPrice) / 100d
                    , "||PD: ", Math.round(10000d * pd) / 10000d
                    , "||Curr Dir", currentDirection, "|2ndLastBar ", secLastBar,
                    " || Percentile ", percentile);

            outputToAutoLog(msg);
            double lastMA = sma.lastEntry().getValue();
            if (secLastBar.strictIncludes(lastMA)
                    && (secLastBar.getBarReturn() * (lastBar.getOpen() > lastMA ? 1 : -1) > 0)) {
                outputToAutoLog(" sec last bar touched MA, playing clip ");
                soundPlayer.playClip();
            } else {
                outputToAutoLog(" no touch ");
            }
        }
    }

    /**
     * open trading from 9 to 9:40
     */
    void openTrading(double freshPrice) {
        LocalDateTime now = LocalDateTime.now();
        if (!(now.toLocalTime().isBefore(LocalTime.of(9, 41)) && now.toLocalTime().isAfter(LocalTime.of(8, 59)))) {
            return;
        }
        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        if (price5.size() <= 1) {
            return;
        }
        SimpleBar lastBar = new SimpleBar(price5.lastEntry().getValue());
        lastBar.add(freshPrice);
        NavigableMap<LocalDateTime, Double> sma;
        sma = XuTraderHelper.getMAGen(price5, currentMAPeriod);
        double maLast = sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;
        double candidatePrice;

        long secBtwnOpenOrders = openTradeSignals.get() == 0 ? 0 :
                Math.max(1, Math.round(1 * Math.pow(2, Math.min(15, openTradeSignals.get() - 1))));

        if (timeDiffInSeconds(lastOpenOrderTime, now) >= secBtwnOpenOrders
                && openTradeSignals.get() <= MAX_OPEN_TRADE_ORDERS) {

            if (lastBar.getOpen() < maLast && freshPrice >= maLast) {
                candidatePrice = roundToXUTradablePrice(maLast, Direction.Long);
                if (checkIfOrderPriceMakeSense(candidatePrice)) {
                    Order o = placeBidLimit(candidatePrice, 1);
                    apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler());
                    openTradeSignals.incrementAndGet();
                    lastOpenOrderTime = LocalDateTime.now();
                    outputOrderToAutoLog(getStr(now, "OPEN ORDER || BIDDING @ ", o.toString(), "SMA"));
                }
            }
            if (lastBar.getOpen() > maLast && freshPrice <= maLast) {
                candidatePrice = roundToXUTradablePrice(maLast, Direction.Short);
                if (checkIfOrderPriceMakeSense(candidatePrice)) {
                    Order o = placeOfferLimit(candidatePrice, 1);
                    apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler());
                    openTradeSignals.incrementAndGet();
                    lastOpenOrderTime = LocalDateTime.now();
                    outputOrderToAutoLog(getStr(now, "OPEN ORDER || OFFERING @ ", o.toString(), "SMA"));
                }
            }
        }
    }

    /**
     * Auto trading based on Moving Avg
     */
    public static void MATrader(double freshPrice) {
        LocalDateTime now = LocalDateTime.now();

        NavigableMap<LocalDateTime, SimpleBar> price5 = map1mTo5mLDT(futData.get(ibContractToFutType(activeFuture)));
        SimpleBar lastBar = new SimpleBar(0.0);
        SimpleBar secLastBar = new SimpleBar(0.0);
        if (price5.size() > 1) {
            lastBar = new SimpleBar(price5.lastEntry().getValue());
            secLastBar = new SimpleBar(price5.lowerEntry(price5.lastKey()).getValue());
        }
        lastBar.add(freshPrice);
        NavigableMap<LocalDateTime, Double> sma;
        sma = XuTraderHelper.getMAGen(price5, currentMAPeriod);
        double maLast = sma.size() > 0 ? sma.lastEntry().getValue() : 0.0;
        int numTrades = 0;
        double candidatePrice = 0.0;
        String priceType;
        int percentile = getPercentileForLast(futData.get(ibContractToFutType(activeFuture)));

        double indexPrice = (ChinaData.priceMapBar.containsKey(ftseIndex) &&
                ChinaData.priceMapBar.get(ftseIndex).size() > 0) ?
                ChinaData.priceMapBar.get(ftseIndex).lastEntry().getValue().getClose() : 0.0;
        double pd = (indexPrice != 0.0 && freshPrice != 0.0) ? (freshPrice / indexPrice - 1) : 0.0;

        if (tradesMap.get(ibContractToFutType(activeFuture)).size() > 0) {
            lastMATradeTime = XUTrader.tradesMap.get(ibContractToFutType(activeFuture)).lastKey();
            numTrades = XUTrader.tradesMap.get(ibContractToFutType(activeFuture))
                    .entrySet().stream().filter(e -> e.getKey().isAfter(getSessionOpenTime()))
                    .mapToInt(e -> e.getValue().getSizeAllAbs()).sum();
        }

        timeBtwnMAOrders = maOrderMap.size() == 0 ? 0 :
                Math.max(5, Math.round(5 * Math.pow(2, Math.min(7, maOrderMap.size() - 1))));

        if (timeDiffinMinutes(lastMAOrderTime, now) >= timeBtwnMAOrders && maSignals.get() <= MAX_MA_SIGNALS_PER_SESSION) {
            if (!lastBar.containsZero() && maLast != 0.0 && secLastBar.strictIncludes(maLast)) {
                if (lastBar.getOpen() > maLast && secLastBar.getBarReturn() > 0.0) {
                    if (currentDirection == Direction.Long || pd > PD_UP_THRESH) {
                        candidatePrice = roundToXUTradablePrice(maLast, Direction.Long);
                        priceType = (pd > PD_UP_THRESH ? "pd > " + PD_UP_THRESH : "already Long") + " BUY @ MA";
                    } else if (pd < PD_DOWN_THRESH) {
                        candidatePrice = askMap.getOrDefault(ibContractToFutType(activeFuture), 0.0);
                        priceType = "pd < " + PD_DOWN_THRESH + " -> LIFT OFFER";
                    } else if (maSignals.get() == 0) {
                        candidatePrice = bidMap.getOrDefault(ibContractToFutType(activeFuture), 0.0);
                        priceType = "maSignals = 0 -> BUY BID";
                    } else {
                        candidatePrice = roundToXUTradablePrice(lastBar.getOpen(), Direction.Long);
                        priceType = " maSignals > 0 -> BUY @ LAST OPEN";
                    }

                    Order o = placeBidLimit(candidatePrice, determinePDPercFactor(now.toLocalTime(), pd, Direction.Long,
                            percentile) * determineTimeDiffFactor());
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        apcon.cancelAllOrders();
                        maOrderMap.put(now, o);
                        maSignals.incrementAndGet();
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler());
                        lastMAOrderTime = LocalDateTime.now();
                        currentDirection = Direction.Long;
                        outputOrderToAutoLog(getStr(now, "MA ORDER || bidding @ ", o.toString(), "type", priceType));
                    }
                } else if (lastBar.getOpen() < maLast && secLastBar.getBarReturn() < 0.0) {
                    if (currentDirection == Direction.Short || pd < PD_DOWN_THRESH) {
                        candidatePrice = roundToXUTradablePrice(maLast, Direction.Short);
                        priceType = (pd < PD_DOWN_THRESH ? "pd < " + PD_DOWN_THRESH : "currDir is short(same)") + " SELL @ MA";
                    } else if (pd > PD_UP_THRESH) {
                        candidatePrice = bidMap.get(ibContractToFutType(activeFuture));
                        priceType = "pd > " + PD_UP_THRESH + " -> SELL @ BID";
                    } else if (maSignals.get() == 0) {
                        candidatePrice = askMap.get(ibContractToFutType(activeFuture));
                        priceType = "SELL: maSignals -> SELL @ OFFER";
                    } else {
                        candidatePrice = roundToXUTradablePrice(lastBar.getOpen(), Direction.Short);
                        priceType = "SELL: maSignals > 0 -> SELL @ LAST OPEN";
                    }
                    Order o = placeOfferLimit(candidatePrice, determinePDPercFactor(now.toLocalTime(), pd, Direction.Short,
                            percentile) * determineTimeDiffFactor());
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        maOrderMap.put(now, o);
                        maSignals.incrementAndGet();
                        apcon.cancelAllOrders();
                        apcon.placeOrModifyOrder(activeFuture, o, new DefaultOrderHandler());
                        lastMAOrderTime = LocalDateTime.now();
                        currentDirection = Direction.Short;
                        outputOrderToAutoLog(getStr(now, "MA ORDER || offering @ ", o.toString(), priceType));
                    }
                }
                if (candidatePrice != 0.0) {
                    String outputMsg = getStr("MA TRIGGER CONDITION|| ", now.truncatedTo(ChronoUnit.MINUTES),
                            "#: ", maSignals.get(), maOrderMap.toString(), "||Last Bar: ", lastBar.toString(),
                            "|SMA:", maLast, "|Last Trade Time:", lastMATradeTime.truncatedTo(ChronoUnit.MINUTES),
                            "|Last Order Time:", lastMAOrderTime.truncatedTo(ChronoUnit.MINUTES),
                            "|Order Wait T:", timeBtwnMAOrders,
                            "|PD", Math.round(pd * 10000d) / 10000d,
                            "|Index", Math.round(100d * indexPrice) / 100d,
                            "|Sec last Bar", secLastBar,
                            "|Perc: ", percentile);
                    outputToAutoLog(outputMsg);
                }
            }
        }
        if (detailedMA.get()) {
            System.out.println(getStr("Detail Fast MA | ", LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
                    , "||P", freshPrice, " ||SMA ", Math.round(100d * maLast) / 100d,
                    " ||LastBar:", lastBar, "||LastOrder T ", lastMAOrderTime.truncatedTo(ChronoUnit.MINUTES),
                    "|| WaitT Order:", timeBtwnMAOrders, "Tr#", numTrades,
                    "||Curr Direction:", currentDirection, "||MA Last: "
                    , maIdeasList.size() > 0 ? maIdeasList.last() : ""));
        }
    }

    /**
     * overnight close trading
     */
    private void overnightTrader() {
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
        int currPercentile = XuTraderHelper.getPercentileForLast(filteredPriceMap);
        double currentFut;
        double pd = 0.0;

        if (futPriceMap.size() > 0) {
            currentFut = futPriceMap.lastEntry().getValue().getClose();
            pd = indexPrice != 0.0 ? Math.round(10000d * (currentFut / indexPrice - 1)) / 10000d : 0.0;
        }

        if (absLotsTraded <= maxOvernightTrades && overnightClosingOrders.get() <= 5) {
            if (now.toLocalTime().isBefore(LocalTime.of(5, 0)) &&
                    now.toLocalTime().isAfter(LocalTime.of(4, 40))) {
                if (pd > 0.005) {
                    double candidatePrice = askMap.getOrDefault(ibContractToFutType((activeFuture)), 0.0);
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        outputToAutoLog(getStr(now, "O/N placing sell order @ ", candidatePrice,
                                " curr p% ", currPercentile, "curr PD: ", pd));
                        apcon.placeOrModifyOrder(activeFuture, placeOfferLimit(candidatePrice, 1.0), this);
                        overnightClosingOrders.incrementAndGet();
                    }
                } else if (pd < -0.005) {
                    double candidatePrice = bidMap.getOrDefault(ibContractToFutType(activeFuture), 0.0);
                    if (checkIfOrderPriceMakeSense(candidatePrice)) {
                        outputToAutoLog(getStr(now, "O/N placing buy order @ ", candidatePrice,
                                " curr p% ", currPercentile, " curr PD: ", pd));
                        apcon.placeOrModifyOrder(activeFuture, placeBidLimit(candidatePrice, 1.0), this);
                        overnightClosingOrders.incrementAndGet();
                    }
                } else {
                    outputToAutoLog(getStr(now, " nothing done "));
                }
            } else {
                outputToAutoLog(" outside tradable time slot");
            }
        } else {
            outputToAutoLog(getStr(now, " trades or delta exceeded MAX "));
        }

        String outputString = getStr("||O/N||", now.format(DateTimeFormatter.ofPattern("M-d H:mm:ss")),
                "||O/N trades done", absLotsTraded, "", "||O/N Delta: ", netTradedDelta,
                "||current percentile ", currPercentile, "||PD: ", pd, "||curr P: ",
                filteredPriceMap.lastEntry().getValue().getClose(), "||index: ", Math.round(100d * indexPrice) / 100d,
                "||bid ", bidMap.getOrDefault(ibContractToFutType(activeFuture), 0.0),
                " ||ask ", askMap.getOrDefault(ibContractToFutType(activeFuture), 0.0));

        outputToAutoLog(outputString);
        requestOvernightExecHistory();
    }

    private void loadXU() {
        System.out.println(" getting XU data ");
        apcon.getSGXA50Historical2(30000, this);
    }

    private static boolean checkIfOrderPriceMakeSense(double p) {
        FutType f = ibContractToFutType(activeFuture);
        System.out.println(getStr("CHECKING PRICE || bid ask price ",
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
            if (!ld.equals(currDate) && lt.equals(LocalTime.of(14, 59))) {
                futPrevCloseMap.put(FutType.get(name), close);
            }

            int daysToGoBack = currDate.getDayOfWeek().equals(DayOfWeek.MONDAY) ? 4 : 2;

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
        FutType f = ibContractToFutType(contract);
        if (contract.symbol().equalsIgnoreCase("SGXA50")) {
            System.out.println(getStr(" exec ", execution.side(), execution.time(), execution.cumQty()
                    , execution.price(), execution.orderRef(), execution.orderId(), execution.permId(), execution.shares()));
        }
        int sign = (execution.side().equals("BOT")) ? 1 : -1;
        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));

        if (ldt.isAfter(LocalDateTime.of(LocalDateTime.now().toLocalDate().minusDays(1L), LocalTime.of(15, 0)))) {
            if (XUTrader.tradesMap.get(f).containsKey(ldt)) {
                XUTrader.tradesMap.get(f).get(ldt)
                        .addTrade(new FutureTrade(execution.price(), (int) Math.round(sign * execution.shares())));
            } else {
                XUTrader.tradesMap.get(f).put(ldt,
                        new TradeBlock(new FutureTrade(execution.price(), (int) Math.round(sign * execution.shares()))));
            }
        }
    }

    @Override
    public void tradeReportEnd() {
        System.out.println(" trade report end printing");
        if (XUTrader.tradesMap.get(ibContractToFutType(activeFuture)).size() > 0) {
            currentDirection = XUTrader.tradesMap.get(ibContractToFutType(activeFuture)).lastEntry().getValue().getSizeAll() > 0 ?
                    Direction.Long : Direction.Short;
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
        XUTrader.updateLog(getStr(" status filled remaining avgFillPrice ",
                status, filled, remaining, avgFillPrice));
        if (status.equals(OrderStatus.Filled)) {
            XuTraderHelper.createDialog(getStr(" status filled remaining avgFillPrice ",
                    status, filled, remaining, avgFillPrice));
        }
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        XUTrader.updateLog(" handle error code " + errorCode + " message " + errorMsg);
    }

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
    }

    @Override
    public void openOrderEnd() {
    }

    @Override
    public void orderStatus(int orderId, OrderStatus status, int filled, int remaining,
                            double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {

        System.out.println(" in order status ");

        XUTrader.updateLog(Utility.getStr(" status filled remaining avgFillPrice ",
                status, filled, remaining, avgFillPrice));

        if (status.equals(OrderStatus.Filled)) {
            XuTraderHelper.createDialog(Utility.getStr(" status filled remaining avgFillPrice ",
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

        ApiController ap = new ApiController(new XuTraderHelper.XUConnectionHandler(),
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

