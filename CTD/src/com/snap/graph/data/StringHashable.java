package com.snap.graph.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class StringHashable implements Comparable<StringHashable> {

	private transient String cachedCanonicalString;
	private transient int cachedHashCode;

	private final transient boolean autoCache;

	public StringHashable() {
		autoCache = autoCache();
	}

	protected boolean autoCache() {
		return false;
	}

	public void cache() {
		clearCache();
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

	public String hexHash() {
		return String.format("%x", hashCode());
	}

	@Override
	public int compareTo(StringHashable o) {
		if (o == null) return 1;
		return toCanonicalString().compareTo(o.toCanonicalString());
	}

	protected abstract String toCanonicalStringInternal();

	@Override
	public final int hashCode() {
		return cachedCanonicalString == null ?
				toCanonicalString().hashCode() : cachedHashCode;
	}

	public final String toCanonicalString() {
		if (cachedCanonicalString != null) {
			return cachedCanonicalString;
		} else if (autoCache) {
			cache();
			return cachedCanonicalString;
		}
		return toCanonicalStringInternal();
	}

	public String toDisplayString() {
		return toCanonicalString();
	}

	@Override
	public String toString() {
		return toCanonicalString();
	}

	protected static String toCannonicalString(
			Collection<? extends StringHashable> collection) {
		List<String> strings = new ArrayList<String>();
		for (StringHashable s : collection) strings.add(s.toCanonicalString());
		return strings.toString();
	}
}