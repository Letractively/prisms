/*
 * MemoryRecordKeeper.java Created Aug 3, 2010 by Andrew Butler, PSL
 */
package prisms.records;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;

import prisms.util.*;
import prisms.util.MemPreparedSearch.MatchState;

/**
 * Keeps changes that have not yet been synchronized by all centers. This record keeper is ONLY
 * meant for synchronization purposes, as it purges changes and synchronization records as soon as
 * they are not needed for synchronization anymore.
 */
public class MemoryRecordKeeper implements RecordKeeper
{
	/** Gives this record keeper access to the ID of an object */
	public interface IDGetter
	{
		/**
		 * @param item The item to get the ID of
		 * @return The ID of the item
		 */
		long getID(Object item);

		/**
		 * Gets all domains that qualify as change history for the given value's type
		 * 
		 * @param value The value to get the history of
		 * @return All domains that record history of the type of the value
		 * @throws PrismsRecordException If an error occurs getting the data
		 */
		SubjectType [] getHistoryDomains(Object value) throws PrismsRecordException;
	}

	static class MemChangeState implements MemPreparedSearch.MatchState
	{
		ArrayList<SyncRecordHolder> theSyncRecords;

		MemChangeState(ArrayList<SyncRecordHolder> syncRecords)
		{
			theSyncRecords = (ArrayList<SyncRecordHolder>) syncRecords.clone();
		}

		@Override
		public MatchState clone()
		{
			MemChangeState ret;
			try
			{
				ret = (MemChangeState) super.clone();
			} catch(CloneNotSupportedException e)
			{
				throw new IllegalStateException(e.getMessage(), e);
			}
			ret.theSyncRecords = (ArrayList<SyncRecordHolder>) ret.theSyncRecords.clone();
			return ret;
		}

		@Override
		public int hashCode()
		{
			assert false : "hashCode not designed";
			return 42; // any arbitrary constant will do
		}

		@Override
		public boolean equals(Object o)
		{
			if(!(o instanceof MemChangeState))
				return false;
			return ((MemChangeState) o).theSyncRecords.equals(theSyncRecords);
		}
	}

	/**
	 * The type of prepared search used by this record keeper when
	 * {@link MemoryRecordKeeper#prepare(Search, Sorter)} is called
	 */
	public class MemChangeSearch extends MemPreparedSearch<ChangeRecord, ChangeSearch, ChangeField>
	{
		/**
		 * @param search The search to prepare
		 * @param sorter The sorter to sort the search results afterward
		 */
		public MemChangeSearch(Search search, Sorter<ChangeField> sorter)
		{
			super(search, sorter, ChangeSearch.class);
		}

		@Override
		protected MatchState createState()
		{
			return new MemChangeState(theSyncRecords);
		}

