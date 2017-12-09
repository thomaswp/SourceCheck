package edu.isnap.eval.export;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

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
import edu.isnap.parser.elements.Code;
import edu.isnap.parser.elements.Code.Accumulator;
import edu.isnap.parser.elements.LiteralBlock;
import edu.isnap.parser.elements.LiteralBlock.Type;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.parser.elements.util.Canonicalization;
import edu.isnap.parser.elements.util.IHasID;

public class JsonAST {


	public final static Set<String> values = new TreeSet<>();

	public static String flushWrittenValues() {
		String ret = String.join("\n", values);
		values.clear();
		return ret;
	}

	public final static HashMap<String, String> valueReplacements = new HashMap<>();
	static {
		valueReplacements.put("catherines variable", "my variable");
		valueReplacements.put("Answer is Kimberly", "Answer is name");
		valueReplacements.put("Bryson", "name");
		valueReplacements.put("Ellis", "name");
		valueReplacements.put("Your name is Bruce", "Your name is name");
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

		exportDataset(Fall2016.instance, true, false, Fall2016.LightsCameraAction);
//		exportAssignment(Fall2016.PolygonMaker);

//		for (Assignment assignment : BJCSolutions2017.All) {
//			Snapshot snapshot = Snapshot.parse(new File(assignment.templateFileBase() + ".xml"));
//			exportSnapshot(assignment, snapshot);
//		}
	}

	protected static void exportDataset(Dataset dataset, boolean canon, boolean solutionsOnly,
			Assignment... exclude) throws FileNotFoundException {
		values.clear();
		for (Assignment assignment : dataset.all()) {
			if (!Arrays.asList(exclude).contains(assignment)) {
				if (solutionsOnly) {
					exportAssignmentSolutions(assignment, canon);
				} else {
					exportAllAssignmentTraces(assignment, canon);
				}
			}
		}
		String dir = solutionsOnly ? "all-solutions" : "all-traces";
		if (canon) dir += "-canon";
		write(dataset.dataDir + "/export/" + dir + "/values.txt", String.join("\n", values));
	}

	public static void exportAllAssignmentTraces(Assignment assignment, boolean canon)
			throws FileNotFoundException {
		exportAssignmentTraces(assignment, canon, "all-traces" + (canon ? "-canon" : ""),
				a -> true, a -> action -> true, a -> a.id);
	}

	public static void exportAssignmentTraces(Assignment assignment, boolean canon, String folder,
			Predicate<AssignmentAttempt> attemptFilter,
			Function<AssignmentAttempt, Predicate<AttemptAction>> actionFilter,
			Function<AssignmentAttempt, String> namer)
			throws FileNotFoundException {
		System.out.println(assignment.name + ": " + folder);
		for (AssignmentAttempt attempt : assignment.load(
				Mode.Use, false, true, new SnapParser.LikelySubmittedOnly()).values()) {
			if (!attemptFilter.test(attempt)) continue;
			String lastJSON = "";
			System.out.println(attempt.id);
//			String last = "";
			int order = 0;
			Snapshot lastSnapshot = null;
			for (AttemptAction action : attempt) {
				if (!actionFilter.apply(attempt).test(action)) continue;
				if (lastSnapshot == action.lastSnapshot) continue;
				lastSnapshot = action.lastSnapshot;
				String json = toJSON(action.lastSnapshot, canon).toString(2);
				if (json.equals(lastJSON)) continue;
				lastJSON = json;
				write(String.format("%s/%s/%05d-%05d.json",
						assignment.dir("export/" + folder),
						namer.apply(attempt), order++, action.id), json);
//				System.out.println(action.id);
//				System.out.println(Diff.diff(last,
//						last = SimpleNodeBuilder.toTree(action.snapshot, true).prettyPrint(), 1));
			}
//			System.out.println(SimpleNodeBuilder.toTree(attempt.submittedSnapshot, true).prettyPrint());
		}
	}

	protected static void exportAssignmentSolutions(Assignment assignment, boolean canon)
			throws FileNotFoundException {
		for (AssignmentAttempt attempt : assignment.load(
				Mode.Use, true, true, new SnapParser.LikelySubmittedOnly()).values()) {
			exportSolution(assignment, attempt.submittedSnapshot, canon);
		}
	}

	protected static void exportSolution(Assignment assignment, Snapshot snapshot, boolean canon)
			throws FileNotFoundException {
		JSONObject json = toJSON(snapshot, canon);
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

	public static JSONObject toJSON(Code code, boolean canon) {
		return toAST(code, canon).toJSON();
	}

	public static ASTNode toAST(Code code, boolean canon) {
		String type = code.type(canon);
		String value = code.value();
		String id = code instanceof IHasID ? ((IHasID) code).getID() : null;

		if (type.equals("snapshot")) {
			type = ASTNode.SNAPSHOT_TYPE;
		}

		if (code instanceof LiteralBlock && ((LiteralBlock) code).type == Type.Text) {
			// Only keep numeric text literal values
			try {
				Double.parseDouble(value);
			} catch (NumberFormatException e) {
				value = null;
			}
		}
		if (value != null) {
			String trimmed = value.trim();
			if (valueReplacements.containsKey(trimmed)) {
				trimmed = value = valueReplacements.get(trimmed);
			}
			values.add(trimmed);
		}

		ASTNode node = new ASTNode(type, value, id);

		code.addChildren(canon, new Accumulator() {
			@Override
			public void add(String type, String value) {
				node.addChild(new ASTNode(type, value, null));
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
				node.addChild(toAST(code, canon));
			}
		});

		return node;
	}
}
