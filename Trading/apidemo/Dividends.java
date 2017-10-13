package apidemo;

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
    LocalDate adjustDate;
    double adjFactor;

    public Dividends() {
        adjustDate = LocalDate.MAX;
        adjFactor = 0.0;
    }

    public Dividends(LocalDate d, double f) {
        adjustDate = d;
        adjFactor = f;
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
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH + "div.txt"), "gbk"))) {
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
