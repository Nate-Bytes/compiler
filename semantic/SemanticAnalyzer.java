package semantic;

import core.*;
import parser.nodes.*;
import java.util.*;

// Walks the AST and checks for semantic problems:
// undefined variables, type mismatches, bad return placement, etc.
public class SemanticAnalyzer {

    // A scope is a map of variable name → inferred type string
    private final Deque<Map<String,String>> scopes = new ArrayDeque<>();
    private final CompilerResult result;
    private boolean inFunction = false;

    // Python built-ins are always in scope
    private static final Set<String> BUILTINS = new HashSet<>(Arrays.asList(
        "print","input","len","range","type","int","float","str","bool",
        "list","dict","tuple","set","abs","max","min","sum","round",
        "True","False","None","enumerate","zip","map","filter","sorted",
        "reversed","open","super","self","Exception","ValueError","TypeError"
    ));

    public SemanticAnalyzer(CompilerResult result) { this.result = result; }

    public static CompilerResult analyze(CompilerResult parseResult) {
        CompilerResult out = new CompilerResult(parseResult.data);
        out.output = parseResult.output;
        SemanticAnalyzer sa = new SemanticAnalyzer(out);
        sa.pushScope();
        sa.visitProgram((ProgramNode) parseResult.data);
        sa.popScope();
        return out;
    }

    private void visitProgram(ProgramNode p) {
        for (Node s : p.statements) visit(s);
    }

    private void visit(Node n) {
        if (n == null) return;
        if (n instanceof AssignNode)   visitAssign((AssignNode) n);
        else if (n instanceof PrintNode)   visitPrint((PrintNode) n);
        else if (n instanceof IfNode)      visitIf((IfNode) n);
        else if (n instanceof WhileNode)   visitWhile((WhileNode) n);
        else if (n instanceof ForNode)     visitFor((ForNode) n);
        else if (n instanceof FuncDefNode) visitFuncDef((FuncDefNode) n);
        else if (n instanceof ReturnNode)  visitReturn((ReturnNode) n);
        else if (n instanceof CallNode)    visitCall((CallNode) n);
        else if (n instanceof ImportNode)  visitImport((ImportNode) n);
        else if (n instanceof BinOpNode)   inferType((BinOpNode) n);
        else if (n instanceof NameNode)    checkName((NameNode) n);
        // Pass/Break/Continue/Number/String/Bool/None are always valid
    }

    private void visitAssign(AssignNode n) {
        String valType = resolveType(n.value);
        define(n.target, valType, n);
    }

    private void visitPrint(PrintNode n) {
        for (Node arg : n.args) {
            if (arg instanceof NameNode) checkName((NameNode) arg);
            else visit(arg);
        }
    }

    private void visitIf(IfNode n) {
        visit(n.condition);
        pushScope(); for (Node s : n.body) visit(s); popScope();
        if (n.elseBody != null) {
            pushScope(); for (Node s : n.elseBody) visit(s); popScope();
        }
    }

    private void visitWhile(WhileNode n) {
        visit(n.condition);
        pushScope(); for (Node s : n.body) visit(s); popScope();
    }

    private void visitFor(ForNode n) {
        visit(n.iterable);
        pushScope();
        define(n.var, "any", n);
        for (Node s : n.body) visit(s);
        popScope();
    }

    private void visitFuncDef(FuncDefNode n) {
        define(n.name, "function", n);
        boolean wasInFunc = inFunction; inFunction = true;
        pushScope();
        for (String p : n.params) define(p, "any", n);
        for (Node s : n.body) visit(s);
        popScope();
        inFunction = wasInFunc;
    }

    private void visitReturn(ReturnNode n) {
        if (!inFunction)
            error("'return' outside of a function", n);
        if (n.value != null) visit(n.value);
    }

    private void visitCall(CallNode n) {
        if (!BUILTINS.contains(n.name) && lookup(n.name) == null)
            error("NameError: name '" + n.name + "' is not defined", n);
        for (Node a : n.args) visit(a);
    }

    private void visitImport(ImportNode n) {
        define(n.alias.isEmpty() ? n.module : n.alias, "module", n);
    }

    private void checkName(NameNode n) {
        if (!BUILTINS.contains(n.name) && lookup(n.name) == null)
            error("NameError: name '" + n.name + "' is not defined", n);
    }

    // Infers type and also checks for mixed string+number operations
    private String inferType(BinOpNode n) {
        String lt = resolveType(n.left);
        String rt = resolveType(n.right);
        if (lt != null && rt != null && !lt.equals("any") && !rt.equals("any")) {
            boolean leftStr  = lt.equals("str");
            boolean rightStr = rt.equals("str");
            boolean leftNum  = lt.equals("int") || lt.equals("float");
            boolean rightNum = rt.equals("int") || rt.equals("float");
            if ((leftStr && rightNum) || (leftNum && rightStr)) {
                if (!n.op.equals("*")) // str*int is valid in Python
                    error("TypeError: cannot use '" + n.op + "' between " + lt + " and " + rt, n);
            }
        }
        if ("int".equals(lt) && "int".equals(rt)) return "int";
        if ("str".equals(lt) || "str".equals(rt)) return "str";
        if ("float".equals(lt) || "float".equals(rt)) return "float";
        return "any";
    }

    // Resolves the inferred type of any expression node
    private String resolveType(Node n) {
        if (n == null) return "any";
        if (n instanceof NumberNode) return ((NumberNode)n).isInt ? "int" : "float";
        if (n instanceof StringNode) return "str";
        if (n instanceof BoolNode)   return "bool";
        if (n instanceof NoneNode)   return "none";
        if (n instanceof NameNode) {
            String t = lookup(((NameNode)n).name); return t != null ? t : "any";
        }
        if (n instanceof BinOpNode)  return inferType((BinOpNode) n);
        if (n instanceof CallNode)   return "any";
        return "any";
    }

    // ── Scope helpers ─────────────────────────────────────────────────────────

    private void pushScope() { scopes.push(new LinkedHashMap<>()); }
    private void popScope()  { if (!scopes.isEmpty()) scopes.pop(); }

    private void define(String name, String type, Node n) {
        if (!scopes.isEmpty()) scopes.peek().put(name, type);
    }

    private String lookup(String name) {
        for (Map<String,String> scope : scopes)
            if (scope.containsKey(name)) return scope.get(name);
        return null;
    }

    private void error(String msg, Node n) {
        result.error(CompilerError.Stage.SEMANTIC, n.line, n.column, msg);
    }
}
