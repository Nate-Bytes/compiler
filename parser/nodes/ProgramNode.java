package parser.nodes;
import java.util.List;
// Root of the AST — holds all top-level statements.
public class ProgramNode extends Node {
    public List<Node> statements;
    public ProgramNode(List<Node> statements) { super(0,0); this.statements = statements; }
}
