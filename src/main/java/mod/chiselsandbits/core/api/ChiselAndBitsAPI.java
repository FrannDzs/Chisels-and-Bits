package mod.chiselsandbits.core.api;

import com.mojang.blaze3d.matrix.MatrixStack;
import mod.chiselsandbits.api.APIExceptions.CannotBeChiseled;
import mod.chiselsandbits.api.APIExceptions.InvalidBitItem;
import mod.chiselsandbits.api.*;
import mod.chiselsandbits.api.ParameterType.DoubleParam;
import mod.chiselsandbits.api.ParameterType.FloatParam;
import mod.chiselsandbits.api.ParameterType.IntegerParam;
import mod.chiselsandbits.chiseledblock.BlockBitInfo;
import mod.chiselsandbits.chiseledblock.BlockChiseled;
import mod.chiselsandbits.chiseledblock.ItemBlockChiseled;
import mod.chiselsandbits.chiseledblock.TileEntityBlockChiseled;
import mod.chiselsandbits.chiseledblock.data.BitLocation;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.client.RenderHelper;
import mod.chiselsandbits.client.UndoTracker;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.helpers.BitInventoryFeeder;
import mod.chiselsandbits.helpers.BitOperation;
import mod.chiselsandbits.helpers.DeprecationHelper;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.items.*;
import mod.chiselsandbits.modes.ChiselMode;
import mod.chiselsandbits.modes.PositivePatternMode;
import mod.chiselsandbits.modes.TapeMeasureModes;
import mod.chiselsandbits.registry.ModBlocks;
import mod.chiselsandbits.registry.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.CapabilityItemHandler;

public class ChiselAndBitsAPI implements IChiselAndBitsAPI
{

	@Override
	public void addEquivilantMaterial(
			final Material newMaterial,
			final Material target )
	{
		ModBlocks.convertMaterialTo( newMaterial, target);
	}

	@Override
	public boolean canBeChiseled(
			final World world,
			final BlockPos pos )
	{
		if ( world == null || pos == null )
		{
			return false;
		}

		final BlockState state = world.getBlockState( pos );
		return state.getBlock() == Blocks.AIR || BlockBitInfo.canChisel( state ) || ModUtil.getChiseledTileEntity( world, pos, false ) != null;
	}

	@Override
	public boolean isBlockChiseled(
			final World world,
			final BlockPos pos )
	{
		if ( world == null || pos == null )
		{
			return false;
		}

		return ModUtil.getChiseledTileEntity( world, pos, false ) != null;
	}

	@Override
	public IBitAccess getBitAccess(
			final World world,
			final BlockPos pos ) throws CannotBeChiseled
	{
		if ( world == null || pos == null )
		{
			throw new CannotBeChiseled();
		}

		final BlockState state = world.getBlockState( pos );
		if ( BlockBitInfo.isSupported( state ) && !(state.getBlock() instanceof BlockChiseled))
		{
			final VoxelBlob blob = new VoxelBlob();
			blob.fill( ModUtil.getStateId( state ) );
			return new BitAccess( world, pos, blob, VoxelBlob.NULL_BLOB );
		}

		if ( world.isAirBlock( pos ) )
		{
			final VoxelBlob blob = new VoxelBlob();
			return new BitAccess( world, pos, blob, VoxelBlob.NULL_BLOB );
		}

		final TileEntityBlockChiseled te = ModUtil.getChiseledTileEntity( world, pos, true );
		if ( te != null )
		{
			final VoxelBlob mask = new VoxelBlob();
			return new BitAccess( world, pos, te.getBlob(), mask );
		}

		throw new CannotBeChiseled();
	}

	@Override
	public IBitBrush createBrush(
			final ItemStack stack ) throws InvalidBitItem
	{
		if ( ModUtil.isEmpty( stack ) )
		{
			return new BitBrush( 0 );
		}

		if ( getItemType( stack ) == ItemType.CHISLED_BIT )
		{
			final int stateID = ItemChiseledBit.getStackState( stack );
			final BlockState state = ModUtil.getStateById( stateID );

			if ( state != null && BlockBitInfo.canChisel( state ) )
			{
				return new BitBrush( stateID );
			}
		}

		throw new InvalidBitItem();
	}

	@Override
	public IBitLocation getBitPos(
			final double hitX,
			final double hitY,
			final double hitZ,
			final Direction side,
			final BlockPos pos,
			final boolean placement )
	{
		final BlockRayTraceResult mop = new BlockRayTraceResult(new Vector3d(hitX, hitY, hitZ), side, pos, false);
		return new BitLocation( mop, placement ? BitOperation.PLACE : BitOperation.CHISEL, false );
	}

