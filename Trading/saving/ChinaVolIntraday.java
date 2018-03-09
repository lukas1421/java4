package saving;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Lob;
import java.sql.Blob;

public class ChinaVolIntraday {

    @Id
    String optionTicker;

    @Column(name = "VOL")
    @Lob
    private Blob intradayVolMap;

    public ChinaVolIntraday() {

    }

    public ChinaVolIntraday(String ticker) {
        optionTicker = ticker;
    }

    public void setVolBlob(Blob v) {
        intradayVolMap = v;
    }

    public Blob getVolBlob() {
        return intradayVolMap;
    }

}
