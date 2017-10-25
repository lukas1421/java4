package historical;

import apidemo.ChinaMain;
import auxiliary.SimpleBar;
import graph.GraphBarTemporal;
import utility.SharpeUtility;
import utility.Utility;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.Buffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HistChinaStocks extends JPanel {


    static JPanel graphPanel;
    static BarModel_China model;
    static JTable tab;

    public static final LocalDate MONDAY_OF_WEEK = Utility.getMondayOfWeek(LocalDateTime.now());

    public static Map<String, String> nameMap = new HashMap<>();


    GraphBarTemporal<LocalDate> graphYtd = new GraphBarTemporal<>();
    GraphBarTemporal<LocalDateTime> graphWtd = new GraphBarTemporal<>();
    File chinaInput = new File(ChinaMain.GLOBALPATH + "ChinaAll.txt");

    static List<String> stockList = new LinkedList<>();
    static Map<String, NavigableMap<LocalDate, SimpleBar>> chinaYtd = new HashMap<>();
    static Map<String, NavigableMap<LocalDateTime, SimpleBar>> chinaWtd = new HashMap<>();

    static Map<String, ChinaResult> ytdResult = new HashMap<>();
    static Map<String, ChinaResult> wtdResult = new HashMap<>();

    static String tdxDayPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export\\" : "J:\\TDX\\T0002\\export\\";

    static String tdxMinutePath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export_1m\\" : "J:\\TDX\\T0002\\export_1m\\";

    static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    int modelRow;
    int indexRow;
    String selectedStock = "";
    TableRowSorter<BarModel_China> sorter;

    public HistChinaStocks() {
        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(chinaInput),"GBK"))) {
            while ((line = reader.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));

                if(!al1.get(0).equals("sh204001") && (al1.get(0).startsWith("sh") || al1.get(0).startsWith("sz"))) {
                    chinaYtd.put(al1.get(0), new TreeMap<>());
                    chinaWtd.put(al1.get(0), new TreeMap<>());
                    stockList.add(al1.get(0));
                    nameMap.put(al1.get(0), al1.get(1));
                    ytdResult.put(al1.get(0), new ChinaResult());
                    wtdResult.put(al1.get(0),new ChinaResult());
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


        graphPanel = new JPanel();

        model = new BarModel_China();

        tab = new JTable(model) {

            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int indexRow, int indexCol) {
                try {
                    Component comp = super.prepareRenderer(renderer, indexRow, indexCol);
                    if (isCellSelected(indexRow, indexCol)) {
                        modelRow = this.convertRowIndexToModel(indexRow);
                        selectedStock = stockList.get(modelRow);
                        //System.out.println(" selected stock in monitor is " + selectedStock);
                        comp.setBackground(Color.GREEN);
                        graphYtd.fillInGraphChinaGen(selectedStock, chinaYtd);
                        graphWtd.fillInGraphChinaGen(selectedStock,chinaWtd);
                        //graphWtd.fillInGraphHKGen(selectedStock, hkWtdAll);
                        graphPanel.repaint();
                    } else {
                        comp.setBackground((indexRow % 2 == 0) ? Color.lightGray : Color.white);
                    }
                    return comp;

                } catch (Exception x) {
                    x.printStackTrace();
                }
                return null;
            }
        };

        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    SwingUtilities.invokeLater(() -> {
                        model.fireTableDataChanged();
                    });
                }
            }
        });


        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 1900;
                return d;
            }
        };

        graphPanel.setLayout(new GridLayout(2, 1));

        JScrollPane jp1 = new JScrollPane(graphYtd){
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                d.width = 1900;
                return d;
            }
        };

        JScrollPane jp2 = new JScrollPane(graphWtd) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 250;
                d.width = 1900;
                return d;
            }
        };



        graphPanel.add(jp1);
        graphPanel.add(jp2);

        JPanel controlPanel = new JPanel();
        JButton refreshButton = new JButton("Refresh");
        JButton ytdButton = new JButton("ytd");
        JButton wtdButton = new JButton("wtd");

        refreshButton.addActionListener(al->{
            refreshAll();
        });

        ytdButton.addActionListener(al->{

            CompletableFuture.runAsync(()->{
                computeYtd();
            });
        });

        wtdButton.addActionListener(al->{
            CompletableFuture.runAsync(()->{
                computeWtd();
            });


        });


        controlPanel.setLayout(new FlowLayout());
        controlPanel.add(refreshButton);
        controlPanel.add(ytdButton);
        controlPanel.add(wtdButton);

        this.setLayout(new BorderLayout());
        this.add(controlPanel, BorderLayout.NORTH);
        this.add(scroll, BorderLayout.CENTER);
        this.add(graphPanel, BorderLayout.SOUTH);

        tab.setAutoCreateRowSorter(true);
        sorter = (TableRowSorter<BarModel_China>) tab.getRowSorter();
    }


    static void refreshAll() {
        SwingUtilities.invokeLater(()->{
            model.fireTableDataChanged();
            graphPanel.repaint();
        });

    }



    //wtd
    static void computeYtd() {
        for(String s:stockList) {
            System.out.println(" processing ytd for " + s);
            String tickerFull = s.substring(0, 2).toUpperCase() + "#" + s.substring(2) + ".txt";
            String line;
            double totalSize = 0.0;

            if (s.substring(0, 2).toUpperCase().equals("SH") || s.substring(0, 2).toUpperCase().equals("SZ")) {
                try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(tdxDayPath + tickerFull)))) {
                    while ((line = reader1.readLine()) != null) {
                        List<String> al1 = Arrays.asList(line.split("\t"));
                        if(al1.get(0).startsWith("2017")) {
                            LocalDate d = LocalDate.parse(al1.get(0), DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                            //System.out.println(" date is " + d);
                            if(chinaYtd.containsKey(s)) {
                                chinaYtd.get(s).put(d, new SimpleBar(Double.parseDouble(al1.get(1)), Double.parseDouble(al1.get(2))
                                        , Double.parseDouble(al1.get(3)), Double.parseDouble(al1.get(4))));
                            } else {
                                throw new IllegalStateException(" cannot find stock " + s);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            //do computation
            System.out.println(" data is " + s + " " + chinaYtd.get(tickerFull));
            if(chinaYtd.containsKey(s) && chinaYtd.get(s).size()>1) {
                NavigableMap<LocalDate, Double> ret = SharpeUtility.getReturnSeries(chinaYtd.get(s),
                        LocalDate.of(2016, Month.DECEMBER, 31));
                double mean = SharpeUtility.getMean(ret);
                double sdDay = SharpeUtility.getSD(ret);
                double sr = SharpeUtility.getSharpe(ret, 252);
                double perc = SharpeUtility.getPercentile(chinaYtd.get(s));
                ytdResult.get(s).fillResult(mean, sdDay, sr, perc);
                System.out.println(Utility.getStrTabbed(" stock mean sd sr perc size firstEntry"
                        , s, mean, sdDay, sr, perc, ret.size(), ret.firstEntry(), ret.lastEntry()));
            } else {
                System.out.println(" name is less than 1 " + tickerFull);
            }
        }

    }

    /////////////////// wtd

    static void computeWtd() {


        for(String s:stockList) {
            System.out.println(" processing wtd for " + s);
            String tickerFull = s.substring(0, 2).toUpperCase() + "#" + s.substring(2) + ".txt";
            String line;
            //boolean found = false;


            if (s.substring(0, 2).toUpperCase().equals("SH") || s.substring(0, 2).toUpperCase().equals("SZ")) {
                try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(tdxMinutePath + tickerFull)))) {
                    while ((line = reader1.readLine()) != null) {
                        List<String> al1 = Arrays.asList(line.split("\t"));
                        if(al1.get(0).startsWith("2017/10") && LocalDate.parse(al1.get(0),DATE_PATTERN).isAfter(MONDAY_OF_WEEK.minusDays(1))) {
                            //found = true;
                            LocalDate d = LocalDate.parse(al1.get(0), DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                            LocalTime lt = roundTo5(stringToLocalTime(al1.get(1)));

                            LocalDateTime ldt = LocalDateTime.of(d, lt);

                            if(chinaWtd.containsKey(s)) {
                                if(!chinaWtd.get(s).containsKey(ldt)) {
                                    chinaWtd.get(s).put(LocalDateTime.of(d, lt)
                                            , new SimpleBar(Double.parseDouble(al1.get(2)), Double.parseDouble(al1.get(3))
                                                    , Double.parseDouble(al1.get(4)), Double.parseDouble(al1.get(5))));
                                } else {
                                    chinaWtd.get(s).get(ldt).updateBar(Double.parseDouble(al1.get(2)), Double.parseDouble(al1.get(3))
                                            , Double.parseDouble(al1.get(4)), Double.parseDouble(al1.get(5)));
                                }
                            } else {
                                throw new IllegalStateException(" cannot find stock " + s);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            //do computation
            System.out.println(" data is " + s + " " + chinaWtd.get(tickerFull));
            if(chinaWtd.containsKey(s) && chinaWtd.get(s).size()>1) {
                NavigableMap<LocalDateTime, Double> ret = SharpeUtility.getReturnSeries(chinaWtd.get(s),
                        LocalDateTime.of(MONDAY_OF_WEEK.minusDays(1), LocalTime.MIN));
                double mean = SharpeUtility.getMean(ret);
                double sdDay = SharpeUtility.getSD(ret)*Math.sqrt(240);
                double sr = SharpeUtility.getSharpe(ret, 240);
                double perc = SharpeUtility.getPercentile(chinaWtd.get(s));
                wtdResult.get(s).fillResult(mean, sdDay, sr, perc);
                System.out.println(Utility.getStrTabbed(" stock mean sd sr perc size firstEntry"
                        , s, mean, sdDay, sr, perc, ret.size(), ret.firstEntry(), ret.lastEntry()));
            } else {
                System.out.println(" name is less than 1 " + tickerFull);
            }
        }

    }


    static LocalTime stringToLocalTime(String s) {
        if(s.length() != 4) {
            System.out.println(" length is not equal to 4") ;
            throw new IllegalArgumentException(" length is not equal to 4 ");
        } else {
            if(s.startsWith("0")) {
                return LocalTime.of(Integer.parseInt(s.substring(1,2)),Integer.parseInt(s.substring(2)));
            } else {
                return LocalTime.of(Integer.parseInt(s.substring(0,2)),Integer.parseInt(s.substring(2)));
            }
        }
    }

    static LocalTime roundTo5(LocalTime t) {
        return (t.getMinute()%5==0)? t:t.plusMinutes(5-t.getMinute()%5);
    }


    public static void main(String[] args) {
        JFrame jf = new JFrame();
        jf.setSize(new Dimension(1500, 1500));
        jf.setLayout(new FlowLayout());
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        HistChinaStocks hc = new HistChinaStocks();
        jf.add(hc);
        jf.setVisible(true);
    }

    public class ChinaResult {

        double meanRtn;
        double sd;
        double sr;
        double perc;

        public ChinaResult() {
            meanRtn = 0.0;
            sd = 0.0;
            sr = 0.0;
            perc = 0.0;
        }

        public ChinaResult(double m, double s, double r, double p) {
            meanRtn = Math.round(1000d * m) / 10d;
            sd = Math.round(1000d * s * Math.sqrt(252)) / 10d;
            sr = Math.round(100d * r) / 100d;
            perc = p;
        }

        public void fillResult(double m, double s, double r, double p) {
            meanRtn = Math.round(1000d * m) / 10d;
            sd = Math.round(1000d * s * Math.sqrt(252)) / 10d;
            sr = Math.round(100d * r) / 100d;
            perc = p;
        }


        public double getMeanRtn() {
            return meanRtn;
        }

        public void setMeanRtn(double meanRtn) {
            this.meanRtn = meanRtn;
        }

        public double getSd() {
            return sd;
        }

        public void setSd(double sd) {
            this.sd = sd;
        }

        public double getSr() {
            return sr;
        }

        public void setSr(double sr) {
            this.sr = sr;
        }

        public double getPerc() {
            return perc;
        }

        public void setPerc(double perc) {
            this.perc = perc;
        }



    }


    private class BarModel_China extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return stockList.size();
        }

        @Override
        public int getColumnCount() {
            return 20;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "ticker";
                case 1:
                    return "chn";
                case 2:
                    return "Y Mean";
                case 3:
                    return "Y sd";
                case 4:
                    return "Y sr";
                case 5:
                    return "Y perc";
                case 6:
                    return "Y n";

                case 7:
                    return "W mean";
                case 8:
                    return "W sd";
                case 9:
                    return "W sr";
                case 10:
                    return "W perc";
                case 11:
                    return "W n";


                default:
                    return "";

            }
        }

        @Override
        public Object getValueAt(int row, int col) {
            String name = stockList.get(row);
            switch (col) {
                case 0:
                    return name;
                case 1:
                    return nameMap.getOrDefault(name,"");
                case 2:
                    return ytdResult.get(name).getMeanRtn();
                case 3:
                    return ytdResult.get(name).getSd();
                case 4:
                    return ytdResult.get(name).getSr();
                case 5:
                    return ytdResult.get(name).getPerc();
                case 6:
                    return chinaYtd.get(name).size();
                case 7:
                    return wtdResult.get(name).getMeanRtn();
                case 8:
                    return wtdResult.get(name).getSd();
                case 9:
                    return wtdResult.get(name).getSr();
                case 10:
                    return wtdResult.get(name).getPerc();
                case 11:
                    return chinaWtd.get(name).size();


                default:
                    return null;

            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch(col) {
                case 0:
                    return String.class;
                case 1:
                    return String.class;
                case 6:
                    return Integer.class;
                case 11:
                    return Integer.class;
                default:
                    return Double.class;
            }
        }
    }

}
