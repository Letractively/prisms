/*
 * ScalableUserSource.java Created Feb 25, 2011 by Andrew Butler, PSL
 */
package prisms.impl;

/** Provides extra methods needed to implement scaling PRISMS across multiple installations */
public interface ScalableUserSource extends prisms.arch.ds.ManageableUserSource
{
	/**
	 * @param record The change record to get the current value of
	 * @return The current value of the field represented by the change
	 * @throws prisms.arch.PrismsException If an error occurs getting the data
	 */
	Object getDBValue(prisms.records.ChangeRecord record) throws prisms.arch.PrismsException;
}
