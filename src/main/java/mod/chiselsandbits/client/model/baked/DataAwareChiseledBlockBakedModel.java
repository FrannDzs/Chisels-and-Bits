package mod.chiselsandbits.client.model.baked;

import mod.chiselsandbits.chiseledblock.TileEntityBlockChiseled;
import mod.chiselsandbits.chiseledblock.data.VoxelBlobStateReference;
import mod.chiselsandbits.chiseledblock.data.VoxelType;
import mod.chiselsandbits.render.ModelCombined;
import mod.chiselsandbits.render.NullBakedModel;
import mod.chiselsandbits.render.chiseledblock.ChiselRenderType;
import mod.chiselsandbits.render.chiseledblock.ChiseledBlockBakedModel;
import mod.chiselsandbits.render.chiseledblock.ChiseledBlockSmartModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.data.ModelDataMap;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class DataAwareChiseledBlockBakedModel extends BaseSmartModel
{
    private final ModelProperty<IBakedModel> MODEL_PROP = new ModelProperty<>();

    @Override
    public boolean isSideLit()
    {
        return true;
    }

    @Override
    public IBakedModel handleBlockState(final BlockState state, final Random random, final IModelData modelData)
    {
        if (!modelData.hasProperty(MODEL_PROP))
            return NullBakedModel.instance;

        return modelData.getData(MODEL_PROP);
    }

    @NotNull
    @Override
    public IModelData getModelData(
      @NotNull final IBlockDisplayReader world, @NotNull final BlockPos pos, @NotNull final BlockState state, @NotNull final IModelData modelData)
    {
/*        final ChiseledBlockBaked model = ChiseledBlockSmartModel.getCachedModel(
          (TileEntityBlockChiseled) Objects.requireNonNull(world.getTileEntity(pos)),
          ChiselRenderType.fromLayer(
            MinecraftForgeClient.getRenderLayer(),
            false
          )
        );*/

        if (state == null || world.getTileEntity(pos) == null)
        {
            return new ModelDataMap.Builder().build();
        }

        // This seems silly, but it proves to be faster in practice.
        VoxelBlobStateReference data = modelData.getData(TileEntityBlockChiseled.MP_VBSR);
        Integer blockP = modelData.getData(TileEntityBlockChiseled.MP_PBSI);
        blockP = blockP == null ? 0 : blockP;

        final RenderType layer = net.minecraftforge.client.MinecraftForgeClient.getRenderLayer();

        if (layer == null)
        {
            final ChiseledBlockBakedModel[] models = new ChiseledBlockBakedModel[ChiselRenderType.values().length];
            int o = 0;

            for (final ChiselRenderType l : ChiselRenderType.values())
            {
                models[o++] = ChiseledBlockSmartModel.getCachedModel(
                  (TileEntityBlockChiseled) Objects.requireNonNull(world.getTileEntity(pos)),
                  l);
            }

            return new ModelDataMap.Builder().withInitial(MODEL_PROP, new ModelCombined(models)).build();
        }

        IBakedModel baked;
        if (RenderType.getBlockRenderTypes().contains(layer) && ChiseledBlockSmartModel.FLUID_RENDER_TYPES.get(RenderType.getBlockRenderTypes().indexOf(layer)))
        {
            final ChiseledBlockBakedModel a = ChiseledBlockSmartModel.getCachedModel(
              (TileEntityBlockChiseled) Objects.requireNonNull(world.getTileEntity(pos)),
              ChiselRenderType.fromLayer(layer, false));
            final ChiseledBlockBakedModel b = ChiseledBlockSmartModel.getCachedModel(
              (TileEntityBlockChiseled) Objects.requireNonNull(world.getTileEntity(pos)),
              ChiselRenderType.fromLayer(layer, true));

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
            baked = ChiseledBlockSmartModel.getCachedModel(
              (TileEntityBlockChiseled) Objects.requireNonNull(world.getTileEntity(pos)),
              ChiselRenderType.fromLayer(layer, false));
        }

        return new ModelDataMap.Builder().withInitial(MODEL_PROP, baked).build();
    }

    @Override
    public IBakedModel func_239290_a_(
      final IBakedModel originalModel, final ItemStack stack, final World world, final LivingEntity entity)
    {
        final IBakedModel[] typedModels = Arrays.stream(VoxelType.values())
          .map(ChiselRenderType::getRenderTypes)
          .filter(types -> !types.isEmpty())
          .map(types -> {
              final IBakedModel[] models = types.stream()
                       .map(type ->  ChiseledBlockSmartModel.getCachedModel(stack, type))
                       .filter(model -> !model.isEmpty())
                       .toArray(IBakedModel[]::new);
              if (models.length == 0)
                  return ChiseledBlockBakedModel.EMPTY;

              return new ModelCombined(models);
          })
          .toArray(IBakedModel[]::new);

        if (typedModels.length == 0)
            return ChiseledBlockBakedModel.EMPTY;

        return new ModelCombined(typedModels);
    }
}
