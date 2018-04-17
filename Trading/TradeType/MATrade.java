package TradeType;

import utility.Utility;

import java.time.LocalDateTime;

public class MATrade {

    private LocalDateTime tradeTime;
    private double tradePrice;
    private int size;

    public MATrade(LocalDateTime t, double p, int q) {
        tradeTime = t;
        tradePrice = p;
        size = q;
    }

    public LocalDateTime getTradeTime() {
        return tradeTime;
    }

    public double getTradePrice() {
        return tradePrice;
    }

    public int getSize() {
        return size;
    }



    @Override
    public String toString() {
        return Utility.getStr(" MATrade", tradeTime, size > 0 ? "BUY" : "SELL", size
                , " @ ", Math.round(100d * tradePrice) / 100d);
    }
}
