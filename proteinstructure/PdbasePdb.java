package proteinstructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.vecmath.Point3d;

import tools.MySQLConnection;

/**
 * A single chain pdb protein structure loaded from a PDBASE database
 * See http://openmms.sdsc.edu/OpenMMS-1.5.1_Std/openmms/docs/guides/PDBase.html 
 * to know what PDBASE is
 * 
 */
public class PdbasePdb extends Pdb {

	private final static String MYSQLSERVER="white";
	private final static String MYSQLUSER=MySQLConnection.getUserName();
	private final static String MYSQLPWD="nieve";
	private final static String DEFAULT_PDBASE_DB="pdbase";

	private MySQLConnection conn;
	
	public enum ChainCodeType {PDB_CHAIN_CODE, CIF_CHAIN_CODE};
	
	private int entrykey;
	private String asymid;
	private int entitykey;
	private String alt_locs_sql_str;

	/**
	 * Constructs an empty Pdb object given pdb code
	 * Data will be loaded from database upon call of load(pdbChainCode, modelSerial)
	 * MySQLConnection is taken from defaults in PdbasePdb class: MYSQLSERVER, MYSQLUSER, MYSQLPWD
	 * Database is taken from default pdbase database in PdbasePdb class: DEFAULT_PDBASE_DB
	 * @param pdbCode
	 * @throws SQLException  
	 * @throws PdbCodeNotFoundError 
	 */
	public PdbasePdb (String pdbCode) throws SQLException, PdbCodeNotFoundError {
		this(pdbCode, DEFAULT_PDBASE_DB, new MySQLConnection(MYSQLSERVER,MYSQLUSER,MYSQLPWD));
	}

	/**
	 * Constructs an empty Pdb object given pdb code, source db and a MySQLConnection.
	 * Data will be loaded from database upon call of load(pdbChainCode, modelSerial)  
	 * @param pdbCode
	 * @param db a pdbase database
	 * @param conn
	 * @throws SQLException 
	 * @throws PdbCodeNotFoundError 
	 */
	public PdbasePdb (String pdbCode, String db, MySQLConnection conn) throws PdbCodeNotFoundError, SQLException  {
		this.pdbCode=pdbCode.toLowerCase();				// our convention: pdb codes are lower case
		this.db=db;
		this.dataLoaded = false;
		
		this.conn = conn;
		
		// this makes sure that we find the pdb code in the database
		this.entrykey = get_entry_key();
	}

	public void load(String pdbChainCode, int modelSerial) throws PdbLoadError {
		load(pdbChainCode, modelSerial, ChainCodeType.PDB_CHAIN_CODE);
	}
	
	/**
	 * Loads PDB data from pdbase given a chain code of type ccType.
	 * Two possible ccTypes: 
	 *  - PDB_CHAIN_CODE the classic, author-assigned pdb code (pdb_strand_id in pdbase): the pdbChainCode member of Pdb class
	 *  - CIF_CHAIN_CODE the cif chain code (asym_id in pdabase): the chainCode member of Pdb class
	 * @param chainCode
	 * @param modelSerial
	 * @param ccType
	 * @throws PdbLoadError
	 */
	public void load(String chainCode, int modelSerial, ChainCodeType ccType) throws PdbLoadError {
		try {
			this.model = modelSerial;
			if (ccType==ChainCodeType.PDB_CHAIN_CODE) {
				this.pdbChainCode=chainCode;	// NOTE! pdb chain code are case sensitive!
				this.asymid=get_asym_id();		
				this.chainCode = asymid;
			} else if (ccType==ChainCodeType.CIF_CHAIN_CODE) {
				this.chainCode = chainCode;
				this.asymid = chainCode;
				this.pdbChainCode = get_pdb_strand_id();
			}
			this.entitykey=get_entity_key();
			this.alt_locs_sql_str=get_atom_alt_locs();
			
			this.sequence = read_seq();
			this.fullLength = sequence.length();
			
			this.pdbresser2resser = get_ressers_mapping();
   
			this.read_atomData(); // populates resser_atom2atomserial, resser2restype, atomser2coord, atomser2resser

			this.obsLength = resser2restype.size();
			
			// we initialise resser2pdbresser from the pdbresser2resser HashMap
			this.resser2pdbresser = new HashMap<Integer, String>();
			for (String pdbresser:pdbresser2resser.keySet()){
				resser2pdbresser.put(pdbresser2resser.get(pdbresser), pdbresser);
			}
			
			secondaryStructure = new SecondaryStructure();	// create empty secondary structure first to make sure object is not null
			readSecStructure();
			if(!secondaryStructure.isEmpty()) {
				secondaryStructure.setComment("Pdbase");
			}
			
			// initialising atomser2atom from resser_atom2atomserial
			atomser2atom = new HashMap<Integer, String>();
			for (String resser_atom:resser_atom2atomserial.keySet()){
				int atomserial = resser_atom2atomserial.get(resser_atom);
				String atom = resser_atom.split("_")[1];
				atomser2atom.put(atomserial,atom);
			}
			
			dataLoaded = true;
			
		} catch (SQLException e) {
			throw new PdbLoadError(e);
		} catch (PdbChainCodeNotFoundError e) {
			throw new PdbLoadError(e);
		} catch (PdbaseInconsistencyError e) {
			throw new PdbLoadError(e);
		}

	}
	
