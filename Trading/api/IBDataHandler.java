package api;

import client.TickType;
import controller.ApiController;
import handler.LiveHandler;
import historical.Request;
import utility.TradingUtility;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static utility.Utility.ibContractToSymbol;
import static utility.Utility.pr;

public class IBDataHandler {

    public static void tickPrice(int reqId, int tickType, double price, int canAutoExecute) {
        if (TradingUtility.globalRequestMap.containsKey(reqId)) {
            Request r = TradingUtility.globalRequestMap.get(reqId);
            LiveHandler lh = (LiveHandler) TradingUtility.globalRequestMap.get(reqId).getHandler();
            try {
                lh.handlePrice(TickType.get(tickType), r.getContract(), price,
                        LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
            } catch (Exception ex) {
                pr(" handling price has issues ");
                ex.printStackTrace();
            }
        }
    }

    public static void tickSize(int reqId, int tickType, int size) {
        if (TradingUtility.globalRequestMap.containsKey(reqId)) {
            Request r = TradingUtility.globalRequestMap.get(reqId);
            LiveHandler lh = (LiveHandler) r.getHandler();
            lh.handleVol(TickType.get(tickType), ibContractToSymbol(r.getContract()), size,
                    LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        }
    }

    public static void tickGeneric(int reqId, int tickType, double value) {
        if (TradingUtility.globalRequestMap.containsKey(reqId)) {
            Request r = TradingUtility.globalRequestMap.get(reqId);
            LiveHandler lh = (LiveHandler) r.getHandler();
            lh.handleGeneric(TickType.get(tickType), ibContractToSymbol(r.getContract()), value,
                    LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        }
    }


}