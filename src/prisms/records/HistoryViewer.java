/*
 * HistoryViewer.java Created Mar 8, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.arch.event.PrismsEvent;
import prisms.records.RecordKeeper.ChangeField;
import prisms.ui.SortTableStructure.TableCell;
import prisms.util.ArrayUtils;
import prisms.util.PrismsUtils;
import prisms.util.preferences.Preference;

/**
 * Allows the user to view the record history in a navagatable table, as well as undo and purge
 * individual entries and modify the auto-purge settings
 */
public abstract class HistoryViewer implements prisms.arch.AppPlugin
{
	private static final Logger log = Logger.getLogger(HistoryViewer.class);

	static int TYPE = 0;

	static int ITEM1 = 1;

	static int ITEM2 = 2;

	static int BEFORE = 3;

	static int AFTER = 4;

	static int USER = 5;

	static int CENTER = 6;

	static int TIME = 7;

	static java.awt.Color MARGINAL = new java.awt.Color(255, 255, 51);

	static java.awt.Color UNFAVORABLE = new java.awt.Color(204, 0, 0);

	static java.text.DecimalFormat FLOAT_FORMAT = new java.text.DecimalFormat("0.####");

	static Preference<Integer> theCountPref;

	PrismsSession theSession;

	private String theName;

	private RecordKeeper theRecordKeeper;

	private HistorySorter<ChangeField> theSorter;

	int theStart;

	int theCount;

	private prisms.ui.SortTableStructure theTable;

	private long theSnapshotTime;

	private long [] theSnapshot;

	private java.util.HashSet<Long> theSelectedIndices;

	Object theHistoryItem;

	RecordUser theActivityUser;

	PrismsCenter theActivityCenter;

	boolean isCenterActivityImport;

	SyncRecord theActivitySyncRecord;

	String theAutoPurgeChangeEvent;

	public void initPlugin(PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
		theCountPref = new Preference<Integer>(theName, "Displayed Entries",
			Preference.Type.NONEG_INT, Integer.class, true);
		theStart = 1;
		session.addEventListener("preferencesChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(prisms.arch.event.PrismsEvent evt)
			{
				prisms.util.preferences.PreferenceEvent pEvt = (prisms.util.preferences.PreferenceEvent) evt;
				if(!pEvt.getPreference().equals(theCountPref))
					return;
				theStart = 1;
				theCount = ((Integer) pEvt.getValue()).intValue();
				refresh();
				initClient();
			}
		});
		String prefPropName = pluginEl.elementTextTrim("preferenceProperty");
		if(prefPropName != null)
		{
			prisms.util.preferences.Preferences prefs = theSession
				.getProperty(prisms.arch.event.PrismsProperty.get(prefPropName,
					prisms.util.preferences.Preferences.class));
			if(prefs.get(theCountPref) == null)
				prefs.set(theCountPref, new Integer(10));
			theCount = prefs.get(theCountPref).intValue();
		}

