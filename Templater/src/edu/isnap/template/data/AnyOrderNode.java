package edu.isnap.template.data;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class AnyOrderNode extends DefaultNode {

	@Override
	public boolean inline() {
		return true;
	}

	@Override
	public List<BNode> getVariants(Context context) {
		if (children.size() == 0) {
			List<BNode> variants = new LinkedList<>();
			variants.add(new BNode(type, inline()));
			return variants;
		}

		int childSize = children.size();
		final List<List<BNode>> orderedChildVariants = new LinkedList<>();
		for (int i = 0; i < childSize; i++) {
			List<BNode> vars = children.get(i).getVariants(context);
			if (vars.size() > 0) {
				orderedChildVariants.add(vars);
			}
		}

		final List<BNode> variants = new ArrayList<>();
		permuteIndices(childSize, new PermuteCallback() {
			@Override
			public void run(int[] indices) {
				List<List<BNode>> childVariants = new LinkedList<>();
				for (int j = 0; j < indices.length; j++) {
					childVariants.add(orderedChildVariants.get(indices[j]));
				}
				variants.addAll(getVariants(childVariants));
			}
		});
		return variants;
	}

	private interface PermuteCallback {
		void run(int[] indices);
	}


	private static void permuteIndices(int size, PermuteCallback callback){
		int[] array = new int[size];
		for (int i = 0; i < size; i++) {
			array[i] = i;
		}
		permuteIndices(array, 0, callback);
	}

	// Credit: http://stackoverflow.com/a/30387403/816458
	private static void permuteIndices(int[] arr, int index, PermuteCallback callback){
	    if(index >= arr.length - 1){ //If we are at the last element - nothing left to permute
	        callback.run(arr);
	        return;
	    }

	    for(int i = index; i < arr.length; i++){ //For each index in the sub array arr[index...end]

	        //Swap the elements at indices index and i
	        int t = arr[index];
	        arr[index] = arr[i];
	        arr[i] = t;

	        //Recurse on the sub array arr[index+1...end]
	        permuteIndices(arr, index+1, callback);

	        //Swap the elements back
	        t = arr[index];
	        arr[index] = arr[i];
	        arr[i] = t;
	    }
	}
}
