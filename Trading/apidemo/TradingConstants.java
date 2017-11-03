package apidemo;

public final class TradingConstants {
    public static final String GLOBALA50EXPIRY = "20171129";
    public static final String GLOBALPATH = "C:\\Users\\" + System.getProperty("user.name") + "\\Desktop\\Trading\\";
    public static final String tdxPath = (System.getProperty("user.name").equals("Luke Shi"))
            ? "G:\\export_1m\\" : "J:\\TDX\\T0002\\export_1m\\";

    private TradingConstants() {
        throw new UnsupportedOperationException(" all constants ");
    }



}
