package fi.dy.masa.enderutilities.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandler;
import fi.dy.masa.enderutilities.gui.client.GuiEnderInfuser;
import fi.dy.masa.enderutilities.inventory.ItemStackHandlerTileEntity;
import fi.dy.masa.enderutilities.inventory.container.ContainerEnderInfuser;
import fi.dy.masa.enderutilities.inventory.wrapper.ItemHandlerWrapperSelective;
import fi.dy.masa.enderutilities.item.base.IChargeable;
import fi.dy.masa.enderutilities.item.base.IModular;
import fi.dy.masa.enderutilities.item.base.ItemModule.ModuleType;
import fi.dy.masa.enderutilities.reference.ReferenceNames;
import fi.dy.masa.enderutilities.util.nbt.UtilItemModular;

public class TileEntityEnderInfuser extends TileEntityEnderUtilitiesInventory implements ITickable
{
    private static final int SLOT_MATERIAL = 0;
    private static final int SLOT_CAP_IN   = 1;
    private static final int SLOT_CAP_OUT  = 2;
    public static final int AMOUNT_PER_ENDERPEARL = 250;
    public static final int AMOUNT_PER_ENDEREYE = 500;
    public static final int ENDER_CHARGE_PER_MILLIBUCKET = 4;
    public static final int MAX_AMOUNT = 4000;
    public int amountStored;
    public int meltingProgress; // 0..100, 100 being 100% done; input item consumed and stored amount increased @ 100
    public boolean isCharging;
    public int chargeableItemCapacity;
    public int chargeableItemStartingCharge;
    public int chargeableItemCurrentCharge;

    public TileEntityEnderInfuser()
    {
        super(ReferenceNames.NAME_TILE_ENTITY_ENDER_INFUSER);
        this.itemHandlerBase = new ItemStackHandlerTileEntity(3, this);
        this.itemHandlerExternal = new ItemHandlerWrapperEnderInfuser(this.getBaseItemHandler());
    }

    @Override
    public void readFromNBTCustom(NBTTagCompound nbt)
    {
        super.readFromNBTCustom(nbt);

        // The stored amount is stored in a Fluid-compatible tag already,
        // just in case I ever decide to change the "Ender Goo" (or Resonant Ender?)
        // to actually be a Fluid, possibly compatible with Resonant Ender.
        if (nbt.hasKey("Fluid", Constants.NBT.TAG_COMPOUND))
        {
            this.amountStored = nbt.getCompoundTag("Fluid").getInteger("Amount");
        }
        this.meltingProgress = nbt.getByte("Progress");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt)
    {
        super.writeToNBT(nbt);

        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Amount", this.amountStored);
        tag.setString("FluidName", "ender"); // For future compatibility
        nbt.setTag("Fluid", tag);
        nbt.setByte("Progress", (byte)this.meltingProgress);

        return nbt;
    }

