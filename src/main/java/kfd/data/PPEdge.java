package kfd.data;

import java.util.Set;
import java.util.List;

public class PPEdge extends AbstractEdge {
    private Set<Integer> paramIdxs;

    public PPEdge(MethodNode caller, MethodNode callee, Set<Integer> paramIdxs) {
        super(caller,callee);
        this.paramIdxs = paramIdxs;
    }

    public Set<Integer> getParamIdxs() {
        return this.paramIdxs;
    }
}
