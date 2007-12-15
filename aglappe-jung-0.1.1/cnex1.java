import tools.MySQLConnection;

import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

public class cnex1 {

	/**
	 * CN2 size x sumdelta 
	 * iterating over all steps by exchange of one neighbor at a time by a common neighbor 
	 * with subsequent scoring 
	 * @author lappe
	 */
	static int maxRank = 21; // value to replace for non-existence of central redue in the resultvector (rank=0) 
	// higher values should penalize non-existence more
	static int VL=1; // Verbosity Level 
	static String user = "lappe"	; // change user name!!
	static MySQLConnection conn;
	static String backgrndDB = "pdb_reps_graph_4_2"; 
	static String targetNodes = "target_node";
	static String targetEdges = "target_edge";
	static double lastEntropy=0.0, lastFreq, lastAUC, lastavgk, lastdevk, lastCorr; 
	static int lastRank, lastTotal, lastxcn=0;  
	static int graphid=0, resnr=0; 
	static int size1=0, size2=0; // the dimensions of the matrices = |shell1|x|shell2| = nr of direct(1) x indirect(2) nbs 
	static String rsideline[], rheadline[]={" -->\t(-)1st shell\t0"," |\t        \t-"," |\t(+)     \t0"," V\t2nd shell\t-", "  \t        \tX"};
	static String sideline[], headline[]={"\t\t0","\t\t-","\t\t0","\t\t-","\t\tX"};
	static String restype="?", ressec="?", newnbhood=""; 
	static int cn1[], cn2[], matsize[][], oCN1[], oCN2[]; 
	static int sumdelta[][], rank[][], total[][], cnall[][];
	static double cnsize[][], correct[][], entropy[][], freq[][], AUC[][]; 
	static String newnbs[][], nbstring[][], moveset[][];  
	
	static int printRank=1, 
	printTotal   = 2, 
	printEntropy = 3, 
	printFreq    = 4,    
	printAUC     = 5, 
	printNbstring= 6, 
	printMoveset = 7,
	printdeltaRank=8,
	printCNSize   =9, 
	printCNSxdelta=10, 
	printMatSize  =11,
	printCNSdMSxdelta=12,   
	printCorrect  = 13,  
	printCordMSxdelta=14,
	printoCN1xoCN2 =15,
	printCNdelta=16;
	
