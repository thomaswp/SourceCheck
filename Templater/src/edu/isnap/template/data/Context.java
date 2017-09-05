package edu.isnap.template.data;

import java.util.HashMap;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;

public class Context {

	public HashMap<String, DefBlockNode> blocksDefs = new HashMap<>();
	public HashMap<String, String> varDefs = new HashMap<>();
	public HashMap<String, Integer> defaultAgs = new HashMap<>();

	private int nextOrderGroup = 1;

	public int nextOrderGroup() {
		return nextOrderGroup++;
	}

	public static Context fromSample(Node sample) {
		final Context context = new Context();
		sample.recurse(new Action() {
			@Override
			public void run(Node node) {
				int litArgs = 0;
				for (Node child : node.children) {
					if (!child.hasType("literal")) return;
					litArgs++;
				}
				Integer prevValue = context.defaultAgs.get(node.type());
				if (prevValue != null && prevValue != litArgs) {
					System.err.println("Multiple default arg values for " + node.type());
				}
				context.defaultAgs.put(node.type(), litArgs);
			}
		});
		return context;
	}

}
