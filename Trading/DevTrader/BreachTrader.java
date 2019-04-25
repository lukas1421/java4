package DevTrader;

import enums.Currency;
import api.TradingConstants;
import auxiliary.SimpleBar;
import client.*;
import controller.ApiConnection;
import controller.ApiController;
import handler.LiveHandler;
import utility.TradingUtility;
import utility.Utility;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static client.Types.TimeInForce.*;
import static util.AutoOrderType.*;
import static utility.TradingUtility.*;
import static utility.Utility.*;

public class BreachTrader implements LiveHandler, ApiController.IPositionHandler {

    static final int MAX_ATTEMPTS = 100;
    static volatile NavigableMap<Integer, OrderAugmented> devOrderMap = new ConcurrentSkipListMap<>();
    static volatile AtomicInteger devTradeID = new AtomicInteger(100);

    private static DateTimeFormatter f = DateTimeFormatter.ofPattern("M-d H:mm:ss");
    private static final DateTimeFormatter f1 = DateTimeFormatter.ofPattern("M-d H:mm");
    public static final DateTimeFormatter f2 = DateTimeFormatter.ofPattern("M-d H:mm:s.SSS");

    private static final int MAX_CROSS_PER_MONTH = 10;

    private static double totalDelta = 0.0;
    private static double totalAbsDelta = 0.0;
    private static ApiController apDev;

