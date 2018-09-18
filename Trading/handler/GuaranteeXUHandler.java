package handler;

import apidemo.AutoTraderMain;
import apidemo.FutType;
import client.*;
import controller.ApiController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static apidemo.AutoTraderMain.globalIdOrderMap;
import static apidemo.AutoTraderXU.*;
import static apidemo.XuTraderHelper.outputOrderToAutoLogXU;
import static apidemo.XuTraderHelper.outputPurelyOrdersDetailedXU;
import static utility.Utility.*;

public class GuaranteeXUHandler implements ApiController.IOrderHandler {

    private static Map<Integer, OrderStatus> idStatusMap = new ConcurrentHashMap<>();
    private int defaultID;
    private ApiController controller;

    public GuaranteeXUHandler(int id, ApiController ap) {
        defaultID = id;
        idStatusMap.put(id, OrderStatus.ConstructedInHandler);
        controller = ap;
    }

    @Override
    public void orderState(OrderState orderState) {
        LocalTime now = LocalTime.now();
        if (globalIdOrderMap.containsKey(defaultID)) {
            globalIdOrderMap.get(defaultID).setFinalActionTime(LocalDateTime.now());
            globalIdOrderMap.get(defaultID).setAugmentedOrderStatus(orderState.status());
        } else {
            throw new IllegalStateException(" global id order map doesn't " +
                    "contain default ID " + defaultID);
        }

        if (orderState.status() != idStatusMap.get(defaultID)) {
            String msg = str(globalIdOrderMap.get(defaultID).getOrder().orderId(), "*GUARANTEE*",
                    "**STATUS CHG**", idStatusMap.get(defaultID), "->", orderState.status(), now,
                    "ID:", defaultID, globalIdOrderMap.get(defaultID),
                    "TIF:", globalIdOrderMap.get(defaultID).getOrder().tif());
            outputPurelyOrdersDetailedXU(msg);
            if (orderState.status() == OrderStatus.PendingCancel &&
                    globalIdOrderMap.get(defaultID).getOrder().tif() == Types.TimeInForce.IOC) {
                FutType f = ibContractToFutType(activeFutureCt);
                double bid = bidMap.get(f);
                double ask = askMap.get(f);
                double freshPrice = futPriceMap.get(f);

                Order prevOrder = globalIdOrderMap.get(defaultID).getOrder();
                Order o = new Order();
                o.action(prevOrder.action());
                o.lmtPrice(freshPrice);
                o.orderType(OrderType.LMT);
                o.totalQuantity(prevOrder.totalQuantity());
                o.outsideRth(true);
                o.tif(Types.TimeInForce.IOC);

                int id = AutoTraderMain.autoTradeID.incrementAndGet();
                controller.placeOrModifyOrder(activeFutureCt, o, new GuaranteeXUHandler(id, controller));
                globalIdOrderMap.put(id, new OrderAugmented(
                        ibContractToSymbol(activeFutureCt), LocalDateTime.now(), o,
                        globalIdOrderMap.get(defaultID).getOrderType(), false));

                outputOrderToAutoLogXU(str(prevOrder.orderId(), "->", o.orderId(),
                        "RESUBMITXU. Type, TIF, Action, P, Q,isPrimary", globalIdOrderMap.get(id).getOrderType(),
                        o.tif(), o.action(), o.lmtPrice(), o.totalQuantity(), globalIdOrderMap.get(id).isPrimaryOrder(),
                        "||current", globalIdOrderMap.get(id), "bid ask sp last"
                        , bid, ask, Math.round(10000d * (ask / bid - 1)), "bp", freshPrice));
            }
            idStatusMap.put(defaultID, orderState.status());
        }
    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId,
                            int parentId, double lastFillPrice, int clientId, String whyHeld) {

    }

    @Override
    public void handle(int errorCode, String errorMsg) {

    }
}
