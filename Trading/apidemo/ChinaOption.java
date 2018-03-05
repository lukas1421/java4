package apidemo;

import auxiliary.SimpleBar;
import graph.GraphOptionVol;
import graph.GraphOptionVolDiff;
import org.apache.commons.math3.distribution.NormalDistribution;
import utility.Utility;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static apidemo.ChinaOptionHelper.getOptionExpiryDate;
import static java.lang.Math.*;

//import org.apache.commons.math3.*;

public class ChinaOption extends JPanel implements Runnable {

    //static final Pattern CALL_NAME = Pattern.compile("(?<=var\\shq_str_)(CON_OP_\\d{8})=\"");

    static HashMap<String, Option> optionMap = new HashMap<>();
    private static TableRowSorter<OptionTableModel> sorter;

    private static GraphOptionVol graphTS = new GraphOptionVol();
    private static GraphOptionVolDiff graphLapse = new GraphOptionVolDiff();


    private static String frontMonth = "1803";
    private static String backMonth = "1804";
    private static String thirdMonth = "1806";
    private static String fourthMonth = "1809";

    public static LocalDate frontExpiry = getOptionExpiryDate(2018, Month.MARCH);
    public static LocalDate backExpiry = getOptionExpiryDate(2018, Month.APRIL);
    public static LocalDate thirdExpiry = getOptionExpiryDate(2018, Month.JUNE);
    public static LocalDate fourthExpiry = getOptionExpiryDate(2018, Month.SEPTEMBER);

    private static volatile boolean filterOn = false;


    private static double interestRate = 0.04;

    private static volatile LocalDate lastTradingDate = LocalDate.now();
    private static HashMap<String, Double> bidMap = new HashMap<>();
    private static HashMap<String, Double> askMap = new HashMap<>();
    private static HashMap<String, Double> optionPriceMap = new HashMap<>();
    private static Map<String, Option> tickerOptionsMap = new HashMap<>();
    private static volatile List<String> optionList = new LinkedList<>();
    private static Map<String, Double> impliedVolMap = new HashMap<>();
    private static Map<String, Double> impliedVolMapYtd = new HashMap<>();
    public volatile static Map<String, Double> deltaMap = new HashMap<>();
    private static Map<LocalDate, NavigableMap<Double, Double>> strikeVolMapCall = new TreeMap<>();
    private static Map<LocalDate, NavigableMap<Double, Double>> strikeVolMapPut = new TreeMap<>();
    private static List<JLabel> labelList = new ArrayList<>();
    private static JLabel timeLabel = new JLabel();
    private static volatile double currentStockPrice;
    private static OptionTableModel model;
    private static volatile LocalDate expiryToCheck = frontExpiry;
    public static volatile boolean showDelta = false;

    //map<option ticker, map<time,vol>>
    public static Map<String, ConcurrentSkipListMap<LocalDateTime, SimpleBar>> todayImpliedVolMap = new HashMap<>();

    private ChinaOption() {

        getLastTradingDate();
        List<LocalDate> expiryList = new ArrayList<>();
        expiryList.add(frontExpiry);
        expiryList.add(backExpiry);
        expiryList.add(thirdExpiry);
        expiryList.add(fourthExpiry);

        for (LocalDate d : expiryList) {
            strikeVolMapCall.put(d, new TreeMap<>());
            strikeVolMapPut.put(d, new TreeMap<>());
        }

        JPanel leftPanel = new JPanel();
        JPanel rightPanel = new JPanel();


        model = new OptionTableModel();
        JTable optionTable = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer tableCellRenderer, int r, int c) {
                Component comp = super.prepareRenderer(tableCellRenderer, r, c);
                if (isCellSelected(r, c)) {
                    comp.setBackground(Color.GREEN);
                } else if (r % 2 == 0) {
                    comp.setBackground(Color.lightGray);
                } else {
                    comp.setBackground(Color.white);
                }
                return comp;
            }
        };

        JScrollPane optTableScroll = new JScrollPane(optionTable) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        leftPanel.setLayout(new BorderLayout());
        rightPanel.setLayout(new BorderLayout());

        leftPanel.add(optTableScroll, BorderLayout.NORTH);

        setLayout(new BorderLayout());
        //add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        JPanel dataPanel = new JPanel();
        dataPanel.setLayout(new GridLayout(10, 3));

        controlPanel.add(timeLabel);

