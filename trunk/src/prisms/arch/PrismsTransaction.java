/*
 * PrismsRequest.java Created Jan 20, 2011 by Andrew Butler, PSL
 */
package prisms.arch;

import org.apache.log4j.Logger;

import prisms.util.ProgramTracker;

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

	private ProgramTracker.PrintConfig theDefaultPrintConfig;

	private PrismsSession theSession;

	private final ProgramTracker theTracker;

	private boolean isStarted;

	private boolean isFinished;

	private Stage theStage;

	private org.json.simple.JSONArray theEvents;

	private Thread theThread;

	private ProgramTracker.PrintConfig thePrintConfig;

	private FinishListener [] theListeners;

	private java.util.ArrayList<ProgramTracker.TrackNode> theRoutines;

	PrismsTransaction(ProgramTracker.PrintConfig defaultPrintConfig)
	{
		theDefaultPrintConfig = defaultPrintConfig;
		theTracker = new ProgramTracker(TRACKER_ROUTINE);
		theListeners = new FinishListener [0];
		theRoutines = new java.util.ArrayList<ProgramTracker.TrackNode>();
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
	public ProgramTracker getTracker()
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
	public void setPrintTracking(ProgramTracker.PrintConfig config)
	{
		thePrintConfig = config;
	}

	/** @param print Whether this transaction should print its tracking data when finished */
	public void setPrintTracking(boolean print)
	{
		if(print)
		{
			if(thePrintConfig == null)
				thePrintConfig = new ProgramTracker.PrintConfig();
			else
			{
				thePrintConfig = thePrintConfig.clone();
				if(thePrintConfig instanceof prisms.arch.PrismsEnv.GlobalPrintConfig)
				{
					prisms.arch.PrismsEnv.GlobalPrintConfig pc = (prisms.arch.PrismsEnv.GlobalPrintConfig) thePrintConfig;
					pc.setPrintThreshold(0);
					pc.setDebugThreshold(Long.MAX_VALUE);
				}
				thePrintConfig.setTaskDisplayThreshold(0);
			}
		}
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
				/* This is the remnants of a workaround for a bug dealing with the tracking. The
				 * tracking would get into an inconsistent state. I believe I have fixed this bug,
				 * but I am leaving the indication that it has recurred in case it does. */
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

			long runTime = theTracker.getData()[0].getLength();
			if(thePrintConfig != null && runTime >= thePrintConfig.getTaskDisplayThreshold())
			{
				StringBuilder data = new StringBuilder();
				theTracker.printData(data, thePrintConfig);
				if(thePrintConfig instanceof prisms.arch.PrismsEnv.GlobalPrintConfig)
				{
					prisms.arch.PrismsEnv.GlobalPrintConfig pc = (prisms.arch.PrismsEnv.GlobalPrintConfig) thePrintConfig;
					if(runTime >= pc.getErrorThreshold())
						log.error(data.toString());
					else if(runTime >= pc.getWarningThreshold())
						log.warn(data.toString());
					else if(runTime >= pc.getInfoThreshold())
						log.info(data.toString());
					else if(runTime >= pc.getDebugThreshold())
						log.debug(data.toString());
					else if(runTime >= pc.getPrintThreshold())
						System.out.println(data.toString());
				}
				else
					log.debug(data.toString());
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
		thePrintConfig = theDefaultPrintConfig;
		theEvents = null;
		theSession = null;
		theStage = null;
		theID = null;
		if(theListeners.length > 0)
			theListeners = new FinishListener [0];
		theTracker.clear();
	}
}
