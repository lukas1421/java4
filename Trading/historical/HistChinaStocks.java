package historical;

import TradeType.MarginTrade;
import TradeType.NormalTrade;
import TradeType.Trade;
import TradeType.TradeBlock;
import apidemo.ChinaMain;
import apidemo.ChinaStock;
import apidemo.FutType;
import apidemo.TradingConstants;
import auxiliary.SimpleBar;
import client.Contract;
import client.ExecutionFilter;
import graph.GraphBarTemporal;
import graph.GraphChinaPnl;
import handler.SGXPositionHandler;
import handler.SGXTradesHandler;
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
import java.util.concurrent.*;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static apidemo.ChinaData.priceMapBar;
import static apidemo.ChinaData.wtdSharpe;
import static apidemo.ChinaPosition.tradesMap;
import static utility.Utility.*;

@SuppressWarnings("SpellCheckingInspection")
public class HistChinaStocks extends JPanel {

    private static JPanel graphPanel;
    private static BarModel_China model;

    private static final LocalDate LAST_YEAR_END = LocalDate.of(2016, 12, 31);
    public static final LocalDate MONDAY_OF_WEEK = Utility.getMondayOfWeek(LocalDateTime.now());
    private static final LocalDate MONTH_FIRST_DAY = Utility.getFirstDayofMonth(LocalDateTime.now());

    public static final String GLOBALPATH = "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\Trading\\";

    public static Map<String, String> nameMap = new HashMap<>();

    private static volatile GraphBarTemporal<LocalDate> graphYtd = new GraphBarTemporal<>();
    private static volatile GraphBarTemporal<LocalDateTime> graphWtd = new GraphBarTemporal<>();
    private static volatile GraphChinaPnl<LocalDateTime> graphWtdPnl = new GraphChinaPnl<>();

    //private File priceInput = new File(GLOBALPATH + "pricesTodayYtd.csv");
    private File sgxOutput = new File(GLOBALPATH + "sgxWtdOutput.txt");

    private static List<String> stockList = new LinkedList<>();
    private static volatile Map<String, NavigableMap<LocalDate, SimpleBar>> chinaYtd = new HashMap<>();
    public static volatile Map<String, NavigableMap<LocalDateTime, SimpleBar>> chinaWtd = new HashMap<>();

    private static volatile Map<String, NavigableMap<LocalDate, Double>> ytdVolTraded = new HashMap<>();

    private static Map<String, ChinaResult> ytdResult = new HashMap<>();
    private static Map<String, ChinaResult> wtdResult = new HashMap<>();

    public static Map<String, NavigableMap<LocalDateTime, TradeBlock>> chinaTradeMap = new HashMap<>();

    private static volatile Map<String, Integer> weekOpenPositionMap = new HashMap<>();
    public static volatile Map<String, Integer> wtdChgInPosition = new HashMap<>();
    public static volatile Map<String, Integer> currentPositionMap = new HashMap<>();
    private static volatile Map<String, Long> sharesOut = new HashMap<>();
    private static Map<String, NavigableMap<LocalDate, Integer>> netSharesTradedByDay = new HashMap<>();
    private static Map<String, NavigableMap<LocalDateTime, Integer>> netSharesTradedWtd = new HashMap<>();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static Map<String, Double> priceMapForHist = new HashMap<>();

