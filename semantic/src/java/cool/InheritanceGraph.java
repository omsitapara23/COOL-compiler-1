package cool;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.lang.StringBuilder;
import java.util.Collections;

// TODO: Add features of Object class
// TODO: Add IO in graph and its features
public class InheritanceGraph {

    private static final String ROOT_CLASS_NAME = "Object";
    private static final int ROOT_CLASS_INDEX = 0;
    private static AST.class_ ROOT_AST_CLASS = new AST.class_(InheritanceGraph.ROOT_CLASS_NAME, null, null, null, 0);
    private static Node ROOT_AST_NODE = new Node(InheritanceGraph.ROOT_AST_CLASS, InheritanceGraph.ROOT_CLASS_INDEX);

    public static String MAIN_CLASS_NAME = "Main";

    public static Map<String,Integer> classNameToIndexMap;
    private List<Node> graph;
    private boolean hasMain;

    public InheritanceGraph() {
        
        graph = new ArrayList<>();
        
        classNameToIndexMap = new HashMap<>();

        hasMain = false;

        addBaseClasses();
    }

    private void addBaseClasses() {
        classNameToIndexMap.put(InheritanceGraph.ROOT_CLASS_NAME, InheritanceGraph.ROOT_CLASS_INDEX);
        graph.add(InheritanceGraph.ROOT_AST_NODE);
        
        classNameToIndexMap.put("Int", -1);
        classNameToIndexMap.put("Bool", -1);
        classNameToIndexMap.put("String", -1);

        // Adding IO
        // TODO: add IO functions
        List<AST.feature> ioFeatures = new ArrayList<>();
        AST.class_ ioAstClass = new AST.class_("IO", null, ROOT_CLASS_NAME, ioFeatures, 0);
        Node ioNode = new Node(ioAstClass, 0);

        ioNode.setParent(InheritanceGraph.ROOT_AST_NODE);
        InheritanceGraph.ROOT_AST_NODE.addChild(ioNode);
        
        classNameToIndexMap.put("IO", graph.size());
        graph.add(ioNode);

    }

    public void addClass(AST.class_ astClass) {
        if(classNameToIndexMap.containsKey(astClass.name)) {
            GlobalData.errors.add(new Error(GlobalData.filename, astClass.getLineNo(),new StringBuilder().append("class '")
                .append(astClass.name).append("' has been redefined").toString()));
            return;
        } else if(isRestrictedClass(astClass.name)) {
            GlobalData.errors.add(new Error(GlobalData.filename, astClass.getLineNo(),new StringBuilder().append("Cannot redefine base class '")
                .append(astClass.name).append("'").toString()));
            return;
        }
        classNameToIndexMap.put(astClass.name, graph.size());
        graph.add(new Node(astClass, graph.size()));
        if(InheritanceGraph.MAIN_CLASS_NAME.equals(astClass.name)) {
            hasMain = true;
        }

    }

    public boolean hasMain() {
        return hasMain;
    }

    public Node getRootNode() {
        return ROOT_AST_NODE;
    }

    public boolean hasClass(String className) {
        return classNameToIndexMap.containsKey(className);
    }

    public void analyze() {
        parentUpdatePass();

        if(!hasMain()) {
            // TODO: what to do for line number
            GlobalData.errors.add(new Error(GlobalData.filename, 0,"'Main' class is missing."));
        }

        List<Stack<Node>> cycles = getCyclesInGraph();
        if(!cycles.isEmpty()) {
            for(Stack<Node> cycle: cycles) {
                StringBuilder errorString = new StringBuilder();
                errorString.append("Classes have cyclic dependency: ");
                int size = cycle.size();
                StringBuilder cyclePath = new StringBuilder();
                for(int i=0; i<size-1; i++) {
                    cyclePath.append(cycle.pop().getAstClass().name).append(" -> ");
                }
                AST.class_ lastClass = cycle.pop().getAstClass();
                String lastClassName = lastClass.name;
                errorString.append(lastClassName).append(" -> ");
                errorString.append(cyclePath).append(lastClassName);
                GlobalData.errors.add(new Error(GlobalData.filename, lastClass.getLineNo(), errorString.toString()));
            }
        }
    }

    private boolean isRestrictedClass(String name) {
        return "IO".equals(name) || "Int".equals(name) || "String".equals(name) || "Bool".equals(name);
    }

    private boolean isRestrictedInheritanceClass(String name) {
        return "Int".equals(name) || "String".equals(name) || "Bool".equals(name);
    }

    private void parentUpdatePass() {
        for(Node cl: graph) {
            if(cl.getAstClass().parent!=null) {
                if(isRestrictedInheritanceClass(cl.getAstClass().parent)) {
                    GlobalData.errors.add(new Error(GlobalData.filename, cl.getAstClass().getLineNo(), 
                                new StringBuilder().append("Cannot inherit base class '").append(cl.getAstClass().parent)
                                .append("'").toString()));
                } else if(classNameToIndexMap.containsKey(cl.getAstClass().parent)) {
                    int parentIndex = classNameToIndexMap.get(cl.getAstClass().parent);
                    cl.setParent(graph.get(parentIndex));
                    graph.get(parentIndex).addChild(cl);
                } else {
                    GlobalData.errors.add(new Error(GlobalData.filename, cl.getAstClass().getLineNo(), 
                                new StringBuilder().append("Inherited class '").append(cl.getAstClass().parent)
                                .append("' for '").append(cl.getAstClass().name).append("' has not been declared").toString()));
                }
            } else {
                if(!InheritanceGraph.ROOT_CLASS_NAME.equals(cl.getAstClass().name)) {
                    cl.setParent(InheritanceGraph.ROOT_AST_NODE);
                    InheritanceGraph.ROOT_AST_NODE.addChild(cl);
                }
            }
        }
    }

