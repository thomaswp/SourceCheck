package pqgram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
/**
 * Generic multiset (or bag)
 */
public class Multiset<T> {
	private List<T> items;
	
	public Multiset() {
		this.items = new ArrayList<T>();  
	}

	public void add(T item) {
		this.items.add(item);
	}
	
	public void remove(T item) {
		this.items.remove(item);
	}
	
	public int size() {
		return this.items.size();
	}
	
	public boolean contains(T item) {
		for (T thing : this.items) {
			if (thing.equals(item)) {
				return true;
			}
		}
		return false;
	}
	
	public Collection<T> getAllElements() {
		return this.items;
	}
	
	public T get(int i) {
		return this.items.get(i);
	}
	
	public Multiset<T> clone() {
		Multiset<T> copy = new Multiset<T>();
		
		for (T item : this.items) {
			copy.add(item);
		}
		
		return copy;
	}
	
	public Multiset<T> intersect(Multiset<T> other) {
		Multiset<T> intersection = new Multiset<T>();
		other = other.clone();
		
		for (T item : this.items) {
			if (other.contains(item)) {
				intersection.add(item);
				other.remove(item);
			}
		}
		
		return intersection;
	}
	
	public Multiset<T> difference(Multiset<T> other) {
		Multiset<T> difference = new Multiset<T>();
		
		Multiset<T> intersection = this.intersect(other);
		
		for (T item : this.items) {
			if (intersection.contains(item)) {
				intersection.remove(item);
			} else {
				difference.add(item);
			}
		}
		
		return difference;
	}
	
	public Multiset<T> union(Multiset<T> other) {
		Multiset<T> union = new Multiset<T>();
		
		for (T item : this.items) {
			union.add(item);
		}
		
		for (T item : other.getAllElements()) {
			union.add(item);
		}
		
		return union;
	}

}
