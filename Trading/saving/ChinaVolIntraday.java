package saving;

import apidemo.ChinaOption;
import auxiliary.SimpleBar;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Lob;
import java.sql.Blob;
import java.time.LocalDateTime;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class ChinaVolIntraday implements ChinaSaveInterface1Blob {

    static final ChinaVolIntraday CVI = new ChinaVolIntraday();

    @Id
    String optionTicker;

    @Column(name = "VOL")
    @Lob
    private Blob intradayVolBlob;

    public ChinaVolIntraday() {}

    public ChinaVolIntraday(String ticker) {
        optionTicker = ticker;
    }


    @Override
    public void setFirstBlob(Blob x) {
        intradayVolBlob = x;
    }

    @Override
    public void updateFirstMap(String name, NavigableMap<LocalDateTime, ?> mp) {
        //noinspection unchecked
        ChinaOption.todayImpliedVolMap.put(name, (ConcurrentSkipListMap<LocalDateTime, SimpleBar>)mp);
    }

    @Override
    public Blob getFirstBlob() {
        return intradayVolBlob;
    }

    @Override
    public String getSimpleName() {
        return "China Vol Intraday";
    }

    public static ChinaVolIntraday getInstance() {
        return CVI;
    }


    @Override
    public ChinaSaveInterface1Blob createInstance(String name) {
        return new ChinaVolIntraday(name);
    }
}
