package apidemo;

import util.HtmlButton;
import util.NewTabbedPanel;
import util.TCombo;
import util.VerticalPanel;
import client.Types;
import controller.ApiController.IHistoricalDataHandler;
import controller.Bar;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import static java.util.stream.Collectors.toList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

public final class HistData extends JPanel implements IHistoricalDataHandler {

    BarModel m_model = new BarModel();
    ArrayList<Bar> m_rows = new ArrayList<>();
    private ConcurrentHashMap<Integer, TreeMap<LocalTime, Double>> map1h = new ConcurrentHashMap<>();
    //private HashMap<Integer,TreeMap<LocalTime,Double>> map1hC = new HashMap<Integer,TreeMap<LocalTime,Double>>();
    //private TreeMap<LocalTime,Double> map2h = new TreeMap<LocalTime,Double>();
    //private ReadWriteLock rwlock = new ReentrantReadWriteLock();
    private static final Calendar CAL = Calendar.getInstance();
    ArrayList<Integer> symbolNames = new ArrayList<>();
    ArrayList<LocalTime> tradeTime = new ArrayList<>();

    private final NewTabbedPanel refreshPanel = new NewTabbedPanel();

    public ConcurrentHashMap<Integer, TreeMap<LocalTime, Double>> getMap() {
        return map1h;
    }
    //tradeTime.add(0);

    //String[] arr = new String[331];                    
    //final boolean m_historical;
    //final apidemo.Chart m_chart = new apidemo.Chart( m_rows);
    //generic column names to be intialized
    public void init() {
        LocalTime lt = LocalTime.of(9, 29);
        int i = 1;
        while (lt.getHour() <= 15) {
            if (lt.getHour() == 12 && lt.getMinute() == 0) {
                lt = LocalTime.of(13, 0);
            }
            //System.out.print(lt.toString() + " ");       
            tradeTime.add(lt);
            i = i + 1;
            lt = lt.plusMinutes(1);
        }
    }

    public void init2() throws IOException {

        TreeMap<LocalTime, Double> tempMap = new TreeMap<>();
        LocalTime lt = LocalTime.of(9, 29);
        int i = 1;

        while (lt.getHour() <= 15) {
            if (lt.getHour() == 12 && lt.getMinute() == 0) {
                lt = LocalTime.of(13, 0);
            }
            //map2h.put(lt,0.0);
            //System.out.print(lt.toString() + " ");
            //arr[i]=lt.toString();
            //  System.out.println(arr[i]);
            tempMap.put(lt, 0.0);
            tradeTime.add(lt);
            i = i + 1;
            lt = lt.plusMinutes(1);
        }

        //System.out.println("reading file started");
        List<Integer> numbers;
        // HashMap<Integer,HashMap<Double,Double>> map1 = new HashMap<Integer,HashMap<Double,Double>>();
        // HashMap<Double,Double> map2 = new HashMap<Double,Double>();

        //m2      
        numbers = Files.lines(Paths.get(ChinaMain.GLOBALPATH + "Table2.txt"))
                .map(line -> line.split("\\s+"))
                .flatMap(Arrays::stream)
                .map(Integer::valueOf)
                .distinct()
                .collect(toList());

        //only read map2h after map2h initialization has been done.
        //rwlock.readLock().lock();
        // map1 cannot be initialized like this.
        Iterator it = numbers.iterator();

//             while (it.hasNext()) {
//              int sym = (int) it.next();
//             }
        numbers.forEach((value) -> {
            map1h.put(value, new TreeMap<>());
        });
        numbers.forEach((value) -> {
            symbolNames.add(value);
        });
        //  map2h.forEach((key,value)-> {System.out.print(key + "\t" );});

        //    rwlock.readLock().unlock();
    }

    HistData() {
        //m_historical = historical;
        init();
        // init2();

        //refreshPanel.addTab( "Top Market Data", new HistPanel() );
        JTable tab = new JTable(m_model);
        JScrollPane scroll = new JScrollPane(tab) {

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 1900;
                return d;
            }
        };

