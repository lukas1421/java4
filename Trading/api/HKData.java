package api;

import auxiliary.SimpleBar;
import client.Contract;
import client.TickType;
import handler.HistoricalHandler;
import handler.LiveHandler;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import static utility.Utility.ibContractToSymbol;

public class HKData extends JPanel implements LiveHandler, HistoricalHandler {

    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, SimpleBar>> hkPriceBar
            = new ConcurrentHashMap<>();

    static volatile ConcurrentHashMap<String, Double> hkPreviousCloseMap = new ConcurrentHashMap<>();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> hkVolMap
            = new ConcurrentHashMap<>();

    public static volatile Semaphore historyDataSem = new Semaphore(50);

    public static volatile ScheduledExecutorService es = Executors.newScheduledThreadPool(10);

    private static List<LocalTime> hkTradingTime = new LinkedList<>();
    private static List<String> hkNames = new LinkedList<>();
    static BarModel_HKData m_model;
    JTable tab;
    private File testOutput = new File(TradingConstants.GLOBALPATH + "hkTestData.txt");

    HKData() {
        Utility.clearFile(testOutput);

        for (LocalTime t = LocalTime.of(9, 19); t.isBefore(LocalTime.of(16, 1)); t = t.plusMinutes(1)) {
            if (!(t.isAfter(LocalTime.of(11, 59)) && t.isBefore(LocalTime.of(13, 0)))) {
                hkTradingTime.add(t);
            }
        }

        String line;
        try (BufferedReader reader1 = new BufferedReader(
                new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + "hkMainList.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                hkPriceBar.put(al1.get(0), new ConcurrentSkipListMap<>());
                hkNames.add(al1.get(0));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        m_model = new BarModel_HKData();
        tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {

                Component comp = super.prepareRenderer(renderer, row, col);

                if (isCellSelected(row, col)) {
                    comp.setBackground(Color.GREEN);
                } else {
                    comp.setBackground((row % 2 == 0) ? Color.lightGray : Color.white);
                }
                return comp;
            }
        };

        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    SwingUtilities.invokeLater(() -> m_model.fireTableDataChanged());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 900;
                return d;
            }
        };
        JPanel controlPanel = new JPanel();
        JButton refreshButton = new JButton("Refresh");
        JButton histButton = new JButton("Today Data");

        refreshButton.addActionListener(al -> this.repaint());

//        histButton.addActionListener(al -> {
//            System.out.println(" requesting hk today data ");
//            ChinaMain.controller().reqHKTodayData();
//        });

        controlPanel.add(refreshButton);
        controlPanel.add(histButton);
        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        tab.setAutoCreateRowSorter(true);
    }

    @Override
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime ldt) {
        String symbol = ibContractToSymbol(ct);
        LocalTime t = ldt.toLocalTime();
        if (tt == TickType.LAST) {
            HKStock.hkCurrPrice.put(symbol, price);

            if (hkPriceBar.containsKey(symbol)) {
                if (hkPriceBar.get(symbol).containsKey(t)) {
                    hkPriceBar.get(symbol).get(t).add(price);
                } else {
                    hkPriceBar.get(symbol).put(t, new SimpleBar(price));
                }
            }
        } else if (tt == TickType.CLOSE) {
            hkPreviousCloseMap.put(symbol, price);
        }
    }

    @Override
    public void handleVol(TickType tt, String name, double vol, LocalDateTime ldt) {
        if (tt == TickType.VOLUME) {
            LocalTime t = ldt.toLocalTime();
            HKStock.hkVol.put(name, vol);
            if (hkVolMap.containsKey(name)) {
                hkVolMap.get(name).put(t, vol);
            }
        }
    }

    @Override
    public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {

    }

    @Override
    public void handleHist(String name, String date, double open, double high, double low, double close) {
        Date dt = new Date(Long.parseLong(date) * 1000);
        Calendar cal = Calendar.getInstance();
        cal.setTime(dt);
        LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
        LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

        if (ld.equals(LocalDate.now())) {
            //System.out.println(" CORRECT name ld ltof open " + name + " " + ld + " " + ltof + " " + open);
            hkPriceBar.get(name).put(lt, new SimpleBar(open, high, low, close));
            if (name.equals("700")) {
                System.out.println(" outputting tencent");
                Utility.simpleWriteToFile(Utility.getStrTabbed(lt, open, high, low, close), true,
                        testOutput);
            }

        }
    }

    @Override
    public void actionUponFinish(String name) {
        historyDataSem.release();
    }

    private class BarModel_HKData extends javax.swing.table.AbstractTableModel {

        @Override
        public int getRowCount() {
            return hkPriceBar.size();
        }

        @Override
        public int getColumnCount() {
            return hkTradingTime.size() + 2;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "T";
                case 1:
                    return "chn";
                default:
                    return hkTradingTime.get(col - 2).truncatedTo(ChronoUnit.MINUTES).toString();
            }
        }

        @Override
        public Class getColumnClass(int col) {
            //noinspection Duplicates
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

            String name = hkNames.get(rowIn);
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return name;
                default:
                    LocalTime t = hkTradingTime.get(col - 2);
                    return hkPriceBar.get(name).containsKey(t) ? hkPriceBar.get(name).get(t).getClose() : 0.0;
            }
        }
    }

    public static void main(String[] args) {
        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1000, 1000));

        HKData hd = new HKData();
        jf.add(hd);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

    }
}
