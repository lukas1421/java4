package apidemo;

import auxiliary.SimpleBar;

import static apidemo.ChinaData.priceMapBar;
import static apidemo.ChinaData.sizeTotalMap;
import static apidemo.ChinaStock.sizeMap;
import static apidemo.ChinaStock.*;
import static apidemo.XU.indexPriceSina;
import static apidemo.XU.indexVol;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;

class SinaStock implements Runnable {

    static Map<String, Double> weightMapA50 = new HashMap<>();

    static private final SinaStock sinastock = new SinaStock();

    static SinaStock getInstance() {
        return sinastock;
    }

    //static List<String> dataList;
    //static List<String> dataListSH;
    //static List<String> dataListSZ;
    //static final String listNames =  weightMap.entrySet().stream().map(Map.Entry::getKey).collect(joining(","));
    //System.out.println(" list name is " + listNames);
    static String urlString;
    static String urlStringSH;
    static String urlStringSZ;
    static final Pattern DATA_PATTERN = Pattern.compile("(?<=var\\shq_str_)((?:sh|sz)\\d{6})");
    Matcher matcher;
    String line;

    static final double OPEN = getOpen();
    static double rtn = 0.0;
    static double sinaVol = 0.0;
    static double currPrice = 0.0;
    static final Predicate<LocalDateTime> FUT_OPEN_PRED = (lt)
            -> !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SATURDAY) && !lt.toLocalDate().getDayOfWeek().equals(DayOfWeek.SUNDAY)
            && lt.toLocalTime().isAfter(LocalTime.of(9, 0, 30));

    final Predicate<LocalTime> FUT_OPEN = (lt) -> lt.isAfter(LocalTime.of(9, 0, 0));

    static final Predicate<LocalTime> DATA_COLLECTION_TIME = (LocalTime lt) -> (lt.isAfter(LocalTime.of(9, 14)) && lt.isBefore(LocalTime.of(11, 35)))
            || (lt.isAfter(LocalTime.of(12, 58)) && lt.isBefore(LocalTime.of(15, 05)));

    private SinaStock() {
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH + "FTSEA50Ticker.txt")))) {
            List<String> dataA50;
            while ((line = reader1.readLine()) != null) {
                dataA50 = Arrays.asList(line.split("\t"));
                weightMapA50.put(dataA50.get(0), pd(dataA50, 1));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {

        //listNameSH = 
        //urlString = "http://hq.sinajs.cn/list=" + listNames;
        urlStringSH = "http://hq.sinajs.cn/list=" + listNameSH;
        urlStringSZ = "http://hq.sinajs.cn/list=" + listNameSZ;

        try {
            URL urlSH = new URL(urlStringSH);
            URL urlSZ = new URL(urlStringSZ);
            URLConnection urlconnSH = urlSH.openConnection();
            URLConnection urlconnSZ = urlSZ.openConnection();
            LocalTime lt = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);

            getInfoFromURLConn(lt, urlconnSH);
            getInfoFromURLConn(lt, urlconnSZ);

            if (FUT_OPEN.test(LocalTime.now())) {
                rtn = weightMapA50.entrySet().stream().mapToDouble(a -> returnMap.getOrDefault(a.getKey(), 0.0) * a.getValue()).sum();
                currPrice = OPEN * (1 + (Math.round(rtn) / 10000d));

                sinaVol = weightMapA50.entrySet().stream()
                        .mapToDouble(a -> sizeMap.getOrDefault(a.getKey(), 0L).doubleValue() * a.getValue() / 100d).sum();

                if (indexPriceSina.containsKey(lt)) {
                    indexPriceSina.get(lt).add(currPrice);
                } else {
                    indexPriceSina.put(lt, new SimpleBar(currPrice));
                }

                if (priceMapBar.containsKey("FTSEA50")) {
                    if (priceMapBar.get("FTSEA50").containsKey(lt)) {
                        priceMapBar.get("FTSEA50").get(lt).add(currPrice);
                    } else {
                        priceMapBar.get("FTSEA50").put(lt, new SimpleBar(currPrice));
                    }
                } else {
                    priceMapBar.put("FTSEA50", (ConcurrentSkipListMap) indexPriceSina);
                }
                indexVol.put(lt, sinaVol);
                openMap.put("FTSEA50", OPEN);
                sizeMap.put("FTSEA50", Math.round(sinaVol));
                sizeTotalMap.get("FTSEA50").put(lt, sinaVol);
                //sizeTotalMap.put("FTSEA50", (ConcurrentSkipListMap)indexVol);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    static void getInfoFromURLConn(LocalTime lt, URLConnection conn) {

        String line;
        Matcher matcher;
        List<String> datalist;

        try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
            while ((line = reader2.readLine()) != null) {
                matcher = DATA_PATTERN.matcher(line);
                datalist = Arrays.asList(line.split(","));

                while (matcher.find()) {
                    String ticker = matcher.group(1);

                    if (pd(datalist, 3) > 0.0001 && pd(datalist, 1) > 0.0001) {
                        openMap.put(ticker, pd(datalist, 1));
                        closeMap.put(ticker, pd(datalist, 2));
                        priceMap.put(ticker, pd(datalist, 3));
                        maxMap.put(ticker, pd(datalist, 4));
                        minMap.put(ticker, pd(datalist, 5));
                        returnMap.put(ticker, 100d * (pd(datalist, 3) / pd(datalist, 2) - 1));
                        sizeMap.put(ticker, Math.round(pd(datalist, 9) / 1000000d));

                        if (priceMapBar.containsKey(ticker) && sizeTotalMap.containsKey(ticker) && DATA_COLLECTION_TIME.test(lt)) {
                            double last = pd(datalist, 3);
                            //priceMapPlain.get(ticker).put(lt,last);
                            sizeTotalMap.get(ticker).put(lt, pd(datalist, 9) / 1000000d);

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
                        ChinaData.priceMapBar.get(ticker).put(lt, new SimpleBar(pd(datalist, 2)));
                        ChinaStock.closeMap.put(ticker, pd(datalist, 2));
                        ChinaStock.priceMap.put(ticker, pd(datalist, 2));
                        ChinaStock.returnMap.put(ticker, 0.0);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static double getOpen() {
        String l;
        double temp = 0.0;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(ChinaMain.GLOBALPATH + "output.txt")))) {
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

    static double pd(List<String> l, int index) {
        return (l.size() > index) ? Double.parseDouble(l.get(index)) : 0.0;
    }

    static void updateBidAskMap(String ticker, LocalTime t, List l, BidAsk ba, Map<String, ? extends NavigableMap<LocalTime, VolBar>> mp) {
        int factor = ba.getValue() * 10;
        if (mp.get(ticker).containsKey(t)) {
            mp.get(ticker).get(t).fillAll(pd(l, 10 + factor), pd(l, 12 + factor), pd(l, 14 + factor), pd(l, 16 + factor), pd(l, 18 + factor));
        } else {
            mp.get(ticker).put(t, new VolBar(pd(l, 10 + factor), pd(l, 12 + factor), pd(l, 14 + factor), pd(l, 16 + factor), pd(l, 18 + factor)));

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
