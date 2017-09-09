package edu.isnap.eval.rules;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.RuleSet;
import edu.isnap.dataset.Assignment;
import edu.isnap.datasets.aggregate.CSC200;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class FindRules {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		Assignment assignment = CSC200.GuessingGame1;
		List<Node> solutions = assignment.load(Mode.Use, true, true,
				new SnapParser.LikelySubmittedOnly()).values().stream()
				.filter(a -> a.submittedSnapshot != null && a.grade != null &&
						a.grade.average() == 1)
				.map(a -> SimpleNodeBuilder.toTree(a.submittedSnapshot, true))
				.collect(Collectors.toList());
		System.out.println(assignment.name);
		new RuleSet(solutions, new HintConfig());

	}


}
