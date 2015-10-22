package com.snap.graph.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Graph<N,E> {
	
	protected final Set<N> vertices = new HashSet<N>();
	protected final Set<Edge<N,E>> edges = new HashSet<Edge<N,E>>();
	protected final HashMap<N, List<Edge<N,E>>> fromMap = new HashMap<N, List<Edge<N,E>>>();
	protected final HashMap<N, List<Edge<N,E>>> toMap = new HashMap<N, List<Edge<N,E>>>();
	protected final HashMap<N, Vertex<N>> vertexMap = new HashMap<N, Vertex<N>>();
	
	private boolean hasGoal;
	
	public boolean hasGoal() {
		return hasGoal;
	}
	
	public Iterable<N> vertices() {
		return vertices;
	}
	
	public Iterable<Edge<N,E>> edges() {
		return edges;
	}
	
	public boolean addVertex(N v) {
		if (vertices.add(v)) {
			vertexMap.put(v, new Vertex<N>(v));
			return true;
		} else {
			vertexMap.get(v).weight++;
		}
		return false;
	}
	
	public boolean addVertex(N v, int color, double colorWeight) {
		return addVertex(v);
	}
	
	public int nVertices() {
		return vertices.size();
	}
	
	public boolean setGoal(N node, boolean goal) {
		Vertex<N> v = vertexMap.get(node);
		if (v == null) return false;
		hasGoal = true;
		v.goalCount++;
		return true;
	}
	
	public boolean hasEdge(N from, N to) {
		List<Edge<N, E>> list = fromMap.get(from);
		return list != null && list.contains(to);
	}
	
	public boolean addEdge(N from , N to, E edgeData) {
		return addAndGetEdge(from, to, edgeData) != null;
	}
	
	protected Edge<N,E> addAndGetEdge(N from , N to, E edgeData) {
		addVertex(from);
		addVertex(to);
		Edge<N,E> edge = new Edge<N,E>(from, to, edgeData);
		boolean added = edges.add(edge);
		if (added) {
			List<Edge<N,E>> edges = fromMap.get(from);
			if (edges == null) {
				fromMap.put(from, edges = new ArrayList<Edge<N,E>>());
			}
			edges.add(edge);
			
			edges = toMap.get(to);
			if (edges == null) {
				toMap.put(to, edges = new ArrayList<Edge<N,E>>());
			}
			edges.add(edge);
			return edge;
		} else {
			List<Edge<N,E>> edges = fromMap.get(from);
			for (Edge<N,E> e : edges) {
				if (e.equals(edge)) {
					e.weight++;
				}
			}	
		}
		return null;
	}
	
	public int outWeight(N vertex, boolean ignoreLoops) {
		List<Edge<N,E>> vertices = fromMap.get(vertex);
		if (vertices == null) return 0;
		int w = 0;
		for (Edge<N,E> edge : vertices) {
			if (ignoreLoops && edge.isLoop()) continue;
			w += edge.weight;
		}
		return w;
	}
	
	public int inWeight(N vertex, boolean ignoreLoops) {
		List<Edge<N, E>> vertices = toMap.get(vertex);
		if (vertices == null) return 0;
		int w = 0;
		for (Edge<N,E> edge : vertices) {
			if (ignoreLoops && edge.isLoop()) continue;
			w += edge.weight;
		}
		return w;
	}
	
	public void prune(int minVertexWeight) {
		List<N> toRemove = new ArrayList<N>();
		for (N n : vertices) {
			if (vertexMap.get(n).weight < minVertexWeight) {
				toRemove.addAll(toRemove);
			}
		}
		for (N n : toRemove) {
			removeVertex(n);
		}
	}
	
	public void removeVertex(N n) {
		vertices.remove(n);
		vertexMap.remove(n);

		List<Edge<N, E>> from = fromMap.get(n);
		if (from != null) edges.removeAll(from);
		fromMap.remove(n);
		
		List<Edge<N, E>> to = toMap.get(n);
		if (to != null) edges.removeAll(to);
		toMap.remove(n);
	}
	
	public static class Vertex<N> {
		public final N data;
		private int goalCount;
		private int weight = 1;
		protected double bValue;

		public int weight() {
			return weight;
		}
		
		public int goalCount() {
			return goalCount;
		}
		
		@SuppressWarnings("unused")
		private Vertex() {
			this(null);
		}
		
		public Vertex(N data) {
			this.data = data;
		}
	}
	
	public static class Edge<N,E> implements Comparable<Edge<N,E>> {
		public final N from, to;
		public final E data;
		public int weight = 1;
		public boolean synthetic;
		protected double bRelativeWeight, bR;
		protected boolean bBest;
		
		@SuppressWarnings("unused")
		private Edge() {
			this(null, null, null);
		}
		
		public Edge(N from, N to, E data) {
			this.from = from;
			this.to = to;
			this.data = data;
		}
		
		public boolean isLoop() {
			return from == null ? to == null : from.equals(to);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Graph.Edge)) return false;
			@SuppressWarnings("unchecked")
			Edge<N,E> e = (Edge<N,E>) obj;
			return (from == null ? e.from == null : from.equals(e.from)) &&
					(to == null ? e.to == null : to.equals(e.to)) &&
					(data == null ? e.data == null : data.equals(e.data));
		} 
		
		public int hashCode() {
			int v = 1;
			v *= 31;
			if (from != null) v += from.hashCode();
			v *= 31;
			if (to != null) v += to.hashCode();
			v *= 31;
			if (data != null) v += data.hashCode();
			return v;
		}
		
		public String description() {
			if (from instanceof StringHashable && to instanceof StringHashable) {
				StringHashable sFrom = (StringHashable) from, sTo = (StringHashable) to;
				String cFrom = sFrom.toCanonicalString(), cTo = sTo.toCanonicalString();
				return difference(cFrom, cTo);
			}
			return "?";
		}
		
		private String difference(String from, String to) {
			if (from.length() > to.length()) return "<" + difference(to, from) + ">";
			
			String diff = "";
			int f = 0, t = 0;
			while (f < from.length() && t < to.length()) {
				char fc = from.charAt(f), tc = to.charAt(t);
				if (fc != tc) {
					diff += tc;
				} else {
					f++;
				}
				t++;
			}
			
			if (f != from.length() || t != to.length()) return "?";
			return diff;
		}

		@Override
		public int compareTo(Edge<N,E> o) {
			return Integer.compare(weight, o == null ? Integer.MIN_VALUE : o.weight);
		}
	}
}