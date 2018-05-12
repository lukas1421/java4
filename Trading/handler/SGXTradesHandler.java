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
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import static historical.HistChinaStocks.chinaTradeMap;
import static utility.Utility.getStr;
import static utility.Utility.ibContractToSymbol;

public class SGXTradesHandler implements ApiController.ITradeReportHandler {
    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {
        String ticker = ibContractToSymbol(contract);

        int sign = (execution.side().equals("BOT")) ? 1 : -1;

        LocalDateTime ldt = LocalDateTime.parse(execution.time(), DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss"));
        LocalDateTime ldtRoundTo5 = Utility.roundTo5Ldt(ldt);

        if (contract.symbol().equals("XINA50")) {
            if (ldt.toLocalDate().isAfter(Utility.getMondayOfWeek(ldt).minusDays(1L))) {

                if (ldt.toLocalDate().equals(LocalDate.of(2018, Month.MAY, 11))) {
                    System.out.println(" ******************************************* ");
                    System.out.println(" SGXTradesHandler " + ticker);
                    System.out.println(getStr(" exec ", execution.side(), execution.time(), execution.cumQty()
                            , execution.price(), execution.shares()));
                }

                try {
                    if (chinaTradeMap.containsKey(ticker)) {
                        if (chinaTradeMap.get(ticker).containsKey(ldtRoundTo5)) {
                            System.out.println(getStr(" Existing Trade: ", ldtRoundTo5,
                                    sign * (int) Math.round(execution.shares())));
                            chinaTradeMap.get(ticker).get(ldtRoundTo5).addTrade(new FutureTrade(execution.price(),
                                    sign * (int) Math.round(execution.shares())));
                        } else {
                            System.out.println(getStr(" new tradeBlock ", ldtRoundTo5,
                                    sign * (int) Math.round(execution.shares())));
                            chinaTradeMap.get(ticker).put(ldtRoundTo5, new TradeBlock(new FutureTrade(execution.price(),
                                    sign * (int) Math.round(execution.shares()))));
                        }
                        if (ldtRoundTo5.toLocalDate().equals(LocalDate.of(2018, Month.MAY, 11))) {
                            System.out.println(getStr(LocalTime.now(), chinaTradeMap.get(ticker).get(ldtRoundTo5)));
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
                chinaTradeMap.get(ticker).put(LocalDateTime.of(LocalDate
                                .parse(TradingConstants.A50_LAST_EXPIRY, DateTimeFormatter.ofPattern("yyyyMMdd")),
                        LocalTime.of(15, 0)),
                        new TradeBlock(new FutureTrade(HistChinaStocks.futExpiryLevel, -1 * HistChinaStocks.futExpiryUnits)));
            }
            int sgxLotsTraded = chinaTradeMap.get(ticker).entrySet().stream()
                    .filter(e -> e.getKey().toLocalDate()
                            .isAfter(HistChinaStocks.MONDAY_OF_WEEK.minusDays(1L)))
                    .mapToInt(e -> e.getValue().getSizeAll()).sum();

            System.out.println(" sgx trades handler trade map " + chinaTradeMap.get(ticker));

            System.out.println(" abs trades by day " + chinaTradeMap.get(ticker).entrySet().stream()
                    .collect(Collectors.groupingBy(
                            e -> e.getKey().toLocalDate(), Collectors.summingInt(e -> e.getValue().getSizeAllAbs()))));

            System.out.println(" buy trades by day " + chinaTradeMap.get(ticker).entrySet().stream()
                    .collect(Collectors.groupingBy(
                            e -> e.getKey().toLocalDate(), Collectors.summingInt(e -> e.getValue().getSizeBot()))));

            System.out.println(" sell trades by day " + chinaTradeMap.get(ticker).entrySet().stream()
                    .collect(Collectors.groupingBy(
                            e -> e.getKey().toLocalDate(), Collectors.summingInt(e -> e.getValue().getSizeSold()))));

            System.out.println(" printing may 11 check trades ");
            chinaTradeMap.get(ticker).entrySet().stream()
                    .filter(e -> e.getKey().toLocalDate().equals(LocalDate.of(2018, Month.MAY, 11)))
                    .forEach(System.out::println);


            int sgxLotsBot = chinaTradeMap.get(ticker).entrySet().stream()
                    .filter(e -> e.getKey().toLocalDate()
                            .isAfter(HistChinaStocks.MONDAY_OF_WEEK.minusDays(1L)))
                    .mapToInt(e -> e.getValue().getSizeBot()).sum();

            int sgxLotsSold = chinaTradeMap.get(ticker).entrySet().stream()
                    .filter(e -> e.getKey().toLocalDate()
                            .isAfter(HistChinaStocks.MONDAY_OF_WEEK.minusDays(1L)))
                    .mapToInt(e -> e.getValue().getSizeSold()).sum();

            HistChinaStocks.wtdChgInPosition.put(ticker, sgxLotsTraded);
            HistChinaStocks.wtdBotPosition.put(ticker, sgxLotsBot);
            HistChinaStocks.wtdSoldPosition.put(ticker, sgxLotsSold);

            System.out.println(" sgx trades handler " + chinaTradeMap.get(ticker));

            System.out.println(" sgx trades map size "
                    + chinaTradeMap.get(ticker).entrySet().stream()
                    .mapToInt(e -> Math.abs(e.getValue().getSizeAll())).sum());


        }
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
    }
}
