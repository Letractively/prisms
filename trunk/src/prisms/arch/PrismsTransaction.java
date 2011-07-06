/*
 * PrismsRequest.java Created Jan 20, 2011 by Andrew Butler, PSL
 */
package prisms.arch;

import org.apache.log4j.Logger;

/** Represents a transaction (a single request) in the PRISMS architecture */
public class PrismsTransaction
{
	private static final Logger log = Logger.getLogger(PrismsTransaction.class);

	/** The event that is the base of the tracker in a transaction */
	public static final String TRACKER_ROUTINE = "PRISMS: prisms.process";

	/** Various stages of initialization of a session */
	public static enum Stage
	{
		/** Represents a call to {@link PrismsApplication#configureSession(PrismsSession)} */
		initApp,
		/** Represents a call to {@link ClientConfig#configure(PrismsSession)} */
		initClient,
		/** Represents a call to {@link PrismsSession#init()} */
		initSession,
		/**
		 * Represents a normal PRISMS call, i.e. to
		 * {@link PrismsSession#processAsync(org.json.simple.JSONObject, boolean[])
		 * PrismsSession.processAsync} or
		 * {@link PrismsSession#processSync(org.json.simple.JSONObject) processSync}
		 */
		processEvent;
	}

	/** A listener to be notified when a transaction finishes its work */
	public interface FinishListener
	{
		/** @param trans The transaction that has finished */
		void finished(PrismsTransaction trans);
	}

	private String theID;

	private PrismsSession theSession;

	private final prisms.util.ProgramTracker theTracker;

	private boolean isStarted;

	private boolean isFinished;

	private Stage theStage;

	private org.json.simple.JSONArray theEvents;

	private Thread theThread;

	private prisms.util.ProgramTracker.PrintConfig thePrintConfig;

	long lastLogged;

	private FinishListener [] theListeners;

	private java.util.ArrayList<prisms.util.ProgramTracker.TrackNode> theRoutines;

	PrismsTransaction()
	{
		theTracker = new prisms.util.ProgramTracker(TRACKER_ROUTINE);
		theListeners = new FinishListener [0];
		theRoutines = new java.util.ArrayList<prisms.util.ProgramTracker.TrackNode>();
	}

	/** @return An ID unique to this transaction */
	public String getID()
	{
		return theID;
	}

	/** @return The session that this transaction is for */
	public PrismsSession getSession()
	{
		return theSession;
	}

	/** @return The program tracker for tracking this transaction */
	public prisms.util.ProgramTracker getTracker()
	{
		return theTracker;
	}

	/** @return Whether this transaction is synchronous (one the client is expecting and waiting for) */
	public boolean isSynchronous()
	{
		return theEvents != null;
	}

	/** @return The stage of session initialization that this transactor is for */
	public Stage getStage()
	{
		return theStage;
	}

	/** @param event The event to post as a response to this transaction */
	public void respond(org.json.simple.JSONObject event)
	{
		if(theEvents != null)
			theEvents.add(event);
		else
			theSession.postOutgoingEvent(event);
	}

	/** @return The thread that is using this transaction */
	public Thread getThread()
	{
		return theThread;
	}

	/**
	 * Sets the print config that this transaction will print its data with. Calling this method
	 * with a non-null parameter will ensure that this transaction logs its tracking data.
	 * 
	 * @param config The print config to print this transaction's tracking data with after the
	 *        transaction is complete
	 */
	public void setPrintTracking(prisms.util.ProgramTracker.PrintConfig config)
	{
		thePrintConfig = config;
	}

	/** @param print Whether this transaction should print its tracking data when finished */
	public void setPrintTracking(boolean print)
	{
		if(print && thePrintConfig == null)
			thePrintConfig = new prisms.util.ProgramTracker.PrintConfig();
		else if(!print && thePrintConfig != null)
			thePrintConfig = null;
	}

	/**
	 * Adds a listener to be notified when this transaction finishes
	 * 
	 * @param L The listener to listen for this transaction's finish
	 */
	public void addFinishListenener(FinishListener L)
	{
		theListeners = prisms.util.ArrayUtils.add(theListeners, L);
	}

