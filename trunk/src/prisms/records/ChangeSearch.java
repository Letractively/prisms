/*
 * ChangeSearch.java Created Feb 22, 2011 by Andrew Butler, PSL
 */
package prisms.records;

import prisms.arch.ds.User;

/** Searches that can be performed on a {@link RecordKeeper} */
public abstract class ChangeSearch extends prisms.util.Search
{
	/** The available types of searches */
	public static enum ChangeSearchType implements SearchType
	{
		/** Search on the ID value of changes */
		id() {
			public IDRange create(String search)
			{
				throw new IllegalArgumentException("Cannot create an ID range search like this");
			}
		},
		/** Search on the major subject value of changes */
		majorSubjectRange() {
			public MajorSubjectRange create(String search)
			{
				throw new IllegalArgumentException(
					"Cannot create a major subject range search like this");
			}
		},
		/** Search for changes on a particular subject center */
		subjectCenter() {
			public SubjectCenterSearch create(String search)
			{
				throw new IllegalArgumentException(
					"Cannot create a subject center search like this");
			}
		},
		/** Searches on change time */
		time() {
			public ChangeTimeSearch create(String search)
			{
				return new ChangeTimeSearch(search);
			}
		},
		/** Searches on the user that caused changes */
		user() {
			public ChangeUserSearch create(String search)
			{
				throw new IllegalArgumentException("Cannot create a user search like this");
			}
		},
		/** Searches on the subject type of changes */
		subjectType() {
			public SubjectTypeSearch create(String search)
			{
				throw new IllegalArgumentException("Cannot create a subject type search like this");
			}
		},
		/** Searches on the change type of changes */
		changeType() {
			public ChangeTypeSearch create(String search)
			{
				throw new IllegalArgumentException("Cannot create a change type search like this");
			}
		},
		/** Searches on the additivity field of changes */
		add() {
			public AdditivitySearch create(String search)
			{
				throw new IllegalArgumentException("Cannot create an additivity search like this");
			}
		},
		/** Searches on the major subject, minor subject, data1, or data2 fields of changes */
		field() {
			public ChangeFieldSearch create(String search)
			{
				throw new IllegalArgumentException("Cannot create a field search like this");
			}
		},
		/** Searches on the major subject, minor subject, data1, or data2 fields of changes */
		syncRecord() {
			public ChangeFieldSearch create(String search)
			{
				throw new IllegalArgumentException("Cannot create a sync record search like this");
			}
		},
		/**
		 * Search that specifies whether to search for non-local-only changes (default) or for
		 * local-only changes or both
		 */
		localOnly() {
			public LocalOnlySearch create(String search)
			{
				throw new IllegalArgumentException("Cannot create a local-only search like this");
			}
		};

		/** The headers that prefix a search of this type */
		public final java.util.List<String> headers;

		ChangeSearchType(String... _headers)
		{
			java.util.ArrayList<String> heads = new java.util.ArrayList<String>();
			for(String header : _headers)
				heads.add(header.toLowerCase());
			headers = java.util.Collections.unmodifiableList(heads);
		}

		public String [] getHeaders()
		{
			return headers.toArray(new String [headers.size()]);
		}

		/**
		 * Checks the given search string against all this type's headers and returns the index of
		 * the header prefix used in the search string
		 * 
		 * @param srch The search string
		 * @return The header index used in the search string, or -1 if none of this type's headers
		 *         are prefixed to the search string
		 */
		protected int headerIndex(String srch)
		{
			srch = srch.trim();
			for(int h = 0; h < headers.size(); h++)
			{
				boolean hasHeader = true;
				for(int c = 0; c < headers.get(h).length(); c++)
				{
					char ch = srch.charAt(c);
					if(Character.isUpperCase(ch))
						ch = (char) (ch - 'A' + 'a');
					if(ch != headers.get(h).charAt(c))
					{
						hasHeader = false;
						break;
					}
				}
				if(hasHeader)
					return h;
			}
			return -1;
		}

