package cool;

abstract class ExpressionVisitorImpl implements Visitor {
    /* NOTE: to know about the individual visit functions
             Check Visitor.java 
    */

    private boolean isPrimitiveType(String type) {
        return Global.Constants.STRING_TYPE.equals(type)
                || Global.Constants.INT_TYPE.equals(type)
                || Global.Constants.BOOL_TYPE.equals(type);
    }

    public String visit(AST.no_expr expr) {
        return null;
    }

    public String visit(AST.assign expr) {
        String retVal = expr.e1.accept(this);
        String castVal = retVal;
        String storeID;
        if(!expr.type.equals(expr.e1.type)) {
            castVal = IRPrinter.createConvertInst(retVal, expr.e1.type, expr.type, IRPrinter.BITCAST);
        }
        if(Global.methodParams.contains(expr.name)) {
            storeID = "%" + expr.name + ".addr";
        } else {
            storeID = IRPrinter.createClassAttrGEP(Global.currentClass, "%this", expr.name);
        }
        IRPrinter.createStoreInst(castVal, storeID, Utils.getBasicTypeOrPointer(expr.type));
        return retVal;
    }

    private boolean isDefaultMethod(String methodName) {
        return "abort".equals(methodName) || "out_int".equals(methodName) || "out_string".equals(methodName) 
        || "in_int".equals(methodName) || "in_string".equals(methodName); 
    }

    private String handleDefaultMethod(AST.static_dispatch expr) {
        String def = null;
        if("abort".equals(expr.name)) {
            expr.caller.accept(this);
            def = IRPrinter.createCallInst("Object", Utils.getMangledName("Object", 
                            "abort"), "");
        } else if("length".equals(expr.name) && Global.Constants.STRING_TYPE.equals(expr.typeid)) {
            String stringReg = expr.caller.accept(this);
            String strlenReg = IRPrinter.createCallInst("i64", "strlen", "i8* " + stringReg);
            def = IRPrinter.createConvertInst(strlenReg,"i64","i32",IRPrinter.TRUNC);
        }
        return def;
    }

    public String visit(AST.static_dispatch expr) {

        String caller = expr.caller.accept(this);

        String ifThenLabel = IRPrinter.getLabel("if.then",false);
        String ifElseLabel = IRPrinter.getLabel("if.else",false);
        String ifEndLabel = IRPrinter.getLabel("if.end",false);

        String cmpInst = IRPrinter.createBinaryInst(IRPrinter.EQ, caller, "null", expr.caller.type, false, false);
        IRPrinter.createCondBreak(cmpInst, ifThenLabel, ifElseLabel);
        
        IRPrinter.createLabel(ifThenLabel);
        IRPrinter.createVoidCallInst(Global.Constants.VOID_CALL_FUNCTION, "i32 "+expr.lineNo);
        IRPrinter.createCallInst("Object", Utils.getMangledName("Object", "abort"), "");
        
        IRPrinter.createBreakInst(ifEndLabel);
 
        IRPrinter.createLabel(ifElseLabel);
        IRPrinter.createBreakInst(ifEndLabel);

        
        IRPrinter.createLabel(ifEndLabel);

        String def = handleDefaultMethod(expr);
        if(def!=null) {
            return def;
        }
        String mthdClass = Utils.getNearestParentWithMethod(expr.typeid, expr.name);
        if(isPrimitiveType(mthdClass)) {
            // TODO
        }
        if(!mthdClass.equals(expr.caller.type)) {
            // TODO : check if the cast is for pointers
            caller = IRPrinter.createConvertInst(caller, expr.caller.type, mthdClass, IRPrinter.BITCAST);
        }

        StringBuilder builder = new StringBuilder();
        builder.append(Utils.getBasicTypeOrPointer(mthdClass)).append(" ").append(caller);
        for(AST.expression argument : expr.actuals) {
            builder.append(", ");
            builder.append(Utils.getBasicTypeOrPointer(argument.type));
            builder.append(" ");
            String pointerReg = argument.accept(this);
        //    String loadReg = IRPrinter.createLoadInst(pointerReg, argument.type);
            builder.append(pointerReg);
        }
        String returnValue = IRPrinter.createCallInst(expr.type, Utils.getMangledName(mthdClass, 
                            expr.name), builder.toString());
        return returnValue;
    }

