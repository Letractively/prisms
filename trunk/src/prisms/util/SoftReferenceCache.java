/**
 * SoftReferenceCache.java Created Mar 18, 2009 by Andrew Butler, PSL
 */
package prisms.util;

import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * A thread-safe cache that keeps its values as {@link java.lang.ref.SoftReference}s so that the
 * cache is, in effect, managed by the JVM and kept as small as is required
 * 
 * @param <K> The type of key used to store values with
 * @param <V> The type of values to store
 */
public class SoftReferenceCache<K, V> implements Map<K, V>
{
	java.util.HashMap<K, KeyedSoftReference<V>> theCache;

	private java.util.concurrent.locks.ReentrantReadWriteLock theLock;

	private java.lang.ref.ReferenceQueue<V> theRefQueue;

	/** Creates a SoftReferenceCache */
	public SoftReferenceCache()
	{
		theCache = new java.util.HashMap<K, KeyedSoftReference<V>>();
		theLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
		theRefQueue = new java.lang.ref.ReferenceQueue<V>();
	}

	public V get(Object key)
	{
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			KeyedSoftReference<V> val = theCache.get(key);
			return val == null ? null : val.get();
		} finally
		{
			lock.unlock();
		}
	}

	public V put(K key, V value)
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			removeQueued();
			KeyedSoftReference<V> val = theCache.get(key);
			theCache.put(key, new KeyedSoftReference<V>(key, value, theRefQueue));
			return val == null ? null : val.get();
		} finally
		{
			lock.unlock();
		}
	}

	public V remove(Object key)
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			removeQueued();
			KeyedSoftReference<V> val = theCache.remove(key);
			return val == null ? null : val.get();
		} finally
		{
			lock.unlock();
		}
	}

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
		Lock lock = theLock.readLock();
		lock.lock();
		try
		{
			KeyedSoftReference<V> val = theCache.get(key);
			return val == null || val.isEnqueued();
		} finally
		{
			lock.unlock();
		}
	}

	public boolean containsValue(Object value)
	{
		throw new UnsupportedOperationException("The containsValue method is not supported by "
			+ getClass().getName());
	}

	public void putAll(Map<? extends K, ? extends V> m)
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		try
		{
			removeQueued();
			for(java.util.Map.Entry<? extends K, ? extends V> entry : m.entrySet())
			{
				theCache.put(entry.getKey(),
					new KeyedSoftReference<V>(entry.getKey(), entry.getValue(), theRefQueue));
			}
		} finally
		{
			lock.unlock();
		}
	}

	public Set<K> keySet()
	{
		Lock lock = theLock.writeLock();
		lock.lock();
		final Object [] keys;
		try
		{
			removeQueued();
			keys = theCache.keySet().toArray();
		} finally
		{
			lock.unlock();
		}
		return new java.util.AbstractSet<K>()
		{
			@Override
			public Iterator<K> iterator()
			{
				return new java.util.Iterator<K>()
				{
					private int index = 0;

					public boolean hasNext()
					{
						return index < keys.length - 1;
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
						SoftReferenceCache.this.remove(keys[index - 1]);
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
		throw new UnsupportedOperationException("The entrySet method is not supported by "
			+ getClass().getName());
	}

	public Collection<V> values()
	{
		throw new UnsupportedOperationException("The values method is not supported by "
			+ getClass().getName());
	}

	/**
	 * Removes all entries in {@link #theRefQueue} from the map since their references have been
	 * deleted. The write lock from {@link #theLock} MUST be obtained by the thread before calling
	 * this method.
	 */
	private void removeQueued()
	{
		KeyedSoftReference<V> ref = (KeyedSoftReference<V>) theRefQueue.poll();
		while(ref != null)
		{
			theCache.remove(ref.theKey);
			ref = (KeyedSoftReference<V>) theRefQueue.poll();
		}
	}

	private class KeyedSoftReference<V2> extends java.lang.ref.SoftReference<V2>
	{
		final K theKey;

		KeyedSoftReference(K key, V2 referent, java.lang.ref.ReferenceQueue<? super V2> queue)
		{
			super(referent, queue);
			theKey = key;
		}
	}
}
