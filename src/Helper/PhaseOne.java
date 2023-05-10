package Helper;

import Attributes.Employee;
import Attributes.SchedulingPeriod;
import Attributes.Skill;

import java.util.List;
import java.util.Random;



public class PhaseOne {
	private Solution initialSolution;
    private SchedulingPeriod schedulingPeriod;
    private Helper helper;

    public PhaseOne(Solution initialSolution, SchedulingPeriod schedulingPeriod) {
        this.initialSolution = initialSolution;
        this.schedulingPeriod = schedulingPeriod;
        helper = new Helper(this.schedulingPeriod,null);
    }
    
    public int[][][] createMatrixNoType(int numNurses) {
		int[][][] PhaseOne = new int [numNurses][128][7];
		
		for(int i=0; i<numNurses; i++) {
			for(int j=0; j<128; j++) {
				for(int d=1; d<8; d++) {
					PhaseOne[i][j][d] = helper.getEntryAijd(i,j,d);
							
				}
			}
		}
		return PhaseOne;
	}
	
	public int[][][][] createMatrixType(int numNurses, int numTypes) {	
		int[][][][] PhaseOne = new int [numNurses][128][numTypes][7];
		
		for(int i=0; i<numNurses; i++) {
			for(int j=0; j<128; j++) {
				for(int t=0; t<numTypes; j++) {
					for(int d=1; d<8; d++) {
						PhaseOne[i][j][t][d] = helper.getEntryAijtd(i,j,t,d);
					}
				}
			}
		}
		return PhaseOne;
	}
    
    
}
