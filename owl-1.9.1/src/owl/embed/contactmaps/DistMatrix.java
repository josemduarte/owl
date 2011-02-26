package owl.embed.contactmaps;

import Jama.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import owl.core.structure.PdbCodeNotFoundException;
import owl.core.structure.PdbLoadError;

import edu.uci.ics.jung.graph.util.*;

/**
 * This class deals with contact maps generated by any evolutionary run and converts it to a <code>{@link Matrix}</code> instance.
 * This instance can in turn be used to compute power sequence of this matrix. Additionally, this class provides methods to deal with
 * this matrix as computing <tt>LR</tt> factorization, minimal polynomials, eigenvalue decomposition etc. The default power sequence
 * only computes the first two powers of the matrix.
 * @author gmueller
 *
 */
public class DistMatrix {
	
	
	/*-------------------------------------------------constructors--------------------------------------------------*/
	
	/**
	 * One parameter constructor: initializes any instance of this class. The default maximal power (minus one)
	 * is set to three. This constructor uses the setter <code>{@link #setDistMat(String, int)}</code>, so for further
	 * details, see the abovementioned method.
	 * @param path a String denoting the absolute path of a file, that will be accepted by the <code>{@link Individuals#Individuals(String)}</code>
	 * constructor 
	 * @throws PdbLoadError 
	 * @throws PdbCodeNotFoundException 
	 * @throws SQLException 
	 * @throws IOException 
	 * 
	 */
	public DistMatrix(String path) throws IOException, SQLException, PdbCodeNotFoundException, PdbLoadError{
		setDistMat(path, 3);
		//calling the initial setter
		
	}
	

	/*----------------------------------------------------members----------------------------------------------------*/
	
	
	/*----------------------------------------------------fields-----------------------------------------------------*/	
	
	/**
	 * dimension of the contact map representing matrix
	 */
    private int dim;
    
    /**
     * highest power p - 1, appearing in the power sequence
     */
    private int max_power;

    /**
     * eigenvalues of the contact map representing matrix
     */
    private double[] eigenvalues;

    /**
     * the contact map representing matrix
     */
    private Matrix mat;
    
    /**
     * the transformation matrix, converting <code>{@link #mat}</code> to a diagonal (or Jacobian) matrix
     */
    private Matrix transformer;
    
    /**
     * chain code of the protein
     */
    private String name;
    
    /**
     * a String instance representing the minimal polynomial <tt>m_A</tt>, by definition the minimal polynomial
     * is the greatest common divisor of the characteristic polynomial <tt>p_A</tt> of the matrix <tt>A</tt>,
     * that holds <tt>m_A | p_A</tt> and <tt>m_A(A) = 0</tt>, where <tt>0</tt> is the zero matrix. <tt>A</tt> is
     * similar to a diagonal matrix <tt>D</tt> if and only if <tt>m_A = p_A</tt>. 
     */
    private String minpoly;
    
    /**
     * the eigenvalue decomposition of <code>{@link #mat}</code>.
     */
    private EigenvalueDecomposition eigmats;
    
    /**
     * the power sequence of the matrix
     */
    private HashMap<Integer,HashMap<Integer,Matrix>> potence_sequence;
    
    
    /**
     * some helper variables, the matrix <code>{@link #unity}</code> is the unity matrix, where only the diagonal entries are
     * non-zero. The matrix <code>{@link #zero}</code> is the zero matrix. 
     */
    public static Matrix unity, zero;
    
    
    /*---------------------------------------------------constants---------------------------------------------------*/
    
    /**
	 * standard alpha helical distance between two adjacent amino acids in Aangstroem
	 */
	public static final double Calpha = 3.8;
	
	
	/*----------------------------------------------------setter-----------------------------------------------------*/
	
