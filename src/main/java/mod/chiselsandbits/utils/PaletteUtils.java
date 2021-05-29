package mod.chiselsandbits.utils;

import com.google.common.collect.Lists;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IntIdentityHashBiMap;
import net.minecraft.util.palette.ArrayPalette;
import net.minecraft.util.palette.HashMapPalette;
import net.minecraft.util.palette.IPalette;
import net.minecraft.util.palette.IdentityPalette;
import net.minecraftforge.registries.GameData;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class PaletteUtils
{

    private PaletteUtils()
    {
        throw new IllegalStateException("Can not instantiate an instance of: PaletteUtils. This is a utility class");
    }

    public static List<BlockState> getOrderedListInPalette(final IPalette<BlockState> stateIPalette)
    {
        if (stateIPalette instanceof ArrayPalette)
        {
            return Arrays.asList(((ArrayPalette<BlockState>) stateIPalette).states);
        }

        if (stateIPalette instanceof HashMapPalette)
        {
            final IntIdentityHashBiMap<BlockState> map = ((HashMapPalette<BlockState>) stateIPalette).statePaletteMap;

            final List<BlockState> dataList = Lists.newArrayList(map);
            dataList.sort(Comparator.comparing(map::getId));

            return dataList;
        }

        if (stateIPalette instanceof IdentityPalette)
        {
            final List<BlockState> dataList = Lists.newArrayList(GameData.getBlockStateIDMap());
            dataList.sort(Comparator.comparing(GameData.getBlockStateIDMap()::getId));

            return dataList;
        }

        throw new IllegalArgumentException("The given palette type is unknown.");
    }

    public static void read(final IPalette<BlockState> stateIPalette, final PacketBuffer buffer)
    {
        if (stateIPalette instanceof ArrayPalette)
        {
            final ArrayPalette<BlockState> palette = (ArrayPalette<BlockState>) stateIPalette;
            palette.arraySize = buffer.readVarInt();

            final Object[] statesArray = palette.states;

            for(int i = 0; i < palette.arraySize; ++i) {
                final Object registryEntry = palette.registry.getByValue(buffer.readVarInt());
                statesArray[i] = registryEntry;
            }
        }

        if (stateIPalette instanceof HashMapPalette)
        {
            final HashMapPalette<BlockState> palette = (HashMapPalette<BlockState>) stateIPalette;
            palette.statePaletteMap.clear();
            int i = buffer.readVarInt();

            for(int j = 0; j < i; ++j) {
                palette.statePaletteMap.add(palette.registry.getByValue(buffer.readVarInt()));
            }

        }
    }
}
