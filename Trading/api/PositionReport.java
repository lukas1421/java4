package api;

import auxiliary.SimpleBar;
import client.Contract;
import client.Types;
import controller.ApiConnection.ILogger.DefaultLogger;
import controller.ApiController;
import controller.ApiController.IConnectionHandler.DefaultConnectionHandler;
import utility.Utility;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static utility.Utility.*;

public class PositionReport implements ApiController.IPositionHandler {

    private static File positionOutput = new File(TradingConstants.GLOBALPATH + "positionReport.txt");
    static final DateTimeFormatter f = DateTimeFormatter.ofPattern("M-d");
    private static final LocalDate LAST_MONTH_DAY = getLastMonthLastDay();
    private static final LocalDate LAST_YEAR_DAY = getLastYearLastDay();
    private volatile static Map<Contract, Double> holdingsMap = new TreeMap<>(Comparator.comparing(Contract::symbol));
    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            morningYtdData = new ConcurrentSkipListMap<>();

    private static ApiController staticController;

    private PositionReport() {

    }

    private void ibTask() {

        clearFile(positionOutput);
        ApiController ap = new ApiController(new DefaultConnectionHandler(), new DefaultLogger(), new DefaultLogger());
        staticController = ap;
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            ap.connect("127.0.0.1", 7496, 2, "");
            connectionStatus = true;
            pr(" connection status is true ");
            l.countDown();
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ");
        }

        if (!connectionStatus) {
            pr(" using port 4001 ");
            ap.connect("127.0.0.1", 4001, 2, "");
            l.countDown();
            pr(" Latch counted down " + LocalTime.now());
        }

        try {
            l.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        pr(" Time after latch released " + LocalTime.now());
        pr(" request holdings ");
        ap.reqPositions(this);
    }

    private static void morningYtdOpen(Contract c, String date, double open, double high, double low,
                                       double close, int volume) {
        String symbol = ibContractToSymbol(c);
        if (!date.startsWith("finished")) {
            LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
            //pr("morningYtdOpen", symbol, ld, open, high, low, close);
            morningYtdData.get(symbol).put(ld, new SimpleBar(open, high, low, close));
        } else {
            //finished
            //pr(" finished ", c.symbol(), date, open, close);
            double size = holdingsMap.getOrDefault(c, 0.0);
            if (morningYtdData.containsKey(symbol) && morningYtdData.get(symbol).size() > 0) {
                double yOpen = morningYtdData.get(symbol).higherEntry(LAST_YEAR_DAY).getValue().getOpen();
                long yCount = morningYtdData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY)).count();
                double mOpen = morningYtdData.get(symbol).higherEntry(LAST_MONTH_DAY).getValue().getOpen();
                long mCount = morningYtdData.get(symbol).entrySet().stream()
                        .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY)).count();
                double last;
                last = morningYtdData.get(symbol).lastEntry().getValue().getClose();
                String info = "";
                double yDev = Math.round((last / yOpen - 1) * 1000d) / 10d;
                double mDev = Math.round((last / mOpen - 1) * 1000d) / 10d;
                if (size > 0) {
                    if (yDev > 0 && mDev > 0) {
                        info = "LONG ON";
                    } else {
                        info = "LONG OFF";
                    }
                } else if (size < 0) {
                    if (yDev < 0 && mDev < 0) {
                        info = "SHORT ON";
                    } else {
                        info = "SHORT OFF";
                    }
                } else {
                    info = "ERROR";
                }

                String out = getStrTabbed(symbol, size,
                        morningYtdData.get(symbol).lastEntry().getKey().format(f), last,
                        "||yOpen", morningYtdData.get(symbol).higherEntry(LAST_YEAR_DAY).getKey().format(f), yOpen,
                        "yDays", yCount, "yUp%",
                        Math.round(1000d * morningYtdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
                                .filter(e -> e.getValue().getClose() > yOpen).count() / yCount) / 10d + "%",
                        "yDev", yDev + "%",
                        "||mOpen ", morningYtdData.get(symbol).higherEntry(LAST_MONTH_DAY).getKey().format(f), mOpen,
                        "mDays", mCount, "mUp%",
                        Math.round(1000d * morningYtdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY))
                                .filter(e -> e.getValue().getClose() > mOpen).count() / mCount) / 10d + "%",
                        "mDev", mDev + "%", info);
                pr("*", out);
                Utility.simpleWriteToFile(out, true, positionOutput);
            }
        }
    }

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        if (!contract.symbol().equals("USD")) {
            holdingsMap.put(contract, position);
        }
    }

    @Override
    public void positionEnd() {
        pr(" holdings map ");

        holdingsMap.forEach((key, value) -> pr("symbol pos ", key.symbol(), value));

        for (Contract c : holdingsMap.keySet()) {
            String k = ibContractToSymbol(c);
            morningYtdData.put(k, new ConcurrentSkipListMap<>());
            if (!k.startsWith("sz") && !k.startsWith("sh") && !k.equals("USD")) {
                staticController.reqHistDayData(ibStockReqId.addAndGet(5),
                        fillContract(c), PositionReport::morningYtdOpen, 250, Types.BarSize._1_day);
            }
        }
    }

    private static Contract fillContract(Contract c) {
        pr("symb ", Optional.ofNullable(c.symbol()),
                "curr", Optional.ofNullable(c.currency()),
                "exch", Optional.ofNullable(c.exchange()),
                "prim exch", Optional.ofNullable(c.primaryExch()),
                "secType", Optional.ofNullable(c.secType())
        );
        if (c.symbol().equals("XINA50")) {
            //pr(" us no exchange ");
            c.exchange("SGX");
        }

        if (c.currency().equals("USD") && c.secType().equals(Types.SecType.STK)) {
            pr(" USD STOCK ", c.symbol());
            c.exchange("SMART");
        }
        return c;
    }


    public static void main(String[] args) {
        PositionReport pr = new PositionReport();
        pr.ibTask();

    }
}
