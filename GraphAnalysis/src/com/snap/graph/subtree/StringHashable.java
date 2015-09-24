package com.snap.graph.subtree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class StringHashable implements Comparable<StringHashable> {
	
	private transient String cachedCanonicalString;
	private transient int cachedHashCode;
	
	public void cache() {
		cachedCanonicalString = toCanonicalStringInternal();
		cachedHashCode = cachedCanonicalString.hashCode();
	}
	
	public boolean cached() {
		return cachedCanonicalString != null;
	}
	
	public void clearCache() {
		cachedCanonicalString = null;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof StringHashable)) return false;
		return toCanonicalString().equals(((StringHashable)obj).toCanonicalString());
	}
	
	@Override
	public int hashCode() {
		return cachedCanonicalString == null ? toCanonicalString().hashCode() : cachedHashCode;
	}
	
	public String hexHash() {
		return String.format("%x", hashCode());
	}
	
	@Override
	public int compareTo(StringHashable o) {
		if (o == null) return 1;
		return toCanonicalString().compareTo(o.toCanonicalString());
	}
	
	protected abstract String toCanonicalStringInternal();
	
	public final String toCanonicalString() {
		return cachedCanonicalString == null ? toCanonicalStringInternal() : cachedCanonicalString;
	}
	
	public String toDisplayString() {
		return toCanonicalString();
	}
	
	@Override
	public String toString() {
		return toCanonicalString();
	}
	
	protected static String toCannonicalString(Collection<? extends StringHashable> collection) {
		List<String> strings = new ArrayList<String>();
		for (StringHashable s : collection) strings.add(s.toCanonicalString());
		return strings.toString();
	}
}