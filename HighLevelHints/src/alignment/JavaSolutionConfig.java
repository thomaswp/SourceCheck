package alignment;

import edu.isnap.hint.HintConfig.ValuesPolicy;
import edu.isnap.java.JavaHintConfig;

public class JavaSolutionConfig extends JavaHintConfig {
	public double baseReward = 2;
	public double factor = 0.5;
	public double outOfOrderReward = 1;
	
	public JavaSolutionConfig() {
		super();
		progressMissingFactor = 0;
		valuesPolicy = ValuesPolicy.MatchAllExactly;
	}
}
