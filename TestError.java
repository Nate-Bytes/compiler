import core.*;

public class TestError {
    public static void main(String[] args) {
        String code = "@ greet(name):\n" +
                      "    message = \"Hello, \" + name + \"!\"\n" +
                      "    return message\n" +
                      "\n" +
                      "print(greet(\"World\"))";

        System.out.println("Testing error handling for '@'...");
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