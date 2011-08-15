/*
 * 1 * DBUtils.java Created Nov 11, 2009 by Andrew Butler, PSL
 */
package prisms.util;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.Map;

import prisms.arch.PrismsException;

/** Contains database utility methods for general use */
public class DBUtils
{
	/** An enumeration of database flavors */
	public static enum ConnType
	{
		/** Oracle */
		ORACLE("oracle"),
		/** HyperSQL */
		HSQL("hsqldb"),
		/** Microsoft SQL Server */
		MSSQL("sqlserver"),
		/** IBM Informix */
		INFORMIX("informix"),
		/** MySQL */
		MYSQL("mysql"),
		/** SyBase */
		SYBASE("sybase"),
		/** PostgreSQL */
		POSTGRES("postgres"),
		/** Unknown type */
		UNKNOWN("unknown database flavor");

		/** The identifier that identifies a database connection's classname as this type */
		public final String identifier;

		ConnType(String id)
		{
			identifier = id.toLowerCase();
		}
	}

	private static final String HEX = "0123456789ABCDEF";

	/**
	 * Translates betwen an SQL character type and a boolean
	 * 
	 * @param b The SQL character representing a boolean
	 * @return The boolean represented by the character
	 */
	public static boolean boolFromSql(String b)
	{
		return "t".equalsIgnoreCase(b);
	}

	/**
	 * Translates betwen an SQL character type and a boolean
	 * 
	 * @param b The boolean to be represented by a character
	 * @return The SQL character representing the boolean
	 */
	public static String boolToSql(boolean b)
	{
		return b ? "'t'" : "'f'";
	}

	/**
	 * Same as {@link #boolToSql(boolean)} but for prepared statements where the bounding ticks are
	 * not needed
	 * 
	 * @param b The boolean to be represented by a character
	 * @return The SQL character representing the boolean
	 */
	public static String boolToSqlP(boolean b)
	{
		return b ? "t" : "f";
	}

	private static final String EMPTY = "*-{EMPTY}-*";

	/**
	 * Formats a generic string for entry into a database using SQL
	 * 
	 * @param str The general string to put into the database
	 * @return The string that should be appended to the SQL string
	 */
	public static String toSQL(String str)
	{
		if(str == null)
			return "NULL";
		else if(str.length() == 0)
			return toSQL(EMPTY);
		str = PrismsUtils.encodeUnicode(str);
		return "'" + str.replaceAll("'", "''") + "'";
	}

	/**
	 * Converts a DBMS-returned string into a java string
	 * 
	 * @param dbString The DBMS-returned string
	 * @return The java string to use
	 */
	public static String fromSQL(String dbString)
	{
		if(dbString == null)
			return null;
		else if(dbString.equals(EMPTY))
			return "";
		else
			return PrismsUtils.decodeUnicode(dbString);
	}

	/**
	 * Creates a timestamp that reflects the given time as a UTC time. This is important for data
	 * that must accurately be reflected across multiple databases.
	 * 
	 * @param time The time to represent
	 * @return A timestamp that reflects the given time in UTC.
	 */
	public static java.sql.Timestamp getUtcTimestamp(long time)
	{
		return new java.sql.Timestamp(time);
		// java.util.TimeZone here = java.util.Calendar.getInstance().getTimeZone();
		// return new java.sql.Timestamp(time - here.getOffset(time));
	}

	/**
	 * @param conn The JDBC connection to get the type of
	 * @return The JDBC connection type of the connection
	 */
	public static ConnType getType(java.sql.Connection conn)
	{
		String connClass = conn.getClass().getName().toLowerCase();
		for(ConnType type : ConnType.values())
			if(connClass.contains(type.identifier))
				return type;
		return ConnType.UNKNOWN;
	}

	/**
	 * Gets the name of the lower-case function that causes a string to take its all lower-case form
	 * in SQL
	 * 
	 * @param type The connection type
	 * @return The lower-case function for the given connection type
	 */
	public static String getLowerFn(ConnType type)
	{
		switch(type)
		{
		case ORACLE:
		case MSSQL:
		case POSTGRES:
		case INFORMIX:
		case SYBASE:
			return "LOWER";
		case HSQL:
		case MYSQL:
			return "LCASE";
		case UNKNOWN:
			throw new IllegalArgumentException(
				"Unknown connection type: no lower case function available");
		}
		throw new IllegalArgumentException("Unrecognized connection type: " + type);
	}

	/**
	 * Gets the name of the function that retrieves the length of a large object (LOB) in SQL
	 * 
	 * @param type The connection type
	 * @return The LOB-length function for the given connection type
	 */
	public static String getLobLengthFn(ConnType type)
	{
		switch(type)
		{
		case ORACLE:
			return "dbms_lob.getlength";
		case MSSQL:
		case SYBASE:
			return "datalength";
		case MYSQL:
		case HSQL:
			return "octet_length";
		case INFORMIX:
			return "length";
		case POSTGRES:
			throw new IllegalArgumentException("LOB length function unknown for PostgreSQL");
		case UNKNOWN:
			throw new IllegalArgumentException(
				"Unknown connection type: no lob length function available");
		}
		throw new IllegalArgumentException("Unrecognized connection type: " + type);
	}

	/**
	 * @param time the java time to format
	 * @param oracle Whether the connection is to an oracle database
	 * @return the sql expression of the java time
	 */
	public static String formatDate(long time, boolean oracle)
	{
		if(time <= 0)
			return "NULL";

		String ret = getUtcTimestamp(time).toString();

		if(oracle)
			ret = "TO_TIMESTAMP('" + ret + "', 'YYYY-MM-DD HH24:MI:SS.FF3')";
		// ret = "TO_DATE('" + ret.substring(0, ret.length()-4) + "', 'YYYY-MM-DD HH24:MI:SS')";
		else
			ret = "'" + ret + "'";

		return ret;
	}

