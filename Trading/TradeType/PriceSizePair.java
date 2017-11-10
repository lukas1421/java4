package TradeType;

public class PriceSizePair {

    private double price;
    private int size;

    public PriceSizePair(double p, int s) {
        price = p;
        size = s;
    }

    public PriceSizePair() {
        price = 0.0;
        size = 0;
    }

    public double getPrice() {
        return price;
    }

    public int getSize() {
        return size;
    }
}
