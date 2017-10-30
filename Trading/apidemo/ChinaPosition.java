package apidemo;

import static apidemo.ChinaData.priceMapBar;
import static java.util.stream.Collectors.*;
import static utility.Utility.BAR_HIGH;
import static utility.Utility.BAR_LOW;
import static apidemo.ChinaStock.closeMap;
import static apidemo.ChinaStock.nameMap;
import static apidemo.ChinaStock.openMap;
import static apidemo.ChinaStock.priceMap;
import static apidemo.ChinaStock.symbolNames;
import static utility.Utility.getStr;

import TradeType.FutureTrade;
import TradeType.MarginTrade;
import TradeType.NormalTrade;
import TradeType.Trade;
import auxiliary.SimpleBar;
import client.CommissionReport;
import client.Contract;
import client.Execution;
import client.ExecutionFilter;
import controller.ApiController;
import graph.GraphPnl;
import handler.FutPositionHandler;
import handler.HistoricalHandler;
import utility.SharpeUtility;
import utility.Utility;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import java.util.stream.Stream;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

public class ChinaPosition extends JPanel implements HistoricalHandler {

    static String line;
    volatile static Map<String, Integer> openPositionMap = new HashMap();
    static Map<String, Double> costMap = new HashMap();
    public volatile static Map<String, ConcurrentSkipListMap<LocalTime, ? super Trade>> tradesMap = new ConcurrentHashMap<>();
    static Map<String, ConcurrentSkipListMap<LocalTime, Double>> tradePnlMap = new ConcurrentHashMap<>();
    public static volatile HashMap<String, Double> wtdMaxMap = new HashMap<>();
    public static volatile HashMap<String, Double> wtdMinMap = new HashMap<>();
    private static String selectedNameStock;
    static double beginningNAV;
    static volatile NavigableMap<String, Double> ytdPNLMap;
    static volatile NavigableMap<LocalTime, Double> mtmPNLMap;
    static volatile NavigableMap<LocalTime, Double> tradePNLMap = new ConcurrentSkipListMap<>();
    static volatile NavigableMap<LocalTime, Double> netPNLMap;
    static volatile NavigableMap<LocalTime, Double> mapToDisplay = mtmPNLMap;
    static volatile NavigableMap<LocalTime, Double> boughtPNLMap = new ConcurrentSkipListMap<>();
    static volatile NavigableMap<LocalTime, Double> soldPNLMap = new ConcurrentSkipListMap<>();
    static volatile NavigableMap<LocalTime, Double> boughtDeltaMap;
    static volatile NavigableMap<LocalTime, Double> soldDeltaMap;
    static volatile NavigableMap<LocalTime, Double> netDeltaMap;
    static volatile NavigableMap<LocalTime, Double> mtmDeltaMap;
    static volatile NavigableMap<String, Double> benchExposureMap;
    static volatile Map<String, Double> pureMtmMap;
    static Map<String, Double> fxMap = new HashMap<>();
    static volatile double mtmDeltaSharpe;
    static volatile double minuteNetPnlSharpe;

    static List<String> dataList;
    static BarModel_POS m_model;
    static volatile boolean filterOn = false;
    static int modelRow;

    static double netYtdPnl = 0.0;
    static double todayNetPnl = 0.0;
    static double boughtDelta = 0.0;
    static double openDelta = 0.0;
    static double netDelta = 0.0;
    static double soldDelta = 0.0;
    static volatile LinkedList<String> chg5m = new LinkedList<>();
    static volatile LinkedList<String> topKiyodo = new LinkedList<>();

    static GraphPnl gpnl = new GraphPnl();

    private final int OPEN_POS_COL = 2;
    private final int NET_POS_COL = 21;

    static TableRowSorter<BarModel_POS> sorter;
    static ScheduledExecutorService ex = Executors.newScheduledThreadPool(10);

    //xu pos
    static volatile int xuOpenPostion;
    public static volatile int xuCurrentPosition;
    static volatile int xuBotPos;
    static volatile int xuSoldPos;

    static volatile double xuOpenPrice;
    static volatile double xuPreviousClose;

    static volatile Predicate<? super Map.Entry<String, ?>> NO_FUT_PRED = m -> !m.getKey().equals("SGXA50");
    static volatile Predicate<? super Map.Entry<String, ?>> GEN_MTM_PRED = m -> true;
    //static Predicate<Map<String, ?>> NO_FUT_PRED = m-> m.get

    @Override
    public void scrollRectToVisible(Rectangle aRect) {
        super.scrollRectToVisible(aRect); //To change body of generated methods, choose Tools | Templates.
    }

    public static volatile boolean buySellTogether = true;

