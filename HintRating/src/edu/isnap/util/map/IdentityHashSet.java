package edu.isnap.util.map;

import java.util.IdentityHashMap;

@SuppressWarnings("serial")
// We cannot actually implement Set because its methods are incompatible with Map
public class IdentityHashSet<K> extends IdentityHashMap<K, Void> {

	public boolean add(K item) {
		if (containsKey(item)) return false;
		put(item, null);
		return true;
	}

	public boolean contains(K item) {
		return containsKey(item);
	}
}
