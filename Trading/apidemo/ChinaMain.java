package apidemo;

import auxiliary.AnaCompute;
import auxiliary.Analysis;
import auxiliary.Dividends;
import auxiliary.StratCompute;
import client.ExecutionFilter;
import client.Types.NewsType;
import controller.ApiConnection.ILogger;
import controller.ApiConnection.ILogger.DefaultLogger;
import controller.ApiController;
import controller.ApiController.IConnectionHandler;
import controller.Formats;
import graph.GraphIndustry;
import historical.Request;
import util.*;
import util.IConnectionConfiguration.DefaultConnectionConfiguration;
import utility.Utility;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static apidemo.ChinaData.priceMapBar;
import static apidemo.ChinaData.priceMapBarYtd;
import static utility.Utility.AM914T;

//import java.time.temporal.ChronoUnit;
//import java.time.temporal.TemporalUnit;

public final class ChinaMain implements IConnectionHandler {

    static {
        NewLookAndFeel.register();
    }
    private final IConnectionConfiguration m_connectionConfiguration;

    public static volatile Map<Integer, Request> globalRequestMap = new HashMap<>();
    public static ChinaMain INSTANCE;
    private final JTextArea m_inLog = new JTextArea();
    private final JTextArea m_outLog = new JTextArea();
    private final static ILogger M_INLOGGER = new DefaultLogger(); //new Logger( m_inLog);
    private final static ILogger M_OUTLOGGER = new DefaultLogger(); // new Logger( m_outLog);
    private final static ApiController M_CONTROLLER = new ApiController(new ChinaMainHandler(), M_INLOGGER, M_OUTLOGGER);
    private final ArrayList<String> m_acctList = new ArrayList<>();
    private final JFrame m_frame = new JFrame();
    private final JFrame m_frame3 = new JFrame();
    private final JFrame m_frame4 = new JFrame();
    private final JFrame m_frame5 = new JFrame();
    private final JFrame m_frame6 = new JFrame();
    private final JFrame m_frame7 = new JFrame();
    private final JFrame m_frame8 = new JFrame();
    private final JFrame m_frame9 = new JFrame();
    private final JFrame m_frame10 = new JFrame();
    private final JFrame m_frame11 = new JFrame();
    private final JFrame m_frame12 = new JFrame();

    private final NewTabbedPanel m_tabbedPanel = new NewTabbedPanel(true);
    public static ConnectionPanel m_connectionPanel;
    //private final ContractInfoPanel m_contractInfoPanel = new ContractInfoPanel();
    //private final TradingPanel m_tradingPanel = new TradingPanel();
    //private final AccountInfoPanel m_acctInfoPanel = new AccountInfoPanel();
    //private final OptionsPanel m_optionsPanel = new OptionsPanel();
    //private final AdvisorPanel m_advisorPanel = new AdvisorPanel();
    //private final ComboPanel m_comboPanel = new ComboPanel();
    //private final StratPanel m_stratPanel = new StratPanel();
    private final JTextArea m_msg = new JTextArea();
    static volatile JLabel twsTime = new JLabel(Utility.timeNowToString());
    static volatile JLabel systemTime = new JLabel(Utility.timeNowToString());
    static volatile JLabel systemNotif = new JLabel("");

    public static final String GLOBALPATH = "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\Trading\\";
    public static final String GLOBALA50EXPIRY = "20171030";
    public static final int GLOBALWIDTH = 1900;
    public static volatile double CNHHKD = 1.18;
    final static int PORT_IBAPI = 4001;
    final static int PORT_NORMAL = 7496;
    //private final Data data = new Data();
    //private final HistData histdata = new HistData();
    //private LiveData livedata = new LiveData();
    //private Analysis analysis = new Analysis();
    //private Backtesting backtesting = new Backtesting();
    private final XU xu = new XU();
    //private Shcomp shcomp = new Shcomp();
    //private ChinaFut chinafut = new ChinaFut();

    private final ChinaStock chinastock = new ChinaStock();

    //System.out.println("time after chinastock");
    private final ChinaIndex chinaindex = new ChinaIndex();

    private final ChinaKeyMonitor keyMon = new ChinaKeyMonitor();

    private final ChinaData chinaData = new ChinaData();

