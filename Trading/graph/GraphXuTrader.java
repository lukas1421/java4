package graph;

import TradeType.TradeBlock;
import apidemo.FutType;
import apidemo.XUTrader;
import auxiliary.SimpleBar;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static java.lang.Math.*;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static utility.Utility.*;

public class GraphXuTrader extends JComponent {

    private static final int WIDTH_BAR = 5;
    int height;
    double min;
    double max;
    double maxRtn;
    double minRtn;
    int last = 0;
    double rtn = 0;
    NavigableMap<LocalTime, SimpleBar> tm;
    private NavigableMap<LocalTime, TradeBlock> trademap;
    private volatile FutType fut;
    volatile String name;
    String chineseName;
    private String bench;
    LocalTime maxAMT;
    LocalTime minAMT;
    volatile int size;
    private static final BasicStroke BS3 = new BasicStroke(3);
    private int wtdP;

//    public GraphXuTrader(NavigableMap<LocalTime, SimpleBar> tm) {
//        this.tm = (tm != null) ? tm.entrySet().stream().filter(e -> !e.getValue()
//                .containsZero()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
//                (u, v) -> u, ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
//    }

    public GraphXuTrader() {
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

    private void setTradesMap(NavigableMap<LocalTime, TradeBlock> trade) {
        //System.out.println(" setting trades map in Graph Xu trader " + trade);
        trademap = trade;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    public void setFut(FutType f) {
        this.fut = f;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setChineseName(String s) {
        this.chineseName = s;
    }

    public void setBench(String s) {
        this.bench = s;
    }

    public void fillInGraph(NavigableMap<LocalTime, SimpleBar> mp) {
        if (XUTrader.gran == DisplayGranularity._1MDATA) {
            this.setNavigableMap(mp);
            //this.setTradesMap(XUTrader.tradesMap.get(activeFuture));
        } else if (XUTrader.gran == DisplayGranularity._5MDATA) {
            this.setNavigableMap(priceMap1mTo5M(mp));
        }
    }

    public void fillTradesMap(NavigableMap<LocalTime,TradeBlock> m) {
        if (XUTrader.gran == DisplayGranularity._1MDATA) {
            this.setTradesMap(tradeBlockRoundGen(m, t->t.truncatedTo(ChronoUnit.MINUTES)));
        } else if (XUTrader.gran == DisplayGranularity._5MDATA) {
            this.setTradesMap(tradeBlock1mTo5M(m));
        }

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

        height = getHeight() - 70;
        min = getMin();
        max = getMax();
        minRtn = getMinRtn();
        maxRtn = getMaxRtn();
        last = 0;
        rtn = getReturn();

        int x = 5;
        for (LocalTime lt : tm.keySet()) {
            int openY = getY(tm.floorEntry(lt).getValue().getOpen());
            int highY = getY(tm.floorEntry(lt).getValue().getHigh());
            int lowY = getY(tm.floorEntry(lt).getValue().getLow());
            int closeY = getY(tm.floorEntry(lt).getValue().getClose());

            //noinspection Duplicates
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
                if (XUTrader.gran == DisplayGranularity._1MDATA) {
                    if (lt.getHour() < 16 && (lt.getMinute() == 0 || lt.getMinute() % 30 == 0)) {
                        g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 40);
                    }
                } else {
                    if (lt.getHour() < 16 && (lt.getMinute() == 0 || lt.getMinute() == 0)) {
                        g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 40);
                    }
                }
            }
            //trades
            if (XUTrader.showTrades) {
                if (trademap.containsKey(lt)) {
                    TradeBlock tb = trademap.get(lt);
                    if (tb.getSizeAll() > 0) {
                        g.setColor(Color.blue);
                        int yCord = getY(tb.getAveragePrice());
                        Polygon p = new Polygon(new int[]{x - 3, x, x + 3}, new int[]{yCord + 4, yCord, yCord + 4}, 3);
                        g.drawPolygon(p);
                        g.fillPolygon(p);
                    } else {
                        g.setColor(Color.black);
                        int yCord = getY(tb.getAveragePrice());
                        Polygon p = new Polygon(new int[]{x - 3, x, x + 3}, new int[]{yCord - 4, yCord, yCord - 4}, 3);
                        g.drawPolygon(p);
                        g.fillPolygon(p);
                    }
                }
            }
            x += WIDTH_BAR;
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
        //g2.drawString(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), 15, 15);
        g2.drawString(Double.toString(getReturn()) + "%", getWidth() / 8, 15);
        g2.drawString("开: " + Double.toString(getOpen()), getWidth() * 2 / 8, 15);
        g2.drawString(Double.toString(getLast()), getWidth() * 3 / 8, 15);
        g2.drawString("Pos: " + XUTrader.currentPosMap.getOrDefault(fut, 0), getWidth() * 4 / 8, 15);
        g2.drawString("Pnl: " + getTradePnl(), getWidth() * 9 / 16, 15);
        g2.drawString("B: " + XUTrader.botMap.getOrDefault(fut, 0), getWidth() * 5 / 8, 15);
        g2.drawString("S: " + XUTrader.soldMap.getOrDefault(fut, 0), getWidth() * 6 / 8, 15);

        g2.setColor(new Color(0, 255 * (100 - wtdP) / 100, 0));
        //g2.fillRect(0,0, getWidth(), getHeight());
        g2.fillRect(getWidth() - 30, 20, 20, 20);
        g2.setColor(getForeground());

//        if (XUTrader.showTrades) {
//            if(XUTrader.tradesMap.get(fut).size()>0) {
//                XUTrader.tradesMap.get(fut).forEach((key, value) -> {
//                    if (value.getSizeAll() > 0) {
//                        g.setColor(Color.blue);
//                        int xCord = getXForLT(key);
//                        int yCord = getY(value.getAveragePrice());
//                        g.drawPolygon(new int[]{xCord - 2, xCord, xCord + 2}, new int[]{yCord + 4, yCord, yCord + 4}, 3);
//                    } else {
//                        g.setColor(Color.black);
//                        int xCord = getXForLT(key);
//                        int yCord = getY(value.getAveragePrice());
//                        g.drawPolygon(new int[]{xCord - 2, xCord, xCord + 2}, new int[]{yCord - 4, yCord, yCord - 4}, 3);
//                    }
//                });
//            }
//        }
    }

    private int getXForLT(LocalTime t) {
        if (XUTrader.gran == DisplayGranularity._1MDATA) {
            long timeDiff = ChronoUnit.MINUTES.between(LocalTime.of(9, 0), t);
            if (t.isAfter(LocalTime.of(11, 30))) {
                timeDiff = timeDiff - 90;
            }
            return (int) (WIDTH_BAR * timeDiff + 5);
        } else if (XUTrader.gran == DisplayGranularity._5MDATA) {
            long timeDiff = (ChronoUnit.MINUTES.between(LocalTime.of(9, 0), t)) / 5;

            if (t.isAfter(LocalTime.of(11, 30))) {
                timeDiff = timeDiff - 18;
            }
            return (int) (WIDTH_BAR * timeDiff + 1);
        }
        return 0;
    }

    private double getTradePnl() {

        double currPrice = getLast();
        //double fx = fxMap.getOrDefault(name,1.0);
        if (XUTrader.tradesMap.containsKey(fut) && XUTrader.tradesMap.get(fut).size() > 0) {
            int netTradedPosition = XUTrader.tradesMap.get(fut).entrySet().stream().mapToInt(e -> e.getValue().getSizeAll()).sum();
            double cost = XUTrader.tradesMap.get(fut).entrySet().stream().mapToDouble(e -> e.getValue().getCostBasisAll("")).sum();
            double mv = netTradedPosition * currPrice;
            //System.out.println(getStr(" currprice, net traded pos cost mv", currPrice, netTradedPosition, cost, mv));
            //System.out.println(getStr(" cost mv ", cost, mv));
            return Math.round(100d * (mv + cost)) / 100d;
        }
        return 0.0;

    }
//    public int getYForLTSell(LocalTime t) {
//        SimpleBar sb = (SimpleBar) XUTrader.xuData.floorEntry(t).getValue();
//        if (sb.normalBar()) {
//            return getY(sb.getHigh());
//        } else {
//            throw new IllegalArgumentException("BAGAYARO");
//        }
//    }
//
//    public int getYForLTBuy(LocalTime t) {
//        SimpleBar sb = (SimpleBar) XUTrader.xuData.floorEntry(t).getValue();
//        if (sb.normalBar()) {
//            return getY(sb.getLow());
//        } else {
//            throw new IllegalArgumentException("BAGAYARO");
//        }
//    }

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
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
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