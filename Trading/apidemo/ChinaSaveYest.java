package apidemo;

import auxiliary.ChinaSaveInterface2Blob;
import auxiliary.SimpleBar;

import java.io.Serializable;
import java.sql.Blob;
import java.time.LocalTime;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import static utility.Utility.trimSkipMap;

@Entity
@Table(name = "CHINASAVEYEST")
public class ChinaSaveYest implements Serializable, ChinaSaveInterface2Blob {

    private static final long serialVersionUID = 1357900L;
    static final ChinaSaveYest CSY = new ChinaSaveYest();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    String stockName;

    @Column(name = "CHINESE")
    String chineseName;

    @Column(name = "DATA1")
    @Lob
    Blob dayPriceMapBlob;

    @Column(name = "VOL")
    @Lob
    Blob volMapBlob;

    public ChinaSaveYest() {
    }

    public ChinaSaveYest(String name) {
        this.stockName = name;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        return hash += (stockName != null ? stockName.hashCode() : 0);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ChinaSaveYest)) {
            return false;
        }
        ChinaSaveYest other = (ChinaSaveYest) object;
        return !((this.stockName == null && other.stockName != null) || (this.stockName != null && !this.stockName.equals(other.stockName)));
    }

    @Override
    public String toString() {
        return "apidemo.ChinaSaveYest[ id=" + stockName + " ]";
    }

    @Override
    public void setFirstBlob(Blob x) {
        this.dayPriceMapBlob = x;
    }

    @Override
    public void setSecondBlob(Blob x) {
        this.volMapBlob = x;
    }

    @Override
    public void updateFirstMap(String name, NavigableMap<LocalTime, ?> mp) {
        ChinaData.priceMapBarYtd.put(name, (ConcurrentSkipListMap<LocalTime, SimpleBar>) trimSkipMap(mp, LocalTime.of(9, 29)));
    }

    @Override
    public void updateSecondMap(String name, NavigableMap<LocalTime, ?> mp) {
        ChinaData.sizeTotalMapYtd.put(name, (ConcurrentSkipListMap<LocalTime, Double>) trimSkipMap(mp, LocalTime.of(9, 29)));
    }

    @Override
    public Blob getFirstBlob() {
        return this.dayPriceMapBlob;
    }

    @Override
    public Blob getSecondBlob() {
        return this.volMapBlob;
    }

    @Override
    public ChinaSaveInterface2Blob createInstance(String name) {
        return new ChinaSaveYest(name);
    }

    static ChinaSaveYest getInstance() {
        return CSY;
    }

    ;
    @Override
    public String getSimpleName() {
        return "yest";
    }
}
