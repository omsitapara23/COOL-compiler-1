package cool;

class IRPrinter {

    public static final String INDENT = "  ";

    public static final String BITCAST = "bitcast";
    public static final String TRUNC = "trunc";
    public static final String ADD = "add";
    public static final String SUB = "sub";
    public static final String MUL = "mul";
    public static final String DIV = "sdiv";
    public static final String ZEXT = "zext";
    public static final String SLT = "icmp slt";
    public static final String SGE = "icmp sge";
    public static final String SGT = "icmp sgt";
    public static final String SLE = "icmp sle";
    public static final String EQ = "icmp eq";
    public static final String XOR = "xor";
    public static final String UNDEF = "undef";


    private static int getAlign(String type) {
        if(type.length() == 0) {
            return -1;
        }
        String checkPointerType = type.substring(type.length()-1);
        if("*".equals(checkPointerType)) {
            return 8;
        }
        return 4;
    }

    public static String createLoadInst(String mem, String type) {
        StringBuilder builder = new StringBuilder(INDENT);
        String storeRegister = "%"+Global.registerCounter;
        Global.registerCounter++;
        builder.append(storeRegister);
        builder.append(" = load ").append(type);
        builder.append(", ").append(type+"* ");
        builder.append(mem).append(", align ");
        builder.append(getAlign(type));
        Global.out.println(builder.toString());
        return storeRegister;
    }

    public static void createStoreInst(String reg, String mem, String type) {
        StringBuilder builder = new StringBuilder(INDENT);
        builder.append("store ").append(type).append(" ");
        builder.append(reg).append(", ");
        builder.append(type+"*").append(" ");
        builder.append(mem).append(", align ");
        builder.append(getAlign(type));
        Global.out.println(builder.toString());
    }

    public static void createDoublePointerStoreInst(String reg, String mem, String type) {
        StringBuilder builder = new StringBuilder(INDENT);
        type = Utils.getStructName(type);
        builder.append("store ").append(type).append("* ");
        builder.append(reg).append(", ");
        builder.append(type).append("** ");
        builder.append(mem).append(", align ");
        builder.append(getAlign(type));
        Global.out.println(builder.toString());
    }

    public static String createBinaryInst(String opType, String op1, String op2, 
                                            String type, boolean nuw, boolean nsw) {
        StringBuilder builder = new StringBuilder(INDENT);
        type = Utils.getBasicTypeOrPointer(type);
        String storeRegister = "%"+Global.registerCounter;
        Global.registerCounter++;
        builder.append(storeRegister);
        builder.append(" = ").append(opType).append(" ");
        if(nuw)
            builder.append("nuw ");
        if(nsw)
            builder.append("nsw ");
        builder.append(type);
        builder.append(" ").append(op1).append(", ");
        builder.append(op2);
        Global.out.println(builder.toString());
        return storeRegister;
    }

    private static boolean isLLVMPrimitive(String type) {
        return "i8*".equals(type) || "i32".equals(type) || "i64".equals(type) || "i8".equals(type) || "i1".equals(type);
    }

    public static String createConvertInst(String reg, String exprFromType, String exprToType, String convertType) {
        // TODO: verify if we need pointer everywhere
        StringBuilder builder = new StringBuilder(INDENT);
        if(!isLLVMPrimitive(exprFromType))
            exprFromType = Utils.getStructName(exprFromType) + "*";
        if(!isLLVMPrimitive(exprToType))
            exprToType = Utils.getStructName(exprToType) + "*";
        String storeRegister = "%"+Global.registerCounter;
        Global.registerCounter++;
        builder.append(storeRegister);
        builder.append(" = ").append(convertType);
        builder.append(" ").append(exprFromType);
        builder.append(" ").append(reg).append(" to ");
        builder.append(exprToType);
        Global.out.println(builder.toString());
        return storeRegister;
    }

    public static void createBreakInst(String label) {
        StringBuilder builder = new StringBuilder(INDENT);
        builder.append("br label %").append(label);
        Global.out.println(builder.toString());
    }

    public static void createCondBreak(String reg, String label1, String label2) {
        StringBuilder builder = new StringBuilder(INDENT);
        builder.append("br i1 ");
        builder.append(reg).append(", ");
        builder.append("label %").append(label1);
        builder.append(", label %").append(label2);
        Global.out.println(builder.toString());
    }

    public static String createLabel(String label) {
        StringBuilder builder = new StringBuilder("\n");
        label = getLabel(label,true);
        builder.append(label).append(":");
        Global.out.println(builder.toString());
        return label;
    }

    public static String getLabel(String label, boolean isExisting) {
        String finalLabel = label;
        if(isExisting) {
            return finalLabel;
        }
        if(Global.labelToCountMap.containsKey(label)) {
            int value = Global.labelToCountMap.get(label);
            finalLabel = label + "." + value;
            Global.labelToCountMap.put(label, Global.labelToCountMap.get(label) + 1);
        }
        else {
            finalLabel = label;
            Global.labelToCountMap.put(label,1); // TODO : check this
        }
        return finalLabel;
    }

