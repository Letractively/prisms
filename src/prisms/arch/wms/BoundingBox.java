package prisms.arch.wms;

/** Represents a WMS bounding box */
public class BoundingBox
{
	/** The minimum latitude for the bounding box */
	public final float minLat;

	/** The maximum latitude for the bounding box */
	public final float maxLat;

	/** The minimum longitude for the bounding box */
	public final float minLon;

	/** The maximum longitude for the bounding box */
	public final float maxLon;

	/**
	 * @param aMinLat The minimum latitude for the box
	 * @param aMaxLat The maximum latitude for the box
	 * @param aMinLon The minimum longitude for the box
	 * @param aMaxLon The maximum longitude for the box
	 */
	public BoundingBox(float aMinLat, float aMaxLat, float aMinLon, float aMaxLon)
	{
		minLat = aMinLat;
		maxLat = aMaxLat;
		minLon = aMinLon;
		maxLon = aMaxLon;
	}

	/** @return The center latitude for this bounding box */
	public float getCenterLat()
	{
		return (minLat + maxLat) / 2;
	}

	/** @return The center longitude for this bounding box */
	public float getCenterLon()
	{
		return (minLon + maxLon) / 2;
	}

	/** @return The latitude range for this bounding box */
	public float getLatRange()
	{
		return maxLat - minLat;
	}

	/** @return The longitude range for this bounding box */
	public float getLonRange()
	{
		return maxLon - minLon;
	}
}