package parser.nodes;
public class BoolNode extends Node {
    public boolean value;
    public BoolNode(boolean value, int line, int col) { super(line,col); this.value=value; }
}
