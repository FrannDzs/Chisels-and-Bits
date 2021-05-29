package mod.chiselsandbits.items;

import com.google.common.base.Stopwatch;
import mod.chiselsandbits.api.ReplacementStateHandler;
import mod.chiselsandbits.bitstorage.BlockBitStorage;
import mod.chiselsandbits.chiseledblock.BlockBitInfo;
import mod.chiselsandbits.chiseledblock.BlockChiseled;
import mod.chiselsandbits.chiseledblock.BlockChiseled.ReplaceWithChiseledValue;
import mod.chiselsandbits.chiseledblock.TileEntityBlockChiseled;
import mod.chiselsandbits.chiseledblock.data.BitLocation;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.core.Log;
import mod.chiselsandbits.events.TickHandler;
import mod.chiselsandbits.helpers.*;
import mod.chiselsandbits.interfaces.ICacheClearable;
import mod.chiselsandbits.interfaces.IChiselModeItem;
import mod.chiselsandbits.interfaces.IItemScrollWheel;
import mod.chiselsandbits.items.ItemBitBag.BagPos;
import mod.chiselsandbits.modes.ChiselMode;
import mod.chiselsandbits.modes.IToolMode;
import mod.chiselsandbits.network.packets.PacketChisel;
import mod.chiselsandbits.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.*;
import net.minecraft.nbt.IntNBT;
import net.minecraft.state.Property;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ItemChiseledBit extends Item implements IItemScrollWheel, IChiselModeItem, ICacheClearable
{

	public static boolean bitBagStackLimitHack;

	private ArrayList<ItemStack> bits;

	public ItemChiseledBit(Item.Properties properties)
	{
		super(properties);
		ChiselsAndBits.getInstance().addClearable( this );
	}

	@Override
	@OnlyIn( Dist.CLIENT )
	public void addInformation(
			final ItemStack stack,
			final World worldIn,
			final List<ITextComponent> tooltip,
			final ITooltipFlag advanced )
	{
		super.addInformation( stack, worldIn, tooltip, advanced );
		ChiselsAndBits.getConfig().getCommon().helpText( LocalStrings.HelpBit, tooltip,
				ClientSide.instance.getKeyName( Minecraft.getInstance().gameSettings.keyBindAttack ),
				ClientSide.instance.getKeyName( Minecraft.getInstance().gameSettings.keyBindUseItem ),
				ClientSide.instance.getModeKey() );

		final int stateId = ItemChiseledBit.getStackState(stack);
		if (stateId == 0) {
		    tooltip.add(new StringTextComponent(TextFormatting.RED.toString() + TextFormatting.ITALIC.toString() + LocalStrings.AnyHelpBit.getLocal() + TextFormatting.RESET.toString()));
        }
	}

    @Override
    public ITextComponent getHighlightTip(final ItemStack item, final ITextComponent displayName)
    {
        return DistExecutor.unsafeRunForDist(() -> () -> {
            if ( ChiselsAndBits.getConfig().getClient().itemNameModeDisplay.get() && displayName instanceof IFormattableTextComponent)
            {
                String extra = "";
                if ( getBitOperation( ClientSide.instance.getPlayer(), Hand.MAIN_HAND, item ) == BitOperation.REPLACE )
                {
                    extra = " - " + LocalStrings.BitOptionReplace.getLocal();
                }

                final IFormattableTextComponent comp = (IFormattableTextComponent) displayName;

                return comp.appendString(" - ").append(new StringTextComponent(ChiselModeManager.getChiselMode( ClientSide.instance.getPlayer(), ChiselToolType.BIT, Hand.MAIN_HAND ).getName().getLocal())).append(new StringTextComponent(extra));
            }

            return displayName;
        },
          () -> () -> displayName);
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
		return ItemChisel.fromBreakToChisel( ChiselMode.castMode( ChiselModeManager.getChiselMode( player, ChiselToolType.BIT, Hand.MAIN_HAND ) ), itemstack, pos, player, Hand.MAIN_HAND );
	}

	public static ITextComponent getBitStateName(
			final BlockState state )
	{
		ItemStack target = null;
		Block blk = null;

		if ( state == null )
		{
			return new StringTextComponent("Null");
		}

		try
		{
			// for an unknown reason its possible to generate mod blocks without
			// proper state here...
			blk = state.getBlock();

			final Item item = Item.getItemFromBlock( blk );
			if ( ModUtil.isEmpty( item ) )
			{
				final Fluid f = BlockBitInfo.getFluidFromBlock( blk );
				if ( f != null )
				{
					return new TranslationTextComponent(f.getAttributes().getTranslationKey());
				}
			}
			else
			{
				target = new ItemStack(() -> Item.getItemFromBlock(state.getBlock()) ,1);
			}
		}
		catch ( final IllegalArgumentException e )
		{
			Log.logError( "Unable to get Item Details for Bit.", e );
		}

		if ( target == null || target.getItem() == null )
		{
			return null;
		}

		try
		{
			final ITextComponent myName = target.getDisplayName();
            if (!(myName instanceof IFormattableTextComponent))
                return myName;

            final IFormattableTextComponent formattableName = (IFormattableTextComponent) myName;

			final Set<String> extra = new HashSet<String>();
			if ( blk != null && state != null )
			{
				for ( final Property<?> p : state.getProperties() )
				{
					if ( p.getName().equals( "axis" ) || p.getName().equals( "facing" ) )
					{
						extra.add( DeprecationHelper.translateToLocal( "mod.chiselsandbits.pretty." + p.getName() + "-" + state.get( p ).toString() ) );
					}
				}
			}

			if ( extra.isEmpty() )
			{
				return myName;
			}

			for ( final String x : extra )
			{
				formattableName.appendString(" ").appendString( x );
			}

			return formattableName;
		}
		catch ( final Exception e )
		{
			return new StringTextComponent("Error");
		}
	}

    static private final NonNullList<ItemStack> alternativeStacks = NonNullList.create();

	public static ITextComponent getBitTypeName(
			final ItemStack stack )
	{
	    int stateID = ItemChiseledBit.getStackState( stack );
        if (stateID == 0) {
            //We are running an empty bit, for display purposes.
            //Lets loop:
            if (alternativeStacks.isEmpty())
                ModItems.ITEM_BLOCK_BIT.get().fillItemGroup(Objects.requireNonNull(ModItems.ITEM_BLOCK_BIT.get().getGroup()), alternativeStacks);

            stateID = ItemChiseledBit.getStackState( alternativeStacks.get  ((int) (TickHandler.getClientTicks() % ((alternativeStacks.size() * 20L)) / 20L)));

            if (stateID == 0)
                alternativeStacks.clear();
        }

		return getBitStateName( ModUtil.getStateById( stateID ) );
	}

	@Override
	public ITextComponent getDisplayName(
			final ItemStack stack )
	{
		final ITextComponent typeName = getBitTypeName( stack );

		if ( typeName == null )
		{
			return super.getDisplayName( stack );
		}

		final IFormattableTextComponent strComponent = new StringTextComponent("");
		return strComponent.append(super.getDisplayName( stack ))
          .appendString(" - ")
          .append(typeName);
	}



	@SuppressWarnings( "deprecation" )
	@Override
	public int getItemStackLimit(ItemStack stack)
	{
		return bitBagStackLimitHack ? ChiselsAndBits.getConfig().getServer().bagStackSize.get() : super.getItemStackLimit(stack);
	}

    @Override
    public ActionResultType onItemUse(final ItemUseContext context)
    {
        if ( !context.getWorld().isRemote )
        {
            return ActionResultType.PASS;
        }

        final Pair<Vector3d, Vector3d> PlayerRay = ModUtil.getPlayerRay( context.getPlayer() );
        final Vector3d ray_from = PlayerRay.getLeft();
        final Vector3d ray_to = PlayerRay.getRight();
        final RayTraceContext rtc = new RayTraceContext(ray_from, ray_to, RayTraceContext.BlockMode.VISUAL, RayTraceContext.FluidMode.NONE, context.getPlayer());

        final RayTraceResult mop = context.getWorld().rayTraceBlocks(rtc);
        if (mop != null)
        {
            final BlockRayTraceResult rayTraceResult = (BlockRayTraceResult) mop;
            return onItemUseInternal(
              context.getPlayer(),
              context.getWorld(),
              context.getPos(),
              context.getHand(),
              rayTraceResult
            );
        }

        return ActionResultType.FAIL;
    }

	public ActionResultType onItemUseInternal (
			final @Nonnull PlayerEntity player,
			final @Nonnull World world,
			final @Nonnull BlockPos usedBlock,
			final @Nonnull Hand hand,
            final @Nonnull BlockRayTraceResult rayTraceResult)
	{
		final ItemStack stack = player.getHeldItem( hand );

		if ( !player.canPlayerEdit( usedBlock, rayTraceResult.getFace(), stack ) )
		{
			return ActionResultType.FAIL;
		}

		// forward interactions to tank...
		final BlockState usedState = world.getBlockState( usedBlock );
		final Block blk = usedState.getBlock();
		if ( blk instanceof BlockBitStorage)
		{
			if ( blk.onBlockActivated( usedState, world, usedBlock, player, hand, rayTraceResult) == ActionResultType.SUCCESS)
			{
				return ActionResultType.SUCCESS;
			}
			return ActionResultType.FAIL;
		}

		if ( world.isRemote )
		{
			final IToolMode mode = ChiselModeManager.getChiselMode( player, ClientSide.instance.getHeldToolType( hand ), hand );
			final BitLocation bitLocation = new BitLocation( rayTraceResult, getBitOperation( player, hand, stack ), false );


			BlockState blkstate = world.getBlockState( bitLocation.blockPos );
			TileEntityBlockChiseled tebc = ModUtil.getChiseledTileEntity( world, bitLocation.blockPos, true );
			ReplaceWithChiseledValue rv = null;
			if ( tebc == null && (rv=BlockChiseled.replaceWithChiseled( world, bitLocation.blockPos, blkstate, ItemChiseledBit.getStackState( stack ), true )).success )
			{
				blkstate = world.getBlockState( bitLocation.blockPos );
				tebc = rv.te;
			}

			if ( tebc != null )
			{
				PacketChisel pc = null;
				if ( mode == ChiselMode.DRAWN_REGION )
				{
					if ( world.isRemote )
					{
						ClientSide.instance.pointAt( getBitOperation( player, hand, stack ).getToolType(), bitLocation, hand );
					}
					return ActionResultType.FAIL;
				}
				else
				{
					pc = new PacketChisel( getBitOperation( player, hand, stack ), bitLocation, rayTraceResult.getFace(), ChiselMode.castMode( mode ), hand );
				}

				final int result = pc.doAction( player );

				if ( result > 0 )
				{
				    ClientSide.instance.setLastTool(ChiselToolType.BIT);
				    ChiselsAndBits.getNetworkChannel().sendToServer(pc);
				}
			}
		}

		return ActionResultType.SUCCESS;

	}

    @Override
    public boolean canHarvestBlock(final ItemStack stack, final BlockState state)
    {
        return state.getBlock() instanceof BlockChiseled || super.canHarvestBlock( stack, state);
    }

	@Override
	public boolean canHarvestBlock(
			final BlockState blk )
	{
		return blk.getBlock() instanceof BlockChiseled || super.canHarvestBlock( blk );
	}

	public static BitOperation getBitOperation(
			final PlayerEntity player,
			final Hand hand,
			final ItemStack stack )
	{
		return ReplacementStateHandler.getInstance().isReplacing() ? BitOperation.REPLACE : BitOperation.PLACE;
	}

	@Override
	public void clearCache()
	{
		bits = null;
	}

    @Override
    public void fillItemGroup(final ItemGroup tab, final NonNullList<ItemStack> items)
    {
        if ( !this.isInGroup( tab ) ) // is this my creative tab?
        {
            return;
        }

        if ( bits == null )
        {
            bits = new ArrayList<ItemStack>();

            final NonNullList<ItemStack> List = NonNullList.create();
            final BitSet used = new BitSet( 4096 );

            for ( final Object obj : ForgeRegistries.ITEMS)
            {
                if ( !( obj instanceof BlockItem ) )
                {
                    continue;
                }

                try
                {
                    Item it = (Item) obj;
                    final ItemGroup ctab = it.getGroup();

                    if ( ctab != null )
                    {
                        it.fillItemGroup( ctab, List );
                    }

                    for ( final ItemStack out : List )
                    {
                        it = out.getItem();

                        if ( !( it instanceof BlockItem ) )
                        {
                            continue;
                        }

                        final BlockState state = DeprecationHelper.getStateFromItem( out );
                        if ( state != null && BlockBitInfo.canChisel( state ) )
                        {
                            used.set( ModUtil.getStateId( state ) );
                            bits.add( ItemChiseledBit.createStack( ModUtil.getStateId( state ), 1, false ) );
                        }
                    }

                }
                catch ( final Throwable t )
                {
                    // a mod did something that isn't acceptable, let them crash
                    // in their own code...
                }

                List.clear();
            }

            for ( final Fluid o : ForgeRegistries.FLUIDS )
            {
                if ( !o.getDefaultState().isSource() )
                {
                    continue;
                }

                bits.add( ItemChiseledBit.createStack( Block.getStateId( o.getDefaultState().getBlockState() ), 1, false ) );
            }
        }

        items.addAll( bits );
    }

	public static boolean sameBit(
			final ItemStack output,
			final int blk )
	{
		return output.hasTag() ? getStackState( output ) == blk : false;
	}

	public static @Nonnull ItemStack createStack(
			final int id,
			final int count,
			final boolean RequireStack )
	{
		final ItemStack out = new ItemStack( ModItems.ITEM_BLOCK_BIT.get(), count );
		out.setTagInfo( "id", IntNBT.valueOf(id) );
		return out;
	}

	@Override
	public void scroll(
			final PlayerEntity player,
			final ItemStack stack,
			final int dwheel )
	{
		final IToolMode mode = ChiselModeManager.getChiselMode( player, ChiselToolType.BIT, Hand.MAIN_HAND );
		ChiselModeManager.scrollOption( ChiselToolType.BIT, mode, mode, dwheel );
	}

	public static int getStackState(
			final ItemStack inHand )
	{
		return inHand != null && inHand.hasTag() ? ModUtil.getTagCompound( inHand ).getInt( "id" ) : 0;
	}

	public static boolean placeBit(
			final IContinuousInventory bits,
			final ActingPlayer player,
			final VoxelBlob vb,
			final int x,
			final int y,
			final int z )
	{
		if ( vb.get( x, y, z ) == 0 )
		{
			final IItemInInventory slot = bits.getItem( 0 );
			final int stateID = ItemChiseledBit.getStackState( slot.getStack() );

			if ( slot.isValid() )
			{
				if ( !player.isCreative() )
				{
					if ( bits.useItem( stateID ) )
						vb.set( x, y, z, stateID );
				}
				else
					vb.set( x, y, z, stateID );
			}

			return true;
		}

		return false;
	}

	public static boolean hasBitSpace(
			final PlayerEntity player,
			final int blk )
	{
		final List<BagPos> bags = ItemBitBag.getBags( player.inventory );
		for ( final BagPos bp : bags )
		{
			for ( int x = 0; x < bp.inv.getSizeInventory(); x++ )
			{
				final ItemStack is = bp.inv.getStackInSlot( x );
				if( ( ItemChiseledBit.sameBit( is, blk ) && ModUtil.getStackSize( is ) < bp.inv.getInventoryStackLimit() ) || ModUtil.isEmpty( is ) )
				{
					return true;
				}
			}
		}
		for ( int x = 0; x < 36; x++ )
		{
			final ItemStack is = player.inventory.getStackInSlot( x );
			if( ( ItemChiseledBit.sameBit( is, blk ) && ModUtil.getStackSize( is ) < is.getMaxStackSize() ) || ModUtil.isEmpty( is ) )
			{
				return true;
			}
		}
		return false;
	}

	private static Stopwatch timer;

	public static boolean checkRequiredSpace(
			final PlayerEntity player,
			final BlockState blkstate) {
		if ( ChiselsAndBits.getConfig().getServer().requireBagSpace.get() && !player.isCreative() )
		{
			//Cycle every item in any bag, if the player can't store the clicked block then
			//send them a message.
			final int stateId = ModUtil.getStateId( blkstate );
			if ( !ItemChiseledBit.hasBitSpace( player, stateId ) )
			{
				if( player.getEntityWorld().isRemote && ( timer == null || timer.elapsed( TimeUnit.MILLISECONDS ) > 1000 ) )
				{
					//Timer is client-sided so it doesn't have to be made player-specific
					timer = Stopwatch.createStarted();
					//Only client should handle messaging.
					player.sendMessage( new TranslationTextComponent( "mod.chiselsandbits.result.require_bag" ), Util.DUMMY_UUID );
				}
				return true;
			}
		}
		return false;
	}
}
