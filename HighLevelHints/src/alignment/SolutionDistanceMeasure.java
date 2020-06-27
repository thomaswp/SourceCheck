package alignment;

import edu.isnap.hint.HintConfig;
import edu.isnap.hint.util.Alignment;
import edu.isnap.node.Node;
import edu.isnap.sourcecheck.NodeAlignment.DistanceMeasure;
import edu.isnap.sourcecheck.NodeAlignment.ProgressDistanceMeasure;

public class SolutionDistanceMeasure extends ProgressDistanceMeasure {

	public SolutionDistanceMeasure(HintConfig config) {
		super(config);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public double measure(Node from, String[] a, String[] b, int[] bOrderGroups) {
//		if (this.config instanceof JavaSolutionConfig) {
//			JavaSolutionConfig config = (JavaSolutionConfig) this.config;
//			return SolutionAlignment.getNormalizedMissingNodeCount(a, b, config.baseReward, config.factor) 
//					+ SolutionAlignment.getNormalizedMissingNodeCount(b, a, config.baseReward, config.factor) 
//					+ SolutionAlignment.getFullOrderReward(
//							SolutionAlignment.getProgress(a, b, 1, 1), config.baseReward, config.factor) 
//					- (SolutionAlignment.getNormalizedProgress(
//							a, b, bOrderGroups, config.baseReward, config.outOfOrderReward, config.factor, new int[b.length])
//							+ SolutionAlignment.getNormalizedProgress(b, a, null, config.baseReward, config.outOfOrderReward, config.factor, new int[a.length])
//							) / 2;
//		}
		return SolutionAlignment.getMissingNodeCount(a, b) + SolutionAlignment.getMissingNodeCount(b, a) 
			+ inOrderReward * SolutionAlignment.getProgress(a, b, 1, 1) 
				- (SolutionAlignment.getProgress(a, b, bOrderGroups, inOrderReward, outOfOrderReward, 0)
						+ SolutionAlignment.getProgress(b, a, null, inOrderReward, outOfOrderReward, 0)) / 2;
	}
	
	@Override
	public double matchedOrphanReward(String type) {
		return 0;
	}
	public static void main(String[] args) {
		HintConfig temp = new JavaSolutionConfig();
		DistanceMeasure sol = new SolutionDistanceMeasure(temp);
		String[] a = {"1", "2", "3", "4", "5", "3"};
		String[] b = {"1", "2", "4", "4", "3", "5"};
		System.out.println(sol.measure(null, a, b, new int[b.length]));
		System.out.println(sol.measure(null, b, a, new int[a.length]));
	}
	
}
