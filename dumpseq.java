import gnu.getopt.Getopt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;

import proteinstructure.Pdb;
import proteinstructure.PdbCodeNotFoundError;
import proteinstructure.PdbLoadError;
import proteinstructure.PdbasePdb;
import proteinstructure.TemplateList;
import tools.MySQLConnection;


public class dumpseq {
	/*------------------------------ constants ------------------------------*/
	
	public static final String			PDB_DB = "pdbase";
	public static final String			DB_HOST = "white";								
	public static final String			DB_USER = MySQLConnection.getUserName();
	public static final String			DB_PWD = "nieve";

	public static final String			GAP_CHARACTER = "-";
	
	public static void main(String[] args) throws IOException {
		
		String progName = "dumpseq";
		
		String help = "Usage, 2 options:\n" +
				"1)  "+progName+" -i <listfile> [-o <output_dir> | -f <one_output_file> | -s] [-N] [-D <pdbase_db>] \n" +
				"2)  "+progName+" -p <pdb_code+chain_code> [-o <output_dir> | -f <one_output_file> | -s] [-N] [-D <pdbase_db>] \n\n" +
				"Output options: -o one file per sequence, -f one file for all sequences, \n" +
				"-s standard output. With -N no FASTA headers will be written\n\n"+
				"In case 2) also a list of comma separated pdb code+chain codes can be \n" +
				"specified, e.g. -p 1bxyA,1josA \n\n" +
				"If pdbase_db not specified, the default pdbase will be used\n"; 

		String listfile = "";
		String[] pdbIds = null;
		String pdbaseDb = PDB_DB;
		String outputDir = "";
		File oneOutputFile = null;
		boolean stdout = false;
		boolean fastaHeader = true;
		
		Getopt g = new Getopt(progName, args, "i:p:o:f:D:sNh?");
		int c;
		while ((c = g.getopt()) != -1) {
			switch(c){
			case 'i':
				listfile = g.getOptarg();
				break;
			case 'p':
				pdbIds = g.getOptarg().split(",");
				break;
			case 'o':
				outputDir = g.getOptarg();
				break;
			case 'f':
				oneOutputFile = new File(g.getOptarg());
				break;				
			case 'D':
				pdbaseDb = g.getOptarg();
				break;
			case 's':
				stdout = true;
				break;
			case 'N':
				fastaHeader = false;
				break;								
			case 'h':
			case '?':
				System.out.println(help);
				System.exit(0);
				break; // getopt() already printed an error
			}
		}

		if (listfile.equals("") && pdbIds==null){
			System.err.println("Either a listfile or some pdb codes/chain codes must be given");
			System.err.println(help);
			System.exit(1);
		}
		if (!listfile.equals("") && pdbIds!=null) {
			System.err.println("Options -p and -i are exclusive. Use only one of them");
			System.err.println(help);
			System.exit(1);			
		}
		if (outputDir.equals("") && stdout==false && oneOutputFile==null) {
			System.err.println("An output option was missing: choose one of -o, -f or -s");
			System.err.println(help);
			System.exit(1);
		}
		
		MySQLConnection conn = null;		

		try{
			conn = new MySQLConnection(DB_HOST, DB_USER, DB_PWD);
		} catch (Exception e) {
			System.err.println("Error opening database connection. Exiting");
			System.exit(1);
		}


		if (!listfile.equals("")) {	
			pdbIds = TemplateList.readIdsListFile(new File(listfile));
		}

		int numPdbs = 0;

		PrintStream Out = null;
		if (stdout) {
			Out = System.out;
		} else if (oneOutputFile!=null) {
			Out = new PrintStream(new FileOutputStream(oneOutputFile));
		} 

		for (int i=0;i<pdbIds.length;i++) {
			String pdbCode = pdbIds[i].substring(0, 4);
			String pdbChainCode = pdbIds[i].substring(4);

			try {

				Pdb pdb = new PdbasePdb(pdbCode, pdbaseDb, conn);
				pdb.load(pdbChainCode);
				
				String sequence = pdb.getSequence();

				File outputFile = new File(outputDir,pdbCode+pdbChainCode+".fasta");
				
				if (!stdout && oneOutputFile==null) {
					Out = new PrintStream(new FileOutputStream(outputFile.getAbsolutePath()));
				}
				
				if (fastaHeader) { 
					Out.println(">"+pdbCode+pdbChainCode);
				}
				
				Out.println(sequence);

				if (!stdout && oneOutputFile==null) {
					Out.close();
				}
				
				if (!stdout) { // if output of sequence is stdout, then we don't want to print anything else to stdout
					System.out.println("Wrote "+pdbCode+pdbChainCode+".fasta");
				}

				numPdbs++;

			} catch (PdbLoadError e) {
				System.err.println("Error loading pdb data for " + pdbCode + pdbChainCode+", specific error: "+e.getMessage());
			} catch (PdbCodeNotFoundError e) {
				System.err.println("Couldn't find pdb code "+pdbCode);
			} catch (SQLException e) {
				System.err.println("SQL error for structure "+pdbCode+pdbChainCode+", error: "+e.getMessage());
			}

		}

		if (!stdout && oneOutputFile!=null) {
			Out.close();
		}
		
		// output results
		if (!stdout) { // if output of sequence is stdout, then we don't want to print anything else to stdout
			System.out.println("Number of dumped sequences: " + numPdbs);
		}


	} 
		

}
