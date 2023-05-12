package main;

import Attributes.*;
import main.RequirementsForDay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Constraint {
    private SchedulingPeriod schedulingPeriod;
    private Helper helper;
    private String[][] roster;

    public Constraint(SchedulingPeriod schedulingPeriod, String[][] roster) {
        this.schedulingPeriod = schedulingPeriod;
        this.roster = roster;
        helper = new Helper(this.schedulingPeriod, this.roster);
    }

    /**
     * Prüft die roster auf harte Restriktionen
     *
     * @return true, wenn die harten Restriktionen erfüllt wurden, false sonst
     */
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

    /**
     * Prüft die weichen Restriktionen
     *
     * @return Strafpunkte
     * @throws Exception wenn harte Restriktionen nicht erfüllt wurden
     */
    public int calcRosterScore() throws Exception {
        int punishmentPoints = 0;
        if (!checkHardConst()) {
            throw new Exception("Verstoß gegen harte Restriktion...");
        }
        punishmentPoints += checkNumbAssigment();
        punishmentPoints += checkMaxConsecutiveWorkingDays();
        punishmentPoints += checkMinConsecutiveWorkingDays();
        punishmentPoints += checkMaxConsecutiveFreeDays();
        punishmentPoints += checkMinConsecutiveFreeDays();
        punishmentPoints += checkWeekendConstraints();
        punishmentPoints += checkCompleteWeekends();
        punishmentPoints += checkIdenticalShiftTypesDuringWeekend();
        punishmentPoints += checkNoNightShiftBeforeFreeWeekend();
        punishmentPoints += checkDayOffRequest();
        punishmentPoints += checkShiftOffRequest();
        punishmentPoints += checkUnwantedPattern();

        return punishmentPoints;
    }

    /**
     * Prüft die aufeinanderfolgende Arbeitstage gegen die im Vertrag vereinbarte maximalgröße.
     * Annahme: Für jeden einzelnen Tag, wird ein weiterer Strafpunkt verteilt
     *
     *
     * @return Strafpunkt
     */
    private int checkMaxConsecutiveWorkingDays() {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        List<Employee> employeeList = helper.getEmployeeList();
        List<Contract> contractList = helper.getContractList();
        int punishmentPoints = 0;
        //für alle employee
        for (int j = 0; j < workOnDayPeriode.get(0).size(); j++) {
            int employeeContractId = employeeList.get(j).getContractId();
            Contract currentContract = contractList.get(employeeContractId);
            int maxConsecutiveWorkingDays = currentContract.getMaxConsecutiveWorkingDays();
            //für alle Tage
            for (int i = 0; i < workOnDayPeriode.size(); i++) {
                //wenn ein Arbeitstag
                if (workOnDayPeriode.get(i).get(j) == 1) {
                    if (currentContract.getMaxConsecutiveWorkingDays_on() == 1) {
                        int counter = 0;
                        //wenn aktueller Tag + MaxConDays noch innerhalb der Periode liegt
                        if (workOnDayPeriode.size() > i + currentContract.getMaxConsecutiveWorkingDays()) {
                            //Zähle alle Tage vom aktuellen bis MaxConDays + 1 zusammen
                            for (int k = 0; k < maxConsecutiveWorkingDays + 1; k++) {
                                counter += workOnDayPeriode.get(i + k).get(j);
                            }
                            if (counter == maxConsecutiveWorkingDays + 1) {
                                punishmentPoints += currentContract.getMaxConsecutiveWorkingDays_weight();
                            }
                        }
                    }
                }
            }
        }
        return punishmentPoints;
    }

    /**
     * Prüft die aufeinanderfolgende Arbeitstage gegen die im Vertrag vereinbarte minimalgröße.
     * Bei unterschreitung der minimalgröße => Strafpunkt je unterschrittenem Tag
     * Annahme: Für jeden Tag gibt es einen Strafpunkt; Am Ende einer Persiode werden auch für die Tage Strafpunkte
     * verteilt, auch wenn der Tage in der nächsten Periode unbekannt sind
     *
     * @return Strafpunkt
     */
    private int checkMinConsecutiveWorkingDays() {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        List<Employee> employeeList = helper.getEmployeeList();
        List<Contract> contractList = helper.getContractList();
        int punishmentPoints = 0;
        //für alle employee
        for (int j = 0; j < employeeList.size(); j++) {
            int employeeContractId = employeeList.get(j).getContractId();
            Contract currentContract = contractList.get(employeeContractId);
            int minConsecutiveWorkingDays = currentContract.getMinConsecutiveWorkingDays();
            //für alle Tage
            if (currentContract.getMinConsecutiveWorkingDays_on() == 1) {
                for (int i = 0; i < workOnDayPeriode.size(); i++) {
                    int counter = 0;
                    //Sonderfall: erster Tag in Periode
                    if (i == 0 && workOnDayPeriode.get(i).get(j) == 1) {

                        //Zähle alle Arbeitstage von i bis minConWorkingDays
                        for (int k = 0; k < minConsecutiveWorkingDays; k++) {
                            if (workOnDayPeriode.get(k).get(j) == 1) {
                                counter++;
                            } else {
                                break;
                            }
                        }
                        if (counter < minConsecutiveWorkingDays) {
                            punishmentPoints += currentContract.getMinConsecutiveWorkingDays_weight();
                        }
                    }
                    //wenn Anfang einer konsekutiven Reihe von Arbeitstagen
                    else if (i + 1 < workOnDayPeriode.size() && workOnDayPeriode.get(i).get(j) == 0 &&
                            workOnDayPeriode.get(i + 1).get(j) == 1) {

                        //wenn aktueller Tag + MinConDays noch innerhalb der Periode liegt
                        if (workOnDayPeriode.size() > i + currentContract.getMinConsecutiveWorkingDays()) {
                            //Zähle alle Tage vom morgigen bis MinConDays
                            for (int k = i + 1; k < i + 1 + minConsecutiveWorkingDays; k++) {
                                // Abfrage, ob die Konsekutive Reihe von Arbeitstagen beendet ist
                                if (workOnDayPeriode.get(k).get(j) == 1) {
                                    counter++;
                                } else {
                                    break;
                                }
                            }
                        }
                        // wenn nicht mehr innerhalb der Periode, zähle Arbeitstage bis Ende der Persiode
                        else {
                            //Zähle alle Tage vom morgigen bis Ende der Periode
                            for (int k = i + 1; k < workOnDayPeriode.size(); k++) {
                                // Abfrage, ob die Konsekutive Reihe von Arbeitstagen beendet ist
                                if (workOnDayPeriode.get(k).get(j) == 1) {
                                    counter++;
                                } else {
                                    break;
                                }
                            }
                        }
                        // Strafpunkte je unterschrittenem Tag
                        if (counter < minConsecutiveWorkingDays) {
                            counter = minConsecutiveWorkingDays - counter;
                            punishmentPoints += currentContract.getMinConsecutiveWorkingDays_weight() * counter;
                        }
                    }
                }
            }
        }
        return punishmentPoints;
    }

    /**
     * Prüft die aufeinanderfolgende freie Tage gegen die im Vertrag vereinbarte maximalgröße.
     * Für jeden Tag extra, wird ein weiterer Strafpunkt verteilt
     * Annahme: siehe MaxConsecutiveWorkingDays
     *
     * @return Strafpunkt
     */
    private int checkMaxConsecutiveFreeDays() {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        List<Employee> employeeList = helper.getEmployeeList();
        List<Contract> contractList = helper.getContractList();
        int punishmentPoints = 0;
        //für alle employee
        for (int j = 0; j < workOnDayPeriode.get(0).size(); j++) {
            int employeeContractId = employeeList.get(j).getContractId();
            Contract currentContract = contractList.get(employeeContractId);
            int maxConsecutiveFreeDays = currentContract.getMaxConsecutiveFreeDays();
            if (currentContract.getMaxConsecutiveFreeDays_on() == 1) {
                //für alle Tage
                for (int i = 0; i < workOnDayPeriode.size(); i++) {
                    //Wenn KEIN Arbeitstag
                    if (workOnDayPeriode.get(i).get(j) == 0) {
                        int counter = 0;
                        //wenn aktueller Tag + MaxConDays noch innerhalb der Periode liegt
                        if (workOnDayPeriode.size() > i + currentContract.getMaxConsecutiveFreeDays()) {
                            //Zähle alle Tage vom aktuellen bis MaxConDays + 1 zusammen
                            for (int k = 0; k < maxConsecutiveFreeDays + 1; k++) {
                                counter += workOnDayPeriode.get(i + k).get(j);
                            }
                            if (counter == 0) {
                                punishmentPoints += currentContract.getMaxConsecutiveFreeDays_weight();
                            }
                        }
                    }
                }
            }
        }
        return punishmentPoints;
    }

    /**
     * Prüft die aufeinanderfolgende freien Arbeitstage gegen die im Vertrag vereinbarte minimalgröße.
     * Bei unterschreitung der minimalgröße => Strafpunkt
     * Annahme: siehe MinConsecutiveWorkingDays
     *
     * @return Strafpunkt
     */
    private int checkMinConsecutiveFreeDays() {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        List<Employee> employeeList = helper.getEmployeeList();
        List<Contract> contractList = helper.getContractList();
        int punishmentPoints = 0;
        //für alle employee
        for (int j = 0; j < workOnDayPeriode.get(0).size(); j++) {
            int employeeContractId = employeeList.get(j).getContractId();
            Contract currentContract = contractList.get(employeeContractId);
            int minConsecutiveFreeDays = currentContract.getMinConsecutiveFreeDays();
            if (currentContract.getMinConsecutiveFreeDays_on() == 1) {
                //für alle Tage
                for (int i = 0; i < workOnDayPeriode.size(); i++) {
                    int counter = 0;
                    //Sonderfall: erster Tag in Periode
                    if (i == 0 && workOnDayPeriode.get(i).get(j) == 0) {

                        //Zähle alle nicht Arbeitstage von i bis minConFreeDays
                        for (int k = 0; k < minConsecutiveFreeDays; k++) {
                            if (workOnDayPeriode.get(k).get(j) == 0) {
                                counter += 1;
                            } else {
                                break;
                            }
                        }
                        if (counter < minConsecutiveFreeDays) {
                            counter = minConsecutiveFreeDays - counter;
                            punishmentPoints += currentContract.getMinConsecutiveFreeDays_weight() * counter;
                        }
                    }
                    //wenn Angang einer Arbeitstagreihe
                    else if (i + 1 < workOnDayPeriode.size() && workOnDayPeriode.get(i).get(j) == 1 &&
                            workOnDayPeriode.get(i + 1).get(j) == 0) {

                        //wenn aktueller Tag + MinConDays noch innerhalb der Periode liegt
                        if (workOnDayPeriode.size() > i + currentContract.getMinConsecutiveFreeDays()) {
                            //Zähle alle Tage vom morgigen bis MinConDays
                            for (int k = 0; k < minConsecutiveFreeDays; k++) {
                                if (workOnDayPeriode.get(i + k + 1).get(j) == 0) {
                                    counter += 1;
                                } else {
                                    break;
                                }
                            }
                        }
                        // wenn nicht mehr innerhalb der Periode, zähle freie Tage bis Ende der Persiode
                        else {
                            //Zähle alle Tage vom morgigen bis Ende der Periode
                            for (int k = i + 1; k < workOnDayPeriode.size(); k++) {
                                // Abfrage, ob die Konsekutive Reihe von Arbeitstagen beendet ist
                                if (workOnDayPeriode.get(k).get(j) == 0) {
                                    counter++;
                                } else {
                                    break;
                                }
                            }
                        }
                        if (counter < minConsecutiveFreeDays) {
                            counter = minConsecutiveFreeDays - counter;
                            punishmentPoints += currentContract.getMinConsecutiveFreeDays_weight() * counter;
                        }
                    }
                }
            }
        }
        return punishmentPoints;
    }

    /**
     * Prüft die aufeinanderfolgenden Arbeits-Wochenenden und zählt:
     * 1. um wie viel die Maximalanzahl überschritten wurde
     * 2. um wie viel die Minimalanzahl unterschritten wurde
     * <p>
     * 3. Prüft die Wochenenden in einer Persiode und zält um wie viel die Maximalanzahl überschritten wurde
     * Annahme: Wenn an einem Tag des WE gearbetet wurde, zählt es als Arbeitswochenende;
     * Für jedes weitereWochenende Wochende gibt es einen weitern Strafpunkt
     *
     * @return Strafpunkte - je überschrittene Einheit
     */
    private int checkWeekendConstraints() {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        List<Employee> employeeList = helper.getEmployeeList();
        List<Contract> contractList = helper.getContractList();
        int punishmentPoints_maxCon = 0;
        int punishmentPoints_minCon = 0;
        int punishmentPoints_maxWorkingWe = 0;
        //für alle employee
        for (int j = 0; j < workOnDayPeriode.get(0).size(); j++) {
            int contractId = employeeList.get(j).getContractId();
            Contract currentContract = contractList.get(contractId);
            List<Day> weekendDefinition = currentContract.getWeekendDefinition();
            int countConsecutiveWorkAtWeekend = 0;
            int countWorkAtWeekend = 0;
            //für alle Tage
            boolean oldValue = false;
            for (int i = 0; i < workOnDayPeriode.size(); i++) {
                Day currentDay = helper.getWeekDayOfPeriode(i);
                if (weekendDefinition.contains(currentDay)) {
                    int indexOfWeekendDefinition = weekendDefinition.indexOf(currentDay);
                    boolean workAtWeekend = false;
                    if (workOnDayPeriode.size() > i + weekendDefinition.size() - 1) {
                        for (int k = 0; k < weekendDefinition.size() - indexOfWeekendDefinition; k++) {
                            if (workOnDayPeriode.get(i + k).get(j) == 1) {
                                workAtWeekend = true;
                                break;
                            }
                        }
                    } else {
                        for (int k = 0; k < workOnDayPeriode.size() - i; k++) {
                            if (workOnDayPeriode.get(i + k).get(j) == 1) {
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
            int maxConsecutiveWorkingWeekends = currentContract.getMaxConsecutiveWorkingWeekends();
            if (currentContract.getMaxConsecutiveWorkingWeekends_on() == 1 &&
                    countConsecutiveWorkAtWeekend > maxConsecutiveWorkingWeekends) {
                punishmentPoints_maxCon += (countConsecutiveWorkAtWeekend - maxConsecutiveWorkingWeekends) *
                        currentContract.getMaxConsecutiveWorkingWeekends_weight();
            }
            int minConsecutiveWorkingWeekends = currentContract.getMinConsecutiveWorkingWeekends();
            if (currentContract.getMinConsecutiveWorkingWeekends_on() == 1 &&
                    countConsecutiveWorkAtWeekend < minConsecutiveWorkingWeekends) {
                punishmentPoints_minCon += (minConsecutiveWorkingWeekends - countConsecutiveWorkAtWeekend) *
                        currentContract.getMaxConsecutiveWorkingWeekends_weight();
            }
            int maxWorkingWeekendsInFourWeeks = currentContract.getMaxWorkingWeekendsInFourWeeks();
            if (currentContract.getMaxWorkingWeekendsInFourWeeks_on() == 1 &&
                    countWorkAtWeekend > maxWorkingWeekendsInFourWeeks) {
                punishmentPoints_maxWorkingWe += (countWorkAtWeekend - maxWorkingWeekendsInFourWeeks) *
                        currentContract.getMaxWorkingWeekendsInFourWeeks_weight();
            }
        }
        return punishmentPoints_maxCon + punishmentPoints_minCon + punishmentPoints_maxWorkingWe;
    }

    /**
     * Prüft nach, wenn am Wochenende gearbetet wird, dann gleich das ganze Wochenende
     * Annahme: Angebrochene Wochenenden am Anfang und am Ende der Persiode werden nicht berücksichtigt
     *
     * @return Strafpunkte
     */
    private int checkCompleteWeekends() {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        List<Employee> employeeList = helper.getEmployeeList();
        List<Contract> contractList = helper.getContractList();
        int punishmentPoints = 0;
        //für alle employee
        for (int j = 0; j < workOnDayPeriode.get(0).size(); j++) {
            int contractId = employeeList.get(j).getContractId();
            Contract currentContract = contractList.get(contractId);
            List<Day> weekendDefinition = currentContract.getWeekendDefinition();
            if (currentContract.isCompleteWeekends()) {
                //für alle Tage
                for (int i = 0; i < workOnDayPeriode.size(); i++) {
                    Day currentDay = helper.getWeekDayOfPeriode(i);
                    if (weekendDefinition.contains(currentDay)) {
                        int indexOfWeekendDefinition = weekendDefinition.indexOf(currentDay);
                        int workAtWeekend = 0;
                        if (workOnDayPeriode.size() > i + weekendDefinition.size() - 1) {
                            for (int k = 0; k < weekendDefinition.size() - indexOfWeekendDefinition; k++) {
                                if (workOnDayPeriode.get(i + k).get(j) == 1) {
                                    workAtWeekend++;
                                }
                            }
                        } else {
                            for (int k = 0; k < workOnDayPeriode.size() - i; k++) {
                                if (workOnDayPeriode.get(i + k).get(j) == 1) {
                                    workAtWeekend++;
                                }
                            }
                            // wenn am letzten Tag gearbeitet wird und es ein Wochenende ist
                            if (workOnDayPeriode.size() - i - 1 == 0 && workAtWeekend != 0) {
                                if (weekendDefinition.size() == 3) {
                                    workAtWeekend += 2;
                                } else {
                                    workAtWeekend += 1;
                                }
                            }
                            //wenn am vorletzten Tag gearbeitet wird und es ein Wochenende ist (nur für Fr,Sa,So)
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
        }
        return punishmentPoints;
    }

    /**
     * Prüft für jeden Employee ob die Nachtschicht vor dem Beginn eines Wochenendes gearbeitet wird.
     * Wenn ja erfolgt ein Strafpunkt.
     * Annahme: Wenn der erste Tag des Wochenendes laut Wochenendefinition nicht gearbetet wird, darf keine Nachtschicht
     * am Vortag verteilt werden
     *
     * @return Strafpunkte
     */
    private int checkNoNightShiftBeforeFreeWeekend() {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        List<Employee> employeeList = helper.getEmployeeList();
        List<Contract> contractList = helper.getContractList();
        int punishmentPoints = 0;
        //für alle employee
        for (int j = 0; j < workOnDayPeriode.get(0).size(); j++) {
            int contractId = employeeList.get(j).getContractId();
            Contract currentContract = contractList.get(contractId);
            List<Day> weekendDefinition = currentContract.getWeekendDefinition();
            if (currentContract.isNoNightShiftBeforeFreeWeekend()) {
                //für alle Tage
                for (int i = 0; i < workOnDayPeriode.size(); i++) {
                    Day currentDay = helper.getWeekDayOfPeriode(i);
                    if (weekendDefinition.get(0) == currentDay &&
                            workOnDayPeriode.get(i).get(j) == 0 &&
                            i != 0 &&
                            roster[j][i-1].equals("N")){   //roster.get(i - 1)[nightShiftIndex][j] == 1) {
                        punishmentPoints++;
                    }
                }
            }
        }
        return punishmentPoints;
    }

    /**
     * Prüft nach, dass an einem Wochenende die gleiche Schicht gearbeitet wird.
     * Annahme: Der erste Tag des definierten Wochenendes legt den Schichttyp fest, auf welchen die nächsten Tage
     * überprüft werden.
     * Nicht an diesem Tag zu arbeiten, ist auch eine Art Schicht.
     * Annahme: Der erste Tag des Wochenendes definiert wie das Restwochenende auszusehen hat.
     *
     * @return Strafpunkte
     */
    private int checkIdenticalShiftTypesDuringWeekend() {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        List<Employee> employeeList = helper.getEmployeeList();
        List<Contract> contractList = helper.getContractList();
        int punishmentPoints = 0;
        //für alle employee
        for (int j = 0; j < workOnDayPeriode.get(0).size(); j++) {
            int contractId = employeeList.get(j).getContractId();
            Contract currentContract = contractList.get(contractId);
            List<Day> weekendDefinition = currentContract.getWeekendDefinition();
            if (currentContract.isIdenticalShiftTypesDuringWeekend()) {
                //für alle Tage
                for (int i = 0; i < workOnDayPeriode.size(); i++) {
                    Day currentDay = helper.getWeekDayOfPeriode(i);
                    if (weekendDefinition.contains(currentDay)) {
                        String currentShift = roster[j][i];//helper.getShiftOfDay(i, j);
                        int indexOfWeekendDefinition = weekendDefinition.indexOf(currentDay);
                        if (workOnDayPeriode.size() > i + weekendDefinition.size() - 1) {
                            for (int k = 0; k < weekendDefinition.size() - indexOfWeekendDefinition; k++) {
                                if (!currentShift.equals(roster[j][k+i])) { //(helper.getShiftOfDay(k + i, j))) {
                                    punishmentPoints++;
                                }
                            }
                            i += weekendDefinition.size() - indexOfWeekendDefinition - 1;
                        } else {
                            // Wenn nicht mehr in Periode
                            for (int k = 0; k < workOnDayPeriode.size() - i; k++) {
                                if (workOnDayPeriode.get(i + k).get(j) == 1) {
                                    if (!currentShift.equals(roster[j][k+i])) { //helper.getShiftOfDay(k + i, j))) {
                                        punishmentPoints++;
                                    }
                                }
                            }
                            i += weekendDefinition.size() - indexOfWeekendDefinition - 1;
                        }
                    }
                }
            }
        }
        return punishmentPoints;
    }

    /**
     * Prüft und zählt die Schichten, an dem sich die Employees frei gewünscht haben, sie aber trotzdem arbeiten müssen
     *
     * @return Strafpunkte
     */
    private int checkShiftOffRequest() {
        List<ShiftOff> shiftOff = helper.getShiftOffRequestList();
        int counter = 0;
        for (ShiftOff s : shiftOff) {
            int dayNumber = helper.getDaysFromStart(s.getDate()) - 1;
            String shift = s.getShiftTypeId(); //int index = shiftWithIndices.indexOf(s.getShiftTypeId());
            String workShiftToday = roster[s.getEmployeeId()][dayNumber];
            if (shift.equals(workShiftToday)) {
                counter += s.getWeight();
            }
        }
        return counter;
    }

    /**
     * Prüft und zählt die Tage, an dem sich die employees frei gewünscht haben, sie aber trotzdem arbeiten Müssen
     *
     * @return Strafpunkte
     * @throws Exception wenn harte Restriktionen nicht erfüllt wurden
     */
    private int checkDayOffRequest() throws Exception {
        List<DayOff> dayOff = helper.getDayOffRequestList();
        int counter = 0;
        for (DayOff d : dayOff) {
            int dayNumber = helper.getDaysFromStart(d.getDate()) - 1;
            int worksToday = worksToday(d.getEmployeeId(), dayNumber);
            if (worksToday == 1) {
                counter += d.getWeight();
            }
        }
        return counter;
    }

    /**
     * Gibt an ob ein employee x am Tag y arbeitet.
     *
     * @param employeeId EmployeeID
     * @param dayNumber  Der Tag nach beginn der Periode (Also wenn die Woche am Montag beginnt,
     *                   wäre der erste Mittwoch eine 3)
     * @return 1, wenn employee an dem Tag arbeitet (egal welche Schicht), 0 sonst
     * @throws Exception
     */
    private int worksToday(int employeeId, int dayNumber) throws Exception {
    	int worksToday = 0;
    	if(roster[employeeId][dayNumber] != null) {
    		worksToday = 1;
        }
        return worksToday;
    }

    /**
     * Prüft, ob die minimale und maximale Anzahl an Diensten in einer Periode, festgelegt im Vertrag, über-
     * bzw. unterschritten sind.
     *
     * @return Differenz in Tagen bei Über- und Unterschreitung (einen Tag zu vie/wenig => Strafpunkt)
     */
    private int checkNumbAssigment() {

        //List: numbOfShiftInPeriod Number of Workingdays per Nurse per Period
        int employeeSize = helper.getEmployeeList().size();
        int shiftSize = helper.getShiftList().size();
        List<Integer> numbOfShiftInPeriod = new ArrayList<>(Collections.nCopies(employeeSize, 0));
        for (int[][] aSolution : roster) {
            for (int k = 0; k < shiftSize; k++) {
                for (int l = 0; l < employeeSize; l++) {
                    int temp = numbOfShiftInPeriod.get(l) + aSolution[k][l];
                    numbOfShiftInPeriod.set(l, temp);
                }
            }
        }
        //Liste der Mitarbeiter mit der Differenz (Restriktionsverstoß) TODO: evtl. umbenennen
        List<Integer> numbOfDiffDays = new ArrayList<>();
        for (int i = 0; i < numbOfShiftInPeriod.size(); i++) {
            Employee e = (Employee) schedulingPeriod.getEmployees().get(i);
            Contract c = (Contract) schedulingPeriod.getContracts().get(e.getContractId());
            int diffDays = 0;
            int daysOfWork = numbOfShiftInPeriod.get(i);
            int maxDaysOfWork = c.getMaxNumAssignments();
            int minDaysOfWork = c.getMinNumAssignments();
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
            numbOfDiffDays.add(diffDays);
        }
        //summierte Liste
        return numbOfDiffDays.stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Prüft die unwantedPatterns
     * Annahme: Erst wenn das gesamte Pattern erfüllt ist, gibt es genau einen Strafpunkt.
     *
     * @return Strafpunkte
     */
    private int checkUnwantedPattern() {
        List<List<Integer>> workOnDayPeriode = helper.getWorkingList();
        List<Employee> employeeList = helper.getEmployeeList();
        List<Contract> contractList = helper.getContractList();
        List<Pattern> patternList = helper.getPatternList();
        int punishmentPoints = 0;
        //für alle employee
        for (int j = 0; j < employeeList.size(); j++) {
            int employeeContractId = employeeList.get(j).getContractId();
            Contract currentContract = contractList.get(employeeContractId);
            List<Integer> unwantedPatterns = currentContract.getUnwantedPatterns();

            //für alle Tage
            for (int i = 0; i < workOnDayPeriode.size(); i++) {
                String shift = roster[j][i]; //helper.getShiftOfDay(i, j);
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
                                String shift2 = roster[j][i+l]; //helper.getShiftOfDay(i + l, j);
                                Day day2 = helper.getWeekDayOfPeriode(i + l);
                                if (((currentEntry.getShiftType().equals("Any")&& shift2 != "None") || currentEntry.getShiftType().equals(shift2)) &&
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
        }
        return punishmentPoints;
    }
}
