package prisms.lang.types;

/** Represents a keyword */
public class ParsedKeyword extends prisms.lang.ParsedItem
{
	private String theName;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theName = getStored("name").text;
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		Class<?> type = ParsedType.getClassFromName(theName, env);
		if(type != null)
			return new prisms.lang.EvaluationResult(new prisms.lang.Type(type));
		throw new prisms.lang.EvaluationException("Syntax error on " + theName + ": delete this token", this,
			getMatch().index);
	}

	/** @return This keyword's name */
	public String getName()
	{
		return theName;
	}
}
