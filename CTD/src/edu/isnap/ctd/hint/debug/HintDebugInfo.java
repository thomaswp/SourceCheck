package edu.isnap.ctd.hint.debug;

import java.util.List;

import org.json.JSONObject;

import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.NodeAlignment.Mapping;

public class HintDebugInfo {

	public final Mapping mapping;
	public final List<EditHint> edits;

	public HintDebugInfo(Mapping mapping, List<EditHint> edits) {
		this.mapping = mapping;
		this.edits = edits;
	}

	public JSONObject toJSON() {
		JSONObject info = new JSONObject();
		info.put("from", mapping.from.toJSON());
		info.put("to", mapping.to.toJSON());
		return info;
	}
}
