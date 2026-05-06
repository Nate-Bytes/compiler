package lexer;

import core.*;
import java.util.ArrayList;
import java.util.List;

// Scans source text character by character and produces a token list.
// Tracks line numbers so every token knows exactly where it came from.
public class Lexer {

    public static CompilerResult tokenize(String source) {
        ArrayList<Token> tokens = new ArrayList<>();
        CompilerResult   result = new CompilerResult(tokens);
        int i = 0, len = source.length(), line = 1, lineStart = 0;
        List<Integer> indentStack = new ArrayList<>();
        indentStack.add(0);
        int currentIndent = 0;
        boolean afterNewline = true;

        while (i < len) {
            char c   = source.charAt(i);
            int  col = i - lineStart + 1;

            if (c == '\n') {
                tokens.add(new Token("\n", "NEWLINE", line, col));
                line++; lineStart = i + 1; i++;
                afterNewline = true;
                currentIndent = 0;
                continue;
            }

            if (afterNewline && Character.isWhitespace(c) && c != '\n') {
                if (c == ' ') currentIndent++;
                else if (c == '\t') currentIndent += 4; // assume tab = 4 spaces
                i++; continue;
            }

            if (afterNewline) {
                // Handle indentation changes
                int prevIndent = indentStack.get(indentStack.size() - 1);
                if (currentIndent > prevIndent) {
                    indentStack.add(currentIndent);
                    tokens.add(new Token("", "INDENT", line, col));
                } else if (currentIndent < prevIndent) {
                    while (indentStack.size() > 1 && indentStack.get(indentStack.size() - 1) > currentIndent) {
                        indentStack.remove(indentStack.size() - 1);
                        tokens.add(new Token("", "DEDENT", line, col));
                    }
                    if (indentStack.get(indentStack.size() - 1) != currentIndent) {
                        result.error(CompilerError.Stage.LEXER, line, col, "Indentation error");
                    }
                }
                afterNewline = false;
            }

            // Skip whitespace not at line start
            if (Character.isWhitespace(c)) { i++; continue; }

            // Comment — skip to end of line
            if (c == '#') {
                while (i < len && source.charAt(i) != '\n') i++;
                continue;
            }

            // String prefix: f"", r"", b""
            int prefixLen = 0;
            if ((c=='f'||c=='F'||c=='r'||c=='R'||c=='b'||c=='B'||c=='u'||c=='U') && i+1<len) {
                char nx = source.charAt(i + 1);
                if (nx == '"' || nx == '\'') prefixLen = 1;
            }

            // String literal
            if (prefixLen > 0 || c == '"' || c == '\'') {
                int start = i;
                i += prefixLen;
                char q = source.charAt(i);
                String ttype = prefixLen > 0 && Character.toLowerCase(source.charAt(start)) == 'f'
                               ? "F-String Literal" : "String Literal";

                // Triple-quoted
                if (i+2 < len && source.charAt(i+1)==q && source.charAt(i+2)==q) {
                    i += 3;
                    boolean closed = false;
                    while (i+2 < len) {
                        if (source.charAt(i)==q && source.charAt(i+1)==q && source.charAt(i+2)==q) {
                            i += 3; closed = true; break;
                        }
                        if (source.charAt(i) == '\n') { line++; lineStart = i+1; }
                        if (source.charAt(i) == '\\') i++; i++;
                    }
                    if (!closed) result.error(CompilerError.Stage.LEXER, line, col, "Unterminated triple-quoted string");
                } else {
                    i++;
                    boolean closed = false;
                    while (i < len) {
                        char ch = source.charAt(i);
                        if (ch == '\\') { i += 2; continue; }
                        if (ch == q)   { i++; closed = true; break; }
                        if (ch == '\n') { result.error(CompilerError.Stage.LEXER, line, col, "Unterminated string literal"); break; }
                        i++;
                    }
                    if (!closed && i >= len)
                        result.error(CompilerError.Stage.LEXER, line, col, "Unterminated string literal");
                }
                tokens.add(new Token(source.substring(start, i), ttype, line, col));
                continue;
            }

            // Number
            if (Character.isDigit(c)) {
                int start = i; boolean isFloat = false;
                if (c=='0' && i+1<len && (source.charAt(i+1)=='x'||source.charAt(i+1)=='X')) {
                    i += 2; while (i<len && isHex(source.charAt(i))) i++;
                } else if (c=='0' && i+1<len && (source.charAt(i+1)=='b'||source.charAt(i+1)=='B')) {
                    i += 2; while (i<len && (source.charAt(i)=='0'||source.charAt(i)=='1')) i++;
                } else if (c=='0' && i+1<len && (source.charAt(i+1)=='o'||source.charAt(i+1)=='O')) {
                    i += 2; while (i<len && source.charAt(i)>='0' && source.charAt(i)<='7') i++;
                } else {
                    while (i<len && Character.isDigit(source.charAt(i))) i++;
                    if (i<len && source.charAt(i)=='.') { isFloat=true; i++; while (i<len && Character.isDigit(source.charAt(i))) i++; }
                    if (i<len && (source.charAt(i)=='e'||source.charAt(i)=='E')) {
                        isFloat=true; i++;
                        if (i<len && (source.charAt(i)=='+'||source.charAt(i)=='-')) i++;
                        while (i<len && Character.isDigit(source.charAt(i))) i++;
                    }
                }
                if (i<len && (source.charAt(i)=='j'||source.charAt(i)=='J')) {
                    i++; tokens.add(new Token(source.substring(start,i), "Complex Literal", line, col));
                } else {
                    tokens.add(new Token(source.substring(start,i), isFloat?"Float Literal":"Integer Literal", line, col));
                }
                continue;
            }

            // Identifier or keyword
            if (Character.isLetter(c) || c=='_') {
                int start = i;
                while (i<len && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i)=='_')) i++;
                String word = source.substring(start, i);
                tokens.add(new Token(word, Dictionary.isKeyword(word) ? "Keyword" : "Identifier", line, col));
                continue;
            }

            // Operator
            String op = Dictionary.matchOperator(source, i);
            if (op != null) {
                tokens.add(new Token(op, "Operator", line, col));
                i += op.length(); continue;
            }

            // Separator
            String sep = String.valueOf(c);
            if (Dictionary.isSeparator(sep)) {
                tokens.add(new Token(sep, "Separator", line, col));
                i++; continue;
            }

            result.error(CompilerError.Stage.LEXER, line, col, "Unexpected character '" + c + "'");
            i++;
        }

        // Emit remaining DEDENTs
        while (indentStack.size() > 1) {
            indentStack.remove(indentStack.size() - 1);
            tokens.add(new Token("", "DEDENT", line, 1));
        }

        return result;
    }

    private static boolean isHex(char c) {
        return (c>='0'&&c<='9')||(c>='a'&&c<='f')||(c>='A'&&c<='F');
    }
}
