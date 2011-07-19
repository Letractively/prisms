/*
 * SynchronizerImpl.java Created Jul 29, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import org.json.simple.JSONObject;

/**
 * Interfaces between a {@link PrismsSynchronizer} and a java object data set. Each instanceof an
 * {@link SynchronizeImpl} has a set of objects that it administrates. Its methods allow a
 * synchronizer to keep the impl's data in sync with another impl at another location.
 */
public interface SynchronizeImpl
{
	/** Writes items in an efficient manner */
	public static interface ItemWriter
	{
		/**
		 * Writes a representation of an item to a stream
		 * 
		 * @param item The item to write
		 * @throws java.io.IOException If an error occurs writing to the stream
		 * @throws PrismsRecordException If an error occurs serializing the item
		 */
		void writeItem(Object item) throws java.io.IOException, PrismsRecordException;
	}

	/** Reads items from their JSON representations */
	public static interface ItemReader
	{
		/**
		 * Reads an item from its JSON representation. This may entail caling the implementation to
		 * parse or it may not.
		 * 
		 * @param json The JSON representation of the item
		 * @return The item represented by the JSON
		 * @throws PrismsRecordException If an error occurs deserializing the JSON
		 */
		Object read(JSONObject json) throws PrismsRecordException;

		/** @return Whether the current parse operation is a part of a change record */
		boolean isChange();

		/** @return The type of the change currently being parsed */
		RecordType getChangeType();

		/** @return The time of the change currently being parsed */
		long getChangeTime();

		/** @return The ID of the user that caused the change currently being parsed */
		long getChangeUserID();
	}

	/** Gets items that may not yet be available in the data set */
	public static interface ItemGetter
	{
		/**
		 * Gets an item that may not yet be represented in this data set. This method should only be
		 * called if the item has no representation in this data set.
		 * 
		 * @param type The type of the item
		 * @param id The ID of the item
		 * @return The item with the given type and ID. This will never be null
		 * @throws PrismsRecordException If there is no item available with the given type and ID or
		 *         if an error occurs retrieving it.
		 */
		Object getItem(String type, long id) throws PrismsRecordException;
	}

	/**
	 * Iterates over a set of items. Differs from java.util.Iterator in that this method may throw a
	 * {@link PrismsRecordException} from its {@link #next()} method
	 */
	public static interface ItemIterator
	{
		/**
		 * @return Whether there are any more items in the data set that have not been returned by
		 *         this iterator
		 * @throws PrismsRecordException If an error occurs determining whether there is more data
		 */
		boolean hasNext() throws PrismsRecordException;

		/**
		 * @return The next item in the data set
		 * @throws PrismsRecordException If an error occurs retrieving the next item
		 */
		Object next() throws PrismsRecordException;
	}

	// The following methods are common with RecordPersister2

	/**
	 * Gets a record user by ID
	 * 
	 * @param id The ID of the user
	 * @param getter The getter to get the user if it is not in this data set
	 * @return The user with the given ID
	 * @throws PrismsRecordException If no such user exists or an error occurs retriving the user
	 */
	RecordUser getUser(long id, ItemGetter getter) throws PrismsRecordException;

	/**
	 * Gets a subject type by its stored name
	 * 
	 * @param typeName The name of the subject (see {@link SubjectType#name()})
	 * @return The subject type corresponding to the name
	 * @throws PrismsRecordException If the subject type cannot be found or another error occurs
	 */
	prisms.records.SubjectType getSubjectType(String typeName) throws PrismsRecordException;

	/**
	 * Gets the unique identifier for a value
	 * 
	 * @param item The item to get the unique ID for
	 * @return The ID to identify the object in a change record
	 */
	long getID(Object item);

	/**
	 * Retrieves the values associated with a set of IDs. Each parameter may be the already-parsed
	 * value (which may be simply returned in the ChangeData return), the identifier for the value,
	 * the serialized value (only in the case of the preValue), or null (except in the case of the
	 * major subject).
	 * 
	 * @param subjectType The subject type of the change record
	 * @param changeType The type of the change
	 * @param majorSubject The major subject or the ID of the major subject to retrieve
	 * @param minorSubject The minor subject or the ID of the minor subject to retrieve. May be
	 *        null.
	 * @param data1 The first metadata or the ID of the first metadata to retrieve. May be null.
	 * @param data2 The second metadata or the ID of the second metadata to retrieve. May be null.
	 * @param preValue The previous value or the ID of the previous value in the change to retrieve.
	 *        May be null.
	 * @param getter The item getter to get items that do not exist in this data set
	 * @return The ChangeData containing the values of each non-null ID.
	 * @throws PrismsRecordException If an error occurs retrieving any of the data.
	 */
	prisms.records.RecordPersister.ChangeData getData(SubjectType subjectType,
		ChangeType changeType, Object majorSubject, Object minorSubject, Object data1,
		Object data2, Object preValue, ItemGetter getter) throws PrismsRecordException;

	// End common methods

	/* Versioning methods
	 * These methods allow data sources that are synchronized to be schema-modified without the need
	 * to distribute the schema change to every installation */

