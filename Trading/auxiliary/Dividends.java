package auxiliary;

import apidemo.ChinaData;
import apidemo.TradingConstants;
import utility.Utility;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Dividends {

    String ticker;
    String chineseName;
    private LocalDate adjustDate;
    private double adjFactor;

    public Dividends() {
        adjustDate = LocalDate.MAX;
        adjFactor = 0.0;
    }

    public Dividends(LocalDate d, double f) {
        adjustDate = d;
        adjFactor = f;
    }

    public static void dealWithDividends() {

        LocalDate today = LocalDate.now().minusDays(1L);
        //LocalDate ytd = today.getDayOfWeek().equals(DayOfWeek.MONDAY)?today.minusDays(3L):today.minusDays(1L);
        //LocalDate y2 = today.getDayOfWeek().equals(DayOfWeek.MONDAY)?today.minusDays(4L):today.minusDays(2L);
        LocalDate ytd = Utility.getPreviousWorkday(today);
        LocalDate y2 = Utility.getPreviousWorkday(ytd);

        System.out.println(" ytd is " + ytd);
        System.out.println(" y2 is " + y2);

        Map<String, Dividends> divTable = getDiv();

        divTable.forEach((ticker, div) -> {
            if (ChinaData.priceMapBarYtd.containsKey(ticker) && ChinaData.priceMapBarY2.containsKey(ticker)) {
                System.out.println(" correcting for ticker " + ticker);
                // if(div.getExDate().equals(today)) {
                System.out.println(" correcting div YTD for " + ticker + " " + div.toString());
                ChinaData.priceMapBarYtd.get(ticker).replaceAll((k, v) -> {
                    SimpleBar sb = new SimpleBar(v);
                    return sb;
                });

                //dangerous reference equality induced by fillHoles by copying the reference of the higher entry
                ChinaData.priceMapBarYtd.get(ticker).entrySet().stream().forEach(e -> {
                    e.getValue().adjustByFactor(div.getAdjFactor());
                });
                //get rid of same reference
                // if(div.getExDate().equals((ytd)) || div.getExDate().equals(today)) {

                System.out.println(" correcting div Y2 for " + ticker + " " + div.toString());

                ChinaData.priceMapBarY2.get(ticker).replaceAll((k, v) -> {
                    SimpleBar sb = new SimpleBar(v);
                    return sb;
                });

                ChinaData.priceMapBarY2.get(ticker).entrySet().stream().forEach(e -> {
                    //System.out.println( " Y2 time " + e.getKey());
                    //System.out.println( " Y2  before " + e.getValue());
                    e.getValue().adjustByFactor(div.getAdjFactor());
                    //System.out.println( " Y2  after " + e.getValue());
                });
            }
        });
    }

    LocalDate getExDate() {
        return adjustDate;
    }

    double getAdjFactor() {
        return adjFactor;
    }

    @Override
    public String toString() {
        return " adjustment date " + adjustDate + " factor " + adjFactor;
    }

    static Map<String, Dividends> getDiv() {
        String line;
        Pattern p = Pattern.compile("(sh|sz)\\d{6}");
        List<String> l;
        Matcher m;

        Map<String, Dividends> adjFactor = new HashMap<>();
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + "div.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                l = Arrays.asList(line.split("\\t+"));
                m = p.matcher(l.get(1));
                if (m.find()) {
                    System.out.println(" ticker " + l.get(1));
                    LocalDate divDate;
                    try {
                        divDate = LocalDate.parse(l.get(4));
                        System.out.println(" div date " + divDate);
                    } catch (DateTimeParseException ex) {
                        System.out.println(" no cash div, go to stock div");
                        divDate = LocalDate.parse(l.get(6));
                        System.out.println(" div date " + divDate);
                    }
                    adjFactor.put(l.get(1), new Dividends(divDate, Double.parseDouble(l.get(8))));
                    System.out.println(l.get(1) + " div just inputted is " + adjFactor.get(l.get(1)));
                }
            }
        } catch (IOException ex1) {
            ex1.printStackTrace();
        }
        return adjFactor;
    }

    public static void main(String[] args) {
        System.out.println(getDiv());
    }
}
