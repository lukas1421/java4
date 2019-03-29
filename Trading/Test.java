import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;

import static utility.Utility.pr;

public class Test {

    private static Predicate<Integer> biggerThan(int x) {
        return e -> e > x;
    }

    static void showAllBiggerThanX() {

    }

    public static void main(String[] args) {
        List<Integer> l = Arrays.asList(1, 2, 3, 4, 10, 12);
        l.stream().filter(e -> e > 10).forEach(System.out::println);
    }
}
