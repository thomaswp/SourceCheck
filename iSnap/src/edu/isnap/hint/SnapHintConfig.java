package edu.isnap.hint;

import java.util.Arrays;
import java.util.HashSet;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.NodeConstructor;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.rating.RatingConfig;

public class SnapHintConfig extends HintConfig {
	public boolean hasIDs = true;

	private static final long serialVersionUID = 1L;

	private final static String Script = "script";
	private final static String Literal = "literal";

	public SnapHintConfig() {
		// In snap, we currently ignore literal values that aren't mapped
		this.valuesPolicy = ValuesPolicy.MappedOnly;
		// iSnap doesn't currently support displaying these, so they should not be used except in
		// algorithm evaluation
		this.createSubedits = false;
	}

	@Override
	public NodeConstructor getNodeConstructor() {
		return SnapNode::new;
	}

	@Override
	public boolean canMove(Node node) {
		// TODO: There are more nodes here that can't move, e.g. varMenu
		return node != null && !node.hasType(Script) && !node.hasType(Literal) &&
				node.hasAncestor(new Node.TypePredicate(Script));
	}

	@Override
	public boolean isOrderInvariant(String type) {
		return Script.equals(type);
	}

	@Override
	public boolean hasFixedChildren(Node node) {
		return node != null && RatingConfig.Snap.hasFixedChildren(node.type(), node.parentType());
	}

	@Override
	public boolean isScript(String type) {
		return Script.equals(type);
	}

	@Override
	public boolean isValueless(String type) {
		// Nodes inserted by Snap automatically are valueless
		// TODO: Decide whether scripts and lists should have value
		return "literal".equals(type); // || "script".equals(type) || "list".equals(type);
	}

	public final HashSet<String> haveSideScripts = new HashSet<>(Arrays.asList(
			new String[] {
					"stage",
					"sprite",
			}
	));

	@Override
	public boolean hasSideScripts(String type) {
		return haveSideScripts.contains(type);
	}

	public final HashSet<String> containers = new HashSet<>(Arrays.asList(
			new String[] {
					"sprite",
			}
	));

	@Override
	public boolean isContainer(String type) {
		return containers.contains(type);
	}

	public final HashSet<String> harmlessTypes = new HashSet<>(Arrays.asList(
			new String[] {
					"receiveGo",
					"setColor",
					"setHue",
					"clear",
					"receiveKey",
					"receiveInteraction",
			}
	));

	@Override
	public boolean isHarmlessType(String type) {
		return harmlessTypes.contains(type);
	}

	private final String[][] valueMappedTypes = {
			new String[] { "var", "varDec", "varMenu" },
			new String[] { "customBlock", "evaluateCustomBlock" },
	};

	@Override
	public String[][] getValueMappedTypes() {
		return valueMappedTypes;
	}

	@Override
	public boolean shouldAutoAdd(Node node) {
		return false;
	}

	// BEGIN old CTD context attributes

	public final HashSet<String> badContext = new HashSet<>();
	{
		for (String c : new String[] {
				// These control structures hold exactly one script
				"doIf",
				"doUntil",
				// Sprites' children are unordered
				"sprite",
				// Custom block inputs aren't usually added up front, so they're more distracting
				// than helpful, and block creation order has more bearing on target goal
				"customBlock",
		}) {
			badContext.add(c);
		}
	}

	@Override
	@Deprecated
	public boolean isBadContext(String type) {
		return badContext.contains(type);
	}

	/**
	 * When we have hints for these parent blocks, we should go straight to the goal, since there's
	 * no point in, e.g., leading them through adding one variable, then another. These are the
	 * "structure hints" on the client side
	 */
	public final HashSet<String> straightToGoal = new HashSet<>(Arrays.asList(
			new String[] {
					"snapshot",
					"stage",
					"sprite",
					"customBlock",
			}
	));

	@Override
	@Deprecated
	public boolean shouldGoStraightToGoal(String type) {
		return straightToGoal.contains(type);
	}

	@Override
	public boolean areNodeIDsConsistent() {
		return hasIDs;
	}
}
