/*
 * SearchAPI.java Created Feb 22, 2011 by Andrew Butler, PSL
 */
package prisms.util;

import java.util.Calendar;

/**
 * Implements an API for allowing containing APIs to provide greater retrieval functionality through
 * very specific search structures
 */
public abstract class Search implements Cloneable
{
	/** Represents a type of search */
	public static interface SearchType
	{
		/**
		 * Creates a search of this type with the given query
		 * 
		 * @param search The search query
		 * @param builder The search builder that is compiling the search
		 * @return The compiled search object representing the given query
		 */
		public abstract Search create(String search, SearchBuilder builder);

		/** @return All headers that may begin a search of this type */
		public abstract String [] getHeaders();
	}

	private static class BaseSearchType implements SearchType
	{
		private final String theName;

		BaseSearchType(String name)
		{
			theName = name;
		}

		public Search create(String search, SearchBuilder builder)
		{
			throw new IllegalStateException("Cannot create " + theName + "-type search like this");
		}

		public String [] getHeaders()
		{
			return new String [0];
		}

		@Override
		public String toString()
		{
			return theName;
		}
	}

	/** Represents a binary operator with which to compare two scalar values with a boolean result */
	public enum Operator
	{
		/** The equality operator */
		EQ("="),
		/** The inequality operator */
		NEQ("!="),
		/** The greater than operator */
		GT(">"),
		/** The greater than or equal to operator */
		GTE(">="),
		/** The less than operator */
		LT("<"),
		/** The less than or equal to operator */
		LTE("<=");

		private final String name;

		private Operator(String aName)
		{
			name = aName;
		}

		@Override
		public String toString()
		{
			return name;
		}

		/**
		 * Parses an operator out of a modifiable character sequence.
		 * 
		 * @param sb The character sequence to parse. The string representation of the operator will
		 *        be removed from this sequence.
		 * @param search The full search string (used for error throwing)
		 * @return The operator represented at the beginning of the given character sequence
		 */
		public static Operator parse(StringBuilder sb, String search)
		{
			if(sb.charAt(0) == '<' || sb.charAt(0) == '>')
			{
				boolean gt = sb.charAt(0) == '>';
				sb.delete(0, 1);
				if(sb.charAt(0) == '=')
				{
					sb.delete(0, 1);
					if(gt)
						return GTE;
					else
						return LTE;
				}
				else
				{
					if(gt)
						return GT;
					else
						return LT;
				}
			}
			else if(sb.charAt(0) == '=')
			{
				sb.delete(0, 1);
				if(sb.charAt(0) == '=')
					sb.delete(0, 1);
				return EQ;
			}
			else if(sb.charAt(0) == '!')
			{
				sb.delete(0, 1);
				if(sb.charAt(0) != '=')
					throw new IllegalArgumentException("Illegal size expression: " + search
						+ "--operator unrecognized");
				sb.delete(0, 1);
				return NEQ;
			}
			else
				/* If a header is used with a parseable value right after, the operator is equality */
				return EQ;
		}
	}

	/** Implements a searchable time range */
	public static class SearchDate
	{
		private static final String [] MONTHS = new String [] {"january", "february", "march",
			"april", "may", "june", "july", "august", "september", "october", "november",
			"december"};

		private static final String [] ABBREV_MONTHS;

		private static final String [] WEEK_DAYS = new String [] {"sunday", "monday", "tuesday",
			"wednesday", "thursday", "friday", "saturday"};

		static
		{
			ABBREV_MONTHS = new String [MONTHS.length];
			for(int m = 0; m < MONTHS.length; m++)
				ABBREV_MONTHS[m] = MONTHS[m].substring(0, 3);
		}

		/** The day of month field, if specified in the search */
		public final Integer monthDay;

		/** The day of week field, if specified in the search */
		public final Integer weekDay;

		/** The month field, if specified in the search */
		public final Integer month;

		/** The year field, if specified in the search */
		public final Integer year;

		/** The hour field, if specified in the search */
		public final Integer hour;

		private final Integer minute;

		private final Integer second;

		private final Integer milli;

		/** The minimum time that this search can be interpreted as */
		public final long minTime;

		/** The maximum time that this search can be interpreted as */
		public final long maxTime;

		/** Whether this date is evaluated in GMT or local time */
		public final boolean gmt;

		/**
		 * Creates a SearchDate. The input may be constrained to any single time range, but
		 * combinations that create multiple ranges (e.g. h!=null, but md==null, meaning at a
		 * particular time on some day) are not accommodated.
		 * 
		 * @param md The day of month field, if specified in the search
		 * @param wd The day of week field, if specified in the search
		 * @param m The month field, if specified in the search
		 * @param y The year field, if specified in the search
		 * @param h The hour field, if specified in the search
		 * @param min The minute field, if specified in the search
		 * @param sec The second field, if specified in the search
		 * @param mills The millisecond field, if specified in the search
		 * @param local If the date is to be evaluated in local time instead of GMT
		 * @throws IllegalArgumentException If the combination of parameters is invalid
		 */
		public SearchDate(Integer md, Integer wd, Integer m, Integer y, Integer h, Integer min,
			Integer sec, Integer mills, boolean local)
		{
			if(wd != null && (md != null || m != null || y != null))
				throw new IllegalArgumentException(
					"If the week day is specified, month day, month, and year cannot be");
			monthDay = md;
			weekDay = wd;
			month = m;
			year = y;
			hour = h;
			minute = min;
			second = sec;
			milli = mills;
			long [] times = calcTimes();
			minTime = times[0];
			maxTime = times[1];
			gmt = !local;
		}

		/**
		 * Creates a SearchDate for a definite time.
		 * 
		 * @param time The millis-past-epoch time for this search date
		 */
		public SearchDate(long time)
		{
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(time);
			cal.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
			gmt = true;
			year = Integer.valueOf(cal.get(Calendar.YEAR));
			month = Integer.valueOf(cal.get(Calendar.MONTH));
			monthDay = Integer.valueOf(cal.get(Calendar.DAY_OF_MONTH));
			hour = Integer.valueOf(cal.get(Calendar.HOUR_OF_DAY));
			minute = Integer.valueOf(cal.get(Calendar.MINUTE));
			second = Integer.valueOf(cal.get(Calendar.SECOND));
			milli = Integer.valueOf(cal.get(Calendar.MILLISECOND));
			minTime = maxTime = time;
			weekDay = null;
		}

