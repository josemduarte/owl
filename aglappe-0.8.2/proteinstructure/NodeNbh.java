package proteinstructure;

import java.util.TreeMap;
import java.util.Collections;

public class NodeNbh extends TreeMap<Integer,String> {
	

	private static final long serialVersionUID = 1L;
	
	public static final String centralLetter="x";

	// central residue
	public int central_resser;
	public String central_resType;
	
	/**
	 * Specific NodeNbh: is a neighbourhood in a specific structure e.g. ABCxEFG with x=D in position 25
	 * @param resser
	 * @param resType
	 */
	public NodeNbh(int resser, String resType){
		super();
		this.central_resser=resser;
		this.central_resType=resType;
	}

	public String getMotifFullGaps(){
		String motif="";
		int min=Math.min(central_resser, Collections.min(this.keySet()));
		int max=Math.max(central_resser, Collections.max(this.keySet()));
		for (int i=min;i<=max;i++){
			if (this.containsKey(i)){
				motif+=AA.threeletter2oneletter(this.get(i));
			} else if (i==central_resser){
				motif+=centralLetter;
			} else {
				motif+="_";
			}
		}
		return motif;
	}
	
	public String getMotif(){
		String motif="";
		int min=Math.min(central_resser, Collections.min(this.keySet()));
		int max=Math.max(central_resser, Collections.max(this.keySet()));
		int gapSize=0;
		String gap="";
		for (int i=min;i<=max;i++){
			if (this.containsKey(i)){
				motif+=gap;
				motif+=AA.threeletter2oneletter(this.get(i));
				gapSize=0;
				gap="";
			} else if (i==central_resser){
				motif+=gap;
				motif+=centralLetter;
				gapSize=0;
				gap="";
			} else {
				gapSize++;
				gap="_{"+gapSize+"}";
			}
		}
		return motif;
	}

	public String toString(){
		if (this.isEmpty()) return "";
		else return this.getMotif();
	}
	
	/**
	 * Returns a copy (deep) of this NodeNbh as a new NodeNbh object
	 * @return
	 */
	public NodeNbh getCopy(){
		NodeNbh copy = new NodeNbh(this.central_resser,this.central_resType);
		copy.putAll(this);
		return copy;
	}
}
