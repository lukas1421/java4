package graph;

import TradeType.Trade;
import apidemo.ChinaPosition;
import utility.Utility;

import static utility.Utility.TIMEMAX;
import static utility.Utility.getStr;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import static java.lang.Math.log;
import static java.lang.Math.round;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

public final class GraphPnl extends JComponent {

    final static int WIDTH_PNL = 5;

    static NavigableMap<LocalTime, Double> tm;
    static NavigableMap<LocalTime, Double> mtmMap;
    static NavigableMap<LocalTime, Double> tradeMap;
    static NavigableMap<LocalTime, Double> netMap;
    static NavigableMap<LocalTime, Double> buyMap;
    static NavigableMap<LocalTime, Double> sellMap;
    static NavigableMap<LocalTime, Double> netDeltaMap;
    static NavigableMap<String, Double> benchMap;
    static Map<String, Double> benchMtmMap;
    static LinkedList<String> pnl1mList;
    String winner1;
    String winner2;
    String winner3;
    String loser1;
    String loser2;
    String loser3;
    volatile String big1;
    volatile String big2;
    volatile String big3;


    static double openDelta;
    static double boughtDelta;
    static double soldDelta;
    static double currentDelta;
    static double netYtdPnl;
    static double todayNetPnl;
    static double mtmPnl;
    static double mtmDeltaSharpe;

    static double minuteNetPnlSharpe;

    static double buyPnl;
    static double sellPnl;

    private int height;
    private double min;
    private double max;
    private int close;
    private int last = 0;
    private double rtn = 0;

    String name = "";
    String chineseName = "";
    long activity;
    private LocalTime maxAMT;
    private LocalTime minAMT;
    private volatile int size;

    static final LocalTime AMCLOSET = LocalTime.of(11, 30);
    static final LocalTime AMOPENT = LocalTime.of(9, 30);
    static final LocalTime PMOPENT = LocalTime.of(13, 0);
    static final LocalTime PMCLOSET = LocalTime.of(15, 0);
    static final Predicate<? super Map.Entry<LocalTime, ? extends Number>> AMPRED = e -> e.getKey().isBefore(AMCLOSET);
    static final Predicate<? super Map.Entry<LocalTime, ? extends Number>> PMPRED = e -> e.getKey().isAfter(PMOPENT);
    static final Predicate<? super Map.Entry<LocalTime, ? extends Number>> TRADING_PRED = e
            -> (e.getKey().isAfter(LocalTime.of(9, 15)) && e.getKey().isBefore(LocalTime.of(11, 31)))
            || (e.getKey().isAfter(LocalTime.of(12, 59)) && e.getKey().isBefore(LocalTime.of(15, 1)));

//    public GraphPnl(NavigableMap<LocalTime, Double> tm) {    
//        this.tm = tm.entrySet().stream().filter(e->e.getValue()!=0).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue, (u,v)->u, ConcurrentSkipListMap::new));
//    } 
    public GraphPnl() {
        name = "";
        chineseName = "";
        maxAMT = LocalTime.of(9, 30);
        minAMT = LocalTime.of(9, 30);
        tm = new ConcurrentSkipListMap<>();
        mtmMap = new ConcurrentSkipListMap<>();
        tradeMap = new ConcurrentSkipListMap<>();
        netMap = new ConcurrentSkipListMap<>();
        netDeltaMap = new ConcurrentSkipListMap<>();
        benchMap = new ConcurrentSkipListMap<>();
        benchMtmMap = new HashMap<>();
    }

    public GraphPnl(String s) {
        this.name = s;
    }

    public void setMtmPnl(double p) {
        mtmPnl = Math.round(100d * p) / 100d;
    }

    public void setNetPnlYtd(double p) {
        netYtdPnl = Math.round(100d * p) / 100d;
    }

    public void setTodayPnl(double p) {
        todayNetPnl = Math.round(100d * p) / 100d;
    }

    public void setOpenDelta(double d) {
        openDelta = d;
    }

    public void setBoughtDelta(double d) {
        boughtDelta = d;
    }

    public void setCurrentDelta(double d) {
        currentDelta = d;
    }

    public void setSoldDelta(double d) {
        soldDelta = d;
    }