    private static volatile Map<String, Double> lastWeekCloseMap = new HashMap<>();
    private static Map<String, Double> totalTradingCostMap = new HashMap<>();
    private static Map<String, Double> costBasisMap = new HashMap<>();
    //static Map<String, Double> netTradePnlMap = new HashMap<>();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static Map<String, Double> wtdTradePnlMap = new HashMap<>();
    private static Map<String, Double> wtdMtmPnlMap = new HashMap<>();
    private static BinaryOperator<NavigableMap<LocalDateTime, Double>> mapSummingDouble = (a, b) -> Stream.of(a, b).flatMap(e -> e.entrySet().stream())
            .collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new, Collectors.summingDouble(
                    Map.Entry::getValue)));

    //static BinaryOperator<NavigableMap<LocalDateTime, Double>> mapOp2 = (a,b) -> Stream.of(a,b).

    private static volatile NavigableMap<LocalDateTime, Double> weekMtmMap = new ConcurrentSkipListMap<>();
    private static volatile NavigableMap<LocalDateTime, Double> weekTradePnlMap = new ConcurrentSkipListMap<>();
    private static volatile NavigableMap<LocalDateTime, Double> weekNetMap = new ConcurrentSkipListMap<>();

    private static volatile NavigableMap<LocalDate, Double> netPnlByWeekday = new ConcurrentSkipListMap<>();
    private static volatile NavigableMap<LocalDate, Double> netPnlByWeekdayAM = new ConcurrentSkipListMap<>();
    private static volatile NavigableMap<LocalDate, Double> netPnlByWeekdayPM = new ConcurrentSkipListMap<>();

    public static Map<String, Double> mtdSharpe = new HashMap<>();
    private static Map<String, Double> fxMap = new HashMap<>();

    private int avgPercentile;
    private int weightedAvgPercentile;
    private static volatile boolean filterOn = false;
    private static final int CHG_POS_COL = 14;
    private static final int CURR_POS_COL = 15;

    public static double futExpiryLevel = 0.0;
    public static int futExpiryUnits = 0;


    private static String tdxDayPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export\\" : "J:\\TDX\\T0002\\export\\";

    private static String tdxMinutePath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export_1m\\" : "J:\\TDX\\T0002\\export_1m\\";

    private static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private static volatile Predicate<? super Map.Entry<String, ?>> MTM_PRED = m -> true;

    private static ScheduledExecutorService computeExe = Executors.newScheduledThreadPool(10);

    private int modelRow;
    private static volatile String selectedStock = "";
    private TableRowSorter<BarModel_China> sorter;

    public HistChinaStocks() {
        String line;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "sharesOut.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                sharesOut.put(al1.get(0), Long.parseLong(al1.get(1).trim()));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "futExpiry.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                if (al1.get(1).equalsIgnoreCase(TradingConstants.A50_LAST_EXPIRY)) {
                    futExpiryLevel = Double.parseDouble(al1.get(3));
                    futExpiryUnits = Integer.parseInt(al1.get(2));
                    System.out.println(getStr(" fut expiry level and units ", futExpiryLevel, futExpiryUnits));
                }
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "fx.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                fxMap.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        File chinaInput = new File(GLOBALPATH + "ChinaAll.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(chinaInput), "GBK"))) {
            while ((line = reader.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));

                if (!al1.get(0).equals("sh204001") && (al1.get(0).startsWith("sh") || al1.get(0).startsWith("sz")
                        || al1.get(0).startsWith("SGX"))) {
                    String ticker = al1.get(0);
                    chinaYtd.put(al1.get(0), new ConcurrentSkipListMap<>());
                    ytdVolTraded.put(al1.get(0), new ConcurrentSkipListMap<>());
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
                    wtdMtmPnlMap.put(ticker, 0.0);
                    wtdChgInPosition.put(ticker, 0);
                    mtdSharpe.put(ticker, 0.0);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        graphPanel = new JPanel();
        model = new BarModel_China();
        JTable tab = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int indexRow, int indexCol) {
                try {
                    Component comp = super.prepareRenderer(renderer, indexRow, indexCol);
                    if (isCellSelected(indexRow, indexCol)) {
                        modelRow = this.convertRowIndexToModel(indexRow);
                        selectedStock = stockList.get(modelRow);
                        comp.setBackground(Color.GREEN);

//                        if (selectedStock.equals("SGXA50")) {
//                            System.out.println(" printing A 50 prices ");
//                            //chinaTradeMap.entrySet().stream().filter(e -> e.getKey().equals("SGXA50")).forEach(System.out::println);
//                            System.out.println(" price is " + priceMapForHist.getOrDefault(selectedStock, 0.0));
//                            System.out.println(" last wtd price " + chinaWtd.get("SGXA50").lastEntry());
//                        }

                        CompletableFuture.runAsync(() -> {
                            SwingUtilities.invokeLater(() -> {
                                graphYtd.fillInGraphChinaGen(selectedStock, chinaYtd);
                                graphYtd.setTradesMap(netSharesTradedByDay.get(selectedStock));
                                graphYtd.setTradePnl(computeCurrentTradePnl(selectedStock, LAST_YEAR_END));
                                graphYtd.setWtdVolTraded(computeWtdVolTraded(selectedStock));
                                graphYtd.setWtdVolPerc(computeWVolPerc(selectedStock));
                            });

                            //CompletableFuture.runAsync(() -> {
                            SwingUtilities.invokeLater(() -> {
                                graphWtd.fillInGraphChinaGen(selectedStock, chinaWtd);
                                graphWtd.setTradesMap(netSharesTradedWtd.get(selectedStock));
                                graphWtd.setTradePnl(computeCurrentTradePnl(selectedStock, MONDAY_OF_WEEK.minusDays(1)));
                                graphWtd.setWtdMtmPnl(wtdMtmPnlMap.getOrDefault(selectedStock, 0.0));
                            });

                            CompletableFuture.runAsync(() -> {
                                //if (chinaTradeMap.containsKey(selectedStock) && chinaTradeMap.get(selectedStock).size() > 0) {

                                weekMtmMap = computeWtdMtmPnl(e -> e.getKey().equals(selectedStock));
                                weekTradePnlMap = computeWtdTradePnl(e -> e.getKey().equals(selectedStock));
                                weekNetMap = computeNet(e -> e.getKey().equals(selectedStock));


                                SwingUtilities.invokeLater(() -> {
                                    graphWtdPnl.setMtm(weekMtmMap);
                                    graphWtdPnl.setTrade(weekTradePnlMap);
                                    graphWtdPnl.setNet(weekNetMap);
                                    graphWtdPnl.setWeekdayMtm(netPnlByWeekday, netPnlByWeekdayAM, netPnlByWeekdayPM);
                                    graphWtdPnl.fillInGraph(selectedStock);
                                });
                            });

                            CompletableFuture.runAsync(() -> {
                                avgPercentile = computeAvgPercentile(e -> e.getKey().equals(selectedStock));
                                weightedAvgPercentile = computeDeltaWeightedPercentile(e -> e.getKey().equals(selectedStock));

                                SwingUtilities.invokeLater(() -> {
                                    graphWtdPnl.setAvgPerc(avgPercentile);
                                    graphWtdPnl.setDeltaWeightedAveragePerc(weightedAvgPercentile);
                                });
                            });
                        }).thenRun(() -> SwingUtilities.invokeLater(() -> graphPanel.repaint()));
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
                    sorter.setRowFilter(null);
                    filterOn = false;
                    SwingUtilities.invokeLater(() -> model.fireTableDataChanged());
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
        //JButton liveUpdateButton = new JButton("Compute");
        //JButton stopButton = new JButton("stop");
        JButton sgxDataButton = new JButton("SGX Data");
        JButton sgxTradesButton = new JButton(" SGX Trades");
        JToggleButton noFutButton = new JToggleButton(" no fut");
        JToggleButton futOnlyButton = new JToggleButton("fut only");
        JButton outputWtdButton = new JButton(" output wtd ");
        JButton activeOnlyButton = new JButton("Active Only");
        JToggleButton autoComputeButton = new JToggleButton("Auto On");
        JButton fillExpiredButton = new JButton("Fill Expired");

        fillExpiredButton.addActionListener(l -> {
            if (chinaWtd.get("SGXA50PR").size() > 0) {
                SimpleBar sb = new SimpleBar(chinaWtd.get("SGXA50PR").lastEntry().getValue().getClose());
                chinaWtd.get("SGXA50").forEach((key, value) -> {
                    if (!chinaWtd.get("SGXA50PR").containsKey(key) && key.isAfter(chinaWtd.get("SGXA50PR").lastKey())) {
                        chinaWtd.get("SGXA50PR").put(key, sb);
                    }
                });
            }
        });

        autoComputeButton.addActionListener(l -> {
            if (autoComputeButton.isSelected()) {
                computeExe = Executors.newScheduledThreadPool(10);
                computeExe.scheduleAtFixedRate(() -> {
                    getTodayDataButton.doClick();
                    getTodayTradesButton.doClick();
                    refreshAll();
                }, 0L, 15L, TimeUnit.SECONDS);

            } else {
                computeExe.shutdown();
            }
        });

        activeOnlyButton.addActionListener(l -> {
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

        sgxDataButton.addActionListener(l ->
                CompletableFuture.runAsync(() -> {
                            ChinaMain.controller().getSGXA50HistoricalCustom(20000,
                                    getExpiredFutContract(), HistChinaStocks::handleSGXA50WtdData, 7);
                            ChinaMain.controller().getSGXA50HistoricalCustom(20001,
                                    getFrontFutContract(), HistChinaStocks::handleSGXA50WtdData, 7);
                            ChinaMain.controller().getSGXA50HistoricalCustom(20002,
                                    getBackFutContract(), HistChinaStocks::handleSGXA50WtdData, 7);
                        }
                ));

        noFutButton.addActionListener(l -> {
            if (noFutButton.isSelected()) {
                MTM_PRED = m -> tickerNotFuture(m.getKey());
            } else {
                MTM_PRED = m -> true;
            }
        });

        futOnlyButton.addActionListener(l -> {
            if (futOnlyButton.isSelected()) {
                MTM_PRED = m -> !tickerNotFuture(m.getKey());
            } else {
                MTM_PRED = m -> true;
            }
        });

//        ButtonGroup futNoFutBg = new ButtonGroup();
//        futNoFutBg.add(noFutButton);
//        futNoFutBg.add(futOnlyButton);


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
            SwingUtilities.invokeLater(() -> {
                model.fireTableDataChanged();
                this.repaint();
            });
            refreshAll();
        });

        ytdButton.addActionListener(al -> CompletableFuture.runAsync(HistChinaStocks::computeYtd).thenRun(() -> {
            System.out.println(" ytd ended ");
            SwingUtilities.invokeLater(() -> {
                model.fireTableDataChanged();
                this.repaint();
            });
        }));

        wtdButton.addActionListener(al -> {
            CompletableFuture.runAsync(HistChinaStocks::computeWtd).thenRun(() -> {
                System.out.println(" wtd ended ");
                SwingUtilities.invokeLater(() -> {
                    model.fireTableDataChanged();
                    this.repaint();
                });
            });
            CompletableFuture.runAsync(() -> {
                ChinaMain.controller().getSGXA50HistoricalCustom(20000, getExpiredFutContract(), HistChinaStocks::handleSGXA50WtdData, 7);
                ChinaMain.controller().getSGXA50HistoricalCustom(20001, getFrontFutContract(), HistChinaStocks::handleSGXA50WtdData, 7);
                ChinaMain.controller().getSGXA50HistoricalCustom(20002, getBackFutContract(), HistChinaStocks::handleSGXA50WtdData, 7);

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
        });

        computeButton.addActionListener(l -> CompletableFuture.runAsync(() -> {
            CompletableFuture.runAsync(HistChinaStocks::computeNetSharesTradedByDay);

            CompletableFuture.runAsync(HistChinaStocks::computeNetSharesTradedWtd);

            CompletableFuture.runAsync(HistChinaStocks::computeTradingCost);

            CompletableFuture.runAsync(HistChinaStocks::computeWtdCurrentTradePnlAll);

            CompletableFuture.runAsync(HistChinaStocks::computeWtdMtmPnlAll);

            CompletableFuture.runAsync(() -> computeWtdMtmPnl(e -> true));
        }).thenRun(() -> SwingUtilities.invokeLater(() -> {
            model.fireTableDataChanged();
            graphPanel.repaint();
            this.repaint();
        })));

        updatePriceButton.addActionListener(al -> {
            updatePrices();
            SwingUtilities.invokeLater(() -> model.fireTableDataChanged());
        });

        getTodayDataButton.addActionListener(al -> {
            for (String s : chinaWtd.keySet()) {
                if (priceMapBar.containsKey(s) && priceMapBar.get(s).size() > 0) {
                    NavigableMap<LocalDateTime, SimpleBar> wtdNew = mergeMaps(chinaWtd.get(s),
                            Utility.priceMapToLDT(priceMap1mTo5M(priceMapBar.get(s)), ChinaMain.currentTradingDate));
                    chinaWtd.put(s, wtdNew);
                    NavigableMap<LocalDate, SimpleBar> ytdNew = mergeMapGen(chinaYtd.get(s), reduceMapToBar(priceMapBar.get(s), ChinaMain.currentTradingDate));
                    chinaYtd.put(s, ytdNew);
                }
            }
            SwingUtilities.invokeLater(() -> model.fireTableDataChanged());
        });

        getTodayTradesButton.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                for (String s : chinaTradeMap.keySet()) {
                    if (!s.equals("SGXA50")) {
                        if (tradesMap.containsKey(s) && tradesMap.get(s).size() > 0) {
                            NavigableMap<LocalDateTime, TradeBlock> res = mergeTradeMap(chinaTradeMap.get(s),
                                    Utility.priceMapToLDT(tradesMap.get(s), ChinaMain.currentTradingDate));
                            chinaTradeMap.put(s, res);
                        }
                    }
                }
            }).thenRun(HistChinaStocks::computePosition);
            SwingUtilities.invokeLater(() -> model.fireTableDataChanged());
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
        controlPanel.add(autoComputeButton);
        controlPanel.add(fillExpiredButton);

        this.setLayout(new BorderLayout());
        this.add(controlPanel, BorderLayout.NORTH);
        this.add(scroll, BorderLayout.CENTER);
        this.add(graphPanel, BorderLayout.SOUTH);

        tab.setAutoCreateRowSorter(true);

        //noinspection unchecked
        sorter = (TableRowSorter<BarModel_China>) tab.getRowSorter();
    }

    private static void updatePrices() {
        chinaWtd.forEach((k, v) -> {
            if (v.size() > 0) {
                priceMapForHist.put(k, v.lastEntry().getValue().getClose());
            }
        });
    }

    private static int monthOpenPos(String name) {
        if (chinaTradeMap.containsKey(name) && chinaTradeMap.get(name).size() > 0) {
            return chinaTradeMap.get(name).entrySet().stream().filter(e -> e.getKey().toLocalDate().isBefore(MONTH_FIRST_DAY))
                    .mapToInt(e -> e.getValue().getSizeAll()).sum();
        }
        return 0;
    }

    private static int mtdChgPos(String name) {

        if (chinaTradeMap.containsKey(name) && chinaTradeMap.get(name).size() > 0) {
            return chinaTradeMap.get(name).entrySet().stream().filter(e -> e.getKey().toLocalDate()
                    .isAfter(MONTH_FIRST_DAY.minusDays(1L)))
                    .mapToInt(e -> e.getValue().getSizeAll()).sum();
        }
        return 0;

    }

    private static double mtdMtm(String name) {
        try {
            if (chinaTradeMap.containsKey(name) && chinaTradeMap.get(name).size() > 0) {

                int mtdOpenPos = chinaTradeMap.get(name).entrySet().stream().filter(e -> e.getKey().toLocalDate().isBefore(MONTH_FIRST_DAY))
                        .mapToInt(e -> e.getValue().getSizeAll()).sum();

                //System.out.println(getStr(" name mtdopenpos monthFDay ", name, mtdOpenPos, MONTH_FIRST_DAY));
                if (chinaYtd.containsKey(name) && chinaYtd.get(name).size() > 0 &&
                        chinaYtd.get(name).lastKey().isAfter(MONTH_FIRST_DAY.minusDays(1L))) {
                    double price = chinaYtd.get(name).lastEntry().getValue().getClose();
                    double lastMonthClose = Optional.ofNullable(chinaYtd.get(name).floorEntry(MONTH_FIRST_DAY.minusDays(1L)))
                            .map(Map.Entry::getValue).map(SimpleBar::getClose)
                            .orElse(Optional.of(chinaYtd.get(name).ceilingEntry(MONTH_FIRST_DAY)).map(Map.Entry::getValue).map(SimpleBar::getOpen)
                                    .orElse(0.0));
                    return mtdOpenPos * (price - lastMonthClose);
                }
            }
        } catch (Exception ex) {
            System.out.println(" histchinastocks mtdmtm wrong " + name);
            ex.printStackTrace();

        }
        return 0.0;
    }

    private static double mtdTradePnl(String name) {
        if (chinaYtd.containsKey(name) && chinaYtd.get(name).size() > 0) {
            double price = chinaYtd.get(name).lastEntry().getValue().getClose();
            int traded = chinaTradeMap.get(name).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONTH_FIRST_DAY.minusDays(1L)))
                    .mapToInt(e -> e.getValue().getSizeAll()).sum();
            double cost = chinaTradeMap.get(name).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONTH_FIRST_DAY.minusDays(1L)))
                    .mapToDouble(e -> e.getValue().getCostBasisAll(name)).sum();
            return price * traded + cost;
        }
        return 0.0;
    }


    private static void handleSGXA50WtdData(Contract c, String date,
                                            double open, double high, double low, double close, @SuppressWarnings("unused") int volume) {

        String ticker = ibContractToSymbol(c);

        System.out.println(" handle sgx a50 wtd data " + ticker);

        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

            //System.out.println(" vol " + volume);


            if (ld.isAfter(HistChinaStocks.MONDAY_OF_WEEK.minusDays(1L))) {
                if ((lt.isAfter(LocalTime.of(8, 59)) && lt.isBefore(LocalTime.of(11, 31)))
                        || (lt.isAfter(LocalTime.of(12, 59)) && lt.isBefore(LocalTime.of(15, 1)))) {

                    LocalDateTime ldt = LocalDateTime.of(ld, lt);
                    LocalDateTime ltTo5 = Utility.roundTo5Ldt(ldt);
                    if (!chinaWtd.get(ticker).containsKey(ltTo5)) {
                        chinaWtd.get(ticker).put(ltTo5, new SimpleBar(open, high, low, close));
                    } else {
                        chinaWtd.get(ticker).get(ltTo5).updateBar(open, high, low, close);
                    }
                } else {
                    priceMapForHist.put(ticker, close);
                    System.out.println(getStr(" most recent price ", ticker, LocalDateTime.of(ld, lt), close));
                }
            } else {
                //System.out.println(" updating close of SGXA50 ");
                HistChinaStocks.lastWeekCloseMap.put(ticker, close);
                //System.out.println(" sgxa50 close " + lastWeekCloseMap.getOrDefault("SGXA50", 0.0));
            }
        } else {
            //priceMapForHist.put(ticker, chinaWtd.get(ticker).lastEntry().getValue().getClose());
            System.out.println(" last line for  " + ticker + " " + getStr(date, open, high, low, close));

            if (ticker.equalsIgnoreCase("SGXA50PR")) {
                chinaWtd.get(ticker).lastEntry().getValue().updateClose(futExpiryLevel);

            }

            NavigableMap<LocalDateTime, Double> ret =
                    SharpeUtility.getReturnSeries(chinaWtd.get(ticker), LocalDateTime.of(MONDAY_OF_WEEK.minusDays(1), LocalTime.MAX));
            double sgxWtdSharpe = SharpeUtility.getSharpe(ret, 48);
            wtdSharpe.put(ticker, sgxWtdSharpe);

            //costBasisMap.put("SGXA50", HistChinaStocks.lastWeekCloseMap.get(close));
            //System.out.println(getStr(date, open, high, low, close));
        }
    }

    private static void getSGXPosition() {
        System.out.println(" getting sgx position ");
        //HistChinaStocks.currentPositionMap.put("SGXA50", 0);
        ChinaMain.controller().reqPositions(new SGXPositionHandler());
    }

    private static void getSGXTrades() {
        System.out.println(" getting sgx trades ");
        chinaTradeMap.put("SGXA50PR", new ConcurrentSkipListMap<>());
        chinaTradeMap.put("SGXA50", new ConcurrentSkipListMap<>());
        chinaTradeMap.put("SGXA50BM", new ConcurrentSkipListMap<>());
        ChinaMain.controller().reqExecutions(new ExecutionFilter(), new SGXTradesHandler());
    }


    private static int computeAvgPercentile(Predicate<? super Map.Entry<String, ?>> p) {
        return (int) Math.round(chinaWtd.entrySet().stream().filter(e -> getCurrentPos(e.getKey()) != 0)
                .filter(p).mapToDouble(e -> SharpeUtility.getPercentile(e.getValue())).average().orElse(0.0));
    }

    /**
     * @param p predicate to filter which stocks to do
     * @return return weighted avg delta
     */
    private static int computeDeltaWeightedPercentile(Predicate<? super Map.Entry<String, ?>> p) {
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

    private static double getCurrentDelta(String name) {
        if (chinaWtd.containsKey(name) && chinaWtd.get(name).size() > 0) {
            return fxMap.getOrDefault(name, 1.0) * getCurrentPos(name) * chinaWtd.get(name).lastEntry().getValue().getClose();
        }
        return 0.0;
    }


    private static int getCurrentPos(String name) {
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
        }).thenRun(() -> SwingUtilities.invokeLater(() -> {
            graphWtdPnl.setMtm(computeWtdMtmPnl(MTM_PRED));
            graphWtdPnl.setWeekdayMtm(netPnlByWeekday, netPnlByWeekdayAM, netPnlByWeekdayPM);
            model.fireTableDataChanged();
            graphWtdPnl.repaint();
        }));
    }

    private static void computePosition() {
        for (String s : chinaTradeMap.keySet()) {
            if (tickerNotFuture(s)) {
                int openPos = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isBefore(MONDAY_OF_WEEK))
                        .mapToInt(e -> e.getValue().getSizeAll()).sum();
                int thisWeekPos = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1L)))
                        .mapToInt(e -> e.getValue().getSizeAll()).sum();
                int currentPos = chinaTradeMap.get(s).entrySet().stream().mapToInt(e -> e.getValue().getSizeAll()).sum();
                weekOpenPositionMap.put(s, openPos);
                wtdChgInPosition.put(s, thisWeekPos);
                currentPositionMap.put(s, currentPos);
            }
        }
    }

    private static NavigableMap<LocalDateTime, Double> computeWtdMtmPnl(Predicate<? super Map.Entry<String, ?>> p) {
        for (FutType f : FutType.values()) {
            String ticker = f.getTicker();
            weekOpenPositionMap.put(ticker, currentPositionMap.getOrDefault(ticker, 0)
                    - wtdChgInPosition.getOrDefault(ticker, 0));
        }

        //if (weekOpenPositionMap.entrySet().stream().filter(p).mapToInt(Map.Entry::getValue).sum() != 0) {
        weekMtmMap = weekOpenPositionMap.entrySet().stream().filter(p).map(e ->
                computeMtm(e.getKey(), e.getValue(), chinaWtd.get(e.getKey()), lastWeekCloseMap.getOrDefault(e.getKey(), 0.0))).
                reduce(mapSummingDouble).orElse(new ConcurrentSkipListMap<>());
        return weekMtmMap;
    }

    private static NavigableMap<LocalDateTime, Double> computeMtm(String ticker, int openPos,
                                                                  NavigableMap<LocalDateTime, SimpleBar> prices, double lastWeekClose) {

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

    private static double computeTrendPnlSum(String name) {
        double fx = fxMap.getOrDefault(name, 1.0);
        //System.out.println(" computing trend pnl for " + name);
        if (chinaWtd.get(name).size() > 0) {
            //, int openPos, NavigableMap<LocalDateTime, SimpleBar> prices, double lastWeekClose
            int openPos = weekOpenPositionMap.getOrDefault(name, 0);
            NavigableMap<LocalDateTime, SimpleBar> prices = chinaWtd.get(name);
            double lastWeekClose = lastWeekCloseMap.getOrDefault(name, 0.0);
            if (prices.size() > 0) {
                return prices.entrySet().stream().map(Map.Entry::getKey).map(LocalDateTime::toLocalDate).distinct()
                        .mapToDouble(s -> fx * openPos *
                                (Optional.ofNullable(prices.floorEntry(LocalDateTime.of(s, LocalTime.of(12, 0))))
                                        .map(Map.Entry::getValue).map(SimpleBar::getClose).orElse(0.0) -
                                        Optional.ofNullable(prices.floorEntry(LocalDateTime.of(s.minusDays(1), LocalTime.of(15, 0))))
                                                .map(Map.Entry::getValue).map(SimpleBar::getClose).orElse(lastWeekClose))).sum();
            }
        }
        return 0.0;
    }

    private static double computeOwedPnl(String name) {
        double fx = fxMap.getOrDefault(name, 1.0);
        //System.out.println(" computing owed pnl for " + name);
        if (chinaWtd.get(name).size() > 0) {
            int openPos = weekOpenPositionMap.getOrDefault(name, 0);
            NavigableMap<LocalDateTime, SimpleBar> prices = chinaWtd.get(name);
            if (prices.size() > 0) {
                return prices.entrySet().stream().map(Map.Entry::getKey).map(LocalDateTime::toLocalDate).distinct()
                        .mapToDouble(s -> fx * openPos * (prices.floorEntry(LocalDateTime.of(s, LocalTime.of(15, 0))).getValue().getClose() -
                                Optional.ofNullable(prices.floorEntry(LocalDateTime.of(s, LocalTime.of(11, 35))))
                                        .map(Map.Entry::getValue).map(SimpleBar::getClose).orElse(0.0))).sum();
            }
        }
        return 0.0;
    }


    private static NavigableMap<LocalDateTime, Double> computeWtdTradePnl(Predicate<? super Map.Entry<String, ?>> p) {
        weekTradePnlMap = chinaTradeMap.entrySet().stream().filter(p).map(e ->
                computeTrade(e.getKey(), chinaWtd.get(e.getKey()), (e.getValue())))
                .reduce(mapSummingDouble).orElse(new ConcurrentSkipListMap<>());
        return weekTradePnlMap;
    }


    private static NavigableMap<LocalDateTime, Double> computeTrade(String ticker, NavigableMap<LocalDateTime, SimpleBar> prices,
                                                                    NavigableMap<LocalDateTime, TradeBlock> trades) {
        NavigableMap<LocalDateTime, Double> res = new ConcurrentSkipListMap<>();
        int currPos = 0;
        double costBasis = 0.0;
        double mv;
        double fx = fxMap.getOrDefault(ticker, 1.0);


        for (LocalDateTime lt : prices.keySet()) {
            //note that prices start from 9:35 to 15:00, it needs to back-include trade in slot
            if (trades.subMap(lt.minusMinutes(5L), false, lt, true).size() > 0) {
                currPos += trades.subMap(lt.minusMinutes(5L), false, lt, true)
                        .entrySet().stream().mapToInt(e -> e.getValue().getSizeAll()).sum();
                costBasis += trades.subMap(lt.minusMinutes(5L), false, lt, true)
                        .entrySet().stream().mapToDouble(e -> e.getValue().getCostBasisAll(ticker)).sum();
            }
            mv = currPos * prices.get(lt).getClose();

            if (chinaTradingTime.test(lt.toLocalTime())) {
                res.put(lt, fx * (costBasis + mv));
            }
        }
        return res;
    }

    private static NavigableMap<LocalDateTime, Double> computeNet(Predicate<? super Map.Entry<String, ?>> p) {
        NavigableMap<LocalDateTime, Double> res = mapSummingDouble.apply(computeWtdMtmPnl(p), computeWtdTradePnl(p));
        computeNetPnlByWeekday(res);
        return res;
    }

    private static void computeNetPnlByWeekday(NavigableMap<LocalDateTime, Double> mp) {

        netPnlByWeekday = mp.keySet().stream().map(LocalDateTime::toLocalDate).distinct().collect(Collectors.toMap(d -> d,
                d -> computeNetPnlForGivenDate(mp, d), (a, b) -> a, ConcurrentSkipListMap::new));

        netPnlByWeekdayAM = mp.keySet().stream().map(LocalDateTime::toLocalDate).distinct().collect(Collectors.toMap(d -> d,
                d -> computeAMNetPnlForGivenDate(mp, d), (a, b) -> a, ConcurrentSkipListMap::new));

        netPnlByWeekdayPM = mp.keySet().stream().map(LocalDateTime::toLocalDate).distinct().collect(Collectors.toMap(d -> d,
                d -> computePMNetPnlForGivenDate(mp, d), (a, b) -> a, ConcurrentSkipListMap::new));

//        System.out.println(" net by week day " + netPnlByWeekday + " " + netPnlByWeekday.values().stream().mapToDouble(e-> e).sum());
//        System.out.println(" am " + netPnlByWeekdayAM + " " + netPnlByWeekdayAM.values().stream().mapToDouble(e-> e).sum());
//        System.out.println(" pm " + netPnlByWeekdayPM + " " +  netPnlByWeekdayPM.values().stream().mapToDouble(e-> e).sum());
//        System.out.println(" am pm " + Stream.of(netPnlByWeekdayAM, netPnlByWeekdayPM).flatMap(e->e.entrySet().stream())
//                .collect(Collectors.groupingBy(Map.Entry::getKey, ConcurrentSkipListMap::new,Collectors.summingDouble(Map.Entry::getValue))));
//        System.out.println(" week day all " + (Double) netPnlByWeekday.entrySet().stream().mapToDouble(Map.Entry::getValue).sum());

    }

    private static double computeNetPnlForGivenDate(NavigableMap<LocalDateTime, Double> mp, LocalDate d) {
        if (mp.lastEntry().getKey().toLocalDate().isBefore(d)) {
            return 0.0;
        }
        double lastV = mp.floorEntry(LocalDateTime.of(d, LocalTime.of(15, 0))).getValue();
        LocalDateTime ytdClose = LocalDateTime.of(d.minusDays(1), LocalTime.of(15, 0));
        double prevV = mp.firstKey().isBefore(ytdClose) ? mp.floorEntry(ytdClose).getValue() : 0.0;
        return lastV - prevV;

    }

    private static double computeAMNetPnlForGivenDate(NavigableMap<LocalDateTime, Double> mp, LocalDate d) {
        if (mp.lastEntry().getKey().toLocalDate().isBefore(d)) {
            return 0.0;
        }

        double lastV = mp.floorEntry(LocalDateTime.of(d, LocalTime.of(12, 0))).getValue();
        LocalDateTime ytdClose = LocalDateTime.of(d.minusDays(1), LocalTime.of(15, 0));
        double prevV = mp.firstKey().isBefore(ytdClose) ? mp.floorEntry(ytdClose).getValue() : 0.0;
        //System.out.println(getStr(" compute am net pnl ", lastV, ytdClose, prevV, lastV - prevV));
        //System.out.println("")
        return lastV - prevV;
    }

    private static double computePMNetPnlForGivenDate(NavigableMap<LocalDateTime, Double> mp, LocalDate d) {
        if (mp.lastEntry().getKey().toLocalDate().isBefore(d)) {
            return 0.0;
        }
        double lastV = mp.floorEntry(LocalDateTime.of(d, LocalTime.of(15, 0))).getValue();
        double noonV = mp.floorEntry(LocalDateTime.of(d, LocalTime.of(11, 35))).getValue();
        return lastV - noonV;
    }


    private static void computeYtd() {
        CompletableFuture.runAsync(() -> {
            for (String s : stockList) {
                String tickerFull = s.substring(0, 2).toUpperCase() + "#" + s.substring(2) + ".txt";
                //double totalSize = 0.0;
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
                                        ytdVolTraded.get(s).put(d, Double.parseDouble(al1.get(6)));
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

    private static void computeWtd() {
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
                        LocalDateTime.of(MONDAY_OF_WEEK.minusDays(1), LocalTime.MAX));
                double mean = SharpeUtility.getMean(ret) * 48;
                double sdDay = SharpeUtility.getSD(ret) * Math.sqrt(48);
                double sr = SharpeUtility.getSharpe(ret, 48);
                double perc = SharpeUtility.getPercentile(chinaWtd.get(s));
                wtdResult.get(s).fillResult(mean, sdDay, sr, perc);
                //System.out.println(" stock size first last mean sd sr " + getStr(s, ret.size(), ret.firstEntry(), ret.lastEntry(), mean, sdDay,sr));
            } else {
                System.out.println(" name is less than 1 " + tickerFull);
            }
        }
        System.out.println(" wtd processing end ");
    }

    private static double getMtdSharpe(String name) {
        if (chinaYtd.containsKey(name) && chinaYtd.get(name).size() > 0) {
            NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(chinaYtd.get(name), MONTH_FIRST_DAY);
            double sr = SharpeUtility.getSharpe(ret, 252);
            mtdSharpe.put(name, sr);
            return sr;
        }

        return 0.0;
    }

    private static double sharpeWeekChg(String name) {

        //LocalDate ld = getMondayOfWeek()
        //MONDAY_OF_WEEK
        if (chinaYtd.containsKey(name) && chinaYtd.get(name).size() > 1) {

            NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(chinaYtd.get(name),
                    LocalDate.of(2016, Month.DECEMBER, 31));

            NavigableMap<LocalDate, Double> ret2 = SharpeUtility.getReturnSeries(chinaYtd.get(name),
                    MONDAY_OF_WEEK.minusDays(1));

            double sum1 = ret.values().stream().mapToDouble(e -> e).sum();
            double sum2 = ret2.values().stream().mapToDouble(e -> e).sum();
            double sumSq1 = ret.values().stream().mapToDouble(e -> Math.pow(e, 2)).sum();
            double sumSq2 = ret2.values().stream().mapToDouble(e -> Math.pow(e, 2)).sum();
            double sumDiff = sum1 - sum2;
            double sumSqDiff = sumSq1 - sumSq2;
            int size1 = ret.size();
            int sizeDiff = ret.size() - ret2.size();
            double sharpNow = (sum1 / size1) / (Math.sqrt((sumSq1 / size1 - Math.pow(sum1 / size1, 2)) * size1 / (size1 - 1))) * Math.sqrt(252);
            double sharpPrev = (sumDiff / sizeDiff) / (Math.sqrt((sumSqDiff / sizeDiff - Math.pow(sumDiff / sizeDiff, 2)) * sizeDiff / (sizeDiff - 1))) * Math.sqrt(252);
            //System.out.println(getStr("name sharpe now previous ",name, sharpNow, sharpPrev));
            //System.out.println(getStr(" size1 sizediff size2 sum1 sum2 sumsq1 sumsq2 ", size1, sizediff, ret2.size(), sum1,sum2, sumsq1, sumsq2));
            return sharpNow - sharpPrev;
        }
        return 0.0;
    }


    private static void loadTradeList() {
        System.out.println(" loading trade list ");
        chinaTradeMap.keySet().forEach(k -> chinaTradeMap.put(k, new ConcurrentSkipListMap<>()));

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
                        chinaTradeMap.get(ticker).get(ldt).addTrade(l.get(2).equals("Stock") ? (new NormalTrade(p, q)) :
                                (l.get(2).equals("Margin") ? new MarginTrade(p, q) : new NormalTrade(0, 0)));
                    } else {
                        switch (l.get(2)) {
                            case "Stock":
                                chinaTradeMap.get(ticker).put(ldt, new TradeBlock(new NormalTrade(p, q)));
                                break;
                            case "Margin":
                                chinaTradeMap.get(ticker).put(ldt, new TradeBlock(new MarginTrade(p, q)));
                                break;
                            case "Dividend":
                                chinaTradeMap.get(ticker).put(ldt, new TradeBlock(new NormalTrade(0.0, q)));
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
                            Collectors.summingInt(e1 -> e1.getValue().getSizeAll())));
            netSharesTradedByDay.put(s, res);
        }
    }

    private static void computeNetSharesTradedWtd() {

        //System.out.println(" compute net shares traded wtd ");

        for (String s : chinaTradeMap.keySet()) {
            NavigableMap<LocalDateTime, Integer> res =
                    chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                            .collect(Collectors.groupingBy(e1 -> Utility.roundTo5Ldt(e1.getKey()), ConcurrentSkipListMap::new,
                                    Collectors.summingInt(e1 -> e1.getValue().getSizeAll())));

            netSharesTradedWtd.put(s, res);

            if (s.equals("SGXA50")) {
                //System.out.println(chinaTradeMap.get(s));
                System.out.println(" SGXA50 compute net shares traded wtd " + res);
                System.out.println(" net shares traded wtd s " + netSharesTradedWtd.get(s));
            }
        }
    }

    private static void computeTradingCost() {
        for (String s : chinaTradeMap.keySet()) {
            if (tickerNotFuture(s)) {
                double tradingCost = chinaTradeMap.get(s).entrySet().stream()
                        .mapToDouble(e -> e.getValue().getTradeList().stream().mapToDouble(
                                t -> HistChinaHelper.getTradingCostCustom(s, e.getKey().toLocalDate(),
                                        (Trade) t)).sum()).sum();

                double costBasis = chinaTradeMap.get(s).entrySet().stream()
                        .mapToDouble(e -> e.getValue().getTradeList().stream().mapToDouble(
                                t -> HistChinaHelper.getCostWithCommissionsCustom(s, e.getKey().toLocalDate(),
                                        (Trade) t)).sum()).sum();

                totalTradingCostMap.put(s, fxMap.getOrDefault(s, 1.0) * tradingCost);
                costBasisMap.put(s, fxMap.getOrDefault(s, 1.0) * costBasis);
            }
        }
    }


    private static double computeCurrentTradePnl(String s, LocalDate cutoff) {
        double costBasis = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(cutoff))
                .mapToDouble(e -> e.getValue().getCostBasisAll(s)).sum();
        int netPosition = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(cutoff))
                .mapToInt(e -> e.getValue().getSizeAll()).sum();
        if (chinaWtd.containsKey(s) && chinaWtd.get(s).size() > 0) {
            double price = chinaWtd.get(s).lastEntry().getValue().getClose();
            return fxMap.getOrDefault(s, 1.0) * (netPosition * price + costBasis);
        }
        return 0.0;
    }

    private static double computeWtdVolTraded(String s) {
        return (ytdVolTraded.containsKey(s) && ytdVolTraded.get(s).size() > 0) ?
                ytdVolTraded.get(s).entrySet().stream().filter(e -> e.getKey().isAfter(MONDAY_OF_WEEK.minusDays(1L)))
                        .mapToDouble(Map.Entry::getValue).sum() : 0.0;
    }

    private static double computeWVolPerc(String s) {
        if (ytdVolTraded.containsKey(s) && ytdVolTraded.get(s).size() > 0) {
            Map<LocalDate, Double> res = ytdVolTraded.get(s).entrySet().stream().collect(Collectors.groupingBy(e -> getMondayOfWeek(e.getKey()),
                    ConcurrentSkipListMap::new, Collectors.averagingDouble(Map.Entry::getValue)));
            double v = ytdVolTraded.get(s).entrySet().stream().filter(e -> e.getKey().isAfter(MONDAY_OF_WEEK.minusDays(1L)))
                    .mapToDouble(Map.Entry::getValue).average().orElse(0.0);
            //System.out.println(" week avg vol is " + res + " v " + v);
            return SharpeUtility.getPercentileGen(res, v);
        }
        return 0.0;
    }

    private static void computeWtdCurrentTradePnlAll() {
        for (String s : chinaTradeMap.keySet()) {
            double costBasis = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                    .mapToDouble(e -> e.getValue().getCostBasisAll(s)).sum();
            int netPosition = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                    .mapToInt(e -> e.getValue().getSizeAll()).sum();
            wtdTradePnlMap.put(s, fxMap.getOrDefault(s, 1.0) * (netPosition *
                    chinaWtd.get(s).lastEntry().getValue().getClose() + costBasis));
        }
    }

    private static double computeWtdTradePnlFor1Stock(String name) {
        if (chinaWtd.containsKey(name) && chinaWtd.get(name).size() > 0) {
            double price = name.equalsIgnoreCase("SGXA50PR") ? futExpiryLevel : chinaWtd.get(name).lastEntry().getValue().getClose();
            int pos = chinaTradeMap.get(name).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                    .mapToInt(e -> e.getValue().getSizeAll()).sum();
            double mv = price * pos;
            double cost = chinaTradeMap.get(name).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                    .mapToDouble(e -> e.getValue().getCostBasisAll(name)).sum();


//            if (name.equals("SGXA50")) {
//                System.out.println(getStr(" computeWtdTradePnlFor1Stock ticker price pos mv cost ",
//                        name, price, pos, mv, cost));
//                System.out.println(getStr(" trading map is " + chinaTradeMap.get(name)));
//            }

            return fxMap.getOrDefault(name, 1.0) * (mv + cost);
        } else {
            return 0.0;
        }
    }

    private static double computeWtdMtmFor1Stock(String s) {
        if (chinaWtd.containsKey(s) && chinaWtd.get(s).size() > 0) {
            if (tickerNotFuture(s)) {
                int openPos = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isBefore(MONDAY_OF_WEEK))
                        .mapToInt(e -> e.getValue().getSizeAll()).sum();

                return fxMap.getOrDefault(s, 1.0)
                        * (openPos * (chinaWtd.get(s).lastEntry().getValue().getClose()
                        - lastWeekCloseMap.getOrDefault(s, chinaWtd.get(s).firstEntry().getValue().getOpen())));
            } else {
                int openPos = currentPositionMap.getOrDefault(s, 0) - wtdChgInPosition.getOrDefault(s, 0);
                double close = s.equalsIgnoreCase("SGXA50PR") ? futExpiryLevel : chinaWtd.get(s).lastEntry().getValue().getClose();
                return openPos != 0 ? fxMap.getOrDefault(s, 1.0) * (openPos) *
                        (close - lastWeekCloseMap.getOrDefault(s, chinaWtd.get(s).firstEntry().getValue().getOpen())) : 0.0;
            }
        }
        return 0.0;
    }


    private static void computeWtdMtmPnlAll() {
        for (String s : chinaTradeMap.keySet()) {
            if (tickerNotFuture(s)) {
                int posBeforeThisWeek = chinaTradeMap.get(s).entrySet().stream().filter(e -> e.getKey().toLocalDate().isBefore(MONDAY_OF_WEEK))
                        .mapToInt(e -> e.getValue().getSizeAll()).sum();
                weekOpenPositionMap.put(s, posBeforeThisWeek);
                wtdMtmPnlMap.put(s, fxMap.getOrDefault(s, 1.0)
                        * (posBeforeThisWeek * (chinaWtd.get(s).lastEntry().getValue().getClose()
                        - lastWeekCloseMap.getOrDefault(s, chinaWtd.get(s).firstEntry().getValue().getOpen()))));
            } else {
                int openPos = currentPositionMap.getOrDefault(s, 0) - wtdChgInPosition.getOrDefault(s, 0);
                //+ (s.equalsIgnoreCase("SGXA50PR") ? futExpiryUnits : 0);
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

//        ChinaResult(double m, double s, double r, double p) {
//            meanRtn = Math.round(1000d * m) / 10d;
//            sd = Math.round(1000d * s * Math.sqrt(252)) / 10d;
//            sr = Math.round(100d * r) / 100d;
//            perc = p;
//        }

        void fillResult(double m, double s, double r, double p) {
            meanRtn = Math.round(1000d * m) / 10d;
            sd = Math.round(1000d * s * Math.sqrt(252)) / 10d;
            sr = Math.round(100d * r) / 100d;
            perc = p;
        }


        double getMeanRtn() {
            return meanRtn;
        }

        double getSd() {
            return sd;
        }

        double getSr() {
            return sr;
        }

        double getPerc() {
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
            return 45;
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
                case 27:
                    return "vol perc";
                case 28:
                    return "w turnover";
                case 29:
                    return "chg sharpe";
                case 30:
                    return "Mtd Mtm";
                case 31:
                    return "Mtm Trade";
                case 32:
                    return "Mtm Net";
                case 33:
                    return "Mo Open pos";
                case 34:
                    return "Mo chg pos";
                case 35:
                    return "New High Date";
                case 36:
                    return "Trend pnl";
                case 37:
                    return "Owed pnl";
                case 38:
                    return "trend-owed";
                case 39:
                    return "mtd sharpe";
                case 40:
                    return "Expired pos";
                default:
                    return "";
            }
        }

        @Override
        public Object getValueAt(int row, int col) {
            String name = stockList.get(row);
            double price;
            double fx = fxMap.getOrDefault(name, 1.0);

            if (chinaWtd.containsKey(name) && chinaWtd.get(name).size() > 0) {
                price = (name.equalsIgnoreCase("SGXA50PR")) ? futExpiryLevel : chinaWtd.get(name).lastEntry().getValue().getClose();
            } else {
                price = ChinaStock.priceMap.getOrDefault(name, 0.0);
            }

            double mtm = r(computeWtdMtmFor1Stock(name));
            double trade = r(computeWtdTradePnlFor1Stock(name));

            if (!tickerNotFuture(name)) {

                //System.out.println(" cost basis ticker " + name);

                double thisWeekCostBasis = chinaTradeMap.get(name).entrySet().stream()
                        .filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1L)))
                        .mapToDouble(e -> e.getValue().getCostBasisAll(name)).sum();

                double thisWeekTradingCost = chinaTradeMap.get(name).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1L)))
                        .mapToDouble(e -> e.getValue().getTransactionAll(name)).sum();

                costBasisMap.put(name, fx * (-1 * lastWeekCloseMap.getOrDefault(name, 0.0) *
                        weekOpenPositionMap.getOrDefault(name, 0) + thisWeekCostBasis));

                totalTradingCostMap.put(name, fx * thisWeekTradingCost);

                //System.out.println(getStr(" histchina cost basis: ", name, thisWeekCostBasis));

                //System.out.println(" week cost basis " + thisWeekCostBasis + " ");
                //System.out.println(" week")
                //System.out.println(" total trading cost " + costBasisMap.getOrDefault(name, 0.0));
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
//                    if (!tickerNotFuture(name)) {
//                        System.out.println("name / trade map this week " + name + " " + chinaTradeMap.get(name));
//                    }

                    return chinaTradeMap.get(name).entrySet().stream().filter(e -> e.getKey().toLocalDate().isAfter(MONDAY_OF_WEEK.minusDays(1)))
                            .mapToInt(e -> e.getValue().getSizeAll()).sum();
                case 13:
                    return currentPositionMap.getOrDefault(name, 0) - wtdChgInPosition.getOrDefault(name, 0);
                //+ (name.equalsIgnoreCase("SGXA50PR") ? futExpiryUnits : 0);
                case 14:
                    return wtdChgInPosition.getOrDefault(name, 0);
                case 15:
                    return currentPositionMap.getOrDefault(name, 0);
                case 16:
                    return r(totalTradingCostMap.getOrDefault(name, 0.0));
                case 17:
                    return price;
                case 18:
                    return r(fxMap.getOrDefault(name, 1.0) * price * currentPositionMap.getOrDefault(name, 0));
                case 19:
                    return r(costBasisMap.getOrDefault(name, 0.0));
                case 20:
                    return r(fx * price * currentPositionMap.getOrDefault(name, 0) + costBasisMap.getOrDefault(name, 0.0));
                case 21:
                    if (totalTradingCostMap.getOrDefault(name, 1.0) != 0.0) {
                        return Math.round((fx * price * currentPositionMap.getOrDefault(name, 0)
                                + costBasisMap.getOrDefault(name, 0.0)) / totalTradingCostMap.getOrDefault(name, 1.0));
                    } else {
                        return 0L;
                    }
                case 22:
                    return trade;
                case 23:
                    return lastWeekCloseMap.getOrDefault(name, 0.0);
                case 24:
                    return mtm;
