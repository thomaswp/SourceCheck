package edu.isnap.ctd.hint;

import org.json.JSONObject;

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
	public JSONObject data() {
		JSONObject data =  super.data();
		data.put("oldRoot", Node.getNodeReference(oldRoot));
		data.put("oldFrom", oldFrom.toJSON(swapArgs));
		data.put("oldTo", VectorState.empty().toJSON(false));
		return data;
	}

	@Override
	public Node outcome() {
		throw new UnsupportedOperationException();
	}
}