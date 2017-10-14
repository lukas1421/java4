package apidemo;

import static apidemo.ChinaDataYesterday.convertTimeToInt;
import static apidemo.ChinaMain.controller;
import static apidemo.ChinaStock.*;
import static utility.Utility.blobify;
import static utility.Utility.getStr;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import static java.time.temporal.ChronoUnit.SECONDS;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import static java.util.Optional.ofNullable;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;

import graph.GraphIndustry;
import saving.ChinaSaveInterface2Blob;
import auxiliary.SimpleBar;
import auxiliary.Strategy;
import auxiliary.VolBar;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import saving.*;
import utility.Utility;

public final class ChinaData extends JPanel {

    //public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> priceMapPlain = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>> priceMapBar = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>> priceMapBarYtd = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>> priceMapBarY2 = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> sizeTotalMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> sizeTotalMapYtd = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> sizeTotalMapY2 = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Strategy>> strategyTotalMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, VolBar>> bidMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, VolBar>> askMap = new ConcurrentHashMap<>();
    //static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> bidTotalMap = new ConcurrentHashMap<>();
    //static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> askTotalMap = new ConcurrentHashMap<>();

    static ConcurrentHashMap<Integer, ConcurrentHashMap<String, ?>> saveMap = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer, ConcurrentHashMap<String, ?>> saveMap2 = new ConcurrentHashMap<>();

    public static volatile Map<String, Double> priceMinuteSharpe = new HashMap<>();
    public static volatile Map<String, Double> wtdSharpe = new HashMap<>();

    static volatile Map<Integer, LocalDate> dateMap = new HashMap<>();
    static volatile Map<LocalDate, Double> ftseOpenMap = new HashMap<>();

    public static List<LocalTime> tradeTime = new LinkedList<>();
    public static List<LocalTime> tradeTimePure = new LinkedList<>();
    static BarModel m_model;

    LocalTime lastSaveTime = Utility.AM929T;
    LocalTime lastLoadTime = Utility.AM929T;
    public static LocalTime lastDataTime = Utility.AM929T;

    static File source = new File(ChinaMain.GLOBALPATH + "CHINASS.ser");
    static File backup = new File(ChinaMain.GLOBALPATH + "CHINABackup.ser");
    static File source2 = new File(ChinaMain.GLOBALPATH + "CHINASS2.ser");
    static File backup2 = new File(ChinaMain.GLOBALPATH + "CHINABackup2.ser");
    static File priceDetailedSource = new File(ChinaMain.GLOBALPATH + "priceDetailed.ser");
    static File priceDetailedBackup = new File(ChinaMain.GLOBALPATH + "priceDetailedBackup.ser");
    static File priceBarSource = new File(ChinaMain.GLOBALPATH + "priceBar.ser");
    static File priceBarBackup = new File(ChinaMain.GLOBALPATH + "priceBarBackup.ser");
    static File priceBarYtdSource = new File(ChinaMain.GLOBALPATH + "priceBarYtd.ser");

    static File shcompSource = new File(ChinaMain.GLOBALPATH + "shcomp.txt");
    public static JButton btnSave2;
    static ExecutorService es = Executors.newCachedThreadPool();
    static final Predicate<? super Entry<LocalTime, Double>> IS_OPEN = e -> e.getKey().isAfter(Utility.AM929T) && e.getValue() != 0.0;

