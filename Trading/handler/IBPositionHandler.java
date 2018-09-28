package handler;

import apidemo.ChinaPosition;
import client.Contract;
import controller.ApiController;

import static utility.Utility.ibContractToSymbol;

public class IBPositionHandler implements ApiController.IPositionHandler {

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        String symbol = ibContractToSymbol(contract);
        ChinaPosition.currentPositionMap.put(symbol, (int) position);
    }

    @Override
    public void positionEnd() {
    }

}
