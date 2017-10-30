package TradeType;

import utility.Utility;

import static java.lang.Math.abs;

public class NormalTrade extends Trade {

    public NormalTrade(double p, int s) {
        super(p, s);
    }

    @Override
    public double getTradingCost(String name) {
        if(price!=0) {
            double brokerage = Math.max(5, Math.round(price * abs(size) * 2 / 100) / 100d);
            double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
            double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
            return brokerage + guohu + stamp;
        } else {
            return 0.0;
        }
    }

    @Override
    public double getCostWithCommission(String name) {
        if(price!=0) { //for dividends condition
            double brokerage = Math.max(5, Math.round(price * abs(size) * 2 / 100) / 100d);
            double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
            double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);

            return (-1d * size * price) - brokerage - guohu - stamp;
        } else {
            return 0.0;
        }
    }

    @Override
    public double getTradingCostCustomBrokerage(String name, double rate) {
        return mergeList.stream().mapToDouble(t->((Trade)t).tradingCostHelper(name,rate)).sum();
    }

    @Override
    public double getCostWithCommissionCustomBrokerage(String name, double rate) {
        return mergeList.stream().mapToDouble(t->((Trade)t).costBasisHelper(name, rate)).sum();
    }


    @Override
    public double costBasisHelper(String name, double rate) {
        if(price != 0.0) {
            double brokerage = Math.max(5, Math.round(price * abs(size) * rate / 100) / 100d);
            double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
            double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
            return (-1d * size * price) - brokerage - guohu - stamp;
        } else {
            return 0.0;
        }

    }

    @Override
    public double tradingCostHelper(String name, double rate) {
        if(price != 0.0) {
            double brokerage = Math.max(5, Math.round(price * abs(size) * rate / 100) / 100d);
            double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
            double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
            return brokerage + guohu + stamp;
        } else {
            return 0.0;
        }
    }


    @Override
    public String toString() {
        return Utility.getStr(" normal trade ", " price ", price, "vol ", size);
    }

}
