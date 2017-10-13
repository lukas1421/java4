package apidemo;

import static apidemo.ChinaStock.TIMEMAX;
import static java.lang.Math.log;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
// this will be a class to analyse data in LiveData

public class Processing implements Runnable {

    ConcurrentMap<Integer, ? extends NavigableMap<LocalTime, Double>> data;
    //LiveData ld;
    //AM
    private double maxAM;
    private LocalTime maxAMT;
    private double minAM;
    private LocalTime minAMT;

    //pm
    private double maxPM;
    private LocalTime maxPMT;
    private double minPM;
    private LocalTime minPMT;

    //Day
    private double maxDay;
    private double minDay;
    private LocalTime maxDayT;
    private LocalTime minDayT;

    private double maxDrawdown;
    private LocalTime processUntil;

    private int activity;
    private int maxRep;

    //percentile
    private double percentile;
    private double percChg1m;
    private double percChg3m;
    private double percChg5m;

    //Ret
    private double rtnOnDay;
    private double rtn1Min;
    private double rtn3Min;
    private double rtn5Min;

    Processing(LiveData ld) {
        this.data = ld.getMap();
    }

    Processing(ConcurrentMap<Integer, ? extends NavigableMap<LocalTime, Double>> data) {

        maxRep = 0;
        activity = 0;
        percentile = 0;
        rtnOnDay = 0;
        this.data = data;
    }

    //getters
    public double getterPercentile() {
        return this.percentile;
    }

    public double getterReturn() {
        return this.rtnOnDay;
    }

    public double getterMaxDay() {
        return this.maxDay;
    }

    public int getterActivity() {
        return this.activity;
    }

    public int getterMaxRep() {
        return maxRep;
    }

