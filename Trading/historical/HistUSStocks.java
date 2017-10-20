package historical;

import apidemo.ChinaMain;
import apidemo.MorningTask;
import auxiliary.SimpleBar;
import client.Contract;
import client.Types;
import controller.ApiConnection;
import controller.ApiController;
import graph.GraphBarTemporal;
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
import java.util.stream.Collectors;

import static historical.HistHKStocks.getMondayOfWeek;

public class HistUSStocks extends JPanel  {

    static final String USCHINASTOCKFILE = "USChinaStocks.txt";
    static final String USALLFILE = "USAll.txt";
    static final String USFAMOUSFILE = "USFamous.txt";

    private static volatile Semaphore sm = new Semaphore(50);
    public static final LocalDate MONDAY_OF_WEEK = getMondayOfWeek(LocalDateTime.now());

    static final String CUTOFFTIME = getDataCutoff();
    static final int DAYSTOREQUESTYtd = (int) Math.round(ChronoUnit.DAYS.between(
            LocalDate.of(LocalDate.now().getYear() - 1, Month.DECEMBER, 31),
            LocalDate.now())*252/365) ;

    static final int DAYSTOREQUESTWtd = (int) ChronoUnit.DAYS.between(
            getMondayOfWeek(LocalDateTime.now()), LocalDate.now()) + 2;

    static ApiController apcon = new ApiController(
            new ApiController.IConnectionHandler.DefaultConnectionHandler(),
            new ApiConnection.ILogger.DefaultLogger(),
            new ApiConnection.ILogger.DefaultLogger());

    //public Contract ctUS = new Contract();
    //public static NavigableMap<LocalDate, SimpleBar> USSINGLE = new TreeMap<>();
    public static volatile Map<String, NavigableMap<LocalDate, SimpleBar>> USALLYtd = new HashMap<>();
    public static volatile Map<String, NavigableMap<LocalDateTime, SimpleBar>> USALLWtd = new HashMap<>();
    private static volatile Map<String, USResult> USResultMapYtd = new HashMap<>();
    private static volatile Map<String, USResult> USResultMapWtd = new HashMap<>();


    public static List<String> usNameList = new LinkedList<>();
    public static File testOutput = new File(ChinaMain.GLOBALPATH + "usTestData.txt");

    static volatile AtomicInteger uniqueID = new AtomicInteger(60000);
    static volatile String selectedStock = "";
    //public static volatile Map<Integer, String> idStockMap = new HashMap<>();

    static BarModel_US m_model;
    static JPanel graphPanel;
    static JTable tab;
    int modelRow;
    int indexRow;
    static TableRowSorter<BarModel_US> sorter;
    public static volatile JLabel totalStocksLabelYtd = new JLabel("Total Y");
    public static volatile JLabel totalStocksLabelWtd = new JLabel("Total W");
    public static volatile AtomicLong stocksProcessedYtd = new AtomicLong(0);
    public static volatile AtomicLong stocksProcessedWtd = new AtomicLong(0);

    GraphBarTemporal<LocalDate> graphYtd = new GraphBarTemporal<>();
    GraphBarTemporal<LocalDateTime> graphWtd = new GraphBarTemporal<>();

    public static File outputYtd = new File(ChinaMain.GLOBALPATH + "usSharpeYtd.txt");
    public static File outputWtd = new File(ChinaMain.GLOBALPATH + "usSharpeWtd.txt");


