package main;

import java.util.ArrayList;
import java.util.List;

public class Permutations {
    public List<String> generatePermutations(String[] strings, int k) {
        List<String> permutations = new ArrayList<>();
        backtrack(strings, new StringBuilder(), new boolean[strings.length], k, permutations);
        return permutations;
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
