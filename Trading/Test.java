import static utility.Utility.pr;

public class Test {

//    static double roundToXUPriceAggressive(double x, Direction dir) {
//        return (Math.round(x * 10) - Math.round(x * 10) % 25) / 10d + (dir == Direction.Long ? 2.5 : 0.0);
//    }
//
//    public static void main(String[] args) {
//        AutoTraderMain a = new AutoTraderMain();
//        AutoTraderMain.checkIfHoliday(LocalDate.now());
//    }

    public static int minToQuarterHour(int min) {
        return (min - min % 15);
    }

    public static void main(String[] args) {

//        System.out.println(minToQuarterHour(14));
//        System.out.println(minToQuarterHour(15));
//        System.out.println(minToQuarterHour(29));
//        System.out.println(minToQuarterHour(31));
//        System.out.println(minToQuarterHour(44));
//        System.out.println(minToQuarterHour(48));

//        double freshPrice = 2.8555;
//        pr(Math.floor(freshPrice * 100d) / 100d);
//        pr(Math.ceil(freshPrice * 100d) / 100d);

//        pr(Math.max(1, Math.floor(Math.abs(-1.0) / 2)));
//        pr(Math.max(1, Math.floor(Math.abs(2.0) / 2)));
//        pr(Math.max(1, Math.floor(Math.abs(-3.0) / 2)));
//        pr(Math.max(1, Math.floor(Math.abs(4.0) / 2)));
//        pr(Math.max(1, Math.floor(Math.abs(-5.0) / 2)));
//        pr(Math.max(1, Math.floor(Math.abs(6.0) / 2)));

        double last = 2.8092;
        pr(Math.floor(last * 100d) / 100d);
        pr(Math.ceil(last * 100d) / 100d);

    }
}
