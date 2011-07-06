/**
 * SmartCache.java Created Mar 26, 2009 by Andrew Butler, PSL
 */
package prisms.util;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A cache that purges values according to their frequency and recency of use and other qualitative
 * values.
 * 
 * @param <K> The type of key to use for the cache
 * @param <V> The type of value to cache
 * @see #shouldRemove(CacheValue, float, float, float)
 */
public class DemandCache<K, V> implements java.util.Map<K, V>
{
	/**
	 * Allows this cache to assess the quality of a cache item to determine its value to the
	 * accessor. Implementors of this class should make the methods as fast as possible to speed up
	 * the purge process.
	 * 
	 * @param <T> The type of value to be qualitized
	 */
	public static interface Qualitizer<T>
	{
		/**
		 * @param value The value to assess
		 * @return The quality of the value. Units are undefined but must be consistent. 0 to 1 is
		 *         recommended but not required.
		 */
		float quality(T value);

		/**
		 * @param value The value to assess
		 * @return The amount of space the value takes up. Units are undefined but must be
		 *         consistent. Bytes is recommended but not required.
		 */
		float size(T value);
	}

	/**
	 * Access to an object in the same amount as a get operation
	 * 
	 * @see #access(Object, int)
	 */
	public static final int ACCESS_GET = 1;

	/**
	 * Access to an object in the same amount as a set operation
	 * 
	 * @see #access(Object, int)
	 */
	public static final int ACCESS_SET = 2;

	class CacheValue
	{
		V value;

		float demand;
	}

	private final Qualitizer<V> theQualitizer;

	private final java.util.concurrent.ConcurrentHashMap<K, CacheValue> theCache;

	private float thePreferredSize;

	private long theHalfLife;

	private float theReference;

	private long theCheckedTime;

	private long thePurgeTime;

	private int thePurgeMods;

	/** Creates a DemandCache with default values */
	public DemandCache()
	{
		this(null, -1, -1);
	}

	/**
	 * Creates a DemandCache
	 * 
	 * @param qualitizer The qualitizer to qualitize the values by
	 * @param prefSize The preferred size of this cache, or <=0 if this cache should have no
	 *        preferred size
	 * @param halfLife The half life of this cache
	 */
	public DemandCache(Qualitizer<V> qualitizer, float prefSize, long halfLife)
	{
		theQualitizer = qualitizer;
		theCache = new java.util.concurrent.ConcurrentHashMap<K, CacheValue>();
		thePreferredSize = prefSize;
		theHalfLife = halfLife;
		theReference = 1;
		theCheckedTime = System.currentTimeMillis();
		thePurgeTime = theCheckedTime;
	}

	/** @return The preferred size of this cache, or <=0 if this cache has no preferred size */
	public float getPreferredSize()
	{
		return thePreferredSize;
	}

	/**
	 * @param prefSize The preferred size for this cache or <=0 if this cache should have no
	 *        preferred size
	 */
	public void setPreferredSize(float prefSize)
	{
		boolean mod = prefSize <= 0 ? false : prefSize < thePreferredSize;
		thePreferredSize = prefSize;
		if(mod)
			purge(true);
	}

	/** @return The approximate half life of items in this cache */
	public long getHalfLife()
	{
		return theHalfLife;
	}

	/** @param halfLife The half life for items in this cache */
	public void setHalfLife(long halfLife)
	{
		boolean mod = halfLife <= 0 ? false : halfLife < theHalfLife;
		theHalfLife = halfLife;
		if(mod)
			purge(true);
	}

	public V get(Object key)
	{
		CacheValue value = theCache.get(key);
		if(value == null)
			return null;
		_access(value, ACCESS_GET);
		return value.value;
	}

	public V put(K key, V value)
	{
		CacheValue newValue = new CacheValue();
		newValue.value = value;
		_access(newValue, ACCESS_SET);
		CacheValue oldValue = theCache.put(key, newValue);
		thePurgeMods++;
		purge(false);
		return oldValue == null ? null : oldValue.value;
	}

