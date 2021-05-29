package mod.chiselsandbits.api;

import com.mojang.blaze3d.matrix.MatrixStack;
import mod.chiselsandbits.api.APIExceptions.CannotBeChiseled;
import mod.chiselsandbits.api.APIExceptions.InvalidBitItem;
import mod.chiselsandbits.chiseledblock.ItemBlockChiseled;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * Do not implement, is passed to your {@link IChiselsAndBitsAddon}
 */
public interface IChiselAndBitsAPI
{

	/**
	 * Determine the Item Type of the item in an ItemStack and return it.
	 *
	 * @param stack
	 * @return ItemType of the item, or null if it is not any of them.
	 */
	@Nullable
	ItemType getItemType(
			ItemStack stack );

	/**
	 * Check if a block can support {@link IBitAccess}
	 *
	 * @param world
	 * @param pos
	 * @return true if the block can be chiseled, this is true for air,
	 *         multi-parts, and blocks which can be chiseled, false otherwise.
	 */
	boolean canBeChiseled(
			World world,
			BlockPos pos );

	/**
	 * is this block already chiseled?
	 *
	 * @param world
	 * @param pos
	 * @return true if the block contains chiseled bits, false otherwise.
	 */
	boolean isBlockChiseled(
			World world,
			BlockPos pos );

	/**
	 * Get Access to the bits for a given block.
	 *
	 * @param world
	 * @param pos
	 * @return A {@link IBitAccess} for the specified location.
	 * @throws CannotBeChiseled
	 *             when the location cannot support bits, or if the parameters
	 *             are invalid.
	 */
	IBitAccess getBitAccess(
			World world,
			BlockPos pos ) throws CannotBeChiseled;

	/**
	 * Create a bit access from an ItemStack, passing an empty ItemStack creates
	 * an empty bit access, passing an invalid item returns null.
	 *
	 * @return a {@link IBitAccess} for an ItemStack.
	 */
	@Nullable
	IBitAccess createBitItem(
			ItemStack stack );

	/**
	 * Create a brush from an ItemStack, once created you can use it many times.
	 *
	 * @param stack the stack.
	 * @return A brush for the specified ItemStack, if passed an empty ItemStack
	 *         an air brush is created.
	 * @throws InvalidBitItem
	 */
	IBitBrush createBrush(
			ItemStack stack ) throws InvalidBitItem;

	/**
	 * Create a brush from a state, once created you can use it many times.
	 *
	 * @param state
	 * @return A brush for the specified state, if null is passed for the item
	 *         an air brush is created.
	 * @throws InvalidBitItem
	 */
	IBitBrush createBrushFromState(
			@Nullable BlockState state ) throws InvalidBitItem;

	/**
	 * Convert ray trace information into bit location information, note that
	 * the block position can change, be aware.
	 *
	 * @param hitX
	 * @param hitY
	 * @param hitZ
	 * @param side
	 * @param pos
	 * @param placement
	 * @return details about the target bit, no arguments should be null.
	 */
	IBitLocation getBitPos(
			double hitX,
			double hitY,
			double hitZ,
			Direction side,
			BlockPos pos,
			boolean placement );

	/**
	 * Get an ItemStack for the bit type of the state...
	 *
	 * VERY IMPORTANT: C&B lets you disable bits, if this happens the Item in
	 * this ItemStack WILL BE empty.
	 *
	 * @param defaultState
	 * @return the bit.
	 */
	ItemStack getBitItem(
			BlockState defaultState ) throws InvalidBitItem;

	/**
	 * Give a bit to a player, it will end up in their inventory, a bag, or if
	 * there is no where to put it, on the ground.
	 *
	 * CLIENT: destroys the item.
	 *
	 * SERVER: adds ItemStack to inv/bag/spawns entity.
	 *
	 * @param player
	 *            player to give bits to.
	 * @param stack
	 *            bits to store.
	 * @param spawnPos
	 *            if null defaults to the players position, absolute position of
	 *            where to spawn bits, should be in the block near where they
	 *            are being extracted from.
	 */
	void giveBitToPlayer(
			PlayerEntity player,
			ItemStack stack,
			Vector3d spawnPos );

	/**
	 * Access the contents of a bitbag as if it was a normal
	 * {@link IItemHandler} with a few extra features.
	 *
	 * @return internal object to manipulate bag.
	 */
	@Nullable
	IBitBag getBitbag(
			ItemStack stack );

	/**
	 * Example: int stackSize =
	 * api.getParameter(IntegerParam.BIT_BAG_MAX_STACK_SIZE );
	 * 
	 * @param which
	 *            - refer to ParameterType for list of possible values.
	 * @return value of specified parameter.
	 */
	<T extends Object> T getParameter(
			ParameterType<T> which );

	/**
	 * Begins an undo group, starting two operations without ending the previous
	 * operation will throw a runtime exception.
	 * 
	 * This is used to merge multiple blocks into a single operation, undo steps
	 * will be recorded regardless of usage of this method, however its
	 * suggested to use groups in any case where a change well affect more then
	 * one block.
	 *
	 * @formatter:off
	 *
	 * 				Example:
	 *
	 *                try { api.beginUndoGroup(); this.manipulateAllTheBlocks();
	 *                } finally { api.endUndoGroup(); }
	 *
	 */
	void beginUndoGroup(
			PlayerEntity player );

	/**
	 * Ends a previously running undo group, must be called after starting an
	 * undo group, closing a group without opening one will result in a runtime
	 * exception.
	 */
	void endUndoGroup(
			PlayerEntity player );

	/**
	 * Register a custom material as equivalent to another material.
	 *
	 * @param newMaterial
	 *            your custom material
	 * @param target
	 *            default MC Material C&B knows about.
	 */
	void addEquivilantMaterial(
			Material newMaterial,
			Material target ); // this should be a material C&B understands,
								// other wise you'll get stone anyway.

	/**
	 * Get a C&B key binding.
	 *
	 * @param modKeyBinding
	 *            the {@link ModKeyBinding} value that denotes the C&B key
	 *            binding to return.
	 * @return a C&B {@link KeyBinding}.
	 */
	@OnlyIn( Dist.CLIENT )
	KeyBinding getKeyBinding(
			ModKeyBinding modKeyBinding );

	/**
	 * Renders a model with transparency.
	 *
	 * @param model
	 * @param world
	 * @param pos
	 * @param alpha
	 *            Should be 0-255.
	 */
	@OnlyIn( Dist.CLIENT )
	void renderModel(
      final MatrixStack stack,
      final IBakedModel model,
      final World world,
      final BlockPos pos,
      final int alpha,
      final int combinedLight,
      final int combinedOverlay);

	/**
	 * Renders a ghost model in the same way that chiseled item blocks are rendered for placement.
	 *  @param model
	 * @param world
 * @param pos
* @param isUnplaceable
	 */
	@OnlyIn( Dist.CLIENT )
	void renderGhostModel(
      final MatrixStack stack,
      final IBakedModel model,
      final World world,
      final BlockPos pos,
      final ItemBlockChiseled.PlacementAttemptResult isUnplaceable,
      final int combinedLight,
      final int combinedOverlay);

}
