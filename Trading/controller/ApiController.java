/* Copyright (C) 2013 Interactive Brokers LLC. All rights reserved.  This code is subject to the terms
 * and conditions of the IB API Non-Commercial License or the IB API Commercial License, as applicable. */
package controller;

import apidemo.*;
import client.*;
import client.Types.*;
import controller.ApiConnection.ILogger;
import handler.HistDataConsumer;
import handler.HistoricalHandler;
import handler.LiveHandler;
import handler.SGXFutureReceiver;
import historical.Request;
import utility.Utility;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static apidemo.ChinaMain.*;
import static apidemo.XUTrader.globalIdOrderMap;
import static java.util.stream.Collectors.toList;
import static utility.Utility.*;

public class ApiController implements EWrapper {

    private final ApiConnection m_client;
    private final ILogger m_outLogger;
    private final ILogger m_inLogger;
    //private int m_reqId;	// used for all requests except orders; designed not to conflict with m_orderId
    private static volatile AtomicInteger m_reqId = new AtomicInteger(10000000);
    private AtomicInteger m_orderId = new AtomicInteger(0);

    private final IConnectionHandler m_connectionHandler;
    private ITradeReportHandler m_tradeReportHandler;
    private IAdvisorHandler m_advisorHandler;
    private IScannerHandler m_scannerHandler;
    private ITimeHandler m_timeHandler;
    private IBulletinHandler m_bulletinHandler;
    private final HashMap<Integer, IInternalHandler> m_contractDetailsMap = new HashMap<>();
    private final HashMap<Integer, IOptHandler> m_optionCompMap = new HashMap<>();
    private final HashMap<Integer, IEfpHandler> m_efpMap = new HashMap<>();
    private final HashMap<Integer, ITopMktDataHandler> m_topMktDataMap = new HashMap<>();
    private final HashMap<Integer, IDeepMktDataHandler> m_deepMktDataMap = new HashMap<>();
    private final HashMap<Integer, IScannerHandler> m_scannerMap = new HashMap<>();
    private final HashMap<Integer, IRealTimeBarHandler> m_realTimeBarMap = new HashMap<>();
    private final HashMap<Integer, IHistoricalDataHandler> m_historicalDataMap = new HashMap<>();
    private final HashMap<Integer, IFundamentalsHandler> m_fundMap = new HashMap<>();
    private final HashMap<Integer, IOrderHandler> m_orderHandlers = new HashMap<>();
    private final HashMap<Integer, IAccountSummaryHandler> m_acctSummaryHandlers = new HashMap<>();
    private final HashMap<Integer, IMarketValueSummaryHandler> m_mktValSummaryHandlers = new HashMap<>();
    private final ConcurrentHashSet<IPositionHandler> m_positionHandlers = new ConcurrentHashSet<>();
    private final ConcurrentHashSet<IAccountHandler> m_accountHandlers = new ConcurrentHashSet<>();
    private final ConcurrentHashSet<ILiveOrderHandler> m_liveOrderHandlers = new ConcurrentHashSet<>();
    static volatile boolean m_connected = false;
    //data structure to store
    //private final ArrayList<ArrayList<Double>> results = new ArrayList<ArrayList<Double>>();
    //private final HashMap<Integer, HashMap<Double,Double>> results2 = new HashMap<Integer, HashMap<Double,Double>>();

    private final HashMap<Integer, TreeMap<LocalTime, Double>> map1 = new HashMap<>(); //stock symbol and map2
    //private final TreeMap<Double,Double> map2 = new TreeMap<>(); //time from 9:30 to 16pm

    //symbol and request ID map
    private final HashMap<Integer, String> m_symReqMap = new HashMap<>();  //for intraday data
    private final HashMap<Integer, String> m_symReqMapH = new HashMap<>(); //for historical data

    //for historical data
    //private static final SimpleDateFormat FORMAT = new SimpleDateFormat( "yyyyMMdd HH:mm:ss");
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm");

    // for historical data maps
    private final HashMap<Integer, TreeMap<LocalTime, Double>> map1h = new HashMap<>(); //stock symbol and map2
    private final TreeMap<LocalTime, Double> map2h = new TreeMap<>(); //time from 9:30 to 16pm

    //calendar
    private static final Calendar CAL = Calendar.getInstance();

    //Handler for top market data
    private final HashMap<Integer, ITopMktDataHandler1> m_topMktDataMap1 = new HashMap<>();
    //Handle for contract details
    private final HashMap<Integer, IInternalHandler1> m_contractDetailsMap1 = new HashMap<>();

