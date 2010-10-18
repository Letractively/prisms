/*
 * SAJParser.java Created Jul 23, 2010 by Andrew Butler, PSL
 */
package prisms.util.json;

import java.io.IOException;
import java.io.Reader;

/**
 * Parses JSON from a stream incrementally, notifying a handler as each piece is parsed
 */
public class SAJParser
{
	/**
	 * Handles the JSON input from the parser
	 */
	public interface ParseHandler
	{
		/**
		 * Called when the start of a JSON object is encountered
		 * 
		 * @param state The current parsing state
		 */
		void startObject(ParseState state);

		/**
		 * Called when the start of a property within JSON object is encountered
		 * 
		 * @param state The current parsing state
		 * @param name The name of the new property
		 */
		void startProperty(ParseState state, String name);

		/**
		 * Called when a separator is encountered in the JSON (comma or colon)
		 * 
		 * @param state The current parsing state
		 */
		void separator(ParseState state);

		/**
		 * Called when the end of a property within a JSON object is encountered
		 * 
		 * @param state The current parsing state
		 * @param propName The name of the property that was parsed
		 */
		void endProperty(ParseState state, String propName);

		/**
		 * Called when the end of a JSON object is encountered
		 * 
		 * @param state The current parsing state
		 */
		void endObject(ParseState state);

		/**
		 * Called when the start of a JSON array is encountered
		 * 
		 * @param state The current parsing state
		 */
		void startArray(ParseState state);

		/**
		 * Called when the end of a JSON array is encountered
		 * 
		 * @param state The current parsing state
		 */
		void endArray(ParseState state);

		/**
		 * Called when a boolean is encountered in the JSON
		 * 
		 * @param state The current parsing state
		 * @param value The boolean value that was parsed
		 */
		void valueBoolean(ParseState state, boolean value);

		/**
		 * Called when a string is encountered in the JSON
		 * 
		 * @param state The current parsing state
		 * @param value The string value that was parsed
		 */
		void valueString(ParseState state, String value);

		/**
		 * Called when a number is encountered in the JSON
		 * 
		 * @param state The current parsing state
		 * @param value The number value that was parsed
		 */
		void valueNumber(ParseState state, Number value);

		/**
		 * Called when a null value is encountered
		 * 
		 * @param state The current parsing state
		 */
		void valueNull(ParseState state);

		/**
		 * Called when the white space is encountered
		 * 
		 * @param state The current parsing state
		 * @param ws The white space that was ignored
		 */
		void whiteSpace(ParseState state, String ws);

		/**
		 * Called when a line- or block-style comment is encountered
		 * 
		 * @param state The current parsing state
		 * @param fullComment The full comment
		 * @param content The content of the comment
		 */
		void comment(ParseState state, String fullComment, String content);

		/**
		 * Called when parsing has finished
		 * 
		 * @return The value that this handler produced from the parsing
		 */
		Object finalValue();

		/**
		 * Called when an error is encountered in the JSON content that prevents parsing from
		 * continuing. This method is always called just before the parser throws a
		 * {@link ParseException}.
		 * 
		 * @param state The parsing state at the time of the error
		 * @param error The description of the error that occurred in the JSON content
		 */
		void error(ParseState state, String error);
	}

	/**
	 * A simple handler that uses the parser's notifications to create the JSON value as it was
	 * represented in the stream.
	 */
	public static class DefaultHandler implements ParseHandler
	{
		private ParseState theState;

		private Object theValue;

		private java.util.ArrayList<Object> thePath;

		/** Creates a DefaultHandler */
		public DefaultHandler()
		{
			thePath = new java.util.ArrayList<Object>();
		}

		/** @return The current state of parsing */
		public ParseState getState()
		{
			return theState;
		}

		/**
		 * Resets this handler's state. This is only useful in order to reuse the handler after a
		 * parsing error.
		 */
		public void reset()
		{
			theState = null;
			theValue = null;
			thePath.clear();
		}

		public void startObject(ParseState state)
		{
			theState = state;
			org.json.simple.JSONObject value = new org.json.simple.JSONObject();
			if(top() instanceof org.json.simple.JSONArray)
				((org.json.simple.JSONArray) top()).add(value);
			else if(top() instanceof org.json.simple.JSONObject)
				((org.json.simple.JSONObject) top()).put(state.fromTop(1).getPropertyName(), value);
			thePath.add(value);
		}

		public void startProperty(ParseState state, String name)
		{
			theState = state;
		}

		public void separator(ParseState state)
		{
			theState = state;
		}

		public void endProperty(ParseState state, String propName)
		{
			theState = state;
		}

		public void endObject(ParseState state)
		{
			theState = state;
			pop();
		}

		public void startArray(ParseState state)
		{
			theState = state;
			org.json.simple.JSONArray value = new org.json.simple.JSONArray();
			if(top() instanceof org.json.simple.JSONArray)
				((org.json.simple.JSONArray) top()).add(value);
			else if(top() instanceof org.json.simple.JSONObject)
				((org.json.simple.JSONObject) top()).put(state.fromTop(1).getPropertyName(), value);
			thePath.add(value);
		}

		public void endArray(ParseState state)
		{
			theState = state;
			pop();
		}

