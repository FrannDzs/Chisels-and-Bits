package mod.chiselsandbits.render.chiseledblock;

import mod.chiselsandbits.chiseledblock.NBTBlobConverter;
import mod.chiselsandbits.chiseledblock.TileEntityBlockChiseled;
import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.chiseledblock.data.VoxelBlobStateInstance;
import mod.chiselsandbits.chiseledblock.data.VoxelBlobStateReference;
import mod.chiselsandbits.client.model.baked.BaseSmartModel;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.helpers.ModUtil;
import mod.chiselsandbits.interfaces.ICacheClearable;
import mod.chiselsandbits.render.ModelCombined;
import mod.chiselsandbits.render.NullBakedModel;
import mod.chiselsandbits.render.cache.CacheMap;
import mod.chiselsandbits.utils.SimpleMaxSizedCache;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.World;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.common.ForgeConfig;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.util.*;

public class ChiseledBlockSmartModel extends BaseSmartModel implements ICacheClearable
{
    private static final SimpleMaxSizedCache<ModelCacheKey, ChiseledBlockBakedModel> MODEL_CACHE = new SimpleMaxSizedCache<>(ChiselsAndBits.getConfig().getClient().modelCacheSize.get());
    private static final CacheMap<ItemStack, IBakedModel>          ITEM_TO_MODEL_CACHE = new CacheMap<>();
    private static final CacheMap<VoxelBlobStateInstance, Integer> SIDE_CACHE          = new CacheMap<>();

    public static final BitSet FLUID_RENDER_TYPES = new BitSet(RenderType.getBlockRenderTypes().size());

    public static int getSides(
      final TileEntityBlockChiseled te)
    {
        final VoxelBlobStateReference ref = te.getBlobStateReference();
        Integer out;

        if (ref == null)
        {
            return 0;
        }

        synchronized (SIDE_CACHE)
        {
            out = SIDE_CACHE.get(ref.getInstance());
            if (out == null)
            {
                final VoxelBlob blob = ref.getVoxelBlob();

                // ignore non-solid, and fluids.
                blob.filter(RenderType.getSolid());
                blob.filterFluids(false);

                out = blob.getSideFlags(0, VoxelBlob.dim_minus_one, VoxelBlob.dim2);
                SIDE_CACHE.put(ref.getInstance(), out);
            }
        }

        return out;
    }

    public static ChiseledBlockBakedModel getCachedModel(
      final TileEntityBlockChiseled te,
      final ChiselRenderType layer)
    {
        final VoxelBlobStateReference data = te.getBlobStateReference();
        Integer blockP = te.getPrimaryBlockStateId();
        VoxelBlob vBlob = (data != null) ? data.getVoxelBlob() : null;
        return getCachedModel(blockP, vBlob, layer, getModelFormat(), Objects.requireNonNull(te.getWorld()).rand);
    }

    public static ChiseledBlockBakedModel getCachedModel(
      final ItemStack stack,
      final ChiselRenderType layer)
    {
        final VoxelBlob blob = ModUtil.getBlobFromStack(stack, null);
        Integer blockP = blob.getVoxelStats().mostCommonState;
        return getCachedModel(blockP, ModUtil.getBlobFromStack(stack, null), layer, getModelFormat(), new Random());
    }

    private static VertexFormat getModelFormat()
    {
        return DefaultVertexFormats.BLOCK;
    }

    public static boolean ForgePipelineDisabled()
    {
        return !ForgeConfig.CLIENT.forgeLightPipelineEnabled.get() || ChiselsAndBits.getConfig().getClient().disableCustomVertexFormats.get();
    }

    public static ChiseledBlockBakedModel getCachedModel(
      final Integer blockP,
      final VoxelBlob data,
      final ChiselRenderType layer,
      final VertexFormat format,
      final Random random)
    {
        if (data == null)
        {
            return new ChiseledBlockBakedModel(blockP, layer, null);
        }

        ChiseledBlockBakedModel out = null;

        if (format == getModelFormat())
        {
            out = MODEL_CACHE.get(new ModelCacheKey(data, layer));
        }

        if (out == null)
        {
            out = new ChiseledBlockBakedModel(blockP, layer, data);

            if (out.isEmpty())
            {
                out = ChiseledBlockBakedModel.breakingParticleModel(layer, blockP, random);
            }

            if (format == getModelFormat())
            {
                MODEL_CACHE.put(new ModelCacheKey(data, layer), out);
            }
        }
        else
        {
            return out;
        }

        return out;
    }

