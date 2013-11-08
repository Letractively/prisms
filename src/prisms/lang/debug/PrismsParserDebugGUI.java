package prisms.lang.debug;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreePath;

import prisms.arch.MutableConfig;
import prisms.arch.PrismsConfig;
import prisms.lang.ParseException;
import prisms.lang.ParseMatch;
import prisms.lang.PrismsParser;
import prisms.lang.PrismsParserDebugger;

public class PrismsParserDebugGUI extends JPanel implements PrismsParserDebugger {
	enum StepTargetType {
		EXIT, CHILD
	}

	private static Color SELECTED = new Color(100, 100, 255);

	private JTextPane theMainText;
	private ParsingExpressionTreeModel theTreeModel;
	private JTree theParseTree;
	private JButton theOverButton;
	private JButton theIntoButton;
	private JButton theOutButton;
	private JButton theResumeButton;
	private JTable theBreakpointList;
	private JLabel theAddBreakpointLabel;
	private JList<OpObject> theDebugPane;
	private JSplitPane theMainSplit;
	private JSplitPane theRightSplit;
	private File theConfigFile;
	private List<String> theOpNames;
	private List<PrismsParserBreakpoint> theBreakpoints;

	private boolean isPopupWhenHit = true;
	private PrismsParser theParser;
	private volatile boolean isSuspended;
	private CharSequence theText;
	private int theIndex;

	private ParseNode theStepTarget;

	private StepTargetType theStepTargetType;

	public PrismsParserDebugGUI() {
		this(new File("PrismsParserDebug.config"));
	}

