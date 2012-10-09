/*
 * ParsedArrayInitializer.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import java.lang.reflect.Array;

import prisms.lang.EvaluationException;
import prisms.lang.ParsedItem;

/** Represents the creation of an array instance */
public class ParsedArrayInitializer extends prisms.lang.ParsedItem
{
	private ParsedType theType;

	private prisms.lang.ParsedItem[] theSizes;

	private prisms.lang.ParsedItem[] theElements;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent, prisms.lang.ParseMatch match)
		throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match);
		theType = (ParsedType) parser.parseStructures(this, getStored("type"))[0];
		boolean hasEmptyDimension = false;
		boolean hasSize = false;
		boolean hasValues = getStored("valueSet") != null;
		java.util.ArrayList<prisms.lang.ParsedItem> sizes = new java.util.ArrayList<prisms.lang.ParsedItem>();
		java.util.ArrayList<prisms.lang.ParsedItem> elements = new java.util.ArrayList<prisms.lang.ParsedItem>();
		for(prisms.lang.ParseMatch m : match.getParsed())
		{
			if("startDimension".equals(m.config.get("storeAs")))
				theType.addArrayDimension();
			else if("size".equals(m.config.get("storeAs")))
			{
				if(hasValues)
					throw new prisms.lang.ParseException(
						"Cannot define dimension expressions when an array initializer is provided", getRoot()
							.getFullCommand(), m.index);
				if(hasEmptyDimension)
					throw new prisms.lang.ParseException("Cannot specify an array dimension after an empty dimension",
						getRoot().getFullCommand(), m.index);
				hasSize = true;
				sizes.add(parser.parseStructures(this, m)[0]);
			}
			else if("endDimension".equals(m.config.get("storeAs")))
			{
				if(!hasSize)
					hasEmptyDimension = true;
			}
			else if("element".equals(m.config.get("storeAs")))
				elements.add(parser.parseStructures(this, m)[0]);
		}
		if(getMatch().isComplete() && !hasValues && sizes.size() == 0)
			throw new prisms.lang.ParseException("Must provide either dimension expressions or an array initializer",
				getRoot().getFullCommand(), match.index);
		if(elements.size() > 0 && theType.getArrayDimension() == 0)
			throw new prisms.lang.ParseException("Syntax error: array initializer or constructor expected", getRoot()
				.getFullCommand(), getStored("valueSet").index);
		theSizes = sizes.toArray(new prisms.lang.ParsedItem [sizes.size()]);
		theElements = elements.toArray(new prisms.lang.ParsedItem [elements.size()]);
	}

	/** @return The base type of the array */
	public ParsedType getType()
	{
		return theType;
	}

	/** @return The sizes that are specified for this array */
	public prisms.lang.ParsedItem[] getSizes()
	{
		return theSizes;
	}

	/** @return The elements specified for this array */
	public prisms.lang.ParsedItem[] getElements()
	{
		return theElements;
	}

	@Override
	public prisms.lang.ParsedItem[] getDependents()
	{
		prisms.lang.ParsedItem[] ret;
		if(theSizes.length > 0)
		{
			ret = new prisms.lang.ParsedItem [theSizes.length + 1];
			ret[0] = theType;
			System.arraycopy(theSizes, 0, ret, 1, theSizes.length);
		}
		else
		{
			ret = new prisms.lang.ParsedItem [theElements.length + 1];
			ret[0] = theType;
			System.arraycopy(theElements, 0, ret, 1, theElements.length);
		}
		return ret;
	}

	@Override
	public void replace(ParsedItem dependent, ParsedItem toReplace) throws IllegalArgumentException {
		for(int i = 0; i < theSizes.length; i++)
			if(theSizes[i] == dependent) {
				theSizes[i] = toReplace;
				return;
			}
		for(int i = 0; i < theElements.length; i++)
			if(theElements[i] == dependent) {
				theElements[i] = toReplace;
				return;
			}
		throw new IllegalArgumentException("No such dependent " + dependent);
	}

	@Override
	public prisms.lang.EvaluationResult evaluate(prisms.lang.EvaluationEnvironment env, boolean asType,
		boolean withValues) throws EvaluationException
	{
		prisms.lang.EvaluationResult typeEval = theType.evaluate(env, true, false);
		if(!typeEval.isType())
			throw new EvaluationException("Unrecognized type " + theType.getMatch().text, this,
				theType.getMatch().index);
		prisms.lang.Type type = typeEval.getType();
		if(type.getBaseType() == null)
		{}
		if(!withValues)
			return new prisms.lang.EvaluationResult(type, null);
		prisms.lang.Type retType = type;
		Object ret = null;
		if(theSizes.length > 0)
		{
			final int [] sizes = new int [theSizes.length];
			for(int i = 0; i < sizes.length; i++)
			{
				prisms.lang.EvaluationResult sizeEval = theSizes[i].evaluate(env, false, withValues);
				if(sizeEval.isType() || sizeEval.getPackageName() != null)
					throw new EvaluationException(sizeEval.typeString() + " cannot be resolved to a variable", this,
						theSizes[i].getMatch().index);
				if(!sizeEval.isIntType() || Long.TYPE.equals(sizeEval.getType().getBaseType()))
					throw new EvaluationException("Type mismatch: " + sizeEval.typeString() + " cannot be cast to int",
						this, theSizes[i].getMatch().index);
				sizes[i] = ((Number) sizeEval.getValue()).intValue();
			}
			type = type.getComponentType();
			ret = Array.newInstance(type.getBaseType(), sizes[0]);
			class ArrayFiller
			{
				public void fillArray(Object array, Class<?> componentType, int dimIdx)
				{
					if(dimIdx == sizes.length)
						return;
					int len = Array.getLength(array);
					for(int i = 0; i < len; i++)
					{
						Object element = Array.newInstance(componentType, sizes[dimIdx]);
						Array.set(array, i, element);
						fillArray(element, componentType.getComponentType(), dimIdx);
					}
				}
			}
			new ArrayFiller().fillArray(ret, type.getComponentType().getBaseType(), 1);
		}
		else
		{
			type = type.getComponentType();
			Object [] elements = new Object [theElements.length];
			for(int i = 0; i < elements.length; i++)
			{
				prisms.lang.EvaluationResult elEval = theElements[i].evaluate(env, false, withValues);
				if(elEval.isType() || elEval.getPackageName() != null)
					throw new EvaluationException(elEval.typeString() + " cannot be resolved to a variable", this,
						theSizes[i].getMatch().index);
				if(!type.isAssignable(elEval.getType()))
					throw new EvaluationException("Type mismatch: cannot convert from " + elEval.typeString() + " to "
						+ type, this, theElements[i].getMatch().index);
				elements[i] = elEval.getValue();
			}
			ret = Array.newInstance(type.getBaseType(), elements.length);
			for(int i = 0; i < elements.length; i++)
				Array.set(ret, i, elements[i]);
		}

		return new prisms.lang.EvaluationResult(retType, ret);
	}

	@Override
	public String toString()
	{
		StringBuilder ret = new StringBuilder();
		ret.append("new ").append(theType);
		for(prisms.lang.ParsedItem size : theSizes)
			ret.append('[').append(size).append(']');
		if(theElements.length > 0)
		{
			ret.append('{');
			for(int i = 0; i < theElements.length; i++)
			{
				if(i > 0)
					ret.append(", ");
				ret.append(theElements[i]);
			}
			ret.append('}');
		}
		return ret.toString();
	}
}
