package edu.isnap.eval.export;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.json.JSONObject;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.BJCSolutions2017;
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

public class JsonAST {

	private static Set<String> values = new TreeSet<>();

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

//		exportDataset(Fall2016.instance, Fall2016.LightsCameraAction);
//		exportAssignment(Fall2016.PolygonMaker);

		for (Assignment assignment : BJCSolutions2017.All) {
			Snapshot snapshot = Snapshot.parse(new File(assignment.templateFileBase() + ".xml"));
			exportSnapshot(assignment, snapshot);
		}
	}

	protected static void exportDataset(Dataset dataset, Assignment... exclude)
			throws FileNotFoundException {
		values.clear();
		for (Assignment assignment : dataset.all()) {
			if (!Arrays.asList(exclude).contains(assignment)) {
				exportAssignment(assignment);
			}
		}
		write(dataset.dataDir + "/analysis/jsonAST/values.txt", String.join("\n", values));
	}

	protected static void exportAssignment(Assignment assignment) throws FileNotFoundException {
		for (AssignmentAttempt attempt : assignment.load(
				Mode.Use, true, true, new SnapParser.LikelySubmittedOnly()).values()) {
			exportSnapshot(assignment, attempt.submittedSnapshot);
		}
	}

	protected static void exportSnapshot(Assignment assignment, Snapshot snapshot)
			throws FileNotFoundException {
		JSONObject json = toJSON(snapshot);
		String jsonString = json.toString(2);
//		System.out.println(jsonString);
		String basePath = assignment.dir("analysis/jsonAST") + "/" + snapshot.guid;
		write(basePath + ".json", jsonString);
		write(basePath + ".txt", SimpleNodeBuilder.toTree(snapshot, true).prettyPrint());
	}

	private static void write(String path, String text) throws FileNotFoundException {
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
		String value = code.value();

		if (type.equals("snapshot")) type = "Snap!shot";

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
			values.add(value.trim());
			node.value = value;
		}

		code.addChildren(false, new Accumulator() {
			@Override
			public void add(String type, String value) {
				node.addChild(new ASTNode(type, value));
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
}
