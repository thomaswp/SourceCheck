package edu.isnap.eval.agreement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Spring2017;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;

public class CHFImport {

	public static void main(String[] args) throws IOException {
		load(Spring2017.GuessingGame1, "edm2017");
	}

	public static void load(Assignment assignment, String folder) throws IOException {
		File directory = new File(assignment.dir("chf/" + folder));
		if (!directory.exists()) {
			System.out.println("No directory: " + directory.getAbsolutePath());
			return;
		}

		ListMap<Integer, CHFEdit> allHints = loadAllHints(directory);
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);
		for (AssignmentAttempt attempt : attempts.values()) {
			for (AttemptAction action : attempt) {
				List<CHFEdit> edits = allHints.get(action.id);
				if (edits == null) continue;

				Node from = SimpleNodeBuilder.toTree(action.lastSnapshot, true);
				from = convertVars(from);
				String fromString = from.prettyPrint(true);
				System.out.println("--------- " + action.id + " ---------");
				System.out.println(fromString);
				for (CHFEdit edit : edits) {
					System.out.println(edit.priority + ": " + edit.error);
					System.out.println(Diff.diff(fromString, edit.outcome.prettyPrint(true), 2));
					System.out.println();
				}

			}
		}

	}

	private static Node convertVars(Node from) {
		String type = from.type();
//		if (type.startsWith("var")) type = "var";
		Node newFrom = new Node(from.parent, type, from.value, from.id);
		newFrom.children.addAll(from.children);
		from = newFrom;
		for (int i = 0; i < from.children.size(); i++) {
			from.children.set(i, convertVars(from.children.get(i)));
		}
		return from;
	}

	private static ListMap<Integer, CHFEdit> loadAllHints(File directory) throws IOException {
		ListMap<Integer, CHFEdit> hints = new ListMap<>();
		for (File file : directory.listFiles()) {
			CHFEdit edit = CHFEdit.parse(file);
			List<CHFEdit> list = hints.getList(edit.rowID);
			list.add(edit);
			list.sort((a, b) -> Integer.compare(a.priority, b.priority));
		}
		return hints;
	}

	private static class CHFEdit {
		public final Node outcome;
		public final double error;
		public final int rowID, priority;

		public CHFEdit(int rowID, Node outcome, double error, int priority) {
			this.rowID = rowID;
			this.outcome = outcome;
			this.error = error;
			this.priority = priority;
		}

		public static CHFEdit parse(File file) throws IOException {
			String contents = new String(Files.readAllBytes(file.toPath()));
			JSONObject json = new JSONObject(contents);
			double error = json.getDouble("error");
			ASTNode root = ASTNode.parse(json);
			String name = file.getName().replace(".json", "");
			int underscoreIndex = name.indexOf("_");
			int id = Integer.parseInt(name.substring(0, underscoreIndex));
			int priority = Integer.parseInt(name.substring(underscoreIndex + 1));
			CHFEdit edit = new CHFEdit(id, root.toNode(), error, priority);
			return edit;
		}
	}
}
