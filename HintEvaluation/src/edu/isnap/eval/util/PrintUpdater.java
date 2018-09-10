package edu.isnap.eval.util;

public class PrintUpdater {
	private final int maxCount;
	private int count;
	private double value;
	private int maxValue;

	public PrintUpdater() {
		this(50);
	}

	public PrintUpdater(int maxCount) {
		this(maxCount, 1);
	}

	public PrintUpdater(int maxCount, int maxValue) {
		this.maxCount = maxCount;
		this.maxValue = maxValue;
	}

	public void updateTo(double progress) {
		int newCount = (int) Math.round(maxCount * progress);
		value = maxValue * progress;
		for (int i = count; i < newCount; i++) System.out.print("+");
		count = newCount;
	}

	public void incrementValue() {
		addValue(1);
	}

	public void addValue(int amount) {
		value += amount;
		updateTo(value / maxValue);
	}

	public void setValue(int value) {
		this.value = value;
		updateTo(value / maxValue);
	}
}
