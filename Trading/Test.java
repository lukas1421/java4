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
        return (min-min%15);
    }

    public static void main(String[] args) {

        System.out.println(minToQuarterHour(14));
        System.out.println(minToQuarterHour(15));
        System.out.println(minToQuarterHour(29));
        System.out.println(minToQuarterHour(31));
        System.out.println(minToQuarterHour(44));
        System.out.println(minToQuarterHour(48));


    }
}
