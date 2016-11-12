package kfd.util;

import kfd.data.MethodNode;
import kfd.data.DefGraph;
import kfd.data.RVEdge;

import kfd.data.DefNode;
import kfd.data.PPEdge;
import kfd.data.IEdge;

import java.lang.StringBuilder;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Deque;
import java.util.ArrayDeque;
import java.io.IOException;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.File;

import soot.Unit;
import soot.Value;
import soot.jimple.ReturnStmt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DOTPrinter {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private DefGraph dg;
    private List<DOTCluster> clusterList;
    private List<String> clusterIDList;
    private Map<String, List<String>> nodeLabelMap;
    private String labelFontColor;

    public DOTPrinter (DefGraph dg) {
        this.dg = dg;
        this.clusterIDList = new ArrayList<String>();
        this.nodeLabelMap = new HashMap<String,List<String>>();
        this.clusterList = new ArrayList<DOTCluster>();
    }

    private void populateDOT() {
        Deque<MethodNode> nodeStack = new ArrayDeque<MethodNode>();
        Set<MethodNode> visitedNodes = new HashSet<MethodNode>();
        //Currently a hack to make sure that DOT works
        //There is an issue if a node has too many connections to to other clusters
        for (IEdge edge : dg.getStartNode().getIncomingEdges()) {
            MethodNode caller = edge.getCaller();
            nodeStack.offer(caller);
        }

        while (! nodeStack.isEmpty()) {
            MethodNode node = nodeStack.poll();
            visitedNodes.add(node);
            DOTCluster cluster = processMethodNode(node);
            clusterList.add(cluster);
            for (IEdge edge : node.getIncomingEdges()) {
                MethodNode caller = edge.getCaller();
                int nodeClusterID = cluster.getID();
                int callerClusterID = getClusterID(caller);
                logger.debug("Processing INCOMING PPEdge between " + node.getMethod() + " and " + caller.getMethod());
                if (edge instanceof PPEdge) {
                    cluster.addPost("tail_node_" + nodeClusterID + " -> " + "head_node_" + callerClusterID + " [label=\"PARAMS=" + ((PPEdge) edge).getParamIdxs() + "\",ltail=cluster_" + nodeClusterID + ",lhead=cluster_" + callerClusterID + "];\n");
                }
                if (! visitedNodes.contains(caller)) {
                    nodeStack.offer(caller);
                }
            }
            for (IEdge edge : node.getOutgoingEdges()) {
                MethodNode callee = edge.getCallee();
                int nodeClusterID = cluster.getID();
                int calleeClusterID = getClusterID(callee);
                logger.debug("Processing OUTGOING RVEdge between " + node.getMethod() + " and " + callee.getMethod());
                if (edge instanceof RVEdge) {
                    DefNode invokeNode = ((RVEdge) edge).getInvokeNode();
                    cluster.addPost(cluster.getNodeLabel(invokeNode) + " -> " + "head_node_" + calleeClusterID + " [lhead=cluster_" + calleeClusterID + "];\n");
                }
                if (! visitedNodes.contains(callee)) {
                    nodeStack.offer(callee);
                }
            }
        }
    }

    public void printToFile(File filename) {
        populateDOT();
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                      new FileOutputStream(filename), "UTF-8"))) {
               writer.write(generateDOT());
        }
        catch (IOException x) {
            System.err.format("IOException: %s%n", x);
        }
    }

    private String generateDOT() {
        StringBuilder sb = new StringBuilder();
        sb.append("strict digraph {\n");

        for (DOTCluster cluster : clusterList) {
            sb.append(cluster.toString());
        }
        sb.append("}");
        return sb.toString();
    }

    private DOTCluster processMethodNode(MethodNode node) {
        DOTCluster cluster = new DOTCluster(getClusterID(node), node);
        List<DefNode> defNodes = node.getDefNodes();
        int clusterID = cluster.getID();
        Deque<DefNode> dnStack = new ArrayDeque<DefNode>();

        for (DefNode dn : defNodes) {
            cluster.addBody("head_node_" + clusterID + " -> " + cluster.getNodeLabel(dn) + " [style=invis];\n");
            dnStack.push(dn);
        }
        while(!dnStack.isEmpty()) {
            DefNode currentDn = dnStack.pop();
            if (currentDn.getNeighbours().isEmpty()) {
                cluster.addBody(cluster.getNodeLabel(currentDn) + " -> " + "tail_node_" + clusterID + " [style=invis];\n");
            }
            else {
                for (DefNode neighbour : currentDn.getNeighbours()) {
                    if (neighbour.getUnit() instanceof ReturnStmt) {
                        continue;
                    }
                    logger.debug("NODE: " + currentDn.getValue() + "@" + currentDn.getUnit() + " -> " + neighbour.getValue() + "@" + neighbour.getUnit() + "  label= " + currentDn.getValue().toString());

                    cluster.addBody(cluster.getNodeLabel(currentDn) + " -> " +  cluster.getNodeLabel(neighbour) + " [label=\"" + currentDn.getValue().toString() + "\"];\n");
                    //cluster.addBody(cluster.getNodeLabel(currentDn), cluster.getNodeLabel(neighbour), "[label=\"" + currentDn.getValue().toString() + "\",fontcolor=" + labelFontColor +"];");
                    dnStack.push(neighbour);
                }
            }
        }
        return cluster;
    }

    private int getClusterID(MethodNode methodNode) {
        String methodName = methodNode.getMethod().toString();
        if (! clusterIDList.contains(methodName)) {
            clusterIDList.add(methodName);
        }
        return clusterIDList.indexOf(methodName);
    }

   

    private class DOTCluster {
        private StringBuilder pre;
        private StringBuilder body;
        private StringBuilder post;
        private int clusterID;
        private String label;
        private List<String> nodeLabelList;

        public DOTCluster(int id, MethodNode methodNode) {
            this.pre = new StringBuilder();
            this.body = new StringBuilder();
            this.post = new StringBuilder();
            this.clusterID = id;
            this.label = methodNode.getMethod().toString().replaceAll("\"","\\\\\"");
            this.nodeLabelList = new ArrayList<String>();
            body.append("label=\"" + label + "\";\n");
            body.append("head_node_" + clusterID + "[shape=point,style=invis,constraint=false];\n");
            body.append("tail_node_" + clusterID + "[shape=point,style=invis,constraint=false];\n");
        }


        public void addPre(String str) {
            this.pre.append(str);
        }
        public void addBody(String str) {
            this.body.append(str);
        }
        public void addPost(String str) {
            this.post.append(str);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(this.pre);
            sb.append("compound=true subgraph cluster_" + this.clusterID + " {\n");
            for (int i = 0; i < this.nodeLabelList.size(); i++) {
                String nodeLabel = nodeLabelList.get(i);
                sb.append("c" + this.clusterID + "_n" + i + " [label=\"" + nodeLabel.replaceAll("\"", "\\\"") + "\"];\n");
            }
            sb.append(this.body);
            sb.append("}\n");
            sb.append(this.post);
            return sb.toString();
        }

        public int getID() {
            return this.clusterID;
        }
        public String getNodeLabel(DefNode dn) {
            String unitStr = dn.getUnit().toString().replaceAll("\"","\\\\\"");
            if (! this.nodeLabelList.contains(unitStr)) {
                this.nodeLabelList.add(unitStr);
            }

            return "c" + this.clusterID + "_n" + this.nodeLabelList.indexOf(unitStr);
        }


        //@Override
        //    public int hashCode() {
        //        int prime = 31;
        //        int result = 1;
        //        result = prime * result + pre.hashCode();
        //        result = prime * result + body.hashCode();
        //        result = prime * result + post.hashCode();
        //        result = prime * result + clusterID.hashCode();
        //        result = prime * result + label.hashCode();
        //    }

        //@Override
        //    public boolean equals(Object obj) {
        //        if (this == obj)
        //            return true
        //        if (obj instanceof getClass()) {
        //            DOTCluster other = (DOTCluster) obj;

        //        }
        //    }
    }

}
