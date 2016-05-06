package com.snap.data;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import com.snap.XML;

public class BlockDefinition extends Block {
	private static final long serialVersionUID = 1L;
	
	public final String type, category;
	public final Script script;
	public final List<String> inputs = new ArrayList<String>();
	public final List<Script> scripts = new ArrayList<Script>();
	
	@SuppressWarnings("unused")
	private BlockDefinition() {
		this(null, null, null, null);
	}
	
	private static String steralizeName(String name) {
		if (name == null) return null;
		return name.replace("&apos;", "'").replaceAll("%'[A-Za-z0-9# ]*'", "%s");
	}
	
	public BlockDefinition(String name, String type, String category, Script script) {
		super(steralizeName(name), -1);
		this.type = type;
		this.script = script;
		this.category = category;
	}
	
	public static BlockDefinition parse(Element element) {
		String name = element.getAttribute("s");
		String type = element.getAttribute("type");
		String category = element.getAttribute("category");
		Element scriptElement = XML.getFirstChildByTagName(element, "script");
		Script script = scriptElement == null ? new Script() : Script.parse(scriptElement);
		BlockDefinition def = new BlockDefinition(name, type, category, script);
		for (Element e : XML.getGrandchildrenByTagName(element, "inputs", "input")) {
			def.inputs.add(e.getAttribute("type"));
		}
		for (Element e : XML.getGrandchildrenByTagName(element, "scripts", "script")) {
			def.scripts.add(Script.parse(e));
		}
		XML.ensureEmpty(element, "header", "code");
		return def;
	}
	
	private static <E> List<E> toList(Iterable<E> iter) {
	    List<E> list = new ArrayList<E>();
	    for (E item : iter) {
	        list.add(item);
	    }
	    return list;
	}
	
	public static BlockDefinition parseEditing(Element element) {
		List<Element> scripts = toList(XML.getGrandchildrenByTagName(element, "scripts", "script"));
		Element mainScript = scripts.get(0);
		List<Element> blocks = toList(XML.getChildrenByTagName(mainScript, "block"));
		Element firstBlock = blocks.get(0);
		if (firstBlock.getAttribute("s").length() > 0) {
			throw new RuntimeException("Improper editing block");
		}
		Element definition = firstBlock;
		while (!definition.getTagName().equals("custom-block")) {
			definition = (Element) definition.getFirstChild();
		}
		
		String name = definition.getAttribute("s");
		
		Script script = new Script();
		for (int i = 1; i < blocks.size(); i++) {
			script.blocks.add((Block) XML.getCodeElement(blocks.get(i)));
		}
		
		BlockDefinition def = new BlockDefinition(name, null, null, script);

		for (Element e : XML.getChildrenByTagName(definition, "l")) {
			def.inputs.add(e.getNodeValue());
		}

		for (int i = 1; i < scripts.size(); i++) {
			def.scripts.add(Script.parse(scripts.get(i)));
		}
		return def;
	}
	
	@Override
	public String toCode(boolean canon) {
		return new CodeBuilder(canon)
		.add(name, "customBlock")
		.addSParameters(canonicalizeVariables(inputs, canon))
		.add(" ")
		.add(script)
		.add("scripts:")
		.indent()
		.add(scripts)
		.close()
		.end();
	}

	@Override
	public String addChildren(boolean canon, Accumulator ac) {
		ac.add(script);
		ac.add(canonicalizeVariables(inputs, canon));
		ac.add(scripts);
		return canon ? "customBlock" : name;
	}
}
