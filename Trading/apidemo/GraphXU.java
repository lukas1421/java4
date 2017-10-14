package apidemo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import static java.lang.Math.log;
import static java.lang.Math.round;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import static java.util.stream.Collectors.*;
import javax.swing.JComponent;

class GraphXU extends JComponent {

    private static final int WIDTH_XU = 4;
    private int height;
    private double min;
    private double max;
    private int close;
    private int last = 0;
    private double rtn = 0.0;
    NavigableMap<LocalTime, Double> tm = new ConcurrentSkipListMap<>();
    NavigableMap<LocalTime, Double> sinaIndexTm;
    NavigableMap<LocalTime, Integer> tmVol;
    String name;
    String chineseName;
    NavigableMap<LocalTime, Double> tm1 = new ConcurrentSkipListMap<>();
    Map<LocalTime, Double> nm;
    private boolean detailed = false;
    static final BasicStroke BS2 = new BasicStroke(2);

    public GraphXU() {
    }

    public void setSkipMap(NavigableMap<LocalTime, ? extends Number> tm) {
        if (tm1 != null) {
            tm1 = tm.entrySet().stream().collect(toMap(Entry::getKey, a -> a.getValue().doubleValue(), (a, b) -> a, ConcurrentSkipListMap::new));
        }
    }

    void setSkipMapD(NavigableMap<LocalTime, ? extends Number> tm) {
        if (tm != null) {
            tm1 = tm.entrySet().stream()
                    .filter(a -> a.getKey().isAfter(LocalTime.now().minusMinutes(20)))
                    .collect(toMap(Entry::getKey, a -> a.getValue().doubleValue(), (a, b) -> a, ConcurrentSkipListMap::new));
            detailed = true;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        LocalTime lastDrawT = LocalTime.of(9, 0);
        height = (int) (getHeight() - 50);
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(BS2);
        min = getMin();
        max = getMax();
        last = 0;
        rtn = getRtn();

        int x = 50;

        for (LocalTime lt : tm1.keySet()) {
            close = getY(tm1.get(lt));
            last = (last == 0) ? close : last;

            g.setColor(Color.black);
            g2.drawLine(x, last, x + 4, close);
            last = close;

            if (!detailed) {
                if (lt.getMinute() == 0 && lt.getSecond() == 0) {
                    g.drawString(Integer.toString(lt.getHour()), x, getHeight() - 25);
                }
                if (lt.getMinute() == 30 && lt.getSecond() == 0) {
                    g.drawString(Integer.toString(lt.getHour()) + ":30", x, getHeight() - 25);
                }
            }
            x += WIDTH_XU;

            if (detailed) {
                g.setColor(Color.black);
                if (lastDrawT.plusMinutes(5).isBefore(tm1.lastKey())) {
                    if (lt.isAfter(lastDrawT.plusMinutes(5))) {
                        g.drawString((Integer.toString(lt.getHour()) + ":" + Integer.toString(lt.getMinute())), x, getHeight() - 25);
                        if (lastDrawT.plusMinutes(5).isBefore(tm1.lastKey())) {
                            lastDrawT = lt;
                        }
                    }
                }
            }
        }

        if (tm1.size() > 0) {
            g.drawString(tm1.firstKey().toString(), 10, getHeight() - 10);
            g.drawString(tm1.lastKey().toString(), getWidth() - 60, getHeight() - 10);
            g.drawString(Double.toString(max), 0, 15);
            g.drawString(Double.toString(min), 0, getHeight() - 50);
            g.drawString(Double.toString(Math.round(100d * (max + min)) / 200d), 0, (getHeight() - 35) / 2);
            g.drawString(Double.toString(tm1.lastEntry().getValue()), getWidth() - 160, getHeight() - 10);
            g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.3F));
            g.drawString(this.getName() + "       " + Double.toString(tm1.lastEntry().getValue()), getWidth() / 2 - 60, 20);
            g.setFont(g.getFont().deriveFont(30F));
            g.drawString(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), getWidth() - 200, 30);
        }
    }

    /**
     * Convert bar value to y coordinate.
     */
    private int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
        return height - (int) val + 5;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1000, 50);
    }

    private double getMin() {
        return (tm1.size() > 0) ? tm1.entrySet().stream().min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0) : 0.0;
    }

    private double getMax() {
        return (tm1.size() > 0) ? tm1.entrySet().stream().max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0) : 0.0;
    }

    private double getRtn() {
        return tm1.size() > 0 ? round(log(tm1.lastEntry().getValue() / tm1.firstEntry().getValue()) * 1000d) / 10d : 0.0;
    }

}
