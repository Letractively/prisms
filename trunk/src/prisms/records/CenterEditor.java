/*
 * CenterEditor2.java Created May 5, 2009 by Andrew Butler, PSL
 */
package prisms.records;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;
import prisms.arch.event.PrismsProperty;
import prisms.ui.UI;
import prisms.util.Sorter;
import prisms.util.json.SAJParser.ParseException;
import prisms.util.json.SAJParser.ParseState;

/** Allows the user to edit a PRISMS center */
public abstract class CenterEditor implements prisms.arch.AppPlugin
{
	static final Logger log = Logger.getLogger(CenterEditor.class);

	PrismsSession theSession;

	private String theName;

	DBRecordKeeper theRecordKeeper;

	PrismsCenter theCenter;

	java.security.cert.X509Certificate[] theServerCerts;

	private Boolean syncAfterCerts;

	PrismsSynchronizer theSynchronizer;

	SyncServiceClient theSyncClient;

	prisms.arch.event.PrismsProperty<? extends RecordUser []> theUserProp;

	prisms.arch.event.PrismsProperty<PrismsCenter []> theCenterProp;

	prisms.util.preferences.Preference<Integer> theCountPref;

	private int theNameLength;

	private int theUrlLength;

	private int theUserNameLength;

	private int thePasswordLength;

	int theImportStart;

	int theExportStart;

	int theRecordCount;

	boolean dataLock;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
		theName = config.get("name");
		String syncPropName = config.get("synchronizer");
		if(syncPropName != null)
		{
			PrismsProperty<PrismsSynchronizer> syncProp = PrismsProperty.get(syncPropName,
				PrismsSynchronizer.class);
			theSynchronizer = session.getProperty(syncProp);
		}
		String syncClientPropName = config.get("sync-client");
		if(syncClientPropName != null)
		{
			PrismsProperty<SyncServiceClient> syncClientProp = PrismsProperty.get(
				syncClientPropName, SyncServiceClient.class);
			theSyncClient = session.getProperty(syncClientProp);
			if(theSyncClient != null)
			{
				if(theSynchronizer == null)
					theSynchronizer = theSyncClient.getSynchronizer();
				else if(!theSynchronizer.equals(theSyncClient.getSynchronizer()))
					throw new IllegalStateException("Sync client is for a different synchronizer");
			}
		}
		String userPropName = config.get("users");
		if(userPropName != null)
			theUserProp = PrismsProperty.get(userPropName, RecordUser [].class);
		String centerPropName = config.get("centers");
		if(centerPropName != null)
			theCenterProp = PrismsProperty.get(centerPropName, PrismsCenter [].class);
		theCountPref = new prisms.util.preferences.Preference<Integer>(theName,
			"Displayed Records", prisms.util.preferences.Preference.Type.NONEG_INT, Integer.class,
			true);
		theCountPref.setDescription("The number of import/export records to display"
			+ " for the selected center");
		session.addEventListener("preferencesChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(!(evt instanceof prisms.util.preferences.PreferenceEvent))
					return;
				prisms.util.preferences.PreferenceEvent pEvt = (prisms.util.preferences.PreferenceEvent) evt;
				if(!pEvt.getPreference().equals(theCountPref))
					return;
				theImportStart = 1;
				theExportStart = 1;
				theRecordCount = ((Integer) pEvt.getNewValue()).intValue();
				sendSyncRecords(false);
			}

