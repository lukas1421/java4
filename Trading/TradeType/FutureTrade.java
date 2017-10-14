package TradeType;

import apidemo.ChinaStockHelper;

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
    public String toString() {
        return ChinaStockHelper.getStr(" future trade ", " price ", price, "vol ", size);
    }

}
