package mod.chiselsandbits.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.mojang.blaze3d.vertex.MatrixApplyingVertexBuilder;
import mod.chiselsandbits.chiseledblock.ItemBlockChiseled;
import mod.chiselsandbits.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

public class RenderHelper
{

    public static Random RENDER_RANDOM = new Random();

    public static void drawSelectionBoundingBoxIfExists(
      final MatrixStack matrixStack,
      final AxisAlignedBB bb,
      final BlockPos blockPos,
      final PlayerEntity player,
      final float partialTicks,
      final boolean NormalBoundingBox)
    {
        drawSelectionBoundingBoxIfExistsWithColor(matrixStack, bb, blockPos, player, partialTicks, NormalBoundingBox, 0, 0, 0, 102, 32);
    }

    public static void drawSelectionBoundingBoxIfExistsWithColor(
      final MatrixStack matrixStack,
      final AxisAlignedBB bb,
      final BlockPos blockPos,
      final PlayerEntity player,
      final float partialTicks,
      final boolean NormalBoundingBox,
      final int red,
      final int green,
      final int blue,
      final int alpha,
      final int seeThruAlpha)
    {
        if (bb != null)
        {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glLineWidth(2.0F);
            RenderSystem.disableTexture();
            RenderSystem.depthMask(false);

            if (!NormalBoundingBox)
            {
                RenderHelper.renderBoundingBox(matrixStack, bb.expand(0.002D, 0.002D, 0.002D).offset(blockPos.getX(), blockPos.getY(), blockPos.getZ()), red, green, blue, alpha);
            }

            RenderSystem.disableDepthTest();

            RenderHelper.renderBoundingBox(matrixStack, bb.expand(0.002D, 0.002D, 0.002D).offset(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
              red,
              green,
              blue,
              seeThruAlpha);

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableTexture();
            RenderSystem.disableBlend();
        }
    }

    public static void drawLineWithColor(
      final MatrixStack matrixStack,
      final Vector3d a,
      final Vector3d b,
      final BlockPos blockPos,
      final PlayerEntity player,
      final float partialTicks,
      final boolean NormalBoundingBox,
      final int red,
      final int green,
      final int blue,
      final int alpha,
      final int seeThruAlpha)
    {
        if (a != null && b != null)
        {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glLineWidth(2.0F);
            RenderSystem.disableTexture();
            RenderSystem.depthMask(false);
            RenderSystem.shadeModel(GL11.GL_FLAT);

            final Vector3d a2 = a.add(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            final Vector3d b2 = b.add(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            if (!NormalBoundingBox)
            {
                RenderHelper.renderLine(matrixStack, a2, b2, red, green, blue, alpha);
            }

            RenderSystem.disableDepthTest();

            RenderHelper.renderLine(matrixStack, a2, b2, red, green, blue, seeThruAlpha);

            RenderSystem.shadeModel(Minecraft.isAmbientOcclusionEnabled() ? GL11.GL_SMOOTH : GL11.GL_FLAT);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableTexture();
            RenderSystem.disableBlend();
        }
    }

    public static void renderQuads(
      final MatrixStack matrixStack,
      final int alpha,
      final BufferBuilder renderer,
      final List<BakedQuad> quads,
      final World worldObj,
      final BlockPos blockPos,
      int combinedLightIn,
      int combinedOverlayIn)
    {
        int i = 0;
        for (final int j = quads.size(); i < j; ++i)
        {
            final BakedQuad bakedquad = quads.get(i);
            final int color = bakedquad.getTintIndex() == -1 ? alpha | 0xffffff : getTint(alpha, bakedquad.getTintIndex(), worldObj, blockPos);

            float cb = color & 0xFF;
            float cg = (color >>> 8) & 0xFF;
            float cr = (color >>> 16) & 0xFF;
            float ca = (color >>> 24) & 0xFF;

            renderer.addVertexData(matrixStack.getLast(), bakedquad, cb, cg, cr, ca, combinedLightIn, combinedOverlayIn, false);
        }
    }

    // Custom replacement of 1.9.4 -> 1.10's method that changed.
    public static void renderBoundingBox(
      final MatrixStack matrixStack, final AxisAlignedBB boundingBox,
      final int red,
      final int green,
      final int blue,
      final int alpha)
    {
        GL11.glPushAttrib(8256);
        final Tessellator tess = Tessellator.getInstance();
        final BufferBuilder bufferBuilder = tess.getBuffer();
        RenderSystem.shadeModel(GL11.GL_FLAT);
        bufferBuilder.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        final float minX = (float) boundingBox.minX;
        final float minY = (float) boundingBox.minY;
        final float minZ = (float) boundingBox.minZ;
        final float maxX = (float) boundingBox.maxX;
        final float maxY = (float) boundingBox.maxY;
        final float maxZ = (float) boundingBox.maxZ;

        // lower ring ( starts to 0 / 0 )
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), minX, minY, minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), maxX, minY, minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), maxX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), minX, minY, maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), minX, minY, minZ).color(red, green, blue, alpha).endVertex();

        // Y line at 0 / 0
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), minX, maxY, minZ).color(red, green, blue, alpha).endVertex();

        // upper ring ( including previous point to draw 4 lines )
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), minX, maxY, minZ).color(red, green, blue, alpha).endVertex();

        /*
         * the next 3 Y Lines use flat shading to render invisible lines to
         * enable doing this all in one pass.
         */

        // Y line at 1 / 0
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), maxX, minY, minZ).color(red, green, blue, 0).endVertex();
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), maxX, maxY, minZ).color(red, green, blue, alpha).endVertex();

        // Y line at 0 / 1
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), minX, minY, maxZ).color(red, green, blue, 0).endVertex();
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), minX, maxY, maxZ).color(red, green, blue, alpha).endVertex();

        // Y line at 1 / 1
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), maxX, minY, maxZ).color(red, green, blue, 0).endVertex();
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), maxX, maxY, maxZ).color(red, green, blue, alpha).endVertex();

        tess.draw();
        GL11.glPopAttrib();
    }

    public static void renderLine(
      final MatrixStack matrixStack,
      final Vector3d a,
      final Vector3d b,
      final int red,
      final int green,
      final int blue,
      final int alpha)
    {
        final Tessellator tess = Tessellator.getInstance();
        final BufferBuilder bufferBuilder = tess.getBuffer();
        RenderSystem.shadeModel(GL11.GL_FLAT);
        bufferBuilder.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), (float) a.x, (float) a.y, (float) a.z).color(red, green, blue, alpha).endVertex();
        bufferBuilder.pos(matrixStack.getLast().getMatrix(), (float) b.x, (float) b.y, (float) b.z).color(red, green, blue, alpha).endVertex();
        tess.draw();
    }

    public static int getTint(
      final int alpha,
      final int tintIndex,
      final World worldObj,
      final BlockPos blockPos)
    {
        return alpha | Minecraft.getInstance().getBlockColors().getColor(ModBlocks.getChiseledDefaultState(), worldObj, blockPos, tintIndex);
    }

    public static void renderModel(
      final MatrixStack matrixStack,
      final IBakedModel model,
      final World worldObj,
      final BlockPos blockPos,
      final int alpha,
      final int combinedLightmap,
      final int combinedOverlay)
    {
        final Tessellator tessellator = Tessellator.getInstance();
        final BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

        for (final Direction enumfacing : Direction.values())
        {
            renderQuads(matrixStack, alpha, buffer, model.getQuads(null, enumfacing, RENDER_RANDOM), worldObj, blockPos, combinedLightmap, combinedOverlay);
        }

        renderQuads(matrixStack, alpha, buffer, model.getQuads(null, null, RENDER_RANDOM), worldObj, blockPos, combinedLightmap, combinedOverlay);
        tessellator.draw();
    }

    public static void renderGhostModel(
      final MatrixStack matrixStack,
      final IBakedModel baked,
      final World worldObj,
      final BlockPos blockPos,
      final ItemBlockChiseled.PlacementAttemptResult placementResult,
      final int combinedLightmap,
      final int combinedOverlay)
    {
        final int alpha = !placementResult.isCanPlace() ? 0x22000000 : 0xaa000000;

        final float r = !placementResult.isCanPlace() || placementResult != ItemBlockChiseled.PlacementAttemptResult.MERGEABLE ? 1f : 1f;
        final float g = !placementResult.isCanPlace() || placementResult != ItemBlockChiseled.PlacementAttemptResult.MERGEABLE ? 1f : 0.5f;
        final float b = !placementResult.isCanPlace() || placementResult != ItemBlockChiseled.PlacementAttemptResult.MERGEABLE ? 1f : 0f;

        Minecraft.getInstance().getTextureManager().bindTexture(PlayerContainer.LOCATION_BLOCKS_TEXTURE);
        RenderSystem.color4f(r, g, b, 1.0f);

        RenderSystem.enableBlend();
        RenderSystem.enableTexture();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.colorMask(false, false, false, false);

        RenderHelper.renderModel(matrixStack, baked, worldObj, blockPos, alpha, combinedLightmap, combinedOverlay);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.color4f(r, g, b, 1.0f);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderHelper.renderModel(matrixStack, baked, worldObj, blockPos, alpha, combinedLightmap, combinedOverlay);

        RenderSystem.disableBlend();
    }
}
