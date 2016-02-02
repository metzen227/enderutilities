package fi.dy.masa.enderutilities.proxy;

import net.minecraft.entity.player.EntityPlayer;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;

import fi.dy.masa.enderutilities.EnderUtilities;
import fi.dy.masa.enderutilities.entity.EntityEnderArrow;
import fi.dy.masa.enderutilities.entity.EntityEnderPearlReusable;
import fi.dy.masa.enderutilities.entity.EntityEndermanFighter;
import fi.dy.masa.enderutilities.event.*;
import fi.dy.masa.enderutilities.reference.ReferenceNames;
import fi.dy.masa.enderutilities.tileentity.TileEntityCreationStation;
import fi.dy.masa.enderutilities.tileentity.TileEntityEnderFurnace;
import fi.dy.masa.enderutilities.tileentity.TileEntityEnderInfuser;
import fi.dy.masa.enderutilities.tileentity.TileEntityEnergyBridge;
import fi.dy.masa.enderutilities.tileentity.TileEntityHandyChest;
import fi.dy.masa.enderutilities.tileentity.TileEntityMemoryChest;
import fi.dy.masa.enderutilities.tileentity.TileEntityToolWorkstation;
import fi.dy.masa.enderutilities.util.ChunkLoading;

public abstract class CommonProxy implements IProxy
{
    @Override
    public EntityPlayer getPlayerFromMessageContext(MessageContext ctx)
    {
        switch (ctx.side)
        {
            case SERVER:
                return ctx.getServerHandler().playerEntity;
            default:
                EnderUtilities.logger.warn("Invalid side in getPlayerFromMessageContext(): " + ctx.side);
                return null;
        }
    }

    @Override
    public void registerEntities()
    {
        int id = 0;
        EntityRegistry.registerModEntity(EntityEnderArrow.class, ReferenceNames.NAME_ENTITY_ENDER_ARROW, id++, EnderUtilities.instance, 64, 2, true);
        EntityRegistry.registerModEntity(EntityEnderPearlReusable.class, ReferenceNames.NAME_ENTITY_ENDER_PEARL_REUSABLE, id++, EnderUtilities.instance, 64, 2, true);
        EntityRegistry.registerModEntity(EntityEndermanFighter.class, ReferenceNames.NAME_ENTITY_ENDERMAN_FIGHTER, id++, EnderUtilities.instance, 64, 3, true);
    }

    @Override
    public void registerEventHandlers()
    {
        MinecraftForge.EVENT_BUS.register(new AnvilUpdateEventHandler());
        MinecraftForge.EVENT_BUS.register(new BlockEventHandler());
        MinecraftForge.EVENT_BUS.register(new EntityInteractEventHandler());
        MinecraftForge.EVENT_BUS.register(new GuiEventHandler());
        MinecraftForge.EVENT_BUS.register(new ItemPickupEventHandler());
        MinecraftForge.EVENT_BUS.register(new LivingDropsEventHandler());
        MinecraftForge.EVENT_BUS.register(new PlayerEventHandler());
        MinecraftForge.EVENT_BUS.register(new WorldEventHandler());
        MinecraftForge.EVENT_BUS.register(new TickHandler());
        MinecraftForge.EVENT_BUS.register(new FMLPlayerEventHandler());
        ForgeChunkManager.setForcedChunkLoadingCallback(EnderUtilities.instance, new ChunkLoading());
    }

    @Override
    public void registerFuelHandlers()
    {
        //GameRegistry.registerFuelHandler(new FuelHandler());
    }

    @Override
    public void registerTileEntities()
    {
        GameRegistry.registerTileEntity(TileEntityEnderFurnace.class, ReferenceNames.getPrefixedName(ReferenceNames.NAME_TILE_ENTITY_ENDER_FURNACE));
        GameRegistry.registerTileEntity(TileEntityToolWorkstation.class, ReferenceNames.getPrefixedName(ReferenceNames.NAME_TILE_ENTITY_TOOL_WORKSTATION));
        GameRegistry.registerTileEntity(TileEntityEnderInfuser.class, ReferenceNames.getPrefixedName(ReferenceNames.NAME_TILE_ENTITY_ENDER_INFUSER));
        GameRegistry.registerTileEntity(TileEntityEnergyBridge.class, ReferenceNames.getPrefixedName(ReferenceNames.NAME_TILE_ENTITY_ENERGY_BRIDGE));
        GameRegistry.registerTileEntity(TileEntityMemoryChest.class, ReferenceNames.getPrefixedName(ReferenceNames.NAME_TILE_ENTITY_MEMORY_CHEST));
        GameRegistry.registerTileEntity(TileEntityHandyChest.class, ReferenceNames.getPrefixedName(ReferenceNames.NAME_TILE_ENTITY_HANDY_CHEST));
        GameRegistry.registerTileEntity(TileEntityCreationStation.class, ReferenceNames.getPrefixedName(ReferenceNames.NAME_TILE_ENTITY_CREATION_STATION));
    }

    @Override
    public void registerKeyBindings()
    {
    }

    @Override
    public void registerRenderers()
    {
    }

    @Override
    public void setupReflection()
    {
    }

    @Override
    public boolean isShiftKeyDown()
    {
        return false;
    }

    @Override
    public boolean isControlKeyDown()
    {
        return false;
    }

    @Override
    public boolean isAltKeyDown()
    {
        return false;
    }
}
