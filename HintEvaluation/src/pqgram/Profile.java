package pqgram;

import astrecognition.model.Graph;

/**
 * Representation of profiles as in the pq-Gram algorithm
 */
public class Profile extends Multiset<Tuple<Graph>> {
	
	public void add(Graph[] tuple) {
		Tuple<Graph> t = new Tuple<Graph>(tuple.length);
		for (int i = 0; i < tuple.length; i++) {
			t.set(i, tuple[i]);
		}
		this.add(t);
	}
	
	public Profile union(Profile other) {
		return makeProfile(super.union(other));
	}
	
	public Profile intersect(Profile other) {
		return makeProfile(super.intersect(other));
	}
	
	public Profile difference(Profile other) {
		return makeProfile(super.difference(other));
	}
	
	@Override
	public Profile clone() {
		Profile i = new Profile();
		for (Tuple<Graph> tup : this.getAllElements()) {
			i.add(tup);
		}
		return i;
	}
	
	private Profile makeProfile(Multiset<Tuple<Graph>> set) {
		Profile i = new Profile();
		
		for (Tuple<Graph> tup : set.getAllElements()) {
			i.add(tup);
		}
		
		return i;
	}
	
	@Override
	public String toString() {
		String str = "";
		for (Tuple<Graph> tup : this.getAllElements()) {
			for (int i = 0; i < tup.length(); i++) {
				str += tup.get(i) + " ";
			}
			str += "\n";
		}
		str += "\n";
		return str;
	}

}
