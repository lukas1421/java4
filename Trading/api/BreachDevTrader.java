package api;

import auxiliary.SimpleBar;
import client.*;
import controller.ApiConnection;
import controller.ApiController;
import handler.GuaranteeDevHandler;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static api.AutoTraderMain.autoTradeID;
import static api.AutoTraderMain.globalIdOrderMap;
import static api.XuTraderHelper.*;
import static client.Types.TimeInForce.IOC;
import static util.AutoOrderType.BREACH_MDEV;
import static utility.Utility.*;

public class BreachDevTrader implements LiveHandler, ApiController.IPositionHandler {

    private static ApiController staticController;
    private static final DateTimeFormatter f1 = DateTimeFormatter.ofPattern("M-d H:mm");
    private static final DateTimeFormatter f2 = DateTimeFormatter.ofPattern("M-d H:mm:s");
    private static final LocalDate LAST_MONTH_DAY = getLastMonthLastDay();
    private static final LocalDate LAST_YEAR_DAY = getLastYearLastDay();

    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);
    private static File breachMDevOutput = new File(TradingConstants.GLOBALPATH + "breachMDev.txt");

    private static final double DELTA_LIMIT = 200000.0;
    public static Map<Currency, Double> fxMap = new HashMap<>();
    public static Map<String, Double> defaultSizeMap = new HashMap<>();

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            ytdDayData = new ConcurrentSkipListMap<>(String::compareTo);

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDateTime, Double>>
            liveData = new ConcurrentSkipListMap<>();

    private volatile static Map<Contract, Double> contractPosMap =
            new TreeMap<>(Comparator.comparing(Utility::ibContractToSymbol));

    private volatile static Map<String, Double> symbolPosMap = new TreeMap<>(String::compareTo);

    private static Map<String, Double> bidMap = new HashMap<>();
    private static Map<String, Double> askMap = new HashMap<>();
    private static volatile Map<String, AtomicBoolean> orderBlocked = new HashMap<>();


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


    private BreachDevTrader() {
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

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "defaultSize.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                defaultSizeMap.put(al1.get(0), Double.parseDouble(al1.get(1)));
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

        Contract activeXIN50Fut = AutoTraderXU.gettingActiveContract();
        registerContract(activeXIN50Fut);

        Contract hk2828 = getGenericContract("2828", "SEHK", "HKD", Types.SecType.STK);
        registerContract(hk2828);

        Contract hk2800 = getGenericContract("2800", "SEHK", "HKD", Types.SecType.STK);
        registerContract(hk2800);

        Contract hk700 = getGenericContract("700", "SEHK", "HKD", Types.SecType.STK);
        registerContract(hk700);

        Contract hk27 = getGenericContract("27", "SEHK", "HKD", Types.SecType.STK);
        registerContract(hk27);

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

        Contract pdd = getUSStockContract("PDD");
        registerContract(pdd);


    }

    private static void registerContract(Contract ct) {
        contractPosMap.put(ct, 0.0);
        symbolPosMap.put(ibContractToSymbol(ct), 0.0);
        liveData.put(ibContractToSymbol(ct), new ConcurrentSkipListMap<>());
        orderBlocked.put(ibContractToSymbol(ct), new AtomicBoolean(false));
    }

    private static void registerContractPosition(Contract ct, double pos) {
        contractPosMap.put(ct, pos);
        symbolPosMap.put(ibContractToSymbol(ct), pos);
        liveData.put(ibContractToSymbol(ct), new ConcurrentSkipListMap<>());
        orderBlocked.put(ibContractToSymbol(ct), new AtomicBoolean(false));
    }


    public void connectAndReqPos() {
        ApiController ap = new ApiController(new ApiController.IConnectionHandler.DefaultConnectionHandler(),
                new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
        staticController = ap;
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
            String k = ibContractToSymbol(c);
            pr(" symbol in pos ", k);
            ytdDayData.put(k, new ConcurrentSkipListMap<>());
            if (!k.equals("USD")) {
                staticController.reqHistDayData(ibStockReqId.addAndGet(5),
                        fillContract(c), BreachDevTrader::ytdOpen, getCalendarYtdDays(), Types.BarSize._1_day);
            }
            staticController.req1ContractLive(c, this, false);
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
        return 0.0;
    }


    @Override
    public void handlePrice(TickType tt, Contract ct, double price, LocalDateTime t) {
        String symbol = ibContractToSymbol(ct);
        switch (tt) {
            case LAST:
                liveData.get(symbol).put(t, price);

                if (liveData.get(symbol).size() > 0 && ytdDayData.get(symbol).size() > 0
                        && ytdDayData.get(symbol).firstKey().isBefore(LAST_YEAR_DAY)) {

                    double yOpen = ytdDayData.get(symbol).ceilingEntry(LAST_YEAR_DAY).getValue().getClose();
                    double mOpen = ytdDayData.get(symbol).ceilingEntry(LAST_MONTH_DAY).getValue().getClose();
                    double pos = symbolPosMap.get(symbol);

                    double firstValue = liveData.get(symbol).firstEntry().getValue();
                    double defaultS = defaultSizeMap.getOrDefault(symbol, getDefaultSize(ct));
                    boolean orderBlockStatus = orderBlocked.get(symbol).get();

                    pr("Dev", symbol, pos, "block?" + orderBlockStatus,
                            "Default:", defaultS, "yOpen:" + yOpen
                                    + "(" + Math.round(1000d * (price / yOpen - 1)) / 10d + "%)"
                            , "mOpen:" + mOpen,
                            "FV:", liveData.get(symbol).firstKey().format(f1) + " " + liveData.get(symbol).firstEntry().getValue() + "(" +
                                    Math.round(1000d * (liveData.get(symbol).firstEntry().getValue() / mOpen - 1)) / 10d + "%)",
                            "LV:", liveData.get(symbol).lastKey().format(f1) + " " + price + "(" +
                                    Math.round(1000d * (price / mOpen - 1)) / 10d + "%)");

                    if (!orderBlocked.get(symbol).get()) {
                        if (firstValue < mOpen && price > mOpen) {
                            if (pos <= 0 && (pos < 0 || price > yOpen)) {
                                if (askMap.getOrDefault(symbol, 0.0) != 0.0
                                        && Math.abs(askMap.get(symbol) / price - 1) < 0.01) {
                                    orderBlocked.get(symbol).set(true);
                                    double size = Math.abs(pos) + (price > yOpen ? defaultS : 0);
                                    int id = autoTradeID.incrementAndGet();
                                    Order o = placeBidLimitTIF(askMap.get(symbol), size, IOC);
                                    globalIdOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_MDEV));
                                    staticController.placeOrModifyOrder(ct, o,
                                            new GuaranteeDevHandler(id, staticController));
                                    outputDetailedGen("*********", breachMDevOutput);
                                    outputDetailedGen(str("NEW", o.orderId(), "Breach MDEV BUY:",
                                            globalIdOrderMap.get(id), "pos", pos), breachMDevOutput);
                                } else if (firstValue > mOpen && price < mOpen) {
                                    if (pos >= 0 && (pos > 0 || price < yOpen)) {
                                        if (bidMap.getOrDefault(symbol, 0.0) != 0.0
                                                && Math.abs(bidMap.get(symbol) / price - 1) < 0.01) {
                                            orderBlocked.get(symbol).set(true);
                                            double size = Math.round(pos) + (price < yOpen ? defaultS : 0);
                                            int id = autoTradeID.incrementAndGet();
                                            Order o = placeOfferLimitTIF(bidMap.get(symbol), size, IOC);
                                            globalIdOrderMap.put(id, new OrderAugmented(ct, t, o, BREACH_MDEV));
                                            staticController.placeOrModifyOrder(ct, o,
                                                    new GuaranteeDevHandler(id, staticController));
                                            outputDetailedGen("*********", breachMDevOutput);
                                            outputDetailedGen(str("NEW", o.orderId(), "Breach MDEV SELL:"
                                                    , globalIdOrderMap.get(id)), breachMDevOutput);
                                        }
                                    }
                                }
                            }
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
        BreachDevTrader trader = new BreachDevTrader();
        trader.connectAndReqPos();
    }

}
