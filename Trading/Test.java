import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.time.LocalDate;
import java.util.regex.Matcher;

import static utility.Utility.pr;

public class Test {

    public static void main(String[] args) {

        SocketAddress addr = new InetSocketAddress("127.0.0.1", 1080);
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, addr);
        Proxy proxy1 = Proxy.NO_PROXY;
        URL url = null;
        try {
            //url = new URL("http://hq.sinajs.cn/list=" + "sh000001");
            url = new URL("https://www.google.com");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        try {
            assert url != null;
            URLConnection conn = url.openConnection(proxy);
            String line1;
            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                while ((line1 = reader2.readLine()) != null) {
                    pr(line1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
