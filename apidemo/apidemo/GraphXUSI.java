package apidemo;

import static apidemo.ChinaStockHelper.getStr;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.*;
import javax.swing.JComponent;

public class GraphXUSI extends JComponent {

    static final int WIDTH_XU = 4;
    int height;
    double minXU;
    double maxXU;
    double minSI;
    double maxSI;
    int close;
    int close1;
    double rtn = 0;
    NavigableMap<LocalTime, Double> xu = new ConcurrentSkipListMap<>();
    NavigableMap<LocalTime, Double> sinaIndexTm;
    NavigableMap<LocalTime, Integer> tmVol;
    String name;
    String chineseName;
    private boolean detailed = false;
    //private boolean drawable = true;
    private double openXU = 0.0;
    private double openSI = 0.0;
    Font orig;
    static final LocalTime AM900 = LocalTime.of(9, 0);
    NavigableMap<LocalTime, Double> sina = new ConcurrentSkipListMap<>();

    GraphXUSI() {
        name = "";
        chineseName = "";
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1000, 100);
    }

    void setSkipMap(NavigableMap<LocalTime, SimpleBar> xuIn, NavigableMap<LocalTime, SimpleBar> siIn) {
        if (xuIn != null && siIn != null) {
            xu = xuIn.entrySet().stream()
                    .collect(Collectors.toMap(Entry::getKey, a -> a.getValue().getClose(), (a, b) -> a, ConcurrentSkipListMap::new));
            sina = siIn.entrySet().stream()
                    .collect(toMap(Entry::getKey, a -> a.getValue().getClose(), (a, b) -> a, ConcurrentSkipListMap::new));
            openXU = Optional.ofNullable(xuIn.ceilingEntry(AM900)).map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            openSI = SinaStock.OPEN;
        }
        repaint();
    }

    void setSkipMapD(NavigableMap<LocalTime, SimpleBar> xu, NavigableMap<LocalTime, SimpleBar> si) {
        if (xu != null && si != null && xu.size() > 0 && si.size() > 0) {
            this.xu = xu.entrySet().stream().filter(a -> a.getKey().isAfter(LocalTime.now().minusMinutes(20)))
                    .collect(toMap(Entry::getKey, e -> e.getValue().getClose(), (a, b) -> a, ConcurrentSkipListMap::new));

            sina = si.entrySet().stream().filter(a -> a.getKey().isAfter(LocalTime.now().minusMinutes(30)))
                    .collect(toMap(Entry::getKey, e -> e.getValue().getClose(), (a, b) -> a, ConcurrentSkipListMap::new));

            openXU = Optional.ofNullable(xu.ceilingEntry(AM900)).orElse(xu.firstEntry()).getValue().getOpen();
            openSI = SinaStock.OPEN;

            detailed = true;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(2));
        height = (int) (getHeight() - 50);
        minXU = getMin(xu);
        minSI = getMin(sina);
        maxXU = getMax(xu);
        maxSI = getMax(sina);

        int last = 0;
        int last1 = 0;
        orig = g.getFont();

        int x = 50;
        LocalTime lastDrawT = LocalTime.of(9, 0);

        for (LocalTime lt : xu.keySet()) {
            close = getYXU(xu.get(lt));
            last = (last == 0) ? close : last;

            g.setColor(Color.black);

            g.setFont(orig.deriveFont(orig.getSize() * 1.3F));
            g2.drawLine(x, last, x + WIDTH_XU, close);
            last = close;

            if (!detailed) {
                if (lt.getMinute() == 0 && lt.getSecond() == 0) {
                    g.drawString(lt.truncatedTo(ChronoUnit.HOURS).toString(), x, getHeight() - 25);
                }
                if (lt.getMinute() == 30 && lt.getSecond() == 0) {
                    g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 25);
                }
            }

            if (sina.containsKey(lt)) {
                close1 = getYSI(sina.get(lt));
                last1 = (last1 == 0) ? close1 : last1;
                g.setColor(Color.red);
                g.drawLine(x, last1, x + WIDTH_XU, close1);

                last1 = close1;
            }

            if (detailed) {
                g.setColor(Color.black);
                if (lastDrawT.plusMinutes(5).isBefore(xu.lastKey())) {
                    if (lt.isAfter(lastDrawT.plusMinutes(5))) {
                        g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 25);

                        if (lastDrawT.plusMinutes(5).isBefore(xu.lastKey())) {
                            lastDrawT = lt;
                        }
                    }
                }
            }
            x += WIDTH_XU;
        }

        if (xu.size() > 0 & sina.size() > 0) {
            g.setColor(Color.black);
            g.drawString(xu.lastKey().toString(), x, getHeight() - 10);
            g.drawString(Double.toString(Math.round(maxXU)), 0, 15);
            g.drawString(Double.toString(Math.round(minXU)), 0, getHeight() - 50);
            g.drawString(Double.toString(Math.round((maxXU + minXU) / 2d)), 0, (getHeight() - 35) / 2);
            g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.3F));

            try {
                g.drawString(getStr("FUT:", xu.lastEntry().getValue()), getWidth() / 2 - 120, 20);
                g.drawString("XU%  " + (Math.round(10000d * (xu.lastEntry().getValue() / openXU - 1)) / 100d) + "    ", getWidth() / 2 + 300, 20);
                g.drawString("P/D: " + (Math.round(10000d * (xu.lastEntry().getValue() / (sina.lastEntry().getValue()) - 1)) / 100d), getWidth() / 2 + 190, 20);
                g.setColor(Color.RED);
                g.drawString("Index: " + Double.toString(Math.round(sina.lastEntry().getValue())), getWidth() / 2 + 30, 20);
                g.drawString("Index%  " + (Math.round(10000d * (sina.lastEntry().getValue() / openSI - 1)) / 100d) + "   ", getWidth() / 2 + 420, 20);
                g.setColor(Color.black);
            } catch (Exception ex) {
                ex.printStackTrace();
                System.out.println("xua is " + xu);
                System.out.println("sina is " + sina);
            }

            g.setFont(orig.deriveFont(orig.getSize() * 1.3F));
        }

        if (sina.size() > 0) {
            g.drawString(Double.toString(Math.round(maxSI)), getWidth() - 60, 15);
            g.drawString(Double.toString(Math.round(minSI)), getWidth() - 60, getHeight() - 50);
            g.drawString(Double.toString(Math.round((maxSI + minSI) / 2d)), getWidth() - 60, (getHeight() - 35) / 2);
        }
    }

    /**
     * Convert bar value to y coordinate.
     */
    private int getYXU(double v) {
        if (maxXU - minXU > 0.0001) {
            double span = maxXU - minXU;
            double pct = (v - minXU) / span;
            double val = pct * height + .5;
            return height - (int) val + 5;
        } else {
            return height / 2;
        }
    }

    private int getYSI(double v) {
        if (maxSI - minSI > 0.0001) {
            double span = maxSI - minSI;
            double pct = (v - minSI) / span;
            double val = pct * height + .5;
            return height - (int) val + 5;
        } else {
            return height / 2;
        }
    }

    double getMin(NavigableMap<LocalTime, Double> tm) {
        return (tm.size() > 0) ? tm.entrySet().stream().min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0) : 0.0;
    }

    double getMax(NavigableMap<LocalTime, Double> tm) {
        return (tm.size() > 0) ? tm.entrySet().stream().max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0) : 0.0;
    }
}
