package mod.chiselsandbits.items;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.fml.common.thread.EffectiveSide;
import org.apache.commons.lang3.tuple.Pair;

import com.google.common.base.Stopwatch;

import mod.chiselsandbits.chiseledblock.BlockBitInfo;
import mod.chiselsandbits.chiseledblock.BlockChiseled;
import mod.chiselsandbits.chiseledblock.data.BitLocation;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.helpers.ActingPlayer;
import mod.chiselsandbits.helpers.BitOperation;
import mod.chiselsandbits.helpers.ChiselModeManager;
import mod.chiselsandbits.helpers.ChiselToolType;
import mod.chiselsandbits.helpers.IContinuousInventory;
import mod.chiselsandbits.helpers.IItemInInventory;
import mod.chiselsandbits.helpers.LocalStrings;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.interfaces.IChiselModeItem;
import mod.chiselsandbits.interfaces.IItemScrollWheel;
import mod.chiselsandbits.modes.ChiselMode;
import mod.chiselsandbits.modes.IToolMode;
import mod.chiselsandbits.network.packets.PacketChisel;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.item.ItemTier.*;

public class ItemChisel extends ToolItem implements IItemScrollWheel, IChiselModeItem
{
	final private static float one_16th = 1.0f / 16.0f;

	public ItemChisel(
			final IItemTier material,
            final Item.Properties properties)
	{
		super( 0.1F, -2.8F, material, new HashSet<Block>(), setupDamageStack(material, properties) );
	}

