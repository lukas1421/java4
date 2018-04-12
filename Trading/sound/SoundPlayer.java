package sound;

import apidemo.TradingConstants;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import javax.swing.*;
import java.io.File;
import java.net.URI;
import java.time.LocalTime;

public class SoundPlayer {

    private static void initAndShowGUI() {
        JFrame frame = new JFrame("Swing and JavaFX");
        final JFXPanel fxPanel = new JFXPanel();
        frame.add(fxPanel);
        frame.setSize(1000, 500);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Platform.runLater(() -> initFX(fxPanel));
    }

    private static void initFX(JFXPanel fxPanel) {
        Scene scene = createScene();
        fxPanel.setScene(scene);
    }

    private static Scene createScene() {
        String fileName = TradingConstants.GLOBALPATH + "suju.wav";
        File file = new File(fileName);
        URI uri = file.toURI();
        Media pick = new Media(uri.toString());
        MediaPlayer player = new MediaPlayer(pick);

        player.setOnEndOfMedia(()->{
            System.out.println(" playing @ " + LocalTime.now());
            player.seek(Duration.ZERO);
        });
        MediaView mediaView = new MediaView(player);
        Group root = new Group(mediaView);
        player.play();

        return (new Scene(root, 500, 200));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SoundPlayer::initAndShowGUI);
    }

}
