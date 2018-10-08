package edu.isnap.util.map;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

public class ExtensionMap<K, V> extends AbstractMap<K, V> {

	protected final MapFactory factory;
	protected final Map<K, V> map;

	public ExtensionMap(MapFactory factory) {
		this.factory = factory;
		this.map = factory.createMap();
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return map.entrySet();
	}

	@Override
	public V put(K key, V value) {
		return map.put(key, value);
	}
}
