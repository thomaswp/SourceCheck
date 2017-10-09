package edu.isnap.ctd.hint.debug;

import java.util.List;

import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.NodeAlignment.Mapping;
import edu.isnap.ctd.util.map.BiMap;

public class HintDebugInfo {

	public final Mapping mapping;
	public final List<EditHint> edits;

	public HintDebugInfo(Mapping mapping, List<EditHint> edits) {
		this.mapping = mapping;
		this.edits = edits;
	}

	public JSONObject toJSON() {
		JSONObject info = new JSONObject();
		info.put("debug", true);
		info.put("from", mapping.from.toJSON());
		info.put("to", mapping.to.toJSON());
		info.put("mapping", getMappingJSON());

		return info;
	}

	private JSONObject getMappingJSON() {
		JSONObject json = new JSONObject();
		json.put("cost", mapping.cost());

		JSONObject nodeMapping = new JSONObject();
		for (Node from : mapping.keysetFrom()) {
			if (from.id == null) continue;
			Node to = mapping.getFrom(from);
			nodeMapping.put(from.id, to.id);
		}
		json.put("nodeMapping", nodeMapping);

		JSONObject valueMappings = new JSONObject();
		for (String[] types : mapping.config.valueMappedTypes) {
			if (types.length == 0) continue;
			String type = types[0];
			BiMap<String, String> map = mapping.valueMappings.get(type);
			if (map == null) continue;
			JSONObject mapping = new JSONObject();
			for (String key : map.keysetFrom()) {
				mapping.put(key, map.getFrom(key));
			}
			valueMappings.put(type, mapping);
		}
		json.put("valueMappings", valueMappings);

		json.put("itemizedCost", mapping.itemizedCostJSON());

		return json;
	}

//	private JSONArray getEdits() {
//		JSONArray array = new JSONArray();
//		for (EditHint edit : edits) {
//			JSONObject data = edit.data();
////			JSONObject outcome = edit.apply(applications);
//		}
//	}
}
