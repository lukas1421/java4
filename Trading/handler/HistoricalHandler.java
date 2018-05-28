package handler;

import static utility.Utility.pr;

public interface HistoricalHandler extends GeneralHandler {
    void handleHist(String name, String date, double open,
                    double high, double low, double close);

    void actionUponFinish(String name);

    class DefaultHistHandler implements HistoricalHandler {
        @Override
        public void handleHist(String name, String date, double open, double high, double low, double close) {
            pr("hist ", name, date, open, high, low, close);
        }

        @Override
        public void actionUponFinish(String name) {
        }
    }
}
