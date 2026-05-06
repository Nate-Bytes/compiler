package interpreter;

import core.*;
import ir.*;
import java.util.*;

// Walks the IR instruction list and executes the program.
// Uses a call stack and environment map to track variables and function calls.
public class Interpreter {

    // One stack frame — holds local variables and the return address
    private static class Frame {
        String funcName;
        Map<String, Object> vars = new LinkedHashMap<>();
        int returnTo; // instruction index to return to
        Frame(String name, int returnTo) { this.funcName = name; this.returnTo = returnTo; }
    }

    private final List<IRInstruction> instructions;
    private final CompilerResult      result;
    private final Map<String, Integer> labels    = new HashMap<>(); // label → instruction index
    private final Map<String, Integer> funcDefs  = new HashMap<>(); // funcName → instruction index
    private final Map<String, List<String>> funcParams = new HashMap<>();
    private final Deque<Frame>         callStack = new ArrayDeque<>();
    private final Map<String, Object>  globals   = new LinkedHashMap<>();
    private int ip = 0; // instruction pointer
    private String pendingCallDest = null; // destination var for user-defined function call
    private static final int MAX_STEPS = 100_000; // prevents infinite loops

    public Interpreter(List<IRInstruction> instructions, CompilerResult result) {
        this.instructions = instructions;
        this.result = result;
    }

    public static CompilerResult run(CompilerResult irResult) {
        CompilerResult out = new CompilerResult(irResult.data);
        out.output = irResult.output;
        @SuppressWarnings("unchecked")
        List<IRInstruction> instrs = (List<IRInstruction>) irResult.data;
        new Interpreter(instrs, out).execute();
        return out;
    }

