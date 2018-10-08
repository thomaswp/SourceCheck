package edu.isnap.util.map;

import java.util.ArrayList;
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
			put(key, list = new ArrayList<>());
		}
		return list;
	}

	public void add(K key, V value) {
		getList(key).add(value);
	}
}
