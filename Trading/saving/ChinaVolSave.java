package saving;


import apidemo.ChinaOption;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.Random;

@javax.persistence.Entity

@Table(name = "CHINAVOLSAVE")

public class ChinaVolSave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int id;

    @Column(name = "VOLDATE")
    LocalDate volDate;

    @Column(name = "CALLPUT")
    String callPut;

    @Column(name = "STRIKE")
    double strike;

    @Column(name = "EXPIRYDATE")
    LocalDate expiryDate;

    @Column(name = "VOL")
    double vol;

    @Column(name = "MONEYNESS")
    int moneyness;

    @Column(name = "OPTIONTICKER")
    String optionTicker;

    public ChinaVolSave() {
        volDate = LocalDate.now();
        callPut = "C";
        Random r = new Random();
        strike = r.nextDouble();
        expiryDate = ChinaOption.frontExpiry;
        vol = 0.25;
        moneyness = 100;
        optionTicker = "CON_OP_10000987";
    }

    public ChinaVolSave(LocalDate voldate, String cp, double k, LocalDate expirydate, double v, int mness, String ticker) {
        volDate = voldate;
        callPut = cp;
        strike = k;
        expiryDate = expirydate;
        vol = v;
        moneyness = mness;
        optionTicker = ticker;
    }
}
