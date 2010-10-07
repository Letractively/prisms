/*
 * PrismsSynchronizer.java Created Jul 29, 2010 by Andrew Butler, PSL
 */
package prisms.records2;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.records2.Synchronize2Impl.ItemGetter;
import prisms.records2.Synchronize2Impl.ItemWriter;
import prisms.util.ArrayUtils;
import prisms.util.json.SAJParser.ParseState;

/**
 * Provides the ability to keep two sets of java objects synchronized across a network connection
 */
public class PrismsSynchronizer2
{
	/** Listens for new sync records created by the synchronizer */
	public interface SyncListener
	{
		/** @param record The new synchronization attempt */
		void syncAttempted(SyncRecord record);

		/** @param record The sync record that was changed */
		void syncChanged(SyncRecord record);
	}

	/**
	 * A default implementation of SyncListener that fires application-wide events whenever
	 * synchronization is attempted or a sync record changed
	 */
	public static class DefaultSyncListener implements SyncListener
	{
		private prisms.arch.PrismsApplication theApp;

		/**
		 * Creates a DefaultSyncListener
		 * 
		 * @param app The application to use to fire the events
		 */
		public DefaultSyncListener(prisms.arch.PrismsApplication app)
		{
			theApp = app;
		}

		public void syncAttempted(SyncRecord record)
		{
			theApp.fireGlobally(null, new prisms.arch.event.PrismsEvent("syncAttempted", "record",
				record));
		}

		public void syncChanged(SyncRecord record)
		{
			theApp.fireGlobally(null, new prisms.arch.event.PrismsEvent("syncAttemptChanged",
				"record", record));
		}
	}

	/** The JSON property within a synchronization stream that the list of all items is sent as */
	public static final String ALL_ITEMS = "allItems";

	/**
	 * The JSON property within a synchronization stream that the list of change records is sent as
	 */
	public static final String CHANGES = "changes";

	/**
	 * The JSON property within a synchronization stream that the center ID of the remote center is
	 * sent as
	 */
	public static final String CENTER_ID = "centerID";

	/**
	 * The JSON property within a synchronization stream that the ID of the synchronization record
	 * on the remote center is sent as
	 */
	public static final String PARALLEL_ID = "parallelID";

	static final Logger log = Logger.getLogger(PrismsSynchronizer2.class);

	private final class RuntimeWrapper extends RuntimeException
	{
		RuntimeWrapper(String message, Exception cause)
		{
			super(message, cause);
		}
	}

	class ObjectID
	{
		final String type;

		final long id;

		ObjectID(String aType, long anID)
		{
			if(aType == null)
				throw new NullPointerException("Null object type");
			type = aType;
			id = anID;
		}

		public boolean equals(Object o)
		{
			if(!(o instanceof ObjectID))
				return false;
			return ((ObjectID) o).type.equals(type) && ((ObjectID) o).id == id;
		}

		public int hashCode()
		{
			return type.hashCode() * 13 + (int) (id / Record2Utils.theCenterIDRange * 7)
				+ (int) (id % Record2Utils.theCenterIDRange);
		}

		public String toString()
		{
			return type + "(" + id + ")";
		}
	}

	class PS2ItemWriter implements ItemWriter
	{
		final prisms.util.json.JsonStreamWriter theWriter;

		private final SyncRecord theSyncRecord;

		private final LatestCenterChange [] theLatestChanges;

		private boolean storeSyncRecord;

		private final java.util.HashSet<ObjectID> theBag;

		private boolean optimizesForOldItems;

		private java.util.HashSet<ObjectID> theNewItems;

		private java.util.HashSet<ObjectID> theOldItems;

		PS2ItemWriter(Writer writer, SyncRecord syncRecord, LatestCenterChange [] changes,
			boolean storeSR)
		{
			theWriter = new prisms.util.json.JsonStreamWriter(writer);
			theSyncRecord = syncRecord;
			theBag = new java.util.HashSet<ObjectID>();
			theLatestChanges = changes;
			storeSyncRecord = storeSR;
			theNewItems = new java.util.HashSet<ObjectID>();
			theOldItems = new java.util.HashSet<ObjectID>();
		}

		void start() throws IOException
		{
			theWriter.startArray();
		}

		/**
		 * Writes an item to the stream
		 * 
		 * @param item The item to write
		 * @throws IOException If an error occurs writing to the stream
		 * @throws PrismsRecordException If an error occurs serializing the data
		 */
		public void writeItem(Object item) throws IOException, PrismsRecordException
		{
			if(item == null)
				theWriter.writeNull();
			else
			{
				String type = getImpl().getType(item.getClass());
				long id = type == null ? -1 : getImpl().getID(item);
				ObjectID oid = null;
				if(type != null)
					oid = new ObjectID(type, id);
				if(type == null)
					doWrite(item);
				else if(theBag.contains(oid))
				{
					theWriter.startObject();
					theWriter.startProperty("type");
					theWriter.writeString(type);
					theWriter.startProperty("id");
					theWriter.writeNumber(new Long(id));
					theWriter.startProperty("-syncstore-");
					theWriter.writeBoolean(true);
					theWriter.endObject();
				}
				else if(optimizesForOldItems && !isNewItem(item, oid))
				{
					theBag.add(oid);
					theWriter.startObject();
					theWriter.startProperty("type");
					theWriter.writeString(type);
					theWriter.startProperty("id");
					theWriter.writeNumber(new Long(id));
					theWriter.startProperty("-localstore-");
					theWriter.writeBoolean(true);
					PrismsSynchronizer2.this.writeItem(item, theWriter, this, true);
					theWriter.endObject();
				}
				else
				{
					theBag.add(oid);
					doWrite(item);
				}
			}
		}

		boolean isNewItem(Object item, ObjectID id) throws PrismsRecordException
		{
			if(theNewItems.contains(id))
				return true;
			if(theOldItems.contains(id))
				return false;
			int subjectCenter = Record2Utils.getCenterID(id.id);
			java.util.HashMap<Integer, Long> latestChanges = new java.util.HashMap<Integer, Long>();
			for(LatestCenterChange change : theLatestChanges)
				if(change.getSubjectCenter() == subjectCenter)
				{
					if(getKeeper().getLatestChange(change.getCenterID(), subjectCenter) < change
						.getLatestChange())
					{ // There may be a remote change that deleted the item--need to send fully
						theNewItems.add(id);
						return true;
					}
					latestChanges.put(new Integer(change.getCenterID()),
						new Long(change.getLatestChange()));
				}
			if(latestChanges.size() == 0)
			{
				theNewItems.add(id);
				return true;
			}
			long [] history = getKeeper().getHistory(item);
			getKeeper().sortChangeIDs(history, false);
			long changeTime = System.currentTimeMillis();
			/* If there is a creation change in the history of the item that the remote center has
			 * not yet received, then the item must be sent completely */
			boolean ret = false;
			for(int h = 0; h < history.length; h++)
			{
				Integer centerID = new Integer(Record2Utils.getCenterID(history[h]));
				Long lcTime = latestChanges.get(centerID);
				if(lcTime != null && lcTime.longValue() >= changeTime)
					continue;
				ChangeRecord record = getKeeper().getChanges(new long [] {history[h]})[0];
				changeTime = record.time;
				if(lcTime != null && lcTime.longValue() >= record.time)
					continue;
				if(record.majorSubject.equals(item))
				{
					if(record.type.changeType != null || record.type.additivity <= 0)
						continue;
				}
				else if(record.minorSubject.equals(item))
				{
					if(record.type.additivity <= 0)
						continue;
				}
				ret = true;
				break;
			}
			if(!ret)
				for(Object depend : getDepends(item))
				{
					String type = getType(depend);
					if(type == null)
					{
						log.error("Type of depend " + depend.getClass() + " is null");
						continue;
					}
					ObjectID dependID = new ObjectID(type, getID(depend));
					if(isNewItem(depend, dependID))
					{
						ret = true;
						break;
					}
				}
			if(ret)
				theNewItems.add(id);
			else
				theOldItems.add(id);
			return ret;
		}

		private void doWrite(Object item) throws IOException, PrismsRecordException
		{
			if(item == null)
				theWriter.writeNull();
			else if(item instanceof Character)
				theWriter.writeString("" + ((Character) item).charValue());
			else if(item instanceof Number)
				theWriter.writeNumber((Number) item);
			else if(item instanceof String)
				theWriter.writeString((String) item);
			else if(item instanceof Boolean)
				theWriter.writeBoolean(((Boolean) item).booleanValue());
			else
			{
				String type = getType(item);
				if(type != null)
				{
					long id = getID(item);
					theWriter.startObject();
					theWriter.startProperty("type");
					theWriter.writeString(type);
					theWriter.startProperty("id");
					theWriter.writeNumber(new Long(id));
					PrismsSynchronizer2.this.writeItem(item, theWriter, this, false);
					theWriter.endObject();
				}
				else
					PrismsSynchronizer2.this.writeItem(item, theWriter, this, false);
			}
		}

