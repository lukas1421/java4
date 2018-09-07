package apidemo;

import client.OrderState;
import client.OrderStatus;
import controller.ApiController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static apidemo.XUTrader.globalIdOrderMap;
import static utility.Utility.str;

public class GuaranteeFillOrderHandler implements ApiController.IOrderHandler {

    static Set<Integer> filledOrderSet = new HashSet<>();
    int defaultID;

    GuaranteeFillOrderHandler(int id) {
        defaultID = id;
    }

    @Override
    public void orderState(OrderState orderState) {
        if (globalIdOrderMap.containsKey(defaultID)) {
            globalIdOrderMap.get(defaultID).setFinalActionTime(LocalDateTime.now());
            globalIdOrderMap.get(defaultID).setAugmentedOrderStatus(orderState.status());
        } else {
            throw new IllegalStateException(" global id order map doesn't " +
                    "contain default ID " + defaultID);
        }

        if (orderState.status() == OrderStatus.Filled) {
            if (!filledOrderSet.contains(defaultID)) {
                String msg = str(globalIdOrderMap.get(defaultID).getOrder().orderId(),
                        "||Order||", LocalTime.now().truncatedTo(ChronoUnit.SECONDS),
                        defaultID, globalIdOrderMap.get(defaultID), orderState.status());
                XuTraderHelper.outputPurelyOrders(msg);
                filledOrderSet.add(defaultID);
            }
        }


    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {

    }

    @Override
    public void handle(int errorCode, String errorMsg) {

    }
}
