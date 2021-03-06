package owl.core.util;

import java.util.Map;

import javax.vecmath.Point3d;

import owl.core.structure.Atom;

/**
 * A grid to be used for calculating atom contacts through our geometric hashing algorithm.
 * 
 * The grid is composed of cells of size of the cutoff so that the distances that need to be calculated
 * are reduced to those within each cell and to the neighbouring cells.
 * 
 * @author duarte_j
 *
 */
public class Grid {
	
	private static final int SCALE=100; // i.e. we use units of hundredths of Amstrongs (thus cutoffs can be specified with a maximum precission of 0.01A)
	
	private GridCell[][][] cells;
	
	private double cutoff;
	private int cellSize;
	
	private Atom[] iAtoms;
	private Atom[] jAtoms;
	
	// the bounds in int grid coordinates
	private int[] bounds;
	
	// the i and j bounding boxes in original double coordinates
	private BoundingBox ibounds;
	private BoundingBox jbounds;
	
	private boolean noOverlap; // if the 2 sets of atoms are found not to overlap then this is set to true
	
	public Grid(double cutoff) {
		this.cutoff = cutoff;
		this.cellSize = (int) Math.floor(cutoff*SCALE);
		this.noOverlap = false;
	}
	
	private int getFloor(double number) {
		return (cellSize*((int)Math.floor(number*SCALE/cellSize)));
	}
	
	private int xintgrid2xgridindex(int xgridDim) {
		return (xgridDim-bounds[0])/cellSize;
	}
	
	private int yintgrid2ygridindex(int ygridDim) {
		return (ygridDim-bounds[1])/cellSize;
	}
	
	private int zintgrid2zgridindex(int zgridDim) {
		return (zgridDim-bounds[2])/cellSize;
	}
	
	/**
	 * Adds the i and j atoms and fills the grid. Their bounds will be computed.
	 * @param iAtoms
	 * @param jAtoms
	 */
	public void addAtoms(Atom[] iAtoms, Atom[] jAtoms) {
		addAtoms(iAtoms, null, jAtoms, null);
	}
	
	/**
	 * Adds the i and j atoms and fils the grid, passing their bounds (array of size 6 with x,y,z minima and x,y,z maxima)
	 * This way the bounds don't need to be recomputed.
	 * @param iAtoms
	 * @param icoordbounds
	 * @param jAtoms
	 * @param jcoordbounds
	 */
	public void addAtoms(Atom[] iAtoms, BoundingBox icoordbounds, Atom[] jAtoms, BoundingBox jcoordbounds) {
		this.iAtoms = iAtoms;

		if (icoordbounds!=null) {
			this.ibounds = icoordbounds;
		} else {
			this.ibounds = new BoundingBox(iAtoms);
		}
		
		this.jAtoms = jAtoms;

		if (jAtoms==iAtoms) {
			this.jbounds=ibounds;
		} else {
			if (jcoordbounds!=null) {
				this.jbounds = jcoordbounds;
			} else {
				this.jbounds = new BoundingBox(jAtoms);
				
			}
		}
		
		fillGrid();
	}

	/**
	 * Creates the grid based on the boundaries defined by all atoms given (iAtoms and jAtoms)
	 * and places the atoms in their corresponding grid cells.
	 * Checks also if the i and j grid overlap, i.e. the enclosing bounds of 
	 * the 2 grids (i and j) are no more than one cell size apart. If they don't
	 * overlap then they are too far apart so there's nothing to calculate, we set
	 * the noOverlap flag and then getDistMatrix will do no calculation at all.
	 * @param iAtoms
	 * @param jAtoms
	 */
	private void fillGrid() {

		if (!ibounds.overlaps(jbounds, cutoff)) {
			//System.out.print("-");
			noOverlap = true;
			return;
		}
		
		findFullGridIntBounds();
		
		cells = new GridCell[1+(bounds[3]-bounds[0])/cellSize]
		                    [1+(bounds[4]-bounds[1])/cellSize]
		                    [1+(bounds[5]-bounds[2])/cellSize];
		
		int i = 0;
		for (Atom atom:iAtoms) {
			Point3d coord = atom.getCoords();
			int xind = xintgrid2xgridindex(getFloor(coord.x));
			int yind = yintgrid2ygridindex(getFloor(coord.y));
			int zind = zintgrid2zgridindex(getFloor(coord.z));
			if (cells[xind][yind][zind]==null) {
				cells[xind][yind][zind] = new GridCell();
			}
			cells[xind][yind][zind].addIindex(i);
			i++;
		}
		
		int j = 0;
		for (Atom atom:jAtoms) {
			Point3d coord = atom.getCoords();
			int xind = xintgrid2xgridindex(getFloor(coord.x));
			int yind = yintgrid2ygridindex(getFloor(coord.y));
			int zind = zintgrid2zgridindex(getFloor(coord.z));
			if (cells[xind][yind][zind]==null) {
				cells[xind][yind][zind] = new GridCell();
			}
			cells[xind][yind][zind].addJindex(j);
			j++;
		}
		
	}
	
