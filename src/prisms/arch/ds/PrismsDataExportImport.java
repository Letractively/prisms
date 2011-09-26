/*
 * PrismsDataExportImport.java Created Sep 16, 2011 by Andrew Butler, PSL
 */
package prisms.arch.ds;

import org.apache.log4j.Logger;

import prisms.arch.PrismsApplication;
import prisms.records.*;
import prisms.util.json.JsonSerialReader;
import prisms.util.json.JsonStreamWriter;
import prisms.util.json.SAJParser;

/**
 * Exports PRISMS data to a hidden (dot-prefixed) file in the exported logs directory or imports the
 * data from there.
 */
public class PrismsDataExportImport
{
	static final Logger log = Logger.getLogger(PrismsDataExportImport.class);

	/** Exports PRISMS data to a file in the exposed logging directory */
	public static class Export
	{
		private final PrismsApplication [] theApps;

		/** @param apps The applications in the PRISMS environment */
		public Export(PrismsApplication [] apps)
		{
			theApps = apps;
		}

		/**
		 * Exports all synchronize-enabled PRISMS data to a file to be imported later
		 * 
		 * @param ui The user interface to communicate with
		 * @param global Whether to export ALL data available to synchronization instead of just the
		 *        local data and local modifications
		 * @return Whether the export succeeded
		 */
		public boolean exportData(prisms.ui.UI ui, boolean global)
		{
			prisms.ui.UI.DefaultProgressInformer pi = new prisms.ui.UI.DefaultProgressInformer();
			pi.setProgressText("Configuring PRISMS applications");
			ui.startTimedTask(pi);
			try
			{
				for(PrismsApplication app : theApps)
				{
					if(!app.isConfigured())
					{
						pi.setProgressText("Configuring " + app.getName());
						// Configure the application. Can't be done except by the PrismsServer
						prisms.util.PrismsServiceConnector conn = new prisms.util.PrismsServiceConnector(
							theApps[0].getEnvironment().getIDs().getLocalInstance().location,
							app.getName(), app.getClients()[0].getName(), "System");
						try
						{
							conn.getConnector().setTrustManager(
								new javax.net.ssl.X509TrustManager()
								{
									public void checkClientTrusted(
										java.security.cert.X509Certificate[] chain, String authType)
										throws java.security.cert.CertificateException
									{
									}

									public void checkServerTrusted(
										java.security.cert.X509Certificate[] chain, String authType)
										throws java.security.cert.CertificateException
									{
									}

									public java.security.cert.X509Certificate[] getAcceptedIssuers()
									{
										return null;
									}
								});
						} catch(java.security.GeneralSecurityException e)
						{
							log.error("Could not set trust manager for service connector", e);
						}
						conn.setUserSource(theApps[0].getEnvironment().getUserSource());
						log.debug("Application " + app.getName()
							+ " is not yet configured. Configuring.");
						try
						{
							conn.getVersion();
						} catch(java.io.IOException e)
						{
							log.debug("Connection failed. App configuration may still succeed", e);
						}
						if(!app.isConfigured())
						{
							ui.error("Could not configure application " + app.getName()
								+ ". Export data cannot proceed.");
							log.error("Could not configure application " + app.getName());
							return false;
						}
					}
				}
				return exportData(ui, pi, global);
			} finally
			{
				pi.setDone();
			}
		}

