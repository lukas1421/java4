import api.TradingConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
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

    private static NavigableMap<Integer, String> keyList = new TreeMap<>();

    private static void loadList() {
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
        String firstInt = JOptionPane.showInputDialog("Please input number ");
        List<String> al1 = Arrays.asList(firstInt.split("\\s+"));
        StringBuilder sb = new StringBuilder();
        for (String s : al1) {
            int input = Integer.parseInt(s);
            if (input > 0 && input <= 224) {
                sb.append(keyList.getOrDefault(input, ""));
                sb.append(" ");
            } else {
                throw new IllegalStateException(" numbers incorrect ");
            }
        }

        String myString = sb.toString();
        StringSelection stringSelection = new StringSelection(myString);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }
}