	/**
	 * the initial setter: first creates an instance of the <code>{@link Individuals}</code> class, gets the contact map from this instance
	 * and converts it to a <code>{@link Matrix}</code> instance. Thereafter, all additional fields are instantiated.
	 * <p>
	 * </p>
	 * It is important to notice, that the used constructor <code>{@link Individuals#Individuals(String)}</code> may exit any run, if no such file exists or
	 * the extension of the given file does not match the predefined extensions in the abovementioned class. See also: <code>{@link Individuals#Individuals(String)}</code>
	 */
    public void setDistMat(String path, int maxpotence) throws IOException, SQLException, PdbCodeNotFoundException, PdbLoadError{
        Individuals in = new Individuals (path);
        //the Individuals instance, since this class accepts only four types of contact map files, one should be
        //sure, what the extension of the denoted file is
        
        double[][] mats = generateContactMatrix(in);
        //generating the contact map matrix as a double instance
        
        max_power = maxpotence;
        //maximal power minus one of the matrix power sequence
        
        int i = mats.length;
        dim = i;
        //dimension of the squared matrix
        
        mat = new Matrix(mats);
        //the actual contact map matrix
        
        name = "A";
        //chain code, only default permitted
        
        setEigVals();
        //instantiating the eigenvalue fields
        
        setTrans();
        //instantiating the transformation matrix
        
        setUnity(i);
        //setting the unity and zero matrix
        
        setPotenceSequence();
        //instantiating the power sequence

    }

    /*----------------------------------------------------auxiliary setter-------------------------------------------*/
    
    /**
     * auxiliary setter: initializes the fields <code>{@link #eigmats}</code> and <code>{@link #eigenvalues}</code>, using
     * standard methods of the <code>{@link Matrix}</code> class
     */
    public void setEigVals(){
        if(mat != null){
        	//only if the field 'mat' was initialized before calling this method, it will proceeds
        	
            int dims = getDim();
            eigmats = new EigenvalueDecomposition(getMat());
            //getting the eigenvalue decomposition of the contact map matrix
            
            Matrix eigs1 = new Matrix(eigmats.getD().getArray());
            //a matrix instance with all eigenvalues (getD() = get diagonal matrix)
            
            double[][] mats = eigs1.getArray();
            //converting the eigenvalue matrix to a double matrix
            
            double[] eigvals = new double[dims];
            for(int i = 0; i < dims; i++){
                eigvals[i] = mats[i][i];
                //copying the eigenvalues
                
            }
            eigenvalues = new double[dims];
            System.arraycopy(eigvals,0,eigenvalues,0,dims);
            //initializing the field "eigenvalues'
            
        }
        else{
            String nullpointer = "The field 'mat' is not initiated!!";
            throw new NullPointerException("\n" + nullpointer);
            //if the field 'mat' was not initialized before calling this method,
            //an NullPointerException is issued, stating the 'non' initialization of the field
            
        }
    }
    
    /**
     * auxiliary setter: creates the transforming matrix <tt>T</tt>, such that <tt>T^{-1} mat T = diag(l_1,l_2,...,l_n)</tt>,
     * where <tt>l_i</tt> equals the i-th eigenvalue. In other words, <code>{@link #transformer}</code> <tt>= T</tt> 
     * is the conjugation matrix that converts the field <code>{@link #mat}</code> to a diagonal matrix.  
     */
    public void setTrans(){
    	try{
    		transformer = new Matrix(eigmats.getV().getArray());
    		//transformation matrix, if field 'eigmats' is not initialized
    		//an NullPointerException may occur
    	}
    	catch(NullPointerException e){
    		if(mat == null){
    			String nullpointer = "Field 'mat' is not initiated!!";
    			System.out.println(nullpointer + "\n" + e);
    		}
    		else{
    			String nullpointer = "Field 'eigmats' is not initiated!!";
    			System.out.println(nullpointer + "\n" + e);
    		}
    	}
    }
    
