package ir;

import core.*;
import parser.nodes.Node;
import java.util.*;

// Walks the AST and emits a flat list of IRInstructions.
// Complex expressions are broken into temporaries: t0, t1, t2...
public class IRGenerator {

    private final List<IRInstruction> instructions = new ArrayList<>();
    private int tempCount  = 0;
    private int labelCount = 0;

    public IRGenerator(CompilerResult result) { }

    public static CompilerResult generate(CompilerResult semResult) {
        CompilerResult out = new CompilerResult(null);
        out.output = semResult.output;
        IRGenerator gen = new IRGenerator(out);
        gen.visitProgram((Node) semResult.data);
        out.data = gen.instructions;
        return out;
    }

    private String newTemp()  { return "t" + tempCount++;  }
    private String newLabel() { return "L" + labelCount++; }

    private void emit(IRInstruction.Op op, String dest, String... args) {
        instructions.add(new IRInstruction(op, dest, args, 0));
    }

    private void visitProgram(Node p) {
        if (p == null || p.children == null) return;
        for (Node s : p.children) visitStmt(s);
    }

    private void visitStmt(Node n) {
        if (n == null) return;
        switch (n.kind) {
            case ASSIGN:   visitAssign(n); break;
            case PRINT:    visitPrint(n);  break;
            case IF:       visitIf(n);     break;
            case WHILE:    visitWhile(n);  break;
            case FOR:      visitFor(n);    break;
            case FUNC_DEF: visitFuncDef(n);break;
            case RETURN:   visitReturn(n); break;
            case IMPORT:   visitImport(n); break;
            case BLOCK:    for (Node child : n.children) visitStmt(child); break;
            default:       if (n.kind == Node.Kind.CALL) visitExpr(n); break;
        }
    }

    private void visitAssign(Node n) {
        String val = visitExpr(getChild(n, 0));
        if ("=".equals(n.op)) {
            emit(IRInstruction.Op.ASSIGN, n.name, val);
        } else {
            String baseOp = n.op.substring(0, n.op.length() - 1);
            String temp = newTemp();
            emit(IRInstruction.Op.BINOP, temp, n.name, baseOp, val);
            emit(IRInstruction.Op.ASSIGN, n.name, temp);
        }
    }

    private void visitPrint(Node n) {
        List<String> argTemps = new ArrayList<>();
        for (Node arg : n.children) argTemps.add(visitExpr(arg));
        emit(IRInstruction.Op.PRINT, null, argTemps.toArray(new String[0]));
    }

    private void visitIf(Node n) {
        String cond = visitExpr(getChild(n, 0));
        String elseLabel = newLabel();
        String endLabel  = newLabel();

        emit(IRInstruction.Op.JUMP_IF_FALSE, null, cond, elseLabel);
        Node body = getChild(n, 1);
        if (body != null) for (Node s : body.children) visitStmt(s);
        emit(IRInstruction.Op.JUMP, null, endLabel);
        emit(IRInstruction.Op.LABEL, elseLabel);
        Node elseBody = getChild(n, 2);
        if (elseBody != null) for (Node s : elseBody.children) visitStmt(s);
        emit(IRInstruction.Op.LABEL, endLabel);
    }

    private void visitWhile(Node n) {
        String startLabel = newLabel();
        String endLabel   = newLabel();
        emit(IRInstruction.Op.LABEL, startLabel);
        String cond = visitExpr(getChild(n, 0));
        emit(IRInstruction.Op.JUMP_IF_FALSE, null, cond, endLabel);
        Node body = getChild(n, 1);
        if (body != null) for (Node s : body.children) visitStmt(s);
        emit(IRInstruction.Op.JUMP, null, startLabel);
        emit(IRInstruction.Op.LABEL, endLabel);
    }

