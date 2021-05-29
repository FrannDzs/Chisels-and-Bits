package mod.chiselsandbits.chiseledblock.serialization;

import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.utils.PaletteUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ObjectIntIdentityMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.palette.*;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.registries.GameData;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NbtBasedPalettedBlobSerializer extends BlobSerializer implements IResizeCallback<BlockState>
{
    private final ObjectIntIdentityMap<BlockState> registry = GameData.getBlockStateIDMap();
    private       HashMapPalette<BlockState>    palette = new HashMapPalette<>(this.registry, 16, this, NBTUtil::readBlockState, NBTUtil::writeBlockState);

    public NbtBasedPalettedBlobSerializer(final VoxelBlob toDeflate)
    {
        super(toDeflate);

        //Setup the palette ids.
        final Map<Integer, Integer> entries = toDeflate.getBlockSums();
        for ( final Map.Entry<Integer, Integer> o : entries.entrySet() )
        {
            this.palette.idFor(ModUtil.getStateById(o.getKey()));
        }
    }

    public NbtBasedPalettedBlobSerializer(final PacketBuffer toInflate)
    {
        super();

        final CompoundNBT wrapper = toInflate.readCompoundTag();
        final ListNBT paletteNBT = Objects.requireNonNull(wrapper).getList("data", Constants.NBT.TAG_COMPOUND);
        this.palette.read(paletteNBT);
    }


    @Override
    public void write(final PacketBuffer to)
    {
        final ListNBT paletteNBT = new ListNBT();
        this.palette.writePaletteToList(paletteNBT);

        final CompoundNBT wrapper = new CompoundNBT();
        wrapper.put("data", paletteNBT);

        to.writeCompoundTag(wrapper);
    }

    @Override
    protected int readStateID(final PacketBuffer buffer)
    {
        //Not needed because of different palette system.
        return 0;
    }

    @Override
    protected void writeStateID(final PacketBuffer buffer, final int key)
    {
        //Noop
    }

    @Override
    protected int getIndex(final int stateID)
    {
        return this.palette.idFor(ModUtil.getStateById(stateID));
    }

    @Override
    protected int getStateID(final int indexID)
    {
        return ModUtil.getStateId(this.palette.get(indexID));
    }

    @Override
    public int getVersion()
    {
        return VoxelBlob.VERSION_COMPACT_PALLETED;
    }

    @Override
    public int onResize(final int newBitSize, final BlockState violatingBlockState)
    {
        final IPalette<BlockState> currentPalette = this.palette;
        final List<BlockState> ids = PaletteUtils.getOrderedListInPalette(currentPalette);
        ids.forEach(this.palette::idFor);

        return this.palette.idFor(violatingBlockState);
    }
}
