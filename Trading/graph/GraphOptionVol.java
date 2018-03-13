package graph;

import apidemo.ChinaOptionHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static apidemo.ChinaOption.*;
import static apidemo.ChinaOptionHelper.getOptionExpiryDate;
import static utility.Utility.*;

public class GraphOptionVol extends JComponent implements MouseMotionListener, MouseListener {


    private String selectedOptionTicker = "";
    private String selectedCP = "";
    private double selectedStrike = 0.0;
    private double selectedVol = 0.0;
    private LocalDate selectedExpiry = LocalDate.MIN;

    private double currentPrice;
    private NavigableMap<Double, Double> volSmileFront = new TreeMap<>();
    private NavigableMap<Double, Double> volSmileBack = new TreeMap<>();
    private NavigableMap<Double, Double> volSmileThird = new TreeMap<>();
    private NavigableMap<Double, Double> volSmileFourth = new TreeMap<>();

    private NavigableMap<Double, Double> deltaMapFront = new TreeMap<>();
    private NavigableMap<Double, Double> deltaMapBack = new TreeMap<>();
    private NavigableMap<Double, Double> deltaMapThird = new TreeMap<>();
    private NavigableMap<Double, Double> deltaMapFourth = new TreeMap<>();

    private static Color color1Exp = Color.black;
    private static Color color2Exp = Color.blue;
    private static Color color3Exp = Color.red;
    private static Color color4Exp = Color.magenta;
    private static HashMap<LocalDate, Color> colorMap = new HashMap<>();


    private int mouseYCord = Integer.MAX_VALUE;
    private int mouseXCord = Integer.MAX_VALUE;

    public GraphOptionVol() {
        colorMap.put(getOptionExpiryDate(2018, Month.MARCH), Color.black);
        colorMap.put(getOptionExpiryDate(2018, Month.APRIL), Color.blue);
        colorMap.put(getOptionExpiryDate(2018, Month.JUNE), Color.red);
        colorMap.put(getOptionExpiryDate(2018, Month.SEPTEMBER), Color.magenta);

        System.out.println(" color map in constructor " + colorMap);
        addMouseMotionListener(this);
        addMouseListener(this);
    }

    public void setCurrentPrice(double p) {
        currentPrice = p;
    }

    public void setVolSmileFront(NavigableMap<Double, Double> mp) {
        //trim adjusted strikes
        NavigableMap<Double, Double> trimmedMap = new TreeMap<>();
        mp.forEach((k, v) -> {
            if ((k * 100) % 5 == 0) {
                trimmedMap.put(k, v);
            }
        });
        //volSmileFront = mp;
        volSmileFront = trimmedMap;
    }

    public void setVolSmileBack(NavigableMap<Double, Double> mp) {
        volSmileBack = mp;
    }

    public void setVolSmileThird(NavigableMap<Double, Double> mp) {
        volSmileThird = mp;
    }

    public void setVolSmileFourth(NavigableMap<Double, Double> mp) {
        volSmileFourth = mp;
    }

    public void setCurrentOption(String ticker, String f, double k, LocalDate exp, double vol) {
        selectedOptionTicker = ticker;
        selectedCP = f;
        selectedStrike = k;
        selectedExpiry = exp;
        selectedVol = vol;
    }

    private void computeDelta() {
        deltaMapFront = getStrikeDeltaMapFromVol(volSmileFront, currentPrice, frontExpiry);
        deltaMapBack = getStrikeDeltaMapFromVol(volSmileBack, currentPrice, backExpiry);
        deltaMapThird = getStrikeDeltaMapFromVol(volSmileThird, currentPrice, thirdExpiry);
        deltaMapFourth = getStrikeDeltaMapFromVol(volSmileFourth, currentPrice, fourthExpiry);
        //System.out.println(" delta map front " + deltaMapFront);
    }