	public V remove(Object key)
	{
		CacheValue oldValue = theCache.remove(key);
		return oldValue == null ? null : oldValue.value;
	}

	public void clear()
	{
		theCache.clear();
	}

	public int size()
	{
		return theCache.size();
	}

	public boolean isEmpty()
	{
		return theCache.isEmpty();
	}

	public boolean containsKey(Object key)
	{
		return theCache.containsKey(key);
	}

	public boolean containsValue(Object value)
	{
		for(CacheValue val : theCache.values())
			if(value.equals(val.value))
				return true;
		return false;
	}

	public void putAll(Map<? extends K, ? extends V> m)
	{
		for(java.util.Map.Entry<? extends K, ? extends V> entry : m.entrySet())
		{
			CacheValue cv = new CacheValue();
			cv.value = entry.getValue();
			_access(cv, ACCESS_SET);
			theCache.put(entry.getKey(), cv);
			thePurgeMods++;
		}
		purge(false);
	}

	public Set<K> keySet()
	{
		final Object [] keys;
		purge(false);
		keys = theCache.keySet().toArray();
		return new java.util.AbstractSet<K>()
		{
			@Override
			public java.util.Iterator<K> iterator()
			{
				return new java.util.Iterator<K>()
				{
					private int index = 0;

					public boolean hasNext()
					{
						return index < keys.length;
					}

					public K next()
					{
						if(index >= keys.length)
							throw new java.util.NoSuchElementException();
						index++;
						return (K) keys[index - 1];
					}

					public void remove()
					{
						DemandCache.this.remove(keys[index - 1]);
					}
				};
			}

			@Override
			public int size()
			{
				return keys.length;
			}
		};
	}

	public Set<java.util.Map.Entry<K, V>> entrySet()
	{
		final Map.Entry<K, CacheValue> [] entries;
		purge(false);
		entries = theCache.entrySet().toArray(new Map.Entry [0]);
		return new java.util.AbstractSet<Map.Entry<K, V>>()
		{
			@Override
			public java.util.Iterator<Map.Entry<K, V>> iterator()
			{
				return new java.util.Iterator<Map.Entry<K, V>>()
				{
					int index = 0;

					public boolean hasNext()
					{
						return index < entries.length;
					}

					public Map.Entry<K, V> next()
					{
						Map.Entry<K, V> ret = new Map.Entry<K, V>()
						{
							private final int entryIndex = index;

							public K getKey()
							{
								return entries[entryIndex].getKey();
							}

							public V getValue()
							{
								return entries[entryIndex].getValue().value;
							}

							public V setValue(V value)
							{
								V retValue = entries[entryIndex].getValue().value;
								entries[entryIndex].getValue().value = value;
								return retValue;
							}

							@Override
							public String toString()
							{
								return entries[entryIndex].getKey().toString() + "="
									+ entries[entryIndex].getValue().value;
							}
						};
						index++;
						return ret;
					}

					public void remove()
					{
						DemandCache.this.remove(entries[index - 1].getKey());
					}
				};
			}

			@Override
			public int size()
			{
				return entries.length;
			}
		};
	}

	public Set<V> values()
	{
		final Set<java.util.Map.Entry<K, V>> entrySet = entrySet();
		return new java.util.AbstractSet<V>()
		{
			@Override
			public Iterator<V> iterator()
			{
				final java.util.Iterator<java.util.Map.Entry<K, V>> entryIter;
				entryIter = entrySet.iterator();
				return new java.util.Iterator<V>()
				{
					public boolean hasNext()
					{
						return entryIter.hasNext();
					}

					public V next()
					{
						return entryIter.next().getValue();
					}

					public void remove()
					{
						entryIter.remove();
					}
				};
			}

			@Override
			public int size()
			{
				return entrySet.size();
			}
		};
	}

