package owl.decoyScoring;

import java.io.File;
import java.io.IOException;

import owl.core.structure.Pdb;
import owl.core.structure.graphs.RIGraph;
import owl.core.util.FileFormatException;


public class ResCombinedScorer extends Scorer {
	private ResCountScorer countScorer;
	private ResTypeScorer typeScorer;
	
	private double countWeight;
	private double typeWeight;

	public ResCombinedScorer(File scMatType, File scMatCount, double typeWeight, double countWeight) throws IOException, FileFormatException {
		

		this.typeScorer = new ResTypeScorer(scMatType);
		this.countScorer = new ResCountScorer(scMatCount);

		this.typeWeight = typeWeight;
		this.countWeight = countWeight;
		
		this.scoringMethod = ScoringMethod.RESCOMBINED;
		
		if (!typeScorer.getContactType().equals(countScorer.getContactType())) {
			throw new IllegalArgumentException("Count scorer and type scorer are based on different contact types");
		}
		this.ct = typeScorer.getContactType();
		if (typeScorer.getCutoff()!=countScorer.getCutoff()) {
			throw new IllegalArgumentException("Count scorer and type scorer are based on different cutoffs");
		}
		this.cutoff = typeScorer.getCutoff();
		
		if (!typeScorer.getListFile().equals(countScorer.getListFile())) {
			throw new IllegalArgumentException("Count scorer and type scorer are based on different training set files");
		}
		this.listFile = typeScorer.getListFile();
		if (typeScorer.sizeOfTrainingSet()!=countScorer.sizeOfTrainingSet()) {
			System.err.println("Warning: count scorer and type scorer are based on training sets of different sizes " +
					"(type: "+typeScorer.sizeOfTrainingSet()+", count: "+countScorer.sizeOfTrainingSet()+"). Using max of both for CombinedScorer");
		}
		this.totalStructures = Math.max(typeScorer.sizeOfTrainingSet(),countScorer.sizeOfTrainingSet());
		
		this.minSeqSep = -1; // this shouldn't be used at all in CombinedScorer (the minSeqSep of the type or count scorers should be taken)
	}

	@Override
	public double scoreIt(Pdb pdb) {
		RIGraph graph = pdb.getRIGraph(this.ct, this.cutoff);
		int typeMinSeqSep = typeScorer.getMinSeqSep();
		int countMinSeqSep = countScorer.getMinSeqSep();
		
		double countScore = 0;
		double typeScore = 0;
		
		if (typeMinSeqSep<countMinSeqSep) {
			graph.restrictContactsToMinRange(typeMinSeqSep);
			typeScore = typeScorer.scoreIt(graph);
			graph.restrictContactsToMinRange(countMinSeqSep);
			countScore = countScorer.scoreIt(graph);	
		} else {
			graph.restrictContactsToMinRange(countMinSeqSep);
			countScore = countScorer.scoreIt(graph);				
			graph.restrictContactsToMinRange(typeMinSeqSep);
			typeScore = typeScorer.scoreIt(graph);
		}
		
		return countWeight*countScore + typeWeight*typeScore;
	}

}
