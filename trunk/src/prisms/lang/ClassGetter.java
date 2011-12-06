package prisms.lang;

/** Allows checking for classes that have not been loaded into the JVM yet */
public class ClassGetter
{
	private java.util.ArrayList<String> thePackageNames;

	private java.util.ArrayList<String> theClassNames;

	private boolean hasScanned;

	/** Creates a class getter */
	public ClassGetter()
	{
		thePackageNames = new java.util.ArrayList<String>();
		theClassNames = new java.util.ArrayList<String>();
	}

	/**
	 * @param name The name of the package to check
	 * @return Whether the given name is indeed a package available to this JVM
	 */
	public boolean isPackage(String name)
	{
		if(thePackageNames.contains(name))
			return true;
		for(Package pkg : Package.getPackages())
			if(pkg.getName().equals(name))
				return true;
		if(hasScanned)
			return false;
		scanClassPath();
		if(thePackageNames.contains(name))
			return true;
		return false;
	}

	/**
	 * @param pkg The name of the package to get subpackages for
	 * @return All packages that are subpackages of the given package
	 */
	public String [] getSubPackages(String pkg)
	{
		scanClassPath();
		java.util.ArrayList<String> ret = new java.util.ArrayList<String>();
		if(pkg == null)
		{
			for(String pn : thePackageNames)
				if(pn.indexOf('.') < 0)
					ret.add(pn);
		}
		else
		{
			pkg += ".";
			for(String pn : thePackageNames)
				if(pn.startsWith(pkg) && pn.lastIndexOf('.') == pkg.length() - 1)
					ret.add(pn);
		}
		return ret.toArray(new String [ret.size()]);
	}

	/**
	 * @param pkg The name of the package to get classes of. May also be a class name to get inner classes of.
	 * @return The names of all classes in the given package
	 */
	public String [] getClasses(String pkg)
	{
		scanClassPath();
		java.util.ArrayList<String> ret = new java.util.ArrayList<String>();
		if(pkg == null)
		{
			for(String cn : theClassNames)
				if(cn.indexOf('.') < 0)
					ret.add(cn);
		}
		else
		{
			pkg += ".";
			for(String cn : theClassNames)
				if(cn.startsWith(pkg) && cn.lastIndexOf('.') == pkg.length() - 1)
					ret.add(cn);
		}
		return ret.toArray(new String [ret.size()]);
	}

	java.util.regex.Pattern fileNamePattern;

	private void scanClassPath()
	{
		if(hasScanned)
			return;

		synchronized(this)
		{
			if(hasScanned)
				return;
			fileNamePattern = java.util.regex.Pattern
				.compile("([_a-zA-Z][_a-zA-Z0-9]*\\$)*[_a-zA-Z][_a-zA-Z0-9]*\\.class");
			java.net.URL res = Class.class.getResource("Class.class");
			if(res.getProtocol().equals("file"))
				scan(new java.io.File(res.getPath()).getParentFile().getParentFile().getParentFile(), null);
			else if(res.getProtocol().equals("jar"))
				try
				{
					scan(new java.util.jar.JarFile(java.net.URLDecoder.decode(
						res.getPath().substring(5, res.getPath().indexOf('!')), "UTF8")));
				} catch(java.io.IOException e)
				{
					e.printStackTrace();
				}

			String cp = System.getProperty("java.class.path");
			for(String p : cp.split(";"))
			{
				if(p.endsWith(".jar"))
					try
					{
						scan(new java.util.jar.JarFile(p));
					} catch(java.io.IOException e)
					{}
				else
					scan(new java.io.File(p), null);
			}
			fileNamePattern = null;
			hasScanned = true;
		}
	}

	private void scan(java.io.File file, String pkg)
	{
		if(!file.exists() || !file.isDirectory())
			return;
		for(java.io.File f : file.listFiles())
		{
			if(f.isDirectory())
				scan(f, pkg == null ? f.getName() : pkg + "." + f.getName());
			else if(fileNamePattern.matcher(f.getName()).matches())
			{
				String cn = f.getName();
				cn = cn.substring(0, cn.lastIndexOf('.'));
				cn = prisms.util.PrismsUtils.replaceAll(cn, "$", ".");
				addClassName(pkg == null ? cn : pkg + "." + cn);
			}
		}
	}

	private void scan(java.util.jar.JarFile jar)
	{
		java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
		while(entries.hasMoreElements())
		{
			java.util.jar.JarEntry entry = entries.nextElement();
			String name = entry.getName();
			String fileName;
			int idx = name.lastIndexOf('/');
			if(idx >= 0)
				fileName = name.substring(idx + 1);
			else
				fileName = name;
			if(fileNamePattern.matcher(fileName).matches())
			{
				String className = fileName.substring(0, fileName.lastIndexOf('.'));
				className = prisms.util.PrismsUtils.replaceAll(className, "$", ".");
				className = prisms.util.PrismsUtils.replaceAll(name.substring(0, idx), "/", ".") + "." + className;
				addClassName(className);
			}
		}
	}

	/** @param className The name of the class to add to this getter */
	public void addClassName(String className)
	{
		className = className.intern();
		if(theClassNames.contains(className))
			return;
		thePackageNames.remove(className);
		theClassNames.add(className);
		int idx = className.lastIndexOf('.');
		if(idx > 0)
			addPackage(className.substring(0, idx));
	}

	private void addPackage(String pkg)
	{
		pkg = pkg.intern();
		if(thePackageNames.contains(pkg) || theClassNames.contains(pkg))
			return;
		thePackageNames.add(pkg);
		int idx = pkg.lastIndexOf('.');
		if(idx > 0)
			addPackage(pkg.substring(0, idx));
	}
}
