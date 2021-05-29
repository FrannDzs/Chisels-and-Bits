package mod.chiselsandbits.chiseledblock;

import javax.annotation.Nonnull;

import mod.chiselsandbits.api.BoxType;
import mod.chiselsandbits.api.IMultiStateBlock;
import mod.chiselsandbits.chiseledblock.data.BitLocation;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.chiseledblock.data.VoxelBlobStateReference;
import mod.chiselsandbits.chiseledblock.data.VoxelShapeCache;
import mod.chiselsandbits.client.CreativeClipboardTab;
import mod.chiselsandbits.client.UndoTracker;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.core.Log;
import mod.chiselsandbits.helpers.BitOperation;
import mod.chiselsandbits.helpers.ChiselToolType;
import mod.chiselsandbits.helpers.ExceptionNoTileEntity;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.items.ItemChiseledBit;
import mod.chiselsandbits.registry.ModBlocks;
import mod.chiselsandbits.utils.SingleBlockBlockReader;
import mod.chiselsandbits.utils.SingleBlockWorldReader;
import net.minecraft.block.*;
import net.minecraft.block.material.PushReaction;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.DirectionalPlaceContext;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ToolType;
import org.jetbrains.annotations.Nullable;

public class BlockChiseled extends Block implements ITileEntityProvider, IMultiStateBlock
{

    public static final BlockPos ZERO = BlockPos.ZERO;

    private static ThreadLocal<BlockState> actingAs = new ThreadLocal<BlockState>();

	public static final BooleanProperty FULL_BLOCK = BooleanProperty.create( "full_block" );

    public final String name;

    public BlockChiseled(final String name, final AbstractBlock.Properties properties) {
        super(properties);
        this.name = name;
        this.setDefaultState(this.stateContainer.getBaseState().with(FULL_BLOCK, false));
    }

    @Override
    public boolean removedByPlayer(final BlockState state, final World world, final BlockPos pos, final PlayerEntity player, final boolean willHarvest, final FluidState fluid)
    {
        if ( !willHarvest && ChiselsAndBits.getConfig().getClient().addBrokenBlocksToCreativeClipboard.get() )
        {

            try
            {
                final TileEntityBlockChiseled tebc = getTileEntity( world, pos );
                CreativeClipboardTab.addItem( tebc.getItemStack( player ) );

                UndoTracker.getInstance().add( world, pos, tebc.getBlobStateReference(), new VoxelBlobStateReference( 0, 0 ) );
            }
            catch ( final ExceptionNoTileEntity e )
            {
                Log.noTileError( e );
            }
        }

        return super.removedByPlayer( state, world, pos, player, willHarvest, fluid );
    }

