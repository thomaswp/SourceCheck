package edu.isnap.ctd.util;

import java.util.HashMap;
import java.util.Map;

public class BiMap<T,U> {

	private final Map<T, U> fromMap;
	private final Map<U, T> toMap;

	public BiMap() {
		this(HashMapFactory);
	}

	public BiMap(MapFactory factory) {
		fromMap = factory.createMap();
		toMap = factory.createMap();
	}

	public interface MapFactory {
		public <A, B> Map<A, B> createMap();
	}

	private final static MapFactory HashMapFactory = new MapFactory() {
		@Override
		public <A, B> Map<A, B> createMap() {
			return new HashMap<>();
		}
	};

	public void put(T from, U to) {
		U removedFrom = fromMap.put(from, to);
		T removedTo = toMap.put(to, from);
		if (removedFrom != null && !removedFrom.equals(to)) toMap.remove(removedFrom);
		if (removedTo != null && !removedTo.equals(from)) fromMap.remove(removedTo);
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

	@Override
	public String toString() {
		return fromMap.toString();
	}

	public void clear() {
		fromMap.clear();
		toMap.clear();
	}
}
