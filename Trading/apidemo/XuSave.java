package apidemo;

import auxiliary.SimpleBar;

import java.io.Serializable;
import java.sql.Blob;
import java.time.LocalTime;
import java.util.NavigableMap;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity
@Table(name = "XU")
class XuSave implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    String name;

    @Column(name = "FUT")
    @Lob
    static Blob lastFutPriceBlob;

    @Column(name = "INDEX")
    @Lob
    static Blob indexPriceBlob;

    @Column(name = "FUTVOL")
    @Lob
    static Blob futVolBlob;

    @Column(name = "INDEXVOL")
    @Lob
    static Blob indexVolBlob;

    static XuSave xsSingleton = new XuSave();

    XuSave() {
    }

    XuSave(String nam) {
        name = nam;
    }

    Blob getFutBlob() {
        return lastFutPriceBlob;
    }

    Blob getIndexBlob() {
        return indexPriceBlob;
    }

    Blob getFutVolBlob() {
        return futVolBlob;
    }

    Blob getIndexVolBlob() {
        return indexVolBlob;
    }

    void setFutBlob(Blob b) {
        lastFutPriceBlob = b;
    }

    void setIndexBlob(Blob b) {
        indexPriceBlob = b;
    }

    void setFutVolBlob(Blob b) {
        futVolBlob = b;
    }

    void setIndexVolBlob(Blob b) {
        indexVolBlob = b;
    }
    //static void setBlobGen(Consumer<Blob> f, Blob b){f.accept(b);} 

    void updateFut(NavigableMap<LocalTime, ?> m) {
        XU.lastFutPrice = (NavigableMap<LocalTime, SimpleBar>) m;
    }

    void updateIndex(NavigableMap<LocalTime, ?> m) {
        XU.indexPriceSina = (NavigableMap<LocalTime, SimpleBar>) m;
    }

    void updateFutVol(NavigableMap<LocalTime, ?> m) {
        XU.futVol = (NavigableMap<LocalTime, Integer>) m;
    }

    void updateIndexVol(NavigableMap<LocalTime, ?> m) {
        XU.indexVol = (NavigableMap<LocalTime, Double>) m;
    }

    static XuSave getInstance() {
        return xsSingleton;
    }

}
