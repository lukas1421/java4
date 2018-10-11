import client.Types;
import controller.ApiConnection;
import controller.ApiController;
import handler.HistoricalHandler;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;

import static apidemo.AutoTraderHK.getHKFutContract;
import static utility.Utility.pr;

public class Test {


    public static void main(String[] args) {
//        pr(getSecondLastBD(LocalDate.now()));
//        pr(getSecondLastBD(LocalDate.of(2018, Month.NOVEMBER, 2)));
//        pr(getSecondLastBD(LocalDate.of(2018, Month.DECEMBER, 2)));


        ApiController ap = new ApiController(new ApiController.IConnectionHandler.DefaultConnectionHandler(),
                new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            ap.connect("127.0.0.1", 7496, 2, "");
            connectionStatus = true;
            pr(" connection status is true ");
            l.countDown();
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ");
        }

        if (!connectionStatus) {
            pr(" using port 4001 ");
            ap.connect("127.0.0.1", 4001, 2, "");
            l.countDown();
            pr(" Latch counted down " + LocalTime.now());
        }

        try {
            l.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        LocalDateTime lt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatTime = lt.format(dtf);

        pr(" format time " + formatTime);

        ap.reqHistoricalDataSimple(500000
                , new HistoricalHandler.DefaultHandler(),
                getHKFutContract("MCH.HK"), formatTime, 1, Types.DurationUnit.DAY,
                Types.BarSize._1_min, Types.WhatToShow.MIDPOINT, false);
    }
}