    ChinaPosition() {

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(ChinaMain.GLOBALPATH + "fx.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                //System.out.println(" al1 " + al1);
                fxMap.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
            //System.out.println(" fx map is " + fxMap);
        } catch (IOException x) {
            x.printStackTrace();
        }

        symbolNames.forEach((String name) -> {
            tradesMap.put(name, new ConcurrentSkipListMap<>());
            tradePnlMap.put(name, new ConcurrentSkipListMap<>());
            wtdMaxMap.put(name, 0.0);
            wtdMinMap.put(name, Double.MAX_VALUE);
        });

        m_model = new BarModel_POS();
        JTable tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                if (isCellSelected(Index_row, Index_col)) {
                    modelRow = this.convertRowIndexToModel(Index_row);
                    selectedNameStock = ChinaStock.symbolNames.get(modelRow);
                    mtmPnlCompute(e -> e.getKey().equals(selectedNameStock), selectedNameStock);

                    CompletableFuture.runAsync(() -> {
                        ChinaBigGraph.setGraph(selectedNameStock);
                    });
                }
                return comp;
            }
        };

        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        if (filterOn) {
                            sorter.setRowFilter(null);
                            filterOn = false;
                        } else {
                            List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
                            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 0, OPEN_POS_COL));
                            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.NOT_EQUAL, 0, NET_POS_COL));
                            sorter.setRowFilter(RowFilter.orFilter(filters));
                            filterOn = true;
                        }
                    }
                } catch (Exception x) {
                    x.printStackTrace();
                    //sorter = (TableRowSorter<BarModel_STOCK>) tab.getRowSorter();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tab, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 900;
                d.height = 650;
                return d;
            }
        };

        JPanel tablePanel = new JPanel();

        tablePanel.add(scroll);

        JPanel controlPanel = new JPanel();
        JButton refreshButton = new JButton("Refresh");
        JButton getOpenButton = new JButton("getOpen");
        JButton getCurrentButton = new JButton("getCurrent");
        JButton getWtdMaxMinButton = new JButton("getWtdMaxMin");

        refreshButton.addActionListener((ActionEvent l) -> {
            CompletableFuture.runAsync(() -> {
                getCurrentPositionNormal();
                getCurrentPositionMargin();
                refreshFuture();
                mtmPnlCompute(GEN_MTM_PRED, "all");
            }).thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    m_model.fireTableDataChanged();
                    gpnl.repaint();
                });
            });

        });

        getOpenButton.addActionListener(l -> {
            getOpenPositionsNormal();
            getOpenTradePositionForFuture();
        });

        getCurrentButton.addActionListener(l -> {
            symbolNames.forEach((String name) -> {
                tradesMap.put(name, new ConcurrentSkipListMap<>());
            });
            getCurrentPositionNormal();
            getCurrentPositionMargin();
        });

        getWtdMaxMinButton.addActionListener(l -> {
            getWtdMaxMin();
        });

        JButton filterButton = new JButton("Active Only");

        sorter = (TableRowSorter<BarModel_POS>) tab.getRowSorter();

        filterButton.addActionListener(l -> {
            if (filterOn) {
                sorter.setRowFilter(null);
                filterOn = false;
            } else {
                List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
                filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER, 0, OPEN_POS_COL));
                filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.NOT_EQUAL, 0, NET_POS_COL));
                sorter.setRowFilter(RowFilter.orFilter(filters));
                filterOn = true;
            }
        });

        JRadioButton rb1 = new JRadioButton("Trade", true);
        JRadioButton rb2 = new JRadioButton("Buy Sell", false);

        JToggleButton autoUpdateButton = new JToggleButton("Auto Update");
        autoUpdateButton.addActionListener(l -> {
            if (autoUpdateButton.isSelected()) {
                ex = Executors.newScheduledThreadPool(10);
                ex.scheduleAtFixedRate(() -> {
                    ChinaPosition.refreshAll();
                }, 0, 1, TimeUnit.SECONDS);
            } else {
                ex.shutdown();
                //System.out.println(" refreshing pnl stopped " + LocalTime.now());
            }
        });
        rb1.addActionListener(l -> {
            if (rb1.isSelected()) {
                buySellTogether = true;
            }
            //System.out.println( " buy sell together on rb1 " + buySellTogether);
        });

        rb2.addActionListener(l -> {
            if (rb2.isSelected()) {
                buySellTogether = false;
            }
            //System.out.println( " buy sell together on rb2 " + buySellTogether);
        });

        ButtonGroup bg = new ButtonGroup();
        bg.add(rb1);
        bg.add(rb2);

        JToggleButton includeFutToggle = new JToggleButton("NO Fut");
        includeFutToggle.addActionListener(l -> {
            if (includeFutToggle.isSelected()) {
                GEN_MTM_PRED = m -> !m.getKey().equals("SGXA50");
            } else {
                GEN_MTM_PRED = m -> true;
            }
        });

        JToggleButton onlyFutToggle = new JToggleButton("Fut Only");
        onlyFutToggle.addActionListener(l -> {
            if (onlyFutToggle.isSelected()) {
                GEN_MTM_PRED = m -> m.getKey().equals("SGXA50");
            } else {
                GEN_MTM_PRED = m -> true;
            }
        });

        controlPanel.add(refreshButton);
        controlPanel.add(filterButton);
        controlPanel.add(getOpenButton);
        controlPanel.add(getCurrentButton);
        controlPanel.add(getWtdMaxMinButton);
        controlPanel.add(rb1);
        controlPanel.add(rb2);
        controlPanel.add(autoUpdateButton);
        controlPanel.add(includeFutToggle);
        controlPanel.add(onlyFutToggle);

        //JPanel graphPanel = new JPanel();
        JScrollPane graphPane = new JScrollPane(gpnl) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 600;
                d.width = ChinaMain.GLOBALWIDTH;
                return d;
            }
        };

        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(graphPane, BorderLayout.SOUTH);
        tab.setAutoCreateRowSorter(true);

        sorter = (TableRowSorter<BarModel_POS>) tab.getRowSorter();
        getWtdMaxMin();
    }

    private static void getWtdMaxMin() {
        String line1;
        List<String> res;
        Pattern p = Pattern.compile("sh|sz");
        Matcher m;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH + "wtdMaxMin.txt"), "gbk"))) {
            while ((line1 = reader1.readLine()) != null) {
                res = Arrays.asList(line1.split("\\s+"));
                m = p.matcher(res.get(0));
                if (m.find()) {
                    wtdMaxMap.put(res.get(0), Double.parseDouble(res.get(1)));
                    wtdMinMap.put(res.get(0), Double.parseDouble(res.get(2)));
                    //System.out.println(res.get(0) + " max " + wtdMaxMap.get(res.get(0)) + " min " +  wtdMinMap.get(res.get(0)));
                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
    }

    private static void refreshAll() {
        mtmPnlCompute(GEN_MTM_PRED, "all");
        SwingUtilities.invokeLater(() -> {
            m_model.fireTableDataChanged();
        });
    }

    static void tradePnlCompute() {
        tradesMap.keySet().forEach(k -> {
            if (tradesMap.get(k).size() > 0) {
                int pos = 0;
                double cb = 0.0;
                double mv;
                for (LocalTime t : ChinaData.priceMapBar.get(k).navigableKeySet()) {
                    if (tradesMap.get(k).containsKey(t)) {
                        //System.out.println( "t "+t+" trade "+ChinaData.priceMapBar.get(k).get(t));
                        pos += ((Trade) tradesMap.get(k).get(t)).getSize();
                        cb += ((Trade) tradesMap.get(k).get(t)).getCostWithCommission(k);
                    }
                    mv = pos * ChinaData.priceMapBar.get(k).get(t).getClose();
                    tradePnlMap.get(k).put(t, cb - mv);
                }
            }
        });
    }

    private static double getAdditionalInfo(LocalTime t, NavigableMap<LocalTime, ? super Trade> trMap, DoublePredicate d, ToDoubleFunction<Trade> f) {
        double res = 0.0;
        for (LocalTime t1 : trMap.subMap(t, true, t.plusMinutes(1), false).keySet()) {
            if (d.test(((Trade) trMap.get(t1)).getSize())) {
                res += f.applyAsDouble(((Trade) trMap.get(t1)));
            }
        }
        return res;
    }

    private static NavigableMap<LocalTime, Double> tradePnlCompute(String name, NavigableMap<LocalTime, SimpleBar> prMap,
                                                                   NavigableMap<LocalTime, ? super Trade> trMap, DoublePredicate d) {
        int pos = 0;
        double cb = 0.0;
        double mv;
        double fx = fxMap.getOrDefault(name, 1.0);
        NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();

        if (trMap.firstKey().isBefore(prMap.firstKey())) {
            for (Map.Entry e : trMap.headMap(prMap.firstKey(), false).entrySet()) {
                pos += ((Trade) e.getValue()).getSize();
                cb += ((Trade) e.getValue()).getCostWithCommission(name);
            }
//            System.out.println(" name is " + name);
//            System.out.println(" pos before open " + pos);
//            System.out.println(" cb before open " + cb);
        }

        for (LocalTime t : prMap.navigableKeySet()) {
            if (trMap.subMap(t, true, t.plusMinutes(1), false).size() > 0) {
                pos += getAdditionalInfo(t, trMap, d, Trade::getSize);
                cb += getAdditionalInfo(t, trMap, d, x -> x.getCostWithCommission(name));
            }
            mv = pos * prMap.get(t).getClose();
            res.put(t, fx * (mv + cb));

        }
        return res;
    }

    static synchronized void mtmPnlCompute(Predicate<? super Map.Entry<String, ?>> p, String nam) {


        //ytdPNLMap = openPositionMap.entrySet().stream().filter(p).collect(collector)
        CompletableFuture.runAsync(() -> {
            netYtdPnl = openPositionMap.entrySet().stream().filter(p).mapToDouble(e -> fxMap.getOrDefault(e.getKey(), 1.0)
                    * e.getValue() * (closeMap.getOrDefault(e.getKey(), 0.0) - costMap.getOrDefault(e.getKey(), 0.0))).sum();

            boughtDelta = tradesMap.entrySet().stream().filter(p).mapToDouble(e -> fxMap.getOrDefault(e.getKey(), 1.0)
                    * priceMap.getOrDefault(e.getKey(), 0.0)
                    * e.getValue().values().stream().map(e1 -> (Trade) e1).filter(e1 -> e1.getSize() > 0).mapToInt(Trade::getSize).sum()).sum();

            soldDelta = tradesMap.entrySet().stream().filter(p).mapToDouble(e -> fxMap.getOrDefault(e.getKey(), 1.0)
                    * priceMap.getOrDefault(e.getKey(), 0.0)
                    * e.getValue().values().stream().map(e1 -> (Trade) e1).filter(e1 -> e1.getSize() < 0).mapToInt(Trade::getSize).sum()).sum();

            openDelta = openPositionMap.entrySet().stream().filter(p).mapToDouble(e -> fxMap.getOrDefault(e.getKey(), 1.0)
                    * e.getValue() * priceMap.getOrDefault(e.getKey(), 0.0)).sum();

            netDelta = openPositionMap.entrySet().stream().filter(p).mapToDouble(e -> fxMap.getOrDefault(e.getKey(), 1.0)
                    * e.getValue() * priceMap.getOrDefault(e.getKey(), 0.0)).sum()
                    + tradesMap.entrySet().stream().filter(p).mapToDouble(e -> fxMap.getOrDefault(e.getKey(), 1.0)
                    * priceMap.getOrDefault(e.getKey(), 0.0)
                    * e.getValue().entrySet().stream().mapToInt(e1 -> ((Trade) e1.getValue()).getSize()).sum()).sum();

            netDeltaMap = Stream.of(openPositionMap.entrySet().stream().filter(e -> e.getValue() != 0).filter(p).map(Entry::getKey).collect(Collectors.toSet()),
                    tradesMap.entrySet().stream().filter(e -> e.getValue().size() > 0).filter(p).map(Entry::getKey).collect(Collectors.toSet()))
                    .flatMap(Collection::stream).distinct().map(e -> getDelta(e, 1))
                    .reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>());

//                    .collect(Collectors.collectingAndThen(toList(),
//                            l -> l.stream().flatMap(e1 -> e1.entrySet().stream()).collect(Collectors.groupingBy(Entry::getKey,
//                                    ConcurrentSkipListMap::new, Collectors.summingDouble(Entry::getValue)))));

            mtmDeltaMap = openPositionMap.entrySet().stream().filter(e -> e.getValue() != 0).filter(p).map(Entry::getKey).collect(Collectors.toSet()).stream().
                    distinct().map(e -> getDelta(e, 0)).reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>());

//                    .collect(Collectors.collectingAndThen(toList(),
//                    l -> l.stream().flatMap(e1 -> e1.entrySet().stream()).collect(Collectors.groupingBy(Entry::getKey,
//                            ConcurrentSkipListMap::new, Collectors.summingDouble(Entry::getValue)))));

            mtmPNLMap = openPositionMap.entrySet().stream().filter(e -> e.getValue() > 0).filter(p)
                    .map(e -> getMtmPNL(ChinaData.priceMapBar.get(e.getKey()), closeMap.getOrDefault(e.getKey(), 0.0), e.getValue(),
                            fxMap.getOrDefault(e.getKey(), 1.0))).reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>());


            if (tradesMap.entrySet().stream().filter(p).mapToInt(e -> e.getValue().size()).sum() > 0) {
                boughtPNLMap = tradesMap.entrySet().stream().filter(p).filter(e -> e.getValue().size() > 0)
                        .map(e -> tradePnlCompute(e.getKey(), ChinaData.priceMapBar.get(e.getKey()), e.getValue(), e1 -> e1 > 0))
                        .reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>());

                soldPNLMap = tradesMap.entrySet().stream().filter(p).filter(e -> e.getValue().size() > 0)
                        .map(e -> tradePnlCompute(e.getKey(), ChinaData.priceMapBar.get(e.getKey()), e.getValue(), e1 -> e1 < 0))
                        .reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>());

                tradePNLMap = tradesMap.entrySet().stream().filter(p).filter(e -> e.getValue().size() > 0)
                        .map(e -> tradePnlCompute(e.getKey(), ChinaData.priceMapBar.get(e.getKey()), e.getValue(), e1 -> true))
                        .reduce(Utility.mapBinOp()).orElse(new ConcurrentSkipListMap<>());

                todayNetPnl = Optional.ofNullable(tradePNLMap.lastEntry()).map(Entry::getValue).orElse(0.0) + netYtdPnl
                        + Optional.ofNullable(mtmPNLMap.lastEntry()).map(Entry::getValue).orElse(0.0);
            }


            netPNLMap = Utility.mapCominberGen((a, b) -> a + b, mtmPNLMap, tradePNLMap);
            mtmDeltaSharpe = SharpeUtility.computeMinuteSharpeFromMtmDeltaMp(mtmDeltaMap);

            minuteNetPnlSharpe = SharpeUtility.computeMinuteNetPnlSharpe(netPNLMap);

            benchExposureMap = ChinaStock.benchSimpleMap.entrySet().stream().filter(e -> !e.getKey().equals("sh204001")).filter(p)
                    .collect(Collectors.groupingBy(s -> ChinaStock.benchSimpleMap.get(s.getKey()), ConcurrentSkipListMap::new,
                            Collectors.summingDouble(s -> fxMap.getOrDefault(s.getKey(), 1.0)
                                    * getNetPosition(s.getKey()) * priceMap.getOrDefault(s.getKey(), 0.0))));

            pureMtmMap = ChinaPosition.openPositionMap.entrySet().stream().filter(p).filter(e -> e.getValue() > 0)
                    .collect(Collectors.groupingBy(e -> ChinaStock.benchSimpleMap.getOrDefault(e.getKey(), ""), HashMap::new,
                            Collectors.summingDouble(e -> fxMap.getOrDefault(e.getKey(), 1.0)
                                    * (ChinaStock.priceMap.getOrDefault(e.getKey(), 0.0) -
                                    ChinaStock.closeMap.getOrDefault(e.getKey(), 0.0)) * (e.getValue()))));
        }).thenRun(() -> {
            SwingUtilities.invokeLater(() -> {
                gpnl.setNetDeltaMap(netDeltaMap);
                gpnl.setBuySellPnlMap(boughtPNLMap, soldPNLMap);
                gpnl.setMtmPnl(Optional.ofNullable(mtmPNLMap.lastEntry()).map(Entry::getValue).orElse(0.0));
                gpnl.setNetPnlYtd(netYtdPnl);
                gpnl.setTodayPnl(todayNetPnl);
                gpnl.setBuyPnl(Optional.ofNullable(boughtPNLMap.lastEntry()).map(Entry::getValue).orElse(0.0));
                gpnl.setSellPnl(Optional.ofNullable(soldPNLMap.lastEntry()).map(Entry::getValue).orElse(0.0));
                gpnl.setBoughtDelta(boughtDelta);
                gpnl.setOpenDelta(openDelta);
                gpnl.setCurrentDelta(netDelta);
                gpnl.setSoldDelta(soldDelta);
                gpnl.setBenchMap(benchExposureMap);
                gpnl.setMtmBenchMap(pureMtmMap);
                gpnl.setNavigableMap(mtmPNLMap, tradePNLMap, netPNLMap);
                gpnl.setName(nam);
                gpnl.setChineseName(nameMap.get(nam));
                gpnl.setMinuteNetPnlSharpe(minuteNetPnlSharpe);
                gpnl.setMtmDeltaSharpe(mtmDeltaSharpe);

                gpnl.refresh();
            });
        });

        CompletableFuture.runAsync(() -> {
            gpnl.setBigKiyodoMap(topPnlKiyodoList());
            //System.out.println(" kiyodo before " + topKiyodo);
        }).thenRun(() -> {
            SwingUtilities.invokeLater(() -> {
                //System.out.println(" kiyodo after " + topKiyodo);
                gpnl.repaint();
            });
        });

        CompletableFuture.runAsync(() -> {
            //chg5m = ;
            gpnl.setPnl1mChgMap(getPnl5mChg());
            //System.out.println(" chg 5 pm before " + chg5m);
        }).thenRun(() -> {
            SwingUtilities.invokeLater(() -> {
                gpnl.repaint();
                //System.out.println(" chg 5 pm after " + chg5m);

            });
        });

    }


    private static NavigableMap<LocalTime, Double> getDelta(String name, int tradesMultiplier) {
        NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
        double fx = fxMap.getOrDefault(name, 1.0);
        int pos = openPositionMap.getOrDefault(name, 0);
        for (LocalTime t : ChinaData.priceMapBar.get(name).keySet()) {
            double price = ChinaData.priceMapBar.get(name).get(t).getClose();
            if (tradesMap.containsKey(name) && tradesMap.get(name).subMap(t, true, t.plusMinutes(1), false).size() > 0) {
                for (LocalTime t1 : tradesMap.get(name).subMap(t, true, t.plusMinutes(1), false).keySet()) {
                    pos += ((Trade) tradesMap.get(name).subMap(t, true, t.plusMinutes(1), false).get(t1)).getSize() * tradesMultiplier;
                }
            }
            res.put(t, pos * price * fx);
        }
        //res.entrySet().stream().filter(e->e.getKey().isAfter(LocalTime.of(11,25))).forEach(System.out::println);
        //System.out.println(" last delta " + res.lastEntry());
        //res.entrySet().stream().filter(e -> e.getKey().isAfter(LocalTime.of(11, 0)) && e.getKey().isBefore(LocalTime.of(13, 30))).forEach(System.out::println);
        return res;
    }

    private static NavigableMap<LocalTime, Double> getMtmPNL(NavigableMap<LocalTime, SimpleBar> m, double close, int openPos, double fx) {
        //.filter(e->((e.isAfter(LocalTime.of(9,30)) && e.isBefore(LocalTime.of(11,30))) || (e.isAfter(LocalTime.of(12,59)) && e.isBefore(LocalTime.of(15,1)))))
        //double fx = fxMap.getOrDefault(m, close)
        NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
        m.keySet().stream().filter(e -> e.isBefore(LocalTime.of(15, 1))).forEach(k -> {
            res.put(k, fx * openPos * (m.get(k).getClose() - close));
        });
        return res;
    }

    private void getOpenTradePositionForFuture() {
        System.out.println(" get open trade position for future ");
        if (ChinaPosition.tradesMap.containsKey("SGXA50")) {
            ChinaPosition.tradesMap.put("SGXA50", new ConcurrentSkipListMap<>());
        }
        ChinaMain.controller().reqPositions(new FutPositionHandler());
        ChinaMain.controller().reqExecutions(new ExecutionFilter(), new FutPosTradesHandler());
        ChinaMain.controller().getSGXA50Historical2(40000, this);

//        ChinaPosition.xuBotPos = ChinaPosition.tradesMap.get("SGXA50").entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() > 0).collect(Collectors.summingInt(e
//                -> ((Trade) e.getValue()).getSize()));
//        ChinaPosition.xuSoldPos = ChinaPosition.tradesMap.get("SGXA50").entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() < 0).collect(Collectors.summingInt(e
//                -> ((Trade) e.getValue()).getSize()));
//        xuOpenPostion = xuCurrentPosition - xuBotPos - xuSoldPos;
//        System.out.println(" XU open bot sold current " + xuOpenPostion + " " + xuBotPos + " " + xuSoldPos + " " + xuCurrentPosition);
//        openPositionMap.put("SGXA50", xuOpenPostion);
    }

    private static void refreshFuture() {

        xuBotPos = ChinaPosition.tradesMap.get("SGXA50").entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() > 0)
                .mapToInt(e -> ((Trade) e.getValue()).getSize()).sum();

        xuSoldPos = ChinaPosition.tradesMap.get("SGXA50").entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() < 0)
                .mapToInt(e -> ((Trade) e.getValue()).getSize()).sum();

        xuOpenPostion = xuCurrentPosition - xuBotPos - xuSoldPos;

        System.out.println(" REFRESHING XU open bot sold current " + xuOpenPostion + " " + xuBotPos + " " + xuSoldPos + " " + xuCurrentPosition);
        openPositionMap.put("SGXA50", xuOpenPostion);
        costMap.put("SGXA50", closeMap.getOrDefault("SGXA50", 0.0));
        openMap.put("SGXA50", xuOpenPrice);
        ChinaStock.closeMap.put("SGXA50", xuOpenPrice);
        //ChinaStock.closeMap.put("SGXA50", priceMapBar.get("SGXA50").firstEntry().getValue().getOpen());
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

            if (ld.equals(currDate) && ((lt.isAfter(LocalTime.of(8, 59)) && lt.isBefore(LocalTime.of(11, 31))) || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
                if (lt.equals(LocalTime.of(9, 0))) {
                    xuOpenPrice = open;
                    System.out.println(" today open is " + xuOpenPrice);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    System.out.println(Utility.getStrCheckNull(dt, open, high, low, close));
                }
            }

        } else {
            System.out.println(getStr(date, open, high, low, close));
        }
    }

    @Override
    public void actionUponFinish(String name) {
        System.out.println(" finished ");
    }

