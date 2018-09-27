package auxiliary;

import apidemo.LiveData;

import java.time.LocalTime;

public class AnaCompute implements Runnable {

    private Analysis ana;
    private LiveData lv;
    //LocalTime 
    private static LocalTime timepoint2 = LocalTime.now();

    public AnaCompute() {
    }

    public AnaCompute(Analysis ana, LiveData lv) {
        this.ana = ana;
        this.lv = lv;

    }

    void setAnaCompute(Analysis ana, LiveData lv) {
        //System.out.println("resetting anaCOmpute");
        // this.ana = ana;
        //  this.lv = lv;
    }

    @Override
    public void run() {
        while (true) {
            //System.out.println ("current time in run is " + LocalTime.now().toString());
            if (LocalTime.now().isAfter(timepoint2)) {
                Analysis.compute(LiveData.map1);
                //  Backtesting.computeYtd(LiveData.map1);
                //  System.out.println (" in AnaCompute run method ");
                //  System.out.println(" active thread count is " + Thread.activeCount());
                //  System.out.println("current thread name is " +  Thread.currentThread().getSymbol());
                timepoint2 = LocalTime.now().plusSeconds(20);
            }

            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            //Thread.sleep(1000);
        } //e.printStackTrace();
    }
}
