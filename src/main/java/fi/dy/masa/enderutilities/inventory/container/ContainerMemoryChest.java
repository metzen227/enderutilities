package fi.dy.masa.enderutilities.inventory.container;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import fi.dy.masa.enderutilities.inventory.ICustomSlotSync;
import fi.dy.masa.enderutilities.inventory.MergeSlotRange;
import fi.dy.masa.enderutilities.inventory.slot.SlotItemHandlerGeneric;
import fi.dy.masa.enderutilities.network.PacketHandler;
import fi.dy.masa.enderutilities.network.message.MessageSyncCustomSlot;
import fi.dy.masa.enderutilities.tileentity.TileEntityMemoryChest;

public class ContainerMemoryChest extends ContainerTileEntityInventory implements ICustomSlotSync
{
    protected TileEntityMemoryChest temc;
    protected List<ItemStack> templateStacksLast;
    protected long templateMask;

    public ContainerMemoryChest(EntityPlayer player, TileEntityMemoryChest te)
    {
        super(player, te);
        this.temc = te;
        this.templateStacksLast = new ArrayList<ItemStack>();

        this.addCustomInventorySlots();
        this.addPlayerInventorySlots(8, this.inventorySlots.get(this.inventorySlots.size() - 1).yDisplayPosition + 32);
    }

    @Override
    protected void addCustomInventorySlots()
    {
        int customInvStart = this.inventorySlots.size();
        int posX = 8;
        int posY = 26;

        int tier = this.temc.getStorageTier();
        int rows = TileEntityMemoryChest.INV_SIZES[tier] / 9;

        for (int i = 0; i < rows; i++)
        {
            for (int j = 0; j < 9; j++)
            {
                this.addSlotToContainer(new SlotItemHandlerGeneric(this.inventory, i * 9 + j, posX + j * 18, posY + i * 18));
            }
        }

        this.customInventorySlots = new MergeSlotRange(customInvStart, this.inventorySlots.size() - customInvStart);
    }

    @Override
    public ItemStack slotClick(int slotNum, int dragType, ClickType clickType, EntityPlayer player)
    {
        // Middle click
        if (clickType == ClickType.CLONE && dragType == 2 && slotNum >= 0 && slotNum < this.inventory.getSlots())
        {
            int invSlotNum = this.getSlot(slotNum) != null ? this.getSlot(slotNum).getSlotIndex() : -1;

            if (invSlotNum != -1)
            {
                ItemStack stackSlot = this.inventory.getStackInSlot(invSlotNum);
                ItemStack stackCursor = this.player.inventory.getItemStack();

                if (stackCursor != null && stackSlot == null && (this.temc.getTemplateMask() & (1 << invSlotNum)) == 0)
                {
                    this.temc.setTemplateStack(invSlotNum, stackCursor);
                }
                else
                {
                    this.temc.setTemplateStack(invSlotNum, stackSlot);
                }

                this.temc.toggleTemplateMask(invSlotNum);
            }

            return null;
        }

        ItemStack stack = super.slotClick(slotNum, dragType, clickType, player);

        this.detectAndSendChanges();

        return stack;
    }

    public TileEntityMemoryChest getTileEntity()
    {
        return this.temc;
    }

    @Override
    protected Slot addSlotToContainer(Slot slot)
    {
        this.templateStacksLast.add(null);

        return super.addSlotToContainer(slot);
    }

    @Override
    public void putCustomStack(int typeId, int slotNum, ItemStack stack)
    {
        this.temc.setTemplateStack(slotNum, stack);
    }

    @Override
    public void detectAndSendChanges()
    {
        super.detectAndSendChanges();

        if (this.temc.getWorld().isRemote == true)
        {
            return;
        }

        for (int i = 0; i < this.templateStacksLast.size(); i++)
        {
            ItemStack currentStack = this.temc.getTemplateStack(i);
            ItemStack prevStack = this.templateStacksLast.get(i);

            if (ItemStack.areItemStacksEqual(prevStack, currentStack) == false)
            {
                prevStack = currentStack != null ? currentStack.copy() : null;
                this.templateStacksLast.set(i, prevStack);

                for (int j = 0; j < this.listeners.size(); j++)
                {
                    ICrafting icrafting = (ICrafting)this.listeners.get(j);
                    if (icrafting instanceof EntityPlayerMP)
                    {
                        PacketHandler.INSTANCE.sendTo(new MessageSyncCustomSlot(this.windowId, 0, i, prevStack), (EntityPlayerMP)icrafting);
                    }
                }
            }
        }

        long mask = this.temc.getTemplateMask();

        for (int j = 0; j < this.listeners.size(); ++j)
        {
            if (this.templateMask != mask)
            {
                ICrafting icrafting = (ICrafting)this.listeners.get(j);
                // Send the long in 16-bit pieces because of the network packet limitation in MP
                icrafting.sendProgressBarUpdate(this, 0, (int)(mask & 0xFFFF));
                icrafting.sendProgressBarUpdate(this, 1, (int)((mask >> 16) & 0xFFFF));
                icrafting.sendProgressBarUpdate(this, 2, (int)((mask >> 32) & 0xFFFF));
                icrafting.sendProgressBarUpdate(this, 3, (int)((mask >> 48) & 0xFFFF));
            }
        }

        this.templateMask = mask;
    }

    @Override
    public void updateProgressBar(int var, int val)
    {
        if (var >= 0 && var <= 3)
        {
            this.templateMask &= ~(0xFFFFL << (var * 16));
            this.templateMask |= (((long)val) << (var * 16));
            this.temc.setTemplateMask(this.templateMask);
        }
    }
}