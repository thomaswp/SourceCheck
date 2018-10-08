package edu.isnap.rating.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HintRequestDataset extends TraceDataset {

	private HintRequestDataset(String name) {
		super(name);
	}

	private final List<HintRequest> allRequests = new ArrayList<>();

	public List<HintRequest> getAllRequests() {
		return Collections.unmodifiableList(allRequests);
	}

	@Deprecated
	public static HintRequestDataset fromDirectory(String name, String directory) throws IOException {
		HintRequestDataset dataset = new HintRequestDataset(name);
		dataset.addDirectory(directory);
		dataset.createRequests();
		return dataset;
	}

	public static HintRequestDataset fromSpreadsheet(String name, String path) throws IOException {
		HintRequestDataset dataset = new HintRequestDataset(name);
		dataset.addSpreadsheet(path);
		dataset.createRequests();
		return dataset;
	}

	private void createRequests() {
		for (List<Trace> traces : traceMap.values()) {
			traces.stream().map(HintRequest::new).forEach(allRequests::add);
		}
	}
}