	/**
	 * Constructs a query that retrieves only a specified number of rows, offset by a given value
	 * 
	 * @param connType The connection type to construct the query for
	 * @param columns The columns to select
	 * @param tables The tables to select from
	 * @param where The where selector (without "WHERE")
	 * @param order The order by clause (without "ORDER BY")
	 * @param offset The number of rows to skip (may be <=0 to skip no rows)
	 * @param limit The number of rows to retrieve (may be <=0 to not enforce a limit. Beware that
	 *        specifying an offset without a limit may not work with some databases.)
	 * @return The constructed query
	 */
	public static String addLimit(ConnType connType, String columns, String tables, String where,
		String order, int offset, int limit)
	{
		StringBuilder baseQuery = new StringBuilder("SELECT ").append(columns);
		baseQuery.append(" FROM ").append(tables);
		if(where != null)
			baseQuery.append(" WHERE ").append(where);
		if(order != null)
			baseQuery.append(" ORDER BY ").append(order);
		if(offset <= 0 && limit <= 0)
			return baseQuery.toString();
		switch(connType)
		{
		case HSQL:
			StringBuilder ret = new StringBuilder(baseQuery);
			if(limit > 0)
			{
				ret.append(" LIMIT ").append(limit);
				if(offset > 0)
					ret.append(" OFFSET ").append(offset);
			}
			else if(offset > 0)
				System.err.println("Offset requires limit in HSQL");
			return ret.toString();
		case ORACLE:
			ret = new StringBuilder("SELECT ").append(columns).append(", ROWNUM FROM (")
				.append(baseQuery).append(") WHERE ROWNUM");
			if(limit > 0)
			{
				if(offset > 0)
					ret.append(" BETWEEN ").append(offset + 1).append(" AND ")
						.append(offset + limit + 1);
				else
					ret.append("<=").append(limit);
			}
			else
				ret.append(">").append(offset);
			return ret.toString();
		case MSSQL:
			int rsID = (int) Math.round(Math.random() * Integer.MAX_VALUE);
			if(order != null)
			{
				ret = new StringBuilder("SELECT ").append(columns).append(" FROM (\n\tSELECT ")
					.append(columns).append(", ROW_NUMBER() OVER (").append(order)
					.append(") AS RowNumber FROM ").append(tables).append(") AS ResultSet")
					.append(rsID).append(" WHERE ResultSet").append(rsID);
				if(limit > 0)
				{
					if(offset > 0)
						ret.append(" BETWEEN ").append(offset + 1).append(" AND ")
							.append(offset + limit + 1);
					else
						ret.append("<=").append(limit);
				}
				else
					ret.append(">").append(offset);
			}
			else
			{
				ret = new StringBuilder("SELECT ").append(columns).append(" FROM ").append(tables)
					.append(" INTO #ResultSet").append(rsID);
				if(where != null)
					ret.append("WHERE ").append(where);
				ret.append(";\n\nSELECT * FROM (\n\tSELECT *, ROW_NUMBER() OVER")
					.append(" (ORDER BY SortConst ASC) As RowNumber FROM (\n\t\tSELECT *, 1")
					.append(" As SortConst FROM #ResultSet" + rsID + "\n\t) AS ResultSet\n)")
					.append(" AS Page WHERE RowNumber");

				if(limit > 0)
				{
					if(offset > 0)
						ret.append(" BETWEEN ").append(offset + 1).append(" AND ")
							.append(offset + limit + 1);
					else
						ret.append("<=").append(limit);
				}
				else
					ret.append(">").append(offset);

				ret.append(";\n\nDROP TABLE #ResultSet").append(rsID).append(';');
			}
			return ret.toString();
		default:
			// TODO Implement for more DBMS's
			throw new IllegalStateException("offset/limit not implemented for " + connType);
		}
	}

	/**
	 * @param conn The connection to test
	 * @return Whether the connection is to an oracle database
	 */
	public static boolean isOracle(java.sql.Connection conn)
	{
		return getType(conn) == ConnType.ORACLE;
	}

