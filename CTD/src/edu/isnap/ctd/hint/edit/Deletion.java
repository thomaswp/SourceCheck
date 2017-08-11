package edu.isnap.ctd.hint.edit;

import java.util.List;

import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;

public class Deletion extends EditHint {
	public final Node node;

	@Override
	protected String action() {
		return "delete";
	}

	public Deletion(Node node) {
		super(node.parent);
		this.node = node;
	}

	@Override
	public JSONObject data() {
		JSONObject data = super.data();
		data.put("node", Node.getNodeReference(node));
		return data;
	}

	@Override
	protected void editChildren(List<String> children) {
		children.remove(node.index());
	}

	@Override
	protected double priority() {
		return 1;
	}

	@Override
	public void apply(List<Application> applications) {
		final int index = node.index();
		applications.add(new Application(parent, index, new EditAction() {
			@Override
			public void apply() {
				node.parent.children.remove(index);
			}
		}));
	}
}