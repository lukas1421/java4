import java.time.LocalDate;

import static utility.Utility.pr;

public class Test {


    public static LocalDate getLastMonthLastDay(LocalDate d) {
        LocalDate now = d.withDayOfMonth(1);
        return now.minusDays(1L);
    }

    public static class class1 {
        public static volatile int class1Var = 5;

        public static int getC1() {
            return class1.class1Var;
        }

        public static void resetC1(int x) {
            class1Var = x;
        }
    }

    public static class class2 {
        public static volatile int class2Var = 10;

        public static int getC1() {
            return class1.class1Var;
        }
    }

    public static void main(String[] args) {
        class1 c1 = new class1();
        class2 c2 = new class2();

        //pr(class1.class1Var);
        class1.resetC1(10);
        pr(class1.getC1());
        pr(class2.getC1());


    }
}