		void writeChange(prisms.records2.ChangeRecord change, boolean preError) throws IOException
		{
			optimizesForOldItems = !preError;
			try
			{
				theWriter.startObject();
				theWriter.startProperty("id");
				theWriter.writeNumber(new Long(change.id));
				theWriter.startProperty("time");
				theWriter.writeNumber(new Long(change.time));
				boolean error = false;
				if(change instanceof ChangeRecordError)
				{
					theWriter.startProperty("user");
					theWriter.writeNumber(new Long(change.user.getID()));
					theWriter.startProperty("error");
					theWriter.writeBoolean(true);
					ChangeRecordError err = (ChangeRecordError) change;
					theWriter.startProperty("subjectType");
					theWriter.writeString(err.getSubjectType());
					if(err.getChangeType() != null)
					{
						theWriter.startProperty("changeType");
						theWriter.writeString(err.getChangeType());
					}
					theWriter.startProperty("additivity");
					theWriter.writeString(err.getAdditivity() > 0 ? "+" : (err.getAdditivity() < 0
						? "-" : "0"));
					theWriter.startProperty("majorSubject");
					theWriter.writeNumber(new Long(err.getMajorSubjectID()));
					if(err.getMinorSubjectID() >= 0)
					{
						theWriter.startProperty("minorSubject");
						theWriter.writeNumber(new Long(err.getMinorSubjectID()));
					}
					if(err.getData1ID() >= 0)
					{
						theWriter.startProperty("data1");
						theWriter.writeNumber(new Long(err.getData1ID()));
					}
					if(err.getData2ID() >= 0)
					{
						theWriter.startProperty("data2");
						theWriter.writeNumber(new Long(err.getData2ID()));
					}
					if(err.getSerializedPreValue() != null)
					{
						theWriter.startProperty("preValue");
						if(err.getSerializedPreValue() instanceof Number)
							theWriter.writeNumber((Number) err.getSerializedPreValue());
						else
							theWriter.writeString((String) err.getSerializedPreValue());
					}
				}
				else
				{
					theWriter.startProperty("subjectType");
					theWriter.writeString(change.type.subjectType.name());
					if(change.type.changeType != null)
					{
						theWriter.startProperty("changeType");
						theWriter.writeString(change.type.changeType.name());
					}
					theWriter.startProperty("additivity");
					theWriter.writeString(change.type.additivity > 0 ? "+"
						: (change.type.additivity < 0 ? "-" : "0"));
					try
					{
						theWriter.startProperty("majorSubject");
						long id = getID(change.majorSubject);
						ObjectID oid = new ObjectID(getType(change.majorSubject), id);
						if(theBag.contains(oid)
							|| (!preError && !isNewItem(change.majorSubject, oid)))
							theWriter.writeNumber(new Long(id));
						else
							writeItem(change.majorSubject);

						theWriter.startProperty("user");
						theWriter.writeNumber(new Long(change.user.getID()));

						if(change.data1 != null)
						{
							theWriter.startProperty("data1");
							id = getID(change.data1);
							oid = new ObjectID(getType(change.data1), id);
							if(theBag.contains(oid) || (!preError && !isNewItem(change.data1, oid)))
								theWriter.writeNumber(new Long(id));
							else
								writeItem(change.data1);
						}

						if(change.data2 != null)
						{
							theWriter.startProperty("data2");
							id = getID(change.data2);
							oid = new ObjectID(getType(change.data2), id);
							if(theBag.contains(oid) || (!preError && !isNewItem(change.data2, oid)))
								theWriter.writeNumber(new Long(id));
							else
								writeItem(change.data2);
						}

						if(change.minorSubject != null)
						{
							theWriter.startProperty("minorSubject");
							id = getID(change.minorSubject);
							oid = new ObjectID(getType(change.minorSubject), id);
							if(theBag.contains(oid)
								|| (!preError && !isNewItem(change.minorSubject, oid)))
								theWriter.writeNumber(new Long(id));
							else
								writeItem(change.minorSubject);
						}

						if(change.previousValue != null)
						{
							theWriter.startProperty("preValue");
							if(change.type.changeType.isObjectIdentifiable())
							{
								id = getID(change.previousValue);
								oid = new ObjectID(getType(change.previousValue), id);
								if(theBag.contains(oid)
									|| (!preError && !isNewItem(change.previousValue, oid)))
									theWriter.writeNumber(new Long(id));
								else
									writeItem(change.previousValue);
							}
							else
								writeItem(change.previousValue);
						}
						Object currentValue = null;
						if(change.type.changeType != null)
							currentValue = getCurrentValue(change);
						if(currentValue != null)
						{
							theWriter.startProperty("currentValue");
							writeItem(currentValue);
						}
					} catch(PrismsRecordException e)
					{
						log.error("Could not write item for change " + change + "(" + change.id
							+ ")", e);
						error = true;
					}
					if(storeSyncRecord)
						try
						{
							getKeeper().associate(change, theSyncRecord, error);
						} catch(PrismsRecordException e)
						{
							log.error("Could not associate change " + change.id
								+ " with sync record " + theSyncRecord, e);
						}
				}
				theWriter.endObject();
			} finally
			{
				optimizesForOldItems = true;
			}
		}

		void writeSkippedChange() throws IOException
		{
			theWriter.startObject();
			theWriter.startProperty("skipped");
			theWriter.writeBoolean(true);
			theWriter.endObject();
		}

		void end() throws IOException
		{
			theWriter.endArray();
		}
	}

	private class PS2ItemReader extends prisms.util.json.SAJParser.DefaultHandler implements
		prisms.records2.Synchronize2Impl.ItemReader, prisms.records2.Synchronize2Impl.ItemGetter
	{
		/** Writes content to an output as it is read */
		private class ParseReader extends java.io.Reader
		{
			private final java.io.Reader theRead;

			private final java.io.Writer theWrite;

			ParseReader(java.io.Reader reader, java.io.Writer writer)
			{
				theRead = reader;
				theWrite = writer;
			}

			@Override
			public int read(char [] cbuf, int off, int len) throws IOException
			{
				len = theRead.read(cbuf, off, len);
				theWrite.write(cbuf, off, len);
				return len;
			}

			@Override
			public void close() throws IOException
			{
				theWrite.close();
			}
		}

		/**
		 * A reference is stored when the ID of an object is parsed but its content should be parsed
		 * later
		 */
		private class Reference
		{
			final Object theRef;

			final ObjectID theID;

			final JSONObject theJson;

			final boolean isNew;

			Reference(Object ref, ObjectID id, JSONObject json, boolean _new)
			{
				theRef = ref;
				theID = id;
				theJson = json;
				isNew = _new;
			}
		}

		private final SyncInputHandler theSyncInput;

		private final SyncRecord theSyncRecord;

		private prisms.ui.UI.DefaultProgressInformer thePI;

		private final java.util.HashMap<ObjectID, Integer> theObjectPositions;

		private final java.util.LinkedHashSet<ObjectID> theExportedObjects;

		private final ObjectBag theBag;

		private prisms.util.RandomAccessTextFile theFile;

		private int thePosition;

		private String theType;

		private int [] theCenterIDs;

		private java.util.HashMap<Integer, Reference []> theReferences;

		private int theDepth;

		private boolean [] theNewItem;

		PS2ItemReader(SyncInputHandler syncInput, SyncRecord record,
			prisms.ui.UI.DefaultProgressInformer pi)
		{
			theSyncInput = syncInput;
			theSyncRecord = record;
			thePI = pi;
			theObjectPositions = new java.util.HashMap<ObjectID, Integer>();
			theExportedObjects = new java.util.LinkedHashSet<ObjectID>();
			theBag = new ObjectBag();
			theCenterIDs = new int [0];
			theReferences = new java.util.HashMap<Integer, Reference []>();
			theNewItem = new boolean [1];
		}

		void store(String type, long id, Object item)
		{
			theBag.add(type, id, item);
		}

		public Object read(JSONObject json) throws PrismsRecordException
		{
			if(json == null)
				return null;
			Object value = null;
			theDepth++;
			try
			{
				String type = (String) json.get("type");
				long id = ((Number) json.get("id")).longValue();
				value = theBag.get(type, id);
				if(value != null)
				{}
				else if(Boolean.TRUE.equals(json.get("-syncstore-")))
				{
					value = getItem(type, id);
					parseEmptyContent();
				}
				else if(Boolean.TRUE.equals(json.get("-localstore-")))
				{
					theNewItem[0] = false;
					value = PrismsSynchronizer2.this.parseID(json, this, theNewItem);
					if(theNewItem[0])
						throw new PrismsRecordException("Item " + type + "/" + id
							+ " not present in data source");
					theBag.add(type, id, value);
				}
				else
				{
					theNewItem[0] = false;
					value = PrismsSynchronizer2.this.parseID(json, this, theNewItem);
					theBag.add(type, id, value);
					parseEmptyContent();
					if(type != null)
					{
						if(theDepth == 1)
							parseContent(value, new ObjectID(type, id), json, theNewItem[0]);
						else
							addEmptyContent(value, new ObjectID(type, id), json, theNewItem[0]);
						parseEmptyContent();
					}
				}
			} finally
			{
				theDepth--;
			}
			return value;
		}

		/**
		 * Parses the identity of an item
		 * 
		 * @param json The JSON to parse the item from
		 * @return Whether the item was created new as a result of this call
		 * @throws PrismsRecordException If an error occurs parsing the item
		 */
		boolean parseID(JSONObject json) throws PrismsRecordException
		{
			if(Boolean.TRUE.equals(json.get("-syncstore-")))
				return false;
			String type = (String) json.get("type");
			long id = ((Number) json.get("id")).longValue();
			if(theBag.get(type, id) != null)
				return false;
			theNewItem[0] = false;
			Object val = PrismsSynchronizer2.this.parseID(json, this, theNewItem);
			theBag.add(type, id, val);
			return theNewItem[0];
		}

		/**
		 * Parses the content of a newly created item
		 * 
		 * @param json The JSON to parse the item from
		 * @throws PrismsRecordException If an error occurs parsing the item or if the item's
		 *         identity has not been parsed previously
		 */
		void parseContent(JSONObject json) throws PrismsRecordException
		{
			String type = (String) json.get("type");
			long id = ((Number) json.get("id")).longValue();
			Object val = theBag.get(type, id);
			if(val == null)
				throw new PrismsRecordException("No parsed item " + type + "/" + id);
			PrismsSynchronizer2.this.parseContent(val, json, true, this);
		}

		private void parseContent(Object value, ObjectID id, JSONObject json, boolean newItem)
			throws PrismsRecordException
		{
			if(newItem || theExportedObjects.contains(id))
				PrismsSynchronizer2.this.parseContent(value, json, newItem, this);
		}

		private void addEmptyContent(Object ref, ObjectID id, JSONObject json, boolean isNew)
			throws PrismsRecordException
		{
			Reference [] refs = theReferences.get(new Integer(theDepth));
			refs = ArrayUtils.add(refs, new Reference(ref, id, json, isNew));
			theReferences.put(new Integer(theDepth), refs);
			parseDeepContent(json);
		}

		private void parseDeepContent(JSONObject json) throws PrismsRecordException
		{
			for(Object val : json.values())
			{
				if(val instanceof org.json.simple.JSONArray)
					parseDeepContent((org.json.simple.JSONArray) val);
				else if(val instanceof JSONObject)
				{
					JSONObject jsonVal = (JSONObject) val;
					if(jsonVal.get("type") instanceof String && jsonVal.get("id") instanceof Number
						&& !Boolean.TRUE.equals(jsonVal.get("-syncstore-"))
						&& !Boolean.TRUE.equals(jsonVal.get("-localstore-")))
						read(jsonVal);
				}
			}
		}

		private void parseDeepContent(org.json.simple.JSONArray json) throws PrismsRecordException
		{
			for(Object val : json)
			{
				if(val instanceof org.json.simple.JSONArray)
					parseDeepContent((org.json.simple.JSONArray) val);
				else if(val instanceof JSONObject)
				{
					JSONObject jsonVal = (JSONObject) val;
					if(jsonVal.get("type") instanceof String && jsonVal.get("id") instanceof Number
						&& !Boolean.TRUE.equals(jsonVal.get("-syncstore-"))
						&& !Boolean.TRUE.equals(jsonVal.get("-localstore-")))
						read(jsonVal);
				}
			}
		}

		private void parseEmptyContent() throws PrismsRecordException
		{
			Reference [] refs = theReferences.get(new Integer(theDepth + 1));
			if(refs == null)
				return;
			theReferences.put(new Integer(theDepth + 1), null);
			for(Reference ref : refs)
				parseContent(ref.theRef, ref.theID, ref.theJson, ref.isNew);
		}

		void parse(java.io.Reader reader, int itemCount) throws IOException,
			prisms.util.json.SAJParser.ParseException
		{
			java.io.File tempFile;
			if(theFile == null)
			{
				tempFile = java.io.File.createTempFile("SyncData" + hashCode(), null);
				tempFile.deleteOnExit();
				theFile = new prisms.util.RandomAccessTextFile(tempFile);
			}
			else
				tempFile = theFile.getFile();
			java.io.Writer writer = new java.io.BufferedWriter(new java.io.FileWriter(tempFile));
			ParseReader parseReader = new ParseReader(reader, writer);
			thePI.setProgress(0);
			thePI.setProgressScale(itemCount);
			new prisms.util.json.SAJParser().parse(parseReader, this);
			writer.close();
		}

		@Override
		public void startObject(ParseState state)
		{
			if(thePI.isCanceled())
				throw new prisms.util.CancelException();
			super.startObject(state);
			thePosition = state.getIndex() - 1;
		}

		@Override
		public void startProperty(ParseState state, String name)
		{
			if(thePI.isCanceled())
				throw new prisms.util.CancelException();
			super.startProperty(state, name);
			if(!name.equals("id"))
				theType = null;
		}

		@Override
		public void valueString(ParseState state, String value)
		{
			super.valueString(state, value);
			if("type".equals(state.top().getPropertyName()))
				theType = value;
		}

		@Override
		public void valueNumber(ParseState state, Number value)
		{
			if(thePI.isCanceled())
				throw new prisms.util.CancelException();
			super.valueNumber(state, value);
			if("id".equals(state.top().getPropertyName()) && theType != null)
			{
				ObjectID id = new ObjectID(theType, value.longValue());
				if(state.getDepth() == 3) // Array, object, property. Exported item.
				{
					thePI.setProgress(thePI.getTaskProgress() + 1);
					theExportedObjects.add(id);
					Integer centerID = new Integer(Record2Utils.getCenterID(id.id));
					if(!ArrayUtils.containsP(theCenterIDs, centerID))
						theCenterIDs = (int []) ArrayUtils.addP(theCenterIDs, centerID);
				}
				if(!theObjectPositions.containsKey(id))
					theObjectPositions.put(id, new Integer(thePosition));
			}
		}

		public Object getItem(String type, long id) throws PrismsRecordException
		{
			Object ret = theBag.get(type, id);
			if(ret != null)
				return ret;
			ObjectID key = new ObjectID(type, id);
			Integer pos = theObjectPositions.get(key);
			if(pos == null)
				throw new PrismsRecordException("No such object " + type + "/" + id
					+ " in full item list or local data set");
			/* Since this item is about to be sync'ed right now, we don't need to sync it when
			 * syncItems is called */
			theObjectPositions.remove(key);
			prisms.util.json.SAJParser.DefaultHandler handler = new prisms.util.json.SAJParser.DefaultHandler();
			try
			{
				java.io.Reader reader = theFile.access(pos.intValue());
				new prisms.util.json.SAJParser().parse(reader, handler);
			} catch(prisms.util.json.SAJParser.ParseException e)
			{
				throw new PrismsRecordException("Malformatted JSON in cached item list", e);
			} catch(IOException e)
			{
				throw new PrismsRecordException("Could not read cached item list", e);
			}
			JSONObject json = (JSONObject) handler.finalValue();
			if(json.containsKey("-syncstore-"))
				throw new PrismsRecordException("Illegal state--first item in full item set"
					+ " matching the given type/id is a reference");
			return read(json);
		}

		public void syncItems(int stages, int stage) throws IOException, PrismsRecordException
		{
			String rootProgress;
			if(stages == 0)
				rootProgress = "Importing remote items";
			else
				rootProgress = "Importing remote items (stage " + stage + " of " + stages + ")";
			thePI.setProgressText(rootProgress);

			thePI.setProgress(0);
			thePI.setProgressScale(theExportedObjects.size());
			int items = 0;
			/* For each item in the full list that was not sync'ed already from being needed by a
			 * change, sync the item with the data set. */
			prisms.util.json.SAJParser.DefaultHandler handler = new prisms.util.json.SAJParser.DefaultHandler();
			for(ObjectID id : theExportedObjects)
			{
				Integer pos = theObjectPositions.remove(id);
				if(pos != null)
				{
					java.io.Reader reader = theFile.access(pos.intValue());
					try
					{
						new prisms.util.json.SAJParser().parse(reader, handler);
					} catch(prisms.util.json.SAJParser.ParseException e)
					{
						throw new PrismsRecordException("Malformatted JSON in cached item list", e);
					}
					JSONObject json = (JSONObject) handler.finalValue();
					handler.reset();
					theNewItem[0] = false;
					Object item;
					theDepth++;
					try
					{
						item = PrismsSynchronizer2.this.parseID(json, this, theNewItem);
						theBag.add(id.type, id.id, item);
						parseEmptyContent();
						if(!json.containsKey("-localstore-"))
						{
							if(theNewItem[0] || relativePriority(theSyncRecord.getCenter()) >= 0)
								parseContent(item, id, json, theNewItem[0]);
						}
						else if(theNewItem[0])
							log.error("Item " + id + " not locally stored!");
						parseEmptyContent();
						thePI.setProgressText(rootProgress + "\nImported " + id.type + " "
							+ prisms.util.PrismsUtils.encodeUnicode("" + item));
					} catch(PrismsRecordException e)
					{
						log.error("Failed to import " + id, e);
					} finally
					{
						theDepth--;
					}
				}
				items++;
				thePI.setProgress(items);
			}
			stage++;
			thePI.setProgressScale(0);
			thePI.setProgress(0);
			thePI.setProgressText(rootProgress);
			if(relativePriority(theSyncRecord.getCenter()) >= 0)
			{
				/* For each item in the data set that has no representation in the sent item list,
				 * delete the item */
				if(stages == 0)
					rootProgress = "Removing remotely deleted items";
				else if(stages == 4)
					rootProgress = "Removing remotely deleted items (stage 4 of " + stages + ")";
				else
					rootProgress = "Removing remotely deleted items (stage 3 of " + stages + ")";
				java.util.ArrayList<Object> toDelete = new java.util.ArrayList<Object>();
				java.util.ArrayList<ObjectID> delIDs = new java.util.ArrayList<ObjectID>();
				prisms.records2.Synchronize2Impl.ItemIterator iter = getImpl().getAllItems(
					theCenterIDs, theSyncRecord.getCenter());
				while(true)
				{
					Object item;
					try
					{
						if(!iter.hasNext())
							break;
						item = iter.next();
					} catch(PrismsRecordException e)
					{
						log.error("Could not retrieve item from full set", e);
						continue;
					}
					ObjectID id = new ObjectID(getType(item), getID(item));
					if(!theExportedObjects.contains(id) && !theSyncInput.isNewItem(item, id.id))
					{
						toDelete.add(item);
						delIDs.add(id);
					}
				}
				thePI.setProgressScale(toDelete.size());
				theExportedObjects.clear();
				items = 0;
				for(int i = 0; i < toDelete.size(); i++)
				{
					Object item = toDelete.get(i);
					ObjectID id = delIDs.get(i);
					try
					{
						getImpl().delete(item, theSyncRecord);
						items++;
						thePI.setProgress(items);
						thePI.setProgressText(rootProgress + "\nDeleted " + id.type + " " + item);
					} catch(Throwable e)
					{
						log.error("Could not delete item " + id.type + "/" + id.id, e);
					}
				}
				toDelete.clear();
				delIDs.clear();
				thePI.setProgressText("Finished importing remote values");
				thePI.setProgressScale(0);
				thePI.setProgress(0);
			}
		}

		void close()
		{
			if(theFile == null)
				return;
			try
			{
				theFile.close(true);
			} catch(IOException e)
			{
				log.error("Could not close cached sync file", e);
			}
			theFile = null;
		}
	}

