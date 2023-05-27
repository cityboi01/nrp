import Attributes.*;
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.cplex.IloCplex;
import main.*;

import java.io.FileNotFoundException;
import java.text.ParseException;
import java.util.List;
import ilog.cplex.*;

public class TwoPhaseNRP {
	SchedulingPeriod schedulingPeriod;
    Helper helper;
	int numNurses;
	int numDays;
	int[] dailyDemand;
	int numTypes;
	Solution initial;
	
	public static void main(String argv[]) throws Exception {
		
		//Read the XML file
        String fileName = "sprint01";
        TwoPhaseNRP instance = new TwoPhaseNRP(fileName);	

        Solution initialSol = instance.initial;
        
        ConstraintChecker constraintChecker = new ConstraintChecker(instance.schedulingPeriod, initialSol.getRoster());
        int numberViolations = 0;
        for(int i=0; i<initialSol.getRoster().length; i++) {
        	numberViolations += constraintChecker.calcViolationsPhase1(i);
        }
        
        System.out.println("violations: " + numberViolations);
        
        String[][] roster = initialSol.getRoster();
        
        for(int j=0; j<1; j++) {
		    for(int i=0; i<4; i++) {
		    	roster = instance.solveWorkRestAssignment(roster, i*7);
		    }
		}
        
        
        //int[][] costsRecom = instance.createMatrixNurseRecombinationCosts(roster, 5);
        
        for(int k=0; k<1; k++) { 
            int[][] costsRecom = instance.createMatrixNurseRecombinationCosts(roster, k);
            System.out.println("NumDays " + k);
        	for(int i=0; i<instance.numNurses; i++) {
	        	for(int j=0; j<instance.numNurses; j++) {
	        		System.out.print(costsRecom[i][j] + "	");
	        		
	            }
	        	System.out.println();
	        }
        	System.out.println();
        }
        
        
        /*
        for(int i=0; i<rosterPhase1.length; i++) {
        	for(int d=0; d<rosterPhase1[0].length; d++) {
        		System.out.print(rosterPhase1[i][d] + "	");
        	}
        	System.out.println();
        }*/ 
	}
	
	
	public String[][] solveWorkRestAssignment(String[][] roster, int startDay) {
		
		int[][] costs = createMatrixCosts(roster, startDay);
		int[][][] matrixNoType = createMatrixNoType(roster);
		
		try {
		// define a new cplex object
		IloCplex cplex = new IloCplex();
		cplex.setOut(null);			
		
		// declare variable matrix: numNurses times 128 many integer variables with lower bound 0 and upper bound 1		
		IloNumVar[][] nurseAssignment= new IloNumVar[numNurses][];
		for(int i=0; i<numNurses; i++) {
			nurseAssignment[i] = cplex.numVarArray(128, 0 , 1 ,IloNumVarType.Int);				
		}

		// add objective function that minimizes total costs
		IloLinearNumExpr obj = cplex.linearNumExpr(); 
		for (int i = 0; i < numNurses; i++) {
			for(int j = 0; j < 128; j++) {
				obj.addTerm(nurseAssignment[i][j],costs[i][j]);
			}
		}
		cplex.addMinimize(obj);
		
		// add constraints
		
		// constraint that ensures at least one work rest pattern is assigned per nurse
		for(int i=0; i < numNurses; i++){
			IloLinearNumExpr expr1 = cplex.linearNumExpr();
			for (int j = 0; j < 128; j++){
				expr1.addTerm(nurseAssignment[i][j], 1); 
			}
			cplex.addEq(expr1, 1);
		}
		
		// constraint that ensures the demand is met each day
		for(int d = 0; d < 7; d++) {
			IloLinearNumExpr expr2 = cplex.linearNumExpr();
			for(int j=0; j < 128; j++){
				for (int i = 0; i < numNurses; i++){
					expr2.addTerm(nurseAssignment[i][j], matrixNoType[i][j][d]); 
				}
			}
			cplex.addEq(expr2, dailyDemand[d]);
		}
		
		// solve ILP
		cplex.solve();
		
		// save optimal value
		int minimumCost = (int) cplex.getObjValue();
		System.out.println("Minimum cost " + minimumCost);


		for(int i=0; i<numNurses; i++) {
			for(int j=0; j<128; j++) {	
				if(cplex.getValue(nurseAssignment[i][j]) == 1) {
					String bitString = String.format("%7s", Integer.toBinaryString(j)).replace(' ', '0');
					for(int d=0; d<7; d++) {
						if(Character.getNumericValue(bitString.charAt(d)) == 0) {
							roster[i][startDay + d] = null;
						}
						else {
							roster[i][startDay + d] = "W";
						}
					}
				}
			}
		}
		
		// close cplex object      
		cplex.close(); 	
		
		} catch (IloException exc) {
			exc.printStackTrace();
		}
		return roster;	 			
	}
	
