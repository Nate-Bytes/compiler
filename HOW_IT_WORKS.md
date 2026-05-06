# How This Python Compiler Works

This repository implements a small Python compiler and runtime in Java. It follows a traditional compiler pipeline with 5 stages:

1. Lexer
2. Parser
3. Semantic Analyzer
4. IR Generator
5. Interpreter

The project is also wrapped in a Swing-based GUI so you can type Python code and run it live.

---

## Project Overview

The compiler lives under the following directories:

- `core/` — shared compiler data and pipeline orchestration
- `lexer/` — tokenization, dictionary loading, and token objects
- `parser/` — recursive-descent parser and AST node definitions
- `semantic/` — semantic analysis and name/type validation
- `ir/` — intermediate representation emission
- `interpreter/` — runtime for executing the generated IR
- `ui/` — Swing GUI editor and output display
- `dict/` — language vocabulary and syntax rules loaded at runtime

The build script `compile.sh` compiles Java sources into `out/`, copies `dict/` into `out/dict/`, and launches `ui.CompilerUI`.

---

## How to Run

From the repository root:

```bash
mkdir -p out
bash compile.sh
```

The UI opens with a sample Python program. Press **Run** or **Ctrl+Enter** to execute.

---

## Stage 1: Lexer

File: `lexer/Lexer.java`

The lexer scans the input source code one character at a time.

Key responsibilities:

- Track line and column positions for error reporting.
- Recognize Python indentation with `INDENT` and `DEDENT` tokens.
- Skip comments beginning with `#`.
- Classify tokens as:
  - keywords
  - identifiers
  - operators
  - separators
  - string literals
  - numeric literals
  - newline markers
- Handle Python-style strings, including prefixes like `f""` and escaped characters.
- Detect malformed tokens and unterminated strings.

It stores token definitions in the `dict/` files and uses `lexer/Dictionary.java` to match operators and keywords.

Output: a flat token list inside a `CompilerResult` object.

---

## Stage 2: Parser

File: `parser/Parser.java`

The parser reads the token stream and constructs an Abstract Syntax Tree (AST).

Key characteristics:

- Uses a recursive-descent strategy.
- Produces `parser.nodes.Node` objects for every statement and expression.
- Supports statements like:
  - `if` / `elif` / `else`
  - `while`
  - `for`
  - `def` function definitions
  - `return`
  - `print`
  - assignments and augmented assignments
- Parses expressions and call syntax.
- Uses `parser/SyntaxRules.java` to validate statement shapes against `dict/syntax_rules.txt`.
- Builds structure nodes such as:
  - `PROGRAM`
  - `BLOCK`
  - `ASSIGN`
  - `PRINT`
  - `IF`
  - `WHILE`
  - `FOR`
  - `FUNC_DEF`
  - `RETURN`
  - `CALL`
  - `NAME`, `NUMBER`, `STRING`, `BOOL`, `NONE`, `BINOP`, `UNARYOP`

Output: the AST root node stored in `CompilerResult.data`.

---

## Stage 3: Semantic Analyzer

File: `semantic/SemanticAnalyzer.java`

The semantic analyzer walks the AST and checks for logical correctness beyond syntax.

Main tasks:

- Maintain nested scopes using a stack of maps.
- Define names when they are assigned or imported.
- Check that names are declared before use.
- Track function definitions and local parameters.
- Validate `return` only appears inside a function.
- Infer basic types for simple expressions (`int`, `float`, `str`, `bool`, `none`, or `any`).
- Report errors such as:
  - undefined names
  - illegal `return`
  - incompatible binary operations like adding a string to a number

This stage does not execute code; it only verifies that the program is semantically consistent.

Output: the same AST if no semantic errors were found.

---

## Stage 4: IR Generator

File: `ir/IRGenerator.java`

This stage converts the AST into a linear intermediate representation (IR).

Goals:

- Flatten nested expressions into simple instructions.
- Create temporaries like `t0`, `t1` for intermediate values.
- Generate labels for control flow constructs.
- Emit operations including:
  - `LOAD_CONST`
  - `ASSIGN`
  - `BINOP`
  - `UNARYOP`
  - `PRINT`
  - `JUMP`
  - `JUMP_IF_FALSE`
  - `LABEL`
  - `FUNC_DEF`
  - `FUNC_END`
  - `RETURN`
  - `CALL`
  - `IMPORT`

Examples:

