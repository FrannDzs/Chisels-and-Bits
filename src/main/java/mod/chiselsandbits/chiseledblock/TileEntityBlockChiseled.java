package mod.chiselsandbits.chiseledblock;

import mod.chiselsandbits.api.*;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.chiseledblock.data.VoxelBlobStateReference;
import mod.chiselsandbits.client.UndoTracker;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.Log;
import mod.chiselsandbits.core.api.BitAccess;
import mod.chiselsandbits.helpers.DeprecationHelper;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.interfaces.IChiseledTileContainer;
import mod.chiselsandbits.registry.ModBlocks;
import mod.chiselsandbits.registry.ModTileEntityTypes;
import mod.chiselsandbits.render.chiseledblock.ChiseledBlockSmartModel;
import mod.chiselsandbits.utils.SingleBlockBlockReader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.data.ModelDataMap;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.thread.EffectiveSide;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class TileEntityBlockChiseled extends TileEntity implements IChiseledTileContainer, IChiseledBlockTileEntity
{
    public static final ModelProperty<VoxelBlobStateReference>    MP_VBSR = new ModelProperty<>();
    public static final ModelProperty<Integer> MP_PBSI = new ModelProperty<>();

    public TileEntityBlockChiseled()
    {
        this(ModTileEntityTypes.CHISELED.get());
    }

    public TileEntityBlockChiseled(final TileEntityType<?> tileEntityTypeIn)
    {
        super(tileEntityTypeIn == null ? ModTileEntityTypes.CHISELED.get() : tileEntityTypeIn);
    }

    public IChiseledTileContainer occlusionState;

    boolean isNormalCube = false;
    int     sideState    = 0;
    int     lightLevel   = -1;

    private BlockState              state;
    private VoxelBlobStateReference blobStateReference;
    private int                     primaryBlockStateId;

    private static final ThreadLocal<Integer> LOCAL_LIGHT_LEVEL = new ThreadLocal<>();

    public VoxelBlobStateReference getBlobStateReference()
    {
        return blobStateReference;
    }

    private void setBlobStateReference(final VoxelBlobStateReference blobStateReference)
    {
        if (this.blobStateReference == null || !this.blobStateReference.equals(blobStateReference))
            this.blobStateReference = blobStateReference;
    }

    public int getPrimaryBlockStateId()
    {
        return primaryBlockStateId;
    }

    public void setPrimaryBlockStateId(final int primaryBlockStateId)
    {
        this.primaryBlockStateId = primaryBlockStateId;

        setLightFromBlock(
          ModUtil.getStateById(primaryBlockStateId)
        );
    }

    public IChiseledTileContainer getTileContainer()
    {
        if (occlusionState != null)
        {
            return occlusionState;
        }

        return this;
    }

    @Override
    public boolean isBlobOccluded(
      final VoxelBlob blob)
    {
        return false;
    }

    @Override
    public void saveData()
    {
        super.markDirty();
    }

    @Override
    public void sendUpdate()
    {
        ModUtil.sendUpdate(Objects.requireNonNull(getWorld()), pos);
    }

    @Override
    public void setWorldAndPos(@NotNull final World world, @NotNull final BlockPos pos)
    {
        super.setWorldAndPos(world, pos);
    }

    @Nonnull
    protected BlockState getState()
    {
        if (state == null)
        {
            state = ModBlocks.getChiseledDefaultState();
        }

        return Objects.requireNonNull(state);
    }

    public BlockState getBlockState(
      final Block alternative)
    {
        final int stateID = getPrimaryBlockStateId();

        final BlockState state = ModUtil.getStateById(stateID);
        if (state != null)
        {
            return state;
        }

        return alternative.getDefaultState();
    }

    public void setState(
      final BlockState blockState,
      final VoxelBlobStateReference newRef)
    {
        final VoxelBlobStateReference originalRef = getBlobStateReference();

        this.state = blockState;

        if (newRef != null && !newRef.equals(originalRef))
        {
            final EventBlockBitPostModification bmm = new EventBlockBitPostModification(Objects.requireNonNull(getWorld()), getPos());
            MinecraftForge.EVENT_BUS.post(bmm);
            setBlobStateReference(newRef);
        }
    }

    @Override
    public SUpdateTileEntityPacket getUpdatePacket()
    {
        final CompoundNBT compound = new CompoundNBT();
        writeChiselData(compound);

        if (compound.size() == 0)
        {
            return null;
        }

        return new SUpdateTileEntityPacket(pos, 255, compound);
    }

    @NotNull
    @Override
    public CompoundNBT getUpdateTag()
    {
        final CompoundNBT compound = new CompoundNBT();

        compound.putInt("x", pos.getX());
        compound.putInt("y", pos.getY());
        compound.putInt("z", pos.getZ());

        writeChiselData(compound);

        return compound;
    }

    @Override
    public void handleUpdateTag(final BlockState state, final CompoundNBT tag)
    {
        readChiselData(tag);
    }

    @Override
    public void onDataPacket(
      final NetworkManager net,
      final SUpdateTileEntityPacket pkt)
    {
        final VoxelBlobStateReference current = getBlobStateReference();
        final int oldLight = lightLevel;
        final boolean changed = readChiselData(pkt.getNbtCompound());

        if (world != null && changed)
        {
            world.markBlockRangeForRenderUpdate(pos, world.getBlockState(pos), Blocks.AIR.getDefaultState());

            // fixes lighting on placement when tile packet arrives.
            if (oldLight != lightLevel)
            {
                world.getLightManager().checkBlock(pos);
            }
        }

        if (world.isRemote()) {
            UndoTracker.getInstance().onNetworkUpdate(current, getBlobStateReference());
        }
    }

    public boolean readChiselData(
      final CompoundNBT tag)
    {
        final NBTBlobConverter converter = new NBTBlobConverter(false, this);
        return converter.readChisleData(tag, VoxelBlob.VERSION_COMPACT_PALLETED);
    }

    public void writeChiselData(
      final CompoundNBT tag)
    {
        new NBTBlobConverter(false, this).writeChiselData(tag, false);
    }

    @NotNull
    @Override
    public CompoundNBT write(@NotNull final CompoundNBT compound)
    {
        final CompoundNBT nbt = super.write(compound);
        writeChiselData(nbt);
        return nbt;
    }

    @Override
    public void read(@NotNull final BlockState state, @NotNull final CompoundNBT nbt)
    {
        super.read(state, nbt);
        readChiselData(nbt);
    }

    @NotNull
    @Override
    public CompoundNBT writeTileEntityToTag(
      @NotNull final CompoundNBT tag,
      final boolean crossWorld)
    {
        final CompoundNBT superNbt = super.write(tag);
        new NBTBlobConverter(false, this).writeChiselData(superNbt, crossWorld);
        superNbt.putBoolean("cw", crossWorld);
        return superNbt;
    }

    @Override
    public void mirror(
      @NotNull final Mirror mirrorIn)
    {
        switch (mirrorIn)
        {
            case FRONT_BACK:
                setBlob(getBlob().mirror(Axis.X), true);
                break;
            case LEFT_RIGHT:
                setBlob(getBlob().mirror(Axis.Z), true);
                break;
            case NONE:
            default:
                break;
        }
    }

    @Override
    public void rotate(
      @NotNull final Rotation rotationIn)
    {
        VoxelBlob blob = ModUtil.rotate(getBlob(), Axis.Y, rotationIn);
        if (blob != null)
        {
            setBlob(blob, true);
        }
    }

    public void fillWith(
      final BlockState blockType)
    {
        final int ref = ModUtil.getStateId(blockType);

        sideState = 0xff;
        lightLevel = DeprecationHelper.getLightValue(blockType);
        isNormalCube = ModUtil.isNormalCube(blockType);

        BlockState defaultState = getState();

        // required for placing bits
        if (ref != 0)
        {
            setPrimaryBlockStateId(ref);
        }

        setState(defaultState, new VoxelBlobStateReference(ModUtil.getStateId(blockType), getPositionRandom(pos)));

        getTileContainer().saveData();
    }

    public static long getPositionRandom(
      final BlockPos pos)
    {
        if (pos != null && EffectiveSide.get().isClient())
        {
            return MathHelper.getPositionRandom(pos);
        }

        return 0;
    }

    public VoxelBlob getBlob()
    {
        VoxelBlob vb;
        final VoxelBlobStateReference vbs = getBlobStateReference();

        if (vbs != null)
        {
            vb = vbs.getVoxelBlob();
        }
        else
        {
            vb = new VoxelBlob();
        }

        return vb;
    }

    public void setBlob(
      final VoxelBlob vb)
    {
        setBlob(vb, true);
    }

    public boolean updateBlob(
      final NBTBlobConverter converter,
      final boolean triggerUpdates)
    {
        final int oldLV = getLightValue();
        final boolean oldNC = isNormalCube();
        final int oldSides = sideState;

        final VoxelBlobStateReference originalRef = getBlobStateReference();

        VoxelBlobStateReference voxelRef;

        sideState = converter.getSideState();
        final int b = converter.getPrimaryBlockStateID();
        lightLevel = converter.getLightValue();
        isNormalCube = converter.isNormalCube();

        try
        {
            voxelRef = converter.getVoxelRef(VoxelBlob.VERSION_COMPACT_PALLETED, getPositionRandom(pos));
        }
        catch (final Exception e)
        {
            Log.logError("Unable to read blob at " + getPos(), e);
            voxelRef = new VoxelBlobStateReference(0, getPositionRandom(pos));
        }

        setPrimaryBlockStateId(b);
        setBlobStateReference(voxelRef);
        setState(getState(), voxelRef);

        if (getWorld() != null && triggerUpdates)
        {
            if (oldLV != getLightValue() || oldNC != isNormalCube())
            {
                getWorld().getLightManager().checkBlock(pos);

                // update block state to reflect lighting characteristics
                final BlockState state = getWorld().getBlockState(pos);
                if (state.isNormalCube(new SingleBlockBlockReader(state), BlockPos.ZERO) != isNormalCube && state.getBlock() instanceof BlockChiseled)
                {
                    getWorld().setBlockState(pos, state.with(BlockChiseled.FULL_BLOCK, isNormalCube));
                }
            }

            if (oldSides != sideState)
            {
                Objects.requireNonNull(world).notifyNeighborsOfStateChange(pos, world.getBlockState(pos).getBlock());
            }
        }

        return voxelRef == null || !voxelRef.equals(originalRef);
    }

    public void setBlob(
      final VoxelBlob vb,
      final boolean triggerUpdates)
    {
        final int olv = getLightValue();
        final boolean oldNC = isNormalCube();

        final VoxelStats common = vb.getVoxelStats();
        final float light = common.blockLight;
        final boolean nc = common.isNormalBlock;
        final int lv = Math.max(0, Math.min(15, (int) (light * 15)));

        // are most of the bits in the center solid?
        final int sideFlags = vb.getSideFlags(5, 11, 4 * 4);

        if (getWorld() == null)
        {
            if (common.mostCommonState == 0)
            {
                common.mostCommonState = getPrimaryBlockStateId();
            }

            sideState = sideFlags;
            lightLevel = lv;
            isNormalCube = nc;

            setBlobStateReference(new VoxelBlobStateReference(vb.blobToBytes(VoxelBlob.VERSION_COMPACT_PALLETED), getPositionRandom(pos)));
            setPrimaryBlockStateId(common.mostCommonState);
            setState(getState(), getBlobStateReference());
            return;
        }

        if (common.isFullBlock)
        {
            setBlobStateReference(new VoxelBlobStateReference(common.mostCommonState, getPositionRandom(pos)));
            setState(getState(), getBlobStateReference());

            final BlockState newState = ModUtil.getStateById(common.mostCommonState);
            if (ChiselsAndBits.getConfig().getServer().canRevertToBlock(newState))
            {
                if (!MinecraftForge.EVENT_BUS.post(new EventFullBlockRestoration(Objects.requireNonNull(world), pos, newState)))
                {
                    world.setBlockState(pos, newState, triggerUpdates ? 3 : 0);
                }
            }
        }
        else if (common.mostCommonState != 0)
        {
            sideState = sideFlags;
            lightLevel = lv;
            isNormalCube = nc;

            setBlobStateReference(new VoxelBlobStateReference(vb.blobToBytes(VoxelBlob.VERSION_COMPACT_PALLETED), getPositionRandom(pos)));
            setPrimaryBlockStateId(common.mostCommonState);
            setState(getState(), getBlobStateReference());

            getTileContainer().saveData();
            getTileContainer().sendUpdate();

            // since its possible for bits to occlude parts.. update every time.
            final Block blk = Objects.requireNonNull(world).getBlockState(pos).getBlock();
            // worldObj.notifyBlockOfStateChange( pos, blk, false );

            if (triggerUpdates)
            {
                world.notifyNeighborsOfStateChange(pos, blk);
            }
        }
        else
        {
            setBlobStateReference(new VoxelBlobStateReference(0, getPositionRandom(pos)));
            setState(getState(), getBlobStateReference());

            ModUtil.removeChiseledBlock(Objects.requireNonNull(world), pos);
        }

        if (olv != lv || oldNC != nc)
        {
            Objects.requireNonNull(world).getLightManager().checkBlock(pos);

            // update block state to reflect lighting characteristics
            final BlockState state = world.getBlockState(pos);
            if (state.isNormalCube(new SingleBlockBlockReader(state), BlockPos.ZERO) != isNormalCube && state.getBlock() instanceof BlockChiseled)
            {
                world.setBlockState(pos, state.with(BlockChiseled.FULL_BLOCK, isNormalCube));
            }
        }
    }

    static private class ItemStackGeneratedCache
    {
        public ItemStackGeneratedCache(
          final ItemStack itemstack,
          final VoxelBlobStateReference blobStateReference,
          final int rotations2)
        {
            out = itemstack == null ? null : itemstack.copy();
            ref = blobStateReference;
            rotations = rotations2;
        }

        final ItemStack               out;
        final VoxelBlobStateReference ref;
        final int                     rotations;

        public ItemStack getItemStack()
        {
            return out == null ? null : out.copy();
        }
    }

    /**
     * prevent mods that constantly ask for pick block from killing the client... ( looking at you waila )
     **/
    private ItemStackGeneratedCache pickCache = null;

    public ItemStack getItemStack(
      final PlayerEntity player)
    {
        final ItemStackGeneratedCache cache = pickCache;

        if (player != null)
        {
            Direction placingFace = ModUtil.getPlaceFace(player);
            final int rotations = ModUtil.getRotationIndex(placingFace);

            if (cache != null && cache.rotations == rotations && cache.ref == getBlobStateReference() && cache.out != null)
            {
                return cache.getItemStack();
            }

            VoxelBlob vb = getBlob();

            int countDown = rotations;
            while (countDown > 0)
            {
                countDown--;
                placingFace = placingFace.rotateYCCW();
                vb = vb.spin(Axis.Y);
            }

            final BitAccess ba = new BitAccess(null, null, vb, VoxelBlob.NULL_BLOB);
            final ItemStack itemstack = ba.getBitsAsItem(placingFace, ItemType.CHISLED_BLOCK, false);

            pickCache = new ItemStackGeneratedCache(itemstack, getBlobStateReference(), rotations);
            return itemstack;
        }
        else
        {
            if (cache != null && cache.rotations == 0 && cache.ref == getBlobStateReference())
            {
                return cache.getItemStack();
            }

            final BitAccess ba = new BitAccess(null, null, getBlob(), VoxelBlob.NULL_BLOB);
            final ItemStack itemstack = ba.getBitsAsItem(null, ItemType.CHISLED_BLOCK, false);

            pickCache = new ItemStackGeneratedCache(itemstack, getBlobStateReference(), 0);
            return itemstack;
        }
    }

    public boolean isNormalCube()
    {
        return isNormalCube;
    }

    public boolean isSideSolid(
      final Direction side)
    {
        return (sideState & 1 << side.ordinal()) != 0;
    }

    public boolean isSideOpaque(
      final Direction side)
    {
        if (this.getWorld() != null && this.getWorld().isRemote)
        {
            return isInnerSideOpaque(side);
        }

        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public boolean isInnerSideOpaque(
      final Direction side)
    {
        final int sideFlags = ChiseledBlockSmartModel.getSides(this);
        return (sideFlags & 1 << side.ordinal()) != 0;
    }

    public void completeEditOperation(
      final VoxelBlob vb)
    {
        final VoxelBlobStateReference before = getBlobStateReference();
        setBlob(vb);
        final VoxelBlobStateReference after = getBlobStateReference();

        if (world != null)
        {
            world.markBlockRangeForRenderUpdate(pos, world.getBlockState(pos), Blocks.AIR.getDefaultState());
        }

        UndoTracker.getInstance().add(getWorld(), getPos(), before, after);
    }

    //TODO: Figure this out.
    public void rotateBlock()
    {
        final VoxelBlob occluded = new VoxelBlob();

        VoxelBlob postRotation = getBlob();
        int maxRotations = 4;
        while (--maxRotations > 0)
        {
            postRotation = postRotation.spin(Axis.Y);

            if (occluded.canMerge(postRotation))
            {
                setBlob(postRotation);
                return;
            }
        }
    }

    public boolean canMerge(
      final VoxelBlob voxelBlob)
    {
        final VoxelBlob vb = getBlob();
        final IChiseledTileContainer occ = getTileContainer();

        return vb.canMerge(voxelBlob) && !occ.isBlobOccluded(voxelBlob);
    }

    public boolean isEmpty(
      final VoxelBlob voxelBlob)
    {
        final VoxelBlob vb = getBlob();
        final IChiseledTileContainer occ = getTileContainer();

        return vb.isEmpty(voxelBlob) && !occ.isBlobOccluded(voxelBlob);
    }


    @NotNull
    @Override
    public Collection<AxisAlignedBB> getBoxes(
      @NotNull final BoxType type)
    {
        final VoxelBlobStateReference ref = getBlobStateReference();

        if (ref != null)
        {
            return ref.getBoxes(type);
        }
        else
        {
            return Collections.emptyList();
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox()
    {
        final BlockPos p = getPos();
        return new AxisAlignedBB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1);
    }

    public void setNormalCube(
      final boolean b)
    {
        isNormalCube = b;
    }

    public static void setLightFromBlock(
      final BlockState defaultState)
    {
        if (defaultState == null)
        {
            LOCAL_LIGHT_LEVEL.remove();
        }
        else
        {
            LOCAL_LIGHT_LEVEL.set(DeprecationHelper.getLightValue(defaultState));
        }
    }

    public int getLightValue()
    {
        // first time requested, pull from local, or default to 0
        if (lightLevel < 0)
        {
            final Integer tmp = LOCAL_LIGHT_LEVEL.get();
            lightLevel = tmp == null ? 0 : tmp;
        }

        return lightLevel;
    }

    @NotNull
    @Override
    public IBitAccess getBitAccess()
    {
        VoxelBlob mask = VoxelBlob.NULL_BLOB;

        if (world != null)
        {
            mask = new VoxelBlob();
        }

        return new BitAccess(world, pos, getBlob(), mask);
    }

    @Override
    public IModelData getModelData()
    {
        return new ModelDataMap.Builder().withInitial(
          MP_PBSI, getPrimaryBlockStateId()
        ).withInitial(
          MP_VBSR, getBlobStateReference()
        ).build();
    }
}
