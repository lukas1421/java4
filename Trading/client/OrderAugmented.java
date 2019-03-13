package client;

import TradeType.FutureTrade;
import net.bytebuddy.pool.TypePool;
import util.AutoOrderType;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static utility.Utility.ibContractToSymbol;
import static utility.Utility.str;

public class OrderAugmented {

    private Contract contract;
    //private String symbol;
    private final LocalDateTime orderTime;
    private Order order;
    private String msg;
    private final AutoOrderType orderType;
    private OrderStatus augmentedOrderStatus;
    private AtomicBoolean primaryOrder = new AtomicBoolean(false);
    private LocalDateTime finalActionTime;


    public OrderAugmented(Contract ct, LocalDateTime t, Order o, String m, AutoOrderType tt) {
        contract = ct;
        orderTime = t;
        order = o;
        msg = m;
        orderType = tt;
        augmentedOrderStatus = OrderStatus.Created;
        finalActionTime = LocalDateTime.MIN;
        primaryOrder.set(true);
    }

    public OrderAugmented(Contract ct, LocalDateTime t, Order o, AutoOrderType tt) {
        contract = ct;
        orderTime = t;
        order = o;
        msg = "";
        orderType = tt;
        augmentedOrderStatus = OrderStatus.Created;
        finalActionTime = LocalDateTime.MIN;
        primaryOrder.set(true);
    }

    public OrderAugmented(Contract ct, LocalDateTime t, Order o, AutoOrderType tt, boolean primary) {
        contract = ct;
        orderTime = t;
        order = o;
        msg = "";
        orderType = tt;
        augmentedOrderStatus = OrderStatus.Created;
        finalActionTime = LocalDateTime.MIN;
        primaryOrder.set(primary);
    }

    public double getPnl(String symb, double currPrice) {
        double costPerUnit = 0.0;
        if (symb.startsWith("SGXA50")) {
            costPerUnit = FutureTrade.COST_PER_LOT;
        }

        double tradedPrice = order.lmtPrice();
        double size = order.totalQuantity();
        if (order.action() == Types.Action.BUY) {
            return Math.round(100d * (currPrice - tradedPrice - costPerUnit) * size) / 100d;
        } else if (order.action() == Types.Action.SELL) {
            return Math.round(100d * (tradedPrice - currPrice - costPerUnit) * size) / 100d;
        }
        return 0.0;
    }


    public boolean isPrimaryOrder() {
        return primaryOrder.get();
    }

    public void setMsg(String m) {
        msg = m;
    }

    public OrderAugmented() {
        contract = new Contract();
        orderTime = LocalDateTime.MIN;
        order = new Order();
        msg = "";
        orderType = AutoOrderType.UNKNOWN;
        augmentedOrderStatus = OrderStatus.Created;
        finalActionTime = LocalDateTime.MIN;
        primaryOrder.set(true);
    }

    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    public Order getOrder() {
        return order;
    }

    public Contract getContract() {
        return contract;
    }

    public String getSymbol() {
        if (!contract.symbol().equals("")) {
            return ibContractToSymbol(contract);
        }
        return "";
    }

    public String getMsg() {
        return msg;
    }

    public AutoOrderType getOrderType() {
        return orderType;
    }

    public void setFinalActionTime(LocalDateTime t) {
        finalActionTime = t;
    }

    public void setAugmentedOrderStatus(OrderStatus s) {
        augmentedOrderStatus = s;
    }

    public OrderStatus getAugmentedOrderStatus() {
        return augmentedOrderStatus;
    }

    @Override
    public String toString() {
        return str(ibContractToSymbol(contract), "T: ", orderTime,
                "Order:", order, "msg:", msg, "Trade type", orderType,
                "Status:", augmentedOrderStatus, "||");
    }
}
