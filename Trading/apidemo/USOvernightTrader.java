package apidemo;

import controller.ApiConnection;
import controller.ApiController;

import javax.swing.*;
import java.util.ArrayList;

public class USOvernightTrader {

    static boolean connectionStatus = true;
    static JLabel connectionLabel;
    static ApiController apcon = new ApiController(new USConnectionHandler(),
            new ApiConnection.ILogger.DefaultLogger(), new ApiConnection.ILogger.DefaultLogger());



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