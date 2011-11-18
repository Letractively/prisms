/*
 * PrismsLangUtils.java Created Nov 18, 2011 by Andrew Butler, PSL
 */
package prisms.lang;

/** Utility methods for the prisms.lang packages */
public class PrismsLangUtils
{
	private PrismsLangUtils()
	{
	}

	/**
	 * @param t1 The type of the variable to assign to
	 * @param t2 The type of the value to assign
	 * @return Whether t1 is the same or a super class of t2
	 */
	public static boolean canAssign(Class<?> t1, Class<?> t2)
	{
		if(t1.isAssignableFrom(t2))
			return true;
		if(!t1.isPrimitive() || !t2.isPrimitive())
			return false;
		if(Boolean.TYPE.equals(t1) || Character.TYPE.equals(t1) || Boolean.TYPE.equals(t2))
			return false;
		if(Double.TYPE.equals(t1))
			return true; // Can assign any number type or char to double
		if(Double.TYPE.equals(t2))
			return false; // Can't assign double to any lesser type
		if(Float.TYPE.equals(t1))
			return true;
		if(Float.TYPE.equals(t2))
			return false;
		if(Long.TYPE.equals(t1))
			return true;
		if(Long.TYPE.equals(t2))
			return false;
		if(Integer.TYPE.equals(t1))
			return true;
		if(Integer.TYPE.equals(t2))
			return false;
		if(Short.TYPE.equals(t1) || Character.TYPE.equals(t1))
			return true;
		return !(Short.TYPE.equals(t2) || Character.TYPE.equals(t2));
	}

	/**
	 * @param t1 One type
	 * @param t2 The other type
	 * @return A type that is the same or a super class of both t1 and t2, or null if no such class
	 *         exists
	 */
	public static Class<?> getMaxType(Class<?> t1, Class<?> t2)
	{
		if(t1.isAssignableFrom(t2))
			return t1;
		if(t2.isAssignableFrom(t1))
			return t2;
		if(!t1.isPrimitive() || !t2.isPrimitive())
			return null;
		if(Boolean.TYPE.equals(t1) || Boolean.TYPE.equals(t2))
			return null;
		if(Double.TYPE.equals(t1) || Double.TYPE.equals(t2))
			return Double.TYPE;
		if(Float.TYPE.equals(t1) || Float.TYPE.equals(t2))
			return Float.TYPE;
		if(Long.TYPE.equals(t1) || Long.TYPE.equals(t2))
			return Long.TYPE;
		if(Integer.TYPE.equals(t1) || Integer.TYPE.equals(t2))
			return Integer.TYPE;
		if(Character.TYPE.equals(t1) || Character.TYPE.equals(t2))
			return Character.TYPE;
		if(Short.TYPE.equals(t1) || Short.TYPE.equals(t2))
			return Short.TYPE;
		return Byte.TYPE;
	}
}