    private final ChinaDataMapYtd chinadatamapytd = new ChinaDataMapYtd();

    private final ChinaDataYesterday chinaDataYtd = new ChinaDataYesterday();

    private final ChinaSizeDataYtd csdy = new ChinaSizeDataYtd();

    private final ChinaSizeData chinaSizeData = new ChinaSizeData();

    //private final ChinaSizeRatio chinasizeratio = new ChinaSizeRatio();
    private final ChinaPosition chinaPos = new ChinaPosition();

    //private ChinaSizeDataDifferenced chinasizedataD= new ChinaSizeDataDifferenced();
    //private final ChinaBidAskData cbad = new ChinaBidAskData();
    //private final ChinaStrategyTable cst = new ChinaStrategyTable();
    //private final IdeaProcessor ip = new IdeaProcessor();
    //private final IdeaProcessorJolt ipJolt = new IdeaProcessorJolt();
    //private final IdeaProcessorPM ipPM = new IdeaProcessorPM();
    private final ChinaBigGraph bg = new ChinaBigGraph();
    private final ChinaGraphIndustry gi = new ChinaGraphIndustry();
    //private final ChinaBidAskGraph gba = new ChinaBidAskGraph();
    // private ChinaMasterMonitor cmm = new ChinaMasterMonitor();
    //private final HibernateUtil hib = new HibernateUtil();

    public static HKData hkdata = new HKData();
    public static HKStock hkstock = new HKStock();
    SinaStock sinastock1 = SinaStock.getInstance();

    private ExecutorService pool;

    public static long counter = 0L;

    private volatile AnaCompute anacompute = new AnaCompute();
    private volatile StratCompute stratcompute = new StratCompute();
    private final ScheduledExecutorService ses = Executors.newScheduledThreadPool(10);

    public static final String tdxPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export_1m\\" : "J:\\TDX\\T0002\\export_1m\\";

    public ArrayList<String> accountList() {
        return m_acctList;
    }

    public static ApiController controller() {
        return M_CONTROLLER;
    }

    public JFrame frame() {
        return m_frame;
    }

    //public static JComponent indexWatcher;
    public static void main(String[] args) throws IOException, InterruptedException {
        start(new ChinaMain(new DefaultConnectionConfiguration()));
    }

    public static void start(ChinaMain cm) throws IOException, InterruptedException {
        INSTANCE = cm;
        INSTANCE.run();
    }

    public ChinaMain(IConnectionConfiguration connectionConfig) {
        m_connectionConfiguration = connectionConfig;
        m_connectionPanel = new ConnectionPanel(); // must be done after connection config is set
    }

