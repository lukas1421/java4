package apidemo;

import static apidemo.ChinaStockHelper.getStr;
import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;



@javax.persistence.Entity

@Table (name="CHINASAVEOHLC")

public class ChinaSaveOHLCYV {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    String stockName;
    @Column(name = "O")
    double open;
    @Column(name = "H")
    double high;
    @Column(name = "L")
    double low;
    @Column(name = "C")
    double close;
    @Column(name = "CY")
    double closeY;
    @Column(name = "V")
    double volume;
    
    public ChinaSaveOHLCYV(String name, double c) {
        this.stockName = name;
        this.open = c;
        this.high= c;
        this.low = c;
        this.close = c;
        this.closeY = c;
        this.volume = 0.0;
    }
    
    public ChinaSaveOHLCYV(String name, double op, double hi, double lo, double c, double cy ,int v ) {
        this.stockName = name;
        this.open = op;
        this.high= hi;
        this.low = lo;
        this.close = c;
        this.closeY = cy;
        this.volume = v;
    }
    
    public ChinaSaveOHLCYV() {}
    ChinaSaveOHLCYV(String name) { this.stockName = name; };
    
    double getOpen() {return open; }
    double getHigh() {return high; }        
    double getLow()  { return low;}    
    double getClose() {return close; }
    double getCloseY() {return closeY; }
    double getVolume() {return volume; }
    void setOpen(double op) {this.open = op;}
    void getHigh(double hi) {this.high = hi; }        
    void getLow(double lo) {  this.low = lo; }    
    void getClose(double cl) { this.close = cl; }
    void getCloseY(double cly) {this.closeY = cly; }
    void getVolume(int v) { this.volume = v; }
    
    @Override public int hashCode() {int hash = 0; return hash += (stockName != null ? stockName.hashCode() : 0);}

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ChinaSaveOHLCYV)) { return false; }
        ChinaSaveOHLCYV other = (ChinaSaveOHLCYV) object;
        return !((this.stockName == null && other.stockName != null) || (this.stockName != null && !this.stockName.equals(other.stockName)));
    }
    
    String s(double d) {return Double.toString(d);}
    
    @Override public String toString() { return getStr(" ","apidemo.ChinaSaveOHLCYV[ id=",stockName," O H L C V",s(open),s(high),s(low),s(close),s(closeY),s(volume)," ]"); }
}