	public static void main(String[] args) {
		
	    if (args.length<2){
			System.err.println("The graph_id and residue-nr. needs to be given .... i.e. 7 110");
			System.exit(1);
		}		
		graphid = Integer.parseInt( args[0]);
		resnr = Integer.parseInt( args[1]); 
		int j_num=0, oj_num=0, oj_shell, oj_cnsize, i, j, x, oi, oj, o1, o2, ixnum, jxnum, score=0;
		boolean overx = false;  
		String sql, oj_res, oj_sec;  
		Statement mstmt, mjst, nstmt;  
		ResultSet mrsst, nrsst;
		
		try {
			conn = new MySQLConnection("white",user,"nieve", backgrndDB); // the UPPERCASE DB!  
			System.out.println("Scoring Target neighborhoods v.0.4. "); 

			sql = "select num, res, sstype from "+targetNodes+" where graph_id="+graphid+" and num="+resnr+";"; 
			mstmt = conn.createStatement();
			mrsst = mstmt.executeQuery(sql); 
			if (mrsst.next()) {
			    // this is the central node -> get type and secondary structure 
				restype =  mrsst.getString( 2).toUpperCase(); 
				ressec  =  mrsst.getString( 3).toUpperCase();
			} // end if central residue
			mrsst.close();
			mstmt.close(); 
			System.out.println("GraphID "+graphid+" Central residue is "+restype+":"+resnr+":"+ressec); 
			
//			retrieve the original nbhood into orig_shell
			System.out.println("retrieving original first shell ... "); 
			mstmt = conn.createStatement();
			mstmt.executeUpdate("drop table if exists orig_shell;"); 
			mstmt.close(); 
	
			mstmt = conn.createStatement();
			mstmt.executeUpdate("create table orig_shell as select i_num, i_res, j_num, j_res, j_sstype, 1 as shell from "+targetEdges+" where graph_id="+graphid+" and i_num="+resnr+";");
			mstmt.close(); 
	
			System.out.println("adding the original 2nd shell ...");  
			sql = "select j_num from orig_shell where shell=1;";
			mstmt = conn.createStatement();
			mrsst = mstmt.executeQuery(sql);
			i=0; 
			while (mrsst.next()) {
				i++; 
				oj_num = mrsst.getInt(1);
				System.out.println(i+":"+oj_num); 
				mjst = conn.createStatement();
				sql = "insert into orig_shell select i_num, i_res, j_num, j_res, j_sstype, 2 as shell from "+targetEdges+" where graph_id="+graphid+" and i_num="+oj_num+";";
				// System.out.println(">"+sql); 
				mjst.executeUpdate( sql); 
				mjst.close(); 
			} // end while 
			mrsst.close(); 
			mstmt.close(); 

			System.out.println("gathering the original 1st and 2nd shell nbs.");  
			sql = "select j_num, j_res, j_sstype, min(shell) as shell, count(*) as cn from orig_shell where j_num!="+resnr+" group by j_num order by j_num;";
			mstmt = conn.createStatement();
			mrsst = mstmt.executeQuery(sql);
			o1=0; 
			o2=0; 		
			while (mrsst.next()) {
				if ( mrsst.getInt( 4)==1) { // count 1st shell entry 
					o1++;
					System.out.print("1#"+o1);
					rheadline[0]+="\t"+o1; 
					rheadline[1]+="\t"+mrsst.getString(2); // res
					rheadline[2]+="\t"+mrsst.getInt(1); // resnum 
					rheadline[3]+="\t"+mrsst.getString(3); // SStype
					rheadline[4]+="\t("+(mrsst.getInt(5)-1)+")"; // CN 
				} // end if 2st shell 
				if ( mrsst.getInt( 4)==2) { // count 2nd shell entry 
					o2++;
					System.out.print("2#"+o2);
				} // end if 2nd shell 
				System.out.println(" :\t"+mrsst.getInt( 1)+"\t"+mrsst.getString( 2)+"\t"+mrsst.getString( 3)+"\t"+mrsst.getInt( 4)+"\t"+mrsst.getInt( 5)); 
			} // end while
			System.out.println("Orig.SIZE 1st shell "+o1); 
			System.out.println("Orig.SIZE 2nd shell "+o2);
			rheadline[4] = rheadline[4].replace("X", ("("+o1)+")");
			rsideline = new String[o2+1];
			rsideline[0]="+0\tRnum:S(cn)"; 
			sumdelta = new int[(o1+1)][(o2+1)];
			newnbs = new String[(o1+1)][(o2+1)];
			cnall = new int[(o1+1)][(o2+1)];
			matsize = new int[(o1+1)][(o2+1)];
			correct = new double[(o1+1)][(o2+1)];
			
			
			oCN1 = new int[(o1+1)];
			oCN2 = new int[(o2+1)];
			oCN1[0]=o1; 
			oCN2[0]=o1;
			mrsst.beforeFirst();
			o1=0; 
			o2=0; 					
			while (mrsst.next()) {
				if ( mrsst.getInt( 4)==1) { // 1st shell entry 
					o1++;
					oCN1[(o1)]=mrsst.getInt(5)-1; // CN 
				} // end if 2st shell 
				if ( mrsst.getInt( 4)==2) { // 2nd shell entry 
					o2++;
					oCN2[(o2)]=mrsst.getInt(5);
				} // end if 2nd shell 
			} // end while
			
			// creating the perturbed version of shell 1 into temp_shell
			for (j=0; j<=o2; j++) { // <=o2 outer loop through all originally indirect contacts
				
				for (i=0; i<=o1; i++) { // inner loop through all originally direct contacts
					if (VL>=1) {
						System.out.println("---------------------------------------------"); 
						System.out.println("Creating perturbed nbhood ("+i+","+j+")\t");
					}
//					clear first 
					nstmt = conn.createStatement();
					nstmt.executeUpdate("drop table if exists temp_shell;"); 
					nstmt.close(); 
					nstmt = conn.createStatement();
					nstmt.executeUpdate("create table temp_shell select * from orig_shell limit 0;"); 
					nstmt.close();
					oi = 0; 
					oj = 0; 
					mrsst.beforeFirst();
					newnbhood=""; 
					overx = false;
					ixnum=0; 
					jxnum=0; 
					while (mrsst.next()) {
						oj_num = mrsst.getInt( 1); 
						oj_res = mrsst.getString(2);
						oj_sec = mrsst.getString(3);
						oj_shell = mrsst.getInt( 4);
						oj_cnsize = mrsst.getInt( 5);
						if (oj_num>resnr) { // we are over x 
							if (!overx) {
								newnbhood+="x"; 
								overx=true; 
							} // end if over x 
						} // END IF J > X 
						if (oj_shell==1) { // a direct 1st shell neighbour 
							oi++;
							if (oi!=i) {// if this is NOT the one direct nb 2B dropped
								// include as 1st shell nbor into temp_shell 	
								nstmt = conn.createStatement();
								sql = "insert into temp_shell values("+resnr+",\'"+restype+"\',"+oj_num+",\'"+oj_res+"\',\'"+oj_sec+"\', 1);"; 
								// System.out.println("oi>"+ sql); 
								nstmt.executeUpdate(sql);
								nstmt.close(); 	
								newnbhood+=oj_res;
							} else {	
								ixnum=oj_num; 
							} // end if ni!=i 
						} else { // 2nd shell neighbour 
							oj++; 
							if (oj==j) { // this is the 2nd shell nb 2B included
								// put as new 1st shell nbor 
								nstmt = conn.createStatement();
								sql = "insert into temp_shell values("+resnr+",\'"+restype+"\',"+oj_num+",\'"+oj_res+"\',\'"+oj_sec+"\', 1);"; 
								// System.out.println("oj>"+ sql);
								nstmt.executeUpdate(sql);
								nstmt.close(); 		
								newnbhood+=oj_res;
								jxnum=oj_num; 
							} // end if
							if (j==0) { // creating the sideline ruler array for the output 
								rsideline[oj] = "+"+oj+"\t"+oj_res+""+oj_num+":"+oj_sec+"("+oj_cnsize+")";
							} // end if j==0 
						} // end if 1st/2nd shell
						
					} // end while through the entire nbhood
					if (!overx) { // we haven't seen a nb > x yet 
						newnbhood+="x"; // x sits at the end of the nbhoodstring 
						overx=true; 
					} // end if over x 
					// System.out.println("new direct nbhood "+newnbhood); 
					// Now the "updated" / perturbed version of shell 1 is in temp_shell
					// we can build 2nd shell accordingly. 
					// System.out.println("building the 2nd shell");  
					sql = "select j_num, j_res, j_sstype from temp_shell where shell=1;";
					nstmt = conn.createStatement();
					nrsst = nstmt.executeQuery(sql);
					x = 0; 
					while (nrsst.next()) {
						x++;
						j_num = nrsst.getInt( 1); 
						// System.out.println(x+":"+nrsst.getString( 2)+" "+j_num+" "+nrsst.getString( 3)); 
						mjst = conn.createStatement();
						sql = "insert into temp_shell select i_num, i_res, j_num, j_res, j_sstype, 2 as shell from "+targetEdges+" where graph_id="+graphid+" and i_num="+j_num+";";
						// System.out.println(">"+sql); 
						mjst.executeUpdate( sql); 
						mjst.close(); 
					} // end while 
					nrsst.close(); 
					nstmt.close(); 
					// and score this move
					lastxcn=0; 
					newnbs[i][j]= newnbhood; 
					if (VL>=1) System.out.print("\n["+i+"]["+j+"]\t-"+ixnum+"/+"+jxnum+"\t"+newnbs[i][j]+"\t ");
					score = scoreCurrentNbhood( ixnum, jxnum);
					if (VL>=1) System.out.print( lastRank+"\t ");
					sumdelta[i][j] = score;
					cnall[i][j] = lastxcn;
					matsize[i][j] = (size1*size2)-1;
					correct[i][j] = lastCorr; 
					if (VL>=1) {
						//reportMatrix( printNbstring ); 	
						reportMatrix( printRank );
						reportMatrix( printdeltaRank );
						reportMatrix( printCNSize );
						reportMatrix( printCNSxdelta); 
						System.out.println("SumDeltaRank Score = \t"+score);
						System.out.println("corrected Score    = \t"+correct[i][j]);
						System.out.println("Matrix size n1*n2-1= \t("+size1+"*"+size2+")-1 ="+matsize[i][j]);
						System.out.println("CN1 x CN2 product  = \t"+cnall[i][j]);
					} else {
						System.out.print("\t"+score+"*"+cnall[i][j]+"\t= "+(score*cnall[i][j]));
					}
					
				} // next i
				System.out.println("\t"); 
			} // next j 
			// report total matrix sumdelta
			// if (VL>=1) {
				System.out.println("GraphID "+graphid+" Central residue is "+restype+":"+resnr+":"+ressec); 
				System.out.println("backgroundDB"+backgrndDB+" \t maxRank : "+maxRank);
				reportResults( o1, o2, printRank); 
				reportResults( o1, o2, printCNSize);
				reportResults( o1, o2, printoCN1xoCN2);
				reportResults( o1, o2, printCNdelta); 
				reportResults( o1, o2, printMatSize);
			//}
			reportResults( o1, o2, printCNSxdelta );
			reportResults( o1, o2, printCNSdMSxdelta ); 
			reportResults( o1, o2, printCorrect);    
			reportResults( o1, o2, printCordMSxdelta);
			// Cleanup ... 
			mrsst.close(); 
			mstmt.close(); 
			
		} catch (SQLException e) {
			e.printStackTrace();
			System.err.println("SQLException: " + e.getMessage());
			System.err.println("SQLState:     " + e.getSQLState());
		} // end try/catch 
		System.out.println("fin."); 
	}	// end main 

	

