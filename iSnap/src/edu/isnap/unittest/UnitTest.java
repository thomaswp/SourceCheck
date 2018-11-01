package edu.isnap.unittest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import edu.isnap.ctd.hint.VectorHint;
import edu.isnap.dataset.Assignment;
import edu.isnap.hint.HintGenerator;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.Node;
import edu.isnap.parser.elements.Snapshot;

public class UnitTest {

	public final String id, xml, hintJSON;
	public final Assignment assignment;

	public UnitTest(String id, Assignment assignment, String xml, String hintJSON) {
		this.id = id;
		this.xml = xml;
		this.assignment = assignment;
		this.hintJSON = hintJSON;
	}

	public boolean run(HintGenerator generator, PrintStream out) {
		TestHint correctHint = null;
		try {
			correctHint = new TestHint(hintJSON);
		} catch (Exception e) {
			e.printStackTrace();
			out.println("Cannot parse hint.");
			return false;
		}

		List<VectorHint> hints = getHints(generator, out);
		if (hints == null) return false;

		for (VectorHint hint : hints) {
			TestHint givenHint = new TestHint(hint);

			if (correctHint.sharesRoot(givenHint)) {
				return correctHint.test(givenHint, out);
			}
		}

		// If it's a bad hint, we pass if nothing matches
		if (correctHint.badHint) return true;

		// Otherwise, we should have had a hint generated, and we fail the test
		out.println("No hint generated for root.");
		return false;
	}

	public List<VectorHint> getHints(HintGenerator generator, PrintStream out) {
		Snapshot snapshot = Snapshot.parse("test_" + id, xml);
		if (snapshot == null) {
			out.println("Cannot parse snapshot.");
			return null;
		}

		Node node = SimpleNodeBuilder.toTree(snapshot, true);
		List<VectorHint> hints = generator.getHints(node);
		return hints;
	}

	public static String saveUnitTest(String assignment, String xml, String hintJSON)
			throws IOException {
		String id = String.format("%x", (xml + hintJSON).hashCode());
		String path = System.getenv().get("dataDir") + "/unittests/" + assignment + "/" +
					id;
		new File(path).mkdirs();
		BufferedWriter writer = new BufferedWriter(new FileWriter(path + "/hint.json"));
		writer.write(hintJSON);
		writer.close();
		writer = new BufferedWriter(new FileWriter(path + "/code.xml"));
		writer.write(xml);
		writer.close();

		return id;
	}

	public boolean expectedFailure() {
		return new TestHint(hintJSON).expectedFailure;
	}
}
