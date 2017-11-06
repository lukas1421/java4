package handler;

import TradeType.FutureTrade;
import TradeType.Trade;
import client.CommissionReport;
import client.Contract;
import client.Execution;
import controller.ApiController;
import historical.HistChinaStocks;
import utility.Utility;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SGXReportHandler implements ApiController.ITradeReportHandler {

    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {
        System.out.println(" in trade report " + contract.toString());
        int sign = (execution.side().equals("BOT")) ? 1 : -1;
        System.out.println(LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss")));
        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));
        LocalDateTime ldtRoundto5 = Utility.roundTo5Ldt(ldt);

        if (ldt.toLocalDate().isAfter(Utility.getMondayOfWeek(ldt).minusDays(1L))) {
//            System.out.println(" exec " + execution.side() + "　" + execution.time() + " " + execution.cumQty()
//                    + " " + execution.price() + " " + execution.orderRef() + " " + execution.orderId() + " " + execution.permId() + " "
//                    + execution.shares());
//            System.out.println(" time string " + ldt.toString());
//            System.out.println(" time is " + ldt.toLocalTime());
//            System.out.println(" day is " + LocalDateTime.now().getDayOfMonth());

            if (HistChinaStocks.chinaTradeMap.get("SGXA50").containsKey(ldtRoundto5)) {
                System.out.println(" lt is " + ldtRoundto5);
                ((Trade) HistChinaStocks.chinaTradeMap.get("SGXA50").get(ldtRoundto5)).merge(new FutureTrade(execution.price(),
                        sign * execution.cumQty()));
            } else {
                System.out.println(" else lt " + ldtRoundto5);
                HistChinaStocks.chinaTradeMap.get("SGXA50").put(ldtRoundto5, new FutureTrade(execution.price(), sign * execution.cumQty()));
            }
        }
    }

    @Override
    public void tradeReportEnd() {
        int sgxLotsTraded = HistChinaStocks.chinaTradeMap.get("SGXA50").entrySet().stream()
                .mapToInt(e->((Trade)e.getValue()).getSizeAll()).sum();

        HistChinaStocks.wtdChgInPosition.put("SGXA50", sgxLotsTraded);
        System.out.println(" trade report end");
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
        System.out.println("commission report");

    }
}
