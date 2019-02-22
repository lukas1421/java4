import auxiliary.SimpleBar;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

import static api.AutoTraderMain.chinaZone;
import static api.AutoTraderMain.nyZone;
import static utility.Utility.pr;

public class Test {


    public static void main(String[] args) {

        LocalDateTime ldt = LocalDateTime.now();
        ZonedDateTime chinaZdt = ZonedDateTime.of(ldt, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();
        //LocalDateTime lt = usLdt.toLocalTime();

        pr("ldt chinaZdt, usZdt, usLDt", ldt, chinaZdt, usZdt, usLdt);
    }
}
