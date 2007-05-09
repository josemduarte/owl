package proteinstructure;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Pdb {
	
	HashMap<String,Integer> resser_atom2atomserial;
	HashMap<Integer,String> resser2restype;
	HashMap<Integer,Double[]> atomser2coord;
	HashMap<Integer,Integer> atomser2resser;
	HashMap<String,ArrayList<String>> aas2atoms = AA.getaas2atoms();
	String sequence="";
	String accode="";
	String chaincode="";
	String db;
	String chain;
	
	public Pdb (String accode, String chaincode) throws PdbaseInconsistencyError, PdbaseAcCodeNotFoundError {
		this(accode,chaincode,PdbaseInfo.pdbaseDB);
		
	}
	
	public Pdb (String accode, String chaincode, String db) throws PdbaseInconsistencyError, PdbaseAcCodeNotFoundError {
		this.accode=accode;
		this.chaincode=chaincode;
		this.db=db;
		this.chain=chaincode; // we initialise it to chaincode, in read_pdb_data_from_pdbase gets reset to the right internal chain id
		read_pdb_data_from_pdbase(db);
	}
	
	public Pdb (String pdbfile) throws FileNotFoundException, IOException {
		this.accode="";
		read_pdb_data_from_file(pdbfile);
	}
	
	public void read_pdb_data_from_pdbase(String db) throws PdbaseInconsistencyError, PdbaseAcCodeNotFoundError{
		resser_atom2atomserial = new HashMap<String,Integer>();
		resser2restype = new HashMap<Integer,String>();
		atomser2coord = new HashMap<Integer,Double[]>();
		atomser2resser = new HashMap<Integer,Integer>();

		PdbaseInfo mypdbaseinfo = new PdbaseInfo(accode,chaincode,db);
		ArrayList<ArrayList> resultset = mypdbaseinfo.read_atomData();
		sequence = mypdbaseinfo.read_seq();
		mypdbaseinfo.close();
		
		for (ArrayList result:resultset){
			int atomserial = (Integer) result.get(0);
			String atom = (String) result.get(1);
			String res_type = (String) result.get(2);
			chain=(String) result.get(3);
			int res_serial = (Integer) result.get(4);
			double x = (Double) result.get(5);
			double y = (Double) result.get(6);
			double z = (Double) result.get(7);
			Double[] coords = {x, y, z};
			ArrayList<String> aalist=AA.aas();
			if (aalist.contains(res_type)) {
				atomser2coord.put(atomserial, coords);
				atomser2resser.put(atomserial, res_serial);
				resser2restype.put(res_serial, res_type);
				ArrayList<String> atomlist = aas2atoms.get(res_type);
				if (atomlist.contains(atom)){
					resser_atom2atomserial.put(res_serial+"_"+atom, atomserial);
				}
			}
		}
	}
	
	public void read_pdb_data_from_file(String pdbfile) throws FileNotFoundException, IOException{
		resser_atom2atomserial = new HashMap<String,Integer>();
		resser2restype = new HashMap<Integer,String>();
		atomser2coord = new HashMap<Integer,Double[]>();
		atomser2resser = new HashMap<Integer,Integer>();

		BufferedReader fpdb = new BufferedReader(new FileReader(new File(pdbfile)));
		String line;
		while ((line = fpdb.readLine() ) != null ) {
			Pattern p = Pattern.compile("^ATOM");
			Matcher m = p.matcher(line);
			if (m.find()){
				Pattern pl = Pattern.compile(".{6}(.....).{2}(...).{1}(...).{2}(.{4}).{4}(.{8})(.{8})(.{8})",Pattern.CASE_INSENSITIVE);
				Matcher ml = pl.matcher(line);
				if (ml.find()) {
					int atomserial=Integer.parseInt(ml.group(1).trim());
					String atom = ml.group(2).trim();
					String res_type = ml.group(3).trim();
					int res_serial = Integer.parseInt(ml.group(4).trim());
					double x = Double.parseDouble(ml.group(5).trim());
					double y = Double.parseDouble(ml.group(6).trim());
					double z = Double.parseDouble(ml.group(7).trim());
					Double[] coords = {x, y, z};
					ArrayList<String> aalist=AA.aas();
					if (aalist.contains(res_type)) {
						atomser2coord.put(atomserial, coords);
						atomser2resser.put(atomserial, res_serial);
						resser2restype.put(res_serial, res_type);
						ArrayList<String> atomlist = aas2atoms.get(res_type);
						if (atomlist.contains(atom)){
							resser_atom2atomserial.put(res_serial+"_"+atom, atomserial);
						}
					}
				}				
			}
		}
		fpdb.close();
		// now we read the sequence from the resser2restype HashMap
		// NOTE: we must make sure elsewhere that there are no unobserved residues, we can't check that here!
		ArrayList<Integer> ressers = new ArrayList<Integer>();
		for (int resser:resser2restype.keySet()) {
			ressers.add(resser);
		}
		Collections.sort(ressers);
		sequence="";
		for (int resser:ressers){
			String oneletter = AA.threeletter2oneletter(resser2restype.get(resser));
			sequence += oneletter;
		}
        // finally we set accode and chaincode to unknown 
        //TODO: we should parse accode and chaincode from appropriate fields in pdb file, 
		// problem: in case of a non-original pdb file there won't be accession code		
		accode="?";
		chaincode="?";
	}

	public void dump2pdbfile(String outfile) throws IOException {
		String chainstr=chain;
		if (chain.equals("NULL")){
			chainstr="A";
		}
		PrintStream Out = new PrintStream(new FileOutputStream(outfile));
		Out.println("HEADER  Dumped from "+db+". pdb accession code="+accode+", pdb chain code="+chaincode);
		for (String resser_atom:resser_atom2atomserial.keySet()){
			int atomserial = resser_atom2atomserial.get(resser_atom);
			int res_serial = Integer.parseInt(resser_atom.split("_")[0]);
			String atom = resser_atom.split("_")[1];
			String res_type = resser2restype.get(res_serial);
			Double[] coords = atomser2coord.get(atomserial);
			Object[] fields = {atomserial, atom, res_type, chainstr, res_serial, coords[0], coords[1], coords[2]};
			Out.printf("ATOM  %5d  %3s %3s %1s%4d    %8.3f%8.3f%8.3f\n",fields);
		}
		Out.println("END");
		Out.close();
	}
	
	public void dumpseq(String seqfile) throws IOException {
		PrintStream Out = new PrintStream(new FileOutputStream(seqfile));
		Out.println(">"+accode+"_"+chaincode);
		Out.println(sequence);
		Out.close();
	}
	
	public int get_length(){
		return resser2restype.size();
	}
	
	public HashMap<Integer,Double[]> get_coords_for_ct(String ct) {
		HashMap<Integer,Double[]> coords = new HashMap<Integer,Double[]>(); 
		HashMap<String,String[]> restype2atoms = AA.ct2atoms(ct);
		for (int resser:resser2restype.keySet()){
			String[] atoms = restype2atoms.get(resser2restype.get(resser));
			for (String atom:atoms){
				if (resser_atom2atomserial.containsKey(resser+"_"+atom)){
					int atomser = resser_atom2atomserial.get(resser+"_"+atom);
					Double[] coord = atomser2coord.get(atomser);
					coords.put(atomser, coord);
				}
				else if (atom.equals("O") && resser_atom2atomserial.containsKey(resser+"_"+"OXT")){
					int atomser = resser_atom2atomserial.get(resser+"_"+"OXT");
					Double[] coord = atomser2coord.get(atomser);
					coords.put(atomser, coord);
				}
				else {
					System.err.println("Couldn't find "+atom+" atom for resser="+resser+". Continuing without that atom for this resser.");
				}
			}
		}
		return coords;
	}
	
	public TreeMap<Contact, Double> calculate_dist_matrix(String ct){
		TreeMap<Contact,Double> dist_matrix = new TreeMap<Contact,Double>();
		if (!ct.contains("/")){
			HashMap<Integer,Double[]> coords = get_coords_for_ct(ct);
			for (int i_atomser:coords.keySet()){
				for (int j_atomser:coords.keySet()){
					if (j_atomser>i_atomser) {
						Contact pair = new Contact(i_atomser,j_atomser);
						dist_matrix.put(pair, distance(coords.get(i_atomser),coords.get(j_atomser)));
					}
				}
			}
		} else {
			String i_ct = ct.split("/")[0];
			String j_ct = ct.split("/")[1];
			HashMap<Integer,Double[]> i_coords = get_coords_for_ct(i_ct);
			HashMap<Integer,Double[]> j_coords = get_coords_for_ct(j_ct);
			for (int i_atomser:i_coords.keySet()){
				for (int j_atomser:j_coords.keySet()){
					if (j_atomser!=i_atomser){
						Contact pair = new Contact(i_atomser,j_atomser);
						dist_matrix.put(pair, distance(i_coords.get(i_atomser),j_coords.get(j_atomser)));
					}
				}
			}
		}
		return dist_matrix;
	}
	
	public Graph get_graph(String ct, double cutoff){
		TreeMap<Contact,Double> dist_matrix = calculate_dist_matrix(ct);
		ArrayList<Contact> contacts = new ArrayList<Contact>();
		for (Contact pair:dist_matrix.keySet()){
			int i_atomser=pair.i;
			int j_atomser=pair.j;
			if (dist_matrix.get(pair)<=cutoff){
				int i_resser = atomser2resser.get(i_atomser);
				int j_resser = atomser2resser.get(j_atomser);
				Contact resser_pair = new Contact(i_resser,j_resser);
				if (i_resser!=j_resser && (! contacts.contains(resser_pair))){
					contacts.add(resser_pair);
				}
			}
		}
		Collections.sort(contacts);
		Graph graph = new Graph (contacts,cutoff,ct);
		return graph;
	}
	
	public Double distance(Double[] coords1, Double[] coords2){
		return Math.sqrt(Math.pow((coords1[0]-coords2[0]),2)+Math.pow((coords1[1]-coords2[1]),2)+Math.pow((coords1[2]-coords2[2]),2));
	}
}