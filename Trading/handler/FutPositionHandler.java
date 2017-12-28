package handler;

import apidemo.ChinaPosition;
import client.Contract;
import controller.ApiController;

public class FutPositionHandler implements ApiController.IPositionHandler {

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        String ticker = utility.Utility.ibContractToSymbol(contract);
        ChinaPosition.currentPositionMap.put(ticker, (int) position);
    }

    @Override
    public void positionEnd() {
    }

}
