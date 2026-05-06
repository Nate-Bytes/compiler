package ir;

// One 3-address IR instruction. Every complex expression is broken into
// single-operation steps. The interpreter reads these top to bottom.
public class IRInstruction {
    public enum Op {
        ASSIGN,      // dest = src
        BINOP,       // dest = left OP right
        UNARYOP,     // dest = OP src
        LABEL,       // LABEL name  (jump target)
        JUMP,        // JUMP label
        JUMP_IF_FALSE, // JUMP_IF_FALSE cond label
        CALL,        // dest = CALL name arg1 arg2...
        PRINT,       // PRINT arg1 arg2...
        RETURN,      // RETURN val
        LOAD_CONST,  // dest = const
        FUNC_DEF,    // FUNC_DEF name param1 param2... END_LABEL
        FUNC_END,    // marks end of function body
        IMPORT       // IMPORT module alias
    }

    public final Op       op;
    public final String   dest;    // result variable or label
    public final String[] args;    // operands
    public final int      line;

    public IRInstruction(Op op, String dest, String[] args, int line) {
        this.op = op; this.dest = dest; this.args = args; this.line = line;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s", op.name()));
        if (dest != null) sb.append(dest).append(" ");
        if (args != null) for (String a : args) sb.append(a).append(" ");
        return sb.toString().trim();
    }
}
