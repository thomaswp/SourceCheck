package com.snap.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Store {
	
	public enum Mode {
		Ignore,
		Use,
		Overwrite
	}
	
	private final static Kryo kryo = new Kryo();
	
	public static <T> T getCachedObject(String path, Class<T> clazz, Mode cacheUse, Loader<T> loader) {
		return getCachedObject(kryo, path, clazz, cacheUse, loader);
	}
	
	public static <T> T getCachedObject(Kryo kryo, String path, Class<T> clazz, Mode cacheUse, Loader<T> loader) {
		File cached = new File(path);
		if (cacheUse == Mode.Use && cached.exists()) {
			try {
				Input input = new Input(new FileInputStream(cached));
				T rows = kryo.readObject(input, clazz);
				input.close();
				if (rows != null) return rows;
			} catch (Exception e) {
				e.printStackTrace();
				cached.delete();
			}
		}
		
		T rows = loader.load();
		
		if (cacheUse != Mode.Ignore) {
			cached.delete();
			try {
				Output output = new Output(new FileOutputStream(cached));
				kryo.writeObject(output, rows);
				output.close();
			} catch (Exception e) { }
		}
		
		return rows;
	}
	
	public interface Loader<T> {
		T load();
	}

}
