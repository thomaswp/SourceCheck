package edu.isnap.eval.tutor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import astrecognition.model.Convert;
import astrecognition.model.Tree;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.hint.IDataConsumer;
import edu.isnap.hint.IDataModel;
import edu.isnap.hint.SolutionsModel;
import edu.isnap.node.ASTNode;
import edu.isnap.node.Node;
import edu.isnap.rating.data.HintOutcome;
import edu.isnap.rating.data.HintRequest;
import edu.isnap.rating.data.Trace;
import edu.isnap.rating.data.TrainingDataset;
import edu.isnap.util.map.IdentityHashSet;
import pqgram.PQGram;
import pqgram.PQGramRecommendation;
import pqgram.Profile;
import pqgram.edits.Deletion;
import pqgram.edits.Edit;

public class PQGramHintSet extends HintDataHintSet {

	private final static int P = 2, Q = 3;

	private final Map<String, List<Tree>> solutionsMap = new HashMap<>();
	private final HashMap<String, Integer> labelMap = new HashMap<>();

	private final static IDataConsumer DataConsumer = new IDataConsumer() {
		@Override
		public IDataModel[] getRequiredData(HintData data) {
			return new IDataModel[] {
					new SolutionsModel(),
			};
		}
	};

	@Override
	public IDataConsumer getDataConsumer() {
		return DataConsumer;
	}

	public PQGramHintSet(String name, HintConfig hintConfig, TrainingDataset dataset) {
		super(name, hintConfig);
		for (String assignmentID : dataset.getAssignmentIDs()) {
			List<Trace> traces = dataset.getTraces(assignmentID);
			HintData data = createHintData(assignmentID, hintConfig, traces);
			solutionsMap.put(assignmentID,
					data.getModel(SolutionsModel.class).getSolutions().stream()
					.map(this::treeToNode)
					.collect(Collectors.toList()));
		}
	}

	@Override
	public HintDataHintSet addHints(List<HintRequest> requests) {
		for (HintRequest request : requests) {
			List<Tree> solutions = this.solutionsMap.get(request.assignmentID);
			Node code = JsonAST.toNode(request.code, hintConfig.getNodeConstructor());
			code = hintConfig.areNodeIDsConsistent() ? code.copy() : copyWithIDs(code);

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

			removeRedundantDeletions(edits);

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

	// Get rid of any deletion that is implicit because an ancestor of the deleted node is
	// also deleted
	private static void removeRedundantDeletions(List<Edit> edits) {
		List<Deletion> deletions = edits.stream()
				.filter(edit -> edit instanceof Deletion)
				.map(edit -> (Deletion) edit)
				.collect(Collectors.toList());
		// Use an identity hash map, since we need to match nodes by identity
		IdentityHashSet<Node> deleted = new IdentityHashSet<>();
		deletions.forEach(deletion -> deleted.add(deletion.deletedNode()));

		for (Deletion deletion : deletions) {
			Node ancestor = deletion.parentNode();
			while (ancestor != null) {
				if (deleted.contains(ancestor)) {
					edits.remove(deletion);
					break;
				}
				ancestor = ancestor.parent;
			}
		}
	}

	// TODO: should we have a flag for using the values somehow?
	private Tree treeToNode(Node node) {
		Tree tree = Convert.nodeToTree(node);
		tree.makeLabelsUnique(labelMap);
		return tree;
	}
}