	/**
	 * @return The total size of the data in this cache, according to
	 *         {@link Qualitizer#size(Object)}. This value will return the same as {@link #size()}
	 *         if the qualitizer was not set in the constructor.
	 */
	public float getTotalSize()
	{
		float ret = 0;
		for(CacheValue value : theCache.values())
			ret += theQualitizer.size(value.value);
		return ret;
	}

	/**
	 * @return The average quality of the values in this cache, according to
	 *         {@link Qualitizer#quality(Object)}. This value will return 1 if the qualitizer was
	 *         not set in the constructor.
	 */
	public float getAverageQuality()
	{
		if(theQualitizer == null)
			return theCache.size();
		float ret = 0;
		int count = 0;
		for(CacheValue value : theCache.values())
		{
			count++;
			ret += theQualitizer.quality(value.value);
		}
		return ret / count;
	}

	private void _access(CacheValue value, int weight)
	{
		if(weight <= 0 || theHalfLife <= 0)
			return;
		updateReference();
		if(weight > 10)
			weight = 10;
		float ref = theReference * weight;
		if(value.demand <= 1)
			value.demand += ref;
		else if(value.demand < ref * 2)
			value.demand += ref / value.demand;
	}

	/**
	 * Performs an access operation on a cache item, causing it to live longer in the cache
	 * 
	 * @param key The key of the item to access
	 * @param weight The weight of the access--higher weight will result in a more persistent cache
	 *        item. 1-10 are supported. A guideline is that the cache item will survive longer by
	 *        {@link #getHalfLife()} <code>weight</code>.
	 * @see #ACCESS_GET
	 * @see #ACCESS_SET
	 */
	public void access(K key, int weight)
	{
		CacheValue value = theCache.get(key);
		if(value != null)
			_access(value, weight);
	}

	/**
	 * Purges the cache of values that are deemed of less use to the accessor. The behavior of this
	 * method depends the behavior of {@link #shouldRemove(CacheValue, float, float, float)}
	 * 
	 * @param force If false, the purge operation will only be performed if this cache determines
	 *        that a purge is needed (determined by the number of modifications directly to this
	 *        cache and time elapsed since the last purge). <code>force</code> may be used to cause
	 *        a purge without a perceived "need", such as may be warranted if a cached item's size
	 *        or quality changes drastically.
	 */
	public void purge(boolean force)
	{
		if(!force)
		{
			float purgeNeed = (thePurgeMods * 1.0f / 4)
				+ (System.currentTimeMillis() - thePurgeTime) / 60000f;
			if(purgeNeed < 1)
				return;
		}
		updateReference();
		scaleReference();
		int count = 0;
		float totalSize = 0;
		float totalQuality = 0;
		if(theQualitizer == null)
		{
			count = theCache.size();
			totalSize = count;
			totalQuality = count;
		}
		else
			for(CacheValue value : theCache.values())
			{
				count++;
				totalSize += theQualitizer.size(value.value);
				totalQuality += theQualitizer.quality(value.value);
			}

		java.util.Iterator<CacheValue> iter = theCache.values().iterator();
		while(iter.hasNext())
		{
			CacheValue next = iter.next();
			if(shouldRemove(next, totalSize, totalQuality / count, count))
			{
				count--;
				if(theQualitizer != null)
				{
					totalSize -= theQualitizer.size(next.value);
					totalQuality -= theQualitizer.quality(next.value);
				}
				else
				{
					totalSize--;
					totalQuality--;
				}
				iter.remove();
			}
		}
		thePurgeTime = System.currentTimeMillis();
		thePurgeMods = 0;
	}

