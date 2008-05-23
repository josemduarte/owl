package graphAveraging;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.TreeMap;

import proteinstructure.Alignment;
import proteinstructure.PairwiseSequenceAlignment;
import proteinstructure.Pdb;
import proteinstructure.Template;
import proteinstructure.TemplateList;
import proteinstructure.PairwiseSequenceAlignment.PairwiseSequenceAlignmentException;
import tools.Goodies;
import tools.MySQLConnection;

public class PhiPsiAverager {

	private Alignment al;								// alignment
	private ArrayList<TemplateWithPhiPsi> templates;

	public PhiPsiAverager(TemplateList templates, Alignment aln) {

		this.al = aln;
		if (!templates.isPdbDataLoaded()) {
			throw new IllegalArgumentException("TemplateList passed to PhiPsiAverager constructor must have PDB data loaded");
		}
		checkSequences(templates);
		getPhiPsi(templates);

	}
	
	/**
	 * Gets the consensus phi/psi angles mapped onto the sequence of the given targetTag
	 * If the targetTag is not in the Alignment an IllegalArgumentException is thrown
	 * @param threshold
	 * @param angleInterval
	 * @param targetTag
	 * @return
	 * @throws IllegalArgumentException if targetTag is not present in this.al
	 */
	public TreeMap<Integer, ConsensusSquare> getConsensusPhiPsiOnTarget(double threshold, int angleInterval, String targetTag) {
		if (!al.hasTag(targetTag)) throw new IllegalArgumentException("Given targetTag is not present in alignment");
		TreeMap<Integer, ConsensusSquare> phiPsiConsensus = this.getConsensusPhiPsi(threshold, angleInterval);
		TreeMap<Integer, ConsensusSquare> phiPsiConsOnTarget = new TreeMap<Integer, ConsensusSquare>();
		for (int i:phiPsiConsensus.keySet()) {
			int resser = al.al2seq(targetTag, i);
			if (resser!=-1) {
				phiPsiConsOnTarget.put(resser, phiPsiConsensus.get(i));
			}
		}
		return phiPsiConsOnTarget;
	}
	
	/**
	 * Gets the consensus phi/psi angles for each alignment column with phi/psi consensus.
	 * The return is a TreeMap with keys alignment positions, values the ConsensusSquare. 
	 * Columns without phi/psi consensus are not present in the TreeMap
	 * @param threshold a value >= 0.5
	 * @param angleInterval
	 * @return
	 * @throws IllegalArgumentException if threshold < 0.5
	 */
	public TreeMap<Integer, ConsensusSquare> getConsensusPhiPsi(double threshold, int angleInterval) {

		if (threshold < 0.5) {
			throw new IllegalArgumentException("Threshold for consensus ph/psi must be above or equals to 0.5");
		}

		// we round DOWN given threshold. So for the 2 cases even/odd and limit case threshold=0.5: 
		// - odd case  : 3 templates => voteThreshold=1, then we apply cutoff (see getInterval) with a '>' so that 2 falls within threshold
		// - even case : 4 templates => voteThreshold=2, then we applu cutoff (see getInterval) with a '>' so that 2 doesn't fall within threshold 
		int voteThreshold = (int) (templates.size()*threshold); // the int casting rounds down
		
		TreeMap<Integer, ConsensusSquare> bounds = new TreeMap<Integer, ConsensusSquare>();

		// we go through each column i in the alignment and find the consensus per column
		for (int i=1; i<=al.getAlignmentLength(); i++) {
			HashMap<Integer,Double> phiAnglesColumnI = new HashMap<Integer, Double>();
			HashMap<Integer,Double> psiAnglesColumnI = new HashMap<Integer, Double>();
			for (int j=0;j<templates.size();j++) {
				TemplateWithPhiPsi template = templates.get(j);
				int resser = al.al2seq(template.getId(), i);

				phiAnglesColumnI.put(j, Double.NaN);
				psiAnglesColumnI.put(j, Double.NaN);
				if (resser!=-1) { // to skip gaps
					if (template.hasPhiPsiAngles(resser)) { // some columns won't have angle data because of unobserved i, i-1 or i+1 residue. Or for N and C-terminals
						phiAnglesColumnI.put(j, template.getPhiAngle(resser));
						psiAnglesColumnI.put(j, template.getPsiAngle(resser));
					}
				} 
			}
			
			ConsensusSquare consIntervalPhPsi = findConsSquare(phiAnglesColumnI, psiAnglesColumnI, voteThreshold, angleInterval);
			
			if (consIntervalPhPsi!=null) {
				bounds.put(i, consIntervalPhPsi);
			}
			
		}
		return bounds;
	}

