package apidemo;

import auxiliary.SimpleBar;

import static apidemo.ChinaData.tradeTime;
import static apidemo.ChinaStock.nameMap;
import static apidemo.ChinaStock.symbolNames;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

class ChinaPeakAnalysis extends JPanel {

    static ConcurrentHashMap<Integer, ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Long>>> saveMap = new ConcurrentHashMap<>();

    BarModel m_model;

    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> dayPeakMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalTime, Double>> pmPeakMap = new ConcurrentHashMap<>();

    ChinaPeakAnalysis() {

        symbolNames.forEach(name -> {
            dayPeakMap.put(name, new ConcurrentSkipListMap<>());
            pmPeakMap.put(name, new ConcurrentSkipListMap<>());
        });

        m_model = new BarModel();
        JTable tab = new JTable(m_model);
        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 1900;
                return d;
            }
        };

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        tab.setAutoCreateRowSorter(true);

        JPanel jp = new JPanel();

        //JButton btnLoad = new JButton("load");
        JButton btnRefresh = new JButton("Refresh");

        JButton btnCompute = new JButton("Compute");

        jp.add(Box.createHorizontalStrut(100));

        jp.add(btnRefresh);
        jp.add(btnCompute);

        add(jp, BorderLayout.NORTH);

        btnRefresh.addActionListener(l -> {
            SwingUtilities.invokeLater(() -> {
                m_model.fireTableDataChanged();
            });
        });
    }

    public static void analyse(String name) {

        TreeMap<LocalTime, Double> tm = new TreeMap(ChinaData.priceMapBar.get(name).entrySet().stream()
                .filter(e -> e.getValue().getClose() > Optional.ofNullable(ChinaData.priceMapBar.get(name)
                        .get(e.getKey().minusMinutes(1L))).map(SimpleBar::getClose).orElse(Double.MAX_VALUE)
                && e.getValue().getClose() >= Optional.ofNullable(ChinaData.priceMapBar.get(name).get(e.getKey().plusMinutes(1L))).map(SimpleBar::getClose)
                        .orElse(Double.MAX_VALUE))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        Iterator<Map.Entry<LocalTime, Double>> it = tm.entrySet().iterator();
        Entry<LocalTime, Double> en;
        LocalTime t;
        double val;
        double lastV;

        while (it.hasNext()) {
            en = it.next();
            t = en.getKey();
            val = en.getValue();
            lastV = val;
        }

    }

    class BarModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return tradeTime.size() + 2;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "T";
                case 1:
                    return "name";
                default:
                    return tradeTime.get(col - 2).toString();
            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                default:
                    return Long.class;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            String name = symbolNames.get(rowIn);

            switch (col) {
                case 0:
                    return name;
                case 1:
                    return nameMap.get(name);
                default:
                    if (ChinaData.sizeTotalMap.containsKey(name)) {
                        if (ChinaData.sizeTotalMap.get(name).containsKey(tradeTime.get(col - 2))) {
                            return Math.round(ChinaData.sizeTotalMap.get(name).get(tradeTime.get(col - 2)));
                        }
                    }
                    return 0L;
            }
        }
    }
}
