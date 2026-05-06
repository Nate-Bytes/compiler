package parser;

import core.*;
import lexer.Token;
import parser.nodes.Node;
import java.util.*;

// Recursive-descent parser. Reads the token list and builds an AST.
// Uses SyntaxRules to validate statement-level structure from syntax_rules.txt.
public class Parser {

    private final List<Token> tokens;
    private final CompilerResult result;
    private int pos = 0;

    public Parser(List<Token> tokens, CompilerResult result) {
        this.tokens = tokens;
        this.result = result;
    }

    public static CompilerResult parse(CompilerResult lexResult) {
        @SuppressWarnings("unchecked")
        List<Token> tokens = (List<Token>) lexResult.data;
        CompilerResult result = new CompilerResult(null);
        result.output = lexResult.output;
        Parser p = new Parser(tokens, result);
        Node program = p.parseProgram();
        result.data = program;
        return result;
    }

    // ── Top-level ─────────────────────────────────────────────────────────────

    private Node parseProgram() {
        List<Node> stmts = new ArrayList<>();
        while (!isAtEnd()) {
            Token t = peek();
            if (t == null) break;
            if (t.type.equals("NEWLINE") || t.type.equals("INDENT") || t.type.equals("DEDENT")) {
                advance();
                continue;
            }
            Node s = parseStatement();
            if (s != null) stmts.add(s);
        }
        return Node.program(stmts);
    }

    private Node parseStatement() {
        Token t = peek();
        if (t == null) return null;

        if (t.type.equals("INDENT") || t.type.equals("DEDENT")) {
            advance();
            return parseStatement();
        }

        if (t.type.equals("Keyword")) {
            SyntaxRules.Rule rule = SyntaxRules.findByKeyword(t.value);
            if (rule != null) validateRule(rule, t);
        }

        switch (t.value) {
            case "if":       return parseIf();
            case "while":    return parseWhile();
            case "for":      return parseFor();
            case "def":      return parseDef();
            case "return":   return parseReturn();
            case "import":   return parseImport();
            case "from":     return parseFromImport();
            case "pass":     advance(); return new Node(Node.Kind.PASS, t.line, t.column);
            case "break":    advance(); return new Node(Node.Kind.BREAK, t.line, t.column);
            case "continue": advance(); return new Node(Node.Kind.CONTINUE, t.line, t.column);
            case "print":    return parsePrint();
            default:
                if (t.type.equals("Identifier") && peekAhead(1) != null &&
                   (peekAhead(1).value.equals("=") || isAugOp(peekAhead(1).value))) {
                    return parseAssignment();
                }
                if (t.type.equals("Keyword")) {
                    error("Unexpected keyword '" + t.value + "'", t);
                    advance(); return null;
                }
                return parseExpressionStatement();
        }
    }

    // ── Statements ───────────────────────────────────────────────────────────

    private Node parseAssignment() {
        Token nameToken = advance();
        Token op = advance();
        Node value = parseExpression();
        SyntaxRules.Rule rule = op.value.equals("=")
            ? SyntaxRules.findByName("assignment")
            : SyntaxRules.findByName("aug_assign");
        if (value == null) {
            error("Expected value after '" + op.value + "'", op);
            return null;
        }
        Node node = new Node(Node.Kind.ASSIGN, nameToken.line, nameToken.column);
        node.name = nameToken.value;
        node.op = op.value;
        node.children.add(value);
        return node;
    }

    private Node parsePrint() {
        Token t = advance();
        SyntaxRules.Rule rule = SyntaxRules.findByName("print_stmt");
        expect("(", "Expected '(' after 'print'", t);
        List<Node> args = parseArgList(")");
        expect(")", "Expected ')' to close print()", t);
        Node node = new Node(Node.Kind.PRINT, t.line, t.column);
        node.children = args;
        return node;
    }

