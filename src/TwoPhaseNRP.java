import Attributes.*;
import Helper.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class TwoPhaseNRP {
	private SchedulingPeriod schedulingPeriod;
	private Helper helper;
	
	public void hello() {
		helper = new Helper(schedulingPeriod, null);
		
        List<Employee> employeeList = helper.getEmployeeList();
        
	}
	
	public static void main(String argv[]) throws Exception {
		
		//Read the XML file
        String fileName = "long01";
        XMLParser xmlParser = new XMLParser(fileName);
        SchedulingPeriod schedulingPeriod = xmlParser.parseXML();
		
        
	}
	
	
	public static int[][][] createMatrixNoType(int numNurses) {
		
		int[][][] PhaseOne = new int [numNurses][128][7];
		
		for(int i=0; i<numNurses; i++) {
			for(int j=0; j<128; j++) {
				for(int d=1; d<8; d++) {
					//PhaseOne[i][j][d] = matrixEntry(i,j,0,d,true);
				}
			}
		}
		return PhaseOne;
	}
	
	public static int[][][][] createMatrixType(int numNurses, int numTypes) {	
		int[][][][] PhaseOne = new int [numNurses][128][numTypes][7];
		
		for(int i=0; i<numNurses; i++) {
			for(int j=0; j<128; j++) {
				for(int t=0; t<numTypes; j++) {
					for(int d=1; d<8; d++) {
						//PhaseOne[i][j][t][d] = matrixEntry(i,j,t,d,false);
					}
				}
			}
		}
		return PhaseOne;
	}
	
	private static Solution getInitialSolution(SchedulingPeriod schedulingPeriod) throws Exception {
        //Initialization
        InitialSolution initialSolution = new InitialSolution(schedulingPeriod);
        List<int[][]> initialRoster = initialSolution.createSolution();

        //Check the constraints for the initialization
        Constraint constraint = new Constraint(schedulingPeriod, initialRoster);
        return new Solution(initialRoster, constraint.calcRosterScore());
    }
	
}
