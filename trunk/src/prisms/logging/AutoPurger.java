/*
 * AutoPurger.java Created Aug 11, 2011 by Andrew Butler, PSL
 */
package prisms.logging;

import prisms.util.ArrayUtils;

/**
 * A configuration with which {@link PrismsLogger} purges log entries automatically to keep the
 * database size manageable
 */
public class AutoPurger implements prisms.util.Sealable, Cloneable
{
	private volatile boolean isSealed;

	private long theMaxAge;

	private int theMaxSize; // In entries (approx. 1KB/entry)

	private String [] theExcludeLoggers;

	/** Creates an auto purger */
	public AutoPurger()
	{
		theExcludeLoggers = new String [0];
	}

	public boolean isSealed()
	{
		return isSealed;
	}

	public void seal()
	{
		isSealed = true;
	}

	void assertUnsealed()
	{
		if(isSealed)
			throw new SealedException("This auto-purger is sealed and cannot be modified");
	}

	/** @return The maximum age of entries in the logging database before they are purged */
	public final long getMaxAge()
	{
		return theMaxAge;
	}

	/** @param age The maximum age of entries in the logging database before they are purged */
	public void setMaxAge(long age)
	{
		assertUnsealed();
		theMaxAge = age;
	}

	/** @return The maximum size of the logging database before entries are purged */
	public final int getMaxSize()
	{
		return theMaxSize;
	}

	/** @param size The maximum size of the logging database before entries are purged */
	public void setMaxSize(int size)
	{
		assertUnsealed();
		theMaxSize = size;
	}

	/** @return All loggers that are excluded from auto-purge */
	public final String [] getExcludeLoggers()
	{
		return theExcludeLoggers;
	}

	/**
	 * Adds a logger to the set of loggers that are excluded from auto-purge
	 * 
	 * @param logger The logger to forbid purging of
	 */
	public void excludeLogger(String logger)
	{
		assertUnsealed();
		if(!ArrayUtils.contains(theExcludeLoggers, logger))
			theExcludeLoggers = ArrayUtils.add(theExcludeLoggers, logger);
	}

	/**
	 * Removes a logger from the set of loggers that are excluded from auto-purge
	 * 
	 * @param logger The logger to allow purging of
	 */
	public void includeLogger(String logger)
	{
		assertUnsealed();
		theExcludeLoggers = ArrayUtils.remove(theExcludeLoggers, logger);
	}

	@Override
	public AutoPurger clone()
	{
		AutoPurger ret;
		try
		{
			ret = (AutoPurger) super.clone();
		} catch(CloneNotSupportedException e)
		{
			throw new IllegalStateException(e.getMessage());
		}
		ret.isSealed = false;
		return ret;
	}
}
