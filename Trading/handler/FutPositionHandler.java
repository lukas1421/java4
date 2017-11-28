//package handler;
//
//import apidemo.ChinaPosition;
//import client.Contract;
//import controller.ApiController;
//
//public class FutPositionHandler implements ApiController.IPositionHandler {
//
//    @Override
//    public void position(String account, Contract contract, double position, double avgCost) {
//
//        String ticker = utility.Utility.ibContractToSymbol(contract);
//
//        if(ticker.equals("SGXA50")) {
//            System.out.println(" XU front position is " + position);
//            ChinaPosition.xuCurrentPositionFront = (int) position;
//        }
//
//        if(ticker.equals("SGXA50BM")) {
//            System.out.println(" XU back position is " + position);
//            ChinaPosition.xuCurrentPositionBack = (int) position;
//        }
//
////        if (contract.symbol().equals("XINA50")) {
////            System.out.println(" XU position is " + position);
////            ChinaPosition.xuCurrentPositionFront = (int) position;
////        }
//    }
//
//    @Override
//    public void positionEnd() {
//        System.out.println(" position request ended in Futpositionhandler");
//    }
//
//}
