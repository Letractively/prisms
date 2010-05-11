/**
 * LinkHelper.java Created Jul 7, 2008 by Andrew Butler, PSL
 */
package prisms.util;

//import org.apache.log4j.Logger;

//import prisms.arch.PrismsApplication;
//import prisms.arch.PrismsProperty;

/**
 * Designed to help link properties after depersisting them from storage
 */
//This class is not yet used and doesn't work since I made the properties typed
//public class LinkHelper
//{
//	private static final Logger log = Logger.getLogger(LinkHelper.class);
//
//	private static class LinkHelperEntry
//	{
//		String [] getters;
//
//		PrismsProperty<?> [] linkProperty;
//
//		String [] setters;
//
//		String [] compareFields;
//	}
//
//	/**
//	 * A type that describes the policy to be taken by this LinkHelper when an item cannot be linked
//	 */
//	public static enum NoLinkPolicy
//	{
//		/**
//		 * The policy of throwing an exception when an item cannot be linked
//		 */
//		THROW,
//		/**
//		 * The policy of removing the item when it cannot be linked
//		 */
//		TRIM,
//		/**
//		 * The policy of logging a warning when an item cannot be linked
//		 */
//		WARN
//	}
//
//	private java.util.List<LinkHelperEntry> theEntries;
//
//	private NoLinkPolicy theNoLinkPolicy;
//
//	/**
//	 * Creates a LinkHelper
//	 */
//	public LinkHelper()
//	{
//		theEntries = new java.util.ArrayList<LinkHelperEntry>();
//		theNoLinkPolicy = NoLinkPolicy.TRIM;
//	}
//
//	/**
//	 * Configures this LinkHelper
//	 * 
//	 * @param configEl The XML configuration element
//	 */
//	public void configure(org.dom4j.Element configEl)
//	{
//		String nlp = configEl.elementTextTrim("nolinkpolicy");
//		if(nlp != null)
//			setNoLinkPolicy(NoLinkPolicy.valueOf(nlp));
//		java.util.Iterator<org.dom4j.Element> entryIter = configEl.elementIterator("entry");
//		while(entryIter.hasNext())
//		{
//			org.dom4j.Element entryEl = entryIter.next();
//			String [] getters = entryEl.elementTextTrim("getters").split("/");
//			String [] linkProperty = entryEl.elementTextTrim("property").split("/");
//			String [] linkTypes = entryEl.elementTextTrim("propertyTypes").split("/");
//			String [] setters = entryEl.elementTextTrim("setters").split("/");
//			String [] compareFields = entryEl.elementTextTrim("comparefields").split(",");
//			for(int c = 0; c < compareFields.length; c++)
//				compareFields[c] = compareFields[c].trim();
//			PrismsProperty<?> [] props = new PrismsProperty [linkProperty.length];
//			for(int p = 0; p < props.length; p++)
//			{
//				try
//				{
//					props[p] = PrismsProperty.get(linkProperty[p], Class.forName(linkTypes[p]));
//				} catch(Exception e)
//				{
//					throw new IllegalArgumentException("Could not configure link helper"
//						+ "--properties configured incorrectly", e);
//				}
//			}
//			addEntry(getters, props, setters, compareFields);
//		}
//	}
//
//	/**
//	 * @param policy The NoLinkPolicy for this LinkHelper
//	 */
//	public void setNoLinkPolicy(NoLinkPolicy policy)
//	{
//		theNoLinkPolicy = policy;
//	}
//
//	/**
//	 * Adds an entry of a property to register for linkage. The <code>getters</code> and
//	 * <code>setters</code> parameters here can be viewed as paths to the target property. This
//	 * LinkHelper will use the first getter on the object to be linked, then the second getter on
//	 * that, and so on until the final specified getter is used. That object will be substituted for
//	 * a matching object from one of the specified <code>properties</code> in the application. The
//	 * last setter will be used to set this new object into its parent, then the second to last
//	 * setter will set the parent into the grandparent, and so on back to the original object. The
//	 * <code>compareFields</code> parameter will be used to compare two objects while attempting a
//	 * match.
//	 * 
//	 * @param properties The properties to look for in the application
//	 * @param getters The getters for to use for each property in looking for matching values
//	 * @param setters The setters
//	 * @param compareFields
//	 */
//	public void addEntry(PrismsProperty<?> [] properties, String [] getters, String [] setters,
//		String [] compareFields)
//	{
//		LinkHelperEntry entry = new LinkHelperEntry();
//		entry.linkProperty = properties;
//		entry.getters = getters;
//		entry.setters = setters;
//		entry.compareFields = compareFields;
//		theEntries.add(entry);
//	}
//
//	/**
//	 * Attempts to link a depersisted object up with other depersisted properties
//	 * 
//	 * @param o The object to link
//	 * @param app The application to get properties from
//	 * @return The linked object
//	 */
//	public Object link(Object o, PrismsApplication app)
//	{
//		for(LinkHelperEntry entry : theEntries)
//		{
//			Object property = app.getGlobalProperty(entry.linkProperty[0]);
//			try
//			{
//				o = linkEntry(o, property, entry.getters, entry.linkProperty, entry.setters, 0,
//					entry.compareFields);
//			} catch(IllegalStateException e)
//			{
//				log.error("Property link error", e);
//			} catch(IllegalArgumentException e)
//			{
//				switch(theNoLinkPolicy)
//				{
//				case THROW:
//					throw e;
//				case TRIM:
//					return null;
//				case WARN:
//					log.warn("Property link error", e);
//					break;
//				}
//			}
//		}
//		return o;
//	}
//
//	public Object linkEntry(Object o, Object property, String [] getters,
//		PrismsProperty<?> [] linkProperty, String [] setters, int getterIdx, String [] compareFields)
//	{
//		if(o == null && getters.length > getterIdx)
//			return null;
//		if(o instanceof Object [])
//		{
//			Object [] o1 = (Object []) o;
//			for(int i = 0; i < o1.length; i++)
//			{
//				try
//				{
//					o1[i] = linkEntry(o1[i], property, getters, linkProperty, setters, getterIdx,
//						compareFields);
//				} catch(IllegalArgumentException e)
//				{
//					switch(theNoLinkPolicy)
//					{
//					case THROW:
//						throw e;
//					case TRIM:
//						o1 = prisms.util.ArrayUtils.remove(o1, i);
//						i--;
//						break;
//					case WARN:
//						log.warn("Property link error", e);
//						break;
//					}
//				}
//			}
//		}
//		else if(getters.length > getterIdx)
//		{
//			Object got = get(o, getters[getterIdx]);
//			Object newValue = linkEntry(got, property, getters, linkProperty, setters,
//				getterIdx + 1, compareFields);
//			set(o, setters[getterIdx], getters[getterIdx], newValue);
//		}
//		else
//		{
//			Object newVal = find(o, property, linkProperty, 1, compareFields);
//			if(newVal == null)
//				throw new IllegalArgumentException("linkable value not found in property");
//			return newVal;
//		}
//		return o;
//	}
//
//	private Object find(Object o, Object property, PrismsProperty<?> [] linkProperty,
//		int propertyIdx, String [] compareFields)
//	{
//		if(property instanceof Object [])
//		{
//			for(Object p1 : ((Object []) property))
//			{
//				Object ret = find(o, p1, linkProperty, propertyIdx, compareFields);
//				if(ret != null)
//					return ret;
//			}
//		}
//		else if(linkProperty.length > propertyIdx)
//		{
//			Object got = get(property, linkProperty[propertyIdx]);
//			return find(o, got, linkProperty, propertyIdx + 1, compareFields);
//		}
//		else if(matches(o, property, compareFields))
//			return property;
//		return null;
//	}
//
//	public boolean matches(Object template, Object property, String [] compareFields)
//	{
//		for(String field : compareFields)
//			if(!prisms.util.ArrayUtils.equals(get(template, field), get(property, field)))
//				return false;
//		return true;
//	}
//
//	private Object get(Object o, String getterMethod)
//	{
//		java.lang.reflect.Method[] methods;
//		try
//		{
//			methods = o.getClass().getMethods();
//		} catch(Exception e)
//		{
//			throw new IllegalStateException("Could not access methods of class " + o.getClass(), e);
//		}
//		java.lang.reflect.Method method = null;
//		boolean indexed = false;
//		for(java.lang.reflect.Method m : methods)
//		{
//			if(!m.getName().equals(getterMethod))
//				continue;
//			if(m.getParameterTypes().length == 0)
//			{
//				method = m;
//				break;
//			}
//			if(m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == Integer.TYPE)
//			{
//				method = m;
//				indexed = true;
//				break;
//			}
//		}
//		if(method != null)
//		{
//			if(indexed)
//			{
//				java.util.ArrayList<Object> ret = new java.util.ArrayList<Object>();
//				boolean exit = false;
//				while(!exit)
//				{
//					try
//					{
//						ret.add(method.invoke(o, new Object [] {new Integer(ret.size())}));
//					} catch(java.lang.reflect.InvocationTargetException e)
//					{
//						if(e.getCause() instanceof IndexOutOfBoundsException
//							|| e.getCause() instanceof IllegalArgumentException)
//							exit = true;
//					} catch(Exception e)
//					{
//						throw new IllegalStateException("Could not invoke indexed getter "
//							+ getterMethod + " of class " + o.getClass(), e);
//					}
//				};
//				return ret.toArray();
//			}
//			else
//			{
//				try
//				{
//					return method.invoke(o, (Object []) null);
//				} catch(Exception e)
//				{
//					throw new IllegalStateException("Could not invoke getter " + getterMethod
//						+ " of class " + o.getClass(), e);
//				}
//			}
//		}
//		java.lang.reflect.Field[] fields;
//		try
//		{
//			fields = o.getClass().getFields();
//		} catch(Exception e)
//		{
//			throw new IllegalStateException("Could not access fields of class " + o.getClass(), e);
//		}
//		java.lang.reflect.Field field = null;
//		for(java.lang.reflect.Field f : fields)
//		{
//			if(!f.getName().equals(getterMethod))
//				continue;
//			field = f;
//		}
//		if(field != null)
//		{
//			try
//			{
//				return field.get(o);
//			} catch(Exception e)
//			{
//				throw new IllegalStateException("Could not get value of field " + getterMethod
//					+ " of class " + o.getClass(), e);
//			}
//		}
//		throw new IllegalStateException("No such getter " + getterMethod + " of class "
//			+ o.getClass());
//	}
//
//	private void set(Object o, String setterMethod, String getterMethod, Object toSet)
//	{
//		java.lang.reflect.Method[] methods;
//		try
//		{
//			methods = o.getClass().getMethods();
//		} catch(Exception e)
//		{
//			throw new IllegalStateException("Could not access methods of class " + o.getClass(), e);
//		}
//		java.lang.reflect.Method method = null;
//		boolean indexed = false;
//		for(java.lang.reflect.Method m : methods)
//		{
//			if(!m.getName().equals(setterMethod))
//				continue;
//			if(m.getParameterTypes().length == 0)
//			{
//				method = m;
//				break;
//			}
//			if(m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == Integer.TYPE)
//			{
//				method = m;
//				indexed = true;
//				break;
//			}
//		}
//		if(method != null)
//		{
//			if(indexed)
//			{
//				if(toSet == null)
//					toSet = new Object [0];
//				else if(!(toSet instanceof Object []))
//					throw new IllegalStateException(
//						"Cannot execute indexed setter on non-indexed type "
//							+ toSet.getClass().getName());
//				Object [] iToSet = (Object []) toSet;
//				int i;
//				for(i = 0; i < iToSet.length; i++)
//				{
//					try
//					{
//						method.invoke(o, new Object [] {new Integer(i), iToSet[i]});
//					} catch(Exception e)
//					{
//						throw new IllegalStateException("Could not invoke indexed setter "
//							+ setterMethod + " of class " + o.getClass(), e);
//					}
//				};
//				boolean exit = false;
//				while(!exit)
//				{
//					try
//					{
//						method.invoke(o, new Object [] {new Integer(i)});
//					} catch(java.lang.reflect.InvocationTargetException e)
//					{
//						if(e.getCause() instanceof IndexOutOfBoundsException
//							|| e.getCause() instanceof IllegalArgumentException)
//							exit = true;
//					} catch(Exception e)
//					{
//						throw new IllegalStateException("Could not invoke indexed getter "
//							+ getterMethod + " of class " + o.getClass(), e);
//					}
//					try
//					{
//						method.invoke(o, new Object [] {new Integer(i), null});
//					} catch(Exception e)
//					{
//						throw new IllegalStateException("Could not invoke indexed setter "
//							+ setterMethod + " of class " + o.getClass() + " with null value", e);
//					}
//					i++;
//				};
//			}
//			else
//			{
//				try
//				{
//					method.invoke(o, new Object [] {toSet});
//				} catch(Exception e)
//				{
//					throw new IllegalStateException("Could not invoke setter " + setterMethod
//						+ " of class " + o.getClass(), e);
//				}
//			}
//		}
//		java.lang.reflect.Field[] fields;
//		try
//		{
//			fields = o.getClass().getFields();
//		} catch(Exception e)
//		{
//			throw new IllegalStateException("Could not access fields of class " + o.getClass(), e);
//		}
//		java.lang.reflect.Field field = null;
//		for(java.lang.reflect.Field f : fields)
//		{
//			if(!f.getName().equals(getterMethod))
//				continue;
//			field = f;
//		}
//		if(field != null)
//		{
//			try
//			{
//				field.set(o, toSet);
//			} catch(Exception e)
//			{
//				throw new IllegalStateException("Could not set value of field " + getterMethod
//					+ " of class " + o.getClass(), e);
//			}
//		}
//		throw new IllegalStateException("No such setter " + setterMethod + " of class "
//			+ o.getClass());
//	}
//}
