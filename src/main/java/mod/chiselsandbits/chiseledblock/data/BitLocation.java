package mod.chiselsandbits.chiseledblock.data;

import mod.chiselsandbits.api.IBitLocation;
import mod.chiselsandbits.helpers.BitOperation;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;

import javax.annotation.Nonnull;

public class BitLocation implements IBitLocation
{
	private static final double One32nd = 0.5 / VoxelBlob.dim;

	@Nonnull
	public BlockPos blockPos;
	public int bitX, bitY, bitZ;

	@Override
	public BlockPos getBlockPos()
	{
		return blockPos;
	}

	@Override
	public int getBitX()
	{
		return bitX;
	}

	@Override
	public int getBitY()
	{
		return bitY;
	}

	@Override
	public int getBitZ()
	{
		return bitZ;
	}

    @Override
    public IBitLocation offSet(final Direction direction)
    {
        final int newBitX = bitX + direction.getXOffset();
        final int newBitY = bitY + direction.getYOffset();
        final int newBitZ = bitZ + direction.getZOffset();

        return new BitLocation(
          blockPos,
          newBitX,
          newBitY,
          newBitZ
        );
    }

    public int snapToValid(
			final int x )
	{
		return Math.min( Math.max( 0, x ), 15 );
	}

	public BitLocation(
			final BlockRayTraceResult mop,
			final BitOperation type,
            final boolean isAccurateSneakPlace)
	{
		final Vector3d hitVec = mop.getHitVec();
		final Vector3d accuratePos = new Vector3d(
		  mop.getPos().getX(),
          mop.getPos().getY(),
          mop.getPos().getZ()
        );
		final Vector3d faceOffset = new Vector3d(
          mop.getFace().getOpposite().getXOffset() * One32nd,
          mop.getFace().getOpposite().getYOffset() * One32nd,
          mop.getFace().getOpposite().getZOffset() * One32nd
        );
        final Vector3d hitDelta = hitVec.subtract(accuratePos).add(faceOffset);
        final Vector3d inBlockPosAccurate = hitDelta.scale(16d);
        final Vector3i inBlockPos = new Vector3i(
          (int) inBlockPosAccurate.getX(),
          (int) inBlockPosAccurate.getY(),
          (int) inBlockPosAccurate.getZ()
        );
        final Vector3i normalizedInBlockPos = new Vector3i(
          inBlockPos.getX(),
          inBlockPos.getY(),
          inBlockPos.getZ()
        );
        final Vector3i normalizedInBlockPosWithOffset = type.usePlacementOffset() && !isAccurateSneakPlace ?
                                                normalizedInBlockPos.offset(mop.getFace(), 1) :
                                                normalizedInBlockPos;

        this.blockPos = mop.getPos();
        this.bitX = normalizedInBlockPosWithOffset.getX();
        this.bitY = normalizedInBlockPosWithOffset.getY();
        this.bitZ = normalizedInBlockPosWithOffset.getZ();

		normalize();
	}

	public BitLocation(
			final BlockPos pos,
			final int x,
			final int y,
			final int z )
	{
		blockPos = pos;
		bitX = x;
		bitY = y;
		bitZ = z;
		normalize();
	}

	public static BitLocation min(
			final BitLocation from,
			final BitLocation to )
	{
		final int bitX = Min( from.blockPos.getX(), to.blockPos.getX(), from.bitX, to.bitX );
		final int bitY = Min( from.blockPos.getY(), to.blockPos.getY(), from.bitY, to.bitY );
		final int bitZ = Min( from.blockPos.getZ(), to.blockPos.getZ(), from.bitZ, to.bitZ );

		return new BitLocation( new BlockPos(
				Math.min( from.blockPos.getX(), to.blockPos.getX() ),
				Math.min( from.blockPos.getY(), to.blockPos.getY() ),
				Math.min( from.blockPos.getZ(), to.blockPos.getZ() ) ),
				bitX, bitY, bitZ );
	}

	public static BitLocation max(
			final BitLocation from,
			final BitLocation to )
	{
		final int bitX = Max( from.blockPos.getX(), to.blockPos.getX(), from.bitX, to.bitX );
		final int bitY = Max( from.blockPos.getY(), to.blockPos.getY(), from.bitY, to.bitY );
		final int bitZ = Max( from.blockPos.getZ(), to.blockPos.getZ(), from.bitZ, to.bitZ );

		return new BitLocation( new BlockPos(
				Math.max( from.blockPos.getX(), to.blockPos.getX() ),
				Math.max( from.blockPos.getY(), to.blockPos.getY() ),
				Math.max( from.blockPos.getZ(), to.blockPos.getZ() ) ),
				bitX, bitY, bitZ );
	}

	private static int Min(
			final int x,
			final int x2,
			final int bitX2,
			final int bitX3 )
	{
		if ( x < x2 )
		{
			return bitX2;
		}
		if ( x2 == x )
		{
			return Math.min( bitX2, bitX3 );
		}

		return bitX3;
	}

	private static int Max(
			final int x,
			final int x2,
			final int bitX2,
			final int bitX3 )
	{
		if ( x > x2 )
		{
			return bitX2;
		}
		if ( x2 == x )
		{
			return Math.max( bitX2, bitX3 );
		}

		return bitX3;
	}

	private void normalize() {
	    final double xOffset = Math.floor(bitX / 16d);
	    final double yOffset = Math.floor(bitY / 16d);
	    final double zOffset = Math.floor(bitZ / 16d);

	    bitX = (bitX + 16) % 16;
	    bitY = (bitY + 16) % 16;
	    bitZ = (bitZ + 16) % 16;

	    this.blockPos = this.blockPos.add(
	      xOffset,
          yOffset,
          zOffset
        );
    }

}
