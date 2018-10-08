package edu.isnap.util.map;

import java.util.HashMap;
import java.util.Map;

public class DoubleMap<K1, K2, V> extends ExtensionMap<K1, Map<K2, V>> {

	public DoubleMap() {
		this(MapFactory.HashMapFactory);
	}

	public DoubleMap(MapFactory factory) {
		super(factory);
	}

	public V get(K1 key1, K2 key2) {
		Map<K2, V> map = get(key1);
		if (map == null) return null;
		return map.get(key2);
	}

	public V put(K1 key1, K2 key2, V value) {
		Map<K2, V> map = get(key1);
		if (map == null) put(key1, map = new HashMap<>());
		return map.put(key2, value);
	}

	public boolean containsKey(K1 key1, K2 key2) {
		return get(key1, key2) != null;
	}

}
