/*
 * DBRecordKeeper.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import static prisms.util.DBUtils.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.log4j.Logger;

import prisms.arch.PrismsException;
import prisms.arch.ds.Transactor;
import prisms.arch.ds.Transactor.TransactionOperation;
import prisms.records.RecordPersister.ChangeData;
import prisms.util.*;

/** Keeps persistent records of changes to a data set */
public class DBRecordKeeper implements RecordKeeper
{
	private static final class DBChangeSearch extends
		DBPreparedSearch<ChangeSearch, ChangeField, PrismsRecordException>
	{
		protected DBChangeSearch(Transactor<PrismsRecordException> transactor, String sql,
			Search srch, Sorter<ChangeField> sorter) throws PrismsRecordException
		{
			super(transactor, sql, srch, sorter, ChangeSearch.class);
		}

		@Override
		protected synchronized long [] execute(Object... params) throws PrismsRecordException
		{
			return super.execute(params);
		}

		@Override
		protected void dispose()
		{
			super.dispose();
		}

		@Override
		protected void setParameter(int type, Object param, int index) throws PrismsRecordException
		{
			if(type == java.sql.Types.CHAR && param instanceof Integer
				&& getParentSearch(index) instanceof ChangeSearch.AdditivitySearch)
			{
				int val = ((Integer) param).intValue();
				if(val < 0)
					param = "-";
				else if(val > 0)
					param = "+";
				else
					param = "0";
			}
			else if(type == java.sql.Types.TIMESTAMP && param == null
				|| (param instanceof Long && ((Long) param).longValue() <= 0))
				param = Long.valueOf(365L * 24 * 60 * 60 * 1000);
			super.setParameter(type, param, index);
		}

		@Override
		protected void addSqlTypes(ChangeSearch search, IntList types)
		{
			switch(search.getType())
			{
			case id:
				ChangeSearch.IDRange ids = (ChangeSearch.IDRange) search;
				if(ids.getMinID() == null)
					types.add(java.sql.Types.NUMERIC);
				if(ids.getMaxID() == null)
					types.add(java.sql.Types.NUMERIC);
				break;
			case majorSubjectRange:
				ChangeSearch.MajorSubjectRange msr = (ChangeSearch.MajorSubjectRange) search;
				if(msr.getMinID() == null)
					types.add(java.sql.Types.NUMERIC);
				if(msr.getMaxID() == null)
					types.add(java.sql.Types.NUMERIC);
				break;
			case subjectCenter:
				ChangeSearch.SubjectCenterSearch scs = (ChangeSearch.SubjectCenterSearch) search;
				if(scs.getSubjectCenter() == null)
					types.add(java.sql.Types.INTEGER);
				break;
			case time:
				ChangeSearch.ChangeTimeSearch cts = (ChangeSearch.ChangeTimeSearch) search;
				if(cts.changeTime == null)
					types.add(java.sql.Types.TIMESTAMP);
				break;
			case user:
				ChangeSearch.ChangeUserSearch cus = (ChangeSearch.ChangeUserSearch) search;
				if(cus.getUser() == null)
					types.add(java.sql.Types.NUMERIC);
				break;
			case subjectType:
				ChangeSearch.SubjectTypeSearch sts = (ChangeSearch.SubjectTypeSearch) search;
				if(sts.getSubjectType() == null)
					types.add(java.sql.Types.VARCHAR);
				break;
			case changeType:
				ChangeSearch.ChangeTypeSearch chType = (ChangeSearch.ChangeTypeSearch) search;
				if(!chType.isSpecified())
					types.add(java.sql.Types.VARCHAR);
				break;
			case add:
				ChangeSearch.AdditivitySearch as = (ChangeSearch.AdditivitySearch) search;
				if(as.getAdditivity() == null)
					types.add(java.sql.Types.CHAR);
				break;
			case field:
				ChangeSearch.ChangeFieldSearch cfs = (ChangeSearch.ChangeFieldSearch) search;
				if(!cfs.isFieldIDSpecified())
					types.add(java.sql.Types.NUMERIC);
				break;
			case syncRecord:
				ChangeSearch.SyncRecordSearch srs = (ChangeSearch.SyncRecordSearch) search;
				if(srs.isSyncRecordSet())
				{
					if(srs.getSyncRecordID() == null)
						types.add(java.sql.Types.INTEGER);
				}
				else if(srs.getTimeOp() != null)
				{
					if(srs.getTime() == null)
						types.add(java.sql.Types.TIMESTAMP);
				}
				else if(srs.getSyncError() != null)
				{}
				else if(srs.isChangeErrorSet())
				{
					if(srs.getChangeError() == null)
						types.add(java.sql.Types.CHAR);
				}
				else if(srs.isSyncImportSet())
				{
					if(srs.isSyncImport() == null)
						types.add(java.sql.Types.CHAR);
				}
				else
					throw new IllegalStateException("Unrecognized sync record search type: " + srs);
				break;
			case localOnly:
				break;
			}
		}

		@Override
		protected void addParamTypes(ChangeSearch search, Collection<Class<?>> types)
		{
			RecordUtils.addParamTypes(search, types);
		}
	}

	/**
	 * A subject type that substitutes for a real subject type in a change record that cannot be
	 * retrieved fully
	 */
	public static class ErrorSubjectType implements SubjectType
	{
		private final String theName;

		/**
		 * The wrapped actual subject type--may be null if this could not be retrieved
		 */
		public final SubjectType theSubjectType;

		/**
		 * The wrapped actual change type if this could be retrieved, or a phony change type if it
		 * could not be retrieved, or null if the change type for the record was null
		 */
		public final ChangeType theChangeType;

		/**
		 * Creates an ErrorSubjectType for a subject/change type that are not recognized by the
		 * persister
		 * 
		 * @param subjectType The name of the subject type
		 * @param changeType The name of the change type
		 */
		public ErrorSubjectType(final String subjectType, final String changeType)
		{
			theName = subjectType;
			theSubjectType = null;
			if(changeType != null)
			{
				theChangeType = new ChangeType()
				{
					public String name()
					{
						return changeType;
					}

					public Class<?> getMinorType()
					{
						return Object.class;
					}

					public Class<?> getObjectType()
					{
						return null;
					}

					public boolean isObjectIdentifiable()
					{
						return false;
					}

					public String toString(int additivity)
					{
						String ret = theSubjectType + " " + name();
						if(additivity > 0)
							ret += " added";
						else if(additivity < 0)
							ret += " removed";
						else
							ret += " changed";
						return ret;
					}

					public String toString(int additivity, Object majorSubject, Object minorSubject)
					{
						return toString(additivity);
					}

					public String toString(int additivity, Object majorSubject,
						Object minorSubject, Object before, Object after)
					{
						return toString(additivity, majorSubject, minorSubject);
					}
				};
			}
			else
				theChangeType = null;
		}

		/**
		 * Creates an ErrorSubjectType that wraps an actual subject/change type pair
		 * 
		 * @param wrap The subject type to wrap
		 * @param ct The change type for the error
		 */
		public ErrorSubjectType(SubjectType wrap, ChangeType ct)
		{
			theName = wrap.name();
			theSubjectType = wrap;
			theChangeType = ct;
		}

		public String name()
		{
			return theName;
		}

		public Class<?> getMajorType()
		{
			return Object.class;
		}

		public Class<?> getMetadataType1()
		{
			return null;
		}

		public Class<?> getMetadataType2()
		{
			return null;
		}

		public Class<? extends Enum<? extends ChangeType>> getChangeTypes()
		{
			return null;
		}

		@Override
		public String toString()
		{
			return theName;
		}
	}

	static final Logger log = Logger.getLogger(DBRecordKeeper.class);

	final String theNamespace;

	Transactor<PrismsRecordException> theTransactor;

	private java.sql.PreparedStatement theChangeInserter;

	java.sql.PreparedStatement theCertSetter;

	final prisms.arch.ds.IDGenerator theIDs;

	RecordPersister thePersister;

	int theLocalPriority;

	private AutoPurger theAutoPurger;

	private long theLastChange;

	/**
	 * Creates a record keeper
	 * 
	 * @param namespace The namespace that this keeper is to use to separate it from other record
	 *        keepers using the same database.
	 * @param connEl The XML element to use to obtain a database connection
	 * @param factory The connection factory to use to obtain a database connection
	 * @param ids The ID generator to get IDs from for this record keeper
	 */
	public DBRecordKeeper(String namespace, prisms.arch.PrismsConfig connEl,
		prisms.arch.ConnectionFactory factory, prisms.arch.ds.IDGenerator ids)
	{
		theNamespace = namespace;
		theTransactor = factory.getConnection(connEl, null,
			new Transactor.Thrower<PrismsRecordException>()
			{
				public void error(String message) throws PrismsRecordException
				{
					throw new PrismsRecordException(message);
				}

				public void error(String message, Throwable cause) throws PrismsRecordException
				{
					throw new PrismsRecordException(message, cause);
				}
			});
		theTransactor.addReconnectListener(new Transactor.ReconnectListener()
		{
			public void reconnected(boolean initial)
			{
				connectionUpdated();
			}

			public void released()
			{
			}
		});
		theIDs = ids;
	}

