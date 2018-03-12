package apidemo;

import auxiliary.SimpleBar;
import graph.GraphOptionIntraday;
import graph.GraphOptionLapse;
import graph.GraphOptionVol;
import graph.GraphOptionVolDiff;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import saving.ChinaSaveInterface1Blob;
import saving.ChinaVolIntraday;
import saving.ChinaVolSave;
import saving.HibernateUtil;
import util.NewTabbedPanel;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Blob;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static apidemo.ChinaOptionHelper.*;
import static java.lang.Math.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static saving.Hibtask.unblob;
import static utility.Utility.*;

//import org.apache.commons.math3.*;

public class ChinaOption extends JPanel implements Runnable {

    static ScheduledExecutorService es = Executors.newScheduledThreadPool(10);
    private static volatile JLabel optionNotif = new JLabel(" Option Notif ");
    private static volatile int runFrequency = 6;
    private static final List<LocalDate> expiryList = new ArrayList<>();
    static HashMap<String, Option> optionMap = new HashMap<>();

    private static GraphOptionVol graphTS = new GraphOptionVol();
    private static GraphOptionVolDiff graphVolDiff = new GraphOptionVolDiff();
    private static GraphOptionLapse graphLapse = new GraphOptionLapse();
    private static GraphOptionLapse graphATMLapse = new GraphOptionLapse();
    private static GraphOptionIntraday graphIntraday = new GraphOptionIntraday();

    public static LocalDate frontExpiry = getOptionExpiryDate(2018, Month.MARCH);
    public static LocalDate backExpiry = getOptionExpiryDate(2018, Month.APRIL);
    public static LocalDate thirdExpiry = getOptionExpiryDate(2018, Month.JUNE);
    public static LocalDate fourthExpiry = getOptionExpiryDate(2018, Month.SEPTEMBER);

    private static String frontMonth = frontExpiry.format(DateTimeFormatter.ofPattern("YYMM"));
    private static String backMonth = backExpiry.format(DateTimeFormatter.ofPattern("YYMM"));
    private static String thirdMonth = thirdExpiry.format(DateTimeFormatter.ofPattern("YYMM"));
    private static String fourthMonth = fourthExpiry.format(DateTimeFormatter.ofPattern("YYMM"));

    private static volatile boolean filterOn = false;
    private static volatile String selectedTicker = "";

    private static double stockPrice = 0.0;
    public static double interestRate = 0.04;

    public static volatile LocalDate previousTradingDate = LocalDate.now();
    private static volatile LocalDate pricingDate = LocalDate.now();
    //private static HashMap<String, Double> bidMap = new HashMap<>();
    //private static HashMap<String, Double> askMap = new HashMap<>();
    private static HashMap<String, Double> optionPriceMap = new HashMap<>();
    public static Map<String, Option> tickerOptionsMap = new HashMap<>();
    private static volatile List<String> optionListLive = new LinkedList<>();
    private static final List<String> optionListLoaded = new LinkedList<>();

    private static Map<String, Double> impliedVolMap = new HashMap<>();
    private static Map<String, Double> impliedVolMapYtd = new HashMap<>();
    public volatile static Map<String, Double> deltaMap = new HashMap<>();
    private static NavigableMap<LocalDate, NavigableMap<Double, Double>> strikeVolMapCall = new TreeMap<>();
    private static NavigableMap<LocalDate, NavigableMap<Double, Double>> strikeVolMapPut = new TreeMap<>();
    //private static List<JLabel> labelList = new ArrayList<>();
    private static JLabel timeLabel = new JLabel();
    public static volatile double currentStockPrice;
    public static volatile LocalDate expiryToCheck = frontExpiry;
    public static volatile boolean showDelta = false;
    private static volatile boolean computeOn = true;
    private static volatile Map<String, ConcurrentSkipListMap<LocalDate, Double>> histVol = new HashMap<>();

    public static volatile Map<String, ConcurrentSkipListMap<LocalDateTime, SimpleBar>> todayImpliedVolMap = new HashMap<>();

    private static NavigableMap<LocalDate, Double> timeLapseVolFront = new TreeMap<>();

    private static NavigableMap<LocalDate, TreeMap<LocalDate, Double>> timeLapseVolAllExpiries = new TreeMap<>();

    public static volatile AtomicInteger graphBarWidth = new AtomicInteger(5);

