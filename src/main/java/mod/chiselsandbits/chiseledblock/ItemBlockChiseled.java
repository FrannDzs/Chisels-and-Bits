package mod.chiselsandbits.chiseledblock;

import mod.chiselsandbits.api.EventBlockBitModification;
import mod.chiselsandbits.api.IBitAccess;
import mod.chiselsandbits.chiseledblock.BlockChiseled.ReplaceWithChiseledValue;
import mod.chiselsandbits.chiseledblock.data.BitLocation;
import mod.chiselsandbits.chiseledblock.data.IntegerBox;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.client.UndoTracker;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.helpers.BitOperation;
import mod.chiselsandbits.helpers.DeprecationHelper;
import mod.chiselsandbits.helpers.LocalStrings;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.interfaces.IItemScrollWheel;
import mod.chiselsandbits.interfaces.IVoxelBlobItem;
import mod.chiselsandbits.items.ItemChiseledBit;
import mod.chiselsandbits.network.packets.PacketAccurateSneakPlace;
import mod.chiselsandbits.network.packets.PacketAccurateSneakPlace.IItemBlockAccurate;
import mod.chiselsandbits.network.packets.PacketRotateVoxelBlob;
import mod.chiselsandbits.render.helpers.SimpleInstanceCache;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.*;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ItemBlockChiseled extends BlockItem implements IVoxelBlobItem, IItemScrollWheel, IItemBlockAccurate
{

	SimpleInstanceCache<ItemStack, List<ITextComponent>> tooltipCache = new SimpleInstanceCache<ItemStack, List<ITextComponent>>( null, new ArrayList<ITextComponent>() );

	public ItemBlockChiseled(
			final Block block, Item.Properties builder )
	{
		super( block , builder);
	}

	@OnlyIn(Dist.CLIENT)
    @Override
    public void addInformation(
      final ItemStack stack, @Nullable final World worldIn, final List<ITextComponent> tooltip, final ITooltipFlag flagIn)
    {
        super.addInformation( stack, worldIn, tooltip, flagIn);
        ChiselsAndBits.getConfig().getCommon().helpText(LocalStrings.HelpChiseledBlock, tooltip,
          ClientSide.instance.getKeyName( Minecraft.getInstance().gameSettings.keyBindUseItem ),
          ClientSide.instance.getKeyName( ClientSide.getOffGridPlacementKey() ) );

        if ( stack.hasTag() )
        {
            if ( ClientSide.instance.holdingShift() )
            {
                if ( tooltipCache.needsUpdate( stack ) )
                {
                    final VoxelBlob blob = ModUtil.getBlobFromStack( stack, null );
                    tooltipCache.updateCachedValue( blob.listContents( new ArrayList<>() ) );
                }

                tooltip.addAll( tooltipCache.getCached() );
            }
            else
            {
                tooltip.add( new StringTextComponent( LocalStrings.ShiftDetails.getLocal() ) );
            }
        }
    }

    @Override
    protected boolean canPlace(final BlockItemUseContext p_195944_1_, final BlockState p_195944_2_)
    {
        //TODO: Check for offgrid logic.
        return canPlaceBlockHere(p_195944_1_.getWorld(), p_195944_1_.getPos(), p_195944_1_.getFace(), p_195944_1_.getPlayer(), p_195944_1_.getHand(), p_195944_1_.getItem(), p_195944_1_.getHitVec().x, p_195944_1_.getHitVec().y, p_195944_1_.getHitVec().z, false);
    }

	public boolean vanillaStylePlacementTest(
			final @Nonnull World worldIn,
			@Nonnull BlockPos pos,
			@Nonnull Direction side,
			final PlayerEntity player,
			final Hand hand,
			final ItemStack stack )
	{
		final Block block = worldIn.getBlockState( pos ).getBlock();

		if ( block == Blocks.SNOW )
		{
			side = Direction.UP;
		}
		else if ( !block.isReplaceable(worldIn.getBlockState(pos), new BlockItemUseContext(player, hand, stack, new BlockRayTraceResult(new Vector3d(0.5, 0.5, 0.5), side, pos, false))) )
		{
			pos = pos.offset( side );
		}

		return true;
	}

	public boolean canPlaceBlockHere(
			final @Nonnull World worldIn,
			final @Nonnull BlockPos pos,
			final @Nonnull Direction side,
			final PlayerEntity player,
			final Hand hand,
			final ItemStack stack,
            final double hitX,
            final double hitY,
            final double hitZ,
			boolean offgrid )
	{
		if ( vanillaStylePlacementTest( worldIn, pos, side, player, hand, stack ) )
		{
			return true;
		}

		if ( offgrid )
		{
			return true;
		}

		if ( tryPlaceBlockAt( getBlock(), stack, player, worldIn, pos, side, Hand.MAIN_HAND, hitX, hitY, hitZ, null, false ).isCanPlace() )
		{
			return true;
		}

		return tryPlaceBlockAt( getBlock(), stack, player, worldIn, pos.offset( side ), side, Hand.MAIN_HAND, hitX, hitY, hitZ, null, false ).isCanPlace();
	}

    @Override
    public ActionResultType onItemUse(final ItemUseContext context)
    {
        final ItemStack stack = context.getPlayer().getHeldItem( context.getHand() );

        if ( !context.getWorld().isRemote && !(context.getPlayer() instanceof FakePlayer))
        {
            // Say it "worked", Don't do anything we'll get a better packet.
            return ActionResultType.SUCCESS;
        }

        // send accurate packet.
        final PacketAccurateSneakPlace pasp = new PacketAccurateSneakPlace(
          context.getItem(),
          context.getPos(),
          context.getHand(),
          context.getFace(),
          context.getHitVec().x,
          context.getHitVec().y,
          context.getHitVec().z,
          ClientSide.offGridPlacement( context.getPlayer() ) //TODO: Figure out the placement logic.
        );

        ChiselsAndBits.getNetworkChannel().sendToServer(pasp);
        //TODO: Figure out the placement logic.
        return tryPlace( new BlockItemUseContext(context), ClientSide.offGridPlacement( context.getPlayer() ) );
    }

    @Override
    public ActionResultType tryPlace(final BlockItemUseContext context)
    {
        return tryPlace(context, false);
    }

    @Override
    protected boolean placeBlock(final BlockItemUseContext context, final BlockState state)
    {
        return placeBitBlock(
          context.getItem(),
          context.getPlayer(),
          context.getWorld(),
          context.getPos(),
          context.getFace(),
          context.getHitVec().x,
          context.getHitVec().y,
          context.getHitVec().z,
          state,
          false
        );
    }

	public boolean placeBitBlock(
			final ItemStack stack,
			final PlayerEntity player,
			final World world,
			final BlockPos pos,
			final Direction side,
			final double hitX,
			final double hitY,
			final double hitZ,
			final BlockState newState,
			boolean offgrid )
	{
		if ( offgrid )
		{
			final BitLocation bl = new BitLocation( new BlockRayTraceResult( new Vector3d( hitX, hitY, hitZ ), side, pos , false), BitOperation.PLACE, true );
			return tryPlaceBlockAt( block, stack, player, world, bl.blockPos, side, Hand.MAIN_HAND, hitX, hitY, hitZ, new BlockPos( bl.bitX, bl.bitY, bl.bitZ ), true ).isCanPlace();
		}
		else
		{
			return tryPlaceBlockAt( block, stack, player, world, pos, side, Hand.MAIN_HAND, hitX, hitY, hitZ, null, true ).isCanPlace();
		}
	}

	static public PlacementAttemptResult tryPlaceBlockAt(
			final @Nonnull Block block,
			final @Nonnull ItemStack stack,
			final @Nonnull PlayerEntity player,
			final @Nonnull World world,
			@Nonnull BlockPos pos,
			final @Nonnull Direction side,
			final @Nonnull Hand hand,
            final double hitX,
            final double hitY,
            final double hitZ,
			final BlockPos partial,
			final boolean modulateWorld )
	{
		final VoxelBlob[][][] blobs = new VoxelBlob[2][2][2];

		// you can't place empty blocks...
		if ( !stack.hasTag() )
		{
			return PlacementAttemptResult.FAILED;
		}

		final VoxelBlob source = ModUtil.getBlobFromStack( stack, player );

		final IntegerBox modelBounds = source.getBounds();
		BlockPos offset = partial == null || modelBounds == null ? new BlockPos( 0, 0, 0 ) : ModUtil.getPartialOffset( side, partial, modelBounds );

		if ( offset.getX() < 0 )
		{
			pos = pos.add( -1, 0, 0 );
			offset = offset.add( VoxelBlob.dim, 0, 0 );
		}

		if ( offset.getY() < 0 )
		{
			pos = pos.add( 0, -1, 0 );
			offset = offset.add( 0, VoxelBlob.dim, 0 );
		}

		if ( offset.getZ() < 0 )
		{
			pos = pos.add( 0, 0, -1 );
			offset = offset.add( 0, 0, VoxelBlob.dim );
		}

		boolean mergable = false;

		for ( int x = 0; x < 2; x++ )
		{
			for ( int y = 0; y < 2; y++ )
			{
				for ( int z = 0; z < 2; z++ )
				{
					blobs[x][y][z] = source.offset( offset.getX() - source.detail * x, offset.getY() - source.detail * y, offset.getZ() - source.detail * z );
					final int solids = blobs[x][y][z].filled();
					if ( solids > 0 )
					{
						final BlockPos bp = pos.add( x, y, z );

						final EventBlockBitModification bmm = new EventBlockBitModification( world, bp, player, hand, stack, true );
						MinecraftForge.EVENT_BUS.post( bmm );

						// test permissions.
						if ( !world.isBlockModifiable( player, bp ) || bmm.isCanceled() )
						{
							return PlacementAttemptResult.FAILED;
						}

						if (!(world.getTileEntity(bp) instanceof TileEntityBlockChiseled) && (world.isAirBlock( bp ) || world.getBlockState( bp ).isReplaceable(new BlockItemUseContext(
						  player,
                          hand,
                          stack,
                          new BlockRayTraceResult(new Vector3d(hitX, hitY, hitZ), side, bp, false)
                        ))) )
						{
							continue;
						}

						final TileEntityBlockChiseled target = ModUtil.getChiseledTileEntity( world, bp, true );
						if ( target != null )
						{
							if ( !target.canMerge( blobs[x][y][z] ) )
							{
								return PlacementAttemptResult.FAILED;
							}

							if (!target.isEmpty(blobs[x][y][z]))
                            {
                                mergable = true;
                            }

							blobs[x][y][z] = blobs[x][y][z].merge( target.getBlob() );
							continue;
						}

						return PlacementAttemptResult.FAILED;
					}
				}
			}
		}

		if ( modulateWorld )
		{
			UndoTracker.getInstance().beginGroup( player );
			try
			{
				for ( int x = 0; x < 2; x++ )
				{
					for ( int y = 0; y < 2; y++ )
					{
						for ( int z = 0; z < 2; z++ )
						{
							if ( blobs[x][y][z].filled() > 0 )
							{
								final BlockPos bp = pos.add( x, y, z );
								final BlockState state = world.getBlockState( bp );

								if ( world.getBlockState( bp ).isReplaceable(new BlockItemUseContext(
                                  player,
                                  hand,
                                  stack,
                                  new BlockRayTraceResult(new Vector3d(hitX, hitY, hitZ), side, bp, false) //TODO: Figure is a recalc of the hit vector is needed here.
                                )) )
								{
									// clear it...
									world.setBlockState(bp, Blocks.AIR.getDefaultState());
								}

								if ( world.isAirBlock( bp ) )
								{
									final int commonBlock = blobs[x][y][z].getVoxelStats().mostCommonState;
									ReplaceWithChiseledValue rv =  BlockChiseled.replaceWithChiseled( world, bp, state, commonBlock, true );
									if ( rv.success && rv.te != null )
									{
										rv.te.completeEditOperation( blobs[x][y][z] );
									}

									continue;
								}

								final TileEntityBlockChiseled target = ModUtil.getChiseledTileEntity( world, bp, true );
								if ( target != null )
								{
									target.completeEditOperation( blobs[x][y][z] );
									continue;
								}

								return PlacementAttemptResult.FAILED;
							}
						}
					}
				}
			}
			finally
			{
				UndoTracker.getInstance().endGroup( player );
			}
		}

		return mergable ? PlacementAttemptResult.MERGEABLE : PlacementAttemptResult.PLACEABLE;
	}

    @Override
    public ITextComponent getDisplayName(final ItemStack stack)
    {
        final CompoundNBT comp = stack.getTag();

        if ( comp != null )
        {
            final CompoundNBT BlockEntityTag = comp.getCompound( ModUtil.NBT_BLOCKENTITYTAG );
            if ( BlockEntityTag != null )
            {
                final NBTBlobConverter c = new NBTBlobConverter();
                c.readChisleData( BlockEntityTag, VoxelBlob.VERSION_ANY );

                final BlockState state = c.getPrimaryBlockState();
                ITextComponent name = ItemChiseledBit.getBitStateName( state );

                if ( name != null )
                {
                    final ITextComponent parent = super.getDisplayName(stack);
                    if (!(parent instanceof IFormattableTextComponent))
                        return parent;

                    final IFormattableTextComponent formattedParent = (IFormattableTextComponent) parent;
                    return formattedParent.appendString( " - " ).append( name );
                }
            }
        }

        return super.getDisplayName( stack );
    }

    @Override
    public void scroll(final PlayerEntity player, final ItemStack stack, final int dwheel)
    {
        final PacketRotateVoxelBlob p = new PacketRotateVoxelBlob(Direction.Axis.Y, dwheel > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90);
        ChiselsAndBits.getNetworkChannel().sendToServer( p );
    }

	@Override
	public void rotate(
			final ItemStack stack,
			final Direction.Axis axis,
			final Rotation rotation )
	{
		Direction side = ModUtil.getSide( stack );

		if ( axis == Axis.Y )
		{
			switch ( rotation )
			{
				case CLOCKWISE_180:
					side = side.rotateY();
				case CLOCKWISE_90:
					side = side.rotateY();
					break;
				case COUNTERCLOCKWISE_90:
					side = side.rotateYCCW();
					break;
				default:
				case NONE:
					break;
			}
		}
		else
		{
			IBitAccess ba = ChiselsAndBits.getApi().createBitItem( stack );
			ba.rotate( axis, rotation );
			stack.setTag( ba.getBitsAsItem( side, ChiselsAndBits.getApi().getItemType( stack ), false ).getTag() );
		}

		ModUtil.setSide( stack, side );
	}

    @Override
    public ActionResultType tryPlace(final ItemUseContext context, final boolean offgrid)
    {
        final BlockState state = context.getWorld().getBlockState( context.getPos() );
        final Block block = state.getBlock();

        Direction side = context.getFace();
        BlockPos pos = context.getPos();

        if ( block == Blocks.SNOW && state.get(SnowBlock.LAYERS).intValue() < 1 )
        {
            side = Direction.UP;
        }
        else
        {
            boolean canMerge = false;
            if ( context.getItem().hasTag() )
            {
                final TileEntityBlockChiseled tebc = ModUtil.getChiseledTileEntity( context.getWorld(), context.getPos(), true );

                if ( tebc != null )
                {
                    final VoxelBlob blob = ModUtil.getBlobFromStack( context.getItem(), context.getPlayer() );
                    canMerge = tebc.canMerge( blob );
                }
            }

            BlockItemUseContext replacementCheckContext = context instanceof BlockItemUseContext ? (BlockItemUseContext) context : new BlockItemUseContext(context);
            if(context.getPlayer().getEntityWorld().getBlockState(context.getPos()).getBlock() instanceof BlockChiseled)
            {
                replacementCheckContext = new DirectionalPlaceContext(context.getWorld(), pos, Direction.DOWN, ItemStack.EMPTY, Direction.UP);
            }

            if (!canMerge && !offgrid && !state.isReplaceable(replacementCheckContext))
            {
                pos = pos.offset( side );
            }
        }

        if ( ModUtil.isEmpty( context.getItem() ) )
        {
            return ActionResultType.FAIL;
        }
        else if ( !context.getPlayer().canPlayerEdit( pos, side, context.getItem() ) )
        {
            return ActionResultType.FAIL;
        }
        else if ( pos.getY() == 255 && DeprecationHelper.getStateFromItem( context.getItem() ).getMaterial().isSolid() )
        {
            return ActionResultType.FAIL;
        }
        else if (context instanceof BlockItemUseContext && canPlaceBlockHere( context.getWorld(), pos, side, context.getPlayer(), context.getHand(), context.getItem(), context.getHitVec().x, context.getHitVec().y, context.getHitVec().z, offgrid ) )
        {
            final int i = context.getItem().getDamage();
            final BlockState BlockState1 = getStateForPlacement((BlockItemUseContext) context);

            if ( placeBitBlock( context.getItem(), context.getPlayer(), context.getWorld(), pos, side, context.getHitVec().x, context.getHitVec().y, context.getHitVec().z, BlockState1, offgrid ) )
            {
                context.getWorld().playSound( pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, DeprecationHelper.getSoundType( this.getBlock() ).getPlaceSound(), SoundCategory.BLOCKS,
                  ( DeprecationHelper.getSoundType( this.block ).getVolume() + 1.0F ) / 2.0F,
                  DeprecationHelper.getSoundType( this.block ).getPitch() * 0.8F, false );

                if (!context.getPlayer().isCreative() && context.getItem().getItem() instanceof ItemBlockChiseled)
                    ModUtil.adjustStackSize( context.getItem(), -1 );

                return ActionResultType.SUCCESS;
            }

            return ActionResultType.FAIL;
        }
        else
        {
            return ActionResultType.FAIL;
        }
    }

    public enum PlacementAttemptResult {
	    FAILED(false),
        MERGEABLE(true),
        PLACEABLE(true);

	    private final boolean canPlace;

        PlacementAttemptResult(final boolean canPlace) {this.canPlace = canPlace;}

        public boolean isCanPlace()
        {
            return canPlace;
        }
    }
}
