import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;

import static utility.Utility.pr;

public class Test {
    private static DateTimeFormatter f = DateTimeFormatter.ofPattern("M-d H:mm:ss:SSS");
    public static void main(String[] args) {
        pr(LocalDateTime.now().format(f));
    }
}
