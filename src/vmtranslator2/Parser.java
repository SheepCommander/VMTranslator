/**
 * Jun
 */
package vmtranslator2;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Parser {
    private List<String> commands;
    private int currentIndex;
    private String currentCommand;
    
    /** Enumerated list of command types. */
    public enum CommandType {
        C_ARITHMETIC,
        C_PUSH,
        C_POP,
        C_LABEL,
        C_GOTO,
        C_IF,
        C_FUNCTION,
        C_CALL,
        C_RETURN,
    }

    /** Opens the input/file stream and gets ready to parse it. */
    public Parser(File file) throws FileNotFoundException {
        Scanner input = new Scanner(file);
        this.commands = new ArrayList<String>();
        this.currentIndex = -1;
        this.currentCommand = "";

        while (input.hasNextLine()) {
            String command = input.nextLine(); // Take only the substring up until the first comment, then trim it.
            command = command.substring(0, (command.contains("//")) ? command.indexOf("//") : command.length()).trim();
            this.commands.add(command);
        }

        input.close();
    }

    /** Are there more commands in the input? */
    public boolean hasMoreCommands() {
        return currentIndex < commands.size() - 1;
    }

    /** Read next command & make it the current command. Only call if {@link #hasMoreCommands} is true. */
    public void advance() {
        this.currentIndex++;
        this.currentCommand = this.commands.get(currentIndex);
        this.currentCommand = currentCommand
                .substring(0,(currentCommand.contains("//") ? currentCommand.indexOf("//") : currentCommand.length()))
                .trim();
    }

    /** Returns the type of the current VM command. */
    public CommandType commandType() {
        String command = this.currentCommand;
        int end = (command.contains(" ")) ? command.indexOf(" ") : command.length();
        String firstWord = command.substring(0, end); // the first word of the command is the command type
        
        return switch (firstWord) {
            case "push" -> CommandType.C_PUSH;
            case "pop" -> CommandType.C_POP;
            case "label" -> CommandType.C_LABEL;
            case "goto" -> CommandType.C_GOTO;
            case "if-goto" -> CommandType.C_IF;
            case "function" -> CommandType.C_FUNCTION;
            case "call" -> CommandType.C_CALL;
            case "return" -> CommandType.C_RETURN;
            default -> CommandType.C_ARITHMETIC;
        };
    }

    /**
     * Should not be called if current command is {@code C_RETURN}.
     * Returns the first argument, or in the case of {@code C_ARITHMETIC}, the command itself is returned.
     */
    public String arg1() {
        String command = this.currentCommand;
        // Check command isn't return.
        if (command.equals("return"))
            throw new RuntimeException("Parser.arg1() should not be called for C_RETURN!");
        // Return the Arithmetic command itself.
        if (command.indexOf(" ") == -1)
            return command;
        // Command has two arguments:
        if (command.indexOf(" ") != command.lastIndexOf(" "))
            return command.substring(command.indexOf(" ") + 1, command.lastIndexOf(" "));
        // Command has one argument:
        return command.substring(command.indexOf(" ") + 1, command.length());
    }

    /** Should only be called if current command is {@code C_PUSH, C_POP, C_FUNCTION,} or {@code C_CALL}. */
    public String arg2() {
        String command = this.currentCommand;
        if (command.lastIndexOf(" ") == command.indexOf(" "))
            throw new RuntimeException("Parser.arg2() should not be called for commands w/ less than two arguments.");

        return command.substring(command.lastIndexOf(" ") + 1, command.length());
    }
}