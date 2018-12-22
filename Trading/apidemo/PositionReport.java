package apidemo;

import client.Contract;
import controller.ApiConnection.ILogger.DefaultLogger;
import controller.ApiController;
import controller.ApiController.IConnectionHandler.DefaultConnectionHandler;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static utility.Utility.getLastMonthLastDay;
import static utility.Utility.getLastYearLastDay;

public class PositionReport {

    private static final LocalDate LAST_MONTH_DAY = getLastMonthLastDay();
    private static final LocalDate LAST_YEAR_DAY = getLastYearLastDay();
    private volatile static Map<Contract, Double> holdingsMap = new HashMap<>();


    private static ApiController staticController;

    private PositionReport() {

    }

    private void ibTask() {
        ApiController ap = new ApiController(new DefaultConnectionHandler(), new DefaultLogger(), new DefaultLogger());
        staticController = ap;
        CountDownLatch l = new CountDownLatch(1);
        boolean connectionStatus = false;




    }

}
