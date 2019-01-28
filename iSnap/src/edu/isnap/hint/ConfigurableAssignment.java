package edu.isnap.hint;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;

public class ConfigurableAssignment extends Assignment implements Configurable {

	public final boolean hasNodeIDs;

	public ConfigurableAssignment(Dataset dataset, String name, Date end, boolean hasNodeIDs,
			boolean graded, Assignment prequel) {
		super(dataset, name, end, graded, prequel);
		this.hasNodeIDs = hasNodeIDs;
	}

	public ConfigurableAssignment(Dataset dataset, String name, Date end, boolean hasNodeIDs) {
		super(dataset, name, end);
		this.hasNodeIDs = hasNodeIDs;
	}

	public static HintConfig getConfig(Assignment assignment) {
		SnapHintConfig config;
		if (assignment instanceof Configurable) {
			config = ((Configurable) assignment).getConfig();
		} else {
			config = new SnapHintConfig();
		}
		return config;
	}

	@Override
	public SnapHintConfig getConfig() {
		SnapHintConfig config = new SnapHintConfig();
		config.hasIDs = hasNodeIDs;
		return config;
	}
}
