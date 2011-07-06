/**
 * ManagerProperties.java Created Mar 11, 2009 by Andrew Butler, PSL
 */
package manager.app;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.User;
import prisms.arch.event.PrismsProperty;

/** The set of all properties available to the manager application */
public class ManagerProperties
{
	private ManagerProperties()
	{
	}

	/** The application that has been selected by the user to be edited */
	public static final PrismsProperty<PrismsApplication> selectedApp = PrismsProperty.create(
		"selectedApp", PrismsApplication.class);

	/** The client that has been selected by the user to be edited */
	public static final PrismsProperty<prisms.arch.ClientConfig> selectedClient = PrismsProperty
		.create("selectedClient", prisms.arch.ClientConfig.class);

	/** The group selected for configuration within the selected application */
	public static final PrismsProperty<prisms.arch.ds.UserGroup> selectedAppGroup = PrismsProperty
		.create("selectedAppGroup", prisms.arch.ds.UserGroup.class);

	/** The permission selected for configuration within the selected application */
	public static final PrismsProperty<prisms.arch.Permission> selectedAppPermission = PrismsProperty
		.create("selectedAppPermission", prisms.arch.Permission.class);

	/** The user that has been selected by the client user to be edited */
	public static final PrismsProperty<User> selectedUser = PrismsProperty.create("selectedUser",
		User.class);

	/** The application selected to configure access and permissions for the {@link #selectedUser} */
	public static final PrismsProperty<PrismsApplication> userApplication = PrismsProperty.create(
		"userApplication", PrismsApplication.class);

	/**
	 * The group selected to configure membership for the {@link #selectedUser} and
	 * {@link #userApplication}
	 */
	public static final PrismsProperty<prisms.arch.ds.UserGroup> userSelectedGroup = PrismsProperty
		.create("userSelectedGroup", prisms.arch.ds.UserGroup.class);

	/** The permission selected to view the characteristics of for the {@link #userSelectedGroup} */
	public static final PrismsProperty<prisms.arch.Permission> userSelectedPermission = PrismsProperty
		.create("userSelectedPermission", prisms.arch.Permission.class);

	/** The performance data selected by the user to view */
	public static final PrismsProperty<prisms.util.ProgramTracker> performanceData = PrismsProperty
		.create("manager/performanceData", prisms.util.ProgramTracker.class);
}
