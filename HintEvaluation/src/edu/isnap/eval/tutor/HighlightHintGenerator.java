package edu.isnap.eval.tutor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.node.ASTNode;
import edu.isnap.node.Node;
import edu.isnap.rating.ColdStart;
import edu.isnap.rating.HintRater;
import edu.isnap.rating.RatingConfig;
import edu.isnap.rating.data.GoldStandard;
import edu.isnap.rating.data.HintRequest;
import edu.isnap.rating.data.HintSet;
import edu.isnap.rating.data.Trace;
import edu.isnap.rating.data.TrainingDataset;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment;
import edu.isnap.sourcecheck.NodeAlignment.DistanceMeasure;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.util.Spreadsheet;

public class HighlightHintGenerator implements ColdStart.IHintGenerator {

	public static void main(String[] args) throws FileNotFoundException, IOException {

		TrainingDataset dataset = TrainingDataset.fromSpreadsheet("itap",
				RunTutorEdits.ITAPS16.getDataDir() + HintRater.TRAINING_FILE);
		for (String assignmentID : dataset.getAssignmentIDs()) {
			System.out.println(" ============= " + assignmentID + " ============= ");
			dataset.printAllSolutions(assignmentID, RatingConfig.Python, true);
		}
//		standard.printAllRequestNodes(hintGenerator.ratingConfig);
	}

	private final HintConfig hintConfig;

	private HintData hintData;
	private HintHighlighter highlighter;


	public HighlightHintGenerator(HintConfig config) {
		this.hintConfig = config;
		clearTraces();
	}

	public HintHighlighter getHighlighter() {
		if (highlighter == null) {
			hintData.finished();
			highlighter = new HintHighlighter(hintData);
		}
		return highlighter;
	}

	@Override
	public void clearTraces() {
		hintData = new HintData(null, hintConfig, 1, HintHighlighter.DataConsumer);
		highlighter = null;
	}

	@Override
	public void addTrace(Trace trace) {
		List<Node> nodes = trace.stream()
				.map(node -> JsonAST.toNode(node, SnapNode::new))
				.collect(Collectors.toList());
		hintData.addTrace(trace.id, nodes);
		highlighter = null;
	}

	@Override
	public HintSet generateHints(String name, RatingConfig ratingConfig,
			List<HintRequest> hintRequests) {
		HintHighlighter highlighter = getHighlighter();
		return new HighlightHintSet(name, hintConfig) {
			@Override
			protected HintHighlighter getHighlighter(HintRequest request) {
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
