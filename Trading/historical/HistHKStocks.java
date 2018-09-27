package historical;

import apidemo.AutoTraderHK;
import apidemo.TradingConstants;
import auxiliary.SimpleBar;
import client.Types;
import controller.ApiConnection;
import controller.ApiController;
import graph.GraphBarTemporal;
import handler.HistoricalHandler;
import utility.SharpeUtility;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static utility.Utility.getMondayOfWeek;

public class HistHKStocks extends JPanel {

    private static final String CUTOFFTIME = getDataCutoff();
    private static final int DAYSTOREQUESTYTD = (int) Math.round(ChronoUnit.DAYS.between(
            LocalDate.of(LocalDate.now().getYear() - 1, Month.DECEMBER, 31), LocalDate.now()) * 252 / 365);

    private static final int DAYSTOREQUESTWTD = (int) ChronoUnit.DAYS.between(
            getMondayOfWeek(LocalDateTime.now()), LocalDate.now()) + 1;


    //public static volatile long totalBeingProcessed = 0;
    static volatile AtomicLong stocksProcessedYtd = new AtomicLong(0);
    static volatile AtomicLong stocksProcessedWtd = new AtomicLong(0);

    private static volatile Semaphore sm = new Semaphore(50);

    private static final LocalDate MONDAY_OF_WEEK = getMondayOfWeek(LocalDateTime.now());

    private final static File hkTestOutput = new File(TradingConstants.GLOBALPATH + "hkTestData.txt");

    //ScheduledExecutorService es = Executors.newSingleThreadScheduledExecutor();
    private static ApiController apcon = new ApiController(new ApiController.IConnectionHandler.DefaultConnectionHandler(),
            new ApiConnection.ILogger.DefaultLogger(),
            new ApiConnection.ILogger.DefaultLogger());

    //public static volatile Contract ctHK = new Contract();
    static volatile Map<String, NavigableMap<LocalDate, SimpleBar>> hkYtdAll = new HashMap<>();
    static volatile Map<String, NavigableMap<LocalDateTime, SimpleBar>> hkWtdAll = new HashMap<>();
    private static volatile Map<String, HKResult> HKResultMapYtd = new HashMap<>();
    private static volatile Map<String, HKResult> HKResultMapWtd = new HashMap<>();

    private static List<String> hkNameList = new LinkedList<>();
    private static File outputYtd = new File(TradingConstants.GLOBALPATH + "hkSharpeYtd.txt");
    private static File outputWtd = new File(TradingConstants.GLOBALPATH + "hkSharpeWtd.txt");

    private static volatile AtomicInteger uniqueID = new AtomicInteger(70000);

    private static volatile String selectedStock = "";

    //public static volatile Map<Integer, String> idStockMap = new HashMap<>();

    private static BarModel_HK m_model;
    private int modelRow;
    private static JPanel graphPanel;
    private GraphBarTemporal<LocalDate> graphYtd = new GraphBarTemporal<>();
    private GraphBarTemporal<LocalDateTime> graphWtd = new GraphBarTemporal<>();

    private static JLabel totalStocksLabelYtd = new JLabel("Total Ytd");
    private static JLabel totalStocksLabelWtd = new JLabel("Total Wtd");