    private void visitFor(Node n) {
        String iterTemp  = visitExpr(getChild(n, 0));
        String idxTemp   = newTemp();
        String startLabel = newLabel();
        String endLabel   = newLabel();

        emit(IRInstruction.Op.ASSIGN, idxTemp, "0");
        emit(IRInstruction.Op.LABEL, startLabel);
        String cmpTemp = newTemp();
        emit(IRInstruction.Op.BINOP, cmpTemp, idxTemp, "<", iterTemp + ".len");
        emit(IRInstruction.Op.JUMP_IF_FALSE, null, cmpTemp, endLabel);
        emit(IRInstruction.Op.BINOP, n.name, iterTemp, "[]", idxTemp);
        Node body = getChild(n, 1);
        if (body != null) for (Node s : body.children) visitStmt(s);
        String nextIdx = newTemp();
        emit(IRInstruction.Op.BINOP, nextIdx, idxTemp, "+", "1");
        emit(IRInstruction.Op.ASSIGN, idxTemp, nextIdx);
        emit(IRInstruction.Op.JUMP, null, startLabel);
        emit(IRInstruction.Op.LABEL, endLabel);
    }

    private void visitFuncDef(Node n) {
        String endLabel = newLabel();
        emit(IRInstruction.Op.FUNC_DEF, n.name, buildParamArgs(n.name, n.params, endLabel));
        Node body = getChild(n, 0);
        if (body != null) for (Node s : body.children) visitStmt(s);
        emit(IRInstruction.Op.RETURN, null, "None");
        emit(IRInstruction.Op.FUNC_END, endLabel);
    }

    private String[] buildParamArgs(String name, List<String> params, String endLabel) {
        String[] arr = new String[params.size() + 1];
        arr[0] = endLabel;
        for (int i = 0; i < params.size(); i++) arr[i+1] = params.get(i);
        return arr;
    }

    private void visitReturn(Node n) {
        String val = n.children.isEmpty() ? "None" : visitExpr(getChild(n, 0));
        emit(IRInstruction.Op.RETURN, null, val);
    }

    private void visitImport(Node n) {
        emit(IRInstruction.Op.IMPORT, n.alias, n.name);
    }

    private String visitExpr(Node n) {
        if (n == null) return "None";
        switch (n.kind) {
            case NUMBER: {
                String temp = newTemp();
                String val = n.value instanceof Long ? String.valueOf((Long) n.value)
                    : String.valueOf((Double) n.value);
                emit(IRInstruction.Op.LOAD_CONST, temp, val);
                return temp;
            }
            case STRING: {
                String temp = newTemp();
                emit(IRInstruction.Op.LOAD_CONST, temp, "\"" + pyStr(n.value) + "\"");
                return temp;
            }
            case BOOL: {
                String temp = newTemp();
                emit(IRInstruction.Op.LOAD_CONST, temp, (Boolean)n.value ? "True" : "False");
                return temp;
            }
            case NONE: {
                String temp = newTemp();
                emit(IRInstruction.Op.LOAD_CONST, temp, "None");
                return temp;
            }
            case NAME:
                return n.name;
            case BINOP: {
                Node left = getChild(n, 0);
                Node right = getChild(n, 1);
                String l = visitExpr(left);
                String r = visitExpr(right);
                String temp = newTemp();
                emit(IRInstruction.Op.BINOP, temp, l, n.op, r);
                return temp;
            }
            case UNARYOP: {
                Node operand = getChild(n, 0);
                String temp = newTemp();
                emit(IRInstruction.Op.UNARYOP, temp, n.op, visitExpr(operand));
                return temp;
            }
            case CALL: {
                List<String> argTemps = new ArrayList<>();
                for (Node a : n.children) argTemps.add(visitExpr(a));
                String temp = newTemp();
                String[] callArgs = new String[argTemps.size() + 1];
                callArgs[0] = n.name;
                for (int i = 0; i < argTemps.size(); i++) callArgs[i+1] = argTemps.get(i);
                emit(IRInstruction.Op.CALL, temp, callArgs);
                return temp;
            }
            default:
                return "None";
        }
    }

    private Node getChild(Node n, int index) {
        return (n == null || n.children == null || index >= n.children.size()) ? null : n.children.get(index);
    }

    private String pyStr(Object v) {
        if (v == null) return "None";
        return v.toString();
    }
}
