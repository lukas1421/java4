package saving;

import utility.Utility;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Blob;
import java.time.LocalTime;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static apidemo.ChinaData.priceMapBarDetail;

@javax.persistence.Entity
@Table(name = "CHINASAVEDETAILED")

public class ChinaSaveDetailed implements Serializable {

    private static final long serialVersionUID = 88888800L;
    private static final ChinaSaveDetailed CS = new ChinaSaveDetailed();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String stockName;

    @Column(name = "DATA")
    @Lob
    private Blob dayPriceMapBlob;

    public ChinaSaveDetailed() {
    }

    private ChinaSaveDetailed(String name) {
        this.stockName = name;
    }

    public static ChinaSaveDetailed getInstance() {
        return CS;
    }

    public void setFirstBlob(Blob x) {
        this.dayPriceMapBlob = x;
    }

    public Blob getFirstBlob() {
        return dayPriceMapBlob;
    }

    public ChinaSaveDetailed createInstance(String name) {
        return new ChinaSaveDetailed(name);
    }

    public void updateFirstMap(String name, NavigableMap<LocalTime, ?> mp) {
        //priceMapBar.put(name,(ConcurrentSkipListMap<LocalTime,SimpleBar>)trimSkipMap(mp, LocalTime.of(9,19)));
        priceMapBarDetail.put(name, (ConcurrentSkipListMap<LocalTime, Double>) Utility.trimSkipMap(mp, LocalTime.of(9, 0)));
    }

    @Override
    public int hashCode() {
        int hash = 0;
        return hash + (stockName != null ? stockName.hashCode() : 0);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ChinaSaveDetailed)) {
            return false;
        }
        ChinaSaveDetailed other = (ChinaSaveDetailed) object;
        return !((this.stockName == null && other.stockName != null) || (this.stockName != null && !this.stockName.equals(other.stockName)));
    }

    @Override
    public String toString() {
        return "saving.ChinaSaveDetailed [ id=" + stockName + " ]";
    }

    public String getSimpleName() {
        return "PriceMapBarDetailed";
    }


}
