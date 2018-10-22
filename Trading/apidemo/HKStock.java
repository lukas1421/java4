package apidemo;

import auxiliary.SimpleBar;
import client.Contract;
import client.Types;
import graph.GraphBarTemporal;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static apidemo.ChinaMain.GLOBAL_REQ_ID;
import static apidemo.ChinaMain.controller;
import static utility.Utility.ibContractToSymbol;
import static utility.Utility.pr;

public class HKStock extends JPanel {
    static volatile Map<String, Double> hkCurrPrice = new HashMap<>();
    static volatile Map<String, Double> hkVol = new HashMap<>();
    private static GraphBarTemporal graph1 = new GraphBarTemporal();

    static Map<String, AtomicBoolean> downloadStatus = new HashMap<>();

    //private static GraphBar graph2 = new GraphBar();
    //private static GraphBar graph3 = new GraphBar();
//    private static GraphBar graph4 = new GraphBar();
//    private static GraphBar graph5 = new GraphBar();
//    private static GraphBar graph6 = new GraphBar();

    BarModel_HKStock m_model;
    static JPanel graphPanel;
    private int modelRow;
    private int indexRow;
    static JTable tab;

    String line;
    private static List<String> symbolNamesHK = new LinkedList<>();
    //private static Map<String, String> haMap = new HashMap<>();
    public static Map<String, String> hkNameMap = new HashMap<>();

    public static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            hkData = new ConcurrentSkipListMap<>();

    private static Semaphore sem = new Semaphore(49);


    @SuppressWarnings("unchecked")
    HKStock() {
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "hkMainNames.txt"), "gbk"))) {
            String line;
            while ((line = reader1.readLine()) != null) {
                List<String> l = Arrays.asList(line.split("\t"));
                String hkSymbol = AutoTraderMain.hkTickerToSymbol(l.get(0));
                hkData.put(hkSymbol, new ConcurrentSkipListMap<>());
                symbolNamesHK.add(hkSymbol);
                hkNameMap.put(hkSymbol, l.get(1));
                downloadStatus.put(hkSymbol, new AtomicBoolean(false));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        m_model = new BarModel_HKStock();
        graphPanel = new JPanel();
        graphPanel.setLayout(new GridLayout(6, 1));

        String hkstock1 = "700";
        //graph1.setNavigableMap(hkstock1);
//        String hkstock2 = "2823";
//        graph2.fillInGraphHK(hkstock2);
//        String hkstock3 = "2822";
//        graph3.fillInGraphHK(hkstock3);
//        String hkstock4 = "3188";
//        graph4.fillInGraphHK(hkstock4);
//        String hkstock5 = "3147";
//        graph5.fillInGraphHK(hkstock5);
//        String hkstock6 = "1398";
//        graph6.fillInGraphHK(hkstock6);

        JScrollPane gp1 = genNewScrollPane(graph1);
//        JScrollPane gp2 = genNewScrollPane(graph2);
//        JScrollPane gp3 = genNewScrollPane(graph3);
//        JScrollPane gp4 = genNewScrollPane(graph4);
//        JScrollPane gp5 = genNewScrollPane(graph5);
//        JScrollPane gp6 = genNewScrollPane(graph6);

        graphPanel.add(gp1);
//        graphPanel.add(gp2);
//        graphPanel.add(gp3);
//        graphPanel.add(gp4);
//        graphPanel.add(gp5);
//        graphPanel.add(gp6);

        JPanel controlPanel = new JPanel();

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(al -> SwingUtilities.invokeLater(() -> {
            graphPanel.repaint();
            tab.repaint();
        }));

        JButton histButton = new JButton("Ytd Data");
        histButton.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                symbolNamesHK.forEach(s -> {
                    Contract hkCt = AutoTraderMain.symbolToHKStkContract(s);
                    if (GLOBAL_REQ_ID.get() % 50 == 0) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        sem.acquire(1);
                        //if (sem.availablePermits() > 0) {
                        pr(" requesting HK, sem#: ", s, sem.availablePermits());
                        controller().reqHistDayData(GLOBAL_REQ_ID.addAndGet(5),
                                hkCt, HKStock::handleHist, 365, Types.BarSize._1_week);
                        //}
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                });
            });
        });

        controlPanel.add(refreshButton);
        controlPanel.add(histButton);

        tab = new JTable(m_model) {
            @Override
            public Component prepareRenderer(TableCellRenderer tableCellRenderer, int row, int col) {
                Component comp = super.prepareRenderer(tableCellRenderer, row, col);
                if (isCellSelected(row, col)) {
                    modelRow = this.convertRowIndexToModel(row);
                    indexRow = row;


                    String selectedNameStock = symbolNamesHK.get(modelRow);

                    if (hkData.containsKey(selectedNameStock) && hkData.get(selectedNameStock).size() > 0) {
                        graph1.setNavigableMap(hkData.get(selectedNameStock));
                    }
                    refreshAll();

//                    if (hkData.containsKey(selectedNameStock)) {
//                        pr("hkstock", selectedNameStock, hkData.get(selectedNameStock));
//                    }

                    comp.setBackground(Color.green);
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
                    m_model.fireTableDataChanged();
                    tab.repaint();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 700;
                return d;
            }
        };

        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.WEST);
        add(graphPanel, BorderLayout.CENTER);
        tab.setAutoCreateRowSorter(true);
        TableRowSorter<BarModel_HKStock> sorter = (TableRowSorter<BarModel_HKStock>) tab.getRowSorter();

    }

    private static void handleHist(Contract c, String date, double open, double high, double low,
                                   double close, int volume) {
        if (!date.startsWith("finished")) {

            String symbol = utility.Utility.ibContractToSymbol(c);

            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            hkData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        } else {
            if (date.startsWith("finished")) {
                String symbol = ibContractToSymbol(c);
                downloadStatus.get(symbol).set(true);
                sem.release(1);
                pr(" sem released ", symbol, "first last",
                        hkData.get(symbol).firstEntry(), hkData.get(symbol).lastEntry(),
                        "#", sem.availablePermits());
            }
        }
    }

    private static JScrollPane genNewScrollPane(JComponent g) {
        return new JScrollPane(g) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                return d;
            }
        };
    }

    class BarModel_HKStock extends javax.swing.table.AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNamesHK.size();
        }

        @Override
        public int getColumnCount() {
            return 15;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "H";
                case 1:
                    return "Chn";
                case 2:
                    return "Downloaded";
                case 3:
                    return "first date";
                default:
                    return "";
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            String name = symbolNamesHK.get(rowIn);

            switch (col) {
                case 0:
                    return name;
                case 1:
                    return hkNameMap.get(name);
                case 2:
                    return downloadStatus.get(name).get();
                case 3:
                    if (hkData.get(name).size() > 0) {
                        return hkData.get(name).firstKey();
                    } else {
                        return LocalDate.MIN;
                    }


                default:
                    return null;
            }
        }

        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                case 2:
                    return Boolean.class;
                case 3:
                    return LocalDate.class;

                default:
                    return Double.class;
            }
        }

    }

    private static void refreshAll() {
        SwingUtilities.invokeLater(() -> {
            graphPanel.repaint();
            tab.repaint();
        });
    }


//    public static void main(String[] args) {
//        JFrame jf = new JFrame();
//        jf.setSize(1900, 1500);
//        HKStock hks = new HKStock();
//        jf.add(hks);
//        jf.setVisible(true);
//        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
//
//    }

}