	/**
	 * Determines whether a cache value should be removed from the cache. The behavior of this
	 * method depends on many variables:
	 * <ul>
	 * <li>How frequently and recently the value has been accessed</li>
	 * <li>The quality of the value according to {@link Qualitizer#quality(Object)} compared to the
	 * average quality of the cache</li>
	 * <li>The size of the value according to {@link Qualitizer#size(Object)} compared to the
	 * average size of the cache's values</li>
	 * <li>The total size of the cache compared to its preferred size (assuming this is set to a
	 * value greater than 0)
	 * </ul>
	 * 
	 * @param value The value to determine the quality of
	 * @param totalSize The total size (determined by the Qualitizer) of this cache
	 * @param avgQuality The average quality of the cache
	 * @param entryCount The number of entries in this cache
	 * @return Whether the value should be removed from the cache
	 */
	protected boolean shouldRemove(CacheValue value, float totalSize, float avgQuality,
		float entryCount)
	{
		float quality;
		float size;
		if(theQualitizer == null)
		{
			quality = 1;
			size = 1;
		}
		else
		{
			quality = theQualitizer.quality(value.value);
			size = theQualitizer.size(value.value);
			if(quality == 0)
				return true; // Remove if the value has no quality
			if(size == 0)
				return false; // Don't remove if the value takes up no space
		}

		/* Take into account how frequently and recently the value was accessed */
		float valueQuality = 1;
		if(value.demand > 0)
			valueQuality *= value.demand / theReference;
		/* Take into account the inherent quality in the value compareed to the average */
		valueQuality *= quality / avgQuality;
		/* Take into account the value's size compared with the average size */
		valueQuality /= size / (totalSize / entryCount);
		/* Take into account the overall size of this cache compared with the preferred size
		 * (Whether it is too big or has room to spare) */
		if(thePreferredSize > 0)
			valueQuality /= totalSize / thePreferredSize;
		return valueQuality < 0.5f;
	}

	/** Updates {@link #theReference} to devalue all items in the cache with age. */
	private void updateReference()
	{
		if(theHalfLife <= 0)
			return;
		long time = System.currentTimeMillis();
		if(time - theCheckedTime < theHalfLife / 100)
			return;
		theReference *= Math.pow(2, (time - theCheckedTime) * 1.0 / theHalfLife);
		theCheckedTime = time;
	}

	/**
	 * Scales all {@link CacheValue#demand} values to keep them and {@link #theReference} small.
	 * This allows the cache to be kept for long periods of time. The write lock must be obtained
	 * before calling this method.
	 */
	private void scaleReference()
	{
		if(theHalfLife <= 0)
			return;
		if(theReference > 1e7)
		{
			for(CacheValue value : theCache.values())
				value.demand /= theReference;
			theReference = 1;
		}
	}

	/**
	 * Unit-test
	 * 
	 * @param args Command-line args, ignored
	 */
	public static void main(String [] args)
	{
		int avgSz = 1;
		final float [] sizes = new float [1000];
		for(int idx = 0; idx < sizes.length; idx++)
			sizes[idx] = (float) (Math.random() * avgSz);
		DemandCache<Integer, Integer> cache = new DemandCache<Integer, Integer>(
			new Qualitizer<Integer>()
			{
				public float quality(Integer value)
				{
					return value.intValue();
				}

				public float size(Integer value)
				{
					return sizes[value.intValue()];
				}
			}, 100, 2000);
		for(int i = 0; i < 1000; i++)
			cache.put(Integer.valueOf(i), Integer.valueOf(i));

		float totalSize = 0;
		float totalQ = 0;
		int purgeCount = 0;
		for(int count = 0; count < 1000000; count++)
		{
			Integer test = Integer.valueOf((int) (Math.random() * sizes.length));
			Integer val = cache.get(test);
			if(val == null)
			{
				totalSize += sizes[test.intValue()];
				totalQ += test.intValue();
				purgeCount++;
				cache.put(test, test);
			}
		}
		System.out.println(purgeCount + " purges detected, avg quality "
			+ Math.round(totalQ / purgeCount / sizes.length * 100) + "%, avg size "
			+ Math.round(totalSize / purgeCount / avgSz * 100) + "%");
	}
}
