package com.snap.graph.unittest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import com.snap.data.Snapshot;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.Hint;
import com.snap.graph.data.HintFactoryMap.VectorHint;
import com.snap.graph.data.Node;
import com.snap.graph.subtree.SubtreeBuilder;
import com.snap.parser.Assignment;

public class UnitTest {

	public final String id, xml, hintJSON;
	public final Assignment assignment;

	public UnitTest(String id, Assignment assignment, String xml, String hintJSON) {
		this.id = id;
		this.xml = xml;
		this.assignment = assignment;
		this.hintJSON = hintJSON;
	}

	public boolean run(SubtreeBuilder builder, PrintStream out) {
		TestHint correctHint = null;
		try {
			correctHint = new TestHint(hintJSON);
		} catch (Exception e) {
			e.printStackTrace();
			out.println("Cannot parse hint.");
			return false;
		}

		List<Hint> hints = getHints(builder, out);
		if (hints == null) return false;

		for (Hint hint : hints) {
			if (!(hint instanceof VectorHint)) continue;
			TestHint givenHint = new TestHint((VectorHint) hint);

			if (correctHint.sharesRoot(givenHint)) {
				return correctHint.test(givenHint, out);
			}
		}

		out.println("No hint generated for root.");
		return false;
	}

	public List<Hint> getHints(SubtreeBuilder builder, PrintStream out) {
		Snapshot snapshot = Snapshot.parse("test_" + id, xml);
		if (snapshot == null) {
			out.println("Cannot parse snapshot.");
			return null;
		}

		Node node = SimpleNodeBuilder.toTree(snapshot, true);
		List<Hint> hints = builder.getHints(node);
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
