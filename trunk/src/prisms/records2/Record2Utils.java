/*
 * RecordUtils.java Created Jul 30, 2010 by Andrew Butler, PSL
 */
package prisms.records2;

import java.io.IOException;

import prisms.util.LongList;
import prisms.util.ObfuscatingStream;
import prisms.util.json.SAJParser.ParseException;

/**
 * A set of utility methods useful in the prisms.records2 package
 */
public class Record2Utils
{
	/**
	 * The range of IDS that may exist in a given PRISMS center
	 */
	public static int theCenterIDRange = 1000000000;

	/**
	 * @param objectID The ID of an object
	 * @return The ID of the center where the given object was created
	 */
	public static int getCenterID(long objectID)
	{
		return (int) (objectID / theCenterIDRange);
	}

	/**
	 * Gets the change type under the subject type by name
	 * 
	 * @param subjectType The subject type to get a change type under
	 * @param typeName The name of the change type to get
	 * @return The change type of the given name under the given subject type
	 * @throws PrismsRecordException If no such change exists for the given subject type
	 */
	public static ChangeType getChangeType(SubjectType subjectType, String typeName)
		throws PrismsRecordException
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
	 * Checks a set of centers for the change save time and last time they synchronized to this
	 * center in order to get a purge-safe time after which change should not be purged so that they
	 * will be available to clients.
	 * 
	 * @param centers The centers to use to determine the safe time for purging changes
	 * @return The purge-safe time after which changes should not be purged
	 */
	public static long getPurgeSafeTime(PrismsCenter [] centers)
	{
		long nowTime = System.currentTimeMillis();
		long ret = nowTime;
		for(PrismsCenter center : centers)
		{
			long lastSync = center.getLastExport();
			long saveTime = center.getChangeSaveTime();

			if(lastSync <= 0)
			{ /* If this center has not synchronized yet, we have to send the whole data set next
				* time anyway, so this center doesn't affect our safe time */
				continue;
			}
			if(lastSync < nowTime - saveTime)
			{ /* If the center hasn't synchronized since its configured save time, we don't save
				* modifications for the center anymore to conserve space. This may hurt
				* synchronization because it's more likely that all items will have to be sent to
				* the center when it next synchronizes. Also, if the local center becomes
				* out-of-date by not synchronizing with this center beyond its configured save time
				* on the remote center, some modifications may be lost on whichever center
				* synchronizes first. We still don't save modifications because we don't know when
				* the center will be able to synchronize again if ever. To mitigate this problem,
				* modification save times should be increased through the user interface. */
				continue;
			}
			if(ret > lastSync)
				ret = lastSync;
		}
		return ret;
	}

	/**
	 * Gets changes that need to be sent to the given center for synchronization
	 * 
	 * @param keeper The record keeper being used for synchronization
	 * @param center The center synchronizing with this center
	 * @param centerID The ID of the center to get changes by
	 * @param subjectCenter The ID of the data set to get changes to
	 * @param lastChange The time of the last change that the remote center has by the change center
	 *        to the given data set
	 * @return The IDs of all changes that should be sent to the remote center to be completely
	 *         synchronized with this center
	 * @throws prisms.records2.PrismsRecordException If an error occurs retrieving the data
	 */
	public static long [] getSyncChanges(RecordKeeper2 keeper, PrismsCenter center, int centerID,
		int subjectCenter, long lastChange) throws PrismsRecordException
	{
		return keeper.getChangeIDs(centerID, subjectCenter, lastChange);
	}

	/**
	 * Gets changes that need to be sent to the given center for synchronization because they were
	 * incorrectly received earlier
	 * 
	 * @param keeper The record keeper being used for synchronization
	 * @param center The center synchronizing with this center
	 * @param centerID The ID of the center to get changes by
	 * @param subjectCenter The ID of the data set to get changes to
	 * @return The IDs of all changes that should be sent to the remote center to be completely
	 *         synchronized with this center
	 * @throws prisms.records2.PrismsRecordException If an error occurs retrieving the data
	 */
	public static long [] getSyncErrorChanges(RecordKeeper2 keeper, PrismsCenter center,
		int centerID, int subjectCenter) throws PrismsRecordException
	{
		LongList ret = new LongList();
		SyncRecord [] records = keeper.getSyncRecords(center, Boolean.FALSE);

		for(int r = records.length - 1; r >= 0; r--)
		{
			SyncRecord record = records[r];
			if(record.getSyncError() != null)
				continue;
			if(!ret.isEmpty())
			{
				long [] successChanges = keeper.getSuccessChanges(record);
				for(int i = 0; i < ret.size(); i++)
				{
					boolean contained = false;
					for(int j = 0; j < successChanges.length; j++)
						if(successChanges[j] == ret.get(i))
						{
							contained = true;
							break;
						}
					if(contained)
					{
						ret.remove(i);
						i--;
					}
				}
			}
			long [] errorChanges = keeper.getErrorChanges(record);
			for(int i = 0; i < errorChanges.length; i++)
				if(getCenterID(errorChanges[i]) == centerID
					&& keeper.getSubjectCenter(errorChanges[i]) == subjectCenter
					&& !ret.contains(errorChanges[i]))
					ret.add(errorChanges[i]);
		}
		records = null;

		return ret.toArray();
	}