/*    public static void handleSGX50HistData(String date, double open, double high, double low, double close, int volume) {
        LocalDate currDate = LocalDate.now();
        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

            if (ld.equals(currDate) && ((lt.isAfter(LocalTime.of(8, 59)) && lt.isBefore(LocalTime.of(11, 31))) || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1))))) {
                if (lt.equals(LocalTime.of(9, 0))) {
                    xuOpenPrice = open;
                    System.out.println(" today open is " + xuOpenPrice);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    System.out.println(ChinaStockHelper.getStrCheckNull(dt, open, high, low, close));
                }
            }

        } else {
            System.out.println(getStr(date, open, high, low, close));
        }
    }*/

    public static void getOpenPositionsNormal() {
        int todaySoldCol = 0;
        int todayBoughtCol = 0;
        int chineseNameCol = 0;
        int currentPosCol = 0;
        int costCol = 0;
        int stockCodeCol = 0;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH + "openPosition.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                dataList = Arrays.asList(line.split("\\s+"));
                System.out.println(Arrays.asList(line.split("\\s+")));
                //System.out.println( " datalist size" + dataList.size());

                System.out.println(" datalist " + dataList);
                if (dataList.size() > 0 && dataList.get(0).equals("证券名称")) {
                    chineseNameCol = dataList.indexOf("证券名称");
                    currentPosCol = dataList.indexOf("证券数量");
                    costCol = dataList.indexOf("成本价");
                    //hard coded to account for exchange rate column is empty this is only for normal pos
                    stockCodeCol = dataList.indexOf("证券代码") - 1;
                    todayBoughtCol = dataList.indexOf("今买数量") - 1;
                    todaySoldCol = dataList.indexOf("今卖数量") - 1;
                    //System.out.println(" today sold col " + todaySoldCol);
                }

                //System.out.println(" datalist size " + dataList.size());
                //System.out.println(" stock code col " + stockCodeCol);
                //System.out.println(" stock code " + dataList.get(stockCodeCol));
                //System.out.println(" chinese name " + dataList.get(chineseNameCol));
                //System.out.println(dataList.size() > stockCodeCol);
                //System.out.println(nameMap.getOrDefault(addSHSZ(dataList.get(stockCodeCol)), "").replace(" ", "").equals(dataList.get(chineseNameCol)));

                if (dataList.size() > 1 && (nameMap.getOrDefault(Utility.addSHSZ(dataList.get(stockCodeCol)), "").replace(" ", "").equals(dataList.get(chineseNameCol))
                        || dataList.get(chineseNameCol).startsWith("XD"))) {
                    //System.out.println( " name " + addSHSZ(dataList.get(stockCodeCol)));
                    String nam = Utility.addSHSZ(dataList.get(stockCodeCol));
                    //System.out.println(" nam " + nam);
                    //System.out.println(" current pos col " + currentPosCol + " pos " + dataList.get(currentPosCol));

                    openPositionMap.put(nam, Integer.parseInt(dataList.get(currentPosCol)) + Integer.parseInt(dataList.get(todaySoldCol))
                            - Integer.parseInt(dataList.get(todayBoughtCol)));
                    costMap.put(nam, Double.parseDouble(dataList.get(costCol)));
                    //ytdPNLMap.put(nam, (closeMap.getOrDefault(nam,0.0)-costMap.getOrDefault(nam,0.0))*Integer.parseInt(dataList.get(1)));
                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
    }

    public static void getOpenPositionsFromMargin() {
        int todaySoldCol = 0;
        int todayBoughtCol = 0;
        int chineseNameCol = 0;
        int openPosCol = 0;
        int costCol = 0;
        int stockCodeCol = 10;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader
                (new FileInputStream(ChinaMain.GLOBALPATH + "openPositionMargin.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                dataList = Arrays.asList(line.split("\\s+"));
                System.out.println(Arrays.asList(line.split("\\s+")));
                //System.out.println( " datalist size" + dataList.size());

                if (dataList.size() > 0 && dataList.get(0).equals("证券名称")) {
                    chineseNameCol = dataList.indexOf("证券名称");
                    openPosCol = dataList.indexOf("证券数量");
                    costCol = dataList.indexOf("成本价");
                    stockCodeCol = dataList.indexOf("证券代码");
                    todayBoughtCol = dataList.indexOf("今买数量");
                    todaySoldCol = dataList.indexOf("今卖数量");
                    System.out.println(" today sold col " + todaySoldCol);
                }

                if (dataList.size() > stockCodeCol && (
                        nameMap.getOrDefault(Utility.addSHSZ(dataList.get(stockCodeCol)), "").replace(" ", "").equals(dataList.get(chineseNameCol))
                                || dataList.get(chineseNameCol).startsWith("XD"))) {

                    //System.out.println( " name " + addSHSZ(dataList.get(stockCodeCol)));
                    String nam = Utility.addSHSZ(dataList.get(stockCodeCol));
                    System.out.println(" nam " + nam);
                    openPositionMap.put(nam, Integer.parseInt(dataList.get(openPosCol)) + Integer.parseInt(dataList.get(todaySoldCol))
                            - Integer.parseInt(dataList.get(todayBoughtCol)));
                    costMap.put(nam, Double.parseDouble(dataList.get(costCol)));
                    //ytdPNLMap.put(nam, (closeMap.getOrDefault(nam,0.0)-costMap.getOrDefault(nam,0.0))*Integer.parseInt(dataList.get(1)));
                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
    }

    public static void getCurrentPositionNormal() {
        int chineseNameCol = 0;
        int openPosCol = 0;
        int orderTimeCol = 0;
        int fillTimeCol = 0;
        int statusCol = 0;
        int costCol = 0;
        int fillAmtCol = 0;
        int stockCodeCol = 0;
        int fillPriceCol = 0;
        int dateCol = 0;
        int buySellCol = 0;
        int beizhuCol = 0;

        File output = new File(ChinaMain.GLOBALPATH + "currentPositionProcessed.txt");
        MorningTask.clearFile(output);
        //MorningTask.simpleWriteToFile("", false, hkTestOutput); //clear content

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(ChinaMain.GLOBALPATH + "currentPosition.txt"), "gbk"))) {

            while ((line = reader1.readLine()) != null) {
                dataList = Arrays.asList(line.split("\\s+"));

                if (dataList.size() > 0 && dataList.get(0).equals("证券名称")) {
                    orderTimeCol = dataList.indexOf("成交时间");
                    fillTimeCol = dataList.indexOf("委托时间");
                    buySellCol = dataList.indexOf("买卖标志");
                    statusCol = dataList.indexOf("状态说明");
                    fillAmtCol = dataList.indexOf("成交数量");
                    fillPriceCol = dataList.indexOf("成交价格");
                    stockCodeCol = dataList.indexOf("证券代码");
                    beizhuCol = dataList.indexOf("备注");
                    //System.out.println(ChinaStockHelper.getStr(orderTimeCol,buySellCol,statusCol,fillAmtCol,fillPriceCol,stockCodeCol));
                    //System.out.println("委托时间" + dataList.indexOf("委托时间"));
                }

                if (dataList.size() > 10 && !dataList.get(stockCodeCol).startsWith("2") && (dataList.get(statusCol).equals("已成交") || dataList.get(statusCol).equals("部分成交"))
                        && (dataList.get(buySellCol).equals("买入") || dataList.get(buySellCol).equals("卖出"))) {

                    String ticker = Utility.addSHSZ(dataList.get(stockCodeCol));
                    LocalTime lt = LocalTime.parse(dataList.get(fillTimeCol)).truncatedTo(ChronoUnit.SECONDS);

                    if (lt.isAfter(LocalTime.of(11, 30, 0)) && lt.isBefore(LocalTime.of(13, 0, 0))) {
                        lt = LocalTime.of(11, 29, 59);
                    }

                    double p = Double.parseDouble(dataList.get(fillPriceCol));
                    int size = Integer.parseInt(dataList.get(fillAmtCol));
                    try {
                        if (tradesMap.containsKey(ticker)) {
                            if (dataList.get(buySellCol).equals("买入")) {
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    System.out.println("merging normal ... ");
                                    //tradesMap.get(ticker).get(lt).merge(new Trade(p,size));
                                    tradesMap.get(ticker).put(lt, new NormalTrade(p, size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new NormalTrade(p, size));
                                }
                                //System.out.println( " name " + ticker + " " + tradesMap.get(ticker));
                            } else if (dataList.get(buySellCol).equals("卖出")) {
                                //System.out.println( " name " + ticker + " " + tradesMap.get(ticker));
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    tradesMap.get(ticker).put(lt, new NormalTrade(p, -1 * size));
                                    //tradesMap.get(ticker).get(lt).merge(new Trade(p,-1*size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new NormalTrade(p, -1 * size));
                                }
                            }
                        } else {
                            throw new IllegalArgumentException(" ticker not allowed " + ticker);
                        }
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                    String outputString = Utility.getStrTabbed(LocalDate.now().toString(), dataList.get(fillTimeCol), "Stock", " ", "CNY",
                            ticker.substring(0, 2).toUpperCase(), " ", "'" + dataList.get(stockCodeCol), dataList.get(buySellCol).equals("买入") ? "B" : "S",
                            "O", (dataList.get(buySellCol).equals("买入") ? "" : "-") + dataList.get(fillAmtCol), "1", dataList.get(fillPriceCol));
                    MorningTask.simpleWriteToFile(outputString, true, output);

                    //hkTestOutput here to currentPositionProcessed
                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
    }

    //get margin position
    public static void getCurrentPositionMargin() {

        int fillTimeCol = 0;
        int statusCol = 0;
        int orderTimeCol = 0;
        int fillAmtCol = 0;
        int stockCodeCol = 0;
        int fillPriceCol = 0;

        int buySellCol = 0;
        int beizhuCol = 0;

        //System.out.println(" getting current margin position ");

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(ChinaMain.GLOBALPATH + "marginCurrentPosition.txt"), "gbk"))) {

            while ((line = reader1.readLine()) != null) {
                dataList = Arrays.asList(line.split("\\s+"));

                if (dataList.size() > 0 && dataList.get(0).equals("证券名称")) {
                    orderTimeCol = dataList.indexOf("成交时间");
                    fillTimeCol = dataList.indexOf("委托时间");
                    buySellCol = dataList.indexOf("买卖标志");
                    statusCol = dataList.indexOf("状态说明");
                    fillAmtCol = dataList.indexOf("成交数量");
                    fillPriceCol = dataList.indexOf("成交价格");
                    stockCodeCol = dataList.indexOf("证券代码");
                    beizhuCol = dataList.indexOf("备注");
                    //System.out.println(ChinaStockHelper.getStr(orderTimeCol,buySellCol,statusCol,fillAmtCol,fillPriceCol,stockCodeCol));
                    //System.out.println("委托时间" + dataList.indexOf("委托时间"));
                }

                if (dataList.size() > 10 && !dataList.get(stockCodeCol).startsWith("2") && (dataList.get(statusCol).equals("已成") || dataList.get(statusCol).equals("部成"))
                        && (dataList.get(buySellCol).equals("证券买入") || dataList.get(buySellCol).equals("证券卖出"))) {

                    String ticker = Utility.addSHSZ(dataList.get(stockCodeCol));
                    LocalTime lt = LocalTime.parse(dataList.get(fillTimeCol)).truncatedTo(ChronoUnit.SECONDS);

                    if (lt.isAfter(LocalTime.of(11, 30, 0)) && lt.isBefore(LocalTime.of(13, 0, 0))) {
                        lt = LocalTime.of(11, 29, 59);
                    }

                    double p = Double.parseDouble(dataList.get(fillPriceCol));
                    int size = Integer.parseInt(dataList.get(fillAmtCol));
                    try {
                        if (dataList.get(buySellCol).equals("证券买入")) {
                            if (dataList.get(beizhuCol).equals("融资开仓")) {
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    System.out.println("merging margin... ");
                                    tradesMap.get(ticker).put(lt, new MarginTrade(p, size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new MarginTrade(p, size));
                                }

                            } else if (dataList.get(beizhuCol).equals("买入担保品")) {
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    System.out.println("merging normal... ");
                                    tradesMap.get(ticker).put(lt, new MarginTrade(p, size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new MarginTrade(p, size));
                                }
                            }
                            //System.out.println( " name " + ticker + " " + tradesMap.get(ticker));
                        } else if (dataList.get(buySellCol).equals("证券卖出")) {
                            //treat all sells as normal stock with brokerage 2 bp
                            if (dataList.get(beizhuCol).equals("卖券还款")) {
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    System.out.println("merging margin... ");
                                    tradesMap.get(ticker).put(lt, new MarginTrade(p, -1 * size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new MarginTrade(p, -1 * size));
                                }
                            } else if (dataList.get(beizhuCol).equals("卖出担保品")) {
                                if (tradesMap.get(ticker).containsKey(lt)) {
                                    System.out.println("merging margin... ");
                                    tradesMap.get(ticker).put(lt, new MarginTrade(p, -1 * size));
                                } else {
                                    tradesMap.get(ticker).put(lt, new MarginTrade(p, -1 * size));
                                }
                            }
                        }

                        //include selling
//                        else if(dataList.get(buySellCol).equals("卖出")) {
//                            //System.out.println( " name " + ticker + " " + tradesMap.get(ticker));
//                            if(tradesMap.get(ticker).containsKey(lt)) {
//                                tradesMap.get(ticker).put(lt, new NormalTrade(p,-1*size));
//                                //tradesMap.get(ticker).get(lt).merge(new Trade(p,-1*size));
//                            } else {
//                                tradesMap.get(ticker).put(lt, new NormalTrade(p,-1*size));
//                            }
//                        }
                    } catch (Exception x) {
                        //System.out.println(" name " + ticker);
                        //System.out.println ( "tradesmap includes " + tradesMap.containsKey(ticker));
                        x.printStackTrace();
                    }
                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
    }

    static Map<String, Integer> getNetPosition() {
        if (openPositionMap.size() > 0 || tradesMap.size() > 0) {

            Map<String, Integer> trades = tradesMap.entrySet().stream().filter(e -> e.getValue().size() > 0)
                    .collect(Collectors.toMap(Entry::getKey, e -> (Integer) e.getValue().entrySet().stream()
                            .mapToInt(e1 -> ((Trade) e1.getValue()).getSize()).sum()));
            Map<String, Integer> res = Stream.of(openPositionMap, trades).flatMap(e -> e.entrySet().stream())
                    .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingInt(Entry::getValue)));

            return res;
        }
        return new HashMap<>();
    }

    //    public static void main(String[] args) {
//        getWtdMaxMin();
//    }
    int getTotalTodayBought(String name) {
        return (tradesMap.get(name).size() > 0) ? tradesMap.get(name).entrySet().stream()
                .filter(e -> ((Trade) e.getValue()).getSize() > 0).mapToInt(e -> ((Trade) e.getValue()).getSize()).sum() : 0;
    }

    int getTotalTodaySold(String name) {
        return (tradesMap.get(name).size() > 0) ? tradesMap.get(name).entrySet().stream()
                .filter(e -> ((Trade) e.getValue()).getSize() < 0).mapToInt(e -> ((Trade) e.getValue()).getSize()).sum() : 0;
    }

    double getTotalDeltaBought(String name) {
        double fx = fxMap.getOrDefault(name, 1.0);
        return (tradesMap.get(name).size() > 0) ? tradesMap.get(name).entrySet().stream()
                .filter(e -> ((Trade) e.getValue()).getSize() > 0)
                .mapToDouble(e -> ((Trade) e.getValue()).getSize() * ((Trade) e.getValue()).getPrice()).sum() * fx : 0;
    }

    double getTotalDeltaSold(String name) {
        double fx = fxMap.getOrDefault(name, 1.0);
        return (tradesMap.get(name).size() > 0) ? tradesMap.get(name).entrySet().stream()
                .filter(e -> ((Trade) e.getValue()).getSize() < 0)
                .mapToDouble(e -> ((Trade) e.getValue()).getSize() * ((Trade) e.getValue()).getPrice()).sum() * fx : 0;
    }

    double getAvgBCost(String name) {
        return (tradesMap.get(name).entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() > 0).count() > 0)
                ? tradesMap.get(name).entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() > 0).collect(Collectors.collectingAndThen(toList(),
                l -> (Double) l.stream().mapToDouble(e -> ((Trade) e.getValue()).getPrice()* ((Trade) e.getValue()).getSize()).sum()
                        / (Double) l.stream().mapToDouble(e -> ((Trade) e.getValue()).getSize()).sum())) : 0.0;
    }

    double getAvgSCost(String name) {
        return (tradesMap.get(name).entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() < 0).count() > 0)
                ? tradesMap.get(name).entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() < 0).collect(Collectors.collectingAndThen(toList(),
                l -> (Double) l.stream().mapToDouble(e -> ((Trade) e.getValue()).getPrice() * ((Trade) e.getValue()).getSize()).sum()
                        / (Double) l.stream().mapToDouble(e -> ((Trade) e.getValue()).getSize()).sum())) : 0.0;
    }

    static double getBuyTradePnl(String name) {
        double fx = fxMap.getOrDefault(name, 1.0);
        return (tradesMap.get(name).size() > 0 && Utility.noZeroArrayGen(name, ChinaStock.priceMap))
                ? tradesMap.get(name).entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() > 0).mapToDouble(e -> (((Trade) e.getValue()).getSize())
                * (ChinaStock.priceMap.getOrDefault(name, 0.0) - ((Trade) e.getValue()).getPrice()) - ((Trade) e.getValue()).getTradingCost(name)).sum() * fx : 0.0;
    }

    //    static double getTradingCost(String name, Trade td) {