    public void generateData() {
        List<Integer> numbers;
        // HashMap<Integer,HashMap<Double,Double>> map1 = new HashMap<Integer,HashMap<Double,Double>>();
        // HashMap<Double,Double> map2 = new HashMap<Double,Double>();

        LocalTime lt = LocalTime.of(9, 30);

        while (lt.getHour() < 16) {
            if (lt.getHour() > 12 && lt.getHour() < 13) {
                lt = LocalTime.of(13, 0);
            }
            map2h.put(lt, 0.0);
            lt = lt.plusMinutes(1);
        }

        try {
            numbers = Files.lines(Paths.get(TradingConstants.GLOBALPATH + "Table2.txt"))
                    .map(line -> line.split("\\s+"))
                    .flatMap(Arrays::stream)
                    .map(Integer::valueOf)
                    .distinct()
                    .collect(toList());
            numbers.forEach((value) -> {
                map1.put(value, new TreeMap<>());
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectAck() {
        //m_connected
        if (m_client.isAsyncEConnect()) {
            pr(" connect Ack sstarting API ");
            m_client.startAPI();
        }
    }

    @Override
    public void orderStatus(int orderId, String status, double filled, double remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
    }

    @Override
    public void updatePortfolio(Contract contract, double position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
    }

    @Override
    public void deltaNeutralValidation(int reqId, DeltaNeutralContract underComp) {
    }

    //    @Override
//    public void position(String account, Contract contract, double pos, double avgCost) {
//        pr(ChinaStockHelper.str("account contract pos avgcost", account, contract, pos, avgCost));
//    }
    @Override
    public void verifyAndAuthMessageAPI(String apiData, String xyzChallange) {
    }

    @Override
    public void verifyAndAuthCompleted(boolean isSuccessful, String errorText) {
    }

    @Override
    public void positionMulti(int reqId, String account, String modelCode, Contract contract, double pos, double avgCost) {
    }

    @Override
    public void positionMultiEnd(int reqId) {
    }

    @Override
    public void accountUpdateMulti(int reqId, String account, String modelCode, String key, String value, String currency) {
    }

    @Override
    public void accountUpdateMultiEnd(int reqId) {
    }

    // ---------------------------------------- Constructor and Connection handling ----------------------------------------
    public interface IConnectionHandler {
        void connected();

        void disconnected();

        void accountList(ArrayList<String> list);

        void error(Exception e);

        void message(int id, int errorCode, String errorMsg);

        void show(String string);

        public static class DefaultConnectionHandler implements IConnectionHandler {
            @Override
            public void connected() {
                m_connected = true;
                pr("Default Conn Handler: connected");
            }

            @Override
            public void disconnected() {
                pr("Default Conn Handler: disconnected");
            }

            @Override
            public void accountList(ArrayList<String> list) {
                pr(" account list is " + list);
            }

            @Override
            public void error(Exception e) {
                pr(" error in iconnectionHandler");
                e.printStackTrace();
            }

            @Override
            public void message(int id, int errorCode, String errorMsg) {
                pr(" error ID " + id + " error code " + errorCode + " errormsg " + errorMsg);
            }

            @Override
            public void show(String string) {
                pr(" show string " + string);
            }
        }

        public static class ChinaMainHandler implements IConnectionHandler {

            @Override
            public void connected() {
                show("connected");
                pr(" connected from connected ");

                SwingUtilities.invokeLater(() -> {
                    ChinaMain.m_connectionPanel.setConnectionStatus("connected");
                    ChinaMain.connectionIndicator.setBackground(Color.green);
                    ChinaMain.connectionIndicator.setText("通");
                    ibConnLatch.countDown();
                    pr(" ib con latch counted down in Apicontroller connected " + LocalTime.now()
                            + " latch remains: " + ibConnLatch.getCount());
                });

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
                pr(" setting panel status disconnected ");

                SwingUtilities.invokeLater(() -> {
                    ChinaMain.m_connectionPanel.setConnectionStatus("disconnected");
                    ChinaMain.connectionIndicator.setBackground(Color.red);
                    ChinaMain.connectionIndicator.setText("断");
                });
            }

            @Override
            public void accountList(ArrayList<String> list) {
                //show("Received account list");
            }

            @Override
            public void show(final String str) {
                pr(str);
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
        }

    }

    public ApiController(IConnectionHandler handler, ILogger inLogger, ILogger outLogger) {
        m_connectionHandler = handler;
        m_client = new ApiConnection(this, inLogger, outLogger);
        m_inLogger = inLogger;
        m_outLogger = outLogger;
    }

    //	public void connect( String host, int port, int clientId) {
//            pr(" in connect " + host + " " + port + " " + clientId);
//            m_client.eConnect(host, port, clientId);
//            sendEOM();
//	}
    public void connect(String host, int port, int clientId, String connectionOpts) {
        pr(" ------------------in connect----------------- " + host + " " + port + " " + clientId);
        pr(str(" checking connection BEFORE----", checkConnection(), port));
        m_client.eConnect(host, port, clientId);
        startMsgProcessingThread();
        pr(str(" checking connection AFTER-----" + checkConnection(), port));
        sendEOM();
        pr(str(" checking connection AFTER EOM-----", checkConnection(), port));
    }

    public ApiConnection client() {
        return m_client;
    }

    private void startMsgProcessingThread() {
        final EReaderSignal signal = new EJavaSignal();
        final EReader reader = new EReader(client(), signal);
        reader.start();
        new Thread() {
            @Override
            public void run() {
                while (client().isConnected()) {
                    signal.waitForSignal();
                    try {
                        reader.processMsgs();
                    } catch (IOException e) {
                        error(e);
                    }
                }
            }
        }.start();
    }

    public void disconnect() {
        pr(" check connection in disconnect BEFORE " + checkConnection());

        m_client.eDisconnect();
        m_connectionHandler.disconnected();
        m_connected = false;
        pr("api controller disconnecting...exiting");
        sendEOM();

        pr(" check connected AFTER " + isConnected());
        pr(" check connection in disconnect AFTER " + checkConnection());
    }

    public void setConnectionStatus(boolean s) {
        m_connected = s;
    }

    private boolean isConnected() {
        return m_connected;
    }

    private boolean checkConnection() {
        if (!isConnected()) {
            error(EClientErrors.NO_VALID_ID, EClientErrors.NOT_CONNECTED.code(), EClientErrors.NOT_CONNECTED.msg());
            return false;
        }
        return true;
    }

    //	public void disconnect() {
//            m_client.eDisconnect();
//            m_connectionHandler.disconnected();
//            sendEOM();
//	}
    @Override
    public void managedAccounts(String accounts) {
        ArrayList<String> list = new ArrayList<>();
        for (StringTokenizer st = new StringTokenizer(accounts, ","); st.hasMoreTokens(); ) {
            list.add(st.nextToken());
        }
        m_connectionHandler.accountList(list);
        recEOM();
    }

    @Override
    public void nextValidId(int orderId) {
        m_orderId.set(orderId);
        //m_reqId = m_orderId + 10000000; // let order id's not collide with other request id's
        m_reqId.set(m_orderId.getAndIncrement() + 10000000);
        if (m_connectionHandler != null) {
            m_connectionHandler.connected();
        }

        recEOM();
    }

    @Override
    public void error(Exception e) {
        m_connectionHandler.error(e);
    }

    @Override
    public void error(int id, int errorCode, String errorMsg) {
        IOrderHandler handler = m_orderHandlers.get(id);
        if (handler != null) {
            handler.handle(errorCode, errorMsg);
        }

        m_liveOrderHandlers.forEach((liveHandler) -> {
            liveHandler.handle(id, errorCode, errorMsg);
        });

        // "no sec def found" response?
        if (errorCode == 200) {
            IInternalHandler hand = m_contractDetailsMap.remove(id);
            if (hand != null) {
                hand.contractDetailsEnd();
            }
        }

        m_connectionHandler.message(id, errorCode, errorMsg);
        recEOM();
    }

    @Override
    public void connectionClosed() {
        pr(" closing connection blah ");
        m_connectionHandler.disconnected();
    }

    // ---------------------------------------- Account and portfolio updates ----------------------------------------
    public interface IAccountHandler {

        public void accountValue(String account, String key, String value, String currency);

        public void accountTime(String timeStamp);

        public void accountDownloadEnd(String account);

        public void updatePortfolio(Position position);
    }

    public void reqAccountUpdates(boolean subscribe, String acctCode, IAccountHandler handler) {
        m_accountHandlers.add(handler);
        m_client.reqAccountUpdates(subscribe, acctCode);
        sendEOM();
    }

    @Override
    public void updateAccountValue(String tag, String value, String currency, String account) {
        if (tag.equals("Currency")) { // ignore this, it is useless
            return;
        }
        m_accountHandlers.forEach((handler) -> {
            handler.accountValue(account, tag, value, currency);
        });
        recEOM();
    }

    @Override
    public void updateAccountTime(String timeStamp) {
        m_accountHandlers.forEach((handler) -> {
            handler.accountTime(timeStamp);
        });
        recEOM();
    }

    @Override
    public void accountDownloadEnd(String account) {
        m_accountHandlers.forEach((handler) -> {
            handler.accountDownloadEnd(account);
        });
        recEOM();
    }

    public void updatePortfolio(Contract contractIn, int positionIn, double marketPrice, double marketValue,
                                double averageCost, double unrealizedPNL, double realizedPNL, String account)
            throws CloneNotSupportedException {
        Contract contract = contractIn.clone();
        contract.exchange(contractIn.exchange());

        Position position = new Position(contract, account,
                positionIn, marketPrice, marketValue, averageCost, unrealizedPNL, realizedPNL);
        m_accountHandlers.forEach((handler) -> handler.updatePortfolio(position));
        recEOM();
    }

    // ---------------------------------------- Account Summary handling ----------------------------------------
    public interface IAccountSummaryHandler {

        void accountSummary(String account, AccountSummaryTag tag, String value, String currency);

        void accountSummaryEnd();

        class AccountInfoHandler implements IAccountSummaryHandler {

            @Override
            public void accountSummary(String account, AccountSummaryTag tag, String value, String currency) {
                String output = getStrCheckNull(LocalDateTime.now(), account, tag, value, currency);
                pr(output);
                if (LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).getMinute() <= 1) {
                    XuTraderHelper.outputToAutoLog(output);
                }
            }

            @Override
            public void accountSummaryEnd() {
                //pr(" account summary end, cancelling acct summary ");
                ChinaMain.controller().cancelAccountSummary(this);

            }
        }
    }

    public interface IMarketValueSummaryHandler {

        void marketValueSummary(String account, MarketValueTag tag, String value, String currency);

        void marketValueSummaryEnd();
    }

    /**
     * @param group pass "All" to get data for all accounts
     */
    public void reqAccountSummary(String group, AccountSummaryTag[] tags, IAccountSummaryHandler handler) {
        StringBuilder sb = new StringBuilder();
        for (AccountSummaryTag tag : tags) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(tag);
        }
        int reqId = m_reqId.getAndIncrement();
        m_acctSummaryHandlers.put(reqId, handler);
        m_client.reqAccountSummary(reqId, group, sb.toString());
        sendEOM();
    }

    public void cancelAccountSummary(IAccountSummaryHandler handler) {
        Integer reqId = getAndRemoveKey(m_acctSummaryHandlers, handler);
        if (reqId != null) {
            m_client.cancelAccountSummary(reqId);
            sendEOM();
        }
    }

    public void reqMarketValueSummary(String group, IMarketValueSummaryHandler handler) {
        //int reqId = m_reqId++;
        int reqId = m_reqId.getAndIncrement();
        m_mktValSummaryHandlers.put(reqId, handler);
        m_client.reqAccountSummary(reqId, group, "$LEDGER");
        sendEOM();
    }

    public void cancelMarketValueSummary(IMarketValueSummaryHandler handler) {
        Integer reqId = getAndRemoveKey(m_mktValSummaryHandlers, handler);
        if (reqId != null) {
            m_client.cancelAccountSummary(reqId);
            sendEOM();
        }
    }

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {

//        System.out.print(Utility.getStrCheckNull("", reqId, Optional.ofNullable(account).orElse("accountnull"),
//                Optional.ofNullable(tag).orElse("tagnull"),
//                Optional.ofNullable(value).orElse("valuenull"),
//                Optional.ofNullable(currency).orElse("currencynull")));

        if (tag.equals("Currency")) { // ignore this, it is useless
            return;
        }

        IAccountSummaryHandler handler = m_acctSummaryHandlers.get(reqId);
        if (handler != null) {
            handler.accountSummary(account, AccountSummaryTag.valueOf(tag), value, currency);
        }

        IMarketValueSummaryHandler handler2 = m_mktValSummaryHandlers.get(reqId);
        if (handler2 != null) {
            handler2.marketValueSummary(account, MarketValueTag.valueOf(tag), value, currency);
        }

        recEOM();
    }

    @Override
    public void accountSummaryEnd(int reqId) {
        IAccountSummaryHandler handler = m_acctSummaryHandlers.get(reqId);
        if (handler != null) {
            handler.accountSummaryEnd();
        }

        IMarketValueSummaryHandler handler2 = m_mktValSummaryHandlers.get(reqId);
        if (handler2 != null) {
            handler2.marketValueSummaryEnd();
        }

        recEOM();
    }

    // ---------------------------------------- Position handling ----------------------------------------
    public interface IPositionHandler {

        void position(String account, Contract contract, double position, double avgCost);

        void positionEnd();

        public static class DefaultPositionHandler implements IPositionHandler {

            @Override
            public void position(String account, Contract contract, double position, double avgCost) {

                pr(" in default position handler " + Utility.str(account, contract.toString(), position, avgCost));
            }

            @Override
            public void positionEnd() {
            }

        }
    }

    public void reqPositions(IPositionHandler handler) {
        //pr(" requesting for position in reqPositions");
        //pr ( " size position handler BEFORE" + m_positionHandlers.size());
        //pr(" requesting position " + );
        m_positionHandlers.add(handler);
        m_client.reqPositions();
        sendEOM();
        //pr ( " size position handler AFTER" + m_positionHandlers.size());
    }

    public void cancelPositions(IPositionHandler handler) {
        m_positionHandlers.remove(handler);
        m_client.cancelPositions();
        sendEOM();
    }

    @Override
    public void position(String account, Contract contract, double pos, double avgCost) {
        //@Override public void position(String account, Contract contract, int pos, double avgCost) {
        //Contract contract = contract.clone();
        //pr (" handler count " + m_positionHandlers.size());
        m_positionHandlers.forEach((handler) -> {
            //pr ( " handling in handler " + handler.toString());
            handler.position(account, contract, pos, avgCost);
        });
        recEOM();
    }

    @Override
    public void positionEnd() {
        m_positionHandlers.forEach((handler) -> {
            handler.positionEnd();
        });
        recEOM();
    }

    // ---------------------------------------- Contract Details ----------------------------------------
    public interface IContractDetailsHandler {

        void contractDetails(ArrayList<ContractDetails> list);
    }

    public void reqContractDetails(Contract contract, final IContractDetailsHandler processor) {
        final ArrayList<ContractDetails> list = new ArrayList<>();
        internalReqContractDetails(contract, new IInternalHandler() {
            @Override
            public void contractDetails(ContractDetails data) {
                list.add(data);
            }

            @Override
            public void contractDetailsEnd() {
                processor.contractDetails(list);
            }
        });
        sendEOM();
    }

    private interface IInternalHandler {

        void contractDetails(ContractDetails data);

        void contractDetailsEnd();
    }

    public interface IInternalHandler1 {

        void contractDetails(ContractDetails data);
    }

    public void customReqContractDetais(Contract contract, IInternalHandler1 processor) {
        int reqId = m_reqId.getAndIncrement();
        m_contractDetailsMap1.put(reqId, processor);
        m_client.reqContractDetails(reqId, contract);
    }

    public void customReqContractDetailFull(IInternalHandler1 processor) {

        pr("requesting contract data begins");

        Contract ct = new Contract();

        int reqId = m_reqId.getAndIncrement();
        int counter = 1;
        for (int nextVal : map1.keySet()) {
            reqId++;
            ct.symbol(String.valueOf(nextVal));
            ct.exchange("SEHK");
            ct.currency("HKD");
            ct.secType(SecType.STK);
            m_contractDetailsMap1.put(reqId, processor);
            m_client.reqContractDetails(reqId, ct);
            counter++;
            try {
                if (counter % 80 == 0) {
                    Thread.sleep(2000);
                    pr("thread sleeping");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        pr("requesting contract data ends");
    }

    private void internalReqContractDetails(Contract contract, IInternalHandler processor) {
        //int reqId = m_reqId++;
        int reqId = m_reqId.getAndIncrement();
        m_contractDetailsMap.put(reqId, processor);
        m_client.reqContractDetails(reqId, contract);
        sendEOM();
    }

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        IInternalHandler handler = m_contractDetailsMap.get(reqId);
        IInternalHandler1 handler1 = m_contractDetailsMap1.get(reqId);

        if (handler != null) {
            handler.contractDetails(contractDetails);
        } else if (handler1 != null) {
            handler1.contractDetails(contractDetails);
        } else {
            show("Error: no contract details handler for reqId " + reqId);
        }
        recEOM();
    }

    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        IInternalHandler handler = m_contractDetailsMap.get(reqId);
        if (handler != null) {
            handler.contractDetails(contractDetails);
        } else {
            show("Error: no bond contract details handler for reqId " + reqId);
        }
        recEOM();
    }

    @Override
    public void contractDetailsEnd(int reqId) {
        IInternalHandler handler = m_contractDetailsMap.remove(reqId);
        if (handler != null) {
            handler.contractDetailsEnd();
        } else {
            show("Error: no contract details handler for reqId " + reqId);
        }
        recEOM();
    }

    // ---------------------------------------- Top Market Data handling ----------------------------------------
    public interface ITopMktDataHandler1 {

        void tickPrice(int symb, TickType tickType, double price, int canAutoExecute);

        void tickSize(int symb, TickType tickType, int size);

        void tickString(TickType tickType, String value);

        void tickSnapshotEnd();

        void marketDataType(MktDataType marketDataType);
    }

    public interface ITopMktDataHandler {

        void tickPrice(TickType tickType, double price, int canAutoExecute);

        void tickSize(TickType tickType, int size);

        void tickString(TickType tickType, String value);

        void tickSnapshotEnd();

        void marketDataType(MktDataType marketDataType);
    }

    public interface IEfpHandler extends ITopMktDataHandler {

        void tickEFP(int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry);
    }

    public interface IOptHandler extends ITopMktDataHandler {

        void tickOptionComputation(TickType tickType, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice);
    }

    public static class TopMktDataAdapter implements ITopMktDataHandler {

        @Override
        public void tickPrice(TickType tickType, double price, int canAutoExecute) {
        }

        @Override
        public void tickSize(TickType tickType, int size) {
        }

        @Override
        public void tickString(TickType tickType, String value) {
        }

        @Override
        public void tickSnapshotEnd() {
        }

        @Override
        public void marketDataType(MktDataType marketDataType) {
        }
    }

    // This method attempts to get real time data
    // write method to store it in a table AND export to excel/matlab
    public void reHKMktDataArray(ITopMktDataHandler1 handler) throws InterruptedException {
        pr("requesting mkt data begins");
        Contract ct = new Contract();
        //int reqId=m_reqId++;

        //int reqId = m_reqId.getAndIncrement();
        //int reqId = m_reqId.get() + 1000;

        int reqId = m_reqId.incrementAndGet();
        int counter = 1;
        boolean isSnapShot = true;

        Iterator it = map1.keySet().iterator();

        if (((LocalTime.now().isAfter(LocalTime.of(9, 19)) && LocalTime.now().isBefore(LocalTime.of(12, 1)))
                || (LocalTime.now().isAfter(LocalTime.of(12, 59)) && LocalTime.now().isBefore(LocalTime.of(16, 1))))) {
            while (it.hasNext()) {
                if (isSnapShot) {
                    Object nextVal = it.next();
                    reqId++;
                    // m_reqId.getAndIncrement();
                    ct.symbol(String.valueOf(nextVal));
                    ct.exchange("SEHK");
                    ct.currency("HKD");
                    ct.secType(SecType.STK);
                    m_symReqMap.put(reqId, String.valueOf(nextVal));
                    m_topMktDataMap1.put(reqId, handler);
                    m_client.reqMktData(reqId, ct, "", isSnapShot, Collections.<TagValue>emptyList());
                    counter++;
                    if (counter % 90 == 0) {
                        Thread.sleep(1000);
                    }
                } else {
                    Object nextVal = it.next();
                    reqId++;
                    pr("Symbol ID is " + nextVal);
                    pr("ticker ID is " + reqId);
                    ct.symbol(String.valueOf(nextVal));
                    ct.exchange("SEHK");
                    ct.currency("HKD");
                    ct.secType(SecType.STK);
                    m_symReqMap.put(reqId, String.valueOf(nextVal));
                    m_topMktDataMap1.put(reqId, handler);
                    m_client.reqMktData(reqId, ct, "", isSnapShot, Collections.<TagValue>emptyList());
                }
            }
            pr("requesting mkt data ends");
        }
    }

    public void reqHKLiveData() {
        pr(" requesting hk ");
        HKData.es.shutdown();
        HKData.es = Executors.newScheduledThreadPool(10);
        HKData.es.scheduleAtFixedRate(() -> {
            HKData.hkPriceBar.keySet().forEach(k -> req1HKStockLive(k));
        }, 5L, 10L, TimeUnit.SECONDS);

    }

    public void reqHKTodayData() {
        //HKData.historyDataSem.drainPermits();
        HKData.historyDataSem = new Semaphore(50);
        HKData.hkPriceBar.keySet().forEach(k -> req1HKStockToday(k));
    }

    public void req1HKStockToday(String stock) {
        CompletableFuture.runAsync(() -> {
            try {
                HKData.historyDataSem.acquire();
                int reqId = m_reqId.incrementAndGet();
                Contract ct = generateHKContract(stock);
                ChinaMain.globalRequestMap.put(reqId, new Request(ct, apidemo.ChinaMain.hkdata));
                String formatTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));

                //pr(" format time " + formatTime);
                String durationStr = 1 + " " + DurationUnit.DAY.toString().charAt(0);
                m_client.reqHistoricalData(reqId, ct, formatTime, durationStr,
                        Types.BarSize._1_min.toString(), Types.WhatToShow.TRADES.toString(),
                        0, 2, Collections.<TagValue>emptyList());
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        });
    }

    public void req1HKStockLive(String stock) {
        try {
            //HKData.dataSemaphore.acquire();
            int reqId = m_reqId.incrementAndGet();
            if (reqId % 90 == 0) {
                //pr(" sleeping " + Thread.currentThread());
                Thread.sleep(1000);
            }
            Contract ct = generateHKContract(stock);
            //pr(" requesting for hk stock " + stock + " id " + reqId);
            ChinaMain.globalRequestMap.put(reqId, new Request(ct, apidemo.ChinaMain.hkdata));

            m_client.reqMktData(reqId, ct, "", true, Collections.<TagValue>emptyList());
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    private Contract generateHKContract(String stock) {
        Contract ct = new Contract();
        ct.symbol(stock);
        ct.exchange("SEHK");
        ct.currency("HKD");
        ct.secType(SecType.STK);
        return ct;
    }

    //xu data
    public void reqXUDataArray() {
        pr("requesting XU data begins " + LocalTime.now());
        Contract frontCt = getFrontFutContract();
        Contract backCt = getBackFutContract();

        int reqIdFront = m_reqId.incrementAndGet();
        int reqIdBack = m_reqId.incrementAndGet();

        if (!globalRequestMap.containsKey(reqIdFront) && !globalRequestMap.containsKey(reqIdBack)) {
            ChinaMain.globalRequestMap.put(reqIdFront, new Request(frontCt, SGXFutureReceiver.getReceiver()));
            ChinaMain.globalRequestMap.put(reqIdBack, new Request(backCt, SGXFutureReceiver.getReceiver()));
            if (m_client.isConnected()) {
                m_client.reqMktData(reqIdFront, frontCt, "", false, Collections.<TagValue>emptyList());
                m_client.reqMktData(reqIdBack, backCt, "", false, Collections.<TagValue>emptyList());
            } else {
                pr(" reqXUDataArray but not connected ");
            }
        } else {
            m_reqId.incrementAndGet();
            pr(" req used " + reqIdFront + " " + globalRequestMap.get(reqIdFront).getContract());
            throw new IllegalArgumentException(" req ID used ");
        }
        pr("requesting XU data ends");
    }


    public void getSGXA50HistoricalCustom(int reqId, Contract c, HistDataConsumer<Contract, String, Double, Integer> dc,
                                          int duration) {
        //Contract c = getFrontFutContract();
        //        Contract c = new Contract();
//        c.symbol("XINA50");
//        c.exchange("SGX");
//        c.currency("USD");
//        c.secType(Types.SecType.FUT);
//        c.lastTradeDateOrContractMonth(TradingConstants.A50_FRONT_EXPIRY);
//        c.right(Types.Right.None);
//        c.secIdType(Types.SecIdType.None);

        String formatTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));

        //int duration = 7;
        DurationUnit durationUnit = DurationUnit.DAY;
        String durationStr = duration + " " + durationUnit.toString().charAt(0);
        Types.BarSize barSize = Types.BarSize._1_min;
        WhatToShow whatToShow = WhatToShow.TRADES;
        boolean rthOnly = false;

        ChinaMain.globalRequestMap.put(reqId, new Request(c, dc));

        CompletableFuture.runAsync(() -> m_client.reqHistoricalData(reqId, c, "", durationStr,
                barSize.toString(), whatToShow.toString(), 0, 2, Collections.<TagValue>emptyList()));
    }

    //    public void getHKIntradayHistoricalData(HistoricalHandler hh) {
//        reqHistoricalDataUSHK(this, uniqueID.get(), generateHKContract(stock), CUTOFFTIME,
//                DAYSTOREQUEST, Types.DurationUnit.DAY,
//                Types.BarSize._1_day, Types.WhatToShow.TRADES, true);
//    }
    public void getSGXA50Historical2(int reqID, HistoricalHandler hh) {
        Contract previousFut = getExpiredFutContract();
        Contract frontFut = getFrontFutContract();
        Contract backFut = getBackFutContract();

        String formatTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));

        int duration = 4;
        DurationUnit durationUnit = DurationUnit.DAY;
        String durationStr = duration + " " + durationUnit.toString().charAt(0);
        Types.BarSize barSize = Types.BarSize._1_min;
        WhatToShow whatToShow = WhatToShow.TRADES;

//        if(!globalRequestMap.containsKey(reqID) && !globalRequestMap.containsKey(reqID+1)) {
        ChinaMain.globalRequestMap.put(reqID, new Request(frontFut, hh));
        ChinaMain.globalRequestMap.put(reqID + 1, new Request(backFut, hh));


        CompletableFuture.runAsync(() -> {

            m_client.reqHistoricalData(reqID, frontFut, "", durationStr, barSize.toString(),
                    whatToShow.toString(), 0, 2, Collections.<TagValue>emptyList());
            m_client.reqHistoricalData(reqID + 1, backFut, "", durationStr, barSize.toString(),
                    whatToShow.toString(), 0, 2, Collections.<TagValue>emptyList());

            if (ChronoUnit.DAYS.between(LocalDate.parse(previousFut.lastTradeDateOrContractMonth(),
                    DateTimeFormatter.ofPattern("yyyyMMdd")), LocalDate.now()) < 7) {
                ChinaMain.globalRequestMap.put(reqID + 2, new Request(previousFut, hh));
                m_client.reqHistoricalData(reqID + 2, previousFut, "", durationStr,
                        barSize.toString(), whatToShow.toString(), 0, 2,
                        Collections.<TagValue>emptyList());
            }
        });
    }

    public void reHistDataArray(IHistoricalDataHandler handler) {
        Contract ct = new Contract();
        int reqId = m_reqId.incrementAndGet();

        for (Object nextVal : map1h.keySet()) {
            reqId++;
            m_historicalDataMap.put(reqId, handler);
            pr("Symbol ID is " + nextVal);
            pr("ticker ID is " + reqId);
            ct.symbol(String.valueOf(nextVal));
            ct.exchange("SEHK");
            ct.currency("HKD");
            ct.secType(SecType.STK);

            m_symReqMapH.put(reqId, String.valueOf(nextVal));

            //m_client.reqMktData(reqId, ct,"",true,Collections.<TagValue>emptyList());
            //m_client.reqHistoricalData(reqId, ct, "20151218 16:00:00", "DAY","1 min" , "TRADES", 0, 2, Collections.<TagValue>emptyList() );
            m_client.reqHistoricalData(reqId, ct, "20151222 16:00:00", "1 D", "1 min", "TRADES", 0, 2, Collections.<TagValue>emptyList());
            sendEOM();
        }
        pr("requesting hist data ends");
    }

    public void reqTopMktData(Contract contract, String genericTickList, boolean snapshot, ITopMktDataHandler handler) {
        int reqId = m_reqId.incrementAndGet();
        m_symReqMap.put(reqId, contract.symbol()); //potential issue
        pr("req id is " + reqId + "contract symbol is " + contract.symbol());
        m_topMktDataMap.put(reqId, handler);

        m_client.reqMktData(reqId, contract, genericTickList, snapshot, Collections.<TagValue>emptyList());

        pr("symbol is " + contract.symbol());
        pr(" reqTopMktData is being requested");

        sendEOM();
    }

    public void reqOptionMktData(Contract contract, String genericTickList, boolean snapshot, IOptHandler handler) {
        int reqId = m_reqId.getAndIncrement();
        m_topMktDataMap.put(reqId, handler);
        m_optionCompMap.put(reqId, handler);
        m_client.reqMktData(reqId, contract, genericTickList, snapshot, Collections.<TagValue>emptyList());
        sendEOM();
    }

    public void reqEfpMktData(Contract contract, String genericTickList, boolean snapshot, IEfpHandler handler) {
        //int reqId = m_reqId++;
        int reqId = m_reqId.getAndIncrement();
        m_topMktDataMap.put(reqId, handler);
        m_efpMap.put(reqId, handler);
        m_client.reqMktData(reqId, contract, genericTickList, snapshot, Collections.<TagValue>emptyList());
        sendEOM();
    }

    public void cancelTopMktData(ITopMktDataHandler handler) {
        Integer reqId = getAndRemoveKey(m_topMktDataMap, handler);
        if (reqId != null) {
            m_client.cancelMktData(reqId);
        } else {
            show("Error: could not cancel top market data");
        }
        sendEOM();
    }

    public void cancelOptionMktData(IOptHandler handler) {
        cancelTopMktData(handler);
        getAndRemoveKey(m_optionCompMap, handler);
    }

    public void cancelEfpMktData(IEfpHandler handler) {
        cancelTopMktData(handler);
        getAndRemoveKey(m_efpMap, handler);
    }

    public void reqMktDataType(MktDataType type) {
        m_client.reqMarketDataType(type.ordinal());
        sendEOM();
    }

    // key method. This method is called when market data changes.
    @Override
    public void tickPrice(int reqId, int tickType, double price, int canAutoExecute) {
        if (ChinaMain.globalRequestMap.containsKey(reqId)) {
            Request r = ChinaMain.globalRequestMap.get(reqId);
            LiveHandler lh = (LiveHandler) ChinaMain.globalRequestMap.get(reqId).getHandler();
            try {
                lh.handlePrice(TickType.get(tickType),
                        utility.Utility.ibContractToSymbol(r.getContract()), price,
                        LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
            } catch (Exception ex) {
                pr(" handling price has issues ");
                ex.printStackTrace();
            }
        }
        recEOM();
    }

    @Override
    public void tickSize(int reqId, int tickType, int size) {
        ITopMktDataHandler handler = m_topMktDataMap.get(reqId);
        ITopMktDataHandler1 handler1;
        int symb;

        if (TickType.get(tickType).equals(TickType.VOLUME) && ChinaMain.globalRequestMap.containsKey(reqId)) {
            Request r = ChinaMain.globalRequestMap.get(reqId);
            LiveHandler lh = (LiveHandler) r.getHandler();
            lh.handleVol(ibContractToSymbol(r.getContract()), size, LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        }

        if (handler != null) {
            handler.tickSize(TickType.get(tickType), size);
        }

        handler1 = m_topMktDataMap1.getOrDefault(reqId, null);

        if (handler1 != null) {
            symb = Integer.parseInt(m_symReqMap.get(reqId));
            handler1.tickSize(symb, TickType.get(tickType), size);
        }

        recEOM();
    }

    @Override
    public void tickGeneric(int reqId, int tickType, double value) {
        ITopMktDataHandler handler = m_topMktDataMap.get(reqId);
        if (handler != null) {
            handler.tickPrice(TickType.get(tickType), value, 0);
        }
        recEOM();
    }


    @Override
    public void tickString(int reqId, int tickType, String value) {
        ITopMktDataHandler handler = m_topMktDataMap.get(reqId);
        if (handler != null) {
            handler.tickString(TickType.get(tickType), value);
        }
        recEOM();
    }

    @Override
    public void tickEFP(int reqId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry) {
        IEfpHandler handler = m_efpMap.get(reqId);
        if (handler != null) {
            handler.tickEFP(tickType, basisPoints, formattedBasisPoints, impliedFuture, holdDays, futureExpiry, dividendImpact, dividendsToExpiry);
        }
        recEOM();
    }

    @Override
    public void tickSnapshotEnd(int reqId) {
        ITopMktDataHandler handler = m_topMktDataMap.get(reqId);
        if (handler != null) {
            handler.tickSnapshotEnd();
        }
        recEOM();
    }

    @Override
    public void marketDataType(int reqId, int marketDataType) {
        ITopMktDataHandler handler = m_topMktDataMap.get(reqId);
        if (handler != null) {
            handler.marketDataType(MktDataType.get(marketDataType));
        }
        recEOM();
    }

    // ---------------------------------------- Deep Market Data handling ----------------------------------------
    public interface IDeepMktDataHandler {

        void updateMktDepth(int position, String marketMaker, DeepType operation, DeepSide side, double price, int size);
    }

    public void reqDeepMktData(Contract contract, int numRows, IDeepMktDataHandler handler) {
        CompletableFuture.runAsync(() -> m_deepMktDataMap.values().forEach(this::cancelDeepMktData)).thenRun(() -> {
            int reqId = m_reqId.getAndIncrement();
            m_deepMktDataMap.put(reqId, handler);
            ArrayList<TagValue> mktDepthOptions = new ArrayList<>();
            m_client.reqMktDepth(reqId, contract, numRows, mktDepthOptions);
            sendEOM();
        });
    }

    private void cancelDeepMktData(IDeepMktDataHandler handler) {
        Integer reqId = getAndRemoveKey(m_deepMktDataMap, handler);
        if (reqId != null) {
            m_client.cancelMktDepth(reqId);
            sendEOM();
        }
    }

    @Override
    public void updateMktDepth(int reqId, int position, int operation, int side, double price, int size) {
        IDeepMktDataHandler handler = m_deepMktDataMap.get(reqId);
        if (handler != null) {
            handler.updateMktDepth(position, null, DeepType.get(operation), DeepSide.get(side), price, size);
        }
        recEOM();
    }

    @Override
    public void updateMktDepthL2(int reqId, int position, String marketMaker, int operation, int side, double price, int size) {
        IDeepMktDataHandler handler = m_deepMktDataMap.get(reqId);
        if (handler != null) {
            handler.updateMktDepth(position, marketMaker, DeepType.get(operation), DeepSide.get(side), price, size);
        }
        recEOM();
    }

    // ---------------------------------------- Option computations ----------------------------------------
    public void reqOptionVolatility(Contract c, double optPrice, double underPrice, IOptHandler handler) {
        int reqId = m_reqId.getAndIncrement();
        //int reqId = m_reqId++;
        m_optionCompMap.put(reqId, handler);
        m_client.calculateImpliedVolatility(reqId, c, optPrice, underPrice);
        sendEOM();
    }

    public void reqOptionComputation(Contract c, double vol, double underPrice, IOptHandler handler) {

        int reqId = m_reqId.getAndIncrement();

        //int reqId = m_reqId++;
        m_optionCompMap.put(reqId, handler);
        m_client.calculateOptionPrice(reqId, c, vol, underPrice);
        sendEOM();
    }

    void cancelOptionComp(IOptHandler handler) {
        Integer reqId = getAndRemoveKey(m_optionCompMap, handler);
        if (reqId != null) {
            m_client.cancelCalculateOptionPrice(reqId);
            sendEOM();
        }
    }

    @Override
    public void tickOptionComputation(int reqId, int tickType, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        IOptHandler handler = m_optionCompMap.get(reqId);
        if (handler != null) {
            handler.tickOptionComputation(TickType.get(tickType), impliedVol, delta, optPrice, pvDividend, gamma, vega, theta, undPrice);
        } else {
            pr(String.format("not handled %s %s %s %s %s %s %s %s %s", tickType, impliedVol, delta, optPrice, pvDividend, gamma, vega, theta, undPrice));
        }
        recEOM();
    }

    // ---------------------------------------- Trade reports ----------------------------------------
    public interface ITradeReportHandler {

        void tradeReport(String tradeKey, Contract contract, Execution execution);

        void tradeReportEnd();

        void commissionReport(String tradeKey, CommissionReport commissionReport);
    }

    public void reqExecutions(ExecutionFilter filter, ITradeReportHandler handler) {
        //pr(" requesting execution ");
        m_tradeReportHandler = handler;
        m_client.reqExecutions(m_reqId.getAndIncrement(), filter);
        sendEOM();
    }

    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        //pr(" exeDetails results out");

        //pr(ChinaStockHelper.getStrCheckNull("|||", reqId,contract.symbol(), execution));
        if (m_tradeReportHandler != null) {
            int i = execution.execId().lastIndexOf('.');
            String tradeKey = execution.execId().substring(0, i);
            m_tradeReportHandler.tradeReport(tradeKey, contract, execution);
        }
        recEOM();
    }

    @Override
    public void execDetailsEnd(int reqId) {
        if (m_tradeReportHandler != null) {
            m_tradeReportHandler.tradeReportEnd();
        }
        recEOM();
    }

    @Override
    public void commissionReport(CommissionReport commissionReport) {
        if (m_tradeReportHandler != null) {
            int i = commissionReport.m_execId.lastIndexOf('.');
            String tradeKey = commissionReport.m_execId.substring(0, i);
            m_tradeReportHandler.commissionReport(tradeKey, commissionReport);
        }
        recEOM();
    }

    // ---------------------------------------- Advisor info ----------------------------------------
    public interface IAdvisorHandler {

        void groups(ArrayList<Group> groups);

        void profiles(ArrayList<Profile> profiles);

        void aliases(ArrayList<Alias> aliases);
    }

    public void reqAdvisorData(FADataType type, IAdvisorHandler handler) {
        m_advisorHandler = handler;
        m_client.requestFA(type.ordinal());
        sendEOM();
    }

    public void updateGroups(ArrayList<Group> groups) {
        m_client.replaceFA(FADataType.GROUPS.ordinal(), AdvisorUtil.getGroupsXml(groups));
        sendEOM();
    }

    public void updateProfiles(ArrayList<Profile> profiles) {
        m_client.replaceFA(FADataType.PROFILES.ordinal(), AdvisorUtil.getProfilesXml(profiles));
        sendEOM();
    }

    @Override
    public final void receiveFA(int faDataType, String xml) {
        if (m_advisorHandler == null) {
            return;
        }

        FADataType type = FADataType.get(faDataType);

        switch (type) {
            case GROUPS:
                ArrayList<Group> groups = AdvisorUtil.getGroups(xml);
                m_advisorHandler.groups(groups);
                break;

            case PROFILES:
                ArrayList<Profile> profiles = AdvisorUtil.getProfiles(xml);
                m_advisorHandler.profiles(profiles);
                break;

            case ALIASES:
                ArrayList<Alias> aliases = AdvisorUtil.getAliases(xml);
                m_advisorHandler.aliases(aliases);
                break;
        }
        recEOM();
    }


    // ---------------------------------------- Trading and Option Exercise ----------------------------------------

    /**
     * This interface is for receiving events for a specific order placed from
     * the API. Compare to ILiveOrderHandler.
     */
    public interface IOrderHandler {
        void orderState(OrderState orderState);

        void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId,
                         int parentId, double lastFillPrice, int clientId, String whyHeld);

        void handle(int errorCode, String errorMsg);

        class DefaultOrderHandler implements IOrderHandler {
            int defaultID;

            public DefaultOrderHandler() {
                defaultID = 0;
            }

            public DefaultOrderHandler(int i) {
                defaultID = i;
            }

            @Override
            public void orderState(OrderState orderState) {
                globalIdOrderMap.get(defaultID).setFinalActionTime(LocalDateTime.now());
                globalIdOrderMap.get(defaultID).setStatus(orderState.status());

                if (orderState.status() == OrderStatus.Filled) {
                    String msg = str("|| OrderState ||", defaultID, globalIdOrderMap.get(defaultID),
                            orderState.status());
                    XuTraderHelper.outputToAutoLog(msg);
                    XuTraderHelper.outputPurelyOrders(msg);

                    if (XuTraderHelper.isFlattenTrade().test(globalIdOrderMap.get(defaultID).getTradeType())) {
                        XUTrader.flattenEagerness = Eagerness.Passive;
                    }
                } else if (orderState.status() == OrderStatus.Cancelled || orderState.status() == OrderStatus.ApiCancelled) {
                    if (XuTraderHelper.isFlattenTrade().test(globalIdOrderMap.get(defaultID).getTradeType())) {
                        pr(" flatten trade IOC cancelled, flatten aggressively ");
                        XUTrader.flattenAggressively();

                        if (XUTrader.flattenEagerness == Eagerness.Passive) {
                            pr(" in flatten order handler change to aggressive");
                            XUTrader.flattenEagerness = Eagerness.Aggressive;
                        }
                    }
                } else {
                    pr(" default order state ELSE: ", orderState.status(),
                            globalIdOrderMap.get(defaultID));
                }
            }

            @Override
            public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId,
                                    int parentId, double lastFillPrice, int clientId, String whyHeld) {
                XuTraderHelper.outputToAutoLog(str("||OrderStatus||", defaultID,
                        globalIdOrderMap.get(defaultID), status, filled,
                        remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld));
            }

            @Override
            public void handle(int errorCode, String errorMsg) {
                pr(" handle error code " + errorCode + " message " + errorMsg);
            }
        }
    }

