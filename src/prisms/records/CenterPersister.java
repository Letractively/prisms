/*
 * CenterPersister.java Created Dec 18, 2009 by Andrew Butler, PSL
 */
package prisms.records;

import org.apache.log4j.Logger;

import prisms.arch.PrismsApplication;
import prisms.arch.event.PrismsEvent;
import prisms.arch.event.PrismsProperty;

/** Persists data centers to and from the record keeping database */
public abstract class CenterPersister extends prisms.util.persisters.ListPersister<PrismsCenter>
	implements prisms.util.persisters.DiscriminatingPersister<PrismsCenter []>
{
	private static final Logger log = Logger.getLogger(CenterPersister.class);

	private PrismsApplication theApp;

	private RecordKeeper theKeeper;

	@Override
	public void configure(prisms.arch.PrismsConfig config, PrismsApplication app,
		PrismsProperty<PrismsCenter []> property)
	{
		theApp = app;
		theKeeper = getKeeper(theApp);
		super.configure(config, app, property);
	}

	/**
	 * @param app The application to get the record keeper in
	 * @return The record keeper that this persister should use to retrieve and save centers
	 */
	public abstract RecordKeeper getKeeper(PrismsApplication app);

	@Override
	protected PrismsCenter [] depersist()
	{
		if(theKeeper == null)
			return new PrismsCenter [0];
		PrismsCenter [] ret;
		try
		{
			ret = theKeeper.getCenters();
		} catch(prisms.records.PrismsRecordException e)
		{
			log.error("Could not get " + theKeeper.getNamespace() + " centers", e);
			return new PrismsCenter [0];
		}
		for(int i = 0; i < ret.length; i++)
			if(ret[i].getID() == 0)
			{
				// Don't include the "Here" center in the UI value
				ret = prisms.util.ArrayUtils.remove(ret, i);
				break;
			}
		return ret;
	}

	@Override
	protected boolean equivalent(PrismsCenter po, PrismsCenter avo)
	{
		return po.equals(avo);
	}

	@Override
	protected PrismsCenter clone(PrismsCenter toClone)
	{
		return toClone;
	}

	@Override
	protected PrismsCenter add(prisms.arch.PrismsSession session, PrismsCenter newValue,
		prisms.arch.event.PrismsPCE<PrismsCenter []> evt)
	{
		if(theKeeper == null)
			return newValue;
		RecordsTransaction trans = prisms.records.RecordUtils.getTransaction(session, evt, theApp
			.getEnvironment().getUserSource());
		if(trans == null)
			return newValue;
		try
		{
			newValue.setDeleted(false);
			theKeeper.putCenter(newValue, trans);
		} catch(prisms.records.PrismsRecordException e)
		{
			log.error("Could not save " + theKeeper.getNamespace() + " center", e);
		}
		return newValue;
	}

	@Override
	protected void remove(prisms.arch.PrismsSession session, PrismsCenter removed,
		prisms.arch.event.PrismsPCE<PrismsCenter []> evt)
	{
		if(theKeeper == null)
			return;
		RecordsTransaction trans = prisms.records.RecordUtils.getTransaction(session, evt, theApp
			.getEnvironment().getUserSource());
		if(trans == null)
			return;
		try
		{
			theKeeper.removeCenter(removed, trans);
		} catch(prisms.records.PrismsRecordException e)
		{
			log.error("Could not delete " + theKeeper.getNamespace() + " center", e);
		}
	}

	@Override
	protected void update(prisms.arch.PrismsSession session, PrismsCenter dbValue,
		PrismsCenter availableValue, prisms.arch.event.PrismsEvent evt)
	{
		if(theKeeper == null)
			return;
		RecordsTransaction trans = prisms.records.RecordUtils.getTransaction(session, evt, theApp
			.getEnvironment().getUserSource());
		if(trans == null)
			return;
		try
		{
			if(evt.name.equals("syncAttempted"))
				theKeeper.putSyncRecord((prisms.records.SyncRecord) evt.getProperty("record"));
			else if(evt.name.equals("syncAttemptChanged"))
				theKeeper.putSyncRecord((prisms.records.SyncRecord) evt.getProperty("record"));
			else if(evt.name.equals("syncAttemptPurged"))
				theKeeper.removeSyncRecord((prisms.records.SyncRecord) evt.getProperty("record"));
			else if(evt.name.equals("centerChanged"))
				theKeeper.putCenter(availableValue, trans);
		} catch(prisms.records.PrismsRecordException e)
		{
			log.error("Could not persist change to " + theKeeper.getNamespace() + " center "
				+ dbValue.getName() + ": " + evt.name, e);
		}
	}

	public boolean applies(PrismsEvent evt)
	{
		if(theKeeper == null)
			return false;
		if(evt.name.equals("syncAttempted") || evt.name.equals("syncAttemptChanged")
			|| evt.name.equals("syncAttemptPurged"))
			return ((SyncRecord) evt.getProperty("record")).getCenter().getNamespace()
				.equals(theKeeper.getNamespace());
		else if(evt.name.equals("centerChanged"))
			return ((PrismsCenter) evt.getProperty("center")).getNamespace().equals(
				theKeeper.getNamespace());
		else
			return false;
	}
}
