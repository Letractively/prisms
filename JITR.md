## The JITR framework allows java code to be parsed and evaluated at runtime. ##

The main class of the JITR framework is prisms.lang.InterpreterPanel, though the prisms.lang.Test class is available for command-line execution. This swing component allows the user to enter slightly specialized java code to be parsed and evaluated. The interpreter supports:

  * **All types** Including primitives, arrays, and classes.
  * **Some generics** These haven't been thoroughly tested, but they're somewhat useful.
  * **import** Imports can be classes, packages (import package.name.`*`), static methods or fields by name (import static), or all static methods and fields of a class (import static className.`*`).  This just makes it quicker to refer to types, just like in java.
  * **Variables** Declare a variable just like in a method in java, e.g. int a.
  * **Fields/methods**  The fields and methods of any java class or instance available to the environment can be accessed.
  * **Operations** Addition, subtraction, multiplication, division, modulo, logical/bitwise and/or/xor, comparisons, bitwise shifts
  * **Control statements** if/else, for, enhanced for, while, do/while, try/catch/finally, switch/case. Statement labels are **NOT** supported at this time.
  * **Previous answer**  Useful for if you forget to or would rather not assign the value of an expression to a variable. The expression "%" may be used to refer to the value of the previous successfully evaluated expression.  The expression %index may be used to refer to answers further back.  For example, "%1" refers to the answer before last.  The index must be a literal number, not an expression.
  * **Function definition**  Functions may be defined similarly to a method within a java class. E.g.
    * int plus(int a, int b)
    * {
    * return a+b
    * }
  * **drop**  Non-final variables and functions may be dropped so that the variable name can be used again with a different type.
  * **Anonymous classes** Anonymous classes of the form `new package.name.InterfaceName(){...}` may be declared in the same way as in java, but **ONLY** for interfaces--abstract classes and other classes may not be subclassed

The interpreter does **NOT** support class definition, except anonymous classes as described above.  Unlike java, it is unnecessary to specify a semicolon at the end of statements.

In the interpreter, if a statement entered returns a value, the value is printed as the answer.  Some statements (e.g. declarations) don't have values, so in those cases no answer will be printed.  If a statement is within a block or control, like a function or loop body or a switch case, the statement has to do something.  Similarly to java, where an expression like "3+5" is illegal because it doesn't affect anything in the program, statements in blocks or controls must be assignments, method calls, control statements, or some other effective statement.

In the InterpreterPanel, a variable named "pane" has been set in the environment.  "pane" is an instance of prisms.lang.InterpreterPanel.EnvPane.  This class has a clearScreen() method and several write() methods that can write any primitive or object to the display (since System.out.println won't print to the display).  This allows items to be printed to the display directly within evaluated code, e.g. within a loop.

The InterpreterPanel also supports some content assist functionality, similar to many IDEs. If Control-Space is pressed, the editor will attempt to sense the context of where the cursor is and suggest types, variables, functions, methods, and fields that may be appropriate.

If loops are used from the InterpreterPanel, Control-C functionality may be used while a loop is executing to terminate it prematurely. This may prevent the application needing to be shut down, allowing the preservation of the execution environment (variables, function definitions, etc.) set up by the user.