	private static Item.Properties setupDamageStack(IItemTier material, Item.Properties properties) {
        long uses = 1;
        if (DIAMOND.equals(material))
        {
            uses = ChiselsAndBits.getConfig().getServer().diamondChiselUses.get();
        }
        else if (GOLD.equals(material))
        {
            uses = ChiselsAndBits.getConfig().getServer().goldChiselUses.get();
        }
        else if (IRON.equals(material))
        {
            uses = ChiselsAndBits.getConfig().getServer().ironChiselUses.get();
        }
        else if (STONE.equals(material))
        {
            uses = ChiselsAndBits.getConfig().getServer().stoneChiselUses.get();
        }
        else if (NETHERITE.equals(material))
        {
            uses = ChiselsAndBits.getConfig().getServer().netheriteChiselUses.get();
        }

        return properties.maxDamage(ChiselsAndBits.getConfig().getServer().damageTools.get() ? (int) Math.max( 0, uses ) : 0);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void addInformation(
      final ItemStack stack, @Nullable final World worldIn, final List<ITextComponent> tooltip, final ITooltipFlag flagIn)
    {
        super.addInformation( stack, worldIn, tooltip, flagIn );
        ChiselsAndBits.getConfig().getCommon().helpText( LocalStrings.HelpChisel, tooltip,
          ClientSide.instance.getKeyName( Minecraft.getInstance().gameSettings.keyBindAttack ),
          ClientSide.instance.getModeKey() );
    }

	private static Stopwatch timer;

	public static void resetDelay()
	{
		timer = null;
	}

	@Override
	/**
	 * alter digging behavior to chisel, uses packets to enable server to stay
	 * in-sync.
	 */
	public boolean onBlockStartBreak(
			final ItemStack itemstack,
			final BlockPos pos,
			final PlayerEntity player )
	{
		return ItemChisel.fromBreakToChisel( ChiselMode.castMode( ChiselModeManager.getChiselMode( player, ChiselToolType.CHISEL, Hand.MAIN_HAND ) ), itemstack, pos, player, Hand.MAIN_HAND );
	}

	static public boolean fromBreakToChisel(
			final ChiselMode mode,
			final ItemStack itemstack,
			final @Nonnull BlockPos pos,
			final PlayerEntity player,
			final Hand hand )
	{
		final BlockState state = player.getEntityWorld().getBlockState( pos );
		if ( ItemChiseledBit.checkRequiredSpace( player, state ) )
		{
			return false;
		}
		if ( BlockBitInfo.canChisel( state ) )
		{
			if ( itemstack != null && ( timer == null || timer.elapsed( TimeUnit.MILLISECONDS ) > 150 ) )
			{
				timer = Stopwatch.createStarted();
				if ( mode == ChiselMode.DRAWN_REGION )
				{
					final Pair<Vector3d, Vector3d> PlayerRay = ModUtil.getPlayerRay( player );
					final Vector3d ray_from = PlayerRay.getLeft();
					final Vector3d ray_to = PlayerRay.getRight();

					final RayTraceContext context = new RayTraceContext(ray_from, ray_to, RayTraceContext.BlockMode.VISUAL, RayTraceContext.FluidMode.NONE, player);

					final RayTraceResult mop = player.world.rayTraceBlocks(context);
					if ( mop != null && mop instanceof BlockRayTraceResult )
					{
					    final BlockRayTraceResult rayTraceResult = (BlockRayTraceResult) mop;
						final BitLocation loc = new BitLocation( rayTraceResult, BitOperation.CHISEL, false );
						ClientSide.instance.pointAt( ChiselToolType.CHISEL, loc, hand );
						return true;
					}

					return true;
				}

				if ( !player.world.isRemote )
				{
					return true;
				}

				final Pair<Vector3d, Vector3d> PlayerRay = ModUtil.getPlayerRay( player );
				final Vector3d ray_from = PlayerRay.getLeft();
				final Vector3d ray_to = PlayerRay.getRight();
                final RayTraceContext context = new RayTraceContext(ray_from, ray_to, RayTraceContext.BlockMode.VISUAL, RayTraceContext.FluidMode.NONE, player);

                BlockRayTraceResult mop = player.world.rayTraceBlocks(context);
				if (mop.getType() != RayTraceResult.Type.MISS)
				{
				    if ((Minecraft.getInstance().objectMouseOver != null ? Minecraft.getInstance().objectMouseOver.getType() : RayTraceResult.Type.MISS) == RayTraceResult.Type.BLOCK) {
                        BlockRayTraceResult minecraftResult = (BlockRayTraceResult) Minecraft.getInstance().objectMouseOver;
                        if (!minecraftResult.getPos().toImmutable().equals(mop.getPos().toImmutable())) {
                            mop = minecraftResult;
                        }
                    }

					useChisel( mode, player, player.world, mop, hand );
				}
			}

			return true;
		}

		if ( player.getEntityWorld().isRemote )
		{
            return ClientSide.instance.getStartPos() != null;
		}

		return false;
	}

    @Override
    public ITextComponent getHighlightTip(final ItemStack item, final ITextComponent displayName)
    {
        if (EffectiveSide.get().isClient() && ChiselsAndBits.getConfig().getClient().itemNameModeDisplay.get() && displayName instanceof IFormattableTextComponent)
        {
            final IFormattableTextComponent formattableTextComponent = (IFormattableTextComponent) displayName;
            if ( ChiselsAndBits.getConfig().getClient().perChiselMode.get() || EffectiveSide.get().isServer())
            {
                return formattableTextComponent.appendString(" - ").appendString(ChiselMode.getMode( item ).string.getLocal());
            }
            else
            {
                return formattableTextComponent.appendString(" - ").appendString(ChiselModeManager.getChiselMode( ClientSide.instance.getPlayer(), ChiselToolType.CHISEL, Hand.MAIN_HAND ).getName().getLocal());
            }
        }

        return displayName;
    }

	@Override
	public ActionResult<ItemStack> onItemRightClick(
			final World worldIn,
			final PlayerEntity playerIn,
			final Hand hand )
	{
		final ItemStack itemStackIn = playerIn.getHeldItem( hand );

		if ( worldIn.isRemote && ChiselsAndBits.getConfig().getClient().enableRightClickModeChange.get() )
		{
			final IToolMode mode = ChiselModeManager.getChiselMode( playerIn, ChiselToolType.CHISEL, hand );
			ChiselModeManager.scrollOption( ChiselToolType.CHISEL, mode, mode, playerIn.isSneaking() ? -1 : 1 );
			return new ActionResult<>(ActionResultType.SUCCESS, itemStackIn);
		}

		return super.onItemRightClick( worldIn, playerIn, hand );
	}

    @Override
    public ActionResultType onItemUseFirst(final ItemStack stack, final ItemUseContext context)
    {
        if ( context.getWorld().isRemote && ChiselsAndBits.getConfig().getClient().enableRightClickModeChange.get() )
        {
            onItemRightClick( context.getWorld(), context.getPlayer(), context.getHand() );
            return ActionResultType.SUCCESS;
        }

        return ActionResultType.FAIL;
    }

	static void useChisel(
			final ChiselMode mode,
			final PlayerEntity player,
			final World world,
			final BlockRayTraceResult rayTraceResult,
			final Hand hand )
	{
		final BitLocation location = new BitLocation(rayTraceResult, BitOperation.CHISEL, false );

		final PacketChisel pc = new PacketChisel( BitOperation.CHISEL, location, rayTraceResult.getFace(), mode, hand );

		final int extractedState = pc.doAction( player );
		if ( extractedState != 0 )
		{
			ClientSide.breakSound( world, rayTraceResult.getPos(), extractedState );

			ChiselsAndBits.getNetworkChannel().sendToServer(pc);
		}
	}

	/**
	 * Modifies VoxelData of TileEntityChiseled
	 *
	 * @param selected
	 *
	 * @param player
	 * @param vb
	 * @param world
	 * @param pos
	 * @param side
	 * @param x
	 * @param y
	 * @param z
	 * @param output
	 * @return
	 */
	static public ItemStack chiselBlock(
			final IContinuousInventory selected,
			final ActingPlayer player,
			final VoxelBlob vb,
			final World world,
			final BlockPos pos,
			final Direction side,
			final int x,
			final int y,
			final int z,
			ItemStack output,
			final List<ItemEntity> spawnlist )
	{
		final boolean isCreative = player.isCreative();

		final int blk = vb.get( x, y, z );
		if ( blk == 0 )
		{
			return output;
		}

		if ( !canMine( selected, ModUtil.getStateById( blk ), player.getPlayer(), world, pos ) )
		{
			return output;
		}

		if ( !selected.useItem( blk ) )
		{
			return output;
		}

		if ( !world.isRemote && !isCreative )
		{
			double hitX = x * one_16th;
			double hitY = y * one_16th;
			double hitZ = z * one_16th;

			final double offset = 0.5;
			hitX += side.getXOffset() * offset;
			hitY += side.getYOffset() * offset;
			hitZ += side.getZOffset() * offset;

			if ( output == null || !ItemChiseledBit.sameBit( output, blk ) || ModUtil.getStackSize( output ) == 64 )
			{
				output = ItemChiseledBit.createStack( blk, 1, true );

                spawnlist.add( new ItemEntity( world, pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ, output ) );
			}
			else
			{
				ModUtil.adjustStackSize( output, 1 );
			}
		}
		else
		{
			// return value...
			output = ItemChiseledBit.createStack( blk, 1, true );
		}

		vb.clear( x, y, z );
		return output;
	}

	private static boolean testingChisel = false;

	public static boolean canMine(
			final IContinuousInventory chiselInv,
			final BlockState state,
			final PlayerEntity player,
			final World world,
			final @Nonnull BlockPos pos )
	{
		final int targetState = ModUtil.getStateId( state );
		IItemInInventory chiselSlot = chiselInv.getItem( targetState );
		ItemStack chisel = chiselSlot.getStack();

		if ( player.isCreative() )
		{
			return world.isBlockModifiable( player, pos );
		}

		if ( ModUtil.isEmpty( chisel ) )
		{
			return false;
		}

		if ( ChiselsAndBits.getConfig().getServer().enableChiselToolHarvestCheck.get() )
		{
			// this is the earily check.
			if ( state.getBlock() instanceof BlockChiseled )
			{
				return ( (BlockChiseled) state.getBlock() ).basicHarvestBlockTest( world, pos, player );
			}

			do
			{
				final Block blk = world.getBlockState( pos ).getBlock();
				BlockChiseled.setActingAs( state );
				testingChisel = true;
				chiselSlot.swapWithWeapon();
				final boolean canHarvest = world.getBlockState(pos).canHarvestBlock(world, pos, player);
				chiselSlot.swapWithWeapon();
				testingChisel = false;
				BlockChiseled.setActingAs( null );

				if ( canHarvest )
				{
					return true;
				}

				chiselInv.fail( targetState );

				chiselSlot = chiselInv.getItem( targetState );
				chisel = chiselSlot.getStack();
			}
			while ( !ModUtil.isEmpty( chisel ) );

			return false;
		}

		return true;
	}

	@Override
	public boolean canHarvestBlock(
			final BlockState blk )
	{
		Item it;

		final IItemTier tier = getTier();
        if (DIAMOND.equals(tier))
        {
            it = Items.DIAMOND_PICKAXE;
        }
        else if (GOLD.equals(tier))
        {
            it = Items.GOLDEN_PICKAXE;
        }
        else if (IRON.equals(tier))
        {
            it = Items.IRON_PICKAXE;
        }
        else if (WOOD.equals(tier))
        {
            it = Items.WOODEN_PICKAXE;
        }
        else
        {
            it = Items.STONE_PICKAXE;
        }

		return blk.getBlock() instanceof BlockChiseled || it.canHarvestBlock( blk );
	}

    @Override
    public int getHarvestLevel(final ItemStack stack, final ToolType tool, @Nullable final PlayerEntity player, @Nullable final BlockState blockState)
    {
        if ( testingChisel && stack.getItem() instanceof ItemChisel )
        {
            final String pattern = "(^|,)" + Pattern.quote( tool.getName() ) + "(,|$)";

            final Pattern p = Pattern.compile( pattern );
            final Matcher m = p.matcher( ChiselsAndBits.getConfig().getServer().enableChiselToolHarvestCheckTools.get() );

            if ( m.find() )
            {
                final ItemChisel ic = (ItemChisel) stack.getItem();
                return ic.getTier().getHarvestLevel();
            }
        }

        return super.getHarvestLevel( stack, tool, player, blockState );
    }

	@Override
	public void scroll(
			final PlayerEntity player,
			final ItemStack stack,
			final int dwheel )
	{
		final IToolMode mode = ChiselModeManager.getChiselMode( player, ChiselToolType.CHISEL, Hand.MAIN_HAND );
		ChiselModeManager.scrollOption( ChiselToolType.CHISEL, mode, mode, dwheel );
	}

    @Override
    public ItemStack getContainerItem(final ItemStack itemStack)
    {
        return itemStack;
    }

    @Override
    public boolean hasContainerItem(final ItemStack stack)
    {
        return true;
    }
}
