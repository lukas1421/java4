package TradeType;

import apidemo.ChinaStock;
import utility.Utility;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.abs;

public abstract class Trade {

    protected double price;
    protected int size;
    boolean mergeStatus =  false;
    //Map<Integer, PriceSizePair>  tradeTracker = new HashMap<>();
    AtomicInteger tradeCount = new AtomicInteger(0);
    List<? super Trade> mergeList = new LinkedList<>();



    public Trade(Trade t) {
        price = t.price;
        size = t.size;
    }

    public Trade(double p, int s) {
        mergeList.add(this);
        //tradeTracker.put(tradeCount.incrementAndGet(), new PriceSizePair(p,s));
        price = p;
        size = s;
    }

    public int getSize() {
        return size;
    }

    public int getSizeAll() {
        return mergeList.stream().mapToInt(t->((Trade)t).getSize()).sum();
    }

    public boolean getMergeStatus() {
        return mergeStatus;
    }

    public List<? super Trade> getMergeList() {
        return mergeList;
    }

    public void merge2(Trade t) {
        mergeList.add(t);
        mergeStatus = true;
    }

    public void merge(Trade t) {
        //tradeTracker.put(tradeCount.incrementAndGet(),new PriceSizePair(t.getPrice(),t.getSize()));
        //mergeList.get(0)
        mergeList.add(t);
        mergeStatus = true;
        int sizeNew = size + t.getSize();
        price = (getCost() + t.getCost()) / sizeNew;
        size = sizeNew;
        //System.out.println(" merged results " + "size price " + size + " " + price);
    }

    public double getPrice() {
        return price;
    }

//    public Trade deepCopy(Trade t) {
//        //return this(t.price,t.size);
//    }
//
//    static List<? super Trade> makeImmutable(List<? super Trade> l) {
//        List<? super Trade> res = new LinkedList<>();
//        l.stream().forEach(t->((Trade)t).);
//    }

    public double getCost() {
        return size * price;
    }

    public abstract double getTradingCost(String name);

    public abstract double getCostWithCommission(String name);

    public abstract double getTradingCostCustomBrokerage(String name, double rate);

    public abstract double getCostWithCommissionCustomBrokerage(String name, double rate);

    public abstract double tradingCostHelper(String name, double rate);

    public abstract double costBasisHelper(String name, double rate);

    public double getMtmPnl(String name) {
        if (ChinaStock.priceMap.containsKey(name)) {
            double brokerage = Math.max(5, Math.round(price * abs(size) * 2 / 100) / 100d);
            double guohu = (name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d;
            double stamp = (size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d;
            return (-1d * size * price) - brokerage - guohu - stamp + (size * ChinaStock.priceMap.getOrDefault(name, 0.0));
        } else {
            return 0.0;
        }
    }

    @Override
    public String toString() {
        return Utility.getStr("price ", price, "vol ", size);
    }
}

