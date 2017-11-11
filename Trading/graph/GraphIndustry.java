package graph;

import apidemo.ChinaStock;
import apidemo.ChinaStockHelper;
import auxiliary.SimpleBar;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static apidemo.ChinaData.*;
import static java.lang.Math.round;
import static java.util.Comparator.comparingDouble;
import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.*;
import static utility.Utility.*;

public class GraphIndustry extends JComponent {

    private static final int X_WIDTH = 4;
    private int height;
    private double min;
    private double max;
    private static volatile ConcurrentMap<String, ConcurrentSkipListMap<LocalTime, Double>> industryPriceMap = new ConcurrentHashMap<>();
    public static volatile Map<String, Double> sectorMapInOrder = new LinkedHashMap<>();
    public static Map<String, ConcurrentSkipListMap<LocalTime, SimpleBar>> industryMapBar = new ConcurrentHashMap<>();

    public static volatile List<String> sectorNamesInOrder = new LinkedList<>();
    public static volatile String quickestRiser;
    //private static volatile String topStockInRiser;
    public static volatile String selectedNameIndus = "";

    public static final Predicate<? super Entry<String, ?>> NO_GC = e -> !e.getKey().equals("sh204001") && e.getKey().length() > 2;
    public static final Predicate<? super Entry<LocalTime, ?>> TRADING_HOURS =
            e -> ((e.getKey().isAfter(LocalTime.of(9, 29)) && e.getKey().isBefore(LocalTime.of(11, 31))) || Utility.PM_PRED.test(e));

    private final static Comparator<? super Entry<String, ? extends NavigableMap<LocalTime, Double>>> LAST_ENTRY_COMPARATOR
            = Comparator.comparingDouble(e -> Optional.ofNullable(e.getValue().lastEntry()).map(Entry::getValue).orElse(0.0));

    //final static Comparator<? super Entry<String, ? extends NavigableMap<LocalTime, Double>>> REVERSED = LAST_ENTRY_COMPARATOR.reversed();

