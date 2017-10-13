package historical;

import handler.GeneralHandler;
import client.Contract;
import handler.HistDataConsumer;

public class Request {

    private Contract contract;
    private GeneralHandler handler;
    private HistDataConsumer<String, Double, Integer> dataConsumer;
    private boolean customHandlingNeeded = false;

    public Request(Contract ct, GeneralHandler h) {
        contract = ct;
        handler = h;
        dataConsumer = null;
        customHandlingNeeded = false;
    }

    public Request(Contract ct, HistDataConsumer<String, Double, Integer> dc) {
        contract = ct;
        handler = null;
        dataConsumer = dc;
        customHandlingNeeded = true;
    }

    public Contract getContract() {
        return contract;
    }

    public GeneralHandler getHandler() {
        return handler;
    }

    public HistDataConsumer<String, Double, Integer> getDataConsumer() {
        if (customHandlingNeeded) {
            return dataConsumer;
        } else {
            return null;
        }
    }

    public boolean getCustomFunctionNeeded() {
        return customHandlingNeeded;
    }

    @Override
    public String toString() {
        return contract.toString() + " " + handler.toString()
                + " custom handling needed " + getCustomFunctionNeeded();
    }
}
