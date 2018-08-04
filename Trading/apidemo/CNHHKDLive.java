package apidemo;

import client.Contract;
import client.TickType;
import client.Types;
import controller.ApiConnection;
import controller.ApiController;
import handler.HistoricalHandler;
import handler.LiveHandler;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

import static utility.Utility.pr;

public class CNHHKDLive implements LiveHandler, HistoricalHandler {

    private Contract getCNHHKDContract() {
        Contract c = new Contract();
        c.symbol("CNH");
        c.secType(Types.SecType.CASH);
        c.exchange("IDEALPRO");
        c.currency("HKD");
        c.strike(0.0);
        c.right(Types.Right.None);
        c.secIdType(Types.SecIdType.None);
        return c;
    }

    private void getUSDDetailed(ApiController ap) {

        Contract c = getCNHHKDContract();

        LocalDateTime lt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatTime = lt.format(dtf);

        pr(" format time " + formatTime);

        pr(" requesting live contract for CNHKKD ");
        ap.reqLiveContract(c, this, false);
    }

    private void getFXLast(ApiController ap) {
        LocalDateTime lt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatTime = lt.format(dtf);

        Contract c = getCNHHKDContract();

        ap.reqHistoricalDataSimple(2, this, c, formatTime, 3, Types.DurationUnit.DAY,
                Types.BarSize._1_hour, Types.WhatToShow.MIDPOINT, false);
    }

    private void getFromIB() {
        ApiController ap = new ApiController(new ApiController.IConnectionHandler.DefaultConnectionHandler(),
                new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            ap.connect("127.0.0.1", 7496, 3, "");
            connectionStatus = true;
            pr(" connection status is true ");
            l.countDown();
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ");
        }

        if (!connectionStatus) {
            pr(" using port 4001 ");
            ap.connect("127.0.0.1", 4001, 3, "");
            l.countDown();
            pr(" Latch counted down " + LocalTime.now());
        }

        try {
            l.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        pr(" Time after latch released " + LocalTime.now());
        pr(" requesting position ");
        //getUSDDetailed(ap);
        getFXLast(ap);
    }

    public static void main(String[] args) {
        CNHHKDLive c = new CNHHKDLive();
        c.getFromIB();
    }

    @Override
    public void handlePrice(TickType tt, String name, double price, LocalDateTime t) {
        pr(name, t, tt, 1 / price);
    }

    @Override
    public void handleVol(String name, double vol, LocalDateTime t) {

    }

    @Override
    public void handleHist(String name, String date, double open, double high, double low, double close) {
        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);

            ZoneId chinaZone = ZoneId.of("Asia/Shanghai");
            ZoneId nyZone = ZoneId.of("America/New_York");
            LocalDateTime ldt = LocalDateTime.ofInstant(dt.toInstant(), chinaZone);
            ZonedDateTime zdt = ZonedDateTime.of(ldt, chinaZone);
            //HKDCNH = 1 / close;

            pr("hist ", name, ldt, Math.round(1 / open * 1000d) / 1000d, Math.round(1 / close * 1000d) / 1000d);
            //Utility.simpleWriteToFile("USD" + "\t" + close, true, fxOutput);

        }

    }

    @Override
    public void actionUponFinish(String name) {

    }
}