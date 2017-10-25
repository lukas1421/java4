package historical;

import apidemo.ChinaMain;
import auxiliary.SimpleBar;
import graph.GraphBarTemporal;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.*;
import java.nio.Buffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

public class HistChinaStocks extends JPanel {


    static JPanel graphPanel;
    static BarModel_China model;
    static JTable tab;

    GraphBarTemporal<LocalDate> graphYtd = new GraphBarTemporal<>();
    GraphBarTemporal<LocalDateTime> graphWtd = new GraphBarTemporal<>();
    File chinaInput = new File(ChinaMain.GLOBALPATH + "ChinaAll.txt");

    static List<String> stockList = new LinkedList<>();
    static Map<String, NavigableMap<LocalDate, SimpleBar>> chinaYtd = new HashMap<>();
    static Map<String, NavigableMap<LocalDateTime, SimpleBar>> chinaWtd = new HashMap<>();
    static Map<String, String> nameMap = new HashMap<>();

    static String ytdPath = ChinaMain.tdxPath;

    int modelRow;
    int indexRow;
    String selectedStock = "";


    public HistChinaStocks() {
        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(chinaInput),"GBK"))) {
            while ((line = reader.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));

                if(al1.get(0).startsWith("sh") || al1.get(0).startsWith("sz")) {
                    chinaYtd.put(al1.get(0), new TreeMap<>());
                    chinaWtd.put(al1.get(0), new TreeMap<>());
                    stockList.add(al1.get(0));
                    nameMap.put(al1.get(0), al1.get(1));
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
                        //System.out.println(" selected stock in monitor is " + selectedStock);
                        comp.setBackground(Color.GREEN);
                        //graphYtd.fillInGraphHKGen(selectedStock, hkYtdAll);
                        //graphWtd.fillInGraphHKGen(selectedStock, hkWtdAll);
                        graphPanel.repaint();
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


        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 1900;
                return d;
            }
        };

        graphPanel.setLayout(new GridLayout(2, 1));

        JScrollPane jp1 = new JScrollPane(graphYtd){
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



        graphPanel.add(jp1);
        graphPanel.add(jp2);

        JPanel controlPanel = new JPanel();
        JButton refreshButton = new JButton("Refresh");
        JButton ytdButton = new JButton("ytd");
        JButton wtdButton = new JButton("wtd");

        refreshButton.addActionListener(al->{
            refreshAll();
        });

        ytdButton.addActionListener(al->{
            computeYtd();
        });

        wtdButton.addActionListener(al->{

            computeWtd();

        });


        controlPanel.setLayout(new FlowLayout());
        controlPanel.add(refreshButton);
        controlPanel.add(ytdButton);
        controlPanel.add(wtdButton);

        this.setLayout(new BorderLayout());
        this.add(controlPanel, BorderLayout.NORTH);
        this.add(scroll, BorderLayout.CENTER);
        this.add(graphPanel, BorderLayout.SOUTH);


    }


    static void refreshAll() {

    }

    static void computeYtd() {

        for(String s:stockList) {
            System.out.println(" processing ytd for " + s);


        }

    }

    static void computeWtd() {

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


    private class BarModel_China extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return stockList.size();
        }

        @Override
        public int getColumnCount() {
            return 10;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "ticker";

                case 1:
                    return "chn";

                default:
                    return "";

            }
        }

        @Override
        public Object getValueAt(int row, int col) {
            String name = stockList.get(row);

            switch (col) {
                case 0:
                    return name;
                case 1:
                    return nameMap.getOrDefault(name,"");

                default:
                    return null;

            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch(col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                default:
                    return Double.class;
            }
        }
    }

}
