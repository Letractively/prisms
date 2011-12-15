package prisms.lang;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import prisms.lang.EvaluationEnvironment.Variable;
import prisms.lang.types.*;

/** A panel that takes user-entered text, interprets it, and prints the results */
public class InterpreterPanel extends javax.swing.JPanel
{
	private static java.awt.Color darkGreen = new java.awt.Color(0, 176, 0);

	private static final int FONT_SIZE = 12;

	/**
	 * An class exposed to the interpreter as the "pane" variable that allows the interpreter to interact with the GUI
	 * in certain ways
	 */
	public class EnvPane
	{
		/**
		 * Writes the string representation of an object to the GUI
		 * 
		 * @param o The object to write
		 */
		public void write(Object o)
		{
			answer(prisms.util.ArrayUtils.toString(o), false);
		}

		/**
		 * Writes the string representation of a byte to the GUI
		 * 
		 * @param b The byte to write
		 */
		public void write(byte b)
		{
			answer(String.valueOf(b), false);
		}

		/**
		 * Writes the string representation of a short int to the GUI
		 * 
		 * @param s The short int to write
		 */
		public void write(short s)
		{
			answer(String.valueOf(s), false);
		}

		/**
		 * Writes the string representation of an integer to the GUI
		 * 
		 * @param i The integer to write
		 */
		public void write(int i)
		{
			answer(String.valueOf(i), false);
		}

		/**
		 * Writes the string representation of a long int to the GUI
		 * 
		 * @param L The long int to write
		 */

		public void write(long L)
		{
			answer(String.valueOf(L), false);
		}

		/**
		 * Writes the string representation of a float to the GUI
		 * 
		 * @param f The float to write
		 */
		public void write(float f)
		{
			answer(String.valueOf(f), false);
		}

		/**
		 * Writes the string representation of a double to the GUI
		 * 
		 * @param d The double to write
		 */
		public void write(double d)
		{
			answer(String.valueOf(d), false);
		}

		/**
		 * Writes the string representation of a boolean to the GUI
		 * 
		 * @param b The boolean to write
		 */
		public void write(boolean b)
		{
			answer(String.valueOf(b), false);
		}

		/**
		 * Writes the string representation of a character to the GUI
		 * 
		 * @param c The character to write
		 */
		public void write(char c)
		{
			answer(String.valueOf(c), false);
		}

		/** Clears the screen of all but the current input */
		public void clearScreen()
		{
			InterpreterPanel.this.clearScreen();
		}
	}

	static class NamedItem implements Comparable<NamedItem>
	{
		final String name;

		final Object item;

		NamedItem(String aName, Object anItem)
		{
			name = aName;
			item = anItem;
		}

		public int compareTo(NamedItem o)
		{
			return name.compareToIgnoreCase(o.name);
		}
	}

	private PrismsParser theParser;

	EvaluationEnvironment theEnv;

	prisms.impl.ThreadPoolWorker theWorker;

	javax.swing.JEditorPane theInput;

	private javax.swing.JPanel theRow;

	IntellisenseMenu theIntellisenseMenu;

	private java.awt.event.KeyListener theReturnListener;

	private java.awt.event.KeyListener theCancelListener;

	private javax.swing.JEditorPane theReplaced;

	private java.awt.event.MouseListener theGrabListener;

	int toChop;

