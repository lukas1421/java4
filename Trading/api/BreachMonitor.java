package api;

import auxiliary.SimpleBar;
import client.Contract;
import client.TickType;
import client.Types;
import controller.ApiConnection;
import controller.ApiController;
import handler.LiveHandler;
import utility.Utility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static utility.Utility.*;

public class BreachMonitor implements LiveHandler, ApiController.IPositionHandler {

    private static final DateTimeFormatter f = DateTimeFormatter.ofPattern("M-d");
    private static final DateTimeFormatter f2 = DateTimeFormatter.ofPattern("M-d H:mm:s");
    private static final LocalDate LAST_MONTH_DAY = getLastMonthLastDay();
    private static final LocalDate LAST_YEAR_DAY = getLastYearLastDay();
    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            ytdDayData = new ConcurrentSkipListMap<>(String::compareTo);
    private static ApiController staticController;
    private volatile static Map<Contract, Double> holdingsMap =
            new TreeMap<>(Comparator.comparing(Utility::ibContractToSymbol));
    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);


    private void getFromIB() {

        ApiController ap = new ApiController(new ApiController.IConnectionHandler.DefaultConnectionHandler(),
                new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
        staticController = ap;
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            ap.connect("127.0.0.1", 7496, 3, "");
            connectionStatus = true;
            pr(" connection : status is true ");
            l.countDown();
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ");
        }

        if (!connectionStatus) {
            pr(" using port 4001");
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
        reqHoldings(ap);
    }

    private void reqHoldings(ApiController ap) {
        //pr(" request holdings ");
        ap.reqPositions(this);
    }

    //positions
    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        if (!contract.symbol().equals("USD")) {
            holdingsMap.put(contract, position);
        }
    }

    @Override
    public void positionEnd() {
        //pr(" holdings map ", holdingsMap);
        holdingsMap.entrySet().stream().forEachOrdered((e)
                -> pr("symb pos ", ibContractToSymbol(e.getKey()), e.getValue()));
        for (Contract c : holdingsMap.keySet()) {
            String k = ibContractToSymbol(c);
            ytdDayData.put(k, new ConcurrentSkipListMap<>());
            if (!k.startsWith("sz") && !k.startsWith("sh") && !k.equals("USD")) {
                staticController.reqHistDayData(ibStockReqId.addAndGet(5),
                        fillContract(c), BreachMonitor::morningYtdOpen, 20, Types.BarSize._1_day);
            }
            staticController.req1ContractLive(c, this, false);
        }
    }

    private static void morningYtdOpen(Contract c, String date, double open, double high, double low,
                                       double close, int volume) {
        String symbol = utility.Utility.ibContractToSymbol(c);
        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            //pr("morningYtdOpen", symbol, ld, open, high, low, close);
            ytdDayData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        } else {
            //finished
            //pr(" finished ", c.symbol(), date, open, close);
            double size = holdingsMap.getOrDefault(c, 0.0);
            if (ytdDayData.containsKey(symbol) && ytdDayData.get(symbol).size() > 0) {

                double yOpen = ytdDayData.get(symbol).higherEntry(LAST_YEAR_DAY).getValue().getOpen();

                long yCount = ytdDayData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY)).count();
                double mOpen = ytdDayData.get(symbol).higherEntry(LAST_MONTH_DAY).getValue().getOpen();
                long mCount = ytdDayData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY)).count();
                double last;
                double secLast;
                last = ytdDayData.get(symbol).lastEntry().getValue().getClose();
                secLast = ytdDayData.get(symbol).lowerEntry(ytdDayData.get(symbol)
                        .lastKey()).getValue().getClose();
                String info = "";
                double lastChg = Math.round((last / secLast - 1) * 1000d) / 10d;
                double yDev = Math.round((last / yOpen - 1) * 1000d) / 10d;
                double mDev = Math.round((last / mOpen - 1) * 1000d) / 10d;
                if (size > 0) {
                    if (yDev > 0 && mDev > 0) {
                        info = "LONG ON ";
                    } else {
                        info = "LONG OFF ";
                    }
                } else if (size < 0) {
                    if (yDev < 0 && mDev < 0) {
                        info = "SHORT ON ";
                    } else {
                        info = "SHORT OFF ";
                    }
                } else {
                    info = "NO POS ";
                }

                String out = str(symbol, size, ytdDayData.get(symbol).lastEntry().getKey().format(f), last,
                        lastChg + "%", "||yOpen", ytdDayData.get(symbol).higherEntry(LAST_YEAR_DAY).getKey().format(f),
                        yOpen,
                        "y#" + yCount, "yUp%",
                        Math.round(1000d * ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
                                .filter(e -> e.getValue().getClose() > yOpen).count() / yCount) / 10d + "%",
                        "yDev", yDev + "%",
                        "||mOpen ", ytdDayData.get(symbol).higherEntry(LAST_MONTH_DAY).getKey().format(f), mOpen,
                        "m#" + mCount, "mUp%",
                        Math.round(1000d * ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY))
                                .filter(e -> e.getValue().getClose() > mOpen).count() / mCount) / 10d + "%",
                        "mDev", mDev + "%", info);
                pr("*LAST", out);
            }
        }
    }

    private static Contract fillContract(Contract c) {
        if (c.symbol().equals("XINA50")) {
            c.exchange("SGX");
        }
        if (c.currency().equals("USD") && c.secType().equals(Types.SecType.STK)) {
            c.exchange("SMART");
        }
        return c;
    }

    //live
    @Override
    public void handlePrice(TickType tt, String symbol, double price, LocalDateTime t) {
        pr("last symbol ", tt, symbol, price, t.format(f2));
        switch (tt) {
            case LAST:
                if (ytdDayData.get(symbol).size() > 0
                        && ytdDayData.get(symbol).firstKey().isBefore(LAST_YEAR_DAY)) {
                    double yOpen = ytdDayData.get(symbol).higherEntry(LAST_YEAR_DAY).getValue().getOpen();
                    double mOpen = ytdDayData.get(symbol).higherEntry(LAST_MONTH_DAY).getValue().getOpen();
                    double secLast;
                    secLast = ytdDayData.get(symbol).lowerEntry(ytdDayData.get(symbol)
                            .lastKey()).getValue().getClose();
                    String info = "";
                    double lastChg = Math.round((price / secLast - 1) * 1000d) / 10d;
                    double yDev = Math.round((price / yOpen - 1) * 1000d) / 10d;
                    double mDev = Math.round((price / mOpen - 1) * 1000d) / 10d;

                    String yBreachStatus = "";
                    if (secLast > yOpen && price < yOpen) {
                        yBreachStatus = "y BREACHED DOWN";
                    } else if (secLast < yOpen && price > yOpen) {
                        yBreachStatus = "y BREACHED UP";
                    }

                    String mBreachStatus = "";
                    if (secLast > mOpen && price < mOpen) {
                        mBreachStatus = "m BREACHED DOWN";
                    } else if (secLast < mOpen && price > mOpen) {
                        mBreachStatus = "m BREACHED UP";
                    }

                    String out = str(symbol, "||LIVE", tt, t.format(f2), price, lastChg + "%"
                            , "LAST DAY", ytdDayData.get(symbol).lastKey().format(f)
                            , ytdDayData.get(symbol).lastEntry().getValue().getClose()
                            , "||yOpen", ytdDayData.get(symbol).higherEntry(LAST_YEAR_DAY).getKey().format(f),
                            yOpen, "yDev", yDev + "%",
                            "||mOpen ", ytdDayData.get(symbol).higherEntry(LAST_MONTH_DAY).getKey().format(f), mOpen,
                            "mDev", mDev + "%", info);
                    pr("*", out, "Y", yBreachStatus, "M", mBreachStatus);
                }
        }
    }

    @Override
    public void handleVol(TickType tt, String symbol, double vol, LocalDateTime t) {
        //pr("handle vol ", tt, symbol, vol, t);

    }

    @Override
    public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {
        //pr("handle generic ", tt, symbol, value, t);

    }

    public static void main(String[] args) {
        BreachMonitor bm = new BreachMonitor();
        bm.getFromIB();


    }

}