    @Override
    protected void paintComponent(Graphics g) {

        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2.5F));
        g.drawString(" Current Vols 4 Expiries", 20, 30);
        g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.4F));

        if (volSmileFront.size() > 0) {

            computeDelta();

            double minVol = minGen(volSmileFront.values().stream().reduce(Math::min).orElse(0.0),
                    volSmileBack.values().stream().reduce(Math::min).orElse(0.0),
                    volSmileThird.values().stream().reduce(Math::min).orElse(0.0));

            double maxVol = maxGen(volSmileFront.values().stream().reduce(Math::max).orElse(0.0),
                    volSmileBack.values().stream().reduce(Math::max).orElse(0.0),
                    volSmileThird.values().stream().reduce(Math::max).orElse(0.0));

            int x = 5;
            int x_width = getWidth() / volSmileFront.size();
            int height = (int) (getHeight() * 0.8);

            int stepsOf10 = (int) Math.floor(maxVol * 10);
            //int topY = (int) (getHeight() * 0.8);
            //int stepSize = (int) (topY / stepsOf10);

            for (int i = 1; i != stepsOf10; i++) {
                g.drawString(Double.toString((double) i / 10), 5, getY((double) i / 10, maxVol, minVol));
            }

            for (Map.Entry<Double, Double> e : volSmileFront.entrySet()) {

                int yFront = getY(e.getValue(), maxVol, minVol);
                int yBack = getY(ChinaOptionHelper.interpolateVol(e.getKey(), volSmileBack), maxVol, minVol);
                int yThird = getY(ChinaOptionHelper.interpolateVol(e.getKey(), volSmileThird), maxVol, minVol);
                int yFourth = getY(ChinaOptionHelper.interpolateVol(e.getKey(), volSmileFourth), maxVol, minVol);

                g.drawOval(x, yFront, 5, 5);
                g.fillOval(x, yFront, 5, 5);
                String priceInPercent = Integer.toString((int) (e.getKey() / currentPrice * 100)) + "%";
                g.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));

                g.drawString(e.getKey().toString(), x, getHeight() - 20);
                g.drawString(priceInPercent, x, getHeight() - 5);
                g.drawString(Math.round(e.getValue() * 100d) + "", x, Math.max(10, yFront - 5));

                // draw circle on selected stock
                if (!selectedOptionTicker.equals("") && e.getKey() == selectedStrike) {
                    int y = getY(selectedVol, maxVol, minVol);
                    g.setColor(colorMap.getOrDefault(selectedExpiry, Color.black));
//                    System.out.println(" current selected color is " + selectedExpiry + " "
//                            + (colorMap.getOrDefault(selectedExpiry, Color.black)));
                    g.drawOval(x, y, 15, 15);
                    g.fillOval(x, y, 15, 15);
                }

                //g.setColor(Color.black);


                if (showDelta) {
                    g.drawString(" [" + Math.round((deltaMapFront.getOrDefault(e.getKey(), 0.0))) + "d]",
                            x + 20, Math.max(10, yFront - 5));
                }

                if ((double) e.getKey() == volSmileFront.lastKey()) {
                    g.drawString("(1)", getWidth() - 20, height / 2);
                }

                if (volSmileBack.size() > 0) {
                    g.setColor(color2Exp);
                    g.drawOval(x, yBack, 5, 5);
                    g.fillOval(x, yBack, 5, 5);
                    g.drawString(Math.round(ChinaOptionHelper.interpolateVol(e.getKey(), volSmileBack) * 100d)
                            + "", x + 10, yBack + 10);

                    if (showDelta) {
                        g.drawString(" [" + Math.round((deltaMapBack.getOrDefault(e.getKey(), 0.0))) + "d]",
                                x + 30, Math.max(10, yBack + 10));
                    }

                    if ((double) e.getKey() == volSmileBack.lastKey()) {
                        g.drawString("(2)", getWidth() - 20, height / 2 + 20);
                    }
                }

                if (volSmileThird.size() > 0) {
                    g.setColor(color3Exp);
                    g.drawOval(x, yThird, 5, 5);
                    g.fillOval(x, yThird, 5, 5);
                    g.drawString(Math.round(ChinaOptionHelper.interpolateVol(e.getKey(), volSmileThird) * 100d) + "", x, yThird + 20);

                    if ((double) e.getKey() == volSmileThird.lastKey()) {
                        g.drawString("(3)", getWidth() - 20, height / 2 + 40);
                    }
                }

                if (volSmileFourth.size() > 0) {
                    g.setColor(color4Exp);
                    g.drawOval(x, yFourth, 5, 5);
                    g.fillOval(x, yFourth, 5, 5);
                    g.drawString(Math.round(ChinaOptionHelper.interpolateVol(e.getKey(),
                            volSmileFourth) * 100d) + "", x, yFourth + 20);

                    if ((double) e.getKey() == volSmileFourth.lastKey()) {
                        g.drawString("(4)", getWidth() - 20, height / 2 + 60);
                    }
                }

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
        this.repaint();
    }
}
