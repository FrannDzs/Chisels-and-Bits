package mod.chiselsandbits.core;

import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import mod.chiselsandbits.api.APIExceptions.CannotBeChiseled;
import mod.chiselsandbits.api.*;
import mod.chiselsandbits.bitstorage.TileEntitySpecialRenderBitStorage;
import mod.chiselsandbits.chiseledblock.*;
import mod.chiselsandbits.chiseledblock.data.*;
import mod.chiselsandbits.chiseledblock.iterators.ChiselIterator;
import mod.chiselsandbits.chiseledblock.iterators.ChiselTypeIterator;
import mod.chiselsandbits.client.*;
import mod.chiselsandbits.client.gui.ChiselsAndBitsMenu;
import mod.chiselsandbits.client.gui.SpriteIconPositioning;
import mod.chiselsandbits.helpers.*;
import mod.chiselsandbits.interfaces.IItemScrollWheel;
import mod.chiselsandbits.interfaces.IPatternItem;
import mod.chiselsandbits.items.ItemChisel;
import mod.chiselsandbits.items.ItemChiseledBit;
import mod.chiselsandbits.modes.ChiselMode;
import mod.chiselsandbits.modes.IToolMode;
import mod.chiselsandbits.modes.PositivePatternMode;
import mod.chiselsandbits.modes.TapeMeasureModes;
import mod.chiselsandbits.network.packets.PacketChisel;
import mod.chiselsandbits.network.packets.PacketRotateVoxelBlob;
import mod.chiselsandbits.network.packets.PacketSetColor;
import mod.chiselsandbits.network.packets.PacketSuppressInteraction;
import mod.chiselsandbits.registry.ModBlocks;
import mod.chiselsandbits.registry.ModItems;
import mod.chiselsandbits.registry.ModTileEntityTypes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.IngameGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.particle.DiggingParticle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.math.*;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.DrawHighlightEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ClientSide
{

    private static final Random     RANDOM   = new Random();
    public static final  ClientSide instance = new ClientSide();

    ReadyState readyState = ReadyState.PENDING_PRE;

    private final HashMap<IToolMode, SpriteIconPositioning> chiselModeIcons = new HashMap<>();
    private       KeyBinding                                rotateCCW;
    private       KeyBinding                                rotateCW;
    private       KeyBinding                                undo;
    private       KeyBinding                                redo;
    private       KeyBinding                                modeMenu;
    private       KeyBinding                                addToClipboard;
    private       KeyBinding                                pickBit;
    private       KeyBinding                                offgridPlacement;
    private       Stopwatch                                 rotateTimer;

    final public TapeMeasures tapeMeasures = new TapeMeasures();

    public KeyBinding getKeyBinding(
      ModKeyBinding modKeyBinding)
    {
        switch (modKeyBinding)
        {
            case ROTATE_CCW:
                return rotateCCW;
            case ROTATE_CW:
                return rotateCW;
            case UNDO:
                return undo;
            case REDO:
                return redo;
            case ADD_TO_CLIPBOARD:
                return addToClipboard;
            case PICK_BIT:
                return pickBit;
            case OFFGRID_PLACEMENT:
                return ClientSide.getOffGridPlacementKey();
            default:
                return modeMenu;
        }
    }

    public void preinit(
      final ChiselsAndBits mod)
    {
        readyState = readyState.updateState(ReadyState.TRIGGER_PRE);

        MinecraftForge.EVENT_BUS.register(instance);

    }

    public void init(
      final ChiselsAndBits chiselsandbits)
    {
        readyState = readyState.updateState(ReadyState.TRIGGER_INIT);

        ClientRegistry.bindTileEntityRenderer(ModTileEntityTypes.BIT_STORAGE.get(), TileEntitySpecialRenderBitStorage::new);
        RenderTypeLookup.setRenderLayer(ModBlocks.BIT_STORAGE_BLOCK.get(), RenderType.getCutoutMipped());

        ModBlocks.getMaterialToBlockConversions().values()
            .stream().map(RegistryObject::get)
            .forEach(b -> RenderTypeLookup.setRenderLayer(b, (Predicate<RenderType>) input -> RenderType.getBlockRenderTypes().contains(input)));

        for (final ChiselMode mode : ChiselMode.values())
        {
            mode.binding = registerKeybind(mode.string.toString(), InputMappings.INPUT_INVALID, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_CHISEL);
        }

        for (final PositivePatternMode mode : PositivePatternMode.values())
        {
            mode.binding = registerKeybind(mode.string.toString(), InputMappings.INPUT_INVALID, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_POSTIVEPATTERN);
        }

        for (final TapeMeasureModes mode : TapeMeasureModes.values())
        {
            mode.binding = registerKeybind(mode.string.toString(), InputMappings.INPUT_INVALID, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_TAPEMEASURE);
        }

        modeMenu = registerKeybind("mod.chiselsandbits.other.mode", InputMappings.INPUT_INVALID, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_MENUITEM);
        rotateCCW = registerKeybind("mod.chiselsandbits.other.rotate.ccw", InputMappings.INPUT_INVALID, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_ROTATEABLE);
        rotateCW = registerKeybind("mod.chiselsandbits.other.rotate.cw", InputMappings.INPUT_INVALID, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_ROTATEABLE);
        pickBit = registerKeybind("mod.chiselsandbits.other.pickbit", InputMappings.INPUT_INVALID, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_ROTATEABLE);
        offgridPlacement = registerKeybind("mod.chiselsandbits.other.offgrid", InputMappings.INPUT_INVALID, "itemGroup.chiselsandbits", ModConflictContext.HOLDING_OFFGRID);
        undo = registerKeybind("mod.chiselsandbits.other.undo", InputMappings.INPUT_INVALID, "itemGroup.chiselsandbits", KeyConflictContext.IN_GAME);
        redo = registerKeybind("mod.chiselsandbits.other.redo", InputMappings.INPUT_INVALID, "itemGroup.chiselsandbits", KeyConflictContext.IN_GAME);
        addToClipboard = registerKeybind("mod.chiselsandbits.other.add_to_clipboard", InputMappings.INPUT_INVALID, "itemGroup.chiselsandbits", KeyConflictContext.IN_GAME);
    }

    private KeyBinding registerKeybind(
      final String bindingName,
      final InputMappings.Input defaultKey,
      final String groupName,
      final IKeyConflictContext context)
    {
        final KeyBinding kb = new KeyBinding(bindingName, context, defaultKey, groupName);
        ClientRegistry.registerKeyBinding(kb);
        return kb;
    }

    public void postinit(
      final ChiselsAndBits mod)
    {
        readyState = readyState.updateState(ReadyState.TRIGGER_POST);

        Minecraft.getInstance().getItemColors().register(new ItemColorBitBag(), ModItems.ITEM_BIT_BAG_DEFAULT.get());
        Minecraft.getInstance().getItemColors().register(new ItemColorBitBag(), ModItems.ITEM_BIT_BAG_DYED.get());
        Minecraft.getInstance().getItemColors().register(new ItemColorBits(), ModItems.ITEM_BLOCK_BIT.get());
        Minecraft.getInstance().getItemColors().register(new ItemColorPatterns(), ModItems.ITEM_POSITIVE_PRINT.get());
        Minecraft.getInstance().getItemColors().register(new ItemColorPatterns(), ModItems.ITEM_POSITIVE_PRINT_WRITTEN.get());
        Minecraft.getInstance().getItemColors().register(new ItemColorPatterns(), ModItems.ITEM_NEGATIVE_PRINT.get());
        Minecraft.getInstance().getItemColors().register(new ItemColorPatterns(), ModItems.ITEM_NEGATIVE_PRINT_WRITTEN.get());
        Minecraft.getInstance().getItemColors().register(new ItemColorPatterns(), ModItems.ITEM_MIRROR_PRINT.get());
        Minecraft.getInstance().getItemColors().register(new ItemColorPatterns(), ModItems.ITEM_MIRROR_PRINT_WRITTEN.get());

        Minecraft.getInstance()
          .getBlockColors()
          .register(new BlockColorChisled(), ModBlocks.getMaterialToBlockConversions().values().stream().map(RegistryObject::get).toArray(Block[]::new));
        Minecraft.getInstance()
          .getItemColors()
          .register(new ItemColorChisled(), ModBlocks.getMaterialToItemConversions().values().stream().map(RegistryObject::get).toArray(Item[]::new));
    }

    public static TextureAtlasSprite undoIcon;
    public static TextureAtlasSprite redoIcon;
    public static TextureAtlasSprite trashIcon;

    public static TextureAtlasSprite sortIcon;

    public static TextureAtlasSprite swapIcon;
    public static TextureAtlasSprite placeIcon;

    public static TextureAtlasSprite roll_x;
    public static TextureAtlasSprite roll_z;
    public static TextureAtlasSprite white;

    public SpriteIconPositioning getIconForMode(
      final IToolMode mode)
    {
        return chiselModeIcons.get(mode);
    }

    public void setIconForMode(
      final IToolMode mode,
      final SpriteIconPositioning positioning
    )
    {
        chiselModeIcons.put(mode, positioning);
    }

    @SubscribeEvent
    public void onRenderGUI(
      final RenderGameOverlayEvent.Post event)
    {
        final ChiselToolType tool = getHeldToolType(lastHand);
        final ElementType type = event.getType();
        if (type == ElementType.ALL && tool != null && tool.hasMenu())
        {
            final boolean wasVisible = ChiselsAndBitsMenu.instance.isVisible();

            if (!modeMenu.isInvalid() && modeMenu.isKeyDown())
            {
                ChiselsAndBitsMenu.instance.actionUsed = false;
                if (ChiselsAndBitsMenu.instance.raiseVisibility())
                    ChiselsAndBitsMenu.instance.getMinecraft().mouseHelper.ungrabMouse();
            }
            else
            {
                if (!ChiselsAndBitsMenu.instance.actionUsed)
                {
                    if (ChiselsAndBitsMenu.instance.switchTo != null)
                    {
                        ClientSide.instance.playRadialMenu();
                        ChiselModeManager.changeChiselMode(tool, ChiselModeManager.getChiselMode(getPlayer(), tool, Hand.MAIN_HAND), ChiselsAndBitsMenu.instance.switchTo);
                    }

                    if (ChiselsAndBitsMenu.instance.doAction != null)
                    {
                        ClientSide.instance.playRadialMenu();
                        switch (ChiselsAndBitsMenu.instance.doAction)
                        {
                            case ROLL_X:
                                PacketRotateVoxelBlob pri = new PacketRotateVoxelBlob(Axis.X, Rotation.CLOCKWISE_90);
                                ChiselsAndBits.getNetworkChannel().sendToServer(pri);
                                break;

                            case ROLL_Z:
                                PacketRotateVoxelBlob pri2 = new PacketRotateVoxelBlob(Axis.Z, Rotation.CLOCKWISE_90);
                                ChiselsAndBits.getNetworkChannel().sendToServer(pri2);
                                break;

                            case REPLACE_TOGGLE:
                                ReplacementStateHandler.getInstance().setReplacing(!ReplacementStateHandler.getInstance().isReplacing());
                                ReflectionWrapper.instance.clearHighlightedStack();
                                break;

                            case UNDO:
                                UndoTracker.getInstance().undo();
                                break;

                            case REDO:
                                UndoTracker.getInstance().redo();
                                break;

                            case BLACK:
                            case BLUE:
                            case BROWN:
                            case CYAN:
                            case GRAY:
                            case GREEN:
                            case LIGHT_BLUE:
                            case LIME:
                            case MAGENTA:
                            case ORANGE:
                            case PINK:
                            case PURPLE:
                            case RED:
                            case LIGHT_GRAY:
                            case WHITE:
                            case YELLOW:

                                final PacketSetColor setColor = new PacketSetColor(DyeColor.valueOf(ChiselsAndBitsMenu.instance.doAction.name()),
                                  getHeldToolType(Hand.MAIN_HAND),
                                  ChiselsAndBits.getConfig().getClient().chatModeNotification.get());
                                ChiselsAndBits.getNetworkChannel().sendToServer(setColor);
                                ReflectionWrapper.instance.clearHighlightedStack();

                                break;
                        }
                    }
                }

                ChiselsAndBitsMenu.instance.actionUsed = true;
                ChiselsAndBitsMenu.instance.decreaseVisibility();
            }

            if (ChiselsAndBitsMenu.instance.isVisible())
            {
                final MainWindow window = event.getWindow();
                ChiselsAndBitsMenu.instance.init(Minecraft.getInstance(), window.getScaledWidth(), window.getScaledHeight());
                ChiselsAndBitsMenu.instance.configure(window.getScaledWidth(), window.getScaledHeight());

                if (!wasVisible)
                {
                    ChiselsAndBitsMenu.instance.getMinecraft().currentScreen = ChiselsAndBitsMenu.instance;
                    ChiselsAndBitsMenu.instance.getMinecraft().mouseHelper.ungrabMouse();
                }

                if (ChiselsAndBitsMenu.instance.getMinecraft().mouseHelper.isMouseGrabbed())
                {
                    KeyBinding.unPressAllKeys();
                }
/*
                final int k1 = (int) (Minecraft.getInstance().mouseHelper.getMouseX() * window.getScaledWidth() / window.getWidth());
                final int l1 = (int) (window.getScaledHeight() - Minecraft.getInstance().mouseHelper.getMouseY() * window.getScaledHeight() / window.getHeight() - 1);

                net.minecraftforge.client.ForgeHooksClient.drawScreen(ChiselsAndBitsMenu.instance, event.getMatrixStack(), k1, l1, event.getPartialTicks());*/
            }
            else
            {
                if (wasVisible)
                {
                    ChiselsAndBitsMenu.instance.getMinecraft().mouseHelper.grabMouse();
                }
            }
        }

        if (!undo.isInvalid() && undo.isPressed())
        {
            UndoTracker.getInstance().undo();
        }

        if (!redo.isInvalid() && redo.isPressed())
        {
            UndoTracker.getInstance().redo();
        }

        if (!addToClipboard.isInvalid() && addToClipboard.isPressed())
        {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.objectMouseOver != null && mc.objectMouseOver.getType() == RayTraceResult.Type.BLOCK && mc.objectMouseOver instanceof BlockRayTraceResult)
            {
                final BlockRayTraceResult rayTraceResult = (BlockRayTraceResult) mc.objectMouseOver;

                try
                {
                    final IBitAccess access = ChiselsAndBits.getApi().getBitAccess(mc.world, rayTraceResult.getPos());
                    final ItemStack is = access.getBitsAsItem(null, ItemType.CHISLED_BLOCK, false);

                    CreativeClipboardTab.addItem(is);
                }
                catch (final CannotBeChiseled e)
                {
                    // nope.
                }
            }
        }

        if (!pickBit.isInvalid() && pickBit.isPressed())
        {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.objectMouseOver != null && mc.objectMouseOver.getType() == RayTraceResult.Type.BLOCK && mc.objectMouseOver instanceof BlockRayTraceResult)
            {
                BlockRayTraceResult rayTraceResult = (BlockRayTraceResult) mc.objectMouseOver;

                try
                {
                    final BitLocation bl = new BitLocation(rayTraceResult, BitOperation.CHISEL, false);
                    final IBitAccess access = ChiselsAndBits.getApi().getBitAccess(mc.world, bl.getBlockPos());
                    final IBitBrush brush = access.getBitAt(bl.getBitX(), bl.getBitY(), bl.getBitZ());
                    final ItemStack is = brush.getItemStack(1);
                    doPick(is);
                }
                catch (final CannotBeChiseled e)
                {
                    // nope.
                }
            }
        }

        if (type == ElementType.HOTBAR && ChiselsAndBits.getConfig().getClient().enableToolbarIcons.get())
        {
            final Minecraft mc = Minecraft.getInstance();
            final MainWindow window = event.getWindow();

            if (!mc.player.isSpectator())
            {
                final IngameGui sc = mc.ingameGUI;

                for (int slot = 0; slot < 9; ++slot)
                {
                    final ItemStack stack = mc.player.inventory.mainInventory.get(slot);
                    if (stack.getItem() instanceof ItemChisel)
                    {
                        final ChiselToolType toolType = getToolTypeForItem(stack);
                        IToolMode mode = toolType.getMode(stack);

                        if (!ChiselsAndBits.getConfig().getClient().perChiselMode.get() && tool == ChiselToolType.CHISEL)
                        {
                            mode = ChiselModeManager.getChiselMode(mc.player, ChiselToolType.CHISEL, lastHand);
                        }

                        final int x = window.getScaledWidth() / 2 - 90 + slot * 20 + 2;
                        final int y = window.getScaledHeight() - 16 - 3;

                        RenderSystem.color4f(1, 1, 1, 1.0f);
                        Minecraft.getInstance().getTextureManager().bindTexture(PlayerContainer.LOCATION_BLOCKS_TEXTURE);
                        final TextureAtlasSprite sprite = chiselModeIcons.get(mode) == null ? getMissingIcon() : chiselModeIcons.get(mode).sprite;

                        RenderSystem.enableBlend();
                        sc.blit(event.getMatrixStack(), x + 1, y + 1, 0, 8, 8, sprite);
                        RenderSystem.disableBlend();
                    }
                }
            }
        }
    }

    public void playRadialMenu()
    {
        final double volume = ChiselsAndBits.getConfig().getClient().radialMenuVolume.get();
        if (volume >= 0.0001f)
        {
            final ISound psr = new SimpleSound(SoundEvents.UI_BUTTON_CLICK, SoundCategory.MASTER, (float) volume, 1.0f, getPlayer().getPosition());
            Minecraft.getInstance().getSoundHandler().play(psr);
        }
    }

    private boolean doPick(
      final @Nonnull ItemStack result)
    {
        final PlayerEntity player = getPlayer();

        for (int x = 0; x < 9; x++)
        {
            final ItemStack stack = player.inventory.getStackInSlot(x);
            if (stack != null && stack.isItemEqual(result) && ItemStack.areItemStackTagsEqual(stack, result))
            {
                player.inventory.currentItem = x;
                return true;
            }
        }

        if (!player.isCreative())
        {
            return false;
        }

        int slot = player.inventory.getFirstEmptyStack();
        if (slot < 0 || slot >= 9)
        {
            slot = player.inventory.currentItem;
        }

        // update inventory..
        player.inventory.setInventorySlotContents(slot, result);
        player.inventory.currentItem = slot;

        // update server...
        final int j = player.container.inventorySlots.size() - 9 + player.inventory.currentItem;
        Minecraft.getInstance().playerController.sendSlotPacket(player.inventory.getStackInSlot(player.inventory.currentItem), j);
        return true;
    }

    public ChiselToolType getHeldToolType(
      final Hand Hand)
    {
        final PlayerEntity player = getPlayer();

        if (player == null)
        {
            return null;
        }

        final ItemStack is = player.getHeldItem(Hand);
        return getToolTypeForItem(is);
    }

    private ChiselToolType getToolTypeForItem(
      final ItemStack is)
    {
        if (is != null && is.getItem() instanceof ItemChisel)
        {
            return ChiselToolType.CHISEL;
        }

        if (is != null && is.getItem() instanceof ItemChiseledBit)
        {
            return ChiselToolType.BIT;
        }

        if (is != null && is.getItem() instanceof ItemBlockChiseled)
        {
            return ChiselToolType.CHISELED_BLOCK;
        }

        if (is != null && is.getItem() == ModItems.ITEM_TAPE_MEASURE.get())
        {
            return ChiselToolType.TAPEMEASURE;
        }

        if (is != null && is.getItem() == ModItems.ITEM_POSITIVE_PRINT.get())
        {
            return ChiselToolType.POSITIVEPATTERN;
        }

        if (is != null && is.getItem() == ModItems.ITEM_POSITIVE_PRINT_WRITTEN.get())
        {
            return ChiselToolType.POSITIVEPATTERN;
        }

        if (is != null && is.getItem() == ModItems.ITEM_NEGATIVE_PRINT.get())
        {
            return ChiselToolType.NEGATIVEPATTERN;
        }

        if (is != null && is.getItem() == ModItems.ITEM_NEGATIVE_PRINT_WRITTEN.get())
        {
            return ChiselToolType.NEGATIVEPATTERN;
        }

        if (is != null && is.getItem() == ModItems.ITEM_MIRROR_PRINT.get())
        {
            return ChiselToolType.MIRRORPATTERN;
        }

        if (is != null && is.getItem() == ModItems.ITEM_MIRROR_PRINT_WRITTEN.get())
        {
            return ChiselToolType.MIRRORPATTERN;
        }

        return null;
    }

    @SubscribeEvent
    public void drawingInteractionPrevention(
      final RightClickBlock pie)
    {
        if (pie.getWorld() != null && pie.getWorld().isRemote)
        {
            final ChiselToolType tool = getHeldToolType(pie.getHand());
            final IToolMode chMode = ChiselModeManager.getChiselMode(getPlayer(), tool, pie.getHand());

            final BitLocation other = getStartPos();
            if ((chMode == ChiselMode.DRAWN_REGION || tool == ChiselToolType.TAPEMEASURE) && other != null)
            {
                // this handles the client side, but the server side will fire
                // separately.
                pie.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void interaction(
      final TickEvent.ClientTickEvent event)
    {
        if (!readyState.isReady())
        {
            return;
        }

        if (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.inventory.getCurrentItem().isEmpty())
        {
            lastTool = getToolTypeForItem(Objects.requireNonNull(Minecraft.getInstance().player).inventory.getCurrentItem());
        }

        // used to prevent hyper chisels.. its actually far worse then you might
        // think...
        if (event.side == LogicalSide.CLIENT && event.type == TickEvent.Type.CLIENT && event.phase == TickEvent.Phase.START
              && !Minecraft.getInstance().gameSettings.keyBindAttack.isInvalid() && !Minecraft.getInstance().gameSettings.keyBindAttack.isKeyDown())
        {
            ItemChisel.resetDelay();
        }

        if (!getToolKey().isInvalid() && !getToolKey().isKeyDown() && lastTool == ChiselToolType.CHISEL)
        {
            if (ticksSinceRelease >= 4)
            {
                if (drawStart != null)
                {
                    drawStart = null;
                    lastHand = Hand.MAIN_HAND;
                }

                lastTool = ChiselToolType.CHISEL;
                ticksSinceRelease = 0;
            }
            else
            {
                ticksSinceRelease++;
            }
        }
        else
        {
            ticksSinceRelease = 0;
        }

        if (!rotateCCW.isInvalid() && rotateCCW.isKeyDown())
        {
            if (rotateTimer == null || rotateTimer.elapsed(TimeUnit.MILLISECONDS) > 200)
            {
                rotateTimer = Stopwatch.createStarted();
                final PacketRotateVoxelBlob p = new PacketRotateVoxelBlob(Axis.Y, Rotation.COUNTERCLOCKWISE_90);
                ChiselsAndBits.getNetworkChannel().sendToServer(p);
            }
        }

        if (!rotateCW.isInvalid() && rotateCW.isKeyDown())
        {
            if (rotateTimer == null || rotateTimer.elapsed(TimeUnit.MILLISECONDS) > 200)
            {
                rotateTimer = Stopwatch.createStarted();
                final PacketRotateVoxelBlob p = new PacketRotateVoxelBlob(Axis.Y, Rotation.CLOCKWISE_90);
                ChiselsAndBits.getNetworkChannel().sendToServer(p);
            }
        }

        for (final ChiselMode mode : ChiselMode.values())
        {
            final KeyBinding kb = (KeyBinding) mode.binding;
            if (!kb.isInvalid() && kb.isKeyDown())
            {
                final ChiselToolType tool = getHeldToolType(lastHand);
                if (tool.isBitOrChisel())
                {
                    ChiselModeManager.changeChiselMode(tool, ChiselModeManager.getChiselMode(getPlayer(), tool, lastHand), mode);
                }
            }
        }

        for (final PositivePatternMode mode : PositivePatternMode.values())
        {
            final KeyBinding kb = (KeyBinding) mode.binding;
            if (!kb.isInvalid() && kb.isKeyDown())
            {
                final ChiselToolType tool = getHeldToolType(lastHand);
                if (tool == ChiselToolType.POSITIVEPATTERN)
                {
                    ChiselModeManager.changeChiselMode(tool, ChiselModeManager.getChiselMode(getPlayer(), tool, lastHand), mode);
                }
            }
        }

        for (final TapeMeasureModes mode : TapeMeasureModes.values())
        {
            final KeyBinding kb = (KeyBinding) mode.binding;
            if (!kb.isInvalid() && kb.isKeyDown())
            {
                final ChiselToolType tool = getHeldToolType(lastHand);
                if (tool == ChiselToolType.TAPEMEASURE)
                {
                    ChiselModeManager.changeChiselMode(tool, ChiselModeManager.getChiselMode(getPlayer(), tool, lastHand), mode);
                }
            }
        }
    }

    boolean wasDrawing = false;

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void drawHighlight(
      final RenderWorldLastEvent event)
    {
        final MatrixStack stack = event.getMatrixStack();
        stack.push();

        Vector3d renderView = Minecraft.getInstance().gameRenderer.getActiveRenderInfo().getProjectedView();
        stack.translate(-renderView.x, -renderView.y, -renderView.z);

        ChiselToolType tool = getHeldToolType(lastHand);
        final IToolMode chMode = ChiselModeManager.getChiselMode(getPlayer(), tool, lastHand);
        if (chMode == ChiselMode.DRAWN_REGION)
        {
            tool = lastTool;
        }

        tapeMeasures.setPreviewMeasure(null, null, chMode, null);

        if (tool == ChiselToolType.TAPEMEASURE)
        {
            final PlayerEntity player = getPlayer();
            final RayTraceResult mop = Minecraft.getInstance().objectMouseOver;
            ;
            final World theWorld = player.world;

            if (mop != null && mop.getType() == RayTraceResult.Type.BLOCK)
            {
                final BlockRayTraceResult blockRayTraceResult = (BlockRayTraceResult) mop;
                final BitLocation location = new BitLocation(blockRayTraceResult, BitOperation.CHISEL, false);
                if (theWorld.getWorldBorder().contains(location.blockPos))
                {
                    final BitLocation other = getStartPos();
                    if (other != null)
                    {
                        tapeMeasures.setPreviewMeasure(other, location, chMode, getPlayer().getHeldItem(lastHand));

                        if (!getToolKey().isInvalid() && !getToolKey().isKeyDown())
                        {
                            tapeMeasures.addMeasure(other, location, chMode, getPlayer().getHeldItem(lastHand));
                            drawStart = null;
                            lastHand = Hand.MAIN_HAND;
                        }
                    }
                }
            }
        }

        tapeMeasures.render(stack, event.getPartialTicks());

        final boolean isDrawing = (chMode == ChiselMode.DRAWN_REGION || tool == ChiselToolType.TAPEMEASURE) && getStartPos() != null;
        if (isDrawing != wasDrawing)
        {
            wasDrawing = isDrawing;
            final PacketSuppressInteraction packet = new PacketSuppressInteraction(isDrawing);
            ChiselsAndBits.getNetworkChannel().sendToServer(packet);
        }

        stack.pop();
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void drawHighlight(
      final DrawHighlightEvent.HighlightBlock event)
    {
        try {
            final MatrixStack stack = event.getMatrix();
            stack.push();
            Vector3d renderView = Minecraft.getInstance().gameRenderer.getActiveRenderInfo().getProjectedView();
            stack.translate(-renderView.x, -renderView.y, -renderView.z);

            ChiselToolType tool = getHeldToolType(lastHand);
            final IToolMode chMode = ChiselModeManager.getChiselMode(getPlayer(), tool, lastHand);
            if (chMode == ChiselMode.DRAWN_REGION)
            {
                tool = lastTool;
            }

            if (tool != null && tool.isBitOrChisel() && chMode != null)
            {
                final PlayerEntity player = Minecraft.getInstance().player;
                final float partialTicks = event.getPartialTicks();
                final RayTraceResult mop = Minecraft.getInstance().objectMouseOver;
                final World theWorld = player.world;

                if (mop == null || mop.getType() != RayTraceResult.Type.BLOCK)
                {
                    return;
                }

                boolean showBox = false;
                if (mop.getType() == RayTraceResult.Type.BLOCK)
                {
                    final BlockRayTraceResult rayTraceResult = (BlockRayTraceResult) mop;
                    final BitLocation location = new BitLocation(rayTraceResult, getLastBitOperation(player, lastHand, getPlayer().getHeldItem(lastHand)), false);
                    if (theWorld.getWorldBorder().contains(location.blockPos))
                    {
                        // this logic originated in the vanilla bounding box...
                        final BlockState state = theWorld.getBlockState(location.blockPos);

                        final boolean isChisel = getDrawnTool() == ChiselToolType.CHISEL;
                        final boolean isBit = getHeldToolType(Hand.MAIN_HAND) == ChiselToolType.BIT;
                        final TileEntityBlockChiseled data = ModUtil.getChiseledTileEntity(theWorld, location.blockPos, false);

                        final VoxelRegionSrc region = new VoxelRegionSrc(theWorld, location.blockPos, 1);
                        final VoxelBlob vb = data != null ? data.getBlob() : new VoxelBlob();

                        if (isChisel && data == null)
                        {
                            showBox = true;
                            vb.fill(1);
                        }

                        final BitLocation other = getStartPos();
                        if (chMode == ChiselMode.DRAWN_REGION && other != null)
                        {
                            final ChiselIterator oneEnd = ChiselTypeIterator.create(VoxelBlob.dim,
                              location.bitX,
                              location.bitY,
                              location.bitZ,
                              VoxelBlob.NULL_BLOB,
                              ChiselMode.SINGLE,
                              Direction.UP,
                              tool == ChiselToolType.BIT);
                            final ChiselIterator otherEnd = ChiselTypeIterator.create(VoxelBlob.dim,
                              other.bitX,
                              other.bitY,
                              other.bitZ,
                              VoxelBlob.NULL_BLOB,
                              ChiselMode.SINGLE,
                              Direction.UP,
                              tool == ChiselToolType.BIT);

                            final AxisAlignedBB a =
                              oneEnd.getBoundingBox(VoxelBlob.NULL_BLOB, false).offset(location.blockPos.getX(), location.blockPos.getY(), location.blockPos.getZ());
                            final AxisAlignedBB b = otherEnd.getBoundingBox(VoxelBlob.NULL_BLOB, false).offset(other.blockPos.getX(), other.blockPos.getY(), other.blockPos.getZ());

                            final AxisAlignedBB bb = a.union(b);

                            final double maxChiseSize = ChiselsAndBits.getConfig().getClient().maxDrawnRegionSize.get() + 0.001;
                            if (bb.maxX - bb.minX <= maxChiseSize && bb.maxY - bb.minY <= maxChiseSize && bb.maxZ - bb.minZ <= maxChiseSize)
                            {
                                RenderHelper.drawSelectionBoundingBoxIfExists(event.getMatrix(), bb, BlockPos.ZERO, player, partialTicks, false);

                                if (!getToolKey().isInvalid() && !getToolKey().isKeyDown())
                                {
                                    final PacketChisel pc = new PacketChisel(getLastBitOperation(player, lastHand, player.getHeldItem(lastHand)), location, other,
                                      Direction.UP,
                                      ChiselMode.DRAWN_REGION, lastHand);

                                    if (pc.doAction(getPlayer()) > 0)
                                    {
                                        ChiselsAndBits.getNetworkChannel().sendToServer(pc);
                                        ClientSide.placeSound(theWorld, location.blockPos, 0);
                                    }

                                    drawStart = null;
                                    lastHand = Hand.MAIN_HAND;
                                    lastTool = ChiselToolType.CHISEL;
                                }
                            }
                        }
                        else
                        {
                            final TileEntity te = theWorld.getChunkAt(location.blockPos).getTileEntity(location.blockPos, Chunk.CreateEntityType.CHECK);
                            boolean isBitBlock = te instanceof TileEntityBlockChiseled;
                            final boolean isBlockSupported = BlockBitInfo.canChisel(state);

                            if (!(isBitBlock || isBlockSupported))
                            {
                                final TileEntityBlockChiseled tebc = ModUtil.getChiseledTileEntity(theWorld, location.blockPos, false);
                                if (tebc != null)
                                {
                                    final VoxelBlob vx = tebc.getBlob();
                                    if (vx.get(location.bitX, location.bitY, location.bitZ) != 0)
                                    {
                                        isBitBlock = true;
                                    }
                                }
                            }

                            if (theWorld.isAirBlock(location.blockPos) || isBitBlock || isBlockSupported)
                            {
                                final ChiselIterator i = ChiselTypeIterator.create(VoxelBlob.dim,
                                  location.bitX,
                                  location.bitY,
                                  location.bitZ,
                                  region,
                                  ChiselMode.castMode(chMode),
                                  rayTraceResult.getFace(),
                                  !isChisel);
                                final AxisAlignedBB bb = i.getBoundingBox(vb, isChisel);
                                RenderHelper.drawSelectionBoundingBoxIfExists(event.getMatrix(), bb, location.blockPos, player, partialTicks, false);
                                showBox = false;
                            }
                            else if (isBit)
                            {
                                final VoxelBlob j = new VoxelBlob();
                                j.fill(1);
                                final ChiselIterator i = ChiselTypeIterator.create(VoxelBlob.dim,
                                  location.bitX,
                                  location.bitY,
                                  location.bitZ,
                                  j,
                                  ChiselMode.castMode(chMode),
                                  rayTraceResult.getFace(),
                                  !isChisel);
                                final AxisAlignedBB bb = snapToSide(i.getBoundingBox(j, isChisel), rayTraceResult.getFace());
                                RenderHelper.drawSelectionBoundingBoxIfExists(event.getMatrix(), bb, location.blockPos, player, partialTicks, false);
                            }
                        }
                    }

                    if (!showBox)
                    {
                        event.setCanceled(true);
                    }
                }
            }

        } finally {
            event.getMatrix().pop();
        }


    }

    private BitOperation getLastBitOperation(
      final PlayerEntity player,
      final Hand lastHand2,
      final ItemStack heldItem)
    {
        return lastTool == ChiselToolType.BIT ? ItemChiseledBit.getBitOperation(player, lastHand, player.getHeldItem(lastHand)) : BitOperation.CHISEL;
    }

    private AxisAlignedBB snapToSide(
      final AxisAlignedBB boundingBox,
      final Direction sideHit)
    {
        if (boundingBox != null)
        {
            switch (sideHit)
            {
                case DOWN:
                    return new AxisAlignedBB(boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
                case EAST:
                    return new AxisAlignedBB(boundingBox.maxX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
                case NORTH:
                    return new AxisAlignedBB(boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
                case SOUTH:
                    return new AxisAlignedBB(boundingBox.minX, boundingBox.minY, boundingBox.maxZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
                case UP:
                    return new AxisAlignedBB(boundingBox.minX, boundingBox.maxY, boundingBox.minZ, boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
                case WEST:
                    return new AxisAlignedBB(boundingBox.minX, boundingBox.minY, boundingBox.minZ, boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
                default:
                    break;
            }
        }

        return boundingBox;
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void drawLast(
      final RenderWorldLastEvent event)
    {
        // important and used for tesr / block rendering.
        ++lastRenderedFrame;

        if (Minecraft.getInstance().gameSettings.hideGUI)
        {
            return;
        }

        // now render the ghosts...
        final PlayerEntity player = Minecraft.getInstance().player;
        final float partialTicks = event.getPartialTicks();
        final RayTraceResult mop = Minecraft.getInstance().objectMouseOver;
        final World theWorld = player.world;
        final ItemStack currentItem = player.getHeldItemMainhand();

        final double x = player.lastTickPosX + (player.getPosX() - player.lastTickPosX) * partialTicks;
        final double y = player.lastTickPosY + (player.getPosY() - player.lastTickPosY) * partialTicks;
        final double z = player.lastTickPosZ + (player.getPosZ() - player.lastTickPosZ) * partialTicks;

        if (mop == null)
        {
            return;
        }

        if (ModUtil.isHoldingPattern(player))
        {
            if (mop.getType() != RayTraceResult.Type.BLOCK)
            {
                return;
            }

            final BlockRayTraceResult rayTraceResult = (BlockRayTraceResult) mop;
            final IToolMode mode = ChiselModeManager.getChiselMode(player, ChiselToolType.POSITIVEPATTERN, Hand.MAIN_HAND);

            final BlockPos pos = rayTraceResult.getPos();
            final BlockPos partial = null;

            final BlockState s = theWorld.getBlockState(pos);
            if (!(s.getBlock() instanceof BlockChiseled) && !BlockBitInfo.canChisel(s))
            {
                return;
            }

            if (!ModItems.ITEM_NEGATIVE_PRINT.get().isWritten(currentItem))
            {
                return;
            }

            final ItemStack item = ModItems.ITEM_NEGATIVE_PRINT.get().getPatternedItem(currentItem, false);
            if (item == null || !item.hasTag())
            {
                return;
            }

            final int rotations = ModUtil.getRotations(player, ModUtil.getSide(currentItem));

            if (mode == PositivePatternMode.PLACEMENT)
            {
                doGhostForChiseledBlock(event.getMatrixStack(), x, y, z, theWorld, player, (BlockRayTraceResult) mop, item, item, rotations);
                return;
            }

            if (item != null && !item.isEmpty())
            {
                final TileEntityBlockChiseled tebc = ModUtil.getChiseledTileEntity(theWorld, pos, false);
                Object cacheRef = tebc != null ? tebc : s;
                if (cacheRef instanceof TileEntityBlockChiseled)
                {
                    cacheRef = ((TileEntityBlockChiseled) cacheRef).getBlobStateReference();
                }

                RenderSystem.depthFunc(GL11.GL_ALWAYS);
                showGhost(event.getMatrixStack(), currentItem, item, rayTraceResult.getPos(), player, rotations, x, y, z, rayTraceResult.getFace(), partial, cacheRef);
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            }
        }
        else if (ModUtil.isHoldingChiseledBlock(player))
        {
            if (mop.getType() != RayTraceResult.Type.BLOCK)
            {
                return;
            }

            final ItemStack item = currentItem;
            if (!item.hasTag())
            {
                return;
            }

            final int rotations = ModUtil.getRotations(player, ModUtil.getSide(item));
            doGhostForChiseledBlock(event.getMatrixStack(), x, y, z, theWorld, player, (BlockRayTraceResult) mop, currentItem, item, rotations);
        }
    }

    private void doGhostForChiseledBlock(
      final MatrixStack matrixStack,
      final double x,
      final double y,
      final double z,
      final World theWorld,
      final PlayerEntity player,
      final BlockRayTraceResult mop,
      final ItemStack currentItem,
      final ItemStack item,
      final int rotations)
    {
        final BlockPos offset = mop.getPos();

        if (ClientSide.offGridPlacement(player))
        {
            final BitLocation bl = new BitLocation(mop, BitOperation.PLACE, false); //The placement offset here needs to be used due to host location handling.
            showGhost(matrixStack, currentItem, item, bl.blockPos, player, rotations, x, y, z, mop.getFace(), new BlockPos(bl.bitX, bl.bitY, bl.bitZ), null);
        }
        else
        {
            boolean canMerge = false;
            if (currentItem.hasTag())
            {
                final TileEntityBlockChiseled tebc = ModUtil.getChiseledTileEntity(theWorld, offset, true);

                if (tebc != null)
                {
                    final VoxelBlob blob = ModUtil.getBlobFromStack(currentItem, player);
                    canMerge = tebc.canMerge(blob);
                }
            }

            BlockPos newOffset = offset;
            final Block block = theWorld.getBlockState(newOffset).getBlock();
            final Hand hand = player.getActiveHand() != null ? player.getActiveHand() : Hand.MAIN_HAND;
            if (!canMerge && !ClientSide.offGridPlacement(player) && !block.isReplaceable(theWorld.getBlockState(newOffset),
              new BlockItemUseContext(player, hand, player.getHeldItem(hand), mop)))
            {
                newOffset = offset.offset(mop.getFace());
            }

            final TileEntity newTarget = theWorld.getTileEntity(newOffset);

            if (theWorld.isAirBlock(newOffset) || newTarget instanceof TileEntityBlockChiseled || (theWorld.getTileEntity(newOffset) instanceof TileEntityBlockChiseled && theWorld.getBlockState(newOffset)
                                                                                                                                .getBlock()
                                                                                                                                .isReplaceable(theWorld.getBlockState(newOffset),
                                                                                                                                  new BlockItemUseContext(player, hand,  player.getHeldItem(hand), new BlockRayTraceResult(
                                                                                                                                    mop.getHitVec().add(mop.getFace().getXOffset(), mop.getFace().getYOffset(), mop.getFace().getZOffset()),
                                                                                                                                    mop.getFace(),
                                                                                                                                    mop.getPos().add(mop.getFace().getDirectionVec()),
                                                                                                                                    mop.isInside()
                                                                                                                                  )))) || ( !(theWorld.getTileEntity(newOffset) instanceof TileEntityBlockChiseled) && theWorld.getBlockState(newOffset)
                                                    .getBlock()
                                                    .isReplaceable(theWorld.getBlockState(newOffset),
                                                      new BlockItemUseContext(player, hand,  player.getHeldItem(hand), mop)))

            )
            {
                final TileEntityBlockChiseled test = ModUtil.getChiseledTileEntity(theWorld, newOffset, false);
                showGhost(matrixStack, currentItem, item, newOffset, player, rotations, x, y, z, mop.getFace(), null, test == null ? null : test.getBlobStateReference());
            }
        }
    }

    private ItemStack  previousItem;
    private int        previousRotations;
    private Object     previousModel;
    private Object     previousCacheRef;
    private IntegerBox modelBounds;
    private boolean                                  isVisible       = true;
    private ItemBlockChiseled.PlacementAttemptResult placementResult = ItemBlockChiseled.PlacementAttemptResult.PLACEABLE;
    private BlockPos                                 lastPartial;
    private BlockPos   lastPos;
    int displayStatus = 0;

    private void showGhost(
      final MatrixStack matrixStack,
      final ItemStack refItem,
      final ItemStack item,
      final BlockPos blockPos,
      final PlayerEntity player,
      final int rotationCount,
      final double x,
      final double y,
      final double z,
      final Direction side,
      final BlockPos partial,
      final Object cacheRef)
    {
        IBakedModel baked = null;

        if (previousCacheRef == cacheRef && samePos(lastPos, blockPos) && previousItem == refItem && previousRotations == rotationCount && previousModel != null && samePos(
          lastPartial,
          partial))
        {
            baked = (IBakedModel) previousModel;
        }
        else
        {
            int rotations = rotationCount;

            previousItem = refItem;
            previousRotations = rotations;
            previousCacheRef = cacheRef;
            lastPos = blockPos;
            lastPartial = partial;

            final NBTBlobConverter c = new NBTBlobConverter();
            c.readChisleData(ModUtil.getSubCompound(item, ModUtil.NBT_BLOCKENTITYTAG, false), VoxelBlob.VERSION_ANY);
            VoxelBlob blob = c.getBlob();

            while (rotations-- > 0)
            {
                blob = blob.spin(Axis.Y);
            }

            modelBounds = blob.getBounds();

            fail:
            if (refItem.getItem() == ModItems.ITEM_NEGATIVE_PRINT.get())
            {
                final VoxelBlob pattern = blob;

                if (cacheRef instanceof VoxelBlobStateReference)
                {
                    blob = ((VoxelBlobStateReference) cacheRef).getVoxelBlob();
                }
                else if (cacheRef instanceof BlockState)
                {
                    blob = new VoxelBlob();
                    blob.fill(ModUtil.getStateId((BlockState) cacheRef));
                }
                else
                {
                    break fail;
                }

                final BitIterator it = new BitIterator();
                while (it.hasNext())
                {
                    if (it.getNext(pattern) == 0)
                    {
                        it.setNext(blob, 0);
                    }
                }
            }

            c.setBlob(blob);

            final Block blk = Block.getBlockFromItem(item.getItem());
            final ItemStack is = c.getItemStack(false);

            if (is == null || is.isEmpty())
            {
                isVisible = false;
            }
            else
            {
                baked = Minecraft.getInstance().getItemRenderer().getItemModelMesher().getItemModel(is);
                previousModel = baked = baked.getOverrides().getOverrideModel(baked, is, (ClientWorld) player.getEntityWorld(), player);

                if (refItem.getItem() instanceof IPatternItem)
                {
                    isVisible = true;
                }
                else
                {
                    isVisible = true;
                    //TODO: Figure out the hitvector here. Might need to pass that down stream.
                    placementResult = ItemBlockChiseled.tryPlaceBlockAt(blk, item, player, player.getEntityWorld(), blockPos, side, Hand.MAIN_HAND, Minecraft.getInstance().objectMouseOver.getHitVec().getX(), Minecraft.getInstance().objectMouseOver.getHitVec().getY(), Minecraft.getInstance().objectMouseOver.getHitVec().getZ(), partial, false);
                }
            }
        }

        if (!isVisible)
        {
            return;
        }

        matrixStack.push();
        matrixStack.translate(blockPos.getX() - x, blockPos.getY() - y - player.getEyeHeight(), blockPos.getZ() - z);
        if (partial != null)
        {
            final BlockPos t = ModUtil.getPartialOffset(side, partial, modelBounds);
            final double fullScale = 1.0 / VoxelBlob.dim;
            matrixStack.translate(t.getX() * fullScale, t.getY() * fullScale, t.getZ() * fullScale);
        }

        RenderHelper.renderGhostModel(matrixStack, baked, player.getEntityWorld(), blockPos, placementResult, WorldRenderer.getCombinedLight(player.getEntityWorld(), blockPos),
          OverlayTexture.NO_OVERLAY);

        matrixStack.pop();
    }

    private boolean samePos(
      final BlockPos lastPartial2,
      final BlockPos partial)
    {
        if (lastPartial2 == partial)
        {
            return true;
        }

        if (lastPartial2 == null || partial == null)
        {
            return false;
        }

        return partial.equals(lastPartial2);
    }

    public PlayerEntity getPlayer()
    {
        return Minecraft.getInstance().player;
    }

    public boolean addHitEffects(
      final World world,
      final BlockRayTraceResult target,
      final BlockState state,
      final ParticleManager effectRenderer)
    {
        final ItemStack hitWith = getPlayer().getHeldItemMainhand();
        if (hitWith != null && (hitWith.getItem() instanceof ItemChisel || hitWith.getItem() instanceof ItemChiseledBit))
        {
            return true; // no
            // effects!
        }

        final BlockPos pos = target.getPos();
        final float boxOffset = 0.1F;

        AxisAlignedBB bb = world.getBlockState(pos).getBlock().getShape(state, world, pos, ISelectionContext.dummy()).getBoundingBox();
        ;

        double x = RANDOM.nextDouble() * (bb.maxX - bb.minX - boxOffset * 2.0F) + boxOffset + bb.minX;
        double y = RANDOM.nextDouble() * (bb.maxY - bb.minY - boxOffset * 2.0F) + boxOffset + bb.minY;
        double z = RANDOM.nextDouble() * (bb.maxZ - bb.minZ - boxOffset * 2.0F) + boxOffset + bb.minZ;

        switch (target.getFace())
        {
            case DOWN:
                y = bb.minY - boxOffset;
                break;
            case EAST:
                x = bb.maxX + boxOffset;
                break;
            case NORTH:
                z = bb.minZ - boxOffset;
                break;
            case SOUTH:
                z = bb.maxZ + boxOffset;
                break;
            case UP:
                y = bb.maxY + boxOffset;
                break;
            case WEST:
                x = bb.minX - boxOffset;
                break;
            default:
                break;
        }

        effectRenderer.addEffect((new DiggingParticle((ClientWorld) world, x, y, z, 0.0D, 0.0D, 0.0D, state)).setBlockPos(pos)
                                   .multiplyVelocity(0.2F)
                                   .multiplyParticleScaleBy(0.6F));

        return true;
    }

    @SubscribeEvent
    public void wheelEvent(
      final InputEvent.MouseScrollEvent me)
    {
        final int dwheel = (int) me.getScrollDelta();
        if (me.isCanceled() || dwheel == 0)
        {
            return;
        }

        final PlayerEntity player = ClientSide.instance.getPlayer();
        final ItemStack is = player.getHeldItemMainhand();

        if (dwheel != 0 && is != null && is.getItem() instanceof IItemScrollWheel && player.isSneaking())
        {
            ((IItemScrollWheel) is.getItem()).scroll(player, is, dwheel);
            me.setCanceled(true);
        }
    }

    public static void placeSound(
      final World world,
      final BlockPos pos,
      final int stateID)
    {
        final BlockState state = ModUtil.getStateById(stateID);
        final Block block = state.getBlock();
        world.playSound(pos.getX() + 0.5,
          pos.getY() + 0.5,
          pos.getZ() + 0.5,
          DeprecationHelper.getSoundType(block).getPlaceSound(),
          SoundCategory.BLOCKS,
          (DeprecationHelper.getSoundType(block).getVolume() + 1.0F) / 16.0F,
          DeprecationHelper.getSoundType(block).getPitch() * 0.9F,
          false);
    }

    public static void breakSound(
      final World world,
      final BlockPos pos,
      final int extractedState)
    {
        final BlockState state = ModUtil.getStateById(extractedState);
        final Block block = state.getBlock();
        world.playSound(pos.getX() + 0.5,
          pos.getY() + 0.5,
          pos.getZ() + 0.5,
          DeprecationHelper.getSoundType(block).getBreakSound(),
          SoundCategory.BLOCKS,
          (DeprecationHelper.getSoundType(block).getVolume() + 1.0F) / 16.0F,
          DeprecationHelper.getSoundType(block).getPitch() * 0.9F,
          false);
    }

    private BitLocation drawStart;
    private int         ticksSinceRelease = 0;
    private int         lastRenderedFrame = Integer.MIN_VALUE;

    public int getLastRenderedFrame()
    {
        return lastRenderedFrame;
    }

    public BitLocation getStartPos()
    {
        return drawStart;
    }

    public void pointAt(
      @Nonnull final ChiselToolType type,
      @Nonnull final BitLocation pos,
      @Nonnull final Hand hand)
    {
        if (drawStart == null)
        {
            drawStart = pos;
            lastTool = type;
            lastHand = hand;
        }
    }

    @Nonnull
    ChiselToolType lastTool = ChiselToolType.CHISEL;

    @Nonnull
    Hand lastHand = Hand.MAIN_HAND;

    public void setLastTool(@Nonnull final ChiselToolType lastTool)
    {
        this.lastTool = lastTool;
    }

    KeyBinding getToolKey()
    {
        if (lastTool == ChiselToolType.CHISEL)
        {
            return Minecraft.getInstance().gameSettings.keyBindAttack;
        }
        else
        {
            return Minecraft.getInstance().gameSettings.keyBindUseItem;
        }
    }

    public boolean addBlockDestroyEffects(
      @Nonnull final World world,
      @Nonnull final BlockPos pos,
      BlockState state,
      final ParticleManager effectRenderer)
    {
        if (!state.getBlock().isAir(state, world, pos))
        {
            VoxelShape voxelshape = state.getShape(world, pos);
            double d0 = 0.25D;
            voxelshape.forEachBox((p_228348_3_, p_228348_5_, p_228348_7_, p_228348_9_, p_228348_11_, p_228348_13_) -> {
                double d1 = Math.min(1.0D, p_228348_9_ - p_228348_3_);
                double d2 = Math.min(1.0D, p_228348_11_ - p_228348_5_);
                double d3 = Math.min(1.0D, p_228348_13_ - p_228348_7_);
                int i = Math.max(2, MathHelper.ceil(d1 / 0.25D));
                int j = Math.max(2, MathHelper.ceil(d2 / 0.25D));
                int k = Math.max(2, MathHelper.ceil(d3 / 0.25D));

                for (int l = 0; l < i; ++l)
                {
                    for (int i1 = 0; i1 < j; ++i1)
                    {
                        for (int j1 = 0; j1 < k; ++j1)
                        {
                            double d4 = ((double) l + 0.5D) / (double) i;
                            double d5 = ((double) i1 + 0.5D) / (double) j;
                            double d6 = ((double) j1 + 0.5D) / (double) k;
                            double d7 = d4 * d1 + p_228348_3_;
                            double d8 = d5 * d2 + p_228348_5_;
                            double d9 = d6 * d3 + p_228348_7_;
                            effectRenderer.addEffect((new DiggingParticle((ClientWorld) world,
                              (double) pos.getX() + d7,
                              (double) pos.getY() + d8,
                              (double) pos.getZ() + d9,
                              d4 - 0.5D,
                              d5 - 0.5D,
                              d6 - 0.5D,
                              state)).setBlockPos(pos));
                        }
                    }
                }
            });
        }

        return true;
    }

    public TextureAtlasSprite getMissingIcon()
    {
        return Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(new ResourceLocation("missingno"));
    }

    public String getModeKey()
    {
        return getKeyName(modeMenu);
    }

    public ChiselToolType getDrawnTool()
    {
        return lastTool;
    }

    public boolean holdingShift()
    {
        return (!Minecraft.getInstance().gameSettings.keyBindSneak.isInvalid() && Minecraft.getInstance().gameSettings.keyBindSneak.isKeyDown()) || Screen.hasShiftDown();
    }

    public String getKeyName(
      KeyBinding bind)
    {
        if (bind == null)
        {
            return LocalStrings.noBind.getLocal();
        }

        if (bind.getKey().getKeyCode() == 0 && bind.getDefault().getKeyCode() != 0)
        {
            //TODO: This previously changed the resulting string to something easier to understand. Not sure that is still needed.
            return DeprecationHelper.translateToLocal(bind.getTranslationKey());
        }

        if (bind.getKey().getKeyCode() == 0)
        {
            return '"' + DeprecationHelper.translateToLocal(bind.getKeyDescription());
        }

        return makeMoreFrendly(bind.getTranslationKey());
    }

    private String makeMoreFrendly(
      String displayName)
    {
        return DeprecationHelper.translateToLocal(displayName)
                                                    .replace("LMENU", LocalStrings.leftAlt.getLocal())
                                                    .replace("RMENU", LocalStrings.rightAlt.getLocal())
                                                    .replace("LSHIFT", LocalStrings.leftShift.getLocal())
                 .replace("RSHIFT", LocalStrings.rightShift.getLocal())
                 .replace("key.keyboard.", "");
    }

    public static boolean offGridPlacement(
      PlayerEntity player)
    {
        if (player instanceof FakePlayer)
        {
            return false;
        }

        if (player.getEntityWorld().isRemote)
        {
            return !getOffGridPlacementKey().isInvalid() && getOffGridPlacementKey().isKeyDown();
        }

        throw new RuntimeException("checking keybinds on server.");
    }

    public static KeyBinding getOffGridPlacementKey()
    {
        if (!ClientSide.instance.offgridPlacement.isInvalid() && ClientSide.instance.offgridPlacement.isDefault())
        {
            return Minecraft.getInstance().gameSettings.keyBindSneak;
        }

        return ClientSide.instance.offgridPlacement;
    }
}
