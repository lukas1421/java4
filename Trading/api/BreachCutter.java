package api;

import auxiliary.SimpleBar;
import client.*;
import controller.ApiConnection;
import controller.ApiController;
import handler.LiveHandler;
import utility.Utility;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static api.AutoTraderMain.*;
import static api.XuTraderHelper.*;
import static util.AutoOrderType.BREACH_CUTTER;
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
    private static volatile NavigableMap<Integer, OrderAugmented> globalIdOrderMap = new ConcurrentSkipListMap<>();
    private static volatile AtomicInteger autoTradeID = new AtomicInteger(100);


    BreachCutter() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "fx.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                fxMap.put(Currency.get(al1.get(0)), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

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

                double last;
                last = ytdDayData.get(symbol).lastEntry().getValue().getClose();
                String info;
                double yDev = Math.round((last / yOpen - 1) * 1000d) / 10d;
                double mDev = Math.round((last / mOpen - 1) * 1000d) / 10d;
                if (size > 0) {
                    if (yDev > 0 && mDev >= 0) {
                        info = "LONG ON/ON ";
                    } else if (yDev > 0 && mDev <= 0) {
                        info = "LONG ON/OFF";
                    } else if (yDev < 0 && mDev >= 0) {
                        info = "LONG OFF/ON";
                    } else {
                        info = "LONG OFF/OFF";
                    }
                } else if (size < 0) {
                    if (yDev < 0 && mDev <= 0) {
                        info = "SHORT ON/ON ";
                    } else if (yDev > 0 && mDev <= 0) {
                        info = "SHORT OFF/ON";
                    } else if (yDev < 0 && mDev >= 0) {
                        info = "SHORT ON/OFF";
                    } else {
                        info = "SHORT OFF/OFF ";
                    }
                } else {
                    info = str(yDev == 0.0 ? "yFlat" : (yDev < 0.0 ? "yDown" : "yUp"),
                            mDev == 0.0 ? "mFlat" : (mDev < 0.0 ? "mDown" : "mUp"));
                }

                double delta = size * last * fxMap.getOrDefault(Currency.get(c.currency()), 1.0);
//                pr("delta ", size, last,
//                        c.currency(), fxMap.getOrDefault(Currency.get(c.currency()), 1.0),
//                        Math.round(delta / 1000d), "k");

                String out = str(symbol, info, Math.round(size),
                        ytdDayData.get(symbol).lastEntry().getKey().format(f), last, "||yOpen",
                        ytdDayData.get(symbol).ceilingEntry(LAST_YEAR_DAY)
                                .getKey().format(f), yOpen,
                        "y#:" + yCount, "yUp%:",
                        Math.round(1000d * ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
                                .filter(e -> e.getValue().getClose() > yOpen).count() / yCount) / 10d + "%",
                        "yDev", yDev + "%",
                        "||mOpen ", ytdDayData.get(symbol).ceilingEntry(LAST_MONTH_DAY).getKey().format(f), mOpen,
                        "m#:" + mCount, "mUp%:",
                        Math.round(1000d * ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY))
                                .filter(e -> e.getValue().getClose() > mOpen).count() / mCount) / 10d + "%",
                        "mDev", mDev + "%");

                pr(LocalTime.now().truncatedTo(ChronoUnit.MINUTES), size != 0.0 ? "*" : ""
                        , out, Math.round(delta / 1000d) + "k");
            }

        }

    }

    @Override
    public void positionEnd() {
        holdingsMap.entrySet().stream().forEachOrdered(e -> pr("symb pos ",
                ibContractToSymbol(e.getKey()), e.getValue()));
        for (Contract c : holdingsMap.keySet()) {
            String k = ibContractToSymbol(c);
            pr("position end: ticker/symbol ", c.symbol(), k);
            ytdDayData.put(k, new ConcurrentSkipListMap<>());
            if (!k.equals("USD")) {
                staticController.reqHistDayData(ibStockReqId.addAndGet(5),
                        fillContract(c), BreachCutter::ytdOpen, getCalendarYtdDays(), Types.BarSize._1_day);
            }
            staticController.req1ContractLive(c, this, false);
        }

    }

    private static int getCalendarYtdDays() {
        return (int) ChronoUnit.DAYS.between(LAST_YEAR_DAY, LocalDate.now());
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
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        if (tt == TickType.LAST) {
            if (ytdDayData.get(symbol).size() > 0
                    && ytdDayData.get(symbol).firstKey().isBefore(LAST_YEAR_DAY)) {
                double yearOpen = ytdDayData.get(symbol).ceilingEntry(LAST_YEAR_DAY).getValue().getClose();
                double monthOpen = ytdDayData.get(symbol).ceilingEntry(LAST_MONTH_DAY).getValue().getClose();
                LocalDate lastKey = ytdDayData.get(symbol).lastKey();

                double yDev = Math.round((price / yearOpen - 1) * 1000d) / 10d;
                double mDev = Math.round((price / monthOpen - 1) * 1000d) / 10d;

                pr("Breach Cutter", symbol, price, t, yDev, mDev);

                double pos = symbolPosMap.getOrDefault(symbol, 0.0);

                if (pos < 0) {
                    if (yDev > 0 || mDev > 0) {
                        int id = autoTradeID.incrementAndGet();
                        Order o = placeBidLimitTIF(price, Math.abs(pos), Types.TimeInForce.DAY);
                        globalIdOrderMap.put(id, new OrderAugmented(symbol, t, o, BREACH_CUTTER));
                        staticController.placeOrModifyOrder(ct, o,
                                new ApiController.IOrderHandler.DefaultOrderHandler(id));
                        outputDetailedXU(symbol, "*********");
                        outputDetailedXU(symbol, str("NEW", o.orderId(), " Breach Cutter BUY #:",
                                globalIdOrderMap.get(id), "pos", pos));
                    }
                } else if (pos > 0) {
                    if (yDev < 0 || mDev < 0) {
                        int id = autoTradeID.incrementAndGet();
                        Order o = placeOfferLimitTIF(price, pos, Types.TimeInForce.DAY);
                        globalIdOrderMap.put(id, new OrderAugmented(symbol, t, o, BREACH_CUTTER));
                        staticController.placeOrModifyOrder(ct, o,
                                new ApiController.IOrderHandler.DefaultOrderHandler(id));
                        outputDetailedXU(symbol, "*********");
                        outputDetailedXU(symbol, str("NEW", o.orderId(), " Breach Cutter sell:"
                                , globalIdOrderMap.get(id), "pos", pos));
                    }
                }
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
