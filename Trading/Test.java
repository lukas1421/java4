import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utility.Utility.pr;

public class Test {


    public static void main(String[] args) {
        Pattern p = Pattern.compile("^(?!sh|sz).*$");
        Matcher m = p.matcher("hk000001");
        while (m.find()) {
            pr(m.group());
        }
    }

//
}
