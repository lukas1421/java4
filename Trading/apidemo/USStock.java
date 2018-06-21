package apidemo;

import auxiliary.SimpleBar;
import client.Contract;
import client.TickType;
import client.Types;
import controller.ApiController;
import graph.GraphBarGen;
import handler.HistoricalHandler;
import handler.LiveHandler;

import javax.swing.*;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static utility.Utility.*;

public class USStock extends JPanel implements LiveHandler, HistoricalHandler {


    private ApiController apcon;
    private GraphBarGen g = new GraphBarGen();
    private Types.BarSize barsize = Types.BarSize._15_mins;

    private static volatile ConcurrentHashMap<String, ConcurrentSkipListMap<LocalDateTime, SimpleBar>>
            usPriceMapBar = new ConcurrentHashMap<>();

    private static volatile NavigableMap<LocalDateTime, SimpleBar> vixMap = new ConcurrentSkipListMap<>();

    USStock(ApiController ap) {
        usPriceMapBar.put("VIX", new ConcurrentSkipListMap<>());
        apcon = ap;
        JButton getLiveButton = new JButton("Live");
        getLiveButton.addActionListener(l -> {
            pr(" requesting vix live ");
            ap.req1ContractLive(getVixContract(), this, false);
        });

        JButton getHistButton = new JButton(" Hist ");
        getHistButton.addActionListener(l -> {
            pr(" requesting hist ");
            ap.req1ContractHistory(getVixContract(), barsize, this);
        });

        JScrollPane scroll = new JScrollPane(g) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 300;
                d.width = 1500;
                return d;
            }
        };

        JButton refreshButton = new JButton(" Refresh ");
        refreshButton.addActionListener(l -> {
            g.repaint();
            this.repaint();
        });

        JPanel controlPanel = new JPanel();
        controlPanel.add(getLiveButton);
        controlPanel.add(getHistButton);
        controlPanel.add(refreshButton);

        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.SOUTH);
    }


    private static Contract getVixContract() {
        Contract ct = new Contract();
        ct.symbol("VIX");
        ct.exchange("CFE");
        ct.currency("USD");
        ct.lastTradeDateOrContractMonth("20180718");
        ct.includeExpired(true);
        ct.tradingClass("VX");
        ct.secType(Types.SecType.FUT);
//        System.out.println(" get expired fut contract " + " expiry date " + TradingConstants.A50_LAST_EXPIRY + " "
//                + ct.lastTradeDateOrContractMonth());
        return ct;
    }


    @Override
    public void handleHist(String name, String date, double open, double high, double low, double close) {
        pr("US hist ", name, date, open, close);

        LocalDate currDate = LocalDate.now();
        if (!date.startsWith("finished")) {
            Date dt = new Date(Long.parseLong(date) * 1000);
            Calendar cal = Calendar.getInstance();
            cal.setTime(dt);
            LocalDate ld = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            LocalTime lt = LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            LocalDateTime ldt = LocalDateTime.of(ld, lt);
//            if (!ld.equals(currDate) && lt.equals(LocalTime.of(14, 59))) {
//                futPrevCloseMap.put(FutType.get(name), close);
//            }

            int daysToGoBack = currDate.getDayOfWeek().equals(DayOfWeek.MONDAY) ? 4 : 2;
            if (ldt.toLocalDate().isAfter(currDate.minusDays(daysToGoBack))) {
                usPriceMapBar.get(name).put(ldt, new SimpleBar(open, high, low, close));
                vixMap.put(ldt, new SimpleBar(open, high, low, close));
            }
        } else {
            pr(str(date, open, high, low, close));
        }

    }

    @Override
    public void actionUponFinish(String name) {
        g.setNavigableMap(vixMap);
        pr(" finish ", name);

    }

    @Override
    public void handlePrice(TickType tt, String name, double price, LocalDateTime t) {
        //vixMap
        LocalDateTime ldt = roundToXLdt(t, 15);

        if (tt == TickType.LAST) {
            if (vixMap.containsKey(ldt)) {
                vixMap.get(ldt).add(price);
            } else {
                vixMap.put(ldt, new SimpleBar(price));
            }
        }

        pr("US ", tt, name, price, t);
    }

    @Override
    public void handleVol(String name, double vol, LocalDateTime t) {
        pr(" US vol ", name, vol, t);
    }
}
