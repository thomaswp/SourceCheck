package edu.isnap.ctd.util;

public class Tuple<T1,T2> {
	public T1 x;
	public T2 y;
	
	public Tuple(T1 x, T2 y) {
		this.x = x;
		this.y = y;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Tuple<?,?>) {
			Tuple<?,?> tuple = (Tuple<?, ?>) obj;
			if (x == null) { 
				if (tuple.x != null) return false; 
			} else if (!x.equals(tuple.x)) return false;
			if (y == null) {
				if (tuple.y != null) return false;
			} else if (!y.equals(tuple.y)) return false;
			return true;
		}
		return super.equals(obj);
	}
	
	@Override
	public int hashCode() {
		int hash = 1;
		hash = hash * 31 + (x == null ? 0 : x.hashCode());
		hash = hash * 31 + (y == null ? 0 : y.hashCode());
		return hash;
	}
	
	@Override
	public String toString() {
		return "{" + x + "," + y + "}";
	}
}