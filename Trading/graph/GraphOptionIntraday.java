package graph;

import auxiliary.SimpleBar;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static utility.Utility.reduceMapToDouble;
import static utility.Utility.roundDownToN;

public class GraphOptionIntraday extends JComponent implements MouseListener, MouseMotionListener {

    private NavigableMap<LocalDateTime, SimpleBar> tm = new ConcurrentSkipListMap<>();

    int height;
    double min;
    double max;
    private String graphTitle;
    private String ticker;
    private double strike;
    private LocalDate expiryDate;
    private String callPutFlag;

    private volatile int mouseXCord = Integer.MAX_VALUE;
    private volatile int mouseYCord = Integer.MAX_VALUE;

    public volatile int graphBarWidth = 3;

    public GraphOptionIntraday() {
        ticker = "";
        graphTitle = "";
        strike = 0.0;
        expiryDate = LocalDate.MIN;
        callPutFlag = "C";
    }

    public void setGraphTitle(String t) {
        graphTitle = t;
    }

    public void setMap(NavigableMap<LocalDateTime, SimpleBar> mapIn) {
        if (mapIn.size() > 0) {
            tm = mapIn;
        }
    }

    public void setTicker(String t) {
        ticker = t;
    }

    public void setNameStrikeExp(String name, double k, LocalDate exp, String flag) {
        ticker = name;
        strike = k;
        expiryDate = exp;
        callPutFlag = flag;
    }

    double getMin() {
        return (tm.size() > 0) ? reduceMapToDouble(tm, SimpleBar::getLow, Math::min) : 0.0;
        //tm.entrySet().stream().min(Utility.BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0)
    }

    double getMax() {
        return (tm.size() > 0) ? reduceMapToDouble(tm, SimpleBar::getHigh, Math::max) : 0.0;
        //tm.entrySet().stream().max(Utility.BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0)
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.black);

        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2.5F));
        g.drawString(" Intraday Vols ", 20, 30);
        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.4F));

        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
        g.drawString(ticker, getWidth() - 400, 20);
        g.drawString(strike + "", getWidth() - 400, 40);
        g.drawString(expiryDate.toString(), getWidth() - 400, 60);
        g.drawString(callPutFlag, getWidth() - 400, 80);
        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));

        height = getHeight() - 70;
        min = getMin();
        max = getMax();

        int last = 0;

        int x = 5;
        for (LocalDateTime lt : tm.keySet()) {
            int openY = getY(tm.floorEntry(lt).getValue().getOpen(), max, min);
            int highY = getY(tm.floorEntry(lt).getValue().getHigh(), max, min);
            int lowY = getY(tm.floorEntry(lt).getValue().getLow(), max, min);
            int closeY = getY(tm.floorEntry(lt).getValue().getClose(), max, min);

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

            if (roundDownToN(mouseXCord, graphBarWidth) == x - 5) {
                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));

                g.drawString(lt.toString() + " " + Math.round(100d * tm.floorEntry(lt).getValue().getClose()) / 100d
                        , x, lowY + (mouseYCord < closeY ? -20 : +20));

                g.drawOval(x + 2, lowY, 5, 5);
                g.fillOval(x + 2, lowY, 5, 5);
                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));

            }

            x += graphBarWidth;
        }

        if (mouseXCord > x && mouseXCord < getWidth() && tm.size() > 0) {

            int lowY = getY(tm.lastEntry().getValue().getLow(), max, min);
            int closeY = getY(tm.lastEntry().getValue().getClose(), max, min);
            g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
            g.drawString(tm.lastKey().toString() + " " +
                            Math.round(100d * tm.lastEntry().getValue().getClose()) / 100d,
                    x, lowY + (mouseYCord < closeY ? -10 : +10));
            g.drawOval(x + 2, lowY, 5, 5);
            g.fillOval(x + 2, lowY, 5, 5);
            g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
        }


    }

    int getY(double v, double maxV, double minV) {
        double span = maxV - minV;
        int height = (int) (getHeight() * 0.75);
        double pct = (v - minV) / span;
        double val = pct * height;
        return height - (int) val;
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
