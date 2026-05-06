package parser.nodes;
// left OP right
public class BinOpNode extends Node {
    public Node left, right; public String op;
    public BinOpNode(Node left, String op, Node right, int line, int col) {
        super(line,col); this.left=left; this.op=op; this.right=right;
    }
}
