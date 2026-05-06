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
  parser/nodes/Node.java \
  parser/nodes/ProgramNode.java \
  parser/nodes/AssignNode.java \
  parser/nodes/BinOpNode.java \
  parser/nodes/UnaryOpNode.java \
  parser/nodes/NumberNode.java \
  parser/nodes/StringNode.java \
  parser/nodes/BoolNode.java \
  parser/nodes/NoneNode.java \
  parser/nodes/NameNode.java \
  parser/nodes/PrintNode.java \
  parser/nodes/IfNode.java \
  parser/nodes/WhileNode.java \
  parser/nodes/ForNode.java \
  parser/nodes/FuncDefNode.java \
  parser/nodes/CallNode.java \
  parser/nodes/ReturnNode.java \
  parser/nodes/ImportNode.java \
  parser/nodes/PassNode.java \
  parser/nodes/BreakNode.java \
  parser/nodes/ContinueNode.java \
  parser/SyntaxRules.java \
  parser/Parser.java \
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
