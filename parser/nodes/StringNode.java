package parser.nodes;
public class StringNode extends Node {
    public String value;
    public StringNode(String value, int line, int col) { super(line,col); this.value=value; }
}
