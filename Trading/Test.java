import AutoTraderOld.XuTraderHelper;
import enums.Direction;
import net.sourceforge.tess4j.TesseractException;

import net.sourceforge.tess4j.Tesseract;

import java.io.File;

import static utility.Utility.pr;


public class Test {


    public static void main(String[] args) {

        pr(XuTraderHelper.roundToPricePassiveGen(13242.6, Direction.Long, 2.5));
        pr(XuTraderHelper.roundToPricePassiveGen(13242.6, Direction.Short, 2.5));
        pr(XuTraderHelper.roundToPricePassiveGen(13242.6, Direction.Long, 5));
        pr(XuTraderHelper.roundToPricePassiveGen(13242.6, Direction.Short, 5));

    }
}

//public class Test {
//
//
//    public static void main(String[] args) {
//
//
//    }
//
//}