    public void placeOrModifyOrder(Contract contract, final Order order, final IOrderHandler handler) {
        // when placing new order, assign new order id
        if (order.lmtPrice() == 0.0 || order.totalQuantity() == 0.0) {
            return;
        }

        if (order.orderId() == 0) {
            order.orderId(m_orderId.incrementAndGet());
        }

        if (handler != null) {
            pr(str("place or modify order ", order.orderId(), handler));
            m_orderHandlers.put(order.orderId(), handler);
        } else {
            pr(" handler is null");
        }
        m_client.placeOrder(contract, order);
        sendEOM();
    }

    public void cancelOrder(int orderId) {
        pr(" cancelling order in Apicontroller " + orderId);
        m_client.cancelOrder(orderId);
        sendEOM();
    }

    public void cancelAllOrders() {
        m_client.reqGlobalCancel();
        sendEOM();
    }

    public void exerciseOption(String account, Contract contract, ExerciseType type, int quantity, boolean override) {
        m_client.exerciseOptions(m_reqId.getAndIncrement(), contract, type.ordinal(), quantity, account, override ? 1 : 0);
        sendEOM();
    }

    public void removeOrderHandler(IOrderHandler handler) {
        getAndRemoveKey(m_orderHandlers, handler);
    }

    // ---------------------------------------- Live order handling ----------------------------------------

