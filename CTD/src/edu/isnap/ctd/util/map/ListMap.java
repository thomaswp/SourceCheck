package edu.isnap.ctd.util.map;

import java.util.LinkedList;
import java.util.List;

public class ListMap<K, V> extends ExtensionMap<K, List<V>> {

	public ListMap() {
		this(MapFactory.HashMapFactory);
	}

	public ListMap(MapFactory factory) {
		super(factory);
	}

	public List<V> getList(K key) {
		List<V> list = get(key);
		if (list == null) {
			return new LinkedList<>();
		}
		return list;
	}

	public List<V> add(K key, V value) {
		List<V> list = get(key);
		if (list == null) {
			put(key, list = new LinkedList<>());
		}
		list.add(value);
		return list;
	}
}