    private void execute() {
        System.err.println("DEBUG: Starting execution");
        // First pass — index all labels and function definitions
        for (int i = 0; i < instructions.size(); i++) {
            IRInstruction ins = instructions.get(i);
            if (ins.op == IRInstruction.Op.LABEL)    labels.put(ins.dest, i);
            if (ins.op == IRInstruction.Op.FUNC_END) labels.put(ins.dest, i);
            if (ins.op == IRInstruction.Op.FUNC_DEF) {
                funcDefs.put(ins.dest, i);
                List<String> params = new ArrayList<>();
                // args[0] = endLabel, args[1..] = params
                for (int j = 1; j < ins.args.length; j++) params.add(ins.args[j]);
                funcParams.put(ins.dest, params);
            }
        }
        System.err.println("DEBUG: Found " + funcDefs.size() + " function definitions");
        System.err.println("DEBUG: Found " + instructions.size() + " total instructions");
        callStack.push(new Frame("<module>", -1));
        int steps = 0;

        while (ip < instructions.size()) {
            if (++steps > MAX_STEPS) {
                error("RuntimeError: maximum execution steps exceeded (possible infinite loop)", 0);
                break;
            }
            IRInstruction ins = instructions.get(ip);
            ip++;
            System.err.println("DEBUG: Executing " + ins.op + " at ip=" + (ip-1));

            switch (ins.op) {
                case LOAD_CONST:  setVar(ins.dest, parseConst(ins.args[0])); break;
                case ASSIGN:      setVar(ins.dest, getVar(ins.args[0])); break;
                case BINOP:       setVar(ins.dest, evalBinOp(ins.args[0], ins.args[1], ins.args[2], ins.line)); break;
                case UNARYOP:     setVar(ins.dest, evalUnary(ins.args[0], ins.args[1], ins.line)); break;

                case LABEL:       /* already indexed */ break;
                case FUNC_DEF:    // skip over function body during normal execution
                    String endLbl = ins.args[0];
                    Integer skipTo = labels.get(endLbl);
                    System.err.println("DEBUG: FUNC_DEF " + ins.dest + " endLabel=" + endLbl + " skipTo=" + skipTo);
                    if (skipTo != null) {
                        ip = skipTo;  // Jump to FUNC_END, the loop will increment ip past it
                        System.err.println("DEBUG:   Jumping to ip=" + ip);
                    }
                    break;
                case FUNC_END:    break;

                case JUMP:
                    Integer jTarget = labels.get(ins.args[0]);
                    if (jTarget != null) ip = jTarget + 1;
                    else error("RuntimeError: unknown label " + ins.args[0], ins.line);
                    break;

                case JUMP_IF_FALSE:
                    Object condVal = getVar(ins.args[0]);
                    if (!isTruthy(condVal)) {
                        Integer jfTarget = labels.get(ins.args[1]);
                        if (jfTarget != null) ip = jfTarget + 1;
                    }
                    break;

                case PRINT:
                    System.err.println("DEBUG: PRINT instruction with " + ins.args.length + " args");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < ins.args.length; i++) {
                        System.err.println("DEBUG:   arg[" + i + "] = " + ins.args[i] + " -> " + getVar(ins.args[i]));
                        if (i > 0) sb.append(" ");
                        sb.append(pyStr(getVar(ins.args[i])));
                    }
                    result.output.append(sb).append("\n");
                    break;

                case CALL:
                    // For user-defined functions, track the destination variable
                    String funcName = ins.args[0];
                    if (funcDefs.containsKey(funcName)) {
                        pendingCallDest = ins.dest;
                        System.err.println("DEBUG: Calling user-defined function " + funcName + ", dest=" + ins.dest);
                    }
                    Object callResult = handleCall(funcName,
                        Arrays.copyOfRange(ins.args, 1, ins.args.length), ins.line);
                    setVar(ins.dest, callResult);
                    System.err.println("DEBUG: CALL result for " + funcName + " = " + callResult);
                    break;

                case RETURN:
                    Object retVal = ins.args.length > 0 ? getVar(ins.args[0]) : null;
                    System.err.println("DEBUG: RETURN retVal=" + retVal + ", pendingCallDest=" + pendingCallDest);
                    if (callStack.size() > 1) {
                        Frame returning = callStack.pop();
                        ip = returning.returnTo;
                        // For user-defined functions, store return value in the destination variable
                        if (pendingCallDest != null) {
                            System.err.println("DEBUG: Setting " + pendingCallDest + " to " + retVal);
                            setVar(pendingCallDest, retVal);
                            pendingCallDest = null;
                        }
                    } else {
                        ip = instructions.size(); // return from top level = exit
                    }
                    break;

                case IMPORT:
                    // We don't execute imports, just note the name exists
                    setVar(ins.dest.isEmpty() ? ins.args[0] : ins.dest, "<module:" + ins.args[0] + ">");
                    break;
            }

            if (result.hasErrors()) break;
        }
    }

    // Handles built-in and user-defined function calls
    private Object handleCall(String name, String[] argKeys, int line) {
        List<Object> argVals = new ArrayList<>();
        for (String k : argKeys) argVals.add(getVar(k));

        switch (name) {
            case "print": {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < argVals.size(); i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(pyStr(argVals.get(i)));
                }
                result.output.append(sb).append("\n");
                return null;
            }
            case "int":   return argVals.isEmpty() ? 0L : toLong(argVals.get(0), line);
            case "float": return argVals.isEmpty() ? 0.0 : toDouble(argVals.get(0), line);
            case "str":   return argVals.isEmpty() ? "" : pyStr(argVals.get(0));
            case "bool":  return argVals.isEmpty() ? false : isTruthy(argVals.get(0));
            case "len": {
                if (argVals.isEmpty()) { error("TypeError: len() requires an argument", line); return 0L; }
                Object v = argVals.get(0);
                if (v instanceof String) return (long)((String)v).length();
                error("TypeError: object has no len()", line); return 0L;
            }
            case "range": {
                long start=0, end=0, step=1;
                if (argVals.size()==1)      { end   = toLong(argVals.get(0), line); }
                else if (argVals.size()>=2) { start = toLong(argVals.get(0), line); end = toLong(argVals.get(1), line); }
                if (argVals.size()>=3)      { step  = toLong(argVals.get(2), line); }
                List<Object> lst = new ArrayList<>();
                for (long v = start; step>0?v<end:v>end; v+=step) lst.add(v);
                return lst;
            }
            case "abs":   return argVals.isEmpty() ? 0L : absVal(argVals.get(0), line);
            case "max": { if (argVals.isEmpty()) return null;
                          Object m = argVals.get(0); for (int i=1;i<argVals.size();i++) if (compare(argVals.get(i),m)>0) m=argVals.get(i); return m; }
            case "min": { if (argVals.isEmpty()) return null;
                          Object m = argVals.get(0); for (int i=1;i<argVals.size();i++) if (compare(argVals.get(i),m)<0) m=argVals.get(i); return m; }
            case "round": {
                if (argVals.isEmpty()) return 0L;
                Object v = argVals.get(0);
                if (v instanceof Double) return Math.round((Double)v);
                return v;
            }
            default:
                // User-defined function
                Integer funcIdx = funcDefs.get(name);
                if (funcIdx == null) { error("NameError: name '" + name + "' is not defined", line); return null; }
                Frame frame = new Frame(name, ip);
                List<String> params = funcParams.getOrDefault(name, new ArrayList<>());
                for (int i = 0; i < params.size(); i++)
                    frame.vars.put(params.get(i), i < argVals.size() ? argVals.get(i) : null);
                callStack.push(frame);
                ip = funcIdx + 1; // jump into function body
                return null; // actual return value set via RETURN
        }
    }

    // ── Variable resolution ───────────────────────────────────────────────────

    private void setVar(String name, Object val) {
        if (!callStack.isEmpty()) callStack.peek().vars.put(name, val);
        else globals.put(name, val);
    }

    private Object getVar(String name) {
        if (name == null) return null;
        // Check current frame first, then globals
        for (Frame f : callStack) {
            if (f.vars.containsKey(name)) return f.vars.get(name);
        }
        if (globals.containsKey(name)) return globals.get(name);
        // Handle special runtime values
        if (name.equals("None")) return null;
        if (name.equals("True")) return true;
        if (name.equals("False")) return false;
        // String constant
        if (name.startsWith("\"") && name.endsWith("\""))
            return name.substring(1, name.length()-1);
        // Number constant
        try { return Long.parseLong(name); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(name); } catch (NumberFormatException ignored) {}
        return null;
    }

    // ── Evaluation ───────────────────────────────────────────────────────────

    private Object evalBinOp(String leftKey, String op, String rightKey, int line) {
        // Special: iterating a list by index
        if (op.equals("[]")) {
            Object container = getVar(leftKey.replace(".len",""));
            Object idx = getVar(rightKey);
            if (container instanceof List) {
                List<?> lst = (List<?>) container;
                long i = toLong(idx, line);
                if (i >= 0 && i < lst.size()) return lst.get((int)i);
                error("IndexError: list index out of range", line); return null;
            }
            return null;
        }
        // Len of container
        if (leftKey.endsWith(".len")) {
            Object container = getVar(leftKey.replace(".len",""));
            long containerLen = 0;
            if (container instanceof List) containerLen = ((List<?>)container).size();
            Object right = getVar(rightKey);
            return evalNum(containerLen, op, right, line);
        }

        Object left  = getVar(leftKey);
        Object right = getVar(rightKey);

        switch (op) {
            case "+":
                if (left instanceof String || right instanceof String)
                    return pyStr(left) + pyStr(right);
                return evalNum(left, op, right, line);
            case "-": case "*": case "/": case "//": case "%": case "**":
                return evalNum(left, op, right, line);
            case "==": return pyEquals(left, right);
            case "!=": return !pyEquals(left, right);
            case "<":  return compare(left, right) < 0;
            case ">":  return compare(left, right) > 0;
            case "<=": return compare(left, right) <= 0;
            case ">=": return compare(left, right) >= 0;
            case "and": return isTruthy(left) ? right : left;
            case "or":  return isTruthy(left) ? left : right;
            case "in":
                if (right instanceof String) return ((String)right).contains(pyStr(left));
                if (right instanceof List)   return ((List<?>)right).contains(left);
                return false;
            default:
                error("RuntimeError: unknown operator '" + op + "'", line);
                return null;
        }
    }

    private Object evalUnary(String op, String valKey, int line) {
        Object val = getVar(valKey);
        switch (op) {
            case "-":
                if (val instanceof Long)   return -(Long) val;
                if (val instanceof Double) return -(Double) val;
                error("TypeError: unary '-' on non-number", line); return null;
            case "+": return val;
            case "not": return !isTruthy(val);
            default: return null;
        }
    }

    private Object evalNum(Object left, String op, Object right, int line) {
        boolean useFloat = (left instanceof Double) || (right instanceof Double);
        double l = toDouble(left, line), r = toDouble(right, line);
        double res;
        switch (op) {
            case "+":  res = l + r; break;
            case "-":  res = l - r; break;
            case "*":  res = l * r; break;
            case "/":
                if (r == 0) { error("ZeroDivisionError: division by zero", line); return null; }
                return l / r; // always float in Python 3
            case "//":
                if (r == 0) { error("ZeroDivisionError: division by zero", line); return null; }
                res = Math.floor(l / r); break;
            case "%":
                if (r == 0) { error("ZeroDivisionError: modulo by zero", line); return null; }
                res = l % r; break;
            case "**": res = Math.pow(l, r); break;
            default: error("Unknown operator " + op, line); return null;
        }
        if (useFloat || op.equals("/")) return res;
        return (long) res;
    }

    // ── Python value helpers ──────────────────────────────────────────────────

    private Object parseConst(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length()-1);
        if (s.equals("True")) return true;
        if (s.equals("False")) return false;
        if (s.equals("None")) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        return s;
    }

    private String pyStr(Object v) {
        if (v == null) return "None";
        if (v instanceof Boolean) return ((Boolean)v) ? "True" : "False";
        if (v instanceof Double) {
            // Print like Python: 3.0 not 3.000000
            double d = (Double) v;
            if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long)d) + ".0";
            return String.valueOf(d);
        }
        if (v instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> lst = (List<?>)v;
            for (int i=0;i<lst.size();i++) { if(i>0)sb.append(", "); sb.append(pyStr(lst.get(i))); }
            return sb.append("]").toString();
        }
        return String.valueOf(v);
    }

    private boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Long)    return (Long) v != 0;
        if (v instanceof Double)  return (Double) v != 0.0;
        if (v instanceof String)  return !((String) v).isEmpty();
        if (v instanceof List)    return !((List<?>) v).isEmpty();
        return true;
    }

    private boolean pyEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Long && b instanceof Double) return ((Long)a).doubleValue() == (Double)b;
        if (a instanceof Double && b instanceof Long) return (Double)a == ((Long)b).doubleValue();
        return a.equals(b);
    }

    private int compare(Object a, Object b) {
        if (a instanceof Long && b instanceof Long) return Long.compare((Long)a,(Long)b);
        return Double.compare(toDouble(a,0), toDouble(b,0));
    }

    private long toLong(Object v, int line) {
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Double)  return ((Double)v).longValue();
        if (v instanceof Boolean) return (Boolean)v ? 1L : 0L;
        if (v instanceof String)  try { return Long.parseLong((String)v); } catch(NumberFormatException ignored){}
        error("TypeError: cannot convert to int", line); return 0;
    }

    private double toDouble(Object v, int line) {
        if (v instanceof Double)  return (Double) v;
        if (v instanceof Long)    return ((Long) v).doubleValue();
        if (v instanceof Boolean) return (Boolean)v ? 1.0 : 0.0;
        if (v instanceof String)  try { return Double.parseDouble((String)v); } catch(NumberFormatException ignored){}
        return 0.0;
    }

    private Object absVal(Object v, int line) {
        if (v instanceof Long)   return Math.abs((Long)v);
        if (v instanceof Double) return Math.abs((Double)v);
        error("TypeError: bad argument to abs()", line); return null;
    }

    private void error(String msg, int line) {
        result.error(CompilerError.Stage.RUNTIME, line, 0, msg);
    }
}