	public String[] getChains()	throws PdbLoadError {
		TreeSet<String> chains = new TreeSet<String>();
		try {
			String sql = "SELECT DISTINCT pdb_strand_id FROM "+db+".pdbx_poly_seq_scheme WHERE entry_key="+entrykey;
			Statement stmt = conn.createStatement();
			ResultSet rsst = stmt.executeQuery(sql);
			while (rsst.next()) {
				chains.add(rsst.getString(1));
			}
			rsst.close();
			stmt.close();
		} catch (SQLException e) {
			throw new PdbLoadError(e);
		}
		
		if (chains.isEmpty()) return null;
		
		String[] chainsArray = new String[chains.size()];
		chains.toArray(chainsArray);
		return chainsArray;
	}
	
	public Integer[] getModels() throws PdbLoadError {
		TreeSet<Integer> models = new TreeSet<Integer>();
		try {
			String sql = "SELECT DISTINCT model_num FROM "+db+".atom_site WHERE entry_key="+entrykey;
			Statement stmt = conn.createStatement();
			ResultSet rsst = stmt.executeQuery(sql);
			while (rsst.next()) {
				models.add(rsst.getInt(1));
			}
			rsst.close();
			stmt.close();
	
		} catch (SQLException e) {
			throw new PdbLoadError(e);
		}
		
		if (models.isEmpty()) return null;		
		Integer[] modelsArray = new Integer[models.size()];
		models.toArray(modelsArray);
		return modelsArray;
	}
	
	private int get_entry_key() throws PdbCodeNotFoundError, SQLException {
		String sql="SELECT entry_key FROM "+db+".struct WHERE entry_id='"+pdbCode.toUpperCase()+"'";
		Statement stmt = conn.createStatement();
		ResultSet rsst = stmt.executeQuery(sql);
		if (rsst.next()) {
			entrykey = rsst.getInt(1);
			if (! rsst.isLast()) {
				//System.err.println("More than 1 entry_key match for accession_code="+pdbCode+", chain_pdb_code="+pdbChainCode);
				throw new PdbCodeNotFoundError("More than 1 entry_key match for accession_code="+pdbCode+", chain_pdb_code="+pdbChainCode);					
			}
		} else {
			//System.err.println("No entry_key match for accession_code="+pdbCode+", chain_pdb_code="+pdbChainCode);
			throw new PdbCodeNotFoundError("No entry_key match for accession_code="+pdbCode+", chain_pdb_code="+pdbChainCode);
		}
		rsst.close();
		stmt.close();
		return entrykey;
	}
	
