package graph;

import historical.HistChinaStocks;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class GraphChinaPnl<T extends Temporal> extends JComponent implements GraphFillable {


    String name = "";
    String chineseName = "";

    NavigableMap<T, Double> mtmMap = new TreeMap<>();
    NavigableMap<T, Double> tradeMap = new TreeMap<>();
    NavigableMap<T, Double> netMap = new TreeMap<>();
    NavigableMap<T, Double> tradePnlMap;
    NavigableMap<T, Double> netPnlMap;
    NavigableMap<T, Double> deltaMap;


    double max;
    double min;
    int height;

    public static final int WIDTH_PNL = 5;

    public GraphChinaPnl() {
        mtmMap = new TreeMap<>();

    }


    public void setMtm(NavigableMap<T, Double> input) {
        if (input.size() != 0) {
            mtmMap = input;
        } else {
            mtmMap = new TreeMap<>();
        }
    }

    public void setTrade(NavigableMap<T, Double> input) {
        if (input.size() != 0) {
            tradeMap = input;
        } else {
            tradeMap = new TreeMap<>();
        }
    }

    public void setNet(NavigableMap<T,Double> input) {
        if (input.size() != 0) {
            netMap = input;
        } else {
            netMap = new TreeMap<>();
        }
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

            if (mtmMap.get(lt) == max) {
                g.drawString("hi: " + ((LocalDateTime) lt).toLocalTime().toString(), x, Math.max(15, last - 10));
            } else if (mtmMap.get(lt) == min) {
                g.drawString(" lo:" + ((LocalDateTime) lt).toLocalTime().toString(), x, Math.min(last + 10, getHeight() - 5));

            }

            if(lt.equals(mtmMap.lastKey())) {
                g.drawString("Mtm: "+Math.round(mtmMap.lastEntry().getValue()),x+10,close);
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

            if(lt.equals(tradeMap.lastKey())) {
                g.drawString("Trade: "+Math.round(tradeMap.lastEntry().getValue()), x+10,close);
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
        g.setColor(Color.red);
        for (T lt : netMap.keySet()) {
            close = getY(tradeMap.floorEntry(lt).getValue());
            last = (last == 0) ? close : last;
            g.drawLine(x, last, x + WIDTH_PNL, close);
            last = close;
            x += WIDTH_PNL;

            if(lt.equals(netMap.lastKey())) {
                g.drawString("Net: "+Math.round(netMap.lastEntry().getValue()), x+200,close);
            }

        }


        g.setColor(Color.black);
        g2.setFont(g.getFont().deriveFont(20F));
        g2.drawString(name, 0, 20);
        g2.drawString(chineseName, getWidth() / 10, 20);
        g2.drawString("" + (mtmMap.size() > 0 ? Math.round(mtmMap.lastEntry().getValue()) : 0.0), getWidth() * 2 / 10, 20);
        g2.drawString(Long.toString(Math.round(max)), getWidth() - 60, 20);
        g2.drawString(Long.toString(Math.round(min)), getWidth() - 60, getHeight() - 20);


    }

    private int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height;
        return height - (int) val + 20;
    }

    private double getMin() {
        return Math.min(mtmMap.entrySet().stream().min(Comparator.comparingDouble(Map.Entry::getValue)).map(Map.Entry::getValue).orElse(0.0)
                , tradeMap.entrySet().stream().min(Comparator.comparingDouble(Map.Entry::getValue)).map(Map.Entry::getValue).orElse(0.0));
    }

    private double getMax() {

        return Math.max(mtmMap.entrySet().stream().max(Comparator.comparingDouble(Map.Entry::getValue)).map(Map.Entry::getValue).orElse(0.0),
                tradeMap.entrySet().stream().max(Comparator.comparingDouble(Map.Entry::getValue)).map(Map.Entry::getValue).orElse(0.0));
    }


    @Override
    public void fillInGraph(String nam) {
        //System.out.println(" nam in fill in graph " + nam);
        if (nam.equals("")) {
            name = "PTF";
            chineseName = "PTF";
        } else {
            name = nam;
            chineseName = HistChinaStocks.nameMap.getOrDefault(nam, "");
        }
        this.repaint();
    }

    @Override
    public void refresh() {
        //fillInGraph(name);
        this.repaint();
    }
}
