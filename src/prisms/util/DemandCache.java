/**
 * SmartCache.java Created Mar 26, 2009 by Andrew Butler, PSL
 */
package prisms.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

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
	 * accessor
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
	 * Access to an object in the same amoutn as a set operation
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

	private final java.util.HashMap<K, CacheValue> theCache;

	private final java.util.concurrent.locks.ReentrantReadWriteLock theLock;

	private float thePreferredSize;

	private long theHalfLife;

	private float theReference;

	private long theCheckedTime;

	private long thePurgeTime;

	/**
	 * Creates a DemandCache with default values
	 */
	public DemandCache()
	{
		this(null, -1, 5L * 60 * 1000);
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
		if(qualitizer == null)
			qualitizer = new Qualitizer<V>()
			{
				public float quality(V value)
				{
					return 1;
				}

				public float size(V value)
				{
					return 1;
				}
			};
		theQualitizer = qualitizer;
		theCache = new java.util.HashMap<K, CacheValue>();
		theLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
		thePreferredSize = prefSize;
		theHalfLife = halfLife;
		theReference = 1;
		theCheckedTime = System.currentTimeMillis();
		thePurgeTime = theCheckedTime;
	}

	/**
	 * @return The preferred size of this cache, or <=0 if this cache has no preferred size
	 */
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
		thePreferredSize = prefSize;
	}

	/**
	 * @return The approximate half life of items in this cache
	 */
	public long getHalfLife()
	{
		return theHalfLife;
	}

	/**
	 * @param halfLife The half life for items in this cache
	 */
	public void setHalfLife(long halfLife)
	{
		theHalfLife = halfLife;
	}

	/**
	 * @see java.util.Map#get(java.lang.Object)
	 */
	public V get(Object key)
	{
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			CacheValue value = theCache.get(key);
			if(value == null)
				return null;
			_access(value, ACCESS_GET);
			return value.value;
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see java.util.Map#put(java.lang.Object, java.lang.Object)
	 */
	public V put(K key, V value)
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			if(thePurgeTime < System.currentTimeMillis() - 60 * 1000)
				purge();
			CacheValue newValue = new CacheValue();
			newValue.value = value;
			_access(newValue, ACCESS_SET);
			CacheValue oldValue = theCache.put(key, newValue);
			return oldValue == null ? null : oldValue.value;
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see java.util.Map#remove(java.lang.Object)
	 */
	public V remove(Object key)
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			if(thePurgeTime < System.currentTimeMillis() - 60 * 1000)
				purge();
			CacheValue oldValue = theCache.remove(key);
			return oldValue == null ? null : oldValue.value;
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see java.util.Map#clear()
	 */
	public void clear()
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			theCache.clear();
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see java.util.Map#size()
	 */
	public int size()
	{
		return theCache.size();
	}

	/**
	 * @see java.util.Map#isEmpty()
	 */
	public boolean isEmpty()
	{
		return theCache.isEmpty();
	}

	/**
	 * @see java.util.Map#containsKey(java.lang.Object)
	 */
	public boolean containsKey(Object key)
	{
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			return theCache.containsKey(key);
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see java.util.Map#containsValue(java.lang.Object)
	 */
	public boolean containsValue(Object value)
	{
		throw new UnsupportedOperationException("The containsValue method is not supported by "
			+ getClass().getName());
	}

	/**
	 * @see java.util.Map#putAll(java.util.Map)
	 */
	public void putAll(Map<? extends K, ? extends V> m)
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			if(thePurgeTime < System.currentTimeMillis() - 60 * 1000)
				purge();
			for(java.util.Map.Entry<? extends K, ? extends V> entry : m.entrySet())
			{
				CacheValue cv = new CacheValue();
				cv.value = entry.getValue();
				_access(cv, ACCESS_SET);
				theCache.put(entry.getKey(), cv);
			}
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @see java.util.Map#keySet()
	 */
	public Set<K> keySet()
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		final Object [] keys;
		try
		{
			if(thePurgeTime < System.currentTimeMillis() - 60 * 1000)
				purge();
			keys = theCache.keySet().toArray();
		} finally
		{
			lock.unlock();
		}
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

	/**
	 * @see java.util.Map#entrySet()
	 */
	public Set<java.util.Map.Entry<K, V>> entrySet()
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		final Map.Entry<K, CacheValue> [] entries;
		try
		{
			if(thePurgeTime < System.currentTimeMillis() - 60 * 1000)
				purge();
			entries = theCache.entrySet().toArray(new Map.Entry [0]);
		} finally
		{
			lock.unlock();
		}
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

	/**
	 * @see java.util.Map#values()
	 */
	public Collection<V> values()
	{
		throw new UnsupportedOperationException("The values method is not supported by "
			+ getClass().getName());
	}

	/**
	 * @return The total size of the data in this cache, according to
	 *         {@link Qualitizer#size(Object)}. This value will return the same as {@link #size()}
	 *         if the qualitizer was not set in the constructor.
	 */
	public float getTotalSize()
	{
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			float ret = 0;
			for(CacheValue value : theCache.values())
				ret += theQualitizer.size(value.value);
			return ret;
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * @return The average quality of the values in this cache, according to
	 *         {@link Qualitizer#quality(Object)}. This value will return 1 if the qualitizer was
	 *         not set in the constructor.
	 */
	public float getOverallQuality()
	{
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			float ret = 0;
			for(CacheValue value : theCache.values())
				ret += theQualitizer.quality(value.value);
			return ret / theCache.size();
		} finally
		{
			lock.unlock();
		}
	}

	private void _access(CacheValue value, int weight)
	{
		if(weight <= 0)
			return;
		updateReference();
		if(weight > 10)
			weight = 10;
		value.demand += theReference * weight;
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
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			CacheValue value = theCache.get(key);
			if(value != null)
				_access(value, weight);
		} finally
		{
			lock.unlock();
		}
	}

	/**
	 * Purges the cache of values that are deemed of less use to the accessor. The behavior of this
	 * method depends the behavior of {@link #shouldRemove(CacheValue, float, float, float)}
	 */
	public void purge()
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			updateReference();
			scaleReference();
			int count = size();
			float totalSize = 0;
			float totalQuality = 0;
			for(CacheValue value : theCache.values())
			{
				totalSize += theQualitizer.size(value.value);
				totalQuality += theQualitizer.quality(value.value);
			}
			totalQuality /= count;

			java.util.Iterator<CacheValue> iter = theCache.values().iterator();
			while(iter.hasNext())
			{
				CacheValue next = iter.next();
				if(shouldRemove(next, totalSize, totalQuality, count))
					iter.remove();
			}
		} finally
		{
			lock.unlock();
		}
		thePurgeTime = System.currentTimeMillis();
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
	 * @param overallQuality The overall quality of the cache
	 * @param entryCount The number of entries in this cache
	 * @return Whether the value should be removed from the cache
	 */
	protected boolean shouldRemove(CacheValue value, float totalSize, float overallQuality,
		float entryCount)
	{
		float quality = theQualitizer.quality(value.value);
		if(quality == 0)
			return true; // Remove if the value has no quality
		float size = theQualitizer.size(value.value);
		if(size == 0)
			return false; // Don't remove if the value takes up no space

		/* Take into account how frequently and recently the value was accessed */
		float valueQuality = value.demand / theReference;
		/* Take into account the inherent quality in the value compareed to the average */
		valueQuality *= quality / overallQuality;
		/* Take into account the value's size compared with the average size */
		valueQuality /= size / (totalSize / entryCount);
		/* Take into account the overall size of this cache compared with the preferred size
		 * (Whether it is too big or has room to spare) */
		if(thePreferredSize > 0)
			valueQuality /= totalSize / thePreferredSize;
		return valueQuality < 0.5f;
	}

	/**
	 * Updates {@link #theReference} to devalue all items in the cache with age. The read lock must
	 * be obtained before calling this method.
	 */
	private void updateReference()
	{
		long time = System.currentTimeMillis();
		if(time - theCheckedTime >= theHalfLife / 100)
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
		if(theReference > 1e7)
		{
			for(CacheValue value : theCache.values())
				value.demand /= theReference;
			theReference = 1;
		}
	}
}
