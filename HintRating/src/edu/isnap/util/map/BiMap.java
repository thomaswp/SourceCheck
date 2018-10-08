package edu.isnap.util.map;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class BiMap<T, U> {

	private final Map<T, U> fromMap;
	private final Map<U, T> toMap;

	public BiMap() {
		this(MapFactory.HashMapFactory);
	}

	public BiMap(MapFactory factory) {
		fromMap = factory.createMap();
		toMap = factory.createMap();
	}

	public U put(T from, U to) {
		U removedFrom = fromMap.put(from, to);
		T removedTo = toMap.put(to, from);
		if (removedFrom != null && !equal(removedFrom, to)) toMap.remove(removedFrom);
		if (removedTo != null && !equal(removedTo, from)) fromMap.remove(removedTo);
		return removedFrom;
	}

	private boolean equal(Object a, Object b) {
		if (fromMap instanceof IdentityHashMap) {
			return a == b;
		}
		if (a == null) return b == null;
		return a.equals(b);
	}

	public U getFrom(T item) {
		return fromMap.get(item);
	}

	public T getTo(U item) {
		return toMap.get(item);
	}

	public boolean containsFrom(T item) {
		return fromMap.containsKey(item);
	}

	public boolean containsTo(U item) {
		return toMap.containsKey(item);
	}

	public void removeFrom(T item) {
		U pair = fromMap.remove(item);
		if (pair != null) toMap.remove(pair);
	}

	public void removeTo(U item) {
		T pair = toMap.remove(item);
		if (pair != null) fromMap.remove(pair);
	}

	public Set<T> keysetFrom() {
		return fromMap.keySet();
	}

	public Set<U> keysetTo() {
		return toMap.keySet();
	}

	@Override
	public String toString() {
		return fromMap.toString();
	}

	public void clear() {
		fromMap.clear();
		toMap.clear();
	}
}
