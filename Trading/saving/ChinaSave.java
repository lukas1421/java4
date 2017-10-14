package saving;

import auxiliary.SimpleBar;
import utility.Utility;

import java.io.Serializable;
import java.sql.Blob;
import java.time.LocalTime;
import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import static apidemo.ChinaData.*;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

@javax.persistence.Entity
@Table(name = "CHINASAVE")
public class ChinaSave implements Serializable, ChinaSaveInterface2Blob {

    private static final long serialVersionUID = 888888L;
    static final ChinaSave CS = new ChinaSave();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    String stockName;

    @Column(name = "DATA")
    @Lob
    Blob dayPriceMapBlob;

    @Column(name = "VOL")
    @Lob
    Blob volMapBlob;

    public ChinaSave() {
    }

    public ChinaSave(String name) {
        this.stockName = name;
    }

    public static ChinaSave getInstance() {
        return CS;
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
    public Blob getFirstBlob() {
        return dayPriceMapBlob;
    }

    @Override
    public Blob getSecondBlob() {
        return volMapBlob;
    }

    @Override
    public ChinaSave createInstance(String name) {
        return new ChinaSave(name);
    }

    @Override
    public void updateFirstMap(String name, NavigableMap<LocalTime, ?> mp) {
        //priceMapBar.put(name,(ConcurrentSkipListMap<LocalTime,SimpleBar>)trimSkipMap(mp, LocalTime.of(9,19)));
        priceMapBar.put(name, (ConcurrentSkipListMap<LocalTime, SimpleBar>) Utility.trimSkipMap(mp, LocalTime.of(9, 24)));
    }

    @Override
    public void updateSecondMap(String name, NavigableMap<LocalTime, ?> mp) {
        sizeTotalMap.put(name, (ConcurrentSkipListMap<LocalTime, Double>) Utility.trimSkipMap(mp, LocalTime.of(9, 24)));
    }

    @Override
    public int hashCode() {
        int hash = 0;
        return hash += (stockName != null ? stockName.hashCode() : 0);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ChinaSave)) {
            return false;
        }
        ChinaSave other = (ChinaSave) object;
        return !((this.stockName == null && other.stockName != null) || (this.stockName != null && !this.stockName.equals(other.stockName)));
    }

    @Override
    public String toString() {
        return "saving.ChinaSave[ id=" + stockName + " ]";
    }

    @Override
    public String getSimpleName() {
        return "PriceMapBar";
    }

}