		boolean exportData(prisms.ui.UI ui, prisms.ui.UI.DefaultProgressInformer pi, boolean global)
		{
			java.io.File exportFile = new java.io.File(theApps[0].getEnvironment().getLogger()
				.getExposedDir()
				+ ".exportedData.dat");
			if(exportFile.exists() && !prisms.util.FileSegmentizerOutputStream.delete(exportFile))
			{
				ui.error("Could not delete data exported on "
					+ prisms.util.PrismsUtils.print(exportFile.lastModified()));
				log.error("Could not delete data exported on "
					+ prisms.util.PrismsUtils.print(exportFile.lastModified()));
				return false;
			}
			prisms.util.FileSegmentizerOutputStream fileStream = null;
			prisms.util.ExportStream exportStream;
			java.io.OutputStreamWriter streamWriter;
			JsonStreamWriter jsw;
			try
			{
				fileStream = new prisms.util.FileSegmentizerOutputStream(exportFile);
				exportStream = new prisms.util.ExportStream(fileStream);
				streamWriter = new java.io.OutputStreamWriter(exportStream);
				// streamWriter = new java.io.OutputStreamWriter(fileStream);
				jsw = new JsonStreamWriter(streamWriter);
			} catch(java.io.IOException e)
			{
				ui.error("Could not write data for export: " + e);
				log.error("Could not write data for export", e);
				if(fileStream != null)
					try
					{
						fileStream.close();
					} catch(java.io.IOException e2)
					{}
				prisms.util.FileSegmentizerOutputStream.delete(exportFile);
				return false;
			}
			boolean success = false;
			try
			{
				jsw.startObject();
				jsw.startProperty("exportTime");
				jsw.writeNumber(Long.valueOf(System.currentTimeMillis()));
				jsw.startProperty("instance");
				jsw.writeNumber(Integer.valueOf(theApps[0].getEnvironment().getIDs().getCenterID()));
				jsw.startProperty("data");
				jsw.startArray();
				java.util.HashSet<String> namespaces = new java.util.HashSet<String>();
				for(PrismsApplication app : theApps)
				{
					for(prisms.arch.event.PrismsProperty<?> property : app.getGlobalProperties())
					{
						if(PrismsSynchronizer.class.isAssignableFrom(property.getType()))
						{
							PrismsSynchronizer sync = (PrismsSynchronizer) app
								.getGlobalProperty(property);
							if(sync == null || !(sync.getKeeper() instanceof DBRecordKeeper))
								continue;
							exportData(ui, sync, jsw, namespaces, pi, global);
						}
					}
				}
				jsw.endArray();
				jsw.endObject();
				jsw.close();
				streamWriter.close();
				fileStream.close();
				success = true;
				ui.info("Data has been exported. On server restart after rebuild,"
					+ " local data will be imported.");
				log.info("Instance "
					+ theApps[0].getEnvironment().getIDs().getLocalInstance().location
					+ ": Data has been exported to " + exportFile.getCanonicalPath());
			} catch(java.io.IOException e)
			{
				ui.error("Data export failed: " + e);
				log.error("Data export failed", e);
			} catch(prisms.records.PrismsRecordException e)
			{
				ui.error("Data export failed: " + e);
				log.error("Data export failed", e);
			} finally
			{
				if(!success)
				{
					try
					{
						fileStream.close();
					} catch(java.io.IOException e2)
					{}
					prisms.util.FileSegmentizerOutputStream.delete(exportFile);
				}
			}
			return success;
		}

