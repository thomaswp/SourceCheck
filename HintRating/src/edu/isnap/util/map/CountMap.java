package edu.isnap.util.map;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CountMap<K> extends ExtensionMap<K, Integer>{

	public CountMap() {
		this(MapFactory.HashMapFactory);
	}

	public CountMap(MapFactory factory) {
		super(factory);
	}

	public int getCount(K key) {
		Integer count = get(key);
		if (count == null) return 0;
		return count;
	}

	public int change(K key, int by) {
		int value = getCount(key) + by;
		put(key, value);
		return value;
	}

	public int increment(K key) {
		return change(key, 1);
	}

	public void incrementAll(Collection<K> items) {
		for (K item : items) increment(item);
	}

	public int decrement(K key) {
		return change(key, -1);
	}

	public void add(CountMap<K> map) {
		for (K key : map.keySet()) {
			change(key, map.getCount(key));
		}
	}

	public void scale(int by) {
		for (K key : keySet()) {
			put(key, get(key) * by);
		}
	}

	public int totalCount() {
		int count = 0;
		for (Integer v : values()) {
			count += v;
		}
		return count;
	}

	public void removeZeros() {
		Set<K> toRemove = new HashSet<>();
		for (K k : keySet()) {
			if (getCount(k) == 0) {
				toRemove.add(k);
			}
		}
		for (K k : toRemove) {
			remove(k);
		}
	}

	public static <K> CountMap<K> fromArray(K[] array) {
		CountMap<K> map = new CountMap<>();
		for (K key : array) {
			map.change(key, 1);
		}
		return map;
	}

	public void printAll(PrintStream out) {
		for (K key : keySet()) {
			out.println(key + ": " + get(key));
		}
	}

	public boolean equals(CountMap<K> map) {
		if (map == null || !map.keySet().equals(keySet())) {
			return false;
		}
		for (K key : keySet()) {
			if (getCount(key) != map.getCount(key)) return false;
		}
		return true;
	}

	// See: https://openaccess.leidenuniv.nl/bitstream/handle/1887/14533/06.pdf?sequence=12
	public static <K> double distance(CountMap<K> map1, CountMap<K> map2) {
		HashSet<K> keys = new HashSet<>(map1.keySet());
		keys.addAll(map2.keySet());
		double dis = 0;
		for (K key : keys) {
			int count1 = map1.getCount(key);
			int count2 = map2.getCount(key);
			double denominator = (count1 + 1) * (count2 + 1);
			dis += Math.abs(count1 - count2) / denominator;
		}
		return dis / (keys.size() + 1);
	}
}
