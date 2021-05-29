package mod.chiselsandbits.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import mod.chiselsandbits.bitbag.BagInventory;
import mod.chiselsandbits.chiseledblock.BlockBitInfo;
import mod.chiselsandbits.chiseledblock.BlockChiseled;
import mod.chiselsandbits.chiseledblock.ItemBlockChiseled;
import mod.chiselsandbits.chiseledblock.NBTBlobConverter;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.helpers.ActingPlayer;
import mod.chiselsandbits.helpers.BitInventoryFeeder;
import mod.chiselsandbits.helpers.ContinousChisels;
import mod.chiselsandbits.helpers.IContinuousInventory;
import mod.chiselsandbits.helpers.IItemInInventory;
import mod.chiselsandbits.helpers.LocalStrings;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.interfaces.IChiselModeItem;
import mod.chiselsandbits.modes.PositivePatternMode;
import mod.chiselsandbits.network.packets.PacketAccurateSneakPlace;
import mod.chiselsandbits.network.packets.PacketAccurateSneakPlace.IItemBlockAccurate;
import mod.chiselsandbits.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.thread.EffectiveSide;

public class ItemPositivePrint extends ItemNegativePrint implements IChiselModeItem, IItemBlockAccurate
{

    public ItemPositivePrint(final Properties properties)
    {
        super(properties);
    }

    @Override
    protected Item getWrittenItem()
    {
        return ModItems.ITEM_POSITIVE_PRINT_WRITTEN.get();
    }

    @Override
	@OnlyIn( Dist.CLIENT )
	public void addInformation(
			final ItemStack stack,
			final World worldIn,
			final List<ITextComponent> tooltip,
			final ITooltipFlag advanced )
	{
		defaultAddInfo( stack, worldIn, tooltip, advanced );
		ChiselsAndBits.getConfig().getCommon().helpText( LocalStrings.HelpPositivePrint, tooltip,
				ClientSide.instance.getKeyName( Minecraft.getInstance().gameSettings.keyBindUseItem ),
				ClientSide.instance.getKeyName( Minecraft.getInstance().gameSettings.keyBindUseItem ),
				ClientSide.instance.getModeKey() );

		if ( stack.hasTag() )
		{
			if ( ClientSide.instance.holdingShift() )
			{
				if ( toolTipCache.needsUpdate( stack ) )
				{
					final VoxelBlob blob = ModUtil.getBlobFromStack( stack, null );
					toolTipCache.updateCachedValue( blob.listContents( new ArrayList<ITextComponent>() ) );
				}

				tooltip.addAll( toolTipCache.getCached() );
			}
			else
			{
				tooltip.add( new StringTextComponent(LocalStrings.ShiftDetails.getLocal()) );
			}
		}
	}

	@Override
	protected CompoundNBT getCompoundFromBlock(
			final World world,
			final BlockPos pos,
			final PlayerEntity player )
	{
		final BlockState state = world.getBlockState( pos );
		final Block blkObj = state.getBlock();

		if ( !( blkObj instanceof BlockChiseled ) && BlockBitInfo.canChisel( state ) )
		{
			final NBTBlobConverter tmp = new NBTBlobConverter();

			tmp.fillWith( state );
			final CompoundNBT comp = new CompoundNBT();
			tmp.writeChiselData( comp, false );

			comp.putByte( ModUtil.NBT_SIDE, (byte) ModUtil.getPlaceFace( player ).ordinal() );
			return comp;
		}

		return super.getCompoundFromBlock( world, pos, player );
	}

	@Override
	protected boolean convertToStone()
	{
		return false;
	}

    @Override
    public ActionResultType onItemUse(final ItemUseContext context)
    {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        Hand hand = context.getHand();
        BlockPos pos = context.getPos();

        final ItemStack stack = player.getHeldItem( hand );
        final BlockState blkstate = world.getBlockState( pos );

        if ( ItemChiseledBit.checkRequiredSpace( player, blkstate ) )
        {
            return ActionResultType.FAIL;
        }

        boolean offgrid = false;

        if ( PositivePatternMode.getMode( stack ) == PositivePatternMode.PLACEMENT )
        {
            if ( !world.isRemote )
            {
                // Say it "worked", Don't do anything we'll get a better
                // packet.
                return ActionResultType.SUCCESS;
            }

            // send accurate packet.
            final PacketAccurateSneakPlace pasp = new PacketAccurateSneakPlace(
              context.getItem(),
              pos,
              hand,
              context.getFace(),
              context.getHitVec().x,
              context.getHitVec().y,
              context.getHitVec().z,
              false
            );

            ChiselsAndBits.getNetworkChannel().sendToServer(pasp);
        }

        return placeItem(context , offgrid );
    }

	public final ActionResultType placeItem(
			final ItemUseContext context,
			boolean offgrid )
	{
        ItemStack stack = context.getItem();
        PlayerEntity player = context.getPlayer();
        Hand hand = context.getHand();
        BlockPos pos = context.getPos();

		if ( PositivePatternMode.getMode( stack ) == PositivePatternMode.PLACEMENT )
		{
			final ItemStack output = getPatternedItem( stack, false );
			if ( output != null )
			{
				final VoxelBlob pattern = ModUtil.getBlobFromStack( stack, player );
				final Map<Integer, Integer> stats = pattern.getBlockSums();

				if ( consumeEntirePattern( pattern, stats, pos, ActingPlayer.testingAs( player, hand ) ) && output.getItem() instanceof ItemBlockChiseled )
				{
					final ItemBlockChiseled ibc = (ItemBlockChiseled) output.getItem();
					final ActionResultType res = ibc.tryPlace( context, offgrid );

					if ( res == ActionResultType.SUCCESS )
					{
						consumeEntirePattern( pattern, stats, pos, ActingPlayer.actingAs( player, hand ) );
					}

					return res;
				}

				return ActionResultType.FAIL;
			}
		}

		return super.onItemUse( context );
	}