		@Override
		protected BitSet matches(ChangeRecord [] items, BitSet filter, ChangeSearch search,
			MatchState state, final Object [] params)
		{
			BitSet ret = new BitSet();
			int p = 0;
			switch(search.getType())
			{
			case id:
				ChangeSearch.IDRange ids = (ChangeSearch.IDRange) search;
				long minID;
				if(ids.getMinID() == null)
				{
					if(params == null)
						throw new IllegalArgumentException("Missing minID for ID range search");
					if(!(params[p] instanceof Long || params[p] instanceof Integer))
						throw new IllegalArgumentException(
							"Parameter of type Long expected for minID parameter of ID range"
								+ " search, but received " + params[0].getClass().getName());
					minID = ((Number) params[p]).longValue();
					p++;
				}
				else
					minID = ids.getMinID().longValue();
				long maxID;
				if(ids.getMaxID() == null)
				{
					if(params == null)
						throw new IllegalArgumentException("Missing maxID for ID range search");
					if(!(params[p] instanceof Long || params[p] instanceof Integer))
						throw new IllegalArgumentException(
							"Parameter of type Long expected for maxID parameter of ID range"
								+ " search, but received " + params[0].getClass().getName());
					maxID = ((Number) params[p]).longValue();
				}
				else
					maxID = ids.getMaxID().longValue();
				for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
					ret.set(i, items[i].id >= minID && items[i].id <= maxID);
				break;
			case majorSubjectRange:
				ChangeSearch.MajorSubjectRange msr = (ChangeSearch.MajorSubjectRange) search;
				if(msr.getMinID() == null)
				{
					if(params == null)
						throw new IllegalArgumentException(
							"Missing minID for major subject range search");
					if(!(params[p] instanceof Long || params[p] instanceof Integer))
						throw new IllegalArgumentException(
							"Parameter of type Long expected for minID parameter of major subject"
								+ " range search, but received " + params[0].getClass().getName());
					minID = ((Number) params[p]).longValue();
					p++;
				}
				else
					minID = msr.getMinID().longValue();
				if(msr.getMaxID() == null)
				{
					if(params == null)
						throw new IllegalArgumentException(
							"Missing maxID for major subject range search");
					if(!(params[p] instanceof Long || params[p] instanceof Integer))
						throw new IllegalArgumentException(
							"Parameter of type Long expected for maxID parameter of major subject"
								+ " range search, but received " + params[0].getClass().getName());
					maxID = ((Number) params[p]).longValue();
				}
				else
					maxID = msr.getMaxID().longValue();
				for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
				{
					if(items[i] instanceof ChangeRecordError)
					{
						ChangeRecordError cre = (ChangeRecordError) items[i];
						ret.set(i, cre.getMajorSubjectID() >= minID
							&& cre.getMajorSubjectID() <= maxID);
					}
					else
						ret.set(i, getID(items[i].majorSubject) >= minID
							&& getID(items[i].majorSubject) <= maxID);
				}
				break;
			case subjectCenter:
				ChangeSearch.SubjectCenterSearch scs = (ChangeSearch.SubjectCenterSearch) search;
				int sc;
				if(scs.getSubjectCenter() == null)
				{
					if(params == null)
						throw new IllegalArgumentException(
							"Missing subject center for subject center search");
					if(!(params[0] instanceof Integer))
						throw new IllegalArgumentException(
							"Parameter of type Long expected for subjectCenter parameter of subject"
								+ " center search, but received " + params[0].getClass().getName());
					sc = ((Number) params[0]).intValue();
				}
				else
					sc = scs.getSubjectCenter().intValue();
				for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
				{
					if(items[i] instanceof ChangeRecordError)
						ret.set(i, prisms.arch.ds.IDGenerator
							.getCenterID(((ChangeRecordError) items[i]).getMajorSubjectID()) == sc);
					else
						ret.set(
							i,
							prisms.arch.ds.IDGenerator.getCenterID(getID(items[i].majorSubject)) == sc);
				}
				break;
			case time:
				ChangeSearch.ChangeTimeSearch timeSearch = (ChangeSearch.ChangeTimeSearch) search;
				Search.SearchDate time;
				if(timeSearch.changeTime == null)
				{
					if(params == null)
						throw new IllegalArgumentException("Missing time for change time search");
					if(params[0] instanceof Long)
						time = new Search.SearchDate(((Number) params[0]).longValue());
					else if(params[0] instanceof java.util.Date)
						time = new Search.SearchDate(((java.util.Date) params[0]).getTime());
					else
						throw new IllegalArgumentException("Parameter of type date expected for"
							+ " time parameter of change time search, but received "
							+ params[0].getClass().getName());
				}
				else
					time = timeSearch.changeTime;

				for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
					ret.set(i, time.matches(timeSearch.operator, items[i].time));
				break;
			case user:
				ChangeSearch.ChangeUserSearch userSearch = (ChangeSearch.ChangeUserSearch) search;
				RecordUser user;
				if(userSearch.getUser() != null)
					user = userSearch.getUser();
				else if(params == null)
					throw new IllegalArgumentException("Missing user for change user search");
				else if(params[0] instanceof RecordUser)
					user = (RecordUser) params[0];
				else if(params[0] instanceof Long)
				{
					long userID = ((Number) params[0]).longValue();
					for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
						ret.set(i, items[i].user.getID() == userID);
					break;
				}
				else
					throw new IllegalArgumentException("Parameter of type RecordUser or Long"
						+ " expected for user parameter of change user search, but received "
						+ params[0].getClass().getName());

				for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
					ret.set(i, user.equals(items[i].user));
				break;
			case subjectType:
				ChangeSearch.SubjectTypeSearch stSearch = (ChangeSearch.SubjectTypeSearch) search;
				Object subjectType;
				if(stSearch.getSubjectType() != null)
					subjectType = stSearch.getSubjectType();
				else if(params == null)
					throw new IllegalArgumentException(
						"Missing subject type for subject type search");
				else if(params[0] instanceof SubjectType || params[0] instanceof String)
					subjectType = params[0];
				else
					throw new IllegalArgumentException("Parameter of type subject type or"
						+ " string expected for subject type parameter of subject type search,"
						+ " but received " + params[0].getClass().getName());

				if(subjectType instanceof String)
				{
					for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
					{
						if(items[i] instanceof ChangeRecordError)
							ret.set(i,
								subjectType.equals(((ChangeRecordError) items[i]).getSubjectType()));
						else
							ret.set(i, subjectType.equals(items[i].type.subjectType.name()));
					}
				}
				else
				{
					SubjectType st = (SubjectType) subjectType;
					for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
					{
						if(items[i] instanceof ChangeRecordError)
							ret.set(i,
								st.name().equals(((ChangeRecordError) items[i]).getSubjectType()));
						else
							ret.set(i, st == items[i].type.subjectType);
					}
				}
				break;
			case changeType:
				ChangeSearch.ChangeTypeSearch ctSearch = (ChangeSearch.ChangeTypeSearch) search;
				Object changeType;
				if(ctSearch.isSpecified())
					changeType = ctSearch.getChangeType();
				else if(params == null)
					throw new IllegalArgumentException("Missing change type for change type search");
				else if(params[0] == null || params[0] instanceof ChangeType
					|| params[0] instanceof String)
					changeType = params[0];
				else
					throw new IllegalArgumentException("Parameter of type change type or"
						+ " string expected for change type parameter of change type search,"
						+ " but received " + params[0].getClass().getName());

				if(changeType == null)
				{
					for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
					{
						if(items[i] instanceof ChangeRecordError)
							ret.set(i, ((ChangeRecordError) items[i]).getSubjectType() == null);
						else
							ret.set(i, items[i].type.subjectType == null);
					}
				}
				else if(changeType instanceof String)
				{
					for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
					{
						if(items[i] instanceof ChangeRecordError)
							ret.set(i,
								changeType.equals(((ChangeRecordError) items[i]).getChangeType()));
						else
							ret.set(i, changeType.equals(items[i].type.changeType.name()));
					}
				}
				else
				{
					ChangeType ct = (ChangeType) changeType;
					for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
					{
						if(items[i] instanceof ChangeRecordError)
							ret.set(i,
								ct.name().equals(((ChangeRecordError) items[i]).getChangeType()));
						else
							ret.set(i, ct == items[i].type.changeType);
					}
				}
				break;
			case add:
				ChangeSearch.AdditivitySearch addSearch = (ChangeSearch.AdditivitySearch) search;
				int add;
				if(addSearch.getAdditivity() != null)
					add = addSearch.getAdditivity().intValue();
				else if(params == null)
					throw new IllegalArgumentException("Missing additivity for additivity search");
				else if(params[0] instanceof Integer)
					add = ((Integer) params[0]).intValue();
				else
					throw new IllegalArgumentException("Parameter of type int expected for"
						+ " additivity parameter of additivity search, but received "
						+ params[0].getClass().getName());
				if(add < -1 || add > 1)
					throw new IllegalArgumentException("Illegal additivity parameter for"
						+ " additivity search: " + add);
				for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
				{
					if(items[i] instanceof ChangeRecordError)
						ret.set(i, ((ChangeRecordError) items[i]).getAdditivity() == add);
					else
						ret.set(i, items[i].type.additivity == add);
				}
				break;
			case field:
				ChangeSearch.ChangeFieldSearch fieldSearch = (ChangeSearch.ChangeFieldSearch) search;
				Long fieldValue;
				if(fieldSearch.isFieldIDSpecified())
					fieldValue = fieldSearch.getFieldID();
				else if(params == null)
					throw new IllegalArgumentException("Missing field value for field search");
				else if(params[0] == null || params[0] instanceof Long)
					fieldValue = (Long) params[0];
				else
					throw new IllegalArgumentException("Parameter of type Long expected for"
						+ " field value parameter of field search, but received "
						+ params[0].getClass().getName());

				if(fieldValue == null)
				{
					for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
					{
						switch(fieldSearch.getFieldType())
						{
						case major:
							break;
						case minor:
							if(items[i] instanceof ChangeRecordError)
								ret.set(i, ((ChangeRecordError) items[i]).getMinorSubjectID() < 0);
							else
								ret.set(i, items[i].minorSubject == null);
							break;
						case data1:
							if(items[i] instanceof ChangeRecordError)
								ret.set(i, ((ChangeRecordError) items[i]).getData1ID() < 0);
							else
								ret.set(i, items[i].data1 == null);
							break;
						case data2:
							if(items[i] instanceof ChangeRecordError)
								ret.set(i, ((ChangeRecordError) items[i]).getData2ID() < 0);
							else
								ret.set(i, items[i].data2 == null);
							break;
						}
					}
				}
				else
				{
					long fv = fieldValue.longValue();
					for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
					{
						switch(fieldSearch.getFieldType())
						{
						case major:
							if(items[i] instanceof ChangeRecordError)
								ret.set(i, ((ChangeRecordError) items[i]).getMajorSubjectID() == fv);
							else
								ret.set(i, getID(items[i].majorSubject) == fv);
							break;
						case minor:
							if(items[i] instanceof ChangeRecordError)
							{
								ChangeRecordError cre = (ChangeRecordError) items[i];
								ret.set(i, cre.getMinorSubjectID() >= 0
									&& cre.getMinorSubjectID() == fv);
							}
							else
								ret.set(i, items[i].minorSubject != null
									&& getID(items[i].minorSubject) == fv);
							break;
						case data1:
							if(items[i] instanceof ChangeRecordError)
							{
								ChangeRecordError cre = (ChangeRecordError) items[i];
								ret.set(i, cre.getData1ID() >= 0 && cre.getData1ID() == fv);
							}
							else
								ret.set(i, items[i].data1 != null && getID(items[i].data1) == fv);
							break;
						case data2:
							if(items[i] instanceof ChangeRecordError)
							{
								ChangeRecordError cre = (ChangeRecordError) items[i];
								ret.set(i, cre.getData2ID() >= 0 && cre.getData2ID() == fv);
							}
							else
								ret.set(i, items[i].data2 != null && getID(items[i].data2) == fv);
							break;
						}
					}
				}
				break;
			case syncRecord:
				ChangeSearch.SyncRecordSearch syncRecordSearch = (ChangeSearch.SyncRecordSearch) search;
				MemChangeState mcs = (MemChangeState) state;
				if(syncRecordSearch.isSyncRecordSet())
				{
					long srID;
					if(syncRecordSearch.getSyncRecordID() != null)
						srID = syncRecordSearch.getSyncRecordID().intValue();
					else if(params == null)
						throw new IllegalArgumentException(
							"Missing sync record ID for sync record search");
					else if(params[0] instanceof Integer || params[0] instanceof Long)
						srID = ((Number) params[0]).intValue();
					else
						throw new IllegalArgumentException("Parameter of type int expected for"
							+ " sync record ID parameter of sync record search, but received "
							+ params[0].getClass().getName());

					for(int sr = 0; sr < mcs.theSyncRecords.size(); sr++)
					{
						SyncRecordHolder holder = mcs.theSyncRecords.get(sr);
						if(holder.theSyncRecord.getID() == srID)
							for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
							{
								if(!ret.get(i))
									ret.set(i, holder.theSuccessChanges.contains(items[i])
										|| holder.theErrorChanges.contains(items[i]));
							}
						else
						{
							mcs.theSyncRecords.remove(sr);
							sr--;
						}
					}
				}
				else if(syncRecordSearch.getTimeOp() != null)
				{
					if(syncRecordSearch.getTime() != null)
						time = syncRecordSearch.getTime();
					else if(params == null)
						throw new IllegalArgumentException(
							"Missing sync time for sync record search");
					else if(params[0] instanceof Long)
						time = new Search.SearchDate(((Long) params[0]).longValue());
					else if(params[0] instanceof java.util.Date)
						time = new Search.SearchDate(((java.util.Date) params[0]).getTime());
					else
						throw new IllegalArgumentException("Parameter of type Long or Date"
							+ " expected for sync time parameter of sync record search, but"
							+ " received " + params[0].getClass().getName());

					for(int sr = 0; sr < mcs.theSyncRecords.size(); sr++)
					{
						SyncRecordHolder holder = mcs.theSyncRecords.get(sr);
						if(time.matches(syncRecordSearch.getTimeOp(),
							holder.theSyncRecord.getSyncTime()))
							for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
							{
								if(!ret.get(i))
									ret.set(i, holder.theSuccessChanges.contains(items[i])
										|| holder.theErrorChanges.contains(items[i]));
							}
						else
						{
							mcs.theSyncRecords.remove(sr);
							sr--;
						}
					}
				}
				else if(syncRecordSearch.getSyncError() != null)
				{
					boolean error;
					if(syncRecordSearch.getSyncError() != null)
						error = syncRecordSearch.getSyncError().booleanValue();
					else if(params == null)
						throw new IllegalArgumentException(
							"Missing sync error for sync record search");
					else if(params[0] instanceof Boolean)
						error = ((Boolean) params[0]).booleanValue();
					else
						throw new IllegalArgumentException("Parameter of type Boolean"
							+ " expected for sync error parameter of sync record search, but"
							+ " received " + params[0].getClass().getName());

					for(int sr = 0; sr < mcs.theSyncRecords.size(); sr++)
					{
						SyncRecordHolder holder = mcs.theSyncRecords.get(sr);
						if((holder.theSyncRecord.getSyncError() != null) == error)
							for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
							{
								if(!ret.get(i))
									ret.set(i, holder.theSuccessChanges.contains(items[i])
										|| holder.theErrorChanges.contains(items[i]));
							}
						else
						{
							mcs.theSyncRecords.remove(sr);
							sr--;
						}
					}
				}
				else if(syncRecordSearch.isChangeErrorSet())
				{
					boolean error;
					if(syncRecordSearch.getChangeError() != null)
						error = syncRecordSearch.getChangeError().booleanValue();
					else if(params == null)
						throw new IllegalArgumentException(
							"Missing change error for sync record search");
					else if(params[0] instanceof Boolean)
						error = ((Boolean) params[0]).booleanValue();
					else
						throw new IllegalArgumentException("Parameter of type Boolean"
							+ " expected for change error parameter of sync record search, but"
							+ " received " + params[0].getClass().getName());

					for(int sr = 0; sr < mcs.theSyncRecords.size(); sr++)
					{
						SyncRecordHolder holder = mcs.theSyncRecords.get(sr);
						for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
						{
							if(ret.get(i))
								continue;
							if(error)
								ret.set(i, holder.theErrorChanges.contains(items[i]));
							else
								ret.set(i, holder.theSuccessChanges.contains(items[i]));
						}
					}
				}
				else if(syncRecordSearch.isSyncImportSet())
				{
					boolean isImport;
					if(syncRecordSearch.isSyncImport() != null)
						isImport = syncRecordSearch.isSyncImport().booleanValue();
					else if(params == null)
						throw new IllegalArgumentException(
							"Missing sync import for sync record search");
					else if(params[0] instanceof Boolean)
						isImport = ((Boolean) params[0]).booleanValue();
					else
						throw new IllegalArgumentException("Parameter of type Boolean"
							+ " expected for sync import parameter of sync record search, but"
							+ " received " + params[0].getClass().getName());

					for(int sr = 0; sr < mcs.theSyncRecords.size(); sr++)
					{
						SyncRecordHolder holder = mcs.theSyncRecords.get(sr);
						if(holder.theSyncRecord.isImport() == isImport)
							for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
							{
								if(!ret.get(i))
									ret.set(i, holder.theSuccessChanges.contains(items[i])
										|| holder.theErrorChanges.contains(items[i]));
							}
						else
						{
							mcs.theSyncRecords.remove(sr);
							sr--;
						}
					}

				}
				else
					throw new IllegalArgumentException("Unrecognized sync record search type: "
						+ syncRecordSearch);
				break;
			case localOnly:
				ChangeSearch.LocalOnlySearch localSearch = (ChangeSearch.LocalOnlySearch) search;
				if(localSearch.getLocalOnly() == null)
					return filter;
				for(int i = filter.nextSetBit(0); i >= 0; i = filter.nextSetBit(i + 1))
					ret.set(i, localSearch.getLocalOnly().booleanValue() == items[i].localOnly);
				break;
			}
			return ret;
		}

