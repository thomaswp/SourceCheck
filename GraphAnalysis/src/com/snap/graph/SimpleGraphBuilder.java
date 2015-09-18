package com.snap.graph;

import java.util.HashMap;

import org.jgraph.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import com.snap.data.Block;
import com.snap.data.Snapshot;

public class SimpleGraphBuilder {

	public StructGraph buildGraph(Snapshot snapshot) {
		StructGraph graph = new StructGraph();
		
		return graph;
	}

//	protected List<Block> addToStructureGraph(StructGraph graph, List<Block> parents) {
//		if (graph.containsVertex(this)) return list(this);
//		
//		addHeaderToStructureGraph(graph);
//		graph.addVertex(this);
//		for (Block parent : parents) graph.addEdge(parent, this);
//		
//		for (String input : inputs) {
//			new VarBlock(input).addToStructureGraph(graph, list(this));
//		}
//		script.addToStructureGraph(graph, list(this));
//		return list(this);
//	}
//
//	@Override
//	protected List<Block> addToStructureGraph(StructGraph graph, List<Block> parents) {
//		graph.addVertex(this);
//		for (Block parent : parents) graph.addEdge(parent, this);
//		for (Block param : parameters) {
//			param.addToStructureGraph(graph, list(this));
//		}
//		if (isCustom) {
//			Block method = graph.methods.get(name);
//			if (method == null) {
//				System.out.println(name);
//			}
//			return method.addToStructureGraph(graph, list(this));
//		} else if (bodies.size() > 0){
//			ArrayList<Block> tails = new ArrayList<Block>();
//			for (Script script : bodies) {
//				tails.addAll(script.addToStructureGraph(graph, list(this)));
//			}
//			for (Block tail : tails) {
//				switch(name) {
//				case "doUntil":
//				case "doForever":
//				case "doRepeat":
//					if (tail != this) graph.addEdge(tail, this);
//					break;
//				case "doIf":
//					break;
//				case "doIfElse":
//					break;
//				}
//			}
//			return tails;
//		} else {
//			return list(this);
//		}
//	}
//	
//	private void addToGraph(final SimpleDirectedGraph<Code, DefaultEdge> graph, final boolean canon) {
//		graph.addVertex(this);
//		addChildren(canon, new  Accumulator() {
//			@Override
//			public void add(List<String> codes) {
//				for (String code : codes) {
//					add(code);
//				}
//			}
//			
//			@Override
//			public void add(String code) { }
//			
//			@Override
//			public void add(Iterable<? extends Code> codes) {
//				for (Code code : codes) {
//					add(code);
//				}
//			}
//			
//			@Override
//			public void add(Code code) {
//				code.addToGraph(graph, canon);
//				graph.addEdge(Code.this, code);
//			}
//		});
//	}
//
//	@Override
//	protected List<Block> addToStructureGraph(StructGraph graph, List<Block> parents) {
//		graph.addVertex(this);
//		for (Block parent : parents) graph.addEdge(parent, this);
//		for (Block block : list) block.addToStructureGraph(graph, list(this));
//		return list(this);
//	}
//
//	@Override
//	protected List<Block> addToStructureGraph(StructGraph graph, List<Block> parents) {
//		Block block = graph.variables.get(value);
//		if (block == null) {
//			graph.addVertex(this);
//			block = this;
//		}
//		for (Block parent : parents) graph.addEdge(parent, block);
//		return list(block);
//	}
//
//	@Override
//	protected List<Block> addToStructureGraph(StructGraph graph, List<Block> parents) {
//		for (Block block : blocks) {
//			parents = block.addToStructureGraph(graph, parents);
//		}
//		return parents;
//	}
//
//	@Override
//	protected List<Block> addToStructureGraph(StructGraph graph, List<Block> parents) {
//		for (String variable : variables) {
//			new VarBlock(variable).addToStructureGraph(graph, parents);
//		}
//		for (BlockDefinition block : blocks) {
//			block.addHeaderToStructureGraph(graph);
//		}
//		for (BlockDefinition block : blocks) {
//			block.addToStructureGraph(graph, parents);
//		}
//		return stage.addToStructureGraph(graph, parents);
//	}
//
//	@Override
//	protected List<Block> addToStructureGraph(StructGraph graph,
//			List<Block> parents) {
//		for (String variable : variables) {
//			new VarBlock(variable).addToStructureGraph(graph, parents);
//		}
//		for (BlockDefinition method : blocks) {
//			method.addHeaderToStructureGraph(graph);
//		}
//		for (BlockDefinition method : blocks) {
//			method.addToStructureGraph(graph, parents);
//		}
//		for (Script script : scripts) {
//			script.addToStructureGraph(graph, parents);
//		}
//		return list();
//	}
//
//	@Override
//	protected List<Block> addToStructureGraph(StructGraph graph,
//			List<Block> parents) {
//		super.addToStructureGraph(graph, parents);
//		for (Sprite sprite : sprites) {
//			sprite.addToStructureGraph(graph, parents);
//		}
//		return list();
//	}
//
//	@Override
//	protected List<Block> addToStructureGraph(StructGraph graph, List<Block> parents) {
//		Block block = graph.variables.get(name);
//		if (block == null) {
//			graph.addVertex(this);
//			graph.variables.put(name, this);
//			block = this;
//		}
//		for (Block parent : parents) graph.addEdge(parent, block);
//		return list(block);
//	}
//	
//	protected void addHeaderToStructureGraph(StructGraph graph) {
//		if (!graph.methods.containsKey(name)) graph.methods.put(name, this);
//	}
//	
	
	@SuppressWarnings("serial")
	public static class StructGraph extends SimpleGraph<Block, DefaultEdge> {
		
		public final HashMap<String, Block> variables = new HashMap<String, Block>();
		public final HashMap<String, Block> methods = new HashMap<String, Block>();
		
		public StructGraph() {
			super(DefaultEdge.class);
		}
		
	}
}
