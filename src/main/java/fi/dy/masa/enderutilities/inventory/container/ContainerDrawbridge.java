package fi.dy.masa.enderutilities.inventory.container;

import net.minecraft.entity.player.EntityPlayer;
import fi.dy.masa.enderutilities.inventory.container.base.ContainerTileLargeStacks;
import fi.dy.masa.enderutilities.inventory.container.base.MergeSlotRange;
import fi.dy.masa.enderutilities.inventory.slot.SlotItemHandlerGeneric;
import fi.dy.masa.enderutilities.tileentity.TileEntityDrawbridge;

public class ContainerDrawbridge extends ContainerTileLargeStacks
{
    private final TileEntityDrawbridge tedb;
    private final boolean advanced;
    private int lengthLast = -1;
    private int delayLast = -1;
    private int rsModeLast = -1;

    public ContainerDrawbridge(EntityPlayer player, TileEntityDrawbridge te)
    {
        super(player, te.getWrappedInventoryForContainer(player), te);
        this.tedb = te;
        this.advanced = te.isAdvanced();
        this.inventoryNonWrapped = te.getInventoryDrawbridge();

        this.detectAndSendChanges();
        this.reAddSlots();
    }

    private void reAddSlots()
    {
        this.inventorySlots.clear();
        this.inventoryItemStacks.clear();
        this.addCustomInventorySlots();
        this.addPlayerInventorySlots(8, this.advanced ? 128 : 54);
    }

    @Override
    protected void addCustomInventorySlots()
    {
        int posX = 17;
        int posY = 42;
        int slots = this.inventory.getSlots();

        this.customInventorySlots = new MergeSlotRange(this.inventorySlots.size(), slots);

        if (this.advanced)
        {
            for (int slot = 0, x = posX; slot < slots; slot++)
            {
                this.addSlotToContainer(new SlotItemHandlerGeneric(this.inventory, slot, x, posY));
                x += 18;

                if (slot % 8 == 7)
                {
                    x = posX;
                    posY += 18;
                }
            }
        }
        else
        {
            this.addSlotToContainer(new SlotItemHandlerGeneric(this.inventory, 0, 8, 21));
        }
    }

    @Override
    public void detectAndSendChanges()
    {
        if (this.isClient)
        {
            return;
        }

        int maxLength = this.tedb.getMaxLength();
        int delay = this.tedb.getDelay();
        int rsMode = this.tedb.getRedstoneModeIntValue();

        for (int i = 0; i < this.listeners.size(); i++)
        {
            if (maxLength != this.lengthLast)
            {
                this.listeners.get(i).sendWindowProperty(this, 0, maxLength);
            }

            if (delay != this.delayLast)
            {
                this.listeners.get(i).sendWindowProperty(this, 1, delay);
            }

            if (rsMode != this.rsModeLast)
            {
                this.listeners.get(i).sendWindowProperty(this, 2, rsMode);
            }
        }

        if (maxLength != this.lengthLast)
        {
            this.reAddSlots();
        }

        this.lengthLast = maxLength;
        this.delayLast = delay;
        this.rsModeLast = rsMode;

        super.detectAndSendChanges();
    }

    @Override
    public void updateProgressBar(int id, int data)
    {
        super.updateProgressBar(id, data);

        switch (id)
        {
            case 0:
                this.tedb.setMaxLength(data);
                this.reAddSlots();
                break;
            case 1:
                this.tedb.setDelay(data);
                break;
            case 2:
                this.tedb.setRedstoneMode(data);
                break;
        }
    }
}