//        return td.getTradingCost(name);
////        double brokerage = Math.max(5,Math.round(td.getPrice()*abs(td.getSize())*2/100)/100d);
////        double guohu = (name.equals("sh510050"))?0:((name.startsWith("sz"))?0.0:Math.round(td.getPrice()*abs(td.getSize())*0.2/100d)/100d);
////        //double stamp = (td.getSize()<0)?Math.round((td.getPrice()*abs(td.getSize()))*0.1)/100d:0.0;
////        double stamp = (name.equals("sh510050"))?0:((td.getSize()<0?1:0)*Math.round((td.getPrice()*abs(td.getSize()))*0.1)/100d);
////
////        return brokerage+guohu+stamp;
//    }
    static double getSellTradePnl(String name) {
        double fx = fxMap.getOrDefault(name, 1.0);
        return (tradesMap.get(name).size() > 0 && Utility.noZeroArrayGen(name, ChinaStock.priceMap))
                ? Math.round(tradesMap.get(name).entrySet().stream()
                .filter(e -> ((Trade) e.getValue()).getSize() < 0)
                .mapToDouble(e -> (((Trade) e.getValue()).getSize() * (ChinaStock.priceMap.getOrDefault(name, 0.0) - ((Trade) e.getValue()).getPrice())
                        - ((Trade) e.getValue()).getTradingCost(name))).sum() * 100d * fx) / 100d : 0.0;
    }

    static int getNetPosition(String name) {
        if (openPositionMap.containsKey(name) || tradesMap.containsKey(name)) {
            return openPositionMap.getOrDefault(name, 0) + (Integer) tradesMap.get(name).entrySet().stream()
                    .mapToInt(e -> ((Trade) e.getValue()).getSize()).sum();
        } else {
            return 0;
        }
    }

    double getNetPnl(String name) {
        double fx = fxMap.getOrDefault(name, 1.0);
        return Math.round(100d * (fx * ((priceMap.getOrDefault(name, 0.0) -
                costMap.getOrDefault(name, 0.0)) * openPositionMap.getOrDefault(name, 0))
                + getBuyTradePnl(name) + getSellTradePnl(name))) / 100d;
    }

    static double r(double d) {
        return Math.round(d * 100d) / 100d;
    }

    private static <T> Comparator<T> reverseThis(Comparator<T> in) {
        return in.reversed();
    }

    private static LinkedList<String> getPnl5mChg() {
        Set<String> ptf = symbolNames.stream().filter(ChinaPosition::relevantStock).collect(toCollection(HashSet::new));
        return ptf.stream().collect(Collectors.toMap(s -> s, ChinaPosition::getPnLChange5m)).entrySet().stream()
                .sorted(reverseThis(Comparator.comparingDouble(Entry::getValue)))
                .map(e -> Utility.getStr(ChinaStock.nameMap.get(e.getKey()), ":", e.getValue()))
                .collect(Collectors.toCollection(LinkedList::new));

    }

    private static boolean relevantStock(String stock) {
        return openPositionMap.containsKey(stock) ||
                (tradesMap.containsKey(stock) && tradesMap.get(stock).size() > 0);
    }

    static double getPnLChange5m(String name) {
        double fx = fxMap.getOrDefault(name, 1.0);
        if (ChinaStock.NORMAL_STOCK.test(name)) {
            LocalTime lastKey = ChinaData.priceMapBar.get(name).lastKey().isAfter(LocalTime.of(15, 0)) ? LocalTime.of(15, 0) : ChinaData.priceMapBar.get(name).lastKey();
            double p = ChinaData.priceMapBar.get(name).lastEntry().getValue().getClose();
            double previousP = ChinaData.priceMapBar.get(name).ceilingEntry(lastKey.minusMinutes(5)).getValue().getClose();
            int openPos = openPositionMap.getOrDefault(name, 0);
            int tradedPos = 0;
            int posThisMinute = 0;
            double tradeChgPnlAfter = 0.0;
            int tradedPosBefore = 0;

            if (tradesMap.containsKey(name)) {
                //tradedPos = tradesMap.get(name).entrySet().stream().filter(e-> e.getKey().isBefore(lastKey)).collect(summingInt(e->e.getValue().getSize()));
                posThisMinute = tradesMap.get(name).entrySet().stream().filter(e -> e.getKey()
                        .truncatedTo(ChronoUnit.MINUTES).equals(lastKey)).mapToInt(e -> ((Trade) e.getValue()).getSize()).sum();

                tradedPosBefore = tradesMap.get(name).entrySet().stream().filter(e -> e.getKey().isBefore(lastKey.minusMinutes(5L)))
                        .mapToInt(e -> ((Trade) e.getValue()).getSize()).sum();

                tradeChgPnlAfter = tradesMap.get(name).entrySet().stream().filter(e -> e.getKey().isAfter(lastKey.minusMinutes(6L))).mapToDouble(e -> ((Trade) e.getValue()).getMtmPnl(name)).sum();
            }
            return Math.round(((openPos + tradedPosBefore) * (p - previousP) + tradeChgPnlAfter) * fx) / 1d;
        }
        return 0.0;
    }

    //    int getNetPnlPercentile(String name) {
