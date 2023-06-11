

import java.util.List;

public class Pattern {
    private int id;
    private int weight;
    private List<PatternEntry> patternEntryList;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public List<PatternEntry> getPatternEntryList() {
        return patternEntryList;
    }

    public void setPatternEntryList(List<PatternEntry> patternEntryList) {
        this.patternEntryList = patternEntryList;
    }
    
    public boolean isShiftSpecific() {
    	for(int i=0; i<patternEntryList.size(); i++) {
    		String shiftType = patternEntryList.get(i).getShiftType();
    		if(shiftType.equals("None") == false && shiftType.equals("Any") == false) {
    			return true;
    		}
    	}
    	return false;
    }
    
}
