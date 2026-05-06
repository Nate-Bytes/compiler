package parser.nodes;
// -x, not x
public class UnaryOpNode extends Node {
    public String op; public Node operand;
    public UnaryOpNode(String op, Node operand, int line, int col) {
        super(line,col); this.op=op; this.operand=operand;
    }
}
