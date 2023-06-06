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
	Solution solutionPhase1;
	Random random = new Random();
	Solution currentSolution;
	ArrayList<ArrayList<Integer>> previousRoster;
	
	public static void main(String argv[]) throws Exception {

		//Read the XML file
		String fileName = "sprint01";
		TwoPhaseNRP instance = new TwoPhaseNRP(fileName);	

		//parameters for ILP and single cut local search phase 1
		int ILPiterPhase1 = 1;
		int maxIterationsLSPhase1 = 0;
		
		//parameters for ILS phase 1
		int maxStagPhase1 = 0;
		int maxOuterCountPhase1 = 0;
		int destroyDaysPhase1 = 0;			//0 corresponds to cycle shift and anything between 1 and 28 gives a new random solution for that number of days
		
		//parameters for ILP phase 2
		int ILPiterPhase2 = 0;
		
		//parameters for ILS phase 2
		int maxStagPhase2 = 500;
		int maxOuterCountPhase2 = 3;
		int destroyDaysPhase2 = 0;
		
		int score = instance.optimizeNurseRoster(ILPiterPhase1, maxIterationsLSPhase1, ILPiterPhase2,
				maxStagPhase1, maxOuterCountPhase1, destroyDaysPhase1, 
				maxStagPhase2, maxOuterCountPhase2, destroyDaysPhase2);
		
		System.out.println(score);
	}
	
	public int optimizeNurseRoster(int ILPiterPhase1, int maxIterationsLSPhase1, int ILPiterPhase2, int maxStagPhase1, int maxOuterCountPhase1, int destroyDaysPhase1, int maxStagPhase2, int maxOuterCountPhase2, int destroyDaysPhase2) throws Exception {
		long startTimeOne = System.currentTimeMillis();
		
		optimizePhase1(ILPiterPhase1, maxIterationsLSPhase1, maxStagPhase1, maxOuterCountPhase1, destroyDaysPhase1);
		this.solutionPhase1 = this.currentSolution.clone();
		System.out.println("time for phase 1: " + (System.currentTimeMillis() - startTimeOne));
		System.out.println();
		
		long startTimeTwo = System.currentTimeMillis();
		optimizePhase2(ILPiterPhase2, maxStagPhase2, maxOuterCountPhase2, destroyDaysPhase2);
		System.out.println("time for phase 2: " + (System.currentTimeMillis() - startTimeTwo));

		int score = this.solutionPhase1.getScore() + this.currentSolution.getScore();
		return score;
	}
	
	public void optimizePhase1(int ILPiter, int maxIterationsLS, int maxStag, int maxOuterCount, int destroyDays) throws Exception {
		ILPphase1(ILPiter, maxIterationsLS);
		groupILS1(maxStag, maxOuterCount, destroyDays);
		
	}
	
	public void optimizePhase2(int ILPiter, int maxStag, int maxOuterCount, int destroyDays) {
		randomShiftAssign();
		ILPphase2(ILPiter);
		groupILS2(maxStag, maxOuterCount, destroyDays);
		
	}
	
	public void ILPphase1(int maxIterationsILP, int maxIterationsLS) throws Exception {
		//initializing initial solution
		this.currentSolution = getInitialSolution();
		
		//Phase 1 weekly ILP optimization 
		for(int j=0; j<maxIterationsILP; j++) {
			for(int i=0; i<this.numDays/7; i++) {
				this.solveWorkRestAssignment(i*7);
			}
			System.out.println("Cost ILP Phase1 iteration " + j + ": " + this.currentSolution.getScore());
		}
		//System.out.println("Cost after Phase1: " + this.currentSolution.getScore());
		
		//local search function call for Phase 1 LS goes here
		for(int j=0; j<maxIterationsLS; j++) {   
			for(int i=0; i<this.numDays; i++) {
				this.singleCutLS(i);
			}
			System.out.println("Cost Single Cut LS Phase1 iteration " + j + ": " + this.currentSolution.getScore());
		}
	}

	public void ILPphase2(int maxIterations) {
		int m = 0;
		while(m<maxIterations) {
			this.randomShiftAssign();
			int currentDay = 0;	
			while(currentDay < this.numDays - 1) {
				this.solveShiftAssignment(currentDay);
				currentDay += 3;
			}
			m++;
			System.out.println("Cost ILP Phase2 iteration " + m + ": " + this.currentSolution.getScore());
		}
		
	}
	

	
	public void solveShiftAssignment(int startDay) {
		//TODO: change back!
		String[][] roster = this.currentSolution.getRoster();
		Solution copySol = this.currentSolution.clone();
		String[][] copyRoster = copySol.getRoster();
		ArrayList<ArrayList<ArrayList<String>>> shiftTypeCombinations = shiftTypeCombinations(startDay);
		ArrayList<ArrayList<ArrayList<ArrayList<Integer>>>> createMatrixShiftType = createMatrixShiftType(copyRoster, shiftTypeCombinations);
		ArrayList<ArrayList<Integer>> costs = createMatrixCostsPhase2(copyRoster, startDay);
		
		List<Shift> shifts = this.helper.getShiftList();
		
//		System.out.println();
//		System.out.println("Previous Roster at start day " + ": " + startDay);
//		for(int i=0; i<this.numNurses; i++) {
//			for(int d=0; d<this.numDays; d++) {
//				System.out.print(this.currentSolution.getRoster()[i][d] + "	");
//			}
//			System.out.println();
//		}
		
		try {
			// define a new cplex object
			IloCplex cplex = new IloCplex();
			cplex.setOut(null);			

			// declare variable matrix: numNurses times 128 many integer variables with lower bound 0 and upper bound 1		
			IloNumVar[][] shiftAssignment= new IloNumVar[numNurses][];
			for(int i=0; i<numNurses; i++) {
				shiftAssignment[i] = cplex.numVarArray(shiftTypeCombinations.get(i).size(), 0 , 1 ,IloNumVarType.Int);				
			}

			// add objective function that minimizes total costs
			IloLinearNumExpr obj = cplex.linearNumExpr(); 
			for (int i = 0; i < numNurses; i++) {
				for(int j = 0; j < shiftTypeCombinations.get(i).size(); j++) {
					obj.addTerm(shiftAssignment[i][j],costs.get(i).get(j));
				}
			}
			cplex.addMinimize(obj);

			// add constraints

			// constraint that ensures exactly one shift combination is assigned per nurse
			for(int i=0; i < numNurses; i++){
				IloLinearNumExpr expr1 = cplex.linearNumExpr();
				for (int j = 0; j < shiftTypeCombinations.get(i).size(); j++){
					expr1.addTerm(shiftAssignment[i][j], 1); 
				}
				cplex.addEq(expr1, 1);
			}                                                                                              

			// constraint that ensures the demand is met each day
			for(int d = 0; d < 3; d++) {
				for(int t = 0; t<shifts.size(); t++) {
					IloLinearNumExpr expr2 = cplex.linearNumExpr();
					for (int i = 0; i < numNurses; i++){
						for(int j=0; j < shiftTypeCombinations.get(i).size(); j++){
							expr2.addTerm(shiftAssignment[i][j], createMatrixShiftType.get(i).get(j).get(t).get(d)); 
						}
					}
					cplex.addEq(expr2, this.helper.getRequirement(shifts.get(t).getId(), d+startDay));
				}	
			}

			// solve ILP
			cplex.solve();

			// save optimal value
			int minimumCost = (int) cplex.getObjValue();
			//System.out.println("Minimum cost " + minimumCost);

//			int costPreviousSol = 0;
//			if(startDay > 2) {	
//				for(int i=0; i<numNurses; i++) {
//					for(int j=0; j<shiftTypeCombinations.get(i).size(); j++) {	
//						costPreviousSol += this.previousRoster.get(i).get(j) * costs.get(i).get(j);
//					}
//				}
//			}
//			
//			System.out.println();
//			System.out.println("Minimum cost previous solution with new costs" + costPreviousSol);
			
//			int[] costPerNursePrevious = new int[this.numNurses];
//			ConstraintChecker checker = new ConstraintChecker(this.schedulingPeriod, roster);

//			for(int i=0; i<numNurses; i++) {
//				String prevComb = "";
//				for(int d=0; d<3; d++) {
//					prevComb += roster[i][startDay + d];
//				}
//				for(int j=0; j<shiftTypeCombinations.get(i).size(); j++) {	
//					//boolean thisCombination = true;
//					
//					String a = "";
//					for(int d=0; d<3; d++) {
//						a += shiftTypeCombinations.get(i).get(j).get(d);
//					}
//					
//					if(prevComb.equals(a)) {
//						costPerNursePrevious[i] = costs.get(i).get(j);
//						String prevCombShift = "";
//						for(int d=0; d<3; d++) {
//							prevCombShift += shiftTypeCombinations.get(i).get(j).get(d);
//						}
//						System.out.println("Nurse " + i + " shift comb previous sol: " + prevCombShift);
//					}
//				}
//			}
			
//			for(int i=0; i<numNurses; i++) {
//				System.out.println("Cost previous sol given these costs nurse " + i + ": " + costPerNursePrevious[i]);
//				try {
//					System.out.println("Value constraintchecker " + checker.calcViolationsPhase2(i));
//				} catch (Exception e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
			
//			int nurse = -1;
//			int pat = -1;
//			String pattern = null;
//			
			this.currentSolution.setScore(0);
			for(int i=0; i<numNurses; i++) {
//				pattern = "";
	
				for(int j=0; j<shiftTypeCombinations.get(i).size(); j++) {	
//					for(int d=0; d<3; d++) {
//						System.out.print(shiftTypeCombinations.get(i).get(j).get(d));
//					}
//					System.out.print("	");
//
//					
					//System.out.print(cplex.getValue(shiftAssignment[i][j]) + " ");
					if((cplex.getValue(shiftAssignment[i][j]) < 1 + 0.000011) && (cplex.getValue(shiftAssignment[i][j]) > 1 - 0.000011)) {
						//String pattern = null;
//						for(int d=0; d<3; d++) {
//							pattern += (shiftTypeCombinations.get(i).get(j).get(d));
//						}
//						nurse = i;
//						pat = j;
						
						this.currentSolution.setNurseScores(i, costs.get(i).get(j));
						this.currentSolution.setScore(this.currentSolution.getScore() + costs.get(i).get(j));
						ArrayList<String> shiftAssign = shiftTypeCombinations.get(i).get(j);
						for(int d=0; d<3; d++) {
							roster[i][startDay + d] = shiftAssign.get(d);
						}
					}
				}
				//System.out.println();
				
				//System.out.println("Nurse " + nurse + " cost pattern  " + pattern + ": " + costs.get(nurse).get(pat));
				//System.out.println();
			}
			
//			for(int i=0; i<this.numNurses; i++) {
//				for(int d=0; d<this.numDays; d++) {
//					System.out.print(this.currentSolution.getRoster()[i][d] + "	");
//				}
//				System.out.println();
//			}
//			
			//this.previousRoster = new ArrayList<ArrayList<Integer>>();

//			for(int i=0; i<numNurses; i++) {
//				ArrayList<Integer> solnurse = new ArrayList<Integer>();
//				for(int j=0; j<shiftTypeCombinations.get(i).size(); j++) {	
//					//solnurse.add((int) cplex.getValue(shiftAssignment[i][j]));
//					System.out.print(cplex.getValue(shiftAssignment[i][j]) + "	");
//				}
//				//this.previousRoster.add(solnurse);
//				System.out.println();
//			}
			
//			checker = new ConstraintChecker(this.schedulingPeriod, roster);
//			
//			for(int i=0; i<numNurses; i++) {
//				try {
//					System.out.println("ILP Nurse " + i + " violations: " + checker.calcViolationsPhase2(i));
//				} catch (Exception e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//			
//			
//			System.out.println();
			
			// close cplex object      
			cplex.close(); 	

		} catch (IloException exc) {
			exc.printStackTrace();
		}
		this.currentSolution.setRoster(roster);	
		
	}
	
	public void randomShiftAssign(){
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

					if (roster[index][column] != null) {
						roster[index][column] = currentShiftType;
						currentDemand--;
					}
				}
			}
		}
		
		ConstraintChecker checker = new ConstraintChecker(this.schedulingPeriod, roster);
		int cost = 0;
		for (int i = 0; i < this.numNurses; i ++) {
			try {
				int nurseCost = checker.calcViolationsPhase2(i);
				cost += nurseCost;
				this.currentSolution.setNurseScores(i, nurseCost);
			}
			catch(Exception e) {
				System.out.println("Exception in perturb2, using new checker.calcVios raised.");
			}
		}
		this.currentSolution.setScore(cost);
		this.currentSolution.setRoster(roster);

	}

	
	public void groupILS1(int maxStag, int maxOuterCount, int destroyDays) {
		int outerCount = 0;
		Solution tempSolution = this.currentSolution.clone();
		while(outerCount < maxOuterCount) {
			int stagCount = 0;
			outerCount ++;
			while(stagCount < maxStag) {
				if(!this.groupSwapPhase1()) {
					stagCount++;
				}
//				if(innerCount % 1000 == 0) {
//					System.out.println("Vios after " + (innerCount) + " GroupSwaps: " + this.currentSolution.getScore());
//				}
			}
			if(tempSolution.getScore() < currentSolution.getScore()) {
				this.currentSolution = tempSolution;
			}
//			System.out.println("Vios (temp) after ILS iteration: " + tempSolution.getScore());
			System.out.println("Cost after ILS1 iteration "+ outerCount + ": " + this.currentSolution.getScore());
			tempSolution = this.currentSolution.clone();
			this.perturb1(destroyDays);
//			System.out.println("Vios after perturbation: " + this.currentSolution.getScore());
		}
		if(tempSolution.getScore() < currentSolution.getScore()) {
			this.currentSolution = tempSolution;
		}
		
	}
	
	public void groupILS2(int maxStag, int maxOuterCount, int destroyDays) {
		int outerCount = 0;
		Solution tempSolution = this.currentSolution.clone();
		while(outerCount < maxOuterCount) {
			int stagCount = 0;
			outerCount ++;
			while(stagCount < maxStag) {
				if(!this.groupSwapPhase2()) {
					stagCount++;
				}
//				if(innerCount % 1000 == 0) {
//					System.out.println("Vios after " + (innerCount) + " GroupSwaps: " + this.currentSolution.getScore());
//				}
			}
			if(tempSolution.getScore() < currentSolution.getScore()) {
				this.currentSolution = tempSolution;
			}
//			System.out.println("Vios (temp) after ILS iteration: " + tempSolution.getScore());
			System.out.println("Cost after ILS2 iteration "+ outerCount + ": " + this.currentSolution.getScore());
			tempSolution = this.currentSolution.clone();
			this.perturb2(destroyDays);
//			System.out.println("Vios after perturbation: " + this.currentSolution.getScore());
		}
		if(tempSolution.getScore() < currentSolution.getScore()) {
			this.currentSolution = tempSolution;
		}
		
	}
	
	public void perturb1(int destroyDays) {
		if(destroyDays == this.numDays) {
			try {
				this.currentSolution = this.getInitialSolution();
			}
			catch(Exception e) {
				System.out.println("Exception in perturb, getting new initial sol raised.");
			}
			
		}
		else if(destroyDays == 0) {
			this.cycleShiftPhase1();
		}
		else {
			Solution randSol = null;
			try {
				randSol = this.getInitialSolution();
			}
			catch(Exception e) {
				System.out.println("Exception in perturb, getting new initial sol raised.");
			}
			ArrayList<Integer> indices = new ArrayList<Integer>();
			for(int i = 0; i < this.numDays; i++) {
				indices.add(i);
			}
			String[][] currentRoster = this.currentSolution.getRoster();
			String[][] randRoster = randSol.getRoster();
			while(destroyDays > 0) {
				int randDay = indices.remove(this.random.nextInt(indices.size()));
				
				for(int i = 0; i < currentRoster.length; i++) {
					currentRoster[i][randDay] = randRoster[i][randDay];
				}
				destroyDays --;
			}
			ConstraintChecker checker = new ConstraintChecker(this.schedulingPeriod, currentRoster);
			int cost = 0;
			for (int i = 0; i < this.numNurses; i ++) {
				try {
					cost += checker.calcViolationsPhase1(i);
				}
				catch(Exception e) {
					System.out.println("Exception in perturb, using new checker.calcVios raised.");
				}
			}
			this.currentSolution.setRoster(currentRoster);
		}
	}
	
	public void perturb2(int destroyDays) {
		if(destroyDays == this.numDays) {
			this.randomShiftAssign();			
		}
		else if(destroyDays == 0) {
			this.cycleShiftPhase2();
		}
		else {
			Solution oldSol = this.currentSolution.clone();
			this.randomShiftAssign();
			
			ArrayList<Integer> indices = new ArrayList<Integer>();
			for(int i = 0; i < this.numDays; i++) {
				indices.add(i);
			}
			String[][] randRoster = this.currentSolution.getRoster();
			String[][] oldRoster = oldSol.getRoster();
			
			int resetDays = this.numDays - destroyDays;
			while(resetDays > 0) {
				int randDay = indices.remove(this.random.nextInt(indices.size()));
				
				for(int i = 0; i < randRoster.length; i++) {
					randRoster[i][randDay] = oldRoster[i][randDay];
				}
				resetDays --;
			}
			ConstraintChecker checker = new ConstraintChecker(this.schedulingPeriod, randRoster);
			int cost = 0;
			for (int i = 0; i < this.numNurses; i ++) {
				try {
					cost += checker.calcViolationsPhase2(i);
				}
				catch(Exception e) {
					System.out.println("Exception in perturb2, using new checker.calcVios raised.");
				}
			}
			this.currentSolution.setRoster(randRoster);
		}
	}

	public void cycleShiftPhase1() {
		String[][] roster = this.currentSolution.getRoster();
		Random random = this.random;
		int column = random.nextInt(roster[0].length); // Select random column
		int startRow = random.nextInt(roster.length - 1); // Select random start row

		// Find a different end row from the start row s.t. endRow > startRow
		int endRow = random.nextInt(roster.length - (startRow +1)) + (startRow + 1);
//		System.out.println("Null occurs " + countNullOccurrences(roster, column) + " times in affected column before cycleShift.");

		// Perform cycle swap within the selected range of rows of the chosen column
		String temp = roster[endRow][column];
		int increment = (startRow < endRow) ? -1 : 1;
		for (int i = endRow; i != startRow; i += increment) {
			roster[i][column] = roster[i + increment][column];
		}
		roster[startRow][column] = temp;
		
		ConstraintChecker checker = new ConstraintChecker(this.schedulingPeriod, roster);
		int delta = 0;
		for (int i = endRow; i != startRow + increment; i += increment) {
			try {
				int viosAfter = checker.calcViolationsPhase1(i);
				delta += (viosAfter - this.currentSolution.getNurseScores()[i]);
				this.currentSolution.setNurseScores(i, viosAfter);
			}
			catch(Exception e) {
				System.out.println("Exception in cycleShiftPhase1, using new checker.calcVios raised.");
			}
		}
		this.currentSolution.setScore(this.currentSolution.getScore() + delta);
		
//		System.out.println("Null occurs " + countNullOccurrences(roster, column) + " times in affected column after cycleShift.");
		
	}
	
	public void cycleShiftPhase2() {
		String[][] roster = this.currentSolution.getRoster();
		Random random = this.random;
		int column = random.nextInt(roster[0].length); // Select random column
		int startRow = random.nextInt(roster.length - 1); // Select random start row

		// Find a different end row from the start row s.t. endRow > startRow
		int endRow = random.nextInt(roster.length - (startRow +1)) + (startRow + 1);
//		System.out.println("Null occurs " + countNullOccurrences(roster, column) + " times in affected column before cycleShift.");
		
		int increment = (startRow < endRow) ? -1 : 1;
		
		ArrayList<Integer> nonNullIndices = new ArrayList<Integer>();
		
		for (int i = endRow; i != startRow + increment; i += increment) {
			if(roster[i][column] != null) {
				nonNullIndices.add(i);
			}
		}
		// Perform cycle swap within the selected range of rows of the chosen column
		if(!nonNullIndices.isEmpty()) {
			String temp = roster[nonNullIndices.get(0)][column];
			for (int i = 0; i != nonNullIndices.size() - 1; i ++) {
				roster[nonNullIndices.get(i)][column] = roster[nonNullIndices.get(i + 1)][column];
			}
			roster[nonNullIndices.get(nonNullIndices.size() - 1)][column] = temp;
			
			ConstraintChecker checker = new ConstraintChecker(this.schedulingPeriod, roster);
			int delta = 0;
			for (int i = 0; i != nonNullIndices.size(); i ++) {
				try {
					int viosAfter = checker.calcViolationsPhase2(nonNullIndices.get(i));
					delta += (viosAfter - this.currentSolution.getNurseScores()[i]);
					this.currentSolution.setNurseScores(i, viosAfter);
				}
				catch(Exception e) {
					System.out.println("Exception in cycleShiftPhase2, using new checker.calcVios raised.");
				}
			}
			this.currentSolution.setScore(this.currentSolution.getScore() + delta);
			
		}
		else {
			this.cycleShiftPhase2();
		}
		
//		System.out.println("Null occurs " + countNullOccurrences(roster, column) + " times in affected column after cycleShift.");
		
	}
	
	public static int countNullOccurrences(String[][] roster, int columnIndex) {
        int count = 0;
        for (String[] row : roster) {
            if (columnIndex >= 0 && columnIndex < row.length && row[columnIndex] == null) {
                count++;
            }
        }
        return count;
    }
	
	
	public boolean groupSwapPhase1(){
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
			System.out.println("Exception in groupSwapPhase1, using new checker.calcVios raised.");
		}

		//compute delta for the two nurses
		int delta = (vioNurse1After + vioNurse2After) - (vioNurse1Before + vioNurse2Before);

		//if new solution is equal or better (lower violations), return new roster
		if (delta <= 0) {
			this.currentSolution.setScore(this.currentSolution.getScore() + delta);
			this.currentSolution.setNurseScores(nurse1, vioNurse1After);
			this.currentSolution.setNurseScores(nurse2, vioNurse2After);
			
			if(delta < 0) {
				return true;
			}
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
		return false;
	}
	
	public boolean groupSwapPhase2(){
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
			if(roster[nurse1][day] != null && roster[nurse2][day] != null) {
				String temp = roster[nurse1][day];
				roster[nurse1][day] = roster[nurse2][day];
				roster[nurse2][day] = temp;
			}
			
		}

		//compute fitness after swap
		int vioNurse1After = 0;
		int vioNurse2After = 0;
		ConstraintChecker constraintCheckerAfter = new ConstraintChecker(this.schedulingPeriod, roster);
		try {
			vioNurse1After = constraintCheckerAfter.calcViolationsPhase2(nurse1);
			vioNurse2After = constraintCheckerAfter.calcViolationsPhase2(nurse2);
		}
		catch(Exception e) {
			System.out.println("Exception in groupSwapPhase2, using new checker.calcVios raised.");
		}

		//compute delta for the two nurses
		int delta = (vioNurse1After + vioNurse2After) - (vioNurse1Before + vioNurse2Before);

		//if new solution is equal or better (lower violations), return new roster
		if (delta <= 0) {
			this.currentSolution.setScore(this.currentSolution.getScore() + delta);
			this.currentSolution.setNurseScores(nurse1, vioNurse1After);
			this.currentSolution.setNurseScores(nurse2, vioNurse2After);
			
			if(delta < 0) {
				return true;
			}
			
		}
		//if not, undo changes and return old roster
		else {
			// Swap entries between the two rows within the selected range of columns in the copies
			for (int day = startColIndex; day <= endColIndex; day++) {
				if(roster[nurse1][day] != null && roster[nurse2][day] != null) {
					String temp = roster[nurse1][day];
					roster[nurse1][day] = roster[nurse2][day];
					roster[nurse2][day] = temp;
				}
			}
		}
		
		return false;

	}
	
	public void solveWorkRestAssignment(int startDay) {
		String[][] roster = this.currentSolution.getRoster();
		int[][] costs = createMatrixCostsPhase1(roster, startDay);
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
			//System.out.println("Minimum cost " + minimumCost);


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
					System.out.println("Exception in createMatrixNurseRecombinationCosts, using new checker.calcVios raised.");
				}

			}

		}	
		return costMatrix;
	}

	public ArrayList<ArrayList<Integer>> createMatrixCostsPhase2(String[][] initialRoster, int startDay){
		ArrayList<ArrayList<Integer>> costs = new ArrayList<ArrayList<Integer>>();
		ArrayList<ArrayList<ArrayList<String>>> combinations = shiftTypeCombinations(startDay);
		String[][] roster = this.currentSolution.getRoster();
		
		for(int i=0; i<numNurses; i++) {
			ArrayList<Integer> list = new ArrayList<Integer>();
			for(int j=0; j<combinations.get(i).size(); j++) {
				String[][] newRoster = new String[this.numNurses][this.numDays];
				for(int d=0; d<startDay; d++) {
					newRoster[i][d] = roster[i][d];
				}
				
				for(int d=startDay; d<(startDay+3); d++) {
					newRoster[i][d] = combinations.get(i).get(j).get(d-startDay);

				}
				
				for(int d=(startDay+3); d<this.numDays; d++) {
					newRoster[i][d] = roster[i][d];
				}
				
				ConstraintChecker constraintChecker = new ConstraintChecker(schedulingPeriod, newRoster);
				try {
					list.add(constraintChecker.calcViolationsPhase2(i));
					
//					for(int d=0; d<numDays; d++) {
//						if(newRoster[i][d] == null) {
//							System.out.print("-");
//						}
//						else {
//							System.out.print(newRoster[i][d]);
//						}
//					}
//					System.out.print("	" + constraintChecker.calcViolationsPhase2(i));
//					System.out.println();
					
				} catch (Exception e) {
					System.out.println("Exception in createMatrixCostsPhase2, using new checker.calcVios raised.");
				}
			}
			costs.add(list);
		}

		return costs;
	}
	
	public int[][] createMatrixCostsPhase1(String[][] initialRoster, int startDay){
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
					System.out.println("Exception in createMatrixCostsPhase1, using new checker.calcVios raised.");
				}
			}
		}

		return costMatrix;
	}

	public ArrayList<ArrayList<ArrayList<ArrayList<Integer>>>> createMatrixShiftType(String[][] roster, ArrayList<ArrayList<ArrayList<String>>> shiftTypeCombinations) {
		ArrayList<ArrayList<ArrayList<ArrayList<Integer>>>> PhaseTwo = new ArrayList<ArrayList<ArrayList<ArrayList<Integer>>>>();
		Helper helper = new Helper(schedulingPeriod, roster);
		List<Shift> shifts = helper.getShiftList();
		
		for(int i=0; i<numNurses; i++) {
			PhaseTwo.add(new ArrayList<ArrayList<ArrayList<Integer>>>());
			for(int j=0; j<shiftTypeCombinations.get(i).size(); j++) {
				PhaseTwo.get(i).add(new ArrayList<ArrayList<Integer>>());
				for(int t=0; t<shifts.size(); t++) {
					PhaseTwo.get(i).get(j).add(new ArrayList<Integer>());
					String shiftID = shifts.get(t).getId();
					for(int d=0; d<3; d++) {
						PhaseTwo.get(i).get(j).get(t).add(helper.getEntryAijtdShift(i, j, shiftID, d, shiftTypeCombinations));
					}
				}
			}
		}
		return PhaseTwo;
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
	
	public ArrayList<ArrayList<ArrayList<String>>> shiftTypeCombinations (int startDay){
		List<Shift> shifts = this.helper.getShiftList();
		String[] shiftIDs = new String[shifts.size()];
		int maxdays = 3;
		
		int index = 0;
		for(Shift s: shifts) {
			String shiftID = s.getId();
			shiftIDs[index] = shiftID;
			index++;
		}
		
		Permutations perm = new Permutations();
		ArrayList<List<ArrayList<String>>> listCombinations = new ArrayList<List<ArrayList<String>>>();
		
		for(int i = 0; i <= maxdays; i++) {
			List<ArrayList<String>> combinations = perm.generatePermutations(shiftIDs, i);
			listCombinations.add(combinations);
		}
		return this.distributeCombinations(startDay, listCombinations);
	}
	
	public ArrayList<ArrayList<ArrayList<String>>> distributeCombinations (int startDay, ArrayList<List<ArrayList<String>>> listCombinations){
		
		int maxdays = 3;
		
		
		ArrayList<ArrayList<ArrayList<String>>> shiftTypeCombinations = new ArrayList<ArrayList<ArrayList<String>>>();
		for(int i=0; i< this.numNurses; i++) {	
			int numWorkingDays = 0;
			for(int d=startDay; d < startDay + maxdays; d++) {
				if(this.currentSolution.getRoster()[i][d] != null){
					numWorkingDays++;
				}
			}
			
			List<ArrayList<String>> combinations = listCombinations.get(numWorkingDays);
			ArrayList<ArrayList<String>> allCombinations = new ArrayList<ArrayList<String>>();
			for(int j=0; j<combinations.size(); j++) {
				ArrayList<String> newList = new ArrayList<String>();
				allCombinations.add(newList);
				int index = 0;
				for(int d=startDay; d < startDay + maxdays; d++) {
					if(this.currentSolution.getRoster()[i][d] != null) {
						String s = combinations.get(j).get(index);
						allCombinations.get(j).add(s);
						index++;
					}
					else {
						allCombinations.get(j).add(null);
					}
				}
			}
			shiftTypeCombinations.add(allCombinations);
		}
		return shiftTypeCombinations;
	}

	public int[] demand() {
		int numDays = this.helper.getDaysInPeriod();
		int[] dailyDemand = new int[numDays];

		for(int d=0; d <numDays; d++) {
			List<main.RequirementsForDay> requirementsForDay = this.helper.getRequirementsForDay(d);
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
			//System.out.println("violations nurse " + i + ": " + numberViolations);
			numberViolations += nurseViolations[i];
		}

		//System.out.println("violations: " + numberViolations);

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
