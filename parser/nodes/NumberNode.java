package parser.nodes;
public class NumberNode extends Node {
    public double value; public boolean isInt;
    public NumberNode(double value, boolean isInt, int line, int col) {
        super(line,col); this.value=value; this.isInt=isInt;
    }
}
