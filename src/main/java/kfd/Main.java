package kfd;

import kfd.data.MethodNode;
import kfd.util.MethodSignatureParamsParser;
import kfd.util.SootCallGraphBuilder;
import kfd.util.DOTPrinter;


import java.util.Collections;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.io.IOException;
import java.io.File;
import java.util.Map;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;


import org.xmlpull.v1.XmlPullParserException;

import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;


import soot.Body;
import soot.PatchingChain;
import soot.Unit;
import soot.SootMethod;
import soot.SootField;
import soot.Scene;
import soot.SootClass;
import soot.Value;
import soot.Local;
import soot.PackManager;
import soot.MethodOrMethodContext;

import org.apache.commons.cli.*;


public class Main {
    //private static final String androidSupportV4Path = ".res/android-jars/support-v4/19.0.0/support-v4-19.0.0.jar";
    private static final String SourcesAndSinksFilePath = "./res/SourcesAndSinks.txt";

    public static void main (String[] args) {
        String apkPath;
        String outputDotFolderString = "./kdfOutput";
        String methodsToAnalyzeFilePath = "./res/MethodsToFind.txt";
        String androidSdkPath = "./res/android.jar";


        Options options = new Options();
        Option outputFolderOpt = new Option("o", "output", true, "Path to output folder [optional. Default=./kfdOuput]");
        outputFolderOpt.setRequired(false);
        options.addOption(outputFolderOpt);

        Option apkOpt = new Option("a", "apk", true, "Path to apk");
        apkOpt.setRequired(true);
        options.addOption(apkOpt);

        Option methodsToAnalyzeOpt = new Option("m", "mta", true, "Path to text file containing the Soot method signature to analyze. [Default=./res/MethodsToFind.txt]");
        methodsToAnalyzeOpt.setRequired(false);
        options.addOption(methodsToAnalyzeOpt);

        Option sdkOpt = new Option ("s", "sdk", true, "Path to Android SDK. [Default=./res/android.jar]");
        sdkOpt.setRequired(false);
        options.addOption(sdkOpt);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        System.out.println("Size of args: " + args.length);

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);

            System.exit(1);
            return;
        }

        apkPath = cmd.getOptionValue("a");
        if (cmd.hasOption("o")) {
            outputDotFolderString = cmd.getOptionValue("o");
        }
        if (cmd.hasOption("s")) {
            androidSdkPath = cmd.getOptionValue("s");
        }
        if (cmd.hasOption("m")) {
            methodsToAnalyzeFilePath = cmd.getOptionValue("m");
        }

        File outputDotFolder = checkOutputFolder(outputDotFolderString); 
        if (outputDotFolder == null) {
            //Error string printed by checkOutputFolder
            return;
        }



        SootCallGraphBuilder scgb = new SootCallGraphBuilder(apkPath, androidSdkPath, SourcesAndSinksFilePath, methodsToAnalyzeFilePath);
        CallGraph cg = scgb.createCallGraph();
        System.out.println("========================================================");
        System.out.println("---- PRINTING CALLGRAPH ----");
        printCallGraph(cg);
        System.out.println("========================================================");

        System.out.println("---- Parsing Method List----");
        Map<SootMethod, Set<Integer>> methodAndParams = MethodSignatureParamsParser.parse(methodsToAnalyzeFilePath);
        System.out.println("========================================================");
        int methodCounter = 0;
        for (Map.Entry<SootMethod, Set<Integer>> entry : methodAndParams.entrySet()){

            SootMethod methodToFind = entry.getKey();
            Set<Integer> paramIdxsToResolve = entry.getValue();

            if (methodToFind != null) {
                System.out.println("|||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
                System.out.println("Looking for instances of method: " + methodToFind.toString());
                ConstantFinder cf = new ConstantFinder(methodToFind, paramIdxsToResolve, cg);
                cf.findConstants();
                System.out.println("========================================================");
                cf.printConstants();
                System.out.println("Done with method: " + methodToFind.toString());
                DOTPrinter dotprinter = new DOTPrinter(cf.getDefGraph());
                dotprinter.printToFile(new File(outputDotFolder,  "method" + methodCounter + ".dot"));
                methodCounter++;
            }
        }
    }

    private static File checkOutputFolder(String outputDotFolderString) {
        File outputDotFolder = new File(outputDotFolderString);
        if (outputDotFolder.exists()) {
            if (! outputDotFolder.isDirectory()) {
                System.err.println("The output folder exists but it is not a directory: " + outputDotFolderString);
                return null;
            }
            System.out.println("Using output folder: " + outputDotFolderString);
        } else {
            System.out.println("Output folder does not exist, creating folder: " + outputDotFolderString);
            outputDotFolder.mkdir();
        }
        return outputDotFolder;
    }

    public static void printCallGraph(CallGraph cg) {
        Set<String> visitedNodes = new HashSet<String>();
        Iterator<Edge> cgEdges = cg.iterator();
        while (cgEdges.hasNext()){
            Edge currentEdge = cgEdges.next();
            SootMethod currentMethod = currentEdge.getSrc().method();
            String currentMethodSignature = currentMethod.getSignature();
            if (!visitedNodes.contains(currentMethodSignature)) {
                visitedNodes.add(currentMethodSignature);
                System.out.println(currentMethodSignature);
                if (currentMethod.hasActiveBody()) {
                    Body methodBody = currentMethod.getActiveBody();
                    PatchingChain<Unit> methodUnits = methodBody.getUnits();
                    Iterator<Unit> unitItr = methodUnits.iterator();
                    while (unitItr.hasNext()) {
                        Unit u = unitItr.next();
                        if (u instanceof Stmt) {
                            Stmt stmt = (Stmt) u;
                            if (stmt.containsInvokeExpr()) {
                                InvokeExpr invExpr = stmt.getInvokeExpr();
                                System.out.println("\t" + invExpr.getMethod().toString());
                            }
                        }
                    }
                }
            }
        }

    }


    
}
