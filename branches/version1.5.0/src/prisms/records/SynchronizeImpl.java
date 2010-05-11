/*
 * SynchronizeImpl.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import prisms.arch.PrismsApplication;
import prisms.ui.UI.DefaultProgressInformer;

/**
 * Provides implementation for synchronizing a set of data between centers
 * 
 * @param <SyncDataType> The type of SyncData required by this implementation
 */
public interface SynchronizeImpl<SyncDataType extends SynchronizeImpl.SyncData>
{
	/**
	 * Synchronization data passed to all synchronization methods. Allows caching and easy access to
	 * frequently-used data.
	 */
	public static class SyncData
	{
		/**
		 * The synchronizer using the implementation
		 */
		public final PrismsSynchronizer<?> synchronizer;

		/**
		 * The center to import from
		 */
		public final PrismsCenter center;

		/**
		 * The session to use to get application data and perform changes
		 */
		public final PrismsApplication app;

		/**
		 * The progress informer interested in information about synchronization progress. May be
		 * null.
		 */
		public final DefaultProgressInformer pi;

		/**
		 * The items to synchronize. May be null.
		 */
		public final Object [] items;

		/**
		 * The changes to synchronize
		 */
		public final ChangeRecord [] mods;

		/**
		 * Creates a sync data object
		 * 
		 * @param sync The synchronizer using the implementation
		 * @param _center The center being imported from
		 * @param anApp The application to use for data retrieval and modification
		 * @param _pi The progress informer
		 * @param _items The items to synchronize
		 * @param _mods The changes to synchronize
		 */
		public SyncData(PrismsSynchronizer<?> sync, PrismsCenter _center, PrismsApplication anApp,
			DefaultProgressInformer _pi, Object [] _items, ChangeRecord [] _mods)
		{
			synchronizer = sync;
			center = _center;
			app = anApp;
			pi = _pi;
			items = _items;
			mods = _mods;
		}

		/**
		 * Called when the synchronizer is finished with this data
		 */
		public void dispose()
		{
		}
	}

	/**
	 * Gets a subject type by its stored name
	 * 
	 * @param typeName The name of the subject (see {@link SubjectType#name()})
	 * @return The subject type corresponding to the name
	 * @throws PrismsRecordException If the subject type cannot be found or another error occurs
	 */
	SubjectType getType(String typeName) throws PrismsRecordException;

	/**
	 * Serializes an item to JSON for passing over a network
	 * 
	 * @param item The item to serialize. It may be a major or minor subject, a metadata, or a field
	 *        value
	 * @return The serialized item
	 * @throws PrismsRecordException If an error occurs during serialization
	 */
	org.json.simple.JSONObject serializeItem(Object item) throws PrismsRecordException;

	/**
	 * Deserializes an item from JSON
	 * 
	 * @param json The JSON-serialized item
	 * @return The deserialized item
	 * @throws PrismsRecordException If an error occurs deserializing the item
	 */
	Object deserializeItem(org.json.simple.JSONObject json) throws PrismsRecordException;

	/**
	 * Generates synchronization data to pass to the other synchronization methods
	 * 
	 * @param synchronizer The synchronizer using this implementation
	 * @param center The center being imported from
	 * @param app The application to use to access and modify data
	 * @param pi The progress informer
	 * @param items The items to synchronize
	 * @param mods The changes to synchronize
	 * @return The sync data to pass to the other synchronization methods
	 */
	SyncDataType genSyncData(PrismsSynchronizer<? super SyncDataType> synchronizer,
		PrismsCenter center, PrismsApplication app, prisms.ui.UI.DefaultProgressInformer pi,
		Object [] items, ChangeRecord [] mods);

	/**
	 * Gets the value of a field in a record
	 * 
	 * @param record The record containing the subject to get the value from and the change type
	 *        determining what field to get the value of
	 * @param data The sync data to use
	 * @return The value of the record's change field in the record's subject
	 * @throws PrismsRecordException If an error occurs getting the value
	 */
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

	/**
	 * @param app The application to use to get the export data
	 * @param center The center to send the items to
	 * @return All items in the application that should be synchronized with another center
	 */
	Object [] getExportItems(PrismsApplication app, PrismsCenter center);
}