		void exportData(prisms.ui.UI ui, PrismsSynchronizer sync, JsonStreamWriter jsw,
			java.util.HashSet<String> namespaces, prisms.ui.UI.DefaultProgressInformer pi,
			boolean global) throws java.io.IOException, PrismsRecordException
		{
			DBRecordKeeper keeper = (DBRecordKeeper) sync.getKeeper();
			String namespace = keeper.getNamespace();
			if(namespaces.contains(namespace))
				return;
			namespaces.add(namespace);
			for(PrismsSynchronizer depend : sync.getDepends())
				exportData(ui, depend, jsw, namespaces, pi, global);
			jsw.startObject();
			jsw.startProperty("namespace");
			jsw.writeString(namespace);
			jsw.startProperty("version");
			String v = null;
			SynchronizeImpl impl = null;
			for(SynchronizeImpl imp : sync.getImpls())
				if(prisms.arch.PrismsConfig.compareVersions(imp.getVersion(), v) > 0)
				{
					impl = imp;
					v = impl.getVersion();
				}

			jsw.writeString(v);
			SyncRecord syncRecord = new SyncRecord(new PrismsCenter("Export"),
				SyncRecord.Type.AUTOMATIC, System.currentTimeMillis(), false);
			pi.setProgressText("Exporting " + namespace + " items");
			ui.startTimedTask(pi);
			PrismsSynchronizer.SyncTransaction syncTrans = sync.transact(v, false, syncRecord,
				false, pi);
			PrismsSynchronizer.PS2ItemWriter itemWriter = new PrismsSynchronizer.PS2ItemWriter(
				syncTrans, jsw, new prisms.records.LatestCenterChange [0]);

			int [] centerIDs = global ? keeper.getAllCenterIDs()
				: new int [] {keeper.getCenterID()};
			SynchronizeImpl.ItemIterator iter = impl.getAllItems(centerIDs, syncRecord.getCenter());
			jsw.startProperty("items");
			jsw.startArray();
			while(iter.hasNext())
				itemWriter.writeItem(iter.next());
			for(PrismsCenter center : keeper.getCenters())
				itemWriter.writeItem(center);
			if(keeper.getAutoPurger() != null)
				itemWriter.writeItem(keeper.getAutoPurger());
			jsw.endArray();

			pi.setProgressText("Exporting " + namespace + " changes");
			prisms.util.Sorter<RecordKeeper.ChangeField> sorter;
			sorter = new prisms.util.Sorter<RecordKeeper.ChangeField>();
			sorter.addSort(RecordKeeper.ChangeField.CHANGE_TIME, true);
			prisms.util.Search changeSearch = global ? null
				: new prisms.records.ChangeSearch.IDRange(Long.valueOf(IDGenerator.getMinID(keeper
					.getCenterID())), Long.valueOf(IDGenerator.getMaxID(keeper.getCenterID())))
					.and(new prisms.records.ChangeSearch.LocalOnlySearch(null));
			prisms.util.LongList changeIDs = new prisms.util.LongList(keeper.search(changeSearch,
				sorter));
			long [] batch = new long [changeIDs.size() < 200 ? changeIDs.size() : 200];
			jsw.startProperty("changes");
			jsw.startArray();
			for(int i = 0; i < changeIDs.size(); i += batch.length)
			{
				if(changeIDs.size() - i < batch.length)
					batch = new long [changeIDs.size() - i];
				changeIDs.arrayCopy(i, batch, 0, batch.length);
				ChangeRecord [] changes = keeper.getItems(batch);
				for(ChangeRecord change : changes)
					if(change.type.subjectType instanceof PrismsChange || impl.shouldSend(change))
						itemWriter.writeChange(change, false);
			}
			jsw.endArray();

			pi.setProgressText("Exporting " + namespace + " sync records");
			jsw.startProperty("syncRecords");
			jsw.startArray();
			for(PrismsCenter center : keeper.getCenters())
				exportSyncRecords(center, keeper, jsw);
			jsw.endArray();

			pi.setProgressText("Exporting " + namespace + " metadata");
			jsw.startProperty("latestChanges");
			jsw.startArray();
			prisms.util.IntList allCenterIDs = new prisms.util.IntList(keeper.getAllCenterIDs());
			for(int i = 0; i < allCenterIDs.size(); i++)
				for(int j = 0; j < allCenterIDs.size(); j++)
				{
					long changeTime = keeper.getLatestChange(allCenterIDs.get(i),
						allCenterIDs.get(j));
					if(changeTime > 0)
					{
						jsw.startObject();
						jsw.startProperty("center");
						jsw.writeNumber(Integer.valueOf(allCenterIDs.get(i)));
						jsw.startProperty("subjectCenter");
						jsw.writeNumber(Integer.valueOf(allCenterIDs.get(i)));
						jsw.startProperty("latestChange");
						jsw.writeNumber(Long.valueOf(changeTime));
						jsw.endObject();
					}
				}
			jsw.endArray();
			jsw.endObject();
		}

