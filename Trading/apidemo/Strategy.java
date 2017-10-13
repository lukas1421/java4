package apidemo;

import static apidemo.ChinaStockHelper.getStr;
import java.io.Serializable;
import java.time.LocalTime;

public class Strategy implements Serializable {

    private final LocalTime entranceTime;
    private final double entrancePrice;
    private final StratType strattype;
    private final double lastPrice;

    Strategy() {
        entranceTime = LocalTime.MIN;
        entrancePrice = 0.0;
        strattype = StratType.GEN;
        lastPrice = 0.0;
    }

    Strategy(LocalTime e, double p, StratType st) {
        entranceTime = e;
        entrancePrice = p;
        strattype = st;
        lastPrice = 0.0;
    }

    Strategy(Strategy s) {
        this.entranceTime = s.entranceTime;
        this.entrancePrice = s.entrancePrice;
        this.strattype = s.strattype;
        this.lastPrice = s.lastPrice;
    }

    public LocalTime getEntranceTime() {
        return this.entranceTime;
    }

    public double getEntrancePrice() {
        return this.entrancePrice;
    }

    public double getReturn() {
        return Math.log(lastPrice / entrancePrice);
    }

    StratType getStrat() {
        return strattype;
    }

    enum StratType {
        AMRETURN, AMRANGE, MA, VOL, OVERSOLD, OVERSOLD2, BIGDROP, PRICEBURST, GEN, SIZEEXPLODE, VRMAX, MAX, TMR;
    }

    @Override
    public String toString() {
        return getStr(entranceTime, entrancePrice, strattype);
    }

}
