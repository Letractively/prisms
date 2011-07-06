/**
 * ExportPlugin.java Created Apr 16, 2009 by Andrew Butler, PSL
 */
package prisms.impl;

import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;

/**
 * A simple example implementation of a DownloadPlugin that simply returns the event as a JSON
 * download
 */
public class SampleDownloadPlugin implements prisms.arch.DownloadPlugin
{
	private PrismsSession theSession;

	volatile String theText;

	volatile int theProgress;

	volatile int theScale;

	volatile boolean isCanceled;

	volatile boolean isFinished;

	String theDownloadContent;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
	}

	public void initClient()
	{
	}

	public void processEvent(final JSONObject evt)
	{
		theSession.getUI().confirm(
			"Are you sure you want to do a download?  This is just a sample, but"
				+ " a download may be an expensive operation.", new prisms.ui.UI.ConfirmListener()
			{
				public void confirmed(boolean confirm)
				{
					if(confirm)
						generateDownload(evt);
				}
			});
	}

	/**
	 * This method generates the download content, then sends the event for the client to initialize
	 * the download
	 * 
	 * @param event the event that sparked the initial download request
	 */
	void generateDownload(JSONObject event)
	{
		theProgress = 0;
		theScale = 10;
		isCanceled = false;
		isFinished = false;
		theText = "Starting download generation";
		/* Here is where we would generate our download content--use a progress dialog if this
		 * content takes a long time */
		theSession.getUI().startTimedTask(new prisms.ui.UI.ProgressInformer()
		{
			public void cancel() throws IllegalStateException
			{
				isCanceled = true;
			}

			public int getTaskProgress()
			{
				return theProgress;
			}

			public int getTaskScale()
			{
				return theScale;
			}

			public String getTaskText()
			{
				System.out.println("Getting text: " + theText);
				return theText;
			}

			public boolean isCancelable()
			{
				return true;
			}

			public boolean isTaskDone()
			{
				return isFinished;
			}
		});
		// Update progress as we generate the content
		while(theProgress < theScale)
		{
			if(isCanceled)
			{
				theProgress = theScale;
				continue;
			}
			theProgress++;
			theText = "Counting to 10: " + theProgress;
			System.out.println("Changing text to " + theText);
			try
			{
				Thread.sleep(1000);
			} catch(InterruptedException e)
			{}
		}
		// Set the content in a variable for the actual download
		theDownloadContent = event.toString();
		if(!isCanceled)
		{
			/* The download event contains must have no "plugin" entry, the "method" entry must be
			 * "doDownload", the "downloadPlugin" and "downloadMethod" entries will be transformed
			 * into the "plugin" and "method" entries in the event passed to the doDownload method.
			 * Any other data you include in this event will be passed on to the download event
			 * as-is */
			event.put("downloadPlugin", event.get("plugin"));
			event.remove("plugin");
			event.put("downloadMethod", event.get("method"));
			event.put("method", "doDownload");
			theSession.postOutgoingEvent(event);
		}
		// Don't let the progress dialog think it is finished until you've sent the download event
		isFinished = true;
	}

	public String getContentType(JSONObject event)
	{
		return "text/json";
	}

	public String getFileName(JSONObject event)
	{
		return "inputEvent.json";
	}

	public void doDownload(JSONObject event, java.io.OutputStream stream)
		throws java.io.IOException
	{
		if(theDownloadContent == null)
			throw new IllegalArgumentException("No download initiated");
		new java.io.OutputStreamWriter(stream).write(theDownloadContent);
		theDownloadContent = null;
	}
}
