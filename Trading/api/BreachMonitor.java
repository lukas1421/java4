package api;

import auxiliary.SimpleBar;
import client.Contract;
import client.TickType;
import client.Types;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static utility.Utility.*;

public class BreachMonitor implements LiveHandler, ApiController.IPositionHandler {

    private static final DateTimeFormatter f = DateTimeFormatter.ofPattern("M-d");

    private static final DateTimeFormatter f2 = DateTimeFormatter.ofPattern("M-d H:mm:s");

    private static final LocalDate LAST_MONTH_DAY = getLastMonthLastDay();

    private static final LocalDate LAST_YEAR_DAY = getLastYearLastDay();

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            ytdDayData = new ConcurrentSkipListMap<>(String::compareTo);

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDateTime, Double>>
            liveData = new ConcurrentSkipListMap<>();

    private static ApiController staticController;

    private volatile static Map<Contract, Double> holdingsMap =
            new TreeMap<>(Comparator.comparing(Utility::ibContractToSymbol));

    private volatile static Map<String, Double> symbolPosMap = new TreeMap<>(String::compareTo);

    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);

    public static Map<Currency, Double> fxMap = new HashMap<>();


    private BreachMonitor() {
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

        Contract activeXIN50Fut = AutoTraderXU.gettingActiveContract();
        registerContract(activeXIN50Fut);

        Contract oil = getOilContract();
        registerContract(oil);

        Contract hk2828 = getGenericContract("2828", "SEHK", "HKD", Types.SecType.STK);
        registerContract(hk2828);

        Contract hk2800 = getGenericContract("2800", "SEHK", "HKD", Types.SecType.STK);
        registerContract(hk2800);

        Contract hk700 = getGenericContract("700", "SEHK", "HKD", Types.SecType.STK);
        registerContract(hk700);

        Contract hk27 = getGenericContract("27", "SEHK", "HKD", Types.SecType.STK);
        registerContract(hk27);

        Contract vix = getVIXContract();
        registerContract(vix);

        Contract spy = getUSStockContract("SPY");
        registerContract(spy);

        Contract qqq = getUSStockContract("QQQ");
        registerContract(qqq);

        Contract baba = getUSStockContract("BABA");
        registerContract(baba);

        Contract fb = getUSStockContract("FB");
        registerContract(fb);

        Contract jd = getUSStockContract("JD");
        registerContract(jd);

        Contract nflx = getUSStockContract("NFLX");
        registerContract(nflx);

        Contract aapl = getUSStockContract("AAPL");
        registerContract(aapl);
    }

    private void registerContract(Contract ct) {
        holdingsMap.put(ct, 0.0);
        symbolPosMap.put(ibContractToSymbol(ct), 0.0);
    }

    private static Contract getOilContract() {
        Contract ct = new Contract();
        ct.symbol("CL");
        ct.exchange("NYMEX");
        ct.currency("USD");
        ct.secType(Types.SecType.FUT);
        ct.lastTradeDateOrContractMonth("20190220");
        return ct;
    }

    private Contract getShanghaiConnectStock(String symb) {
        Contract ct = new Contract();
        ct.symbol(symb);
        ct.exchange("SEHKNTL");
        ct.currency("CNH");
        ct.secType(Types.SecType.STK);
        return ct;
    }


    private void getFromIB() {
        ApiController ap = new ApiController(new ApiController.IConnectionHandler.DefaultConnectionHandler(),
                new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
        staticController = ap;
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            pr(" using port 4001");
            ap.connect("127.0.0.1", 4001, 4, "");
            connectionStatus = true;
            //pr(" connection : status is true ");
            l.countDown();
            pr(" Latch counted down 4001 " + LocalTime.now());
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ", ex);
        }

        if (!connectionStatus) {
            pr(" using port 7496");
            ap.connect("127.0.0.1", 7496, 4, "");
            l.countDown();
            pr(" Latch counted down 7496" + LocalTime.now());
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
            symbolPosMap.put(ibContractToSymbol(contract), position);
        }
    }

    @Override
    public void positionEnd() {
        //pr(" holdings map ", holdingsMap);
//        holdingsMap.entrySet().stream().forEachOrdered((e)
//                -> pr("symb pos ", ibContractToSymbol(e.getKey()), e.getValue()));
        for (Contract c : holdingsMap.keySet()) {
            String k = ibContractToSymbol(c);
            //pr("position end: ticker/symbol ", c.symbol(), k);
            ytdDayData.put(k, new ConcurrentSkipListMap<>());
            if (!k.equals("USD")) {
                //if (!k.startsWith("sz") && !k.startsWith("sh") && !k.equals("USD")) {
                staticController.reqHistDayData(ibStockReqId.addAndGet(5),
                        fillContract(c), BreachMonitor::ytdOpen, getCalendarYtdDays(), Types.BarSize._1_day);
            }
            staticController.req1ContractLive(c, this, false);
        }
    }

    private static int getCalendarYtdDays() {
        return (int) ChronoUnit.DAYS.between(LAST_YEAR_DAY, LocalDate.now());
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
                double secLast;
                last = ytdDayData.get(symbol).lastEntry().getValue().getClose();
                secLast = ytdDayData.get(symbol).lowerEntry(ytdDayData.get(symbol)
                        .lastKey()).getValue().getClose();
                String info;
                double lastChg = Math.round((last / secLast - 1) * 1000d) / 10d;
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

                String out = str(symbol, info, "POS:" + (size != 0.0 ? size : 0.0),
                        "||LAST:", ytdDayData.get(symbol).lastEntry().getKey().format(f), last,
                        lastChg + "%", "||YYY", "Up%:" +
                                Math.round(100d * ytdDayData.get(symbol).entrySet().stream()
                                        .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
                                        .filter(e -> e.getValue().getClose() > yOpen).count() / yCount) + "%",
                        "yDev:" + yDev + "%" + "(" + ytdDayData.get(symbol).ceilingEntry(LAST_YEAR_DAY)
                                .getKey().format(f) + " " + yOpen + ")",
                        "||MMM", "Up%:" +
                                Math.round(100d * ytdDayData.get(symbol).entrySet().stream()
                                        .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY))
                                        .filter(e -> e.getValue().getClose() > mOpen).count() / mCount) + "%",
                        "mDev:" + mDev + "%" + "(" +
                                ytdDayData.get(symbol)
                                        .ceilingEntry(LAST_MONTH_DAY).getKey().format(f) + " " + mOpen + ")");

                pr(LocalTime.now().truncatedTo(ChronoUnit.MINUTES), size != 0.0 ? "*" : ""
                        , out, Math.round(delta / 1000d) + "k");
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
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        //pr("last symbol ", tt, symbol, price, t.format(f2));
        switch (tt) {
            case LAST:
                if (!liveData.containsKey(symbol)) {
                    liveData.put(symbol, new ConcurrentSkipListMap<>());
                }
                liveData.get(symbol).put(t, price);

                if (ytdDayData.get(symbol).size() > 0
                        && ytdDayData.get(symbol).firstKey().isBefore(LAST_YEAR_DAY)) {
                    double yOpen = ytdDayData.get(symbol).higherEntry(LAST_YEAR_DAY).getValue().getOpen();
                    double mOpen = ytdDayData.get(symbol).higherEntry(LAST_MONTH_DAY).getValue().getOpen();
                    double last;
                    double secLast;
                    LocalDate lastKey = ytdDayData.get(symbol).lastKey();
                    LocalDate secLastKey = ytdDayData.get(symbol).lowerKey(lastKey);

                    last = ytdDayData.get(symbol).lastEntry().getValue().getClose();
                    secLast = ytdDayData.get(symbol).lowerEntry(lastKey).getValue().getClose();

                    String info = "";
                    double lastChg = Math.round((price / secLast - 1) * 1000d) / 10d;
                    double yDev = Math.round((price / yOpen - 1) * 1000d) / 10d;
                    double mDev = Math.round((price / mOpen - 1) * 1000d) / 10d;

                    String yBreachStatus = "";
                    String mBreachStatus = "";

                    if (secLast > yOpen && price < yOpen) {
                        yBreachStatus = "y DOWN";
                    } else if (secLast < yOpen && price > yOpen) {
                        yBreachStatus = "y UP";
                    }

                    if (secLast > mOpen && price < mOpen) {
                        mBreachStatus = "m DOWN";
                    } else if (secLast < mOpen && price > mOpen) {
                        mBreachStatus = "m UP";
                    }

                    double pos = symbolPosMap.getOrDefault(symbol, 0.0);
                    if (pos < 0) {
                        if (yDev > 0 || mDev > 0) {
                            info = "SHORT OFFSIDE";
                        }
                    } else if (pos > 0) {
                        if (yDev < 0 || mDev < 0) {
                            info = "LONG OFFSIDE";
                        }
                    }

                    String out = str(symbol, pos, "||LIVE", tt, t.format(f2), price
                            , "CHG%:", lastChg + "%", "PREV:", secLastKey.format(f), secLast
                            , "||yOpen", ytdDayData.get(symbol).higherEntry(LAST_YEAR_DAY).getKey().format(f),
                            yOpen, "yDev", yDev + "%",
                            "||mOpen ", ytdDayData.get(symbol).higherEntry(LAST_MONTH_DAY).getKey().format(f), mOpen,
                            "mDev", mDev + "%", info);
                    //pr("*", out, yBreachStatus, mBreachStatus);
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

        ScheduledExecutorService es = Executors.newScheduledThreadPool(10);
        es.scheduleAtFixedRate(() -> {
            pr("**********************************************");
            pr("running @ ", LocalTime.now());
            bm.reqHoldings(staticController);
        }, 1, 1, TimeUnit.MINUTES);
    }
}
