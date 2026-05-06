package lexer;

import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

// Loads keyword, operator, and separator lists from txt files next to the class.
public class Dictionary {
    private static String[] KEYWORDS   = new String[0];
    private static String[] OPERATORS  = new String[0];
    private static String[] SEPARATORS = new String[0];
    private static Set<String> KEYWORD_SET;
    private static Set<String> SEPARATOR_SET;

    private static final File CLASS_DIR = resolveClassDir();

    private static File resolveClassDir() {
        try {
            File loc = new File(Dictionary.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return loc.isDirectory() ? loc : loc.getParentFile();
        } catch (URISyntaxException e) {
            return new File(".");
        }
    }

    static {
        KEYWORDS   = load("dict/keywords.txt");
        OPERATORS  = load("dict/operators.txt");
        SEPARATORS = load("dict/separators.txt");
        KEYWORD_SET = new HashSet<>(java.util.Arrays.asList(KEYWORDS));
        SEPARATOR_SET = new HashSet<>(java.util.Arrays.asList(SEPARATORS));
        // Sort operators by length descending for longest-match optimization
        java.util.Arrays.sort(OPERATORS, (a, b) -> Integer.compare(b.length(), a.length()));
    }

    private static String[] load(String path) {
        File file = new File(CLASS_DIR, path);
        ArrayList<String> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) list.add(line);
            }
        } catch (IOException e) {
            System.err.println("[Dictionary] Cannot load " + file.getAbsolutePath());
        }
        return list.toArray(new String[0]);
    }

    public static boolean isKeyword(String w) {
        return KEYWORD_SET.contains(w);
    }

    public static boolean isSeparator(String s) {
        return SEPARATOR_SET.contains(s);
    }

    // Returns the longest matching operator at position i, or null.
    public static String matchOperator(String src, int i) {
        for (String op : OPERATORS)
            if (src.startsWith(op, i)) return op;
        return null;
    }
}
