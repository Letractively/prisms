/*
 * Table.java Created Apr 8, 2010 by Andrew Butler, PSL
 */
package prisms.osql;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import org.apache.log4j.Logger;

/** A base table is a table that is represented directly in the database schema */
public class BaseTable extends Table
{
	private static final Logger log = Logger.getLogger(BaseTable.class);

	private final Connection theConn;

	private String theSchema;

	private String theName;

	private Column<?> [] theColumns;

	private TableKey [] thePKs;

	private ForeignKey [] theFKs;

	BaseTable(Connection conn, String schema, String name) throws PrismsSqlException
	{
		theConn = conn;
		theSchema = schema.toUpperCase();
		theName = name.toUpperCase();

		fillPrimary();
		fillForeign();
	}

	private void fillPrimary() throws PrismsSqlException
	{
		ResultSet rs = null;
		try
		{
			DatabaseMetaData md = theConn.getSqlConnection().getMetaData();
			rs = md.getColumns(null, theSchema, theName, null);
			java.util.ArrayList<Column<?>> columns = new java.util.ArrayList<Column<?>>();
			while(rs.next())
				columns.add(ColumnCreator.createColumn(this, rs));
			rs.close();
			rs = null;
			theColumns = columns.toArray(new Column [columns.size()]);
			columns.clear();
			columns = null;

			java.util.LinkedHashMap<String, TableKey> pks = new java.util.LinkedHashMap<String, TableKey>();
			rs = md.getPrimaryKeys(null, theSchema, theName);
			while(rs.next())
			{
				String pkName = rs.getString("PK_NAME");
				TableKey key = pks.get(pkName);
				if(key == null)
				{
					key = new TableKey(this, pkName);
					pks.put(pkName, key);
				}
				key.addColumn(getColumn(rs.getString("COLUMN_NAME")));
			}
			rs.close();
			rs = null;
			thePKs = pks.values().toArray(new TableKey [pks.size()]);
			pks.clear();
			pks = null;
			if(thePKs.length == 0)
			{
				thePKs = new TableKey [] {new TableKey(this, null)};
				for(Column<?> column : theColumns)
					thePKs[0].addColumn(column);
			}
		} catch(java.sql.SQLException e)
		{
			throw new PrismsSqlException("Could not query for table columns", e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(java.sql.SQLException e)
				{
					System.err.println("Connection: Connection error");
				}
		}
	}

	private void fillForeign() throws PrismsSqlException
	{
		class FKTemplate
		{
			String theFKName;

			TableKey theReference;

			Column<?> [] theFKColumns;

			ForeignKey.LinkRule theUpdateRule;

			ForeignKey.LinkRule theDeleteRule;
		}
		ResultSet rs = null;
		try
		{
			DatabaseMetaData md = theConn.getSqlConnection().getMetaData();
			java.util.LinkedHashMap<prisms.util.DualKey<String, String>, FKTemplate> fks;
			fks = new java.util.LinkedHashMap<prisms.util.DualKey<String, String>, FKTemplate>();
			rs = md.getImportedKeys(null, theSchema, theName);
			while(rs.next())
			{
				String fkName = rs.getString("FK_NAME");
				String schema = rs.getString("PKTABLE_SCHEM");
				String tableName = rs.getString("PKTABLE_NAME");
				BaseTable fkTable = theConn.getTable(schema, tableName);
				prisms.util.DualKey<String, String> key = new prisms.util.DualKey<String, String>(
					fkName, schema + "." + tableName);
				FKTemplate fk = fks.get(key);
				if(fk == null)
				{
					fk = new FKTemplate();
					fk.theFKName = fkName;
					fk.theReference = fkTable.getPK(rs.getString("PK_NAME"));
					fk.theFKColumns = new Column [fk.theReference.getColumnCount()];
					switch(rs.getShort("UPDATE_RULE"))
					{
					case DatabaseMetaData.importedKeyNoAction:
					case DatabaseMetaData.importedKeyRestrict:
						fk.theUpdateRule = ForeignKey.LinkRule.RESTRICT;
						break;
					case DatabaseMetaData.importedKeyCascade:
						fk.theUpdateRule = ForeignKey.LinkRule.CASCADE;
						break;
					case DatabaseMetaData.importedKeySetNull:
						fk.theUpdateRule = ForeignKey.LinkRule.SETNULL;
						break;
					case DatabaseMetaData.importedKeySetDefault:
						fk.theUpdateRule = ForeignKey.LinkRule.SETDEFAULT;
						break;
					default:
						log.error("Unrecognized update rule");
						fk.theUpdateRule = ForeignKey.LinkRule.RESTRICT;
					}
					switch(rs.getShort("DELETE_RULE"))
					{
					case DatabaseMetaData.importedKeyNoAction:
					case DatabaseMetaData.importedKeyRestrict:
						fk.theDeleteRule = ForeignKey.LinkRule.RESTRICT;
						break;
					case DatabaseMetaData.importedKeyCascade:
						fk.theDeleteRule = ForeignKey.LinkRule.CASCADE;
						break;
					case DatabaseMetaData.importedKeySetNull:
						fk.theDeleteRule = ForeignKey.LinkRule.SETNULL;
						break;
					case DatabaseMetaData.importedKeySetDefault:
						fk.theDeleteRule = ForeignKey.LinkRule.SETDEFAULT;
						break;
					default:
						log.error("Unrecognized delete rule");
						fk.theDeleteRule = ForeignKey.LinkRule.RESTRICT;
					}
				}
				Column<?> fkColumn = getColumn(rs.getString("PKCOLUMN_NAME"));
				String fkcName = rs.getString("FKCOLUMN_NAME");
				int fkc;
				for(fkc = 0; fkc < fk.theReference.getColumnCount(); fkc++)
					if(fk.theReference.getColumn(fkc).getName().equals(fkcName))
						break;
				fk.theFKColumns[fkc] = fkColumn;
			}
			rs.close();
			rs = null;
			theFKs = new ForeignKey [0];
			for(FKTemplate fk : fks.values())
				theFKs = prisms.util.ArrayUtils.add(theFKs, new ForeignKey(fk.theFKName, this,
					fk.theFKColumns, fk.theReference, fk.theUpdateRule, fk.theDeleteRule));
		} catch(java.sql.SQLException e)
		{
			throw new PrismsSqlException("Could not query for table columns", e);
		} finally
		{
			if(rs != null)
				try
				{
					rs.close();
				} catch(java.sql.SQLException e)
				{
					System.err.println("Connection: Connection error");
				}
		}
	}

	@Override
	public Connection getConnection()
	{
		return theConn;
	}

	/** @return The name of the schema that this table is a part of */
	public String getSchema()
	{
		return theSchema;
	}

	/** @return This table's name */
	public String getName()
	{
		return theName;
	}

	@Override
	public ForeignKey [] getForeignKeys()
	{
		return theFKs;
	}

	@Override
	public ForeignKey [] getForeignKeys(Table table)
	{
		java.util.ArrayList<ForeignKey> ret = new java.util.ArrayList<ForeignKey>();
		for(ForeignKey key : theFKs)
			if(key.getImportKey().getTable().equals(table))
				ret.add(key);
		return ret.toArray(new ForeignKey [ret.size()]);
	}

	@Override
	public Column<?> [] getColumns()
	{
		return theColumns;
	}

	/** @return All primary keys this table has */
	public TableKey [] getPrimaryKeys()
	{
		return thePKs;
	}

	/**
	 * @param name The name of the primary key to get
	 * @return The primary key with the given name, or null if no such key exists
	 */
	public TableKey getPK(String name)
	{
		for(TableKey key : thePKs)
			if(key.getName().equals(name))
				return key;
		return null;
	}

	@Override
	public void toSQL(StringBuilder ret)
	{
		if(theSchema != null && theSchema.length() > 0)
		{
			ret.append(theSchema);
			ret.append('.');
		}
		ret.append(theName);
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		if(theSchema != null && theSchema.length() > 0)
			ret.append(theSchema).append('.');
		ret.append(theName);
		return ret.toString();
	}

	@Override
	public boolean equals(Object o)
	{
		if(!(o instanceof BaseTable))
			return false;
		BaseTable t = (BaseTable) o;
		return t.theConn.equals(theConn)
			&& (t.theSchema == null ? theSchema == null : t.theSchema.equals(theSchema))
			&& t.theName.equals(theName);
	}

	@Override
	public int hashCode()
	{
		return theConn.hashCode() * 13 + (theSchema != null ? theSchema.hashCode() * 7 : 0)
			+ theName.hashCode();
	}
}
