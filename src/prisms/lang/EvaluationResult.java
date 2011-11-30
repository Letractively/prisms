/*
 * EvaluationResult.java Created Nov 18, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** Contains the type and value of the results of evaluating an expression */
public class EvaluationResult
{
	/** Represents a type of control operation */
	public static enum ControlType
	{
		/** Represents the return of a function */
		RETURN,
		/** Represents the continuation of a loop */
		CONTINUE,
		/** Represents the termination of a loop or the exit from a switch case */
		BREAK;
	}

	private final Type theType;

	private final Object theValue;

	private final boolean isType;

	private final String thePackageName;

	private final ControlType theControl;

	private final ParsedItem theControlItem;

	/**
	 * Creates a result that represents a value
	 * 
	 * @param type The type of the result
	 * @param value The value of the result
	 */
	public EvaluationResult(Type type, Object value)
	{
		theType = type;
		theValue = value;
		isType = false;
		thePackageName = null;
		theControl = null;
		theControlItem = null;
	}

	/**
	 * Creates a result that represents a type
	 * 
	 * @param type The result type
	 */
	public EvaluationResult(Type type)
	{
		theType = type;
		isType = true;
		theValue = null;
		thePackageName = null;
		theControl = null;
		theControlItem = null;
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
		theControl = null;
		theControlItem = null;
	}

	/**
	 * Creates a result that represents a control operation
	 * 
	 * @param control The control operation that this result is to represent
	 * @param value The value that is being returned for a return operation
	 * @param item The control statement that will be used to throw an exception if that type of control is not valid in
	 *        the context
	 */
	public EvaluationResult(ControlType control, Object value, ParsedItem item)
	{
		theControl = control;
		theValue = value;
		isType = false;
		theType = null;
		thePackageName = null;
		theControlItem = item;
	}

	/** @return Whether this result is a value as opposed to a type, package, or control operation */
	public boolean isValue()
	{
		return thePackageName == null && !isType && theControl == null;
	}

	/** @return Whether this result is a type as opposed to a value or a package */
	public boolean isType()
	{
		return isType;
	}

	/** @return The type of the evaluated expression (may be null if this is a package) */
	public Type getType()
	{
		return theType;
	}

	/**
	 * @return The name of the package this result represents, or null if it represents a type or value
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

	/** @return The type of control that was the result of the block of statements */
	public ControlType getControl()
	{
		return theControl;
	}

	/** @return The control item that caused this control result */
	public ParsedItem getControlItem()
	{
		return theControlItem;
	}

	/** @return The name of the type of this result */
	public String typeString()
	{
		if(thePackageName != null)
			return thePackageName;
		else if(theControl != null)
			return theControl.name().toLowerCase();
		else
			return theType.toString();
	}

	/** @return The name before the first '.' in this result's type name */
	public String getFirstVar()
	{
		if(thePackageName != null)
			return getFirstVar(thePackageName);
		else
			return getFirstVar(theType.toString());
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
		return isIntType(theType.getBaseType());
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
	 * @return The primitive type represented by the given wrapper class, or null if the given type is not primitive or
	 *         a primitive wrapper
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
