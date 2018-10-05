package apidemo;

import client.*;
import controller.ApiController;
import handler.GuaranteeUSHandler;
import util.AutoOrderType;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static apidemo.AutoTraderMain.*;
import static apidemo.ChinaData.priceMapBarDetail;
import static apidemo.XuTraderHelper.*;
import static client.Types.TimeInForce.DAY;
import static client.Types.TimeInForce.IOC;
import static java.time.temporal.ChronoUnit.SECONDS;
import static util.AutoOrderType.*;
import static utility.Utility.*;

//import static apidemo.AutoTraderXU.*;

public class AutoTraderUS {

    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalDateTime, Double>> usPriceMapDetail
            = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> usBidMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> usAskMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> usOpenMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> usFreshPriceMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> usShortableValueMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> usOpenDevDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> usHiloDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> usPMOpenDevDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, Direction> usPMHiloDirection = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualUSDevMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualUSHiloMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualUSPMDevMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualUSPMHiloMap = new ConcurrentHashMap<>();


//    private static volatile EnumMap<HalfHour, AtomicBoolean> manualUSHalfHourDev = new EnumMap<>(HalfHour.class);
//    private static volatile EnumMap<HalfHour, Direction> hHrUSDevDirection = new EnumMap<>(HalfHour.class);

    private static volatile Map<String, EnumMap<HalfHour, AtomicBoolean>> manualUSHalfHourDev = new ConcurrentHashMap<>();
    private static volatile Map<String, EnumMap<HalfHour, Direction>> hHrUSDevDirection = new ConcurrentHashMap<>();

    private static volatile Map<String, EnumMap<QuarterHour, AtomicBoolean>> manualUSQuarterHourDev
            = new ConcurrentHashMap<>();
    private static volatile Map<String, EnumMap<QuarterHour, Direction>> qHrUSDevDirection
            = new ConcurrentHashMap<>();


    private static final double US_MIN_SHORT_LEVEL = 1.5;

    private static final int MAX_US_ORDERS = 4;
    private static final int MAX_US_HALFHOUR_ORDERS = 2;
    private static final int MAX_US_QUARTERHOUR_ORDERS = 2;
    public static List<String> usSymbols = new ArrayList<>();
    private static final double US_BASE_SIZE = 100;
    private static final double US_SAFETY_RATIO = 0.02;

    //profit taker
    private static double upThresh = 0.02;
    private static double downThresh = -0.02;
    private static double retreatUpThresh = upThresh * 0.3;
    private static double retreatDownThresh = downThresh * 0.3;

    public static Contract tickerToUSContract(String ticker) {
        Contract ct = new Contract();
        ct.symbol(ticker);
        ct.exchange("SMART");
        ct.currency("USD");
        ct.secType(Types.SecType.STK);
        return ct;
    }

    AutoTraderUS() {
        //usSymbols.add(ibContractToSymbol(tickerToUSContract("NIO")));
        //usSymbols.add(ibContractToSymbol(tickerToUSContract("QTT")));
        //usSymbols.add(ibContractToSymbol(tickerToUSContract("PDD")));
        usSymbols.add(ibContractToSymbol(tickerToUSContract("NBEV")));

        usSymbols.forEach(s -> {
            manualUSHalfHourDev.put(s, new EnumMap<>(HalfHour.class));
            hHrUSDevDirection.put(s, new EnumMap<>(HalfHour.class));
            manualUSQuarterHourDev.put(s, new EnumMap<>(QuarterHour.class));
            qHrUSDevDirection.put(s, new EnumMap<>(QuarterHour.class));


            for (HalfHour h : HalfHour.values()) {
                manualUSHalfHourDev.get(s).put(h, new AtomicBoolean(false));
                hHrUSDevDirection.get(s).put(h, Direction.Flat);
            }

            for (QuarterHour q : QuarterHour.values()) {
                manualUSQuarterHourDev.get(s).put(q, new AtomicBoolean(false));
                qHrUSDevDirection.get(s).put(q, Direction.Flat);
            }


            if (!priceMapBarDetail.containsKey(s)) {
                priceMapBarDetail.put(s, new ConcurrentSkipListMap<>());
            }
            usBidMap.put(s, 0.0);
            usAskMap.put(s, 0.0);
            usOpenMap.put(s, 0.0);
            usFreshPriceMap.put(s, 0.0);
            usShortableValueMap.put(s, 0.0);

            manualUSDevMap.put(s, new AtomicBoolean(false));
            usOpenDevDirection.put(s, Direction.Flat);

            manualUSHiloMap.put(s, new AtomicBoolean(false));
            usHiloDirection.put(s, Direction.Flat);

            manualUSPMDevMap.put(s, new AtomicBoolean(false));
            usPMOpenDevDirection.put(s, Direction.Flat);

            manualUSPMHiloMap.put(s, new AtomicBoolean(false));
            usPMHiloDirection.put(s, Direction.Flat);
        });
    }

    /**
     * process US traders
     *
     * @param symbol     stock name
     * @param nowMilli   time
     * @param freshPrice last price
     */
    public static void processMainUS(String symbol, LocalDateTime nowMilli, double freshPrice) {

        //cancelAllOrdersAfterDeadline(nowMilli.toLocalTime(), ltof(10, 0, 0));
        //cancelAllOrdersAfterDeadline(nowMilli.toLocalTime(), ltof(12, 30, 0));


        if (globalTradingOn.get()) {
            usQuarterHourDevTrader(symbol, nowMilli, freshPrice);
            //usRelativeProfitTaker(symbol, nowMilli, freshPrice);
            //usOpenDeviationTrader(symbol, nowMilli, freshPrice);
            //usHalfHourDevTrader(symbol, nowMilli, freshPrice);
        }
        usPostCutoffLiqTrader(symbol, nowMilli, freshPrice);
        usCloseLiqTrader(symbol, nowMilli, freshPrice);

        //usPMOpenDeviationTrader(symbol, nowMilli, freshPrice);
        //usPMHiloTrader(symbol, nowMilli, freshPrice);
        //usPostPMCutoffLiqTrader(symbol, nowMilli, freshPrice);
    }