		@Override
		public int compare(ChangeRecord o1, ChangeRecord o2, ChangeField field)
		{
			return MemoryRecordKeeper.compare(o1, o2, field);
		}

		@Override
		protected void addParamTypes(ChangeSearch search, Collection<Class<?>> types)
		{
			RecordUtils.addParamTypes(search, types);
		}
	}

	/** Sorts change records based on a given sorter */
	public static class ChangeSorter implements java.util.Comparator<ChangeRecord>
	{
		private final Sorter<ChangeField> theSorter;

		/** @param sorter The sorter to use to sort changes */
		public ChangeSorter(Sorter<ChangeField> sorter)
		{
			if(sorter == null)
			{
				sorter = new Sorter<ChangeField>();
				sorter.addSort(ChangeField.CHANGE_TIME, false);
			}
			theSorter = sorter;
		}

		public int compare(ChangeRecord o1, ChangeRecord o2)
		{
			return MemoryRecordKeeper.compare(o1, o2, theSorter);
		}
	}

	/**
	 * Compares to change records based on a sorter
	 * 
	 * @param o1 One change record
	 * @param o2 The other change record
	 * @param sorter The sorter to use to compare the changes
	 * @return >0 If <code>o1</code> should appear before <code>o2</code> in a sorted list, -1 if
	 *         <code>o2</code> should appear first, or 0 if the two changes sort to the same order.
	 */
	public static int compare(ChangeRecord o1, ChangeRecord o2, Sorter<ChangeField> sorter)
	{
		for(int f = 0; f < sorter.getSortCount(); f++)
		{
			ChangeField field = sorter.getField(f);
			int ret = compare(o1, o2, field);
			if(ret == 0)
				continue;
			if(!sorter.isAscending(f))
				ret = -ret;
			return ret;
		}
		return 0;
	}