	public String[][] singleCutLS(String[][] roster, int k){
		 
		
		return roster;
	}
	
	
	public int[][] createMatrixNurseRecombinationCosts(String[][] roster, int k){
		String[][] extraRoster = new String[numNurses][numDays];
		int[][] costMatrix = new int[numNurses][numNurses];
		
		ConstraintChecker constraintChecker = new ConstraintChecker(schedulingPeriod, roster);

		for(int i=0; i<numNurses; i++) {
			int costNurse;
			try {
				costNurse = constraintChecker.calcViolationsPhase1(i);
				costMatrix[i][i] = costNurse;
			} catch (Exception e) {
			}
		}
		
		System.out.println("Nurse 0:");
		for(int l=0; l< numDays; l++) {
			System.out.print(roster[0][l] + "	");
		}
		System.out.println();
		
		System.out.println("Nurse 1:");
		for(int l=0; l< numDays; l++) {
			System.out.print(roster[1][l] + "	");
		}
		System.out.println();
		
		for(int i=0; i<2; i++) {
			for(int j=i+1; j<2; j++) {
				
				for(int l=0; l< k; l++) {
					extraRoster[0][l] = roster[i][l];
					extraRoster[1][l] = roster[j][l];
				}
				for(int l=k; l< numDays; l++) {
					extraRoster[0][l] = roster[j][l];
					extraRoster[1][l] = roster[i][l];
				}
				
				System.out.println("Nurse 0:");
				for(int l=0; l< numDays; l++) {
					System.out.print(roster[0][l] + "	");
				}
				System.out.println();
				
				System.out.println("Nurse 1:");
				for(int l=0; l< numDays; l++) {
					System.out.print(roster[1][l] + "	");
				}
				System.out.println();
				
			}
		}
		
		
		for(int i=0; i<numNurses; i++) {
			for(int j=i+1; j<numNurses; j++) {
				
				for(int l=0; l< k; l++) {
					extraRoster[0][l] = roster[i][l];
					extraRoster[1][l] = roster[j][l];
				}
				for(int l=k; l< numDays; l++) {
					extraRoster[0][l] = roster[j][l];
					extraRoster[1][l] = roster[i][l];
				}
				
				constraintChecker = new ConstraintChecker(schedulingPeriod, extraRoster);
				
				try {
					costMatrix[i][j] = constraintChecker.calcViolationsPhase1(0);
					costMatrix[j][i] = constraintChecker.calcViolationsPhase1(1);;
				} catch (Exception e) {
				}
			}
		}
		
		
		return costMatrix;
	}
	
	public int[][] createMatrixCosts(String[][] initialRoster, int startDay){
		int numNurses = schedulingPeriod.getEmployees().size();
		int[][] costMatrix = new int[numNurses][128];
		
		for(int i=0; i<numNurses; i++) {
			for(int j=0; j<128; j++) {
				String bitString = String.format("%7s", Integer.toBinaryString(j)).replace(' ', '0');
				for(int d=0; d<7; d++) {
					if(Character.getNumericValue(bitString.charAt(d)) == 0) {
						initialRoster[i][startDay + d] = null;
					}
					else {
						initialRoster[i][startDay + d] = "W";
					}
				}
		        ConstraintChecker constraintChecker = new ConstraintChecker(schedulingPeriod, initialRoster);
		        try {
					costMatrix[i][j] = constraintChecker.calcViolationsPhase1(i);
				} catch (Exception e) {
				}
			}
		}
		
		return costMatrix;
	}
	
	public int[][][] createMatrixNoType(String[][] roster) {
		
		int[][][] PhaseOne = new int [numNurses][128][7];
        Helper helper = new Helper(schedulingPeriod, roster);

		for(int i=0; i<numNurses; i++) {
			for(int j=0; j<128; j++) {
				for(int d=0; d<7; d++) {
					PhaseOne[i][j][d] = helper.getEntryAijd(i, j, d);
				}
			}
		}
		return PhaseOne;
	}
	
	public int[][][][] createMatrixType(String[][] roster) {	
		int[][][][] PhaseOne = new int [numNurses][128][numTypes][7];
		Helper helper = new Helper(schedulingPeriod, roster);
		
		for(int i=0; i<numNurses; i++) {
			for(int j=0; j<128; j++) {
				for(int t=0; t<numTypes; j++) {
					for(int d=1; d<8; d++) {
						PhaseOne[i][j][t][d] = helper.getEntryAijtd(i, j, t, d);								
					}
				}
			}
		}
		return PhaseOne;
	}
	
	public int[] demand() {
        int numDays = helper.getDaysInPeriod();
        int[] dailyDemand = new int[numDays];
        
		for(int d=0; d <numDays; d++) {
    		List<main.RequirementsForDay> requirementsForDay = helper.getRequirementsForDay(d);
            int numShiftTypes = requirementsForDay.size();
            for(int t=0; t<numShiftTypes; t++) {
            	dailyDemand[d] += requirementsForDay.get(t).getDemand();
            }
		}
		return dailyDemand;
	}
	
	public Solution getInitialSolution() throws Exception {
        //Initialization
        InitializeSolution initialSolution = new InitializeSolution(schedulingPeriod);
        String[][] initialRoster = initialSolution.createSolutionWorkRest();
        
        return new Solution(initialRoster, 0);
    }
	
	//method to read the input file
 	public TwoPhaseNRP(String filename) throws Exception{
 		XMLParser xmlParser = new XMLParser(filename);
 		this.schedulingPeriod = xmlParser.parseXML();	
 		this.numNurses = schedulingPeriod.getEmployees().size();
 		this.numTypes = schedulingPeriod.getSkills().size();
 		this.helper = new Helper(this.schedulingPeriod, new String[numNurses][numDays]);
 		this.numDays = helper.getDaysInPeriod();
 		
 		this.dailyDemand = demand();
 		this.initial = getInitialSolution();
 		
 	}

}
