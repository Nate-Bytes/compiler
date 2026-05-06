package parser.nodes;
// A variable reference: just a name
public class NameNode extends Node {
    public String name;
    public NameNode(String name, int line, int col) { super(line,col); this.name=name; }
}
