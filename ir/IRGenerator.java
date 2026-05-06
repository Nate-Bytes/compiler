package ir;

import core.*;
import parser.nodes.*;
import java.util.*;

// Walks the AST and emits a flat list of IRInstructions.
// Complex expressions are broken into temporaries: t0, t1, t2...
public class IRGenerator {

    private final List<IRInstruction> instructions = new ArrayList<>();
    private final CompilerResult result;
    private int tempCount  = 0;
    private int labelCount = 0;

    public IRGenerator(CompilerResult result) { this.result = result; }

    public static CompilerResult generate(CompilerResult semResult) {
        CompilerResult out = new CompilerResult(null);
        out.output = semResult.output;
        IRGenerator gen = new IRGenerator(out);
        gen.visitProgram((ProgramNode) semResult.data);
        out.data = gen.instructions;
        return out;
    }

    private String newTemp()  { return "t" + tempCount++;  }
    private String newLabel() { return "L" + labelCount++; }

    private void emit(IRInstruction.Op op, String dest, String... args) {
        instructions.add(new IRInstruction(op, dest, args, 0));
    }

    // ── Visitors ──────────────────────────────────────────────────────────────

    private void visitProgram(ProgramNode p) {
        for (Node s : p.statements) visitStmt(s);
    }

    private void visitStmt(Node n) {
        if (n == null) return;
        if (n instanceof AssignNode)   visitAssign((AssignNode) n);
        else if (n instanceof PrintNode)    visitPrint((PrintNode) n);
        else if (n instanceof IfNode)       visitIf((IfNode) n);
        else if (n instanceof WhileNode)    visitWhile((WhileNode) n);
        else if (n instanceof ForNode)      visitFor((ForNode) n);
        else if (n instanceof FuncDefNode)  visitFuncDef((FuncDefNode) n);
        else if (n instanceof ReturnNode)   visitReturn((ReturnNode) n);
        else if (n instanceof CallNode)     { String t = visitExpr(n); }
        else if (n instanceof ImportNode)   visitImport((ImportNode) n);
        // pass/break/continue are no-ops at IR level
    }

    private void visitAssign(AssignNode n) {
        String val = visitExpr(n.value);
        if (n.op.equals("=")) {
            emit(IRInstruction.Op.ASSIGN, n.target, val);
        } else {
            // Augmented: x += y  →  t0 = x OP y; x = t0
            String baseOp = n.op.substring(0, n.op.length() - 1); // strip '='
            String temp = newTemp();
            emit(IRInstruction.Op.BINOP, temp, n.target, baseOp, val);
            emit(IRInstruction.Op.ASSIGN, n.target, temp);
        }
    }

    private void visitPrint(PrintNode n) {
        List<String> argTemps = new ArrayList<>();
        for (Node arg : n.args) argTemps.add(visitExpr(arg));
        emit(IRInstruction.Op.PRINT, null, argTemps.toArray(new String[0]));
    }

    private void visitIf(IfNode n) {
        String cond    = visitExpr(n.condition);
        String elseLabel = newLabel();
        String endLabel  = newLabel();

        emit(IRInstruction.Op.JUMP_IF_FALSE, null, cond, elseLabel);
        for (Node s : n.body) visitStmt(s);
        emit(IRInstruction.Op.JUMP, null, endLabel);
        emit(IRInstruction.Op.LABEL, elseLabel);
        if (n.elseBody != null) for (Node s : n.elseBody) visitStmt(s);
        emit(IRInstruction.Op.LABEL, endLabel);
    }

    private void visitWhile(WhileNode n) {
        String startLabel = newLabel();
        String endLabel   = newLabel();
        emit(IRInstruction.Op.LABEL, startLabel);
        String cond = visitExpr(n.condition);
        emit(IRInstruction.Op.JUMP_IF_FALSE, null, cond, endLabel);
        for (Node s : n.body) visitStmt(s);
        emit(IRInstruction.Op.JUMP, null, startLabel);
        emit(IRInstruction.Op.LABEL, endLabel);
    }

