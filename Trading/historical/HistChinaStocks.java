package historical;

import TradeType.MarginTrade;
import TradeType.NormalTrade;
import TradeType.Trade;
import apidemo.ChinaMain;
import auxiliary.SimpleBar;
import graph.GraphBarTemporal;
import graph.GraphChinaPnl;
import utility.SharpeUtility;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HistChinaStocks extends JPanel {

    static JPanel graphPanel;
    static BarModel_China model;
    static JTable tab;

    public static final LocalDate LAST_YEAR_END = LocalDate.of(2016, 12, 31);
    public static final LocalDate MONDAY_OF_WEEK = Utility.getMondayOfWeek(LocalDateTime.now());

    public static final String GLOBALPATH = "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\Trading\\";

    public static Map<String, String> nameMap = new HashMap<>();

    static GraphBarTemporal<LocalDate> graphYtd = new GraphBarTemporal<>();
    static GraphBarTemporal<LocalDateTime> graphWtd = new GraphBarTemporal<>();
    static GraphChinaPnl<LocalDateTime> graphWtdPnl = new GraphChinaPnl<>();

    File chinaInput = new File(GLOBALPATH + "ChinaAll.txt");
    File priceInput = new File(GLOBALPATH + "pricesTodayYtd.csv");

    static List<String> stockList = new LinkedList<>();
    static Map<String, NavigableMap<LocalDate, SimpleBar>> chinaYtd = new HashMap<>();
    static Map<String, NavigableMap<LocalDateTime, SimpleBar>> chinaWtd = new HashMap<>();

    static Map<String, ChinaResult> ytdResult = new HashMap<>();
    static Map<String, ChinaResult> wtdResult = new HashMap<>();

    static List<ChinaTrade> chinaTradeList = new LinkedList<>();

    public static Map<String, NavigableMap<LocalDateTime, ? super Trade>> chinaTradeMap = new HashMap<>();

    public static Map<String, NavigableMap<LocalDate, Integer>> netSharesTradedByDay = new HashMap<>();
    public static Map<String, NavigableMap<LocalDateTime, Integer>> netSharesTradedWtd = new HashMap<>();
    static Map<String, Double> priceMap = new HashMap<>();
    static Map<String, Double> lastWeekCloseMap = new HashMap<>();
    static Map<String, Double> totalTradingCostMap = new HashMap<>();
    static Map<String, Double> costBasisMap = new HashMap<>();
    static Map<String, Double> netTradePnlMap = new HashMap<>();
    static Map<String, Double> wtdTradePnlMap = new HashMap<>();
    static Map<String, Double> wtdMtmPnlMap = new HashMap<>();
    static BinaryOperator<NavigableMap<LocalDateTime, Double>> mapOp = (a, b) -> Stream.of(a, b).flatMap(e -> e.entrySet().stream())
            .collect(Collectors.groupingBy(e1 -> e1.getKey(), TreeMap::new, Collectors.summingDouble(
                    e1 -> e1.getValue())));

    static volatile NavigableMap<LocalDateTime, Double> weekMtmMap = new TreeMap<>();
    static volatile NavigableMap<LocalDateTime, Double> weekTradePnlMap = new TreeMap<>();
    static volatile NavigableMap<LocalDateTime, Double> weekNetMap = new TreeMap<>();

    static String tdxDayPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export\\" : "J:\\TDX\\T0002\\export\\";

    static String tdxMinutePath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export_1m\\" : "J:\\TDX\\T0002\\export_1m\\";

    static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    int modelRow;
    int indexRow;
    static volatile String selectedStock = "";
    TableRowSorter<BarModel_China> sorter;

    public HistChinaStocks() {
        String line;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(priceInput)))) {
            while ((line = reader.readLine()) != null) {
                List<String> l = Arrays.asList(line.split(","));
                if (l.size() >= 2) {
                    priceMap.put(l.get(0), Double.parseDouble(l.get(1)));
                } else {
                    System.out.println(" line is wrong " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(chinaInput), "GBK"))) {
            while ((line = reader.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));

                if (!al1.get(0).equals("sh204001") && (al1.get(0).startsWith("sh") || al1.get(0).startsWith("sz"))) {
                    chinaYtd.put(al1.get(0), new ConcurrentSkipListMap<>());
                    chinaWtd.put(al1.get(0), new ConcurrentSkipListMap<>());
                    stockList.add(al1.get(0));
                    nameMap.put(al1.get(0), al1.get(1));
                    ytdResult.put(al1.get(0), new ChinaResult());
                    wtdResult.put(al1.get(0), new ChinaResult());
                    chinaTradeMap.put(al1.get(0), new TreeMap<>());
                    netSharesTradedByDay.put(al1.get(0), new TreeMap<>());
                    netSharesTradedWtd.put(al1.get(0), new TreeMap<>());
                    totalTradingCostMap.put(al1.get(0), 0.0);
                    costBasisMap.put(al1.get(0), 0.0);
                    wtdTradePnlMap.put(al1.get(0), 0.0);
                    lastWeekCloseMap.put(al1.get(0), 0.0);
                    wtdMtmPnlMap.put(al1.get(0), 0.0);

                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


        graphPanel = new JPanel();

        model = new BarModel_China();

        tab = new JTable(model) {

            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int indexRow, int indexCol) {
                try {
                    Component comp = super.prepareRenderer(renderer, indexRow, indexCol);
                    if (isCellSelected(indexRow, indexCol)) {
                        modelRow = this.convertRowIndexToModel(indexRow);
                        selectedStock = stockList.get(modelRow);
                        comp.setBackground(Color.GREEN);
                        CompletableFuture.runAsync(() -> {
                            graphYtd.fillInGraphChinaGen(selectedStock, chinaYtd);
                            graphWtd.fillInGraphChinaGen(selectedStock, chinaWtd);
                            graphYtd.setTradesMap(netSharesTradedByDay.get(selectedStock));
                            graphWtd.setTradesMap(netSharesTradedWtd.get(selectedStock));
                            graphYtd.setTradePnl(computeCurrentTradePnl(selectedStock, LAST_YEAR_END));
                            graphWtd.setTradePnl(computeCurrentTradePnl(selectedStock, MONDAY_OF_WEEK.minusDays(1)));
                            graphWtd.setWtdMtmPnl(wtdMtmPnlMap.getOrDefault(selectedStock, 0.0));
                            graphWtdPnl.fillInGraph(selectedStock);
                            graphWtdPnl.setMtm(computeWtdMtmPnl(e -> e.getKey().equals(selectedStock)));
                            graphWtdPnl.setTrade(computeWtdTradePnl(e -> e.getKey().equals(selectedStock)));
                            //graphWtdPnl.setNet(mapOp.apply(weekMtmMap,weekTradePnlMap));
                            graphWtdPnl.setNet(computeNet(e -> e.getKey().equals(selectedStock)));
                        }).thenRun(() -> {
                            graphPanel.repaint();
                        });

                    } else {
                        comp.setBackground((indexRow % 2 == 0) ? Color.lightGray : Color.white);
                    }
                    return comp;

                } catch (Exception x) {
                    x.printStackTrace();
                }
                return null;
            }
        };

        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    SwingUtilities.invokeLater(() -> {
                        model.fireTableDataChanged();
                    });
                }
            }
        });


        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 1900;
                d.height = 300;
                return d;
            }
        };

        graphPanel.setLayout(new GridLayout(3, 1));

        JScrollPane jp1 = new JScrollPane(graphYtd) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 200;
                d.width = 1900;
                return d;
            }
        };

        JScrollPane jp2 = new JScrollPane(graphWtd) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 200;
                d.width = 1900;
                return d;
            }
        };

        JScrollPane jp3 = new JScrollPane(graphWtdPnl) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 200;
                d.width = 1900;
                return d;
            }
        };

        graphPanel.add(jp1);
        graphPanel.add(jp2);
        graphPanel.add(jp3);

        JPanel controlPanel = new JPanel();
        JButton refreshButton = new JButton("Refresh");
        JButton ytdButton = new JButton("ytd");
        JButton wtdButton = new JButton("wtd");
        JButton loadTradesButton = new JButton("Load trades");
        JButton computeButton = new JButton("Compute");
        JButton updatePriceButton = new JButton(" update price ");

        refreshButton.addActionListener(al -> {
            refreshAll();
        });

        ytdButton.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                computeYtd();
            }).thenRun(() -> {
                System.out.println(" refreshing from ytd ");
                refreshAll();
            });

        });

        wtdButton.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                computeWtd();
            }).thenRun(() -> {
                System.out.println(" refreshing from wtd ");
                refreshAll();
            });
        });

        loadTradesButton.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                loadTradeList();
            }).thenRun(() -> {
                refreshAll();
            });
        });

        computeButton.addActionListener(l -> {
            CompletableFuture.runAsync(() -> {
                computeNetSharesTradedByDay();
            });

            CompletableFuture.runAsync(() -> {
                computeNetSharesTradedWtd();
            });

            CompletableFuture.runAsync(() -> {
                computeTradingCost();
            });

            CompletableFuture.runAsync(() -> {
                computeWtdCurrentTradePnlAll();
            });

            CompletableFuture.runAsync(() -> {
                computeWtdMtmPnlAll();
            });

            CompletableFuture.runAsync(() -> {
                computeWtdMtmPnl(e -> true);
            });

        });

        updatePriceButton.addActionListener(al -> {
            String line1;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(priceInput)))) {
                while ((line1 = reader.readLine()) != null) {
                    List<String> l = Arrays.asList(line1.split(","));
                    if (l.size() >= 2) {
                        priceMap.put(l.get(0), Double.parseDouble(l.get(1)));
                    } else {
                        System.out.println(" line is wrong " + line1);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });


        controlPanel.setLayout(new FlowLayout());
        controlPanel.add(refreshButton);
        controlPanel.add(ytdButton);
        controlPanel.add(wtdButton);
        controlPanel.add(loadTradesButton);
        controlPanel.add(computeButton);
        controlPanel.add(updatePriceButton);

        this.setLayout(new BorderLayout());
        this.add(controlPanel, BorderLayout.NORTH);
        this.add(scroll, BorderLayout.CENTER);
        this.add(graphPanel, BorderLayout.SOUTH);

        tab.setAutoCreateRowSorter(true);
        sorter = (TableRowSorter<BarModel_China>) tab.getRowSorter();
    }


    static void refreshAll() {
        SwingUtilities.invokeLater(() -> {
            graphWtdPnl.fillInGraph("");
            graphWtdPnl.setMtm(computeWtdMtmPnl(e -> true));
            graphWtdPnl.setTrade(computeWtdTradePnl(e -> true));
            graphWtdPnl.setNet(computeNet(e -> true));
            model.fireTableDataChanged();
            graphPanel.repaint();
        });
    }

    static NavigableMap<LocalDateTime, Double> computeWtdMtmPnl(Predicate<? super Map.Entry> p) {
        LocalDate cutoff = MONDAY_OF_WEEK;
        Map<String, Integer> weekOpenPositionMap = new HashMap<>();
        for (String s : chinaTradeMap.keySet()) {
            int openPos = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isBefore(cutoff))
                    .mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();
            weekOpenPositionMap.put(s, openPos);
        }

        if (weekOpenPositionMap.entrySet().stream().filter(p).mapToInt(Map.Entry::getValue).sum() != 0) {
            weekMtmMap = weekOpenPositionMap.entrySet().stream().filter(p).map(e ->
                    computeMtm(e.getValue(), chinaWtd.get(e.getKey()), lastWeekCloseMap.getOrDefault(e.getKey(), 0.0))).
                    reduce(mapOp).orElse(new TreeMap<>());
            return weekMtmMap;
        }
        return new TreeMap<>();
    }

    static NavigableMap<LocalDateTime, Double> computeMtm(int openPos, NavigableMap<LocalDateTime, SimpleBar> prices, double lastWeekClose) {
        NavigableMap<LocalDateTime, Double> res = new TreeMap<>();
        for (Map.Entry e : prices.entrySet()) {
            LocalDateTime t = (LocalDateTime) e.getKey();
            SimpleBar sb = (SimpleBar) e.getValue();
            res.put(t, openPos * (sb.getClose() - lastWeekClose));
        }
        return res;
    }


    static NavigableMap<LocalDateTime, Double> computeWtdTradePnl(Predicate<? super Map.Entry> p) {
        weekTradePnlMap = chinaTradeMap.entrySet().stream().filter(p).map(e ->
                computeTrade(e.getKey(), chinaWtd.get(e.getKey()), e.getValue()))
                .reduce(mapOp).orElse(new TreeMap<>());
        return weekTradePnlMap;
    }

    static NavigableMap<LocalDateTime, Double> computeTrade(String ticker, NavigableMap<LocalDateTime, SimpleBar> prices,
                                                            NavigableMap<LocalDateTime, ? super Trade> trades) {
        NavigableMap<LocalDateTime, Double> res = new TreeMap<>();
        int currPos = 0;
        double costBasis = 0.0;
        double mv = 0.0;
        for (LocalDateTime lt : prices.keySet()) {
            if (trades.subMap(lt, true, lt.plusMinutes(5), false).size() > 0) {
                currPos += trades.subMap(lt, true, lt.plusMinutes(5), false)
                        .entrySet().stream().mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();
                costBasis += trades.subMap(lt, true, lt.plusMinutes(5), false)
                        .entrySet().stream().mapToDouble(e -> ((Trade) e.getValue()).getCostAll(ticker)).sum();
            }
            mv = currPos * prices.get(lt).getClose();
            res.put(lt, costBasis + mv);
        }
        if (currPos == 0) {
            return new TreeMap<>();
        }
        return res;
    }

    static NavigableMap<LocalDateTime, Double> computeNet(Predicate<? super Map.Entry> p) {
        return mapOp.apply(computeWtdMtmPnl(p), computeWtdTradePnl(p));
    }


