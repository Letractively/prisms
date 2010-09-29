/*
 * JsonUtils.java Created Aug 25, 2010 by Andrew Butler, PSL
 */
package prisms.util.json;

import java.io.IOException;

import prisms.util.json.SAJParser.ParseState;

/**
 * Formats a JSON file to be more easily read by a human
 */
public class JsonFormatter
{
	/**
	 * Formats a file
	 * 
	 * @param args The location of the file to format
	 * @throws IOException If an error occurs reading or writing the file
	 */
	public static void main(String [] args) throws IOException
	{
		StringBuilder contents = new StringBuilder();
		{
			java.io.Reader fileReader = new java.io.FileReader(args[0]);
			int read = fileReader.read();
			while(read >= 0)
			{
				contents.append((char) read);
				read = fileReader.read();
			}
			fileReader.close();
		}
		final java.io.FileWriter fileWriter;
		try
		{
			fileWriter = new java.io.FileWriter(args[0]);
		} catch(IOException e1)
		{
			e1.printStackTrace();
			return;
		}
		java.io.Writer writer = new java.io.Writer()
		{
			@Override
			public void write(char [] cbuf, int off, int len) throws IOException
			{
				System.out.print(new String(cbuf, off, len));
				fileWriter.write(cbuf, off, len);
			}

			@Override
			public void flush() throws IOException
			{
				System.out.flush();
				fileWriter.flush();
			}

			@Override
			public void close() throws IOException
			{
			}
		};
		final JsonStreamWriter jsonWriter = new JsonStreamWriter(writer);
		jsonWriter.setFormatIndent("\t");
		SAJParser.ParseHandler handler = new SAJParser.ParseHandler()
		{
			public void startObject(ParseState state)
			{
				try
				{
					jsonWriter.startObject();
				} catch(IOException e)
				{
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			public void startProperty(ParseState state, String name)
			{
				try
				{
					jsonWriter.startProperty(name);
				} catch(IOException e)
				{
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			public void separator(ParseState state)
			{
			}

			public void endProperty(ParseState state, String propName)
			{
			}

			public void endObject(ParseState state)
			{
				try
				{
					jsonWriter.endObject();
				} catch(IOException e)
				{
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			public void startArray(ParseState state)
			{
				try
				{
					jsonWriter.startArray();
				} catch(IOException e)
				{
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			public void endArray(ParseState state)
			{
				try
				{
					jsonWriter.endArray();
				} catch(IOException e)
				{
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			public void valueBoolean(ParseState state, boolean value)
			{
				try
				{
					jsonWriter.writeBoolean(value);
				} catch(IOException e)
				{
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			public void valueString(ParseState state, String value)
			{
				try
				{
					jsonWriter.writeString(value);
				} catch(IOException e)
				{
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			public void valueNumber(ParseState state, Number value)
			{
				try
				{
					jsonWriter.writeNumber(value);
				} catch(IOException e)
				{
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			public void valueNull(ParseState state)
			{
				try
				{
					jsonWriter.writeNull();
				} catch(IOException e)
				{
					throw new IllegalStateException("Could not write JSON", e);
				}
			}

			public void whiteSpace(ParseState state, String ws)
			{
			}

			public void comment(ParseState state, String fullComment, String content)
			{
			}

			public Object finalValue()
			{
				return null;
			}

			public void error(ParseState state, String error)
			{
				System.err.println("\nError at Line " + state.getLineNumber() + ", char "
					+ state.getCharNumber() + "--(Line " + jsonWriter.getLineNumber()
					+ " formatted)");
			}
		};
		try
		{
			new SAJParser().parse(new java.io.StringReader(contents.toString()), handler);
		} catch(SAJParser.ParseException e)
		{
			e.printStackTrace();
		} finally
		{
			fileWriter.flush();
			fileWriter.close();
		}
	}
}
