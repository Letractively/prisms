/**
 * ArrayInitializer.java Created Mar 11, 2009 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * Initializes an array to length 0 if it is null
 */
public class ArrayPropertyInitializer implements prisms.arch.event.SessionMonitor
{
	/**
	 * @see prisms.arch.event.SessionMonitor#register(prisms.arch.PrismsSession, org.dom4j.Element)
	 */
	public void register(prisms.arch.PrismsSession session, org.dom4j.Element configEl)
	{
		prisms.arch.event.PrismsProperty<Object []> prop = prisms.arch.event.PrismsProperty.get(configEl
			.elementText("property"), Object [].class);
		if(session.getProperty(prop) == null)
			session.setProperty(prop, (Object []) java.lang.reflect.Array.newInstance(prop
				.getType().getComponentType(), 0));
	}
}
