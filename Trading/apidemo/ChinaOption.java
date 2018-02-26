package apidemo;

import org.apache.commons.math3.distribution.NormalDistribution;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Math.*;

//import org.apache.commons.math3.*;

public class ChinaOption extends JPanel implements Runnable {
//    static String urlString;
//    static String urlStringSH;

    private static final Pattern DATA_PATTERN = Pattern.compile("(?<=var\\shq_str_)((?:sh|sz)\\d{6})");
    private static final Pattern CALL_NAME_PATTERN = Pattern.compile("(?<=var\\shq_str_OP_UP_5100501802=)\"(.*?),\"");
    private static final Pattern CALL_PATTERN = Pattern.compile("(?<=var\\shq_str_)(CON_OP_\\d{8})=\"(.*?)\";");
    //static final Pattern CALL_NAME = Pattern.compile("(?<=var\\shq_str_)(CON_OP_\\d{8})=\"");

    private static String frontMonth = "1802";
    private static String backMonth = "1803";

    private static LocalDate frontExpiry = LocalDate.of(2018, Month.FEBRUARY, 28);
    private static LocalDate backExpiry = LocalDate.of(2018, Month.MARCH, 28);

    private static double interestRate = 0.04;

    private static String primaryCall = "CON_OP_" + "10001145";

    private static HashMap<String, Double> bidMap = new HashMap<>();
    private static HashMap<String, Double> askMap = new HashMap<>();
    private static HashMap<String, Double> optionPriceMap = new HashMap<>();
    private static HashMap<String, Option> tickerOptionsMap = new HashMap<>();
    private static Map<Double, Double> strikeVolMap = new TreeMap<>();
    private static List<JLabel> labelList = new ArrayList<>();
    private static JLabel timeLabel = new JLabel();

    private static Option firstO = new Option(2.95, frontExpiry, CallPutFlag.CALL);

    private ChinaOption() {

        setLayout(new BorderLayout());
        JPanel controlPanel = new JPanel();
        JPanel dataPanel = new JPanel();
        dataPanel.setLayout(new GridLayout(10, 3));

        //JLabel timeLabel = new JLabel(LocalTime.now().toString());
        controlPanel.add(timeLabel);

        optionPriceMap.put(primaryCall, 0.0);
        bidMap.put(primaryCall, 0.0);
        askMap.put(primaryCall, 0.0);

        add(controlPanel, BorderLayout.NORTH);
        add(dataPanel, BorderLayout.SOUTH);

        JLabel j11 = new JLabel(primaryCall);
        labelList.add(j11);
        JLabel j12 = new JLabel(" option price ");
        labelList.add(j12);
        JLabel j13 = new JLabel(" stock price ");
        labelList.add(j13);
        JLabel j14 = new JLabel(" Strike ");
        labelList.add(j14);

        JLabel j15 = new JLabel(" IV ");
        labelList.add(j15);
        JLabel j16 = new JLabel(" BID ");
        labelList.add(j16);
        JLabel j17 = new JLabel(" ASK  ");
        labelList.add(j17);
        JLabel j18 = new JLabel(" Delta ");
        labelList.add(j18);
        JLabel j19 = new JLabel(" Gamma  ");
        labelList.add(j19);

        JLabel j21 = new JLabel("0.0");
        labelList.add(j21);
        JLabel j22 = new JLabel("0.0");
        labelList.add(j22);
        JLabel j23 = new JLabel("0.0");
        labelList.add(j23);
        JLabel j24 = new JLabel("0.0");
        labelList.add(j24);

        JLabel j25 = new JLabel("0.0");
        labelList.add(j25);
        JLabel j26 = new JLabel("0.0");
        labelList.add(j26);
        JLabel j27 = new JLabel("0.0");
        labelList.add(j27);
        JLabel j28 = new JLabel("0.0");
        labelList.add(j28);
        JLabel j29 = new JLabel("0.0 ");
        labelList.add(j29);

        timeLabel.setOpaque(true);
        timeLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        timeLabel.setFont(timeLabel.getFont().deriveFont(30F));
        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        labelList.forEach(l -> {
            l.setOpaque(true);
            l.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            l.setFont(l.getFont().deriveFont(30F));
            l.setHorizontalAlignment(SwingConstants.CENTER);
        });

        dataPanel.add(j11);
        dataPanel.add(j21);
        dataPanel.add(j12);
        dataPanel.add(j22);
        dataPanel.add(j13);
        dataPanel.add(j23);
        dataPanel.add(j14);
        dataPanel.add(j24);
        dataPanel.add(j15);
        dataPanel.add(j25);
        dataPanel.add(j16);
        dataPanel.add(j26);
        dataPanel.add(j17);
        dataPanel.add(j27);
        dataPanel.add(j18);
        dataPanel.add(j28);
        dataPanel.add(j19);
        dataPanel.add(j29);
    }