    public HistUSStocks() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(ChinaMain.GLOBALPATH + USCHINASTOCKFILE), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                USALLYtd.put(al1.get(0), new TreeMap<>());
                USResultMapYtd.put(al1.get(0), new USResult());
                USALLWtd.put(al1.get(0), new TreeMap<>());
                USResultMapWtd.put(al1.get(0), new USResult());
            }
        } catch (IOException ex) {
        }

        usNameList = USALLYtd.keySet().stream().collect(Collectors.toList());
        System.out.println(" us name list " + usNameList);

        m_model = new BarModel_US();
        graphPanel = new JPanel();

        tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                try {
                    Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                    if (isCellSelected(Index_row, Index_col)) {
                        modelRow = this.convertRowIndexToModel(Index_row);
                        selectedStock = usNameList.get(modelRow);
                        graphYtd.fillInGraphHKGen(selectedStock, USALLYtd);
                        graphWtd.fillInGraphHKGen(selectedStock, USALLWtd);
                        comp.setBackground(Color.GREEN);
                    } else {
                        comp.setBackground((Index_row % 2 == 0) ? Color.lightGray : Color.white);
                    }
                    return comp;
                } catch (Exception e) {
                    //throw new RuntimeException("");
                }
                return null;
            }
        };

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                //d.height = 600;
                d.width = 1000;
                return d;
            }
        };

        JScrollPane jp1 = new JScrollPane(graphYtd) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d =  super.getPreferredSize();
                d.height = 250;
                d.width = 1900;
                return d;
            }
        };

        JScrollPane jp2 = new JScrollPane(graphWtd){
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                d.width = 1900;
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

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(al -> {
            System.out.println(" refreshing ");
            SwingUtilities.invokeLater(() -> {
                this.repaint();
                m_model.fireTableDataChanged();
            });
        });

        JButton getHistoricalYtdButton = new JButton("Ytd");
        getHistoricalYtdButton.addActionListener(al -> {
            sm = new Semaphore(50);
            requestAllUSStocksYtd();
        });

        JButton getHistoricalWtdButton = new JButton("Wtd");
        getHistoricalWtdButton.addActionListener(al -> {
            sm = new Semaphore(50);
            requestAllUSStocksWtd();
        });

        JButton outputYtdButton = new JButton("Output Y");
        outputYtdButton.addActionListener(al->{
            if(USALLYtd.containsKey(selectedStock)) {
                MorningTask.clearFile(testOutput);
                USALLYtd.get(selectedStock).entrySet().forEach(e->
                        MorningTask.simpleWriteToFile(
                                Utility.getStrTabbed(e.getKey(),e.getValue().getOpen(),e.getValue().getHigh()
                                        ,e.getValue().getLow()
                                        ,e.getValue().getClose()), true,testOutput));
            } else {
                System.out.println(" cannot find stock for outtputting ytd " + selectedStock);
            }
        });

        JButton outputWtdButton = new JButton("Output Y");
        outputWtdButton.addActionListener(al-> {
            if(USALLWtd.containsKey(selectedStock)) {
                MorningTask.clearFile(testOutput);
                USALLWtd.get(selectedStock).entrySet().forEach(e ->
                        MorningTask.simpleWriteToFile(
                                Utility.getStrTabbed(e.getKey(), e.getValue().getOpen(), e.getValue().getHigh()
                                        , e.getValue().getLow()
                                        , e.getValue().getClose()), true, testOutput));
            } else {
                System.out.println(" cannot find stock for outputting wtd " + selectedStock);
            }
        });



        controlPanel.add(refreshButton);
        controlPanel.add(getHistoricalYtdButton);
        controlPanel.add(getHistoricalWtdButton);
        controlPanel.add(outputYtdButton);
        controlPanel.add(outputWtdButton);

        totalStocksLabelYtd.setFont(totalStocksLabelYtd.getFont().deriveFont(30L));
        controlPanel.add(totalStocksLabelYtd);

        totalStocksLabelWtd.setFont(totalStocksLabelWtd.getFont().deriveFont(30L));
        controlPanel.add(totalStocksLabelWtd);


        graphPanel.setLayout(new GridLayout(2,1));
        graphPanel.add(jp1);
        graphPanel.add(jp2);


        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(graphPanel,BorderLayout.SOUTH);

        tab.setAutoCreateRowSorter(true);
        sorter = (TableRowSorter<BarModel_US>) tab.getRowSorter();
    }

    public static void refreshYtd() {
        SwingUtilities.invokeLater(() -> {
            totalStocksLabelYtd.setText(Long.toString(stocksProcessedYtd.get()) + "/" + Long.toString(USALLYtd.size()));
            //System.out.println(" refreshing all ");
            //m_model.fireTableDataChanged();
        });
    }

    public static void refreshWtd() {
        SwingUtilities.invokeLater(() -> {
            totalStocksLabelWtd.setText(Long.toString(stocksProcessedWtd.get()) + "/" + Long.toString(USALLWtd.size()));
            //System.out.println(" refreshing all ");
            //m_model.fireTableDataChanged();
        });
    }

    void requestAllUSStocksYtd() {
        stocksProcessedYtd = new AtomicLong(0);
        MorningTask.clearFile(HistUSStocks.outputYtd);
        USALLYtd.keySet().forEach(k -> request1StockYtd(k));
    }

    void requestAllUSStocksWtd() {
        stocksProcessedWtd = new AtomicLong(0);
        MorningTask.clearFile(HistUSStocks.outputWtd);
        USALLWtd.keySet().forEach(k -> request1StockWtd(k));
    }

    Contract generateUSContract(String stock) {
        Contract ct = new Contract();
        ct.symbol(stock);
        ct.currency("USD");
        ct.exchange("SMART");
        ct.secType(Types.SecType.STK);
        return ct;
    }

    void request1StockYtd(String stock) {
        CompletableFuture.runAsync(() -> {
            //System.out.println(" request stock in completefuture " + Thread.currentThread().getName());
            //System.out.println(" available " + sm.availablePermits());
            //System.out.println(" queue length is " + sm.getQueueLength());

            try {
                //System.out.println(" permits before " + sm.availablePermits());
                sm.acquire();
                //System.out.println(" stock is " + stock);
                //idStockMap.put(uniqueID.incrementAndGet(), stock);
                apcon.reqHistoricalDataUSHK(new YtdHandler(), uniqueID.incrementAndGet(),
                        generateUSContract(stock), CUTOFFTIME, DAYSTOREQUESTYtd, Types.DurationUnit.DAY,
                        Types.BarSize._1_day, Types.WhatToShow.TRADES, true);
            } catch (InterruptedException ex) {
                Logger.getLogger(HistHKStocks.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    void request1StockWtd(String stock) {
        CompletableFuture.runAsync(() -> {
            //System.out.println(" request stock in completefuture " + Thread.currentThread().getName());
            //System.out.println(" available " + sm.availablePermits());
            //System.out.println(" queue length is " + sm.getQueueLength());

            try {
                //System.out.println(" permits before " + sm.availablePermits());
                sm.acquire();
                //System.out.println(" stock is " + stock);
                //idStockMap.put(uniqueID.incrementAndGet(), stock);
                apcon.reqHistoricalDataUSHK(new WtdHandler(), uniqueID.incrementAndGet(),
                        generateUSContract(stock), CUTOFFTIME, DAYSTOREQUESTWtd, Types.DurationUnit.DAY,
                        Types.BarSize._5_mins, Types.WhatToShow.TRADES, true);
            } catch (InterruptedException ex) {
                Logger.getLogger(HistHKStocks.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    static String getDataCutoff() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));
    }

    void connectToTWS(int port) {
        System.out.println(" trying to connect");
        try {
            apcon.connect("127.0.0.1", port, 101, "");
        } catch (Exception ex) {
            System.out.println(" connect to tws failed ");
        }
    }

    static ApiController getAPICon() {
        return apcon;
    }

    public static void computeAll() {
        System.out.println(" computing starts ");
        USALLYtd.keySet().forEach(k -> {
            NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(USALLYtd.get(k),
                    LocalDate.of(2016, Month.DECEMBER, 31));
            double mean = SharpeUtility.getMean(ret);
            double sd = SharpeUtility.getSD(ret);
            double sr = SharpeUtility.getSharpe(ret, 252);
            double perc = SharpeUtility.getPercentile(USALLYtd.get(k));
            System.out.println(Utility.getStrTabbed(" stock mean sd sr ", k, mean, sd, sr));
        });
    }

    public static void computeYtd(String stock) {
        NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(USALLYtd.get(stock),
                LocalDate.of(2016, Month.DECEMBER, 31));
        double mean = SharpeUtility.getMean(ret);
        double sd = SharpeUtility.getSD(ret);
        double sr = SharpeUtility.getSharpe(ret,252);
        double perc = SharpeUtility.getPercentile(USALLYtd.get(stock));
        USResultMapYtd.get(stock).fillResult(mean, sd, sr, perc);
        System.out.println(Utility.getStrTabbed(" stock mean sd sr perc", stock, mean, sd, sr, perc));
    }

    public static void computeWtd(String stock) {
        System.out.println(" computing Wtd starts for stock " + stock);
        NavigableMap<LocalDateTime, Double> ret = SharpeUtility.getReturnSeries(USALLWtd.get(stock),
                LocalDateTime.of(MONDAY_OF_WEEK.minusDays(1), LocalTime.MIN));
        double mean = SharpeUtility.getMean(ret);
        double sd = SharpeUtility.getSD(ret);
        double sr = SharpeUtility.getSharpe(ret,48);
        double perc = SharpeUtility.getPercentile(USALLWtd.get(stock));
        USResultMapWtd.get(stock).fillResult(mean, sd, sr, perc);
        System.out.println(Utility.getStrTabbed(" wtd stock mean sd sr perc size firstEntry last Entry",
                stock, mean, sd, sr, perc,ret.size(), ret.firstEntry(), ret.lastEntry()));
    }


    public static void main(String[] args) {
        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1900, 1000));
        HistUSStocks us = new HistUSStocks();
        jf.add(us);
        jf.setLayout(new FlowLayout());
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
        CompletableFuture.runAsync(() -> {
            us.connectToTWS(7496);
        });
    }



    class YtdHandler implements handler.HistoricalHandler {
        @Override
        public void handleHist(String name, String date, double open, double high, double low, double close) {
            USALLYtd.get(name).put(convertStringToDate(date), new SimpleBar(open, high, low, close));
        }

        @Override
        public void actionUponFinish(String name) {
            //System.out.println(" YTD action upon finish for " + name);
            stocksProcessedYtd.incrementAndGet();
            sm.release(1);
            computeYtd(name);
            refreshYtd();
        }
    }

    class WtdHandler implements handler.HistoricalHandler {
        @Override
        public void handleHist(String name, String date, double open, double high, double low, double close) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            LocalDateTime ldt = LocalDateTime.of(ld,lt);

            LocalDate monOfWeek = getMondayOfWeek(LocalDateTime.now());

            if (ld.isEqual(monOfWeek) || ld.isAfter(monOfWeek)) {
                //System.out.println(" CORRECT name ld lt open " + name + " " + ld + " " + lt + " " + open);
                USALLWtd.get(name).put(ldt, new SimpleBar(open, high, low, close));
            }
        }

        @Override
        public void actionUponFinish(String name) {
            //System.out.println(" WTD action upon finish for " + name);
            stocksProcessedWtd.incrementAndGet();
            sm.release(1);
            //System.out.println(" current permit after done " + HistHKStocks.sm.availablePermits());
            computeWtd(name);
            refreshWtd();

        }
    }



    public LocalDate convertStringToDate(String date) {
        LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
        return ld;
    }

    private class USResult {

        double mean;
        double sd;
        double sr;
        double perc;

        USResult(double m, double s, double r, double p) {
            mean = m;
            sd = s;
            sr = r;
            perc = p;
        }

        void fillResult(double m, double s, double r, double p) {
            mean = Math.round(1000d * m) / 10d;
            sd = Math.round(1000d * s * Math.sqrt(252)) / 10d;
            sr = Math.round(100d * r) / 100d;
            perc = p;
        }

        USResult() {
            mean = 0.0;
            sd = 0.0;
            sr = 0.0;
            perc = 0.0;
        }

        public double getMean() {
            return mean;
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

    private class BarModel_US extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return USALLYtd.size();
        }

        @Override
        public int getColumnCount() {
            return 11;
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

            String name = usNameList.get(rowIn);
            //System.out.println(" row in " + rowIn + " name " + name);
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return USResultMapYtd.get(name).getMean();
                case 2:
                    return USResultMapYtd.get(name).getSd();
                case 3:
                    return USResultMapYtd.get(name).getSr();
                case 4:
                    return USResultMapYtd.get(name).getPerc();
                case 5:
                    return USALLYtd.get(name).size();
                case 6:
                    return USResultMapWtd.get(name).getMean();

                case 7:
                    return USResultMapWtd.get(name).getSd();

                case 8:
                    return USResultMapWtd.get(name).getSr();
                case 9:
                    return USResultMapWtd.get(name).getPerc();
                case 10:
                    return USALLWtd.get(name).size();

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
