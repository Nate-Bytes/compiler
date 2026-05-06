package parser.nodes;
// x = expr  or  x += expr
public class AssignNode extends Node {
    public String target, op;
    public Node   value;
    public AssignNode(String target, String op, Node value, int line, int col) {
        super(line, col); this.target = target; this.op = op; this.value = value;
    }
}
