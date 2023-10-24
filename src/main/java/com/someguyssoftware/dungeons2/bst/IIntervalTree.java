package com.someguyssoftware.dungeons2.bst;

import java.util.List;

/**
 * 
 * @author Mark Gottschling on Sep 20, 2022
 *
 * @param <D>
 */
public interface IIntervalTree<D> {

	IInterval<D> insert(IInterval<D> interval);

	void clear();

	IInterval<D> delete(IInterval<D> target);

	List<IInterval<D>> getOverlapping(IInterval<D> interval, IInterval<D> testInterval, boolean findFast,
			boolean includeBorder);

}
