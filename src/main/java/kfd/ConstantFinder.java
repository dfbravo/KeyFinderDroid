package kfd;

import kfd.data.DefGraph;
import kfd.data.DefNode;
import kfd.data.IEdge;
import kfd.data.PPEdge;
import kfd.data.RVEdge;
import kfd.data.MethodNode;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Deque;
import java.util.Iterator;

import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

import soot.jimple.Expr;
import soot.jimple.InvokeExpr;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.BinopExpr;
import soot.jimple.UnopExpr;
import soot.jimple.CastExpr;
import soot.jimple.InstanceOfExpr;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.Stmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.StaticFieldRef;
import soot.jimple.Constant;
import soot.jimple.ParameterRef;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.ArrayRef;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;

import soot.util.Chain;

import soot.Body;
import soot.SootFieldRef;
import soot.PatchingChain;
import soot.Unit;
import soot.SootMethod;
import soot.SootField;
import soot.SootClass;
import soot.Value;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.VoidType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConstantFinder {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private SootMethod initialMethodToFind;
    private Set<Integer> initialParamIdxsToResolve;
    private CallGraph cg;
    private DefGraph dg;

    public ConstantFinder(SootMethod initialMethodToFind, Set<Integer> initialParamIdxsToResolve, CallGraph cg) {
        this.initialMethodToFind = initialMethodToFind;
        this.initialParamIdxsToResolve = initialParamIdxsToResolve;
        this.cg = cg;
        this.dg = new DefGraph();
    }

    private void initDefGraph() {
       MethodNode startMethodNode = new MethodNode(initialMethodToFind);
       dg.setStartNode(startMethodNode);

       Set<MethodNode> callerNodes = getMethodNodeCallers(startMethodNode);

       for (MethodNode callerNode : callerNodes) {
           dg.addPPEdge(callerNode, startMethodNode, initialParamIdxsToResolve);
       } 
    }

    public void findConstants() {
        initDefGraph();

        Deque<MethodNode> methodsToAnalyze = new ArrayDeque<MethodNode>();
        methodsToAnalyze.push(dg.getStartNode());

        while (!methodsToAnalyze.isEmpty()) {
            MethodNode mn = methodsToAnalyze.pop();

            for (IEdge edge : mn.getIncomingEdges()){
                Set<Integer> newParamIdxsToResolve = findHardCodedKey(edge);

                if (!newParamIdxsToResolve.isEmpty()) {
                    Set<MethodNode> callerNodes = getMethodNodeCallers(edge.getCaller());
                    for (MethodNode callerNode : callerNodes) {
                        dg.addPPEdge(callerNode, edge.getCaller(), newParamIdxsToResolve);
                        methodsToAnalyze.push(edge.getCaller());
                    }
                }
            }
        }
    }

    private Set<MethodNode> getMethodNodeCallers(MethodNode mn) {
        Set<MethodNode> mnCallers = new HashSet<MethodNode>();
        Set<SootMethod> smCallers = getSootMethodCallers(mn.getMethod());
        for (SootMethod sm : smCallers) {
            mnCallers.add(new MethodNode(sm));
        }
        return mnCallers;
    }

    private Set<Integer> findHardCodedKey(IEdge edge) {
        Set<Integer> newParamIdxs = new HashSet<Integer>();
        MethodNode callerNode = edge.getCaller();
        SootMethod callerMethod = callerNode.getMethod();
        MethodNode calleeNode = edge.getCallee();
        Set<Integer> paramIdxsToResolve = ((PPEdge) edge).getParamIdxs();
        
        if (callerMethod.hasActiveBody()) {
            Map<Unit, List<Value>> callsiteVals = getCallsiteVals(callerMethod, calleeNode.getMethod());
            
            for (Map.Entry<Unit, List<Value>> callsiteValEntry : callsiteVals.entrySet()) {
                Unit callsite = callsiteValEntry.getKey();
                List<Value> callsiteParams = callsiteValEntry.getValue();
                for (Integer paramIdx : paramIdxsToResolve) {
                    Value val = callsiteParams.get(paramIdx.intValue());
                    DefNode dn = new DefNode(callsite, val, null, callerNode);
                    Set<Integer> newParams = resolveDefs(dn);
                    callerNode.addDefNode(dn);
                    if (newParams.isEmpty()){
                        logger.debug("Not adding new params");
                    }
                    else {
                        logger.debug("Adding new params");
                    } 
                    newParamIdxs.addAll(newParams);
                }

            }
        }
        return newParamIdxs;
    }
    /** 
     * Finds the Unit callsites and the associated Values.
     * This function looks for Unit callsite of the callee method in the body of the caller method.
     * It returns a mapping of the Unit and its associated Values.
     */
    private Map<Unit, List<Value>> getCallsiteVals(SootMethod callerMethod, SootMethod calleeMethod) {
        Body callerBody = callerMethod.getActiveBody();
        Map<Unit, List<Value>> callsiteVals = new HashMap<Unit, List<Value>>();

        PatchingChain<Unit> callerUnits = callerBody.getUnits();
        Iterator<Unit> unitItr = callerUnits.iterator();
        while (unitItr.hasNext()) {
            Unit u = unitItr.next();
            if (u instanceof Stmt) {
                Stmt stmt = (Stmt) u;
                if (stmt.containsInvokeExpr()) {
                    InvokeExpr invExpr = stmt.getInvokeExpr();
                    SootMethod invMethod = invExpr.getMethod();

                    if (invMethod == calleeMethod){

                        List<Value> invArgs = invExpr.getArgs();
                        callsiteVals.put(u, invArgs);
                    }
                }
            }
        }
        return callsiteVals;
    }

    private Set<SootMethod> getSootMethodCallers(SootMethod method) {
        Set<SootMethod> callers = new HashSet<SootMethod>();
        //This function takes in a MethodOrMethodContext. A SootMethod can be casted as such.
        //The Context portion will be null. It does not matter in this case because
        //The callgraph is context-insensitive (since it is SPARK - see Soot Survivor's Guide)
        Iterator<Edge> callerEdges = cg.edgesInto((MethodOrMethodContext) method);
        while (callerEdges.hasNext()) {
            Edge callerEdge = callerEdges.next();
            callers.add(callerEdge.getSrc().method());
        }
        return callers;
    }

    private void printMethodDetails(SootMethod method) {
        logger.debug("----- PRINTING METHOD DETAILS -----");
        logger.debug("METHOD: " + method);
        if (method.hasActiveBody()) {
            Body b = method.getActiveBody();
            UnitGraph uGraph = new ExceptionalUnitGraph(b);
            LocalDefs ld = new SimpleLocalDefs(uGraph);

            List<Local> paramLocals = b.getParameterLocals();
            StringBuilder sb = new StringBuilder();
            //System.out.print("PARAMETER LOCALS: ");
            sb.append("PARAMETER LOCALS: ");
            for (Local l : paramLocals) {
                //System.out.print(l.toString() + " ");
                sb.append(l.toString() + " ");
            }
            logger.debug(sb.toString());
            logger.debug("");

            sb = new StringBuilder();
            //System.out.print("LOCALS: ");
            sb.append("LOCALS: ");
            Chain<Local> locals = b.getLocals();
            for (Local l : locals) {
                //System.out.print(l.toString() + " ");
                sb.append(l.toString() + " ");
            }
            logger.debug(sb.toString());
            logger.debug("");

            PatchingChain<Unit> units = b.getUnits();
            Iterator<Unit> unitItr = units.iterator();
            while (unitItr.hasNext()) {
                Unit u = unitItr.next();
                if (u instanceof Stmt) {
                    Stmt stmt = (Stmt) u;
                    logger.debug("\tSTMT: " + stmt.toString());
                    logger.debug("\tTYPE: " + stmt.getClass().toString());
                    if (stmt.containsInvokeExpr()) {
                        InvokeExpr invExpr = stmt.getInvokeExpr();

                        List<Value> invArgs = invExpr.getArgs();
                        if (invArgs.isEmpty()) {
                            logger.debug("\t\tNo params");
                        } else {
                            int paramIdx = 0;
                            for (Value val : invArgs) {
                                sb = new StringBuilder();
                                //System.out.print("\t\tparam" + paramIdx + ": " + val.toString() + " " + val.getType().toString());
                                sb.append("\t\tparam" + paramIdx + ": " + val.toString() + " " + val.getType().toString());
                                if (val instanceof Constant) {
                                    sb.append(" Constant");
                                }
                                else if (val instanceof Expr) {
                                    sb.append(" Expr");
                                }
                                else if (val instanceof Local) {
                                    if (paramLocals.contains(val)) {
                                        sb.append(" Local(param)");
                                    }
                                    else {
                                        sb.append(" Local");
                                    }
                                    logger.debug(sb.toString());
                                    List<Unit> valDefs = ld.getDefsOfAt((Local)val, u);
                                    logger.debug("\tDEFS: ");
                                    for (Unit def : valDefs) {
                                        logger.debug("\t\t" + def.toString());
                                    }
                                }
                                paramIdx++;
                            }
                        }
                    }
                }
                logger.debug("");
            }
        }
        else {
            logger.debug(method.getName() + " does not have an active body!");
        }

        logger.debug("------------------------------------");
    }

    /** This method resolves the definitions of the Value at the specific callsite. 
     *  It uses the SimpleLocalDefs to get the definitions at a specific Unit.
     *  The definition may contain other Values whose definition must also be resolved.
     *  The values are stored in a stack rather than resolving recursively.
     */
    private Set<Integer> resolveDefs(DefNode initialDn) {
        Set<Unit> visitedDefs = new HashSet<Unit>();
        logger.debug("Calling resolveDefs");
        Set<Integer> paramIdxsToResolve = new HashSet<Integer>();
        Deque<DefNode> dnStack = new ArrayDeque<DefNode>();
        dnStack.push(initialDn);
        while (!dnStack.isEmpty()) {
            DefNode currentDefNode = dnStack.pop();
            logger.debug("DEFS OF: " + currentDefNode.getUnit().toString());
            if (currentDefNode.getValue() instanceof Constant) {
                Value val = currentDefNode.getValue();
                logger.debug("Found Constant: " + val.toString());
            }
            else {
                List<Unit> valDefs = getDefsOfDefNode(currentDefNode);
                int i = 0;
                int totalDefNum = valDefs.size();
                if (totalDefNum > 1) {
                    logger.debug("WARNING: FOUND MORE THAN ONE DEFINITION FOR: " + currentDefNode.getValue());
                }
                for (Unit defUnit : valDefs) {
                    if (! visitedDefs.contains(defUnit)) {
                        visitedDefs.add(defUnit);
                        i++;
                        logger.debug("DEF " + i + "/" + totalDefNum + ": " +  defUnit.toString() + " | " + defUnit.getClass().toString());
                        DefinitionStmt defStmt = (DefinitionStmt) defUnit;
                        Value leftOp = defStmt.getLeftOp();
                        Value rightOp = defStmt.getRightOp();
                        logger.debug("rightOp is of type " + rightOp.getClass().toString());
                        if (rightOp instanceof Constant) {
                            DefNode newDefNode = currentDefNode.createAndAddNeighbour(defUnit, rightOp);
                            logger.debug("Found Constant: " + rightOp.toString());
                        }
                        else if (rightOp instanceof Expr) {
                            List<DefNode> nodesToAdd = resolveExpr(defUnit, rightOp, currentDefNode);
                            for (DefNode newDefNode : nodesToAdd) {
                                dnStack.push(newDefNode);
                            }
                        }
                        else if (rightOp instanceof ParameterRef) {
                            DefNode newDefNode = currentDefNode.createAndAddNeighbour(defUnit, rightOp);
                            paramIdxsToResolve.add(new Integer(((ParameterRef) rightOp).getIndex()));
                        }
                        else if (rightOp instanceof FieldRef) {
                            SootField field = ((FieldRef) rightOp).getField();
                            List<DefNode> nodesToAdd = resolveFieldRef(defUnit, rightOp, currentDefNode);
                            for (DefNode newDefNode : nodesToAdd) {
                                dnStack.push(newDefNode);
                            }
                        }
                        else if (rightOp instanceof ArrayRef) {
                            ArrayRef ar = (ArrayRef) rightOp;
                            Value array = ar.getBase();
                            Value index = ar.getIndex();
                            DefNode newDn = currentDefNode.createAndAddNeighbour(defUnit, array);
                            dnStack.push(newDn);
                            newDn = currentDefNode.createAndAddNeighbour(defUnit, index);
                            dnStack.push(newDn);

                        }
                        else if (rightOp instanceof Local) {
                            DefNode newDefNode = currentDefNode.createAndAddNeighbour(defUnit, rightOp);
                            dnStack.push(newDefNode);
                        }
                        else {
                            logger.debug("Do not support rightOp of type " + rightOp.getClass().toString());
                        }
                    }
                }
                //logger.debug("---------");
            }
        }
        return paramIdxsToResolve;
    }

    private List<DefNode> findArrayContents(DefNode dn) {
        List<DefNode> newDefNodes = new ArrayList<DefNode>();
        //This is the method where the array is created
        //It is assumed its contents are defined within this method as well
        SootMethod initializer = dn.getMethodNode().getMethod();
        Unit defUnit = dn.getUnit();

        if (defUnit instanceof DefinitionStmt) {
            Value base = ((DefinitionStmt) defUnit).getLeftOp();
            for (Unit u : initializer.getActiveBody().getUnits()) {
                //logger.debug(u.toString());
                if (u instanceof DefinitionStmt) {
                    Value leftOp = ((DefinitionStmt) u).getLeftOp();
                    if (leftOp instanceof ArrayRef) {
                        Value val = ((ArrayRef) leftOp).getBase();
                        if (val.equivTo(base)) {
                            Value rightOp = ((DefinitionStmt) u).getRightOp();
                            DefNode newDn = dn.createAndAddNeighbour(u, rightOp);
                            newDefNodes.add(newDn);
                        }
                    }
                }
            }
        }
        return newDefNodes;
    }

    private List<DefNode> resolveExpr(Unit defUnit, Value expr, DefNode pred) {
        List<DefNode> newDefNodes = new ArrayList<DefNode>();
        if (expr instanceof BinopExpr) {
            BinopExpr binExpr = (BinopExpr) expr;
            //Obtain the binary expression's operands
            Value leftOp = binExpr.getOp1();
            Value rightOp = binExpr.getOp2();
            DefNode newDn = pred.createAndAddNeighbour(defUnit, leftOp);
            newDefNodes.add(newDn);
            newDn = pred.createAndAddNeighbour(defUnit, rightOp);
            newDefNodes.add(newDn);
        }
        else if (expr instanceof UnopExpr) {
            UnopExpr unExpr = (UnopExpr) expr;
            Value op = unExpr.getOp();
            DefNode newDn = pred.createAndAddNeighbour(defUnit, op);
            newDefNodes.add(newDn);
        }
        else if (expr instanceof InstanceOfExpr) {
            InstanceOfExpr instOfExpr = (InstanceOfExpr) expr;
            Value op = instOfExpr.getOp();
            DefNode newDn = pred.createAndAddNeighbour(defUnit, op);
            newDefNodes.add(newDn);
        }
        else if (expr instanceof CastExpr) {
            CastExpr castExpr = (CastExpr) expr;
            Value op = castExpr.getOp();
            DefNode newDn = pred.createAndAddNeighbour(defUnit, op);
            newDefNodes.add(newDn);
        }
        else if (expr instanceof InvokeExpr) {
            InvokeExpr invExpr = (InvokeExpr) expr;
            if (expr instanceof InstanceInvokeExpr) {
                InstanceInvokeExpr instInvExpr = (InstanceInvokeExpr) expr;
                DefNode newDn = pred.createAndAddNeighbour(defUnit, instInvExpr.getBase());
                newDefNodes.add(newDn);
                logger.debug("\tAdding " + instInvExpr.getBase() + " to the list (InstInvoke)");
            }
            //if (invExpr.getArgCount() != 0) {
                List<DefNode> nodesToAdd = resolveInvokeExpr(pred, invExpr);
                newDefNodes.addAll(nodesToAdd);
            //}
        }
        else if (expr instanceof NewArrayExpr) {
            DefNode newDefNode = pred.createAndAddNeighbour(defUnit, expr);
            newDefNodes.addAll(findArrayContents(newDefNode));
            logger.debug("Adding nodes due to NewArrayExpr");
            for (DefNode dn : newDefNodes) {
                logger.debug(">unit " + dn.getUnit().toString());
                logger.debug(">value " + dn.getValue().toString());
            }
        }
        else if (expr instanceof NewExpr) {
            DefNode newDefNode = pred.createAndAddNeighbour(defUnit, expr);
            //The new DefNode is not added to the list. This is a new object and cannot be resolved further.
        }
        return newDefNodes;
    }

    private List<DefNode> resolveInvokeExpr(DefNode callerDn, InvokeExpr invExpr) {
        List<DefNode> newDefNodes = new ArrayList<DefNode>();
        SootMethod calleeMethod = invExpr.getMethod();

        logger.debug("\tAttempting to resolve an InvokeExpr");
        if (calleeMethod.hasActiveBody()) {
            logger.debug("\tHas active body");
            if (calleeMethod.getReturnType() instanceof VoidType) {
                logger.debug("\tVoid return type");
                List<Value> args = invExpr.getArgs();
                for (Value arg : args) {
                    DefNode newDn = callerDn.createAndAddNeighbour(callerDn.getUnit(), arg);
                    newDefNodes.add(newDn);
                }
            }
            else {
                MethodNode calleeNode = new MethodNode(calleeMethod);
                dg.addRVEdge(callerDn.getMethodNode(), calleeNode, callerDn);
                logger.debug("\tReturn of type " + calleeMethod.getReturnType().getClass().toString());
                List<Unit> returnStmts = getReturnStmts(calleeMethod);
                Set<Integer> paramIdxs = new HashSet<Integer>();
                for (Unit returnStmt : returnStmts) {
                    Value retVal = ((ReturnStmt) returnStmt).getOp();
                    DefNode retDn = callerDn.createAndAddInterNeighbour(returnStmt, retVal, new MethodNode(calleeMethod));
                    calleeNode.addDefNode(retDn);
                    logger.debug("\tRESOLVING RETURN TYPE ///////////////////");
                    paramIdxs.addAll(resolveDefs(retDn));
                    logger.debug("\tDONE RESOLVING RETURN TYPE ///////////////////");
                }
                List<Value> args = invExpr.getArgs();
                for (Integer paramIdx : paramIdxs) {

                    logger.debug("\tAdding parameter " + paramIdx + "to the list");
                    Value arg = args.get(paramIdx);
                    DefNode newDn = callerDn.createAndAddNeighbour(callerDn.getUnit(), arg);
                    newDefNodes.add(newDn);
                }
            }
        }
        else {
            //If the method called does not have a body. We assume that all the params must be resolved
            List<Value> args = invExpr.getArgs();
            for (Value arg : args) {
                DefNode newDn = callerDn.createAndAddNeighbour(callerDn.getUnit(), arg);
                newDefNodes.add(newDn);
            }
        }
        return newDefNodes; 
    }

    private List<Unit> getReturnStmts(SootMethod method) {
        List<Unit> returnUnits = new ArrayList<Unit>();
        if (method.hasActiveBody()) {
            Body b = method.getActiveBody();
            for (Unit u : b.getUnits()) {
                if (u instanceof ReturnStmt) {
                    logger.debug("\t\tFound return statement: " + u.toString());
                    returnUnits.add(u);
                }
                else if (u instanceof ReturnVoidStmt) {
                    logger.debug("Found return void statement, but the method has a return type!!!!");
                }
            }
        }
        return returnUnits;
    }

    private List<DefNode> resolveFieldRef(Unit defUnit, Value val, DefNode pred) {
        List<DefNode> newDefNodes = new ArrayList<DefNode>();
        if (val instanceof StaticFieldRef) {
            StaticFieldRef staticRef = (StaticFieldRef) val;
            SootField sf = staticRef.getField();
            SootClass declaringClass = staticRef.getFieldRef().declaringClass();
            if (declaringClass.declaresField(sf.getSubSignature())){
                SootMethod clinitMethod = declaringClass.getMethodUnsafe("void <clinit>()");
                if (clinitMethod != null) {
                    DefNode newDn = new DefNode(defUnit, val, pred, new MethodNode(clinitMethod));
                    pred.addNeighbour(newDn);
                    newDefNodes.add(newDn);
                }
                else{
                    //clinit does not exit
                }
            }
        }
        else if (val instanceof InstanceFieldRef) {
            InstanceFieldRef instRef = (InstanceFieldRef) val;
            SootField sf = instRef.getField();
            SootFieldRef sfr = instRef.getFieldRef();
            SootClass declaringClass = instRef.getFieldRef().declaringClass();
            if (declaringClass.declaresField(sf.getSubSignature())){
                for (SootMethod method : declaringClass.getMethods()) {
                    if (method.getSubSignature().contains("void <init>(")) {
                        DefNode newDn = new DefNode(defUnit, val, pred, new MethodNode(method));
                        pred.addNeighbour(newDn);
                        newDefNodes.add(newDn);
                    }
                }
            }
            if (newDefNodes.isEmpty()) {
                logger.debug("Did not find assignment to InstanceFieldRef: " + val.toString() + "in any of the <init> methods of its containing class");
            }
        }
        else {
            logger.debug("FieldRef Not Supported");
        }
        return newDefNodes;
    }


    private List<Unit> getDefsOfDefNode(DefNode dn) {
        List<Unit> valDefs = null;
        if (dn.getValue() instanceof Local) {
            logger.debug("SimpleDefs");
            Body callerBody = dn.getMethodNode().getMethod().getActiveBody();
            UnitGraph uGraph = new ExceptionalUnitGraph(callerBody);
            LocalDefs ld = new SimpleLocalDefs(uGraph);
            valDefs = ld.getDefsOfAt((Local) dn.getValue(), dn.getUnit());
            for (int i = 0; i < valDefs.size(); i++) {
                Unit u = valDefs.get(i);
                if (u == dn.getUnit()) {
                    logger.debug("Found Unit where Value defines itself!!!!");
                    valDefs.remove(i);
                }
            }
        }
        else {
            valDefs = new ArrayList<Unit>();
            logger.debug("Not SimpleDefs");
            //logger.debug("INSIDE: " + dn.getMethod().toString());
            if(dn.getMethodNode().getMethod().hasActiveBody()) {
                Body b = dn.getMethodNode().getMethod().getActiveBody();
                for (Unit u : b.getUnits()){
                    //logger.debug(u.toString());
                    if (u instanceof DefinitionStmt) {
                        DefinitionStmt defStmt = (DefinitionStmt) u;
                        Value leftOp = defStmt.getLeftOp();
                        Value rightOp = defStmt.getRightOp();
                        //Debugging print statements
                        //logger.debug("METHODLEFT:" + leftOp.toString() + " | " + leftOp.getClass().toString());
                        //logger.debug("METHODRIGHT: " + rightOp.toString() + " | " + rightOp.getClass().toString());
                        if (leftOp.equivTo(dn.getValue())) {
                            valDefs.add(u);
                        }
                    }
                }

            }
        }
        logger.debug(">>Getting defs at " + dn.getUnit().toString());
        for (Unit u : valDefs) {
            logger.debug(">>" + u.toString());
        }
        return valDefs;
    }

    public void printDOTFormat() {
    }

    public void printConstants() {
        for (IEdge edge: dg.getStartNode().getIncomingEdges()) {
            printIEdge(edge, "");
        }
    }

    public void printIEdge(IEdge edge, String indent) {
        MethodNode caller = edge.getCaller();
        MethodNode callee = edge.getCallee();

        System.out.println("----------------");
        System.out.println(indent + "CALLER: " + caller.getMethod().toString());
        System.out.println(indent + "CALLEE: " + callee.getMethod().toString());
        System.out.println(indent + "PARAMS: " + ((PPEdge) edge).getParamIdxs().toString());
        for (DefNode dn : caller.getDefNodes()) {
            System.out.println(indent + "Callsite DefNode: " + dn.getUnit().toString());
            printDefNodeChain(dn, "\t");
        }
        for (IEdge eeee : caller.getIncomingEdges()) {
            printIEdge(eeee, indent + ">");
        }
    }

    public void printDefNodeChain(DefNode dn, String indent) {
        Unit u = dn.getUnit();
        Value val = dn.getValue();
        List<DefNode> neighbours = dn.getNeighbours();



        if (neighbours.isEmpty()) {
            if (val instanceof Constant) {
                System.out.println(indent + "Found constant " + val.toString() + " in " + u.toString());
            }
            else {
                System.out.println(indent + "Resolved to " + val.toString() + " in " + u.toString());
            }
            System.out.println(indent + "\t" + "Definition found in method " + dn.getMethodNode().getMethod());
        }

        else {
            if (val instanceof NewArrayExpr) {
                System.out.println(indent + "Found array in " + u.toString());
                for (DefNode neighbour : dn.getNeighbours()) {
                    System.out.println(indent + "\t" + neighbour.getUnit().toString());
                }
                System.out.println(indent + "\t" + "Definition found in method " + dn.getMethodNode().getMethod());
            }
            else {
                System.out.println(indent + "Looking for " + val.toString() + " in " + u.toString());
                for (DefNode neighbour : dn.getNeighbours()) {
                    printDefNodeChain(neighbour, indent);
                }
            }
        }
    }

    public DefGraph getDefGraph() {
        return this.dg;
    }
}
