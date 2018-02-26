package graph;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static utility.Utility.getStr;
import static utility.Utility.roundDownToN;

public class GraphOptionVol extends JComponent implements MouseMotionListener, MouseListener {


    NavigableMap<Double, Double> volSmileCall = new TreeMap<>();
    NavigableMap<Double, Double> volSmilePut = new TreeMap<>();
    private int mouseYCord = Integer.MAX_VALUE;
    private int mouseXCord = Integer.MAX_VALUE;

    public GraphOptionVol() {
        addMouseMotionListener(this);
        addMouseListener(this);
    }

    public void setVolSmile(NavigableMap<Double, Double> mp) {
        volSmileCall = mp;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (volSmileCall.size() > 0) {
            double minStrike = volSmileCall.keySet().stream().reduce(Math::min).orElse(0.0);
            double maxStrike = volSmileCall.keySet().stream().reduce(Math::max).orElse(0.0);
            double minVol = volSmileCall.values().stream().reduce(Math::min).orElse(0.0);
            double maxVol = volSmileCall.values().stream().reduce(Math::max).orElse(0.0);

            //double strikeRange = maxStrike - minStrike;
            //double heightRange = maxVol;

            int x = 5;
            int x_width = getWidth() / volSmileCall.size();

            int stepsOf10 = (int) Math.floor(maxVol * 10);
            int topY = (int) (getHeight() * 0.8);
            int stepSize = (int) (topY / stepsOf10);

            for (int i = 1; i != stepsOf10; i++) {
                System.out.println(getStr(" stepsof10, stepsize i ", stepsOf10, stepSize, i));
                g.drawString(Double.toString((double)i/10), 5, getY((double)i/10,maxVol,minVol));
            }


            //for(int i )

            for (Map.Entry e : volSmileCall.entrySet()) {
                int y = getY((double) e.getValue(), maxVol, minVol);
                g.drawOval(x, y, 5, 5);
                g.fillOval(x, y, 5, 5);
                g.drawString(e.getKey().toString(), x, getHeight() - 10);
                g.drawString(Integer.toString((int)(((double)e.getValue())*100d)), x, y + 20);

                if (roundDownToN(mouseXCord, x_width) == x - 5) {
                    g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 3F));
                    g.drawString(e.toString(), x, y);
                    g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.333F));
                }
                x = x + x_width;
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
