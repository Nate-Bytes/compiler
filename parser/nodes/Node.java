package parser.nodes;

// Base class for every AST node. Every node knows its line and column.
public abstract class Node {
    public int line, column;
    public Node(int line, int column) { this.line = line; this.column = column; }
}
