/*
 * SharedObjectGovernor.java Created Nov 1, 2010 by Andrew Butler, PSL
 */
package prisms.util.persisters;

import org.dom4j.Element;

import prisms.arch.PrismsApplication;
import prisms.arch.ds.User;

/** Governs the distribution of SharedObjects between sessions */
public class SharedObjectGovernor implements Governor<SharedObject>
{
	private String theViewAllPermission;

	private String theEditAllPermission;

	public void configure(Element configEl)
	{
		theViewAllPermission = configEl.elementTextTrim("view-all");
		theEditAllPermission = configEl.elementTextTrim("edit-all");
	}

	public boolean isShared(SharedObject item)
	{
		return item.getShareKey().isShared();
	}

	public boolean canView(PrismsApplication app, User user, SharedObject item)
	{
		if(user == null)
			return true;
		if(theViewAllPermission != null && user.getPermissions(app).has(theViewAllPermission))
			return true;
		if(theEditAllPermission != null && user.getPermissions(app).has(theEditAllPermission))
			return true;
		return item.getShareKey().canView(user);
	}

	public boolean canEdit(PrismsApplication app, User user, SharedObject item)
	{
		if(theEditAllPermission != null && user.getPermissions(app).has(theEditAllPermission))
			return true;
		return item.getShareKey().canEdit(user);
	}
}
