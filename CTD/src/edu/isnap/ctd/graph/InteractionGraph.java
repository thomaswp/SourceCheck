package edu.isnap.ctd.graph;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class InteractionGraph<T> extends Graph<T, Void> {

	public void addEdge(T from , T to) {
		addEdge(from, to, null);
	}

	public void bellmanBackup() {
		bellmanBackup(1);
	}

	protected double getGoalValue(Vertex<T> vertex) {
		// The scale is arbitrary, but we start with a larger number because other things will
		// may scale it down, and it's nice not to go < 1
		return vertex.goalCount() * 100;
	}

	public void bellmanBackup(int minGoalCount) {
		calculateProbabilities();
		for (Vertex<T> v : vertexMap.values()) {
			if (v.goalCount() >= minGoalCount && v.data != null) {
				v.bValue = getGoalValue(v);
			} else {
				v.bValue = 0;
			}
		}
		int i;
		for (i = 0; i < 1000; i++) {
			boolean updated = false;
			for (Vertex<T> v : vertexMap.values()) {
				updated |= updateNode(v, minGoalCount);
			}
			if (!updated) break;
		}
		if (i == 1000) System.err.println("Maxed bellman backup");
		setBest();
	}

	private void setBest() {
		for (Vertex<T> v : vertexMap.values()) {
			if (!fromMap.containsKey(v.data)) continue;
			Edge<T,?> best = null;
			double bestValue = v.bValue;
			for (Edge<T,?> e : fromMap.get(v.data)) {
				if (e.isLoop()) continue;
				double value = vertexMap.get(e.to).bValue;
				if (value > bestValue) {
					bestValue = value;
					best = e;
				}
			}
			if (best != null) best.bBest = true;
		}
	}

	private void calculateProbabilities() {
		for (T node : vertices) {
			if (!fromMap.containsKey(node)) continue;
			double outWeight = outWeight(node, true, false);
			for (Edge<T,?> e : fromMap.get(node)) {
				if (e.isLoop()) continue;
				e.bRelativeWeight = e.weight / outWeight;
//				e.bR = e.data.error() ? -5 : -1;
				e.bBest = false;
			}
		}
	}

	private boolean updateNode(Vertex<T> v, int minGoalCount) {
		if (!fromMap.containsKey(v.data)) return false;
		double value = v.bValue;
		double newValue = 0;
		boolean counted = false;
		for (Edge<T,?> edge : fromMap.get(v.data)) {
			if (edge.isLoop()) continue;
			counted = true;
			// Note: e.bR is always 0; we have no reward values on actions
			newValue += edge.bRelativeWeight * (0 + 0.99 * vertexMap.get(edge.to).bValue);
		}
		if (counted) {
			v.bValue = Math.round(newValue * 64) / 64.0;
		}
		if (v.goalCount() >= minGoalCount) {
			// Legitimate goal states should never drop below their goal value
			v.bValue = Math.max(v.bValue, getGoalValue(v));
		}
		return value != v.bValue;
	}

	public void export(PrintStream ps, boolean showLoops, int prune, boolean colorEdges,
			boolean yEd, boolean useValueForArrows) {

		ps.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		ps.print("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" "
				+ "xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\" "
				+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
		if (yEd) {
				ps.print("xmlns:y=\"http://www.yworks.com/xml/graphml\" "
				+ "xmlns:yed=\"http://www.yworks.com/xml/yed/3\"");
		}
		ps.println(">");
		printAttr(ps, "weightE", "edge", "weight", "int");
		printAttr(ps, "weightN", "node", "weight", "int");
		printAttr(ps, "error", "edge", "error", "int");
		printAttr(ps, "goal", "node", "goal", "int");
		printAttr(ps, "descE", "edge", "description", "string");
		printAttr(ps, "descN", "node", "description", "string");
		printAttr(ps, "prob", "edge", "probability", "double");
		printAttr(ps, "value", "node", "value", "double");
		printAttr(ps, "best", "edge", "best", "int");
//		printAttr(ps, "ideal", "node", "ideal", "int");
//		printAttr(ps, "start", "node", "start", "int");

		if (yEd) {
			ps.println("<key for='node' id='graphics' yfiles.type='nodegraphics'/>");
			ps.println("<key for='edge' id='edges' yfiles.type='edgegraphics'/>");
		}

		Set<T> ignoreNs = new HashSet<>();
		for (Vertex<T> v : vertexMap.values()) {
			if (v.weight() < prune) ignoreNs.add(v.data);
		}

		ps.println("<graph id='G' edgedefault='directed'>");

		for (T state : vertices()) {
			if (ignoreNs.contains(state)) continue;

			int inWeight = inWeight(state, false, true), outWeight = outWeight(state, false, true);

			Vertex<T> vertex = vertexMap.get(state);

			ps.printf("<node id='%s'>", state.hashCode());
			ps.printf("<data key='descN'><![CDATA[%s]]></data>", state.toString());
			ps.printf("<data key='weightN'>%d</data>", vertex.weight());
			ps.printf("<data key='value'>%.04f</data>", vertex.bValue);
			ps.printf("<data key='goal'>%d</data>", vertex.goalCount());

			if (yEd) {
				String color = "#888888";

				ps.print("<data key='graphics'>");
				ps.print("<y:ShapeNode>");
//				ps.printf("<y:NodeLabel>%d</y:NodeLabel>", n++);
				ps.printf("<y:NodeLabel fontSize=\"9\">%s</y:NodeLabel>", state.toString());
				ps.printf("<y:Fill color='%s' transparent='false'/>", color);

				int exit = inWeight - outWeight;

				String borderColor = "#000000";
				double borderWidth = 1;
				if (vertex.goalCount() > 0) {
					borderColor = "#00DD00";
					borderWidth = Math.log(Math.max(1, vertex.goalCount())) + 1;
				} else if (exit > 0) {
					borderColor = "#DD0000";
					borderWidth = Math.log(Math.max(1, exit)) + 1;
				}

				ps.printf("<y:BorderStyle color='%s' type='line' width='%.04f'/>", borderColor, borderWidth);
				ps.print("</y:ShapeNode>");
				ps.print("</data>");
			}

			ps.println("</node>");
		}


		int i = 0;
		for (Graph.Edge<T,Void> edge : edges()) {
			if (!showLoops && edge.from.equals(edge.to)) continue;
			if (edge.isLoop() && edge.weight <= prune) continue;
			if (edge.synthetic) continue;
			if (ignoreNs.contains(edge.to) || ignoreNs.contains(edge.from)) continue;

			String color = "#000000";

			Comparator<Edge<?,?>> edgeComparator;
			double minValue;
			if (useValueForArrows) {
				edgeComparator = Comparator.comparing(e -> vertexMap.get(e.to).bValue);
				minValue = vertexMap.get(edge.from).bValue;
			} else {
				edgeComparator = Comparator.comparing(e -> e.weight);
				minValue = 0;
			}

			boolean best = fromMap.containsKey(edge.from) && fromMap.get(edge.from).stream()
					.filter(e -> !ignoreNs.contains(e.to) && vertexMap.get(e.to).bValue >= minValue)
					.max(edgeComparator)
					.orElse(null) == edge;

			String id = "" + i++;
			ps.printf("<edge id='%s' source='%s' target='%s'>", id, edge.from.hashCode(), edge.to.hashCode());
			ps.printf("<data key='weightE'>%d</data>", edge.weight);
			ps.printf("<data key='prob'>%.04f</data>", edge.bRelativeWeight);
			ps.printf("<data key='best'>%d</data>", best ? 1 : 0);

			if (yEd) {
				ps.printf("<data key='edges'><y:PolyLineEdge><y:LineStyle color='%s' type='%s' width='%.02f'/>"
						+ "<y:Arrows source='none' target='%s'/></y:PolyLineEdge></data>",
						color,
						edge.synthetic ? "dashed" : "line",
						Math.log(edge.weight) + 1,
						best ? "standard" : "transparent_circle");
			}

			ps.println("</edge>");
		}

		ps.println("</graph>");
		ps.println("</graphml>");
		ps.close();
	}

	private static void printAttr(PrintStream ps, String id, String target, String name, String type) {
		ps.printf("<key id='%s' for='%s' attr.name='%s' attr.type='%s'/>\n",
				id, target, name, type);
	}
}
