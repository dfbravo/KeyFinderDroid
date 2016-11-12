package kfd.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Scene;
import soot.SootMethod;
import soot.MethodOrMethodContext;
import soot.SootClass;


public class MethodSignatureParamsParser {
    protected static final Logger logger = LoggerFactory.getLogger(MethodSignatureParamsParser.class);

    public static Map<SootMethod, Set<Integer>> parse(String filePath) {
        Map<SootMethod, Set<Integer>> MethodsAndParams = new HashMap<SootMethod, Set<Integer>>();
        Pattern methodParamsPattern = Pattern.compile("^(<.*>),\\[(.*)\\]");
        try {
            BufferedReader br = new BufferedReader(new FileReader(filePath));
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                Matcher matcher = methodParamsPattern.matcher(line);
                if (matcher.find()) {
                    String methodSignature = matcher.group(1);
                    SootMethod sm = methodSignatureToSootMethod(methodSignature);
                    String[] stringParams = matcher.group(2).split(",");
                    Set<Integer> params = new HashSet<Integer>();
                    for (String stringParam : stringParams) {
                        params.add(Integer.decode(stringParam));
                    }
                    MethodsAndParams.put(sm, params);
                }
                else {
                    logger.warn("Found erronous line in " + filePath);
                    logger.warn(line);
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return MethodsAndParams;
    }

    private static SootMethod methodSignatureToSootMethod(String methodSignature) {
        /* Pattern Groups:
         * 1 - class name
         * 2 - return type
         * 3 - method name and params
         */
        Pattern signaturePattern = Pattern.compile("^<([\\w\\.]*):\\s+([a-zA-Z]*)\\s+(.*)>$");
        Matcher signatureMatcher = signaturePattern.matcher(methodSignature);
        logger.info("Converting signature " + methodSignature);
        SootMethod sm = null;
        if (signatureMatcher.find()) {
            String className = signatureMatcher.group(1);
            SootClass sc = Scene.v().getSootClassUnsafe(className);
            if (sc != null) {
                logger.debug("\tMethod belongs to class: " + sc.toString());
                logger.debug("\tMethods in the class:");
                List<SootMethod> methods = sc.getMethods();
                if (methods.isEmpty()) {
                    logger.debug("\t\tNo methods found");
                }
                else {
                    for (SootMethod potentialMethod : sc.getMethods()) {
                        String potentialMethodSignature = potentialMethod.getSignature();
                        logger.debug("\t-- " + potentialMethodSignature);
                        if (methodSignature.equals(potentialMethodSignature)){
                            if (sm != null) {
                                logger.warn("Found multiple matches for method signature");
                            }
                            sm = potentialMethod;
                        }
                    }
                }
            }
            else {
                logger.debug("Could not find class");
            }
        }
        else {
            logger.debug("Method signature did not match");
        }
        return sm;
    }
}
