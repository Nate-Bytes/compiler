package parser.nodes;
import java.util.List;
public class FuncDefNode extends Node {
    public String name; public List<String> params; public List<Node> body;
    public FuncDefNode(String name, List<String> params, List<Node> body, int line, int col) {
        super(line,col); this.name=name; this.params=params; this.body=body;
    }
}