    public void setBuyPnl(double p) {
        buyPnl = Math.round(100d * p) / 100d;
    }

    public void setSellPnl(double p) {
        sellPnl = Math.round(100d * p) / 100d;
    }

    public void setNetDeltaMap(NavigableMap<LocalTime, Double> m) {

        netDeltaMap = (m != null) ? m.entrySet().stream().filter(TRADING_PRED)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new))
                : new ConcurrentSkipListMap<>();

        //System.out.println(" print delta map in graph pnl ");
        //netDeltaMap.entrySet().stream().filter(e->e.getKey().isBefore(LocalTime.of(9,45))).forEach(System.out::println);
        //netDeltaMap.entrySet().stream().filter(e->e.getKey().isAfter(LocalTime.of(14,15))).forEach(System.out::println);
    }

    public void setBenchMap(NavigableMap<String, Double> m) {
        this.benchMap = m;
    }

    public void setMtmBenchMap(Map<String, Double> m) {
        this.benchMtmMap = m;
    }

    public void setNavigableMap(NavigableMap<LocalTime, Double> mtmmap, NavigableMap<LocalTime, Double> trademap, NavigableMap<LocalTime, Double> netmap) {

        mtmMap = (mtmmap != null) ? mtmmap.entrySet().stream().filter(TRADING_PRED)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new))
                : new ConcurrentSkipListMap<>();

        tradeMap = (trademap != null) ? trademap.entrySet().stream().filter(TRADING_PRED)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new))
                : new ConcurrentSkipListMap<>();

        netMap = (netmap != null) ? netmap.entrySet().stream().filter(TRADING_PRED)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new))
                : new ConcurrentSkipListMap<>();

        //System.out.println(" printing net map ");
        //netMap.entrySet().stream().filter(e -> e.getKey().isAfter(LocalTime.of(14, 45))).forEach(System.out::println);
        tm = (netmap != null) ? netmap.entrySet().stream().filter(TRADING_PRED)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> u, ConcurrentSkipListMap::new))
                : new ConcurrentSkipListMap<>();
    }

    public void setBuySellPnlMap(NavigableMap<LocalTime, Double> buym, NavigableMap<LocalTime, Double> sellm) {
        buyMap = (buym != null && buym.size() > 0 && buym.lastEntry().getValue() != 0.0) ? buym : new ConcurrentSkipListMap<>();
        sellMap = (sellm != null && sellm.size() > 0 && sellm.lastEntry().getValue() != 0.0) ? sellm : new ConcurrentSkipListMap<>();
    }

    public void setPnl1mChgMap(LinkedList<String> m) {
        pnl1mList = m;
        String t;
        winner1 = (t = m.poll()) != null ? t : "";
        winner2 = (t = m.poll()) != null ? t : "";
        winner3 = (t = m.poll()) != null ? t : "";

        loser1 = (t = m.pollLast()) != null ? t : "";
        loser2 = (t = m.pollLast()) != null ? t : "";
        loser3 = (t = m.pollLast()) != null ? t : "";
    }

    public void setBigKiyodoMap(LinkedList<String> m) {
        //pnl1mList = m;
        String t;
        big1 = (t = m.poll()) != null ? t : "";
        big2 = (t = m.poll()) != null ? t : "";
        big3 = (t = m.poll()) != null ? t : "";
    }


    //NavigableMap<LocalTime,Double> getTreeMap() {return this.tm;}
    @Override
    public void setName(String s) {
        this.name = s;
    }

    public void setChineseName(String s) {
        chineseName = (s == null) ? "" : s;
    }

    public void setMinuteNetPnlSharpe(double d) {
        minuteNetPnlSharpe = Math.round(100d * d) / 100d;
    }

    public void setMtmDeltaSharpe(double d) {
        mtmDeltaSharpe = Math.round(100d * d) / 100d;
    }

    public void setMaxAMT(LocalTime t) {
        this.maxAMT = (t != null) ? t : TIMEMAX;
    }

    public void setMinAMT(LocalTime t) {
        this.minAMT = (t != null) ? t : TIMEMAX;
    }

    public void fillInGraph(String nam) {
    }

    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g.setColor(Color.black);

        height = (int) (getHeight() - 100);
        min = getMin();
        max = getMax();

        double minDelta = getMinDelta();
        double maxDelta = getMaxDelta();

        //System.out.println("printing component min delta " + minDelta);
        //System.out.println("printing component max delta " + maxDelta);
        LocalTime netmint = getNetPnlMinTime();
        LocalTime netmaxt = getNetPnlMaxTime();

        rtn = getReturn();
        int x = 5;

        g2.setFont(g.getFont().deriveFont(g.getFont().getSize() * 1.5F));
        g2.setStroke(new BasicStroke(2));

        last = 0;
        //int x = 5;
        g2.setColor(Color.CYAN);
        for (LocalTime lt : netDeltaMap.keySet()) {
            close = getYDelta(netDeltaMap.floorEntry(lt).getValue());
            last = (last == 0) ? close : last;
            g.drawLine(x, last, x + WIDTH_PNL, close);
            last = close;
            x += WIDTH_PNL;
        }

        x = 5;
        last = 0;
        g2.setColor(Color.RED);
        for (LocalTime lt : mtmMap.keySet()) {
            close = getY(mtmMap.floorEntry(lt).getValue());
            last = (last == 0) ? close : last;
            g.drawLine(x, last, x + WIDTH_PNL, close);
            last = close;

            x += WIDTH_PNL;
        }
        if (mtmMap.size() > 0) {
            g.drawString("MTM: " + Math.round(100d * Optional.ofNullable(mtmMap.lastEntry()).map(Entry::getValue).orElse(0.0)) / 100d, x + WIDTH_PNL, last + 10);
        }

        x = 5;
        last = 0;
        g2.setColor(Color.BLUE);
        int mult = 1;

        if (ChinaPosition.buySellTogether) {
            //System.out.println(" pnl together");
            for (LocalTime lt : tradeMap.keySet()) {
                close = getY(tradeMap.floorEntry(lt).getValue());
                last = (last == 0) ? close : last;
                g.drawLine(x, last, x + WIDTH_PNL, close);
                last = close;
                x += WIDTH_PNL;
//                if (ChinaPosition.tradesMap.containsKey(name) && ChinaPosition.tradesMap.get(name).containsKey(lt)) {
//                    g.drawString(ChinaStockHelper.getStr(((Trade) ChinaPosition.tradesMap.get(name).get(lt)).getSize()), x - 20, close - (mult * 50));
//                    g.drawLine(x, close - (mult * 40), x, close);
//                    mult = -1 * mult;
//                }
                if (ChinaPosition.tradesMap.containsKey(name)
                        && ChinaPosition.tradesMap.get(name).subMap(lt, true, lt.plusMinutes(1), false).entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() != 0).count() > 0) {

                    double pos = ChinaPosition.tradesMap.get(name).subMap(lt, true, lt.plusMinutes(1), false).values().stream().map(e -> (Trade) e).mapToInt(Trade::getSize).filter(n -> n != 0).sum();
                    g.drawString(Utility.getStr(pos), x - 20, close - (mult * 50));
                    g.drawLine(x, close - (mult * 40), x, close);
                    mult = -1 * mult;

                }

            }
            if (tradeMap.size() > 0) {
                g.drawString("Trade: " + Math.round(100d * Optional.ofNullable(tradeMap.lastEntry()).map(Entry::getValue).orElse(0.0)) / 100d, x + WIDTH_PNL, last + 10);
            }

        } else {

            //System.out.println(" separate ");
            g2.setColor(new Color(50, 150, 0));
            for (LocalTime lt : buyMap.keySet()) {
                close = getY(buyMap.floorEntry(lt).getValue());
                last = (last == 0) ? close : last;
                g.drawLine(x, last, x + WIDTH_PNL, close);
                last = close;
                x += WIDTH_PNL;
                if (ChinaPosition.tradesMap.containsKey(name)
                        && ChinaPosition.tradesMap.get(name).subMap(lt, true, lt.plusMinutes(1), false).entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() > 0).count() > 0) {

                    double pos = ChinaPosition.tradesMap.get(name).subMap(lt, true, lt.plusMinutes(1), false).values().stream().map(e -> (Trade) e).mapToInt(Trade::getSize).filter(n -> n > 0).sum();
                    g.drawString(Utility.getStr(pos), x - 20, close - (mult * 50));
                    g.drawLine(x, close - (mult * 40), x, close);
                    mult = -1 * mult;

                }
            }

            if (buyMap.size() > 0) {
                g.drawString("BUY: " + Math.round(100d * Optional.ofNullable(buyMap.lastEntry()).map(Entry::getValue).orElse(0.0)) / 100d, x + WIDTH_PNL, last + 10);
            }

            x = 5;
            last = 0;
            g2.setColor(Color.BLUE);
            for (LocalTime lt : sellMap.keySet()) {
                close = getY(sellMap.floorEntry(lt).getValue());
                last = (last == 0) ? close : last;
                g.drawLine(x, last, x + WIDTH_PNL, close);
                last = close;
                x += WIDTH_PNL;
                if (ChinaPosition.tradesMap.containsKey(name) && ChinaPosition.tradesMap.get(name).subMap(lt, true, lt.plusMinutes(1), false)
                        .entrySet().stream().filter(e -> ((Trade) e.getValue()).getSize() < 0).count() > 0) {
                    double pos = ChinaPosition.tradesMap.get(name).subMap(lt, true, lt.plusMinutes(1), false).values().stream().map(e -> (Trade) e).mapToInt(Trade::getSize).filter(n -> n < 0).sum();
                    g.drawString(Utility.getStr(pos), x - 20, close - (mult * 50));
                    g.drawLine(x, close - (mult * 40), x, close);
                    mult = -1 * mult;
                }
            }
            if (sellMap.size() > 0) {
                g.drawString("SELL: " + Math.round(100d * Optional.ofNullable(sellMap.lastEntry()).map(Entry::getValue).orElse(0.0)) / 100d, x + WIDTH_PNL, last + 10);
            }
        }

        x = 5;
        last = 0;
        g2.setColor(Color.BLACK);
        for (LocalTime lt : netMap.keySet()) {
            close = getY(netMap.floorEntry(lt).getValue());

            last = (last == 0) ? close : last;
            g.drawLine(x, last, x + WIDTH_PNL, close);
            last = close;
            x += WIDTH_PNL;

            if (lt.equals(netMap.firstKey())) {
                g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 25);
            } else {
                if (lt.getMinute() == 0 || (lt.getHour() != 9 && lt.getHour() != 11 && lt.getMinute() == 30)) {
                    g.drawString(lt.truncatedTo(ChronoUnit.MINUTES).toString(), x, getHeight() - 25);
                }
            }

            if (lt.equals(netmint)) {
                g.drawString(getStr("低", lt, r(netMap.getOrDefault(lt, 0.0))), x - 10, last + 8);
            } else if (lt.equals(netmaxt)) {
                g.drawString(getStr("高", lt, r(netMap.getOrDefault(lt, 0.0))), x - 10, last - 30);
            }
        }

        g.drawString("NET: " + Math.round(100d * Optional.ofNullable(netMap.lastEntry()).map(Entry::getValue).orElse(0.0)) / 100d, x + WIDTH_PNL, last - 10);

        g2.setColor(Color.red);
        g2.setStroke(new BasicStroke(3));

        g2.drawString(Double.toString(min), getWidth() - 80, getHeight() - 5);
        g2.drawString(Double.toString(max), getWidth() - 80, 15);
        g2.drawString(Double.toString(minDelta), 5, getHeight() - 30);
        g2.drawString(Double.toString(maxDelta), 5, 40);

        g2.drawString(name, 5, 15);
        g2.drawString(chineseName, getWidth() / 7, 15);
        g2.drawString(Double.toString(getLast()), getWidth() / 7 * 2, 15);

        g2.drawString("A低:" + getAMMinT().toString(), getWidth() / 7 * 3, 15);
        g2.drawString(Double.toString(getMinAM()), getWidth() / 7 * 4, 15);

        g2.drawString("A高:" + getAMMaxT().toString(), getWidth() / 7 * 5, 15);
        g2.drawString(Double.toString(getMaxAM()), getWidth() / 7 * 6, 15);

        g2.drawString("P低:" + getPMMinT().toString(), getWidth() / 7 * 3, 30);
        g2.drawString(Double.toString(getMinPM()), getWidth() / 7 * 4, 30);

        g2.drawString("P高:" + getPMMaxT().toString(), getWidth() / 7 * 5, 30);
        g2.drawString(Double.toString(getMaxPM()), getWidth() / 7 * 6, 30);

        g2.drawString("低:" + getMinT().toString(), getWidth() / 7 * 3, 45);
        g2.drawString(Double.toString(getMin()), getWidth() / 7 * 4, 45);

        g2.drawString("高:" + getMaxT().toString(), getWidth() / 7 * 5, 45);
        g2.drawString(Double.toString(getMax()), getWidth() / 7 * 6, 45);

        g2.drawString("925: " + Double.toString(get925()), 5, getHeight() - 5);
        g2.drawString("930: " + Double.toString(get930()), getWidth() / 8, getHeight() - 5);
        g2.drawString("935: " + Double.toString(get935()), getWidth() / 8 * 2, getHeight() - 5);
        g2.drawString("940: " + Double.toString(get940()), getWidth() / 8 * 3, getHeight() - 5);
        g2.drawString("P%: " + Double.toString(getDayVRPercentile()), getWidth() / 8 * 5 + 15, getHeight() - 5);
        g2.drawString("pmP%: " + Double.toString(getPMVRPercentile()), getWidth() / 8 * 6 + 15, getHeight() - 5);

        g2.setColor(Color.BLUE);

        //add percent
        g2.drawString("大: " + Long.toString(round(benchMap.getOrDefault("大", 0.0) / 1000d)) + " k   " + Double.toString(round(100d * benchMap.getOrDefault("大", 0.0) / currentDelta)) + " % ",
                getWidth() / 7 * 5, getHeight() / 7 + 10);
        g2.drawString("主板: " + Long.toString(round(benchMap.getOrDefault("主板", 0.0) / 1000d)) + " k    " + Double.toString(round(100d * benchMap.getOrDefault("主板", 0.0) / currentDelta)) + " % ",
                getWidth() / 7 * 5, getHeight() / 7 + 30);
        g2.drawString("沪深: " + Long.toString(round(benchMap.getOrDefault("沪深", 0.0) / 1000d)) + " k     " + Double.toString(round(100d * benchMap.getOrDefault("沪深", 0.0) / currentDelta)) + " % ",
                getWidth() / 7 * 5, getHeight() / 7 + 50);
        g2.drawString("创: " + Long.toString(round(benchMap.getOrDefault("创", 0.0) / 1000d)) + " k     " + Double.toString(round(100d * benchMap.getOrDefault("创", 0.0) / currentDelta)) + " % ",
                getWidth() / 7 * 5, getHeight() / 7 + 70);
        g2.drawString("小: " + Long.toString(round((benchMap.getOrDefault("小", 0.0)) / 1000d)) + " k     " + Double.toString(round(100d * benchMap.getOrDefault("小", 0.0) / currentDelta)) + " % ",
                getWidth() / 7 * 5, getHeight() / 7 + 90);
        g2.drawString("中证: " + Long.toString(round(benchMap.getOrDefault("中证", 0.0) / 1000d)) + " k     " + Double.toString(round(100d * benchMap.getOrDefault("中证", 0.0) / currentDelta)) + " % ",
                getWidth() / 7 * 5, getHeight() / 7 + 110);

