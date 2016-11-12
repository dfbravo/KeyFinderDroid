package kfd.data;

import java.util.List;

import soot.SootMethod;

public interface IEdge{
    public MethodNode getCaller();
    public MethodNode getCallee();
    public List<IEdge> getNeighbours();
    public void addNeighbour(IEdge edge);
}
