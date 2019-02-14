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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static api.AutoTraderMain.autoTradeID;
import static api.AutoTraderMain.globalIdOrderMap;
import static api.XuTraderHelper.*;
import static client.Types.TimeInForce.IOC;
import static util.AutoOrderType.BREACH_MDEV;
import static utility.Utility.*;

public class BreachMDevTrader implements LiveHandler, ApiController.IPositionHandler {

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

    private Map<String, Double> bidMap = new HashMap<>();
    private Map<String, Double> askMap = new HashMap<>();
    private static volatile Map<String, AtomicBoolean> orderBlocked = new HashMap<>();


    private BreachMDevTrader() {
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
    }

    private static void registerContract(Contract ct) {
        contractPosMap.put(ct, 0.0);
        symbolPosMap.put(ibContractToSymbol(ct), 0.0);
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
            //pr(" connection : status is true ");
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
            registerContract(contract);
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
                        fillContract(c), BreachMDevTrader::ytdOpen, getCalendarYtdDays(), Types.BarSize._1_day);
            }
            staticController.req1ContractLive(c, this, false);
        }
    }

    private static int getCalendarYtdDays() {
        return (int) ChronoUnit.DAYS.between(LAST_YEAR_DAY, LocalDate.now());
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
                    double lastValue = liveData.get(symbol).lastEntry().getValue();
                    double defaultS = defaultSizeMap.getOrDefault(symbol, 0.0);
                    boolean orderBlockStatus = orderBlocked.get(symbol).get();

                    pr("MDev", symbol, pos, "block:" + orderBlockStatus,
                            "DefaultS:", defaultS, "yOpen:" + yOpen, "mOpen:" + mOpen,
                            "FV:", liveData.get(symbol).firstKey().format(f1) + " " + liveData.get(symbol).firstEntry().getValue() + "(" +
                                    Math.round(1000d * (liveData.get(symbol).firstEntry().getValue() / mOpen - 1)) / 10d + "%)",
                            "LV:", liveData.get(symbol).lastKey().format(f1) + " " + liveData.get(symbol).lastEntry().getValue() + "(" +
                                    Math.round(1000d * (liveData.get(symbol).lastEntry().getValue() / mOpen - 1)) / 10d + "%)");

                    if (!orderBlocked.get(symbol).get()) {
                        if (firstValue < mOpen && lastValue > mOpen) {
                            if (pos <= 0) {
                                if (askMap.getOrDefault(symbol, 0.0) != 0.0
                                        && Math.abs(askMap.get(symbol) / price - 1) < 0.01) {
                                    orderBlocked.get(symbol).set(true);
                                    int id = autoTradeID.incrementAndGet();
                                    Order o = placeBidLimitTIF(askMap.get(symbol), Math.abs(pos) + defaultS, IOC);
                                    globalIdOrderMap.put(id, new OrderAugmented(symbol, t, o, BREACH_MDEV));
                                    staticController.placeOrModifyOrder(ct, o,
                                            new ApiController.IOrderHandler.DefaultOrderHandler(id));
                                    outputDetailedGen("*********", breachMDevOutput);
                                    outputDetailedGen(str("NEW", o.orderId(), "Breach MDEV BUY:",
                                            globalIdOrderMap.get(id), "pos", pos), breachMDevOutput);
                                } else if (firstValue > mOpen && lastValue < mOpen) {
                                    if (pos >= 0) {
                                        if (bidMap.getOrDefault(symbol, 0.0) != 0.0
                                                && Math.abs(bidMap.get(symbol) / price - 1) < 0.01) {
                                            orderBlocked.get(symbol).set(true);
                                            int id = autoTradeID.incrementAndGet();
                                            Order o = placeOfferLimitTIF(bidMap.get(symbol), pos + defaultS, IOC);
                                            globalIdOrderMap.put(id, new OrderAugmented(symbol, t, o, BREACH_MDEV));
                                            staticController.placeOrModifyOrder(ct, o,
                                                    new ApiController.IOrderHandler.DefaultOrderHandler(id));
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
        BreachMDevTrader trader = new BreachMDevTrader();
        trader.connectAndReqPos();
    }

}
