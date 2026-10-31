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

/**
 * The little sled a bleep rides on snow: a red wooden deck under its tracks on two steel runners that curl up at the
 * front. Texture: entity/bleep/sled.png (64 x 32: red wood at the top, steel below, drawn by tools/gen-assets.mjs).
 */
public class SledModel extends EntityModel<BleepRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Bleep.id("bleep"), "sled");

    public SledModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0, 24, 0)); // as BleepModel: y 0 is the ground
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("deck", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7.5f, -2, -7, 15, 1, 14),   // the red deck it stands on (BleepModel lifts it 2 pixels)
                PartPose.ZERO);
        root.addOrReplaceChild("runners", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-7, -1, -8, 1, 1, 16)       // the steel runners on the snow
                .texOffs(0, 16).addBox(6, -1, -8, 1, 1, 16)
                .texOffs(0, 16).addBox(-7, -3, -10, 1, 2, 2)       // curled up at the front
                .texOffs(0, 16).addBox(6, -3, -10, 1, 2, 2)
                .texOffs(0, 16).addBox(-7, -4, -11, 14, 1, 1),     // and the bar between them
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }
}
