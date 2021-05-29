package mod.chiselsandbits.printer;

import com.google.common.collect.ImmutableMap;
import mod.chiselsandbits.core.ChiselsAndBits;
import mod.chiselsandbits.helpers.LocalStrings;
import net.minecraft.block.*;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ChiselPrinterBlock extends ContainerBlock
{

    public static final  DirectionProperty FACING   = HorizontalBlock.HORIZONTAL_FACING;

    private static final Map<Direction, VoxelShape> BUTTON_VS_MAP = ImmutableMap.<Direction, VoxelShape>builder()
                                                                      .put(Direction.NORTH, Block.makeCuboidShape(7, 1, -0.5, 12, 4, 0))
                                                                      .put(Direction.EAST, Block.makeCuboidShape(16,1,7,16.5,4,12))
                                                                      .put(Direction.SOUTH, Block.makeCuboidShape(4, 1, 16, 9, 4, 16.5))
                                                                      .put(Direction.WEST, Block.makeCuboidShape(-0.5, 1, 4, 0, 4, 9))
                                                                      .build();

    private static final VoxelShape        VS_NORTH = createForDirection(Direction.NORTH);
    private static final VoxelShape        VS_EAST = createForDirection(Direction.EAST);
    private static final VoxelShape        VS_SOUTH = createForDirection(Direction.SOUTH);
    private static final VoxelShape        VS_WEST = createForDirection(Direction.WEST);

    private static final Map<Direction, VoxelShape> VS_MAP = ImmutableMap.<Direction, VoxelShape>builder()
        .put(Direction.NORTH, VS_NORTH)
        .put(Direction.EAST, VS_EAST)
        .put(Direction.SOUTH, VS_SOUTH)
        .put(Direction.WEST, VS_WEST)
        .build();

    private static VoxelShape createForDirection(final Direction direction)
    {
        return VoxelShapes.combineAndSimplify(VoxelShapes.combineAndSimplify(Stream.of(
          Block.makeCuboidShape(0, 5, 0, 2, 16, 2),
          Block.makeCuboidShape(14, 5, 0, 16, 16, 2),
          Block.makeCuboidShape(0, 5, 14, 2, 16, 16),
          Block.makeCuboidShape(14, 5, 14, 16, 16, 16)
          ).reduce((v1, v2) -> VoxelShapes.combineAndSimplify(v1, v2, IBooleanFunction.OR)).orElse(VoxelShapes.empty()), Stream.of(
          Block.makeCuboidShape(2, 14, 0, 14, 16, 2),
          Block.makeCuboidShape(2, 14, 14, 14, 16, 16),
          Block.makeCuboidShape(0, 14, 2, 2, 16, 14),
          Block.makeCuboidShape(14, 14, 2, 16, 16, 14),
          Stream.of(
            Block.makeCuboidShape(2, 14, 7, 14, 16, 9),
            Block.makeCuboidShape(7, 13.99, 2, 9, 15.98, 14),
            Block.makeCuboidShape(7, 11, 7, 9, 14, 9),
            Block.makeCuboidShape(7.5, 10, 7.5, 8.5, 11, 8.5)
          ).reduce((v1, v2) -> VoxelShapes.combineAndSimplify(v1, v2, IBooleanFunction.OR)).orElse(VoxelShapes.empty())
          ).reduce((v1, v2) -> VoxelShapes.combineAndSimplify(v1, v2, IBooleanFunction.OR)).orElse(VoxelShapes.empty()), IBooleanFunction.OR),
          VoxelShapes.combineAndSimplify(Block.makeCuboidShape(0, 0, 0, 16, 5, 16), BUTTON_VS_MAP.getOrDefault(direction, VoxelShapes.empty()), IBooleanFunction.OR),
          IBooleanFunction.OR);
    }

    public ChiselPrinterBlock(final Properties builder)
    {
        super(builder);
        this.setDefaultState(this.stateContainer.getBaseState().with(FACING, Direction.NORTH));
    }

    public BlockState getStateForPlacement(BlockItemUseContext context)
    {
        return this.getDefaultState().with(FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    public BlockRenderType getRenderType(BlockState state)
    {
        return BlockRenderType.MODEL;
    }

    public BlockState rotate(BlockState state, Rotation rot)
    {
        return state.with(FACING, rot.rotate(state.get(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirrorIn)
    {
        return state.rotate(mirrorIn.toRotation(state.get(FACING)));
    }

    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(final BlockState state, final IBlockReader worldIn, final BlockPos pos, final ISelectionContext context)
    {
        return VS_MAP.getOrDefault(state.get(FACING), VoxelShapes.empty());
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(final IBlockReader worldIn)
    {
        return new ChiselPrinterTileEntity();
    }

    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit)
    {
        if (worldIn.isRemote)
        {
            return ActionResultType.SUCCESS;
        }
        else
        {
            player.openContainer((INamedContainerProvider) worldIn.getTileEntity(pos));
            return ActionResultType.CONSUME;
        }
    }

    @Override
    public void addInformation(
      final ItemStack stack, @Nullable final IBlockReader worldIn, final List<ITextComponent> tooltip, final ITooltipFlag flagIn)
    {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        ChiselsAndBits.getConfig().getCommon().helpText(LocalStrings.ChiselStationHelp, tooltip);
    }
}
