package core;

import lexer.Lexer;
import parser.Parser;
import semantic.SemanticAnalyzer;
import ir.IRGenerator;
import interpreter.Interpreter;

// Runs all five compiler stages in order.
// Stops at the first stage that produces errors.
public class Pipeline {

    public static CompilerResult run(String source) {
        // Stage 1 — Lex
        CompilerResult r = Lexer.tokenize(source);
        if (r.hasErrors()) return r;

        // Stage 2 — Parse
        r = Parser.parse(r);
        if (r.hasErrors()) return r;

        // Stage 3 — Semantic analysis
        r = SemanticAnalyzer.analyze(r);
        if (r.hasErrors()) return r;

        // Stage 4 — IR generation
        r = IRGenerator.generate(r);
        if (r.hasErrors()) return r;

        // Stage 5 — Interpret / execute
        r = Interpreter.run(r);
        return r;
    }
}