	private class ChangeReader extends prisms.util.json.SAJParser.DefaultHandler
	{
		private final Reader theReader;

		private final SyncRecord theSyncRecord;

		private PS2ItemReader theGetter;

		private prisms.ui.UI.DefaultProgressInformer thePI;

		private int theTotalChangeCount;

		private int theChangeCount;

		private boolean storeSyncRecord;

		private java.util.HashSet<ObjectID> theItemsNeedParsing;

		ChangeReader(Reader reader, SyncRecord record, PS2ItemReader getter, int totalChangeCount,
			prisms.ui.UI.DefaultProgressInformer pi, boolean storeSR)
		{
			theReader = reader;
			theSyncRecord = record;
			theGetter = getter;
			theTotalChangeCount = totalChangeCount;
			thePI = pi;
			thePI.setProgressScale(totalChangeCount);
			storeSyncRecord = storeSR;
			theItemsNeedParsing = new java.util.HashSet<ObjectID>();
		}

		int parse() throws java.io.IOException, prisms.util.json.SAJParser.ParseException
		{
			new prisms.util.json.SAJParser().parse(theReader, this);
			return theChangeCount;
		}

		public void endObject(ParseState state)
		{
			super.endObject(state);
			if(thePI.isCanceled())
				throw new prisms.util.CancelException();
			JSONObject json = (JSONObject) finalValue();
			if(getDepth() == 1)
			{ // Change-level
				theChangeCount++;
				if(thePI != null && theTotalChangeCount > 0)
					thePI.setProgress(theChangeCount);
				if(Boolean.TRUE.equals(json.get("skipped")))
					return;
				boolean store = true;
				ChangeRecord change = parseChange(json, theGetter);
				try
				{
					if(getKeeper().hasChange(change.id))
					{
						store = false;
						if(getKeeper().hasSuccessfulChange(change.id))
							return;
					}
				} catch(PrismsRecordException e)
				{
					log.error("Could not check existence of change", e);
					return;
				}
				if(thePI.isCanceled())
					throw new prisms.util.CancelException();
				boolean error = change instanceof ChangeRecordError;
				try
				{
					if(!error && getKeeper().getSuccessors(change).length == 0)
					{
						Object currentValue = json.get("currentValue");
						if(currentValue != null && currentValue instanceof JSONObject)
							currentValue = theGetter.read((JSONObject) currentValue);
						thePI.setProgressText(prisms.util.PrismsUtils.encodeUnicode("Importing "
							+ change.toString(currentValue)));
						getImpl().doChange(change, currentValue, theGetter);
					}
				} catch(Exception e)
				{
					log.error("Could not perform change " + change.id, e);
					error = true;
				}
				try
				{
					if(store)
						getKeeper().persist(change);
					if(storeSyncRecord)
						getKeeper().associate(change, theSyncRecord, error);
				} catch(PrismsRecordException e2)
				{
					log.error("Could not persist change " + change.id, e2);
				}
			}
			else if(json.containsKey("type") && json.containsKey("id")
				&& !Boolean.TRUE.equals(json.get("-syncstore-")))
			{
				try
				{
					for(int d = getDepth() - 1; d >= 0; d--)
					{
						Object val = fromTop(d);
						if(!(val instanceof JSONObject))
							continue;
						JSONObject jsonVal = (JSONObject) val;
						if(jsonVal.containsKey("type") && jsonVal.containsKey("id")
							&& !Boolean.TRUE.equals(jsonVal.get("-syncstore-")))
						{
							if(theGetter.parseID(jsonVal))
								theItemsNeedParsing.add(new ObjectID((String) jsonVal.get("type"),
									((Number) jsonVal.get("id")).longValue()));
						}
					}
					if(theItemsNeedParsing.remove(new ObjectID((String) json.get("type"),
						((Number) json.get("id")).longValue())) || theGetter.parseID(json))
						theGetter.parseContent(json);
				} catch(Exception e)
				{
					log.error("Could not read item", e);
				}
			}
		}
	}

	private class SyncInputHandler extends prisms.util.json.SAJParser.DefaultHandler
	{
		private final Reader theReader;

		final SyncRecord theSyncRecord;

		final prisms.ui.UI.DefaultProgressInformer thePI;