	// /**
	// * Gets changes that should be re-applied locally after a potentially destructive
	// * synchronization import in order to preserve as much local data as possible.
	// *
	// * @param keeper The record keeper to get the changes from
	// * @param center The center that the synchronization is with
	// * @param subjectCenters The subject centers that are out-of-date for synchronization and need
	// * items imported
	// * @return The IDs of all changes that need to be re-applied after destructive sync import
	// with
	// * the given center
	// * @throws PrismsRecordException If an error occurs retrieving the data
	// */
	// public static long [] getPostDestructiveSyncChanges(RecordKeeper2 keeper, PrismsCenter
	// center,
	// int [] subjectCenters) throws PrismsRecordException
	// {
	// long lastSync = -1;
	// for(SyncRecord record : keeper.getSyncRecords(center, Boolean.TRUE))
	// if(record.getSyncError() == null && record.getSyncTime() > lastSync)
	// lastSync = record.getSyncTime();
	// LongList ret = new LongList();
	// for(int i = 0; i < subjectCenters.length; i++)
	// ret.addAll(keeper.getChangeIDs(-1, subjectCenters[i], lastSync));
	// return keeper.sortChangeIDs(ret.toArray(), true);
	// }

	/**
	 * Tests whether one change is a successor of another
	 * 
	 * @param test The change to test
	 * @param change The change to see if <code>test</code> is a successor of
	 * @return Whether <code>test</code> is a successor of <code>change</code>
	 */
	public static boolean isSuccessor(ChangeRecord test, ChangeRecord change)
	{
		if(change instanceof prisms.records2.ChangeRecordError
			|| test instanceof prisms.records2.ChangeRecordError)
			return false;
		if(test.time < change.time)
			return false;
		if(!change.type.subjectType.equals(test.type.subjectType))
			return false;
		if(!equal(change.type.changeType, test.type.changeType))
			return false;
		if((change.type.additivity == 0) != (test.type.additivity == 0))
			return false;
		if(!change.majorSubject.equals(test.majorSubject))
			return false;
		if(!equal(change.minorSubject, test.minorSubject))
			return false;
		return true;
	}

	private static boolean equal(Object o1, Object o2)
	{
		return o1 == null ? o2 == null : o1.equals(o2);
	}

	/**
	 * Retrieves and serializes the time of the latest change for each center that this center has
	 * modifications from. This data is required by a remote center in order to receive
	 * synchronization data.
	 * 
	 * @param keeper The record keeper to use to retrieve the data
	 * @param jsw The writer to serialize the change data to
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 * @throws java.io.IOException If an error occurs writing to the stream
	 */
	public static void serializeCenterChanges(RecordKeeper2 keeper,
		prisms.util.json.JsonSerialWriter jsw) throws PrismsRecordException, java.io.IOException
	{
		jsw.startArray();
		int [] centerIDs = keeper.getAllCenterIDs();
		for(int centerID : centerIDs)
		{
			for(int subjectCenter : centerIDs)
			{
				long lastChange = keeper.getLatestChange(centerID, subjectCenter);
				if(lastChange > 0)
				{
					jsw.startObject();
					jsw.startProperty("centerID");
					jsw.writeNumber(new Integer(centerID));
					jsw.startProperty("subjectCenter");
					jsw.writeNumber(new Integer(subjectCenter));
					jsw.startProperty("latestChange");
					jsw.writeNumber(new Long(lastChange));
					jsw.endObject();
				}
			}
		}
		jsw.endArray();
	}

	/**
	 * Retrieves and serializes the time of the latest change for each center that this center has
	 * modifications from. This data is required by a remote center in order to receive
	 * synchronization data.
	 * 
	 * @param keeper The record keeper to use to retrieve the data
	 * @return The serialized change data
	 * @throws PrismsRecordException If an error occurs retrieving the data
	 */
	public static org.json.simple.JSONArray serializeCenterChanges(RecordKeeper2 keeper)
		throws PrismsRecordException
	{
		java.io.StringWriter writer = new java.io.StringWriter();
		try
		{
			serializeCenterChanges(keeper, new prisms.util.json.JsonStreamWriter(writer));
			return (org.json.simple.JSONArray) new prisms.util.json.SAJParser().parse(
				new java.io.StringReader(writer.toString()),
				new prisms.util.json.SAJParser.DefaultHandler());
		} catch(IOException e)
		{
			throw new PrismsRecordException("IO Exception???!!!", e);
		} catch(ParseException e)
		{
			throw new PrismsRecordException("Parse Exception!!!", e);
		}
	}

	/**
	 * Wraps a stream to write exported data. The data is first zipped, then obfuscated to prevent
	 * human modification.
	 * 
	 * @param os The output stream to write the exported data to
	 * @return The wrapped output stream to write data to in-the-clear
	 * @throws java.io.IOException If an error occurs wrapping the stream
	 */
	public static java.io.OutputStream exportStream(java.io.OutputStream os)
		throws java.io.IOException
	{
		return prisms.util.ObfuscatingStream.obfuscate(new java.util.zip.GZIPOutputStream(os));
	}

	/**
	 * Wraps an exported stream.
	 * 
	 * @param is The exported stream to read the data from
	 * @return The plain-text stream
	 * @throws java.io.IOException If an error occurs wrapping the stream
	 */
	public static java.io.InputStream importStream(java.io.InputStream is)
		throws java.io.IOException
	{
		return ObfuscatingStream.unobfuscate(new java.util.zip.GZIPInputStream(is));
	}
}
