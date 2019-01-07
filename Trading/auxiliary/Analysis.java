package auxiliary;

import api.LiveData;
import api.TradingConstants;
import graph.Graph;

import javax.swing.*;
import javax.swing.RowFilter.ComparisonType;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import static java.lang.Math.log;

public class Analysis extends JPanel {
    //static class BarResultsPanel extends NewTabbedPanel.NewTabPanel implements ApiController.IHistoricalDataHandler, ApiController.IRealTimeBarHandler {

    static volatile BarModel m_model;
    Trait trait = new Trait();
    static HashMap<Integer, Trait> hm = new HashMap<Integer, Trait>();
    static volatile ConcurrentHashMap<Integer, String> nameMapCopy = new ConcurrentHashMap<Integer, String>();
    static volatile ConcurrentHashMap<Integer, Integer> sizeMapCopy = new ConcurrentHashMap<>();

    private static final Calendar CAL = Calendar.getInstance();
    static ArrayList<Integer> symbolNames = new ArrayList<Integer>();
    ArrayList<LocalTime> tradeTime = new ArrayList<>();

    private static volatile long actThresh;
    private static volatile long maxRepThresh;

    private static volatile long actThreshDisp = 0;
    private static volatile long maxDrawdownDisp = 100;
    private static volatile long minSizeDisp = 0;
    private static volatile double priceThreshDisp = 0.0;
    private static volatile double rtnThreshDisp = 0.0;

    public static long getActThresh() {
        return actThresh;
    }

    public static long maxRepThresh() {
        return maxRepThresh;
    }

    private static volatile boolean ComputingOn = false;
    private static volatile boolean filterOn = false;

    private int rowSelected;
    private int modelRow;

    private static ConcurrentHashMap<Integer, NavigableMap<LocalTime, Double>> mapCopy = new ConcurrentHashMap<Integer, NavigableMap<LocalTime, Double>>();

    public static boolean graphCreated = false;

    private Graph graph1 = new Graph();
    private Graph graph2 = new Graph();
    private Graph graph3 = new Graph();
    private Graph graph4 = new Graph();
    private Graph graph5 = new Graph();
    private Graph graph6 = new Graph();

    //rowfilter
    private final TableRowSorter<BarModel> sorter;

    Analysis() {
        try {
            ArrayList<Integer> numbers = new ArrayList();
//             numbers = Files.lines(Paths.get(ApideDemo.GLOBALPATH+"Table2.txt"))
//                .map(line -> line.split("\\s+"))
//                .flatMap(Arrays::stream)
//                .map(Integer::valueOf)
//                .distinct()
//                .collect(toList());

            actThresh = 10;
            maxRepThresh = 100;

            InputStreamReader reader = new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + "Table6.txt"), "gbk");
            BufferedReader reader1 = new BufferedReader(reader);
            //HashMap<Integer, String> map = new HashMap<Integer, String>();

            int sym;
            String name;
            String line;
            ArrayList<String> str = new ArrayList<>();
            while ((line = reader1.readLine()) != null) {

                str.add(line);
                //  System.out.println("line" + line);
                //  System.out.println(line.indexOf("\t"));
                //  System.out.println(Integer.parseInt(line.substring(0, line.indexOf("\t"))));
                //  System.out.println(line.substring(line.indexOf("\t")+1, line.length()));

                sym = Integer.parseInt(line.substring(0, line.indexOf("\t")));
                name = line.substring(line.indexOf("\t") + 1, line.length());

                numbers.add(sym);
                nameMapCopy.put(sym, name);
            }

