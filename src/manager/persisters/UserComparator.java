/*
 * UserComparator.java Created Nov 11, 2010 by Andrew Butler, PSL
 */
package manager.persisters;

import prisms.arch.ds.User;

/** Sorts users by their name */
public class UserComparator implements java.util.Comparator<User>
{
	public int compare(User o1, User o2)
	{
		int ret = o1.getName().compareToIgnoreCase(o2.getName());
		if(ret == 0)
			ret = o1.getID() > o2.getID() ? 1 : o1.getID() < o2.getID() ? -1 : 0;
		return ret;
	}
}
