package edu.isnap.eval.tutor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.ctd.util.NodeAlignment;
import edu.isnap.ctd.util.NodeAlignment.DistanceMeasure;
import edu.isnap.ctd.util.NodeAlignment.Mapping;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.rating.ColdStart;
import edu.isnap.rating.GoldStandard;
import edu.isnap.rating.HintRequest;
import edu.isnap.rating.HintSet;
import edu.isnap.rating.RateHints;
import edu.isnap.rating.RatingConfig;
import edu.isnap.rating.Trace;
import edu.isnap.rating.TrainingDataset;

public class HighlightHintGenerator implements ColdStart.HintGenerator {

	public static void main(String[] args) throws FileNotFoundException, IOException {

		TrainingDataset dataset = TrainingDataset.fromSpreadsheet("itap",
				RunTutorEdits.ITAPS16.getDataDir() + RateHints.TRAINING_FILE);
		for (String assignmentID : dataset.getAssignmentIDs()) {
			System.out.println(" ============= " + assignmentID + " ============= ");
			dataset.printAllSolutions(assignmentID, RatingConfig.Python, true);
		}
//		standard.printAllRequestNodes(hintGenerator.ratingConfig);
	}

	private final RatingConfig ratingConfig;
	private final HintConfig hintConfig;

	private HintMapBuilder builder;
	private HintHighlighter highlighter;


	public HighlightHintGenerator(HintConfig config) {
		this.hintConfig = config;
		this.ratingConfig = HighlightHintSet.getRatingConfig(config);
		clearTraces();
	}

	public HintHighlighter getHighlighter() {
		if (highlighter == null) {
			builder.finishedAdding();
			highlighter = builder.hintHighlighter();
		}
		return highlighter;
	}

	@Override
	public void clearTraces() {
		builder = new HintMapBuilder(new HintMap(hintConfig), 1);
		highlighter = null;
	}

	@Override
	public void addTrace(Trace trace) {
		List<Node> nodes = trace.stream()
				.map(node -> JsonAST.toNode(node, SnapNode::new))
				.collect(Collectors.toList());
		builder.addAttempt(nodes, ratingConfig.areNodeIDsConsistent());
		highlighter = null;
	}

	@Override
	public HintSet generateHints(String name, List<HintRequest> hintRequests) {
		HintHighlighter highlighter = getHighlighter();
		return new HighlightHintSet(name, hintConfig) {
			@Override
			protected HintHighlighter getHighlighter(HintRequest request, HintMap baseMap) {
				return highlighter;
			}
		}.addHints(hintRequests);
	}

	public static Spreadsheet getCostsSpreadsheet(TrainingDataset dataset,
			GoldStandard standard, HintConfig hintConfig) {
		Spreadsheet spreadsheet = new Spreadsheet();
		DistanceMeasure distanceMeasure = HintHighlighter.getDistanceMeasure(hintConfig);
		for (String assignmentID : dataset.getAssignmentIDs()) {
			List<Trace> allTraces = new ArrayList<>(dataset.getTraces(assignmentID));
			for (Trace trace : allTraces) {
				Node toNode = JsonAST.toNode(trace.getFinalSnapshot(), hintConfig.getNodeConstructor());
				for (String requestID : standard.getRequestIDs(assignmentID)) {
					ASTNode from = standard.getHintRequestNode(assignmentID, requestID);
					if (from == null) continue;
					Node fromNode = JsonAST.toNode(from, hintConfig.getNodeConstructor());
					Mapping mapping = new NodeAlignment(fromNode, toNode, hintConfig)
							.calculateMapping(distanceMeasure);

					spreadsheet.newRow();
					spreadsheet.put("traceID", trace.id);
					spreadsheet.put("requestID", requestID);
					spreadsheet.put("cost", mapping.cost());
				}
			}
		}
		return spreadsheet;
	}
}
