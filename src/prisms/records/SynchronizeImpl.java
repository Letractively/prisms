/*
 * SynchronizeImpl.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import prisms.arch.PrismsSession;
import prisms.ui.UI.DefaultProgressInformer;

public interface SynchronizeImpl<SyncDataType extends SynchronizeImpl.SyncData>
{
	public static class SyncData
	{
		public final PrismsCenter center;

		public final PrismsSession session;

		public final DefaultProgressInformer pi;

		public final Object [] items;

		public final ChangeRecord [] mods;

		public SyncData(PrismsCenter _center, PrismsSession aSession, DefaultProgressInformer _pi,
			Object [] _items)
		{
			this(_center, aSession, _pi, _items, null);
		}

		public SyncData(PrismsCenter _center, PrismsSession aSession, DefaultProgressInformer _pi,
			ChangeRecord [] _mods)
		{
			this(_center, aSession, _pi, null, _mods);
		}

		private SyncData(PrismsCenter _center, PrismsSession aSession, DefaultProgressInformer _pi,
			Object [] _items, ChangeRecord [] _mods)
		{
			center = _center;
			session = aSession;
			pi = _pi;
			items = _items;
			mods = _mods;
		}

		public void dispose()
		{
		}
	}

	SubjectType getType(String typeName) throws PrismsRecordException;

	org.json.simple.JSONObject serializeItem(Object item) throws PrismsRecordException;

	Object deserializeItem(org.json.simple.JSONObject json) throws PrismsRecordException;

	SyncDataType genSyncData(PrismsCenter center, PrismsSession session,
		prisms.ui.UI.DefaultProgressInformer pi, Object [] items, ChangeRecord [] mods);

	Object getFieldValue(ChangeRecord record, SyncDataType data) throws PrismsRecordException;

	/**
	 * Adds an object to the session (and to persistent storage, if applicable) or updates it with
	 * the given state.
	 * 
	 * @param obj The object to add or update
	 * @param data Synchronization data to use to synchronize the object
	 * @throws PrismsRecordException If an error occurs performing the change
	 */
	void syncObject(Object obj, SyncDataType data) throws PrismsRecordException;

	/**
	 * Tests whether an object or a relationship exists in the data set governed by the
	 * synchronizer. If the object exists in the database but is marked as deleted, this method
	 * returns false.
	 * 
	 * @param record The change record containing the object or relationship to find
	 * @param data The synchronization data to assist in the operation
	 * @return The
	 * @throws PrismsRecordException
	 */
	boolean itemExists(ChangeRecord record, SyncDataType data) throws PrismsRecordException;

	/**
	 * Adds an object to the set of data governed by the synchronizer
	 * 
	 * @param record The record containing the data to add
	 * @param data The synchronization data to assist in the operation
	 * @return Whether the synchronizer should persist the modification after this operation. If the
	 *         add was unsuccessful, the modification should not be persisted because it will not
	 *         key to anything
	 * @throws PrismsRecordException
	 */
	boolean addObject(ChangeRecord record, SyncDataType data) throws PrismsRecordException;

	/**
	 * Removes an object from the set of data governed by the synchronizer. This may just mean it
	 * should be removed from memory and a database deleted flag set.
	 * 
	 * @param record The record containing the data to remove
	 * @param data The synchronization data to assist in the operation
	 * @return Whether the synchronizer should persist the modification after this operation. If the
	 *         object was not found, the modification should not be persisted because it will not
	 *         key to anything
	 * @throws PrismsRecordException
	 */
	boolean deleteObject(ChangeRecord record, SyncDataType data) throws PrismsRecordException;

	/**
	 * Modifies an object in the set of data governed by the synchronizer
	 * 
	 * @param record The record containing the data to modify
	 * @param fieldValue The new value for the field
	 * @param data The synchronization data to assist in the operation
	 * @return Whether the synchronizer should persist the modification after this operation. If the
	 *         object was not found, the modification should not be persisted because it will not
	 *         key to anything
	 * @throws PrismsRecordException
	 */
	boolean setField(ChangeRecord record, Object fieldValue, SyncDataType data)
		throws PrismsRecordException;
}
