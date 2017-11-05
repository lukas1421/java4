package TradeType;

import java.time.LocalDateTime;

class ChinaTrade {
    private String ticker;
    private LocalDateTime tradeTime;
    private double price;
    private int quantity;

    public ChinaTrade() {
        ticker = "";
        tradeTime = LocalDateTime.now();
        price = 0.0;
        quantity = 0;
    }

    public ChinaTrade(String s, LocalDateTime t, double p, int q) {
        ticker = s;
        tradeTime = t;
        price = p;
        quantity = q;
    }
}
