/*
 * PreparedUpdate.java Created Jul 20, 2011 by Andrew Butler, PSL
 */
package prisms.osql;

/** An insert, update, or delete statement that is prepared for optimized multiple calls */
public class PreparedUpdate extends PreparedCall
{
	PreparedUpdate(Connection conn, String sql)
	{
		super(conn, sql);
	}
}
