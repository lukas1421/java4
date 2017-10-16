package auxiliary;

import java.time.LocalTime;

public class ChinaIdeaCompute implements Runnable {

//this class does periodic computation of chinadata
    @Override
    public void run() {
        try {
            System.out.println("Idea Compute" + LocalTime.now());
            IdeaProcessor.chooseGraphs();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("interrupted");
            System.out.println("Idea computeYtd issue");
        }
    }
}
