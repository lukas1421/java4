package handler;

import api.BreachDevTrader;
import api.TradingConstants;
import client.*;
import controller.ApiController;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static api.AutoTraderMain.autoTradeID;
import static api.AutoTraderMain.globalIdOrderMap;
import static api.XuTraderHelper.*;
import static client.OrderStatus.Filled;
import static client.OrderStatus.PendingCancel;
import static client.Types.TimeInForce.IOC;
import static utility.Utility.str;

public class GuaranteeDevHandler implements ApiController.IOrderHandler {

    private static Map<Integer, OrderStatus> idStatusMap = new ConcurrentHashMap<>();
    private int primaryID;
    private int currentID;
    private ApiController controller;
    private static File breachMDevOutput = new File(TradingConstants.GLOBALPATH + "breachMDev.txt");


    public GuaranteeDevHandler(int id, ApiController ap) {
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
        if (globalIdOrderMap.containsKey(currentID)) {
            globalIdOrderMap.get(currentID).setFinalActionTime(LocalDateTime.now());
            globalIdOrderMap.get(currentID).setAugmentedOrderStatus(orderState.status());
        } else {
            throw new IllegalStateException(" global id order map doesn't contain ID" + currentID);
        }

        if (orderState.status() != idStatusMap.get(currentID)) {
            if (orderState.status() == Filled) {
                String msg = str(globalIdOrderMap.get(primaryID).getOrder().orderId(),
                        globalIdOrderMap.get(currentID).getOrder().orderId(),
                        "*GUARANTEE DEV FILL*", idStatusMap.get(currentID), "->", orderState.status(), now,
                        "ID:", currentID, globalIdOrderMap.get(currentID),
                        "TIF:", globalIdOrderMap.get(currentID).getOrder().tif());
                outputToSymbolFile(globalIdOrderMap.get(primaryID).getSymbol(), msg, breachMDevOutput);
            }

            if (orderState.status() == PendingCancel && globalIdOrderMap.get(currentID).getOrder().tif() == IOC) {
                Contract ct = globalIdOrderMap.get(currentID).getContract();
                String symbol = globalIdOrderMap.get(currentID).getSymbol();
                double lastPrice = BreachDevTrader.getLiveData(symbol);
                double bid = BreachDevTrader.getBid(symbol);
                double ask = BreachDevTrader.getAsk(symbol);

                Order prevOrder = globalIdOrderMap.get(currentID).getOrder();
                Order o = new Order();
                o.action(prevOrder.action());
                o.lmtPrice(prevOrder.action() == Types.Action.BUY ? ask :
                        (prevOrder.action() == Types.Action.SELL ? bid : lastPrice));
                o.orderType(OrderType.LMT);
                o.totalQuantity(prevOrder.totalQuantity());
                o.outsideRth(true);
                o.tif(IOC);

                int newID = autoTradeID.incrementAndGet();
                controller.placeOrModifyOrder(ct, o, new GuaranteeDevHandler(primaryID, newID, controller));
                globalIdOrderMap.put(newID, new OrderAugmented(ct, LocalDateTime.now(), o,
                        globalIdOrderMap.get(currentID).getOrderType(), false));

                outputToSymbolFile(globalIdOrderMap.get(primaryID).getSymbol(),
                        str(globalIdOrderMap.get(primaryID).getOrder().orderId(),
                                prevOrder.orderId(), "->", o.orderId(),
                                "BREACH RESUBMIT:", globalIdOrderMap.get(newID).getOrderType(),
                                o.tif(), o.action(), o.lmtPrice(), o.totalQuantity(), "Primary? " +
                                        globalIdOrderMap.get(newID).isPrimaryOrder(),
                                "current", globalIdOrderMap.get(newID), "bid ask sp last"
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
                , globalIdOrderMap.get(currentID)));
    }
}