	/**
	 * Compares a single field of two change records
	 * 
	 * @param o1 The first change record
	 * @param o2 The second change record
	 * @param field The field to compare
	 * @return The comparison of the field between the two changes
	 */
	public static int compare(ChangeRecord o1, ChangeRecord o2, ChangeField field)
	{
		int ret = 0;
		switch(field)
		{
		case CHANGE_TIME:
			if(o1.time > o2.time)
				ret = 1;
			else if(o1.time < o2.time)
				ret = -1;
			break;
		case CHANGE_TYPE:
			if(o1.type.subjectType != o2.type.subjectType)
				ret = o1.type.subjectType.name().compareTo(o2.type.subjectType.name());
			else if(o1.type.changeType != o2.type.changeType)
			{
				if(o1.type.changeType == null)
					ret = 1;
				else if(o2.type.changeType == null)
					ret = -1;
				else
					ret = o1.type.changeType.name().compareTo(o2.type.changeType.name());
			}
			else if(o1.type.additivity != o2.type.additivity)
			{
				if(o1.type.additivity != 0 && o2.type.additivity != 0)
					ret = o1.type.additivity - o2.type.additivity;
				else if(o1.type.additivity != 0)
					ret = 1;
				else
					ret = -1;
			}
			break;
		case CHANGE_USER:
			if(o1.user.getID() > o2.user.getID())
				ret = 1;
			else if(o1.user.getID() < o2.user.getID())
				ret = -1;
			break;
		}
		return ret;
	}

