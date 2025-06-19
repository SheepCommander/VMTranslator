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
    private int functionLabelI;

    /** Opens the output file (only needs to be done ONCE) and gets ready to write into it. */
    public CodeWriter(String outputFile) throws FileNotFoundException {
        this.output = new PrintStream(outputFile);
        this.simpleLabelI = 0;
        this.functionLabelI = 0;
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
            this.fileName = "sys";
            writeCall("init", 0);
            writeReturn();
            this.fileName = "";
        }
    }


    /** Writes ASM code that effects the label command. */
    public void writeLabel(String label) {
        output.printf("// writeLabel()\n");
        output.printf("(%s$%s)\n", fileName, label);
    }

    /** Writes ASM code that effects the goto command. */
    public void writeGoto(String label) {
        output.printf("""
                // writeGoto()
                @%s$%s
                0;JMP
                """, fileName, label);
    }

    /** Writes ASM code that effects the if-goto command. */
    public void writeIf(String label) {
        output.printf("""
                // writeIf()
                @SP
                AM=M-1
                D=M
                @%s$%s
                D;JNE
                """, fileName, label);
    }

    /** Writes ASM code that effects the call command. */
    public void writeCall(String functionName, int numArgs) {
        output.println("// writeCall() " + functionName + " " + numArgs);

        String returnLabel = functionName + "$ret." + this.functionLabelI;

        // Next we gotta save the return address so we know where to put stuff once the function finishes
        // Since we're pushing the literal address and not the memory inside of the address, 
        // we can't use our push() function that expects to push the memory of whatever we give it, so we have to do it manually
        output.println("@" + returnLabel);
        output.println("D=A"); // D now stores the address (this is the line we had to change from push())
        output.println("@SP");
        output.println("M=M+1");
        output.println("A=M-1");
        output.println("M=D");

        // And we gotta save whatever values the segment pointers point to so that we can put them back once the function finishes
        push("LCL");
        push("ARG");
        push("THIS");
        push("THAT");

        // Since we have 5 things that are now on the stack, we set @ARG to @SP - 5 - numArgs so that the function knows where the arguments start
        output.println("@SP");
        output.println("D=M");
        output.println("@" + (numArgs + 5));
        output.println("D=D-A");
        output.println("@ARG");
        output.println("M=D");

        // And LCL is set to whatever the stack pointer is after all of that pushing happens to mark where we can put all of our local variables (a.k.a. the base address)
        output.println("@SP");
        output.println("D=M");
        output.println("@LCL");
        output.println("M=D");

        // Last but not least, we have to jump to the start of the function using our GOTO function
        output.println("@" + functionName);
        output.println("0;JMP");

        // And make a return label so we know where to come back once the function finishes
        output.println("(" + returnLabel + ")");

        this.functionLabelI++; // end by incrementing!!
    }

    /** Writes ASM code that effects the return command. */
    public void writeReturn() {
        output.println("// writeReturn()");
        // With the way our writeCall() works, our function took up a chunk of the stack to free up some space for the pointers
        // When we return, it's our job to restore everything to how it was pre-call
        // First lets throw LCL (which stores the "base address" of the function's local variables) into temp storage (R13)
        output.println("@LCL");
        output.println("D=M");
        output.println("@R13");
        output.println("M=D");

        // Next let's throw the return address into temp storage (R14), which we can get by doing D (Which holds the base address) - 5 (because we threw 4 memory segments on the stack after the return address)
        output.println("@5");
        output.println("A=D-A");
        output.println("D=M");
        output.println("@R14");
        output.println("M=D");

        // Take the return value from the top of the stack and put it where the caller expects it at ARG, which is at the very start of our local function
        output.println("@SP");
        output.println("AM=M-1");
        output.println("D=M");
        output.println("@ARG");
        output.println("A=M"); // extra line from pop()
        output.println("M=D");

        // Change @SP to the spot @ARG points to  + 1
        output.println("@ARG");
        output.println("D=M+1");
        output.println("@SP");
        output.println("M=D");

        // Since R13 stores the "base address" of the function's local variables, where the stack pointer with all of our segments on it used to be, we can use it to now put all of them back
        // Put THAT back and move R13 down one
        output.println("@R13");
        output.println("AM=M-1");
        output.println("D=M");
        output.println("@THAT");
        output.println("M=D");

        // Put THIS back and move R13 down one
        output.println("@R13");
        output.println("AM=M-1");
        output.println("D=M");
        output.println("@THIS");
        output.println("M=D");

        // Put ARG back and move R13 down one
        output.println("@R13");
        output.println("AM=M-1");
        output.println("D=M");
        output.println("@ARG");
        output.println("M=D");

        // Put LCL back and move R13 down one
        output.println("@R13");
        output.println("AM=M-1");
        output.println("D=M");
        output.println("@LCL");
        output.println("M=D");

        // Jump to the return address (stored in R14 from earlier)
        output.println("@R14");
        output.println("A=M");
        output.println("0;JMP");
    }
    
    /** Writes ASM code that effects the function command. */
    public void writeFunction(String functionName, int numLocals) {
        output.printf("// function %s %d\n", functionName, numLocals);
        output.printf("(%s)\n", functionName);

        // Set all local variables to 0 by pushing 0 numLocals times
        for (int i=0; i<numLocals; i++) {
            pushConstant(0);
        }
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
                case "static" -> push("" + (index + 16));
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
                case "static" -> pop("" + (index+16));
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