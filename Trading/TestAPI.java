import apidemo.AutoTraderHK;
import client.Contract;
import controller.ApiConnection;
import controller.ApiController;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;

import static utility.Utility.pr;

public class TestAPI {


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

        // req
        //ap.reqUSAutoTrader();
        //ap.req1StockLive("IQ", "SMART", "USD", new ReceiverUS("IQ"), false);
        Contract hkTestCont = AutoTraderHK.tickerToHKStkContract("5");
        ap.reqContractDetails(hkTestCont, new ApiController.IContractDetailsHandler.DefaultContractDetailsHandler());


    }
}