		private PS2ItemReader theItemReader;

		private long [] theNewChanges;

		private long [] theCreations;

		private Object [] theCurrentValues;

		private boolean isCenterIDSet;

		private int theTotalItemCount;

		private int theTotalChangeCount;

		private boolean storeSyncRecord;

		private boolean hasDoneChanges;

		private int theStageCount;

		private int [] theCurrentStage;

		private java.util.ArrayList<LatestCenterChange> theLatestChanges;

		SyncInputHandler(Reader reader, SyncRecord record, prisms.ui.UI.DefaultProgressInformer pi,
			boolean storeSR)
		{
			theReader = reader;
			theSyncRecord = record;
			thePI = pi;
			theItemReader = new PS2ItemReader(this, record, thePI);
			theTotalChangeCount = -1;
			storeSyncRecord = storeSR;
			theLatestChanges = new java.util.ArrayList<LatestCenterChange>();
		}

		@Override
		public void separator(ParseState state)
		{
			super.separator(state);
			if(thePI.isCanceled())
				throw new prisms.util.CancelException();
			try
			{
				if(ALL_ITEMS.equals(state.top().getPropertyName()))
				{
					checkStage();
					if(theStageCount == 0)
						thePI.setProgressText("Retrieving and storing remote values");
					else
						thePI.setProgressText("Retrieving and storing remote values (Stage "
							+ theCurrentStage[0] + " of " + theStageCount + ")");
					if(!isCenterIDSet)
						throw new prisms.util.json.SAJParser.ParseException(
							"Can't import items before remote center ID is sent", state);
					super.valueNull(state);
					state.spoofValue();
					theItemReader.parse(theReader, theTotalItemCount);
					theCurrentStage[0]++;
					if(thePI.isCanceled())
						throw new prisms.util.CancelException();
					thePI.setProgressScale(0);
				}
				else if(CHANGES.equals(state.top().getPropertyName()))
				{
					if(hasDoneChanges)
						return;
					hasDoneChanges = true;
					checkStage();
					if(theStageCount == 0)
						thePI.setProgressText("Importing changes");
					else
						thePI.setProgressText("Importing changes (Stage " + theCurrentStage[0]
							+ " of " + theStageCount + ")");
					if(!isCenterIDSet)
						throw new prisms.util.json.SAJParser.ParseException(
							"Can't import changes before center ID is sent", state);
					super.valueNull(state);
					state.spoofValue();
					// First, read in the changes from the remote center
					try
					{
						new ChangeReader(theReader, theSyncRecord, theItemReader,
							theTotalChangeCount, thePI, storeSyncRecord).parse();
					} catch(prisms.util.CancelException e)
					{}
					if(theTotalChangeCount > 0)
						theCurrentStage[0]++;
					thePI.setCancelable(false);
					// Next, synchronize this center's item set with the remote center's items
					try
					{
						theItemReader.syncItems(theStageCount, theCurrentStage[0]);
					} catch(PrismsRecordException e)
					{
						throw new RuntimeWrapper("Could not sync item set", e);
					}
					// Next, since that was successful, store the latest change times from the
					// remote center
					for(LatestCenterChange lcc : theLatestChanges)
					{
						try
						{
							getKeeper().setLatestChange(lcc.getCenterID(), lcc.getSubjectCenter(),
								lcc.getLatestChange());
						} catch(PrismsRecordException e)
						{
							log.error("Could not set latest change time", e);
						}
					}
					theLatestChanges.clear();
					if(theNewChanges != null && theNewChanges.length > 0)
					{
						/* After a destructive sync, we need to re-apply changes that have occurred
						 * since the last time the remote center synchronized with the local center. */
						String baseText;
						if(theStageCount == 0)
							baseText = "Recovering local changes";
						else
							baseText = "Recovering local changes (stage " + theStageCount + " of "
								+ theStageCount + ")";
						thePI.setProgressText(baseText);
						thePI.setProgress(0);
						thePI.setProgressScale(0);
						theCreations = null;
						long [] batch = new long [theNewChanges.length < 100 ? theNewChanges.length
							: 100];
						for(int i = 0; i < theNewChanges.length; i += batch.length)
						{
							if(theNewChanges.length - i < batch.length)
								batch = new long [theNewChanges.length - i];
							System.arraycopy(theNewChanges, i, batch, 0, batch.length);
							ChangeRecord [] records;
							try
							{
								records = getKeeper().getChanges(batch);
							} catch(PrismsRecordException e)
							{
								log.error("Could not get changes " + i + " through "
									+ (i + batch.length - 1), e);
								continue;
							}
							thePI.setProgressScale(theNewChanges.length);
							for(int j = 0; j < records.length; j++)
							{
								thePI.setProgress(i + j);
								if(records[j] instanceof ChangeRecordError)
								{
									log.error("Could not redo change " + records[j].id
										+ " after destructive sync");
									continue;
								}
								try
								{
									if(shouldSend(records[j])
										&& getKeeper().getSuccessors(records[j]).length == 0)
										getImpl().doChange(records[j], theCurrentValues[i + j],
											theItemReader);
								} catch(PrismsRecordException e)
								{
									log.error("Could not perform change " + records[j].id, e);
								}
								records[j] = null;
								theCurrentValues[i + j] = null;
							}
						}
						theNewChanges = null;
						theCurrentValues = null;
					}
					thePI.setProgressScale(0);
					thePI.setProgress(0);
					thePI.setProgressText("Finalizing import and refreshing client");
					theItemReader.close();
				}
				else if("latestChanges".equals(state.top().getPropertyName()))
				{
					if(!isCenterIDSet)
						throw new prisms.util.json.SAJParser.ParseException(
							"Can't import changes before center ID is sent", state);
					super.valueNull(state);
					state.spoofValue();
					thePI.setCancelable(false);
					prisms.util.json.SAJParser.DefaultHandler handler = new prisms.util.json.SAJParser.DefaultHandler();
					new prisms.util.json.SAJParser().parse(theReader, handler);
					org.json.simple.JSONArray changes = (org.json.simple.JSONArray) handler
						.finalValue();
					for(JSONObject change : (JSONObject []) changes.toArray(new JSONObject [changes
						.size()]))
					{
						int centerID = ((Number) change.get("centerID")).intValue();
						int subjectCenter = ((Number) change.get("subjectCenter")).intValue();
						long remoteTime = ((Number) change.get("latestChange")).longValue();
						theLatestChanges.add(new LatestCenterChange(centerID, subjectCenter,
							remoteTime));
					}
					/* If all items are sent from the remote center, then the synchronization
					 * is a destructive sync. We need to get all changes that we have since the
					 * last time the remote center synchronized with the local center and re-apply
					 * them after importing the remote center's data as-is. If, however, the remote
					 * center is not a priority, then the sync will not be evaluated destructively,
					 * so no post-destruct changes are needed. */
					if(relativePriority(theSyncRecord.getCenter()) < 0)
						return;
					try
					{
						theStageCount++;
						if(thePI.isCanceled())
							throw new prisms.util.CancelException();
						SyncOutput sync = getSyncOutput(theSyncRecord.getCenter(),
							theLatestChanges.toArray(new LatestCenterChange [theLatestChanges
								.size()]), true, true);
						theNewChanges = sync.theChangeIDs;
						theCreations = new long [theNewChanges.length];
						java.util.Arrays.fill(theCreations, -1);
						theCurrentValues = new Object [theNewChanges.length];
						long [] batch = new long [theNewChanges.length < 100 ? theNewChanges.length
							: 100];
						for(int i = 0; i < theNewChanges.length; i += batch.length)
						{
							int len = theNewChanges.length - i;
							if(len < batch.length)
								batch = new long [len];
							else
								len = batch.length;
							System.arraycopy(theNewChanges, i, batch, 0, len);
							ChangeRecord [] records = getKeeper().getChanges(batch);
							for(int j = 0; j < records.length; j++)
							{
								if(records[j] instanceof ChangeRecordError)
									continue;
								if(records[j].type.additivity == 0)
									theCurrentValues[i + j] = getCurrentValue(records[j]);
								if(records[j].type.changeType == null
									&& records[j].type.additivity > 0)
									theCreations[i] = getID(records[j].majorSubject);
							}
						}
						if(theNewChanges.length > 0)
							theStageCount++;
					} catch(PrismsRecordException e)
					{
						throw new RuntimeWrapper("Could not retrieve changes"
							+ " since last sync by remote center", e);
					}
				}
			} catch(IOException e)
			{
				throw new RuntimeWrapper("Could not read synchronization stream", e);
			} catch(prisms.util.json.SAJParser.ParseException e)
			{
				throw new RuntimeWrapper("Could not parse synchronization stream", e);
			}
		}

		private void checkStage()
		{
			if(theCurrentStage != null)
				return;
			int count = 0;
			if(theTotalItemCount > 0)
			{
				count = 2;
				if(theTotalChangeCount > 0)
					count++;
				if(relativePriority(theSyncRecord.getCenter()) >= 0)
					count++;
			}
			theStageCount = count;
			theCurrentStage = new int [] {1};
		}

		@Override
		public void valueNumber(ParseState state, Number value)
		{
			super.valueNumber(state, value);
			if(PARALLEL_ID.equals(state.top().getPropertyName()))
				theSyncRecord.setParallelID(value.intValue());
			else if(CENTER_ID.equals(state.top().getPropertyName()))
			{
				isCenterIDSet = true;
				if(theSyncRecord.getCenter().getCenterID() < 0)
				{
					if(getKeeper().getCenterID() == value.intValue())
						throw new RuntimeWrapper("A center cannot synchronize with itself", null);
					theSyncRecord.getCenter().setCenterID(value.intValue());
					try
					{
						getKeeper().putCenter(theSyncRecord.getCenter(), null, null);
					} catch(PrismsRecordException e)
					{
						throw new RuntimeWrapper("Could not set center ID", e);
					}
				}
				else if(theSyncRecord.getCenter().getCenterID() != value.intValue())
					throw new RuntimeWrapper("Synchronization data not sent from center "
						+ theSyncRecord.getCenter(), new PrismsRecordException(
						"Synchronization data not sent from center " + theSyncRecord.getCenter()));
			}
			else if("syncPriority".equals(state.top().getPropertyName()))
			{
				if(theSyncRecord.getCenter().getPriority() != value.intValue())
				{
					theSyncRecord.getCenter().setPriority(value.intValue());
					try
					{
						getKeeper().putCenter(theSyncRecord.getCenter(), null, null);
					} catch(PrismsRecordException e)
					{
						throw new RuntimeWrapper("Could not set center sync priority", e);
					}
				}
			}
			else if("itemCount".equals(state.top().getPropertyName()))
				theTotalItemCount = value.intValue();
			else if("changeCount".equals(state.top().getPropertyName()))
				theTotalChangeCount = value.intValue();
		}

		boolean isNewItem(Object item, long id)
		{
			for(int c = 0; c < theCreations.length; c++)
				if(theCreations[c] == id)
					return true;
			return false;
		}
	}

	private class SyncReceiptHandler extends prisms.util.json.SAJParser.DefaultHandler
	{
		private PrismsCenter theCenter;

		private SyncRecord theRecord;

		private prisms.util.LongList theErrorChanges;