    private ChinaOption() {

        ChinaOptionHelper.getLastTradingDate();
        loadOptionTickers();

        expiryList.add(frontExpiry);
        expiryList.add(backExpiry);
        expiryList.add(thirdExpiry);
        expiryList.add(fourthExpiry);

        graphLapse.setGraphTitle("Fixed K Lapse");
        graphATMLapse.setGraphTitle(" ATM Lapse ");
        graphIntraday.setGraphTitle(" Intraday Vol ");

        for (LocalDate d : expiryList) {
            strikeVolMapCall.put(d, new TreeMap<>());
            strikeVolMapPut.put(d, new TreeMap<>());
        }

        JPanel leftPanel = new JPanel();
        JPanel rightPanel = new JPanel();

        NewTabbedPanel p = new NewTabbedPanel();

        OptionTableModel model = new OptionTableModel();
        JTable optionTable = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer tableCellRenderer, int r, int c) {
                if (optionListLoaded.size() > r) {
                    int modelRow = this.convertRowIndexToModel(r);
                    selectedTicker = optionListLoaded.get(modelRow);
                }

                Component comp = super.prepareRenderer(tableCellRenderer, r, c);
                if (isCellSelected(r, c)) {
                    comp.setBackground(Color.GREEN);

                    if (histVol.containsKey(selectedTicker)) {
                        graphLapse.setVolLapse(histVol.get(selectedTicker));
                        if (tickerOptionsMap.containsKey(selectedTicker)) {
                            graphLapse.setNameStrikeExp(selectedTicker, tickerOptionsMap.get(selectedTicker).getStrike(),
                                    tickerOptionsMap.get(selectedTicker).getExpiryDate(),
                                    tickerOptionsMap.get(selectedTicker).getCPString());
                            graphLapse.repaint();
                        }
                    }

                    if (tickerOptionsMap.containsKey(selectedTicker)) {
                        LocalDate selectedExpiry = tickerOptionsMap.get(selectedTicker).getExpiryDate();
                        double strike = tickerOptionsMap.get(selectedTicker).getStrike();
                        String callput = tickerOptionsMap.get(selectedTicker).getCPString();
                        graphIntraday.setNameStrikeExp(selectedTicker, strike, selectedExpiry, callput);
                        graphIntraday.setMap(todayImpliedVolMap.get(selectedTicker));
                        graphIntraday.repaint();
                        //System.out.println(" ticker intraday vol " + selectedTicker + " " + todayImpliedVolMap.get(selectedTicker));
//                       if (timeLapseVolAllExpiries.containsKey(selectedExpiry)) {
//                            graphATMLapse.setVolLapse(timeLapseVolAllExpiries.get(selectedExpiry));
//                            //graphATMLapse.setNameStrikeExp("ATM lapse", 0.0, selectedExpiry, "");
//                        }
//                        graphATMLapse.repaint();
                    }
                } else if (r % 2 == 0) {
                    comp.setBackground(Color.lightGray);
                } else {
                    comp.setBackground(Color.white);
                }
                return comp;
            }
        };

        JScrollPane optTableScroll = new JScrollPane(optionTable) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 200;
                d.width = 1500;
                return d;
            }
        };

        leftPanel.setLayout(new BorderLayout());
        rightPanel.setLayout(new BorderLayout());

        leftPanel.add(optTableScroll, BorderLayout.NORTH);

        setLayout(new BorderLayout());
        add(rightPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        JPanel dataPanel = new JPanel();
        dataPanel.setLayout(new GridLayout(10, 3));

        JScrollPane scrollTS = new JScrollPane(graphTS) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane scrollDiff = new JScrollPane(graphVolDiff) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane scrollLapse = new JScrollPane(graphLapse) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane scrollATMLapse = new JScrollPane(graphATMLapse) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane scrollIntraday = new JScrollPane(graphIntraday) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        add(controlPanel, BorderLayout.NORTH);
        rightPanel.add(optTableScroll);

        JPanel graphPanel = new JPanel();
        p.addTab("Graph1", graphPanel);
        graphPanel.setLayout(new GridLayout(2, 1));
        graphPanel.add(scrollTS);
        graphPanel.add(scrollDiff);

        JPanel graphPanel2 = new JPanel();
        p.addTab("Graph2", graphPanel2);
        graphPanel2.setLayout(new GridLayout(2, 1));
        graphPanel2.add(scrollLapse);
        graphPanel2.add(scrollATMLapse);

        //graph 3
        JPanel graphPanel3 = new JPanel();
        p.addTab("Graph Intraday", graphPanel3);
        graphPanel3.setLayout(new GridLayout(2, 1));
        graphPanel3.add(scrollIntraday);

        //selecting
        p.select("Graph Intraday");


        //rightPanel.add(graphPanel, BorderLayout.SOUTH);
        rightPanel.add(p, BorderLayout.SOUTH);

        JButton saveVolsButton = new JButton(" Save Vols ");
        JButton saveVolsHibButton = new JButton(" Save Vols hib ");
        JButton getPreviousVolButton = new JButton("Prev Vol");

        JButton frontMonthButton = new JButton("Front");
        JButton backMonthButton = new JButton("Back");
        JButton thirdMonthButton = new JButton("Third");
        JButton fourthMonthButton = new JButton("Fourth");
        JButton showDeltaButton = new JButton("Show Delta");

        JToggleButton computeOnButton = new JToggleButton("compute");
        computeOnButton.setSelected(true);

        JButton saveIntradayButton = new JButton(" Save Intraday ");
        JButton loadIntradayButton = new JButton(" Load intraday");
        JButton outputOptionsButton = new JButton(" Output Options ");


        outputOptionsButton.addActionListener(l -> {
            outputOptions();
        });

        JButton barWidthUp = new JButton(" UP ");
        barWidthUp.addActionListener(l -> {
            graphBarWidth.incrementAndGet();
            SwingUtilities.invokeLater(graphIntraday::repaint);
        });

        JButton barWidthDown = new JButton(" DOWN ");
        barWidthDown.addActionListener(l -> {
            graphBarWidth.set(Math.max(1, graphBarWidth.decrementAndGet()));
            SwingUtilities.invokeLater(graphIntraday::repaint);
        });

        saveIntradayButton.addActionListener(l -> {
            saveIntradayVolsHib(todayImpliedVolMap, ChinaVolIntraday.getInstance());
        });

        loadIntradayButton.addActionListener(l -> {
            loadIntradayVolsHib(ChinaVolIntraday.getInstance());
        });

        showDeltaButton.addActionListener(l -> showDelta = !showDelta);

        getPreviousVolButton.addActionListener(l -> loadPreviousOptions());

        frontMonthButton.addActionListener(l -> {
            expiryToCheck = frontExpiry;
            refreshAllGraphs();
        });

        backMonthButton.addActionListener(l -> {
            expiryToCheck = backExpiry;
            refreshAllGraphs();
        });
        thirdMonthButton.addActionListener(l -> {
            expiryToCheck = thirdExpiry;
            refreshAllGraphs();
        });
        fourthMonthButton.addActionListener(l -> {
            expiryToCheck = fourthExpiry;
            refreshAllGraphs();
        });
        computeOnButton.addActionListener(l -> computeOn = computeOnButton.isSelected());


        saveVolsButton.addActionListener(l -> saveVols());
        saveVolsHibButton.addActionListener(l -> {
            saveHibEOD();
            //ChinaOptionHelper.getVolsFromVolOutputToHib();
        });

        controlPanel.add(saveVolsButton);
        controlPanel.add(saveVolsHibButton);
        controlPanel.add(getPreviousVolButton);

        controlPanel.add(Box.createHorizontalStrut(10));

        controlPanel.add(frontMonthButton);
        controlPanel.add(backMonthButton);
        controlPanel.add(thirdMonthButton);
        controlPanel.add(fourthMonthButton);

        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(showDeltaButton);
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(computeOnButton);

        controlPanel.add(saveIntradayButton);
        controlPanel.add(loadIntradayButton);
        controlPanel.add(outputOptionsButton);

        controlPanel.add(barWidthUp);
        controlPanel.add(barWidthDown);


        timeLabel.setOpaque(true);
        timeLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        timeLabel.setFont(timeLabel.getFont().deriveFont(30F));
        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        optionNotif.setOpaque(true);
        optionNotif.setBackground(Color.orange);
        optionNotif.setForeground(Color.black);
        optionNotif.setFont(optionNotif.getFont().deriveFont(15F));

        JPanel notifPanel = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 50;
                return d;
            }
        };

        notifPanel.setLayout(new GridLayout(1, 5));
        notifPanel.add(timeLabel);
        notifPanel.add(optionNotif);
        add(notifPanel, BorderLayout.SOUTH);
