package graph;

import apidemo.XU;
import auxiliary.SimpleBar;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import static apidemo.ChinaStock.closeMap;
import static graph.GraphHelper.*;
import static java.util.Optional.ofNullable;
import static utility.Utility.roundDownToN;

public class GraphBarGen extends JComponent implements MouseMotionListener, MouseListener {

    int height;
    double min;
    double max;
    double maxRtn;
    double minRtn;
    int last = 0;
    double rtn = 0;
    NavigableMap<LocalDateTime, SimpleBar> tm;
    String name;
    String chineseName;
    private String bench;
    private double sharpe;
    LocalTime maxAMT;
    LocalTime minAMT;
    volatile int size;
    private static final BasicStroke BS3 = new BasicStroke(3);
    //private Predicate<? super Map.Entry<LocalTime, ?>> graphBarDispPred;

    private volatile int mouseXCord = Integer.MAX_VALUE;
    private volatile int mouseYCord = Integer.MAX_VALUE;


    GraphBarGen() {
        name = "";
        chineseName = "";
        maxAMT = LocalTime.of(9, 30);
        minAMT = Utility.AMOPENT;
        this.tm = new ConcurrentSkipListMap<>();
        //graphBarDispPred = p;
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public void setNavigableMap(NavigableMap<LocalTime, SimpleBar> tm) {
        this.tm = (tm != null) ? tm.entrySet().stream().filter(e -> !e.getValue().containsZero())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u,
                        ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = getHeight() - 70;
        min = getMin(tm);
        max = getMax(tm);
        minRtn = getMinRtn(tm);
        maxRtn = getMaxRtn(tm);
        last = 0;
        rtn = getRtn(tm);

        int x = 5;
        for (LocalTime lt : tm.keySet()) {
            int openY = getY(tm.floorEntry(lt).getValue().getOpen());
            int highY = getY(tm.floorEntry(lt).getValue().getHigh());
            int lowY = getY(tm.floorEntry(lt).getValue().getLow());
            int closeY = getY(tm.floorEntry(lt).getValue().getClose());

            if (closeY < openY) {
                g.setColor(new Color(0, 140, 0));
                g.fillRect(x, closeY, 3, openY - closeY);
            } else if (closeY > openY) {
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
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11
                        && lt.getMinute() == 30)) {
                    g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 40);
                }
            }
            if (roundDownToN(mouseXCord, XU.graphBarWidth.get()) == x - 5) {
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
                g.drawString(lt.toString() + " " + Math.round(100d * tm.floorEntry(lt).getValue().getClose()) / 100d, x, lowY + (mouseYCord < closeY ? -20 : +20));
                g.drawOval(x + 2, lowY, 5, 5);
                g.fillOval(x + 2, lowY, 5, 5);
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));

            }

            x += XU.graphBarWidth.get();
        }

        if (mouseXCord > x && mouseXCord < getWidth() && tm.size() > 0) {

            int lowY = getY(tm.lastEntry().getValue().getLow());
            int closeY = getY(tm.lastEntry().getValue().getClose());
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
            g.drawString(tm.lastKey().toString() + " " +
                            Math.round(100d * tm.lastEntry().getValue().getClose()) / 100d,
                    x, lowY + (mouseYCord < closeY ? -10 : +10));
            g.drawOval(x + 2, lowY, 5, 5);
            g.fillOval(x + 2, lowY, 5, 5);
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
        }

        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(BS3);

        g2.drawString(Double.toString(minRtn) + "%", getWidth() - 40, getHeight() - 33);
        g2.drawString(Double.toString(maxRtn) + "%", getWidth() - 40, 15);
        //g2.drawString(Double.toString(ChinaStock.getCurrentMARatio(name)),getWidth()-40, getHeight()/2);

        if (!ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }
        //add bench here
        g2.drawString(Double.toString(getLast()) + " (" + (Math.round(100d * closeMap.getOrDefault(name, 0.0))) / 100d + ")"
                , getWidth() * 3 / 8, 15);

        g2.drawString("P%:" + Integer.toString(getCurrentPercentile()), getWidth() * 9 / 16, 15);
        g2.drawString("涨:" + Double.toString(getRtn()) + "%", getWidth() * 21 / 32, 15);
        g2.drawString("高 " + (getAMMaxT()), getWidth() * 12 / 16, 15);
        //g2.drawString("低 " + (getAMMinT()), getWidth() * 7 * 8, 15);
        g2.drawString("夏 " + sharpe, getWidth() * 7 / 8, 15);

        //below
        g2.drawString("开 " + Double.toString(getRetOPC()), 5, getHeight() - 25);
        g2.drawString("一 " + Double.toString(getFirst1()), getWidth() / 9, getHeight() - 25);
        g2.drawString("量 " + Long.toString(getSize1()), 5, getHeight() - 5);
        g2.drawString("位Y " + Integer.toString(getCurrentMaxMinYP()), getWidth() / 9, getHeight() - 5);
        g2.drawString("十  " + Double.toString(getFirst10()), getWidth() / 9 + 75, getHeight() - 25);
        g2.drawString("V比 " + Double.toString(getSizeSizeYT()), getWidth() / 9 + 75, getHeight() - 5);

    }

    int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
        return height - (int) val + 20;
    }


    @Override
    public void mouseClicked(MouseEvent mouseEvent) {

    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseEntered(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseExited(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {

    }
}