		SyncReceiptHandler()
		{
			theErrorChanges = new prisms.util.LongList();
		}

		@Override
		public void endArray(ParseState state)
		{
			super.endArray(state);
			if("errors".equals(state.top().getPropertyName()))
			{
				long [] ids = theErrorChanges.toArray();
				theErrorChanges.clear();
				long [] batch = new long [ids.length < 100 ? ids.length : 100];
				int i = 0;
				while(i < ids.length)
				{
					int length = ids.length - i;
					if(length > batch.length)
						length = batch.length;
					System.arraycopy(ids, i, batch, 0, length);
					ChangeRecord [] records;
					try
					{
						records = getKeeper().getChanges(batch);
					} catch(PrismsRecordException e)
					{
						throw new RuntimeWrapper(
							"Could not retrieve change records for synchronization", e);
					}
					for(ChangeRecord record : records)
					{
						try
						{
							getKeeper().associate(record, theRecord, true);
						} catch(PrismsRecordException e)
						{
							log.error("Could not associate change " + record.id
								+ " with sync record for export", e);
						}
					}
					i += length;
				}
			}
		}

		@Override
		public void valueNumber(ParseState state, Number value)
		{
			super.valueNumber(state, value);
			if(CENTER_ID.equals(state.top().getPropertyName()))
			{
				PrismsCenter [] centers;
				try
				{
					centers = getKeeper().getCenters();
				} catch(PrismsRecordException e)
				{
					throw new RuntimeWrapper("Could not retrieve centers", e);
				}
				for(PrismsCenter center : centers)
					if(center.getCenterID() == value.intValue())
					{
						theCenter = center;
						break;
					}
				if(theCenter == null)
					throw new RuntimeWrapper("No such center with ID " + value, null);
			}
			else if(theCenter == null)
				throw new RuntimeWrapper(CENTER_ID
					+ " must be the first element in a synchronization receipt", null);
			else if("syncRecord".equals(state.top().getPropertyName()))
			{
				SyncRecord [] records;
				try
				{
					records = getKeeper().getSyncRecords(theCenter, Boolean.FALSE);
				} catch(PrismsRecordException e)
				{
					throw new RuntimeWrapper("Could not retrieve sync records", e);
				}
				if(records.length == 0)
					throw new RuntimeWrapper("No sync records for center " + theCenter, null);
				for(SyncRecord record : records)
					if(record.getID() == value.intValue())
					{
						theRecord = record;
						break;
					}
				if(theRecord == null)
					throw new RuntimeWrapper("No such sync record with ID " + value
						+ " for center " + theCenter, null);
			}
			else if(theRecord == null)
				throw new RuntimeWrapper(
					"syncRecord must be the second element in a synchronization recept", null);
			else if(PARALLEL_ID.equals(state.top().getPropertyName()))
			{
				theRecord.setParallelID(value.intValue());
				try
				{
					getKeeper().putSyncRecord(theRecord);
				} catch(PrismsRecordException e)
				{
					throw new RuntimeWrapper("Could not store synchronization error message", e);
				}
				if(theSyncListener != null)
					theSyncListener.syncChanged(theRecord);
			}
			else if(state.top().token == prisms.util.json.SAJParser.ParseToken.ARRAY
				&& "errors".equals(state.fromTop(1).getPropertyName()))
				theErrorChanges.add(value.longValue());
		}

		@Override
		public void valueNull(ParseState state)
		{
			super.valueNull(state);
			if("syncError".equals(state.top().getPropertyName()))
			{
				theRecord.setSyncError(null);
				try
				{
					getKeeper().putSyncRecord(theRecord);
				} catch(PrismsRecordException e)
				{
					throw new RuntimeWrapper("Could not store synchronization error message", e);
				}
				theRecord.getCenter().setLastExport(theRecord.getSyncTime());
				try
				{
					getKeeper().putCenter(theCenter, null, null);
				} catch(PrismsRecordException e)
				{
					throw new RuntimeWrapper("Could not alter center's sync times", e);
				}
				if(theSyncListener != null)
					theSyncListener.syncChanged(theRecord);
			}
		}

		@Override
		public void valueString(ParseState state, String value)
		{
			super.valueString(state, value);
			if("syncError".equals(state.top().getPropertyName()))
			{
				theRecord.setSyncError(value);
				try
				{
					getKeeper().putSyncRecord(theRecord);
				} catch(PrismsRecordException e)
				{
					throw new RuntimeWrapper("Could not store synchronization error message", e);
				}
				if(theSyncListener != null)
					theSyncListener.syncChanged(theRecord);
			}
		}
	}

	private RecordKeeper2 theKeeper;

	private Synchronize2Impl theImpl;

	SyncListener theSyncListener;

	/**
	 * Creates a synchronizer
	 * 
	 * @param keeper The record keeper to keep track of changes
	 * @param impl The implementation to perform the synchronization of the data
	 */
	public PrismsSynchronizer2(RecordKeeper2 keeper, Synchronize2Impl impl)
	{
		theKeeper = keeper;
		theImpl = impl;
	}

	/**
	 * @param listener The listener to listen for new sync records
	 */
	public void setSyncListener(SyncListener listener)
	{
		theSyncListener = listener;
	}

	/**
	 * Reads a synchronization stream, performing the operations required to synchronize the local
	 * center's data with the remote center
	 * 
	 * @param center The center to synchronize this center's data with
	 * @param type The type of synchronization
	 * @param reader The synchronization stream
	 * @param pi The progress informer to alert the user to the status of synchronization. May be
	 *        null.
	 * @param storeSyncRecord Whether to store the sync record and associated changes
	 * @return The synchronization record of the synchronization that occurred
	 * @throws IOException If the stream cannot be completely read or parsed
	 * @throws PrismsRecordException If an error occurs importing the synchronization data
	 */
	public SyncRecord doSyncInput(PrismsCenter center, SyncRecord.Type type, Reader reader,
		prisms.ui.UI.DefaultProgressInformer pi, boolean storeSyncRecord) throws IOException,
		PrismsRecordException
	{
		if(pi == null)
			pi = new prisms.ui.UI.DefaultProgressInformer();
		pi.setProgressText("Reading synchronization data");
		SyncRecord syncRecord = new SyncRecord(center, type, System.currentTimeMillis(), true);
		syncRecord.setSyncError("?");
		if(storeSyncRecord)
			try
			{
				theKeeper.putSyncRecord(syncRecord);
			} catch(PrismsRecordException e)
			{
				log.error("Could not persist synchronization record", e);
				throw new IOException("Could not persist synchronization record: " + e.getMessage());
			}
		if(theSyncListener != null && storeSyncRecord)
			theSyncListener.syncAttempted(syncRecord);
		prisms.util.json.SAJParser parser = new prisms.util.json.SAJParser();
		try
		{
			parser.parse(reader, new SyncInputHandler(reader, syncRecord, pi, storeSyncRecord));
			syncRecord.setSyncError(null);
			if(storeSyncRecord)
				center.setLastImport(syncRecord.getSyncTime());
			if(storeSyncRecord)
				verifySyncSuccess(syncRecord);
		} catch(prisms.util.json.SAJParser.ParseException e)
		{
			syncRecord.setSyncError("Could not parse synchronization stream");
			throw new PrismsRecordException("Could not parse synchronization stream", e);
		} catch(RuntimeWrapper e)
		{
			syncRecord.setSyncError("Could not parse synchronization stream");
			if(e.getCause() instanceof IOException)
				throw (IOException) e.getCause();
			else if(e.getCause() instanceof PrismsRecordException)
				throw (PrismsRecordException) e.getCause();
			else
				throw new PrismsRecordException("Could not parse synchronization stream",
					e.getCause());
		} catch(prisms.util.CancelException e)
		{
			syncRecord.setSyncError("Import user canceled operation");
		} catch(Throwable e)
		{
			syncRecord.setSyncError("Could not parse synchronization stream");
			throw new PrismsRecordException("Could not parse synchronization stream", e);
		} finally
		{
			if(storeSyncRecord)
			{
				try
				{
					theKeeper.putSyncRecord(syncRecord);
				} catch(PrismsRecordException e)
				{
					log.error("Could not store synchronization record", e);
				}
				try
				{
					theKeeper.putCenter(center, null, null);
				} catch(PrismsRecordException e)
				{
					log.error("Could not store alter center's sync time", e);
				}
				if(theSyncListener != null)
					theSyncListener.syncChanged(syncRecord);
			}
		}
		pi.setProgressText("Synchronization successful");
		return syncRecord;
	}

	private class SyncOutput
	{
		final prisms.util.IntList theLateIDs;

		final LatestCenterChange [] theLocalChanges;

		final long [] theChangeIDs;

		final prisms.util.LongList theErrorChanges;

		SyncOutput(prisms.util.IntList lateIDs, LatestCenterChange [] localChanges,
			long [] changeIDs, prisms.util.LongList errorChanges)
		{
			theLateIDs = lateIDs;
			theLocalChanges = localChanges;
			theChangeIDs = changeIDs;
			theErrorChanges = errorChanges;
		}
	}

