package core;

import java.util.ArrayList;

// Passed between every stage. Carries the source tokens/AST/IR forward,
// and accumulates errors. If hasErrors() is true the pipeline stops.
public class CompilerResult {
    public Object              data;    // stage output: token list, AST root, IR list, etc.
    public ArrayList<CompilerError> errors = new ArrayList<>();
    public StringBuilder       output = new StringBuilder(); // final program stdout

    public CompilerResult(Object data) { this.data = data; }

    public void addError(CompilerError e) { errors.add(e); }

    public boolean hasErrors() { return !errors.isEmpty(); }

    // Shorthand used by every stage
    public void error(CompilerError.Stage stage, int line, int col, String msg) {
        errors.add(new CompilerError(stage, line, col, msg));
    }
}
