/*
 * RecordKeeper.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import prisms.records.RecordPersister.ChangeData;
import prisms.util.ArrayUtils;
import prisms.util.PrismsUtils;

/**
 * Keeps records of changes to a data set
 */
public class RecordKeeper
{
	/**
	 * Fields on which the history may be sorted
	 */
	public static enum ChangeField implements HistorySorter.Field
	{
		/**
		 * Sort on the subject type
		 */
		CHANGE_TYPE("type"),
		/**
		 * Sort on the change time
		 */
		CHANGE_TIME("changeTime"),
		/**
		 * Sort on the user that made the change
		 */
		CHANGE_USER("changeUser");

		private final String theDBValue;

		ChangeField(String dbValue)
		{
			theDBValue = dbValue;
		}

		public String toString()
		{
			return theDBValue;
		}
	}

	static final Logger log = Logger.getLogger(RecordKeeper.class);

	/**
	 * The range of IDS that may exist in a given PRISMS center
	 */
	protected static int theCenterIDRange = 1000000000;

	final String theNamespace;

	private org.dom4j.Element theConnEl;

	private prisms.arch.PersisterFactory theFactory;

	private java.sql.Connection theConn;

	String DBOWNER;

	RecordPersister thePersister;

	private int theCenterID;

	private AutoPurger theAutoPurger;

	private SyncRecord theSyncRecord;

	/**
	 * Creates a record keeper
	 * 
	 * @param namespace The namespace that this keeper is to use to separate it from other record
	 *        keepers using the same database.
	 * @param connEl The XML element to use to obtain a database connection
	 * @param factory The persister factory to use to obtain a database connection
	 */
	public RecordKeeper(String namespace, org.dom4j.Element connEl,
		prisms.arch.PersisterFactory factory)
	{
		theNamespace = namespace;
		theConnEl = connEl;
		theFactory = factory;
		try
		{
			doStartup();
		} catch(PrismsRecordException e)
		{
			log.error("Could not perform startup operations", e);
		}
	}

	/**
	 * This method is for startup purposes ONLY! The persister cannot be changed out dynamically.
	 * This method must be called before any of the data methods are called.
	 * 
	 * @param persister The persister implementation allowing this keeper to associate itself with
	 *        implementation-specific data
	 */
	public void setPersister(RecordPersister persister)
	{
		if(thePersister != null)
			throw new IllegalArgumentException("The persister cannot be changed");
		thePersister = persister;
	}

	/**
	 * Peforms initial functions to set up this data source
	 * 
	 * @throws PrismsRecordException If an error occurs getting the setup data
	 */
	protected void doStartup() throws PrismsRecordException
	{
		PrismsCenter selfCenter = getCenter(0, null);
		if(selfCenter != null)
			theCenterID = selfCenter.getCenterID();
		else
		{
			selfCenter = new PrismsCenter(0, "Here");
			selfCenter.setNamespace(theNamespace);
			selfCenter.setCenterID((int) (Math.random() * theCenterIDRange));
			theCenterID = selfCenter.getCenterID();
			putCenter(selfCenter, null);
			log.debug("Created rules center with ID " + selfCenter.getID());
		}
	}

	/**
	 * @return The namespace that this record keeper is in. More than one record keeper can use the
	 *         same database, provided they keep different namespaces.
	 */
	public String getNamespace()
	{
		return theNamespace;
	}

	/**
	 * @return The persister implementation allowing this record keeper to associate itself with
	 *         implementation-specific data
	 */
	public RecordPersister getPersister()
	{
		return thePersister;
	}

	/**
	 * @return This database's identifier
	 */
	public int getCenterID()
	{
		return theCenterID;
	}

	/**
	 * @param objectID The ID of an object
	 * @return The ID of the center where the given object was created
	 */
	public static int getCenterID(long objectID)
	{
		return (int) (objectID / theCenterIDRange);
	}

