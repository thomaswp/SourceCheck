package clustering;

import net.sf.javaml.distance.DistanceMeasure;

import java.util.Iterator;

import net.sf.javaml.core.*;

public class PreComputed implements DistanceMeasure {
	private double max;
	private double min;
	private Dataset data;
	
	public PreComputed(Dataset data) {
		this.data = data;
		this.max = Double.NEGATIVE_INFINITY;
		this.min = Double.POSITIVE_INFINITY;
		for (Instance i : data) {
			Iterator<Double> itr = i.iterator();
			while (itr.hasNext()) {
				double value = itr.next();
				if (value > this.max) this.max = value;
				if (value < this.min) this.min = value;
			}
		}
	}

	@Override
	public boolean compare(double arg0, double arg1) {
		return arg0 >= arg1;
	}

	@Override
	public double getMaxValue() {
		return this.max;
	}

	@Override
	public double getMinValue() {
		return this.min;
	}

	@Override
	public double measure(Instance arg0, Instance arg1) {
		int arg0Idx = data.indexOf(arg0);
		int arg1Idx = data.indexOf(arg1);
		
		// Check if the distance matrix is symmetric
		double arg0toarg1 = arg0.value(arg1Idx);
		double arg1toarg0 = arg1.value(arg0Idx);
		if (arg0toarg1 != arg1toarg0) {
			return (arg0toarg1 + arg1toarg0) / 2;
			//throw new RuntimeException("The precomputed distance matrix is not symmetric.");
		}
		return arg0toarg1;
	}
	
}

