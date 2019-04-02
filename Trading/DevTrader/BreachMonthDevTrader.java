package DevTrader;

import enums.Currency;
import api.TradingConstants;
import auxiliary.SimpleBar;
import client.*;
import controller.ApiConnection;
import controller.ApiController;
import handler.LiveHandler;
import utility.Utility;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static DevTrader.BreachDevTrader.devOrderMap;
import static DevTrader.BreachDevTrader.devTradeID;
import static client.Types.TimeInForce.*;
import static util.AutoOrderType.*;
import static utility.TradingUtility.*;
import static utility.Utility.*;

public class BreachMonthDevTrader implements LiveHandler, ApiController.IPositionHandler {
    private static DateTimeFormatter f = DateTimeFormatter.ofPattern("M-d H:mm:ss");
    private static final DateTimeFormatter f1 = DateTimeFormatter.ofPattern("M-d H:mm");
    private static final DateTimeFormatter f2 = DateTimeFormatter.ofPattern("M-d H:mm:s");

    private static double totalDelta = 0.0;
    private static ApiController apMonthDev;

    private static final LocalDate LAST_MONTH_DAY = Utility.getLastMonthLastDay();
    private static final LocalDate LAST_YEAR_DAY = getLastYearLastDay();

    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);
    private static File devOutput = new File(TradingConstants.GLOBALPATH + "breachMDev.txt");

    private static final double HI_LIMIT = 2000000.0;
    private static final double LO_LIMIT = -2000000.0;

    public static Map<Currency, Double> fx = new HashMap<>();
    private static Map<String, Double> multi = new HashMap<>();
    private static Map<String, Double> defaultSize = new HashMap<>();

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            mtdDayData = new ConcurrentSkipListMap<>(String::compareTo);

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDateTime, Double>>
            liveData = new ConcurrentSkipListMap<>();

    private volatile static Map<Contract, Double> contractPosMap =
            new ConcurrentSkipListMap<>(Comparator.comparing(Utility::ibContractToSymbol));

    private volatile static Map<String, Double> symbolPosMap = new ConcurrentSkipListMap<>(String::compareTo);

    private static Map<String, Double> bidMap = new ConcurrentHashMap<>();
    private static Map<String, Double> askMap = new ConcurrentHashMap<>();
    //private static volatile Map<String, AtomicBoolean> addingBlocked = new HashMap<>();
    //private static volatile Map<String, AtomicBoolean> cuttingBlocked = new HashMap<>();
    //private static volatile Map<String, AtomicBoolean> tradingBlocked = new HashMap<>();
    private static volatile Map<String, AtomicBoolean> addedMap = new ConcurrentHashMap<>();
    private static volatile Map<String, AtomicBoolean> liquidatedMap = new ConcurrentHashMap<>();

    public static double getLiveData(String symb) {
        if (liveData.containsKey(symb) && liveData.get(symb).size() > 0) {
            return liveData.get(symb).lastEntry().getValue();
        }
        return 0.0;
    }

    public static double getBid(String symb) {
        return bidMap.getOrDefault(symb, 0.0);
    }

    public static double getAsk(String symb) {
        return askMap.getOrDefault(symb, 0.0);
    }


    private BreachMonthDevTrader() {
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
                new FileInputStream(TradingConstants.GLOBALPATH + "defaultSize.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                defaultSize.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        Contract activeXIN50Fut = gettingActiveContract();
        registerContract(activeXIN50Fut);


        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "breachUSNames.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                registerContract(getUSStockContract(al1.get(0)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
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
        apMonthDev = ap;
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            pr(" using port 4001");
            ap.connect("127.0.0.1", 4001, 6, "");
            connectionStatus = true;
            l.countDown();
            pr(" Latch counted down 4001 " + LocalTime.now());
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ", ex);
        }

        if (!connectionStatus) {
            pr(" using port 7496");
            ap.connect("127.0.0.1", 7496, 6, "");
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


    private static void mtdOpen(Contract c, String date, double open, double high, double low,
                                double close, int volume) {
        String symbol = ibContractToSymbol(c);
        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            mtdDayData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        }
    }

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        if (!contract.symbol().equals("USD")) {
            registerContractPosition(contract, position);
        }
    }

    @Override
    public void positionEnd() {
        for (Contract c : contractPosMap.keySet()) {
            String symb = ibContractToSymbol(c);
            pr(" symbol in positionEnd ", symb);
            mtdDayData.put(symb, new ConcurrentSkipListMap<>());
            if (!symb.equals("USD")) {
                apMonthDev.reqHistDayData(ibStockReqId.addAndGet(5),
                        fillContract(c), BreachMonthDevTrader::mtdOpen, getCalendarYtdDays(), Types.BarSize._1_day);
            }
            apMonthDev.req1ContractLive(c, this, false);
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
        if (size != 0.0) {
            if (ct.secType() == Types.SecType.STK) {
                return price * size * fx;
            } else if (ct.secType() == Types.SecType.FUT) {
                if (multi.containsKey(ibContractToSymbol(ct))) {
                    return price * size * fx * multi.get(ibContractToSymbol(ct));
                } else {
                    throw new IllegalStateException(str("no multiplier", ibContractToSymbol(ct)));
                }
            }
        }
        return 0.0;
    }

    private static void breachAdder(Contract ct, double price, LocalDateTime t, double mOpen) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        double defaultS;
        if (defaultSize.containsKey(symbol)) {
            defaultS = defaultSize.get(symbol);
        } else {
            defaultS = getDefaultSize(ct);
        }
        double prevClose = getLastPriceFromMtd(ct);

        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

        if (!added && !liquidated && pos == 0.0 && prevClose != 0.0) {

            pr(t.format(f1), "breach adder", symbol, "pos", pos, "prevC", prevClose,
                    "price", price, "mOpen", mOpen);

            if (price > mOpen && totalDelta < HI_LIMIT) {
                if (bidMap.containsKey(symbol) && Math.abs(bidMap.get(symbol) / price - 1) < 0.01) {
                    addedMap.put(symbol, new AtomicBoolean(true));
                    int id = devTradeID.incrementAndGet();
                    Order o = placeBidLimitTIF(bidMap.get(symbol), defaultS, GTC);
                    devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_ADDER));
                    apMonthDev.placeOrModifyOrder(ct, o, new PatientDevHandler(id));
                    outputToSymbolFile(symbol, str("********", t), devOutput);
                    outputToSymbolFile(symbol, str(o.orderId(), "ADDER BUY:",
                            devOrderMap.get(id), "mOpen", mOpen,
                            "prevClose", prevClose, "price", price), devOutput);
                }
            } else if (price < mOpen && totalDelta > LO_LIMIT) {
                if (askMap.containsKey(symbol) && Math.abs(askMap.get(symbol) / price - 1) < 0.01) {
                    addedMap.put(symbol, new AtomicBoolean(true));
                    int id = devTradeID.incrementAndGet();
                    Order o = placeOfferLimitTIF(askMap.get(symbol), defaultS, GTC);
                    devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_ADDER));
                    apMonthDev.placeOrModifyOrder(ct, o, new PatientDevHandler(id));
                    outputToSymbolFile(symbol, str("********", t), devOutput);
                    outputToSymbolFile(symbol, str(o.orderId(), "ADDER SELL:",
                            devOrderMap.get(id), "mOpen", mOpen,
                            "prevClose", prevClose, "price", price), devOutput);
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

    private static void breachCutter(Contract ct, double price, LocalDateTime t, double mOpen) {
        String symbol = ibContractToSymbol(ct);
        double pos = symbolPosMap.get(symbol);
        boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
        boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

        if (!liquidated && pos != 0.0) {
            if (pos < 0.0 && price > mOpen) {
                if (bidMap.containsKey(symbol) && Math.abs(bidMap.get(symbol) / price - 1) < 0.01) {
                    liquidatedMap.put(symbol, new AtomicBoolean(true));
                    int id = devTradeID.incrementAndGet();
                    Order o = placeBidLimitTIF(bidMap.get(symbol), Math.abs(pos), IOC);
                    devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_CUTTER));
                    apMonthDev.placeOrModifyOrder(ct, o, new GuaranteeDevHandler(id, apMonthDev));
                    outputToSymbolFile(symbol, str("********", t), devOutput);
                    outputToSymbolFile(symbol, str(o.orderId(), "Cutter BUY:",
                            "added?" + added, devOrderMap.get(id), "pos", pos, "mOpen", mOpen,
                            "price", price), devOutput);
                }
            } else if (pos > 0.0 && price < mOpen) {
                if (askMap.containsKey(symbol) && Math.abs(askMap.get(symbol) / price - 1) < 0.01) {
                    liquidatedMap.put(symbol, new AtomicBoolean(true));
                    int id = devTradeID.incrementAndGet();
                    Order o = placeOfferLimitTIF(askMap.get(symbol), pos, IOC);
                    devOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_CUTTER));
                    apMonthDev.placeOrModifyOrder(ct, o, new GuaranteeDevHandler(id, apMonthDev));
                    outputToSymbolFile(symbol, str("********", t), devOutput);
                    outputToSymbolFile(symbol, str(o.orderId(), "Cutter SELL:",
                            "added?" + added, added ? "close cut" : "live cut",
                            devOrderMap.get(id), "pos", pos, "mOpen", mOpen, "price", price), devOutput);
                }
            }
        }
    }


    @Override
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        switch (tt) {
            case LAST:

                liveData.get(symbol).put(t, price);

                if (liveData.get(symbol).size() > 0 && mtdDayData.get(symbol).size() > 0
                        && mtdDayData.get(symbol).firstKey().isBefore(LAST_MONTH_DAY)) {

                    LocalDate mFirstDate = mtdDayData.get(symbol).ceilingEntry(LAST_MONTH_DAY).getKey();
                    double mOpen = mtdDayData.get(symbol).ceilingEntry(LAST_MONTH_DAY).getValue().getClose();
                    double pos = symbolPosMap.get(symbol);


                    if (timeIsOk(ct, t)) {
                        pr(" time is ok going into breach cutter adder ", symbol, price, t, mOpen);
                        //breachCutter(ct, price, t, mOpen);
                        //breachAdder(ct, price, t, mOpen);
                    }

                    double defaultS = defaultSize.getOrDefault(symbol, getDefaultSize(ct));

                    boolean added = addedMap.containsKey(symbol) && addedMap.get(symbol).get();
                    boolean liquidated = liquidatedMap.containsKey(symbol) && liquidatedMap.get(symbol).get();

                    double delta = getDelta(ct, price, pos, fx.getOrDefault(Currency.get(ct.currency()), 1.0));

                    String deltaDisplay = str(Math.round(1 / 1000d * getDelta(ct, price, pos,
                            fx.getOrDefault(Currency.get(ct.currency()), 1.0))));

                    if (liveData.containsKey(symbol) && liveData.get(symbol).size() > 0) {
                        pr(symbol, "POS:", pos, "added?" + added, "liq?" + liquidated, "Default:", defaultS,
                                "mOpen:" + mFirstDate + " " + mOpen
                                        + "(" + Math.round(1000d * (price / mOpen - 1)) / 10d + "%)",
                                "Last:", liveData.get(symbol).lastKey().format(f1) + " " + price
                                , pos != 0.0 ? ("Delta:" + deltaDisplay
                                        + "k " + (totalDelta != 0.0 ? "(" + Math.round(100d * delta / totalDelta)
                                        + "%)" : "")) : "");
                    }

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

    private static double getLastPriceFromMtd(Contract ct) {
        String symbol = ibContractToSymbol(ct);
        if (mtdDayData.containsKey(symbol) && mtdDayData.get(symbol).size() > 0) {
            return mtdDayData.get(symbol).lastEntry().getValue().getClose();
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
        BreachMonthDevTrader trader = new BreachMonthDevTrader();
        trader.connectAndReqPos();
        apMonthDev.cancelAllOrders();

        ScheduledExecutorService es = Executors.newScheduledThreadPool(10);
        es.scheduleAtFixedRate(() -> {
            totalDelta = contractPosMap.entrySet().stream().mapToDouble(e -> getDelta(e.getKey()
                    , getLastPriceFromMtd(e.getKey()), e.getValue(),
                    fx.getOrDefault(Currency.get(e.getKey().currency()), 1.0))).sum();
            pr(LocalDateTime.now().format(f),
                    "current total delta:", Math.round(totalDelta / 1000d) + "k");
        }, 0, 10, TimeUnit.SECONDS);
    }

}
