package auxiliary;

///*
// * To change this license header, choose License Headers in Project Properties.
// * To change this template file, choose Tools | Templates
// * and open the template in the editor.
// */
//package api;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.FileReader;
//import java.io.IOException;
//import java.time.LocalDate;
//import java.time.Month;
//import java.util.Arrays;
//import java.util.List;
//import static java.util.stream.Collectors.toList;
//import java.util.stream.DoubleStream;
//
//import org.rosuda.REngine.REXP;
//import org.rosuda.REngine.REXPDouble;
//import org.rosuda.REngine.REXPFactor;
//import org.rosuda.REngine.REXPGenericVector;
//import org.rosuda.REngine.REXPList;
//import org.rosuda.REngine.REXPMismatchException;
//import org.rosuda.REngine.REXPString;
//import org.rosuda.REngine.REngineException;
//import org.rosuda.REngine.RFactor;
//import org.rosuda.REngine.RList;
//import org.rosuda.REngine.Rserve.RConnection;
//import org.rosuda.REngine.Rserve.RserveException;
//
//public class Rtest {
//
//    public static void main(String[] args) throws RserveException, REXPMismatchException, FileNotFoundException, IOException, REngineException {
//        
//        
//        try{
//            RConnection c = new RConnection();
//            System.out.println(c.toString());
//            
//            if(c.isConnected()) {
//                System.out.println("Connected to RServe.");
//                if(c.needLogin()) {
//                    System.out.println("Providing Login");
//                    c.login("username", "password");
//                }
//                
//                c.eval("library(rvest)");
//                c.eval("library(data.table)");
//                c.eval("library(lubridate)");
//                c.eval("library(stringr)");
//                c.eval("library(zoo)");
//               
//                //REXP rResponseObject = c.parseAndEval("try(eval("+"analyzeSS(\"sh600519\")"+"),silent=TRUE)"); 
//                //getWtdPercentileAll()
//                                
//                String mainExpression = "";
//                String mainExpression2 = "getWtdPercentileAll()";
//                String mainExpression3 ="getDataPure(\"sh600519\")";
//                
//                //REXP r1 = c.parseAndEval("try(eval("+mainExpression2+"),silent=TRUE)");
//                    
//                REXPGenericVector r2 = (REXPGenericVector)c.parseAndEval("try(eval("+mainExpression2+"),silent=TRUE)");
//                
//                System.out.println( " r2 is " + r2);
//                
//                REXPString r = (REXPString)c.parseAndEval("capture.hkTestOutput(try(eval("+mainExpression2+"),silent=TRUE))");
//                
//                String[] out = r.asStrings();
//                
//                System.out.println("out is how long " + out.length);
//                
//                for(String s:out) {
//                    System.out.println(s); 
//                }
//                
//                if (r2.inherits("try-error")) { 
//                    System.out.println("R Serve Eval Exception : "+r2.asString()); 
//                } else {
//                    System.out.println(" printing results ");
//                    
//                    REXPGenericVector r1 = (REXPGenericVector)r2;
//                    RList l = r1.asList();
//                    System.out.println("keys " + l.keyAt(0));
//                    
//                    for(int i = 0; i<l.size(); i++) {
//                        Object d= l.get(i);
//                        System.out.println( "key at "+ i + " "+ l.keyAt(i));
//                        
//                        if(l.keyAt(i).equals("D")){
//                            System.out.println("D");
//                            double[] d2 = ((REXPDouble)d).asDoubles();
//                            for(double d3:d2) {
//                                System.out.println("double value "+d3);
//                                System.out.println(LocalDate.of(1970, 1,1).plusDays(Math.round(d3)));
//                            }
//                        } else {
//                            System.out.println(" d class " +d.getClass().toString());
//                            System.out.println(" d class 2 " +d.getClass().toGenericString());
//                            System.out.println(" printing non d");
//                            if(d instanceof REXPDouble) {
//                                System.out.println(" printing Double ");
//                                double[] d11 = ((REXPDouble)d).asDoubles();
//                                List<Double> d2 = DoubleStream.of(d11).boxed().collect(toList());
//                                
//                                System.out.println(d2.size());
//                                for(double d3:d2) {
//                                    System.out.println(d3);
//                                }
//                            } else if( d instanceof REXPFactor) {
//                                System.out.println(" printing factor ");
//                                RFactor f = ((REXPFactor)d).asFactor();
//                                System.out.print(" levels is " + f.levels());
//                                System.out.println("factor here");
//                            } else if(d instanceof REXPString) {   
//                                System.out.println(" printing string ");
//                                String[] d2 = ((REXPString) d).asStrings();
//                                for(String x: d2) {
//                                    System.out.println(x);
//                                }
//                            } else if(d instanceof REXPGenericVector) {
//                                REXPGenericVector d1 = (REXPGenericVector)d;
//                                RList x= ((REXPGenericVector) d).asList();
//                                //x.values().
//                                for(i=0; i<x.size(); i++) {
//                                    System.out.println( "key at "+ i + " "+ x.keyAt(i));
//                                    System.out.println(x.at(i).getClass().toString());
//                                    System.out.println(x.at(i).getClass().toGenericString());
//                                    System.out.println(x.at(i).asDouble());
//                                }
//                                
//                            }
//                        }
//                    }
//                }                
//                c.close();
//                System.out.println("Done.");
//            } else {
//                System.out.println("Rserve could not connect");
//            }
//        } catch(Exception ex) {
//            ex.printStackTrace();
//            System.out.println("Session Closed");
//        }
//    }
//
//}
