package edu.isnap.eval.util;

public class PrintUpdater {
	private final int maxCount;
	private int count;
	
	public PrintUpdater(int maxCount) {
		this.maxCount = maxCount;
	}
	
	public void update(double progress) {
		int newCount = (int) Math.round(maxCount * progress);
		for (int i = count; i < newCount; i++) System.out.print("+");
		count = newCount;
	}
}
