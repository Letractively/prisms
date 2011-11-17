/*
 * ParsedArrayInitializer.java Created Nov 16, 2011 by Andrew Butler, PSL
 */
package prisms.lang.types;

/** Represents the creation of an array instance */
public class ParsedArrayInitializer extends prisms.lang.ParseStruct
{
	private prisms.lang.ParseStruct theType;

	private int theDimension;

	private prisms.lang.ParseStruct[] theSizes;

	private prisms.lang.ParseStruct[] theElements;

	@Override
	public void setup(prisms.lang.PrismsParser parser, prisms.lang.ParseStruct parent,
		prisms.lang.ParseMatch match, int start) throws prisms.lang.ParseException
	{
		super.setup(parser, parent, match, start);
		theType = parser.parseStructures(this, getStored("type"))[0];
		boolean hasEmptyDimension = false;
		boolean hasSize = false;
		boolean hasValues = getStored("valueSet") != null;
		java.util.ArrayList<prisms.lang.ParseStruct> sizes = new java.util.ArrayList<prisms.lang.ParseStruct>();
		java.util.ArrayList<prisms.lang.ParseStruct> elements = new java.util.ArrayList<prisms.lang.ParseStruct>();
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
		theSizes = sizes.toArray(new prisms.lang.ParseStruct [sizes.size()]);
		theElements = elements.toArray(new prisms.lang.ParseStruct [elements.size()]);
	}

	/** @return The base type of the array */
	public prisms.lang.ParseStruct getType()
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
	public prisms.lang.ParseStruct[] getSizes()
	{
		return theSizes;
	}

	/** @return The elements specified for this array */
	public prisms.lang.ParseStruct[] getElements()
	{
		return theElements;
	}
}
