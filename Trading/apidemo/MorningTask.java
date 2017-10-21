package apidemo;

import static utility.Utility.pd;

import auxiliary.SimpleBar;
import client.Contract;
import client.Types;
import controller.ApiConnection.ILogger.DefaultLogger;
import controller.ApiController;
import controller.ApiController.IConnectionHandler.DefaultConnectionHandler;
import handler.HistoricalHandler;
import utility.Utility;

import java.io.*;
//import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MorningTask implements HistoricalHandler {

    static File output = new File(ChinaMain.GLOBALPATH + "morningOutput.txt");
    static File bocOutput = new File(ChinaMain.GLOBALPATH + "BOCUSD.txt");
    static File fxOutput = new File(ChinaMain.GLOBALPATH + "fx.txt");
    static final String tdxPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export\\" : "J:\\TDX\\T0002\\export\\";
    static final Pattern DATA_PATTERN = Pattern.compile("(?<=var\\shq_str_)((?:sh|sz)\\d{6})");
    static String indices = "sh000300,sh000001,sz399006,sz399001,sh000905,sh000016";
    static String urlString;
    //static String line;
    static List<String> dataList = new ArrayList<>();
    //static Proxy proxy = new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",1080));
    static Proxy proxy = Proxy.NO_PROXY;

    public static void runThis() {
        MorningTask mt = new MorningTask();

        //processShcomp();
        mt.getFX();
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            //writeIndex(out);
            writeIndexTDX(out);
            writeETF(out);
            writeA50(out);
            writeA50FT(out);
            writeXIN0U(out);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        getBOCFX2();
        processShcomp();

        System.out.println("done and starting exiting sequence in 5");
        ScheduledExecutorService es = Executors.newSingleThreadScheduledExecutor();
        es.scheduleAtFixedRate(() -> {
            System.out.println(" countDown ... ");
        }, 0, 1, TimeUnit.SECONDS);

        es.schedule(() -> {
            System.exit(0);
        }, 5, TimeUnit.SECONDS);
    }


    //        static void simpleWrite(LinkedList<String> s) {
