package parser.nodes;
import java.util.List;
public class PrintNode extends Node {
    public List<Node> args;
    public PrintNode(List<Node> args, int line, int col) { super(line,col); this.args=args; }
}