//        optionPriceMap.put(primaryCall, 0.0);
//        bidMap.put(primaryCall, 0.0);
//        askMap.put(primaryCall, 0.0);

        JScrollPane scrollTS = new JScrollPane(graphTS) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        JScrollPane scrollLapse = new JScrollPane(graphLapse) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        add(controlPanel, BorderLayout.NORTH);
        rightPanel.add(optTableScroll);
        //rightPanel.add(dataPanel, BorderLayout.CENTER);
        JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new GridLayout(2, 1));
        graphPanel.add(scrollTS);
        graphPanel.add(scrollLapse);

        rightPanel.add(graphPanel, BorderLayout.SOUTH);

        JButton saveVolsButton = new JButton(" Save Vols ");
        JButton saveVolsHibButton = new JButton(" Save Vols hib ");
        JButton saveOptionButton = new JButton(" Save options ");
        JButton getPreviousVolButton = new JButton("Prev Vol");

        JButton frontMonthButton = new JButton("Front");
        JButton backMonthButton = new JButton("Back");
        JButton thirdMonthButton = new JButton("Third");
        JButton fourthMonthButton = new JButton("Fourth");
        JButton showDeltaButton = new JButton("Show Delta");

        showDeltaButton.addActionListener(l -> {
            showDelta = !showDelta;
        });

        getPreviousVolButton.addActionListener(l -> loadPreviousOptions());

        frontMonthButton.addActionListener(l -> expiryToCheck = frontExpiry);
        backMonthButton.addActionListener(l -> expiryToCheck = backExpiry);
        thirdMonthButton.addActionListener(l -> expiryToCheck = thirdExpiry);
        fourthMonthButton.addActionListener(l -> expiryToCheck = fourthExpiry);


        saveVolsButton.addActionListener(l -> saveVols());
        //saveOptionButton.addActionListener(l -> saveOptions());

        controlPanel.add(saveVolsButton);
        controlPanel.add(saveVolsHibButton);
        //controlPanel.add(saveOptionButton);
        controlPanel.add(getPreviousVolButton);
        controlPanel.add(Box.createHorizontalStrut(10));

        controlPanel.add(frontMonthButton);
        controlPanel.add(backMonthButton);
        controlPanel.add(thirdMonthButton);
        controlPanel.add(fourthMonthButton);

        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(showDeltaButton);

        JLabel j11 = new JLabel("call");
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

        optionTable.setAutoCreateRowSorter(true);
        sorter = (TableRowSorter<OptionTableModel>) optionTable.getRowSorter();
    }

