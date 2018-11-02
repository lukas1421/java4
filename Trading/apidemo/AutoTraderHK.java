package apidemo;

import client.Contract;
import client.Order;
import client.OrderAugmented;
import controller.ApiController;
import controller.ApiController.IOrderHandler.DefaultOrderHandler;
import handler.GuaranteeHKHandler;
import util.AutoOrderType;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static apidemo.AutoTraderMain.*;
import static apidemo.ChinaData.priceMapBarDetail;
import static apidemo.ChinaMain.MONDAY_OF_WEEK;
import static apidemo.XuTraderHelper.*;
import static client.Types.TimeInForce.DAY;
import static client.Types.TimeInForce.IOC;
import static java.time.temporal.ChronoUnit.SECONDS;
import static util.AutoOrderType.*;
import static utility.Utility.*;

public class AutoTraderHK extends JPanel {

    public static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalDateTime, Double>> hkPriceMapDetail
            = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkBidMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkAskMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkOpenMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkFreshPriceMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Double> hkShortableValueMap = new ConcurrentHashMap<>();

    private static volatile ConcurrentHashMap<String, Direction> hkWDevDir = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualHKWDev = new ConcurrentHashMap<>();

    private static volatile ConcurrentHashMap<String, Direction> hkHiloDir = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualHKHilo = new ConcurrentHashMap<>();

    private static volatile ConcurrentHashMap<String, Direction> hkPMHiloDir = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<String, AtomicBoolean> manualHKPMHilo = new ConcurrentHashMap<>();

    private static final double MIN_SHORT_LEVEL = 1.5;

    private static long MAX_ORDER_HK = 2;

    private static final double HK_SAFETY_RATIO = 0.005;

    public static List<String> hkSymbols = new ArrayList<>();

    private static Map<String, Integer> hkSizeMap = new HashMap<>();

    //fut dev
    private static final double MAX_DEV_HK = 0.001;
    private static final double hiThresh = 0.005;
    private static final double loThresh = -0.005;
    private static final double retreatHIThresh = 0.85 * hiThresh;
    private static final double retreatLOThresh = 0.85 * loThresh;

