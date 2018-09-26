package handler;

import client.*;
import controller.ApiController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static apidemo.AutoTraderMain.autoTradeID;
import static apidemo.AutoTraderMain.globalIdOrderMap;
import static apidemo.AutoTraderUS.*;
import static apidemo.XuTraderHelper.*;
import static client.OrderStatus.Filled;
import static client.OrderStatus.PendingCancel;
import static client.Types.TimeInForce.IOC;
import static utility.Utility.str;

public class GuaranteeUSHandler implements ApiController.IOrderHandler {

    private static Map<Integer, OrderStatus> idStatusMap = new ConcurrentHashMap<>();
    private int primaryID;
    private int defaultID;
    private ApiController controller;

    public GuaranteeUSHandler(int id, ApiController ap) {
        primaryID = id;
        defaultID = id;
        idStatusMap.put(id, OrderStatus.ConstructedInHandler);
        controller = ap;
    }


    public GuaranteeUSHandler(int prim, int id, ApiController ap) {
        primaryID = prim;
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
            if (orderState.status() == Filled) {
                String msg = str(globalIdOrderMap.get(primaryID).getOrder().orderId(),
                        globalIdOrderMap.get(defaultID).getOrder().orderId(),
                        "*GUARANTEE US FILL*",
                        idStatusMap.get(defaultID), "->", orderState.status(), now,
                        "ID:", defaultID, globalIdOrderMap.get(defaultID),
                        "TIF:", globalIdOrderMap.get(defaultID).getOrder().tif());
                outputPurelyOrdersDetailedXU(msg);
            }

            if (orderState.status() == PendingCancel && globalIdOrderMap.get(defaultID).getOrder().tif() == IOC) {
                String symbol = globalIdOrderMap.get(defaultID).getSymbol();
                double freshPrice = usFreshPriceMap.get(symbol);
                double bid = usBidMap.get(symbol);
                double ask = usAskMap.get(symbol);
                Contract ct = tickerToUSContract(symbol);

                Order prevOrder = globalIdOrderMap.get(defaultID).getOrder();
                Order o = new Order();
                o.action(prevOrder.action());
                o.lmtPrice(freshPrice);
                o.orderType(OrderType.LMT);
                o.totalQuantity(prevOrder.totalQuantity());
                o.outsideRth(true);
                o.tif(IOC);

                int newID = autoTradeID.incrementAndGet();
                controller.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(primaryID, newID, controller));
                globalIdOrderMap.put(newID, new OrderAugmented(symbol, LocalDateTime.now(), o,
                        globalIdOrderMap.get(defaultID).getOrderType(), false));

                outputOrderToAutoLogXU(str(globalIdOrderMap.get(primaryID).getOrder().orderId(),
                        prevOrder.orderId(), "->", o.orderId(),
                        "RESUBMIT US:", globalIdOrderMap.get(newID).getOrderType(),
                        o.tif(), o.action(), o.lmtPrice(), o.totalQuantity(), globalIdOrderMap.get(newID).isPrimaryOrder(),
                        "current", globalIdOrderMap.get(newID), "bid ask sp last"
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
/*
        outputOrderToAutoLogXU(str("ERROR:", "Guarantee US handler:", defaultID, errorCode, errorMsg
                , globalIdOrderMap.get(defaultID)));
*/
        outputToErrorLog(str("ERROR:", "Guarantee US handler:", defaultID, errorCode, errorMsg
                , globalIdOrderMap.get(defaultID)));
    }
}
