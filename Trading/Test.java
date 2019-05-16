import utility.TradingUtility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;

import static utility.Utility.pr;


public class Test {


    public static void main(String[] args) {


        pr(TradingUtility.getActiveBTCExpiry());
        pr(TradingUtility.get2ndBTCExpiry());

//        pr(TradingUtility.getPrevBTCExpiryGivenTime(LocalDateTime.of(LocalDate.of(2019, Month.MAY,16),
//                LocalTime.of(0,0,0))));
//
//        pr(TradingUtility.getPrevBTCExpiryGivenTime(LocalDateTime.of(LocalDate.of(2019, Month.MAY,16),
//                LocalTime.of(5,0,0))));

    }
}

//public class Test {
//
//
//    public static void main(String[] args) {
//
//
//    }
//
//}