		public void valueString(ParseState state, String value)
		{
			theState = state;
			if(top() instanceof org.json.simple.JSONArray)
				((org.json.simple.JSONArray) top()).add(value);
			else if(top() instanceof org.json.simple.JSONObject)
				((org.json.simple.JSONObject) top()).put(state.top().getPropertyName(), value);
			else
				theValue = value;
		}

		public void valueNumber(ParseState state, Number value)
		{
			theState = state;
			if(top() instanceof org.json.simple.JSONArray)
				((org.json.simple.JSONArray) top()).add(value);
			else if(top() instanceof org.json.simple.JSONObject)
				((org.json.simple.JSONObject) top()).put(state.top().getPropertyName(), value);
			else
				theValue = value;
		}

		public void valueBoolean(ParseState state, boolean value)
		{
			Boolean bValue = value ? Boolean.TRUE : Boolean.FALSE;
			theState = state;
			if(top() instanceof org.json.simple.JSONArray)
				((org.json.simple.JSONArray) top()).add(bValue);
			else if(top() instanceof org.json.simple.JSONObject)
				((org.json.simple.JSONObject) top()).put(state.top().getPropertyName(), bValue);
			else
				theValue = new Boolean(value);
		}

		public void valueNull(ParseState state)
		{
			theState = state;
			if(top() instanceof org.json.simple.JSONArray)
				((org.json.simple.JSONArray) top()).add(null);
			else if(top() instanceof org.json.simple.JSONObject)
				((org.json.simple.JSONObject) top()).put(state.top().getPropertyName(), null);
			else
				theValue = null;
		}

		public void whiteSpace(ParseState state, String ws)
		{
			theState = state;
		}

		public void comment(ParseState state, String fullComment, String content)
		{
			theState = state;
		}

		public Object finalValue()
		{
			return theValue;
		}

		public void error(ParseState state, String error)
		{
			theState = state;
		}

		/**
		 * Replaces the most recently parsed value with another. This is useful for modifying the
		 * content of a JSON structure as it is parsed.
		 * 
		 * @param lastValue The value to replace the most recently parsed value with.
		 */
		protected void setLastValue(Object lastValue)
		{
			theValue = lastValue;
			Object top = top();
			if(top instanceof org.json.simple.JSONArray)
			{
				org.json.simple.JSONArray jsonA = (org.json.simple.JSONArray) top;
				jsonA.set(jsonA.size() - 1, lastValue);
			}
		}

		/**
		 * @return The depth of the item that is currently being parsed
		 */
		public int getDepth()
		{
			return thePath.size();
		}

		/**
		 * @return The item that is currently being parsed
		 */
		public Object top()
		{
			if(thePath.size() == 0)
				return null;
			return thePath.get(thePath.size() - 1);
		}

		/**
		 * @param depth The depth of the item to get
		 * @return The item that is being parsed at the given depth, or null if depth>
		 *         {@link #getDepth()}
		 */
		public Object fromTop(int depth)
		{
			if(thePath.size() <= depth)
				return null;
			return thePath.get(thePath.size() - depth - 1);
		}

		private void pop()
		{
			theValue = thePath.remove(thePath.size() - 1);
		}
	}

	/** Represents a type of JSON item that can be parsed */
	public static enum ParseToken
	{
		/** Represents a JSON object */
		OBJECT,
		/** Represents a JSON array */
		ARRAY,
		/** Represents a property within a JSON object */
		PROPERTY;
	}

	/**
	 * Represents an object whose parsing has not been completed
	 */
	public static class ParseNode
	{
		/** The type of object that this node represents */
		public final ParseToken token;

		private String thePropertyName;

		private boolean hasContent;

		ParseNode(ParseToken _token)
		{
			token = _token;
		}

		void setPropertyName(String propName)
		{
			thePropertyName = propName;
		}

		void setHasContent()
		{
			hasContent = true;
		}

		/**
		 * @return The name of the property that this represents (if its token is
		 *         {@link ParseToken#PROPERTY})
		 */
		public String getPropertyName()
		{
			return thePropertyName;
		}

		/**
		 * @return Whether this node has content yet
		 */
		public boolean hasContent()
		{
			return hasContent;
		}

		@Override
		public String toString()
		{
			switch(token)
			{
			case ARRAY:
				return "array";
			case OBJECT:
				return "object";
			case PROPERTY:
				return "property(" + thePropertyName + ")";
			}
			return "Unrecognized";
		}
	}

	/** A state object which can be queried for the path to the currently parsed object */
	public static class ParseState
	{
		private final Reader theReader;

		private final ParseHandler [] theHandlers;

		private final java.util.ArrayList<ParseNode> thePath;

		private int theLastChar;

		private int theCurrentChar;

		private int theIndex;

		private int theLineNumber;

		private int theCharNumber;

		private boolean isSeparated;

		ParseState(Reader reader, ParseHandler... helper)
		{
			theReader = reader;
			theHandlers = helper;
			thePath = new java.util.ArrayList<ParseNode>();
			theLastChar = -1;
			theCurrentChar = -1;
			theLineNumber = 1;
		}