    /**
     * auxiliary setter: generates the power sequence of the contact map matrix
     * <code>{@link mat}</code>. If the rank of the i-th power is zero, the computation
     * is ceased. The field <code>{@link #potence_sequence}</code> contains the power as
     * the first key value, the second key value is the rank of the matrix. 
     */
    public void setPotenceSequence(){
    	if(mat != null){
    		//check for initialization
    		
    		int counter = 1;
    		int max_pow = max_power;
    		//maximal power minus one occurring in the power sequence
    		
    		potence_sequence = new HashMap<Integer,HashMap<Integer,Matrix>> (2 * max_pow);
    		HashMap<Integer,Matrix> submap = new HashMap<Integer,Matrix> ();
    		Matrix ma = new Matrix (unity.getArray());
    		//starting with the unity matrix
    		
    		boolean tester = true; 
    		while(counter < max_pow && tester){
    			//iterating over the powers
    			
    			ma = ma.times(mat);
    			int rank = ma.rank();
    			if(rank == 0){
    				//check, whether the rank is zero
    				
    				tester = false;
    			}
    			Integer potence = new Integer (counter), rankd = new Integer (rank);
    			submap.put(rankd, ma);
    			//adding the rank and the matrix to a submap
    			
    			potence_sequence.put(potence, submap);
    			//adding it to the field, where
    			
    			counter++;
    		}
    	}
    	throw new NullPointerException ("The field 'mat' must be initialized before calling this method.");
    }
    
    /**
     * auxiliary setter: initializes the field <code>{@link #minpoly}</code>
     */
    public void minPoly(){
    	minpoly = "";
    	//try{
    	int dim1 = dim;
    	Matrix min = new Matrix(dim1, dim1);
    	double[] eigvec = new double[dim1];
    	System.arraycopy(eigenvalues, 0, eigvec, 0, dim1);
    	for(int i = 0; i < dim1; i++){
    		if(i > 0){
    			min = new Matrix((min.times(getMat().minus(getUntiy().times(eigvec[i])))).getArray());
    			if(!isEqualTo(min,zero)){
    				if(eigvec[i] >= 0.0){
    					minpoly += "(" + getName() + " - " + eigvec[i] + " I_" + dim1 +")";
    				}
    				else{
    					minpoly += "(" + getName() + " + " + Math.abs(eigvec[i]) + " I_" + dim1 +")";
    				}
    			}
    		}
    		else{
    			min = new Matrix((getMat().minus(getUntiy().times(eigvec[i]))).getArray());
    		}
    	}
    }
    
    
    /*----------------------------------------------------getter-----------------------------------------------------*/
    

    public int getDim(){
        return dim;
    }

    public Matrix getMat(){
        return new Matrix (mat.getArray());
    }
    
    public EigenvalueDecomposition getEigDecomp(){
    	return eigmats;
    }
    
    public double[] getEigvals(){
    	int dim1 = getDim();
    	double[] eigs = new double[dim1];
    	System.arraycopy(eigenvalues, 0, eigs, 0, dim1);
    	return eigs;
    }
    
    public double getEigvals(int i){
    	return getEigvals()[i];
    }
    
    public static Matrix getUntiy(){
    	return new Matrix(unity.getArray());
    }
    
    public String getName(){
    	return name;
    }
    
    public double[][] genMat(int index1, int index2, int value) {
    	int dim = getDim();
    	double[][] array = new double[dim][dim];
    	boolean tester = index1 < 0 || index1 < 0;
    	if(index1 < dim && index2 < dim && !tester){
    	}
    	else{
    		if(index1 >= dim){
    			String dimmismatch = "Index 'index1 = " + index1 + " is greater than dimension 'dim' = " + dim + " of this 'Matrix' instance!!";
    			throw new IllegalArgumentException("\n" + dimmismatch);
    		}
    		if(index2 >= dim){
    			String dimmismatch = "Index 'index2 = " + index2 + " is greater than dimension 'dim' = " + dim + " of this 'Matrix' instance!!";
    			throw new IllegalArgumentException("\n" + dimmismatch);
    		}
    		if(tester){
    			String dimmismatch = "Both indices must never be less than zero!!";
    			throw new IllegalArgumentException("\n" + dimmismatch);
    		}
    	}
    	return array;
    }
    
    public double[] get3GreatestEigs(){
    	int dim1 = getDim();
    	int[] comparer = new int[dim];
    	for(int i = 0; i < dim1 - 1; i++){
    		for(int j = i + 1; j < dim1; j++){
    			double val1 = Math.abs(getEigvals(i));
    			double val2 = Math.abs(getEigvals(j));
    			if(val1 < val2){
    				comparer[j]++;
    			}
    			if(val2 < val1){
    				comparer[i]++;
    			}
    		}
    	}
    	double[] greatest = new double[3];
    	for(int i = 0; i < dim1; i++){
    		if(comparer[i] == dim1 - 1){
    			greatest[0] = getEigvals(i);
    		}
    		if(comparer[i] == dim1 - 2){
    			greatest[1] = getEigvals(i);
    		}
    		if(comparer[i] == dim1 - 3){
    			greatest[2] = getEigvals(i);
    		}
    	}
    	return greatest;
    }
    
