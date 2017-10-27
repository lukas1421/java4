package graph;

import auxiliary.SimpleBar;
import historical.HistChinaStocks;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GraphBarTemporal<T extends Temporal> extends JComponent implements GraphFillable{


    static final int WIDTH_BAR = 4;
    int height;
    double min;
    double max;
    double maxRtn;
    double minRtn;
    int closeY;
    int highY;
    int lowY;
    int openY;
    int last = 0;
    double rtn = 0;
    NavigableMap<T, SimpleBar> mainMap;
    NavigableMap<T, Integer> histTradesMap;
    int netCurrentPosition;
    double currentTradePnl;
    double currentMtmPnl;
    String name;
    String chineseName;
    String bench;
    double sharpe;
    T maxAMT;
    T minAMT;
    volatile int size;
    static final BasicStroke BS3 = new BasicStroke(3);

    int wtdP;

    public GraphBarTemporal(NavigableMap<T, SimpleBar> tm1) {
        this.mainMap = (tm1 != null) ? tm1.entrySet().stream().filter(e -> !e.getValue().containsZero())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (u, v) -> u, ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

    public GraphBarTemporal() {
        name = "";
        chineseName = "";
        //maxAMT = LocalTime.of(9, 30);
        //minAMT = Utility.AMOPENT;
        this.mainMap = new ConcurrentSkipListMap<>();
    }

    public void setTradesMap(NavigableMap<T, Integer> tm) {
        //System.out.println(" trade history is " + tm);
        histTradesMap = tm;
        netCurrentPosition = tm.entrySet().stream().collect(Collectors.summingInt(Map.Entry::getValue));
    }

    public void setTradePnl(double p) {
        currentTradePnl = Math.round(p*100d)/100d;
    }

    public void setWtdMtmPnl(double p) {
        currentMtmPnl = Math.round(p*100d)/100d;

    }

    GraphBarTemporal(String s) {
        this.name = s;
    }

    public void setNavigableMap(NavigableMap<T, SimpleBar> tm1) {
        this.mainMap = (tm1 != null) ? tm1.entrySet().stream().filter(e -> !e.getValue().containsZero())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u,
                        ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
        //System.out.println(" mainMap in set navigable map is " + mainMap);
    }

    public NavigableMap<T, SimpleBar> getNavigableMap() {
        return this.mainMap;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public void setChineseName(String s) {
        this.chineseName = s;
    }

    public void setSize1(long s) {
        this.size = (int) s;
    }

    public void setBench(String s) {
        this.bench = s;
    }

    public void setSharpe(double s) {
        this.sharpe = s;
    }


    public void fillInGraphHKGen(String name, Map<String, NavigableMap<T, SimpleBar>> mp) {
        this.name = name;
        setName(name);
        //setChineseName(HKStock.hkNameMap.getOrDefault(name,""));

        if (mp.containsKey(name) && mp.get(name).size() > 0) {
            //System.out.println(" in graph bar temporal setting " + name);
            //System.out.println(mp.get(name));
            this.setNavigableMap(mp.get(name));
        } else {
            this.setNavigableMap(new ConcurrentSkipListMap<>());
        }
        this.repaint();
    }

    public void fillInGraphChinaGen(String name, Map<String, NavigableMap<T, SimpleBar>> mp) {
        this.name = name;
        setName(name);
        setChineseName(HistChinaStocks.nameMap.getOrDefault(name,""));

        if (mp.containsKey(name) && mp.get(name).size() > 0) {
            //System.out.println(" in graph bar temporal setting " + name);
            //System.out.println(mp.get(name));
            this.setNavigableMap(mp.get(name));
        } else {
            this.setNavigableMap(new ConcurrentSkipListMap<>());
        }
        this.repaint();
    }

    @Override
    public void fillInGraph(String name) {


    }

    @Override
    public void refresh() {
        //fillInGraphHKGen(name, mainMap);
    }

    public void refresh(Consumer<String> cons){
        cons.accept(name);
    }

    @Override
    protected void paintComponent(Graphics g) {

        //System.out.println(" drawing graph mainMap is " + mainMap);

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = (int) (getHeight() - 70);
        min = Utility.getMinGen(mainMap, SimpleBar::getLow);
        max = Utility.getMaxGen(mainMap, SimpleBar::getHigh);
        //minRtn = getMinRtn();
        //maxRtn = getMaxRtn();
        last = 0;
        //rtn = getRtn();

        int x = 5;
        for (T lt : mainMap.keySet()) {
            openY = getY(mainMap.floorEntry(lt).getValue().getOpen());
            highY = getY(mainMap.floorEntry(lt).getValue().getHigh());
            lowY = getY(mainMap.floorEntry(lt).getValue().getLow());
            closeY = getY(mainMap.floorEntry(lt).getValue().getClose());

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


            if(histTradesMap.containsKey(lt)) {
                int q = histTradesMap.get(lt);
                if(lt.getClass()==LocalDateTime.class) {
                    g.setColor(Color.blue);
                    g.drawString(((LocalDateTime)lt).toLocalTime().toString(), x, getHeight()-20);
                }
                if (q > 0) {
                    g.setColor(Color.blue);
                    int xCord = x;
                    int yCord = lowY;
                    Polygon p = new Polygon(new int[]{xCord - 10, xCord, xCord + 10}, new int[]{yCord + 10, yCord, yCord + 10}, 3);
                    g.drawPolygon(p);
                    g.fillPolygon(p);
                    g.drawString(Long.toString(Math.round(q/1000.0)), xCord, yCord+25);
                    //g.drawString();
                } else {
                    g.setColor(Color.black);
                    int xCord = x;
                    int yCord = highY;
                    Polygon p1 = new Polygon(new int[]{xCord - 10, xCord, xCord + 10}, new int[]{yCord - 10, yCord, yCord - 10}, 3);
                    g.drawPolygon(p1);
                    g.fillPolygon(p1);
                    g.drawString(Long.toString(Math.round(q/1000.0)), xCord, yCord-25);
                }

                //g.drawString(lt.toString(), x, getHeight()-40);
            }


            g.setColor(Color.black);

            if (lt.equals(mainMap.firstKey())) {
                g.drawString(lt.toString(), x, getHeight() - 40);
            } else if (lt.equals(mainMap.lastKey())) {
                g.drawString(lt.toString(), x, getHeight() - 40);
            } else {
                if (lt.getClass() == LocalDate.class) {
                    LocalDate ltn = (LocalDate) lt;
                    if (ltn.getMonth() != ((LocalDate) mainMap.lowerKey(lt)).getMonth()) {
                        g.drawString(Integer.toString(ltn.getMonth().getValue()), x, getHeight() - 40);
                    }

                } else if (lt.getClass() == LocalDateTime.class) {
                    LocalDateTime ldt = (LocalDateTime) lt;
                    if (ldt.getDayOfMonth() != ((LocalDateTime) mainMap.lowerKey(lt)).getDayOfMonth()) {
                        g.drawString(Integer.toString(ldt.getDayOfMonth()), x, getHeight() - 40);
                    }

                }
            }
            x += WIDTH_BAR;
        }

        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(BS3);

        g2.drawString(Double.toString(max), getWidth() - 40, 15);
        g2.drawString(Double.toString(min), getWidth() - 40, getHeight() - 33);
        //g2.drawString(Double.toString(ChinaStock.getCurrentMARatio(name)),getWidth()-40, getHeight()/2);
        //g2.drawString("周" + Integer.toString(wtdP), getWidth() - 40, getHeight() / 2);

        if (!Optional.ofNullable(name).orElse("").equals("")) {
            g2.drawString(name, 5, 15);
        }

        if (!Optional.ofNullable(chineseName).orElse("").equals("")) {
            g2.drawString(chineseName, getWidth() / 8, 15);
        }

        g2.drawString(" pos: " + Integer.toString(netCurrentPosition), getWidth()*7/8, getHeight()/6);
        g2.drawString(" Trade pnl " + Double.toString(currentTradePnl), getWidth()*7/8, getHeight()*2/6);
        g2.drawString(" mtm pnl " + Double.toString(currentMtmPnl), getWidth()*7/8, getHeight()*3/6);


        if (!Optional.ofNullable(bench).orElse("").equals("")) {
            g2.drawString("(" + bench + ")", getWidth() * 2 / 8, 15);
        }

        //add bench here
//        g2.drawString(Double.toString(getLast()), getWidth() * 3 / 8, 15);

//        g2.drawString("P%:" + Double.toString(getCurrentPercentile()), getWidth() * 4 / 8, 15);
//        g2.drawString("涨:" + Double.toString(getRtn()) + "%", getWidth() * 5 / 8, 15);
//        g2.drawString("高 " + (getAMMaxT()), getWidth() * 6 / 8, 15);
//        //g2.drawString("低 " + (getAMMinT()), getWidth() * 7 * 8, 15);
//        g2.drawString("夏 " + sharpe, getWidth() * 7 / 8, 15);
//
//        //below
//        g2.drawString("开 " + Double.toString(getRetOPC()), 5, getHeight() - 25);
//        g2.drawString("一 " + Double.toString(getFirst1()), getWidth() / 9, getHeight() - 25);
//        g2.drawString("量 " + Long.toString(getSize1()), 5, getHeight() - 5);
//        g2.drawString("位Y " + Integer.toString(getCurrentMaxMinYP()), getWidth() / 9, getHeight() - 5);
//        g2.drawString("十  " + Double.toString(getFirst10()), getWidth() / 9 + 75, getHeight() - 25);
//        g2.drawString("V比 " + Double.toString(getSizeSizeYT()), getWidth() / 9 + 75, getHeight() - 5);
//
//        g2.setColor(Color.BLUE);
//        g2.drawString("开% " + Double.toString(getOpenYP()), getWidth() / 9 * 2 + 70, getHeight() - 25);
//        g2.drawString("收% " + Double.toString(getCloseYP()), getWidth() / 9 * 3 + 70, getHeight() - 25);
//        g2.drawString("CH " + Double.toString(getRetCHY()), getWidth() / 9 * 4 + 70, getHeight() - 25);
//        g2.drawString("CL " + Double.toString(getRetCLY()), getWidth() / 9 * 5 + 70, getHeight() - 25);
//        g2.drawString("和 " + Double.toString(round(100d * (getRetCLY() + getRetCHY())) / 100d), getWidth() / 9 * 6 + 70, getHeight() - 25);
//        g2.drawString("HO " + Double.toString(getHO()), getWidth() / 9 * 7 + 50, getHeight() - 25);
//
//        g2.drawString("低 " + Integer.toString(getMinTY()), getWidth() / 9 * 2 + 70, getHeight() - 5);
//        g2.drawString("高 " + Integer.toString(getMaxTY()), getWidth() / 9 * 4 - 90 + 70, getHeight() - 5);
//        g2.drawString("CO " + Double.toString(getRetCO()), getWidth() / 9 * 4 + 70, getHeight() - 5);
//        g2.drawString("CC " + Double.toString(getRetCC()), getWidth() / 9 * 5 + 70, getHeight() - 5);
//        g2.drawString("振" + Double.toString(getRangeY()), getWidth() / 9 * 6 + 70, getHeight() - 5);
//        g2.drawString("折R " + Double.toString(getHOCHRangeRatio()), getWidth() / 9 * 7 + 50, getHeight() - 5);
//        g2.drawString("晏 " + Integer.toString(getPMchgY()), getWidth() - 60, getHeight() - 5);

        //g2.setColor(new Color(0, colorGen(wtdP), 0));
        //g2.fillRect(0,0, getWidth(), getHeight());
        g2.fillRect(getWidth() - 30, 20, 20, 20);
        g2.setColor(getForeground());
    }

    /**
     * Convert bar value to y coordinate.
     */
    int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
        return height - (int) val + 20;
    }


}
