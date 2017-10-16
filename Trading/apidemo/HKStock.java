package apidemo;

import graph.GraphBar;
import graph.GraphSize;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.*;
import java.util.LinkedList;
import java.util.List;

public class HKStock extends JPanel {



    static GraphBar graph1 = new GraphBar();
    static GraphBar graph2 = new GraphBar();
    static GraphBar graph3 = new GraphBar();
    static GraphBar graph4 = new GraphBar();
    static GraphBar graph5 = new GraphBar();
    static GraphBar graph6 = new GraphBar();

    BarModel_HKStock m_model;
    static JPanel graphPanel;
    int modelRow;
    int indexRow;
    static JTable tab;

    static TableRowSorter<BarModel_HKStock> sorter;
    final File hkstockFile= new File(ChinaMain.GLOBALPATH+"hkMainList.txt");
    String line;
    public static List<String> symbolNamesHK = new LinkedList<>();

    public HKStock() {

        try(BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(hkstockFile)))){
            while((line=reader1.readLine())!=null) {
                //System.out.println(" hk line is " + line);
                symbolNamesHK.add(line);
            }
            System.out.println(" hk size " + symbolNamesHK.size());

        } catch(IOException e) {
            e.printStackTrace();

        }
        //System.out.println(symbolNamesHK);

        m_model = new BarModel_HKStock();
        graphPanel = new JPanel();
        graphPanel.setLayout(new GridLayout(6,1));


        JScrollPane gp1 = genNewScrollPane(graph1);
        JScrollPane gp2 = genNewScrollPane(graph2);
        JScrollPane gp3 = genNewScrollPane(graph3);
        JScrollPane gp4 = genNewScrollPane(graph4);
        JScrollPane gp5 = genNewScrollPane(graph5);
        JScrollPane gp6 = genNewScrollPane(graph6);



        graphPanel.add(gp1);
        graphPanel.add(gp2);
        graphPanel.add(gp3);
        graphPanel.add(gp4);
        graphPanel.add(gp5);
        graphPanel.add(gp6);

        JPanel controlPanel = new JPanel();

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(al->{
            SwingUtilities.invokeLater(()->{
                graphPanel.repaint();
            });
        });
        controlPanel.add(refreshButton);


        tab = new JTable(m_model){
            @Override
            public Component prepareRenderer(TableCellRenderer tableCellRenderer, int row, int col) {
                Component comp = super.prepareRenderer(tableCellRenderer, row, col);
                if(isCellSelected(row,col)){
                    modelRow = this.convertRowIndexToModel(row);
                    indexRow = row;
                    comp.setBackground(Color.green);
                } else {
                    comp.setBackground((row%2==0)?Color.lightGray:Color.white);
                }
                return comp;
            }
        };

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 900;
                return d;
            }
        };

        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.WEST);
        add(graphPanel, BorderLayout.CENTER);
        tab.setAutoCreateRowSorter(true);
        sorter = (TableRowSorter<BarModel_HKStock>)tab.getRowSorter();

    }

    static JScrollPane genNewScrollPane(JComponent g) {
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
            return 10;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "T";
                case 1:
                    return "Chn";
                case 2:
                    return "Price";
                case 3:
                    return "Rtn";
                case 4:
                    return "Ytd Sharpe";
                case 5:
                    return "Wtd Sharpe";
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
                    return name;
                case 2:
                    if(HKData.hkPriceBar.containsKey(name) && HKData.hkPriceBar.get(name).size() > 0) {
                        System.out.println(" hk size " + HKData.hkPriceBar.get(name).size());
                        return HKData.hkPriceBar.get(name).lastEntry().getValue().getClose();
                    } else {
                        return 0;
                    }

                case 3:
                    return 0.0;
                case 4:
                    return 0.0;
                default:
                    return null;
            }
        }

        public Class getColumnClass(int col) {
            switch (col) {
                case 1:
                    return String.class;
                default:
                    return Double.class;
            }
        }

    }

    public static void main(String[] args) {

        JFrame jf = new JFrame();
        jf.setSize(1900,1500);

        HKStock hks = new HKStock();
        jf.add(hks);
        jf.setVisible(true);



    }

}
