/**
 * Parser: Handles the parsing of a single .vm file, and encapsulates access to the input code. It reads VM
 * commands, parses them, and provides convenient access to their components. In addition, it removes all
 * white space and comments
 */
package vmtranslator;

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

    /**
     * Handles the parsing of a single .vm file.
     * 
     * @param file
     * @throws FileNotFoundException
     */
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

    /**
     * Are there more commands in the input?
     * 
     * @return
     */
    public boolean hasMoreCommands() {
        return this.currentIndex < this.commands.size() - 1;
    }

    /**
     * Reads the next command from the input and makes it the current command.
     * pre: Should only be called if {@link #hasMoreCommands()} is true.
     * Initially there is no current command.
     */
    public void advance() {
        this.currentIndex++;
        this.currentCommand = this.commands.get(currentIndex);
        this.currentCommand = currentCommand.substring(0,
                (currentCommand.contains("//") ? currentCommand.indexOf("//") : currentCommand.length()));
        this.currentCommand.trim();
    }

    /**
     * Returns the type of the current VM command.
     * 
     * @return C_Arithmetic for all arithmetic commands.
     */
    public CommandType commandType() {
        String command = this.currentCommand;
        int end = (command.contains(" ")) ? command.indexOf(" ") : command.length();
        String firstWord = command.substring(0, end); // the first word of the command is the command type
        switch (firstWord) {
            case "push":
                return CommandType.C_PUSH;
            case "pop":
                return CommandType.C_POP;
            case "label":
                return CommandType.C_LABEL;
            case "goto":
                return CommandType.C_GOTO;
            case "if-goto":
                return CommandType.C_IF;
            case "function":
                return CommandType.C_FUNCTION;
            case "call":
                return CommandType.C_CALL;
            case "return":
                return CommandType.C_RETURN;
            default:
                return CommandType.C_ARITHMETIC;
        }
    }

    /**
     * Should not be called if the current command is {@code C_RETURN}.
     * 
     * @return Returns the first argument of the current command. In the case of
     *         {@code C_ARITHMETIC},
     *         the command itself (sub, add, etc.) is returned.
     */
    public String arg1() {
        String command = this.currentCommand;
        if (command.equals("return"))
            throw new RuntimeException("Parser.arg1() should not be called for C_RETURN!");
        if (command.indexOf(" ") == -1)
            return command; // Return the Arithmetic command itself.
        if (command.indexOf(" ") != command.lastIndexOf(" ")) // Command has two arguments
            return command.substring(command.indexOf(" ") + 1, command.lastIndexOf(" "));

        // Command has one argument
        return command.substring(command.indexOf(" ") + 1, command.length());
    }

    /**
     * Should be called only if the current command is
     * {@code C_PUSH, C_POP, C_FUNCTION,} or {@code C_CALL}.
     * 
     * @return Returns the second argument of the current command.
     */
    public String arg2() {
        String command = this.currentCommand;
        if (command.lastIndexOf(" ") == command.indexOf(" "))
            throw new RuntimeException("Parser.arg2() should not be called for commands w/ less than two arguments.");

        return command.substring(command.lastIndexOf(" ") + 1, command.length());
    }
}
