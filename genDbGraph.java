import gnu.getopt.Getopt;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;


import proteinstructure.Graph;
import proteinstructure.Pdb;
import proteinstructure.PdbChainCodeNotFoundError;
import proteinstructure.PdbCodeNotFoundError;
import proteinstructure.PdbaseInconsistencyError;
import proteinstructure.PdbasePdb;
import proteinstructure.PdbfileFormatError;
import proteinstructure.PdbfilePdb;
import tools.MySQLConnection;


public class genDbGraph {
	/*------------------------------ constants ------------------------------*/
	
	public static final String			PDB_DB = "pdbase";
	public static final String			DB_HOST = "white";								
	public static final String			DB_USER = getUserName();
	public static final String			DB_PWD = "nieve";
	public static final String			DSSP_EXE = "/project/StruPPi/bin/dssp";
	public static final String			DSSP_PARAMS = "--";

	//public static double			cutoff = 4.2;
	//public static String			edgeType = "ALL";
	
	/*---------------------------- private methods --------------------------*/
	/** 
	 * Get user name from operating system (for use as database username). 
	 * */
	private static String getUserName() {
		String user = null;
		user = System.getProperty("user.name");
		if(user == null) {
			System.err.println("Could not get user name from operating system.");
		}
		return user;
	}
	
	public static void main(String[] args) throws IOException {
		
		
		String help = "Usage, 3 options:\n" +
				"1)  genDbGraph -i <listfile> -d <distance_cutoff> -t <contact_type> -o <output_db> [-D <pdbase_db>] \n" +
				"2)  genDbGraph -p <pdb_code> -c <chain_pdb_code> -d <distance_cutoff> -t <contact_type> -o <output_db> [-D <pdbase_db>] \n" +
				"3)  genDbGraph -f <pdbfile> -c <chain_pdb_code> -d <distance_cutoff> -t <contact_type> -o <output_db> \n" +
				"In case 2) also a list of comma separated pdb codes and chain codes can be specified, e.g. -p 1bxy,1jos -c A,A\n" +
				"If pdbase_db not specified, the default pdbase will be used\n"; 

		String listfile = "";
		String[] pdbCodes = null;
		String[] pdbChainCodes = null;
		String pdbfile = "";
		String pdbaseDb = PDB_DB;
		String edgeType = "";
		double cutoff = 0.0;
		String outputDb = "";
		
		Getopt g = new Getopt("genDbGraph", args, "i:p:c:f:d:t:o:D:h?");
		int c;
		while ((c = g.getopt()) != -1) {
			switch(c){
			case 'i':
				listfile = g.getOptarg();
				break;
			case 'p':
				pdbCodes = g.getOptarg().split(",");
				break;
			case 'c':
				pdbChainCodes = g.getOptarg().split(",");
				break;
			case 'f':
				pdbfile = g.getOptarg();
				break;
			case 'd':
				cutoff = Double.valueOf(g.getOptarg());
				break;
			case 't':
				edgeType = g.getOptarg();
				break;
			case 'o':
				outputDb = g.getOptarg();
				break;
			case 'D':
				pdbaseDb = g.getOptarg();
				break;
			case 'h':
			case '?':
				System.out.println(help);
				System.exit(0);
				break; // getopt() already printed an error
			}
		}

		if (outputDb.equals("") || edgeType.equals("") || cutoff==0.0) {
			System.err.println("Some missing option");
			System.err.println(help);
			System.exit(1);
		}
		if (listfile.equals("") && pdbCodes==null && pdbfile.equals("")){
			System.err.println("Either a listfile, some pdb codes/chain codes or a pdbfile must be given");
			System.err.println(help);
			System.exit(1);
		}
		if ((!listfile.equals("") && pdbCodes!=null) || (!listfile.equals("") && !pdbfile.equals("")) || (pdbCodes!=null && !pdbfile.equals(""))) {
			System.err.println("Options -p/-c, -i and -f/-c are exclusive. Use only one of them");
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
		
		
		if (pdbfile.equals("")){
			
			if (!listfile.equals("")) {			
				BufferedReader fpdb = new BufferedReader(new FileReader(listfile));
				String line = "";
				int numLines = 0;
				fpdb.mark(100000);
				while ((line = fpdb.readLine() ) != null ) {
					numLines++;
				}
				fpdb.reset();
				pdbCodes = new String[numLines];
				pdbChainCodes = new String[numLines];
				numLines = 0;
				while ((line = fpdb.readLine() ) != null ) {
					pdbCodes[numLines] = line.split("\\s")[0].toLowerCase();
					pdbChainCodes[numLines] = line.split("\\s")[1];
					numLines++;
				}
			}

			int numPdbs = 0;

			for (int i=0;i<pdbCodes.length;i++) {
				String pdbCode = pdbCodes[i];
				String pdbChainCode = pdbChainCodes[i];

				if(pdbChainCode == null) {
					pdbChainCode = "NULL";
				}

				
				try {
					Pdb pdb = new PdbasePdb(pdbCode, pdbChainCode, pdbaseDb, conn);

					// get graph
					Graph graph = pdb.get_graph(edgeType, cutoff);

					graph.write_graph_to_db(conn,outputDb);

					System.out.println(pdbCode+"_"+pdbChainCode);
					numPdbs++;
					
				} catch (PdbaseInconsistencyError e) {
					System.err.println("Inconsistency in " + pdbCode + pdbChainCode);
				} catch (PdbCodeNotFoundError e) {
					System.err.println("Couldn't find pdb code "+pdbCode);
				} catch (SQLException e) {
					e.printStackTrace();
				} catch (PdbChainCodeNotFoundError e) {
					System.err.println("Couldn't find pdb chain code "+pdbChainCode+" for pdb code "+pdbCode);
				}

			}

			// output results
			System.out.println("Number of structures loaded successfully: " + numPdbs);


		} else {
			String pdbChainCode = pdbChainCodes[0];
			try {
				Pdb pdb = new PdbfilePdb(pdbfile,pdbChainCode);
				if (!pdb.hasSecondaryStructure()) {
					pdb.runDssp(DSSP_EXE, DSSP_PARAMS);
				}
				Graph graph = pdb.get_graph(edgeType, cutoff);
				try {
					graph.write_graph_to_db(conn, outputDb);
					System.out.println("Loaded to database graph for file "+pdbfile);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				
			} catch (PdbfileFormatError e) {
				System.err.println("pdb file "+pdbfile+" doesn't have right format");
			} catch (PdbChainCodeNotFoundError e) {
				System.err.println("chain code "+pdbChainCode+" wasn't found in file "+pdbfile);	
			}
		}
	}

}