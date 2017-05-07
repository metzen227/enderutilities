package fi.dy.masa.enderutilities.proxy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public interface IProxy
{
    public EntityPlayer getPlayerFromMessageContext(MessageContext ctx);

    public void playSound(int soundId, float pitch, float volume, boolean repeat, boolean stop, float x, float y, float z);

    public void registerColorHandlers();

    public void registerEntities();

    public void registerEventHandlers();

    public void registerKeyBindings();

    public void registerModels();

    public void registerRenderers();

    public void registerSounds();

    public void registerTileEntities();

    public boolean isShiftKeyDown();

    public boolean isControlKeyDown();

    public boolean isAltKeyDown();
}
