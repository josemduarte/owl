package embed;

import java.sql.*;
import java.util.*;
import java.io.*;

import tools.*;

/**
 * A class dealing with neighborhood Strings. All these neighborhood Strings are generated by the class
 * <code>{@link NeighbourhoodString}</code> in the same package, and put in a <tt>.out</tt> file. If such
 * files do not exist, the class 2-parameter constructor issues an IllegalArgumentException or FileNotFoundException.
 * So any use of this class for an other file type must implement this class as a superclass and override the initial
 * setter method <code>{@link #setNbhStringMaps(String, String)}</code>. It is important to notice, that the two parameters
 * of this constructor a String denoting the directory containing all the neighborhood String files and the second parameter
 * is the file name of the final population.
 * <p>
 * </p>
 * <p>
 * </p>
 * The output of this class is a String in table format, which in turn can be written to a text file by the implemented
 * <code>{@link #writeToFile(String,String)}</code> method. The format is:
 * <p>
 * pdb code 	single letter aa code + position 	abundance 	ranked by the occurrence within the table (i out of twenty)</p>  
 * @author gmueller
 * @see <code>{@link NeighbourhoodString}</code> in order to see the standard output of the mentioned class
 *
 */
public class NbhStringRanker extends tools.MySQLConnection {
	
	/*---------------------------------------------------members-----------------------------------------------------*/
	
	/*--------------------------------------------------constants----------------------------------------------------*/
	/**
	 * command leader: a constant String instance giving the first part of the SQL query
	 */
	private static final String command_leader    = "select res, count(*) AS c from cullpdb_20.nbstrings where f LIKE '";
	
	/**
	 * command trailer: a constant String instance giving the final part of the SQL query
	 */
	private static final String command_trailer   = "' group by  res order by c desc;";
	
	/*---------------------------------------------------fields------------------------------------------------------*/
	
	/**
	 * a String HashMap, where the key is the single letter amino acid code plus its position within the sequence and
	 * the value is the neighborhood String. It only reads the files, that do not match the final population neighborhood
	 * String files
	 */
	HashMap<String,String> aa_nbh_map_random;
	
	/**
	 * a String HashMap, where the key is the single letter amino acid code plus its position within the sequence and
	 * the value is the neighborhood String. This field only contains the information from the final generation neighborhood
	 * String file. 
	 */
	HashMap<String,String> aa_nbh_map_final;
	
	/**
	 * a HashMap containing the single letter aa code and maps them to all possible positions within the protein sequence
	 */
	HashMap<String,HashSet<Integer>> aa_map;
	
	/**
	 * the pdb code of the protein, if the pdb codes of two different neighborhood String files do not match, the <code>{@link #setNbhStringMaps(String, String)}</code> throws
	 * an <code>IllegalArgumentException</code>.
	 */	
	String pdb_code;
	
	/**
	 * the content, listing the results from all queries
	 */
	private String content;
	
	
	/*-------------------------------------------------constructors--------------------------------------------------*/
	/**
	 * Zero-parameter constructor: pretty much does not do anything
	 */
	public NbhStringRanker () throws SQLException{
		super();		
	}
	
	/**
	 * Two-parameter constructor: this is the most important constructor. It uses a String parameter to
	 * define the directory containing the neighborhood String files, which in turn will be listed in a File array.
	 * The second parameter specifies the final neighborhood String file, which is equivalent to the last generation in the evolutionary run from
	 * any of the classes <code>{@link Individuals}</code>, <code>{@link Demes}</code> or <code>{@link Species}</code> in the embed package.
	 * @param dir a String denoting the directory of the neighborhood String files
	 * @param final_generation the neighborhood String file of the last generation
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws SQLException
	 * @throws IllegalArgumentException
	 */
	public NbhStringRanker (String dir, String final_generation) throws FileNotFoundException, IOException, SQLException, IllegalArgumentException {
		super();
		this.setNbhStringMaps(dir,final_generation);
	}
	
