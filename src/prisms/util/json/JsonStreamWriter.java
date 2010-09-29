/*
 * JsonStreamWriter.java Created Aug 20, 2010 by Andrew Butler, PSL
 */
package prisms.util.json;

import java.io.IOException;

import prisms.util.json.SAJParser.ParseNode;
import prisms.util.json.SAJParser.ParseToken;

/**
 * Writes JSON content to a stream
 */
public class JsonStreamWriter implements JsonSerialWriter
{
	private final java.io.Writer theWriter;

	private java.util.ArrayList<ParseNode> thePath;

	private StringBuilder theSB;

	private boolean useFormalJson;

	private int theLineNumber;

	private String theFormatIndent;

	/**
	 * @return Whether this writer writes formal JSON
	 */
	public boolean isFormal()
	{
		return useFormalJson;
	}

	/**
	 * Sets whether this writer sticks to the formal JSON schema. If false, this writer can be
	 * slightly more efficient (space-wise) by:
	 * <ul>
	 * <li>Removing quotation marks from property names that contain no white space or syntax
	 * characters</li>
	 * <li>Sending large integers in hexadecimal format</li>
	 * </ul>
	 * 
	 * @param formal Whether this writer writes formal JSON
	 */
	public void setFormal(boolean formal)
	{
		useFormalJson = formal;
	}

	/**
	 * @return The string used to indent JSON content that is written, or null if this writer does
	 *         not format its data
	 */
	public String getFormatIndent()
	{
		return theFormatIndent;
	}

	/**
	 * @param indent The string to use to indent JSON content that is written, or null if this
	 *        writer should not format its data
	 */
	public void setFormatIndent(String indent)
	{
		theFormatIndent = indent;
	}

	/**
	 * @return The number of lines that have been written by this writer.
	 */
	public int getLineNumber()
	{
		return theLineNumber;
	}

	/**
	 * @param writer The writer to write the JSON content to
	 */
	public JsonStreamWriter(java.io.Writer writer)
	{
		theWriter = writer;
		thePath = new java.util.ArrayList<ParseNode>();
		theSB = new StringBuilder();
	}

	public void startObject() throws IOException
	{
		content(ParseToken.OBJECT);
		thePath.add(new ParseNode(ParseToken.OBJECT));
		theWriter.write('{');
	}

	/**
	 * @param ch The character to test
	 * @return The integer representation of the character that should be printed after the '\' to
	 *         represent the escaped character in a JSON string if it needs escaping; otherwise -1
	 */
	public static int needsEscape(char ch)
	{
		switch(ch)
		{
		case '"':
		case '\\':
			return ch;
		case '\u2044':
			return '/';
		case '\n':
			return 'n';
		case '\r':
			return 'r';
		case '\t':
			return 't';
		case '\f':
			return 'f';
		case '\b':
			return 'b';
		default:
			return -1;
		}
	}

	public void startProperty(String name) throws IOException
	{
		ParseNode top = top();
		if(top == null)
			throw new IllegalStateException("No object started!!");
		switch(top.token)
		{
		case OBJECT:
			if(top.hasContent())
				theWriter.write(',');
			else
				top.setHasContent();
			break;
		case ARRAY:
			throw new IllegalStateException("No properties allowed in array");
		case PROPERTY:
			if(!top.hasContent())
				theWriter.write("null");
			pop();
			theWriter.write(',');
			break;
		}
		writeLine();
		theSB.append(name);
		boolean quoted = useFormalJson;
		if(!quoted)
		{
			for(int c = 0; c < name.length(); c++)
			{
				char ch = name.charAt(c);
				if(SAJParser.isWhiteSpace(ch) || SAJParser.isSyntax(ch))
					quoted = true;
				int esc = needsEscape(ch);
				if(esc >= 0)
				{
					theSB.insert(c, '\\');
					c++;
					theSB.setCharAt(c, (char) esc);
				}
			}
		}
		if(quoted)
		{
			theSB.insert(0, '"');
			theSB.append('"');
		}
		theSB.append(':');
		if(theFormatIndent != null)
			theSB.append(' ');
		theWriter.write(theSB.toString());
		theSB.setLength(0);
		thePath.add(new ParseNode(ParseToken.PROPERTY));
	}

	public void endObject() throws IOException
	{
		ParseNode top = top();
		switch(top.token)
		{
		case OBJECT:
			break;
		case ARRAY:
			throw new IllegalStateException("Can't end object--top item is an array");
		case PROPERTY:
			if(!top.hasContent())
				theWriter.write("null");
			pop();
			break;
		}
		pop();
		writeLine();
		theWriter.write('}');
	}

	public void startArray() throws IOException
	{
		content(ParseToken.ARRAY);
		thePath.add(new ParseNode(ParseToken.ARRAY));
		theWriter.write('[');
	}

