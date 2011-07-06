/**
 * ReaUtils.java Created Apr 22, 2009 by Andrew Butler, PSL
 */
package prisms.records;

import prisms.util.ObfuscatingStream;

/**
 * A utility to obfuscate and compress export data for download to the client and then read it on
 * import
 */
public class OldRecordUtils
{
	/**
	 * Writes export data to a stream. The data is first zipped, then obfuscated to prevent human
	 * modification.
	 * 
	 * @param json The input plain-text data to write
	 * @param os The output stream to write the data to
	 * @throws java.io.IOException If an error occurs writing the data
	 */
	public static void writeExportData(String json, java.io.OutputStream os)
		throws java.io.IOException
	{
		java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
			ObfuscatingStream.obfuscate(os));
		java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry("export.json");
		zos.putNextEntry(zipEntry);
		zos.setLevel(9);
		java.io.Writer writer = new java.io.OutputStreamWriter(zos);
		writer.write(json, 0, json.length());
		writer.flush();
		writer.close();
		zos.finish();
		zos.close();
	}

	/**
	 * Reads previously exported data from a stream
	 * 
	 * @param is The stream to read the data from
	 * @return The plain-text data that was stored in the stream
	 * @throws java.io.IOException If an error occurs reading the data
	 */
	public static String readImportData(java.io.InputStream is) throws java.io.IOException
	{
		java.util.zip.ZipInputStream zis;
		zis = new java.util.zip.ZipInputStream(ObfuscatingStream.unobfuscate(is));
		java.io.Reader reader = new java.io.InputStreamReader(zis);
		zis.getNextEntry();
		StringBuilder ret = new StringBuilder();
		char [] read = new char [1024];
		int count = reader.read(read);
		while(count > 0)
		{
			ret.append(read, 0, count);
			count = reader.read(read);
		}
		reader.close();
		zis.close();
		return ret.toString();
	}

	/**
	 * Tests this class, "exporting" args[0] and re-"importing" it, displaying all the steps.
	 * 
	 * @param args The command-line arguments
	 */
	public static void main(String [] args)
	{
		try
		{
			/*System.out.println("Obfuscating \"" + args[0] + "\"");
			java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
			writeExportData(args[0], os);
			os.close();
			byte [] bytes = os.toByteArray();
			System.out.println("Obfuscated data: " + prisms.util.ArrayUtils.toString(bytes));
			String result = readImportData(new java.io.ByteArrayInputStream(bytes));
			System.out.println("Unobfuscated to \"" + result + "\"");
			*/

			java.io.FileInputStream is = new java.io.FileInputStream(args[0]);
			String result = readImportData(is);

			java.io.FileWriter fw = new java.io.FileWriter("C:\\exportTextOut.txt");
			fw.write(result);
			fw.close();

			System.out.println("Done!");

		} catch(java.io.IOException e)
		{
			throw new IllegalStateException("Error reading/writing data", e);
		}
	}
}
