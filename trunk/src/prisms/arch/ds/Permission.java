/**
 * Permission.java Created Jun 27, 2008 by Andrew Butler, PSL
 */
package prisms.arch.ds;

/**
 * Represents a permission in the PRISMS architecture
 */
public interface Permission
{
	/**
	 * @return The application that this permission applies to
	 */
	prisms.arch.PrismsApplication getApp();

	/**
	 * @return The name of this permission
	 */
	String getName();

	/**
	 * @return A description of what capability this permission conveys
	 */
	String getDescrip();
}
