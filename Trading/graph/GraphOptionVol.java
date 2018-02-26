package graph;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static utility.Utility.*;

public class GraphOptionVol extends JComponent implements MouseMotionListener, MouseListener {


    private double currentPrice;
    private NavigableMap<Double, Double> volSmileFront = new TreeMap<>();
    private NavigableMap<Double, Double> volSmileBack = new TreeMap<>();
    private NavigableMap<Double, Double> volSmileThird = new TreeMap<>();

    private int mouseYCord = Integer.MAX_VALUE;
    private int mouseXCord = Integer.MAX_VALUE;

    public GraphOptionVol() {
        addMouseMotionListener(this);
        addMouseListener(this);
    }

    public void setCurrentPrice(double p) {
        currentPrice = p;
    }

    public void setVolSmileFront(NavigableMap<Double, Double> mp) {
        volSmileFront = mp;
    }

    public void setVolSmileBack(NavigableMap<Double, Double> mp) {
        volSmileBack = mp;
    }

    public void setVolSmileThird(NavigableMap<Double, Double> mp) {
        volSmileThird = mp;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (volSmileFront.size() > 0) {

            double minVol = minGen(volSmileFront.values().stream().reduce(Math::min).orElse(0.0),
                    volSmileBack.values().stream().reduce(Math::min).orElse(0.0),
                    volSmileThird.values().stream().reduce(Math::min).orElse(0.0));

            double maxVol = maxGen(volSmileFront.values().stream().reduce(Math::max).orElse(0.0),
                    volSmileBack.values().stream().reduce(Math::max).orElse(0.0),
                    volSmileThird.values().stream().reduce(Math::max).orElse(0.0));

            int x = 5;
            int x_width = getWidth() / volSmileFront.size();

            int stepsOf10 = (int) Math.floor(maxVol * 10);
            //int topY = (int) (getHeight() * 0.8);
            //int stepSize = (int) (topY / stepsOf10);

            for (int i = 1; i != stepsOf10; i++) {
                g.drawString(Double.toString((double) i / 10), 5, getY((double) i / 10, maxVol, minVol));
            }

            for (Map.Entry<Double, Double> e : volSmileFront.entrySet()) {
                int yFront = getY(e.getValue(), maxVol, minVol);
                int yBack = getY(interpolateVol(e.getKey(), volSmileBack), maxVol, minVol);
                int yThird = getY(interpolateVol(e.getKey(), volSmileThird), maxVol, minVol);

                g.drawOval(x, yFront, 5, 5);
                g.fillOval(x, yFront, 5, 5);
                String priceInPercent = Integer.toString((int) (e.getKey() / currentPrice * 100)) + "%";
                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));

                g.drawString(e.getKey().toString(), x, getHeight() - 20);
                g.drawString(priceInPercent, x, getHeight() - 5);
                g.drawString(Math.round(e.getValue() * 100d) + "", x + 10, yFront + 15);

                g.setColor(Color.blue);
                g.drawOval(x, yBack, 5, 5);
                g.fillOval(x, yBack, 5, 5);
                g.drawString(Math.round(interpolateVol(e.getKey(), volSmileBack) * 100d) + "", x + 10, yBack + 10);
                g.setColor(Color.black);

                g.setColor(Color.red);
                g.drawOval(x, yThird, 5, 5);
                g.fillOval(x, yThird, 5, 5);
                g.drawString(Math.round(interpolateVol(e.getKey(), volSmileThird) * 100d) + "", x, yThird + 20);
                g.setColor(Color.black);

                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.6666F));

                if (roundDownToN(mouseXCord, x_width) == x - 5) {
                    g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 3F));
                    g.drawString(e.toString(), x, yFront);
                    g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.333F));
                }
                x = x + x_width;
            }
        }
    }

    private double interpolateVol(double strike, NavigableMap<Double, Double> mp) {
        if (mp.containsKey(strike)) {
            return mp.get(strike);
        } else {
            if (strike >= mp.firstKey() && strike <= mp.lastKey()) {
                double higherKey = mp.ceilingKey(strike);
                double lowerKey = mp.floorKey(strike);
                return mp.get(lowerKey) + (strike - lowerKey) / (higherKey - lowerKey) * (mp.get(higherKey) - mp.get(lowerKey));
            } else {
                return 0.0;
            }
        }
    }

    int getY(double v, double maxV, double minV) {
        double span = maxV - minV;
        int height = (int) (getHeight() * 0.8);
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
        mouseYCord = Integer.MAX_VALUE;
        mouseXCord = Integer.MAX_VALUE;
        this.repaint();

    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseXCord = e.getX();
        mouseYCord = e.getY();
        System.out.println(" graph bar x mouse x is " + mouseXCord);
        this.repaint();

    }
}
