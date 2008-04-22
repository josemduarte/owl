package sequence;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import org.apache.commons.collections15.Transformer;

import edu.uci.ics.jung.graph.SparseGraph;
import edu.uci.ics.jung.graph.util.Pair;

import proteinstructure.*;

/**
 * A collection of little tools related to Blast and Processing Blast output.
 * @author stehr
 *
 */
public class BlastUtils {

	private static final File tempDir = new File("/tmp/");
	private static final String maxClusterExecutable = "/project/StruPPi/bin/maxcluster";
	private static final double similarityGraphGdtCutoff = 50.0;
	
	class DoubleWrapper {
		public double val;	
		DoubleWrapper(double v) {
			this.val = v;
		}
	}
	
	/**
	 * Uses a perl script to render a PNG image of the given blast output file.
	 * @param blastOutputFile
	 * @param imgFile
	 */
	public static void renderBlast(File blastOutputFile, File imgFile) throws IOException {
		String renderScript = "/project/StruPPi/CASP8/scripts/render_blast.pl";
		String cmdLine = String.format("%s %s > %s", renderScript, blastOutputFile.getAbsolutePath(), imgFile.getAbsolutePath());
		Runtime.getRuntime().exec(cmdLine);
	}
	
	/**
	 * Helper function used by writeClusterGraph writing a similarity matrix to a file.
	 * @param sparseMatrix
	 * @param labels
	 * @param outFile
	 */
	private static void writeMatrixToFile(HashMap<Pair<Integer>, Double> sparseMatrix, String[] labels, File outFile) throws IOException {
				
		// initialize temp matrix
		int mSize = labels.length;	// assuming matrix size equals number of labels
		System.out.println(mSize);
		double[][] tmpMatrix = new double[mSize][mSize];
		for (int i = 0; i < tmpMatrix.length; i++) {
			tmpMatrix[i][i] = 100.0;
		}
		for(Pair<Integer> pair:sparseMatrix.keySet()) {
			int i = pair.getFirst() - 1;
			int j = pair.getSecond() - 1;
			if(i >= mSize || j >= mSize) {
				System.err.printf("Error: Entry (%d,%d) in matrix exceeds number of labels (%d).", i+1,j+1,mSize);
			} else {
				if(sparseMatrix.containsKey(pair)) {
					tmpMatrix[i][j] = sparseMatrix.get(pair);
					tmpMatrix[j][i] = sparseMatrix.get(pair);
				}
			}
		}
		
		// write matrix to file
		PrintWriter out = new PrintWriter(outFile);
		String sep = "\t";
		for(String label:labels) {
			out.print(sep + label);
		}
		out.println();
		for (int i = 0; i < tmpMatrix.length; i++) {
			out.print(labels[i]);
			for (int j = 0; j < tmpMatrix.length; j++) {
				out.print(sep + tmpMatrix[i][j]);
			}
			out.println();
		}
		out.close();
	}
	
	/**
	 * Calculates a similarity matrix for a set of blast hits and outputs a graph overview for visual inspection.
	 * Now also writes the similarity matrix to a file (to be used by R script).
	 */
	public static void writeClusterGraph(TemplateList templates, File graphFile, File matrixFile) throws IOException {
		if (templates.size()==0) return;
		
		String listFileName = "listfile";
		File listFile = new File(tempDir, listFileName);
		listFile.deleteOnExit();
		PrintWriter out = new PrintWriter(listFile);
		
		// create list file
		Iterator<Template> it = templates.iterator();
		while(it.hasNext()) {
			Template template = it.next();
			// extract pdb and chain code
			
			String pdbCode = template.getId().substring(0, 4);
			String chain = template.getId().substring(4);
			File pdbFile = new File(tempDir, pdbCode + chain + ".pdb");
			pdbFile.deleteOnExit();
			
			if(template.hasStructure()) {
				
				// write to file
				template.getPdb().dump2pdbfile(pdbFile.getAbsolutePath());
				
				// add to listfile
				out.println(pdbFile.getAbsolutePath());				
		}
		out.close();
		
		// run maxcluster
		MaxClusterRunner mcr = new MaxClusterRunner(maxClusterExecutable);
		HashMap<Pair<Integer>, Double> matrix = mcr.calculateSequenceIndependentMatrix(listFile.getAbsolutePath(), MaxClusterRunner.ScoreType.GDT);

		// write similarity matrix file
		String[] templateIds = templates.getIds();
		writeMatrixToFile(matrix, templateIds, matrixFile);
		
		// generate graph from similarity matrix
		SparseGraph<String, DoubleWrapper> simGraph = new SparseGraph<String, DoubleWrapper>();
		// write nodes
		for(String id:templateIds) {
			simGraph.addVertex(id);
		}
		
		// write edges
		for(Pair<Integer> edge:matrix.keySet()) {
			String start = templateIds[edge.getFirst()-1];
			String end = templateIds[edge.getSecond()-1];
			double weight = matrix.get(edge);
			//System.out.println(weight);
			if(weight > similarityGraphGdtCutoff) {
				simGraph.addEdge(new BlastUtils().new DoubleWrapper(weight), new Pair<String>(start, end));
			}
		}
		
		// write GDL file for aiSee
		GraphIOGDLFile<String, DoubleWrapper> gdlfileIO = new GraphIOGDLFile<String, DoubleWrapper>();
		gdlfileIO.writeGdlFile(simGraph, graphFile.getAbsolutePath(),
				new Transformer<String, Integer>() {public Integer transform(String s) {return s.hashCode();} },
				new Transformer<String, String>()  {public String transform(String s) {return s;} }, 
				new Transformer<String, String>()  {public String transform(String s) {return "white";} }
				);
		}
	}
	
	/**
	 * Testing some of the methods in this class.
	 * @param args
	 */
	public static void main(String[] args) {
		File blastOutput = new File("");
		File imgFile = new File("");
		try {
			renderBlast(blastOutput, imgFile);
		} catch(IOException e) {
			System.out.println("RenderBlast failed: " + e.getMessage());
		}
	}
	
}