            numbers.forEach((value) -> {
                symbolNames.add(value);
            });
            numbers.forEach((value) -> {
                hm.put(value, new Trait());
            });
            //   System.out.println (" symbol names is " + symbolNames + "\t" );
            //   System.out.println ( " symbol size is " + symbolNames.size());
        } catch (IOException e) {
            e.printStackTrace();
        }

        m_model = new BarModel();

        JTable tab = new JTable(m_model) {
            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);
                // int rendererWidth = comp.getPreferredSize().width;

                if (isCellSelected(Index_row, Index_col)) {
                    rowSelected = Index_row;
                    // System.out.println("rowSelected " + rowSelected);
                    modelRow = this.convertRowIndexToModel(Index_row);
                    //comp.setBackground(Color.CYAN);
                    comp.setBackground(Color.GREEN);

                    //System.out.println( " mapcopy size is " + mapCopy.size());
                    if (mapCopy.size() > 0) {
                        graph1.setNavigableMap(mapCopy.get(symbolNames.get(modelRow)));
                        graph1.setName(Integer.toString(symbolNames.get(modelRow)));
                        graph1.setChineseName(hm.get((symbolNames.get(modelRow))).longName());
                        graph1.setMaxAMT(hm.get((symbolNames.get(modelRow))).maxAMT());
                        graph1.setMinAMT(hm.get((symbolNames.get(modelRow))).minAMT());
                        graph1.setSize1(hm.get((symbolNames.get(modelRow))).currSize());

                        if (this.getParent().getParent().getParent().getComponentCount() == 3) {
                            this.getParent().getParent().getParent().getComponent(2).repaint();
                        }
                    }
                } else {

                    if (Index_row % 2 == 0) {
                        comp.setBackground(Color.lightGray);
                    } else {
                        comp.setBackground(Color.white);
                    }

                    if (convertRowIndexToModel(Index_row) < symbolNames.size()) {
                        try {
                            if (hm.get((symbolNames.get(convertRowIndexToModel(Index_row)))).strategy2() == true) {
                                // comp.setBackground(Color.GREEN );
                                comp.setBackground(Color.CYAN);
                            }

//                          if(hm.get((symbolNames.get(convertRowIndexToModel(Index_row)))).percChg5m() > 10) {
//                              comp.setBackground(Color.PINK);
//                          }   
                            if (hm.get((symbolNames.get(convertRowIndexToModel(Index_row)))).strategy2() == true
                                    && hm.get((symbolNames.get(convertRowIndexToModel(Index_row)))).percChg5m() > 0) {
                                comp.setBackground(Color.PINK);
                            }

                            if (hm.get((symbolNames.get(convertRowIndexToModel(Index_row)))).rtnOnDay() < 0 && Index_col == 17) {
                                comp.setBackground(Color.RED);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                return comp;
            }
        };

        JPanel jp = new JPanel();
        jp.setName("Top panel");
        jp.setLayout(new BorderLayout());
        JPanel jpTop = new JPanel();
        JPanel jpBottom = new JPanel();
        jpTop.setLayout(new GridLayout(1, 0, 5, 5));
        jpBottom.setLayout(new GridLayout(1, 0, 5, 5));

        JPanel jpLeft = new JPanel();
        jpLeft.setLayout(new BorderLayout());

        jpLeft.add(jpTop, BorderLayout.NORTH);
        jpLeft.add(jpBottom, BorderLayout.SOUTH);

        jp.add(jpLeft, BorderLayout.EAST);

        JPanel jpRight = new JPanel();
        jpRight.setLayout(new FlowLayout());
        //add(jp,BorderLayout.NORTH);

        //Activity Threshold
        JLabel jl1 = new JLabel("Activity Thresh");
        JTextField tf = new JTextField("50");
        //

        //MaxRep threshold
        JLabel jl2 = new JLabel("Max Rep Thresh");
        JTextField tf1 = new JTextField("100");
        // 
        JLabel jl3 = new JLabel("to be used");
        JTextField tf2 = new JTextField("1246");

        JLabel jl4 = new JLabel("Graph2");
        JTextField tf3 = new JTextField("1");

        JLabel jl5 = new JLabel("Graph3");
        JTextField tf4 = new JTextField("2");

        JLabel jl6 = new JLabel("Graph4");
        JTextField tf5 = new JTextField("3147");

        JLabel jl7 = new JLabel("Graph5");
        JTextField tf6 = new JTextField("2822");

        JLabel jl8 = new JLabel("Graph6");
        JTextField tf7 = new JTextField("2823");

        jpTop.add(jl1);
        jpBottom.add(tf);
        jpTop.add(jl2);
        jpBottom.add(tf1);
        jpTop.add(jl3);
        jpBottom.add(tf2);
        jpTop.add(jl4);
        jpBottom.add(tf3);
        jpTop.add(jl5);
        jpBottom.add(tf4);
        jpTop.add(jl6);
        jpBottom.add(tf5);
        jpTop.add(jl7);
        jpBottom.add(tf6);
        jpTop.add(jl8);
        jpBottom.add(tf7);

        JButton jb = new JButton("Computing on");
        //JButton jb1 = new JButton("run");
        jb.addActionListener(al -> {
            toggleComputingOn();
            System.out.println("computing status is " + ComputingOn);
        });

        JButton jb1 = new JButton("Filter Activity");
        jb1.addActionListener(al -> {
            toggleFilterOn();
            System.out.println("filter status is " + filterOn);
        });

        //JButton jb11 = new JButton ("")
        JLabel drawdownB = new JLabel("Drawdown");
        JLabel activityThreshB = new JLabel("Actvty Thresh");
        JLabel sizeThreshB = new JLabel("size Thresh");
        JLabel priceThreshB = new JLabel("Price Thresh");
        JLabel rtnThreshB = new JLabel("Rtn Thresh");

        JTextField tf11 = new JTextField("50"); //drawdown
        JTextField tf12 = new JTextField("20"); //activity
        JTextField tf13 = new JTextField("1"); //volume traded
        JTextField tf14 = new JTextField("1.0"); //price 
        JTextField tf15 = new JTextField("5.0"); //return

        JButton jb2 = new JButton("Graph");

        jb2.addActionListener(al -> {

            if (mapCopy.size() > 0) {
                try {
                    //toggleComputingOn();
                    System.out.println("mapcopy size " + mapCopy.size());
                    graph1.setNavigableMap(mapCopy.get(symbolNames.get(modelRow)));
                    graph2.setNavigableMap(mapCopy.get(Integer.parseInt(tf3.getText())));
                    graph3.setNavigableMap(mapCopy.get(Integer.parseInt(tf4.getText())));
                    graph4.setNavigableMap(mapCopy.get(Integer.parseInt(tf5.getText())));
                    graph5.setNavigableMap(mapCopy.get(Integer.parseInt(tf6.getText())));
                    graph6.setNavigableMap(mapCopy.get(Integer.parseInt(tf7.getText())));

                    graph2.setName(Integer.toString(Integer.parseInt(tf3.getText())));
                    graph2.setChineseName(hm.get(Integer.parseInt(tf3.getText())).longName());
                    graph3.setName(Integer.toString(Integer.parseInt(tf4.getText())));
                    graph3.setChineseName(hm.get(Integer.parseInt(tf4.getText())).longName());
                    graph4.setName(Integer.toString(Integer.parseInt(tf5.getText())));
                    graph4.setChineseName(hm.get(Integer.parseInt(tf5.getText())).longName());
                    graph5.setName(Integer.toString(Integer.parseInt(tf6.getText())));
                    graph5.setChineseName(hm.get(Integer.parseInt(tf6.getText())).longName());
                    graph6.setName(Integer.toString(Integer.parseInt(tf7.getText())));
                    graph6.setChineseName(hm.get(Integer.parseInt(tf7.getText())).longName());

                    graph2.setMaxAMT(hm.get(Integer.parseInt(tf3.getText())).maxAMT());
                    graph2.setMinAMT(hm.get(Integer.parseInt(tf3.getText())).minAMT());
                    graph2.setSize1(hm.get(Integer.parseInt(tf3.getText())).currSize());
                    graph3.setMaxAMT(hm.get(Integer.parseInt(tf4.getText())).maxAMT());
                    graph3.setMinAMT(hm.get(Integer.parseInt(tf4.getText())).minAMT());
                    graph3.setSize1(hm.get(Integer.parseInt(tf4.getText())).currSize());
                    graph4.setMaxAMT(hm.get(Integer.parseInt(tf5.getText())).maxAMT());
                    graph4.setMinAMT(hm.get(Integer.parseInt(tf5.getText())).minAMT());
                    graph4.setSize1(hm.get(Integer.parseInt(tf5.getText())).currSize());
                    graph5.setMaxAMT(hm.get(Integer.parseInt(tf6.getText())).maxAMT());
                    graph5.setMinAMT(hm.get(Integer.parseInt(tf6.getText())).minAMT());
                    graph5.setSize1(hm.get(Integer.parseInt(tf6.getText())).currSize());
                    graph6.setMaxAMT(hm.get(Integer.parseInt(tf7.getText())).maxAMT());
                    graph6.setMinAMT(hm.get(Integer.parseInt(tf7.getText())).minAMT());
                    graph6.setSize1(hm.get(Integer.parseInt(tf7.getText())).currSize());

                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    System.out.println("incorrect symbol input");
                    tf3.setText("1");
                    tf4.setText("2");
                    tf5.setText("3147");
                    tf6.setText("2822");
                    tf7.setText("2823");
                }

                //graph1.getNavigableMap();
                System.out.println(" graphCreated is " + graphCreated);
                if (!graphCreated) {

                    JPanel graphPanel = new JPanel();
                    graphPanel.setLayout(new GridLayout(6, 1));
                    JScrollPane chartScroll = new JScrollPane(graph1) {
                        @Override
                        public Dimension getPreferredSize() {
                            Dimension d = super.getPreferredSize();
                            d.height = 250;
                            return d;
                        }
                    };
                    JScrollPane chartScroll2 = new JScrollPane(graph2) {
                        @Override
                        public Dimension getPreferredSize() {
                            Dimension d = super.getPreferredSize();
                            d.height = 250;
                            return d;
                        }
                    };
                    JScrollPane chartScroll3 = new JScrollPane(graph3) {
                        @Override
                        public Dimension getPreferredSize() {
                            Dimension d = super.getPreferredSize();
                            d.height = 250;
                            return d;
                        }
                    };
                    JScrollPane chartScroll4 = new JScrollPane(graph4) {

                        @Override
                        public Dimension getPreferredSize() {
                            Dimension d = super.getPreferredSize();
                            d.height = 250;
                            return d;
                        }
                    };
                    JScrollPane chartScroll5 = new JScrollPane(graph5) {
                        @Override
                        public Dimension getPreferredSize() {
                            Dimension d = super.getPreferredSize();
                            d.height = 250;
                            return d;
                        }
                    };
                    JScrollPane chartScroll6 = new JScrollPane(graph6) {
                        @Override
                        public Dimension getPreferredSize() {
                            Dimension d = super.getPreferredSize();
                            d.height = 250;
                            return d;
                        }
                    };
                    graphPanel.add(chartScroll);
                    graphPanel.add(chartScroll2);
                    graphPanel.add(chartScroll3);
                    graphPanel.add(chartScroll4);
                    graphPanel.add(chartScroll5);
                    graphPanel.add(chartScroll6);

                    chartScroll.setName(" graph scrollpane");
                    chartScroll2.setName(" graph scrollpane 2");
                    chartScroll3.setName(" graph scrollpane 3");
                    chartScroll4.setName(" graph scrollpane 4");
                    chartScroll5.setName(" graph scrollpane 5");
                    chartScroll6.setName(" graph scrollpane 6");
                    //add(chartScroll, BorderLayout.CENTER);
                    add(graphPanel, BorderLayout.CENTER);

                    //this.setSymbol("graph");
                    graphCreated = true;
                    this.repaint();
                    //System.out.println
                    // repaint();
                    // JButton jbrandom = new JButton("random button");
                    // jp.add(jbrandom);
                } else {
                    graph1.setNavigableMap(mapCopy.get(symbolNames.get(modelRow)));
                    graph2.setNavigableMap(mapCopy.get(Integer.parseInt(tf3.getText())));
                    graph3.setNavigableMap(mapCopy.get(Integer.parseInt(tf4.getText())));
                    graph4.setNavigableMap(mapCopy.get(Integer.parseInt(tf5.getText())));
                    graph5.setNavigableMap(mapCopy.get(Integer.parseInt(tf6.getText())));
                    graph6.setNavigableMap(mapCopy.get(Integer.parseInt(tf7.getText())));

                    graph2.setName(Integer.toString(Integer.parseInt(tf3.getText())));
                    graph2.setChineseName(hm.get(Integer.parseInt(tf3.getText())).longName());
                    graph3.setName(Integer.toString(Integer.parseInt(tf4.getText())));
                    graph3.setChineseName(hm.get(Integer.parseInt(tf4.getText())).longName());
                    graph4.setName(Integer.toString(Integer.parseInt(tf5.getText())));
                    graph4.setChineseName(hm.get(Integer.parseInt(tf5.getText())).longName());
                    graph5.setName(Integer.toString(Integer.parseInt(tf6.getText())));
                    graph5.setChineseName(hm.get(Integer.parseInt(tf6.getText())).longName());
                    graph6.setName(Integer.toString(Integer.parseInt(tf7.getText())));
                    graph6.setChineseName(hm.get(Integer.parseInt(tf7.getText())).longName());

                    repaint();
                }
                System.out.println("Graphing");
            }
        });

        jpRight.add(jb);
        jpRight.add(jb1);
        jpRight.add(jb2);

        jpRight.add(drawdownB);
        jpRight.add(tf11);
        jpRight.add(Box.createHorizontalStrut(10));

        jpRight.add(activityThreshB);
        jpRight.add(tf12);
        jpRight.add(Box.createHorizontalStrut(10));

        jpRight.add(sizeThreshB);
        jpRight.add(tf13);
        jpRight.add(Box.createHorizontalStrut(10));

        jpRight.add(priceThreshB);
        jpRight.add(tf14);
        jpRight.add(Box.createHorizontalStrut(10));

        jpRight.add(rtnThreshB);
        jpRight.add(tf15);
        jpRight.add(Box.createHorizontalStrut(10));

        tf11.addActionListener(ae -> {
            maxDrawdownDisp = Integer.parseInt(tf11.getText());
            System.out.println(" max drawdown for display is " + maxDrawdownDisp);
        });
        tf12.addActionListener(ae -> {
            actThreshDisp = Integer.parseInt(tf12.getText());
            System.out.println(" activity threshold for display is " + actThreshDisp);
        });
        tf13.addActionListener(ae -> {
            minSizeDisp = Integer.parseInt(tf13.getText());
            System.out.println(" minSizeMap threashold for display is  " + minSizeDisp);
        });
        tf14.addActionListener(ae -> {
            priceThreshDisp = Double.parseDouble(tf14.getText());
            System.out.println(" priceThreshDisp threashold for display is  " + priceThreshDisp);
        });
        tf15.addActionListener(ae -> {
            rtnThreshDisp = Double.parseDouble(tf15.getText());
            System.out.println(" rtn threashold for display is  " + rtnThreshDisp);
        });

        jp.add(jpRight, BorderLayout.WEST);

        JScrollPane scroll = new JScrollPane(tab) {
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 1200;
                return d;
            }
        };
        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        add(jp, BorderLayout.NORTH);

        //add(tf, BorderLayout.BEFORE_FIRST_LINE);
        tf.addActionListener(ae -> {
            actThresh = Integer.parseInt(tf.getText());
            System.out.println(" activity threshold is " + actThresh);
        });
        tf1.addActionListener(ae -> {
            maxRepThresh = Integer.parseInt(tf1.getText());
            System.out.println(" maxRep threshold is " + maxRepThresh);
        });
        tab.setAutoCreateRowSorter(true);
        sorter = (TableRowSorter<BarModel>) tab.getRowSorter();
        // sorter.setRowFilter(RowFilter.numberFilter(ComparisonType.NOT_EQUAL,0,10));
    }

    NavigableMap<LocalTime, Double> tm;

    public void toggleComputingOn() {
        ComputingOn = !ComputingOn;
    }

    public void toggleFilterOn() {
        if (filterOn == false) {
            List<RowFilter<Object, Object>> filters = new ArrayList<>(2);
            filters.add(RowFilter.numberFilter(ComparisonType.AFTER, actThreshDisp, 10));
            filters.add(RowFilter.numberFilter(ComparisonType.BEFORE, maxDrawdownDisp, 8));
            filters.add(RowFilter.numberFilter(ComparisonType.AFTER, minSizeDisp, 9));
            filters.add(RowFilter.numberFilter(ComparisonType.AFTER, priceThreshDisp, 1));
            filters.add(RowFilter.numberFilter(ComparisonType.AFTER, rtnThreshDisp, 17));

            //sorter.setRowFilter(RowFilter.numberFilter(ComparisonType.NOT_EQUAL,0,10));
            sorter.setRowFilter(RowFilter.andFilter(filters));

            filterOn = true;
        } else {
            //sorter.setRowFilter(RowFilter.numberFilter(ComparisonType.AFTER,-1,10));
            //sorter.setrow
            sorter.setRowFilter(null);
            filterOn = false;
        }
    }

    public boolean getComputingStatus() {
        return ComputingOn;
    }

    public static void compute(ConcurrentMap<Integer, ? extends NavigableMap<LocalTime, Double>> map) {

        mapCopy = (ConcurrentHashMap<Integer, NavigableMap<LocalTime, Double>>) map;
        sizeMapCopy = LiveData.sizeMap;
        //nameMapCopy = LiveData.nameMap;
        long start = System.currentTimeMillis();
        //System.out.println("name copy map size" + nameMapCopy.size());
        //System.out.print("print all map" + nameMapCopy.toString());

        int symb;
        Trait trait = new Trait();
        //  System.out.println("mapcopy size is " + mapCopy.size());

        Iterator it = map.keySet().iterator();
        // System.out.println( " size of the map in analysis " + map.size());

        while (it.hasNext()) {
            symb = (int) it.next();
            // if (symb<100) {
            try {
                trait = computeAll(map.get(symb));

                if (nameMapCopy.containsKey(symb)) {
                    trait.longName(nameMapCopy.get(symb));
                } else {
                    trait.longName("no name");
                }

                if (sizeMapCopy.containsKey(symb)) {
                    trait.currSize(sizeMapCopy.get(symb));
                    //System.out.println(" sizeMapCopy.get(symb)" + trait.currSize());
                } else {
                    trait.currSize(0);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            hm.put(symb, trait);
            //fire();        
        }

        System.out.println("Computing cost is  " + (System.currentTimeMillis() - start));
    }

    //day
    public static double getMaxDay(NavigableMap<LocalTime, Double> tm) {
        try {
            //return tm.entrySet().stream().max((entry1,entry2)-> entry1.getValue()>=entry2.getValue() ? 1:-1).get().getValue();

            return tm.entrySet().parallelStream().mapToDouble(a -> a.getValue()).max().getAsDouble();

            //return tm.entrySet().stream().
            //return tm.entrySet().stream().max()
        } catch (Exception ex) {
            System.out.println("tm has issues" + tm.size());
            throw ex;
        }
        //return 0;
    }

    public static double getMinDay(NavigableMap<LocalTime, Double> tm) {
        try {
            //return tm.entrySet().stream().filter((entry1) -> entry1.getValue()!= 0).min((entry1,entry2)-> entry1.getValue()>=entry2.getValue() ? 1:-1).get().getValue();   
            return tm.entrySet().parallelStream().mapToDouble(a -> a.getValue()).min().getAsDouble();

        } catch (Exception e) {
            return 0;
        }
    }

    public static LocalTime getMaxDayT(NavigableMap<LocalTime, Double> tm) {
        return tm.entrySet().parallelStream().max((entry1, entry2) -> entry1.getValue() >= entry2.getValue() ? 1 : -1).get().getKey();
    }

    public static LocalTime getMinDayT(NavigableMap<LocalTime, Double> tm) {
        try {
            // return tm.entrySet().stream().filter((entry1) -> entry1.getValue()!= 0).min((entry1,entry2)-> entry1.getValue()>=entry2.getValue() ? 1:-1).get().getKey();  
            return tm.entrySet().parallelStream().min((entry1, entry2) -> entry1.getValue() >= entry2.getValue() ? 1 : -1).get().getKey();//.map(Map.Entry::getKey).get();
        } catch (Exception e) {
            return LocalTime.of(9, 30);
        }
    }

    public static LocalTime getMaxAMT(NavigableMap<LocalTime, Double> tm) {
        if (tm.size() > 2) {
            if (tm.firstKey().isBefore(LocalTime.of(12, 1))) {
                return tm.entrySet().stream().filter((k) -> k.getKey().isBefore(LocalTime.of(12, 1))).max((entry1, entry2) -> entry1.getValue() >= entry2.getValue() ? 1 : -1).get().getKey();
            }
        }
        return LocalTime.of(9, 20);
    }

    public static LocalTime getMinAMT(NavigableMap<LocalTime, Double> tm) {
        if (tm.size() > 2) {
            if (tm.firstKey().isBefore(LocalTime.of(12, 1))) {
                return tm.entrySet().stream().filter((k) -> k.getKey().isBefore(LocalTime.of(12, 1))).min((entry1, entry2) -> entry1.getValue() >= entry2.getValue() ? 1 : -1).get().getKey();
            }
        }
        return LocalTime.of(9, 20);
    }

    public static long getActivity(NavigableMap<LocalTime, Double> tm) {

        Iterator it = tm.keySet().iterator();
        LongAdder la = new LongAdder();
        double last = 0;
        double current;

        while (it.hasNext()) {
            if ((current = tm.get((LocalTime) it.next())) != last) {
                la.increment();
            }
            last = current;
        }
        //activity = m_activity;
        return la.sum() - 1;

        //return tm.entrySet().stream().spliterator().
    }

    public static long getMaxRep(NavigableMap<LocalTime, Double> tm) {
//            ConcurrentHashMap<Double,LongAdder > mp = new ConcurrentHashMap<Double,LongAdder>();
//            Iterator it = tm.keySet().iterator();
//            double current = 0;
//            
//            while (it.hasNext()) {
//                if (mp.containsKey(current=tm.get((LocalTime)it.next()))) {
//                     mp.get(current).increment();
//                } else {
//                    mp.put(current, new LongAdder())  ;
//                    mp.get(current).increment();
//                }
//            }
//            return mp.entrySet().stream().max((entry1,entry2)-> entry1.getValue().sum()>=entry2.getValue().sum() ? 1:-1).get().getValue().sum();

        return tm.entrySet().parallelStream().collect(Collectors.groupingBy((a -> a.getValue()), Collectors.counting())).entrySet().parallelStream().max(Map.Entry.comparingByValue()).map(Map.Entry::getValue).get();
    }

    public static long getMaxRep(SortedMap<LocalTime, Double> tm) {
//            ConcurrentHashMap<Double,LongAdder > mp = new ConcurrentHashMap<Double,LongAdder>();
//            Iterator it = tm.keySet().iterator();
//            double current = 0;
//            while (it.hasNext()) {
//                if (mp.containsKey(current=tm.get((LocalTime)it.next()))) {
//                     mp.get(current).increment();
//                } else {
//                    mp.put(current, new LongAdder())  ;
//                    mp.get(current).increment();
//                }
//            }
//            return mp.entrySet().stream().max((entry1,entry2)-> entry1.getValue().sum()>=entry2.getValue().sum() ? 1:-1).get().getValue().sum();
        return tm.entrySet().parallelStream().collect(Collectors.groupingBy((a -> a.getValue()), Collectors.counting())).entrySet().parallelStream().max(Map.Entry.comparingByValue()).map(Map.Entry::getValue).get();

    }

//        public void rtn1Min(int symbol) {
//            LocalTime lt = data.get(symbol).lastKey();
//            
//            try {
//                rtn1Min =  log(data.get(symbol).lastEntry().getValue()/data.get(symbol).lowerEntry(lt).getValue());
//                rtn3Min = log(data.get(symbol).lastEntry().getValue()/data.get(symbol).lowerEntry(lt.minusMinutes(2)).getValue());
//                rtn5Min = log(data.get(symbol).lastEntry().getValue()/data.get(symbol).lowerEntry(lt.minusMinutes(4)).getValue());
//            } catch(NullPointerException e) {
//            
//            }
//        }
    public static int getPercentile(NavigableMap<LocalTime, Double> tm) {
        if (getMaxDay(tm) - getMinDay(tm) > 0.0001) {
            return (int) (100 * (tm.lastEntry().getValue() - getMinDay(tm)) / (getMaxDay(tm) - getMinDay(tm)));
        }
        return 0;
    }

//        public void percentileCompute(int symbol) {
//            LocalTime lt = data.get(symbol).lastKey();
//            percChg1m =  (data.get(symbol).lastEntry().getValue()-data.get(symbol).lowerEntry(lt).getValue())/(maxDay-minDay);
//            percChg3m =  (data.get(symbol).lastEntry().getValue()-data.get(symbol).lowerEntry(lt.minusMinutes(2)).getValue())/(maxDay-minDay);
//            percChg5m =  (data.get(symbol).lastEntry().getValue()-data.get(symbol).lowerEntry(lt.minusMinutes(4)).getValue())/(maxDay-minDay);
//            
//        }
    public static double getRtnOnDay(NavigableMap<LocalTime, Double> tm) {
        if (Math.abs(tm.lastEntry().getValue() - tm.firstEntry().getValue()) > 0.0001) {
            return (double) 100 * Math.round(log(tm.lastEntry().getValue() / tm.firstEntry().getValue()) * 1000d) / 1000d;
        }
        return 0;
    }

    public double rtn1Min(NavigableMap<LocalTime, Double> tm) {
        LocalTime lt = tm.lastKey();
        try {
            return log(tm.lastEntry().getValue() / tm.lowerEntry(lt).getValue());
            //rtn3Min = log(data.get(symbol).lastEntry().getValue()/data.get(symbol).lowerEntry(lt.minusMinutes(2)).getValue());
            //rtn5Min = log(data.get(symbol).lastEntry().getValue()/data.get(symbol).lowerEntry(lt.minusMinutes(4)).getValue());
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public double rtn3Min(NavigableMap<LocalTime, Double> tm) {
        LocalTime lt = tm.lastKey();
        try {
            return log(tm.lastEntry().getValue() / tm.lowerEntry(lt.minusMinutes(2)).getValue());
            //rtn5Min = log(data.get(symbol).lastEntry().getValue()/data.get(symbol).lowerEntry(lt.minusMinutes(4)).getValue());
        } catch (NullPointerException e) {
            return 0;
        }
    }

    public double rtn5Min(NavigableMap<LocalTime, Double> tm) {
        LocalTime lt = tm.lastKey();
        try {
            return log(tm.lastEntry().getValue() / tm.lowerEntry(lt.minusMinutes(4)).getValue());
        } catch (NullPointerException e) {
            return 0;
        }
    }

    public static int percChg1m(NavigableMap<LocalTime, Double> tm) {
        LocalTime lt = tm.lastKey();
        try {
            return (int) (100 * (tm.lastEntry().getValue() - tm.lowerEntry(lt).getValue()) / (getMaxDay(tm) - getMinDay(tm)));
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static int percChg3m(NavigableMap<LocalTime, Double> tm) {
        LocalTime lt = tm.lastKey();

        if (tm.size() > 5) {
            try {
                return (int) (100 * (tm.lastEntry().getValue() - tm.lowerEntry(lt.minusMinutes(2)).getValue()) / (getMaxDay(tm) - getMinDay(tm)));
            } catch (Exception e) {
                e.printStackTrace();
                return 0;
            }
        }
        return 0;
    }
    // 3m 

    public static int percChg5m(NavigableMap<LocalTime, Double> tm) {

        LocalTime lt = tm.lastKey();

        if (tm.size() > 7) {
            try {
                return (int) (100 * (tm.lastEntry().getValue() - tm.lowerEntry(lt.minusMinutes(4)).getValue()) / (getMaxDay(tm) - getMinDay(tm)));
            } catch (Exception e) {
                e.printStackTrace();
                return 0;
            }
        }
        return 0;
    }

    public static int drawDownCompute(NavigableMap<LocalTime, Double> tm) {
        //use this for size
        Iterator it = tm.keySet().iterator();
        LocalTime k;
        double v;
        double maxDd = 0;
        double max = 0;

        TreeSet<Double> ts = new TreeSet<>();

        try {
            while (it.hasNext()) {
                k = (LocalTime) it.next();
                //     System.out.println(" time is " + k);
                v = tm.get(k);
                //       System.out.println(" v is " + v);
                if (v > max) {
                    if (maxDd != 0) {
                        ts.add(maxDd);
                    }
                    max = v;
                    maxDd = 0;
                    //         System.out.println ("max is " + max);

                } else {
                    maxDd = (max - v) > maxDd ? (max - v) : maxDd;
                    //          System.out.println ("maxDd is " + maxDd);
                }
            }
            if (!ts.contains(maxDd)) {
                ts.add(maxDd);
            }
            //ts.pollLast();
            // System.out.println(" current subset is " + ts);
            //System.out.println( " polling last " + ts.pollLast());
            //System.out.println( " polling last " + ts.pollLast());
            if (ts.isEmpty() || getMaxDay(tm) - getMinDay(tm) < 0.0001) {
                return -1;
            } else {
                return (int) ((ts.pollLast()) / (getMaxDay(tm) - getMinDay(tm)) * 100);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static int drawDownCompute2(NavigableMap<LocalTime, Double> tm) {
        //use this for size
        Iterator it = tm.keySet().iterator();
        LocalTime k;
        double v;
        double maxDd = 0;
        double max = 0;

        TreeSet<Double> ts = new TreeSet<>();

        try {
            while (it.hasNext()) {
                k = (LocalTime) it.next();
                //      System.out.println(" time is " + k);
                v = tm.get(k);
                //        System.out.println(" v is " + v);
                if (v > max) {
                    if (maxDd != 0) {
                        ts.add(maxDd);
                    }
                    max = v;
                    maxDd = 0;
                    //          System.out.println ("max is " + max);

                } else {
                    maxDd = (max - v) > maxDd ? (max - v) : maxDd;
                    //           System.out.println ("maxDd is " + maxDd);
                }
            }
            if (!ts.contains(maxDd)) {
                ts.add(maxDd);
            }

            if (!ts.isEmpty()) {
                ts.pollLast();
            }
            // System.out.println(" current subset is " + ts);
            //System.out.println( " polling last " + ts.pollLast());
            //System.out.println( " polling last " + ts.pollLast());

            if (ts.isEmpty() || getMaxDay(tm) - getMinDay(tm) < 0.0001) {
                return -1;
            } else {
                double i = ts.pollLast();
                //System.out.println(" maxDd " + i);
                return (int) (i / (getMaxDay(tm) - getMinDay(tm)) * 100);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;

    }

    public static int getJolt(NavigableMap<LocalTime, Double> tm) {

        LocalTime k;
        double v;
        Iterator it = tm.keySet().iterator();
        LocalTime left;
        LocalTime right;
        LocalTime tempLeft = tm.firstKey();
        LocalTime tempRight = tm.firstKey();

        double max = 0;
        double tempMax = 0;
        double min = Double.MAX_VALUE;
        double chgPerMin;
        double maxChgPerMin = 0;
        int timeShift;

        while (it.hasNext()) {
            k = (LocalTime) it.next();
            v = tm.get(k);

            //
            if (k.isAfter(LocalTime.of(9, 30))) {

                if (v < min) {
                    min = v;
                    tempLeft = k;
                    tempRight = k;
                    tempMax = 0;
                } else {
                    if (v - min > tempMax) {
                        tempMax = v - min;
                        tempRight = k;
                        //  System.out.println("tempmax is " + tempMax);
                        //  System.out.println( "tempLeft is " + tempLeft);
                        //  System.out.println ("tempRIght is " + tempRight);

                        // chgPerMin = (tempMax)/((tempRight.toSecondOfDay()-tempLeft.toSecondOfDay())/60-timeShift);
                        //  maxChgPerMin = Math.max(chgPerMin, maxChgPerMin);
                    }
                }

                if (tempMax > max) {
                    max = tempMax;
                    left = tempLeft;
                    right = tempRight;

                    if (tempRight.isAfter(LocalTime.of(12, 1)) && tempLeft.isBefore(LocalTime.of(12, 0))) {
                        timeShift = 60;
                    } else {
                        timeShift = 0;
                    }

                    chgPerMin = (max) / ((right.toSecondOfDay() - left.toSecondOfDay()) / 60 - timeShift);
                    maxChgPerMin = Math.max(chgPerMin, maxChgPerMin);

//                    System.out.println(" max is " + max);
//                    System.out.println("chgPermin is " + chgPerMin);
//                    System.out.println( " minutes passed is " + ((right.toSecondOfDay()-left.toSecondOfDay())/60-timeShift));
//                    System.out.println("max chg per min is " + maxChgPerMin);
//                    System.out.println ("left is " + left );
//                    System.out.println(" right is " + right);
                }
            }

            //System.out.println(" max is " + max);
            //System.out.println(" left is " + left);
            //System.out.println( "right is " + right);
        }

        return (int) (maxChgPerMin / (getMaxDay(tm) - getMinDay(tm)) * 100);
        //return (int)(i/(getMaxDay(tm)-getMinDay(tm))*100);
        //return max;
    }

    // not minute by minute, but a non-reversing up-trend 
    public static int getPureJolt(NavigableMap<LocalTime, Double> tm) {

        LocalTime k;
        double v;
        Iterator it = tm.keySet().iterator();
        LocalTime left = tm.firstKey();
        LocalTime right = tm.firstKey();
        LocalTime tempLeft = tm.firstKey();
        LocalTime tempRight = tm.firstKey();

        double max = 0;
        double tempMax = 0;
        double min = Double.MAX_VALUE;
        double chg = 0;
        //double maxChgPerMin = 0;
        int timeShift = 0;

        while (it.hasNext()) {
            k = (LocalTime) it.next();
            v = tm.get(k);

            //
            if (k.isAfter(LocalTime.of(9, 30))) {

                if (v < min) {
                    min = v;
                    tempLeft = k;
                    tempRight = k;
                    tempMax = 0;
                } else {
                    if (v - min > tempMax) {
                        tempMax = v - min;
                        tempRight = k;

                        if (tempRight.isAfter(LocalTime.of(12, 1)) && tempLeft.isBefore(LocalTime.of(12, 0))) {
                            timeShift = 60;
                        } else {
                            timeShift = 0;
                        }
                    }
                }

                if (tempMax > max) {
                    max = tempMax;
                    left = tempLeft;
                    right = tempRight;

                    if (tempRight.isAfter(LocalTime.of(12, 1)) && tempLeft.isBefore(LocalTime.of(12, 0))) {
                        timeShift = 60;
                    } else {
                        timeShift = 0;
                    }
                    chg = max;
                    //maxChgPerMin = Math.max(chgPerMin, maxChgPerMin);
                }
            }
        }
        return (int) (chg / (getMaxDay(tm) - getMinDay(tm)) * 100);
    }

    public static int getMaxMinJolt(NavigableMap<LocalTime, Double> tm) {
        NavigableMap<LocalTime, Double> maxminarray = new ConcurrentSkipListMap<>();
        Iterator it = tm.keySet().iterator();
        LocalTime k;
        double v;
        Boolean flagMax = true;
        double previousMax = 0;
        double previousMin = 0;
        TreeSet<Double> ts = new TreeSet<>();
        double max, min;
        double maxJolt = 0, maxDraw = 0;
        LocalTime maxJoltStart = LocalTime.of(9, 30), maxJoltEnd = LocalTime.of(9, 30), maxDdStart = LocalTime.of(9, 30), maxDdEnd = LocalTime.of(9, 30);
        LocalTime tempMaxJoltStart, tempMaxJoltEnd, tempMaxDdStart, tempMaxDdEnd;
        tempMaxJoltStart = LocalTime.of(9, 30);
        tempMaxJoltEnd = LocalTime.of(9, 30);
        tempMaxDdStart = LocalTime.of(9, 30);
        tempMaxDdEnd = LocalTime.of(9, 30);

        while (it.hasNext()) {
            k = (LocalTime) it.next();
            v = tm.get(k);

            if (maxminarray.isEmpty() || maxminarray.size() == 1) {
                maxminarray.put(k, v);
                previousMax = v;
                previousMin = v;

            } else {
                if (v > maxminarray.lastEntry().getValue()) {
                    //System.out.println("v is " + v + "last entry is " + maxminarray.lastEntry().toString());
                    if (flagMax == true) {
                        maxminarray.remove(maxminarray.lastKey());
                    }

                    maxminarray.put(k, v);
                    flagMax = true;
                    ts.add(v - previousMin);
                    previousMax = v;

                    tempMaxDdStart = k;

                    if (v - previousMin > maxJolt) {
                        maxJolt = v - previousMin;
                        maxJoltStart = tempMaxJoltStart;
                        maxJoltEnd = k;
                    }
                    //System.out.println(" v is " + v + " previous is " + previousMax);
                    //  System.out.println(" ts to string " + ts.toString());

                } else if (v < maxminarray.lastEntry().getValue()) {
                    if (flagMax != true) {
                        maxminarray.remove(maxminarray.lastKey());
                    }

                    maxminarray.put(k, v);
                    ts.add(v - previousMax);
                    previousMin = v;
                    tempMaxJoltStart = k;

                    flagMax = false;

                    if (v - previousMax < maxDraw) {
                        maxDraw = v - previousMax;
                        maxDdStart = tempMaxDdStart;
                        maxDdEnd = k;
                    }

                }
            }
        }
        // System.out.println("printing maxminarray" + maxminarray);
        //  System.out.println(" max is " + ts.stream().max(Comparator.naturalOrder()));
        //System.out.println(" min is " + ts.stream().min(Comparator.naturalOrder()));  
        //  System.out.println(" jolt start end time "+ maxJoltStart.toString() + " "+ maxJoltEnd.toString());
        //  System.out.println(" Dd start end time "+ maxDdStart.toString() + " "+ maxDdEnd.toString());
        //max =ts.stream().max(Comparator.naturalOrder()).get();

        try {
            if (!ts.isEmpty()) {
                return (int) (ts.stream().max(Comparator.naturalOrder()).get() / (getMaxDay(tm) - getMinDay(tm)) * 100);
            } else {
                return -1;
            }
        } catch (NoSuchElementException ex) {
            ex.printStackTrace();
            System.out.println("printing issue tm  " + tm.toString());
            return 0;
        }
        //maxminarray.entrySet().stream().
    }

    public static boolean computeStrategy1(NavigableMap<LocalTime, Double> tm) {
        return getMaxDayT(tm).isAfter(LocalTime.of(10, 30))
                && getMinDayT(tm).isBefore(LocalTime.of(10, 0))
                && (getActivity(tm) > actThresh)
                && (getMaxRep(tm) < maxRepThresh);
    }

    public static boolean computeStrategy2(NavigableMap<LocalTime, Double> tm) {
        return getMaxAMT(tm).isAfter(LocalTime.of(10, 30))
                && getMinAMT(tm).isBefore(LocalTime.of(10, 0))
                && getPercentile(tm) > 50
                && drawDownCompute(tm) < 50;
    }

    public static LocalTime computeLastTimestamp(NavigableMap<LocalTime, Double> tm) {
        LocalTime localMax;
        LocalTime dayMax;

        if ((localMax = getMaxAMT(tm)).isAfter(LocalTime.of(10, 30))
                && (getMinAMT(tm)).isBefore(LocalTime.of(9, 45))
                && getPercentile(tm) > 0) {
            return (dayMax = getMaxDayT(tm)).isAfter(localMax) ? dayMax : localMax;
        }
        return LocalTime.of(9, 30);
    }

    public static LocalTime computeLastTimestamp1(NavigableMap<LocalTime, Double> tm) {
        LocalTime maxt;

        if ((maxt = getMaxAMT(tm)).isAfter(LocalTime.of(10, 30))
                && (getMinAMT(tm)).isBefore(LocalTime.of(10, 0))) {
            if (percChg3m(tm) > 0) {
                //return (dayMax=getMaxDayT(tm)).isAfter(localMax)?dayMax:localMax;
                return LocalTime.of(LocalTime.now().getHour(), LocalTime.now().getMinute());
            } else {
                return getMaxDayT(tm);
            }
        }
        return LocalTime.of(9, 30);
    }

//        public LocalTime computeLastTimestamp() {  
//        }
    public static Trait computeAll(NavigableMap<LocalTime, Double> tm) {

        // System.out.println(" In analysis, computing all for a stock");
        Trait t = new Trait();

        try {
            // System.out.println(" In analysis, computing all for a stock");
            //Trait t = new Trait();
            //last price
            if (tm.size() > 1) {
                // System.out.println(
                LocalTime lt = tm.lastKey();
                //  System.out.println("2");
                //t.lastPrice(tm.lowerEntry(lt).getValue());
                t.lastPrice(tm.lastEntry().getValue());
                //maxDay
                t.maxDay(getMaxDay(tm));
                //minDay
                t.minDay(getMinDay(tm));
                //maxDayT
                t.maxDayT(getMaxDayT(tm));
                //minDayT
                t.minDayT(getMinDayT(tm));
                //AM max time
                t.maxAMT(getMaxAMT(tm));
                //AM min time
                t.minAMT(getMinAMT(tm));
                //maxDrawDown
                t.maxDrawDown(drawDownCompute(tm));
                //secondary drawdown
                t.maxDrawDown2(drawDownCompute2(tm));
                //processUntil
                //activty
                t.activity(getActivity(tm));
                //maxRep
                t.maxRep(getMaxRep(tm));
                //percentile
                t.percentile(getPercentile(tm));
                //1 min chg in perc
                t.percChg1m(percChg1m(tm));
                //3 min chg in perc
                t.percChg3m(percChg3m(tm));
                //5m chg in perc
                t.percChg5m(percChg5m(tm));
                //
                // t.jolt(getJolt(tm));
                //t.jolt(getPureJolt(tm));
                t.jolt(getMaxMinJolt(tm));
                //rtnOnDay
                t.rtnOnDay(getRtnOnDay(tm));
                //strategy 1
                t.strategy1(computeStrategy1(tm));
                //strategy 2
                t.strategy2(computeStrategy2(tm));
                //strategy last time stamp
                t.lastStratTimestamp(computeLastTimestamp1(tm));

                //long curr = System.currentTimeMillis();
                getMaxMinJolt(tm);
                //System.out.println("get Jolt cost" + (System.currentTimeMillis()-curr));
            }
        } catch (Exception e) {
            System.out.println("issue here");
            //e.addSuppressed(e);
            e.printStackTrace();
        }

        return t;
    }
//		/** Called when the tab is first visited. */
//		@Override public void activated() {
//		}
//
//		/** Called when the tab is closed by clicking the X. */
//		@Override public void closed() {
//
//		}
//
//		@Override public void historicalData(Bar bar, boolean hasGaps) {
//			m_rows.add( bar);
//                        System.out.println("historical data coming");
//		}
//		
//		@Override public void historicalDataEnd() {
//			fire();
//		}
//
//		@Override public void realtimeBar(Bar bar) {
//			m_rows.add( bar); 
//			fire();
//		}
//		

    private static void fire() {
        SwingUtilities.invokeLater(() -> {
            m_model.fireTableDataChanged();
        });
    }

    class BarModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNames.size();
            //return hm.size();
        }

        @Override
        public int getColumnCount() {
            return 22;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "T";
                case 1:
                    return "Last P";
                case 2:
                    return "MaxDay";
                case 3:
                    return "MinDay";
                case 4:
                    return "MaxDayT";
                case 5:
                    return "MinDayT";
                case 6:
                    return "MaxAMT";
                case 7:
                    return "MinAMT";
                case 8:
                    return "maxD";
                //case 9: return "maxDD2";
                case 9:
                    return "size";
                case 10:
                    return "Actvty";
                case 11:
                    return "MaxRep";
                case 12:
                    return "%ile";
                case 13:
                    return "%1M";
                case 14:
                    return "%3M";
                case 15:
                    return "%5M";
                case 16:
                    return "jolt";
                case 17:
                    return "RtnDay";
                case 18:
                    return "Strat1";
                case 19:
                    return "strat2";
                case 20:
                    return "LastT";
                case 21:
                    return "name";
                //case 6: return "WAP";
                default:
                    return null;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            //Bar row = m_rows.get( rowIn);
            //System.out.println ( " try to get analysis table value @ " + rowIn + " " + col);
            //System.out.println("symbol name in analysis is " + symbolNames.get(rowIn));

            switch (col) {
                //case 0: return symbolNames.get(rowIn);

                case 0:
                    return symbolNames.get(rowIn);
                case 1:
                    return hm.get(symbolNames.get(rowIn)).lastPrice();
                case 2:
                    return hm.get(symbolNames.get(rowIn)).maxDay();
                case 3:
                    return hm.get(symbolNames.get(rowIn)).minDay();
                case 4:
                    return hm.get(symbolNames.get(rowIn)).maxDayT();
                case 5:
                    return hm.get(symbolNames.get(rowIn)).minDayT();
                case 6:
                    return hm.get(symbolNames.get(rowIn)).maxAMT();
                case 7:
                    return hm.get(symbolNames.get(rowIn)).minAMT();
                case 8:
                    return hm.get(symbolNames.get(rowIn)).maxDrawDown();
                case 9:
                    return Math.round((Optional.ofNullable(sizeMapCopy.get(symbolNames.get(rowIn))).orElse(0))
                            * (Optional.ofNullable(hm.get(symbolNames.get(rowIn)).minDay()).orElse(0.0)) / 1000000);
                case 10:
                    return hm.get(symbolNames.get(rowIn)).activity();
                case 11:
                    return hm.get(symbolNames.get(rowIn)).maxRep();
                case 12:
                    return hm.get(symbolNames.get(rowIn)).percentile();
                case 13:
                    return hm.get(symbolNames.get(rowIn)).percChg1m();
                case 14:
                    return hm.get(symbolNames.get(rowIn)).percChg3m();
                case 15:
                    return hm.get(symbolNames.get(rowIn)).percChg5m();
                case 16:
                    return hm.get(symbolNames.get(rowIn)).jolt();
                case 17:
                    return hm.get(symbolNames.get(rowIn)).rtnOnDay();
                case 18:
                    return hm.get(symbolNames.get(rowIn)).strategy1();
                case 19:
                    return hm.get(symbolNames.get(rowIn)).strategy2();
                case 20:
                    return hm.get(symbolNames.get(rowIn)).lastStratTimestamp();
                case 21:
                    return hm.get(symbolNames.get(rowIn)).longName();
                default:
                    return null;
            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return Integer.class;
                case 1:
                    return Double.class;
                case 2:
                    return Double.class;
                case 3:
                    return Double.class;
                case 4:
                    return LocalTime.class;
                case 5:
                    return LocalTime.class;
                case 6:
                    return LocalTime.class;
                case 7:
                    return LocalTime.class;
                case 8:
                    return Integer.class;
                case 9:
                    return Double.class;
                case 10:
                    return Integer.class;
                case 11:
                    return Integer.class;
                case 12:
                    return Integer.class;
                case 13:
                    return Integer.class;
                case 14:
                    return Integer.class;
                case 15:
                    return Integer.class;
                case 16:
                    return Integer.class;
                case 17:
                    return Double.class;
                case 18:
                    return Boolean.class;
                case 19:
                    return Boolean.class;
                case 20:
                    return LocalTime.class;
                case 21:
                    return String.class;
                default:
                    return String.class;
            }
        }
    }
}
