package api;

import auxiliary.SimpleBar;
import client.Contract;
import client.Types;
import controller.ApiConnection.ILogger.DefaultLogger;
import controller.ApiController;
import handler.DefaultConnectionHandler;
import utility.TradingUtility;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static utility.Utility.*;

public class ETFReport {


    private static File etfFile = new File(TradingConstants.GLOBALPATH + "etfFile.txt");
    static final DateTimeFormatter f = DateTimeFormatter.ofPattern("M-d");
    private static final LocalDate LAST_MONTH_DAY = getLastMonthLastDay();
    private static final LocalDate LAST_YEAR_DAY = getLastYearLastDay();
    //private volatile static Map<Contract, Double> holdingsMap = new TreeMap<>(Comparator.comparing(Contract::symbol));
    private static volatile AtomicInteger ibStockReqId = new AtomicInteger(60000);

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            morningYtdData = new ConcurrentSkipListMap<>();

    private static Map<String, String> etfCountryMap = new TreeMap<>(Comparator.naturalOrder());
    //private Map<String,String> etfCountryMap = new HashMap<>();


    private ETFReport() {
        String line;
        try (BufferedReader reader1 = new BufferedReader
                (new InputStreamReader(new FileInputStream(etfFile), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                etfCountryMap.put(al1.get(0), al1.get(1));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void runThis() {
        ApiController ap = new ApiController(new DefaultConnectionHandler(), new DefaultLogger(), new DefaultLogger());
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

        for (String k : etfCountryMap.keySet()) {
            //String k = ibContractToSymbol(c);
            morningYtdData.put(k, new ConcurrentSkipListMap<>());
            if (!k.startsWith("sz") && !k.startsWith("sh") && !k.equals("USD")) {
                TradingUtility.reqHistDayData(ap, ibStockReqId.addAndGet(5),
                        etfToUSContract(k), ETFReport::morningYtdOpen, 250, Types.BarSize._1_day);
            }
        }
    }

    private static Contract etfToUSContract(String key) {
        Contract c = new Contract();
        c.symbol(key);
        c.secType(Types.SecType.STK);
        c.exchange("SMART");
        if (key.equals("DXJ")) {
            c.primaryExch("ARCA");
        }
        c.currency("USD");
        return c;
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
            //double size = holdingsMap.getOrDefault(c, 0.0);
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
                double secLast = morningYtdData.get(symbol).lowerEntry(morningYtdData.get(symbol).lastKey())
                        .getValue().getClose();
                double lastChg = Math.round((last / secLast - 1) * 1000d) / 10d;

                String out = getStrTabbed(symbol, etfCountryMap.get(symbol),
                        morningYtdData.get(symbol).lastEntry().getKey().format(f), last,
                        lastChg + "%",
                        "||yOpen " + morningYtdData.get(symbol).higherEntry(LAST_YEAR_DAY).getKey().format(f), yOpen,
                        "yDays" + yCount, "yUp%",
                        Math.round(1000d * morningYtdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_YEAR_DAY))
                                .filter(e -> e.getValue().getClose() > yOpen).count() / yCount) / 10d + "%",
                        "yDev", yDev + "%",
                        "||mOpen " + morningYtdData.get(symbol).higherEntry(LAST_MONTH_DAY).getKey().format(f), mOpen,
                        "mDays" + mCount, "mUp%",
                        Math.round(1000d * morningYtdData.get(symbol).entrySet().stream()
                                .filter(e -> e.getKey().isAfter(LAST_MONTH_DAY))
                                .filter(e -> e.getValue().getClose() > mOpen).count() / mCount) / 10d + "%",
                        "mDev", mDev + "%", info);
                pr("*", out);
                //Utility.simpleWriteToFile(out, true, positionOutput);
            }
        }
    }


    public static void main(String[] args) {
        ETFReport e = new ETFReport();
        e.runThis();

    }


}
