package TradeType;

import utility.Utility;

import java.time.LocalDateTime;

public class MAIdea {

    private LocalDateTime tradeTime;
    private double tradePrice;
    private int size;

    public MAIdea(LocalDateTime t, double p, int q) {
        tradeTime = t;
        tradePrice = p;
        size = q;
    }

    public LocalDateTime getIdeaTime() {
        return tradeTime;
    }

    public double getIdeaPrice() {
        return tradePrice;
    }

    public int getIdeaSize() {
        return size;
    }


    @Override
    public String toString() {
        return Utility.getStr(" MAIdea", tradeTime, size > 0 ? "BUY" : "SELL", size
                , " @ ", Math.round(100d * tradePrice) / 100d);
    }
}