    /**
     * quarter hour trader
     *
     * @param symbol     symbol
     * @param nowMilli   time now
     * @param freshPrice price
     */
    private static void usQuarterHourDevTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = tickerToUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);

        if (lt.isBefore(LocalTime.of(9, 29, 59)) || lt.isAfter(ltof(11, 0, 0))) {
            return;
        }

        LocalTime qHrStart = ltof(lt.getHour(), minuteToQuarterHour(lt.getMinute()), 0);
        double qHrOpen = prices.ceilingEntry(qHrStart).getValue();
        LocalTime quarterHourOpenTime = prices.ceilingEntry(qHrStart).getKey();

        QuarterHour q = QuarterHour.get(qHrStart);
        AutoOrderType ot = getOrderTypeByQuarterHour(q);
        LocalTime lastKey = prices.lastKey();

        double maxSoFar = prices.entrySet().stream().filter(e -> e.getKey().isAfter(qHrStart))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minSoFar = prices.entrySet().stream().filter(e -> e.getKey().isAfter(qHrStart))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);


        long qHrOrderNum = getOrderSizeForTradeType(symbol, ot);
        double qHrFilled = getFilledForType(symbol, ot);

        if (!manualUSQuarterHourDev.get(symbol).get(q).get()) {
            if (lt.isBefore(q.getStartTime().plusMinutes(1L))) {
                outputDetailedUS(symbol, str(" set US qhr dev direction", symbol, q, lt));
                manualUSQuarterHourDev.get(symbol).get(q).set(true);
            } else {
                if (freshPrice > qHrOpen) {
                    outputDetailedUS(symbol, str(" set US qhr dev fresh>start", symbol, q, lt,
                            "fresh>start", freshPrice, ">", qHrStart));
                    qHrUSDevDirection.get(symbol).put(q, Direction.Long);
                    manualUSQuarterHourDev.get(symbol).get(q).set(true);
                } else if (freshPrice < qHrOpen) {
                    outputDetailedUS(symbol, str(" set US qhr dev fresh<start", symbol, q, lt,
                            "fresh<start", freshPrice, "<", qHrStart));
                    qHrUSDevDirection.get(symbol).put(q, Direction.Short);
                    manualUSQuarterHourDev.get(symbol).get(q).set(true);
                } else {
                    qHrUSDevDirection.get(symbol).put(q, Direction.Flat);
                }
            }
        }

        pr("US q hr:", lt.truncatedTo(ChronoUnit.SECONDS), ot,
                "#:", qHrOrderNum, "FL#", qHrFilled,
                "lastKey", lastKey, "qStart", qHrStart,
                "qHr", q, "openEntry", quarterHourOpenTime, qHrOpen,
                "P", freshPrice, "currpos", currPos
                , "filled", qHrFilled,
                "dir:", qHrUSDevDirection.get(symbol).get(q),
                "manual?", manualUSQuarterHourDev.get(symbol).get(q));

        if (qHrOrderNum >= MAX_US_QUARTERHOUR_ORDERS) {
            return;
        }


        LocalDateTime lastOrderTime = getLastOrderTime(symbol, ot);
        long milliLast2 = lastTwoOrderMilliDiff(symbol, ot);
        int waitTimeSec = (milliLast2 < 60000) ? 300 : 1;

        double buyPrice = Math.round(freshPrice * 100d) / 100d;
        double sellPrice = Math.round(freshPrice * 100d) / 100d;
        double buySize;
        double sellSize;


        if ((minSoFar / qHrOpen - 1 < downThresh) && (freshPrice / minSoFar - 1 > retreatUpThresh)
                && qHrOrderNum % 2 == 1 && currPos < 0 && qHrUSDevDirection.get(symbol).get(q) == Direction.Short) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(buyPrice, Math.abs(currPos), IOC);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
            outputDetailedUS(symbol, "**********");
            outputDetailedUS(symbol, str("NEW", o.orderId(), "US take profit BUY#",
                    qHrOrderNum, globalIdOrderMap.get(id), "min open last", minSoFar, qHrOpen, freshPrice,
                    "buyPrice", buyPrice, "qHfilled", qHrFilled, "currPos", currPos,
                    "min/open-1", minSoFar / qHrOpen - 1, "down thresh", downThresh,
                    "p/min-1", freshPrice / minSoFar - 1, "retreatUpThresh", retreatUpThresh));

        } else if ((maxSoFar / qHrOpen - 1 > upThresh) && (freshPrice / maxSoFar - 1 < retreatDownThresh)
                && qHrOrderNum % 2 == 1 && currPos > 0 && qHrUSDevDirection.get(symbol).get(q) == Direction.Long) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(sellPrice, currPos, IOC);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
            outputDetailedUS(symbol, "**********");
            outputDetailedUS(symbol, str("NEW", o.orderId(), "US take profit SELL#",
                    qHrOrderNum, globalIdOrderMap.get(id), "max open last", maxSoFar, qHrOpen, freshPrice,
                    "sellPrice", sellPrice, "qHfilled", qHrFilled, "currPos", currPos,
                    "max/open-1", maxSoFar / qHrOpen - 1, "up thresh", upThresh,
                    "p/max-1", freshPrice / maxSoFar - 1, "retreathDownThresh", retreatDownThresh));
        } else {
            if (SECONDS.between(lastOrderTime, nowMilli) > waitTimeSec) {
                if (freshPrice > qHrOpen && !noMoreBuy.get() &&
                        qHrUSDevDirection.get(symbol).get(q) != Direction.Long) {
                    int id = autoTradeID.incrementAndGet();
                    buySize = currPos < 0 ? (Math.abs(currPos) + (qHrOrderNum % 2 == 0 ? US_BASE_SIZE : 0)) : US_BASE_SIZE;
                    Order o = placeBidLimitTIF(buyPrice, buySize, IOC);
                    globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
                    apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                    outputDetailedUS(symbol, "**********");
                    outputDetailedUS(symbol, str("NEW", o.orderId(), "q hr US dev buy #:",
                            qHrOrderNum, globalIdOrderMap.get(id), q, "type", ot,
                            "qHfilled", qHrFilled, "currPos", currPos,
                            "dir", qHrUSDevDirection.get(symbol).get(q),
                            "open fresh buyPrice", qHrOpen, freshPrice, buyPrice, "buysize", buySize));
                    qHrUSDevDirection.get(symbol).put(q, Direction.Long);
                } else if (freshPrice < qHrOpen && !noMoreSell.get() &&
                        qHrUSDevDirection.get(symbol).get(q) != Direction.Short) {
                    int id = autoTradeID.incrementAndGet();
                    sellSize = currPos > 0 ? (currPos + (qHrOrderNum % 2 == 0 ? US_BASE_SIZE : 0)) : US_BASE_SIZE;
                    Order o = placeOfferLimitTIF(sellPrice, sellSize, IOC);
                    globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
                    apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                    outputDetailedUS(symbol, "**********");
                    outputDetailedUS(symbol, str("NEW", o.orderId(), "q hr US dev sell #:",
                            qHrOrderNum, globalIdOrderMap.get(id), q, "type", ot,
                            "qHfilled", qHrFilled, "currPos", currPos,
                            "dir", qHrUSDevDirection.get(symbol).get(q),
                            "open fresh sellPrice", qHrOpen, freshPrice, sellPrice, "sellsize", sellSize));
                    qHrUSDevDirection.get(symbol).put(q, Direction.Short);
                }
            }
        }
    }

    /**
     * US profiter taker based on drawback
     *
     * @param symbol     stock name
     * @param nowMilli   time now
     * @param freshPrice price
     */
    private static void usRelativeProfitTaker(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = tickerToUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);

        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        LocalTime amStart = ltof(9, 29, 59);

        if (lt.isBefore(amStart)) {
            return;
        }

        if (prices.size() <= 1) {
            return;
        }
        LocalTime lastKey = prices.lastKey();

        LocalTime quarterHourStart = ltof(lt.getHour(), minuteToQuarterHour(lt.getMinute()), 0);
        double quarterHourOpen = prices.ceilingEntry(quarterHourStart).getValue();

        double maxSoFar = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(quarterHourStart))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minSoFar = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(quarterHourStart))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        long numOrders = getOrderSizeForTradeType(symbol, US_RELATIVE_TAKE_PROFIT);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_RELATIVE_TAKE_PROFIT);
        OrderStatus lastStatus = getLastPrimaryOrderStatus(symbol, US_RELATIVE_TAKE_PROFIT);


        pr(symbol, " US profit taker#:", numOrders, lt.truncatedTo(ChronoUnit.SECONDS),
                "lastKey", lastKey,
                "max min fresh", maxSoFar, minSoFar, freshPrice,
                "max/open, min/open", maxSoFar / quarterHourOpen - 1, minSoFar / quarterHourOpen - 1,
                "pull back(max, min) ", freshPrice / maxSoFar - 1, freshPrice / minSoFar - 1);

        if (lastStatus != OrderStatus.Filled && lastStatus != OrderStatus.NoOrder) {
            pr(" us relative profit taker, status: ", symbol, lt, lastStatus);
            return;
        }

        if (numOrders >= 1) {
            return;
        }

        double buyPrice = Math.round(freshPrice * 100d) / 100d;
        double sellPrice = Math.round(freshPrice * 100d) / 100d;

        if (SECONDS.between(lastOrderTime, nowMilli) > 60) {
            if (currPos < 0) {
                if ((minSoFar / quarterHourOpen - 1 < downThresh) && (freshPrice / minSoFar - 1 > retreatUpThresh)) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeBidLimitTIF(buyPrice, Math.abs(currPos), IOC);
                    globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_RELATIVE_TAKE_PROFIT));
                    apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                    outputDetailedUS(symbol, "**********");
                    outputDetailedUS(symbol, str("NEW", o.orderId(), "US take profit BUY#",
                            globalIdOrderMap.get(id), "min open fresh", minSoFar, quarterHourOpen, freshPrice,
                            "buyPrice", buyPrice,
                            "min/open-1", minSoFar / quarterHourOpen - 1, "down thresh", downThresh,
                            "p/min-1", freshPrice / minSoFar - 1, "retreatUpThresh", retreatUpThresh));
                }
            } else if (currPos > 0) {
                if ((maxSoFar / quarterHourOpen - 1 > upThresh) && (freshPrice / maxSoFar - 1 < retreatDownThresh)) {
                    int id = autoTradeID.incrementAndGet();
                    Order o = placeOfferLimitTIF(sellPrice, currPos, IOC);
                    globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_RELATIVE_TAKE_PROFIT));
                    apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                    outputDetailedUS(symbol, "**********");
                    outputDetailedUS(symbol, str("NEW", o.orderId(), "US take profit SELL#",
                            globalIdOrderMap.get(id), "max open fresh ", maxSoFar, quarterHourOpen, freshPrice,
                            "sellPrice", sellPrice,
                            "max/open-1", maxSoFar / quarterHourOpen - 1, "up thresh", upThresh,
                            "p/max-1", freshPrice / maxSoFar - 1, "retreathDownThresh", retreatDownThresh));
                }
            }
        }
    }


    /**
     * half hour US traders
     *
     * @param symbol     US name
     * @param nowMilli   time now
     * @param freshPrice fresh price
     */
    private static void usHalfHourDevTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = tickerToUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);

        if (lt.isBefore(LocalTime.of(9, 29, 59)) || lt.isAfter(ltof(11, 0, 0))) {
            return;
        }

        LocalTime halfHourStartTime = ltof(lt.getHour(), lt.getMinute() < 30 ? 0 : 30, 0);
        double halfHourOpen = prices.ceilingEntry(halfHourStartTime).getValue();

        HalfHour h = HalfHour.get(halfHourStartTime);
        AutoOrderType ot = getOrderTypeByHalfHour(h);

        long halfHourOrderNum = getOrderSizeForTradeType(symbol, ot);

        if (!manualUSHalfHourDev.get(symbol).get(h).get()) {
            if (lt.isBefore(h.getStartTime())) {
                outputDetailedUS(symbol, str(" setting manual US halfhour dev direction", symbol, h, lt));
                manualUSHalfHourDev.get(symbol).get(h).set(true);
            } else {
                if (freshPrice > halfHourOpen) {
                    outputDetailedUS(symbol, str(" setting manual US halfhour dev fresh>start", symbol, h, lt));
                    hHrUSDevDirection.get(symbol).put(h, Direction.Long);
                    manualUSHalfHourDev.get(symbol).get(h).set(true);
                } else if (freshPrice < halfHourOpen) {
                    outputDetailedUS(symbol, str(" setting manual US halfhour dev fresh<start", symbol, h, lt));
                    hHrUSDevDirection.get(symbol).put(h, Direction.Short);
                    manualUSHalfHourDev.get(symbol).get(h).set(true);
                } else {
                    hHrUSDevDirection.get(symbol).put(h, Direction.Flat);
                }
            }
        }

        pr(symbol, "US half hour trader ", lt, "start", halfHourStartTime, "halfHour", h, "open", halfHourOpen,
                "type", ot, "#:", halfHourOrderNum, "fresh", freshPrice, "dir",
                hHrUSDevDirection.get(symbol).get(h), "manual? ", manualUSHalfHourDev.get(symbol).get(h).get());

        if (halfHourOrderNum >= MAX_US_HALFHOUR_ORDERS) {
            return;
        }

        LocalDateTime lastOrderTime = getLastOrderTime(symbol, ot);
        long milliLast2 = lastTwoOrderMilliDiff(symbol, ot);
        int waitTimeSec = (milliLast2 < 60000) ? 300 : 10;

        if (SECONDS.between(lastOrderTime, nowMilli) > waitTimeSec) {
            if (freshPrice > halfHourOpen && !noMoreBuy.get() && hHrUSDevDirection.get(symbol
            ).get(h) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, US_BASE_SIZE, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), "half hr US dev buy #:",
                        globalIdOrderMap.get(id), h, "type", ot,
                        "dir", hHrUSDevDirection.get(symbol).get(h)));
                hHrUSDevDirection.get(symbol).put(h, Direction.Long);
            } else if (freshPrice < halfHourOpen && !noMoreSell.get() &&
                    hHrUSDevDirection.get(symbol).get(h) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, US_BASE_SIZE, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), "half hr US dev sell #:",
                        globalIdOrderMap.get(id), h, "type", ot,
                        "dir", hHrUSDevDirection.get(symbol).get(h)));
                hHrUSDevDirection.get(symbol).put(h, Direction.Short);

            }
        }
    }


    /**
     * us open deviation trader
     *
     * @param symbol     symbol
     * @param nowMilli   time now
     * @param freshPrice price
     */
    private static void usOpenDeviationTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = tickerToUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double open = usOpenMap.getOrDefault(symbol, 0.0);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        LocalTime amStart = ltof(9, 29, 59);
        LocalTime amObservationStart = ltof(9, 29, 55);
        LocalTime cutoff = ltof(10, 0);

        cancelAfterDeadline(lt, symbol, US_STOCK_OPENDEV, cutoff);

        if (lt.isBefore(amStart) || lt.isAfter(cutoff)) {
            return;
        }

        double manualOpen = prices.ceilingEntry(amObservationStart).getValue();

        if (!manualUSDevMap.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 35))) {
                outputDetailedUS(symbol, str("Setting manual US Dev 935", symbol, lt));
                manualUSDevMap.get(symbol).set(true);
            } else {
                if (freshPrice > manualOpen) {
                    outputDetailedUS(symbol, str("Setting manual US Dev: last>open", symbol, lt));
                    usOpenDevDirection.put(symbol, Direction.Long);
                    manualUSDevMap.get(symbol).set(true);
                } else if (freshPrice < manualOpen) {
                    outputDetailedUS(symbol, str("Setting manual US Dev: last < open", symbol, lt));
                    usOpenDevDirection.put(symbol, Direction.Short);
                    manualUSDevMap.get(symbol).set(true);
                } else {
                    usOpenDevDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, US_STOCK_OPENDEV);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_STOCK_OPENDEV);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, US_STOCK_OPENDEV);
        int waitSec = (milliLastTwo < 60000) ? 300 : 10;

        double buySize = US_BASE_SIZE * ((numOrders == 0 || numOrders == (MAX_US_ORDERS - 1)) ? 1 : 1);
        double sellSize = US_BASE_SIZE * ((numOrders == 0 || numOrders == (MAX_US_ORDERS - 1)) ? 1 : 1);

        if (numOrders >= MAX_US_ORDERS) {
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && usShortableValueMap.get(symbol) > US_MIN_SHORT_LEVEL)
            if (!noMoreBuy.get() && freshPrice > manualOpen && usOpenDevDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, buySize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_OPENDEV));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), "US open dev BUY#", numOrders,
                        globalIdOrderMap.get(id),
                        "open, manual Open(9 29 55), fresh", open, manualOpen, freshPrice,
                        "buy size/ currpos", buySize, currPos,
                        "last order T, milliLast2, waitSec, nextT", lastOrderTime, milliLastTwo, waitSec,
                        lastOrderTime.plusSeconds(waitSec),
                        "dir", usOpenDevDirection.get(symbol), "manual?", manualUSDevMap.get(symbol)
                        , "short value", usShortableValueMap.get(symbol)));
                usOpenDevDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && freshPrice < manualOpen && usOpenDevDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, sellSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_OPENDEV));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), "US open dev SELL#:", numOrders,
                        globalIdOrderMap.get(id),
                        "open, manual Open(9 29 55), fresh", open, manualOpen, freshPrice,
                        "sell size/ currpos", sellSize, currPos,
                        "last order T, milliLast2, waitSec, nextT", lastOrderTime, milliLastTwo, waitSec,
                        lastOrderTime.plusSeconds(waitSec),
                        "dir", usOpenDevDirection.get(symbol), "manual?", manualUSDevMap.get(symbol)
                        , "short value", usShortableValueMap.get(symbol)));
                usOpenDevDirection.put(symbol, Direction.Short);
            }
        //        pr(" US open dev#:", numOrders, lt, nowMilli, "name, price ", symbol, freshPrice,
