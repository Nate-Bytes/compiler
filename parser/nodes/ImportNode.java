package parser.nodes;
public class ImportNode extends Node {
    public String module, alias;
    public ImportNode(String module, String alias, int line, int col) {
        super(line,col); this.module=module; this.alias=alias;
    }
}
