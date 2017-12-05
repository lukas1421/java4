package TradeType;

import utility.Utility;

public class FutureTrade extends Trade {

    private final double COST_PER_LOT = 1.505;

    public FutureTrade(double p, int s) {
        super(p, s);
    }

    @Override
    public double getTransactionFee(String name) {
        return COST_PER_LOT * Math.abs(size);
    }

    @Override
    public double getCostBasisWithFees(String name) {
        return (-1d * size * price) - COST_PER_LOT * Math.abs(size);
    }

    @Override
    public double getTransactionFeeCustomBrokerage(String name, double rate) {
        return mergeList.stream().mapToDouble(t->((Trade)t).transactionFeeHelper(name,rate)).sum();
    }

    @Override
    public double getCostBasisWithFeesCustomBrokerage(String name, double rate) {
        return mergeList.stream().mapToDouble(t->((Trade)t).costBasisHelper(name,rate)).sum();
    }

    @Override
    public double transactionFeeHelper(String name, double rate) {
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
