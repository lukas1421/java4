package graph;

import graph.GraphMonitor;

import static java.lang.Math.abs;
import static java.lang.Math.round;

import java.util.HashMap;
import java.util.Map;

public class GraphMonitorFactory {

    static Map<Integer, GraphMonitor> mp = new HashMap<>();

    private GraphMonitorFactory() {
        throw new UnsupportedOperationException();
    }

    static GraphMonitor generate(int i) {
        GraphMonitor gm = new GraphMonitor();
        mp.put(i, gm);
        return gm;
    }

    static GraphMonitor getGraphMonitor(int i) {
        return mp.getOrDefault(i, new GraphMonitor());
    }

    static void clearAllGraphs() {
        mp.entrySet().forEach(e -> e.getValue().clearGraph());
    }

}
