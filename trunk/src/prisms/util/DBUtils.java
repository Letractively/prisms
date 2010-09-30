/*
 * DBUtils.java Created Nov 11, 2009 by Andrew Butler, PSL
 */
package prisms.util;

import java.sql.Types;

/**
 * Contains database utility methods for general use
 */
public class DBUtils
{
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
	 * Formats a generic string for entry into a database using SQL
	 * 
	 * @param str The general string to put into the database
	 * @return The string that should be appended to the SQL string
	 */
	public static String toSQL(String str)
	{
		if(str == null)
			return "NULL";
		return "'" + str.replaceAll("'", "''") + "'";
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

		String ret = new java.sql.Timestamp(time).toString();

		if(oracle)
			ret = "TO_TIMESTAMP('" + ret + "', 'YYYY-MM-DD HH24:MI:SS.FF3')";
		// ret = "TO_DATE('" + ret.substring(0, ret.length()-4) + "', 'YYYY-MM-DD HH24:MI:SS')";
		else
			ret = "'" + ret + "'";

		return ret;
	}

	/**
	 * @param conn The connection to test
	 * @return Whether the connection is to an oracle database
	 */
	public static boolean isOracle(java.sql.Connection conn)
	{
		return conn.getClass().getName().toLowerCase().contains("ora");
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
		String tog = "";
		if(encKey > 0)
		{
			while(t < toEnc.length())
			{
				int a = toEnc.charAt(t);
				int c = (a ^ encKey) % 256;
				char d = (char) c;
				tog = tog + d;
				t++;
			}
		}
		return tog;
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
			String ret = column;
			if(theValues.length == 1)
				ret += "=" + theValues[0];
			else
			{
				ret += " IN (";
				for(int v = 0; v < theValues.length; v++)
				{
					ret += theValues[v];
					if(v < theValues.length - 1)
						ret += ", ";
				}
				ret += ")";
			}
			return ret;
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
			String ret = "(";
			for(int i = 0; i < exprs.length; i++)
			{
				ret += exprs[i].toSQL(column);
				if(i < exprs.length - 1)
					ret += " OR ";
			}
			ret += ")";
			return ret;
		}
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
		{
			long [] temp = new long [ids.length];
			System.arraycopy(ids, 0, temp, 0, ids.length);
			ids = temp;
		}
		java.util.Arrays.sort(ids);
		java.util.ArrayList<Integer> duplicates = new java.util.ArrayList<Integer>();
		for(int i = 1; i < ids.length; i++)
			if(ids[i] == ids[i - 1])
				duplicates.add(new Integer(i));
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
						solos.add(new Long(ids[j]));
				else
					ors.add(new CompareExpression(ids[start], ids[i - 1]));
				start = i;
			}
		}
		if(ids.length - start < 2)
			for(int j = start; j < ids.length; j++)
				solos.add(new Long(ids[j]));
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
			String sql = "INSERT INTO " + table + " (";
			for(int i = 0; i < columns.size(); i++)
			{
				sql += columns.get(i);
				if(i < columns.size() - 1)
					sql += ", ";
			}
			sql += ") VALUES (";
			for(int i = 0; i < columns.size(); i++)
			{
				sql += "?";
				if(i < columns.size() - 1)
					sql += ", ";
			}
			sql += ")";
			java.sql.PreparedStatement pStmt = destConn.prepareStatement(sql);
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
