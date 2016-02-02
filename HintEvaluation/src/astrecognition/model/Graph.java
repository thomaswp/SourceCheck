package astrecognition.model;

import java.util.Collection;

public abstract class Graph implements Comparable<Graph> {

	@Override
	public abstract int compareTo(Graph graph);
	
	public abstract boolean isSink();
	public abstract Collection<Graph> getConnections();
	public abstract String getLabel();
	public abstract String getUniqueLabel();
	public abstract String toString();
	public abstract int getTotalNodeCount();
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Graph) {
			return this.compareTo((Graph) obj) == 0;
		}
		return super.equals(obj);
	}

}
