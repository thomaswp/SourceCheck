package edu.isnap.datasets;

import java.util.Date;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.hint.ConfigurableAssignment;
import edu.isnap.hint.SnapHintConfig;

public class MTurkSolutions extends Dataset {

	public final static Date start = null;
	public final static String dataDir = "../data/mturk/solutions";
	public final static MTurkSolutions instance = new MTurkSolutions();

	private static class TemplateAssignment extends ConfigurableAssignment {

		public TemplateAssignment(String name) {
			super(instance, name, null, false);
		}

		@Override
		public SnapHintConfig getConfig() {
			SnapHintConfig config = new SnapHintConfig();
			config.preprocessSolutions = false;
			return config;
		}
	}

	public final static Assignment PolygonMakerSimple =
			new TemplateAssignment("polygonMakerSimple");
	public final static Assignment DrawTriangles =
			new TemplateAssignment("drawTriangles");

	public final static Assignment[] All = {
		PolygonMakerSimple
	};

	private MTurkSolutions() {
		super(start, dataDir);
	}

	@Override
	public Assignment[] all() {
		return All;
	}

}