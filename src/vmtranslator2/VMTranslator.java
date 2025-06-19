/**
 * Jun
 */
package vmtranslator2;

import java.io.File;
import java.io.FileNotFoundException;

public class VMTranslator {
    public static void main(String[] args) throws FileNotFoundException {
        if (args.length == 0) {
            System.out.println("Error: Filepath required.\n");
            return;
        }

        // Arguments
        String filePath = args[0];
        boolean sysInit = true;
        boolean comments = true;
        for (String arg : args)
            switch (arg) {
                case "-noSysInit" -> sysInit = false;
                case "-nS" -> sysInit = false;
                case "-noComments" -> comments = false;
                case "-nC" -> comments = false;
            }

        // Prepare Input & Output files
        File input = new File(filePath);
        File[] inputFiles;
        String outputPath;
        
        // Convert input path into an array of files
        if (input.isFile()) {
            inputFiles = new File[1];
            inputFiles[0] = input;
            outputPath = filePath.substring(0, filePath.lastIndexOf(".")) + ".asm";
        } else {
            inputFiles = input.listFiles();
            outputPath = filePath + ".asm";
        }
        
        // Process the input files!
        CodeWriter codeWriter = new CodeWriter(outputPath); // ONLY MAKE ONE, ELI
        codeWriter.writeInit(sysInit); // Write bootstrap code, optionally calling sysInit based on -noSysInit flag.

        for (File fileI : inputFiles) {
            // Only process VM files
            if (!fileI.getName().contains(".vm")) {
                continue;
            }
            // Prepare file
            codeWriter.setFileName(fileI.getName());
            Parser parser = new Parser(fileI);
            // Process
            while (parser.hasMoreCommands()) {
                parser.advance(); // Go to first/next index. (Initially -1)

                Parser.CommandType cType = parser.commandType();
                switch (cType) {
                    case C_ARITHMETIC ->
                        codeWriter.writeArithmetic(parser.arg1());
                    case C_POP ->
                        codeWriter.writePushPop(cType, parser.arg1(), Integer.parseInt(parser.arg2()));
                    case C_PUSH ->
                        codeWriter.writePushPop(cType, parser.arg1(), Integer.parseInt(parser.arg2()));
                    case C_LABEL ->
                        codeWriter.writeLabel(parser.arg1()); 
                    case C_GOTO ->
                        codeWriter.writeGoto(parser.arg1());
                    case C_IF ->
                        codeWriter.writeIf(parser.arg1());
                    case C_CALL ->
                        codeWriter.writeCall(parser.arg1(), Integer.parseInt(parser.arg2()));
                    case C_RETURN ->
                        codeWriter.writeReturn();
                    case C_FUNCTION ->
                        codeWriter.writeFunction(parser.arg1(), Integer.parseInt(parser.arg2()));
                }
            }
        }
        // Finish processing and close the ouptut stream!
        codeWriter.close();
    }
}