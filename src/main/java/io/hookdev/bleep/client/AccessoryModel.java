package io.hookdev.bleep.client;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.entity.Accessory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * What a bleep wears: the same skeleton as BleepModel (so a hat moves with its head), without its own boxes, and the
 * hats and sunglasses on its head, the rest on its body (the jacket has sleeves on its arms; the cape swings out as it
 * drives). Only the ones it wears are shown. Texture: entity/bleep/accessories.png
 * (64 x 64, drawn by tools/gen-assets.mjs from these boxes).
 */
public class AccessoryModel extends BleepModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Bleep.id("bleep"), "accessories");

    private final Map<Accessory, List<ModelPart>> parts = new EnumMap<>(Accessory.class);
    private final ModelPart cape;

    public AccessoryModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild("body"), head = body.getChild("head");
        for (Accessory accessory : Accessory.values()) {
            List<ModelPart> list = new ArrayList<>();
            list.add((accessory.slot.onHead() ? head : body).getChild(accessory.id()));
            if (accessory == Accessory.LEATHER_JACKET) { // and its sleeves
                list.add(body.getChild("right_arm").getChild(accessory.id()));
                list.add(body.getChild("left_arm").getChild(accessory.id()));
            }
            parts.put(accessory, list);
        }
        cape = body.getChild("cape");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0, 24, 0));
        PartDefinition root = mesh.getRoot();
        // the skeleton of BleepModel, with no boxes of its own
        root.addOrReplaceChild("chassis", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_track", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("left_track", CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < 3; i++) {
            root.addOrReplaceChild("right_wheel_" + i, CubeListBuilder.create(), PartPose.offset(-7.25f, -2, -3.5f + 3.5f * i));
            root.addOrReplaceChild("left_wheel_" + i, CubeListBuilder.create(), PartPose.offset(7.25f, -2, -3.5f + 3.5f * i));
        }
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, -5, 0));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0, -8, 0));
        PartDefinition rightArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-4, -5, 0));
        PartDefinition leftArm = body.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(4, -5, 0));
        // hats, on top of its head (the head's top is at -7)
        head.addOrReplaceChild("party_hat", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3, -8, -3, 6, 1, 6)
                .texOffs(24, 0).addBox(-2, -10, -2, 4, 2, 4)
                .texOffs(40, 0).addBox(-1, -12, -1, 2, 2, 2), PartPose.ZERO);
        head.addOrReplaceChild("top_hat", CubeListBuilder.create()
                .texOffs(0, 8).addBox(-4, -8, -4, 8, 1, 8)
                .texOffs(32, 8).addBox(-3, -13, -3, 6, 5, 6), PartPose.ZERO);
        head.addOrReplaceChild("antenna", CubeListBuilder.create()
                .texOffs(0, 20).addBox(-0.5f, -13, -0.5f, 1, 6, 1)
                .texOffs(4, 20).addBox(-1, -15, -1, 2, 2, 2)
                .texOffs(12, 20).addBox(-2.5f, -11, -0.5f, 2, 2, 1)   // the bow, one loop each side
                .texOffs(12, 20).addBox(0.5f, -11, -0.5f, 2, 2, 1), PartPose.ZERO);
        // stickers, on the round front of its body
        body.addOrReplaceChild("sticker_star", CubeListBuilder.create().texOffs(0, 30).addBox(0, -4.5f, -5.5f, 3, 3, 1), PartPose.ZERO);
        body.addOrReplaceChild("sticker_heart", CubeListBuilder.create().texOffs(8, 30).addBox(0, -4.5f, -5.5f, 3, 3, 1), PartPose.ZERO);
        body.addOrReplaceChild("sticker_bolt", CubeListBuilder.create().texOffs(16, 30).addBox(0, -4.5f, -5.5f, 3, 3, 1), PartPose.ZERO);
        // a crown: a gold band, a point at each corner and a red gem at the front
        head.addOrReplaceChild("crown", CubeListBuilder.create()
                .texOffs(24, 20).addBox(-3, -8, -3, 6, 1, 6)
                .texOffs(48, 20).addBox(-3, -10, -3, 1, 2, 1).texOffs(48, 20).addBox(2, -10, -3, 1, 2, 1)
                .texOffs(48, 20).addBox(-3, -10, 2, 1, 2, 1).texOffs(48, 20).addBox(2, -10, 2, 1, 2, 1)
                .texOffs(52, 20).addBox(-0.5f, -9.5f, -3.2f, 1, 1, 1), PartPose.ZERO);
        // sunglasses over its screen (its front is at -4, the eyes on its screen at -5 to -3.5)
        head.addOrReplaceChild("sunglasses", CubeListBuilder.create()
                .texOffs(0, 36).addBox(-6, -6, -5, 12, 1, 1)
                .texOffs(26, 36).addBox(-4, -5, -5, 3, 2, 1).texOffs(26, 36).addBox(1, -5, -5, 3, 2, 1)
                .texOffs(34, 36).addBox(-7, -6, -4, 1, 1, 3).texOffs(34, 36).addBox(6, -6, -4, 1, 1, 3), PartPose.ZERO);
        // a leather jacket: just round its body, its round front and back, a collar, and sleeves on its arms
        CubeDeformation leather = new CubeDeformation(0.35f);
        body.addOrReplaceChild("leather_jacket", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-4, -6, -3.5f, 8, 6, 7, leather)
                .texOffs(30, 40).addBox(-3, -5.5f, -4.5f, 6, 5, 1, leather)
                .texOffs(30, 40).addBox(-3, -5.5f, 3.5f, 6, 5, 1, leather)
                .texOffs(44, 40).addBox(-2, -7, -2, 4, 1, 4, new CubeDeformation(0.4f)), PartPose.ZERO);
        rightArm.addOrReplaceChild("leather_jacket", CubeListBuilder.create().texOffs(30, 47).addBox(-2, 0, -1, 2, 4, 2, new CubeDeformation(0.3f)), PartPose.ZERO);
        leftArm.addOrReplaceChild("leather_jacket", CubeListBuilder.create().texOffs(30, 47).addBox(0, 0, -1, 2, 4, 2, new CubeDeformation(0.3f)), PartPose.ZERO);
        // a cape from its shoulders, hanging from the top so it can swing out
        body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 54).addBox(-4, 0, 0, 8, 9, 1), PartPose.offset(0, -6, 4.9f));
        // round its neck (its neck hides under its head, so these sit at the top of its chest): a bow tie, or a striped
        // scarf round its shoulders with a tail down its front
        body.addOrReplaceChild("bow_tie", CubeListBuilder.create()
                .texOffs(20, 54).addBox(-3, -6.5f, -5.5f, 2, 2, 1).texOffs(20, 54).addBox(1, -6.5f, -5.5f, 2, 2, 1)
                .texOffs(28, 54).addBox(-1, -6, -5.7f, 2, 1, 1), PartPose.ZERO);
        body.addOrReplaceChild("scarf", CubeListBuilder.create()
                .texOffs(34, 54).addBox(-4, -6.5f, -3.5f, 8, 1, 7, new CubeDeformation(0.5f))
                .texOffs(20, 59).addBox(-2.5f, -5.5f, -5.6f, 1, 4, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(BleepRenderState state) {
        super.setupAnim(state); // the same pose as the bleep itself
        for (var entry : parts.entrySet()) {
            boolean on = state.worn.get(entry.getKey().slot) == entry.getKey();
            for (ModelPart part : entry.getValue()) part.visible = on;
        }
        // the cape swings out behind it as it drives, and flutters a little
        cape.xRot = 0.12f + Math.min(state.walkAnimationSpeed, 1) * 0.6f + net.minecraft.util.Mth.sin(state.ageInTicks * 0.15f) * 0.04f;
    }
}
