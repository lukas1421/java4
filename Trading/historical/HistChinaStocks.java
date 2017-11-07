package historical;

import TradeType.MarginTrade;
import TradeType.NormalTrade;
import TradeType.Trade;
import apidemo.ChinaMain;
import apidemo.TradingConstants;
import auxiliary.SimpleBar;
import client.ExecutionFilter;
import graph.GraphBarTemporal;
import graph.GraphChinaPnl;
import handler.SGXPositionHandler;
import handler.SGXReportHandler;
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
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static apidemo.ChinaData.priceMapBar;
import static apidemo.ChinaPosition.tradesMap;
import static utility.Utility.*;

public class HistChinaStocks extends JPanel {

    private static JPanel graphPanel;
    private static BarModel_China model;
    private static JTable tab;

    private static final LocalDate LAST_YEAR_END = LocalDate.of(2016, 12, 31);
    public static final LocalDate MONDAY_OF_WEEK = Utility.getMondayOfWeek(LocalDateTime.now());

    public static final String GLOBALPATH = "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\Trading\\";

    public static Map<String, String> nameMap = new HashMap<>();

    private static volatile GraphBarTemporal<LocalDate> graphYtd = new GraphBarTemporal<>();
    private static volatile GraphBarTemporal<LocalDateTime> graphWtd = new GraphBarTemporal<>();
    private static volatile GraphChinaPnl<LocalDateTime> graphWtdPnl = new GraphChinaPnl<>();

    private File chinaInput = new File(GLOBALPATH + "ChinaAll.txt");
    private File priceInput = new File(GLOBALPATH + "pricesTodayYtd.csv");
    private File sgxOutput = new File(GLOBALPATH + "sgxWtdOutput.txt");

    private static List<String> stockList = new LinkedList<>();
    private static volatile Map<String, NavigableMap<LocalDate, SimpleBar>> chinaYtd = new HashMap<>();
    public static volatile Map<String, NavigableMap<LocalDateTime, SimpleBar>> chinaWtd = new HashMap<>();

    private static Map<String, ChinaResult> ytdResult = new HashMap<>();
    private static Map<String, ChinaResult> wtdResult = new HashMap<>();

    private static Predicate<LocalTime> chinaTradingTime = t -> (t.isAfter(LocalTime.of(9, 29)) && t.isBefore(LocalTime.of(11, 31))) ||
            (t.isAfter(LocalTime.of(12, 59)) && t.isBefore(LocalTime.of(15, 1)));

    public static Map<String, NavigableMap<LocalDateTime, ? super Trade>> chinaTradeMap = new HashMap<>();


    public static volatile Map<String, Integer> weekOpenPositionMap = new HashMap<>();
    public static volatile Map<String, Integer> wtdChgInPosition = new HashMap<>();
    public static volatile Map<String, Integer> currentPositionMap = new HashMap<>();

    public static Map<String, NavigableMap<LocalDate, Integer>> netSharesTradedByDay = new HashMap<>();
    public static Map<String, NavigableMap<LocalDateTime, Integer>> netSharesTradedWtd = new HashMap<>();

    static Map<String, Double> priceMapForHist = new HashMap<>();

    static Map<String, Double> lastWeekCloseMap = new HashMap<>();
    static Map<String, Double> totalTradingCostMap = new HashMap<>();
    static Map<String, Double> costBasisMap = new HashMap<>();
    static Map<String, Double> netTradePnlMap = new HashMap<>();
    static Map<String, Double> wtdTradePnlMap = new HashMap<>();
    static Map<String, Double> wtdMtmPnlMap = new HashMap<>();
    static BinaryOperator<NavigableMap<LocalDateTime, Double>> mapOp = (a, b) -> Stream.of(a, b).flatMap(e -> e.entrySet().stream())
            .collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new, Collectors.summingDouble(
                    Map.Entry::getValue)));

    //static BinaryOperator<NavigableMap<LocalDateTime, Double>> mapOp2 = (a,b) -> Stream.of(a,b).

    static volatile NavigableMap<LocalDateTime, Double> weekMtmMap = new ConcurrentSkipListMap<>();
    static volatile NavigableMap<LocalDateTime, Double> weekTradePnlMap = new ConcurrentSkipListMap<>();
    static volatile NavigableMap<LocalDateTime, Double> weekNetMap = new ConcurrentSkipListMap<>();

    static volatile NavigableMap<LocalDate, Double> netPnlByWeekday = new ConcurrentSkipListMap<>();
    static volatile NavigableMap<LocalDate, Double> netPnlByWeekdayAM = new ConcurrentSkipListMap<>();
    static volatile NavigableMap<LocalDate, Double> netPnlByWeekdayPM = new ConcurrentSkipListMap<>();

    static Map<String, Double> fxMap = new HashMap<>();

    int avgPercentile;
    int weightedAvgPercentile;
    static volatile boolean filterOn =  false;
    static final int CHG_POS_COL = 14;
    static final int CURR_POS_COL= 15;


    private static String tdxDayPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export\\" : "J:\\TDX\\T0002\\export\\";

    private static String tdxMinutePath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export_1m\\" : "J:\\TDX\\T0002\\export_1m\\";

    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    public static volatile LocalDate recentTradingDate;

    static volatile Predicate<? super Map.Entry<String, ?>> MTM_PRED = m -> true;


    private int modelRow;
    int indexRow;
    private static volatile String selectedStock = "";
    private TableRowSorter<BarModel_China> sorter;

    public HistChinaStocks() {
        String line;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "fx.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                fxMap.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(
                TradingConstants.GLOBALPATH + "mostRecentTradingDate.txt")))) {
            line = reader.readLine();
            recentTradingDate = max(LocalDate.parse(line, DateTimeFormatter.ofPattern("yyyy-MM-dd")), getMondayOfWeek(LocalDateTime.now()));
        } catch (IOException io) {
            io.printStackTrace();
        }