    private HistHKStocks() {
        System.out.println(" monday of week " + MONDAY_OF_WEEK);
        System.out.println(" days to request WTD " + DAYSTOREQUESTWTD);
        String line;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "hkMainList.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                hkYtdAll.put(al1.get(0), new TreeMap<>());
                hkWtdAll.put(al1.get(0), new TreeMap<>());
                HKResultMapYtd.put(al1.get(0), new HKResult());
                HKResultMapWtd.put(al1.get(0), new HKResult());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        hkNameList = new ArrayList<>(hkYtdAll.keySet());
        System.out.println(" hk name list " + hkNameList);

        m_model = new BarModel_HK();
        graphPanel = new JPanel();

        JScrollPane jp1 = new JScrollPane(graphYtd) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                d.width = 1900;
                return d;
            }
        };

        JScrollPane jp2 = new JScrollPane(graphWtd) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                d.width = 1900;
                return d;
            }
        };
        //jp1.add();
        //jp2.add(graphWtd);
        graphPanel.setLayout(new GridLayout(2, 1));
        graphPanel.add(jp1);
        graphPanel.add(jp2);


        JTable tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int indexRow, int indexCol) {
                try {
                    Component comp = super.prepareRenderer(renderer, indexRow, indexCol);
                    if (isCellSelected(indexRow, indexCol)) {
                        modelRow = this.convertRowIndexToModel(indexRow);
                        selectedStock = hkNameList.get(modelRow);
                        //System.out.println(" selected stock in monitor is " + selectedStock);
                        comp.setBackground(Color.GREEN);
                        graphYtd.fillInGraphHKGen(selectedStock, hkYtdAll);
                        graphWtd.fillInGraphHKGen(selectedStock, hkWtdAll);
                        SwingUtilities.invokeLater(() -> graphPanel.repaint());

                    } else {
                        comp.setBackground((indexRow % 2 == 0) ? Color.lightGray : Color.white);
                    }
                    return comp;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        };

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                //d.height = 1000;
                d.width = 1000;
                return d;
            }
        };

        JPanel controlPanel = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 100;
                return d;
            }
        };

        JButton getYtdButton = new JButton("Ytd");
        JButton getWtdButton = new JButton("Wtd");
        JButton outputYtdButton = new JButton("Output Y");
        JButton outputWtdButton = new JButton("Output W");

        getYtdButton.addActionListener(al -> {
            sm = new Semaphore(50);
            requestAllHKStocksYtd();
        });

        getWtdButton.addActionListener(al -> {
            sm = new Semaphore(50);
            requestAllHKStocksWtd();
        });

        outputYtdButton.addActionListener(al -> {
            if (hkYtdAll.containsKey(selectedStock)) {
                Utility.clearFile(hkTestOutput);
                hkYtdAll.get(selectedStock).forEach((key, value) -> Utility.simpleWriteToFile(
                        Utility.getStrTabbed(key, value.getOpen(), value.getHigh()
                                , value.getLow()
                                , value.getClose()), true, hkTestOutput));
            } else {
                System.out.println(" cannot find stock for outtputting ytd " + selectedStock);
            }
        });

        outputWtdButton.addActionListener(al -> {
            if (hkWtdAll.containsKey(selectedStock)) {
                Utility.clearFile(hkTestOutput);
                hkWtdAll.get(selectedStock).forEach((key, value) -> Utility.simpleWriteToFile(
                        Utility.getStrTabbed(key, value.getOpen(), value.getHigh()
                                , value.getLow()
                                , value.getClose()), true, hkTestOutput));
            } else {
                System.out.println(" cannot find stock for outputting wtd " + selectedStock);
            }
        });

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(al -> {
            System.out.println(" refreshing ");
            SwingUtilities.invokeLater(() -> {
                this.repaint();
                m_model.fireTableDataChanged();
            });
        });

        controlPanel.add(refreshButton);
        controlPanel.add(getYtdButton);
        controlPanel.add(getWtdButton);
        controlPanel.add(outputYtdButton);
        controlPanel.add(outputWtdButton);

        totalStocksLabelYtd.setFont(totalStocksLabelYtd.getFont().deriveFont(30L));
        controlPanel.add(totalStocksLabelYtd);

        totalStocksLabelWtd.setFont(totalStocksLabelWtd.getFont().deriveFont(30L));
        controlPanel.add(totalStocksLabelWtd);

        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(graphPanel, BorderLayout.SOUTH);

        tab.setAutoCreateRowSorter(true);
        //noinspection unchecked,unused
        TableRowSorter<BarModel_HK> sorter = (TableRowSorter<BarModel_HK>) tab.getRowSorter();

    }

    private void requestAllHKStocksYtd() {
        stocksProcessedYtd = new AtomicLong(0);
        Utility.clearFile(HistHKStocks.outputYtd);
        hkYtdAll.keySet().forEach(this::request1StockYtd);
        //request1StockYtd("700");
    }

    private void requestAllHKStocksWtd() {
        stocksProcessedWtd = new AtomicLong(0);
        Utility.clearFile(HistHKStocks.outputWtd);
        hkWtdAll.keySet().forEach(this::request1StockWtd);
        //request1StockWtd("700");
    }


    private static void refreshYtd() {
        totalStocksLabelYtd.setText(Long.toString(stocksProcessedYtd.get()) + "/" + Long.toString(hkYtdAll.size()));
        //System.out.println(" refreshing YTD ");
        //m_model.fireTableDataChanged();
    }

    private static void refreshWtd() {
        totalStocksLabelWtd.setText(Long.toString(stocksProcessedWtd.get()) + "/" + Long.toString(hkWtdAll.size()));
        //System.out.println(" refreshing WTD ");
        //m_model.fireTableDataChanged();
    }

    private void request1StockYtd(String stock) {
        CompletableFuture.runAsync(() -> {
            //System.out.println(" request stock in completefuture " + Thread.currentThread().getSymbol());
            //System.out.println(" available " + sm.availablePermits());
            //System.out.println(" queue length is " + sm.getQueueLength());

            try {
                //System.out.println(" permits before " + sm.availablePermits());
                sm.acquire();
                //System.out.println(" unique id " + uniqueID.get() + " stock " + idStockMap.get(uniqueID.get()));
                //System.out.println(" cut off time " + cutoffTime + " days to request " + daysToRequest);
                System.out.println(" stock is " + stock);
                //idStockMap.put(uniqueID.incrementAndGet(), stock);
                System.out.println(" days requested  " + DAYSTOREQUESTYTD);
                apcon.reqHistoricalDataUSHK(new YtdDataHandler(), uniqueID.incrementAndGet(), AutoTraderHK.tickerToHKContract(stock), CUTOFFTIME,
                        DAYSTOREQUESTYTD, Types.DurationUnit.DAY,
                        Types.BarSize._1_day, Types.WhatToShow.TRADES, true);
            } catch (InterruptedException ex) {
                Logger.getLogger(HistHKStocks.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    private void request1StockWtd(String stock) {
        CompletableFuture.runAsync(() -> {
            //System.out.println(" request stock in completefuture " + Thread.currentThread().getSymbol());
            //System.out.println(" available " + sm.availablePermits());
            //System.out.println(" queue length is " + sm.getQueueLength());

            try {
                //System.out.println(" permits before " + sm.availablePermits());
                sm.acquire();
                //System.out.println(" unique id " + uniqueID.get() + " stock " + idStockMap.get(uniqueID.get()));
                //System.out.println(" cut off time " + cutoffTime + " days to request " + daysToRequest);
                System.out.println(" stock is " + stock);
                //idStockMap.put(uniqueID.incrementAndGet(), stock);
                apcon.reqHistoricalDataUSHK(new WtdDataHandler()
                        , uniqueID.incrementAndGet(), AutoTraderHK.tickerToHKContract(stock), CUTOFFTIME,
                        DAYSTOREQUESTWTD, Types.DurationUnit.DAY,
                        Types.BarSize._5_mins, Types.WhatToShow.TRADES, true);
            } catch (InterruptedException ex) {
                Logger.getLogger(HistHKStocks.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    private static String getDataCutoff() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));
    }

    @SuppressWarnings("SameParameterValue")
    private void connectToTWS(int port) {
        System.out.println(" trying to connect");
        try {
            apcon.connect("127.0.0.1", port, 101, "");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        //apcon.client().reqIds(-1);
    }

//    static ApiController getAPICon() {
//        return apcon;
//    }

//    public static void computeAll() {
//        System.out.println(" computing starts ");
//        hkYtdAll.keySet().forEach(k -> {
//            NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(hkYtdAll.get(k),
//                    LocalDate.of(2016, Month.DECEMBER, 31));
//            double mean = SharpeUtility.getMean(ret);
//            double sd = SharpeUtility.getSD(ret);
//            double sr = SharpeUtility.getSharpe(ret,252);
//            double perc = SharpeUtility.getPercentile(hkYtdAll.get(k));
//
//            System.out.println(Utility.getStrTabbed(" stock mean sd sr perc ", k, mean, sd, sr, perc));
//        });
//    }

    static void computeYtd(String stock) {
        System.out.println(" computing Ytd starts for stock " + stock);
        NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(hkYtdAll.get(stock),
                LocalDate.of(2016, Month.DECEMBER, 31));
        double mean = SharpeUtility.getMean(ret);
        double sdDay = SharpeUtility.getSD(ret);
        double sr = SharpeUtility.getSharpe(ret, 252);
        double perc = SharpeUtility.getPercentile(hkYtdAll.get(stock));
        HKResultMapYtd.get(stock).fillResult(mean, sdDay, sr, perc);
        System.out.println(Utility.getStrTabbed(" stock mean sd sr perc size firstEntry"
                , stock, mean, sdDay, sr, perc, ret.size(), ret.firstEntry(), ret.lastEntry()));

//        if(stock.equals("700")) {
//            System.out.println(" outputting 700 ");
//            hkYtdAll.get(stock).entrySet().forEach(e->
//                    MorningTask.simpleWriteToFile(
//                            Utility.getStrTabbed(e.getKey(),e.getValue().getOpen(),e.getValue().getHigh()
//                                    ,e.getValue().getLow()
//                            ,e.getValue().getClose()), true, hkTestOutput));
//        }
    }

    static void computeWtd(String stock) {
        System.out.println(" computing Wtd starts for stock " + stock);
        NavigableMap<LocalDateTime, Double> ret = SharpeUtility.getReturnSeries(hkWtdAll.get(stock),
                LocalDateTime.of(MONDAY_OF_WEEK.minusDays(1), LocalTime.MIN));
        double mean = SharpeUtility.getMean(ret);
        double sdDay = SharpeUtility.getSD(ret) * Math.sqrt(68);
        double sr = SharpeUtility.getSharpe(ret, 68);
        double perc = SharpeUtility.getPercentile(hkWtdAll.get(stock));
        HKResultMapWtd.get(stock).fillResult(mean, sdDay, sr, perc);
        System.out.println(Utility.getStrTabbed(" wtd stock mean sd sr perc size firstEntry last Entry",
                stock, mean, sdDay, sr, perc, ret.size(), ret.firstEntry(), ret.lastEntry()));

//        if(stock.equals("700")) {
//            System.out.println(" outputting 700 ");
//            hkWtdAll.get(stock).entrySet().forEach(e ->
//                    MorningTask.simpleWriteToFile(
//                            Utility.getStrTabbed(e.getKey(), e.getValue().getOpen(), e.getValue().getHigh()
//                                    , e.getValue().getLow()
//                                    , e.getValue().getClose()), true, hkTestOutput));
//        }
    }

    static class YtdDataHandler implements HistoricalHandler {
        @Override
        public void handleHist(String name, String date, double open, double high, double low, double close) {
            hkYtdAll.get(name).put(convertStringToDate(date), new SimpleBar(open, high, low, close));
        }

        @Override
        public void actionUponFinish(String name) {
            stocksProcessedYtd.incrementAndGet();
            sm.release(1);
            //System.out.println(" current permit after done " + HistHKStocks.sm.availablePermits());
            computeYtd(name);
            refreshYtd();
        }
    }


    //from hkdata get data today
    static class WtdDataHandler implements HistoricalHandler {

        @Override
        public void handleHist(String name, String date, double open, double high, double low, double close) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            LocalDateTime ldt = LocalDateTime.of(ld, lt);

            LocalDate monOfWeek = getMondayOfWeek(LocalDateTime.now());

            if (ld.isEqual(monOfWeek) || ld.isAfter(monOfWeek)) {
                //System.out.println(" CORRECT name ld lt open " + name + " " + ld + " " + lt + " " + open);
                hkWtdAll.get(name).put(ldt, new SimpleBar(open, high, low, close));
//                if (name.equals("700")) {
////                    System.out.println(" outputting tencent");
////                    MorningTask.simpleWriteToFile(Utility.getStrTabbed(lt, open, high, low, close), true,
////                            usTestOutput);
//                }
            }
        }

        @Override
        public void actionUponFinish(String name) {
            stocksProcessedWtd.incrementAndGet();
            sm.release(1);
            //System.out.println(" current permit after done " + HistHKStocks.sm.availablePermits());
            computeWtd(name);
            refreshWtd();

        }
    }



    public static void main(String[] args) {
        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1000, 1000));

        HistHKStocks hk = new HistHKStocks();
        jf.add(hk);
        jf.setLayout(new FlowLayout());
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jf.setVisible(true);
        CompletableFuture.runAsync(() -> hk.connectToTWS(7496));
    }


    static LocalDate convertStringToDate(String date) {
        return LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private class HKResult {

        double mean;
        double sd;
        double sr;
        double perc;

//        HKResult(double m, double s, double r, double p) {
//            mean = m;
//            sd = s;
//            sr = r;
//            perc = p;
//        }

        void fillResult(double m, double s, double r, double p) {
            mean = Math.round(1000d * m) / 10d;
            sd = Math.round(1000d * s * Math.sqrt(252)) / 10d;
            sr = Math.round(100d * r) / 100d;
            perc = p;
        }

        HKResult() {
            mean = 0.0;
            sd = 0.0;
            sr = 0.0;
            perc = 0.0;
        }

        double getMean() {
            return mean;
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

    private class BarModel_HK extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return hkYtdAll.size();
        }

        @Override
        public int getColumnCount() {
            return 15;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "name";
                case 1:
                    return "meanY";
                case 2:
                    return "sdY";
                case 3:
                    return "SharpeY";
                case 4:
                    return "PercY";
                case 5:
                    return "DaysY";
                case 6:
                    return "meanW";
                case 7:
                    return "sdW";
                case 8:
                    return "sharpeW";
                case 9:
                    return "percW";
                case 10:
                    return "DaysW";
                default:
                    return "";
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {

            String name = hkNameList.get(rowIn);
            //System.out.println(" row in " + rowIn + " name " + name);
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return HKResultMapYtd.get(name).getMean();
                case 2:
                    return HKResultMapYtd.get(name).getSd();
                case 3:
                    return HKResultMapYtd.get(name).getSr();
                case 4:
                    return HKResultMapYtd.get(name).getPerc();
                case 5:
                    return hkYtdAll.get(name).size();

                case 6:
                    return HKResultMapWtd.get(name).getMean();
                case 7:
                    return HKResultMapWtd.get(name).getSd();
                case 8:
                    return HKResultMapWtd.get(name).getSr();
                case 9:
                    return HKResultMapWtd.get(name).getPerc();
                case 10:
                    return hkWtdAll.get(name).size();


                default:
                    return null;
            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return String.class;
                default:
                    return Double.class;
            }
        }
    }
}
