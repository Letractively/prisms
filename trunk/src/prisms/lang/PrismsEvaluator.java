/*
 * PrismsEvaluator.java Created Nov 14, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** Evaluates expressions */
public interface PrismsEvaluator
{
	/** Contains the type and value of the results of evaluating an expression */
	public static class EvalResult
	{
		private final boolean isType;

		private final Class<?> theType;

		private final String thePackageName;

		private final Object theValue;

		/**
		 * Creates a result that represents a value
		 * 
		 * @param type The type of the result
		 * @param value The value of the result
		 */
		public EvalResult(Class<?> type, Object value)
		{
			theType = type;
			theValue = value;
			isType = false;
			thePackageName = null;
		}

		/**
		 * Creates a result that represents a type
		 * 
		 * @param type The result type
		 */
		public EvalResult(Class<?> type)
		{
			theType = type;
			isType = true;
			thePackageName = null;
			theValue = null;
		}

		/**
		 * Creates a result that represents a package
		 * 
		 * @param packageName The name of the package
		 */
		public EvalResult(String packageName)
		{
			thePackageName = packageName;
			theType = null;
			isType = false;
			theValue = null;
		}

		/** @return The type of the evaluated expression (may be null if this is a package) */
		public Class<?> getType()
		{
			return theType;
		}

		/** @return Whether this result is a type or a value */
		public boolean isType()
		{
			return isType;
		}

		/**
		 * @return The name of the package this result represents, or null if it represents a type
		 *         or value
		 */
		public String getPackageName()
		{
			return thePackageName;
		}

		/** @return The value of this result */
		public Object getValue()
		{
			return theValue;
		}

		/** @return The name of the type of this result */
		public String typeString()
		{
			if(thePackageName != null)
				return thePackageName;
			else
				return typeString(theType);
		}

		/**
		 * @param t The type to represent
		 * @return The string representation of the given type
		 */
		public static String typeString(Class<?> t)
		{
			if(t.isArray())
				return typeString(t.getComponentType()) + "[]";
			else
				return t.getName();
		}

		/** @return The name before the first '.' in this result's type name */
		public String getFirstVar()
		{
			if(thePackageName != null)
				return getFirstVar(thePackageName);
			else
				return getFirstVar(theType.getName());
		}

		private static String getFirstVar(String s)
		{
			int idx = s.indexOf('.');
			if(idx >= 0)
				return s.substring(0, idx);
			else
				return s;
		}

		/** @return Whether this result represents a primitive integer-type (e.g. long, int, byte) */
		public boolean isIntType()
		{
			return isIntType(theType);
		}

		/**
		 * @param type The type to check
		 * @return Whether the given type can be used with integer operators
		 */
		public static boolean isIntType(Class<?> type)
		{
			return Long.TYPE.equals(type) || Integer.TYPE.equals(type) || Short.TYPE.equals(type)
				|| Character.TYPE.equals(type) || Byte.TYPE.equals(type);
		}
	}

	/**
	 * Validates or evaluates an expression
	 * 
	 * @param struct The expression to validate or evluate
	 * @param env The environment to evaluate the expression within
	 * @param asType Whether the return value is expected to be a type
	 * @param withValues Whether to evaluate the value of the expression, or just validate it
	 * @return The result of the expression
	 * @throws EvaluationException If the expression cannot be validated or evaluated
	 */
	EvalResult evaluate(ParseStruct struct, EvaluationEnvironment env, boolean asType,
		boolean withValues) throws EvaluationException;
}
