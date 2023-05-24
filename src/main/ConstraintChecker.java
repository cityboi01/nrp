package main;

import Attributes.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConstraintChecker {
    private SchedulingPeriod schedulingPeriod;
    private Helper helper;
    private String[][] roster;

    public ConstraintChecker(SchedulingPeriod schedulingPeriod, String[][] roster) {
        this.schedulingPeriod = schedulingPeriod;
        this.roster = roster;
        helper = new Helper(this.schedulingPeriod, this.roster);
    }

    public boolean checkHardConst() {
        List<Employee> employeeList = helper.getEmployeeList();

        //Checks if the shift demand is met each day
        for (int d = 0; d < roster[0].length; d++) {
        	List<RequirementsForDay> requirementsForDay = helper.getRequirementsForDay(d);
            int numShiftType = requirementsForDay.size();
            
            for(int t=0; t<numShiftType; t++) {
            	String shiftID = requirementsForDay.get(t).getShiftID();
            	
            	int supplyOnShift = 0;
            	for(int i=0; i< employeeList.size(); i++) {
            		if(shiftID.equals("DH")) {
            			if(roster[d][i].equals("DH")) {
            				if(!employeeList.get(i).getSkills().contains(Skill.HEAD_NURSE)) {
                				return false;
                			}
            				else {
            					supplyOnShift++;
            				}
	            		}
            		}
            		else {
	            		if(roster[d][i].equals(shiftID)) {
	            			supplyOnShift++;
	            		}
            		}
                }
            	if(supplyOnShift < requirementsForDay.get(d).getDemand()) {
            		return false;
            	}	
            }
        }
        return true;
    }
    
    public int calcViolationsPhase1(int employeeID) throws Exception {
        int punishmentPoints = 0;
        punishmentPoints += checkMaxConsecutiveWorkingDays(employeeID);
        punishmentPoints += checkMinConsecutiveWorkingDays(employeeID);
        punishmentPoints += checkMaxConsecutiveFreeDays(employeeID);
        punishmentPoints += checkMinConsecutiveFreeDays(employeeID);
        punishmentPoints += checkWeekendConstraints(employeeID);
        punishmentPoints += checkCompleteWeekends(employeeID);
        punishmentPoints += checkNumbAssigment(employeeID);
        punishmentPoints += checkDayOffRequest(employeeID);
        punishmentPoints += checkUnwantedPatternDay(employeeID);
        
        //punishmentPoints = checkMinConsecutiveWorkingDays(employeeID);
 
        return punishmentPoints;
    }
    
    public int calcViolationsPhase2(int employeeID) throws Exception {
        int punishmentPoints = 0;
        punishmentPoints += checkShiftOffRequest(employeeID);
        punishmentPoints += checkIdenticalShiftTypesDuringWeekend(employeeID);
        punishmentPoints += checkNoNightShiftBeforeFreeWeekend(employeeID);
        punishmentPoints += checkUnwantedPatternShift(employeeID);
        
        return punishmentPoints;
    }
    
    private int checkMaxConsecutiveWorkingDays(int employeeID) {
    	List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        Employee e = (Employee) schedulingPeriod.getEmployees().get(employeeID);
        Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());

        int punishmentPoints = 0;
        int maxConsecutiveWorkingDays = c.getMaxConsecutiveWorkingDays();
        for (int i = 0; i < workOnDayPeriode.size(); i++) {
            if (workOnDayPeriode.get(i).get(employeeID) == 1) {
                if (c.getMaxConsecutiveWorkingDays_on() == 1) {
                    int counter = 0;
                    //check if the current day + max consecutive day is still in the period
                    if (workOnDayPeriode.size() > i + maxConsecutiveWorkingDays) {
                        //count the days from the current day to the max consecutive days + 1 
                        for (int k = 0; k < maxConsecutiveWorkingDays + 1; k++) {
                            counter += workOnDayPeriode.get(i + k).get(employeeID);
                        }
                        if (counter == maxConsecutiveWorkingDays + 1) {
                            punishmentPoints += c.getMaxConsecutiveWorkingDays_weight();
                        }
                    }
                }
            }
        }
        return punishmentPoints;
    }
    
    
    private int checkMinConsecutiveWorkingDays(int employeeID) {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        Employee e = (Employee) schedulingPeriod.getEmployees().get(employeeID);
        Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());
        int punishmentPoints = 0;

        int minConsecutiveWorkingDays = c.getMinConsecutiveWorkingDays();
        //System.out.println("Min consecutive days:" + minConsecutiveWorkingDays);
        if (c.getMinConsecutiveWorkingDays_on() == 1) {
            for (int i = 0; i < workOnDayPeriode.size(); i++) {
                int counter = 0;
                //Exception: first day off the period
                if (i == 0 && workOnDayPeriode.get(i).get(employeeID) == 1) {
                	//Count the number of working days from i to minConWorkingDays
                    for (int k = 0; k < minConsecutiveWorkingDays; k++) {
                        if (workOnDayPeriode.get(k).get(employeeID) == 1) {
                            counter++;
                        } else {
                            break;
                        }
                    }
                    if (counter < minConsecutiveWorkingDays) {
                        punishmentPoints += c.getMinConsecutiveWorkingDays_weight();
                    }
                }
                //in case of the start of a line of consecutive days
                else if (i + 1 < workOnDayPeriode.size() && workOnDayPeriode.get(i).get(employeeID) == 0 &&
                        workOnDayPeriode.get(i + 1).get(employeeID) == 1) {

                    //check if the current day + min consecutive day is still in the period
                    if (workOnDayPeriode.size() > i + c.getMinConsecutiveWorkingDays()) {
                    	//Count the number of working days from tomorrow to minConWorkingDays
                        for (int k = i + 1; k < i + 1 + minConsecutiveWorkingDays; k++) {
                        	//Check if he is actually working consecutive days
                        	if (workOnDayPeriode.get(k).get(employeeID) == 1) {
                                counter++;
                            } else {
                                break;
                            }
                        }
                    }
                    //if you are not in the period anymore, count the number of days until the end of the period
                    else {
                    	//Count the number of working days from i to minConWorkingDays
                        for (int k = i + 1; k < workOnDayPeriode.size(); k++) {
                        	//Check if he is actually working consecutive days
                        	if (workOnDayPeriode.get(k).get(employeeID) == 1) {
                                counter++;
                            } else {
                                break;
                            }
                        }
                    }
                    // Punishment points for each day not working consecutively
                    if (counter < minConsecutiveWorkingDays) {
                        counter = minConsecutiveWorkingDays - counter;
                        punishmentPoints += c.getMinConsecutiveWorkingDays_weight() * counter;
                    }
                }
            }
        }
        return punishmentPoints;
    }
    
    private int checkMaxConsecutiveFreeDays(int employeeID) {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        Employee e = (Employee) schedulingPeriod.getEmployees().get(employeeID);
        Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());
        int punishmentPoints = 0;
        
        int maxConsecutiveFreeDays = c.getMaxConsecutiveFreeDays();
        //System.out.println("Free days: " + maxConsecutiveFreeDays);
        if (c.getMaxConsecutiveFreeDays_on() == 1) {
            for (int i = 0; i < workOnDayPeriode.size(); i++) {
                if (workOnDayPeriode.get(i).get(employeeID) == 0) {
                    int counter = 0;
                    //check if the current day plus max consecutive days in still in the period
                    if (workOnDayPeriode.size() > i + c.getMaxConsecutiveFreeDays()) {
                        //count all days from current day to max cons days + 1 
                        for (int k = 0; k < maxConsecutiveFreeDays + 1; k++) {
                            counter += workOnDayPeriode.get(i + k).get(employeeID);
                        }
                        if (counter == 0) {
                            punishmentPoints += c.getMaxConsecutiveFreeDays_weight();
                        }
                    }
                }
            }
        }
        return punishmentPoints;
    }
    
    private int checkMinConsecutiveFreeDays(int employeeID) {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        Employee e = (Employee) schedulingPeriod.getEmployees().get(employeeID);
        Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());
        int punishmentPoints = 0;
        
        int minConsecutiveFreeDays = c.getMinConsecutiveFreeDays();
        //System.out.println("Min consecutive free days:" + minConsecutiveFreeDays);

        if (c.getMinConsecutiveFreeDays_on() == 1) {
            for (int i = 0; i < workOnDayPeriode.size(); i++) {
                int counter = 0;
                //Exception: first day off the period
                if (i == 0 && workOnDayPeriode.get(i).get(employeeID) == 0) {
                	//Count the number of free days from i to minConWorkingDays
                    for (int k = 0; k < minConsecutiveFreeDays; k++) {
                        if (workOnDayPeriode.get(k).get(employeeID) == 0) {
                            counter += 1;
                        } else {
                            break;
                        }
                    }
                    if (counter < minConsecutiveFreeDays) {
                        counter = minConsecutiveFreeDays - counter;
                        punishmentPoints += c.getMinConsecutiveFreeDays_weight() * counter;
                    }
                }
                //in case of the start of a line of consecutive days
                else if (i + 1 < workOnDayPeriode.size() && workOnDayPeriode.get(i).get(employeeID) == 1 &&
                        workOnDayPeriode.get(i + 1).get(employeeID) == 0) {

                    //check if the current day + min consecutive day is still in the period
                    if (workOnDayPeriode.size() > i + c.getMinConsecutiveFreeDays()) {
                    	//Count the number of working days from tomorrow to minConWorkingDays
                        for (int k = 0; k < minConsecutiveFreeDays; k++) {
                            if (workOnDayPeriode.get(i + k + 1).get(employeeID) == 0) {
                                counter += 1;
                            } else {
                                break;
                            }
                        }
                    }
                    //if you are not in the period anymore, count the number of days until the end of the period
                    else {
                        //count all days from current day to max cons days + 1 
                        for (int k = i + 1; k < workOnDayPeriode.size(); k++) {
                        	//Check if he is actually working consecutive days
                            if (workOnDayPeriode.get(k).get(employeeID) == 0) {
                                counter++;
                            } else {
                                break;
                            }
                        }
                    }
                    if (counter < minConsecutiveFreeDays) {
                        counter = minConsecutiveFreeDays - counter;
                        punishmentPoints += c.getMinConsecutiveFreeDays_weight() * counter;
                    }
                }
            }
        }
        
        return punishmentPoints;
    }
    
    private int checkWeekendConstraints(int employeeID) {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        Employee e = (Employee) schedulingPeriod.getEmployees().get(employeeID);
        Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());
      
        int punishmentPoints_maxCon = 0;
        int punishmentPoints_minCon = 0;
        int punishmentPoints_maxWorkingWe = 0;
     
        List<Day> weekendDefinition = c.getWeekendDefinition();
        int countConsecutiveWorkAtWeekend = 0;
        int countWorkAtWeekend = 0;
       
        boolean oldValue = false;
        for (int i = 0; i < workOnDayPeriode.size(); i++) {
            Day currentDay = helper.getWeekDayOfPeriode(i);
            if (weekendDefinition.contains(currentDay)) {
                int indexOfWeekendDefinition = weekendDefinition.indexOf(currentDay);
                boolean workAtWeekend = false;
                if (workOnDayPeriode.size() > i + weekendDefinition.size() - 1) {
                    for (int k = 0; k < weekendDefinition.size() - indexOfWeekendDefinition; k++) {
                        if (workOnDayPeriode.get(i + k).get(employeeID) == 1) {
                            workAtWeekend = true;
                            break;
                        }
                    }
                } else {
                    for (int k = 0; k < workOnDayPeriode.size() - i; k++) {
                        if (workOnDayPeriode.get(i + k).get(employeeID) == 1) {
                            workAtWeekend = true;
                            break;
                        }
                    }
                }
                if (oldValue && workAtWeekend) {
                    countConsecutiveWorkAtWeekend++;
                }
                if (workAtWeekend) {
                    countWorkAtWeekend++;
                    i += weekendDefinition.size() - indexOfWeekendDefinition - 1;
                }
                oldValue = workAtWeekend;
            }

        }
        int maxConsecutiveWorkingWeekends = c.getMaxConsecutiveWorkingWeekends();
        if (c.getMaxConsecutiveWorkingWeekends_on() == 1 &&
                countConsecutiveWorkAtWeekend > maxConsecutiveWorkingWeekends) {
            punishmentPoints_maxCon += (countConsecutiveWorkAtWeekend - maxConsecutiveWorkingWeekends) *
                    c.getMaxConsecutiveWorkingWeekends_weight();
        }
        int minConsecutiveWorkingWeekends = c.getMinConsecutiveWorkingWeekends();
        if (c.getMinConsecutiveWorkingWeekends_on() == 1 &&
                countConsecutiveWorkAtWeekend < minConsecutiveWorkingWeekends) {
            punishmentPoints_minCon += (minConsecutiveWorkingWeekends - countConsecutiveWorkAtWeekend) *
                    c.getMaxConsecutiveWorkingWeekends_weight();
        }
        int maxWorkingWeekendsInFourWeeks = c.getMaxWorkingWeekendsInFourWeeks();
        if (c.getMaxWorkingWeekendsInFourWeeks_on() == 1 &&
                countWorkAtWeekend > maxWorkingWeekendsInFourWeeks) {
            punishmentPoints_maxWorkingWe += (countWorkAtWeekend - maxWorkingWeekendsInFourWeeks) *
                    c.getMaxWorkingWeekendsInFourWeeks_weight();
        }
        
        /*System.out.println("Max consecutive " + maxConsecutiveWorkingWeekends);
        System.out.println("Min consecutive " + minConsecutiveWorkingWeekends);
        System.out.println("Max working " + maxWorkingWeekendsInFourWeeks);
        System.out.println();
        System.out.println("Max consecutive punishment points" + punishmentPoints_maxCon);
        System.out.println("Min consecutive punishment points" + punishmentPoints_minCon);
        System.out.println("Max working weekend punishment points" + punishmentPoints_maxWorkingWe);*/
        
        return punishmentPoints_maxCon + punishmentPoints_minCon + punishmentPoints_maxWorkingWe;
    }

    
    private int checkCompleteWeekends(int employeeID) {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        Employee e = (Employee) schedulingPeriod.getEmployees().get(employeeID);
        Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());
        int punishmentPoints = 0;
        
        List<Day> weekendDefinition = c.getWeekendDefinition();
        if (c.isCompleteWeekends()) {
            for (int i = 0; i < workOnDayPeriode.size(); i++) {
                Day currentDay = helper.getWeekDayOfPeriode(i);
                if (weekendDefinition.contains(currentDay)) {
                    int indexOfWeekendDefinition = weekendDefinition.indexOf(currentDay);
                    int workAtWeekend = 0;
                    if (workOnDayPeriode.size() > i + weekendDefinition.size() - 1) {
                        for (int k = 0; k < weekendDefinition.size() - indexOfWeekendDefinition; k++) {
                            if (workOnDayPeriode.get(i + k).get(employeeID) == 1) {
                                workAtWeekend++;
                            }
                        }
                    } else {
                        for (int k = 0; k < workOnDayPeriode.size() - i; k++) {
                            if (workOnDayPeriode.get(i + k).get(employeeID) == 1) {
                                workAtWeekend++;
                            }
                        }
                        //check if the last day is a working day and it is a weekend
                        if (workOnDayPeriode.size() - i - 1 == 0 && workAtWeekend != 0) {
                            if (weekendDefinition.size() == 3) {
                                workAtWeekend += 2;
                            } else {
                                workAtWeekend += 1;
                            }
                        }
                        //check if the second last day is a working day and it is a weekend (only for (Fr,Sa,Su)
                        if (workOnDayPeriode.size() - i - 1 == 1 && workAtWeekend != 0) {
                            if (weekendDefinition.size() == 3) {
                                workAtWeekend += 1;
                            }
                        }
                    }
                    if (workAtWeekend != 0 && workAtWeekend !=
                            weekendDefinition.size() - indexOfWeekendDefinition) {
                        punishmentPoints++;
                    }
                    i += weekendDefinition.size();
                    if (i >= workOnDayPeriode.size()) {
                        break;
                    }
                }
            }
        }
        
        return punishmentPoints;
    }
    
    private int checkNumbAssigment(int employeeID) {
        //Calculate the number of working days for the nurse
        int daysOfWork = 0;
		for(int d=0; d<roster[0].length; d++) {
			if(roster[employeeID][d] != null) {
				daysOfWork++;
			}
		}
        
        //Calculate the penalties by taking the difference between daysOfWork and maxDaysOfWork/minDaysOfWork
        int diffDays = 0;
        Employee e = (Employee) schedulingPeriod.getEmployees().get(employeeID);
        Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());
        int maxDaysOfWork = c.getMaxNumAssignments();
        int minDaysOfWork = c.getMinNumAssignments();
        
        //System.out.println("Max days of work: " + maxDaysOfWork);
        //System.out.println("Min days of work: " + minDaysOfWork);
        
        if (c.getMaxNumAssignments_on() == 1) {
            if (maxDaysOfWork < daysOfWork) {
                diffDays += (daysOfWork - maxDaysOfWork) * c.getMaxNumAssignments_weight();
            }
        }
        if (c.getMinNumAssignments_on() == 1) {
            if (minDaysOfWork > daysOfWork) {
                diffDays += (minDaysOfWork - daysOfWork) * c.getMinNumAssignments_weight();
            }
        }
        return diffDays;
    }
    
    private int checkDayOffRequest(int employeeID) throws Exception {
        List<DayOff> dayOff = helper.getDayOffRequestList();
        int counter = 0;
        for (DayOff d : dayOff) {
            int dayNumber = helper.getDaysFromStart(d.getDate()) - 1;
            if(d.getEmployeeId() == employeeID) {
            	int worksToday = worksToday(d.getEmployeeId(), dayNumber);
                if (worksToday == 1) {
                    counter += d.getWeight();
                }
            }  
        }
        return counter;
    }
    
    private int checkUnwantedPatternDay(int employeeID) {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        Employee e = (Employee) schedulingPeriod.getEmployees().get(employeeID);
        Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());
        List<Pattern> patternList = helper.getPatternList();
        int punishmentPoints = 0;
        List<Integer> unwantedPatterns = c.getUnwantedPatterns();

        for (int i = 0; i < workOnDayPeriode.size(); i++) {
            String shift = roster[employeeID][i]; 
        	Day day = helper.getWeekDayOfPeriode(i);
            for (int k : unwantedPatterns) {
                boolean pattern_ok = false;
                Pattern pattern = patternList.get(k);
                List<PatternEntry> patternEntry = pattern.getPatternEntryList();
                PatternEntry trigger = patternEntry.get(0);
                if (((trigger.getShiftType().equals("Any") && shift != null) || ( trigger.getShiftType().equals("None") && shift != null)) && (trigger.getDay() == day)) {
                    for (int l = 1; l < patternEntry.size(); l++) {
                    	//if the pattern can still fit in the remaining days of the schedule
                        if (!(i + unwantedPatterns.size() - 1 >= workOnDayPeriode.size())) {
                            PatternEntry currentEntry = patternEntry.get(l);
                            String shift2 = roster[employeeID][i+l];
                            Day day2 = helper.getWeekDayOfPeriode(i + l);
                            if (((currentEntry.getShiftType().equals("Any") && shift2 != null) || ( trigger.getShiftType().equals("None") && shift2 != null)) &&
                                    (currentEntry.getDay() == day2)) {
                                pattern_ok = true;
                            } else {
                                pattern_ok = false;
                                break;
                            }
                        }
                    }
                    if (pattern_ok) {
                        punishmentPoints++;
                    }
                }
            }
        }
        return punishmentPoints;
    }
    
    private int checkUnwantedPatternShift(int employeeID) {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        Employee e = (Employee) schedulingPeriod.getEmployees().get(employeeID);
        Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());
        List<Pattern> patternList = helper.getPatternList();
        int punishmentPoints = 0;
        List<Integer> unwantedPatterns = c.getUnwantedPatterns();

        for (int i = 0; i < workOnDayPeriode.size(); i++) {
            String shift = roster[employeeID][i]; 
            Day day = helper.getWeekDayOfPeriode(i);
            for (int k : unwantedPatterns) {
                boolean pattern_ok = false;
                Pattern pattern = patternList.get(k);
                List<PatternEntry> patternEntry = pattern.getPatternEntryList();
                PatternEntry trigger = patternEntry.get(0);
                if (trigger.getShiftType().equals(shift) &&
                        (trigger.getDay() == Day.Any || trigger.getDay() == day)) {
                    for (int l = 1; l < patternEntry.size(); l++) {
                        if (!(i + unwantedPatterns.size() - 1 >= workOnDayPeriode.size())) {
                            PatternEntry currentEntry = patternEntry.get(l);
                            String shift2 = roster[employeeID][i+l]; 
                            Day day2 = helper.getWeekDayOfPeriode(i + l);
                            if (( currentEntry.getShiftType().equals(shift2)) &&
                                    (currentEntry.getDay() == Day.Any || currentEntry.getDay() == day2)) {
                                pattern_ok = true;
                            } else {
                                pattern_ok = false;
                                break;
                            }
                        }
                    }
                    if (pattern_ok) {
                        punishmentPoints++;
                    }
                }
            }
        }
        return punishmentPoints;
    }
    
    private int checkShiftOffRequest(int employeeID) {
        List<ShiftOff> shiftOff = helper.getShiftOffRequestList();
        int counter = 0;
        for (ShiftOff s : shiftOff) {
            int dayNumber = helper.getDaysFromStart(s.getDate()) - 1;
            if(s.getEmployeeId() == employeeID) {
		        String shift = s.getShiftTypeId(); //int index = shiftWithIndices.indexOf(s.getShiftTypeId());
		        String workShiftToday = roster[employeeID][dayNumber];
	            if (shift.equals(workShiftToday)) {
	                counter += s.getWeight();
	            }
            }
        }
        return counter;
    }
    
    private int checkIdenticalShiftTypesDuringWeekend(int employeeID) {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        Employee e = (Employee) schedulingPeriod.getEmployees().get(employeeID);
        Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());
        int punishmentPoints = 0;
        
        List<Day> weekendDefinition = c.getWeekendDefinition();
        if (c.isIdenticalShiftTypesDuringWeekend()) {
            for (int i = 0; i < workOnDayPeriode.size(); i++) {
                Day currentDay = helper.getWeekDayOfPeriode(i);
                if (weekendDefinition.contains(currentDay)) {
                    String currentShift = roster[employeeID][i];
                    int indexOfWeekendDefinition = weekendDefinition.indexOf(currentDay);
                    if (workOnDayPeriode.size() > i + weekendDefinition.size() - 1) {
                        for (int k = 0; k < weekendDefinition.size() - indexOfWeekendDefinition; k++) {
                            if (!currentShift.equals(roster[employeeID][k+i])) { 
                                punishmentPoints++;
                            }
                        }
                        i += weekendDefinition.size() - indexOfWeekendDefinition - 1;
                    } else {
                        // when not within the period anymore
                        for (int k = 0; k < workOnDayPeriode.size() - i; k++) {
                            if (workOnDayPeriode.get(i + k).get(employeeID) == 1) {
                                if (!currentShift.equals(roster[employeeID][k+i])) {
                                    punishmentPoints++;
                                }
                            }
                        }
                        i += weekendDefinition.size() - indexOfWeekendDefinition - 1;
                    }
                }
            }
        }
        
        return punishmentPoints;
    }
    
    private int checkNoNightShiftBeforeFreeWeekend(int employeeID) {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        Employee e = (Employee) schedulingPeriod.getEmployees().get(employeeID);
        Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());
        int punishmentPoints = 0;
        
        List<Day> weekendDefinition = c.getWeekendDefinition();
        if (c.isNoNightShiftBeforeFreeWeekend()) {
            for (int i = 0; i < workOnDayPeriode.size(); i++) {
                Day currentDay = helper.getWeekDayOfPeriode(i);
                if (weekendDefinition.get(0) == currentDay &&
                        workOnDayPeriode.get(i).get(employeeID) == 0 &&
                        i != 0 &&
                        roster[employeeID][i-1].equals("N")){
                    punishmentPoints++;
                }
            }
        }
        return punishmentPoints;
    }

    
    private int worksToday(int employeeID, int dayNumber) throws Exception {
    	int worksToday = 0;
    	if(roster[employeeID][dayNumber] != null) {
    		worksToday = 1;
        }
        return worksToday;
    }


}