	@Override
	public ItemType getItemType(
			final ItemStack stack )
	{
		if ( stack.getItem() instanceof ItemChiseledBit )
		{
			return ItemType.CHISLED_BIT;
		}

		if ( stack.getItem() instanceof ItemBitBag )
		{
			return ItemType.BIT_BAG;
		}

		if ( stack.getItem() instanceof ItemChisel )
		{
			return ItemType.CHISEL;
		}

		if ( stack.getItem() instanceof ItemBlockChiseled )
		{
			return ItemType.CHISLED_BLOCK;
		}

		if ( stack.getItem() instanceof ItemMirrorPrint )
		{
			return ItemType.MIRROR_DESIGN;
		}

		if ( stack.getItem() instanceof ItemPositivePrint )
		{
			return ItemType.POSITIVE_DESIGN;
		}

		if ( stack.getItem() instanceof ItemNegativePrint )
		{
			return ItemType.NEGATIVE_DESIGN;
		}

		if ( stack.getItem() instanceof ItemWrench )
		{
			return ItemType.WRENCH;
		}

		return null;
	}

	@Override
	public IBitAccess createBitItem(
			final ItemStack stack )
	{
		if ( ModUtil.isEmpty( stack ) )
		{
			return new BitAccess( null, null, new VoxelBlob(), VoxelBlob.NULL_BLOB );
		}

		final ItemType type = getItemType( stack );
		if ( type != null && type.isBitAccess )
		{
			final VoxelBlob blob = ModUtil.getBlobFromStack( stack, null );
			return new BitAccess( null, null, blob, VoxelBlob.NULL_BLOB );
		}

		if ( stack.getItem() instanceof BlockItem)
		{
			final BlockState state = DeprecationHelper.getStateFromItem( stack );

			if ( BlockBitInfo.canChisel( state ) )
			{
				final VoxelBlob blob = new VoxelBlob();
				blob.fill( ModUtil.getStateId( state ) );
				return new BitAccess( null, null, blob, VoxelBlob.NULL_BLOB );
			}
		}

		return null;
	}

	@Override
	public IBitBrush createBrushFromState(
			final BlockState state ) throws InvalidBitItem
	{
		if ( state == null || state.getBlock() == Blocks.AIR )
		{
			return new BitBrush( 0 );
		}

		if ( !BlockBitInfo.canChisel( state ) )
		{
			throw new InvalidBitItem();
		}

		return new BitBrush( ModUtil.getStateId( state ) );
	}

	@Override
	public ItemStack getBitItem(
			final BlockState state ) throws InvalidBitItem
	{
		if ( !BlockBitInfo.canChisel( state ) )
		{
			throw new InvalidBitItem();
		}

		return ItemChiseledBit.createStack( ModUtil.getStateId( state ), 1, true );
	}

	@Override
	public void giveBitToPlayer(
			final PlayerEntity player,
			final ItemStack stack,
			Vector3d spawnPos )
	{
		if ( ModUtil.isEmpty( stack ) )
		{
			return;
		}

		if ( spawnPos == null )
		{
			spawnPos = new Vector3d( player.getPosX(), player.getPosY(), player.getPosZ() );
		}

		final ItemEntity ei = new ItemEntity( player.getEntityWorld(), spawnPos.x, spawnPos.y, spawnPos.z, stack );

		if ( stack.getItem() == ModItems.ITEM_BLOCK_BIT.get() )
		{
			if ( player.getEntityWorld().isRemote )
			{
				return;
			}

			BitInventoryFeeder feeder = new BitInventoryFeeder( player, player.getEntityWorld() );
			feeder.addItem(ei);
			return;
		}
		else if ( !player.inventory.addItemStackToInventory( stack ) )
		{
			ei.setItem( stack );
			player.getEntityWorld().addEntity( ei );
		}
	}

	@Override
	public IBitBag getBitbag(
			final ItemStack stack )
	{
		if ( !ModUtil.isEmpty( stack ) )
		{
			final Object o = stack.getCapability( CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, Direction.UP );
			if ( o instanceof IBitBag )
			{
				return (IBitBag) o;
			}
		}

		return null;
	}

	@Override
	public void beginUndoGroup(
			final PlayerEntity player )
	{
		UndoTracker.getInstance().beginGroup( player );
	}