//        //if(netPnl)
//        double min = netPNLMap.entrySet().stream().min(Map.Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
//        double max = netPNLMap.entrySet().stream().max(Map.Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
//        double last = netPNLMap.lastEntry().getValue();
//
//    }
    private static int getPercentile(double max, double min, double now) {
        if (max != 0.0 && min != 0.0 && now != 0.0 && max != min) {
            double max1 = Math.max(max, now);
            double min1 = Math.min(min, now);
            return (int) Math.round((now - min1) / (max1 - min1) * 100d);
        } else {
            return 0;
        }
    }

    public static int getPercentileWrapper(String name) {
        double curr = 0.0;
        double maxT = 0.0;
        double minT = 0.0;
        if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0) {
            curr = priceMapBar.get(name).lastEntry().getValue().getClose();
            maxT = priceMapBar.get(name).entrySet().stream().max(BAR_HIGH).map(Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
            minT = priceMapBar.get(name).entrySet().stream().min(BAR_LOW).map(Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
        }
        double max = Math.max(maxT, wtdMaxMap.getOrDefault(name, 0.0));
        double min = Math.min(minT, wtdMinMap.getOrDefault(name, 0.0));

        return getPercentile(max, min, curr);
    }

    static int getChangeInPercentileToday(String name) {
        double curr = 0.0;
        double maxT = 0.0;
        double minT = 0.0;
        if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0) {
            curr = priceMapBar.get(name).lastEntry().getValue().getClose();
            maxT = priceMapBar.get(name).entrySet().stream().max(BAR_HIGH).map(Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
            minT = priceMapBar.get(name).entrySet().stream().min(BAR_LOW).map(Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
        }

        double max = Math.max(maxT, wtdMaxMap.getOrDefault(name, 0.0));
        double min = Math.min(minT, wtdMinMap.getOrDefault(name, 0.0));
        double open = openMap.getOrDefault(name, 0.0);

        if (max != 0.0 && min != 0.0 && curr != 0.0 && open != 0.0 && max != min) {
            return (int) Math.round(100d * (curr - open) / (max - min));
        }
        return 0;
    }

    public static double getPotentialReturnToMid(String name) {

        double curr = 0.0;
        double maxT = 0.0;
        double minT = 0.0;
        if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0) {
            curr = priceMapBar.get(name).lastEntry().getValue().getClose();
            maxT = priceMapBar.get(name).entrySet().stream().max(BAR_HIGH).map(Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
            minT = priceMapBar.get(name).entrySet().stream().min(BAR_LOW).map(Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
        }

        double max = Math.max(maxT, wtdMaxMap.getOrDefault(name, 0.0));
        double min = Math.min(minT, wtdMinMap.getOrDefault(name, 0.0));

        if (max != 0.0 && min != 0.0 && curr != 0.0 && max != min) {
            return Math.round(1000d * ((max + min) / 2 / curr - 1)) / 10d;
        }
        return 0.0;
    }

    public static int getCurrentDelta(String name) {
        return (int) Math.round(fxMap.getOrDefault(name, 1.0) * priceMap.getOrDefault(name, 0.0) * getNetPosition(name) / 1000d);
    }

    public static double getMtmPnl(String name) {
        if (openPositionMap.containsKey(name)) {
            return r((priceMap.getOrDefault(name, 0.0) - closeMap.getOrDefault(name, 0.0)) * openPositionMap.getOrDefault(name, 0) * fxMap.getOrDefault(name, 1.0));
        }
        return 0.0;
    }

    public static double getTradePnl(String name) {
        if (tradesMap.containsKey(name) && tradesMap.get(name).size() > 0) {
            return (getBuyTradePnl(name) + getSellTradePnl(name));
        }
        return 0;
    }


    static double getTodayTotalPnl(String name) {
        double fx = fxMap.getOrDefault(name, 1.0);
        return Math.round(100d * (getMtmPnl(name) + getBuyTradePnl(name) + getSellTradePnl(name))) / 100d;
    }

    //todo
    static synchronized LinkedList<String> topPnlKiyodoList() {
        LinkedList<String> res;

        Map<String, Double> tickerNetpnl = priceMapBar.entrySet().stream().filter(e -> relevantStock(e.getKey()))
                .collect(Collectors.toMap(Entry::getKey, e -> getTodayTotalPnl(e.getKey())));

        double netPnlAll = tickerNetpnl.entrySet().stream().mapToDouble(Map.Entry::getValue).sum();

        res = tickerNetpnl.entrySet().stream().sorted(reverseThis(Comparator.comparingDouble(e -> Math.abs(e.getValue()))))
                .map(Map.Entry::getKey).map(s -> Utility.getStr(nameMap.get(s),
                        tickerNetpnl.getOrDefault(s, 0.0), Math.round(100d * tickerNetpnl.getOrDefault(s, 0.0) / netPnlAll), "%"))
                .collect(Collectors.toCollection(LinkedList::new));
        //System.out.println(" top kiyodo list " + res);
        return res;
    }


    private final class BarModel_POS extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return 35;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "T";
                case 1:
                    return "name";
                case 2:
                    return "开Pos";
                case 3:
                    return "Cost";
                case 4:
                    return "市值";
                case 5:
                    return "Close";
                case 6:
                    return "Open";
                case 7:
                    return "P";
                case 8:
                    return "cc";
                case 9:
                    return "Open Pnl";
                case 10:
                    return "Today MTM";
                case 11:
                    return "Net Pnl YTD";
                case 12:
                    return "Total MTM";
                case 13:
                    return "today 买";
                case 14:
                    return "delta 买";
                case 15:
                    return "avg B cost";
                case 16:
                    return "Buy PnL";
                case 17:
                    return "today 卖";
                case 18:
                    return "delta 卖";
                case 19:
                    return "avg S cost";
                case 20:
                    return "Sell PnL";
                case 21:
                    return "net pos";
                case 22:
                    return "Total Tr Pnl";
                case 23:
                    return "Total pnl";
                case 24:
                    return "P%";
                case 25:
                    return "1m动";
                case 26:
                    return "Bench";
                case 27:
                    return "wkPerc";
                case 28:
                    return "wkMax";
                case 29:
                    return "wkMin";
                case 30:
                    return "deviation";
                case 31:
                    return "Today Total Pnl";

                default:
                    return null;
            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                case 2:
                    return Integer.class;
                case 13:
                    return Integer.class;
                case 17:
                    return Integer.class;
                case 21:
                    return Integer.class;
                case 24:
                    return Integer.class;
                case 26:
                    return String.class;
                case 27:
                    return Integer.class;

                default:
                    return Double.class;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            String name = symbolNames.get(rowIn);
            int openpos = openPositionMap.getOrDefault(name, 0);
            double currPrice = ChinaStock.priceMap.getOrDefault(name, 0.0);

            switch (col) {
                case 0:
                    return name;
                case 1:
                    return ChinaStock.nameMap.get(name);
                case 2:
                    return openpos;
                case 3:
                    return costMap.getOrDefault(name, 0.0);
                case 4:
                    return Math.round(fxMap.getOrDefault(name, 1.0) * currPrice * getNetPosition(name) / 1000d) * 1.0d;
                case 5:
                    return ChinaStock.closeMap.getOrDefault(name, 0.0);
                case 6:
                    return ChinaStock.openMap.getOrDefault(name, 0.0);
                case 7:
                    return ChinaStock.priceMap.getOrDefault(name, 0.0);
                case 8:
                    return closeMap.getOrDefault(name, 0.0) == 0.0 ? 0 : Math.round(1000d * (priceMap.getOrDefault(name, 0.0) / closeMap.getOrDefault(name, 0.0) - 1)) / 10d;
                case 9:
                    return r(fxMap.getOrDefault(name, 1.0) * (openMap.getOrDefault(name, 0.0) - closeMap.getOrDefault(name, 0.0)) * openpos);
                case 10:
                    return r(fxMap.getOrDefault(name, 1.0) * (priceMap.getOrDefault(name, 0.0) - closeMap.getOrDefault(name, 0.0)) * openpos);
                case 11:
                    return r(fxMap.getOrDefault(name, 1.0) * (closeMap.getOrDefault(name, 0.0) - costMap.getOrDefault(name, 0.0)) * openpos);
                case 12:
                    return r(fxMap.getOrDefault(name, 1.0) * (priceMap.getOrDefault(name, 0.0) - costMap.getOrDefault(name, 0.0)) * openpos);
                case 13:
                    return getTotalTodayBought(name);
                case 14:
                    return r(getTotalDeltaBought(name) / 1000d);
                case 15:
                    return r(getAvgBCost(name));
                case 16:
                    return r(getBuyTradePnl(name));
                case 17:
                    return getTotalTodaySold(name);
                case 18:
                    return r(getTotalDeltaSold(name) / 1000d);
                case 19:
                    return r(getAvgSCost(name));
                case 20:
                    return r(getSellTradePnl(name));
                case 21:
                    return getNetPosition(name);
                case 22:
                    return r(getBuyTradePnl(name) + getSellTradePnl(name));
                case 23:
                    return r(getNetPnl(name));
                case 24:
                    return ChinaStock.getPercentileBar(name);
                case 25:
                    return r(getPnLChange5m(name));
                case 26:
                    return ChinaStock.benchMap.getOrDefault(name, "");
                case 27:
                    return getPercentileWrapper(name);
                case 28:
                    return Math.max(wtdMaxMap.getOrDefault(name, 0.0), ChinaStock.maxMap.getOrDefault(name, Double.MIN_VALUE));
                case 29:
                    return Math.min(wtdMinMap.getOrDefault(name, 0.0), ChinaStock.minMap.getOrDefault(name, Double.MAX_VALUE));
                case 30:
                    return (currPrice != 0.0) ? Math.round((((wtdMaxMap.getOrDefault(name, 0.0) + wtdMinMap.getOrDefault(name, 0.0)) / 2) / currPrice - 1) * 1000d) / 10d : 0.0;
                case 31:
                    return getTodayTotalPnl(name);

                default:
                    return null;
            }
        }
    }
}

