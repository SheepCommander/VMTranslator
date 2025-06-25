/**
 * Jun
 */
package vmtranslator2;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public class CodeWriter {
    // Globals
    private String fileName; // the file name minus ".vm"
    private PrintStream output;
    // Arithmetic translation
    private Map<String, String> arithmeticTL;
    private final String SIMPLE_LABEL = "Unique_Label";
    private int simpleLabelI;
    // Function / Program flow translation
    private String lastFunctionName; // the last function defined by a `function f n` command
    private int returnLabelCounter = 0;

    /** Opens the output file (only needs to be done ONCE) and gets ready to write into it. */
    public CodeWriter(String outputFile) throws FileNotFoundException {
        this.output = new PrintStream(outputFile);
        this.simpleLabelI = 0;
        __initArithmetic();
    }
    private void __initArithmetic() {
        this.arithmeticTL = new HashMap<>();
        arithmeticTL.put("",""); // ignore empty lines
        arithmeticTL.put("add", """
                @SP
                AM=M-1
                D=M
                A=A-1
                M=D+M
                """);
        arithmeticTL.put("sub", """
                @SP
                AM=M-1
                D=M
                A=A-1
                M=M-D
                """);
        arithmeticTL.put("neg", """
                @SP
                A=M-1
                M=-M
                """);
        arithmeticTL.put("eq", """
                @SP
                AM=M-1
                D=M
                A=A-1
                D=M-D
                M=-1
                @Unique_Label
                D;JEQ
                @SP
                A=M-1
                M=0
                (Unique_Label)
                """);
        arithmeticTL.put("gt", """
                @SP
                AM=M-1
                D=M
                A=A-1
                D=M-D
                M=-1
                @Unique_Label
                D;JGT
                @SP
                A=M-1
                M=0
                (Unique_Label)
                """);
        arithmeticTL.put("lt", """
                @SP
                AM=M-1
                D=M
                A=A-1
                D=M-D
                M=-1
                @Unique_Label
                D;JLT
                @SP
                A=M-1
                M=0
                (Unique_Label)
                """);
        arithmeticTL.put("and", """
                @SP
                AM=M-1
                D=M
                A=A-1
                M=D&M
                """);
        arithmeticTL.put("or", """
                @SP
                AM=M-1
                D=M
                A=A-1
                M=D|M
                """);
        arithmeticTL.put("not", """
                @SP
                A=M-1
                M=!M
                """);
    }

    /** Informs the CodeWriter that translation of a new VM file has started. */
    public void setFileName(String fileName) { 
        this.fileName = fileName.replace(".vm", "");
    }

    /** MUST GO FIRST: Sets Stack Pointer to 256 and calls sysInit if {@code sysInit} is true. */
    public void writeInit(boolean sysInit) {
        output.print("""
            @256
            D=A
            @SP
            M=D
            """);
        if (sysInit) { // sys.init: call sys.init 0 arguments
            writeCall("Sys.init", 0);
        }
    }

    /** Writes ASM code that effects the label command. */
    public void writeLabel(String label) {
        output.printf("// writeLabel()\n");
        output.printf("(%s$%s)\n", this.lastFunctionName, label);
    }

    /** Writes ASM code that effects the goto command. */
    public void writeGoto(String label) {
        output.printf("// writeGoto()\n");
        output.printf("""
                @%s$%s
                0;JMP
                """, this.lastFunctionName, label);
    }

    /** Writes ASM code that effects the if-goto command. */
    public void writeIf(String label) {
        output.printf("// writeIf()\n");
        output.printf("""
                @SP
                AM=M-1
                D=M
                @%s$%s
                D;JNE
                """, this.lastFunctionName, label);
    }

    /** Writes ASM code that effects the call command. */
    public void writeCall(String functionName, int numArgs) {
        /*  push return-address     // (see label below)
            push LCL                // save LCL of calling function
            push ARG                // save ARG of calling function
            push THIS               // save THIS
            push THAT               // save THAT
            ARG = SP - n - 5        // Reposition ARG (n = numberOfArgs) (numArgs)
            LCL = SP                // Reposition LCL
            goto f                  // Transfer control
        (return-address)        // eventually return
        */
        output.printf("// writeCall() %s %d\n", functionName, numArgs);
        // Generate a unique return address label.
        String returnLabel = functionName + "$ret." + returnLabelCounter++;

        // Step 1: Push the return address onto the stack.
        output.println("// call " + functionName + " " + numArgs);
        output.println("@" + returnLabel);
        output.println("D=A");
        pushDToStack();

        // Step 2 & 3: Push LCL, ARG, THIS, THAT of the caller.
        push("LCL");
        push("ARG");
        push("THIS");
        push("THAT");

        // Step 4: Reposition ARG for the callee: ARG = SP - numArgs - 5.
        output.println("@SP");
        output.println("D=M");
        output.println("@" + (numArgs + 5));
        output.println("D=D-A");
        output.println("@ARG");
        output.println("M=D");

        // Step 5: Reposition LCL for the callee: LCL = SP.
        output.println("@SP");
        output.println("D=M");
        output.println("@LCL");
        output.println("M=D");

        // Step 6: Transfer control to the function.
        output.println("@" + functionName);
        output.println("0;JMP");

        // Step 7: Declare the return label.
        output.println("(" + returnLabel + ")");
    }
    // Helper method to push the value in D register onto the stack.
    private void pushDToStack() {
        output.println("@SP");
        output.println("M=M+1");
        output.println("A=M-1");
        output.println("M=D");
    }

    /** Writes ASM code that effects the return command. */
    public void writeReturn() {
        /*  FRAME = LCL             // FRAME is a temporary variable 
            RET = *(FRAME - 5)      // Put the return-address in a temp var
            *ARG = pop()            // Reposition the return value for the caller
            SP = ARG+1              // Restore the SP of the caller
            THAT = *(FRAME - 1)     // Restore THAT
            THIS = *(FRAME - 2)     // Restore THIS
            ARG = *(FRAME - 3)      // Restore ARG
            LCL = *(FRAME - 4)      // Restore LCL
            goto RET            // Goto return-address (in the Caller's code)
        */
        output.print("// writeReturn()\n");
        // FRAME = R13 = LCL
        output.print("""
                @LCL
                D=M
                @R13
                M=D
                """);
        // RETURN_ADDRESS = R14 = *(LCL - 5)
        output.print("""
                @5
                A=D-A
                D=M
                @R14
                M=D
                """);
        // *ARG = pop()
        output.print("""
                @SP
                AM=M-1
                D=M
                @ARG
                A=M
                M=D
                """);
        // SP = ARG+1
        output.print("""
                @ARG
                D=M+1
                @SP
                M=D
                """);
        // THAT = *(FRAME - 1)
        output.print("""
                @R13
                AM=M-1
                D=M
                @THAT
                M=D
                """);
        output.print("""
                @R13
                AM=M-1
                D=M
                @THIS
                M=D
                """);
        output.print("""
                @R13
                AM=M-1
                D=M
                @ARG
                M=D
                """);
        output.print("""
                @R13
                AM=M-1
                D=M
                @LCL
                M=D
                """);
        // goto RET
        output.print("""
                @R14
                A=M
                0;JMP
                """);
    }
    
    /** Writes ASM code that effects the function command. */
    public void writeFunction(String functionName, int numLocals) {
        output.printf("// writeFunction() %s %d\n", functionName, numLocals);
        output.printf("(%s)\n", functionName);

        // Set all local variables to 0 by pushing 0 numLocals times
        for (int i=0; i<numLocals; i++) {
            pushConstant(0);
        }

        // For writeLabel, writeIf, and writeGoto
        this.lastFunctionName = functionName;
    }

    /** Writes the ASM translation of the given arithmetic command. */
    public void writeArithmetic(String command) {
        String commandTL = arithmeticTL.get(command);
        if (commandTL.contains("Unique_Label")) {
                commandTL = commandTL.replaceAll("Unique_Label", SIMPLE_LABEL + simpleLabelI);
                simpleLabelI++;
        }
        output.print(commandTL);
    }

    /** Writes the ASM translation of a C_PUSH or C_POP command. */
    public void writePushPop(Parser.CommandType command, String segment, int index) {
        if (command == Parser.CommandType.C_PUSH) {
            switch (segment) {
                case "argument" -> push("ARG", index);
                case "local" -> push("LCL", index);
                case "this" -> push("THIS", index);
                case "that" -> push("THAT", index);
                case "static" -> push(fileName + "." + index);

                case "constant" -> pushConstant(index);
                case "pointer" -> {
                    if (index == 0) push("THIS");
                    if (index == 1) push("THAT");
                }
                case "temp" -> push("R"+ (index + 5));
            }
        }
        if (command == Parser.CommandType.C_POP) {
            switch (segment) {
                case "argument" -> pop("ARG", index);
                case "local" -> pop("LCL", index);
                case "this" -> pop("THIS", index);
                case "that" -> pop("THAT", index);
                case "static" -> pop(fileName + "." + index);
                case "pointer" -> {
                    if (index == 0) pop("THIS");
                    if (index == 1) pop("THAT");
                }
                case "temp" -> pop("R" + (index + 5));
            }
        }
    }
    // Pushes a constant to the stack
    private void pushConstant(int index) {
        output.printf("""
            @%d
            D=A
            @SP
            M=M+1
            A=M-1
            M=D
            """, index);
    }
    // Pushes whatever's inside (address) to the stack **/
    private void push(String address) {
        output.printf("""
                @%s
                D=M
                @SP
                M=M+1
                A=M-1
                M=D
                """, address);
    }
    // Pushes whatever's inside the address (index) spaces above [segment] to the stack **/
    private void push(String segment, int index) {
        output.printf("""
                @%d
                D=A
                @%s
                A=D+M
                D=M
                @SP
                M=M+1
                A=M-1
                M=D
                """, index, segment);
    }
    // Pops whatever's at the top of the stack to the spot (index) spaces above [segment]
    private void pop(String segment, int index) {
        output.printf("""
                @%d
                D=A
                @%s
                D=D+M
                @R13
                M=D
                @SP
                AM=M-1
                D=M
                @R13
                A=M
                M=D
                """, index, segment);
    }
    // Pops whatever's at the top of the stack to the spot: (address)
    private void pop(String address) {
        output.printf("""
                @SP
                AM=M-1
                D=M
                @%s
                M=D
                """, address);
    }

    /** Close the output file. */
    public void close() {
        this.output.close();
    }
}