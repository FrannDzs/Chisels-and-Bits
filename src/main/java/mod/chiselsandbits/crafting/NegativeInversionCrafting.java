package mod.chiselsandbits.crafting;

import mod.chiselsandbits.chiseledblock.NBTBlobConverter;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.items.ItemNegativePrint;
import mod.chiselsandbits.registry.ModItems;
import mod.chiselsandbits.registry.ModRecipeSerializers;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.SpecialRecipe;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public class NegativeInversionCrafting extends SpecialRecipe
{

	public NegativeInversionCrafting(
			ResourceLocation name )
	{
		super( name );
	}

	@Override
	public boolean matches(
			final CraftingInventory craftingInv,
			final World worldIn )
	{
		return analzyeCraftingInventory( craftingInv, true ) != null;
	}

	public ItemStack analzyeCraftingInventory(
			final CraftingInventory craftingInv,
			final boolean generatePattern )
	{
		ItemStack targetA = null;
		ItemStack targetB = null;

		for ( int x = 0; x < craftingInv.getSizeInventory(); x++ )
		{
			final ItemStack f = craftingInv.getStackInSlot( x );
			if ( f.isEmpty() )
			{
				continue;
			}

			if ( f.getItem() instanceof ItemNegativePrint)
			{
				if (ModItems.ITEM_NEGATIVE_PRINT.get().isWritten(f))
				{
					if ( targetA != null )
					{
						return null;
					}

					targetA = f;
				}
				else
				{
					if ( targetB != null )
					{
						return null;
					}

					targetB = f;
				}
			}
			else if ( !ModUtil.isEmpty( f ) )
			{
				return null;
			}
		}

		if ( targetA != null && targetB != null )
		{
			if ( generatePattern )
			{
				return targetA;
			}

			final NBTBlobConverter tmp = new NBTBlobConverter();
			tmp.readChisleData( targetA.getTag(), VoxelBlob.VERSION_ANY );

			final VoxelBlob bestBlob = tmp.getBlob();
			bestBlob.binaryReplacement( ModUtil.getStateId( Blocks.STONE.getDefaultState() ), 0 );

			tmp.setBlob( bestBlob );

			final CompoundNBT comp = ModUtil.getTagCompound( targetA ).copy();
			tmp.writeChiselData( comp, false );

			final ItemStack outputPattern = new ItemStack( targetA.getItem() );
			outputPattern.setTag( comp );

			return outputPattern;
		}

		return null;
	}

	@Override
	public ItemStack getCraftingResult(
			final CraftingInventory craftingInv )
	{
		return analzyeCraftingInventory( craftingInv, false );
	}

	@Override
	public boolean canFit(
			final int width,
			final int height )
	{
		return width > 1 || height > 1;
	}

	@Override
	public ItemStack getRecipeOutput()
	{
		return ModUtil.getEmptyStack(); // nope
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(
			final CraftingInventory craftingInv )
	{
		final NonNullList<ItemStack> aitemstack = NonNullList.withSize( craftingInv.getSizeInventory(), ItemStack.EMPTY );

		for ( int i = 0; i < aitemstack.size(); ++i )
		{
			final ItemStack itemstack = craftingInv.getStackInSlot( i );
			if ( itemstack != null && itemstack.getItem() == ModItems.ITEM_NEGATIVE_PRINT_WRITTEN.get() && itemstack.hasTag() )
			{
				ModUtil.adjustStackSize( itemstack, 1 );
			}
		}

		return aitemstack;
	}

    @Override
    public IRecipeSerializer<?> getSerializer()
    {
        return ModRecipeSerializers.NEGATIVE_INVERSION_CRAFTING.get();
    }
}
