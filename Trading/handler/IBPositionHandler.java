package handler;

import apidemo.ChinaPosition;
import client.Contract;
import controller.ApiController;

import static utility.Utility.ibContractToSymbol;

public class IBPositionHandler implements ApiController.IPositionHandler {

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
//        pr("acct, contract, position, avg cost ", account, contract.symbol(),
//                Optional.ofNullable(contract.exchange()), Optional.ofNullable(contract.currency())
//                , Optional.ofNullable(contract.secType()), position, avgCost);

        String symbol = ibContractToSymbol(contract);
        ChinaPosition.currentPositionMap.put(symbol, (int) position);
    }

    @Override
    public void positionEnd() {
//        ChinaPosition.currentPositionMap.forEach((k, v) -> {
//            if (v != 0.0) {
//                pr(" printing current position ", k, v);
//            }
//        });
    }

}
