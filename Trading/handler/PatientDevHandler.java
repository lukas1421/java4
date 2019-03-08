package handler;

import api.TradingConstants;
import client.OrderState;
import client.OrderStatus;
import controller.ApiController;
import utility.Utility;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static api.AutoTraderMain.globalIdOrderMap;
import static api.XuTraderHelper.outputToError;
import static api.XuTraderHelper.outputToSymbolFile;
import static client.OrderStatus.Filled;
import static utility.Utility.str;

public class PatientDevHandler implements ApiController.IOrderHandler {

    private static Map<Integer, OrderStatus> idStatusMap = new ConcurrentHashMap<>();
    private int tradeID;
    private ApiController controller;
    private static File breachMDevOutput = new File(TradingConstants.GLOBALPATH + "breachMDev.txt");


    public PatientDevHandler(int id) {
        tradeID = id;
        idStatusMap.put(id, OrderStatus.ConstructedInHandler);
    }

    @Override
    public void orderState(OrderState orderState) {
        LocalDateTime now = LocalDateTime.now();
        if (globalIdOrderMap.containsKey(tradeID)) {
            globalIdOrderMap.get(tradeID).setFinalActionTime(LocalDateTime.now());
            globalIdOrderMap.get(tradeID).setAugmentedOrderStatus(orderState.status());
        } else {
            throw new IllegalStateException(" global id order map doesn't contain ID" + tradeID);
        }

        if (orderState.status() != idStatusMap.get(tradeID)) {
            if (orderState.status() == Filled) {
                String msg = Utility.str(globalIdOrderMap.get(tradeID).getOrder().orderId(),
                        globalIdOrderMap.get(tradeID).getOrder().orderId(),
                        "*PATIENT DEV FILL*", idStatusMap.get(tradeID), "->", orderState.status(), now,
                        "ID:", tradeID, globalIdOrderMap.get(tradeID),
                        "TIF:", globalIdOrderMap.get(tradeID).getOrder().tif());
                outputToSymbolFile(globalIdOrderMap.get(tradeID).getSymbol(), msg, breachMDevOutput);
            }
            idStatusMap.put(tradeID, orderState.status());
        }


    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {

    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        outputToError(str("ERROR: Patient Dev Handler:", tradeID, errorCode, errorMsg
                , globalIdOrderMap.get(tradeID)));

    }
}
