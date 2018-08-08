package apidemo;

import client.Contract;
import client.TickType;
import client.Types;
import controller.ApiConnection;
import controller.ApiController;
import handler.HistoricalHandler;
import handler.LiveHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.SECONDS;
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

    private void getBOCOfficial() {
        pr(" getting BOCFX ");
        String urlString = "http://www.boc.cn/sourcedb/whpj";
        String line1;
        Pattern p1 = Pattern.compile("(?s)港币</td>.*");
        Pattern p2 = Pattern.compile("<td>(.*?)</td>");
        Pattern p3 = Pattern.compile("</tr>");
        boolean found = false;
        List<String> l = new LinkedList<>();

        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(Proxy.NO_PROXY);
            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                while ((line1 = reader2.readLine()) != null) {
                    if (!found) {
                        Matcher m = p1.matcher(line1);
                        while (m.find()) {
                            found = true;
                        }
                    } else {
                        if (p3.matcher(line1).find()) {
                            break;
                        } else {
                            Matcher m2 = p2.matcher(line1);
                            while (m2.find()) {
                                l.add(m2.group(1));
                            }
                        }
                    }
                }
                pr("l " + l);

                if (l.size() > 0) {
                    pr("***********************************");
                    pr("BOC HKD" +
                            "\t" + Double.parseDouble(l.get(3)) / 100d + "\t" + l.get(5) + "\t" + l.get(6));
                    pr("***********************************");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
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
        getUSDDetailed(ap);
        getFXLast(ap);
    }

    public static void main(String[] args) {

        CNHHKDLive c = new CNHHKDLive();
        c.getFromIB();

        ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);

        ses.scheduleAtFixedRate(c::getBOCOfficial, 0, 30, SECONDS);

    }

    @Override
    public void handlePrice(TickType tt, String name, double price, LocalDateTime t) {
        pr("handleprice ", name, t, tt, Math.round(10000d / price) / 10000d);
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

            int hr = ldt.getHour();

            if (hr % 3 == 0 && hr >= 6) {
                pr("hist: ", name, ldt, Math.round(1 / open * 1000d) / 1000d, Math.round(1 / close * 1000d) / 1000d);
            }
            //Utility.simpleWriteToFile("USD" + "\t" + close, true, fxOutput);

        }

    }

    @Override
    public void actionUponFinish(String name) {

    }
}