    private void run() throws IOException, InterruptedException {
        m_tabbedPanel.addTab("XU", xu);
        //m_tabbedPanel.addTab("Shcomp", shcomp);
        //m_tabbedPanel.addTab("ChinaFut", chinafut);
        //m_tabbedPanel.addTab("Live", livedata);
        //m_tabbedPanel.addTab("Analysis", analysis);
        m_tabbedPanel.addTab("Stock ", chinastock);
        //m_tabbedPanel.addTab("Index ", chinaindex);

        m_tabbedPanel.addTab("Ytd", chinaDataYtd);
        m_tabbedPanel.addTab("Data ", chinaData);
        m_tabbedPanel.addTab("Data ytd", chinadatamapytd);
        m_tabbedPanel.addTab("Size", chinaSizeData);
        //m_tabbedPanel.addTab("Pos", chinapos);

        m_tabbedPanel.addTab("Index", chinaindex);

        //m_tabbedPanel.addTab("Size Ratio", chinasizeratio);
        //m_tabbedPanel.addTab("Size Diff",chinasizedataD);
        m_tabbedPanel.addTab("Size ytd", csdy);
        //m_tabbedPanel.addTab("Ch Bid Ask", cbad);
        //m_tabbedPanel.addTab("Strat", cst);
        //m_tabbedPanel.addTab("China Monitor",cmm);

        m_tabbedPanel.addTab("Connection", m_connectionPanel);
        //m_tabbedPanel.addTab( "Market Data", m_mktDataPanel);
        //m_tabbedPanel.addTab( "Trading", m_tradingPanel);
        // m_tabbedPanel.addTab( "Account Info", m_acctInfoPanel);
        m_tabbedPanel.select("Data ");
        //m_tabbedPanel.addTab( "Options", m_optionsPanel);
        //m_tabbedPanel.addTab( "Combos", m_comboPanel);
        // m_tabbedPanel.addTab( "Contract Info", m_contractInfoPanel);
        //m_tabbedPanel.addTab( "Advisor", m_advisorPanel);
        // m_tabbedPanel.addTab( "Strategy", m_stratPanel); in progress

        //m_tabbedPanel.addTab("Hist", histdata);

        m_tabbedPanel.addTab(" HK Data", hkdata);
        m_tabbedPanel.addTab(" HK Stock", hkstock);

        // m_tabbedPanel.addTab("Backtesting", backtesting);
        //m_tabbedPanel.
        //m_tabbedPanel.getComponent(15).
        //m_comboPanel.
        m_msg.setEditable(false);
        m_msg.setLineWrap(true);
        JScrollPane msgScroll = new JScrollPane(m_msg);
        msgScroll.setPreferredSize(new Dimension(10000, 50));
        JScrollPane outLogScroll = new JScrollPane(m_outLog);
        outLogScroll.setPreferredSize(new Dimension(10000, 50));
        JScrollPane inLogScroll = new JScrollPane(m_inLog);
        inLogScroll.setPreferredSize(new Dimension(10000, 50));

        systemTime.setFont(systemTime.getFont().deriveFont(25F));
        systemTime.setBackground(Color.orange);
        systemTime.setForeground(Color.black);

        twsTime.setFont(systemTime.getFont().deriveFont(25F));
        twsTime.setBackground(Color.orange);
        twsTime.setForeground(Color.black);

        systemNotif.setOpaque(true);
        systemNotif.setBackground(Color.orange);
        systemNotif.setForeground(Color.black);
        systemNotif.setFont(systemNotif.getFont().deriveFont(25F));

        JPanel threadManager = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 100;
                return d;
            }
        };

        JButton runAnalysis = new JButton("Run Analysis");
        runAnalysis.addActionListener((ae) -> {
            Analysis.compute(LiveData.map1);
        });

        JButton startPool = new JButton("start Analysis");
        JButton startPool2 = new JButton("start Backtesting");
        JButton startXU = new JButton("ON XU");
        JButton startHK = new JButton(" ON HK");
        JButton stopXU = new JButton("Kill XU");
        JButton onShcomp = new JButton("ON shcomp/ChinaFut");
        JButton offShcomp = new JButton("Kill Shcomp/ChinaFut");
        JButton saveAll = new JButton("saveAll");

        // quick buttons for loading
        JButton getSinaData = new JButton("get Index");

        getSinaData.addActionListener((ae) -> {
            ses.scheduleAtFixedRate(sinastock1, 0, 1, TimeUnit.SECONDS);
            xu.startIndex();
            ses.scheduleAtFixedRate(() -> {
                if (LocalTime.now().isAfter(AM914T) && LocalTime.now().isBefore(LocalTime.of(15, 15))) {
                    XU.saveHibXU();
                    ChinaData.withHibernate();
                    ChinaData.saveChinaOHLC();
                    ChinaData.outputPrices();
                    MorningTask.getBOCFX();
                    ChinaData.outputRecentTradingDate();
                }
            }, 10, 5, TimeUnit.MINUTES);
        });

        JButton loadYesterday = new JButton("Load Yest");
        loadYesterday.addActionListener((ae) -> {
            ChinaDataYesterday.loadYesterdayData();
        });

        JButton loadChinaBar = new JButton("Load Bar");
        loadChinaBar.addActionListener((ae) -> {
            ChinaData.loadPriceBar();
        });

        JButton showIdeaGraphs = new JButton("show Ideas");
        showIdeaGraphs.addActionListener((ae) -> {
            if (m_frame4.isVisible() || m_frame5.isVisible()) {
                m_frame4.setVisible(false);
                m_frame5.setVisible(false);
            } else {
                m_frame4.setVisible(true);
                m_frame5.setVisible(true);
            }
        });

        JButton showPMGraphs = new JButton("Show PM");
        showPMGraphs.addActionListener((ae) -> {
            m_frame6.setVisible(!m_frame6.isVisible());
        });
        JButton vrPageToggle = new JButton("Show VR");
        vrPageToggle.addActionListener((ae) -> {
            m_frame3.setVisible(!m_frame3.isVisible());
        });
        JButton computeVR = new JButton("Compute VR");
        computeVR.addActionListener((ae) -> {
            ChinaSizeRatio.computeSizeRatio();
        });
        JButton showBigGraph = new JButton("Big Graph");
        showBigGraph.addActionListener((ae) -> {
            m_frame7.setVisible(!m_frame7.isVisible());
        });

        JButton computeIndustry = new JButton("Industry");
        computeIndustry.addActionListener((ae) -> {
            m_frame8.setVisible(!m_frame8.isVisible());
            GraphIndustry.compute();
        });

        JButton showBA = new JButton("BA");
        showBA.addActionListener((ae) -> {
            m_frame9.setVisible(!m_frame9.isVisible());
        });

        JButton suspendIndex = new JButton("stop index");
        suspendIndex.addActionListener(l -> {
            xu.suspendIndex();
        });
        JButton killAllDiags = new JButton("Kill Diags");
        killAllDiags.addActionListener(l -> {
            ChinaStockHelper.killAllDialogs();
        });

        //JButton tdxButton = new JButton("TDX");
        JButton fillHolesButton = new JButton("FillHoles");
        fillHolesButton.addActionListener(l -> {
            //ChinaStockHelper.checkZerosAndFix();
            System.out.println(" filling holes for today ");
            ChinaStockHelper.fillHolesInData(priceMapBar, LocalTime.of(9, 24));
            System.out.println(" filling holes for ytd ");
            ChinaStockHelper.fillHolesInData(priceMapBarYtd, LocalTime.of(9, 29));
            ChinaStockHelper.fillHolesInSize();
//            ChinaStockHelper.deleteAllAfterT(LocalTime.now());
        });

        JButton forwardfillButton = new JButton("fwdFill");

        forwardfillButton.addActionListener(l -> {
            ChinaStockHelper.fwdFillHolesInData();
        });

        JButton fixMapButton = new JButton("fix Map");

        fixMapButton.addActionListener(l -> {
            ChinaStockHelper.fixVolMap();
            Utility.fixPriceMap(ChinaData.priceMapBar);
        });

