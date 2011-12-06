package prisms.lang;

/** Functions as an intellisense menu for the InterpreterPanel, but may be used elsewhere. */
public class IntellisenseMenu extends javax.swing.JDialog
{
	static final java.awt.Color SELECTED_COLOR = new java.awt.Color(168, 168, 255);

	static final java.awt.Color UNSELECTED_COLOR = java.awt.Color.white;

	/** A listener for the user's selection of an item in the intellisense menu */
	public static interface IntellisenseListener
	{
		/**
		 * Called when an intellisense item is selected
		 * 
		 * @param item The item selected by the user
		 * @param text The append text for the item
		 */
		void itemSelected(Object item, String text);
	}

	class IntellisenseMenuItem extends javax.swing.JPanel
	{
		private javax.swing.JLabel theTextLabel;

		private Object theItem;

		private String theAppendText;

		IntellisenseMenuItem()
		{
			theTextLabel = new javax.swing.JLabel();
			add(theTextLabel);
			addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent evt)
				{
					if(evt.getClickCount() >= 2)
						selected(IntellisenseMenuItem.this);

				}

				@Override
				public void mousePressed(java.awt.event.MouseEvent evt)
				{
					if(evt.getClickCount() == 1)
						navTo(IntellisenseMenuItem.this);
				}
			});
			// addActionListener(new java.awt.event.ActionListener()
			// {
			// public void actionPerformed(java.awt.event.ActionEvent evt)
			// {
			// selected(IntellisenseMenuItem.this);
			// }
			// });
		}

		public void set(String type, String text, Object item, String appendText)
		{
			// TODO Make icons for "variable", "function", "package", "class", "field", method
			theTextLabel.setText(text);
			setSelected(false);
			theItem = item;
			theAppendText = appendText;
		}

		public void setSelected(boolean b)
		{
			if(b)
				setBackground(SELECTED_COLOR);
			else
				setBackground(UNSELECTED_COLOR);
		}

		@Override
		public void doLayout()
		{
			theTextLabel.setBounds(0, 0, theTextLabel.getPreferredSize().width, theTextLabel.getPreferredSize().height);
			setPreferredSize(theTextLabel.getPreferredSize());
		}

		public Object getItem()
		{
			return theItem;
		}

		public String getAppendText()
		{
			return theAppendText;
		}
	}

	private javax.swing.JPanel theMenuHolder;

	private java.util.ArrayList<IntellisenseMenuItem> theMenuItemCache;

	private java.util.ArrayList<IntellisenseListener> theListeners;

	private java.awt.Component theSensingComponent;

	private java.awt.event.KeyListener theNavListener;

	private java.awt.event.MouseListener theClickListener;

	private int theNavIndex;

	/** Creates an intellisense menu */
	public IntellisenseMenu()
	{
		theMenuHolder = new javax.swing.JPanel()
		{
			@Override
			public void doLayout()
			{
				int top = 0;
				int maxW = 0;
				for(int i = 0; i < getComponentCount(); i++)
				{
					java.awt.Component c = getComponent(i);
					int h = c.getPreferredSize().height;
					int w = c.getPreferredSize().width;
					if(w > maxW)
						maxW = w;
					top += h;
				}
				if(maxW > IntellisenseMenu.this.getMaximumSize().width)
					maxW = IntellisenseMenu.this.getMaximumSize().width;
				if(maxW < IntellisenseMenu.this.getMinimumSize().width)
					maxW = IntellisenseMenu.this.getMinimumSize().width;

				top = 0;
				for(int i = 0; i < getComponentCount(); i++)
				{
					java.awt.Component c = getComponent(i);
					int h = c.getPreferredSize().height;
					c.setBounds(0, top, maxW, h);
					top += h;
				}
				if(top > IntellisenseMenu.this.getMaximumSize().height)
				{
					top = IntellisenseMenu.this.getMaximumSize().height;
					maxW += 15;
				}
				if(top < IntellisenseMenu.this.getMinimumSize().height)
					top = IntellisenseMenu.this.getMinimumSize().height;
				if(maxW > IntellisenseMenu.this.getMaximumSize().width)
					maxW = IntellisenseMenu.this.getMaximumSize().width;
				if(maxW < IntellisenseMenu.this.getMinimumSize().width)
					maxW = IntellisenseMenu.this.getMinimumSize().width;
				IntellisenseMenu.this.setSize(new java.awt.Dimension(maxW + 3, top + 3));
			}
		};
		theMenuHolder.setLayout(new javax.swing.BoxLayout(theMenuHolder, javax.swing.BoxLayout.Y_AXIS));
		javax.swing.JScrollPane intelMenuSP = new javax.swing.JScrollPane(theMenuHolder);
		setContentPane(intelMenuSP);
		setMinimumSize(new java.awt.Dimension(10, 15));
		setMaximumSize(new java.awt.Dimension(500, 300));
		setAlwaysOnTop(true);
		setIconImage(null);
		setUndecorated(true);
		theMenuItemCache = new java.util.ArrayList<IntellisenseMenuItem>();
		theListeners = new java.util.ArrayList<IntellisenseListener>();

		theNavListener = new java.awt.event.KeyAdapter()
		{
			@Override
			public void keyPressed(java.awt.event.KeyEvent evt)
			{
				if(evt.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE)
					clear(true);
				else if(evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER)
					navSelected();
				else if(evt.getKeyCode() == java.awt.event.KeyEvent.VK_UP)
					navUp();
				else if(evt.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN)
					navDown();
			}
		};
		theClickListener = new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent evt)
			{
				clear(true);
			}
		};
	}

	/** @param il The listener to listen for the user's selections */
	public void addListener(IntellisenseListener il)
	{
		theListeners.add(il);
	}

	/**
	 * Adds a menu item to this menu
	 * 
	 * @param type The type of the item. Not used yet. TODO
	 * @param menuText The text to display for the menu item
	 * @param item The item represented by the menu item
	 * @param appendText The text to append to the input if the menu item is selected
	 */
	public void addMenuItem(String type, String menuText, Object item, String appendText)
	{
		IntellisenseMenuItem menuItem;
		if(theMenuItemCache.isEmpty())
			menuItem = new IntellisenseMenuItem();
		else
			menuItem = theMenuItemCache.remove(theMenuItemCache.size() - 1);
		menuItem.set(type, menuText, item, appendText);
		theMenuHolder.add(menuItem);
	}

	/**
	 * Clears all menu items from this menu
	 * 
	 * @param hide Whether to also hide the menu
	 */
	public void clear(boolean hide)
	{
		if(hide)
		{
			setVisible(false);
			theSensingComponent.removeKeyListener(theNavListener);
			theSensingComponent.removeMouseListener(theClickListener);
			theSensingComponent = null;
		}
		theNavIndex = 0;
		for(int c = 0; c < theMenuHolder.getComponentCount(); c++)
		{
			if(theMenuHolder.getComponent(c) instanceof IntellisenseMenuItem)
			{
				theMenuItemCache.add((IntellisenseMenuItem) theMenuHolder.getComponent(c));
				theMenuHolder.remove(c);
				c--;
			}
		}
	}

	/**
	 * Displays this menu
	 * 
	 * @param c The component to display the menu over
	 * @param pos The point on the component to display this menu over
	 * @return Whether this menu was successfully displayed
	 */
	public boolean show(java.awt.Component c, java.awt.Point pos)
	{
		if(!c.isShowing())
			return false;
		if(theSensingComponent == null)
		{
			theSensingComponent = c;
			theSensingComponent.addKeyListener(theNavListener);
			theSensingComponent.addMouseListener(theClickListener);
		}
		if(theMenuHolder.getComponentCount() == 0)
			addMenuItem(null, "No proposals", null, null);
		((IntellisenseMenuItem) theMenuHolder.getComponent(0)).setSelected(true);
		java.awt.Point p = c.getLocationOnScreen();
		setLocation(p.x + pos.x, p.y + pos.y);
		if(!isVisible())
			setVisible(true);
		if(c instanceof javax.swing.JComponent)
			((javax.swing.JComponent) c).grabFocus();
		return true;
	}

	/**
	 * Displays this menu
	 * 
	 * @param tc The text component to display the menu just under the caret position of
	 * @return Whether this menu was successfully displayed
	 */
	public boolean show(javax.swing.text.JTextComponent tc)
	{
		java.awt.Rectangle pos;
		try
		{
			pos = tc.modelToView(tc.getCaretPosition());
		} catch(javax.swing.text.BadLocationException e)
		{
			return false;
		}
		if(pos == null)
			return false;
		return show(tc, new java.awt.Point(pos.x, pos.y + pos.height));
	}

	void navUp()
	{
		theNavIndex--;
		if(theNavIndex < 0)
			theNavIndex = theMenuHolder.getComponentCount() - 1;
		for(int i = 0; i < theMenuHolder.getComponentCount(); i++)
			((IntellisenseMenuItem) theMenuHolder.getComponent(i)).setSelected(i == theNavIndex);
	}

	void navDown()
	{
		theNavIndex++;
		if(theNavIndex >= theMenuHolder.getComponentCount())
			theNavIndex = 0;
		for(int i = 0; i < theMenuHolder.getComponentCount(); i++)
			((IntellisenseMenuItem) theMenuHolder.getComponent(i)).setSelected(i == theNavIndex);
	}

	void navTo(IntellisenseMenuItem item)
	{
		theNavIndex = 0;
		for(int i = 0; i < theMenuHolder.getComponentCount(); i++)
		{
			if(theMenuHolder.getComponent(i) == item)
			{
				theNavIndex = i;
				((IntellisenseMenuItem) theMenuHolder.getComponent(i)).setSelected(true);
			}
			else
				((IntellisenseMenuItem) theMenuHolder.getComponent(i)).setSelected(false);
		}
	}

	void navSelected()
	{
		selected((IntellisenseMenuItem) theMenuHolder.getComponent(theNavIndex));
	}

	void selected(IntellisenseMenuItem item)
	{
		if(item.getItem() != null || item.getAppendText() != null)
		{
			for(IntellisenseListener il : theListeners)
				il.itemSelected(item.getItem(), item.getAppendText());
		}
		clear(true);
	}
}
