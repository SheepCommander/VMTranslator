/**
 * The main program should construct a Parser to parse the VM input file and a CodeWriter
 * to generate code into the corresponding output file. It should then march through the VM commands in the
 * input file and generate assembly code for each one of them.
 * If the program’s argument is a directory name rather than a file name, the main program should process
 * all the .vm files in this directory. In doing so, it should use a separate Parser for handling each input file
 * and a single CodeWriter for handling the output.
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

        if (input.isFile()) {
            files = new File[1];
            files[0] = input;
            outputPath = filePath.substring(0, filePath.lastIndexOf(".")) + ".asm";
        } else {
            files = input.listFiles();
            outputPath = filePath + ".asm";
        }

        CodeWriter codeWriter = new CodeWriter(outputPath);
        
        for (File fileI : files) {
            if ( !fileI.getName().contains(".vm") ) {
                continue;
            }

            Parser parser = new Parser(fileI);
            while (parser.hasMoreCommands()) {
                parser.advance(); // go to first (or next) index
                Parser.CommandType cType = parser.commandType();
                String arg1 = parser.arg1();
                System.out.println(arg1);
                switch (cType) {
                    case vmtranslator.Parser.CommandType.C_ARITHMETIC:
                        codeWriter.writeArithmetic(arg1);
                        break;
                    case Parser.CommandType.C_POP:
                    case Parser.CommandType.C_PUSH:
                        String arg2 = parser.arg2();
                        codeWriter.writePushPop(cType, arg1, Integer.parseInt(arg2));
                        break;
                }
            }
            codeWriter.close();
        }
    }
}