    ChinaData() {
        LocalTime lt = LocalTime.of(9, 19);
        while (lt.isBefore(LocalTime.of(15, 1))) {
            if (lt.getHour() == 11 && lt.getMinute() == 31) {
                lt = LocalTime.of(13, 0);
            }
            tradeTime.add(lt);
            if (lt.isAfter(LocalTime.of(9, 29))) {
                tradeTimePure.add(lt);
            }
            lt = lt.plusMinutes(1);
        }

        symbolNames.forEach((String v) -> {
            //priceMapPlain.put(v, new ConcurrentSkipListMap<>());
            priceMapBar.put(v, new ConcurrentSkipListMap<>());
            priceMapBarYtd.put(v, new ConcurrentSkipListMap<>());
            priceMapBarY2.put(v, new ConcurrentSkipListMap<>());
            sizeTotalMap.put(v, new ConcurrentSkipListMap<>());
            sizeTotalMapYtd.put(v, new ConcurrentSkipListMap<>());
            sizeTotalMapY2.put(v, new ConcurrentSkipListMap<>());
            bidMap.put(v, new ConcurrentSkipListMap<>());
            askMap.put(v, new ConcurrentSkipListMap<>());
            strategyTotalMap.put(v, new ConcurrentSkipListMap<>());
            strategyTotalMap.get(v).put(LocalTime.MIN, new Strategy());
            priceMinuteSharpe.put(v, 0.0);
        });

        m_model = new BarModel();
        JTable tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                if (isCellSelected(Index_row, Index_col)) {
                    comp.setBackground(Color.GREEN);
                } else if (Index_row % 2 == 0) {
                    comp.setBackground(Color.lightGray);
                } else {
                    comp.setBackground(Color.white);
                }
                return comp;
            }
        };
        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    SwingUtilities.invokeLater(() -> {
                        m_model.fireTableDataChanged();
                    });
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = ChinaMain.GLOBALWIDTH;
                return d;
            }
        };

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        tab.setAutoCreateRowSorter(true);

        JPanel jp = new JPanel();
        jp.setLayout(new GridLayout(2, 1));
        JPanel buttonUpPanel = new JPanel();
        JPanel buttonDownPanel = new JPanel();
        buttonUpPanel.setLayout(new FlowLayout());
        buttonDownPanel.setLayout(new FlowLayout());
        jp.add(buttonUpPanel);
        jp.add(buttonDownPanel);

        JButton btnSave = new JButton("Save1");
        JButton btnSaveBar = new JButton("Save Bar");
        JButton saveHibernate = new JButton("save hib");
        JButton saveOHLCButton = new JButton("Save OHLC");
        JButton btnSaveBarYtd = new JButton("Save Bar YTD");
        JButton hibMorning = new JButton("HibMorning");
        JButton saveBidAsk = new JButton("Save BidAsk");
        JButton loadHibBidAsk = new JButton("Load BidAsk");
        JButton saveStratButton = new JButton("Save Strat");
        JButton loadStratButton = new JButton("Load Strat");
        JButton loadHibGenPriceButton = new JButton("Load hib");
        JButton loadHibernateY = new JButton("Load hib Y");
        JButton btnLoadBarYtd = new JButton("Load Bar YTD");
        JButton btnLoadBar = new JButton("Load Bar");
        JButton shcompToText = new JButton("上证");
        JButton closeHib = new JButton("Close Hib");
        JButton getFXButton = new JButton("FX");
        JButton buildA50Button = new JButton("build A50");
        JButton getSGXA50HistButton = new JButton("SGXA50");
        JButton getSGXA50TodayButton = new JButton("Fut Today");
        JButton tdxButton = new JButton("TDX");
        JButton retrieveAllButton = new JButton("RetrieveAll");
        JButton retrieveTodayButton = new JButton("RetrieveToday");
        JButton outputPricesButton = new JButton("Output prices");

        JButton saveHibYtdButton = new JButton("Save Hib Ytd");
        JButton saveHibY2Button = new JButton("Save Hib Y2");

        //buttonUpPanel.add(btnSave);            buttonUpPanel.add(Box.createHorizontalStrut(10));
        //buttonUpPanel.add(btnSaveBar);         buttonUpPanel.add(Box.createHorizontalStrut(10));
        buttonUpPanel.add(saveHibernate);
        buttonUpPanel.add(Box.createHorizontalStrut(10));
        buttonUpPanel.add(saveOHLCButton);
        buttonUpPanel.add(Box.createHorizontalStrut(10));
        //buttonUpPanel.add(btnSaveBarYtd);
        //buttonUpPanel.add(Box.createHorizontalStrut(10));
        //buttonUpPanel.add(saveBidAsk);
        //buttonUpPanel.add(Box.createHorizontalStrut(10));
        //buttonUpPanel.add(saveStratButton);
        //buttonUpPanel.add(Box.createHorizontalStrut(10));
        buttonUpPanel.add(saveHibYtdButton);
        buttonUpPanel.add(Box.createHorizontalStrut(10));
        buttonUpPanel.add(saveHibY2Button);
        buttonUpPanel.add(Box.createHorizontalStrut(150));

        buttonDownPanel.add(loadHibGenPriceButton);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(loadHibernateY);
        buttonDownPanel.add(Box.createHorizontalStrut(20));
        //buttonDownPanel.add(loadHibBidAsk);
        //buttonDownPanel.add(Box.createHorizontalStrut(10));
        //buttonDownPanel.add(loadStratButton);
        //buttonDownPanel.add(Box.createHorizontalStrut(10));
        //buttonDownPanel.add(btnLoadBarYtd);
        //buttonDownPanel.add(Box.createHorizontalStrut(10));
        //buttonDownPanel.add(btnLoadBar);         buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(shcompToText);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(hibMorning);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(closeHib);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(getFXButton);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(buildA50Button);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(getSGXA50HistButton);
        buttonDownPanel.add(getSGXA50TodayButton);
        buttonDownPanel.add(Box.createHorizontalStrut(10));
        buttonDownPanel.add(tdxButton);
        buttonDownPanel.add(retrieveAllButton);
        buttonDownPanel.add(retrieveTodayButton);
        buttonDownPanel.add(outputPricesButton);
        buttonDownPanel.add(Box.createHorizontalStrut(10));

        add(jp, BorderLayout.NORTH);


        btnSaveBarYtd.addActionListener(al -> {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(priceBarYtdSource))) {
                oos.writeObject(priceMapBarYtd);
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Saving Ytd done" + LocalTime.now());
        });

