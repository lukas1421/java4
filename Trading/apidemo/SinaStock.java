package apidemo;

import auxiliary.SimpleBar;
import auxiliary.VolBar;
import historical.HistChinaStocks;
import utility.Utility;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static apidemo.ChinaData.priceMapBar;
import static apidemo.ChinaData.sizeTotalMap;
import static apidemo.ChinaStock.*;
import static apidemo.XU.indexPriceSina;
import static apidemo.XU.indexVol;

public class SinaStock implements Runnable {

    //static File = new File()

    static Map<String, Double> weightMapA50 = new HashMap<>();

    static private final SinaStock sinastock = new SinaStock();

    static SinaStock getInstance() {
        return sinastock;
    }

    private static final Pattern DATA_PATTERN = Pattern.compile("(?<=var\\shq_str_)((?:sh|sz)\\d{6})");
    Matcher matcher;
    String line;
    //public static volatile LocalDate mostRecentTradingDay = LocalDate.now();

    public static final double OPEN = getOpen();
    static volatile double rtn = 0.0;
    private static final Predicate<LocalDateTime> FUT_OPEN_PRED = (lt)
            -> !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SATURDAY) && !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SUNDAY)
            && lt.toLocalTime().isAfter(LocalTime.of(9, 0, 30));

    public final Predicate<LocalTime> FUT_OPEN = (lt) -> lt.isAfter(LocalTime.of(9, 0, 0));

    private static final Predicate<LocalDateTime> DATA_COLLECTION_TIME =
            lt -> !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SATURDAY) && !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SUNDAY)
                    && ((lt.toLocalTime().isAfter(LocalTime.of(9, 14)) && lt.toLocalTime().isBefore(LocalTime.of(11, 35)))
                    || (lt.toLocalTime().isAfter(LocalTime.of(12, 58)) && lt.toLocalTime().isBefore(LocalTime.of(15, 5))));

    private SinaStock() {
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + "FTSEA50Ticker.txt")))) {
            List<String> dataA50;
            while ((line = reader1.readLine()) != null) {
                dataA50 = Arrays.asList(line.split("\t"));
                weightMapA50.put(dataA50.get(0), Utility.pd(dataA50, 1));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        //urlString = "http://hq.sinajs.cn/list=" + listNames;
        String urlStringSH = "http://hq.sinajs.cn/list=" + listNameSH;
        String urlStringSZ = "http://hq.sinajs.cn/list=" + listNameSZ;

        try {
            URL urlSH = new URL(urlStringSH);
            URL urlSZ = new URL(urlStringSZ);
            URLConnection urlconnSH = urlSH.openConnection();
            URLConnection urlconnSZ = urlSZ.openConnection();
            //LocalTime lt = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
            LocalDateTime ldt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

            getInfoFromURLConn(ldt, urlconnSH);
            getInfoFromURLConn(ldt, urlconnSZ);

            if (FUT_OPEN_PRED.test(LocalDateTime.now())) {
                rtn = weightMapA50.entrySet().stream().mapToDouble(a -> returnMap.getOrDefault(a.getKey(), 0.0) * a.getValue()).sum();
                double currPrice = OPEN * (1 + (Math.round(rtn) / 10000d));

                double sinaVol = weightMapA50.entrySet().stream()
                        .mapToDouble(a -> sizeMap.getOrDefault(a.getKey(), 0L).doubleValue() * a.getValue() / 100d).sum();

                if (indexPriceSina.containsKey(ldt.toLocalTime())) {
                    indexPriceSina.get(ldt.toLocalTime()).add(currPrice);
                } else {
                    indexPriceSina.put(ldt.toLocalTime(), new SimpleBar(currPrice));
                }

                if (priceMapBar.containsKey("FTSEA50")) {
                    if (priceMapBar.get("FTSEA50").containsKey(ldt.toLocalTime())) {
                        priceMapBar.get("FTSEA50").get(ldt.toLocalTime()).add(currPrice);
                    } else {
                        priceMapBar.get("FTSEA50").put(ldt.toLocalTime(), new SimpleBar(currPrice));
                    }
                } else {
                    priceMapBar.put("FTSEA50", (ConcurrentSkipListMap) indexPriceSina);
                }
                indexVol.put(ldt.toLocalTime(), sinaVol);
                openMap.put("FTSEA50", OPEN);
                sizeMap.put("FTSEA50", Math.round(sinaVol));
                sizeTotalMap.get("FTSEA50").put(ldt.toLocalTime(), sinaVol);
                //sizeTotalMap.put("FTSEA50", (ConcurrentSkipListMap)indexVol);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void getInfoFromURLConn(LocalDateTime ldt, URLConnection conn) {

        String line;
        Matcher matcher;
        List<String> datalist;
        LocalTime lt = ldt.toLocalTime();

        try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
            while ((line = reader2.readLine()) != null) {
                matcher = DATA_PATTERN.matcher(line);
                datalist = Arrays.asList(line.split(","));

                while (matcher.find()) {
                    String ticker = matcher.group(1);

                    if (Utility.pd(datalist, 3) > 0.0001 && Utility.pd(datalist, 1) > 0.0001) {
                        openMap.put(ticker, Utility.pd(datalist, 1));
                        closeMap.put(ticker, Utility.pd(datalist, 2));
                        priceMap.put(ticker, Utility.pd(datalist, 3));
                        maxMap.put(ticker, Utility.pd(datalist, 4));
                        minMap.put(ticker, Utility.pd(datalist, 5));
                        returnMap.put(ticker, 100d * (Utility.pd(datalist, 3) / Utility.pd(datalist, 2) - 1));
                        sizeMap.put(ticker, Math.round(Utility.pd(datalist, 9) / 1000000d));
                        HistChinaStocks.recentTradingDate = LocalDate.parse(datalist.get(30));
                        //ChinaData.outputRecentTradingDate();
                        //System.out.println(" most recent trading day " + mostRecentTradingDay);
                        //System.out.println(" last data available date " + datalist.get(30) + " " + datalist.get(31));

                        if (priceMapBar.containsKey(ticker) && sizeTotalMap.containsKey(ticker) && DATA_COLLECTION_TIME.test(ldt)) {
                            double last = Utility.pd(datalist, 3);
                            //priceMapPlain.get(ticker).put(lt,last);
                            sizeTotalMap.get(ticker).put(lt, Utility.pd(datalist, 9) / 1000000d);

                            if (priceMapBar.get(ticker).containsKey(lt)) {
                                priceMapBar.get(ticker).get(lt).add(last);
                            } else {
                                priceMapBar.get(ticker).put(lt, new SimpleBar(last));
                            }

                            try {
                                ChinaStock.process(ticker, lt, last);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                        //updateBidAskMap(ticker, lt, datalist, BidAsk.BID, bidMap);
                        //updateBidAskMap(ticker, lt, datalist, BidAsk.ASK, askMap);
                    } else {
                        if (priceMapBar.containsKey(ticker) && sizeTotalMap.containsKey(ticker) && DATA_COLLECTION_TIME.test(ldt)) {
                            ChinaData.priceMapBar.get(ticker).put(lt, new SimpleBar(Utility.pd(datalist, 2)));
                        }

                        ChinaStock.closeMap.put(ticker, Utility.pd(datalist, 2));
                        ChinaStock.priceMap.put(ticker, Utility.pd(datalist, 2));
                        ChinaStock.returnMap.put(ticker, 0.0);

                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static double getOpen() {
        String l;
        double temp = 0.0;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(MorningTask.output)))) {
            while ((l = reader1.readLine()) != null) {
                List<String> s = Arrays.asList(l.split("\t"));
                if (s.get(0).equals("FTSE A50")) {
                    temp = Double.parseDouble(s.get(1));
                    //System.out.println(" open is " + temp);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return temp;
    }

    static LocalTime gt() {
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        return LocalTime.of(now.getHour(), now.getMinute(), (now.getSecond() / 5) * 5);
    }

    static void updateBidAskMap(String ticker, LocalTime t, List l, BidAsk ba, Map<String, ? extends NavigableMap<LocalTime, VolBar>> mp) {
        int factor = ba.getValue() * 10;
        if (mp.get(ticker).containsKey(t)) {
            mp.get(ticker).get(t).fillAll(Utility.pd(l, 10 + factor), Utility.pd(l, 12 + factor), Utility.pd(l, 14 + factor), Utility.pd(l, 16 + factor), Utility.pd(l, 18 + factor));
        } else {
            mp.get(ticker).put(t, new VolBar(Utility.pd(l, 10 + factor), Utility.pd(l, 12 + factor), Utility.pd(l, 14 + factor), Utility.pd(l, 16 + factor), Utility.pd(l, 18 + factor)));

        }
    }

    enum BidAsk {
        BID(0), ASK(1);
        int val;

        BidAsk(int i) {
            val = i;
        }

        int getValue() {
            return val;
        }

        void setValue(int i) {
            val = i;
        }
    }
}
