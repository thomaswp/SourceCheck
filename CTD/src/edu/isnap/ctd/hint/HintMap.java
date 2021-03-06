package edu.isnap.ctd.hint;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import edu.isnap.ctd.graph.vector.IndexedVectorState;
import edu.isnap.ctd.graph.vector.VectorGraph;
import edu.isnap.ctd.graph.vector.VectorState;
import edu.isnap.hint.HintConfig;
import edu.isnap.node.Node;
import edu.isnap.node.Node.Action;

/**
 * Class for handling the core logic of the CTD algorithm.
 */
@SuppressWarnings("deprecation")
public class HintMap {

	public final HintConfig config;

	protected final HashMap<Node, VectorGraph> map = new HashMap<>();

	public HintConfig getHintConfig() {
		return config;
	}

	@SuppressWarnings("unused")
	private HintMap() {
		this(null);
	}

	public HintMap(HintConfig config) {
		this.config = config;
	}

	/**
	 * Gets the root path for the given Node, which contains only the nodes in the root path from
	 * this given node to its root. The Node returned is the end of the path, not the root.
	 */
	public static Node toRootPath(Node node) {
		if (node == null) return null;

		Node parent = toRootPath(node.parent);
		String type = node.type();
		Node child = node.constructNode(parent, type, null, null);
		if (parent != null) parent.children.add(child);

		return child;
	}

	public void clear() {
		map.clear();
	}

	public void addEdge(Node from, Node to) {
		// The from Node can be null for new nodes, and is the assumed empty
		VectorState fromState = from == null ?
				VectorState.empty() : getVectorState(from);
		VectorState toState = getVectorState(to);
		// Don't include loops
		if (fromState.equals(toState)) return;
		getGraph(to, true).addEdge(fromState, toState);
	}

	public void addVertex(Node node, double perc) {

	}

	public VectorGraph getGraph(Node node) {
		return getGraph(node, false);
	}

	private VectorGraph getGraph(Node node, boolean addIfMissing) {
		Node rootPathChild = toRootPath(node);
		Node rootPath = rootPathChild.root();
		VectorGraph graph = map.get(rootPath);
		if (addIfMissing && graph == null) {
			graph = new VectorGraph(rootPathChild);
			map.put(rootPath, graph);
		}
		return graph;
	}

	public static VectorState getVectorState(Node node) {
		return new VectorState(getChildren(node));
	}

	private static List<String> getChildren(Node node) {
		List<String> children = new ArrayList<>();
		if (node == null) return children;
		for (Node child : node.children) {
			// Not sure why this was here, but since it's use with node.index(), it needs to
			// include all children so the list length matches the node's number of children
//			if ("null".equals(child.type())) continue;
			children.add(child.type());
		}
		return children;
	}

	public HintMap instance() {
		return new HintMap(config);
	}

	public void setSolution(Node solution) {
		solution.recurse(new Action() {
			@Override
			public void run(Node item) {
				VectorGraph graph = getGraph(item, true);
				VectorState children = getVectorState(item);
				// TODO: find a more elegant solution that doesn't involve this awkward cast
				if (!((Set<VectorState>) graph.vertices()).contains(children)) {
					graph.addVertex(children);
				}
				graph.setGoal(children, getContext(item));
			}
		});
	}

	public IndexedVectorState getContext(Node item) {
		return getContext(item, config.maxContextSiblings);
	}

	private IndexedVectorState getContext(Node item, int maxLength) {
		Node contextChild = item;
		while (contextChild.parent != null &&
				config.isBadContext(contextChild.parent.type())) {
			contextChild = contextChild.parent;
		}
		int index = contextChild.index();
		return new IndexedVectorState(getChildren(contextChild.parent), index, maxLength);
	}

	public void finish() {
		for (VectorGraph graph : map.values()) {
			graph.prune(config.pruneNodes);
		}

		for (Node key : map.keySet()) {
			VectorGraph graph = map.get(key);
			if (!graph.hasGoal()) continue;
			Node parent = key.copy();
			// first go down the root path
			while (parent.children.size() == 1) {
				parent = parent.children.get(0);
			}

			// Record the type at the end of the root path
			String type = parent.type();
			parent = parent.parent;
			// then go back up at least one, possible more if there's a bad context
			while (parent != null && config.isBadContext(parent.type())) {
				type = parent.type();
				parent = parent.parent;
			}
			if (parent == null) continue;
			parent.children.clear();

			VectorGraph parentGraph = map.get(parent.root());
			int clusterCount = parentGraph.getMedianPositiveChildCountInGoals(type);
			graph.setClusters(clusterCount);
		}

		for (VectorGraph graph : map.values()) {
			graph.generateAndRemoveEdges(config.maxEdgeAddDistance, config.maxEdgeDistance);
			graph.bellmanBackup(config.pruneGoals);
		}
	}

	public void addMap(HintMap hintMap) {
		HashMap<Node,VectorGraph> addMap = hintMap.map;
		for (Node rootpath : addMap.keySet()) {
			VectorGraph graph = addMap.get(rootpath);
			VectorGraph myGraph = map.get(rootpath);
			if (myGraph == null) {
				myGraph = new VectorGraph(graph.rootPathEnd);
				map.put(rootpath, myGraph);
			}
			myGraph.addGraph(graph, true);
		}
	}

	/**
	 * Saves graphs and other debugging files to the given directory for this HintGenerator.
	 * @param rootDir The root directory in which to save the files.
	 * @param minVertices The minimum number of vertices a graph must have to be saved.
	 * @throws FileNotFoundException
	 */
	public void saveGraphs(String rootDir, int minVertices)
			throws FileNotFoundException {
		for (Node node : map.keySet()) {
			VectorGraph graph = map.get(node);
			if (graph.nVertices() < minVertices) continue;
			if (!graph.hasGoal()) continue;

			graph.bellmanBackup(config.pruneGoals);
			Node child = node;
			String dir = rootDir;
			while (child.children.size() > 0) {
				dir += child.type().replaceAll("\\W+", "_") + "/";
				child = child.children.get(0);
			}
			new File(dir).mkdirs();
			File file = new File(dir, child.type().replaceAll("\\W+", "_"));

			graph.export(new PrintStream(new FileOutputStream(file + ".graphml")), true,
					0, false, true, false);
			graph.exportGoals(new PrintStream(file + ".txt"));
		}
	}

}
