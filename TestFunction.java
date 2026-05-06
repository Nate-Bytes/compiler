import core.*;

public class TestFunction {
    public static void main(String[] args) {
        String code = "def greet(name):\n" +
                      "    message = \"Hello, \" + name + \"!\"\n" +
                      "    return message\n" +
                      "\n" +
                      "print(greet(\"World\"))";
        
        System.out.println("Testing function definition...");
        System.out.println("Code:\n" + code);
        System.out.println("\n--- Execution ---");
        
        CompilerResult result = Pipeline.run(code);
        
        if (result.hasErrors()) {
            System.out.println("ERRORS:");
            for (CompilerError err : result.errors) {
                System.out.println("  " + err.message);
            }
        } else {
            System.out.println("Output:\n" + result.output);
        }
    }
}
