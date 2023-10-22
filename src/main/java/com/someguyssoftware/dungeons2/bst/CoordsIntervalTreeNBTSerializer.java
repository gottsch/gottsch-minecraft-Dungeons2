package com.someguyssoftware.dungeons2.bst;

import java.util.function.Supplier;

import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 
 * @author Mark Gottschling on Sep 20, 2022
 *
 */
public class CoordsIntervalTreeNBTSerializer<D extends INBTSerializable<NBTTagCompound>> {
	private static final String START_KEY = "coords1";
	private static final String END_KEY = "coords2";
	private static final String LEFT_KEY = "left";
	private static final String RIGHT_KEY = "right";
	private static final String MIN_KEY = "min";
	private static final String MAX_KEY = "max";
	private static final String DATA_KEY = "data";
	
	private Supplier<D> dataSupplier;
	
	public CoordsIntervalTreeNBTSerializer(Supplier<D> dataSupplier) {
		this.dataSupplier = dataSupplier;
	}
	
	/**
	 * 
	 * @param tree
	 * @param tag
	 * @param tagName
	 */
	public void save(CoordsIntervalTree<D> tree, NBTTagCompound tag, String tagName) {
		if (tree.getRoot() == null) {
			return;
		}		
		NBTTagCompound rootNbt = new NBTTagCompound();
		save((CoordsInterval<D>)tree.getRoot(), rootNbt);
		tag.setTag(tagName, rootNbt);
	}
	
	/**
	 * 
	 * @param interval
	 * @param tag
	 */
	private void save(CoordsInterval<D> interval, NBTTagCompound tag) {	
		NBTTagCompound coordsNbt1 = new NBTTagCompound();
		NBTTagCompound coordsNbt2 = new NBTTagCompound();
		
		interval.getCoords1().writeToNBT(coordsNbt1);
		interval.getCoords2().writeToNBT(coordsNbt2);
		
		tag.setTag(START_KEY, coordsNbt1);
		tag.setTag(END_KEY, coordsNbt2);

		tag.setInteger(MIN_KEY, interval.getMin());
		tag.setInteger(MAX_KEY, interval.getMax());
		
		if (interval.getData() != null) {
			NBTTagCompound dataNbt =interval.getData().serializeNBT();
			tag.setTag(DATA_KEY, dataNbt);
		}
		
		if (interval.getLeft() != null) {
			NBTTagCompound left = new NBTTagCompound();
			save((CoordsInterval<D>)interval.getLeft(), left);
			tag.setTag(LEFT_KEY, left);
		}

		if (interval.getRight() != null) {
			NBTTagCompound right = new NBTTagCompound();
			save((CoordsInterval<D>)interval.getRight(), right);
			tag.setTag(RIGHT_KEY, right);
		}
	}
	
	/**
	 * 
	 * @param tag
	 * @param tagName
	 * @return
	 */
	public synchronized CoordsIntervalTree<D> load(NBTTagCompound tag, String tagName) {
		CoordsIntervalTree<D> tree = new CoordsIntervalTree<>();
		
		NBTTagCompound rootNbt = tag.getCompoundTag(tagName);
		CoordsInterval<D> root = load(rootNbt);
		if (root != null && !root.isEmpty()) {
			tree.setRoot(root);
		}
		return tree;
	}
	
	/**
	 * 
	 * @param tag
	 * @return
	 */
	private synchronized CoordsInterval<D> load(NBTTagCompound tag) {
		CoordsInterval<D> interval = new CoordsInterval<D>();
		
		ICoords c1 = CoordsInterval.EMPTY.getCoords1();
		ICoords c2 = CoordsInterval.EMPTY.getCoords2();
		if (tag.hasKey(START_KEY)) {
			c1 = ICoords.readFromNBT((NBTTagCompound) tag.getTag(START_KEY));
		}
		if (tag.hasKey(END_KEY)) {
			c2 = ICoords.readFromNBT((NBTTagCompound) tag.getTag(END_KEY));
		}
		interval.setCoords1(c1);
		interval.setCoords2(c2);
		
		if (tag.hasKey(MIN_KEY)) {
			interval.setMin(tag.getInteger(MIN_KEY));
		}
		if (tag.hasKey(MAX_KEY)) {
			interval.setMax(tag.getInteger(MAX_KEY));
		}
		
		if (tag.hasKey(DATA_KEY) && dataSupplier != null) {
			D data = getDataSupplier().get();
			data.deserializeNBT(tag);
			interval.setData(data);
		}
		
		if (tag.hasKey(LEFT_KEY)) {
			CoordsInterval<D> left = load((NBTTagCompound) tag.getTag(LEFT_KEY));
			if (!left.isEmpty()) {
				interval.setLeft(left);
			}
		}
		
		if (tag.hasKey(RIGHT_KEY)) {
			CoordsInterval<D> right = load((NBTTagCompound) tag.getTag(RIGHT_KEY));
			if (!right.isEmpty()) {
				interval.setLeft(right);
			}
			else {
				interval.setRight(right);
			}			
		}		
		return interval;
	}

	public Supplier<D> getDataSupplier() {
		return dataSupplier;
	}

}
