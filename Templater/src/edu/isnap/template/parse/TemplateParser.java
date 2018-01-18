package edu.isnap.template.parse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.graph.Node.NodeConstructor;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.ctd.util.Diff;
import edu.isnap.dataset.Assignment;
import edu.isnap.hint.ConfigurableAssignment;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.elements.CallBlock;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.template.data.BNode;
import edu.isnap.template.data.Context;
import edu.isnap.template.data.DefaultNode;

public class TemplateParser {

	public static final String DATASET = "template";

	public static void parseSnapTemplate(Assignment assignment) throws IOException {
		String baseFile = assignment.templateFileBase();
		HintConfig config = assignment instanceof ConfigurableAssignment ?
				((ConfigurableAssignment) assignment).getConfig() : new SnapHintConfig();
		Node sample = SimpleNodeBuilder.toTree(Snapshot.parse(new File(baseFile + ".xml")), true);
		HintMap hintMap = parseTemplate(baseFile, sample, config);


		saveHintMap(hintMap, "../HintServer/WebContent/WEB-INF/data", assignment.name);
		saveHintMap(hintMap, assignment.dataset.dataDir, assignment.name);
	}

	public static HintMap parseTemplate(String baseFile, Node sample, HintConfig config)
			throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(baseFile + ".snap"));
		String template = new String(encoded);
		DefaultNode node = new TemplateParser(template).parse();


		List<BNode> variants = node.getVariants(Context.fromSample(sample));

		HintMap hintMap = new HintMap(config);
		for (BNode variant : variants) {
			Node n = variant.toNode(sample::constructNode);
			verifyNode(n);
			hintMap.solutions.add(n);
		}

		printVariants(node.getVariants(Context.fromSample(sample).withOptional(false)),
				sample::constructNode);
		System.out.println("--------------------");
		System.out.println(variants.size());
		System.out.println(sample.prettyPrint(true));

		return hintMap;
	}

	public static void saveHintMap(HintMap hintMap, String basePath, String name)
			throws FileNotFoundException {
		HintMapBuilder hmb = new HintMapBuilder(hintMap, 1);
		Kryo kryo = SnapHintBuilder.getKryo();
		String path = SnapHintBuilder.getStorePath(basePath, name, 1, DATASET);
		Output output = new Output(new FileOutputStream(path));
		kryo.writeObject(output, hmb);
		output.close();
	}

	// TODO: rather erroring, replace and add the needed canonicalizations
	private static void verifyNode(Node node) {
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				if (CallBlock.SYMMETRIC.contains(node.type())) {
					String t0 = node.children.get(0).type();
					String t1 = node.children.get(1).type();
					if (t0.compareTo(t1) > 1) {
						throw new RuntimeException(String.format(
								"Children of %s out of order: %s, %s",
								node.type(), t0, t1));
					}
					// Just like the SimpleNodeBuilder, set the children of a symmetric function
					// to be orderless
					// Order groups not yet supported for arguments
//					for (Node child : node.children) {
//						child.setOrderGroup(1);
//					}
				} else if (CallBlock.OPPOSITES.containsKey(node.type())) {
					throw new RuntimeException(String.format("Cannot use %s; use %s instead!",
							node.type(), CallBlock.OPPOSITES.get(node.type())));
				}
			}
		});
	}

	private static void printVariants(List<BNode> variants, NodeConstructor constructor) {
		String last = "";
		for (BNode variant : variants) {
			System.out.println("--------------------");
			System.out.println(variant.deepestContextSnapshot());
			String out = variant.toNode(constructor).prettyPrint(true);
			String diff = Diff.diff(last, out, 2);
			if (StringUtils.countMatches(out, "\n") <= StringUtils.countMatches(diff, "\n")) {
				System.out.println(out);
			} else {
				System.out.println(diff);
			}
			last = out;
		}
	}

	private String[] parts;
	private int index;

	public TemplateParser(String template) {
		Pattern pattern = Pattern.compile(":`([^`]*)`");
		Matcher matcher = pattern.matcher(template);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			// TODO: This should really escape ( { } ) and space
			matcher.appendReplacement(sb, ":" + matcher.group(1).replaceAll(" ", "&nbsp;"));
		}
		matcher.appendTail(sb);
		template = sb.toString();
		this.parts = template
				.replaceAll("(\\{|\\}|\\(|\\))", " $1 ")
				.replaceAll("(\\{|\\}|\\(|\\))", " $1 ")
				.replace(",", "").split("\\s+");

		for (int i = 0; i < parts.length; i++) {
			parts[i] = parts[i].replaceAll("&nbsp;", " ");
		}
//		for (String part : parts) {
//			System.out.println("`" + part + "`");
//		}
	}

	public DefaultNode parse() {
		index = 0;
		DefaultNode node = parseNode();
		if (index < parts.length) {
			System.err.println("Unused parts: " + rest());
		}
		return node;
	}

	private String rest() {
		return Arrays.toString(Arrays.copyOfRange(parts, index, parts.length));
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

	private boolean isEnd() {
		String token = peek();
		return "}".equals(token) || ")".equals(token);
	}

	private boolean matches(String start, String end) {
		if ("{".equals(start)) return "}".equals(end);
		if ("(".equals(start)) return ")".equals(end);
		return false;
	}

	private DefaultNode parseNode() {
		if (isEnd() || isStart()) throw new RuntimeException("Extra start or end: " + rest());
		DefaultNode node = DefaultNode.create(read());
		if (node.type.startsWith("@")) {
			if (!"{".equals(peek()) && !isEnd()) {
				String next = read();
				if ("(".equals(next)) {
					while (!")".equals((next = read()))) {
						node.args.add(next);
					}
				} else {
					node.args.add(next);
				}
			}
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
