SP 0
LCL 1
ARG 2
THIS 3
THAT 4
TEMP 5-12
R13-R15 free to use

argument    Stores func's arguments.            Allocated dynamically by VM when function is entered
local       Stores func's local vars.           Allocated dynamically by VM & initialized to 0's when func is entered
static      Stores static vars shared by all    Allocated by VM for each .vm file; shared by all funcs in .vm file
            funcs in same .vm file.
constant    Pseudo-segment holds 0..32767.      Emulated by VM, seen by all funcs in program
this        General-purpose segments.           Any VM func can use these segments to manipulate selected areas on heap.
that        Can correspond Heap areas.  
pointer     A two-entry segment that            Any VM func can set pointer 0 (or 1) to some address;
            holds base addresses of this        this has the effect of setting the this (or that) segment
            and that segment.                   to the heap area beginning in that address.
temp        Fixed eight-entry segment           May be used in any VM func for any purpose.
            that holds temporary variables      Shared by all functions.
            for general use.

SP, LCL, ARG, THIS, THAT
- SP: points to stack top
- Rest: point to base addresses of virtual segments local, argument, this, and that.

Xxx.j symbols
- Each static variable j in the file Xxx.vm is translated into assembly Xxx.j;
- In the subsequent assembly process, these symbolic variables will be allocated RAM space by the HACK assembler.

---
add
    @SP
    AM=M-1 // Go to top of stack AND decrement stack pointer
    D=M // Store #1 in D
    A=A-1 // Go down 1
    M=D+M // Add #2 + #1

sub
    @SP
    AM=M-1
    D=M
    A=A-1
    M=M-D

neg
    @SP
    A=M-1
    M=-M

eq
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

gt
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

lt
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

and
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

or
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

not
    @SP
    A=M-1
    M=!M
---
push {segment} {index}  // push the value of segment[index] onto stack
    @%d
    D=A
    @%s
    A=D+M
    D=M
    @SP
    M=M+1
    A=M-1
    M=D

push constant {index}
    @%d
    D=A
    @SP
    M=M+1
    A=M-1
    M=D

push temp {index} // 0-7 + 5  = 5-12
    @R%d
    D=M
    @SP
    M=M+1
    A=M-1
    M=D

push pointer {index} // THIS | THAT
    @%s
    D=M
    @SP
    M=M+1
    A=M-1
    M=D

push static {index} // index + 16
    @R%d
    D=M
    @SP
    M=M+1
    A=M-1
    M=D

pop {segment} {index}   // Pop the top stack value & store it in segment[index]
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

pop pointer {0-1}
    @SP
    AM=M-1
    D=M
    @THIS
    M=D

pop temp {index} // 0-7 + 5  = 5-12
    @SP
    AM=M-1
    D=M
    @R%d
    M=D

pop static {index} // index + 16
    @SP
    AM=M-1
    D=M
    @R%d
    M=D


label {symbol} //// BRANCHING COMMANDS
    (function.label)

goto {symbol}
    @function.label
    0;JMP

if-goto {symbol}
    @SP
    AM=M-1
    D=M
    @function.label
    D;JLT


Function functionName nLocals //// FUNCTION COMMANDS
    (functionName)
    loop push 0 nLocals times

Call functionName numOfArgs  (call avg 3)
    push return_address
    push LCL
    push ARG
    push THIS
    push THAT
    _ _ SET ARG = SP - nArgs - 5
    _ _ SET LCL = SP
    @functionName
    0;JMP
    (return_address)

return
    // return address: LCL - 5
    set SP to ARG+1
    set THAT
    set THIS
    set LCL
    set ARG
    Goto return address
        @return_address
        0;JMP



avg(x, y, z)
    push x
    push y
    push z // SP = 503
    call avg 3 ->
    ->  push return_address
        push LCL
        push ARG
        push THIS
        push THAT // SP = 508
        _ _ SET ARG = SP - nArgs - 5        // SP - 3 - 5 = 500
        _ _ SET LCL = SP
        @avg
        0;JMP
        (return_address)# VMTranslator
