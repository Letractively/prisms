package prisms.lang.debug;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Image;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;

import prisms.arch.PrismsConfig;
import prisms.lang.ParseException;
import prisms.lang.ParseMatch;
import prisms.lang.PrismsParser;
import prisms.lang.PrismsParserDebugger;

public class PrismsParserDebugGUI extends JPanel implements PrismsParserDebugger {
	private JTextField theMainText;
	private JTree theParseTree;
	private JButton theOverButton;
	private JButton theIntoButton;
	private JButton theOutButton;
	private JButton theResumeButton;

	private JTable theBreakpointList;

	private JTextPane theDebugPane;
	private JSplitPane theMainSplit;
	private JSplitPane theRightSplit;

	private List<String> theOpNames;
	private PrismsParser theParser;

	private List<PrismsParserBreakpoint> theBreakpoints;
	private volatile PrismsConfig theTargetOp;
	private volatile boolean isSuspended;

	public PrismsParserDebugGUI() {
		super(new BorderLayout());

		theMainText = new JTextField();
		theMainText.setEditable(false);
		theParseTree = new JTree();
		theOverButton = new JButton(getIcon("arrow180.png", 24, 24));
		theIntoButton = new JButton(getIcon("arrow90down.png", 24, 24));
		theOutButton = new JButton(getIcon("arrow90right.png", 24, 24));
		theResumeButton = new JButton(getIcon("play.png", 24, 24));
		theDebugPane = new JTextPane();
		theDebugPane.setContentType("text/html");
		theDebugPane.setEditable(false);
		theBreakpointList = new JTable(0, 1);
		theBreakpointList.getColumnModel().getColumn(0).setCellRenderer(new BreakpointRenderer());
		theBreakpointList.getColumnModel().getColumn(0).setCellEditor(new BreakpointEditor());
		theMainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		theRightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

		theBreakpoints = new ArrayList<>();

		add(theMainText, BorderLayout.NORTH);
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
		JScrollPane breakpointScroll = new JScrollPane(theBreakpointList);
		theRightSplit.setBottomComponent(breakpointScroll);
		JScrollPane debugScroll = new JScrollPane(theDebugPane);
		theRightSplit.setTopComponent(debugScroll);

		((DefaultTableModel) theBreakpointList.getModel()).addRow(new Object[] {new PrismsParserBreakpoint()});

		theOpNames = new ArrayList<>();
	}

	private static ImageIcon getIcon(String location, int w, int h) {
		ImageIcon icon = new ImageIcon(PrismsParserDebugGUI.class.getResource(location));
		Image img = icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
		return new ImageIcon(img);
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
	public void preParse(CharSequence text, int index, PrismsConfig op) {
		update(text, index, op, null);
		if(theTargetOp == op)
			suspend(null);
		else {
			for(PrismsParserBreakpoint breakpoint : theBreakpoints) {
				if(breakpoint.getPreCursorText() == null
					|| (breakpoint.getPreCursorText().matcher(text.subSequence(0, index)).matches() && breakpoint.getPostCursorText()
						.matcher(text.subSequence(index, text.length())).matches())) {
					if(breakpoint.getOpName() == null || breakpoint.getOpName().equals(op.get("name"))) {
						suspend(breakpoint);
						break;
					}
				}
			}
		}
	}

	@Override
	public void postParse(CharSequence text, int startIndex, PrismsConfig op, ParseMatch match) {
		update(text, startIndex, op, match);
	}

	private void reset() {
	}

	private void update(CharSequence text, int index, PrismsConfig op, ParseMatch match) {
	}

	private void suspend(PrismsParserBreakpoint breakpoint) {
		isSuspended = true;
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				// TODO Update GUI state to allow user to interact with debugger
			}
		});
		while(isSuspended) {
			try {
				Thread.sleep(50);
			} catch(InterruptedException e) {
			}
		}
	}

	private static class BreakpointRenderer implements DCRTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable aTable, Object aValue, boolean aIsSelected, boolean aHasFocus, int aRow,
			int aColumn) {
			// TODO Auto-generated method stub
			return null;
		}
	}

	private static class BreakpointEditor implements TableCellEditor {
	}
}
