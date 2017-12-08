package TradeType;

import utility.Utility;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

//trade block contains variout trades
public final class TradeBlock {

    private List<? super Trade> mergeList = Collections.synchronizedList(new LinkedList<>());

    public List<? super Trade> getTradeList() {
        return mergeList;
    }

    public TradeBlock(Trade t) {
        mergeList.add(t);
    }

    public void merge(Trade t) {
        mergeList.add(t);
    }

    public int getSizeAll() {
        return mergeList.stream().mapToInt(t -> ((Trade) t).getSize()).sum();
    }

    public double getAveragePrice() {
        return getDeltaAll() / getSizeAll();
    }

//    public double applyDoubleFunction(ToDoubleFunction<? super Trade> f, DoubleBinaryOperator op) {
//        return mergeList.stream().mapToDouble(e-> f.applyAsDouble((Trade)e)).reduce(op).orElse(0.0);
//    }

    public double getDeltaAll() {
        return mergeList.stream().mapToDouble(t -> ((Trade) t).getDelta()).sum();
    }


    public double getTransactionAll(String name) {
        return mergeList.stream().mapToDouble(t -> ((Trade) t).getTransactionFee(name)).sum();
    }

    public double getCostBasisAll(String name) {
        return mergeList.stream().mapToDouble(t -> ((Trade) t).getCostBasisWithFees(name)).sum();
    }

    public double getMtmPnlAll(String name) {
        return mergeList.stream().mapToDouble(t -> ((Trade) t).getMtmPnl(name)).sum();
    }

    @Override
    public String toString() {
        return Utility.getStr(" trade block size", mergeList.size(),
                mergeList.stream().map(Object::toString).collect(Collectors.joining(",")));
    }
}
