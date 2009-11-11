/*
 * DBUtils.java Created Nov 11, 2009 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * Contains database utility methods for general use
 */
public class DBUtils
{
	/**
	 * Translates betwen an SQL character type and a boolean
	 * 
	 * @param b The SQL character representing a boolean
	 * @return The boolean represented by the character
	 */
	public static boolean getBoolean(String b)
	{
		return "t".equalsIgnoreCase(b);
	}

	/**
	 * Translates betwen an SQL character type and a boolean
	 * 
	 * @param b The boolean to be represented by a character
	 * @return The SQL character representing the boolean
	 */
	public static char getBoolString(boolean b)
	{
		return b ? 't' : 'f';
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
}
