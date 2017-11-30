package handler;

import TradeType.FutureTrade;
import TradeType.Trade;
import apidemo.FutType;
import client.CommissionReport;
import client.Contract;
import client.Execution;
import controller.ApiController;
import historical.HistChinaStocks;
import utility.Utility;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static utility.Utility.getStr;
import static utility.Utility.ibContractToSymbol;

public class SGXReportHandler implements ApiController.ITradeReportHandler {


    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {
        System.out.println(" SGXReportHandler in trade report " + contract.symbol());
        String ticker = ibContractToSymbol(contract);

        int sign = (execution.side().equals("BOT")) ? 1 : -1;

        System.out.println(LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss")));
        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));

        LocalDateTime ldtRoundto5 = Utility.roundTo5Ldt(ldt);

        if (contract.symbol().equals("XINA50")) {
            if (ldt.toLocalDate().isAfter(Utility.getMondayOfWeek(ldt).minusDays(1L))) {
                System.out.println(" exec " + execution.side() + "　" + execution.time() + " " + execution.cumQty()
                        + " " + execution.price() + " " + execution.orderRef() + " " + execution.orderId() + " " + execution.permId() + " "
                        + execution.shares());
                System.out.println(" time string " + ldt.toString());
                System.out.println(" time is " + ldt.toLocalTime());
                System.out.println(" day is " + LocalDateTime.now().getDayOfMonth());
                try {
                    if(HistChinaStocks.chinaTradeMap.containsKey(ticker)) {
                        if (HistChinaStocks.chinaTradeMap.get(ticker).containsKey(ldtRoundto5)) {
                            System.out.println(" lt is " + ldtRoundto5);
                            ((Trade) HistChinaStocks.chinaTradeMap.get(ticker).get(ldtRoundto5)).merge(new FutureTrade(execution.price(),
                                    sign * execution.cumQty()));
                        } else {
                            System.out.println(" else lt " + ldtRoundto5);
                            HistChinaStocks.chinaTradeMap.get(ticker).put(ldtRoundto5, new FutureTrade(execution.price(), sign * execution.cumQty()));
                        }
                    } else {
                        System.out.println(" sgx trade handler does not contain ticker for " + ticker);
                    }


                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.out.println(" ticker wrong in sgx report handler " + ticker + " wrong contract is " + contract.toString());
                }
            }
        } else {
            System.out.println(" do not recognize this ticker " + contract.symbol());
        }
    }

    @Override
    public void tradeReportEnd() {
        System.out.println(" trade report end begins ");

        for (FutType f : FutType.values()) {
            System.out.println(" type is " + f + " ticker is " + f.getTicker());

            String ticker = f.getTicker();

            int sgxLotsTraded = HistChinaStocks.chinaTradeMap.get(ticker).entrySet().stream().filter(e -> e.getKey().toLocalDate()
                    .isAfter(HistChinaStocks.MONDAY_OF_WEEK.minusDays(1L)))
                    .mapToInt(e -> ((Trade) e.getValue()).getSizeAll()).sum();

            System.out.println(" trade report end / name / trade map " + ticker + " " + HistChinaStocks.chinaTradeMap.get(ticker));
            System.out.println(getStr(" trade report end / ticker / traded ", ticker, sgxLotsTraded));

            HistChinaStocks.wtdChgInPosition.put(ticker, sgxLotsTraded);

            System.out.println(" trade report end");
        }
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
        System.out.println("commission report");

    }
}
