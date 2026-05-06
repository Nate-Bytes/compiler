# 🐍 Python Compiler

A fully hand-written Python compiler and interpreter built in Java — no external libraries. Features a Programmiz-style GUI with a live code editor on the left and output panel on the right.

---

## Features

- **Full 5-stage compiler pipeline** — Lexer → Parser → Semantic Analyzer → IR Generator → Interpreter
- **Live syntax highlighting** — keywords, strings, numbers, comments, and function names colored in real time
- **Line numbers** — always in sync with the editor
- **Structured error messages** — every error shows the stage it came from, line number, and column
- **No hardcoded rules** — keywords, operators, separators, and grammar rules are all loaded from `.txt` files at runtime
- **Dark themed UI** — editor left, output right, green Run button

---

## Requirements

- Java 11 or higher
- No external libraries or dependencies — pure Java

---

## Project Structure

```
compiler/
├── compile.sh                   ← build and run script
├── dict/
│   ├── keywords.txt             ← Python keywords and built-ins
│   ├── operators.txt            ← Python operators (longest match first)
│   ├── separators.txt           ← Python separators
│   └── syntax_rules.txt        ← statement-level grammar rules
├── core/
│   ├── CompilerError.java       ← structured error: stage + line + col + message
│   ├── CompilerResult.java      ← data carrier passed between stages
│   └── Pipeline.java            ← orchestrates all 5 stages in order
├── lexer/
│   ├── Token.java               ← one token: value, type, line, column
│   ├── Dictionary.java          ← loads keyword/operator/separator lists from txt files
│   └── Lexer.java               ← scans source text into a token list
├── parser/
│   ├── SyntaxRules.java         ← loads grammar rules from syntax_rules.txt
│   ├── Parser.java              ← recursive-descent parser, builds AST
│   └── nodes/                   ← 21 AST node classes (IfNode, ForNode, etc.)
├── semantic/
│   └── SemanticAnalyzer.java    ← scope tracking, undefined names, type checks
├── ir/
│   ├── IRInstruction.java       ← one 3-address IR instruction
│   └── IRGenerator.java         ← walks AST and emits flat IR instruction list
├── interpreter/
│   └── Interpreter.java         ← executes IR instructions, produces output
└── ui/
    └── CompilerUI.java          ← Swing GUI: editor + output panel
```

---

## How to Run

**1. Extract the zip**

```bash
unzip compiler.zip
cd compiler
```

**2. Create the output directory**

```bash
mkdir out
```

**3. Compile and launch**

```bash
bash compile.sh
```

That's it. The script compiles all Java files into `out/`, copies the `dict/` folder into `out/dict/`, and launches the GUI.

---

## How to Use the GUI

| Action | How |
|---|---|
| Write code | Type in the left editor panel |
| Run code | Click **▶ Run** or press **Ctrl+Enter** |
| Clear output | Click **Clear** |
| See errors | Errors appear in the output panel with stage, line, and column |

---

## What Python Syntax Is Supported

### Variables and expressions
```python
x = 10
y = 3.14
name = "Alice"
result = x * 2 + y
```

### Conditionals
```python
if x > 5:
    print("big")
elif x == 5:
    print("five")
else:
    print("small")
```

### Loops
```python
for i in range(5):
    print(i)

while x > 0:
    x -= 1
```

### Functions
```python
def add(a, b):
    return a + b

print(add(3, 4))
```

### Built-in functions
```python
print(len("hello"))
print(range(10))
print(abs(-5))
print(max(1, 2, 3))
print(str(42))
print(int("10"))
```

---

## The 5 Compiler Stages

### Stage 1 — Lexer
Scans the source text character by character and produces a flat list of tokens. Each token carries its value, type, line number, and column. Handles strings (including `f""` and `r""`), numbers (int, float, hex, binary, octal, complex), comments, and all Python operators.

### Stage 2 — Parser
Reads the token list and builds an Abstract Syntax Tree (AST). Uses `syntax_rules.txt` to validate statement structure. If the structure doesn't match any rule, a parser error is reported and execution stops.

### Stage 3 — Semantic Analyzer
Walks the AST and checks for logical problems: undefined variable names, type mismatches (e.g. adding a string to an int), `return` used outside a function, and other rule violations that are syntactically valid but semantically wrong.

### Stage 4 — IR Generator
Walks the AST and emits a flat list of 3-address instructions. Every complex expression is broken into single-operation steps using temporary variables:

```
# x = a + 5 * 2  becomes:
LOAD_CONST   t0  5
LOAD_CONST   t1  2
BINOP        t2  t0 * t1
BINOP        t3  a + t2
ASSIGN       x   t3
```

### Stage 5 — Interpreter
Reads the IR instruction list top to bottom and executes it. Manages a call stack for function calls, tracks variables in scoped frames, and writes `print()` output to the output panel.

---

## Runtime Errors Caught

| Error | Example |
|---|---|
| `NameError` | Using a variable before assigning it |
| `TypeError` | Adding a string and a number with `+` |
| `ZeroDivisionError` | `x / 0` or `x % 0` |
| `IndexError` | Accessing a list index out of range |
| Infinite loop protection | Stops after 100,000 steps |

---

## Customizing the Dictionary Files

All rules and token lists are plain text — edit them without touching any Java code.

### `dict/keywords.txt`
Add or remove Python keywords and built-in names. One entry per line. Lines starting with `#` are comments.

### `dict/operators.txt`
**Order matters.** Multi-character operators (`**`, `==`, `+=`) must appear before single-character ones (`*`, `=`, `+`) so the longest-match scan works correctly.

### `dict/syntax_rules.txt`
Defines valid statement shapes. Format: `rule_name : TOKEN TOKEN ...`

```
# Example rules
assignment  : ID = EXPR
if_stmt     : if EXPR :
def_stmt    : def ID ( PARAMS ) :
```

Add a new rule here and the parser will validate against it automatically.

---

## Known Limitations

- Single-line statements only (no multi-line expressions split across lines)
- No list literals `[1, 2, 3]` or dict literals `{}`
- No classes (syntax accepted but not executed)
- No exception handling (`try/except` parsed but not executed)
- No standard library beyond the built-ins listed above
- `input()` is parsed but returns an empty string at runtime

---

## Built With

- **Java Swing** — GUI
- **Pure Java** — zero external libraries
- **Hand-written recursive descent parser** — no parser generators
- **Custom 3-address IR** — designed for simplicity and readability
