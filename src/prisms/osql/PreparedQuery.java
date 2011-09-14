/*
 * PreparedQuery.java Created Jul 20, 2011 by Andrew Butler, PSL
 */
package prisms.osql;

/** A query that is prepared for optimized multiple calls */
public class PreparedQuery extends PreparedCall
{
	private final Query theQuery;

	PreparedQuery(Connection conn, Query query, String sql)
	{
		super(conn, sql);
		theQuery = query;
	}

	/** @return The query that this is prepared for */
	public Query getQuery()
	{
		return theQuery;
	}
}
