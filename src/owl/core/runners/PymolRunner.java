package owl.core.runners;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import owl.core.structure.ChainInterface;
import owl.core.structure.ChainInterfaceList;
import owl.core.structure.InterfaceRimCore;
import owl.core.structure.PdbChain;
import owl.core.structure.PdbLoadException;
import owl.core.structure.PdbfileParser;
import owl.core.structure.Residue;

public class PymolRunner {
	
	/**
	 * We use 26 colors corresponding to chain letters A to Z (second 13 are repeated from first 13)
	 */
	private static final String[] DEF_CHAIN_COLORS = 
	{"green","cyan","yellow","white","lightblue","magenta","red","orange","wheat","limon","salmon","palegreen","lightorange",
	 "green","cyan","yellow","white","lightblue","magenta","red","orange","wheat","limon","salmon","palegreen","lightorange",};
	
	private static final String DEF_SYM_RELATED_CHAIN_COLOR = "grey";
	
	private static final String[] DEF_CHAIN_COLORS_ASU_ALLINTERFACES = 
		{"green", "tv_green", "chartreuse", "splitpea", "smudge", "palegreen", "limegreen", "lime", "limon", "forest"};
	
	private static final String DEF_TN_STYLE = "cartoon";
	private static final String DEF_TN_BG_COLOR = "white";
	private static final int[] DEF_TN_HEIGHTS = {75};
	private static final int[] DEF_TN_WIDTHS = {75};
	
	private static final double MIN_INTERF_AREA_TO_DISPLAY = 500;
	
	private File pymolExec;
	private String[] chainColors;
	private String symRelatedColor;
	private String interf1color;
	private String interf2color;
	
	private HashMap<String, String> colorMappings;
	
	public PymolRunner(File pymolExec) {
		this.pymolExec = pymolExec;
		chainColors = DEF_CHAIN_COLORS;
		symRelatedColor = DEF_SYM_RELATED_CHAIN_COLOR;
	}
	
	public void setColors(String[] chainColors, String symRelatedColor) {
		this.chainColors = chainColors;
		this.symRelatedColor = symRelatedColor;
	}
	
	/**
	 * Generates png images of the desired heights and widths with the specified style and 
	 * coloring each chain with a color as set in {@link #setColors(String[], String)}
	 * @param pdbFile
	 * @param outPngFiles output png file names
	 * @param style can be cartoon, surface, spheres
	 * @param bgColor the background color for the image: black, white, gray
	 * @param heights
	 * @param widths 
	 * @throws IOException 
	 * @throws InterruptedException 
	 * @throws PdbLoadException 
	 * @throws IllegalArgumentException if heights length differs from widhts length
	 */
	public void generatePng(File pdbFile, File[] outPngFiles, String style, String bgColor, int[] heights, int[] widths) 
	throws PdbLoadException, IOException, InterruptedException {
		
		if (heights.length!=widths.length || heights.length!=outPngFiles.length) 
			throw new IllegalArgumentException("The number of heights is different from the number of widths or the number of output png files");
		String molecName = pdbFile.getName().substring(0, pdbFile.getName().lastIndexOf('.'));
		PdbfileParser parser = new PdbfileParser(pdbFile.getAbsolutePath());
		String[] chains = parser.getChains();

		List<String> command = new ArrayList<String>();
		command.add(pymolExec.getAbsolutePath());
		command.add("-q");
		command.add("-c");

		StringBuffer pymolScriptBuilder = new StringBuffer();
		
		pymolScriptBuilder.append("load "+pdbFile.getAbsolutePath() + ";");
		
		pymolScriptBuilder.append("bg "+bgColor + ";");
	
		pymolScriptBuilder.append("orient;");

		pymolScriptBuilder.append("remove solvent;");
		
		pymolScriptBuilder.append("as "+style + ";");
		for (int c=0;c<chains.length;c++) {
			char letter = chains[c].charAt(0);
			String color = null;
			if (letter<'A' || letter>'Z') {
				// if out of the range A-Z then we assign simply a color based on the chain index
				color = chainColors[c%chainColors.length];
			} else {
				// A-Z correspond to ASCII codes 65 to 90. The letter ascii code modulo 65 gives an indexing of 0 (A) to 25 (Z)
				// a given letter will always get the same color assigned
				color = chainColors[letter%65];	
			}
			pymolScriptBuilder.append("color "+color+", "+molecName+" and chain "+letter + ";");
		}

		for (int i=0;i<heights.length;i++) {
			pymolScriptBuilder.append("viewport "+heights[i]+","+widths[i] + ";");
			
			pymolScriptBuilder.append("ray;");
			
			pymolScriptBuilder.append("png "+outPngFiles[i].getAbsolutePath() + ";");
		}
		
		pymolScriptBuilder.append("quit;");
		
		command.add("-d");
		command.add(pymolScriptBuilder.toString());
		
		Process pymolProcess = new ProcessBuilder(command).start();
		int exit = pymolProcess.waitFor();
		if (exit!=0) {
			throw new IOException("Pymol exited with error status "+exit);
		}
	}
	
