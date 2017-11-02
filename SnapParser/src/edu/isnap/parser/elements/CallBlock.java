package edu.isnap.parser.elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.w3c.dom.Element;

import edu.isnap.parser.elements.util.Canonicalization;
import edu.isnap.parser.elements.util.XML;

public class CallBlock extends Block {
	private static final long serialVersionUID = 1L;

	public final static HashSet<String> SYMMETRIC = new HashSet<>();
	public final static HashMap<String, String> OPPOSITES = new HashMap<>();
	static {
		String[] symmetric = new String[] {
			"reportSum", "reportProduct", "reportRandom", "reportEquals",
			"reportAnd", "reportOr",
		};
		for (String s : symmetric) SYMMETRIC.add(s);

		OPPOSITES.put("reportGreaterThan", "reportLessThan");
	}

	public final List<Block> parameters = new ArrayList<>();
	public final List<Script> bodies = new ArrayList<>();
	public final boolean isCustom;

	@Override
	public String type() {
		if (isCustom) {
			// If this is a tool, gets its simplified name; otherwise returns evaluateCustomBlock
			return BlockDefinition.getCustomBlockCall(name);
		}
		// We treat all non-custom-block calls as unique types
		return name;
	}

	@Override
	public String type(boolean canon) {
		if (canon && OPPOSITES.containsKey(name)) {
			// Since the type can actually be changed by canonicalization, we have to handle it
			// specially for CallBlocks
			return OPPOSITES.get(name);
		}
		return type();
	}

	@Override
	public String value() {
		if (isCustom && !BlockDefinition.isTool(name)) {
			// Only non-tool custom block calls have a value: the name of the custom block called
			// Other calls return their name as their type
			return name;
		}
		return null;
	}

	@SuppressWarnings("unused")
	private CallBlock() {
		this(null, null, false);
	}

	public CallBlock(String type, String id, boolean isCustom) {
		super(type, id);
		this.isCustom = isCustom;
	}

	public static Block parse(Element element) {
		if (element.hasAttribute("var")) {
			return new VarBlock(
					element.getAttribute("var"),
					getID(element));
		}

		CallBlock block = new CallBlock(
				element.getAttribute("s").replaceAll("%[^(\\s%)]*", "%s"),
				getID(element),
				element.getTagName().equals("custom-block"));
		for (Code code : XML.getCode(element)) {
			if (code instanceof Block) {
				block.parameters.add((Block) code);
			} else if (code instanceof Script) {
				block.bodies.add((Script) code);
			} else {
				throw new RuntimeException("Unknown code in call block: " + code);
			}
		}
		return block;
	}

	private List<Block> params(boolean canon) {
		if (!canon || parameters.size() != 2) return parameters;
		if (SYMMETRIC.contains(name)) {
			if (parameters.get(0).type(canon).compareTo(parameters.get(1).type(canon)) < 1) {
				return parameters;
			}
		} else if (!OPPOSITES.containsKey(name)) {
			return parameters;
		}
		ArrayList<Block> params = new ArrayList<>();
		params.addAll(parameters);
		Collections.reverse(params);
		return params;
	}

	@Override
	public String toCode(boolean canon) {
		return new CodeBuilder(canon)
		.add(type(canon))
		.addParameters(params(canon))
		.add(bodies.size() > 0 ? " " : "")
		.add(bodies)
		.end();
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) {
		List<Block> params = params(canon);
		if (params != parameters) {
			ac.add(new Canonicalization.SwapBinaryArgs());
		}
		ac.add(params);
		ac.add(bodies);
		if (OPPOSITES.containsKey(name)) ac.add(new Canonicalization.Rename(name));
	}
}
