package com.snap.graph.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Graph<N,E> {
	
	public final Set<N> vertices = new HashSet<N>();
	public final Set<Edge> edges = new HashSet<Graph<N,E>.Edge>();
	public final HashMap<N, List<Edge>> fromMap = new HashMap<N, List<Edge>>();
	public final HashMap<N, List<Edge>> toMap = new HashMap<N, List<Edge>>();
	public final HashMap<N, Vertex> vertexMap = new HashMap<N, Vertex>();
	
	public Iterable<N> vertices() {
		return vertices;
	}
	
	public Iterable<Graph<N,E>.Edge> edges() {
		return edges;
	}
	
	public boolean addVertex(N v) {
		if (vertices.add(v)) {
			vertexMap.put(v, new Vertex(v));
			return true;
		}
		return false;
	}
	
	public boolean addVertex(N v, int color, double colorWeight) {
		boolean result = addVertex(v);
		addWeight(vertexMap.get(v).colorWeights, color, colorWeight);
		return result;
	}
	
	public int nVertices() {
		return vertices.size();
	}
	
	public boolean addEdge(N from , N to, E edgeData, int color, double colorWeight) {
		addVertex(from);
		addVertex(to);
		Edge edge = new Edge(from, to, edgeData);
		boolean added = edges.add(edge);
		if (added) {
			List<Edge> edges = fromMap.get(from);
			if (edges == null) {
				fromMap.put(from, edges = new ArrayList<Graph<N,E>.Edge>());
			}
			edges.add(edge);
			
			edges = toMap.get(to);
			if (edges == null) {
				toMap.put(to, edges = new ArrayList<Graph<N,E>.Edge>());
			}
			edges.add(edge);
		}
		List<Edge> edges = fromMap.get(from);
		for (Edge e : edges) {
			if (e.equals(edge)) {
				if (!added) e.weight++;
				addWeight(e.colorWeights, color, colorWeight);
				break;
			}
		}
		return added;
	}
	
	public void addGraph(Graph<N, E> graph) {
		for (Vertex v : graph.vertexMap.values()) {
			for (Integer color : v.colorWeights.keySet()) {
				addVertex(v.data, color, v.colorWeights.get(color));
			}
			if (v.colorWeights.size() == 0) {
				addVertex(v.data);
			}
		}
		for (Edge e : graph.edges) {
			for (Integer color : e.colorWeights.keySet()) {
				addEdge(e.from, e.to, e.data, color, e.colorWeights.get(color));
			}
		}
	}
	
	private void addWeight(HashMap<Integer, Double> colorWeights, int color, double weight) {
		double value = 0;
		if (colorWeights.containsKey(color)) {
			value = colorWeights.get(color);
		}
		colorWeights.put(color, value + weight);
	}
	
	public int vertexColor(N v) {
		return color(vertexMap.get(v).colorWeights);
	}
	
	public boolean hasMultipleColors(N v) {
		return vertexMap.get(v).colorWeights.size() > 1;
	}
	
	private int color(HashMap<Integer, Double> colorWeights) {
		if (colorWeights.size() == 0) return 0x999999;
		double r = 0, b = 0, g = 0;
		double weightTotal = 0;
		for (int color : colorWeights.keySet()) {
			double weight = colorWeights.get(color);
			r += ((color & 0x00ff0000) >> 16) * weight;
			g += ((color & 0x0000ff00) >> 8) * weight;
			b += (color & 0x000000ff) * weight;
			weightTotal += weight;
		}
		return ((int)(r / weightTotal) << 16) |
				((int)(g / weightTotal) << 8) |
				(int)(b / weightTotal);
	}
	
	public int outWeight(N vertex, boolean ignoreLoops) {
		List<Graph<N, E>.Edge> vertices = fromMap.get(vertex);
		if (vertices == null) return 0;
		int w = 0;
		for (Edge edge : vertices) {
			if (ignoreLoops && edge.isLoop()) continue;
			w += edge.weight;
		}
		return w;
	}
	
	public int inWeight(N vertex, boolean ignoreLoops) {
		List<Graph<N, E>.Edge> vertices = toMap.get(vertex);
		if (vertices == null) return 0;
		int w = 0;
		for (Edge edge : vertices) {
			if (ignoreLoops && edge.isLoop()) continue;
			w += edge.weight;
		}
		return w;
	}
	
	
	public class Vertex {
		public final N data;
		protected double bValue;

		public final HashMap<Integer, Double> colorWeights = new HashMap<Integer, Double>();
		
		public Vertex(N data) {
			this.data = data;
		}
	}
	
	public class Edge implements Comparable<Edge> {
		public final N from, to;
		public final E data;
		public int weight = 1;
		public final HashMap<Integer, Double> colorWeights = new HashMap<Integer, Double>();
		protected double bRelativeWeight, bR;
		protected boolean bBest;
		
		public Edge(N from, N to, E data) {
			this.from = from;
			this.to = to;
			this.data = data;
		}
		
		public int color() {
			return Graph.this.color(colorWeights);
		}
		
		public boolean isLoop() {
			return from == null ? to == null : from.equals(to);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Graph.Edge)) return false;
			@SuppressWarnings("unchecked")
			Edge e = (Edge) obj;
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
		public int compareTo(Edge o) {
			return Integer.compare(weight, o == null ? Integer.MIN_VALUE : o.weight);
		}
	}
}