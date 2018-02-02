package graph;

import TradeType.TradeBlock;
import apidemo.ChinaData;
import apidemo.ChinaMain;
import apidemo.ChinaPosition;
import apidemo.ChinaStock;
import auxiliary.SimpleBar;
import historical.HistChinaStocks;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static apidemo.ChinaData.priceMapBar;
import static apidemo.ChinaKeyMonitor.dispGran;
import static apidemo.ChinaStock.NORMAL_STOCK;
import static apidemo.ChinaStock.closeMap;
import static java.lang.Math.*;
import static java.util.Optional.ofNullable;
import static utility.Utility.*;

public class GraphMonitor extends JComponent implements GraphFillable, MouseListener, MouseMotionListener {

    private static final int WIDTH_MON = 2;
    String name;
    String chineseName;
    private volatile NavigableMap<LocalDateTime, SimpleBar> tm;
    private NavigableMap<LocalDateTime, TradeBlock> trades = new ConcurrentSkipListMap<>();

    //private NavigableMap<LocalDateTime, SimpleBar> tmLDT;
    //NavigableMap<LocalDateTime, ? super Trade> tradesLdt = new ConcurrentSkipListMap<>();

    private double maxToday;
    private double minToday;
    double minRtn;
    double maxRtn;
    int height;
    int last = 0;
    double rtn = 0;
    int size;
    private String bench;
    private double ytdSharpe;
    private double minSharpe;
    private double wtdSharpe;
    private static final BasicStroke BS3 = new BasicStroke(3);
    private int ytdCloseP;
    private int ytdY2CloseP;
    private int current2DayP;
    private int current3DayP;
    private int wtdP;

    private volatile int mouseXCord = Integer.MAX_VALUE;
    private volatile int mouseYCord = Integer.MAX_VALUE;


    GraphMonitor() {
        name = "";
        chineseName = "";
        this.tm = new ConcurrentSkipListMap<>();
        addMouseListener(this);
        addMouseMotionListener(this);
        //this.tmLDT = new ConcurrentSkipListMap<>();
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String s) {
        this.name = s;
    }

    public void setChineseName(String s) {
        this.chineseName = s;
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = getHeight() - 70;
        minToday = getMin();
        maxToday = getMax();
        minRtn = getMinRtn();
        maxRtn = getMaxRtn();
        last = 0;

        int x = 5;
        for (LocalDateTime lt : tm.keySet()) {
            int openY = getY(tm.floorEntry(lt).getValue().getOpen());
            int highY = getY(tm.floorEntry(lt).getValue().getHigh());
            int lowY = getY(tm.floorEntry(lt).getValue().getLow());
            int closeY = getY(tm.floorEntry(lt).getValue().getClose());

            //noinspection Duplicates
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

            if (trades.subMap(lt, true, lt.plusMinutes(dispGran.getMinuteDiff()), false).size() > 0) {
                for (Map.Entry e : trades.subMap(lt, true, lt.plusMinutes(dispGran.getMinuteDiff()),
                        false).entrySet()) {
                    TradeBlock t = (TradeBlock) e.getValue();
                    System.out.println(getStr(" trades in graph monitor margin%" +
                            "", name, t, t.hasMargin()));


                    if (t.getSizeAll() > 0) {
                        g.setColor(Color.blue);
                        Polygon p = new Polygon(new int[]{x - 10, x, x + 10},
                                new int[]{lowY + 10, lowY, lowY + 10}, 3);
                        g.drawPolygon(p);
                        if (!t.hasMargin()) {
                            g.fillPolygon(p);
                        }
                    } else {
                        g.setColor(Color.black);
                        Polygon p1 = new Polygon(new int[]{x - 10, x, x + 10},
                                new int[]{highY - 10, highY, highY - 10}, 3);
                        g.drawPolygon(p1);
                        if (!t.hasMargin()) {
                            g.fillPolygon(p1);
                        }
                    }
                }
            }

            g.setColor(Color.black);

            if (dispGran == DisplayGranularity._1MDATA) {
                if (lt.equals(tm.firstKey())) {
                    g.drawString(lt.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 5);
                } else {
                    if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11
                            && lt.getMinute() == 30)) {
                        g.drawString(lt.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 5);
                    }
                }
            } else if (dispGran == DisplayGranularity._5MDATA) {
                if (lt.equals(tm.firstKey())) {
                    g.drawString(lt.toLocalDate().format(DateTimeFormatter.ofPattern("MM-dd")), x, getHeight() - 5);
                } else if (lt.equals(tm.lastKey())) {
                    g.drawString(lt.format(DateTimeFormatter.ofPattern("HH:mm")), x + 20, getHeight() - 5);
                } else {
                    if (lt.getDayOfMonth() != tm.lowerKey(lt).getDayOfMonth()) {
                        g.drawString(lt.toLocalDate().format(DateTimeFormatter.ofPattern("MM-dd")), x, getHeight() - 5);

                    }
                }
            }

