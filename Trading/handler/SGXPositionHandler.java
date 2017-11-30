package handler;

import client.Contract;
import controller.ApiController;
import historical.HistChinaStocks;

import javax.swing.*;

public class SGXPositionHandler implements ApiController.IPositionHandler {

        @Override
        public void position(String account, Contract contract, double position, double avgCost) {

            String ticker = utility.Utility.ibContractToSymbol(contract);


            SwingUtilities.invokeLater(() -> {
                HistChinaStocks.currentPositionMap.put(ticker, (int) position);
//                if (contract.symbol().equals("XINA50")) {
//                    if(contract.lastTradeDateOrContractMonth().equals(TradingConstants.GLOBALA50FRONTEXPIRY)) {
//                        System.out.println(" current position SGXA50 is " + position);
//                        HistChinaStocks.currentPositionMap.put("SGXA50", (int) position);
//                    } else if(contract.lastTradeDateOrContractMonth().equals(TradingConstants.GLOBALA50BACKEXPIRY)) {
//                        System.out.println(" current position SGXA50BM is " + position);
//                        HistChinaStocks.currentPositionMap.put("SGXA50BM", (int) position);
//                    }
//                }
            });
        }

    @Override
    public void positionEnd() {
        //System.out.println(" position end ");
    }
}