//        try (BufferedWriter out = new BufferedWriter(new FileWriter(testOutput))){
//            String toWrite;
//            while((toWrite=s.poll())!=null) {
//                out.append(toWrite);
//                out.newLine();
//            }
//        } catch ( IOException x) {
//            x.printStackTrace();
//        }
//    }
    static void simpleWrite(String s, boolean b) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, b))) {
            out.append(s);
            out.newLine();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    public static void clearFile(File f) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(f, false))) {
            out.flush();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    public static void simpleWriteToFile(String s, boolean b, File f) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(f, b))) {
            out.append(s);
            out.newLine();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    static void writeIndexTDX(BufferedWriter out) {
        String line;
        List<String> ind = Arrays.asList(indices.split(","));
        System.out.println(ind);
        String currentLine = "";
        String previousLine = "";
        for (String s : ind) {
            String name = s.substring(0, 2).toUpperCase() + "#" + s.substring(2) + ".txt";
            String filePath = tdxPath + name + ".txt";
            currentLine = "";
            previousLine = "";
            try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(tdxPath + name), "GBK"))) {
                while ((line = reader1.readLine()) != null && !line.startsWith("数据来源")) {
                    previousLine = currentLine;
                    currentLine = line;
                }
                List<String> todayList = Arrays.asList(currentLine.split("\t"));
                List<String> ytdList = Arrays.asList(previousLine.split("\t"));

                String output = Utility.getStrTabbed(s, pd(todayList, 4), pd(ytdList, 4),
                        Double.toString(Math.round(10000d * (pd(todayList, 4) / pd(ytdList, 4) - 1)) / 100d) + "%");
                System.out.println(" stock return " + s + " " + output);

                out.write(output);
                out.newLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static void writeIndex(BufferedWriter out) {
        String line;
        try {
            urlString = "http://hq.sinajs.cn/list=" + indices;
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection();
            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream(), "gbk"))) {
                while ((line = reader2.readLine()) != null) {
                    Matcher matcher = DATA_PATTERN.matcher(line);
                    dataList = Arrays.asList(line.split(","));
                    if (matcher.find()) {
                        out.write(Utility.getStrTabbed(matcher.group(1), pd(dataList, 3), pd(dataList, 2),
                                Double.toString(Math.round(10000d * (pd(dataList, 3) / pd(dataList, 2) - 1)) / 100d) + "%"));
                        out.newLine();
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    static void writeETF(BufferedWriter out) {
        String line;
        List<String> etfs = Arrays.asList("2823:HK", "2822:HK", "3147:HK", "3188:HK", "FXI:US", "CNXT:US", "ASHR:US", "ASHS:US");
        Pattern p = Pattern.compile("(?<=\\\"price\\\":)(\\d+(.\\d+)?)");
        Pattern p2 = Pattern.compile("(?<=\\\"netAssetValue\\\":)\\d+(.\\d+)?");
        Pattern p3 = Pattern.compile("(?<=\\\"netAssetValueDate\\\":\\\")\\d{4}-\\d{2}-\\d{2}(?=\\\")");

        //Proxy proxy = new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",1080));
        for (String e : etfs) {
            urlString = "https://www.bloomberg.com/quote/" + e;
            System.out.println(" etf is " + e);

            try {

                URL url = new URL(urlString);
                //System.out.println(" default port "+url.getDefaultPort());
                //System.out.println(" path "+url.getPath());
                URLConnection urlconn = url.openConnection(proxy);
                //urlconn.
                //System.out.println(" content is" + urlconn.getContent());
                //urlconn.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");

                try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(e);
                    sb.append("\t");

                    while ((line = reader2.readLine()) != null) {
                        Matcher matcher = p.matcher(line);
                        Matcher m2 = p2.matcher(line);
                        Matcher m3 = p3.matcher(line);

                        while (matcher.find()) {
                            sb.append(matcher.group());
                            sb.append("\t");
                        }

                        while (m2.find()) {
                            sb.append(m2.group());
                            sb.append("\t");
                        }

                        while (m3.find()) {
                            sb.append(m3.group());
                        }
                    }
                    out.append(sb);
                    out.newLine();
                    System.out.println(" sb " + sb);
                }

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    static void writeA50(BufferedWriter out) {

        urlString = "https://www.investing.com/indices/ftse-china-a50";
        String line;
        //Pattern p = Pattern.compile("(?<=div class=\\\"price\\\">)\\d+\\.\\d+(?=</div>)");
        Pattern p = Pattern.compile("(?<=<td id=\\\"_last_28930\\\" class=\\\"pid-28930-last\\\">)\\d+,\\d+\\.\\d+");
        //Pattern p = Pattern.compile("(?<=pid-28930-last\\\">)\\d+");
        //Pattern p2 = Pattern.compile("(?<=\\\"netAssetValue\\\":)\\d+.\\d+(?=,) ") ;
        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(proxy);
            urlconn.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");

            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                while ((line = reader2.readLine()) != null) {
                    Matcher m = p.matcher(line);
                    while (m.find()) {
                        out.write("FTSE A50" + "\t" + m.group().replace(",", ""));
                        out.newLine();
                        System.out.println(m.group());
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    static LocalDate getLastBizDate(LocalDate inDate) {
        if (inDate.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
            return inDate.minusDays(3);
        } else {
            return inDate.minusDays(1);
        }
    }

    static void writeA50FT(BufferedWriter out) {
        String line;
        urlString = "https://markets.ft.com/data/indices/tearsheet/historical?s=FTXIN9:FSI";
        Pattern p;
        //Pattern p = Pattern.compile("(?<=reactid=\\\"270\\\">)\\d+,\\d+\\.\\d+");
        //Pattern p = Pattern.compile("(?<=\\\"270\\\">)...........................................................");
        LocalDate dt = getLastBizDate(LocalDate.now());
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy", Locale.US);
        String ds = dt.format(f);
        System.out.println(ds);

        p = Pattern.compile("(?<=" + ds + "</span>)(.*?)(?:<span class)");

        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(proxy);

            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                while ((line = reader2.readLine()) != null) {
                    Matcher m = p.matcher(line);
                    while (m.find()) {
                        //System.out.println (" found " + line);
                        //out.write("FTSE A50 " + m.group().replace(",", ""));
                        System.out.println(m.group());
                        List<String> sp = Arrays.asList(m.group(1).replace(",", "")
                                .split("</td><td>")); //m.group()
                        System.out.println(Double.parseDouble(sp.get(4)));
                        out.append("FTSEA50 2" + "\t" + sp.get(4));
                        out.newLine();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    static void writeXIN0U(BufferedWriter out) {
        System.out.println((" getting XIN0U"));
        String line;
        urlString = "https://finance.yahoo.com/quote/XIN0UN.FGI?ltr=1";
        Pattern p;
        p = Pattern.compile("\\\"exchangeName\\\":\\\"FTSE Index\\\".*?\\\"regularMarketPrice\\\":\\{\\\"raw\\\":(\\d+(\\.\\d+)?)");

        //Proxy proxy = proxy;
        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(proxy);
            //URLConnection urlconn = url.openConnection();
            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                while ((line = reader2.readLine()) != null) {
                    Matcher m = p.matcher(line);
                    while (m.find()) {
                        System.out.println(m.group(1));
                        System.out.println("XIN0U" + "\t" + m.group(1));
                        out.append("XIN0U" + "\t" + m.group(1));
                        out.newLine();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    static void getBOCFX() {
        urlString = "http://www.boc.cn/sourcedb/whpj";
        String line1;
        Pattern p = Pattern.compile("美元</td>(.*?)(?:</tr>)");
        Pattern p1 = Pattern.compile("(?s)美元</td>.*");
        Pattern p2 = Pattern.compile("<td>(.*?)</td>");
        Pattern p3 = Pattern.compile("</tr>");
        boolean found = false;
        List<String> l = new LinkedList<>();

        try {
            URL url = new URL(urlString);
            URLConnection urlconn = url.openConnection(proxy);

            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconn.getInputStream()))) {
                while ((line1 = reader2.readLine()) != null) {
                    if (!found) {
                        Matcher m = p1.matcher(line1);
                        while (m.find()) {
                            found = true;
                        }
                    } else {
                        if (p3.matcher(line1).find()) {
                            break;
                        } else {
                            Matcher m2 = p2.matcher(line1);
                            while (m2.find()) {
                                l.add(m2.group(1));
                            }
                        }
                    }
                }
                System.out.println("l " + l);

                if (l.size() > 0) {
                    clearFile(bocOutput);
                    simpleWriteToFile("BOCFX" + "\t" + Double.parseDouble(l.get(4)) / 100d + "\t" + l.get(5) + "\t" + l.get(6), true,bocOutput);
                    //simpleWrite("BOCFX" + "\t" + Double.parseDouble(l.get(4)) / 100d + "\t" + l.get(5) + "\t" + l.get(6), true);
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void getBOCFX2() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(bocOutput), "GBK"))) {
            while ((line = reader1.readLine()) != null) {
                System.out.println(" outputting BOCFX " + line);
                simpleWrite(line,true);
            }
        } catch (IOException io) {
            io.printStackTrace();
        }
    }


    public static void handleHistoricalData(String date, double c) {
        System.out.println(" handling historical data ");

        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            System.out.println(" hour is " + cal.get(Calendar.HOUR_OF_DAY));
            System.out.println(" Date " + dt.toString() + " close " + c);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            System.out.println(sdf.format(dt));

            switch (cal.get(Calendar.HOUR_OF_DAY)) {
                case 13:
                    simpleWrite("HK NOON" + "\t" + c + "\t" + sdf.format(dt) + "\t" + cal.get(Calendar.HOUR_OF_DAY), false);
                    break;
                case 16:
                    simpleWrite("HK CLOSE" + "\t" + c + "\t" + sdf.format(dt) + "\t" + cal.get(Calendar.HOUR_OF_DAY), true);
                    break;
                case 4:
                    simpleWrite("US CLOSE" + "\t" + c + "\t" + sdf.format(dt) + "\t" + cal.get(Calendar.HOUR_OF_DAY), true);
                    simpleWriteToFile("SGXA50" + "\t" + c, false, fxOutput);
                    break;
            }

//            if(cal.get(Calendar.HOUR_OF_DAY)==4 || cal.get(Calendar.HOUR_OF_DAY)==13
//                    || cal.get(Calendar.HOUR_OF_DAY)==16) {
//                simpleWrite(dt+"\t"+c);
//            }
            //simpleWrite(l);
        }
    }

    void getFX() {
        ApiController ap = new ApiController(new DefaultConnectionHandler(), new DefaultLogger(), new DefaultLogger());
        try {
            //ap.connect( "127.0.0.1", 4001, 2,"" );
            ap.connect("127.0.0.1", 7496, 2, "");
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        Contract c = new Contract();
        c.symbol("USD");
        c.secType(Types.SecType.CASH);
        c.exchange("IDEALPRO");
        c.currency("CNH");
        c.strike(0.0);
        c.right(Types.Right.None);
        c.secIdType(Types.SecIdType.None);

        LocalDateTime lt = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
        String formatTime = lt.format(dtf);

        System.out.println(" format time " + formatTime);

        ap.reqHistoricalDataSimple(this, c, formatTime, 2, Types.DurationUnit.DAY,
                Types.BarSize._1_hour, Types.WhatToShow.MIDPOINT, false);
    }

    static void processShcomp() {

        final String tdxPath = ChinaMain.tdxPath;
        File output = new File(ChinaMain.GLOBALPATH + "shcomp.txt");
        LocalDate t = LocalDate.now();

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(ChinaMain.GLOBALPATH + "mostRecentTradingDate.txt"), "gbk"))) {
            String line;
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                t = LocalDate.parse(al1.get(0));
                System.out.println(" current t is " + t);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }


        final String dateString = t.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        System.out.println(" date is " + dateString);

        boolean found = false;
        String name = "SH#000001.txt";
        String line;
        NavigableMap<LocalTime, SimpleBar> dataMap = new TreeMap<>();

        //AmOpen	931	935	940	AmClose	AmMax	AmMin	AmMaxT	AmMinT	PmOpen	Pm1310	PmClose	PmMax	PmMin	PmMaxT	PmMinT
        final String headers = Utility.getStrTabbed("AmOpen", "931", "935", "940", "AmClose",
                "AmMax", "AmMin", "AmMaxT", "AmMinT", "PmOpen", "Pm1310", "PmClose", "PmMax", "PmMin", "PmMaxT", "PmMinT");
        String data;

        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(tdxPath + name)))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                if (al1.get(0).equals(dateString)) {
                    found = true;
                    String time = al1.get(1);
                    LocalTime lt = LocalTime.of(Integer.parseInt(time.substring(0, 2)), Integer.parseInt(time.substring(2)));
                    dataMap.put(lt.minusMinutes(1L), new SimpleBar(Double.parseDouble(al1.get(2)), Double.parseDouble(al1.get(3)),
                            Double.parseDouble(al1.get(4)), Double.parseDouble(al1.get(5))));
                }
            }

        } catch (IOException | NumberFormatException ex) {
            ex.printStackTrace();
        }

        double amopen = dataMap.firstEntry().getValue().getOpen();
        double c931 = dataMap.ceilingEntry(LocalTime.of(9, 31)).getValue().getClose();
        double c935 = dataMap.ceilingEntry(LocalTime.of(9, 35)).getValue().getClose();
        double c940 = dataMap.ceilingEntry(LocalTime.of(9, 40)).getValue().getClose();
        double amclose = dataMap.floorEntry(LocalTime.of(11, 30)).getValue().getClose();
        double ammax = dataMap.entrySet().stream().filter(e -> e.getKey().isBefore(LocalTime.of(11, 31)))
                .map(Map.Entry::getValue).mapToDouble(SimpleBar::getHigh).max().getAsDouble();
        double ammin = dataMap.entrySet().stream().filter(e -> e.getKey().isBefore(LocalTime.of(11, 31)))
                .map(Map.Entry::getValue).mapToDouble(SimpleBar::getLow).min().getAsDouble();
        LocalTime ammaxt = dataMap.entrySet().stream().filter(e -> e.getKey().isBefore(LocalTime.of(11, 31)))
                .max(Comparator.comparingDouble(e -> e.getValue().getHigh())).map(Map.Entry::getKey).get();
        LocalTime ammint = dataMap.entrySet().stream().filter(e -> e.getKey().isBefore(LocalTime.of(11, 31)))
                .min(Comparator.comparingDouble(e -> e.getValue().getLow())).map(Map.Entry::getKey).get();

        double pmopen = dataMap.ceilingEntry(LocalTime.of(13, 0)).getValue().getOpen();
        double pm1310 = dataMap.ceilingEntry(LocalTime.of(13, 10)).getValue().getClose();
        double pmclose = dataMap.floorEntry(LocalTime.of(15, 0)).getValue().getClose();
        double pmmax = dataMap.entrySet().stream().filter(e -> e.getKey().isAfter(LocalTime.of(12, 59))).
                map(Map.Entry::getValue).mapToDouble(SimpleBar::getHigh).max().getAsDouble();
        double pmmin = dataMap.entrySet().stream().filter(e -> e.getKey().isAfter(LocalTime.of(12, 59))).
                map(Map.Entry::getValue).mapToDouble(SimpleBar::getLow).min().getAsDouble();
        LocalTime pmmaxt = dataMap.entrySet().stream().filter(e -> e.getKey().isAfter(LocalTime.of(12, 59)))
                .max(Comparator.comparingDouble(e -> e.getValue().getHigh())).map(Map.Entry::getKey).get();
        LocalTime pmmint = dataMap.entrySet().stream().filter(e -> e.getKey().isAfter(LocalTime.of(12, 59)))
                .min(Comparator.comparingDouble(e -> e.getValue().getLow())).map(Map.Entry::getKey).get();

        /*        final String headers = ChinaStockHelper.getStrTabbed("AmOpen","931","935","940","AmClose",
                "AmMax","AmMin","AmMaxT","AmMinT","PmOpen","Pm1310","PmClose","PmMax","PmMin","PmMaxT","PmMinT");
        String data;*/
        data = Utility.getStrTabbed(amopen, c931, c935, c940, amclose, ammax, ammin, convertLTtoString(ammaxt),
                convertLTtoString(ammint), pmopen, pm1310, pmclose, pmmax, pmmin,
                convertLTtoString(pmmaxt), convertLTtoString(pmmint));

        clearFile(output);
        simpleWriteToFile(headers, true, output);
        simpleWriteToFile(data, true, output);

    }

    static String convertLTtoString(LocalTime t) {
        return Integer.toString(t.getHour() * 100 + t.getMinute());
    }

    public static void main(String[] args) {
        //MorningTask mt = new MorningTask();
        //mt.getIndices();

        //MorningTask.getFX();
        MorningTask.runThis();
    }

    @Override
    public void handleHist(String name, String date, double open, double high, double low, double close) {
        System.out.println(" handling historical data ");

        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            System.out.println(" hour is " + cal.get(Calendar.HOUR_OF_DAY));
            System.out.println(" Date " + dt.toString() + " close " + close);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            System.out.println(sdf.format(dt));

            switch (cal.get(Calendar.HOUR_OF_DAY)) {
                case 13:
                    simpleWrite("HK NOON" + "\t" + close + "\t" + sdf.format(dt) + "\t" + cal.get(Calendar.HOUR_OF_DAY), false);
                    break;
                case 16:
                    simpleWrite("HK CLOSE" + "\t" + close + "\t" + sdf.format(dt) + "\t" + cal.get(Calendar.HOUR_OF_DAY), true);
                    break;
                case 4:
                    simpleWrite("US CLOSE" + "\t" + close + "\t" + sdf.format(dt) + "\t" + cal.get(Calendar.HOUR_OF_DAY), true);
                    simpleWriteToFile("SGXA50" + "\t" + close, false, fxOutput);
                    break;
            }
        }
    }

    @Override
    public void actionUponFinish(String name) {

    }

}
