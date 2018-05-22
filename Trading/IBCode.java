import apidemo.TradingConstants;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import static utility.Utility.pr;

public class IBCode extends JPanel {

    public static NavigableMap<Integer, String> keyList = new TreeMap<>();

    IBCode() {


    }

    public static void loadList() {
        String line;
        try (BufferedReader reader1 = new BufferedReader(
                new InputStreamReader(new FileInputStream(TradingConstants.GLOBALPATH + "IBCodes.txt")))) {
            while ((line = reader1.readLine()) != null) {
                List<String> al1 = Arrays.asList(line.split("\t"));
                keyList.put(Integer.parseInt(al1.get(0)), al1.get(1));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        pr(keyList);
    }

    public static void main(String[] args) {
        loadList();
//        JFrame jf = new JFrame();
//        jf.setSize(new Dimension(300, 300));
//        IBCode ib = new IBCode();
//        jf.add(ib);
//        jf.setLayout(new FlowLayout());
//        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        jf.setVisible(true);

        String firstInt = JOptionPane.showInputDialog("Please input number ");
        List<String> al1 = Arrays.asList(firstInt.split("\\s+"));
        StringBuilder sb = new StringBuilder();
        for (String s : al1) {
            int input = Integer.parseInt(s);
            sb.append(keyList.getOrDefault(input, ""));
            sb.append(" ");
        }

        JTextArea ta = new JTextArea(10, 10);
        ta.setText(sb.toString());
        ta.setWrapStyleWord(true);
        ta.setLineWrap(true);
        ta.setCaretPosition(0);
        ta.setSelectionStart(0);
        ta.setSelectionEnd(6);
        ta.setEditable(false);

        JOptionPane.showMessageDialog(null, new JScrollPane(ta), "RESULT", JOptionPane.INFORMATION_MESSAGE);
        //JOptionPane.showMessageDialog(null, sb);


    }

}
