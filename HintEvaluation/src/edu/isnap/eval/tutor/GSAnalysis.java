package edu.isnap.eval.tutor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.Bag;

import costmodel.CostModel;
import distance.APTED;
import edu.isnap.node.ASTNode;
import edu.isnap.node.ASTSnapshot;
import edu.isnap.rating.EditExtractor;
import edu.isnap.rating.EditExtractor.Edit;
import edu.isnap.rating.data.GoldStandard;
import edu.isnap.rating.data.HintRequestDataset;
import edu.isnap.rating.data.Trace;
import edu.isnap.rating.data.TrainingDataset;
import edu.isnap.rating.data.TutorHint;
import edu.isnap.util.Spreadsheet;
import edu.isnap.util.map.ListMap;
import edu.isnap.rating.RatingConfig;
import node.Node;

public class GSAnalysis {

	public static void writeAnalysis(String path, GoldStandard goldStandard,
			TrainingDataset training, HintRequestDataset requests, RatingConfig config)
			throws FileNotFoundException, IOException {
		EditExtractor extractor = new EditExtractor(config, ASTNode.EMPTY_TYPE);
		FromStats fromStats = new FromStats(extractor, training);

		Map<String, Integer> traceLengths = requests.getAllRequests().stream()
			.collect(Collectors.toMap(
					request -> request.id,
					request -> request.history.size()));

		goldStandard.createHintsSpreadsheet((hint, spreadsheet) -> {
			spreadsheet.put("requestTreeSize", hint.from.treeSize());
			spreadsheet.put("traceLength", traceLengths.get(hint.requestID));
			Bag<Edit> edits = extractor.getEdits(hint.from, hint.to);
			EditExtractor.addEditInfo(spreadsheet, edits);
			fromStats.addToSpreadsheet(hint, spreadsheet);
		}).write(path);
	}

	private static CostModel<ASTNode> costModel = new CostModel<ASTNode>() {
		@Override
		public float ren(Node<ASTNode> nodeA, Node<ASTNode> nodeB) {
			// If the nodes are equal, there is no cost to "rename"
			if (nodeA.getNodeData().shallowEquals(nodeB.getNodeData(), false)) {
				return 0;
			}
			return 1f;
		}

		@Override
		public float ins(Node<ASTNode> node) {
			return 1f;
		}

		@Override
		public float del(Node<ASTNode> node) {
			return 1f;
		}
	};

	private static class FromStats {

		final APTED<CostModel<ASTNode>, ASTNode> apted = new APTED<>(costModel);
		final ListMap<String, ASTSnapshot> solutionMap = new ListMap<>();
		final Map<ASTSnapshot, Node<ASTNode>> nodeMap = new IdentityHashMap<>();
		final EditExtractor extractor;

		ASTNode lastFrom;
		double minEdits, medEdits, minAPTED, medAPTED;

		public FromStats(EditExtractor extractor, TrainingDataset training) {
			this.extractor = extractor;
			for (String assignmentID : training.getAssignmentIDs()) {
				List<Trace> traces = training.getTraces(assignmentID);
				List<ASTSnapshot> solutions = traces.stream()
						.map(trace -> trace.getFinalSnapshot())
						.collect(Collectors.toList());
				solutionMap.put(assignmentID, solutions);
				solutions.forEach(solution ->
					nodeMap.put(solution, EditExtractor.toNode(solution)));
			}
		}

		private void addToSpreadsheet(TutorHint hint, Spreadsheet spreadsheet) {
			update(hint);
			spreadsheet.put("minEdits", minEdits);
			spreadsheet.put("medEdits", medEdits);
			spreadsheet.put("minAPTED", minAPTED);
			spreadsheet.put("medAPTED", medAPTED);
		}

		private void update(TutorHint hint) {
			if (hint.from.equals(lastFrom)) return;
			lastFrom = hint.from;
			List<ASTSnapshot> solutions = solutionMap.get(hint.assignmentID);
			int[] editCounts = solutions.stream()
				.mapToInt(solution ->
					extractor.extractEditsUsingCodeAlign(hint.from, solution).size())
				.sorted().toArray();
			medEdits = editCounts.length % 2 == 0 ?
					(editCounts[editCounts.length / 2] +
							editCounts[editCounts.length / 2 + 1]) / 2.0 :
						editCounts[editCounts.length / 2];
			minEdits = editCounts[0];

			Node<ASTNode> fromNode = EditExtractor.toNode(hint.from);
			double[] apteds = solutions.stream()
					.map(solution -> nodeMap.get(solution))
					.mapToDouble(solution ->
						apted.computeEditDistance(fromNode, solution))
					.sorted().toArray();
			medAPTED = apteds.length % 2 == 0 ?
					(apteds[apteds.length / 2] + apteds[apteds.length / 2 + 1]) / 2 :
					apteds[apteds.length / 2];
			minAPTED = apteds[0];
		}
	}
}
