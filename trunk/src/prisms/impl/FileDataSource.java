/**
 * FileDataSource.java Created Oct 16, 2007 by Andrew Butler, PSL
 */
package prisms.impl;

import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;

/**
 * A simple version of a PropertyDataSource that reads each property from a different file in a
 * directory
 */
public class FileDataSource implements prisms.arch.event.PropertyDataSource
{
	private static final Logger log = Logger.getLogger(FileDataSource.class);

	private static final String EXTENSION = ".json";

	private File theDir;

	/**
	 * Creates a FileDataSource
	 */
	public FileDataSource()
	{
	}

	public void configure(org.dom4j.Element configEl)
	{
		File dir = new File(configEl.elementText("dir"));
		if(!dir.exists())
		{
			if(!dir.mkdirs())
				throw new IllegalArgumentException(dir + " does not exist and cannot be created");
		}
		else if(!dir.isDirectory())
			throw new IllegalArgumentException(dir + " is not a directory");
		theDir = dir;
	}

	/**
	 * @see prisms.arch.event.PropertyDataSource#getData(java.lang.String)
	 */
	public Object getData(final String propName)
	{
		File [] files = theDir.listFiles(new java.io.FileFilter()
		{
			public boolean accept(File file)
			{
				return file.getName().equalsIgnoreCase(propName + EXTENSION);
			}
		});
		if(files == null || files.length == 0)
			return null;
		java.io.InputStream stream;
		try
		{
			stream = new java.io.FileInputStream(files[0]);
		} catch(IOException e)
		{
			log.error("Error reading file " + files[0], e);
			return null;
		}
		java.io.Reader src = new java.io.BufferedReader(new java.io.InputStreamReader(stream));
		Object ret;
		try
		{
			ret = org.json.simple.JSONValue.parse(src);
		} catch(Throwable e)
		{
			log.error("Contents of file for property " + propName + " do not parse to a JSON type",
				e);
			return null;
		} finally
		{
			try
			{
				src.close();
			} catch(IOException e)
			{}
		}
		return ret;
	}

	/**
	 * @see prisms.arch.event.PropertyDataSource#saveData(java.lang.String, Object)
	 */
	public void saveData(final String propName, Object value)
	{
		java.io.FileOutputStream stream;
		File [] files = theDir.listFiles(new java.io.FileFilter()
		{
			public boolean accept(File file)
			{
				return file.getName().equalsIgnoreCase(propName + EXTENSION);
			}
		});
		if(files != null && files.length == 1)
		{
			try
			{
				stream = new java.io.FileOutputStream(files[0]);
			} catch(IOException e)
			{
				log.error("Cannot write to file " + files[0], e);
				return;
			}
		}
		else
		{
			String absPath = theDir.getAbsolutePath();
			if(!absPath.endsWith(File.separator))
				absPath += File.separator;
			try
			{
				stream = new java.io.FileOutputStream(absPath + propName + EXTENSION);
			} catch(IOException e)
			{
				log.error("Cannot write to file " + absPath + propName + EXTENSION, e);
				return;
			}
		}
		try
		{
			if(value instanceof String)
				value = "\"" + value + "\"";
			stream.write(("" + value).getBytes());
		} catch(IOException e)
		{
			try
			{
				log.error("Cannot write data to file " + stream.getFD(), e);
			} catch(IOException e2)
			{
				log.error("Cannot write data to file", e);
			}
		} finally
		{
			try
			{
				stream.close();
			} catch(IOException e)
			{}
		}
	}
}