		/**
		 * Cleans a search query, returning only the string to search for
		 * 
		 * @param srch The search query
		 * @return The cleaned search string
		 */
		protected String clean(String srch)
		{
			srch = srch.trim();
			for(String header : headers)
			{
				if(srch.length() < header.length())
					continue;
				boolean hasHeader = true;
				for(int c = 0; c < header.length(); c++)
				{
					char ch = srch.charAt(c);
					if(Character.isUpperCase(ch))
						ch = (char) (ch - 'A' + 'a');
					if(ch != header.charAt(c))
					{
						hasHeader = false;
						break;
					}
				}
				if(hasHeader)
				{
					srch = srch.substring(header.length());
					srch = srch.trim();
					break;
				}
			}
			if((srch.startsWith("\"") && srch.endsWith("\""))
				|| (srch.startsWith("'") && srch.endsWith("'")))
				srch = srch.substring(1, srch.length() - 1);
			return srch;
		}
	}

	/** A search for a range of change IDs */
	public static class IDRange extends ChangeSearch
	{
		/** The type of this search */
		public static final ChangeSearchType type = ChangeSearchType.id;

		private Long theMinID;

		private Long theMaxID;

		/**
		 * @param minID The minimum change ID to search for. May be null for a prepared search.
		 * @param maxID The maximum change ID to search for. May be null for a prepared search.
		 */
		public IDRange(Long minID, Long maxID)
		{
			theMinID = minID;
			theMaxID = maxID;
		}

		/** @return The minimum change ID to search for. May be null for a prepared search. */
		public Long getMinID()
		{
			return theMinID;
		}

		/** @return The maximum change ID to search for. May be null for a prepared search. */
		public Long getMaxID()
		{
			return theMaxID;
		}