	/**
	 * @return All non-deleted external centers known in this namespace
	 * @throws PrismsRecordException If an error occurs retrieving the centers
	 */
	public PrismsCenter [] getCenters() throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = "SELECT * FROM " + DBOWNER + "prisms_center_view LEFT OUTER JOIN " + DBOWNER
			+ "prisms_center ON centerID=" + DBOWNER + "prisms_center.id WHERE recordNS="
			+ toSQL(theNamespace) + " AND deleted=" + boolToSQL(false);
		checkConnection();
		try
		{
			stmt = theConn.createStatement();
			rs = stmt.executeQuery(sql);
			ArrayList<PrismsCenter> ret = new ArrayList<PrismsCenter>();
			ArrayList<Number> clientUsers = new ArrayList<Number>();
			while(rs.next())
			{
				PrismsCenter pc = new PrismsCenter(rs.getInt("id"), rs.getString("name"));
				pc.setNamespace(theNamespace);
				Number centerID = (Number) rs.getObject("centerID");
				if(centerID != null)
					pc.setCenterID(centerID.intValue());
				pc.setName(rs.getString("name"));
				pc.setServerURL(rs.getString("url"));
				pc.setServerUserName(rs.getString("serverUserName"));
				pc.setServerPassword(unprotect(rs.getString("serverPassword")));
				Number syncFreq = (Number) rs.getObject("syncFrequency");
				if(syncFreq != null)
					pc.setServerSyncFrequency(syncFreq.longValue());
				clientUsers.add((Number) rs.getObject("clientUser"));
				Number changeSaveTime = (Number) rs.getObject("changeSaveTime");
				if(changeSaveTime != null)
					pc.setChangeSaveTime(changeSaveTime.longValue());
				java.sql.Timestamp time;
				time = rs.getTimestamp("lastImportSync");
				if(time != null)
					pc.setLastImport(time.getTime());
				time = rs.getTimestamp("lastExportSync");
				if(time != null)
					pc.setLastExport(time.getTime());
				ret.add(pc);
			}
			rs.close();
			rs = null;
			for(int i = 0; i < ret.size(); i++)
			{
				if(clientUsers.get(i) != null)
					ret.get(i).setClientUser(thePersister.getUser(clientUsers.get(i).longValue()));
			}
			return ret.toArray(new PrismsCenter [ret.size()]);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get rules centers: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	PrismsCenter getCenter(int id, Statement stmt) throws PrismsRecordException
	{
		ResultSet rs = null;
		String sql = null;
		PrismsCenter pc;
		Number clientUserID;
		boolean delStmt = false;
		try
		{
			if(stmt == null)
			{
				delStmt = true;
				checkConnection();
				stmt = theConn.createStatement();
			}
			sql = "SELECT * FROM " + DBOWNER + "prisms_center_view WHERE id=" + id
				+ " AND recordNS=" + toSQL(theNamespace);
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			pc = new PrismsCenter(id, rs.getString("name"));
			Number centerID = (Number) rs.getObject("centerID");
			if(centerID != null)
				pc.setCenterID(centerID.intValue());
			pc.setNamespace(theNamespace);
			pc.setName(rs.getString("name"));
			pc.setServerURL(rs.getString("url"));
			pc.setServerUserName(rs.getString("serverUserName"));
			pc.setServerPassword(unprotect(rs.getString("serverPassword")));
			Number syncFreq = (Number) rs.getObject("syncFrequency");
			if(syncFreq != null)
				pc.setServerSyncFrequency(syncFreq.longValue());
			clientUserID = (Number) rs.getObject("clientUser");
			Number changeSaveTime = (Number) rs.getObject("changeSaveTime");
			if(changeSaveTime != null)
				pc.setChangeSaveTime(changeSaveTime.longValue());
			java.sql.Timestamp time;
			time = rs.getTimestamp("lastImportSync");
			if(time != null)
				pc.setLastImport(time.getTime());
			time = rs.getTimestamp("lastExportSync");
			if(time != null)
				pc.setLastExport(time.getTime());
			pc.isDeleted = boolFromSQL(rs.getString("deleted"));
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get rules center for ID " + id + ": SQL="
				+ sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			if(stmt != null && delStmt)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		if(clientUserID != null)
			pc.setClientUser(thePersister.getUser(clientUserID.longValue()));
		return pc;
	}

	/**
	 * Adds a new center to the record keeper or updates an existing center
	 * 
	 * @param center The center to add or update
	 * @param user The user that caused the change
	 * @throws PrismsRecordException If an error occurs persisting the data
	 */
	public synchronized void putCenter(PrismsCenter center, RecordUser user)
		throws PrismsRecordException
	{
		Statement stmt = null;
		checkConnection();
		try
		{
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not create statement", e);
			}
			if(center.getID() < 0)
				try
				{
					center.setID(getNextIntID(stmt, DBOWNER + "prisms_center_view", "id"));
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Could not get next center ID", e);
				}
			PrismsCenter dbCenter = getCenter(center.getID(), stmt);
			String sql;
			if(center.getCenterID() >= 0 && (dbCenter == null || dbCenter.getCenterID() < 0))
			{
				boolean hasCenter;
				sql = "SELECT id FROM " + DBOWNER + "prisms_center WHERE id="
					+ center.getCenterID();
				ResultSet rs = null;
				try
				{
					rs = stmt.executeQuery(sql);
					hasCenter = rs.next();
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Could not query center: SQL=" + sql, e);
				} finally
				{
					if(rs != null)
						try
						{
							rs.close();
						} catch(SQLException e)
						{
							log.error("Connection error", e);
						}
				}
				if(!hasCenter)
				{
					sql = "INSERT INTO " + DBOWNER + "prisms_center (id) VALUES ("
						+ center.getCenterID() + ")";
					try
					{
						stmt = theConn.createStatement();
						stmt.execute(sql);
					} catch(SQLException e)
					{
						throw new PrismsRecordException("Could not insert center: SQL=" + sql, e);
					}
				}
			}
			if(dbCenter == null)
			{
				if(user == null && center.getID() != 0)
				{
					log.warn("Cannot insert PRISMS center view--no user");
					return;
				}
				log.debug("Adding center " + center);
				sql = "INSERT INTO " + DBOWNER
					+ "prisms_center_view (id, centerID, recordNS, name,"
					+ " url, serverUserName, serverPassword, syncFrequency, clientUser,"
					+ " changeSaveTime, lastImportSync, lastExportSync, deleted) VALUES("
					+ center.getID() + ", ";
				sql += (center.getCenterID() >= 0 ? "" + center.getCenterID() : "NULL");
				sql += ", " + toSQL(theNamespace) + ", " + toSQL(center.getName()) + ", "
					+ toSQL(center.getServerURL()) + ", " + toSQL(center.getServerUserName())
					+ ", " + toSQL(protect(center.getServerPassword())) + ", ";
				sql += (center.getServerSyncFrequency() > 0 ? "" + center.getServerSyncFrequency()
					: "NULL")
					+ ", ";
				sql += (center.getClientUser() != null ? "" + center.getClientUser().getID()
					: "NULL")
					+ ", ";
				sql += (center.getChangeSaveTime() > 0 ? "" + center.getChangeSaveTime() : "NULL")
					+ ", "
					+ (center.getLastImport() > 0 ? formatDate(center.getLastImport()) : "NULL")
					+ ", "
					+ (center.getLastExport() > 0 ? formatDate(center.getLastExport()) : "NULL")
					+ ", " + boolToSQL(center.isDeleted()) + ")";
				try
				{
					stmt.execute(sql);
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Could not insert center: SQL=" + sql, e);
				}
				if(user != null)
					persist(user, PrismsChange.center, null, 1, center, null, null, null, null);
			}
			else
			{
				String changeMsg = "Updated center " + dbCenter + ":\n";
				boolean modified = false;
				if(dbCenter.getCenterID() < 0 && center.getCenterID() >= 0)
				{
					changeMsg += "Center ID set to " + center.getCenterID() + "\n";
					modified = true;
					// No modification here--user-transparent
					dbCenter.setCenterID(center.getCenterID());
				}
				if(!dbCenter.getName().equals(center.getName()))
				{
					changeMsg += "Changed name from " + dbCenter.getName() + " to "
						+ center.getName() + "\n";
					modified = true;
					if(user == null)
						throw new PrismsRecordException("Cannot modify a center without a user");
					persist(user, PrismsChange.center, PrismsChange.CenterChange.name, 0, dbCenter,
						null, dbCenter.getName(), null, null);
					dbCenter.setName(center.getName());
				}
				if(!equal(dbCenter.getServerURL(), center.getServerURL()))
				{
					changeMsg += "Changed URL from " + dbCenter.getServerURL() + " to "
						+ center.getServerURL() + "\n";
					modified = true;
					if(user == null)
						throw new PrismsRecordException("Cannot modify a center without a user");
					persist(user, PrismsChange.center, PrismsChange.CenterChange.url, 0, dbCenter,
						null, dbCenter.getServerURL(), null, null);
					dbCenter.setServerURL(center.getServerURL());
				}
				if(!equal(dbCenter.getServerUserName(), center.getServerUserName()))
				{
					changeMsg += "Changed server user name from " + dbCenter.getServerUserName()
						+ " to " + center.getServerUserName() + "\n";
					modified = true;
					if(user == null)
						throw new PrismsRecordException("Cannot modify a center without a user");
					persist(user, PrismsChange.center, PrismsChange.CenterChange.serverUserName, 0,
						dbCenter, null, dbCenter.getServerUserName(), null, null);
					dbCenter.setServerUserName(center.getServerUserName());
				}
				if(!equal(dbCenter.getServerPassword(), center.getServerPassword()))
				{
					changeMsg += "Changed server password from ";
					if(dbCenter.getServerPassword() == null)
						changeMsg += null;
					else
						for(int c = 0; c < dbCenter.getServerPassword().length(); c++)
							changeMsg += '*';
					changeMsg += " to ";
					if(center.getServerPassword() == null)
						changeMsg += null;
					else
						for(int c = 0; c < center.getServerPassword().length(); c++)
							changeMsg += '*';
					changeMsg += "\n";
					modified = true;
					if(user == null)
						throw new PrismsRecordException("Cannot modify a center without a user");
					persist(user, PrismsChange.center, PrismsChange.CenterChange.serverPassword, 0,
						dbCenter, null, dbCenter.getServerPassword(), null, null);
					dbCenter.setServerPassword(center.getServerPassword());
				}
				if(dbCenter.getServerSyncFrequency() != center.getServerSyncFrequency())
				{
					changeMsg += "Changed server synchronization frequency from "
						+ (dbCenter.getServerSyncFrequency() >= 0 ? PrismsUtils
							.printTimeLength(dbCenter.getServerSyncFrequency()) : "none")
						+ " to "
						+ (center.getServerSyncFrequency() >= 0 ? PrismsUtils
							.printTimeLength(center.getServerSyncFrequency()) : "none") + "\n";
					modified = true;
					if(user == null)
						throw new PrismsRecordException("Cannot modify a center without a user");
					persist(user, PrismsChange.center, PrismsChange.CenterChange.syncFrequency, 0,
						dbCenter, null, new Long(dbCenter.getServerSyncFrequency()), null, null);
					dbCenter.setServerSyncFrequency(center.getServerSyncFrequency());
				}
				if(!equal(dbCenter.getClientUser(), center.getClientUser()))
				{
					changeMsg += "Changed client user from "
						+ (dbCenter.getClientUser() == null ? "none" : dbCenter.getClientUser()
							.getName())
						+ " to "
						+ (center.getClientUser() == null ? "none" : center.getClientUser()
							.getName()) + "\n";
					modified = true;
					if(user == null)
						throw new PrismsRecordException("Cannot modify a center without a user");
					persist(user, PrismsChange.center, PrismsChange.CenterChange.clientUser, 0,
						dbCenter, null, dbCenter.getClientUser(), null, null);
					dbCenter.setClientUser(center.getClientUser());
				}
				if(dbCenter.getChangeSaveTime() != center.getChangeSaveTime())
				{
					changeMsg += "Changed modification save time from "
						+ (dbCenter.getChangeSaveTime() >= 0 ? PrismsUtils.printTimeLength(dbCenter
							.getChangeSaveTime()) : "none")
						+ " to "
						+ (center.getChangeSaveTime() >= 0 ? PrismsUtils.printTimeLength(center
							.getChangeSaveTime()) : "none") + "\n";
					modified = true;
					if(user == null)
						throw new PrismsRecordException("Cannot modify a center without a user");
					persist(user, PrismsChange.center, PrismsChange.CenterChange.changeSaveTime, 0,
						dbCenter, null, new Long(dbCenter.getChangeSaveTime()), null, null);
					dbCenter.setChangeSaveTime(center.getChangeSaveTime());
				}
				if(dbCenter.getLastImport() != center.getLastImport())
				{
					changeMsg += "Change last import time from "
						+ (dbCenter.getLastImport() > 0 ? PrismsUtils.print(dbCenter
							.getLastImport()) : "none")
						+ " to "
						+ (center.getLastImport() > 0 ? PrismsUtils.print(center.getLastImport())
							: "none");
					modified = true;
					dbCenter.setLastImport(center.getLastImport());
				}
				if(dbCenter.getLastExport() != center.getLastExport())
				{
					changeMsg += "Change last export time from "
						+ (dbCenter.getLastExport() > 0 ? PrismsUtils.print(dbCenter
							.getLastExport()) : "none")
						+ " to "
						+ (center.getLastExport() > 0 ? PrismsUtils.print(dbCenter.getLastExport())
							: "none");
					modified = true;
					dbCenter.setLastExport(center.getLastExport());
				}

				if(dbCenter.isDeleted() && !center.isDeleted())
				{
					changeMsg += "Re-creating center";
					if(user == null)
						throw new PrismsRecordException(
							"Cannot modify a rules center without a user");
					persist(user, PrismsChange.center, null, 1, dbCenter, null, null, null, null);
					dbCenter.isDeleted = false;
					modified = true;
				}

				if(modified)
				{
					log.debug(changeMsg.substring(0, changeMsg.length() - 1));
					sql = "UPDATE " + DBOWNER + "prisms_center_view SET centerID="
						+ (dbCenter.getCenterID() < 0 ? "NULL" : "" + dbCenter.getCenterID())
						+ ", name=" + toSQL(dbCenter.getName()) + ", url="
						+ toSQL(dbCenter.getServerURL()) + ", serverUserName="
						+ toSQL(dbCenter.getServerUserName()) + ", serverPassword="
						+ toSQL(protect(dbCenter.getServerPassword()));
					sql += ", syncFrequency="
						+ (dbCenter.getServerSyncFrequency() > 0 ? ""
							+ dbCenter.getServerSyncFrequency() : "NULL");
					sql += ", clientUser="
						+ (dbCenter.getClientUser() == null ? "NULL" : ""
							+ dbCenter.getClientUser().getID());
					sql += ", changeSaveTime="
						+ (dbCenter.getChangeSaveTime() > 0 ? "" + dbCenter.getChangeSaveTime()
							: "NULL") + ", deleted=" + boolToSQL(dbCenter.isDeleted());
					sql += ", lastImportSync=" + formatDate(dbCenter.getLastImport())
						+ ", lastExportSync=" + formatDate(dbCenter.getLastExport());
					sql += " WHERE id=" + dbCenter.getID();

					try
					{
						stmt.executeUpdate(sql);
					} catch(SQLException e)
					{
						throw new PrismsRecordException("Could not update center: SQL=" + sql, e);
					}
				}
			}
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	/**
	 * Deletes a center
	 * 
	 * @param center The center to delete
	 * @param user The user that caused the change
	 * @throws PrismsRecordException If an error occurs deleting the center
	 */
	public synchronized void removeCenter(PrismsCenter center, RecordUser user)
		throws PrismsRecordException
	{
		Statement stmt = null;
		String sql = "UPDATE " + DBOWNER + "prisms_center_view SET deleted=" + boolToSQL(true)
			+ " WHERE id=" + center.getID();
		checkConnection();
		try
		{
			stmt = theConn.createStatement();
			stmt.executeUpdate(sql);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not delete center: SQL=" + sql, e);
		}
		center.isDeleted = true;
		persist(user, PrismsChange.center, null, -1, center, null, null, null, null);
	}

	/**
	 * Gets the synchronization records for a center
	 * 
	 * @param center The center to get synchronization records for
	 * @param isImport true if this method should only get import records, false if it should only
	 *        get export records, null if it should get both types
	 * @return The requested synchronization records
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public SyncRecord [] getSyncRecords(PrismsCenter center, Boolean isImport)
		throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = "SELECT * FROM " + DBOWNER + "prisms_sync_record WHERE recordNS="
			+ toSQL(theNamespace) + " AND syncCenter=" + center.getID();
		if(isImport != null)
			sql += " AND isImport=" + boolToSQL(isImport.booleanValue());
		sql += " ORDER BY syncTime DESC";
		ArrayList<SyncRecord> ret = new ArrayList<SyncRecord>();
		checkConnection();
		try
		{
			stmt = theConn.createStatement();
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				SyncRecord sr = new SyncRecord(rs.getInt("id"), center, SyncRecord.Type.byName(rs
					.getString("syncType")), rs.getTimestamp("syncTime").getTime(), boolFromSQL(rs
					.getString("isImport")));
				sr.setParallelID(rs.getInt("parallelID"));
				sr.setSyncError(rs.getString("syncError"));
				ret.add(sr);
			}
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get sync records: SQL=" + sql, e);
		}
		return ret.toArray(new SyncRecord [ret.size()]);
	}

	/**
	 * Puts a new synchronization record in the database or updates an existing record
	 * 
	 * @param record The synchronization record to add or update
	 * @throws PrismsRecordException If an error occurs setting the data
	 */
	public synchronized void putSyncRecord(SyncRecord record) throws PrismsRecordException
	{
		Statement stmt = null;
		checkConnection();
		try
		{
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not create statement", e);
			}
			SyncRecord dbRecord = getSyncRecord(record.getCenter(), record.getID(), stmt);
			String sql;
			if(dbRecord != null)
			{
				String changeMsg = "Updated sync record " + dbRecord + ":\n";
				boolean modified = false;
				if(!dbRecord.getSyncType().equals(record.getSyncType()))
				{
					changeMsg += "Changed type from " + dbRecord.getSyncType() + " to "
						+ record.getSyncType() + "\n";
					modified = true;
					dbRecord.setSyncType(record.getSyncType());
				}
				if(dbRecord.isImport() != record.isImport())
				{
					changeMsg += "Changed import from " + dbRecord.isImport() + " to "
						+ record.isImport() + "\n";
					modified = true;
					dbRecord.setImport(record.isImport());
				}
				if(dbRecord.getSyncTime() != record.getSyncTime())
				{
					changeMsg += "Changed time from " + PrismsUtils.print(dbRecord.getSyncTime())
						+ " to " + PrismsUtils.print(record.getSyncTime()) + "\n";
					modified = true;
					dbRecord.setSyncTime(record.getSyncTime());
				}
				if(dbRecord.getParallelID() != record.getParallelID())
				{
					changeMsg += "Changed parallel ID from "
						+ (dbRecord.getParallelID() < 0 ? "none" : "" + dbRecord.getParallelID())
						+ " to "
						+ (record.getParallelID() < 0 ? "none" : "" + record.getParallelID());
					modified = true;
					dbRecord.setParallelID(record.getParallelID());
				}
				if(!equal(dbRecord.getSyncError(), record.getSyncError()))
				{
					changeMsg += "Changed error from "
						+ (dbRecord.getSyncError() != null ? dbRecord.getSyncError() : "none")
						+ " to " + (record.getSyncError() != null ? record.getSyncError() : "none")
						+ "\n";
					modified = true;
					dbRecord.setSyncError(record.getSyncError());
				}

				if(modified)
				{
					log.debug(changeMsg.substring(0, changeMsg.length() - 1));
					sql = "UPDATE " + DBOWNER + "prisms_sync_record SET syncType="
						+ toSQL(dbRecord.getSyncType().toString()) + ", isImport="
						+ boolToSQL(dbRecord.isImport()) + ", syncTime="
						+ formatDate(dbRecord.getSyncTime()) + ", parallelID="
						+ (dbRecord.getParallelID() < 0 ? "NULL" : "" + dbRecord.getParallelID())
						+ ", syncError=" + toSQL(dbRecord.getSyncError()) + " WHERE id="
						+ dbRecord.getID();
					try
					{
						stmt.executeUpdate(sql);
					} catch(SQLException e)
					{
						throw new PrismsRecordException("Could not update sync record: SQL=" + sql,
							e);
					}
				}
			}
			else
			{
				log.debug("Sync record " + record + " inserted");
				try
				{
					record.setID(getNextIntID(stmt, DBOWNER + "prisms_sync_record", "id"));
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Could not get next record ID", e);
				}
				sql = "INSERT INTO " + DBOWNER + "prisms_sync_record (id, syncCenter, recordNS,"
					+ " syncType, isImport, syncTime, parallelID, syncError) VALUES ("
					+ record.getID() + ", " + record.getCenter().getID() + ", "
					+ toSQL(theNamespace) + ", " + toSQL(record.getSyncType().toString()) + ", "
					+ boolToSQL(record.isImport()) + ", " + formatDate(record.getSyncTime()) + ", "
					+ (record.getParallelID() < 0 ? "NULL" : "" + record.getParallelID()) + ", "
					+ toSQL(record.getSyncError()) + ")";
				try
				{
					stmt.execute(sql);
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Could not insert sync record: SQL=" + sql, e);
				}
			}
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	SyncRecord getSyncRecord(PrismsCenter center, int id, Statement stmt)
		throws PrismsRecordException
	{
		ResultSet rs = null;
		String sql = "SELECT * FROM " + DBOWNER + "prisms_sync_record WHERE recordNS="
			+ toSQL(theNamespace) + " AND id=" + id;
		try
		{
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			SyncRecord sr = new SyncRecord(rs.getInt("id"), center, SyncRecord.Type.byName(rs
				.getString("syncType")), rs.getTimestamp("syncTime").getTime(), boolFromSQL(rs
				.getString("isImport")));
			sr.setParallelID(rs.getInt("parallelID"));
			sr.setSyncError(rs.getString("syncError"));
			return sr;
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get sync records: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	/**
	 * Removes a synchronization record from the database
	 * 
	 * @param record The record to remove
	 * @throws PrismsRecordException If an error occurs deleting the record
	 */
	public void removeSyncRecord(SyncRecord record) throws PrismsRecordException
	{
		log.debug("Removed sync record " + record);
		String sql = "DELETE FROM " + DBOWNER + "prisms_sync_record WHERE recordNS="
			+ toSQL(theNamespace) + " AND id=" + record.getID();
		Statement stmt = null;
		checkConnection();
		try
		{
			stmt = theConn.createStatement();
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not remove sync record: SQL=" + sql, e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	/**
	 * Gets the IDs of changes that have happened since a given time
	 * 
	 * @param since The earliest time to retrieve changes from
	 * @return The IDs of all requested changes
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public long [] getChangeIDs(long since) throws PrismsRecordException
	{
		return getChangeRecords(null, null,
			(since > 0 ? "changeTime>=" + formatDate(since) : null), "changeTime DESC");
	}

	private long [] getChangeRecords(Statement stmt, String join, String where, String order)
		throws PrismsRecordException
	{
		boolean closeStmt = stmt == null;
		ResultSet rs = null;
		checkConnection();
		String sql = "SELECT id FROM " + DBOWNER + "prisms_change_record";
		if(join != null)
			sql += " " + join;
		sql += " WHERE recordNS=" + toSQL(theNamespace);
		if(where != null)
			sql += " AND (" + where + ")";
		if(order != null)
			sql += " ORDER BY " + order;
		ArrayList<Long> ret = new ArrayList<Long>();
		try
		{
			if(stmt == null)
				stmt = theConn.createStatement();
			rs = stmt.executeQuery(sql);
			while(rs.next())
				ret.add(new Long(rs.getLong(1)));
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get all change IDs: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Connection error", e);
				}
			if(closeStmt && stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Connection error", e);
				}
		}
		long [] retArray = new long [ret.size()];
		for(int i = 0; i < retArray.length; i++)
			retArray[i] = ret.get(i).longValue();
		return retArray;
	}

	/**
	 * Gets the IDs of all changes to an item
	 * 
	 * @param historyItem The item to get the history of
	 * @return The IDs of all changes to the item
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public long [] getHistory(Object historyItem) throws PrismsRecordException
	{
		long itemID = getDataID(historyItem);
		SubjectType [] types = getHistoryDomains(historyItem);
		String where = "";
		for(int i = 0; i < types.length; i++)
		{
			if(i > 0)
				where += " OR ";
			where += "(subjectType=" + toSQL(types[i].name()) + " AND (";
			boolean useOr = false;
			if(types[i].getMajorType().isInstance(historyItem))
			{
				where += "majorSubject=" + itemID;
				useOr = true;
			}
			if(types[i].getMetadataType1() != null
				&& types[i].getMetadataType1().isInstance(historyItem))
			{
				if(useOr)
					where += " OR ";
				where += "changeData1=" + itemID;
				useOr = true;
			}
			if(types[i].getMetadataType2() != null
				&& types[i].getMetadataType2().isInstance(historyItem))
			{
				if(useOr)
					where += " OR ";
				where += "changeData2=" + itemID;
				useOr = true;
			}
			where += "))";
		}
		return getChangeRecords(null, null, where, "changeTime DESC");
	}

	SubjectType [] getHistoryDomains(Object item) throws PrismsRecordException
	{
		if(item instanceof PrismsCenter)
			return new SubjectType [] {PrismsChange.center};
		if(item instanceof AutoPurger)
			return new SubjectType [] {PrismsChange.autoPurge};
		else
			return thePersister.getHistoryDomains(item);
	}

	/**
	 * Gets the IDs of all changes effected by a given user
	 * 
	 * @param user The user to get the activity of
	 * @return The IDs of all changes that the user caused
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public long [] getHistoryBy(RecordUser user) throws PrismsRecordException
	{
		return getChangeRecords(null, null, "changeUser=" + user.getID(), "changeTime DESC");
	}

	/**
	 * Gets all changes that are to the same subject and field as the given change but occur after
	 * it.
	 * 
	 * @param change The change to get the successors of
	 * @return The IDs of the successors of the given change
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public long [] getSuccessors(ChangeRecord change) throws PrismsRecordException
	{
		String where = "majorSubject=" + getDataID(change.majorSubject);
		if(change.minorSubject != null)
			where += " AND minorSubject = " + getDataID(change.minorSubject);
		else
			where += " AND minorSubject IS NULL";
		where += " AND subjectType=" + toSQL(change.type.subjectType.toString());
		if(change.type.changeType != null)
			where += " AND changeType=" + toSQL(change.type.changeType.toString());
		else
			where += " AND changeType IS NULL";
		if(change.type.additivity == 0)
			where += " AND additivity=0";
		else
			where += " AND additivity<>0";
		where += " AND changeTime>" + formatDate(change.time);
		where += " AND id<>" + change.id;
		return getChangeRecords(null, null, where, "changeTime");
	}

	/**
	 * Gets all changes that are associated with a synchronization record
	 * 
	 * @param syncRecord The synchronization record to get the changes of
	 * @return All changes associated with the synchronization record
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public long [] getSyncChanges(SyncRecord syncRecord) throws PrismsRecordException
	{
		return getChangeRecords(null, "INNER JOIN " + DBOWNER
			+ "prisms_sync_assoc ON changeRecord=id", "syncRecord=" + syncRecord.getID(),
			"changeTime DESC");
	}

	/**
	 * Sorts a set of change IDs based on a set of criteria
	 * 
	 * @param ids The IDs of the changes to sort
	 * @param sorter The sorter determining how the changes should be sorted
	 * @return The same IDs as passed in, but ordered differently. The only time the unordered set
	 *         will be different is if one or more of the IDs passed in corresponds to a change that
	 *         has been purged or does not exist.
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public long [] sortChangeIDs(long [] ids, HistorySorter<ChangeField> sorter)
		throws PrismsRecordException
	{
		if(ids.length <= 1 || sorter.getSortCount() == 0)
			return ids;
		String where = "id IN (";
		for(int i = 0; i < ids.length; i++)
		{
			if(i > 0)
				where += ", ";
			where += ids[i];
		}
		where += ")";
		String order;
		if(sorter.getSortCount() > 0)
		{
			order = "";
			for(int sc = 0; sc < sorter.getSortCount(); sc++)
			{
				if(sc > 0)
					order += ", ";
				ChangeField field = sorter.getField(sc);
				switch(field)
				{
				case CHANGE_TYPE:
					order += " subjectType " + (sorter.isAscending(sc) ? "ASC" : "DESC") + ",";
					order += " changeType " + (sorter.isAscending(sc) ? "ASC" : "DESC");
					break;
				case CHANGE_TIME:
				case CHANGE_USER:
					order += " " + sorter.getField(sc).toString()
						+ (sorter.isAscending(sc) ? " ASC" : " DESC");
					break;
				}
			}
		}
		else
			order = "modTime DESC";
		return getChangeRecords(null, null, where, order);
	}

	/**
	 * Gets changes by IDs
	 * 
	 * @param ids The IDs of the changes to get
	 * @return The changes whose IDs are given
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public ChangeRecord [] getChanges(long [] ids) throws PrismsRecordException
	{
		return getChanges(null, ids);
	}

	private class ChangeTemplate
	{
		long id;

		long time;

		long userID;

		String subjectType;

		String changeType;

		char add;

		long majorSubjectID;

		Number minorSubjectID;

		Number preValueID;

		String previousValue;

		Number data1;

		Number data2;

		ChangeTemplate()
		{
		}
	}

	private ChangeRecord [] getChanges(Statement stmt, long [] ids) throws PrismsRecordException
	{
		if(ids.length == 0)
			return new ChangeRecord [0];
		boolean closeAfter = stmt == null;
		if(stmt == null)
		{
			checkConnection();
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not create statement", e);
			}
		}
		String sql = "SELECT * FROM " + DBOWNER + "prisms_change_record  WHERE recordNS="
			+ toSQL(theNamespace) + " AND id IN (";
		for(int i = 0; i < ids.length; i++)
		{
			sql += ids[i];
			if(i < ids.length - 1)
				sql += ", ";
		}
		sql += ")";
		try
		{
			ResultSet rs = null;
			ArrayList<ChangeTemplate> changeList = new ArrayList<ChangeTemplate>();
			try
			{
				rs = stmt.executeQuery(sql);
				while(rs.next())
				{
					ChangeTemplate template = new ChangeTemplate();
					changeList.add(template);
					template.id = rs.getLong("id");
					template.subjectType = fromSQL(rs.getString("subjectType"));
					template.changeType = fromSQL(rs.getString("changeType"));
					template.add = rs.getString("additivity").charAt(0);
					template.time = rs.getTimestamp("changeTime").getTime();
					template.userID = rs.getLong("changeUser");
					template.majorSubjectID = rs.getLong("majorSubject");
					template.minorSubjectID = (Number) rs.getObject("minorSubject");
					template.data1 = (Number) rs.getObject("changeData1");
					template.data2 = (Number) rs.getObject("changeData2");
					Number pvNumber = (Number) rs.getObject("preValueID");
					if(pvNumber != null)
						template.preValueID = pvNumber;
					else
					{
						template.previousValue = rs.getString("shortPreValue");
						if(template.previousValue == null)
						{
							java.sql.Clob clob = rs.getClob("longPreValue");
							if(clob != null)
								try
								{
									StringBuilder fv = new StringBuilder();
									java.io.Reader reader = clob.getCharacterStream();
									int read;
									while((read = reader.read()) >= 0)
										fv.append((char) read);
									template.previousValue = fv.toString();
								} catch(java.io.IOException e)
								{
									throw new PrismsRecordException(
										"Could not read long field value", e);
								}
						}
					}
				}
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not get modifications: SQL=" + sql, e);
			} finally
			{
				try
				{
					if(rs != null)
						rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			}
			ChangeRecord [] ret = new ChangeRecord [changeList.size()];
			for(int i = 0; i < ret.length; i++)
			{
				ChangeTemplate template = changeList.get(i);
				SubjectType subjectType = getType(template.subjectType);
				ChangeType changeType = getChangeType(subjectType, template.changeType);
				int add = template.add == '+' ? 1 : (template.add == '-' ? -1 : 0);
				ChangeData changeData = getChangeData(subjectType, changeType,
					template.majorSubjectID, template.minorSubjectID, template.data1,
					template.data2, template.preValueID);
				if(changeData.preValue == null && template.previousValue != null)
					changeData.preValue = deserialize(changeType.getObjectType(),
						template.previousValue);
				ret[i] = new ChangeRecord(template.id, template.time, thePersister
					.getUser(template.userID), subjectType, changeType, add,
					changeData.majorSubject, changeData.minorSubject, changeData.preValue,
					changeData.data1, changeData.data2);
			}
			Long [] idObjs = new Long [ids.length];
			for(int i = 0; i < ids.length; i++)
				idObjs[i] = new Long(ids[i]);
			return ArrayUtils.adjust(ret, idObjs,
				new ArrayUtils.DifferenceListener<ChangeRecord, Long>()
				{
					public boolean identity(ChangeRecord o1, Long o2)
					{
						return o1.id == o2.longValue();
					}

					public ChangeRecord added(Long o, int idx, int retIdx)
					{
						return null;
					}

					public ChangeRecord removed(ChangeRecord o, int idx, int incMod, int retIdx)
					{
						return o;
					}

					public ChangeRecord set(ChangeRecord o1, int idx1, int incMod, Long o2,
						int idx2, int retIdx)
					{
						return o1;
					}
				});
		} finally
		{
			try
			{
				if(closeAfter && stmt != null)
					stmt.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			}
		}
	}

	SubjectType getType(String typeName) throws PrismsRecordException
	{
		for(PrismsChange ch : PrismsChange.values())
			if(ch.name().equals(typeName))
				return ch;
		return thePersister.getType(typeName);
	}

	long getDataID(Object obj) throws PrismsRecordException
	{
		if(obj instanceof PrismsCenter)
			return ((PrismsCenter) obj).getID();
		if(obj instanceof AutoPurger)
			return 0;
		return thePersister.getID(obj);
	}

	ChangeData getChangeData(SubjectType subjectType, ChangeType changeType, long majorSubjectID,
		Number minorSubjectID, Number data1ID, Number data2ID, Number preValueID)
		throws PrismsRecordException
	{
		if(subjectType instanceof PrismsChange)
		{
			PrismsChange pc = (PrismsChange) subjectType;
			switch(pc)
			{
			case center:
				PrismsCenter ctr = getCenter((int) majorSubjectID, null);
				ChangeData ret = new ChangeData(ctr, null, null, null, null);
				if(changeType == null)
					return ret;
				switch((PrismsChange.CenterChange) changeType)
				{
				case name:
				case url:
				case serverUserName:
				case serverPassword:
				case syncFrequency:
				case changeSaveTime:
					return ret;
				case clientUser:
					if(preValueID != null)
						ret.preValue = thePersister.getUser(preValueID.longValue());
					return ret;
				}
				break;
			case autoPurge:
				AutoPurger ap = getAutoPurger();
				ret = new ChangeData(ap, null, null, null, null);
				if(changeType == null)
					return ret;
				switch((PrismsChange.AutoPurgeChange) changeType)
				{
				case age:
				case entryCount:
				case excludeType:
					return ret;
				case excludeUser:
					if(minorSubjectID != null)
						ret.minorSubject = thePersister.getUser(minorSubjectID.longValue());
					return ret;
				}
			}
			throw new PrismsRecordException("Unrecognized subjectType/changeType " + subjectType
				+ "/" + changeType);
		}
		return thePersister.getData(subjectType, changeType, majorSubjectID, minorSubjectID,
			data1ID, data2ID, preValueID);
	}

	String serialize(Object obj) throws PrismsRecordException
	{
		if(obj == null)
			return null;
		if(obj instanceof Boolean || obj instanceof Integer || obj instanceof Long
			|| obj instanceof Float || obj instanceof Double || obj instanceof String)
			return obj.toString();
		return thePersister.serialize(obj);
	}

	Object deserialize(Class<?> type, String serialized) throws PrismsRecordException
	{
		if(serialized == null)
			return null;
		else if(type == String.class)
			return serialized;
		else if(type == Boolean.class)
			return "true".equalsIgnoreCase(serialized) ? Boolean.TRUE : Boolean.FALSE;
		else if(type == Integer.class)
			return new Integer(serialized);
		else if(type == Long.class)
			return new Long(serialized);
		else if(type == Float.class)
			return new Float(serialized);
		else if(type == Double.class)
			return new Double(serialized);
		else
			return thePersister.deserialize(type, serialized);
	}

	ChangeType getChangeType(SubjectType subjectType, String typeName) throws PrismsRecordException
	{
		if(typeName == null)
			return null;
		Class<? extends Enum<? extends ChangeType>> types = subjectType.getChangeTypes();
		if(types == null)
			throw new PrismsRecordException("Change domain " + subjectType + " allows no fields: "
				+ typeName);
		for(Enum<? extends ChangeType> f : types.getEnumConstants())
		{
			if(f.toString().equals(typeName))
				return (ChangeType) f;
		}
		throw new PrismsRecordException("Change type " + typeName
			+ " does not exist in subject type " + subjectType);
	}

	/**
	 * Checks all active rules centers in this data source for the modification save time and last
	 * time they synchronized to this center in order to get a purge-safe time after which
	 * modifications should not be purged so that they will be available to clients.
	 * 
	 * @return The purge-safe time after which modifications should not be purged
	 * @throws PrismsRecordException If an error occurs reading the data
	 */
	public long getPurgeSafeTime() throws PrismsRecordException
	{
		long nowTime = System.currentTimeMillis();
		long ret = nowTime;
		Statement stmt = null;
		ResultSet rs = null;
		String sql = "SELECT changeSaveTime, lastExportSync FROM " + DBOWNER
			+ "prisms_center_view WHERE recordNS=" + toSQL(theNamespace);
		checkConnection();
		try
		{
			stmt = theConn.createStatement();
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				long lastSync;
				java.sql.Timestamp time = rs.getTimestamp("lastExportSync");
				lastSync = time == null ? -1 : time.getTime();
				long saveTime = rs.getLong("changeSaveTime");
				if(lastSync < nowTime - saveTime)
					lastSync = nowTime - saveTime;
				if(ret > lastSync)
					ret = lastSync;
			}
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not check centers for purge times: SQL=" + sql,
				e);
		}
		return ret;
	}

	/**
	 * @return The auto-purger that manages the changes in this record keeper
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public AutoPurger getAutoPurger() throws PrismsRecordException
	{
		if(theAutoPurger != null)
			return theAutoPurger;
		Statement stmt = null;
		checkConnection();
		try
		{
			stmt = theConn.createStatement();
			theAutoPurger = dbGetAutoPurger(stmt);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not create statement", e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		return theAutoPurger;
	}

	/**
	 * Modifies the auto-purge settings that manage the changes in this record keeper
	 * 
	 * @param purger The purger that this record keeper should use to auto-purge its entries
	 * @param user The user that caused this change
	 * @throws PrismsRecordException If an error occurs setting the data
	 */
	public synchronized void setAutoPurger(AutoPurger purger, RecordUser user)
		throws PrismsRecordException
	{
		Statement stmt = null;
		checkConnection();
		try
		{
			stmt = theConn.createStatement();
			dbSetAutoPurger(stmt, purger, user);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not create statement", e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	/**
	 * @param purger The auto purger to test
	 * @return The number of modifications that would be deleted by this purger at the moment if it
	 *         were set
	 * @throws PrismsRecordException If an error occurs performing the calculation
	 */
	public synchronized int previewAutoPurge(final AutoPurger purger) throws PrismsRecordException
	{
		Statement stmt = null;
		checkConnection();
		try
		{
			checkConnection();
			stmt = theConn.createStatement();
			return purger.previewRowsDeleted(this, stmt, DBOWNER + "prisms_change_record",
				"changeTime", "changeUser", "subjectType", "changeType", "additivity");
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not create statement", e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	/**
	 * Sets the sync record that is to be associated with each persisted change until the sync
	 * record is cleared
	 * 
	 * @param record The record that should be associated with each change record persisted, or null
	 *        if no record should be associated so
	 */
	public synchronized void setSyncRecord(SyncRecord record)
	{
		theSyncRecord = record;
	}

	/**
	 * Persists a change
	 * 
	 * @param user The user that caused the change
	 * @param subjectType The subject type of the change
	 * @param changeType The type of the change to the subject
	 * @param additivity The additivity of the change
	 * @param majorSubject The major component of the subject
	 * @param minorSubject The minor component of the subject
	 * @param previousValue The value that was set for the field before this change
	 * @param data1 The first item of metadata
	 * @param data2 The second item of metadata
	 * @throws PrismsRecordException If an error occurs setting the data
	 */
	public synchronized void persist(RecordUser user, SubjectType subjectType,
		ChangeType changeType, int additivity, Object majorSubject, Object minorSubject,
		Object previousValue, Object data1, Object data2) throws PrismsRecordException
	{
		Statement stmt = null;
		checkConnection();
		long id;
		try
		{
			stmt = theConn.createStatement();
			id = getNextID(stmt, DBOWNER, "prisms_change_record", "id", null);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not persist " + subjectType + " change", e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		ChangeRecord record = new ChangeRecord(id, System.currentTimeMillis(), user, subjectType,
			changeType, additivity, majorSubject, minorSubject, previousValue, data1, data2);
		persist(record);
	}

	/**
	 * Persists a change that already has an assigned ID
	 * 
	 * @param record The record of the change
	 * @throws PrismsRecordException If an error occurs setting the data
	 */
	public synchronized void persist(ChangeRecord record) throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		java.sql.PreparedStatement pStmt = null;
		checkConnection();
		String sql = null;
		try
		{
			stmt = theConn.createStatement();
			sql = "SELECT id FROM " + DBOWNER + "prisms_change_record WHERE id=" + record.id;
			rs = stmt.executeQuery(sql);
			if(rs.next())
			{ // modification already exists
				rs.close();
				rs = null;
				if(theSyncRecord != null)
				{
					sql = "INSERT INTO " + DBOWNER + "prisms_sync_assoc (recordNS, syncRecord,"
						+ " changeRecord) VALUES (" + toSQL(theNamespace) + ", "
						+ theSyncRecord.getID() + ", " + record.id + ")";
					stmt.execute(sql);
				}
				return;
			}
			sql = "INSERT INTO " + DBOWNER + "prisms_change_record (id, recordNS, changeTime,"
				+ " changeUser, subjectType, changeType, additivity, majorSubject, minorSubject,"
				+ " preValueID, shortPreValue, longPreValue, changeData1, changeData2)"
				+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
			pStmt = theConn.prepareStatement(sql);
			pStmt.setLong(1, record.id);
			pStmt.setString(2, theNamespace);
			pStmt.setTimestamp(3, new java.sql.Timestamp(System.currentTimeMillis()));
			pStmt.setLong(4, record.user.getID());
			pStmt.setString(5, record.type.subjectType.name());
			pStmt.setString(6, record.type.changeType == null ? null : record.type.changeType
				.name());
			pStmt.setString(7, record.type.additivity < 0 ? "-" : (record.type.additivity > 0 ? "+"
				: "0"));
			if(record.majorSubject == null)
				pStmt.setNull(8, java.sql.Types.NUMERIC);
			else
				pStmt.setLong(8, getDataID(record.majorSubject));
			if(record.minorSubject == null)
				pStmt.setNull(9, java.sql.Types.NUMERIC);
			else
				pStmt.setLong(9, getDataID(record.minorSubject));
			if(record.previousValue == null)
			{
				pStmt.setNull(10, java.sql.Types.NUMERIC);
				pStmt.setNull(11, java.sql.Types.VARCHAR);
				pStmt.setNull(12, java.sql.Types.CLOB);
			}
			else if(record.type.changeType.isObjectIdentifiable())
			{
				pStmt.setLong(10, getDataID(record.previousValue));
				pStmt.setNull(11, java.sql.Types.VARCHAR);
				pStmt.setNull(12, java.sql.Types.CLOB);
			}
			else
			{
				pStmt.setNull(10, java.sql.Types.NUMERIC);
				String serialized = serialize(record.previousValue);
				if(serialized.length() <= 100)
				{
					pStmt.setString(11, serialized);
					pStmt.setNull(12, java.sql.Types.CLOB);
				}
				else
				{
					pStmt.setNull(11, java.sql.Types.VARCHAR);
					pStmt.setCharacterStream(12, new java.io.StringReader(serialized), serialized
						.length());
				}
			}
			if(record.data1 == null)
				pStmt.setNull(13, java.sql.Types.NUMERIC);
			else
				pStmt.setLong(13, getDataID(record.data1));
			if(record.data2 == null)
				pStmt.setNull(14, java.sql.Types.NUMERIC);
			else
				pStmt.setLong(14, getDataID(record.data2));
			pStmt.execute();
			if(theSyncRecord != null)
			{
				sql = "INSERT INTO " + DBOWNER + "prisms_sync_assoc (recordNS, syncRecord,"
					+ " changeRecord) VALUES (" + toSQL(theNamespace) + ", "
					+ theSyncRecord.getID() + ", " + record.id + ")";
				stmt.execute(sql);
			}
			if(theAutoPurger == null)
				getAutoPurger();
			theAutoPurger.doPurge(this, stmt, DBOWNER + "prisms_change_record", "changeTime",
				"changeUser", "subjectType", "changeType", "additivity");
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not persist " + record.type.subjectType
				+ " change: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			try
			{
				if(pStmt != null)
					pStmt.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			} catch(Error e)
			{
				// Keep getting these from an HSQL bug--silence
				if(!e.getMessage().contains("compilation"))
					log.error("Error", e);
			}
		}
	}

	/**
	 * Associates a change with a synchronization record. For use when the change is already in the
	 * database when the synchronization attempt occurs.
	 * 
	 * @param change The already-existent change to associate with the sync record
	 * @param syncRecord The synchronization record to associate the change with
	 * @throws PrismsRecordException If an error occurs setting the data
	 */
	public synchronized void associate(ChangeRecord change, SyncRecord syncRecord)
		throws PrismsRecordException
	{
		Statement stmt = null;
		checkConnection();
		String sql = "INSERT INTO " + DBOWNER + "prisms_sync_assoc (recordNS, syncRecord,"
			+ " changeRecord) VALUES (" + toSQL(theNamespace) + ", " + syncRecord.getID() + ", "
			+ change.id + ")";
		try
		{
			stmt = theConn.createStatement();
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not associate change " + change
				+ " with sync record " + syncRecord + ": SQL=" + sql, e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	/**
	 * Purges a record from the database
	 * 
	 * @param record The record to purge
	 * @param stmt The optional statement to use for the operation
	 * @throws PrismsRecordException If an error occurs deleting the data
	 */
	public synchronized void purge(ChangeRecord record, Statement stmt)
		throws PrismsRecordException
	{
		boolean closeStmt = false;
		if(stmt == null)
		{
			checkConnection();
			closeStmt = true;
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not create statement", e);
			}
		}
		String sql = "SELECT id FROM " + DBOWNER + "jme3_modification WHERE id=" + record.id;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			if(!rs.next())
			{
				log.warn("Attempted to purge modification " + record
					+ " from database but it no longer exists");
				return;
			}
			dbDeleteMod(record, stmt);
			checkForExpiredData(record, stmt);
		} catch(SQLException e)
		{
			throw new PrismsRecordException(
				"Could not verify presence of modification: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			if(stmt != null && closeStmt)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	void dbDeleteMod(ChangeRecord record, Statement stmt) throws PrismsRecordException
	{
		String sql = "DELETE FROM " + DBOWNER + "prisms_change_record WHERE recordNS="
			+ toSQL(theNamespace) + " AND id=" + record.id;
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not purge modification: SQL=" + sql, e);
		}
	}

	void checkForExpiredData(ChangeRecord record, Statement stmt) throws PrismsRecordException
	{
		if(getRecords(record.majorSubject, stmt).length == 0)
			checkItemForDelete(record.majorSubject, stmt);
		if(record.data1 != null && getRecords(record.data1, stmt).length == 0)
			checkItemForDelete(record.data1, stmt);
		if(record.data2 != null && getRecords(record.data2, stmt).length == 0)
			checkItemForDelete(record.data2, stmt);
		if(record.minorSubject != null && getRecords(record.minorSubject, stmt).length == 0)
			checkItemForDelete(record.minorSubject, stmt);
		if(record.previousValue != null && getRecords(record.previousValue, stmt).length == 0)
			checkItemForDelete(record.minorSubject, stmt);
	}

	/**
	 * Checks the database for references to a particular object
	 * 
	 * @param dbObject The object to check for references to
	 * @param stmt The optional statement to use for the operation
	 * @return Whether the database has any references to the given object
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public long [] getRecords(Object dbObject, Statement stmt) throws PrismsRecordException
	{
		boolean closeStmt = false;
		if(stmt == null)
		{
			checkConnection();
			closeStmt = true;
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not create statement", e);
			}
		}
		long [] ret = new long [0];
		try
		{
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not create statement", e);
			}
			SubjectType [] types = PrismsChange.values();
			for(SubjectType type : types)
			{
				long [] temp = getRecords(type, dbObject, stmt);
				if(temp.length > 0)
					ret = (long []) ArrayUtils.concatP(Long.TYPE, ret, temp);
			}
			types = getAllDomains();
			for(SubjectType type : types)
			{
				long [] temp = getRecords(type, dbObject, stmt);
				if(temp.length > 0)
					ret = (long []) ArrayUtils.concatP(Long.TYPE, ret, temp);
			}
		} finally
		{
			if(stmt != null && closeStmt)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		return ret;
	}

	SubjectType [] getAllDomains() throws PrismsRecordException
	{
		SubjectType [] ret = new SubjectType [] {PrismsChange.center, PrismsChange.autoPurge};
		ret = ArrayUtils.concat(SubjectType.class, ret, thePersister.getAllDomains());
		return ret;
	}

	long [] getRecords(SubjectType type, Object dbObject, Statement stmt)
		throws PrismsRecordException
	{
		String where = "";
		if(type.getMajorType().isInstance(dbObject))
			where += "(subjectType=" + toSQL(type.name()) + " AND majorSubject="
				+ getDataID(dbObject) + ")";
		if(type.getMetadataType1() != null && type.getMetadataType1().isInstance(dbObject))
		{
			if(where.length() > 0)
				where += " OR ";
			where += "(subjectType=" + toSQL(type.name()) + " AND data1=" + getDataID(dbObject)
				+ ")";
		}
		if(type.getMetadataType2() != null && type.getMetadataType2().isInstance(dbObject))
		{
			if(where.length() > 0)
				where += " OR ";
			where += "(subjectType=" + toSQL(type.name()) + " AND data2=" + getDataID(dbObject)
				+ ")";
		}
		for(ChangeType ct : (ChangeType []) type.getChangeTypes().getEnumConstants())
		{
			if(ct.getMinorType() != null && ct.getMinorType().isInstance(dbObject))
			{
				if(where.length() > 0)
					where += " OR ";
				where += "(subjectType=" + toSQL(type.name()) + " AND changeType="
					+ toSQL(ct.name()) + " AND minorSubject=" + getDataID(dbObject) + ")";
			}
			if(ct.isObjectIdentifiable() && ct.getObjectType() != null
				&& ct.getObjectType().isInstance(dbObject))
			{
				if(where.length() > 0)
					where += " OR ";
				where += "(subjectType=" + toSQL(type.name()) + " AND changeType="
					+ toSQL(ct.name()) + " AND preValueID=" + getDataID(dbObject) + ")";
			}
		}
		return getChangeRecords(stmt, null, where, null);
	}

	void checkItemForDelete(Object item, Statement stmt) throws PrismsRecordException
	{
		if(item instanceof String || item instanceof Integer || item instanceof Long
			|| item instanceof Float || item instanceof Double || item instanceof Boolean)
			return;
		if(item instanceof PrismsCenter)
		{
			if(!((PrismsCenter) item).isDeleted())
				return;
			dbDeleteCenter(((PrismsCenter) item).getID(), stmt);
		}
		else if(item instanceof AutoPurger)
			return;
		else
			thePersister.checkItemForDelete(item, stmt);
	}

	void dbDeleteCenter(int id, Statement stmt) throws PrismsRecordException
	{
		String sql = "DELETE FROM " + DBOWNER + "prisms_center_view WHERE recordNS="
			+ toSQL(theNamespace) + " AND id=" + id;
		try
		{
			stmt.execute(sql);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not delete center: SQL=" + sql, e);
		}
	}

	/**
	 * Purges all changes containing an item from the database
	 * 
	 * @param item The item to purge
	 * @param stmt The optional statement to use for the operation
	 * @throws PrismsRecordException If an error occurs purging the changes
	 */
	public void purgeItem(Object item, Statement stmt) throws PrismsRecordException
	{
		boolean closeStmt = stmt == null;
		if(stmt == null)
		{
			checkConnection();
			try
			{
				stmt = theConn.createStatement();
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not create statement", e);
			}
		}
		try
		{
			long [] ids = getRecords(item, stmt);
			if(ids.length == 0)
				return;
			ChangeRecord [] records = getChanges(ids);
			for(ChangeRecord record : records)
				purge(record, stmt);
		} finally
		{
			if(stmt != null && closeStmt)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	AutoPurger dbGetAutoPurger(Statement stmt) throws PrismsRecordException
	{
		AutoPurger ret = new AutoPurger();
		ResultSet rs = null;
		// Get the base parameters
		String sql = "SELECT * FROM " + DBOWNER + "prisms_auto_purge WHERE recordNS="
			+ toSQL(theNamespace);
		try
		{
			rs = stmt.executeQuery(sql);
			if(rs.next())
			{
				Number ec = (Number) rs.getObject("entryCount");
				Number age = (Number) rs.getObject("age");
				ret.setEntryCount(ec == null ? -1 : ec.intValue());
				ret.setAge(age == null ? -1 : age.longValue());
			}
			else
			{
				rs.close();
				rs = null;
				stmt.execute("INSERT INTO " + DBOWNER
					+ "prisms_auto_purge (recordNS, entryCount, age) VALUES ("
					+ toSQL(theNamespace) + ", NULL, NULL)");
			}
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get auto-purge: SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		// Get the excluded users
		sql = "SELECT * FROM " + DBOWNER + "prisms_purge_excl_user WHERE recordNS="
			+ toSQL(theNamespace);
		try
		{
			rs = stmt.executeQuery(sql);
			while(rs.next())
				ret.addExcludeUser(thePersister.getUser(rs.getLong("exclUser")));
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get auto-purge excluded users: SQL=" + sql,
				e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		// Get the excluded types
		sql = "SELECT * FROM " + DBOWNER + "prisms_purge_excl_type WHERE recordNS="
			+ toSQL(theNamespace);
		try
		{
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				String subjectTypeStr = fromSQL(rs.getString("exclSubjectType"));
				String changeTypeStr = fromSQL(rs.getString("exclChangeType"));
				char addChar = rs.getString("exclAdditivity").charAt(0);
				SubjectType subjectType = getType(subjectTypeStr);
				ChangeType changeType = getChangeType(subjectType, changeTypeStr);
				int additivity = addChar == '-' ? -1 : (addChar == '+' ? 1 : 0);
				ret.addExcludeType(new RecordType(subjectType, changeType, additivity));
			}
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get auto-purge excluded types: SQL=" + sql,
				e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		return ret;
	}

	synchronized void dbSetAutoPurger(final Statement stmt, AutoPurger purger, final RecordUser user)
		throws PrismsRecordException
	{
		final AutoPurger dbPurger = dbGetAutoPurger(stmt);
		theAutoPurger = purger;
		String changeMsg = "Updated auto-purger:\n";
		boolean modified = false;
		if(dbPurger.getEntryCount() != purger.getEntryCount())
		{
			changeMsg += "Changed entry count from "
				+ (dbPurger.getEntryCount() >= 0 ? "" + dbPurger.getEntryCount() : "none") + " to "
				+ (purger.getEntryCount() >= 0 ? "" + purger.getEntryCount() : "none") + "\n";
			modified = true;
			persist(user, PrismsChange.autoPurge, PrismsChange.AutoPurgeChange.entryCount, 0,
				dbPurger, null, new Integer(dbPurger.getEntryCount()), null, null);
			dbPurger.setEntryCount(purger.getEntryCount());
		}
		if(dbPurger.getAge() != purger.getAge())
		{
			changeMsg += "Changed age from "
				+ (dbPurger.getAge() >= 0 ? PrismsUtils.printTimeLength(dbPurger.getAge()) : "none")
				+ " to "
				+ (purger.getAge() >= 0 ? PrismsUtils.printTimeLength(purger.getAge()) : "none")
				+ "\n";
			modified = true;
			persist(user, PrismsChange.autoPurge, PrismsChange.AutoPurgeChange.age, 0, dbPurger,
				null, new Long(dbPurger.getAge()), null, null);
			dbPurger.setAge(purger.getAge());
		}
		if(modified)
		{
			log.debug(changeMsg.substring(0, changeMsg.length() - 1));
			String sql = "UPDATE " + DBOWNER + "jme3_auto_purge SET entryCount="
				+ (dbPurger.getEntryCount() < 0 ? "NULL" : "" + dbPurger.getEntryCount())
				+ ", age=" + (dbPurger.getAge() < 0 ? "NULL" : "" + dbPurger.getAge())
				+ " WHERE recordNS=" + toSQL(theNamespace);
			try
			{
				stmt.executeUpdate(sql);
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not update auto-purge: SQL=" + sql, e);
			}
		}
		ArrayUtils.adjust(dbPurger.getExcludeUsers(), purger.getExcludeUsers(),
			new ArrayUtils.DifferenceListenerE<RecordUser, RecordUser, PrismsRecordException>()
			{
				public boolean identity(RecordUser o1, RecordUser o2)
				{
					return o1.getID() == o2.getID();
				}

				public RecordUser added(RecordUser o, int idx, int retIdx)
					throws PrismsRecordException
				{
					log.debug("Excluding user " + o + " from auto-purge");
					persist(user, PrismsChange.autoPurge, PrismsChange.AutoPurgeChange.excludeUser,
						1, dbPurger, null, thePersister.getUser(o.getID()), null, null);
					String sql = "INSERT INTO " + DBOWNER
						+ "jme3_purge_excl_user (recordNS, exclUser) VALUES(" + toSQL(theNamespace)
						+ ", " + o.getID() + ")";
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not add excluded user to auto-purge: SQL=" + sql, e);
					}
					return o;
				}

				public RecordUser removed(RecordUser o, int idx, int incMod, int retIdx)
					throws PrismsRecordException
				{
					log.debug("Re-including user " + o + " in auto-purge");
					persist(user, PrismsChange.autoPurge, PrismsChange.AutoPurgeChange.excludeUser,
						-1, dbPurger, null, thePersister.getUser(o.getID()), null, null);
					String sql = "DELETE FROM " + DBOWNER + "jme3_purge_excl_user WHERE recordNS="
						+ toSQL(theNamespace) + " AND exclUser=" + o.getID();
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not remove excluded user from auto-purge: SQL=" + sql, e);
					}
					return null;
				}

				public RecordUser set(RecordUser o1, int idx1, int incMod, RecordUser o2, int idx2,
					int retIdx)
				{
					return o1;
				}
			});
		ArrayUtils.adjust(dbPurger.getExcludeTypes(), purger.getExcludeTypes(),
			new ArrayUtils.DifferenceListenerE<RecordType, RecordType, PrismsRecordException>()
			{
				public boolean identity(RecordType o1, RecordType o2)
				{
					return o1.equals(o2);
				}

				public RecordType added(RecordType o, int idx, int retIdx)
					throws PrismsRecordException
				{
					log.debug("Excluding modification type " + o + " from auto-purge");
					persist(user, PrismsChange.autoPurge, PrismsChange.AutoPurgeChange.excludeType,
						1, dbPurger, o, null, null, null);
					String sql = "INSERT INTO " + DBOWNER
						+ "jme3_purge_excl_type (recordNS, exclSubjectType, exclChangeType,"
						+ " exclAdditivity) VALUES(" + toSQL(theNamespace) + ", ";
					sql += toSQL(o.subjectType.name()) + ", "
						+ (o.changeType == null ? "NULL" : toSQL(o.changeType.name())) + ", "
						+ toSQL(o.additivity < 0 ? "-" : (o.additivity > 0 ? "+" : "0")) + ")";
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not add excluded type to auto-purge: SQL=" + sql, e);
					}
					return o;
				}

				public RecordType removed(RecordType o, int idx, int incMod, int retIdx)
					throws PrismsRecordException
				{
					log.debug("Re-including modification type " + o + " in auto-purge");
					persist(user, PrismsChange.autoPurge, PrismsChange.AutoPurgeChange.excludeType,
						-1, dbPurger, null, o, null, null);
					String sql = "DELETE FROM " + DBOWNER + "jme3_purge_excl_type WHERE recordNS="
						+ toSQL(theNamespace) + " AND exclSubjectType="
						+ toSQL(o.subjectType.name()) + " AND exclChangeType=";
					sql += (o.changeType == null ? "NULL" : toSQL(o.changeType.name()))
						+ " AND additivity= ";
					sql += toSQL(o.additivity < 0 ? "-" : (o.additivity > 0 ? "+" : "0"));
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not remove excluded type from auto-purge: SQL=" + sql, e);
					}
					return null;
				}

				public RecordType set(RecordType o1, int idx1, int incMod, RecordType o2, int idx2,
					int retIdx)
				{
					return o1;
				}
			});
	}

	// Utility methods

	/**
	 * Gets the next ID for the given table within this center and namespace
	 * 
	 * @param prismsStmt The active statement pointing to the PRISMS records database. Cannot be
	 *        null or closed.
	 * @param prefix The table prefix to use for the query
	 * @param table The name of the table to get the next ID for (including any applicable prefix)
	 * @param column The ID column of the table
	 * @param extStmt The active statement pointing to the database where the actual implementation
	 *        data resides. If this is null it will be assumed that the implementation data resides
	 *        in the same database as the PRISMS records data and will use the prismsStmt for
	 *        implementation-specific queries.
	 * @return The next ID that should be used for an entry in the table
	 * @throws PrismsRecordException If an error occurs deriving the data
	 */
	public long getNextID(Statement prismsStmt, String prefix, String table, String column,
		Statement extStmt) throws PrismsRecordException
	{
		if(extStmt == null)
			extStmt = prismsStmt;
		ResultSet rs = null;
		String sql = null;
		try
		{
			long centerMin = ((long) theCenterID) * theCenterIDRange;
			long centerMax = centerMin + theCenterIDRange - 1;

			sql = "SELECT DISTINCT nextID FROM " + DBOWNER
				+ "prisms_auto_increment WHERE recordNS=" + toSQL(theNamespace) + " AND tableName="
				+ toSQL(table);
			rs = prismsStmt.executeQuery(sql);

			long ret;
			if(rs.next())
				ret = rs.getLong(1);
			else
				ret = -1;
			rs.close();
			if(ret < 0)
			{
				sql = "SELECT MAX(" + column + ") FROM " + prefix + table + " WHERE " + column
					+ ">=" + centerMin + " AND " + column + " <=" + centerMax;
				rs = extStmt.executeQuery(sql);
				if(rs.next())
				{
					ret = rs.getLong(1);
					if(ret < centerMin || ret > centerMax)
						ret = centerMin;
					else
						ret++;
				}
				else
					ret = centerMin;
				if(ret > centerMax)
					throw new PrismsRecordException("All " + table + " ids are used!");
				// update the db
				sql = "INSERT INTO " + DBOWNER + "prisms_auto_increment (recordNS, tableName,"
					+ " nextID) VALUES(" + toSQL(theNamespace) + ", " + toSQL(table) + ", "
					+ centerMin + ")";
				prismsStmt.execute(sql);
			}
			sql = null;
			long nextTry = nextAvailableID(extStmt, prefix + table, column, ret + 1);
			if(nextTry > centerMax)
				nextTry = nextAvailableID(extStmt, prefix + table, column, centerMin);
			if(nextTry == ret || nextTry > centerMax)
				throw new PrismsRecordException("All " + table + " ids are used!");

			sql = "UPDATE " + DBOWNER + "prisms_auto_increment SET nextID = " + nextTry
				+ " WHERE recordNS=" + toSQL(theNamespace) + " AND tableName = " + toSQL(table);
			prismsStmt.executeUpdate(sql);
			return ret;
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get next ID: SQL=" + sql, e);
		} finally
		{
			try
			{
				if(rs != null)
					rs.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			}
		}
	}

	private long nextAvailableID(Statement stmt, String table, String column, long start)
		throws PrismsRecordException
	{
		String sql = "SELECT DISTINCT " + column + " FROM " + table + " WHERE " + column + ">="
			+ start + " ORDER BY " + column;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				long tempID = rs.getLong(1);
				if(start != tempID)
					break;
				start++;
			}
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get next available table ID: SQL=" + sql, e);
		} finally
		{
			try
			{
				if(rs != null)
					rs.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			}
		}
		return start;
	}

	/**
	 * Gets the next ID for a table whose value is not dependent on the center
	 * 
	 * @param stmt The statement pointing to the given table
	 * @param tableName The table to get the next ID for
	 * @param column The ID column in the table
	 * @return The next ID to use for an entry in the table
	 * @throws SQLException If an error occurs retrieving the data
	 */
	public int getNextIntID(Statement stmt, String tableName, String column) throws SQLException
	{
		int id = 0;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery("SELECT DISTINCT " + column + " FROM " + tableName
				+ " ORDER BY " + column);
			while(rs.next())
			{
				int tempID = rs.getInt(1);
				if(id != tempID)
					break;
				id++;
			}
		} finally
		{
			try
			{
				if(rs != null)
					rs.close();
			} catch(SQLException e)
			{
				e.printStackTrace();
				log.error("Connection error", e);
			}
		}
		return id;
	}

	/**
	 * Gets the maximum length of data for a field
	 * 
	 * @param tableName The name of the table
	 * @param fieldName The name of the field to get the length for
	 * @return The maximum length that data in the given field can be
	 * @throws PrismsRecordException If an error occurs retrieving the information
	 */
	public int getFieldSize(String tableName, String fieldName) throws PrismsRecordException
	{
		checkConnection();
		return getFieldSize(theConn, DBOWNER + tableName, fieldName);
	}

	/**
	 * Gets the maximum length of data for a field
	 * 
	 * @param conn The connection to get information from
	 * @param tableName The name of the table
	 * @param fieldName The name of the field to get the length for
	 * @return The maximum length that data in the given field can be
	 * @throws PrismsRecordException If an error occurs retrieving the information, such as the
	 *         table or field not existing
	 */
	public static int getFieldSize(java.sql.Connection conn, String tableName, String fieldName)
		throws PrismsRecordException
	{
		if(isOracle(conn))
			throw new PrismsRecordException(
				"Accessing Oracle metadata is unsafe--cannot get field size");
		ResultSet rs = null;
		try
		{
			String schema = null;
			tableName = tableName.toUpperCase();
			int dotIdx = tableName.indexOf('.');
			if(dotIdx >= 0)
			{
				schema = tableName.substring(0, dotIdx).toUpperCase();
				tableName = tableName.substring(dotIdx + 1).toUpperCase();
			}
			rs = conn.getMetaData().getColumns(null, schema, tableName, null);
			while(rs.next())
			{
				String name = rs.getString("COLUMN_NAME");
				if(name.equalsIgnoreCase(fieldName))
					return rs.getInt("COLUMN_SIZE");
			}

			throw new PrismsRecordException("No such field " + fieldName + " in table "
				+ (schema != null ? schema + "." : "") + tableName);

		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get field length of " + tableName + "."
				+ fieldName, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
	}

	/**
	 * @param conn The connection to test
	 * @return Whether the connection is to an oracle database
	 */
	public static boolean isOracle(java.sql.Connection conn)
	{
		return conn.getClass().getName().toLowerCase().contains("ora");
	}

	private Boolean _isOracle = null;

	/**
	 * @return Whether this data source is using an oracle database or not
	 */
	protected boolean isOracle()
	{
		if(_isOracle == null)
			_isOracle = new Boolean(isOracle(theConn));
		return _isOracle.booleanValue();
	}

	/**
	 * @param time the java time to format
	 * @return the sql expression of the java time
	 */
	public String formatDate(long time)
	{
		if(time <= 0)
			return "NULL";

		String ret = new java.sql.Timestamp(time).toString();

		if(isOracle())
			ret = "TO_TIMESTAMP('" + ret + "', 'YYYY-MM-DD HH24:MI:SS.FF3')";
		// ret = "TO_DATE('" + ret.substring(0, ret.length()-4) + "', 'YYYY-MM-DD HH24:MI:SS')";
		else
			ret = "'" + ret + "'";

		return ret;
	}

	private static final String EMPTY = "*-{EMPTY}-*";

	/**
	 * Converts a java string to a properly-escaped SQL string. This method properly handles single
	 * quotes and empty strings (oracle treats empty strings as null).
	 * 
	 * @param string The java string
	 * @return The SQL to send to the DBMS
	 */
	public static String toSQL(String string)
	{
		if(string == null)
			return "NULL";
		if(string.length() == 0)
			string = EMPTY;
		return "'" + string.replaceAll("'", "''") + "'";
	}

	/**
	 * Converts a DBMS-returned string into a java string
	 * 
	 * @param dbString The DBMS-returned string
	 * @return The java string to use
	 */
	public static String fromSQL(String dbString)
	{
		if(dbString == null)
			return null;
		else if(dbString.equals(EMPTY))
			return "";
		else
			return dbString;
	}

	/**
	 * Converts a boolean to a string. Some DBMS's don't have a boolean type, so we have to use
	 * CHAR(1)
	 * 
	 * @param b The boolean to convert
	 * @return The converted boolean
	 */
	public static String boolToSQL(boolean b)
	{
		return b ? "'t'" : "'f'";
	}

	/**
	 * Converts an SQL boolean to a java boolean. Some DBMS's don't have a boolean type, so we have
	 * to use CHAR(1)
	 * 
	 * @param dbBool The database boolean
	 * @return The java boolean to use
	 */
	public boolean boolFromSQL(String dbBool)
	{
		return "t".equalsIgnoreCase(dbBool);
	}

	private static final String XOR_KEY = "JmE3_sYnC_xOr_EnCrYpT_kEy_769465";

	/**
	 * Protects a password so that it is not stored in clear text
	 * 
	 * @param password The password to protect
	 * @return The protected password to store in the database
	 */
	public static String protect(String password)
	{
		return xorEncStr(password, XOR_KEY);
	}

	/**
	 * Recovers a password from its protected form
	 * 
	 * @param protectedPassword The protected password to recover the password from
	 * @return The plain password
	 */
	public static String unprotect(String protectedPassword)
	{
		return xorEncStr(protectedPassword, XOR_KEY);
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
	private static String xorEncStr(String toEnc, String encKey)
	{
		if(toEnc == null)
			return null;
		int t = 0;
		int encKeyI = 0;

		while(t < encKey.length())
		{
			encKeyI += encKey.charAt(t);
			t += 1;
		}
		return xorEnc(toEnc, encKeyI);
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
				int c = (a ^ encKey) % 256;
				char d = (char) c;
				tog = tog + d;
				t++;
			}
		}
		return tog;
	}

	static boolean equal(Object o1, Object o2)
	{
		return o1 == null ? o2 == null : o1.equals(o2);
	}

	void checkConnection() throws PrismsRecordException
	{
		try
		{
			if(theConn == null || theConn.isClosed())
			{
				theConn = theFactory.getConnection(theConnEl, null);
				DBOWNER = theFactory.getTablePrefix(theConn, theConnEl, null);
				if(DBOWNER.length() > 0 || isOracle())
					DBOWNER = DBOWNER + "V3";
			}
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not renew connection ", e);
		}
	}
}
