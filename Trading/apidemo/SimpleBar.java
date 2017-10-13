package apidemo;

import static apidemo.ChinaStockHelper.getStr;
import java.io.Serializable;
import java.util.function.BinaryOperator;

public class SimpleBar implements Serializable, Comparable<SimpleBar> {

    static final long serialVersionUID = -34735107L;

    double open;
    double high;
    double low;
    double close;

    private static SimpleBar ZERO_BAR = new SimpleBar(0.0);

    public SimpleBar() {
        open = 0.0;
        high = 0.0;
        low = 0.0;
        close = 0.0;
    }

    public SimpleBar(double o, double h, double l, double c) {
        this.open = o;
        this.high = h;
        this.low = l;
        this.close = c;
    }

    static BinaryOperator<SimpleBar> addSB() {
        return (a, b) -> new SimpleBar(r(a.getOpen() + b.getOpen()), r(a.getHigh() + b.getHigh()),
                r(a.getLow() + b.getLow()), r(a.getClose() + b.getClose()));
    }

    SimpleBar(SimpleBar sb) {
        open = sb.getOpen();
        high = sb.getHigh();
        low = sb.getLow();
        close = sb.getClose();
    }

    static final SimpleBar getZeroBar() {
        return ZERO_BAR;
    }

    SimpleBar(double v) {
        open = v;
        high = v;
        low = v;
        close = v;
    }

    void adjustByFactor(double f) {
        //System.out.println ( ChinaStockHelper.getStr("BEFORE open high low close ",open, high, low, close ));
        open = open * f;
        high = high * f;
        low = low * f;
        close = close * f;
        //System.out.println ( ChinaStockHelper.getStr("AFTER open high low close ",open, high, low, close ));
    }

    void updateOpen(double o) {
        open = o;
    }

    void updateHigh(double h) {
        high = h;
    }

    void updateLow(double l) {
        low = l;
    }

    void updateClose(double c) {
        close = c;
    }

    double getOpen() {
        return open;
    }

    double getHigh() {
        return high;
    }

    double getLow() {
        return low;
    }

    double getClose() {
        return close;
    }

    void add(double last) {
        if (open == 0.0 || high == 0.0 || low == 0.0 || close == 0.0) {
            open = last;
            high = last;
            low = last;
            close = last;
        } else {
            close = last;
            if (last > high) {
                high = last;
            }
            if (last < low) {
                low = last;
            }
        }
    }

    void round() {
        open = Math.round(100d * open) / 100d;
        high = Math.round(100d * high) / 100d;
        low = Math.round(100d * low) / 100d;
        close = Math.round(100d * close) / 100d;
    }

    static double r(double n) {
        return Math.round(n * 100d) / 100d;
    }

    /**
     * if any contains zero
     */
    boolean containsZero() {
        return (open == 0 || high == 0.0 || low == 0.0 || close == 0.0);
    }

    boolean normalBar() {
        return (open != 0 && high != 0 && low != 0.0 && close != 0.0);
    }

    double getHLRange() {
        return (low != 0.0) ? (high / low - 1) : 0.0;
    }

    double getBarReturn() {
        return (open != 0.0) ? (close / open - 1) : 0.0;
    }

    int getOP() {
        return (int) ((open - low) / (high - low) * 100d);
    }

    int getCP() {
        return (int) ((close - low) / (high - low) * 100d);
    }

    @Override
    public String toString() {
        return getStr("open:", open, " high:", high, "low:", low, "close:", close);
    }

    @Override
    public int compareTo(SimpleBar o) {
        return this.high >= o.high ? 1 : -1;
    }
}
