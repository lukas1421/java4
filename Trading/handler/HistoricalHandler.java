package handler;

public interface HistoricalHandler extends GeneralHandler {
    void handleHist(String name, String date, double open,
                    double high, double low, double close);
    void actionUponFinish(String name);
}
