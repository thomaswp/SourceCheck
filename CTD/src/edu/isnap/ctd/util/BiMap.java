package edu.isnap.ctd.util;

import java.util.HashMap;

public class BiMap<T,U> {

	private final HashMap<T, U> fromMap = new HashMap<>();
	private final HashMap<U, T> toMap = new HashMap<>();
	
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
}