		int nextChar() throws IOException
		{
			do
			{
				boolean lastLine = theCurrentChar == '\n' || theCurrentChar == '\r';
				theIndex++;
				theCharNumber++;
				if(theLastChar >= 0)
					theCurrentChar = theLastChar;
				else
					theCurrentChar = theReader.read();
				theLastChar = -1;
				if(theCurrentChar == '\n' || theCurrentChar == '\r')
				{
					theCharNumber = 0;
					if(!lastLine)
						theLineNumber++;
				}
				/* Ignoring newlines helps when parsing output that is wrapped regardless of
				 * content, e.g. when copying from DOS command prompt output */
			} while(theCurrentChar >= 0 && (theCurrentChar == '\n' || theCurrentChar == '\r'));
			return theCurrentChar;
		}

		int currentChar()
		{
			return theCurrentChar;
		}

		/**
		 * "Backs up" the stream so that the most recent character read is the next character
		 * returned from {@link #nextChar()}. This only works once in between calls to
		 * {@link #nextChar()}
		 */
		void backUp()
		{
			if(theLastChar >= 0)
				throw new IllegalStateException("Can't back up before the first read"
					+ " or more than once between reads!");
			theLastChar = theCurrentChar;
			theIndex--;
		}

		private void startItem() throws ParseException
		{
			ParseNode top = top();
			if(top != null)
			{
				switch(top.token)
				{
				case OBJECT:
					error("Property name missing in object");
					break;
				case ARRAY:
					if(top.hasContent() && !isSeparated)
						error("Missing comma separator in array");
					top.setHasContent();
					break;
				case PROPERTY:
					if(!isSeparated)
						error("Missing separator colon after object property "
							+ top.getPropertyName());
					top.setHasContent();
					fromTop(1).setHasContent();
					break;
				}
			}
			isSeparated = false;
		}

		void startObject() throws ParseException
		{
			startItem();
			push(ParseToken.OBJECT);
			for(ParseHandler handler : theHandlers)
				handler.startObject(this);
		}

		void startArray() throws ParseException
		{
			startItem();
			push(ParseToken.ARRAY);
			for(ParseHandler handler : theHandlers)
				handler.startArray(this);
		}

		void startProperty(String propertyName) throws ParseException
		{
			push(ParseToken.PROPERTY);
			thePath.get(thePath.size() - 1).setPropertyName(propertyName);
			for(ParseHandler handler : theHandlers)
				handler.startProperty(this, propertyName);
		}

		void setPrimitive(Object value) throws ParseException
		{
			startItem();
			for(ParseHandler handler : theHandlers)
			{
				if(value == null)
					handler.valueNull(this);
				else if(value instanceof Boolean)
					handler.valueBoolean(this, ((Boolean) value).booleanValue());
				else if(value instanceof Number)
					handler.valueNumber(this, (Number) value);
				else if(value instanceof String)
					handler.valueString(this, (String) value);
				else
					error("Unrecognized primitive value: " + value);
			}
		}

		private void push(ParseToken token) throws ParseException
		{
			if(top() != null)
			{
				switch(top().token)
				{
				case OBJECT:
					if(token != ParseToken.PROPERTY)
						error("Property name required in object");
					break;
				case ARRAY:
					break;
				case PROPERTY:
					break;
				}
			}
			isSeparated = false;
			thePath.add(new ParseNode(token));
		}

		void separate(char ch) throws ParseException
		{
			if(ch == ',')
			{
				ParseNode top = top();
				if(top == null)
					error("Unexpected separator comma without object or array");
				switch(top.token)
				{
				case OBJECT:
					if(!top.hasContent())
						error("Unexpected separator comma before first property in object");
					break;
				case ARRAY:
					if(!top.hasContent())
						error("Unexpected separator comma before first element in array");
					break;
				case PROPERTY:
					if(top.hasContent())
						pop(ParseToken.PROPERTY);
					else
						error("Unexpected separator comma after object property");
					break;
				}
			}
			else if(ch == ':')
			{
				ParseNode top = top();
				if(top == null)
					error("Unexpected separator colon without object or array");
				switch(top.token)
				{
				case OBJECT:
					error("Unexpected separator colon before property declaration in object");
					break;
				case ARRAY:
					error("Unexpected separator colon in array");
					break;
				case PROPERTY:
					if(isSeparated)
						error("Dual separator colons in object property");
					break;
				}
			}
			else
				error("'" + ch + "' is not a separator");
			isSeparated = true;
			for(ParseHandler handler : theHandlers)
				handler.separator(this);
		}

		void whiteSpace(String ws)
		{
			for(ParseHandler handler : theHandlers)
				handler.whiteSpace(this, ws);
		}

		void comment(String comment, String content)
		{
			for(ParseHandler handler : theHandlers)
				handler.comment(this, comment, content);
		}