//        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(priceInput)))) {
//            while ((line = reader.readLine()) != null) {
//                List<String> l = Arrays.asList(line.split(","));
//                if (l.size() >= 2) {
//                    priceMapForHist.put(l.get(0), Double.parseDouble(l.get(1)));
//                } else {
//                    System.out.println(" line is wrong " + line);
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(chinaInput), "GBK"))) {
            while ((line = reader.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));

                if (!al1.get(0).equals("sh204001") && (al1.get(0).startsWith("sh") || al1.get(0).startsWith("sz")
                        || al1.get(0).startsWith("SGX"))) {
                    chinaYtd.put(al1.get(0), new ConcurrentSkipListMap<>());
                    chinaWtd.put(al1.get(0), new ConcurrentSkipListMap<>());
                    stockList.add(al1.get(0));
                    nameMap.put(al1.get(0), al1.get(1));
                    ytdResult.put(al1.get(0), new ChinaResult());
                    wtdResult.put(al1.get(0), new ChinaResult());
                    chinaTradeMap.put(al1.get(0), new ConcurrentSkipListMap<>());
                    netSharesTradedByDay.put(al1.get(0), new ConcurrentSkipListMap<>());
                    netSharesTradedWtd.put(al1.get(0), new ConcurrentSkipListMap<>());
                    totalTradingCostMap.put(al1.get(0), 0.0);
                    costBasisMap.put(al1.get(0), 0.0);
                    wtdTradePnlMap.put(al1.get(0), 0.0);
                    lastWeekCloseMap.put(al1.get(0), 0.0);
                    wtdMtmPnlMap.put(al1.get(0), 0.0);
                    wtdChgInPosition.put(al1.get(0), 0);
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
                            CompletableFuture.runAsync(() -> {
                                graphYtd.fillInGraphChinaGen(selectedStock, chinaYtd);
                                graphYtd.setTradesMap(netSharesTradedByDay.get(selectedStock));
                                graphYtd.setTradePnl(computeCurrentTradePnl(selectedStock, LAST_YEAR_END));
                            });

                            CompletableFuture.runAsync(() -> {
                                graphWtd.fillInGraphChinaGen(selectedStock, chinaWtd);
                                graphWtd.setTradesMap(netSharesTradedWtd.get(selectedStock));
                                graphWtd.setTradePnl(computeCurrentTradePnl(selectedStock, MONDAY_OF_WEEK.minusDays(1)));
                                graphWtd.setWtdMtmPnl(wtdMtmPnlMap.getOrDefault(selectedStock, 0.0));
                            });

                            CompletableFuture.runAsync(() -> {
                                if (chinaTradeMap.containsKey(selectedStock) && chinaTradeMap.get(selectedStock).size() > 0) {

                                    weekMtmMap = computeWtdMtmPnl(e -> e.getKey().equals(selectedStock));
                                    weekTradePnlMap = computeWtdTradePnl(e -> e.getKey().equals(selectedStock));
                                    weekNetMap = computeNet(e -> e.getKey().equals(selectedStock));
                                    System.out.println(" seleced stock is " + selectedStock + "  trade : "
                                            + wtdTradePnlMap.getOrDefault(selectedStock, 0.0) + "  mtm: "
                                            + wtdMtmPnlMap.getOrDefault(selectedStock,0.0));



                                    SwingUtilities.invokeLater(() -> {
                                        graphWtdPnl.setMtm(weekMtmMap);
                                        graphWtdPnl.setTrade(weekTradePnlMap);
                                        graphWtdPnl.setNet(weekNetMap);
                                        graphWtdPnl.setWeekdayMtm(netPnlByWeekday, netPnlByWeekdayAM, netPnlByWeekdayPM);
                                        graphWtdPnl.fillInGraph(selectedStock);
                                    });
                                } else {
                                    graphWtdPnl.clearGraph();
                                }
                            });

                            CompletableFuture.runAsync(() -> {
                                avgPercentile = computeAvgPercentile(e -> e.getKey().equals(selectedStock));
                                weightedAvgPercentile = computeDeltaWeightedPercentile(e -> e.getKey().equals(selectedStock));

                                SwingUtilities.invokeLater(() -> {
                                    graphWtdPnl.setAvgPerc(avgPercentile);
                                    graphWtdPnl.setDeltaWeightedAveragePerc(weightedAvgPercentile);
                                });

                            });

                        }).thenRun(() -> {
                            SwingUtilities.invokeLater(() -> {
                                graphPanel.repaint();
                            });
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
        JButton getTodayDataButton = new JButton(" Today Data");
        JButton getTodayTradesButton = new JButton("Today trades");
        JButton liveUpdateButton = new JButton("Compute");
        JButton stopButton = new JButton("stop");
        JButton sgxDataButton = new JButton("SGX Data");
        JButton sgxTradesButton = new JButton(" SGX Trades");
        JToggleButton noFutButton = new JToggleButton(" no fut");
        JToggleButton futOnlyButton = new JToggleButton("fut only");
        JButton outputWtdButton = new JButton(" output wtd ");
        JButton activeOnlyButton = new JButton("Active Only");


        activeOnlyButton.addActionListener(l->{
            if (filterOn) {
                sorter.setRowFilter(null);
                filterOn = false;
            } else {
                List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
                filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.NOT_EQUAL, 0, CHG_POS_COL));
                filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.NOT_EQUAL, 0, CURR_POS_COL));
                sorter.setRowFilter(RowFilter.orFilter(filters));
                filterOn = true;
            }
        });

        sgxTradesButton.addActionListener(al -> {
            getSGXPosition();
            getSGXTrades();
        });

        sgxDataButton.addActionListener(l -> {
            CompletableFuture.runAsync(() -> {
                ChinaMain.controller().getSGXA50HistoricalCustom(20000, HistChinaStocks::handleSGXA50WtdData, 7);
            });
        });

        noFutButton.addActionListener(l -> {
            if (noFutButton.isSelected()) {
                MTM_PRED = m -> !m.getKey().equals("SGXA50");
            } else {
                MTM_PRED = m -> true;
            }
        });

        futOnlyButton.addActionListener(l -> {
            if (futOnlyButton.isSelected()) {
                MTM_PRED = m -> m.getKey().equals("SGXA50");
            } else {
                MTM_PRED = m -> true;
            }
        });

        outputWtdButton.addActionListener(l -> {
            if (chinaWtd.containsKey(selectedStock)) {
                System.out.println(" outputting to file for " + selectedStock);
                clearFile(sgxOutput);
                chinaWtd.get(selectedStock).forEach((key, value) -> simpleWriteToFile(
                        Utility.getStrTabbed(key, value.getOpen(), value.getHigh()
                                , value.getLow(), value.getClose()), true, sgxOutput));
            } else {
                System.out.println(" cannot find stock for outtputting ytd " + selectedStock);
            }
        });

        refreshButton.addActionListener(al -> {
            //computeButton.doClick();
            refreshAll();

        });

        ytdButton.addActionListener(al -> {
            CompletableFuture.runAsync(HistChinaStocks::computeYtd).thenRun(() -> {
                System.out.println(" ytd ended ");
                SwingUtilities.invokeLater(() -> {
                    model.fireTableDataChanged();
                    this.repaint();
                });
            });

        });

        wtdButton.addActionListener(al -> {
            CompletableFuture.runAsync(HistChinaStocks::computeWtd).thenRun(() -> {
                System.out.println(" wtd ended ");
                SwingUtilities.invokeLater(() -> {
                    model.fireTableDataChanged();
                    this.repaint();
                });
            });
            CompletableFuture.runAsync(() -> {
                ChinaMain.controller().getSGXA50HistoricalCustom(20000, HistChinaStocks::handleSGXA50WtdData, 7);
            });
        });

        loadTradesButton.addActionListener(al -> {
            CompletableFuture.runAsync(HistChinaStocks::loadTradeList).thenRun(() -> {
                System.out.println(" loading trade list finished ");
                computePosition();
            });

            CompletableFuture.runAsync(() -> {
                getSGXPosition();
                getSGXTrades();
            }).thenRun(() -> SwingUtilities.invokeLater(this::repaint));
            //computeButton.doClick();
            //refreshAll();
        });

        computeButton.addActionListener(l -> {
            CompletableFuture.runAsync(() -> {
                CompletableFuture.runAsync(HistChinaStocks::computeNetSharesTradedByDay);

                CompletableFuture.runAsync(HistChinaStocks::computeNetSharesTradedWtd);

                CompletableFuture.runAsync(HistChinaStocks::computeTradingCost);

                CompletableFuture.runAsync(HistChinaStocks::computeWtdCurrentTradePnlAll);

                CompletableFuture.runAsync(HistChinaStocks::computeWtdMtmPnlAll);

                CompletableFuture.runAsync(() -> {
                    computeWtdMtmPnl(e -> true);
                });
            }).thenRun(() -> SwingUtilities.invokeLater(()->{
                model.fireTableDataChanged();
                this.repaint();
            }));

        });

        updatePriceButton.addActionListener(al -> {
            updatePrices();
            SwingUtilities.invokeLater(() -> {
                model.fireTableDataChanged();
            });
        });

        getTodayDataButton.addActionListener(al -> {
            for (String s : chinaWtd.keySet()) {
                if (priceMapBar.containsKey(s) && priceMapBar.get(s).size() > 0) {
                    NavigableMap<LocalDateTime, SimpleBar> res = mergeMap(chinaWtd.get(s),
                            Utility.priceMapToLDT(priceMap1mTo5M(priceMapBar.get(s)), recentTradingDate));
                    chinaWtd.put(s, res);
                }
            }
            SwingUtilities.invokeLater(() -> {
                model.fireTableDataChanged();
            });
        });

        getTodayTradesButton.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                for (String s : chinaTradeMap.keySet()) {
                    if (tradesMap.containsKey(s) && tradesMap.get(s).size() > 0) {
                        NavigableMap<LocalDateTime, ? super Trade> res = mergeTradeMap(chinaTradeMap.get(s),
                                Utility.priceMapToLDT(tradesMap.get(s), recentTradingDate));
                        chinaTradeMap.put(s, res);
                    }
                }
            }).thenRun(HistChinaStocks::computePosition);
            SwingUtilities.invokeLater(() -> {
                model.fireTableDataChanged();
            });
        });


        controlPanel.setLayout(new FlowLayout());
        controlPanel.add(refreshButton);
        controlPanel.add(getTodayDataButton);
        controlPanel.add(getTodayTradesButton);
        controlPanel.add(ytdButton);
        controlPanel.add(wtdButton);

        controlPanel.add(loadTradesButton);
        controlPanel.add(computeButton);
        controlPanel.add(updatePriceButton);

        controlPanel.add(sgxDataButton);
        controlPanel.add(sgxTradesButton);
        controlPanel.add(noFutButton);
        controlPanel.add(futOnlyButton);
        controlPanel.add(outputWtdButton);
        controlPanel.add(activeOnlyButton);

        this.setLayout(new BorderLayout());
        this.add(controlPanel, BorderLayout.NORTH);
        this.add(scroll, BorderLayout.CENTER);
        this.add(graphPanel, BorderLayout.SOUTH);

        tab.setAutoCreateRowSorter(true);

        sorter = (TableRowSorter<BarModel_China>) tab.getRowSorter();
    }

    static void updatePrices() {
        chinaWtd.forEach((k, v) -> {
            if (v.size() > 0) {
                priceMapForHist.put(k, v.lastEntry().getValue().getClose());
            }
        });
    }


    static void handleSGXA50WtdData(String date, double open, double high, double low, double close, int volume) {

        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));


            if (ld.isAfter(HistChinaStocks.MONDAY_OF_WEEK.minusDays(1L))) {
                if ((lt.isAfter(LocalTime.of(8, 59)) && lt.isBefore(LocalTime.of(11, 31)))
                        || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1)))) {

                    LocalDateTime ldt = LocalDateTime.of(ld, lt);
                    LocalDateTime ltTo5 = Utility.roundTo5Ldt(ldt);
                    if (!chinaWtd.get("SGXA50").containsKey(ltTo5)) {
                        chinaWtd.get("SGXA50").put(ltTo5, new SimpleBar(open, high, low, close));
                    } else {
                        chinaWtd.get("SGXA50").get(ltTo5).updateBar(open, high, low, close);
                    }
                }
            } else {
                System.out.println(" updating close of SGXA50 ");
                HistChinaStocks.lastWeekCloseMap.put("SGXA50", close);
            }
        } else {
            priceMapForHist.put("SGXA50", chinaWtd.get("SGXA50").lastEntry().getValue().getClose());
            //costBasisMap.put("SGXA50", HistChinaStocks.lastWeekCloseMap.get(close));
            //System.out.println(getStr(date, open, high, low, close));
        }

    }

    public static void getSGXPosition() {
        System.out.println(" getting sgx position ");
        //HistChinaStocks.currentPositionMap.put("SGXA50", 0);
        ChinaMain.controller().reqPositions(new SGXPositionHandler());
    }

    public static void getSGXTrades() {

        System.out.println(" getting sgx trades ");
        HistChinaStocks.chinaTradeMap.put("SGXA50", new ConcurrentSkipListMap<>());
        ChinaMain.controller().reqExecutions(new ExecutionFilter(), new SGXReportHandler());
    }


    static int computeAvgPercentile(Predicate<? super Map.Entry<String, ?>> p) {
        return (int) Math.round(chinaWtd.entrySet().stream().filter(e -> getCurrentPos(e.getKey()) != 0)
                .filter(p).mapToDouble(e -> SharpeUtility.getPercentile(e.getValue())).average().orElse(0.0));
    }

    static int computeDeltaWeightedPercentile(Predicate<? super Map.Entry<String, ?>> p) {
        //double sumDelta = stockList.stream().mapToDouble(s->getCurrentPos(s)*priceMap.getOrDefault(s,0.0)).sum();
        double sumDelta = chinaWtd.entrySet().stream().filter(p).mapToDouble(e -> getCurrentDelta(e.getKey())).sum();
        //System.out.println(" sum delta is " + sumDelta);
        return (int) Math.round(chinaWtd.entrySet().stream().filter(e -> getCurrentPos(e.getKey()) > 0).filter(p)
                .mapToDouble(e -> getCurrentDelta(e.getKey()) / sumDelta * SharpeUtility.getPercentile(e.getValue()))
                .sum());

//                        .sorted(reverseThis(Comparator.comparingDouble(e->getCurrentDelta(e.getKey()))))
//                .peek(e->System.out.println(e.getKey() + " Delta: " + getCurrentDelta(e.getKey())
//                        + " pos: " + getCurrentPos(e.getKey()) + " p: "+ SharpeUtility.getPercentile(e.getValue())
//                        + " first " + e.getValue().firstEntry()
//                        + " last " + e.getValue().lastEntry()
//                        + " max " + e.getValue().entrySet().stream().mapToDouble(e1->e1.getValue().getHigh()).max().orElse(0.0)
//                        + " min " + e.getValue().entrySet().stream().mapToDouble(e1->e1.getValue().getLow()).min().orElse(0.0)))

    }

    static double getCurrentDelta(String name) {
        return fxMap.getOrDefault(name, 1.0) * getCurrentPos(name) * priceMapForHist.getOrDefault(name, 0.0);
    }


    static int getCurrentPos(String name) {
        return currentPositionMap.getOrDefault(name, 0);
        //return chinaTradeMap.get(name).entrySet().stream().mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();
    }

    private static void refreshAll() {
        //System.out.println(" mtm_ pred " + MTM_PRED);
        CompletableFuture.runAsync(() -> {
            graphWtdPnl.fillInGraph("");
            graphWtdPnl.setTrade(computeWtdTradePnl(MTM_PRED));
            graphWtdPnl.setNet(computeNet(MTM_PRED));
            graphWtdPnl.setAvgPerc(computeAvgPercentile(MTM_PRED));
            graphWtdPnl.setDeltaWeightedAveragePerc(computeDeltaWeightedPercentile(MTM_PRED));
        }).thenRun(() -> {
            SwingUtilities.invokeLater(() -> {
                graphWtdPnl.setMtm(computeWtdMtmPnl(MTM_PRED));
                if (selectedStock.equals("SGXA50")) {
                    System.out.println(weekMtmMap);
                }
                graphWtdPnl.setWeekdayMtm(netPnlByWeekday, netPnlByWeekdayAM, netPnlByWeekdayPM);
                model.fireTableDataChanged();
                graphWtdPnl.repaint();
            });
        });
    }

    static void computePosition() {
        for (String s : chinaTradeMap.keySet()) {
            if (!s.equals("SGXA50")) {
                int openPos = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isBefore(MONDAY_OF_WEEK))
                        .mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();
                int thisWeekPos = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1L)))
                        .mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();
                int currentPos = chinaTradeMap.get(s).entrySet().stream().mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();
                weekOpenPositionMap.put(s, openPos);
                wtdChgInPosition.put(s, thisWeekPos);
                currentPositionMap.put(s, currentPos);
            }
        }
    }

    private static NavigableMap<LocalDateTime, Double> computeWtdMtmPnl(Predicate<? super Map.Entry<String, ?>> p) {

        weekOpenPositionMap.put("SGXA50", currentPositionMap.getOrDefault("SGXA50", 0)
                - wtdChgInPosition.getOrDefault("SGXA50", 0));

        //if (weekOpenPositionMap.entrySet().stream().filter(p).mapToInt(Map.Entry::getValue).sum() != 0) {
        weekMtmMap = weekOpenPositionMap.entrySet().stream().filter(p).map(e ->
                computeMtm(e.getKey(), e.getValue(), chinaWtd.get(e.getKey()), lastWeekCloseMap.getOrDefault(e.getKey(), 0.0))).
                reduce(mapOp).orElse(new ConcurrentSkipListMap<>());
        return weekMtmMap;
        //}
        //return new ConcurrentSkipListMap<>();
    }

    private static NavigableMap<LocalDateTime, Double> computeMtm(String ticker, int openPos, NavigableMap<LocalDateTime, SimpleBar> prices, double lastWeekClose) {
        NavigableMap<LocalDateTime, Double> res = new ConcurrentSkipListMap<>();
        double fx = fxMap.getOrDefault(ticker, 1.0);


        prices.forEach((k, v) -> {
            if (chinaTradingTime.test(k.toLocalTime())) {
                res.put(k, fx * openPos * (v.getClose() - lastWeekClose));
            }
        });

//        for (Map.Entry e : prices.entrySet()) {
//            LocalDateTime t = (LocalDateTime) e.getKey();
//            SimpleBar sb = (SimpleBar) e.getValue();
//            res.put(t, fx * openPos * (sb.getClose() - lastWeekClose));
//        }
        return res;
    }


    private static NavigableMap<LocalDateTime, Double> computeWtdTradePnl(Predicate<? super Map.Entry<String, ?>> p) {
        weekTradePnlMap = chinaTradeMap.entrySet().stream().filter(p).map(e ->
                computeTrade(e.getKey(), chinaWtd.get(e.getKey()), e.getValue()))
                .reduce(mapOp).orElse(new ConcurrentSkipListMap<>());
        return weekTradePnlMap;
    }

    private static NavigableMap<LocalDateTime, Double> computeTrade(String ticker, NavigableMap<LocalDateTime, SimpleBar> prices,
                                                                    NavigableMap<LocalDateTime, ? super Trade> trades) {
        NavigableMap<LocalDateTime, Double> res = new ConcurrentSkipListMap<>();
        int currPos = 0;
        double costBasis = 0.0;
        double mv = 0.0;
        double fx = fxMap.getOrDefault(ticker, 1.0);
        for (LocalDateTime lt : prices.keySet()) {

            if (trades.subMap(lt, true, lt.plusMinutes(5), false).size() > 0) {
                currPos += trades.subMap(lt, true, lt.plusMinutes(5), false)
                        .entrySet().stream().mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();
                costBasis += trades.subMap(lt, true, lt.plusMinutes(5), false)
                        .entrySet().stream().mapToDouble(e -> ((Trade) e.getValue()).getCostAll(ticker)).sum();
            }
            mv = currPos * prices.get(lt).getClose();

            if (chinaTradingTime.test(lt.toLocalTime())) {
                res.put(lt, fx * (costBasis + mv));
            }
        }
        if (currPos == 0) {
            return new ConcurrentSkipListMap<>();
        }
//        if (ticker.equals("SGXA50")) {
//            System.out.println(" SGX trade map is " + res);
//        }
        return res;
    }

    private static NavigableMap<LocalDateTime, Double> computeNet(Predicate<? super Map.Entry<String, ?>> p) {
        NavigableMap<LocalDateTime, Double> res = mapOp.apply(computeWtdMtmPnl(p), computeWtdTradePnl(p));
        //System.out.println(" computeNet is " + )
        computeNetPnlByWeekday(res);
        return res;
    }

    private static void computeNetPnlByWeekday(NavigableMap<LocalDateTime, Double> mp) {


        netPnlByWeekday = mp.keySet().stream().map(LocalDateTime::toLocalDate).distinct().collect(Collectors.toMap(d -> d,
                d -> computeNetPnlForGivenDate(mp, (LocalDate) d), (a, b) -> a, ConcurrentSkipListMap::new));

        netPnlByWeekdayAM = mp.keySet().stream().map(LocalDateTime::toLocalDate).distinct().collect(Collectors.toMap(d -> d,
                d -> computeAMNetPnlForGivenDate(mp, (LocalDate) d), (a, b) -> a, ConcurrentSkipListMap::new));

        netPnlByWeekdayPM = mp.keySet().stream().map(LocalDateTime::toLocalDate).distinct().collect(Collectors.toMap(d -> d,
                d -> computePMNetPnlForGivenDate(mp, (LocalDate) d), (a, b) -> a, ConcurrentSkipListMap::new));

//        System.out.println(" net by week day " + netPnlByWeekday + " " + netPnlByWeekday.values().stream().mapToDouble(e-> e).sum());
//        System.out.println(" am " + netPnlByWeekdayAM + " " + netPnlByWeekdayAM.values().stream().mapToDouble(e-> e).sum());
//        System.out.println(" pm " + netPnlByWeekdayPM + " " +  netPnlByWeekdayPM.values().stream().mapToDouble(e-> e).sum());
//        System.out.println(" am pm " + Stream.of(netPnlByWeekdayAM, netPnlByWeekdayPM).flatMap(e->e.entrySet().stream())
//                .collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new,Collectors.summingDouble(Map.Entry::getValue))));
//        System.out.println(" week day all " + (Double) netPnlByWeekday.entrySet().stream().mapToDouble(Map.Entry::getValue).sum());

    }

    private static double computeNetPnlForGivenDate(NavigableMap<LocalDateTime, Double> mp, LocalDate d) {
        double lastV = mp.floorEntry(LocalDateTime.of(d, LocalTime.of(15, 0))).getValue();
        LocalDateTime ytdClose = LocalDateTime.of(d.minusDays(1), LocalTime.of(15, 0));
        double prevV = mp.firstKey().isBefore(ytdClose) ? mp.floorEntry(ytdClose).getValue() : 0.0;
        return lastV - prevV;
    }

    private static double computeAMNetPnlForGivenDate(NavigableMap<LocalDateTime, Double> mp, LocalDate d) {
        double lastV = mp.floorEntry(LocalDateTime.of(d, LocalTime.of(12, 0))).getValue();
        LocalDateTime ytdClose = LocalDateTime.of(d.minusDays(1), LocalTime.of(15, 0));
        double prevV = mp.firstKey().isBefore(ytdClose) ? mp.floorEntry(ytdClose).getValue() : 0.0;
        return lastV - prevV;
    }

    static double computePMNetPnlForGivenDate(NavigableMap<LocalDateTime, Double> mp, LocalDate d) {
        double lastV = mp.floorEntry(LocalDateTime.of(d, LocalTime.of(15, 0))).getValue();
        double noonV = mp.floorEntry(LocalDateTime.of(d, LocalTime.of(11, 35))).getValue();
        return lastV - noonV;
    }


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
                        if (al1.get(0).startsWith("2017/") && LocalDate.parse(al1.get(0), DATE_PATTERN).isAfter(MONDAY_OF_WEEK.minusDays(1))) {
                            //found = true;
                            LocalDate d = LocalDate.parse(al1.get(0), DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                            LocalTime lt = Utility.roundTo5(HistChinaHelper.stringToLocalTime(al1.get(1)));

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
            if (chinaWtd.containsKey(s) && chinaWtd.get(s).size() > 1) {
                NavigableMap<LocalDateTime, Double> ret = SharpeUtility.getReturnSeries(chinaWtd.get(s),
                        LocalDateTime.of(MONDAY_OF_WEEK.minusDays(1), LocalTime.MIN));
                double mean = SharpeUtility.getMean(ret);
                double sdDay = SharpeUtility.getSD(ret) * Math.sqrt(240);
                double sr = SharpeUtility.getSharpe(ret, 240);
                double perc = SharpeUtility.getPercentile(chinaWtd.get(s));
                wtdResult.get(s).fillResult(mean, sdDay, sr, perc);
            } else {
                System.out.println(" name is less than 1 " + tickerFull);
            }
        }
        System.out.println(" wtd processing end ");
    }


    static void loadTradeList() {
        System.out.println(" loading trade list ");
        chinaTradeMap.keySet().forEach(k -> {
            chinaTradeMap.put(k, new ConcurrentSkipListMap<>());
        });

        File f = new File(TradingConstants.GLOBALPATH + "tradeHistoryRecap.txt");
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
                        switch (l.get(2)) {
                            case "Stock":
                                chinaTradeMap.get(ticker).put(ldt, new NormalTrade(p, q));
                                break;
                            case "Margin":
                                chinaTradeMap.get(ticker).put(ldt, new MarginTrade(p, q));
                                break;
                            case "Dividend":
                                chinaTradeMap.get(ticker).put(ldt, new NormalTrade(0.0, q));
                                break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //should be equal to current position
    private static void computeNetSharesTradedByDay() {
        for (String s : chinaTradeMap.keySet()) {
            NavigableMap<LocalDate, Integer> res = chinaTradeMap.get(s).entrySet().stream()
                    .collect(Collectors.groupingBy(e1 -> e1.getKey().toLocalDate(), ConcurrentSkipListMap::new,
                            Collectors.summingInt(e1 -> ((Trade) e1.getValue()).getSizeAll())));
            netSharesTradedByDay.put(s, res);
        }
    }

    private static void computeNetSharesTradedWtd() {

        System.out.println(" compute net shares traded wtd ");

        for (String s : chinaTradeMap.keySet()) {
            NavigableMap<LocalDateTime, Integer> res =
                    chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                            .collect(Collectors.groupingBy(e1 -> Utility.roundTo5Ldt(e1.getKey()), ConcurrentSkipListMap::new,
                                    Collectors.summingInt(e1 -> ((Trade) e1.getValue()).getSizeAll())));


            netSharesTradedWtd.put(s, res);

            if (s.equals("SGXA50")) {
                System.out.println(chinaTradeMap.get(s));
                System.out.println(" SGXA50 compute net shares traded wtd " + res);
                System.out.println(" net shares traded wtd s " + netSharesTradedWtd.get(s));
            }
        }
    }

    private static void computeTradingCost() {
        for (String s : chinaTradeMap.keySet()) {
            if (!s.equals("SGXA50")) {
                double tradingCost = chinaTradeMap.get(s).entrySet().stream()
                        .mapToDouble(e -> HistChinaHelper.getTradingCostCustom(s, e.getKey().toLocalDate(), (Trade) e.getValue())).sum();

                double costBasis = chinaTradeMap.get(s).entrySet().stream()
                        .mapToDouble(e -> HistChinaHelper.getCostWithCommissionsCustom(s, e.getKey().toLocalDate(), (Trade) e.getValue())).sum();

                totalTradingCostMap.put(s, fxMap.getOrDefault(s, 1.0) * tradingCost);
                costBasisMap.put(s, fxMap.getOrDefault(s, 1.0) * costBasis);
            }
        }
    }


    private static double computeCurrentTradePnl(String s, LocalDate cutoff) {
        double costBasis = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(cutoff))
                .mapToDouble(e -> ((Trade) e.getValue()).getCostWithCommission(s)).sum();
        int netPosition = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(cutoff))
                .mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();
        return fxMap.getOrDefault(s, 1.0) * (netPosition * priceMapForHist.getOrDefault(s, 0.0) + costBasis);
    }

    private static void computeWtdCurrentTradePnlAll() {
        for (String s : chinaTradeMap.keySet()) {
            double costBasis = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                    .mapToDouble(e -> ((Trade) e.getValue()).getCostWithCommission(s)).sum();
            int netPosition = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                    .mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();

            wtdTradePnlMap.put(s, fxMap.getOrDefault(s, 1.0) * (netPosition *
                    chinaWtd.get(s).lastEntry().getValue().getClose()+ costBasis));
        }
    }

    static double computeWtdTradePnlFor1Stock(String name) {
        if (chinaWtd.containsKey(name) && chinaWtd.get(name).size() > 0) {
            double price = chinaWtd.get(name).lastEntry().getValue().getClose();
            int pos = chinaTradeMap.get(name).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                    .mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();
            double mv = price * pos;
            double cost = chinaTradeMap.get(name).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                    .mapToDouble(e -> ((Trade) e.getValue()).getCostWithCommission(name)).sum();
            return fxMap.getOrDefault(name, 1.0) * (mv + cost);
        } else {
            return 0.0;
        }
    }


    static void computeWtdMtmPnlAll() {
        for (String s : chinaTradeMap.keySet()) {
            if (!s.equals("SGXA50")) {
                int posBeforeThisWeek = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isBefore(MONDAY_OF_WEEK))
                        .mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();
                weekOpenPositionMap.put(s, posBeforeThisWeek);
                wtdMtmPnlMap.put(s, fxMap.getOrDefault(s, 1.0)
                        * (posBeforeThisWeek * (chinaWtd.get(s).lastEntry().getValue().getClose()
                        - lastWeekCloseMap.getOrDefault(s, chinaWtd.get(s).firstEntry().getValue().getOpen()))));
            } else {
                int openPos = currentPositionMap.getOrDefault("SGXA50", 0) - wtdChgInPosition.getOrDefault("SGXA50", 0);
                wtdMtmPnlMap.put(s, fxMap.getOrDefault(s, 1.0) * (openPos) * (chinaWtd.get(s).lastEntry().getValue().getClose()
                        - lastWeekCloseMap.getOrDefault(s, chinaWtd.get(s).firstEntry().getValue().getOpen())));
            }
        }
    }