	public void endArray() throws IOException
	{
		ParseNode top = top();
		if(top == null)
			throw new IllegalStateException("No array started!");
		switch(top.token)
		{
		case OBJECT:
			throw new IllegalStateException("Can't end array--top item is an object");
		case ARRAY:
			break;
		case PROPERTY:
			throw new IllegalStateException("Can't end array--top item is a property");
		}
		pop();
		writeLine();
		theWriter.write(']');
	}

	public void writeString(String value) throws IOException
	{
		if(value == null)
		{
			writeNull();
			return;
		}
		theSB.append(value);
		for(int c = 0; c < theSB.length(); c++)
		{
			char ch = theSB.charAt(c);
			int esc = needsEscape(ch);
			if(esc >= 0)
			{
				theSB.insert(c, '\\');
				c++;
				theSB.setCharAt(c, (char) esc);
			}
		}
		theSB.insert(0, '"');
		theSB.append('"');
		content(null);
		theWriter.write(theSB.toString());
		theSB.setLength(0);
	}

	public void writeNumber(Number value) throws IOException
	{
		if(value == null)
		{
			writeNull();
			return;
		}
		content(null);
		boolean written = false;
		if(value instanceof Integer || value instanceof Long)
		{
			long val = value.longValue();
			if(val >= 1000 || val <= -1000)
			{
				written = true;
				if(val < 0)
				{
					theSB.append('-');
					val = -val;
				}
				theSB.append('0');
				theSB.append('x');
				theSB.append(Long.toHexString(val));
				theWriter.write(theSB.toString());
			}
		}
		if(!written)
		{
			theSB.append(value);
			theWriter.write(theSB.toString());
		}
		theSB.setLength(0);
	}

	public void writeBoolean(boolean value) throws IOException
	{
		content(null);
		theWriter.write(value ? "true" : "false");
	}

	public void writeNull() throws IOException
	{
		content(null);
		theWriter.write("null");
	}

	/**
	 * Call this method <b>before</b> writing a custom JSON item to the stream independent of this
	 * JSON writer
	 * 
	 * @throws IOException If an error occurs prepping the stream for the coming value
	 */
	public void writeCustomValue() throws IOException
	{
		content(null);
	}

	/**
	 * Finishes writing the JSON content that has been started
	 * 
	 * @throws IOException If an error occurs writing to the stream
	 */
	public void close() throws IOException
	{
		ParseNode top = top();
		while(top != null)
		{
			switch(top.token)
			{
			case OBJECT:
				endObject();
				break;
			case ARRAY:
				endArray();
				break;
			case PROPERTY:
				endObject();
				break;
			}
			top = top();
		}
	}

	private void content(ParseToken token) throws IOException
	{
		ParseNode top = top();
		if(top != null)
			switch(top.token)
			{
			case OBJECT:
				throw new IllegalStateException("Property name missing in object");
			case ARRAY:
				if(top.hasContent())
					theWriter.write(',');
				top.setHasContent();
				if(token == null)
					writeLine();
				break;
			case PROPERTY:
				pop();
				break;
			}
	}

	private void writeLine() throws IOException
	{
		if(theFormatIndent == null)
			return;
		theLineNumber++;
		theSB.append('\n');
		for(int i = 0; i < getDepth(); i++)
			theSB.append(theFormatIndent);
		theWriter.write(theSB.toString());
		theSB.setLength(0);
	}

	/**
	 * @return The current depth of the content being written
	 */
	public int getDepth()
	{
		return thePath.size();
	}

	/**
	 * @return The node that is currently being written
	 */
	public ParseNode top()
	{
		if(thePath.size() == 0)
			return null;
		return thePath.get(thePath.size() - 1);
	}

	/**
	 * @param depth The depth of the item to get
	 * @return The item that is being written at the given depth, or null if depth>
	 *         {@link #getDepth()}
	 */
	public ParseNode fromTop(int depth)
	{
		if(thePath.size() <= depth)
			return null;
		return thePath.get(thePath.size() - depth - 1);
	}

	private void pop()
	{
		thePath.remove(thePath.size() - 1);
	}

	/**
	 * Tests this class
	 * 
	 * @param args Command-line args, ignored
	 */
	public static void main(String [] args)
	{
		JsonStreamWriter writer = new JsonStreamWriter(new java.io.Writer()
		{
			@Override
			public void write(char [] cbuf, int off, int len) throws IOException
			{
				System.out.print(new String(cbuf, off, len));
			}

			@Override
			public void flush() throws IOException
			{
				System.out.flush();
			}

			@Override
			public void close() throws IOException
			{
			}
		});
		writer.setFormatIndent("    ");
		try
		{
			writer.startArray();
			writer.startObject();
			writer.startProperty("prop1");
			writer.writeString("Value");
			writer.startProperty("prop2\\");
			writer.startArray();
			writer.writeNumber(new Integer(5));
			writer.writeBoolean(true);
			writer.endArray();
			writer.startProperty("prop3");
			writer.writeNull();
			writer.endObject();
			writer.close();
		} catch(IOException e)
		{
			e.printStackTrace();
		}
	}
}
