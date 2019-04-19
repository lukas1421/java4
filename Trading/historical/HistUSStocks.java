package historical;

import api.TradingConstants;
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

//import javax.swing.table.TableRowSorter;

public class HistUSStocks extends JPanel {

    //static final String USCHINASTOCKFILE = "USChinaStocks.txt";
    private static final String USALLFILE = "USAll.txt";
    //static final String USFAMOUSFILE = "USFamous.txt";
    private static final String USCurrent = USALLFILE;

    private static volatile Semaphore sm = new Semaphore(50);
    private static final LocalDate MONDAY_OF_WEEK = getMondayOfWeek(LocalDateTime.now());

    private static final String CUTOFFTIME = getDataCutoff();
    private static final int DAYSTOREQUESTYtd = (int) Math.round(ChronoUnit.DAYS.between(
            LocalDate.of(LocalDate.now().getYear() - 1, Month.DECEMBER, 31),
            LocalDate.now()) * 252 / 365);

    private static final int DAYSTOREQUESTWtd = (int) ChronoUnit.DAYS.between(
            getMondayOfWeek(LocalDateTime.now()), LocalDate.now()) + 2;

    private static ApiController apcon = new ApiController(
            new ApiController.IConnectionHandler.DefaultConnectionHandler(),
            new ApiConnection.ILogger.DefaultLogger(),
            new ApiConnection.ILogger.DefaultLogger());

    //public Contract ctUS = new Contract();
    //public static NavigableMap<LocalDate, SimpleBar> USSINGLE = new TreeMap<>();
    private static volatile Map<String, NavigableMap<LocalDate, SimpleBar>> USALLYtd = new HashMap<>();
    private static volatile Map<String, NavigableMap<LocalDateTime, SimpleBar>> USALLWtd = new HashMap<>();
    private static volatile Map<String, USResult> USResultMapYtd = new HashMap<>();
    private static volatile Map<String, USResult> USResultMapWtd = new HashMap<>();


    private static List<String> usNameList = new LinkedList<>();
    private static File usTestOutput = new File(TradingConstants.GLOBALPATH + "usTestData.txt");

    private static volatile AtomicInteger uniqueID = new AtomicInteger(60000);
    private static volatile String selectedStock = "";
    //public static volatile Map<Integer, String> idStockMap = new HashMap<>();

    private static BarModel_US m_model;
    private int modelRow;
    private static volatile JLabel totalStocksLabelYtd = new JLabel("Total Y");
    private static volatile JLabel totalStocksLabelWtd = new JLabel("Total W");
    private static volatile AtomicLong stocksProcessedYtd = new AtomicLong(0);
    private static volatile AtomicLong stocksProcessedWtd = new AtomicLong(0);

    private GraphBarTemporal<LocalDate> graphYtd = new GraphBarTemporal<>();
    private GraphBarTemporal<LocalDateTime> graphWtd = new GraphBarTemporal<>();

    private static File outputYtd = new File(TradingConstants.GLOBALPATH + "usSharpeYtd.txt");
    private static File outputWtd = new File(TradingConstants.GLOBALPATH + "usSharpeWtd.txt");


