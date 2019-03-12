package api;

import AutoTraderOld.AutoTraderMain;
import auxiliary.SimpleBar;
import client.Contract;
import client.Types;
import controller.ApiConnection;
import controller.ApiController;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static utility.Utility.pr;

public class ResearchAPI {

    private static volatile ConcurrentSkipListMap<String, ConcurrentSkipListMap<LocalDate, SimpleBar>>
            hkData = new ConcurrentSkipListMap<>();
    private static volatile HashMap<String, String> hkNameMap = new HashMap<>();

    private static AtomicInteger hkReqId = new AtomicInteger(500000);

    ResearchAPI() {
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "hkMainNames.txt"), "gbk"))) {
            String line;
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                String hkSymbol = AutoTraderMain.hkTickerToSymbol(al1.get(0));
                hkData.put(hkSymbol, new ConcurrentSkipListMap<>());
                hkNameMap.put(hkSymbol, al1.get(1));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void handleHist(Contract c, String date, double open, double high, double low,
                                   double close, int volume) {
        String symbol = utility.Utility.ibContractToSymbol(c);
        //pr(c.symbol(), date, open, high, low, close, volume);
        LocalDate ld = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
        hkData.get(symbol).put(ld, new SimpleBar(open, high, low, close));

    }


    @SuppressWarnings("Duplicates")
    public static void main(String[] args) {
        ApiController ap = new ApiController(new ApiController.IConnectionHandler.DefaultConnectionHandler(),
                new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());
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

        hkNameMap.keySet().forEach(s -> {
            Contract hkCt = AutoTraderMain.symbolToHKStkContract(s);
            if (hkReqId.get() % 90 == 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            ap.reqHistDayData(hkReqId.incrementAndGet()
                    , hkCt, ResearchAPI::handleHist, 365, Types.BarSize._1_week);
        });

//        //ap.req1StockLive("IQ", "SMART", "USD", new ReceiverUS("IQ"), false);
//        Contract hkCt = AutoTraderMain.tickerToHKStkContract("5");
////        Contract ct = getFrontFutContract();
//        //Contract i = AutoTraderMain.getXINAIndexContract();
//        ap.reqHistDayData(10000, hkCt, ResearchAPI::handleHist, 365, Types.BarSize._1_day);

    }

}
