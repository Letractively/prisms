/*
 * UnlLoader.java Created Sep 28, 2005 by Andrew Butler, PSL
 */
package prisms.util;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.log4j.Logger;

/**
 * A simple UNL loader that reads a UNL file and writes its contents to a database given a template
 * statement. The main method uses command-line arguments to simplify calling the utility method
 */
public class UnlLoader
{
	private static final Logger log = Logger.getLogger(UnlLoader.class);

	private static final java.util.regex.Pattern DATE_PATTERN = java.util.regex.Pattern
		.compile("\\d{1,2}/\\d{1,2}/\\d{2,4}");

	private static final java.text.DateFormat DATE_FORMAT = new java.text.SimpleDateFormat("M/d/y");

	/**
	 * Inserts UNL data into a database
	 * 
	 * @param con The connection to use
	 * @param stmt The statement to execute
	 * @param in The reader providing UNL data
	 * @param columnCount The number of columns in the UNL file
	 * @throws IOException If an error occurs reading the UNL data
	 * @throws SQLException If the data cannot be inserted
	 */
	public static void insertUNL(java.sql.Connection con, java.sql.PreparedStatement stmt,
		java.io.Reader in, int columnCount) throws IOException, SQLException
	{
		int nextChar = in.read();
		int row, col;
		for(row = 0; nextChar >= 0; row++)
		{
			for(col = 0; col < columnCount; col++)
			{
				StringBuilder nextArg = new StringBuilder();
				while(nextChar >= 0 && nextChar != '|' && nextChar != '\n' && nextChar != '\r')
				{
					nextArg.append((char) nextChar);
					nextChar = in.read();
				}
				try
				{
					if(DATE_PATTERN.matcher(nextArg).matches())
					{
						try
						{
							stmt.setDate(col + 1,
								new java.sql.Date(DATE_FORMAT.parse(nextArg.toString()).getTime()));
						} catch(java.text.ParseException e)
						{
							log.error("Could not parse date properly", e);
							throw new SQLException("Could not parse date" + " properly: " + nextArg);
						}
					}
					else if(nextArg.length() == 0)
						// stmt.setNull(col + 1, java.sql.Types.NULL);
						stmt.setNull(col + 1, java.sql.Types.FLOAT);
					else
						stmt.setString(col + 1, nextArg.toString());
				} catch(SQLException e)
				{
					log.error("Row " + row + ", Column " + (col + 1) + "; arg=" + nextArg);
					throw e;
				}
				if(nextChar == '|')
					nextChar = in.read();
			}
			stmt.execute();
			while(nextChar >= 0 && nextChar != '\n' && nextChar != '\r')
				nextChar = in.read();
			while(nextChar == '\n' || nextChar == '\r')
				nextChar = in.read();
		}
	}

	/**
	 * Performs a load for one UNL file
	 * 
	 * @param con The JDBC connection to use
	 * @param tableName The table name to insert into
	 * @param colNames The names of the columns to insert data into
	 * @param unl The UNL data to read
	 * @return True if and only if all the UNL data from the reader was inserted successfully into
	 *         the table.
	 */
	public static boolean doLoad(java.sql.Connection con, String tableName, String [] colNames,
		java.io.Reader unl)
	{
		int i;
		StringBuffer stmtBfr = new StringBuffer("INSERT INTO ");
		stmtBfr.append(tableName);
		stmtBfr.append(" (");
		for(i = 0; i < colNames.length; i++)
		{
			stmtBfr.append(colNames[i]);
			if(i < colNames.length - 1)
				stmtBfr.append(", ");
		}
		stmtBfr.append(") VALUES (");
		for(i = 0; i < colNames.length; i++)
		{
			stmtBfr.append("?");
			if(i < colNames.length - 1)
				stmtBfr.append(", ");
		}
		stmtBfr.append(")");
		java.sql.PreparedStatement stmt;
		try
		{
			stmt = con.prepareStatement(stmtBfr.toString());
		} catch(SQLException e)
		{
			log.error("Could not prepare UNL insertion statement", e);
			try
			{
				con.close();
			} catch(SQLException e2)
			{
				log.error("Could not close connection", e2);
			}
			return false;
		}
		try
		{
			java.sql.Statement delStmt = con.createStatement();
			try
			{
				delStmt.execute("DELETE FROM " + tableName);
			} finally
			{
				delStmt.close();
			}
			insertUNL(con, stmt, unl, colNames.length);
		} catch(IOException e)
		{
			log.error("Could not read UNL file", e);
			return false;
		} catch(SQLException e)
		{
			try
			{
				con.rollback();
			} catch(SQLException e2)
			{
				log.error("Could not rollback", e2);
			}
			log.error("Could not execute SQL", e);
			return false;
		}
		return true;
	}

