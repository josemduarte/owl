package proteinstructure;

/**
 * A particular secondary structure element within a protein structure
 */
public class SecStrucElement {
	
	/*------------------------------ constants ------------------------------*/
	// three/four state secondary structure types (for 3 state, skip turn)
	public static final char HELIX = 'H';	// a helix
	public static final char STRAND = 'S';  // a beta strand
	public static final char TURN = 'T';    // a hydrogen bonded turn
	public static final char OTHER = 'O';   // all other states
	
	/*--------------------------- member variables --------------------------*/
	
	String secStrucId;		// legacy field for old ss ids (e.g. H1, S1, ...)
	char secStrucType;		// one of the above constants
	Interval interval;		// the location of this element in the sequence
	
	/*----------------------------- constructors ----------------------------*/
	
	public SecStrucElement(char secStrucType, int startRes, int endRes, String legacyId) {
		this.secStrucId = legacyId;
		this.secStrucType = secStrucType;
		this.interval = new Interval(startRes, endRes);
	}
	
	/*---------------------------- public methods ---------------------------*/
	
	public SecStrucElement copy() {
		return new SecStrucElement(secStrucType, interval.beg, interval.end, secStrucId);
	}
	
	/** Returns the legacy ID of this element (e.g. H1, S1, ...) */
	public String getId() {
		return secStrucId;
	}
	
	/** Returns the dssp type of this element. Valid values are H, S, T, O */ 
	public char getType() {
		return secStrucType;
	}
	
	/** Returns the range of this ss element in the sequence. */ 
	public Interval getInterval() {
		return interval;
	}
	
	/** Returns true if this ss element is a helix */
	public boolean isHelix() {
		return secStrucType == HELIX;
	}
	
	/** Returns true if this ss element is a beta strand */
	public boolean isStrand() {
		return secStrucType == STRAND;
	}
	
	/** Returns true if this ss element is a hydrogen bonded turn */
	public boolean isTurn() {
		return secStrucType == TURN;
	}	

	/*---------------------------- static methods ---------------------------*/
	
	public static char getFourStateTypeFromDsspType(char dsspType) {
		char type = dsspType;
		switch(dsspType) {
		case 'H':
		case 'G':
		case 'I': 
			type = HELIX;
			break;
		case 'E':
			type = STRAND;
			break;
		case 'T':
			type = TURN;
			break;
		case 'S':
		case 'B':
			type = OTHER;
			break;
		default:
			type = OTHER;
		}
		return type;
	}
	
}