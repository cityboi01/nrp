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
		String fileName = "toy1";
		TwoPhaseNRP instance = new TwoPhaseNRP(fileName);	
		//initializing initial solution
		instance.currentSolution = instance.getInitialSolution();

		for(int i=0; i<instance.numNurses; i++) {
        	for(int d=0; d<instance.numDays; d++) {
        		System.out.print(instance.currentSolution.getRoster()[i][d] + "	");
        	}
        	System.out.println();
		}
		
		//local search function call for Phase 1 LS goes here
		for(int j=0; j<0; j++) {   
			for(int i=0; i<instance.numDays; i++) {
				instance.singleCutLS(i);
			}
			System.out.println("Vios after Cut1 cycle " + (j+1) + ": " + instance.currentSolution.getScore());
		}
//		int count = 0;
//		while(count < 0) {
//			count++;
//			instance.groupSwapPhase1();
//			if(count % 1000 == 0) {
//				System.out.println("Vios after " + (count) + " GroupSwaps: " + instance.currentSolution.getScore());
//			}
//		}
		
		instance.groupILS1(0, 0, 0);
		
		
		//Phase 1 weekly ILP optimization 
		for(int j=0; j<1; j++) {
			for(int i=0; i<instance.numDays/7; i++) {
				instance.solveWorkRestAssignment(i*7);
			}
			System.out.println("Vios after ILP1 cycle " + (j+1) + ": " + instance.currentSolution.getScore());
		}
		System.out.println("Cost after Phase1: " + instance.currentSolution.getScore());
		
				
		//test randomShiftAssign 
		System.out.println("Work assign completed.");
		
		instance.randomShiftAssign();
		
		instance.groupILS2(1000, 5, 0);
		String[][] rosterPhase1 = instance.currentSolution.getRoster();
		
		int cost = 0;
		ConstraintChecker checker = new ConstraintChecker(instance.schedulingPeriod, rosterPhase1);
		for(int i=0; i<rosterPhase1.length; i++) {
			for(int d=0; d<rosterPhase1[0].length; d++) {
				System.out.print(rosterPhase1[i][d] + "	");
			}
			System.out.println();
		}
		

		Solution copySol = instance.currentSolution.clone();
		String[][] roster = copySol.getRoster();
		ArrayList<ArrayList<Integer>> costs = instance.createMatrixCostsPhase2(roster, 0);
		ArrayList<ArrayList<ArrayList<String>>> comb = instance.shiftTypeCombinations(0);

		for(int n=0; n<instance.numNurses; n++) {
			System.out.println("nurse " + n);
			for(int i=0; i<costs.get(n).size(); i++) {
				for(int d=0; d<3; d++) {
					System.out.print(comb.get(n).get(i).get(d) + " ");
				}
				System.out.println(": " + costs.get(n).get(i) + " ");
			}
			System.out.println();
		}
		
		int m = 0;
		while(m<10) {
			instance.randomShiftAssign();
			int currentDay = 0;	
			while(currentDay < instance.numDays - 1) {
				instance.solveShiftAssignment(currentDay);
				currentDay += 3;
			}
			System.out.println(m);
			m++;
		}
		
		//test randomShiftAssign 
		//instance.randomShiftAssign();
		System.out.println("Shift assign completed.");
		String[][] rosterPhase2 = instance.currentSolution.getRoster();
		
		cost = 0;
		checker = new ConstraintChecker(instance.schedulingPeriod, rosterPhase1);
		for(int i=0; i<rosterPhase2.length; i++) {
			for(int d=0; d<rosterPhase2[0].length; d++) {
				System.out.print(rosterPhase2[i][d] + "	");
			}
			System.out.println();
		}
		
		
		for(int i=0; i<rosterPhase1.length; i++) {
			System.out.println("Violations phase 2 nurse " + i + ":	" + checker.calcViolationsPhase2(i));
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
			System.out.println("Minimum cost " + minimumCost);


			this.currentSolution.setScore(0);
			for(int i=0; i<numNurses; i++) {
				for(int j=0; j<shiftTypeCombinations.get(i).size(); j++) {	
					//System.out.print(cplex.getValue(shiftAssignment[i][j]) + " ");
					if((cplex.getValue(shiftAssignment[i][j]) < 1 + 0.000011) && (cplex.getValue(shiftAssignment[i][j]) > 1 - 0.000011)) {
						this.currentSolution.setNurseScores(i, costs.get(i).get(j));
						this.currentSolution.setScore(this.currentSolution.getScore() + costs.get(i).get(j));
						ArrayList<String> shiftAssign = shiftTypeCombinations.get(i).get(j);
						for(int d=0; d<3; d++) {
							roster[i][startDay + d] = shiftAssign.get(d);
						}
					}
				}
				//System.out.println();
			}

			// close cplex object      
			cplex.close(); 	

		} catch (IloException exc) {
			exc.printStackTrace();
		}
		this.currentSolution.setRoster(roster);	
		
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

					if (roster[index][column] != null) {
						roster[index][column] = currentShiftType;
						currentDemand--;
					}
				}
			}
		}

		return roster;

	}

	
	public void groupILS1(int maxInnerCount, int maxOuterCount, int destroyDays) {
		int outerCount = 0;
		Solution tempSolution = this.currentSolution.clone();
		while(outerCount < maxOuterCount) {
			int innerCount = 0;
			outerCount ++;
			while(innerCount < maxInnerCount) {
				innerCount++;
				this.groupSwapPhase1();
//				if(innerCount % 1000 == 0) {
//					System.out.println("Vios after " + (innerCount) + " GroupSwaps: " + this.currentSolution.getScore());
//				}
			}
			if(tempSolution.getScore() < currentSolution.getScore()) {
				this.currentSolution = tempSolution;
			}
//			System.out.println("Vios (temp) after ILS iteration: " + tempSolution.getScore());
			System.out.println("Vios (current) after ILS iteration: " + this.currentSolution.getScore());
			tempSolution = this.currentSolution.clone();
			this.perturb1(destroyDays);
//			System.out.println("Vios after perturbation: " + this.currentSolution.getScore());
		}
		if(tempSolution.getScore() < currentSolution.getScore()) {
			this.currentSolution = tempSolution;
		}
		
	}
	
	public void groupILS2(int maxInnerCount, int maxOuterCount, int destroyDays) {
		int outerCount = 0;
		Solution tempSolution = this.currentSolution.clone();
		while(outerCount < maxOuterCount) {
			int innerCount = 0;
			outerCount ++;
			while(innerCount < maxInnerCount) {
				innerCount++;
				this.groupSwapPhase2();
//				if(innerCount % 1000 == 0) {
//					System.out.println("Vios after " + (innerCount) + " GroupSwaps: " + this.currentSolution.getScore());
//				}
			}
			if(tempSolution.getScore() < currentSolution.getScore()) {
				this.currentSolution = tempSolution;
			}
//			System.out.println("Vios (temp) after ILS iteration: " + tempSolution.getScore());
			System.out.println("Vios (current) after ILS iteration: " + this.currentSolution.getScore());
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
		this.currentSolution.setRoster(roster);
		
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
		this.currentSolution.setRoster(roster);
		
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
			System.out.println("Exception in groupSwapPhase1, using new checker.calcVios raised.");
		}

		//compute delta for the two nurses
		int delta = (vioNurse1After + vioNurse2After) - (vioNurse1Before + vioNurse2Before);

		//if new solution is equal or better (lower violations), return new roster
		if (delta <= 0) {
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
		this.currentSolution.setRoster(roster);

	}
	
	public void groupSwapPhase2(){
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
			return;
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
		this.currentSolution.setRoster(roster);

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
					System.out.println("Exception in createMatrixNurseRecombinationCosts, using new checker.calcVios raised.");
				}

			}

		}	
		return costMatrix;
	}

	public ArrayList<ArrayList<Integer>> createMatrixCostsPhase2(String[][] initialRoster, int startDay){
		ArrayList<ArrayList<Integer>> costs = new ArrayList<ArrayList<Integer>>();
		ArrayList<ArrayList<ArrayList<String>>> combinations = shiftTypeCombinations(startDay);
		//String[][] newRoster = new String[this.numNurses][this.numDays];
		
		for(int i=0; i<numNurses; i++) {
			ArrayList<Integer> list = new ArrayList<Integer>();
			for(int j=0; j<combinations.get(i).size(); j++) { 
				for(int d=0; d<3; d++) {
					initialRoster[i][startDay + d] = combinations.get(i).get(j).get(d);
				}
				ConstraintChecker constraintChecker = new ConstraintChecker(schedulingPeriod, initialRoster);
				try {
					list.add(constraintChecker.calcViolationsPhase2(i));
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
