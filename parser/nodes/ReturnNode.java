package parser.nodes;
public class ReturnNode extends Node {
    public Node value; // null = bare return
    public ReturnNode(Node value, int line, int col) { super(line,col); this.value=value; }
}
