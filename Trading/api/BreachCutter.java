package api;

import auxiliary.SimpleBar;
import client.*;
import controller.ApiConnection;
import controller.ApiController;
import handler.LiveHandler;
import utility.Utility;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static api.AutoTraderMain.*;
import static api.XuTraderHelper.*;
import static client.Types.TimeInForce.IOC;
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

    private volatile static Map<Contract, Double> contractPosMap =
            new TreeMap<>(Comparator.comparing(Utility::ibContractToSymbol));

    private volatile static Map<String, Double> symbolPosMap = new TreeMap<>(String::compareTo);

    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);

    public static Map<Currency, Double> fxMap = new HashMap<>();

    static File breachOutput = new File(TradingConstants.GLOBALPATH + "breachOrders.txt");
    //private static volatile NavigableMap<Integer, OrderAugmented> globalIdOrderMap = new ConcurrentSkipListMap<>();
    //private static volatile AtomicInteger autoTradeID = new AtomicInteger(100);

    private Map<String, Double> bidMap = new HashMap<>();
    private Map<String, Double> askMap = new HashMap<>();


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

        boolean firstPortWorked = false;

        try {
            ap.connect("127.0.0.1", 4001, 3, "");
            firstPortWorked = true;
            l.countDown();
            pr(" Latch counted down " + LocalTime.now());

        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ", ex);
        }

        if (!firstPortWorked) {
            pr(" using port 7496");
            ap.connect("127.0.0.1", 7496, 3, "");
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
            contractPosMap.put(contract, position);
            symbolPosMap.put(ibContractToSymbol(contract), position);
        }
    }

    @Override
    public void positionEnd() {
//        contractPosMap.entrySet().stream().forEachOrdered(e -> pr("symb pos ",
//                ibContractToSymbol(e.getKey()), e.getValue()));
        for (Contract c : contractPosMap.keySet()) {
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


    private static void ytdOpen(Contract c, String date, double open, double high, double low,
                                double close, int volume) {
        String symbol = utility.Utility.ibContractToSymbol(c);
        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            ytdDayData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        } else {
            double size = contractPosMap.getOrDefault(c, 0.0);
            if (ytdDayData.containsKey(symbol) && ytdDayData.get(symbol).size() > 0) {

                double yOpen = ytdDayData.get(symbol).ceilingEntry(LAST_YEAR_DAY).getValue().getClose();

                long yCount = ytdDayData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY)).count();

                double mOpen = ytdDayData.get(symbol).ceilingEntry(LAST_MONTH_DAY).getValue().getClose();

                long mCount = ytdDayData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY)).count();

                double last;
                last = ytdDayData.get(symbol).lastEntry().getValue().getClose();
                String info = "";
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
                }

                double delta = size * last * fxMap.getOrDefault(Currency.get(c.currency()), 1.0);

                String out = str(symbol, info, "Pos:" + Math.round(size),
                        "|||Last:",
                        ytdDayData.get(symbol).lastEntry().getKey().format(f), last, "||YYY",
                        ytdDayData.get(symbol).ceilingEntry(LAST_YEAR_DAY)
                                .getKey().format(f), yOpen,
                        "y#:" + yCount, "yUp%:",
                        Math.round(1000d * ytdDayData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
                                .filter(e -> e.getValue().getClose() > yOpen).count() / yCount) / 10d + "%",
                        "yDev", yDev + "%",
                        "||MMM ", ytdDayData.get(symbol).ceilingEntry(LAST_MONTH_DAY).getKey().format(f), mOpen,
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
        switch (tt) {
            case LAST:
                if (ytdDayData.get(symbol).size() > 0
                        && ytdDayData.get(symbol).firstKey().isBefore(LAST_YEAR_DAY)) {
                    double yearOpen = ytdDayData.get(symbol).ceilingEntry(LAST_YEAR_DAY).getValue().getClose();
                    double monthOpen = ytdDayData.get(symbol).ceilingEntry(LAST_MONTH_DAY).getValue().getClose();
                    LocalDate lastKey = ytdDayData.get(symbol).lastKey();

                    double yDev = Math.round((price / yearOpen - 1) * 1000d) / 10d;
                    double mDev = Math.round((price / monthOpen - 1) * 1000d) / 10d;


                    double pos = symbolPosMap.getOrDefault(symbol, 0.0);

                    pr("Cutter", t.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                            , symbol, price, "pos:", pos, "yDev:", yDev + "%" + "(" + yearOpen + ")"
                            , "mDev:", mDev + "%" + "(" + monthOpen + ")");

                    if (pos < 0) {
                        if (price >= monthOpen && askMap.getOrDefault(symbol, 0.0) != 0.0
                                && Math.abs(askMap.get(symbol) / price - 1) < 0.01) {
                            int id = autoTradeID.incrementAndGet();
                            Order o = placeBidLimitTIF(askMap.get(symbol), Math.abs(pos), IOC);
                            globalIdOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_CUTTER));
                            staticController.placeOrModifyOrder(ct, o,
                                    new ApiController.IOrderHandler.DefaultOrderHandler(id));
                            outputDetailedGen("*********", breachOutput);
                            outputDetailedGen(str("NEW", o.orderId(), "Breach Cutter BUY #:",
                                    globalIdOrderMap.get(id), "pos", pos), breachOutput);
                        }
                    } else if (pos > 0) {
                        if (price <= monthOpen && bidMap.getOrDefault(symbol, 0.0) != 0.0
                                && Math.abs(bidMap.get(symbol) / price - 1) < 0.01) {
                            int id = autoTradeID.incrementAndGet();
                            Order o = placeOfferLimitTIF(bidMap.get(symbol), pos, IOC);
                            globalIdOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_CUTTER));
                            staticController.placeOrModifyOrder(ct, o,
                                    new ApiController.IOrderHandler.DefaultOrderHandler(id));
                            outputDetailedGen("*********", breachOutput);
                            outputDetailedGen(str("NEW", o.orderId(), "Breach Cutter sell:"
                                    , globalIdOrderMap.get(id), "pos", pos), breachOutput);
                        }
                    }
                }
            case BID:
                bidMap.put(symbol, price);
            case ASK:
                askMap.put(symbol, price);
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
