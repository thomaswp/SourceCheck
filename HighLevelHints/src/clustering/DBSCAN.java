package clustering;

import net.sf.javaml.core.*;
import net.sf.javaml.clustering.*;
import net.sf.javaml.distance.*;

public class DBSCAN {
	public static Dataset[] fit_predict(Dataset data) {
		Clusterer dbscan = new DensityBasedSpatialClustering();
		return dbscan.cluster(data);
	}
	
	public static Dataset[] fit_predict(Dataset data, double epsilon, int minPoints) {
		Clusterer dbscan = new DensityBasedSpatialClustering(epsilon, minPoints);
		return dbscan.cluster(data);
	}
	
	public static Dataset[] fit_predict(Dataset data, double epsilon, int minPoints, DistanceMeasure dm) {
		Clusterer dbscan = new DensityBasedSpatialClustering(epsilon, minPoints, dm);
		return dbscan.cluster(data);
	}
}