//        JButton getSGXA50HistButton = new JButton("SGXA50 ");
//
//        getSGXA50HistButton.addActionListener(l -> {
//            //System.out.println("in action listening thread"+Thread.currentThread().getName());
//            long start = System.currentTimeMillis();
//            CompletableFuture.runAsync(() -> {
//                System.out.println("in completablefuture thread" + Thread.currentThread().getName());
//                controller().getSGXA50Historical();
//            });
//            System.out.println(" time total " + (System.currentTimeMillis() - start));
//        });
        JButton getPosButton = new JButton("getPos");
        getPosButton.addActionListener(l -> {
            System.out.println(" requesting position ");
            //controller().client().reqPositions();
            controller().client().reqAccountSummary(5, "All", "NetLiquidation,BuyingPower");
            controller().client().reqExecutions(6, new ExecutionFilter());
        });

        JButton dividendButton = new JButton("Dividends");
        dividendButton.addActionListener(l -> {
            Dividends.dealWithDividends();
        });

        JButton roundDataButton = new JButton("Round");
        roundDataButton.addActionListener(l -> {
            ChinaStockHelper.roundAllData();
        });

        startPool.addActionListener((ae) -> {
            pool = Executors.newCachedThreadPool();
            pool.execute(anacompute);
        });

        startPool2.addActionListener((ae) -> {
            pool = Executors.newCachedThreadPool();
            pool.execute(stratcompute);
        });

        startXU.addActionListener((ae) -> {
            try {
                M_CONTROLLER.reqXUDataArray(xu);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        });

        startHK.addActionListener(al -> {
            M_CONTROLLER.reqHKLiveData();
        });

        stopXU.addActionListener((ae) -> {
            M_CONTROLLER.cancelTopMktData(xu);
        });

        JButton stopAnalysis = new JButton("Stop Analysis");

        stopAnalysis.addActionListener((ae) -> {
            pool.shutdownNow();
        });

        offShcomp.addActionListener((ae) -> {
            ses.shutdown();
        });

        saveAll.addActionListener((al) -> {
            XU.saveXU();
        });

        threadManager.add(getSinaData);
        //threadManager.add(loadChinaBar);
        threadManager.add(loadYesterday);

        //threadManager.add(showIdeaGraphs);
        //threadManager.add(showPMGraphs);
        //threadManager.add(vrPageToggle);
        //threadManager.add(computeVR);
        threadManager.add(showBigGraph);
        threadManager.add(computeIndustry);

        threadManager.add(Box.createHorizontalStrut(30));
        //threadManager.add(showBA);
        //threadManager.add(suspendIndex);
        threadManager.add(killAllDiags);
        //threadManager.add(tdxButton);
        threadManager.add(fillHolesButton);
        threadManager.add(forwardfillButton);
        //threadManager.add(fixMapButton);
        //threadManager.add(getSGXA50HistButton);
        //threadManager.add(getPosButton);
        threadManager.add(dividendButton);
        threadManager.add(startXU);
        threadManager.add(startHK);
        //threadManager.add(roundDataButton);
        threadManager.add(Box.createHorizontalStrut(30));
        threadManager.add(systemTime);
        threadManager.add(Box.createHorizontalStrut(30));
        threadManager.add(twsTime);
        threadManager.add(systemNotif);

        NewTabbedPanel bot = new NewTabbedPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 90;
                return d;
            }
        };

        bot.addTab("Messages", msgScroll);
        bot.addTab("Log (out)", outLogScroll);
        bot.addTab("Log (in)", inLogScroll);
        bot.addTab("Analysis", threadManager);
        bot.select("Analysis");
        //bot.addTab("Analysis" , indexWatcher);

        m_frame.add(m_tabbedPanel);
        m_frame.add(bot, BorderLayout.SOUTH);
        m_frame.setSize(1920, 1080);
        m_frame.setVisible(true);
        m_frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        m_frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println(" closing main frame ");
                int ans = JOptionPane.showConfirmDialog(null, "are you sure", "", JOptionPane.YES_NO_OPTION);
                if (ans == JOptionPane.YES_OPTION) {
                    System.out.println(" yes pressed");
                    m_frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    CompletableFuture.runAsync(() -> {
                        XU.saveHibXU();
                    }).thenRun(() -> {
                        System.out.println(" disposing ");
                        m_frame.dispose();
                    });
                } else {
                    System.out.println(" no pressed");
                }
            }
        });

