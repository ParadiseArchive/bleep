package io.hookdev.bleep.client;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.entity.Accessory;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * What a bleep wears: the same skeleton as BleepModel (so a hat moves with its head), without its own boxes, and the
 * hats on its head and the stickers on its front. Only the ones it wears are shown. Texture: entity/bleep/accessories.png
 * (64 x 64, drawn by tools/gen-assets.mjs from these boxes).
 */
public class AccessoryModel extends BleepModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Bleep.id("bleep"), "accessories");

    private final Map<Accessory, ModelPart> parts = new EnumMap<>(Accessory.class);

    public AccessoryModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild("body"), head = body.getChild("head");
        for (Accessory accessory : Accessory.values()) parts.put(accessory, (accessory.hat ? head : body).getChild(accessory.id()));
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
        body.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-4, -5, 0));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(4, -5, 0));
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
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(BleepRenderState state) {
        super.setupAnim(state); // the same pose as the bleep itself
        for (var entry : parts.entrySet()) {
            entry.getValue().visible = entry.getKey() == (entry.getKey().hat ? state.hat : state.sticker);
        }
    }
}