	/**
	 * Calculates an int array of size 6 into member variable bounds:
	 * - elements 0,1,2: minimum x,y,z of the iAtoms and jAtoms
	 * - elements 3,4,5: maximum x,y,z of the iAtoms and jAtoms
	 */
	private void findFullGridIntBounds() {
		int[] iIntBounds = getIntBounds(ibounds);
		
		bounds = new int[6];
		if (ibounds==jbounds) {
			bounds = iIntBounds;
		} else {
			int[] jIntBounds = getIntBounds(jbounds);
			bounds[0] = Math.min(iIntBounds[0],jIntBounds[0]);
			bounds[1] = Math.min(iIntBounds[1],jIntBounds[1]);
			bounds[2] = Math.min(iIntBounds[2],jIntBounds[2]);
			bounds[3] = Math.max(iIntBounds[3],jIntBounds[3]);
			bounds[4] = Math.max(iIntBounds[4],jIntBounds[4]);
			bounds[5] = Math.max(iIntBounds[5],jIntBounds[5]);
		}
	}

	/**
	 * Returns an int array of size 6 :
	 * - elements 0,1,2: minimum x,y,z (in grid int coordinates) of the given atoms
	 * - elements 3,4,5: maximum x,y,z (in grid int coordinates) of the given atoms
	 * @return 
	 */
	private int[] getIntBounds(BoundingBox coordbounds) {
		int[] bs = new int[6];
		bs[0] = getFloor(coordbounds.xmin);
		bs[1] = getFloor(coordbounds.ymin);
		bs[2] = getFloor(coordbounds.zmin);
		bs[3] = getFloor(coordbounds.xmax);
		bs[4] = getFloor(coordbounds.ymax);
		bs[5] = getFloor(coordbounds.zmax);
		return bs;
	}
	
	/**
	 * Calculates a distance matrix for i to j atoms. The distance of any 2 atoms that 
	 * are more than 2 cellSizes apart need not be calculated, in that case we set the 
	 * distance value in the matrix to 0.0f
	 * 
	 * The procedure is first calculate all pairwise distances within the same cell and then
	 * distances of points of each cell to all its neighbouring cells.
	 * @param crossed
	 * @return
	 */
	public float[][] getDistMatrix(boolean crossed) {
		// to minimise memory footprint we use floats
		float[][]distMatrix = new float[iAtoms.length][jAtoms.length];
		
		// if the 2 sets of atoms are not overlapping they are too far away and no need to calculate anything
		if (noOverlap) return distMatrix;
		
		for (int xind=0;xind<cells.length;xind++) {
			for (int yind=0;yind<cells[xind].length;yind++) {
				for (int zind=0;zind<cells[xind][yind].length;zind++) {
					// distances of points within this cell
					GridCell thisCell = cells[xind][yind][zind];
					if (thisCell==null) continue;
					thisCell.getDistancesWithinCell(distMatrix,iAtoms,jAtoms,crossed);
					
					// distances of points from this box to all neighbouring boxes: 26 iterations (26 neighbouring boxes)
					for (int x=xind-1;x<=xind+1;x++) {
						for (int y=yind-1;y<=yind+1;y++) {
							for (int z=zind-1;z<=zind+1;z++) {
								if (x==xind && y==yind && z==zind) continue;
								if (x>=0 && x<cells.length && y>=0 && y<cells[x].length && z>=0 && z<cells[x][y].length) {
									if (cells[x][y][z] == null) continue;
									thisCell.getDistancesToNeighborCell(cells[x][y][z],distMatrix,iAtoms,jAtoms,crossed);
								}
							}
						}
					}
				}
			}
		}
		return distMatrix;
	}
	
	public void countDensity(Map<Integer,Integer> densityCount) {
		// count density
		
		for (int xind=0;xind<cells.length;xind++) {
			for (int yind=0;yind<cells[xind].length;yind++) {
				for (int zind=0;zind<cells[xind][yind].length;zind++) {
					if (cells[xind][yind][zind]==null) continue;
					int size = getNumGridNbs(xind,yind,zind);	// count number of neighbouring grid cells with points in them
					
					if(densityCount.containsKey(size)) {
						int old = densityCount.get(size);
						densityCount.put(size, ++old);
					} else {
						densityCount.put(size, 1);
					}
				}
			}
		}
		
	}
	
	/** 
	 * Returns the number of neighbours of given grid cell (cells with points in them)
	 * @param xind x index of cell
	 * @param yind y index of cell
	 * @param zind z index of cell
	 * @return 
	 */
	private int getNumGridNbs(int xind,int yind,int zind) {
		int nbs = 0;
		
		for (int x=xind-1;x<=xind+1;x++) {
			for (int y=yind-1;y<=yind+1;y++) {
				for (int z=zind-1;z<=zind+1;z++) {
					if (x==xind && y==yind && z==zind) continue;
					if (x>=0 && x<cells.length && y>=0 && y<cells[x].length && z>=0 && z<cells[x][y].length) {
						if (cells[x][y][z] != null) nbs++;
					}
				}
			}
		}
		return nbs;
	}
	
	public double getCutoff() {
		return cutoff;
	}
	
	/**
	 * Tells whether (after having added atoms to grid) the i and j grids are not overlapping. 
	 * Overlap is defined as enclosing bounds of the 2 grids being no more than one cell size apart.
	 * @return true if the 2 grids don't overlap, false if they do
	 */
	public boolean isNoOverlap() {
		return noOverlap;
	}
	
}