	/** @return This record keeper's connection transactor */
	public Transactor<PrismsRecordException> getTransactor()
	{
		return theTransactor;
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
		theTransactor.checkConnected();
		if(theChangeInserter == null)
			prepareStatements();
		PrismsCenter selfCenter = getCenter(0, null);
		if(selfCenter == null)
		{
			selfCenter = new PrismsCenter(0, "Here");
			selfCenter.setPriority(100);
			selfCenter.setNamespace(theNamespace);
			selfCenter.setCenterID(theIDs.getCenterID());
			theLocalPriority = selfCenter.getPriority();
			ignoreUser = true;
			try
			{
				putCenter(selfCenter, null);
			} finally
			{
				ignoreUser = false;
			}
			log.debug("Created data center with ID " + selfCenter.getCenterID());
		}
		else if(selfCenter.getCenterID() != theIDs.getCenterID())
			installRecordKeeper(selfCenter);
		else
			theLocalPriority = selfCenter.getPriority();
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

	/** @return This record keeper's ID generator */
	public prisms.arch.ds.IDGenerator getIDs()
	{
		return theIDs;
	}

	public int getCenterID()
	{
		return theIDs.getCenterID();
	}

	public int getLocalPriority()
	{
		return theLocalPriority;
	}

	/**
	 * Installs this record keeper into the database
	 * 
	 * @param selfCenter The center that was "Here" in the old installation--may be null
	 * @throws PrismsRecordException If an error occurs installing this record keeper
	 */
	private void installRecordKeeper(final PrismsCenter selfCenter) throws PrismsRecordException
	{
		/* This is a new installation with copied data. We need to assert our independence.
		 * We need to move the center that was "Here" when the data was copied to a different ID
		 * under the name "Installation".  Then we create the "Here" center like normal. Then we
		 * update the synchronization records to point to "Here", since we know that the data is
		 * synchronized with other centers just as it was when the data was copied.  We also
		 * add a sync record with the Installation server since we have all the data it had
		 * when it was copied.
		 */
		theTransactor.performTransaction(
			new Transactor.TransactionOperation<PrismsRecordException>()
			{
				public Object run(Statement stmt) throws PrismsRecordException
				{
					ResultSet rs = null;
					String sql;
					ignoreUser = true;
					PrismsCenter sc = selfCenter;
					try
					{
						// Make a new center to represent the old "Here" center
						PrismsCenter oldHere = null;
						if(sc != null)
						{
							for(PrismsCenter center : getCenters())
								if(center.getName().endsWith("Installation"))
								{
									if(center.getName().equals("Installation"))
									{
										center.setName("Old Installation");
										putCenter(center, null);
									}
									else
									{
										java.util.regex.Matcher match = java.util.regex.Pattern
											.compile("Old\\((\\d*)\\) Installation").matcher(
												center.getName());
										if(match.matches())
										{
											int index = Integer.parseInt(match.group(1));
											center
												.setName("Old (" + (index + 1) + ") Installation");
											putCenter(center, null);
										}
									}
								}
							oldHere = new PrismsCenter("Installation");
							oldHere.setCenterID(sc.getCenterID());
							putCenter(oldHere, null);
						}
						else
							sc = new PrismsCenter(0, "Here");
						sc.setPriority(100);

						// Adjust the center ID of the real "Here"
						selfCenter.setCenterID(theIDs.getCenterID());
						putCenter(selfCenter, null);
						theLocalPriority = sc.getPriority();
						log.debug("Created data center with ID " + selfCenter.getCenterID());

						if(oldHere != null)
						{
							sql = "SELECT MAX(changeTime) FROM " + theTransactor.getTablePrefix()
								+ "prisms_change_record WHERE recordNS=" + toSQL(theNamespace);
							rs = stmt.executeQuery(sql);
							if(rs.next())
							{
								java.sql.Timestamp time = rs.getTimestamp(1);
								if(time != null)
								{
									SyncRecord record = new SyncRecord(oldHere,
										SyncRecord.Type.AUTOMATIC, time.getTime(), true);
									putSyncRecord(record);
								}
							}
							rs.close();
							rs = null;
						}
					} catch(SQLException e)
					{
						throw new PrismsRecordException("Could not install record keeper", e);
					} finally
					{
						ignoreUser = false;
						if(rs != null)
							try
							{
								rs.close();
							} catch(SQLException e)
							{
								log.error("Connection error", e);
							}
					}
					return null;
				}
			}, "Could not install record keeper");
	}

	public PrismsCenter [] getCenters() throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = "SELECT * FROM " + theTransactor.getTablePrefix()
			+ "prisms_center_view LEFT OUTER JOIN " + theTransactor.getTablePrefix()
			+ "prisms_center ON centerID=" + theTransactor.getTablePrefix()
			+ "prisms_center.id WHERE recordNS=" + toSQL(theNamespace) + " AND deleted="
			+ boolToSql(false);
		try
		{
			stmt = theTransactor.getConnection().createStatement();
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
				java.sql.Blob certs = rs.getBlob("serverCerts");
				if(certs != null)
				{
					java.io.InputStream stream = null;
					try
					{
						stream = certs.getBinaryStream();
						pc.setCertificates(java.security.cert.CertificateFactory
							.getInstance("X.509").generateCertificates(stream)
							.toArray(new java.security.cert.X509Certificate [0]));
					} catch(java.security.cert.CertificateException e)
					{
						log.error(
							"Could not read stored server certificates for center " + pc.getName(),
							e);
					} finally
					{
						if(stream != null)
							try
							{
								stream.close();
							} catch(java.io.IOException e)
							{
								log.error("Could not close blob input stream", e);
							}
						certs.free();
					}
				}
				pc.setServerUserName(rs.getString("serverUserName"));
				pc.setServerPassword(DBUtils.unprotect(rs.getString("serverPassword")));
				Number syncFreq = (Number) rs.getObject("syncFrequency");
				if(syncFreq != null)
					pc.setServerSyncFrequency(syncFreq.longValue());
				clientUsers.add((Number) rs.getObject("clientUser"));
				Number changeSaveTime = (Number) rs.getObject("changeSaveTime");
				if(changeSaveTime != null)
					pc.setChangeSaveTime(changeSaveTime.longValue());
				pc.setPriority(rs.getInt("syncPriority"));
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
			if(thePersister != null)
				for(int i = 0; i < ret.size(); i++)
				{
					if(clientUsers.get(i) != null)
						ret.get(i).setClientUser(
							thePersister.getUser(clientUsers.get(i).longValue()));
				}
			return ret.toArray(new PrismsCenter [ret.size()]);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get data centers: SQL=" + sql, e);
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

	/**
	 * Retrieves a center by ID
	 * 
	 * @param id The ID of the center to get
	 * @param stmt The statement to use to retrieve the data (may be null to use a temporary
	 *        statement)
	 * @return The center with the given ID, or null if no such center exists
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public PrismsCenter getCenter(int id, Statement stmt) throws PrismsRecordException
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
				stmt = theTransactor.getConnection().createStatement();
			}
			sql = "SELECT * FROM " + theTransactor.getTablePrefix()
				+ "prisms_center_view WHERE id=" + id + " AND recordNS=" + toSQL(theNamespace);
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
			java.sql.Blob certs = rs.getBlob("serverCerts");
			if(certs != null)
				try
				{
					pc.setCertificates(java.security.cert.CertificateFactory.getInstance("X.509")
						.generateCertificates(certs.getBinaryStream())
						.toArray(new java.security.cert.X509Certificate [0]));
				} catch(java.security.cert.CertificateException e)
				{
					log.error(
						"Could not read stored server certificates for center " + pc.getName(), e);
				}
			pc.setServerUserName(rs.getString("serverUserName"));
			pc.setServerPassword(DBUtils.unprotect(rs.getString("serverPassword")));
			Number syncFreq = (Number) rs.getObject("syncFrequency");
			if(syncFreq != null)
				pc.setServerSyncFrequency(syncFreq.longValue());
			clientUserID = (Number) rs.getObject("clientUser");
			Number changeSaveTime = (Number) rs.getObject("changeSaveTime");
			if(changeSaveTime != null)
				pc.setChangeSaveTime(changeSaveTime.longValue());
			pc.setPriority(rs.getInt("syncPriority"));
			java.sql.Timestamp time;
			time = rs.getTimestamp("lastImportSync");
			if(time != null)
				pc.setLastImport(time.getTime());
			time = rs.getTimestamp("lastExportSync");
			if(time != null)
				pc.setLastExport(time.getTime());
			pc.setDeleted(boolFromSql(rs.getString("deleted")));
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get data center for ID " + id + ": SQL="
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
		if(clientUserID != null && thePersister != null)
			pc.setClientUser(thePersister.getUser(clientUserID.longValue()));
		return pc;
	}

	boolean ignoreUser = false;

	public void putCenter(final PrismsCenter center, final RecordsTransaction trans)
		throws PrismsRecordException
	{
		if(center.getNamespace() != null)
		{
			if(!center.getNamespace().equals(theNamespace))
				throw new PrismsRecordException("Center " + center
					+ " does not belong to this record keeper");
		}
		else
			center.setNamespace(theNamespace);
		if(trans != null)
			trans.setTime();

		theTransactor.performTransaction(new TransactionOperation<PrismsRecordException>()
		{
			public Object run(Statement stmt) throws PrismsRecordException
			{
				if(center.getID() < 0)
					try
					{
						center.setID(theIDs.getNextIntID(stmt, "prisms_center_view",
							theTransactor.getTablePrefix(), "id", "recordNS=" + toSQL(theNamespace)));
					} catch(PrismsException e)
					{
						throw new PrismsRecordException("Could not get next center ID", e);
					}
				PrismsCenter dbCenter = getCenter(center.getID(), stmt);
				String sql;
				if(center.getCenterID() >= 0
					&& (dbCenter == null || dbCenter.getCenterID() != center.getCenterID()))
				{
					boolean hasCenter;
					sql = "SELECT id FROM " + theTransactor.getTablePrefix()
						+ "prisms_center WHERE id=" + center.getCenterID();
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
						sql = "INSERT INTO " + theTransactor.getTablePrefix()
							+ "prisms_center (id) VALUES (" + center.getCenterID() + ")";
						try
						{
							stmt.execute(sql);
						} catch(SQLException e)
						{
							throw new PrismsRecordException("Could not insert center: SQL=" + sql,
								e);
						}
					}
				}
				if(dbCenter == null)
				{
					if((trans == null || trans.getUser() == null) && center.getID() != 0
						&& !ignoreUser)
					{
						log.warn("Cannot insert PRISMS center view--no user");
						return null;
					}
					log.debug("Adding center " + center);
					sql = "INSERT INTO " + theTransactor.getTablePrefix() + "prisms_center_view"
						+ " (id, centerID, recordNS, name, url, serverUserName, serverPassword,"
						+ " syncFrequency, clientUser, changeSaveTime, syncPriority,"
						+ " lastImportSync, lastExportSync, deleted) VALUES(" + center.getID()
						+ ", ";
					sql += (center.getCenterID() >= 0 ? "" + center.getCenterID() : "NULL");
					sql += ", " + toSQL(theNamespace) + ", " + toSQL(center.getName()) + ", "
						+ toSQL(center.getServerURL()) + ", " + toSQL(center.getServerUserName())
						+ ", " + toSQL(DBUtils.protect(center.getServerPassword())) + ", ";
					sql += (center.getServerSyncFrequency() > 0 ? ""
						+ center.getServerSyncFrequency() : "NULL")
						+ ", ";
					sql += (center.getClientUser() != null ? "" + center.getClientUser().getID()
						: "NULL") + ", ";
					sql += (center.getChangeSaveTime() > 0 ? "" + center.getChangeSaveTime()
						: "NULL") + ", " + center.getPriority() + ", ";
					sql += (center.getLastImport() > 0 ? formatDate(center.getLastImport())
						: "NULL")
						+ ", "
						+ (center.getLastExport() > 0 ? formatDate(center.getLastExport()) : "NULL")
						+ ", " + boolToSql(center.isDeleted()) + ")";
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						throw new PrismsRecordException("Could not insert center: SQL=" + sql, e);
					}
					addModification(trans, PrismsChange.center, null, 1, center, null, null, null,
						null);
					if(center.getCertificates() != null)
						try
						{
							java.sql.Blob blob = theTransactor.getConnection().createBlob();
							java.io.OutputStream stream = blob.setBinaryStream(1);
							try
							{
								for(int c = 0; c < center.getCertificates().length; c++)
									for(byte enc : center.getCertificates()[c].getEncoded())
										stream.write(enc);
								stream.close();
							} catch(java.security.cert.CertificateEncodingException e)
							{
								log.error("Could not encode server certificates for center "
									+ center, e);
								blob.free();
								blob = null;
							} catch(java.io.IOException e)
							{
								log.error("Could not write server certificates for center "
									+ center, e);
								blob.free();
								blob = null;
							}
							if(blob != null)
							{
								theCertSetter.setBlob(1, blob);
								theCertSetter.setInt(2, center.getID());
								theCertSetter.executeUpdate();
							}
						} catch(SQLException e)
						{
							log.error("Could not store server certificates for center " + center, e);
						}
				}
				else
				{
					String changeMsg = "Updated center " + dbCenter + ":\n";
					boolean modified = false;
					boolean blobModified = false;
					if(dbCenter.getCenterID() != center.getCenterID())
					{
						changeMsg += "Center ID set to " + center.getCenterID() + "\n";
						modified = true;
						// No modification here--user-transparent
						dbCenter.setCenterID(center.getCenterID());
					}
					if(dbCenter.getPriority() != center.getPriority())
					{
						changeMsg += "Priority changed from " + dbCenter.getPriority() + " to "
							+ center.getPriority();
						modified = true;
						dbCenter.setPriority(center.getPriority());
					}
					if(!dbCenter.getName().equals(center.getName()))
					{
						changeMsg += "Changed name from " + dbCenter.getName() + " to "
							+ center.getName() + "\n";
						modified = true;
						if(!ignoreUser)
						{
							if(trans == null || trans.getUser() == null)
								throw new PrismsRecordException(
									"Cannot modify a center without a user");
							addModification(trans, PrismsChange.center,
								PrismsChange.CenterChange.name, 0, dbCenter, null,
								dbCenter.getName(), null, null);
						}
						dbCenter.setName(center.getName());
					}
					if(!equal(dbCenter.getServerURL(), center.getServerURL()))
					{
						changeMsg += "Changed URL from " + dbCenter.getServerURL() + " to "
							+ center.getServerURL() + "\n";
						modified = true;
						if(!ignoreUser)
						{
							if(trans == null || trans.getUser() == null)
								throw new PrismsRecordException(
									"Cannot modify a center without a user");
							addModification(trans, PrismsChange.center,
								PrismsChange.CenterChange.url, 0, dbCenter, null,
								dbCenter.getServerURL(), null, null);
						}
						dbCenter.setServerURL(center.getServerURL());
					}
					if(!ArrayUtils.equals(dbCenter.getCertificates(), center.getCertificates()))
					{
						blobModified = true;
						changeMsg += "Changed server certificates from ";
						if(dbCenter.getCertificates() == null
							|| dbCenter.getCertificates().length == 0)
							changeMsg += "none";
						else
							changeMsg += prisms.ui.CertificateSerializer.getCertSubjectName(
								dbCenter.getCertificates()[0], false);
						changeMsg += " to ";
						if(center.getCertificates() == null || center.getCertificates().length == 0)
							changeMsg += "none";
						else
							changeMsg += prisms.ui.CertificateSerializer.getCertSubjectName(
								center.getCertificates()[0], false);
						changeMsg += "\n";
						try
						{
							byte [] bytes;
							if(center.getCertificates() != null)
								bytes = getCertBytes(center);
							else
								bytes = null;
							if(bytes != null)
							{
								theCertSetter.setBinaryStream(1, new java.io.ByteArrayInputStream(
									bytes), bytes.length);
								theCertSetter.setInt(2, center.getID());
								theCertSetter.executeUpdate();
							}
							else
							{
								theCertSetter.setNull(1, java.sql.Types.BLOB);
								theCertSetter.setInt(2, center.getID());
								theCertSetter.executeUpdate();
							}
						} catch(SQLException e)
						{
							log.error("Could not set server certificates for center " + center, e);
						}
						if(!ignoreUser)
						{
							if(trans == null || trans.getUser() == null)
								throw new PrismsRecordException(
									"Cannot modify a center without a user");
							addModification(trans, PrismsChange.center,
								PrismsChange.CenterChange.serverCerts, 0, dbCenter, null,
								dbCenter.getCertificates(), null, null);
						}
					}
					if(!equal(dbCenter.getServerUserName(), center.getServerUserName()))
					{
						changeMsg += "Changed server user name from "
							+ dbCenter.getServerUserName() + " to " + center.getServerUserName()
							+ "\n";
						modified = true;
						if(!ignoreUser)
						{
							if(trans == null || trans.getUser() == null)
								throw new PrismsRecordException(
									"Cannot modify a center without a user");
							addModification(trans, PrismsChange.center,
								PrismsChange.CenterChange.serverUserName, 0, dbCenter, null,
								dbCenter.getServerUserName(), null, null);
						}
						dbCenter.setServerUserName(center.getServerUserName());
					}
					if(!equal(dbCenter.getServerPassword(), center.getServerPassword()))
					{
						StringBuilder msg = new StringBuilder("Changed server password from ");
						if(dbCenter.getServerPassword() == null)
							msg.append("null");
						else
							for(int c = 0; c < dbCenter.getServerPassword().length(); c++)
								msg.append('*');
						changeMsg += " to ";
						if(center.getServerPassword() == null)
							msg.append("null");
						else
							for(int c = 0; c < center.getServerPassword().length(); c++)
								msg.append('*');
						msg.append('\n');
						modified = true;
						if(!ignoreUser)
						{
							if(trans == null || trans.getUser() == null)
								throw new PrismsRecordException(
									"Cannot modify a center without a user");
							addModification(trans, PrismsChange.center,
								PrismsChange.CenterChange.serverPassword, 0, dbCenter, null,
								dbCenter.getServerPassword(), null, null);
						}
						dbCenter.setServerPassword(center.getServerPassword());
						changeMsg += msg.toString();
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
						if(!ignoreUser)
						{
							if(trans == null || trans.getUser() == null)
								throw new PrismsRecordException(
									"Cannot modify a center without a user");
							addModification(trans, PrismsChange.center,
								PrismsChange.CenterChange.syncFrequency, 0, dbCenter, null,
								Long.valueOf(dbCenter.getServerSyncFrequency()), null, null);
						}
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
						if(!ignoreUser)
						{
							if(trans == null || trans.getUser() == null)
								throw new PrismsRecordException(
									"Cannot modify a center without a user");
							addModification(trans, PrismsChange.center,
								PrismsChange.CenterChange.clientUser, 0, dbCenter, null,
								dbCenter.getClientUser(), null, null);
						}
						dbCenter.setClientUser(center.getClientUser());
					}
					if(dbCenter.getChangeSaveTime() != center.getChangeSaveTime())
					{
						changeMsg += "Changed modification save time from "
							+ (dbCenter.getChangeSaveTime() >= 0 ? PrismsUtils
								.printTimeLength(dbCenter.getChangeSaveTime()) : "none")
							+ " to "
							+ (center.getChangeSaveTime() >= 0 ? PrismsUtils.printTimeLength(center
								.getChangeSaveTime()) : "none") + "\n";
						modified = true;
						if(!ignoreUser)
						{
							if(trans == null || trans.getUser() == null)
								throw new PrismsRecordException(
									"Cannot modify a center without a user");
							addModification(trans, PrismsChange.center,
								PrismsChange.CenterChange.changeSaveTime, 0, dbCenter, null,
								Long.valueOf(dbCenter.getChangeSaveTime()), null, null);
						}
						dbCenter.setChangeSaveTime(center.getChangeSaveTime());
					}
					if(dbCenter.getLastImport() != center.getLastImport())
					{
						changeMsg += "Change last import time from "
							+ (dbCenter.getLastImport() > 0 ? PrismsUtils.print(dbCenter
								.getLastImport()) : "none")
							+ " to "
							+ (center.getLastImport() > 0 ? PrismsUtils.print(center
								.getLastImport()) : "none");
						modified = true;
						dbCenter.setLastImport(center.getLastImport());
					}
					if(dbCenter.getLastExport() != center.getLastExport())
					{
						changeMsg += "Change last export time from "
							+ (dbCenter.getLastExport() > 0 ? PrismsUtils.print(dbCenter
								.getLastExport()) : "none")
							+ " to "
							+ (center.getLastExport() > 0 ? PrismsUtils.print(dbCenter
								.getLastExport()) : "none");
						modified = true;
						dbCenter.setLastExport(center.getLastExport());
					}

					if(dbCenter.isDeleted() && !center.isDeleted())
					{
						changeMsg += "Re-creating center";
						if(!ignoreUser)
						{
							if(trans == null || trans.getUser() == null)
								throw new PrismsRecordException("Cannot modify a data center"
									+ " without a user");
							addModification(trans, PrismsChange.center, null, 1, dbCenter, null,
								null, null, null);
						}
						dbCenter.setDeleted(false);
						modified = true;
					}

					if(modified || blobModified)
						log.debug(changeMsg.substring(0, changeMsg.length() - 1));

					if(modified)
					{
						sql = "UPDATE " + theTransactor.getTablePrefix()
							+ "prisms_center_view SET centerID="
							+ (dbCenter.getCenterID() < 0 ? "NULL" : "" + dbCenter.getCenterID())
							+ ", name=" + toSQL(dbCenter.getName()) + ", url="
							+ toSQL(dbCenter.getServerURL()) + ", serverUserName="
							+ toSQL(dbCenter.getServerUserName()) + ", serverPassword="
							+ toSQL(DBUtils.protect(dbCenter.getServerPassword()));
						sql += ", syncFrequency="
							+ (dbCenter.getServerSyncFrequency() > 0 ? ""
								+ dbCenter.getServerSyncFrequency() : "NULL");
						sql += ", clientUser="
							+ (dbCenter.getClientUser() == null ? "NULL" : ""
								+ dbCenter.getClientUser().getID());
						sql += ", changeSaveTime="
							+ (dbCenter.getChangeSaveTime() > 0 ? "" + dbCenter.getChangeSaveTime()
								: "NULL") + ", deleted=" + boolToSql(dbCenter.isDeleted());
						sql += ", syncPriority=" + dbCenter.getPriority();
						sql += ", lastImportSync=" + formatDate(dbCenter.getLastImport())
							+ ", lastExportSync=" + formatDate(dbCenter.getLastExport());
						sql += " WHERE id=" + dbCenter.getID();

						try
						{
							stmt.executeUpdate(sql);
						} catch(SQLException e)
						{
							throw new PrismsRecordException("Could not update center: SQL=" + sql,
								e);
						}
					}
				}
				return null;
			}
		}, "Could not add/modify center " + center);
	}

	byte [] getCertBytes(PrismsCenter center)
	{
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		try
		{
			for(int c = 0; c < center.getCertificates().length; c++)
				for(byte enc : center.getCertificates()[c].getEncoded())
					bytes.write(enc & 0xff);
		} catch(java.security.cert.CertificateEncodingException e)
		{
			log.error("Could not encode server certificates for center " + center, e);
			return null;
		}
		return bytes.toByteArray();
	}

	public void removeCenter(final PrismsCenter center, final RecordsTransaction trans)
		throws PrismsRecordException
	{
		theTransactor.performTransaction(new TransactionOperation<PrismsRecordException>()
		{
			public Object run(Statement stmt) throws PrismsRecordException
			{
				String sql = "UPDATE " + theTransactor.getTablePrefix()
					+ "prisms_center_view SET deleted=" + boolToSql(true) + " WHERE id="
					+ center.getID();
				try
				{
					stmt.executeUpdate(sql);
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Could not delete center: SQL=" + sql, e);
				}
				center.setDeleted(true);
				addModification(trans, PrismsChange.center, null, -1, center, null, null, null,
					null);
				return null;
			}
		}, "Could not remove center " + center);
	}

	void addModification(RecordsTransaction trans, PrismsChange subjectType,
		prisms.records.ChangeType changeType, int add, Object majorSubject, Object minorSubject,
		Object previousValue, Object data1, Object data2) throws PrismsRecordException
	{
		if(trans == null)
			return;
		prisms.records.ChangeRecord record;
		record = persist(trans, subjectType, changeType, add, majorSubject, minorSubject,
			previousValue, data1, data2);
		if(trans.getRecord() != null)
		{
			try
			{
				associate(record, trans.getRecord(), false);
			} catch(prisms.records.PrismsRecordException e)
			{
				log.error("Could not associate change record with sync record", e);
			}
		}
	}

	public SyncRecord [] getSyncRecords(PrismsCenter center, Boolean isImport)
		throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = "SELECT * FROM " + theTransactor.getTablePrefix()
			+ "prisms_sync_record WHERE recordNS=" + toSQL(theNamespace) + " AND syncCenter="
			+ center.getID();
		if(isImport != null)
			sql += " AND isImport=" + boolToSql(isImport.booleanValue());
		sql += " ORDER BY syncTime DESC";
		ArrayList<SyncRecord> ret = new ArrayList<SyncRecord>();
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				SyncRecord sr = new SyncRecord(rs.getInt("id"), center, SyncRecord.Type.byName(rs
					.getString("syncType")), rs.getTimestamp("syncTime").getTime(),
					boolFromSql(rs.getString("isImport")));
				sr.setParallelID(rs.getInt("parallelID"));
				sr.setSyncError(rs.getString("syncError"));
				ret.add(sr);
			}
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
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		return ret.toArray(new SyncRecord [ret.size()]);
	}

	public void putSyncRecord(final SyncRecord record) throws PrismsRecordException
	{
		theTransactor.performTransaction(new TransactionOperation<PrismsRecordException>()
		{
			public Object run(Statement stmt) throws PrismsRecordException
			{
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
						changeMsg += "Changed time from "
							+ PrismsUtils.print(dbRecord.getSyncTime()) + " to "
							+ PrismsUtils.print(record.getSyncTime()) + "\n";
						modified = true;
						dbRecord.setSyncTime(record.getSyncTime());
					}
					if(dbRecord.getParallelID() != record.getParallelID())
					{
						changeMsg += "Changed parallel ID from "
							+ (dbRecord.getParallelID() < 0 ? "none" : ""
								+ dbRecord.getParallelID()) + " to "
							+ (record.getParallelID() < 0 ? "none" : "" + record.getParallelID())
							+ "\n";
						modified = true;
						dbRecord.setParallelID(record.getParallelID());
					}
					if(!equal(dbRecord.getSyncError(), record.getSyncError()))
					{
						changeMsg += "Changed error from "
							+ (dbRecord.getSyncError() != null ? dbRecord.getSyncError() : "none")
							+ " to "
							+ (record.getSyncError() != null ? record.getSyncError() : "none")
							+ "\n";
						modified = true;
						dbRecord.setSyncError(record.getSyncError());
					}

					if(modified)
					{
						log.debug(changeMsg.substring(0, changeMsg.length() - 1));
						sql = "UPDATE "
							+ theTransactor.getTablePrefix()
							+ "prisms_sync_record SET syncType="
							+ toSQL(dbRecord.getSyncType().toString())
							+ ", isImport="
							+ boolToSql(dbRecord.isImport())
							+ ", syncTime="
							+ formatDate(dbRecord.getSyncTime())
							+ ", parallelID="
							+ (dbRecord.getParallelID() < 0 ? "NULL" : ""
								+ dbRecord.getParallelID()) + ", syncError="
							+ toSQL(dbRecord.getSyncError()) + " WHERE id=" + dbRecord.getID();
						try
						{
							stmt.executeUpdate(sql);
						} catch(SQLException e)
						{
							throw new PrismsRecordException("Could not update sync record: SQL="
								+ sql, e);
						}
					}
				}
				else
				{
					log.debug("Sync record " + record + " inserted");
					try
					{
						record.setID(theIDs.getNextIntID(stmt, "prisms_sync_record",
							theTransactor.getTablePrefix(), "id", "recordNS=" + toSQL(theNamespace)));
					} catch(PrismsException e)
					{
						throw new PrismsRecordException("Could not get next record ID", e);
					}
					sql = "INSERT INTO " + theTransactor.getTablePrefix()
						+ "prisms_sync_record (id, syncCenter, recordNS,"
						+ " syncType, isImport, syncTime, parallelID, syncError) VALUES ("
						+ record.getID() + ", " + record.getCenter().getID() + ", "
						+ toSQL(theNamespace) + ", " + toSQL(record.getSyncType().toString())
						+ ", " + boolToSql(record.isImport()) + ", "
						+ formatDate(record.getSyncTime()) + ", "
						+ (record.getParallelID() < 0 ? "NULL" : "" + record.getParallelID())
						+ ", " + toSQL(record.getSyncError()) + ")";
					try
					{
						stmt.execute(sql);
					} catch(SQLException e)
					{
						throw new PrismsRecordException("Could not insert sync record: SQL=" + sql,
							e);
					}
				}
				return null;
			}
		}, "Could not add/modify sync record " + record);
	}

	SyncRecord getSyncRecord(PrismsCenter center, int id, Statement stmt)
		throws PrismsRecordException
	{
		ResultSet rs = null;
		String sql = "SELECT * FROM " + theTransactor.getTablePrefix()
			+ "prisms_sync_record WHERE recordNS=" + toSQL(theNamespace) + " AND id=" + id;
		try
		{
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			SyncRecord sr = new SyncRecord(rs.getInt("id"), center, SyncRecord.Type.byName(rs
				.getString("syncType")), rs.getTimestamp("syncTime").getTime(),
				boolFromSql(rs.getString("isImport")));
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

	public void removeSyncRecord(SyncRecord record) throws PrismsRecordException
	{
		log.debug("Removed sync record " + record);
		String sql = "DELETE FROM " + theTransactor.getTablePrefix()
			+ "prisms_sync_record WHERE recordNS=" + toSQL(theNamespace) + " AND id="
			+ record.getID();
		Statement stmt = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
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

	public int [] getAllCenterIDs() throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = null;
		IntList ret = new IntList();
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			sql = "SELECT DISTINCT centerID FROM " + theTransactor.getTablePrefix()
				+ "prisms_purge_record WHERE recordNS=" + toSQL(theNamespace);
			rs = stmt.executeQuery(sql);
			while(rs.next())
				ret.add(rs.getInt(1));
			rs.close();
			rs = null;
			sql = "SELECT DISTINCT subjectCenter FROM " + theTransactor.getTablePrefix()
				+ "prisms_purge_record WHERE recordNS=" + toSQL(theNamespace);
			rs = stmt.executeQuery(sql);
			while(rs.next())
				if(!ret.contains(rs.getInt(1)))
					ret.add(rs.getInt(1));
			rs.close();
			rs = null;
			sql = "SELECT id, subjectCenter FROM " + theTransactor.getTablePrefix()
				+ "prisms_change_record WHERE recordNS=" + toSQL(theNamespace);
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				int centerID = RecordUtils.getCenterID(rs.getLong(1));
				if(!ret.contains(centerID))
					ret.add(centerID);
				centerID = rs.getInt(2);
				if(!ret.contains(centerID))
					ret.add(centerID);
			}
			rs.close();
			rs = null;
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not retrieve all center IDs: SQL=" + sql, e);
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
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Connection error", e);
				}
		}
		return ret.toArray();
	}

	public long getLatestChange(int centerID, int subjectCenter) throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = null;
		long ret = -1;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			sql = "SELECT MAX(changeTime) FROM " + theTransactor.getTablePrefix()
				+ "prisms_change_record WHERE recordNS=" + toSQL(theNamespace) + " AND id>="
				+ (centerID * 1L * RecordUtils.theCenterIDRange) + " AND id<"
				+ ((centerID + 1L) * RecordUtils.theCenterIDRange) + " AND subjectCenter="
				+ subjectCenter;
			rs = stmt.executeQuery(sql);
			if(rs.next())
			{
				java.sql.Timestamp ts = rs.getTimestamp(1);
				if(ts != null)
					ret = ts.getTime();
			}
			rs.close();
			rs = null;
			sql = "SELECT latestChange FROM " + theTransactor.getTablePrefix()
				+ "prisms_purge_record WHERE recordNS=" + toSQL(theNamespace) + " AND centerID="
				+ centerID + " AND subjectCenter=" + subjectCenter;
			rs = stmt.executeQuery(sql);
			if(rs.next())
			{
				long latestChange = rs.getTimestamp(1).getTime();
				if(latestChange > ret)
					ret = latestChange;
			}
			rs.close();
			rs = null;
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not retrieve all center IDs: SQL=" + sql, e);
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
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Connection error", e);
				}
		}
		return ret;
	}

	public void setLatestChange(int centerID, int subjectCenter, long time)
		throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			sql = "SELECT MAX(changeTime) FROM " + theTransactor.getTablePrefix()
				+ "prisms_change_record WHERE recordNS=" + toSQL(theNamespace) + " AND id>="
				+ (centerID * 1L * RecordUtils.theCenterIDRange) + " AND id<"
				+ ((centerID + 1L) * RecordUtils.theCenterIDRange) + " AND subjectCenter="
				+ subjectCenter;
			rs = stmt.executeQuery(sql);
			if(rs.next())
			{
				java.sql.Timestamp ts = rs.getTimestamp(1);
				if(ts != null && ts.getTime() >= time)
					return;
			}
			rs.close();
			rs = null;
			sql = "SELECT latestChange FROM " + theTransactor.getTablePrefix()
				+ "prisms_purge_record WHERE recordNS=" + toSQL(theNamespace) + " AND centerID="
				+ centerID + " AND subjectCenter=" + subjectCenter;
			rs = stmt.executeQuery(sql);
			boolean hasEntry = rs.next();
			if(hasEntry)
			{
				java.sql.Timestamp ts = rs.getTimestamp(1);
				hasEntry = ts != null;
				if(hasEntry && ts.getTime() >= time)
					return;
			}
			rs.close();
			rs = null;
			if(hasEntry)
			{
				sql = "UPDATE " + theTransactor.getTablePrefix()
					+ "prisms_purge_record SET latestChange=" + formatDate(time)
					+ " WHERE recordNS=" + toSQL(theNamespace) + " AND centerID=" + centerID
					+ " AND subjectCenter=" + subjectCenter;
				stmt.executeUpdate(sql);
			}
			else
			{
				sql = "INSERT INTO " + theTransactor.getTablePrefix()
					+ "prisms_purge_record (recordNS, centerID," + "subjectCenter, latestChange)"
					+ " VALUES (" + toSQL(theNamespace) + ", " + centerID + ", " + subjectCenter
					+ ", " + formatDate(time) + ")";
				stmt.execute(sql);
			}
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not retrieve all center IDs: SQL=" + sql, e);
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
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Connection error", e);
				}
		}
	}

	public Search getHistorySearch(Object historyItem) throws PrismsRecordException
	{
		return RecordUtils.getHistorySearch(historyItem, thePersister);
	}

	public Search getSuccessorSearch(ChangeRecord change) throws PrismsRecordException
	{
		return RecordUtils.getSuccessorSearch(change, thePersister);
	}

	public long [] search(Search search, Sorter<ChangeField> sorter) throws PrismsRecordException
	{
		String sql = createQuery(search, sorter, false) + " ORDER BY " + getOrder(sorter);
		Statement stmt = null;
		ResultSet rs = null;
		LongList ret = new LongList();
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			while(rs.next())
				ret.add(rs.getLong(1));
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not query changes: SQL=" + sql, e);
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
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
		}
		return ret.toArray();
	}

	public PreparedSearch<ChangeField> prepare(Search search, Sorter<ChangeField> sorter)
		throws PrismsRecordException
	{
		String sql = createQuery(search, sorter, true) + " ORDER BY " + getOrder(sorter);
		return new DBChangeSearch(theTransactor, sql, search, sorter);
	}

	public long [] execute(PreparedSearch<ChangeField> search, Object... params)
		throws PrismsRecordException
	{
		return ((DBChangeSearch) search).execute(params);
	}

	public void destroy(PreparedSearch<ChangeField> search) throws PrismsRecordException
	{
		((DBChangeSearch) search).dispose();
	}

	public ChangeRecord [] getItems(long... ids) throws PrismsRecordException
	{
		return getChanges(null, ids);
	}

	public int getSubjectCenter(long changeID) throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = "SELECT subjectCenter FROM " + theTransactor.getTablePrefix()
			+ "prisms_change_record WHERE recordNS=" + toSQL(theNamespace) + " AND id=" + changeID;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return -1;
			return rs.getInt(1);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get subject center: SQL=" + sql, e);
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
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Connection error", e);
				}
		}
	}

	private long [] getChangeRecords(Statement stmt, String join, String where, String order)
		throws PrismsRecordException
	{
		boolean closeStmt = stmt == null;
		ResultSet rs = null;
		String sql = "SELECT id FROM " + theTransactor.getTablePrefix() + "prisms_change_record";
		if(join != null)
			sql += " " + join;
		sql += " WHERE " + theTransactor.getTablePrefix() + "prisms_change_record.recordNS="
			+ toSQL(theNamespace);
		if(where != null && where.length() > 0)
			sql += " AND (" + where + ")";
		if(order != null)
			sql += " ORDER BY " + order;
		ArrayList<Long> ret = new ArrayList<Long>();
		try
		{
			if(stmt == null)
				stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			while(rs.next())
				ret.add(Long.valueOf(rs.getLong(1)));
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

	public boolean hasChange(long changeID) throws PrismsRecordException
	{
		return getChangeRecords(null, null, "id=" + changeID, null).length > 0;
	}

	public boolean hasSuccessfulChange(long changeID) throws PrismsRecordException
	{
		if(theIDs.belongs(changeID))
			return true;
		Statement stmt = null;
		ResultSet rs = null;
		String sql = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			sql = "SELECT count(*) FROM " + theTransactor.getTablePrefix()
				+ "prisms_sync_assoc WHERE recordNS=" + toSQL(theNamespace) + " AND changeRecord="
				+ changeID + " AND error=" + boolToSql(false);
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return false;
			int count = rs.getInt(1);
			return count > 0;
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not check success of change", e);
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
			try
			{
				if(stmt != null)
					stmt.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			}
		}
	}

	public long [] sortChangeIDs(long [] ids, boolean ascending) throws PrismsRecordException
	{
		Sorter<ChangeField> sorter = new Sorter<ChangeField>();
		sorter.addSort(ChangeField.CHANGE_TIME, ascending);
		return sortChangeIDs(ids, sorter);
	}

	/**
	 * A more efficient method for retrieving sorted IDs
	 * 
	 * @param historyItem The history item to get history of (may be null)
	 * @param user The user to get changes by
	 * @param center The center to get changes imported from/exported to
	 * @param isImport Whether the changes returned (if center is non-null) should be those imported
	 *        from or exported to (or both) the given center
	 * @param syncRecord The sync record to get change records for (may be null)
	 * @param sorter The sorter to determine the order of the IDs returned
	 * @return The IDs of all changes that match the given parameters and ordered as determined by
	 *         the given sorter
	 * @throws PrismsRecordException If the information cannot be retrieved
	 */
	public long [] getChangeIDs(Object historyItem, RecordUser user, PrismsCenter center,
		Boolean isImport, SyncRecord syncRecord, Sorter<ChangeField> sorter)
		throws PrismsRecordException
	{
		String join = null;
		StringBuilder where = new StringBuilder();
		if(historyItem != null)
		{
			long itemID = getDataID(historyItem);
			SubjectType [] types = getHistoryDomains(historyItem);
			if(types.length == 0)
				return new long [0];
			for(int i = 0; i < types.length; i++)
			{
				if(i > 0)
					where.append(" OR ");
				where.append("(subjectType=");
				where.append(toSQL(types[i].name()));
				where.append(" AND (");
				boolean useOr = false;
				if(types[i].getMajorType().isInstance(historyItem))
				{
					where.append("majorSubject=");
					where.append(itemID);
					useOr = true;
				}
				if(types[i].getMetadataType1() != null
					&& types[i].getMetadataType1().isInstance(historyItem))
				{
					if(useOr)
						where.append(" OR ");
					where.append("changeData1=");
					where.append(itemID);
					useOr = true;
				}
				if(types[i].getMetadataType2() != null
					&& types[i].getMetadataType2().isInstance(historyItem))
				{
					if(useOr)
						where.append(" OR ");
					where.append("changeData2=");
					where.append(itemID);
					useOr = true;
				}
				if(!useOr)
				{
					ChangeType [] changes = (ChangeType []) types[i].getChangeTypes()
						.getEnumConstants();
					for(ChangeType ch : changes)
					{
						if(ch.getMinorType() != null && ch.getMinorType().isInstance(historyItem))
						{
							if(useOr)
								where.append(" OR ");
							where.append("(changeType=");
							where.append(toSQL(ch.name()));
							where.append(" AND minorSubject=");
							where.append(itemID);
							where.append(')');
							useOr = true;
						}
					}
				}
				if(!useOr)
					throw new PrismsRecordException("Subject type " + types[i]
						+ " does not apply to " + historyItem.getClass().getName());
				where.append("))");
			}
		}
		else if(user != null)
		{
			where.append("changeUser=");
			where.append(user.getID());
		}
		else if(center != null)
		{
			join = "INNER JOIN " + theTransactor.getTablePrefix()
				+ "prisms_sync_assoc ON changeRecord=" + theTransactor.getTablePrefix()
				+ "prisms_change_record.id INNER JOIN " + theTransactor.getTablePrefix()
				+ "prisms_sync_record ON syncRecord=" + theTransactor.getTablePrefix()
				+ "prisms_sync_record.id";
			where.append("syncCenter=");
			where.append(center.getID());
			if(isImport != null)
			{
				where.append(" AND isImport=");
				where.append(boolToSql(isImport.booleanValue()));
			}
		}
		else if(syncRecord != null)
		{
			join = "INNER JOIN " + theTransactor.getTablePrefix()
				+ "prisms_sync_assoc ON changeRecord=id";
			where.append("syncRecord=");
			where.append(syncRecord.getID());
		}

		StringBuilder order = new StringBuilder();
		if(sorter.getSortCount() > 0)
		{
			for(int sc = 0; sc < sorter.getSortCount(); sc++)
			{
				if(sc > 0)
					order.append(", ");
				ChangeField field = sorter.getField(sc);
				switch(field)
				{
				case CHANGE_TYPE:
					order.append(" subjectType ");
					order.append(sorter.isAscending(sc) ? "ASC" : "DESC");
					order.append(", changeType ");
					order.append(sorter.isAscending(sc) ? "ASC" : "DESC");
					break;
				case CHANGE_TIME:
				case CHANGE_USER:
					order.append(' ');
					order.append(sorter.getField(sc).toString());
					order.append(sorter.isAscending(sc) ? " ASC" : " DESC");
					break;
				}
			}
		}
		else
			order.append("modTime DESC");
		return getChangeRecords(null, join, where.length() == 0 ? null : where.toString(),
			order.toString());
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
	public long [] sortChangeIDs(long [] ids, Sorter<ChangeField> sorter)
		throws PrismsRecordException
	{
		if(ids.length <= 1 || sorter.getSortCount() == 0)
			return ids;
		prisms.util.DBUtils.KeyExpression expr = prisms.util.DBUtils.simplifyKeySet(ids, 200);
		String where = expr.toSQL("id");
		StringBuilder order = new StringBuilder();
		if(sorter.getSortCount() > 0)
		{
			for(int sc = 0; sc < sorter.getSortCount(); sc++)
			{
				if(sc > 0)
					order.append(", ");
				ChangeField field = sorter.getField(sc);
				switch(field)
				{
				case CHANGE_TYPE:
					order.append(" subjectType ");
					order.append(sorter.isAscending(sc) ? "ASC" : "DESC");
					order.append(",");
					order.append(" changeType ");
					order.append(sorter.isAscending(sc) ? "ASC" : "DESC");
					break;
				case CHANGE_TIME:
				case CHANGE_USER:
					order.append(' ');
					order.append(sorter.getField(sc).toString());
					order.append(sorter.isAscending(sc) ? " ASC" : " DESC");
					break;
				}
			}
		}
		else
			order.append("modTime DESC");
		return getChangeRecords(null, null, where, order.toString());
	}

	/**
	 * Gets the change record with the given ID. If the change record cannot be retrieved, a record
	 * is returned with error fields so that some information can be displayed to the user
	 * 
	 * @param id The ID of the record to get
	 * @return The record with the given ID, or as much information about the record as can be
	 *         retrieved
	 * @throws PrismsRecordException If not enough information about the record cannot be retrieved
	 *         to construct an errored object
	 */
	public ChangeRecord getChangeError(long id) throws PrismsRecordException
	{
		return getChangeError(null, id);
	}

	ChangeRecordError getChangeError(Statement stmt, long id) throws PrismsRecordException
	{
		String sql = "SELECT * FROM " + theTransactor.getTablePrefix()
			+ "prisms_change_record WHERE recordNS=" + toSQL(theNamespace) + " AND id=" + id;
		boolean closeStmt = false;
		if(stmt == null)
		{
			closeStmt = true;
			try
			{
				stmt = theTransactor.getConnection().createStatement();
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not create statement", e);
			}
		}
		ResultSet rs = null;
		try
		{
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				return null;
			final ChangeTemplate template = getChangeTemplate(rs);
			rs.close();
			rs = null;
			SubjectType subjectType;
			ChangeType changeType;
			RecordUser user;
			try
			{
				user = thePersister.getUser(template.userID);
			} catch(PrismsRecordException e)
			{
				user = new RecordUser()
				{

					public long getID()
					{
						return template.id;
					}

					public String getName()
					{
						return "Error";
					}

					public boolean isDeleted()
					{
						return false;
					}
				};
			}
			ChangeRecordError ret = new ChangeRecordError(template.id, template.local,
				template.time, user);
			ret.setSubjectType(template.subjectType);
			ret.setChangeType(template.changeType);
			ret.setAdditivity(template.add == '+' ? 1 : (template.add == '-' ? -1 : 0));
			try
			{
				subjectType = getType(template.subjectType);
				changeType = RecordUtils.getChangeType(subjectType, template.changeType);
				ChangeData changeData = getChangeData(subjectType, changeType,
					template.majorSubjectID, template.minorSubjectID, template.data1,
					template.data2, template.preValueID, template.previousValue);
				ret.setMajorSubject(changeData.majorSubject, template.majorSubjectID);
				ret.setMinorSubject(changeData.minorSubject, template.minorSubjectID == null ? -1
					: template.minorSubjectID.longValue());
				if(template.preValueID != null)
					ret.setPreValue(changeData.preValue, template.preValueID);
				else if(template.previousValue != null)
					ret.setPreValue(changeData.preValue, template.previousValue);
				ret.setData1(changeData.data1,
					template.data1 == null ? -1 : template.data1.longValue());
				ret.setData2(changeData.data2,
					template.data2 == null ? -1 : template.data2.longValue());
			} catch(PrismsRecordException e)
			{
				ret.setMajorSubject(null, template.majorSubjectID);
				ret.setMinorSubject(null, template.minorSubjectID == null ? -1
					: template.minorSubjectID.longValue());
				if(template.preValueID != null)
					ret.setPreValue(null, template.preValueID);
				else if(template.previousValue != null)
					ret.setPreValue(null, template.previousValue);
				ret.setData1(null, template.data1 == null ? -1 : template.data1.longValue());
				ret.setData2(null, template.data2 == null ? -1 : template.data2.longValue());
			}
			return ret;
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not get record data: SQL=" + sql, e);
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
			try
			{
				if(closeStmt && stmt != null)
					stmt.close();
			} catch(SQLException e)
			{
				log.error("Connection error", e);
			}
		}
	}

	public long getLatestPurgedChange(int centerID, int subjectCenter) throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = null;
		long ret = -1;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			sql = "SELECT latestChange FROM " + theTransactor.getTablePrefix()
				+ "prisms_purge_record WHERE recordNS=" + toSQL(theNamespace) + " AND centerID="
				+ centerID + " AND subjectCenter=" + subjectCenter;
			rs = stmt.executeQuery(sql);
			if(rs.next())
			{
				long val = rs.getTimestamp(1).getTime();
				if(val > ret)
					ret = val;
			}
			rs.close();
			rs = null;
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not retrieve all center IDs: SQL=" + sql, e);
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
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					throw new PrismsRecordException("Connection error", e);
				}
		}
		return ret;
	}