    private void visitFor(ForNode n) {
        // for x in range(n) — we emit a runtime-level FOR loop via special IR
        String iterTemp  = visitExpr(n.iterable);
        String idxTemp   = newTemp();
        String startLabel = newLabel();
        String endLabel   = newLabel();

        emit(IRInstruction.Op.ASSIGN, idxTemp, "0");
        emit(IRInstruction.Op.LABEL, startLabel);
        // Check: idxTemp < len(iter)
        String cmpTemp = newTemp();
        emit(IRInstruction.Op.BINOP, cmpTemp, idxTemp, "<", iterTemp + ".len");
        emit(IRInstruction.Op.JUMP_IF_FALSE, null, cmpTemp, endLabel);
        // Assign loop var
        emit(IRInstruction.Op.BINOP, n.var, iterTemp, "[]", idxTemp);
        for (Node s : n.body) visitStmt(s);
        // Increment index
        String nextIdx = newTemp();
        emit(IRInstruction.Op.BINOP, nextIdx, idxTemp, "+", "1");
        emit(IRInstruction.Op.ASSIGN, idxTemp, nextIdx);
        emit(IRInstruction.Op.JUMP, null, startLabel);
        emit(IRInstruction.Op.LABEL, endLabel);
    }

    private void visitFuncDef(FuncDefNode n) {
        String endLabel = newLabel();
        emit(IRInstruction.Op.FUNC_DEF, n.name,
             buildParamArgs(n.name, n.params, endLabel));
        for (Node s : n.body) visitStmt(s);
        emit(IRInstruction.Op.FUNC_END, endLabel);
    }

    private String[] buildParamArgs(String name, List<String> params, String endLabel) {
        // args = [endLabel, param0, param1, ...]
        String[] arr = new String[params.size() + 1];
        arr[0] = endLabel;
        for (int i = 0; i < params.size(); i++) arr[i+1] = params.get(i);
        return arr;
    }

    private void visitReturn(ReturnNode n) {
        String val = n.value != null ? visitExpr(n.value) : "None";
        emit(IRInstruction.Op.RETURN, null, val);
    }

    private void visitImport(ImportNode n) {
        emit(IRInstruction.Op.IMPORT, n.alias, n.module);
    }

    // Returns the temp variable name holding the result of this expression.
    private String visitExpr(Node n) {
        if (n == null) return "None";

        if (n instanceof NumberNode) {
            NumberNode num = (NumberNode) n;
            String temp = newTemp();
            String val = num.isInt ? String.valueOf((long) num.value) : String.valueOf(num.value);
            emit(IRInstruction.Op.LOAD_CONST, temp, val);
            return temp;
        }
        if (n instanceof StringNode) {
            String temp = newTemp();
            emit(IRInstruction.Op.LOAD_CONST, temp, "\"" + ((StringNode)n).value + "\"");
            return temp;
        }
        if (n instanceof BoolNode) {
            String temp = newTemp();
            emit(IRInstruction.Op.LOAD_CONST, temp, ((BoolNode)n).value ? "True" : "False");
            return temp;
        }
        if (n instanceof NoneNode) {
            String temp = newTemp();
            emit(IRInstruction.Op.LOAD_CONST, temp, "None");
            return temp;
        }
        if (n instanceof NameNode) {
            return ((NameNode)n).name;
        }
        if (n instanceof BinOpNode) {
            BinOpNode b = (BinOpNode) n;
            String l = visitExpr(b.left);
            String r = visitExpr(b.right);
            String temp = newTemp();
            emit(IRInstruction.Op.BINOP, temp, l, b.op, r);
            return temp;
        }
        if (n instanceof UnaryOpNode) {
            UnaryOpNode u = (UnaryOpNode) n;
            String operand = visitExpr(u.operand);
            String temp = newTemp();
            emit(IRInstruction.Op.UNARYOP, temp, u.op, operand);
            return temp;
        }
        if (n instanceof CallNode) {
            CallNode c = (CallNode) n;
            List<String> argTemps = new ArrayList<>();
            for (Node a : c.args) argTemps.add(visitExpr(a));
            String temp = newTemp();
            String[] callArgs = new String[argTemps.size() + 1];
            callArgs[0] = c.name;
            for (int i = 0; i < argTemps.size(); i++) callArgs[i+1] = argTemps.get(i);
            emit(IRInstruction.Op.CALL, temp, callArgs);
            return temp;
        }
        return "None";
    }
}
