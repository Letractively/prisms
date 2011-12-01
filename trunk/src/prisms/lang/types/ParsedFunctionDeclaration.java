package prisms.lang.types;

/** Represents a user-created function */
public class ParsedFunctionDeclaration extends prisms.lang.ParsedItem
{
	private String theName;

	private ParsedDeclaration [] theParameters;

	private prisms.lang.ParsedItem theReturnType;

	private ParsedType [] theExceptionTypes;

	private ParsedStatementBlock theBody;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theName = getStored("name").text;
		theReturnType = parser.parseStructures(this, getStored("returnType"))[0];
		theBody = (ParsedStatementBlock) parser.parseStructures(this, getStored("body"))[0];
		theParameters = new ParsedDeclaration [0];
		theExceptionTypes = new ParsedType [0];
		for(prisms.lang.ParseMatch m : match.getParsed())
		{
			if("parameter".equals(m.config.get("storeAs")))
				theParameters = prisms.util.ArrayUtils.add(theParameters,
					(ParsedDeclaration) parser.parseStructures(this, m)[0]);
			else if("exception".equals(m.config.get("storeAs")))
				theExceptionTypes = prisms.util.ArrayUtils.add(theExceptionTypes,
					(ParsedType) parser.parseStructures(this, m)[0]);
		}
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws prisms.lang.EvaluationException
	{
		prisms.lang.EvaluationEnvironment scoped = env.scope(false);
		prisms.lang.EvaluationResult ret = theReturnType.evaluate(scoped, true, withValues);
		if(!ret.isType())
			throw new prisms.lang.EvaluationException(ret + " cannot be resolved to a type", theReturnType,
				theReturnType.getMatch().index);
		scoped.setReturnType(ret.getType());
		for(ParsedDeclaration dec : theParameters)
			dec.evaluate(scoped, false, false);
		prisms.lang.Type[] exTypes = new prisms.lang.Type [theExceptionTypes.length];
		for(int i = 0; i < exTypes.length; i++)
			exTypes[i] = theExceptionTypes[i].evaluate(scoped, true, withValues).getType();
		scoped.setHandledExceptionTypes(exTypes);
		theBody.evaluate(scoped, false, false);
		env.declareFunction(this);
		return null;
	}

	/**
	 * Executes this function against a set of user-supplied parameters
	 * 
	 * @param env The evaluation environment to execute the function within
	 * @param params The parameters to evaluate the function against
	 * @param withValues Whether to validate or evaluate this function
	 * @return The return value of the function
	 * @throws prisms.lang.EvaluationException If an error occurs evaluating the function or if the evaluation throws an
	 *         exception
	 */
	public prisms.lang.EvaluationResult execute(prisms.lang.EvaluationEnvironment env,
		prisms.lang.EvaluationResult[] params, boolean withValues) throws prisms.lang.EvaluationException
	{
		prisms.lang.EvaluationEnvironment scoped = env.scope(false);
		if(theParameters.length != params.length)
			throw new IllegalStateException("Illegal parameters");
		for(int i = 0; i < theParameters.length; i++)
		{
			if(!theParameters[i].getType().evaluate(scoped, true, withValues).getType()
				.isAssignable(params[i].getType()))
				throw new IllegalStateException("Illegal parameters");
			theParameters[i].evaluate(scoped, false, withValues);
			if(withValues)
				scoped.setVariable(theParameters[i].getName(), params[i].getValue(), theParameters[i],
					theParameters[i].getMatch().index);
		}
		for(ParsedType et : theExceptionTypes)
			if(!env.canHandle(et.evaluate(scoped, true, withValues).getType()))
				throw new prisms.lang.EvaluationException("Unhandled exception type " + et, et, et.getMatch().index);
		prisms.lang.EvaluationResult retRes = theReturnType.evaluate(scoped, true, withValues);
		if(!retRes.isType())
			throw new prisms.lang.EvaluationException(retRes + " cannot be resolved to a type", theReturnType,
				theReturnType.getMatch().index);
		scoped.setReturnType(retRes.getType());
		prisms.lang.Type[] exTypes = new prisms.lang.Type [theExceptionTypes.length];
		for(int i = 0; i < exTypes.length; i++)
			exTypes[i] = theExceptionTypes[i].evaluate(scoped, true, withValues).getType();
		scoped.setHandledExceptionTypes(exTypes);
		try
		{
			prisms.lang.EvaluationResult res = theBody.evaluate(scoped, false, withValues);
			if(withValues && res == null && !Void.TYPE.equals(retRes.getType().getBaseType()))
				throw new prisms.lang.EvaluationException("No value returned", theBody, theBody.getMatch().index
					+ theBody.getMatch().text.length());
			if(withValues && res != null && !isAssignable(retRes.getType(), res.getValue()))
				throw new prisms.lang.EvaluationException("Type mismatch: cannot convert from " + res.getType()
					+ " to " + retRes.getType(), theBody, theBody.getMatch().index);
			return new prisms.lang.EvaluationResult(retRes.getType(), res == null ? null : res.getValue());
		} catch(prisms.lang.ExecutionException e)
		{
			if(e.getCause() instanceof Error)
				throw e;
			else if(e.getCause() instanceof RuntimeException)
				throw e;
			for(prisms.lang.Type exType : exTypes)
				if(exType.isAssignable(e.getType()))
					throw e;
			throw new prisms.lang.EvaluationException("Unhandled exception type " + e.getType(), e.getCause(), theBody,
				theBody.getMatch().index);
		}
	}

	private boolean isAssignable(prisms.lang.Type t, Object value)
	{
		if(value == null)
			return !t.isPrimitive();
		if(t.isPrimitive())
		{
			Class<?> prim = prisms.lang.Type.getPrimitiveType(value.getClass());
			return prim != null && t.isAssignableFrom(prim);
		}
		else
			return t.isAssignableFrom(value.getClass());
	}

	/** @return The name of the function */
	public String getName()
	{
		return theName;
	}

	/** @return The declarations of the function's parameter arguments */
	public ParsedDeclaration [] getParameters()
	{
		return theParameters;
	}

	/**
	 * A placeholder for future functionality
	 * 
	 * @return Whether this function takes variable arguments
	 */
	public boolean isVarArgs()
	{
		return false;
	}

	/** @return The type of this function's return value */
	public prisms.lang.ParsedItem getReturnType()
	{
		return theReturnType;
	}

	/** @return The types of exceptions that this function may throw */
	public ParsedType [] getExceptionTypes()
	{
		return theExceptionTypes;
	}

	/** @return This function's body */
	public ParsedStatementBlock getBody()
	{
		return theBody;
	}

	/** @return A short representation of this function's signature */
	public String getShortSig()
	{
		StringBuilder ret = new StringBuilder();
		ret.append(theName);
		ret.append('(');
		for(int p = 0; p < theParameters.length; p++)
		{
			if(p > 0)
				ret.append(", ");
			ret.append(theParameters[p].getType());
		}
		ret.append(')');
		return ret.toString();
	}
}
