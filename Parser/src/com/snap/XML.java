package com.snap;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.snap.data.BlockDefinition;
import com.snap.data.CallBlock;
import com.snap.data.Code;
import com.snap.data.ListBlock;
import com.snap.data.LiteralBlock;
import com.snap.data.Script;
import com.snap.data.Sprite;

public class XML {
	public static Iterable<Element> getChildrenByTagName(Element e, String... tags) {
		return getChildrenByTagName(e, new TagsPredicate(tags));
	}
	
	private static Iterable<Element> getChildrenByTagName(Element e, Predicate pred) {
		List<Element> list = new ArrayList<Element>();
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
		if (child == null) return new ArrayList<Element>();
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
	
	//TODO: autolambda should have a structure
	public final static Predicate ignorePredicate = new TagsPredicate("watcher", "comment", "autolambda");
	
	public static List<Code> getCodeInFirstChild(Element element, String childTag) {
		return getCode(getFirstChildByTagName(element, childTag));
	}
	
	public static List<Code> getCode(Element element) {
		List<Code> children = new ArrayList<Code>();
		Node child = element.getFirstChild();
		while (child != null) {
			if (child instanceof Element) {
				Element childElement = (Element) child;
				if (callBlockPredicate.is(childElement)) {
					children.add(CallBlock.parse(childElement));
				} else if (literalBlockPredicate.is(childElement)) {
					children.add(LiteralBlock.parse(childElement));
				} else if (listBlockPredicate.is(childElement)) {
					children.add(ListBlock.parse(childElement));
				} else if (blockDefinitionPredicate.is(childElement)) {
					children.add(BlockDefinition.parse(childElement));
				} else if (scriptPredicate.is(childElement)) {
					children.add(Script.parse(childElement));
				} else if (spritePredicate.is(childElement)) {
					children.add(Sprite.parse(childElement));
				} else if (!ignorePredicate.is(childElement)) {
					throw new RuntimeException("No data-structure for: " + childElement.getTagName());
				}
			}
			child = child.getNextSibling();
		}
		return children;
	}
	
	public static void ensureEmpty(Element element, String... tags) {
		for (String list : tags) {
			if (XML.getFirstChildByTagName(element, list).getChildNodes().getLength() > 0) {
				throw new RuntimeException("Unknown child in " + list);
			}
		}
	}
}
