package edu.isnap.ctd.util;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("serial")
public class DoubleMap<T, U, V> extends HashMap<T, Map<U, V>>{

	public V get(T key1, U key2) {
		Map<U, V> map = get(key1);
		if (map == null) return null;
		return map.get(key2);
	}

	public V put(T key1, U key2, V value) {
		Map<U, V> map = get(key1);
		if (map == null) put(key1, map = new HashMap<>());
		return map.put(key2, value);
	}

	public boolean containsKey(T key1, U key2) {
		return get(key1, key2) != null;
	}

}
