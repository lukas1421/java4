package handler;

import apidemo.ChinaPosition;
import client.Contract;
import controller.ApiController;

import java.util.Optional;

import static utility.Utility.ibContractToSymbol;
import static utility.Utility.pr;

public class IBPositionHandler implements ApiController.IPositionHandler {

    @Override
    public void position(String account, Contract contract, double position, double avgCost) {
        pr("acct, contract, position, avg cost ", account, contract.symbol(),
                Optional.ofNullable(contract.exchange()), Optional.ofNullable(contract.currency())
                , Optional.ofNullable(contract.secType()), position, avgCost);

        String ticker = ibContractToSymbol(contract);
        ChinaPosition.currentPositionMap.put(ticker, (int) position);
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