	/**
	 * Creates synchronization output to be sent to a remote center
	 * 
	 * @param center The center to generate the synchronization data for
	 * @param changes The times of the latest changes by and for each center that the remote center
	 *        has imported
	 * @param withRecords Whether the remote center is interested in the integrity of their
	 *        record-keeping even to the detriment of synchronization performance
	 * @param exclusiveForSubjects Whether to exclude data sets that the remote center is not aware
	 *        of. This is only true when the method is used internally.
	 * @return The synchronization output to send to the remote center
	 * @throws PrismsRecordException If an error occurs generating the data
	 */
	SyncOutput getSyncOutput(final PrismsCenter center, LatestCenterChange [] changes,
		boolean withRecords, boolean exclusiveForSubjects) throws PrismsRecordException
	{
		final int [] centerIDs = theKeeper.getAllCenterIDs();
		LatestCenterChange [] localChanges;
		{
			java.util.ArrayList<LatestCenterChange> ret = new java.util.ArrayList<LatestCenterChange>();
			for(int centerID : centerIDs)
				for(int subjectCenter : centerIDs)
				{
					if(exclusiveForSubjects)
					{
						boolean hit = false;
						for(LatestCenterChange lcc : changes)
							if(lcc.getSubjectCenter() == subjectCenter)
							{
								hit = true;
								break;
							}
						if(!hit)
							continue;
					}
					long lastChange = theKeeper.getLatestChange(centerID, subjectCenter);
					if(lastChange > 0)
						ret.add(new LatestCenterChange(centerID, subjectCenter, lastChange));
				}
			localChanges = ret.toArray(new LatestCenterChange [ret.size()]);
		}

		final prisms.util.IntList lateIDs = new prisms.util.IntList();
		final prisms.util.LongList errorChanges = new prisms.util.LongList();
		final java.util.ArrayList<LatestCenterChange> updateChanges = new java.util.ArrayList<LatestCenterChange>();
		ArrayUtils
			.adjust(
				localChanges,
				changes,
				new ArrayUtils.DifferenceListenerE<LatestCenterChange, LatestCenterChange, PrismsRecordException>()
				{
					public boolean identity(LatestCenterChange o1, LatestCenterChange o2)
					{
						return o1.getCenterID() == o2.getCenterID()
							&& o1.getSubjectCenter() == o2.getSubjectCenter();
					}

					public LatestCenterChange added(LatestCenterChange o, int mIdx, int retIdx)
					{
						return null;
					}

					public LatestCenterChange removed(LatestCenterChange o, int oIdx, int incMod,
						int retIdx) throws PrismsRecordException
					{
						updateChanges.add(new LatestCenterChange(o.getCenterID(), o
							.getSubjectCenter(), -1));
						if(getKeeper().getLatestPurgedChange(o.getCenterID(), o.getSubjectCenter()) > 0)
						{
							if(!lateIDs.contains(o.getSubjectCenter()))
								lateIDs.add(o.getSubjectCenter());
						}
						return o;
					}

					public LatestCenterChange set(LatestCenterChange o1, int idx1, int incMod,
						LatestCenterChange o2, int idx2, int retIdx) throws PrismsRecordException
					{
						if(getKeeper().getLatestPurgedChange(o1.getCenterID(),
							o1.getSubjectCenter()) > o2.getLatestChange())
						{
							if(!lateIDs.contains(o1.getSubjectCenter()))
								lateIDs.add(o1.getSubjectCenter());
						}
						errorChanges.addAll(Record2Utils.getSyncErrorChanges(getKeeper(), center,
							o1.getCenterID(), o1.getSubjectCenter()));
						if(o1.getLatestChange() > o2.getLatestChange())
							updateChanges.add(o2);
						return o1;
					}
				});

		prisms.util.LongList changeIDs = new prisms.util.LongList();
		for(LatestCenterChange updateChange : updateChanges)
		{
			if(lateIDs.contains(updateChange.getSubjectCenter()) && !withRecords)
				continue;
			long [] ids;
			try
			{
				ids = Record2Utils.getSyncChanges(theKeeper, center, updateChange.getCenterID(),
					updateChange.getSubjectCenter(), updateChange.getLatestChange() + 1);
				changeIDs.addAll(ids);
			} catch(PrismsRecordException e)
			{
				throw new PrismsRecordException("Could not get change records for synchronization",
					e);
			}
		}
		if(changeIDs.size() <= 100)
		{ // Possibly some or all of these don't require being sent (e.g. changes to centers)
			try
			{
				ChangeRecord [] records = theKeeper.getChanges(changeIDs.toArray());
				for(ChangeRecord record : records)
					if(record != null && !shouldSend(record))
						changeIDs.remove(record.id);
			} catch(PrismsRecordException e)
			{
				throw new PrismsRecordException("Could not trim modifications", e);
			}
		}
		changeIDs.addAll(errorChanges);
		long [] ids = changeIDs.toArray();
		changeIDs.clear();
		changeIDs = null;
		ids = theKeeper.sortChangeIDs(ids, true);
		return new SyncOutput(lateIDs, localChanges, ids, errorChanges);
	}

	/**
	 * Writes to a synchronization stream, performing the operations required to synchronize the
	 * remote center's data with the local center
	 * 
	 * @param center The center synchronizing with this center
	 * @param changes The latest change times for each center that has changes on the remote center
	 * @param type The type of synchronization
	 * @param writer The synchronization stream
	 * @param pi The progress to use to inform the user of the status of the operation
	 * @param withRecords Whether the remote center makes use of record-keeping. If false,
	 *        synchronization can be optimized further in some cases.
	 * @param storeSyncRecord Whether to store the sync record and associated changes
	 * @return The record of the synchronization export
	 * @throws IOException If the stream cannot be completely written
	 * @throws PrismsRecordException If an error occurs gathering the data to write to the stream
	 */
	public SyncRecord doSyncOutput(PrismsCenter center, LatestCenterChange [] changes,
		SyncRecord.Type type, Writer writer, prisms.ui.UI.DefaultProgressInformer pi,
		boolean withRecords, boolean storeSyncRecord) throws IOException, PrismsRecordException
	{
		if(pi == null)
			pi = new prisms.ui.UI.DefaultProgressInformer();
		pi.setProgressText("Starting synchronization data generation");
		final SyncRecord syncRecord = new SyncRecord(center, type, System.currentTimeMillis(),
			false);
		syncRecord.setSyncError("?");
		if(storeSyncRecord)
			try
			{
				theKeeper.putSyncRecord(syncRecord);
			} catch(PrismsRecordException e)
			{
				throw new PrismsRecordException("Could not store synchronization record", e);
			}
		if(theSyncListener != null && storeSyncRecord)
			theSyncListener.syncAttempted(syncRecord);
		SyncOutput sync;
		try
		{
			sync = getSyncOutput(center, changes, withRecords, false);
		} catch(PrismsRecordException e)
		{
			log.error("Sync output failed", e);
			syncRecord.setSyncError(e.getMessage());
			try
			{
				if(storeSyncRecord)
					theKeeper.putSyncRecord(syncRecord);
			} catch(PrismsRecordException e2)
			{
				throw new PrismsRecordException("Could not store synchronization record", e2);
			}
			throw e;
		} catch(Throwable e)
		{
			log.error("Sync output failed", e);
			syncRecord.setSyncError("Exception: " + e.getClass().getName() + ": " + e.getMessage());
			try
			{
				if(storeSyncRecord)
					theKeeper.putSyncRecord(syncRecord);
			} catch(PrismsRecordException e2)
			{
				throw new PrismsRecordException("Could not store synchronization record", e2);
			}
			throw new PrismsRecordException("Sync output failed", e);
		}
		if(pi.isCanceled())
		{
			syncRecord.setSyncError("Export user canceled operation");
			if(storeSyncRecord)
				try
				{
					theKeeper.putSyncRecord(syncRecord);
				} catch(PrismsRecordException e)
				{
					throw new PrismsRecordException("Could not store synchronization record", e);
				}
			return syncRecord;
		}
		final prisms.util.json.JsonStreamWriter jsw = new prisms.util.json.JsonStreamWriter(writer);
		jsw.startObject();
		jsw.startProperty(CENTER_ID);
		jsw.writeNumber(new Integer(theKeeper.getCenterID()));
		jsw.startProperty(PARALLEL_ID);
		jsw.writeNumber(new Integer(syncRecord.getID()));
		jsw.startProperty("sync-data");
		jsw.startArray();

		int stages = 0;
		if(!sync.theLateIDs.isEmpty())
			stages++;
		if(sync.theChangeIDs.length > 0)
			stages++;

		int stage = 1;
		try
		{
			PS2ItemWriter itemWriter = new PS2ItemWriter(writer, syncRecord, changes,
				storeSyncRecord);
			jsw.startObject();
			if(!sync.theLateIDs.isEmpty())
			{
				pi.setProgressText("Writing local values to stream");
				int itemCount = 0;
				prisms.records2.Synchronize2Impl.ItemIterator allItems;
				try
				{
					allItems = theImpl.getAllItems(sync.theLateIDs.toArray(), center);
				} catch(PrismsRecordException e)
				{
					throw new prisms.records2.PrismsRecordException(
						"Could not retrieve all items for synchronization", e);
				}
				while(true)
				{
					if(pi.isCanceled())
					{
						syncRecord.setSyncError("Export user canceled operation");
						if(storeSyncRecord)
							try
							{
								theKeeper.putSyncRecord(syncRecord);
							} catch(PrismsRecordException e)
							{
								throw new PrismsRecordException(
									"Could not store synchronization record", e);
							}
						return syncRecord;
					}
					try
					{
						if(!allItems.hasNext())
							break;
						allItems.next();
						itemCount++;
					} catch(PrismsRecordException e)
					{
						throw new prisms.records2.PrismsRecordException(
							"Could not retrieve item for synchronization", e);
					}
				}
				allItems = null;
				jsw.startProperty("itemCount");
				jsw.writeNumber(new Integer(itemCount));
				String baseText = "Writing local values to stream";
				if(stages > 1)
					baseText += " (stage " + stage + " of " + stages + ")";
				pi.setProgressText(baseText);
				pi.setProgress(0);
				pi.setProgressScale(itemCount);
				jsw.startProperty("changeCount");
				jsw.writeNumber(new Integer(sync.theChangeIDs.length));
				jsw.startProperty(ALL_ITEMS);
				jsw.writeCustomValue();
				itemWriter.start();
				try
				{
					allItems = theImpl.getAllItems(sync.theLateIDs.toArray(), center);
				} catch(PrismsRecordException e)
				{
					throw new prisms.records2.PrismsRecordException(
						"Could not retrieve all items for synchronization", e);
				}
				while(true)
				{
					if(pi.isCanceled())
					{
						syncRecord.setSyncError("Export user canceled operation");
						if(storeSyncRecord)
							try
							{
								theKeeper.putSyncRecord(syncRecord);
							} catch(PrismsRecordException e)
							{
								throw new PrismsRecordException(
									"Could not store synchronization record", e);
							}
						return syncRecord;
					}
					Object next;
					try
					{
						if(!allItems.hasNext())
							break;
						next = allItems.next();
					} catch(PrismsRecordException e)
					{
						throw new prisms.records2.PrismsRecordException(
							"Could not retrieve item for synchronization", e);
					}
					pi.setProgressText(baseText + "\nWriting " + getType(next) + " "
						+ prisms.util.PrismsUtils.encodeUnicode("" + next));
					itemWriter.writeItem(next);
				}
				allItems = null;
				itemWriter.end();
				pi.setProgressText("Finished writing local values");
				stage++;

				jsw.startProperty("latestChanges");
				jsw.startArray();
				for(LatestCenterChange change : sync.theLocalChanges)
				{
					jsw.startObject();
					jsw.startProperty("centerID");
					jsw.writeNumber(new Integer(change.getCenterID()));
					jsw.startProperty("subjectCenter");
					jsw.writeNumber(new Integer(change.getSubjectCenter()));
					jsw.startProperty("latestChange");
					jsw.writeNumber(new Long(change.getLatestChange()));
					jsw.endObject();
				}
				jsw.endArray();
			}

			if(pi.isCanceled())
			{
				syncRecord.setSyncError("Export user canceled operation");
				if(storeSyncRecord)
					try
					{
						theKeeper.putSyncRecord(syncRecord);
					} catch(PrismsRecordException e)
					{
						throw new PrismsRecordException("Could not store synchronization record", e);
					}
				return syncRecord;
			}
			String baseText = "Writing changes to stream";
			if(stages > 1)
				baseText += " (stage " + stage + " of " + stages + ")";
			long [] ids = sync.theChangeIDs;
			pi.setProgressScale(ids.length);
			theKeeper.sortChangeIDs(ids, true);
			jsw.startProperty("changeCount");
			jsw.writeNumber(new Integer(ids.length));
			jsw.startProperty(CHANGES);
			jsw.writeCustomValue();
			itemWriter.start();
			long [] batch = new long [ids.length < 100 ? ids.length : 100];
			int i = 0, j = 0;
			while(j < ids.length)
			{
				if(pi.isCanceled())
				{
					syncRecord.setSyncError("Export user canceled operation");
					if(storeSyncRecord)
						try
						{
							theKeeper.putSyncRecord(syncRecord);
						} catch(PrismsRecordException e)
						{
							throw new PrismsRecordException(
								"Could not store synchronization record", e);
						}
					return syncRecord;
				}
				int length = ids.length - j;
				if(length > batch.length)
					length = batch.length;
				System.arraycopy(ids, j, batch, 0, length);
				ChangeRecord [] records;
				try
				{
					records = theKeeper.getChanges(batch);
				} catch(PrismsRecordException e)
				{
					throw new prisms.records2.PrismsRecordException(
						"Could not retrieve change records for synchronization", e);
				}
				for(ChangeRecord record : records)
				{
					if(pi.isCanceled())
					{
						syncRecord.setSyncError("Export user canceled operation");
						if(storeSyncRecord)
							try
							{
								theKeeper.putSyncRecord(syncRecord);
							} catch(PrismsRecordException e)
							{
								throw new PrismsRecordException(
									"Could not store synchronization record", e);
							}
						return syncRecord;
					}
					i++;
					pi.setProgress(i);
					if(!shouldSend(record))
						continue;
					if(sync.theLateIDs.contains(Record2Utils
						.getCenterID(getID(record.majorSubject))))
						itemWriter.writeSkippedChange();
					else
					{
						pi.setProgressText(baseText + "\nExported "
							+ prisms.util.PrismsUtils.encodeUnicode("" + record));
						itemWriter.writeChange(record, sync.theErrorChanges.contains(record.id));
					}
				}
				j += length;
			}
			itemWriter.end();
			jsw.endObject();
		} catch(RuntimeException e)
		{
			throw new PrismsRecordException("Synchronization export failed", e);
		}
		jsw.endArray();
		jsw.endObject();
		jsw.close();
		return syncRecord;
	}

