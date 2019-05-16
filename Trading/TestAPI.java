import client.*;
import controller.ApiConnection;
import controller.ApiController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;

import static utility.TradingUtility.getActiveBTCExpiry;
import static utility.TradingUtility.getPrevBTCExpiry;
import static utility.Utility.*;

public class TestAPI {

    private volatile static Map<String, Double> contractPosMap = new ConcurrentSkipListMap<>(String::compareTo);
    private volatile static Map<String, Double> symbolPosMap = new ConcurrentSkipListMap<>(String::compareTo);
    private volatile static Map<String, Double> symbolPriceMap = new ConcurrentSkipListMap<>(String::compareTo);


    TestAPI() {
//        String line;
//        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
//                new FileInputStream(TradingConstants.GLOBALPATH + "breachUSNames.txt")))) {
//            while ((line = reader1.readLine()) != null) {
//                List<String> al1 = Arrays.asList(line.split("\t"));
//                registerContract(getUSStockContract(al1.get(0)));
//            }
//        } catch (IOException x) {
//            x.printStackTrace();
//        }
    }

    private void registerContract(Contract ct) {
        String symbol = ibContractToSymbol(ct);
        if (!symbol.equalsIgnoreCase("SGXA50PR")) {
            symbolPosMap.put(symbol, 0.0);
            symbolPriceMap.put(symbol, 0.0);
        }
    }


    private static Contract getFrontFutContract() {
        Contract ct = new Contract();
        ct.symbol("XINA50");
        ct.exchange("SGX");
        ct.currency("USD");
//        pr("front exp date ", TradingConstants.A50_FRONT_EXPIRY);
        //ct.localSymbol("XINA50");
        //ct.lastTradeDateOrContractMonth(TradingConstants.A50_FRONT_EXPIRY);
        ct.secType(Types.SecType.CONTFUT);
        return ct;
    }

    public static Contract getPrevBTC() {
        Contract ct = new Contract();
        ct.symbol("GXBT");
        ct.exchange("CFECRYPTO");
        ct.secType(Types.SecType.FUT);
        //ct.secType(Types.SecType.FUT);
//        pr(getPrevBTCExpiry().format(futExpPattern));
        ct.lastTradeDateOrContractMonth(getPrevBTCExpiry().format(futExpPattern));
        ct.includeExpired(true);
        ct.currency("USD");
        return ct;
    }

    public static Contract getActiveBTC() {
        Contract ct = new Contract();
        ct.symbol("GXBT");
        ct.exchange("CFECRYPTO");
        ct.secType(Types.SecType.FUT);
        ct.lastTradeDateOrContractMonth(getActiveBTCExpiry().format(futExpPattern));
        ct.currency("USD");
        return ct;
    }

    public static Contract getContBTC() {
        Contract ct = new Contract();
        ct.symbol("GXBT");
        ct.exchange("CFECRYPTO");
        ct.secType(Types.SecType.CONTFUT);
        ct.currency("USD");
        return ct;
    }

    static void handleHist(Contract c, String date, double open, double high, double low,
                           double close, int volume) {
        String symbol = utility.Utility.ibContractToSymbol(c);
        pr(c.symbol(), date, open, high, low, close, volume);
    }


    public static void main(String[] args) {
        ApiController ap = new ApiController(new ApiController.IConnectionHandler.DefaultConnectionHandler(),
                new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;

        try {
            ap.connect("127.0.0.1", 7496, 100, "");
            connectionStatus = true;
            pr(" connection status is true ");
            l.countDown();
        } catch (IllegalStateException ex) {
            pr(" illegal state exception caught ");
        }

        if (!connectionStatus) {
            pr(" using port 4001 ");
            ap.connect("127.0.0.1", 4001, 100, "");
            l.countDown();
            pr(" Latch counted down " + LocalTime.now());
        }

        try {
            l.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        pr(" Time after latch released " + LocalTime.now());

        //Contract ct = getFrontFutContract();

        //Contract ct = getPrevBTC();
//        Contract ct = getActiveBTC();
        Contract ct = getContBTC();
        //ct.secType(Types.SecType.CONTFUT);

        ap.reqHistDayData(10001,
                ct, (contract, date, open, high, low, close, vol) -> {
                    if (!date.startsWith("finished")) {
                        pr(date, open, high, low, close);
//                        Date dt = new Date(Long.parseLong(date) * 1000);
//                        Calendar cal = Calendar.getInstance();
//                        cal.setTime(dt);
//                        LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
//                        LocalTime lt = ltof(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
//                        LocalDateTime ldt = LocalDateTime.of(ld, lt);
//                        pr(ldt, open, high, low, close);

//                        pr(LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"))
//                                , open, high, low, close);
                    } else {
                        pr(date, open, close);
                    }
                }, 10, Types.BarSize._1_day);


    }
}
