package parser.nodes;
import java.util.List;
public class CallNode extends Node {
    public String name; public List<Node> args;
    public CallNode(String name, List<Node> args, int line, int col) {
        super(line,col); this.name=name; this.args=args;
    }
}
