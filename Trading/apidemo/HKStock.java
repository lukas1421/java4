package apidemo;

import javax.swing.JPanel;

public class HKStock extends JPanel {

    BarModel_HKStock m_model;

    static GraphBar graph1 = new GraphBar();
    static GraphBar graph2 = new GraphBar();
    static GraphBar graph3 = new GraphBar();
    static GraphBar graph4 = new GraphBar();
    static GraphBar graph5 = new GraphBar();
    static GraphSize graph6 = new GraphSize();

    public HKStock() {

        m_model = new BarModel_HKStock();

        JPanel controlPanel = new JPanel();

        //test

        //test


    }

    class BarModel_HKStock extends javax.swing.table.AbstractTableModel {

        @Override
        public int getRowCount() {
            return HKData.hkPriceBar.size();
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

                default:
                    return "";
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            String name = HKData.hkNames.get(rowIn);
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return name;
                case 2:

                default:
                    return null;
            }
        }

        public Class getColumnClass(int col) {
            switch (col) {
                case 1:
                    return String.class;
                default:
                    return String.class;
            }
        }

    }

}
