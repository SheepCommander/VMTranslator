/**
 * The main program should construct a Parser to parse the VM input file and a CodeWriter
 * to generate code into the corresponding output file. It should then march through the VM commands in the
 * input file and generate assembly code for each one of them.
 * If the program’s argument is a directory name rather than a file name, the main program should process
 * all the .vm files in this directory. In doing so, it should use a separate Parser for handling each input file
 * and a single CodeWriter for handling the output.
 * 
 * Jun O
 */
package vmtranslator;

import java.io.File;
import java.io.FileNotFoundException;

public class VMTranslator {
    public static void main(String[] args) throws FileNotFoundException {
        if (args.length == 0) {
            System.out.println("Error: Filepath required.\n");
            return;
        }

        String filePath = args[0];
        String outputPath;
        File input = new File(filePath);
        File[] files;

        // Treat all inputs like folders
        if (input.isFile()) {
            files = new File[1];
            files[0] = input;
            outputPath = filePath.substring(0, filePath.lastIndexOf(".")) + ".asm";
        } else {
            files = input.listFiles();
            outputPath = filePath + ".asm";
        }

        // Process files
        CodeWriter codeWriter = new CodeWriter(outputPath);
        codeWriter.writeInit();

        for (File fileI : files) {
            if ( !fileI.getName().contains(".vm") ) {
                continue;
            }

            codeWriter.setFileName(fileI.getName());
            Parser parser = new Parser(fileI);
            while (parser.hasMoreCommands()) {
                parser.advance(); // go to first (or next) index
                Parser.CommandType cType = parser.commandType();
                String arg1 = (cType != Parser.CommandType.C_RETURN) ? parser.arg1() : "";
                System.out.println(arg1);
                switch (cType) {
                    case C_ARITHMETIC:
                        codeWriter.writeArithmetic(arg1);
                        break;
                    case C_POP: case C_PUSH:
                        String arg2 = parser.arg2();
                        codeWriter.writePushPop(cType, arg1, Integer.parseInt(arg2));
                        break;
                    case C_CALL:
                        String n = parser.arg2();
                        codeWriter.writeCall(arg1, Integer.parseInt(n));
                        break;
                    case C_FUNCTION:
                        String nLocals = parser.arg2();
                        codeWriter.writeFunction(arg1, Integer.parseInt(nLocals));
                        break;
                    case C_RETURN:
                        codeWriter.writeReturn();
                        break;
                    case C_GOTO:
                        codeWriter.writeGoto(arg1);
                        break;
                    case C_IF:
                        codeWriter.writeGoto(arg1);
                        break;
                    case C_LABEL:
                        codeWriter.writeLabel(arg1);
                        break;
               }
            }
        }
        codeWriter.close();
    }
}