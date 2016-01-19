package mod.chiselsandbits.api;

import mod.chiselsandbits.api.APIExceptions.SpaceOccupied;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/**
 * Do not implement, acquire from {@link IChiselAndBitsAPI}
 */
public interface IBitAccess
{

	/**
	 * Process each bit in the {@link IBitAccess} and return a new bit in its
	 * place, can be used to optimize large changes, or iteration.
	 * 
	 * @param visitor
	 */
	void visitBits(
			IBitVisitor visitor );

	/**
	 * Returns the bit at the specific location, this should always return a
	 * valid IBitBrush, never null.
	 *
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	IBitBrush getBitAt(
			int x,
			int y,
			int z );

	/**
	 * Sets the bit at the specific location, if you pass null it will use use
	 * air.
	 *
	 * @param x
	 * @param y
	 * @param z
	 * @param bit
	 * @throws SpaceOccupied
	 */
	void setBitAt(
			int x,
			int y,
			int z,
			IBitBrush bit ) throws SpaceOccupied;

	/**
	 * Any time you modify a block you must commit your changes for them to take
	 * affect, optionally you can trigger updates or not.
	 *
	 * If the {@link IBitAccess} is not in the world this method does nothing.
	 * 
	 * @param triggerUpdates
	 *            normally true, only use false if your doing something special.
	 */
	void commitChanges(
			boolean triggerUpdates );

	/**
	 * Any time you modify a block you must commit your changes for them to take
	 * affect.
	 *
	 * If the {@link IBitAccess} is not in the world this method does nothing.
	 */
	@Deprecated
	void commitChanges();

	/**
	 * Returns an item for the {@link IBitAccess}
	 *
	 * Usable for any {@link IBitAccess}
	 *
	 * @param side
	 *            angle the player is looking at, can be null.
	 * @param type
	 *            what type of item to give.
	 * @return an Item for bits, null if there are no bits.
	 */
	ItemStack getBitsAsItem(
			EnumFacing side,
			ItemType type );

}
