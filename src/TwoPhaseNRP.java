import Attributes.*;
import Helper.RequirementsForDay;
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.cplex.IloCplex;
import main.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import ilog.cplex.*;
import ilog.concert.*;

public class TwoPhaseNRP {
	private SchedulingPeriod schedulingPeriod;
	private Helper helper;
	
	public void hello() {
		helper = new Helper(schedulingPeriod, null);
	}
	
	public static void main(String argv[]) throws Exception {
		
		//Read the XML file
        String fileName = "sprint01";
        XMLParser xmlParser = new XMLParser(fileName);
        SchedulingPeriod schedulingPeriod = xmlParser.parseXML();
		
        /*int best = 99999;
        for(int i=0; i<1000000; i++) {
        	
        	Solution inital = getInitialSolution(schedulingPeriod);
            
            Constraint constraint = new Constraint(schedulingPeriod, inital.getRoster());
            
            if (best > constraint.calcRosterScorePhaseOne()) {
            	best = constraint.calcRosterScorePhaseOne();
            }  
        }
        System.out.println("Penalty: " + best); */
        
        Solution initial = getInitialSolution(schedulingPeriod);
        ConstraintChecker constraintChecker = new ConstraintChecker(schedulingPeriod, initial.getRoster());
        
        int numberViolations = 0;
        for(int i=0; i<initial.getRoster().length; i++) {
        	System.out.println("violations " + i + ": " + constraintChecker.calcViolations(i));
        	numberViolations += constraintChecker.calcViolations(i);
        }
        System.out.println("violations: " + numberViolations);
        
        String[][] roster = initial.getRoster();
        for(int i=0; i<roster.length; i++) {
        	for(int d=0; d<roster[0].length; d++) {
        		System.out.print(roster[i][d] + "	");
        	}
        	System.out.println();
        } 
        
        int[][] costs = createMatrixCosts(initial, 0, schedulingPeriod);
        for(int i=0; i<costs.length; i++) {
        	for(int j=0; j<costs[0].length; j++) {
        		System.out.print(costs[i][j] + "	");
        	}
        	System.out.println();
        } 
        
        
        int[][] rosterPhase1 = solve(roster.length, createMatrixCosts(initial, 0, schedulingPeriod), createMatrixNoType(roster.length, schedulingPeriod, roster), demand(schedulingPeriod, roster));
        for(int i=0; i<rosterPhase1.length; i++) {
        	for(int d=0; d<rosterPhase1[0].length; d++) {
        		System.out.print(rosterPhase1[i][d] + "	");
        	}
        	System.out.println();
        } 
	}
	
	
	public static int[][] solve(int numNurses, int[][] costs, int[][][] matrixNoType, int[] demand) {
		int[][] nurseAssignmentILP = new int[numNurses][128];

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
			cplex.addEq(expr2, demand[d]);
		}
		
		// solve ILP
		cplex.solve();
		
		// save optimal value
		int minimumCost = (int) cplex.getObjValue();
		System.out.println("Minimum cost " + minimumCost);


		for (int i = 0; i < numNurses; i++) {
			for(int j = 0; j < 128; j++) {
				nurseAssignmentILP[i][j] = (int) cplex.getValue(nurseAssignment[i][j]); 
			}
		}
		
		// close cplex object      
		cplex.close(); 	
		
		} catch (IloException exc) {
			exc.printStackTrace();
		}
		
		return nurseAssignmentILP;	 			

	}
	
	
	public static int[][] createMatrixCosts(Solution initial, int startDay, SchedulingPeriod schedulingPeriod){
		int numNurses = schedulingPeriod.getEmployees().size();
		int[][] costMatrix = new int[numNurses][128];
		String[][] roster = initial.getRoster();
		
		for(int i=0; i<numNurses; i++) {
			for(int j=0; j<128; j++) {
				String bitString = String.format("%7s", Integer.toBinaryString(j)).replace(' ', '0');
				for(int d=0; d<7; d++) {
					if(Character.getNumericValue(bitString.charAt(d)) == 0) {
						roster[i][startDay + d] = null;
					}
					else {
						roster[i][startDay + d] = "W";
					}
				}
		        ConstraintChecker constraintChecker = new ConstraintChecker(schedulingPeriod, roster);
		        try {
					costMatrix[i][j] = constraintChecker.calcViolations(i);
				} catch (Exception e) {
				}
			}
		}
		
		return costMatrix;
	}
	
	public static int[][][] createMatrixNoType(int numNurses, SchedulingPeriod schedulingPeriod, String[][] roster) {
		
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
	
	public static int[][][][] createMatrixType(int numNurses, int numTypes, SchedulingPeriod schedulingPeriod, String[][] roster) {	
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
	
	public static int[] demand(SchedulingPeriod schedulingPeriod, String[][] roster) {
		Helper helper = new Helper(schedulingPeriod, roster);
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
	
	private static Solution getInitialSolution(SchedulingPeriod schedulingPeriod) throws Exception {
        //Initialization
        InitializeSolution initialSolution = new InitializeSolution(schedulingPeriod);
        String[][] initialRoster = initialSolution.createSolutionWorkRest();
        
        return new Solution(initialRoster, 0);
    }
	
}
