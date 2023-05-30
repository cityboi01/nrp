package main;

public class Solution {
	private String[][] roster;
	private int score;
	private int[] nurseScores;
	
	public Solution(String[][] roster, int score, int[] nurseScores) {
		this.roster = roster;
        this.score = score;
        this.nurseScores = nurseScores;
	}
	
	public String[][] getRoster() {
        return this.roster;
    }
	
	public void setRoster(String[][] roster) {
		this.roster = roster;
	}

    public int getScore() {
        return this.score;
    }
    
    public void setScore(int score) {
    	this.score = score;
    }

	public int[] getNurseScores() {
		return nurseScores;
	}

	public void setNurseScores(int nurse, int nurseScore) {
		this.nurseScores[nurse] = nurseScore;
	}
    
    
}
