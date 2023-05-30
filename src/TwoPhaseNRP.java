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
        String fileName = "sprint_late08";
        TwoPhaseNRP instance = new TwoPhaseNRP(fileName);	
        //initializing initial solution
        Solution initialSol = instance.initial;
        
        //fitness of initial solution
        ConstraintChecker constraintChecker = new ConstraintChecker(instance.schedulingPeriod, initialSol.getRoster());
        int numberViolations = 0;
        for(int i=0; i<initialSol.getRoster().length; i++) {
        	int violations = constraintChecker.calcViolationsPhase1(0);
        	//System.out.println("violations: " + violations);
        	numberViolations += violations;
        }
        
        System.out.println("violations: " + numberViolations);
        
        //gets roster of initial solution to work with
        String[][] roster = initialSol.getRoster();
        
        //Phase 1 weekly ILP optimization 
        for(int j=0; j<5; j++) {
		    for(int i=0; i<4; i++) {
		    	roster = instance.solveWorkRestAssignment(roster, i*7);
		    }
		    System.out.println("another cycle ");
		}

        //local search function call for Phase 1 LS goes here
    	for(int j=0; j<10; j++) {   
    		for(int i=0; i<28; i++) {
	        	roster = instance.singleCutLS(roster, i);
	        }
    	}
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
				if((cplex.getValue(nurseAssignment[i][j]) < 1 + 0.00001) && (cplex.getValue(nurseAssignment[i][j]) > 1 - 0.00001)) {
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
		int[][] nurseRecombinationCosts = createMatrixNurseRecombinationCosts(roster, k);
		String[][] newRoster = new String[numNurses][numDays];
		
		try {
			// define a new cplex object
			IloCplex cplex = new IloCplex();
			cplex.setOut(null);			
			
			// declare variable matrix: numNurses times numNurses many integer variables with lower bound 0 and upper bound 1		
			IloNumVar[][] nurseCombination= new IloNumVar[numNurses][];
			for(int i=0; i<numNurses; i++) {
				nurseCombination[i] = cplex.numVarArray(numNurses, 0 , 1 ,IloNumVarType.Int);				
			}

			// add objective function that minimizes total costs
			IloLinearNumExpr obj = cplex.linearNumExpr(); 
			for (int i = 0; i < numNurses; i++) {
				for(int j = 0; j < numNurses; j++) {
					obj.addTerm(nurseCombination[i][j],nurseRecombinationCosts[i][j]);
				}
			}
			cplex.addMinimize(obj);
			
			
			// constraints that ensure that the partial rosters of nurses are used exactly ones
			for(int i=0; i < numNurses; i++){
				IloLinearNumExpr expr1 = cplex.linearNumExpr();
				for (int j = 0; j < numNurses; j++){
					expr1.addTerm(nurseCombination[i][j], 1); 
				}
				cplex.addEq(expr1, 1);
			}
			
			for(int j=0; j < numNurses; j++){
				IloLinearNumExpr expr2 = cplex.linearNumExpr();
				for (int i = 0; i < numNurses; i++){
					expr2.addTerm(nurseCombination[i][j], 1); 
				}
				cplex.addEq(expr2, 1);
			}
			
			// solve ILP
			cplex.solve();
			
			// save optimal value
			int minimumCost = (int) cplex.getObjValue();
			System.out.println("New cost " + minimumCost);

			for(int i=0; i<numNurses; i++) {
				for(int j=0; j<numNurses; j++) {	
					if((cplex.getValue(nurseCombination[i][j]) < 1 + 0.00001) && (cplex.getValue(nurseCombination[i][j]) > 1 - 0.00001)) {
		             	
						for(int l=0; l< k+1; l++) {
							newRoster[i][l] = roster[i][l];
						}
						for(int l=k+1; l< numDays; l++) {
							newRoster[i][l] = roster[j][l];
						}
					}
				}
			}
			
			// close cplex object      
			cplex.close(); 	
			
			} catch (IloException exc) {
				exc.printStackTrace();
			}
		return newRoster;
	}
	
	
	public int[][] createMatrixNurseRecombinationCosts(String[][] roster, int k){
		String[][] extraRoster = new String[numNurses][numDays];
		int[][] costMatrix = new int[numNurses][numNurses];
		
		ConstraintChecker constraintChecker = new ConstraintChecker(schedulingPeriod, roster);
		
		for(int i=0; i<numNurses; i++) {
			for(int j=i; j<numNurses; j++) {
				
				for(int l=0; l< k+1; l++) {
					extraRoster[i][l] = roster[i][l];
					extraRoster[j][l] = roster[j][l];
				}
				for(int l=k+1; l< numDays; l++) {
					extraRoster[i][l] = roster[j][l];
					extraRoster[j][l] = roster[i][l];
				}
				constraintChecker = new ConstraintChecker(schedulingPeriod, extraRoster);
				
				try {
					costMatrix[i][j] = constraintChecker.calcViolationsPhase1(i);
					costMatrix[j][i] = constraintChecker.calcViolationsPhase1(j);
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
