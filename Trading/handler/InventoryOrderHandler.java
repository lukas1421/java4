package handler;

import apidemo.XUTrader;
import apidemo.XuTraderHelper;
import client.OrderState;
import client.OrderStatus;
import controller.ApiController;

import java.time.LocalDateTime;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import static apidemo.XUTrader.globalIdOrderMap;
import static utility.Utility.getStr;

public class InventoryOrderHandler implements ApiController.IOrderHandler {


    private int defaultID;
    private CountDownLatch latch;
    private CyclicBarrier barrier;

    public InventoryOrderHandler(int i, CountDownLatch l, CyclicBarrier cb) {
        //System.out.println(getStr(" constructing inventory handler ", i, l, cb));
        defaultID = i;
        latch = l;
        barrier = cb;
    }

    @Override
    public void orderState(OrderState orderState) {

        globalIdOrderMap.get(defaultID).setStatus(orderState.status());
        globalIdOrderMap.get(defaultID).setFinalActionTime(LocalDateTime.now());

        if (orderState.status() == OrderStatus.Filled) {
            globalIdOrderMap.get(defaultID).setFinalActionTime(LocalDateTime.now());
            String msg = getStr("|| OrderState ||", defaultID, globalIdOrderMap.get(defaultID),
                    orderState.status());
            XuTraderHelper.outputToAutoLog(msg);
            XuTraderHelper.outputPurelyOrders(msg);
            if (latch.getCount() == 1) {
                System.out.println(" counting down latch ");
                latch.countDown();

                CompletableFuture.runAsync(() -> {
                    try {
                        System.out.println(" barrier waiting BEFORE #" + barrier.getNumberWaiting());
                        barrier.await();
                        System.out.println(" barrier waiting AFTER #" + barrier.getNumberWaiting());
                    } catch (InterruptedException | BrokenBarrierException e) {
                        barrier.reset();
                        e.printStackTrace();
                    }
                });
            }
            System.out.println(" order state filled ends");
        } else if (orderState.status() == OrderStatus.Cancelled || orderState.status() == OrderStatus.ApiCancelled) {
            globalIdOrderMap.get(defaultID).setFinalActionTime(LocalDateTime.now());
            XuTraderHelper.outputToAutoLog(getStr(" order cancelled ", defaultID,
                    XUTrader.globalIdOrderMap.get(defaultID).getOrder().orderId(),
                    XUTrader.globalIdOrderMap.get(defaultID).getOrder()));
        }
    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        System.out.println(" in orderStatus Inventory Order handler  ");
        System.out.println(getStr(" status filled remained avgFillprice permId parentID, lastFill, clientID, whyheld "
                , status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld));
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        //System.out.println(getStr(" handling error in inventoryOrderhandle ", errorCode, errorMsg));
    }

    @Override
    public String toString() {
        return getStr(" inventory handler for ", defaultID, XUTrader.globalIdOrderMap.get(defaultID));
    }
}