//        btnSaveBar.addActionListener(al -> {
//            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(priceBarSource))) {
//                oos.writeObject(trimMap(priceMapBar));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            System.out.println(" saving bar done");
//        });
        btnLoadBar.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(priceBarSource))) {
                    priceMapBar = (ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>>) ois.readObject();
                } catch (IOException | ClassNotFoundException e3) {
                    e3.printStackTrace();
                }
            }, es);
        });

        saveHibernate.addActionListener(al -> {
            withHibernate();
        });
        saveOHLCButton.addActionListener(al -> {
            saveChinaOHLC();
        });
        loadHibGenPriceButton.addActionListener(al -> {
            Hibtask.loadHibGenPrice();
        });
        hibMorning.addActionListener(al -> {
            int ans = JOptionPane.showConfirmDialog(null, "are you sure", "", JOptionPane.YES_NO_OPTION);
            if (ans == JOptionPane.YES_OPTION) {
                Hibtask.hibernateMorningTask();
            } else {
                System.out.println(" nothing done ");
            }
        });
        saveBidAsk.addActionListener(al -> {
            hibSaveGenBidAsk();
        });
        loadHibBidAsk.addActionListener(al -> {
            loadHibGenBidAsk();
        });
        saveStratButton.addActionListener(al -> {
            saveHibGen(strategyTotalMap, new ConcurrentHashMap<>(), ChinaSaveStrat.getInstance());
        });
        saveHibYtdButton.addActionListener(al -> {
            hibSaveGenYtd();
        });
        saveHibY2Button.addActionListener(al -> {
            hibSaveGenY2();
        });

        loadStratButton.addActionListener(al -> {
            hibLoadStrat();
        });
        btnLoadBarYtd.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(priceBarYtdSource))) {
                    priceMapBarYtd = (ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>>) ois.readObject();
                } catch (IOException | ClassNotFoundException e3) {
                    e3.printStackTrace();
                }
            }, es).whenComplete((ok, ex) -> System.out.println("loading Bar done" + LocalTime.now()));
        });

        loadHibernateY.addActionListener(al -> {
            loadHibernateYesterday();
        });

        shcompToText.addActionListener(al -> {
            writeShcomp2();
        });
        closeHib.addActionListener(al -> {
            Hibtask.closeHibSessionFactory();
        });

        getFXButton.addActionListener(al -> {
            ChinaStockHelper.getHistoricalFX();
        });

        buildA50Button.addActionListener(al -> {
            System.out.println(" building A50 ");
            System.out.println(" date map " + dateMap);
            System.out.println(" ftse open map " + ftseOpenMap);

            ChinaStockHelper.buildA50FromSS(ftseOpenMap.get(dateMap.get(2)));
            ChinaStockHelper.buildA50Gen(ftseOpenMap.get(dateMap.get(1)), ChinaData.priceMapBarYtd, ChinaData.sizeTotalMapYtd, new HashMap<>());
            ChinaStockHelper.buildA50Gen(ftseOpenMap.get(dateMap.get(0)), ChinaData.priceMapBarY2, ChinaData.sizeTotalMapY2, new HashMap<>());
            //ChinaStockHelper.buildA50FromSS();
            //ChinaStockHelper.buildA50FromSSYtdY2();
        });

        getSGXA50HistButton.addActionListener(l -> {
            CompletableFuture.runAsync(() -> {
                controller().getSGXA50Historical(20000, ChinaData::handleSGX50HistData, 7);
            });
        });

        getSGXA50TodayButton.addActionListener(l -> {
            CompletableFuture.runAsync(() -> {
                controller().getSGXA50Historical(50000, ChinaData::handleSGXDataToday, 1);
            });
        });

        tdxButton.addActionListener(l -> {
            getFromTDX(dateMap.get(2), dateMap.get(1), dateMap.get(0));
        });

        retrieveAllButton.addActionListener(l -> {
            retrieveDataAll();
        });

        retrieveTodayButton.addActionListener(l -> {
            getTodayTDX(LocalDate.now());
        });

        outputPricesButton.addActionListener(l -> {
            outputPrices();
        });

    }

    public static void outputPrices() {
        System.out.println(" outputting prices");
        File output = new File(ChinaMain.GLOBALPATH + "pricesTodayYtd.csv");
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, false))) {
            symbolNames.forEach(s -> {
                try {
                    out.append(Utility.getStrComma(s, priceMap.getOrDefault(s, 0.0), closeMap.getOrDefault(s, 0.0)));
                    out.newLine();
                } catch (IOException ex) {
                    Logger.getLogger(ChinaData.class.getName()).log(Level.SEVERE, null, ex);
                }
            });

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void outputRecentTradingDate() {
        System.out.println(" most recent trading date " + SinaStock.mostRecentTradingDay.toString());
        File output = new File(ChinaMain.GLOBALPATH + "mostRecentTradingDate.txt");

        //MorningTask.clearFile(output);
        MorningTask.simpleWriteToFile(SinaStock.mostRecentTradingDay.toString(),false,output);

    }

    static void getTodayTDX(LocalDate dat) {
        CompletableFuture.runAsync(() -> {
            ChinaStockHelper.getFilesFromTDXGen(dat, ChinaData.priceMapBar, ChinaData.sizeTotalMap);
        });
    }

    static void getFromTDX(LocalDate today, LocalDate ytd, LocalDate y2) {

        CompletableFuture.runAsync(() -> {
            ChinaStockHelper.getFilesFromTDXGen(today, ChinaData.priceMapBar, ChinaData.sizeTotalMap);
        });
        CompletableFuture.runAsync(() -> {
            ChinaStockHelper.getFilesFromTDXGen(ytd, ChinaData.priceMapBarYtd, ChinaData.sizeTotalMapYtd);
        });

        CompletableFuture.runAsync(() -> {
            ChinaStockHelper.getFilesFromTDXGen(y2, ChinaData.priceMapBarY2, ChinaData.sizeTotalMapY2);
        });
    }

    public static void retrieveDataAll() {
//        //should be strictly 3 lines for Y2 Ytd today (pmb)
//        LocalDate today;
//        LocalDate ytd;
//        LocalDate y2;

        int lineNo = 0;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(ChinaMain.GLOBALPATH + "ftseA50Open.txt"), "gbk"))) {
            String line;
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                dateMap.put(lineNo, LocalDate.parse(al1.get(0)));
                ftseOpenMap.put(LocalDate.parse(al1.get(0)), Double.parseDouble(al1.get(1)));
                lineNo++;
                System.out.println(" line is " + lineNo);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        System.out.println(" get date Map  " + dateMap.toString());
        System.out.println(" get ftse open map " + ftseOpenMap.toString());

        CompletableFuture.runAsync(() -> {
            controller().getSGXA50Historical(20000, ChinaData::handleSGX50HistData, 7);
        });

        // get from tdx
        getFromTDX(dateMap.get(2), dateMap.get(1), dateMap.get(0));
        //futures

        //build A50
        //ChinaStockHelper.buildA50FromSS(openMap.get(dateMap.get(2)));
        //ChinaStockHelper.buildA50Gen(openMap.get(dateMap.get(1)), ChinaData.priceMapBarYtd, ChinaData.sizeTotalMapYtd, closeMap);
        //ChinaStockHelper.buildA50Gen(openMap.get(dateMap.get(0)), ChinaData.priceMapBarY2, ChinaData.sizeTotalMapY2, ChinaDataYesterday.closeMapY);
    }

    public static void loadPriceBar() {
        CompletableFuture.runAsync(() -> {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(priceBarSource))) {
                priceMapBar = (ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>>) ois.readObject();
            } catch (IOException | ClassNotFoundException ex) {
                ex.printStackTrace();
            }
        }, es).whenComplete((ok, ex) -> {
            System.out.println("loading price bar" + LocalTime.now());
            ChinaSizeRatio.computeSizeRatio();
            System.out.println(" computing Size Ratio");
        });
    }

    static ConcurrentHashMap<String, TreeMap<LocalTime, Double>> convertMap(ConcurrentHashMap<String, TreeMap<LocalTime, Long>> mapFrom) {
        ConcurrentHashMap<String, TreeMap<LocalTime, Double>> mpTo = new ConcurrentHashMap<>();

        mapFrom.keySet().forEach(key -> {
            mpTo.put(key, new TreeMap<>());
            mapFrom.get(key).keySet().forEach(t -> {
                mpTo.get(key).put(t, mapFrom.get(key).get(t) + 0.0);
            });
        });
        return mpTo;
    }

    public static void withHibernate() {
//        Properties p = System.getProperties();
//        p.put("derby.locks.waitTimeout", "20");
//        System.out.println( " get properties " + p.getProperty("derby.locks.waitTimeout"));
        if (priceMapBar.entrySet().stream().mapToInt(e -> e.getValue().size()).max().orElse(0) > 0) { // && LocalTime.now().isAfter(AM914T)
            saveHibGen(priceMapBar, sizeTotalMap, ChinaSave.getInstance());
        }
    }

    //static void hibSave
    public void hibSaveGenYtd() {
        saveHibGen(priceMapBarYtd, sizeTotalMapYtd, ChinaSaveYest.getInstance());
    }

    public void hibSaveGenY2() {
        saveHibGen(priceMapBarY2, sizeTotalMapY2, ChinaSaveY2.getInstance());
    }

    public void hibSaveGenBidAsk() {
        saveHibGen(bidMap, askMap, ChinaSaveBidAsk.getInstance());
    }

    public void hibSaveStrat() {
        saveHibGen(strategyTotalMap, new ConcurrentHashMap<>(), ChinaSaveStrat.getInstance());
    }

    public void hibLoadStrat() {
        CompletableFuture.runAsync(() -> {
            Hibtask.loadHibGen(ChinaSaveStrat.getInstance());
        });
    }

    public void loadHibGenBidAsk() {
        Hibtask.loadHibGen(ChinaSaveBidAsk.getInstance());
    }

    public static void saveHibGen(Map<String, ? extends NavigableMap<LocalTime, ?>> mp, Map<String, ? extends NavigableMap<LocalTime, ?>> mp2, ChinaSaveInterface2Blob saveclass) {
        LocalTime start = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        SessionFactory sessionF = HibernateUtil.getSessionFactory();

        CompletableFuture.runAsync(() -> {

            try (Session session = sessionF.openSession()) {
                try {
                    session.getTransaction().begin();
                    session.createQuery("DELETE from " + saveclass.getClass().getName()).executeUpdate();
                    AtomicLong i = new AtomicLong(0L);
                    mp.keySet().forEach(name -> {
                        if (mp2.isEmpty() || mp2.containsKey(name)) {
                            ChinaSaveInterface2Blob cs = saveclass.createInstance(name);
                            cs.setFirstBlob(blobify(mp.get(name), session));
                            cs.setSecondBlob(blobify(mp2.get(name), session));
                            session.save(cs);
                            if (i.get() % 100 == 0) {
                                session.flush();
                            }
                            i.incrementAndGet();
                        }
                    });
                    session.getTransaction().commit();
                    session.close();
                } catch (org.hibernate.exception.LockAcquisitionException x) {
                    //x.printStackTrace();
                    //session.getTransaction().rollback();
                    session.close();
                }
            }
        }).thenAccept(
                v -> {
                    ChinaMain.updateSystemNotif(Utility.getStr("存", saveclass.getSimpleName(),
                            LocalTime.now().truncatedTo(ChronoUnit.SECONDS), " Taken: ",
                            SECONDS.between(start, LocalTime.now().truncatedTo(ChronoUnit.SECONDS))));
                    System.out.println(getStr(" done saving ", LocalTime.now()));
                }
        );
    }

    public static void loadHibernateYesterday() {

        CompletableFuture.runAsync(() -> {
            Hibtask.loadHibGen(ChinaSaveYest.getInstance());
        }).thenRun(() -> {
            CompletableFuture.runAsync(() -> {
                GraphIndustry.getIndustryPriceYtd(priceMapBarYtd);
            });
            CompletableFuture.runAsync(() -> {
                Utility.getIndustryVolYtd(sizeTotalMapYtd);
            });
        }).thenAccept(
                v -> {
                    ChinaMain.updateSystemNotif(Utility.getStr(" Loading HIB-Y done ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
                }
        );

        CompletableFuture.runAsync(() -> {
            Hibtask.loadHibGen(ChinaSaveY2.getInstance());
        }).thenRun(() -> {
            CompletableFuture.runAsync(() -> {
                GraphIndustry.getIndustryPriceYtd(priceMapBarY2);
            });
            CompletableFuture.runAsync(() -> {
                Utility.getIndustryVolYtd(sizeTotalMapY2);
            });
        }).thenAccept(
                v -> {
                    ChinaMain.updateSystemNotif(Utility.getStr(" Loading HIB-Y2 done ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
                }
        );
    }

    public void loadHibernateY2() {
        CompletableFuture.runAsync(() -> {
            Hibtask.loadHibGen(ChinaSaveY2.getInstance());
        }).thenRun(() -> {
            CompletableFuture.runAsync(() -> {
                GraphIndustry.getIndustryPriceYtd(priceMapBarY2);
            });
            CompletableFuture.runAsync(() -> {
                Utility.getIndustryVolYtd(sizeTotalMapY2);
            });
        });
    }

    public static void saveChinaOHLC() {
        CompletableFuture.runAsync(() -> {
            SessionFactory sessionF = HibernateUtil.getSessionFactory();
            try (Session session = sessionF.openSession()) {
                session.getTransaction().begin();
                try {
                    symbolNames.forEach(name -> {
                        if (Utility.noZeroArrayGen(name, openMap, maxMap, minMap, priceMap, closeMap, sizeMap)) {
                            ChinaSaveOHLCYV c = new ChinaSaveOHLCYV(name, openMap.get(name), maxMap.get(name), minMap.get(name), priceMap.get(name), closeMap.get(name), sizeMap.get(name).intValue());
                            session.saveOrUpdate(c);
                        } else if (Utility.NO_ZERO.test(closeMap, name)) {
                            System.out.println("only close available " + name);
                            ChinaSaveOHLCYV c = new ChinaSaveOHLCYV(name, closeMap.get(name));
                            session.saveOrUpdate(c);
                        } else {
                            System.out.println(" chinasaveohcl all 0 " + name);
                            ChinaSaveOHLCYV c = new ChinaSaveOHLCYV(name, 0.0);
                            session.saveOrUpdate(c);
                        }
                    });
                    session.getTransaction().commit();
                } catch (org.hibernate.exception.LockAcquisitionException x) {
                    x.printStackTrace();
                    session.getTransaction().rollback();
                    session.close();
                }
            }
        }).thenAccept(
                v -> {
                    ChinaMain.updateSystemNotif(Utility.getStr(" 存 OHLC ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
                    System.out.println(getStr(" done saving ", LocalTime.now()));
                }
        );
    }

    public static void writeShcomp() {
        String ticker = "sh000001";
        SimpleBar sb;
        int time;
        LocalDate today = LocalDate.now();
        LocalDate lastDay;
        lastDay = (today.getDayOfWeek() == DayOfWeek.MONDAY)
                ? today.minusDays(3) : today.minusDays(1);

        System.out.println(" today+lastday " + today + " " + lastDay);

        if (ChinaStock.NORMAL_STOCK.test(ticker)) {
            try (BufferedWriter out = new BufferedWriter(new FileWriter(shcompSource))) {
                out.write("D" + "\t" + "T" + "\t" + "O" + "\t" + "H" + "\t" + "L" + "\t" + "C");
                out.newLine();

                for (LocalTime t : ChinaData.priceMapBar.get(ticker).keySet()) {
                    sb = ChinaData.priceMapBar.get(ticker).get(t);
                    time = convertTimeToInt(t);
                    if (t.isAfter(LocalTime.of(9, 29, 59))) {
                        out.write(lastDay.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "\t" + time + "\t" + sb.getOpen() + "\t" + sb.getHigh() + "\t" + sb.getLow() + "\t" + sb.getClose());
                        out.newLine();
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        System.out.println(" done writing to shcomp ");
    }

    static void writeShcomp2() {

        CompletableFuture.runAsync(() -> {

            String ticker = "sh000001";
            if (ChinaStock.NORMAL_STOCK.test(ticker)) {
                ConcurrentSkipListMap<LocalTime, SimpleBar> nm = priceMapBar.get(ticker);
                double open = nm.floorEntry(Utility.AMOPENT).getValue().getOpen();
                double v931 = nm.floorEntry(LocalTime.of(9, 31)).getValue().getClose();
                double v935 = nm.floorEntry(LocalTime.of(9, 35)).getValue().getClose();
                double v940 = nm.floorEntry(LocalTime.of(9, 40)).getValue().getClose();
                double amClose = nm.floorEntry(LocalTime.of(11, 30)).getValue().getClose();
                double pmOpen = nm.ceilingEntry(LocalTime.of(13, 0)).getValue().getOpen();
                double pm1310 = nm.ceilingEntry(LocalTime.of(13, 10)).getValue().getClose();
                double pmClose = nm.floorEntry(LocalTime.of(15, 0)).getValue().getClose();
                double amMax = ChinaStock.GETMAX.applyAsDouble(ticker, Utility.AM_PRED);
                double amMin = ChinaStock.GETMIN.applyAsDouble(ticker, Utility.AM_PRED);
                double pmMax = ChinaStock.GETMAX.applyAsDouble(ticker, Utility.PM_PRED);
                double pmMin = ChinaStock.GETMIN.applyAsDouble(ticker, Utility.PM_PRED);
                int amMaxT = convertTimeToInt(GETMAXTIME.apply(ticker, Utility.AM_PRED));
                int amMinT = convertTimeToInt(GETMINTIME.apply(ticker, Utility.AM_PRED));
                int pmMaxT = convertTimeToInt(GETMAXTIME.apply(ticker, Utility.PM_PRED));
                int pmMinT = convertTimeToInt(GETMINTIME.apply(ticker, Utility.PM_PRED));

                try (BufferedWriter out = new BufferedWriter(new FileWriter(shcompSource))) {
                    out.append(Utility.getStrTabbed("AmOpen", "931", "935", "940", "AmClose", "AmMax", "AmMin", "AmMaxT", "AmMinT",
                            "PmOpen", "Pm1310", "PmClose", "PmMax", "PmMin", "PmMaxT", "PmMinT"));

                    out.newLine();
                    out.append(Utility.getStrTabbed(open, v931, v935, v940, amClose, amMax, amMin, amMaxT, amMinT,
                            pmOpen, pm1310, pmClose, pmMax, pmMin, pmMaxT, pmMinT));
                } catch (IOException x) {
                    x.printStackTrace();
                }
            }
        }).thenAccept(
                v -> {
                    ChinaMain.updateSystemNotif(Utility.getStr(" Write SHCOMP ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
                }
        );

    }

    public static double getVolZScore(String name) {
        if (Utility.normalMapGen(name, sizeTotalMap)) {
            NavigableMap<LocalTime, Double> tm = ChinaData.sizeTotalMap.get(name);
            NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
            tm.keySet().forEach((LocalTime t) -> {
                res.put(t, tm.get(t) - ofNullable(tm.lowerEntry(t)).map(Entry::getValue).orElse(0.0));
            });
            double last = res.lastEntry().getValue();
            final double average = res.entrySet().stream().filter(IS_OPEN)
                    .mapToDouble(Map.Entry::getValue).average().orElse(0.0);

            double sd = Math.sqrt(res.entrySet().stream().filter(IS_OPEN)
                    .mapToDouble(e -> Math.pow(e.getValue() - average, 2)).average().orElse(0.0));

            return (sd != 0.0) ? (last - average) / sd : 0.0;
        }
        return 0.0;
    }

    public static void handleSGX50HistData(String date, double open, double high, double low, double close, int volume) {

//        LocalDate currDate = LocalDate.now();
//        long daysToSubtract = (currDate.getDayOfWeek().equals(DayOfWeek.MONDAY)) ? 3L : 1L;
//        long daysToSubtract1 = (currDate.getDayOfWeek().equals(DayOfWeek.MONDAY)) ? 4L : 2L;
//        LocalDate ytd = currDate.minusDays(daysToSubtract);
//        LocalDate y2 = currDate.minusDays(daysToSubtract1);
        LocalDate currDate = ChinaData.dateMap.get(2);
        LocalDate ytd = ChinaData.dateMap.get(1);
        LocalDate y2 = ChinaData.dateMap.get(0);

        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            System.out.println(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            System.out.println(" ld " + ld);
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

            if (ld.equals(currDate) && ((lt.isAfter(LocalTime.of(9, 29)) && lt.isBefore(LocalTime.of(11, 31))) || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                System.out.println(getStr(dt, open, high, low, close));
                double previousVol = Optional.ofNullable(ChinaData.sizeTotalMapYtd.get("SGXA50").lowerEntry(lt)).map(Entry::getValue).orElse(0.0);
                ChinaData.priceMapBar.get("SGXA50").put(lt, new SimpleBar(open, high, low, close));
                ChinaData.sizeTotalMap.get("SGXA50").put(lt, volume * 1d + previousVol);
            }

            if (ld.equals(ytd) && ((lt.isAfter(LocalTime.of(9, 29)) && lt.isBefore(LocalTime.of(11, 31))) || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                System.out.println(getStr(dt, open, high, low, close));
                ChinaData.priceMapBarYtd.get("SGXA50").put(lt, new SimpleBar(open, high, low, close));
                double previousVol = Optional.ofNullable(ChinaData.sizeTotalMapYtd.get("SGXA50").lowerEntry(lt)).map(Entry::getValue).orElse(0.0);
                ChinaData.sizeTotalMapYtd.get("SGXA50").put(lt, volume * 1d + previousVol);
            }

            if (ld.equals(y2) && ((lt.isAfter(LocalTime.of(9, 29)) && lt.isBefore(LocalTime.of(11, 31))) || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
                //SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                System.out.println(getStr(dt, open, high, low, close));
                ChinaData.priceMapBarY2.get("SGXA50").put(lt, new SimpleBar(open, high, low, close));
                double previousVol = Optional.ofNullable(ChinaData.sizeTotalMapY2.get("SGXA50").lowerEntry(lt)).map(Entry::getValue).orElse(0.0);
                ChinaData.sizeTotalMapY2.get("SGXA50").put(lt, volume * 1d + previousVol);
            }
        } else {
            System.out.println(getStr(date, open, high, low, close));
        }
    }

    public static void handleSGXDataToday(String date, double open, double high, double low, double close, int volume) {
        //System.out.println(" handling SGX today ");

        LocalDate currDate = LocalDate.now();

        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            //System.out.println(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            //System.out.println(" ld " + ld);
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

            if (ld.equals(currDate) && ((lt.isAfter(LocalTime.of(8, 59)) && lt.isBefore(LocalTime.of(11, 31))) || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
                //SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                System.out.println(getStr(dt, open, high, low, close));
                double previousVol = Optional.ofNullable(ChinaData.sizeTotalMapYtd.get("SGXA50").lowerEntry(lt)).map(Entry::getValue).orElse(0.0);
                ChinaData.priceMapBar.get("SGXA50").put(lt, new SimpleBar(open, high, low, close));
                ChinaData.sizeTotalMap.get("SGXA50").put(lt, volume * 1d + previousVol);
            }
        } else {
            System.out.println(getStr(date, open, high, low, close));
        }
    }

//    @Override
//    public void handleHist(String name, String date, double open, double high, double low, double close) {
//        System.out.println(" not used ");
//    }
//
//    @Override
//    public void actionUponFinish(String name) {
//        System.out.println(" not used ");
//    }
    class BarModel extends javax.swing.table.AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return tradeTime.size() + 2;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "T";
                case 1:
                    return "name";
                default:
                    return tradeTime.get(col - 2).toString();
            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                default:
                    return Double.class;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            String name = (rowIn < symbolNamesFull.size()) ? symbolNamesFull.get(rowIn) : "";
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return nameMap.get(name);
                default:
                    try {
                        if (priceMapBar.containsKey(name)) {
                            return (priceMapBar.get(name).containsKey(tradeTime.get(col - 2))) ? priceMapBar.get(name).get(tradeTime.get(col - 2)).getClose() : 0.0;
                        }
                    } catch (Exception ex) {
                        System.out.println(" name in china map " + name);
                        System.out.println(" priceMapBar " + priceMapBar.get(name));
                        ex.printStackTrace();
                    }
                    return null;
            }
        }
    }
}
