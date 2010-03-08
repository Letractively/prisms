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

import static prisms.records.PrismsChanges.*;

public class RecordKeeper
{
	static final Logger log = Logger.getLogger(RecordKeeper.class);

	/**
	 * The range of IDS that may exist in a given PRISMS center
	 */
	protected int theCenterIDRange = 1000000000;

	final String theNamespace;

	private org.dom4j.Element theConnEl;

	private prisms.arch.PersisterFactory theFactory;

	private java.sql.Connection theConn;

	String DBOWNER;

	RecordPersister thePersister;

	private int theCenterID;

	private AutoPurger theAutoPurger;

	private SyncRecord theSyncRecord;

	public RecordKeeper(String namespace, org.dom4j.Element connEl,
		prisms.arch.PersisterFactory factory, RecordPersister persister)
	{
		theNamespace = namespace;
		theConnEl = connEl;
		theFactory = factory;
		thePersister = persister;
		try
		{
			doStartup();
		} catch(PrismsRecordException e)
		{
			log.error("Could not perform startup operations", e);
		}
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
			theCenterID = selfCenter.getID();
		else
		{
			selfCenter = new PrismsCenter(theNamespace, 0, "Here");
			selfCenter.setID((int) (Math.random() * theCenterIDRange));
			putCenter(selfCenter, null);
			log.debug("Created rules center with ID " + selfCenter.getID());
			theCenterID = selfCenter.getID();
		}
	}

	public String getNamespace()
	{
		return theNamespace;
	}

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