    AutoTraderHK() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "hkSizeMap.txt"), "gbk"))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                hkSizeMap.put(al1.get(0), Integer.parseInt(al1.get(1)));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        hkSymbols.add(ibContractToSymbol(getHKFutContract("MCH.HK")));
        //hkSymbols.add(ibContractToSymbol(tickerToHKStkContract("2688")));
        //hkSymbols.add(ibContractToSymbol(tickerToHKStkContract("1548")));
        //hkSymbols.add(ibContractToSymbol(tickerToHKStkContract("3333")));
        //String symbol = ibContractToSymbol(ct);
        //hkSymbols.add(ibContractToSymbol(tickerToHKStkContract("3690")));

        hkSymbols.forEach((s) -> {
            if (!priceMapBarDetail.containsKey(s)) {
                priceMapBarDetail.put(s, new ConcurrentSkipListMap<>());
            }

            hkBidMap.put(s, 0.0);
            hkAskMap.put(s, 0.0);
            hkOpenMap.put(s, 0.0);
            hkFreshPriceMap.put(s, 0.0);
            hkShortableValueMap.put(s, 0.0);

            hkWDevDir.put(s, Direction.Flat);
            manualHKWDev.put(s, new AtomicBoolean(false));

            hkHiloDir.put(s, Direction.Flat);
            manualHKHilo.put(s, new AtomicBoolean(false));

            hkPMHiloDir.put(s, Direction.Flat);
            manualHKPMHilo.put(s, new AtomicBoolean(false));
        });
    }


    public static void processeMainHK(String symbol, LocalDateTime nowMilli, double last) {
        //cancelAllOrdersAfterDeadline(nowMilli.toLocalTime(), ltof(10, 0, 0));

        if (globalTradingOn.get()) {

            //hkFutDev(symbol, nowMilli, last);
            //hkFutWCutoffLiq(symbol, nowMilli, last);
            //hkDev(symbol, nowMilli, last);
            //hkHiloTrader(symbol, nowMilli, freshPrice);
            //hkPMHiloTrader(symbol, nowMilli, freshPrice);
        }

        //hkPostAMCutoffLiqTrader(symbol, nowMilli, last);
        //hkPostPMCutoffLiqTrader(symbol, nowMilli, last);
        //hkCloseLiqTrader(symbol, nowMilli, last);

    }

    private static void hkFutWCutoffLiq(String symbol, LocalDateTime nowMilli, double freshPrice) {
        if (!symbol.equals("MCH.HK")) {
            pr(" hk symbol not MCH.HK");
            return;
        }
        LocalDateTime monObt = ldtof(MONDAY_OF_WEEK, ltof(9, 14, 0));
        AutoOrderType ot = HK_W_POSTCUTOFF_LIQ;
        DayOfWeek d = nowMilli.getDayOfWeek();

        double safetyMargin = freshPrice * 0.001;

        long numOrders = getOrderSizeForTradeType(symbol, ot);

        if (numOrders != 0) {
            return;
        }

        if (d == DayOfWeek.MONDAY || d == DayOfWeek.TUESDAY) {
            return;
        }

        Contract ct = getHKFutContract(symbol);
        NavigableMap<LocalDateTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        double weekOpen = prices.ceilingEntry(monObt).getValue();

        if (currPos < 0 && freshPrice > weekOpen - safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, Math.abs(currPos), IOC);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
            apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK W cutoff liq BUY#:", numOrders,
                    globalIdOrderMap.get(id),
                    "freshPrice, manualOpen", freshPrice, weekOpen, "safety margin ", safetyMargin,
                    "cut level", weekOpen - safetyMargin));
        } else if (currPos > 0 && freshPrice < weekOpen + safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, currPos, IOC);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
            apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK W cutoff liq SELL#:", numOrders,
                    globalIdOrderMap.get(id),
                    "freshPrice, manualOpen", freshPrice, weekOpen, "safety margin", safetyMargin,
                    "cut level", weekOpen + safetyMargin));
        }
    }


    /**
     * cut pos after cutoff if on the wrong side of manual open
     *
     * @param symbol     hk stock symbol (starting with hk)
     * @param nowMilli   time now in milliseconds
     * @param freshPrice last stock price
     */
    private static void hkPostAMCutoffLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        LocalTime amCutoff = ltof(10, 0);
        LocalDate td = getTradeDate(nowMilli);
        LocalDateTime amObservationStart = LocalDateTime.of(td, ltof(9, 19, 0));

        double safetyMargin = freshPrice * HK_SAFETY_RATIO;

        if (lt.isBefore(amCutoff)) {
            return;
        }

        long numOrders = getOrderSizeForTradeType(symbol, HK_POST_AMCUTOFF_LIQ);

        if (numOrders >= 1) {
            return;
        }

        String ticker = hkSymbolToTicker(symbol);
        Contract ct = tickerToHKStkContract(ticker);
        NavigableMap<LocalDateTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        double manualOpen = prices.ceilingEntry(amObservationStart).getValue();

        if (currPos < 0 && freshPrice > manualOpen - safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, Math.abs(currPos), DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_POST_AMCUTOFF_LIQ));
            apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK post AM cutoff liq BUY#:", numOrders,
                    globalIdOrderMap.get(id), "shortability ", hkShortableValueMap.get(symbol),
                    "freshPrice, manualOpen", freshPrice, manualOpen,
                    "safety ratio ", HK_SAFETY_RATIO,
                    "safety margin ", safetyMargin,
                    "cut level", manualOpen - safetyMargin));
        } else if (currPos > 0 && freshPrice < manualOpen + safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, currPos, DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_POST_AMCUTOFF_LIQ));
            apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK post AM cutoff liq SELL#:", numOrders,
                    globalIdOrderMap.get(id), "shortability ", hkShortableValueMap.get(symbol),
                    "freshPrice, manualOpen", freshPrice, manualOpen,
                    "safety ratio", HK_SAFETY_RATIO,
                    "safety margin", safetyMargin,
                    "cut level", manualOpen + safetyMargin));
        }
    }

    private static void hkPostPMCutoffLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        LocalDate td = getTradeDate(nowMilli);
        LocalTime pmCutoff = ltof(13, 30);
        LocalDateTime pmObservationStart = LocalDateTime.of(td, ltof(12, 58, 0));
        double safetyMargin = freshPrice * HK_SAFETY_RATIO;

        if (lt.isBefore(pmCutoff)) {
            return;
        }

        long numOrders = getOrderSizeForTradeType(symbol, HK_POST_PMCUTOFF_LIQ);

        if (numOrders >= 1) {
            return;
        }

        String ticker = hkSymbolToTicker(symbol);
        Contract ct = tickerToHKStkContract(ticker);
        NavigableMap<LocalDateTime, Double> prices = priceMapBarDetail.get(symbol);
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        double manualPMOpen = prices.ceilingEntry(pmObservationStart).getValue();

        if (currPos < 0 && freshPrice > manualPMOpen - safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, Math.abs(currPos), DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_POST_PMCUTOFF_LIQ));
            apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK PM post cutoff liq BUY#:", numOrders,
                    globalIdOrderMap.get(id), "shortability ", hkShortableValueMap.get(symbol),
                    "freshPrice, PMOpen", freshPrice, manualPMOpen,
                    "safety ratio", HK_SAFETY_RATIO, "safety margin ", safetyMargin,
                    " safety level ", manualPMOpen - safetyMargin));

        } else if (currPos > 0 && freshPrice < manualPMOpen + safetyMargin) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, currPos, DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_POST_PMCUTOFF_LIQ));
            apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK PM post cutoff liq SELL#:", numOrders,
                    globalIdOrderMap.get(id), "shortability ", hkShortableValueMap.get(symbol),
                    "freshPrice, PMOpen", freshPrice, manualPMOpen,
                    "safety ratio ", HK_SAFETY_RATIO, "safety margin ", safetyMargin,
                    " safety level ", manualPMOpen + safetyMargin));
        }
    }

    private static void hkFutDev(String symbol, LocalDateTime nowMilli, double last) {

        if (!symbol.equals("MCH.HK")) {
            pr(" hk symbol not MCH.HK");
            return;
        }

        LocalTime lt = nowMilli.toLocalTime();
        LocalDateTime monObt = ldtof(MONDAY_OF_WEEK, ltof(8, 59, 50));
        Contract ct = getHKFutContract(symbol);
        LocalDate tradeDate = getTradeDate(nowMilli);
        DayOfWeek d = tradeDate.getDayOfWeek();

        if (d != DayOfWeek.MONDAY && d != DayOfWeek.TUESDAY) {
            return;
        }

        NavigableMap<LocalDateTime, Double> prices = priceMapBarDetail.get(symbol);

        if (prices.size() == 0) {
            return;
        }

        AutoOrderType ot = HK_FUT_WDEV;
        double pos = ibPositionMap.getOrDefault(symbol, 0.0);

        NavigableMap<LocalDateTime, Double> futPrice = priceMapBarDetail.get(symbol).entrySet().stream()
                .filter(e -> e.getKey().isAfter(monObt))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, ConcurrentSkipListMap::new));

