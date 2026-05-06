package core;

// One structured error — knows which stage it came from, where it happened, and what went wrong.
public class CompilerError {
    public enum Stage { LEXER, PARSER, SEMANTIC, IR, CODEGEN, RUNTIME }

    public final Stage  stage;
    public final int    line;
    public final int    column;
    public final String message;

    public CompilerError(Stage stage, int line, int column, String message) {
        this.stage   = stage;
        this.line    = line;
        this.column  = column;
        this.message = message;
    }

    public String toString() {
        return stage + " Error [line " + line + ", col " + column + "]: " + message;
    }
}