    private Node parseIf() {
        Token t = advance();
        Node cond = parseExpression();
        if (cond == null) { error("Expected condition after 'if'", t); return null; }
        expect(":", "Expected ':' after if condition", t);
        Node body = Node.block(parseIndentedBlock(t), t.line, t.column);
        Node elseBody = Node.block(new ArrayList<>(), t.line, t.column);

        while (peek() != null && (peek().value.equals("elif") || peek().value.equals("else"))) {
            Token branch = advance();
            if (branch.value.equals("elif")) {
                Node elifCond = parseExpression();
                expect(":", "Expected ':' after elif condition", branch);
                List<Node> elifBody = parseIndentedBlock(branch);
                Node nestedIf = new Node(Node.Kind.IF, branch.line, branch.column);
                nestedIf.children.add(elifCond);
                nestedIf.children.add(Node.block(elifBody, branch.line, branch.column));
                nestedIf.children.add(Node.block(new ArrayList<>(), branch.line, branch.column));
                elseBody.children.add(nestedIf);
            } else {
                expect(":", "Expected ':' after else", branch);
                elseBody = Node.block(parseIndentedBlock(branch), branch.line, branch.column);
                break;
            }
        }

        Node node = new Node(Node.Kind.IF, t.line, t.column);
        node.children.add(cond);
        node.children.add(body);
        node.children.add(elseBody);
        return node;
    }

    private Node parseWhile() {
        Token t = advance();
        Node cond = parseExpression();
        if (cond == null) { error("Expected condition after 'while'", t); return null; }
        expect(":", "Expected ':' after while condition", t);
        Node body = Node.block(parseIndentedBlock(t), t.line, t.column);
        Node node = new Node(Node.Kind.WHILE, t.line, t.column);
        node.children.add(cond);
        node.children.add(body);
        return node;
    }

    private Node parseFor() {
        Token t = advance();
        Token var = expect("Identifier", "Expected variable name after 'for'", t);
        expectKeyword("in", "Expected 'in' in for loop", t);
        Node iterable = parseExpression();
        if (iterable == null) { error("Expected iterable after 'in'", t); return null; }
        expect(":", "Expected ':' after for iterable", t);
        Node body = Node.block(parseIndentedBlock(t), t.line, t.column);
        Node node = new Node(Node.Kind.FOR, t.line, t.column);
        node.name = var != null ? var.value : "_";
        node.children.add(iterable);
        node.children.add(body);
        return node;
    }

    private Node parseDef() {
        Token t = advance();
        Token name = expect("Identifier", "Expected function name after 'def'", t);
        if (name == null) return null;
        expect("(", "Expected '(' after function name", t);
        List<String> params = parseParamList();
        expect(")", "Expected ')' to close parameter list", t);
        expect(":", "Expected ':' after def header", t);
        Node body = Node.block(parseIndentedBlock(t), t.line, t.column);
        Node node = new Node(Node.Kind.FUNC_DEF, t.line, t.column);
        node.name = name.value;
        node.params = params;
        node.children.add(body);
        return node;
    }

    private Node parseReturn() {
        Token t = advance();
        Node node = new Node(Node.Kind.RETURN, t.line, t.column);
        if (isAtEnd() || peek().value.equals("\n")) return node;
        Node val = parseExpression();
        if (val != null) node.children.add(val);
        return node;
    }

    private Node parseImport() {
        Token t = advance();
        Token mod = expect("Identifier", "Expected module name after 'import'", t);
        String alias = mod != null ? mod.value : "";
        if (peek() != null && peek().value.equals("as")) {
            advance();
            Token a = expect("Identifier", "Expected alias after 'as'", t);
            if (a != null) alias = a.value;
        }
        Node node = new Node(Node.Kind.IMPORT, t.line, t.column);
        node.name = mod != null ? mod.value : "";
        node.alias = alias;
        return node;
    }

    private Node parseFromImport() {
        Token t = advance();
        Token mod = expect("Identifier", "Expected module name after 'from'", t);
        expectKeyword("import", "Expected 'import' after module name", t);
        Token name = expect("Identifier", "Expected name after 'import'", t);
        Node node = new Node(Node.Kind.IMPORT, t.line, t.column);
        node.name = (mod != null ? mod.value : "") + "." + (name != null ? name.value : "");
        node.alias = name != null ? name.value : "";
        return node;
    }

    private Node parseExpressionStatement() {
        return parseExpression();
    }

    // ── Expression parsing (recursive descent) ───────────────────────────────

    private Node parseExpression() { return parseOr(); }