    /**
     * This interface is for downloading and receiving events for all live
     * orders. Compare to IOrderHandler.
     */
    public interface ILiveOrderHandler {
        void openOrder(Contract contract, Order order, OrderState orderState);

        void openOrderEnd();

        void orderStatus(int orderId, OrderStatus status, int filled, int remaining, double avgFillPrice, long permId,
                         int parentId, double lastFillPrice, int clientId, String whyHeld);

        void handle(int orderId, int errorCode, String errorMsg);  // add permId?
    }

    public void reqLiveOrders(ILiveOrderHandler handler) {
        m_liveOrderHandlers.add(handler);
        m_client.reqAllOpenOrders();
        sendEOM();
    }

    public void takeTwsOrders(ILiveOrderHandler handler) {
        m_liveOrderHandlers.add(handler);
        m_client.reqOpenOrders();
        sendEOM();
    }

    public void takeFutureTwsOrders(ILiveOrderHandler handler) {
        m_liveOrderHandlers.add(handler);
        m_client.reqAutoOpenOrders(true);
        sendEOM();
    }

    public void removeLiveOrderHandler(ILiveOrderHandler handler) {
        m_liveOrderHandlers.remove(handler);
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order orderIn, OrderState orderState) {
        //Order order = new Order( orderIn);

        IOrderHandler handler = m_orderHandlers.get(orderId);

//        if (orderId != 0) {
//            pr(str(" order ID ", orderId, contract.symbol(), orderState.status()));
//            //pr(str(" handling open order ", orderId, handler));
//            pr("handler contained in order handlers " + m_orderHandlers.containsKey(orderId));
//        }

        if (handler != null) {
            handler.orderState(orderState);
        }

        if (!orderIn.whatIf()) {
            m_liveOrderHandlers.forEach((liveHandler) -> {
                liveHandler.openOrder(contract, orderIn, orderState);
            });
        }
        recEOM();
    }