	/**
	 * initial setter: reads all specified neighborhood String files (nbh files, with '.out' extension) and
	 * converts them to a HashMap, where the key is single letter amino acid code plus the position within the
	 * peptide chain and value is the actual neighborhood String. In case of
	 * neighborhood String files in different format, one has to implement this class as superclass and must (!) override this method,
	 * otherwise an Exception may occur.
	 * @param dir String denoting the absolute path of the directory, where all neighborhood String files are
	 * stored
	 * @param final_generation the file name of the final generation neighborhood String file
	 * @throws IOException the denoted directory does not exist
	 * @throws FileNotFoundException files do not exist
	 * @throws IllegalArgumentException
	 */
	public void setNbhStringMaps (String dir, String final_generation) throws IOException, FileNotFoundException, IllegalArgumentException {
		File file = new File (dir);
		if(file.exists()){
			//check, whether the directory exists
			
			File[] list = file.listFiles(new RegexFileFilter (".*.out"));
			//File array with all files having an '.out' extension
			
			int length = list.length;
			
			File   fina = new File (dir + final_generation);
			//the final generation neighborhood String file gets its own File instance
			
			if(fina.exists()){
				//check, whether the final generation neighborhood String files exist
				
				this.aa_map = new HashMap<String,HashSet<Integer>> ();
				
				this.setAANbhStringMaps(fina, true);
			}
			else{
				throw new IllegalArgumentException ("The neighborhood String file of the final generation was not found!");
				//if it does not exist, an Exception is thrown
				
			}
			if(length > 0){
				//check, whether the File array contains at least one entry
				
				for(int i = 0; i < length; i++){
					//loop over the File array
					
					this.setAANbhStringMaps(list[i], false);
				}
			}
			else{
				throw new IllegalArgumentException ("There are no further neighborhood String files, please make sure final and inital neighborhood String files are present at the same time.");
				//if the File array has no entries, an exception is thrown
				
			}
		}
	}
	
	/**
	 * auxiliary method: this is the actual reader method, reading the neighborhood String files, converts
	 * them to a <code>{@link HashMap}</code> and stores them in either of the two fields:
	 * <p>
	 * </p>
	 * <p>
	 * <code>{@link #aa_nbh_map_final}</code>: the neighborhood String map of the final generation
	 * </p>
	 * <p>
	 * <code>{@link #aa_nbh_map_random}</code>: the neighborhood String map of the initial generation
	 * </p>
	 * @param file a File instance representing the neighborhood String file
	 * @param final_vs_random determines, where to store the HashMap, if <tt>true</tt>, then the
	 * <code>{@link #aa_nbh_map_final}</code> will be used, otherwise <code>{@link #aa_nbh_map_random}</code> 
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws IllegalArgumentException if two pdb codes do not match
	 */
	private void setAANbhStringMaps(File file, boolean final_vs_random) throws IOException, FileNotFoundException, IllegalArgumentException {
		BufferedReader reader = new BufferedReader (new FileReader (file));
		String linereader = "";
		HashMap<String,String> str_map = new HashMap<String,String> ();
		//the neighborhood String map
		
		while((linereader = reader.readLine()) != null){
			//reading the file
			
			String[] str_array = linereader.split("\t");
			//splitting the line reader, first column: pdb code, second column: chain code, third column: position,
			//fourth column: neighborhood String
			
			if(this.pdb_code == null){
				//check, whether the field 'pdb_code' was not initialized, yet
				
				this.pdb_code = new String (str_array[0]);
				String f_val = str_array[3] + str_array[2];
				//first value = key: amino acid code + position
				
				String s_val = str_array[4];
				//neighborhood String
				
				str_map.put(f_val, s_val);
				//place both key and value in the HashMap
				
				if(this.aa_map.containsKey(str_array[3])){
					//check, whether the amino acid as been place in the field 'aa_map'
					
					HashSet<Integer> index_set = this.aa_map.get(str_array[3]);
					Integer index = new Integer ((int) Double.parseDouble(str_array[2]));
					index_set.add(index);
					this.aa_map.put(str_array[3], index_set);
				}
				else{
					HashSet<Integer> index_set = new HashSet<Integer> ();
					Integer index = new Integer ((int) Double.parseDouble(str_array[2]));
					index_set.add(index);
					this.aa_map.put(str_array[3], index_set);
				}
			}
			else{
				//if the field 'pdb_code' was initialized before
				
				if(str_array[0].matches(this.pdb_code)){
					//check, whether both pdb code match, if not an IllegalArgumentException is
					//thrown
					
					String f_val = str_array[3] + str_array[2];
					//first value = key: amino acid code + position
					
					String s_val = str_array[4];
					//neighborhood String
					
					str_map.put(f_val, s_val);
					//place both key and value in the HashMap
					
					if(this.aa_map.containsKey(str_array[3])){
						HashSet<Integer> index_set = this.aa_map.get(str_array[3]);
						Integer index = new Integer ((int) Double.parseDouble(str_array[2]));
						index_set.add(index);
						this.aa_map.put(str_array[3], index_set);
					}
					else{
						HashSet<Integer> index_set = new HashSet<Integer> ();
						Integer index = new Integer ((int) Double.parseDouble(str_array[2]));
						index_set.add(index);
						this.aa_map.put(str_array[3], index_set);
					}
				}
				else{
					throw new IllegalArgumentException ("The pdb codes do not match, please make sure only neighbourhood String files of the same proteins are treated at the same time!!");
				}
			}
		}
		if(final_vs_random){
			this.aa_nbh_map_final = new HashMap<String,String> (str_map);
		}
		else{
			this.aa_nbh_map_random = new HashMap<String,String> (str_map);
		}
	}
	
