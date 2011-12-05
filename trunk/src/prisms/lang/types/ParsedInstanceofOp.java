package prisms.lang.types;

/** Represents a use of the instanceof operator */
public class ParsedInstanceofOp extends prisms.lang.ParsedItem
{
	private prisms.lang.ParsedItem theVariable;

	private prisms.lang.ParsedItem theType;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theVariable = parser.parseStructures(this, getStored("variable"))[0];
		theType = parser.parseStructures(this, getStored("type"))[0];
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		prisms.lang.EvaluationResult type = theType.evaluate(env, true, withValues);
		if(!type.isType())
			throw new prisms.lang.EvaluationException("Invalid type: " + theType.getMatch().text, theType,
				theType.getMatch().index);
		if(type.getType().isPrimitive())
			throw new prisms.lang.EvaluationException("Syntax error on " + theType.getMatch().text
				+ ": dimensions expected after this token", theType, theType.getMatch().index);
		if(type.getType().getBaseType() == null || type.getType().getParamTypes().length > 0)
			throw new prisms.lang.EvaluationException(
				"Cannot perform an instanceof check against a parameterized type", theType, theType.getMatch().index);
		prisms.lang.EvaluationResult varType = theVariable.evaluate(env, false, withValues);
		if(varType.isType() || varType.getPackageName() != null)
			throw new prisms.lang.EvaluationException(
				theVariable.getMatch().text + " cannot be resolved to a variable", theVariable,
				theVariable.getMatch().index);
		if(type.getType().isAssignable(varType.getType()))
			return new prisms.lang.EvaluationResult(new prisms.lang.Type(Boolean.TYPE), Boolean.TRUE);
		else if(!type.getType().getBaseType().isInterface() && !varType.getType().getBaseType().isInterface()
			&& !varType.getType().isAssignable(type.getType()))
			throw new prisms.lang.EvaluationException("Incompatible conditional operand types " + varType.getType()
				+ " and " + type.getType(), this, theVariable.getMatch().index);
		if(!withValues)
			return new prisms.lang.EvaluationResult(new prisms.lang.Type(Boolean.TYPE), (Boolean) null);
		return new prisms.lang.EvaluationResult(new prisms.lang.Type(Boolean.TYPE), Boolean.valueOf(type.getType()
			.getBaseType().isInstance(varType.getValue())));
	}

	/** @return The variable that is being checked against a type */
	public prisms.lang.ParsedItem getVariable()
	{
		return theVariable;
	}

	/** @return The type that the variable is being checked against */
	public prisms.lang.ParsedItem getType()
	{
		return theType;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		return new prisms.lang.ParsedItem [] {theVariable, theType};
	}

	@Override
	public String toString()
	{
		return theVariable + " instanceof " + theType;
	}
}
