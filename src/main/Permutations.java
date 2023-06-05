package main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Permutations {
	public List<ArrayList<String>> generatePermutations(String[] strings, int k) {
		String[] modStrings = substituteDH(strings);
		List<String> permutations = new ArrayList<>();
		backtrack(modStrings, new StringBuilder(), new ArrayList<>(), k, permutations);
//		System.out.println("Number of permutations of length " + k + ": " + permutations.size());
		
		return splitAndResubstitutePermutations(permutations);
	}



	private String[] substituteDH(String[] strings) {
		String[] modifiedStrings = Arrays.copyOf(strings, strings.length);
		for (int i = 0; i < modifiedStrings.length; i++) {
			if (modifiedStrings[i].equals("DH")) {
				modifiedStrings[i] = "H";
			}
		}
		return modifiedStrings;
	}

	private List<ArrayList<String>> splitAndResubstitutePermutations(List<String> permutations) {
		List<ArrayList<String>> splitStrings = new ArrayList<>();
		for (String permutation : permutations) {
			ArrayList<String> split = new ArrayList<>();
			for (int i = 0; i < permutation.length(); i++) {
				String substring = Character.toString(permutation.charAt(i));
				if(!substring.equals("H")) {
					split.add(substring);
				}
				else {
					split.add("DH");
				}

			}
			splitStrings.add(split);
		}
		return splitStrings;
	}

	private static void backtrack(String[] strings, StringBuilder current, List<Integer> usedIndices, int k, List<String> permutations) {
		if (current.length() == k) {
			permutations.add(current.toString());
			return;
		}

		for (int i = 0; i < strings.length; i++) {
			usedIndices.add(i);
			current.append(strings[i]);
			backtrack(strings, current, usedIndices, k, permutations);
			current.deleteCharAt(current.length() - 1);
			usedIndices.remove(usedIndices.size() - 1);
		}
	}


/*public static void main(String[] args) {
        String[] strings = {"A", "B", "C", "D"};
        int k = 2;

        List<String> permutations = generatePermutations(strings, k);
        for (String permutation : permutations) {
            System.out.println(permutation);
        }
    }*/
}
