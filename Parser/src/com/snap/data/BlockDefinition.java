package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class BlockDefinition extends Block {
	
	public final String type;
	public final Script script;
	public final List<String> inputs = new ArrayList<String>();
	
	public BlockDefinition(String name, String type, Script script) {
		super(name.replace("&apos;", "'").replaceAll("%'[A-Za-z0-9# ]*'", "%s"));
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
