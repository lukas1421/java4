package historical;

import TradeType.Trade;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;

class HistChinaHelper {

    private HistChinaHelper() {
        throw new UnsupportedOperationException();
    }


    static double getTradingCostCustom(String name, LocalDate ld, Trade t) {
        if (ld.isBefore(LocalDate.of(2016, Month.NOVEMBER, 3))) {
            return t.getTradingCostCustomBrokerage(name, 3.1);
        } else {
            return t.getTradingCostCustomBrokerage(name, 2.0);
        }
    }

    static double getCostWithCommissionsCustom(String name, LocalDate ld, Trade t) {
        if (ld.isBefore(LocalDate.of(2016, Month.NOVEMBER, 3))) {
            return t.getCostWithCommissionCustomBrokerage(name, 3.1);
        } else {
            return t.getCostWithCommissionCustomBrokerage(name, 2.0);
        }
    }

    static LocalTime stringToLocalTime(String s) {
        if (s.length() != 4) {
            System.out.println(" length is not equal to 4");
            throw new IllegalArgumentException(" length is not equal to 4 ");
        } else {
            if (s.startsWith("0")) {
                return LocalTime.of(Integer.parseInt(s.substring(1, 2)), Integer.parseInt(s.substring(2)));
            } else {
                return LocalTime.of(Integer.parseInt(s.substring(0, 2)), Integer.parseInt(s.substring(2)));
            }
        }
    }
}