	private static class SyncRecordHolder
	{
		SyncRecord theSyncRecord;

		ArrayList<ChangeRecord> theSuccessChanges;

		ArrayList<ChangeRecord> theErrorChanges;

		SyncRecordHolder(SyncRecord record)
		{
			theSyncRecord = record;
			theSuccessChanges = new ArrayList<ChangeRecord>();
			theErrorChanges = new ArrayList<ChangeRecord>();
		}
	}

	private final String theNamespace;

	private final int theCenterID;

	private int theSyncPriority;

	private final IDGetter theIDGetter;

	private java.util.concurrent.locks.ReentrantLock theLock;

	private ArrayList<PrismsCenter> theCenters;

	private ArrayList<ChangeRecord> theChanges;

	ArrayList<SyncRecordHolder> theSyncRecords;

	/** The last time this record keeper was checked for items that need to be purged */
	private long theLastPurge;

	private int theRetrySetting;

	private int theNextCenterID;

	private int theNextSyncRecordID;

	private int theNextChangeID;

	private java.util.HashMap<DualKey<Integer, Integer>, Long> theRecentPurges;

	/**
	 * Creates a record keeper with a random center ID
	 * 
	 * @param namespace The namespace for this record keeper
	 * @param getter The ID getter for this record keeper
	 */
	public MemoryRecordKeeper(String namespace, IDGetter getter)
	{
		this(namespace, getter, PrismsUtils.getRandomInt());
	}

	/**
	 * Creates a record keeper with a given ID
	 * 
	 * @param namespace The namespace for this record keeper
	 * @param centerID The center ID for this record keeper
	 * @param getter The ID getter for this record keeper
	 */
	public MemoryRecordKeeper(String namespace, IDGetter getter, int centerID)
	{
		theNamespace = namespace;
		theCenterID = centerID;
		theIDGetter = getter;
		theLock = new java.util.concurrent.locks.ReentrantLock();
		theCenters = new ArrayList<PrismsCenter>();
		theChanges = new ArrayList<ChangeRecord>();
		theSyncRecords = new ArrayList<SyncRecordHolder>();
		theLastPurge = System.currentTimeMillis();
		theRecentPurges = new java.util.HashMap<DualKey<Integer, Integer>, Long>();
		theRetrySetting = 2;
	}

	public String getNamespace()
	{
		return theNamespace;
	}

	public int getCenterID()
	{
		return theCenterID;
	}

	public int getLocalPriority()
	{
		return theSyncPriority;
	}

	/**
	 * @param priority The synchronization priority for this keeper's local center
	 * @see PrismsCenter#getPriority()
	 */
	public void setLocalPriority(int priority)
	{
		theSyncPriority = priority;
	}

	public PrismsCenter [] getCenters() throws PrismsRecordException
	{
		return theCenters.toArray(new PrismsCenter [theCenters.size()]);
	}

	public PrismsCenter getCenter(int id) throws PrismsRecordException
	{
		for(PrismsCenter c : theCenters)
			if(c.getID() == id)
				return c;
		return null;
	}

	public void putCenter(PrismsCenter center, RecordsTransaction trans)
		throws PrismsRecordException
	{
		theLock.lock();
		try
		{
			if(center.getID() < 0)
			{
				center.setID(theNextCenterID);
				theNextCenterID++;
			}
			for(PrismsCenter c : theCenters)
			{
				if(center.equals(c))
				{
					if(center != c)
					{
						c.setCenterID(center.getCenterID());
						c.setName(center.getName());
						c.setServerURL(center.getServerURL());
						c.setServerUserName(center.getServerUserName());
						c.setServerPassword(center.getServerPassword());
						c.setServerSyncFrequency(center.getServerSyncFrequency());
						c.setLastImport(center.getLastImport());
						c.setClientUser(center.getClientUser());
						c.setChangeSaveTime(center.getChangeSaveTime());
						c.setLastExport(center.getLastExport());
						purge();
					}
					return;
				}
			}
			theCenters.add(center);
		} finally
		{
			theLock.unlock();
		}
	}