	/** Creates an intepreter panel */
	public InterpreterPanel()
	{
		setBackground(java.awt.Color.white);
		theGrabListener = new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent ev)
			{
				if(ev.getComponent() != theInput)
				{
					theInput.grabFocus();
					if(theIntellisenseMenu.isVisible())
						theIntellisenseMenu.clear(true);
				}
			}
		};
		addMouseListener(theGrabListener);

		theReturnListener = new java.awt.event.KeyAdapter()
		{
			@Override
			public void keyPressed(java.awt.event.KeyEvent evt)
			{
				if(evt.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE && evt.isControlDown())
					intellisenseTriggered();
				else if(evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER)
				{
					if(theIntellisenseMenu.isVisible())
						evt.consume();
					else
					{
						setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
						theWorker.run(new Runnable()
						{
							public void run()
							{
								checkInput();
								java.awt.EventQueue.invokeLater(new Runnable()
								{
									public void run()
									{
										setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.DEFAULT_CURSOR));
									}
								});
							}
						}, new prisms.arch.Worker.ErrorListener()
						{
							public void error(Error error)
							{
								error.printStackTrace();
							}

							public void runtime(RuntimeException ex)
							{
								ex.printStackTrace();
							}
						});
					}
				}
			}

			@Override
			public void keyTyped(java.awt.event.KeyEvent evt)
			{
				if(evt.getKeyChar() == ' ' && evt.isControlDown())
				{}
				else if(theIntellisenseMenu.isVisible())
				{
					if(evt.getKeyChar() == ' ')
						theIntellisenseMenu.clear(true);
					else
						java.awt.EventQueue.invokeLater(new Runnable()
						{
							public void run()
							{
								intellisenseTriggered();
							}
						});
				}
			}
		};
		theCancelListener = new java.awt.event.KeyAdapter()
		{
			@Override
			public void keyPressed(java.awt.event.KeyEvent evt)
			{
				if(evt.getKeyCode() == java.awt.event.KeyEvent.VK_C && evt.isControlDown())
					theEnv.cancel();
			}
		};

		theInput = new javax.swing.JEditorPane();
		theInput.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, FONT_SIZE));
		theInput.setForeground(java.awt.Color.blue);
		theInput.addKeyListener(theReturnListener);
		newLine();

		theParser = new prisms.lang.PrismsParser();
		theParser.configure(prisms.arch.PrismsConfig.fromXml(null, getGrammar()));
		theEnv = new DefaultEvaluationEnvironment();
		addVariable("pane", EnvPane.class, new EnvPane());
		theEnv.setHandledExceptionTypes(new Type [] {new Type(Exception.class)});
		theWorker = new prisms.impl.ThreadPoolWorker("Execution Worker", 1);
		theWorker.setPriority(Thread.MIN_PRIORITY);

		theIntellisenseMenu = new IntellisenseMenu();
		theIntellisenseMenu.addListener(new IntellisenseMenu.IntellisenseListener()
		{
			public void itemSelected(Object item, String text)
			{
				String curText = theInput.getText();
				theInput.setText(curText.substring(0, theInput.getCaretPosition() - toChop) + text
					+ curText.substring(theInput.getCaretPosition()));
			}
		});
	}

	/**
	 * Adds a final variable to this interpreter's environment
	 * 
	 * @param name The name of the variable to add
	 * @param type The type of the variable to add
	 * @param value The value of the variable to add
	 */
	public <T> void addVariable(String name, Class<? super T> type, T value)
	{
		try
		{
			theEnv.declareVariable(name, new Type(type), true, null, 0);
			theEnv.setVariable(name, value, null, 0);
		} catch(EvaluationException e1)
		{
			e1.printStackTrace();
		}
	}

	@Override
	public void doLayout()
	{
		super.doLayout();
		int top = 0;
		for(java.awt.Component c : getComponents())
		{
			javax.swing.JComponent jc = (javax.swing.JComponent) c;
			jc.setBounds(0, top, getWidth(), jc.getPreferredSize().height);
			top += jc.getHeight();
		}
		setPreferredSize(new java.awt.Dimension(getWidth(), top + 50));
	}

	org.dom4j.Element getGrammar()
	{
		try
		{
			return new org.dom4j.io.SAXReader().read(PrismsParser.class.getResourceAsStream("Grammar.xml"))
				.getRootElement();
		} catch(org.dom4j.DocumentException e)
		{
			throw new IllegalStateException("Could not get grammar for interpretation", e);
		}
	}

	void intellisenseTriggered()
	{
		theIntellisenseMenu.clear(false);
		String text = theInput.getText();
		ParsedItem [] structs;
		if(text.trim().length() == 0)
			structs = new ParsedItem [0];
		else
			try
			{
				ParseMatch [] matches = theParser.parseMatches(text);
				ParseStructRoot root = new ParseStructRoot(text);
				structs = theParser.parseStructures(root, matches);
			} catch(ParseException e)
			{
				theIntellisenseMenu.show(theInput);
				return;
			}

		int caret = theInput.getCaretPosition();
		ParsedItem target = getTarget(structs, caret);
		ParsedItem parent = target == null ? null : target.getParent();
		String toMatch;
		if(target != null)
		{
			if(target instanceof ParsedIdentifier || target instanceof ParsedKeyword || target instanceof ParsedBoolean
				|| target instanceof ParsedReturn || target instanceof ParsedType)
				toMatch = target.toString();
			else if(target instanceof ParsedChar || target instanceof ParsedNumber
				|| target instanceof ParsedPreviousAnswer || target instanceof ParsedString)
				return;
			else if(target instanceof ParsedMethod)
			{
				ParsedMethod m = (ParsedMethod) target;
				ParseMatch paren = target.getStored("method");
				if(paren == null || caret <= paren.index)
				{
					parent = target;
					toMatch = m.getName().substring(0, caret - target.getStored("name").index);
				}
				else
					toMatch = "";
			}
			else
				toMatch = "";
		}
		else
			toMatch = "";

		toChop = toMatch.length();
		if(target instanceof ParsedType)
		{
			// Type only with context
			String context;
			if(toMatch.indexOf('.') >= 0)
			{
				context = toMatch.substring(0, toMatch.lastIndexOf('.'));
				toMatch = toMatch.substring(toMatch.lastIndexOf('.') + 1);
			}
			else
				context = null;
			System.out.println("Type intellisense for \"" + toMatch + "\" (context " + context + ")");

			java.util.ArrayList<NamedItem> items = new java.util.ArrayList<NamedItem>();
			if(context == null)
			{
				for(Class<?> type : theEnv.getImportTypes())
				{
					String imp = Type.isImported(type, theEnv);
					if(getMatchStrength(imp, toMatch) > 0)
						items.add(new NamedItem(imp, imp));
				}
				for(String pkg : theEnv.getImportPackages())
					for(String cn : theEnv.getClassGetter().getClasses(pkg))
						if(getMatchStrength(cn, toMatch) > 0)
							items.add(new NamedItem(cn, cn));
				for(String cn : theEnv.getClassGetter().getClasses(null))
					if(getMatchStrength(cn, toMatch) > 0)
						items.add(new NamedItem(cn, cn));
				for(String cn : theEnv.getClassGetter().getClasses("java.lang"))
					if(getMatchStrength(cn, toMatch) > 0)
						items.add(new NamedItem(cn, cn));
				for(String pn : theEnv.getClassGetter().getSubPackages(null))
					if(getMatchStrength(pn, toMatch) > 0)
						items.add(new NamedItem(pn, pn));
			}
			else
			{
				for(String cn : theEnv.getClassGetter().getClasses(context))
					if(getMatchStrength(cn, toMatch) > 0)
						items.add(new NamedItem(cn, cn));
				for(String pn : theEnv.getClassGetter().getSubPackages(context))
					if(getMatchStrength(pn, toMatch) > 0)
						items.add(new NamedItem(pn, pn));
			}
			java.util.Collections.sort(items);
			sortByMatch(items, toMatch);

			for(NamedItem item : items)
				if(item.item instanceof String)
					theIntellisenseMenu.addMenuItem("class", item.name, item.name, className(item.name));
			theIntellisenseMenu.show(theInput);
		}
		else if(target instanceof ParsedConstructor || parent instanceof ParsedConstructor
			|| target instanceof ParsedArrayInitializer || parent instanceof ParsedArrayInitializer
			|| target instanceof ParsedImport || parent instanceof ParsedImport)
		{
			// Type only
			System.out.println("Type intellisense for \"" + toMatch + "\"");

			java.util.ArrayList<NamedItem> items = new java.util.ArrayList<NamedItem>();
			for(Class<?> type : theEnv.getImportTypes())
			{
				String imp = Type.isImported(type, theEnv);
				if(getMatchStrength(imp, toMatch) > 0)
					items.add(new NamedItem(imp, imp));
			}
			for(String pkg : theEnv.getImportPackages())
				for(String cn : theEnv.getClassGetter().getClasses(pkg))
					if(getMatchStrength(cn, toMatch) > 0)
						items.add(new NamedItem(cn, cn));
			for(String cn : theEnv.getClassGetter().getClasses(null))
				if(getMatchStrength(cn, toMatch) > 0)
					items.add(new NamedItem(cn, cn));
			for(String cn : theEnv.getClassGetter().getClasses("java.lang"))
				if(getMatchStrength(cn, toMatch) > 0)
					items.add(new NamedItem(cn, cn));
			for(String pn : theEnv.getClassGetter().getSubPackages(null))
				if(getMatchStrength(pn, toMatch) > 0)
					items.add(new NamedItem(pn, pn));
			java.util.Collections.sort(items);
			sortByMatch(items, toMatch);

			for(NamedItem item : items)
				if(item.item instanceof String)
					theIntellisenseMenu.addMenuItem("class", item.name, item.name, className(item.name));
			theIntellisenseMenu.show(theInput);
		}
		else if(target instanceof ParsedDrop || parent instanceof ParsedDrop)
		{
			// Variable only
			System.out.println("Variable intellisense on \"" + toMatch + "\"");

			if(toMatch.indexOf('.') >= 0)
			{
				theIntellisenseMenu.show(theInput);
				return;
			}
			EvaluationEnvironment trans = theEnv.transact();
			for(int i = 0; i < structs.length; i++)
				evalDeclares(structs[i], trans);
			java.util.ArrayList<NamedItem> items = new java.util.ArrayList<NamedItem>();
			for(Variable var : trans.getDeclaredVariables())
				if(getMatchStrength(var.getName(), toMatch) > 0)
					items.add(new NamedItem(var.getName(), var));
			for(ParsedFunctionDeclaration func : trans.getDeclaredFunctions())
				if(getMatchStrength(func.getName(), toMatch) > 0)
					items.add(new NamedItem(func.getName(), func));
			java.util.Collections.sort(items);
			sortByMatch(items, toMatch);

			for(NamedItem item : items)
			{
				if(item.item instanceof Variable)
					theIntellisenseMenu.addMenuItem("variable", item.name + ": " + ((Variable) item.item).getType(),
						item.item, item.name);
				else
					theIntellisenseMenu.addMenuItem("function", ((ParsedFunctionDeclaration) item.item).getShortSig()
						+ ": " + ((ParsedFunctionDeclaration) item.item).getReturnType(), item.item,
						((ParsedFunctionDeclaration) item.item).getShortSig());
			}
			theIntellisenseMenu.show(theInput);
		}
		else if(parent instanceof ParsedMethod && ((ParsedMethod) parent).getContext() != null)
		{
			// Member
			EvaluationEnvironment trans = theEnv.transact();
			for(int i = 0; i < structs.length; i++)
				evalDeclares(structs[i], trans);
			EvaluationResult ctxRes;
			try
			{
				ctxRes = ((ParsedMethod) parent).getContext().evaluate(trans, false, false);
			} catch(EvaluationException e)
			{
				theIntellisenseMenu.show(theInput);
				return;
			}

			String msg = "Member intellisense on \"" + toMatch + "\" for context ";
			if(ctxRes.getPackageName() != null)
			{
				msg += "package ";
				msg += ctxRes.getPackageName();
			}
			else if(ctxRes.isType())
			{
				msg += "type ";
				msg += ctxRes.getType().toString();
			}
			else
				msg += ctxRes.getType().toString();

			System.out.println(msg);

			java.util.ArrayList<NamedItem> items = new java.util.ArrayList<NamedItem>();
			if(ctxRes.getPackageName() != null)
			{
				for(String cn : theEnv.getClassGetter().getClasses(ctxRes.getPackageName()))
					if(getMatchStrength(cn, toMatch) > 0)
						items.add(new NamedItem(cn, cn));
				for(String pn : theEnv.getClassGetter().getSubPackages(ctxRes.getPackageName()))
					if(getMatchStrength(pn, toMatch) > 0)
						items.add(new NamedItem(pn, pn));
			}
			else if(ctxRes.isType())
			{ // Static fields/methods
				Method [] methods = ctxRes.getType().getBaseType().getDeclaredMethods();
				for(Method m : methods)
				{
					if(m.isSynthetic() || (m.getModifiers() & Modifier.STATIC) == 0)
						continue;
					else if(theEnv.usePublicOnly() && (m.getModifiers() & Modifier.PUBLIC) == 0)
						continue;
					else if(getMatchStrength(m.getName(), toMatch) > 0)
						items.add(new NamedItem(m.getName(), m));
				}
				Field [] fields = ctxRes.getType().getBaseType().getDeclaredFields();
				for(Field f : fields)
				{
					if(f.isSynthetic() || (f.getModifiers() & Modifier.STATIC) == 0)
						continue;
					else if(theEnv.usePublicOnly() && (f.getModifiers() & Modifier.PUBLIC) == 0)
						continue;
					else if(getMatchStrength(f.getName(), toMatch) > 0)
						items.add(new NamedItem(f.getName(), f));
				}
				for(String cn : theEnv.getClassGetter().getClasses(ctxRes.getType().toString()))
					if(getMatchStrength(cn, toMatch) > 0)
						items.add(new NamedItem(cn, cn));
			}
			else
			{
				Method [] methods = ctxRes.getType().getBaseType().getDeclaredMethods();
				for(Method m : methods)
				{
					if(m.isSynthetic() || (m.getModifiers() & Modifier.STATIC) != 0)
						continue;
					else if(theEnv.usePublicOnly() && (m.getModifiers() & Modifier.PUBLIC) == 0)
						continue;
					else if(getMatchStrength(m.getName(), toMatch) > 0)
						items.add(new NamedItem(m.getName(), m));
				}
				Field [] fields = ctxRes.getType().getBaseType().getDeclaredFields();
				for(Field f : fields)
				{
					if(f.isSynthetic() || (f.getModifiers() & Modifier.STATIC) != 0)
						continue;
					else if(theEnv.usePublicOnly() && (f.getModifiers() & Modifier.PUBLIC) == 0)
						continue;
					else if(getMatchStrength(f.getName(), toMatch) > 0)
						items.add(new NamedItem(f.getName(), f));
				}
			}
			java.util.Collections.sort(items);
			sortByMatch(items, toMatch);

			for(NamedItem item : items)
			{
				if(item.item instanceof Field)
					theIntellisenseMenu.addMenuItem("field",
						item.name + ": " + new Type(((Field) item.item).getGenericType()), item.item, item.name);
				else if(item.item instanceof Method) // Better method string
					theIntellisenseMenu.addMenuItem("method", "" + name((Method) item.item), item.item, item.name
						+ (((Method) item.item).getParameterTypes().length > 0 ? "(" : "()"));
				else if(item.item instanceof String)
					theIntellisenseMenu.addMenuItem("class", item.name, item.item, className(item.name));
			}
			theIntellisenseMenu.show(theInput);
		}
		else
		{
			// Possibly either
			System.out.println("General intellisense on \"" + toMatch + "\"");

			EvaluationEnvironment trans = theEnv.transact();
			for(int i = 0; i < structs.length; i++)
				evalDeclares(structs[i], trans);
			java.util.ArrayList<NamedItem> items = new java.util.ArrayList<NamedItem>();
			for(Variable var : trans.getDeclaredVariables())
				if(getMatchStrength(var.getName(), toMatch) > 0)
					items.add(new NamedItem(var.getName(), var));
			for(ParsedFunctionDeclaration func : trans.getDeclaredFunctions())
				if(getMatchStrength(func.getName(), toMatch) > 0)
					items.add(new NamedItem(func.getName(), func));
			java.util.Collections.sort(items);
			sortByMatch(items, toMatch);

			for(NamedItem item : items)
			{
				if(item.item instanceof Variable)
					theIntellisenseMenu.addMenuItem("variable", item.name + ": " + ((Variable) item.item).getType(),
						item.item, item.name);
				else
					theIntellisenseMenu.addMenuItem("function", ((ParsedFunctionDeclaration) item.item).getShortSig()
						+ ": " + ((ParsedFunctionDeclaration) item.item).getReturnType(), item.item, item.name
						+ (((ParsedFunctionDeclaration) item.item).getParameters().length > 0 ? "(" : "()"));
			}
			theIntellisenseMenu.show(theInput);
		}
	}

	private ParsedItem getTarget(ParsedItem [] items, int position)
	{
		for(int i = 0; i < items.length; i++)
		{
			if(position <= items[i].getMatch().index)
				return null;
			else if(items[i].getMatch().index + items[i].getMatch().text.length() >= position)
			{
				ParsedItem [] deps = items[i].getDependents();
				if(deps.length == 0)
					return items[i];
				else
				{
					ParsedItem ret = getTarget(deps, position);
					return ret == null ? items[i] : ret;
				}
			}
		}
		return null;
	}

	/**
	 * Causes declarations to be evaluated so that all variables available to the scope at the point of incompleteness
	 * are known
	 */
	private static void evalDeclares(ParsedItem item, EvaluationEnvironment env)
	{
		if(item instanceof ParsedDeclaration)
		{
			if(item.getMatch().isComplete())
				try
				{
					item.evaluate(env, false, false);
				} catch(EvaluationException e)
				{}
		}
		else
			for(ParsedItem dep : item.getDependents())
				evalDeclares(dep, env);
	}

	private String name(java.lang.reflect.Method m)
	{
		StringBuilder ret = new StringBuilder();
		ret.append(m.getName()).append('(');
		for(int i = 0; i < m.getParameterTypes().length; i++)
		{
			if(i > 0)
				ret.append(", ");
			ret.append(new Type(m.getGenericParameterTypes()[i]).toString(theEnv));
		}
		ret.append("): ");
		ret.append(new Type(m.getGenericReturnType()).toString(theEnv));
		return ret.toString();
	}

	private String className(String name)
	{
		int idx = name.lastIndexOf('.');
		if(idx >= 0)
			return name.substring(idx + 1);
		else
			return name;
	}

	private static void sortByMatch(final java.util.List<NamedItem> items, String toMatch)
	{
		Integer [] strengths = new Integer [items.size()];
		for(int i = 0; i < strengths.length; i++)
			strengths[i] = Integer.valueOf(getMatchStrength(items.get(i).name, toMatch));
		prisms.util.ArrayUtils.sort(strengths, new prisms.util.ArrayUtils.SortListener<Integer>()
		{
			public int compare(Integer o1, Integer o2)
			{
				return o1.compareTo(o2);
			}

			public void swapped(Integer o1, int idx1, Integer o2, int idx2)
			{
				NamedItem temp = items.get(idx1);
				items.set(idx1, items.get(idx2));
				items.set(idx2, temp);
			}
		});
	}

	private static int getMatchStrength(String test, String toMatch)
	{
		if(toMatch.length() == 0)
			return 1;
		if(test.equals(toMatch))
			return 1000;
		if(test.equalsIgnoreCase(toMatch))
			return 900;
		int cc = matchCamelCase(test, toMatch);
		if(cc > 0)
			return cc;
		if(test.toLowerCase().startsWith(toMatch.toLowerCase()))
			return 100;
		else
			return 0;
	}

	private static int matchCamelCase(String test, String toMatch)
	{
		int i, j;
		for(i = 0, j = 0; i < toMatch.length() && j < test.length();)
		{
			if(toMatch.charAt(i) == test.charAt(j))
			{
				i++;
				j++;
			}
			else if(i > 0 && toMatch.charAt(i) >= 'A' && toMatch.charAt(i) <= 'Z')
			{
				while(j < test.length() && (test.charAt(j) < 'A' || test.charAt(j) > 'Z'))
					j++;
				if(toMatch.charAt(i) == test.charAt(j))
				{
					i++;
					j++;
				}
				else
					break;
			}
			else
				break;
		}
		if(i < toMatch.length())
			return 0;
		if(j == test.length())
			return 800;
		for(; j < test.length(); j++)
			if(test.charAt(j) >= 'A' && test.charAt(j) <= 'Z')
				return 300;
		return 500;
	}

	void checkInput()
	{
		theEnv.uncancel();
		String text = theInput.getText().trim();

		ParsedItem [] structs;
		try
		{
			ParseMatch [] matches = theParser.parseMatches(text);
			if(matches.length == 0 || !matches[matches.length - 1].isComplete())
				return;
			ParseStructRoot root = new ParseStructRoot(text);
			structs = theParser.parseStructures(root, matches);
		} catch(ParseException e)
		{
			pareStackTrace(e);
			e.printStackTrace();
			replaceInput();
			String msg = e.toString();
			int idx = msg.indexOf(e.getMessage());
			if(idx > 0)
				msg = msg.substring(idx);
			answer(msg, true);
			newLine();
			return;
		}
		replaceInput();

		try
		{
			for(ParsedItem s : structs)
			{
				try
				{
					s.evaluate(theEnv.transact(), false, false);
					EvaluationResult type = s.evaluate(theEnv, false, true);
					if(type != null && !Void.TYPE.equals(type.getType().getBaseType()))
					{
						answer(prisms.util.ArrayUtils.toString(type.getValue()), false);
						if(!(s instanceof prisms.lang.types.ParsedPreviousAnswer))
							theEnv.addHistory(type.getType(), type.getValue());
					}
				} catch(ExecutionException e)
				{
					pareStackTrace(e.getCause());
					e.getCause().printStackTrace();
					answer(e.getCause().toString(), true);
				} catch(EvaluationException e)
				{
					pareStackTrace(e);
					e.printStackTrace();
					answer(e.getMessage(), true);
				} catch(RuntimeException e)
				{
					pareStackTrace(e);
					e.printStackTrace();
					answer(e.toString(), true);
				}
			}
		} finally
		{
			newLine();
		}
	}

	private static void pareStackTrace(Throwable e)
	{
		int i = e.getStackTrace().length - 1;
		while(i >= 0 && !e.getStackTrace()[i].getClassName().startsWith("prisms.lang"))
			i--;
		if(i >= 0)
		{
			StackTraceElement [] newST = new StackTraceElement [i + 1];
			System.arraycopy(e.getStackTrace(), 0, newST, 0, newST.length);
			e.setStackTrace(newST);
		}
	}

	void replaceInput()
	{
		if(java.awt.EventQueue.isDispatchThread())
		{
			if(theReplaced != null)
				theReplaced.removeKeyListener(theCancelListener);
			theInput.setText(theInput.getText().trim());
			theRow.remove(theInput);
			theReplaced = new javax.swing.JEditorPane();
			theReplaced.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, FONT_SIZE));
			theReplaced.addKeyListener(theCancelListener);
			theReplaced.addMouseListener(theGrabListener);
			theReplaced.setForeground(java.awt.Color.blue);
			theReplaced.setText(theInput.getText().trim());
			theReplaced.setEditable(false);
			theRow.add(theReplaced);
			theRow = null;
			theReplaced.grabFocus();
		}
		else
			java.awt.EventQueue.invokeLater(new Runnable()
			{
				public void run()
				{
					replaceInput();
				}
			});
	}

	void newLine()
	{
		if(java.awt.EventQueue.isDispatchThread())
		{
			theRow = new javax.swing.JPanel();
			theRow.addMouseListener(theGrabListener);
			theRow.setLayout(new javax.swing.BoxLayout(theRow, javax.swing.BoxLayout.X_AXIS));
			add(theRow);
			javax.swing.JLabel prompt = new javax.swing.JLabel("  > ");
			prompt.setForeground(darkGreen);
			prompt.setFont(prompt.getFont().deriveFont(java.awt.Font.BOLD));
			prompt.setBackground(java.awt.Color.white);
			theRow.add(prompt);
			theRow.add(theInput);
			theInput.setText("");
			doLayout();
			repaint();
			theInput.grabFocus();
			if(getParent() instanceof javax.swing.JViewport)
			{
				int y = getHeight() - getParent().getHeight();
				if(y > 0)
					((javax.swing.JViewport) getParent()).setViewPosition(new java.awt.Point(0, y));
			}
			java.awt.EventQueue.invokeLater(new Runnable()
			{
				public void run()
				{
					theInput.setText("");
				}
			});
		}
		else
			java.awt.EventQueue.invokeLater(new Runnable()
			{
				public void run()
				{
					newLine();
				}
			});
	}

	void answer(final String text, final boolean error)
	{
		if(java.awt.EventQueue.isDispatchThread())
		{
			String textTrim = text.trim();
			if(textTrim.length() == 0)
				return;
			javax.swing.JEditorPane answer = new javax.swing.JEditorPane();
			answer.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, FONT_SIZE));
			answer.addMouseListener(theGrabListener);
			answer.setText("        " + textTrim);
			answer.setEditable(false);
			if(error)
				answer.setForeground(java.awt.Color.red);
			add(answer);
		}
		else
			java.awt.EventQueue.invokeLater(new Runnable()
			{
				public void run()
				{
					answer(text, error);
				}
			});
	}

	void clearScreen()
	{
		for(java.awt.Component c : getComponents())
		{
			if(c == theRow)
				break;
			remove(c);
		}
	}

	/**
	 * Creates a new JFrame wrapping an InterpreterPanel. The actual InterpreterPanel can be retrieved from this frame
	 * using {@link #getPanelFromFrame(javax.swing.JFrame)}. The frame is named "JITR" and sized 480x640 to the middle
	 * of the window
	 * 
	 * @return The frame created
	 */
	public static javax.swing.JFrame createInterpreterFrame()
	{
		javax.swing.JFrame frame = new javax.swing.JFrame();
		frame.setTitle("JITR");
		frame.setSize(480, 640);
		InterpreterPanel interp = new InterpreterPanel();
		javax.swing.JScrollPane contentPane = new javax.swing.JScrollPane(interp);
		contentPane.getVerticalScrollBar().setUnitIncrement(10);
		frame.setContentPane(contentPane);
		((javax.swing.JScrollPane) frame.getContentPane())
			.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		java.awt.Dimension dim = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		frame.setLocation((int) (dim.getWidth() - frame.getWidth()) / 2,
			(int) (dim.getHeight() - frame.getHeight()) / 2);
		frame.setVisible(true);
		return frame;
	}

	/**
	 * @param frame The frame created with {@link #createInterpreterFrame()}
	 * @return The InterpreterPanel wrapped by the frame
	 */
	public static InterpreterPanel getPanelFromFrame(javax.swing.JFrame frame)
	{
		return (InterpreterPanel) ((javax.swing.JScrollPane) frame.getContentPane()).getViewport().getView();
	}

	/**
	 * Tests the Interpreter panel
	 * 
	 * @param args Command line arguments, ignored
	 */
	public static void main(String [] args)
	{
		prisms.arch.PrismsServer.initLog4j(prisms.arch.PrismsServer.class.getResource("log4j.xml"));
		javax.swing.JFrame frame = createInterpreterFrame();
		frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
	}
}
