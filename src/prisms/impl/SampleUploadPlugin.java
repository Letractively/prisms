/*
 * SampleUploadPlugin.java Created Apr 16, 2009 by Andrew Butler, PSL
 */
package prisms.impl;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import prisms.arch.PrismsSession;

/**
 * A simple example implementation of an UploadPlugin that simply prints information on the uploaded
 * file
 */
public class SampleUploadPlugin implements prisms.arch.UploadPlugin
{
	private static final Logger log = Logger.getLogger(SampleUploadPlugin.class);

	private PrismsSession theSession;

	volatile String theText;

	volatile int theProgress;

	volatile int theScale;

	volatile boolean isCanceled;

	volatile boolean isFinished;

	public void initPlugin(PrismsSession session, prisms.arch.PrismsConfig config)
	{
		theSession = session;
	}

	public void initClient()
	{
	}

	public void processEvent(JSONObject evt)
	{
		startUpload(evt);
	}

	void startUpload(JSONObject evt)
	{
		/*The uploader is different than the downloader in that client events sent from the
		 * doUpload method, like progress bars, will be picked up, so all we have to do here is send
		 * the upload event 
		 */
		evt.put("uploadPlugin", evt.get("plugin"));
		evt.remove("plugin");
		evt.put("uploadMethod", evt.get("method"));
		evt.put("method", "doUpload");
		evt.put("message", "Select the file to upload");
		theSession.postOutgoingEvent(evt);
	}

	public void doUpload(JSONObject event, String fileName, String contentType,
		java.io.InputStream input, long size) throws java.io.IOException
	{
		theProgress = 0;
		theScale = 5;
		isCanceled = false;
		isFinished = false;
		theText = "Uploading file " + fileName;
		prisms.ui.UI ui = theSession.getUI();
		/* Here we start the progress bar that will run after the upload. This works to do it here
		 * for the upload but not the download*/
		ui.startTimedTask(new prisms.ui.UI.ProgressInformer()
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
				return isCanceled || isFinished;
			}
		});
		theProgress++;
		try
		{
			if(contentType.startsWith("text"))
			{
				java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
				int read = input.read();
				while(read >= 0)
				{
					bos.write(read);
					read = input.read();
				}
				String content = new String(bos.toByteArray());
				log.info("Uploaded text file " + fileName + " of type " + contentType + " of size "
					+ size + ":\n" + content);
			}
			else
			{
				log.info("Uploaded binary file " + fileName + " of type " + contentType
					+ " of size " + size);
			}
		} finally
		{
			input.close();
		}
		// Update progress as we process the data
		while(theProgress < theScale)
		{
			if(isCanceled)
			{
				theProgress = theScale;
				continue;
			}
			theProgress++;
			theText = "Counting to " + theScale + ": " + theProgress;
			System.out.println("Changing text to " + theText);
			try
			{
				Thread.sleep(1000);
			} catch(InterruptedException e)
			{}
		}
		isFinished = true;
	}

	/**
	 * Uploads a file to the localhost server as a test
	 * 
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String [] args)
	{
		prisms.util.PrismsServiceConnector conn = new prisms.util.PrismsServiceConnector(
			"http://localhost/WeatherEffectsServlet/prisms", "MANAGER", "Upload Test", "admin");
		conn.getConnector().setHostnameVerifier(new javax.net.ssl.HostnameVerifier()
		{
			public boolean verify(String hostname, javax.net.ssl.SSLSession session)
			{
				return true;
			}
		});
		try
		{
			conn.getConnector().setTrustManager(new javax.net.ssl.X509TrustManager()
			{
				public void checkClientTrusted(java.security.cert.X509Certificate[] arg0,
					String arg1) throws java.security.cert.CertificateException
				{

				}

				public void checkServerTrusted(java.security.cert.X509Certificate[] arg0,
					String arg1) throws java.security.cert.CertificateException
				{

				}

				public java.security.cert.X509Certificate[] getAcceptedIssuers()
				{
					return null;
				}
			});
		} catch(java.security.GeneralSecurityException e)
		{
			throw new IllegalStateException(e.getMessage(), e);
		}
		conn.setPassword("admin");
		conn.getConnector().setFollowRedirects(Boolean.FALSE);
		java.io.OutputStream stream = null;
		java.io.FileInputStream in = null;
		try
		{
			conn.init();
			stream = conn.uploadData("build.xml", "text/xml", "Upload", "upload", "testName",
				"testValue");
			in = new java.io.FileInputStream("build.xml");
			int read = in.read();
			while(read >= 0)
			{
				stream.write(read);
				read = in.read();
			}
			stream.close();
			System.out.println("Upload successful");
		} catch(java.io.IOException e)
		{
			throw new IllegalStateException(e.getMessage(), e);
		} finally
		{
			try
			{
				if(in != null)
					in.close();
				conn.logout(true);
			} catch(java.io.IOException e)
			{
				throw new IllegalStateException(e.getMessage(), e);
			}
		}
	}
}
