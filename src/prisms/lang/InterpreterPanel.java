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

	private EvaluationEnvironment theValidationEnv;

	private EvaluationEnvironment theRuntimeEnv;

	private javax.swing.JTextArea theInput;

	private javax.swing.JPanel theRow;

	private java.awt.event.KeyListener theReturnListener;

	java.util.ArrayList<Object> toWrite;

	/** Creates an intepreter panel */
	public InterpreterPanel()
	{
		setBackground(java.awt.Color.white);

		theReturnListener = new java.awt.event.KeyAdapter()
		{
			@Override
			public void keyReleased(java.awt.event.KeyEvent e)
			{
				if(e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER)
					checkInput();
			}
		};

		theInput = new javax.swing.JTextArea();
		theInput.setForeground(java.awt.Color.blue);
		theInput.addKeyListener(theReturnListener);
		newLine();

		theParser = new prisms.lang.PrismsParser();
		theParser.configure(prisms.arch.PrismsConfig.fromXml(null, getGrammar()));
		theValidationEnv = new DefaultEvaluationEnvironment();
		theRuntimeEnv = new DefaultEvaluationEnvironment();
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
			theValidationEnv.declareVariable(name, new Type(type), true, null, 0);
			theRuntimeEnv.declareVariable(name, new Type(type), true, null, 0);
			theRuntimeEnv.setVariable(name, value, null, 0);
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
			e.printStackTrace();
			theRow.remove(theInput);
			String input = theInput.getText().trim();
			if(input.indexOf('\n') < 0)
			{
				javax.swing.JLabel replace = new javax.swing.JLabel();
				replace.setForeground(java.awt.Color.blue);
				replace.setText(theInput.getText().trim());
				theRow.add(replace);
			}
			else
			{
				String [] inputs = input.split("\n");
				javax.swing.JPanel replace = new javax.swing.JPanel();
				replace.setLayout(new javax.swing.BoxLayout(replace, javax.swing.BoxLayout.Y_AXIS));
				for(String in : inputs)
				{
					javax.swing.JLabel rep = new javax.swing.JLabel();
					rep.setForeground(java.awt.Color.blue);
					rep.setText(in);
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
			javax.swing.JLabel replace = new javax.swing.JLabel();
			replace.setForeground(java.awt.Color.blue);
			replace.setText(theInput.getText().trim());
			theRow.add(replace);
		}
		else
		{
			String [] inputs = input.split("\n");
			javax.swing.JPanel replace = new javax.swing.JPanel();
			replace.setLayout(new javax.swing.BoxLayout(replace, javax.swing.BoxLayout.Y_AXIS));
			for(String in : inputs)
			{
				javax.swing.JLabel rep = new javax.swing.JLabel();
				rep.setForeground(java.awt.Color.blue);
				rep.setText(in);
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
					s.evaluate(theValidationEnv, false, false);
					EvaluationResult type = s.evaluate(theRuntimeEnv, false, true);
					for(Object o : toWrite)
						answer(prisms.util.ArrayUtils.toString(o), false);
					toWrite.clear();
					if(type != null && !Void.TYPE.equals(type.getType()))
					{
						answer(prisms.util.ArrayUtils.toString(type.getValue()), false);
						if(!(s instanceof prisms.lang.types.ParsedPreviousAnswer))
						{
							theValidationEnv.addHistory(type.getType(), type.getValue());
							theRuntimeEnv.addHistory(type.getType(), type.getValue());
						}
					}
				} catch(EvaluationException e)
				{
					e.printStackTrace();
					answer(e.getMessage(), true);
				} catch(RuntimeException e)
				{
					e.printStackTrace();
					answer(e.toString(), true);
				}
			}
		} finally
		{
			newLine();
		}
	}

	private void newLine()
	{
		theRow = new javax.swing.JPanel();
		theRow.setLayout(new javax.swing.BoxLayout(theRow, javax.swing.BoxLayout.X_AXIS));
		add(theRow);
		javax.swing.JLabel prompt = new javax.swing.JLabel("  > ");
		prompt.setForeground(darkGreen);
		prompt.setFont(prompt.getFont().deriveFont(java.awt.Font.BOLD));
		theRow.add(prompt);
		theRow.add(theInput);
		theInput.setText("");
		doLayout();
		repaint();
		theInput.grabFocus();
	}

	private void answer(String text, boolean error)
	{
		text = text.trim();
		if(text.length() == 0)
			return;
		javax.swing.JLabel answer = new javax.swing.JLabel();
		answer.setText("        " + text);
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
		frame.setTitle("Interpretation Test");
		frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
		frame.setSize(480, 640);
		InterpreterPanel interp = new InterpreterPanel();
		frame.setContentPane(new javax.swing.JScrollPane(interp));
		java.awt.Dimension dim = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		frame.setLocation((int) (dim.getWidth() - frame.getWidth()) / 2,
			(int) (dim.getHeight() - frame.getHeight()) / 2);
		frame.setVisible(true);
	}
}