	public static void reportResults( int o1, int o2, int what2print) {
		System.out.println("Printing "+what2print); 
		if (what2print==printRank) System.out.println("Overall resulting SumDeltaRank Matrix" ); 
		if (what2print==printNbstring) System.out.println("Overall tested new nbhoodStrings" ); 
		if (what2print==printCNSize) System.out.println("Overall cnsize" ); 
		if (what2print==printCNSxdelta) System.out.println("Overall (cnsize*sumDeltaRank)" ); 
		if (what2print==printMatSize) System.out.println( "Overall matsize[i][j]" );
		if (what2print==printCNSdMSxdelta) System.out.println( "Overall sumdelta[i][j]*cnall[i][j]/matsize[i][j]" );
		if (what2print==printCorrect) System.out.println( "Overall correct[i][j]" );
		if (what2print==printCordMSxdelta) System.out.println( "Overall correct[i][j]*cnall[i][j]/matsize[i][j]" );
		if (what2print==printoCN1xoCN2) System.out.println( "Overall oCN1[i]*oCN2[j]" ); 
		if (what2print==printCNdelta) System.out.println( "Overall (oCN1[i]*oCN2[j])-cnall[i][j]" );	
		
		// print headerline(s)
		System.out.println(rheadline[0]);
		System.out.println(rheadline[1]);
		System.out.println(rheadline[2]);
		System.out.println(rheadline[3]);
		System.out.println(rheadline[4]);
		for (int j=0; j<=o2; j++) {	
			// print rsideline
			System.out.print( rsideline[j]+"\t"); 
			for ( int i=0; i<=o1; i++) {
				if (what2print==printRank) System.out.print( sumdelta[i][j] ); 
				if (what2print==printNbstring) System.out.print( newnbs[i][j] );
				if (what2print==printCNSize) System.out.print( cnall[i][j] ); 
				if (what2print==printCNSxdelta) System.out.print( sumdelta[i][j]*cnall[i][j] );
				if (what2print==printMatSize) System.out.print( matsize[i][j] );
				if (what2print==printCNSdMSxdelta) System.out.print( sumdelta[i][j]*cnall[i][j]/matsize[i][j] );
				if (what2print==printCorrect) System.out.print( String.format("%.3f", correct[i][j]) ); 
				if (what2print==printCordMSxdelta) System.out.print( String.format("%.3f", correct[i][j]*cnall[i][j]/matsize[i][j]) );
				if (what2print==printoCN1xoCN2) System.out.print( oCN1[i]*oCN2[j]); 
				if (what2print==printCNdelta) System.out.print( (oCN1[i]*oCN2[j])-cnall[i][j]);	
				System.out.print("\t"); 
			} // next i 
			System.out.println(""); 
		} // next j  	
	} // end of reportResults  
	

	
	public static int scoreCurrentNbhood( int ixnum, int jxnum) {
		int ixcn=0, jxcn=0, n1=0, n2=0, ni, nj, i, j, j_num, j_shell, j_cnsize, sumdeltarank=0;
		String sql, j_res, j_sec, nbs, mymove, precol;
		boolean overx = false;
		lastCorr = 0.0; 
		Statement stmt;  
		ResultSet rsst;
		
		try {
			headline[0]="\t\t\t0";
			headline[1]="\t\t\t-";
			headline[2]="\t\t\t0";
			headline[3]="\t\t\t-";
			headline[4]="\t\t\tX";
	
			// System.out.println("retrieving the entire nbhood (1st and 2nd shell)");  
			sql = "select j_num, j_res, j_sstype, min(shell) as shell, count(*) as cn from temp_shell where j_num!="+resnr+" group by j_num order by j_num;";
			stmt = conn.createStatement();
			rsst = stmt.executeQuery(sql);
			// counting shell2
			n2=0; 		
			while (rsst.next()) {
				if ( rsst.getInt( 4)==1) { // count 1st shell entry 
					n1++;
					// System.out.print("1#"+n1);
					headline[0]+="\t"+n1; 
					headline[1]+="\t"+rsst.getString(2); // res
					headline[2]+="\t"+rsst.getInt(1); // resnum 
					headline[3]+="\t"+rsst.getString(3); // SStype
					headline[4]+="\t("+((rsst.getInt(5))-1)+")"; // CNSize
				} // end if 2st shell 
				if ( rsst.getInt( 4)==2) { // count 2nd shell entry 
					n2++;
					// System.out.print("2#"+n2);
				} // end if 2nd shell 
				// System.out.println(" :\t"+rsst.getInt( 1)+"\t"+rsst.getString( 2)+"\t"+rsst.getString( 3)+"\t"+rsst.getInt( 4)+"\t"+rsst.getInt( 5)); 
			} // end while
			size1 = n1; 
			size2 = n2;
			ixcn = n1; 
			jxcn = n1;
			if (VL>=1) {
				System.out.println("|1st shell|="+size1+" \tx\t |2nd shell|="+size2);
			}
			headline[4]=headline[4].replace("X",("("+size1+")"));
			
			// n1 and n2 are known, initialise matrices accordingly. 
			// nbhood, move, rank, entropy, freq, AUC etc. (evtl.+ degree(?)) 
			rank = new int[(n1+1)][(n2+1)];
			rank[0][0]=maxRank;
			total = new int[(n1+1)][(n2+1)];
			entropy = new double[(n1+1)][(n2+1)];
			freq = new double[(n1+1)][(n2+1)];        
			AUC = new double[(n1+1)][(n2+1)];
			nbstring = new String[(n1+1)][(n2+1)];
			moveset = new String[(n1+1)][(n2+1)];
			sideline = new String[n2+1];
			cn1 = new int[n1+1]; 
			cn2 = new int[n2+1]; 
			cnsize = new double[(n1+1)][(n2+1)];
  
			for (j=0; j<=n2; j++) { // outer loop through all indirect contacts
				for (i=0; i<=n1; i++) { // inner loop through all direct contacts
					mymove = "";
					overx = false;
					if (VL>=1) {
						System.out.print("("+i+","+j+")\t");
					}
					ni = 0; 
					nj = 0; 
					sideline[0]="+0\tRnum:S("+n1+")";
					cn1[0]=n1; 
					cn2[0]=n1; 
					nbs="%";
					rsst.beforeFirst();
					while (rsst.next()) {
						j_num = rsst.getInt(1); 
						j_res = rsst.getString(2);
						j_sec = rsst.getString(3);
						j_shell = rsst.getInt(4);
						j_cnsize = rsst.getInt(5);
						
						// if edge exists between the dropped direct and the new (ind.)nbour then cnsize of dropped+1 
						
						if (j_num>resnr) { // we are over x 
							if (!overx) {
								nbs+="x%"; 
								overx=true; 
							} // end if over x 
						} // END IF J > X 
						if (j_shell==1) { // a direct 1st shell neighbour 
							ni++;
							if (ni!=i) {// if this is NOT the one direct nb 2B dropped 
								nbs+=j_res.toUpperCase()+"%"; // it is included
								if ( j_num==jxnum && j==0) { // This is the direct nb dropped
									jxcn=j_cnsize;
									if (VL>=2) System.out.print("(j"+jxnum+":"+jxcn+")");
								}
							} else { // this one IS dropped 
								mymove += "(-"+j_res+":"+j_num+":"+j_sec+"/"+j_cnsize+")";
								cn1[ni]=j_cnsize; 
							} // end if ni!=i 
							
						} else { // 2nd shell neighbour 
							nj++; 
							if (nj==j) { // this is the 2nd shell nb 2B included
								nbs+=j_res.toUpperCase()+"%";
								mymove += "(+"+j_res+":"+j_num+":"+j_sec+"/"+j_cnsize+")";
								if ( j_num==ixnum && i==0) { // This is the dropped direct nb, no 2b found in 2ns shell 
									ixcn=j_cnsize;
									if (VL>=2) System.out.print("(i"+ixnum+":"+ixcn+")");
								}
								cn2[nj] = j_cnsize; 
							} // end if 
							
//							// only once for building the sidelines
							if (j==0) {
								sideline[nj] = "+"+nj+"\t"+j_res+""+j_num+":"+j_sec+"("+j_cnsize+")";
								// was here: cn2[nj] = j_cnsize; 
							} // end if sideline 						
						} // end if 1st/2nd shell
						
					} // end while through the entire nbhood
					
					if (!overx) { // in case x is the very last we haven't seen it yet  
						nbs+="x%"; // add it in the end 
						overx=true; 
					} // end if over x
					if (VL>=1) {
						System.out.print("("+nbs+")\t");
					}
					nbstring[i][j] = nbs;
					moveset[i][j] = mymove;
					if (VL>=2) System.out.print(" "+mymove);
					precol = nbstring[i][0]; 
					getEntropy( nbs, restype, precol);
					if (lastRank==0) lastRank = maxRank;
					rank[i][j] = lastRank;
					entropy[i][j] = lastEntropy; 
					freq[i][j] = lastFreq;
					AUC[i][j]  = lastAUC;
					total[i][j]= lastTotal; 
					cnsize[i][j]=(double) ((double)cn1[i])/((double)cn2[j]);
					if (VL>=2) System.out.print("= "+cn1[i]+"/"+cn2[j]+"="+String.format("%.3f",cnsize[i][j])+"\t");					
										
					if (lastRank > 0) { 
						sumdeltarank += ( (lastRank-rank[0][0]) );
						lastCorr += ( (lastRank-rank[0][0])*cnsize[i][j] );
					} else {
						sumdeltarank += ( (maxRank-rank[0][0]) );
						lastCorr += ( (maxRank-rank[0][0])*cnsize[i][j] );
					} // end if lastRank was defined 
					
					if (VL>=2) System.out.print(""+lastRank+"\t");
				} // close inner loop (i)
				if (VL>=1) {
				   System.out.println(".");
				} else {
					System.out.print(".");
				} 
			} // next outerloop (j)
			lastxcn=(ixcn*jxcn);
			if (VL>=1) {
				System.out.println("lastxcn=(ixcn*jxcn)=("+ixcn+"*"+jxcn+")="+lastxcn);
			}
			rsst.close(); 
			stmt.close(); 
			
		} catch (SQLException e) {
			e.printStackTrace();
			System.err.println("SQLException: " + e.getMessage());
			System.err.println("SQLState:     " + e.getSQLState());
		} // end try/catch 
		// System.out.println("fin.");
		return sumdeltarank; 
	}	// end scoreCurrentNbhood

