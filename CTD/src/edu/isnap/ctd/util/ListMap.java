package edu.isnap.ctd.util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class ListMap<K, V> extends HashMap<K, List<V>> {
	private static final long serialVersionUID = 1L;

	public List<V> getList(K key) {
		List<V> list = get(key);
		if (list == null) {
			return new LinkedList<V>();
		}
		return list;
	}

	public List<V> add(K key, V value) {
		List<V> list = get(key);
		if (list == null) {
			put(key, list = new LinkedList<V>());
		}
		list.add(value);
		return list;
	}
}
