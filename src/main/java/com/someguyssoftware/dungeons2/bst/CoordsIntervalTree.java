package com.someguyssoftware.dungeons2.bst;

import java.util.ArrayList;
import java.util.List;

import com.someguyssoftware.gottschcore.GottschCore;

/**
 * 
 * @author Mark Gottschling on Sep 20, 2022
 *
 */
public class CoordsIntervalTree<D> implements IIntervalTree<D> {
	private IInterval<D> root;
	
	@Override
	public void clear() {
		setRoot(null);
	}
	
	@Override
	public synchronized IInterval<D> insert(IInterval<D> interval) {
		root = insert(root, interval);
		return root;
	}
	
	/**
	 * 
	 * @param interval
	 * @param newInterval
	 * @return
	 */
	private IInterval<D> insert(IInterval<D> interval, IInterval<D> newInterval) {
		if (interval == null) {
			interval = newInterval;
			return interval;
		}

		if (interval.getMax() == null ||  newInterval.getEnd() > interval.getMax()) {
			interval.setMax(newInterval.getEnd());
		}
        if (interval.getMin() == null || newInterval.getStart() < interval.getMin()) {
        	interval.setMin(newInterval.getStart());
        }
        
		if (interval.compareTo(newInterval) <= 0) {

			if (interval.getRight() == null) {
				interval.setRight(newInterval);
			}
			else {
				insert(interval.getRight(), newInterval);
			}
		}
		else {
			if (interval.getLeft() == null) {
				interval.setLeft(newInterval);
			}
			else {
				insert(interval.getLeft(), newInterval);
			}
		}
		return interval;
	}
	
	/**
	 * @param target
	 * @return
	 */
	@Override
	public synchronized IInterval<D> delete(IInterval<D> target) {
		root = delete(root, target);
		return root;
	}
	
	/**
	 * 
	 * @param interval
	 * @param target
	 * @return
	 */
	private IInterval<D> delete(IInterval<D> interval, IInterval<D> target) {
		GottschCore.logger.debug("delete interval -> {}, target -> {}", interval, target);
		if (interval == null) {
			return interval;
		}

		if (interval.compareTo(target) < 0) {
			interval.setRight(delete(interval.getRight(), target));
		}
		else if (interval.compareTo(target) > 0) {
			interval.setLeft(delete(interval.getLeft(), target));
		}
		else {
			// node with no leaf nodes
			if (interval.getLeft() == null && interval.getRight() == null) {
				return null;
			}
			else if (interval.getLeft() == null) {
				return interval.getRight();
			}
			else if (interval.getRight() == null) {
				return interval.getLeft();
			}
			else {
				// insert right tree into left tree
				insert(interval.getLeft(), interval.getRight());
				// return the left tree
				return interval.getLeft();
			}
		}
		return interval;
	}
	
	/**
	 * find-fast, include-border convenience method
	 * @param interval
	 * @param testInterval
	 * @return
	 */
	public List<IInterval<D>> getOverlapping(IInterval<D> interval, IInterval<D> testInterval) {
		return getOverlapping(interval, testInterval, true, true);
	}	
	
	/**
	 * 
	 * @param interval
	 * @param testInterval
	 */
	@Override
	public synchronized List<IInterval<D>> getOverlapping(IInterval<D> interval, IInterval<D> testInterval, boolean findFast, boolean includeBorder) {
		List<IInterval<D>> results = new ArrayList<>();
		if (includeBorder) {
			checkOverlap(interval, testInterval, results, findFast);
		}
		else {
			checkOverlapNoBorder(interval, testInterval, results, findFast);
		}
		return results;
	}
	
	/**
	 * convenience wrapper
	 * @param interval
	 * @param testInterval
	 * @param results
	 * @param findFast
	 * @return
	 */
	private boolean checkOverlap(IInterval<D> interval, IInterval<D> testInterval, List<IInterval<D>> results, boolean findFast) {
		return checkOverlap((CoordsInterval<D>)interval, (CoordsInterval<D>)testInterval, results, findFast);
	}
	
	/**
	 * 
	 * @param interval
	 * @param testInterval
	 * @param results
	 * @param findFirst find first occurrence only
	 * @return whether an overlap was found in this subtree
	 */
	private boolean checkOverlap(CoordsInterval<D> interval, CoordsInterval<D> testInterval, List<IInterval<D>> results, boolean findFast) {
	
		if (interval == null) {
			return false;
		}

		// short-circuit
        if(testInterval.getStart() > interval.getMax() || testInterval.getEnd() < interval.getMin()) {
        	return false;
        }

		if (!((interval.getStart() > testInterval.getEnd()) || (interval.getEnd() < testInterval.getStart()))) {
			// x-axis overlaps, check z-axis
			if (!((interval.getStartZ() > testInterval.getEndZ()) || (interval.getEndZ() < testInterval.getStartZ()))) {
				// z-axis overlaps, check y-axis
				if (!((interval.getStartY() > testInterval.getEndY()) || (interval.getEndY() < testInterval.getStartY()))) {

					results.add(interval);
					if (findFast) {
						return true;
					}				
				}
			}
		}

		// walk the left branch
		if ((interval.getLeft() != null) && (interval.getLeft().getMax() >= testInterval.getStart())) {
			if (this.checkOverlap(interval.getLeft(), testInterval, results, findFast) && findFast) {
				return true;
			}
		}

		// walk the right branch
		if (this.checkOverlap(interval.getRight(), testInterval, results, findFast) && findFast) {
			return true;
		}
		
		return false;
	}
	
	private boolean checkOverlapNoBorder(IInterval<D> interval, IInterval<D> testInterval, List<IInterval<D>> results, boolean findFast) {
		return checkOverlapNoBorder((CoordsInterval<D>)interval, (CoordsInterval<D>)testInterval, results, findFast);
	}
	
	/**
	 * 
	 * @param interval
	 * @param testInterval
	 * @param results
	 * @param findFast
	 * @return
	 */
	private boolean checkOverlapNoBorder(CoordsInterval<D> interval, CoordsInterval<D> testInterval, List<IInterval<D>> results, boolean findFast) {
		if (interval == null) {
			return false;
		}

		// short-circuit
        if(testInterval.getStart() > interval.getMax() || testInterval.getEnd() < interval.getMin()) { //TESTING - adding >=, <= instead of >, <
        	return false;
        }

		if (!((interval.getStart() >= testInterval.getEnd()) || (interval.getEnd() <= testInterval.getStart()))) { // TESTING - adding >= and <= to all comparisons
			// x-axis overlaps, check z-axis
			if (!((interval.getStartZ() >= testInterval.getEndZ()) || (interval.getEndZ() <= testInterval.getStartZ()))) {
				// z-axis overlaps, check y-axis
				if (!((interval.getStartY() >= testInterval.getEndY()) || (interval.getEndY() <= testInterval.getStartY()))) {
					results.add(interval);
					if (findFast) {
						return true;
					}				
				}
			}
		}

		// walk the left branch
		if ((interval.getLeft() != null) && (interval.getLeft().getMax() > testInterval.getStart())) { // TESTING replaced >= with >
			if (this.checkOverlapNoBorder(interval.getLeft(), testInterval, results, findFast) && findFast) {
				return true;
			}
		}

		// walk the right branch
		if (this.checkOverlapNoBorder(interval.getRight(), testInterval, results, findFast) && findFast) {
			return true;
		}
		
		return false;
	}
	
	public synchronized IInterval<D> getRoot() {
		return root;
	}

	public synchronized void setRoot(IInterval<D> root) {
		this.root = root;
	}
}