    private boolean getCyclesInGraphUtil(int v, List<Boolean> visited, List<Boolean> recStack, Stack<Node> cycle) {
        Node currentNode = graph.get(v);
        cycle.push(currentNode);
        if(visited.get(v) == false) {
            visited.set(v, true);
            recStack.set(v, true);
            if(currentNode.parentExists()) {
                int parentIndex = currentNode.getParent().getIndex();
                if(parentIndex != Node.NO_PARENT) {
                    if ( (!visited.get(parentIndex) && getCyclesInGraphUtil(parentIndex, visited, recStack, cycle)) 
                          || recStack.get(parentIndex) ) {
                        return true;
                    }
                }
            }
        }
        cycle.pop();
        recStack.set(v, false);
        return false;
    }

    public List<Stack<Node>> getCyclesInGraph() {

        int V = graph.size();
        List<Boolean> visited = new ArrayList<>();
        List<Boolean> recStack = new ArrayList<>();
        Stack<Node> cycle = new Stack<>();
        for(int i = 0; i < V; i++) {
            visited.add(false);
            recStack.add(false);
        }
         
         List<Stack<Node>> cycles = new ArrayList<>();
        for(int i = 0; i < V; i++)
            if (getCyclesInGraphUtil(i, visited, recStack, cycle)) {
                cycles.add(cycle);
                cycle = new Stack<>();
            }
     
        return cycles;
    }
    
    public boolean isConforming(String type1, String type2) {
        // TODO check if string corresponding to type exists
        if(type1.equals(type2)) {
            return true;
        }
        Node type1Node = graph.get(classNameToIndexMap.get(type1));
        Node type2Node = graph.get(classNameToIndexMap.get(type2));
        while(type2Node.parentExists()) {
            type2Node = type2Node.getParent();
            if(type1Node.equals(type2Node)) {
                return true;
            }
        }
        return false;
    }
    
    public String getJoinOf(String type1, String type2) {
        if(type1.equals(type2)) {
            return type1;
        }
        Node type1Node = graph.get(classNameToIndexMap.get(type1));
        Node type2Node = graph.get(classNameToIndexMap.get(type2));
        Node lca = getLCA(type1Node, type2Node);
        return lca.getAstClass().name;
    }
    
    public Node getLCA(Node node1, Node node2) {
        // TODO check if index valid 
        // TODO check that null is not referenced
        Node lca;
        List<Boolean> visited = new ArrayList<>(graph.size());
        visited.addAll(Collections.nCopies(graph.size(),Boolean.FALSE));
        while(!node1.equals(InheritanceGraph.ROOT_AST_NODE)) {
            visited.set(node1.getIndex(),true);
            node1 = node1.getParent();
        }
        do {
            if(visited.get(node2.getIndex())) {
                lca = node2;
                break;
            }
            node2 = node2.getParent();
        }while(true);
        return lca;
    }
    
  /*  private void setLevel(Node node, int level) {
        node.setLevel(level);
        if(node.getChildren() != null) { // does this ever happen?
            for(Node child : node.getChildren()) {
                setLevel(child, level+1);
            }   
        }    
    }
    
    private void traverse(Node node, Node head, int previousSection) {
        setLevel(InheritanceGraph.ROOT_AST_NODE,0);
        int currentSection = Math.floor(Math.sqrt(node.getLevel()) + 1); // TODO check!
        if(currentSection == 1) {
            node.P = 
        }
    } */

    public static class Node {

        public static final int NO_PARENT = -1;

        private AST.class_ astClass;
        private int index;
        private Node parent;
        private List<Node> children;
        private boolean isInitiated;
  //      private int level;
  //      private Node P;

        public Node(AST.class_ astClass, int index) {
            this.isInitiated = false;
            init(astClass, index);
        }

        private void init(AST.class_ astClass, int index) {
            if(isInitiated) return;
            this.astClass = astClass;
            this.index = index;
            this.children = new ArrayList<>();
            this.parent = null;
            this.isInitiated = true;
    //        this.level = -1;
    //        this.P = null;
        }

        public void addChild(Node child) {
            children.add(child);
        }

        public AST.class_ getAstClass() {
            return astClass;
        }

        public int getIndex() {
            return index;
        }

        public boolean parentExists() {
            return parent!=null;
        }

        public Node getParent() {
            return parent;
        }

        public void setParent(Node parent) {
            this.parent = parent;
        }

        public List<Node> getChildren() {
            return children;
        }
        
        public boolean equals(Node node) {
            return this.index == node.getIndex();
        }
        
   /*     public int getLevel() {
            return level;
        }
        
        public void setLevel(int levelToSet) {
            this.level = levelToSet;
        }
        
        public Node getP() {
            return this.P;
        }
        
        public Node setP(Node PToSet) {
            this.P = PToSet;
        } */
    }
}