    // loop through map once, get Max, Min, maxT, minT, 
    public void getAll(int symbol) {

        //data.get(symbol).
        maxDay = Collections.max(data.get(symbol).values());
        minDay = Collections.min(data.get(symbol).values());

        //Day Max and Min
        maxDayT = data.get(symbol).entrySet().stream().max(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
        maxDay = data.get(symbol).entrySet().stream().max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
        minDayT = data.get(symbol).entrySet().stream().min(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
        minDay = data.get(symbol).entrySet().stream().min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);

        //AM Max and Min
        maxAMT = data.get(symbol).entrySet().stream().filter(p -> p.getKey().isBefore(LocalTime.of(12, 0))).max(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
        maxAM = data.get(symbol).entrySet().stream().filter(p -> p.getKey().isBefore(LocalTime.of(12, 0))).max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
        minAMT = data.get(symbol).entrySet().stream().filter(p -> p.getKey().isBefore(LocalTime.of(12, 0))).min(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
        minAM = data.get(symbol).entrySet().stream().filter(p -> p.getKey().isBefore(LocalTime.of(12, 0))).min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);

        //PM Max and Min
        maxPMT = data.get(symbol).entrySet().stream().filter(p -> p.getKey().isBefore(LocalTime.of(12, 0))).max(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
        maxPM = data.get(symbol).entrySet().stream().filter(p -> p.getKey().isBefore(LocalTime.of(12, 0))).max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
        minPMT = data.get(symbol).entrySet().stream().filter(p -> p.getKey().isAfter(LocalTime.of(12, 0))).min(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
        minPM = data.get(symbol).entrySet().stream().filter(p -> p.getKey().isAfter(LocalTime.of(12, 0))).min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);

        processUntil = data.get(symbol).lastKey();

        percentile = ((data.get(symbol).lastEntry().getValue()) - minDay) / (maxDay - minDay);

        rtnOnDay = log(data.get(symbol).lastEntry().getValue() / data.get(symbol).firstEntry().getValue());
        //return 0.0;
    }

    public double getMaxDay(Map<LocalTime, Double> tm) {
        return tm.entrySet().stream().max(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
    }

    public double getMinDay(Map<LocalTime, Double> tm) {
        return tm.entrySet().stream().min(Entry.comparingByValue()).map(Entry::getValue).orElse(0.0);
    }

    public LocalTime getMaxDayT(Map<LocalTime, Double> tm) {
        return tm.entrySet().stream().max(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
    }

    public LocalTime getMinDayT(Map<LocalTime, Double> tm) {
        return tm.entrySet().stream().min(Entry.comparingByValue()).map(Entry::getKey).orElse(TIMEMAX);
    }

    public void run() {
    }

    public void getMinAM(TreeMap<LocalTime, Double> tm) {
    }

    public int getActivity(TreeMap<LocalTime, Double> tm) {
        Iterator it = tm.keySet().iterator();
        LocalTime lt;
        double last = 0;
        double current;
        int m_activity = 0;

        while (it.hasNext()) {
            lt = (LocalTime) it.next();
            current = tm.get(lt);
            //last = data.get(symbol).get(lt);
            if (current == last) {
                m_activity++;
            }
            last = current;
        }
        //activity = m_activity;
        return m_activity;
    }

    public int getMaxRep(TreeMap<LocalTime, Double> tm) {
        HashMap<Double, Integer> mp = new HashMap<>();
        Iterator it = tm.keySet().iterator();
        LocalTime lt;
        double last = 0;
        double current;
        int m_activity = 0;
        int count;

        while (it.hasNext()) {
            lt = (LocalTime) it.next();
            current = tm.get(lt);
            //last = data.get(symbol).get(lt);
            if (mp.containsKey(current)) {
                count = mp.get(current);
                mp.put(current, count + 1);
            } else {
                mp.put(current, 1);
            }
        }

        //maxRep = mp.entrySet().stream().max((entry1,entry2)-> entry1.getValue()> entry2.getValue() ? 1:-1).map(Entry::getValue).orElse(0.0);
        return mp.entrySet().stream().max((entry1, entry2) -> entry1.getValue() > entry2.getValue() ? 1 : -1).map(Entry::getValue).orElse(0);
    }

    public void rtn1Min(int symbol) {
        LocalTime lt = data.get(symbol).lastKey();

        try {
            rtn1Min = log(data.get(symbol).lastEntry().getValue() / data.get(symbol).lowerEntry(lt).getValue());
            rtn3Min = log(data.get(symbol).lastEntry().getValue() / data.get(symbol).lowerEntry(lt.minusMinutes(2)).getValue());
            rtn5Min = log(data.get(symbol).lastEntry().getValue() / data.get(symbol).lowerEntry(lt.minusMinutes(4)).getValue());
        } catch (NullPointerException e) {

        }
    }

    public double getPercentile(TreeMap<LocalTime, Double> tm) {

        return (tm.lastEntry().getValue() - minDay) / (maxDay - minDay);
    }

    public void percentileCompute(int symbol) {
        LocalTime lt = data.get(symbol).lastKey();
        percChg1m = (data.get(symbol).lastEntry().getValue() - data.get(symbol).lowerEntry(lt).getValue()) / (maxDay - minDay);
        percChg3m = (data.get(symbol).lastEntry().getValue() - data.get(symbol).lowerEntry(lt.minusMinutes(2)).getValue()) / (maxDay - minDay);
        percChg5m = (data.get(symbol).lastEntry().getValue() - data.get(symbol).lowerEntry(lt.minusMinutes(4)).getValue()) / (maxDay - minDay);

    }

    public double getRtnOnDay(TreeMap<LocalTime, Double> tm) {

        return log(tm.lastEntry().getValue() / tm.firstEntry().getValue());
    }

    public void drawDownCompute(int symbol) {

    }

    public Trait computeAll(TreeMap<LocalTime, Double> tm) {
        Trait t = new Trait();
        //last price
        LocalTime lt = tm.lastKey();
        t.lastPrice(tm.lowerEntry(lt).getValue());
        //maxDay
        t.maxDay(getMaxDay(tm));
        //minDay
        t.maxDay(getMinDay(tm));
        //maxDayT
        t.maxDayT(getMaxDayT(tm));
        //minDayT
        t.minDayT(getMinDayT(tm));
        //maxDrawDown

        //processUntil
        //activty
        t.activity(getActivity(tm));
        //maxRep
        t.maxRep(getActivity(tm));
        //percentile
        //t.percentile(getPercentile(tm));
        //rtnOnDay
        t.rtnOnDay(getRtnOnDay(tm));

        return t;
    }

}
