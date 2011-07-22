/*
 * ConfigurableResource.java Created Jul 20, 2011 by Andrew Butler, PSL
 */
package prisms.util.persisters;

/**
 * A resource that is easily instantiated and configured from within the PRISMS framework. Resources
 * that implement this may be configured in XML instead of requiring extensions of
 * {@link prisms.arch.AppConfig} to instantiate, configure, and install them.
 */
public interface ConfigurableResource
{
	/**
	 * Configures this resource
	 * 
	 * @param config The configuration for the resource
	 * @param app The application that this resource is being instantiated for. If this resource is
	 *        a global resource (usable by more than one application), this method should only rely
	 *        on global properties in the application and its environment.
	 */
	void configure(prisms.arch.PrismsConfig config, prisms.arch.PrismsApplication app);
}