    private Node parseOr() {
        Node left = parseAnd();
        while (peek() != null && peek().value.equals("or")) {
            Token op = advance();
            Node right = parseAnd();
            Node node = new Node(Node.Kind.BINOP, op.line, op.column);
            node.op = "or";
            node.children.add(left);
            node.children.add(right);
            left = node;
        }
        return left;
    }

    private Node parseAnd() {
        Node left = parseNot();
        while (peek() != null && peek().value.equals("and")) {
            Token op = advance();
            Node right = parseNot();
            Node node = new Node(Node.Kind.BINOP, op.line, op.column);
            node.op = "and";
            node.children.add(left);
            node.children.add(right);
            left = node;
        }
        return left;
    }

    private Node parseNot() {
        if (peek() != null && peek().value.equals("not")) {
            Token op = advance();
            Node node = new Node(Node.Kind.UNARYOP, op.line, op.column);
            node.op = "not";
            node.children.add(parseNot());
            return node;
        }
        return parseComparison();
    }

    private static final Set<String> CMP_OPS = new HashSet<>(
        Arrays.asList("==","!=","<",">","<=",">=","in","not","is"));

    private Node parseComparison() {
        Node left = parseAddSub();
        while (peek() != null && CMP_OPS.contains(peek().value)) {
            Token op = advance();
            String opStr = op.value;
            if ((opStr.equals("not") && peek()!=null && peek().value.equals("in")) ||
                (opStr.equals("is")  && peek()!=null && peek().value.equals("not"))) {
                opStr += " " + advance().value;
            }
            Node right = parseAddSub();
            Node node = new Node(Node.Kind.BINOP, op.line, op.column);
            node.op = opStr;
            node.children.add(left);
            node.children.add(right);
            left = node;
        }
        return left;
    }

    private Node parseAddSub() {
        Node left = parseMulDiv();
        while (peek() != null && (peek().value.equals("+") || peek().value.equals("-"))) {
            Token op = advance();
            Node right = parseMulDiv();
            Node node = new Node(Node.Kind.BINOP, op.line, op.column);
            node.op = op.value;
            node.children.add(left);
            node.children.add(right);
            left = node;
        }
        return left;
    }

    private Node parseMulDiv() {
        Node left = parseUnary();
        while (peek() != null && (peek().value.equals("*") || peek().value.equals("/")
                || peek().value.equals("//") || peek().value.equals("%") || peek().value.equals("**"))) {
            Token op = advance();
            Node right = parseUnary();
            Node node = new Node(Node.Kind.BINOP, op.line, op.column);
            node.op = op.value;
            node.children.add(left);
            node.children.add(right);
            left = node;
        }
        return left;
    }

    private Node parseUnary() {
        if (peek() != null && (peek().value.equals("-") || peek().value.equals("+"))) {
            Token op = advance();
            Node node = new Node(Node.Kind.UNARYOP, op.line, op.column);
            node.op = op.value;
            node.children.add(parseUnary());
            return node;
        }
        return parsePrimary();
    }

    private Node parsePrimary() {
        Token t = peek();
        if (t == null) return null;

        if (t.type.equals("Integer Literal")) {
            advance();
            try { return createNumberNode(Long.parseLong(t.value), true, t.line, t.column); }
            catch (NumberFormatException e) { return createNumberNode(0L, true, t.line, t.column); }
        }
        if (t.type.equals("Float Literal")) {
            advance();
            try { return createNumberNode(Double.parseDouble(t.value), false, t.line, t.column); }
            catch (NumberFormatException e) { return createNumberNode(0.0, false, t.line, t.column); }
        }

        if (t.type.equals("String Literal") || t.type.equals("F-String Literal")) {
            advance();
            String raw = t.value;
            int start = 0;
            if (raw.length() > 0 && !raw.startsWith("\"") && !raw.startsWith("'")) start = 1;
            if (raw.length() > start + 1) raw = raw.substring(start+1, raw.length()-1);
            Node node = new Node(Node.Kind.STRING, t.line, t.column);
            node.value = raw;
            return node;
        }

        if (t.value.equals("True"))  { advance(); Node node = new Node(Node.Kind.BOOL, t.line, t.column); node.value = true; return node; }
        if (t.value.equals("False")) { advance(); Node node = new Node(Node.Kind.BOOL, t.line, t.column); node.value = false; return node; }
        if (t.value.equals("None"))  { advance(); return new Node(Node.Kind.NONE, t.line, t.column); }

        if (t.value.equals("(")) {
            advance();
            Node inner = parseExpression();
            expect(")", "Expected ')'", t);
            return inner;
        }

        if (t.type.equals("Identifier") || t.type.equals("Keyword")) {
            advance();
            if (peek() != null && peek().value.equals("(")) {
                advance();
                List<Node> args = parseArgList(")");
                expect(")", "Expected ')' to close function call", t);
                Node node = new Node(Node.Kind.CALL, t.line, t.column);
                node.name = t.value;
                node.children = args;
                return node;
            }
            Node node = new Node(Node.Kind.NAME, t.line, t.column);
            node.name = t.value;
            return node;
        }

        return null;
    }

