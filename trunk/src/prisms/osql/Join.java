/*
 * Join.java Created Jul 18, 2011 by Andrew Butler, PSL
 */
package prisms.osql;

import java.util.ArrayList;

/** Represents a table structure consisting of one or more database tables joined together */
public class Join extends Table
{
	/** All available types of table joins */
	public static enum JoinType
	{
		/** INNER JOIN, the default. Selects rows with matches in both tables. */
		INNER,
		/** LEFT JOIN. Selects rows with matches in the left table */
		LEFT,
		/** RIGHT JOIN. Selects rows with matches in the right table */
		RIGHT,
		/** FULL JOIN. Selects rows with matches in either table */
		FULL;
	}

	private ForeignKey [] theLinkKeys;

	private Table [] theLinkTables;

	private JoinType [] theJoinTypes;

	private Join(ForeignKey [] linkKeys, Table [] linkTables, JoinType [] joinTypes)
	{
		theLinkKeys = linkKeys;
		theLinkTables = linkTables;
		theJoinTypes = joinTypes;
	}

	@Override
	public Connection getConnection()
	{
		return theLinkKeys[0].getTable().getConnection();
	}

	/** @return All tables that are joined in this structure */
	public Table [] getTables()
	{
		return prisms.util.ArrayUtils.add(theLinkTables, theLinkKeys[0].getTable(), 0);
	}

	/** @return The foreign keys used to join this structure's tables */
	public ForeignKey [] getLinkKeys()
	{
		return theLinkKeys.clone();
	}

	/** @return The join types for each join in this structure */
	public JoinType [] getJoinTypes()
	{
		return theJoinTypes;
	}

	@Override
	public Column<?> [] getColumns()
	{
		ArrayList<Column<?>> ret = new ArrayList<Column<?>>();
		ArrayList<Table> tables = new ArrayList<Table>();
		for(Column<?> c : theLinkKeys[0].getTable().getColumns())
			ret.add(c);
		tables.add(theLinkKeys[0].getTable());
		for(int k = 0; k < theLinkKeys.length; k++)
		{
			if(tables.contains(theLinkTables[k]))
				continue;
			for(Column<?> c : theLinkTables[k].getColumns())
			{
				boolean isKey = false;
				for(Column<?> c2 : theLinkKeys[k].getImportKey())
					if(c.getName().equals(c2.getName()))
						isKey = true;
				if(!isKey)
					ret.add(c);
			}
		}
		return ret.toArray(new Column [ret.size()]);
	}

	@Override
	public ForeignKey [] getForeignKeys()
	{
		ArrayList<Table> tables = new ArrayList<Table>();
		ArrayList<ForeignKey> ret = new ArrayList<ForeignKey>();
		for(ForeignKey key : theLinkKeys[0].getTable().getForeignKeys())
			ret.add(key);
		tables.add(theLinkKeys[0].getTable());
		for(int t = 0; t < theLinkTables.length; t++)
		{
			ret.remove(theLinkKeys[t]);
			if(!tables.contains(theLinkTables[t]))
			{
				for(ForeignKey key : theLinkTables[t].getForeignKeys())
					ret.add(key);
				tables.add(theLinkTables[t]);
			}
		}
		return ret.toArray(new ForeignKey [ret.size()]);
	}

	@Override
	public void toSQL(StringBuilder ret)
	{
		for(int t = 0; t < theLinkKeys.length; t++)
		{
			if(t == 0)
				theLinkKeys[t].getTable().toSQL(ret);
			ret.append(theJoinTypes[t].name()).append(" JOIN ");
			theLinkKeys[t].getImportKey().getTable().toSQL(ret);
			ret.append(" ON ");
			for(int c = 0; c < theLinkKeys[t].getColumnCount(); c++)
			{
				if(c > 0)
					ret.append(" AND ");
				theLinkKeys[t].getColumn(c).toSQL(ret);
				ret.append('=');
				theLinkKeys[t].getImportKey().getColumn(c).toSQL(ret);
			}
		}
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		toSQL(ret);
		return ret.toString();
	}

	/**
	 * Makes an inner join of two or more tables
	 * 
	 * @param keys The foreign keys to join the tables of
	 * @return The Join representing the joined tables
	 * @throws PrismsSqlException If the keys are incompatible or duplicated
	 */
	public static Join innerJoin(ForeignKey... keys) throws PrismsSqlException
	{
		if(keys.length < 1)
			throw new IllegalArgumentException("A join must have at least two tables");
		Table [] tables = new Table [keys.length];
		JoinType [] types = new JoinType [keys.length];
		for(int t = 0; t < types.length; t++)
		{
			tables[t] = keys[t].getImportKey().getTable();
			types[t] = JoinType.INNER;
		}
		return join(keys, tables, types);
	}

	/**
	 * Joins two or more tables together
	 * 
	 * @param keys The foreign keys to join the tables of
	 * @param tables The tables referred to by each of the keys--one table per key
	 * @param types The join types for each join
	 * @return The Join structure representing the joined tables
	 * @throws PrismsSqlException If the keys are incompatible or duplicated or if the arguments are
	 *         otherwise invalid
	 */
	public static Join join(ForeignKey [] keys, Table [] tables, JoinType [] types)
		throws PrismsSqlException
	{
		if(keys.length < 1)
			throw new IllegalArgumentException("A join must have at least two tables");
		if(types.length != keys.length)
			throw new IllegalArgumentException("Need as many join types as there are joins");

		ArrayList<Table> allTables = new ArrayList<Table>();
		ArrayList<ForeignKey> allKeys = new ArrayList<ForeignKey>();
		for(int k = 0; k < tables.length; k++)
		{
			ForeignKey key = keys[k];
			if(allTables.size() == 0)
				allTables.add(key.getTable());
			else if(allKeys.contains(key))
				throw new PrismsSqlException("Key " + key + " has already been joined");
			else if(!allTables.contains(key.getTable()))
				throw new PrismsSqlException("Table " + key.getTable()
					+ " is not referenced earlier in the keys");
			else if(!tablesID(tables[k], key.getImportKey().getTable()))
				throw new PrismsSqlException("Foreign key " + key + " does not refer to table "
					+ tables[k]);
			allTables.add(key.getImportKey().getTable());
		}
		return new Join(keys, tables, types);
	}

	private static boolean tablesID(Table t1, Table t2)
	{
		if(t1 instanceof TableAlias)
		{
			if(t2 instanceof TableAlias)
				return ((TableAlias) t1).getBase().equals(((TableAlias) t2).getBase());
			else
				return ((TableAlias) t1).getBase().equals(t2);
		}
		else if(t2 instanceof TableAlias)
			return t1.equals(((TableAlias) t2).getBase());
		else
			return t1.equals(t2);
	}

	/**
	 * Joins more tables into a structure. The original structure is not modified.
	 * 
	 * @param join The join to append to
	 * @param keys The keys to join in
	 * @param tables The tables to join in
	 * @param types The join types to join them by
	 * @return The new Join structure
	 * @throws PrismsSqlException If the keys are incompatibile or duplicated
	 */
	public static Join join(Join join, ForeignKey [] keys, Table [] tables, JoinType [] types)
		throws PrismsSqlException
	{
		keys = prisms.util.ArrayUtils.addAll(join.theLinkKeys, keys);
		tables = prisms.util.ArrayUtils.addAll(join.theLinkTables, tables);
		types = prisms.util.ArrayUtils.addAll(join.theJoinTypes, types);
		return join(keys, tables, types);
	}
}
