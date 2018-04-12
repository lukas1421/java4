package sound;

import apidemo.TradingConstants;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

import javax.swing.*;
import java.io.File;
import java.net.URI;

public class EmbeddedSoundPlayer {

    private static MediaPlayer player;

    public EmbeddedSoundPlayer() {
        SwingUtilities.invokeLater(EmbeddedSoundPlayer::initAndShowGUI);
    }

    public void playClip() {
        try {
            if(player.getStatus()== MediaPlayer.Status.PLAYING) {
                player.stop();
            } else {
                player.play();
            }
        } catch(MediaException ex) {
            System.out.println(" media not available ");
            ex.printStackTrace();
        };
    }

    private static void initAndShowGUI() {
        //JFrame jf = new JFrame(" Sound player ");
        //jf.setSize(300,300);
        //JButton startButton = new JButton("Start");
        //JButton stopButton = new JButton("Stop");
//        startButton.addActionListener(l->{
//            if(player.getStatus()== MediaPlayer.Status.PLAYING) {
//                player.stop();
//                player.play();
//            } else {
//                player.play();
//            }
//        });
//        stopButton.addActionListener(l->{
//            player.stop();
//        });
//        jf.setLayout(new GridLayout(1,2));
//        jf.add(startButton);
//        jf.add(stopButton);
//        jf.setVisible(true);
//        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final JFXPanel fxPanel = new JFXPanel();
        Platform.runLater(() -> initFX(fxPanel));
    }

    private static void initFX(JFXPanel fxPanel) {
        Scene scene = createScene();
        fxPanel.setScene(scene);
    }

    private static MediaPlayer fileNameToURIString(String s) {
        File file = new File(s);
        URI uri = file.toURI();
        Media pick = new Media((uri.toString()));
        return new MediaPlayer(pick);
    }

    private static Scene createScene() {
        String fileName = TradingConstants.GLOBALPATH + "suju.wav";
        player = fileNameToURIString(fileName);
//        player.setOnEndOfMedia(()->{
//            player.seek(Duration.ZERO);
//        });
        MediaView mediaView = new MediaView(player);
        Group root = new Group(mediaView);

        return (new Scene(root, 500, 200));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(EmbeddedSoundPlayer::initAndShowGUI);
    }
}
