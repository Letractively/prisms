/* ParsedConstructor.java Created Nov 15, 2011 by Andrew Butler, PSL */
package prisms.lang.types;

import java.lang.reflect.Method;

import prisms.lang.EvaluationException;
import prisms.lang.ParsedItem;
import prisms.lang.Type;

/** Represents a constructor or anonymous class declaration */
public class ParsedConstructor extends ParsedItem {
	private ParsedType theType;

	private ParsedItem [] theArguments;

	private boolean isAnonymous;

	private ParsedStatementBlock theInstanceInitializer;

	private ParsedStatementBlock theFields;

	private ParsedFunctionDeclaration [] theMethods;

	@Override
	public void setup(prisms.lang.PrismsParser parser, ParsedItem parent, prisms.lang.ParseMatch match) throws prisms.lang.ParseException {
		super.setup(parser, parent, match);
		theType = (ParsedType) parser.parseStructures(this, getStored("type"))[0];
		if(theType.isBounded())
			throw new prisms.lang.ParseException("Cannot instantiate a generic type", getRoot().getFullCommand(), theType.getMatch().index);
		isAnonymous = getStored("anonymous") != null;
		theInstanceInitializer = (ParsedStatementBlock) parser.parseStructures(this, getStored("instanceInitializer"))[0];
		theArguments = parser.parseStructures(this, getAllStored("argument"));

		if(isAnonymous) {
			theFields = new ParsedStatementBlock(parser, this, getMatch(), parser.parseStructures(this, getAllStored("field")));
			for(ParsedItem field : theFields.getContents()) {
				if(field instanceof ParsedDeclaration) {
				} else if(field instanceof ParsedAssignmentOperator) {
					ParsedAssignmentOperator assign = (ParsedAssignmentOperator) field;
					if(!assign.getName().equals("=") || !(assign.getVariable() instanceof ParsedDeclaration))
						throw new prisms.lang.ParseException(
							"Fields in an anonymous class must be declarations or assignments of declarations", getRoot().getFullCommand(),
							field.getMatch().index);
				} else
					throw new prisms.lang.ParseException("Unrecognized field statement", getRoot().getFullCommand(), field.getMatch().index);
			}
			ParsedItem [] methods = parser.parseStructures(this, getAllStored("method"));
			theMethods = new ParsedFunctionDeclaration[methods.length];
			System.arraycopy(methods, 0, theMethods, 0, methods.length);
		}
	}

	/** @return The type that this constructor is to instantiate */
	public ParsedType getType() {
		return theType;
	}

	/** @return The arguments to this constructor */
	public ParsedItem [] getArguments() {
		return theArguments;
	}

	/** @return Whether this constructor is an anonymous class declaration */
	public boolean isAnonymous() {
		return isAnonymous;
	}

	/** @return The instance initializer of this anonymous class, if there is one */
	public ParsedStatementBlock getInstanceInitializer() {
		return theInstanceInitializer;
	}

	/** @return The statements that declare the fields for this anonymous class */
	public ParsedStatementBlock getFields() {
		return theFields;
	}

	/** @return The methods declared by this anonymous class */
	public ParsedFunctionDeclaration [] getMethods() {
		return theMethods;
	}