            if (roundDownToN(mouseXCord, WIDTH_MON) == x - 5) {
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
                g.drawString(lt.toLocalTime().toString() + " " + Math.round(100d * tm.floorEntry(lt).getValue().getClose()) / 100d,
                        (mouseXCord <= (getWidth() / 2)) ? x : x - (getWidth() / 3),
                        lowY + (mouseYCord < closeY ? -20 : +20));
                g.drawOval(x + 2, lowY, 5, 5);
                g.fillOval(x + 2, lowY, 5, 5);
                g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
            }

            x += WIDTH_MON;
        }

        if (mouseXCord > x && mouseXCord < getWidth() && tm.size() > 0) {

            int lowY = getY(tm.lastEntry().getValue().getLow());
            int closeY = getY(tm.lastEntry().getValue().getClose());
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 2F));
            g.drawString(tm.lastKey().toLocalTime().toString() + " " +
                            Math.round(100d * tm.lastEntry().getValue().getClose()) / 100d,
                    x, lowY + (mouseYCord < closeY ? -20 : +20));
            g.drawOval(x + 2, lowY, 5, 5);
            g.fillOval(x + 2, lowY, 5, 5);
            g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 0.5F));
        }

        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(BS3);

        g2.drawString(Double.toString(minRtn) + "%", getWidth() - 40, getHeight() - 33);
        g2.drawString(Double.toString(maxRtn) + "%", getWidth() - 40, 15);

        g2.setColor(Color.blue);
        g2.drawString("Y " + Integer.toString(ytdCloseP), getWidth() - 45, 35);
        g2.drawString("2 " + Integer.toString(ytdY2CloseP), getWidth() - 45, 55);
        g2.drawString("二 " + Integer.toString(current2DayP), getWidth() - 45, 75);
        g2.drawString("三 " + Integer.toString(current3DayP), getWidth() - 45, 95);
        //g2.drawString(Integer.toString(wtdP),getWidth()-40,115);

        g2.setColor(Color.black);

        //g2.drawString(Double.toString(ChinaStock.getCurrentMARatio(name)),getWidth()-40, getHeight()/2);
        if (!ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }
        if (!ofNullable(chineseName).orElse("").equals("")) {
            g2.drawString(chineseName, getWidth() / 6, 15);
        }

        if (!ChinaStock.shortIndustryMap.getOrDefault(name, "").equals("")) {
            g2.drawString(ChinaStock.shortIndustryMap.get(name), 7 * getWidth() / 24, 15);
        }

        if (!ofNullable(bench).orElse("").equals("")) {
            g2.drawString("(" + bench + ")", getWidth() * 2 / 6, 15);
        }

        g2.drawString(Double.toString(getLast()), getWidth() * 3 / 6, 15);

        g2.drawString(Double.toString(getReturn()) + "%", getWidth() * 4 / 6, 15);

        g2.drawString(Integer.toString(ChinaPosition.getCurrentDelta(name)) + " k", getWidth() * 5 / 6, 15);

        double mtmPnl = Math.round(ChinaPosition.getMtmPnl(name) / 100d) / 10d;
        double trPnl = Math.round(ChinaPosition.getTradePnl(name) / 100d) / 10d;

        g2.setColor(mtmPnl > 0 ? new Color(30, 150, 0) : Color.red);
        g2.drawString("M " + Double.toString(mtmPnl) + "k", getWidth() * 5 / 6, 45);

        g2.setColor(trPnl > 0 ? new Color(50, 150, 0) : Color.red);
        g2.drawString("T " + Double.toString(trPnl) + " k", getWidth() * 5 / 6, 75);
        g2.setColor(Color.RED);

        g2.drawString("周 " + Integer.toString(ChinaPosition.getPercentileWrapper(name)), getWidth() * 5 / 6, 95);
        //g2.drawString("P变 " + Integer.toString(ChinaPosition.getChangeInPercentileToday(name)), getWidth()*5/6, 115);
        g2.drawString("分夏 " + Double.toString(Math.round(100d * minSharpe) / 100d), getWidth() * 5 / 6, 115);
        g2.drawString("弹 " + Double.toString(ChinaPosition.getPotentialReturnToMid(name)), getWidth() * 5 / 6, 135);

        g2.drawString("年夏" + Double.toString(ytdSharpe), getWidth() * 5 / 6 + 10, getHeight() - 5);

        g2.drawString("周夏" + Double.toString(wtdSharpe), getWidth() * 4 / 6, getHeight() - 20);

        g2.setColor(new Color(0, Math.min(250, 250 * (100 - wtdP) / 100), 0));
        //g2.fillRect(0,0, getWidth(), getHeight());
        g2.fillRect(getWidth() - 30, getHeight() - 30, 30, 30);
        g2.setColor(getForeground());

        //g2.drawString("color", getWidth()-40, getHeight()-5);
        //this.setfo
        //setBackground(new Color(100+100*wtdP/100,255,100+100*wtdP/100));
    }

    int getY(double v) {
        double span = maxToday - minToday;
        double pct = (v - minToday) / span;
        double val = pct * height + .5;
        return height - (int) val + 30;
    }

    double getMin() {
        return (tm.size() > 0) ? tm.entrySet().stream().min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow).orElse(0.0) : 0.0;
    }

    double getMax() {
        return (tm.size() > 0) ? tm.entrySet().stream().max(BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;
    }

    double getLast() {
        return (tm.size() > 0) ? round(1000d * tm.lastEntry().getValue().getClose()) / 1000d : 0.0;
    }

    private void setSize1(long s) {
        this.size = (int) s;
    }

    void setBench(String s) {
        this.bench = s;
    }

    private void setYtdSharpe(double s) {
        this.ytdSharpe = s;
    }

    private void setMinSharpe(double s) {
        this.minSharpe = s;
    }

    private void setWtdSharpe(double s) {
        this.wtdSharpe = Math.round(s * 100d) / 100d;
    }

    double getReturn() {

        if (tm.size() > 0) {
            double initialP = 0.0;
            if (dispGran == DisplayGranularity._1MDATA) {
                initialP = closeMap.getOrDefault(name,
                        tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0));
            } else {
                initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            }
            double finalP = tm.lastEntry().getValue().getClose();
            //System.out.println(getStr(" chinese initial final ", chineseName,initialP,finalP));
            return (double) round((finalP / initialP - 1) * 1000d) / 10d;
        }
        return 0.0;
    }

    @SuppressWarnings("Duplicates")
    void clearGraph() {
        this.name = "";
        setName("");
        setChineseName("");
        setBench("");
        setYtdSharpe(0.0);
        setMinSharpe(0.0);
        setWtdSharpe(0.0);
        setSize1(0L);
        this.setNavigableMap(new ConcurrentSkipListMap<>());
    }

    @Override
    public void fillInGraph(String name) {
        this.name = name;
        setName(name);
        setChineseName(ChinaStock.nameMap.get(name));
        setBench(ChinaStock.benchMap.getOrDefault(name, ""));
        setYtdSharpe(ChinaStock.sharpeMap.getOrDefault(name, 0.0));
        setMinSharpe(ChinaData.priceMinuteSharpe.getOrDefault(name, 0.0));
        setWtdSharpe(ChinaData.wtdSharpe.getOrDefault(name, 0.0));
        setSize1(ChinaStock.sizeMap.getOrDefault(name, 0L));


//        if(HistChinaStocks.chinaTradeMap.containsKey(name)) {
//            trades = mergeTradeMap(HistChinaStocks.chinaTradeMap.get(name),
//                    ChinaPosition.tradesMapFront.containsKey(name)?
//                    ChinaPosition.tradesMapFront.get(name) : new ConcurrentSkipListMap<>());
//        } else {


        trades = priceMapToLDT(ChinaPosition.tradesMap.containsKey(name) ?
                ChinaPosition.tradesMap.get(name) : new ConcurrentSkipListMap<>(), ChinaMain.currentTradingDate);

        //System.out.println(getStr("graph monitor name trade ", name, trades));

        if (HistChinaStocks.chinaTradeMap.containsKey(name) && HistChinaStocks.chinaTradeMap.get(name).size() > 0) {
            //LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
            trades = mergeTradeMap(HistChinaStocks.chinaTradeMap.get(name).headMap(
                    LocalDateTime.of(ChinaMain.currentTradingDate, LocalTime.MIN), false),
                    priceMapToLDT(ChinaPosition.tradesMap.containsKey(name) ?
                            ChinaPosition.tradesMap.get(name) : new ConcurrentSkipListMap<>(), ChinaMain.currentTradingDate));
            //System.out.println(" merged trade is " + trades);
        }

        if (NORMAL_STOCK.test(name)) {
            this.setNavigableMap(priceMapBar.get(name));
            getYtdY2CloseP(name);
        } else {
            this.setNavigableMap(new ConcurrentSkipListMap<>());
        }
    }

    @Override
    public void refresh() {
        fillInGraph(name);
    }

    void setNavigableMap(NavigableMap<LocalTime, SimpleBar> tmIn) {

        NavigableMap<LocalDateTime, SimpleBar> res = new ConcurrentSkipListMap<>();

        if (dispGran == DisplayGranularity._1MDATA) {
            res = priceMapToLDT(tmIn, ChinaMain.currentTradingDate);
        } else if (dispGran == DisplayGranularity._5MDATA) {
            if (HistChinaStocks.chinaWtd.containsKey(name) && HistChinaStocks.chinaWtd.get(name).size() > 0) {
                res = trimMapWithLocalTimePred(mergeMaps(HistChinaStocks.chinaWtd.get(name), Utility.priceMap1mTo5M(tmIn)), chinaTradingTimePred);
            } else {
                res = trimMapWithLocalTimePred(priceMapToLDT(priceMap1mTo5M(tmIn), ChinaMain.currentTradingDate), chinaTradingTimePred);
            }
        }
        NavigableMap<LocalDateTime, SimpleBar> finalRes = res;
        SwingUtilities.invokeLater(() -> this.tm = finalRes);
    }

    double getMaxRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMax();
            return abs(finalP - initialP) > 0.0001 ? (double) round((finalP / initialP - 1) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    double getMinRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Map.Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMin();
            return (Math.abs(finalP - initialP) > 0.0001) ? (double) round(log(getMin() / initialP) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    private void getYtdY2CloseP(String name) {
        double current;
        double maxT;
        double minT;

        if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0) {
            current = priceMapBar.get(name).lastEntry().getValue().getClose();
            maxT = priceMapBar.get(name).entrySet().stream().max(BAR_HIGH).map(Map.Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
            minT = priceMapBar.get(name).entrySet().stream().min(BAR_LOW).map(Map.Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
        } else {
            current = 0.0;
            maxT = Double.MIN_VALUE;
            minT = Double.MAX_VALUE;
        }

        if (ChinaData.priceMapBarYtd.containsKey(name) && ChinaData.priceMapBarYtd.get(name).size() > 0) {
            double closeY1 = ChinaData.priceMapBarYtd.get(name).lastEntry().getValue().getClose();
            double maxY = ChinaData.priceMapBarYtd.get(name).entrySet().stream()
                    .max(BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);
            double minY = ChinaData.priceMapBarYtd.get(name).entrySet().stream()
                    .min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow).orElse(0.0);
            ytdCloseP = (int) Math.round(100d * (closeY1 - minY) / (maxY - minY));
            current2DayP = (int) Math.round(100d * (current - Utility.reduceDouble(Math::min, minT, minY))
                    / (Utility.reduceDouble(Math::max, maxT, maxY) - Utility.reduceDouble(Math::min, minT, minY)));

            if (ChinaData.priceMapBarY2.containsKey(name) && ChinaData.priceMapBarY2.get(name).size() > 0) {
                double maxY2 = ChinaData.priceMapBarY2.get(name).entrySet().stream()
                        .max(BAR_HIGH).map(Map.Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);
                double minY2 = ChinaData.priceMapBarY2.get(name).entrySet().stream()
                        .min(BAR_LOW).map(Map.Entry::getValue).map(SimpleBar::getLow).orElse(0.0);

                ytdY2CloseP = (int) Math.round(100d * (closeY1 - Utility.reduceDouble(Math::min, minY2, minY))
                        / (Utility.reduceDouble(Math::max, maxY2, maxY) - Utility.reduceDouble(Math::min, minY2, minY)));

                current3DayP = (int) Math.round(100d * (current - Utility.reduceDouble(Math::min, minT, minY, minY2))
                        / (Utility.reduceDouble(Math::max, maxT, maxY, maxY2) - Utility.reduceDouble(Math::min, minT, minY, minY2)));
            }

            wtdP = (int) Math.round(100d * (current - Utility.reduceDouble(Math::min, minT, ChinaPosition.wtdMinMap.getOrDefault(name,
                    Double.MAX_VALUE))) / (Utility.reduceDouble(Math::max, maxT, ChinaPosition.wtdMaxMap.getOrDefault(name, 0.0))
                    - Utility.reduceDouble(Math::min, minT, ChinaPosition.wtdMinMap.getOrDefault(name, Double.MAX_VALUE))));
//            System.out.println(" name " + name + " current max min wtd wtdMax wtdMin "+ getStr(current,maxT,minT,wtdP,
//                    ChinaPosition.wtdMinMap.getOrDefault(name,Double.MAX_VALUE), ChinaPosition.wtdMinMap.getOrDefault(name, Double.MAX_VALUE)));
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {

    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {
        //System.out.println(" mouse exit from graph bar ");
        mouseXCord = Integer.MAX_VALUE;
        mouseYCord = Integer.MAX_VALUE;
        this.repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {

    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseXCord = e.getX();
        mouseYCord = e.getY();
        //System.out.println(" graph bar x mouse x is " + mouseXCord);
        this.repaint();

    }
}
