import utility.Utility;

import java.time.LocalTime;
import java.util.NavigableMap;
import java.util.TreeMap;

public class Test {


    public static void main(String[] args) {
        NavigableMap<LocalTime, Double> m = new TreeMap<>();
        m.put(LocalTime.of(9, 0), 100.0);
        m.put(LocalTime.of(10, 0), 100.0);
        m.put(LocalTime.of(11, 0), 100.0);


        //first max
        m.entrySet().stream().max((e1, e2) -> e1.getValue() >= e2.getValue() ? 1 : -1).map(e -> e.getKey()).ifPresent(
                Utility::pr);
        //last max
        m.entrySet().stream().max((e1, e2) -> e1.getValue() > e2.getValue() ? 1 : -1).map(e -> e.getKey()).ifPresent(
                Utility::pr);
        //last min
        m.entrySet().stream().min((e1, e2) -> e1.getValue() >= e2.getValue() ? 1 : -1).map(e -> e.getKey()).ifPresent(
                Utility::pr);
        //first min
        m.entrySet().stream().min((e1, e2) -> e1.getValue() > e2.getValue() ? 1 : -1).map(e -> e.getKey()).ifPresent(
                Utility::pr);
    }
}
