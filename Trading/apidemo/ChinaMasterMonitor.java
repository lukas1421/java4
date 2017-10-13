package apidemo;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import javax.swing.JPanel;

import javafx.application.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.effect.Glow;
import javafx.stage.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javax.swing.JFrame;

public final class ChinaMasterMonitor extends JPanel {

    //general monitor parameters
    //number of stocks up
    public static int numberUp;
    //strategy 1 parameters
    public static int percentileYCeiling;
    public static int percentileJolt;

    //strategy 2 parameters (late morning ~ afternoon)
    public static int amMaxTFloor;
    public static int amMinTCeiling;
    public static int f10Floor; //default = 0.0

    public ArrayList returnQuantile;
    public ArrayList amMaxQuantile;
    public ArrayList amMinQuantile;
    public ArrayList retOPCQuantile;
    public ArrayList f10Quantile;

    final JFXPanel fxPanel;

    ChinaMasterMonitor() {

        JPanel jp = new JPanel();
        //JFrame frame = new JFrame("swing and java");
        fxPanel = new JFXPanel();
        jp.add(fxPanel);

        //jp.add(frame);
        this.setLayout(new GridLayout());
        //frame.setSize(300,200);
        //frame.setVisible(true);
        //frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        //setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        //add(frame);

        add(jp);
        Scene scene = createScene();
        fxPanel.setScene(scene);

        //Platform.runLater(() -> initFX(fxPanel));
    }

    public ArrayList computeQuantiles(ArrayList a) {
        Collections.sort(a);
        ArrayList res = new ArrayList();
        res.add(a.get(a.size() / 4));
        res.add(a.get(a.size() * 2 / 4));
        res.add(a.get(a.size() * 3 / 4));
        res.add(a.get(a.size() - 1));
        System.out.println(" res"
                + " is " + res);
        return res;
    }

    public static void initAndShowGUI() {
        JFrame frame = new JFrame("Swing and Java");
        final JFXPanel fxPanel = new JFXPanel();
        frame.add(fxPanel);
        frame.setSize(300, 200);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Platform.runLater(() -> initFX(fxPanel));

    }

    private static void initFX(JFXPanel fxPanel) {

        Scene scene = createScene();
        fxPanel.setScene(scene);
    }

    private static Scene createScene() {

        GridPane root = new GridPane();
        Scene scene = new Scene(root, 1000, 1000);
        Text text = new Text();

        Button a = new Button("Alpha");
        Button b = new Button("Beta");
        b.setEffect(new Glow(2.0));
        Label response = new Label("push a button");
        a.setOnAction(ae -> {
            response.setText("alpha was pressed");

        });
        b.setOnAction(ae -> {
            response.setText("beta was pressed");

        });

        // text.setX(40);
        //text.setY(100);
        text.setFont(new Font(25));
        Label upNo = new Label("Up");
        Label downNo = new Label("down");
        Label upValue = new Label("");

        Label retOPC = new Label("retOPC%");
        Label f10 = new Label("F10");
        Label amMaxT = new Label("amMaxT");
        Label amMinT = new Label("amMinT");

        upValue.setText(Double.toString(100));
        Label downValue = new Label("");
        downValue.setText(Double.toString(100));

        text.setText("welcome");
        //root.getChildren().add(text);

        FlowPane fp2 = new FlowPane();
        fp2.getChildren().addAll(response, a, b);

        //root.(fp2);
        //root.getChildren().addAll(upNo,downNo);
        ObservableList<String> selectedStocks = FXCollections.observableArrayList(ChinaStock.nameMap.values());
        ListView<String> test = new ListView<>(selectedStocks);
        test.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        root.setAlignment(Pos.TOP_LEFT);
        root.setVisible(true);
        root.add(upNo, 0, 1);
        root.add(downNo, 0, 2);
        root.add(upValue, 1, 1);
        root.add(downValue, 1, 2);
        root.add(retOPC, 0, 3);
        root.add(f10, 0, 4);
        root.add(amMaxT, 0, 5);
        root.add(amMinT, 0, 6);

        root.add(fp2, 0, 7);
        root.add(text, 0, 10);
        root.add(test, 0, 13);

        root.setGridLinesVisible(true);
        //root.
        //root.add(myLabel,0,4);
        // GridPane.setRowIndex(fp2, 2);
        //  GridPane.setColumnIndex(fp2, 2);

        //root.(myLabel);
        return (scene);
    }

    public void start(Stage myStage) {
        myStage.setTitle("app");
        FlowPane rootNode = new FlowPane();

        Scene myScene = new Scene(rootNode, 300, 200);
        myStage.setScene(myScene);
        myStage.show();
    }

    public static void main(String[] args) {

        JFrame jf = new JFrame();
        ChinaMasterMonitor cmm = new ChinaMasterMonitor();
        jf.add(cmm);
        jf.setSize(500, 500);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
//        SwingUtilities.invokeLater(()-> {
//            initAndShowGUI();
//            
//            
//        });
        //launch(args);

//        ArrayList res = new ArrayList();
//        Random rn  = new Random();
//        
//        
//        IntStream.range(1,100000).forEach(i -> res.add(rn.nextInt(10000)));
//        System.out.println((cmm.computeQuantiles(res)));
    }
}
