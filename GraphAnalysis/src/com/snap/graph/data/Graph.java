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
	
	private transient N lastPathNode;
	
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
	
	
	public void endPath() {
		this.lastPathNode = null;
	}
	
	public Edge<N,E> addPathNode(N n, E edgeData) {
		if (n.equals(lastPathNode)) return null;
		addVertex(n);
		
		Edge<N,E> edge = null;
		if (lastPathNode != null) {
			edge = addOrGetEdge(lastPathNode, n, edgeData);
		}
		
		lastPathNode = n;
		
		return edge;
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

	public boolean isGoal(N node) {
		Vertex<N> vertex = vertexMap.get(node);
		return vertex != null && vertex.goalCount > 0;
	}
	
	public int getGoalCount(N node) {
		Vertex<N> vertex = vertexMap.get(node);
		return vertex != null ? vertex.goalCount : 0;
	}
	
	public boolean hasEdge(N from, N to) {
		List<Edge<N, E>> list = fromMap.get(from);
		if (list == null) return false;
		for (Edge<N,E> edge : list) {
			if (edge.to.equals(to)) return true;
		}
		return false;
	}
	
	public void addEdge(N from , N to, E edgeData) {
		addOrGetEdge(from, to, edgeData);
	}
	
	protected Edge<N,E> addOrGetEdge(N from , N to, E edgeData) {
		if (!vertices.contains(from)) addVertex(from);
		if (!vertices.contains(to)) addVertex(to);
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
					return e;
				}
			}	
		}
		return null;
	}


	public void addGraph(Graph<N,E> graph, boolean atomicWeight) {
		for (Vertex<N> v : graph.vertexMap.values()) {
			addVertex(v.data);
			Vertex<N> myVertex = vertexMap.get(v.data);
			myVertex.goalCount += Math.min(v.goalCount, atomicWeight ? 1 : Integer.MAX_VALUE);
			if (!atomicWeight) myVertex.weight += v.weight - 1;
			if (myVertex.weight < myVertex.goalCount) {
				System.out.println("!");
			}
		}
		for (Edge<N, E> edge : graph.edges) {
			Edge<N, E> myEdge = addOrGetEdge(edge.from, edge.to, edge.data);
			if (!atomicWeight) myEdge.weight += edge.weight - 1;
		}
		hasGoal |= graph.hasGoal;
	}
	
	public int outWeight(N vertex, boolean ignoreLoops, boolean ignoreSynthetic) {
		return getEdgeWeight(ignoreLoops, ignoreSynthetic, fromMap.get(vertex));
	}
	
	public int inWeight(N vertex, boolean ignoreLoops, boolean ignoreSynthetic) {
		return getEdgeWeight(ignoreLoops, ignoreSynthetic, toMap.get(vertex));
	}

	private int getEdgeWeight(boolean ignoreLoops, boolean ignoreSynthetic, List<Edge<N, E>> vertices) {
		if (vertices == null) return 0;
		int w = 0;
		for (Edge<N,E> edge : vertices) {
			if (ignoreLoops && edge.isLoop()) continue;
			if (ignoreSynthetic && edge.synthetic) continue;
			w += edge.weight;
		}
		return w;
	}
	
	public void prune(int minVertexWeight) {
		List<N> toRemove = new ArrayList<N>();
		for (N n : vertices) {
			Vertex<N> vertex = vertexMap.get(n);
			if (vertex.goalCount > 0) continue;
			if (vertex.weight < minVertexWeight) {
				toRemove.add(n);
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
		if (from != null) {
			edges.removeAll(from);
			for (Edge<N, E> edge : from) {
				List<Edge<N, E>> list = toMap.get(edge.to);
				if (list != null) list.remove(edge);
			}
		}
		fromMap.remove(n);
		
		List<Edge<N, E>> to = toMap.get(n);
		if (to != null) {
			edges.removeAll(to);
			for (Edge<N, E> edge : to) {
				List<Edge<N, E>> list = fromMap.get(edge.from);
				if (list != null) list.remove(edge);
			}
		}
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