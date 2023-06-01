import Attributes.*;
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.cplex.IloCplex;
import main.*;

import java.io.FileNotFoundException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import ilog.cplex.*;

public class TwoPhaseNRP {
	SchedulingPeriod schedulingPeriod;
	Helper helper;
	int numNurses;
	int numDays;
	int[] dailyDemand;
	int numSkillTypes;
	Random random = new Random();
	Solution currentSolution;

	public static void main(String argv[]) throws Exception {

		//Read the XML file

		String fileName = "sprint01";
		TwoPhaseNRP instance = new TwoPhaseNRP(fileName);	
		//initializing initial solution
		instance.currentSolution = instance.getInitialSolution();



		//Phase 1 weekly ILP optimization 
		for(int j=0; j<5; j++) {
			for(int i=0; i<4; i++) {
				instance.solveWorkRestAssignment(i*7);
			}
			System.out.println("Vios after ILP1 cycle " + (j+1) + ": " + instance.currentSolution.getScore());
		}

		//local search function call for Phase 1 LS goes here
		for(int j=0; j<5; j++) {   
			for(int i=0; i<28; i++) {
				instance.singleCutLS(i);
			}
			System.out.println("Vios after Cut1 cycle " + (j+1) + ": " + instance.currentSolution.getScore());
		}
		int count = 0;
		while(count < 50000) {
			count++;
			instance.groupSwapPhase1();
			if(count % 1000 == 0) {
				System.out.println("Vios after " + (count) + " GroupSwaps: " + instance.currentSolution.getScore());
			}
		}
		System.out.println("Cost after Phase1: " + instance.currentSolution.getScore());

		//test randomShiftAssign
		instance.randomShiftAssign();
		System.out.println("Shift assign completed.");
		String[][] rosterPhase1 = instance.currentSolution.getRoster();
		int cost = 0;
		ConstraintChecker checker = new ConstraintChecker(instance.schedulingPeriod, rosterPhase1);
		for(int i=0; i<rosterPhase1.length; i++) {
			for(int d=0; d<rosterPhase1[0].length; d++) {
				System.out.print(rosterPhase1[i][d] + "	");
			}
			cost += checker.calcViolationsPhase2(i);
			System.out.println();
		}
		System.out.println(cost);
		


	}

	public String[][] randomShiftAssign(){
		String[][] roster = this.currentSolution.getRoster();
		int numColumns = roster[0].length;
		int numShiftTypes = this.schedulingPeriod.getShiftTypes().size();
		String[] shiftTypes = this.helper.getShiftWithIndices().toArray(new String[numShiftTypes]);

		for (int column = 0; column < numColumns; column++) {
			ArrayList<Integer> indices = new ArrayList<>(roster.length);
			for (int i = 0; i < roster.length; i++) {
				indices.add(i);
			}

			int[] demands = new int[numShiftTypes];
			for (int i = 0; i < numShiftTypes; i++) {
				demands[i] = this.helper.getRequirement(shiftTypes[i], column);
			}

			for (int i = 0; i < numShiftTypes; i++) {
				String currentShiftType = shiftTypes[i];
				int currentDemand = demands[i];

				while (currentDemand > 0) {
					int randomIndex = this.random.nextInt(indices.size());
					int index = indices.remove(randomIndex);

					if (roster[index][column] == "W") {
						roster[index][column] = currentShiftType;
						currentDemand--;
					}
				}
			}
		}

		return roster;

	}

