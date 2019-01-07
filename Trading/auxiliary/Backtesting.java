package auxiliary;

import api.TradingConstants;
import graph.Graph;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static java.util.stream.Collectors.toList;

//import java.util;s

public class Backtesting extends JPanel {

    private static ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>> mapCopy;
    private static ConcurrentHashMap<Integer, Strategy> hm = new ConcurrentHashMap<Integer, Strategy>();
    private static ArrayList<Integer> symbolNames = new ArrayList<Integer>();

    private int rowSelected;
    private int modelRow;

    private static boolean graphCreated = false;
    private Graph graph1 = new Graph();

    Backtesting() {
        mapCopy = new ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>>();

        try {

            List<Integer> numbers;

            numbers = Files.lines(Paths.get(TradingConstants.GLOBALPATH + "Table2.txt"))
                    .map(line -> line.split("\\s+"))
                    .flatMap(Arrays::stream)
                    .map(Integer::valueOf)
                    .distinct()
                    .collect(toList());

            symbolNames.addAll(numbers);
            numbers.forEach((value) -> {
                hm.put(value, new Strategy());
            });
            // System.out.println (" symbol names is " + symbolNames + "\t" );
            //System.out.println ( " symbol size is " + symbolNames.size());

        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }

        BarModel m_model = new BarModel();

        JTable tab = new JTable(m_model) {

            public Component prepareRenderer(TableCellRenderer renderer, int Index_row, int Index_col) {
                Component comp = super.prepareRenderer(renderer, Index_row, Index_col);

                if (isCellSelected(Index_row, Index_col)) {
                    rowSelected = Index_row;
                    modelRow = this.convertRowIndexToModel(Index_row);
                    comp.setBackground(Color.CYAN);

                    if (mapCopy.size() > 0) {
                        graph1.setNavigableMap(mapCopy.get(symbolNames.get(modelRow)), d->d, d->d==0.0);
                    }

                    if (this.getParent().getParent().getParent().getComponentCount() == 3) {
                        // System.out.print(" get component count " + this.getParent().getComponentCount());
                        //  System.out.print(" get parent component count " + this.getParent().getParent().getComponentCount());
                        // System.out.print(" get name of compoent 0 " + this.getParent().getParent().getParent().getComponent(0).getSymbol());
                        //   System.out.print(" get name of compoent 1 " + this.getParent().getParent().getParent().getComponent(1).getSymbol());
                        //  System.out.print(" get name of compoent 2 " + this.getParent().getParent().getParent().getComponent(2).getSymbol());

                        this.getParent().getParent().getParent().getComponent(2).repaint();
                        //this.getpa
                        //System.out.println(" repainting " + System.currentTimeMillis());
                    }
                } else if (Index_row % 2 == 0) {
                    comp.setBackground(Color.lightGray);
                } else {
                    comp.setBackground(Color.white);
                }
                return comp;
            }
        };

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 1100;
                return d;
            }
        };

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        scroll.setName("scroll");

        tab.setAutoCreateRowSorter(true);
        TableRowSorter<BarModel> sorter = (TableRowSorter<BarModel>) tab.getRowSorter();
        //sorter.setRowFilter(RowFilter.numberFilter(RowFilter.ComparisonType.NOT_EQUAL,0,10));

        JPanel jp = new JPanel();
        jp.setName("Top panel");

        //jp.setLayout(new FlowLayout());
        jp.setLayout(new BorderLayout());

        //
        JButton jb2 = new JButton("Graph");
        jp.add(jb2);

        jb2.addActionListener(al -> {

            if (mapCopy.size() > 0) {
                try {
                    System.out.println("mapcopy size " + mapCopy.size());
                    graph1.setNavigableMap(mapCopy.get(symbolNames.get(modelRow)),d->d,d-> d==0.0);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("incorrect symbol input");

                }

                System.out.println(" graphCreated is " + graphCreated);
                if (!graphCreated) {

                    JPanel graphPanel = new JPanel();

                    graphPanel.setLayout(new GridLayout(6, 1));

                    JScrollPane chartScroll = new JScrollPane(graph1) {
                        public Dimension getPreferredSize() {
                            Dimension d = super.getPreferredSize();
                            d.height = 250;
                            return d;
                        }
                    };

                    graphPanel.add(chartScroll);
                    graphPanel.setName("graph panel");

                    chartScroll.setName(" graph scrollpane");

                    add(graphPanel, BorderLayout.CENTER);
                    System.out.println("graph created");
                    graphCreated = true;
                    this.repaint();
                } else {
                    graph1.setNavigableMap(mapCopy.get(symbolNames.get(modelRow)), d->d, d->d==0.0);
                    //  System.out.println("model row is " + modelRow);
                    //  System.out.println("symbol names . get modelrow " + symbolNames.get(modelRow).toString());
                    //  System.out.println(mapCopy.get(symbolNames.get(modelRow)).toString());
                    repaint();
                }
                //  System.out.println("Graphing");
            }
        });

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        add(jp, BorderLayout.NORTH);
        //add(jp, BorderLayout.NORTH);  

    }
    //end

    Backtesting(ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>> map) {
        mapCopy = map;
    }

    public void testingStrategy() {
        int symb;
        for (Object o : mapCopy.keySet()) {
            symb = (int) o;
            hm.put(symb, new Strategy(computeAll(mapCopy.get(symb))));
        }
    }

