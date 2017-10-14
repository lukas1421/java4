package TradeType;

import apidemo.ChinaStock;
import utility.Utility;

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
        return Utility.getStr("price ", price, "vol ", size);
    }
}