		ParseNode pop(ParseToken token) throws ParseException
		{
			ParseNode ret = top();
			switch(token)
			{
			case OBJECT:
				if(ret == null)
					error("Unexpected '}' before input");
				switch(ret.token)
				{
				case ARRAY:
					error("Expected ']' for array end but found '}'");
					break;
				case PROPERTY:
					if(!ret.hasContent())
						error("Property " + ret.getPropertyName() + " missing content");
					else if(isSeparated())
						error("Expected next property after " + ret.getPropertyName());
					pop(ParseToken.PROPERTY);
					//$FALL-THROUGH$
				case OBJECT:
					thePath.remove(thePath.size() - 1);
					for(ParseHandler handler : theHandlers)
						handler.endObject(this);
					break;
				}
				break;
			case ARRAY:
				if(ret == null)
					error("Unexpected ']' before input");
				switch(ret.token)
				{
				case OBJECT:
				case PROPERTY:
					error("Expected '}' for object end but fount ']'");
					break;
				case ARRAY:
					thePath.remove(thePath.size() - 1);
					for(ParseHandler handler : theHandlers)
						handler.endArray(this);
					break;
				}
				break;
			case PROPERTY:
				if(ret == null)
					error("Unexpected property end");
				switch(ret.token)
				{
				case OBJECT:
				case ARRAY:
					error("Unexpected property end");
					break;
				case PROPERTY:
					thePath.remove(thePath.size() - 1);
					for(ParseHandler handler : theHandlers)
						handler.endProperty(this, ret.getPropertyName());
					break;
				}
			}
			return ret;
		}

		/**
		 * @return The current depth of the parsing (e.g. an array within a property within an
		 *         object=depth of 3)
		 */
		public int getDepth()
		{
			return thePath.size();
		}

		/**
		 * @return The item that is currently being parsed
		 */
		public ParseNode top()
		{
			if(thePath.size() == 0)
				return null;
			return thePath.get(thePath.size() - 1);
		}

		/**
		 * @param depth The depth of the item to get
		 * @return The item that is being parsed at the given depth, or null if depth>
		 *         {@link #getDepth()}
		 */
		public ParseNode fromTop(int depth)
		{
			if(thePath.size() <= depth)
				return null;
			return thePath.get(thePath.size() - depth - 1);
		}

		/** @return The number of characters that have been read from the stream so far */
		public int getIndex()
		{
			return theIndex;
		}

		/**
		 * @return The number of new lines that have been read so far plus one
		 */
		public int getLineNumber()
		{
			return theLineNumber;
		}

		/**
		 * @return The number of characters that have been read since the last new line
		 */
		public int getCharNumber()
		{
			return theCharNumber;
		}

		/**
		 * @return Whether the current parsing node has been separated, e.g. whether a comma has
		 *         occurred after an array element or an object property (if this token is
		 *         {@link ParseToken#OBJECT}) or if a colon has occurred after a property
		 *         declaration (if this token is {@link ParseToken#PROPERTY})
		 */
		public boolean isSeparated()
		{
			return isSeparated;
		}

		/**
		 * <p>
		 * Causes the parser to act as if it just encountered a value. This is useful when parsing
		 * an outer JSON shell in which individual elements are parsed by a different parser. For
		 * instance, if an event has a data property whose value should be parsed externally, this
		 * parser can be used for the event, then after the data property is encountered (and after
		 * the separator colon), the external parser can parse the value of data and this method can
		 * be called to satisfy the parser so the event can be further parsed. This method does not
		 * cause any callbacks to be invoked in the handler.
		 * </p>
		 * <p>
		 * This method will throw a ParseException unless it is invoked in one of the following
		 * situations:
		 * <ul>
		 * <li>Before any content has been parsed. Calling this in this situation will have no
		 * effect at all.</li>
		 * <li>After the separator colon after a property declaration in a JSON object</li>
		 * <li>After the beginning of an array</li>
		 * <li>After a separator comma within an array</li>
		 * </ul>
		 * </p>
		 * <p>
		 * If this method is called superfluously even though the stream is read exclusively from
		 * this parser, the parser will throw a ParseException after the stream's content is read
		 * since it will believe it already encountered a value and duplicate values without a
		 * separator are illegal in JSON.
		 * </p>
		 * 
		 * @throws ParseException If the current state is not appropriate for a value
		 */
		public void spoofValue() throws ParseException
		{
			startItem();
		}

		void error(String error) throws ParseException
		{
			for(ParseHandler handler : theHandlers)
				handler.error(this, error);
			throw new ParseException(error, this);
		}
	}

	/**
	 * An exception that occurs because of invalid JSON content
	 */
	public static class ParseException extends Exception
	{
		private final ParseState theState;

		/**
		 * Creates a ParseException
		 * 
		 * @param message The message detailing what was wrong with the JSON content
		 * @param state The state that the parsing was in when the illegal content was encountered
		 */
		public ParseException(String message, ParseState state)
		{
			super(message);
			theState = state;
		}

		/**
		 * @return The state that the parsing was in when the illegal content was encountered
		 */
		public ParseState getParseState()
		{
			return theState;
		}
	}

	private boolean allowComments;

	private boolean useFormalJson;

	/**
	 * Creates a parser
	 */
	public SAJParser()
	{
		allowComments = true;
	}

	/**
	 * @return Whether or not this parser allows comments (block- and line-style)
	 */
	public boolean allowsComments()
	{
		return allowComments;
	}

	/**
	 * @param allowed Whether this parser should allow block- and line-style comments
	 */
	public void setAllowsComments(boolean allowed)
	{
		allowComments = allowed;
	}

	/**
	 * @return Whether this parser will parse its content strictly
	 * @see #setFormal(boolean)
	 */
	public boolean isFormal()
	{
		return useFormalJson;
	}