	/** @return Whether this transaction is currently running */
	public boolean isStarted()
	{
		return isStarted;
	}

	/** @return Whether this transaction has finished executing */
	public boolean isFinished()
	{
		return isFinished;
	}

	/**
	 * Called by the PRISMS architecture. Initializes a transaction with the correct settings.
	 * 
	 * @param session The session that this transaction is for
	 */
	void init(PrismsSession session, Stage stage)
	{
		if(isStarted)
			throw new IllegalStateException("This transaction is already in use");
		isFinished = false;
		isStarted = true;
		theID = prisms.util.PrismsUtils.getRandomString(16);
		theTracker.setName("PRISMS " + stage + " for " + session.getApp().getName()
			+ " session for " + session.getUser().getName());
		theRoutines.add(theTracker.start(TRACKER_ROUTINE));
		theRoutines.add(theTracker.start("PRISMS." + stage));
		// if(theTracker.getCurrentTask().getDepth() != theRoutines.size())
		// log.error("Tracking error!");
		theThread = Thread.currentThread();
		theSession = session;
		theStage = stage;
	}

	void setSynchronous()
	{
		if(theEvents == null)
			theEvents = new org.json.simple.JSONArray();
	}

	org.json.simple.JSONArray finish()
	{
		if(isFinished)
		{
			log.error("This transaction is already finished");
			return null;
		}
		try
		{
			for(FinishListener L : theListeners)
				try
				{
					L.finished(this);
				} catch(Throwable e)
				{
					log.error("Finish listener threw exception: ", e);
				}

			if(theTracker.getCurrentTask().getParent() == null && theRoutines.size() > 1)
			{
				/* ERROR This is a bug that I can't track down. Its manifestation is that the
				 * current task, which should have a depth of 2, has its parent field set to null.
				 * The tracker's node set contains a single node with a single child that is the
				 * same as the current task, but the child task doesn't link to its parent,
				 * resulting in an error when the top-level task is ended.
				 * 
				 * There is some evidence that this may be a result of a concurrency issue, but the
				 * only culprit I can think of would be my ResourcePool class, which I have tested
				 * very thoroughly, I think.
				 * 
				 * The only 2 effects of this bug are slightly skewed performance statistics, since
				 * a task cannot be ended and throws an exception before the tracking data can be
				 * compiled into the session and application tracking sets; and a stack trace being
				 * printed to the log. It's possible that if the tracking feature were used more
				 * than it is, that this bug would cause problems there (this would certainly be
				 * the case if the bug is concurrency, meaning 2 threads are using the same
				 * transaction at the same time). I'll come back to this later, but for now I can't
				 * find it so I'm documenting it, working around it (the return statement below),
				 * and leaving it.
				 * 
				 * Edit: I have found a bug with the merge method in ProgramTracker that may have
				 * been the cause of the problem. It caused the master trackers associated with
				 * sessions and apps to incorporate TrackNodes of the transaction trackers that were
				 * merged into them. When these nodes merged with other nodes, it cause those
				 * intruders to be modified from a different thread than the one where the node was
				 * being used correctly. I am keeping this doc here, but removing the workaround
				 * return so it is more obvious if the problem recurs.
				 */
				log.error("Transaction tracking error");
				// return theEvents;
			}
			try
			{
				while(theRoutines.size() > 0)
					theTracker.end(theRoutines.remove(theRoutines.size() - 1));
			} catch(IllegalStateException e)
			{
				return theEvents;
			}
			theSession.getTrackSet().addTrackData(theTracker);
			theSession.getApp().getTrackSet().addTrackData(theTracker);

			if(thePrintConfig != null
				&& theTracker.getData()[0].getLength() >= thePrintConfig.getDisplayThreshold())
			{
				StringBuilder data = new StringBuilder();
				theTracker.printData(data, thePrintConfig);
				System.out.println(data.toString());
			}

			return theEvents;
		} finally
		{
			clear();
		}
	}

	void clear()
	{
		isFinished = true;
		isStarted = false;
		theThread = null;
		thePrintConfig = null;
		theEvents = null;
		theSession = null;
		theStage = null;
		theID = null;
		if(theListeners.length > 0)
			theListeners = new FinishListener [0];
		theTracker.clear();
	}
}
