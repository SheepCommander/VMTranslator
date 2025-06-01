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

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public class CodeWriter {
    private PrintStream output;
    private String fileName;
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

    private int simpleLabelI;
    private final String simpleLabel =  "Unique_Label";
    // label
    // goto
    // if-goto
    // function
    // call functionName
    // return

    /**
     * Opens the output file/stream, getting ready to write into it.
     * 
     * @param file Output file/stream
     * @throws FileNotFoundException
     */
    public CodeWriter(String file) throws FileNotFoundException {
        this.output = new PrintStream(file);
        this.fileName = file;
        this.simpleLabelI = 0;

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

    /**
     * Inform the translator that the translation of a new VM file has started.
     * 
     * @param fileName the new file name
     * @throws FileNotFoundException
     */
    public void setFileName(String fileName) throws FileNotFoundException {
        this.fileName = fileName;
        close();
        this.output = new PrintStream(fileName);
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
