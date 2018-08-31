package client;

import TradeType.FutureTrade;
import util.AutoOrderType;

import java.time.LocalDateTime;

import static utility.Utility.str;

public class OrderAugmented {

    private final LocalDateTime orderTime;
    private Order order;
    private String msg;
    private final AutoOrderType orderType;
    private OrderStatus status;
    private LocalDateTime finalActionTime;

    public OrderAugmented(LocalDateTime t, Order o, String m, AutoOrderType tt) {
        orderTime = t;
        order = o;
        msg = m;
        orderType = tt;
        status = OrderStatus.Created;
        finalActionTime = LocalDateTime.MIN;
    }

    public double getPnl(double currPrice) {
        double tradedPrice = order.lmtPrice();
        double size = order.totalQuantity();
        if (order.action() == Types.Action.BUY) {
            return Math.round(100d * (currPrice - tradedPrice - FutureTrade.COST_PER_LOT) * size) / 100d;
        } else if (order.action() == Types.Action.SELL) {
            return Math.round(100d * (tradedPrice - currPrice - FutureTrade.COST_PER_LOT) * size) / 100d;
        }
        return 0.0;
    }

    public OrderAugmented(LocalDateTime t, Order o, AutoOrderType tt) {
        orderTime = t;
        order = o;
        msg = "";
        orderType = tt;
        status = OrderStatus.Created;
        finalActionTime = LocalDateTime.MIN;
    }

    public void setMsg(String m) {
        msg = m;
    }

    public OrderAugmented() {
        orderTime = LocalDateTime.MIN;
        order = new Order();
        msg = "";
        orderType = AutoOrderType.UNKNOWN;
        status = OrderStatus.Created;
        finalActionTime = LocalDateTime.MIN;
    }

    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    public Order getOrder() {
        return order;
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

    public void setStatus(OrderStatus s) {
        status = s;
    }

    public OrderStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return str("T: ", orderTime.toLocalTime(),
                "Order:", order, "msg:", msg, "Tradetype", orderType,
                //order.orderId() == 0 ? "" : order.orderId(),
                "Status:", status);
    }
}