	/**
	 * @param args This method takes 5 arguments in sequence:
	 *        <ol>
	 *        <li>The UnlLoader file to read: a properties file mapping all UNL files to the table
	 *        name to write to, a semicolon, and a comma-separated list of the columns that each
	 *        datum in the rows of the UNL file represents</li>
	 *        <li>The qualified class name of the JDBC driver to use</li>
	 *        <li>The JDBC URL to connect to the database with</li>
	 *        <li>The user name to connect to the database with</li>
	 *        <li>The password to connect to the database with</li>
	 *        </ol>
	 */
	public static void main(String [] args)
	{
		prisms.arch.PrismsServer.initLog4j(prisms.arch.PrismsServer.class.getResource("log4j.xml"));
		if(args.length != 5)
			throw new IllegalArgumentException("UnlLoader expects 5 arguments:"
				+ "The name of the UnlLoader file to read and the JDBC driver"
				+ " name, URL, user name, and password");
		String loadFile = args[0];
		String driverName = args[1];
		String url = args[2];
		String userName = args[3];
		String password = args[4];
		java.sql.Connection con;
		try
		{
			Class.forName(driverName);
		} catch(Throwable e)
		{
			log.error("Could not load JDBC Driver " + driverName, e);
			return;
		}
		try
		{
			con = java.sql.DriverManager.getConnection(url, userName, password);
		} catch(SQLException e)
		{
			log.error("Could not connect to database " + url, e);
			return;
		}
		java.util.Properties loadProps = new java.util.Properties();
		java.io.InputStream loadIS;
		try
		{
			loadIS = new java.io.BufferedInputStream(new java.io.FileInputStream(loadFile));
		} catch(java.io.IOException e)
		{
			log.error("Could not open UnlLoader file " + loadFile, e);
			try
			{
				con.close();
			} catch(SQLException e2)
			{
				log.error("Could not close connection", e2);
			}
			return;
		}
		try
		{
			loadProps.load(loadIS);
		} catch(java.io.IOException e)
		{
			log.error("Could not read UnlLoader file " + loadFile, e);
			try
			{
				con.close();
			} catch(SQLException e2)
			{
				log.error("Could not close connection", e2);
			}
			return;
		}
		java.util.Iterator<Object> unlFiles = loadProps.keySet().iterator();
		try
		{
			while(unlFiles.hasNext())
			{
				String unlFile = (String) unlFiles.next();
				java.io.Reader unlReader;
				try
				{
					unlReader = new java.io.BufferedReader(new java.io.FileReader(unlFile));
				} catch(java.io.IOException e)
				{
					log.error("Could not read UNL file " + unlFile, e);
					continue;
				}
				String unlProp = loadProps.getProperty(unlFile);
				String tableName = unlProp.substring(0, unlProp.indexOf(";"));
				unlProp = unlProp.substring(tableName.length() + 1);
				String [] colNames = unlProp.split(",");
				for(int col = 0; col < colNames.length; col++)
					colNames[col] = colNames[col].trim();
				if(!doLoad(con, tableName, colNames, unlReader))
					log.error("Load unsuccessful for UNL file " + unlFile);
			}
		} finally
		{
			if(driverName.indexOf("hsql") >= 0)
			{
				try
				{
					java.sql.Statement sdStmt = con.createStatement();
					try
					{
						sdStmt.execute("SHUTDOWN");
					} finally
					{
						sdStmt.close();
					}
				} catch(SQLException e)
				{
					log.error("Could not execute HSQL shutdown", e);
				}
			}
			try
			{
				con.close();
			} catch(SQLException e)
			{
				log.error("Could not close connection", e);
			}
		}
	}
}
