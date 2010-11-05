/*
 * PrismsProperties.java Created Oct 26, 2010 by Andrew Butler, PSL
 */
package prisms.arch.event;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.User;

/** Properties not specific to an application implementation */
public class PrismsProperties
{
	/** All PRISMS users that can access the application */
	public static final PrismsProperty<User []> users = PrismsProperty.create("prisms/users",
		User [].class);

	/**
	 * All applications configured in PRISMS. This propery will only be available to the manager
	 * application.
	 */
	public static final PrismsProperty<PrismsApplication []> applications = PrismsProperty.create(
		"applications", PrismsApplication [].class);
}
