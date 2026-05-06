import core.Pipeline;
import core.CompilerResult;
import ir.IRInstruction;
import parser.nodes.Node;
import java.util.List;

public class TestFix {
    public static void main(String[] args) {
        String code = "def greet(name):\n" +
                      "    return \"Hello, \" + name + \"!\"\n" +
                      "\n" +
                      "print(greet(\"World\"))\n";
        
        CompilerResult lexResult = lexer.Lexer.tokenize(code);
        if (lexResult.hasErrors()) {
            System.out.println("LEXER ERRORS:");
            lexResult.errors.forEach(e -> System.out.println("  " + e.message));
            return;
        }
        
        CompilerResult parseResult = parser.Parser.parse(lexResult);
        if (parseResult.hasErrors()) {
            System.out.println("PARSER ERRORS:");
            parseResult.errors.forEach(e -> System.out.println("  " + e.message));
            return;
        }
        
        Node program = (Node) parseResult.data;
        System.out.println("Parse tree statements:");
        for (int i = 0; i < program.children.size(); i++) {
            Node n = program.children.get(i);
            System.out.println("  [" + i + "] " + n.kind);
        }
        
        CompilerResult semResult = semantic.SemanticAnalyzer.analyze(parseResult);
        if (semResult.hasErrors()) {
            System.out.println("\nSEMANTIC ERRORS:");
            semResult.errors.forEach(e -> System.out.println("  " + e.message));
            return;
        }
        
        CompilerResult irResult = ir.IRGenerator.generate(semResult);
        if (irResult.hasErrors()) {
            System.out.println("\nIR ERRORS:");
            irResult.errors.forEach(e -> System.out.println("  " + e.message));
            return;
        }
        
        @SuppressWarnings("unchecked")
        List<IRInstruction> instructions = (List<IRInstruction>) irResult.data;
        System.out.println("\nIR Instructions:");
        for (int i = 0; i < instructions.size(); i++) {
            IRInstruction ins = instructions.get(i);
            System.out.println("  [" + i + "] " + ins.op + " dest=" + ins.dest + " args=" + java.util.Arrays.toString(ins.args));
        }
    }
}



