package edu.isnap.template.parse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.template.data.BNode;
import edu.isnap.template.data.Context;
import edu.isnap.template.data.DefaultNode;

public class Parser {
	public static void main(String[] args) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get("test.txt"));
		String template = new String(encoded);
		DefaultNode node = new Parser(template).parse();


		Node sample = SimpleNodeBuilder.toTree(Snapshot.parse(new File("Etch-a-Sketch.xml")), true);
		List<BNode> variants = node.getVariants(Context.fromSample(sample));

		HintMap hintMap = new HintMap(new HintConfig());
		for (BNode variant : variants) {
			hintMap.solutions.add(variant.toNode());
		}

		Kryo kryo = SnapHintBuilder.getKryo();
		String path = SnapHintBuilder.getStorePath(
				"../HintServer/WebContent/WEB-INF/data", "lineArt", 1);
		Output output = new Output(new FileOutputStream(path));
		kryo.writeObject(output, hintMap);
		output.close();


		printVariants(sample, variants);
	}

	private static void printVariants(Node sample, List<BNode> variants) {
		for (BNode variant : variants) {
			System.out.println("--------------------");
			System.out.println(variant.toNode().prettyPrint());
		}
		System.out.println("--------------------");
		System.out.println(variants.size());

		System.out.println(sample.prettyPrint());
	}

	private String[] parts;
	private int index;

	public Parser(String template) {
//		this.template = template;
		this.parts = template.replaceAll("(\\{|\\}|\\(|\\))", " $1 ")
				.replace(",", "").split("\\s+");
//		for (String part : parts) {
//			System.out.println("`" + part + "`");
//		}
	}

	public DefaultNode parse() {
		index = 0;
		return parseNode();
	}

	private String read() {
		return parts[index++];
	}

	private String peek() {
		return parts[index];
	}

	private boolean isStart() {
		String token = peek();
		return "{".equals(token) || "(".equals(token);
	}

	private boolean matches(String start, String end) {
		if ("{".equals(start)) return "}".equals(end);
		if ("(".equals(start)) return ")".equals(end);
		return false;
	}

	private DefaultNode parseNode() {
		DefaultNode node = DefaultNode.create(read());
		if (node.type.startsWith("@") && !isStart()) {
			node.name = read();
		}
//		System.out.println("Enterring " + node.type + " [" + peek() + "]");

		if (isStart()) {
			String start = read();

			while (!matches(start, peek())) {
				node.children.add(parseNode());
			}
			read();
		}

//		System.out.println("Exiting " + node.type);
		return node;
	}
}
