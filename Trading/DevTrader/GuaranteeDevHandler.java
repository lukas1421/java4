package DevTrader;

import api.TradingConstants;
import client.*;
import controller.ApiController;
import utility.Utility;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static DevTrader.BreachDevTrader.devOrderMap;
import static DevTrader.BreachDevTrader.devTradeID;
import static client.OrderStatus.Filled;
import static client.OrderStatus.PendingCancel;
import static client.Types.TimeInForce.IOC;
import static utility.TradingUtility.outputToError;
import static utility.Utility.str;

public class GuaranteeDevHandler implements ApiController.IOrderHandler {

    private static Map<Integer, OrderStatus> idStatusMap = new ConcurrentHashMap<>();
    private int primaryID;
    private int currentID;
    private ApiController controller;
    private static File breachMDevOutput = new File(TradingConstants.GLOBALPATH + "breachMDev.txt");


    GuaranteeDevHandler(int id, ApiController ap) {
        primaryID = id;
        currentID = id;
        idStatusMap.put(id, OrderStatus.ConstructedInHandler);
        controller = ap;
    }


    private GuaranteeDevHandler(int prim, int id, ApiController ap) {
        primaryID = prim;
        currentID = id;
        idStatusMap.put(id, OrderStatus.ConstructedInHandler);
        controller = ap;
    }


    @Override
    public void orderState(OrderState orderState) {
        LocalDateTime now = LocalDateTime.now();
        if (devOrderMap.containsKey(currentID)) {
            devOrderMap.get(currentID).setFinalActionTime(LocalDateTime.now());
            devOrderMap.get(currentID).setAugmentedOrderStatus(orderState.status());
        } else {
            throw new IllegalStateException(" dev id order map doesn't contain ID" + currentID);
        }

        if (orderState.status() != idStatusMap.get(currentID)) {
            if (orderState.status() == Filled) {
                String msg = str(devOrderMap.get(primaryID).getOrder().orderId(),
                        devOrderMap.get(currentID).getOrder().orderId(),
                        "*GUARANTEE DEV FILL*", idStatusMap.get(currentID), "->", orderState.status(), now,
                        "ID:", currentID, devOrderMap.get(currentID),
                        "TIF:", devOrderMap.get(currentID).getOrder().tif());
                Utility.outputToSymbolFile(devOrderMap.get(primaryID).getSymbol(), msg, breachMDevOutput);
            }

            if (orderState.status() == PendingCancel && devOrderMap.get(currentID).getOrder().tif() == IOC) {
                Contract ct = devOrderMap.get(currentID).getContract();
                String symbol = devOrderMap.get(currentID).getSymbol();
                double lastPrice = BreachDevTrader.getLiveData(symbol);
                double bid = BreachDevTrader.getBid(symbol);
                double ask = BreachDevTrader.getAsk(symbol);

                Order prevOrder = devOrderMap.get(currentID).getOrder();
                Order o = new Order();
                o.action(prevOrder.action());
                o.lmtPrice(prevOrder.action() == Types.Action.BUY ? ask :
                        (prevOrder.action() == Types.Action.SELL ? bid : lastPrice));
                o.orderType(OrderType.LMT);
                o.totalQuantity(prevOrder.totalQuantity());
                o.outsideRth(true);
                o.tif(IOC);

                int newID = devTradeID.incrementAndGet();
                controller.placeOrModifyOrder(ct, o, new GuaranteeDevHandler(primaryID, newID, controller));
                devOrderMap.put(newID, new OrderAugmented(ct, LocalDateTime.now(), o,
                        devOrderMap.get(currentID).getOrderType(), false));

                Utility.outputToSymbolFile(devOrderMap.get(primaryID).getSymbol(),
                        str(devOrderMap.get(primaryID).getOrder().orderId(),
                                prevOrder.orderId(), "->", o.orderId(),
                                "BREACH RESUBMIT:", devOrderMap.get(newID).getOrderType(),
                                o.tif(), o.action(), o.lmtPrice(), o.totalQuantity(), "Primary? " +
                                        devOrderMap.get(newID).isPrimaryOrder(),
                                "current", devOrderMap.get(newID), "bid ask sp last"
                                , bid, ask, Math.round(10000d * (ask / bid - 1)), "bp", lastPrice), breachMDevOutput);
            }
            idStatusMap.put(currentID, orderState.status());
        }
    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId
            , int parentId, double lastFillPrice, int clientId, String whyHeld) {

    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        outputToError(str("ERROR:", "Guarantee Dev handler:", currentID, errorCode, errorMsg
                , devOrderMap.get(currentID)));
    }
}