    public Matrix getTransformer (){
    	return new Matrix (transformer.getArray());
    }
    
    public String toString(){
    	int dim1 = dim;
    	String matstring = "";
    	for(int i = 0; i < dim1; i++){
    		for(int j = 0; j < dim1; j++){
    			if(j == 0){
    				matstring = matstring + getMat().getArray()[i][j];
    			}
    			if(j < dim1 - 1 && j > 0){
    				matstring = matstring + "\t" + getMat().getArray()[i][j]; 
    			}
    			if(j == dim1 - 1){
    				matstring = matstring + "\t" + getMat().getArray()[i][j] + "\n";
    			}
    		}
    	}
    	return matstring;
    }
    
    
    public static boolean isEqualTo (Matrix m1, Matrix m2){
    	boolean tester = true;
    	int dim1 = m1.getColumnDimension();    	
    	for(int i = 0; i < dim1; i++){
    		for(int j = 0; j < dim1; j++){
    			double val1 = m1.getArray()[i][j], log1 = Math.log10(val1), exp1 = Math.pow(10.0, log1 + 5);
    			double val2 = m2.getArray()[i][j], log2 = Math.log10(val2), exp2 = Math.pow(10.0, log2 + 5);
    			double round1 = val1*exp1;
    			double round2 = val2*exp2;
    			round1 = Math.floor(round1)/exp1;
    			round2 = Math.floor(round2)/exp2;
    			if(round1 == round2){
    				tester = tester && true;
    			}
    		}
    	}
    	return tester;
    }

    
    /**
     *  
     * @param dim1
     */
    public static void setUnity(int dim1){
    	double[][] unit = new double[dim1][dim1];
    	zero = new Matrix(unit);
    	for(int i = 0; i < dim1; i++){
    		unit[i][i] = 1.0;
    	}
    	unity = new Matrix(unit);
    }
    
    public static double[][] generateRandomMatrix (int i){
        double[][] mats = new double[i][i];
        for(int j = 0; j < i; j++){
            for(int k = j + 1; k < i; k++){
                if(k == j + 1){
                    mats[j][k] = Calpha;
                    mats[k][j] = Calpha;
                }
                else{
                    Random rand1 = new Random();
                    Random rand2 = new Random();
                    int intval1 = rand1.nextInt(10);
                    int intval2 = rand2.nextInt(10);
                    double val = ((double) intval1) + ((double) intval2)*0.1;
                    mats[j][k] = val;
                    mats[k][j] = val;
                }
            }
        }
        return mats;
    }
    
    public static double[][] generateContactMatrix (Individuals in){
    	HashSet<Pair<Integer>> contact_set = in.getHashSet();
    	int size = in.getSequence().length();
    	double[][] matrix = new double[size][size];
    	Iterator<Pair<Integer>> it = contact_set.iterator();
    	while(it.hasNext()){
    		Pair<Integer> pair = it.next();
    		int f_val = pair.getFirst().intValue() - 1, s_val = pair.getSecond().intValue() - 1;
    		matrix[f_val][s_val] = 1.0; matrix[s_val][f_val] = 1.0;
    	}
    	return matrix;
    }

    public static void main(String[] args) throws IOException, SQLException, PdbCodeNotFoundException, PdbLoadError{
    	String dir = "/project/StruPPi/gabriel/Arbeiten/run_051109/1sha/Starter/starter01sha0-34.indi";
        DistMatrix dis = new DistMatrix(dir);
        System.out.println(dis.toString() + "Eigenvalue #1: " + dis.get3GreatestEigs()[0] + "\nEigenvalue #2: " + dis.get3GreatestEigs()[1] + "\nEigenvalue #3: " + dis.get3GreatestEigs()[2] + "\n");
        //generateContactMatrix(new Individuals(dir)).it;
    }
}

