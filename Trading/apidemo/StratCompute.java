package apidemo;

import auxiliary.Backtesting;

import java.time.LocalTime;

public class StratCompute implements Runnable {

    private static LocalTime timepoint2 = LocalTime.now();

    @Override
    public void run() {
        while (true) {
            if (LocalTime.now().isAfter(timepoint2)) {
                Backtesting.compute(LiveData.map1);
                timepoint2 = LocalTime.now().plusSeconds(20);
            }
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }
    }
}
