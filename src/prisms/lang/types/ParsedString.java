/*
 * ParsedString.java Created Nov 11, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents a character string */
public class ParsedString extends prisms.lang.ParseStruct
{
	private String theValue;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		String val = getStored("value").text;
		theValue = unescape(val);
	}

	/** @return The parsed string that this structure represents */
	public String getValue()
	{
		return theValue;
	}

	static String unescape(String str)
	{
		StringBuilder ret = new StringBuilder(str);
		prisms.util.PrismsUtils.replaceAll(ret, "\\\"", "\"");
		prisms.util.PrismsUtils.replaceAll(ret, "\\n", "\n");
		prisms.util.PrismsUtils.replaceAll(ret, "\\t", "\t");
		return prisms.util.PrismsUtils.decodeUnicode(ret.toString());
	}

	static String escape(String str)
	{
		StringBuilder ret = new StringBuilder(str);
		prisms.util.PrismsUtils.replaceAll(ret, "\"", "\\\"");
		prisms.util.PrismsUtils.replaceAll(ret, "\n", "\\n");
		prisms.util.PrismsUtils.replaceAll(ret, "\t", "\\t");
		prisms.util.PrismsUtils.encodeUnicode(ret);
		return ret.toString();
	}

	@Override
	public String toString()
	{
		return "\"" + escape(theValue) + "\"";
	}
}