	private static class ChangeTemplate
	{
		long id;

		boolean local;

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

		@Override
		public String toString()
		{
			return "(" + id + ") " + subjectType + "/" + changeType + "/" + add;
		}
	}

	private ChangeRecord [] getChanges(Statement stmt, long [] ids) throws PrismsRecordException
	{
		if(ids.length == 0)
			return new ChangeRecord [0];
		boolean closeAfter = stmt == null;
		if(stmt == null)
		{
			try
			{
				stmt = theTransactor.getConnection().createStatement();
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not create statement", e);
			}
		}
		prisms.util.DBUtils.KeyExpression expr = prisms.util.DBUtils.simplifyKeySet(ids, 200);
		prisms.util.DBUtils.KeyExpression[] exprs;
		if(expr instanceof prisms.util.DBUtils.OrExpression && expr.getComplexity() > 200)
			exprs = ((prisms.util.DBUtils.OrExpression) expr).exprs;
		else
			exprs = new prisms.util.DBUtils.KeyExpression [] {expr};

		try
		{
			ArrayList<ChangeTemplate> changeList = new ArrayList<ChangeTemplate>();
			for(int i = 0; i < exprs.length; i++)
			{
				String sql = "SELECT * FROM " + theTransactor.getTablePrefix()
					+ "prisms_change_record WHERE recordNS=" + toSQL(theNamespace) + " AND "
					+ exprs[i].toSQL("id");
				ResultSet rs = null;
				try
				{
					rs = stmt.executeQuery(sql);
					while(rs.next())
					{
						ChangeTemplate template = getChangeTemplate(rs);
						changeList.add(template);
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
			}

			ChangeRecord [] ret = new ChangeRecord [changeList.size()];
			for(int i = 0; i < ret.length; i++)
			{
				ChangeTemplate template = changeList.get(i);
				try
				{
					SubjectType subjectType = getType(template.subjectType);
					ChangeType changeType = RecordUtils.getChangeType(subjectType,
						template.changeType);
					int add = template.add == '+' ? 1 : (template.add == '-' ? -1 : 0);
					ChangeData changeData = getChangeData(subjectType, changeType,
						template.majorSubjectID, template.minorSubjectID, template.data1,
						template.data2, template.preValueID, template.previousValue);
					ret[i] = new ChangeRecord(template.id, template.local, template.time,
						thePersister.getUser(template.userID), subjectType, changeType, add,
						changeData.majorSubject, changeData.minorSubject, changeData.preValue,
						changeData.data1, changeData.data2);
				} catch(PrismsRecordException e)
				{
					log.error("Could not get record " + template, e);
					ret[i] = getChangeError(stmt, template.id);
				} catch(RuntimeException e)
				{
					log.error("Could not instantiate record " + template, e);
					ret[i] = getChangeError(stmt, i);
				}
			}
			Long [] idObjs = new Long [ids.length];
			for(int i = 0; i < ids.length; i++)
				idObjs[i] = Long.valueOf(ids[i]);
			final ArrayUtils.ArrayAdjuster<ChangeRecord, Long, RuntimeException> [] adjuster;
			adjuster = new ArrayUtils.ArrayAdjuster [1];
			adjuster[0] = new ArrayUtils.ArrayAdjuster<ChangeRecord, Long, RuntimeException>(ret,
				idObjs, new ArrayUtils.DifferenceListener<ChangeRecord, Long>()
				{
					public boolean identity(ChangeRecord o1, Long o2)
					{
						return o1 != null && o1.id == o2.longValue();
					}

					public ChangeRecord added(Long o, int idx, int retIdx)
					{
						adjuster[0].nullElement();
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
			return adjuster[0].adjust();
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

	private ChangeTemplate getChangeTemplate(ResultSet rs) throws SQLException,
		PrismsRecordException
	{
		ChangeTemplate template = new ChangeTemplate();
		template.id = rs.getLong("id");
		template.local = boolFromSql(rs.getString("localOnly"));
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
			template.previousValue = fromSQL(rs.getString("shortPreValue"));
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
						template.previousValue = PrismsUtils.decodeUnicode(fv.toString());
					} catch(java.io.IOException e)
					{
						throw new PrismsRecordException("Could not read long field value", e);
					}
			}
		}
		return template;
	}

	SubjectType getType(String typeName) throws PrismsRecordException
	{
		if(ChangeRecordError.ErrorSubjectType.name().equals(typeName))
			return ChangeRecordError.ErrorSubjectType;
		for(PrismsChange ch : PrismsChange.values())
			if(ch.name().equals(typeName))
				return ch;
		return thePersister.getSubjectType(typeName);
	}

	long getDataID(Object obj) throws PrismsRecordException
	{
		if(obj instanceof PrismsCenter)
			return ((PrismsCenter) obj).getID();
		if(obj instanceof AutoPurger)
			return 0;
		return thePersister.getID(obj);
	}

	int getSubjectCenter(Object obj) throws PrismsRecordException
	{
		if(obj instanceof PrismsCenter)
			return getCenterID();
		if(obj instanceof AutoPurger)
			return getCenterID();
		return RecordUtils.getCenterID(thePersister.getID(obj));
	}

	ChangeData getChangeData(SubjectType subjectType, ChangeType changeType, long majorSubjectID,
		Number minorSubjectID, Number data1ID, Number data2ID, Number preValueID,
		String serialPreValue) throws PrismsRecordException
	{
		if(subjectType instanceof PrismsChange)
		{
			PrismsChange pc = (PrismsChange) subjectType;
			switch(pc)
			{
			case center:
				PrismsCenter ctr = getCenter((int) majorSubjectID, null);
				if(ctr == null)
					throw new PrismsRecordException("Could not get center with ID "
						+ majorSubjectID);
				ChangeData ret = new ChangeData(ctr, null, null, null, null);
				if(changeType == null)
					return ret;
				switch((PrismsChange.CenterChange) changeType)
				{
				case name:
				case url:
				case serverUserName:
				case serverPassword:
					ret.preValue = serialPreValue;
					return ret;
				case serverCerts:
					if(serialPreValue == null)
						return ret;
					byte [] bytes = new byte [serialPreValue.length() / 2];
					for(int i = 0; i < serialPreValue.length(); i += 2)
					{
						int dig = hexDig(serialPreValue.charAt(i));
						if(dig < 0)
							throw new PrismsRecordException(
								"Non-hex digit! Could not decode certificates");
						bytes[i / 2] = (byte) (dig << 4);
						dig = hexDig(serialPreValue.charAt(i + 1));
						if(dig < 0)
							throw new PrismsRecordException(
								"Non-hex digit! Could not decode certificates");
						bytes[i / 2] |= dig;
					}
					try
					{
						ret.preValue = java.security.cert.CertificateFactory.getInstance("X.509")
							.generateCertificates(new java.io.ByteArrayInputStream(bytes))
							.toArray(new java.security.cert.X509Certificate [0]);
					} catch(java.security.cert.CertificateException e)
					{
						throw new PrismsRecordException("Could not decode certificates", e);
					}
					return ret;
				case syncFrequency:
				case changeSaveTime:
					if(serialPreValue != null)
						ret.preValue = Long.valueOf(serialPreValue);
					else
						ret.preValue = Long.valueOf(-1);
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
					ret.preValue = Long.valueOf(serialPreValue);
					return ret;
				case entryCount:
					ret.preValue = Integer.valueOf(serialPreValue);
					return ret;
				case excludeType:
					SubjectType rtST = getType(serialPreValue.substring(0,
						serialPreValue.indexOf('/')));
					serialPreValue = serialPreValue.substring(serialPreValue.indexOf('/') + 1);
					String ctName = serialPreValue.substring(0, serialPreValue.indexOf('/'));
					ChangeType rtCT = ctName.equals("null") ? null : RecordUtils.getChangeType(
						subjectType, ctName);
					serialPreValue = serialPreValue.substring(serialPreValue.indexOf('/') + 1);
					int add;
					if(serialPreValue.charAt(0) == '+')
						add = 1;
					else if(serialPreValue.charAt(0) == '-')
						add = -1;
					else
						add = 0;
					ret.preValue = new RecordType(rtST, rtCT, add);
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
		Object preValue;
		if(serialPreValue != null)
		{
			if(serialPreValue.length() == 0)
				preValue = null;
			else if(changeType.getObjectType() == Long.class)
				preValue = Long.valueOf(serialPreValue);
			else if(changeType.getObjectType() == Integer.class)
				preValue = Integer.valueOf(serialPreValue);
			else if(changeType.getObjectType() == Short.class)
				preValue = Short.valueOf(serialPreValue);
			else if(changeType.getObjectType() == Byte.class)
				preValue = Byte.valueOf(serialPreValue);
			else if(changeType.getObjectType() == Float.class)
				preValue = Float.valueOf(serialPreValue);
			else if(changeType.getObjectType() == Double.class)
				preValue = Double.valueOf(serialPreValue);
			else if(changeType.getObjectType() == Boolean.class)
				preValue = Boolean.valueOf("true".equals(serialPreValue));
			else
				preValue = serialPreValue;
		}
		else
			preValue = preValueID;
		return thePersister.getData(subjectType, changeType, Long.valueOf(majorSubjectID),
			minorSubjectID, data1ID, data2ID, preValue);
	}

	private int hexDig(char c)
	{
		if(c >= '0' && c <= '9')
			return c - '0';
		else if(c >= 'A' && c <= 'F')
			return c - 'A' + 10;
		else if(c >= 'a' && c <= 'f')
			return c - 'a' + 10;
		else
			return -1;
	}

	String serializePreValue(ChangeRecord change) throws PrismsRecordException
	{
		Object obj = change.previousValue;
		if(obj == null)
			return null;
		if(obj instanceof RecordType)
		{
			RecordType type = (RecordType) obj;
			StringBuilder ret = new StringBuilder();
			ret.append(type.subjectType.name());
			ret.append('/');
			ret.append(type.changeType == null ? "null" : type.changeType.name());
			ret.append('/');
			ret.append(type.additivity > 0 ? '+' : (type.additivity < 0 ? '-' : '0'));
			return ret.toString();
		}
		else if(obj instanceof java.security.cert.X509Certificate[])
		{
			char [] hex = new char [] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B',
				'C', 'D', 'E', 'F'};
			StringBuilder ret = new StringBuilder();
			try
			{
				for(java.security.cert.X509Certificate cert : (java.security.cert.X509Certificate[]) obj)
					for(byte enc : cert.getEncoded())
					{
						ret.append(hex[(enc >>> 4) & 0xf]);
						ret.append(hex[enc & 0xf]);
					}
			} catch(java.security.cert.CertificateEncodingException e)
			{
				throw new PrismsRecordException("Could not encode certificates", e);
			}
			return ret.toString();
		}
		else if(obj instanceof Boolean || obj instanceof Integer || obj instanceof Long
			|| obj instanceof Float || obj instanceof Double || obj instanceof String)
			return obj.toString();
		return thePersister.serializePreValue(change);
	}

	// TODO This code might be useful somewhere
	// Object deserialize(Class<?> type, String serialized) throws PrismsRecordException
	// {
	// if(type == null || serialized == null)
	// return null;
	// else if(type == String.class)
	// return serialized;
	// else if(type == Boolean.class)
	// return "true".equalsIgnoreCase(serialized) ? Boolean.TRUE : Boolean.FALSE;
	// else if(type == Integer.class)
	// return new Integer(serialized);
	// else if(type == Long.class)
	// return new Long(serialized);
	// else if(type == Float.class)
	// return new Float(serialized);
	// else if(type == Double.class)
	// return new Double(serialized);
	// else
	// return thePersister.deserialize(type, serialized);
	// }

	/**
	 * @return The auto-purger that manages the changes in this record keeper
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public AutoPurger getAutoPurger() throws PrismsRecordException
	{
		if(theAutoPurger != null)
			return theAutoPurger;
		Statement stmt = null;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
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
	public void setAutoPurger(final AutoPurger purger, final RecordsTransaction user)
		throws PrismsRecordException
	{
		theTransactor.performTransaction(new TransactionOperation<PrismsRecordException>()
		{
			public Object run(Statement stmt) throws PrismsRecordException
			{
				dbSetAutoPurger(stmt, purger, user);
				return null;
			}
		}, "Could not set auto-purger");
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
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			return purger.previewRowsDeleted(this, stmt, theTransactor.getTablePrefix()
				+ "prisms_change_record", "changeTime", "changeUser", "subjectType", "changeType",
				"additivity");
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

	public ChangeRecord persist(RecordsTransaction trans, SubjectType subjectType,
		ChangeType changeType, int additivity, Object majorSubject, Object minorSubject,
		Object previousValue, Object data1, Object data2) throws PrismsRecordException
	{
		if(trans.isMemoryOnly())
			return null;
		Statement stmt = null;
		long id;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			id = theIDs.getNextID("prisms_change_record", "id", stmt,
				theTransactor.getTablePrefix(), "recordNS=" + toSQL(theNamespace));
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not persist " + subjectType + " change", e);
		} catch(PrismsException e)
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
		long time;
		synchronized(this)
		{
			time = System.currentTimeMillis();
			if(time <= theLastChange)
			{
				theLastChange++;
				time = theLastChange;
			}
			else
				theLastChange = time;
		}
		ChangeRecord record = new ChangeRecord(id, !trans.shouldRecord(), time, trans.getUser(),
			subjectType, changeType, additivity, majorSubject, minorSubject, previousValue, data1,
			data2);
		persist(record);
		return record;
	}

	public void persist(ChangeRecord record) throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = null;
		try
		{
			try
			{
				stmt = theTransactor.getConnection().createStatement();
				sql = "SELECT id FROM " + theTransactor.getTablePrefix()
					+ "prisms_change_record WHERE recordNS=" + toSQL(theNamespace) + " AND id="
					+ record.id;
				rs = stmt.executeQuery(sql);
				if(rs.next())
				{ // modification already exists
					rs.close();
					rs = null;
					return;
				}
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
			synchronized(this)
			{
				java.sql.PreparedStatement pStmt = theChangeInserter;
				pStmt.clearParameters();
				pStmt.setLong(1, record.id);
				pStmt.setString(2, boolToSqlP(record.localOnly));
				pStmt.setTimestamp(3, getUtcTimestamp(record.time));
				pStmt.setLong(4, record.user.getID());
				if(record instanceof ChangeRecordError)
				{
					ChangeRecordError error = (ChangeRecordError) record;
					pStmt.setString(5, error.getSubjectType());
					if(error.getChangeType() == null)
						pStmt.setNull(6, java.sql.Types.VARCHAR);
					else
						pStmt.setString(6, error.getChangeType());
					pStmt.setString(7, error.getAdditivity() < 0 ? "-" : (error.getAdditivity() > 0
						? "+" : "0"));
					pStmt.setInt(8, RecordUtils.getCenterID(error.getMajorSubjectID()));
					pStmt.setLong(9, error.getMajorSubjectID());
					if(error.getMinorSubjectID() >= 0)
						pStmt.setLong(10, error.getMinorSubjectID());
					else
						pStmt.setNull(10, java.sql.Types.NUMERIC);
					if(error.getSerializedPreValue() instanceof Number)
					{
						pStmt.setLong(11, ((Number) error.getSerializedPreValue()).longValue());
						pStmt.setNull(12, java.sql.Types.VARCHAR);
						pStmt.setNull(13, java.sql.Types.CLOB);
					}
					else if(error.getSerializedPreValue() != null)
					{
						pStmt.setNull(11, java.sql.Types.NUMERIC);
						String serialized = (String) error.getSerializedPreValue();
						if(serialized.length() <= 100)
						{
							pStmt.setString(12, PrismsUtils.encodeUnicode(serialized));
							pStmt.setNull(13, java.sql.Types.CLOB);
						}
						else
						{
							pStmt.setNull(12, java.sql.Types.VARCHAR);
							pStmt.setCharacterStream(13,
								new java.io.StringReader(PrismsUtils.encodeUnicode(serialized)),
								serialized.length());
						}
					}
					else
					{
						pStmt.setNull(11, java.sql.Types.NUMERIC);
						pStmt.setNull(12, java.sql.Types.VARCHAR);
						pStmt.setNull(13, java.sql.Types.CLOB);
					}
					if(error.getData1ID() >= 0)
						pStmt.setLong(14, error.getData1ID());
					else
						pStmt.setNull(14, java.sql.Types.NUMERIC);
					if(error.getData2ID() >= 0)
						pStmt.setLong(15, error.getData2ID());
					else
						pStmt.setNull(15, java.sql.Types.NUMERIC);
				}
				else
				{
					pStmt.setString(5, record.type.subjectType.name());
					if(record.type.changeType == null)
						pStmt.setNull(6, java.sql.Types.VARCHAR);
					else
						pStmt.setString(6, record.type.changeType.name());
					pStmt.setString(7, record.type.additivity < 0 ? "-"
						: (record.type.additivity > 0 ? "+" : "0"));
					pStmt.setInt(8, getSubjectCenter(record.majorSubject));
					pStmt.setLong(9, getDataID(record.majorSubject));
					if(record.minorSubject == null)
						pStmt.setNull(10, java.sql.Types.NUMERIC);
					else
						pStmt.setLong(10, getDataID(record.minorSubject));
					if(record.previousValue == null)
					{
						pStmt.setNull(11, java.sql.Types.NUMERIC);
						pStmt.setNull(12, java.sql.Types.VARCHAR);
						pStmt.setNull(13, java.sql.Types.CLOB);
					}
					else if(record.type.changeType.isObjectIdentifiable())
					{
						pStmt.setLong(11, getDataID(record.previousValue));
						pStmt.setNull(12, java.sql.Types.VARCHAR);
						pStmt.setNull(13, java.sql.Types.CLOB);
					}
					else
					{
						pStmt.setNull(11, java.sql.Types.NUMERIC);
						String serialized = serializePreValue(record);
						if(serialized.length() <= 100)
						{
							pStmt.setString(12, PrismsUtils.encodeUnicode(serialized));
							pStmt.setNull(13, java.sql.Types.CLOB);
						}
						else
						{
							pStmt.setNull(12, java.sql.Types.VARCHAR);
							pStmt.setCharacterStream(13,
								new java.io.StringReader(PrismsUtils.encodeUnicode(serialized)),
								serialized.length());
						}
					}
					if(record.data1 == null)
						pStmt.setNull(14, java.sql.Types.NUMERIC);
					else
						pStmt.setLong(14, getDataID(record.data1));
					if(record.data2 == null)
						pStmt.setNull(15, java.sql.Types.NUMERIC);
					else
						pStmt.setLong(15, getDataID(record.data2));
				}
				pStmt.execute();
				pStmt.clearParameters();
			}
			if(theAutoPurger == null)
				getAutoPurger();
			theAutoPurger.doPurge(this, stmt, theTransactor.getTablePrefix()
				+ "prisms_change_record", "changeTime", "changeUser", "subjectType", "changeType",
				"additivity");
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not persist " + record.type.subjectType
				+ " change: SQL=" + sql, e);
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

	public void associate(ChangeRecord change, SyncRecord syncRecord, boolean error)
		throws PrismsRecordException
	{
		Statement stmt = null;
		ResultSet rs = null;
		String sql = "SELECT error FROM " + theTransactor.getTablePrefix()
			+ "prisms_sync_assoc WHERE recordNS=" + toSQL(theNamespace) + " AND syncRecord="
			+ syncRecord.getID() + " and changeRecord=" + change.id;
		Boolean errorB;
		try
		{
			stmt = theTransactor.getConnection().createStatement();
			rs = stmt.executeQuery(sql);
			if(!rs.next())
				errorB = null;
			else if(boolFromSql(rs.getString(1)))
				errorB = Boolean.TRUE;
			else
				errorB = Boolean.FALSE;
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not associate change " + change
				+ " with sync record " + syncRecord + ": SQL=" + sql, e);
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
			rs = null;
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(SQLException e)
				{
					log.error("Connection error", e);
				}
			stmt = null;
		}
		if(errorB != null)
		{
			if(errorB.booleanValue() == error)
				return;
			sql = "UPDATE " + theTransactor.getTablePrefix() + "prisms_sync_assoc SET error="
				+ boolToSql(error) + " WHERE recordNS=" + toSQL(theNamespace) + " AND syncRecord="
				+ syncRecord.getID() + " AND changeRecord=" + change.id;
		}
		else
			sql = "INSERT INTO " + theTransactor.getTablePrefix()
				+ "prisms_sync_assoc (recordNS, syncRecord," + " changeRecord, error) VALUES ("
				+ toSQL(theNamespace) + ", " + syncRecord.getID() + ", " + change.id + ", "
				+ boolToSql(error) + ")";
		try
		{
			stmt = theTransactor.getConnection().createStatement();
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
	public void purge(ChangeRecord record, Statement stmt) throws PrismsRecordException
	{
		boolean closeStmt = false;
		if(stmt == null)
		{
			closeStmt = true;
			try
			{
				stmt = theTransactor.getConnection().createStatement();
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not create statement", e);
			}
		}
		String sql = "SELECT changeTime FROM " + theTransactor.getTablePrefix()
			+ "prisms_change_record WHERE recordNS=" + toSQL(theNamespace) + " AND id=" + record.id;
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
			long time = rs.getTimestamp(1).getTime();
			rs.close();
			rs = null;
			int centerID = RecordUtils.getCenterID(record.id);
			int subjectCenter = getSubjectCenter(record.majorSubject);
			sql = "SELECT latestChange FROM " + theTransactor.getTablePrefix()
				+ "prisms_purge_record WHERE recordNS=" + toSQL(theNamespace) + " AND centerID="
				+ centerID + " AND subjectCenter=" + subjectCenter;
			rs = stmt.executeQuery(sql);
			boolean update = rs.next();
			if(!update || rs.getTimestamp(1).getTime() < time)
			{
				rs.close();
				rs = null;
				if(update)
				{
					sql = "UPDATE " + theTransactor.getTablePrefix()
						+ "prisms_purge_record SET latestChange=" + formatDate(time)
						+ " WHERE recordNS=" + toSQL(theNamespace) + " AND centerID=" + centerID
						+ " AND subjectCenter=" + subjectCenter;
					stmt.executeUpdate(sql);
				}
				else
				{
					sql = "INSERT INTO " + theTransactor.getTablePrefix()
						+ "prisms_purge_record (recordNS, centerID,"
						+ " subjectCenter, latestChange) VALUES (" + toSQL(theNamespace) + ", "
						+ centerID + ", " + subjectCenter + ", " + formatDate(time) + ")";
					stmt.execute(sql);
				}
			}
			else
			{
				rs.close();
				rs = null;
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
		String sql = "DELETE FROM " + theTransactor.getTablePrefix()
			+ "prisms_change_record WHERE recordNS=" + toSQL(theNamespace) + " AND id=" + record.id;
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
			checkItemForDelete(record.previousValue, stmt);
	}

	/**
	 * Checks the database for references to a particular object. Different than a history search in
	 * that this method gets ALL references to the given object, regardless of semantics.
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
			closeStmt = true;
			try
			{
				stmt = theTransactor.getConnection().createStatement();
			} catch(SQLException e)
			{
				throw new PrismsRecordException("Could not create statement", e);
			}
		}
		long [] ret = new long [0];
		try
		{
			SubjectType [] types = getAllDomains();
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
		ret = ArrayUtils.concat(SubjectType.class, ret, thePersister.getAllSubjectTypes());
		return ret;
	}

	long [] getRecords(SubjectType type, Object dbObject, Statement stmt)
		throws PrismsRecordException
	{
		StringBuilder where = new StringBuilder();
		if(type.getMajorType().isInstance(dbObject))
		{
			where.append("(subjectType=");
			where.append(toSQL(type.name()));
			where.append(" AND majorSubject=");
			where.append(getDataID(dbObject));
			where.append(')');
		}
		if(type.getMetadataType1() != null && type.getMetadataType1().isInstance(dbObject))
		{
			if(where.length() > 0)
				where.append(" OR ");
			where.append("(subjectType=");
			where.append(toSQL(type.name()));
			where.append(" AND changeData1=");
			where.append(getDataID(dbObject));
			where.append(')');
		}
		if(type.getMetadataType2() != null && type.getMetadataType2().isInstance(dbObject))
		{
			if(where.length() > 0)
				where.append(" OR ");
			where.append("(subjectType=");
			where.append(toSQL(type.name()));
			where.append(" AND changeData2=");
			where.append(getDataID(dbObject));
			where.append(')');
		}
		for(ChangeType ct : (ChangeType []) type.getChangeTypes().getEnumConstants())
		{
			if(ct.getMinorType() != null && ct.getMinorType().isInstance(dbObject))
			{
				if(where.length() > 0)
					where.append(" OR ");
				where.append("(subjectType=");
				where.append(toSQL(type.name()));
				where.append(" AND changeType=");
				where.append(toSQL(ct.name()));
				where.append(" AND minorSubject=");
				where.append(getDataID(dbObject));
				where.append(")");
			}
			if(ct.isObjectIdentifiable() && ct.getObjectType() != null
				&& ct.getObjectType().isInstance(dbObject))
			{
				if(where.length() > 0)
					where.append(" OR ");
				where.append("(subjectType=");
				where.append(toSQL(type.name()));
				where.append(" AND changeType=");
				where.append(toSQL(ct.name()));
				where.append(" AND preValueID=");
				where.append(getDataID(dbObject));
				where.append(')');
			}
		}
		if(where.length() == 0)
			return new long [0];
		return getChangeRecords(stmt, null, where.toString(), null);
	}

	void checkItemForDelete(Object item, Statement stmt) throws PrismsRecordException
	{
		if(item instanceof String || item instanceof Integer || item instanceof Long
			|| item instanceof Float || item instanceof Double || item instanceof Boolean)
			return;
		if(item instanceof PrismsCenter)
			dbDeleteCenter(((PrismsCenter) item).getID(), stmt);
		else if(item instanceof AutoPurger)
			return;
		else
			thePersister.checkItemForDelete(item, stmt);
	}

	void dbDeleteCenter(int id, Statement stmt) throws PrismsRecordException
	{
		String sql = "DELETE FROM " + theTransactor.getTablePrefix()
			+ "prisms_center_view WHERE recordNS=" + toSQL(theNamespace) + " AND id=" + id;
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
			try
			{
				stmt = theTransactor.getConnection().createStatement();
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
			ChangeRecord [] records = getItems(ids);
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
		String sql = "SELECT * FROM " + theTransactor.getTablePrefix()
			+ "prisms_auto_purge WHERE recordNS=" + toSQL(theNamespace);
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
				stmt.execute("INSERT INTO " + theTransactor.getTablePrefix()
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
		sql = "SELECT * FROM " + theTransactor.getTablePrefix()
			+ "prisms_purge_excl_user WHERE recordNS=" + toSQL(theNamespace);
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
		sql = "SELECT * FROM " + theTransactor.getTablePrefix()
			+ "prisms_purge_excl_type WHERE recordNS=" + toSQL(theNamespace);
		try
		{
			rs = stmt.executeQuery(sql);
			while(rs.next())
			{
				String subjectTypeStr = fromSQL(rs.getString("exclSubjectType"));
				String changeTypeStr = fromSQL(rs.getString("exclChangeType"));
				char addChar = rs.getString("exclAdditivity").charAt(0);
				SubjectType subjectType = getType(subjectTypeStr);
				ChangeType changeType = RecordUtils.getChangeType(subjectType, changeTypeStr);
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

	void dbSetAutoPurger(final Statement stmt, AutoPurger purger, final RecordsTransaction trans)
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
			addModification(trans, PrismsChange.autoPurge, PrismsChange.AutoPurgeChange.entryCount,
				0, dbPurger, null, Integer.valueOf(dbPurger.getEntryCount()), null, null);
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
			addModification(trans, PrismsChange.autoPurge, PrismsChange.AutoPurgeChange.age, 0,
				dbPurger, null, Long.valueOf(dbPurger.getAge()), null, null);
			dbPurger.setAge(purger.getAge());
		}
		if(modified)
		{
			log.debug(changeMsg.substring(0, changeMsg.length() - 1));
			String sql = "UPDATE " + theTransactor.getTablePrefix()
				+ "prisms_auto_purge SET entryCount="
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
					addModification(trans, PrismsChange.autoPurge,
						PrismsChange.AutoPurgeChange.excludeUser, 1, dbPurger, null,
						thePersister.getUser(o.getID()), null, null);
					String sql = "INSERT INTO " + theTransactor.getTablePrefix()
						+ "prisms_purge_excl_user (recordNS, exclUser) VALUES("
						+ toSQL(theNamespace) + ", " + o.getID() + ")";
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
					addModification(trans, PrismsChange.autoPurge,
						PrismsChange.AutoPurgeChange.excludeUser, -1, dbPurger, null,
						thePersister.getUser(o.getID()), null, null);
					String sql = "DELETE FROM " + theTransactor.getTablePrefix()
						+ "prisms_purge_excl_user WHERE recordNS=" + toSQL(theNamespace)
						+ " AND exclUser=" + o.getID();
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
					addModification(trans, PrismsChange.autoPurge,
						PrismsChange.AutoPurgeChange.excludeType, 1, dbPurger, null, o, null, null);
					String sql = "INSERT INTO " + theTransactor.getTablePrefix()
						+ "prisms_purge_excl_type (recordNS, exclSubjectType, exclChangeType,"
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
					addModification(trans, PrismsChange.autoPurge,
						PrismsChange.AutoPurgeChange.excludeType, -1, dbPurger, null, o, null, null);
					String sql = "DELETE FROM " + theTransactor.getTablePrefix()
						+ "prisms_purge_excl_type WHERE recordNS=" + toSQL(theNamespace)
						+ " AND exclSubjectType=" + toSQL(o.subjectType.name())
						+ " AND exclChangeType=";
					sql += (o.changeType == null ? "NULL" : toSQL(o.changeType.name()))
						+ " AND exclAdditivity= ";
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
	 * Gets the maximum length of data for a field
	 * 
	 * @param tableName The name of the table
	 * @param fieldName The name of the field to get the length for
	 * @return The maximum length that data in the given field can be
	 * @throws PrismsRecordException If an error occurs retrieving the information
	 */
	public int getFieldSize(String tableName, String fieldName) throws PrismsRecordException
	{
		try
		{
			return DBUtils.getFieldSize(theTransactor.getConnection(),
				theTransactor.getTablePrefix() + tableName, fieldName);
		} catch(PrismsException e)
		{
			throw new PrismsRecordException(e.getMessage(), e);
		}
	}

	private Boolean _isOracle = null;

	/**
	 * @return Whether this data source is using an oracle database or not
	 * @throws PrismsRecordException If the connection cannot be obtained
	 */
	protected boolean isOracle() throws PrismsRecordException
	{
		if(_isOracle == null)
			_isOracle = Boolean.valueOf(DBUtils.isOracle(theTransactor.getConnection()));
		return _isOracle.booleanValue();
	}

	String formatDate(long time) throws PrismsRecordException
	{
		return DBUtils.formatDate(time, isOracle());
	}

	static boolean equal(Object o1, Object o2)
	{
		return o1 == null ? o2 == null : o1.equals(o2);
	}

	private void prepareStatements() throws PrismsRecordException
	{
		String sql = "INSERT INTO " + theTransactor.getTablePrefix()
			+ "prisms_change_record (recordNS, id, localOnly, changeTime,"
			+ " changeUser, subjectType, changeType, additivity, subjectCenter, majorSubject,"
			+ " minorSubject, preValueID, shortPreValue, longPreValue, changeData1,"
			+ " changeData2) VALUES (" + toSQL(theNamespace)
			+ ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		try
		{
			theChangeInserter = theTransactor.getConnection().prepareStatement(sql);
			sql = "UPDATE " + theTransactor.getTablePrefix()
				+ "prisms_center_view SET serverCerts=?" + " WHERE id=?";
			theCertSetter = theTransactor.getConnection().prepareStatement(sql);
		} catch(SQLException e)
		{
			throw new PrismsRecordException("Could not prepare statement", e);
		}
	}

	/** Called whenever this keeper's connection is connected or re-connected. For subclasses. */
	protected void connectionUpdated()
	{
		try
		{
			prepareStatements();
		} catch(PrismsRecordException e)
		{
			log.error("Could not get connection", e);
		}
	}

	private String createQuery(Search search, Sorter<ChangeField> sorter, boolean withParameters)
		throws PrismsRecordException
	{
		StringBuilder joins = new StringBuilder();
		StringBuilder wheres = new StringBuilder();
		if(search == null)
			search = new ChangeSearch.LocalOnlySearch(Boolean.FALSE);
		else if(!hasLocalOnly(search))
			search = search.and(new ChangeSearch.LocalOnlySearch(Boolean.FALSE));
		if(search instanceof Search.ExpressionSearch)
			((Search.ExpressionSearch) search).simplify();
		createQuery(search, withParameters, joins, wheres);
		StringBuilder ret = new StringBuilder("SELECT DISTINCT change.id");
		if(sorter != null)
		{
			for(int i = 0; i < sorter.getSortCount(); i++)
			{
				switch(sorter.getField(i))
				{
				case CHANGE_TYPE:
					ret.append(", change.subjectType, change.changeType");
					break;
				case CHANGE_TIME:
				case CHANGE_USER:
					ret.append(", change.");
					ret.append(sorter.getField(i).toString());
					break;
				}
			}
		}
		else
			ret.append(", change.changeTime");
		ret.append(" FROM ");
		ret.append(theTransactor.getTablePrefix());
		ret.append("prisms_change_record change");
		ret.append(joins);
		if(wheres.length() > 0)
		{
			ret.append(" WHERE ");
			ret.append(wheres);
			ret.append(" AND change.recordNS=");
			ret.append(toSQL(theNamespace));
		}
		else
		{
			ret.append(" WHERE change.recordNS=");
			ret.append(toSQL(theNamespace));
		}
		return ret.toString();
	}

	private void createQuery(Search search, boolean withParameters, StringBuilder joins,
		StringBuilder wheres) throws PrismsRecordException
	{
		if(search instanceof Search.NotSearch)
		{
			Search.NotSearch not = (Search.NotSearch) search;
			wheres.append("NOT ");
			boolean withParen = not.getParent() != null;
			if(withParen)
				wheres.append('(');
			createQuery(not.getOperand(), withParameters, joins, wheres);
			if(withParen)
				wheres.append(')');
		}
		else if(search instanceof Search.ExpressionSearch)
		{
			Search.ExpressionSearch exp = (Search.ExpressionSearch) search;
			boolean withParen = exp.getParent() != null;
			if(withParen)
				wheres.append('(');
			boolean first = true;
			for(Search srch : exp)
			{
				if(!first)
				{
					if(exp.and)
						wheres.append(" AND ");
					else
						wheres.append(" OR ");
				}
				first = false;
				createQuery(srch, withParameters, joins, wheres);
			}
			if(withParen)
				wheres.append(')');
		}
		else if(search.getType() instanceof ChangeSearch.ChangeSearchType)
		{
			ChangeSearch.ChangeSearchType type = (ChangeSearch.ChangeSearchType) search.getType();
			switch(type)
			{
			case id:
				ChangeSearch.IDRange ids = (ChangeSearch.IDRange) search;
				if(ids.getMinID() != null && ids.getMaxID() != null
					&& ids.getMinID().longValue() == ids.getMaxID().longValue())
				{
					wheres.append("change.id=");
					wheres.append(ids.getMinID());
				}
				else
				{
					wheres.append('(');
					wheres.append("change.id>=");
					if(ids.getMinID() == null)
					{
						if(withParameters)
							wheres.append('?');
						else
							throw new PrismsRecordException("No minimum ID specified for ID range");
					}
					else
						wheres.append(ids.getMinID());
					wheres.append(" AND change.id<=");
					if(ids.getMaxID() == null)
					{
						if(withParameters)
							wheres.append('?');
						else
							throw new PrismsRecordException("No maximum ID specified for ID range");
					}
					else
						wheres.append(ids.getMaxID());
					wheres.append(')');
				}
				break;
			case majorSubjectRange:
				ChangeSearch.MajorSubjectRange msr = (ChangeSearch.MajorSubjectRange) search;
				wheres.append('(');
				wheres.append("change.majorSubject>=");
				if(msr.getMinID() == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsRecordException(
							"No minimum major subject ID specified for major subject range");
				}
				else
					wheres.append(msr.getMinID());
				wheres.append(" AND change.majorSubject<=");
				if(msr.getMaxID() == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsRecordException(
							"No maximum major subject ID specified for major subject range");
				}
				else
					wheres.append(msr.getMaxID());
				wheres.append(')');
				break;
			case subjectCenter:
				ChangeSearch.SubjectCenterSearch scs = (ChangeSearch.SubjectCenterSearch) search;
				wheres.append("change.subjectCenter=");
				if(scs.getSubjectCenter() == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsRecordException(
							"No subject center specified for subject center search");
				}
				else
					wheres.append(scs.getSubjectCenter());
				break;
			case time:
				ChangeSearch.ChangeTimeSearch cts = (ChangeSearch.ChangeTimeSearch) search;
				appendTime(cts.operator, cts.changeTime, "change.changeTime", wheres,
					withParameters);
				break;
			case user:
				ChangeSearch.ChangeUserSearch cus = (ChangeSearch.ChangeUserSearch) search;
				wheres.append("change.changeUser=");
				if(cus.getUser() == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsRecordException("No user specified for user search");
				}
				else
					wheres.append(cus.getUser().getID());
				break;
			case subjectType:
				ChangeSearch.SubjectTypeSearch sts = (ChangeSearch.SubjectTypeSearch) search;
				wheres.append("change.subjectType=");
				if(sts.getSubjectType() == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsRecordException(
							"No subject type specified for subject type search");
				}
				else
					wheres.append(DBUtils.toSQL(sts.getSubjectType().name()));
				break;
			case changeType:
				ChangeSearch.ChangeTypeSearch chType = (ChangeSearch.ChangeTypeSearch) search;
				wheres.append("change.changeType");
				if(!chType.isSpecified())
				{
					if(withParameters)
						wheres.append("=?");
					else
						throw new PrismsRecordException(
							"No change type specified for change type search");
				}
				else if(chType.getChangeType() == null)
					wheres.append(" IS NULL");
				else
					wheres.append("=" + DBUtils.toSQL(chType.getChangeType().name()));
				break;
			case add:
				ChangeSearch.AdditivitySearch as = (ChangeSearch.AdditivitySearch) search;
				wheres.append("change.additivity=");
				if(as.getAdditivity() == null)
				{
					if(withParameters)
						wheres.append('?');
					else
						throw new PrismsRecordException(
							"No additivity specified for additivity search");
				}
				else if(as.getAdditivity().intValue() < 0)
					wheres.append(toSQL("-"));
				else if(as.getAdditivity().intValue() > 0)
					wheres.append(toSQL("+"));
				else
					wheres.append(toSQL("0"));
				break;
			case field:
				ChangeSearch.ChangeFieldSearch cfs = (ChangeSearch.ChangeFieldSearch) search;
				switch(cfs.getFieldType())
				{
				case major:
					wheres.append("change.majorSubject");
					break;
				case minor:
					wheres.append("change.minorSubject");
					break;
				case data1:
					wheres.append("change.changeData1");
					break;
				case data2:
					wheres.append("change.changeData2");
					break;
				}
				if(!cfs.isFieldIDSpecified())
				{
					if(withParameters)
						wheres.append("=?");
					else
						throw new PrismsRecordException("No field ID specified for field search");
				}
				else if(cfs.getFieldID() == null)
					wheres.append(" IS NULL");
				else
				{
					wheres.append('=');
					wheres.append(cfs.getFieldID());
				}
				break;
			case syncRecord:
				ChangeSearch.SyncRecordSearch srs = (ChangeSearch.SyncRecordSearch) search;
				if(joins.indexOf("syncAssoc") < 0)
				{
					joins.append(" LEFT JOIN ");
					joins.append(theTransactor.getTablePrefix());
					joins.append("prisms_sync_assoc syncAssoc ON syncAssoc.changeRecord=change.id");
				}
				if(srs.isSyncRecordSet())
				{
					wheres.append("syncAssoc.syncRecord=");
					if(srs.getSyncRecordID() == null)
					{
						if(withParameters)
							wheres.append('?');
						else
							throw new PrismsRecordException(
								"No sync record ID specified for sync record search");
					}
					else
						wheres.append(srs.getSyncRecordID());
				}
				else if(srs.getTimeOp() != null)
				{
					if(joins.indexOf("syncRecord") < 0)
					{
						joins.append(" LEFT JOIN ");
						joins.append(theTransactor.getTablePrefix());
						joins
							.append("prisms_sync_record syncRecord ON syncAssoc.syncRecord=syncRecord.id");
					}
					appendTime(srs.getTimeOp(), srs.getTime(), "syncRecord.syncTime", wheres,
						withParameters);
				}
				else if(srs.getSyncError() != null)
				{
					if(joins.indexOf("syncRecord") < 0)
					{
						joins.append(" LEFT JOIN ");
						joins.append(theTransactor.getTablePrefix());
						joins
							.append("prisms_sync_record syncRecord ON syncAssoc.syncRecord=syncRecord.id");
					}
					wheres.append("syncRecord.syncError IS ");
					if(srs.getSyncError().booleanValue())
						wheres.append("NOT ");
					wheres.append("NULL");
				}
				else if(srs.isChangeErrorSet())
				{
					wheres.append("syncAssoc.error=");
					if(srs.getChangeError() == null)
					{
						if(withParameters)
							wheres.append('?');
						else
							throw new PrismsRecordException(
								"No change error specified with sync record search");
					}
					else
						wheres.append(DBUtils.boolToSql(srs.getChangeError().booleanValue()));
				}
				else if(srs.isSyncImportSet())
				{
					if(joins.indexOf("syncRecord") < 0)
					{
						joins.append(" LEFT JOIN ");
						joins.append(theTransactor.getTablePrefix());
						joins
							.append("prisms_sync_record syncRecord ON syncAssoc.syncRecord=syncRecord.id");
					}
					wheres.append("syncRecord.isImport=");
					if(srs.isSyncImport() == null)
					{
						if(withParameters)
							wheres.append('?');
						else
							throw new PrismsRecordException(
								"No sync import specified with sync record search");
					}
					else
						wheres.append(DBUtils.boolToSql(srs.isSyncImport().booleanValue()));
				}
				else
					throw new IllegalStateException("Unrecognized sync record search type: " + srs);
				break;
			case localOnly:
				ChangeSearch.LocalOnlySearch los = (ChangeSearch.LocalOnlySearch) search;
				if(los.getLocalOnly() == null)
					// Just need something here so that the expression's SQL is valid
					wheres.append("change.localOnly IS NOT NULL");
				else
				{
					wheres.append("change.localOnly=");
					wheres.append(DBUtils.boolToSql(los.getLocalOnly().booleanValue()));
				}
				break;
			}
		}
		else
			throw new PrismsRecordException("Unrecognized search type: " + search.getType());
	}

	private void appendTime(Search.Operator op, Search.SearchDate time, String field,
		StringBuilder wheres, boolean withParameters) throws PrismsRecordException
	{
		if(time == null)
		{
			wheres.append(field);
			wheres.append(op);
			if(withParameters)
				wheres.append('?');
			else
				throw new PrismsRecordException("No time specified for time search");
		}
		else if(time.minTime == time.maxTime)
		{
			wheres.append(field);
			wheres.append(op);
			wheres.append(DBUtils.formatDate(time.minTime, isOracle()));
		}
		else
		{

			switch(op)
			{
			case EQ:
				wheres.append('(');
				wheres.append(field);
				wheres.append(">=");
				wheres.append(DBUtils.formatDate(time.minTime, isOracle()));
				wheres.append(" AND ");
				wheres.append(field);
				wheres.append("<=");
				wheres.append(DBUtils.formatDate(time.maxTime, isOracle()));
				wheres.append(')');
				break;
			case NEQ:
				wheres.append('(');
				wheres.append(field);
				wheres.append('<');
				wheres.append(DBUtils.formatDate(time.minTime, isOracle()));
				wheres.append(" OR ");
				wheres.append(field);
				wheres.append('>');
				wheres.append(DBUtils.formatDate(time.maxTime, isOracle()));
				wheres.append(')');
				break;
			case GT:
				wheres.append(field);
				wheres.append('>');
				wheres.append(DBUtils.formatDate(time.maxTime, isOracle()));
				break;
			case GTE:
				wheres.append(field);
				wheres.append(">=");
				wheres.append(DBUtils.formatDate(time.minTime, isOracle()));
				break;
			case LT:
				wheres.append(field);
				wheres.append('<');
				wheres.append(DBUtils.formatDate(time.minTime, isOracle()));
				break;
			case LTE:
				wheres.append(field);
				wheres.append("<=");
				wheres.append(DBUtils.formatDate(time.maxTime, isOracle()));
				break;
			}
		}
	}

	/**
	 * Checks a search for a {@link ChangeSearch.LocalOnlySearch} instance, as this has impacts on
	 * how a search is performed.
	 * 
	 * @param search The search to search for a local only type
	 * @return Whether the given search has a local-only search or not
	 */
	public static boolean hasLocalOnly(Search search)
	{
		if(search instanceof ChangeSearch.LocalOnlySearch)
			return true;
		else if(search instanceof Search.CompoundSearch)
			for(Search srch : (Search.CompoundSearch) search)
				if(hasLocalOnly(srch))
					return true;
		return false;
	}

	private String getOrder(Sorter<ChangeField> sorter)
	{
		StringBuilder order = new StringBuilder();
		if(sorter != null && sorter.getSortCount() > 0)
		{
			for(int sc = 0; sc < sorter.getSortCount(); sc++)
			{
				if(sc > 0)
					order.append(", ");
				ChangeField field = sorter.getField(sc);
				switch(field)
				{
				case CHANGE_TYPE:
					order.append("change.subjectType ");
					order.append(sorter.isAscending(sc) ? "ASC" : "DESC");
					order.append(",");
					order.append("change.changeType ");
					order.append(sorter.isAscending(sc) ? "ASC" : "DESC");
					break;
				case CHANGE_TIME:
				case CHANGE_USER:
					order.append("change.");
					order.append(sorter.getField(sc).toString());
					order.append(sorter.isAscending(sc) ? " ASC" : " DESC");
					break;
				}
			}
		}
		else
			order.append("changeTime DESC");
		return order.toString();
	}

	public void disconnect()
	{
		try
		{
			if(theChangeInserter != null)
				theChangeInserter.close();
			if(theCertSetter != null)
				theCertSetter.close();
		} catch(SQLException e)
		{
			log.error("Could not close prepared statement", e);
		} catch(Error e)
		{
			// Keep getting these from an HSQL bug--silence
			if(!e.getMessage().contains("compilation"))
				log.error("Error", e);
		}
		theTransactor.release();
	}
}