//        if (lt.isBefore(ltof(9, 10)) || lt.isAfter(cutoff)) {
//            return;
//        }

        LocalDateTime lastOrderT = getLastOrderTime(symbol, ot);
        long milliSinceLast = tSincePrevOrderMilli(symbol, ot, nowMilli);
        long milliLast2 = lastTwoOrderMilliDiff(symbol, ot);
        long numOrders = getOrderSizeForTradeType(symbol, ot);
        int waitSec = getWaitSec(milliLast2);
        //double priceOffset = getPriceOffset(milliLast2, last);
        double open = futPrice.firstEntry().getValue();
        LocalDateTime openT = futPrice.firstEntry().getKey();
        double filled = getFilledForType(symbol, ot);
        double dev = (last / open) - 1;
        //int baseSize = DEV_BASE_SIZE;
        //double baseSize = getXUBaseSize(DEV_BASE_SIZE, milliSinceLast, numOrders);
        double baseSize = 1;

        pr("HKWdev", lt.truncatedTo(ChronoUnit.MILLIS),
                "#", numOrders, "F#", filled, "opEn:", openT, open,
                "P", last, "pos", pos, "dev", r10000(dev), "maxD", MAX_DEV_HK,
                "tFrLastOrd", showLong(milliSinceLast),
                "wait", waitSec, "nextT", lastOrderT.toLocalTime().plusSeconds(waitSec).truncatedTo(SECONDS),
                (nowMilli.isBefore(lastOrderT.plusSeconds(waitSec)) ? "wait" : "vacant"),
                "baseSize", baseSize, "dir", hkWDevDir.get(symbol), "manual",
                manualHKWDev.get(symbol));

        if (numOrders >= MAX_ORDER_HK) {
            return;
        }

        if (futPrice.firstKey().getDayOfWeek() != DayOfWeek.MONDAY) {
            return;
        }

        if (!manualHKWDev.get(symbol).get()) {
            if (nowMilli.isBefore(monObt.plusMinutes(1L))) {
                outputDetailedHK(symbol, str("set fut open Wdev dir 9:01", nowMilli, last));
                manualHKWDev.get(symbol).set(true);
            } else {
                if (last > open) {
                    outputDetailedHK(symbol, str("set fut Wdev dir fresh > open", lt, last, ">", open));
                    hkWDevDir.put(symbol, Direction.Long);
                    manualHKWDev.get(symbol).set(true);
                } else if (last < open) {
                    outputDetailedHK(symbol, str("set fut Wdev dir fresh < open", lt, last, "<", open));
                    hkWDevDir.put(symbol, Direction.Short);
                    manualHKWDev.get(symbol).set(true);
                } else {
                    hkWDevDir.put(symbol, Direction.Flat);
                }
            }
        }

        double maxV = futPrice.entrySet().stream().filter(e -> e.getKey().isAfter(monObt))
                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);

        double minV = futPrice.entrySet().stream().filter(e -> e.getKey().isAfter(monObt))
                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);

        double buySize;
        double sellSize;

        if ((minV / open - 1 < loThresh) && (last / minV - 1 > retreatHIThresh)
                && filled <= -1 && pos < 0 && (numOrders % 2 == 1)) {
            int id = autoTradeID.incrementAndGet();
            buySize = Math.max(1, Math.floor(Math.abs(pos) / 2));
            Order o = placeBidLimitTIF(last, buySize, IOC);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", lt, o.orderId(), "hkfut Wdev take profit BUY",
                    "max,min,oautopen,last", maxV, minV, open, last,
                    "min/open", r10000(minV / open - 1), "loThresh", loThresh,
                    "p/min", r10000(last / minV - 1), "retreatHIThresh", retreatHIThresh));
        } else if ((maxV / open - 1 > hiThresh) && (last / maxV - 1 < retreatLOThresh)
                && filled >= 1 && pos > 0 && (numOrders % 2 == 1)) {
            int id = autoTradeID.incrementAndGet();
            sellSize = Math.max(1, Math.floor(Math.abs(pos) / 2));
            Order o = placeOfferLimitTIF(last, sellSize, IOC);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", lt, o.orderId(), "hkfut Wdev take profit SELL",
                    "max,min,mopen,last", maxV, minV, open, last,
                    "max/open", r(maxV / open - 1), "hiThresh", hiThresh,
                    "p/max", r(last / maxV - 1), "retreatLoThresh", retreatLOThresh));
        } else {
            if ((SECONDS.between(lastOrderT, nowMilli) > waitSec && Math.abs(dev) < MAX_DEV_HK) ||
                    (numOrders % 2 == 1)) {
                if (!noMoreBuy.get() && last > open && hkWDevDir.get(symbol) != Direction.Long) {
                    int id = autoTradeID.incrementAndGet();
                    buySize = pos < 0 ? (Math.abs(pos) + (numOrders % 2 == 0 ? baseSize : 0)) : baseSize;
                    Order o = placeBidLimitTIF(last, buySize, IOC);
                    globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
                    apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
                    outputDetailedHK(symbol, "**********");
                    outputDetailedHK(symbol, str("NEW", lt, o.orderId(), "hkfut Wdev BUY #:", numOrders,
                            globalIdOrderMap.get(id), "open,last ", open, last, "milliLast2", showLong(milliLast2),
                            "waitSec", waitSec, "nextT", lastOrderT.plusSeconds(waitSec), "baseSize", baseSize));
                    hkWDevDir.put(symbol, Direction.Long);
                } else if (!noMoreSell.get() && last < open && hkWDevDir.get(symbol) != Direction.Short) {
                    int id = autoTradeID.incrementAndGet();
                    sellSize = pos > 0 ? (Math.abs(pos) + (numOrders % 2 == 0 ? baseSize : 0)) : baseSize;
                    Order o = placeOfferLimitTIF(last, sellSize, IOC);
                    globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, ot));
                    apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
                    outputDetailedHK(symbol, "**********");
                    outputDetailedHK(symbol, str("NEW", lt, o.orderId(), "hkfut Wdev SELL #:", numOrders,
                            globalIdOrderMap.get(id), "open,last ", open, last, "milliLast2", showLong(milliLast2),
                            "waitSec", waitSec, "nextT", lastOrderT.plusSeconds(waitSec), "baseSize", baseSize));
                    hkWDevDir.put(symbol, Direction.Short);
                }
            }
        }
    }

    /**
     * hk open deviation trader
     *
     * @param symbol     stock name
     * @param nowMilli   time now
     * @param freshPrice last price
     */
    private static void hkDev(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        LocalTime cutoff = ltof(10, 0);
        LocalDate td = getTradeDate(nowMilli);
        LocalDateTime amObservationStart = LocalDateTime.of(td, ltof(9, 19, 0));
        LocalTime amTradingStart = ltof(9, 28, 0);
        String ticker = hkSymbolToTicker(symbol);
        Contract ct = tickerToHKStkContract(ticker);
        int size = hkSizeMap.getOrDefault(symbol, 100);

        NavigableMap<LocalDateTime, Double> prices = priceMapBarDetail.get(symbol);
        double open = hkOpenMap.getOrDefault(symbol, 0.0);

        if (prices.size() == 0) {
            return;
        }

        if (lt.isBefore(amTradingStart) || lt.isAfter(cutoff)) {
            return;
        }

        double manualOpen = prices.ceilingEntry(amObservationStart).getValue();

        double firstTick = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(amObservationStart))
                .filter(e -> Math.abs(e.getValue() - manualOpen) > 0.01).findFirst().map(Map.Entry::getValue)
                .orElse(0.0);

        LocalDateTime firstTickTime = prices.entrySet().stream()
                .filter(e -> e.getKey().isAfter(amObservationStart))
                .filter(e -> Math.abs(e.getValue() - manualOpen) > 0.01).findFirst().map(Map.Entry::getKey)
                .orElse(LocalDateTime.MIN);


        if (!manualHKWDev.get(symbol).get()) {
            if (lt.isBefore(ltof(9, 35, 0))) {
                outputDetailedHK(symbol, str("setting manual HK dev:before 935 ", symbol, lt));
                manualHKWDev.get(symbol).set(true);
            } else {
                if (freshPrice > open) {
                    outputDetailedHK(symbol, str("setting manual HK dev: fresh>open ", symbol, lt));
                    hkWDevDir.put(symbol, Direction.Long);
                    manualHKWDev.get(symbol).set(true);
                } else if (freshPrice < open) {
                    outputDetailedHK(symbol, str("setting manual HK dev: fresh<open", symbol, lt));
                    hkWDevDir.put(symbol, Direction.Short);
                    manualHKWDev.get(symbol).set(true);
                } else {
                    hkWDevDir.put(symbol, Direction.Flat);
                }
            }
        }

        long numOrders = getOrderSizeForTradeType(symbol, HK_STOCK_DEV);
        long milliLastTwo = lastTwoOrderMilliDiff(symbol, HK_STOCK_DEV);
        LocalDateTime lastOrderTime = getLastOrderTime(symbol, HK_STOCK_DEV);
        long waitSec = (milliLastTwo < 60000) ? 300 : 10;
        if (numOrders >= MAX_ORDER_HK) {
            return;
        }

        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);


        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && hkShortableValueMap.getOrDefault(symbol, 0.0) >
                MIN_SHORT_LEVEL) {
            if (!noMoreBuy.get() && freshPrice > open && hkWDevDir.get(symbol) != Direction.Long) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeBidLimitTIF(freshPrice, size, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_DEV));
                apcon.placeOrModifyOrder(ct, o, new DefaultOrderHandler(id));
                outputDetailedHK(symbol, "**********");
                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK open dev BUY#:", numOrders,
                        globalIdOrderMap.get(id),
                        "open, manualOpen, ft, ftT", open, manualOpen, firstTick, firstTickTime,
                        "last Order T, milliLastTwo", lastOrderTime, milliLastTwo,
                        "pos", currPos, "dir", hkWDevDir.get(symbol), "manual?", manualHKWDev.get(symbol),
                        "shortability ", hkShortableValueMap.get(symbol)));
                hkWDevDir.put(symbol, Direction.Long);
            } else if (!noMoreSell.get() && freshPrice < open && hkWDevDir.get(symbol) != Direction.Short) {
                int id = autoTradeID.incrementAndGet();
                Order o = placeOfferLimitTIF(freshPrice, size, DAY);
                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_DEV));
                apcon.placeOrModifyOrder(ct, o, new ApiController.IOrderHandler.DefaultOrderHandler(id));
                outputDetailedHK(symbol, "**********");
                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK open dev SELL#:", numOrders,
                        globalIdOrderMap.get(id),
                        "open, manualOpen, ft, ftT", open, manualOpen, firstTick, firstTickTime,
                        "last Order T, milliLastTwo", lastOrderTime, milliLastTwo,
                        "pos", currPos, "dir", hkWDevDir.get(symbol), "manual?", manualHKWDev.get(symbol),
                        "shortability ", hkShortableValueMap.get(symbol)));
                hkWDevDir.put(symbol, Direction.Short);
            }
        }
        pr(" open deviation hk ", prices);
        pr(" HK open dev #: ", numOrders, nowMilli, ticker, symbol, "price:", freshPrice,
                "open,manualOpen,ft, ftT", open, manualOpen, firstTick, firstTickTime,
                "last order T", lastOrderTime, "milliLastTwo", milliLastTwo, "waitSec", waitSec,
                "pos", currPos, "dir:", hkWDevDir.get(symbol), "manual? ", manualHKWDev.get(symbol),
                "shortable value ", hkShortableValueMap.get(symbol));
    }

    /**
     * liquidate holdings at hk close
     *
     * @param symbol     hk stock
     * @param nowMilli   time in milliseconds
     * @param freshPrice last stock price
     */
    private static void hkCloseLiqTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
        LocalTime lt = nowMilli.toLocalTime();
        String ticker = hkSymbolToTicker(symbol);
        Contract ct = tickerToHKStkContract(ticker);
        if (lt.isBefore(ltof(15, 50)) || lt.isAfter(ltof(16, 0))) {
            return;
        }
        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
        long numOrders = getOrderSizeForTradeType(symbol, HK_CLOSE_LIQ);
        if (numOrders >= 1) {
            return;
        }
        if (currPos < 0) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeBidLimitTIF(freshPrice, Math.abs(currPos), DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_CLOSE_LIQ));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK close Liq BUY:#:", numOrders,
                    globalIdOrderMap.get(id), "pos", currPos, "time", lt));
        } else if (currPos > 0) {
            int id = autoTradeID.incrementAndGet();
            Order o = placeOfferLimitTIF(freshPrice, currPos, DAY);
            globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_CLOSE_LIQ));
            apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
            outputDetailedHK(symbol, "**********");
            outputDetailedHK(symbol, str("NEW", o.orderId(), "HK close Liq SELL:#:", numOrders,
                    globalIdOrderMap.get(id), "pos", currPos, "time", lt));
        }
    }