    static void updateData(double opPrice, double stock, double vol, double bid, double ask, Option opt) {
        SwingUtilities.invokeLater(() -> {

            labelList.get(10).setText(Utility.getStrCheckNull(opPrice));
            labelList.get(11).setText(Utility.getStrCheckNull(stock));
            labelList.get(12).setText(Utility.getStrCheckNull(opt.getStrike()));
            labelList.get(13).setText(Utility.getStrCheckNull(vol));
            labelList.get(14).setText(Utility.getStrCheckNull(bid));
            labelList.get(15).setText(Utility.getStrCheckNull(ask));

            labelList.get(16).setText(Utility.getStrCheckNull(getDelta(stock, opt.getStrike(),
                    vol, opt.getTimeToExpiry(), interestRate)));

            labelList.get(17).setText(Utility.getStrCheckNull(getGamma(stock, opt.getStrike(),
                    vol, opt.getTimeToExpiry(), interestRate)));

            timeLabel.setText(LocalTime.now().toString());

        });
    }

    @Override
    public void run() {
        try {
            String callStringFront = "http://hq.sinajs.cn/list=OP_UP_510050" + frontMonth;
            String callStringBack = "http://hq.sinajs.cn/list=OP_UP_510050" + backMonth;
            URL urlCall = new URL(callStringFront);
            String putStringFront = "http://hq.sinajs.cn/list=OP_DOWN_510050" + frontMonth;
            String putStringBack = "http://hq.sinajs.cn/list=OP_DOWN_510050" + backMonth;

            URL urlPul = new URL(putStringFront);
            URLConnection urlconnCall = urlCall.openConnection();
            URLConnection urlconnPut = urlPul.openConnection();
            Matcher m;
            String line;
            List<String> datalist;

            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(urlconnCall.getInputStream(), "gbk"))) {
                while ((line = reader2.readLine()) != null) {
                    m = CALL_NAME_PATTERN.matcher(line);

                    while (m.find()) {
                        String res = m.group(1);
                        datalist = Arrays.asList(res.split(","));
                        //System.out.println(datalist.stream().collect(Collectors.joining(",")));

                        URL allCalls = new URL("http://hq.sinajs.cn/list=" +
                                datalist.stream().collect(Collectors.joining(",")));

                        //System.out.println(" all calls is " + allCalls.toString());
                        URLConnection urlconnAllCalls = allCalls.openConnection();
                        getInfoFromURLConn(urlconnAllCalls);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (IOException ex2) {
            ex2.printStackTrace();
        }

        double pr = ChinaOption.optionPriceMap.get(primaryCall);
        double stock = get510050Price();
        double vol = simpleSolver(pr, fillInBS(stock, firstO), 0, 1);

        //System.out.println(" option price " + pr);
        //System.out.println(" stock price " + stock);
        //System.out.println(simpleSolver(pr, fillInBS(stock, firstO), 0, 1));
        updateData(pr, stock, vol, bidMap.getOrDefault(primaryCall, 0.0), askMap.getOrDefault(primaryCall, 0.0), firstO);

    }

    static void getInfoFromURLConn(URLConnection conn) {
        String line;
        Matcher matcher;

        System.out.println(" getting URL from conn ");
        try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
            while ((line = reader2.readLine()) != null) {
                matcher = CALL_PATTERN.matcher(line);

                while (matcher.find()) {
                    String resName = matcher.group(1);
                    String res = matcher.group(2);
                    //System.out.println(Arrays.asList(res.split(",")));
                    List<String> res1 = Arrays.asList(res.split(","));

                    if (resName.equals(primaryCall)) {
                        //System.out.println(res1.size());
                        //System.out.println(res1.get(37));
                        optionPriceMap.put(resName, Double.parseDouble(res1.get(2)));
                        bidMap.put(resName, Double.parseDouble(res1.get(1)));
                        askMap.put(resName, Double.parseDouble(res1.get(3)));
                        //System.out.println(" call price " + optionPriceMap.get(resName));
                    }

                    optionPriceMap.put(resName, Double.parseDouble(res1.get(2)));
                    tickerOptionsMap.put(resName, new Option(Double.parseDouble(res1.get(7)), frontExpiry,
                            CallPutFlag.CALL));


                    //System.out.println(tickerOptionsMap);

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        double stockPrice = get510050Price();

        tickerOptionsMap.forEach((k, v) ->
                strikeVolMap.put(v.getStrike(), simpleSolver(optionPriceMap.get(k), fillInBS(stockPrice, v),
                        0, 1)));

        System.out.println(" strike vol map " + strikeVolMap);
    }

    static double bs(double s, double k, double v, double t, double r) {
        double d1 = (Math.log(s / k) + (r + 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double d2 = (Math.log(s / k) + (r - 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double nd1 = (new NormalDistribution()).cumulativeProbability(d1);
        double nd2 = (new NormalDistribution()).cumulativeProbability(d2);
        double call = s * nd1 - exp(-r * t) * k * nd2;
        double put = exp(-r * t) * k * (1 - nd2) - s * (1 - nd1);

        double delta = nd1;
        double gamma = 0.4 * exp(-0.5 * pow(d1, 2)) / (s * v * sqrt(t));
        double vega = 0.4 * s * sqrt(t) / 100;

        return call;
    }

    private static double getDelta(double s, double k, double v, double t, double r) {
        //System.out.println(Utility.getStr(" s k v t r  ", s, k, v, t, r));
        double d1 = (Math.log(s / k) + (r + 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double nd1 = (new NormalDistribution()).cumulativeProbability(d1);
        return Math.round(1000d * nd1) / 1000d;
    }

    private static double getGamma(double s, double k, double v, double t, double r) {
        double d1 = (Math.log(s / k) + (r + 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double gamma = 0.4 * exp(-0.5 * pow(d1, 2)) / (s * v * sqrt(t));
        return Math.round(1000d * gamma) / 1000d;
    }

    private static DoubleUnaryOperator fillInBS(double s, Option opt) {
        //System.out.println(" filling in bs ");
        //System.out.println(" strike " + opt.getStrike());
        //System.out.println(" t " + opt.getTimeToExpiry());
        //System.out.println(" rate " + opt.getRate());
        return (double v) -> bs(s, opt.getStrike(), v, opt.getTimeToExpiry(), interestRate);
    }

    private static double simpleSolver(double target, DoubleUnaryOperator o, double lowerGuess, double higherGuess) {
        double guess = 0.0;
        double res;
        double midGuess = (lowerGuess + higherGuess) / 2;
        while (!((Math.abs(target - o.applyAsDouble(midGuess)) < 0.000001) || midGuess == 0.0 || midGuess == 1.0)) {
            if (o.applyAsDouble(midGuess) < target) {
                lowerGuess = midGuess;
            } else {
                higherGuess = midGuess;
            }
            midGuess = (lowerGuess + higherGuess) / 2;
            //System.out.println("mid guess is " + midGuess);
        }
        return Math.round(10000d * midGuess) / 10000d;
    }

    //    static double simpleSolver2(double target, double stock, Option op) {
//        return simpleSolver(target, fillInBS(stock, op.getStrike(), op.getTimeToExpiry(),op.getRate()), 0, 1);
//    }
    private static double get510050Price() {
        try {
            URL allCalls = new URL("http://hq.sinajs.cn/list=sh510050");
            String line;
            Matcher matcher;
            List<String> datalist;
            URLConnection conn = allCalls.openConnection();
            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
                while ((line = reader2.readLine()) != null) {
                    matcher = DATA_PATTERN.matcher(line);
                    datalist = Arrays.asList(line.split(","));
                    while (matcher.find()) {
                        //String ticker = matcher.group(1);
                        //System.out.println("510050 price is " + datalist.get(3));
                        return Double.parseDouble(datalist.get(3));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return 0.0;
    }

    public static void main(String[] argsv) {

        JFrame jf = new JFrame();
        jf.setSize(new Dimension(800, 800));
        ChinaOption co = new ChinaOption();
        jf.add(co);
        jf.setLayout(new FlowLayout());
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
        ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);
        ses.scheduleAtFixedRate(co, 0, 1, TimeUnit.SECONDS);

        //co.run();
        //bs(2.374,2.4, 0.0787, 0.0219178, 0.045);
        //System.out.println(fillInBs(2.374, 2.4, 0.0219178, 0.045).applyAsDouble(0.0787));
    }
}

class Option {

    private final double strike;
    private final LocalDate expiryDate;
    private final CallPutFlag callput;

    Option(double k, LocalDate t, CallPutFlag cp) {
        strike = k;
        expiryDate = t;
        callput = cp;
    }

    double getStrike() {
        return strike;
    }

    CallPutFlag getCPFlag() {
        return callput;
    }

    double getTimeToExpiry() {
        double x = ((ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)) / 365.0d);
        //System.out.println(" time to expiry " + x);
        return x;
    }

    @Override
    public String toString() {
        return Utility.getStr(" strike expiry ", strike, expiryDate);
    }
}

enum CallPutFlag {
    CALL, PUT
}