	@Override
	public void endUndoGroup(
			final PlayerEntity player )
	{
		UndoTracker.getInstance().endGroup( player );
	}

	@Override
	@OnlyIn( Dist.CLIENT )
	public KeyBinding getKeyBinding(
			ModKeyBinding modKeyBinding )
	{
		switch ( modKeyBinding )
		{
			case SINGLE:
				return (KeyBinding) ChiselMode.SINGLE.binding;
			case SNAP2:
				return (KeyBinding) ChiselMode.SNAP2.binding;
			case SNAP4:
				return (KeyBinding) ChiselMode.SNAP4.binding;
			case SNAP8:
				return (KeyBinding) ChiselMode.SNAP8.binding;
			case LINE:
				return (KeyBinding) ChiselMode.LINE.binding;
			case PLANE:
				return (KeyBinding) ChiselMode.PLANE.binding;
			case CONNECTED_PLANE:
				return (KeyBinding) ChiselMode.CONNECTED_PLANE.binding;
			case CUBE_SMALL:
				return (KeyBinding) ChiselMode.CUBE_SMALL.binding;
			case CUBE_MEDIUM:
				return (KeyBinding) ChiselMode.CUBE_MEDIUM.binding;
			case CUBE_LARGE:
				return (KeyBinding) ChiselMode.CUBE_LARGE.binding;
			case SAME_MATERIAL:
				return (KeyBinding) ChiselMode.SAME_MATERIAL.binding;
			case DRAWN_REGION:
				return (KeyBinding) ChiselMode.DRAWN_REGION.binding;
			case CONNECTED_MATERIAL:
				return (KeyBinding) ChiselMode.CONNECTED_MATERIAL.binding;
			case REPLACE:
				return (KeyBinding) PositivePatternMode.REPLACE.binding;
			case ADDITIVE:
				return (KeyBinding) PositivePatternMode.ADDITIVE.binding;
			case PLACEMENT:
				return (KeyBinding) PositivePatternMode.PLACEMENT.binding;
			case IMPOSE:
				return (KeyBinding) PositivePatternMode.IMPOSE.binding;
			case BIT:
				return (KeyBinding) TapeMeasureModes.BIT.binding;
			case BLOCK:
				return (KeyBinding) TapeMeasureModes.BLOCK.binding;
			case DISTANCE:
				return (KeyBinding) TapeMeasureModes.DISTANCE.binding;
			default:
				return ClientSide.instance.getKeyBinding( modKeyBinding );
		}
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	@Override
	public Object getParameter(
			ParameterType which )
	{
		switch (which.getType())
		{
			case BOOLEAN:
				switch ( (ParameterType.BooleanParam) which )
				{
					case ENABLE_DAMAGE_TOOLS:
						return ChiselsAndBits.getConfig().getServer().damageTools.get();
					case ENABLE_BIT_LIGHT_SOURCE:
						return ChiselsAndBits.getConfig().getServer().enableBitLightSource.get();
				}
				break;

			case DOUBLE:
				switch ( (DoubleParam) which )
				{
					case BIT_MAX_DRAWN_REGION_SIZE:
						return ChiselsAndBits.getConfig().getClient().maxDrawnRegionSize.get();
				}
				break;

			case FLOAT:
				switch ( (FloatParam) which )
				{
					case BLOCK_FULL_LIGHT_PERCENTAGE:
						return ChiselsAndBits.getConfig().getServer().bitLightPercentage.get();
				}
				break;

			case INTEGER:
				switch ( (IntegerParam) which )
				{
					case BIT_BAG_MAX_STACK_SIZE:
						return ChiselsAndBits.getConfig().getServer().bagStackSize.get();
				}
				break;

		}

		return null;
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void renderModel(
	  final MatrixStack stack,
			final IBakedModel model,
			final World world,
			final BlockPos pos,
			final int alpha,
      final int combinedLight,
      final int combinedOverlay)
	{
		RenderHelper.renderModel( stack, model, world, pos, alpha << 24, combinedLight, combinedOverlay );
	}

	@Override
    @OnlyIn(Dist.CLIENT)
	public void renderGhostModel(
	  final MatrixStack stack,
			final IBakedModel model,
			final World world,
			final BlockPos pos,
			final ItemBlockChiseled.PlacementAttemptResult placementAttemptResult,
      final int combinedLight,
      final int combinedOverlay)
	{
		RenderHelper.renderGhostModel(stack, model, world, pos, placementAttemptResult,combinedLight,combinedOverlay );
	}

}
