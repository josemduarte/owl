package proteinstructure;

import gnu.getopt.Getopt;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;

import tools.MySQLConnection;

public class ResTypeScorer extends TypeScorer {
	
	private static final File DEFAULT_LIST_FILE = new File("/project/StruPPi/jose/emp_potential/cullpdb_pc20_res1.6_R0.25_d090728_chains1627.list");
	private static final double DEFAULT_CUTOFF = 8.0;
	private static final int DEFAULT_MIN_SEQ_SEP = 3;
	private static final String DEFAULT_CT = "Cb";

	private static final int NUM_RES_TYPES = 20;
	

	/**
	 * Constructs a ResTypeScorer by taking a list of structure ids (pdbCodes+pdbChainCodes),
	 * and parameters contact type, distance cutoff and minimum sequence separation used for 
	 * calculating the scoring matrix. 
	 * @param structureIds the list of PDB ids (pdbCode+pdbChainCode)
	 * @param ct the contact type to be used as definition of contacts
	 * @param cutoff the distance cutoff to be used as definition of contacts
	 * @param minSeqSep the minimum sequence separation to be used when counting type pairs
	 * @throws SQLException if can't establish connection to db server
	 */
	public ResTypeScorer(String[] structureIds, String ct, double cutoff, int minSeqSep) throws SQLException {
		
		this.structureIds = structureIds;
		this.ct = ct;
		this.cutoff = cutoff;
		this.minSeqSep = minSeqSep;
		
		this.numEntities = NUM_RES_TYPES;
		entityCounts = new int[numEntities];
		pairCounts = new int[numEntities][numEntities];
		totalStructures = 0;
		
		this.conn = new MySQLConnection();
		
	}

	/**
	 * Constructs a ResTypeScorer given a file with a scoring matrix. All values of the matrix 
	 * and some other necessary fields will be read from the file.
	 * @param scMatFile
	 * @throws IOException
	 * @throws FileFormatError
	 */
	public ResTypeScorer(File scMatFile) throws IOException, FileFormatError  {
		readFromFile(scMatFile);
	}

	
	private void initResMap() {
		types2indices = new HashMap<String, Integer>();
		int i=0;
		for (String resType:AAinfo.getAAs()) {
			types2indices.put(resType,i);
			i++;
		}
		indices2types = new HashMap<Integer, String>();
		for (String resType:types2indices.keySet()) {
			indices2types.put(types2indices.get(resType), resType);
		}
	}

	/**
	 * Performs the counts of the residue type pairs and stores them in the internal arrays.
	 * Use subsequently {@link #calcScoringMat()} to compute the scoring matrix from counts arrays.
	 * @throws SQLException
	 */
	public void countResTypes() throws SQLException {
		this.initResMap();

		for (String id:structureIds) {
			String pdbCode = id.substring(0,4);
			String pdbChainCode = id.substring(4,5);
			Pdb pdb = null;
			try {
				pdb = new PdbasePdb(pdbCode,DB,conn);
				pdb.load(pdbChainCode);
				if (!isValidPdb(pdb)) {
					System.err.println(id+" didn't pass the quality checks to be included in training set");
					continue;
				}
				System.out.println(id);
				totalStructures++;
			} catch (PdbCodeNotFoundError e) {
				System.err.println("Couldn't find pdb "+pdbCode);
				continue;
			} catch (PdbLoadError e) {
				System.err.println("Couldn't load pdb "+pdbCode);
				continue;
			}

			RIGraph graph = pdb.get_graph(ct, cutoff);
			graph.restrictContactsToMinRange(minSeqSep);
			for (RIGNode node:graph.getVertices()) {
				countEntity(types2indices.get(node.getResidueType()));
			}
			for (RIGEdge edge:graph.getEdges()) {
				String iRes = graph.getEndpoints(edge).getFirst().getResidueType();
				String jRes = graph.getEndpoints(edge).getSecond().getResidueType();
				countPair(types2indices.get(iRes),types2indices.get(jRes));
			}
		}
	}
	
	@Override
	public double scoreIt(Pdb pdb, int minSeqSep) {
		RIGraph graph = pdb.get_graph(this.ct, this.cutoff);
		graph.restrictContactsToMinRange(minSeqSep);
		
		double totalScore = 0;
		for (RIGEdge edge:graph.getEdges()) {
			String iRes = graph.getEndpoints(edge).getFirst().getResidueType();
			String jRes = graph.getEndpoints(edge).getSecond().getResidueType();
			int i = types2indices.get(iRes);
			int j = types2indices.get(jRes);
			if (j>=i) {
				totalScore+= scoringMat[i][j];
			} else {
				totalScore+= scoringMat[j][i];
			}
		}
		
		return (totalScore/graph.getEdgeCount());
	}

	public static void main(String[] args) throws Exception {
		String help = 
			"\nCompiles a scoring matrix based on residue types from a given file with a list of \n" +
			"pdb structures.\n" +
			"Usage:\n" +
			"ResTypeScorer -o <output_matrix_file> [-l <list_file> -c <cutoff> -m <out>]\n"+
			"  -o <file>     : file to write the scoring matrix to\n" +
			"  -l <file>     : file with list of pdbCodes+pdbChainCodes to use as training set \n" +
			"                  the scoring matrix. Default is "+DEFAULT_LIST_FILE+"\n" +
			"  -t <string>   : contact type. Default: "+DEFAULT_CT+"\n"+
			"  -c <float>    : distance cutoff for the atom contacts. Default: "+DEFAULT_CUTOFF+"\n" +
			"  -m <int>      : minimum sequence separation to consider a contact. Default: "+DEFAULT_MIN_SEQ_SEP+"\n";

			File listFile = DEFAULT_LIST_FILE;
			File scMatFile = null;
			double cutoff = DEFAULT_CUTOFF;
			int minSeqSep = DEFAULT_MIN_SEQ_SEP;
			String ct = DEFAULT_CT;

			Getopt g = new Getopt("ResTypeScorer", args, "l:t:c:o:m:h?");
			int c;
			while ((c = g.getopt()) != -1) {
				switch(c){
				case 'l':
					listFile = new File(g.getOptarg());
					break;
				case 't':
					ct = g.getOptarg();
					break;									
				case 'c':
					cutoff = Double.parseDouble(g.getOptarg());
					break;				
				case 'o':
					scMatFile = new File(g.getOptarg());
					break;
				case 'm':
					minSeqSep = Integer.parseInt(g.getOptarg());
					break;				
				case 'h':
				case '?':
					System.out.println(help);
					System.exit(0);
					break; // getopt() already printed an error
				}
			}

			if (scMatFile==null) {
				System.err.println("Missing argument output matrix file (-o)");
				System.exit(1);
			}

		String[] ids = TemplateList.readIdsListFile(listFile);
		ResTypeScorer sc = new ResTypeScorer(ids,ct,cutoff,minSeqSep);
		sc.countResTypes();
		sc.calcScoringMat();
		sc.writeScMatToFile(scMatFile,false);
		
	}


}