	/**
	 * Sets whether this parser parses its content strictly or loosely. Some examples of content
	 * that would violate strict parsing but would be acceptable to a loose parser are:
	 * <ul>
	 * <li>Property names that are not enclosed in quotation marks</li>
	 * <li>Strings enclosed in tick (') marks instead of quotations</li>
	 * <li>A "+" sign before a positive number</li>
	 * <li>"Inf", "Infinity", "-Inf", "-Infinity", and "NaN" as numerical identifiers</li>
	 * <li>Octal and hexadecimal integers (prefixed by 0 and 0x, respectively)</li>
	 * <li>"undefined" as an identifier (interpreted identically to null)</li>
	 * </ul>
	 * 
	 * @param formal Whether this parser should parse its content strictly
	 */
	public void setFormal(boolean formal)
	{
		useFormalJson = formal;
	}

	/**
	 * Parses a single JSON item from a stream
	 * 
	 * @param reader The stream to parse data from
	 * @param handlers The handlers to be notified of JSON content
	 * @return The value returned from the handler after parsing has finished
	 * @throws IOException If an error occurs reading from the stream
	 * @throws ParseException If an error occurs parsing the JSON content
	 */
	public Object parse(Reader reader, ParseHandler... handlers) throws IOException, ParseException
	{
		StringBuilder sb = new StringBuilder();
		ParseState state = new ParseState(reader, handlers);
		int ch = state.nextChar();
		boolean hadContent = false;
		do
		{
			if(ch < 0)
				state.error("Unexpected end of content");
			else if(isWhiteSpace(ch))
				state.whiteSpace(parseWhiteSpace(sb, state));
			else if(ch == '{')
			{
				state.startObject();
				hadContent = true;
			}
			else if(ch == '[')
			{
				state.startArray();
				hadContent = true;
			}
			else if(ch == ',' || ch == ':')
				state.separate((char) ch);
			else if(ch == '}')
				state.pop(ParseToken.OBJECT);
			else if(ch == ']')
				state.pop(ParseToken.ARRAY);
			else if(ch == '/' && allowComments)
				parseComment(sb, state);
			else
			{
				if(state.top() == null)
				{
					state.setPrimitive(parsePrimitive(sb, state));
					hadContent = true;
				}
				else
				{
					switch(state.top().token)
					{
					case OBJECT:
						state.startProperty(parsePropertyName(sb, state));
						break;
					case ARRAY:
					case PROPERTY:
						state.setPrimitive(parsePrimitive(sb, state));
						break;
					}
				}
			}
			if(state.getDepth() > 0 || !hadContent)
				ch = state.nextChar();
		} while(state.getDepth() > 0 || !hadContent);
		if(handlers.length > 0)
			return handlers[0].finalValue();
		else
			return null;
	}

	/**
	 * @param ch The character to test
	 * @return Whether the character qualifies as white space
	 */
	public static boolean isWhiteSpace(int ch)
	{
		return ch <= ' ';
	}

	/**
	 * @param ch The character to test
	 * @return Whether the character is a syntax token in JSON
	 */
	public static boolean isSyntax(int ch)
	{
		return ch == '{' || ch == '}' || ch == '[' || ch == ']' || ch == ',' || ch == ':';
	}

	String parseWhiteSpace(StringBuilder sb, ParseState state) throws IOException
	{
		int ch = state.currentChar();
		do
		{
			sb.append((char) ch);
			ch = state.nextChar();
		} while(isWhiteSpace(ch));
		state.backUp();
		String ret = sb.toString();
		sb.setLength(0);
		return ret;
	}

	void parseComment(StringBuilder sb, ParseState state) throws IOException, ParseException
	{
		int ch = state.currentChar();
		if(ch != '/')
			state.error("Invalid comment");
		ch = state.nextChar();
		sb.append('/');
		if(ch == '/')
		{ // Line comment
			sb.append('/');
			ch = state.nextChar();
			while(ch >= 0 && ch != '\n' && ch != '\r')
			{
				sb.append((char) ch);
				ch = state.nextChar();
			}
			state.backUp();
			String comment = sb.toString();
			sb.delete(0, 2);
			state.comment(comment, sb.toString().trim());
			sb.setLength(0);
		}
		else if(ch == '*')
		{ // Block comment
			sb.append('*');
			boolean lastStar = false;
			ch = state.nextChar();
			while(ch >= 0 && (!lastStar || ch != '/'))
			{
				if(ch == '*')
					lastStar = true;
				else
					lastStar = false;
				sb.append((char) ch);
				ch = state.nextChar();
			}
			if(ch < 0)
				state.backUp();
			else
				sb.append((char) ch);
			String comment = sb.toString();
			sb.delete(0, 2);
			sb.delete(sb.length() - 2, sb.length());
			/* Now we "clean up" the comment to deliver just the content. This involves removing
			 * initial and terminal white space on each line, and the initial asterisks--
			 * specifically the first continuous set of asterisks that occur. */
			boolean newLine = true;
			boolean lineHadStar = false;
			lastStar = false;
			for(int c = 0; c < sb.length(); c++)
			{
				ch = sb.charAt(c);
				if(newLine)
				{
					if(isWhiteSpace(ch))
					{
						lastStar = false;
						sb.deleteCharAt(c);
						c--;
					}
					else if(ch == '*' && (lastStar || !lineHadStar))
					{
						sb.deleteCharAt(c);
						c--;
						lineHadStar = true;
						lastStar = true;
					}
					else
					{
						lastStar = false;
						lineHadStar = false;
						newLine = false;
					}
				}
				else if(ch == '\n')
				{
					newLine = true;
					while(c > 0 && isWhiteSpace(sb.charAt(c - 1)))
					{
						sb.deleteCharAt(c - 1);
						c--;
					}
					if(c == 0)
					{
						sb.deleteCharAt(c - 1);
						c--;
					}
				}
			}
			for(int c = sb.length() - 1; c >= 0 && isWhiteSpace(sb.charAt(c)); c--)
				sb.deleteCharAt(c);
			state.comment(comment, sb.toString());
		}
		else
			state.error("Invalid comment");
		sb.setLength(0);
	}

