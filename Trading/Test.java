import apidemo.Direction;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static utility.Utility.pr;

public class Test {

    static double roundToXUPriceAggressive(double x, Direction dir) {
        return (Math.round(x * 10) - Math.round(x * 10) % 25) / 10d + (dir == Direction.Long ? 2.5 : 0.0);
    }

    public static void main(String[] args) {

        LocalDateTime t = LocalDateTime.now();

        ZoneId chinaZone = ZoneId.of("Asia/Shanghai");
        ZoneId nyZone = ZoneId.of("America/New_York");

        ZonedDateTime chinaZdt = ZonedDateTime.of(t, chinaZone);
        ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
        LocalDateTime usLdt = usZdt.toLocalDateTime();

        pr(t, usLdt);

        //pr(roundToXUPriceAggressive(11279.62, Direction.Short));
    }
}