//                "open/manual open ", usOpenMap.getOrDefault(symbol, 0.0), manualOpen,
//                "last order T/ millilast2/ waitsec", lastOrderTime, milliLastTwo, waitSec,
//                "curr pos ", currPos, "dir: ", usOpenDevDirection.get(symbol));

    }

    /**
     * us pm open deviation trader
     *
     * @param symbol     us stock
     * @param nowMilli   time now
     * @param freshPrice last stock price
     */
    private static void usPMOpenDeviationTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = tickerToUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        LocalTime pmStart = ltof(11, 59, 59);
        LocalTime pmObservationStart = ltof(11, 59, 55);
        LocalTime pmCutoff = ltof(12, 30);

        cancelAfterDeadline(lt, symbol, US_STOCK_PMOPENDEV, pmCutoff);

        if (lt.isBefore(pmStart) || lt.isAfter(pmCutoff)) {
            return;
        }
        double buySize = US_BASE_SIZE;
        double sellSize = US_BASE_SIZE;

        double pmOpen = prices.ceilingEntry(pmObservationStart).getValue();

        if (!manualUSPMDevMap.get(symbol).get()) {
            if (lt.isBefore(ltof(12, 5))) {
                outputDetailedUS(symbol, str("Setting manual US PM Dev 1205", symbol, lt));
                manualUSPMDevMap.get(symbol).set(true);
            } else {
                if (freshPrice > pmOpen) {
                    outputDetailedUS(symbol, str("Setting manual US PM Dev: last > open", symbol, lt));
                    usPMOpenDevDirection.put(symbol, Direction.Long);
                    manualUSPMDevMap.get(symbol).set(true);
                } else if (freshPrice < pmOpen) {
                    outputDetailedUS(symbol, str("Setting manual US PM Dev: last < open", symbol, lt));
                    usPMOpenDevDirection.put(symbol, Direction.Short);
                    manualUSPMDevMap.get(symbol).set(true);
                } else {
                    usPMOpenDevDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, US_STOCK_PMOPENDEV);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_STOCK_PMOPENDEV);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, US_STOCK_PMOPENDEV);
        int waitSec = (milliLastTwo < 60000) ? 300 : 10;

        if (numOrders >= MAX_US_ORDERS) {
            return;
        }

