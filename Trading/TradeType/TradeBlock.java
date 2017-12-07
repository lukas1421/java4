package TradeType;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class TradeBlock {
    List<? super Trade> mergeList = Collections.synchronizedList(new LinkedList<>());

    public int getSizeAll() {
        return mergeList.stream().mapToInt(t->((Trade)t).getSize()).sum();
    }

}
