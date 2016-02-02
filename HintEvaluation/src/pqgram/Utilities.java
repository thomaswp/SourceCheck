package pqgram;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
/**
 * Generic utility methods used during the pq-Gram algorithms
 */
public class Utilities {
	
	public static <T, U> Map<T, U> cloneMap(Map<T, U> map) {
		Map<T, U> map2 = new HashMap<T, U>();
		Set<Entry<T, U>> entries = map.entrySet();
		for (Entry<T, U> e : entries) {
			map2.put(e.getKey(), e.getValue());
		}
		return map2;
	}

	public static <T> void printList(List<T> list) {
		for (T element : list) {
			System.out.println(element);
		}
	}

	public static List<String> list(String str1, String str2) {
		return Arrays.asList(str1, str2);
	}

}
