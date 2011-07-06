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
				sql = "SELECT * FROM " + prefix + "prisms_installation";
				rs = stmt.executeQuery(sql);
				if(!rs.next())
					throw new IllegalStateException(
						"PRISMS has not been installed--no users to reset passwords");
				try
				{
					theCenterID = rs.getInt("centerID");
				} catch(java.sql.SQLException e)
				{
					/* Probably means this is an older version of PRISMS with no centerID in the
					 * prisms_installation table */
					theCenterID = 0;
				}
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
			int nextPwdID = (int) prisms.arch.ds.IDGenerator.nextAvailableID(stmt, prefix
				+ "prisms_user_password", "id", 0, null);
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
		prisms.arch.PrismsConfig root;
		try
		{
			root = prisms.arch.PrismsConfig.fromXml(null, "PasswordReset.xml",
				prisms.arch.PrismsConfig.getLocation(PrismsPasswordResetter.class));
		} catch(java.io.IOException e)
		{
			throw new IllegalArgumentException("No PasswordReset.xml", e);
		}
		prisms.arch.ConnectionFactory factory = new prisms.impl.DefaultConnectionFactory();
		factory.configure(root.subConfig("connection-factory"));
		prisms.arch.PrismsConfig connEl = root.subConfig("connection");
		prisms.arch.ds.Transactor<java.sql.SQLException> trans = factory.getConnection(connEl, null,
			null);
		PrismsPasswordResetter resetter = new PrismsPasswordResetter();
		java.sql.Statement stmt = null;
		try
		{
			stmt = trans.getConnection().createStatement();

			for(prisms.arch.PrismsConfig userEl : root.subConfigs("user"))
			{
				String userName = userEl.get("name");
				String password = userEl.get("password");
				Integer expireMinutes = userEl.get("expire") == null ? null : new Integer(
					userEl.get("expire"));
				try
				{
					resetter.setPassword(stmt, trans.getTablePrefix(), userName, -1, password,
						expireMinutes);
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
			trans.release();
			factory.destroy();
		}
		System.out.println("Finished setting passwords");
	}
}
