package apidemo;

import static java.lang.Math.abs;

public abstract class Trade {

    protected double price;
    protected int size;

    public Trade(double p, int s) {
        price = p;
        size = s;
    }

    public int getSize() {
        return size;
    }

    public void merge(Trade t) {
        int sizeNew = size + t.getSize();
        price = (getCost() + t.getCost()) / sizeNew;
        size = sizeNew;
        //System.out.println(" merged results " + "size price " + size + " " + price);
    }

    public double getPrice() {
        return price;
    }

    public double getCost() {
        return size * price;
    }

    public abstract double getTradingCost(String name);

    public abstract double getCostWithCommission(String name);

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
        return ChinaStockHelper.getStr("price ", price, "vol ", size);
    }
}

class NormalTrade extends Trade {

    public NormalTrade(double p, int s) {
        super(p, s);
    }

    @Override
    public double getCostWithCommission(String name) {
        double brokerage = Math.max(5, Math.round(price * abs(size) * 2 / 100) / 100d);
        double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
        double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);

        return (-1d * size * price) - brokerage - guohu - stamp;
    }

    @Override
    public double getTradingCost(String name) {
        double brokerage = Math.max(5, Math.round(price * abs(size) * 2 / 100) / 100d);
        double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
        double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
        return brokerage + guohu + stamp;
    }

    @Override
    public String toString() {
        return ChinaStockHelper.getStr(" normal trade ", " price ", price, "vol ", size);
    }

}

class MarginTrade extends Trade {

    public MarginTrade(double p, int s) {
        super(p, s);
    }

    @Override
    public double getCostWithCommission(String name) {
        double brokerage = Math.max(5, Math.round(price * abs(size) * 3 / 100) / 100d);
        double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
        double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);

        return (-1d * size * price) - brokerage - guohu - stamp;
    }

    @Override
    public double getTradingCost(String name) {
        double brokerage = Math.max(5, Math.round(price * abs(size) * 3 / 100) / 100d);
        double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
        double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
        //System.out.println( " name price size " + price + " " + size );
        //System.out.println(" name brokerage guohu stamp " + name + " " +  brokerage  + " " +  guohu   + " " + stamp);
        return brokerage + guohu + stamp;
    }

    @Override
    public String toString() {
        return ChinaStockHelper.getStr(" margin trade ", " price ", price, "vol ", size);
    }

}

class FutureTrade extends Trade {

    public final double COST_PER_LOT = 1.505;

    public FutureTrade(double p, int s) {
        super(p, s);
    }

    @Override
    public double getTradingCost(String name) {
        return COST_PER_LOT * Math.abs(size);
    }

    @Override
    public double getCostWithCommission(String name) {
        return (-1d * size * price) - COST_PER_LOT * Math.abs(size);
    }

    @Override
    public String toString() {
        return ChinaStockHelper.getStr(" future trade ", " price ", price, "vol ", size);
    }

}
