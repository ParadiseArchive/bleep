package io.hookdev.bleep.client;

import io.hookdev.bleep.Bleep;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The little boat a bleep sits in on the water: a wooden hull round its tracks, a pointed bow at the front, rocking
 * gently. Texture: entity/bleep/boat.png (128 x 64, planks all over, drawn by tools/gen-assets.mjs). On snow the same
 * layer draws a sled instead (see SledModel).
 */
public class BoatModel extends EntityModel<BleepRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Bleep.id("bleep"), "boat");
    private final ModelPart hull;

    public BoatModel(ModelPart root) {
        super(root);
        this.hull = root.getChild("hull");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0, 24, 0)); // as BleepModel: y 0 is the ground
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hull", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8, -1, -8, 16, 1, 17)    // the bottom
                .texOffs(0, 20).addBox(-9, -6, -8, 1, 6, 17)    // the sides
                .texOffs(40, 20).addBox(8, -6, -8, 1, 6, 17)
                .texOffs(0, 44).addBox(-8, -6, 9, 16, 6, 1)     // the stern
                .texOffs(36, 44).addBox(-7, -6, -10, 14, 6, 2)  // the bow, narrowing
                .texOffs(72, 44).addBox(-5, -6, -12, 10, 6, 2)
                .texOffs(100, 44).addBox(-2, -6, -13, 4, 6, 1),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(BleepRenderState state) {
        super.setupAnim(state);
        hull.zRot = Mth.sin(state.ageInTicks * 0.12f) * 0.05f; // rocking on the water
        hull.xRot = Mth.sin(state.ageInTicks * 0.09f + 1) * 0.03f;
    }
}
