/*
 * PasswordConstraints.java Created Feb 19, 2010 by Andrew Butler, PSL
 */
package prisms.arch.ds;

/** Defines constraints that may be placed on passwords within PRISMS */
public class PasswordConstraints
{
	private boolean isLocked;

	private int theMinCharacterLength;

	private int theMinUpperCase;

	private int theMinLowerCase;

	private int theMinDigits;

	private int theMinSpecialChars;

	private long theMaxPasswordDuration;

	private int theNumPreviousUnique;

	private long theMinPasswordChangeInterval;

	/** Creates a PasswordConstraints object with no constraints */
	public PasswordConstraints()
	{
		theMaxPasswordDuration = -1;
	}

	/**
	 * @return The number of constraints on passwords (not including the maximum password duration
	 *         and minimum password change interval)
	 */
	public int getNumConstraints()
	{
		int count = 0;
		if(theMinCharacterLength > 0)
			count++;
		if(theMinUpperCase > 0)
			count++;
		if(theMinLowerCase > 0)
			count++;
		if(theMinDigits > 0)
			count++;
		if(theMinSpecialChars > 0)
			count++;
		if(theNumPreviousUnique > 0)
			count++;
		return count;
	}

	/** @return Whether these constraints are permanently locked so that they cannot be modified */
	public boolean isLocked()
	{
		return isLocked;
	}

	/** Locks these password constraints so that they cannot be modified */
	public void lock()
	{
		isLocked = true;
	}

	/** @return The minimum number of characters a password can contain */
	public int getMinCharacterLength()
	{
		return theMinCharacterLength;
	}

	/** @param length The minimum number of characters a password should contain */
	public void setMinCharacterLength(int length)
	{
		if(isLocked)
			throw new IllegalArgumentException("Password constraints are locked--cannot be changed");
		theMinCharacterLength = length;
	}

	/** @return The minimum number of upper-case characters a password can contain */
	public int getMinUpperCase()
	{
		return theMinUpperCase;
	}

	/** @param count The minimum number of upper-case characters a password should contain */
	public void setMinUpperCase(int count)
	{
		if(isLocked)
			throw new IllegalArgumentException("Password constraints are locked--cannot be changed");
		theMinUpperCase = count;
	}

	/** @return The minimum number of lower-case characters a password can contain */
	public int getMinLowerCase()
	{
		return theMinLowerCase;
	}

	/** @param count The minimum number of lower-case characters a password should contain */
	public void setMinLowerCase(int count)
	{
		if(isLocked)
			throw new IllegalArgumentException("Password constraints are locked--cannot be changed");
		theMinLowerCase = count;
	}

	/** @return The minimum number of decimal digits a password can contain */
	public int getMinDigits()
	{
		return theMinDigits;
	}

	/** @param count The minimum number of decimal digits a password should contain */
	public void setMinDigits(int count)
	{
		if(isLocked)
			throw new IllegalArgumentException("Password constraints are locked--cannot be changed");
		theMinDigits = count;
	}

	/** @return The minimum number of special characters a password can contain */
	public int getMinSpecialChars()
	{
		return theMinSpecialChars;
	}

	/** @param count The minimum number of special characters a password should contain */
	public void setMinSpecialChars(int count)
	{
		if(isLocked)
			throw new IllegalArgumentException("Password constraints are locked--cannot be changed");
		theMinSpecialChars = count;
	}

	/**
	 * @return The maximum amount of time a password can be valid before requiring the user to
	 *         change it
	 */
	public long getMaxPasswordDuration()
	{
		return theMaxPasswordDuration;
	}

	/**
	 * @param duration The maximum amound of time a password should be valid before requiring the
	 *        user to change it
	 */
	public void setMaxPasswordDuration(long duration)
	{
		if(isLocked)
			throw new IllegalArgumentException("Password constraints are locked--cannot be changed");
		theMaxPasswordDuration = duration;
	}

	/** @return The number of previous passwords a user may not reuse */
	public int getNumPreviousUnique()
	{
		return theNumPreviousUnique;
	}

	/** @param count The number of previous passwords a user should not be able to reuse */
	public void setNumPreviousUnique(int count)
	{
		if(isLocked)
			throw new IllegalArgumentException("Password constraints are locked--cannot be changed");
		theNumPreviousUnique = count;
	}

	/**
	 * @return The minimum time interval that may pass between a user changing his password (does
	 *         not apply to administrative password changes)
	 */
	public long getMinPasswordChangeInterval()
	{
		return theMinPasswordChangeInterval;
	}

	/**
	 * @param interval The minimum time interval that should pass between a user changing his
	 *        password (does not apply to administrative password changes)
	 */
	public void setMinPasswordChangeInterval(long interval)
	{
		if(isLocked)
			throw new IllegalArgumentException("Password constraints are locked--cannot be changed");
		theMinPasswordChangeInterval = interval;
	}
}
