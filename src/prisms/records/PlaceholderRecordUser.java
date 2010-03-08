/*
 * PlaceholderRecordUser.java Created Mar 3, 2010 by Andrew Butler, PSL
 */
package prisms.records;

class PlaceholderRecordUser implements RecordUser
{
	private final long theID;

	private final String theName;

	PlaceholderRecordUser(long id, String name)
	{
		theID = id;
		theName = name;
	}

	public long getID()
	{
		return theID;
	}

	public String getName()
	{
		return theName;
	}
}
