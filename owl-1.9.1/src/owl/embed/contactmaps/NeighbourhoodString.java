package owl.embed.contactmaps;

import java.io.*;
import java.util.*;
import java.sql.SQLException;

import owl.core.structure.PdbCodeNotFoundException;
import owl.core.structure.PdbLoadError;
import owl.core.util.RegexFileFilter;

import edu.uci.ics.jung.graph.util.Pair;


/**
 * This class is intended to deal contact maps generated by different evolutionary runs as implemented 
 * @author gmueller
 *
 */
public class NeighbourhoodString {
	
	private HashMap<Integer,HashSet<Integer>> index_map_sparse;
	
	private HashMap<Integer,Integer> index_counter;
	
	private HashMap<Integer,String> sequence;
	
	private String file_name;
	
	private String output;
	
	private String content;
	
	private String pdb_code;
	
	private String chain_code;
	
	private String path;
	
	private static final String separator = "%x";
	
	/*---------------------------------------constructors-------------------------------------------------------*/
	
	public NeighbourhoodString (String dir) throws IOException, SQLException, PdbCodeNotFoundException, PdbLoadError{
		setNeighbourhoodString(dir);
	}
	
	
	/*----------------------------------------setters------------------------------------------------------------*/
	
	/**
	 * initial setter: initialized all instances of this class. It checks whether 
	 */
	public void setNeighbourhoodString (String dir) throws IOException, SQLException, PdbCodeNotFoundException, PdbLoadError{
		File is_f = new File(dir);
		if(is_f.exists()){
			path = new String(dir);
			file_name = is_f.getName();
			if(Individuals.isReadableFileFormat(is_f)){
				Individuals individual = new Individuals(is_f.getAbsolutePath());
				extractContactMap(individual.getHashSet());
				//setIndexMapFull(individual.getHashSet());
				chain_code = new String (individual.getChainCode());
				pdb_code = new String (individual.getName());
				setSequence(individual.getSequence());
				setContent();
			}
			else{
				throw new IllegalArgumentException ("The path '"+path+"' does not denote a readable file.");
			}
		}
		else{
			throw new IllegalArgumentException("The path '"+dir+"' does not exist.");
		}
	}
	
	/*---------------------------------------auxiliaries---------------------------------------------------------*/
	
	/**
	 * auxiliary setter: initializes the field <code>{@link #sequence}</code> by taking the sequence String of an
	 * Individuals instance and adds it to the HashMap, where the key is a Integer (index i + 1) of the character array 
	 * and the entries are single character Strings (i.e. the corresponding single code amino acid at position i). 
	 */
	public void setSequence(String seq){
		if(seq != null){
			sequence = new HashMap<Integer,String> (); 
			for(int i = 0; i <seq.length(); i++){
				Integer index = new Integer (i + 1);
				String substr = seq.substring(i, i + 1);
				sequence.put(index, substr);
			}
		}
		else{
			throw new NullPointerException ("The parameter 'seq' must always be initialized before calling the method 'seqSequence(String).");
		}
	}
	
	public void setContent (){
		if(index_map_sparse != null && sequence != null){
			HashMap<Integer,String> seq_map = getSequence();
			HashMap<Integer,HashSet<Integer>> ind_map1 = getIndexMap();
			Set<Integer> keyset = ind_map1.keySet();
			Iterator<Integer> it = keyset.iterator();
			content = "";
			while(it.hasNext()){
				Integer index = it.next();
				int[] subset = convertHashSetToIntArray(ind_map1.get(index));
				int length = subset.length;
				String neighbours_left1 = getPDBCode() + "\t" + getChainCode() + "\t" + index.toString() + "\t" + seq_map.get(index) + "\t";
				String neighbour_right1 = "";
				for(int i = 0; i  < length; i++){
					if(subset[i] < index.intValue()){
						neighbours_left1 += "%" + seq_map.get(new Integer (subset[i]));
					}
					else{
						neighbour_right1 += "%" + seq_map.get(new Integer (subset[i]));
					}
				}
				neighbour_right1 += "%\n";
				content += neighbours_left1 + separator + neighbour_right1;
			}
		}
		else{
			throw new NullPointerException ("Both fields 'index_map' and 'sequence' must be initialized before calling the mthod 'setContent()'!");
		}
	}
	