//        pr(" US pm open dev#:", numOrders, lt, nowMilli, "name, price ", symbol, freshPrice,
//                "open/manual open ", usOpenMap.getOrDefault(symbol, 0.0), pmOpen,
//                "last order T/ millilast2/ waitsec", lastOrderTime, milliLastTwo, waitSec,
//                "curr pos ", currPos, "dir: ", usPMOpenDevDirection.get(symbol)
//                , "manual:", manualUSPMDevMap.get(symbol));

        double buyPrice;
        double sellPrice;

        if (numOrders < 2) {
            buyPrice = freshPrice;
            sellPrice = freshPrice;
        } else {
            buyPrice = Math.min(freshPrice, pmOpen);
            sellPrice = Math.max(freshPrice, pmOpen);
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && usShortableValueMap.get(symbol) > US_MIN_SHORT_LEVEL)
            if (!noMoreBuy.get() && freshPrice > pmOpen && usPMOpenDevDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(buyPrice, buySize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_PMOPENDEV));
                apcon.placeOrModifyOrder(ct, o, new ApiController.IOrderHandler.DefaultOrderHandler(id));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), "US PM open dev BUY#:", numOrders,
                        globalIdOrderMap.get(id),
                        "pm Open(11 59 59)", pmOpen, "buy size/ currpos", buySize, currPos,
                        "last order T, milliLast2, waitSec, nextT", lastOrderTime, milliLastTwo, waitSec,
                        lastOrderTime.plusSeconds(waitSec),
                        "dir", usPMOpenDevDirection.get(symbol), "manual?", manualUSPMDevMap.get(symbol)
                        , "short value", usShortableValueMap.get(symbol)));
                usPMOpenDevDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && freshPrice < pmOpen && usPMOpenDevDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(sellPrice, sellSize, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_PMOPENDEV));
                apcon.placeOrModifyOrder(ct, o, new ApiController.IOrderHandler.DefaultOrderHandler(id));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), "US PM open dev SELL#:", numOrders, globalIdOrderMap.get(id),
                        "pm Open(12 59 59)", pmOpen, "sell size/ currpos", sellSize, currPos,
                        "last order T, milliLast2, waitSec, nextT", lastOrderTime, milliLastTwo, waitSec,
                        lastOrderTime.plusSeconds(waitSec),
                        "dir", usPMOpenDevDirection.get(symbol), "manual?", manualUSPMDevMap.get(symbol)
                        , "short value", usShortableValueMap.get(symbol)));
                usPMOpenDevDirection.put(symbol, Direction.Short);
            }
    }


    /**
     * us hilo trader
     *
     * @param symbol     symbol
     * @param nowMilli   time now
     * @param freshPrice price last
     */
    private static void usHiloTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        Contract ct = tickerToUSContract(symbol);
        LocalTime amStart = ltof(9, 29, 59);
        LocalTime amObservationStart = ltof(9, 29, 55);
        LocalTime amCutoff = ltof(10, 0);

        cancelAfterDeadline(nowMilli.toLocalTime(), symbol, US_STOCK_HILO, amCutoff);

        if (lt.isBefore(amStart) || lt.isAfter(amCutoff)) {
            return;
        }

        if (prices.size() < 1 || prices.lastKey().isBefore(amStart)) {
            return;
        }

        LocalTime lastKey = prices.lastKey();

        double maxSoFar = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(amObservationStart))
                .filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minSoFar = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(amObservationStart))
                .filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalTime maxT = getFirstMaxTPred(prices, e -> e.isAfter(amObservationStart));
        LocalTime minT = getFirstMinTPred(prices, e -> e.isAfter(amObservationStart));
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);

        if (!manualUSHiloMap.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 35))) {
                manualUSHiloMap.get(symbol).set(true);
                outputDetailedUS(symbol, str("Setting manual US Hilo 935", symbol, lt));
            } else {
                if (maxT.isAfter(minT)) {
                    outputDetailedUS(symbol, str("Setting manual US Hilo: maxT>minT", symbol, lt));
                    usHiloDirection.put(symbol, Direction.Long);
                    manualUSHiloMap.get(symbol).set(true);
                } else if (minT.isAfter(maxT)) {
                    outputDetailedUS(symbol, str("Setting manual US Hilo: minT>maxT", symbol, lt));
                    usHiloDirection.put(symbol, Direction.Short);
                    manualUSHiloMap.get(symbol).set(true);
                } else {
                    usHiloDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, US_STOCK_HILO);
        if (numOrders >= MAX_US_ORDERS) {
            return;
        }

        LocalDateTime lastOrderT = getLastOrderTime(symbol, US_STOCK_HILO);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, US_STOCK_HILO);
        int waitSec = (milliLastTwo < 60000) ? 300 : 10;

        double buySize = US_BASE_SIZE * ((numOrders == 0 || numOrders == (MAX_US_ORDERS - 1)) ? 1 : 2);
        double sellSize = US_BASE_SIZE * ((numOrders == 0 || numOrders == (MAX_US_ORDERS - 1)) ? 1 : 2);

        if (SECONDS.between(lastOrderT, nowMilli) > waitSec && maxSoFar != 0.0 && minSoFar != 0.0 &&
                usShortableValueMap.get(symbol) > US_MIN_SHORT_LEVEL) {
            if (!noMoreBuy.get() && (freshPrice > maxSoFar || maxT.isAfter(minT))
                    && usHiloDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, buySize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), "US hilo buy#:", numOrders,
                        globalIdOrderMap.get(id), "buyQ/curr pos", buySize, currPos,
                        "max min maxT minT ", maxSoFar, minSoFar, maxT, minT,
                        "last order T, milliLast2, waitSec,nexT", lastOrderT, milliLastTwo, waitSec,
                        lastOrderT.plusSeconds(waitSec), "dir", usHiloDirection.get(symbol),
                        "manual?", manualUSHiloMap.get(symbol), "short value", usShortableValueMap.get(symbol)));
                usHiloDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && (freshPrice < minSoFar || minT.isAfter(maxT))
                    && usHiloDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, sellSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_HILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), "US hilo sell#:", numOrders,
                        globalIdOrderMap.get(id), "sellQ/curr pos", sellSize, currPos,
                        "max min maxT minT ", maxSoFar, minSoFar, maxT, minT,
                        "last order T, milliLast2, waitSec, nextT", lastOrderT, milliLastTwo, waitSec,
                        lastOrderT.plusSeconds(waitSec), "dir", usHiloDirection.get(symbol),
                        "manual?", manualUSHiloMap.get(symbol), "short value", usShortableValueMap.get(symbol)));
                usHiloDirection.put(symbol, Direction.Short);
            }
        }
    }

    /**
     * US pm hilo trader
     *
     * @param symbol     usstock
     * @param nowMilli   now time
     * @param freshPrice last stock price
     */
    private static void usPMHiloTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        Contract ct = tickerToUSContract(symbol);
        LocalTime pmStart = ltof(11, 59, 59);
        LocalTime pmObservationStart = ltof(11, 59, 55);
        LocalTime pmCutoff = ltof(12, 30);

        cancelAfterDeadline(nowMilli.toLocalTime(), symbol, US_STOCK_PMHILO, pmCutoff);

        if (lt.isBefore(pmStart) || lt.isAfter(pmCutoff)) {
            return;
        }

        if (prices.size() < 1 || prices.lastKey().isBefore(pmStart)) {
            return;
        }

        LocalTime lastKey = prices.lastKey();
        double maxPMSoFar = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(pmObservationStart))
                .filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minPMSoFar = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(pmObservationStart))
                .filter(e -> e.getKey().isBefore(lastKey))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        LocalTime maxPMT = getFirstMaxTPred(prices, e -> e.isAfter(pmObservationStart));
        LocalTime minPMT = getFirstMinTPred(prices, e -> e.isAfter(pmObservationStart));
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);


        if (!manualUSPMHiloMap.get(symbol).get()) {
            if (lt.isBefore(ltof(12, 5))) {
                manualUSPMHiloMap.get(symbol).set(true);
                outputDetailedUS(symbol, str("Setting manual US PM Hilo 1305", symbol, lt));
            } else {
                if (maxPMT.isAfter(minPMT)) {
                    outputDetailedUS(symbol, str("Setting manual US PM Hilo: maxT>minT", symbol, lt));
                    usPMHiloDirection.put(symbol, Direction.Long);
                    manualUSPMHiloMap.get(symbol).set(true);
                } else if (minPMT.isAfter(maxPMT)) {
                    outputDetailedUS(symbol, str("Setting manual US PM Hilo: minT>maxT", symbol, lt));
                    usPMHiloDirection.put(symbol, Direction.Short);
                    manualUSPMHiloMap.get(symbol).set(true);
                } else {
                    usPMHiloDirection.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, US_STOCK_PMHILO);
        if (numOrders >= MAX_US_ORDERS) {
            return;
        }

        double buySize = US_BASE_SIZE * ((numOrders == 0 || numOrders == (MAX_US_ORDERS - 1)) ? 1 : 1);
        double sellSize = US_BASE_SIZE * ((numOrders == 0 || numOrders == (MAX_US_ORDERS - 1)) ? 1 : 1);

        LocalDateTime lastOrderT = getLastOrderTime(symbol, US_STOCK_PMHILO);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, US_STOCK_PMHILO);
        int waitSec = (milliLastTwo < 60000) ? 300 : 10;

//        pr(" US PM hilo#:", numOrders, ", name, price ", nowMilli, symbol, freshPrice,
//                "last order T, milliLast2, waitSec", lastOrderT, milliLastTwo, waitSec,
//                "max min maxt mint ", maxPMSoFar, minPMSoFar, maxPMT, minPMT,
//                lastOrderT.plusSeconds(waitSec), "dir: ", usPMHiloDirection.get(symbol), "currPos ", currPos,
//                "shortability", usShortableValueMap.get(symbol));

        if (SECONDS.between(lastOrderT, nowMilli) > waitSec && maxPMSoFar != 0.0 && minPMSoFar != 0.0
                && usShortableValueMap.get(symbol) > US_MIN_SHORT_LEVEL) {
            if (!noMoreBuy.get() && (freshPrice > maxPMSoFar || maxPMT.isAfter(minPMT))
                    && usPMHiloDirection.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, buySize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_PMHILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), "US PM hilo buy#:", numOrders, globalIdOrderMap.get(id),
                        "buy size/curr pos", buySize, currPos,
                        "max min maxT minT ", maxPMSoFar, minPMSoFar, maxPMT, minPMT,
                        "last order T, milliLast2, waitSec,nexT", lastOrderT, milliLastTwo, waitSec,
                        lastOrderT.plusSeconds(waitSec), "dir", usPMHiloDirection.get(symbol),
                        "manual?", manualUSPMHiloMap.get(symbol), "short value", usShortableValueMap.get(symbol)));
                usPMHiloDirection.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && (freshPrice < minPMSoFar || minPMT.isAfter(maxPMT))
                    && usPMHiloDirection.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, sellSize, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_STOCK_PMHILO));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), "US PM hilo sell#:", numOrders, globalIdOrderMap.get(id),
                        "sell size/curr pos", sellSize, currPos,
                        "max min maxT minT ", maxPMSoFar, minPMSoFar, maxPMT, minPMT,
                        "last order T, milliLast2, waitSec, nextT", lastOrderT, milliLastTwo, waitSec,
                        lastOrderT.plusSeconds(waitSec), "dir", usPMHiloDirection.get(symbol),
                        "manual?", manualUSPMHiloMap.get(symbol), "short value", usShortableValueMap.get(symbol)));
                usPMHiloDirection.put(symbol, Direction.Short);
            }
        }

    }

    /**
     * liquidation after cut off if hit
     *
     * @param symbol     symbol
     * @param nowMilli   time now
     * @param freshPrice fresh price
     */
    private static void usPostCutoffLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = tickerToUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        LocalTime amStart = ltof(9, 29, 59);
        LocalTime amObservationStart = ltof(9, 29, 55);
        //LocalTime amEnd = ltof(12, 0, 0);
        LocalTime cutoff = ltof(10, 0);
        LocalTime pmClose = ltof(16, 0);
        double safetyMargin = freshPrice * US_SAFETY_RATIO;

        if (lt.isBefore(cutoff) || lt.isAfter(pmClose)) {
            return;
        }

        if (prices.lastKey().isBefore(amStart)) {
            return;
        }

        double manualOpen = prices.ceilingEntry(amObservationStart).getValue();

        long numOrderPostCutoff = getOrderSizeForTradeType(symbol, US_POST_AMCUTOFF_LIQ);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_POST_AMCUTOFF_LIQ);


        if (numOrderPostCutoff >= 1) {
            return;
        }

        if (lt.isAfter(cutoff) && lt.isBefore(pmClose)) {
            if (currPos < 0 && freshPrice > manualOpen - safetyMargin) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, Math.abs(currPos), IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_POST_AMCUTOFF_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), " US postAMCutoff liq sell BUY #:",
                        numOrderPostCutoff, "T now", lt, " cutoff", cutoff
                        , globalIdOrderMap.get(id), "last order T", lastOrderTime, "currPos", currPos,
                        "fresh:", freshPrice, "open ", manualOpen, "safety ratio ", US_SAFETY_RATIO,
                        "safety margin ", safetyMargin, "cut level ", manualOpen - safetyMargin));
            } else if (currPos > 0 && freshPrice < manualOpen + safetyMargin) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, currPos, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_POST_AMCUTOFF_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), " US postAMCutoff liq SELL #:",
                        numOrderPostCutoff, "T now", lt, " cutoff", cutoff
                        , globalIdOrderMap.get(id), "last order T", lastOrderTime, "currPos", currPos,
                        "fresh:", freshPrice, "open ", manualOpen, "safety ratio ", US_SAFETY_RATIO,
                        "safety margin ", safetyMargin, "safety level", manualOpen + safetyMargin));
            }
        }
    }

    /**
     * adding US pm trader
     *
     * @param symbol     us stock
     * @param nowMilli   time now
     * @param freshPrice price now
     */
    private static void usPostPMCutoffLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        Contract ct = tickerToUSContract(symbol);
        NavigableMap<LocalTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        LocalTime pmStart = ltof(11, 59, 59);
        LocalTime pmObservationStart = ltof(11, 59, 55);
        LocalTime pmCutoff = ltof(12, 30);
        LocalTime pmClose = ltof(16, 0);
        double safetyMargin = freshPrice * US_SAFETY_RATIO;

        if (lt.isBefore(pmCutoff) || lt.isAfter(pmClose)) {
            return;
        }

        if (prices.lastKey().isBefore(pmStart)) {
            return;
        }

        double pmOpen = prices.ceilingEntry(pmObservationStart).getValue();

        long numOrderPMCutoff = getOrderSizeForTradeType(symbol, US_POST_PMCUTOFF_LIQ);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_POST_PMCUTOFF_LIQ);


        if (numOrderPMCutoff >= 1) {
            return;
        }

        if (lt.isAfter(pmCutoff) && lt.isBefore(pmClose)) {
            if (currPos < 0 && freshPrice > pmOpen - safetyMargin) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, Math.abs(currPos), IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_POST_PMCUTOFF_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), " US postPMCutoff liq sell BUY #:", numOrderPMCutoff
                        , "T now", lt, " cutoff", pmCutoff
                        , globalIdOrderMap.get(id), "last order T", lastOrderTime, "currPos", currPos,
                        "fresh:", freshPrice, "open", pmOpen, "safety ratio ", US_SAFETY_RATIO,
                        "safety margin ", safetyMargin, "safety level ", pmOpen - safetyMargin));
            } else if (currPos > 0 && freshPrice < pmOpen + safetyMargin) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, currPos, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_POST_PMCUTOFF_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), " US postPMCutoff liq SELL #:", numOrderPMCutoff
                        , "T now", lt, " cutoff", pmCutoff
                        , globalIdOrderMap.get(id), "last order T", lastOrderTime, "currPos", currPos,
                        "fresh:", freshPrice, "open", pmOpen, "safety ratio ", US_SAFETY_RATIO,
                        "safety margin ", safetyMargin, "safety level ", pmOpen + safetyMargin));
            }
        }
    }

    /**
     * liquidate at US close
     *
     * @param symbol     symbol
     * @param nowMilli   now time
     * @param freshPrice price
     */
    private static void usCloseLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime liqStartTime = ltof(15, 50);
        LocalTime liqEndTime = ltof(16, 0);
        Contract ct = tickerToUSContract(symbol);
        long liqWaitSecs = 60;

        if (nowMilli.toLocalTime().isBefore(liqStartTime) || nowMilli.toLocalTime().isAfter(liqEndTime)) {
            return;
        }

        LocalDateTime lastOrderTime = getLastOrderTime(symbol, US_CLOSE_LIQ);
        long numOrderCloseLiq = getOrderSizeForTradeType(symbol, US_CLOSE_LIQ);

        double pos = ibPositionMap.getOrDefault(symbol, 0.0);

        if (numOrderCloseLiq >= 1) {
            return;
        }

        if (SECONDS.between(lastOrderTime, nowMilli) > liqWaitSecs) {
            if (pos < 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, Math.abs(pos), IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_CLOSE_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), " US close liq BUY #:", numOrderCloseLiq,
                        globalIdOrderMap.get(id), "last order T", lastOrderTime, "pos", pos));
            } else if (pos > 0) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, pos, IOC);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, US_CLOSE_LIQ));
                apcon.placeOrModifyOrder(ct, o, new GuaranteeUSHandler(id, apcon));
                outputDetailedUS(symbol, "**********");
                outputDetailedUS(symbol, str("NEW", o.orderId(), " US close liq SELL #:", numOrderCloseLiq
                        , globalIdOrderMap.get(id), "last order T", lastOrderTime, "pos", pos));
            }
        }
    }
}
