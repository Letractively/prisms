/*
 * PrismsPasswordResetter.java Created Nov 12, 2010 by Andrew Butler, PSL
 */
package prisms.util;

import prisms.arch.PrismsException;

/** A utility to reset passwords in a PRISMS database */
public class PrismsPasswordResetter
{
	private int theCenterID;

	private prisms.arch.ds.Hashing theHashing;

	/**
	 * Creates an uninitialized password resetter. The information necessary to reset passwords
	 * within PRISMS will be gathered as needed from the
	 * {@link #setPassword(java.sql.Statement, String, String, long, String, Integer)} method.
	 */
	public PrismsPasswordResetter()
	{
		theCenterID = -1;
	}

	/**
	 * Creates an initialized password resetter. If the parameters in this constructor are available
	 * to the calling method, this constructor should be used to save the first invocation of
	 * {@link #setPassword(java.sql.Statement, String, String, long, String, Integer)} from having
	 * to re-generate the information
	 * 
	 * @param hashing The hashing logon information for the PRISMS installation
	 * @param centerID The local center ID for the PRISMS installation
	 */
	public PrismsPasswordResetter(prisms.arch.ds.Hashing hashing, int centerID)
	{
		theHashing = hashing;
		theCenterID = centerID;
	}

	/**
	 * Sets the password of a given user directly into the database
	 * 
	 * @param stmt The database statement to use to get and set the necessary data
	 * @param prefix The prefix to use before table names in the SQL
	 * @param userName The name of the user to set the password of. This may be null if userID is
	 *        provided, but if userID is not provided an extra SQL call will need to be made,
	 *        impacting performance slightly.
	 * @param userID The ID of the user to set the password of. This may be negative if userName is
	 *        provided, but if userID is not provided an extra SQL call will need to be made,
	 *        impacting performance slightly.
	 * @param password The password to set for the given user
	 * @param expireMinutes The number of minutes after which the set password will expire,
	 *        requiring the user to reset the password again. This may be null to set a password
	 *        that will not expire.
	 * @throws PrismsException If an error occurs setting the password information
	 */
	public void setPassword(java.sql.Statement stmt, String prefix, String userName, long userID,
		String password, Integer expireMinutes) throws PrismsException
	{
		String sql = null;
		java.sql.ResultSet rs = null;
		try
		{
			if(theCenterID < 0)
			{
				sql = "SELECT centerID FROM " + prefix + "prisms_installation";
				rs = stmt.executeQuery(sql);
				if(!rs.next())
					throw new IllegalStateException(
						"PRISMS has not been installed--no users to reset passwords");
				theCenterID = rs.getInt(1);
				rs.close();
				rs = null;
			}
		} catch(java.sql.SQLException e)
		{
			throw new PrismsException("Could not query center ID", e);
		}

		try
		{
			if(theHashing == null)
			{
				sql = "SELECT multiple, modulus FROM " + prefix + "prisms_hashing ORDER BY id";
				LongList mults = new LongList();
				LongList mods = new LongList();
				rs = stmt.executeQuery(sql);
				while(rs.next())
				{
					mults.add(rs.getLong(1));
					mods.add(rs.getLong(2));
				}
				rs.close();
				rs = null;
				theHashing = new prisms.arch.ds.Hashing();
				theHashing.setPrimaryHashing(mults.toArray(), mods.toArray());
				mults = null;
				mods = null;
			}
		} catch(java.sql.SQLException e)
		{
			throw new PrismsException("Could not query hashing information", e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(java.sql.SQLException e)
				{
					System.err.print("Connection error: ");
					e.printStackTrace();
				}
		}

		try
		{
			if(userID < 0)
			{
				sql = "SELECT id FROM " + prefix + "prisms_user WHERE id>="
					+ prisms.arch.ds.IDGenerator.getMinID(theCenterID) + " AND id<="
					+ prisms.arch.ds.IDGenerator.getMaxID(theCenterID) + " AND deleted="
					+ DBUtils.boolToSql(false) + " AND userName=" + DBUtils.toSQL(userName);
				rs = stmt.executeQuery(sql);
				if(!rs.next())
				{
					rs.close();
					rs = null;
					throw new PrismsException("No such local, non-deleted user named " + userName);
				}
				userID = rs.getLong(1);
				rs.close();
				rs = null;
			}
			long pwdExpire = -1;
			if(expireMinutes != null)
				pwdExpire = System.currentTimeMillis() + expireMinutes.intValue() * 60L * 1000;
			String pwdData = prisms.impl.DBUserSource.join(theHashing.partialHash(password));
			int nextPwdID = prisms.arch.ds.IDGenerator.getNextIntID(stmt, prefix
				+ "prisms_user_password", "id", null);
			sql = "INSERT INTO " + prefix + "prisms_user_password (id, pwdUser, pwdData, pwdTime,"
				+ " pwdExpire) VALUES (" + nextPwdID + ", " + userID + ", "
				+ DBUtils.toSQL(pwdData) + ", " + System.currentTimeMillis() + ", "
				+ (pwdExpire > 0 ? "" + pwdExpire : "NULL") + ")";
			stmt.executeUpdate(sql);
		} catch(java.sql.SQLException e)
		{
			String message = "Could not reset password of user ";
			if(userName != null)
				message += userName;
			else
				message += "with ID " + userID;
			throw new PrismsException(message + ": SQL=" + sql, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(java.sql.SQLException e)
				{
					System.err.print("Connection error: ");
					e.printStackTrace();
				}
		}
	}

	/**
	 * The main method that does the password-resetting from an XML file. Since this method operates
	 * straight through an SQL connection, the PRISMS server must be brought down and restarted in
	 * order for this method's effects to be seen in the server.
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args)
	{
		org.dom4j.Element root;
		try
		{
			root = PrismsUtils.getRootElement("PasswordReset.xml",
				PrismsUtils.getLocation(PrismsPasswordResetter.class));
		} catch(java.io.IOException e)
		{
			throw new IllegalArgumentException("No PasswordReset.xml", e);
		}
		prisms.impl.DefaultPersisterFactory factory = new prisms.impl.DefaultPersisterFactory();
		factory.configure(root.element("persisterFactory"));
		org.dom4j.Element connEl = root.element("connection");
		java.sql.Connection conn = factory.getConnection(connEl);
		String prefix = factory.getTablePrefix(conn, connEl);
		PrismsPasswordResetter resetter = new PrismsPasswordResetter();
		java.sql.Statement stmt = null;
		try
		{
			stmt = conn.createStatement();

			for(org.dom4j.Element userEl : (java.util.List<org.dom4j.Element>) root
				.elements("user"))
			{
				String userName = userEl.attributeValue("name");
				String password = userEl.attributeValue("password");
				Integer expireMinutes = userEl.attributeValue("expire") == null ? null
					: new Integer(userEl.attributeValue("expire"));
				try
				{
					resetter.setPassword(stmt, prefix, userName, -1, password, expireMinutes);
					System.out.println("Reset password of user " + userName);
				} catch(PrismsException e)
				{
					e.printStackTrace();
				}
			}
		} catch(java.sql.SQLException e)
		{
			throw new IllegalArgumentException("Could not create statement", e);
		} finally
		{
			if(stmt != null)
				try
				{
					stmt.close();
				} catch(java.sql.SQLException e)
				{}
			factory.disconnect(conn, connEl);
			factory.destroy();
		}
		System.out.println("Finished setting passwords");
	}
}
