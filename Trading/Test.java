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

    public static void main(String[] args) {
        double defaultSize = 500;
        double livePos = 355;

        double roundPos = Math.floor((defaultSize - Math.abs(livePos)) / 100d) * 100d;

        pr(roundPos);
    }
}