		/**
		 * Parses a date from a search string, removing it from the string
		 * 
		 * @param sb The charater sequence to parse the date out of
		 * @param srch The original search string (for error throwing)
		 * @return The parsed date
		 */
		public static SearchDate parse(StringBuilder sb, String srch)
		{
			if(sb.length() > 0 && sb.charAt(0) == '"')
				sb.delete(0, 1);
			java.util.Calendar now = java.util.Calendar.getInstance();
			Integer d, m, y, h, min = null, sec = null, mill = null;
			final boolean [] local = new boolean [] {false};
			boolean doubleZero = sb.length() >= 2 && sb.charAt(0) == '0' && sb.charAt(1) == '0';
			boolean tripleZero = doubleZero && sb.length() >= 3 && sb.charAt(2) == '0';
			int num = parseInt(sb);
			if(num >= 0)
			{ // num could be day or time
				trim(sb);
				int month = parseMonth(sb, srch);
				if(month >= 0)
				{ // num was the day. Now year and/or hour may be specified
					d = Integer.valueOf(num);
					m = Integer.valueOf(month);
					if(sb.length() > 0)
					{
						if(!Character.isWhitespace(sb.charAt(0)))
						{
							int year = parseInt(sb);
							if(year >= 0)
							{
								if(year < 100)
								{
									year += (now.get(Calendar.YEAR) / 100) * 100;
									if(year > now.get(Calendar.YEAR))
										year -= 100;
								}
								else if(year <= now.get(Calendar.YEAR))
								{}
								else
									throw new IllegalArgumentException("Illegal year in search "
										+ srch);
								y = Integer.valueOf(year);
							}
							else
								y = null;
						}
						else
							y = null;
						int [] hm = parseTime(sb, srch, local);
						if(hm != null)
						{
							h = Integer.valueOf(hm[0]);
							if(hm.length > 1 && hm[1] >= 0)
								min = Integer.valueOf(hm[1]);
							if(hm.length > 2 && hm[2] >= 0)
								sec = Integer.valueOf(hm[2]);
							if(hm.length > 3 && hm[3] >= 0)
								mill = Integer.valueOf(hm[3]);
						}
						else
							h = null;
					}
					else
						y = h = null;
				}
				else if(parseSuffix(sb, num))
				{ // num was the day, with no month/year specified
					now.add(Calendar.MONTH, -1);
					if(num < 1 && num > now.getActualMaximum(Calendar.DAY_OF_MONTH))
					{
						now.add(Calendar.MONTH, 1);
						if(num < 1 || num > 31)
							throw new IllegalArgumentException("Illegal day in search " + srch);
						else
							throw new IllegalArgumentException("Illegal day in search " + srch
								+ " for " + print(MONTHS[now.get(Calendar.MONTH) - 1]));
					}
					d = Integer.valueOf(num);
					int [] hm = parseTime(sb, srch, local);
					if(hm != null)
					{
						h = Integer.valueOf(hm[0]);
						if(hm.length > 1 && hm[1] >= 0)
							min = Integer.valueOf(hm[1]);
						if(hm.length > 2 && hm[2] >= 0)
							sec = Integer.valueOf(hm[2]);
						if(hm.length > 3 && hm[3] >= 0)
							mill = Integer.valueOf(hm[3]);
					}
					else
						h = null;
					m = null;
					y = null;
					h = null;
				}
				else
				{ // num was the time
					m = null;
					y = null;
					d = null;
					h = null;
					if((num == 0 && !tripleZero) || (num > 0 && num < 24 && !doubleZero))
						h = Integer.valueOf(num);
					else if(num >= 2400 || num % 100 >= 60)
						throw new IllegalArgumentException("Illegal time in search " + srch + ": "
							+ num);
					else
					{
						h = Integer.valueOf(num / 100);
						min = Integer.valueOf(num % 100);
					}
					if(sb.length() > 0 && sb.charAt(0) == ':')
					{
						sb.delete(0, 1);
						int s = parseInt(sb);
						if(s >= 0)
						{
							sec = Integer.valueOf(s);
							if(sb.length() > 0 && sb.charAt(0) == '.')
							{
								int mil = parseInt(sb);
								if(mil >= 0)
									mill = Integer.valueOf(mil);
							}
						}
					}
				}
			}
			else
			{
				d = null;
				int month = parseMonth(sb, srch);
				if(month < 0)
				{
					num = parseWeekDay(sb, srch);
					if(num < 0)
						throw new IllegalArgumentException("Illegal data value in search: " + srch);
					trim(sb);
					Integer wd = Integer.valueOf(num);
					num = parseInt(sb);
					if(num < 0)
						h = null;
					else
					{
						h = Integer.valueOf(num);
						if(sb.length() > 0 && (sb.charAt(0) == 'Z' || sb.charAt(0) == 'z'))
							sb.delete(0, 1);
					}
					if(sb.length() > 0 && sb.charAt(0) == '"')
						sb.delete(0, 1);
					return new SearchDate(null, wd, null, null, h, min, null, null, local[0]);
				}
				m = Integer.valueOf(month);
				if(sb.length() > 0)
				{
					if(!Character.isWhitespace(sb.charAt(0)))
					{
						int year = parseInt(sb);
						if(year >= 0)
						{
							if(year < 100)
							{
								year += (now.get(Calendar.YEAR) / 100) * 100;
								if(year > now.get(Calendar.YEAR))
									year -= 100;
							}
							else if(year <= now.get(Calendar.YEAR))
							{}
							else
								throw new IllegalArgumentException("Illegal year in search " + srch);
							y = Integer.valueOf(year);
						}
						else
							y = null;
					}
					else
						y = null;
					int [] hm = parseTime(sb, srch, local);
					if(hm != null)
					{
						h = Integer.valueOf(hm[0]);
						if(hm.length > 1 && hm[1] >= 0)
							min = Integer.valueOf(hm[1]);
						if(hm.length > 2 && hm[2] >= 0)
							sec = Integer.valueOf(hm[2]);
						if(hm.length > 3 && hm[3] >= 0)
							mill = Integer.valueOf(hm[3]);
					}
					else
						h = null;
				}
				else
					y = h = null;
			}
			if(sb.length() > 0 && sb.charAt(0) == '"')
				sb.delete(0, 1);
			return new SearchDate(d, null, m, y, h, min, sec, mill, local[0]);
		}