	public PrismsParserDebugGUI(File configFile) {
		super(new BorderLayout());

		theConfigFile = configFile;
		theMainText = new JTextPane();
		theMainText.setContentType("text/html");
		theMainText.setEditable(false);
		theMainText.setBackground(java.awt.Color.white);
		theTreeModel = new ParsingExpressionTreeModel();
		theTreeModel.setDisplayed(false);
		theParseTree = new JTree(theTreeModel);
		theParseTree.getSelectionModel().setSelectionMode(javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION);
		theParseTree.getSelectionModel().addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
			@Override
			public void valueChanged(TreeSelectionEvent e) {
				if(e.getPath() == null)
					setDebugOperator(null);
				else
					setDebugOperator((ParseNode) e.getPath().getLastPathComponent());
				setGuiEnabled(isSuspended);
			}
		});
		theOverButton = new JButton(getIcon("arrow180.png", 24, 16));
		theIntoButton = new JButton(getIcon("arrow90down.png", 24, 24));
		theOutButton = new JButton(getIcon("arrow90right.png", 24, 24));
		theResumeButton = new JButton(getIcon("play.png", 24, 24));
		theAddBreakpointLabel = new JLabel(getIcon("bluePlus.png", 16, 16));
		theDebugPane = new JList<>(new DefaultListModel<OpObject>());
		theDebugPane.setCellRenderer(new OpObjectRenderer());
		theBreakpointList = new JTable(0, 4);
		theBreakpointList.getTableHeader().setReorderingAllowed(false);
		theBreakpointList.getColumnModel().getColumn(0).setHeaderValue("");
		theBreakpointList.getColumnModel().getColumn(0).setCellRenderer(new BreakpointEnabledRenderer());
		theBreakpointList.getColumnModel().getColumn(0).setCellEditor(new BreakpointEnabledEditor());
		theBreakpointList.getColumnModel().getColumn(1).setHeaderValue("Text");
		theBreakpointList.getColumnModel().getColumn(1).setCellRenderer(new BreakpointTextRenderer());
		theBreakpointList.getColumnModel().getColumn(1).setCellEditor(new BreakpointTextEditor());
		theBreakpointList.getColumnModel().getColumn(2).setHeaderValue("Operator");
		theBreakpointList.getColumnModel().getColumn(2).setCellRenderer(new BreakpointOpRenderer());
		theBreakpointList.getColumnModel().getColumn(2).setCellEditor(new BreakpointOpEditor());
		theBreakpointList.getColumnModel().getColumn(3).setHeaderValue("");
		theBreakpointList.getColumnModel().getColumn(3).setCellRenderer(new BreakpointDeleteRenderer());
		theBreakpointList.getColumnModel().getColumn(3).setCellEditor(new BreakpointDeleteEditor());
		theMainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		theRightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

		theBreakpoints = new java.util.concurrent.CopyOnWriteArrayList<>();

		JScrollPane mainTextScroll = new JScrollPane(theMainText);
		mainTextScroll.setPreferredSize(new Dimension(100, 200));
		add(mainTextScroll, BorderLayout.NORTH);
		add(theMainSplit);
		JScrollPane treeScroll = new JScrollPane(theParseTree);
		theMainSplit.setLeftComponent(treeScroll);
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		buttonPanel.add(theOverButton);
		buttonPanel.add(theIntoButton);
		buttonPanel.add(theOutButton);
		buttonPanel.add(theResumeButton);
		JPanel rightPanel = new JPanel(new BorderLayout());
		theMainSplit.setRightComponent(rightPanel);
		rightPanel.add(buttonPanel, BorderLayout.NORTH);
		rightPanel.add(theRightSplit);
		JPanel breakpointPanel = new JPanel(new BorderLayout());
		JPanel breakpointTop = new JPanel(new BorderLayout());
		breakpointTop.add(new JLabel("Breakpoints"), BorderLayout.WEST);
		breakpointPanel.add(breakpointTop, BorderLayout.NORTH);
		breakpointTop.add(theAddBreakpointLabel, BorderLayout.EAST);
		JScrollPane breakpointScroll = new JScrollPane(theBreakpointList);
		breakpointPanel.add(breakpointScroll);
		theRightSplit.setBottomComponent(breakpointPanel);
		JScrollPane debugScroll = new JScrollPane(theDebugPane);
		theRightSplit.setTopComponent(debugScroll);

		theOpNames = new ArrayList<>();

		theAddBreakpointLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				addBreakpoint();
			}
		});
		theOverButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				stepOver();
			}
		});
		theIntoButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				stepInto();
			}
		});
		theOutButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				stepOut();
			}
		});
		theResumeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				resume();
			}
		});

		reset();
		readConfig();
	}

	public void setPopupWhenHit(boolean popup) {
		isPopupWhenHit = popup;
	}

	protected MutableConfig getConfig() {
		try {
			return new MutableConfig(null, PrismsConfig.fromXml(null, theConfigFile.toURI().toString()));
		} catch(java.io.IOException e) {
			e.printStackTrace();
			return new MutableConfig("config");
		}
	}

	protected void writeConfig(MutableConfig config) {
		try (java.io.BufferedOutputStream os = new java.io.BufferedOutputStream(new java.io.FileOutputStream(theConfigFile))) {
			MutableConfig.writeAsXml(config, os);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private static ImageIcon getIcon(String location, int w, int h) {
		ImageIcon icon = new ImageIcon(PrismsParserDebugGUI.class.getResource(location));
		Image img = icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
		return new ImageIcon(img);
	}

	private void readConfig() {
		MutableConfig config = getConfig();
		if(config.subConfig("breakpoints") != null) {
			for(MutableConfig breakpointConfig : config.subConfig("breakpoints").subConfigs("breakpoint")) {
				PrismsParserBreakpoint breakpoint = new PrismsParserBreakpoint();
				breakpoint.setPreCursorText(breakpointConfig.get("pre") == null ? null : Pattern.compile(
					".*" + breakpointConfig.get("pre"), Pattern.DOTALL));
				breakpoint.setPostCursorText(breakpointConfig.get("post") == null ? null : Pattern.compile(breakpointConfig.get("post")
					+ ".*", Pattern.DOTALL));
				breakpoint.setOpName(breakpointConfig.get("op"));
				breakpoint.setEnabled(breakpointConfig.is("enabled", true));
				theBreakpoints.add(breakpoint);
				((javax.swing.table.DefaultTableModel) theBreakpointList.getModel()).addRow(new Object[] {breakpoint, breakpoint,
					breakpoint, breakpoint});
			}
		}
	}

	private void writeConfig() {
		MutableConfig config = getConfig();
		MutableConfig breakpoints = config.subConfig("breakpoints");
		if(breakpoints == null) {
			breakpoints = new MutableConfig("breakpoints");
			config.addSubConfig(breakpoints);
		} else {
			config.setSubConfigs(new MutableConfig[0]);
		}
		for(PrismsParserBreakpoint bp : theBreakpoints) {
			breakpoints.addSubConfig(new MutableConfig("breakpoint")
			.set("pre", bp.getPreCursorText() == null ? null : bp.getPreCursorText().pattern().substring(2))
			.set(
				"post",
				bp.getPostCursorText() == null ? null : bp.getPostCursorText().pattern()
					.substring(0, bp.getPostCursorText().pattern().length() - 2)).set("enabled", "" + bp.isEnabled()));
		}
		writeConfig(config);
	}

	private void breakpointChanged(PrismsParserBreakpoint breakpoint) {
		writeConfig();
	}

	private void breakpointDeleted(PrismsParserBreakpoint breakpoint) {
		theBreakpoints.remove(breakpoint);
		javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel) theBreakpointList.getModel();
		for(int i = 0; i < model.getRowCount(); i++)
			if(model.getValueAt(i, 0) == breakpoint) {
				model.removeRow(i);
				break;
			}
		writeConfig();
	}

	private void addBreakpoint() {
		PrismsParserBreakpoint breakpoint = new PrismsParserBreakpoint();
		theBreakpoints.add(breakpoint);
		((javax.swing.table.DefaultTableModel) theBreakpointList.getModel()).addRow(new Object[] {breakpoint, breakpoint, breakpoint,
			breakpoint});
		writeConfig();
	}

	@Override
	public void init(PrismsParser parser) {
		theParser = parser;
		theOpNames.clear();
		for(PrismsConfig op : parser.getOperators())
			theOpNames.add(op.get("name"));
		reset();
	}

	@Override
	public void start(CharSequence text) {
	}

	@Override
	public void end(CharSequence text, ParseMatch [] matches) {
	}

	@Override
	public void fail(CharSequence text, ParseMatch [] matches, ParseException e) {
	}

	@Override
	public void preParse(CharSequence text, int index, final PrismsConfig op) {
		boolean isOnStepTarget = false;
		if(theStepTarget != null && theStepTargetType == StepTargetType.CHILD && theStepTarget == theTreeModel.getCursor()) {
			for(PrismsConfig sub : theTreeModel.getCursor().theOp.subConfigs())
				if(sub == op) {
					isOnStepTarget = true;
					break;
				}
		}
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				theTreeModel.startNew(op);
			}
		});
		update(text, index, op, null);

		if(isOnStepTarget)
			suspend(null);
		else if(theStepTarget == null) {
			CharSequence pre = text.subSequence(0, index);
			CharSequence post = text.subSequence(index, text.length());
			for(PrismsParserBreakpoint breakpoint : theBreakpoints) {
				if(!breakpoint.isEnabled())
					continue;
				if(breakpoint.getPreCursorText() == null && breakpoint.getPostCursorText() == null && breakpoint.getOpName() == null)
					continue;
				if(breakpoint.getPreCursorText() != null && !breakpoint.getPreCursorText().matcher(pre).matches())
					continue;
				if(breakpoint.getPostCursorText() != null && !breakpoint.getPostCursorText().matcher(post).matches())
					continue;
				if(breakpoint.getOpName() != null && !breakpoint.getOpName().equals(op.get("name")))
					continue;
				suspend(breakpoint);
				break;
			}
		}
	}

	@Override
	public void postParse(CharSequence text, int startIndex, PrismsConfig op, final ParseMatch match) {
		boolean isOnStepTarget = false;
		if(theStepTarget != null && theStepTarget == theTreeModel.getCursor())
			isOnStepTarget = true;
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				theTreeModel.finish(match);
			}
		});
		update(text, startIndex, op, match);

		if(isOnStepTarget)
			suspend(null);
	}

	@Override
	public void matchDiscarded(final ParseMatch match) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				theTreeModel.matchDiscarded(match);
			}
		});
	}

	@Override
	public void usedCache(final ParseMatch match) {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				theTreeModel.add(match);
			}
		});
	}

	private void reset() {
		theText = "";
		theIndex = 0;
	}

	private void update(CharSequence text, int index, PrismsConfig op, ParseMatch match) {
		theText = text;
		theIndex = index;
	}

	private boolean firstRender = true;

	private void render() {
		if(firstRender) {
			theMainSplit.setDividerLocation(.3);
			theRightSplit.setDividerLocation(.5);
			firstRender = false;
		}
		if(isPopupWhenHit) {
			Component parent = getParent();
			while(parent != null && !(parent instanceof java.awt.Window))
				parent = parent.getParent();
			if(parent != null)
				((java.awt.Window) parent).setVisible(true);
		}

		theTreeModel.setDisplayed(true);
		StringBuilder sb = new StringBuilder("<html>");
		for(int c = 0; c < theIndex; c++) {
			char ch = theText.charAt(c);
			if(ch == '<')
				sb.append("&lt;");
			else if(ch == '\n')
				sb.append("<br>");
			else if(ch == '\r') {
			} else if(ch == '\t')
				sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
			else
				sb.append(ch);
		}
		sb.append("<b><font color=\"red\" size=\"4\">|</font></b>");
		for(int c = theIndex; c < theText.length(); c++) {
			char ch = theText.charAt(c);
			if(ch == '<')
				sb.append("&lt;");
			else if(ch == '\n')
				sb.append("<br>");
			else if(ch == '\r') {
			} else if(ch == '\t')
				sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
			else
				sb.append(ch);
		}
		sb.append("</html>");
		theMainText.setText(sb.toString());
	}

	private void setDebugOperator(ParseNode op) {
		DefaultListModel<OpObject> model = (DefaultListModel<OpObject>) theDebugPane.getModel();
		model.removeAllElements();
		while(op != null && !op.theOp.getName().equals("operator")) {
			op = op.theParent;
		}
		if(op != null) {
			addToModel(model, op.theOp, 0);
		}
	}

	private void addToModel(DefaultListModel<OpObject> model, PrismsConfig op, int indent) {
		model.addElement(new OpObject(op, indent, false));
		boolean needsEnd = false;
		for(PrismsConfig sub : op.subConfigs()) {
			if(!PrismsParser.NON_OP_CONFIGS.contains(sub.getName())) {
				needsEnd = true;
				addToModel(model, sub, indent + 1);
			}
		}
		if(needsEnd)
			model.addElement(new OpObject(op, indent, true));
	}

	private void suspend(PrismsParserBreakpoint breakpoint) {
		theStepTarget = null;
		isSuspended = true;
		setGuiEnabled(true);
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				render();
			}
		});
		while(isSuspended) {
			try {
				Thread.sleep(50);
			} catch(InterruptedException e) {
			}
		}
	}

	private void stepOver() {
		ParseNode target = theParseTree.getSelectionPath() == null ? theTreeModel.getCursor() : (ParseNode) theParseTree.getSelectionPath()
			.getLastPathComponent();
		if(target == null)
			throw new IllegalStateException("No selection or cursor!");
		theStepTarget = target.theParent;
		theStepTargetType = StepTargetType.CHILD;
		setGuiEnabled(false);
		isSuspended = false;
	}

	private void stepInto() {
		ParseNode target = theParseTree.getSelectionPath() == null ? theTreeModel.getCursor() : (ParseNode) theParseTree.getSelectionPath()
			.getLastPathComponent();
		if(target == null)
			throw new IllegalStateException("No selection or cursor!");
		theStepTarget = target;
		theStepTargetType = StepTargetType.CHILD;
		setGuiEnabled(false);
		isSuspended = false;
	}

	private void stepOut() {
		ParseNode target = theParseTree.getSelectionPath() == null ? theTreeModel.getCursor() : (ParseNode) theParseTree.getSelectionPath()
			.getLastPathComponent();
		if(target == null)
			throw new IllegalStateException("No selection or cursor!");
		theStepTarget = target;
		theStepTargetType = StepTargetType.EXIT;
		setGuiEnabled(false);
		isSuspended = false;
	}

	private void resume() {
		theStepTarget = null;
		setGuiEnabled(false);
		isSuspended = false;
	}

	private void setGuiEnabled(boolean enabled) {
		if(enabled) {
			ParseNode cursor = theTreeModel.getCursor();
			theOverButton.setEnabled(cursor != null && cursor.theParent != null);
			theIntoButton.setEnabled(cursor != null && theParseTree.getSelectionPath() != null
				&& theParseTree.getSelectionPath().getLastPathComponent() == theTreeModel.getCursor() && isDescendable(cursor.theOp));
			theOutButton.setEnabled(cursor != null && cursor.theParent != null);
		} else {
			theOverButton.setEnabled(false);
			theIntoButton.setEnabled(false);
			theOutButton.setEnabled(false);
			theResumeButton.setEnabled(false);
		}
	}

	private boolean isDescendable(PrismsConfig op) {
		if(op.getName().equals("op"))
			return true;
		for(PrismsConfig child : op.subConfigs())
			if(!PrismsParser.NON_OP_CONFIGS.contains(child.getName()))
				return true;
		return false;
	}

	public static JFrame getDebuggerFrame(final PrismsParserDebugGUI debugger) {
		JFrame ret = new JFrame("Prisms Parser Debug");
		ret.setContentPane(debugger);
		ret.pack();
		ret.setLocationRelativeTo(null);
		ret.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if(debugger.isSuspended)
					debugger.resume();
			}
		});
		return ret;
	}

	public static void main(String [] args) {
		try {
			javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
		} catch(Exception e) {
			System.err.println("Could not install system L&F");
			e.printStackTrace();
		}
		PrismsParserDebugGUI debugger;
		if(args.length > 0)
			debugger = new PrismsParserDebugGUI(new File(args[0]));
		else
			debugger = new PrismsParserDebugGUI();
		JFrame frame = getDebuggerFrame(debugger);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		frame.setVisible(true);
	}

	private class ParsingExpressionTreeModel implements javax.swing.tree.TreeModel {
		private ParseNode theRoot;

		private ParseNode theCursor;

		private List<TreeModelListener> theListeners = new ArrayList<>();

		private boolean isDisplayed;

		private boolean isDirty;

		ParseNode getCursor() {
			return theCursor;
		}

		void setDisplayed(boolean displayed) {
			isDisplayed = displayed;
			if(isDirty)
				refresh();
		}

		void startNew(PrismsConfig op) {
			ParseNode newNode = new ParseNode(theCursor, op);
			boolean newRoot = theCursor == null;
			if(newRoot)
				theRoot = newNode;
			else
				theCursor.theChildren.add(newNode);
			theCursor = newNode;

			if(isDisplayed) {
				if(newRoot) {
					TreeModelEvent evt = new TreeModelEvent(this, new Object[] {theRoot});
					for(TreeModelListener listener : theListeners)
						listener.treeStructureChanged(evt);
				} else {
					TreeModelEvent evt = new TreeModelEvent(this, new Object[] {theCursor.theParent}, new int[] {getIndexOfChild(
						theCursor.theParent, theCursor)}, new Object[] {theCursor});
					for(TreeModelListener listener : theListeners)
						listener.treeNodesInserted(evt);
				}
				theParseTree.setSelectionPath(new TreePath(getPath(theCursor)));
				if(theCursor.theParent != null)
					theParseTree.expandPath(new TreePath(getPath(theCursor.theParent)));
			} else
				isDirty = true;
		}

		void finish(ParseMatch match) {
			if(theCursor == null)
				return;
			theCursor.theMatch = match;
			ParseNode changed = theCursor;
			theCursor = theCursor.theParent;
			int childIdx = theCursor == null ? 0 : theCursor.theChildren.indexOf(changed);
			if(match == null && theCursor != null) {
				theCursor.theChildren.remove(childIdx);
			}

			if(isDisplayed && theCursor != null) {
				if(match != null) {
					TreeModelEvent evt = new TreeModelEvent(this, new Object[] {theCursor}, new int[] {childIdx}, new Object[] {changed});
					for(TreeModelListener listener : theListeners)
						listener.treeNodesChanged(evt);
				} else {
					TreeModelEvent evt = new TreeModelEvent(this, new Object[] {theCursor}, new int[] {childIdx}, new Object[] {changed});
					for(TreeModelListener listener : theListeners)
						listener.treeNodesRemoved(evt);
				}
				if(theCursor != null) {
					theParseTree.setSelectionPath(new TreePath(getPath(theCursor)));
					if(theCursor.theParent != null)
						theParseTree.expandPath(new TreePath(getPath(theCursor.theParent)));
				}
			} else
				isDirty = true;
		}

		void matchDiscarded(ParseMatch match) {
			if(theCursor == null)
				return;
			ParseNode removed = null;
			for(ParseNode child : theCursor.theChildren)
				if(child.theMatch == match) {
					removed = child;
					break;
				}
			if(removed == null)
				return;
			int childIdx = theCursor == null ? 0 : theCursor.theChildren.indexOf(removed);
			theCursor.theChildren.remove(childIdx);
			if(isDisplayed) {
				TreeModelEvent evt = new TreeModelEvent(this, new Object[] {theCursor}, new int[] {childIdx}, new Object[] {removed});
				for(TreeModelListener listener : theListeners)
					listener.treeNodesRemoved(evt);
			} else
				isDirty = true;
		}

		void add(ParseMatch match) {
			ParseNode newNode = new ParseNode(theCursor, match);
			boolean newRoot = theCursor == null;
			if(newRoot)
				theRoot = newNode;
			else
				theCursor.theChildren.add(newNode);

			if(isDisplayed) {
				if(newRoot) {
					TreeModelEvent evt = new TreeModelEvent(this, new Object[] {theRoot});
					for(TreeModelListener listener : theListeners)
						listener.treeStructureChanged(evt);
				} else {
					TreeModelEvent evt = new TreeModelEvent(this, new Object[] {theCursor},
						new int[] {getIndexOfChild(theCursor, newNode)}, new Object[] {newNode});
					for(TreeModelListener listener : theListeners)
						listener.treeNodesInserted(evt);
				}
				if(theCursor != null && theCursor.theParent != null)
					theParseTree.expandPath(new TreePath(getPath(theCursor.theParent)));
			} else
				isDirty = true;
		}

		void refresh() {
			TreeModelEvent evt = new TreeModelEvent(this, new Object[] {getRoot()});
			for(TreeModelListener listener : theListeners)
				listener.treeStructureChanged(evt);
			isDirty = false;
			if(theCursor != null) {
				theParseTree.setSelectionPath(new TreePath(getPath(theCursor)));
				// TODO Trying to expand the tree to the cursor. Not working yet.
				if(theCursor.theParent != null)
					theParseTree.expandPath(new TreePath(getPath(theCursor.theParent)));
			}
		}

		Object [] getPath(ParseNode node) {
			ArrayList<Object> ret = new ArrayList<>();
			do {
				ret.add(0, node);
				node = node.theParent;
			} while(node != null);
			return ret.toArray();
		}

		@Override
		public Object getRoot() {
			if(theRoot == null)
				return new ParseNode(null, new MutableConfig("(empty)"));
			return theRoot;
		}

		@Override
		public Object getChild(Object parent, int index) {
			return ((ParseNode) parent).theChildren.get(index);
		}

		@Override
		public int getChildCount(Object parent) {
			return ((ParseNode) parent).theChildren.size();
		}

		@Override
		public boolean isLeaf(Object node) {
			return getChildCount(node) == 0;
		}

		@Override
		public int getIndexOfChild(Object parent, Object child) {
			return ((ParseNode) parent).theChildren.indexOf(child);
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue) {
		}

		@Override
		public void addTreeModelListener(TreeModelListener l) {
			theListeners.add(l);
		}

		@Override
		public void removeTreeModelListener(TreeModelListener l) {
			theListeners.remove(l);
		}
	}

	private static class ParseNode {
		final ParseNode theParent;

		PrismsConfig theOp;

		ParseMatch theMatch;

		final List<ParseNode> theChildren = new ArrayList<>();

		private final String theString;

		ParseNode(ParseNode parent, PrismsConfig op) {
			theParent = parent;
			theOp = op;
			StringBuilder str = new StringBuilder();
			str.append("<").append(op.getName());
			for(PrismsConfig sub : op.subConfigs()) {
				if(sub.getValue() != null && sub.subConfigs().length == 0)
					str.append(' ').append(sub.getName()).append("=\"").append(sub.getValue()).append('"');
			}
			str.append(">");
			theString = str.toString();
		}

		ParseNode(ParseNode parent, ParseMatch match) {
			this(parent, match.config);
			theMatch = match;
		}

		@Override
		public String toString() {
			if(theMatch == null)
				return theString;
			else
				return theString + " - \"" + theMatch.text + "\"";
		}
	}

	private static class OpObject {
		final PrismsConfig theOp;

		final int theIndent;

		final boolean isTerminal;

		OpObject(PrismsConfig op, int indent, boolean terminal) {
			theOp = op;
			theIndent = indent;
			isTerminal = terminal;
		}
	}

	private static class BreakpointEnabledRenderer extends JCheckBox implements TableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			if(value instanceof PrismsParserBreakpoint)
				setSelected(((PrismsParserBreakpoint) value).isEnabled());
			else if(value instanceof Boolean)
				setSelected((Boolean) value);
			return this;
		}
	}

	private class BreakpointEnabledEditor extends javax.swing.DefaultCellEditor {
		private PrismsParserBreakpoint theEditingBreakpoint;

		BreakpointEnabledEditor() {
			super(new JCheckBox());
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			theEditingBreakpoint = (PrismsParserBreakpoint) value;
			return super.getTableCellEditorComponent(table, theEditingBreakpoint.isEnabled(), isSelected, row, column);
		}

		@Override
		public boolean stopCellEditing() {
			boolean ret = super.stopCellEditing();
			theEditingBreakpoint.setEnabled(((JCheckBox) getComponent()).isEnabled());
			breakpointChanged(theEditingBreakpoint);
			return ret;
		}

		@Override
		public Object getCellEditorValue() {
			return theEditingBreakpoint;
		}
	}

	private static class BreakpointTextRenderer extends JLabel implements TableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			PrismsParserBreakpoint breakpoint = (PrismsParserBreakpoint) value;
			String pre = breakpoint.getPreCursorText() == null ? null : breakpoint.getPreCursorText().pattern();
			String post = breakpoint.getPostCursorText() == null ? null : breakpoint.getPostCursorText().pattern();
			if(pre == null && post == null) {
				setText("");
			} else {
				String blue = "<font color=\"blue\" size=\"3\">";
				StringBuilder text = new StringBuilder("<html>");
				if(pre != null)
					text.append(blue).append("(</font>").append(pre.substring(2).replaceAll("<", "&lt;")).append(blue).append(")</font>");
				text.append("<font color=\"red\" size=\"3\">.</font>");
				if(post != null)
					text.append(blue).append("(</font>").append(post.substring(0, post.length() - 2).replaceAll("<", "&lt;")).append(blue)
					.append(")</font>");
				text.append("</html>");
				setText(text.toString());
			}
			return this;
		}
	}

	private class BreakpointTextEditor extends JPanel implements TableCellEditor {
		private JTextField thePreField;

		private JTextField thePostField;

		private PrismsParserBreakpoint theEditingBreakpoint;

		private ArrayList<CellEditorListener> theListeners;

		BreakpointTextEditor() {
			setLayout(null);
			thePreField = new JTextField();
			thePostField = new JTextField();
			add(thePreField);
			add(thePostField);
			theListeners = new ArrayList<>();
			thePreField.addKeyListener(new java.awt.event.KeyAdapter() {
				@Override
				public void keyTyped(KeyEvent e) {
					super.keyTyped(e);
					EventQueue.invokeLater(new Runnable() {
						@Override
						public void run() {
							checkPattern(thePreField);
						}
					});
				}
			});
		}

		private void checkPattern(JTextField field) {
			try {
				Pattern.compile(field.getText());
				field.setBackground(java.awt.Color.white);
				field.setToolTipText(null);
			} catch(PatternSyntaxException e) {
				field.setBackground(java.awt.Color.red);
				field.setToolTipText(e.getMessage());
			}
		}

		@Override
		public void doLayout() {
			thePreField.setBounds(0, 0, getWidth() / 2, getHeight());
			thePostField.setBounds(getWidth() / 2 + 1, 0, getWidth() / 2, getHeight());
		}

		@Override
		public Dimension getPreferredSize() {
			Dimension ret = new Dimension(thePreField.getPreferredSize());
			ret.width *= 2;
			return ret;
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			theEditingBreakpoint = (PrismsParserBreakpoint) value;
			// Lop off the .*
			String text;
			if(theEditingBreakpoint.getPreCursorText() == null)
				text = "";
			else
				text = theEditingBreakpoint.getPreCursorText().pattern().substring(2);
			thePreField.setText(text);
			checkPattern(thePreField);
			if(theEditingBreakpoint.getPostCursorText() == null)
				text = "";
			else {
				text = theEditingBreakpoint.getPostCursorText().pattern();
				text = text.substring(0, text.length() - 2);
			}
			thePostField.setText(text);
			checkPattern(thePostField);
			return this;
		}

		@Override
		public Object getCellEditorValue() {
			return theEditingBreakpoint;
		}

		@Override
		public boolean isCellEditable(EventObject anEvent) {
			return true;
		}

		@Override
		public boolean shouldSelectCell(EventObject anEvent) {
			return true;
		}

		@Override
		public boolean stopCellEditing() {
			Pattern prePattern;
			Pattern postPattern;
			try {
				prePattern = thePreField.getText().length() == 0 ? null : Pattern.compile(".*" + thePreField.getText(), Pattern.DOTALL);
				postPattern = thePostField.getText().length() == 0 ? null : Pattern.compile(thePostField.getText() + ".*", Pattern.DOTALL);
			} catch(PatternSyntaxException e) {
				return false;
			}
			theEditingBreakpoint.setPreCursorText(prePattern);
			theEditingBreakpoint.setPostCursorText(postPattern);
			breakpointChanged(theEditingBreakpoint);

			ChangeEvent evt = new ChangeEvent(this);
			for(CellEditorListener listener : theListeners.toArray(new CellEditorListener[0]))
				listener.editingStopped(evt);
			return true;
		}

		@Override
		public void cancelCellEditing() {
			ChangeEvent evt = new ChangeEvent(this);
			for(CellEditorListener listener : theListeners.toArray(new CellEditorListener[0]))
				listener.editingCanceled(evt);
		}

		@Override
		public void addCellEditorListener(CellEditorListener l) {
			theListeners.add(l);
		}

		@Override
		public void removeCellEditorListener(CellEditorListener l) {
			theListeners.remove(l);
		}
	}

	private static class BreakpointOpRenderer extends javax.swing.table.DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			PrismsParserBreakpoint breakpoint = (PrismsParserBreakpoint) value;
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if(breakpoint.getOpName() == null)
				setText("");
			else
				setText("<" + breakpoint.getOpName() + ">");
			return this;
		}
	}

	private class BreakpointOpEditor extends javax.swing.DefaultCellEditor {
		private final String NONE = "(any)";

		private PrismsParserBreakpoint theEditingBreakpoint;

		BreakpointOpEditor() {
			super(new JComboBox<String>());
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			theEditingBreakpoint = (PrismsParserBreakpoint) value;
			javax.swing.DefaultComboBoxModel<String> model = (javax.swing.DefaultComboBoxModel<String>) ((JComboBox<String>) getComponent())
				.getModel();
			model.removeAllElements();
			model.addElement(NONE);
			for(String opName : theOpNames)
				model.addElement("<" + opName + ">");
			model.setSelectedItem(theEditingBreakpoint.getOpName() == null ? NONE : theEditingBreakpoint.getOpName());
			Component ret = super.getTableCellEditorComponent(table, value, isSelected, row, column);
			return ret;
		}

		@Override
		public boolean stopCellEditing() {
			String opName = (String) ((JComboBox<String>) getComponent()).getSelectedItem();
			if(opName.equals(NONE))
				theEditingBreakpoint.setOpName(null);
			else
				theEditingBreakpoint.setOpName(opName.substring(1, opName.length() - 1));
			breakpointChanged(theEditingBreakpoint);
			return super.stopCellEditing();
		}
	}

	private static class BreakpointDeleteRenderer extends javax.swing.table.DefaultTableCellRenderer {
		private ImageIcon theIcon = PrismsParserDebugGUI.getIcon("delete.png", 16, 16);

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			setText("");
			setIcon(theIcon);
			return this;
		}
	}

	private class BreakpointDeleteEditor extends JPanel implements TableCellEditor {
		private ArrayList<CellEditorListener> theListeners = new ArrayList<>();

		@Override
		public Object getCellEditorValue() {
			return null;
		}

		@Override
		public boolean isCellEditable(EventObject anEvent) {
			return false;
		}

		@Override
		public boolean shouldSelectCell(EventObject anEvent) {
			return false;
		}

		@Override
		public boolean stopCellEditing() {
			ChangeEvent evt = new ChangeEvent(this);
			for(CellEditorListener listener : theListeners.toArray(new CellEditorListener[0]))
				listener.editingStopped(evt);
			return true;
		}

		@Override
		public void cancelCellEditing() {
			ChangeEvent evt = new ChangeEvent(this);
			for(CellEditorListener listener : theListeners.toArray(new CellEditorListener[0]))
				listener.editingCanceled(evt);
		}

		@Override
		public void addCellEditorListener(CellEditorListener l) {
			theListeners.add(l);
		}

		@Override
		public void removeCellEditorListener(CellEditorListener l) {
			theListeners.remove(l);
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, final Object value, boolean isSelected, int row, int column) {
			EventQueue.invokeLater(new Runnable() {
				@Override
				public void run() {
					stopCellEditing();
					breakpointDeleted((PrismsParserBreakpoint) value);
				}
			});
			return this;
		}
	}

	private class OpObjectRenderer extends JPanel implements javax.swing.ListCellRenderer<OpObject> {
		private JLabel theLabel;

		OpObjectRenderer() {
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			theLabel = new JLabel();
			add(theLabel);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends OpObject> list, OpObject value, int index, boolean isSelected,
			boolean cellHasFocus) {
			StringBuilder text = new StringBuilder("<html>");
			for(int i = 0; i < value.theIndent; i++)
				text.append("&nbsp;&nbsp;&nbsp;&nbsp;");
			text.append("<font color=\"blue\">&lt;</font><font color=\"red\">");
			if(value.isTerminal)
				text.append('/');
			text.append(value.theOp.getName()).append("</font>");
			if(!value.isTerminal) {
				boolean needsEnding = true;
				for(PrismsConfig sub : value.theOp.subConfigs()) {
					if(!PrismsParser.NON_OP_CONFIGS.contains(sub.getName())) {
						needsEnding = false;
						continue;
					}
					text.append(" <font color=\"red\">").append(sub.getName()).append("</font><font color=\"blue\">=\"</font>")
					.append(sub.getValue()).append("<font color=\"blue\">\"</font>");
				}
				if(value.theOp.getValue() != null && value.theOp.getValue().length() > 0) {
					text.append("<font color=\"blue\">&gt;</font>");
					text.append(value.theOp.getValue());
					if(needsEnding) {
						text.append("<font color=\"blue\">&lt;/</font><font color=\"red\">");
						text.append(value.theOp.getName()).append("</font>");
					}
				} else if(needsEnding)
					text.append(" <font color=\"blue\">/</font>");
			}
			text.append("<font color=\"blue\">&gt;</font>");
			text.append("</html>");
			theLabel.setText(text.toString());

			Color bg = Color.white;
			boolean cursor = theTreeModel.getCursor() != null && theTreeModel.getCursor().theOp == value.theOp;
			if(cursor)
				cursor = (theTreeModel.getCursor().theMatch != null) == value.isTerminal;
			boolean selected = !value.isTerminal && theParseTree.getSelectionPath() != null
				&& ((ParseNode) theParseTree.getSelectionPath().getLastPathComponent()).theOp == value.theOp;
			if(cursor && selected)
				bg = new Color(0, 255, 255);
			else if(cursor)
				bg = Color.green;
			else if(selected)
				bg = SELECTED;
			setBackground(bg);
			return this;
		}
	}
}
