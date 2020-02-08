
# HintEvaluation

The HintEvaluation project contains a number of classes which perform automated, technical evaluations of the Snap hints. Some of these evaluations methods may be a bit dated, since they were designed for an older version of the algorithm.

Most evaluation logic can be found in the [edu.isnap.eval](src/edu/isnap/eval) package. These classes have main methods, which mainly produce .csv files in the analysis folder of a given dataset. These are used by the R scripts, found in the [R folder](../R).

This would be an appropriate project to add additional evaluation scripts.