package edu.isnap.ctd.util;

public class Cast {
	public static <T> T cast(Object o, Class<T> clazz) {
		if (clazz.isInstance(o)) return clazz.cast(o);
		return null;
	}
}
