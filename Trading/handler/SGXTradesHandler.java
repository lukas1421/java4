package handler;

import TradeType.FutureTrade;
import TradeType.TradeBlock;
import apidemo.FutType;
import apidemo.TradingConstants;
import client.CommissionReport;
import client.Contract;
import client.Execution;
import controller.ApiController;
import historical.HistChinaStocks;
import utility.Utility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static utility.Utility.ibContractToSymbol;

public class SGXTradesHandler implements ApiController.ITradeReportHandler {
    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {
        String ticker = ibContractToSymbol(contract);
        System.out.println(" ******************************************* ");
        System.out.println(" SGXTradesHandler " + ticker);
        int sign = (execution.side().equals("BOT")) ? 1 : -1;

        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));

        LocalDateTime ldtRoundTo5 = Utility.roundTo5Ldt(ldt);

        if (contract.symbol().equals("XINA50")) {
            if (ldt.toLocalDate().isAfter(Utility.getMondayOfWeek(ldt).minusDays(1L))) {
                System.out.println(" exec " + execution.side() + "　" + execution.time() + " " + execution.cumQty()
                        + " " + execution.price() + " " + execution.shares());

                try {
                    if (HistChinaStocks.chinaTradeMap.containsKey(ticker)) {
                        if (HistChinaStocks.chinaTradeMap.get(ticker).containsKey(ldtRoundTo5)) {
                            System.out.println(" Existing Trade: " + ldtRoundTo5);
                            HistChinaStocks.chinaTradeMap.get(ticker).get(ldtRoundTo5).addTrade(new FutureTrade(execution.price(),
                                    sign * execution.cumQty()));
                        } else {
                            System.out.println(" new tradeBlock " + ldtRoundTo5);
                            HistChinaStocks.chinaTradeMap.get(ticker).put(ldtRoundTo5, new TradeBlock(new FutureTrade(execution.price(),
                                    sign * execution.cumQty())));
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

    @SuppressWarnings("SpellCheckingInspection")
    @Override
    public void tradeReportEnd() {
        System.out.println("SGXTradeshandler :: trade report end ");

        for (FutType f : FutType.values()) {
            System.out.println(" type is " + f + " ticker is " + f.getTicker());

            String ticker = f.getTicker();

            if (ticker.equalsIgnoreCase("SGXA50PR")) {
                HistChinaStocks.chinaTradeMap.get(ticker).put(LocalDateTime.of(LocalDate.parse(TradingConstants.A50_LAST_EXPIRY,DateTimeFormatter.ofPattern("yyyyMMdd")),
                        LocalTime.of(15,0)), new TradeBlock(new FutureTrade(HistChinaStocks.futExpiryLevel, -1 * HistChinaStocks.futExpiryUnits)));
            }

            int sgxLotsTraded = HistChinaStocks.chinaTradeMap.get(ticker).entrySet().stream()
                    .filter(e -> e.getKey().toLocalDate()
                            .isAfter(HistChinaStocks.MONDAY_OF_WEEK.minusDays(1L)))
                    .mapToInt(e -> e.getValue().getSizeAll()).sum();

            HistChinaStocks.wtdChgInPosition.put(ticker, sgxLotsTraded);
        }
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
    }
}
