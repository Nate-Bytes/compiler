package lexer;

// One token: its text, type, line number, and column.
public class Token {
    public final String value;
    public final String type;
    public final int    line;
    public final int    column;

    public Token(String value, String type, int line, int column) {
        this.value  = value;
        this.type   = type;
        this.line   = line;
        this.column = column;
    }

    public String toString() {
        return "[" + type + " '" + value + "' L" + line + ":C" + column + "]";
    }
}
