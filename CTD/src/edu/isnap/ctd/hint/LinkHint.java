package edu.isnap.ctd.hint;

import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.vector.VectorState;

public class LinkHint extends VectorHint {

	public final Node oldRoot;
	public final VectorState oldFrom;
	public final String oldRootPath;

	public LinkHint(VectorHint mainHint, VectorHint oldHint) {
		super(mainHint.root, mainHint.rootPathString, mainHint.from,
				mainHint.goal.limitTo(mainHint.from, oldHint.from),
				mainHint.goal, mainHint.caution);
		oldRoot = oldHint.root;
		oldFrom = oldHint.from;
		oldRootPath = oldHint.rootPathString;
	}

	@Override
	public String from() {
		return super.from() + " and " + oldRootPath + ": " + oldFrom;
	}

	@Override
	public String to() {
		return super.to() + " and " + oldRootPath + ": []";
	}

	@Override
	protected Map<String, String> dataMap() {
		Map<String, String> map =  super.dataMap();
		map.put("oldRoot", getNodeReference(oldRoot));
		map.put("oldFrom", oldFrom.toJson(swapArgs));
		map.put("oldTo", VectorState.empty().toJson());
		return map;
	}

	@Override
	public Node outcome() {
		throw new UnsupportedOperationException();
	}
}