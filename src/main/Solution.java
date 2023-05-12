package main;

public class Solution {
	private String[][] roster;
	private int score;
	
	public Solution(String[][] roster, int score) {
		this.roster = roster;
        this.score = score;
	}
	
	public String[][] getRoster() {
        return roster;
    }

    public int getScore() {
        return score;
    }
}
