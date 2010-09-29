package prisms.records2;

/** Represents the last change received for a given center */
public class LatestCenterChange
{
	private final int theCenterID;

	private final int theSubjectCenter;

	private long theLatestChange;

	/**
	 * @param centerID The ID of the center the change is for
	 * @param subjectCenter The ID of the center whose data set was modified
	 * @param latestChange The time of the last change recorded for the given center
	 */
	public LatestCenterChange(int centerID, int subjectCenter, long latestChange)
	{
		theCenterID = centerID;
		theSubjectCenter = subjectCenter;
		theLatestChange = latestChange;
	}

	/** @return The ID of the center that caused the change */
	public int getCenterID()
	{
		return theCenterID;
	}

	/** @return The ID of the center whose data set was modified */
	public int getSubjectCenter()
	{
		return theSubjectCenter;
	}

	/** @return The time of the last change recorded by the given center against the given data set */
	public long getLatestChange()
	{
		return theLatestChange;
	}

	/**
	 * @param latestChange The time of the last change recorded by the given center against the
	 *        given data set
	 */
	public void setLatestChange(long latestChange)
	{
		theLatestChange = latestChange;
	}
}
