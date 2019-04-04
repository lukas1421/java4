import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;

import static utility.Utility.pr;

public class Test {

    public static void main(String[] args) {

        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Shutdown Hook is running !")));
        System.out.println("Application Terminating ...");


        new ScheduledThreadPoolExecutor(1)
                .schedule(() -> System.exit(0), 20, TimeUnit.SECONDS);


    }
}
