package apidemo;

public class IBTrade {

    double price;
    int size;

    public IBTrade(double p, int s) {
        price = p;
        size = s;
    }

    public double getPrice() {
        return price;
    }

    public int getSize() {
        return size;
    }

    void merge(IBTrade t) {
        int sizeNew = size + t.getSize();
        price = (getCost() + t.getCost()) / sizeNew;
        size = sizeNew;
        System.out.println(" merged results " + "size price " + size + " " + price);
    }

    public double getCost() {
        return size * price;
    }

    @Override
    public String toString() {
        return " price " + price + " volumn " + size;
    }
}
