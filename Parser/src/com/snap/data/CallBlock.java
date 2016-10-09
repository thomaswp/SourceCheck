package com.snap.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class CallBlock extends Block {
	private static final long serialVersionUID = 1L;

	private final static HashSet<String> SYMMETRIC = new HashSet<String>();
	private final static HashMap<String, String> OPPOSITES = new HashMap<String, String>();
	static {
		String[] symmetric = new String[] {
			"reportSum", "reportProduct", "reportRandom", "reportEquals",
			"reportAnd", "reportOr",
		};
		for (String s : symmetric) SYMMETRIC.add(s);

		OPPOSITES.put("reportGreaterThan", "reportLessThan");
	}

	public final List<Block> parameters = new ArrayList<Block>();
	public final List<Script> bodies = new ArrayList<Script>();
	public final boolean isCustom;

	@Override
	public String name(boolean canon) {
		if (canon) {
			if (OPPOSITES.containsKey(name)) {
				return OPPOSITES.get(name);
			}
			return type();
		}

		return name;
	}

	@Override
	public String type() {
		if (isCustom) return "evaluateCustomBlock";
		return name;
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
			if (parameters.get(0).name(canon).compareTo(parameters.get(1).name(canon)) < 1) return parameters;
		} else if (!OPPOSITES.containsKey(name)) {
			return parameters;
		}
		ArrayList<Block> params = new ArrayList<Block>();
		params.addAll(parameters);
		Collections.reverse(params);
		return params;
	}

	@Override
	public String toCode(boolean canon) {
		return new CodeBuilder(canon)
		.add(name(canon))
		.addParameters(params(canon))
		.add(bodies.size() > 0 ? " " : "")
		.add(bodies)
		.end();
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) {
		List<Block> params = params(canon);
		if (params != parameters) {
			ac.add(new Canonicalization.SwapArgs());
		}
		ac.add(params);
		ac.add(bodies);
		if (OPPOSITES.containsKey(name)) ac.add(new Canonicalization.InvertOp(name));
		else if (!name.equals(name(canon))) ac.add(new Canonicalization.Rename(name));
	}
}