	/**
	 * Finds the final ConsensusSquare from the list of all candidates 
	 * returned from findAllConsSquares
	 * The returning value is the square region of the phi/psi (Ramachandran)
	 * space where the consensus phi/psi angles are.
	 * @param values1stDim
	 * @param values2ndDim
	 * @param voteThreshold
	 * @param angleInterval
	 * @return the ConsensusSquare with the phi/psi consensus intervals or null if no consensus for this column
	 */
	private ConsensusSquare findConsSquare(HashMap<Integer,Double> values1stDim, HashMap<Integer,Double> values2ndDim, int voteThreshold, int angleInterval) {
		ArrayList<ConsensusSquare> allConsIntervals = findAllConsSquares(values1stDim, values2ndDim, voteThreshold, angleInterval);
		if (allConsIntervals.isEmpty()) {
			return null;
		}
		// 1st we sort on voteCount descending (max count first)
		Collections.sort(allConsIntervals, new Comparator<ConsensusSquare>() {
			public int compare(ConsensusSquare o1, ConsensusSquare o2) {
				return (new Integer(o2.getVoteCount()).compareTo(o1.getVoteCount()));
			}
		});
		// now we take first (max votes) 
		// we want the solution with the maximum vote count. But in case of overlap there could be multiple solutions with maximum vote count
		// from within those we can't do much to select one (thery are all equivalent consensus-wise) 
		// We will simply return the first, hopefully this is not totally random (since we use an ArrayList 
		// then they are first ordered as found and when we sort above, hopefully the sort algorithm behaves in the same way for 
		// tie breaks of inputs in the same order. TODO is there something better we can do here? it doesn't seem very important 
		// anyway. Overlap cases should be rare)
		return allConsIntervals.get(0);
	}
	
	/**
	 * Finds all candidates of 2-dimensional consensus (by 2-dimensional we mean 
	 * in phi/psi angle space, i.e. in both phi and psi) by taking all candidates 
	 * in 1st dimension (phi) and looking if consensus also holds in second 
	 * dimension (psi).  
	 * 
	 * @param values1stDim
	 * @param values2ndDim
	 * @param voteThreshold
	 * @param angleInterval
	 * @return
	 */
	private ArrayList<ConsensusSquare> findAllConsSquares(HashMap<Integer,Double> values1stDim, HashMap<Integer,Double> values2ndDim, int voteThreshold, int angleInterval) {
		ArrayList<ConsensusSquare> consInterval2DCandidates = new ArrayList<ConsensusSquare>();
		
		ArrayList<ConsensusInterval> consIntervals1stDim = findAllConsInterval1stDim(values1stDim, voteThreshold, angleInterval);
		for (ConsensusInterval consInterv1stDim: consIntervals1stDim) {
			ConsensusInterval consInterv2ndDim = findConsInterval2ndDim(values2ndDim, consInterv1stDim, angleInterval);
			if (consInterv2ndDim!=null) {
				// ok this is a 2D interval candidate, we put it in the ArrayList. There simply shouldn't be duplicates
				consInterval2DCandidates.add(new ConsensusSquare(consInterv1stDim, consInterv2ndDim));
			}
		}
		return consInterval2DCandidates;
	}

	/**
	 * Finds all candidates of consensus in the 1st dimension (in phi/psi angle 
	 * space, i.e. the phi angles)
	 * This is the core of the algorithm. We sort phi angle values and then put 
	 * them together one by one and see if they fit within the angleInterval window 
	 * and if the consensus is above the voteThreshold. If both this conditions are 
	 * fullfilled then it is considered a candidate and added to the final output ArrayList
	 * @param anglesInColumn
	 * @param voteThreshold
	 * @param angleInterval
	 * @return
	 */
	private ArrayList<ConsensusInterval> findAllConsInterval1stDim(HashMap<Integer,Double> anglesInColumn, int voteThreshold, int angleInterval) {
		
		ArrayList<ConsensusInterval> allConsInterv1stDim = new ArrayList<ConsensusInterval>();
		
		// anglesInColumn: keys index j corresponding to templates order, values angles unsorted
		// we want the angles sorted so that we can then find intervals easily, but at the same time we 
		// want to keep the j indices. This is exactly what Goodies.sortMapByValue does
		LinkedHashMap<Integer, Double> anglesSorted = Goodies.sortMapByValue(anglesInColumn, Goodies.ASCENDING);
		ArrayList<Double> values = new ArrayList<Double>(anglesSorted.values());
		ArrayList<Integer> jIndices = new ArrayList<Integer>(anglesSorted.keySet());
		for (int startIndex=0;startIndex<values.size()-1;startIndex++) {
			ConsensusInterval consInterv = new ConsensusInterval();
			consInterv.addVoter(jIndices.get(startIndex), templates.get(jIndices.get(startIndex)).getId(), values.get(startIndex));
			for (int endIndex=startIndex+1;endIndex<values.size();endIndex++) {
				if ((values.get(endIndex) - values.get(startIndex))<=angleInterval) {
					consInterv.addVoter(jIndices.get(endIndex), templates.get(jIndices.get(endIndex)).getId(), values.get(endIndex));
				}
			}
			if (consInterv.getVoteCount()>voteThreshold) {  // note we use strictly bigger, see comment in getConsensusPhiPsi
				consInterv.reCenterInterval(angleInterval);
				allConsInterv1stDim.add(consInterv);
			}
		}
		return allConsInterv1stDim;
	}

