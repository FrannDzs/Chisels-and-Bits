package mod.chiselsandbits.chiseledblock;

import mod.chiselsandbits.api.VoxelStats;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.chiseledblock.data.VoxelBlobStateReference;
import mod.chiselsandbits.chiseledblock.serialization.StringStates;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.StringNBT;

import java.io.IOException;

public class NBTBlobConverter
{

	public static final String NBT_SIDE_FLAGS = "s";
	public static final String NBT_NORMALCUBE_FLAG = "nc";
	public static final String NBT_LIGHTVALUE = "lv";

	public static final String NBT_PRIMARY_STATE = "b";
	public static final String NBT_LEGACY_VOXEL = "v";
	public static final String NBT_VERSIONED_VOXEL = "X";

	TileEntityBlockChiseled tile;

	private int sideState;
	private int lightValue;
	private boolean isNormalCube;
	private int primaryBlockState;
	private VoxelBlobStateReference voxelBlobRef;

	private int format = -1;
	private final boolean triggerUpdates;

	public int getSideState()
	{
		return sideState;
	}

	public int getLightValue()
	{
		return lightValue;
	}

	public boolean isNormalCube()
	{
		return isNormalCube;
	}

	public int getPrimaryBlockStateID()
	{
		return primaryBlockState;
	}

	public BlockState getPrimaryBlockState()
	{
		return ModUtil.getStateById( primaryBlockState );
	}

	public VoxelBlobStateReference getVoxelRef(
			final int version,
			final long weight ) throws Exception
	{
		final VoxelBlobStateReference voxelRef = getRef();

		if ( format == version )
		{
			return new VoxelBlobStateReference( voxelRef.getByteArray(), weight );
		}

		return new VoxelBlobStateReference( voxelRef.getVoxelBlobCatchable().blobToBytes( version ), weight );
	}

	public NBTBlobConverter()
	{
		triggerUpdates = false;
	}

	public NBTBlobConverter(
			final boolean triggerBlockUpdates,
			final TileEntityBlockChiseled tile )
	{
		this.tile = tile;

		triggerUpdates = triggerBlockUpdates;
		sideState = tile.sideState;
		lightValue = tile.getLightValue();
		isNormalCube = tile.isNormalCube;
		primaryBlockState = ModUtil.getStateId( tile.getBlockState( Blocks.COBBLESTONE ) );
		voxelBlobRef = tile.getBlobStateReference();
		format = voxelBlobRef == null ? -1 : voxelBlobRef.getFormat();
	}

	public void fillWith(
			final BlockState state )
	{
		voxelBlobRef = new VoxelBlobStateReference( ModUtil.getStateId( state ), 0 );
		updateFromBlob();
	}

	public void setBlob(
			final VoxelBlob vb )
	{
		voxelBlobRef = new VoxelBlobStateReference( vb, 0 );
		format = voxelBlobRef.getFormat();
		updateFromBlob();
	}

	public final void writeChiselData(
			final CompoundNBT compound,
			final boolean crossWorld )
	{
		final VoxelBlobStateReference voxelRef = getRef();

		if ( primaryBlockState == 0 )
		{
			return;
		}

		final int newFormat = crossWorld ? VoxelBlob.VERSION_CROSSWORLD : VoxelBlob.VERSION_COMPACT_PALLETED;
		final byte[] voxelBytes = newFormat == format ? voxelRef.getByteArray() : voxelRef.getVoxelBlob().blobToBytes( newFormat );

		compound.putInt( NBT_LIGHTVALUE, lightValue );

		if ( crossWorld )
		{
			compound.putString( NBT_PRIMARY_STATE, StringStates.getNameFromStateID( primaryBlockState ) );
		}
		else
		{
			compound.putInt( NBT_PRIMARY_STATE, primaryBlockState );
		}

		compound.putInt( NBT_SIDE_FLAGS, sideState );
		compound.putBoolean( NBT_NORMALCUBE_FLAG, isNormalCube );
		compound.putByteArray( NBT_VERSIONED_VOXEL, voxelBytes );
	}