	public static boolean edgeExists( int u_num, int v_num) {
		int c=0;
		String sql;
		boolean found = false;  
		Statement st;  
		ResultSet rs;
		try {
			sql = "select count(*) from temp_shell where i_num="+u_num+" and j_num="+v_num+";";
			st = conn.createStatement();
			rs = st.executeQuery(sql);
			if (rs.next()) c = rs.getInt(1); 
			found = (c>0); 
			rs.close();
			st.close(); 
		} catch (SQLException e) {
			e.printStackTrace();
			System.err.println("SQLException: " + e.getMessage());
			System.err.println("SQLState:     " + e.getSQLState());
		} // end try/catch 
		return found; 
	} // end edgeExists( u_num, v_num)

	public static void reportMatrix( int what2print) {
		System.out.println("\nPrinting "+what2print); 
		if (what2print==printRank) System.out.print("rank[i][j]" ); 
		if (what2print==printTotal) System.out.print("total[i][j]" ); 
		if (what2print==printEntropy) System.out.print("entropy[i][j]" );
		if (what2print==printFreq ) System.out.print("freq[i][j]" ); 
		if (what2print==printAUC) System.out.print("AUC[i][j]" ); 
		if (what2print==printNbstring) System.out.print("nbstring[i][j]" ); 
		if (what2print==printMoveset) System.out.print("moveset[i][j]" );
		if (what2print==printdeltaRank) System.out.print("rank[i][j]-rank[0][0]" );
		if (what2print==printCNSize) System.out.print("cnsize[i][j]" );
		if (what2print==printCNSxdelta) System.out.print("cnsize[i][j]*(rank[i][j]-rank[0][0])" );
		
		System.out.println("..."); 
		// print headerline(s)
		System.out.println(headline[0]);
		System.out.println(headline[1]);
		System.out.println(headline[2]);
		System.out.println(headline[3]);
		System.out.println(headline[4]);
		for (int j=0; j<=size2; j++) {	
			// print sideline
			System.out.print( sideline[j]+"\t"); 
			for ( int i=0; i<=size1; i++) {
				if (what2print==printRank) System.out.print( rank[i][j] ); 
				if (what2print==printTotal) System.out.print( total[i][j] ); 
				if (what2print==printEntropy) System.out.print( entropy[i][j] );
				if (what2print==printFreq ) System.out.print( freq[i][j] ); 
				if (what2print==printAUC) System.out.print( AUC[i][j] ); 
				if (what2print==printNbstring) System.out.print( nbstring[i][j] ); 
				if (what2print==printMoveset) System.out.print( moveset[i][j] ); 
				if (what2print==printdeltaRank) System.out.print( rank[i][j]-rank[0][0] );
				if (what2print==printCNSize) System.out.print( String.format("%.3f",cnsize[i][j]) );
				if (what2print==printCNSxdelta) System.out.print( String.format("%.3f", (cnsize[i][j]*(rank[i][j]-rank[0][0]))) ); 
				System.out.print("\t"); 
			} // next i 
			System.out.println(""); 
		} // next j  	
	} // end of report 
	
