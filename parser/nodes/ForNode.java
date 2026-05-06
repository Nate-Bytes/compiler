package parser.nodes;
import java.util.List;
public class ForNode extends Node {
    public String var; public Node iterable; public List<Node> body;
    public ForNode(String var, Node iterable, List<Node> body, int line, int col) {
        super(line,col); this.var=var; this.iterable=iterable; this.body=body;
    }
}
