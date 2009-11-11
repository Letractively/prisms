/**
 * DataEvent.java Created Oct 11, 2007 by Andrew Butler, PSL
 */
package prisms.ui;

/**
 * An event that may occur on a data structure
 */
public abstract class DataEvent
{
	/**
	 * The type of an event
	 */
	public static enum Type
	{
		/**
		 * An event for which a node was added
		 */
		ADD,
		/**
		 * An event for which a node was removed
		 */
		REMOVE,
		/**
		 * An event for which a node was moved
		 */
		MOVE,
		/**
		 * An event for which a node was changed
		 */
		CHANGE,
		/**
		 * An event for which the data structure should be completely refreshed
		 */
		REFRESH;
	}

	private final Type theType;

	/**
	 * Creates a DataEvent
	 * 
	 * @param type The type of the event
	 */
	public DataEvent(Type type)
	{
		theType = type;
	}

	/**
	 * @return This event's type
	 */
	public Type getType()
	{
		return theType;
	}
}