//        JPanel vrOnly = new JPanel();
//        vrOnly.setLayout(new BorderLayout());
//        vrOnly.add(chinasizeratio, BorderLayout.CENTER);
//        m_frame3.add(vrOnly);
//        m_frame3.setTitle("VR");
//        m_frame3.setSize(1920,1080);
//        m_frame3.setVisible(false);
//        JPanel ipOnly = new JPanel();
//        ipOnly.setLayout(new BorderLayout());
//        ipOnly.add(ip, BorderLayout.CENTER);
//        m_frame4.add(ipOnly);
//        m_frame4.setTitle("Idea");
//        m_frame4.setSize(1920,1080);
//        m_frame4.setVisible(false);
//        JPanel ipJoltOnly = new JPanel();
//        ipJoltOnly.setLayout(new BorderLayout());
//        ipJoltOnly.add(ipJolt, BorderLayout.CENTER);
//        m_frame5.add(ipJoltOnly);
//        m_frame5.setTitle("Jolt");
//        m_frame5.setSize(1920,1080);
//        m_frame5.setVisible(false);
//        JPanel ipPMOnly = new JPanel();
//        ipPMOnly.setLayout(new BorderLayout());
//        ipPMOnly.add(ipPM, BorderLayout.CENTER);
//        m_frame6.add(ipPMOnly);
//        m_frame6.setTitle("PM");
//        m_frame6.setSize(1920,1080);
//        m_frame6.setVisible(false);
        JPanel bigGraphOnly = new JPanel();
        bigGraphOnly.setLayout(new BorderLayout());
        bigGraphOnly.add(bg, BorderLayout.CENTER);
        m_frame7.add(bigGraphOnly);
        m_frame7.setTitle("bigGraph");
        m_frame7.setSize(1920, 1080);
        m_frame7.setVisible(false);

        JPanel graphIndustryOnly = new JPanel();
        graphIndustryOnly.setLayout(new BorderLayout());
        graphIndustryOnly.add(gi, BorderLayout.CENTER);
        m_frame8.add(graphIndustryOnly);
        m_frame8.setTitle("IndustryGraph");
        m_frame8.setSize(1920, 1080);
        m_frame8.setVisible(false);

