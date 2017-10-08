package cool;

import java.lang.StringBuilder;
import java.util.Map;
import java.util.HashMap;

class VisitorImpl extends ExpressionVisitorImpl {
    /* NOTE: to know about the individual visit functions
             Check Visitor.java 
    */

    private String createClassAttrGEP(String className, String classRegister, String at) {
        String gepRegister = "%"+Global.registerCounter;
        Global.registerCounter++;
        String structName = Utils.getStructName(className);
        StringBuilder builder = new StringBuilder(IRPrinter.INDENT);
        builder.append(gepRegister)
        .append(" = getelementptr inbounds ").append(structName).append(", ")
        .append(structName).append("* ").append(classRegister).append(",")
        .append(Global.classToVariableToIndexListMap.get(className).get(at));
        
        Global.out.println(builder.toString());
        return gepRegister;
    }

    private String createStringGEP(String str) {
        if(!Global.stringConstantToRegisterMap.containsKey(str))
            return null;
        String gepRegister = "%"+Global.registerCounter;
        Global.registerCounter++;

        StringBuilder builder = new StringBuilder(IRPrinter.INDENT);

        builder.append(gepRegister)
        .append(" = getelementptr inbounds [").append(str.length()+1).append(" x i8], [")
        .append(str.length()+1).append(" x i8]* ").append(Global.stringConstantToRegisterMap.get(str))
        .append(", i32 0, i32 0");
        Global.out.println(builder.toString());

        return gepRegister;
    }

    private void printStringConstants() {
        StringBuilder structBuilder = new StringBuilder();
        if(!Global.stringConstantToRegisterMap.containsKey("")) {
            Global.stringConstantToRegisterMap.put("", "@.str."+Global.stringRegisterCounter);
            Global.stringRegisterCounter++;
        }
        for(Map.Entry<String,String> entry: Global.stringConstantToRegisterMap.entrySet()) {
            structBuilder.setLength(0);
            structBuilder.append(entry.getValue()).append(" = private unnamed_addr constant [")
            .append(entry.getKey().length()+1).append(" x i8] c\"").append(entry.getKey()).append("\\00\", align 1");
            Global.out.println(structBuilder.toString());
        }
    }

    private void generateStructs() {
        Global.out.println();
        InheritanceGraph.Node rootNode = Global.inheritanceGraph.getRootNode();
        Global.out.println(Utils.getStructName(Global.Constants.ROOT_TYPE) + " = type {}");
        Global.classToVariableToIndexListMap.put(Global.Constants.ROOT_TYPE, new HashMap<>());

        for(InheritanceGraph.Node child: rootNode.getChildren()) {
            generateStructsDFS(child);
        }
        Global.out.println();
    }

    private void generateStructsDFS(InheritanceGraph.Node node) {
        AST.class_ cl = node.getAstClass();
        StringBuilder builder = new StringBuilder(Utils.getStructName(cl.name));
        builder.append(" = type { ").append(Utils.getStructName(node.getParent().getAstClass().name));
        
        Map<String, String> variableToIndexListMap = new HashMap<>();
        Map<String, String> parentMap = Global.classToVariableToIndexListMap.get(node.getParent().getAstClass().name);
        for(Map.Entry<String, String> entry : parentMap.entrySet()) {
            variableToIndexListMap.put(entry.getKey(), " i32 0,"+entry.getValue());
        }


        if(Global.Constants.STRING_TYPE.equals(cl.name)) {
            builder.append(", i8*");
            variableToIndexListMap.put("val", " i32 0, i32 1");
        } else if(Global.Constants.INT_TYPE.equals(cl.name)) {
            builder.append(", i32");
            variableToIndexListMap.put("val", " i32 0, i32 1");
        } else if(Global.Constants.BOOL_TYPE.equals(cl.name)) {
            builder.append(", i8");
            variableToIndexListMap.put("val", " i32 0, i32 1");
        } else {
            int index = 0;
            for(AST.feature f : cl.features) {
                if(f instanceof AST.attr) {
                    index++;
                    AST.attr a = (AST.attr) f;
                    builder.append(", ").append(Utils.convertType(a));
                    variableToIndexListMap.put(a.name, " i32 0, i32 "+index);
                }
            }
        }

        builder.append(" }");
        Global.out.println(builder.toString());


        Global.classToVariableToIndexListMap.put(cl.name, variableToIndexListMap);
        
        for(InheritanceGraph.Node child: node.getChildren()) {
            generateStructsDFS(child);
        }
    }

    public void generateConstructors() {
        InheritanceGraph.Node rootNode = Global.inheritanceGraph.getRootNode();
        generateConstructorsDFS(rootNode);
    }

    public void generateConstructorsDFS(InheritanceGraph.Node node) {
        AST.class_ cl = node.getAstClass();
        Global.labelToCountMap.clear();
        Global.out.println();
        Global.registerCounter = 0;
        Global.currentClass = cl.name;
        Global.out.println("define void @" + Utils.getMangledName(cl.name, cl.name) + "(" + Utils.getStructName(cl.name) + "* %this) {");
        IRPrinter.createLabel("entry");
        createCallForParentConstructor(Global.currentClass, "%this");
        
        if(Global.Constants.STRING_TYPE.equals(cl.name)) {
            generateStringConstructBody();
        } else if(Global.Constants.INT_TYPE.equals(cl.name)) {
            generateIntConstructBody();
        } else if(Global.Constants.BOOL_TYPE.equals(cl.name)) {
            generateBoolConstructBody();
        } else {
            for(AST.feature f : cl.features) {
                if(f instanceof AST.attr) {
                    AST.attr a = (AST.attr) f;
                    a.accept(this);
                }
            }
        } 

        Global.out.println(IRPrinter.INDENT+"ret void");
        Global.out.println("}");
        for(InheritanceGraph.Node child: node.getChildren()) {
            generateConstructorsDFS(child);
        }
    }

