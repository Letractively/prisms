package prisms.lang.types;

/** Represents a null identifier */
public class ParsedNull extends prisms.lang.ParsedItem
{
	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		return new prisms.lang.EvaluationResult(new prisms.lang.Type(prisms.lang.Type.NULL.getClass()), null);
	}
}
