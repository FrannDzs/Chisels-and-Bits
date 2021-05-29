package mod.chiselsandbits.items;

import mod.chiselsandbits.chiseledblock.data.BitLocation;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.core.ClientSide;
import mod.chiselsandbits.core.ReflectionWrapper;
import mod.chiselsandbits.helpers.*;
import mod.chiselsandbits.interfaces.IChiselModeItem;
import mod.chiselsandbits.interfaces.IItemScrollWheel;
import mod.chiselsandbits.modes.TapeMeasureModes;
import mod.chiselsandbits.network.packets.PacketSetColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.thread.EffectiveSide;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class ItemTapeMeasure extends Item implements IChiselModeItem, IItemScrollWheel
{
	public ItemTapeMeasure(Item.Properties properties)
	{
	    super(properties.maxStackSize(1));
	}

	@Override
	@OnlyIn( Dist.CLIENT )
	public void addInformation(
			final ItemStack stack,
			final World worldIn,
			final List<ITextComponent> tooltip,
			final ITooltipFlag advanced )
	{
		super.addInformation( stack, worldIn, tooltip, advanced );
		ChiselsAndBits.getConfig().getCommon().helpText( LocalStrings.HelpTapeMeasure, tooltip,
				ClientSide.instance.getKeyName( Minecraft.getInstance().gameSettings.keyBindUseItem ),
				ClientSide.instance.getKeyName( Minecraft.getInstance().gameSettings.keyBindUseItem ),
				ClientSide.instance.getKeyName( Minecraft.getInstance().gameSettings.keyBindSneak ),
				ClientSide.instance.getModeKey() );
	}

	@Override
	public ActionResult<ItemStack> onItemRightClick(
			final World worldIn,
			final PlayerEntity playerIn,
			final Hand hand )
	{
		if ( playerIn.isSneaking() && playerIn.getEntityWorld().isRemote )
		{
			ClientSide.instance.tapeMeasures.clear();
		}

		final ItemStack itemstack = playerIn.getHeldItem( hand );
		return new ActionResult<>(ActionResultType.SUCCESS, itemstack);
	}

    @Override
    public ActionResultType onItemUse(final ItemUseContext context)
    {
        if ( context.getWorld().isRemote )
        {
            if ( context.getPlayer().isSneaking() )
            {
                ClientSide.instance.tapeMeasures.clear();
                return ActionResultType.SUCCESS;
            }

            final Pair<Vector3d, Vector3d> PlayerRay = ModUtil.getPlayerRay( context.getPlayer() );
            final Vector3d ray_from = PlayerRay.getLeft();
            final Vector3d ray_to = PlayerRay.getRight();

            final RayTraceContext rayTraceContext = new RayTraceContext(ray_from, ray_to, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, context.getPlayer());

            final BlockRayTraceResult mop = context.getPlayer().getEntityWorld().rayTraceBlocks(rayTraceContext);
            if (mop.getType() == RayTraceResult.Type.BLOCK)
            {
                final BitLocation loc = new BitLocation( mop, BitOperation.CHISEL, false );
                ClientSide.instance.pointAt( ChiselToolType.TAPEMEASURE, loc, context.getHand() );
            }
            else
                return ActionResultType.FAIL;
        }

        return ActionResultType.SUCCESS;
    }

    @Override
    public ITextComponent getHighlightTip(final ItemStack item, final ITextComponent displayName)
    {
        if (EffectiveSide.get().isClient() && displayName instanceof IFormattableTextComponent && ChiselsAndBits.getConfig().getClient().itemNameModeDisplay.get() )
        {
            final IFormattableTextComponent formattableTextComponent = (IFormattableTextComponent) displayName;
            return formattableTextComponent.appendString(" - ").appendString(TapeMeasureModes.getMode( item ).string.getLocal()).appendString(" - ").appendString(DeprecationHelper.translateToLocal( "chiselsandbits.color." + getTapeColor( item ).getTranslationKey()) );
        }

        return displayName;
    }

	public DyeColor getTapeColor(
			final ItemStack item )
	{
		final CompoundNBT compound = item.getTag();
		if ( compound != null && compound.contains( "color" ) )
		{
			try
			{
				return DyeColor.valueOf( compound.getString( "color" ) );
			}
			catch ( final IllegalArgumentException iae )
			{
				// nope!
			}
		}

		return DyeColor.WHITE;
	}

	@Override
	public void scroll(
			final PlayerEntity player,
			final ItemStack stack,
			final int dwheel )
	{
		final DyeColor color = getTapeColor( stack );
		int next = color.ordinal() + ( dwheel < 0 ? -1 : 1 );

		if ( next < 0 )
		{
			next = DyeColor.values().length - 1;
		}

		if ( next >= DyeColor.values().length )
		{
			next = 0;
		}

		final DyeColor col = DyeColor.values()[next];
		setTapeColor( stack, col );

		final PacketSetColor setColor = new PacketSetColor(col, ChiselToolType.TAPEMEASURE, ChiselsAndBits.getConfig().getClient().chatModeNotification.get());

		ChiselsAndBits.getNetworkChannel().sendToServer(setColor);
		ReflectionWrapper.instance.clearHighlightedStack();
	}

	public void setTapeColor(
			final ItemStack stack,
			final DyeColor color )
	{
		stack.setTagInfo( "color", StringNBT.valueOf(color.getString()) );
	}

}
