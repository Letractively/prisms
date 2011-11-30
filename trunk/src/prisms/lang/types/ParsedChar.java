/*
 * ParsedChar.java Created Nov 15, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents a character literal */
public class ParsedChar extends prisms.lang.ParsedItem
{
	private char theValue;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		String value = getStored("value").text;
		value = prisms.util.PrismsUtils.decodeUnicode(value);
		if(value.length() != 1)
			throw new prisms.lang.ParseException("Invalid character constant", getRoot().getFullCommand(),
				getStored("value").index);
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

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		return new prisms.lang.EvaluationResult(new prisms.lang.Type(Character.TYPE), Character.valueOf(theValue));
	}
}
