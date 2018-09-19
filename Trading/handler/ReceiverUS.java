package handler;

import apidemo.ChinaData;
import client.TickType;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static apidemo.AutoTraderMain.chinaZone;
import static apidemo.AutoTraderMain.nyZone;
import static apidemo.AutoTraderUS.*;
import static utility.Utility.pr;


public class ReceiverUS implements LiveHandler {
    private ReceiverUS() {
    }

    private static final ReceiverUS rec = new ReceiverUS();

    public static ReceiverUS getReceiverUS() {
        return rec;
    }

    @Override
    public void handlePrice(TickType tt, String symbol, double price, LocalDateTime t) {
        ZonedDateTime chinaZdt = ZonedDateTime.of(t, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();
        pr(" US handle price ", tt, symbol, price, "ChinaT: ", t, "US T:", usLdt);

        switch (tt) {
            case BID:
                usBidMap.put(symbol, price);
                break;
            case ASK:
                usAskMap.put(symbol, price);
                break;
            case LAST:
                usFreshPriceMap.put(symbol, price);
                ChinaData.priceMapBarDetail.get(symbol).put(usLdt.toLocalTime(), price);
                processMainUS(symbol, usLdt, price);
                break;
            case OPEN:
                usOpenMap.put(symbol, price);
                break;
        }

    }

    @Override
    public void handleVol(String name, double vol, LocalDateTime t) {

    }
}
