/*
 * Governor.java Created Nov 1, 2010 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.User;

/**
 * Governs a set of items, determining what sessions should contain what items
 * 
 * @param <T> The type of item that is governed
 */
public interface Governor<T>
{
	/**
	 * Configures this governor
	 * 
	 * @param configEl The XML to use to configure this governor
	 */
	void configure(org.dom4j.Element configEl);

	/**
	 * @param item The item to test
	 * @return Whether the given item is to be shared between sessions
	 */
	boolean isShared(T item);

	/**
	 * @param app The application to test
	 * @param user The user to test
	 * @param item The item to test
	 * @return Whether the given item should be available to a session of the given application and
	 *         user
	 */
	boolean canView(PrismsApplication app, User user, T item);

	/**
	 * @param app The application to test
	 * @param user The user to test
	 * @param item The item to test
	 * @return Whether the given user is allowed to edit the given item from within the given
	 *         application
	 */
	boolean canEdit(PrismsApplication app, User user, T item);
}
