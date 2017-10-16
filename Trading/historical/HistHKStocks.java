package historical;

import apidemo.ChinaMain;
import apidemo.MorningTask;
import auxiliary.SimpleBar;
import client.Contract;
import client.Types;
import controller.ApiConnection;
import controller.ApiController;
import handler.HistoricalHandler;
import utility.SharpeUtility;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HistHKStocks extends JPanel {

    static final String CUTOFFTIME = getDataCutoff();
    static final int DAYSTOREQUEST = (int) ChronoUnit.DAYS.between(
            LocalDate.of(LocalDate.now().getYear() - 1, Month.DECEMBER, 31), LocalDate.now()) + 1;

    //public static volatile long totalBeingProcessed = 0;
    static volatile long requestCounter = 0L;
    public static volatile long stocksProcessedYtd = 0;
    public static volatile long stocksProcessedWtd = 0;

    public static volatile Semaphore sm = new Semaphore(50);

    public final static File testOutput = new File(ChinaMain.GLOBALPATH+"hkTestData.txt");

    //ScheduledExecutorService es = Executors.newSingleThreadScheduledExecutor();
    static ApiController apcon = new ApiController(new ApiController.IConnectionHandler.DefaultConnectionHandler(),
            new ApiConnection.ILogger.DefaultLogger(),
            new ApiConnection.ILogger.DefaultLogger());

    //public static volatile Contract ctHK = new Contract();
    public static volatile Map<String, NavigableMap<LocalDate, SimpleBar>> hkYtdAll = new HashMap<>();
    public static volatile Map<String, NavigableMap<LocalDateTime, SimpleBar>> hkWtdAll = new HashMap<>();
    private static volatile Map<String, HKResult> HKResultMapYtd = new HashMap<>();
    private static volatile Map<String, HKResult> HKResultMapWtd = new HashMap<>();

    public static List<String> hkNameList = new LinkedList<>();
    public static File outputYtd = new File(ChinaMain.GLOBALPATH + "hkSharpeYtd.txt");
    public static File outputWtd = new File(ChinaMain.GLOBALPATH + "hkSharpeWtd.txt");

    static volatile AtomicInteger uniqueID = new AtomicInteger(70000);

    //public static volatile Map<Integer, String> idStockMap = new HashMap<>();

    static BarModel_HK m_model;
    static JTable tab;
    int modelRow;
    int indexRow;
    static TableRowSorter<BarModel_HK> sorter;

    public static JLabel totalStocksLabelYtd = new JLabel("Total Ytd");
    public static JLabel totalStocksLabelWtd = new JLabel("Total Wtd");

    public HistHKStocks() {
        String line;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(ChinaMain.GLOBALPATH + "hkMainList.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                hkYtdAll.put(al1.get(0), new TreeMap<>());
                hkWtdAll.put(al1.get(0),new TreeMap<>());
                HKResultMapYtd.put(al1.get(0), new HKResult());
                HKResultMapWtd.put(al1.get(0), new HKResult());
            }
        } catch (IOException ex) {
        }
        hkNameList = hkYtdAll.keySet().stream().collect(Collectors.toList());
        System.out.println(" hk name list " + hkNameList);

        m_model = new BarModel_HK();

        tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int indexRow, int indexCol) {
                try {
                    Component comp = super.prepareRenderer(renderer, indexRow, indexCol);
                    if (isCellSelected(indexRow, indexCol)) {
                        modelRow = this.convertRowIndexToModel(indexRow);
                        comp.setBackground(Color.GREEN);
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
                d.height = 600;
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

        getYtdButton.addActionListener(al -> {
            requestAllHKStocksYtd();
        });

        getWtdButton.addActionListener(al->{
            requestAllHKStocksWtd();
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

        totalStocksLabelYtd.setFont(totalStocksLabelYtd.getFont().deriveFont(30L));
        controlPanel.add(totalStocksLabelYtd);

        totalStocksLabelWtd.setFont(totalStocksLabelWtd.getFont().deriveFont(30L));
        controlPanel.add(totalStocksLabelWtd);

        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        tab.setAutoCreateRowSorter(true);
        sorter = (TableRowSorter<BarModel_HK>) tab.getRowSorter();

    }

    void requestAllHKStocksYtd() {
        stocksProcessedYtd = 0;
        MorningTask.clearFile(HistHKStocks.outputYtd);
        hkYtdAll.keySet().forEach(k -> request1StockYtd(k));
    }

    void requestAllHKStocksWtd() {
        stocksProcessedWtd = 0;
        MorningTask.clearFile(HistHKStocks.outputWtd);
        hkWtdAll.keySet().forEach(k -> request1StockWtd(k));
    }


    Contract generateHKContract(String stock) {
        Contract ct = new Contract();
        ct.symbol(stock);
        ct.exchange("SEHK");
        ct.currency("HKD");
        ct.secType(Types.SecType.STK);
        return ct;
    }

    public static void refreshYtd() {
        totalStocksLabelYtd.setText(Long.toString(stocksProcessedYtd) + "/" + Long.toString(hkYtdAll.size()));
        System.out.println(" refreshing YTD ");
        m_model.fireTableDataChanged();
    }

    public static void refreshWtd() {
        totalStocksLabelWtd.setText(Long.toString(stocksProcessedWtd) + "/" + Long.toString(hkWtdAll.size()));
        System.out.println(" refreshing WTD ");
        m_model.fireTableDataChanged();
    }

    void request1StockYtd(String stock) {
        CompletableFuture.runAsync(() -> {
            System.out.println(" request stock in completefuture " + Thread.currentThread().getName());
            System.out.println(" available " + sm.availablePermits());
            System.out.println(" queue length is " + sm.getQueueLength());

            try {
                System.out.println(" permits before " + sm.availablePermits());
                sm.acquire();
                //System.out.println(" unique id " + uniqueID.get() + " stock " + idStockMap.get(uniqueID.get()));
                //System.out.println(" cut off time " + cutoffTime + " days to request " + daysToRequest);
                System.out.println(" stock is " + stock);
                //idStockMap.put(uniqueID.incrementAndGet(), stock);
                apcon.reqHistoricalDataUSHK(new YtdDataHandler(), uniqueID.get(), generateHKContract(stock), CUTOFFTIME,
                        DAYSTOREQUEST, Types.DurationUnit.DAY,
                        Types.BarSize._1_day, Types.WhatToShow.TRADES, true);
            } catch (InterruptedException ex) {
                Logger.getLogger(HistHKStocks.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    void request1StockWtd(String stock) {
        CompletableFuture.runAsync(() -> {
            System.out.println(" request stock in completefuture " + Thread.currentThread().getName());
            System.out.println(" available " + sm.availablePermits());
            System.out.println(" queue length is " + sm.getQueueLength());

            try {
                System.out.println(" permits before " + sm.availablePermits());
                sm.acquire();
                //System.out.println(" unique id " + uniqueID.get() + " stock " + idStockMap.get(uniqueID.get()));
                //System.out.println(" cut off time " + cutoffTime + " days to request " + daysToRequest);
                System.out.println(" stock is " + stock);
                //idStockMap.put(uniqueID.incrementAndGet(), stock);
                apcon.reqHistoricalDataUSHK(new WtdDataHandler()
                        , uniqueID.get(), generateHKContract(stock), CUTOFFTIME,
                        7, Types.DurationUnit.DAY,
                        Types.BarSize._1_min, Types.WhatToShow.TRADES, true);
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
        }
        //apcon.client().reqIds(-1);
    }

    static ApiController getAPICon() {
        return apcon;
    }

    public static void computeAll() {
        System.out.println(" computing starts ");
        hkYtdAll.keySet().forEach(k -> {
            NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(hkYtdAll.get(k),
                    t -> t.isAfter(LocalDate.of(2016, Month.DECEMBER, 31)));
            double mean = SharpeUtility.getMean(ret);
            double sd = SharpeUtility.getSD(ret);
            double sr = SharpeUtility.getSharpe(ret);
            double perc = SharpeUtility.getPercentile(hkYtdAll.get(k));

            System.out.println(Utility.getStrTabbed(" stock mean sd sr perc ", k, mean, sd, sr, perc));
        });
    }

    public static void computeYtd(String stock) {
        System.out.println(" computing Ytd starts for stock " + stock);
        NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(hkYtdAll.get(stock),
                t -> t.isAfter(LocalDate.of(2016, Month.DECEMBER, 31)));
        double mean = SharpeUtility.getMean(ret);
        double sd = SharpeUtility.getSD(ret);
        double sr = SharpeUtility.getSharpe(ret);
        double perc = SharpeUtility.getPercentile(hkYtdAll.get(stock));
        HKResultMapYtd.get(stock).fillResult(mean, sd, sr, perc);
        System.out.println(Utility.getStrTabbed(" stock mean sd sr perc", stock, mean, sd, sr, perc));
    }

    public static void computeWtd(String stock) {
        System.out.println(" computing Wtd starts for stock " + stock);
        NavigableMap<LocalDateTime, Double> ret = SharpeUtility.getReturnSeries(hkWtdAll.get(stock),
                t -> t.isAfter(getMondayOfWeek(LocalDateTime.now())));
        double mean = SharpeUtility.getMean(ret);
        double sd = SharpeUtility.getSD(ret);
        double sr = SharpeUtility.getSharpe(ret);
        double perc = SharpeUtility.getPercentile(hkYtdAll.get(stock));
        HKResultMapWtd.get(stock).fillResult(mean, sd, sr, perc);
        System.out.println(Utility.getStrTabbed(" wtd stock mean sd sr perc", stock, mean, sd, sr, perc));
    }

    static class YtdDataHandler implements HistoricalHandler{
        @Override
        public void handleHist(String name, String date, double open, double high, double low, double close) {
            hkYtdAll.get(name).put(convertStringToDate(date), new SimpleBar(open, high, low, close));
        }

        @Override
        public void actionUponFinish(String name) {
            stocksProcessedYtd++;
            sm.release(1);
            System.out.println(" current permit after done " + HistHKStocks.sm.availablePermits());
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
            LocalDateTime ldt = LocalDateTime.of(ld,lt);

            LocalDate monOfWeek = getMondayOfWeek(LocalDateTime.now()).toLocalDate();

            if (ld.isEqual(monOfWeek) || ld.isAfter(monOfWeek)) {
                //System.out.println(" CORRECT name ld lt open " + name + " " + ld + " " + lt + " " + open);
                hkWtdAll.get(name).put(ldt, new SimpleBar(open, high, low, close));
                if(name.equals("700")) {
                    System.out.println(" outputting tencent");
                    MorningTask.simpleWriteToFile(Utility.getStrTabbed(lt, open, high, low, close), true,
                            testOutput);
                }
            }
        }

        @Override
        public void actionUponFinish(String name) {
            stocksProcessedWtd++;
            sm.release(1);
            System.out.println(" current permit after done " + HistHKStocks.sm.availablePermits());
            computeWtd(name);
            refreshWtd();

        }
    }

    static LocalDateTime getMondayOfWeek(LocalDateTime ld) {
        LocalDateTime res = ld;
        while(!res.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
            res = res.minusDays(1);
        }
        return res;
    }



    public static void main(String[] args) {
        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1000, 1000));

        HistHKStocks hk = new HistHKStocks();
        jf.add(hk);
        jf.setLayout(new FlowLayout());
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
        CompletableFuture.runAsync(() -> {
            hk.connectToTWS(7496);
        });
    }



    public static LocalDate convertStringToDate(String date) {
        LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
        return ld;
    }

    private class HKResult {

        double mean;
        double sd;
        double sr;
        double perc;

        HKResult(double m, double s, double r, double p) {
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

        HKResult() {
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
                    return "mean";
                case 2:
                    return "sd";
                case 3:
                    return "Sharpe";
                case 4:
                    return "Perc";
                case 5:
                    return "Days";
                case 6:
                    return "wtd Sharpe";
                case 7:
                    return "Wtd perc";
                case 8:
                    return "Wtd days";
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
                    return HKResultMapWtd.get(name).getSr();
                case 7:
                    return HKResultMapWtd.get(name).getPerc();
                case 8:
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
