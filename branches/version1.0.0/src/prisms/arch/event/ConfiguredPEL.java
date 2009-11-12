/**
 * ConfiguredPEL.java Created Dec 10, 2007 by Andrew Butler, PSL
 */
package prisms.arch.event;

import org.dom4j.Element;

import prisms.arch.AppConfig;
import prisms.arch.PrismsSession;

/**
 * A {@link PrismsEventListener} that can be configured using {@link AppConfig}. An event listener
 * that is configured from XML must generally be told what session it is in since the
 * {@link PrismsEventListener} interface assumes that it already knows.
 */
public interface ConfiguredPEL extends PrismsEventListener
{
	/**
	 * Sets this listener's session so it can perform its action properly
	 * 
	 * @param session The session that this listener is listening to
	 * @param configEl TODO
	 */
	void configure(PrismsSession session, Element configEl);
}
