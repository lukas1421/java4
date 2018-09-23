import apidemo.AutoTraderMain;
import apidemo.Direction;

import java.time.LocalDate;

public class Test {

    static double roundToXUPriceAggressive(double x, Direction dir) {
        return (Math.round(x * 10) - Math.round(x * 10) % 25) / 10d + (dir == Direction.Long ? 2.5 : 0.0);
    }

    public static void main(String[] args) {
        AutoTraderMain a = new AutoTraderMain();
        AutoTraderMain.checkIfHoliday(LocalDate.now());
    }
}
