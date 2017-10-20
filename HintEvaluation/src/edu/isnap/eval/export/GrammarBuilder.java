package edu.isnap.eval.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.ctd.graph.INode;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.aggregate.CSC200;
import edu.isnap.parser.Store.Mode;

public class GrammarBuilder {

	public static void main(String[] args) {
		System.out.println(
				GrammarBuilder.fromDataset("Snap!", CSC200.instance).toJSON().toString(4));
	}

	public final String domain;

	private Set<String> rootTypes = new HashSet<>();
	private Map<String, Type> types = new HashMap<>();

	public GrammarBuilder(String domain) {
		this.domain = domain;
	}

	public void add(INode root) {
		rootTypes.add(root.type());

		INode.recurse(root, node -> {
			Type type = types.get(node.type());
			if (type == null) {
				types.put(node.type(), type = new Type(node.type()));
			}
			type.count++;

			for (int i = 0; i < node.children().size(); i++) {
				INode child = node.children().get(i);
				type.getInput(i).addType(child.type());
			}
		});
	}

	public JSONObject toJSON() {
		JSONObject grammar = new JSONObject();
		grammar.put("grammar_domain", domain);
		grammar.put("root", new JSONArray(rootTypes));

		List<Type> types = new ArrayList<>(this.types.values());
		types.sort((t1, t2) -> -Integer.compare(t1.count, t2.count));

		JSONObject nodeTypes = new JSONObject();
		types.forEach(t -> nodeTypes.put(t.name, t.toJSON()));
		grammar.put("node_types", nodeTypes);

		return grammar;
	}

	public static GrammarBuilder fromDataset(String domain, Dataset dataset) {
		return fromAssignments(domain, dataset.all());
	}

	public static GrammarBuilder fromAssignments(String domain, Assignment... assignments) {
		GrammarBuilder builder = new GrammarBuilder(domain);
		for (Assignment assignment : assignments) {
			assignment.load(Mode.Use, true).values().stream()
			.flatMap(attempt -> attempt.rows.rows.stream())
			.map(action ->  JsonAST.toAST(action.snapshot))
			.forEach(node -> builder.add(node));
		}
		return builder;
	}

	private class Type {
		public final String name;
		public final List<Input> inputs = new ArrayList<>();
		public int count = 0;

		public Type(String name) {
			this.name = name;
		}

		public boolean isFlexible() {
			return inputs.stream().map(i -> i.count).distinct().limit(2).count() > 1;
		}

		public JSONObject toJSON() {
			JSONObject children = new JSONObject();
			boolean isFlexible = isFlexible();
//			object.put("type", name);

//			JSONObject children = new JSONObject();
			children.put("type", isFlexible ? "flexible" : "fixed");

			if (isFlexible) {
				Set<String> types = inputs.stream()
						.flatMap(i -> i.types.stream())
						.collect(Collectors.toSet());
				children.put("permitted_children", new JSONArray(types));
			} else {
				children.put("count", inputs.size());
				for (int i = 0; i < inputs.size(); i++) {
					children.put(String.valueOf(i), new JSONArray(inputs.get(i).types));
				}
			}

//			object.put("children", children);

			return children;
		}

		public Input getInput(int index) {
			while (inputs.size() <= index) inputs.add(new Input());
			return inputs.get(index);
		}
	}

	private static class Input {
		public final Set<String> types = new HashSet<>();
		public int count = 0;

		public void addType(String type) {
			types.add(type);
			count++;
		}
	}
}