		void exportSyncRecords(PrismsCenter center, DBRecordKeeper keeper, JsonStreamWriter jsw)
			throws java.io.IOException, PrismsRecordException
		{
			jsw.startObject();
			jsw.startProperty("id");
			jsw.writeNumber(Integer.valueOf(center.getID()));
			jsw.startProperty("centerID");
			jsw.writeNumber(Integer.valueOf(center.getCenterID()));
			jsw.startProperty("syncRecords");
			jsw.startArray();
			for(SyncRecord record : keeper.getSyncRecords(center, null))
			{
				jsw.startObject();
				jsw.startProperty("id");
				jsw.writeNumber(Integer.valueOf(record.getID()));
				jsw.startProperty("parallelID");
				jsw.writeNumber(Integer.valueOf(record.getParallelID()));
				jsw.startProperty("syncType");
				jsw.writeString(record.getSyncType().toString());
				jsw.startProperty("time");
				jsw.writeNumber(Long.valueOf(record.getSyncTime()));
				jsw.startProperty("isImport");
				jsw.writeBoolean(record.isImport());
				jsw.startProperty("syncError");
				jsw.writeString(record.getSyncError());
				jsw.startProperty("associated");
				jsw.startArray();
				for(long assoc : keeper.getChangeIDs(null, null, null, null, record, null))
				{
					jsw.startObject();
					jsw.startProperty("id");
					jsw.writeNumber(Long.valueOf(assoc));
					jsw.startProperty("error");
					jsw.writeBoolean(!keeper.hasSuccessfulChange(assoc));
					jsw.endObject();
				}
				jsw.endArray();
				jsw.endObject();
			}
			jsw.endArray();
			jsw.endObject();
		}
	}

	/** Imports PRISMS data from a file in the exposed logging directory */
	public static class Import
	{
		private long theDataAgeThreshold;

		private JsonSerialReader theJsonReader;

		private java.io.Reader theFileReader;

		private long theExportDataTime;

		/** Creates an importer */
		public Import()
		{
			theDataAgeThreshold = 7L * 24 * 60 * 60 * 1000;
		}

		/**
		 * @param thresh The threshold age after which exported data is deemed to be too old and
		 *        will not be imported. The default is 7 days.
		 */
		public void setDataAgeThreshold(long thresh)
		{
			theDataAgeThreshold = thresh;
		}

		/**
		 * Reads the exported file and returns the instance ID stored therein
		 * 
		 * @param exposedDir The exposed logging directory to look for the exported data in
		 * @return The instance ID stored in the export file or -1 if the file did not exist or
		 *         could not be parsed as an export file
		 */
		public int getInstanceID(String exposedDir)
		{
			java.io.File importFile = new java.io.File(exposedDir + ".exportedData.dat");
			if(!importFile.exists())
				return -1;
			prisms.util.FileSegmentizerInputStream fileStream = null;
			prisms.util.ImportStream importStream;
			java.io.InputStreamReader streamReader;
			JsonSerialReader jsr;
			try
			{
				fileStream = new prisms.util.FileSegmentizerInputStream(importFile);
				importStream = new prisms.util.ImportStream(fileStream);
				streamReader = new java.io.InputStreamReader(importStream);
				jsr = new JsonSerialReader(streamReader);
			} catch(java.io.IOException e)
			{
				log.error("Could not read data for import", e);
				if(fileStream != null)
					try
					{
						fileStream.close();
					} catch(java.io.IOException e2)
					{}
				return -1;
			}
			boolean success = false;
			try
			{
				jsr.startObject();
				if(!"exportTime".equals(jsr.getNextProperty()))
				{
					log.error("Expected exportTime in exported data");
					return -1;
				}
				theExportDataTime = jsr.parseLong();
				if(System.currentTimeMillis() - theExportDataTime > theDataAgeThreshold)
				{
					log.error("Exported data is too old ("
						+ prisms.util.PrismsUtils.print(theExportDataTime) + "). Import canceled.");
					return -1;
				}
				if(!"instance".equals(jsr.getNextProperty()))
					return -1;
				int ret = jsr.parseInt();
				success = true;
				theJsonReader = jsr;
				theFileReader = streamReader;
				return ret;
			} catch(Exception e)
			{
				log.error("Could not read exported data", e);
				return -1;
			} finally
			{
				if(!success)
					try
					{
						fileStream.close();
					} catch(java.io.IOException e)
					{}
			}
		}