	public void groupSwapPhase1(){
		String[][] roster = this.currentSolution.getRoster();
		// Select two random and non-equal rows
		int nurse1 = this.random.nextInt(this.numNurses);
		int nurse2 = this.random.nextInt(this.numNurses);
		while(nurse1 == nurse2) {
			nurse2 = this.random.nextInt(this.numNurses);
		}

		//compute fitness before swap
		int vioNurse1Before = this.currentSolution.getNurseScores()[nurse1];
		int vioNurse2Before = this.currentSolution.getNurseScores()[nurse2];



		// Select a random consecutive range (group) of columns/days; swap ranges includes both start and end (can be equal)
		int startColIndex = this.random.nextInt(this.numDays);
		int endColIndex = this.random.nextInt(this.numDays - startColIndex) + startColIndex;

		//		//copy the subrosters of the two chosen nurses in case changes need to be undone (no improvement)
		//		String[][] subrosterCopies = new String[2][this.numDays];
		//		subrosterCopies[0] = Arrays.copyOf(roster[nurse1], this.numDays);
		//		subrosterCopies[1] = Arrays.copyOf(roster[nurse2], this.numDays);

		// Swap entries between the two rows within the selected range of columns in the copies
		for (int day = startColIndex; day <= endColIndex; day++) {
			String temp = roster[nurse1][day];
			roster[nurse1][day] = roster[nurse2][day];
			roster[nurse2][day] = temp;
		}

		//compute fitness after swap
		int vioNurse1After = 0;
		int vioNurse2After = 0;
		ConstraintChecker constraintCheckerAfter = new ConstraintChecker(this.schedulingPeriod, roster);
		try {
			vioNurse1After = constraintCheckerAfter.calcViolationsPhase1(nurse1);
			vioNurse2After = constraintCheckerAfter.calcViolationsPhase1(nurse2);
		}
		catch(Exception e) {

		}

		//compute delta for the two nurses
		int delta = (vioNurse1After + vioNurse2After) - (vioNurse1Before + vioNurse2Before);

		//if new solution is equal or better (lower violations), return new roster
		if (delta <= 0) {
			this.currentSolution.setRoster(roster);
			this.currentSolution.setScore(this.currentSolution.getScore() + delta);
			this.currentSolution.setNurseScores(nurse1, vioNurse1After);
			this.currentSolution.setNurseScores(nurse2, vioNurse2After);
			return;
		}
		//if not, undo changes and return old roster
		else {
			// Swap entries between the two rows within the selected range of columns in the copies
			for (int day = startColIndex; day <= endColIndex; day++) {
				String temp = roster[nurse1][day];
				roster[nurse1][day] = roster[nurse2][day];
				roster[nurse2][day] = temp;
			}
		}

	}


	public void solveWorkRestAssignment(int startDay) {
		String[][] roster = this.currentSolution.getRoster();
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


			this.currentSolution.setScore(0);
			for(int i=0; i<numNurses; i++) {
				for(int j=0; j<128; j++) {	
					if((cplex.getValue(nurseAssignment[i][j]) < 1 + 0.000011) && (cplex.getValue(nurseAssignment[i][j]) > 1 - 0.000011)) {
						this.currentSolution.setNurseScores(i, costs[i][j]);
						this.currentSolution.setScore(this.currentSolution.getScore() + costs[i][j]);
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
		this.currentSolution.setRoster(roster);	 			
	}

	public void singleCutLS(int k){
		String[][] roster = this.currentSolution.getRoster();
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

			this.currentSolution.setScore(0);
			for(int i=0; i<numNurses; i++) {
				for(int j=0; j<numNurses; j++) {	
					if((cplex.getValue(nurseCombination[i][j]) < 1 + 0.00001) && (cplex.getValue(nurseCombination[i][j]) > 1 - 0.00001)) {
						this.currentSolution.setNurseScores(i, nurseRecombinationCosts[i][j]);
						this.currentSolution.setScore(this.currentSolution.getScore() + nurseRecombinationCosts[i][j]);
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
		this.currentSolution.setRoster(newRoster);
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
		int[][][][] PhaseOne = new int [numNurses][128][numSkillTypes][7];
		Helper helper = new Helper(schedulingPeriod, roster);

		for(int i=0; i<numNurses; i++) {
			for(int j=0; j<128; j++) {
				for(int t=0; t<numSkillTypes; j++) {
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
		InitializeSolution initialSolution = new InitializeSolution(this.schedulingPeriod);
		String[][] initialRoster = initialSolution.createSolutionWorkRest();

		//fitness of initial solution
		ConstraintChecker constraintChecker = new ConstraintChecker(this.schedulingPeriod, initialRoster);
		int numberViolations = 0;
		int[] nurseViolations = new int[this.numNurses];
		for(int i=0; i< this.numNurses; i++) {
			nurseViolations[i] = constraintChecker.calcViolationsPhase1(i);
			numberViolations += nurseViolations[i];
		}

		System.out.println("violations: " + numberViolations);

		return new Solution(initialRoster, numberViolations, nurseViolations);
	}

	//method to read the input file
	public TwoPhaseNRP(String filename) throws Exception{
		XMLParser xmlParser = new XMLParser(filename);
		this.schedulingPeriod = xmlParser.parseXML();	
		this.numNurses = schedulingPeriod.getEmployees().size();
		this.numSkillTypes = schedulingPeriod.getSkills().size();
		this.helper = new Helper(this.schedulingPeriod, new String[numNurses][numDays]);
		this.numDays = helper.getDaysInPeriod();

		this.dailyDemand = demand();

	}

}