	Object parsePrimitive(StringBuilder sb, ParseState state) throws IOException, ParseException
	{
		int ch = state.currentChar();
		if(ch == '\'' || ch == '"')
			return parseString(sb, state);
		else if(ch == '-' || ch == '+' || ch == 'I' || ch == 'N' || (ch >= '0' && ch <= '9'))
			return parseNumber(sb, state);
		else if(ch == 't' || ch == 'f')
			return parseBoolean(state);
		else if(ch == 'n' || ch == 'u')
			return parseNull(state);
		else
		{
			state.error("Unrecognized start of value: " + (char) ch);
			return null;
		}
	}

	String parseString(StringBuilder sb, ParseState state) throws IOException, ParseException
	{
		int startChar = state.currentChar();
		if(useFormalJson && startChar != '"')
			state.error("Strings must be enclosed in quotations in formal JSON");
		int ch = state.nextChar();
		boolean escaped = false;
		while(true)
		{
			if(escaped)
			{
				switch(ch)
				{
				case '"':
					sb.append('"');
					break;
				case '\'':
					sb.append('\'');
					break;
				case '\\':
					sb.append('\\');
					break;
				case '/':
					// Solidus
					sb.append('\u2044');
					break;
				case 'n':
					sb.append('\n');
					break;
				case 'r':
					sb.append('\r');
					break;
				case 't':
					sb.append('\t');
					break;
				case 'b':
					sb.append('\b');
					break;
				case 'f':
					sb.append('\f');
					break;
				case 'u':
					int unicode = 0;
					int ch2 = 0;
					int i;
					for(i = 0; i < 4; i++)
					{
						ch2 = state.nextChar();
						if(ch2 >= '0' && ch2 <= '9')
							unicode = unicode << 4 + (ch2 - '0');
						else if(ch2 >= 'a' && ch2 <= 'f')
							unicode = unicode << 4 + (ch2 - 'a' + 10);
						else if(ch2 >= 'A' && ch2 <= 'F')
							unicode = unicode << 4 + (ch2 - 'A' + 10);
						else
							break;
					}
					sb.append(new String(Character.toChars(unicode)));
					if(i < 4)
					{
						ch = ch2;
						continue;
					}
					break;
				default:
					state.error(((char) ch) + " is not escapable");
				}
				escaped = false;
			}
			else if(ch == '\\')
				escaped = true;
			else if(ch == startChar)
				break;
			else
				sb.append((char) ch);
			ch = state.nextChar();
		}
		String ret = sb.toString();
		sb.setLength(0);
		return ret;
	}

