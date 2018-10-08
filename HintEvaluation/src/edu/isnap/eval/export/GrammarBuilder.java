package edu.isnap.eval.export;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.aggregate.CSC200;
import edu.isnap.node.INode;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.BlockDefinition;

public class GrammarBuilder {

	public static void main(String[] args) {
		System.out.println(
				GrammarBuilder.fromDataset("Snap!", CSC200.instance).toJSON().toString(4));
	}

	public final String domain;

	private final Set<String> rootTypes = new HashSet<>();
	private final Map<String, Type> types = new HashMap<>();
	private final Map<String, Set<String>> categories;

	private Set<String> allChildren = new HashSet<>();

	public GrammarBuilder(String domain, Map<String, Set<String>> categories) {
		this.domain = domain;
		this.categories = categories;
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

		JSONObject cats = new JSONObject();
		categories.keySet().forEach(cat -> cats.put(cat, new JSONArray(categories.get(cat))));
		grammar.put("categories", cats);

		grammar.put("special_types", new JSONArray(allChildren));

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
		GrammarBuilder builder = new GrammarBuilder(domain, readSnapCategories());
		for (Assignment assignment : assignments) {
			assignment.load(Mode.Use, true).values().stream()
			.flatMap(attempt -> attempt.rows.rows.stream())
			.map(action ->  JsonAST.toAST(action.snapshot, true))
			.forEach(node -> builder.add(node));
		}
		return builder;
	}

	private static Map<String, Set<String>> readSnapCategories() {
		Map<String, Set<String>> categories = new HashMap<>();
		try {
			String source = new String(Files.readAllBytes(new File("blocks.json").toPath()));
			JSONObject blocks = new JSONObject(source);

			Iterator<?> keys = blocks.keys();
			while (keys.hasNext()) {
				String type = (String) keys.next();
				JSONObject def = blocks.getJSONObject(type);
				String category = def.getString("type");
				if ("predicate".equals(category)) category = "reporter";
				category = category.toUpperCase();

				Set<String> set = categories.get(category);
				if (set == null) {
					categories.put(category, set = new HashSet<>());
				}
				if (BlockDefinition.isImported(type)) {
					type = BlockDefinition.getCustomBlockCall(type, true);
				}
				set.add(type);
			}

			Set<String> reporters = categories.get("REPORTER");
			reporters.add("literal");
			reporters.add("var");
			reporters.add("reportTrue");
			reporters.add("reportFalse");
			reporters.add("evaluateCustomBlock");

			Set<String> commands = categories.get("COMMAND");
			commands.add("evaluateCustomBlock");

//			for (String category : categories.keySet()) {
//				System.out.println("-----+ " + category + " +------");
//				categories.get(category).forEach(System.out::println);
//			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		return categories;
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

	private class Input {
		public final Set<String> types = new HashSet<>();
		public int count = 0;

		public void addType(String type) {
			List<String> cats = categories.keySet().stream()
				.filter(cat -> categories.get(cat).contains(type))
				.collect(Collectors.toList());

			if (cats.size() == 1) {
				types.add(cats.get(0));
			} else {
				types.add(type);
				allChildren.add(type);
			}
			count++;
		}
	}
}
