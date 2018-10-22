import apidemo.AutoTraderMain;
import client.Contract;
import client.Types;
import controller.ApiConnection;
import controller.ApiController;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;

import static utility.Utility.pr;

public class TestAPI {


    TestAPI() {

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

        //ap.req1StockLive("IQ", "SMART", "USD", new ReceiverUS("IQ"), false);
        Contract hkCt = AutoTraderMain.tickerToHKStkContract("5");
//        Contract ct = getFrontFutContract();
        //Contract i = AutoTraderMain.getXINAIndexContract();
        ap.reqHistDayData(10000, hkCt, TestAPI::handleHist, 365, Types.BarSize._1_day);

    }
}
