package prisms.lang;

import java.util.ArrayList;

/** Allows checking for classes that have not been loaded into the JVM yet */
public class ClassGetter {
	private static final Class<?> NONE = new Object() {
	}.getClass();

	private static final Class<?> HOLDER = new Object() {
	}.getClass();

	private java.util.Map<String, String> thePackageNames;

	private java.util.Map<String, Class<?>> theClassCache;

	private boolean hasScanned;

	/** Creates a class getter */
	public ClassGetter() {
		thePackageNames = new java.util.concurrent.ConcurrentHashMap<>();
		theClassCache = new java.util.concurrent.ConcurrentHashMap<>();
	}

	/**
	 * @param name The name of the class to get
	 * @return The class with the given name, or null if no such class exists in the this classloader
	 */
	public Class<?> getClass(String name) {
		Class<?> ret = theClassCache.get(name);
		if(ret == NONE)
			return null;
		else if(ret != null && ret != HOLDER)
			return ret;
		try {
			ret = Class.forName(name);
		} catch(ClassNotFoundException e) {
			ret = null;
		}
		if(ret != null)
			theClassCache.put(name, ret);
		else
			theClassCache.put(name, NONE);
		return ret;
	}

	/**
	 * @param name The name of the package to check
	 * @return Whether the given name is indeed a package available to this JVM
	 */
	public boolean isPackage(String name) {
		if(thePackageNames.containsKey(name))
			return true;
		String startName = name + '.';
		for(Package pkg : Package.getPackages())
			if(pkg.getName().equals(name) || pkg.getName().startsWith(startName)) {
				thePackageNames.put(name, name);
				return true;
			}
		if(hasScanned)
			return false;
		scanClassPath();
		if(thePackageNames.containsKey(name))
			return true;
		return false;
	}

	/**
	 * @param pkg The name of the package to get subpackages for
	 * @return All packages that are subpackages of the given package
	 */
	public String [] getSubPackages(String pkg) {
		scanClassPath();
		ArrayList<String> ret = new ArrayList<>();
		if(pkg == null) {
			for(String pn : thePackageNames.keySet())
				if(pn.indexOf('.') < 0)
					ret.add(pn);
		} else {
			pkg += ".";
			for(String pn : thePackageNames.keySet())
				if(pn.startsWith(pkg) && pn.lastIndexOf('.') == pkg.length() - 1)
					ret.add(pn);
		}
		return ret.toArray(new String[ret.size()]);
	}

	/**
	 * @param pkg The name of the package to get classes of. May also be a class name to get inner classes of.
	 * @return The names of all classes in the given package
	 */
	public String [] getClasses(String pkg) {
		scanClassPath();
		ArrayList<String> ret = new ArrayList<>();
		if(pkg == null) {
			for(String cn : theClassCache.keySet())
				if(cn.indexOf('.') < 0)
					ret.add(cn);
		} else {
			pkg += ".";
			for(String cn : theClassCache.keySet())
				if(cn.startsWith(pkg) && cn.lastIndexOf('.') == pkg.length() - 1)
					ret.add(cn);
		}
		return ret.toArray(new String[ret.size()]);
	}

	java.util.regex.Pattern fileNamePattern;

	private void scanClassPath() {
		if(hasScanned)
			return;

		synchronized(this) {
			if(hasScanned)
				return;
			fileNamePattern = java.util.regex.Pattern.compile("([_a-zA-Z][_a-zA-Z0-9]*\\$)*[_a-zA-Z][_a-zA-Z0-9]*\\.class");
			java.net.URL res = Class.class.getResource("Class.class");
			if(res.getProtocol().equals("file"))
				scan(new java.io.File(res.getPath()).getParentFile().getParentFile().getParentFile(), null);
			else if(res.getProtocol().equals("jar"))
				try {
					scan(new java.util.jar.JarFile(java.net.URLDecoder.decode(res.getPath().substring(5, res.getPath().indexOf('!')),
						"UTF8")));
				} catch(java.io.IOException e) {
					e.printStackTrace();
				}

			String cp = System.getProperty("java.class.path");
			for(String p : cp.split(";")) {
				if(p.endsWith(".jar"))
					try {
						scan(new java.util.jar.JarFile(p));
					} catch(java.io.IOException e) {
					}
				else
					scan(new java.io.File(p), null);
			}
			fileNamePattern = null;
			hasScanned = true;
		}
	}

	private void scan(java.io.File file, String pkg) {
		if(!file.exists() || !file.isDirectory())
			return;
		for(java.io.File f : file.listFiles()) {
			if(f.isDirectory())
				scan(f, pkg == null ? f.getName() : pkg + "." + f.getName());
			else if(fileNamePattern.matcher(f.getName()).matches()) {
				String cn = f.getName();
				cn = cn.substring(0, cn.lastIndexOf('.'));
				cn = prisms.util.PrismsUtils.replaceAll(cn, "$", ".");
				addClassName(pkg == null ? cn : pkg + "." + cn);
			}
		}
	}

	private void scan(java.util.jar.JarFile jar) {
		java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
		while(entries.hasMoreElements()) {
			java.util.jar.JarEntry entry = entries.nextElement();
			String name = entry.getName();
			String fileName;
			int idx = name.lastIndexOf('/');
			if(idx >= 0)
				fileName = name.substring(idx + 1);
			else
				fileName = name;
			if(fileNamePattern.matcher(fileName).matches()) {
				String className = fileName.substring(0, fileName.lastIndexOf('.'));
				className = prisms.util.PrismsUtils.replaceAll(className, "$", ".");
				if(idx >= 0)
					className = prisms.util.PrismsUtils.replaceAll(name.substring(0, idx), "/", ".") + "." + className;
				addClassName(className);
			}
		}
	}

	/** @param className The name of the class to add to this getter */
	public void addClassName(String className) {
		className = className.intern();
		if(theClassCache.containsKey(className))
			return;
		thePackageNames.remove(className);
		theClassCache.put(className, HOLDER);
		int idx = className.lastIndexOf('.');
		if(idx > 0)
			addPackage(className.substring(0, idx));
	}

	private void addPackage(String pkg) {
		pkg = pkg.intern();
		if(thePackageNames.containsKey(pkg) || theClassCache.containsKey(pkg))
			return;
		thePackageNames.put(pkg, pkg);
		int idx = pkg.lastIndexOf('.');
		if(idx > 0)
			addPackage(pkg.substring(0, idx));
	}
}