class FutPosTradesHandler implements ApiController.ITradeReportHandler {

    private static LocalTime roundUpLocalTime(LocalTime t) {
        if (t.isAfter(LocalTime.of(11, 30)) && t.isBefore(LocalTime.of(13, 0))) {
            return LocalTime.of(11, 29);
        } else {
            return t;
        }
    }

    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {
        System.out.println(" in trade report " + contract.toString());
        int sign = (execution.side().equals("BOT")) ? 1 : -1;
        System.out.println(LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss")));
        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));

        if (ldt.getDayOfMonth() == LocalDateTime.now().getDayOfMonth()) {

            LocalTime lt = roundUpLocalTime(ldt.toLocalTime());

            System.out.println(" exec " + execution.side() + "　" + execution.time() + " " + execution.cumQty()
                    + " " + execution.price() + " " + execution.orderRef() + " " + execution.orderId() + " " + execution.permId() + " "
                    + execution.shares());
            System.out.println(" time string " + ldt.toString());
            System.out.println(" time is " + ldt.toLocalTime());
            System.out.println(" day is " + LocalDateTime.now().getDayOfMonth());

            if (ChinaPosition.tradesMap.get("SGXA50").containsKey(lt)) {
                System.out.println(" lt is " + lt);
                ((Trade) ChinaPosition.tradesMap.get("SGXA50").get(lt)).merge(new FutureTrade(execution.price(), sign * execution.cumQty()));
            } else {
                System.out.println(" else lt " + lt);
                ChinaPosition.tradesMap.get("SGXA50").put(lt, new FutureTrade(execution.price(), sign * execution.cumQty()));
            }
        }
    }

    @Override
    public void tradeReportEnd() {

//        System.out.println(" printing trades map ");
//
//        ChinaPosition.tradesMap.get("SGXA50").entrySet().stream().forEach(System.out::println);
//
//        ChinaPosition.xuBotPos = ChinaPosition.tradesMap.get("SGXA50").entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() > 0).collect(Collectors.summingInt(e
//                -> ((Trade) e.getValue()).getSize()));
//
//        ChinaPosition.xuSoldPos = ChinaPosition.tradesMap.get("SGXA50").entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() < 0).collect(Collectors.summingInt(e
//                -> ((Trade) e.getValue()).getSize()));
//
//        System.out.println(" xu bot pos  " + ChinaPosition.xuBotPos + " xu sold pos " + ChinaPosition.xuSoldPos);
//
//        System.out.println(" trade report ended ");
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
        //System.out.println(" commission report being produced ");
    }
}