    @Override
    public void openOrderEnd() {
        m_liveOrderHandlers.forEach(ILiveOrderHandler::openOrderEnd);
        recEOM();
    }

    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        IOrderHandler handler = m_orderHandlers.get(orderId);
        if (handler != null) {
            handler.orderStatus(OrderStatus.valueOf(status), filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
        }

        m_liveOrderHandlers.forEach((liveOrderHandler) -> {
            liveOrderHandler.orderStatus(orderId, OrderStatus.valueOf(status), filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
        });
        recEOM();
    }

    // ---------------------------------------- Market Scanners ----------------------------------------
    public interface IScannerHandler {

        void scannerParameters(String xml);

        void scannerData(int rank, ContractDetails contractDetails, String legsStr);

        void scannerDataEnd();
    }

    public void reqScannerParameters(IScannerHandler handler) {
        m_scannerHandler = handler;
        m_client.reqScannerParameters();
        sendEOM();
    }

    public void reqScannerSubscription(ScannerSubscription sub, IScannerHandler handler) {
        int reqId = m_reqId.getAndIncrement();

        m_scannerMap.put(reqId, handler);
        ArrayList<TagValue> scannerSubscriptionOptions = new ArrayList<>();
        m_client.reqScannerSubscription(reqId, sub, scannerSubscriptionOptions);
        sendEOM();
    }

    public void cancelScannerSubscription(IScannerHandler handler) {
        Integer reqId = getAndRemoveKey(m_scannerMap, handler);
        if (reqId != null) {
            m_client.cancelScannerSubscription(reqId);
            sendEOM();
        }
    }

    @Override
    public void scannerParameters(String xml) {
        m_scannerHandler.scannerParameters(xml);
        recEOM();
    }

    @Override
    public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark, String projection, String legsStr) {
        IScannerHandler handler = m_scannerMap.get(reqId);
        if (handler != null) {
            handler.scannerData(rank, contractDetails, legsStr);
        }
        recEOM();
    }

