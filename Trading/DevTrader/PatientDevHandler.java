package DevTrader;

import api.TradingConstants;
import client.OrderState;
import client.OrderStatus;
import controller.ApiController;
import utility.Utility;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static DevTrader.BreachDevTrader.devOrderMap;
import static utility.TradingUtility.outputToError;
import static utility.Utility.outputToSymbolFile;
import static client.OrderStatus.Filled;
import static utility.Utility.str;

public class PatientDevHandler implements ApiController.IOrderHandler {

    private static Map<Integer, OrderStatus> idStatusMap = new ConcurrentHashMap<>();
    private int tradeID;
    private static File breachMDevOutput = new File(TradingConstants.GLOBALPATH + "breachMDev.txt");


    PatientDevHandler(int id) {
        tradeID = id;
        idStatusMap.put(id, OrderStatus.ConstructedInHandler);
    }

    @Override
    public void orderState(OrderState orderState) {
        LocalDateTime now = LocalDateTime.now();
        if (devOrderMap.containsKey(tradeID)) {
            devOrderMap.get(tradeID).setFinalActionTime(LocalDateTime.now());
            devOrderMap.get(tradeID).setAugmentedOrderStatus(orderState.status());
        } else {
            throw new IllegalStateException(" global id order map doesn't contain ID" + tradeID);
        }

        if (orderState.status() != idStatusMap.get(tradeID)) {
            if (orderState.status() == Filled) {
                String msg = Utility.str(devOrderMap.get(tradeID).getOrder().orderId(),
                        devOrderMap.get(tradeID).getOrder().orderId(),
                        "*PATIENT DEV FILL*", idStatusMap.get(tradeID), "->", orderState.status(), now,
                        "ID:", tradeID, devOrderMap.get(tradeID),
                        "TIF:", devOrderMap.get(tradeID).getOrder().tif());
                outputToSymbolFile(devOrderMap.get(tradeID).getSymbol(), msg, breachMDevOutput);
            }
            idStatusMap.put(tradeID, orderState.status());
        }


    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId,
                            int parentId, double lastFillPrice, int clientId, String whyHeld) {

    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        outputToError(str("ERROR: Patient Dev Handler:", tradeID, errorCode, errorMsg
                , devOrderMap.get(tradeID)));

    }
}