	public final boolean readChisleData(
			final CompoundNBT compound,
			final int preferedFormat )
	{
		if ( compound == null )
		{
			voxelBlobRef = new VoxelBlobStateReference( 0, 0 );
			format = voxelBlobRef.getFormat();

			if ( tile != null )
			{
				return tile.updateBlob( this, triggerUpdates );
			}

			return false;
		}

		sideState = compound.getInt( NBT_SIDE_FLAGS );

		if ( compound.get( NBT_PRIMARY_STATE ) instanceof StringNBT)
		{
			primaryBlockState = StringStates.getStateIDFromName( compound.getString( NBT_PRIMARY_STATE ) );
		}
		{
			primaryBlockState = compound.getInt( NBT_PRIMARY_STATE );
		}

		lightValue = compound.getInt( NBT_LIGHTVALUE );
		isNormalCube = compound.getBoolean( NBT_NORMALCUBE_FLAG );
		byte[] v = compound.getByteArray( NBT_VERSIONED_VOXEL );

		if (v.length == 0)
		{
			final byte[] vx = compound.getByteArray( NBT_LEGACY_VOXEL );
			if (vx.length > 0)
			{
				final VoxelBlob bx = new VoxelBlob();

				try
				{
					bx.fromLegacyByteArray( vx );
				}
				catch ( final IOException e )
				{
				}

				v = bx.blobToBytes( VoxelBlob.VERSION_COMPACT_PALLETED );
				format = VoxelBlob.VERSION_COMPACT_PALLETED;
			}
		}

		if ( primaryBlockState == 0 )
		{
			// if load fails default to cobble stone...
			primaryBlockState = ModUtil.getStateId( Blocks.COBBLESTONE.getDefaultState() );
		}

		voxelBlobRef = new VoxelBlobStateReference( v, 0 );
		format = voxelBlobRef.getFormat();

		boolean formatChanged = false;

		if ( preferedFormat != format && preferedFormat != VoxelBlob.VERSION_ANY )
		{
			formatChanged = true;
			v = voxelBlobRef.getVoxelBlob().blobToBytes( preferedFormat );
			voxelBlobRef = new VoxelBlobStateReference( v, 0 );
			format = voxelBlobRef.getFormat();
		}

		if ( tile != null )
		{
			if ( formatChanged )
			{
				// this only works on already loaded tiles, so i'm not sure
				// there is much point in it.
				tile.markDirty();
			}

			return tile.updateBlob( this, triggerUpdates );
		}

		return true;
	}

	public void updateFromBlob()
	{
		final VoxelBlob vb = getRef().getVoxelBlob();

		final VoxelStats common = vb.getVoxelStats();
		final float floatLight = common.blockLight;

		isNormalCube = common.isNormalBlock;
		lightValue = Math.max( 0, Math.min( 15, (int) ( floatLight * 15 ) ) );
		sideState = vb.getSideFlags( 5, 11, 4 * 4 );
		primaryBlockState = common.mostCommonState;
	}

	public ItemStack getItemStack(
			final boolean crossWorld )
	{
		final Block blk = ModBlocks.convertGivenStateToChiseledBlock( getPrimaryBlockState() );

		if ( blk != null )
		{
			final ItemStack is = new ItemStack( blk );
			final CompoundNBT compound = ModUtil.getSubCompound( is, ModUtil.NBT_BLOCKENTITYTAG, true );
			writeChiselData( compound, crossWorld );

			if ( compound.size() > 0 )
			{
				return is;
			}
		}

		return null;
	}

	private VoxelBlobStateReference getRef()
	{
		if ( voxelBlobRef == null )
		{
			voxelBlobRef = new VoxelBlobStateReference( 0, 0 );
		}

		return voxelBlobRef;
	}

	public VoxelBlob getBlob()
	{
		return getRef().getVoxelBlob();
	}

}