		private static int [] parseTime(StringBuilder sb, String srch, boolean [] local)
		{
			trim(sb);
			boolean doubleZero = sb.length() >= 2 && sb.charAt(0) == '0' && sb.charAt(1) == '0';
			boolean tripleZero = doubleZero && sb.length() >= 3 && sb.charAt(2) == '0';
			int num = parseInt(sb);
			if(num < 0)
				return null;
			if((num == 0 && !tripleZero) || (num > 0 && num < 24 && !doubleZero))
			{
				if(sb.length() == 0 || (sb.charAt(0) != 'Z' && sb.charAt(0) != 'z'))
				{
					sb.delete(0, 1);
					local[0] = true;
				}
				return new int [] {num};
			}
			else if(num >= 2400 || num % 100 >= 60)
				throw new IllegalArgumentException("Illegal time in search " + srch + ": " + num);
			int h = num / 100;
			int m = num % 100;
			if(sb.length() == 0 || (sb.charAt(0) != 'Z' && sb.charAt(0) != 'z'))
			{
				sb.delete(0, 1);
				local[0] = true;
			}
			if(sb.length() == 0 || sb.charAt(0) != ':')
				return new int [] {h, m};
			sb.delete(0, 1);
			int s = parseInt(sb);
			if(s < 0)
				return new int [] {h, m};
			if(sb.length() == 0 || sb.charAt(0) != '.')
				return new int [] {h, m, s};
			sb.delete(0, 1);
			int mil = parseInt(sb);
			if(mil < 0)
				return new int [] {h, m, s};
			return new int [] {h, m, s, mil};
		}

		/**
		 * Parses a positive integer and removes its representation from a modifiable character
		 * sequence
		 * 
		 * @param sb The character sequence to parse the integer from
		 * @return The parsed int, or -1 if the first item in the sequence is not an integer
		 */
		public static int parseInt(StringBuilder sb)
		{
			int numberChars = 0;
			for(;; numberChars++)
			{
				if(numberChars >= sb.length())
					break;
				char ch = sb.charAt(numberChars);
				if(ch >= '0' && ch <= '9')
					continue;
				else
					break;
			}
			if(numberChars == 0)
				return -1;
			else
			{
				int ret = Integer.parseInt(sb.substring(0, numberChars));
				sb.delete(0, numberChars);
				return ret;
			}
		}

		/**
		 * Checks the string to see if it begins with the correct suffix for the given number, e.g.
		 * 1<b>st</b>, 102<b>nd</b>, 13<b>th</b>
		 * 
		 * @param sb The string to check for the suffix
		 * @param num The number whose suffix to check for
		 * @return Whether the suffix was the first thing in the string. If so, the suffix will be
		 *         removed from the string
		 */
		public static boolean parseSuffix(StringBuilder sb, int num)
		{
			if(sb.length() < 2)
				return false;
			boolean ret;
			if(num % 10 == 1 && num / 10 != 1)
				ret = sb.charAt(0) == 's' && sb.charAt(1) == 't';
			else if(num % 10 == 2 && num / 10 != 1)
				ret = sb.charAt(0) == 'n' && sb.charAt(1) == 'd';
			else if(num % 10 == 2 && num / 10 != 1)
				ret = sb.charAt(0) == 'r' && sb.charAt(1) == 'd';
			else
				ret = sb.charAt(0) == 't' && sb.charAt(1) == 'h';
			if(ret)
				sb.delete(0, 2);
			return ret;
		}

