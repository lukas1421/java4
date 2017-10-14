package historical;

import apidemo.*;
import auxiliary.SimpleBar;
import client.Contract;
import client.Types;
import controller.ApiConnection;
import controller.ApiController;
import handler.HistoricalHandler;
import utility.SharpeUtility;
import utility.Utility;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

public class HistHKStocks extends JPanel implements HistoricalHandler {

    static final String CUTOFFTIME = getDataCutoff();
    static final int DAYSTOREQUEST = (int) ChronoUnit.DAYS.between(
            LocalDate.of(LocalDate.now().getYear() - 1, Month.DECEMBER, 31), LocalDate.now()) + 1;

    //public static volatile long totalBeingProcessed = 0;
    static volatile long requestCounter = 0L;
    public static volatile long stocksProcessed = 0;

    public static volatile Semaphore sm = new Semaphore(50);

    //ScheduledExecutorService es = Executors.newSingleThreadScheduledExecutor();
    static ApiController apcon = new ApiController(new ApiController.IConnectionHandler.DefaultConnectionHandler(),
            new ApiConnection.ILogger.DefaultLogger(),
            new ApiConnection.ILogger.DefaultLogger());

    //public static volatile Contract ctHK = new Contract();
    public static volatile Map<String, NavigableMap<LocalDate, SimpleBar>> HKALL = new HashMap<>();
    private static volatile Map<String, HKResult> HKResultMap = new HashMap<>();

    public static List<String> hkNameList = new LinkedList<>();
    public static File output = new File(ChinaMain.GLOBALPATH + "hkTestData.txt");
    static volatile AtomicInteger uniqueID = new AtomicInteger(70000);

    public static volatile Map<Integer, String> idStockMap = new HashMap<>();

    static BarModel_HK m_model;
    static JTable tab;
    int modelRow;
    int indexRow;
    static TableRowSorter<BarModel_HK> sorter;

    public static JLabel totalStocksLabel = new JLabel("Total");

    public HistHKStocks() {
        String line;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(ChinaMain.GLOBALPATH + "hkMainList.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                HKALL.put(al1.get(0), new TreeMap<>());
                HKResultMap.put(al1.get(0), new HKResult());
            }
        } catch (IOException ex) {
        }
        hkNameList = HKALL.keySet().stream().collect(Collectors.toList());
        System.out.println(" hk name list " + hkNameList);

        m_model = new BarModel_HK();

        tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                try {
                    Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                    if (isCellSelected(Index_row, Index_col)) {
                        modelRow = this.convertRowIndexToModel(Index_row);
                        comp.setBackground(Color.GREEN);
                    } else {
                        comp.setBackground((Index_row % 2 == 0) ? Color.lightGray : Color.white);
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

        JButton getHistoricalButton = new JButton("Historical");
        getHistoricalButton.addActionListener(al -> {
            requestAllHKStocks();
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
        controlPanel.add(getHistoricalButton);
        totalStocksLabel.setFont(totalStocksLabel.getFont().deriveFont(30L));
        controlPanel.add(totalStocksLabel);

        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        tab.setAutoCreateRowSorter(true);
        sorter = (TableRowSorter<BarModel_HK>) tab.getRowSorter();

    }

    void requestAllHKStocks() {
        stocksProcessed = 0;
        MorningTask.clearFile(HistHKStocks.output);
        HKALL.keySet().forEach(k -> request1Stock(k));
    }

    Contract generateHKContract(String stock) {
        Contract ct = new Contract();
        ct.symbol(stock);
        ct.exchange("SEHK");
        ct.currency("HKD");
        ct.secType(Types.SecType.STK);
        return ct;
    }

    public static void refreshAll() {
        totalStocksLabel.setText(Long.toString(stocksProcessed) + "/" + Long.toString(HKALL.size()));
        System.out.println(" refreshing all ");
        m_model.fireTableDataChanged();
    }

    void request1Stock(String stock) {

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
                idStockMap.put(uniqueID.incrementAndGet(), stock);
                apcon.reqHistoricalDataUSHK(this, uniqueID.get(), generateHKContract(stock), CUTOFFTIME,
                        DAYSTOREQUEST, Types.DurationUnit.DAY,
                        Types.BarSize._1_day, Types.WhatToShow.TRADES, true);
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
        HKALL.keySet().forEach(k -> {
            NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(HKALL.get(k),
                    t -> t.isAfter(LocalDate.of(2016, Month.DECEMBER, 31)));
            double mean = SharpeUtility.getMean(ret);
            double sd = SharpeUtility.getSD(ret);
            double sr = SharpeUtility.getSharpe(ret);
            double perc = SharpeUtility.getPercentile(HKALL.get(k));

            System.out.println(Utility.getStrTabbed(" stock mean sd sr perc ", k, mean, sd, sr, perc));
        });
    }

    public static void compute(String stock) {
        System.out.println(" computing starts for stock " + stock);
        NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(HKALL.get(stock),
                t -> t.isAfter(LocalDate.of(2016, Month.DECEMBER, 31)));
        double mean = SharpeUtility.getMean(ret);
        double sd = SharpeUtility.getSD(ret);
        double sr = SharpeUtility.getSharpe(ret);
        double perc = SharpeUtility.getPercentile(HKALL.get(stock));
        HKResultMap.get(stock).fillResult(mean, sd, sr, perc);
        System.out.println(Utility.getStrTabbed(" stock mean sd sr perc", stock, mean, sd, sr, perc));

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

    @Override
    public void handleHist(String name, String date, double open, double high, double low, double close) {
        HKALL.get(name).put(convertStringToDate(date), new SimpleBar(open, high, low, close));
    }

    @Override
    public void actionUponFinish(String name) {
        stocksProcessed++;
        sm.release(1);
        System.out.println(" current permit after done " + HistHKStocks.sm.availablePermits());
        compute(name);
        refreshAll();
    }

    public LocalDate convertStringToDate(String date) {
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
            return HKALL.size();
        }

        @Override
        public int getColumnCount() {
            return 6;
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
                    return HKResultMap.get(name).getMean();
                case 2:
                    return HKResultMap.get(name).getSd();
                case 3:
                    return HKResultMap.get(name).getSr();
                case 4:
                    return HKResultMap.get(name).getPerc();
                case 5:
                    return HKALL.get(name).size();
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
