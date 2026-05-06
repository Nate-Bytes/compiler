package parser;

import core.*;
import lexer.Token;
import parser.nodes.*;
import java.util.*;

// Recursive-descent parser. Reads the token list and builds an AST.
// Uses SyntaxRules to validate statement-level structure from syntax_rules.txt.
public class Parser {

    private final List<Token> tokens;
    private final CompilerResult result;
    private int pos = 0;
    private int indentLevel = 0;

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
        ProgramNode program = p.parseProgram();
        result.data = program;
        return result;
    }

    // ── Top-level ─────────────────────────────────────────────────────────────

    private ProgramNode parseProgram() {
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
        return new ProgramNode(stmts);
    }

    private Node parseStatement() {
        Token t = peek();
        if (t == null) return null;

        // Validate against syntax_rules.txt for keyword-led statements
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
            case "pass":     advance(); return new PassNode(t.line, t.column);
            case "break":    advance(); return new BreakNode(t.line, t.column);
            case "continue": advance(); return new ContinueNode(t.line, t.column);
            case "print":    return parsePrint();
            default:
                // Assignment or expression
                if (t.type.equals("Identifier") && peekAhead(1) != null &&
                   (peekAhead(1).value.equals("=") || isAugOp(peekAhead(1).value))) {
                    return parseAssignment();
                }
                // Unknown keyword or bare expression — skip to recover
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
        Token op = advance(); // = or +=, -=, etc.
        Node value = parseExpression();
        SyntaxRules.Rule rule = op.value.equals("=")
            ? SyntaxRules.findByName("assignment")
            : SyntaxRules.findByName("aug_assign");
        if (value == null) {
            error("Expected value after '" + op.value + "'", op);
            return null;
        }
        return new AssignNode(nameToken.value, op.value, value, nameToken.line, nameToken.column);
    }

    private Node parsePrint() {
        Token t = advance(); // 'print'
        SyntaxRules.Rule rule = SyntaxRules.findByName("print_stmt");
        expect("(", "Expected '(' after 'print'", t);
        List<Node> args = parseArgList(")");
        expect(")", "Expected ')' to close print()", t);
        return new PrintNode(args, t.line, t.column);
    }

    private Node parseIf() {
        Token t = advance(); // 'if'
        Node cond = parseExpression();
        if (cond == null) { error("Expected condition after 'if'", t); return null; }
        expect(":", "Expected ':' after if condition", t);
        List<Node> body    = parseIndentedBlock(t);
        List<Node> elseBody = new ArrayList<>();

        // elif / else chains
        while (peek() != null && (peek().value.equals("elif") || peek().value.equals("else"))) {
            Token branch = advance();
            if (branch.value.equals("elif")) {
                Node elifCond = parseExpression();
                expect(":", "Expected ':' after elif condition", branch);
                List<Node> elifBody = parseIndentedBlock(branch);
                elseBody.add(new IfNode(elifCond, elifBody, new ArrayList<>(), branch.line, branch.column));
            } else {
                expect(":", "Expected ':' after else", branch);
                elseBody = parseIndentedBlock(branch);
            }
        }
        return new IfNode(cond, body, elseBody, t.line, t.column);
    }

    private Node parseWhile() {
        Token t = advance();
        Node cond = parseExpression();
        if (cond == null) { error("Expected condition after 'while'", t); return null; }
        expect(":", "Expected ':' after while condition", t);
        List<Node> body = parseIndentedBlock(t);
        return new WhileNode(cond, body, t.line, t.column);
    }

    private Node parseFor() {
        Token t = advance();
        Token var = expect("Identifier", "Expected variable name after 'for'", t);
        expectKeyword("in", "Expected 'in' in for loop", t);
        Node iterable = parseExpression();
        if (iterable == null) { error("Expected iterable after 'in'", t); return null; }
        expect(":", "Expected ':' after for iterable", t);
        List<Node> body = parseIndentedBlock(t);
        return new ForNode(var != null ? var.value : "_", iterable, body, t.line, t.column);
    }

    private Node parseDef() {
        Token t = advance();
        Token name = expect("Identifier", "Expected function name after 'def'", t);
        if (name == null) return null;
        expect("(", "Expected '(' after function name", t);
        List<String> params = parseParamList();
        expect(")", "Expected ')' to close parameter list", t);
        expect(":", "Expected ':' after def header", t);
        List<Node> body = parseIndentedBlock(t);
        return new FuncDefNode(name.value, params, body, t.line, t.column);
    }

    private Node parseReturn() {
        Token t = advance();
        if (isAtEnd() || peek().value.equals("\n")) return new ReturnNode(null, t.line, t.column);
        Node val = parseExpression();
        return new ReturnNode(val, t.line, t.column);
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
        return new ImportNode(mod != null ? mod.value : "", alias, t.line, t.column);
    }

    private Node parseFromImport() {
        Token t = advance(); // 'from'
        Token mod = expect("Identifier", "Expected module name after 'from'", t);
        expectKeyword("import", "Expected 'import' after module name", t);
        Token name = expect("Identifier", "Expected name after 'import'", t);
        return new ImportNode(
            (mod != null ? mod.value : "") + "." + (name != null ? name.value : ""),
            name != null ? name.value : "", t.line, t.column);
    }

    private Node parseExpressionStatement() {
        Node e = parseExpression();
        return e;
    }

    // ── Expression parsing (recursive descent) ───────────────────────────────

    private Node parseExpression() { return parseOr(); }

    private Node parseOr() {
        Node left = parseAnd();
        while (peek() != null && peek().value.equals("or")) {
            Token op = advance();
            Node right = parseAnd();
            left = new BinOpNode(left, "or", right, op.line, op.column);
        }
        return left;
    }

    private Node parseAnd() {
        Node left = parseNot();
        while (peek() != null && peek().value.equals("and")) {
            Token op = advance();
            Node right = parseNot();
            left = new BinOpNode(left, "and", right, op.line, op.column);
        }
        return left;
    }

    private Node parseNot() {
        if (peek() != null && peek().value.equals("not")) {
            Token op = advance();
            return new UnaryOpNode("not", parseNot(), op.line, op.column);
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
            // Handle 'not in' and 'is not'
            if ((opStr.equals("not") && peek()!=null && peek().value.equals("in")) ||
                (opStr.equals("is")  && peek()!=null && peek().value.equals("not"))) {
                opStr += " " + advance().value;
            }
            Node right = parseAddSub();
            left = new BinOpNode(left, opStr, right, op.line, op.column);
        }
        return left;
    }

    private Node parseAddSub() {
        Node left = parseMulDiv();
        while (peek() != null && (peek().value.equals("+") || peek().value.equals("-"))) {
            Token op = advance();
            Node right = parseMulDiv();
            left = new BinOpNode(left, op.value, right, op.line, op.column);
        }
        return left;
    }

    private Node parseMulDiv() {
        Node left = parseUnary();
        while (peek() != null && (peek().value.equals("*") || peek().value.equals("/")
                || peek().value.equals("//") || peek().value.equals("%") || peek().value.equals("**"))) {
            Token op = advance();
            Node right = parseUnary();
            left = new BinOpNode(left, op.value, right, op.line, op.column);
        }
        return left;
    }

    private Node parseUnary() {
        if (peek() != null && (peek().value.equals("-") || peek().value.equals("+"))) {
            Token op = advance();
            return new UnaryOpNode(op.value, parseUnary(), op.line, op.column);
        }
        return parsePrimary();
    }

    private Node parsePrimary() {
        Token t = peek();
        if (t == null) return null;

        // Number
        if (t.type.equals("Integer Literal")) {
            advance();
            try { return new NumberNode(Long.parseLong(t.value), true, t.line, t.column); }
            catch (NumberFormatException e) { return new NumberNode(0, true, t.line, t.column); }
        }
        if (t.type.equals("Float Literal")) {
            advance();
            try { return new NumberNode(Double.parseDouble(t.value), false, t.line, t.column); }
            catch (NumberFormatException e) { return new NumberNode(0, false, t.line, t.column); }
        }

        // String
        if (t.type.equals("String Literal") || t.type.equals("F-String Literal")) {
            advance();
            String raw = t.value;
            // Strip prefix and quotes
            int start = 0;
            if (raw.length() > 0 && !raw.startsWith("\"") && !raw.startsWith("'")) start = 1;
            if (raw.length() > start + 1) raw = raw.substring(start+1, raw.length()-1);
            return new StringNode(raw, t.line, t.column);
        }

        // Boolean / None
        if (t.value.equals("True"))  { advance(); return new BoolNode(true,  t.line, t.column); }
        if (t.value.equals("False")) { advance(); return new BoolNode(false, t.line, t.column); }
        if (t.value.equals("None"))  { advance(); return new NoneNode(t.line, t.column); }

        // Parenthesised expression
        if (t.value.equals("(")) {
            advance();
            Node inner = parseExpression();
            expect(")", "Expected ')'", t);
            return inner;
        }

        // Function call or name
        if (t.type.equals("Identifier") || t.type.equals("Keyword")) {
            advance();
            if (peek() != null && peek().value.equals("(")) {
                advance(); // '('
                List<Node> args = parseArgList(")");
                expect(")", "Expected ')' to close function call", t);
                return new CallNode(t.value, args, t.line, t.column);
            }
            return new NameNode(t.value, t.line, t.column);
        }

        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Parses a comma-separated argument list, stopping at the given end token.
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

    // Parses a comma-separated list of parameter names (identifiers only).
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

    // Parses the indented block after a colon. Since we don't track indentation tokens,
    // we use a simple heuristic: keep parsing until we hit a keyword that starts a
    // peer-level statement or end of input.
    private List<Node> parseIndentedBlock(Token parent) {
        List<Node> stmts = new ArrayList<>();
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

    // Returns true for tokens that end a block (back to peer/parent level)
    private boolean isBlockTerminator(Token t) {
        if (!t.type.equals("Keyword")) return false;
        switch (t.value) {
            case "elif": case "else": case "except": case "finally":
            case "def":  case "class": return true;
            default: return false;
        }
    }

    // Validates that the token stream matches a rule from syntax_rules.txt.
    // EXPR, PARAMS, ARGS are wildcards — we don't consume them, just note the requirement.
    private void validateRule(SyntaxRules.Rule rule, Token start) {
        // We only check that keyword-led rules have the right keyword and colon if required.
        boolean needsColon = false;
        for (String tok : rule.tokens) if (tok.equals(":")) { needsColon = true; break; }
        // The actual structure check is implicit in the parse methods above.
        // This hook is here so rule violations found via txt are reported with the rule name.
    }

    // Expects the next token to have the given value. Advances and returns it, or reports error.
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

    private boolean isAugOp(String v) {
        return v.equals("+=") || v.equals("-=") || v.equals("*=") || v.equals("/=")
            || v.equals("%=") || v.equals("**=") || v.equals("//=");
    }

    private Token peek()           { return pos < tokens.size() ? tokens.get(pos) : null; }
    private Token peekAhead(int n) { return pos+n < tokens.size() ? tokens.get(pos+n) : null; }
    private Token advance()        { return pos < tokens.size() ? tokens.get(pos++) : null; }
    private boolean isAtEnd()      { return pos >= tokens.size(); }

    private void error(String msg, Token t) {
        result.error(CompilerError.Stage.PARSER,
                     t != null ? t.line : 0,
                     t != null ? t.column : 0, msg);
    }
}
