package kfd.data;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import soot.Unit;

public class DefGraph {
    protected MethodNode startNode;
    protected List<MethodNode> nodes;
    protected List<IEdge> edges;

    public DefGraph() {
        this.startNode = null;
        this.nodes = new ArrayList<MethodNode>();
        this.edges = new ArrayList<IEdge>();
    }

    public void setStartNode(MethodNode startNode) {
        this.startNode = startNode;
    }

    public MethodNode getStartNode() {
        return this.startNode;
    }

    public void addPPEdge(MethodNode caller, MethodNode callee, Set<Integer> paramIdxs) {
        PPEdge edge = new PPEdge(caller, callee, paramIdxs);
        this.edges.add(edge);
        this.nodes.add(caller);
        this.nodes.add(callee);
        caller.addOutgoingEdge(edge);
        callee.addIncomingEdge(edge);
    }

    public void addRVEdge(MethodNode caller, MethodNode callee, DefNode invokeNode) {
        RVEdge edge = new RVEdge(caller, callee, invokeNode);
        edges.add(edge);
        this.edges.add(edge);
        this.nodes.add(caller);
        this.nodes.add(callee);
        caller.addOutgoingEdge(edge);
        callee.addIncomingEdge(edge);
    }

    protected void addEdge() {

    }

    protected void addNode(MethodNode methodNode) {
        this.nodes.add(methodNode);
    }
}
