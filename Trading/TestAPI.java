import client.*;
import controller.ApiConnection;
import controller.ApiController;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;

import static utility.Utility.ibContractToSymbol;
import static utility.Utility.pr;

public class TestAPI {


    TestAPI() {

    }

    private Contract getUSStockContract(String symb) {
        Contract ct = new Contract();
        ct.symbol(symb);
        ct.exchange("SMART");
        ct.currency("USD");
        ct.secType(Types.SecType.STK);
        return ct;
    }

    static void handleHist(Contract c, String date, double open, double high, double low,
                           double close, int volume) {
        String symbol = utility.Utility.ibContractToSymbol(c);
        pr(c.symbol(), date, open, high, low, close, volume);
    }

    static class TradesHandler implements ApiController.ITradeReportHandler {

        @Override
        public void tradeReport(String tradeKey, Contract contract, Execution execution) {
            pr("key symb exec ", tradeKey, ibContractToSymbol(contract), execution.price(), execution.shares());

        }

        @Override
        public void tradeReportEnd() {
            pr("trade report  end ");

        }

        @Override
        public void commissionReport(String tradeKey, CommissionReport commissionReport) {

        }
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
        ap.reqExecutions(new ExecutionFilter(), new TradesHandler());

        //ap.reqPositions(new ApiController.IPositionHandler.DefaultPositionHandler());

    }
}