    @Override
    public void scannerDataEnd(int reqId) {
        IScannerHandler handler = m_scannerMap.get(reqId);
        if (handler != null) {
            handler.scannerDataEnd();
        }
        recEOM();
    }

    // ----------------------------------------- Historical data handling ----------------------------------------
    public interface IHistoricalDataHandler {

        void historicalData(Bar bar, boolean hasGaps);

        void historicalDataEnd();
    }

    /**
     * @param contract    * @param endDateTime format is YYYYMMDD HH:MM:SS [TMZ]
     * @param endDateTime
     * @param duration    is number of durationUnits
     * @param handler
     */
    public void reqHistoricalData(Contract contract, String endDateTime, int duration, DurationUnit durationUnit, BarSize barSize, WhatToShow whatToShow, boolean rthOnly, IHistoricalDataHandler handler) {

        int reqId = m_reqId.getAndIncrement();
        m_symReqMapH.put(reqId, contract.symbol());
        m_historicalDataMap.put(reqId, handler);
        String durationStr = duration + " " + durationUnit.toString().charAt(0);
        pr(endDateTime);
        pr(durationStr);
        pr(durationUnit.toString());
        pr(barSize.toString());
        pr(whatToShow.toString());
        m_client.reqHistoricalData(reqId, contract, endDateTime, durationStr, barSize.toString(), whatToShow.toString(), rthOnly ? 1 : 0, 2, Collections.<TagValue>emptyList());
        sendEOM();
    }