	@Override
	public ParsedItem [] getDependents() {
		java.util.ArrayList<ParsedItem> ret = new java.util.ArrayList<ParsedItem>();
		ret.add(theType);
		for(ParsedItem arg : theArguments)
			ret.add(arg);
		if(theInstanceInitializer != null)
			ret.add(theInstanceInitializer);
		if(theFields != null)
			for(ParsedItem item : theFields.getContents())
				ret.add(item);
		if(theMethods != null)
			for(ParsedFunctionDeclaration f : theMethods)
				ret.add(f);
		return prisms.util.ArrayUtils.add(theArguments, theType, 0);
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException {
		if(theType == dependent) {
			if(toReplace instanceof ParsedType)
				theType = (ParsedType) toReplace;
			else
				throw new IllegalArgumentException("Cannot replace the type of a constructor with " + toReplace.getClass().getSimpleName());
		} else {
			for(int i = 0; i < theArguments.length; i++)
				if(theArguments[i] == dependent) {
					theArguments[i] = toReplace;
					return;
				}
			if(theInstanceInitializer != null && theInstanceInitializer == dependent) {
				if(toReplace instanceof ParsedStatementBlock) {
					theInstanceInitializer = (ParsedStatementBlock) toReplace;
					return;
				} else
					throw new IllegalArgumentException("Cannot replace the initializer block of a constructor with "
						+ toReplace.getClass().getSimpleName());
			}
			if(theFields != null)
				for(int i = 0; i < theFields.getContents().length; i++)
					if(theFields.getContents()[i] == dependent) {
						theFields.replace(dependent, toReplace);
						return;
					}
			if(theMethods != null)
				for(int i = 0; i < theMethods.length; i++)
					if(theMethods[i] == dependent) {
						if(toReplace instanceof ParsedFunctionDeclaration) {
							theMethods[i] = (ParsedFunctionDeclaration) toReplace;
							return;
						} else
							throw new IllegalArgumentException("Cannot replace a method of a constructor (anonymous inner class) with "
								+ toReplace.getClass().getSimpleName());
					}
			throw new IllegalArgumentException("No such dependent " + dependent);
		}
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder();
		ret.append("new ").append(theType.toString()).append('(');
		boolean first = true;
		for(ParsedItem arg : theArguments) {
			if(first)
				first = false;
			else
				ret.append(", ");
			ret.append(arg.toString());
		}
		ret.append(')');
		return ret.toString();
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType, boolean withValues)
		throws EvaluationException {
		prisms.lang.EvaluationResult type = theType.evaluate(env, true, false);
		if(!type.isType())
			throw new EvaluationException(type.typeString() + " cannot be resolved to a type", this, theType.getMatch().index);
		if(!isAnonymous)
			return evaluateConstructor(env, type.getType(), withValues);
		else
			return evaluateAnonymous(env, type.getType(), withValues);
	}

	@SuppressWarnings("rawtypes")
	public java.lang.reflect.Constructor<?> getTarget(Type type, Type [] argTypes, boolean publicOnly) throws EvaluationException {
		java.lang.reflect.Constructor [] constructors;
		if(publicOnly)
			constructors = type.getBaseType().getConstructors();
		else
			constructors = type.getBaseType().getDeclaredConstructors();

		java.lang.reflect.Constructor goodTarget = null;
		java.lang.reflect.Constructor badTarget = null;
		for(java.lang.reflect.Constructor c : constructors) {
			java.lang.reflect.Type [] _paramTypes = c.getGenericParameterTypes();
			Type [] paramTypes = new Type[_paramTypes.length];
			for(int p = 0; p < paramTypes.length; p++)
				paramTypes[p] = type.resolve(_paramTypes[p], c.getDeclaringClass(), null, c.getGenericParameterTypes(), argTypes);
			if(paramTypes.length > argTypes.length + 1)
				continue;
			boolean bad = false;
			int p;
			for(p = 0; !bad && p < paramTypes.length - 1; p++) {
				if(!paramTypes[p].isAssignable(argTypes[p]))
					bad = true;
			}
			if(bad) {
				if(badTarget == null)
					badTarget = c;
				continue;
			}
			java.lang.reflect.Constructor target = null;
			if(paramTypes.length == argTypes.length && (paramTypes.length == 0 || paramTypes[p].isAssignable(argTypes[p])))
				target = c;
			else if(c.isVarArgs()) {
				Type varArgType = paramTypes[paramTypes.length - 1].getComponentType();
				for(; !bad && p < argTypes.length; p++)
					if(!varArgType.isAssignable(argTypes[p]))
						bad = true;
				if(!bad)
					target = c;
			}
			if(target == null) {
				if(badTarget == null)
					badTarget = c;
				continue;
			}
			if(publicOnly && (target.getModifiers() & java.lang.reflect.Modifier.PUBLIC) == 0)
				badTarget = target;
			else {
				goodTarget = target;
				break;
			}
		}
		if(goodTarget != null)
			return goodTarget;
		else if(badTarget != null) {
			StringBuilder msg = new StringBuilder();
			msg.append("new ").append(type.getBaseType().getName()).append('(');
			Class<?> [] paramTypes = badTarget.getParameterTypes();
			int p;
			for(p = 0; p < paramTypes.length - 1; p++) {
				msg.append(prisms.lang.Type.typeString(paramTypes[p]));
				msg.append(", ");
			}
			if(badTarget.isVarArgs())
				msg.append(prisms.lang.Type.typeString(paramTypes[p].getComponentType())).append("...");
			else
				msg.append(prisms.lang.Type.typeString(paramTypes[p]));
			msg.append(')');
			if(publicOnly && (badTarget.getModifiers() & java.lang.reflect.Modifier.PUBLIC) == 0)
				throw new EvaluationException("The constructor " + msg + " is not visible", this, getStored("type").index);
			StringBuilder types = new StringBuilder();
			for(p = 0; p < argTypes.length; p++) {
				if(p > 0)
					types.append(", ");
				types.append(argTypes[p]);
			}
			throw new EvaluationException("The constructor " + msg + " is undefined for parameter types " + types, this,
				getStored("type").index);
		} else {
			StringBuilder msg = new StringBuilder();
			msg.append("new ").append(type.getBaseType().getName()).append('(');
			int p;
			for(p = 0; p < argTypes.length; p++) {
				if(p > 0)
					msg.append(", ");
				msg.append(argTypes[p]);
			}
			msg.append(')');
			throw new EvaluationException("The constructor " + msg + " is undefined", this, getStored("type").index);
		}
	}

	prisms.lang.EvaluationResult evaluateConstructor(prisms.lang.EvaluationEnvironment env, Type type, boolean withValues)
		throws EvaluationException {
		prisms.lang.EvaluationResult [] argRes = new prisms.lang.EvaluationResult[theArguments.length];
		Type [] argTypes = new Type[theArguments.length];
		for(int i = 0; i < argRes.length; i++) {
			argRes[i] = theArguments[i].evaluate(env, false, withValues);
			argTypes[i] = argRes[i].getType();
		}

		java.lang.reflect.Constructor<?> goodTarget = getTarget(type, argTypes, env.usePublicOnly());
		for(java.lang.reflect.Type c : goodTarget.getGenericExceptionTypes()) {
			Type ct = new Type(c);
			if(!env.canHandle(ct))
				throw new EvaluationException("Unhandled exception type " + ct, this, getMatch().index);
		}
		Class<?> [] paramTypes = goodTarget.getParameterTypes();
		Object [] args = new Object[paramTypes.length];
		for(int i = 0; i < args.length - 1; i++)
			args[i] = argRes[i].getValue();
		if(!goodTarget.isVarArgs()) {
			if(args.length > 0)
				args[args.length - 1] = argRes[args.length - 1].getValue();
		} else {
			Object varArgs = java.lang.reflect.Array.newInstance(paramTypes[args.length - 1].getComponentType(), theArguments.length
				- paramTypes.length + 1);
			args[args.length - 1] = varArgs;
			for(int i = paramTypes.length - 1; i < theArguments.length; i++)
				java.lang.reflect.Array.set(varArgs, i - paramTypes.length + 1, argRes[i].getValue());
		}
		try {
			return new prisms.lang.EvaluationResult(type, withValues ? goodTarget.newInstance(args) : null);
		} catch(java.lang.reflect.InvocationTargetException e) {
			throw new prisms.lang.ExecutionException(new Type(e.getCause().getClass()), e.getCause(), this, getStored("type").index);
		} catch(Exception e) {
			throw new EvaluationException("Could not invoke constructor of class " + type.getBaseType().getName(), e, this,
				getStored("type").index);
		}
	}

	prisms.lang.EvaluationResult evaluateAnonymous(prisms.lang.EvaluationEnvironment env, Type type, boolean withValues)
		throws EvaluationException {
		if(java.lang.reflect.Modifier.isFinal(type.getBaseType().getModifiers()))
			throw new EvaluationException("An anonymous class cannot subclass the final class " + type.getBaseType().getName(), this,
				theType.getMatch().index);
		if(!type.getBaseType().isInterface())
			throw new EvaluationException("Only interfaces can be implemented by anonymous" + " classes in this interpreter", this,
				theType.getMatch().index);
		prisms.lang.EvaluationEnvironment instanceScope = env.scope(false);
		for(ParsedItem field : theFields.getContents()) {
			field.evaluate(instanceScope, false, withValues);
			if(field instanceof ParsedDeclaration) {
				String name = ((ParsedDeclaration) field).getName();
				instanceScope.setVariable(name, getDefaultValue(instanceScope.getVariableType(name).getBaseType()), this,
					field.getMatch().index);
			}
		}
		for(java.lang.reflect.Method m : type.getBaseType().getMethods()) {
			boolean found = false;
			for(ParsedFunctionDeclaration f : theMethods) {
				if(!f.getName().equals(m.getName()) || f.getParameters().length != m.getParameterTypes().length)
					continue;
				int p;
				for(p = 0; p < f.getParameters().length; p++)
					if(!f.getParameters()[p]
						.getType()
						.evaluate(env, true, false)
						.getType()
						.isAssignable(
							type.resolve(m.getGenericParameterTypes()[p], m.getDeclaringClass(), null, new java.lang.reflect.Type[0],
								new Type[0])))
						break;
				if(p == f.getParameters().length)
					found = true;
				else
					continue;
				Type rt = type.resolve(m.getReturnType(), m.getDeclaringClass(), null, new java.lang.reflect.Type[0], new Type[0]);
				if(!rt.isAssignable(f.getReturnType().evaluate(env, true, false).getType())) {
					StringBuilder msg = new StringBuilder();
					msg.append("Return type").append(f.getReturnType()).append(" not compatible with return type ").append(rt)
					.append(" of method ");
					msg.append(m.getDeclaringClass().getName()).append(m.getName()).append('(');
					for(int p2 = 0; p2 < m.getParameterTypes().length; p2++) {
						if(p2 > 0)
							msg.append(", ");
						msg.append(type.resolve(m.getGenericParameterTypes()[p2], m.getDeclaringClass(), null,
							new java.lang.reflect.Type[0], new Type[0]));
					}
					msg.append(')');
					throw new EvaluationException(msg.toString(), this, f.getMatch().index);
				}
				for(ParsedType exType : f.getExceptionTypes()) {
					Type t = exType.evaluate(env, true, false).getType();
					if(t.canAssignTo(RuntimeException.class) || t.canAssignTo(Error.class))
						continue;
					boolean exOk = false;
					for(java.lang.reflect.Type met : m.getGenericExceptionTypes())
						if(type.resolve(met, m.getDeclaringClass(), null, new java.lang.reflect.Type[0], new Type[0]).isAssignable(t)) {
							exOk = true;
							break;
						}
					if(!exOk) {
						StringBuilder msg = new StringBuilder();
						msg.append("Declared throwable type ").append(t).append(" not declared to be thrown by method ");
						msg.append(m.getDeclaringClass().getName()).append(m.getName()).append('(');
						for(int p2 = 0; p2 < m.getParameterTypes().length; p2++) {
							if(p2 > 0)
								msg.append(", ");
							msg.append(type.resolve(m.getGenericParameterTypes()[p2], m.getDeclaringClass(), null,
								new java.lang.reflect.Type[0], new Type[0]));
						}
						msg.append(')');
						throw new EvaluationException(msg.toString(), this, f.getMatch().index);
					}
				}
			}
			if(!found) {
				StringBuilder msg = new StringBuilder();
				msg.append("Anonymous class implementing ").append(theType).append(" must implement the abstract method ");
				msg.append(m.getName()).append('(');
				for(int p = 0; p < m.getParameterTypes().length; p++) {
					if(p > 0)
						msg.append(", ");
					msg.append(type.resolve(m.getGenericParameterTypes()[p], m.getDeclaringClass(), null, new java.lang.reflect.Type[0],
						new Type[0]));
				}
				msg.append(')');
				throw new EvaluationException(msg.toString(), this, theType.getMatch().index);
			}
		}
		if(theInstanceInitializer != null)
			theInstanceInitializer.evaluate(instanceScope, false, withValues);

		for(ParsedFunctionDeclaration method : theMethods)
			method.evaluate(instanceScope, false, withValues);

		Object proxy = null;
		if(withValues)
			proxy = java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {type.getBaseType()},
				new AnonymousClassHandler(type, instanceScope));
		return new prisms.lang.EvaluationResult(type, proxy);
	}