	/**
	 * Gets the asym_id given a pdb_strand_id (from field pdbChainCode) and entry_key (from field entrykey)
	 * @return
	 * @throws PdbChainCodeNotFoundError
	 * @throws SQLException
	 */
	private String get_asym_id() throws PdbChainCodeNotFoundError, SQLException {
		String asymid = null;
		String pdbstrandid=pdbChainCode;
		if (pdbChainCode.equals(NULL_CHAIN_CODE)){
			pdbstrandid="A";
		}
		// NOTE: as pdbx_poly_seq_scheme contains a record per residue, this query returns many (identical) records
		// We use the 'LIMIT 1' simply to be able to easily catch the error of no matches with an if (rsst.next()), see below
		// NOTE2: pdb_strand_id case sensitive!
		String sql="SELECT asym_id " +
				" FROM "+db+".pdbx_poly_seq_scheme " +
				" WHERE entry_key=" + entrykey +
				" AND pdb_strand_id='"+pdbstrandid+"' " +
				" LIMIT 1";

		Statement stmt = conn.createStatement();
		ResultSet rsst = stmt.executeQuery(sql);
		if (rsst.next()) {
			asymid = rsst.getString(1);
		} else {
			//System.err.println("No asym_id match for entry_key="+entrykey+", pdb_strand_id="+pdbChainCode);
			throw new PdbChainCodeNotFoundError("No asym_id match for entry_key="+entrykey+", pdb_strand_id="+pdbChainCode);
		}
		rsst.close();
		stmt.close();

		return asymid;	
	}

	/**
	 * Gets the pdb_strand_id given an asym_id (from field asymid) and entry_key (from field entrykey)
	 * @return
	 * @throws PdbChainCodeNotFoundError
	 * @throws SQLException
	 */
	private String get_pdb_strand_id() throws PdbChainCodeNotFoundError, SQLException {
		String pdbstrandid = null;
		// NOTE: as pdbx_poly_seq_scheme contains a record per residue, this query returns many (identical) records
		// We use the 'LIMIT 1' simply to be able to easily catch the error of no matches with an if (rsst.next()), see below
		String sql="SELECT pdb_strand_id " +
				" FROM "+db+".pdbx_poly_seq_scheme " +
				" WHERE entry_key=" + entrykey +
				" AND asym_id='"+asymid+"' " +
				" LIMIT 1";

		Statement stmt = conn.createStatement();
		ResultSet rsst = stmt.executeQuery(sql);
		if (rsst.next()) {
			pdbstrandid = rsst.getString(1);
		} else {
			throw new PdbChainCodeNotFoundError("No pdb_strand_id match for entry_key="+entrykey+", asym_id="+asymid);
		}
		rsst.close();
		stmt.close();

		return pdbstrandid;	
	}
	
	// NOTE: Entity key not really needed since there can be only one entity_key
	// per entry_key,asym_id combination 
	private int get_entity_key() throws PdbaseInconsistencyError, SQLException {
		String sql="SELECT entity_key " +
				" FROM "+db+".struct_asym " +
				" WHERE entry_key="+ entrykey +
				" AND id='"+asymid+"'";

		Statement stmt = conn.createStatement();
		ResultSet rsst = stmt.executeQuery(sql);
		if (rsst.next()) {
			entitykey = rsst.getInt(1);
			if (! rsst.isLast()) {
				throw new PdbaseInconsistencyError("More than 1 entity_key match for entry_key="+entrykey+", asym_id="+asymid);					
			}
		} else {
			throw new PdbaseInconsistencyError("No entity_key match for entry_key="+entrykey+", asym_id="+asymid);
		}
		rsst.close();
		stmt.close();
		return entitykey;
	}
	
