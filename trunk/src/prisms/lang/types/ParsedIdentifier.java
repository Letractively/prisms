/* ParsedIdentifier.java Created Nov 14, 2011 by Andrew Butler, PSL */
package prisms.lang.types;

import prisms.lang.EvaluationEnvironment;
import prisms.lang.EvaluationException;
import prisms.lang.EvaluationResult;
import prisms.lang.ParsedItem;

/** A simple identifier */
public class ParsedIdentifier extends Assignable {
	private String theName;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException {
		super.setup(parser, parent, match);
		theName = getStored("name").text;
	}

	/** @return This identifier's name */
	public String getName() {
		return theName;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents() {
		return new prisms.lang.ParsedItem[0];
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException {
		throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public String toString() {
		return theName;
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues)
		throws prisms.lang.EvaluationException {
		if("null".equals(theName))
			return new EvaluationResult(new prisms.lang.Type(prisms.lang.Type.NULL.getClass()), null);
		if(!asType) {
			prisms.lang.Type type = env.getVariableType(theName);
			if(type != null)
				return new EvaluationResult(type, withValues ? env.getVariable(theName, this, getStored("name").index) : null);
		}
		if("boolean".equals(theName))
			return new EvaluationResult(new prisms.lang.Type(Boolean.TYPE));
		else if("char".equals(theName))
			return new EvaluationResult(new prisms.lang.Type(Character.TYPE));
		else if("double".equals(theName))
			return new EvaluationResult(new prisms.lang.Type(Double.TYPE));
		else if("float".equals(theName))
			return new EvaluationResult(new prisms.lang.Type(Float.TYPE));
		else if("long".equals(theName))
			return new EvaluationResult(new prisms.lang.Type(Long.TYPE));
		else if("int".equals(theName))
			return new EvaluationResult(new prisms.lang.Type(Integer.TYPE));
		else if("short".equals(theName))
			return new EvaluationResult(new prisms.lang.Type(Short.TYPE));
		else if("byte".equals(theName))
			return new EvaluationResult(new prisms.lang.Type(Byte.TYPE));
		Class<?> clazz = env.getClassGetter().getClass(theName);
		if(clazz != null)
			return new EvaluationResult(new prisms.lang.Type(clazz));
		clazz = env.getImportType(theName);
		if(clazz != null)
			return new EvaluationResult(new prisms.lang.Type(clazz));
		clazz = env.getClassGetter().getClass("java.lang." + theName);
		if(clazz != null)
			return new EvaluationResult(new prisms.lang.Type(clazz));
		if(env.getClassGetter().isPackage(theName))
			return new EvaluationResult(theName);
		Class<?> importType = env.getImportMethodType(theName);
		if(importType != null) {
			for(java.lang.reflect.Field f : importType.getDeclaredFields()) {
				if(!theName.equals(f.getName()))
					continue;
				if((f.getModifiers() & java.lang.reflect.Modifier.STATIC) == 0)
					continue;
				if(env.usePublicOnly() && (f.getModifiers() & java.lang.reflect.Modifier.PUBLIC) == 0)
					continue;
				try {
					return new EvaluationResult(new prisms.lang.Type(f.getGenericType()), withValues ? f.get(null) : null);
				} catch(Exception e) {
					throw new EvaluationException("Could not access field " + theName + " on type "
						+ prisms.lang.Type.typeString(importType), e, this, getStored("name").index);
				}
			}
		}
		throw new EvaluationException(theName + " cannot be resolved to a " + (asType ? "type" : "variable"), this, getMatch().index);
	}

	@Override
	public EvaluationResult getValue(EvaluationEnvironment env, ParsedAssignmentOperator assign) throws EvaluationException {
		return evaluate(env, false, true);
	}

	@Override
	public void assign(EvaluationResult value, EvaluationEnvironment env, ParsedAssignmentOperator assign) throws EvaluationException {
		env.setVariable(theName, value.getValue(), assign, assign.getStored("name").index);
	}
}
