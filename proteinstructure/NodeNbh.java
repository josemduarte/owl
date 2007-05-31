package proteinstructure;

import java.util.TreeMap;
import java.util.Collections;

public class NodeNbh extends TreeMap<Integer,String> {
	

	private static final long serialVersionUID = 1L;
	
	public static final String centralLetter="x";

	// central residue
	public int central_resser;
	public String central_resType;
	
	public NodeNbh(int i_resser, String i_resType){
		super();
		this.central_resser=i_resser;
		this.central_resType=i_resType;
	}
	
	public String getMotif(){
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

	public String getMotifwg(){
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

}