    public String visit(AST.cond expr) { // incomplete TODO: what is incomplete?
        String ifThenLabel = IRPrinter.getLabel("if.then",false);
        String ifElseLabel = IRPrinter.getLabel("if.else",false);
        String ifEndLabel = IRPrinter.getLabel("if.end",false);
        String resultType = Global.inheritanceGraph.getJoinOf(expr.ifbody.type,expr.elsebody.type);
        String retVal = IRPrinter.createAlloca(resultType);

        String cmpInst = expr.predicate.accept(this);
        
        IRPrinter.createCondBreak(cmpInst, ifThenLabel, ifElseLabel);
        
        IRPrinter.createLabel(ifThenLabel);
        String ifBody = expr.ifbody.accept(this);
        if(!resultType.equals(expr.ifbody.type)) {
            ifBody = IRPrinter.createConvertInst(ifBody, expr.ifbody.type, resultType, IRPrinter.BITCAST);
        }
        if(Utils.isPrimitiveType(resultType)) {
            IRPrinter.createStoreInst(ifBody, retVal, Utils.getBasicType(resultType));
        } else {
            String loadVal = IRPrinter.createLoadInst(ifBody, Utils.getBasicType(resultType));
            IRPrinter.createStoreInst(loadVal, retVal, Utils.getBasicType(resultType));
        }
        IRPrinter.createBreakInst(ifEndLabel);
        
        IRPrinter.createLabel(ifElseLabel);
        String ifElse = expr.elsebody.accept(this);
        if(!resultType.equals(expr.elsebody.type)) {
            ifElse = IRPrinter.createConvertInst(ifElse, expr.elsebody.type, resultType, IRPrinter.BITCAST);
        }
        if(Utils.isPrimitiveType(resultType)) {
            IRPrinter.createStoreInst(ifElse, retVal, Utils.getBasicType(resultType));
        } else {
            String loadVal = IRPrinter.createLoadInst(ifElse, Utils.getBasicType(resultType));
            IRPrinter.createStoreInst(loadVal, retVal, Utils.getBasicType(resultType));
        }
        
        IRPrinter.createBreakInst(ifEndLabel);
        IRPrinter.createLabel(ifEndLabel);

        
        if(Utils.isPrimitiveType(resultType)) {
            return IRPrinter.createLoadInst(retVal, Utils.getBasicType(resultType));
        } else {
            return retVal;
        }
    }

    public String visit(AST.loop expr) { // incomplete
        String whileCondLabel = IRPrinter.getLabel("while.cond",false);
        String whileBodyLabel = IRPrinter.getLabel("while.body",false);
        String whileEndLabel = IRPrinter.getLabel("while.end",false);
        IRPrinter.createBreakInst(whileCondLabel);

        IRPrinter.createLabel(whileCondLabel);

        String whilePredicate = expr.predicate.accept(this);


        IRPrinter.createCondBreak(whilePredicate,whileBodyLabel,whileEndLabel);

        IRPrinter.createLabel(whileBodyLabel);
        String whileBody = expr.body.accept(this);
        IRPrinter.createBreakInst(whileCondLabel);

        IRPrinter.createLabel(whileEndLabel);
        return "null";

    }

    public String visit(AST.block expr) {
        String returnValue = null;
        for(AST.expression exprInBlock : expr.l1) {
            returnValue = exprInBlock.accept(this);
        }
        return returnValue;
    }   

    public String visit(AST.new_ expr) {
        if(isPrimitiveType(expr.type)) {
            return Utils.getDefaultValue(expr.type);
        }
        String bytesToAllocate = ""+Global.classSizeMap.get(expr.type);
        String storeRegisterForCall = IRPrinter.createMallocInst(bytesToAllocate);
        String returnValue = IRPrinter.createConvertInst(storeRegisterForCall, "i8*", 
                                        expr.typeid, IRPrinter.BITCAST);
        IRPrinter.createVoidCallInst(Utils.getMangledName(expr.typeid, expr.typeid), 
                                Utils.getStructName(expr.typeid)+ "* " + returnValue);
        return returnValue;
    }

    public String visit(AST.isvoid expr) {
        String op = expr.e1.accept(this);
        return IRPrinter.createBinaryInst(IRPrinter.EQ, op, "null", expr.type, false, false);
    }

    public String visit(AST.plus expr) {
        String op1 = expr.e1.accept(this);
        String op2 = expr.e2.accept(this);

        return IRPrinter.createBinaryInst(IRPrinter.ADD, op1, op2, expr.type, false, true); // TODO - set flags correctly
    }

