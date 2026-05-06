package lexer;

import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;

// Loads keyword, operator, and separator lists from txt files next to the class.
public class Dictionary {
    private static String[] KEYWORDS   = new String[0];
    private static String[] OPERATORS  = new String[0];
    private static String[] SEPARATORS = new String[0];

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
        for (String k : KEYWORDS) if (k.equals(w)) return true;
        return false;
    }

    public static boolean isSeparator(String s) {
        for (String sep : SEPARATORS) if (sep.equals(s)) return true;
        return false;
    }

    // Returns the longest matching operator at position i, or null.
    public static String matchOperator(String src, int i) {
        for (String op : OPERATORS)
            if (src.startsWith(op, i)) return op;
        return null;
    }
}