	/**
	 * The method, that issues all SQL queries and stores them in the <code>{@link #content}</code> field.
	 *  
	 * @throws SQLException
	 */
	public void runQueries () throws SQLException{
		HashMap<String,String> final_map = this.getAaNbhStringMapFinal();
		HashMap<String,String> rando_map = this.getAaNbhStringMapRandom();
		//getting the neighborhood String maps
		
		Set<String> keyset = final_map.keySet();
		Iterator<String> it = keyset.iterator();
		this.content    = "pdb code \t aa \t occurrance \t rank of 20 \t type\n";
		while(it.hasNext()){
			//iterating over all entries
			
			String key      = it.next();
			Statement stmt, stmt2;
			ResultSet rs, rs2;
			try{
				stmt = super.createStatement();
				rs = stmt.executeQuery(command_leader + final_map.get(key) + command_trailer);
				//executing the query from the final generation
				
				while(rs.next()){
					//iterating over the result set tables and check for the matching amino acid
					
					String str = rs.getString("res");
					if(key.contains(str)){
						//does the entry match the amino acid key?
						
						this.content += this.pdb_code + "\t" + key + "\t" + rs.getString("c") + "\t" + rs.getRow() + "\t" + "final\n";
						//creating the field 'content'
						
						break;
					}
				}
				rs.close();
				stmt.close();
			}
			catch(SQLException e){
				//catching SQL exception
				
				System.err.println("SQLException: " + e.getMessage());
				System.err.println("SQLState:     " + e.getSQLState());
				System.err.println("VendorError:  " + e.getErrorCode());
				e.printStackTrace();
			}
			try{
				stmt2 = super.createStatement();
				rs2 = stmt2.executeQuery(command_leader + rando_map.get(key) + command_trailer);
				//executing the query from the random set
				
				while(rs2.next()){
					//iterating over all result set entries from random set
					
					String str = rs2.getString("res");
					if(key.contains(str)){
						//does the entry match the amino acid?
						
						this.content += this.pdb_code + "\t" + key + "\t" + rs2.getString("c") + "\t" + rs2.getRow() + "\t" + "random\n";
						//creating the field 'content'
						
						break;
					}
				}
				rs2.close();
				stmt2.close();
			}
			catch(SQLException e){
				//catching SQL exception
				
				System.err.println("SQLException: " + e.getMessage());
				System.err.println("SQLState:     " + e.getSQLState());
				System.err.println("VendorError:  " + e.getErrorCode());
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * method writing the field <code>{@link #content}</code> to a text file.
	 * @param dir a String denoting the output directory
	 * @param file_name the file name
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public void writeToFile (String dir, String file_name) throws FileNotFoundException, IOException {
		File file = new File (dir + file_name);
		if(!file.exists()){
			file.mkdirs();
		}
		FileOutputStream output = new FileOutputStream (file);
		PrintStream printer     = new PrintStream    (output);
		printer.print(this.toString());
		printer.close();
		output.close();
	}

	/**
	 * method that returns a new HashMap instance of the field <code>{@link #aa_nbh_map_final}</code>
	 * @return a new HashMap of the neighborhood Strings of the final generation
	 */
	protected HashMap<String,String> getAaNbhStringMapFinal (){
		return new HashMap<String,String> (this.aa_nbh_map_final);
	}
	
	/**
	 * method that returns a new HashMap instance of the field <code>{@link #aa_nbh_map_random}</code>
	 * @return a new HashMap of the neighborhood Strings of the random set
	 */
	protected HashMap<String,String> getAaNbhStringMapRandom (){
		return new HashMap<String,String> (this.aa_nbh_map_random);
	}
	
	/**
	 * method that returns the field <code>{@link #content}</code> as a new String instance
	 */
	public String toString (){
		return new String (this.content);
	}
	
	public static void main (String[] args) throws FileNotFoundException, IOException, SQLException{
		String dir        = "/project/StruPPi/gabriel/Arbeiten/run_051109/nbhstrings_1sha/";
		String final_file = "1shademe0gen12-34.out";
		NbhStringRanker conn = new NbhStringRanker (dir,final_file);
		conn.runQueries();
		System.out.println(conn.toString());
	}
}
