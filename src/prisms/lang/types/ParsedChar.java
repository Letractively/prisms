/*
 * ParsedChar.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents a character literal */
public class ParsedChar extends prisms.lang.ParseStruct
{
	private char theValue;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		String value = getStored("value").text;
		value = prisms.util.PrismsUtils.decodeUnicode(value);
		if(value.length() != 1)
			throw new prisms.lang.ParseException("Invalid character constant", getRoot()
				.getFullCommand(), getStored("value").index);
		theValue = value.charAt(0);
	}

	/** @return The value of this character */
	public char getValue()
	{
		return theValue;
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder('\'').append(theValue).append('\'');
		prisms.util.PrismsUtils.encodeUnicode(ret);
		return ret.toString();
	}
}