	Number parseNumber(StringBuilder sb, ParseState state) throws IOException, ParseException
	{
		int ch = state.currentChar();
		boolean neg = ch == '-';
		if(neg)
			ch = state.nextChar();
		else if(ch == '+')
		{
			if(useFormalJson)
				state.error("Plus sign not allowed as number prefix in formal JSON");
			ch = state.nextChar();
		}
		while(isWhiteSpace(ch))
			ch = state.nextChar();
		if(ch == 'I')
		{
			ch = state.nextChar();
			if(ch != 'n')
				state.error("Invalid infinite");
			ch = state.nextChar();
			if(ch != 'f')
				state.error("Invalid infinite");
			ch = state.nextChar();
			if(ch == 'i')
			{
				ch = state.nextChar();
				if(ch != 'n')
					state.error("Invalid infinite");
				ch = state.nextChar();
				if(ch != 'i')
					state.error("Invalid infinite");
				ch = state.nextChar();
				if(ch != 't')
					state.error("Invalid infinite");
				ch = state.nextChar();
				if(ch != 'y')
					state.error("Invalid infinite");
			}
			else
				state.backUp();
			if(useFormalJson)
				state.error("Infinite values not allowed in formal JSON");
			return new Float(neg ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY);
		}
		if(ch == 'N')
		{
			ch = state.nextChar();
			if(ch != 'a')
				state.error("Invalid NaN");
			ch = state.nextChar();
			if(ch != 'N')
				state.error("Invalid NaN");
			if(useFormalJson)
				state.error("NaN not allowed in formal JSON");
			return new Float(Float.NaN);
		}
		int radix = ch == '0' ? 8 : 10;
		if(radix == 8)
		{
			ch = state.nextChar();
			if(ch == 'x')
			{
				radix = 16;
				ch = state.nextChar();
			}
			else if(ch < '0' || ch > '9')
				radix = 10;
		}
		if(useFormalJson && radix != 10)
			state.error("Numbers in formal JSON must be in base 10");
		// 0=whole, 1=part, 2=exp
		int numState = 0;
		String whole = null;
		String part = null;
		String expNum = null;
		boolean expNeg = false;
		char type = 0;
		while(true)
		{
			if(isWhiteSpace(ch))
			{}
			else if(ch >= '0' && ch <= '9')
			{
				if(ch >= '8' && radix == 8)
					state.error("8 or 9 digit used in octal number");
				sb.append((char) ch);
				ch = state.nextChar();
			}
			else if(ch >= 'a' && ch <= 'f')
			{
				if(radix < 16)
				{
					if(ch == 'e')
					{
						if(part != null)
							state.error("Multiple exponentials in number");
						switch(numState)
						{
						case 0:
							whole = sb.toString();
							break;
						case 1:
							part = sb.toString();
							break;
						}
						numState = 2;
						sb.setLength(0);
						ch = state.nextChar();
					}
					else if(ch == 'f')
					{
						if(radix != 10)
							state.error("No octal floating point numbers");
						if(useFormalJson)
							state.error("Formal JSON does not allow type specification on numbers");
						switch(numState)
						{
						case 0:
							whole = sb.toString();
							break;
						case 1:
							part = sb.toString();
							break;
						default:
							expNum = sb.toString();
						}
						sb.setLength(0);
						type = 'f';
						ch = state.nextChar();
						break;
					}
					else
						state.error("Hexadecimal digits used in " + radix + "-based number");
				}
				else
				{
					if(numState > 0)
						state.error("No hexadecimal floating-point numbers");
					sb.append((char) ch);
					ch = state.nextChar();
				}
			}
			else if(ch >= 'A' && ch <= 'F')
			{
				if(radix < 16)
				{
					if(ch == 'E')
					{
						if(part != null)
							state.error("Multiple exponentials in number");
						switch(numState)
						{
						case 0:
							whole = sb.toString();
							break;
						case 1:
							part = sb.toString();
							break;
						}
						numState = 2;
						sb.setLength(0);
						ch = state.nextChar();
					}
					else if(ch == 'F')
					{
						if(radix != 10)
							state.error("No octal floating point numbers");
						switch(numState)
						{
						case 0:
							whole = sb.toString();
							break;
						case 1:
							part = sb.toString();
							break;
						default:
							expNum = sb.toString();
						}
						sb.setLength(0);
						type = 'f';
						ch = state.nextChar();
						break;
					}
					else
						state.error("Hexadecimal digits used in " + radix + "-based number");
				}
				else
				{
					if(numState > 0)
						state.error("No hexadecimal floating-point numbers");
					sb.append((char) ch);
					ch = state.nextChar();
				}
			}
			else if(ch == '-' || ch == '+')
			{
				if(numState != 2 || sb.length() > 0)
					state.error("Subtraction in JSON number not supported");
				expNeg = ch == '-';
				ch = state.nextChar();
			}
			else if(ch == '.')
			{
				if(radix != 10)
					state.error("No octal or hexadecimal floating point numbers");
				if(numState != 0)
					state.error("Decimal in incorrect place in number");
				numState = 1;
				whole = sb.toString();
				sb.setLength(0);
				ch = state.nextChar();
			}
			else if(ch == 'l' || ch == 'L')
			{
				switch(numState)
				{
				case 0:
					whole = sb.toString();
					break;
				default:
					state.error("No decimals or exponentials in long numbers");
				}
				sb.setLength(0);
				type = 'l';
				ch = state.nextChar();
				break;
			}
			else
				break;
		}
		state.backUp();
		if(sb.length() > 0)
		{
			switch(numState)
			{
			case 0:
				whole = sb.toString();
				break;
			case 1:
				part = sb.toString();
				break;
			default:
				expNum = sb.toString();
			}
		}
		sb.setLength(0);
		if(part != null || expNum != null || numState > 0 || type == 'f')
		{
			double ret = 0;
			String max = Long.toString(Long.MAX_VALUE, radix);
			if(testMax(whole, max))
				state.error("Number size greater than maximum");
			if(testMax(part, max))
				state.error("Number size greater than maximum");
			if(testMax(expNum, max))
				state.error("Number size greater than maximum");
			if(whole != null && whole.length() > 0)
				ret = Long.parseLong(whole, radix);
			if(part != null && part.length() > 0)
				ret += Long.parseLong(part) * powNeg10(part.length());
			if(expNum != null && expNum.length() > 0)
			{
				if(!expNeg)
					ret *= pow10((int) Long.parseLong(expNum));
				else
					ret /= pow10((int) Long.parseLong(expNum));
			}
			if(type == 'f')
				return new Float(neg ? -(float) ret : (float) ret);
			else
				return new Double(neg ? -ret : ret);
		}
		else
		{
			if(!testMax(whole, Integer.toString(Integer.MAX_VALUE, radix)))
			{
				int ret;
				if(whole != null && whole.length() > 0)
					ret = Integer.parseInt(whole, radix);
				else
					ret = 0;
				return new Integer(neg ? -ret : ret);
			}
			else
			{
				if(testMax(whole, Long.toString(Long.MAX_VALUE, radix)))
					state.error("Number size greater than maximum");
				long ret;
				if(whole != null && whole.length() > 0)
					ret = Long.parseLong(whole, radix);
				else
					ret = 0;
				return new Long(neg ? -ret : ret);
			}
		}
	}