	private String get_atom_alt_locs() throws PdbaseInconsistencyError, SQLException{
		ArrayList<String> alt_ids = new ArrayList<String>();
		HashMap<String,Integer> alt_ids2keys = new HashMap<String,Integer>();
		String alt_loc_field="label_alt_key";
		String sql="SELECT id, atom_sites_alt_key FROM "+db+".atom_sites_alt WHERE entry_key="+entrykey;

		Statement stmt = conn.createStatement();
		ResultSet rsst = stmt.executeQuery(sql);
		int count=0;
		while (rsst.next()) {
			count++;
			alt_ids.add(rsst.getString(1));
			alt_ids2keys.put(rsst.getString(1), rsst.getInt(2));
		}
		if (count!=0){
			if ((! alt_ids.contains(".")) || alt_ids.indexOf(".")!=alt_ids.lastIndexOf(".")){ // second term is a way of finding out if there is more than 1 ocurrence of "." in the ArrayList 
				throw new PdbaseInconsistencyError("alt_codes exist for entry_key "+entrykey+" but there is either no default value '.' or more than 1 '.'. Something wrong with this entry_key or with "+DEFAULT_PDBASE_DB+" db!");
			}
			alt_ids.remove(".");
			Collections.sort(alt_ids);
			String lowest_alt_id = alt_ids.get(0);
			alt_locs_sql_str = "("+alt_loc_field+"="+alt_ids2keys.get(".")+" OR "+alt_loc_field+"="+alt_ids2keys.get(lowest_alt_id)+")";
		} else {
			alt_locs_sql_str=alt_loc_field+" IS NULL";
		} 

		rsst.close();
		stmt.close();

		return alt_locs_sql_str;
	}
	
	private void read_atomData() throws PdbaseInconsistencyError, SQLException{
		resser_atom2atomserial = new HashMap<String,Integer>();
		resser2restype = new HashMap<Integer,String>();
		atomser2coord = new HashMap<Integer,Point3d>();
		atomser2resser = new HashMap<Integer,Integer>();

		// NOTE: label_entity_key not really needed since there can be only one entity_key
		// per entry_key,asym_id combination
		String sql = "SELECT id, label_atom_id, label_comp_id, label_seq_id, Cartn_x, Cartn_y, Cartn_z " +
				" FROM "+db+".atom_site " +
				" WHERE entry_key="+entrykey +
				" AND label_asym_id='"+asymid+"' " +
				" AND label_entity_key="+ entitykey +
				" AND model_num="+ model +
				" AND "+alt_locs_sql_str;

		Statement stmt = conn.createStatement();
		ResultSet rsst = stmt.executeQuery(sql);
		int count=0;
		while (rsst.next()){
			count++;

			int atomserial = rsst.getInt(1); 			// atomserial
			String atom = rsst.getString(2).trim();  	// atom
			String res_type = rsst.getString(3).trim();	// res_type
			int res_serial = rsst.getInt(4);			// res_serial
			double x = rsst.getDouble(5);				// x
			double y = rsst.getDouble(6);				// y
			double z = rsst.getDouble(7);				// z
			Point3d coords = new Point3d(x, y, z);
			if (AAinfo.isValidAA(res_type)) {
				atomser2coord.put(atomserial, coords);
				atomser2resser.put(atomserial, res_serial);
				resser2restype.put(res_serial, res_type);
				if (AAinfo.isValidAtomWithOXT(res_type,atom)){
					resser_atom2atomserial.put(res_serial+"_"+atom, atomserial);
				}
			}

		}
		if (count==0){
			throw new PdbaseInconsistencyError("atom data query returned no data at all for entry_key="+entrykey+", asym_id="+asymid+", entity_key="+entitykey+", model_num="+model+", alt_locs_sql_str='"+alt_locs_sql_str+"'");
		}
		rsst.close();
		stmt.close();
	}
	
	private String read_seq() throws PdbaseInconsistencyError, SQLException{
		String sequence="";

        // we use seq_id+0 (implicitly converts to int) in ORDER BY because seq_id is varchar!!
        String sql="SELECT mon_id" +
        		" FROM "+db+".pdbx_poly_seq_scheme " +
        		" WHERE entry_key=" + entrykey +
        		" AND asym_id='"+asymid+"' " +
        		" ORDER BY seq_id+0";

        Statement stmt = conn.createStatement();
        ResultSet rsst = stmt.executeQuery(sql);
        int count=0;
        while (rsst.next()) {
        	count++;
        	String res_type = rsst.getString(1);
        	if (AAinfo.isValidAA(res_type)){
        		sequence+=AAinfo.threeletter2oneletter(res_type);
        	} else {
        		sequence+=AAinfo.NONSTANDARD_AA_ONE_LETTER;
        	}
        } 
        if (count==0) {
        	throw new PdbaseInconsistencyError("No sequence data match for entry_key="+entrykey+", asym_id="+asymid);
        }
        rsst.close();
        stmt.close();

		return sequence;
	}
	