			@Override
			public String toString()
			{
				return getSession().getApp().getName() + " Center Editor Preference Applier";
			}
		});
		prisms.util.preferences.Preferences prefs = theSession.getPreferences();
		if(prefs.get(theCountPref) == null)
			prefs.set(theCountPref, Integer.valueOf(10));
		theRecordCount = prefs.get(theCountPref).intValue();
		if(theRecordKeeper != null)
		{
			try
			{
				theNameLength = theRecordKeeper.getFieldSize("prisms_center_view", "name");
				theUrlLength = theRecordKeeper.getFieldSize("prisms_center_view", "url");
				theUserNameLength = theRecordKeeper.getFieldSize("prisms_center_view",
					"serverUserName");
				thePasswordLength = theRecordKeeper.getFieldSize("prisms_center_view",
					"serverPassword");
			} catch(PrismsRecordException e)
			{
				// log.error("Could not get field length", e);
				theNameLength = 64;
				theUrlLength = 256;
				theUserNameLength = 32;
				thePasswordLength = 32;
			}
		}
		String selCenterProp = config.get("selectedCenterProperty");
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
					theServerCerts = null;
					theCenter = evt.getNewValue();
					sendCenter(true, true);
				}

				@Override
				public String toString()
				{
					return getSession().getApp().getName() + " Center Editor Content Updater";
				}
			});
		session.addEventListener("centerChanged", new prisms.arch.event.PrismsEventListener()
		{
			public void eventOccurred(PrismsSession session2, prisms.arch.event.PrismsEvent evt)
			{
				if(evt.getProperty("center").equals(theCenter) && !dataLock)
				{
					theServerCerts = null;
					sendCenter(false, false);
				}
			}

			@Override
			public String toString()
			{
				return getSession().getApp().getName() + " Center Editor Content Changer";
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
					prisms.util.Search search = new ChangeSearch.SyncRecordSearch(
						Integer.valueOf(record.getID()));
					try
					{
						modIDs = theRecordKeeper.search(search, null);
						holder.theResultCount = modIDs.length;
					} catch(prisms.records.PrismsRecordException e)
					{
						log.error("Could not get modifications for sync record " + record, e);
						holder.theResultCount = -1;
					}
				}
				sendSyncRecords(false);
			}

			@Override
			public String toString()
			{
				return getSession().getApp().getName() + " Center Editor Sync Record Updater";
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

			@Override
			public String toString()
			{
				return getSession().getApp().getName() + " Center Editor Sync Record Remover";
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
					theSession.getUI().error(
						"Could not generate synchronization receipt: " + e.getMessage());
				}
			}

			@Override
			public String toString()
			{
				return getSession().getApp().getName() + " Center Editor Sync Receipt Requester";
			}
		});
	}

	/** @return The record keeper that this center editor uses */
	public DBRecordKeeper getRecordKeeper()
	{
		return theRecordKeeper;
	}

	/**
	 * This must be called prior to {@link #initPlugin(PrismsSession, prisms.arch.PrismsConfig)}
	 * 
	 * @param keeper The record keeper that this center editor is to use
	 */
	public void setRecordKeeper(DBRecordKeeper keeper)
	{
		theRecordKeeper = keeper;
	}

	public void initClient()
	{
		theImportStart = 0;
		theExportStart = 0;
		sendCenter(false, false);
		if(theServerCerts != null)
			sendCerts();
	}

	/** @return The session using this editor */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/** @return The name of this plugin */
	public String getName()
	{
		return theName;
	}

	/** @return The center being edited */
	public PrismsCenter getCenter()
	{
		return theCenter;
	}

	void assertEditable()
	{
		if(!isEditable())
			throw new IllegalArgumentException(
				"You do not have permission to edit this data center");
	}

	public void processEvent(JSONObject evt)
	{
		if(theCenter == null)
			throw new IllegalStateException("No selected data center to operate on");

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
				theSession.getStatus().sendStatusError(
					"Data center name must be less than " + (theNameLength + 1) + " characters");
				isError = true;
			}
			else
			{
				status = "Name of data center \"" + theCenter.getName() + "\" changed to \""
					+ newName + "\"";
				theCenter.setName(newName);
			}
		}
		else if("setURL".equals(evt.get("method")))
		{
			String newURL = (String) evt.get("url");
			if(newURL.length() > theUrlLength)
			{
				theSession.getStatus().sendStatusError(
					"Data center URL must be less than " + (theUrlLength + 1) + " characters");
				isError = true;
			}
			else
			{
				status = "URL of data center \"" + theCenter.getName() + "\" changed to \""
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
				theSession.getStatus().sendStatusError(
					"Data center server user name must be less than " + (theUserNameLength + 1)
						+ " characters");
				isError = true;
			}
			else
			{
				status = "Server user name of data center \"" + theCenter.getName()
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
				theSession.getStatus().sendStatusError(
					"Data center password must be less than " + (thePasswordLength + 1)
						+ " characters");
				isError = true;
			}
			else
			{
				StringBuilder st = new StringBuilder();
				st.append("Password of data center \"");
				st.append(theCenter.getName());
				st.append("\" changed to \"");
				for(int c = 0; c < newPassword.length(); c++)
					st.append('*');
				st.append('\"');
				if(newPassword.length() == 0)
					theCenter.setServerPassword(null);
				else
					theCenter.setServerPassword(newPassword);
				status = st.toString();
			}
		}
		else if("setSyncFrequency".equals(evt.get("method")))
		{
			long freq = ((Number) ((JSONObject) evt.get("freq")).get("seconds")).longValue() * 1000;
			status = "Server synchronization frequency of data center \"" + theCenter.getName()
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
					if(RecordUtils.getCenterID(user.getID()) == theRecordKeeper.getCenterID()
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
		else if("viewCertificate".equals(evt.get("method")))
		{
			if(theCenter.getCertificates() == null || theCenter.getCertificates().length == 0)
			{
				getSession().getUI().confirm(
					"No server certificates stored for center " + theCenter
						+ ". Would you like to retrieve them now?", new UI.ConfirmListener()
					{
						public void confirmed(boolean confirm)
						{
							if(!confirm)
								return;
							UI.DefaultProgressInformer pi = new UI.DefaultProgressInformer();
							pi.setCancelable(false);
							try
							{
								pi.setProgressText("Contacting server "
									+ new java.net.URL(theCenter.getServerURL()).getHost()
									+ " for certificates");
							} catch(IOException e)
							{
								getSession().getUI().error(
									"Could not parse URL " + theCenter.getServerURL());
								return;
							}
							getSession().getUI().startTimedTask(pi);
							java.security.cert.X509Certificate[] certs;
							try
							{
								certs = theSyncClient.getCertificates(theCenter);
								if(certs != null)
									promptCerts(certs, null);
								else
									getSession().getUI().info(
										"The server did not provide security certificates");
							} catch(PrismsRecordException e2)
							{
								log.error("Certificate retrieval failed", e2);
								getSession().getUI().info(
									"Could not contact center " + theCenter + ": "
										+ e2.getMessage());
							} finally
							{
								pi.setDone();
							}
							return;
						}
					});
			}
			else
			{
				JSONObject toPost = new JSONObject();
				toPost.put("plugin", getName());
				toPost.put("method", "showCertificate");
				toPost.put("certificate", prisms.ui.CertificateSerializer.serialize(
					theCenter.getCertificates(), theCenter.getServerURL()));
				getSession().postOutgoingEvent(toPost);
			}
		}
		else if("clearCertificate".equals(evt.get("method")))
		{
			getSession().getUI().confirm(
				"Are you sure you want to clear the server certificate?"
					+ "\nSynchronization on this center may not work until the server"
					+ " certificate is manually accepted again.", new UI.ConfirmListener()
				{
					public void confirmed(boolean confirm)
					{
						if(!confirm)
							return;
						theCenter.setCertificates(null);
						getSession().fireEvent("centerChanged", "center", theCenter);
					}
				});
		}
		else if("acceptCertificate".equals(evt.get("method")))
		{
			boolean accept = ((Boolean) evt.get("accepted")).booleanValue();
			if(accept)
			{
				theCenter.setCertificates(theServerCerts);
				theServerCerts = null;
				dataLock = true;
				try
				{
					getSession().fireEvent("centerChanged", "center", theCenter);
				} finally
				{
					dataLock = false;
				}
				if(syncAfterCerts == null)
				{}
				else if(syncAfterCerts.booleanValue())
					syncNow();
				else
					testURL();
			}
			else
				theServerCerts = null;
		}
		else
			throw new IllegalArgumentException("Unrecognized " + theName + " event " + evt);

		if(!isError && status != null)
		{
			theSession.fireEvent("centerChanged", "center", theCenter);
			theSession.getStatus().sendStatusUpdate(status);
		}
		else if(status != null)
			theSession.getStatus().sendStatusError(status);
	}

	/**
	 * Sends the display information for this plugin to the client
	 * 
	 * @param show Whether this method should select its client editor's tab
	 * @param refresh Whether to refresh the synchronization records for the center
	 */
	protected void sendCenter(boolean show, boolean refresh)
	{
		if(theRecordKeeper == null)
		{
			JSONObject evt = new JSONObject();
			evt.put("plugin", theName);
			evt.put("method", "hide");
			theSession.postOutgoingEvent(evt);
			return;
		}
		JSONObject evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setEnabled");
		evt.put("enabled", Boolean.valueOf(isEditable()));
		evt.put("purgeEnabled", Boolean.valueOf(canPurge()));
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
			if(theRecordKeeper.getCenterID() == RecordUtils.getCenterID(users[u].getID()))
				jsonUsers.add(users[u].getName());
		evt.put("users", jsonUsers);
		theSession.postOutgoingEvent(evt);

		evt = new JSONObject();
		evt.put("plugin", theName);
		evt.put("method", "setCenter");
		evt.put("show", Boolean.valueOf(show));
		JSONObject center = null;
		if(theCenter != null)
		{
			center = new JSONObject();
			center.put("name", theCenter.getName());
			center.put("url", theCenter.getServerURL());
			center.put("serverUserName", theCenter.getServerUserName());
			center.put("syncFrequency",
				Integer.valueOf((int) (theCenter.getServerSyncFrequency() / 1000)));
			if(theCenter.getClientUser() != null)
				center.put("clientUser", theCenter.getClientUser().getName());
			prisms.records.RecordUtils.CenterStatus status;
			try
			{
				status = RecordUtils.getCenterStatus(theCenter, theRecordKeeper);
			} catch(PrismsRecordException e)
			{
				log.error("Could not get status of center " + theCenter, e);
				status = null;
			}
			if(status != null)
			{
				center.put("quality", status.quality.name());
				center.put("status", status.message);
			}
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

		@Override
		public int hashCode()
		{
			assert false : "hashCode not designed";
			return 42; // any arbitrary constant will do
		}

		@Override
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
				} catch(prisms.records.PrismsRecordException e)
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
				prisms.util.Search search = new ChangeSearch.SyncRecordSearch(
					Integer.valueOf(record.getID()));
				if(record.getSyncError() == null)
				{
					long [] modIDs;
					try
					{
						modIDs = theRecordKeeper.search(search, null);
						holder.theResultCount = modIDs.length;
					} catch(prisms.records.PrismsRecordException e)
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
		UI ui = getSession().getUI();
		UI.DefaultProgressInformer pi = new UI.DefaultProgressInformer();
		pi.setProgressText("Connecting to data center " + theCenter);
		ui.startTimedTask(pi);
		int [] items;
		if(theSyncClient == null)
		{
			ui.error("No synchronizer--cannot synchronize");
			return;
		}
		try
		{
			items = theSyncClient.checkSync(theCenter, pi);
		} catch(prisms.records.PrismsRecordException e)
		{
			if(e.getCause() != null
				&& e.getCause().getMessage()
					.contains(java.security.cert.CertificateException.class.getName()))
			{
				// Certificate error--grab the certs
				java.security.cert.X509Certificate[] certs;
				try
				{
					certs = theSyncClient.getCertificates(theCenter);
				} catch(PrismsRecordException e2)
				{
					certs = null;
					log.error("Certificate retrieval failed", e2);
					ui.info("Could not contact center " + theCenter + ": " + e2.getMessage());
				}
				if(certs != null)
					promptCerts(certs, Boolean.FALSE);
				else
				{
					log.error("Could not get certificate information", e);
					ui.info("Could not contact center " + theCenter + ": SSL authentication failed");
				}
				return;
			}
			log.error("URL test failed", e);
			ui.info(e.getMessage());
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
		try
		{
			if(RecordUtils.areDependentsSetUp(theSyncClient.getSynchronizer(), theCenter) != null)
				postIDSet(theSyncClient.getSynchronizer(), theCenter);
		} catch(PrismsRecordException e)
		{
			log.error("Could not generate dependent centers", e);
		}
	}

	void promptCerts(java.security.cert.X509Certificate[] certs, Boolean forSync)
	{
		theServerCerts = certs;
		syncAfterCerts = forSync;
		sendCerts();
	}

	void sendCerts()
	{
		JSONObject evt = new JSONObject();
		evt.put("plugin", getName());
		evt.put("method", "checkCertificate");
		evt.put("newCert",
			prisms.ui.CertificateSerializer.serialize(theServerCerts, theCenter.getServerURL()));
		if(theCenter.getCertificates() != null && theCenter.getCertificates().length > 0)
			evt.put(
				"oldCert",
				prisms.ui.CertificateSerializer.serialize(theCenter.getCertificates(),
					theCenter.getServerURL()));
		getSession().postOutgoingEvent(evt);
	}

	void importSyncByFile()
	{
		if(theSynchronizer == null)
			getSession().getUI().error("No synchronizer--cannot synchronize");
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
		getSession().getUI().select(msg, options, 0, new UI.SelectListener()
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
		if(theSynchronizer == null)
			getSession().getUI().error("No synchronizer--cannot synchronize");
		String msg = "To synchronize " + theCenter + " with this data center via file transfer:\n";
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
		getSession().getUI().select(msg, options, 0, new UI.SelectListener()
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
				+ " have permission to generate synchronization data for other data centers");
		theSession.fireEvent("downloadSyncRequest", "center", theCenter, "withRecords",
			Boolean.valueOf(getSyncClient(theCenter).requiresRecords()));
	}

	void downloadSyncData()
	{
		if(!isEditable())
			throw new IllegalArgumentException("User " + theSession.getUser() + " does not"
				+ " have permission to generate synchronization data for other data centers");
		theSession.fireEvent("downloadSyncData", "center", theCenter, "pids",
			new CenterEditorPIDS());
	}

	void uploadSyncData()
	{
		if(!isEditable())
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to synchronize with other data centers");
		theSession.fireEvent("uploadSyncData", "center", theCenter, "pids", new CenterEditorPIDS());
	}

	void genSyncReceipt()
	{
		if(theRecordKeeper == null)
			getSession().getUI().error("No record keeper--cannot generate synchronization receipt");
		if(!isEditable())
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to synchronize with other data centers");

		SyncRecord latestRecord = null;
		for(RecordHolder holder : theImportRecords)
		{
			if(latestRecord == null || holder.theRecord.getSyncTime() > latestRecord.getSyncTime())
				latestRecord = holder.theRecord;
		}
		if(latestRecord == null)
		{
			getSession().getUI().error(
				"No synchronizations have been attempted"
					+ "--cannot generate synchronization receipt");
			return;
		}
		try
		{
			genSyncReceipt(latestRecord);
		} catch(PrismsRecordException e)
		{
			log.error("Could not generate synchronization receipt", e);
			getSession().getUI().error(
				"Could not generate synchronization receipt: " + e.getMessage());
		}
	}

	private static final char [] HEX_CHARS = new char [] {'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

	void genSyncReceipt(SyncRecord record) throws PrismsRecordException
	{
		prisms.util.Search search = new ChangeSearch.SyncRecordSearch(Integer.valueOf(record
			.getID()));
		int errors, success;
		errors = theRecordKeeper.search(
			search.and(ChangeSearch.SyncRecordSearch.forChangeError(Boolean.TRUE)), null).length;
		success = theRecordKeeper.search(
			search.and(ChangeSearch.SyncRecordSearch.forChangeError(Boolean.FALSE)), null).length;
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
		if(theSynchronizer == null)
			getSession().getUI().error("No synchronizer--cannot upload sync receipt");
		if(!isEditable())
			throw new IllegalArgumentException("User " + theSession.getUser() + " does not"
				+ " have permission to generate synchronization data for other data centers");
		getSession().getUI().input("Copy the synchronization receipt from the client here", null,
			new UI.InputListener()
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
						getSession().getUI()
							.error("Could not read sync receipt: " + e.getMessage());
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
								theSynchronizer.readSyncReceipt(reader);
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
		UI ui = getSession().getUI();
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
		UI ui = getSession().getUI();
		if(theSynchronizer == null)
			ui.error("No synchronizer--cannot synchronize");
		if(!isEditable())
			throw new IllegalArgumentException("User " + theSession.getUser()
				+ " does not have permission to synchronize with other data centers");
		UI.AppLockProgress pi = new UI.AppLockProgress(getSession().getApp());
		pi.setPostReload(true);
		pi.setProgressText("Initializing synchronization");
		pi.setCancelable(true);

		String error = null;
		try
		{
			SyncServiceClient syncClient = getSyncClient(theCenter);
			syncClient.synchronize(theCenter, SyncRecord.Type.MANUAL_REMOTE, pi,
				new CenterEditorPIDS(), true);
		} catch(prisms.records.PrismsRecordException e)
		{
			if(e.getCause() != null
				&& e.getCause().getMessage() != null
				&& e.getCause().getMessage()
					.contains(java.security.cert.CertificateException.class.getName()))
			{
				// Certificate error--grab the certs
				java.security.cert.X509Certificate[] certs;
				try
				{
					certs = theSyncClient.getCertificates(theCenter);
				} catch(PrismsRecordException e2)
				{
					certs = null;
					log.error("Certificate retrieval failed", e2);
					ui.info("Could not contact center " + theCenter + ": " + e2.getMessage());
				}
				if(certs != null)
					promptCerts(certs, Boolean.TRUE);
				else
				{
					log.error("Could not get certificate information", e);
					ui.info("Could not contact center " + theCenter + ": SSL authentication failed");
				}
				return;
			}
			log.error("Manual synchronization failed", e);
			error = e.getMessage();
		} finally
		{
			pi.setDone();
		}
		if(error == null)
			ui.info(pi.getTaskText());
		else
			ui.error(error);
	}

	void showAllImportHistory()
	{
		theSession.fireEvent("showCenterHistory", "center", theCenter, "import", Boolean.TRUE);
	}

	void showAllExportHistory()
	{
		theSession.fireEvent("showCenterHistory", "center", theCenter, "import", Boolean.FALSE);
	}

	enum SyncRecordField implements Sorter.Field
	{
		RECORD_TYPE("Type"), RECORD_TIME("Time"), RECORD_RESULTS("Results");

		public final String display;

		SyncRecordField(String disp)
		{
			display = disp;
		}

		@Override
		public String toString()
		{
			return display;
		}

		static SyncRecordField byName(String name)
		{
			for(SyncRecordField srf : values())
				if(srf.display.equals(name))
					return srf;
			return null;
		}
	}

	SyncRecordField getField(String name)
	{
		SyncRecordField ret = SyncRecordField.byName(name);
		if(ret == null)
			throw new IllegalArgumentException("Unrecognized sync record field" + name);
		return ret;
	}

	Sorter<SyncRecordField> theImportSorter;

	Sorter<SyncRecordField> theExportSorter;

	void sortBy(String column, boolean ascending, boolean isImport)
	{
		if(theImportSorter == null)
			theImportSorter = new Sorter<SyncRecordField>();
		if(theExportSorter == null)
			theExportSorter = new Sorter<SyncRecordField>();
		Sorter<SyncRecordField> sorter;
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
			theImportSorter = new Sorter<SyncRecordField>();
		if(theExportSorter == null)
			theExportSorter = new Sorter<SyncRecordField>();
		final Sorter<SyncRecordField> sorter;
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
			theSession.fireEvent("showSyncRecordHistory", RecordUtils.SYNC_RECORD_EVENT_PROP,
				holder.theRecord);
		else if(holder.theRecord.getSyncError().equals("?"))
			getSession().getUI().warn("Synchronize results unknown");
		else
			getSession().getUI().error("Synchronize failed -- " + holder.theRecord.getSyncError());
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
		StringBuilder tog = new StringBuilder();
		if(encKey > 0)
		{
			while(t < toEnc.length())
			{
				int a = toEnc.charAt(t);
				int c = a ^ encKey;
				char d = (char) c;
				tog.append(d);
				t++;
			}
		}
		return tog.toString();
	}

	java.awt.Color unknownColor = new java.awt.Color(160, 160, 0);

	void setRecord(prisms.ui.SortTableStructure.TableRow row, RecordHolder holder)
	{
		SyncRecord record = holder.theRecord;
		row.cell(0).setLabel(record.getSyncType().toString());
		row.cell(1).setLabel(prisms.util.PrismsUtils.print(record.getSyncTime()));
		JSONObject linkID = prisms.util.PrismsUtils.rEventProps("id",
			Integer.valueOf(record.getID()));
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
		if(error != null)
			getSession().getUI().error(error);
		if(toPurge.length == 0 && error == null)
			getSession().getUI().info(
				"No " + (server ? "import" : "export") + " records selected to purge");
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
			getSession().getUI().confirm(
				"Are you sure you want to purge the record of " + toPurge[0] + "?", cl);
		else
			getSession().getUI().confirm(
				"Are you sure you want to purge these " + toPurge.length
					+ " synchronization records?", cl);
	}

	/** @return The synchronizer that this editor is to use for manual synchronization */
	protected PrismsSynchronizer getSynchronizer()
	{
		if(theSynchronizer == null)
			throw new IllegalStateException("No synchronizer set for plugin " + theName);
		return theSynchronizer;
	}

	/**
	 * @param center The center that the client is to synchronize with
	 * @return The synchronize client that this editor is to use for manual synchronization on the
	 *         given center
	 */
	protected SyncServiceClient getSyncClient(PrismsCenter center)
	{
		if(theSyncClient == null)
			throw new IllegalStateException("No synchronize client set for plugin " + theName);
		return theSyncClient;
	}

	/** @return All users available to connect to a center */
	protected RecordUser [] getUsers()
	{
		if(theUserProp == null)
			throw new IllegalStateException("No users property set for plugin " + theName);
		return getSession().getProperty(theUserProp);
	}

	/** @return All centers available for synchronization */
	protected PrismsCenter [] getCenters()
	{
		if(theCenterProp == null)
			throw new IllegalStateException("No centers property set for plugin " + theName);
		return getSession().getProperty(theCenterProp);
	}

	/**
	 * Performs operations on a center after its center ID is set, specifically creating any
	 * dependent centers that need to be created
	 * 
	 * @param sync The synchronizer that is synchronizing with the center
	 * @param center The center that the synchronizer is synchronizing with
	 * @throws PrismsRecordException If the operation cannot be performed
	 */
	protected void postIDSet(PrismsSynchronizer sync, PrismsCenter center)
		throws PrismsRecordException
	{
		for(PrismsSynchronizer depend : sync.getDepends())
			if(sync.getDependCenter(depend, theCenter) == null)
				createDependentCenter(depend, theCenter);
	}

	/**
	 * In order for synchronization to be successful, the synchronizer's dependents must first
	 * synchronize successfully. In order to do this, centers must be set up in each of the
	 * dependents' record keepers. Since this operation might be regulated by permissions, this
	 * method is provided to allow implementations to check the session user's permissions, create
	 * the center, and perform any startup operations with it (such as setting the new center into a
	 * property).
	 * 
	 * @param dependSync The synchronizer to create the center for
	 * @param center The center for the synchronizer that this center editor edits centers for
	 * @throws PrismsRecordException If the center cannot be created for any reason, including if
	 *         the session user is not allowed to set up synchronization with the given server in
	 *         the dependent synchronizer.
	 */
	protected abstract void createDependentCenter(PrismsSynchronizer dependSync, PrismsCenter center)
		throws PrismsRecordException;

	/** A {@link prisms.records.PrismsSynchronizer.PostIDSet} for this center editor */
	protected class CenterEditorPIDS implements prisms.records.PrismsSynchronizer.PostIDSet
	{
		public void postIDSet(PrismsSynchronizer sync, PrismsCenter center)
			throws PrismsRecordException
		{
			CenterEditor.this.postIDSet(sync, center);
		}
	}

	/** @return Whether the current session has authority to edit the selected center */
	protected abstract boolean isEditable();

	/** @return Whether the current session has authority to purge synchronization records */
	protected abstract boolean canPurge();
}