//    /**
//     * hk hilo trader for hk
//     *
//     * @param symbol     hk stock name
//     * @param nowMilli   time now
//     * @param freshPrice last hk price
//     */
//
//    private static void hkHiloTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
//        LocalTime lt = nowMilli.toLocalTime();
//
//        LocalTime cutoff = ltof(10, 0);
//        LocalTime amObservationStart = ltof(9, 19, 0);
//        LocalTime amTradingStart = ltof(9, 28);
//
//        String ticker = hkSymbolToTicker(symbol);
//        Contract ct = tickerToHKStkContract(ticker);
//        NavigableMap<LocalDateTime, Double> prices = priceMapBarDetail.get(symbol);
//        int size = hkSizeMap.getOrDefault(symbol, 100);
//
//        if (prices.size() <= 1) {
//            return;
//        }
//        if (lt.isBefore(amTradingStart) || lt.isAfter(cutoff)) {
//            return;
//        }
//        LocalTime lastKey = prices.lastKey().toLocalTime();
//        double maxSoFar = prices.entrySet().stream()
//                .filter(e -> e.getKey().toLocalTime().isAfter(amObservationStart))
//                .filter(e -> e.getKey().toLocalTime().isBefore(lastKey))
//                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);
//
//        double minSoFar = prices.entrySet().stream()
//                .filter(e -> e.getKey().toLocalTime().isAfter(amObservationStart))
//                .filter(e -> e.getKey().toLocalTime().isBefore(lastKey))
//                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);
//
//        LocalTime maxT = getFirstMaxTPred(prices, e -> e.isAfter(amObservationStart));
//        LocalTime minT = getFirstMinTPred(prices, e -> e.isAfter(amObservationStart));
//
//        if (!manualHKHilo.get(symbol).get()) {
//            if (lt.isBefore(ltof(9, 35))) {
//                outputDetailedHK(symbol, str(" setting manual HK hilo: pre 935", symbol, lt));
//                manualHKHilo.get(symbol).set(true);
//            } else {
//                if (maxT.isAfter(minT)) {
//                    outputDetailedHK(symbol, str(" setting manual HK hilo: maxT>minT", symbol, lt));
//                    hkHiloDir.put(symbol, Direction.Long);
//                    manualHKHilo.get(symbol).set(true);
//                } else if (minT.isAfter(maxT)) {
//                    outputDetailedHK(symbol, str(" setting manual HK hilo: minT>maxT", symbol, lt));
//                    hkHiloDir.put(symbol, Direction.Short);
//                    manualHKHilo.get(symbol).set(true);
//                } else {
//                    hkHiloDir.put(symbol, Direction.Flat);
//                }
//            }
//        }
//
//        long numOrders = getOrderSizeForTradeType(symbol, HK_STOCK_HILO);
//        LocalDateTime lastOrderTime = getLastOrderTime(symbol, HK_STOCK_HILO);
//        long milliLastTwo = lastTwoOrderMilliDiff(symbol, HK_STOCK_HILO);
//        long waitSec = (milliLastTwo < 60000) ? 300 : 10;
//        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
//        int buySize = size * ((numOrders == 0 || numOrders == (MAX_ORDER_HK - 1)) ? 1 : 2);
//        int sellSize = size * ((numOrders == 0 || numOrders == (MAX_ORDER_HK - 1)) ? 1 : 2);
//
//        if (numOrders >= MAX_ORDER_HK) {
//            return;
//        }
//
//        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && maxSoFar != 0.0 && minSoFar != 0.0
//                && hkShortableValueMap.getOrDefault(symbol, 0.0) > MIN_SHORT_LEVEL) {
//            if (!noMoreBuy.get() && (freshPrice > maxSoFar || maxT.isAfter(minT))
//                    && hkHiloDir.get(symbol) != Direction.Long) {
//                int id = autoTradeID.incrementAndGet();
//                Order o = placeBidLimitTIF(freshPrice, buySize, DAY);
//                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_HILO));
//                apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
//                outputDetailedHK(symbol, "**********");
//                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK hilo buy#:", numOrders, globalIdOrderMap.get(id),
//                        "max min maxT minT ", maxSoFar, minSoFar, maxT, minT, "pos", currPos,
//                        "last order T, milliLast2, waitSec", lastOrderTime, milliLastTwo, waitSec,
//                        "dir, manual ", hkHiloDir.get(symbol), manualHKHilo.get(symbol),
//                        "shortability ", hkShortableValueMap.get(symbol)));
//                hkHiloDir.put(symbol, Direction.Long);
//            } else if (!noMoreSell.get() && (freshPrice < minSoFar || minT.isAfter(maxT))
//                    && hkHiloDir.get(symbol) != Direction.Short) {
//                int id = autoTradeID.incrementAndGet();
//                Order o = placeOfferLimitTIF(freshPrice, sellSize, DAY);
//                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_HILO));
//                apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
//                outputDetailedHK(symbol, "**********");
//                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK hilo sell#:", numOrders, globalIdOrderMap.get(id),
//                        "max min maxT minT ", maxSoFar, minSoFar, maxT, minT, "pos", currPos,
//                        "last order T, milliLast2, wait Sec", lastOrderTime, milliLastTwo, waitSec,
//                        "dir, manual ", hkHiloDir.get(symbol), manualHKHilo.get(symbol),
//                        "shortability ", hkShortableValueMap.get(symbol)));
//                hkHiloDir.put(symbol, Direction.Short);
//            }
//        }
//        pr(" HK hilo#: ", numOrders, lt, ticker, symbol, "price", freshPrice, "pos", currPos,
//                "max min maxT minT", maxSoFar, minSoFar, maxT, minT,
//                " last order T", lastOrderTime, " milliLastTwo ", milliLastTwo, "wait Sec", waitSec,
//                "dir:", hkHiloDir.get(symbol), "manual?", manualHKHilo.get(symbol),
//                "shortable value?", hkShortableValueMap.get(symbol));
//    }

