package mod.chiselsandbits.printer;

import mod.chiselsandbits.bitstorage.TileEntityBitStorage;
import mod.chiselsandbits.chiseledblock.BlockBitInfo;
import mod.chiselsandbits.chiseledblock.NBTBlobConverter;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.helpers.LocalStrings;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.interfaces.IPatternItem;
import mod.chiselsandbits.items.ItemChisel;
import mod.chiselsandbits.registry.ModBlocks;
import mod.chiselsandbits.registry.ModTileEntityTypes;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.IIntArray;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullLazy;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.EmptyHandler;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ChiselPrinterTileEntity extends TileEntity implements ITickableTileEntity, INamedContainerProvider
{
    private final LazyOptional<EmptyHandler> empty_handler = LazyOptional.of(NonNullLazy.of(EmptyHandler::new));
    private final LazyOptional<ItemStackHandler> tool_handler   = LazyOptional.of(NonNullLazy.of(() -> new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(final int slot, @NotNull final ItemStack stack)
        {
            return stack.getItem() instanceof ItemChisel;
        }

        @Override
        public int getSlotLimit(final int slot)
        {
            return 1;
        }
    }));
    private final LazyOptional<ItemStackHandler> pattern_handler   = LazyOptional.of(NonNullLazy.of(() -> new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(final int slot, @NotNull final ItemStack stack)
        {
            return stack.getItem() instanceof IPatternItem;
        }

        @Override
        public int getSlotLimit(final int slot)
        {
            return 1;
        }

        @Override
        protected void onContentsChanged(final int slot)
        {
            currentRealisedWorkingStack.setValue(ItemStack.EMPTY);
        }
    }));
    private final LazyOptional<ItemStackHandler> result_handler = LazyOptional.of(NonNullLazy.of(() -> new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(final int slot, @NotNull final ItemStack stack)
        {
            return true;
        }
    }));

    protected final IIntArray stationData = new IIntArray() {
        public int get(int index) {
            if (index == 0)
            {
                return ChiselPrinterTileEntity.this.progress;
            }
            return 0;
        }

        public void set(int index, int value) {
            if (index == 0)
            {
                ChiselPrinterTileEntity.this.progress = value;
            }
        }

        public int size() {
            return 1;
        }
    };

    private int progress = 0;
    private long lastTickTime = 0L;
    private final MutableObject<ItemStack> currentRealisedWorkingStack = new MutableObject<>(ItemStack.EMPTY);

    public ChiselPrinterTileEntity()
    {
        super(ModTileEntityTypes.CHISEL_PRINTER.get());
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull final Capability<T> cap, @Nullable final Direction side)
    {
        if (cap != CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
            return super.getCapability(cap, side);

        if (side != null)
        {
            switch(side){
                case DOWN:
                    return result_handler.cast();
                case UP:
                case NORTH:
                case SOUTH:
                case WEST:
                case EAST:
                    return tool_handler.cast();
            }
        }

        return empty_handler.cast();
    }

    @Override
    public void read(final BlockState state, final CompoundNBT nbt)
    {
        super.read(state, nbt);

        tool_handler.ifPresent(h -> h.deserializeNBT(nbt.getCompound("tool")));
        pattern_handler.ifPresent(h -> h.deserializeNBT(nbt.getCompound("pattern")));
        result_handler.ifPresent(h -> h.deserializeNBT(nbt.getCompound("result")));

        progress = nbt.getInt("progress");
    }

    @Override
    public CompoundNBT write(final CompoundNBT compound)
    {
        super.write(compound);

        tool_handler.ifPresent(h -> compound.put("tool", h.serializeNBT()));
        pattern_handler.ifPresent(h -> compound.put("pattern", h.serializeNBT()));
        result_handler.ifPresent(h -> compound.put("result", h.serializeNBT()));

        compound.putInt("progress", progress);

        return compound;
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        final CompoundNBT nbt = new CompoundNBT();
        write(nbt);
        return nbt;
    }

    @Override
    public void tick()
    {
        if (getWorld() == null || lastTickTime == getWorld().getGameTime() || getWorld().isRemote()) {
            return;
        }

        this.lastTickTime = getWorld().getGameTime();

        if (couldWork()) {
           if (canWork()) {
                progress++;
                if (progress >= 100) {
                    result_handler.ifPresent(h -> h.insertItem(0, realisePattern(true), false));
                    currentRealisedWorkingStack.setValue(ItemStack.EMPTY);
                    progress = 0;
                    damageChisel();
                }
                markDirty();
            }
        } else if (progress != 0) {
            progress = 0;
            markDirty();
        }
    }

    public IItemHandlerModifiable getPatternHandler() {
        return pattern_handler.orElseThrow(() -> new IllegalStateException("Missing empty handler."));
    }

    public IItemHandlerModifiable getToolHandler() {
        return tool_handler.orElseThrow(() -> new IllegalStateException("Missing tool handler."));
    }

    public IItemHandlerModifiable getResultHandler() {
        return result_handler.orElseThrow(() -> new IllegalStateException("Missing result handler."));
    }

    public boolean hasPatternStack() {
        return !getPatternStack().isEmpty();
    }

    public boolean hasToolStack() {
        return !getToolStack().isEmpty();
    }

    public boolean hasRealisedStack() {
        return !getRealisedStack().isEmpty();
    }

    public boolean hasOutputStack() {
        return !getOutputStack().isEmpty();
    }

    public boolean canMergeOutputs() {
        if (!hasOutputStack())
            return true;

        if (!hasRealisedStack())
            return false;

        return ItemHandlerHelper.canItemStacksStack(getOutputStack(), getRealisedStack());
    }

    public boolean canWork() {
        return hasPatternStack() && hasToolStack() && canMergeOutputs();
    }

    public boolean couldWork() {
        return hasPatternStack() && hasToolStack();
    }

    public boolean hasMergeableInput() {
        if (!hasOutputStack())
            return true;

        return ItemHandlerHelper.canItemStacksStack(getOutputStack(), realisePattern(false));
    }

    public ItemStack getPatternStack() {
        return pattern_handler.map(h -> h.getStackInSlot(0)).orElse(ItemStack.EMPTY);
    }

    public ItemStack getToolStack() {
        return tool_handler.map(h -> h.getStackInSlot(0)).orElse(ItemStack.EMPTY);
    }

    public ItemStack getRealisedStack() {
        ItemStack realisedStack = currentRealisedWorkingStack.getValue();
        if (realisedStack.isEmpty()) {
            realisedStack = realisePattern(false);
            currentRealisedWorkingStack.setValue(realisedStack);
        }

        return realisedStack;
    }

    public ItemStack getOutputStack() {
        return result_handler.map(h -> h.getStackInSlot(0)).orElse(ItemStack.EMPTY);
    }

    private ItemStack realisePattern(final boolean consumeResources) {
        if (!hasPatternStack())
            return ItemStack.EMPTY;

        final ItemStack stack = getPatternStack();
        if (!(stack.getItem() instanceof IPatternItem))
            return ItemStack.EMPTY;

        final IPatternItem patternItem = (IPatternItem) stack.getItem();
        final ItemStack realisedPattern = patternItem.getPatternedItem(stack.copy(), true);
        if (realisedPattern == null || realisedPattern.isEmpty())
            return ItemStack.EMPTY;

        BlockState firstState = getPrimaryBlockState();
        BlockState secondState = getSecondaryBlockState();
        BlockState thirdState = getTertiaryBlockState();

        if (firstState == null)
            firstState = Blocks.AIR.getDefaultState();

        if (secondState == null)
            secondState = Blocks.AIR.getDefaultState();

        if (thirdState == null)
            thirdState = Blocks.AIR.getDefaultState();

        if ((!BlockBitInfo.isSupported(firstState) && !firstState.isAir())
                || (!BlockBitInfo.isSupported(secondState) && !secondState.isAir())
                || (!BlockBitInfo.isSupported(thirdState) && !thirdState.isAir())
        )
            return ItemStack.EMPTY;

        final NBTBlobConverter c = new NBTBlobConverter();
        final CompoundNBT tag = ModUtil.getSubCompound(realisedPattern, ModUtil.NBT_BLOCKENTITYTAG, false).copy();
        c.readChisleData(tag, VoxelBlob.VERSION_ANY);
        VoxelBlob blob = c.getBlob();

        final VoxelBlob.PartialFillResult fillResult = blob.clearAllBut(
          ModUtil.getStateId(firstState),
          ModUtil.getStateId(secondState),
          ModUtil.getStateId(thirdState)
        );

        if (fillResult.getFirstStateUsedCount() == 0 && fillResult.getSecondStateUsedCount() == 0 && fillResult.getThirdStateUsedCount() == 0)
            return ItemStack.EMPTY;

        if (fillResult.getFirstStateUsedCount() > getAvailablePrimaryBlockState() ||
                fillResult.getSecondStateUsedCount() > getAvailableSecondaryBlockState() ||
                fillResult.getThirdStateUsedCount() > getAvailableTertiaryBlockState())
            return ItemStack.EMPTY;

        if (consumeResources) {
            drainPrimaryStorage(fillResult.getFirstStateUsedCount());
            drainSecondaryStorage(fillResult.getSecondStateUsedCount());
            drainTertiaryStorage(fillResult.getThirdStateUsedCount());
        }

        c.setBlob(blob);

        final BlockState state = c.getPrimaryBlockState();
        final ItemStack itemstack = new ItemStack(ModBlocks.convertGivenStateToChiseledBlock(state), 1 );
        c.writeChiselData(tag, false);

        itemstack.setTagInfo( ModUtil.NBT_BLOCKENTITYTAG, tag );
        return itemstack;
    }



    private void damageChisel() {
        if (getWorld() != null && !getWorld().isRemote())
        {
            getToolStack().attemptDamageItem(1, getWorld().getRandom(), null);
        }
    }

    @Nullable
    @Override
    public Container createMenu(final int containerId, @NotNull final PlayerInventory playerInventory, @NotNull final PlayerEntity playerEntity)
    {
        return new ChiselPrinterContainer(
          containerId,
          playerInventory,
          getPatternHandler(),
          getToolHandler(),
          getResultHandler(),
          stationData);
    }

    @Override
    public ITextComponent getDisplayName()
    {
        return LocalStrings.ChiselStationName.getLocalText();
    }

    public int getAvailablePrimaryBlockState() {
        final Direction facing = Objects.requireNonNull(this.getWorld()).getBlockState(this.getPos()).get(ChiselPrinterBlock.FACING);
        final Direction targetedFacing = facing.rotateY();

        return getStorageContents(targetedFacing);
    }

    public int getAvailableSecondaryBlockState() {
        final Direction facing = Objects.requireNonNull(this.getWorld()).getBlockState(this.getPos()).get(ChiselPrinterBlock.FACING);
        final Direction targetedFacing = facing.rotateY().rotateY();

        return getStorageContents(targetedFacing);
    }

    public int getAvailableTertiaryBlockState() {
        final Direction facing = Objects.requireNonNull(this.getWorld()).getBlockState(this.getPos()).get(ChiselPrinterBlock.FACING);
        final Direction targetedFacing = facing.rotateYCCW();

        return getStorageContents(targetedFacing);
    }

    private int getStorageContents(final Direction targetedFacing)
    {
        final TileEntity targetedTileEntity = this.getWorld().getTileEntity(this.getPos().offset(targetedFacing));
        if (targetedTileEntity instanceof TileEntityBitStorage)
        {
            final TileEntityBitStorage storage = (TileEntityBitStorage) targetedTileEntity;
            return storage.getBits();
        }

        return 0;
    }

    public BlockState getPrimaryBlockState() {
        final Direction facing = Objects.requireNonNull(this.getWorld()).getBlockState(this.getPos()).get(ChiselPrinterBlock.FACING);
        final Direction targetedFacing = facing.rotateY();

        return getStorage(targetedFacing);
    }

    public BlockState getSecondaryBlockState() {
        final Direction facing = Objects.requireNonNull(this.getWorld()).getBlockState(this.getPos()).get(ChiselPrinterBlock.FACING);
        final Direction targetedFacing = facing.rotateY().rotateY();

        return getStorage(targetedFacing);
    }

    public BlockState getTertiaryBlockState() {
        final Direction facing = Objects.requireNonNull(this.getWorld()).getBlockState(this.getPos()).get(ChiselPrinterBlock.FACING);
        final Direction targetedFacing = facing.rotateYCCW();

        return getStorage(targetedFacing);
    }

    private BlockState getStorage(final Direction targetedFacing)
    {
        final TileEntity targetedTileEntity = this.getWorld().getTileEntity(this.getPos().offset(targetedFacing));
        if (targetedTileEntity instanceof TileEntityBitStorage)
        {
            final TileEntityBitStorage storage = (TileEntityBitStorage) targetedTileEntity;
            return storage.getState();
        }

        return Blocks.AIR.getDefaultState();
    }

    public void drainPrimaryStorage(final int amount) {
        final Direction facing = Objects.requireNonNull(this.getWorld()).getBlockState(this.getPos()).get(ChiselPrinterBlock.FACING);
        final Direction targetedFacing = facing.rotateY();

        drainStorage(amount, targetedFacing);
    }

    public void drainSecondaryStorage(final int amount) {
        final Direction facing = Objects.requireNonNull(this.getWorld()).getBlockState(this.getPos()).get(ChiselPrinterBlock.FACING);
        final Direction targetedFacing = facing.rotateY().rotateY();

        drainStorage(amount, targetedFacing);
    }

    public void drainTertiaryStorage(final int amount) {
        final Direction facing = Objects.requireNonNull(this.getWorld()).getBlockState(this.getPos()).get(ChiselPrinterBlock.FACING);
        final Direction targetedFacing = facing.rotateYCCW();

        drainStorage(amount, targetedFacing);
    }

    private void drainStorage(final int amount, final Direction targetedFacing)
    {
        final TileEntity targetedTileEntity = this.getWorld().getTileEntity(this.getPos().offset(targetedFacing));
        if (targetedTileEntity instanceof TileEntityBitStorage)
        {
            final TileEntityBitStorage storage = (TileEntityBitStorage) targetedTileEntity;
            storage.extractBits(0, amount, false);
        }
    }
}