//        Map<Integer, String> res = getPtfCompoString(benchMap, currentDelta, benchMtmMap);
//        
//        g2.drawString(res.getOrDefault(0,""), getWidth()/7*5 - 30, getHeight()/7+10);
//        g2.drawString(res.getOrDefault(1,""), getWidth()/7*5 - 30, getHeight()/7+30);
//        g2.drawString(res.getOrDefault(2,""), getWidth()/7*5 - 30, getHeight()/7+50);
//        g2.drawString(res.getOrDefault(3,""), getWidth()/7*5 - 30, getHeight()/7+70);
//        g2.drawString(res.getOrDefault(4,""), getWidth()/7*5 - 30, getHeight()/7+90);
//        g2.drawString(res.getOrDefault(5,""), getWidth()/7*5 - 30, getHeight()/7+110);
        g2.drawString(" DELTA: " + Double.toString(Math.round(currentDelta / 1000d)) + " K", getWidth() / 7 * 6, getHeight() / 3 - 10);
        g2.setColor(Color.RED);

        g2.setColor(netYtdPnl > 0.0 ? new Color(50, 150, 0) : Color.red);
        g2.drawString("Ytd Net pnl: " + Double.toString(netYtdPnl), getWidth() / 7 * 6, getHeight() / 3 + 20);

        g2.setColor(buyPnl > 0.0 ? new Color(50, 150, 0) : Color.red);
        g2.drawString("Buy pnl: " + Double.toString(buyPnl), getWidth() / 7 * 6, getHeight() / 3 + 40);

        g2.setColor(sellPnl > 0.0 ? new Color(50, 150, 0) : Color.red);
        g2.drawString("Sell pnl: " + Double.toString(sellPnl), getWidth() / 7 * 6, getHeight() / 3 + 60);

        g2.setColor(mtmPnl > 0.0 ? new Color(50, 150, 0) : Color.red);
        g2.drawString("MTM pnl: " + Double.toString(mtmPnl), getWidth() / 7 * 6, getHeight() / 3 + 80);

        //g2.setFont(g.getFont().deriveFont(25F));
        g2.setColor(todayNetPnl > 0.0 ? new Color(50, 150, 0) : Color.red);
        g2.drawString("Net pnl: " + Double.toString(todayNetPnl), getWidth() / 7 * 6, getHeight() / 3 + 110);
        g2.drawString("Mtm Sharpe: " + Double.toString(mtmDeltaSharpe), getWidth() / 7 * 6, getHeight() / 3 + 130);
        g2.drawString("netpnl Sharpe: " + Double.toString(minuteNetPnlSharpe), getWidth() / 7 * 5, getHeight() / 3 + 130);

        g2.drawString("big1 " +big1, getWidth() / 7 * 5, getHeight() / 3 + 150);
        g2.drawString("big2 " +big2, getWidth() / 7 * 5, getHeight() / 3 + 170);
        g2.drawString("big3 " +big3, getWidth() / 7 * 5, getHeight() / 3 + 190);

        g2.setColor(Color.red);
        g2.setFont(g.getFont().deriveFont(20F));
        g2.drawString("Open Delta: " + Double.toString(Math.round(openDelta / 1000d)) + " K", getWidth() / 7 * 6, getHeight() / 3 + 150);
        g2.drawString("Bought Delta: " + Double.toString(Math.round(boughtDelta / 1000d)) + " K", getWidth() / 7 * 6, getHeight() / 3 + 170);
        g2.drawString("Sold Delta: " + Double.toString(Math.round(soldDelta / 1000d)) + " K", getWidth() / 7 * 6, getHeight() / 3 + 190);
        g2.drawString("rtn on +Delta: " + Double.toString(Math.round(buyPnl / boughtDelta * 1000d) / 10d) + " %", getWidth() / 7 * 6, getHeight() / 3 + 210);
        g2.drawString("rtn on -Delta: " + Double.toString(Math.round(-1 * sellPnl / soldDelta * 1000d) / 10d) + " %", getWidth() / 7 * 6, getHeight() / 3 + 230);

        int heightStart = getHeight() / 3 + 250;
        g2.drawString("w1 " + winner1, getWidth() / 7 * 6, heightStart);
        g2.drawString("w2 " + winner2, getWidth() / 7 * 6, heightStart + 20);
        g2.drawString("w3 " + winner3, getWidth() / 7 * 6, heightStart + 40);
        g2.drawString("l1 " + loser1, getWidth() / 7 * 6, heightStart + 80);
        g2.drawString("l2 " + loser2, getWidth() / 7 * 6, heightStart + 100);
        g2.drawString("l3 " + loser3, getWidth() / 7 * 6, heightStart + 120);

        g2.setColor(Color.black);
        g2.setFont(g.getFont().deriveFont(30F));
        g2.drawString(LocalTime.now().toString(), getWidth() - 170, 50);
    }

    /**
     * Convert bar value to y coordinate.
     */
    private int getY(double v) {
        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height + .5;
        return height - (int) val + 50;
    }

    private int getYDelta(double v) {
        double maxD = getMaxDelta();
        double minD = getMinDelta();
        double span = maxD - minD;
        double pct = (v - minD) / span;
        double val = pct * height + .5;
        return height - (int) val + 50;
    }

    public <T> Comparator<T> reverseThis(Comparator<T> comp) {
        return comp.reversed();
    }

    Map<Integer, String> getPtfCompoString(NavigableMap<String, Double> bench, double delta, Map<String, Double> mtm) {
        Map<Integer, String> res = new LinkedHashMap<>();
        LinkedList<String> sortedBench = bench.entrySet().stream()
                .sorted(reverseThis(Comparator.comparingDouble(Map.Entry::getValue))).collect(Collectors.mapping(Map.Entry::getKey, Collectors.toCollection(LinkedList::new)));
        Iterator<String> it = sortedBench.iterator();

//        Map<String, Double> mtmPnlAll = ChinaPosition.openPositionMap.entrySet().stream().filter(e->e.getValue()>0)
//                .collect(Collectors.groupingBy(e-> ChinaStock.benchSimpleMap.getOrDefault(e.getKey(),""),
//                        Collectors.summingDouble(e-> (ChinaStock.priceMap.getOrDefault(e.getKey(),0.0) - ChinaStock.closeMap.getOrDefault(e.getKey(), 0.0))*(e.getValue()))));
        double netMtmAll = Double.MAX_VALUE;
        if (mtm.size() > 0) {
            netMtmAll = mtm.entrySet().stream().collect(Collectors.summingDouble(e -> e.getValue()));
        }
        //"大: " + Long.toString(round(benchMap.getOrDefault("大", 0.0)/1000d))+" k   " + Double.toString(round(100d*benchMap.getOrDefault("大", 0.0)/currentDelta))+ " % "
        int i = 0;
        while (it.hasNext()) {
            String s = it.next();
            String resStr = s + " : " + Long.toString(round(bench.getOrDefault(s, 0.0) / 1000d)) + " k " + Long.toString(round(100d * bench.getOrDefault(s, 0.0) / delta)) + " % "
                    + " ||||| 盈: " + round(mtm.getOrDefault(s, 0.0) / 100d) / 10d + " k   " + Long.toString(round(100d * mtm.getOrDefault(s, 0.0) / netMtmAll)) + " % ";

            res.put(i, resStr);
            i++;
        }
        return res;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(50, 50);
    }

    private double getMin() {
        return (ChinaPosition.buySellTogether)
                ? Math.round(100d * Stream.of(mtmMap, tradeMap, netMap).flatMap(m -> m.entrySet().stream()).min(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0)) / 100d
                : Math.round(100d * Stream.of(mtmMap, buyMap, sellMap, netMap).flatMap(m -> m.entrySet().stream()).min(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0)) / 100d;
    }

    private double getMax() {
        return (ChinaPosition.buySellTogether)
                ? Math.round(100d * Stream.of(mtmMap, tradeMap, netMap).flatMap(m -> m.entrySet().stream()).max(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0)) / 100d
                : Math.round(100d * Stream.of(mtmMap, buyMap, sellMap, netMap).flatMap(m -> m.entrySet().stream()).max(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0)) / 100d;
    }

    private double getMinDelta() {
        return Math.round(100d * netDeltaMap.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0)) / 100d;
    }

    private double getMaxDelta() {
        return Math.round(100d * netDeltaMap.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0)) / 100d;
    }

    LocalTime getNetPnlMaxTime() {
        return netMap.entrySet().stream().max(Map.Entry.comparingByValue()).map(Entry::getKey).orElse(LocalTime.MAX);
    }

    LocalTime getNetPnlMinTime() {
        return netMap.entrySet().stream().min(Map.Entry.comparingByValue()).map(Entry::getKey).orElse(LocalTime.MAX);
    }

    private double getMinAM() {
        return (tm.size() > 0 && tm.firstKey().isBefore(LocalTime.of(12, 0)))
                ? Math.round(100d * tm.entrySet().stream().filter(AMPRED).min(Map.Entry.comparingByValue()).get().getValue()) / 100d : 0.0;
    }

    private double getMaxAM() {
        if (tm.size() > 0 && tm.firstKey().isBefore(AMCLOSET)) {
            return Math.round(100d * tm.entrySet().stream().filter(AMPRED).max(Map.Entry.comparingByValue()).get().getValue()) / 100d;
        }
        return 0.0;
    }

    private double getMinPM() {
        if (tm.size() > 0 && tm.lastKey().isAfter(PMOPENT)) {
            return Math.round(100d * tm.entrySet().stream().filter(PMPRED).min(Map.Entry.comparingByValue()).get().getValue()) / 100d;
        }
        return 0.0;
    }

    private double getMaxPM() {
        if (tm.size() > 0 && tm.lastKey().isAfter(PMOPENT)) {
            return Math.round(100d * tm.entrySet().stream().filter(PMPRED).max(Map.Entry.comparingByValue()).get().getValue()) / 100d;
        }
        return 0.0;
    }

    private double get925() {
        return Math.round(10d * Optional.ofNullable(tm.get(LocalTime.of(9, 25))).orElse(0.0)) / 10d;
    }

    private double get930() {
        return Math.round(10d * Optional.ofNullable(tm.get(LocalTime.of(9, 30))).orElse(0.0)) / 10d;
    }

    private double get935() {
        return Math.round(10d * Optional.ofNullable(tm.get(LocalTime.of(9, 35))).orElse(0.0)) / 10d;
    }

    private double get940() {
        return Math.round(10d * Optional.ofNullable(tm.get(LocalTime.of(9, 40))).orElse(0.0)) / 10d;
    }

    private double getReturn() {
        return (tm.size() > 0) ? (double) 100 * Math.round(log(tm.lastEntry().getValue() / tm.firstEntry().getValue()) * 1000d) / 1000d : 0.0;
    }

    private double getMaxRtn() {
        return (tm.size() > 0) ? (Math.round(log(getMax() / tm.firstEntry().getValue()) * 1000d) / 10d) : 0.0;
    }

    private double getMinRtn() {
        return (tm.size() > 0) ? (Math.round(log(getMin() / tm.firstEntry().getValue()) * 1000d) / 10d) : 0.0;
    }

    private double getLast() {
        return (tm.size() > 0) ? Math.round(100d * tm.lastEntry().getValue()) / 100d : 0;
    }

    private LocalTime getAMMinT() {
        return tm.entrySet().stream().filter(AMPRED).min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(TIMEMAX);
    }

    private LocalTime getAMMaxT() {
        return tm.entrySet().stream().filter(AMPRED).max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(TIMEMAX);
    }

    private LocalTime getPMMinT() {
        return tm.entrySet().stream().filter(PMPRED).min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(TIMEMAX);
    }

    private LocalTime getPMMaxT() {
        return tm.entrySet().stream().filter(PMPRED).max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(TIMEMAX);
    }

    private LocalTime getMinT() {
        return tm.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(TIMEMAX);
    }

    private LocalTime getMaxT() {
        return tm.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(TIMEMAX);
    }

    static double r(double d) {
        return Math.round(d * 100d) / 100d;
    }

    private int getDayVRPercentile() {
        if (tm.size() > 0) {
            double maxD = tm.entrySet().stream().max(Map.Entry.comparingByValue()).map(e -> e.getValue()).orElse(0.0);
            double minD = tm.entrySet().stream().min(Map.Entry.comparingByValue()).map(e -> e.getValue()).orElse(0.0);
            double lastD = tm.lastEntry().getValue();
            return (int) Math.round(100d * (lastD - minD) / (maxD - minD));
        }
        return 0;
    }

    private int getPMVRPercentile() {
        if (tm.size() > 2 && tm.lastKey().isAfter(PMOPENT)) {
            double pmmax = tm.entrySet().stream().filter(PMPRED).max(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0);
            double pmmin = tm.entrySet().stream().filter(PMPRED).min(Map.Entry.comparingByValue()).map(Map.Entry::getValue).orElse(0.0);
            double lastD = tm.lastEntry().getValue();
            return (int) Math.round(100d * (lastD - pmmin) / (pmmax - pmmin));
        }
        return 0;
    }
}