//        JPanel graphBAOnly = new JPanel();
//        graphBAOnly.setLayout(new BorderLayout());
//        graphBAOnly.add(gba,BorderLayout.CENTER);
//        m_frame9.add(graphBAOnly);
//        m_frame9.setTitle("BA Graph");
//        m_frame9.setSize(1920,1080);
//        m_frame9.setVisible(false);
//        JPanel indexOnly = new JPanel();
//        indexOnly.setLayout(new BorderLayout());
//        indexOnly.add(chinaindex,BorderLayout.CENTER);
//        m_frame10.add(indexOnly);
//        m_frame10.setTitle("Index Graph");
//        m_frame10.setSize(1920,1080);
//        m_frame10.setVisible(true);
//
        JPanel posOnly = new JPanel();
        posOnly.setLayout(new BorderLayout());
        posOnly.add(chinaPos, BorderLayout.CENTER);
        m_frame10.add(posOnly);
        m_frame10.setTitle("Pos");
        m_frame10.setSize(1920, 1080);
        m_frame10.setVisible(true);

        JPanel ptfMonitor = new JPanel();
        ptfMonitor.setLayout(new BorderLayout());
        ptfMonitor.add(keyMon, BorderLayout.CENTER);
        m_frame11.add(ptfMonitor);
        m_frame11.setTitle("Mon");
        m_frame11.setSize(1920, 1080);
        m_frame11.setVisible(true);

        // make initial connection to local host, port 7496, client id 0, 4001 is for with IBAPI
        // m_controller.connect( "127.0.0.1", PORT_IBAPI, 0);
        // m_controller.connect( "127.0.0.1", 7496, 0);
        CompletableFuture.runAsync(() -> {
            try {
                //System.out.println(" get default connection option "+ m_connectionConfiguration.getDefaultConnectOptions() != null ? "non null" : "null");
                controller().connect("127.0.0.1", 7496, 0, m_connectionConfiguration.getDefaultConnectOptions() != null ? "" : null);

            } catch (Exception ex) {
                System.out.println(" error in controller ");
                ex.printStackTrace();
            }
        });

        CompletableFuture.runAsync(() -> {
            M_CONTROLLER.generateData();
        });

        CompletableFuture.runAsync(() -> {
            try {
                M_CONTROLLER.reqXUDataArray(xu);
            } catch (InterruptedException x) {
                x.printStackTrace();
            }
        });
    }

    public ChinaMain() {
        this.m_connectionConfiguration = null;
    }

    @Override
    public void connected() {
        show("connected");
        System.out.println(" connected from connected ");
        ChinaMain.m_connectionPanel.setConnectionStatus("connected");
        controller().reqCurrentTime((long time) -> {
            show("Server date/time is " + Formats.fmtDate(time * 1000));
        });
        controller().reqBulletins(true, (int msgId, NewsType newsType, String message, String exchange) -> {
            String str = String.format("Received bulletin:  type=%s  exchange=%s", newsType, exchange);
            show(str);
            show(message);
        });
    }

    @Override
    public void disconnected() {
        show("disconnected");
        System.out.println(" setting panel status disconnected ");
        m_connectionPanel.m_status.setText("disconnected");
    }

    @Override
    public void accountList(ArrayList<String> list) {
        show("Received account list");
        m_acctList.clear();
        m_acctList.addAll(list);
    }

    @Override
    public void show(final String str) {
        SwingUtilities.invokeLater(() -> {
            m_msg.append(str);
            m_msg.append("\n\n");
            Dimension d = m_msg.getSize();
            m_msg.scrollRectToVisible(new Rectangle(0, d.height, 1, 1));
        });
    }

    @Override
    public void error(Exception e) {
        e.printStackTrace();
        show(e.toString());
    }

    @Override
    public void message(int id, int errorCode, String errorMsg) {
        show(id + " " + errorCode + " " + errorMsg);
    }

    public final class ConnectionPanel extends JPanel {

        private final JTextField m_host = new JTextField(m_connectionConfiguration.getDefaultHost(), 10);
        private final JTextField m_port = new JTextField(m_connectionConfiguration.getDefaultPort(), 7);
        private final JTextField m_connectOptionsTF = new JTextField(m_connectionConfiguration.getDefaultConnectOptions(), 30);
        private final JTextField m_clientId = new JTextField("0", 7);
        private volatile JLabel m_status = new JLabel("Disconnected");
        private final JLabel m_defaultPortNumberLabel = new JLabel("<html>Live Trading ports:<b> TWS: 7496; IB Gateway: 4001.</b><br>"
                + "Simulated Trading ports for new installations of "
                + "version 954.1 or newer: "
                + "<b>TWS: 7497; IB Gateway: 4002</b></html>");

        public ConnectionPanel() {
            HtmlButton connect7496 = new HtmlButton("Connect7496") {
                @Override
                public void actionPerformed() {
                    onConnect("7496");
                }
            };

            HtmlButton connect4001 = new HtmlButton("Connect4001") {
                @Override
                public void actionPerformed() {
                    onConnect("4001");
                }
            };

            HtmlButton disconnect = new HtmlButton("Disconnect") {
                @Override
                public void actionPerformed() {
                    System.out.println(" disconnect button clicked ");
                    controller().disconnect();
                }
            };

            JPanel p1 = new VerticalPanel();
            p1.add("Host", m_host);
            p1.add("Port", m_port);
            p1.add("Client ID", m_clientId);
            if (m_connectionConfiguration.getDefaultConnectOptions() != null) {
                p1.add("Connect options", m_connectOptionsTF);
            }
            p1.add("", m_defaultPortNumberLabel);

            JPanel p2 = new VerticalPanel();
            p2.add(connect7496);
            p2.add(connect4001);
            p2.add(disconnect);
            p2.add(Box.createVerticalStrut(20));

            JPanel p3 = new VerticalPanel();
            p3.setBorder(new EmptyBorder(20, 0, 0, 0));
            p3.add("Connection status: ", m_status);

            JPanel p4 = new JPanel(new BorderLayout());
            p4.add(p1, BorderLayout.WEST);
            p4.add(p2);
            p4.add(p3, BorderLayout.SOUTH);

            setLayout(new BorderLayout());
            add(p4, BorderLayout.NORTH);
        }

        public void setConnectionStatus(String s) {
            m_status.setText(s);
        }

        protected void onConnect(String portNum) {
            int port = Integer.parseInt(portNum);
            int clientId = Integer.parseInt(m_clientId.getText());
            System.out.println(" port " + portNum + " client id " + clientId
                    + " connect options " + m_connectOptionsTF.getText());
            controller().connect(m_host.getText(), port, clientId, m_connectOptionsTF.getText());
        }
    }

    public static void updateSystemNotif(String text) {

        systemNotif.setText(text);
        systemNotif.setBackground(shiftColor(systemNotif.getBackground()));

        ScheduledExecutorService es = Executors.newScheduledThreadPool(1);

        es.schedule(() -> {
            systemNotif.setText("");
            systemNotif.setBackground(Color.orange);
        }, 10, TimeUnit.SECONDS);

    }

    static void updateSystemTime(String text) {
        systemTime.setText(text);
        //systemNotif.setBackground(shiftColor(systemNotif.getBackground()));
    }

    public static void updateTWSTime(String text) {
        twsTime.setText(text);
        //systemNotif.setBackground(shiftColor(systemNotif.getBackground()));
    }

    static Color shiftColor(Color c) {
        return new Color(c.getRed(), (c.getGreen() + 50) % 250, c.getBlue());
    }

    private static class Logger implements ILogger {

        final private JTextArea m_area;

        Logger(JTextArea area) {
            m_area = area;
        }

        @Override
        public void log(final String str) {
            SwingUtilities.invokeLater(() -> {
                LocalTime start = LocalTime.now();
                System.out.println(" start " + LocalTime.now());
                m_area.append(str);
                Dimension d = m_area.getSize();
                m_area.scrollRectToVisible(new Rectangle(0, d.height, 1, 1));
                LocalTime end = LocalTime.now();
            });
        }
    }
}