    public String visit(AST.sub expr) {
        String op1 = expr.e1.accept(this);
        String op2 = expr.e2.accept(this);
        return IRPrinter.createBinaryInst(IRPrinter.SUB, op1, op2, expr.type, false, true); // TODO - set flags correctly
    }
    
    public String visit(AST.mul expr) {
        String op1 = expr.e1.accept(this);
        String op2 = expr.e2.accept(this);
        return IRPrinter.createBinaryInst(IRPrinter.MUL, op1, op2, expr.type, false, true); // TODO - set flags correctly
    }
    
    public String visit(AST.divide expr) {
        String op1 = expr.e1.accept(this);
        String op2 = expr.e2.accept(this);
        
        String ifThenLabel = IRPrinter.getLabel("if.then",false);
        String ifElseLabel = IRPrinter.getLabel("if.else",false);
        String ifEndLabel = IRPrinter.getLabel("if.end",false);

        String cmpInst = IRPrinter.createBinaryInst(IRPrinter.EQ, op2, "0", Global.Constants.INT_TYPE, false, false);;
        IRPrinter.createCondBreak(cmpInst, ifThenLabel, ifElseLabel);
        
        IRPrinter.createLabel(ifThenLabel);
        IRPrinter.createVoidCallInst(Global.Constants.DIVIDE_BY_ZERO_FUNCTION, "i32 "+expr.lineNo);
        IRPrinter.createCallInst("Object", Utils.getMangledName("Object", "abort"), "");
        
        IRPrinter.createBreakInst(ifEndLabel);
 
        IRPrinter.createLabel(ifElseLabel);
        IRPrinter.createBreakInst(ifEndLabel);

        
        IRPrinter.createLabel(ifEndLabel);

        return IRPrinter.createBinaryInst(IRPrinter.DIV, op1, op2, expr.type, false, false); // TODO - set flags correctly
    }
    
    public String visit(AST.comp expr) {
        String op = expr.e1.accept(this);
        return IRPrinter.createBinaryInst(IRPrinter.XOR, op, "true", expr.e1.type, false, false);
    }
    
    public String visit(AST.lt expr) {
        String op1 = expr.e1.accept(this);
        String op2 = expr.e2.accept(this);
        return IRPrinter.createBinaryInst(IRPrinter.SLT, op1, op2, expr.e1.type, false, false); // TODO - set flags correctly
    }
    
    public String visit(AST.leq expr) {
        String op1 = expr.e1.accept(this);
        String op2 = expr.e2.accept(this);
        return IRPrinter.createBinaryInst(IRPrinter.SLE, op1, op2, expr.e1.type, false, false); // TODO - set flags correctly
    }
    
    public String visit(AST.eq expr) {
        String op1 = expr.e1.accept(this);
        String op2 = expr.e2.accept(this);
        return IRPrinter.createBinaryInst(IRPrinter.EQ, op1, op2, expr.e1.type, false, false); // TODO - set flags correctly
    }
    
    public String visit(AST.neg expr) {
        String op = expr.e1.accept(this);
        return IRPrinter.createBinaryInst(IRPrinter.SUB, "0", op, expr.type, false, true); // TODO - set flags correctly
    }
    
    public String visit(AST.object expr) {
        if("self".equals(expr.name)) {
            return "%this";
        }
        if(Global.methodParams.contains(expr.name)) {
            // return "%"+expr.name+".addr";
            return IRPrinter.createLoadInst("%"+expr.name+".addr", Utils.getBasicTypeOrPointer(expr.type));
        }
        String objectPointer = IRPrinter.createClassAttrGEP(Global.currentClass,"%this",expr.name);
        if(isPrimitiveType(expr.type)) {
            objectPointer = IRPrinter.createLoadInst(objectPointer, Utils.getBasicType(expr.type));
        }
        else {
            objectPointer = IRPrinter.createLoadInst(objectPointer, Utils.getBasicType(expr.type)+"*");
        }
        return objectPointer;
    }
    
    public String visit(AST.int_const expr) {
        return String.valueOf(expr.value);
    }
    
    public String visit(AST.string_const expr) {
        return IRPrinter.createStringGEP(expr.value);
    }
    
    public String visit(AST.bool_const expr) {
        if(expr.value) return "1";
        else return "0";
    }

    /* Functions below this are meant to be empty, will not be used */

    public String visit(AST.typcase expr) {
        return null;
    }

    public String visit(AST.let expr) {
        return null;
    }

    public String visit(AST.branch expr) {
        return null;
    }   

    public String visit(AST.dispatch expr) {
        return null;
    }

}