	/**
	 * Generates a synchronization receipt and sends it to the writer
	 * 
	 * @param center The center to get the latest sync record and send the receipt for
	 * @param writer The writer to send the receipt over
	 * @param remoteSyncRecord The ID of the remote synchronization record, or -1 to generate a
	 *        receipt for the most recent sync receipt
	 * @throws IOException If a problem occurs writing to the writer
	 * @throws PrismsRecordException If an error occurs generating the synchronization receipt
	 */
	public void sendSyncReceipt(PrismsCenter center, Writer writer, int remoteSyncRecord)
		throws IOException, PrismsRecordException
	{
		SyncRecord [] records = theKeeper.getSyncRecords(center, Boolean.TRUE);
		prisms.util.json.JsonStreamWriter jsw = new prisms.util.json.JsonStreamWriter(writer);
		if(records.length == 0)
		{
			jsw.startObject();
			jsw.startProperty(CENTER_ID);
			jsw.writeNumber(new Integer(getKeeper().getCenterID()));
			jsw.startProperty("error");
			jsw.writeString("Synchronization not received");
			jsw.endObject();
			jsw.close();
			return;
		}
		SyncRecord record = records[records.length - 1];
		if(remoteSyncRecord >= 0 && record.getParallelID() != remoteSyncRecord)
		{
			record = null;
			for(SyncRecord r : records)
				if(r.getParallelID() == remoteSyncRecord)
				{
					record = r;
					break;
				}
			if(record == null)
			{
				jsw.startObject();
				jsw.startProperty(CENTER_ID);
				jsw.writeNumber(new Integer(getKeeper().getCenterID()));
				jsw.startProperty("error");
				jsw.writeString("Synchronization not received");
				jsw.endObject();
				jsw.close();
				return;
			}
		}
		long [] errors = theKeeper.getErrorChanges(record);
		jsw.startObject();
		jsw.startProperty(CENTER_ID);
		jsw.writeNumber(new Integer(getKeeper().getCenterID()));
		jsw.startProperty("syncRecord");
		jsw.writeNumber(new Integer(record.getParallelID()));
		jsw.startProperty(PARALLEL_ID);
		jsw.writeNumber(new Integer(record.getID()));
		jsw.startProperty("syncError");
		jsw.writeString(record.getSyncError());
		if(errors.length > 0)
		{
			jsw.startProperty("errors");
			jsw.startArray();
			for(long err : errors)
				jsw.writeNumber(new Long(err));
			jsw.endArray();
		}
		jsw.endObject();
		jsw.close();
	}

	/**
	 * Reads a synchronization receipt generated by another center
	 * 
	 * @param reader The reader to read the receipt from
	 * @throws IOException If an error occurs reading or importing the receipt
	 */
	public void readSyncReceipt(Reader reader) throws IOException
	{
		try
		{
			new prisms.util.json.SAJParser().parse(reader, new SyncReceiptHandler());
		} catch(prisms.util.json.SAJParser.ParseException e)
		{
			log.error("Could not parse sync receipt", e);
			throw new IOException("Could not parse sync receipt: " + e.getMessage());
		} catch(RuntimeWrapper e)
		{
			if(e.getCause() == null)
				log.error("Could not read sync receipt", e);
			else
				log.error("Could not read sync receipt", e.getCause());
		}
	}

	/**
	 * Checks for the number of items that this center needs to send a remote center in order to get
	 * the remote center's data set up-to-date with this center
	 * 
	 * @param center The remote center to check
	 * @param changes The latest change times for each center that has changes on the remote center
	 * @param withRecords Whether the remote center makes use of record-keeping. If false,
	 *        synchronization can be optimized further in some cases.
	 * @return The number of changes needed to update the remote center--[0] is the number of items
	 *         that must be sent as a result of being out-of-date; [1] is the number of changes
	 *         needed to be sent.
	 * @throws PrismsRecordException If an error occurs accessing the data
	 */
	public int [] checkSync(final PrismsCenter center, LatestCenterChange [] changes,
		boolean withRecords) throws PrismsRecordException
	{
		SyncOutput sync = getSyncOutput(center, changes, withRecords, false);

		int [] ret = new int [] {0, 0};
		if(!sync.theLateIDs.isEmpty())
		{
			prisms.records2.Synchronize2Impl.ItemIterator allItems;
			try
			{
				allItems = theImpl.getAllItems(sync.theLateIDs.toArray(), center);
			} catch(PrismsRecordException e)
			{
				throw new prisms.records2.PrismsRecordException(
					"Could not retrieve all items for synchronization", e);
			}
			while(allItems.hasNext())
			{
				try
				{
					allItems.next();
					ret[0]++;
				} catch(PrismsRecordException e)
				{
					throw new prisms.records2.PrismsRecordException(
						"Could not retrieve item for synchronization", e);
				}
			}
		}
		ret[1] = sync.theChangeIDs.length;
		return ret;
	}

	/**
	 * @return The implementation that interfaces specific sets of java objects with this PRISMS
	 *         synchronizer
	 */
	public Synchronize2Impl getImpl()
	{
		return theImpl;
	}

	/**
	 * @return The record keeper that this synchronizer uses to keep track of changes
	 */
	public RecordKeeper2 getKeeper()
	{
		return theKeeper;
	}

	/**
	 * Gets the type of an item
	 * 
	 * @param item The item to get the string type of
	 * @return The string type of the given item
	 * @throws PrismsRecordException If the item's type is not recognized
	 */
	public String getType(Object item) throws PrismsRecordException
	{
		if(item instanceof PrismsCenter)
			return "center";
		else if(item instanceof AutoPurger2)
			return "purger";
		else
			return theImpl.getType(item.getClass());
	}

	/**
	 * Gets the identifier of an item
	 * 
	 * @param item The item to get the identifier of
	 * @return The item's identifier
	 */
	public long getID(Object item)
	{
		if(item instanceof PrismsCenter)
			return ((PrismsCenter) item).getID();
		else if(item instanceof AutoPurger2)
			return 0;
		else
			return theImpl.getID(item);
	}

	Object [] getDepends(Object item) throws PrismsRecordException
	{
		if(item instanceof PrismsCenter || item instanceof AutoPurger2)
			return new Object [0];
		else
			return theImpl.getDepends(item);
	}

	boolean verifySyncSuccess(SyncRecord syncRecord) throws PrismsRecordException
	{
		boolean ret = true;
		long [] changeIDs = theKeeper.getSyncChanges(syncRecord);
		long [] batch = new long [changeIDs.length < 100 ? changeIDs.length : 100];
		for(int i = 0; i < changeIDs.length;)
		{
			int len = changeIDs.length - i < batch.length ? changeIDs.length - i : batch.length;
			System.arraycopy(changeIDs, i, batch, 0, len);
			ChangeRecord [] records = theKeeper.getChanges(batch);
			for(ChangeRecord record : records)
			{
				if(record == null)
					ret = false;
				else if(record instanceof ChangeRecordError)
					ret = false;
			}
			i += len;
		}
		return ret;
	}

	/**
	 * Screens out changes that should not be sent for synchronization. Such changes may be kept
	 * only for record-keeping purposes on local data, as is the case with changes to PRISMS centers
	 * and auto-purge functionality.
	 * 
	 * @param record The record to test
	 * @return Whether the change record is one that should be sent to the remote center on sync
	 */
	protected boolean shouldSend(ChangeRecord record)
	{
		if(record instanceof ChangeRecordError)
		{
			for(PrismsChange pc : PrismsChange.values())
				if(pc.name().equals(((ChangeRecordError) record).getSubjectType()))
					return false;
			return true;
		}
		else
			return !(record.type.subjectType instanceof PrismsChange);
	}

