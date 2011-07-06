/*
 * PrismsCenter.java Created Mar 1, 2010 by Andrew Butler, PSL
 */
package prisms.records;

/**
 * Represents a center with which the local center can synchronize items or that can synchronize
 * items from the local center.
 */
public class PrismsCenter
{
	private String theNamespace;

	private int theID;

	private int theCenterID;

	private String theName;

	private String theServerURL;

	private String theServerUserName;

	private String theServerPassword;

	private long theServerSyncFrequency;

	private RecordUser theClientUser;

	private long theChangeSaveTime;

	private int thePriority;

	private long theLastImport;

	private long theLastExport;

	private java.security.cert.X509Certificate[] theCerts;

	boolean isDeleted;

	/**
	 * Creates a center
	 * 
	 * @param id The local center ID
	 * @param name The name of the center
	 */
	public PrismsCenter(int id, String name)
	{
		theID = id;
		theCenterID = -1;
		theName = name;
		theServerSyncFrequency = -1;
		theChangeSaveTime = 10L * 3651 * 24 * 60 * 60 * 1000;
	}

	/**
	 * Creates a center
	 * 
	 * @param name The name of the center
	 */
	public PrismsCenter(String name)
	{
		this(-1, name);
	}

	/** @return The namespace that this view of the PRISMS center is in */
	public String getNamespace()
	{
		return theNamespace;
	}

	/** @param namespace The namespace that this view of the PRISMS center is in */
	public void setNamespace(String namespace)
	{
		theNamespace = namespace;
	}

	/** @return The local database ID for the center */
	public int getID()
	{
		return theID;
	}

	/** @param id The local database ID for the center */
	public void setID(int id)
	{
		theID = id;
	}

	/** @return The center's unique identifier */
	public int getCenterID()
	{
		return theCenterID;
	}

	/** @param centerID The center's unique identifier */
	public void setCenterID(int centerID)
	{
		theCenterID = centerID;
	}

	/** @return The local name for this center */
	public String getName()
	{
		return theName;
	}

	/** @param name The local name for this center */
	public void setName(String name)
	{
		theName = name;
	}

	/** @return The URL to use to synchronize with this center */
	public String getServerURL()
	{
		return theServerURL;
	}

	/** @param url The URL to use to synchronize with this center */
	public void setServerURL(String url)
	{
		theServerURL = url;
	}

	/** @return The server certificates recognized for this center's remote server */
	public java.security.cert.X509Certificate[] getCertificates()
	{
		return theCerts;
	}

	/** @param certs The server certificates to recognize for this center's remote server */
	public void setCertificates(java.security.cert.X509Certificate[] certs)
	{
		theCerts = certs;
	}

	/** @return The user name to use to synchronize with this center */
	public String getServerUserName()
	{
		return theServerUserName;
	}

	/** @param userName The user name to use to synchronize with this center */
	public void setServerUserName(String userName)
	{
		theServerUserName = userName;
	}

	/** @return The password to use to synchronize with this center */
	public String getServerPassword()
	{
		return theServerPassword;
	}

	/** @param password The password to use to synchronize with this center */
	public void setServerPassword(String password)
	{
		theServerPassword = password;
	}

	/** @return The frequency, in milliseconds, between synchronizations with this center */
	public long getServerSyncFrequency()
	{
		return theServerSyncFrequency;
	}

	/** @param freq The frequency, in milliseconds, to set between synchronizations with this center */
	public void setServerSyncFrequency(long freq)
	{
		theServerSyncFrequency = freq;
	}

	/** @return The user that this center may use to synchronize with the local center */
	public RecordUser getClientUser()
	{
		return theClientUser;
	}

	/** @param user The user that this center may use to synchronize with the local center */
	public void setClientUser(RecordUser user)
	{
		theClientUser = user;
	}

	/**
	 * @return The length of time (in milliseconds) that the local server will prevent the purging
	 *         of changes so that this center can synchronize on them.
	 */
	public long getChangeSaveTime()
	{
		return theChangeSaveTime;
	}

	/**
	 * @param saveTime The length of time (in milliseconds) that the local server should prevent the
	 *        purging of changes so that this center can synchronize on them.
	 */
	public void setChangeSaveTime(long saveTime)
	{
		theChangeSaveTime = saveTime;
	}

	/**
	 * @return The priority of this center for synchronization. High-priority centers will never
	 *         have their data destroyed without record by lower-priority centers. Low-priority
	 *         centers may lose local changes irreversibly as a result of synchronizing with
	 *         higher-priority centers if the changes have been purged.
	 */
	public int getPriority()
	{
		return thePriority;
	}

	/**
	 * @param priority The priority for this center
	 * @see #setPriority(int)
	 */
	public void setPriority(int priority)
	{
		thePriority = priority;
	}

	/**
	 * @return The last time the local center synchronized with (imported changes from) this center
	 */
	public long getLastImport()
	{
		return theLastImport;
	}

	/**
	 * @param lastImport The last time the local center synchronized successfully with (imported
	 *        changes from) this center
	 */
	public void setLastImport(long lastImport)
	{
		theLastImport = lastImport;
	}

	/**
	 * @return The last time this center synchronized successfully with (imported changes from) the
	 *         local center
	 */
	public long getLastExport()
	{
		return theLastExport;
	}

	/**
	 * @param lastExport The last time this center synchronized with (imported changes from) the
	 *        local center
	 */
	public void setLastExport(long lastExport)
	{
		theLastExport = lastExport;
	}

	/** @return Whether this center has been deleted */
	public boolean isDeleted()
	{
		return isDeleted;
	}

	/** @param deleted Whether this center is deleted or not */
	public void setDeleted(boolean deleted)
	{
		isDeleted = deleted;
	}

	@Override
	public boolean equals(Object o)
	{
		if(!(o instanceof PrismsCenter))
			return false;
		return theID == ((PrismsCenter) o).theID;
	}

	@Override
	public int hashCode()
	{
		return theID;
	}

	@Override
	public String toString()
	{
		return theName;
	}
}
