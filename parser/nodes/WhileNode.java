package parser.nodes;
import java.util.List;
public class WhileNode extends Node {
    public Node condition; public List<Node> body;
    public WhileNode(Node condition, List<Node> body, int line, int col) {
        super(line,col); this.condition=condition; this.body=body;
    }
}