	private HashMap<String,Integer> get_ressers_mapping() throws PdbaseInconsistencyError, SQLException{
		String pdbstrandid=pdbChainCode;
		if (pdbChainCode.equals(NULL_CHAIN_CODE)){
			pdbstrandid="A";
		}

		HashMap<String,Integer> map = new HashMap<String, Integer>();
		String sql="SELECT seq_id, concat(pdb_seq_num,IF(pdb_ins_code='.','',pdb_ins_code))" +
					" FROM "+db+".pdbx_poly_seq_scheme " +
					" WHERE entry_key=" + entrykey +
					" AND asym_id='"+asymid+"' " +
					" AND pdb_strand_id='"+pdbstrandid+"' " +
					" ORDER BY seq_id+0";

		Statement stmt = conn.createStatement();
		ResultSet rsst = stmt.executeQuery(sql);
		int count=0;
		while (rsst.next()) {
			count++;
			int resser = Integer.parseInt(rsst.getString(1));
			String pdbresser = rsst.getString(2);
			map.put(pdbresser, resser);
		} 
		if (count==0) {
			throw new PdbaseInconsistencyError("No residue serials mapping data match for entry_key="+entrykey+", asym_id="+asymid+", pdb_strand_id="+pdbstrandid);
		}
		rsst.close();
		stmt.close();

		return map;
	}

	private void readSecStructure() throws SQLException {
		this.secondaryStructure = new SecondaryStructure();
		
		// HELIX AND TURN -- struct_conf table
		String sql = "SELECT id,beg_label_seq_id,end_label_seq_id " +
				" FROM "+db+".struct_conf " +
				" WHERE entry_key="+entrykey+
				" AND beg_label_asym_id='"+asymid+"'";
		Statement stmt = conn.createStatement();
		ResultSet rsst = stmt.executeQuery(sql);
		int count=0;
		while (rsst.next()) {
			count++;
			String id = rsst.getString(1).trim(); // id is either HELIX_Pnn or TURN_Pnn
			Pattern p = Pattern.compile("^(\\w).+_P(\\d)+$");
			Matcher m = p.matcher(id);
			String ssId="Unknown";
			if (m.find()){
				ssId = m.group(1)+m.group(2); // e.g.: Hnn (helices) or Tnn (turns) 				
			}
			int beg = rsst.getInt(2);
			int end =rsst.getInt(3);
			char ssType = SecStrucElement.OTHER;
			if(id.startsWith("H")) {
				ssType = SecStrucElement.HELIX;
			} else if(id.startsWith("T")) {
				ssType = SecStrucElement.TURN;
			} else {
				System.err.println("Unknown secondary structure type " + id + " encountered when reading from Pdbase. Skipping.");
			}
			if(ssType != SecStrucElement.OTHER) {
				SecStrucElement ssElem = new SecStrucElement(ssType, beg, end, ssId);
				secondaryStructure.add(ssElem);
			}
		} 
		rsst.close();
		stmt.close();
		
		// SHEET -- struct_sheet_range table
		sql = "SELECT sheet_id, id, beg_label_seq_id, end_label_seq_id " +
				" FROM "+db+".struct_sheet_range " +
				" WHERE entry_key="+entrykey+
				" AND beg_label_asym_id='"+asymid+"'";
		stmt = conn.createStatement();
		rsst = stmt.executeQuery(sql);
		count=0;
		while (rsst.next()) {
			count++;
			String sheetid = rsst.getString(1).trim();
			int id = rsst.getInt(2);
			int beg = rsst.getInt(3);
			int end =rsst.getInt(4);
			String ssId=SecStrucElement.STRAND+sheetid+id; // e.g.: SA1, SA2..., SB1, SB2,...
			SecStrucElement ssElem = new SecStrucElement(SecStrucElement.STRAND, beg, end, ssId);
			secondaryStructure.add(ssElem);
		} 
		rsst.close();
		stmt.close();

	}
}
