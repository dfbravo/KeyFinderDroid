package kfd.data; 

import java.util.Set;
import java.util.List;

public class RVEdge extends AbstractEdge {
    private DefNode invokeNode;

    public RVEdge(MethodNode caller, MethodNode callee, DefNode invokeNode) {
        super(caller, callee);
        this.invokeNode = invokeNode;
    }

    public DefNode getInvokeNode() {
        return this.invokeNode;
    }
}
