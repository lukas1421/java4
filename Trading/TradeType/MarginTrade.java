package TradeType;

import utility.Utility;

import static java.lang.Math.abs;

public class MarginTrade extends Trade {

    public MarginTrade(double p, int s) {
        super(p, s);
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
    public double getCostWithCommission(String name) {
        double brokerage = Math.max(5, Math.round(price * abs(size) * 3 / 100) / 100d);
        double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
        double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);

        return (-1d * size * price) - brokerage - guohu - stamp;
    }




    @Override
    public double getTradingCostCustomBrokerage(String name, double rate) {
        return mergeList.stream().mapToDouble(t->((Trade)t).tradingCostHelper(name,rate)).sum();
    }

    @Override
    public double getCostWithCommissionCustomBrokerage(String name, double rate) {
        return mergeList.stream().mapToDouble(t->((Trade)t).costBasisHelper(name,rate)).sum();
    }

    @Override
    public double tradingCostHelper(String name, double rate) {
        double brokerage = Math.max(5, Math.round(price * abs(size) * 3 / 100) / 100d);
        double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
        double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
        //System.out.println( " name price size " + price + " " + size );
        //System.out.println(" name brokerage guohu stamp " + name + " " +  brokerage  + " " +  guohu   + " " + stamp);
        return brokerage + guohu + stamp;
    }

    @Override
    public double costBasisHelper(String name, double rate) {
        double brokerage = Math.max(5, Math.round(price * abs(size) * 3 / 100) / 100d);
        double guohu = (name.equals("sh510050")) ? 0 : ((name.startsWith("sz")) ? 0.0 : Math.round(price * abs(size) * 0.2 / 100d) / 100d);
        double stamp = (name.equals("sh510050")) ? 0 : ((size < 0 ? 1 : 0) * Math.round((price * abs(size)) * 0.1) / 100d);
        return (-1d * size * price) - brokerage - guohu - stamp;
    }

    @Override
    public String toString() {
        return Utility.getStr(" margin trade ", " price ", price, "vol ", size);
    }

}