		/** @return The time at which this importer's data was exported */
		public long getExportDataTime()
		{
			return theExportDataTime;
		}

		/**
		 * Imports data stored in an export file
		 * 
		 * @param apps The applications in the PRISMS environment
		 * @return Whether the import succeeded
		 */
		public boolean importData(PrismsApplication [] apps)
		{
			try
			{
				java.util.HashMap<String, PrismsSynchronizer> syncs;
				java.util.HashMap<String, PrismsApplication> appsByNS;
				syncs = new java.util.HashMap<String, PrismsSynchronizer>();
				appsByNS = new java.util.HashMap<String, PrismsApplication>();
				for(PrismsApplication app : apps)
				{
					for(prisms.arch.event.PrismsProperty<?> property : app.getGlobalProperties())
					{
						if(PrismsSynchronizer.class.isAssignableFrom(property.getType()))
						{
							PrismsSynchronizer sync = (PrismsSynchronizer) app
								.getGlobalProperty(property);
							if(sync == null || !(sync.getKeeper() instanceof DBRecordKeeper))
								continue;
							syncs.put(((DBRecordKeeper) sync.getKeeper()).getNamespace(), sync);
							appsByNS.put(((DBRecordKeeper) sync.getKeeper()).getNamespace(), app);
						}
					}
				}
				String propName;
				do
				{
					propName = theJsonReader.getNextProperty();
				} while(propName != null && !"data".equals(propName));
				if(propName == null)
				{
					log.error("No \"data\" property found in exported data");
					return false;
				}

				theJsonReader.startArray();
				JsonSerialReader.JsonParseItem item;
				StringBuilder message = new StringBuilder();
				message.append("Imported PRISMS data from ")
					.append(prisms.util.PrismsUtils.print(theExportDataTime)).append(" (");
				prisms.util.PrismsUtils.printTimeLength(System.currentTimeMillis()
					- theExportDataTime, message, false);
				message.append(" old).");
				for(item = theJsonReader.getNextItem(true); item instanceof JsonSerialReader.ObjectItem; item = theJsonReader
					.getNextItem(true))
				{
					JsonSerialReader.StructState syncState = theJsonReader.save();
					try
					{
						/* The current state is now just past the beginning of one synchronizer's exported data */
						if(!"namespace".equals(theJsonReader.getNextProperty()))
						{
							message.append("\n\tNamespace expected in sync data set");
							continue;
						}
						String namespace = theJsonReader.parseString();
						PrismsSynchronizer sync = syncs.get(namespace);
						if(sync == null)
						{
							message
								.append("\n\tNo synchronizer loaded with namespace " + namespace);
							continue;
						}
						message.append("\n\tImporting data for namespace \"").append(namespace)
							.append('"');
						String version;
						if(!"version".equals(theJsonReader.getNextProperty()))
							version = null;
						else
							version = theJsonReader.parseString();
						boolean preAI = ((DBRecordKeeper) sync.getKeeper()).hasAbsoluteIntegrity();
						((DBRecordKeeper) sync.getKeeper()).setAbsoluteIntegrity(true);
						try
						{
							importData(appsByNS.get(namespace), sync, version, theJsonReader,
								message);
						} catch(Exception e)
						{
							message.append("\n\t\tImport failed: ").append(e);
							log.error("Could not import data for synchronizer " + namespace, e);
						} finally
						{
							((DBRecordKeeper) sync.getKeeper()).setAbsoluteIntegrity(preAI);
						}
					} finally
					{
						theJsonReader.endObject(syncState);
					}
				}
				log.info(message.toString());
				return true;
			} catch(Exception e)
			{
				log.error("Could not read or parse exported data", e);
				return false;
			} finally
			{
				close();
			}
		}

		/** Closes this importer's resources */
		public void close()
		{
			if(theFileReader != null)
				try
				{
					theFileReader.close();
				} catch(java.io.IOException e)
				{
					log.error("Could not close import stream", e);
				}
		}

