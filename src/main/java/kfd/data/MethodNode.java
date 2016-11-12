package kfd.data;

import java.util.List;
import java.util.ArrayList;

import soot.SootMethod;

public class MethodNode {
    protected List<DefNode> defNodes;
    protected List<IEdge> incomingEdges;
    protected List<IEdge> outgoingEdges;
    protected SootMethod method;
    public MethodNode(SootMethod method) {
        this.defNodes = new ArrayList<DefNode>();
        this.incomingEdges = new ArrayList<IEdge>();
        this.outgoingEdges = new ArrayList<IEdge>();
        this.method = method;
    }

    public SootMethod getMethod() {
        return this.method;
    }

    public void addDefNode(DefNode newNode) {
        this.defNodes.add(newNode);
    }

    public void addOutgoingEdge(IEdge newEdge) {
        this.outgoingEdges.add(newEdge);
    }

    public void addIncomingEdge(IEdge newEdge) {
        this.incomingEdges.add(newEdge);
    }

    public List<IEdge> getOutgoingEdges() {
        return this.outgoingEdges;
    }

    public List<IEdge> getIncomingEdges() {
        return this.incomingEdges;
    }

    public boolean hasOutgoingEdges() {
        if (outgoingEdges.isEmpty()) {
            return false;
        }
        return true;
    }

    public List<DefNode> getDefNodes() {
        return this.defNodes;
    }

}