	public void removeCenter(PrismsCenter center, RecordsTransaction trans)
		throws PrismsRecordException
	{
		for(int c = 0; c < theCenters.size(); c++)
		{
			if(theCenters.get(c).equals(center))
			{
				theLock.lock();
				try
				{
					theCenters.remove(c);
					java.util.Iterator<SyncRecordHolder> iter = theSyncRecords.iterator();
					while(iter.hasNext())
						if(iter.next().theSyncRecord.getCenter().equals(center))
							iter.remove();
					purge();
				} finally
				{
					theLock.unlock();
				}
				return;
			}
		}
		throw new prisms.records.PrismsRecordException("No such center " + center);
	}

	public int [] getAllCenterIDs() throws PrismsRecordException
	{
		int [] ret = new int [theRecentPurges.size()];
		int i = 0;
		java.util.Iterator<DualKey<Integer, Integer>> iter = theRecentPurges.keySet().iterator();
		while(iter.hasNext())
			ret[i++] = iter.next().getKey1().intValue();
		for(ChangeRecord change : theChanges)
		{
			int centerID = RecordUtils.getCenterID(change.id);
			boolean contained = false;
			for(i = 0; i < ret.length; i++)
				if(ret[i] == centerID)
				{
					contained = true;
					break;
				}
			if(contained)
				continue;
			ret = (int []) ArrayUtils.addP(ret, Integer.valueOf(centerID));
		}
		return ret;
	}

	public long getLatestChange(int centerID, int subjectCenter)
	{
		Long purged = theRecentPurges.get(new DualKey<Integer, Integer>(Integer.valueOf(centerID),
			Integer.valueOf(subjectCenter)));
		for(int c = theChanges.size() - 1; c >= 0; c--)
		{
			if(purged != null && purged.longValue() > theChanges.get(c).time)
				break;
			if(RecordUtils.getCenterID(theChanges.get(c).id) != centerID)
				continue;
			if(getSubjectCenter(theChanges.get(c)) != subjectCenter)
				continue;
			return theChanges.get(c).time;
		}
		return purged == null ? -1 : purged.longValue();
	}

	public int getSubjectCenter(long changeID)
	{
		for(ChangeRecord change : theChanges)
			if(change.id == changeID)
				return getSubjectCenter(change);
		return -1;
	}

	public long [] search(Search search, Sorter<ChangeField> sorter) throws PrismsRecordException
	{
		PreparedSearch<ChangeField> prepared = prepare(search, sorter);
		long [] ret = execute(prepared, new Object [0]);
		destroy(prepared);
		return ret;
	}

	public PreparedSearch<ChangeField> prepare(Search search, Sorter<ChangeField> sorter)
		throws PrismsRecordException
	{
		return new MemChangeSearch(search, sorter);
	}

	public long [] execute(PreparedSearch<ChangeField> search, Object... params)
		throws PrismsRecordException
	{
		LongList ret = new LongList();
		for(ChangeRecord record : ((MemChangeSearch) search).execute(
			theChanges.toArray(new ChangeRecord [theChanges.size()]), params))
			ret.add(record.id);
		return ret.toArray();
	}

	public void destroy(PreparedSearch<ChangeField> search) throws PrismsRecordException
	{
	}

	public ChangeRecord [] getItems(long... ids) throws PrismsRecordException
	{
		LongList idList = new LongList(ids);
		java.util.ArrayList<ChangeRecord> ret = new java.util.ArrayList<ChangeRecord>();
		for(ChangeRecord change : theChanges)
			if(idList.contains(change.id))
				ret.add(change);
		return ArrayUtils.adjust(ret.toArray(new ChangeRecord [ret.size()]),
			idList.toObjectArray(), new ArrayUtils.DifferenceListener<ChangeRecord, Long>()
			{
				public boolean identity(ChangeRecord o1, Long o2)
				{
					return o1.id == o2.longValue();
				}

				public ChangeRecord added(Long o, int mIdx, int retIdx)
				{
					return null;
				}

				public ChangeRecord removed(ChangeRecord o, int oIdx, int incMod, int retIdx)
				{
					return null;
				}

				public ChangeRecord set(ChangeRecord o1, int idx1, int incMod, Long o2, int idx2,
					int retIdx)
				{
					return o1;
				}
			});
	}

	public Search getHistorySearch(Object historyItem) throws PrismsRecordException
	{
		return RecordUtils.getHistorySearch(historyItem, theIDGetter);
	}

	public Search getSuccessorSearch(ChangeRecord change) throws PrismsRecordException
	{
		return RecordUtils.getSuccessorSearch(change, theIDGetter);
	}

	long getID(Object item)
	{
		if(item instanceof PrismsCenter)
			return ((PrismsCenter) item).getID();
		else if(item instanceof AutoPurger)
			return 0;
		else
			return theIDGetter.getID(item);
	}

	private int getSubjectCenter(ChangeRecord change)
	{
		if(change instanceof ChangeRecordError)
		{
			ChangeRecordError error = (ChangeRecordError) change;
			for(PrismsChange pc : PrismsChange.values())
				if(pc.name().equals(error.getSubjectType()))
					return theCenterID;
			return RecordUtils.getCenterID(error.getMajorSubjectID());
		}
		else
		{
			Object item = change.majorSubject;
			if(item instanceof PrismsCenter || item instanceof AutoPurger)
				return theCenterID;
			return RecordUtils.getCenterID(theIDGetter.getID(item));
		}
	}

	SubjectType [] getHistoryDomains(Object item) throws PrismsRecordException
	{
		if(item instanceof PrismsCenter)
			return new SubjectType [] {PrismsChange.center};
		if(item instanceof AutoPurger)
			return new SubjectType [] {PrismsChange.autoPurge};
		else
			return theIDGetter.getHistoryDomains(item);
	}

