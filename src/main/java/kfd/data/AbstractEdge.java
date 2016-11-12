package kfd.data;

import java.util.List;
import java.util.ArrayList;

public abstract class AbstractEdge implements IEdge {
    protected MethodNode caller;
    protected MethodNode callee;
    protected List<IEdge> neighbours;

    public AbstractEdge(MethodNode caller, MethodNode callee) {
        this.caller = caller;
        this.callee = callee;
        this.neighbours = new ArrayList<IEdge>();
    }
    public MethodNode getCaller() {
        return this.caller;
    }
    public MethodNode getCallee() {
        return this.callee;
    }
    public List<IEdge> getNeighbours() {
        return this.neighbours;
    }
    public void addNeighbour(IEdge edge){
        this.neighbours.add(edge);
    }
}