	Object getCurrentValue(ChangeRecord record) throws PrismsRecordException
	{
		if(record.type.subjectType instanceof PrismsChange)
		{
			switch((PrismsChange) record.type.subjectType)
			{
			case center:
				PrismsCenter center = (PrismsCenter) record.majorSubject;
				switch((PrismsChange.CenterChange) record.type.changeType)
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
				AutoPurger2 purger = (AutoPurger2) record.majorSubject;
				switch((PrismsChange.AutoPurgeChange) record.type.changeType)
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
			return null;
		}
		else
			return getImpl().getCurrentValue(record);
	}

	void writeItem(Object item, prisms.util.json.JsonSerialWriter jsonWriter,
		prisms.records2.Synchronize2Impl.ItemWriter itemWriter, boolean justID) throws IOException,
		PrismsRecordException
	{
		if(item instanceof PrismsCenter)
		{
			if(justID)
				return;
			PrismsCenter center = (PrismsCenter) item;
			jsonWriter.startProperty("name");
			jsonWriter.writeString(center.getName());
			if(center.getCenterID() < 0)
			{
				jsonWriter.startProperty("centerID");
				jsonWriter.writeNumber(new Integer(center.getCenterID()));
			}
			jsonWriter.startProperty("serverURL");
			jsonWriter.writeString(center.getServerURL());
			jsonWriter.startProperty("serverUserName");
			jsonWriter.writeString(center.getServerUserName());
			jsonWriter.startProperty("serverPassword");
			jsonWriter.writeString(center.getServerPassword());
			jsonWriter.startProperty("syncFrequency");
			jsonWriter.writeNumber(new Long(center.getServerSyncFrequency()));
			jsonWriter.startProperty("clientUser");
			itemWriter.writeItem(center.getClientUser());
			jsonWriter.startProperty("changeSaveTime");
			jsonWriter.writeNumber(new Long(center.getChangeSaveTime()));
			jsonWriter.startProperty("syncPriority");
			jsonWriter.writeNumber(new Integer(center.getPriority()));
			if(center.isDeleted())
			{
				jsonWriter.startProperty("deleted");
				jsonWriter.writeBoolean(true);
			}
		}
		else if(item instanceof AutoPurger2)
		{
			if(justID)
				return;
			AutoPurger2 purger = (AutoPurger2) item;
			jsonWriter.startProperty("age");
			jsonWriter.writeNumber(new Long(purger.getAge()));
			jsonWriter.startProperty("entryCount");
			jsonWriter.writeNumber(new Integer(purger.getEntryCount()));
			if(purger.getExcludeUsers().length > 0)
			{
				jsonWriter.startProperty("excludeUsers");
				jsonWriter.startArray();
				for(RecordUser user : purger.getExcludeUsers())
					itemWriter.writeItem(user);
				jsonWriter.endArray();
			}
			if(purger.getExcludeTypes().length > 0)
			{
				jsonWriter.startProperty("excludeTypes");
				jsonWriter.startArray();
				for(RecordType type : purger.getExcludeTypes())
				{
					jsonWriter.startObject();
					jsonWriter.startProperty("subjectType");
					jsonWriter.writeString(type.subjectType.name());
					if(type.changeType != null)
					{
						jsonWriter.startProperty("changeType");
						jsonWriter.writeString(type.changeType.name());
					}
					jsonWriter.startProperty("additivity");
					jsonWriter.writeString(type.additivity > 0 ? "+" : (type.additivity < 0 ? "-"
						: "0"));
					jsonWriter.endObject();
				}
				jsonWriter.endArray();
			}
		}
		else
			theImpl.writeItem(item, jsonWriter, itemWriter, justID);
	}

	Object parseID(JSONObject json, Synchronize2Impl.ItemReader reader, boolean [] newItem)
		throws PrismsRecordException
	{
		/* Currently at least, centers and auto-purge settings are local only, so these will never
		 * be sent across, meaning this code here will never be executed.  I'm leaving it in in case
		 * this condition ever changes. */
		if("center".equals(json.get("type")))
		{
			// Need to retrieve existing center
			PrismsCenter center = new PrismsCenter(((Number) json.get("id")).intValue(),
				(String) json.get("name"));
			newItem[0] = true;
			return center;
		}
		else if("purger".equals(json.get("type")))
		{
			// Need to retrieve existing auto-purger
			AutoPurger2 purger = new AutoPurger2();
			newItem[0] = true;
			return purger;
		}
		else
			return theImpl.parseID(json, reader, newItem);
	}

	void parseContent(Object item, JSONObject json, boolean newItem,
		prisms.records2.Synchronize2Impl.ItemReader reader) throws PrismsRecordException
	{
		/* Currently at least, centers and auto-purge settings are local only, so these will never
		 * be sent across, meaning this code here will never be executed.  I'm leaving it in in case
		 * this condition ever changes. */
		if(item instanceof PrismsCenter)
		{
			// Need to retrieve existing center
			PrismsCenter center = (PrismsCenter) item;
			if(json.get("centerID") != null)
				center.setCenterID(((Number) json.get("centerID")).intValue());
			center.setPriority(((Number) json.get("syncPriority")).intValue());
			center.setServerURL((String) json.get("serverURL"));
			center.setServerUserName((String) json.get("serverUserName"));
			center.setServerPassword((String) json.get("serverPassword"));
			center.setServerSyncFrequency(((Number) json.get("syncFrequency")).longValue());
			center.setClientUser((RecordUser) reader.read((JSONObject) json.get("clientUser")));
			center.setChangeSaveTime(((Number) json.get("changeSaveTime")).longValue());
			if(json.get("deleted") != null && ((Boolean) json.get("deleted")).booleanValue())
				center.setDeleted(true);
			// Need to fire events here if changed
		}
		else if(item instanceof AutoPurger2)
		{
			// Need to retrieve existing auto-purger
			AutoPurger2 purger = (AutoPurger2) item;
			purger.setAge(((Number) json.get("age")).longValue());
			purger.setEntryCount(((Number) json.get("entryCount")).intValue());
			if(json.get("excludeUsers") != null)
				for(Object user : (org.json.simple.JSONArray) json.get("excludeUsers"))
					purger.addExcludeUser((RecordUser) reader.read((JSONObject) user));
			if(json.get("excludeTypes") != null)
				for(Object type : (org.json.simple.JSONArray) json.get("excludeUsers"))
				{
					JSONObject jsonType = (JSONObject) type;
					SubjectType subject = getSubjectType((String) jsonType.get("subjectType"));
					ChangeType change = null;
					if(jsonType.get("changeType") != null)
						change = Record2Utils.getChangeType(subject,
							(String) jsonType.get("changeType"));
					char addCh = ((String) jsonType.get("additivity")).charAt(0);
					int add = addCh == '+' ? 1 : (addCh == '-' ? -1 : 0);
					purger.addExcludeType(new RecordType(subject, change, add));
				}
			// Need to fire events here if changed
		}
		else
			theImpl.parseContent(item, json, newItem, reader);
	}

	prisms.records2.ChangeRecord parseChange(JSONObject json, PS2ItemReader reader)
	{
		if(json.containsKey("error"))
			return parseErrorChange(json, reader);
		long id = ((Number) json.get("id")).longValue();
		long time = ((Number) json.get("time")).longValue();
		long userID = ((Number) json.get("user")).longValue();
		SubjectType subjectType;
		try
		{
			subjectType = getSubjectType((String) json.get("subjectType"));
		} catch(PrismsRecordException e)
		{
			return parseErrorChange(json, reader);
		}
		if(subjectType == ChangeRecordError.ErrorSubjectType)
			return parseErrorChange(json, reader);
		String ctName = (String) json.get("changeType");
		prisms.records2.ChangeType changeType = null;
		if(ctName != null)
		{
			for(prisms.records2.ChangeType ct : (prisms.records2.ChangeType[]) subjectType
				.getChangeTypes().getEnumConstants())
				if(ct.name().equals(ctName))
				{
					changeType = ct;
					break;
				}
			if(changeType == null)
				return parseErrorChange(json, reader);
		}
		char addChar = ((String) json.get("additivity")).charAt(0);
		int add = addChar == '+' ? 1 : (addChar == '-' ? -1 : 0);
		RecordUser user;
		Object major = json.get("majorSubject");
		if(major instanceof JSONObject)
		{
			try
			{
				major = reader.read((JSONObject) major);
			} catch(Throwable e)
			{
				log.error("Could not parse major subject", e);
				return parseErrorChange(json, reader);
			}
		}
		if(major instanceof RecordUser && ((RecordUser) major).getID() == userID)
			user = (RecordUser) major;
		else
		{
			try
			{
				user = theImpl.getUser(userID, reader);
			} catch(PrismsRecordException e)
			{
				log.error("Could not get user", e);
				return parseErrorChange(json, reader);
			}
		}
		Object data1 = json.get("data1");
		Object data2 = json.get("data2");
		Object minor = json.get("minorSubject");
		Object preValue = json.get("preValue");
		try
		{
			if(data1 instanceof JSONObject)
				data1 = reader.read((JSONObject) data1);
			if(data2 instanceof JSONObject)
				data2 = reader.read((JSONObject) data2);
			if(minor instanceof JSONObject)
				minor = reader.read((JSONObject) minor);
			if(preValue instanceof JSONObject)
				preValue = reader.read((JSONObject) preValue);
		} catch(Throwable e)
		{
			log.error("Could not parse change data", e);
			return parseErrorChange(json, reader);
		}
		prisms.records2.RecordPersister2.ChangeData data;
		try
		{
			data = theImpl.getData(subjectType, changeType, major, minor, data1, data2, preValue,
				reader);
		} catch(Throwable e)
		{
			log.error("Could not get change data", e);
			return parseErrorChange(json, reader);
		}
		try
		{
			if(data.data1 != null)
				reader.store(getType(data.data1.getClass()), getID(data.data1), data.data1);
			if(data.data2 != null)
				reader.store(getType(data.data2.getClass()), getID(data.data2), data.data2);
			if(data.minorSubject != null)
				reader.store(getType(data.minorSubject.getClass()), getID(data.minorSubject),
					data.minorSubject);
			if(data.preValue != null)
			{
				String type = getType(data.preValue);
				if(type != null)
					reader.store(type, getID(data.preValue), data.preValue);
			}
		} catch(PrismsRecordException e)
		{
			log.error("Could not store data", e);
		}
		try
		{
			return new prisms.records2.ChangeRecord(id, time, user, subjectType, changeType, add,
				data.majorSubject, data.minorSubject, data.preValue, data.data1, data.data2);
		} catch(IllegalArgumentException e)
		{
			log.error("Could not instantiate change record with id " + id, e);
			return parseErrorChange(json, reader);
		}
	}

	int relativePriority(PrismsCenter center)
	{
		return center.getPriority() - theKeeper.getLocalPriority();
	}

	private SubjectType getSubjectType(String name) throws PrismsRecordException
	{
		for(PrismsChange ch : PrismsChange.values())
			if(ch.name().equals(name))
				return ch;
		return theImpl.getSubjectType(name);
	}

	private ChangeRecordError parseErrorChange(JSONObject json, ItemGetter getter)
	{
		long id = ((Number) json.get("id")).longValue();
		long time = ((Number) json.get("time")).longValue();
		final long userID = ((Number) json.get("user")).longValue();
		RecordUser user;
		try
		{
			user = theImpl.getUser(userID, getter);
		} catch(PrismsRecordException e)
		{
			user = new RecordUser()
			{

				public long getID()
				{
					return userID;
				}

				public String getName()
				{
					return "Unknown";
				}

				public boolean isDeleted()
				{
					return false;
				}
			};
		}
		ChangeRecordError ret = new ChangeRecordError(id, time, user);
		ret.setSubjectType((String) json.get("subjectType"));
		ret.setChangeType((String) json.get("changeType"));
		String add = (String) json.get("additivity");
		ret.setAdditivity(add.equals("+") ? 1 : (add.equals("-") ? -1 : 0));

		Object major = json.get("majorSubject");
		Object data1 = json.get("data1");
		Object data2 = json.get("data2");
		Object minor = json.get("minorSubject");
		Object preValue = json.get("preValue");
		if(major instanceof Number)
			ret.setMajorSubject(null, ((Number) major).longValue());
		else
			ret.setMajorSubject(null, ((Number) ((JSONObject) major).get("id")).longValue());

		if(minor instanceof Number)
			ret.setMinorSubject(null, ((Number) minor).longValue());
		else if(minor != null)
			ret.setMinorSubject(null, ((Number) ((JSONObject) minor).get("id")).longValue());

		if(data1 instanceof Number)
			ret.setData1(null, ((Number) data1).longValue());
		else if(data1 != null)
			ret.setData1(null, ((Number) ((JSONObject) data1).get("id")).longValue());

		if(data2 instanceof Number)
			ret.setData2(null, ((Number) data2).longValue());
		else if(data2 != null)
			ret.setData2(null, ((Number) ((JSONObject) data2).get("id")).longValue());

		if(preValue instanceof Boolean)
			preValue = preValue.toString();
		else if(preValue instanceof JSONObject)
			preValue = ((JSONObject) preValue).get("id");
		ret.setPreValue(null, preValue);
		return ret;
	}
}