	public void setLatestChange(int centerID, int subjectCenter, long time)
	{
		Long oldTime = theRecentPurges.get(new DualKey<Integer, Integer>(Integer.valueOf(centerID),
			Integer.valueOf(subjectCenter)));
		if(oldTime != null && oldTime.longValue() >= time)
			return;
		for(int i = theChanges.size() - 1; i >= 0; i--)
		{
			ChangeRecord change = theChanges.get(i);
			if(change.time <= time)
				break;
			if(RecordUtils.getCenterID(change.id) == centerID
				&& getSubjectCenter(change) == subjectCenter)
				return;
		}
		theRecentPurges
			.put(
				new DualKey<Integer, Integer>(Integer.valueOf(centerID), Integer
					.valueOf(subjectCenter)), Long.valueOf(time));
	}

	public long [] sortChangeIDs(long [] changeIDs, boolean ascending)
	{
		Long [] cids = new Long [changeIDs.length];
		for(int i = 0; i < cids.length; i++)
			cids[i] = Long.valueOf(changeIDs[i]);
		cids = ArrayUtils.adjust(cids, theChanges.toArray(new ChangeRecord [theChanges.size()]),
			new ArrayUtils.DifferenceListener<Long, ChangeRecord>()
			{
				public boolean identity(Long o1, ChangeRecord o2)
				{
					return o1.longValue() == o2.id;
				}

				public Long added(ChangeRecord o, int mIdx, int retIdx)
				{
					return null;
				}

				public Long removed(Long o, int oIdx, int incMod, int retIdx)
				{
					return null;
				}

				public Long set(Long o1, int idx1, int incMod, ChangeRecord o2, int idx2, int retIdx)
				{
					return o1;
				}
			});
		for(int i = 0; i < cids.length; i++)
			changeIDs[i] = cids[i].longValue();
		return changeIDs;
	}

	public boolean hasChange(long changeID)
	{
		for(ChangeRecord ch : theChanges)
			if(ch.id == changeID)
				return true;
		return false;
	}

	public boolean hasSuccessfulChange(long changeID)
	{
		if(RecordUtils.getCenterID(changeID) == theCenterID)
			return true;
		for(SyncRecordHolder sync : theSyncRecords)
			for(ChangeRecord change : sync.theSuccessChanges)
				if(change.id == changeID)
					return true;
		return false;
	}

	public SyncRecord [] getSyncRecords(PrismsCenter center, Boolean isImport)
	{
		java.util.ArrayList<SyncRecord> ret = new java.util.ArrayList<SyncRecord>();
		for(SyncRecordHolder holder : theSyncRecords)
			if(holder.theSyncRecord.getCenter().equals(center))
			{
				if(isImport != null && holder.theSyncRecord.isImport() != isImport.booleanValue())
					continue;
				ret.add(holder.theSyncRecord);
			}
		return ret.toArray(new SyncRecord [ret.size()]);
	}

	public void putSyncRecord(SyncRecord record)
	{
		for(SyncRecordHolder holder : theSyncRecords)
			if(holder.theSyncRecord.equals(record))
			{
				if(holder.theSyncRecord != record)
				{
					holder.theSyncRecord.setImport(record.isImport());
					holder.theSyncRecord.setSyncType(record.getSyncType());
					holder.theSyncRecord.setParallelID(record.getParallelID());
					holder.theSyncRecord.setSyncError(record.getSyncError());
				}
				return;
			}
		theLock.lock();
		try
		{
			if(record.getID() < 0)
			{
				record.setID(theNextSyncRecordID);
				theNextSyncRecordID++;
			}
			for(SyncRecordHolder holder : theSyncRecords)
				if(holder.theSyncRecord.equals(record))
				{
					if(holder.theSyncRecord != record)
					{
						holder.theSyncRecord.setImport(record.isImport());
						holder.theSyncRecord.setSyncType(record.getSyncType());
						holder.theSyncRecord.setParallelID(record.getParallelID());
						holder.theSyncRecord.setSyncError(record.getSyncError());
					}
					return;
				}
			int min = 0, max = theSyncRecords.size();
			while(min < max)
			{
				int mid = (min + max) / 2;
				if(theSyncRecords.get(mid).theSyncRecord.getSyncTime() > record.getSyncTime())
					max = mid - 1;
				else if(theSyncRecords.get(mid).theSyncRecord.getSyncTime() < record.getSyncTime())
					min = mid + 1;
				else
					min = max = mid;
			}

			theSyncRecords.add(min, new SyncRecordHolder(record));
			purge();
		} finally
		{
			theLock.unlock();
		}
	}

	public void associate(ChangeRecord change, SyncRecord syncRecord, boolean error)
		throws PrismsRecordException
	{
		for(SyncRecordHolder holder : theSyncRecords)
			if(holder.theSyncRecord.equals(syncRecord))
			{
				if(error)
				{
					for(int i = 0; i < holder.theErrorChanges.size(); i++)
						if(holder.theErrorChanges.get(i).equals(change))
							return;
				}
				else
				{
					for(int i = 0; i < holder.theSuccessChanges.size(); i++)
						if(holder.theSuccessChanges.get(i).equals(change))
							return;
				}
				theLock.lock();
				try
				{
					ArrayList<ChangeRecord> list;
					if(error)
					{
						list = holder.theErrorChanges;
						int idx = holder.theSuccessChanges.indexOf(change);
						if(idx >= 0)
							holder.theSuccessChanges.remove(idx);
					}
					else
					{
						list = holder.theSuccessChanges;
						int idx = holder.theErrorChanges.indexOf(change);
						if(idx >= 0)
							holder.theErrorChanges.remove(idx);
					}
					int min = 0, max = list.size();
					while(min < max)
					{
						int mid = (min + max) / 2;
						if(list.get(mid).time > change.time)
							max = mid - 1;
						else if(list.get(mid).time < change.time)
							min = mid + 1;
						else
							min = max = mid;
					}
					list.add(min, change);
				} finally
				{
					theLock.unlock();
				}
				return;
			}
		throw new prisms.records.PrismsRecordException("No such sync record: " + syncRecord);
	}

