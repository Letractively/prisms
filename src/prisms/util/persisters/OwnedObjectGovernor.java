/*
 * OwnedObjectGovernor.java Created Nov 1, 2010 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.User;

/** Governs the distribution of OwnedObjects between sessions */
public class OwnedObjectGovernor implements Governor<OwnedObject>
{
	private String theViewAllPermission;

	private String theEditAllPermission;

	public void configure(prisms.arch.PrismsConfig config)
	{
		theViewAllPermission = config.get("view-all");
		theEditAllPermission = config.get("edit-all");
	}

	public boolean isShared(OwnedObject item)
	{
		return true;
	}

	public boolean canView(PrismsApplication app, User user, OwnedObject item)
	{
		if(user == null)
			return true;
		if(theViewAllPermission != null && user.getPermissions(app).has(theViewAllPermission))
			return true;
		if(theEditAllPermission != null && user.getPermissions(app).has(theEditAllPermission))
			return true;
		return item.isPublic() || user.equals(item.getOwner());
	}

	public boolean canEdit(PrismsApplication app, User user, OwnedObject item)
	{
		if(theEditAllPermission != null && user.getPermissions(app).has(theEditAllPermission))
			return true;
		return item.isPublic() || user.equals(item.getOwner());
	}
}
