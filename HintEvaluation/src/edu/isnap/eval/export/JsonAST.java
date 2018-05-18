package edu.isnap.eval.export;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import org.json.JSONObject;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.ASTSnapshot;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.NodeConstructor;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
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
import edu.isnap.rating.Trace;
import edu.isnap.rating.TraceDataset;

public class JsonAST {

	public final static String SNAPSHOT_TYPE = "Snap!shot";

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
		valueReplacements.put("Sabrina", "name");
	}

//	public static void main(String[] args) throws IOException {
//		Collection<AssignmentAttempt> attempts =
//				Fall2016.GuessingGame1.load(Mode.Use, false, true,
//						new SnapParser.LikelySubmittedOnly()).values();
//
//		for (AssignmentAttempt attempt : attempts) {
//			Snapshot submitted = attempt.submittedSnapshot;
//			System.out.println(toJSON(submitted).toString(2));
//			break;
//		}

//		exportDataset(Fall2016.instance, true, false, Fall2016.LightsCameraAction);
//		exportAssignment(Fall2016.PolygonMaker);

//		for (Assignment assignment : BJCSolutions2017.All) {
//			Snapshot snapshot = Snapshot.parse(new File(assignment.templateFileBase() + ".xml"));
//			exportSnapshot(assignment, snapshot);
//		}
//	}

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
		TraceDataset dataset = new TraceDataset("all-traces");
		for (AssignmentAttempt attempt : assignment.load(
				Mode.Use, false, true, new SnapParser.LikelySubmittedOnly()).values()) {
			System.out.println(attempt.id);
			Trace trace = createTrace(attempt, assignment.name, canon, null);
			dataset.addTrace(trace);
		}
		String dir = assignment.dir("export/all-traces" + (canon ? "-canon" : "") + "/");
		dataset.writeToFolder(dir);
	}

	public static Trace createTrace(AssignmentAttempt attempt, String assignmentID, boolean canon,
			Integer stopID) {
		Snapshot lastSnapshot = null;
		ASTSnapshot lastNode = null;
		boolean attemptCorrect = attempt.grade != null && attempt.grade.average() == 1;
		String id = stopID == null ? attempt.id : String.valueOf(stopID);
		Trace trace = new Trace(id, assignmentID);
		boolean stop = false;
		for (AttemptAction action : attempt) {
			// Set the stop flag when we see the stopID, but only break on the next snapshot
			if (stopID != null && action.id == stopID) stop = true;
			if (stop && action.id != stopID) break;
			if (lastSnapshot == action.lastSnapshot) continue;
			lastSnapshot = action.lastSnapshot;
			// TODO: This is not always true for the last snapshots of correct traces.
			// Fix before sharing the dataset!
			boolean correct = attemptCorrect && lastSnapshot == attempt.submittedSnapshot;
			ASTSnapshot node = JsonAST.toAST(action.lastSnapshot, canon).toSnapshot(correct, null);
			if (node.equals(lastNode, true, true)) continue;
			lastNode = node;
			trace.add(node);
		}
		return trace;
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
			type = SNAPSHOT_TYPE;
		}

		// Scripts really shouldn't have an ID, since it doesn't stay constant over snapshots, but
		// the current implementation does, and I'm afraid to break something, so we remove it here.
		if (type.equals("script")) {
			id = null;
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

	public static Node toNode(Code code, boolean canon, NodeConstructor constructor) {
		return toNode(toAST(code, canon), constructor);
	}

	public static Node toNode(ASTNode astNode, NodeConstructor constructor) {
		return toNode(astNode, null, constructor);
	}

	public static Node toNode(ASTNode astNode, Node parent, NodeConstructor constructor) {
		String type = astNode.type;
		if (SNAPSHOT_TYPE.equals(type)) type = "snapshot";
		Node node = constructor.constructNode(parent, type, astNode.value, astNode.id);
		node.tag = astNode;
		for (ASTNode child : astNode.children()) {
			node.children.add(toNode(child, node, constructor));
		}
		return node;
	}
}
