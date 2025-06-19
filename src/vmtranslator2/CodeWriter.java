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
                D=D&M
                M=-1
                @Unique_Label
                D+1;JEQ
                @SP
                A=M-1
                M=0
                (Unique_Label)
                """);
        arithmeticTL.put("or", """
                @SP
                AM=M-1
                D=M
                A=A-1
                D=D|M
                M=-1
                @Unique_Label
                D+1;JEQ
                @SP
                A=M-1
                M=0
                (Unique_Label)
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
        output.println("""
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

    }
    /** Writes ASM code that effects the goto command. */
    public void writeGoto(String label) {

    }
    /** Writes ASM code that effects the if-goto command. */
    public void writeIf(String label) {

    }
    /** Writes ASM code that effects the call command. */
    public void writeCall(String functionName, int numArgs) {

    }
    /** Writes ASM code that effects the return command. */
    public void writeReturn() {

    }
    /** Writes ASM code that effects the function command. */
    public void writeFunction(String functionName, int numLocals) {

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
                case "constant" ->
                    output.printf("""
                        @%d
                        D=A
                        @SP
                        M=M+1
                        A=M-1
                        M=D
                        """, index);
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