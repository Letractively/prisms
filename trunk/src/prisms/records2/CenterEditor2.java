/*
 * ConditionClassEditor.java Created May 5, 2009 by Andrew Butler, PSL
 */
package prisms.records2;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.ui.UI;
import prisms.util.json.SAJParser.ParseException;
import prisms.util.json.SAJParser.ParseState;

/**
 * Allows the user to edit a condition
 */
public abstract class CenterEditor2 implements prisms.arch.AppPlugin
{
	static final Logger log = Logger.getLogger(CenterEditor2.class);

	static prisms.util.preferences.Preference<Integer> theCountPref;

	PrismsSession theSession;

	private String theName;

	DBRecordKeeper theRecordKeeper;

	PrismsCenter theCenter;

	private String theAppName;

	private String theClientName;

	private String theServicePluginName;

	private int theNameLength;

	private int theUrlLength;

	private int theUserNameLength;

	private int thePasswordLength;

	int theImportStart;

	int theExportStart;

	int theRecordCount;

	boolean dataLock;

	/**
	 * @see prisms.arch.AppPlugin#initPlugin(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void initPlugin(PrismsSession session, org.dom4j.Element pluginEl)
	{
		theSession = session;
		theName = pluginEl.elementText("name");
		theAppName = pluginEl.elementTextTrim("service-app");
		theClientName = pluginEl.elementTextTrim("service-client");
		theServicePluginName = pluginEl.elementTextTrim("service-plugin");
		if(theAppName == null || theClientName == null || theServicePluginName == null)
			throw new IllegalStateException("service-app, service-client, and service-plugin"
				+ " elements required for center editor");
		theCountPref = new prisms.util.preferences.Preference<Integer>(theName,
			"Displayed Records", prisms.util.preferences.Preference.Type.NONEG_INT, Integer.class,
			true);
		session.addEventListener("preferencesChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				prisms.util.preferences.PreferenceEvent pEvt = (prisms.util.preferences.PreferenceEvent) evt;
				if(!pEvt.getPreference().equals(theCountPref))
					return;
				theImportStart = 1;
				theExportStart = 1;
				theRecordCount = ((Integer) pEvt.getValue()).intValue();
				sendSyncRecords(false);
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
			if(prefs.get(theCountPref) == null)
				prefs.set(theCountPref, new Integer(10));
			theRecordCount = prefs.get(theCountPref).intValue();
		}
		try
		{
			theNameLength = theRecordKeeper.getFieldSize("prisms_center_view", "name");
			theUrlLength = theRecordKeeper.getFieldSize("prisms_center_view", "url");
			theUserNameLength = theRecordKeeper
				.getFieldSize("prisms_center_view", "serverUserName");
			thePasswordLength = theRecordKeeper
				.getFieldSize("prisms_center_view", "serverPassword");
		} catch(PrismsRecordException e)
		{
			// log.error("Could not get field length", e);
			theNameLength = 64;
			theUrlLength = 256;
			theUserNameLength = 32;
			thePasswordLength = 32;
		}
		String selCenterProp = pluginEl.elementTextTrim("selectedCenterProperty");
		if(selCenterProp == null)
			throw new IllegalStateException("No selectedCenterProperty");
		prisms.arch.event.PrismsProperty<? extends PrismsCenter> centerProp = prisms.arch.event.PrismsProperty
			.get(selCenterProp, PrismsCenter.class);
		theCenter = session.getProperty(centerProp);
		session.addPropertyChangeListener(centerProp,
			new prisms.arch.event.PrismsPCL<PrismsCenter>()
			{
				public void propertyChange(prisms.arch.event.PrismsPCE<PrismsCenter> evt)
				{
					theCenter = evt.getNewValue();
					sendCenter(true, true);
				}
			});
		session.addEventListener("centerChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(evt.getProperty("center").equals(theCenter))
					sendCenter(false, false);
			}
		});
		prisms.arch.event.PrismsEventListener recordListener = new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				SyncRecord record = (SyncRecord) evt.getProperty("record");
				if(!record.getCenter().equals(theCenter))
					return;
				RecordHolder holder = new RecordHolder(record);
				int idx;
				if(record.isImport())
				{
					idx = theImportRecords.indexOf(holder);
					if(idx >= 0)
					{
						holder = theImportRecords.get(idx);
						holder.theRecord = record;
					}
					else
						theImportRecords.add(holder);
				}
				else
				{
					idx = theExportRecords.indexOf(holder);
					if(idx >= 0)
					{
						holder = theExportRecords.get(idx);
						holder.theRecord = record;
					}
					else
						theExportRecords.add(holder);
				}
				if(record.getSyncError() == null)
				{
					long [] modIDs;
					try
					{
						modIDs = theRecordKeeper.getSyncChanges(record);
						holder.theResultCount = modIDs.length;
					} catch(prisms.records2.PrismsRecordException e)
					{
						log.error("Could not get modifications for sync record " + record, e);
						holder.theResultCount = -1;
					}
				}
				sendSyncRecords(false);
			}
		};
		session.addEventListener("syncAttempted", recordListener);
		session.addEventListener("syncAttemptChanged", recordListener);
		session.addEventListener("syncPurged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(dataLock)
					return;
				SyncRecord record = (SyncRecord) evt.getProperty("record");
				if(!record.getCenter().equals(theCenter))
					return;
				RecordHolder holder = new RecordHolder(record);
				if(record.isImport())
					theImportRecords.remove(holder);
				else
					theExportRecords.remove(holder);
			}
		});
		session.addEventListener("genSyncReceipt", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				try
				{
					genSyncReceipt((SyncRecord) evt.getProperty("syncRecord"));
				} catch(PrismsRecordException e)
				{
					log.error("Could not generate synchronization receipt", e);
					((UI) theSession.getPlugin("UI"))
						.error("Could not generate synchronization receipt: " + e.getMessage());
				}
			}
		});
		/* This event can be fired periodically to automatically synchronize with configured centers
		 * on a timer */
		session.addEventListener("doAutoSynchronize", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				String tag = "synchronized" + getSynchronizer().hashCode();
				if(evt.getProperty(tag) != null)
					return;
				evt.setProperty(tag, Boolean.TRUE);
				autoSync();
			}
		});
	}

	/**
	 * @return The record keeper that this center editor uses
	 */
	public DBRecordKeeper getRecordKeeper()
	{
		return theRecordKeeper;
	}

	/**
	 * This must be called prior to {@link #initPlugin(PrismsSession, org.dom4j.Element)}
	 * 
	 * @param keeper The record keeper that this center editor is to use
	 */
	public void setRecordKeeper(DBRecordKeeper keeper)
	{
		theRecordKeeper = keeper;
	}

	/**
	 * @see prisms.arch.AppPlugin#initClient()
	 */
	public void initClient()
	{
		theImportStart = 0;
		theExportStart = 0;
		sendCenter(false, false);
	}

	/**
	 * @return The session using this editor
	 */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/**
	 * @return The name of this plugin
	 */
	public String getName()
	{
		return theName;
	}

	/**
	 * @return The center being edited
	 */
	public PrismsCenter getCenter()
	{
		return theCenter;
	}

	void assertEditable()
	{
		if(!isEditable())
		{
			throw new IllegalArgumentException(
				"You do not have permission to edit this rules center");
		}
	}

	/**
	 * @see prisms.arch.AppPlugin#processEvent(org.json.simple.JSONObject)
	 */
	public void processEvent(JSONObject evt)
	{
		if(theCenter == null)
			throw new IllegalStateException("No selected rules center to operate on");

		if("testURL".equals(evt.get("method")))
		{
			testURL();
			return;
		}
		else if("importSyncByFile".equals(evt.get("method")))
		{
			importSyncByFile();
			return;
		}
		else if("exportSyncByFile".equals(evt.get("method")))
		{
			exportSyncByFile();
			return;
		}
		else if("syncNow".equals(evt.get("method")))
		{
			syncNow();
			return;
		}
		else if("showAllImportHistory".equals(evt.get("method")))
		{
			showAllImportHistory();
			return;
		}
		else if("showAllExportHistory".equals(evt.get("method")))
		{
			showAllExportHistory();
			return;
		}
		else if("sortBy".equals(evt.get("method")))
		{
			sortBy((String) evt.get("column"), ((Boolean) evt.get("ascending")).booleanValue(),
				((Boolean) evt.get("isImport")).booleanValue());
			return;
		}
		else if("navigateTo".equals(evt.get("method")))
		{
			navigateTo(((Number) evt.get("start")).intValue(),
				((Boolean) evt.get("isImport")).booleanValue());
			return;
		}
		else if("selectChanged".equals(evt.get("method")))
		{
			selectChanged(((Boolean) evt.get("isImport")).booleanValue(),
				((Number) evt.get("start")).intValue(), ((Number) evt.get("end")).intValue(),
				((Boolean) evt.get("selected")).booleanValue());
			return;
		}
		else if("getRecordResults".equals(evt.get("method")))
		{
			JSONObject linkID = (JSONObject) evt.get("linkID");
			showSyncResults(((Number) linkID.get("id")).intValue());
			return;
		}
		else if("purgeSyncRecords".equals(evt.get("method")))
		{
			purgeSyncRecords(((Boolean) evt.get("isImport")).booleanValue());
			return;
		}

		assertEditable();
		String status = null;
		boolean isError = false;
		if("setName".equals(evt.get("method")))
		{
			String newName = (String) evt.get("name");
			if(newName.length() > theNameLength)
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("sendStatusError",
					"message", "Rules center name must be less than " + (theNameLength + 1)
						+ " characters"));
				isError = true;
			}
			else
			{
				status = "Name of rules center \"" + theCenter.getName() + "\" changed to \""
					+ newName + "\"";
				theCenter.setName(newName);
			}
		}
		else if("setURL".equals(evt.get("method")))
		{
			String newURL = (String) evt.get("url");
			if(newURL.length() > theUrlLength)
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("sendStatusError",
					"message", "Rules center URL must be less than " + (theUrlLength + 1)
						+ " characters"));
				isError = true;
			}
			else
			{
				status = "URL of rules center \"" + theCenter.getName() + "\" changed to \""
					+ newURL + "\"";
				if(newURL.length() == 0)
					theCenter.setServerURL(null);
				else
					theCenter.setServerURL(newURL);
			}
		}
		else if("setServerUser".equals(evt.get("method")))
		{
			String newName = (String) evt.get("userName");
			if(newName.length() > theUserNameLength)
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("sendStatusError",
					"message", "Rules center server user name must be less than "
						+ (theUserNameLength + 1) + " characters"));
				isError = true;
			}
			else
			{
				status = "Server user name of rules center \"" + theCenter.getName()
					+ "\" changed to \"" + newName + "\"";
				if(newName.length() == 0)
					theCenter.setServerUserName(null);
				else
					theCenter.setServerUserName(newName);
			}
		}
		else if("setServerPassword".equals(evt.get("method")))
		{
			String newPassword = (String) evt.get("password");
			newPassword = xorEnc(newPassword, 93);
			if(newPassword.length() > thePasswordLength)
			{
				theSession.fireEvent(new prisms.arch.event.PrismsEvent("sendStatusError",
					"message", "Rules center password must be less than " + (thePasswordLength + 1)
						+ " characters"));
				isError = true;
			}
			else
			{
				status = "Password of rules center \"" + theCenter.getName() + "\" changed to \"";
				for(int c = 0; c < newPassword.length(); c++)
					status += '*';
				status += "\"";
				if(newPassword.length() == 0)
					theCenter.setServerPassword(null);
				else
					theCenter.setServerPassword(newPassword);
			}
		}
		else if("setSyncFrequency".equals(evt.get("method")))
		{
			long freq = ((Number) ((JSONObject) evt.get("freq")).get("seconds")).longValue() * 1000;
			status = "Server synchronization frequency of rules center \"" + theCenter.getName()
				+ "\" changed to " + prisms.util.PrismsUtils.printTimeLength(freq);
			theCenter.setServerSyncFrequency(freq);
		}
		else if("setClientUser".equals(evt.get("method")))
		{
			String userName = (String) evt.get("user");
			theCenter.setClientUser(null);
			if(!userName.equals("No User"))
			{
				theCenter.setClientUser(null);
				for(RecordUser user : getUsers())
					if(Record2Utils.getCenterID(user.getID()) == theRecordKeeper.getCenterID()
						&& user.getName().equals(userName))
					{
						theCenter.setClientUser(user);
						break;
					}
				if(theCenter.getClientUser() == null)
				{
					isError = true;
					status = "No such user: " + userName;
				}
				else
					status = "Set client user of rule center "
						+ theCenter
						+ " to "
						+ (theCenter.getClientUser() == null ? "none" : theCenter.getClientUser()
							.getName());
			}
			else
				status = "Set client user to none";
		}
		// else if("setModificationSaveTime".equals(evt.get("method")))
		// {
		// long saveTime = ((Number) ((JSONObject) evt.get("saveTime")).get("seconds"))
		// .longValue() * 1000;
		// status = "Client modification save time for rules center \"" + theCenter.getName()
		// + "\" changed to " + prisms.util.PrismsUtils.printTimeLength(saveTime);
		// theCenter.setChangeSaveTime(saveTime);
		// }
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event " + evt);

		if(!isError)
		{
			theSession.fireEvent(new prisms.arch.event.PrismsEvent("centerChanged", "center",
				theCenter));
			theSession.fireEvent(new prisms.arch.event.PrismsEvent("sendStatusUpdate", "message",
				status));
		}
		else
			theSession.fireEvent(new prisms.arch.event.PrismsEvent("sendStatusError", "message",
				status));
	}

	/**
	 * Sends the display information for this plugin to the client
	 * 
	 * @param show Whether this method should select its client editor's tab
	 * @param refresh Whether to refresh the synchronization records for the center
	 */
	protected void sendCenter(boolean show, boolean refresh)
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setEnabled");
		evt.put("enabled", new Boolean(isEditable()));
		evt.put("purgeEnabled", new Boolean(canPurge()));
		theSession.postOutgoingEvent(evt);

		RecordUser [] users = prisms.util.ArrayUtils.adjust(getUsers(), getCenters(),
			new prisms.util.ArrayUtils.DifferenceListener<RecordUser, PrismsCenter>()
			{
				public boolean identity(RecordUser o1, PrismsCenter o2)
				{
					return o1.equals(o2.getClientUser());
				}

				public RecordUser added(PrismsCenter o, int idx, int retIdx)
				{
					return null;
				}

				public RecordUser removed(RecordUser o, int idx, int incMod, int retIdx)
				{
					return o;
				}

				public RecordUser set(RecordUser o1, int idx1, int incMod, PrismsCenter o2,
					int idx2, int retIdx)
				{
					if(o2 == theCenter)
						return o1;
					else
						return null;
				}
			});
		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setUsers");
		org.json.simple.JSONArray jsonUsers = new org.json.simple.JSONArray();
		for(int u = 0; u < users.length; u++)
			if(theRecordKeeper.getCenterID() == Record2Utils.getCenterID(users[u].getID()))
				jsonUsers.add(users[u].getName());
		evt.put("users", jsonUsers);
		theSession.postOutgoingEvent(evt);

		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setCenter");
		evt.put("show", new Boolean(show));
		JSONObject center = null;
		if(theCenter != null)
		{
			center = new JSONObject();
			center.put("name", theCenter.getName());
			center.put("url", theCenter.getServerURL());
			center.put("serverUserName", theCenter.getServerUserName());
			center.put("syncFrequency", new Integer(
				(int) (theCenter.getServerSyncFrequency() / 1000)));
			if(theCenter.getClientUser() != null)
				center.put("clientUser", theCenter.getClientUser().getName());
			// center.put("modificationSaveTime", new Integer(
			// (int) (theCenter.getChangeSaveTime() / 1000)));
		}
		evt.put("center", center);
		theSession.postOutgoingEvent(evt);
		sendSyncRecords(refresh);
	}

	private static class RecordHolder
	{
		SyncRecord theRecord;

		int theResultCount;

		boolean selected;

		RecordHolder(SyncRecord record)
		{
			theRecord = record;
		}

		public boolean equals(Object o)
		{
			if(!(o instanceof RecordHolder))
				return false;
			return ((RecordHolder) o).theRecord.equals(theRecord);
		}
	}

	java.util.List<RecordHolder> theImportRecords;

	java.util.List<RecordHolder> theExportRecords;

	void sendSyncRecords(boolean refresh)
	{
		if(refresh || theImportRecords == null || theExportRecords == null)
		{
			SyncRecord [] allRecords;
			if(theCenter == null)
				allRecords = new SyncRecord [0];
			else
			{
				try
				{
					allRecords = theRecordKeeper.getSyncRecords(theCenter, null);
				} catch(prisms.records2.PrismsRecordException e)
				{
					throw new IllegalStateException(
						"Could not get synchronization records for center " + theCenter, e);
				}
			}
			if(theImportRecords == null)
				theImportRecords = new java.util.ArrayList<RecordHolder>();
			else
				theImportRecords.clear();
			if(theExportRecords == null)
				theExportRecords = new java.util.ArrayList<RecordHolder>();
			else
				theExportRecords.clear();
			for(SyncRecord record : allRecords)
			{
				RecordHolder holder = new RecordHolder(record);
				if(record.isImport())
					theImportRecords.add(holder);
				else
					theExportRecords.add(holder);
				if(record.getSyncError() == null)
				{
					long [] modIDs;
					try
					{
						modIDs = theRecordKeeper.getSyncChanges(record);
						holder.theResultCount = modIDs.length;
					} catch(prisms.records2.PrismsRecordException e)
					{
						log.error("Could not get modifications for sync record " + record, e);
						holder.theResultCount = -1;
					}
				}
			}
		}
		sort(theImportRecords, true);
		sort(theExportRecords, false);

		prisms.ui.SortTableStructure importTable = new prisms.ui.SortTableStructure(3);
		importTable.setColumn(0, "Type", true);
		importTable.setColumn(1, "Time", true);
		importTable.setColumn(2, "Results", true);
		prisms.ui.SortTableStructure exportTable = new prisms.ui.SortTableStructure(3);
		exportTable.setColumn(0, "Type", true);
		exportTable.setColumn(1, "Time", true);
		exportTable.setColumn(2, "Results", true);
		int realImportCount = theImportRecords.size() - theImportStart;
		if(realImportCount > theRecordCount)
			realImportCount = theRecordCount;
		if(realImportCount < 0)
			realImportCount = 0;
		importTable.setRowCount(realImportCount);
		int realExportCount = theExportRecords.size() - theExportStart;
		if(realExportCount > theRecordCount)
			realExportCount = theRecordCount;
		if(realExportCount < 0)
			realExportCount = 0;
		exportTable.setRowCount(realExportCount);

		for(int r = 0; r < realImportCount; r++)
			setRecord(importTable.row(r), theImportRecords.get(theImportStart + r));
		for(int r = 0; r < realExportCount; r++)
			setRecord(exportTable.row(r), theExportRecords.get(theExportStart + r));
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setSyncRecords");
		evt.put("importRecords", importTable.serialize(theImportStart + 1, theImportStart
			+ realImportCount, theRecordCount, theImportRecords.size()));
		evt.put("exportRecords", exportTable.serialize(theExportStart + 1, theExportStart
			+ realExportCount, theRecordCount, theExportRecords.size()));
		theSession.postOutgoingEvent(evt);
	}

	void testURL()
	{
		UI ui = (UI) theSession.getPlugin("UI");
		if(ui == null)
			throw new IllegalStateException("No UI to use to test URL");
		UI.DefaultProgressInformer pi = new UI.DefaultProgressInformer();
		ui.startTimedTask(pi);
		int [] items;
		try
		{
			items = new SyncServiceClient(getSynchronizer(), theAppName, theClientName,
				theServicePluginName).checkSync(theCenter, pi, requiresRecords());
		} catch(prisms.records2.PrismsRecordException e)
		{
			log.error("URL test failed", e);
			((UI) theSession.getPlugin("UI")).info(e.getMessage());
			return;
		} finally
		{
			pi.setDone();
		}
		if(items[0] == 0 && items[1] == 0)
			ui.info("Synchronization is up-to-date!");
		else if(items[0] == 0)
		{
			if(items[1] == 1)
				ui.info("1 modification to synchronize");
			else
				ui.info(items[1] + " modifications to synchronize");
		}
		else if(items[1] == 0)
		{
			if(items[0] == 1)
				ui.info("1 item to synchronize");
			else
				ui.info(items[0] + " items to synchronize");
		}
		else
		{
			if(items[0] == 1)
			{
				if(items[1] == 1)
					ui.info("1 item and 1 modification to synchronize");
				else
					ui.info("1 item and " + items[1] + " modifications to synchronize");
			}
			else
			{
				if(items[1] == 1)
					ui.info(items[0] + " items and 1 modification to synchronize");
				else
					ui.info(items[0] + " items and " + items[1] + " modifications to synchronize");
			}
		}
	}

	void importSyncByFile()
	{
		String msg = "To synchronize with " + theCenter + " via file transfer:\n";
		msg += "\t1)  Generate a synchronization request file here.\n\t\tSend this to an admin on "
			+ theCenter + " via email or other method.\n";
		msg += "\t2)  An admin on " + theCenter + " must select this center in their REA v3"
			+ " and click\n\t\t \"Export File...\" on the right side.\n";
		msg += "\t3)  The admin must select \"Generate Synchronization File\" and\n\t\tupload the"
			+ " request file you sent them.\n";
		msg += "\t4)  The admin must send you the file that is downloaded.\n";
		msg += "\t5)  Click \"Import File...\" here and select \"Upload Synchronization File\".\n";
		msg += "\t6)  Select the file the " + theCenter + " admin sent you.\n";
		msg += "\t7)  Send the synchronization receipt that pops up back to the " + theCenter
			+ " admin.\n";
		msg += "\t8)  The admin should select this center and click \"Export File...\" again.\n";
		msg += "\t9) The admin should select \"Upload Synchronization Receipt\" and\n\t\t copy the"
			+ " receipt into the box that pops up and click \"OK\".\n";
		msg += "\nWhat would you like to do?";
		final String [] options = new String [] {"Generate Synchronization Request",
			"Upload Synchronization File", "Generate Synchronization Receipt"};
		((UI) theSession.getPlugin("UI")).select(msg, options, 0, new UI.SelectListener()
		{
			public void selected(String select)
			{
				if(select == null)
					return;
				else if(select.equals(options[0]))
					genSyncRequest();
				else if(select.equals(options[1]))
					uploadSyncData();
				else if(select.equals(options[2]))
					genSyncReceipt();
				else
					throw new IllegalArgumentException("Option not recognized: " + select);
			}
		});
	}

	void exportSyncByFile()
	{
		String msg = "To synchronize " + theCenter + " with this rules center via file transfer:\n";
		msg += "\t1)  An admin on " + theCenter + " must select this center in their REA v3"
			+ ", click\n\t\t\"Import File...\" on the left side, and select"
			+ " \"Generate Synchronization Request\".\n\t\t They must send you the file"
			+ " that is downloaded.\n";
		msg += "\t2)  Select \"Generate Synchronization File\" here and select the file they sent you.\n";
		msg += "\t3)  Send the file that is downloaded to the " + theCenter + " admin.\n";
		msg += "\t4)  The admin must select this center in their REA v3 and click\n"
			+ "\t\t\"Import File...\"on the left and select \"Upload Synchronization File\".\n";
		msg += "\t5)  The admin must select the file you sent them.\n";
		msg += "\t6)  The admin should send the synchronization receipt that pops up back to you.\n";
		msg += "\t7)  Select this center and click \"Export File...\" again.\n";
		msg += "\t8) Select \"Upload Synchronization Receipt\" and copy the receipt into\n\t\t"
			+ " the box that pops up and click \"OK\".\n";
		msg += "\nWhat would you like to do?";
		final String [] options = new String [] {"Generate Synchronization File",
			"Upload Synchronization Receipt"};
		((UI) theSession.getPlugin("UI")).select(msg, options, 0, new UI.SelectListener()
		{
			public void selected(String select)
			{
				if(select == null)
					return;
				else if(select.equals(options[0]))
					downloadSyncData();
				else if(select.equals(options[1]))
					uploadSyncReceipt();
				else
					throw new IllegalArgumentException("Option not recognized: " + select);
			}
		});
	}

	void genSyncRequest()
	{
		if(!isEditable())
			throw new IllegalArgumentException("User " + theSession.getUser() + " does not"
				+ " have permission to generate synchronization data for other rules centers");
		theSession.fireEvent("downloadSyncRequest", "center", theCenter, "withRecords",
			new Boolean(requiresRecords()));
	}

	void downloadSyncData()
	{
		if(!isEditable())
			throw new IllegalArgumentException("User " + theSession.getUser() + " does not"
				+ " have permission to generate synchronization data for other rules centers");
		theSession.fireEvent("downloadSyncData", "center", theCenter);
	}

	void uploadSyncData()
	{
		if(!isEditable())
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to synchronize with other rules centers");
		theSession.fireEvent("uploadSyncData", "center", theCenter);
	}

	void genSyncReceipt()
	{
		if(!isEditable())
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to synchronize with other rules centers");

		SyncRecord latestRecord = null;
		for(RecordHolder holder : theImportRecords)
		{
			if(latestRecord == null || holder.theRecord.getSyncTime() > latestRecord.getSyncTime())
				latestRecord = holder.theRecord;
		}
		if(latestRecord == null)
		{
			((UI) theSession.getPlugin("UI")).error("No synchronizations have been attempted"
				+ "--cannot generate synchronization receipt");
			return;
		}
		try
		{
			genSyncReceipt(latestRecord);
		} catch(PrismsRecordException e)
		{
			log.error("Could not generate synchronization receipt", e);
			((UI) theSession.getPlugin("UI")).error("Could not generate synchronization receipt: "
				+ e.getMessage());
		}
	}

	private static final char [] HEX_CHARS = new char [] {'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

	void genSyncReceipt(SyncRecord record) throws PrismsRecordException
	{
		int errors, success;
		errors = theRecordKeeper.getErrorChanges(record).length;
		success = theRecordKeeper.getSuccessChanges(record).length;
		String message;
		if(record.getSyncError() != null)
			message = record.getSyncError();
		else if(errors == 0)
		{
			if(success == 0)
				message = "Synchronization successful";
			else
				message = "Synchronization successful--" + success + " items synchronized";
		}
		else if(success == 0)
			message = errors + " synchronize errors";
		else
			message = success + " successful changes, " + errors + " errors";

		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		try
		{
			java.io.Writer writer = new java.io.OutputStreamWriter(
				new prisms.util.ExportStream(out));
			prisms.util.json.JsonStreamWriter jsw = new prisms.util.json.JsonStreamWriter(writer);
			jsw.startObject();
			jsw.startProperty("message");
			jsw.writeString(message);
			jsw.startProperty("receipt");
			jsw.writeCustomValue();
			getSynchronizer().sendSyncReceipt(record.getCenter(), writer, record.getParallelID());
			jsw.endObject();
			writer.close();
		} catch(java.io.IOException e)
		{
			throw new PrismsRecordException("IO Exception???!!!", e);
		}
		byte [] receiptBytes = out.toByteArray();
		StringBuilder receiptStr = new StringBuilder();
		for(int b = 0; b < receiptBytes.length; b++)
		{
			int chr = (receiptBytes[b] + 256) % 256;
			receiptStr.append(HEX_CHARS[chr >>> 4]);
			receiptStr.append(HEX_CHARS[chr & 0xf]);
		}
		message = "Send this return receipt to a " + theCenter + " admin and tell them to\n"
			+ "select this center in their REA v3, click \"Export File...\",\n"
			+ "select \"Upload Synchronization Receipt\",\n"
			+ " and paste the receipt into the box that pops up.\n" + message;
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "displaySyncInfo");
		evt.put("message", message);
		evt.put("data", receiptStr.toString());
		theSession.postOutgoingEvent(evt);
	}

	void uploadSyncReceipt()
	{
		if(!isEditable())
			throw new IllegalArgumentException("User " + theSession.getUser() + " does not"
				+ " have permission to generate synchronization data for other rules centers");
		((UI) theSession.getPlugin("UI")).input(
			"Copy the synchronization receipt from the client here", null, new UI.InputListener()
			{
				public void inputed(String input)
				{
					if(input == null)
						return;
					try
					{
						setSyncReceipt(input.trim());
					} catch(PrismsRecordException e)
					{
						log.error("Could not read sync receipt", e);
						((UI) getSession().getPlugin("UI")).error("Could not read sync receipt: "
							+ e.getMessage());
					}
				}
			});
	}

	void setSyncReceipt(String receiptStr) throws PrismsRecordException
	{
		byte [] receiptBytes = new byte [receiptStr.length() / 2];
		for(int i = 0; i < receiptBytes.length; i++)
		{
			int _byte = fromHex(receiptStr.charAt(i * 2));
			_byte = _byte << 4 | fromHex(receiptStr.charAt(i * 2 + 1));
			receiptBytes[i] = (byte) _byte;
		}
		final String [] message = new String [] {null};
		final boolean [] hasReceipt = new boolean [] {false};
		final java.io.Reader reader;
		try
		{
			java.io.Reader tempReader = new java.io.InputStreamReader(new prisms.util.ImportStream(
				new java.io.ByteArrayInputStream(receiptBytes)));
			java.io.StringWriter writer = new java.io.StringWriter();
			int read = tempReader.read();
			while(read >= 0)
			{
				writer.write(read);
				read = tempReader.read();
			}
			String json = writer.toString();
			reader = new java.io.StringReader(json);
			new prisms.util.json.SAJParser().parse(reader,
				new prisms.util.json.SAJParser.DefaultHandler()
				{
					@Override
					public void separator(ParseState state)
					{
						super.separator(state);
						if("receipt".equals(state.top().getPropertyName()))
						{
							try
							{
								state.spoofValue();
								valueNull(state);
								getSynchronizer().readSyncReceipt(reader);
							} catch(IOException e)
							{
								throw new IllegalStateException(
									"Could not parse synchronization receipt", e);
							} catch(ParseException e)
							{
								throw new IllegalStateException(
									"Could not parse synchronization receipt", e);
							} catch(PrismsRecordException e)
							{
								throw new IllegalStateException(
									"Could not read synchronization receipt: " + e.getMessage(), e);
							}
							hasReceipt[0] = true;
						}
					}

					@Override
					public void valueString(ParseState state, String value)
					{
						super.valueString(state, value);
						if("message".equals(state.top().getPropertyName()))
							message[0] = value;
					}
				});
		} catch(IOException e)
		{
			throw new PrismsRecordException("Not a synchronization receipt", e);
		} catch(ParseException e)
		{
			throw new PrismsRecordException("Could not parse synchronization receipt", e);
		} catch(IllegalStateException e)
		{
			if(e.getCause() != null)
				throw new PrismsRecordException(e.getMessage(), e.getCause());
			else
				throw e;
		}
		UI ui = (UI) theSession.getPlugin("UI");
		if(!hasReceipt[0])
			ui.error("Not a synchronization receipt");
		else if(message[0] == null)
			ui.warn("No message included--receipt input successful");
		else
			ui.info(message[0]);
	}

	int fromHex(char hexChar)
	{
		if(hexChar >= '0' && hexChar <= '9')
			return hexChar - '0';
		else if(hexChar >= 'A' && hexChar <= 'F')
			return hexChar - 'A' + 10;
		else
			return hexChar - 'a' + 10;
	}

	void syncNow()
	{
		if(!isEditable())
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to synchronize with other rules centers");
		UI.AppLockProgress pi = new UI.AppLockProgress(getSession().getApp());
		pi.setPostReload(true);
		pi.setProgressText("Initializing synchronization");
		pi.setCancelable(true);

		String error = null;
		try
		{
			SyncServiceClient syncClient = new SyncServiceClient(getSynchronizer(), theAppName,
				theClientName, theServicePluginName);
			syncClient.setSecurityInfo(getTrustStore(), getTrustPassword());
			syncClient.synchronize(theCenter, SyncRecord.Type.MANUAL_REMOTE, pi, requiresRecords(),
				true);
		} catch(prisms.records2.PrismsRecordException e)
		{
			log.error("Manual synchronization failed", e);
			error = e.getMessage();
		} finally
		{
			pi.setDone();
		}
		if(error == null)
			((UI) theSession.getPlugin("UI")).info(pi.getTaskText());
		else
			((UI) theSession.getPlugin("UI")).error(error);
	}

	void showAllImportHistory()
	{
		theSession.fireEvent("showCenterHistory", "center", theCenter, "import", Boolean.TRUE);
	}

	void showAllExportHistory()
	{
		theSession.fireEvent("showCenterHistory", "center", theCenter, "import", Boolean.FALSE);
	}

	enum SyncRecordField implements HistorySorter.Field
	{
		RECORD_TYPE, RECORD_TIME, RECORD_RESULTS;
	}

	SyncRecordField getField(String name)
	{
		if(name.equals("Type"))
			return SyncRecordField.RECORD_TYPE;
		else if(name.equals("Time"))
			return SyncRecordField.RECORD_TIME;
		else if(name.equals("Results"))
			return SyncRecordField.RECORD_RESULTS;
		else
			throw new IllegalArgumentException("Unrecognized sync record field" + name);
	}

	HistorySorter<SyncRecordField> theImportSorter;

	HistorySorter<SyncRecordField> theExportSorter;

	void sortBy(String column, boolean ascending, boolean isImport)
	{
		if(theImportSorter == null)
			theImportSorter = new HistorySorter<SyncRecordField>();
		if(theExportSorter == null)
			theExportSorter = new HistorySorter<SyncRecordField>();
		HistorySorter<SyncRecordField> sorter;
		if(isImport)
			sorter = theImportSorter;
		else
			sorter = theExportSorter;
		sorter.addSort(getField(column), ascending);
		sendSyncRecords(false);
	}

	void sort(java.util.List<RecordHolder> records, boolean isImport)
	{
		if(theImportSorter == null)
			theImportSorter = new HistorySorter<SyncRecordField>();
		if(theExportSorter == null)
			theExportSorter = new HistorySorter<SyncRecordField>();
		final HistorySorter<SyncRecordField> sorter;
		if(isImport)
			sorter = theImportSorter;
		else
			sorter = theExportSorter;
		if(sorter.getSortCount() == 0)
			sorter.addSort(SyncRecordField.RECORD_TIME, false);
		java.util.Collections.sort(records, new java.util.Comparator<RecordHolder>()
		{
			public int compare(RecordHolder rh1, RecordHolder rh2)
			{
				SyncRecord r1 = rh1.theRecord;
				SyncRecord r2 = rh2.theRecord;
				for(int sort = sorter.getSortCount() - 1; sort >= 0; sort--)
				{
					int ret = 0;
					boolean switchHit = false;
					SyncRecordField field = sorter.getField(sort);
					switch(field)
					{
					case RECORD_TYPE:
						switchHit = true;
						ret = r1.getSyncType().ordinal() - r2.getSyncType().ordinal();
						break;
					case RECORD_TIME:
						switchHit = true;
						ret = r1.getSyncTime() > r2.getSyncTime() ? 1 : (r1.getSyncTime() < r2
							.getSyncTime() ? -1 : 0);
						break;
					case RECORD_RESULTS: {
						switchHit = true;
						if(r1.getSyncError() != null)
						{
							if(r2.getSyncError() != null)
							{
								if("?".equals(r1.getSyncError()))
								{ // r1's results are unknown
									if("?".equals(r2.getSyncError()))
										ret = 0; // Both results are unknown
									else
										ret = 1; // r2's results are error
								}
								else
								{ // r1's results are error
									if("?".equals(r2.getSyncError()))
										ret = -1; // r2's results are unknown
									else
										ret = 0; // Both results are error
								}
							}
							else
								ret = -1; // r2's results are not unknown or error
						}
						// r1's results are not unknown or error
						else if(r2.getSyncError() != null)
							ret = 1; // r2's results are unknown or error
						else
							// Both results are quantifiable
							ret = rh1.theResultCount - rh2.theResultCount;
					}
					}
					if(!switchHit)
					{
						log.error("Unrecognized sort field: " + field);
						ret = 0;
					}
					if(ret == 0)
						continue;
					if(!sorter.isAscending(sort))
						ret = -ret;
					return ret;
				}
				return 0;
			}
		});
	}

	void navigateTo(int start, boolean isImport)
	{
		if(isImport)
			theImportStart = start - 1;
		else
			theExportStart = start - 1;
		sendSyncRecords(false);
	}

	void selectChanged(boolean isImport, int start, int end, boolean selected)
	{
		java.util.List<RecordHolder> records;
		if(isImport)
			records = theImportRecords;
		else
			records = theExportRecords;
		for(int i = start - 1; i < end && i < records.size(); i++)
			records.get(i).selected = selected;
	}

	void showSyncResults(int syncID)
	{
		RecordHolder holder = null;
		for(RecordHolder rh : theImportRecords)
		{
			if(rh.theRecord.getID() == syncID)
			{
				holder = rh;
				break;
			}
		}
		if(holder == null)
		{
			for(RecordHolder rh : theExportRecords)
			{
				if(rh.theRecord.getID() == syncID)
				{
					holder = rh;
					break;
				}
			}
		}
		if(holder == null)
			throw new IllegalArgumentException("No such sync record for " + theCenter);
		if(holder.theRecord.getSyncError() == null)
			theSession.fireEvent("showSyncRecordHistory", "syncRecord", holder.theRecord);
		else if(holder.theRecord.getSyncError().equals("?"))
			((UI) theSession.getPlugin("UI")).warn("Synchronize results unknown");
		else
			((UI) theSession.getPlugin("UI")).error("Synchronize failed -- "
				+ holder.theRecord.getSyncError());
	}

	/**
	 * Created by Matthew Shaffer (matt-shaffer.com)
	 * 
	 * This method uses simple xor encryption to encrypt a password with a key so that it is at
	 * least not stored in clear text.
	 * 
	 * @param toEnc The string to encrypt
	 * @param encKey The encryption key
	 * @return The encrypted string
	 */
	private static String xorEnc(String toEnc, int encKey)
	{
		int t = 0;
		String tog = "";
		if(encKey > 0)
		{
			while(t < toEnc.length())
			{
				int a = toEnc.charAt(t);
				int c = a ^ encKey;
				char d = (char) c;
				tog = tog + d;
				t++;
			}
		}
		return tog;
	}

	java.awt.Color unknownColor = new java.awt.Color(160, 160, 0);

	void setRecord(prisms.ui.SortTableStructure.TableRow row, RecordHolder holder)
	{
		SyncRecord record = holder.theRecord;
		row.cell(0).setLabel(record.getSyncType().toString());
		row.cell(1).setLabel(prisms.util.PrismsUtils.print(record.getSyncTime()));
		JSONObject linkID = prisms.util.PrismsUtils.rEventProps("id", new Integer(record.getID()));
		if(record.getSyncError() == null)
		{
			if(holder.theResultCount < 0)
			{
				row.cell(2).setLabel("Could not get results");
				row.cell(2).setFontColor(java.awt.Color.red);
			}
			else if(record.isImport())
				row.cell(2).set(holder.theResultCount + " items retrieved", linkID, null);
			else
				row.cell(2).set(holder.theResultCount + " items sent", linkID, null);
		}
		else if(record.getSyncError().equals("?"))
		{
			row.cell(2).set("Unknown", linkID, null);
			row.cell(2).setFontColor(unknownColor);
		}
		else
		{
			row.cell(2).set("Synchronization Failure", linkID, null);
			row.cell(2).setFontColor(java.awt.Color.red);
		}
		row.setSelected(holder.selected);
	}

	void purgeSyncRecords(boolean server)
	{
		if(!canPurge())
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to purge synchronization records");
		final java.util.List<RecordHolder> records;
		if(server)
			records = theImportRecords;
		else
			records = theExportRecords;
		long maxTime = 0;
		for(int i = 0; i < records.size(); i++)
			if(records.get(i).theRecord.getSyncError() == null
				&& records.get(i).theRecord.getSyncTime() > maxTime)
				maxTime = records.get(i).theRecord.getSyncTime();
		RecordHolder [] toPurge = new RecordHolder [0];
		String error = null;
		for(int i = 0; i < records.size(); i++)
		{
			if(records.get(i).selected)
			{
				if(records.get(i).theRecord.getSyncTime() == maxTime)
				{
					error = "The latest successful synchronization record cannot be purged";
					continue;
				}
				toPurge = prisms.util.ArrayUtils.add(toPurge, records.get(i));
			}
		}
		UI ui = (UI) theSession.getPlugin("UI");
		if(error != null)
			ui.error(error);
		if(toPurge.length == 0 && error == null)
			ui.info("No " + (server ? "import" : "export") + " records selected to purge");
		if(toPurge.length == 0)
			return;
		UI.ConfirmListener cl = new UI.ConfirmListener()
		{
			public void confirmed(boolean confirm)
			{
				if(!confirm)
					return;
				dataLock = true;
				try
				{
					java.util.Iterator<RecordHolder> iter = records.iterator();
					while(iter.hasNext())
					{
						RecordHolder holder = iter.next();
						if(!holder.selected)
							continue;
						theSession.fireEvent("syncPurged", "record", holder.theRecord);
						iter.remove();
					}
				} finally
				{
					dataLock = false;
				}
				sendSyncRecords(false);
			}
		};
		if(toPurge.length == 1)
			ui.confirm("Are you sure you want to purge the record of " + toPurge[0] + "?", cl);
		else
			ui.confirm("Are you sure you want to purge these " + toPurge.length
				+ " synchronization records?", cl);
	}

	void autoSync()
	{
		prisms.records2.PrismsSynchronizer2 sync = getSynchronizer();
		long time = System.currentTimeMillis();
		PrismsCenter [] centers;
		try
		{
			centers = sync.getKeeper().getCenters();
		} catch(PrismsRecordException e)
		{
			throw new IllegalStateException("Could not get centers", e);
		}
		PrismsCenter [] toSync = new PrismsCenter [0];
		for(int rc = 0; rc < centers.length; rc++)
		{
			long syncFreq = centers[rc].getServerSyncFrequency();
			if(syncFreq <= 0)
				continue;
			long lastSync = centers[rc].getLastImport();
			if(time - lastSync >= syncFreq)
				toSync = prisms.util.ArrayUtils.add(toSync, centers[rc]);
		}
		if(toSync.length == 0)
			return;
		for(PrismsCenter center : toSync)
		{
			SyncServiceClient syncClient = new SyncServiceClient(getSynchronizer(), theAppName,
				theClientName, theServicePluginName);
			syncClient.setSecurityInfo(getTrustStore(), getTrustPassword());
			try
			{
				syncClient.synchronize(center, SyncRecord.Type.AUTOMATIC, null, requiresRecords(),
					true);
			} catch(prisms.records2.PrismsRecordException e)
			{
				log.error("Automatic synchronization failed", e);
			}
		}
	}

	/** @return The synchronizer that this editor is to use for manual synchronization */
	protected abstract PrismsSynchronizer2 getSynchronizer();

	/** @return All users available to connect to a center */
	protected abstract RecordUser [] getUsers();

	/** @return All centers available for synchronization */
	protected abstract PrismsCenter [] getCenters();

	/** @return Whether the current session has authority to edit the selected center */
	protected abstract boolean isEditable();

	/** @return Whether the current session has authority to purge synchronization records */
	protected abstract boolean canPurge();

	/**
	 * @return Whether accurate record-keeping is more important to this local center than speedy
	 *         synchronization
	 */
	protected abstract boolean requiresRecords();

	/**
	 * Gets the location of the trust store to use for connecting to the selected center. Should be
	 * overridden by subclasses in environments where secure connections will be needed.
	 * 
	 * @return The location of the trust store
	 */
	protected String getTrustStore()
	{
		return null;
	}

	/**
	 * Gets the password to the trust store to use for connecting to the selected center. Should be
	 * overridden by subclasses in environments where secure connections will be needed.
	 * 
	 * @return The password to use to access the trust store
	 */
	protected String getTrustPassword()
	{
		return null;
	}
}