	/**
	 * @return The version of this synchronization implementation. See
	 *         {@link prisms.arch.PrismsConfig#compareVersions(String, String)} for the forms this
	 *         string may take
	 */
	String getVersion();

	/**
	 * @param change The change record to check
	 * @return Whether the given change record should be sent to the client
	 */
	boolean shouldSend(ChangeRecord change);

	// End versioning methods

	/**
	 * Gets a type name by java class
	 * 
	 * @param type The java type of the type to get
	 * @return The name of the type with the given java class
	 * @throws PrismsRecordException If the given type is unrecognized
	 */
	String getType(Class<?> type) throws PrismsRecordException;

	/**
	 * Gets the set of items on which the given item depends. This is NOT the set of all items that
	 * the given item refers to, but only those items without which the given item cannot exist.
	 * Those items which, if they are deleted, will cause the given item to be unavailable. None of
	 * the item's depends may depend on the item, or on another item which depends on the item, etc.
	 * The objects returned must be a typed part of this data set (i.e. getType(item) must not
	 * return null).
	 * 
	 * @param item The item to retrieive information from
	 * @return All items on which the given item depends
	 * @throws PrismsRecordException If an error occurs getting the data
	 */
	Object [] getDepends(Object item) throws PrismsRecordException;

	/**
	 * @param centerIDs The IDs of the centers to get the items created by
	 * @param syncCenter The center to which the items are to be sent
	 * @return An iterator that iterates over all items created by the given centers in the data set
	 * @throws PrismsRecordException If an error occurs generating the iterator
	 */
	ItemIterator getAllItems(int [] centerIDs, PrismsCenter syncCenter)
		throws PrismsRecordException;

	/**
	 * Writes an item to JSON output. Components of the item should be written using
	 * {@link ItemWriter#writeItem(Object)} method so that the writer can eliminate duplicates in
	 * the stream. When this method is called, the JSON object has already been started and its type
	 * and ID properties written to the writer.
	 * 
	 * @param item The item to write
	 * @param jsonWriter The writer to use to write json data
	 * @param itemWriter The writer to use to write contained data items
	 * @param change The change for which the item needs to be serialized, or null if the item is
	 *        being serialized as part of a complete data set (from
	 *        {@link #getAllItems(int[], PrismsCenter)})
	 * @param justID Whether the item should be written in an abbreviated form that allows it to be
	 *        parsed by ID to retrieve data that already exists in the data source.
	 * @throws java.io.IOException If an error occurs writing to the stream
	 * @throws PrismsRecordException If an error occurs writing the data
	 */
	void writeItem(Object item, prisms.util.json.JsonSerialWriter jsonWriter,
		ItemWriter itemWriter, boolean justID) throws java.io.IOException, PrismsRecordException;

	/**
	 * Parses an item's identity from JSON. The reader parameter should be utilized whenever one of
	 * the JSON object's properties is parsable using this impl. This allows the synchronization
	 * architecture to re-use parsed items, maintaining data integrity and preventing reparsing. If
	 * the item already exists in the impl's data set (identified by the "id" property), that item
	 * should be returned instead of creating a new item.
	 * 
	 * @param json The mixed-content JSON object to use to parse the item
	 * @param reader The item reader to help with parsing
	 * @param newItem The 0th element of this array should be set to true if the item had to be
	 *        created as a result of this method call (because the given identity was not found in
	 *        this data set)
	 * @return The parsed item
	 * @throws PrismsRecordException If an error occurs parsing the item
	 */
	Object parseID(JSONObject json, ItemReader reader, boolean [] newItem)
		throws PrismsRecordException;

	/**
	 * Parses an item's content from JSON. The reader parameter should be utilized whenever one of
	 * the JSON object's properties is parsable using this impl. This allows the synchronization
	 * architecture to re-use parsed items, maintaining data integrity and preventing reparsing.
	 * 
	 * @param item The item to modify
	 * @param json The json data to modify the item with
	 * @param newItem Whether the item has been newly created
	 * @param reader The item reader to help with parsing
	 * @throws PrismsRecordException If an error occurs parsing the item
	 */
	void parseContent(Object item, JSONObject json, boolean newItem, ItemReader reader)
		throws PrismsRecordException;

	/**
	 * Deletes an item from this data set
	 * 
	 * @param item The item to delete
	 * @param syncRecord The synchronization record to record the change under
	 * @throws PrismsRecordException If the item is unrecognized or if an error occurs deleting the
	 *         item
	 */
	void delete(Object item, SyncRecord syncRecord) throws PrismsRecordException;

	/**
	 * Performs a change on this impl's data set
	 * 
	 * @param change The change to perform
	 * @param currentValue The current value after the change (the corresponding value to
	 *        {@link ChangeRecord#previousValue})
	 * @throws PrismsRecordException If an error occurs performing the change
	 */
	void doChange(ChangeRecord change, Object currentValue) throws PrismsRecordException;

	/**
	 * @param change The change to get the field value of
	 * @return The current value of the field referred to by the change
	 * @throws PrismsRecordException If an error occurs getting the data
	 */
	Object getCurrentValue(ChangeRecord change) throws PrismsRecordException;
}
