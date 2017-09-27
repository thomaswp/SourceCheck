package edu.isnap.eval.export;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.json.JSONObject;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Fall2016;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.BlockDefinition;
import edu.isnap.parser.elements.CallBlock;
import edu.isnap.parser.elements.Code;
import edu.isnap.parser.elements.Code.Accumulator;
import edu.isnap.parser.elements.LiteralBlock;
import edu.isnap.parser.elements.LiteralBlock.Type;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.parser.elements.util.Canonicalization;

public class JsonAST {

	public final static Set<String> values = new TreeSet<>();

	public final static HashMap<String, String> valueReplacements = new HashMap<>();
	static {
		valueReplacements.put("catherines variable", "my variable");
		valueReplacements.put("Answer is Kimberly", "Answer is name");
		valueReplacements.put("Bryson", "name");
		valueReplacements.put("Ellis", "name");
	}

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

		exportDataset(Fall2016.instance, false, Fall2016.LightsCameraAction);
//		exportAssignment(Fall2016.PolygonMaker);

//		for (Assignment assignment : BJCSolutions2017.All) {
//			Snapshot snapshot = Snapshot.parse(new File(assignment.templateFileBase() + ".xml"));
//			exportSnapshot(assignment, snapshot);
//		}
	}

	protected static void exportDataset(Dataset dataset, boolean solutionsOnly,
			Assignment... exclude) throws FileNotFoundException {
		values.clear();
		for (Assignment assignment : dataset.all()) {
			if (!Arrays.asList(exclude).contains(assignment)) {
				if (solutionsOnly) {
					exportAssignmentSolutions(assignment);
				} else {
					exportAssignmentTraces(assignment);
				}
			}
		}
		String dir = solutionsOnly ? "all-solutions" : "all-traces";
		write(dataset.dataDir + "/export/" + dir + "/values.txt", String.join("\n", values));
	}

	protected static void exportAssignmentTraces(Assignment assignment)
			throws FileNotFoundException {
		System.out.println(assignment.name);
		for (AssignmentAttempt attempt : assignment.load(
				Mode.Use, true, true, new SnapParser.LikelySubmittedOnly()).values()) {
			String lastJSON = "";
			System.out.println(attempt.id);
//			String last = "";
			int lastID = -1;
			int order = 0;
			for (AttemptAction action : attempt) {
				if (action.snapshot == null) continue;
				if (action.id < lastID) throw new RuntimeException("PRABLEM");
				lastID = action.id;
				String json = toJSON(action.snapshot).toString(2);
				if (json.equals(lastJSON)) continue;
				lastJSON = json;
				write(String.format("%s/%s/%05d-%05d.json",
						assignment.dir("export/all-traces"), attempt.id, order++, action.id), json);
//				System.out.println(action.id);
//				System.out.println(Diff.diff(last,
//						last = SimpleNodeBuilder.toTree(action.snapshot, true).prettyPrint(), 1));
			}
//			System.out.println(SimpleNodeBuilder.toTree(attempt.submittedSnapshot, true).prettyPrint());
		}
	}

	protected static void exportAssignmentSolutions(Assignment assignment)
			throws FileNotFoundException {
		for (AssignmentAttempt attempt : assignment.load(
				Mode.Use, true, true, new SnapParser.LikelySubmittedOnly()).values()) {
			exportSolution(assignment, attempt.submittedSnapshot);
		}
	}

	protected static void exportSolution(Assignment assignment, Snapshot snapshot)
			throws FileNotFoundException {
		JSONObject json = toJSON(snapshot);
		String jsonString = json.toString(2);
//		System.out.println(jsonString);
		String basePath = assignment.dir("export/all-solutions") + "/" + snapshot.guid;
		write(basePath + ".json", jsonString);
		write(basePath + ".txt", SimpleNodeBuilder.toTree(snapshot, true).prettyPrint());
	}

	public static void write(String path, String text) throws FileNotFoundException {
		File file = new File(path);
		file.getParentFile().mkdirs();
		PrintWriter writer = new PrintWriter(file);
		writer.println(text);
		writer.close();
	}

	public static JSONObject toJSON(Code code) {
		return toAST(code).toJSON();
	}

	public static ASTNode toAST(Code code) {


		String type = code.type();
		String value = code.name(false);

		if (type.equals("snapshot")) {
			type = "Snap!shot";
			value = ((Snapshot) code).guid;
		}

		if (code instanceof CallBlock) {
			CallBlock callBlock = (CallBlock) code;
			if (callBlock.isCustom) {
				type = BlockDefinition.getCustomBlockCall(callBlock.name);
			} else {
				type = callBlock.name;
			}
		}

		ASTNode node = new ASTNode(type);

		if (code instanceof LiteralBlock && ((LiteralBlock) code).type == Type.Text) {
			// Only keep numeric text literal values
			try {
				Double.parseDouble(value);
			} catch (NumberFormatException e) {
				value = null;
			}
		}
		if (value != null && !type.equals(value)) {
			String trimmed = value.trim();
			if (valueReplacements.containsKey(trimmed)) {
				trimmed = value = valueReplacements.get(trimmed);
			}
			values.add(trimmed);
			node.value = value;
		}

		code.addChildren(false, new Accumulator() {
			@Override
			public void addVariables(List<String> vars) {
				for (String var : vars) {
					node.addChild(variableToAST(var));
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
				if (code instanceof BlockDefinition) {
					// Skip imported block definitions
					if (((BlockDefinition) code).isImported) return;
				}
				node.addChild(toAST(code));
			}
		});

		return node;
	}

	public static ASTNode variableToAST(String value) {
		return new ASTNode("var", value);
	}
}
