package apidemo;

import client.*;
import controller.ApiController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static apidemo.XUTrader.*;
import static apidemo.XuTraderHelper.outputPurelyOrdersDetailed;
import static utility.Utility.ibContractToFutType;
import static utility.Utility.str;

public class GuaranteeFillOrderHandler implements ApiController.IOrderHandler {

    private static Map<Integer, OrderStatus> idStatusMap = new ConcurrentHashMap<>();
    private int defaultID;
    ApiController controller;

    private GuaranteeFillOrderHandler(int id, ApiController ap) {
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
            String msg = str("*MUST FILL*", globalIdOrderMap.get(defaultID).getOrder().orderId(),
                    "**STATUS CHG**", idStatusMap.get(defaultID), "->", orderState.status(), now,
                    defaultID, globalIdOrderMap.get(defaultID), globalIdOrderMap.get(defaultID).getOrder().tif());
            outputPurelyOrdersDetailed(msg);
            //if IOC and cancelled, fill at market price
            if (orderState.status() == OrderStatus.Cancelled &&
                    globalIdOrderMap.get(defaultID).getOrder().tif() == Types.TimeInForce.IOC) {
                FutType f = ibContractToFutType(activeFutureCt);
                double bid = bidMap.get(f);
                double ask = askMap.get(f);
                double last = futPriceMap.get(f);

                Order prevOrder = globalIdOrderMap.get(defaultID).getOrder();
                Order o = new Order();
                o.action(prevOrder.action());
                o.lmtPrice(prevOrder.action() == Types.Action.BUY ? ask :
                        (prevOrder.action() == Types.Action.SELL ? bid : last));
                o.orderType(OrderType.LMT);
                o.totalQuantity(prevOrder.totalQuantity());
                o.outsideRth(true);
                o.tif(Types.TimeInForce.FOK);

                int id = autoTradeID.incrementAndGet();
                controller.placeOrModifyOrder(activeFutureCt, o, new GuaranteeFillOrderHandler(id, controller));
                globalIdOrderMap.put(id, new OrderAugmented(LocalDateTime.now(), o,
                        globalIdOrderMap.get(defaultID).getOrderType()));
            }

            idStatusMap.put(defaultID, orderState.status());
        }


    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {

    }

    @Override
    public void handle(int errorCode, String errorMsg) {

    }
}
