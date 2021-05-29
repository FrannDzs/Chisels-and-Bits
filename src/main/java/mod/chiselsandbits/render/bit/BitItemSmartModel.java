package mod.chiselsandbits.render.bit;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

import mod.chiselsandbits.chiseledblock.data.VoxelBlob;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.events.TickHandler;
import mod.chiselsandbits.interfaces.ICacheClearable;
import mod.chiselsandbits.items.ItemChiseledBit;
import mod.chiselsandbits.client.model.baked.BaseSmartModel;
import mod.chiselsandbits.registry.ModItems;
import mod.chiselsandbits.render.ModelCombined;
import mod.chiselsandbits.render.chiseledblock.ChiselRenderType;
import mod.chiselsandbits.render.chiseledblock.ChiseledBlockBakedModel;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

public class BitItemSmartModel extends BaseSmartModel implements ICacheClearable
{
	static private final HashMap<Integer, IBakedModel> modelCache      = new HashMap<Integer, IBakedModel>();
	static private final HashMap<Integer, IBakedModel> largeModelCache = new HashMap<Integer, IBakedModel>();

    static private final NonNullList<ItemStack> alternativeStacks = NonNullList.create();

	private IBakedModel getCachedModel(
			int stateID,
			final boolean large )
	{
	    if (stateID == 0) {
	        //We are running an empty bit, for display purposes.
            //Lets loop:
            if (alternativeStacks.isEmpty())
                ModItems.ITEM_BLOCK_BIT.get().fillItemGroup(Objects.requireNonNull(ModItems.ITEM_BLOCK_BIT.get().getGroup()), alternativeStacks);

            final int alternativeIndex = (int)((Math.floor(TickHandler.getClientTicks() / 20d)) % alternativeStacks.size());

            stateID = ItemChiseledBit.getStackState( alternativeStacks.get(alternativeIndex));
        }

		final HashMap<Integer, IBakedModel> target = large ? largeModelCache : modelCache;
		IBakedModel out = target.get( stateID );

		if ( out == null )
		{
			if ( large )
			{
				final int workingStateId = stateID;
				out = new ModelCombined(Arrays.stream(ChiselRenderType.values())
                  .map(type -> {
                      final VoxelBlob blob = new VoxelBlob();
                      blob.fill( workingStateId );

                      return new ChiseledBlockBakedModel(workingStateId, type, blob);
                  })
                  .filter(model -> !model.isEmpty())
                  .toArray(IBakedModel[]::new));
			}
			else
			{
				out = new BitItemBaked( stateID );
			}

			target.put( stateID, out );
		}

		return out;
	}

    public IBakedModel func_239290_a_(
      final IBakedModel originalModel,
      final ItemStack stack,
      final World world,
      final LivingEntity entity )
    {
        return getCachedModel( ItemChiseledBit.getStackState( stack ), ClientSide.instance.holdingShift() );
    }

	@Override
	public void clearCache()
	{
		modelCache.clear();
		largeModelCache.clear();
	}

    @Override
    public boolean isSideLit()
    {
        return true;
    }
}
