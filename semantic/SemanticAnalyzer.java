package semantic;

import core.*;
import parser.nodes.Node;
import java.util.*;

// Walks the AST and checks for semantic problems:
// undefined variables, type mismatches, bad return placement, etc.
public class SemanticAnalyzer {

    private final Deque<Map<String,String>> scopes = new ArrayDeque<>();
    private final CompilerResult result;
    private boolean inFunction = false;

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
        sa.visitProgram((Node) parseResult.data);
        sa.popScope();
        return out;
    }

    private void visitProgram(Node p) {
        if (p == null || p.children == null) return;
        for (Node s : p.children) visit(s);
    }

    private void visit(Node n) {
        if (n == null) return;
        switch (n.kind) {
            case ASSIGN:   visitAssign(n); break;
            case PRINT:    visitPrint(n);  break;
            case IF:       visitIf(n);     break;
            case WHILE:    visitWhile(n);  break;
            case FOR:      visitFor(n);    break;
            case FUNC_DEF: visitFuncDef(n);break;
            case RETURN:   visitReturn(n); break;
            case CALL:     visitCall(n);   break;
            case IMPORT:   visitImport(n); break;
            case BINOP:    inferType(n);   break;
            case NAME:     checkName(n);   break;
            case BLOCK:    for (Node child : n.children) visit(child); break;
            default: break;
        }
    }

    private void visitAssign(Node n) {
        String valType = resolveType(getChild(n, 0));
        define(n.name, valType, n);
    }

    private void visitPrint(Node n) {
        for (Node arg : n.children) {
            if (arg.kind == Node.Kind.NAME) checkName(arg);
            else visit(arg);
        }
    }

    private void visitIf(Node n) {
        visit(getChild(n, 0));
        pushScope();
        Node body = getChild(n, 1);
        if (body != null) for (Node s : body.children) visit(s);
        popScope();
        Node elseBody = getChild(n, 2);
        if (elseBody != null) {
            pushScope();
            for (Node s : elseBody.children) visit(s);
            popScope();
        }
    }

    private void visitWhile(Node n) {
        visit(getChild(n, 0));
        pushScope();
        Node body = getChild(n, 1);
        if (body != null) for (Node s : body.children) visit(s);
        popScope();
    }

    private void visitFor(Node n) {
        visit(getChild(n, 0));
        pushScope();
        define(n.name, "any", n);
        Node body = getChild(n, 1);
        if (body != null) for (Node s : body.children) visit(s);
        popScope();
    }

    private void visitFuncDef(Node n) {
        define(n.name, "function", n);
        boolean wasInFunc = inFunction;
        inFunction = true;
        pushScope();
        for (String p : n.params) define(p, "any", n);
        Node body = getChild(n, 0);
        if (body != null) for (Node s : body.children) visit(s);
        popScope();
        inFunction = wasInFunc;
    }

    private void visitReturn(Node n) {
        if (!inFunction) error("'return' outside of a function", n);
        if (!n.children.isEmpty()) visit(getChild(n, 0));
    }

    private void visitCall(Node n) {
        if (!BUILTINS.contains(n.name) && lookup(n.name) == null)
            error("NameError: name '" + n.name + "' is not defined", n);
        for (Node a : n.children) visit(a);
    }

    private void visitImport(Node n) {
        define(n.alias == null || n.alias.isEmpty() ? n.name : n.alias, "module", n);
    }

    private void checkName(Node n) {
        if (!BUILTINS.contains(n.name) && lookup(n.name) == null)
            error("NameError: name '" + n.name + "' is not defined", n);
    }

    private String inferType(Node n) {
        String lt = resolveType(getChild(n, 0));
        String rt = resolveType(getChild(n, 1));
        if (lt != null && rt != null && !lt.equals("any") && !rt.equals("any")) {
            boolean leftStr  = lt.equals("str");
            boolean rightStr = rt.equals("str");
            boolean leftNum  = lt.equals("int") || lt.equals("float");
            boolean rightNum = rt.equals("int") || rt.equals("float");
            if ((leftStr && rightNum) || (leftNum && rightStr)) {
                if (!n.op.equals("*"))
                    error("TypeError: cannot use '" + n.op + "' between " + lt + " and " + rt, n);
            }
        }
        if ("int".equals(lt) && "int".equals(rt)) return "int";
        if ("str".equals(lt) || "str".equals(rt)) return "str";
        if ("float".equals(lt) || "float".equals(rt)) return "float";
        return "any";
    }

    private String resolveType(Node n) {
        if (n == null) return "any";
        switch (n.kind) {
            case NUMBER:
                return (n.value instanceof Long) ? "int" : "float";
            case STRING:
                return "str";
            case BOOL:
                return "bool";
            case NONE:
                return "none";
            case NAME: {
                String t = lookup(n.name);
                return t != null ? t : "any";
            }
            case BINOP:
                return inferType(n);
            case CALL:
                return "any";
            default:
                return "any";
        }
    }

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

    private Node getChild(Node n, int index) {
        return (n == null || n.children == null || index >= n.children.size()) ? null : n.children.get(index);
    }
}
