package kfd.data;

import java.util.ArrayList;
import java.util.List;

import soot.Value;
import soot.Unit;
import soot.SootMethod;


public class DefNode {
    private Unit unit; //The Unit where we want the definition
    private Value val; //Value who's definition we are looking for
    private MethodNode methodNode;
    private DefNode predecessor;
    private List<DefNode> neighbours;

    public DefNode(Unit _unit, Value _val, DefNode _predecessor, MethodNode _methodNode) {
        this.unit = _unit;
        this.val = _val;
        this.predecessor = _predecessor;
        this.methodNode = _methodNode;
        this.neighbours = new ArrayList<DefNode>();
    }

    public Unit getUnit() {
        return this.unit;
    }
    public Value getValue() {
        return this.val;
    }
    public MethodNode getMethodNode() {
        return this.methodNode;
    }
    public List<DefNode> getNeighbours() {
        return this.neighbours;
    }
    public void addNeighbour(DefNode newNode) {
        this.neighbours.add(newNode);
    }
    public DefNode createAndAddNeighbour(Unit unit, Value val) {
        DefNode newDefNode = new DefNode(unit, val, this, this.getMethodNode());
        this.addNeighbour(newDefNode);
        return newDefNode;
    }
    public DefNode createAndAddInterNeighbour(Unit unit, Value val, MethodNode methodNode) {
        DefNode newDefNode = new DefNode(unit, val, this, methodNode);
        this.addNeighbour(newDefNode);
        return newDefNode;
    }

    
}

