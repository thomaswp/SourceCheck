package edu.isnap.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import edu.isnap.dataset.IVersioned;

/**
 * Class for caching and loading data-structures using Kryo.
 */
public class Store {

	/** Mode for loading data. */
	public enum Mode {
		/** Ignores any cached data and reloads from scratch, without overwriting cached data. */
		Ignore,
		/** Uses any existing cached data if it exists, or loads and caches if not. */
		Use,
		/** Reloads data and overwrites the existing cache. */
		Overwrite
	}

	private final static Kryo kryo = new Kryo();

	public static <T> T getCachedObject(String path, Class<T> clazz, Mode cacheUse,
			Loader<T> loader) {
		return getCachedObject(kryo, path, clazz, cacheUse, loader);
	}

	public static <T> T getCachedObject(Kryo kryo, String path, Class<T> clazz, Mode cacheUse,
			Loader<T> loader) {
		File cached = new File(path);
		if (cacheUse == Mode.Use && cached.exists()) {
			try {
				Input input = new Input(new FileInputStream(cached));
				T rows = kryo.readObject(input, clazz);
				input.close();
				if (rows != null) {
					if (!(rows instanceof IVersioned) || ((IVersioned)rows).isUpToDate()) {
						return rows;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Error Kryo parsing: " + path);
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
