/*
 * EvaluationResult.java Created Nov 18, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/**
 * Contains the type and value of the results of evaluating an expression
 * 
 * @param <T> The type of the result
 */
public class EvaluationResult<T>
{
	private final Class<? extends T> theType;

	private final T theValue;

	private final boolean isType;

	private final String thePackageName;

	/**
	 * Creates a result that represents a value
	 * 
	 * @param type The type of the result
	 * @param value The value of the result
	 */
	public EvaluationResult(Class<? extends T> type, T value)
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
	public EvaluationResult(Class<T> type)
	{
		theType = type;
		isType = true;
		theValue = null;
		thePackageName = null;
	}

	/**
	 * Creates a result that represents a package
	 * 
	 * @param packageName The name of the package
	 */
	public EvaluationResult(String packageName)
	{
		thePackageName = packageName;
		theType = null;
		isType = false;
		theValue = null;
	}

	/** @return Whether this result is a value as opposed to a type or a package */
	public boolean isValue()
	{
		return thePackageName == null && !isType;
	}

	/** @return Whether this result is a type as opposed to a value or a package */
	public boolean isType()
	{
		return isType;
	}

	/** @return The type of the evaluated expression (may be null if this is a package) */
	public Class<? extends T> getType()
	{
		return theType;
	}

	/**
	 * @return The name of the package this result represents, or null if it represents a type or
	 *         value
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

	/**
	 * @param type The wrapper type to primitize
	 * @return The primitive type represented by the given wrapper class, or null if the given type
	 *         is not primitive or a primitive wrapper
	 */
	public static Class<?> getPrimitiveType(Class<?> type)
	{
		if(type.isPrimitive())
			return type;
		else if(Boolean.class.equals(type))
			return Boolean.TYPE;
		else if(Character.class.equals(type))
			return Character.TYPE;
		else if(Double.class.equals(type))
			return Double.TYPE;
		else if(Float.class.equals(type))
			return Float.TYPE;
		else if(Long.class.equals(type))
			return Long.TYPE;
		else if(Integer.class.equals(type))
			return Integer.TYPE;
		else if(Short.class.equals(type))
			return Short.TYPE;
		else if(Byte.class.equals(type))
			return Byte.TYPE;
		else
			return null;
	}
}
