package handler;

import client.Contract;
import controller.ApiController;
import historical.HistChinaStocks;

import javax.swing.*;

public class SGXPositionHandler implements ApiController.IPositionHandler {

        @Override
        public void position(String account, Contract contract, double position, double avgCost) {

            SwingUtilities.invokeLater(() -> {
                if (contract.symbol().equals("XINA50")) {
                    System.out.println(" current position is " + position);
                    HistChinaStocks.currentPositionMap.put("SGXA50", (int)position);
                }
            });
        }

    @Override
    public void positionEnd() {
        System.out.println(" position end ");
    }
}
