package handler;

import apidemo.ChinaPosition;
import client.Contract;
import controller.ApiController;

public class FutPositionHandler implements ApiController.IPositionHandler {

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {

        String ticker = utility.Utility.ibContractToSymbol(contract);

        if(ticker.equals("SGXA50")) {
            System.out.println(" XU position is " + position);
            ChinaPosition.xuCurrentPosition = (int) position;
        }

//        if (contract.symbol().equals("XINA50")) {
//            System.out.println(" XU position is " + position);
//            ChinaPosition.xuCurrentPosition = (int) position;
//        }
    }

    @Override
    public void positionEnd() {
        System.out.println(" position request ended");
    }

}
