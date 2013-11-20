package prisms.lang.types;

import prisms.lang.ParsedItem;
import prisms.lang.Type;

/** Represents a type name */
public class ParsedType extends prisms.lang.ParsedItem {
	private String theName;

	private int theArrayDimension;

	private ParsedType [] theParamTypes;

	private boolean isBounded;

	private ParsedType theBound;

	private boolean isUpperBound;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException {
		super.setup(parser, parent, match);
		isBounded = false;
		theParamTypes = new ParsedType[0];
		if("basic type".equals(match.config.get("name"))) {
			isBounded = false;
			String name = "";
			for(prisms.lang.ParseMatch m : getAllStored("name")) {
				if(name.length() > 0)
					name += ".";
				name += m.text;
			}
			theName = name;
		} else if("wildcard type".equals(match.config.get("name"))) {
			isBounded = true;
			if(getStored("wildcard") != null)
				isUpperBound = true;
			else if(getStored("bound") != null) {
				theBound = (ParsedType) parser.parseStructures(this, getStored("bound"))[0];
				isUpperBound = getStored("extendsBound") != null;
			} else
				setup(parser, parent, getStored("type"));
		} else {
			theName = ((ParsedType) parser.parseStructures(this, getStored("base"))[0]).getName();
			isBounded = false;
			ParsedItem [] pts = parser.parseStructures(this, getAllStored("paramType"));
			theParamTypes = new ParsedType[pts.length];
			System.arraycopy(pts, 0, theParamTypes, 0, pts.length);
		}
		theArrayDimension = getAllStored("array").length;
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues)
		throws prisms.lang.EvaluationException {
		if(theName != null) {
			Type [] paramTypes = new Type[theParamTypes.length];
			Class<?> ret = getClassFromName(theName, env);
			if(ret != null) {
				for(int p = 0; p < paramTypes.length; p++)
					paramTypes[p] = theParamTypes[p].evaluate(env, true, withValues).getType();
				if(paramTypes.length > 0 && ret.getTypeParameters().length == 0) {
					String args = prisms.util.ArrayUtils.toString(paramTypes);
					args = args.substring(1, args.length() - 1);
					int index = getStored("base").index + getStored("base").text.length();
					throw new prisms.lang.EvaluationException("The type " + ret.getName()
						+ " is not generic; it cannot be parameterized with arguments <" + args + ">", this, index);
				}
				if(paramTypes.length > 0 && paramTypes.length != ret.getTypeParameters().length) {
					String type = ret.getName() + "<";
					for(java.lang.reflect.Type t : ret.getTypeParameters())
						type += t + ", ";
					type = type.substring(0, type.length() - 2);
					type += ">";
					String args = prisms.util.ArrayUtils.toString(paramTypes);
					args = args.substring(1, args.length() - 1);
					int index = getStored("base").index + getStored("base").text.length();
					throw new prisms.lang.EvaluationException("Incorrect number of arguments for type " + type
						+ "; it cannot be parameterized with arguments <" + args + ">", this, index);
				}
				Type t = new Type(ret, paramTypes);
				for(int i = 0; i < theArrayDimension; i++)
					t = t.getArrayType();
				return new prisms.lang.EvaluationResult(t);
			}
			StringBuilder name = new StringBuilder(theName);
			int idx = name.lastIndexOf(".");
			while(idx >= 0) {
				name.setCharAt(idx, '$');
				ret = getClassFromName(name.toString(), env);
				if(ret != null) {
					for(int p = 0; p < paramTypes.length; p++)
						paramTypes[p] = theParamTypes[p].evaluate(env, true, withValues).getType();
					Type t = new Type(ret, paramTypes);
					for(int i = 0; i < theArrayDimension; i++)
						t = t.getArrayType();
					return new prisms.lang.EvaluationResult(t);
				}
				idx = name.lastIndexOf(".");
			}
			name = new StringBuilder(theName);
			idx = name.indexOf(".");
			ret = env.getImportType(idx >= 0 ? name.substring(0, idx) : name.toString());
			if(ret != null) {
				if(idx >= 0) {
					name.delete(0, idx + 1);
					for(idx = name.indexOf("."); idx >= 0; idx = name.indexOf("."))
						name.setCharAt(idx, '$');
					ret = getClassFromName(ret.getName() + name, env);
				}
				if(ret != null) {
					for(int p = 0; p < paramTypes.length; p++)
						paramTypes[p] = theParamTypes[p].evaluate(env, true, withValues).getType();
					Type t = new Type(ret, paramTypes);
					for(int i = 0; i < theArrayDimension; i++)
						t = t.getArrayType();
					return new prisms.lang.EvaluationResult(t);
				}
			}
			if(paramTypes.length == 0 && theArrayDimension == 0)
				if(env.getClassGetter().isPackage(theName))
					return new prisms.lang.EvaluationResult(theName);
		} else
			return new prisms.lang.EvaluationResult(new Type(theBound.evaluate(env, true, withValues).getType(), isUpperBound));

		throw new prisms.lang.EvaluationException(theName + " cannot be resolved to a type", this, getMatch().index);
	}