    public void reqHistoricalDataUSHK(HistoricalHandler hh, int reqId, Contract contract, String endDateTime, int duration,
                                      DurationUnit durationUnit, BarSize barSize, WhatToShow whatToShow, boolean rthOnly) {

        ChinaMain.globalRequestMap.put(reqId, new Request(contract, hh));

        //pr(" getting historical data simple US HK");
        //pr(" contract " + contract);
        //pr(" req id is" + reqId);
        String durationStr = duration + " " + durationUnit.toString().charAt(0);
        m_client.reqHistoricalData(reqId, contract, endDateTime, durationStr,
                barSize.toString(), whatToShow.toString(), rthOnly ? 1 : 0, 2, Collections.<TagValue>emptyList());
    }

    public void reqHistoricalDataSimple(int reqId, HistoricalHandler hh, Contract contract, String endDateTime, int duration,
                                        DurationUnit durationUnit, BarSize barSize, WhatToShow whatToShow, boolean rthOnly) {

        pr(" getting historical data simple ");
        pr(" contract " + contract);


        pr(str("stock reqid ", contract.symbol(), reqId));
        ChinaMain.globalRequestMap.put(reqId, new Request(contract, hh));

        pr(" req id is" + reqId);
        String durationStr = duration + " " + durationUnit.toString().charAt(0);
        m_client.reqHistoricalData(reqId, contract, endDateTime, durationStr,
                barSize.toString(), whatToShow.toString(), rthOnly ? 1 : 0, 2, Collections.<TagValue>emptyList());
    }

    public void cancelHistoricalData(IHistoricalDataHandler handler) {
        Integer reqId = getAndRemoveKey(m_historicalDataMap, handler);
        if (reqId != null) {
            m_client.cancelHistoricalData(reqId);
            sendEOM();
        }
    }
    // this method returns the data by reqID. Calls handler.historicalData which fills the table

