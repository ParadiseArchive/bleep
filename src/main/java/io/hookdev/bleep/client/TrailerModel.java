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
 * The trailer behind a bleep (owner, 31 Oct 2026): a little wooden cart on two wheels, a drawbar to the back of its
 * tracks, and a chest in it (its storage is a big chest now). Texture: entity/bleep/trailer.png (64 x 64: planks at the
 * top, the chest and the wheels below, drawn by tools/gen-assets.mjs). The wheels turn as it drives.
 */
public class TrailerModel extends EntityModel<BleepRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Bleep.id("bleep"), "trailer");
    private final ModelPart rightWheel, leftWheel;

    public TrailerModel(ModelPart root) {
        super(root);
        this.rightWheel = root.getChild("right_wheel");
        this.leftWheel = root.getChild("left_wheel");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0, 24, 0)); // as BleepModel: y 0 is the ground
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("cart", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5, -4, 9, 10, 1, 10)       // the floor
                .texOffs(0, 12).addBox(-5, -8, 9, 1, 4, 10)       // the sides
                .texOffs(0, 12).addBox(4, -8, 9, 1, 4, 10)
                .texOffs(24, 12).addBox(-4, -8, 9, 8, 4, 1)       // the front and the back
                .texOffs(24, 18).addBox(-4, -8, 18, 8, 4, 1)
                .texOffs(44, 12).addBox(-0.5f, -4, 5, 1, 1, 4),   // the drawbar to its tracks
                PartPose.ZERO);
        root.addOrReplaceChild("chest", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-3.5f, -9, 10.5f, 7, 5, 7), // a chest in the cart, its lid just over the sides
                PartPose.ZERO);
        root.addOrReplaceChild("right_wheel", CubeListBuilder.create().texOffs(32, 32).addBox(-0.5f, -2, -2, 1, 4, 4), PartPose.offset(-5.5f, -2, 14));
        root.addOrReplaceChild("left_wheel", CubeListBuilder.create().texOffs(32, 32).addBox(-0.5f, -2, -2, 1, 4, 4), PartPose.offset(5.5f, -2, 14));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(BleepRenderState state) {
        super.setupAnim(state);
        rightWheel.xRot = leftWheel.xRot = state.walkAnimationPos * 0.9f;
    }
}
