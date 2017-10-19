package graph;

import apidemo.ChinaPosition;
import apidemo.ChinaStock;
import apidemo.HKData;
import apidemo.HKStock;
import auxiliary.SimpleBar;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static apidemo.ChinaData.*;
import static apidemo.ChinaDataYesterday.*;
import static apidemo.ChinaStock.*;
import static java.lang.Double.min;
import static java.lang.Math.*;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static utility.Utility.applyAllDouble;

public final class GraphBar extends JComponent implements GraphFillable {

    static final int WIDTH_BAR = 3;
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
    NavigableMap<LocalTime, SimpleBar> tm;
    String name;
    String chineseName;
    String bench;
    double sharpe;
    LocalTime maxAMT;
    LocalTime minAMT;
    volatile int size;
    static final BasicStroke BS3 = new BasicStroke(3);

    int wtdP;

    public GraphBar(NavigableMap<LocalTime, SimpleBar> tm) {
        this.tm = (tm != null) ? tm.entrySet().stream().filter(e -> !e.getValue().containsZero()).collect(Collectors.toMap(Entry::getKey, Entry::getValue,
                (u, v) -> u, ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

    public GraphBar() {
        name = "";
        chineseName = "";
        maxAMT = LocalTime.of(9, 30);
        minAMT = Utility.AMOPENT;
        this.tm = new ConcurrentSkipListMap<>();
    }

    GraphBar(String s) {
        this.name = s;
    }

    public void setNavigableMap(NavigableMap<LocalTime, SimpleBar> tm) {
        this.tm = (tm != null) ? tm.entrySet().stream().filter(e -> !e.getValue().containsZero())
                .collect(toMap(Entry::getKey, Entry::getValue, (u, v) -> u,
                        ConcurrentSkipListMap::new)) : new ConcurrentSkipListMap<>();
    }

    public NavigableMap<LocalTime, SimpleBar> getNavigableMap() {
        return this.tm;
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

    @Override
    public void fillInGraph(String name) {
        this.name = name;
        setName(name);
        setChineseName(nameMap.get(name));
        setBench(benchMap.getOrDefault(name, ""));
        setSharpe(sharpeMap.getOrDefault(name, 0.0));

        //System.out.println( " bench is " + bench );
        setSize1(sizeMap.getOrDefault(name, 0L));
        if (NORMAL_STOCK.test(name)) {
            this.setNavigableMap(priceMapBar.get(name));
            this.computeWtd();
        } else {
            this.setNavigableMap(new ConcurrentSkipListMap<>());
        }
    }

    public void fillInGraphHK(String name) {
        //System.out.println(" filling HK " + name);
        this.name = name;
        setName(name);
        setChineseName(HKStock.hkNameMap.getOrDefault(name,""));

        if (HKData.hkPriceBar.containsKey(name) && HKData.hkPriceBar.get(name).size()>0) {
            this.setNavigableMap(HKData.hkPriceBar.get(name));
        } else {
            this.setNavigableMap(new ConcurrentSkipListMap<>());
        }
    }

//    public void fillInGraphHKGen(String name, Map<String, NavigableMap<? extends  Temporal, SimpleBar>> mp) {
//        //System.out.println(" filling HK " + name);
//        this.name = name;
//        setName(name);
//        //setChineseName(HKStock.hkNameMap.getOrDefault(name,""));
//
//        if (mp.containsKey(name) && mp.get(name).size()>0) {
//            this.setNavigableMap(mp.get(name));
//        } else {
//            this.setNavigableMap(new ConcurrentSkipListMap<>());
//        }
//    }

    @Override
    public void refresh() {
        fillInGraph(name);
    }

    public void refresh(Consumer<String> cons) {
        cons.accept(name);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = (int) (getHeight() - 70);
        min = getMin();
        max = getMax();
        minRtn = getMinRtn();
        maxRtn = getMaxRtn();
        last = 0;
        rtn = getRtn();

        int x = 5;
        for (LocalTime lt : tm.keySet()) {
            openY = getY(tm.floorEntry(lt).getValue().getOpen());
            highY = getY(tm.floorEntry(lt).getValue().getHigh());
            lowY = getY(tm.floorEntry(lt).getValue().getLow());
            closeY = getY(tm.floorEntry(lt).getValue().getClose());

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
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11
                        && lt.getMinute() == 30)) {
                    g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 40);
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
        g2.drawString(Double.toString(getLast()), getWidth() * 3 / 8, 15);

        g2.drawString("P%:" + Double.toString(getCurrentPercentile()), getWidth() * 4 / 8, 15);
        g2.drawString("涨:" + Double.toString(getRtn()) + "%", getWidth() * 5 / 8, 15);
        g2.drawString("高 " + (getAMMaxT()), getWidth() * 6 / 8, 15);
        //g2.drawString("低 " + (getAMMinT()), getWidth() * 7 * 8, 15);
        g2.drawString("夏 " + sharpe, getWidth() * 7 / 8, 15);

        //below               
        g2.drawString("开 " + Double.toString(getRetOPC()), 5, getHeight() - 25);
        g2.drawString("一 " + Double.toString(getFirst1()), getWidth() / 9, getHeight() - 25);
        g2.drawString("量 " + Long.toString(getSize1()), 5, getHeight() - 5);
        g2.drawString("位Y " + Integer.toString(getCurrentMaxMinYP()), getWidth() / 9, getHeight() - 5);
        g2.drawString("十  " + Double.toString(getFirst10()), getWidth() / 9 + 75, getHeight() - 25);
        g2.drawString("V比 " + Double.toString(getSizeSizeYT()), getWidth() / 9 + 75, getHeight() - 5);

        g2.setColor(Color.BLUE);
        g2.drawString("开% " + Double.toString(getOpenYP()), getWidth() / 9 * 2 + 70, getHeight() - 25);
        g2.drawString("收% " + Double.toString(getCloseYP()), getWidth() / 9 * 3 + 70, getHeight() - 25);
        g2.drawString("CH " + Double.toString(getRetCHY()), getWidth() / 9 * 4 + 70, getHeight() - 25);
        g2.drawString("CL " + Double.toString(getRetCLY()), getWidth() / 9 * 5 + 70, getHeight() - 25);
        g2.drawString("和 " + Double.toString(round(100d * (getRetCLY() + getRetCHY())) / 100d), getWidth() / 9 * 6 + 70, getHeight() - 25);
        g2.drawString("HO " + Double.toString(getHO()), getWidth() / 9 * 7 + 50, getHeight() - 25);

        g2.drawString("低 " + Integer.toString(getMinTY()), getWidth() / 9 * 2 + 70, getHeight() - 5);
        g2.drawString("高 " + Integer.toString(getMaxTY()), getWidth() / 9 * 4 - 90 + 70, getHeight() - 5);
        g2.drawString("CO " + Double.toString(getRetCO()), getWidth() / 9 * 4 + 70, getHeight() - 5);
        g2.drawString("CC " + Double.toString(getRetCC()), getWidth() / 9 * 5 + 70, getHeight() - 5);
        g2.drawString("振" + Double.toString(getRangeY()), getWidth() / 9 * 6 + 70, getHeight() - 5);
        g2.drawString("折R " + Double.toString(getHOCHRangeRatio()), getWidth() / 9 * 7 + 50, getHeight() - 5);
        g2.drawString("晏 " + Integer.toString(getPMchgY()), getWidth() - 60, getHeight() - 5);

        g2.setColor(new Color(0, colorGen(wtdP), 0));
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

    public static int colorGen(int wtd) {
        return Math.max(0, Math.min(255, 230 * (100 - wtd) / 100));
    }

    public void computeWtd() {

        double current;
        double maxT;
        double minT;

        if (priceMapBar.containsKey(name) && priceMapBar.get(name).size() > 0) {
            current = priceMapBar.get(name).lastEntry().getValue().getClose();
            maxT = priceMapBar.get(name).entrySet().stream().max(Utility.BAR_HIGH).map(Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);
            minT = priceMapBar.get(name).entrySet().stream().min(Utility.BAR_LOW).map(Entry::getValue)
                    .map(SimpleBar::getHigh).orElse(0.0);

        } else {
            current = 0.0;
            maxT = Double.MIN_VALUE;
            minT = Double.MAX_VALUE;
        }

        wtdP = (int) Math.round(100d * (current - applyAllDouble(Math::min, minT, ChinaPosition.wtdMinMap.getOrDefault(name, 0.0)))
                / (applyAllDouble(Math::max, maxT, ChinaPosition.wtdMaxMap.getOrDefault(name, 0.0))
                - applyAllDouble(Math::min, minT, ChinaPosition.wtdMinMap.getOrDefault(name, 0.0))));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(50, 50);
    }


    double getMin() {
        return (tm.size() > 0) ? tm.entrySet().stream().min(Utility.BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0) : 0.0;
    }

    double getMax() {
        return (tm.size() > 0) ? tm.entrySet().stream().max(Utility.BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0) : 0.0;
    }

    double getRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = tm.lastEntry().getValue().getClose();
            return (double) round((finalP / initialP - 1) * 1000d) / 10d;
        }
        return 0.0;
    }

    double getRangeY() {
        return Utility.noZeroArrayGen(name, minMapY, maxMapY) ? round(100d * (maxMapY.get(name) / minMapY.get(name) - 1)) / 100d : 0.0;
    }

    double getMaxRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0);
            double finalP = getMax();
            return abs(finalP - initialP) > 0.0001 ? (double) round((finalP / initialP - 1) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    double getMinRtn() {
        if (tm.size() > 0) {
            double initialP = tm.entrySet().stream().findFirst().map(Entry::getValue).map(e -> e.getOpen()).orElse(0.0);
            double finalP = getMin();
            return (Math.abs(finalP - initialP) > 0.0001) ? (double) round(log(getMin() / initialP) * 1000d) / 10d : 0;
        }
        return 0.0;
    }

    double getLast() {
        return round(100d * priceMap.getOrDefault(name, (tm.size() > 0) ? tm.lastEntry().getValue().getClose() : 0.0)) / 100d;
    }

    long getSize1() {
        return sizeMap.getOrDefault(name, 0L);
    }

    double getRetOPC() {
        return (Utility.noZeroArrayGen(name, closeMap, openMap)) ? round(1000d * (openMap.get(name) / closeMap.get(name) - 1)) / 10d : 0.0;
    }

    double getFirst1() {
        return (Utility.normalMapGen(name, priceMapBar) && priceMapBar.get(name).containsKey(Utility.AMOPENT) && Utility.noZeroArrayGen(name, openMap))
                ? round(1000d * (priceMapBar.get(name).floorEntry(Utility.AMOPENT).getValue().getClose() / openMap.get(name) - 1)) / 10d : 0.0;
    }

    double getFirst10() {
        return (Utility.normalMapGen(name, priceMapBar) && priceMapBar.get(name).containsKey(Utility.AMOPENT) && Utility.noZeroArrayGen(name, openMap))
                ? round(1000d * (priceMapBar.get(name).floorEntry(Utility.AM940T).getValue().getClose() / openMap.get(name) - 1)) / 10d : 0.0;
    }

    int getCurrentMaxMinYP() {
        return (Utility.noZeroArrayGen(name, minMapY, priceMap)) ? (int) min(100, round(100d * (priceMap.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

    double getOpenYP() {
        return (Utility.noZeroArrayGen(name, minMapY)) ? (int) min(100, round(100d * (openMapY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

    int getCloseYP() {
        return (Utility.noZeroArrayGen(name, minMapY)) ? (int) min(100, round(100d * (closeMapY.get(name) - minMapY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }

    double getCurrentPercentile() {
        return (Utility.noZeroArrayGen(name, priceMap, maxMap, minMap)) ? min(100.0, round(100d * ((priceMap.get(name) - minMap.get(name)) / (maxMap.get(name) - minMap.get(name))))) : 0.0;
    }

    double getRetCHY() {
        return (Utility.noZeroArrayGen(name, closeMapY, maxMapY)) ? min(100.0, round(1000d * log(closeMapY.get(name) / maxMapY.get(name)))) / 10d : 0.0;
    }

    double getHO() {
        return round(1000d * ofNullable(retHOY.get(name)).orElse(0.0)) / 10d;
    }

    double getHOCHRangeRatio() {
        return (Utility.noZeroArrayGen(name, retHOY, retCHY, minMapY, maxMapY))
                ? round((retHOY.get(name) - retCHY.get(name)) / ((maxMapY.get(name) / minMapY.get(name) - 1)) * 10d) / 10d : 0.0;
    }

    double getRetCLY() {
        return (Utility.noZeroArrayGen(name, closeMapY, minMapY)) ? min(100.0, round(1000d * log(closeMapY.get(name) / minMapY.get(name)))) / 10d : 0.0;
    }

    double getRetCC() {
        return round(1000d * ofNullable(retCCY.get(name)).orElse(0.0)) / 10d;
    }

    double getRetCO() {
        return round(1000d * ofNullable(retCOY.get(name)).orElse(0.0)) / 10d;
    }

    int getMinTY() {
        return ofNullable(minTY.get(name)).orElse(0);
    }

    int getMaxTY() {
        return ofNullable(maxTY.get(name)).orElse(0);
    }

    LocalTime getAMMinT() {
        return (tm.size() > 0 && tm.firstKey().isBefore(Utility.AMCLOSET) && tm.lastKey().isAfter(Utility.AMOPENT))
                ? tm.entrySet().stream().filter(Utility.AM_PRED).min(Utility.BAR_LOW).map(Entry::getKey).orElse(Utility.AMOPENT) : Utility.AMOPENT;
    }

    LocalTime getAMMaxT() {
        return (!tm.isEmpty() & tm.size() > 2 && tm.firstKey().isBefore(Utility.AMCLOSET) && tm.lastKey().isAfter(Utility.AMOPENT))
                ? tm.entrySet().stream().filter(Utility.AM_PRED).max(Utility.BAR_HIGH).map(Entry::getKey).orElse(Utility.AMOPENT) : Utility.AMOPENT;
    }

    Double getSizeSizeY() {
        return (Utility.noZeroArrayGen(name, ChinaStock.sizeMap, sizeY)) ? round(10d * sizeMap.get(name) / sizeY.get(name)) / 10d : 0.0;
    }

    Double getSizeSizeYT() {
        if (Utility.normalMapGen(name, sizeTotalMapYtd, sizeTotalMap)) {
            LocalTime lastEntryTime = sizeTotalMap.get(name).lastEntry().getKey();
            double yest = ofNullable(sizeTotalMapYtd.get(name).floorEntry(lastEntryTime)).map(Entry::getValue).orElse(0.0);
            return yest != 0.0 ? round(10d * sizeTotalMap.get(name).lastEntry().getValue() / yest) / 10d : 0.0;
        }
        return 0.0;
    }

    int getPMchgY() {
        return (Utility.noZeroArrayGen(name, minMapY, amCloseY, closeMapY, maxMapY))
                ? (int) min(100, round(100d * (closeMapY.get(name) - amCloseY.get(name)) / (maxMapY.get(name) - minMapY.get(name)))) : 0;
    }
}