    private static final BasicStroke BS4 = new BasicStroke(4);
    private static final BasicStroke BS0 = new BasicStroke();

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.black);
        g.setColor(Color.black);
        height = (int) (getHeight() * 0.85);
        min = getMin();
        max = getMax();

        int last;
        int x;
        String maxNumTimeIndus = industryPriceMap.entrySet().stream().max(Comparator.comparingInt(e->e.getValue().size())).map(Entry::getKey).orElse("");
        //(e1, e2) -> e1.getValue().size() > e2.getValue().size() ? 1 : -1

        for (String key : industryPriceMap.keySet()) {

            if (industryPriceMap.get(key).size() > 0) {

//                System.out.println(" industry key is " + key);
//                System.out.println(" industry size is " + industryPriceMap.get(key).size());
//                System.out.println(" industry first entry is " + industryPriceMap.get(key).firstEntry());
//                System.out.println(" industry last entry is " + industryPriceMap.get(key).lastEntry());
//                double thisMax = Math.round(10d * industryPriceMap.get(key).entrySet().stream()
//                        .max(Map.Entry.comparingByValue()).map(Entry::getValue).orElse(0.0)) / 10d;

                double thisLast = Math.round(10d * industryPriceMap.get(key).lastEntry().getValue()) / 10d;
                //int maxY = getY(thisMax);

                g2.setColor(new Color((g2.getColor().getRed() + (int) (Math.random() * (255))) % 255,
                        (g2.getColor().getGreen() + (int) (Math.random() * (255))) % 150,
                        (g2.getColor().getBlue() + (int) (Math.random() * (255))) % 255));

                if (selectedNameIndus.equals(key)) {
                    g2.setColor(Color.red);
                }
                if (quickestRiser != null && quickestRiser.equals(key)) {
                    g2.setColor(Color.blue);
                }

                last = 0;
                x = 25;

                for (LocalTime t : industryPriceMap.get(key).keySet()) {
                    int close = getY(industryPriceMap.get(key).floorEntry(t).getValue());
                    last = (last == 0) ? close : last;

                    g2.setFont(g.getFont().deriveFont(30F));
                    g2.setStroke(((selectedNameIndus != null && selectedNameIndus.equals(key))
                            || (quickestRiser != null && quickestRiser.equals(key))) ? BS4 : BS0);

                    g2.drawLine(x, last, x + X_WIDTH, close);
                    last = close;

                    if (key.equals(maxNumTimeIndus)) {
                        if (t.equals(industryPriceMap.get(key).firstKey())) {
                            g2.drawString(t.truncatedTo(ChronoUnit.MINUTES).toString(), x - 30, getHeight() - 40);
                        } else {
                            if (t.getMinute() == 0 || (t.getHour() != 9 && t.getHour() != 11 && t.getMinute() == 30)) {
                                g.drawString(t.truncatedTo(ChronoUnit.MINUTES).toString(), x - 30, getHeight() - 40);
                            }
                        }
                    }

                    if (t.equals(industryPriceMap.get(key).lastKey())) {
                        g2.setFont(g.getFont().deriveFont((selectedNameIndus.equals(key) || quickestRiser.equals(key)) ? Font.BOLD : Font.PLAIN,
                                (selectedNameIndus.equals(key) || quickestRiser.equals(key)) ? 54F : 18F));
                        g.drawString(key, x + 30 + (int) (Math.random() * (Math.min(400, getWidth() - x - 200))), last);
                        g.drawString(getStr(thisLast), getWidth() - 70 - (int) (50 * Math.random()), last);
                    }
                    x += X_WIDTH;
                }
            }
        }

        g2.setColor(Color.black);
        g2.setFont(g.getFont().deriveFont(36F));
        g2.setStroke(BS4);
        g2.drawString(LocalTime.now().toString(), 40, 40);
    }

    private static NavigableMap<LocalTime, Double> getReturnMap(NavigableMap<LocalTime, SimpleBar> mp) {
        NavigableMap<LocalTime, Double> res = new ConcurrentSkipListMap<>();
        if (mp.entrySet().stream().filter(e -> e.getValue().normalBar()).count() > 0) {
            mp.keySet().forEach((t) -> {
                if (t.isBefore(AMOPENT)) {
                    res.put(t, 0.0);
                } else if (TIME_BETWEEN.between(AMOPENT, true, AMCLOSET, true).test(t) || (t.isAfter(LocalTime.of(12, 59)))) {
                    if (mp.ceilingEntry(AMOPENT).getValue().getOpen() != 0.0) {
                        res.put(t, 100d * (mp.floorEntry(t).getValue().getClose() / mp.ceilingEntry(AMOPENT).getValue().getOpen() - 1));
                    } else {
                        //System.out.println( " am open t is 0 ");
                        //System.out.println(" wrong entry: first entry " + mp.ceilingEntry(t).getValue());
                        res.put(t, 0.0);
                    }
                }
            });
        }
        return res;
    }

    double getMax() {
        //System.out.println ( " industry max is " + max);

        //System.out.println( " max temp is " + maxTemp);
        return (industryPriceMap.size() > 0)
                ? industryPriceMap.entrySet().stream().filter(e -> e.getValue().size() > 0)
                        .flatMap(e -> e.getValue().entrySet().stream())
                        .max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0) : 0.0;
    }

    double getMin() {
        //System.out.println ( " industry in is " + min);
        return (industryPriceMap.size() > 0)
                ? industryPriceMap.entrySet().stream().filter(e -> e.getValue().size() > 0)
                .flatMap(e -> e.getValue().entrySet().stream()).min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0) : 0.0;
    }

    private int getY(double v) {
        //System.out.println ( " industry max is " + max);
        //System.out.println ( " industry in is " + min);

        double span = max - min;
        double pct = (v - min) / span;
        double val = pct * height;
        return height - (int) val + 20;
    }

    public static void compute() {

        CompletableFuture.runAsync(GraphIndustry::getIndustryPrice);
        CompletableFuture.runAsync(GraphIndustry::getIndustryVol);
//        System.out.println(" short names are "+priceMapBar.entrySet().stream().filter(NO_GC).collect(groupingByConcurrent(
//                e->ChinaStock.shortIndustryMap.get(e.getKey()),ConcurrentHashMap::new,Collectors.counting())));

        CompletableFuture.supplyAsync(()
                -> priceMapBar.entrySet().stream().filter(NO_GC).collect(groupingByConcurrent(
                        e -> ChinaStock.shortIndustryMap.get(e.getKey()), ConcurrentHashMap::new,
                        mapping(e -> getReturnMap(e.getValue()), collectingAndThen(toList(),
                                e -> e.stream().flatMap(e1 -> e1.entrySet().stream().filter(TRADING_HOURS))
                                        .collect(groupingByConcurrent(Entry::getKey,
                                                ConcurrentSkipListMap::new, averagingDouble(Entry::getValue))))))))
                .thenAccept(
                        m -> {
                            industryPriceMap = m;

                            CompletableFuture.supplyAsync(()
                                    -> m.entrySet().stream().max(comparingByValue(comparingDouble(GraphIndustry::getMapLastReturn)))
                                    .map(Entry::getKey).orElse(""))
                                    .thenAccept(qr -> {
                                        quickestRiser = qr;
                                        //topStockInRiser = getTopStockForRiser(qr);
                                    });

                            CompletableFuture.supplyAsync(()
                                    -> m.entrySet().stream().sorted(LAST_ENTRY_COMPARATOR.reversed())
                                    .collect(Collectors.toMap(Entry::getKey,
                                            e -> Optional.ofNullable(e.getValue().lastEntry()).map(Entry::getValue).orElse(0.0),
                                            (a, b) -> a, LinkedHashMap::new)))
                                    .thenAcceptAsync(sm -> {
                                        sectorMapInOrder = sm;
                                        sectorNamesInOrder = new LinkedList<>(sm.keySet());
                                    });
                        });

//        sectorMapInOrder = industryPriceMap.entrySet().stream().sorted(REVERSED)
//                .collect(Collectors.toMap(Entry::getKey, e->Optional.ofNullable(e.getValue().lastEntry()).map(Entry::getValue).orElse(0.0),(a,b)->a, LinkedHashMap::new));
//        
//        sectorNamesInOrder = sectorMapInOrder.keySet().stream().collect(toCollection(LinkedList::new));
//        
//        quickestRiser = industryPriceMap.entrySet().stream().max(comparingByValue(comparingDouble(GraphIndustry::getMapLastReturn))).map(Entry::getKey).orElse("");
//        topStockInRiser = getTopStockForRiser(quickestRiser);
    }

    private static String getTopStockForRiser(String riser) {
        return priceMapBar.entrySet().stream().filter(NO_GC).filter(e -> ChinaStock.shortIndustryMap.get(e.getKey()).equals(riser))
                .max(Comparator.comparingDouble(e -> Optional.ofNullable(e.getValue().lastEntry())
                        .map(Entry::getValue).map(SimpleBar::getBarReturn).orElse(0.0)))
                .map(Entry::getKey).orElse("");
    }

    private static double getMapLastReturn(NavigableMap<LocalTime, Double> mp) {
        if (mp.size() > 1) {
            double last = mp.lastEntry().getValue();
            double secondLast = Optional.ofNullable(mp.lowerEntry(mp.lastKey())).map(Entry::getValue).orElse(Double.MAX_VALUE);
            return (last - secondLast);
        }
        return 0.0;
    }

    public static void getIndustryPrice() {
        CompletableFuture.supplyAsync(()
                -> priceMapBar.entrySet().stream().filter(NO_GC)
                        .collect(groupingByConcurrent(e -> ChinaStock.industryNameMap.get(e.getKey()),
                                mapping(Entry::getValue, collectingAndThen(toList(), e -> e.stream().flatMap(e1 -> e1.entrySet().stream().filter(TRADING_HOURS))
                                .collect(groupingByConcurrent(Entry::getKey, ConcurrentSkipListMap::new, mapping(Entry::getValue,
                                        collectingAndThen(reducing(SimpleBar.addSB()), e1 -> e1.orElseGet(SimpleBar::new))))))))))
                .thenAccept(m -> {
                    industryMapBar = m;
                    CompletableFuture.runAsync(GraphIndustry::processIndustry);
                    industryMapBar.keySet().forEach(s -> {
                        if (industryMapBar.get(s).size() > 0) {

                            CompletableFuture.runAsync(() -> priceMapBar.put(s, m.get(s)));
                            CompletableFuture.runAsync(() ->
                                    ChinaStock.openMap.put(s, Optional.ofNullable(industryMapBar.get(s).floorEntry(AMOPENT)).map(Entry::getValue).map(SimpleBar::getOpen).orElse(getIndustryOpen(s))));

                            CompletableFuture.runAsync(() -> ChinaStock.maxMap.put(s, industryMapBar.get(s).entrySet().stream()
                                    .filter(IS_OPEN_PRED).max(BAR_HIGH).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0)));
                            CompletableFuture.runAsync(() -> ChinaStock.minMap.put(s, industryMapBar.get(s).entrySet().stream()
                                    .filter(IS_OPEN_PRED).min(BAR_LOW).map(Entry::getValue).map(SimpleBar::getLow).orElse(0.0)));
                            CompletableFuture.runAsync(() -> ChinaStock.priceMap.put(s, industryMapBar.get(s).lastEntry().getValue().getClose()));
                            CompletableFuture.runAsync(() -> ChinaStock.closeMap.put(s, Optional.ofNullable(priceMapBarYtd.get(s))
                                    .map(ConcurrentSkipListMap::lastEntry).map(Entry::getValue).map(SimpleBar::getClose)
                                    .orElse(Optional.ofNullable(industryMapBar.get(s)).map(ConcurrentSkipListMap::firstEntry)
                                            .map(Entry::getValue).map(SimpleBar::getOpen).orElse(0.0))));
                        }
                    });
                });
    }

    private static void processIndustry() {

        industryMapBar.forEach((indusName, value) -> {

            SimpleBar lastBar = value.lastEntry().getValue();
            double lastHigh = lastBar.getHigh();
            //double lastClose = lastBar.getClose();
            LocalTime lastKey = value.lastKey();
            //double lastSize = sizeTotalMap.get(indusName).lastEntry().getValue();

            double prevHigh = value.headMap(lastKey, false).entrySet().stream()
                    .max(comparingByValue(comparingDouble(SimpleBar::getHigh))).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);
//            double prevLow = value.headMap(lastKey, false).entrySet().stream()
//                    .min(comparingByValue(comparingDouble(SimpleBar::getLow))).map(Entry::getValue).map(SimpleBar::getHigh).orElse(0.0);

            //LocalTime highT = value.entrySet().stream().max(comparingByValue(comparingDouble(SimpleBar::getHigh))).map(Entry::getKey).orElse(TIMEMAX);
            //LocalTime lowT = value.entrySet().stream().min(comparingByValue(comparingDouble(SimpleBar::getLow))).map(Entry::getKey).orElse(TIMEMAX);

            String topRiser = getTopStockForRiser(indusName);

            String msg = getStr(" break high ", indusName, lastKey, "top riser:", topRiser, ChinaStock.nameMap.get(topRiser));

            if (lastHigh > prevHigh) {
                ChinaStockHelper.createDialogJD(indusName, msg, lastKey);
            }
        });
    }

    public static double getIndustryOpen(String sector) {
        try {
            return ChinaStock.openMap.entrySet().stream().filter(NO_GC)
                    .filter(e -> ChinaStock.industryNameMap.get(e.getKey()).equals(sector)).mapToDouble(Entry::getValue).sum();
        } catch (Exception x) {
            System.out.println(" sector wrong " + sector);
            x.printStackTrace();
            return 0.0;
        }
    }

    private static void getIndustryVol() {
        CompletableFuture.supplyAsync(()
                -> sizeTotalMap.entrySet().stream().filter(NO_GC)
                        .collect(groupingByConcurrent(e -> ChinaStock.industryNameMap.get(e.getKey()),
                                mapping(Entry::getValue, Collectors.collectingAndThen(toList(),
                                        e -> e.stream().flatMap(e1 -> e1.entrySet().stream().filter(TRADING_HOURS))
                                .collect(groupingBy(Entry::getKey, ConcurrentSkipListMap::new, summingDouble(Entry::getValue))))))))
                .thenAccept(
                        mp -> mp.keySet().forEach(s -> {
                            if (mp.get(s).size() > 0) {
                                sizeTotalMap.put(s, mp.get(s));
                                ChinaStock.sizeMap.put(s, round(mp.get(s).lastEntry().getValue()));
                            }
                        }));
    }

    @SuppressWarnings("unchecked")
    public static <T extends NavigableMap<LocalTime, SimpleBar>> void getIndustryPriceYtd(Map<String, T> mp) {
        CompletableFuture.supplyAsync(()
                -> mp.entrySet().stream().filter(NO_GC).collect(groupingBy(e -> ChinaStock.industryNameMap.get(e.getKey()), HashMap::new,
                        mapping(Entry::getValue, collectingAndThen(toList(), e -> e.stream().flatMap(e1 -> e1.entrySet().stream().filter(TRADING_HOURS))
                        .collect(groupingBy(Entry::getKey, ConcurrentSkipListMap::new, mapping(Entry::getValue,
                                collectingAndThen(reducing(SimpleBar.addSB()), e1 -> e1.orElseGet(SimpleBar::new))))))))))
                .thenAccept(m -> m.keySet().forEach(s -> {
            //System.out.println(" indus " + s);
            //System.out.println(" price map " + m.get(s));
            mp.put(s, (T) m.get(s));

        }));
    }

}
