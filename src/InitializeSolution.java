



import java.util.List;
import java.util.Random;

public class InitializeSolution {
	private Helper helper;

    public InitializeSolution(SchedulingPeriod schedulingPeriod) {
        helper = new Helper(schedulingPeriod, null);
    }
    
    public String[][] createSolutionWorkRest() {
    	List<Employee> employeeList = helper.getEmployeeList();
        int numEmployees = employeeList.size();
        int numDays = helper.getDaysInPeriod();
        
    	String[][] solutionMatrix = new String[numEmployees][numDays];
    		
    	for(int d=0; d <numDays; d++) {
    		int dailyDemand = 0;
    		List<RequirementsForDay> requirementsForDay = helper.getRequirementsForDay(d);
            int numShiftTypes = requirementsForDay.size();
            for(int t=0; t<numShiftTypes; t++) {
            	dailyDemand += requirementsForDay.get(t).getDemand();
            }

            int workingNurses = 0;
            Random r = new Random();
            while(workingNurses < dailyDemand) {
            	int nurseID = r.nextInt(numEmployees);
            	
            	if(solutionMatrix[nurseID][d] == null) {
            		solutionMatrix[nurseID][d] = "W";
            		workingNurses++;
            	}
//            	if(!solutionMatrix[nurseID][d].equals("W")) {
//            		solutionMatrix[nurseID][d] = "W";
//            		workingNurses++;
//            	}
            }
            
    	}
    	return solutionMatrix;
    }
}
