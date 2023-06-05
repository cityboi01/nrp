package main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Permutations {
    public List<ArrayList<String>> generatePermutations(String[] strings, int k) {
    	String[] modStrings = substituteDH(strings);
    	modStrings = repeatElements(modStrings);
        List<String> permutations = new ArrayList<>();
        backtrack(modStrings, new StringBuilder(), new boolean[modStrings.length], k, permutations);
        return splitAndResubstitutePermutations(permutations);
    }
    
    public static String[] repeatElements(String[] originalArray) {
        int originalLength = originalArray.length;
        int newLength = originalLength * 3;
        String[] repeatedArray = new String[newLength];
        
        for (int i = 0; i < originalLength; i++) {
            String originalElement = originalArray[i];
            for (int j = 0; j < 3; j++) {
                int index = (i * 3) + j;
                repeatedArray[index] = originalElement;
            }
        }
        
        return repeatedArray;
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

    private static void backtrack(String[] strings, StringBuilder current, boolean[] used, int k, List<String> permutations) {
        if (current.length() == k) {
            permutations.add(current.toString());
            return;
        }

        for (int i = 0; i < strings.length; i++) {
            if (used[i]) {
                continue;
            }

            used[i] = true;
            current.append(strings[i]);
            backtrack(strings, current, used, k, permutations);
            current.deleteCharAt(current.length() - 1);
            used[i] = false;
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
