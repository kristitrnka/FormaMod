package com.forma.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/** Client-only fly camera used as the render view entity while the editor is open. Never added to the world. */
public class EditorCamera extends Entity {
    public float speed = 1.0f;
    private double velX, velY, velZ;

    public EditorCamera(World world, EntityPlayer from) {
        super(world);
        setSize(0.1f, 0.1f);
        noClip = true;
        setPositionAndRotation(from.posX, from.posY + from.getEyeHeight(), from.posZ, from.rotationYaw, from.rotationPitch);
        syncPrev();
    }

    @Override
    protected void entityInit() {}

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {}

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {}

    @Override
    public float getEyeHeight() {
        return 0f;
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    private void syncPrev() {
        prevPosX = lastTickPosX = posX;
        prevPosY = lastTickPosY = posY;
        prevPosZ = lastTickPosZ = posZ;
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
    }

    /** Mouse-look, same feel as vanilla. dx/dy are raw mouse deltas. */
    public void rotate(int dx, int dy) {
        float s = Minecraft.getMinecraft().gameSettings.mouseSensitivity * 0.6f + 0.2f;
        float k = s * s * s * 8.0f * 0.15f;
        rotationYaw += dx * k;
        rotationPitch = MathHelper.clamp(rotationPitch - dy * k, -89.9f, 89.9f);
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
    }

    /** Called once per client tick. */
    public void tickMovement(boolean keysEnabled) {
        syncPrev();
        float forward = 0, strafe = 0, up = 0;
        boolean fast = false;
        if (keysEnabled) {
            GameSettings gs = Minecraft.getMinecraft().gameSettings;
            if (down(gs.keyBindForward)) forward++;
            if (down(gs.keyBindBack)) forward--;
            if (down(gs.keyBindLeft)) strafe++;
            if (down(gs.keyBindRight)) strafe--;
            if (down(gs.keyBindJump)) up++;
            if (down(gs.keyBindSneak)) up--;
            fast = down(gs.keyBindSprint);
        }
        double accel = 0.35 * speed * (fast ? 3.0 : 1.0);
        double yaw = Math.toRadians(rotationYaw);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        velX += (-sin * forward + cos * strafe) * accel;
        velZ += (cos * forward + sin * strafe) * accel;
        velY += up * accel;
        velX *= 0.6;
        velY *= 0.6;
        velZ *= 0.6;
        setPosition(posX + velX, posY + velY, posZ + velZ);
    }

    private static boolean down(KeyBinding key) {
        int code = key.getKeyCode();
        return code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
    }
}
