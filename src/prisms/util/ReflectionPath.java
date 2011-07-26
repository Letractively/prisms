/*
 * ReflectionPath.java Created Jul 22, 2011 by Andrew Butler, PSL
 */
package prisms.util;

import java.lang.reflect.AccessibleObject;

/**
 * A reflection path keeps a set of "steps" going from one type to another type. Each step is a
 * non-static field or non-static, zero-argument method. When this path is followed, an instance of
 * the starting type is given and the first step is invoked on that instance. The next step is
 * invoked on the return value of the first step, and so on. The return value of the last step as
 * invoked on the result of hte second-to-last step is the result.
 * 
 * @param <S> The type at the beginning of the path
 */
public class ReflectionPath<S>
{
	final Class<S> theStartType;

	final AccessibleObject [] theReflectPath;

	private ReflectionPath(Class<S> startType, AccessibleObject [] reflectPath)
	{
		theStartType = startType;
		theReflectPath = reflectPath;
	}

	/** @return The starting type for this path */
	public Class<S> getStartType()
	{
		return theStartType;
	}

	/**
	 * @return The number of steps in this path. Paths returned from {@link #path(Object)} will be
	 *         this long plus one (because the start item is included)
	 */
	public int getPathLength()
	{
		return theReflectPath.length;
	}

	/**
	 * Gets one step along the path
	 * 
	 * @param index The index of the step to get
	 * @return The step in this path at the given index
	 */
	public AccessibleObject getStep(int index)
	{
		return theReflectPath[index];
	}

	/**
	 * Follows this path from the given start instance
	 * 
	 * @param start The start instance to follow the path from
	 * @return The end of the path
	 * @throws IllegalStateException If the path cannot be followed either because of security or if
	 *         one of the methods throws an exception
	 */
	public Object follow(S start) throws IllegalStateException
	{
		Object ret = start;
		for(int p = 0; p < theReflectPath.length; p++)
		{
			try
			{
				AccessibleObject ao = theReflectPath[p];
				if(ao instanceof java.lang.reflect.Method)
					ret = ((java.lang.reflect.Method) ao).invoke(ret);
				else if(ao instanceof java.lang.reflect.Field)
					ret = ((java.lang.reflect.Field) ao).get(ret);
				else
					throw new IllegalStateException();
			} catch(java.lang.reflect.InvocationTargetException e)
			{
				throw new IllegalStateException("Reflection path failed at " + toString(p)
					+ " for " + start, e.getCause());
			} catch(IllegalAccessException e)
			{
				throw new IllegalStateException("Reflection path failed at " + toString(p)
					+ " for " + start, e);
			}
			if(ret == null)
				throw new IllegalStateException(toString(p) + " for " + start + " was null!");
		}
		return ret;
	}

	/**
	 * Follows this path from the given start instance, returning the entire path followed
	 * 
	 * @param start The start instance to follow the path from
	 * @return Every object along the path, including the start
	 * @throws IllegalStateException If the path cannot be followed either because of security or if
	 *         one of the methods throws an exception
	 */
	public Object [] path(S start) throws IllegalStateException
	{
		java.util.ArrayList<Object> ret = new java.util.ArrayList<Object>();
		ret.add(start);
		Object step = start;
		for(int p = 0; p < theReflectPath.length; p++)
		{
			try
			{
				AccessibleObject ao = theReflectPath[p];
				if(ao instanceof java.lang.reflect.Method)
					step = ((java.lang.reflect.Method) ao).invoke(step);
				else if(ao instanceof java.lang.reflect.Field)
					step = ((java.lang.reflect.Field) ao).get(step);
				else
					throw new IllegalStateException();
			} catch(java.lang.reflect.InvocationTargetException e)
			{
				throw new IllegalStateException("Reflection path failed at " + toString(p)
					+ " for " + start, e.getCause());
			} catch(IllegalAccessException e)
			{
				throw new IllegalStateException("Reflection path failed at " + toString(p)
					+ " for " + start, e);
			}
			if(step == null)
				throw new IllegalStateException(toString(p) + " for " + start + " was null!");
			ret.add(step);
		}
		return ret.toArray();
	}

	private String toString(int p)
	{
		StringBuilder ret = new StringBuilder();
		ret.append(theStartType.getName());
		for(int p0 = 0; p0 < theReflectPath.length && p0 <= p; p++)
		{
			ret.append('.');
			AccessibleObject ao = theReflectPath[p0];
			if(ao instanceof java.lang.reflect.Method)
				ret.append(((java.lang.reflect.Method) ao).getName()).append("()");
			else
				ret.append(((java.lang.reflect.Field) ao).getName());
		}
		return ret.toString();
	}

	@Override
	public String toString()
	{
		return toString(theReflectPath.length);
	}

	/**
	 * Compiles a path from a starting type and a path string
	 * 
	 * @param <S> The starting type
	 * @param type The starting type class
	 * @param reflectPath The reflection path string to parse
	 * @return The reflection path parsed
	 * @throws SecurityException If the fields or methods referred to in the path cannot be accessed
	 * @throws NoSuchMethodException If one of the methods referred to in the path does not exist or
	 *         is not zero-length and non-static
	 * @throws NoSuchFieldException If one of the fields referred to in the path does not exist or
	 *         is static
	 */
	public static <S> ReflectionPath<S> compile(Class<S> type, String reflectPath)
		throws SecurityException, NoSuchMethodException, NoSuchFieldException
	{
		String [] pathSplit;
		if(reflectPath.contains("."))
			pathSplit = reflectPath.split("\\.");
		else
			pathSplit = new String [] {reflectPath};
		for(int p = 0; p < pathSplit.length; p++)
			pathSplit[p] = pathSplit[p].trim();
		return compile(type, pathSplit);
	}

	/**
	 * Compiles a path from a starting type and a path of step strings
	 * 
	 * @param <S> The starting type
	 * @param type The starting type class
	 * @param reflectPath The reflection path string to parse
	 * @return The reflection path parsed
	 * @throws SecurityException If the fields or methods referred to in the path cannot be accessed
	 * @throws NoSuchMethodException If one of the methods referred to in the path does not exist or
	 *         is not zero-length and non-static
	 * @throws NoSuchFieldException If one of the fields referred to in the path does not exist or
	 *         is static
	 */
	public static <S> ReflectionPath<S> compile(Class<S> type, String [] reflectPath)
		throws SecurityException, NoSuchMethodException, NoSuchFieldException
	{
		Class<?> type_i = type;
		AccessibleObject [] realPath = new AccessibleObject [reflectPath.length];
		for(int i = 0; i < reflectPath.length; i++)
		{
			if(reflectPath[i].endsWith("()"))
			{
				String methodName = reflectPath[i].substring(0, reflectPath[i].length() - 2).trim();
				java.lang.reflect.Method method = type_i.getMethod(methodName, new Class [0]);
				if((method.getModifiers() & java.lang.reflect.Modifier.STATIC) > 0)
					throw new NoSuchMethodException("Method " + type_i.getName() + "."
						+ reflectPath[i] + " is static");
				realPath[i] = method;
				type_i = method.getReturnType();
			}
			else
			{
				java.lang.reflect.Field field = type_i.getField(reflectPath[i]);
				if((field.getModifiers() & java.lang.reflect.Modifier.STATIC) > 0)
					throw new NoSuchMethodException("Field " + type_i.getName() + "."
						+ reflectPath[i] + " is static");
				realPath[i] = field;
				type_i = field.getType();
			}
		}
		return new ReflectionPath<S>(type, realPath);
	}
}