	public void removeSyncRecord(SyncRecord record) throws PrismsRecordException
	{
		theLock.lock();
		try
		{
			for(int h = 0; h < theSyncRecords.size(); h++)
				if(theSyncRecords.get(h).theSyncRecord.equals(record))
				{
					theSyncRecords.remove(h);
					return;
				}
		} finally
		{
			theLock.unlock();
		}
		throw new prisms.records.PrismsRecordException("No such sync record: " + record);
	}

	public ChangeRecord persist(RecordsTransaction trans, SubjectType subjectType,
		ChangeType changeType, int additivity, Object majorSubject, Object minorSubject,
		Object previousValue, Object data1, Object data2) throws PrismsRecordException
	{
		if(trans == null || trans.isMemoryOnly())
			return null;
		theLock.lock();
		try
		{
			long id = theCenterID * 1L * RecordUtils.theCenterIDRange + theNextChangeID;
			theNextChangeID++;
			ChangeRecord record = new ChangeRecord(id, !trans.shouldRecord(),
				System.currentTimeMillis(), trans.getUser(), subjectType, changeType, additivity,
				majorSubject, minorSubject, previousValue, data1, data2);
			persist(record);
			return record;
		} finally
		{
			theLock.unlock();
		}
	}

	public void persist(ChangeRecord record) throws PrismsRecordException
	{
		for(ChangeRecord change : theChanges)
			if(change.equals(record))
				return;
		theLock.lock();
		try
		{
			int min = 0, max = theChanges.size();
			while(min < max)
			{
				int mid = (min + max) / 2;
				if(theChanges.get(mid).time > record.time)
					max = mid - 1;
				else if(theChanges.get(mid).time < record.time)
					min = mid + 1;
				else
					min = max = mid;
			}
			theChanges.add(min, record);
		} finally
		{
			theLock.unlock();
		}
	}

	public long getLatestPurgedChange(int centerID, int subjectCenter) throws PrismsRecordException
	{
		Long ret = theRecentPurges.get(new DualKey<Integer, Integer>(Integer.valueOf(centerID),
			Integer.valueOf(subjectCenter)));
		if(ret == null)
			return -1;
		return ret.longValue();
	}

	private void purge()
	{
		// This must only be called if the lock is already obtained
		if(theChanges.isEmpty())
			return;
		long now = System.currentTimeMillis();
		if(now - theLastPurge < 30000)
			return;
		theLastPurge = now;
		long purgeTime = RecordUtils.getPurgeSafeTime(theCenters
			.toArray(new PrismsCenter [theCenters.size()]));
		if(theChanges.get(0).time >= purgeTime)
			return;
		boolean purgedSomething = false;
		// Purge changes that aren't needed for synchronization anymore
		for(int i = 0; i < theChanges.size(); i++)
		{
			ChangeRecord change = theChanges.get(i);
			if(change.time >= purgeTime)
				break;
			if(hasSyncExportError(change))
				continue; // A center is waiting for the change to be re-sent
			for(SyncRecordHolder holder : theSyncRecords)
			{
				if(holder.theSyncRecord.getSyncTime() < change.time)
					continue; // The change can't be in this sync record
				holder.theErrorChanges.remove(change);
				holder.theSuccessChanges.remove(change);
			}
			purgedSomething = true;
			theChanges.remove(i);
			Integer centerID = Integer.valueOf(RecordUtils.getCenterID(change.id));
			Integer subjectCenter = Integer.valueOf(getSubjectCenter(change));
			DualKey<Integer, Integer> key = new DualKey<Integer, Integer>(centerID, subjectCenter);
			Long recentPurge = theRecentPurges.get(key);
			if(recentPurge == null || recentPurge.longValue() < change.time)
				theRecentPurges.put(key, Long.valueOf(change.time));
			i--;
		}
		if(purgedSomething)
		{
			// Purge sync records that aren't needed for synchronization anymore
			for(int i = 0; i < theSyncRecords.size(); i++)
			{
				SyncRecordHolder holder = theSyncRecords.get(i);
				if(holder.theSyncRecord.getSyncTime() >= purgeTime)
					continue;
				if(holder.theSyncRecord.getSyncError() != null)
				{
					theSyncRecords.remove(i);
					i--;
					continue;
				}
				if(!holder.theErrorChanges.isEmpty() || !holder.theSuccessChanges.isEmpty())
					continue;
				theSyncRecords.remove(i);
				i--;
			}
		}
	}

	private boolean hasSyncExportError(ChangeRecord change)
	{
		int [] count = new int [theCenters.size()];
		for(SyncRecordHolder holder : theSyncRecords)
		{
			int centerIdx = theCenters.indexOf(holder.theSyncRecord.getCenter());
			if(count[centerIdx] < 0)
				continue;
			if(holder.theSyncRecord.getSyncTime() < change.time || holder.theSyncRecord.isImport())
				continue;
			if(holder.theSuccessChanges.contains(change))
				count[centerIdx] = -1;
			else if(holder.theErrorChanges.contains(change))
				count[centerIdx]++;
		}
		/* Return true if the change hasn't been successfully sent to a center AND the sync on that
		 * change has not been attempted enough times to satisfy the retry setting */
		for(int c : count)
			if(c > 0 && c < theRetrySetting)
				return true;
		return false;
	}

	public void disconnect()
	{
	}
}