    @Override
    public void update()
    {
        if (this.getWorld().isRemote)
        {
            return;
        }

        boolean dirty = false;

        // Melt Ender Pearls or Eyes of Ender into... emm... Ender Goo?
        if (this.getBaseItemHandler().getStackInSlot(SLOT_MATERIAL).isEmpty() == false)
        {
            Item item = this.getBaseItemHandler().getStackInSlot(SLOT_MATERIAL).getItem();
            int amount = 0;

            if (item == Items.ENDER_PEARL)
            {
                amount = AMOUNT_PER_ENDERPEARL;
            }
            else if (item == Items.ENDER_EYE)
            {
                amount = AMOUNT_PER_ENDEREYE;
            }

            if (amount > 0 && (amount + this.amountStored <= MAX_AMOUNT))
            {
                this.meltingProgress += 2;

                if (this.meltingProgress >= 100)
                {
                    this.amountStored += amount;
                    this.meltingProgress = 0;
                    this.getBaseItemHandler().extractItem(SLOT_MATERIAL, 1, false);
                }

                dirty = true;
            }
        }
        else
        {
            this.meltingProgress = 0;
        }

        // NOTE: This does break the IItemHandler contract of not modifying the items
        // you get from getStackInSlot(), but since this is internal usage, whatever...
        // Otherwise we would be constantly extracting and inserting it back.
        ItemStack inputStack = this.getBaseItemHandler().getStackInSlot(SLOT_CAP_IN);

        // Charge IChargeable items with the Ender Goo
        if (inputStack.isEmpty() == false)
        {
            ItemStack chargeableStack = inputStack;
            Item item = inputStack.getItem();

            if (item instanceof IChargeable || item instanceof IModular)
            {
                boolean isModular = false;
                IChargeable iChargeable = null;

                if (item instanceof IChargeable)
                {
                    iChargeable = (IChargeable) item;
                }
                else // if (item instanceof IModular)
                {
                    chargeableStack = UtilItemModular.getSelectedModuleStack(inputStack, ModuleType.TYPE_ENDERCAPACITOR);

                    if (chargeableStack.isEmpty() == false && chargeableStack.getItem() instanceof IChargeable)
                    {
                        iChargeable = (IChargeable) chargeableStack.getItem();
                        isModular = true;
                    }
                }

                if (iChargeable != null && this.amountStored > 0)
                {
                    int charge = (this.amountStored >= 10 ? 10 : this.amountStored) * ENDER_CHARGE_PER_MILLIBUCKET;
                    int filled = iChargeable.addCharge(chargeableStack, charge, false);

                    if (filled > 0)
                    {
                        // Just started charging an item, grab the current charge level and capacity for progress bar updating
                        if (this.isCharging == false)
                        {
                            this.chargeableItemCapacity = iChargeable.getCapacity(chargeableStack);
                            this.chargeableItemStartingCharge = iChargeable.getCharge(chargeableStack);
                            this.chargeableItemCurrentCharge = this.chargeableItemStartingCharge;
                            this.isCharging = true;
                        }

                        if (filled < charge)
                        {
                            charge = filled;
                        }

                        charge = iChargeable.addCharge(chargeableStack, charge, true);
                        int used = (int)Math.ceil(charge / ENDER_CHARGE_PER_MILLIBUCKET);
                        this.amountStored -= used;
                        this.chargeableItemCurrentCharge += charge; // = item.getCharge(capacitorStack);
                        dirty = true;

                        if (isModular)
                        {
                            UtilItemModular.setSelectedModuleStack(inputStack, ModuleType.TYPE_ENDERCAPACITOR, chargeableStack);
                        }

                        // Put the item back into the slot
                        //this.itemHandler.insertItem(SLOT_CAP_IN, inputStack, false);
                        //this.itemHandler.setStackInSlot(SLOT_CAP_IN, inputStack);
                    }
                }

                // A fully charged item is in the input slot, move it to the output slot, if possible
                if (iChargeable != null && iChargeable.getCharge(chargeableStack) >= iChargeable.getCapacity(chargeableStack))
                {
                    this.isCharging = false;
                    this.chargeableItemCurrentCharge = 0;
                    this.chargeableItemStartingCharge = 0;
                    this.chargeableItemCapacity = 0;

                    // Move the item from the input slot to the output slot
                    if (this.getBaseItemHandler().insertItem(SLOT_CAP_OUT, this.getBaseItemHandler().extractItem(SLOT_CAP_IN, 1, true), true).isEmpty())
                    {
                        this.getBaseItemHandler().insertItem(SLOT_CAP_OUT, this.getBaseItemHandler().extractItem(SLOT_CAP_IN, 1, false), false);
                        dirty = true;
                    }
                }
            }
        }
        else
        {
            this.isCharging = false;
            this.chargeableItemCurrentCharge = 0;
            this.chargeableItemStartingCharge = 0;
            this.chargeableItemCapacity = 0;
        }

        if (dirty)
        {
            this.markDirty();
        }
    }

    private class ItemHandlerWrapperEnderInfuser extends ItemHandlerWrapperSelective
    {
        public ItemHandlerWrapperEnderInfuser(IItemHandler baseHandler)
        {
            super(baseHandler);
        }

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack)
        {
            if (stack.isEmpty())
            {
                return false;
            }

            // Only accept chargeable items to the item input slot
            if (slot == SLOT_CAP_IN)
            {
                Item item = stack.getItem();
                return item instanceof IChargeable ||
                       (item instanceof IModular && ((IModular) item).getInstalledModuleCount(stack, ModuleType.TYPE_ENDERCAPACITOR) > 0);
            }

            // Only allow Ender Pearls and Eyes of Ender to the material slot
            return slot == SLOT_MATERIAL && (stack.getItem() == Items.ENDER_PEARL || stack.getItem() == Items.ENDER_EYE);
        }

        @Override
        public boolean canExtractFromSlot(int slot)
        {
            return slot == SLOT_CAP_OUT;
        }
    }

    @Override
    public ContainerEnderInfuser getContainer(EntityPlayer player)
    {
        return new ContainerEnderInfuser(player, this);
    }

    @Override
    public Object getGui(EntityPlayer player)
    {
        return new GuiEnderInfuser(this.getContainer(player), this);
    }
}
