package apidemo;

import client.*;
import controller.ApiConnection;
import controller.ApiController;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class USOvernightTrader extends JPanel {

    static boolean connectionStatus = true;
    static JLabel connectionLabel;
    static ApiController apcon = new ApiController(new USConnectionHandler(),
            new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());





    public static void main(String[] args) {

        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1000,1000));
        USOvernightTrader uso = new USOvernightTrader();
        jf.add(uso);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);




    }

}

class USConnectionHandler implements ApiController.IConnectionHandler {

    @Override
    public void connected() {
        System.out.println("connected in US Connection handler");
        USOvernightTrader.connectionStatus = true;
        USOvernightTrader.connectionLabel.setText(Boolean.toString(USOvernightTrader.connectionStatus));
        USOvernightTrader.apcon.setConnectionStatus(true);
    }

    @Override
    public void disconnected() {
        System.out.println("disconnected in XUConnectionHandler");
        USOvernightTrader.connectionStatus = false;
        USOvernightTrader.connectionLabel.setText(Boolean.toString(USOvernightTrader.connectionStatus));
    }

    @Override
    public void accountList(ArrayList<String> list) {
        System.out.println(" account list is " + list);
    }

    @Override
    public void error(Exception e) {
        System.out.println(" error in XUConnectionHandler");
        e.printStackTrace();
    }

    @Override
    public void message(int id, int errorCode, String errorMsg) {
        System.out.println(" error ID " + id + " error code " + errorCode + " errormsg " + errorMsg);
    }

    @Override
    public void show(String string) {
        System.out.println(" show string " + string);
    }
}

class USOrderHandler implements ApiController.IOrderHandler {

    @Override
    public void orderState(OrderState orderState) {

    }

    @Override
    public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice,
                            long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {

    }

    @Override
    public void handle(int errorCode, String errorMsg) {

    }
}

class USPositionHandler implements ApiController.IPositionHandler {

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {

    }

    @Override
    public void positionEnd() {

    }
}

class USTradeDefaultHandler implements ApiController.ITradeReportHandler {

    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {

    }

    @Override
    public void tradeReportEnd() {
        System.out.println(" trade report ended ");

    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {

    }
}

class USLiveOrderHandler implements ApiController.ILiveOrderHandler {

    @Override
    public void openOrder(Contract contract, Order order, OrderState orderState) {


    }

    @Override
    public void openOrderEnd() {

    }

    @Override
    public void orderStatus(int orderId, OrderStatus status, int filled, int remaining, double avgFillPrice,
                            long permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {

    }

    @Override
    public void handle(int orderId, int errorCode, String errorMsg) {

    }
}