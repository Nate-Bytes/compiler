#!/bin/bash
# Compiles and runs the Python Compiler
# Run from the compiler/ directory: bash compile.sh

echo "Compiling..."
javac -d out \
  core/CompilerError.java \
  core/CompilerResult.java \
  lexer/Token.java \
  lexer/Dictionary.java \
  lexer/Lexer.java \
  parser/Parser.java \
  parser/SyntaxRules.java \
  parser/nodes/Node.java \
  semantic/SemanticAnalyzer.java \
  ir/IRInstruction.java \
  ir/IRGenerator.java \
  interpreter/Interpreter.java \
  core/Pipeline.java \
  ui/CompilerUI.java 2>&1

if [ $? -eq 0 ]; then
  echo "Success! Copying dict files..."
  mkdir -p out/dict
  cp dict/*.txt out/dict/
  echo "Running..."
  java -cp out ui.CompilerUI
else
  echo "Compilation failed."
fi
