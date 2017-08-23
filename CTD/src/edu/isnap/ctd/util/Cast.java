package edu.isnap.ctd.util;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

public class Cast {

	public static <T> T cast(Object o, Class<T> clazz) {
		if (clazz.isInstance(o)) return clazz.cast(o);
		return null;
	}

	public static void main(String[] args) {
		System.out.println(cast(null, Object.class));
		System.out.println(cast(System.in, Object.class));
		System.out.println(cast(System.in, InputStream.class));
		System.out.println(cast(System.in, BufferedInputStream.class));
		System.out.println(cast(System.in, FileInputStream.class));
		System.out.println(System.in);
	}
}
