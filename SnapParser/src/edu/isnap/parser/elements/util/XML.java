package edu.isnap.parser.elements.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.isnap.parser.elements.BlockDefinition;
import edu.isnap.parser.elements.CallBlock;
import edu.isnap.parser.elements.Code;
import edu.isnap.parser.elements.ListBlock;
import edu.isnap.parser.elements.LiteralBlock;
import edu.isnap.parser.elements.Script;
import edu.isnap.parser.elements.Sprite;

public class XML {

	private static HashMap<String, Code> refMap = new HashMap<>();
	private static Set<String> importedBlockSelectors = new HashSet<>();

	public static void buildRefMap(Element root, String... tags) {
		NodeList blocks = root.getElementsByTagName("block-definition");
		for (int i = 0; i < blocks.getLength(); i++) {
			Element item = as(blocks.item(i), Element.class);
			if (item != null) {
				if (item.getAttribute("isImported").equals("true")) {
					String selector = BlockDefinition.normalizeSelector(item.getAttribute("s"));
					importedBlockSelectors.add(selector);
				}
			}
		}
		for (String tag : tags) {
			NodeList list = root.getElementsByTagName(tag);
			for (int i = 0; i < list.getLength(); i++) {
				Element item = as(list.item(i), Element.class);
				if (item != null) {
					String id = item.getAttribute("id");
					if (id != null) refMap.put(id, getCodeElement(item));
				}
			}
		}
	}

	public static void clearRefMap() {
		refMap.clear();
		importedBlockSelectors.clear();
	}

	public static boolean isImportedBlock(String selector) {
		return importedBlockSelectors.contains(selector);
	}

	@SuppressWarnings("unchecked")
	private static <T> T as(Object obj, Class<T> clazz) {
		if (obj == null) return null;
		if (clazz.isAssignableFrom(obj.getClass())) return (T) obj;
		return null;
	}

	public static Iterable<Element> getChildrenByTagName(Element e, String... tags) {
		return getChildrenByTagName(e, new TagsPredicate(tags));
	}

	private static Iterable<Element> getChildrenByTagName(Element e, Predicate pred) {
		List<Element> list = new ArrayList<>();
		Node child = e.getFirstChild();
		while (child != null) {
			if (child instanceof Element && pred.is((Element) child)) {
				list.add((Element) child);
			}
			child = child.getNextSibling();
		}
		return list;
	}

	public static Element getFirstChildByTagName(Element e, String... tags) {
		return getFirstChildByTagName(e, new TagsPredicate(tags));
	}

	public static Element getFirstChild(Element e) {
		return getFirstChildByTagName(e, new Predicate() {
			@Override
			public boolean is(Element e) {
				return true;
			}
		});
	}

	private static Element getFirstChildByTagName(Element e, Predicate pred) {
		Node child = e.getFirstChild();
		while (child != null) {
			if (child instanceof Element && pred.is((Element) child)) {
				break;
			}
			child = child.getNextSibling();
		}
		return (Element) child;
	}

	public static Iterable<Element> getGrandchildrenByTagName(Element e,
			String childTag, String... grandchildTags) {
		return getGrandchildrenByTagName(e, new TagsPredicate(childTag),
				new TagsPredicate(grandchildTags));
	}

	private static Iterable<Element> getGrandchildrenByTagName(Element e,
			Predicate childPred, Predicate grandchildPred) {
		Element child = getFirstChildByTagName(e, childPred);
		if (child == null) return new ArrayList<>();
		return getChildrenByTagName(child, grandchildPred);
	}

	private interface Predicate {
		boolean is(Element e);
	}

	private static class TagsPredicate implements Predicate {
		private final String[] tags;
		public TagsPredicate(String... tags) { this.tags = tags; }

		@Override
		public boolean is(Element e) {
			for (String tag : tags) {
				if (e.getTagName().equals(tag)) return true;
			}
			return false;
		}
	}

	public final static Predicate callBlockPredicate = new TagsPredicate("block", "custom-block");
	public final static Predicate literalBlockPredicate = new TagsPredicate("l", "color");
	public final static Predicate listBlockPredicate = new TagsPredicate("list");
	public final static Predicate blockDefinitionPredicate = new TagsPredicate("block-definition");
	public final static Predicate scriptPredicate = new TagsPredicate("script");
	public final static Predicate spritePredicate = new TagsPredicate("sprite");
	public final static Predicate refPredicate = new TagsPredicate("ref");

	//TODO: autolambda should have a structure
	// Variables for some reason show up in calls to custom blocks..
	public final static Predicate ignorePredicate = new TagsPredicate("watcher", "comment", "autolambda", "variables", "receiver");

	public static List<Code> getCodeInFirstChild(Element element, String childTag) {
		return getCode(getFirstChildByTagName(element, childTag));
	}

	public static List<Code> getCode(Element element) {
		List<Code> children = new ArrayList<>();
		Node child = element.getFirstChild();
		while (child != null) {
			if (child instanceof Element) {
				Element childElement = (Element) child;
				Code code = getCodeElement(childElement);
				if (code != null) children.add(code);
			}
			child = child.getNextSibling();
		}
		return children;
	}

	public static Code getCodeElement(Element childElement) {
		if (callBlockPredicate.is(childElement)) {
			return CallBlock.parse(childElement);
		} else if (literalBlockPredicate.is(childElement)) {
			return LiteralBlock.parse(childElement);
		} else if (listBlockPredicate.is(childElement)) {
			return ListBlock.parse(childElement);
		} else if (blockDefinitionPredicate.is(childElement)) {
			return BlockDefinition.parse(childElement);
		} else if (scriptPredicate.is(childElement)) {
			return Script.parse(childElement);
		} else if (spritePredicate.is(childElement)) {
			return Sprite.parse(childElement);
		} else if (refPredicate.is(childElement)) {
			String id = childElement.getAttribute("id");
			String parentNodeType = childElement.getParentNode() != null ?
				childElement.getParentNode().getNodeName() : null;
			if (!refMap.containsKey(id) &&
					// For some reason, there's a bug where ref get into block parameters, and they
					// don't appear to be referencing anything meaningful
					!"block".equals(parentNodeType)) {
				throw new RuntimeException("No ref for: " + id + " in " + parentNodeType);
			}
			return refMap.get(id);
		} else if (!ignorePredicate.is(childElement)) {
			throw new RuntimeException("No data-structure for: " + childElement.getTagName());
		}
		return null;
	}

	public static void ensureEmpty(Element element, String... tags) {
		for (String list : tags) {
			if (XML.getFirstChildByTagName(element, list).getChildNodes().getLength() > 0) {
				throw new RuntimeException("Unknown child in " + list);
			}
		}
	}
}
