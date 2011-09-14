/*
 * TableAlias.java Created Jul 18, 2011 by Andrew Butler, PSL
 */
package prisms.osql;

/** Represents an alias of a database table */
public class TableAlias extends Table
{
	private final BaseTable theBase;

	private final String theName;

	private Column<?> [] theColumns;

	private ForeignKey [] theFKs;

	/**
	 * Creates an alias for a database table
	 * 
	 * @param base The base table to create an alias of
	 * @param name The name for the alias
	 */
	@SuppressWarnings("rawtypes")
	public TableAlias(BaseTable base, String name)
	{
		theBase = base;
		theName = name;

		Column<?> [] baseCols = theBase.getColumns();
		theColumns = new Column<?> [baseCols.length];
		for(int c = 0; c < theColumns.length; c++)
			theColumns[c] = new Column(this, baseCols[c].getName(), baseCols[c].getDataType(),
				baseCols[c].getSize(), baseCols[c].getDecimalDigits(), baseCols[c].isNullable())
			{
				@Override
				public void toSQL(StringBuilder ret)
				{
					ret.append(TableAlias.this.getName()).append('.');
					ret.append(getName());
				}
			};

		ForeignKey [] baseKeys = theBase.getForeignKeys();
		theFKs = new ForeignKey [baseKeys.length];
		for(int k = 0; k < theFKs.length; k++)
		{
			Column<?> [] keyCols = new Column [baseKeys[k].getColumnCount()];
			for(int c = 0; c < keyCols.length; c++)
				keyCols[c] = getColumn(baseKeys[k].getColumn(c).getName());
			theFKs[k] = new ForeignKey(baseKeys[k].getName(), this, keyCols,
				baseKeys[k].getImportKey(), baseKeys[k].getUpdateRule(),
				baseKeys[k].getDeleteRule());
		}
	}

	/** @return The base table that this alias is for */
	public BaseTable getBase()
	{
		return theBase;
	}

	/** @return The alias name for the table */
	public String getName()
	{
		return theName;
	}

	@Override
	public Connection getConnection()
	{
		return theBase.getConnection();
	}

	@Override
	public Column<?> [] getColumns()
	{
		return theColumns;
	}

	@Override
	public ForeignKey [] getForeignKeys()
	{
		return theFKs;
	}

	@Override
	public void toSQL(StringBuilder ret)
	{
		theBase.toSQL(ret);
		ret.append(" AS ");
		ret.append(theName);
	}

	@Override
	public boolean equals(Object obj)
	{
		if(!(obj instanceof TableAlias))
			return false;
		TableAlias alias = (TableAlias) obj;
		return alias.theBase.equals(theBase) && alias.theName.equals(theName);
	}

	@Override
	public int hashCode()
	{
		return theBase.hashCode() * 3 + theName.hashCode();
	}

	@Override
	public String toString()
	{
		// TODO Auto-generated method stub
		return super.toString();
	}
}
