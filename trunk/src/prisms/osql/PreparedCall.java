/*
 * PreparedCall.java Created Jul 20, 2011 by Andrew Butler, PSL
 */
package prisms.osql;

/**
 * A prepared call is a database call that is prepared so that it can be executed multiple times
 * without needing to be compiled multiple times
 */
public class PreparedCall
{
	private final Connection theConn;

	private final String theSQL;

	private int theConnID;

	@SuppressWarnings("unused")
	private java.sql.PreparedStatement thePrepared;

	PreparedCall(Connection conn, String sql)
	{
		theConn = conn;
		theSQL = sql;
		theConnID = -1;
	}

	void update(int connID) throws PrismsSqlException
	{
		if(theConnID == connID)
			return;
		theConnID = connID;
		try
		{
			thePrepared = theConn.getSqlConnection().prepareStatement(theSQL);
		} catch(java.sql.SQLException e)
		{
			throw new PrismsSqlException("Could not prepare statement", e);
		}
	}

	/** @return The SQL that this prepared call represents */
	public String getSQL()
	{
		return theSQL;
	}
}