	/**
	 * Generates png file, pymol pse file and pml script for given interface producing a 
	 * mixed cartoon/surface representation of interface with selections 
	 * coloring each chain with a color as set in {@link #setColors(String[], String)} and 
	 * through {@link #readColorsFromPropertiesFile(InputStream)}
	 * @param pymolExec
	 * @param interf
	 * @param pdbFile
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public void generateInterfPngPsePml(ChainInterface interf, double caCutoff, File pdbFile, File pseFile, File pmlFile, String base) 
	throws IOException, InterruptedException {
		
		String molecName = getPymolMolecName(pdbFile);

		File[] pngFiles = new File[DEF_TN_HEIGHTS.length];
		for (int i=0;i<DEF_TN_HEIGHTS.length;i++) {
			pngFiles[i] = new File(pdbFile.getParent(),base+"."+DEF_TN_WIDTHS[i]+"x"+DEF_TN_HEIGHTS[i]+".png");
		}
		
		char chain1 = interf.getFirstMolecule().getPdbChainCode().charAt(0);
		char chain2 = interf.getSecondPdbChainCodeForOutput().charAt(0);
		
		String color1 = getChainColor(chain1, 0, interf.isSymRelated());
		String color2 = getChainColor(chain2, 1, interf.isSymRelated());
		
		List<String> command = new ArrayList<String>();
		command.add(pymolExec.getAbsolutePath());
		command.add("-q");
		command.add("-c");


		// NOTE we used to pass all commands in one string after -d (with the pymolScriptBuilder StringBuffer.
		//      But pymol 1.3 and 1.4 seem to have problems with very long strings (causing segfaults)
		//      Because of that now we write most commands to pml file (which we were doing anyway so that users can 
		//      use the pml scripts if they want) and then load the pmls with pymol "@" command
		
		
		StringBuffer pymolScriptBuilder = new StringBuffer();
		PrintStream pml = new PrintStream(pmlFile);
		
		pymolScriptBuilder.append("load "+pdbFile.getAbsolutePath()+";");
				
		String cmd;

		cmd = "orient";
		writeCommand(cmd, pml);
		
		cmd = "remove solvent";
		writeCommand(cmd, pml);
		
		cmd = "as cartoon";
		writeCommand(cmd, pml);
		
		cmd = "color "+color1+", "+molecName+" and chain "+chain1;
		writeCommand(cmd, pml);
		cmd = "color "+color2+", "+molecName+" and chain "+chain2;
		writeCommand(cmd, pml);
		
		interf.calcRimAndCore(caCutoff);

		cmd = "select chain"+chain1+", chain "+chain1;
		writeCommand(cmd, pml);
		cmd = "select chain"+chain2+", chain "+chain2;
		writeCommand(cmd, pml);
		
		cmd = getSelString("core", chain1, interf.getFirstRimCore().getCoreResidues());
		writeCommand(cmd, pml);
		cmd = getSelString("core", chain2, interf.getSecondRimCore().getCoreResidues());
		writeCommand(cmd, pml);
		cmd = getSelString("rim", chain1, interf.getFirstRimCore().getRimResidues());
		writeCommand(cmd, pml);
		cmd = getSelString("rim", chain2, interf.getSecondRimCore().getRimResidues());
		writeCommand(cmd, pml);
		
		cmd = "select interface"+chain1+", core"+chain1+" or rim"+chain1;
		writeCommand(cmd, pml);
		cmd = "select interface"+chain2+", core"+chain2+" or rim"+chain2;
		writeCommand(cmd, pml);
		cmd = "select bothinterf , interface"+chain1+" or interface"+chain2;
		writeCommand(cmd, pml);
		// not showing surface anymore, was not so useful 
		//cmd = "show surface, chain "+chain1;
		//writeCommand(cmd, pml);
		//cmd = "show surface, chain "+chain2;
		//writeCommand(cmd, pml);
		//pymolScriptBuilder.append("color blue, core"+chains[0]+";");
		//pymolScriptBuilder.append("color red, rim"+chains[0]+";");
		cmd = "color "+interf1color+", core"+chain1;
		writeCommand(cmd, pml);
		//pymolScriptBuilder.append("color slate, core"+chains[1]+";");
		//pymolScriptBuilder.append("color raspberry, rim"+chains[1]+";");
		cmd = "color "+interf2color+", core"+chain2;
		writeCommand(cmd, pml);
		cmd = "show sticks, bothinterf";
		writeCommand(cmd, pml);
		//cmd = "set transparency, 0.35";
		//writeCommand(cmd, pml);
		//pymolScriptBuilder.append("zoom bothinterf"+";");
		cmd = "select resi 0";// so that the last selection is deactivated
		writeCommand(cmd, pml);
		
		pml.close();
		
		pymolScriptBuilder.append("@ "+pmlFile+";");
		
		pymolScriptBuilder.append("save "+pseFile+";");

		// and now creating the png thumbnail
		pymolScriptBuilder.append("bg "+DEF_TN_BG_COLOR+";");
		
		pymolScriptBuilder.append("as "+DEF_TN_STYLE+";");
		
		pymolScriptBuilder.append("color "+color1+", "+molecName+" and chain "+chain1+";");
		pymolScriptBuilder.append("color "+color2+", "+molecName+" and chain "+chain2+";");
		
		for (int i=0;i<DEF_TN_HEIGHTS.length;i++) {
			pymolScriptBuilder.append("viewport "+DEF_TN_HEIGHTS[i]+","+DEF_TN_WIDTHS[i] + ";");
			
			pymolScriptBuilder.append("ray;");
			
			pymolScriptBuilder.append("png "+pngFiles[i].getAbsolutePath() + ";");
		}
		
		pymolScriptBuilder.append("quit;");
		
		command.add("-d");
		
		//System.out.println(pymolScriptBuilder.toString());
		
		command.add(pymolScriptBuilder.toString());

		
		Process pymolProcess = new ProcessBuilder(command).start();
		int exit = pymolProcess.waitFor();
		if (exit!=0) {
			throw new IOException("Pymol exited with error status "+exit);
		}
	}
	
	public void generateInterfacesPse(File asuPdbFile, Set<String> chains, File pmlFile, File pseFile, File[] interfacePdbFiles, ChainInterfaceList interfaces) 
			throws IOException, InterruptedException {
		
		String molecName = getPymolMolecName(asuPdbFile);
		
		List<String> command = new ArrayList<String>();
		command.add(pymolExec.getAbsolutePath());
		command.add("-q");
		command.add("-c");

		StringBuffer pymolScriptBuilder = new StringBuffer();
		PrintStream pml = new PrintStream(pmlFile);
		
		String cmd;

		
		// loading and coloring
		cmd = "load "+asuPdbFile.getAbsolutePath();
		writeCommand(cmd,pml);
		
		cmd = "show cell";
		writeCommand(cmd,pml);
		
		int i = 0;
		for (String chain:chains) {
			cmd = "color "+DEF_CHAIN_COLORS_ASU_ALLINTERFACES[i%DEF_CHAIN_COLORS_ASU_ALLINTERFACES.length]+
					", "+molecName+" and chain "+chain;
			writeCommand(cmd, pml);
			
			i++;			
		}
				
		i = 1;
		for (File interfPdbFile:interfacePdbFiles) {
			cmd = "load "+interfPdbFile.getAbsolutePath();
			writeCommand(cmd,pml);
			
			String symMolecName = getPymolMolecName(interfPdbFile);
			String color = chainColors[i%chainColors.length];
			cmd = "color "+color+", "+symMolecName;
			writeCommand(cmd, pml);
			i++;
		}
		
		// selections 
		
		i = 0;
		for (String chain:chains) {
			
			cmd = "select "+molecName+"."+chain+", "+molecName+" and chain " + chain;
			writeCommand(cmd,pml);

			i++;			
		}
		
		i = 1;
		for (File interfPdbFile:interfacePdbFiles) {
			ChainInterface interf = interfaces.get(i);
			char chain1 = interf.getFirstMolecule().getPdbChainCode().charAt(0);
			char chain2 = interf.getSecondPdbChainCodeForOutput().charAt(0);
			
			String symMolecName = getPymolMolecName(interfPdbFile);
			
			cmd = "select "+symMolecName+"."+chain1+", "+symMolecName+" and chain " + chain1;
			writeCommand(cmd,pml);
			
			cmd = "select "+symMolecName+"."+chain2+", "+symMolecName+" and chain " + chain2;
			writeCommand(cmd,pml);
			
			i++;
		}
		
		cmd = "remove solvent";
		writeCommand(cmd, pml);
		
		cmd = "as cartoon";
		writeCommand(cmd, pml);

		
		pml.close();
		
		pymolScriptBuilder.append("@ "+pmlFile+";");
		
		pymolScriptBuilder.append("save "+pseFile+";");

		
		
		
		pymolScriptBuilder.append("quit;");
		
		command.add("-d");

		command.add(pymolScriptBuilder.toString());

		
		Process pymolProcess = new ProcessBuilder(command).start();
		int exit = pymolProcess.waitFor();
		if (exit!=0) {
			throw new IOException("Pymol exited with error status "+exit);
		}

	}
	
	/**
	 * Generates a pymol pse file with surface representation and spectrum b-factor coloring
	 * with magenta dots overlaying marking the core residues of each of the interfaces given
	 * @param chain
	 * @param interfaces
	 * @param caCutoff
	 * @param pdbFile
	 * @param pseFile
	 * @param pmlFile 
	 * @param minScore the minimum possible score for b factor colors scaling (passed as minimum to PyMOL's spectrum command)
	 * if <0 then no minimum passed (PyMOL will calculate based on present b-factors)
	 * @param maxScore the maximum possible score for b factor colors scaling (passed as maximum to PyMOL's spectrum command)
	 * if <0 then no maximum passed (PyMOL will calculate based on present b-factors)
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public void generateChainPse(PdbChain chain, ChainInterfaceList interfaces, double caCutoffGeom, double caCutoffCoreSurf, File pdbFile, File pseFile, File pmlFile, double minScore, double maxScore) 
	throws IOException, InterruptedException {
		
		String molecName = getPymolMolecName(pdbFile);

		//char chain1 = chain.getPdbChainCode().charAt(0);
		
		List<String> command = new ArrayList<String>();
		command.add(pymolExec.getAbsolutePath());
		command.add("-q");
		command.add("-c");


		// NOTE we used to pass all commands in one string after -d (with the pymolScriptBuilder StringBuffer.
		//      But pymol 1.3 and 1.4 seem to have problems with very long strings (causing segfaults)
		//      Because of that now we write most commands to pml file (which we were doing anyway so that users can 
		//      use the pml scripts if they want) and then load the pmls with pymol "@" command
		
		
		StringBuffer pymolScriptBuilder = new StringBuffer();
		PrintStream pml = new PrintStream(pmlFile);
		
		pymolScriptBuilder.append("load "+pdbFile.getAbsolutePath()+";");
				
		String cmd;

		cmd = "orient";
		writeCommand(cmd, pml);
		
		cmd = "remove solvent";
		writeCommand(cmd, pml);

		// we need to show sticks so that clicking selects residues (otherwise if only surface shown, clicking doesn't select anything)
		cmd = "as sticks"; 
		writeCommand(cmd, pml);

		cmd = "show surface";
		writeCommand(cmd, pml);
		
		String spectrumParams = "";
		if (minScore>=0) spectrumParams+=", minimum="+minScore;
		if (maxScore>=0) spectrumParams+=", maximum="+maxScore;
		cmd = "spectrum b"+spectrumParams;
		writeCommand(cmd, pml);

		String dotsLayerMolecName = molecName+"Dots";
		cmd = "copy "+dotsLayerMolecName+", "+molecName;
		writeCommand(cmd, pml);

		cmd = "copy "+dotsLayerMolecName+", "+molecName;
		writeCommand(cmd, pml);
		cmd = "h_add "+dotsLayerMolecName;
		writeCommand(cmd, pml);
		cmd = "color magenta, "+dotsLayerMolecName;
		writeCommand(cmd, pml);
		
		String caCutoffGeomStr = String.format("_%2.0f",caCutoffGeom*100.0);
		String caCutoffCoreSurfStr = String.format("_%2.0f",caCutoffCoreSurf*100.0);
		
		List<Integer> interfaceIds = new ArrayList<Integer>();
		
		for (ChainInterface interf:interfaces) {
			if (interf.getInterfaceArea()<MIN_INTERF_AREA_TO_DISPLAY) continue;
			
			InterfaceRimCore rimCore = null;
			if (interf.getFirstMolecule().getPdbChainCode().equals(chain.getPdbChainCode())) {
				interf.calcRimAndCore(caCutoffGeom);
				rimCore = interf.getFirstRimCore();
				
			} else if (interf.getSecondMolecule().getPdbChainCode().equals(chain.getPdbChainCode())) {
				interf.calcRimAndCore(caCutoffGeom);
				rimCore = interf.getSecondRimCore();
				
			} else {
				continue;
			}
			int id = interf.getId();
			interfaceIds.add(id);
			
			
			selectRimCore(pml, rimCore, dotsLayerMolecName, id+"Dots"+caCutoffGeomStr);
			
			cmd = "select interface"+id+"Dots, core"+id+"Dots"+caCutoffGeomStr+" or rim"+id+"Dots"+caCutoffGeomStr;
			writeCommand(cmd, pml);
			
			selectRimCore(pml, rimCore, molecName, id+caCutoffGeomStr);
			
			cmd = "select interface"+id+", core"+id+caCutoffGeomStr+" or rim"+id+caCutoffGeomStr;
			writeCommand(cmd, pml);


			if (interf.getFirstMolecule().getPdbChainCode().equals(chain.getPdbChainCode())) {
				interf.calcRimAndCore(caCutoffCoreSurf);
				rimCore = interf.getFirstRimCore();
				
			} else if (interf.getSecondMolecule().getPdbChainCode().equals(chain.getPdbChainCode())) {
				interf.calcRimAndCore(caCutoffCoreSurf);
				rimCore = interf.getSecondRimCore();
				
			} 
			selectRimCore(pml, rimCore, dotsLayerMolecName, id+"Dots"+caCutoffCoreSurfStr);

			selectRimCore(pml, rimCore, molecName, id+caCutoffCoreSurfStr);

		}
		
		// remove from the dots layer everything not in an interface
		cmd = "remove "+dotsLayerMolecName+" ";
		for (int id:interfaceIds) {
			cmd+=" and not interface"+id+"Dots";
		}
		writeCommand(cmd, pml);
		
		cmd = "hide everything, "+dotsLayerMolecName;
		writeCommand(cmd, pml);
		
		// show the dots for core+caCutoffCoreSurf only 
		for (int id:interfaceIds) {
			cmd = "as dots, core"+id+"Dots"+caCutoffCoreSurfStr;			
			writeCommand(cmd, pml);		
			break; // i.e. we only show cores for the first interface
		}

		cmd = "set dot_width=1, "+dotsLayerMolecName;
		writeCommand(cmd, pml);

		
		cmd = "select resi 0";// so that the last selection is deactivated
		writeCommand(cmd, pml);
		
		pml.close();
		
		pymolScriptBuilder.append("@ "+pmlFile+";");
		
		pymolScriptBuilder.append("save "+pseFile+";");

		pymolScriptBuilder.append("quit;");
		
		command.add("-d");
		
		//System.out.println(pymolScriptBuilder.toString());
		
		command.add(pymolScriptBuilder.toString());

		
		Process pymolProcess = new ProcessBuilder(command).start();
		int exit = pymolProcess.waitFor();
		if (exit!=0) {
			throw new IOException("Pymol exited with error status "+exit);
		}
	}

	
	public String getChainColor(char letter, int index, boolean isSymRelated) {
		String color = null;
		if (isSymRelated && index!=0) {
			color = symRelatedColor;
		} else {
			if (letter<'A' || letter>'Z') {
				// if out of the range A-Z then we assign simply a color based on the chain index
				color = chainColors[index%chainColors.length];
			} else {
				// A-Z correspond to ASCII codes 65 to 90. The letter ascii code modulo 65 gives an indexing of 0 (A) to 25 (Z)
				// a given letter will always get the same color assigned
				color = chainColors[letter%65];	
			}
		}
		return color;
	}
	
	private void selectRimCore(PrintStream pml, InterfaceRimCore rimCore, String molecName, String suffix) {
		String cmd = "select core"+suffix+", "+molecName+" and resi "+getResiSelString(rimCore.getCoreResidues());
		writeCommand(cmd, pml);
		cmd = "select rim"+suffix+", "+molecName+" and resi "+getResiSelString(rimCore.getRimResidues());
		writeCommand(cmd, pml);			
	}
	
	private String getPymolMolecName(File pdbFile) {
		 return pdbFile.getName().substring(0, pdbFile.getName().lastIndexOf('.'));
	}
	
	private String getResiSelString(List<Residue> list) {
		if (list.isEmpty()) return "0";
		StringBuffer sb = new StringBuffer();
		for (int i=0;i<list.size();i++) {
			sb.append(list.get(i).getSerial());
			if (i!=list.size()-1) sb.append("+");
		}
		return sb.toString();
	}

	private String getSelString(String namePrefix, char chainName, List<Residue> list) {
		return "select "+namePrefix+chainName+", chain "+chainName+" and resi "+getResiSelString(list);
	}

	private void writeCommand(String cmd, PrintStream ps) {
		//if (sb!=null) {
		//	sb.append(cmd+";");
		//}
		if (ps!=null) {
			ps.println(cmd);
		}
		
	}
	
	/**
	 * Reads from properties file the chain colors: 26 colors, one per alphabet letter
	 * and a color for the sym related chain
	 * @param is
	 * @throws IOException
	 */
	public void readColorsFromPropertiesFile(InputStream is) throws IOException {
		
		Properties p = new Properties();
		p.load(is);

		chainColors = new String[26];
		char letter = 'A';
		for (int i=0;i<26;i++) {
			chainColors[i] = p.getProperty(Character.toString(letter)); 
			letter++;
		}
		symRelatedColor = p.getProperty("SYMCHAIN");
		interf1color = p.getProperty("INTERF1");
		interf2color = p.getProperty("INTERF2");
	}	
	
	/**
	 * Reads from resource file the color mappings of pymol color names to hex RGB color codes
	 * @param is
	 * @throws IOException
	 */
	public void readColorMappingsFromResourceFile(InputStream is) throws IOException {
		colorMappings = new HashMap<String, String>();
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line;
		while ((line=br.readLine())!=null) {
			if (line.isEmpty()) continue;
			if (line.startsWith("#")) continue;
			String[] tokens = line.split(" ");
			colorMappings.put(tokens[0], tokens[1]);
		}
		br.close();
	}
	
	/**
	 * Given a pymol color name (e.g. "raspberry") returns the hex RGB color code, e.g. #b24c66
	 * or null if no such color name exists.
	 * @param pymolColor
	 * @return
	 */
	public String getHexColorCode(String pymolColor) {
		return colorMappings.get(pymolColor);
	}

	public String getInterf1Color() {
		return interf1color;
	}
	
	public String getInterf2Color() {
		return interf2color;
	}
}