		theTable = new prisms.ui.SortTableStructure(TIME + 1);
		theTable.setColumn(ITEM1, "Primary", false);
		theTable.setColumn(ITEM2, "Secondary", false);
		theTable.setColumn(TYPE, "Change Type", true);
		theTable.setColumn(BEFORE, "Before", false);
		theTable.setColumn(AFTER, "After", false);
		theTable.setColumn(USER, "User", true);
		theTable.setColumn(CENTER, "Center", true);
		theTable.setColumn(TIME, "Date/Time", true);
		theSorter = new HistorySorter<ChangeField>();
		theSelectedIndices = new java.util.HashSet<Long>();
		String showHistoryEvent = pluginEl.elementTextTrim("showHistoryEvent");
		if(showHistoryEvent != null)
		{
			theSession.addEventListener(showHistoryEvent,
				new prisms.arch.event.PrismsEventListener()
				{
					public void eventOccurred(PrismsEvent evt)
					{
						theHistoryItem = evt.getProperty("item");
						theActivityUser = null;
						theActivityCenter = null;
						reset(true);
					}
				});
		}
		String showUserActivityEvent = pluginEl.elementTextTrim("userActivityEvent");
		if(showUserActivityEvent != null)
		{
			theSession.addEventListener(showUserActivityEvent,
				new prisms.arch.event.PrismsEventListener()
				{
					public void eventOccurred(PrismsEvent evt)
					{
						theHistoryItem = null;
						theActivityUser = (RecordUser) evt.getProperty("user");
						theActivityCenter = null;
						reset(true);
					}
				});
		}
		String showCenterHistoryEvent = pluginEl.elementTextTrim("centerHistoryEvent");
		if(showCenterHistoryEvent != null)
		{
			theSession.addEventListener(showCenterHistoryEvent,
				new prisms.arch.event.PrismsEventListener()
				{
					public void eventOccurred(PrismsEvent evt)
					{
						theHistoryItem = null;
						theActivityCenter = (PrismsCenter) evt.getProperty("center");
						isCenterActivityImport = ((Boolean) evt.getProperty("import"))
							.booleanValue();
						theActivityUser = null;
						reset(true);
					}
				});
		}
		String showSyncRecordHistoryEvent = pluginEl.elementTextTrim("syncRecordHistoryEvent");
		if(showSyncRecordHistoryEvent != null)
		{
			theSession.addEventListener("showSyncRecordHistory",
				new prisms.arch.event.PrismsEventListener()
				{
					public void eventOccurred(PrismsEvent evt)
					{
						theHistoryItem = null;
						theActivitySyncRecord = (SyncRecord) evt.getProperty("syncRecord");
						theActivityCenter = null;
						theActivityUser = null;
						reset(true);
					}
				});
		}
		theAutoPurgeChangeEvent = pluginEl.elementTextTrim("autoPurgeChangeEvent");
		if(theAutoPurgeChangeEvent == null)
			theAutoPurgeChangeEvent = "autoPurgeChanged";
		theSession.addEventListener(theAutoPurgeChangeEvent,
			new prisms.arch.event.PrismsEventListener()
			{
				public void eventOccurred(PrismsEvent evt)
				{
					sendAutoPurge();
				}
			});
		reset(false);
	}

	public void initClient()
	{
		sendDisplay(false);
	}

	/**
	 * @return The session that this viewer is in
	 */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/**
	 * @return This plugin's name
	 */
	public String getName()
	{
		return theName;
	}

	/**
	 * Sets this viewer's record keeper. This must be called before this viewer does anything.
	 * 
	 * @param keeper The record keeper for this viewer to use
	 */
	protected void setRecordKeeper(RecordKeeper keeper)
	{
		theRecordKeeper = keeper;
		reset(false);
	}

	/**
	 * @return The record keeper that this viewer is viewing
	 */
	protected RecordKeeper getRecordKeeper()
	{
		return theRecordKeeper;
	}

	long [] getFilteredSnapshot()
	{
		if(theSnapshot == null)
			return new long [0];
		if(theStart < 1)
			theStart = 1;
		if(theStart > theSnapshot.length)
			return new long [0];
		int length = theCount;
		if(length > theSnapshot.length - theStart + 1)
			length = theSnapshot.length - theStart + 1;
		long [] ret = new long [length];
		System.arraycopy(theSnapshot, theStart - 1, ret, 0, length);
		return ret;
	}

	public void processEvent(JSONObject evt)
	{
		if("refresh".equals(evt.get("method")))
		{
			refresh();
			initClient();
		}
		else if("navigate".equals(evt.get("method")))
		{
			int idx = ((Number) evt.get("start")).intValue();
			theStart = idx;
			initClient();
		}
		else if("goToLink".equals(evt.get("method")))
		{
			JSONObject id = (JSONObject) evt.get("id");
			goToLink(id);
		}
		else if("setSort".equals(evt.get("method")))
		{
			theSorter.addSort(getFieldName((String) evt.get("column")), ((Boolean) evt
				.get("ascending")).booleanValue());
			refresh();
			initClient();
		}
		else if("setSelected".equals(evt.get("method")))
		{
			setSelected(((Number) evt.get("start")).intValue(), ((Number) evt.get("end"))
				.intValue(), ((Boolean) evt.get("selected")).booleanValue());
		}
		else if("undoSelected".equals(evt.get("method")))
			undoSelected();
		else if("purgeSelected".equals(evt.get("method")))
			purgeSelected();
		else if("showAllHistory".equals(evt.get("method")))
		{
			theHistoryItem = null;
			theActivityUser = null;
			theActivityCenter = null;
			theActivitySyncRecord = null;
			reset(true);
		}
		else if("setAutoPurge".equals(evt.get("method")))
			setAutoPurge((JSONObject) evt.get("autoPurge"));
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event: " + evt);
	}

	void refresh()
	{
		if(theRecordKeeper == null)
			return;
		theSnapshotTime = System.currentTimeMillis();
		try
		{
			long [] snapshot;
			if(theHistoryItem != null)
				snapshot = theRecordKeeper.getHistory(theHistoryItem);
			else if(theActivityUser != null)
				snapshot = theRecordKeeper.getHistoryBy(theActivityUser);
			else if(theActivityCenter != null)
			{
				prisms.records.SyncRecord[] records = theRecordKeeper.getSyncRecords(
					theActivityCenter, new Boolean(isCenterActivityImport));
				snapshot = new long [0];
				for(prisms.records.SyncRecord record : records)
					snapshot = (long []) ArrayUtils.concatP(Long.TYPE, snapshot, theRecordKeeper
						.getSyncChanges(record));
			}
			else if(theActivitySyncRecord != null)
				snapshot = theRecordKeeper.getSyncChanges(theActivitySyncRecord);
			else
				snapshot = theRecordKeeper.getChangeIDs(0);

			theSnapshot = theRecordKeeper.sortChangeIDs(snapshot, theSorter);
		} catch(PrismsRecordException e)
		{
			theSnapshot = new long [0];
			throw new IllegalStateException("Could not get history", e);
		}
		if(theStart > theSnapshot.length)
			theStart = 0;
		java.util.Iterator<Long> selIter = theSelectedIndices.iterator();
		while(selIter.hasNext())
			if(!ArrayUtils.containsP(theSnapshot, selIter.next()))
				selIter.remove();
	}

	void reset(boolean show)
	{
		theSorter.clear();
		theStart = 1;
		theSelectedIndices.clear();
		refresh();
		sendDisplay(show);
	}

	void sendDisplay(boolean show)
	{
		ChangeRecord [] mods;
		long [] fs = getFilteredSnapshot();
		if(fs.length == 0)
			mods = new ChangeRecord [0];
		else
			try
			{
				mods = theRecordKeeper.getChanges(fs);
			} catch(PrismsRecordException e)
			{
				throw new IllegalStateException("Could not get history", e);
			}
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setSnapshotTime");
		evt.put("time", PrismsUtils.print(theSnapshotTime));
		theSession.postOutgoingEvent(evt);
		sendItem();
		sendAutoPurge();
		sendContent(fs, mods, show);
	}

	void sendItem()
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setItem");
		JSONObject jsonItem = null;
		if(theHistoryItem != null)
		{
			jsonItem = new JSONObject();
			jsonItem.put("text", display(theHistoryItem));
		}
		else if(theActivityUser != null)
		{
			jsonItem = new JSONObject();
			jsonItem.put("isUserActivity", Boolean.TRUE);
			jsonItem.put("text", theActivityUser.getName());
		}
		else if(theActivityCenter != null)
		{
			jsonItem = new JSONObject();
			jsonItem.put("isCenterActivity", Boolean.TRUE);
			jsonItem.put("import", new Boolean(isCenterActivityImport));
			jsonItem.put("text", theActivityCenter.getName());
		}
		else if(theActivitySyncRecord != null)
		{
			jsonItem = new JSONObject();
			jsonItem.put("isSyncRecordActivity", Boolean.TRUE);
			jsonItem.put("import", new Boolean(theActivitySyncRecord.isImport()));
			jsonItem.put("text", theActivitySyncRecord.getCenter() + " at "
				+ PrismsUtils.print(theActivitySyncRecord.getSyncTime()));
		}
		evt.put("item", jsonItem);
		theSession.postOutgoingEvent(evt);
	}

	void sendContent(long [] ids, ChangeRecord [] mods, boolean show)
	{
		theTable.setRowCount(ids.length);
		for(int m = 0; m < ids.length; m++)
			setRow(getMod(mods, ids[m]), m);
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setContent");
		evt.put("content", theTable.serialize(theStart, theStart + ids.length - 1, theCount,
			theSnapshot == null ? 0 : theSnapshot.length));
		evt.put("show", new Boolean(show));
		theSession.postOutgoingEvent(evt);
	}

	void sendAutoPurge()
	{
		if(theRecordKeeper == null)
			return;
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setPurgeEnabled");
		evt.put("enabled", new Boolean(canEditAutoPurge(getUser())));
		theSession.postOutgoingEvent(evt);

		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setAutoPurge");
		JSONObject autoPurge;
		prisms.records.AutoPurger ap;
		try
		{
			ap = theRecordKeeper.getAutoPurger();
		} catch(PrismsRecordException e)
		{
			throw new IllegalStateException("Could not get auto-purger", e);
		}
		if(ap == null)
			autoPurge = null;
		else
		{
			autoPurge = new JSONObject();
			autoPurge.put("entryCount", ap.getEntryCount() >= 0 ? new Integer(ap.getEntryCount())
				: null);
			autoPurge.put("age", ap.getAge() >= 0 ? new Integer((int) (ap.getAge() / 1000)) : null);

			JSONArray jsonA = new JSONArray();
			for(RecordUser u : getUsers())
				jsonA.add(u.getName());
			autoPurge.put("allUsers", jsonA);

			jsonA = new JSONArray();
			for(RecordUser u : ap.getExcludeUsers())
				jsonA.add(u.getName());
			autoPurge.put("excludeUsers", jsonA);
			// TODO auto-purge types
		}
		evt.put("autoPurge", autoPurge);
		theSession.postOutgoingEvent(evt);
	}

	ChangeRecord getMod(ChangeRecord [] mods, long id)
	{
		for(int m = 0; m < mods.length; m++)
			if(mods[m].id == id)
				return mods[m];
		return null;
	}

	void setRow(ChangeRecord mod, int index)
	{
		prisms.ui.SortTableStructure.TableRow row = theTable.row(index);
		row.clear();
		row.cell(TYPE).setBold(true);
		if(mod == null)
		{
			row.cell(TYPE).setLabel("(Entry Purged or Malformed)");
			row.cell(ITEM1).setLabel("---------");
			row.cell(ITEM2).setLabel("---------");
			row.cell(BEFORE).setLabel("---------");
			row.cell(AFTER).setLabel("---------");
			row.cell(USER).setLabel("---------");
			row.cell(CENTER).setLabel("---------");
			row.cell(TIME).setLabel("---------");
			return;
		}
		row.cell(TYPE).setLabel(mod.type.toString());
		row.setSelected(theSelectedIndices.contains(new Long(mod.id)));
		setUser(row.cell(USER), mod.user);
		int centerID = RecordKeeper.getCenterID(mod.user.getID());
		if(centerID == theRecordKeeper.getCenterID())
			row.cell(CENTER).setLabel("Here");
		else
		{
			boolean found = false;
			PrismsCenter [] centers;
			try
			{
				centers = theRecordKeeper.getCenters();
			} catch(PrismsRecordException e)
			{
				throw new IllegalStateException("Could not get centers", e);
			}
			for(PrismsCenter center : centers)
				if(center.getCenterID() == centerID)
				{
					setCenter(row.cell(CENTER), center);
					found = true;
					break;
				}
			if(!found)
				row.cell(CENTER).setLabel("Unknown");
		}
		row.cell(TIME).setLabel(printTime(new Long(mod.time)));
		Object afterValue = null;
		if(mod.type.changeType != null)
		{
			ChangeRecord [] successor;
			try
			{
				long [] ids = theRecordKeeper.getSuccessors(mod);
				if(ids.length > 1)
					ids = new long [] {ids[0]};
				successor = theRecordKeeper.getChanges(ids);
			} catch(PrismsRecordException e)
			{
				throw new IllegalStateException("Could not get next modification", e);
			}
			if(successor.length > 0)
				afterValue = successor[0].previousValue;
			else
				afterValue = getFieldValue(mod.majorSubject, mod.minorSubject, mod.type);
		}
		set(row.cell(ITEM1), row.cell(ITEM2), row.cell(BEFORE), row.cell(AFTER), mod, afterValue);
	}

	/**
	 * @param linkID The link ID to navigate to
	 */
	protected void goToLink(JSONObject linkID)
	{
		goToLink((String) linkID.get("type"), deserializeID((String) linkID.get("id")));
	}

	/**
	 * Serializes a long integer ID into a hexadecimal string. Javascript mangles very long integers
	 * (since it actually uses a 64-bit floating-point type), so this is necessary
	 * 
	 * @param id The ID to serialize
	 * @return The serialized ID
	 */
	protected String serializeID(long id)
	{
		return Long.toHexString(id);
	}

	/**
	 * Deserializes a long integer ID from a hexadecimal string
	 * 
	 * @param id The serialized ID
	 * @return The deserialized ID
	 */
	protected long deserializeID(String id)
	{
		return Long.parseLong(id, 16);
	}

	void setSelected(int start, int end, boolean selected)
	{
		for(int i = start - 1; i <= end - 1; i++)
		{
			if(selected)
				theSelectedIndices.add(new Long(theSnapshot[i]));
			else
				theSelectedIndices.remove(new Long(theSnapshot[i]));
		}
	}

	void undoSelected()
	{
		if(theSelectedIndices.size() == 0)
			return;
		long [] ids = new long [theSelectedIndices.size()];
		int i = 0;
		for(Long id : theSelectedIndices)
			ids[i++] = id.longValue();
		ChangeRecord [] mods;
		try
		{
			mods = theRecordKeeper.getChanges(ids);
		} catch(PrismsRecordException e)
		{
			throw new IllegalStateException("Cannot get changes", e);
		}
		// Mods need to be undone in reverse time order
		java.util.Arrays.sort(mods, new java.util.Comparator<ChangeRecord>()
		{
			public int compare(ChangeRecord mod1, ChangeRecord mod2)
			{
				if(mod1.time > mod2.time)
					return -1;
				else if(mod1.time < mod2.time)
					return 1;
				else
					return 0;
			}
		});
		final prisms.ui.UI ui = (prisms.ui.UI) theSession.getPlugin("UI");
		String error = null;
		for(int m = 0; m < mods.length; m++)
		{
			String modError = assertCanUndo(getUser(), mods[m]);
			if(modError != null)
			{
				mods = prisms.util.ArrayUtils.remove(mods, m);
				if(error == null)
					error = modError;
				else
					error += "\n" + modError;
				m--;
			}
		}
		if(error != null)
			ui.error(error);
		if(mods.length == 0)
			return;
		final ChangeRecord [] fMods = mods;
		prisms.ui.UI.ConfirmListener cl = new prisms.ui.UI.ConfirmListener()
		{
			public void confirmed(boolean confirm)
			{
				if(!confirm)
					return;
				String error2 = null;
				for(ChangeRecord mod : fMods)
				{
					String modError = undo(mod);
					if(modError != null)
					{
						if(error2 == null)
							error2 = modError;
						else
							error2 += "\n" + modError;
					}
				}
				if(error2 != null)
					ui.error(error2);
			}
		};
		String uiStr;
		if(fMods.length > 1)
			uiStr = "these " + fMods.length + " modifications";
		else
		{
			ChangeRecord [] succ;
			try
			{
				long [] sIDs = theRecordKeeper.getSuccessors(fMods[0]);
				if(sIDs.length > 1)
					sIDs = new long [] {sIDs[0]};
				succ = theRecordKeeper.getChanges(sIDs);
			} catch(PrismsRecordException e)
			{
				log.error("Could not get successors of " + fMods[0], e);
				succ = new ChangeRecord [0];
			}
			Object preValue;
			if(succ.length > 0)
				preValue = succ[0].previousValue;
			else
				preValue = getFieldValue(fMods[0].majorSubject, fMods[0].minorSubject,
					fMods[0].type);
			uiStr = "modification " + fMods[0].toString(preValue);
		}
		ui.confirm("Are you sure you want to undo " + uiStr + "?", cl);
	}

	void purgeSelected()
	{
		if(theSelectedIndices.size() == 0)
			return;
		if(!canPurge(getUser()))
			throw new IllegalArgumentException("You do not have permission to purge modifications");
		long maxTime;
		long [] ids = new long [theSelectedIndices.size()];
		int i = 0;
		for(Long id : theSelectedIndices)
			ids[i++] = id.longValue();
		ChangeRecord [] _mods;
		try
		{
			maxTime = theRecordKeeper.getPurgeSafeTime();
			_mods = theRecordKeeper.getChanges(ids);
		} catch(PrismsRecordException e)
		{
			throw new IllegalStateException("Cannot get modifications", e);
		}
		String timeError = "";
		for(int m = 0; m < _mods.length; m++)
			if(_mods[m].time >= maxTime && shouldSaveForSync(_mods[m]))
			{
				timeError += _mods[m].toString() + " is being saved for synchronization"
					+ " by one or more clients and cannot be purged\n";
				_mods = ArrayUtils.remove(_mods, m);
				m--;
			}
		final prisms.ui.UI ui = (prisms.ui.UI) theSession.getPlugin("UI");
		if(timeError.length() > 0)
			ui.error(timeError.substring(0, timeError.length() - 1));
		if(_mods.length == 0)
			return;
		final ChangeRecord [] mods = _mods;
		prisms.ui.UI.ConfirmListener cl = new prisms.ui.UI.ConfirmListener()
		{
			public void confirmed(boolean confirm)
			{
				if(!confirm)
					return;
				String error = null;
				for(ChangeRecord mod : mods)
				{
					String modError = purge(mod);
					if(modError != null)
					{
						if(error == null)
							error = modError;
						else
							error += "\n" + modError;
					}
				}
				if(error != null)
					ui.error(error);
				refresh();
				initClient();
			}
		};
		String warn = null;
		for(ChangeRecord mod : mods)
		{
			String modWarn = getPurgeWarn(mod);
			if(modWarn != null)
			{
				if(warn == null)
					warn = modWarn;
				else
					warn += "\n" + modWarn;
			}
		}
		String uiStr;
		if(warn != null)
			uiStr = warn + "\nDo you want to proceed?";
		else if(mods.length > 1)
			uiStr = "Are you sure you want to purge these " + ids.length + " modifications?";
		else
			uiStr = "Are you sure you want to purge this modification?";
		ui.confirm(uiStr, cl);
	}

	/**
	 * @param mod The modification to save or not
	 * @return Whether the modification should be saved to be picked up by synchronization by
	 *         clients that have not received the modification yet.
	 */
	protected boolean shouldSaveForSync(ChangeRecord mod)
	{
		return !(mod.type.subjectType instanceof PrismsChange);
	}

	void setAutoPurge(JSONObject ap)
	{
		final AutoPurger purger = new AutoPurger();
		if(ap.get("entryCount") != null)
			purger.setEntryCount(((Number) ap.get("entryCount")).intValue());
		else
			purger.setEntryCount(-1);
		if(ap.get("age") != null)
			purger.setAge(((Number) ap.get("age")).intValue() * 1000L);
		else
			purger.setAge(-1);
		for(RecordUser u : getUsers())
		{
			if(((JSONArray) ap.get("excludeUsers")).contains(u.getName()))
				purger.addExcludeUser(u);
			else if(ArrayUtils.contains(purger.getExcludeUsers(), u))
				purger.removeExcludeUser(u);
		}
		// TODO exclude types
		prisms.ui.UI ui = (prisms.ui.UI) theSession.getPlugin("UI");
		try
		{
			if(purger.equals(theRecordKeeper.getAutoPurger()))
			{
				if(ui != null)
					ui.info("Auto-Purge settings are not modified");
				return;
			}
		} catch(PrismsRecordException e)
		{
			throw new IllegalStateException("Could not get auto-purger", e);
		}
		prisms.ui.UI.ConfirmListener cl = new prisms.ui.UI.ConfirmListener()
		{
			public void confirmed(boolean confirm)
			{
				if(confirm)
				{
					try
					{
						getRecordKeeper().setAutoPurger(purger, getUser());
					} catch(PrismsRecordException e)
					{
						throw new IllegalStateException("Can't set auto-purger", e);
					}
					theSession.fireEvent(new PrismsEvent(theAutoPurgeChangeEvent));
					theSession.runEventually(new Runnable()
					{
						public void run()
						{
							refresh();
						}
					});
				}
				else
					sendAutoPurge();
			}
		};
		int count;
		try
		{
			count = theRecordKeeper.previewAutoPurge(purger);
		} catch(PrismsRecordException e)
		{
			throw new IllegalStateException("Can't preview auto-purger", e);
		}
		if(ui != null)
		{
			String msg;
			if(count > 0)
				msg = "These auto-purge settings will delete at least " + count
					+ " entries immediately.\nAre you sure you want to set it this way?";
			else
				msg = "Are you sure you want to change the auto-purge settings?";
			ui.confirm(msg, cl);
		}
		else
			cl.confirmed(true);
	}

	static ChangeField getFieldName(String fieldLabel)
	{
		if(fieldLabel.equals("Change Type"))
			return ChangeField.CHANGE_TYPE;
		else if(fieldLabel.equals("User"))
			return ChangeField.CHANGE_USER;
		else if(fieldLabel.equals("Date/Time"))
			return ChangeField.CHANGE_TIME;
		else
			throw new IllegalArgumentException("Unrecognized sort field: " + fieldLabel);
	}

	String getPurgeWarn(ChangeRecord mod)
	{
		if(mod.type.additivity >= 0 || mod.type.changeType != null)
			return null;
		String ret = "This will cause " + display(mod.majorSubject)
			+ " to be unavailable for re-creation";
		return ret;
	}

	String purge(ChangeRecord mod)
	{
		try
		{
			theRecordKeeper.purge(mod, null);
			return null;
		} catch(PrismsRecordException e)
		{
			log.error("Could not purge modification " + mod, e);
			return "Could not purge modification " + mod;
		}
	}

	/**
	 * @return The record user using this viewer
	 */
	protected abstract RecordUser getUser();

	/**
	 * @return All users available for exclusion in the auto purge
	 */
	protected abstract RecordUser [] getUsers();

	/**
	 * Navigates a link clicked in the table
	 * 
	 * @param type The type of object clicked
	 * @param id The ID of the object clicked
	 */
	protected abstract void goToLink(String type, long id);

	/**
	 * This should be implemented by subclasses to handle types other than centers and auto-purge
	 * 
	 * @param item The item to display. This is a major type in a modification.
	 * @return A display string to represent the item in the UI
	 */
	protected String display(Object item)
	{
		if(item instanceof PrismsCenter)
			return "Center " + ((PrismsCenter) item).getName();
		else if(item instanceof AutoPurger)
			return "Auto-Purger";
		else
			throw new IllegalArgumentException("Unrecognized display type: "
				+ item.getClass().getName());
	}

	/**
	 * Represents a center in a display cell.
	 * 
	 * @param cell The table cell to represent the center
	 * @param center The center to represent
	 */
	protected void setCenter(TableCell cell, PrismsCenter center)
	{
		if(center == null)
		{
			cell.setLabel("none");
			return;
		}
		String label = center.getName();
		if(center.isDeleted())
			label += " (deleted)";
		cell.set(trim(label), null, "prisms/center");
	}

	/**
	 * Represents a user in a display cell.
	 * 
	 * @param cell The table cell to represent the user
	 * @param user The user to represent
	 */
	protected void setUser(TableCell cell, RecordUser user)
	{
		if(user == null)
		{
			cell.setLabel("none");
			return;
		}
		String label = user.getName();
		if(user.isDeleted())
			label += " (deleted)";
		cell.set(trim(label), null, "prisms/user");
	}

	/**
	 * Gets the value of a field on an object. This should be overridden by subclasses to handle
	 * types other than instances of {@link PrismsChange}
	 * 
	 * @param majorSubject The major subject of the change
	 * @param minorSubject The minor subject of the change
	 * @param type The type of the change
	 * @return The value of the field in the subject
	 */
	protected Object getFieldValue(Object majorSubject, Object minorSubject, RecordType type)
	{
		if(!(type.subjectType instanceof PrismsChange))
			throw new IllegalArgumentException("Unrecognized subjectType "
				+ type.subjectType.getClass().getName());
		switch((PrismsChange) type.subjectType)
		{
		case center:
			PrismsCenter center = (PrismsCenter) majorSubject;
			switch((PrismsChange.CenterChange) type.changeType)
			{
			case name:
				return center.getName();
			case url:
				return center.getServerURL();
			case serverUserName:
				return center.getServerUserName();
			case serverPassword:
				return center.getServerPassword();
			case syncFrequency:
				return new Long(center.getServerSyncFrequency());
			case clientUser:
				return center.getClientUser();
			case changeSaveTime:
				return new Long(center.getChangeSaveTime());
			}
			break;
		case autoPurge:
			AutoPurger purger = (AutoPurger) majorSubject;
			switch((PrismsChange.AutoPurgeChange) type.changeType)
			{
			case entryCount:
				return new Integer(purger.getEntryCount());
			case age:
				return new Long(purger.getAge());
			case excludeUser:
			case excludeType:
				return null;
			}
			break;
		}
		throw new IllegalStateException("Unrecognized PRISMS type " + type.subjectType);
	}

	/**
	 * Sets the value of the major subject, minor subject, before, and after table cells
	 * 
	 * @param majorSubjectCell The cell to represent the major subject
	 * @param minorSubjectCell The cell to represent the minor subject
	 * @param beforeCell The cell to represent the value before the change
	 * @param afterCell The cell to represent the value after the change
	 * @param mod The change to represent
	 * @param afterValue The value after the change, or null if this is not a field-modification
	 *        operation
	 */
	protected void set(TableCell majorSubjectCell, TableCell minorSubjectCell,
		TableCell beforeCell, TableCell afterCell, ChangeRecord mod, Object afterValue)
	{
		if(!(mod.type.subjectType instanceof PrismsChange))
			throw new IllegalArgumentException("Unrecognized subjectType "
				+ mod.type.subjectType.getClass().getName());
		switch((PrismsChange) mod.type.subjectType)
		{
		case center:
			setCenter(majorSubjectCell, (PrismsCenter) mod.majorSubject);
			if(mod.type.changeType == null)
				return;
			switch((PrismsChange.CenterChange) mod.type.changeType)
			{
			case name:
				beforeCell.setLabel((String) mod.previousValue);
				afterCell.setLabel((String) afterValue);
				return;
			case url:
			case serverUserName:
				beforeCell
					.setLabel(mod.previousValue == null ? "none" : (String) mod.previousValue);
				afterCell.setLabel(afterValue == null ? "none" : (String) afterValue);
				return;
			case serverPassword:
				beforeCell.setLabel(mod.previousValue == null ? "none"
					: protect((String) mod.previousValue));
				afterCell.setLabel(afterValue == null ? "none" : protect((String) afterValue));
				return;
			case syncFrequency:
				long time = ((Number) mod.previousValue).longValue();
				beforeCell.setLabel(time > 0 ? PrismsUtils.printTimeLength(time) : "none");
				time = ((Number) afterValue).longValue();
				afterCell.setLabel(time > 0 ? PrismsUtils.printTimeLength(time) : "none");
				return;
			case clientUser:
				setUser(beforeCell, (RecordUser) mod.previousValue);
				setUser(afterCell, (RecordUser) afterValue);
				return;
			case changeSaveTime:
				time = ((Number) mod.previousValue).longValue();
				beforeCell.setLabel(time > 0 ? PrismsUtils.printTimeLength(time) : "none");
				time = ((Number) afterValue).longValue();
				afterCell.setLabel(time > 0 ? PrismsUtils.printTimeLength(time) : "none");
				return;
			}
			throw new IllegalStateException("Unrecognized center change " + mod.type.changeType);
		case autoPurge:
			majorSubjectCell.set("Auto-Purger", null, null);
			if(mod.type.changeType == null)
				return;
			switch((PrismsChange.AutoPurgeChange) mod.type.changeType)
			{
			case entryCount:
				int count = ((Number) mod.previousValue).intValue();
				beforeCell.setLabel(count >= 0 ? "" + count : "none");
				count = ((Number) afterValue).intValue();
				afterCell.setLabel(count >= 0 ? "" + count : "none");
				return;
			case age:
				beforeCell.setLabel(printTime(mod.previousValue));
				afterCell.setLabel(printTime(afterValue));
				return;
			case excludeUser:
				setUser(minorSubjectCell, (RecordUser) mod.minorSubject);
				return;
			case excludeType:
				minorSubjectCell.setLabel((String) mod.previousValue);
				return;
			}
			throw new IllegalStateException("Unrecognized auto-purge change " + mod.type.changeType);
		}
		throw new IllegalStateException("Unrecognized PRISMS type " + mod.type.subjectType);
	}

	/**
	 * Trims a string for display in an area with limited space.
	 * 
	 * @param str The string to display
	 * @return The trimmed string
	 */
	protected final String trim(String str)
	{
		if(str.length() > 83)
			str = str.substring(0, 80) + "...";
		return str;
	}

	/**
	 * @param time The time Number to display
	 * @return The GMT time display represented by the number
	 */
	protected static String printTime(Object time)
	{
		if(!(time instanceof Long))
			return "None";
		else if(((Long) time).longValue() < 0)
			return "None";
		else
			return PrismsUtils.print(((Long) time).longValue());
	}

	/**
	 * @param f The Number to print
	 * @return The formatted floating point representation
	 */
	protected static String printFloat(Object f)
	{
		if(!(f instanceof Float) && !(f instanceof Double))
			return "None";
		else if(Double.isNaN(((Number) f).doubleValue()))
			return "None";
		else
			return FLOAT_FORMAT.format(f);
	}

	String protect(String s)
	{
		char [] ret = new char [s.length()];
		for(int i = 0; i < ret.length; i++)
			ret[i] = '*';
		return new String(ret);
	}

	/**
	 * @param user The user using this viewer
	 * @return Whether the user has authority to purge history items
	 */
	protected abstract boolean canPurge(RecordUser user);

	/**
	 * @param user The user using this viewer
	 * @return Whether the user has authority to modify auto-purge settings
	 */
	protected abstract boolean canEditAutoPurge(RecordUser user);

	/**
	 * Asserts that the given user can undo a change
	 * 
	 * @param user The user using this viewer
	 * @param mod The change to undo
	 * @return null if the user can, in fact, undo the change. An error string if the user cannot.
	 */
	protected abstract String assertCanUndo(RecordUser user, ChangeRecord mod);

	/**
	 * Undoes a change. This method should be overridden in subclasses to handle change types other
	 * than centers and auto-purge.
	 * 
	 * @param mod The change to undo
	 * @return null if the undo succeeds, an error string if the undo cannot be performed
	 */
	protected String undo(ChangeRecord mod)
	{
		if(!(mod.type.subjectType instanceof PrismsChange))
			throw new IllegalArgumentException("Unrecognized subjectType "
				+ mod.type.subjectType.getClass().getName());
		switch((PrismsChange) mod.type.subjectType)
		{
		case center:
			PrismsCenter center = (PrismsCenter) mod.majorSubject;
			if(mod.type.changeType == null)
			{
				if(mod.type.additivity > 0)
					return undoCenterCreated(center);
				else
					return undoCenterDeleted(center);
			}
			else
			{
				switch((PrismsChange.CenterChange) mod.type.changeType)
				{
				case name:
				case url:
				case serverUserName:
				case serverPassword:
				case syncFrequency:
				case clientUser:
				case changeSaveTime:
					return undoCenterChanged(center, mod);
				}
				return "Unrecognized " + mod.type.subjectType + " change type "
					+ mod.type.changeType;
			}
		case autoPurge:
			AutoPurger purger = (AutoPurger) mod.majorSubject;
			if(mod.type.changeType == null)
				throw new IllegalStateException("Auto purge cannot be added or removed");
			switch((PrismsChange.AutoPurgeChange) mod.type.changeType)
			{
			case entryCount:
			case age:
				return undoAutoPurgeModified(purger, mod);
			case excludeType:
				if(mod.type.additivity > 0)
					return undoAutoPurgeTypeAdded(purger, (RecordType) mod.previousValue);
				else
					return undoAutoPurgeTypeRemoved(purger, (RecordType) mod.previousValue);
			case excludeUser:
				RecordUser user = (RecordUser) mod.minorSubject;
				if(mod.type.additivity > 0)
					return undoAutoPurgeUserAdded(purger, user);
				else
					return undoAutoPurgeUserRemoved(purger, user);
			}
		}
		throw new IllegalStateException("Unrecognized PRISMS change " + mod.type.changeType);
	}

	/**
	 * Called to undo the creation of a rule center
	 * 
	 * @param center The center to remove
	 * @return An error string, or null if the operation is successful
	 */
	protected String undoCenterCreated(PrismsCenter center)
	{
		try
		{
			theRecordKeeper.removeCenter(center, getUser());
			return null;
		} catch(PrismsRecordException e)
		{
			log.error("Could not delete center", e);
			return "Could not delete center: " + e.getMessage();
		}
	}

	/**
	 * Called to undo the exclusion of a user from auto-purge
	 * 
	 * @param purger The auto-purger to modify
	 * @param user The user to re-include in auto-purge
	 * @return An error string, or null if the operation is successful
	 */
	protected String undoAutoPurgeUserAdded(AutoPurger purger, RecordUser user)
	{
		purger.removeExcludeUser(user);
		try
		{
			theRecordKeeper.setAutoPurger(purger, getUser());
		} catch(PrismsRecordException e)
		{
			log.error("Could not re-exclude user " + user + " from auto-purge", e);
			return "Could not re-exclude user " + user + " from auto-purge: " + e.getMessage();
		}
		getSession().fireEvent(new PrismsEvent("autoPurgeChanged"));
		return null;
	}

	/**
	 * Called to undo the exclusion of a change type in auto-purge
	 * 
	 * @param purger The auto-purger to modify
	 * @param type The type to re-include in auto-purge
	 * @return An error string, or null if the operation is successful
	 */
	protected String undoAutoPurgeTypeAdded(AutoPurger purger, RecordType type)
	{
		purger.removeExcludeType(type);
		try
		{
			theRecordKeeper.setAutoPurger(purger, getUser());
		} catch(PrismsRecordException e)
		{
			log.error("Could not re-exclude type " + type + " from auto-purge", e);
			return "Could not re-exclude type " + type + " from auto-purge: " + e.getMessage();
		}
		getSession().fireEvent(new PrismsEvent("autoPurgeChanged"));
		return null;
	}

	/**
	 * Called to undo the deletion of a center
	 * 
	 * @param center The center to re-create
	 * @return An error string, or null if the operation is successful
	 */
	protected String undoCenterDeleted(PrismsCenter center)
	{
		try
		{
			theRecordKeeper.putCenter(center, getUser());
			return null;
		} catch(PrismsRecordException e)
		{
			log.error("Could not re-add center", e);
			return "Could not re-add center: " + e.getMessage();
		}
	}

	/**
	 * Called to undo the inclusion of a user in auto-purge
	 * 
	 * @param purger The auto-purger to modify
	 * @param user The user to re-exclude in auto-purge
	 * @return An error string, or null if the operation is successful
	 */
	protected String undoAutoPurgeUserRemoved(AutoPurger purger, RecordUser user)
	{
		purger.addExcludeUser(user);
		try
		{
			theRecordKeeper.setAutoPurger(purger, getUser());
		} catch(PrismsRecordException e)
		{
			log.error("Could not re-include user " + user + " in auto-purge", e);
			return "Could not re-include user " + user + " in auto-purge: " + e.getMessage();
		}
		getSession().fireEvent(new PrismsEvent(theAutoPurgeChangeEvent));
		return null;
	}

	/**
	 * Called to undo the inclusion of a change type in auto-purge
	 * 
	 * @param purger The auto-purger to modify
	 * @param type The type to re-exclude in auto-purge
	 * @return An error string, or null if the operation is successful
	 */
	protected String undoAutoPurgeTypeRemoved(AutoPurger purger, RecordType type)
	{
		purger.addExcludeType(type);
		try
		{
			theRecordKeeper.setAutoPurger(purger, getUser());
		} catch(PrismsRecordException e)
		{
			log.error("Could not re-include type " + type + " in auto-purge", e);
			return "Could not re-include type " + type + " in auto-purge: " + e.getMessage();
		}
		getSession().fireEvent(new PrismsEvent(theAutoPurgeChangeEvent));
		return null;
	}

	/**
	 * Called to undo a modification to a center
	 * 
	 * @param center The center to undo the change on
	 * @param mod The modification to undo
	 * @return An error string, or null if the operation is successful
	 */
	protected String undoCenterChanged(PrismsCenter center, ChangeRecord mod)
	{
		boolean switchHit = false;
		switch((PrismsChange.CenterChange) mod.type.changeType)
		{
		case name:
			switchHit = true;
			center.setName((String) mod.previousValue);
			break;
		case url:
			switchHit = true;
			center.setServerURL((String) mod.previousValue);
			break;
		case serverUserName:
			switchHit = true;
			center.setServerUserName((String) mod.previousValue);
			break;
		case serverPassword:
			switchHit = true;
			center.setServerPassword((String) mod.previousValue);
			break;
		case syncFrequency:
			switchHit = true;
			center.setServerSyncFrequency(((Number) mod.previousValue).longValue());
			break;
		case clientUser:
			switchHit = true;
			center.setClientUser((RecordUser) mod.previousValue);
			break;
		case changeSaveTime:
			switchHit = true;
			center.setChangeSaveTime(((Number) mod.previousValue).longValue());
			break;
		}
		if(!switchHit)
			throw new IllegalStateException("Could not undo modification of rules center"
				+ "--fieldName unrecognized: " + mod.type.changeType);
		return null;
	}

	/**
	 * Called to undo a change to a field in the auto-purge
	 * 
	 * @param purger The auto-purger to modify
	 * @param mod The modification to undo
	 * @return An error string, or null if the operation is successful
	 */
	protected String undoAutoPurgeModified(AutoPurger purger, ChangeRecord mod)
	{
		boolean switchHit = false;
		switch((prisms.records.PrismsChange.AutoPurgeChange) mod.type.changeType)
		{
		case entryCount:
			switchHit = true;
			purger.setEntryCount(((Number) mod.previousValue).intValue());
			break;
		case age:
			switchHit = true;
			purger.setAge(((Number) mod.previousValue).longValue());
			break;
		case excludeType:
		case excludeUser:
			throw new IllegalStateException("Bad routing--should go to some other undo method");
		}
		if(!switchHit)
			throw new IllegalStateException(
				"Could not undo modification of auto-purge--fieldName unrecognized: "
					+ mod.type.changeType);
		try
		{
			theRecordKeeper.setAutoPurger(purger, getUser());
		} catch(PrismsRecordException e)
		{
			log.error("Could not set auto-purger", e);
			return "Could not set auto-purger: " + e.getMessage();
		}
		getSession().fireEvent(new PrismsEvent(theAutoPurgeChangeEvent));
		return null;
	}
}