		@Override
		public ChangeSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return (theMinID == null ? "?" : "" + theMinID) + ">=id<="
				+ (theMaxID == null ? "?" : "" + theMaxID);
		}
	}

	/** A search for a range of major subject IDs */
	public static class MajorSubjectRange extends ChangeSearch
	{
		/** The type of this search */
		public static final ChangeSearchType type = ChangeSearchType.majorSubjectRange;

		private Long theMinID;

		private Long theMaxID;

		/**
		 * @param minID The minimum major subject to search for. May be null for a prepared search.
		 * @param maxID The maximum major subject to search for. May be null for a prepared search.
		 */
		public MajorSubjectRange(Long minID, Long maxID)
		{
			theMinID = minID;
			theMaxID = maxID;
		}

		/** @return The minimum major subject to search for. May be null for a prepared search. */
		public Long getMinID()
		{
			return theMinID;
		}

		/** @return The maximum major subject to search for. May be null for a prepared search. */
		public Long getMaxID()
		{
			return theMaxID;
		}

		@Override
		public ChangeSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return (theMinID == null ? "?" : "" + theMinID) + "<=Major Subject>="
				+ (theMaxID == null ? "?" : "" + theMaxID);
		}
	}

	/** A search for changes to data created by a particular center */
	public static class SubjectCenterSearch extends ChangeSearch
	{
		/** The type of this search */
		public static final ChangeSearchType type = ChangeSearchType.subjectCenter;

		private Integer theSubjectCenter;

		/**
		 * @param sc The ID of the center to search for changes to. May be null for a prepared
		 *        search.
		 */
		public SubjectCenterSearch(Integer sc)
		{
			theSubjectCenter = sc;
		}

		/**
		 * @return The ID of the center to search for changes to. May be null for a prepared search.
		 */
		public Integer getSubjectCenter()
		{
			return theSubjectCenter;
		}

		@Override
		public ChangeSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return "Subject Center=" + (theSubjectCenter == null ? "?" : "" + theSubjectCenter);
		}
	}

	/** A search for changes by change time */
	public static class ChangeTimeSearch extends ChangeSearch
	{
		/** The type of this search */
		public static final ChangeSearchType type = ChangeSearchType.time;

		/** The operator determining how to use the search date */
		public final Operator operator;

		/** When the change occurred. May be null for a prepared search. */
		public final SearchDate changeTime;

		/** @param srch The entire search query (with or without header) */
		public ChangeTimeSearch(String srch)
		{
			srch = type.clean(srch);
			StringBuilder sb = new StringBuilder(srch);
			operator = Operator.parse(sb, srch);
			changeTime = SearchDate.parse(sb, srch);
		}

		/**
		 * @param op The operator to determine how to use the search time
		 * @param time The change time to search against. May be null for a prepared search.
		 */
		public ChangeTimeSearch(Operator op, SearchDate time)
		{
			operator = op;
			changeTime = time;
		}

		@Override
		public ChangeSearchType getType()
		{
			return type;
		}

		@Override
		public ChangeTimeSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public String toString()
		{
			return type.headers.get(0) + operator + changeTime;
		}
	}

	/** A search for changes by a particular user */
	public static class ChangeUserSearch extends ChangeSearch
	{
		/** The type of this search */
		public static final ChangeSearchType type = ChangeSearchType.user;

		private User theUser;

		/** @param user The user to search for changes by. May be null for a prepared search. */
		public ChangeUserSearch(User user)
		{
			theUser = user;
		}

		/** @return The user to search for changes by. May be null for a prepared search. */
		public User getUser()
		{
			return theUser;
		}

		@Override
		public ChangeSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return "user:" + theUser;
		}
	}

	/** A search for changes of a particular subject type */
	public static class SubjectTypeSearch extends ChangeSearch
	{
		/** The type of this search */
		public static final ChangeSearchType type = ChangeSearchType.subjectType;

		private SubjectType theSubjectType;

		/** @param subjectType the subject type to search for. May be null for a prepared search. */
		public SubjectTypeSearch(SubjectType subjectType)
		{
			theSubjectType = subjectType;
		}

		/** @return subjectType the subject type to search for. May be null for a prepared search. */
		public SubjectType getSubjectType()
		{
			return theSubjectType;
		}

		@Override
		public ChangeSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return "subjectType:" + theSubjectType;
		}
	}

	/** A search for changes of a particular change type */
	public static class ChangeTypeSearch extends ChangeSearch
	{
		/** The type of this search */
		public static final ChangeSearchType type = ChangeSearchType.changeType;

		private ChangeType theChangeType;

		private boolean isSpecified;

		/**
		 * @param changeType The change type to search for. May be null for a prepared search.
		 * @param specified Whether a null value for the changeType parameter means a search for a
		 *        null change type or a parameter in a prepared search
		 */
		public ChangeTypeSearch(ChangeType changeType, boolean specified)
		{
			theChangeType = changeType;
			isSpecified = specified;
		}

		/** @return The change type to search for. May be null for a prepared search. */
		public ChangeType getChangeType()
		{
			return theChangeType;
		}

		/**
		 * @return Whether a null value in this search's change type means a search for a null
		 *         change type or a parameter in a prepared search
		 */
		public boolean isSpecified()
		{
			return isSpecified;
		}

		@Override
		public ChangeSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return "changeType:" + theChangeType;
		}
	}

	/** A search for changes with a particular additivity */
	public static class AdditivitySearch extends ChangeSearch
	{
		/** The type of this search */
		public static final ChangeSearchType type = ChangeSearchType.add;

		private Integer theAddivitity;

		/** @param add The additivity to search for. May be null for a prepared search. */
		public AdditivitySearch(Integer add)
		{
			if(add != null && (add.intValue() < -1 || add.intValue() > 1))
				throw new IllegalArgumentException("Illegal additivity value--must be -1, 0, or 1");
			theAddivitity = add;
		}

		/** @return The additivity to search for. May be null for a prepared search */
		public Integer getAdditivity()
		{
			return theAddivitity;
		}

		@Override
		public ChangeSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return "additivity:" + (theAddivitity == null ? "?" : "" + theAddivitity);
		}
	}

	/** A search for changes where one of the data fields matches a certain value */
	public static class ChangeFieldSearch extends ChangeSearch
	{
		/** The type of this search */
		public static final ChangeSearchType type = ChangeSearchType.field;

		/** Possible field types to search against */
		public static enum FieldType
		{
			/** Search against the major subject */
			major,
			/** Search against the minor subject */
			minor,
			/** Search against the first metadata */
			data1,
			/** Search against the second metadata */
			data2;
		}

		private FieldType theFieldType;

		private boolean isFieldIDSpecified;

		private Long theFieldID;

		/**
		 * @param fieldType The field type to search against
		 * @param fieldID The ID to search for in the field. May be null for a prepared search or to
		 *        specify a null value.
		 * @param isSpecified Whether a null value for <code>fieldID</code> means a prepared search
		 *        parameter or a null value.
		 */
		public ChangeFieldSearch(FieldType fieldType, Long fieldID, boolean isSpecified)
		{
			theFieldType = fieldType;
			theFieldID = fieldID;
			isFieldIDSpecified = fieldID != null || isSpecified;
		}

		/** @return The field type to search against */
		public FieldType getFieldType()
		{
			return theFieldType;
		}

		/**
		 * @return Whether the field ID is specified. Will be false if the ID is to be replaced by a
		 *         parameter in a prepared search.
		 */
		public boolean isFieldIDSpecified()
		{
			return isFieldIDSpecified;
		}

		/**
		 * @return The ID to search for in the field. May be null for a prepared search or to
		 *         specify a null value.
		 */
		public Long getFieldID()
		{
			return theFieldID;
		}

		@Override
		public ChangeSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			return theFieldType + ":" + theFieldID;
		}
	}

	/** Allows searching for change records by their association with a synchronization record */
	public static class SyncRecordSearch extends ChangeSearch
	{
		/** The type of this search */
		public static ChangeSearchType type = ChangeSearchType.syncRecord;

		private boolean isSyncRecordSet;

		private Integer theSyncRecordID;

		private Operator theTimeOp;

		private SearchDate theTime;

		private Boolean theSyncError;

		private boolean isChangeErrorSet;

		private Boolean theChangeError;

		private boolean isImportSet;

		private Boolean isImport;

		private SyncRecordSearch()
		{
		}

		/**
		 * Creates a search for change records associated with a particular sync record
		 * 
		 * @param syncRecordID The ID of the sync record to get changes of. May be null for a
		 *        prepared search.
		 */
		public SyncRecordSearch(Integer syncRecordID)
		{
			isSyncRecordSet = true;
			theSyncRecordID = syncRecordID;
		}

		/**
		 * Creates a search for change records associated with sync records whose sync time matches
		 * matches some constraint
		 * 
		 * @param timeOp The operator to search on
		 * @param time The time to search on. May be null for a prepared search.
		 */
		public SyncRecordSearch(Operator timeOp, SearchDate time)
		{
			theTimeOp = timeOp;
			theTime = time;
		}

		/**
		 * Creates a search for change records associated with sync records with a particular error
		 * status
		 * 
		 * @param syncError Whether to search for changes on sync records that have errored out or
		 *        for sync records that were successful
		 * @return The search
		 */
		public static SyncRecordSearch forSyncError(boolean syncError)
		{
			SyncRecordSearch ret = new SyncRecordSearch();
			ret.theSyncError = Boolean.valueOf(syncError);
			return ret;
		}

		/**
		 * Creates a search for change records imported or exported with or without error
		 * 
		 * @param changeError Whether to search for changes that were imported or exported with an
		 *        error or successfully
		 * @return The search
		 */
		public static SyncRecordSearch forChangeError(Boolean changeError)
		{
			SyncRecordSearch ret = new SyncRecordSearch();
			ret.isChangeErrorSet = true;
			ret.theChangeError = changeError;
			return ret;
		}

		/**
		 * Creates a search for change records associated with imports or exports
		 * 
		 * @param _isImport Whether to search for imported or exported changes. May be null for a
		 *        prepared search.
		 * @return The search
		 */
		public static SyncRecordSearch forImport(Boolean _isImport)
		{
			SyncRecordSearch ret = new SyncRecordSearch();
			ret.isImportSet = true;
			ret.isImport = _isImport;
			return ret;
		}

		/** @return Whether the sync record is specified on this search */
		public boolean isSyncRecordSet()
		{
			return isSyncRecordSet;
		}

		/**
		 * @return The ID of the sync record to search for. May be null if
		 *         {@link #isSyncRecordSet()} is false or if this is for a prepared search
		 */
		public Integer getSyncRecordID()
		{
			return theSyncRecordID;
		}

		/**
		 * @return The operator to search by sync time. Will be null if this is not a sync time
		 *         search.
		 */
		public Operator getTimeOp()
		{
			return theTimeOp;
		}

		/**
		 * @return The time to search against sync time. May be null if this is not a sync time
		 *         search or if this is for a prepared search.
		 */
		public SearchDate getTime()
		{
			return theTime;
		}

		/**
		 * @return Whether this search is for sync records with errors or not. Will be null if this
		 *         is not a sync error search
		 */
		public Boolean getSyncError()
		{
			return theSyncError;
		}

		/** @return Whether this is a change error search */
		public boolean isChangeErrorSet()
		{
			return isChangeErrorSet;
		}

		/**
		 * @return Whether this search searches for changes imported/exported with error or
		 *         successfully. May be null if this is not a change error search or if this is for
		 *         a prepares search.
		 */
		public Boolean getChangeError()
		{
			return theChangeError;
		}

		/** @return Whether this is a sync import search */
		public boolean isSyncImportSet()
		{
			return isImportSet;
		}

		/**
		 * @return Whether this search searches for imports or exports. May be null if this is not a
		 *         sync import search or if this is for a prepared search.
		 */
		public Boolean isSyncImport()
		{
			return isImport;
		}

		@Override
		public ChangeSearchType getType()
		{
			return type;
		}

		@Override
		public String toString()
		{
			if(isSyncRecordSet)
				return "Sync Record " + (theSyncRecordID == null ? "?" : "" + theSyncRecordID);
			else if(theTimeOp != null)
				return "Sync Record Time " + theTimeOp + " " + theTime;
			else if(theSyncError != null)
				return "Sync Record Error " + theSyncError;
			else if(isChangeErrorSet)
				return "Change Error " + (theChangeError == null ? "?" : "" + theChangeError);
			else if(isImportSet)
				return "Sync Record Import " + (isImport == null ? "?" : "" + isImport);
			else
				throw new IllegalStateException("Unrecognized sync record search type");
		}
	}

	/**
	 * The default for a record keeper search is to exclude local-only changes. Including this type
	 * of search in a query allows the calling code to customize this behavior.
	 */
	public static class LocalOnlySearch extends ChangeSearch
	{
		/** The type of this search */
		public static final ChangeSearchType TYPE = ChangeSearchType.localOnly;

		private Boolean theLocalOnly;

		/**
		 * @param localOnly Whether to search for local-only changes exclusively, to exclude
		 *        local-only changes, or to include both
		 */
		public LocalOnlySearch(Boolean localOnly)
		{
			theLocalOnly = localOnly;
		}

		/**
		 * @return Whether this search searches for local-only changes, excludes them, or includes
		 *         both
		 */
		public Boolean getLocalOnly()
		{
			return theLocalOnly;
		}

		@Override
		public ChangeSearchType getType()
		{
			return TYPE;
		}

		@Override
		public String toString()
		{
			return "localOnly: " + (theLocalOnly == null ? "both" : "" + theLocalOnly);
		}
	}

	@Override
	public abstract ChangeSearchType getType();
}