	private boolean testMax(String num, String max) throws ParseException
	{
		if(num == null || num.length() < max.length())
			return false;
		if(num.length() > max.length())
			return true;
		if(num.compareToIgnoreCase(max) > 0)
			return true;
		return false;
	}

	private double powNeg10(int pow)
	{
		double ret = 1;
		for(; pow > 0; pow--)
			ret /= 10;
		return ret;
	}

	private double pow10(int pow)
	{
		double ret = 1;
		for(; pow > 0; pow--)
			ret *= 10;
		return ret;
	}

	Boolean parseBoolean(ParseState state) throws IOException, ParseException
	{
		Boolean ret;
		int ch = state.currentChar();
		if(ch == 't')
		{
			ch = state.nextChar();
			if(ch != 'r')
				state.error("Invalid true boolean");
			ch = state.nextChar();
			if(ch != 'u')
				state.error("Invalid true boolean");
			ch = state.nextChar();
			if(ch != 'e')
				state.error("Invalid true boolean");
			ret = Boolean.TRUE;
		}
		else if(ch == 'f')
		{
			ch = state.nextChar();
			if(ch != 'a')
				state.error("Invalid false boolean");
			ch = state.nextChar();
			if(ch != 'l')
				state.error("Invalid false boolean");
			ch = state.nextChar();
			if(ch != 's')
				state.error("Invalid false boolean");
			ch = state.nextChar();
			if(ch != 'e')
				state.error("Invalid false boolean");
			ret = Boolean.FALSE;
		}
		else
		{
			state.error("Invalid boolean");
			return null;
		}
		return ret;
	}

	Object parseNull(ParseState state) throws IOException, ParseException
	{
		int ch = state.currentChar();
		if(ch == 'n')
		{
			ch = state.nextChar();
			if(ch != 'u')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'l')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'l')
				state.error("Invalid null");
		}
		else if(ch == 'u')
		{
			ch = state.nextChar();
			if(ch != 'n')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'd')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'e')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'f')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'i')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'n')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'e')
				state.error("Invalid null");
			ch = state.nextChar();
			if(ch != 'd')
				state.error("Invalid null");
			if(useFormalJson)
				state.error("undefined is not a valid identifier in formal JSON");
		}
		else
			state.error("Invalid null");
		return null;
	}

	String parsePropertyName(StringBuilder sb, ParseState state) throws IOException, ParseException
	{
		int ch = state.currentChar();
		int isQuoted = 0;
		if(ch == '\'')
			isQuoted = 1;
		if(ch == '"')
			isQuoted = 2;
		if(isQuoted > 0)
			return parseString(sb, state);
		else if(useFormalJson)
			state.error("Property names must be quoted in formal JSON");
		boolean escaped = false;
		while(true)
		{
			if(escaped)
			{
				switch(ch)
				{
				case '"':
					sb.append('"');
					break;
				case '\'':
					sb.append('\'');
					break;
				case '\\':
					sb.append('\\');
					break;
				case '/':
					// Solidus
					sb.append('\u2044');
					break;
				case 'n':
					sb.append('\n');
					break;
				case 'r':
					sb.append('\r');
					break;
				case 't':
					sb.append('\t');
					break;
				case 'b':
					sb.append('\b');
					break;
				case 'f':
					sb.append('\f');
					break;
				case 'u':
					int unicode = 0;
					int ch2 = 0;
					int i;
					for(i = 0; i < 4; i++)
					{
						ch2 = state.nextChar();
						if(ch2 >= '0' && ch2 <= '9')
							unicode = unicode << 4 + (ch2 - '0');
						else if(ch2 >= 'a' && ch2 <= 'f')
							unicode = unicode << 4 + (ch2 - 'a' + 10);
						else if(ch2 >= 'A' && ch2 <= 'F')
							unicode = unicode << 4 + (ch2 - 'A' + 10);
						else
							break;
					}
					sb.append(new String(Character.toChars(unicode)));
					if(i < 4)
					{
						ch = ch2;
						continue;
					}
					break;
				default:
					state.error(((char) ch) + " is not escapable");
				}
			}
			else if(ch == '\\')
				escaped = true;
			else if(isSyntax(ch) || isWhiteSpace(ch))
				break; // Finished--not valid for unquoted property
			else
				sb.append((char) ch);
			ch = state.nextChar();
		}
		state.backUp();
		String ret = sb.toString();
		sb.setLength(0);
		return ret;
	}

	/**
	 * Main tester method. This method asks the user repeatedly for JSON input to parse and prints
	 * it to the screen formatted.
	 * 
	 * @param args Command-line arguments, ignored.
	 */
	public static void main(String [] args)
	{
		DefaultHandler handler = new DefaultHandler();
		SAJParser parser = new SAJParser();
		while(true)
		{
			System.out.println("\nEnter a JSON value to parse");
			try
			{
				System.out.println(prisms.util.JsonUtils.format(parser.parse(
					new java.io.InputStreamReader(System.in), handler)));
			} catch(IOException e)
			{
				handler.reset();
				System.err.println("Could not read from system in: " + e.getMessage());
			} catch(ParseException e)
			{
				handler.reset();
				System.err.println("Could not parse JSON: " + e.getMessage());
			}
		}
	}
}