	/**
	 * Gets the maximum length of data for a field
	 * 
	 * @param conn The connection to get information from
	 * @param tableName The name of the table
	 * @param fieldName The name of the field to get the length for
	 * @return The maximum length that data in the given field can be
	 * @throws PrismsException If an error occurs retrieving the information, such as the table or
	 *         field not existing
	 */
	public static int getFieldSize(java.sql.Connection conn, String tableName, String fieldName)
		throws PrismsException
	{
		if(DBUtils.isOracle(conn))
			throw new PrismsException("Accessing Oracle metadata is unsafe--cannot get field size");
		java.sql.ResultSet rs = null;
		try
		{
			String schema = null;
			tableName = tableName.toUpperCase();
			int dotIdx = tableName.indexOf('.');
			if(dotIdx >= 0)
			{
				schema = tableName.substring(0, dotIdx).toUpperCase();
				tableName = tableName.substring(dotIdx + 1).toUpperCase();
			}
			rs = conn.getMetaData().getColumns(null, schema, tableName, null);
			while(rs.next())
			{
				String name = rs.getString("COLUMN_NAME");
				if(name.equalsIgnoreCase(fieldName))
					return rs.getInt("COLUMN_SIZE");
			}

			throw new PrismsException("No such field " + fieldName + " in table "
				+ (schema != null ? schema + "." : "") + tableName);

		} catch(SQLException e)
		{
			throw new PrismsException("Could not get field length of " + tableName + "."
				+ fieldName, e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(SQLException e)
				{
					System.err.println("Connection error");
					e.printStackTrace(System.err);
				}
		}
	}

	/**
	 * Puts binary data into a prepared statement as a BLOB
	 * 
	 * @param stmt The prepared statement
	 * @param index The index that the binary data goes in
	 * @param type The SQL type of the data
	 * @param input The binary data to put in the blob
	 * @throws SQLException If the data cannot be set for the statement
	 */
	public static void setBlob(java.sql.PreparedStatement stmt, int index, int type,
		java.io.InputStream input) throws SQLException
	{
		if(PrismsUtils.isJava6())
			throw new SQLException("Cannot set binary data in <JDK 6 machine");
		if(input == null)
			stmt.setNull(index, type);
		else
		{
			java.sql.Blob blob = stmt.getConnection().createBlob();
			java.io.OutputStream stream = blob.setBinaryStream(1);
			try
			{
				int read = input.read();
				while(read >= 0)
				{
					stream.write(read);
					read = input.read();
				}
				stream.close();
			} catch(java.io.IOException e)
			{
				throw new SQLException(e.getMessage(), e);
			}
			stmt.setBlob(index, blob);
		}
	}

	/**
	 * Puts character stream data into a prepared statement as a CLOB
	 * 
	 * @param stmt The prepared statement
	 * @param index The index that the character data goes in
	 * @param type The SQL type of the data
	 * @param input The character data to put in the blob
	 * @throws SQLException If the data cannot be set for the statement
	 */
	public static void setClob(java.sql.PreparedStatement stmt, int index, int type,
		java.io.Reader input) throws SQLException
	{
		if(PrismsUtils.isJava6())
			throw new SQLException("Cannot set CLOB in <JDK 6 machine");
		if(input == null)
			stmt.setNull(index, type);
		else
		{
			java.sql.Clob clob = stmt.getConnection().createClob();
			java.io.Writer stream = clob.setCharacterStream(1);
			try
			{
				int read = input.read();
				while(read >= 0)
				{
					stream.write(read);
					read = input.read();
				}
				stream.close();
			} catch(java.io.IOException e)
			{
				throw new SQLException(e.getMessage(), e);
			}
			stmt.setClob(index, clob);
		}
	}

	private static final String XOR_KEY = "PrIsMs_sYnC_xOr_EnCrYpT_kEy_769465";

	/**
	 * Protects a password so that it is not stored in clear text. The return value will be twice as
	 * long as the input to ensure that only ASCII characters are stored.
	 * 
	 * @param password The password to protect
	 * @return The protected password to store in the database
	 */
	public static String protect(String password)
	{
		if(password == null)
			return null;
		return toHex(xorEncStr(password, XOR_KEY));
	}

	/**
	 * Recovers a password from its protected form
	 * 
	 * @param protectedPassword The protected password to recover the password from
	 * @return The plain password
	 */
	public static String unprotect(String protectedPassword)
	{
		if(protectedPassword == null)
			return null;
		return xorEncStr(fromHex(protectedPassword), XOR_KEY);
	}

	/**
	 * Created by Matthew Shaffer (matt-shaffer.com)
	 * 
	 * This method uses simple xor encryption to encrypt a password with a key so that it is at
	 * least not stored in clear text.
	 * 
	 * @param toEnc The string to encrypt
	 * @param encKey The encryption key
	 * @return The encrypted string
	 */
	private static String xorEncStr(String toEnc, String encKey)
	{
		if(toEnc == null)
			return null;
		int t = 0;
		int encKeyI = 0;

		while(t < encKey.length())
		{
			encKeyI += encKey.charAt(t);
			t += 1;
		}
		return xorEnc(toEnc, encKeyI);
	}

	/**
	 * Created by Matthew Shaffer (matt-shaffer.com), modified by Andrew Butler
	 * 
	 * This method uses simple xor encryption to encrypt a password with a key so that it is at
	 * least not stored in clear text.
	 * 
	 * @param toEnc The string to encrypt
	 * @param encKey The encryption key
	 * @return The encrypted string
	 */
	private static String xorEnc(String toEnc, int encKey)
	{
		int t = 0;
		StringBuilder tog = new StringBuilder();
		if(encKey > 0)
		{
			while(t < toEnc.length())
			{
				int a = toEnc.charAt(t);
				int c = (a ^ encKey) % 256;
				char d = (char) c;
				tog.append(d);
				t++;
			}
		}
		return tog.toString();
	}

	private static String toHex(String str)
	{
		StringBuilder ret = new StringBuilder();
		for(int i = 0; i < str.length(); i++)
		{
			char c = str.charAt(i);
			ret.append(HEX.charAt(c / 16));
			ret.append(HEX.charAt(c % 16));
		}
		return ret.toString();
	}

	private static String fromHex(String str)
	{
		if(str.length() % 2 != 0)
			return null;
		StringBuilder ret = new StringBuilder();
		for(int i = 0; i < str.length(); i += 2)
		{
			int c = HEX.indexOf(str.charAt(i));
			if(c < 0)
				return null;
			c *= HEX.length();
			c += HEX.indexOf(str.charAt(i + 1));
			ret.append((char) c);
		}
		return ret.toString();
	}

	/** An expression to evaluate against an integer field in a database. */
	public static interface KeyExpression
	{
		/**
		 * @return The complexity of the expression
		 */
		int getComplexity();

		/**
		 * Generates the SQL where clause (without the "where") that allows selection of rows whose
		 * column matches this expression
		 * 
		 * @param column The name of the column to evaluate against this expression
		 * @return The SQL where clause
		 */
		String toSQL(String column);
	}

	/** Represents an expression that matches no rows */
	public static class NoneExpression implements KeyExpression
	{
		public int getComplexity()
		{
			return 2;
		}

		public String toSQL(String column)
		{
			return "(" + column + "=0 AND " + column + "=1)";
		}
	}

	/** Matches rows whose key matches a comparison expresion */
	public static class CompareExpression implements KeyExpression
	{
		/** The minimum value of the expression */
		public final long min;

		/** The maximum value of the expression */
		public final long max;

		/**
		 * Creates a CompareExpression
		 * 
		 * @param aMin The minimum value for the expression
		 * @param aMax The maximum value for the expression
		 */
		public CompareExpression(long aMin, long aMax)
		{
			min = aMin;
			max = aMax;
		}

		public int getComplexity()
		{
			int ret = 0;
			if(min > Long.MIN_VALUE)
			{
				ret++;
				if(max > Long.MIN_VALUE)
					ret += 2;
			}
			else if(max > Long.MIN_VALUE)
				ret++;
			return ret;
		}

		public String toSQL(String column)
		{
			String ret;
			if(min > Long.MIN_VALUE)
			{
				ret = column + ">=" + min;
				if(max > Long.MIN_VALUE)
					ret = "(" + ret + " AND " + column + "<=" + max + ")";
			}
			else if(max > Long.MIN_VALUE)
				ret = column + "<=" + max;
			else
				ret = "(" + column + "=0 AND " + column + "=1)";
			return ret;
		}
	}

	/** Matches rows whose keys are in a set */
	public static class ContainedExpression implements KeyExpression
	{
		/** The set of keys that this expression matches */
		public long [] theValues;

		public int getComplexity()
		{
			return theValues.length;
		}

		public String toSQL(String column)
		{
			StringBuilder ret = new StringBuilder(column);
			if(theValues.length == 1)
			{
				ret.append('=');
				ret.append(theValues[0]);
			}
			else
			{
				ret.append(" IN (");
				for(int v = 0; v < theValues.length; v++)
				{
					ret.append(theValues[v]);
					if(v < theValues.length - 1)
						ret.append(", ");
				}
				ret.append(')');
			}
			return ret.toString();
		}
	}

	/** Matches rows that match any of a set of subexpressions */
	public static class OrExpression implements KeyExpression
	{
		/** The set of subexpressions */
		public KeyExpression [] exprs;

		void flatten()
		{
			int count = 0;
			for(int i = 0; i < exprs.length; i++)
			{
				if(exprs[i] instanceof NoneExpression)
					continue;
				else if(exprs[i] instanceof OrExpression)
					count += ((OrExpression) exprs[i]).exprs.length;
				else
					count++;
			}
			if(count == exprs.length)
				return;
			KeyExpression [] newExprs = new KeyExpression [count];
			int idx = 0;
			for(int i = 0; i < exprs.length; i++)
			{
				if(exprs[i] instanceof NoneExpression)
					continue;
				else if(exprs[i] instanceof OrExpression)
				{
					for(int j = 0; j < ((OrExpression) exprs[i]).exprs.length; j++, idx++)
						newExprs[idx] = ((OrExpression) exprs[i]).exprs[j];
				}
				else
					newExprs[idx++] = exprs[i];
			}
			exprs = newExprs;
		}

		public int getComplexity()
		{
			int ret = 0;
			for(int i = 0; i < exprs.length; i++)
			{
				ret += exprs[i].getComplexity();
				if(ret < exprs.length - 1)
					ret++;
			}
			return ret;
		}

		public String toSQL(String column)
		{
			StringBuilder ret = new StringBuilder("(");
			for(int i = 0; i < exprs.length; i++)
			{
				ret.append(exprs[i].toSQL(column));
				if(i < exprs.length - 1)
					ret.append(" OR ");
			}
			ret.append(')');
			return ret.toString();
		}
	}

	private static boolean isSorted(long [] ids)
	{
		if(ids.length < 2)
			return true;
		long id = ids[0];
		for(int i = 1; i < ids.length; i++)
		{
			if(ids[i] < id)
				return false;
			id = ids[i];
		}
		return true;
	}

	/**
	 * Compiles a set of keys into an expression that can be evaluated on a database more quickly
	 * and reliably than using an expression like "IN (id1, id2, ...)"
	 * 
	 * @param ids The set of IDs to compile into an expression
	 * @param maxComplexity The maximum complexity for the OR'ed expressions
	 * @return A single expression whose complexity<=maxComplexity, or an {@link OrExpression} whose
	 *         {@link OrExpression#exprs} are all <=maxComplexity
	 */
	public static KeyExpression simplifyKeySet(long [] ids, int maxComplexity)
	{
		// First we need to sort the list and remove duplicates
		if(!isSorted(ids))
		{
			long [] temp = new long [ids.length];
			System.arraycopy(ids, 0, temp, 0, ids.length);
			ids = temp;
			java.util.Arrays.sort(ids);
		}
		java.util.ArrayList<Integer> duplicates = new java.util.ArrayList<Integer>();
		for(int i = 1; i < ids.length; i++)
			if(ids[i] == ids[i - 1])
				duplicates.add(Integer.valueOf(i));
		if(duplicates.size() > 0)
		{
			long [] newIDs = new long [ids.length - duplicates.size()];
			int lastIdx = 0;
			for(int i = 0; i < duplicates.size(); i++)
			{
				int dup = duplicates.get(i).intValue();
				if(dup != lastIdx)
					System.arraycopy(ids, lastIdx, newIDs, lastIdx - i, dup - lastIdx);
				lastIdx = dup + 1;
			}
			if(lastIdx != ids.length)
				System.arraycopy(ids, lastIdx, newIDs, lastIdx - duplicates.size(), ids.length
					- lastIdx);
			ids = newIDs;
		}
		duplicates = null;
		KeyExpression ret = simplifyKeySet(ids);
		/* If ret is too complex, divide into multiple expressions of <=minComplexity that OR to
		 * make the same overall expression */
		return expand(ret, maxComplexity);
	}

	static KeyExpression simplifyKeySet(long [] ids)
	{
		if(ids.length == 0)
			return null;
		if(ids[ids.length - 1] - ids[0] == ids.length - 1)
			return new CompareExpression(ids[0], ids[ids.length - 1]);
		int start = 0;
		java.util.ArrayList<Long> solos = new java.util.ArrayList<Long>();
		java.util.ArrayList<KeyExpression> ors = new java.util.ArrayList<KeyExpression>();
		for(int i = 1; i < ids.length; i++)
		{
			if(ids[i] != ids[i - 1] + 1)
			{
				if(i - start <= 2)
					for(int j = start; j < i; j++)
						solos.add(Long.valueOf(ids[j]));
				else
					ors.add(new CompareExpression(ids[start], ids[i - 1]));
				start = i;
			}
		}
		if(ids.length - start < 2)
			for(int j = start; j < ids.length; j++)
				solos.add(Long.valueOf(ids[j]));
		else
			ors.add(new CompareExpression(ids[start], ids[ids.length - 1]));
		KeyExpression ret;
		if(ors.size() > 1)
		{
			ret = new OrExpression();
			((OrExpression) ret).exprs = ors.toArray(new KeyExpression [ors.size()]);
		}
		else if(ors.size() == 1)
			ret = ors.get(0);
		else
			ret = new NoneExpression();
		if(solos.size() > 0)
		{
			ContainedExpression solosExpr = new ContainedExpression();
			solosExpr.theValues = new long [solos.size()];
			for(int i = 0; i < solos.size(); i++)
				solosExpr.theValues[i] = solos.get(i).longValue();
			OrExpression ret2 = new OrExpression();
			ret2.exprs = new KeyExpression [] {ret, solosExpr};
			ret = ret2;
		}
		return ret;
	}

	/**
	 * If the given expression is too complex, this method creates and returns an OrExpression whose
	 * sub-expressions are sufficiently simple.
	 * 
	 * @param expr The expression to expand
	 * @param maxComplexity The expanded expression which is either simple enough itself, or an
	 *        OrExpression whose sub-expressions are simple enough.
	 * @return The expanded expression
	 */
	public static KeyExpression expand(KeyExpression expr, int maxComplexity)
	{
		if(expr instanceof ContainedExpression)
		{
			ContainedExpression cont = (ContainedExpression) expr;
			int split = (cont.theValues.length - 1) / maxComplexity + 1;
			OrExpression ret = new OrExpression();
			ret.exprs = new KeyExpression [split];
			int idx = 0;
			for(int i = 0; i < split; i++)
			{
				int nextIdx = cont.theValues.length * (i + 1) / split;
				ContainedExpression ret_i = new ContainedExpression();
				ret.exprs[i] = ret_i;
				ret_i.theValues = new long [nextIdx - idx];
				for(int j = idx; j < nextIdx; j++)
					ret_i.theValues[j - idx] = cont.theValues[j];
			}
			return ret;
		}
		else if(expr instanceof OrExpression)
		{
			OrExpression boolExp = (OrExpression) expr;
			for(int i = 0; i < boolExp.exprs.length; i++)
				boolExp.exprs[i] = expand(boolExp.exprs[i], maxComplexity - 1);
			boolExp.flatten();
			return boolExp;
		}
		else
			return expr;
	}

	/**
	 * Performs an update using a very complex key. This call works even on databases that do not
	 * support operations of the given key's complexity
	 * 
	 * @param stmt The statement to use to update the data
	 * @param preSQL The SQL statement updating the data. It should have a where clause at its end
	 *        so that a clause can be added to the string.
	 * @param keys The key structure to update the data for
	 * @param postSQL Potentially more SQL to append after the key structure clause
	 * @param column The column that the key is for
	 * @param maxComplexity The maximum complexity to query for at a time
	 * @return The number of rows updated or deleted as a result of this call
	 * @throws SQLException If an error occurs updting the database
	 */
	public static int executeUpdate(Statement stmt, String preSQL, KeyExpression keys,
		String postSQL, String column, int maxComplexity) throws SQLException
	{
		if(!(keys instanceof OrExpression) || keys.getComplexity() < maxComplexity)
			return stmt.executeUpdate(preSQL + keys.toSQL(column) + postSQL);

		OrExpression or = (OrExpression) keys;
		int ret = 0;
		OrExpression internalOr = new OrExpression();
		int start = 0, end = 0, comp = 0;
		while(end < or.exprs.length)
		{
			comp += or.exprs[end].getComplexity() + 1;
			end++;
			if(comp >= maxComplexity)
			{
				if(end - start > 1)
				{
					end--;
					if(internalOr.exprs == null || internalOr.exprs.length != end - start)
						internalOr.exprs = new KeyExpression [end - start];
					System.arraycopy(or.exprs, start, internalOr.exprs, 0, end - start);
					ret += stmt.executeUpdate(preSQL + internalOr.toSQL(column) + postSQL);
				}
				else
					ret += executeUpdate(stmt, preSQL, or.exprs[start], postSQL, column,
						maxComplexity);
				comp = 0;
				start = end;
			}
		}
		if(start < or.exprs.length)
		{
			if(internalOr.exprs == null || internalOr.exprs.length != or.exprs.length - start)
				internalOr.exprs = new KeyExpression [or.exprs.length - start];
			System.arraycopy(or.exprs, start, internalOr.exprs, 0, or.exprs.length - start);
			ret += executeUpdate(stmt, preSQL, internalOr, postSQL, column, maxComplexity);
		}
		return ret;
	}

	/**
	 * Performs a database query using a very complex key. This call works even on databases that do
	 * not support operations of the given key's complexity, as long as the key can be broken down
	 * into separate queries that merge to the same result. ORDER BY clauses are not supported by
	 * this method.
	 * 
	 * @param stmt The statement to use to query the data
	 * @param preSQL The SQL statement querying the data. It should end have a where clause at its
	 *        end so that a clause can be added to the string.
	 * @param keys The key structure to get the data for
	 * @param postSQL Potentially more SQL to append after the key structure clause
	 * @param column The column that the key is for
	 * @param maxComplexity The maximum complexity to query for at a time
	 * @return A result set that iterates through all applicable rows in the database
	 * @throws SQLException If an error occurs getting the results
	 */
	public static ResultSet executeQuery(final Statement stmt, final String preSQL,
		KeyExpression keys, final String postSQL, final String column, final int maxComplexity)
		throws SQLException
	{
		if(!(keys instanceof OrExpression) || keys.getComplexity() <= maxComplexity)
			return stmt.executeQuery(preSQL + keys.toSQL(column) + postSQL);

		final OrExpression or = (OrExpression) keys;
		java.sql.ResultSet retRS = new java.sql.ResultSet()
		{
			private int start;

			private int end;

			private int comp;

			private OrExpression internalOr = new OrExpression();

			private ResultSet wrapped;

			public boolean next() throws SQLException
			{
				boolean ret = wrapped == null ? false : wrapped.next();
				while(!ret && end < or.exprs.length)
				{
					if(wrapped != null)
					{
						wrapped.close();
						wrapped = null;
					}
					comp += or.exprs[end].getComplexity() + 1;
					end++;
					if(comp >= maxComplexity)
					{
						if(end - start > 1)
						{
							end--;
							if(internalOr.exprs == null || internalOr.exprs.length != end - start)
								internalOr.exprs = new KeyExpression [end - start];
							System.arraycopy(or.exprs, start, internalOr.exprs, 0, end - start);
							wrapped = stmt
								.executeQuery(preSQL + internalOr.toSQL(column) + postSQL);
						}
						else
							wrapped = executeQuery(stmt, preSQL, or.exprs[start], postSQL, column,
								maxComplexity);
						comp = 0;
						start = end;
						ret = wrapped.next();
					}
				}
				if(!ret && start < or.exprs.length)
				{
					if(internalOr.exprs == null
						|| internalOr.exprs.length != or.exprs.length - start)
						internalOr.exprs = new KeyExpression [or.exprs.length - start];
					System.arraycopy(or.exprs, start, internalOr.exprs, 0, or.exprs.length - start);
					wrapped = executeQuery(stmt, preSQL, internalOr, postSQL, column, maxComplexity);
				}
				return ret;
			}

			public void close() throws SQLException
			{
				if(wrapped != null)
				{
					wrapped.close();
					wrapped = null;
				}
			}

			public boolean isWrapperFor(Class<?> iface) throws SQLException
			{
				return wrapped.isWrapperFor(iface);
			}

			public <T> T unwrap(Class<T> iface) throws SQLException
			{
				return wrapped.unwrap(iface);
			}

			public boolean wasNull() throws SQLException
			{
				return wrapped.wasNull();
			}

			public String getString(int columnIndex) throws SQLException
			{
				return wrapped.getString(columnIndex);
			}

			public String getString(String columnLabel) throws SQLException
			{
				return wrapped.getString(columnLabel);
			}

			public boolean getBoolean(int columnIndex) throws SQLException
			{
				return wrapped.getBoolean(columnIndex);
			}

			public boolean getBoolean(String columnLabel) throws SQLException
			{
				return wrapped.getBoolean(columnLabel);
			}

			public byte getByte(int columnIndex) throws SQLException
			{
				return wrapped.getByte(columnIndex);
			}

			public byte getByte(String columnLabel) throws SQLException
			{
				return wrapped.getByte(columnLabel);
			}

			public short getShort(int columnIndex) throws SQLException
			{
				return wrapped.getShort(columnIndex);
			}

			public short getShort(String columnLabel) throws SQLException
			{
				return wrapped.getShort(columnLabel);
			}

			public int getInt(int columnIndex) throws SQLException
			{
				return wrapped.getInt(columnIndex);
			}

			public int getInt(String columnLabel) throws SQLException
			{
				return wrapped.getInt(columnLabel);
			}

			public long getLong(int columnIndex) throws SQLException
			{
				return wrapped.getLong(columnIndex);
			}

			public long getLong(String columnLabel) throws SQLException
			{
				return wrapped.getLong(columnLabel);
			}

			public float getFloat(int columnIndex) throws SQLException
			{
				return wrapped.getFloat(columnIndex);
			}

			public float getFloat(String columnLabel) throws SQLException
			{
				return wrapped.getFloat(columnLabel);
			}

			public double getDouble(int columnIndex) throws SQLException
			{
				return wrapped.getDouble(columnIndex);
			}

			public double getDouble(String columnLabel) throws SQLException
			{
				return wrapped.getDouble(columnLabel);
			}

			public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException
			{
				return wrapped.getBigDecimal(columnIndex);
			}

			public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException
			{
				return wrapped.getBigDecimal(columnLabel);
			}

			public byte [] getBytes(int columnIndex) throws SQLException
			{
				return wrapped.getBytes(columnIndex);
			}

			public byte [] getBytes(String columnLabel) throws SQLException
			{
				return wrapped.getBytes(columnLabel);
			}

			public Date getDate(int columnIndex) throws SQLException
			{
				return wrapped.getDate(columnIndex);
			}

			public Date getDate(String columnLabel) throws SQLException
			{
				return wrapped.getDate(columnLabel);
			}

			public Time getTime(int columnIndex) throws SQLException
			{
				return wrapped.getTime(columnIndex);
			}

			public Time getTime(String columnLabel) throws SQLException
			{
				return wrapped.getTime(columnLabel);
			}

			public Timestamp getTimestamp(int columnIndex) throws SQLException
			{
				return wrapped.getTimestamp(columnIndex);
			}

			public Timestamp getTimestamp(String columnLabel) throws SQLException
			{
				return wrapped.getTimestamp(columnLabel);
			}

			public InputStream getAsciiStream(int columnIndex) throws SQLException
			{
				return wrapped.getAsciiStream(columnIndex);
			}

			public InputStream getAsciiStream(String columnLabel) throws SQLException
			{
				return wrapped.getAsciiStream(columnLabel);
			}

			@SuppressWarnings("deprecation")
			public InputStream getUnicodeStream(int columnIndex) throws SQLException
			{
				return wrapped.getUnicodeStream(columnIndex);
			}

			@SuppressWarnings("deprecation")
			public InputStream getUnicodeStream(String columnLabel) throws SQLException
			{
				return wrapped.getUnicodeStream(columnLabel);
			}

			public InputStream getBinaryStream(int columnIndex) throws SQLException
			{
				return wrapped.getBinaryStream(columnIndex);
			}

			public InputStream getBinaryStream(String columnLabel) throws SQLException
			{
				return wrapped.getBinaryStream(columnLabel);
			}

			public Object getObject(int columnIndex) throws SQLException
			{
				return wrapped.getObject(columnIndex);
			}

			public Object getObject(String columnLabel) throws SQLException
			{
				return wrapped.getObject(columnLabel);
			}

			public Reader getCharacterStream(int columnIndex) throws SQLException
			{
				return wrapped.getCharacterStream(columnIndex);
			}

			public Reader getCharacterStream(String columnLabel) throws SQLException
			{
				return wrapped.getCharacterStream(columnLabel);
			}

			public BigDecimal getBigDecimal(int columnIndex) throws SQLException
			{
				return wrapped.getBigDecimal(columnIndex);
			}

			public BigDecimal getBigDecimal(String columnLabel) throws SQLException
			{
				return wrapped.getBigDecimal(columnLabel);
			}

			public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException
			{
				return wrapped.getObject(columnIndex, map);
			}

			public Ref getRef(int columnIndex) throws SQLException
			{
				return wrapped.getRef(columnIndex);
			}

			public Ref getRef(String columnLabel) throws SQLException
			{
				return wrapped.getRef(columnLabel);
			}

			public Blob getBlob(int columnIndex) throws SQLException
			{
				return wrapped.getBlob(columnIndex);
			}

			public Blob getBlob(String columnLabel) throws SQLException
			{
				return wrapped.getBlob(columnLabel);
			}

			public Clob getClob(int columnIndex) throws SQLException
			{
				return wrapped.getClob(columnIndex);
			}

			public Clob getClob(String columnLabel) throws SQLException
			{
				return wrapped.getClob(columnLabel);
			}

			public Array getArray(int columnIndex) throws SQLException
			{
				return wrapped.getArray(columnIndex);
			}

			public Array getArray(String columnLabel) throws SQLException
			{
				return wrapped.getArray(columnLabel);
			}

			public Object getObject(String columnLabel, Map<String, Class<?>> map)
				throws SQLException
			{
				return wrapped.getObject(columnLabel, map);
			}

			public Date getDate(int columnIndex, Calendar cal) throws SQLException
			{
				return wrapped.getDate(columnIndex);
			}

			public Date getDate(String columnLabel, Calendar cal) throws SQLException
			{
				return wrapped.getDate(columnLabel, cal);
			}

			public Time getTime(int columnIndex, Calendar cal) throws SQLException
			{
				return wrapped.getTime(columnIndex, cal);
			}

			public Time getTime(String columnLabel, Calendar cal) throws SQLException
			{
				return wrapped.getTime(columnLabel, cal);
			}

			public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException
			{
				return wrapped.getTimestamp(columnIndex, cal);
			}

			public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException
			{
				return wrapped.getTimestamp(columnLabel, cal);
			}

			public URL getURL(int columnIndex) throws SQLException
			{
				return wrapped.getURL(columnIndex);
			}

			public URL getURL(String columnLabel) throws SQLException
			{
				return wrapped.getURL(columnLabel);
			}

			public NClob getNClob(int columnIndex) throws SQLException
			{
				return wrapped.getNClob(columnIndex);
			}

			public NClob getNClob(String columnLabel) throws SQLException
			{
				return wrapped.getNClob(columnLabel);
			}

			public SQLXML getSQLXML(int columnIndex) throws SQLException
			{
				return wrapped.getSQLXML(columnIndex);
			}

			public SQLXML getSQLXML(String columnLabel) throws SQLException
			{
				return wrapped.getSQLXML(columnLabel);
			}

			public String getNString(int columnIndex) throws SQLException
			{
				return wrapped.getNString(columnIndex);
			}

			public String getNString(String columnLabel) throws SQLException
			{
				return wrapped.getNString(columnLabel);
			}

			public Reader getNCharacterStream(int columnIndex) throws SQLException
			{
				return wrapped.getNCharacterStream(columnIndex);
			}

			public Reader getNCharacterStream(String columnLabel) throws SQLException
			{
				return wrapped.getNCharacterStream(columnLabel);
			}

			public SQLWarning getWarnings() throws SQLException
			{
				return wrapped.getWarnings();
			}

			public void clearWarnings() throws SQLException
			{
				wrapped.clearWarnings();
			}

			public String getCursorName() throws SQLException
			{
				return wrapped.getCursorName();
			}

			public ResultSetMetaData getMetaData() throws SQLException
			{
				return wrapped.getMetaData();
			}

			public int findColumn(String columnLabel) throws SQLException
			{
				return wrapped.findColumn(columnLabel);
			}

			public boolean isBeforeFirst() throws SQLException
			{
				return wrapped.isBeforeFirst();
			}

			public boolean isAfterLast() throws SQLException
			{
				return wrapped.isAfterLast();
			}

			public boolean isFirst() throws SQLException
			{
				return wrapped.isFirst();
			}

			public boolean isLast() throws SQLException
			{
				return wrapped.isLast();
			}

			public void beforeFirst() throws SQLException
			{
				wrapped.beforeFirst();
			}

			public void afterLast() throws SQLException
			{
				wrapped.afterLast();
			}

			public boolean first() throws SQLException
			{
				return wrapped.first();
			}

			public boolean last() throws SQLException
			{
				return wrapped.last();
			}

			public int getRow() throws SQLException
			{
				return wrapped.getRow();
			}

			public boolean absolute(int row) throws SQLException
			{
				return wrapped.absolute(row);
			}

			public boolean relative(int rows) throws SQLException
			{
				return wrapped.relative(rows);
			}

			public boolean previous() throws SQLException
			{
				return wrapped.previous();
			}

			public void setFetchDirection(int direction) throws SQLException
			{
				wrapped.setFetchDirection(direction);
			}

			public int getFetchDirection() throws SQLException
			{
				return wrapped.getFetchDirection();
			}

			public void setFetchSize(int rows) throws SQLException
			{
				wrapped.setFetchSize(rows);
			}

			public int getFetchSize() throws SQLException
			{
				return wrapped.getFetchSize();
			}

			public int getType() throws SQLException
			{
				return wrapped.getType();
			}

			public int getConcurrency() throws SQLException
			{
				return wrapped.getConcurrency();
			}

			public boolean rowUpdated() throws SQLException
			{
				return wrapped.rowUpdated();
			}

			public boolean rowInserted() throws SQLException
			{
				return wrapped.rowInserted();
			}

			public boolean rowDeleted() throws SQLException
			{
				return wrapped.rowDeleted();
			}

			public void updateNull(int columnIndex) throws SQLException
			{
				wrapped.updateNull(columnIndex);
			}

			public void updateNull(String columnLabel) throws SQLException
			{
				wrapped.updateNull(columnLabel);
			}

			public void updateBoolean(int columnIndex, boolean x) throws SQLException
			{
				wrapped.updateBoolean(columnIndex, x);
			}

			public void updateBoolean(String columnLabel, boolean x) throws SQLException
			{
				wrapped.updateBoolean(columnLabel, x);
			}

			public void updateByte(int columnIndex, byte x) throws SQLException
			{
				wrapped.updateByte(columnIndex, x);
			}

			public void updateByte(String columnLabel, byte x) throws SQLException
			{
				wrapped.updateByte(columnLabel, x);
			}

			public void updateShort(int columnIndex, short x) throws SQLException
			{
				wrapped.updateShort(columnIndex, x);
			}

			public void updateShort(String columnLabel, short x) throws SQLException
			{
				wrapped.updateShort(columnLabel, x);
			}

			public void updateInt(int columnIndex, int x) throws SQLException
			{
				wrapped.updateInt(columnIndex, x);
			}

			public void updateInt(String columnLabel, int x) throws SQLException
			{
				wrapped.updateInt(columnLabel, x);
			}

			public void updateLong(int columnIndex, long x) throws SQLException
			{
				wrapped.updateLong(columnIndex, x);
			}

			public void updateLong(String columnLabel, long x) throws SQLException
			{
				wrapped.updateLong(columnLabel, x);
			}

			public void updateFloat(int columnIndex, float x) throws SQLException
			{
				wrapped.updateFloat(columnIndex, x);
			}

			public void updateFloat(String columnLabel, float x) throws SQLException
			{
				wrapped.updateFloat(columnLabel, x);
			}

			public void updateDouble(int columnIndex, double x) throws SQLException
			{
				wrapped.updateDouble(columnIndex, x);
			}

			public void updateDouble(String columnLabel, double x) throws SQLException
			{
				wrapped.updateDouble(columnLabel, x);
			}

			public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException
			{
				wrapped.updateBigDecimal(columnIndex, x);
			}

			public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException
			{
				wrapped.updateBigDecimal(columnLabel, x);
			}

			public void updateString(int columnIndex, String x) throws SQLException
			{
				wrapped.updateString(columnIndex, x);
			}

			public void updateString(String columnLabel, String x) throws SQLException
			{
				wrapped.updateString(columnLabel, x);
			}

			public void updateNString(int columnIndex, String nString) throws SQLException
			{
				wrapped.updateNString(columnIndex, nString);
			}

			public void updateNString(String columnLabel, String nString) throws SQLException
			{
				wrapped.updateNString(columnLabel, nString);
			}

			public void updateBytes(int columnIndex, byte [] x) throws SQLException
			{
				wrapped.updateBytes(columnIndex, x);
			}

			public void updateBytes(String columnLabel, byte [] x) throws SQLException
			{
				wrapped.updateBytes(columnLabel, x);
			}

			public void updateDate(int columnIndex, Date x) throws SQLException
			{
				wrapped.updateDate(columnIndex, x);
			}

			public void updateDate(String columnLabel, Date x) throws SQLException
			{
				wrapped.updateDate(columnLabel, x);
			}

			public void updateTime(int columnIndex, Time x) throws SQLException
			{
				wrapped.updateTime(columnIndex, x);
			}

			public void updateTime(String columnLabel, Time x) throws SQLException
			{
				wrapped.updateTime(columnLabel, x);
			}

			public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException
			{
				wrapped.updateTimestamp(columnIndex, x);
			}

			public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException
			{
				wrapped.updateTimestamp(columnLabel, x);
			}

			public void updateBinaryStream(int columnIndex, InputStream x, int length)
				throws SQLException
			{
				wrapped.updateBinaryStream(columnIndex, x, length);
			}

			public void updateBinaryStream(String columnLabel, InputStream x, int length)
				throws SQLException
			{
				wrapped.updateBinaryStream(columnLabel, x, length);
			}

			public void updateBlob(int columnIndex, Blob x) throws SQLException
			{
				wrapped.updateBlob(columnIndex, x);
			}

			public void updateBlob(String columnLabel, Blob x) throws SQLException
			{
				wrapped.updateBlob(columnLabel, x);
			}

			public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException
			{
				wrapped.updateBlob(columnIndex, inputStream);
			}

			public void updateBlob(int columnIndex, InputStream inputStream, long length)
				throws SQLException
			{
				wrapped.updateBlob(columnIndex, inputStream, length);
			}

			public void updateBlob(String columnLabel, InputStream inputStream, long length)
				throws SQLException
			{
				wrapped.updateBlob(columnLabel, inputStream, length);
			}

			public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException
			{
				wrapped.updateBlob(columnLabel, inputStream);
			}

			public void updateBinaryStream(int columnIndex, InputStream x, long length)
				throws SQLException
			{
				wrapped.updateBinaryStream(columnIndex, x, length);
			}

			public void updateBinaryStream(String columnLabel, InputStream x, long length)
				throws SQLException
			{
				wrapped.updateBinaryStream(columnLabel, x, length);
			}

			public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException
			{
				wrapped.updateBinaryStream(columnIndex, x);
			}

			public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException
			{
				wrapped.updateBinaryStream(columnLabel, x);
			}

			public void updateAsciiStream(int columnIndex, InputStream x, int length)
				throws SQLException
			{
				wrapped.updateAsciiStream(columnIndex, x, length);
			}

			public void updateAsciiStream(String columnLabel, InputStream x, int length)
				throws SQLException
			{
				wrapped.updateAsciiStream(columnLabel, x, length);
			}

			public void updateAsciiStream(int columnIndex, InputStream x, long length)
				throws SQLException
			{
				wrapped.updateAsciiStream(columnIndex, x, length);
			}

			public void updateAsciiStream(String columnLabel, InputStream x, long length)
				throws SQLException
			{
				wrapped.updateAsciiStream(columnLabel, x, length);
			}

			public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException
			{
				wrapped.updateAsciiStream(columnIndex, x);
			}

			public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException
			{
				wrapped.updateAsciiStream(columnLabel, x);
			}

			public void updateCharacterStream(int columnIndex, Reader x, int length)
				throws SQLException
			{
				wrapped.updateCharacterStream(columnIndex, x, length);
			}

			public void updateCharacterStream(String columnLabel, Reader reader, int length)
				throws SQLException
			{
				wrapped.updateCharacterStream(columnLabel, reader, length);
			}

			public void updateCharacterStream(int columnIndex, Reader x, long length)
				throws SQLException
			{
				wrapped.updateCharacterStream(columnIndex, x, length);
			}

			public void updateCharacterStream(String columnLabel, Reader reader, long length)
				throws SQLException
			{
				wrapped.updateCharacterStream(columnLabel, reader, length);
			}

			public void updateCharacterStream(int columnIndex, Reader x) throws SQLException
			{
				wrapped.updateCharacterStream(columnIndex, x);
			}

			public void updateCharacterStream(String columnLabel, Reader reader)
				throws SQLException
			{
				wrapped.updateCharacterStream(columnLabel, reader);
			}

			public void updateClob(int columnIndex, Clob x) throws SQLException
			{
				wrapped.updateClob(columnIndex, x);
			}

			public void updateClob(String columnLabel, Clob x) throws SQLException
			{
				wrapped.updateClob(columnLabel, x);
			}

			public void updateClob(int columnIndex, Reader reader, long length) throws SQLException
			{
				wrapped.updateClob(columnIndex, reader, length);
			}

			public void updateClob(String columnLabel, Reader reader, long length)
				throws SQLException
			{
				wrapped.updateClob(columnLabel, reader, length);
			}

			public void updateClob(int columnIndex, Reader reader) throws SQLException
			{
				wrapped.updateClob(columnIndex, reader);
			}

			public void updateClob(String columnLabel, Reader reader) throws SQLException
			{
				wrapped.updateClob(columnLabel, reader);
			}

			public void updateNClob(int columnIndex, NClob nClob) throws SQLException
			{
				wrapped.updateNClob(columnIndex, nClob);
			}

			public void updateNClob(String columnLabel, NClob nClob) throws SQLException
			{
				wrapped.updateNClob(columnLabel, nClob);
			}

			public void updateNClob(int columnIndex, Reader reader, long length)
				throws SQLException
			{
				wrapped.updateNClob(columnIndex, reader, length);
			}

			public void updateNClob(String columnLabel, Reader reader, long length)
				throws SQLException
			{
				wrapped.updateNClob(columnLabel, reader, length);
			}

			public void updateNClob(int columnIndex, Reader reader) throws SQLException
			{
				wrapped.updateNClob(columnIndex, reader);
			}

			public void updateNClob(String columnLabel, Reader reader) throws SQLException
			{
				wrapped.updateNClob(columnLabel, reader);
			}

			public void updateNCharacterStream(int columnIndex, Reader x, long length)
				throws SQLException
			{
				wrapped.updateNCharacterStream(columnIndex, x, length);
			}

			public void updateNCharacterStream(String columnLabel, Reader reader, long length)
				throws SQLException
			{
				wrapped.updateNCharacterStream(columnLabel, reader, length);
			}

			public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException
			{
				wrapped.updateNCharacterStream(columnIndex, x);
			}

			public void updateNCharacterStream(String columnLabel, Reader reader)
				throws SQLException
			{
				wrapped.updateNCharacterStream(columnLabel, reader);
			}

			public void updateObject(int columnIndex, Object x, int scaleOrLength)
				throws SQLException
			{
				wrapped.updateObject(columnIndex, x, scaleOrLength);
			}

			public void updateObject(String columnLabel, Object x, int scaleOrLength)
				throws SQLException
			{
				wrapped.updateObject(columnLabel, x, scaleOrLength);
			}

			public void updateObject(int columnIndex, Object x) throws SQLException
			{
				wrapped.updateObject(columnIndex, x);
			}

			public void updateObject(String columnLabel, Object x) throws SQLException
			{
				wrapped.updateObject(columnLabel, x);
			}

			public void updateRef(int columnIndex, Ref x) throws SQLException
			{
				wrapped.updateRef(columnIndex, x);
			}

			public void updateRef(String columnLabel, Ref x) throws SQLException
			{
				wrapped.updateRef(columnLabel, x);
			}

			public void updateArray(int columnIndex, Array x) throws SQLException
			{
				wrapped.updateArray(columnIndex, x);
			}

			public void updateArray(String columnLabel, Array x) throws SQLException
			{
				wrapped.updateArray(columnLabel, x);
			}

			public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException
			{
				wrapped.updateSQLXML(columnIndex, xmlObject);
			}

			public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException
			{
				wrapped.updateSQLXML(columnLabel, xmlObject);
			}

			public void insertRow() throws SQLException
			{
				wrapped.insertRow();
			}

			public void updateRow() throws SQLException
			{
				wrapped.updateRow();
			}

			public void deleteRow() throws SQLException
			{
				wrapped.deleteRow();
			}

			public void refreshRow() throws SQLException
			{
				wrapped.refreshRow();
			}

			public void cancelRowUpdates() throws SQLException
			{
				wrapped.cancelRowUpdates();
			}

			public void moveToInsertRow() throws SQLException
			{
				wrapped.moveToInsertRow();
			}

			public void moveToCurrentRow() throws SQLException
			{
				wrapped.moveToCurrentRow();
			}

			public Statement getStatement() throws SQLException
			{
				return wrapped.getStatement();
			}

			public RowId getRowId(int columnIndex) throws SQLException
			{
				return wrapped.getRowId(columnIndex);
			}

			public RowId getRowId(String columnLabel) throws SQLException
			{
				return wrapped.getRowId(columnLabel);
			}

			public void updateRowId(int columnIndex, RowId x) throws SQLException
			{
				wrapped.updateRowId(columnIndex, x);
			}

			public void updateRowId(String columnLabel, RowId x) throws SQLException
			{
				wrapped.updateRowId(columnLabel, x);
			}

			public int getHoldability() throws SQLException
			{
				return wrapped.getHoldability();
			}

			public boolean isClosed() throws SQLException
			{
				return wrapped.isClosed();
			}

		};
		return retRS;
	}

	/**
	 * Copies data from one database to another
	 * 
	 * @param srcConn The connection to copy data from
	 * @param destConn The connection to copy data to
	 * @param schema The database schema to copy
	 * @param tables The list of tables to copy data between the connections. These tables must
	 *        exist and have identical schema in both databases
	 * @param clearFirst Whether to clear all data from the destination tables before inserting the
	 *        source's data
	 * @throws java.sql.SQLException If an error occurs copying the data
	 */
	public static void copyDB(java.sql.Connection srcConn, java.sql.Connection destConn,
		String schema, String [] tables, boolean clearFirst) throws java.sql.SQLException
	{
		java.sql.ResultSet rs;
		java.util.ArrayList<String> columns = new java.util.ArrayList<String>();
		IntList types = new IntList();
		java.sql.Statement srcStmt = srcConn.createStatement();
		java.sql.Statement destStmt = null;
		if(clearFirst)
			destStmt = destConn.createStatement();
		schema = schema.toUpperCase();
		for(String table : tables)
		{
			table = table.toUpperCase();
			rs = srcConn.getMetaData().getColumns(null, schema, table, null);
			while(rs.next())
			{
				columns.add(rs.getString("COLUMN_NAME"));
				String typeName = rs.getString("TYPE_NAME").toLowerCase();
				if(typeName.startsWith("varchar"))
					types.add(Types.VARCHAR);
				else if(typeName.startsWith("numeric") || typeName.startsWith("number"))
					types.add(Types.NUMERIC);
				else if(typeName.startsWith("int"))
					types.add(Types.INTEGER);
				else if(typeName.equals("longvarchar"))
					types.add(Types.LONGVARCHAR);
				else if(typeName.equals("longvarbinary"))
					types.add(Types.LONGVARBINARY);
				else if(typeName.startsWith("char"))
					types.add(Types.CHAR);
				else if(typeName.equals("clob"))
					types.add(Types.CLOB);
				else if(typeName.equals("blob"))
					types.add(Types.BLOB);
				else if(typeName.startsWith("timestamp") || typeName.startsWith("datetime"))
					types.add(Types.TIMESTAMP);
				else if(typeName.equals("smallint"))
					types.add(Types.SMALLINT);
				else if(typeName.startsWith("date"))
					types.add(Types.DATE);
				else if(typeName.equals("float"))
					types.add(Types.FLOAT);
				else if(typeName.equals("double"))
					types.add(Types.DOUBLE);
				else if(typeName.equals("boolean"))
					types.add(Types.BOOLEAN);
				else
					throw new IllegalStateException("Unrecognized type " + typeName);
			}
			rs.close();
			StringBuilder sql = new StringBuilder("INSERT INTO ");
			sql.append(table);
			sql.append('(');
			for(int i = 0; i < columns.size(); i++)
			{
				sql.append(columns.get(i));
				if(i < columns.size() - 1)
					sql.append(", ");
			}
			sql.append(") VALUES (");
			for(int i = 0; i < columns.size(); i++)
			{
				sql.append('?');
				if(i < columns.size() - 1)
					sql.append(", ");
			}
			sql.append(')');
			java.sql.PreparedStatement pStmt = destConn.prepareStatement(sql.toString());
			rs = srcStmt.executeQuery("SELECT * FROM " + table);
			if(clearFirst)
				destStmt.execute("DELETE FROM " + table);
			/* If an entry refers to another in the same table, it may fail to insert because the
			 * other entry is not present yet. For this reason, we try up to 5 times to insert the
			 * entries, hoping that if it fails once for this reason, it will succeed the next time
			 * because its parent entry has been inserted. This may still fail if there are too many
			 * recursive references.*/
			java.util.ArrayList<java.util.HashMap<String, Object>> entries;
			entries = new java.util.ArrayList<java.util.HashMap<String, Object>>();
			java.util.HashMap<String, Object> entry = new java.util.HashMap<String, Object>();
			while(rs.next())
			{
				pStmt.clearParameters();
				for(int i = 0; i < columns.size(); i++)
				{
					Object value;
					if(types.get(i) == Types.TIMESTAMP)
						value = rs.getTimestamp(columns.get(i));
					else
						value = rs.getObject(columns.get(i));
					entry.put(columns.get(i), value);
					if(value == null)
						pStmt.setNull(i + 1, types.get(i));
					else
						pStmt.setObject(i + 1, value);
				}
				try
				{
					pStmt.execute();
					entry.clear();
				} catch(java.sql.SQLException e)
				{
					entries.add(entry);
					entry = new java.util.HashMap<String, Object>();
				}
			}
			rs.close();
			for(int tries = 0; !entries.isEmpty() && tries < 3; tries++)
			{
				java.util.Iterator<java.util.HashMap<String, Object>> entryIter;
				entryIter = entries.iterator();
				while(entryIter.hasNext())
				{
					entry = entryIter.next();
					pStmt.clearParameters();
					for(int i = 0; i < columns.size(); i++)
					{
						Object value = entry.get(columns.get(i));
						if(value == null)
							pStmt.setNull(i + 1, types.get(i));
						else
							pStmt.setObject(i + 1, value);
					}
					try
					{
						pStmt.execute();
						entryIter.remove();
					} catch(java.sql.SQLException e)
					{}
				}
			}
			if(!entries.isEmpty())
			{
				java.util.Iterator<java.util.HashMap<String, Object>> entryIter;
				entryIter = entries.iterator();
				while(entryIter.hasNext())
				{
					entry = entryIter.next();
					pStmt.clearParameters();
					for(int i = 0; i < columns.size(); i++)
					{
						Object value = entry.get(columns.get(i));
						if(value == null)
							pStmt.setNull(i + 1, types.get(i));
						else
							pStmt.setObject(i + 1, value);
					}
					pStmt.execute();
					entryIter.remove();
				}
			}
			try
			{
				pStmt.close();
			} catch(Error e)
			{
				// HSQL gives us a bad error here. Keep going.
			}
			columns.clear();
			types.clear();
		}
		srcStmt.close();
		if(destStmt != null)
			destStmt.close();
	}

	/**
	 * Internal testing method
	 * 
	 * @param args Command line args, ignored
	 */
	public static final void main(String [] args)
	{
		long [] ids = new long [] {1, 2, 3, 3, 4, 5, 6, 8, 8, 8, 8, 9, 9, 10, 10, 11, 12, 13, 14,
			20};
		simplifyKeySet(ids, 100);
	}
}
