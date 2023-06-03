package main;

public class Solution implements Cloneable{
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
    
	@Override
	public Solution clone() {
        try {
            Solution cloned = (Solution) super.clone();
            cloned.roster = this.cloneRoster();
            cloned.nurseScores = this.cloneNurseScores();
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
    
    private String[][] cloneRoster() {
        String[][] clonedRoster = new String[this.roster.length][];
        for (int i = 0; i < this.roster.length; i++) {
            clonedRoster[i] = this.roster[i].clone();
        }
        return clonedRoster;
    }
    
    private int[] cloneNurseScores() {
        return this.nurseScores.clone();
    }
}
