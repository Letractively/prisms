/*
 * ParsedNumber.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import prisms.lang.EvaluationResult;
import prisms.lang.ParseException;
import prisms.lang.ParseMatch;

/** Represents a number literal */
public class ParsedNumber extends prisms.lang.ParsedItem
{
	private static String MAX_LONG = "" + Long.MAX_VALUE;

	private static String MAX_INT = "" + Integer.MAX_VALUE;

	private Number theValue;

	private boolean isScientific;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, ParseMatch match)
		throws ParseException
	{
		super.setup(parser, parent, match);
		boolean floatType = getStored("floatType") != null;
		boolean longType = getStored("longType") != null;
		if(floatType && longType)
			throw new ParseException("'f' and 'L' may not be specified in the same number", getRoot().getFullCommand(),
				getMatch().index);
		boolean neg = getStored("neg") != null;
		if(getStored("hex") != null)
		{
			String value = getStored("value").text;
			value = prisms.util.PrismsUtils.replaceAll(value, "_", "");
			if(longType)
			{
				if(value.length() > 16)
					throw new ParseException("The literal 0x" + getStored("value").text
						+ " of type long is out of range", getRoot().getFullCommand(), getMatch().index);
				long val = 0;
				for(int c = 0; c < value.length(); c++)
				{
					char ch = value.charAt(c);
					if(ch >= '0' && ch <= '9')
						val = (val << 4) | (ch - '0');
					else if(ch >= 'a' && ch <= 'f')
						val = (val << 4) | (ch - 'a' + 10);
					else if(ch >= 'A' && ch <= 'F')
						val = (val << 4) | (ch - 'A' + 10);
				}
				theValue = Long.valueOf(neg ? -val : val);
			}
			else
			{
				if(value.length() > 8)
					throw new ParseException("The literal 0x" + getStored("value").text
						+ " of type int is out of range", getRoot().getFullCommand(), getMatch().index);
				int val = 0;
				for(int c = 0; c < value.length(); c++)
				{
					char ch = value.charAt(c);
					if(ch >= '0' && ch <= '9')
						val = (val << 4) | (ch - '0');
					else if(ch >= 'a' && ch <= 'f')
						val = (val << 4) | (ch - 'a' + 10);
					else if(ch >= 'A' && ch <= 'F')
						val = (val << 4) | (ch - 'A' + 10);
				}
				theValue = Integer.valueOf(neg ? -val : val);
			}
		}
		else if(getStored("oct") != null)
		{
			String value = getStored("value").text;
			if(longType)
			{
				if(value.length() > 22 || (value.length() == 22 && value.charAt(0) > 1))
					throw new ParseException("The literal 0" + getStored("value").text
						+ " of type long is out of range", getRoot().getFullCommand(), getMatch().index);
				long val = 0;
				for(int c = 0; c < value.length(); c++)
					val = (val << 3) | (value.charAt(c) - '0');
				theValue = Long.valueOf(neg ? -val : val);
			}
			else
			{
				if(value.length() > 11 || (value.length() == 11 && value.charAt(0) > 4))
					throw new ParseException(
						"The literal 0" + getStored("value").text + " of type int is out of range", getRoot()
							.getFullCommand(), getMatch().index);
				int val = 0;
				for(int c = 0; c < value.length(); c++)
				{
					if(value.charAt(c) == '_')
						continue;
					if(value.charAt(c) < '0' || value.charAt(c) >= '7')
						throw new ParseException("Octal literals may only contain digits 0 through 7", getRoot()
							.getFullCommand(), getStored("value").index + c);
					val = (val << 3) | (value.charAt(c) - '0');
				}
				theValue = Integer.valueOf(neg ? -val : val);
			}
		}
		else if(getStored("binary") != null)
		{
			String value = getStored("value").text;
			if(longType)
			{
				if(value.length() > 64)
					throw new ParseException("The literal 0b" + getStored("value").text
						+ " of type long is out of range", getRoot().getFullCommand(), getMatch().index);
				long val = 0;
				for(int c = 0; c < value.length(); c++)
				{
					if(value.charAt(c) == '_')
						continue;
					if(value.charAt(c) < '0' || value.charAt(c) >= '1')
						throw new ParseException("Binary literals may not contain digits other than 0 and 1", getRoot()
							.getFullCommand(), getStored("value").index + c);
					val = (val << 1) | (value.charAt(c) - '0');
				}
				theValue = Long.valueOf(neg ? -val : val);
			}
			else
			{
				if(value.length() > 32)
					throw new ParseException("The literal 0b" + getStored("value").text
						+ " of type int is out of range", getRoot().getFullCommand(), getMatch().index);
				int val = 0;
				for(int c = 0; c < value.length(); c++)
					val = (val << 1) | (value.charAt(c) - '0');
				theValue = Integer.valueOf(neg ? -val : val);
			}
		}
		else
		{
			ParseMatch intPart = getStored("integer");
			ParseMatch fractPart = getStored("fractional");
			ParseMatch expPart = getStored("exp");
			boolean expNeg = getStored("expNeg") != null;
			if(fractPart == null && expPart == null && !floatType)
			{
				String value = intPart.text;
				value = prisms.util.PrismsUtils.replaceAll(value, "_", "");
				if(longType)
				{
					if(value.length() > MAX_LONG.length()
						|| (value.length() == MAX_LONG.length() && value.compareTo(MAX_LONG) > 0))
						throw new ParseException("The literal " + value + " of type long is out of range", getRoot()
							.getFullCommand(), getMatch().index);
					long val = 0;
					for(int c = 0; c < value.length(); c++)
						val = val * 10 + (value.charAt(c) - '0');
					theValue = Long.valueOf(neg ? -val : val);
				}
				else
				{
					if(value.length() > MAX_INT.length()
						|| (value.length() == MAX_INT.length() && value.compareTo(MAX_INT) > 0))
						throw new ParseException("The literal " + value + " of type int is out of range", getRoot()
							.getFullCommand(), getMatch().index);
					int val = 0;
					for(int c = 0; c < value.length(); c++)
						val = val * 10 + (value.charAt(c) - '0');
					theValue = Integer.valueOf(neg ? -val : val);
				}
			}
			else
			{
				double d = 0;
				String value;
				if(intPart != null)
				{
					value = intPart.text;
					value = prisms.util.PrismsUtils.replaceAll(value, "_", "");
					for(int c = 0; c < value.length(); c++)
						d = d * 10 + value.charAt(c) - '0';
				}
				if(fractPart != null)
				{
					double frac = 0;
					value = fractPart.text;
					value = prisms.util.PrismsUtils.replaceAll(value, "_", "");
					for(int c = value.length() - 1; c >= 0; c--)
						frac = frac / 10 + value.charAt(c) - '0';
					frac /= 10;
					d += frac;
				}
				if(expPart != null)
				{
					int exp = 0;
					value = expPart.text;
					value = prisms.util.PrismsUtils.replaceAll(value, "_", "");
					for(int c = 0; c < value.length(); c++)
						exp = exp * 10 + value.charAt(c) - '0';
					for(int i = 0; i < exp; i++)
						if(expNeg)
							d /= 10;
						else
							d *= 10;
				}
				if(floatType)
					theValue = Float.valueOf(neg ? (float) -d : (float) d);
				else
					theValue = Double.valueOf(neg ? -d : d);
			}
		}
	}

	/** @return The value of this number */
	public Number getValue()
	{
		return theValue;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		return new prisms.lang.ParsedItem [0];
	}

	/** @return Whether this value was reported in scientific notation */
	public boolean isScientific()
	{
		return isScientific;
	}

	@Override
	public String toString()
	{
		return theValue.toString();
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		return new prisms.lang.EvaluationResult(new prisms.lang.Type(EvaluationResult.getPrimitiveType(theValue
			.getClass())), theValue);
	}
}