    private static final LocalDate LAST_MONTH_DAY = getLastMonthLastDay();
    private static final LocalDate LAST_YEAR_DAY = getLastYearLastDay();

    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);
    private static File devOutput = new File(TradingConstants.GLOBALPATH + "breachMDev.txt");

    private static final double HI_LIMIT = 4000000.0;
    private static final double LO_LIMIT = -4000000.0;
    //private static final double ABS_LIMIT = 5000000.0;

    public static Map<Currency, Double> fx = new HashMap<>();
    private static Map<String, Double> multi = new HashMap<>();
    private static Map<String, Double> defaultSize = new HashMap<>();

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            ytdDayData = new ConcurrentSkipListMap<>(String::compareTo);

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDateTime, Double>>
            liveData = new ConcurrentSkipListMap<>();

    private volatile static Map<Contract, Double> contractPosMap =
            new ConcurrentSkipListMap<>(Comparator.comparing(Utility::ibContractToSymbol));

    private volatile static Map<String, Double> symbolPosMap = new ConcurrentSkipListMap<>(String::compareTo);

    private static Map<String, Double> lastMap = new ConcurrentHashMap<>();
    private static Map<String, Double> bidMap = new ConcurrentHashMap<>();
    private static Map<String, Double> askMap = new ConcurrentHashMap<>();
    private static volatile Map<String, AtomicBoolean> addedMap = new ConcurrentHashMap<>();
    private static volatile Map<String, AtomicBoolean> liquidatedMap = new ConcurrentHashMap<>();

    static double getLast(String symb) {
        return lastMap.getOrDefault(symb, 0.0);
    }

    static double getBid(String symb) {
        return bidMap.getOrDefault(symb, 0.0);
    }

    static double getAsk(String symb) {
        return askMap.getOrDefault(symb, 0.0);
    }

    private static Semaphore histSemaphore = new Semaphore(45);

    private BreachTrader() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "fx.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                fx.put(Currency.get(al1.get(0)), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "multiplier.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                multi.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "defaultNonUSSize.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                defaultSize.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        registerContract(getActiveA50Contract());
        registerContract(getActiveBTCContract());

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "breachUSNames.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                registerContract(getUSStockContract(al1.get(0)));
                defaultSize.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    static double getDefaultSize(String symb) {
        if (defaultSize.containsKey(symb)) {
            return defaultSize.get(symb);
        }
        outputToSpecial(str(LocalDateTime.now(), symb, "no default size "));
        return 0.0;
    }

    private static void registerContract(Contract ct) {
        contractPosMap.put(ct, 0.0);
        symbolPosMap.put(ibContractToSymbol(ct), 0.0);
        String symbol = ibContractToSymbol(ct);
        if (!liveData.containsKey(symbol)) {
            liveData.put(symbol, new ConcurrentSkipListMap<>());
        }
    }

    private static void registerContractPosition(Contract ct, double pos) {
        contractPosMap.put(ct, pos);
        symbolPosMap.put(ibContractToSymbol(ct), pos);
        String symbol = ibContractToSymbol(ct);
        if (!liveData.containsKey(symbol)) {
            liveData.put(ibContractToSymbol(ct), new ConcurrentSkipListMap<>());
        }
    }


    public void connectAndReqPos() {
        ApiController ap = new ApiController(new ApiController.IConnectionHandler.DefaultConnectionHandler(),
                new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
        apDev = ap;
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            pr(" using port 4001");
            ap.connect("127.0.0.1", 4001, 5, "");
            connectionStatus = true;
            l.countDown();
            pr(" Latch counted down 4001 " + LocalTime.now());
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ", ex);
        }

        if (!connectionStatus) {
            pr(" using port 7496");
            ap.connect("127.0.0.1", 7496, 5, "");
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
        ap.reqPositions(this);
    }


    private static void ytdOpen(Contract c, String date, double open, double high, double low,
                                double close, int volume) {
        String symbol = utility.Utility.ibContractToSymbol(c);
        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            ytdDayData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        } else {
            histSemaphore.release(1);
        }
    }

    static double getLivePos(String symb) {
        if (symbolPosMap.containsKey(symb)) {
            return symbolPosMap.get(symb);
        }
        outputToSpecial(str(LocalTime.now(), " get live pos from breach dev not found ", symb));
        return 0.0;
    }

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        String symbol = ibContractToSymbol(contract);
        if (!contract.symbol().equals("USD") &&
                (position != 0 || symbolPosMap.getOrDefault(symbol, 0.0) != 0.0)) {
            registerContractPosition(contract, position);
        }
    }

    @Override
    public void positionEnd() {
        for (Contract c : contractPosMap.keySet()) {
            String symb = ibContractToSymbol(c);
            pr(" symbol in positionEnd ", symb);
            ytdDayData.put(symb, new ConcurrentSkipListMap<>());

            if (!symb.equals("USD")) {
                CompletableFuture.runAsync(() -> {
                    try {
                        histSemaphore.acquire();
                        apDev.reqHistDayData(ibStockReqId.addAndGet(5),
                                histCompatibleCt(c), BreachTrader::ytdOpen,
                                getCalendarYtdDays() + 10, Types.BarSize._1_day);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }
            apDev.req1ContractLive(c, this, false);
        }
    }

    private static int getCalendarYtdDays() {
        return (int) ChronoUnit.DAYS.between(LAST_YEAR_DAY, LocalDate.now());
    }

    private static double getDefaultSize(Contract ct) {
        if (ct.secType() == Types.SecType.FUT) {
            return 1;
        } else if (ct.secType() == Types.SecType.STK && ct.currency().equalsIgnoreCase("USD")) {
            return 100.0;
        }
        throw new IllegalStateException(str(ibContractToSymbol(ct), " no default size "));
    }

    private static double getDelta(Contract ct, double price, double size, double fx) {

        if (ct.secType() == Types.SecType.STK) {
            return price * size * fx;
        } else if (ct.secType() == Types.SecType.FUT) {
            if (multi.containsKey(ibContractToSymbol(ct))) {
                return price * size * fx * multi.get(ibContractToSymbol(ct));
            } else {
                outputToSymbolFile(ibContractToSymbol(ct), str("no multi", ibContractToSymbol(ct),
                        price, size, fx), devOutput);
                throw new IllegalStateException(str("no multiplier", ibContractToSymbol(ct)));
            }
        }
        outputToSymbolFile(ibContractToSymbol(ct), str(" cannot get delta for symbol type"
                , ct.symbol(), ct.secType()), devOutput);
        throw new IllegalStateException(str(" cannot get delta for symbol type", ct.symbol(), ct.secType()));
    }

    private static void breachAdder(Contract ct, double price, LocalDateTime t, double yOpen, double mOpen) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        double defaultS;
        if (defaultSize.containsKey(symbol)) {
            defaultS = defaultSize.get(symbol);
        } else {
            defaultS = getDefaultSize(ct);
        }
        double prevClose = getLastPriceFromYtd(ct);

        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

        long numCrosses = ytdDayData.get(symbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY))
                .filter(e -> e.getValue().includes(mOpen))
                .count();


        if (numCrosses >= MAX_CROSS_PER_MONTH) {
            outputToSymbolFile(symbol, str(symbol, numCrosses, "exceeding numCrosses no trade "
                    , t.format(f1)), devOutput);
        }

        if (!added && !liquidated && pos == 0.0 && prevClose != 0.0 && numCrosses < MAX_CROSS_PER_MONTH) {

//            pr(t.format(f1), "breach adder", symbol, "pos", pos, "prevC", prevClose,
//                    "price", price, "yOpen", yOpen, "mOpen", mOpen, "devFromMaxOpen",
//                    r10000(price / Math.max(yOpen, mOpen) - 1), "devFromMin",
//                    r10000(price / Math.min(yOpen, mOpen) - 1));

            if (price > yOpen && price > mOpen && totalDelta < HI_LIMIT) {
                //&& ((price / Math.max(yOpen, mOpen) - 1) < 0.02)) {
                addedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(bidMap.getOrDefault(symbol, price), defaultS, DAY);
                if (checkDeltaImpact(ct, o)) {
                    devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_ADDER));
                    apDev.placeOrModifyOrder(ct, o, new PatientDevHandler(id));
                    outputToSymbolFile(symbol, str("********", t.format(f1)), devOutput);
                    outputToSymbolFile(symbol, str(o.orderId(), id, "ADDER BUY:",
                            devOrderMap.get(id), "yOpen", yOpen, "mOpen", mOpen,
                            "prevClose", prevClose, "price", price, "devFromMaxOpen",
                            r10000(price / Math.max(yOpen, mOpen) - 1))
                            , devOutput);
                }
            } else if (price < yOpen && price < mOpen && totalDelta > LO_LIMIT) {
                // && (price / Math.min(yOpen, mOpen) - 1) > -0.02) {
                addedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(askMap.getOrDefault(symbol, price), defaultS, DAY);

                if (checkDeltaImpact(ct, o)) {
                    devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_ADDER));
                    apDev.placeOrModifyOrder(ct, o, new PatientDevHandler(id));
                    outputToSymbolFile(symbol, str("********", t.format(f1)), devOutput);
                    outputToSymbolFile(symbol, str(o.orderId(), id, "ADDER SELL:",
                            devOrderMap.get(id), "yOpen", yOpen, "mOpen", mOpen,
                            "prevClose", prevClose, "price", price, "devFromMinOpen",
                            r10000(price / Math.min(mOpen, yOpen) - 1)), devOutput);
                }
            }
        }
    }

    private static boolean timeIsOk(Contract ct, LocalDateTime chinaTime) {
        if (ct.currency().equalsIgnoreCase("USD") && ct.secType() == Types.SecType.STK) {
            ZonedDateTime chinaZdt = ZonedDateTime.of(chinaTime, chinaZone);
            ZonedDateTime usZdt = chinaZdt.withZoneSameInstant(nyZone);
            LocalTime usLt = usZdt.toLocalDateTime().toLocalTime();

            return ltBtwn(usLt, 9, 29, 16, 1);
        } else if (ct.currency().equalsIgnoreCase("HKD") && ct.secType() == Types.SecType.STK) {
            return ltBtwn(chinaTime.toLocalTime(), 9, 29, 16, 1);
        }
        return true;
    }


    private static void breachCutter(Contract ct, double price, LocalDateTime t, double yOpen, double mOpen) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

        if (!liquidated && pos != 0.0) {
            if (pos < 0.0 && (price > mOpen || price > yOpen)) {
                checkIfAdderPending(symbol);
                liquidatedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(bidMap.getOrDefault(symbol, price), Math.abs(pos), IOC);
                devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_CUTTER));
                apDev.placeOrModifyOrder(ct, o, new GuaranteeDevHandler(id, apDev));
                outputToSymbolFile(symbol, str("********", t), devOutput);
                outputToSymbolFile(symbol, str(o.orderId(), id, "Cutter BUY:",
                        "added?" + added, devOrderMap.get(id), "pos", pos, "yOpen", yOpen, "mOpen", mOpen,
                        "price", price), devOutput);
            } else if (pos > 0.0 && (price < mOpen || price < yOpen)) {
                checkIfAdderPending(symbol);
                liquidatedMap.put(symbol, new AtomicBoolean(true));
                int id = devTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(askMap.getOrDefault(symbol, price), pos, IOC);
                devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_CUTTER));
                apDev.placeOrModifyOrder(ct, o, new GuaranteeDevHandler(id, apDev));
                outputToSymbolFile(symbol, str("********", t), devOutput);
                outputToSymbolFile(symbol, str(o.orderId(), id, "Cutter SELL:",
                        "added?" + added, devOrderMap.get(id), "pos", pos, "yOpen", yOpen, "mOpen", mOpen,
                        "price", price), devOutput);
            }
        }
    }


    private static void checkIfAdderPending(String symbol) {
        devOrderMap.entrySet().stream().filter(e -> e.getValue().getSymbol().equalsIgnoreCase(symbol))
                .filter(e -> e.getValue().getOrderType() == BREACH_ADDER)
                .filter(e -> e.getValue().getAugmentedOrderStatus() == OrderStatus.Submitted)
                .forEach(e -> {
                    outputToSymbolFile(symbol, str("Cancel submitted before cutting"
                            , e.getValue()), devOutput);
                    apDev.cancelOrder(e.getValue().getOrder().orderId());
                });
    }


    private static boolean checkDeltaImpact(Contract ct, Order o) {
        double totalQ = o.totalQuantity();
        double lmtPrice = o.lmtPrice();
        double xxxCny = fx.getOrDefault(Currency.get(ct.currency()), 1.0);
        String symbol = ibContractToSymbol(ct);

        double impact = getDelta(ct, lmtPrice, totalQ, xxxCny);
        if (Math.abs(impact) > 300000) {
            TradingUtility.outputToError(str("IMPACT TOO BIG", impact, ct.symbol(), o.action(),
                    o.lmtPrice(), o.totalQuantity()));
            outputToSymbolFile(symbol, str("IMPACT TOO BIG ", impact), devOutput);
            return false;
        } else {
            outputToSymbolFile(symbol, str("delta impact check PASSED ", Math.round(impact)), devOutput);
            return true;
        }
    }


    @Override
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        switch (tt) {
            case LAST:
                liveData.get(symbol).put(t, price);
                lastMap.put(symbol, price);

//                if (symbol.equalsIgnoreCase("GXBT")) {
//                    pr("handle price last ", symbol, t, price, ytdDayData.get(symbol));
//                }

//                if (ytdDayData.containsKey(symbol) && ytdDayData.get(symbol).size() > 0) {
//                    pr(symbol, t, price, "First", ytdDayData.get(symbol).firstKey(),
//                            ytdDayData.get(symbol).firstEntry().getValue().getClose(),
//                            "mFirst", ytdDayData.get(symbol).floorEntry(LAST_MONTH_DAY).getKey(),
//                            ytdDayData.get(symbol).floorEntry(LAST_MONTH_DAY).getValue().getClose());
//                }

                if (liveData.get(symbol).size() > 0 && ytdDayData.get(symbol).size() > 0
                        && ytdDayData.get(symbol).firstKey().isBefore(LAST_YEAR_DAY)) {

                    LocalDate yStartDate = ytdDayData.get(symbol).floorEntry(LAST_YEAR_DAY).getKey();
                    double yStart = ytdDayData.get(symbol).floorEntry(LAST_YEAR_DAY).getValue().getClose();
                    LocalDate mStartDate = ytdDayData.get(symbol).floorEntry(LAST_MONTH_DAY).getKey();
                    double mStart = ytdDayData.get(symbol).floorEntry(LAST_MONTH_DAY).getValue().getClose();
                    double pos = symbolPosMap.get(symbol);
                    long numCrosses = ytdDayData.get(symbol).entrySet().stream()
                            .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY))
                            .filter(e -> e.getValue().includes(mStart))
                            .count();


                    if (timeIsOk(ct, t)) {
                        breachCutter(ct, price, t, yStart, mStart);
                        breachAdder(ct, price, t, yStart, mStart);
                    }

                    double defaultS = defaultSize.getOrDefault(symbol, getDefaultSize(ct));

                    boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
                    boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

                    double delta = getDelta(ct, price, pos, fx.getOrDefault(Currency.get(ct.currency()), 1.0));

                    String deltaDisplay = str(Math.round(1 / 1000d * getDelta(ct, price, pos,
                            fx.getOrDefault(Currency.get(ct.currency()), 1.0))));