    private Node createNumberNode(Object value, boolean isInt, int line, int column) {
        Node node = new Node(Node.Kind.NUMBER, line, column);
        node.value = value;
        node.op = isInt ? "int" : "float";
        return node;
    }

    private List<Node> parseArgList(String end) {
        List<Node> args = new ArrayList<>();
        while (peek() != null && !peek().value.equals(end)) {
            Node arg = parseExpression();
            if (arg != null) args.add(arg);
            if (peek() != null && peek().value.equals(",")) advance();
            else break;
        }
        return args;
    }

    private List<String> parseParamList() {
        List<String> params = new ArrayList<>();
        while (peek() != null && !peek().value.equals(")")) {
            Token p = peek();
            if (p.type.equals("Identifier")) { advance(); params.add(p.value); }
            if (peek() != null && peek().value.equals(",")) advance();
            else break;
        }
        return params;
    }

    private List<Node> parseIndentedBlock(Token parent) {
        List<Node> stmts = new ArrayList<>();
        while (peek() != null && peek().type.equals("NEWLINE")) {
            advance();
        }
        expect("INDENT", "Expected indented block after '" + parent.value + "'", parent);
        while (!isAtEnd()) {
            Token t = peek();
            if (t == null) break;
            if (t.type.equals("DEDENT")) {
                advance();
                break;
            }
            if (t.type.equals("NEWLINE")) {
                advance();
                continue;
            }
            Node s = parseStatement();
            if (s != null) stmts.add(s);
        }
        return stmts;
    }

    private boolean isBlockTerminator(Token t) {
        if (!t.type.equals("Keyword")) return false;
        switch (t.value) {
            case "elif": case "else": case "except": case "finally":
            case "def":  case "class": return true;
            default: return false;
        }
    }

    private void validateRule(SyntaxRules.Rule rule, Token start) {
        boolean needsColon = false;
        for (String tok : rule.tokens) if (tok.equals(":")) { needsColon = true; break; }
    }

    private boolean isAugOp(String value) {
        return value.equals("+=") || value.equals("-=") || value.equals("*=") ||
               value.equals("/=") || value.equals("%=") || value.equals("//=") ||
               value.equals("**=");
    }

    private Token expect(String valueOrType, String msg, Token context) {
        Token t = peek();
        if (t != null && (t.value.equals(valueOrType) || t.type.equals(valueOrType))) {
            return advance();
        }
        error(msg + (t != null ? " (got '" + t.value + "')" : " (got end of input)"), context);
        return null;
    }

    private void expectKeyword(String kw, String msg, Token context) {
        Token t = peek();
        if (t != null && t.value.equals(kw)) { advance(); return; }
        error(msg + (t != null ? " (got '" + t.value + "')" : ""), context);
    }

    private Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private Token peekAhead(int offset) {
        int index = pos + offset;
        return index < tokens.size() ? tokens.get(index) : null;
    }

    private Token advance() {
        return pos < tokens.size() ? tokens.get(pos++) : null;
    }

    private boolean isAtEnd() {
        return pos >= tokens.size();
    }

    private void error(String message, Token context) {
        result.error(CompilerError.Stage.PARSER, context.line, context.column, message);
    }
}
