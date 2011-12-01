package prisms.lang;

/** A panel that takes user-entered text, interprets it, and prints the results */
public class InterpreterPanel extends javax.swing.JPanel
{
	private static java.awt.Color darkGreen = new java.awt.Color(0, 176, 0);

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
			toWrite.add(o);
		}

		/**
		 * Writes the string representation of a byte to the GUI
		 * 
		 * @param b The byte to write
		 */
		public void write(byte b)
		{
			toWrite.add(Byte.valueOf(b));
		}

		/**
		 * Writes the string representation of a short int to the GUI
		 * 
		 * @param s The short int to write
		 */
		public void write(short s)
		{
			toWrite.add(Short.valueOf(s));
		}

		/**
		 * Writes the string representation of an integer to the GUI
		 * 
		 * @param i The integer to write
		 */
		public void write(int i)
		{
			toWrite.add(Integer.valueOf(i));
		}

		/**
		 * Writes the string representation of a long int to the GUI
		 * 
		 * @param L The long int to write
		 */

		public void write(long L)
		{
			toWrite.add(Long.valueOf(L));
		}

		/**
		 * Writes the string representation of a float to the GUI
		 * 
		 * @param f The float to write
		 */
		public void write(float f)
		{
			toWrite.add(Float.valueOf(f));
		}

		/**
		 * Writes the string representation of a double to the GUI
		 * 
		 * @param d The double to write
		 */
		public void write(double d)
		{
			toWrite.add(Double.valueOf(d));
		}

		/**
		 * Writes the string representation of a boolean to the GUI
		 * 
		 * @param b The boolean to write
		 */
		public void write(boolean b)
		{
			toWrite.add(Boolean.valueOf(b));
		}

		/**
		 * Writes the string representation of a character to the GUI
		 * 
		 * @param c The character to write
		 */
		public void write(char c)
		{
			toWrite.add(Character.valueOf(c));
		}

		/** Clears the screen of all but the current input */
		public void clearScreen()
		{
			InterpreterPanel.this.clearScreen();
		}
	}

	private PrismsParser theParser;

	private EvaluationEnvironment theEnv;

	private javax.swing.JEditorPane theInput;

	private javax.swing.JPanel theRow;

	private java.awt.event.KeyListener theReturnListener;

	private java.awt.event.MouseListener theGrabListener;

	java.util.ArrayList<Object> toWrite;

	/** Creates an intepreter panel */
	public InterpreterPanel()
	{
		setBackground(java.awt.Color.white);
		theGrabListener = new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent ev)
			{
				theInput.grabFocus();
			}
		};
		addMouseListener(theGrabListener);

		theReturnListener = new java.awt.event.KeyAdapter()
		{
			@Override
			public void keyPressed(java.awt.event.KeyEvent evt)
			{
				if(evt.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE && evt.isControlDown())
					showIntelliSense();
			}

			@Override
			public void keyReleased(java.awt.event.KeyEvent evt)
			{
				if(evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER)
					checkInput();
			}
		};

		theInput = new javax.swing.JEditorPane();
		theInput.setForeground(java.awt.Color.blue);
		theInput.addKeyListener(theReturnListener);
		newLine();

		theParser = new prisms.lang.PrismsParser();
		theParser.configure(prisms.arch.PrismsConfig.fromXml(null, getGrammar()));
		theEnv = new DefaultEvaluationEnvironment();
		addVariable("pane", EnvPane.class, new EnvPane());

		toWrite = new java.util.ArrayList<Object>();
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
		setPreferredSize(new java.awt.Dimension(getWidth(), top));
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

	void showIntelliSense()
	{
		System.out.println("Intellisense!");
	}

	void checkInput()
	{
		String text = theInput.getText();

		ParsedItem [] structs;
		try
		{
			ParseMatch [] matches = theParser.parseMatches(text);
			ParseStructRoot root = new ParseStructRoot(text);
			structs = theParser.parseStructures(root, matches);
		} catch(IncompleteInputException e)
		{
			return;
		} catch(ParseException e)
		{
			pareStackTrace(e);
			e.printStackTrace();
			theRow.remove(theInput);
			String input = theInput.getText().trim();
			if(input.indexOf('\n') < 0)
			{
				javax.swing.JEditorPane replace = new javax.swing.JEditorPane();
				replace.setForeground(java.awt.Color.blue);
				replace.setText(theInput.getText().trim());
				replace.setEditable(false);
				theRow.add(replace);
			}
			else
			{
				String [] inputs = input.split("\n");
				javax.swing.JPanel replace = new javax.swing.JPanel();
				replace.setLayout(new javax.swing.BoxLayout(replace, javax.swing.BoxLayout.Y_AXIS));
				for(String in : inputs)
				{
					javax.swing.JEditorPane rep = new javax.swing.JEditorPane();
					rep.setForeground(java.awt.Color.blue);
					rep.setText(in);
					rep.setEditable(false);
					replace.add(rep);
				}
				theRow.add(replace);
			}
			theRow = null;
			answer(e.getMessage(), true);
			newLine();
			return;
		}
		theRow.remove(theInput);
		String input = theInput.getText().trim();
		if(input.indexOf('\n') < 0)
		{
			javax.swing.JEditorPane replace = new javax.swing.JEditorPane();
			replace.addMouseListener(theGrabListener);
			replace.setForeground(java.awt.Color.blue);
			replace.setText(theInput.getText().trim());
			replace.setEditable(false);
			theRow.add(replace);
		}
		else
		{
			String [] inputs = input.split("\n");
			javax.swing.JPanel replace = new javax.swing.JPanel();
			replace.addMouseListener(theGrabListener);
			replace.setLayout(new javax.swing.BoxLayout(replace, javax.swing.BoxLayout.Y_AXIS));
			for(String in : inputs)
			{
				javax.swing.JEditorPane rep = new javax.swing.JEditorPane();
				rep.addMouseListener(theGrabListener);
				rep.setForeground(java.awt.Color.blue);
				rep.setText(in);
				rep.setEditable(false);
				replace.add(rep);
			}
			theRow.add(replace);
		}
		theRow = null;

		try
		{
			for(ParsedItem s : structs)
			{
				try
				{
					s.evaluate(theEnv.transact(), false, false);
					EvaluationResult type = s.evaluate(theEnv, false, true);
					for(Object o : toWrite)
						answer(prisms.util.ArrayUtils.toString(o), false);
					toWrite.clear();
					if(type != null && !Void.TYPE.equals(type.getType()))
					{
						answer(prisms.util.ArrayUtils.toString(type.getValue()), false);
						if(!(s instanceof prisms.lang.types.ParsedPreviousAnswer))
							theEnv.addHistory(type.getType(), type.getValue());
					}
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

	private void newLine()
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
		doLayout();
	}

	private void answer(String text, boolean error)
	{
		text = text.trim();
		if(text.length() == 0)
			return;
		javax.swing.JEditorPane answer = new javax.swing.JEditorPane();
		answer.addMouseListener(theGrabListener);
		answer.setText("        " + text);
		answer.setEditable(false);
		if(error)
			answer.setForeground(java.awt.Color.red);
		add(answer);
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
	 * Tests the Interpreter panel
	 * 
	 * @param args Command line arguments, ignored
	 */
	public static void main(String [] args)
	{
		javax.swing.JFrame frame = new javax.swing.JFrame();
		frame.setTitle("Interpreter");
		frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		frame.setSize(480, 640);
		InterpreterPanel interp = new InterpreterPanel();
		frame.setContentPane(new javax.swing.JScrollPane(interp));
		((javax.swing.JScrollPane) frame.getContentPane())
			.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		java.awt.Dimension dim = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		frame.setLocation((int) (dim.getWidth() - frame.getWidth()) / 2,
			(int) (dim.getHeight() - frame.getHeight()) / 2);
		frame.setVisible(true);
	}
}
