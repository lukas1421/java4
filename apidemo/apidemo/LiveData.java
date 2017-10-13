package apidemo;

import client.ContractDetails;
import client.TickType;
import client.Types.MktDataType;
import controller.Bar;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import static java.util.stream.Collectors.toList;
import javax.swing.JScrollPane;
import javax.swing.table.AbstractTableModel;
import controller.ApiController.ITopMktDataHandler1;
import controller.ApiController.IInternalHandler1;
import java.io.File;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.StandardCopyOption;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JButton;

import javax.swing.JPanel;
import javax.swing.JTable;

final class LiveData extends JPanel implements ITopMktDataHandler1, IInternalHandler1 {

    static volatile ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>> map1 = new ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>>();
    static volatile HashMap<Integer, String> nameMap = new HashMap<Integer, String>();
    static volatile ConcurrentHashMap<Integer, Integer> sizeMap = new ConcurrentHashMap<>();
    static ConcurrentHashMap<Integer, ConcurrentHashMap<?, ?>> saveMap = new ConcurrentHashMap<>();
    BarModel_Livedata m_model = new BarModel_Livedata();

    ArrayList<Bar> m_rows = new ArrayList<>();
    private final static Calendar CAL = Calendar.getInstance();
    ArrayList<Integer> symbolNames = new ArrayList<>();
    ArrayList<LocalTime> tradeTime = new ArrayList<>();
    ExecutorService es = Executors.newCachedThreadPool();

    static File source = new File(ChinaMain.GLOBALPATH + "HKSS.ser");
    static File backup = new File(ChinaMain.GLOBALPATH + "HKSSBackup.ser");

    public ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>> getMap() {
        System.out.println(" passing map ");
        return LiveData.map1;
    }

    LiveData() {

        try {
            List<Integer> numbers = Files.lines(Paths.get(ChinaMain.GLOBALPATH + "Table2.txt"))
                    .map(Integer::valueOf).peek(System.out::println)
                    .distinct()
                    .collect(toList());

            numbers.forEach((value) -> {
                map1.put(value, new ConcurrentSkipListMap<>());
                sizeMap.put(value, 0);
                nameMap.put(value, "");
                symbolNames.add(value);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        LocalTime lt = LocalTime.of(9, 19);
        while (lt.isBefore(LocalTime.of(16, 1))) {
            if (lt.getHour() == 12 && lt.getMinute() == 1) {
                lt = LocalTime.of(13, 0);
            }
            tradeTime.add(lt);
            lt = lt.plusMinutes(1);
        }

        JTable tab = new JTable(m_model);

        JScrollPane scroll = new JScrollPane(tab) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.width = 1900;
                return d;
            }
        };

        setLayout(new BorderLayout());
        add(scroll, BorderLayout.WEST);
        tab.setAutoCreateRowSorter(true);

        JPanel jp = new JPanel();

        JButton btnSave = new JButton("save");
        JButton btnLoad = new JButton("load");

        jp.add(btnSave);
        jp.add(btnLoad);
        add(jp, BorderLayout.NORTH);

        btnSave.addActionListener(al -> {
            try {
                Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(source))) {
                ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>> map2 = new ConcurrentHashMap<>(map1);
                ConcurrentHashMap<Integer, Integer> sizeMap1 = new ConcurrentHashMap<>(sizeMap);
                saveMap.put(1, map2);
                saveMap.put(2, sizeMap1);
                oos.writeObject(saveMap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        btnLoad.addActionListener(al -> {
            CompletableFuture.runAsync(() -> {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(source))) {
                    saveMap = (ConcurrentHashMap<Integer, ConcurrentHashMap<?, ?>>) ois.readObject();
                    map1 = (ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>>) saveMap.get(1);
                    sizeMap = (ConcurrentHashMap<Integer, Integer>) saveMap.get(2);
                    System.out.println("cheung kong " + map1.get(1));
                } catch (IOException | ClassNotFoundException e2) {
                    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(backup))) {
                        saveMap = (ConcurrentHashMap<Integer, ConcurrentHashMap<?, ?>>) ois.readObject();
                        map1 = (ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>>) saveMap.get(1);
                        sizeMap = (ConcurrentHashMap<Integer, Integer>) saveMap.get(2);
                    } catch (Exception e3) {
                        e3.printStackTrace();
                    }
                }
            }, es).whenComplete((ok, ex) -> System.out.println("loading LIVEDATA done"));
        });
    }

    void init1() throws IOException {
        System.out.println("reading file started");
        List<Integer> numbers;
        numbers = Files.lines(Paths.get(ChinaMain.GLOBALPATH + "Table2.txt"))
                .map(Integer::valueOf)
                .distinct()
                .collect(toList());
        System.out.println(" size of data list is " + numbers.size());
        numbers.forEach((value) -> {
            map1.put(value, new ConcurrentSkipListMap<>());
        });
        System.out.println(" size of map1 in init1 is " + map1.size());
        numbers.forEach((value) -> {
            symbolNames.add(value);
        });
    }

    public void saveSS() {
        try {
            Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("last modified for HKSS is " + new Date(source.lastModified()));
            System.out.println("last modified for HKSSBackup " + new Date(backup.lastModified()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(source))) {
            //ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>> map2 = new ConcurrentHashMap<Integer, ConcurrentSkipListMap<LocalTime, Double>>(map1);
            //ConcurrentHashMap<Integer, Integer> sizeMap1 = new ConcurrentHashMap<>(sizeMap);
            saveMap.put(1, map1);
            saveMap.put(2, sizeMap);
            oos.writeObject(saveMap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void tickPrice(int symbol, TickType tickType, double price, int canAutoExecute) {
        LocalTime lt = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        switch (tickType) {
            case BID:
                break;
            case ASK:
                break;
            case LAST:
                map1.get(symbol).put(lt, price);
                break;
            case CLOSE:
                map1.get(symbol).put(LocalTime.of(9, 19), price);
                break;
        }
    }

    @Override
    public void tickSize(int symbol, TickType tickType, int size) {
        switch (tickType) {
            case BID_SIZE:
                break;
            case ASK_SIZE:
                break;
            case VOLUME:
                sizeMap.put(symbol, size);
                break;
        }
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

    @Override
    public void contractDetails(ContractDetails data) {
        int symbol;
        String name;
        symbol = Integer.parseInt(data.contract().symbol());
        name = data.longName();
        nameMap.put(symbol, name);
    }

    class BarModel_Livedata extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return symbolNames.size();
        }

        @Override
        public int getColumnCount() {
            return tradeTime.size() + 1;
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case 0:
                    return "T";
                default:
                    return tradeTime.get(col - 1).toString();
            }
        }

        @Override
        public Class getColumnClass(int col) {
            switch (col) {
                case 0:
                    return Integer.class;
                default:
                    return Double.class;
            }
        }

        @Override
        public Object getValueAt(int rowIn, int col) {
            int sym = (rowIn <= symbolNames.size()) ? symbolNames.get(rowIn) : 0;
            switch (col) {
                case 0:
                    return sym;
                default:
                    return (map1.containsKey(sym) && map1.get(sym).containsKey(tradeTime.get(col - 1))) ? map1.get(sym).get(tradeTime.get(col - 1)) : 0.0;
            }
        }
    }
}