	/**
	 * auxiliary method: sets the fields <code>{@link #index_map_sparse}</code> and <code>{@link #index_counter}</code> by getting
	 * all contact pairs <code>(i,j)</code> from the parameter 'contactset' and stores them in both fields. In <code>{@link #index_map_sparse}</code> all
	 * indices <code>i,j</code> and their nodes are stored while in <code>{@link #index_counter}</code> each index and its frequencies are stored.
	 * @param contactset
	 */
	public void extractContactMap (HashSet<Pair<Integer>> contactset){
		index_map_sparse = new HashMap<Integer,HashSet<Integer>> ();
		//HashMap containing all indices contained in the 'contactset' parameter
		
		index_counter = new HashMap<Integer,Integer>();
		//HashMap counting the frequencies of each index in the 'contactset' parameter
		
		Iterator<Pair<Integer>> it = contactset.iterator();
		while(it.hasNext()){
			//loop over all entries in the HashSet 'contactset'
			
			Pair<Integer> pair = it.next();
			Integer f_val = pair.getFirst(), s_val = pair.getSecond();
			HashSet<Integer> indeces = new HashSet<Integer> ();
			//a HashSet counting the frequencies of each index
			
			if(index_map_sparse.containsKey(f_val)){
				//check whether the first value is already present in the HashMap
				
				indeces = new HashSet<Integer> (index_map_sparse.get(f_val));
				indeces.add(s_val);
				index_map_sparse.put(f_val, indeces);
				int counter1 = index_counter.get(f_val).intValue() + 1;
				index_counter.put(f_val, new Integer(counter1));
			}
			else{
				//if the first value is not present yet
				indeces.add(s_val);
				index_map_sparse.put(f_val, indeces);
				index_counter.put(f_val, new Integer(1));
			}
			indeces = new HashSet<Integer> ();
			if(index_map_sparse.containsKey(s_val)){
				//check whether the second value is already present in the HashMap
				
				indeces = new HashSet<Integer> (index_map_sparse.get(s_val));
				indeces.add(f_val);
				index_map_sparse.put(s_val,indeces);
				int counter1 = index_counter.get(s_val).intValue() + 1;
				index_counter.put(s_val, new Integer(counter1));
			}
			else{
				//if the second value is not present yet
				
				indeces.add(f_val);
				index_map_sparse.put(s_val, indeces);
				index_counter.put(s_val, new Integer(1));
			}
		}
	}
	
	public void writeToFile () throws IOException{
		if(content != null){
			String dir = (new String (path)).replaceAll(file_name,"nbhstrings/");
			String name = (new String (file_name)).split("\\.")[0];
			output = name;
			File outputdir = new File(dir);
			if(!outputdir.exists()){
				outputdir.mkdirs();
			}
			FileOutputStream output = new FileOutputStream (dir+name+".out");
			PrintStream printa = new PrintStream(output);
			printa.print(toString());
			System.out.println(name + " written to file...");
			printa.close();
			output.close();
		}
	}
	
	public HashMap<Integer,HashSet<Integer>> getIndexMap (){
		return new HashMap<Integer,HashSet<Integer>>(index_map_sparse);
	}
	
	public String getChainCode(){
		return new String (chain_code);
	}
	public String getPDBCode (){
		return new String (pdb_code);
	}
	
	public HashMap<Integer,String> getSequence (){
		return new HashMap<Integer,String>(sequence);
	}
	
	public String getFileName (){
		return new String (file_name);
	}
	
	public String getOutput (){
		return new String (output);
	}
	
	public String toString(){
		return new String (content);
	}
	
	public static Individuals[] getIndividualsFirstAndLast(String dir) throws IOException, SQLException, PdbCodeNotFoundException, PdbLoadError{
		Individuals[] first_n_last = new Individuals[2];
		File file = new File(dir);
		if(Individuals.containsReadableFiles(file)){
			boolean is_not_empty = false;
			int i = 0;
			File[] list = new File[0];
			while(!is_not_empty){
				if(i >= Individuals.file_extension.length){
					break;
				}
				list = file.listFiles(new RegexFileFilter(".*."+Individuals.file_extension[i]));
				if(list.length > 0){
					is_not_empty = true;
				}
				i++;
			}
			if(list.length > 0){
				Arrays.sort(list);
				first_n_last[0] = new Individuals(list[0].getAbsolutePath());
				first_n_last[1] = new Individuals(list[list.length-1].getAbsolutePath());
			}
			else{
				throw new IllegalArgumentException("The denoted path does not contain any readable files...");
			}
			return first_n_last;
		}
		else{
			throw new IllegalArgumentException("The denoted path does not contain any readable files...");
		}
	}
	
	public static int[] convertHashSetToIntArray (HashSet<Integer> set){
		if(set != null){
			int[] array = new int[set.size()];
			int counter = 0;
			Iterator<Integer> it = set.iterator();
			while(it.hasNext()){
				array[counter] = it.next().intValue();
				counter++;
			}
			Arrays.sort(array);
			return array;
		}
		else{
			throw new NullPointerException("The parameter 'set' must never be null.");
		}
	}
	
	public static void main (String[] args) throws IOException, SQLException, PdbCodeNotFoundException, PdbLoadError{
		String dir = "/project/StruPPi/gabriel/Arbeiten/1sha/1sha/deme0/evo2/1shademe0gen12-34.cmap"; //Starter/";
		//String helper = "starter", helper2 = "1sha";
		for(int i = 0; i < 1; i++){
			//int value1 = i%20;
			//int value2 = (int) (((double) i)/((double) 20));
			NeighbourhoodString nbhstr = new NeighbourhoodString(dir);// + helper + value2 + helper2 + value1 + "-34.indi");
			nbhstr.writeToFile();
		}
	}

}