	/**
	 * @return The name of this type. This may be:
	 *         <ul>
	 *         <li>The name of the type declared</li>
	 *         <li>The raw type if this represents a parameterized type</li>
	 *         <li>null if this represents a wildcard type</li>
	 *         </ul>
	 */
	public String getName() {
		return theName;
	}

	/** @return The dimension of this type if it is an array */
	public int getArrayDimension() {
		return theArrayDimension;
	}

	/** Increases the array dimension of this type by 1 */
	public void addArrayDimension() {
		theArrayDimension++;
	}

	/** @return The parameter types of this generic type */
	public ParsedType [] getParameterTypes() {
		return theParamTypes;
	}

	/** @return Whether this type is a bounded (wild card) type */
	public boolean isBounded() {
		return isBounded;
	}

	/** @return The bound of this type */
	public ParsedType getBound() {
		return theBound;
	}

	/** @return Whether this bounded type is upper-bounded or lower-bounded */
	public boolean isUpperBound() {
		return isUpperBound;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents() {
		if(theBound != null)
			return new prisms.lang.ParsedItem[] {theBound};
		else
			return theParamTypes;
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException {
		if(dependent == theBound) {
			if(toReplace instanceof ParsedType || toReplace == null)
				theBound = (ParsedType) toReplace;
			else
				throw new IllegalArgumentException("Cannot replace the type bound with " + toReplace.getClass().getSimpleName());
		} else
			throw new IllegalArgumentException("No such dependent " + dependent);
	}

	/**
	 * @param name The name of the type to get
	 * @param env The evaluation environment to get the type in
	 * @return The type of the given name, or null if the given name does not refer to a valid type
	 */
	public static Class<?> getClassFromName(String name, prisms.lang.EvaluationEnvironment env) {
		if("boolean".equals(name))
			return Boolean.TYPE;
		else if("char".equals(name))
			return Character.TYPE;
		else if("double".equals(name))
			return Double.TYPE;
		else if("float".equals(name))
			return Float.TYPE;
		else if("long".equals(name))
			return Long.TYPE;
		else if("int".equals(name))
			return Integer.TYPE;
		else if("short".equals(name))
			return Short.TYPE;
		else if("byte".equals(name))
			return Byte.TYPE;
		else if("null".equals(name))
			return Type.NULL.getClass();
		else if("void".equals(name))
			return Void.TYPE;
		Class<?> clazz;
		try {
			clazz = Class.forName(name);
		} catch(ClassNotFoundException e) {
			clazz = null;
		}
		if(clazz != null)
			return clazz;
		clazz = env.getImportType(name);
		if(clazz != null)
			return clazz;
		try {
			clazz = Class.forName("java.lang." + name);
		} catch(ClassNotFoundException e) {
			clazz = null;
		}
		if(clazz != null)
			return clazz;
		return null;
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder();
		if(theName != null) {
			ret.append(theName);
			if(theParamTypes.length > 0) {
				ret.append('<');
				for(int i = 0; i < theParamTypes.length; i++) {
					if(i > 0)
						ret.append(", ");
					ret.append(theParamTypes[i]);
				}
				ret.append('>');
			}
		} else {
			ret.append('?');
			if(theBound != null) {
				ret.append(isUpperBound ? "extends " : "super ");
				ret.append(theBound);
			}
		}
		for(int i = 0; i < theArrayDimension; i++) {
			if(i == 0)
				ret.append(' ');
			ret.append("[]");
		}
		return ret.toString();
	}
}
