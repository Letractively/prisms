/*
 * ParsedArrayInitializer.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

import java.lang.reflect.Array;

import prisms.lang.EvaluationException;
import prisms.lang.PrismsEvaluator.EvalResult;

/** Represents the creation of an array instance */
public class ParsedArrayInitializer extends prisms.lang.ParsedItem
{
	private prisms.lang.ParsedItem theType;

	private int theDimension;

	private prisms.lang.ParsedItem[] theSizes;

	private prisms.lang.ParsedItem[] theElements;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParsedItem parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theType = parser.parseStructures(this, getStored("type"))[0];
		boolean hasEmptyDimension = false;
		boolean hasSize = false;
		boolean hasValues = getStored("valueSet") != null;
		java.util.ArrayList<prisms.lang.ParsedItem> sizes = new java.util.ArrayList<prisms.lang.ParsedItem>();
		java.util.ArrayList<prisms.lang.ParsedItem> elements = new java.util.ArrayList<prisms.lang.ParsedItem>();
		for(prisms.lang.ParseMatch m : match.getParsed())
		{
			if("startDimension".equals(m.config.get("storeAs")))
				theDimension++;
			else if("size".equals(m.config.get("storeAs")))
			{
				if(hasValues)
					throw new prisms.lang.ParseException(
						"Cannot define dimension expressions when an array initializer is provided",
						getRoot().getFullCommand(), m.index);
				if(hasEmptyDimension)
					throw new prisms.lang.ParseException(
						"Cannot specify an array dimension after an empty dimension", getRoot()
							.getFullCommand(), m.index);
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
		if(!hasValues && sizes.size() == 0)
			throw new prisms.lang.ParseException(
				"Must provide either dimension expressions or an array initializer", getRoot()
					.getFullCommand(), match.index);
		theSizes = sizes.toArray(new prisms.lang.ParsedItem [sizes.size()]);
		theElements = elements.toArray(new prisms.lang.ParsedItem [elements.size()]);
	}

	/** @return The base type of the array */
	public prisms.lang.ParsedItem getType()
	{
		return theType;
	}

	/** @return The dimension of the array */
	public int getDimension()
	{
		return theDimension;
	}

	/**
	 * @return The sizes that are specified for this array. There may be fewer sizes than
	 *         {@link #getDimension()}.
	 */
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
	public prisms.lang.EvaluationResult<Object> evaluate(prisms.lang.EvaluationEnvironment env,
		boolean asType, boolean withValues) throws EvaluationException
	{
		prisms.lang.EvaluationResult<?> typeEval = theType.evaluate(env, true, false);
		if(!typeEval.isType())
			throw new EvaluationException("Unrecognized type " + theType.getMatch().text, this,
				theType.getMatch().index);
		Class<?> type = typeEval.getType();
		for(int i = theDimension - 1; i >= 0; i--)
			type = Array.newInstance(type, 0).getClass();
		if(!withValues)
			return new prisms.lang.EvaluationResult<Object>(type, null);
		Object ret = null;
		if(theSizes.length > 0)
		{
			final int [] sizes = new int [theSizes.length];
			for(int i = 0; i < sizes.length; i++)
			{
				prisms.lang.EvaluationResult<?> sizeEval = theSizes[i].evaluate(env, false,
					withValues);
				if(sizeEval.isType() || sizeEval.getPackageName() != null)
					throw new EvaluationException(sizeEval.typeString()
						+ " cannot be resolved to a variable", this, theSizes[i].getMatch().index);
				if(!sizeEval.isIntType() || Long.TYPE.equals(sizeEval.getType()))
					throw new EvaluationException("Type mismatch: " + sizeEval.typeString()
						+ " cannot be cast to int", this, theSizes[i].getMatch().index);
				sizes[i] = ((Number) sizeEval.getValue()).intValue();
			}
			type = type.getComponentType();
			ret = Array.newInstance(type, sizes[0]);
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
			new ArrayFiller().fillArray(ret, type.getComponentType(), 1);
		}
		else
		{
			type = type.getComponentType();
			Object [] elements = new Object [theElements.length];
			for(int i = 0; i < elements.length; i++)
			{
				prisms.lang.EvaluationResult<?> elEval = theElements[i].evaluate(env, false,
					withValues);
				if(elEval.isType() || elEval.getPackageName() != null)
					throw new EvaluationException(elEval.typeString()
						+ " cannot be resolved to a variable", this, theSizes[i].getMatch().index);
				if(!prisms.lang.PrismsLangUtils.canAssign(type, elEval.getType()))
					throw new EvaluationException("Type mismatch: cannot convert from "
						+ elEval.typeString() + " to " + EvalResult.typeString(type), this,
						theElements[i].getMatch().index);
				elements[i] = elEval.getValue();
			}
			ret = Array.newInstance(type, elements.length);
			for(int i = 0; i < elements.length; i++)
				Array.set(ret, i, elements[i]);
		}

		return new prisms.lang.EvaluationResult<Object>(ret.getClass(), ret);
	}
}