    private void generateStringConstructBody() {
        String gepRegister = createClassAttrGEP(Global.Constants.STRING_TYPE, "%this", "val");
        IRPrinter.createStoreInst(createStringGEP(""), gepRegister, "i8*");
    }

    private void generateIntConstructBody() {
        String gepRegister = createClassAttrGEP(Global.Constants.INT_TYPE, "%this", "val");
        IRPrinter.createStoreInst("0", gepRegister, "i32");
    }

    private void generateBoolConstructBody() {
        String gepRegister = createClassAttrGEP(Global.Constants.BOOL_TYPE, "%this", "val");
        IRPrinter.createStoreInst("0", gepRegister, "i8");
    }

    private void generateDefaultMethods() {

        // String set method
        Global.registerCounter = 0;
        Global.out.println();
        Global.out.println("define void @" + Utils.getMangledName(Global.Constants.STRING_TYPE, "set") 
            + "(" + Utils.getStructName(Global.Constants.STRING_TYPE) + "* %this, i8* %s) {");
        Global.out.println("entry:");
        String gepRegister = createClassAttrGEP(Global.Constants.STRING_TYPE, "%this", "val");
        IRPrinter.createStoreInst("%s", gepRegister, "i8*");
        Global.out.println(IRPrinter.INDENT+"ret void");
        Global.out.println("}");

        // Int set method
        Global.registerCounter = 0;
        Global.out.println();
        Global.out.println("define void @" + Utils.getMangledName(Global.Constants.INT_TYPE, "set") 
            + "(" + Utils.getStructName(Global.Constants.INT_TYPE) + "* %this, i32 %s) {");
        Global.out.println("entry:");
        gepRegister = createClassAttrGEP(Global.Constants.INT_TYPE, "%this", "val");
        IRPrinter.createStoreInst("%s", gepRegister, "i32");
        Global.out.println(IRPrinter.INDENT+"ret void");
        Global.out.println("}");

        // Bool set method
        Global.registerCounter = 0;
        Global.out.println();
        Global.out.println("define void @" + Utils.getMangledName(Global.Constants.BOOL_TYPE, "set") 
            + "(" + Utils.getStructName(Global.Constants.BOOL_TYPE) + "* %this, i8 %s) {");
        Global.out.println("entry:");
        gepRegister = createClassAttrGEP(Global.Constants.BOOL_TYPE, "%this", "val");
        IRPrinter.createStoreInst("%s", gepRegister, "i8");
        Global.out.println(IRPrinter.INDENT+"ret void");
        Global.out.println("}");

    }

    public void visit(AST.program prog) {

        // preparing inheritance graph
        Global.inheritanceGraph = new InheritanceGraph();
        for(AST.class_ cl: prog.classes) {
            Global.inheritanceGraph.addClass(cl);
        }

        Global.inheritanceGraph.update();
        
        printStringConstants();
        generateStructs();
        generateConstructors();
        generateDefaultMethods();

    }

    public void visit(AST.class_ cl) {
        
    }

    public String getDefaultValue(String type) {
        if(Global.Constants.INT_TYPE.equals(type)) {
            return "0";
        } else if(Global.Constants.BOOL_TYPE.equals(type)) {
            return "0";
        } else if(Global.Constants.STRING_TYPE.equals(type)) {
            return createStringGEP("");
        } else {
            return "undef";
        }
    }

    private void createCallForConstructor(String className, String reg) {
        StringBuilder builder = new StringBuilder(IRPrinter.INDENT);
        builder.append("call void @").append(Utils.getMangledName(className, className))
        .append("(").append(Utils.getStructName(className)).append("* ").append(reg).append(")");
        Global.out.println(builder.toString());
    }

    private void createCallForParentConstructor(String className, String childReg) {
        String parentType = Global.inheritanceGraph.getParentClassName(className);
        if(parentType!=null) {
            String bitcastRegister = IRPrinter.createConvertInst(childReg, Global.currentClass, 
                parentType, IRPrinter.BITCAST);
            createCallForConstructor(parentType, bitcastRegister);
        }
    }

    public void visit(AST.attr at) {
        String gepRegister = createClassAttrGEP(Global.currentClass, "%this", at.name);
        String valueRegister = at.value.accept(this);
        if(Global.inheritanceGraph.isRestrictedInheritanceClass(at.typeid)) {
            if(valueRegister==null) {
                createCallForConstructor(at.typeid, gepRegister);
            } else {
                // call function
                IRPrinter.createStoreInst(valueRegister, gepRegister, at.typeid);
            }
        } else {
            if(valueRegister==null) {
                IRPrinter.createStoreInst(getDefaultValue(at.typeid), gepRegister, at.typeid);
            } else {
                IRPrinter.createStoreInst(valueRegister, gepRegister, at.typeid);
            }
        }
    }

    public void visit(AST.method mthd) {
        
    }

    public void visit(AST.formal fm) {
        
    }

}
