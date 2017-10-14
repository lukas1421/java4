package apidemo;

import graph.GraphBig;
import graph.GraphBigYtd;
import graph.GraphIndustry;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import static apidemo.ChinaStock.industryNameMap;

public final class ChinaBigGraph extends JPanel {

    public static GraphBig gb = new GraphBig();
    public static GraphBigYtd gYtd = new GraphBigYtd();
    public static LocalTime lastSetTime = LocalTime.MIN;
    static String currentStock = "sh000001";
    public final int TOP_GRAPH_HEIGHT = 600;
    public final int YTD_GRAPH_HEIGHT = 400;

    ChinaBigGraph() {

        JScrollPane chartScroll = new JScrollPane(gb) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = TOP_GRAPH_HEIGHT;
                d.width = ChinaMain.GLOBALWIDTH;
                return d;
            }
        };

        JScrollPane chartScrollYtd = new JScrollPane(gYtd) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = YTD_GRAPH_HEIGHT;
                d.width = ChinaMain.GLOBALWIDTH;
                return d;
            }
        };

        gb.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (industryNameMap.getOrDefault(gb.getName(), "").equals("板块")) {
                    ChinaStock.setIndustryFilter(gb.getName());
                    GraphIndustry.selectedNameIndus = ChinaStock.longShortIndusMap.getOrDefault(gb.getName(), "");
                    ChinaGraphIndustry.pureRefresh();
                } else {
                    ChinaStock.setGraphGen(industryNameMap.get(gb.getName()), ChinaStock.graph5);
                    ChinaStock.setIndustryFilter(industryNameMap.get(gb.getName()));
                    GraphIndustry.selectedNameIndus = ChinaStock.shortIndustryMap.getOrDefault(gb.getName(), "");
                    ChinaGraphIndustry.pureRefresh();
                }
                ChinaStock.refreshGraphs();
            }
        });

        setLayout(new BorderLayout());

        JPanel jp = new JPanel();
        jp.add(chartScroll);

        JPanel jpBelow = new JPanel();
        jpBelow.add(chartScrollYtd);

        add(jp, BorderLayout.CENTER);
        add(jpBelow, BorderLayout.SOUTH);
    }

    public static void refresh() {
        gb.repaint();
        gYtd.repaint();
        setGraph(currentStock);
    }

    public static void setGraph(String nam) {
        if (nam != null) {
            gb.fillInGraph(nam);
            gYtd.fillInGraph(nam);
            gb.repaint();
            gYtd.repaint();
            lastSetTime = LocalTime.now();
            currentStock = nam;
        }
    }
}