	/**
	 * Gets the ConsensusInterval for the given list of 2nd dimension values (psi angles) for a 
	 * column in the alignment and the given consInterva1stDim (the phi consensus interval).
	 * We get each of the 2nd dimension (psi) values corresponding to each 1st dimension (phi) 
	 * voted template, if they are within a angleInterval window then they are taken as our 
	 * ConsensusInterval (recentering first the interval at max-min/2)
	 * 
	 * @param anglesInColumn
	 * @param consInterv1stDim
	 * @param angleInterval 
	 * @return the ConsensusInterval or null if the psi values don't fit within an angleInterval window
	 */
	private ConsensusInterval findConsInterval2ndDim(HashMap<Integer,Double> anglesInColumn, ConsensusInterval consInterv1stDim, int angleInterval) {
		
		// we use a values2ndDim ArrayList to put the 2nd dimension (psi) values corresponding to the given 1st dimension (phi) voters 
		ArrayList<Double> values2ndDim = new ArrayList<Double>();
		for (int voterIndex:consInterv1stDim.getVotersIndices()) {
			values2ndDim.add(anglesInColumn.get(voterIndex));
		}
		// now values2ndDim contains one value for each of the 1st dimension voters. We want to see if they fall within the angleInterval cutoff 
		double intervWidth = Collections.max(values2ndDim) - Collections.min(values2ndDim);
		if (intervWidth<=angleInterval) {
			// first we initialise with a dummy 0,0 interval, then we use the recenter method to get the interval
			// ATTENTION! we pass references of voters and voterIndices from consInterv1stDim: that means that the 2 members of 
			// the final ConsensusSquare object will be referencing the same objects
			ConsensusInterval consInterv2ndDim = 
				new ConsensusInterval(0, 0, 
						consInterv1stDim.getVoters(), 
						values2ndDim, 
						consInterv1stDim.getVotersIndices(), 
						consInterv1stDim.getVoteCount());
			consInterv2ndDim.reCenterInterval(angleInterval);
			return consInterv2ndDim;
		}
		return null;
	}
	
	/**
	 * Internal class to hold the Template together with its phi/psi angles. 
	 */
	private class TemplateWithPhiPsi extends Template {
		private TreeMap<Integer,double[]> phipsiAngles;
		public TemplateWithPhiPsi (String id, Pdb pdb, TreeMap<Integer,double[]> phipsiAngles) {
			super(id);
			this.phipsiAngles = phipsiAngles;
			this.setPdb(pdb);
		}
		
		public double getPhiAngle(int resser) {
			return phipsiAngles.get(resser)[0];
		}
		public double getPsiAngle(int resser) {
			return phipsiAngles.get(resser)[1];
		}
		public boolean hasPhiPsiAngles(int resser) {
			return phipsiAngles.containsKey(resser);
		}
	}
	
	
	/**
	 * Gets the phi/psi angles for all templates putting them into this.templates.
	 * Checks first that all template have PDB data removing the ones that don't
	 */
	private void getPhiPsi(TemplateList templates) {
		this.templates = new ArrayList<TemplateWithPhiPsi>();
		for (Template template: templates) {
			if (template.hasPdbData()) {
				this.templates.add(new TemplateWithPhiPsi(template.getId(), template.getPdb(), template.getPdb().getAllPhiPsi()));
			}
		}
		if (this.templates.size()<templates.size()) {
			System.out.println("Using only "+this.templates.size()+" templates for psi/phi averaging, out of given "+templates.size());
		}
 	}
	
	/**
	 * Checks that tags and sequences are consistent between this.al and this.templates 
	 *
	 */
	private void checkSequences(TemplateList templates){
		for (Template template :templates){
			if (!al.hasTag(template.getId())){
				System.err.println("Alignment is missing template sequence "+template.getId()+", check the FASTA tags");
				// TODO throw exception
			}
		}
		//if (templates.size()!=al.getNumberOfSequences()-1){
		//	System.err.println("Number of sequences in alignment is different from number of templates +1 ");
		//	// TODO throw exception
		//}

		for (Template template:templates){
			if(!al.getSequenceNoGaps(template.getId()).equals(template.getPdb().getSequence())) {
				System.err.println("Sequence of template (from pdbase) "+template.getId()+" does not match sequence in alignment");
				System.err.println("Trying to align sequences of alignment vs pdbase sequence: ");
				try {
					PairwiseSequenceAlignment alCheck = new PairwiseSequenceAlignment(template.getPdb().getSequence(),al.getSequenceNoGaps(template.getId()),"pdbase","alignment");
					alCheck.printAlignment();
				} catch (PairwiseSequenceAlignmentException e) {
					System.err.println("Error while creating alignment check, can't display an alignment. The 2 sequences are: ");
					System.err.println("graph:     "+template.getPdb().getSequence());
					System.err.println("alignment: "+al.getSequenceNoGaps(template.getId()));
				}
				// TODO throw exception
			}			
		}
	}
	