	/**
	 * @param type The type to get the default value for
	 * @return The value that is initialized in a field by default if its value is not initialized by the constructor
	 */
	public static Object getDefaultValue(Class<?> type) {
		if(Boolean.TYPE.equals(type))
			return Boolean.FALSE;
		else if(Character.TYPE.equals(type))
			return Character.valueOf((char) 0);
		else if(Float.TYPE.equals(type))
			return Float.valueOf(0);
		else if(Double.TYPE.equals(type))
			return Double.valueOf(0);
		else if(Long.TYPE.equals(type))
			return Long.valueOf(0);
		else if(Long.TYPE.equals(type))
			return Long.valueOf(0);
		else if(Long.TYPE.equals(type))
			return Long.valueOf(0);
		else if(Long.TYPE.equals(type))
			return Long.valueOf(0);
		else
			return null;
	}

	/** A proxy handler for implementing interfaces with anonymous classes */
	public class AnonymousClassHandler implements java.lang.reflect.InvocationHandler {
		private final Type theImplType;

		private final prisms.lang.EvaluationEnvironment theEnv;

		/**
		 * @param implType The interface type being implemented
		 * @param env The evaluation environment representing this instance's state
		 */
		public AnonymousClassHandler(Type implType, prisms.lang.EvaluationEnvironment env) {
			theImplType = implType;
			theEnv = env;
		}