//                    if (liveData.containsKey(symbol) && liveData.get(symbol).size() > 0) {
//                        //pr(symbol, liveData.get(symbol));
//                        pr(symbol, "POS:", pos, "added?" + added, "liq?" + liquidated, "Default:", defaultS,
//                                "yStart:" + yStartDate + " " + yStart
//                                        + "(" + Math.round(1000d * (price / yStart - 1)) / 10d + "%)"
//                                , "mStart:" + mStartDate + " " + mStart
//                                        + "(" + Math.round(1000d * (price / mStart - 1)) / 10d + "%)",
//                                "Last:", liveData.get(symbol).lastKey().format(f1) + " " + price
//                                , pos != 0.0 ? ("Delta:" + deltaDisplay
//                                        + "k " + (totalDelta != 0.0 ? "(" + Math.round(100d * delta / totalDelta)
//                                        + "%)" : "")) : "");
//                    }
                }
                break;
            case BID:
                bidMap.put(symbol, price);
                break;
            case ASK:
                askMap.put(symbol, price);
                break;
        }
    }

    private static double getLastPriceFromYtd(Contract ct) {
        String symbol = ibContractToSymbol(ct);
        if (ytdDayData.containsKey(symbol) && ytdDayData.get(symbol).size() > 0) {
            return ytdDayData.get(symbol).lastEntry().getValue().getClose();
        }
        return 0.0;
    }

    @Override
    public void handleVol(TickType tt, String symbol, double vol, LocalDateTime t) {

    }

    @Override
    public void handleGeneric(TickType tt, String symbol, double value, LocalDateTime t) {

    }


    public static void main(String[] args) {
        BreachTrader trader = new BreachTrader();
        trader.connectAndReqPos();
        apDev.cancelAllOrders();

        ScheduledExecutorService es = Executors.newScheduledThreadPool(10);
        es.scheduleAtFixedRate(() -> {
            totalDelta = contractPosMap.entrySet().stream().mapToDouble(e -> getDelta(e.getKey()
                    , getLastPriceFromYtd(e.getKey()), e.getValue(),
                    fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0))).sum();
            totalAbsDelta = contractPosMap.entrySet().stream().mapToDouble(e ->
                    Math.abs(getDelta(e.getKey(), getLastPriceFromYtd(e.getKey()), e.getValue(),
                            fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0)))).sum();
            double longDelta = contractPosMap.entrySet().stream().mapToDouble(e ->
                    Math.max(0, getDelta(e.getKey(), getLastPriceFromYtd(e.getKey()), e.getValue(),
                            fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0)))).sum();

            double shortDelta = contractPosMap.entrySet().stream().mapToDouble(e ->
                    Math.min(0, getDelta(e.getKey(), getLastPriceFromYtd(e.getKey()), e.getValue(),
                            fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0)))).sum();

            pr(LocalDateTime.now().format(f),
                    "||net delta:" + Math.round(totalDelta / 1000d) + "k",
                    "||abs delta:" + Math.round(totalAbsDelta / 1000d) + "k",
                    "||long/short:", Math.round(longDelta / 1000d) + "k",
                    Math.round(shortDelta / 1000d) + "k");
        }, 0, 1, TimeUnit.MINUTES);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            pr("closing hook ");
            devOrderMap.forEach((k, v) -> {
                if (v.getAugmentedOrderStatus() != OrderStatus.Filled &&
                        v.getAugmentedOrderStatus() != OrderStatus.PendingCancel) {
                    outputToSymbolFile(v.getSymbol(), str("Shutdown status",
                            LocalDateTime.now().format(f1), v.getAugmentedOrderStatus(), v), devOutput);
                }
            });
            apDev.cancelAllOrders();
        }));
    }
}

