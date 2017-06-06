package edu.isnap.eval.export;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.BJCSolutions2017;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.elements.BlockDefinition;
import edu.isnap.parser.elements.CallBlock;
import edu.isnap.parser.elements.Code;
import edu.isnap.parser.elements.Code.Accumulator;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.parser.elements.util.Canonicalization;

public class JsonAST {

	public static void main(String[] args) throws IOException {
//		Collection<AssignmentAttempt> attempts =
//				Fall2016.GuessingGame1.load(Mode.Use, false, true,
//						new SnapParser.LikelySubmittedOnly()).values();
//
//		for (AssignmentAttempt attempt : attempts) {
//			Snapshot submitted = attempt.submittedSnapshot;
//			System.out.println(toJSON(submitted).toString(2));
//			break;
//		}

		for (Assignment assignment : BJCSolutions2017.All) {
			Snapshot snapshot = Snapshot.parse(new File(assignment.templateFileBase() + ".xml"));
			JSONObject json = toJSON(snapshot);
			String jsonString = json.toString(2);
			System.out.println(jsonString);
			write(assignment.analysisDir() + ".json", jsonString);
			write(assignment.analysisDir() + ".txt",
					SimpleNodeBuilder.toTree(snapshot, true).prettyPrint());
		}
	}

	private static void write(String path, String text) throws FileNotFoundException {
		File file = new File(path);
		file.getParentFile().mkdirs();
		PrintWriter writer = new PrintWriter(file);
		writer.println(text);
		writer.close();
	}

	public static JSONObject toJSON(Code code) {
		JSONObject object = new JSONObject();
		List<JSONObject> children = new LinkedList<>();


		String type = code.type();
		String value = code.name(false);

		if (type.equals("snapshot")) type = "Snap!shot";

		if (code instanceof CallBlock) {
			CallBlock callBlock = (CallBlock) code;
			if (callBlock.isCustom) {
				type = BlockDefinition.getCustomBlockCall(callBlock.name);
			} else {
				type = callBlock.name;
			}
			object.put("type", type);
		}

		object.put("type", type);
		if (!type.equals(value)) object.put("value", value);

		code.addChildren(false, new Accumulator() {
			@Override
			public void addVariables(List<String> vars) {
				for (String var : vars) {
					children.add(variableToJSON(var));
				}
			}

			@Override
			public void add(Iterable<? extends Code> codes) {
				for (Code code : codes) add(code);
			}

			@Override
			public void add(Canonicalization canon) { }

			@Override
			public void add(Code code) {
				children.add(toJSON(code));
			}
		});

		if (children.size() > 0) {
			JSONObject childMap = new JSONObject();
			for (int i = 0; i < children.size(); i++) {
				childMap.put(String.valueOf(i), children.get(i));
			}
			object.put("children", childMap);
		}

		return object;
	}

	public static JSONObject variableToJSON(String value) {
		JSONObject object = new JSONObject();
		object.put("type", "var");
		object.put("value", value);
		return object;
	}

	// TODO: What about actual canonicalizations..? These won't match
	@Deprecated
	public static JSONObject toJSON(Node node, Node nodeCanon) {
		JSONObject obj = new JSONObject();
		String type = nodeCanon.type();
		if (type.equals("snapshot")) type = "Snap!shot";
		obj.put("type", type);

		String value = node.type();
		if (!value.equals(type)) {
			obj.put("value", node.type());
		}

		if (node.children.size() > 0) {
			if (node.children.size() != nodeCanon.children.size()) {
				System.out.println(node.parent.prettyPrint());
				System.out.println(nodeCanon.parent.prettyPrint());
			}
			JSONObject children = new JSONObject();
			for (int i = 0; i < node.children.size(); i++) {
				JSONObject child = toJSON(node.children.get(i), nodeCanon.children.get(i));
				children.put(String.valueOf(i), child);
			}
			obj.put("children", children);
		}

		return obj;

	}

}