		/** @return The evaluation environment representing this instance's state */
		public prisms.lang.EvaluationEnvironment getEnv() {
			return theEnv;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object [] args) throws Throwable {
			for(ParsedFunctionDeclaration m : getMethods()) {
				if(!m.getName().equals(method.getName()) || m.getParameters().length != method.getParameterTypes().length)
					continue;
				prisms.lang.EvaluationResult [] argRes = new prisms.lang.EvaluationResult[m.getParameters().length];
				int p;
				for(p = 0; p < m.getParameters().length; p++) {
					Type argType = theImplType.resolve(method.getGenericParameterTypes()[p], method.getDeclaringClass(), null,
						new java.lang.reflect.Type[0], new Type[0]);
					if(!m.getParameters()[p].evaluateType(theEnv).isAssignable(argType))
						break;
					argRes[p] = new prisms.lang.EvaluationResult(argType, args[p]);
				}
				if(p < m.getParameters().length)
					continue;
				return m.execute(theEnv, argRes, true).getValue();
			}
			if(method.getDeclaringClass().equals(Object.class)) {
				if("toString".equals(method.getName()))
					return theImplType.getName() + "$JITRAnon@" + Integer.toHexString(System.identityHashCode(proxy));
				else if("equals".equals(method.getName()))
					return Boolean.valueOf(proxy == args[0]);
				else if("hashCode".equals(method.getName()))
					return Integer.valueOf(System.identityHashCode(proxy));
			}
			throw new IllegalStateException("Unsupported method! " + method);
		}
	}
}