	PrismsCenter [] getCenters() throws PrismsRecordException
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
				PrismsCenter pc = new PrismsCenter(theNamespace, rs.getInt("id"), rs
					.getString("name"));
				Number centerID = (Number) rs.getObject("id");
				if(centerID != null)
					pc.setCenterID(centerID.intValue());
				pc.setName(rs.getString("name"));
				pc.setServerURL(rs.getString("url"));
				pc.setServerUserName(rs.getString("serverUserName"));
				pc.setServerPassword(rs.getString("serverPassword"));
				Number syncFreq = (Number) rs.getObject("syncFrequency");
				if(syncFreq != null)
					pc.setServerSyncFrequency(syncFreq.longValue());
				clientUsers.add((Number) rs.getObject("clientUser"));
				Number changeSaveTime = (Number) rs.getObject("changeSaveTime");
				if(changeSaveTime != null)
					pc.setChangeSaveTime(changeSaveTime.longValue());
				java.sql.Timestamp time;
				time = rs.getTimestamp("lastImport");
				if(time != null)
					pc.setLastImport(time.getTime());
				time = rs.getTimestamp("lastExport");
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
		String sql = "SELECT * FROM " + DBOWNER + "prisms_center_view WHERE centerID=" + id
			+ " AND recordNS=" + toSQL(theNamespace);
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
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			pc = new PrismsCenter(theNamespace, id, rs.getString("name"));
			pc.setName(rs.getString("name"));
			pc.setServerURL(rs.getString("url"));
			pc.setServerUserName(rs.getString("serverUserName"));
			pc.setServerPassword(rs.getString("serverPassword"));
			Number syncFreq = (Number) rs.getObject("syncFrequency");
			if(syncFreq != null)
				pc.setServerSyncFrequency(syncFreq.longValue());
			clientUserID = (Number) rs.getObject("clientUser");
			Number changeSaveTime = (Number) rs.getObject("changeSaveTime");
			if(changeSaveTime != null)
				pc.setChangeSaveTime(changeSaveTime.longValue());
			java.sql.Timestamp time;
			time = rs.getTimestamp("lastImport");
			if(time != null)
				pc.setLastImport(time.getTime());
			time = rs.getTimestamp("lastExport");
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
			PrismsCenter dbCenter = getCenter(theCenterID, stmt);
			String sql;
			if(center.getCenterID() >= 0 && (dbCenter == null || dbCenter.getCenterID() < 0))
			{
				sql = "INSERT INTO " + DBOWNER + "prisms_center (id) VALUES (" + center.getID()
					+ ")";
				try
				{
					stmt = theConn.createStatement();
					stmt.execute(sql);
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Could not insert center: SQL=" + sql, e);
				}
			}
			if(dbCenter == null)
			{
				if(user == null && center.getID() != 0)
				{
					log.warn("Cannot insert PRISMS center view--no user");
					return;
				}
				sql = "INSERT INTO " + DBOWNER
					+ "prisms_center_view (id, centerID, recordNS, name,"
					+ " url, serverUserName, serverPassword, syncFrequency, clientUser,"
					+ " changeSaveTime, lastImport, lastExport)" + " VALUES(" + center.getID()
					+ ", ";
				sql += (center.getCenterID() >= 0 ? "" + center.getCenterID() : "NULL");
				sql += ", " + toSQL(theNamespace) + ", " + toSQL(center.getName()) + ", "
					+ toSQL(center.getServerURL()) + ", " + toSQL(center.getServerUserName())
					+ ", " + toSQL(center.getServerPassword()) + ", ";
				sql += (center.getServerSyncFrequency() > 0 ? "" + center.getServerSyncFrequency()
					: "NULL")
					+ ", ";
				sql += (center.getClientUser() != null ? "" + center.getClientUser().getID()
					: "NULL")
					+ ", ";
				sql += (center.getChangeSaveTime() > 0 ? "" + center.getChangeSaveTime() : "NULL")
					+ ")";
				try
				{
					stmt.execute(sql);
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Could not insert center: SQL=" + sql, e);
				}
				persist(user, PrismsChanges.center, null, 1, center, null, null, null, null);
			}
			else
			{
				String changeMsg = "Updated rules center " + dbCenter + ":\n";
				boolean modified = false;
				boolean centerIDOnly = false;
				if(dbCenter.getCenterID() < 0 && center.getCenterID() >= 0)
				{
					changeMsg += "Center ID set to " + center.getCenterID() + "\n";
					modified = true;
					centerIDOnly = true;
					// No modification here--user-transparent
					dbCenter.setCenterID(center.getCenterID());
				}
				if(!dbCenter.getName().equals(center.getName()))
				{
					changeMsg += "Changed name from " + dbCenter.getName() + " to "
						+ center.getName() + "\n";
					modified = true;
					centerIDOnly = false;
					persist(user, PrismsChanges.center, CenterChange.name, 0, dbCenter, null,
						dbCenter.getName(), null, null);
					dbCenter.setName(center.getName());
				}
				if(!equal(dbCenter.getServerURL(), center.getServerURL()))
				{
					changeMsg += "Changed URL from " + dbCenter.getServerURL() + " to "
						+ center.getServerURL() + "\n";
					modified = true;
					centerIDOnly = false;
					persist(user, PrismsChanges.center, CenterChange.url, 0, dbCenter, null,
						dbCenter.getServerURL(), null, null);
					dbCenter.setServerURL(center.getServerURL());
				}
				if(!equal(dbCenter.getServerUserName(), center.getServerUserName()))
				{
					changeMsg += "Changed server user name from " + dbCenter.getServerUserName()
						+ " to " + center.getServerUserName() + "\n";
					modified = true;
					centerIDOnly = false;
					persist(user, PrismsChanges.center, CenterChange.serverUserName, 0, dbCenter,
						null, dbCenter.getServerUserName(), null, null);
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
					centerIDOnly = false;
					persist(user, PrismsChanges.center, CenterChange.serverPassword, 0, dbCenter,
						null, dbCenter.getServerPassword(), null, null);
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
					centerIDOnly = false;
					persist(user, PrismsChanges.center, CenterChange.syncFrequency, 0, dbCenter,
						null, new Long(dbCenter.getServerSyncFrequency()), null, null);
					dbCenter.setServerSyncFrequency(center.getServerSyncFrequency());
				}
				else if(!equal(dbCenter.getClientUser(), center.getClientUser()))
				{
					changeMsg += "Changed client user from "
						+ (dbCenter.getClientUser() == null ? "none" : dbCenter.getClientUser()
							.getName())
						+ " to "
						+ (center.getClientUser() == null ? "none" : center.getClientUser()
							.getName()) + "\n";
					modified = true;
					centerIDOnly = false;
					persist(user, PrismsChanges.center, CenterChange.clientUser, 0, dbCenter, null,
						dbCenter.getClientUser(), null, null);
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
					centerIDOnly = false;
					persist(user, PrismsChanges.center, CenterChange.changeSaveTime, 0, dbCenter,
						null, new Long(dbCenter.getChangeSaveTime()), null, null);
					dbCenter.setChangeSaveTime(center.getChangeSaveTime());
				}

				if(dbCenter.isDeleted() && (!center.isDeleted() || center == dbCenter))
				{
					changeMsg += "Re-creating center";
					persist(user, PrismsChanges.center, null, 1, dbCenter, null, null, null, null);
					modified = true;
					centerIDOnly = false;
				}

				if(modified)
				{
					if(!centerIDOnly && center.getID() != 0 && user == null)
					{
						log.warn("Cannot modify a rules center without a user");
						return;
					}
					log.debug(changeMsg.substring(0, changeMsg.length() - 1));
					sql = "UPDATE " + DBOWNER + "prisms_center_view SET centerID="
						+ dbCenter.getCenterID() + ", name=" + toSQL(dbCenter.getName()) + ", url="
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
							: "NULL") + ", deleted=" + boolToSQL(false) + " WHERE id="
						+ dbCenter.getID();
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

	public long [] getChangeIDs() throws PrismsRecordException
	{
		return getChangeRecords(null, null, null, "changeTime DESC");
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
		sql += " WHERE changeNS=" + toSQL(theNamespace);
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

	public long [] getHistory(Object historyItem) throws PrismsRecordException
	{
		long itemID = getDataID(historyItem);
		String where = "(majorSubject=" + itemID + " OR changeData1=" + itemID + " OR changeData2="
			+ itemID + ")";
		SubjectType [] changeTypes = thePersister.getHistoryDomains(historyItem);
		where += " AND subjectType IN (";
		for(int i = 0; i < changeTypes.length; i++)
		{
			if(i > 0)
				where += ", ";
			where += toSQL(changeTypes[i].toString());
		}
		where += ")";
		return getChangeRecords(null, null, where, "changeTime DESC");
	}

	public long [] getSuccessors(ChangeRecord change) throws PrismsRecordException
	{
		String where = "majorSubject=" + getDataID(change.majorSubject);
		if(change.minorSubject != null)
			where += " AND minorSubject = " + thePersister.getID(change.minorSubject);
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
		return getChangeRecords(null, null, where, "changeTime");
	}

	public long [] getSyncChanges(SyncRecord syncRecord) throws PrismsRecordException
	{
		return getChangeRecords(null, "INNER JOIN " + DBOWNER
			+ "prisms_sync_assoc ON changeRecord=id", "syncAttempt=" + syncRecord.getID(),
			"changeTime DESC");
	}

	public long [] sortChangeIDs(long [] ids, HistorySorter sorter) throws PrismsRecordException
	{
		if(ids.length <= 1)
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
		if(sorter != null && sorter.getSortCount() > 0)
		{
			order = "";
			for(int sc = 0; sc < sorter.getSortCount(); sc++)
			{
				if(sc > 0)
					order += ", ";
				order += sorter.getField(sc).toString()
					+ (sorter.isAscending(sc) ? " ASC" : " DESC");
			}
		}
		else
			order = "modTime DESC";
		return getChangeRecords(null, null, where, order);
	}

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
		String sql = "SELECT * FROM " + DBOWNER + "prisms_change_record  WHERE id IN (";
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
						template.previousValue = rs.getString("shortFieldValue");
						if(template.previousValue == null)
						{
							java.sql.Clob clob = rs.getClob("longFieldValue");
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
				throw new PrismsRecordException("Could not get modifications", e);
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
		for(PrismsChanges ch : PrismsChanges.values())
			if(ch.name().equals(typeName))
				return ch;
		return thePersister.getType(typeName);
	}

	long getDataID(Object obj) throws PrismsRecordException
	{
		if(obj instanceof AutoPurger)
			return 0;
		return thePersister.getID(obj);
	}

	ChangeData getChangeData(SubjectType subjectType, ChangeType changeType, long majorSubjectID,
		Number minorSubjectID, Number data1ID, Number data2ID, Number preValueID)
		throws PrismsRecordException
	{
		if(subjectType instanceof PrismsChanges)
		{
			PrismsChanges pc = (PrismsChanges) subjectType;
			switch(pc)
			{
			case center:
				PrismsCenter ctr = getCenter((int) majorSubjectID, null);
				ChangeData ret = new ChangeData(ctr, null, null, null, null);
				switch((PrismsChanges.CenterChange) changeType)
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
				switch((PrismsChanges.AutoPurgeChange) changeType)
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
		String sql = "SELECT changeSaveTime, lastExport FROM " + DBOWNER
			+ "prisms_center_view WHERE recordNS=" + toSQL(theNamespace);
		checkConnection();
		try
		{
			stmt = theConn.createStatement();
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				long lastSync;
				java.sql.Timestamp time = rs.getTimestamp("lastExport");
				lastSync = time == null ? -1 : time.getTime();
				long saveTime = rs.getLong("changeSaveTime");
				if(lastSync < nowTime - saveTime)
					lastSync = nowTime - saveTime;
				if(ret > lastSync)
					ret = lastSync;
			}
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not check centers for purge times", e);
		}
		return ret;
	}

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

	public void setAutoPurger(AutoPurger purger, RecordUser user) throws PrismsRecordException
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
	public int previewAutoPurge(final AutoPurger purger) throws PrismsRecordException
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

	public void setSyncRecord(SyncRecord record)
	{
		theSyncRecord = record;
	}

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
			id = getNextID(stmt, "prisms_change_record", "id", null);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not persist " + subjectType + " change");
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

	public synchronized void persist(ChangeRecord record) throws PrismsRecordException
	{
		Statement stmt = null;
		java.sql.PreparedStatement pStmt = null;
		checkConnection();
		String sql = null;
		try
		{
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
			pStmt.setString(6, record.type.changeType == null ? "NULL" : record.type.changeType
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
					pStmt.setCharacterStream(12, new java.io.StringReader(serialized));
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
				stmt = theConn.createStatement();
				sql = "INSERT INTO " + DBOWNER + "prisms_sync_assoc (syncRecord, changeRecord)"
					+ " VALUES (" + theSyncRecord.getID() + ", " + record.id + ")";
				stmt.execute(sql);
			}
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not persist " + record.type.subjectType
				+ " change");
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

	public void purge(ChangeRecord record) throws PrismsRecordException
	{
		// TODO
	}

	public boolean hasRecords(Object dbObject) throws PrismsRecordException
	{
		// TODO
		return true;
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
				ret.addExcludeUser(rs.getLong("exclUser"));
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

	void dbSetAutoPurger(final Statement stmt, AutoPurger purger, final RecordUser user)
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
			persist(user, autoPurge, AutoPurgeChange.entryCount, 0, dbPurger, null, new Integer(
				dbPurger.getEntryCount()), null, null);
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
			persist(user, autoPurge, AutoPurgeChange.age, 0, dbPurger, null, new Long(dbPurger
				.getAge()), null, null);
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
		Long [] oldUsers = new Long [dbPurger.getExcludeUsers().length];
		for(int i = 0; i < oldUsers.length; i++)
			oldUsers[i] = new Long(dbPurger.getExcludeUsers()[i]);
		Long [] newUsers = new Long [purger.getExcludeUsers().length];
		for(int i = 0; i < newUsers.length; i++)
			newUsers[i] = new Long(purger.getExcludeUsers()[i]);
		ArrayUtils.adjust(oldUsers, oldUsers,
			new ArrayUtils.DifferenceListenerE<Long, Long, PrismsRecordException>()
			{
				public boolean identity(Long o1, Long o2)
				{
					return o1.equals(o2);
				}

				public Long added(Long o, int idx, int retIdx) throws PrismsRecordException
				{
					log.debug("Excluding user " + o + " from auto-purge");
					persist(user, autoPurge, AutoPurgeChange.excludeUser, 1, dbPurger, null,
						thePersister.getUser(o.longValue()), null, null);
					String sql = "INSERT INTO " + DBOWNER
						+ "jme3_purge_excl_user (recordNS, exclUser) VALUES(" + toSQL(theNamespace)
						+ ", " + o.longValue() + ")";
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not add excluded user to auto-purge: SQL=" + sql, e);
					}
					return o;
				}

				public Long removed(Long o, int idx, int incMod, int retIdx)
					throws PrismsRecordException
				{
					log.debug("Re-including user " + o + " in auto-purge");
					persist(user, autoPurge, AutoPurgeChange.excludeUser, -1, dbPurger, null,
						thePersister.getUser(o.longValue()), null, null);
					String sql = "DELETE FROM " + DBOWNER + "jme3_purge_excl_user WHERE recordNS="
						+ toSQL(theNamespace) + " AND exclUser=" + o.longValue();
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						log.error("Could not remove excluded user from auto-purge: SQL=" + sql, e);
					}
					return null;
				}

				public Long set(Long o1, int idx1, int incMod, Long o2, int idx2, int retIdx)
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
					persist(user, autoPurge, AutoPurgeChange.excludeType, 1, dbPurger, o, null,
						null, null);
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
					persist(user, autoPurge, AutoPurgeChange.excludeType, -1, dbPurger, null, o,
						null, null);
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
	public long getNextID(Statement prismsStmt, String table, String column, Statement extStmt)
		throws SQLException, PrismsRecordException
	{
		if(extStmt == null)
			extStmt = prismsStmt;
		ResultSet rs = null;
		String sql;
		try
		{
			long centerMin = ((long) theCenterID) * theCenterIDRange;
			long centerMax = centerMin + theCenterIDRange - 1;

			rs = prismsStmt.executeQuery("SELECT DISTINCT nextID FROM " + DBOWNER
				+ "prisms_auto_increment WHERE recordNS=" + toSQL(theNamespace) + " AND tableName="
				+ toSQL(table));

			long ret;
			if(rs.next())
				ret = rs.getLong(1);
			else
				ret = -1;
			rs.close();
			if(ret < 0)
			{
				sql = "SELECT MAX(" + column + ") FROM " + DBOWNER + table + " WHERE " + column
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
					+ " nextID) VALUES(" + toSQL(theNamespace) + toSQL(table) + ", " + centerMin
					+ ")";
				prismsStmt.execute(sql);
			}
			long nextTry = nextAvailableID(extStmt, table, column, ret + 1);
			if(nextTry > centerMax)
				nextTry = nextAvailableID(extStmt, table, column, centerMin);
			if(nextTry == ret || nextTry > centerMax)
				throw new PrismsRecordException("All " + table + " ids are used!");

			sql = "UPDATE " + DBOWNER + "prisms_auto_increment SET nextID = " + nextTry
				+ " WHERE recordNS=" + toSQL(theNamespace) + " AND tableName = " + toSQL(table);
			prismsStmt.executeUpdate(sql);
			return ret;
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
		throws SQLException
	{
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery("SELECT DISTINCT " + column + " FROM " + DBOWNER + table
				+ " WHERE " + column + ">=" + start + " ORDER BY " + column);
			while(rs.next())
			{
				long tempID = rs.getLong(1);
				if(start != tempID)
					break;
				start++;
			}
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

	public int getNextIntID(Statement stmt, String tableName, String column) throws SQLException
	{
		int id = 0;
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery("SELECT DISTINCT " + column + " FROM " + DBOWNER + tableName
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

	private Boolean _isOracle = null;

	/**
	 * @return Whether this data source is using an oracle database or not
	 */
	protected boolean isOracle()
	{
		if(_isOracle == null)
			_isOracle = new Boolean(theConn.getClass().getName().toLowerCase().contains("ora"));
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

	void checkConnection() throws IllegalStateException
	{
		try
		{
			if(theConn == null || theConn.isClosed())
			{
				theConn = theFactory.getConnection(theConnEl, null);
				DBOWNER = theFactory.getTablePrefix(theConn, theConnEl, null);
			}
		} catch(SQLException e)
		{
			throw new IllegalStateException("Could not renew connection ", e);
		}
	}
}
