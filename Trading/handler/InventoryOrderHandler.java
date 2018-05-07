package handler;

import apidemo.XUTrader;
import apidemo.XuTraderHelper;
import client.OrderState;
import client.OrderStatus;
import controller.ApiController;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import static utility.Utility.getStr;

public class InventoryOrderHandler implements ApiController.IOrderHandler {


    private int defaultID;
    private CountDownLatch latch;
    private CyclicBarrier barrier;

    public InventoryOrderHandler(int i, CountDownLatch l, CyclicBarrier cb) {
        defaultID = i;
        latch = l;
        barrier = cb;
    }

    @Override
    public void orderState(OrderState orderState) {
        if (orderState.status() == OrderStatus.Filled) {
            XuTraderHelper.outputToAutoLog(
                    getStr("|| OrderState ||", defaultID, XUTrader.globalIdOrderMap.get(defaultID),
                            orderState.status()));
            if (latch.getCount() == 1) {
                latch.countDown();
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
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
