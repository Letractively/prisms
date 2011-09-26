/*
 * PrismsSyncPersister.java Created Sep 14, 2011 by Andrew Butler, PSL
 */
package prisms.impl;

/** Creates a synchronizer for PRISMS user data */
public class PrismsSyncPersister extends prisms.records.SynchronizerPersister
{
	@Override
	protected prisms.records.PrismsSynchronizer createSynchronizer(prisms.arch.PrismsConfig config,
		prisms.arch.PrismsApplication app)
	{
		prisms.records.DBRecordKeeper prismsKeeper = ((prisms.arch.ds.ManageableUserSource) app
			.getEnvironment().getUserSource()).getRecordKeeper();
		prisms.records.PrismsSynchronizer prismsSync = new prisms.records.PrismsSynchronizer(
			prismsKeeper, (prisms.impl.PrismsSyncImpl) prismsKeeper.getPersister());
		return prismsSync;
	}
}