    @SuppressWarnings("unchecked")
    private HistUSStocks() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + USCurrent), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                USALLYtd.put(al1.get(0), new TreeMap<>());
                USResultMapYtd.put(al1.get(0), new USResult());
                USALLWtd.put(al1.get(0), new TreeMap<>());
                USResultMapWtd.put(al1.get(0), new USResult());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        usNameList = new ArrayList<>(USALLYtd.keySet());
        System.out.println(" us name list " + usNameList);

        m_model = new BarModel_US();
        JPanel graphPanel = new JPanel();

        JTable tab = new JTable(m_model) {
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
        outputYtdButton.addActionListener(al -> {
            if (USALLYtd.containsKey(selectedStock)) {
                Utility.clearFile(usTestOutput);
                USALLYtd.get(selectedStock).forEach((key, value) -> Utility.simpleWriteToFile(
                        Utility.getStrTabbed(key, value.getOpen(), value.getHigh()
                                , value.getLow()
                                , value.getClose()), true, usTestOutput));
            } else {
                System.out.println(" cannot find stock for outtputting ytd " + selectedStock);
            }
        });

        JButton outputWtdButton = new JButton("Output W");
        outputWtdButton.addActionListener(al -> {
            if (USALLWtd.containsKey(selectedStock)) {
                Utility.clearFile(usTestOutput);
                USALLWtd.get(selectedStock).forEach((key, value) -> Utility.simpleWriteToFile(
                        Utility.getStrTabbed(key, value.getOpen(), value.getHigh()
                                , value.getLow()
                                , value.getClose()), true, usTestOutput));
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


        graphPanel.setLayout(new GridLayout(2, 1));
        graphPanel.add(jp1);
        graphPanel.add(jp2);


        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(graphPanel, BorderLayout.SOUTH);

        tab.setAutoCreateRowSorter(true);
        //TableRowSorter<BarModel_US> sorter = (TableRowSorter<BarModel_US>) tab.getRowSorter();
    }

    private static void refreshYtd() {
        SwingUtilities.invokeLater(() -> {
            totalStocksLabelYtd.setText(Long.toString(stocksProcessedYtd.get()) + "/" + Long.toString(USALLYtd.size()));
            //System.out.println(" refreshing all ");
            //m_model.fireTableDataChanged();
        });
    }

    private static void refreshWtd() {
        SwingUtilities.invokeLater(() -> {
            totalStocksLabelWtd.setText(Long.toString(stocksProcessedWtd.get()) + "/" + Long.toString(USALLWtd.size()));
            //System.out.println(" refreshing all ");
            //m_model.fireTableDataChanged();
        });
    }

    private void requestAllUSStocksYtd() {
        stocksProcessedYtd = new AtomicLong(0);
        Utility.clearFile(HistUSStocks.outputYtd);
        USALLYtd.keySet().forEach(this::request1StockYtd);
    }

    private void requestAllUSStocksWtd() {
        stocksProcessedWtd = new AtomicLong(0);
        Utility.clearFile(HistUSStocks.outputWtd);
        USALLWtd.keySet().forEach(this::request1StockWtd);
    }

    private Contract generateUSStkContract(String symb) {
        Contract ct = new Contract();
        ct.symbol(symb);
        ct.currency("USD");
        ct.exchange("SMART");
        if (symb.equals("ASHR") || symb.equals("MSFT") || symb.equals("CSCO")) {
            ct.primaryExch("ARCA");
        }
        ct.secType(Types.SecType.STK);
        return ct;
    }

    private void request1StockYtd(String stock) {
        CompletableFuture.runAsync(() -> {
            //System.out.println(" request stock in completefuture " + Thread.currentThread().getSymbol());
            //System.out.println(" available " + sm.availablePermits());
            //System.out.println(" queue length is " + sm.getQueueLength());

            try {
                //System.out.println(" permits before " + sm.availablePermits());
                sm.acquire();
                //System.out.println(" stock is " + stock);
                //idStockMap.put(uniqueID.incrementAndGet(), stock);
                apcon.reqHistoricalDataUSHK(new YtdHandler(), uniqueID.incrementAndGet(),
                        generateUSStkContract(stock), CUTOFFTIME, DAYSTOREQUESTYtd, Types.DurationUnit.DAY,
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
                //System.out.println(" stock is " + stock);
                //idStockMap.put(uniqueID.incrementAndGet(), stock);
                apcon.reqHistoricalDataUSHK(new WtdHandler(), uniqueID.incrementAndGet(),
                        generateUSStkContract(stock), CUTOFFTIME, DAYSTOREQUESTWtd, Types.DurationUnit.DAY,
                        Types.BarSize._5_mins, Types.WhatToShow.TRADES, true);
            } catch (InterruptedException ex) {
                Logger.getLogger(HistHKStocks.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    private static String getDataCutoff() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));
    }

    private void connectToTWS() {
        System.out.println(" trying to connectAndReqPos");
        try {
            apcon.connect("127.0.0.1", 7496, 101, "");
        } catch (Exception ex) {
            System.out.println(" connectAndReqPos to tws failed ");
        }
    }

    @SuppressWarnings("unused")
    public static void computeAll() {
        System.out.println(" computing starts ");
        USALLYtd.keySet().forEach(k -> {
            NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(USALLYtd.get(k),
                    LocalDate.of(2016, Month.DECEMBER, 31));
            double mean = SharpeUtility.getMean(ret);
            double sd = SharpeUtility.getSD(ret);
            double sr = SharpeUtility.getSharpe(ret, 252);
            //double perc = SharpeUtility.getPercentile(USALLYtd.get(k));
            System.out.println(Utility.getStrTabbed(" stock mean sd sr ", k, mean, sd, sr));
        });
    }

    private static void computeYtd(String stock) {
        NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(USALLYtd.get(stock),
                LocalDate.of(2016, Month.DECEMBER, 31));
        double mean = SharpeUtility.getMean(ret);
        double sd = SharpeUtility.getSD(ret);
        double sr = SharpeUtility.getSharpe(ret, 252);
        double perc = SharpeUtility.getPercentile(USALLYtd.get(stock));
        USResultMapYtd.get(stock).fillResult(mean, sd, sr, perc);
        System.out.println(Utility.getStrTabbed(" stock mean sd sr perc", stock, mean, sd, sr, perc));
    }

    private static void computeWtd(String stock) {
        System.out.println(" computing Wtd starts for stock " + stock);
        NavigableMap<LocalDateTime, Double> ret = SharpeUtility.getReturnSeries(USALLWtd.get(stock),
                LocalDateTime.of(MONDAY_OF_WEEK.minusDays(1), LocalTime.MIN));
        double mean = SharpeUtility.getMean(ret);
        double sd = SharpeUtility.getSD(ret) * Math.sqrt(78); //get day vol
        double sr = SharpeUtility.getSharpe(ret, 78); //get day SR
        double perc = SharpeUtility.getPercentile(USALLWtd.get(stock));
        USResultMapWtd.get(stock).fillResult(mean, sd, sr, perc);
        System.out.println(Utility.getStrTabbed(" wtd stock mean sd sr perc size firstEntry last Entry",
                stock, mean, sd, sr, perc, ret.size(), ret.firstEntry(), ret.lastEntry()));
    }


    public static void main(String[] args) {
        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1900, 1000));
        HistUSStocks us = new HistUSStocks();
        jf.add(us);
        jf.setLayout(new FlowLayout());
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jf.setVisible(true);
        CompletableFuture.runAsync(us::connectToTWS);
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
            LocalDateTime ldt = LocalDateTime.of(ld, lt);

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

    private LocalDate convertStringToDate(String date) {
        return LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private class USResult {

        double mean;
        double sd;
        double sr;
        double perc;

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
                    return "nY";
                case 6:
                    return "meanW";
                case 7:
                    return "sdW";
                case 8:
                    return "sharpeW";
                case 9:
                    return "percW";
                case 10:
                    return "nW";
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
