package graph;

import javax.swing.*;
import java.awt.*;
import java.time.temporal.Temporal;
import java.util.NavigableMap;

public class GraphChinaPnl<T extends Temporal> extends JComponent {


    NavigableMap<T, Double> mtmMap;
    NavigableMap<T, Double> tradePnlMap;
    NavigableMap<T, Double> netPnlMap;
    NavigableMap<T, Double> deltaMap;

    public static final int WIDTH_PNL = 5;

    public void setMtm(NavigableMap<T,Double> input) {
        mtmMap = input;
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        int x = 5;
        int last = 0;
        int close = 0;

        g2.setColor(Color.RED);
        for (T lt : mtmMap.keySet()) {
            //close = getY(mtmMap.floorEntry(lt).getValue());
            last = (last == 0) ? close : last;
            g.drawLine(x, last, x + WIDTH_PNL, close);
            last = close;

            x += WIDTH_PNL;
        }

    }
}