//        labelList.forEach(l -> {
//            l.setOpaque(true);
//            l.setBorder(BorderFactory.createLineBorder(Color.BLACK));
//            l.setFont(l.getFont().deriveFont(30F));
//            l.setHorizontalAlignment(SwingConstants.CENTER);
//        });

        optionTable.setAutoCreateRowSorter(true);
        @SuppressWarnings("unchecked")
        TableRowSorter<OptionTableModel> sorter = (TableRowSorter<OptionTableModel>) optionTable.getRowSorter();
    }

    private static void updateOptionSystemInfo(String text) {
        SwingUtilities.invokeLater(() -> {
            optionNotif.setText(text);
            optionNotif.setBackground(shiftColor(optionNotif.getBackground()));
        });

        if (!es.isShutdown()) {
            es.shutdown();
        }
        es = Executors.newScheduledThreadPool(10);
        es.schedule(() -> {
            //timeLabel.setText(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
            optionNotif.setText("");
            optionNotif.setBackground(Color.orange);
        }, 10, SECONDS);


    }

    private static void refreshAllGraphs() {
        graphTS.repaint();
        graphVolDiff.repaint();
        graphLapse.repaint();
        graphATMLapse.repaint();
    }

    private void outputOptions() {
        System.out.println(" outputting options");
        File output = new File(TradingConstants.GLOBALPATH + "optionList.txt");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, false))) {
            optionListLive.forEach(s -> {
                try {
                    out.append(s);
                    out.newLine();
                } catch (IOException ex) {
                    Logger.getLogger(ChinaData.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadOptionTickers() {

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "optionList.txt"), "gbk"))) {
            String line;
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                optionListLoaded.add(al1.get(0));
                todayImpliedVolMap.put(al1.get(0), new ConcurrentSkipListMap<>());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    private void saveIntradayVolsHib(Map<String, ? extends NavigableMap<LocalDateTime, ?>> mp,
                                     ChinaSaveInterface1Blob saveclass) {

        LocalTime start = LocalTime.now();
        SessionFactory sessionF = HibernateUtil.getSessionFactory();
        CompletableFuture.runAsync(() -> {

            try (Session session = sessionF.openSession()) {
                try {
                    session.getTransaction().begin();
                    session.createQuery("DELETE from " + saveclass.getClass().getName()).executeUpdate();
                    AtomicLong i = new AtomicLong(0L);
                    optionListLoaded.forEach(name -> {
                        ChinaSaveInterface1Blob cs = saveclass.createInstance(name);
                        cs.setFirstBlob(blobify(mp.get(name), session));
                        session.save(cs);
                        if (i.get() % 100 == 0) {
                            session.flush();
                        }
                        i.incrementAndGet();
                    });
                    session.getTransaction().commit();
                    session.close();
                } catch (org.hibernate.exception.LockAcquisitionException x) {
                    x.printStackTrace();
                    session.close();
                }
            }
        }).thenAccept(
                v -> {
                    updateOptionSystemInfo(Utility.getStr("存", saveclass.getSimpleName(),
                            LocalTime.now().truncatedTo(ChronoUnit.SECONDS), " Taken: ",
                            ChronoUnit.SECONDS.between(start, LocalTime.now().truncatedTo(ChronoUnit.SECONDS))));
                    System.out.println(getStr(" done saving ", LocalTime.now()));
                }
        );

    }

    private void loadIntradayVolsHib(ChinaSaveInterface1Blob saveclass) {
        LocalTime start = LocalTime.now();
        CompletableFuture.runAsync(() -> {
            SessionFactory sessionF = HibernateUtil.getSessionFactory();
            String problemKey = " ";
            try (Session session = sessionF.openSession()) {
                for (String key : optionListLoaded) {
                    try {
                        //System.out.println(" loading key " + key + " save class " + saveclass.getClass());
                        ChinaSaveInterface1Blob cs = session.load(saveclass.getClass(), key);
                        Blob blob1 = cs.getFirstBlob();
                        saveclass.updateFirstMap(key, unblob(blob1));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.out.println(" error message " + key + " " + ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                System.out.println(getStr(" ticker has problem " + problemKey));
                ex.printStackTrace();
            }
        }).thenAccept(
                v -> {
                    updateOptionSystemInfo(Utility.getStr(" LOAD INTRADAY VOLS DONE ",
                            LocalTime.now().truncatedTo(ChronoUnit.SECONDS), " Taken: ",
                            ChronoUnit.SECONDS.between(start, LocalTime.now().truncatedTo(ChronoUnit.SECONDS))
                    ));
                }
        );
        ;
    }


    private void saveVols() {

        File output = new File(TradingConstants.GLOBALPATH + "volOutput.csv");

        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            saveVolHelper(out, pricingDate, CallPutFlag.CALL, strikeVolMapCall, frontExpiry, currentStockPrice);
            saveVolHelper(out, pricingDate, CallPutFlag.CALL, strikeVolMapCall, backExpiry, currentStockPrice);
            saveVolHelper(out, pricingDate, CallPutFlag.CALL, strikeVolMapCall, thirdExpiry, currentStockPrice);
            saveVolHelper(out, pricingDate, CallPutFlag.CALL, strikeVolMapCall, fourthExpiry, currentStockPrice);

            saveVolHelper(out, pricingDate, CallPutFlag.PUT, strikeVolMapPut, frontExpiry, currentStockPrice);
            saveVolHelper(out, pricingDate, CallPutFlag.PUT, strikeVolMapPut, backExpiry, currentStockPrice);
            saveVolHelper(out, pricingDate, CallPutFlag.PUT, strikeVolMapPut, thirdExpiry, currentStockPrice);
            saveVolHelper(out, pricingDate, CallPutFlag.PUT, strikeVolMapPut, fourthExpiry, currentStockPrice);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void saveHibEOD() {
        SessionFactory sessionF = HibernateUtil.getSessionFactory();
        try (Session session = sessionF.openSession()) {
            session.getTransaction().begin();
            AtomicInteger i = new AtomicInteger(1);
            HashMap<CallPutFlag, NavigableMap<LocalDate, NavigableMap<Double, Double>>> strikeVolMaps =
                    new HashMap<>();
            strikeVolMaps.put(CallPutFlag.CALL, strikeVolMapCall);
            strikeVolMaps.put(CallPutFlag.PUT, strikeVolMapPut);

            try {
                for (CallPutFlag f : CallPutFlag.values()) {
                    for (LocalDate exp : expiryList) {
                        strikeVolMaps.get(f).get(exp).forEach((strike, vol) -> {
                            String callput = (f == CallPutFlag.CALL ? "C" : "P");
                            String ticker = getOptionTicker(tickerOptionsMap, f, strike, exp);
                            int moneyness = (int) Math.round((strike / currentStockPrice) * 100d);
                            ChinaVolSave v = new ChinaVolSave(pricingDate, callput, strike, exp, vol, moneyness, ticker);
                            System.out.println(getStr(" pricingdate callput exp vol moneyness ticker counter "
                                    , pricingDate, callput, strike, exp, vol, moneyness, ticker, i.get()));
                            session.saveOrUpdate(v);
                            i.incrementAndGet();
                            if (i.get() % 100 == 0) {
                                session.flush();
                            }
                        });
                    }
                }
                session.getTransaction().commit();
            } catch (Exception x) {
                x.printStackTrace();
                session.getTransaction().rollback();
                session.close();
            }
        }
    }


    private void saveVolHelper(BufferedWriter w, LocalDate writeDate, CallPutFlag f,
                               Map<LocalDate, NavigableMap<Double, Double>> mp, LocalDate expireDate, double spot) {
        if (mp.containsKey(expireDate)) {
            mp.get(expireDate).forEach((k, v) -> {
                try {
                    w.append(Utility.getStrComma(writeDate.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
                            , f == CallPutFlag.CALL ? "C" : "P", k,
                            expireDate.format(DateTimeFormatter.ofPattern("yyyy/M/d")),
                            v, Math.round((k / spot) * 100d), getOptionTicker(tickerOptionsMap, f, k, expireDate)));
                    w.newLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Override
    public void run() {
        timeLabel.setText(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString() + (LocalTime.now().getSecond() == 0 ? ":00" : ""));
        if (computeOn) {
            System.out.println(" running @ " + LocalTime.now());
            try {
                String callStringFront = "http://hq.sinajs.cn/list=OP_UP_510050" + frontMonth;
                URL urlCallFront = new URL(callStringFront);
                URLConnection urlconnCallFront = urlCallFront.openConnection();

                String putStringFront = "http://hq.sinajs.cn/list=OP_DOWN_510050" + frontMonth;
                URL urlPutFront = new URL(putStringFront);
                URLConnection urlconnPutFront = urlPutFront.openConnection();

                String callStringBack = "http://hq.sinajs.cn/list=OP_UP_510050" + backMonth;
                URL urlCallBack = new URL(callStringBack);
                URLConnection urlconnCallBack = urlCallBack.openConnection();

                String putStringBack = "http://hq.sinajs.cn/list=OP_DOWN_510050" + backMonth;
                URL urlPutBack = new URL(putStringBack);
                URLConnection urlconnPutBack = urlPutBack.openConnection();

                String callStringThird = "http://hq.sinajs.cn/list=OP_UP_510050" + thirdMonth;
                URL urlCallThird = new URL(callStringThird);
                URLConnection urlconnCallThird = urlCallThird.openConnection();

                String putStringThird = "http://hq.sinajs.cn/list=OP_DOWN_510050" + thirdMonth;
                URL urlPutThird = new URL(putStringThird);
                URLConnection urlconnPutThird = urlPutThird.openConnection();

                String callStringFourth = "http://hq.sinajs.cn/list=OP_UP_510050" + fourthMonth;
                URL urlCallFourth = new URL(callStringFourth);
                URLConnection urlconnCallFourth = urlCallFourth.openConnection();

                String putStringFourth = "http://hq.sinajs.cn/list=OP_DOWN_510050" + fourthMonth;
                URL urlPutFourth = new URL(putStringFourth);
                URLConnection urlconnPutFourth = urlPutFourth.openConnection();

                getOptionInfo(urlconnCallFront, CallPutFlag.CALL, frontExpiry);
                getOptionInfo(urlconnPutFront, CallPutFlag.PUT, frontExpiry);
                getOptionInfo(urlconnCallBack, CallPutFlag.CALL, backExpiry);
                getOptionInfo(urlconnPutBack, CallPutFlag.PUT, backExpiry);
                getOptionInfo(urlconnCallThird, CallPutFlag.CALL, thirdExpiry);
                getOptionInfo(urlconnPutThird, CallPutFlag.PUT, thirdExpiry);
                getOptionInfo(urlconnCallFourth, CallPutFlag.CALL, fourthExpiry);
                getOptionInfo(urlconnPutFourth, CallPutFlag.PUT, fourthExpiry);
            } catch (IOException ex2) {
                ex2.printStackTrace();
            }
            graphTS.repaint();
            graphVolDiff.repaint();

            for (LocalDate d : expiryList) {
                if (strikeVolMapCall.containsKey(d) && strikeVolMapPut.containsKey(d)
                        && timeLapseVolAllExpiries.containsKey(d)) {

                    NavigableMap<Integer, Double> todayMoneynessVol =
                            mergePutCallVolsMoneyness(strikeVolMapCall.get(d), strikeVolMapPut.get(d), stockPrice);
                    timeLapseVolAllExpiries.get(d).put(pricingDate, getVolByMoneyness(todayMoneynessVol, 100));
                }
            }
            if (timeLapseVolAllExpiries.containsKey(expiryToCheck)) {
                graphATMLapse.setVolLapse(timeLapseVolAllExpiries.get(expiryToCheck));
                graphATMLapse.setGraphTitle(expiryToCheck.format(DateTimeFormatter.ofPattern("MM-dd")) + " ATM lapse ");
            }
            graphATMLapse.repaint();
        }
    }

    private static void getOptionInfo(URLConnection conn, CallPutFlag f, LocalDate expiry) {
        String line;
        try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
            while ((line = reader2.readLine()) != null) {
                Matcher m = (f == CallPutFlag.CALL ? ChinaOptionHelper.CALL_NAME_PATTERN.matcher(line)
                        : ChinaOptionHelper.PUT_NAME_PATTERN.matcher(line));
                List<String> datalist;
                while (m.find()) {
                    String res = m.group(1);
                    datalist = Arrays.asList(res.split(","));
                    URL allOptions = new URL("http://hq.sinajs.cn/list=" +
                            datalist.stream().collect(Collectors.joining(",")));
                    URLConnection urlconnAllPutsThird = allOptions.openConnection();
                    getInfoFromURLConn(urlconnAllPutsThird, f, expiry);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void getInfoFromURLConn(URLConnection conn, CallPutFlag f, LocalDate expiry) {
        String line;
        Matcher matcher;
        try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
            while ((line = reader2.readLine()) != null) {
                matcher = ChinaOptionHelper.OPTION_PATTERN.matcher(line);

                while (matcher.find()) {
                    String resName = matcher.group(1);
                    String res = matcher.group(2);
                    List<String> res1 = Arrays.asList(res.split(","));

                    optionPriceMap.put(resName, Double.parseDouble(res1.get(2)));
                    optionPriceMap.put(resName, Double.parseDouble(res1.get(2)));
                    if (!optionListLive.contains(resName)) {
                        optionListLive.add(resName);
                    }

                    tickerOptionsMap.put(resName, f == CallPutFlag.CALL ?
                            new CallOption(Double.parseDouble(res1.get(7)), expiry) :
                            new PutOption(Double.parseDouble(res1.get(7)), expiry));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        stockPrice = get510050Price();
        LocalDateTime currentLDT = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        tickerOptionsMap.forEach((k, v) -> {
            double vol = ChinaOptionHelper.simpleSolver(optionPriceMap.get(k), ChinaOptionHelper.fillInBS(stockPrice, v));
            impliedVolMap.put(k, vol);
            deltaMap.put(k, getDelta(v.getCallOrPut(), stockPrice, v.getStrike(), vol, v.getTimeToExpiry(), interestRate));

            // today localtime vol map
            if (todayImpliedVolMap.containsKey(k)) {
                if (todayImpliedVolMap.get(k).containsKey(currentLDT)) {
                    todayImpliedVolMap.get(k).get(currentLDT).add(vol);
                } else {
                    todayImpliedVolMap.get(k).put(currentLDT, new SimpleBar(vol));
                }
            } else {
                todayImpliedVolMap.put(k, new ConcurrentSkipListMap<>());
                todayImpliedVolMap.get(k).put(currentLDT, new SimpleBar(vol));
            }

            if (v.getCallOrPut() == CallPutFlag.CALL) {
                strikeVolMapCall.get(v.getExpiryDate()).put(v.getStrike(), vol);
            } else {
                strikeVolMapPut.get(v.getExpiryDate()).put(v.getStrike(), vol);
            }

            if (!histVol.containsKey(k)) {
                histVol.put(k, new ConcurrentSkipListMap<>());
            }
            histVol.get(k).put(LocalDate.now(), vol);
        });

        graphTS.setVolSmileFront(mergePutCallVols(strikeVolMapCall.get(frontExpiry), strikeVolMapPut.get(frontExpiry), stockPrice));
        graphTS.setVolSmileBack(mergePutCallVols(strikeVolMapCall.get(backExpiry), strikeVolMapPut.get(backExpiry), stockPrice));
        graphTS.setVolSmileThird(mergePutCallVols(strikeVolMapCall.get(thirdExpiry), strikeVolMapPut.get(thirdExpiry), stockPrice));
        graphTS.setVolSmileFourth(mergePutCallVols(strikeVolMapCall.get(fourthExpiry), strikeVolMapPut.get(fourthExpiry), stockPrice));

        graphTS.setCurrentPrice(stockPrice);
        graphVolDiff.setCurrentPrice(stockPrice);
        graphVolDiff.setVolNow(mergePutCallVols(strikeVolMapCall.get(expiryToCheck),
                strikeVolMapPut.get(expiryToCheck), stockPrice));

        if (impliedVolMapYtd.size() > 0) {
            NavigableMap<Double, Double> callMap = impliedVolMapYtd.entrySet().stream()
                    .filter(e -> (tickerOptionsMap.get(e.getKey()).getCallOrPut() == CallPutFlag.CALL)
                            && tickerOptionsMap.get(e.getKey()).getExpiryDate().equals(expiryToCheck))
                    .collect(Collectors.toMap(e -> {
                        if (tickerOptionsMap.containsKey(e.getKey())) {
                            return tickerOptionsMap.get(e.getKey()).getStrike();
                        } else {
                            return 0.0;
                        }
                    }, Map.Entry::getValue, (a, b) -> a, TreeMap::new));

            NavigableMap<Double, Double> putMap = impliedVolMapYtd.entrySet().stream()
                    .filter(e -> tickerOptionsMap.get(e.getKey())
                            .getCallOrPut() == CallPutFlag.PUT &&
                            tickerOptionsMap.get(e.getKey()).getExpiryDate().equals(expiryToCheck))
                    .collect(Collectors.toMap(e -> {
                        if (tickerOptionsMap.containsKey(e.getKey())) {
                            return tickerOptionsMap.get(e.getKey()).getStrike();
                        } else {
                            return 0.0;
                        }
                    }, Map.Entry::getValue, (a, b) -> a, TreeMap::new));
            graphVolDiff.setVolPrev1(mergePutCallVols(callMap, putMap, stockPrice));
        }

        if (histVol.containsKey(selectedTicker)) {
            graphLapse.setVolLapse(histVol.get(selectedTicker));
            if (tickerOptionsMap.containsKey(selectedTicker)) {
                graphLapse.setNameStrikeExp(selectedTicker, tickerOptionsMap.get(selectedTicker).getStrike(),
                        tickerOptionsMap.get(selectedTicker).getExpiryDate(),
                        tickerOptionsMap.get(selectedTicker).getCPString());
                graphLapse.repaint();
            }
        }
    }

    private static void loadPreviousOptions() {
        String line;

        //NavigableMap<LocalDate, TreeMap<Integer, Double>> timeLapseMoneynessVolFront = new TreeMap<>();
        NavigableMap<LocalDate, TreeMap<LocalDate, TreeMap<Integer, Double>>>
                timeLapseMoneynessVolAllExpiries = new TreeMap<>();

        for (LocalDate expiry : expiryList) {
            timeLapseMoneynessVolAllExpiries.put(expiry, new TreeMap<>());
            timeLapseVolAllExpiries.put(expiry, new TreeMap<>());
        }

        //2018/2/26	C	2.6	2018/2/28	0	88
        // record date (at close) || CP Flag || strike || expiry date || vol || moneyness
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "volOutput.csv")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split(","));
                LocalDate volDate = LocalDate.parse(al1.get(0), DateTimeFormatter.ofPattern("yyyy/M/d"));
                CallPutFlag f = al1.get(1).equalsIgnoreCase("C") ? CallPutFlag.CALL : CallPutFlag.PUT;
                double strike = Double.parseDouble(al1.get(2));
                LocalDate expiry = LocalDate.parse(al1.get(3), DateTimeFormatter.ofPattern("yyyy/M/dd"));
                double volPrev = Double.parseDouble(al1.get(4));
                int moneyness = Integer.parseInt(al1.get(5));
                String ticker = getOptionTicker(tickerOptionsMap, f, strike, expiry);


//                if (expiry.equals(frontExpiry)) {
//                    if (!timeLapseMoneynessVolFront.containsKey(volDate)) {
//                        timeLapseMoneynessVolFront.put(volDate, new TreeMap<>());
//                    }
//                    if ((f == CallPutFlag.CALL && moneyness >= 100) || (f == CallPutFlag.PUT && moneyness < 100)) {
//                        timeLapseMoneynessVolFront.get(volDate).put(moneyness, volPrev);
//                    }
//                }

                if (expiryList.contains(expiry)) {
                    if (!timeLapseMoneynessVolAllExpiries.get(expiry).containsKey(volDate)) {
                        timeLapseMoneynessVolAllExpiries.get(expiry).put(volDate, new TreeMap<>());
                    }
                    if ((f == CallPutFlag.CALL && moneyness >= 100) || (f == CallPutFlag.PUT && moneyness < 100)) {
                        timeLapseMoneynessVolAllExpiries.get(expiry).get(volDate).put(moneyness, volPrev);
                    }
                }

                if (histVol.containsKey(ticker)) {
                    histVol.get(ticker).put(volDate, volPrev);
                } else {
                    histVol.put(ticker, new ConcurrentSkipListMap<>());
                    histVol.get(ticker).put(volDate, volPrev);
                }

                if (volDate.equals(previousTradingDate)) {
                    if (!ticker.equals("")) {
                        impliedVolMapYtd.put(ticker, volPrev);
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

//        timeLapseMoneynessVolFront.entrySet().forEach(System.out::println);
//        timeLapseMoneynessVolFront.forEach((key, value) ->
//                timeLapseVolFront.put(key, ChinaOptionHelper.getVolByMoneyness(value, 100)));
//        timeLapseVolFront.entrySet().forEach(System.out::println);

        for (LocalDate expiry : expiryList) {
            timeLapseMoneynessVolAllExpiries.get(expiry).forEach((k, v) ->
                    timeLapseVolAllExpiries.get(expiry).put(k, ChinaOptionHelper.getVolByMoneyness(v, 100)));
            System.out.println(" expiry is " + expiry);
            timeLapseMoneynessVolAllExpiries.get(expiry).entrySet().forEach(System.out::println);
        }
    }


    private static double getDeltaFromStrikeExpiry(CallPutFlag f, double strike, LocalDate expiry) {
        String ticker = getOptionTicker(tickerOptionsMap, f, strike, expiry);
        return deltaMap.getOrDefault(ticker, 0.0);
    }

    public static NavigableMap<Double, Double> getStrikeDeltaMapFromVol(NavigableMap<Double, Double> volMap,
                                                                        double stock, LocalDate expiry) {
        NavigableMap<Double, Double> res = new TreeMap<>();
        for (double strike : volMap.keySet()) {
            if (strike < stock) {
                res.put(strike, getDeltaFromStrikeExpiry(CallPutFlag.PUT, strike, expiry));
            } else {
                res.put(strike, getDeltaFromStrikeExpiry(CallPutFlag.CALL, strike, expiry));
            }
        }
        return res;
    }

    private static NavigableMap<Integer, Double> mergePutCallVolsMoneyness(
            NavigableMap<Double, Double> callMap, NavigableMap<Double, Double> putMap, double spot) {
        NavigableMap<Integer, Double> res = new TreeMap<>();

        callMap.forEach((k, v) -> {
            if (k > spot) {
                res.put((int) Math.round(k / spot * 100), v);
            }
        });
        putMap.forEach((k, v) -> {
            if (k < spot) {
                res.put((int) Math.round(k / spot * 100), v);
            }
        });
        return res;
    }


    private static NavigableMap<Double, Double> mergePutCallVols(NavigableMap<Double, Double> callMap
            , NavigableMap<Double, Double> putMap, double spot) {
        NavigableMap<Double, Double> res = new TreeMap<>();

        callMap.forEach((k, v) -> {
            if (k > spot) {
                res.put(k, v);
            }
        });
        putMap.forEach((k, v) -> {
            if (k < spot) {
                res.put(k, v);
            }
        });
        return res;
    }

    private static double getDelta(CallPutFlag f, double s, double k, double v, double t, double r) {
        double d1 = (Math.log(s / k) + (r + 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double nd1 = (new NormalDistribution()).cumulativeProbability(d1);
        return Math.round(100d * (f == CallPutFlag.CALL ? nd1 : (nd1 - 1)));
    }

    private static double getGamma(double s, double k, double v, double t, double r) {
        double d1 = (Math.log(s / k) + (r + 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double gamma = 0.4 * exp(-0.5 * pow(d1, 2)) / (s * v * sqrt(t));
        return Math.round(1000d * gamma) / 1000d;
    }

    private static double get510050Price() {
        try {
            URL allCalls = new URL("http://hq.sinajs.cn/list=sh510050");
            String line;
            Matcher matcher;
            List<String> datalist;
            URLConnection conn = allCalls.openConnection();
            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
                while ((line = reader2.readLine()) != null) {
                    matcher = ChinaOptionHelper.DATA_PATTERN.matcher(line);
                    datalist = Arrays.asList(line.split(","));
                    if (matcher.find()) {
                        currentStockPrice = Double.parseDouble(datalist.get(3));
                        return currentStockPrice;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return 0.0;
    }

    public static void main(String[] argsv) {

        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1500, 1900));
        ChinaOption co = new ChinaOption();
        jf.add(co);
        jf.setLayout(new FlowLayout());
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
        ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);

        ses.scheduleAtFixedRate(co, 0, 5, SECONDS);

    }

    class OptionTableModel extends javax.swing.table.AbstractTableModel {

        @Override
        public int getRowCount() {
            return 200;
        }

        @Override
        public int getColumnCount() {
            return 12;
        }

        @Override
        public String getColumnName(int col) {
            //noinspection Duplicates
            switch (col) {
                case 0:
                    return "Ticker";
                case 1:
                    return "CP";
                case 2:
                    return "Expiry";
                case 3:
                    return "Days to Exp";
                case 4:
                    return "K";
                case 5:
                    return "Price";
                case 6:
                    return "Vol";
                case 7:
                    return "Vol Ytd";
                case 8:
                    return "Vol Chg 1d";
                case 9:
                    return " Moneyness ";
                case 10:
                    return " Delta ";
                default:
                    return "";
            }
        }

        @Override
        public Class getColumnClass(int col) {
            //noinspection Duplicates
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                case 2:
                    return LocalDate.class;
                case 9:
                    return Integer.class;
                case 10:
                    return Integer.class;
                default:
                    return Double.class;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {

            String name = optionListLoaded.size() > rowIn ? optionListLoaded.get(rowIn) : "";

            double strike = tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getStrike() : 0.0;
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getCPString() : "";
                case 2:
                    return tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getExpiryDate() :
                            LocalDate.MIN;
                case 3:
                    return tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getTimeToExpiryDays() : 0.0;
                case 4:
                    return strike;
                case 5:
                    return tickerOptionsMap.containsKey(name) ? optionPriceMap.getOrDefault(name, 0.0) : 0.0;
                case 6:
                    return impliedVolMap.getOrDefault(name, 0.0);
                case 7:
                    return impliedVolMapYtd.getOrDefault(name, 0.0);
                case 8:
                    return impliedVolMap.getOrDefault(name, 0.0) - impliedVolMapYtd.getOrDefault(name, 0.0);
                case 9:
                    return currentStockPrice != 0.0 ? Math.round(100d * strike / currentStockPrice) : 0;
                case 10:
                    return Math.round(deltaMap.getOrDefault(name, 0.0));

                default:
                    return 0.0;
            }
        }
    }
}

abstract class Option {

    private final double strike;
    private final LocalDate expiryDate;
    private final CallPutFlag callput;

    Option(double k, LocalDate t, CallPutFlag f) {
        strike = k;
        expiryDate = t;
        callput = f;
    }

    double getStrike() {
        return strike;
    }

    CallPutFlag getCallOrPut() {
        return callput;
    }

    String getCPString() {
        return callput == CallPutFlag.CALL ? "C" : "P";
    }

    LocalDate getExpiryDate() {
        return expiryDate;
    }


    private double percentageDayLeft(LocalTime lt) {

        if (lt.isBefore(LocalTime.of(9, 30))) {
            return 1.0;
        } else if (lt.isAfter(LocalTime.of(15, 0))) {
            return 0.0;
        }

        if (lt.isAfter(LocalTime.of(11, 30)) && lt.isBefore(LocalTime.of(13, 0))) {
            return 0.5;
        }
        return ((ChronoUnit.MINUTES.between(lt, LocalTime.of(15, 0))) -
                (lt.isBefore(LocalTime.of(11, 30)) ? 90 : 0)) / 240.0;
    }


    double getTimeToExpiry() {
        return (ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)
                + percentageDayLeft(LocalTime.now())) / 365.0d;
    }

    double getTimeToExpiryDays() {
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)
                + percentageDayLeft(LocalTime.now());
    }

    @Override
    public String toString() {
        return Utility.getStr(" strike expiry ", strike, expiryDate);
    }
}

class CallOption extends Option {
    CallOption(double k, LocalDate t) {
        super(k, t, CallPutFlag.CALL);
    }
}

class PutOption extends Option {
    PutOption(double k, LocalDate t) {
        super(k, t, CallPutFlag.PUT);
    }
}


enum CallPutFlag {
    CALL, PUT
}

enum Moneyness {
    ATM, Call25, Put25
}
