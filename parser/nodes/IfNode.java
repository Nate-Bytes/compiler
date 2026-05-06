package parser.nodes;
import java.util.List;
public class IfNode extends Node {
    public Node condition;
    public List<Node> body, elseBody;  // elseBody holds elif/else chain
    public IfNode(Node condition, List<Node> body, List<Node> elseBody, int line, int col) {
        super(line,col); this.condition=condition; this.body=body; this.elseBody=elseBody;
    }
}