    @Override
    public IBakedModel handleBlockState(final BlockState state, final Random rand, final IModelData modelData)
    {
        if (state == null)
        {
            return NullBakedModel.instance;
        }

        // This seems silly, but it proves to be faster in practice.
        VoxelBlobStateReference data = modelData.getData(TileEntityBlockChiseled.MP_VBSR);
        final VoxelBlob blob = data == null ? null : data.getVoxelBlob();
        Integer blockP = modelData.getData(TileEntityBlockChiseled.MP_PBSI);
        blockP = blockP == null ? 0 : blockP;

        final RenderType layer = net.minecraftforge.client.MinecraftForgeClient.getRenderLayer();

        if (layer == null)
        {
            final ChiseledBlockBakedModel[] models = new ChiseledBlockBakedModel[ChiselRenderType.values().length];
            int o = 0;

            for (final ChiselRenderType l : ChiselRenderType.values())
            {
                models[o++] = getCachedModel(blockP, blob, l, getModelFormat(), rand);
            }

            return new ModelCombined(models);
        }

        IBakedModel baked;
        if (RenderType.getBlockRenderTypes().contains(layer) && FLUID_RENDER_TYPES.get(RenderType.getBlockRenderTypes().indexOf(layer)))
        {
            final ChiseledBlockBakedModel a = getCachedModel(blockP, blob, ChiselRenderType.fromLayer(layer, false), getModelFormat(), rand);
            final ChiseledBlockBakedModel b = getCachedModel(blockP, blob, ChiselRenderType.fromLayer(layer, true), getModelFormat(), rand);

            if (a.isEmpty())
            {
                baked = b;
            }
            else if (b.isEmpty())
            {
                baked = a;
            }
            else
            {
                baked = new ModelCombined(a, b);
            }
        }
        else
        {
            baked = getCachedModel(blockP, blob, ChiselRenderType.fromLayer(layer, false), getModelFormat(), rand);
        }

        return baked;
    }

    @Override
    public IBakedModel func_239290_a_(final IBakedModel originalModel, final ItemStack stack, final World world, final LivingEntity entity)
    {
        IBakedModel mdl = ITEM_TO_MODEL_CACHE.get(stack);

        if (mdl != null)
        {
            return mdl;
        }

        CompoundNBT c = stack.getTag();
        if (c == null)
        {
            return this;
        }

        c = c.getCompound(ModUtil.NBT_BLOCKENTITYTAG);

        final byte[] data = c.getByteArray(NBTBlobConverter.NBT_LEGACY_VOXEL);
        byte[] vdata = c.getByteArray(NBTBlobConverter.NBT_VERSIONED_VOXEL);
        final Integer blockP = c.getInt(NBTBlobConverter.NBT_PRIMARY_STATE);

        if (vdata.length == 0 && data.length > 0)
        {
            final VoxelBlob xx = new VoxelBlob();

            try
            {
                xx.fromLegacyByteArray(data);
            }
            catch (final IOException e)
            {
                // :_(
            }

            vdata = xx.blobToBytes(VoxelBlob.VERSION_COMPACT_PALLETED);
        }

        final IBakedModel[] models = new IBakedModel[ChiselRenderType.values().length];
        for (final ChiselRenderType l : ChiselRenderType.values())
        {
            models[l.ordinal()] = getCachedModel(blockP, new VoxelBlobStateReference(vdata, 0L).getVoxelBlob(), l, DefaultVertexFormats.BLOCK, new Random());
        }

        mdl = new ModelCombined(models);

        ITEM_TO_MODEL_CACHE.put(stack, mdl);

        return mdl;
    }

    @Override
    public void clearCache()
    {
        SIDE_CACHE.clear();
        MODEL_CACHE.clear();
        ITEM_TO_MODEL_CACHE.clear();

        FLUID_RENDER_TYPES.clear();
        final List<RenderType> blockRenderTypes = RenderType.getBlockRenderTypes();
        for (int i = 0; i < blockRenderTypes.size(); i++)
        {
            final RenderType renderType = blockRenderTypes.get(i);
            for (final Fluid fluid : ForgeRegistries.FLUIDS)
            {
                if (RenderTypeLookup.canRenderInLayer(fluid.getDefaultState(), renderType))
                {
                    FLUID_RENDER_TYPES.set(i);
                    break;
                }
            }
        }
    }

    public static void onConfigurationReload(final ModConfig.Reloading event) {
        MODEL_CACHE.changeMaxSize(ChiselsAndBits.getConfig().getClient().modelCacheSize.get());
    }

    @Override
    public boolean isSideLit()
    {
        return true;
    }

    private static final class ModelCacheKey {
        private final VoxelBlob blob;
        private final ChiselRenderType type;

        private ModelCacheKey(final VoxelBlob blob, final ChiselRenderType type) {
            this.blob = blob;
            this.type = type;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (!(o instanceof ModelCacheKey))
            {
                return false;
            }
            final ModelCacheKey that = (ModelCacheKey) o;
            return Objects.equals(blob, that.blob) &&
                     Objects.equals(type, that.type);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(blob, type);
        }
    }
}
