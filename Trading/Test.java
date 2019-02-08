import auxiliary.SimpleBar;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

import static utility.Utility.pr;

public class Test {

    private static ConcurrentSkipListMap<LocalDate, SimpleBar>
            ytdData = new ConcurrentSkipListMap<>();

    public static void readThis() {

        String line = "";
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream("/home/l/chinaData/sh000001_day.csv")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split(","));
                pr(al1);
                if (!al1.get(0).equalsIgnoreCase("date")) {
                    LocalDate d = LocalDate.parse(al1.get(0), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    ytdData.put(d, new SimpleBar(Double.parseDouble(al1.get(1)), Double.parseDouble(al1.get(2)),
                            Double.parseDouble(al1.get(3)), Double.parseDouble(al1.get(4))));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        Test.readThis();

    }
}
