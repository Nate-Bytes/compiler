package ui;

import core.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

// Main window. Editor left, output right. Looks like Programmiz.
public class CompilerUI extends JFrame {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final Color BG          = new Color(18,  18,  24);
    private static final Color SURFACE     = new Color(26,  26,  35);
    private static final Color GUTTER      = new Color(22,  22,  30);
    private static final Color BORDER_COL  = new Color(48,  48,  64);
    private static final Color ACCENT      = new Color(99,  179, 237);
    private static final Color ACCENT_HOV  = new Color(129, 199, 247);
    private static final Color RUN_GREEN   = new Color(72,  199, 142);
    private static final Color RUN_HOV     = new Color(92,  219, 162);
    private static final Color FG          = new Color(212, 212, 220);
    private static final Color FG_DIM      = new Color(110, 110, 130);
    private static final Color ERR_FG      = new Color(255, 100, 100);
    private static final Color ERR_BG      = new Color(40,  20,  20);
    private static final Color WARN_FG     = new Color(255, 200,  80);
    private static final Color OUTPUT_FG   = new Color(180, 255, 180);
    private static final Color LINE_HL     = new Color(32,  32,  44);

    // ── Syntax highlight colors ───────────────────────────────────────────────
    private static final Color SH_KEYWORD  = new Color(197, 134, 192);
    private static final Color SH_STRING   = new Color(206, 145, 120);
    private static final Color SH_NUMBER   = new Color(181, 206, 168);
    private static final Color SH_COMMENT  = new Color(106, 153, 85);
    private static final Color SH_BUILTIN  = new Color(220, 220, 170);
    private static final Color SH_FUNC     = new Color(100, 180, 255);

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "False","None","True","and","as","assert","async","await","break",
        "class","continue","def","del","elif","else","except","finally",
        "for","from","global","if","import","in","is","lambda","nonlocal",
        "not","or","pass","raise","return","try","while","with","yield"));
    private static final Set<String> BUILTINS = new HashSet<>(Arrays.asList(
        "print","input","len","range","type","int","float","str","bool",
        "list","dict","tuple","set","abs","max","min","sum","round","open","super"));

    private static final String SAMPLE =
        "# Welcome to Python Compiler\n" +
        "\n" +
        "def greet(name):\n" +
        "    message = \"Hello, \" + name + \"!\"\n" +
        "    return message\n" +
        "\n" +
        "def factorial(n):\n" +
        "    result = 1\n" +
        "    for i in range(1, n + 1):\n" +
        "        result = result * i\n" +
        "    return result\n" +
        "\n" +
        "print(greet(\"World\"))\n" +
        "print(\"5! =\", factorial(5))\n" +
        "\n" +
        "x = 10\n" +
        "y = 3\n" +
        "print(\"Sum:\", x + y)\n" +
        "print(\"Division:\", x / y)\n";

    // ── Components ────────────────────────────────────────────────────────────
    private JTextPane   editor;
    private JTextArea   lineNumbers;
    private JTextPane   outputPane;
    private JLabel      statusLabel;
    private JButton     runButton;
    private boolean     highlighting = false;

    public CompilerUI() {
        super("Python Compiler");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 720);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        buildUI();
        editor.setText(SAMPLE);
        SwingUtilities.invokeLater(() -> {
            editor.setCaretPosition(0);
            refreshLineNumbers();
            applySyntaxHighlight();
        });
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        setContentPane(root);

        root.add(buildTopBar(),    BorderLayout.NORTH);
        root.add(buildSplitPane(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(SURFACE);
        bar.setBorder(new MatteBorder(0,0,1,0,BORDER_COL));
        bar.setPreferredSize(new Dimension(0, 48));

        // Logo
        JLabel logo = new JLabel("  🐍  Python Compiler");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        logo.setForeground(FG);

        // Buttons panel
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btns.setBackground(SURFACE);

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(FG_DIM);

        runButton = makeButton("▶  Run", RUN_GREEN, RUN_HOV, Color.WHITE);
        runButton.addActionListener(e -> runCode());

        JButton clearBtn = makeButton("Clear", SURFACE, new Color(55,55,72), FG_DIM);
        clearBtn.setBorder(new CompoundBorder(
            new LineBorder(BORDER_COL, 1),
            new EmptyBorder(5,14,5,14)));
        clearBtn.addActionListener(e -> {
            outputPane.setText("");
            setStatus("Ready", FG_DIM);
        });

        btns.add(statusLabel);
        btns.add(clearBtn);
        btns.add(runButton);

        bar.add(logo, BorderLayout.WEST);
        bar.add(btns, BorderLayout.EAST);
        return bar;
    }

    private JSplitPane buildSplitPane() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildEditorPanel(), buildOutputPanel());
        split.setDividerLocation(620);
        split.setDividerSize(4);
        split.setBackground(BORDER_COL);
        split.setBorder(null);
        return split;
    }

    private JPanel buildEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);

        // Tab header
        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabBar.setBackground(SURFACE);
        tabBar.setBorder(new MatteBorder(0,0,1,0,BORDER_COL));
        JLabel tab = new JLabel("  main.py  ");
        tab.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tab.setForeground(ACCENT);
        tab.setOpaque(true); tab.setBackground(BG);
        tab.setBorder(new MatteBorder(2,0,0,0,ACCENT));
        tab.setPreferredSize(new Dimension(90, 32));
        tab.setHorizontalAlignment(SwingConstants.CENTER);
        tabBar.add(tab);

        // Editor
        editor = new JTextPane();
        editor.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
        editor.setBackground(BG);
        editor.setForeground(FG);
        editor.setCaretColor(ACCENT);
        editor.setSelectionColor(new Color(50,80,120));
        editor.setBorder(new EmptyBorder(8,8,8,8));

        // Line numbers
        lineNumbers = new JTextArea("1");
        lineNumbers.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
        lineNumbers.setBackground(GUTTER);
        lineNumbers.setForeground(FG_DIM);
        lineNumbers.setEditable(false);
        lineNumbers.setFocusable(false);
        lineNumbers.setBorder(new EmptyBorder(8,8,8,8));
        lineNumbers.setPreferredSize(new Dimension(40, Integer.MAX_VALUE));

        JScrollPane scroll = new JScrollPane(editor);
        scroll.setBorder(null);
        scroll.setRowHeaderView(lineNumbers);
        scroll.getViewport().setBackground(BG);
        scroll.getRowHeader().setBackground(GUTTER);

        // Ctrl+Enter = run
        editor.getInputMap().put(KeyStroke.getKeyStroke("ctrl ENTER"), "run");
        editor.getActionMap().put("run", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { runCode(); }
        });

        // Live syntax highlight + line numbers
        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(() -> { refreshLineNumbers(); applySyntaxHighlight(); }); }
            public void removeUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(() -> { refreshLineNumbers(); applySyntaxHighlight(); }); }
            public void changedUpdate(DocumentEvent e) { }
        });

        panel.add(tabBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SURFACE);

        // Tab header
        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabBar.setBackground(SURFACE);
        tabBar.setBorder(new MatteBorder(0,0,1,0,BORDER_COL));
        JLabel tab = new JLabel("  Output  ");
        tab.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tab.setForeground(ACCENT);
        tab.setOpaque(true); tab.setBackground(SURFACE);
        tab.setBorder(new MatteBorder(2,0,0,0,ACCENT));
        tab.setPreferredSize(new Dimension(80, 32));
        tab.setHorizontalAlignment(SwingConstants.CENTER);
        tabBar.add(tab);

        outputPane = new JTextPane();
        outputPane.setEditable(false);
        outputPane.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
        outputPane.setBackground(SURFACE);
        outputPane.setForeground(OUTPUT_FG);
        outputPane.setBorder(new EmptyBorder(12,16,12,16));

        JScrollPane scroll = new JScrollPane(outputPane);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(SURFACE);

        panel.add(tabBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        bar.setBackground(new Color(14,14,20));
        bar.setBorder(new MatteBorder(1,0,0,0,BORDER_COL));
        JLabel info = new JLabel("Python 3  |  Ctrl+Enter to run");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        info.setForeground(FG_DIM);
        bar.add(info);
        return bar;
    }

    // ── Run ───────────────────────────────────────────────────────────────────

    private void runCode() {
        String source = editor.getText();
        if (source.trim().isEmpty()) { setOutput("(empty input)", FG_DIM); return; }

        runButton.setEnabled(false);
        setStatus("Running...", ACCENT);

        SwingWorker<CompilerResult, Void> worker = new SwingWorker<>() {
            protected CompilerResult doInBackground() { return Pipeline.run(source); }
            protected void done() {
                try {
                    CompilerResult result = get();
                    if (result.hasErrors()) {
                        showErrors(result.errors);
                    } else {
                        String out = result.output.toString().trim();
                        setOutput(out.isEmpty() ? "(no output)" : out, OUTPUT_FG);
                        setStatus("Done", RUN_GREEN);
                    }
                } catch (Exception ex) {
                    setOutput("Internal error: " + ex.getMessage(), ERR_FG);
                } finally {
                    runButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void showErrors(java.util.List<CompilerError> errors) {
        StyledDocument doc = outputPane.getStyledDocument();
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}

        for (CompilerError err : errors) {
            appendStyled("● ", ERR_FG, Font.BOLD);
            appendStyled(err.stage.name() + " ERROR", ERR_FG, Font.BOLD);
            appendStyled("  [line " + err.line + ", col " + err.column + "]\n", FG_DIM, Font.PLAIN);
            appendStyled("  " + err.message + "\n\n", FG, Font.PLAIN);
        }
        setStatus(errors.size() + " error(s)", ERR_FG);
    }

    private void setOutput(String text, Color color) {
        StyledDocument doc = outputPane.getStyledDocument();
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}
        appendStyled(text, color, Font.PLAIN);
    }

    private void appendStyled(String text, Color color, int style) {
        StyledDocument doc = outputPane.getStyledDocument();
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        StyleConstants.setBold(attrs, style == Font.BOLD);
        try { doc.insertString(doc.getLength(), text, attrs); }
        catch (BadLocationException ignored) {}
    }

    // ── Line numbers ──────────────────────────────────────────────────────────

    private void refreshLineNumbers() {
        String text = editor.getText();
        int lines = text.isEmpty() ? 1 : text.split("\n", -1).length;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            if (i > 1) sb.append("\n");
            sb.append(i);
        }
        String current = lineNumbers.getText();
        String next = sb.toString();
        if (!current.equals(next)) lineNumbers.setText(next);
    }

    // ── Syntax highlighting ───────────────────────────────────────────────────

    private void applySyntaxHighlight() {
        if (highlighting) return;
        highlighting = true;
        StyledDocument doc = editor.getStyledDocument();
        String text;
        int caret = editor.getCaretPosition();
        try { text = doc.getText(0, doc.getLength()); }
        catch (BadLocationException e) { highlighting = false; return; }

        // Reset all to default
        SimpleAttributeSet def = new SimpleAttributeSet();
        StyleConstants.setForeground(def, FG);
        StyleConstants.setBold(def, false);
        doc.setCharacterAttributes(0, text.length(), def, true);

        int i = 0, len = text.length();
        while (i < len) {
            char c = text.charAt(i);

            // Comment
            if (c == '#') {
                int end = text.indexOf('\n', i);
                if (end == -1) end = len;
                color(doc, i, end - i, SH_COMMENT, false);
                i = end; continue;
            }

            // String
            if (c == '"' || c == '\'') {
                int start = i;
                char q = c; i++;
                // Triple quote
                if (i+1<len && text.charAt(i)==q && text.charAt(i+1)==q) {
                    i+=2;
                    while (i+2<len && !(text.charAt(i)==q&&text.charAt(i+1)==q&&text.charAt(i+2)==q)) i++;
                    i = Math.min(i+3, len);
                } else {
                    while (i<len && text.charAt(i)!=q && text.charAt(i)!='\n') {
                        if (text.charAt(i)=='\\') i++;
                        i++;
                    }
                    if (i<len && text.charAt(i)==q) i++;
                }
                color(doc, start, i-start, SH_STRING, false);
                continue;
            }

            // Number
            if (Character.isDigit(c)) {
                int start = i;
                while (i<len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i)=='.')) i++;
                color(doc, start, i-start, SH_NUMBER, false);
                continue;
            }

            // Word (keyword, builtin, function def name, or identifier)
            if (Character.isLetter(c) || c=='_') {
                int start = i;
                while (i<len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i)=='_')) i++;
                String word = text.substring(start, i);
                if (KEYWORDS.contains(word)) {
                    color(doc, start, word.length(), SH_KEYWORD, true);
                } else if (BUILTINS.contains(word)) {
                    color(doc, start, word.length(), SH_BUILTIN, false);
                } else if (i<len && text.charAt(i)=='(') {
                    color(doc, start, word.length(), SH_FUNC, false);
                }
                continue;
            }
            i++;
        }
        // Restore caret
        try { editor.setCaretPosition(Math.min(caret, doc.getLength())); }
        catch (Exception ignored) {}
        highlighting = false;
    }

    private void color(StyledDocument doc, int start, int length, Color c, boolean bold) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, c);
        StyleConstants.setBold(a, bold);
        doc.setCharacterAttributes(start, length, a, false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    private JButton makeButton(String label, Color bg, Color hover, Color fg) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bg); btn.setForeground(fg);
        btn.setBorder(new EmptyBorder(6,18,6,18));
        btn.setFocusPainted(false); btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(hover); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(bg);    }
        });
        return btn;
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings","on");
        System.setProperty("swing.aatext","true");
        SwingUtilities.invokeLater(() -> new CompilerUI().setVisible(true));
    }
}
