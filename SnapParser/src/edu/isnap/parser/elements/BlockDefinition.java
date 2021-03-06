package edu.isnap.parser.elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Element;

import edu.isnap.parser.elements.BlockDefinitionGroup.BlockIndex;
import edu.isnap.parser.elements.util.IHasID;
import edu.isnap.parser.elements.util.XML;

public class BlockDefinition extends Code implements IHasID {
	private static final long serialVersionUID = 1L;

	public final static String[] TOOLS_BLOCKS = new String[] {
       	"label %s of size %s",
       	"map %s over %s",
       	"empty? %s",
       	"keep items such that %s from %s",
       	"combine with %s items of %s",
       	"if %s then %s else %s",
       	"for %s = %s to %s %s",
       	"join words %s",
       	"list $arrowRight sentence %s",
       	"sentence $arrowRight list %s",
       	"catch %s %s",
       	"throw %s",
       	"catch %s %s",
       	"throw %s %s",
       	"for each %s of %s %s",
       	"if %s do %s and pause all $pause-1-255-220-0",
       	"word $arrowRight list %s",
       	"ignore %s",
       	"tell %s to %s",
       	"ask %s for %s",
       	"list $arrowRight word %s",
	};

	private final static Set<String> TOOLS_BLOCKS_SET =
			new HashSet<>(Arrays.asList(TOOLS_BLOCKS));

	public final String name, type, category, guid;
	public final boolean isImported;
	public final Script script;
	public final List<String> inputs = new ArrayList<>();
	public final List<Script> scripts = new ArrayList<>();
	public String parentID;

	public transient BlockIndex blockIndex;

	@Override
	public String type() {
		return "customBlock";
	}

	@Override
	public String value() {
		return name;
	}

	@SuppressWarnings("unused")
	private BlockDefinition() {
		this(null, null, null, null, false, null);
	}

	private final static Pattern ARG_PATTERN = Pattern.compile("%'([^']*)'");

	public static String extractInputs(String name, List<String> inputs) {
		if (name == null) return null;
		Matcher matcher = ARG_PATTERN.matcher(name);
		if (inputs != null) {
			while (matcher.find()) {
				inputs.add(matcher.group(1));
			}
		}
		return matcher.replaceAll("%s");
	}

	public BlockDefinition(String name, String type, String category, String guid,
			boolean isImported, Script script) {
		this.name = extractInputs(name, inputs);
		this.type = type;
		this.category = category;
		this.guid = guid;
		this.isImported = isImported;
		this.script = script;
	}

	public static boolean isImported(String selector) {
		return TOOLS_BLOCKS_SET.contains(selector) || XML.isImportedBlock(selector);
	}

	public static String normalizeSelector(String selector) {
		return selector.replaceAll("%[^(\\s%)]*", "%s");
	}

	public static BlockDefinition parse(Element element) {
		String name = element.getAttribute("s");
		String type = element.getAttribute("type");
		String category = element.getAttribute("category");
		String guid = element.getAttribute("guid");
		Element scriptElement = XML.getFirstChildByTagName(element, "script");
		Script script = scriptElement == null ? new Script() : Script.parse(scriptElement);
		boolean isImported = isImported(normalizeSelector(name)) ||
				"true".equals(element.getAttribute("isImported"));
		BlockDefinition def = new BlockDefinition(name, type, category, guid, isImported, script);
		for (Element e : XML.getGrandchildrenByTagName(element, "scripts", "script")) {
			def.scripts.add(Script.parse(e));
		}
		// Unparsed children:
		// <code>: Snap-to-code mappings to translating Snap
		// <header>: Snap-to-header mappings for translating Snap
		return def;
	}

	public static BlockDefinition parseFromScripts(Element element, String defaultGUID) {
		String guid = element.getAttribute("guid");
		// GUID used to be stored in the <editing> tag, so we allow the parent to pass it in for
		// backwards compatibility
		if (guid.isEmpty()) guid = defaultGUID;

		List<Element> scripts = toList(XML.getChildrenByTagName(element, "script"));
		Element mainScript = scripts.get(0);
		List<Element> blocks = toList(XML.getChildrenByTagName(mainScript,
				"block", "custom-block"));
		Element hatBlock = blocks.get(0);
		String hatBlockId = hatBlock.getAttribute("id");
		if (hatBlock.getAttribute("s").length() > 0) {
			throw new RuntimeException("Improper editing block");
		}
		Element definition = hatBlock;
		while (!definition.getTagName().equals("custom-block")) {
			definition = (Element) definition.getFirstChild();
		}

		String name = definition.getAttribute("s");

		Script script = new Script(hatBlockId);
		for (int i = 1; i < blocks.size(); i++) {
			script.blocks.add((Block) XML.getCodeElement(blocks.get(i)));
		}

		boolean isImported = isImported(name);
		BlockDefinition def = new BlockDefinition(name, null, null, guid, isImported, script);

		for (int i = 1; i < scripts.size(); i++) {
			def.scripts.add(Script.parse(scripts.get(i)));
		}
		return def;
	}

	private static <E> List<E> toList(Iterable<E> iter) {
	    List<E> list = new ArrayList<>();
	    for (E item : iter) {
	        list.add(item);
	    }
	    return list;
	}

	@Override
	public String toCode(boolean canon) {
		return new CodeBuilder(canon)
		.add(name)
		.addSParameters(inputs)
		.indent()
		.add("script: ")
		.add(script)
		// TODO: These should be included in canon, just filtered out for solutions as side scripts.
		// We can reuse side-scripts in custom blocks just like in main scripts
		.ifNotCanon()
		.add("scripts:")
		.indent()
		.add(Sprite.getScripts(scripts, canon))
		.close()
		.endIf()
		.close()
		.end();
	}

	@Override
	public void addChildren(boolean canon, Accumulator ac) {
		ac.add(script);
		addVariables(ac, inputs);
		// We ignore aditional scripts if canonizing because they're generally not hintable
		// TODO: make a more generic solution for ignoring things for hints but not generally
		if (!canon) ac.add(scripts);
	}

	@Override
	public String getID() {
		if (guid != null && guid.length() > 0) return guid;
		return String.format("%s[%s,%s,%s](%s)", parentID, name, type, category, inputs.toString());
	}

	public final static String EVALUATE_CUSTOM_BLOCK = "evaluateCustomBlock";

	private static String getCustomBlockSelector(String name) {
		return name.replace("%s", "_").replaceAll("\\s", "").replaceAll("[^A-Za-z_]", "*");
	}

	public static String getCustomBlockCall(String name, boolean isImported) {
		if (isImported) {
			return getCustomBlockSelector(normalizeSelector(name));
		}
		return EVALUATE_CUSTOM_BLOCK;
	}
}