- `x = 1 + 2` becomes a load constant and a binop.
- `if`/`while` generates labels and conditional jumps.
- A `for` loop becomes an index-based iteration over a list.
- Function definitions emit `FUNC_DEF` and `FUNC_END` markers.

Output: a `List<IRInstruction>` stored in `CompilerResult.data`.

---

## Stage 5: Interpreter

File: `interpreter/Interpreter.java`

The interpreter executes the IR instruction list.

How it works:

- Performs a first pass to index all labels and function definitions.
- Uses a call stack with frames for local variables and return addresses.
- Maintains a global variable map.
- Executes instructions sequentially with an instruction pointer (`ip`).
- Evaluates operations using helper methods:
  - `evalBinOp`
  - `evalUnary`
  - `evalNum`
  - `parseConst`
- Provides built-in functions such as `print`, `int`, `float`, `str`, `bool`, `len`, `range`, `abs`, `max`, `min`, and `round`.
- Supports user-defined function calls by pushing a new frame and jumping into the function body.
- Handles runtime errors like:
  - `ZeroDivisionError`
  - `TypeError`
  - `NameError`
  - `IndexError`
  - infinite loops via a step limit

The interpreter also converts Python-style values into Java objects (`Long`, `Double`, `String`, `Boolean`, `List<Object>`) and formats output with Python-like rules.

Result output is collected in `CompilerResult.output` and shown in the UI.

---

## Pipeline Orchestration

File: `core/Pipeline.java`

The pipeline is simple but strict:

1. `Lexer.tokenize(source)`
2. `Parser.parse(lexResult)`
3. `SemanticAnalyzer.analyze(parseResult)`
4. `IRGenerator.generate(semResult)`
5. `Interpreter.run(irResult)`

If any stage reports errors, the pipeline stops immediately and returns the error list.

Compiler data is stored in a reusable `CompilerResult` object from `core/CompilerResult.java`, while error information is stored in `core/CompilerError.java`.

---

## GUI and User Interaction

File: `ui/CompilerUI.java`

The GUI provides:

- A left-hand code editor with live syntax highlighting
- A line-number gutter
- A right-hand output panel
- Run and Clear buttons
- Keyboard shortcut `Ctrl+Enter`
- Status messages for ready/error states

The UI listens for text changes and re-highlights code continuously. When code is executed, the UI passes the text to `Pipeline.run(...)` and renders either compiler errors or program output.

---

## Data-Driven Language Rules

The compiler does not hardcode all Python syntax. Instead it relies on plain text files in `dict/`:

- `keywords.txt` — all recognized Python keywords and built-ins.
- `operators.txt` — operators by longest-first matching order.
- `separators.txt` — punctuation and delimiter symbols.
- `syntax_rules.txt` — statement-level grammar shapes used by the parser.

This makes the language easy to extend or tweak without changing Java code.

---

## Example Execution Flow

Given this code:

```python
x = 10
print(x * 2)
```

1. Lexer produces tokens: `IDENTIFIER(x)`, `OPERATOR(=)`, `NUMBER(10)`, `NEWLINE`, `KEYWORD(print)`, `(`, `IDENTIFIER(x)`, `OPERATOR(*)`, `NUMBER(2)`, `)`
2. Parser builds an AST with an assignment node and a print call.
3. Semantic analyzer verifies `x` is defined and the `print` call is valid.
4. IR generator emits instructions like:
   - `LOAD_CONST t0 10`
   - `ASSIGN x t0`
   - `LOAD_CONST t1 2`
   - `BINOP t2 x * t1`
   - `CALL t3 print t2`
5. Interpreter executes the IR, writing `20` to the output panel.

---

## What This Compiler Can Do

Supported features include:

- numerical and string literals
- boolean values and `None`
- variable assignment
- arithmetic and comparison operators
- `if` / `elif` / `else`
- `while` and `for` loops
- function definitions and calls
- `return`
- `print()` and other built-ins
- simple `import` handling

---

## Limitations

The implementation is intentionally small and educational. It does not support full Python semantics such as:

- full object model / classes
- list and dict comprehensions
- exceptions beyond simple runtime errors
- modules beyond name registration
- complex Python library behavior
- full generator/coroutine support

Still, it is a complete end-to-end compiler and interpreter pipeline for a subset of Python.

---

## Summary

This compiler shows how a real language toolchain works by splitting execution into separate stages:

- lexical analysis
- syntactic structure building
- semantic validation
- intermediate representation creation
- runtime execution

The GUI wraps the pipeline so the result is a usable live editor and interpreter.