	/**
	 * To test the class
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		MySQLConnection conn = new MySQLConnection("white","duarte","nieve");
		
		File templatesFile = new File("/scratch/local/phipsi/T0332.templates");
		File alnFile = new File("/scratch/local/phipsi/T0332.target2templ.fasta");
		File psipredFile = new File("/scratch/local/phipsi/T0332.horiz");
		
		String pdbaseDb = "pdbase";
		TemplateList templates = new TemplateList(templatesFile);
		//Template temp1 = new Template("2f8aA");
		//Template temp2 = new Template("1gp1A");
		//Template temp3 = new Template("2gs3A");
		
		//TemplateList templates = new TemplateList();
		//templates.add(temp1);
		//templates.add(temp2);
		//templates.add(temp3);
		templates.loadPdbData(conn, pdbaseDb);
		
		Alignment aln = new Alignment(alnFile.getAbsolutePath(),"FASTA");
		System.out.println("Secondary structure matching: ");
		aln.writeSecStructMatching(System.out, "T0332", psipredFile , conn, pdbaseDb, "/project/StruPPi/bin/dssp");
		System.out.println();
		System.out.println("phi/psi angles of each sequence in alignment. Legend: line1 aln positions, line2 sequence positions, line3 phi, line4 psi");
		PhiPsiAverager ppAvrg = new PhiPsiAverager(templates,aln);
		
		TreeMap<Integer, ConsensusSquare> phipsibounds = ppAvrg.getConsensusPhiPsi(0.5, 20);
		
		// printing phi/psi angles in each of the templates		
		for (TemplateWithPhiPsi template:ppAvrg.templates) {
			TreeMap<Integer,double[]> phipsi = template.phipsiAngles;
			//String sequence = template.getPdb().getSequence();
			System.out.println(template.getId());
			// printing alignment positions
			for (int i=1; i<=aln.getAlignmentLength(); i++) {
				System.out.printf("%5d",i);
			}
			System.out.println();
			// printing residue serials
			for (int i=1; i<=aln.getAlignmentLength(); i++) {
				int resser = aln.al2seq(template.getId(), i);
				if (resser!=-1 && template.hasPhiPsiAngles(resser))
					System.out.printf("%5s",resser);
				else System.out.printf("%5s","");
			}
			System.out.println();
			// printing phis
			for (int i=1; i<=aln.getAlignmentLength(); i++) {
				int resser = aln.al2seq(template.getId(), i);
				if (resser!=-1 && template.hasPhiPsiAngles(resser))
					System.out.printf("%5.0f", phipsi.get(resser)[0]);
				else System.out.printf("%5s","");
			}
			System.out.println();
			// printing psis
			for (int i=1; i<=aln.getAlignmentLength(); i++) {
				int resser = aln.al2seq(template.getId(), i);
				if (resser!=-1 && template.hasPhiPsiAngles(resser))
					System.out.printf("%5.0f", phipsi.get(resser)[1]);
				else System.out.printf("%5s","");
			}
			System.out.println();
		}
		
		// printing consensus intervals
		System.out.println("Alignment positions that have phi/psi consensus.");
		System.out.println("Legend: col1= aln pos, columns before #: phi interval begin, phi interval end, phi values of members. Same for columns after # but for psi");
		for (int alnPos: phipsibounds.keySet()) {
			System.out.printf("%3d %4d %4d ",
					alnPos,
					phipsibounds.get(alnPos).getConsInterval1stDim().beg,
					phipsibounds.get(alnPos).getConsInterval1stDim().end);
			for (double val:phipsibounds.get(alnPos).getConsInterval1stDim().getValues()) {
				System.out.printf("%4.0f ",val);	
			}
			System.out.printf("# %4d %4d ",
					phipsibounds.get(alnPos).getConsInterval2ndDim().beg, 
					phipsibounds.get(alnPos).getConsInterval2ndDim().end);
			for (double val:phipsibounds.get(alnPos).getConsInterval2ndDim().getValues()) {
				System.out.printf("%4.0f ",val);	
			}
			System.out.println();
		}
		
	}
}