//    public static void main(String[] args) {
//        JFrame jf = new JFrame();
//        jf.setSize(new Dimension(1500, 1500));
//        jf.setLayout(new FlowLayout());
//        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        HistChinaStocks hc = new HistChinaStocks();
//        jf.add(hc);
//        jf.setVisible(true);
//    }

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
            return 30;
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
                    return "Wk Open Pos";
                case 14:
                    return "Wk Chg Pos";


                case 15:
                    return "curr pos";
                case 16:
                    return "Trans Cost";
                case 17:
                    return "p";
                case 18:
                    return "Delta";
                case 19:
                    return "cost basis";
                case 20:
                    return "Net Pnl";
                case 21:
                    return "Pnl/cost";
                case 22:
                    return "w Tr pnl";
                case 23:
                    return "last week P";
                case 24:
                    return "w Mtm";
                case 25:
                    return "w net";
                case 26:
                    return "perc";
                default:
                    return "";

            }
        }

        @Override
        public Object getValueAt(int row, int col) {
            String name = stockList.get(row);
            double price = 0.0;
            //double fx = fxMap.getOrDefault(name, 1.0);

//            if (netSharesTradedByDay.containsKey(name) && netSharesTradedByDay.get(name).size() > 0) {
//                currPos = netSharesTradedByDay.get(name).entrySet().stream().mapToInt(Map.Entry::getValue).sum();
//            }
//            if (chinaTradeMap.containsKey(name) && chinaTradeMap.get(name).size() > 0) {
//                currPos = chinaTradeMap.get(name).entrySet().stream().map(e -> ((Trade) e.getValue()).getSizeAll())
//                        .reduce(Integer::sum).get();
//                currentPositionMap.put(name, currPos);
//            }

            if (chinaWtd.containsKey(name) && chinaWtd.get(name).size() > 0) {
                price = chinaWtd.get(name).lastEntry().getValue().getClose();
            }

            if (name.equals("SGXA50")) {
                costBasisMap.put(name, -1.0 * fxMap.getOrDefault(name, 1.0) * lastWeekCloseMap.getOrDefault("SGXA50", 0.0) *
                        currentPositionMap.getOrDefault(name, 0));
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
                    return currentPositionMap.getOrDefault(name, 0) -
                            wtdChgInPosition.getOrDefault(name, 0);
                //return weekOpenPositionMap.getOrDefault(name, 0);
                case 14:
                    return wtdChgInPosition.getOrDefault(name, 0);
                case 15:
                    return currentPositionMap.getOrDefault(name, 0);
                case 16:
                    return totalTradingCostMap.getOrDefault(name, 0.0);
                case 17:
                    //return priceMapForHist.getOrDefault(name, 0.0);
                    return price;
                case 18:
                    return fxMap.getOrDefault(name, 1.0) * price * currentPositionMap.getOrDefault(name, 0);
                case 19:
                    return costBasisMap.getOrDefault(name, 0.0);
                case 20:
                    return fxMap.getOrDefault(name, 1.0)
                            * price * currentPositionMap.getOrDefault(name, 0) + costBasisMap.getOrDefault(name, 0.0);
                case 21:
                    if (totalTradingCostMap.getOrDefault(name, 1.0) != 0.0) {
                        return Math.round((price * currentPositionMap.getOrDefault(name, 0)
                                + costBasisMap.getOrDefault(name, 0.0)) / (totalTradingCostMap.getOrDefault(name, 1.0)));
                    } else {
                        return 0L;
                    }
                case 22:
                    return r(computeWtdTradePnlFor1Stock(name));
                //return wtdTradePnlMap.getOrDefault(name, 0.0);
                case 23:
                    return lastWeekCloseMap.getOrDefault(name, 0.0);
                case 24:
                    return r(fxMap.getOrDefault(name, 1.0) * (currentPositionMap.getOrDefault(name, 0) -
                            wtdChgInPosition.getOrDefault(name, 0)) *
                            (price - lastWeekCloseMap.getOrDefault(name, 0.0)));
                //return wtdMtmPnlMap.getOrDefault(name, 0.0);
                case 25:
                    return r(wtdTradePnlMap.getOrDefault(name, 0.0) + wtdMtmPnlMap.getOrDefault(name, 0.0));
                case 26:
                    return SharpeUtility.getPercentile(chinaWtd.get(name));
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
                case 14:
                    return Integer.class;
                case 15:
                    return Integer.class;
                case 21:
                    return Long.class;
                case 26:
                    return Integer.class;
                default:
                    return Double.class;
            }
        }
    }

}

