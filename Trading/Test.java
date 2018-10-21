import java.time.LocalDate;

import static utility.Utility.pr;

public class Test {


    private static LocalDate getLastMonthLastDay() {
        LocalDate now = LocalDate.now().withDayOfMonth(1);
        return now.minusDays(1L);
    }

    private static LocalDate getLastYearLastDay() {
        LocalDate now = LocalDate.now().withDayOfYear(1);
        return now.minusDays(1L);
    }

    public static void main(String[] args) {

//        Pattern p = Pattern.compile("^(?!sh|sz).*$");
//        Matcher m = p.matcher("hk000001");
//        while (m.find()) {
//            pr(m.group());
//        }
        pr(getLastYearLastDay());

    }

//
}
