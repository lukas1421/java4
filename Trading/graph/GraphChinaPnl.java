package graph;

import historical.HistChinaStocks;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.DoubleBinaryOperator;
import java.util.function.ToDoubleFunction;

public class GraphChinaPnl<T extends Temporal> extends JComponent implements GraphFillable {


    String name = "";
    String chineseName = "";

    volatile NavigableMap<T, Double> mtmMap = new ConcurrentSkipListMap<>();
    volatile NavigableMap<T, Double> tradeMap = new ConcurrentSkipListMap<>();
    volatile NavigableMap<T, Double> netMap = new ConcurrentSkipListMap<>();

    NavigableMap<LocalDate, Double> mtmByDay = new ConcurrentSkipListMap<>();
    NavigableMap<LocalDate, Double> mtmByAm = new ConcurrentSkipListMap<>();
    NavigableMap<LocalDate, Double> mtmByPm = new ConcurrentSkipListMap<>();

    int averagePerc ;
    int deltaWeightedAveragePerc;


    NavigableMap<T, Double> deltaMap;

    double max;
    double min;
    int height;

    public static final int WIDTH_PNL = 4;

    public GraphChinaPnl() {
        mtmMap = new ConcurrentSkipListMap<>();
        tradeMap = new ConcurrentSkipListMap<>();
        netMap = new ConcurrentSkipListMap<>();

    }


    public void setMtm(NavigableMap<T, Double> input) {
        mtmMap = input;
    }

    public void setTrade(NavigableMap<T, Double> input) {
        tradeMap = input;
        //System.out.println(" print trade map in graph china pnl " + tradeMap);
    }

    public void setNet(NavigableMap<T, Double> input) {
        netMap = input;
    }

    public void setWeekdayMtm(NavigableMap<LocalDate, Double> d, NavigableMap<LocalDate, Double> am, NavigableMap<LocalDate, Double> pm) {
        mtmByDay = d;
        mtmByAm = am;
        mtmByPm = pm;
    }

    public void setAvgPerc(int p) {
        averagePerc = p;
    }

