/*
 * DataType.java Created Apr 8, 2010 by Andrew Butler, PSL
 */
package prisms.osql;

/**
 * Represents the mapping of an SQL data type to a Java data type
 * 
 * @param <T> The java data type
 */
public interface DataType<T>
{
	/** @return The SQL name of the data type */
	String getSqlName();

	/** @return The pretty name of the data type */
	String getPrettyName();

	/** @return The java data type */
	Class<T> getJavaType();

	/** @return The SQL data type (see {@link java.sql.Types}) */
	int getSqlType();

	/**
	 * Retrieves a value of this type from a result set
	 * 
	 * @param rs The result set to retrieve the data from
	 * @param colName The name of the column to retrieve the data from
	 * @param column The column to get the data for
	 * @return The retrieved value
	 * @throws PrismsSqlException If the value could not be retrieved or compiled
	 */
	T get(java.sql.ResultSet rs, String colName, Column<T> column) throws PrismsSqlException;

	/**
	 * @param value The value to check
	 * @param column The column to write the data for
	 * @return Whether the given value can be written to straight SQL instead of needing a prepared
	 *         statement
	 */
	boolean isStringable(T value, Column<T> column);

	/**
	 * Writes a value to SQL
	 * 
	 * @param value The value to write
	 * @param column The column to write the data for
	 * @param ret The string builder to write the value to
	 */
	void toSQL(T value, StringBuilder ret, Column<T> column);

	/**
	 * Sets a value as a parameter in a prepared statement
	 * 
	 * @param value The value to add
	 * @param stmt The prepared statement to set the value in
	 * @param paramIdx The index to set the value at
	 * @param column The column to write the data for
	 * @throws java.sql.SQLException If an error occurs attempting to set the value
	 */
	void setParam(T value, java.sql.PreparedStatement stmt, int paramIdx, Column<T> column)
		throws java.sql.SQLException;
}