	public static void getEntropy( String nbs, String centRes, String predec) {
		String sql, res, this_n, prec_n; 
		Statement stmt;  
		ResultSet rsst;
		double p, psum=0.0, logp, plogp, plogpsum=0.0;
		int counter=0, c=0, lastc=0; 
		try {
			// Hashing first row tables comes first
			if (VL>=2) {
				System.out.println("getEntropy for ");
				System.out.println("nbs    : "+nbs); 
				System.out.println("centRes: "+centRes);
				System.out.println("predec : "+predec);
			}
			this_n = nbs.replace("%",""); 
			prec_n = predec.replace("%","");
			if (VL>=2) System.out.println("this_n: ["+this_n+"]"); 
			if (VL>=2) System.out.println("prec_n: ["+prec_n+"]");
			if (prec_n.equals(this_n)) {
				if (VL>=2) System.out.println("have to create db for this "+prec_n);
				sql = "create table IF NOT EXISTS nbhashtables."+prec_n+" as select res, n, k from single_model_node where n like '"+nbs+"';"; 
				if (VL>=2) System.out.println(" >> "+sql); 
				stmt = conn.createStatement();
				stmt.executeUpdate( sql); 
				stmt.close(); 
			} else if (VL>=2) System.out.println("using preceding db of "+prec_n); 
			
			// now we can safely derive the estimates from the hashtable
			sql = "select count(*) from nbhashtables."+prec_n+" where n like '"+nbs+"';";
			if (VL>=2) System.out.println( sql); 
			stmt = conn.createStatement();
			rsst = stmt.executeQuery(sql);
			if (rsst.next()) lastTotal = rsst.getInt( 1); 
			rsst.close(); 
			stmt.close(); 
			
			sql = "select res, count(*) as t, count(*)/"+lastTotal+" as p, avg( k), stddev( k) from nbhashtables."+prec_n+" where n like '"+nbs+"' group by res order by p DESC;";
			stmt = conn.createStatement();
			rsst = stmt.executeQuery(sql);
			if (VL>=2) System.out.println("rank : res : total t : fraction p : log2(p) : -p*log2(p)");
			int rank = 0; 
			boolean seenCentRes = false;  
			lastAUC = 0.0; 
			lastRank = 0; 
			lastFreq = 0.0;
			lastavgk = 0.0;
			lastdevk = 0.0;
			while (rsst.next()) {	
				counter++; 
				res = rsst.getString(1); // 1st column -- res
				c = rsst.getInt( 2); // 2nds column : count/residue 
				p = rsst.getDouble(3); // 3rd: fraction p 
				if (VL>=2) System.out.print(counter+"/"+rank+ " : " + res+"   : "+c+ " : " + p);
				logp = Math.log(p)/Math.log(2.0); // to basis 2 for info in bits 
				if (VL>=2) System.out.print(" : " + logp); 
				plogp = -1.0 * p * logp;  
				if (VL>=2) System.out.print(" : " + plogp);
				plogpsum += plogp;  
				psum += p; 
				
				if ((c == lastc) && (lastc>0)) { // tie 
					if (VL>=2) System.out.print(" <-- TIE!");
					lastRank = counter; 		
				} // end if 
					
				if (res.equals(centRes)) { 
					if (VL>=2) System.out.print(" <== " + centRes);
					seenCentRes = true;
					lastFreq = p;
					lastRank = counter; 
					lastavgk = rsst.getDouble(4);
					lastdevk = rsst.getDouble(5);
					lastc = c;
				}
				if (seenCentRes) lastAUC += p;
	
				if (VL>=2) System.out.println("");
			}
			if (VL>=2) System.out.println("Sum :"+lastTotal+"       : "+psum+"       : "+plogpsum);
			if (VL>=2) System.out.println("=> rank "+lastRank); 
			rsst.close(); 
			stmt.close(); 
			lastEntropy = plogpsum; 
			if (lastRank==0) lastRank = maxRank;  
		} catch (SQLException e) {
			e.printStackTrace();
			System.err.println("SQLException: " + e.getMessage());
			System.err.println("SQLState:     " + e.getSQLState());
		}
		
	} // end of getEntropy 

} // end class 
