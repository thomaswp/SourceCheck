package edu.isnap.rating;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HintRequestDataset extends TraceDataset {

	private HintRequestDataset() { }

	private final List<HintRequest> allRequests = new ArrayList<>();

	public List<HintRequest> getAllRequests() {
		return Collections.unmodifiableList(allRequests);
	}

	public static HintRequestDataset fromDirectory(String name, String directory) throws IOException {
		HintRequestDataset trainingDataset = new HintRequestDataset();
		addDirectory(trainingDataset, name, directory);
		for (List<Trace> traces : trainingDataset.tracesMap.values()) {
			traces.stream().map(HintRequest::new).forEach(trainingDataset.allRequests::add);
		}
		return trainingDataset;
	}
}