//    static <T extends Temporal> BinaryOperator<NavigableMap<T, Double>> mapAcc() {
//        return (a, b) -> Stream.of(a, b).flatMap(e -> e.entrySet().stream())
//                .collect(Collectors.groupingBy(e1 -> e1.getKey(), TreeMap::new, Collectors.summingDouble(
//                        e1 -> e1.getValue())));
//    }


    static void computeYtd() {
        CompletableFuture.runAsync(() -> {
            for (String s : stockList) {
                String tickerFull = s.substring(0, 2).toUpperCase() + "#" + s.substring(2) + ".txt";
                double totalSize = 0.0;
                CompletableFuture.runAsync(() -> {
                    String line;
                    if (s.substring(0, 2).toUpperCase().equals("SH") || s.substring(0, 2).toUpperCase().equals("SZ")) {
                        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(tdxDayPath + tickerFull)))) {
                            while ((line = reader1.readLine()) != null) {
                                List<String> al1 = Arrays.asList(line.split("\t"));
                                if (al1.get(0).startsWith("2017") || al1.get(0).startsWith("2016/1")) {
                                    LocalDate d = LocalDate.parse(al1.get(0), DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                                    if (chinaYtd.containsKey(s)) {
                                        chinaYtd.get(s).put(d, new SimpleBar(Double.parseDouble(al1.get(1)), Double.parseDouble(al1.get(2))
                                                , Double.parseDouble(al1.get(3)), Double.parseDouble(al1.get(4))));
                                    } else {
                                        throw new IllegalStateException(" cannot find stock " + s);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).thenRunAsync(() -> {
                    CompletableFuture.runAsync(() -> {
                        if (chinaYtd.get(s).size() > 0 && chinaYtd.get(s).firstKey().isBefore(MONDAY_OF_WEEK)) {
                            lastWeekCloseMap.put(s, chinaYtd.get(s).lowerEntry(MONDAY_OF_WEEK).getValue().getClose());
                        } else {
                            lastWeekCloseMap.put(s, 0.0);
                        }
                    });

                    CompletableFuture.runAsync(() -> {
                        //do computation
                        //System.out.println(" data is " + s + " " + chinaYtd.get(s));
                        if (chinaYtd.containsKey(s) && chinaYtd.get(s).size() > 1) {
                            NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(chinaYtd.get(s),
                                    LocalDate.of(2016, Month.DECEMBER, 31));
                            double mean = SharpeUtility.getMean(ret);
                            double sdDay = SharpeUtility.getSD(ret);
                            double sr = SharpeUtility.getSharpe(ret, 252);
                            double perc = SharpeUtility.getPercentile(chinaYtd.get(s));
                            ytdResult.get(s).fillResult(mean, sdDay, sr, perc);
                            //System.out.println(Utility.getStrTabbed(" stock mean sd sr perc size firstEntry"
                            //        , s, mean, sdDay, sr, perc, ret.size(), ret.firstEntry(), ret.lastEntry()));
                        } else {
                            System.out.println(" name is less than 1 " + tickerFull);
                        }
                    });
                });
            }

        }).thenRun(() -> {
            System.out.println(" ytd processing end ");
            refreshAll();
        });
    }

    /////////////////// wtd

    static void computeWtd() {
        for (String s : stockList) {
            //System.out.println(" processing wtd for " + s);
            String tickerFull = s.substring(0, 2).toUpperCase() + "#" + s.substring(2) + ".txt";
            String line;

            if (s.substring(0, 2).toUpperCase().equals("SH") || s.substring(0, 2).toUpperCase().equals("SZ")) {
                try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(tdxMinutePath + tickerFull)))) {
                    while ((line = reader1.readLine()) != null) {
                        List<String> al1 = Arrays.asList(line.split("\t"));
                        if (al1.get(0).startsWith("2017/10") && LocalDate.parse(al1.get(0), DATE_PATTERN).isAfter(MONDAY_OF_WEEK.minusDays(1))) {
                            //found = true;
                            LocalDate d = LocalDate.parse(al1.get(0), DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                            LocalTime lt = roundTo5(stringToLocalTime(al1.get(1)));

                            LocalDateTime ldt = LocalDateTime.of(d, lt);

                            if (chinaWtd.containsKey(s)) {
                                if (!chinaWtd.get(s).containsKey(ldt)) {
                                    chinaWtd.get(s).put(LocalDateTime.of(d, lt)
                                            , new SimpleBar(Double.parseDouble(al1.get(2)), Double.parseDouble(al1.get(3))
                                                    , Double.parseDouble(al1.get(4)), Double.parseDouble(al1.get(5))));
                                } else {
                                    chinaWtd.get(s).get(ldt).updateBar(Double.parseDouble(al1.get(2)), Double.parseDouble(al1.get(3))
                                            , Double.parseDouble(al1.get(4)), Double.parseDouble(al1.get(5)));
                                }
                            } else {
                                throw new IllegalStateException(" cannot find stock " + s);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            //do computation
            //System.out.println(" data is " + s + " " + chinaWtd.get(tickerFull));
            if (chinaWtd.containsKey(s) && chinaWtd.get(s).size() > 1) {
                NavigableMap<LocalDateTime, Double> ret = SharpeUtility.getReturnSeries(chinaWtd.get(s),
                        LocalDateTime.of(MONDAY_OF_WEEK.minusDays(1), LocalTime.MIN));
                double mean = SharpeUtility.getMean(ret);
                double sdDay = SharpeUtility.getSD(ret) * Math.sqrt(240);
                double sr = SharpeUtility.getSharpe(ret, 240);
                double perc = SharpeUtility.getPercentile(chinaWtd.get(s));
                wtdResult.get(s).fillResult(mean, sdDay, sr, perc);
                //System.out.println(Utility.getStrTabbed(" stock mean sd sr perc size firstEntry"
                //        , s, mean, sdDay, sr, perc, ret.size(), ret.firstEntry(), ret.lastEntry()));
            } else {
                System.out.println(" name is less than 1 " + tickerFull);
            }
        }
        System.out.println(" wtd processing end ");

    }

    static LocalTime stringToLocalTime(String s) {
        if (s.length() != 4) {
            System.out.println(" length is not equal to 4");
            throw new IllegalArgumentException(" length is not equal to 4 ");
        } else {
            if (s.startsWith("0")) {
                return LocalTime.of(Integer.parseInt(s.substring(1, 2)), Integer.parseInt(s.substring(2)));
            } else {
                return LocalTime.of(Integer.parseInt(s.substring(0, 2)), Integer.parseInt(s.substring(2)));
            }
        }
    }

    static LocalTime roundTo5(LocalTime t) {
        return (t.getMinute() % 5 == 0) ? t : t.plusMinutes(5 - t.getMinute() % 5);
    }

    static LocalDateTime roundTo5Ldt(LocalDateTime t) {
        return LocalDateTime.of(t.toLocalDate(), roundTo5(t.truncatedTo(ChronoUnit.MINUTES).toLocalTime()));
    }


    static void loadTradeList() {
        System.out.println(" loading trade list ");

        chinaTradeMap.keySet().forEach(k -> {
            chinaTradeMap.put(k, new TreeMap<>());
        });

        File f = new File(ChinaMain.GLOBALPATH + "tradeHistoryRecap.txt");
        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "GBK"))) {

            while ((line = reader.readLine()) != null) {
                List<String> l = Arrays.asList(line.split("\t"));
                LocalDate d = LocalDate.parse(l.get(0), DateTimeFormatter.ofPattern("yyyy/M/d"));
                LocalTime t = LocalTime.parse(l.get(1), DateTimeFormatter.ofPattern("H:mm:ss"));
                String ticker = l.get(5).toLowerCase() + l.get(7);
                int q = Integer.parseInt(l.get(10));
                double p = Double.parseDouble(l.get(12));

                LocalDateTime ldt = LocalDateTime.of(d, t);

                if (chinaTradeMap.containsKey(ticker)) {
                    if (chinaTradeMap.get(ticker).containsKey(ldt)) {
                        ((Trade) chinaTradeMap.get(ticker).get(ldt)).merge2(l.get(2).equals("Stock") ? (new NormalTrade(p, q)) :
                                (l.get(2).equals("Margin") ? new MarginTrade(p, q) : new NormalTrade(0, 0)));

                    } else {
                        if (l.get(2).equals("Stock")) {
                            chinaTradeMap.get(ticker).put(ldt, new NormalTrade(p, q));
                            //System.out.println(" inputting trade " + ldt + " " + chinaTradeMap.get(ticker));
                            //System.out.println(" china trade map get ticker " + chinaTradeMap.get(ticker));
                        } else if (l.get(2).equals("Margin")) {
                            chinaTradeMap.get(ticker).put(ldt, new MarginTrade(p, q));
                            //System.out.println(" margin get ticker " + chinaTradeMap.get(ticker));
                        } else if (l.get(2).equals("Dividend")) {
                            chinaTradeMap.get(ticker).put(ldt, new NormalTrade(0.0, q));
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    static void computeNetSharesTradedByDay() {
        for (String s : chinaTradeMap.keySet()) {
            NavigableMap<LocalDate, Integer> res = chinaTradeMap.get(s).entrySet().stream()
                    .collect(Collectors.groupingBy(e1 -> e1.getKey().toLocalDate(), TreeMap::new, Collectors.summingInt(e1 -> ((Trade) e1.getValue()).getSizeAll())));
            netSharesTradedByDay.put(s, res);
        }
        //graphYtd.setTradesMap(netSharesTradedByDay);
    }

    static void computeNetSharesTradedWtd() {
        for (String s : chinaTradeMap.keySet()) {
            NavigableMap<LocalDateTime, Integer> res =
                    chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                            .collect(Collectors.groupingBy(e1 -> roundTo5Ldt(e1.getKey()), TreeMap::new, Collectors.summingInt(e1 -> ((Trade) e1.getValue()).getSizeAll())));
            netSharesTradedWtd.put(s, res);
        }
        //graphWtd.setTradesMap();
    }

    static void computeTradingCost() {
        for (String s : chinaTradeMap.keySet()) {
            double tradingCost = chinaTradeMap.get(s).entrySet().stream()
                    .mapToDouble(e -> getTradingCostCustom(s, e.getKey().toLocalDate(), (Trade) e.getValue())).sum();
            //.collect(Collectors.summingDouble(e -> ((Trade) e.getValue()).getTradingCost(s)));
            double costBasis = chinaTradeMap.get(s).entrySet().stream()
                    .mapToDouble(e -> getCostWithCommissionsCustom(s, e.getKey().toLocalDate(), (Trade) e.getValue())).sum();
            //.collect(Collectors.summingDouble(e -> ((Trade) e.getValue()).getCostWithCommission(s)));
            totalTradingCostMap.put(s, tradingCost);
            costBasisMap.put(s, costBasis);
        }
    }

    static double getTradingCostCustom(String name, LocalDate ld, Trade t) {
        if (ld.isBefore(LocalDate.of(2016, Month.NOVEMBER, 3))) {
            return t.getTradingCostCustomBrokerage(name, 3.1);
        } else {
            return t.getTradingCostCustomBrokerage(name, 2.0);
        }
    }

    static double getCostWithCommissionsCustom(String name, LocalDate ld, Trade t) {
        if (ld.isBefore(LocalDate.of(2016, Month.NOVEMBER, 3))) {
            return t.getCostWithCommissionCustomBrokerage(name, 3.1);
        } else {
            return t.getCostWithCommissionCustomBrokerage(name, 2.0);
        }
    }


    static double computeCurrentTradePnl(String s, LocalDate cutoff) {
        double costBasis = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(cutoff))
                .mapToDouble(e -> ((Trade) e.getValue()).getCostWithCommission(s)).sum();
        int netPosition = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(cutoff))
                .mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();

        return netPosition * priceMap.getOrDefault(s, 0.0) + costBasis;
    }

    static void computeWtdCurrentTradePnlAll() {
        for (String s : chinaTradeMap.keySet()) {
            double costBasis = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                    .mapToDouble(e -> ((Trade) e.getValue()).getCostWithCommission(s)).sum();
            int netPosition = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                    .mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();

            wtdTradePnlMap.put(s, netPosition * priceMap.getOrDefault(s, 0.0) + costBasis);
        }
    }

    static void computeWtdMtmPnlAll() {
        for (String s : chinaTradeMap.keySet()) {
            int posBeforeThisWeek = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isBefore(MONDAY_OF_WEEK))
                    .mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();
            wtdMtmPnlMap.put(s, posBeforeThisWeek * (priceMap.getOrDefault(s, 0.0) - lastWeekCloseMap.getOrDefault(s, 0.0)));
        }
    }

    public static void main(String[] args) {
        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1500, 1500));
        jf.setLayout(new FlowLayout());
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        HistChinaStocks hc = new HistChinaStocks();
        jf.add(hc);
        jf.setVisible(true);
    }

    class ChinaResult {

        double meanRtn;
        double sd;
        double sr;
        double perc;

        ChinaResult() {
            meanRtn = 0.0;
            sd = 0.0;
            sr = 0.0;
            perc = 0.0;
        }

        ChinaResult(double m, double s, double r, double p) {
            meanRtn = Math.round(1000d * m) / 10d;
            sd = Math.round(1000d * s * Math.sqrt(252)) / 10d;
            sr = Math.round(100d * r) / 100d;
            perc = p;
        }

        public void fillResult(double m, double s, double r, double p) {
            meanRtn = Math.round(1000d * m) / 10d;
            sd = Math.round(1000d * s * Math.sqrt(252)) / 10d;
            sr = Math.round(100d * r) / 100d;
            perc = p;
        }


        public double getMeanRtn() {
            return meanRtn;
        }

        public double getSd() {
            return sd;
        }

        public double getSr() {
            return sr;
        }

        public double getPerc() {
            return perc;
        }


    }


    private class BarModel_China extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return stockList.size();
        }

        @Override
        public int getColumnCount() {
            return 25;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "ticker";
                case 1:
                    return "chn";
                case 2:
                    return "Y Mean";
                case 3:
                    return "Y sd";
                case 4:
                    return "Y sr";
                case 5:
                    return "Y perc";
                case 6:
                    return "Y n";
                case 7:
                    return "W mean";
                case 8:
                    return "W sd";
                case 9:
                    return "W sr";
                case 10:
                    return "W perc";
                case 11:
                    return "W n";
                case 12:
                    return " Trades n";
                case 13:
                    return "pos";
                case 14:
                    return "Trans Cost";
                case 15:
                    return "p";
                case 16:
                    return "Delta";
                case 17:
                    return "cost basis";
                case 18:
                    return "Net Pnl";
                case 19:
                    return "Pnl/cost";
                case 20:
                    return "w Tr pnl";
                case 21:
                    return "last week P";
                case 22:
                    return "w Mtm";
                case 23:
                    return "w net";
                default:
                    return "";

            }
        }

        @Override
        public Object getValueAt(int row, int col) {
            String name = stockList.get(row);
            int currPos = 0;
            if (netSharesTradedByDay.containsKey(name) && netSharesTradedByDay.get(name).size() > 0) {
                currPos = netSharesTradedByDay.get(name).entrySet().stream().mapToInt(Map.Entry::getValue).sum();
            }
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return nameMap.getOrDefault(name, "");
                case 2:
                    return ytdResult.get(name).getMeanRtn();
                case 3:
                    return ytdResult.get(name).getSd();
                case 4:
                    return ytdResult.get(name).getSr();
                case 5:
                    return ytdResult.get(name).getPerc();
                case 6:
                    return chinaYtd.get(name).size();
                case 7:
                    return wtdResult.get(name).getMeanRtn();
                case 8:
                    return wtdResult.get(name).getSd();
                case 9:
                    return wtdResult.get(name).getSr();
                case 10:
                    return wtdResult.get(name).getPerc();
                case 11:
                    return chinaWtd.get(name).size();
                case 12:
                    return chinaTradeMap.get(name).size();
                case 13:
                    return currPos;
                case 14:
                    return totalTradingCostMap.getOrDefault(name, 0.0);
                case 15:
                    return priceMap.getOrDefault(name, 0.0);
                case 16:
                    return priceMap.getOrDefault(name, 0.0) * currPos;
                case 17:
                    return costBasisMap.getOrDefault(name, 0.0);
                case 18:
                    return priceMap.getOrDefault(name, 0.0) * currPos
                            + costBasisMap.getOrDefault(name, 0.0);
                case 19:
                    if (totalTradingCostMap.getOrDefault(name, 1.0) != 0.0) {
                        return Math.round((priceMap.getOrDefault(name, 0.0) * currPos
                                + costBasisMap.getOrDefault(name, 0.0)) / (totalTradingCostMap.getOrDefault(name, 1.0)));
                    } else {
                        return 0L;
                    }
                case 20:
                    return wtdTradePnlMap.getOrDefault(name, 0.0);
                case 21:
                    return lastWeekCloseMap.getOrDefault(name, 0.0);
                case 22:
                    return wtdMtmPnlMap.getOrDefault(name, 0.0);
                case 23:
                    return wtdTradePnlMap.getOrDefault(name, 0.0) + wtdMtmPnlMap.getOrDefault(name, 0.0);
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
                case 6:
                    return Integer.class;
                case 11:
                    return Integer.class;
                case 12:
                    return Integer.class;
                case 13:
                    return Integer.class;
                case 19:
                    return Long.class;
                default:
                    return Double.class;
            }
        }
    }

}

class ChinaTrade {
    String ticker;
    LocalDateTime tradeTime;
    double price;
    int quantity;

    public ChinaTrade() {
        ticker = "";
        tradeTime = LocalDateTime.now();
        price = 0.0;
        quantity = 0;
    }

    public ChinaTrade(String s, LocalDateTime t, double p, int q) {
        ticker = s;
        tradeTime = t;
        price = p;
        quantity = q;
    }
}