    @Override
    public boolean shouldCheckWeakPower(final BlockState state, final IWorldReader world, final BlockPos pos, final Direction side)
    {
        return isFullCube( state );
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public float getAmbientOcclusionLightValue(final BlockState state, final IBlockReader worldIn, final BlockPos pos)
    {
        return isFullCube( state ) ? 0.2F : 1F;
    }

    @Override
    public boolean isReplaceable(final BlockState state, final BlockItemUseContext useContext)
    {
        try
        {
            BlockPos target = useContext.getPos();
            if (!(useContext instanceof DirectionalPlaceContext) && !useContext.replacingClickedOnBlock()) {
                target = target.offset(useContext.getFace().getOpposite());
            }

            return getTileEntity( useContext.getWorld(), target ).getBlob().filled() == 0;
        }
        catch ( final ExceptionNoTileEntity e )
        {
            Log.noTileError( e );
            return super.isReplaceable( state, useContext );
        }
    }

    @Override
    public float getSlipperiness(final BlockState state, final IWorldReader world, final BlockPos pos, @Nullable final Entity entity)
    {
        try
        {
            BlockState internalState = getTileEntity( world, pos ).getBlockState( Blocks.STONE );

            if ( internalState != null )
            {
                return internalState.getBlock().getSlipperiness( internalState, new SingleBlockWorldReader(internalState, internalState.getBlock(), world), BlockPos.ZERO, entity );
            }
        }
        catch ( ExceptionNoTileEntity e )
        {
            Log.noTileError( e );
        }

        return super.getSlipperiness( state, world, pos, entity );
    }

	static ExceptionNoTileEntity noTileEntity = new ExceptionNoTileEntity();

	public static @Nonnull TileEntityBlockChiseled getTileEntity(
			final TileEntity te ) throws ExceptionNoTileEntity
	{
		if ( te == null )
		{
			throw noTileEntity;
		}

		try
		{
			return (TileEntityBlockChiseled) te;
		}
		catch ( final ClassCastException e )
		{
			throw noTileEntity;
		}
	}

	public static @Nonnull TileEntityBlockChiseled getTileEntity(
			final @Nonnull IBlockReader world,
			final @Nonnull BlockPos pos ) throws ExceptionNoTileEntity
	{
		final TileEntity te = ModUtil.getTileEntitySafely( world, pos );
        return getTileEntity(te);
	}

    @Override
    public int getOpacity(final BlockState state, final IBlockReader worldIn, final BlockPos pos)
    {
        return isFullCube( state ) ? 0 : 1;
    }

	public static boolean isFullCube(
			final BlockState state )
	{
		return state.get(FULL_BLOCK);
	}

	@Override
	public void harvestBlock(
			final World worldIn,
			final PlayerEntity player,
			final BlockPos pos,
			final BlockState state,
			final TileEntity te,
			final ItemStack stack )
	{
		try
		{
			spawnAsEntity( worldIn, pos, getTileEntity( te ).getItemStack( player ) );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			super.harvestBlock( worldIn, player, pos, state, (TileEntity) null, stack );
		}
	}

    @Override
    public void onBlockPlacedBy(final World worldIn, final BlockPos pos, final BlockState state, @Nullable final LivingEntity placer, final ItemStack stack)
    {
        try
        {
            if ( stack == null || placer == null || !stack.hasTag() )
            {
                return;
            }

            final TileEntityBlockChiseled bc = getTileEntity( worldIn, pos );
            if (worldIn.isRemote)
            {
                bc.getState();
            }
            int rotations = ModUtil.getRotations( placer, ModUtil.getSide( stack ) );

            VoxelBlob blob = bc.getBlob();
            while ( rotations-- > 0 )
            {
                blob = blob.spin( Axis.Y );
            }
            bc.setBlob( blob );
        }
        catch ( final ExceptionNoTileEntity e )
        {
            Log.noTileError( e );
        }
    }



    @Override
    public ItemStack getPickBlock(final BlockState state, final RayTraceResult target, final IBlockReader world, final BlockPos pos, final PlayerEntity player)
    {
        if (!(target instanceof BlockRayTraceResult))
            return ItemStack.EMPTY;

        try
        {
            return getPickBlock((BlockRayTraceResult) target, pos, getTileEntity( world, pos ) );
        }
        catch ( final ExceptionNoTileEntity e )
        {
            Log.noTileError( e );
            return ModUtil.getEmptyStack();
        }
    }

	/**
	 * Client side method.
	 */
	private ChiselToolType getClientHeldTool()
	{
		return ClientSide.instance.getHeldToolType( Hand.MAIN_HAND );
	}

	public ItemStack getPickBlock(
			final BlockRayTraceResult target,
			final BlockPos pos,
			final TileEntityBlockChiseled te )
	{
		if ( te.getWorld().isRemote )
		{
			if ( getClientHeldTool() != null )
			{
				final VoxelBlob vb = te.getBlob();

				final BitLocation bitLoc = new BitLocation( target, BitOperation.CHISEL, false );

				final int itemBlock = vb.get( bitLoc.bitX, bitLoc.bitY, bitLoc.bitZ );
				if ( itemBlock == 0 )
				{
					return ModUtil.getEmptyStack();
				}

				return ItemChiseledBit.createStack( itemBlock, 1, false );
			}

			return te.getItemStack( ClientSide.instance.getPlayer() );
		}

		return te.getItemStack( null );
	}

    @Override
    protected void fillStateContainer(final StateContainer.Builder<Block, BlockState> builder)
    {
        super.fillStateContainer(builder);
        builder.add(FULL_BLOCK);
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(final BlockState state, final IBlockReader world)
    {
        return new TileEntityBlockChiseled();
    }

    @Override
    public boolean addDestroyEffects(final BlockState state, final World world, final BlockPos pos, final ParticleManager manager)
    {
        try
        {
            final BlockState internalState = getTileEntity( world, pos ).getBlockState( this );
            return ClientSide.instance.addBlockDestroyEffects( world, pos, internalState, manager );
        }
        catch ( final ExceptionNoTileEntity e )
        {
            Log.noTileError( e );
        }

        return true;
    }

	@Override
	@OnlyIn( Dist.CLIENT )
	public boolean addHitEffects(
			final BlockState state,
			final World world,
			final RayTraceResult target,
			final ParticleManager effectRenderer )
	{
	    if (!(target instanceof BlockRayTraceResult))
	        return false;

		try
		{
		    final BlockRayTraceResult rayTraceResult = (BlockRayTraceResult) target;
			final BlockPos pos = rayTraceResult.getPos();
			final BlockState bs = getTileEntity( world, pos ).getBlockState( this );
			return ClientSide.instance.addHitEffects( world, rayTraceResult, bs, effectRenderer );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return true;
		}
	}

    @Override
    public VoxelShape getShape(final BlockState state, final IBlockReader reader, final BlockPos pos, final ISelectionContext context)
    {
        try
        {
            final VoxelBlob blob = getTileEntity(reader, pos).getBlob();
            if (blob == null)
                return VoxelShapes.empty();

            return VoxelShapeCache.getInstance().get(blob, BoxType.OCCLUSION);
        }
        catch (ExceptionNoTileEntity exceptionNoTileEntity)
        {
            return VoxelShapes.empty();
        }
    }

    @Deprecated
    public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        try
        {
            final VoxelBlob blob = getTileEntity(worldIn, pos).getBlob();
            if (blob == null)
                return VoxelShapes.empty();

            return VoxelShapeCache.getInstance().get(blob, BoxType.COLLISION);
        }
        catch (ExceptionNoTileEntity exceptionNoTileEntity)
        {
            return VoxelShapes.empty();
        }
    }

    @Deprecated
    public VoxelShape getRayTraceShape(BlockState state, IBlockReader reader, BlockPos pos, ISelectionContext context) {
        try
        {
            final VoxelBlob blob = getTileEntity(reader, pos).getBlob();
            if (blob == null)
                return VoxelShapes.empty();

            return VoxelShapeCache.getInstance().get(blob, BoxType.OCCLUSION);
        }
        catch (ExceptionNoTileEntity exceptionNoTileEntity)
        {
            return VoxelShapes.empty();
        }
    }

    @Override
    public boolean isVariableOpacity()
    {
        return true;
    }

    public boolean propagatesSkylightDown(BlockState state, IBlockReader reader, BlockPos pos) {
        return true;
    }

	public static boolean replaceWithChiseled(
			final World world,
			final BlockPos pos,
			final BlockState originalState,
			final boolean triggerUpdate )
	{
		return replaceWithChiseled( world, pos, originalState, 0, triggerUpdate ).success;
	}

    @Override
    public BlockState rotate(final BlockState state, final IWorld world, final BlockPos pos, final Rotation direction)
    {
        try
        {
            getTileEntity( world, pos ).rotateBlock();
            return state;
        }
        catch ( final ExceptionNoTileEntity e )
        {
            Log.noTileError( e );
            return state;
        }
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(final IBlockReader worldIn)
    {
        return new TileEntityBlockChiseled();
    }

    public static class ReplaceWithChiseledValue
    {
		public boolean success = false;
		public TileEntityBlockChiseled te = null;
	};

    public static ReplaceWithChiseledValue replaceWithChiseled(
			final @Nonnull World world,
			final @Nonnull BlockPos pos,
			final BlockState originalState,
			final int fragmentBlockStateID,
			final boolean triggerUpdate )
	{
		BlockState actingState = originalState;
		Block target = originalState.getBlock();
		final boolean isAir = world.isAirBlock( pos ) || actingState.isReplaceable( new DirectionalPlaceContext(world, pos, Direction.DOWN, ItemStack.EMPTY, Direction.UP) );
		ReplaceWithChiseledValue rv = new ReplaceWithChiseledValue();
		
		if ( BlockBitInfo.canChisel( actingState ) || isAir )
		{
			BlockChiseled blk = ModBlocks.convertGivenStateToChiseledBlock( originalState );

			int BlockID = ModUtil.getStateId( actingState );

			if ( isAir )
			{
				actingState = ModUtil.getStateById( fragmentBlockStateID );
				target = actingState.getBlock();
				BlockID = ModUtil.getStateId( actingState );
				blk = ModBlocks.convertGivenStateToChiseledBlock( actingState );
				// its still air tho..
				actingState = Blocks.AIR.getDefaultState();
			}

			if ( BlockID == 0 )
			{
				return rv;
			}

			if ( blk != null && blk != target )
			{
				TileEntityBlockChiseled.setLightFromBlock( actingState );
				world.setBlockState( pos, blk.getDefaultState(), triggerUpdate ? 3 : 0 );
				TileEntityBlockChiseled.setLightFromBlock( null );
				final TileEntity te = world.getTileEntity( pos );

				TileEntityBlockChiseled tec;
				if ( !( te instanceof TileEntityBlockChiseled ) )
				{
					tec = (TileEntityBlockChiseled) blk.createTileEntity( blk.getDefaultState(), world );
					world.setTileEntity( pos, tec );
				}
				else
				{
					tec = (TileEntityBlockChiseled) te;
				}

				if ( tec != null )
				{
					tec.fillWith( actingState );
					tec.setPrimaryBlockStateId(BlockID);
					tec.setState( tec.getState(), tec.getBlobStateReference() );
				}

				rv.success = true;
				rv.te = tec;
				
				return rv;
			}
		}

		return rv;
	}

	public BlockState getCommonState(
            final TileEntityBlockChiseled te)
	{
		final VoxelBlobStateReference data = te.getBlobStateReference();

		if ( data != null )
		{
			final VoxelBlob vb = data.getVoxelBlob();
			if ( vb != null )
			{
				return ModUtil.getStateById( vb.getVoxelStats().mostCommonState );
			}
		}

		return null;
	}

	@Override
	public int getLightValue(
			final BlockState state,
			final IBlockReader world,
			final BlockPos pos )
	{
		// is this the right block?
		final BlockState realState = world.getBlockState( pos );
		final Block realBlock = realState.getBlock();
		if ( realBlock != this )
		{
			return realBlock.getLightValue( realState, world, pos );
		}

		// enabled?
		if ( ChiselsAndBits.getConfig().getServer().enableBitLightSource.get() )
		{
			try
			{
				return getTileEntity( world, pos ).getLightValue();
			}
			catch ( final ExceptionNoTileEntity e )
			{
				Log.noTileError( e );
			}
		}

		return 0;
	}

	public static void setActingAs(
			final BlockState state )
	{
		actingAs.set( state );
	}

    @Override
    public boolean canHarvestBlock(final BlockState state, final IBlockReader world, final BlockPos pos, final PlayerEntity player)
    {
        if ( ChiselsAndBits.getConfig().getServer().enableToolHarvestLevels.get() )
        {
            BlockState activeState = actingAs.get();

            if ( activeState == null )
            {
                activeState = getPrimaryState( world, pos );
            }

            return activeState.canHarvestBlock( new SingleBlockBlockReader( activeState ), pos, player );
        }

        return true;
    }

    @Override
    public float getPlayerRelativeBlockHardness(final BlockState state, final PlayerEntity player, final IBlockReader worldIn, final BlockPos pos)
    {
        if ( ChiselsAndBits.getConfig().getServer().enableToolHarvestLevels.get() )
        {
            BlockState actingState = actingAs.get();

            if ( actingState == null )
            {
                actingState = getPrimaryState( worldIn, pos );
            }

            final float hardness = state.getBlockHardness(worldIn, pos);
            if ( hardness < 0.0F )
            {
                return 0.0F;
            }

            // since we can't call getDigSpeed on the acting state, we can just
            // do some math to try and roughly estimate it.
            float denom = player.inventory.getDestroySpeed( actingState );
            float numer = player.inventory.getDestroySpeed( state );

            if ( !state.canHarvestBlock( new SingleBlockBlockReader( state ), ZERO, player ) )
            {
                return player.getDigSpeed( actingState, pos ) / hardness / 100F * ( numer / denom );
            }
            else
            {
                return player.getDigSpeed( actingState, pos ) / hardness / 30F * ( numer / denom );
            }
        }

        return super.getPlayerRelativeBlockHardness(state, player, worldIn, pos);
    }

    @Override
    public boolean isToolEffective(final BlockState state, final ToolType tool)
    {
        return Blocks.STONE.isToolEffective( Blocks.STONE.getDefaultState(), tool );
    }

	public ResourceLocation getModel()
	{
		return new ResourceLocation( ChiselsAndBits.MODID, name );
	}

    @Override
    public void fillItemGroup(final ItemGroup group, final NonNullList<ItemStack> items)
    {
        //No items
    }

	@Override
	public BlockState getPrimaryState(
			final IBlockReader world,
			final BlockPos pos )
	{
		try
		{
			return getTileEntity( world, pos ).getBlockState( Blocks.STONE );
		}
		catch ( final ExceptionNoTileEntity e )
		{
			Log.noTileError( e );
			return Blocks.STONE.getDefaultState();
		}
	}

	public boolean basicHarvestBlockTest(
			World world,
			BlockPos pos,
			PlayerEntity player )
	{
		return super.canHarvestBlock(world.getBlockState(pos), world, pos, player );
	}

    @Override
    public PushReaction getPushReaction(final BlockState state)
    {
        return PushReaction.BLOCK;
    }
}
