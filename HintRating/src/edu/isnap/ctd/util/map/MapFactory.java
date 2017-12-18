package edu.isnap.ctd.util.map;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public interface MapFactory {
	public <A, B> Map<A, B> createMap();

	public final static MapFactory HashMapFactory = new MapFactory() {
		@Override
		public <A, B> Map<A, B> createMap() {
			return new HashMap<>();
		}
	};

	public static final MapFactory TreeMapFactory = new MapFactory() {
		@Override
		public <A, B> Map<A, B> createMap() {
			return new TreeMap<>();
		}
	};

	public final MapFactory IdentityHashMapFactory = new MapFactory() {
		@Override
		public <A, B> Map<A, B> createMap() {
			return new IdentityHashMap<>();
		}
	};

	public final MapFactory LinkedHashMapFactory = new MapFactory() {
		@Override
		public <A, B> Map<A, B> createMap() {
			return new LinkedHashMap<>();
		}
	};
}
