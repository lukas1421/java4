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
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static utility.Utility.*;
import static utility.Utility.getLastYearLastDay;

public class BreachCutter implements LiveHandler, ApiController.IPositionHandler {

    private static final DateTimeFormatter f = DateTimeFormatter.ofPattern("M-d");
    private static final DateTimeFormatter f2 = DateTimeFormatter.ofPattern("M-d H:mm:s");
    private static final LocalDate LAST_MONTH_DAY = getLastMonthLastDay();
    private static final LocalDate LAST_YEAR_DAY = getLastYearLastDay();
    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            ytdDayData = new ConcurrentSkipListMap<>(String::compareTo);
    private volatile static Map<Contract, Double> holdingsMap =
            new TreeMap<>(Comparator.comparing(Utility::ibContractToSymbol));
    private volatile static Map<String, Double> symbolPosMap = new TreeMap<>(String::compareTo);
    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);
    public static Map<Currency, Double> fxMap = new HashMap<>();


    private static ApiController staticController;

    private void connectAndReqHoldings() {
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


    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        if (!contract.symbol().equals("USD")) {
            holdingsMap.put(contract, position);
            symbolPosMap.put(ibContractToSymbol(contract), position);
        }
    }


    private static void ytdOpen(Contract c, String date, double open, double high, double low,
                                double close, int volume) {
        String symbol = utility.Utility.ibContractToSymbol(c);
        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            ytdDayData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        } else {
            double size = holdingsMap.getOrDefault(c, 0.0);
            if (ytdDayData.containsKey(symbol) && ytdDayData.get(symbol).size() > 0) {

                double yOpen = ytdDayData.get(symbol).ceilingEntry(LAST_YEAR_DAY).getValue().getClose();

                long yCount = ytdDayData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY)).count();

                double mOpen = ytdDayData.get(symbol).ceilingEntry(LAST_MONTH_DAY).getValue().getClose();

                long mCount = ytdDayData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY)).count();
            }
        }


    }

    @Override
    public void positionEnd() {
        //pr(" holdings map ", holdingsMap);
        holdingsMap.entrySet().stream().forEachOrdered((e)
                -> pr("symb pos ", ibContractToSymbol(e.getKey()), e.getValue()));
        for (Contract c : holdingsMap.keySet()) {
            String k = ibContractToSymbol(c);
            pr("position end: ticker/symbol ", c.symbol(), k);
            ytdDayData.put(k, new ConcurrentSkipListMap<>());
            if (!k.equals("USD")) {
                staticController.reqHistDayData(ibStockReqId.addAndGet(5),
                        fillContract(c), BreachCutter::ytdOpen, 20, Types.BarSize._1_day);
            }
            staticController.req1ContractLive(c, this, false);
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

    @Override
    public void handlePrice(TickType tt, String symbol, double price, LocalDateTime t) {
        switch (tt) {
            case LAST:
                if (ytdDayData.get(symbol).size() > 0
                        && ytdDayData.get(symbol).firstKey().isBefore(LAST_YEAR_DAY)) {
                    double yOpen = ytdDayData.get(symbol).higherEntry(LAST_YEAR_DAY).getValue().getOpen();
                    double mOpen = ytdDayData.get(symbol).higherEntry(LAST_MONTH_DAY).getValue().getOpen();
                    LocalDate lastKey = ytdDayData.get(symbol).lastKey();
                    LocalDate secLastKey = ytdDayData.get(symbol).lowerKey(lastKey);

                    String info = "";
                    double yDev = Math.round((price / yOpen - 1) * 1000d) / 10d;
                    double mDev = Math.round((price / mOpen - 1) * 1000d) / 10d;

                    double pos = symbolPosMap.getOrDefault(symbol, 0.0);

                    if (pos < 0) {
                        if (yDev > 0 || mDev > 0) {
                            //cut short pos
                            info = "SHORT OFFSIDE";
                        }
                    } else if (pos > 0) {
                        if (yDev < 0 || mDev < 0) {
                            //cut long pos
                            info = "LONG OFFSIDE";
                        }
                    }

                    String out = str(symbol, pos, "||LIVE", tt, t.format(f2), price,
                            "PREV:", secLastKey.format(f)
                            , "||yOpen", ytdDayData.get(symbol).higherEntry(LAST_YEAR_DAY).getKey().format(f),
                            yOpen, "yDev", yDev + "%",
                            "||mOpen ", ytdDayData.get(symbol).higherEntry(LAST_MONTH_DAY).getKey().format(f), mOpen,
                            "mDev", mDev + "%", info);
                }
        }

    }

    @Override
    public void handleVol(TickType tt, String symbol, double vol, LocalDateTime t) {

    }

    @Override
    public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {

    }

    public static void main(String[] args) {

        BreachCutter cutter = new BreachCutter();
        cutter.connectAndReqHoldings();


    }
}