    public static String createPHINode(String type, String v1, String label1, String v2, String label2) {
        StringBuilder builder = new StringBuilder(INDENT);
        type = Utils.getBasicTypeOrPointer(type);
        String storeRegister = "%"+Global.registerCounter;
        Global.registerCounter++;
        builder.append(storeRegister);
        builder.append(" = phi ").append(type);
        builder.append(" [ ").append(v1).append(", %");
        builder.append(label1).append(" ] , [ ");
        builder.append(v2).append(", %");
        builder.append(label2).append(" ]");
        Global.out.println(builder.toString());
        return storeRegister;
    }

    public static void createVoidCallInst(String callee, String args) {
        StringBuilder builder = new StringBuilder(INDENT);
        builder.append("call void @").append(callee);
        builder.append("(").append(args).append(")");
        Global.out.println(builder.toString());
    }

    public static String createCallInst(String type, String callee, String args) {
        StringBuilder builder = new StringBuilder(INDENT);
        type = Utils.getBasicTypeOrPointer(type);
        String storeRegisterForCall = "%"+Global.registerCounter;
        Global.registerCounter++;
        builder.append(storeRegisterForCall);
        builder.append(" = call ").append(type);
        builder.append(" @").append(callee);
        builder.append("(").append(args).append(")");
        Global.out.println(builder.toString());
        return storeRegisterForCall;
    }

    public static String createMallocInst(String bitCount) {
        StringBuilder builder = new StringBuilder(INDENT);
        String storeRegister = "%"+Global.registerCounter;
        Global.registerCounter++;
        builder.append(storeRegister);
        builder.append(" = call noalias i8* @malloc(i64 ");
        builder.append(bitCount).append(")");
        Global.out.println(builder.toString());
        return storeRegister;
    }


    public static String createStringGEP(String str) {
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

    public static String createClassAttrGEP(String className, String classRegister, String at) {
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

    public static String createTypeNameGEP(String classRegister) {
        // NOTE: classRegister should already be bit casted to Object 
        String gepRegister = "%"+Global.registerCounter;
        Global.registerCounter++;
        String structName = Utils.getStructName(Global.Constants.ROOT_TYPE);
        StringBuilder builder = new StringBuilder(IRPrinter.INDENT);
        builder.append(gepRegister)
        .append(" = getelementptr inbounds ").append(structName).append(", ")
        .append(structName).append("* ").append(classRegister).append(", i32 0, i32 0");
        Global.out.println(builder.toString());
        return gepRegister;
    }

    public static String createAlloca(String className) {
        String gepRegister = "%"+Global.registerCounter;
        Global.registerCounter++;
        String structName = Utils.getBasicType(className);
        StringBuilder builder = new StringBuilder(IRPrinter.INDENT);
        builder.append(gepRegister).append(" = alloca ").append(structName).append(", align 8");
        Global.out.println(builder.toString());
        return gepRegister;
    }

    public static String createAlloca(String className, String regName) {
        String gepRegister = "%"+regName;
        // String structName = Utils.getStructName(className);
        StringBuilder builder = new StringBuilder(IRPrinter.INDENT);
        builder.append(gepRegister).append(" = alloca ").append(className).append(", align 8");
        Global.out.println(builder.toString());
        return gepRegister;
    }

    public static void createCallForConstructor(String className, String reg) {
        StringBuilder builder = new StringBuilder(IRPrinter.INDENT);
        builder.append("call void @").append(Utils.getMangledName(className, className))
        .append("(").append(Utils.getStructName(className)).append("* ").append(reg).append(")");
        Global.out.println(builder.toString());
    }

    public static String createAbortForPrimitive(String className) {
        String loadNameReg = IRPrinter.createStringGEP(className);
        String arg1 = IRPrinter.createStringGEP("%s");
        String arg2 = IRPrinter.createStringGEP(Global.Constants.ABORT_MESSAGE);
        Global.out.println(IRPrinter.INDENT+"%"+Global.registerCounter+" = call i32 (i8*, ...) @printf(i8* "+arg1+", i8* "+arg2+")");
        Global.registerCounter++;
        Global.out.println(IRPrinter.INDENT+"%"+Global.registerCounter+" = call i32 (i8*, ...) @printf(i8* "+arg1+", i8* "+loadNameReg+")");
        Global.registerCounter++;
        arg2 = IRPrinter.createStringGEP("\n");
        Global.out.println(IRPrinter.INDENT+"%"+Global.registerCounter+" = call i32 (i8*, ...) @printf(i8* "+arg1+", i8* "+arg2+")");
        Global.registerCounter++;
        Global.out.println(IRPrinter.INDENT+"call void @exit(i32 0)");

        String bytesToAllocate = ""+Global.classSizeMap.get(Global.Constants.ROOT_TYPE);
        String storeRegisterForCall = IRPrinter.createMallocInst(bytesToAllocate);
        String returnValue = IRPrinter.createConvertInst(storeRegisterForCall, "i8*", 
                                        Global.Constants.ROOT_TYPE, IRPrinter.BITCAST);
        IRPrinter.createVoidCallInst(Utils.getMangledName(Global.Constants.ROOT_TYPE, Global.Constants.ROOT_TYPE), 
                                Utils.getStructName(Global.Constants.ROOT_TYPE)+ "* " + returnValue);
        return returnValue;
    }

}