	private boolean consumeEntirePattern(
			final VoxelBlob pattern,
			final Map<Integer, Integer> stats,
			final BlockPos pos,
			final ActingPlayer player )
	{
		final List<BagInventory> bags = ModUtil.getBags( player );

		for ( final Entry<Integer, Integer> type : stats.entrySet() )
		{
			final int inPattern = type.getKey();

			if ( type.getKey() == 0 )
			{
				continue;
			}

			IItemInInventory bit = ModUtil.findBit( player, pos, inPattern );
			int stillNeeded = type.getValue() - ModUtil.consumeBagBit( bags, inPattern, type.getValue() );
			if ( stillNeeded != 0 )
			{
				for ( int x = stillNeeded; x > 0 && bit.isValid(); --x )
				{
					if ( bit.consume() )
					{
						stillNeeded--;
						bit = ModUtil.findBit( player, pos, inPattern );
					}
				}

				if ( stillNeeded != 0 )
				{
					return false;
				}
			}
		}

		return true;
	}

	@Override
	protected void applyPrint(
			final ItemStack stack,
			final World world,
			final BlockPos pos,
			final Direction side,
			final VoxelBlob vb,
			final VoxelBlob pattern,
			final PlayerEntity who,
			final Hand hand )
	{
		// snag a tool...
		final ActingPlayer player = ActingPlayer.actingAs( who, hand );
		final IContinuousInventory selected = new ContinousChisels( player, pos, side );
		ItemStack spawnedItem = null;

		final VoxelBlob filled = new VoxelBlob();

		final List<BagInventory> bags = ModUtil.getBags( player );
		final List<ItemEntity> spawnlist = new ArrayList<>();

		final PositivePatternMode chiselMode = PositivePatternMode.getMode( stack );
		final boolean chisel_bits = chiselMode == PositivePatternMode.IMPOSE || chiselMode == PositivePatternMode.REPLACE;
		final boolean chisel_to_air = chiselMode == PositivePatternMode.REPLACE;

		for ( int y = 0; y < vb.detail; y++ )
		{
			for ( int z = 0; z < vb.detail; z++ )
			{
				for ( int x = 0; x < vb.detail; x++ )
				{
					int inPlace = vb.get( x, y, z );
					final int inPattern = pattern.get( x, y, z );
					if ( inPlace != inPattern )
					{
						if ( inPlace != 0 && chisel_bits && selected.isValid() )
						{
							if ( chisel_to_air || inPattern != 0 )
							{
								spawnedItem = ItemChisel.chiselBlock( selected, player, vb, world, pos, side, x, y, z, spawnedItem, spawnlist );

								if ( spawnedItem != null )
								{
									inPlace = 0;
								}
							}
						}

						if ( inPlace == 0 && inPattern != 0 && filled.get( x, y, z ) == 0 )
						{
							final IItemInInventory bit = ModUtil.findBit( player, pos, inPattern );
							if ( ModUtil.consumeBagBit( bags, inPattern, 1 ) == 1 )
							{
								vb.set( x, y, z, inPattern );
							}
							else if ( bit.isValid() )
							{
								if ( !player.isCreative() )
								{
									if ( bit.consume() )
										vb.set( x, y, z, inPattern );
								}
								else
									vb.set( x, y, z, inPattern );
							}
						}
					}
				}
			}
		}

		BitInventoryFeeder feeder = new BitInventoryFeeder( who, world );
		for ( final ItemEntity ei : spawnlist )
		{
			feeder.addItem( ei );
			ItemBitBag.cleanupInventory( who, ei.getItem() );
		}

	}

    @Override
    public ITextComponent getHighlightTip(final ItemStack item, final ITextComponent displayName)
    {
        if (EffectiveSide.get().isClient() && displayName instanceof IFormattableTextComponent && ChiselsAndBits.getConfig().getClient().itemNameModeDisplay.get() )
        {
            IFormattableTextComponent formattableTextComponent = (IFormattableTextComponent) displayName;
            return formattableTextComponent.appendString(" - ").appendString(PositivePatternMode.getMode( item ).string.getLocal());
        }

        return displayName;
    }



    @Override
    public ActionResultType tryPlace(final ItemUseContext context, final boolean offGrid)
    {
        if ( PositivePatternMode.getMode( context.getItem() ) == PositivePatternMode.PLACEMENT )
        {
            final ItemStack output = getPatternedItem( context.getItem(), false );
            if ( output != null )
            {
                final VoxelBlob pattern = ModUtil.getBlobFromStack( context.getItem(), context.getPlayer() );
                final Map<Integer, Integer> stats = pattern.getBlockSums();

                if ( consumeEntirePattern( pattern, stats, context.getPos(), ActingPlayer.testingAs( context.getPlayer(), context.getHand() ) ) && output.getItem() instanceof ItemBlockChiseled )
                {
                    final ItemBlockChiseled ibc = (ItemBlockChiseled) output.getItem();
                    final ActionResultType res = ibc.tryPlace( new BlockItemUseContext(context), offGrid );

                    if ( res == ActionResultType.SUCCESS )
                    {
                        consumeEntirePattern( pattern, stats, context.getPos(), ActingPlayer.actingAs( context.getPlayer(), context.getHand() ) );
                    }

                    return res;
                }

                return ActionResultType.FAIL;
            }
        }

        return super.onItemUse( context );
    }
}
