package edu.isnap.template.parse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import edu.isnap.dataset.Assignment;
import edu.isnap.hint.ConfigurableAssignment;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.Node;
import edu.isnap.node.Node.Action;
import edu.isnap.node.Node.NodeConstructor;
import edu.isnap.parser.elements.CallBlock;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.template.data.BNode;
import edu.isnap.template.data.Context;
import edu.isnap.template.data.DefaultNode;
import edu.isnap.util.Diff;

public class TemplateParser {

	public static final String DATASET = "template";

	public static void parseSnapTemplate(Assignment assignment) throws IOException {
		String baseFile = assignment.templateFileBase();
		HintConfig config = ConfigurableAssignment.getConfig(assignment);
		Node sample = SimpleNodeBuilder.toTree(Snapshot.parse(new File(baseFile + ".xml")), true);
		HintData hintData = parseTemplate(assignment.name, baseFile, sample, config);


		saveHintMap(hintData, "../HintServer/WebContent/WEB-INF/data", assignment.name);
		saveHintMap(hintData, assignment.dataset.dataDir, assignment.name);
	}

	public static HintData parseTemplate(String assignment, String baseFile, Node sample,
			HintConfig config) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(baseFile + ".snap"));
		String template = new String(encoded);
		DefaultNode node = new TemplateParser(template).parse();

		List<BNode> variants = node.getVariants(Context.fromSample(sample));

		HintData hintData = new HintData(assignment, config, 1,
//				CTDHintGenerator.DataConsumer,
				HintHighlighter.DataConsumer);
		for (BNode variant : variants) {
			Node n = variant.toNode(sample::constructNode);
			n.cache();
			verifyNode(n);
			hintData.addTrace(null, Collections.singletonList(n));
		}

		printVariants(node.getVariants(Context.fromSample(sample).withOptional(false)),
				sample::constructNode);
		System.out.println("--------------------");
		System.out.println(variants.size());
		System.out.println(sample.prettyPrint(true));

		return hintData;
	}

	public static void saveHintMap(HintData hintData, String basePath, String name)
			throws FileNotFoundException {
		new File(basePath).mkdirs();
		Kryo kryo = SnapHintBuilder.getKryo();
		String path = SnapHintBuilder.getStorePath(basePath, name, 1, DATASET);
		Output output = new Output(new FileOutputStream(path));
		kryo.writeObject(output, hintData);
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
	private Map<String, String> valueMap = new HashMap<>();

	public TemplateParser(String template) {
		template = Arrays.stream(template.split("\n")).map(line -> {
				int comment = line.indexOf("//");
				if (comment < 0) return line;
				return line.substring(0, comment);
			}).reduce("", (a, b) -> a + b);
		Pattern pattern = Pattern.compile(":`([^`]*)`");
		Matcher matcher = pattern.matcher(template);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			// TODO: This should really escape ( { } ) and space
			String holder = "_HOLDER_" + valueMap.size() + "_";
			valueMap.put(holder, matcher.group(1));
			matcher.appendReplacement(sb, ":" + holder);
		}
		matcher.appendTail(sb);
		template = sb.toString();
		this.parts = template
				.replaceAll("(\\{|\\}|\\(|\\))", " $1 ")
				.replaceAll("(\\{|\\}|\\(|\\))", " $1 ")
				.replace(",", "").split("\\s+");

		for (String holder : valueMap.keySet()) {
			for (int i = 0; i < parts.length; i++) {
				parts[i] = parts[i].replaceAll(holder, valueMap.get(holder));
			}
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
		} else if (node.type.equals("@hint")) {
			node.children.add(parseNode());
		}

//		System.out.println("Exiting " + node.type);
		return node;
	}
}
