/**
 * Translates VM commands into Hack assembly code.
 * 
 * The VM translator should accept a single command line parameter, as follows:
 * > VMtranslator source
 * Where source is either a file name of the form Xxx.vm (the extension is mandatory) or a directory name
 * containing one or more .vm files (in which case there is no extension). The result of the translation is
 * always a single assembly language file named Xxx.asm, created in the same directory as the input Xxx.
 * The translated code must conform to the standard VM mapping on the Hack platform.
 */
package vmtranslator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public class CodeWriter {
    private PrintStream output;
    private String fileName;
    private int simpleLabelI;
    private final String simpleLabel =  "Unique_Label";

    private Map<String, String> arithmeticTL;
    private String pushSegmentTL;
    private String pushConstantTL;
    private String popTL;
    private String popPointerTL;
    private String pushPointerTL;
    private String popTempTL;
    private String pushTempTL;
    private String popStaticTL;
    private String pushStaticTL;

    private Map<String, String> labels;

    /**
     * Opens the output file/stream, getting ready to write into it.
     * 
     * @param file Output file/stream
     * @throws FileNotFoundException
     */
    public CodeWriter(String file) throws FileNotFoundException {
        this.output = new PrintStream(file);
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
        this.pushSegmentTL = """
                @%d
                D=A
                @%s
                A=D+M
                D=M
                @SP
                M=M+1
                A=M-1
                M=D
                """;
        this.pushConstantTL = """
                @%d
                D=A
                @SP
                M=M+1
                A=M-1
                M=D
                """;
        this.popTL = """
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
                """;
        this.popPointerTL = """
                @SP
                AM=M-1
                D=M
                @%s
                M=D
                """;
        this.pushPointerTL = """
                @%s
                D=M
                @SP
                M=M+1
                A=M-1
                M=D
                """;
        this.pushTempTL = """
                @R%d
                D=M
                @SP
                M=M+1
                A=M-1
                M=D
                """;
        this.popTempTL = """
                @SP
                AM=M-1
                D=M
                @R%d
                M=D
                """;
        this.pushStaticTL = """
                @%d
                D=M
                @SP
                M=M+1
                A=M-1
                M=D
                """;
        this.popStaticTL = """
                @SP
                AM=M-1
                D=M
                @%d
                M=D        
                """;
    }

    private void w(String str) {
        output.println(str);
    }
    /**
     * Inform the translator that the translation of a new VM file has started.
     */
    public void setFileName(String fileName) {
        this.fileName = fileName.replace(".vm","");
    }

    /**
     * Adds the boot strap code to the file (should be called at beginning & only once)
     */
    public void writeInit() {
        // set stack pointer to 256 (0x0100)
        output.println("""
            @256
            D=A
            @SP
            M=D
            """);
        // sys.init: call sys.init 0 arguments
        this.fileName = "sys"; // Shouldnt be a problem; init MUST go first before reading files.
        writeCall("init", 0);
        writeReturn();
        this.fileName = "";
    }

    private void push(String address) {
        w(address); // Go to the address of the address
        w("D=M"); // D now stores the memory of the address
        w("@SP"); // Go to the stack pointer
        w("M=M+1"); // Move the stack pointer up
        w("A=M-1"); // Go to where the stack pointer previously was (above the top of the stack)
        w("M=D"); // Make the new top of the stack whatever was in D
    }
    public void writeCall(String functionName, int numArgs) {
        // Alright, so basically for our functions, we'll need to be able to use local variables and access certain memory spots, stuff that relies on our segment pointers like LCL and ARG
        // Since function calls can be within function calls, we have to make sure that whenever we're about to use our pointers, we store the original values they hold so that we can restore them when the current function is done
        // So we throw all of them on the stack
        w("// call " + functionName + " " + numArgs);

        // First and foremost lets give our function a unique name
        simpleLabelI++;
        String returnLabel = functionName + "$ret." + simpleLabelI;

        // Next we gotta save the return address so we know where to put stuff once the function finishes
        // Since we're pushing the literal address and not the memory inside of the address, 
        // we can't use our push() function that expects to push the memory of whatever we give it, so we have to do it manually
        w("@" + returnLabel);
        w("D=A"); // D now stores the address (this is the line we had to change from push())
        w("@SP");
        w("M=M+1");
        w("A=M-1");
        w("M=D");

        // And we gotta save whatever values the segment pointers point to so that we can put them back once the function finishes
        push("@LCL");
        push("@ARG");
        push("@THIS");
        push("@THAT");

        // Since we have 5 things that are now on the stack, we set @ARG to @SP - 5 - numArgs so that the function knows where the arguments start
        w("@SP");
        w("D=M");
        w("@" + (numArgs + 5));
        w("D=D-A");
        w("@ARG");
        w("M=D");

        // And LCL is set to whatever the stack pointer is after all of that pushing happens to mark where we can put all of our local variables (a.k.a. the base address)
        w("@SP");
        w("D=M");
        w("@LCL");
        w("M=D");

        // Last but not least, we have to jump to the start of the function using our GOTO function
        w("@" + functionName);
        w("0;JMP");

        // And make a return label so we know where to come back once the function finishes
        w("(" + returnLabel + ")");
    }

    public void writeFunction(String funcName, int numLocals) {
        w("// function " + funcName + " " + numLocals);

        // Write the label so we know where it starts
        w("(" + funcName + ")");

        // Set all local variables to 0 by pushing 0 numLocals times
        for (int i = 0; i < numLocals; i++) {
            w("@0");
            w("D=A");
            w("@SP");
            w("A=M");
            w("M=D");
            w("@SP");
            w("M=M+1");
        }
    }
    public void writeReturn() {
        output.println( // store LCL in R13
            """
            @LCL
            D=M
            @R13
            M=D
            """ + //return addresss: LCL - 5
            """
            @5
            A=D-A
            D=M
            @R14
            M=D
            """+ // Take the return value from the top of the stack and put it where the caller expects it at ARG, which is at the very start of our local function
            """
            @SP
            AM=M-1
            D=M
            @ARG
            A=M
            M=D
            """+ //set SP to ARG+1
            """
            @ARG
            D=M+1
            @SP
            M=D
            """+
            // Since R13 stores the "base address" of the function's local variables, where the stack pointer with all of our segments on it used to be, we can use it to now put all of them back
            // Put THAT back and move R13 down one
            """
            @R13
            AM=M-1
            D=M
            @THAT
            M=D
            """+
            // Put THIS back and move R13 down one
            """
            @R13
            AM=M-1
            D=M
            @THIS
            M=D
            """+
            // Put ARG back and move R13 down one
            """
            @R13
            AM=M-1
            D=M
            @ARG
            M=D
            """+
            // Put LCL back and move R13 down one
            """
            @R13
            AM=M-1
            D=M
            @LCL
            M=D
            """+
            // Jump to the return address (stored in R14 from earlier)
            """
            @R14
            A=M
            0;JMP
            """);
    }

    public void writeGoto(String label) {
        w("@" + fileName + "$" + label);
        w("0;JMP"); 
    }
    public void writeLabel(String label) {
        w("(" + fileName + "$" + label + ")");
    }
    public void writeIf(String label) {
        w("// writeIf " + label);
        w("@SP");
        w("AM=M-1");
        w("D=M");
        w("@" + fileName + "$" + label);
        w("D;JNE");
    }

    /**
     * Writes the assembly translation of the given VM command.
     * 
     * @param command the VM command.
     */
    public void writeArithmetic(String command) {
        String commandTL = arithmeticTL.get(command);
        if (commandTL.contains("Unique_Label")) {
                commandTL = commandTL.replaceAll("Unique_Label", simpleLabel + simpleLabelI);
                simpleLabelI++;
        }
        output.print(commandTL);
    }

    /**
     * Writes the assembly code that is the translation of the given command*
     * 
     * @param command *either C_PUSH or C_POP
     * @param segment valid segment (lowercase)
     * @param index
     */
    public void writePushPop(Parser.CommandType command, String segment, int index) {
        Map<String, String> segmentTL = new HashMap<>();
        segmentTL.put("argument", "ARG");
        segmentTL.put("local", "LCL");
        segmentTL.put("static", "static"); // 16-225 static variables
        segmentTL.put("constant", "constant"); // handled below
        segmentTL.put("this", "THIS");
        segmentTL.put("that", "THAT");
        segmentTL.put("pointer", "pointer"); // should not show up- SEE BELOW
        segmentTL.put("temp", "temp"); // should not show up- address R5-R12

        segment = segmentTL.get(segment);
        switch (command) {
                case Parser.CommandType.C_PUSH:
                        switch (segment) {
                                case "constant":
                                        output.print(String.format(this.pushConstantTL, index));
                                        break;
                                case "pointer":
                                        segment = (index == 0) ? "THIS" : "THAT";
                                        output.print(String.format(this.pushPointerTL, segment));
                                        break;
                                case "temp":
                                        output.print(String.format(this.pushTempTL, index+5));
                                        break;
                                case "static":
                                        output.print(String.format(this.pushStaticTL, index+16));
                                        break;
                                default:
                                        output.print(String.format(this.pushSegmentTL, index, segment));
                        }
                        break;
                case Parser.CommandType.C_POP:
                        switch (segment) {
                                case "pointer":
                                        segment = (index == 0) ? "THIS" : "THAT";
                                        output.print(String.format(this.popPointerTL, segment));
                                        break;
                                case "temp":
                                        output.print(String.format(this.popTempTL, index+5));
                                        break;
                                case "static":
                                        output.print(String.format(this.popStaticTL, index+16));
                                        break;
                                default:
                                        output.print(String.format(this.popTL, index, segment));
                        }
                        break;
                default:
                        System.out.println("ERROR");
                        break;
        }
    }

    /**
     * Close the output file.
     */
    public void close() {
        this.output.close();
    }
}
