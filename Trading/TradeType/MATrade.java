package TradeType;

import utility.Utility;

import java.time.LocalDateTime;

public class MATrade {

    private LocalDateTime tradeTime;
    private double tradePrice;
    private int size;

    MATrade() {

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

    public MATrade(LocalDateTime t, double p, int q) {
        tradeTime = t;
        tradePrice = p;
        size = q;
    }

    @Override
    public String toString() {
        return Utility.getStr(" time price quantity ", tradeTime, Math.round(tradePrice), size > 0 ? "BUY" : "SELL", size);
    }
}
