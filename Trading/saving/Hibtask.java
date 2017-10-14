package saving;

import apidemo.ChinaData;
import apidemo.ChinaMain;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import utility.Utility;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;

import static apidemo.ChinaStock.symbolNames;
import static java.time.temporal.ChronoUnit.SECONDS;

public class Hibtask {


    public static ConcurrentSkipListMap<LocalTime, ?> unblob(Blob b) {
        try {
            int len = (int) b.length();
            if (len > 1) {
                byte[] buf = b.getBytes(1, len);
                try (ObjectInputStream iin = new ObjectInputStream(new ByteArrayInputStream(buf))) {
                    //saveclass.updateFirstMap(key, (ConcurrentSkipListMap<LocalTime,?>)iin.readObject());
                    //c.accept((ConcurrentSkipListMap<LocalTime,?>)iin.readObject());
                    return ((ConcurrentSkipListMap<LocalTime, ?>) iin.readObject());
                } catch (IOException | ClassNotFoundException io) {
                    System.out.println(" issue is with " + "XU");
                    io.printStackTrace();
                }
            }
        } catch (SQLException sq) {
            sq.printStackTrace();
        }
        return new ConcurrentSkipListMap<>();
    }

    public static void loadHibGen(ChinaSaveInterface2Blob saveclass) {
        LocalTime start = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        SessionFactory sessionF = HibernateUtil.getSessionFactory();
        try (Session session = sessionF.openSession()) {
            symbolNames.forEach((key) -> {
                ChinaSaveInterface2Blob cs = session.load(saveclass.getClass(), key);
                Blob blob1 = cs.getFirstBlob();
                Blob blob2 = cs.getSecondBlob();
                saveclass.updateFirstMap(key, unblob(blob1));
                saveclass.updateSecondMap(key, unblob(blob2));
            });
        }
    }

    public static void loadHibGenPrice() {
        LocalTime start = LocalTime.now().truncatedTo(ChronoUnit.SECONDS);
        CompletableFuture.runAsync(() -> {
            loadHibGen(ChinaSave.getInstance());
            System.out.println(" load finished " + " size is " + ChinaData.priceMapBar.size());
        }).thenAccept(
                v -> {
                    ChinaMain.updateSystemNotif(Utility.getStr(" LOAD HIB T DONE ",
                            LocalTime.now().truncatedTo(ChronoUnit.SECONDS), " Taken: ",
                            SECONDS.between(start, LocalTime.now().truncatedTo(ChronoUnit.SECONDS))
                    ));
                }
        );
    }

    public static void hibernateMorningTask() {
        CompletableFuture.runAsync(() -> {
            SessionFactory sessionF = HibernateUtil.getSessionFactory();
            try (Session session = sessionF.openSession()) {
                try {
                    session.getTransaction().begin();
                    session.createQuery("DELETE from saving.ChinaSaveY2").executeUpdate();
                    session.createQuery("insert into saving.ChinaSaveY2(stockName,dayPriceMapBlob,volMapBlob) select stockName,dayPriceMapBlob,volMapBlob from saving.ChinaSaveYest").executeUpdate();
                    session.createQuery("DELETE from saving.ChinaSaveYest").executeUpdate();
                    session.createQuery("insert into saving.ChinaSaveYest(stockName,dayPriceMapBlob,volMapBlob) select stockName,dayPriceMapBlob,volMapBlob from saving.ChinaSave").executeUpdate();
                } catch (Exception ex) {
                    session.getTransaction().rollback();
                    ex.printStackTrace();
                    session.close();
                }
                session.getTransaction().commit();
            }
        }).thenAccept(v -> {
            ChinaMain.updateSystemNotif(Utility.getStr(" HIB Today -> YTD DONE ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
        }
        ).thenAccept(v -> {
            CompletableFuture.runAsync(() -> {
                loadHibGenPrice();
            });
            CompletableFuture.runAsync(() -> {
                ChinaData.loadHibernateYesterday();
            });
        }).thenAccept(v -> {
            ChinaMain.updateSystemNotif(Utility.getStr(" Loading done ", LocalTime.now().truncatedTo(ChronoUnit.SECONDS)));
        });
    }

    public static void closeHibSessionFactory() {
        HibernateUtil.close();
    }
}
