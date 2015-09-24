package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class BlockDefinition extends Block {
	private static final long serialVersionUID = 1L;
	
	public final String type;
	public final Script script;
	public final List<String> inputs = new ArrayList<String>();
	
	@SuppressWarnings("unused")
	private BlockDefinition() {
		this(null, null, null);
	}
	
	private static String steralizeName(String name) {
		if (name == null) return null;
		return name.replace("&apos;", "'").replaceAll("%'[A-Za-z0-9# ]*'", "%s");
	}
	
	public BlockDefinition(String name, String type, Script script) {
		super(steralizeName(name));
		this.type = type;
		this.script = script;
	}
	
	public static BlockDefinition parse(Element element) {
		String name = element.getAttribute("s");
		String type = element.getAttribute("type");
		Element scriptElement = XML.getFirstChildByTagName(element, "script");
		Script script = scriptElement == null ? new Script() : Script.parse(scriptElement);
		BlockDefinition def = new BlockDefinition(name, type, script);
		for (Element e : XML.getGrandchildrenByTagName(element, "inputs", "input")) {
			def.inputs.add(e.getAttribute("type"));
		}
		XML.ensureEmpty(element, "header", "code");
		return def;
	}
	
	@Override
	public String toCode() {
		return new CodeBuilder()
		.add(name)
		.addSParameters(inputs)
		.add(" ")
		.add(script)
		.end();
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		ac.add(script);
		ac.add(canonicalizeVariables(inputs, canon));
		return name;
	}
}
