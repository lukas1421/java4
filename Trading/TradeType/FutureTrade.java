package TradeType;

import utility.Utility;

public class FutureTrade extends Trade {

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
    public double getTradingCostCustomBrokerage(String name, double rate) {
        return mergeList.stream().mapToDouble(t->((Trade)t).tradingCostHelper(name,rate)).sum();
    }

    @Override
    public double getCostWithCommissionCustomBrokerage(String name, double rate) {
        return mergeList.stream().mapToDouble(t->((Trade)t).costBasisHelper(name,rate)).sum();
    }

    @Override
    public double tradingCostHelper(String name, double rate) {
        return COST_PER_LOT * Math.abs(size);
    }

    @Override
    public double costBasisHelper(String name, double rate) {
        return (-1d * size * price) - COST_PER_LOT * Math.abs(size);
    }

    @Override
    public String toString() {
        return Utility.getStr(" future trade ", " price ", price, "vol ", size);
    }

}
