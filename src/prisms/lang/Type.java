package prisms.lang;

/** Represents the type of an evaluated expression */
public class Type
{
	private Class<?> theBaseType;

	private Type [] theParamTypes;

	private String theName;

	private boolean isBounded;

	private boolean isUpperBound;

	private Type theBoundType;

	/** The object whose class is returned for the type of the "null" identifier */
	public static final Object NULL = new Object()
	{
		@Override
		public String toString()
		{
			return "null";
		}
	};

	/**
	 * @param base The base type
	 * @param paramTypes The type parameters for the type
	 */
	public Type(Class<?> base, Type... paramTypes)
	{
		theBaseType = base;
		theParamTypes = paramTypes;
	}

	/**
	 * @param bound The bounding type
	 * @param upper Whether the type is upper- or lower- bound
	 */
	public Type(Type bound, boolean upper)
	{
		isBounded = true;
		theBoundType = bound;
		isUpperBound = upper;
	}

	/**
	 * Parses a type from a java.lang.reflect.Type
	 * 
	 * @param type The reflected type to represent
	 */
	public Type(java.lang.reflect.Type type)
	{
		init(type);
	}

	private void init(java.lang.reflect.Type type)
	{
		if(type == null)
		{
			theBaseType = Void.TYPE;
			theParamTypes = new Type [0];
		}
		else if(type instanceof Class)
		{
			theBaseType = (Class<?>) type;
			theParamTypes = new Type [0];
		}
		else if(type instanceof java.lang.reflect.ParameterizedType)
		{
			java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) type;
			theBaseType = (Class<?>) pt.getRawType();
			theParamTypes = new Type [pt.getActualTypeArguments().length];
			for(int p = 0; p < theParamTypes.length; p++)
				theParamTypes[p] = new Type(pt.getActualTypeArguments()[p]);
		}
		else if(type instanceof java.lang.reflect.WildcardType)
		{
			isBounded = true;
			java.lang.reflect.WildcardType wt = (java.lang.reflect.WildcardType) type;
			isUpperBound = wt.getLowerBounds().length == 0;
			if(wt.getLowerBounds().length > 0)
				theBoundType = new Type(wt.getLowerBounds()[0]);
			else if(wt.getUpperBounds().length > 0)
				theBoundType = new Type(wt.getUpperBounds()[0]);
		}
		else if(type instanceof java.lang.reflect.TypeVariable)
		{
			java.lang.reflect.TypeVariable<?> tv = (java.lang.reflect.TypeVariable<?>) type;
			theName = tv.getName();
			isBounded = true;
			isUpperBound = true;
			if(tv.getBounds().length > 0)
				theBoundType = new Type(tv.getBounds()[0]);
		}
		else if(type instanceof java.lang.reflect.GenericArrayType)
		{
			java.lang.reflect.GenericArrayType at = (java.lang.reflect.GenericArrayType) type;
			init(at.getGenericComponentType());
			if(theBaseType != null)
				theBaseType = java.lang.reflect.Array.newInstance(theBaseType, 0).getClass();
			else if(theBoundType != null)
				theBoundType = theBoundType.getArrayType();
			else
				theBoundType = new Type(java.lang.reflect.Array.newInstance(Object.class, 0).getClass());
		}
		else
			throw new IllegalArgumentException("Unrecognize reflect type: " + type.getClass().getName());
	}

	/**
	 * @param c The type to check
	 * @return Whether a variable with a type of the given class can be assigned to a variable of this type
	 */
	public boolean isAssignableFrom(Class<?> c)
	{
		if(isBounded)
		{
			if(isUpperBound)
				return theBoundType.isAssignableFrom(c);
			else
				return theBoundType.canAssignTo(c);
		}
		else
			return isAssignable(theBaseType, c);
	}

	/**
	 * @param c The type to check
	 * @return Whether a variable of this type can be assigned to a variable of the type of the given class
	 */
	public boolean canAssignTo(Class<?> c)
	{
		if(isBounded)
		{
			if(isUpperBound)
				return c.isAssignableFrom(c);
			else
				return c == Object.class;
		}
		else
			return c.isAssignableFrom(theBaseType);
	}

	/**
	 * @param t The type to check
	 * @return Whether a variable of the given type can be assigned to a variable of this type
	 */
	public boolean isAssignable(Type t)
	{
		if(t != null && t.theBaseType == NULL.getClass())
			return !isPrimitive();
		if(isBounded)
		{
			if(t == null)
				return theBoundType == null || theBoundType.isAssignable(null);
			else if(t.isBounded)
			{
				if(isUpperBound)
				{
					if(t.isUpperBound)
						return theBoundType.isAssignable(t.theBoundType);
					else
						return theBoundType == null || theBoundType.isAssignable(null);
				}
				else
				{
					if(t.isUpperBound)
						return false;
					else
						return t.theBoundType == null || t.theBoundType.isAssignable(theBoundType);
				}
			}
			else
			{
				if(isUpperBound)
					return theBoundType == null || theBoundType.isAssignableFrom(t.theBaseType);
				else
					return theBoundType.canAssignTo(t.theBaseType);
			}
		}
		else
		{
			if(t == null)
				return theBaseType == Object.class;
			else if(t.isBounded)
			{
				if(t.isUpperBound)
				{
					if(t.theBoundType == null)
						return theBaseType == Object.class;
					else
						return isAssignable(t.theBoundType);
				}
				else
					return theBaseType == Object.class;
			}
			else
			{
				if(!isAssignable(theBaseType, t.theBaseType))
					return false;
				for(int p = 0; p < theParamTypes.length; p++)
					if(!theParamTypes[p].isAssignable(t.resolve(theBaseType.getTypeParameters()[p], theBaseType, null)))
						return false;
				return true;
			}
		}
	}

	private static boolean isAssignable(Class<?> to, Class<?> from)
	{
		if(to == from)
			return true;
		if(to == Void.TYPE)
			return false;
		if(to.isPrimitive())
		{
			if(!from.isPrimitive())
				return false;
			if(to == Boolean.TYPE)
				return false;
			if(to == Character.TYPE)
				return from == Character.TYPE || from == Integer.TYPE || from == Short.TYPE || from == Byte.TYPE;
			if(to == Double.TYPE)
				return from != Boolean.TYPE;
			if(to == Float.TYPE)
				return from != Boolean.TYPE && from != Double.TYPE;
			if(to == Long.TYPE)
				return from != Boolean.TYPE && from != Double.TYPE && from != Float.TYPE;
			if(to == Integer.TYPE)
				return from == Character.TYPE || from == Integer.TYPE || from == Short.TYPE || from == Byte.TYPE;
			if(to == Short.TYPE)
				return from == Short.TYPE || from == Byte.TYPE;
			if(to == Byte.TYPE)
				return from == Byte.TYPE;
		}
		if(from.isPrimitive())
			return false;
		if(from == NULL.getClass())
			return true;
		return to.isAssignableFrom(from);
	}

	/**
	 * Resolves a type that may be dependent upon this type (generics)
	 * 
	 * @param type The type to resolve
	 * @param declaringClass The class that declared the given type
	 * @param methodTypes A map of type variable name/type of the inferred types of an invocation for a method's
	 *        declared type variables. May be null if this resolution is not for a type-parameter-declaring method.
	 * @return The resolved type
	 */
	public Type resolve(java.lang.reflect.Type type, Class<?> declaringClass, java.util.Map<String, Type> methodTypes)
	{
		if(type == null)
			return new Type(type);
		else if(type instanceof Class)
			return new Type(type);
		else if(type instanceof java.lang.reflect.ParameterizedType)
		{
			java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) type;
			Type ret = new Type(pt.getRawType());
			ret.theParamTypes = new Type [pt.getActualTypeArguments().length];
			for(int p = 0; p < ret.theParamTypes.length; p++)
				ret.theParamTypes[p] = resolve(pt.getActualTypeArguments()[p], declaringClass, methodTypes);
			return ret;
		}
		else if(type instanceof java.lang.reflect.WildcardType)
		{
			Type ret = new Type(null);
			ret.isBounded = true;
			java.lang.reflect.WildcardType wt = (java.lang.reflect.WildcardType) type;
			ret.isUpperBound = wt.getLowerBounds().length == 0;
			if(wt.getLowerBounds().length > 0)
				ret.theBoundType = resolve(wt.getLowerBounds()[0], declaringClass, methodTypes);
			else if(wt.getUpperBounds().length > 0)
				ret.theBoundType = resolve(wt.getUpperBounds()[0], declaringClass, methodTypes);
			return ret;
		}
		else if(type instanceof java.lang.reflect.TypeVariable)
		{
			java.lang.reflect.TypeVariable<?> tv = (java.lang.reflect.TypeVariable<?>) type;
			if(tv.getName().equals(theName))
				return this;
			if(methodTypes != null && methodTypes.get(tv.getName()) != null)
				return methodTypes.get(tv.getName());
			if(theBaseType != null)
			{
				int decIndex = prisms.util.ArrayUtils.indexOf(declaringClass.getTypeParameters(), tv);
				if(decIndex >= 0)
				{
					java.lang.reflect.Type[] path = getTypePath(declaringClass, theBaseType);
					if(path == null)
						return new Type(type);
					if(path.length > 0 && path[0] instanceof java.lang.reflect.ParameterizedType)
					{
						java.lang.reflect.ParameterizedType superPath = (java.lang.reflect.ParameterizedType) path[0];
						java.lang.reflect.Type paramType = superPath.getActualTypeArguments()[decIndex];
						return resolve(paramType, (Class<?>) (path[1] instanceof Class ? path[1]
							: ((java.lang.reflect.ParameterizedType) path[1]).getRawType()), methodTypes);
					}
				}
				for(int p = 0; p < theBaseType.getTypeParameters().length; p++)
					if(theBaseType.getTypeParameters()[p].getName().equals(tv.getName()))
					{
						if(theParamTypes.length > 0)
							return theParamTypes[p];
					}
			}
			Type ret = new Type(null);
			ret.theBaseType = null;
			ret.theName = tv.getName();
			ret.isBounded = true;
			ret.isUpperBound = true;
			if(tv.getBounds().length > 0)
				ret.theBoundType = resolve(tv.getBounds()[0], declaringClass, methodTypes);
			return ret;
		}
		else if(type instanceof java.lang.reflect.GenericArrayType)
		{
			java.lang.reflect.GenericArrayType at = (java.lang.reflect.GenericArrayType) type;
			Type ret = resolve(at.getGenericComponentType(), declaringClass, methodTypes);
			if(ret.theBaseType != null)
			{
				if(Void.TYPE != ret.theBaseType)
					ret.theBaseType = java.lang.reflect.Array.newInstance(ret.theBaseType, 0).getClass();
			}
			else if(ret.theBoundType != null)
				ret.theBoundType = ret.theBoundType.getArrayType();
			else
				ret.theBoundType = new Type(java.lang.reflect.Array.newInstance(Object.class, 0).getClass());
			return ret;
		}
		else
			throw new IllegalArgumentException("Unrecognize reflect type: " + type.getClass().getName());
	}

	static java.lang.reflect.Type[] getTypePath(Class<?> superType, Class<?> subType)
	{
		if(superType == subType)
			return new java.lang.reflect.Type [] {superType};
		java.lang.reflect.Type[] ret;
		if(subType.getSuperclass() != null)
		{
			ret = getTypePath(superType, subType.getSuperclass());
			if(ret != null)
			{
				ret[ret.length - 1] = subType.getGenericSuperclass();
				ret = prisms.util.ArrayUtils.add(ret, subType);
				return ret;
			}
		}
		for(int i = 0; i < subType.getInterfaces().length; i++)
		{
			ret = getTypePath(superType, subType.getInterfaces()[i]);
			if(ret != null)
			{
				ret[ret.length - 1] = subType.getGenericInterfaces()[i];
				ret = prisms.util.ArrayUtils.add(ret, subType);
				return ret;
			}
		}
		return null;
	}

	/** @return Whether this represents a primitive type or not */
	public boolean isPrimitive()
	{
		return theBaseType != null && theBaseType.isPrimitive();
	}

	/** @return Whether this represents an array type or not */
	public boolean isArray()
	{
		if(theBaseType != null)
			return theBaseType.isArray();
		else if(theBoundType != null)
			return theBoundType.isArray();
		else
			return false;
	}

	/** @return This type's base type. Will be null if this is a wildcard type. */
	public Class<?> getBaseType()
	{
		return theBaseType;
	}

	/**
	 * @return This type's parameters. Zero-length if this type's base type is not generic. Null if this is a wildcard
	 *         type.
	 */
	public Type [] getParamTypes()
	{
		return theParamTypes;
	}

	/** @return The name of this type variable, or null if this type is not variable */
	public String getName()
	{
		return theName;
	}

	/** @return The bound of this type */
	public Type getBoundType()
	{
		return theBoundType;
	}

	/** @return Whether this type is upper- or lower-bound */
	public boolean isUpperBound()
	{
		return isUpperBound;
	}

	/** @return This array-type's component type */
	public Type getComponentType()
	{
		Type ret;
		if(theBaseType != null)
			ret = new Type(theBaseType.getComponentType(), theParamTypes);
		else if(theBoundType != null)
			ret = new Type(theBoundType.getComponentType(), isUpperBound);
		else
			throw new IllegalStateException("Type " + this + " is not an array type");
		return ret;
	}

	/** @return The class that most closely represents this type */
	public Class<?> toClass()
	{
		if(theBaseType != null)
			return theBaseType;
		else if(theBoundType != null)
		{
			if(isUpperBound)
				return theBoundType.toClass();
			else
				return Object.class;
		}
		else
			return Object.class;
	}

	/** @return The array type whose component type is this type */
	public Type getArrayType()
	{
		Type ret;
		if(theBaseType != null)
		{
			if(Void.TYPE == theBaseType)
				ret = this;
			else
				ret = new Type(java.lang.reflect.Array.newInstance(theBaseType, 0).getClass(), theParamTypes);
		}
		else if(theBoundType != null)
			ret = new Type(theBoundType.getArrayType(), isUpperBound);
		else
			ret = new Type(new Type(java.lang.reflect.Array.newInstance(Object.class, 0).getClass()), isUpperBound);
		return ret;
	}

	/**
	 * @param t The type to get the common type of
	 * @return The most specific type that is assignable from both this and <code>t</code>. May be null if one or both
	 *         types are primitive and incompatible
	 */
	public Type getCommonType(Type t)
	{
		if(theBaseType == NULL.getClass())
			return t;
		if(t.theBaseType == NULL.getClass())
			return this;
		if(isAssignable(t))
			return this;
		if(t.isAssignable(this))
			return t;
		if(!t.isPrimitive() && !t.isPrimitive())
		{
			if(theBaseType != null)
			{
				if(theBaseType.getSuperclass() != null && t.canAssignTo(theBaseType.getSuperclass()))
					return new Type(theBaseType.getSuperclass(), theParamTypes);
				for(Class<?> intf : theBaseType.getInterfaces())
					if(t.canAssignTo(intf))
					{
						Type [] paramTypes = new Type [intf.getTypeParameters().length];
						for(int p = 0; p < paramTypes.length; p++)
							paramTypes[p] = resolve(intf.getTypeParameters()[p], intf, null);
						return new Type(intf, paramTypes);
					}
				return new Type(Object.class);
			}
			else if(theBoundType != null)
			{
				Type ct = theBoundType.getCommonType(t);
				if(ct != null)
					return new Type(ct, isUpperBound);
				else
					return new Type(Object.class);
			}
			else
				return new Type(Object.class);
		}

		if(!t.isPrimitive() || !t.isPrimitive())
			return null;
		if(Boolean.TYPE.equals(theBaseType) || Boolean.TYPE.equals(t.theBaseType))
			return null;
		if(Double.TYPE.equals(theBaseType) || Double.TYPE.equals(t.theBaseType))
			return new Type(Double.TYPE);
		if(Float.TYPE.equals(theBaseType) || Float.TYPE.equals(t.theBaseType))
			return new Type(Float.TYPE);
		if(Long.TYPE.equals(theBaseType) || Long.TYPE.equals(t.theBaseType))
			return new Type(Long.TYPE);
		if(Integer.TYPE.equals(theBaseType) || Integer.TYPE.equals(t.theBaseType))
			return new Type(Integer.TYPE);
		if(Character.TYPE.equals(theBaseType) || Character.TYPE.equals(t.theBaseType))
			return new Type(Character.TYPE);
		if(Short.TYPE.equals(theBaseType) || Short.TYPE.equals(t.theBaseType))
			return new Type(Short.TYPE);
		return new Type(Byte.TYPE);
	}

	@Override
	public String toString()
	{
		return toString(null);
	}

	/**
	 * Like {@link #toString()}, but shortens this type's string representation wherever possible by cutting off package
	 * names where they can be made implicit.
	 * 
	 * @param env The evaluation environment to check type imports of. May be null.
	 * @return A string representing this type
	 */
	public String toString(EvaluationEnvironment env)
	{
		StringBuilder ret = new StringBuilder();
		if(isBounded)
		{
			ret.append("? ");
			if(theBoundType != null)
			{
				ret.append(isUpperBound ? "extends " : "super ");
				ret.append(theBoundType.toString(env));
			}
		}
		else
		{
			int arrayDim = 0;
			Class<?> base = theBaseType;
			while(base.isArray())
			{
				arrayDim++;
				base = base.getComponentType();
			}
			String imp = isImported(base, env);
			if(imp != null)
				ret.append(imp);
			else if(base == NULL.getClass())
				ret.append("null");
			else
				ret.append(base.getName());
			if(theParamTypes.length > 0)
			{
				ret.append('<');
				boolean first = true;
				for(Type p : theParamTypes)
				{
					if(first)
						first = false;
					else
						ret.append(", ");
					ret.append(p.toString(env));
				}
				ret.append('>');
			}
			for(int i = 0; i < arrayDim; i++)
				ret.append("[]");
		}
		return ret.toString();
	}

	/**
	 * Checks to see whether a class may be represented without its package name
	 * 
	 * @param t The class to check
	 * @param env The evaluation environment to check the class in. May be null.
	 * @return True if the class belongs to the default package or java.lang, or if the class's name or its package have
	 *         been imported into <code>env</code>
	 */
	public static String isImported(Class<?> t, EvaluationEnvironment env)
	{
		String name = t.getName();
		int idx = name.indexOf('.');
		if(idx < 0)
			return name;
		String pkg = name.substring(0, idx);
		name = name.substring(idx + 1);
		if(pkg.equals("java.lang"))
			return name;
		if(env == null)
			return null;
		return env.getImportType(name) != null ? name : null;
	}

	@Override
	public boolean equals(Object o)
	{
		if(!(o instanceof Type))
			return false;
		Type t = (Type) o;
		if(isBounded != t.isBounded)
			return false;
		if(theBaseType == null ? t.theBaseType != null : !theBaseType.equals(t.theBaseType))
			return false;
		if(theBoundType == null ? t.theBoundType != null : !theBoundType.equals(t.theBoundType))
			return false;
		if(isUpperBound != t.isUpperBound)
			return false;
		if(theName == null ? t.theName != null : !theName.equals(t.theName))
			return false;
		if(!prisms.util.ArrayUtils.equals(theParamTypes, t.theParamTypes))
			return false;
		return true;
	}

	/**
	 * @param wrapper The wrapper for a primtive type
	 * @return The wrapped primitive type, or null if the given type is not primitive or a primitive wrapper
	 */
	public static Class<?> getPrimitiveType(Class<?> wrapper)
	{
		if(wrapper.isPrimitive())
			return wrapper;
		if(wrapper.equals(Double.class))
			return Double.TYPE;
		else if(wrapper.equals(Float.class))
			return Float.TYPE;
		else if(wrapper.equals(Long.class))
			return Long.TYPE;
		else if(wrapper.equals(Integer.class))
			return Integer.TYPE;
		else if(wrapper.equals(Character.class))
			return Character.TYPE;
		else if(wrapper.equals(Short.class))
			return Short.TYPE;
		else if(wrapper.equals(Byte.class))
			return Byte.TYPE;
		else if(wrapper.equals(Boolean.class))
			return Boolean.TYPE;
		else
			return null;
	}

	/**
	 * @param t The type to represent
	 * @return A string representation of the type
	 */
	public static String typeString(Class<?> t)
	{
		if(t.isArray())
			return typeString(t.getComponentType()) + "[]";
		else
			return t.getName();
	}
}
