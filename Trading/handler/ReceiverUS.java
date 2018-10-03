package handler;

import apidemo.ChinaData;
import apidemo.ChinaStock;
import client.TickType;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import static apidemo.AutoTraderMain.*;
import static apidemo.AutoTraderUS.*;
import static apidemo.XuTraderHelper.outputDetailedUS;
import static utility.Utility.str;


public class ReceiverUS implements LiveHandler {
    private String symbolToReceive;

    public ReceiverUS(String symbol) {
        symbolToReceive = symbol;
    }

//    private static final ReceiverUS rec = new ReceiverUS();
//    public static ReceiverUS getReceiverUS() {
//        return rec;
//    }

    @Override
    public void handlePrice(TickType tt, String symbol, double price, LocalDateTime t) {
        ZonedDateTime chinaZdt = ZonedDateTime.of(t, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();
        LocalTime usLt = usLdt.toLocalTime();

        switch (tt) {
            case LAST:
                usFreshPriceMap.put(symbol, price);
                if (usLt.isAfter(ltof(3, 0)) && usLt.isBefore(ltof(17, 0))) {
                    ChinaData.priceMapBarDetail.get(symbol).put(usLt, price);
//                    outputToUSPriceTest("***********");
//                    outputToUSPriceTest(str(" US -> PMB Detailed, SYMB, tt, symb, price,lastEntry "
//                            , symbolToReceive, tt, symbol, price, ChinaData.priceMapBarDetail.get(symbol).lastEntry()));
                }
                processMainUS(symbol, usLdt, price);
                ChinaStock.priceMap.put(symbol, price);
//                outputToUSPriceTest(str(" US handle price ", symbolToReceive,
//                        tt, symbol, price, "ChinaT: ", t, "US T:", usLdt));
                break;
            case BID:
                usBidMap.put(symbol, price);
                break;
            case ASK:
                usAskMap.put(symbol, price);
                break;
            case OPEN:
                usOpenMap.put(symbol, price);
                break;
        }

    }

    @Override
    public void handleVol(TickType tt, String name, double vol, LocalDateTime t) {


    }

    @Override
    public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {
        switch (tt) {
            case SHORTABLE:
                outputDetailedUS(symbol, str("US handle generic", tt, symbol, value, t));
                usShortableValueMap.put(symbol, value);
                break;

        }
    }
}
