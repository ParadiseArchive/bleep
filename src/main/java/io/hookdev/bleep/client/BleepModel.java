package io.hookdev.bleep.client;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Emotion;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * A bleep, in pixels (1/16 block): two tracks with three wheels each, a chassis between them, a round white body,
 * big arms, and a wide head that is one display at the front. The texture is 64 x 64 (tools/gen-assets.mjs draws it
 * from the same box sizes); the faces on the display come from a 128 x 128 texture, so the display has twice the
 * pixels: 20 x 10.
 */
public class BleepModel extends EntityModel<BleepRenderState> implements ArmedModel<BleepRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Bleep.id("bleep"), "main");
    private static final float ARMS_FORWARD = -0.9f;

    private final ModelPart body, head, rightArm, leftArm, chassis, rightTrack, leftTrack;
    private final ModelPart[] wheels = new ModelPart[6];

    public BleepModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.chassis = root.getChild("chassis");
        this.rightTrack = root.getChild("right_track");
        this.leftTrack = root.getChild("left_track");
        for (int i = 0; i < 3; i++) {
            wheels[i] = root.getChild("right_wheel_" + i);
            wheels[3 + i] = root.getChild("left_wheel_" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition().transformed(p -> p.translated(0, 24, 0)); // y 0 is the ground, up is negative
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("chassis", CubeListBuilder.create().texOffs(0, 30).addBox(-4, -5, -4, 8, 2, 8), PartPose.ZERO);
        root.addOrReplaceChild("right_track", CubeListBuilder.create().texOffs(0, 41).addBox(-7, -4, -5.5f, 3, 4, 11), PartPose.ZERO);
        root.addOrReplaceChild("left_track", CubeListBuilder.create().texOffs(28, 41).addBox(4, -4, -5.5f, 3, 4, 11), PartPose.ZERO);
        for (int i = 0; i < 3; i++) { // the wheels in the tracks, front to back, turned about their middles as it drives
            float z = -3.5f + 3.5f * i;
            root.addOrReplaceChild("right_wheel_" + i, CubeListBuilder.create().texOffs(56, 41).addBox(-0.5f, -1.5f, -1.5f, 1, 3, 3), PartPose.offset(-7.25f, -2, z));
            root.addOrReplaceChild("left_wheel_" + i, CubeListBuilder.create().texOffs(56, 41).addBox(-0.5f, -1.5f, -1.5f, 1, 3, 3), PartPose.offset(7.25f, -2, z));
        }
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-4, -6, -3.5f, 8, 6, 7)
                .texOffs(32, 16).addBox(-3, -5.5f, -4.5f, 6, 5, 1) // rounded at the front
                .texOffs(32, 16).addBox(-3, -5.5f, 3.5f, 6, 5, 1)  // and at the back
                .texOffs(32, 30).addBox(-2, -8, -2, 4, 2, 4),      // the neck
                PartPose.offset(0, -5, 0));
        body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6, -7, -4, 12, 7, 8)
                .texOffs(56, 48).addBox(-7, -5, -1.5f, 1, 3, 3)   // a round knob on each side
                .texOffs(56, 48).addBox(6, -5, -1.5f, 1, 3, 3),
                PartPose.offset(0, -8, 0));
        body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-2, 0, -1, 2, 6, 2)
                .texOffs(48, 0).addBox(-2.5f, 6, -1.5f, 3, 2, 3), // the hand
                PartPose.offset(-4, -5, 0));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(40, 0).addBox(0, 0, -1, 2, 6, 2)
                .texOffs(48, 0).addBox(-0.5f, 6, -1.5f, 3, 2, 3),
                PartPose.offset(4, -5, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(BleepRenderState state) {
        super.setupAnim(state);
        // being put together: the tracks first, then its body, its arms and its head, a quarter of the time each
        int built = state.assembly > 0 ? BleepEntity.ASSEMBLE_TICKS - state.assembly : BleepEntity.ASSEMBLE_TICKS;
        body.visible = built >= BleepEntity.ASSEMBLE_STEP;
        rightArm.visible = leftArm.visible = built >= 2 * BleepEntity.ASSEMBLE_STEP;
        head.visible = built >= 3 * BleepEntity.ASSEMBLE_STEP;
        chassis.visible = rightTrack.visible = leftTrack.visible = true;
        float walk = state.walkAnimationPos, speed = Math.min(state.walkAnimationSpeed, 1), age = state.ageInTicks;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        for (ModelPart wheel : wheels) wheel.xRot = walk * 1.5f;
        body.y += Mth.sin(walk * 2) * 0.3f * speed; // a little bumpy on the tracks
        float sway = Mth.sin(age * 0.08f) * 0.08f, swing = Mth.sin(walk) * 0.3f * speed;
        rightArm.xRot = ARMS_FORWARD + sway + swing;
        leftArm.xRot = ARMS_FORWARD - sway - swing;
        Emotion emotion = state.emotion;
        switch (emotion) {
            case EXCITED -> { // both arms up in a V beside its head, waving
                rightArm.xRot = leftArm.xRot = -0.2f;
                rightArm.zRot = 2.5f + Mth.sin(age * 0.6f) * 0.3f;
                leftArm.zRot = -2.5f - Mth.sin(age * 0.6f) * 0.3f;
            }
            case SURPRISED -> { // arms flung out to the sides
                rightArm.xRot = leftArm.xRot = -0.3f;
                rightArm.zRot = 1.6f;
                leftArm.zRot = -1.6f;
                head.xRot -= 0.15f;
            }
            case ANGRY -> { // fists up, shaking
                rightArm.xRot = leftArm.xRot = -1.5f + Mth.sin(age * 1.2f) * 0.15f;
                head.xRot += 0.1f;
            }
            case SAD, TIRED -> {
                rightArm.xRot = leftArm.xRot = -0.2f;
                head.xRot += 0.3f;
            }
            case BORED -> {
                rightArm.xRot = leftArm.xRot = -0.4f;
                head.zRot = 0.15f;
            }
            case ASLEEP -> { // switched off: arms down, head hanging
                rightArm.xRot = leftArm.xRot = 0;
                head.xRot = 0.45f;
                head.yRot = 0;
                head.zRot = 0.1f;
            }
            case BOOT -> {
                rightArm.xRot = leftArm.xRot = 0;
                head.xRot = head.yRot = 0;
            }
            case STUBBORN -> { // arms folded, looking away
                rightArm.xRot = leftArm.xRot = -1.2f;
                rightArm.zRot = -0.7f;
                leftArm.zRot = 0.7f;
                head.yRot += 0.6f;
                head.xRot -= 0.1f;
            }
            case GLITCH -> { // twitching all over
                head.zRot = Mth.sin(age * 3.1f) * 0.25f;
                head.yRot += Mth.sin(age * 2.3f) * 0.3f;
                rightArm.xRot = -1.8f + Mth.sin(age * 4.1f) * 0.6f;
                leftArm.xRot = -1.8f + Mth.cos(age * 3.7f) * 0.6f;
            }
            default -> {}
        }
        if (state.dancing) { // swaying to the music, arms up in turn
            float beat = age * 0.4f;
            body.yRot = Mth.sin(beat) * 0.35f;
            head.zRot = Mth.sin(beat) * 0.2f;
            rightArm.xRot = -2.2f + Mth.sin(beat * 1.25f) * 0.6f;
            leftArm.xRot = -2.2f - Mth.sin(beat * 1.25f) * 0.6f;
            rightArm.zRot = 0.3f;
            leftArm.zRot = -0.3f;
        }
        if (state.carrying && emotion != Emotion.GLITCH) { // holding its find out in front
            rightArm.xRot = leftArm.xRot = -1.1f;
            rightArm.zRot = leftArm.zRot = 0;
        }
    }

    /** Where an item in its hand goes: in the hand at the end of the arm. */
    @Override
    public void translateToHand(BleepRenderState state, HumanoidArm arm, PoseStack poseStack) {
        root.translateAndRotate(poseStack);
        body.translateAndRotate(poseStack);
        (arm == HumanoidArm.RIGHT ? rightArm : leftArm).translateAndRotate(poseStack);
        poseStack.translate((arm == HumanoidArm.RIGHT ? -1 : 1) / 16f, 0, 0);
    }
}