//    //save options
//    private void saveOptions() {
//        File output = new File(TradingConstants.GLOBALPATH + "optionOutput.csv");
//
//        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, false))) {
//
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }
//    }

    private void saveVols() {
        File output = new File(TradingConstants.GLOBALPATH + "volOutput.csv");

        try (BufferedWriter out = new BufferedWriter(new FileWriter(output, true))) {
            saveVolHelper(out, LocalDate.now(), CallPutFlag.CALL, strikeVolMapCall, frontExpiry, currentStockPrice);
            saveVolHelper(out, LocalDate.now(), CallPutFlag.CALL, strikeVolMapCall, backExpiry, currentStockPrice);
            saveVolHelper(out, LocalDate.now(), CallPutFlag.CALL, strikeVolMapCall, thirdExpiry, currentStockPrice);
            saveVolHelper(out, LocalDate.now(), CallPutFlag.CALL, strikeVolMapCall, fourthExpiry, currentStockPrice);

            saveVolHelper(out, LocalDate.now(), CallPutFlag.PUT, strikeVolMapPut, frontExpiry, currentStockPrice);
            saveVolHelper(out, LocalDate.now(), CallPutFlag.PUT, strikeVolMapPut, backExpiry, currentStockPrice);
            saveVolHelper(out, LocalDate.now(), CallPutFlag.PUT, strikeVolMapPut, thirdExpiry, currentStockPrice);
            saveVolHelper(out, LocalDate.now(), CallPutFlag.PUT, strikeVolMapPut, fourthExpiry, currentStockPrice);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void saveVolHelper(BufferedWriter w, LocalDate writeDate, CallPutFlag f,
                               Map<LocalDate, NavigableMap<Double, Double>> mp, LocalDate expireDate, double spot) {
        if (mp.containsKey(expireDate)) {
            mp.get(expireDate).forEach((k, v) -> {
                try {
                    w.append(Utility.getStrComma(writeDate.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
                            , f == CallPutFlag.CALL ? "C" : "P", k, expireDate, v
                            , Math.round((k / spot) * 100d), getOptionTicker(tickerOptionsMap, f, k, expireDate)));
                    w.newLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static void updateData(double opPrice, double stock, double vol, double bid, double ask, Option opt) {
        SwingUtilities.invokeLater(() -> {

            labelList.get(10).setText(Utility.getStrCheckNull(opPrice));
            labelList.get(11).setText(Utility.getStrCheckNull(stock));
            labelList.get(12).setText(Utility.getStrCheckNull(opt.getStrike()));
            labelList.get(13).setText(Utility.getStrCheckNull(vol));
            labelList.get(14).setText(Utility.getStrCheckNull(bid));
            labelList.get(15).setText(Utility.getStrCheckNull(ask));
            labelList.get(16).setText(Utility.getStrCheckNull(getDelta(opt.getCallOrPut(), stock, opt.getStrike(),
                    vol, opt.getTimeToExpiry(), interestRate)));
            labelList.get(17).setText(Utility.getStrCheckNull(getGamma(stock, opt.getStrike(),
                    vol, opt.getTimeToExpiry(), interestRate)));
            timeLabel.setText(LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        });
    }

    @Override
    public void run() {
        System.out.println(" running @ " + LocalTime.now());
        try {
            String callStringFront = "http://hq.sinajs.cn/list=OP_UP_510050" + frontMonth;
            URL urlCallFront = new URL(callStringFront);
            URLConnection urlconnCallFront = urlCallFront.openConnection();

            String putStringFront = "http://hq.sinajs.cn/list=OP_DOWN_510050" + frontMonth;
            URL urlPutFront = new URL(putStringFront);
            URLConnection urlconnPutFront = urlPutFront.openConnection();

            String callStringBack = "http://hq.sinajs.cn/list=OP_UP_510050" + backMonth;
            URL urlCallBack = new URL(callStringBack);
            URLConnection urlconnCallBack = urlCallBack.openConnection();

            String putStringBack = "http://hq.sinajs.cn/list=OP_DOWN_510050" + backMonth;
            URL urlPutBack = new URL(putStringBack);
            URLConnection urlconnPutBack = urlPutBack.openConnection();

            String callStringThird = "http://hq.sinajs.cn/list=OP_UP_510050" + thirdMonth;
            URL urlCallThird = new URL(callStringThird);
            URLConnection urlconnCallThird = urlCallThird.openConnection();

            String putStringThird = "http://hq.sinajs.cn/list=OP_DOWN_510050" + thirdMonth;
            URL urlPutThird = new URL(putStringThird);
            URLConnection urlconnPutThird = urlPutThird.openConnection();

            String callStringFourth = "http://hq.sinajs.cn/list=OP_UP_510050" + fourthMonth;
            URL urlCallFourth = new URL(callStringFourth);
            URLConnection urlconnCallFourth = urlCallFourth.openConnection();

            String putStringFourth = "http://hq.sinajs.cn/list=OP_DOWN_510050" + fourthMonth;
            URL urlPutFourth = new URL(putStringFourth);
            URLConnection urlconnPutFourth = urlPutFourth.openConnection();

            getOptionInfo(urlconnCallFront, CallPutFlag.CALL, frontExpiry);
            getOptionInfo(urlconnPutFront, CallPutFlag.PUT, frontExpiry);

            getOptionInfo(urlconnCallBack, CallPutFlag.CALL, backExpiry);
            getOptionInfo(urlconnPutBack, CallPutFlag.PUT, backExpiry);

            getOptionInfo(urlconnCallThird, CallPutFlag.CALL, thirdExpiry);
            getOptionInfo(urlconnPutThird, CallPutFlag.PUT, thirdExpiry);

            getOptionInfo(urlconnCallFourth, CallPutFlag.CALL, fourthExpiry);
            getOptionInfo(urlconnPutFourth, CallPutFlag.PUT, fourthExpiry);


        } catch (IOException ex2) {
            ex2.printStackTrace();
        }

        graphTS.repaint();
        graphLapse.repaint();
    }

    private static void getOptionInfo(URLConnection conn, CallPutFlag f, LocalDate expiry) {
        String line;
        try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
            while ((line = reader2.readLine()) != null) {
                Matcher m = (f == CallPutFlag.CALL ? ChinaOptionHelper.CALL_NAME_PATTERN.matcher(line) : ChinaOptionHelper.PUT_NAME_PATTERN.matcher(line));
                List<String> datalist;
                while (m.find()) {
                    String res = m.group(1);
                    datalist = Arrays.asList(res.split(","));
                    URL allOptions = new URL("http://hq.sinajs.cn/list=" +
                            datalist.stream().collect(Collectors.joining(",")));
                    URLConnection urlconnAllPutsThird = allOptions.openConnection();
                    getInfoFromURLConn(urlconnAllPutsThird, f, expiry);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void getInfoFromURLConn(URLConnection conn, CallPutFlag f, LocalDate expiry) {
        String line;
        Matcher matcher;
        //System.out.println(" getting URL from conn ");
        try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream(), "gbk"))) {
            while ((line = reader2.readLine()) != null) {
                matcher = ChinaOptionHelper.OPTION_PATTERN.matcher(line);

                while (matcher.find()) {
                    String resName = matcher.group(1);
                    String res = matcher.group(2);
                    List<String> res1 = Arrays.asList(res.split(","));

                    optionPriceMap.put(resName, Double.parseDouble(res1.get(2)));
                    bidMap.put(resName, Double.parseDouble(res1.get(1)));
                    askMap.put(resName, Double.parseDouble(res1.get(3)));
                    optionPriceMap.put(resName, Double.parseDouble(res1.get(2)));
                    optionList.add(resName);
                    tickerOptionsMap.put(resName, f == CallPutFlag.CALL ?
                            new CallOption(Double.parseDouble(res1.get(7)), expiry) :
                            new PutOption(Double.parseDouble(res1.get(7)), expiry));

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        double stockPrice = get510050Price();
        LocalDateTime currentLDT = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        tickerOptionsMap.forEach((k, v) -> {
            double vol = ChinaOptionHelper.simpleSolver(optionPriceMap.get(k), fillInBS(stockPrice, v));
            impliedVolMap.put(k, vol);
            deltaMap.put(k, getDelta(v.getCallOrPut(), stockPrice, v.getStrike(), vol, v.getTimeToExpiry(), interestRate));
            if (todayImpliedVolMap.containsKey(k)) {
                if (todayImpliedVolMap.get(k).containsKey(currentLDT)) {
                    todayImpliedVolMap.get(k).get(currentLDT).add(vol);
                } else {
                    todayImpliedVolMap.get(k).put(currentLDT, new SimpleBar(vol));
                }
            } else {
                todayImpliedVolMap.put(k, new ConcurrentSkipListMap<>());
                todayImpliedVolMap.get(k).put(currentLDT, new SimpleBar(vol));
            }

            if (v.getCallOrPut() == CallPutFlag.CALL) {
                strikeVolMapCall.get(v.getExpiryDate()).put(v.getStrike(), vol);
            } else {
                strikeVolMapPut.get(v.getExpiryDate()).put(v.getStrike(), vol);
            }
        });

        graphTS.setVolSmileFront(mergePutCallVols(strikeVolMapCall.get(frontExpiry), strikeVolMapPut.get(frontExpiry), stockPrice));
        graphTS.setVolSmileBack(mergePutCallVols(strikeVolMapCall.get(backExpiry), strikeVolMapPut.get(backExpiry), stockPrice));
        graphTS.setVolSmileThird(mergePutCallVols(strikeVolMapCall.get(thirdExpiry), strikeVolMapPut.get(thirdExpiry), stockPrice));
        graphTS.setVolSmileFourth(mergePutCallVols(strikeVolMapCall.get(fourthExpiry), strikeVolMapPut.get(fourthExpiry), stockPrice));

        graphTS.setCurrentPrice(stockPrice);
        graphLapse.setCurrentPrice(stockPrice);

        graphLapse.setVolNow(mergePutCallVols(strikeVolMapCall.get(expiryToCheck),
                strikeVolMapPut.get(expiryToCheck), stockPrice));

        if (impliedVolMapYtd.size() > 0) {
            NavigableMap<Double, Double> callMap = impliedVolMapYtd.entrySet().stream()
                    .filter(e -> (tickerOptionsMap.get(e.getKey()).getCallOrPut() == CallPutFlag.CALL)
                            && tickerOptionsMap.get(e.getKey()).getExpiryDate().equals(expiryToCheck))
                    .collect(Collectors.toMap(e -> {
                        if (tickerOptionsMap.containsKey(e.getKey())) {
                            return tickerOptionsMap.get(e.getKey()).getStrike();
                        } else {
                            return 0.0;
                        }
                    }, Map.Entry::getValue, (a, b) -> a, TreeMap::new));

            NavigableMap<Double, Double> putMap = impliedVolMapYtd.entrySet().stream()
                    .filter(e -> tickerOptionsMap.get(e.getKey())
                            .getCallOrPut() == CallPutFlag.PUT &&
                            tickerOptionsMap.get(e.getKey()).getExpiryDate().equals(expiryToCheck))
                    .collect(Collectors.toMap(e -> {
                        if (tickerOptionsMap.containsKey(e.getKey())) {
                            return tickerOptionsMap.get(e.getKey()).getStrike();
                        } else {
                            return 0.0;
                        }
                    }, Map.Entry::getValue, (a, b) -> a, TreeMap::new));
            graphLapse.setVolPrev1(mergePutCallVols(callMap, putMap, stockPrice));
        }
        SwingUtilities.invokeLater(() -> model.fireTableDataChanged());
    }

    private void getLastTradingDate() {
        int lineNo = 0;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "ftseA50Open.txt"), "gbk"))) {
            String line;
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                if (lineNo > 2) {
                    throw new IllegalArgumentException(" ERROR: date map has more than 3 lines ");
                }
                lastTradingDate = LocalDate.parse(al1.get(0));
                lineNo++;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void loadPreviousOptions() {
        String line;
        LocalDate lastDate = lastTradingDate;
        //2018/2/26	C	2.6	2018/2/28	0	88
        // record date (at close) || CP Flag || strike || expiry date || vol || moneyness
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(
                new FileInputStream(TradingConstants.GLOBALPATH + "volOutput.csv")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split(","));

                if (LocalDate.parse(al1.get(0), DateTimeFormatter.ofPattern("yyyy/M/d")).equals(lastDate)) {
                    CallPutFlag f = al1.get(1).equalsIgnoreCase("C") ? CallPutFlag.CALL : CallPutFlag.PUT;
                    double strike = Double.parseDouble(al1.get(2));
                    LocalDate expiry = LocalDate.parse(al1.get(3), DateTimeFormatter.ofPattern("yyyy/M/dd"));
                    String ticker = getOptionTicker(tickerOptionsMap, f, strike, expiry);
                    double volPrev = Double.parseDouble(al1.get(4));

                    if (!ticker.equals("")) {
                        impliedVolMapYtd.put(ticker, volPrev);
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static String getOptionTicker(Map<String, Option> mp, CallPutFlag f, double strike, LocalDate expiry) {
        for (Map.Entry<String, Option> e : mp.entrySet()) {
            if (e.getValue().getCallOrPut() == f && e.getValue().getStrike() == strike
                    && e.getValue().getExpiryDate().equals(expiry)) {
                return e.getKey();
            }
        }
        return "";
    }


    private static Option getOption(Map<String, Option> mp, CallPutFlag f, double strike, LocalDate expiry) {
        for (Map.Entry<String, Option> e : mp.entrySet()) {
            if (e.getValue().getCallOrPut() == f && e.getValue().getStrike() == strike
                    && e.getValue().getExpiryDate().equals(expiry)) {
                return e.getValue();
            }
        }
        throw new IllegalStateException(" no option found ");
    }


    private static double getDeltaFromStrikeExpiry(CallPutFlag f, double strike, LocalDate expiry, double stock, double vol) {
        String ticker = getOptionTicker(tickerOptionsMap, f, strike, expiry);
        return deltaMap.getOrDefault(ticker, 0.0);
//        Option opt = getOption(tickerOptionsMap, f, strike, expiry);
//        return getDeltaForOption(opt, stock, vol);
    }

    public static NavigableMap<Double, Double> getStrikeDeltaMapFromVol(NavigableMap<Double, Double> volMap, double stock, LocalDate expiry) {
        NavigableMap<Double, Double> res = new TreeMap<>();
        for (double strike : volMap.keySet()) {
            if (strike < stock) {
                res.put(strike, getDeltaFromStrikeExpiry(CallPutFlag.PUT, strike, expiry, stock, volMap.get(strike)));
            } else {
                res.put(strike, getDeltaFromStrikeExpiry(CallPutFlag.CALL, strike, expiry, stock, volMap.get(strike)));
            }
        }
        return res;
    }

    private static NavigableMap<Double, Double> mergePutCallVols(NavigableMap<Double, Double> callMap
            , NavigableMap<Double, Double> putMap, double spot) {
        NavigableMap<Double, Double> res = new TreeMap<>();

        callMap.forEach((k, v) -> {
            if (k > spot) {
                res.put(k, v);
            }
        });
        putMap.forEach((k, v) -> {
            if (k < spot) {
                res.put(k, v);
            }
        });
        return res;
    }

    private static double bs(CallPutFlag f, double s, double k, double v, double t, double r) {
        double d1 = (Math.log(s / k) + (r + 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double d2 = (Math.log(s / k) + (r - 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double nd1 = (new NormalDistribution()).cumulativeProbability(d1);
        double nd2 = (new NormalDistribution()).cumulativeProbability(d2);
        double call = s * nd1 - exp(-r * t) * k * nd2;
        double put = exp(-r * t) * k * (1 - nd2) - s * (1 - nd1);

//        double delta = nd1;
//        double gamma = 0.4 * exp(-0.5 * pow(d1, 2)) / (s * v * sqrt(t));
//        double vega = 0.4 * s * sqrt(t) / 100;

        return f == CallPutFlag.CALL ? call : put;
    }

    private static double getDelta(CallPutFlag f, double s, double k, double v, double t, double r) {
        //System.out.println(Utility.getStr(" s k v t r  ", s, k, v, t, r));
        double d1 = (Math.log(s / k) + (r + 0.5 * pow(v, 2)) * t) / (sqrt(t) * v);
        double nd1 = (new NormalDistribution()).cumulativeProbability(d1);

        return Math.round(100d * (f == CallPutFlag.CALL ? nd1 : (nd1 - 1)));
    }

    private static double getDeltaForOption(Option opt, double s, double v) {
        return getDelta(opt.getCallOrPut(), s, opt.getStrike(), v, opt.getTimeToExpiry(), interestRate);
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
        return (double v) -> bs(opt.getCallOrPut(), s, opt.getStrike(), v,
                opt.getTimeToExpiry(), interestRate);
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
                    matcher = ChinaOptionHelper.DATA_PATTERN.matcher(line);
                    datalist = Arrays.asList(line.split(","));
                    if (matcher.find()) {
                        //String ticker = matcher.group(1);
                        //System.out.println("510050 price is " + datalist.get(3));
                        currentStockPrice = Double.parseDouble(datalist.get(3));
                        return currentStockPrice;
                        //return Double.parseDouble(datalist.get(3));
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
        jf.setSize(new Dimension(1500, 1900));
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

    class OptionTableModel extends javax.swing.table.AbstractTableModel {

        @Override
        public int getRowCount() {
            return 200;
        }

        @Override
        public int getColumnCount() {
            return 12;
        }

        @Override
        public String getColumnName(int col) {
            //noinspection Duplicates
            switch (col) {
                case 0:
                    return "Ticker";
                case 1:
                    return "CP";
                case 2:
                    return "Expiry";
                case 3:
                    return "Days to Exp";
                case 4:
                    return "K";
                case 5:
                    return "Price";
                case 6:
                    return "Vol";
                case 7:
                    return "Vol Ytd";
                case 8:
                    return "Vol Chg 1d";
                case 9:
                    return " Moneyness ";
                case 10:
                    return " Delta ";


                default:
                    return "";
            }
        }

        @Override
        public Class getColumnClass(int col) {
            //noinspection Duplicates
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                case 2:
                    return LocalDate.class;
                case 3:
                    return Double.class;
                default:
                    return Double.class;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {

            String name = optionList.size() > rowIn ? optionList.get(rowIn) : "";
            double strike = tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getStrike() : 0.0;
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getCPString() : "";
                case 2:
                    return tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getExpiryDate() :
                            LocalDate.MIN;
                case 3:
                    return tickerOptionsMap.containsKey(name) ? tickerOptionsMap.get(name).getTimeToExpiry() : 0.0;
                case 4:
                    return strike;
                case 5:
                    return tickerOptionsMap.containsKey(name) ? optionPriceMap.getOrDefault(name, 0.0) : 0.0;
                case 6:
                    return impliedVolMap.getOrDefault(name, 0.0);
                case 7:
                    return impliedVolMapYtd.getOrDefault(name, 0.0);
                case 8:
                    return impliedVolMap.getOrDefault(name, 0.0) - impliedVolMapYtd.getOrDefault(name, 0.0);
                case 9:
                    return currentStockPrice != 0.0 ? Math.round(100d * strike / currentStockPrice) : 0.0;
                case 10:
                    return deltaMap.getOrDefault(name, 0.0);


                default:
                    return 0.0;
            }
        }
    }
}

abstract class Option {

    private final double strike;
    private final LocalDate expiryDate;
    private final CallPutFlag callput;

    Option(double k, LocalDate t, CallPutFlag f) {
        strike = k;
        expiryDate = t;
        callput = f;
    }

    double getStrike() {
        return strike;
    }

    CallPutFlag getCallOrPut() {
        return callput;
    }

    String getCPString() {
        return callput == CallPutFlag.CALL ? "C" : "P";
    }

    LocalDate getExpiryDate() {
        return expiryDate;
    }


    private double percentageDayLeft(LocalTime lt) {

        if (lt.isBefore(LocalTime.of(9, 30))) {
            return 1.0;
        } else if (lt.isAfter(LocalTime.of(15, 0))) {
            return 0.0;
        }

        if (lt.isAfter(LocalTime.of(11, 30)) && lt.isBefore(LocalTime.of(15, 0))) {
            return 0.5;
        }
        return ((ChronoUnit.MINUTES.between(lt, LocalTime.of(15, 0))) -
                (lt.isBefore(LocalTime.of(11, 30)) ? 90 : 0)) / 240;
    }


    double getTimeToExpiry() {
        return (ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)
                + percentageDayLeft(LocalTime.now())) / 365.0d;

//        double x = ((ChronoUnit.DAYS.between(LocalDate.now(), expiryDate) + 1) / 365.0d);
//        //System.out.println(" time to expiry " + x);
//        return x;
    }

//    double getTimeToExpiryDetailed() {
//        return (ChronoUnit.MINUTES.between(LocalDate.now(), LocalTime.of(15, 0)) / 350) / 365.0d
//                + ((ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)) / 365.0d);
//    }

    @Override
    public String toString() {
        return Utility.getStr(" strike expiry ", strike, expiryDate);
    }
}

class CallOption extends Option {
    CallOption(double k, LocalDate t) {
        super(k, t, CallPutFlag.CALL);
    }
}

class PutOption extends Option {
    PutOption(double k, LocalDate t) {
        super(k, t, CallPutFlag.PUT);
    }
}


enum CallPutFlag {
    CALL, PUT
}