//
    public static void compute(ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>> map) {
        mapCopy = map;
        int symb;
        Strategy st = new Strategy();
        //  System.out.println("mapcopy size is " + mapCopy.size());

        Iterator it = map.keySet().iterator();
        // System.out.println( " size of the map in analysis " + map.size());

        while (it.hasNext()) {
            symb = (int) it.next();
            // if (symb==877) {
            //  System.out.println("symbol being computed in analysis is***************************************** " + symb);
            //symbolNames.add(symb);
            //takes in a ConcurrentSkipListMap 
            try {
                //  System.out.println("symbol is " + symb);
                st = computeAll(mapCopy.get(symb));
            } catch (Exception e) {
                e.printStackTrace();

            }
            //  if (trait.activity()> Analysis.actThresh) {
            // System.out.println(" trait.activity is  " + trait.activity());
            //  System.out.println(" Analysis. actThresh" + Analysis.actThresh);
            hm.put(symb, st);

        }
    }

    private static Strategy computeAll(ConcurrentSkipListMap<LocalTime, Double> tm) {
        if (tm.size() > 1) {
            Iterator it = tm.keySet().iterator();
            double max = Double.MIN_VALUE;
            double min = Double.MAX_VALUE;
            LocalTime minTime = LocalTime.of(9, 30);
            LocalTime maxTime = LocalTime.of(9, 30);
            int percentile = 0;
            double maxDd = 0;
            double tempMaxDd = 0;
            double v;
            LocalTime k;
            ArrayList<Double> priceHist = new ArrayList<Double>(350);
            int activity = 0;
            double previous = 0;

            double dayMax = Analysis.getMaxDay(tm);;
            LocalTime dayMaxT = Analysis.getMaxDayT(tm);
            int dayDrawdown = Analysis.drawDownCompute(tm);
            //System.out.println ("day max is " + dayMax);

            LocalTime enter = LocalTime.of(9, 30);
            LocalTime exit = LocalTime.of(9, 30);
            double enterValue = 0;
            double exitValue = 0;
            Strategy st = new Strategy();
            long maxRep = 0;

            while (it.hasNext()) {
                k = (LocalTime) it.next();
                v = tm.get(k);

                //   System.out.println(" current value is " + v);
                if (previous != v) {
                    activity++;
                }

                if (v > max) {
                    max = v > max ? v : max;
                    maxTime = k;
                    tempMaxDd = 0;
                } else {
                    tempMaxDd = ((max - v) > tempMaxDd ? (max - v) : tempMaxDd);
                    maxDd = (tempMaxDd > maxDd ? tempMaxDd : maxDd);
                    //System.out.println("tempmaxDd" + tempMaxDd);
                    //System.out.println(" maxdd is " + maxDd);
                    //System.out.println(" max is " + max);
                }

                if (v < min) {
                    min = v;
                    minTime = k;
                }

                //if(v > max)
                percentile = (int) ((v - min) / (max - min) * 100);
                priceHist.add(v);

                //percentileList
                try {
                    //  System.out.println(" current time is " + k);
                    //  System.out.println(" min time is " + minTime.toString());
                    //  System.out.println(" max time is " + maxTime.toString());
                    //  System.out.println(" percentile is " + percentile);
                    //  System.out.println(" percentile before 6 is  " + priceHist.get(priceHist.size()-6) );
                    //  System.out.println(" drawdown is  " + (int)(maxDd/(max-min)*100));
                } catch (ArrayIndexOutOfBoundsException e) {
                    //    e.printStackTrace();
                }

                if (minTime.isBefore(LocalTime.of(10, 0))
                        && maxTime.isAfter(LocalTime.of(10, 30))
                        && percentile > 50
                        && k.isBefore(LocalTime.of(12, 1))
                        && (100 * (v - priceHist.get(priceHist.size() - 3)) / (max - min) > 10)
                        && (int) (maxDd / (max - min) * 100) < 50
                        && tempMaxDd != 0
                        && activity > 20) {
                    enter = k;
                    enterValue = v;
                    maxRep = Analysis.getMaxRep(tm.headMap(enter, true));
                    //tm.
                    // tm.head

                    //  System.out.println(" maxdd is " + maxDd);
                    //   System.out.println(" max is " + max);    
                    //  System.out.println(" min is " + min);
                    //exit = k; 
                }

                if (enter.isAfter(LocalTime.of(9, 30))) {
                    exit = tm.floorKey(LocalTime.of(12, 0));
                    exitValue = tm.get(exit);
                    //st.setStrategy(enter, exit,enterValue, exitValue, Math.log(exitValue/enterValue),dayMax,dayMaxT,dayDrawdown,maxRep );
                    //System.out.println(" enter " + enter);
                    break;

                }
                previous = v;
            }
            return st;
        }
        return new Strategy();
    }

    class BarModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNames.size();

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
                    return "Enter";
                case 2:
                    return "Exit";
                case 3:
                    return "Enter value";
                case 4:
                    return "Exit Value";
                case 5:
                    return "rtn";
                case 6:
                    return "day max";
                case 7:
                    return "day max time";
                case 8:
                    return "day drawdown";
                case 9:
                    return "max rep";
                default:
                    return null;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
//            switch( col) {
//                case 0: return symbolNames.get(rowIn);
//                case 1: return hm.get(symbolNames.get(rowIn)).getEnter();
//                case 2: return hm.get(symbolNames.get(rowIn)).getExit();
//                case 3: return hm.get(symbolNames.get(rowIn)).getEnterValue();
//                case 4: return hm.get(symbolNames.get(rowIn)).getExitValue();
//                case 5: return hm.get(symbolNames.get(rowIn)).getRtn();
//                case 6: return hm.get(symbolNames.get(rowIn)).getDayMax();
//                case 7: return hm.get(symbolNames.get(rowIn)).getDayMaxT();
//                case 8: return hm.get(symbolNames.get(rowIn)).getDayDrawdown();
//                case 9: return hm.get(symbolNames.get(rowIn)).getMaxRep();

            return null;
//            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return Integer.class;
                case 1:
                    return LocalTime.class;
                case 2:
                    return LocalTime.class;
                case 3:
                    return Double.class;
                case 4:
                    return Double.class;
                case 5:
                    return Double.class;
                case 6:
                    return Double.class;
                case 7:
                    return LocalTime.class;
                case 8:
                    return Long.class;
                default:
                    return String.class;

            }
        }
    }
}
