/*
 * PrismsProperties.java Created Oct 26, 2010 by Andrew Butler, PSL
 */
package prisms.arch.event;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.User;

/** Properties not specific to an application implementation */
public class PrismsProperties
{
	/** The user preferences */
	public static final PrismsProperty<prisms.util.preferences.Preferences> preferences = PrismsProperty
		.create("prisms/preferences", prisms.util.preferences.Preferences.class);

	/** All PRISMS users that can access the application */
	public static final PrismsProperty<User []> users = PrismsProperty.create("prisms/users",
		User [].class);

	/** A synchronizer for PRISMS user data */
	public static final PrismsProperty<prisms.records.PrismsSynchronizer> synchronizer = PrismsProperty
		.create("prisms/synchronizer", prisms.records.PrismsSynchronizer.class);

	/**
	 * All applications configured in PRISMS. This propery will only be available to the manager
	 * application.
	 */
	public static final PrismsProperty<PrismsApplication []> applications = PrismsProperty.create(
		"applications", PrismsApplication [].class);
}
