package com.snap.graph.data;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

public class NodeGraph extends Graph<Node, Void> {
	
	public boolean addEdge(Node from , Node to) {
		return addEdge(from, to, null, 0, 1);
	}
	
	public void export(PrintStream ps, boolean showLoops, int prune, boolean colorEdges, boolean yEd) {
				
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
		printAttr(ps, "ideal", "node", "ideal", "int");
		printAttr(ps, "start", "node", "start", "int");
		
		if (yEd) {
			ps.println("<key for='node' id='graphics' yfiles.type='nodegraphics'/>");
			ps.println("<key for='edge' id='edges' yfiles.type='edgegraphics'/>");
		}
		
		Set<Node> ignoreNs = new HashSet<Node>();
		for (Node state : vertices()) {
			int inWeight = inWeight(state, true), outWeight = outWeight(state, true);
			if (inWeight <= prune && outWeight <= prune) ignoreNs.add(state);
		}
		
		ps.println("<graph id='G' edgedefault='directed'>");			
		
		int n = 1;
		for (Node state : vertices()) {
			if (ignoreNs.contains(state)) continue;
			
			int inWeight = inWeight(state, false), outWeight = outWeight(state, false);
			
			String color = String.format("#%x", vertexColor(state));
//			if (state.quit()) color = "#DD0000";

			
			ps.printf("<node id='%s'>", state.hexHash());
			ps.printf("<data key='descN'><![CDATA[%s]]></data>", state.toDisplayString());
			ps.printf("<data key='weightN'>%d</data>", inWeight(state, true));
			ps.printf("<data key='value'>%.04f</data>", vertexMap.get(state).bValue);
			
			if (yEd) {
				ps.print("<data key='graphics'>");
				ps.print("<y:ShapeNode>");
				ps.printf("<y:NodeLabel>%d</y:NodeLabel>", n++);
				ps.printf("<y:Fill color='%s' transparent='false'/>", color);

				int exit = inWeight - outWeight;
				
				String borderColor = "#000000";
				double borderWidth = 1; 
				if (exit > 0) {
					borderColor = "#DD0000";
					borderWidth = Math.log(Math.max(1, exit)) + 1;
				}

				ps.printf("<y:BorderStyle color='%s' type='line' width='%.04f'/>", borderColor, borderWidth);
				if (hasMultipleColors(state)) ps.print("<y:Shape type=\"roundrectangle\"/>");
				ps.print("</y:ShapeNode>");
				ps.print("</data>");
			}
			
			ps.println("</node>");
		}
		

		int i = 0;
		for (Graph.Edge<Node,Void> edge : edges()) {
			if (!showLoops && edge.from.equals(edge.to)) continue;
			if (edge.isLoop() && edge.weight <= prune) continue;
			if (ignoreNs.contains(edge.to) || ignoreNs.contains(edge.from)) continue;
			
			String color = "#000000";
			
			if (colorEdges) {
				color = String.format("#%x", edge.color());
			}
			
			String id = "" + i++;
			ps.printf("<edge id='%s' source='%s' target='%s'>", id, edge.from.hexHash(), edge.to.hexHash());
			ps.printf("<data key='weightE'>%d</data>", edge.weight);
			ps.printf("<data key='prob'>%.04f</data>", edge.bRelativeWeight);
			ps.printf("<data key='best'>%d</data>", edge.bBest ? 1 : 0);
			
			if (yEd) {
				ps.printf("<data key='edges'><y:PolyLineEdge><y:LineStyle color='%s' type='%s' width='%.02f'/>"
						+ "<y:Arrows source='none' target='%s'/></y:PolyLineEdge></data>",
						color,
						"line",
						Math.log(edge.weight) + 1,
						"standard");
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