//                            r(fx * (currentPositionMap.getOrDefault(name, 0) -
//                            wtdChgInPosition.getOrDefault(name, 0)) *
//                            (price - lastWeekCloseMap.getOrDefault(name, 0.0)));
                case 25:
                    return r(mtm + trade);
                //return r(wtdTradePnlMap.getOrDefault(name, 0.0) + wtdMtmPnlMap.getOrDefault(name, 0.0));
                case 26:
                    return SharpeUtility.getPercentile(chinaWtd.get(name));
                case 27:
                    return computeWVolPerc(name);
                case 28:
                    return (sharesOut.getOrDefault(name, 0L) != 0 && price != 0.0) ?
                            Math.round(1000d * (computeWtdVolTraded(name) / (price * sharesOut.get(name)))) / 10d : 0.0;
                case 29:
                    return r(sharpeWeekChg(name));
                case 30:
                    return r(mtdMtm(name));
                case 31:
                    return r(mtdTradePnl(name));
                case 32:
                    return r(mtdMtm(name) + mtdTradePnl(name));
                case 33:
                    return monthOpenPos(name);
                case 34:
                    return mtdChgPos(name);
                case 35:
                    return chinaYtd.get(name).entrySet().stream().max(Comparator.comparingDouble(e -> e.getValue().getHigh())).map(Map.Entry::getKey)
                            .orElse(LocalDate.MIN);
                case 36:
                    return r(computeTrendPnlSum(name));
                case 37:
                    return r(computeOwedPnl(name));
                case 38:
                    return r(computeTrendPnlSum(name) - computeOwedPnl(name));
                case 39:
                    return r(getMtdSharpe(name));
                case 40:
                    return name.equalsIgnoreCase("SGXA50PR") ? futExpiryUnits : 0;
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
                case 33:
                    return Integer.class;
                case 34:
                    return Integer.class;
                case 35:
                    return LocalDate.class;
                case 40:
                    return Integer.class;
                default:
                    return Double.class;
            }
        }
    }

}

