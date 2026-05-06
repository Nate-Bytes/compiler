package parser;

import java.io.*;
import java.net.URISyntaxException;
import java.util.*;

// Loads statement grammar rules from syntax_rules.txt at runtime.
// Each rule maps a rule name to its expected token sequence.
// The Parser uses these to validate statement structure before building AST nodes.
public class SyntaxRules {

    // One rule entry: name and its expected token sequence
    public static class Rule {
        public final String   name;
        public final String[] tokens;  // e.g. ["if", "EXPR", ":"]
        public Rule(String name, String[] tokens) { this.name=name; this.tokens=tokens; }
    }

    private static final List<Rule> rules = new ArrayList<>();
    private static final File CLASS_DIR = resolveClassDir();

    private static File resolveClassDir() {
        try {
            File loc = new File(SyntaxRules.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return loc.isDirectory() ? loc : loc.getParentFile();
        } catch (URISyntaxException e) { return new File("."); }
    }

    static {
        File file = new File(CLASS_DIR, "dict/syntax_rules.txt");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String name   = line.substring(0, colon).trim();
                String rest   = line.substring(colon + 1).trim();
                String[] toks = rest.split("\\s+");
                rules.add(new Rule(name, toks));
            }
        } catch (IOException e) {
            System.err.println("[SyntaxRules] Cannot load syntax_rules.txt: " + e.getMessage());
        }
    }

    // Returns the rule whose leading keyword matches the given keyword token value.
    // e.g. "if" → returns the if_stmt rule
    public static Rule findByKeyword(String keyword) {
        for (Rule r : rules)
            if (r.tokens.length > 0 && r.tokens[0].equals(keyword)) return r;
        return null;
    }

    // Returns a rule by name directly (e.g. "assignment")
    public static Rule findByName(String name) {
        for (Rule r : rules) if (r.name.equals(name)) return r;
        return null;
    }

    public static List<Rule> all() { return rules; }
}
