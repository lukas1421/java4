package graph;

import apidemo.SimpleBar;
import apidemo.XUTrader;

import static apidemo.ChinaStock.AMOPENT;
import static apidemo.ChinaStock.BAR_HIGH;
import static apidemo.ChinaStock.BAR_LOW;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import static java.lang.Math.abs;
import static java.lang.Math.log;
import static java.lang.Math.round;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NavigableMap;
import static java.util.Optional.ofNullable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toMap;
import javax.swing.JComponent;

public class GraphBarGen extends JComponent {

    static final int WIDTH_BAR = 5;
    int height;
    double min;
    double max;
    double maxRtn;
    double minRtn;
    int closeY;
    int highY;
    int lowY;
    int openY;
    int last = 0;
    double rtn = 0;
    NavigableMap<LocalTime, SimpleBar> tm;
    String name;
    String chineseName;
    String bench;
    LocalTime maxAMT;
    LocalTime minAMT;
    volatile int size;
    static final BasicStroke BS3 = new BasicStroke(3);
    int wtdP;

    public GraphBarGen(NavigableMap<LocalTime, SimpleBar> tm) {
        this.tm = (tm != null) ? tm.entrySet().stream().filter(e -> !e.getValue()
                .containsZero()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (u, v) -> u, ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

    public GraphBarGen() {
        name = "";
        chineseName = "";
        maxAMT = LocalTime.of(9, 30);
        minAMT = AMOPENT;
        this.tm = new ConcurrentSkipListMap<>();
    }

    public void setNavigableMap(NavigableMap<LocalTime, SimpleBar> tm) {
        this.tm = (tm != null) ? tm.entrySet().stream().filter(e -> !e.getValue().containsZero())
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u,
                        ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

    public NavigableMap<LocalTime, SimpleBar> getNavigableMap() {
        return this.tm;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setChineseName(String s) {
        this.chineseName = s;
    }

    public void setSize1(long s) {
        this.size = (int) s;
    }

    public void setBench(String s) {
        this.bench = s;
    }

    public void fillInGraph(NavigableMap<LocalTime, SimpleBar> mp) {
        this.setNavigableMap(mp);
    }

    public void refresh() {
        this.setNavigableMap(tm);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = (int) (getHeight() - 70);
        min = getMin();
        max = getMax();
        minRtn = getMinRtn();
        maxRtn = getMaxRtn();
        last = 0;
        rtn = getReturn();

        int x = 5;
        for (LocalTime lt : tm.keySet()) {
            openY = getY(tm.floorEntry(lt).getValue().getOpen());
            highY = getY(tm.floorEntry(lt).getValue().getHigh());
            lowY = getY(tm.floorEntry(lt).getValue().getLow());
            closeY = getY(tm.floorEntry(lt).getValue().getClose());

            if (closeY < openY) {  //close>open
                g.setColor(new Color(0, 140, 0));
                g.fillRect(x, closeY, 3, openY - closeY);
            } else if (closeY > openY) { //close<open, Y is Y coordinates                    
                g.setColor(Color.red);
                g.fillRect(x, openY, 3, closeY - openY);
            } else {
                g.setColor(Color.black);
                g.drawLine(x, openY, x + 2, openY);
            }
            g.drawLine(x + 1, highY, x + 1, lowY);

            g.setColor(Color.black);
            if (lt.equals(tm.firstKey())) {
                g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 40);
            } else {
                if (lt.getHour() < 16 && (lt.getMinute() == 0 || lt.getMinute() % 15 == 0)) {
                    g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 40);
                }
            }
            x += WIDTH_BAR;
            //System.out.println(" time is " + lt + " x is " + x);

        }

        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(BS3);

        g2.drawString(Double.toString(minRtn) + "%", getWidth() - 40, getHeight() - 33);
        g2.drawString(Double.toString(maxRtn) + "%", getWidth() - 40, 15);
        //g2.drawString(Double.toString(ChinaStock.getCurrentMARatio(name)),getWidth()-40, getHeight()/2);
        g2.drawString("周" + Integer.toString(wtdP), getWidth() - 40, getHeight() / 2);

        if (!ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }
        if (!ofNullable(chineseName).orElse("").equals("")) {
            g2.drawString(chineseName, getWidth() / 8, 15);
        }
        if (!ofNullable(bench).orElse("").equals("")) {
            g2.drawString("(" + bench + ")", getWidth() * 2 / 8, 15);
        }

        //add bench here
        g2.drawString(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), 15, 15);
        g2.drawString(Double.toString(getReturn()) + "%", getWidth() / 8, 15);
        g2.drawString("开: " + Double.toString(getOpen()), getWidth() * 2 / 8, 15);
        g2.drawString(Double.toString(getLast()), getWidth() * 3 / 8, 15);
        g2.drawString("Pos: " + XUTrader.netPosition, getWidth() * 4 / 8, 15);
        g2.drawString("B: " + XUTrader.netBoughtPosition, getWidth() * 5 / 8, 15);
        g2.drawString("S: " + XUTrader.netSoldPosition, getWidth() * 6 / 8, 15);

        g2.setColor(new Color(0, 255 * (100 - wtdP) / 100, 0));
        //g2.fillRect(0,0, getWidth(), getHeight());
        g2.fillRect(getWidth() - 30, 20, 20, 20);
        g2.setColor(getForeground());

        if (XUTrader.showTrades) {
            XUTrader.tradesMap.entrySet().stream().forEach(e -> {
                //g.drawString(Integer.toString(e.getValue().getSize()), getXForLT(e.getKey()), getHeight()-20);
                if (e.getValue().getSize() > 0) {
                    g.setColor(Color.blue);

                    //g.drawString(Integer.toString(e.getValue().getSize()), getXForLT(e.getKey()), getYForLTBuy(e.getKey())+20);
                    int xCord = getXForLT(e.getKey());
                    //int yCord = getYForLTBuy(e.getKey());
                    int yCord = getY(e.getValue().getPrice());
                    g.drawPolygon(new int[]{xCord - 1, xCord, xCord + 1}, new int[]{yCord + 2, yCord, yCord + 2}, 3);

                } else {
                    g.setColor(Color.black);
                    //g.drawString(Integer.toString(e.getValue().getSize()), getXForLT(e.getKey()), getYForLTSell(e.getKey())-20);
                    int xCord = getXForLT(e.getKey());
                    //int yCord = getYForLTSell(e.getKey());
                    int yCord = getY(e.getValue().getPrice());
                    g.drawPolygon(new int[]{xCord - 1, xCord, xCord + 1}, new int[]{yCord - 3, yCord, yCord - 3}, 3);
                }
                System.out.println(e.getKey());
            });
        }
    }

    public int getXForLT(LocalTime t) {
        long timeDiff = ChronoUnit.MINUTES.between(LocalTime.of(9, 0), t);
        if (t.isAfter(LocalTime.of(11, 30))) {
            timeDiff = timeDiff - 90;
        }
        //System.out.println(" time in between " + timeDiff);
        return (int) (WIDTH_BAR * timeDiff + 5);
    }

    public int getYForLTSell(LocalTime t) {
        SimpleBar sb = (SimpleBar) XUTrader.xuData.floorEntry(t).getValue();
        if (sb.normalBar()) {
            return getY(sb.getHigh());
        } else {
            throw new IllegalArgumentException("BAGAYARO");
        }
    }

    public int getYForLTBuy(LocalTime t) {
        SimpleBar sb = (SimpleBar) XUTrader.xuData.floorEntry(t).getValue();
        if (sb.normalBar()) {
            return getY(sb.getLow());
        } else {
            throw new IllegalArgumentException("BAGAYARO");
        }
    }

    /**
     * Convert bar value to y coordinate.
     */
    public int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
        return height - (int) val + 20;
    }

    public double getMin() {
        return (tm.size() > 0) ? tm.entrySet().stream().min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow).orElse(0.0) : 0.0;
    }

    public double getMax() {
        return (tm.size() > 0) ? tm.entrySet().stream().max(BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;
    }

    public double getReturn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = tm.lastEntry().getValue().getClose();
            return (double) round((finalP / initialP - 1) * 10000d) / 100d;
        }
        return 0.0;
    }

    public double getMaxRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMax();
            return abs(finalP - initialP) > 0.0001 ? (double) round((finalP / initialP - 1) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    public double getMinRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(e -> e.getOpen()).orElse(0.0);
            double finalP = getMin();
            return (Math.abs(finalP - initialP) > 0.0001) ? (double) round(log(getMin() / initialP) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    public double getOpen() {
        return (tm.size() > 0) ? tm.firstEntry().getValue().getOpen() : 0.0;
    }

    public double getLast() {
        return (tm.size() > 0) ? tm.lastEntry().getValue().getClose() : 0.0;
    }
}