        //JScrollPane chartScroll = new JScrollPane( m_chart);
        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        //add( chartScroll, BorderLayout.CENTER);

    }

    /**
     * Called when the tab is first visited.
     */
    public void activated() {
    }

    /**
     * Called when the tab is closed by clicking the X.
     */
    public void closed() {
    }

    // this one has bugs.
    @Override
    public void historicalData(Bar bar, boolean hasGaps) {

        int symInt = bar.symbol();
        int hr;

        if (map1h.containsKey(symInt)) {

            CAL.setTime(new Date(bar.time() * 1000));

            if (CAL.get(Calendar.HOUR) > 21) {
                hr = CAL.get(Calendar.HOUR) - 12;
            } else if (CAL.get(Calendar.HOUR) <= 4) {
                hr = CAL.get(Calendar.HOUR) + 12;
            } else {
                hr = CAL.get(Calendar.HOUR);
            }

            LocalTime lt = LocalTime.of(hr, CAL.get(Calendar.MINUTE));

            System.out.println(" current value for " + symInt + " " + lt + " " + map1h.get(symInt).get(lt));

            map1h.get(symInt).put(lt, bar.close());
        }
    }

    @Override
    public void historicalDataEnd() {
        m_model.fireTableDataChanged();
    }

    private void fire() {
        SwingUtilities.invokeLater(() -> {
            m_model.fireTableDataChanged();
            System.out.println("table updated");
        });
    }

    private void fire(int i, int j) {
        SwingUtilities.invokeLater(() -> {
            //m_model.fireTableRowsInserted( m_rows.size() - 1, m_rows.size() - 1);
            //m_model.fireTableCellUpdated(i,j);
        });
    }

//                public void testPrint() {
//                    for (int i:map1hC.keySet()){
//                        System.out.println(" symbol is " + i);
//                        for (LocalTime t : map1hC.get(i).keySet())
//                        System.out.println(" time is " + t + " value is " +  map1hC.get(i).get(t));
//                    }
//                }
    class BarModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            //return m_rows.size();
            //return map1h.size();
            return symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return tradeTime.size();
        }

        @Override
        public String getColumnName(int col) {

            return tradeTime.get(col).toString();
        }

        @Override
        public Object getValueAt(int rowIn, int col) {

            if (col == 0) {
                return symbolNames.get(rowIn);
            }

            // System.out.println (" trying to get value at " + rowIn + " " + col);
            int sym;
            if (rowIn <= symbolNames.size()) {
                sym = symbolNames.get(rowIn);
                //System.out.println( " sym gotten is " + sym);
            } else {
                sym = 0;
            }

            if (map1h.containsKey(sym)) {
                if (map1h.get(sym).containsKey(tradeTime.get(col))) {
                    return map1h.get(sym).get(tradeTime.get(col));
                }
            }
            return 0;
        }
    }

    private final class HistPanel extends NewTabbedPanel.NewTabPanel {

        final TopModel m_model = new TopModel();
        //final JTable m_tab = new TopTable( m_model);
        final TCombo<Types.MktDataType> m_typeCombo = new TCombo<>(Types.MktDataType.values());

        HistPanel() {
            //m_typeCombo.removeItemAt( 0);

            //JScrollPane scroll = new JScrollPane( m_tab);
            HtmlButton reqType = new HtmlButton("Go") {
                @Override
                protected void actionPerformed() {
                    onReqType();
                }
            };

            VerticalPanel butPanel = new VerticalPanel();
            butPanel.add("Market data type", m_typeCombo, reqType);

            setLayout(new BorderLayout());
            //add( scroll);
            add(butPanel, BorderLayout.SOUTH);
        }

        /**
         * Called when the tab is first visited.
         */
        @Override
        public void activated() {
        }

        /**
         * Called when the tab is closed by clicking the X.
         */
        @Override
        public void closed() {
            m_model.desubscribe();
        }

        void onReqType() {
            //ApiDemo.INSTANCE.controller().reqMktDataType( m_typeCombo.getSelectedItem() );
            ChinaMain.INSTANCE.controller().reHistDataArray((IHistoricalDataHandler) this.getParent().getComponent(1));
        }

        class TopTable extends JTable {

            public TopTable(TopModel model) {
                super(model);
            }

            @Override
            public TableCellRenderer getCellRenderer(int rowIn, int column) {
                TableCellRenderer rend = super.getCellRenderer(rowIn, column);
                m_model.color(rend, rowIn, getForeground());
                return rend;
            }
        }
    }
}
