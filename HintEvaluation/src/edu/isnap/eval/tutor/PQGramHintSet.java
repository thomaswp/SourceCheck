package edu.isnap.eval.tutor;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import astrecognition.model.Convert;
import astrecognition.model.Tree;
import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintMapBuilder;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.rating.HintOutcome;
import edu.isnap.rating.HintRequest;
import edu.isnap.rating.Trace;
import edu.isnap.rating.TrainingDataset;
import pqgram.PQGram;
import pqgram.PQGramRecommendation;
import pqgram.Profile;
import pqgram.edits.Edit;

public class PQGramHintSet extends HintMapHintSet {

	private final static int P = 2, Q = 3;

	private final Map<String, List<Tree>> solutionsMap = new HashMap<>();
	private final HashMap<String, Integer> labelMap = new HashMap<>();

	public PQGramHintSet(String name, HintConfig hintConfig, String directory)
			throws IOException {
		this(name, hintConfig, TrainingDataset.fromDirectory(name, directory));
	}

	public PQGramHintSet(String name, HintConfig hintConfig, TrainingDataset dataset) {
		super(name, hintConfig);
		for (String assignmentID : dataset.getAssignmentIDs()) {
			List<Trace> traces = dataset.getTraces(assignmentID);
			HintMapBuilder builder = createHintBuilder(hintConfig, traces);
			solutionsMap.put(assignmentID, builder.hintMap.solutions.stream()
					.map(this::treeToNode)
					.collect(Collectors.toList()));
		}
	}

	@Override
	public HintMapHintSet addHints(List<HintRequest> requests) {
		for (HintRequest request : requests) {
			List<Tree> solutions = this.solutionsMap.get(request.assignmentID);
			Node code = JsonAST.toNode(request.code, hintConfig.getNodeConstructor());
			code = config.areNodeIDsConsistent() ? code.copy() : copyWithIDs(code);

			Tree fromTree = treeToNode(code);

			Map<Tree, Double> distances = new IdentityHashMap<>();
			for (Tree solution : solutions) {
				distances.put(solution, PQGram.getDistance(fromTree, solution, P, Q));
			}
			solutions.sort(Comparator.comparing((Tree s) -> distances.get(s))
					.thenComparing(s -> s.tag.treeSize()));

			Tree toTree = solutions.get(0);

			Profile fromProfile = PQGram.getProfile(fromTree, P, Q);
			Profile toProfile = PQGram.getProfile(toTree, P, Q);

			List<Edit> edits = PQGramRecommendation.getEdits(fromProfile, toProfile,
					fromTree, toTree);

			for (Edit edit : edits) {
				Node to = edit.outcome(code);
				// This can happen if a hint cannot be implemented yet
				if (to == null) continue;
				to = to.root();
				ASTNode outcomeNode = to.toASTNode();
				if (outcomeNode.hasType("snapshot")) outcomeNode.type = "Snap!shot";
				HintOutcome outcome = new HintOutcome(outcomeNode, request.assignmentID,
						request.id, 1);
				add(outcome);
			}
		}
		finish();
		return this;
	}

	private Tree treeToNode(Node node) {
		Tree tree = Convert.nodeToTree(node);
		tree.makeLabelsUnique(labelMap);
		return tree;
	}
}