    public void setDeltaWeightedAveragePerc(int p) {
        deltaWeightedAveragePerc = p;
    }



    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);
        g2.setFont(g.getFont().deriveFont(20F));

        int x = 5;
        int last = 0;
        int close = 0;
        min = getMin();
        max = getMax();
        height = (int) (getHeight() * 0.7);

        g2.setColor(Color.RED);

        for (T lt : mtmMap.keySet()) {
            close = getY(mtmMap.floorEntry(lt).getValue());
            last = (last == 0) ? close : last;
            g.drawLine(x, last, x + WIDTH_PNL, close);
            last = close;

            //System.out.println(" mtm map lt " + lt + " first key " + mtmMap.firstKey());
            if (lt.equals(mtmMap.firstKey())) {
                g.drawString(lt.toString(), x, getHeight() - 10);
            } else if (lt.equals(mtmMap.lastKey())) {
                g.drawString(lt.toString(), x, getHeight() - 10);
            } else {
                if (lt.getClass() == LocalDateTime.class) {
                    LocalDateTime t = (LocalDateTime) lt;
                    LocalDateTime lowerT = (LocalDateTime) mtmMap.lowerKey(lt);
                    if (t.toLocalDate().getDayOfMonth() != lowerT.toLocalDate().getDayOfMonth()) {
                        g.drawString(Integer.toString(t.toLocalDate().getDayOfMonth()), x, getHeight() - 10);
                    }
                }
            }

            if (lt.equals(mtmMap.lastKey())) {
                g.drawString("Mtm: " + Math.round(mtmMap.lastEntry().getValue()), x + 10, close);
            }

            x += WIDTH_PNL;
        }


        x = 5;
        last = 0;
        g.setColor(Color.BLUE);


        for (T lt : tradeMap.keySet()) {
            close = getY(tradeMap.floorEntry(lt).getValue());
            last = (last == 0) ? close : last;
            g.drawLine(x, last, x + WIDTH_PNL, close);
            last = close;
            x += WIDTH_PNL;

            try {
                if (lt.equals(tradeMap.lastKey())) {
                    g.drawString("Trade: " + Math.round(tradeMap.lastEntry().getValue()), x - 10, close);
                }
            } catch (Exception ex) {
                System.out.println(" trade map last key issue : size is " + tradeMap.size() + " " + tradeMap);
            }

            if (mtmMap.size() == 0) {
                if (lt.equals(tradeMap.firstKey())) {
                    g.drawString(lt.toString(), x, getHeight() - 10);
                } else if (lt.equals(tradeMap.lastKey())) {
                    g.drawString(lt.toString(), x, getHeight() - 10);
                } else {
                    if (lt.getClass() == LocalDateTime.class) {
                        LocalDateTime t = (LocalDateTime) lt;
                        LocalDateTime lowerT = (LocalDateTime) tradeMap.lowerKey(lt);
                        if (t.toLocalDate().getDayOfMonth() != lowerT.toLocalDate().getDayOfMonth()) {
                            g.drawString(Integer.toString(t.toLocalDate().getDayOfMonth()), x, getHeight() - 10);
                        }
                    }
                }
            }
        }


        x = 5;
        last = 0;
        g.setColor(new Color(50, 150, 0));
        if (netMap.size() > 0) {
            //System.out.println(" checked size for  " + name);

            for (T lt : netMap.keySet()) {
                //System.out.println(" name current lt is " + name + " " + lt + " net map size " + netMap.size());
                //System.out.println(" current lt is " + lt + " " + netMap.floorEntry(lt));

                try {
                    close = getY(netMap.floorEntry(lt).getValue());
                } catch (Exception ex) {
                    System.out.println(" get Y netmap wrong " + lt + name);
                }

                last = (last == 0) ? close : last;
                g.drawLine(x, last, x + WIDTH_PNL, close);
                last = close;
                x += WIDTH_PNL;


                try {
                    if (netMap.get(lt) == Utility.reduceMap(Math::max, netMap) &&
                            lt.equals(getEarliestT2(netMap, Math::max))) {

                        g.drawString("H:" + ((LocalDateTime) lt).toLocalTime().toString(), x, Math.max(15, last - 10));
                    } else if (netMap.get(lt) ==
                            Utility.reduceMap(Math::min, netMap)
                            && lt.equals(getEarliestT2(netMap, Math::min))) {

                        g.drawString("L:" + ((LocalDateTime) lt).toLocalTime().toString(), x, Math.min(last + 10, getHeight() - 10));
                    }
                } catch (Exception ex) {
                    System.out.println(" netmap reducing map for max or min  " + lt + " name " + name + " net map size is " + netMap.size());
                }

                try {
                    if (lt.equals(netMap.lastKey())) {
                        g.drawString("Net: " + Math.round(netMap.lastEntry().getValue()), x - 150, 15);
                    }
                } catch (Exception ex) {
                    System.out.println(" lt equals net map last key issue " + lt + " name " + name);
                }

            }
        }

        int temp = 20;
        for(Map.Entry e:mtmByDay.entrySet()) {
            temp = temp +20;
            g2.drawString(e.getKey().toString() + ": " + Math.round((Double)e.getValue()), getWidth()*6/8, temp);
        }
        g2.drawString("Total:         "
                +Math.round(mtmByDay.entrySet().stream().mapToDouble(Map.Entry::getValue).sum()), getWidth()*6/8, temp+20);

        temp = 20;
        for(Map.Entry e:mtmByAm.entrySet()) {
            temp = temp +20;
            g2.drawString(""+Math.round((Double)e.getValue()), getWidth()*7/8, temp);
        }
        g2.drawString(""+Math.round(mtmByAm.entrySet().stream().mapToDouble(Map.Entry::getValue).sum()), getWidth()*7/8, temp+20);

        temp = 20;
        for(Map.Entry e:mtmByPm.entrySet()) {
            temp = temp +20;
            g2.drawString(""+Math.round((Double)e.getValue()), getWidth()*15/16, temp);
        }
        g2.drawString(""+ Math.round(mtmByPm.entrySet().stream().mapToDouble(Map.Entry::getValue).sum()), getWidth()*15/16, temp+20);

        g.setColor(Color.black);
        g2.setFont(g.getFont().deriveFont(20F));
        g2.drawString(name, 0, 20);
        g2.drawString(chineseName, getWidth() / 10, 20);
        g2.drawString("" +averagePerc, getWidth()*5/8, 20);
        g2.drawString("" +deltaWeightedAveragePerc, getWidth()*5/8+30, 20);
        g2.drawString("" + (mtmMap.size() > 0 ? Math.round(mtmMap.lastEntry().getValue()) : 0.0), getWidth() * 2 / 10, 20);
        g2.drawString(Long.toString(Math.round(max)), getWidth() - 60, 20);
        g2.drawString(Long.toString(Math.round(min)), getWidth() - 60,getHeight() - 20);
    }

    private int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height;
        return height - (int) val + 20;
    }

    private double getMin() {
        return Utility.reduceMap(Math::min, mtmMap, tradeMap, netMap);
    }

    private double getMax() {
        return Utility.reduceMap(Math::max, mtmMap, tradeMap, netMap);
    }

    static public <T> T getEarliestT(NavigableMap<T, Double> mp, ToDoubleFunction<NavigableMap<T, Double>> f) {
        double target = f.applyAsDouble(mp);
        return mp.entrySet().stream().filter(e -> e.getValue() == target).findFirst().map(Map.Entry::getKey).orElse(mp.firstKey());
    }

    private static <T> T getEarliestT2(NavigableMap<T, Double> mp, DoubleBinaryOperator b) {
        if (mp.size() > 0) {
            double target = Utility.reduceMap(b, mp);
            return mp.entrySet().stream().filter(e -> e.getValue() == target).findFirst().map(Map.Entry::getKey).orElse(mp.firstKey());
        } else {
            throw new IllegalStateException(" map size wrong in get earliest t2");
        }
    }

    public void clearGraph() {
        SwingUtilities.invokeLater(() -> {
            name = "";
            chineseName = "";
            mtmMap = new ConcurrentSkipListMap<>();
            tradeMap = new ConcurrentSkipListMap<>();
            netMap = new ConcurrentSkipListMap<>();
            this.repaint();
        });
    }

    @Override
    public void fillInGraph(String nam) {
        if (nam.equals("")) {
            name = "PTF";
            chineseName = "PTF";
        } else {
            name = nam;
            chineseName = HistChinaStocks.nameMap.getOrDefault(nam, "");
        }
        SwingUtilities.invokeLater(() -> {
            this.repaint();
        });
    }


    @Override
    public void refresh() {
        //fillInGraph(name);
//        SwingUtilities.invokeLater(()-> {
//            this.repaint();
//        });
    }
}