		private static int parseMonth(StringBuilder sb, String srch)
		{
			if(sb.length() < 3)
				return -1;
			for(int m = 0; m < ABBREV_MONTHS.length; m++)
			{
				int i;
				for(i = 0; i < 3; i++)
				{
					char ch = sb.charAt(i);
					if(ch >= 'A' && ch <= 'Z')
						ch = (char) (ch - 'A' + 'a');
					if(ABBREV_MONTHS[m].charAt(i) != ch)
						break;
				}
				if(i < 3)
					continue;
				if(sb.length() - i == 0)
				{
					sb.delete(0, i);
					return m;
				}
				char ch = sb.charAt(i);
				if(!((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')))
				{
					sb.delete(0, i);
					return m;
				}
				for(; i < MONTHS[m].length() && i < sb.length(); i++)
				{
					ch = sb.charAt(i);
					if(ch >= 'A' && ch <= 'Z')
						ch = (char) (ch - 'A' + 'a');
					if(!(ch >= 'a' && ch <= 'z'))
						break;
					if(ch != MONTHS[m].charAt(i))
						throw new IllegalStateException("Illegal date value in search " + srch);
				}
				sb.delete(0, i);
				return m;
			}
			return -1;
		}

		private static int parseWeekDay(StringBuilder sb, String srch)
		{
			for(int d = 0; d < WEEK_DAYS.length; d++)
			{
				int i;
				for(i = 0; i < sb.length(); i++)
				{
					int ch = sb.charAt(i);
					if(ch >= 'A' && ch <= 'Z')
						ch = (char) (ch - 'A' + 'a');
					if(ch != WEEK_DAYS[d].charAt(i))
						break;
				}
				if(i >= 2)
				{
					sb.delete(0, i);
					switch(d)
					{
					case 0:
						return Calendar.SUNDAY;
					case 1:
						return Calendar.MONDAY;
					case 2:
						return Calendar.TUESDAY;
					case 3:
						return Calendar.WEDNESDAY;
					case 4:
						return Calendar.THURSDAY;
					case 5:
						return Calendar.FRIDAY;
					case 6:
						return Calendar.SATURDAY;
					}
				}
			}
			return -1;
		}

		private static String print(String name)
		{
			StringBuilder ret = new StringBuilder(name);
			boolean newWord = true;
			for(int i = 0; i < ret.length(); i++)
			{
				if(newWord && Character.isLowerCase(ret.charAt(i)))
					ret.setCharAt(i, (char) (ret.charAt(i) + 'A' - 'a'));
				newWord = Character.isWhitespace(ret.charAt(i));
			}
			return ret.toString();
		}

		private long [] calcTimes()
		{
			long now = System.currentTimeMillis();
			long min, max;
			Calendar cal = Calendar.getInstance();
			if(gmt)
				cal.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
			cal.set(Calendar.MINUTE, minute == null ? 0 : minute.intValue());
			cal.set(Calendar.SECOND, second == null ? 0 : second.intValue());
			cal.set(Calendar.MILLISECOND, milli == null ? 0 : milli.intValue());
			if(weekDay != null)
			{
				cal.set(Calendar.DAY_OF_WEEK, weekDay.intValue());
				if(hour != null)
				{
					cal.set(Calendar.HOUR_OF_DAY, hour.intValue());
					if(cal.getTimeInMillis() > now)
						cal.add(Calendar.DAY_OF_MONTH, -7);
					min = cal.getTimeInMillis();
					cal.add(Calendar.HOUR_OF_DAY, 1);
					max = cal.getTimeInMillis();
				}
				else
				{
					cal.set(Calendar.HOUR_OF_DAY, 0);
					if(cal.getTimeInMillis() > now)
						cal.add(Calendar.DAY_OF_MONTH, -7);
					min = cal.getTimeInMillis();
					cal.add(Calendar.DAY_OF_MONTH, 1);
					max = cal.getTimeInMillis();
				}
			}
			else if(year != null)
			{
				cal.set(Calendar.YEAR, year.intValue());
				if(month != null)
				{
					cal.set(Calendar.MONTH, month.intValue());
					if(monthDay != null)
					{
						cal.set(Calendar.DAY_OF_MONTH, monthDay.intValue());
						if(hour != null)
						{
							cal.set(Calendar.HOUR_OF_DAY, hour.intValue());
							min = cal.getTimeInMillis();
							cal.add(Calendar.HOUR_OF_DAY, 1);
							max = cal.getTimeInMillis();
						}
						else
						{
							cal.set(Calendar.HOUR_OF_DAY, 0);
							min = cal.getTimeInMillis();
							cal.add(Calendar.DAY_OF_MONTH, 1);
							max = cal.getTimeInMillis();
						}
					}
					else
					{
						cal.set(Calendar.DAY_OF_MONTH, 1);
						if(hour != null)
							throw new IllegalArgumentException("Hour cannot be specified"
								+ " if monthDay is not specified but year is");
						else
						{
							cal.set(Calendar.HOUR_OF_DAY, 0);
							min = cal.getTimeInMillis();
							cal.add(Calendar.MONTH, 1);
							max = cal.getTimeInMillis();
						}
					}
				}
				else
				{
					cal.set(Calendar.MONTH, Calendar.JANUARY);
					if(monthDay != null)
						throw new IllegalArgumentException("monthDay cannot be specified"
							+ " if month is not specified but year is");
					else
					{
						cal.set(Calendar.DAY_OF_MONTH, 1);
						if(hour != null)
							throw new IllegalArgumentException("Hour cannot be specified"
								+ " if monthDay is not specified but year is");
						else
						{
							cal.set(Calendar.HOUR_OF_DAY, 0);
							min = cal.getTimeInMillis();
							cal.add(Calendar.YEAR, 1);
							max = cal.getTimeInMillis();
						}
					}
				}
			}
			else
			{
				// Use current year
				if(month != null)
				{
					cal.set(Calendar.MONTH, month.intValue());
					if(monthDay != null)
					{
						cal.set(Calendar.DAY_OF_MONTH, monthDay.intValue());
						if(hour != null)
						{
							cal.set(Calendar.HOUR_OF_DAY, hour.intValue());
							if(cal.getTimeInMillis() > now)
								cal.add(Calendar.YEAR, -1);
							min = cal.getTimeInMillis();
							cal.add(Calendar.HOUR_OF_DAY, 1);
							max = cal.getTimeInMillis();
						}
						else
						{
							cal.set(Calendar.HOUR_OF_DAY, 0);
							if(cal.getTimeInMillis() > now)
								cal.add(Calendar.YEAR, -1);
							min = cal.getTimeInMillis();
							cal.add(Calendar.DAY_OF_MONTH, 1);
							max = cal.getTimeInMillis();
						}
					}
					else
					{
						cal.set(Calendar.DAY_OF_MONTH, 1);
						if(hour != null)
							throw new IllegalArgumentException("Hour cannot be specified"
								+ " if monthDay is not specified but month is");
						else
						{
							cal.set(Calendar.HOUR_OF_DAY, 0);
							if(cal.getTimeInMillis() > now)
								cal.add(Calendar.YEAR, -1);
							min = cal.getTimeInMillis();
							cal.add(Calendar.MONTH, 1);
							max = cal.getTimeInMillis();
						}
					}
				}
				else
				{
					if(monthDay != null)
					{
						cal.set(Calendar.DAY_OF_MONTH, monthDay.intValue());
						if(hour != null)
						{
							cal.set(Calendar.HOUR_OF_DAY, hour.intValue());
							if(cal.getTimeInMillis() > now)
								cal.add(Calendar.MONTH, -1);
							min = cal.getTimeInMillis();
							cal.add(Calendar.HOUR_OF_DAY, 1);
							max = cal.getTimeInMillis();
						}
						else
						{
							cal.set(Calendar.HOUR_OF_DAY, 0);
							if(cal.getTimeInMillis() > now)
								cal.add(Calendar.MONTH, -1);
							min = cal.getTimeInMillis();
							cal.add(Calendar.DAY_OF_MONTH, 1);
							max = cal.getTimeInMillis();
						}
					}
					else
					{
						if(hour != null)
						{
							cal.set(Calendar.HOUR_OF_DAY, hour.intValue());
							if(cal.getTimeInMillis() > now)
								cal.add(Calendar.DAY_OF_MONTH, -1);
							min = cal.getTimeInMillis();
							cal.add(Calendar.HOUR_OF_DAY, 1);
							max = cal.getTimeInMillis();
						}
						else
							throw new IllegalArgumentException("No date constraints at all!");
					}
				}
			}
			return new long [] {min, max - 1};
		}

		/**
		 * @param op The operator to use to check the time
		 * @param time The time to check
		 * @return Whether a given time matches this search date
		 */
		public boolean matches(Operator op, long time)
		{
			switch(op)
			{
			case EQ:
				return minTime <= time && time <= maxTime;
			case NEQ:
				return time < minTime || time > maxTime;
			case GT:
				return time > maxTime;
			case GTE:
				return time >= minTime;
			case LT:
				return time < minTime;
			case LTE:
				return time <= maxTime;
			}
			throw new IllegalStateException("Unrecognized operator: " + op);
		}

		@Override
		public boolean equals(Object o)
		{
			if(!(o instanceof SearchDate))
				return false;
			SearchDate sd = (SearchDate) o;
			return sd.minTime == minTime && sd.maxTime == maxTime && equal(sd.year, year)
				&& equal(sd.month, month) && equal(sd.monthDay, monthDay) && equal(sd.hour, hour)
				&& equal(sd.minute, minute) && equal(sd.second, second) && equal(sd.milli, milli)
				&& equal(sd.weekDay, weekDay);
		}

		@Override
		public int hashCode()
		{
			long ret = minTime + maxTime + hash(year) + hash(month) + hash(monthDay) + hash(hour)
				+ hash(minute) + hash(second) + hash(milli) + hash(weekDay);
			return (int) ((ret & 0xffffffffL) + (ret >>> 32));
		}

		@Override
		public String toString()
		{
			StringBuilder ret = new StringBuilder();
			if(weekDay != null || hour != null)
				ret.append('"');
			if(weekDay != null)
				ret.append(print(WEEK_DAYS[weekDay.intValue()])).append(' ');
			if(monthDay != null)
				ret.append(monthDay);
			if(month != null)
				ret.append(print(ABBREV_MONTHS[month.intValue()]));
			if(year != null)
				ret.append(year);
			if(hour != null)
			{
				if(ret.length() > 0)
					ret.append(' ');
				if(hour.intValue() < 10)
					ret.append('0');
				ret.append(hour);
				if(minute != null)
				{
					if(minute.intValue() < 10)
						ret.append('0');
					ret.append(minute);
					if(gmt)
						ret.append('Z');
					ret.append(':');
					if(second.intValue() < 10)
						ret.append('0');
					ret.append(second);
					ret.append('.');
					if(milli.intValue() < 100)
						ret.append('0');
					if(milli.intValue() < 10)
						ret.append('0');
					ret.append(milli);
				}
				else
				{
					ret.append("00");
					if(gmt)
						ret.append('Z');
				}
			}
			if(weekDay != null || hour != null)
				ret.append('"');
			return ret.toString();
		}
	}

	/** Implements a searchable age range */
	public static class SearchAge
	{
		/** The number of years in the age */
		public final Integer years;

		/** The number of months in the age */
		public final Integer months;

		/** The number of weeks in the age */
		public final Integer weeks;

		/** The number of days in the age */
		public final Integer days;

		/** The number of hours in the age */
		public final Integer hours;

		/** The number of minutes in the age */
		public final Integer minutes;

		/** The number of seconds in the age */
		public final Integer seconds;

		/**
		 * @param y The number of years in the age
		 * @param mo The number of months in the age
		 * @param w The number of weeks in the age
		 * @param d The number of days in the age
		 * @param h The number of hours in the age
		 * @param min The number of minutes in the age
		 * @param sec The number of seconds in the age
		 */
		public SearchAge(Integer y, Integer mo, Integer w, Integer d, Integer h, Integer min,
			Integer sec)
		{
			years = y;
			months = mo;
			weeks = w;
			days = d;
			hours = h;
			minutes = min;
			seconds = sec;
		}

		/**
		 * Parses a search age from a string
		 * 
		 * @param sb The string to parse the search age out of
		 * @param srch The search as a whole, for error throwing
		 * @return The parsed search age
		 */
		public static SearchAge parse(StringBuilder sb, String srch)
		{
			Integer y = null, mo = null, w = null, d = null, h = null, min = null, sec = null;
			int c = 0;
			for(; c < sb.length() && !Character.isLetter(sb.charAt(c))
				&& (sb.charAt(c) < '0' || sb.charAt(c) > '9'); c++);

			while(c < sb.length())
			{
				int amt = 0;
				for(; c < sb.length() && sb.charAt(c) >= '0' && sb.charAt(c) <= '9'; c++)
					amt = amt * 10 + sb.charAt(c) - '0';

				for(; c < sb.length() && !Character.isLetter(sb.charAt(c))
					&& (sb.charAt(c) < '0' || sb.charAt(c) > '9'); c++);

				StringBuilder unit = new StringBuilder();
				for(; c < sb.length() && Character.isLetter(sb.charAt(c)); c++)
					unit.append(sb.charAt(c));
				String unitS = unit.toString().toLowerCase();
				if(unitS.length() == 0)
					throw new IllegalArgumentException("No unit for age: " + srch);
				if("years".startsWith(unitS))
				{
					if(y != null)
						throw new IllegalArgumentException("Multiple years specified for age: "
							+ srch);
					y = Integer.valueOf(amt);
				}
				else if("months".startsWith(unitS) && unitS.length() > 1)
				{ // "m" means minutes, not months
					if(mo != null)
						throw new IllegalArgumentException("Multiple months specified for age: "
							+ srch);
					mo = Integer.valueOf(amt);
				}
				else if("weeks".startsWith(unitS))
				{
					if(w != null)
						throw new IllegalArgumentException("Multiple weeks specified for age: "
							+ srch);
					w = Integer.valueOf(amt);
				}
				else if("days".startsWith(unitS))
				{
					if(d != null)
						throw new IllegalArgumentException("Multiple days specified for age: "
							+ srch);
					d = Integer.valueOf(amt);
				}
				else if("hours".startsWith(unitS))
				{
					if(h != null)
						throw new IllegalArgumentException("Multiple hours specified for age: "
							+ srch);
					h = Integer.valueOf(amt);
				}
				else if("minutes".startsWith(unitS))
				{
					if(min != null)
						throw new IllegalArgumentException("Multiple minutes specified for age: "
							+ srch);
					min = Integer.valueOf(amt);
				}
				else if("seconds".startsWith(unitS))
				{
					if(sec != null)
						throw new IllegalArgumentException("Multiple seconds specified for age: "
							+ srch);
					sec = Integer.valueOf(amt);
				}
				else
					throw new IllegalArgumentException("Unrecognized age unit: " + unitS
						+ " in search " + srch);

				for(; c < sb.length() && !Character.isLetter(sb.charAt(c))
					&& (sb.charAt(c) < '0' || sb.charAt(c) > '9'); c++);
				if(c < sb.length() && sb.charAt(c) == ',')
				{
					c++;
					for(; c < sb.length() && !Character.isLetter(sb.charAt(c))
						&& (sb.charAt(c) < '0' || sb.charAt(c) > '9'); c++);
				}
			}
			if(y == null && mo == null && w == null && d == null && h == null && min == null
				&& sec == null)
				throw new IllegalArgumentException("No age specified in search: " + srch);
			return new SearchAge(y, mo, w, d, h, min, sec);
		}

		/**
		 * @param now The current time
		 * @return The time that is this age's time ago from the given time
		 */
		public long getTime(long now)
		{
			if(years != null && years.intValue() > 0)
				now -= years.intValue() * 365L * 24 * 60 * 60 * 1000;
			if(months != null && months.intValue() > 0)
				now -= months.intValue() * 30L * 24 * 60 * 60 * 1000;
			if(weeks != null && weeks.intValue() > 0)
				now -= weeks.intValue() * 7L * 24 * 60 * 60 * 1000;
			if(days != null && days.intValue() > 0)
				now -= days.intValue() * 24L * 60 * 60 * 1000;
			if(hours != null && hours.intValue() > 0)
				now -= hours.intValue() * 60L * 60 * 1000;
			if(minutes != null && minutes.intValue() > 0)
				now -= minutes.intValue() * 60L * 1000;
			if(seconds != null && seconds.intValue() > 0)
				now -= seconds.intValue() * 1000L;
			return now;
		}

		/** @return The precision of this age */
		public long getPrecision()
		{
			long prec = 0;
			if(years != null && years.intValue() > 0)
			{
				if(years.intValue() <= 4)
					prec = years.intValue() * 3L * 30 * 24 * 60 * 60 * 1000;
				else
					prec = 365L * 24 * 60 * 60 * 1000;
			}
			if(months != null && months.intValue() > 0)
			{
				if(months.intValue() <= 4)
					prec = months.intValue() * 7L * 24 * 60 * 60 * 1000;
				else
					prec = 30L * 24 * 60 * 60 * 1000;
			}
			if(weeks != null && weeks.intValue() > 0)
			{
				if(weeks.intValue() <= 3)
					prec = weeks.intValue() * 2L * 24 * 60 * 60 * 1000;
				else
					prec = 7L * 24 * 60 * 60 * 1000;
			}
			if(days != null && days.intValue() > 0)
			{
				if(days.intValue() <= 4)
					prec = days.intValue() * 6L * 60 * 60 * 1000;
				else
					prec = 24L * 60 * 60 * 1000;
			}
			if(hours != null && hours.intValue() > 0)
			{
				if(hours.intValue() < 4)
					prec = hours.intValue() * 15L * 60 * 1000;
				else
					prec = 60L * 60 * 1000;
			}
			if(minutes != null && minutes.intValue() > 0)
			{
				if(minutes.intValue() < 4)
					prec = minutes.intValue() * 15L * 1000;
				else
					prec = 60L * 1000;
			}
			if(seconds != null && seconds.intValue() > 0)
			{
				if(seconds.intValue() <= 4)
					prec = seconds.intValue() * 250L;
				else
					prec = 1000L;
			}
			return prec;
		}

		/**
		 * @param now The current time
		 * @return The minimum time included in this search age
		 */
		public long getMin(long now)
		{
			return getTime(now) - getPrecision();
		}

		/**
		 * @param now The current time
		 * @return The maximum time included in this search age
		 */
		public long getMax(long now)
		{
			return getTime(now) + getPrecision();
		}

		/**
		 * Checks a time to see if it matches this search age
		 * 
		 * @param op The operator to check against
		 * @param now The current time
		 * @param time The time to test
		 * @return Whether the given time matches this age
		 */
		public boolean matches(Operator op, long now, long time)
		{
			long ageTime = getTime(now);
			long prec = getPrecision();
			switch(op)
			{
			case EQ:
				return Math.abs(time - ageTime) <= prec;
			case GT:
				return time < ageTime;
			case GTE:
				return time <= ageTime + prec;
			case LT:
				return time > ageTime;
			case LTE:
				return time >= ageTime - prec;
			case NEQ:
				return Math.abs(time - ageTime) > prec;
			}
			throw new IllegalStateException("Unrecognized operator: " + op);
		}

		@Override
		public boolean equals(Object o)
		{
			if(!(o instanceof SearchAge))
				return false;
			SearchAge a = (SearchAge) o;
			return equal(years, a.years) && equal(months, a.months) && equal(weeks, a.weeks)
				&& equal(days, a.days) && equal(hours, a.hours) && equal(minutes, a.minutes)
				&& equal(seconds, a.seconds);
		}

		@Override
		public int hashCode()
		{
			long ret = hash(years) + hash(months) + hash(weeks) + hash(days) + hash(hours)
				+ hash(minutes) + hash(seconds);
			return (int) ((ret & 0xffffffffL) + (ret >>> 32));
		}

		@Override
		public String toString()
		{
			StringBuilder ret = new StringBuilder();
			boolean comma = false;
			if(years != null && years.intValue() > 0)
			{
				if(comma)
					ret.append(",");
				comma = true;
				ret.append(years).append("year");
				if(years.intValue() > 1)
					ret.append('s');
			}
			if(months != null && months.intValue() > 0)
			{
				if(comma)
					ret.append(",");
				comma = true;
				ret.append(months).append("month");
				if(months.intValue() > 1)
					ret.append('s');
			}
			if(weeks != null && weeks.intValue() > 0)
			{
				if(comma)
					ret.append(",");
				comma = true;
				ret.append(weeks).append("week");
				if(weeks.intValue() > 1)
					ret.append('s');
			}
			if(days != null && days.intValue() > 0)
			{
				if(comma)
					ret.append(",");
				comma = true;
				ret.append(days).append("day");
				if(days.intValue() > 1)
					ret.append('s');
			}
			if(hours != null)
			{
				if(comma)
					ret.append(",");
				comma = true;
				ret.append(hours).append("hour");
				if(hours.intValue() > 1)
					ret.append('s');
			}
			if(minutes != null)
			{
				if(comma)
					ret.append(",");
				comma = true;
				ret.append(minutes).append("minute");
				if(minutes.intValue() > 1)
					ret.append('s');
			}
			if(seconds != null)
			{
				if(comma)
					ret.append(",");
				comma = true;
				ret.append(seconds).append("second");
				if(seconds.intValue() > 1)
					ret.append('s');
			}
			return ret.toString();
		}
	}

	static boolean equal(Object o1, Object o2)
	{
		return o1 == null ? o2 == null : o1.equals(o2);
	}

	static int hash(Object o1)
	{
		return o1 == null ? 0 : o1.hashCode();
	}

	/** Iterates through a {@link CompoundSearch}'s children */
	public static class SearchIterator implements java.util.Iterator<Search>
	{
		private final Search [] theSearches;

		private int theIndex;

		/**
		 * Creates a search iterator
		 * 
		 * @param searches The searches to iterate through
		 */
		public SearchIterator(Search... searches)
		{
			theSearches = searches;
		}

		public boolean hasNext()
		{
			return theIndex < theSearches.length;
		}

		public Search next()
		{
			return theSearches[theIndex++];
		}

		public void remove()
		{
			throw new UnsupportedOperationException("remove() not supported by search iterator");
		}
	}

	/**
	 * A compound search is a search that may be a logical combination of one or more other searches
	 * and may have a parent search
	 */
	public static abstract class CompoundSearch extends Search implements Iterable<Search>
	{
		private CompoundSearch theParent;

		/** @return This search's parent search--may be null if this is the root expression */
		public CompoundSearch getParent()
		{
			return theParent;
		}

		/** @param search The search that contains this search */
		public void setParent(CompoundSearch search)
		{
			theParent = search;
		}
	}

	/** Searches for messages NOT matching a given search */
	public static class NotSearch extends CompoundSearch
	{
		/** The type of this search */
		public static final SearchType type = new BaseSearchType("NOT");

		private Search operand;

		/** Creates an empty NOT search */
		public NotSearch()
		{
		}

		/** @param op The search whose results will NOT be returned from this search */
		public NotSearch(Search op)
		{
			operand = op;
		}

		@Override
		public SearchType getType()
		{
			return type;
		}

		/** @return The search whose results will NOT be returned from this search */
		public Search getOperand()
		{
			return operand;
		}

		/** @param srch The search whose results should NOT be returned from this search */
		public void setOperand(Search srch)
		{
			operand = srch;
		}

		public java.util.Iterator<Search> iterator()
		{
			return new SearchIterator(operand);
		}

		@Override
		public NotSearch clone()
		{
			return this; // Immutable
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof NotSearch && ((NotSearch) o).operand.equals(operand);
		}

		@Override
		public int hashCode()
		{
			return operand.hashCode() + 17;
		}

		@Override
		public String toString()
		{
			return "!" + operand.toString();
		}
	}

	/**
	 * A compound message search that combines one or more simpler searches (any of which may also
	 * be an ExpressionSearch) with an AND or OR operation
	 */
	public static class ExpressionSearch extends CompoundSearch
	{
		/** The type of this search */
		public static final SearchType type = new BaseSearchType("EXPR");

		private Search [] theOperands;

		/** Whether the operation of the search is AND or OR */
		public final boolean and;

		/** @param _and Whether the expression is AND'ed or OR'ed */
		public ExpressionSearch(boolean _and)
		{
			and = _and;
			theOperands = new Search [0];
		}

		/** @return The number of operands in this search */
		public int getOperandCount()
		{
			return theOperands.length;
		}

		/**
		 * @param index The index of the operand to get
		 * @return The operand at this given index
		 */
		public Search getOperand(int index)
		{
			return theOperands[index];
		}

		/**
		 * Adds an operand to this expression
		 * 
		 * @param search The search to add as an operand to this expression
		 */
		public void add(Search search)
		{
			if(search instanceof CompoundSearch)
				((CompoundSearch) search).setParent(this);
			theOperands = ArrayUtils.add(theOperands, search);
		}

		/**
		 * Removes an operand from this expression
		 * 
		 * @param search The search to remove as an operand from this expression
		 * @return Whether the operand was found in this expression
		 */
		public boolean remove(Search search)
		{
			int index = ArrayUtils.indexOf(theOperands, search);
			if(index >= 0)
			{
				remove(index);
				return true;
			}
			else
				return false;
		}

		/**
		 * Removes an operand from this expression
		 * 
		 * @param index The index of the operand to remove
		 * @return The operand that was removed
		 */
		public Search remove(int index)
		{
			Search ret = theOperands[index];
			theOperands = ArrayUtils.remove(theOperands, index);
			if(ret instanceof CompoundSearch)
				((CompoundSearch) ret).setParent(null);
			return ret;
		}

		public java.util.Iterator<Search> iterator()
		{
			return new SearchIterator(theOperands);
		}

		/**
		 * A shorthand, chaining-enabled method
		 * 
		 * @param ops The operands to add to this search
		 * @return this
		 */
		public ExpressionSearch addOps(Search... ops)
		{
			for(Search op : ops)
				add(op);
			return this;
		}

		@Override
		public SearchType getType()
		{
			return type;
		}

		@Override
		public ExpressionSearch or(Search srch)
		{
			if(and)
				return super.or(srch);
			else
			{
				add(srch);
				return this;
			}
		}

		@Override
		public ExpressionSearch and(Search srch)
		{
			if(and)
			{
				add(srch);
				return this;
			}
			else
				return super.and(srch);
		}

		/** Recursively simplifies this expression if possible */
		public void simplify()
		{
			for(int o = 0; o < theOperands.length; o++)
			{
				if(!(theOperands[o] instanceof ExpressionSearch))
					continue;
				ExpressionSearch exp = (ExpressionSearch) theOperands[o];
				exp.simplify();
				if(exp.getOperandCount() == 0)
				{
					remove(o);
					o--;
				}
				if(exp.and == and)
				{
					Search [] newOps = new Search [theOperands.length + exp.theOperands.length - 1];
					System.arraycopy(theOperands, 0, newOps, 0, o);
					System.arraycopy(exp.theOperands, 0, newOps, o, exp.theOperands.length);
					System.arraycopy(theOperands, o + 1, newOps, o + exp.theOperands.length,
						theOperands.length - o - 1);
					for(Search op : exp.theOperands)
						if(op instanceof CompoundSearch)
							((CompoundSearch) op).setParent(this);
					o += exp.theOperands.length - 1;
					theOperands = newOps;
				}
			}
		}

		@Override
		public boolean equals(Object o)
		{
			if(!(o instanceof ExpressionSearch))
				return false;
			ExpressionSearch exp = (ExpressionSearch) o;
			if(exp.getOperandCount() != getOperandCount())
				return false;
			for(int i = 0; i < getOperandCount(); i++)
				if(!getOperand(i).equals(exp.getOperand(i)))
					return false;
			return true;
		}

		@Override
		public int hashCode()
		{
			int ret = getOperandCount();
			for(int i = 0; i < getOperandCount(); i++)
				ret += getOperand(i).hashCode();
			return ret;
		}

		@Override
		public ExpressionSearch clone()
		{
			ExpressionSearch ret = (ExpressionSearch) super.clone();
			for(int o = 0; o < theOperands.length; o++)
			{
				ret.theOperands[o] = theOperands[o].clone();
				if(ret.theOperands[o] instanceof CompoundSearch)
					((CompoundSearch) ret.theOperands[o]).setParent(ret);
			}
			return ret;
		}

		@Override
		public String toString()
		{
			StringBuilder ret = new StringBuilder();
			for(int i = 0; i < theOperands.length; i++)
			{
				if(i > 0)
					ret.append(and ? " " : " OR ");
				ret.append(theOperands[i].toString());
			}
			if(getParent() != null)
			{
				ret.insert(0, '(');
				ret.append(')');
			}
			return ret.toString();
		}
	}

	/** @return This search's type */
	public abstract SearchType getType();

	/**
	 * Short-hand for creating expressions
	 * 
	 * @param srch The search to or with this search
	 * @return The or expression search
	 */
	public ExpressionSearch or(Search srch)
	{
		return new ExpressionSearch(false).addOps(this, srch);
	}

	/**
	 * Short-hand for creating expressions
	 * 
	 * @param srch The search to and with this search
	 * @return The and expression search
	 */
	public ExpressionSearch and(Search srch)
	{
		return new ExpressionSearch(true).addOps(this, srch);
	}

	@Override
	public Search clone()
	{
		Search ret;
		try
		{
			ret = (Search) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException(e.getMessage(), e);
		}
		return ret;
	}

	@Override
	public abstract String toString();

	/** Builds a search from a string representation */
	protected static abstract class SearchBuilder
	{
		/**
		 * Compiles a search structure from a search string
		 * 
		 * @param searchText The search text
		 * @return The compiled search structure
		 */
		public Search createSearch(String searchText)
		{
			StringBuilder sb = new StringBuilder(searchText);
			trim(sb);
			Search current = null;
			while(sb.length() > 0)
			{
				boolean not = false;
				if(sb.length() > 1 && lower(sb.charAt(0)) == 'o' && lower(sb.charAt(1)) == 'r')
				{ // OR operator
					sb.delete(0, 2);
					ExpressionSearch exp = exp(false);
					exp.add(current);
					current = exp;
					trim(sb);
					continue;
				}
				else if(sb.length() > 2 && lower(sb.charAt(0)) == 'a' && lower(sb.charAt(1)) == 'n'
					&& lower(sb.charAt(2)) == 'd')
				{
					sb.delete(0, 3);
					trim(sb);
				}
				else if(sb.length() > 1 && sb.charAt(0) == '!')
				{
					sb.delete(0, 1);
					trim(sb);
					not = true;
				}

				Search next;
				if(sb.charAt(0) == '(')
				{
					int c = goPastParen(sb, 0);
					String nextExpr = sb.substring(1, c).trim();
					if(nextExpr.length() == 0)
						throw new IllegalArgumentException("Empty parenthetical expression");
					next = createSearch(nextExpr);
					sb.delete(0, c + 1);
				}
				else
					next = parseNext(sb);
				if(not)
				{
					NotSearch notS = not();
					notS.setOperand(next);
					next = notS;
				}

				if(current instanceof ExpressionSearch
					&& ((ExpressionSearch) current).getOperandCount() == 1)
					((ExpressionSearch) current).add(next);
				else if(current != null)
				{
					ExpressionSearch exp = exp(true);
					exp.addOps(current, next);
					current = exp;
				}
				else
					current = next;

				trim(sb);
			}
			if(current instanceof ExpressionSearch)
				((ExpressionSearch) current).simplify();
			return current;
		}

		/**
		 * Creates an expression search for this builder
		 * 
		 * @param and Whether the expression should be an AND or an OR
		 * @return the expression
		 */
		public ExpressionSearch exp(boolean and)
		{
			return new ExpressionSearch(and);
		}

		/**
		 * Creates a not search for this builder
		 * 
		 * @return The not search
		 */
		public NotSearch not()
		{
			return new NotSearch();
		}

		/**
		 * @return All search types that may be parsed out of this search builder
		 */
		public abstract SearchType [] getAllTypes();

		/**
		 * Parses the next non-expression search
		 * 
		 * @param sb The character sequence to parse the search out of
		 * @return The parsed search
		 */
		public Search parseNext(StringBuilder sb)
		{
			for(SearchType type : getAllTypes())
			{
				for(String header : type.getHeaders())
				{
					if(sb.length() < header.length())
						continue;
					boolean hasHeader = true;
					int c;
					for(c = 0; c < header.length(); c++)
						if(lower(sb.charAt(c)) != header.charAt(c))
						{
							hasHeader = false;
							break;
						}
					if(hasHeader)
					{
						trim(sb);
						for(; c < sb.length(); c++)
						{
							if(Character.isWhitespace(sb.charAt(c)))
								break;
							else if(sb.charAt(c) == '\"')
								c = goPastQuote(sb, c);
							else if(sb.charAt(c) == '(')
								c = goPastParen(sb, c);
						}
						Search ret = type.create(sb.substring(0, c), this);
						sb.delete(0, c + 1);
						return ret;
					}
				}
			}
			return defaultSearch(sb);
		}

		/**
		 * Parses a search without a header
		 * 
		 * @param sb The character sequence to parse the search out of
		 * @return The search
		 */
		public abstract Search defaultSearch(StringBuilder sb);

		/**
		 * Gets the position of the close parenthesis matching the open parenthesis at the given
		 * index
		 * 
		 * @param sb The character sequence to find the parenthesis in
		 * @param c The position of the open parenthesis
		 * @return The position of the closing parenthesis to match the given open one
		 */
		public int goPastParen(StringBuilder sb, int c)
		{
			boolean esc = false;
			int depth = 1;
			for(c++; c < sb.length(); c++)
			{
				if(!esc)
				{
					if(sb.charAt(c) == '(')
						depth++;
					else if(sb.charAt(c) == ')')
					{
						depth--;
						if(depth == 0)
							break;
					}
					else if(sb.charAt(c) == '\\')
						esc = true;
				}
				else
					esc = false;
			}
			if(depth == 0)
				return c;
			else
				throw new IllegalArgumentException("Unclosed parenthesis!");
		}

		/**
		 * Gets the position of the closing quote matching the open quote at the given index
		 * 
		 * @param sb The character sequence to find the quote in
		 * @param c The position of the open quote
		 * @return The position of the closing quote to match the given open one
		 */
		public int goPastQuote(StringBuilder sb, int c)
		{
			boolean esc = false;
			for(c++; c < sb.length(); c++)
			{
				if(!esc && sb.charAt(c) == '"')
					break;
				else if(!esc && sb.charAt(c) == '\\')
					esc = true;
				else
					esc = false;
			}
			if(!esc && sb.charAt(c) == '\"')
				return c;
			else
				throw new IllegalArgumentException("Unterminated end quote");
		}
	}

	/**
	 * Forces a character to be lower-case
	 * 
	 * @param c The character
	 * @return The lower-case character
	 */
	public static char lower(char c)
	{
		if(c >= 'A' && c <= 'Z')
			return (char) (c - 'A' + 'a');
		else
			return c;
	}

	/**
	 * Trims white space off of a string builder on both ends
	 * 
	 * @param sb The string builder to trim
	 */
	public static void trim(StringBuilder sb)
	{
		int i;
		for(i = 0; i < sb.length() && Character.isWhitespace(sb.charAt(i)); i++);
		if(i > 0)
			sb.delete(0, i);
		for(i = sb.length() - 1; i >= 0 && Character.isWhitespace(sb.charAt(i)); i--);
		if(i < sb.length() - 1)
			sb.delete(i + 1, sb.length());
	}
}
