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
		processEvent,
		/** Represents a transaction instantiated outside the PRISMS framework */
		external;
	}

	/** A listener to be notified when a transaction finishes its work */
	public interface FinishListener
	{
		/** @param trans The transaction that has finished */
		void finished(PrismsTransaction trans);
	}

	private String theID;

	private ProgramTracker.PrintConfig theDefaultPrintConfig;

	private PrismsApplication theApp;

	private PrismsSession theSession;

	private final ProgramTracker theTracker;

	private java.lang.management.ThreadMXBean theThreadBean;

	private boolean isStarted;

	private boolean isFinished;

	private Stage theStage;

	private org.json.simple.JSONArray theEvents;

	private Thread theThread;

	private ProgramTracker.PrintConfig thePrintConfig;

	private long theStartCpuTime;

	private boolean useJMX;

	private FinishListener [] theListeners;

	private java.util.ArrayList<ProgramTracker.TrackNode> theRoutines;

	int theDuplicateStartCount;

	PrismsTransaction(ProgramTracker.PrintConfig defaultPrintConfig)
	{
		theDefaultPrintConfig = defaultPrintConfig;
		theTracker = new ProgramTracker("Not in use");
		theListeners = new FinishListener [0];
		theRoutines = new java.util.ArrayList<ProgramTracker.TrackNode>();
		theThreadBean = java.lang.management.ManagementFactory.getThreadMXBean();
		useJMX = true;
	}

	/** @return An ID unique to this transaction */
	public String getID()
	{
		return theID;
	}

	/** @return The application that this transaction is for */
	public PrismsApplication getApp()
	{
		return theApp;
	}

	/** @return The session that this transaction is for. May be null. */
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
		else if(theSession != null)
			theSession.postOutgoingEvent(event);
		else
			throw new IllegalStateException("This transaction is global for " + theApp
				+ ": Events cannot be posted to it.");
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

	/** @return The amount of processing resources this transaction has used so far, in microseconds */
	public long getCpuTime()
	{
		if(theThreadBean == null || !useJMX || !isStarted || isFinished)
			return -1;
		return (theThreadBean.getCurrentThreadCpuTime() - theStartCpuTime) / 1000;
	}

	/**
	 * Called by the PRISMS architecture. Initializes a transaction with the correct settings.
	 * 
	 * @param session The session that this transaction is for
	 */
	void init(PrismsSession session, Stage stage)
	{
		theTracker.setName("PRISMS " + stage + " for " + session.getApp().getName()
			+ " session for " + session.getUser().getName());
		init(session.getApp(), stage);
		theSession = session;
	}

	void init(PrismsApplication app, Stage stage)
	{
		if(isStarted)
			throw new IllegalStateException("This transaction is already in use");
		if(theThreadBean != null && useJMX)
			try
			{
				theStartCpuTime = theThreadBean.getCurrentThreadCpuTime();
			} catch(Throwable e)
			{
				useJMX = false;
			}
		isFinished = false;
		isStarted = true;
		theID = prisms.util.PrismsUtils.getRandomString(16);
		theApp = app;
		theThread = Thread.currentThread();
		theStage = stage;
		if(theTracker.getName().equals("Not in use"))
		{
			if(app != null)
				theTracker.setName("PRISMS " + stage + " for " + app.getName() + " (global)");
			else
				theTracker.setName("PRISMS " + stage);
		}
		theRoutines.add(theTracker.start("PRISMS." + stage));
	}

	void setSynchronous()
	{
		if(theEvents == null)
			theEvents = new org.json.simple.JSONArray();
	}

	org.json.simple.JSONArray getEvents()
	{
		return theEvents;
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
			{
				prisms.util.ProgramTracker.TrackNode track = theTracker
					.start("Transaction Finish Listener " + L);
				try
				{
					L.finished(this);
				} catch(Throwable e)
				{
					log.error("Finish listener threw exception: ", e);
				} finally
				{
					theTracker.end(track);
				}
			}

			while(theRoutines.size() > 0)
				theTracker.end(theRoutines.remove(theRoutines.size() - 1));
			if(theSession != null)
				theSession.getTrackSet().addTrackData(theTracker);
			if(theApp != null)
				theApp.getTrackSet().addTrackData(theTracker);

			if(theThreadBean != null && useJMX && theApp != null)
			{
				long cpuTime = theThreadBean.getCurrentThreadCpuTime() - theStartCpuTime;
				cpuTime /= 1000; // Nanos to micros
				if(theSession != null)
					theApp.getEnvironment().addUserCPU(theSession.getUser(), cpuTime);
				theApp.addCpuTime(cpuTime);
			}

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
		theApp = null;
		theStage = null;
		theID = null;
		theStartCpuTime = 0;
		if(theListeners.length > 0)
			theListeners = new FinishListener [0];
		theTracker.clear();
		theTracker.setName("Not in use");
		theDuplicateStartCount = 0;
	}
}
