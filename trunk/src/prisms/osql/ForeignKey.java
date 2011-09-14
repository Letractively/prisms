/*
 * ForeignKey.java Created Sep 15, 2010 by Andrew Butler, PSL
 */
package prisms.osql;

/** Represents a foreign key pointing from one table to another */
public class ForeignKey extends TableKey
{
	/** An action to occur when data in the target table of a foreign key is modified */
	public static enum LinkRule
	{
		/** Prevents the modification of the data */
		RESTRICT,
		/** Updates or deletes the data in the keyed table to match that in the target table */
		CASCADE,
		/** Sets the data in the keyed table to null when the row in the target table is deleted */
		SETNULL,
		/**
		 * Sets the data in the keyed table to its default values when the row in the target table
		 * is deleted
		 */
		SETDEFAULT;
	}

	private TableKey theImportedKey;

	private LinkRule theUpdateRule;

	private LinkRule theDeleteRule;

	ForeignKey(String name, Table importTable, Column<?> [] keyColumns, TableKey importKey,
		LinkRule update, LinkRule delete)
	{
		super(importTable, name);
		for(Column<?> c : keyColumns)
			addColumn(c);
		theImportedKey = importKey;
		theUpdateRule = update;
		theDeleteRule = delete;
	}

	/** @return The unique key in the target table that this foreign key points to */
	public TableKey getImportKey()
	{
		return theImportedKey;
	}

	/** @return The action that happens when data in the key columns of the target table is updated */
	public LinkRule getUpdateRule()
	{
		return theUpdateRule;
	}

	/** @return The action that happens when rows of the target table are removed */
	public LinkRule getDeleteRule()
	{
		return theDeleteRule;
	}
}