    @Override
    public void historicalData(int reqId, String date, double open, double high, double low,
                               double close, int volume, int count, double wap, boolean hasGaps) {

        //pr(str("historical data in apicontroller/reqid / date / close ", reqId, date, close));


        if (ChinaMain.globalRequestMap.containsKey(reqId)) {
            Request r = ChinaMain.globalRequestMap.get(reqId);
            String symb = utility.Utility.ibContractToSymbol(r.getContract());

            if (r.getCustomFunctionNeeded()) {
                //pr(" date open volume" + date + " " + open + " " + volume);
                r.getDataConsumer().apply(r.getContract(), date, open, high, low, close, volume);
            } else {
                HistoricalHandler hh = (HistoricalHandler) r.getHandler();
                if (!date.startsWith("finished")) {
                    try {
                        hh.handleHist(symb, date, open, high, low, close);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else if (date.toUpperCase().startsWith("ERROR")) {
                    hh.actionUponFinish(symb);
                    throw new IllegalStateException(" error found ");
                } else {
                    //try this in historicalDataEnd
                    hh.actionUponFinish(symb);
                }
            }
        }

        //pr("successful connecting to historical data");
        //pr(" req ID " + reqId);
//        if (reqId == 10000) {
//            MorningTask.handleHistoricalData(date, close);
//        }
//        if (reqId == 20000) {
//            pr(" handling sgx data ");
//            ChinaData.handleSGX50HistData(date, open, high, low, close, volume);
//            return;
//        }
//        if (reqId == 30000) {
//            pr(" handling sgx data for XUTrader");
//            XUTrader.handleSGX50HistData(date, open, high, low, close, volume);
//            return;
//        }
//        if (reqId == 40000) {
//            ChinaPosition.handleSGX50HistData(date, open, high, low, close, volume);
//            return;
//        }
//        if (reqId == 50000) {
//            ChinaData.handleSGXDataToday(date, open, high, low, close, volume);
//            return;
//        }
//        if (reqId >= 60000 && reqId < 70000) {
//            if (!date.startsWith("finished")) {
//                //pr(" req id " + reqId + " stock "+ USStocks.idStockMap.get(reqId));
//                HistUSStocks.USALLYtd.get(HistUSStocks.idStockMap.get(reqId))
//                        .put(LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd")),
//                                new SimpleBar(open, high, low, close));
//            } else {
//                HistUSStocks.stocksProcessedYtd++;
//                HistUSStocks.sm.release(1);
//                pr(" current permit after done " + HistUSStocks.sm.availablePermits());
//                HistUSStocks.computeYtd(HistUSStocks.idStockMap.get(reqId));
//                HistUSStocks.refreshYtd();
//            }
//            return;
//        }
//
//        if (reqId >= 70000) {
//            if (!date.startsWith("finished")) {
//                //pr(" req id " + reqId + " stock " + HistHKStocks.idStockMap.get(reqId));
//
//                HistHKStocks.hkYtdAll.get(HistHKStocks.idStockMap.get(reqId))
//                        .put(LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd")),
//                                new SimpleBar(open, high, low, close));
//                if (HistHKStocks.idStockMap.get(reqId).equals("700")) {
//                    //MorningTask.clearFile(HistHKStocks.usTestOutput);
//                    MorningTask.simpleWriteToFile(ChinaStockHelper.getStrTabbed(date, open, high, low, close, volume),
//                            true, HistHKStocks.usTestOutput);
//                }
//            } else {
//                HistHKStocks.stocksProcessedYtd++;
//                HistHKStocks.sm.release(1);
//                pr(" current permit after done " + HistHKStocks.sm.availablePermits());
//                HistHKStocks.computeYtd(HistHKStocks.idStockMap.get(reqId));
//                HistHKStocks.refreshYtd();
//            }
//            return;
//        }
/*        long start = System.nanoTime();
        Date dt;
        boolean isNumericalTicker = true;
        int symb = 0;
        Bar bar;
        IHistoricalDataHandler handler;
        //pr("response received");
        //pr( "date "+date);

        if (date.startsWith("finished")) {
            //pr("finished");
        } else {
            try {
                dt = new Date(Long.parseLong(date) * 1000);
            } catch (NumberFormatException x) {
                pr(" dt cannot be parsed " + date);
            }
            //pr(" date"+(new Date(Long.parseLong(date)*1000))+"close "+close);
            //dt.toInstant().
        }

        if (m_historicalDataMap.containsKey(reqId)) {
            handler = m_historicalDataMap.get(reqId);
            pr("ID found ");
        } else {
            handler = null;
        }

        try {
            symb = Integer.parseInt(m_symReqMapH.get(reqId));
            pr(" symb " + symb);
        } catch (NumberFormatException e) {
            isNumericalTicker = false;
        }

        if (handler != null) {
            if (date.startsWith("finished")) {
                handler.historicalDataEnd();
            } else {
                long longDate;
                if (date.length() == 8) {
//                           int year = Integer.parseInt( date.substring( 0, 4) );
//                           int month = Integer.parseInt( date.substring( 4, 6) );
//                           int day = Integer.parseInt( date.substring( 6) );

                    Date dt2 = new Date(Long.parseLong(date) * 1000);
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(dt2);
                    longDate = cal.getTimeInMillis() / 1000L;
                    //LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH));
                    //ocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

                    //longDate = new Date( year - 1900, month - 1, day).getTime() / 1000;
                } else {
                    longDate = Long.parseLong(date);
                }

                if (isNumericalTicker) {
                    bar = new Bar(symb, longDate, high, low, open, close, wap, volume, count);
                } else {
                    bar = new Bar(longDate, high, low, open, close, wap, volume, count);
                }
                pr("Bar is " + bar);
                handler.historicalData(bar, hasGaps);
            }
        }*/

        recEOM();
    }

    @Override
    public void historicalDataEnd(int reqId) {
        pr(" historical data ending for " + reqId);
        if (ChinaMain.globalRequestMap.containsKey(reqId)) {
            Request r = ChinaMain.globalRequestMap.get(reqId);
            String symb = ibContractToSymbol(r.getContract());
            if (r.getCustomFunctionNeeded()) {
                pr(" custom handling needed ");
            } else {
                HistoricalHandler hh = (HistoricalHandler) r.getHandler();
                hh.actionUponFinish(symb);
            }
        }
    }

    //----------------------------------------- Real-time bars --------------------------------------
    public interface IRealTimeBarHandler {

        void realtimeBar(Bar bar); // time is in seconds since epoch
    }

    public void reqRealTimeBars(Contract contract, WhatToShow whatToShow, boolean rthOnly, IRealTimeBarHandler handler) {
        //int reqId = m_reqId++;

        int reqId = m_reqId.getAndIncrement();
        m_realTimeBarMap.put(reqId, handler);
        ArrayList<TagValue> realTimeBarsOptions = new ArrayList<>();
        m_client.reqRealTimeBars(reqId, contract, 0, whatToShow.toString(), rthOnly, realTimeBarsOptions);
        //add

        sendEOM();
    }

    public void cancelRealtimeBars(IRealTimeBarHandler handler) {
        Integer reqId = getAndRemoveKey(m_realTimeBarMap, handler);
        if (reqId != null) {
            m_client.cancelRealTimeBars(reqId);
            sendEOM();
        }
    }

    // this method receives the results
    @Override
    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {
        IRealTimeBarHandler handler = m_realTimeBarMap.get(reqId);
        if (handler != null) {
            Bar bar = new Bar(time, high, low, open, close, wap, volume, count);
            handler.realtimeBar(bar);
        }
        recEOM();

    }

    // ----------------------------------------- Fundamentals handling ----------------------------------------
    public interface IFundamentalsHandler {

        void fundamentals(String str);
    }

    public void reqFundamentals(Contract contract, FundamentalType reportType, IFundamentalsHandler handler) {
        //int reqId = m_reqId++;

        int reqId = m_reqId.getAndIncrement();
        m_fundMap.put(reqId, handler);
        m_client.reqFundamentalData(reqId, contract, reportType.getApiString());
        sendEOM();
    }

    @Override
    public void fundamentalData(int reqId, String data) {
        IFundamentalsHandler handler = m_fundMap.get(reqId);
        if (handler != null) {
            handler.fundamentals(data);
        }
        recEOM();

    }

    // ---------------------------------------- Time handling ----------------------------------------
    public interface ITimeHandler {

        void currentTime(long time);

    }

    public void reqCurrentTime(ITimeHandler handler) {
        m_timeHandler = handler;
        m_client.reqCurrentTime();
        sendEOM();
    }

    @Override
    public void currentTime(long time) {
        Date d = new Date(time * 1000);
        LocalTime t = LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()).toLocalTime();
        //pr(" time is " + (new Date(time * 1000)));
        ChinaMain.updateTWSTime("TWS: " + Utility.timeToString(t));
        //ChinaMain.updateTWSTime(new Date(time * 1000).toString());
        //m_timeHandler.currentTime(time);
        recEOM();

    }

    // ---------------------------------------- Bulletins handling ----------------------------------------
    public interface IBulletinHandler {

        void bulletin(int msgId, NewsType newsType, String message, String exchange);
    }

    public void reqBulletins(boolean allMessages, IBulletinHandler handler) {
        m_bulletinHandler = handler;
        m_client.reqNewsBulletins(allMessages);
        sendEOM();
    }

    public void cancelBulletins() {
        m_client.cancelNewsBulletins();
    }

    @Override
    public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
        m_bulletinHandler.bulletin(msgId, NewsType.get(msgType), message, origExchange);
        recEOM();
    }

    @Override
    public void verifyMessageAPI(String apiData) {
    }

    @Override
    public void verifyCompleted(boolean isSuccessful, String errorText) {
    }

    @Override
    public void displayGroupList(int reqId, String groups) {
    }

    @Override
    public void displayGroupUpdated(int reqId, String contractInfo) {
    }

    // ---------------------------------------- other methods ----------------------------------------

    /**
     * Not supported in ApiController.
     */
//	@Override public void deltaNeutralValidation(int reqId, UnderComp underComp) {
//		show( "RECEIVED DN VALIDATION");
//		recEOM();
//	}
    private void sendEOM() {
        m_outLogger.log("\n");
    }

    private void recEOM() {
        // = LocalTime.now();
        //pr( " rec EOM BEFORE "+ LocalTime.now());

        m_inLogger.log("\n");

        //pr( " rec EOM AFTER "+ LocalTime.now());
        //pr( " length in recEOM "+(System.currentTimeMillis()-start));
    }

    public void show(String string) {
        m_connectionHandler.show(string);
    }

    private static <K, V> K getAndRemoveKey(HashMap<K, V> map, V value) {
        for (Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue() == value) {
                map.remove(entry.getKey());
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Obsolete, never called.
     *
     * @param str
     */
    @Override
    public void error(String str) {
        throw new RuntimeException();
    }

    @Override
    public void securityDefinitionOptionalParameter(int reqId, String exchange, int underlyingConId, String tradingClass,
                                                    String multiplier, Set<String> expirations, Set<Double> strikes) {
//		ISecDefOptParamsReqHandler handler = m_secDefOptParamsReqMap.get( reqId);
//
//		if (handler != null) {
//			handler.securityDefinitionOptionalParameter(exchange, underlyingConId, tradingClass, multiplier, expirations, strikes);
//		}
    }

    @Override
    public void securityDefinitionOptionalParameterEnd(int reqId) {
//        ISecDefOptParamsReqHandler handler = m_secDefOptParamsReqMap.get( reqId);
//		if (handler != null) {
//			handler.securityDefinitionOptionalParameterEnd(reqId);
//		}
    }

    @Override
    public void softDollarTiers(int reqId, SoftDollarTier[] tiers) {
//        ISoftDollarTiersReqHandler handler = m_softDollarTiersReqMap.get(reqId);
//
//		if (handler != null) {
//			handler.softDollarTiers(tiers);
//		}
    }
}