//    /**
//     * hk hilo trader for hk (PM)
//     *
//     * @param symbol     hk stock name
//     * @param nowMilli   time now
//     * @param freshPrice last hk price
//     */

//    private static void hkPMHiloTrader(String symbol, LocalDateTime nowMilli, double freshPrice) {
//        LocalTime lt = nowMilli.toLocalTime();
//
//        LocalTime pmCutoff = ltof(13, 30);
//        LocalTime pmObservationStart = ltof(12, 58, 0);
//        LocalTime pmTradingStart = ltof(12, 58);
//
//        String ticker = hkSymbolToTicker(symbol);
//        Contract ct = tickerToHKStkContract(ticker);
//        NavigableMap<LocalDateTime, Double> prices = priceMapBarDetail.get(symbol);
//
//        int size = hkSizeMap.getOrDefault(symbol, 100);
//
//        if (prices.size() <= 1) {
//            return;
//        }
//        if (lt.isBefore(pmTradingStart) || lt.isAfter(pmCutoff)) {
//            return;
//        }
//        LocalTime lastKey = prices.lastKey().toLocalTime();
//        double maxPMSoFar = prices.entrySet().stream()
//                .filter(e -> e.getKey().toLocalTime().isAfter(pmObservationStart))
//                .filter(e -> e.getKey().toLocalTime().isBefore(lastKey))
//                .mapToDouble(Map.Entry::getValue).max().orElse(0.0);
//
//        double minPMSoFar = prices.entrySet().stream()
//                .filter(e -> e.getKey().toLocalTime().isAfter(pmObservationStart))
//                .filter(e -> e.getKey().toLocalTime().isBefore(lastKey))
//                .mapToDouble(Map.Entry::getValue).min().orElse(0.0);
//
//        LocalDateTime maxPMT = getFirstMaxTPredLdt(prices, e -> e.isAfter(pmObservationStart));
//        LocalDateTime minPMT = getFirstMinTPredLdt(prices, e -> e.isAfter(pmObservationStart));
//
//        if (!manualHKPMHilo.get(symbol).get()) {
//            if (lt.isBefore(ltof(13, 5))) {
//                outputDetailedHK(symbol, str(" setting manual HK PM hilo: pre 13:05", lt));
//                manualHKPMHilo.get(symbol).set(true);
//            } else {
//                if (maxPMT.isAfter(minPMT)) {
//                    outputDetailedHK(symbol, str(" setting manual HK PM hilo: maxT>minT", lt));
//                    hkPMHiloDir.put(symbol, Direction.Long);
//                    manualHKPMHilo.get(symbol).set(true);
//                } else if (minPMT.isAfter(maxPMT)) {
//                    outputDetailedHK(symbol, str(" setting manual HK PM hilo: minT>maxT", lt));
//                    hkPMHiloDir.put(symbol, Direction.Short);
//                    manualHKPMHilo.get(symbol).set(true);
//                } else {
//                    hkPMHiloDir.put(symbol, Direction.Flat);
//                }
//            }
//        }
//
//        long numOrders = getOrderSizeForTradeType(symbol, HK_STOCK_PMHILO);
//        LocalDateTime lastOrderTime = getLastOrderTime(symbol, HK_STOCK_PMHILO);
//        long milliLastTwo = lastTwoOrderMilliDiff(symbol, HK_STOCK_PMHILO);
//        long waitSec = (milliLastTwo < 60000) ? 300 : 10;
//        double currPos = ibPositionMap.getOrDefault(symbol, 0.0);
//        int buySize = size * ((numOrders == 0 || numOrders == (MAX_ORDER_HK - 1)) ? 1 : 2);
//        int sellSize = size * ((numOrders == 0 || numOrders == (MAX_ORDER_HK - 1)) ? 1 : 2);
//
//        if (numOrders >= MAX_ORDER_HK) {
//            return;
//        }
//
//        if (SECONDS.between(lastOrderTime, nowMilli) > waitSec && maxPMSoFar != 0.0 && minPMSoFar != 0.0
//                && hkShortableValueMap.getOrDefault(symbol, 0.0) > MIN_SHORT_LEVEL) {
//            if (!noMoreBuy.get() && (freshPrice > maxPMSoFar || maxPMT.isAfter(minPMT))
//                    && hkPMHiloDir.get(symbol) != Direction.Long) {
//                int id = autoTradeID.incrementAndGet();
//                Order o = placeBidLimitTIF(freshPrice, buySize, DAY);
//                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_PMHILO));
//                apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
//                outputDetailedHK(symbol, "**********");
//                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK PM hilo buy#:", numOrders,
//                        globalIdOrderMap.get(id),
//                        "max min maxT minT ", maxPMSoFar, minPMSoFar, maxPMT, minPMT, "pos", currPos,
//                        "last order T, milliLast2, waitSec", lastOrderTime, milliLastTwo, waitSec,
//                        "dir, manual ", hkPMHiloDir.get(symbol), manualHKPMHilo.get(symbol),
//                        "shortability ", hkShortableValueMap.get(symbol)));
//                hkPMHiloDir.put(symbol, Direction.Long);
//            } else if (!noMoreSell.get() && (freshPrice < minPMSoFar || minPMT.isAfter(maxPMT))
//                    && hkPMHiloDir.get(symbol) != Direction.Short) {
//                int id = autoTradeID.incrementAndGet();
//                Order o = placeOfferLimitTIF(freshPrice, sellSize, DAY);
//                globalIdOrderMap.put(id, new OrderAugmented(symbol, nowMilli, o, HK_STOCK_PMHILO));
//                apcon.placeOrModifyOrder(ct, o, new GuaranteeHKHandler(id, apcon));
//                outputDetailedHK(symbol, "**********");
//                outputDetailedHK(symbol, str("NEW", o.orderId(), "HK PM hilo sell#:", numOrders,
//                        globalIdOrderMap.get(id),
//                        "max min maxT minT ", maxPMSoFar, minPMSoFar, maxPMT, minPMT, "pos", currPos,
//                        "last order T, milliLast2, wait Sec", lastOrderTime, milliLastTwo, waitSec,
//                        "dir, manual ", hkPMHiloDir.get(symbol), manualHKPMHilo.get(symbol),
//                        "shortability ", hkShortableValueMap.get(symbol)));
//                hkPMHiloDir.put(symbol, Direction.Short);
//            }
//        }
//        pr(" HK PM hilo#: ", numOrders, lt, ticker, symbol, "price", freshPrice, "pos", currPos,
//                "max min maxT minT", maxPMSoFar, minPMSoFar, maxPMT, minPMT,
//                " last order T", lastOrderTime, " milliLastTwo ", milliLastTwo, "wait Sec", waitSec,
//                "dir:", hkPMHiloDir.get(symbol), "manual?", manualHKPMHilo.get(symbol),
//                "shortable value?", hkShortableValueMap.get(symbol));
//    }


    static LocalDate getSecondLastBD(LocalDate d) {
        LocalDate res = d.plusMonths(1L).withDayOfMonth(1);
        int i = 0;
        while (i != 2) {
            res = res.minusDays(1);
            if (res.getDayOfWeek() != DayOfWeek.SATURDAY && res.getDayOfWeek() != DayOfWeek.SUNDAY) {
                i = i + 1;
            }
        }
        return res;
    }

}
