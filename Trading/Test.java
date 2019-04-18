import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;

import static utility.Utility.pr;

public class Test {
    public static final DateTimeFormatter f3 = DateTimeFormatter.ofPattern("M-d H:mm:s.SSSS");

    public static void main(String[] args) {
        CompletableFuture<Integer> c1 = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 1;
        });
        CompletableFuture<Void> c2 = c1.thenAccept((a) -> pr("a is ", a));


//        try {
//            c2.get();
//        } catch (InterruptedException | ExecutionException e) {
//            e.printStackTrace();
//        }

    }
}