		void importData(PrismsApplication app, PrismsSynchronizer sync, String version,
			JsonSerialReader jsr, StringBuilder message) throws java.io.IOException,
			SAJParser.ParseException
		{
			SyncRecord syncRecord = new SyncRecord(new PrismsCenter("Export"),
				SyncRecord.Type.AUTOMATIC, System.currentTimeMillis(), false);
			PrismsSynchronizer.SyncTransaction trans = sync.transact(version, true, syncRecord,
				false, new prisms.ui.UI.DefaultProgressInformer());

			// Items
			PrismsSynchronizer.PS2ItemReader itemReader = new PrismsSynchronizer.PS2ItemReader(
				trans);
			if(!jsr.goToProperty("items"))
			{
				message.append("\n\t\tNo items property in sync data");
				return;
			}
			int items = 0;
			org.json.simple.JSONObject obj;
			jsr.startArray();
			obj = jsr.parseObject();
			while(obj != null)
			{
				items++;
				try
				{
					itemReader.read(obj);
				} catch(PrismsRecordException e)
				{
					log.error("Error parsing item", e);
				}
				obj = jsr.parseObject();
			}
			jsr.endArray(null);
			message.append("\n\t\t").append(items).append(" items");

			PrismsCenter [] centers;
			try
			{
				centers = sync.getKeeper().getCenters();
			} catch(PrismsRecordException e)
			{
				message.append("\n\t\t").append("Could not get centers");
				log.error("Could not get centers", e);
				centers = null;
			}
			if(trans.getImpl().getCentersProperty() != null)
				app.setGlobalProperty(trans.getImpl().getCentersProperty(), centers,
					RecordUtils.TRANSACTION_EVENT_PROP, new prisms.records.RecordsTransaction());

			// Changes
			PrismsSynchronizer.ChangeReader changeReader = new PrismsSynchronizer.ChangeReader(
				trans, null, itemReader, 0);
			if(!jsr.goToProperty("changes"))
			{
				message.append("\n\t\tNo changes property in sync data");
				return;
			}
			int changes = 0;
			jsr.startArray();
			obj = jsr.parseObject();
			while(obj != null)
			{
				changes++;
				changeReader.readChange(obj);
				obj = jsr.parseObject();
			}
			jsr.endArray(null);
			message.append(", ").append(changes).append(" changes");
			changeReader = null;
			itemReader = null;

			// Sync records
			if(centers != null)
			{
				if(!jsr.goToProperty("syncRecords"))
				{
					message.append("\n\t\t").append("No syncRecords property in sync data");
					return;
				}
				int syncRecords = 0;
				jsr.startArray();
				while(jsr.getNextItem(true) instanceof JsonSerialReader.ObjectItem)
				{
					JsonSerialReader.StructState centerState = jsr.save();
					try
					{
						if(!jsr.goToProperty("id"))
						{
							message.append("\n\t\tID missing in sync records group");
							break;
						}
						int centerID = jsr.parseInt();
						PrismsCenter center = null;
						for(PrismsCenter c : centers)
							if(c.getID() == centerID)
							{
								center = c;
								break;
							}
						if(center == null)
						{
							log.warn("No such center with ID " + centerID);
							continue;
						}
						if(!jsr.goToProperty("syncRecords"))
						{
							message.append("\n\t\tsyncRecords missing in sync records group");
							continue;
						}
						jsr.startArray();
						while(jsr.getNextItem(true) instanceof JsonSerialReader.ObjectItem)
						{
							JsonSerialReader.StructState srState = jsr.save();
							try
							{
								if(!jsr.goToProperty("id"))
								{
									log.warn("No id property in sync record");
									continue;
								}
								int srID = jsr.parseInt();
								if(!jsr.goToProperty("parallelID"))
								{
									log.warn("No parallelID property in sync record");
									continue;
								}
								int parallelID = jsr.parseInt();
								if(!jsr.goToProperty("syncType"))
								{
									log.warn("No syncType property in sync record");
									continue;
								}
								String typeName = jsr.parseString();
								SyncRecord.Type type = SyncRecord.Type.byName(typeName);
								if(type == null)
								{
									log.warn("Unrecognized sync type in sync record: " + typeName);
									continue;
								}
								if(!jsr.goToProperty("time"))
								{
									log.warn("No time property in sync record");
									continue;
								}
								long time = jsr.parseLong();
								if(!jsr.goToProperty("isImport"))
								{
									log.warn("No isImport property in sync record");
									continue;
								}
								boolean isImport = jsr.parseBoolean();
								if(!jsr.goToProperty("syncError"))
								{
									log.warn("No syncError property in sync record");
									continue;
								}
								Object syncError = jsr.parseNext();
								SyncRecord sr = new SyncRecord(center, type, time, isImport);
								sr.setID(srID);
								sr.setParallelID(parallelID);
								if(syncError instanceof String)
									sr.setSyncError((String) syncError);
								else if(syncError == JsonSerialReader.NULL)
									sr.setSyncError(null);
								else
									String.class.cast(syncError);
								try
								{
									sync.getKeeper().putSyncRecord(sr);
								} catch(PrismsRecordException e)
								{
									log.error("Could not persist sync record", e);
									continue;
								}
								syncRecords++;
								if(!jsr.goToProperty("associated"))
								{
									log.warn("No associated property in sync record");
									continue;
								}
								prisms.util.LongList assocIDs = new prisms.util.LongList();
								prisms.util.BooleanList assocErrors = new prisms.util.BooleanList();
								jsr.startArray();
								while(jsr.getNextItem(true) instanceof JsonSerialReader.ObjectItem)
								{
									if(!jsr.goToProperty("id"))
									{
										log.warn("No id for associated change");
										jsr.endObject(null);
										continue;
									}
									assocIDs.add(jsr.parseLong());
									if(jsr.goToProperty("error"))
										assocErrors.add(jsr.parseBoolean());
									else
										assocErrors.add(false);
									jsr.endObject(null);
								}
								jsr.endArray(null);
								ChangeRecord [] assocChanges;
								try
								{
									assocChanges = sync.getKeeper().getItems(assocIDs.toArray());
								} catch(PrismsRecordException e)
								{
									log.error("Could not get associated changes", e);
									continue;
								}
								for(int i = 0; i < assocChanges.length; i++)
								{
									if(assocChanges[i] != null)
										try
										{
											sync.getKeeper().associate(assocChanges[i], sr,
												assocErrors.get(i));
										} catch(PrismsRecordException e)
										{
											log.error("Could not associated change", e);
										}
								}
							} finally
							{
								jsr.endObject(srState);
							}
						}
						jsr.endArray(null);
					} finally
					{
						jsr.endObject(centerState);
					}
				}
				jsr.endArray(null);
				message.append(", ").append(syncRecords).append(" sync records");
			}

			// Latest changes
			if(!jsr.goToProperty("latestChanges"))
			{
				message.append("\n\t\t").append("No latestChanges property in sync data");
				return;
			}
			jsr.startArray();
			while(jsr.getNextItem(true) instanceof JsonSerialReader.ObjectItem)
			{
				JsonSerialReader.StructState lcState = jsr.save();
				try
				{
					if(!jsr.goToProperty("center"))
					{
						log.warn("No center for latest change entry");
						continue;
					}
					int centerID = jsr.parseInt();
					if(!jsr.goToProperty("subjectCenter"))
					{
						log.warn("No subjectCenter for latest change entry");
						continue;
					}
					int subjectCenter = jsr.parseInt();
					if(!jsr.goToProperty("latestChange"))
					{
						log.warn("No latestChange for latest change entry");
						continue;
					}
					long changeTime = jsr.parseLong();
					try
					{
						sync.getKeeper().setLatestChange(centerID, subjectCenter, changeTime);
					} catch(PrismsRecordException e)
					{
						log.error("Could not set latest change", e);
					}
				} finally
				{
					jsr.endObject(lcState);
				}
			}
		}
	}
}
