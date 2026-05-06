package parser.nodes;

import java.util.ArrayList;
import java.util.List;

public class Node {
    public enum Kind {
        PROGRAM,
        BLOCK,
        ASSIGN,
        PRINT,
        IF,
        WHILE,
        FOR,
        FUNC_DEF,
        RETURN,
        IMPORT,
        CALL,
        NAME,
        NUMBER,
        STRING,
        BOOL,
        NONE,
        BINOP,
        UNARYOP,
        PASS,
        BREAK,
        CONTINUE
    }

    public final Kind kind;
    public final int line;
    public final int column;
    public String name;
    public String op;
    public Object value;
    public String alias;
    public List<String> params;
    public List<Node> children;

    public Node(Kind kind, int line, int column) {
        this.kind = kind;
        this.line = line;
        this.column = column;
        this.params = new ArrayList<>();
        this.children = new ArrayList<>();
    }

    public static Node program(List<Node> statements) {
        Node node = new Node(Kind.PROGRAM, 0, 0);
        node.children = statements != null ? statements : new ArrayList<>();
        return node;
    }

    public static Node block(List<Node> statements, int line, int column) {
        Node node = new Node(Kind.BLOCK, line, column);
        node.children = statements != null ? statements : new ArrayList<>();
        return node;
    }
}
