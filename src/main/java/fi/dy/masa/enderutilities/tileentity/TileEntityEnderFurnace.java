package fi.dy.masa.enderutilities.tileentity;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import fi.dy.masa.enderutilities.gui.client.GuiEnderFurnace;
import fi.dy.masa.enderutilities.inventory.ItemStackHandlerTileEntity;
import fi.dy.masa.enderutilities.inventory.container.ContainerEnderFurnace;
import fi.dy.masa.enderutilities.inventory.wrapper.ItemHandlerWrapperSelective;
import fi.dy.masa.enderutilities.reference.ReferenceNames;
import fi.dy.masa.enderutilities.util.InventoryUtils;

public class TileEntityEnderFurnace extends TileEntityEnderUtilitiesInventory implements ITickable
{
    // The values that define how fuels burn and items smelt
    public static final int COOKTIME_INC_NOFUEL = 1; // No fuel mode: 60 seconds per item
    public static final int COOKTIME_INC_SLOW = 20; // Slow/eco mode: 3 seconds per item
    public static final int COOKTIME_INC_FAST = 60; // Fast mode: 1 second per item (3x as fast)
    public static final int COOKTIME_DEFAULT = 1200; // Base cooktime per item: 3 seconds on slow

    public static final int BURNTIME_USAGE_SLOW = 20; // Slow/eco mode base usage
    public static final int BURNTIME_USAGE_FAST = 120; // Fast mode: use fuel 6x faster over time

    public static final int OUTPUT_INTERVAL = 20; // Only try outputting items to an Ender Chest once every 1 seconds, to try to reduce server load

    protected static final int[] SLOTS_SIDES = new int[] {0, 1, 2};
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;

    private final IItemHandler inventoryWrapperSmeltables;
    private final IItemHandler inventoryWrapperNonSmeltables;
    public boolean fastMode;
    public boolean outputToEnderChest;
    private ItemStack smeltingResultCache = ItemStack.EMPTY;
    private boolean inputDirty;
    //private boolean fuelDirty;

    public int burnTimeRemaining;   // Remaining burn time from the currently burning fuel
    public int burnTimeFresh;       // The time the currently burning fuel will burn in total
    public int cookTime;            // The time the currently cooking item has been cooking for

    public boolean isBurningLast;
    public boolean isCookingLast;

    private int timer;

    public TileEntityEnderFurnace()
    {
        super(ReferenceNames.NAME_TILE_ENTITY_ENDER_FURNACE);
        this.inputDirty = true;
        //this.fuelDirty = true;
        this.fastMode = false;
        this.outputToEnderChest = false;
        this.burnTimeRemaining = 0;
        this.cookTime = 0;
        this.timer = 0;

        this.itemHandlerBase = new ItemStackHandlerTileEntity(0, 3, 1024, true, "Items", this);
        this.inventoryWrapperSmeltables = new ItemHandlerWrapperEnderFurnaceSmeltables(this.getBaseItemHandler(), this);
        this.inventoryWrapperNonSmeltables = new ItemHandlerWrapperEnderFurnaceNonSmeltables(this.getBaseItemHandler(), this);

        // This is only used for the container
        this.itemHandlerExternal = new ItemHandlerWrapperEnderFurnace(this.getBaseItemHandler(), this);
    }

    @Override
    public void readFromNBTCustom(NBTTagCompound nbt)
    {
        super.readFromNBTCustom(nbt);

        byte flags              = nbt.getByte("Flags"); // Flags
        this.fastMode           = (flags & 0x01) == 0x01;
        this.outputToEnderChest = (flags & 0x02) == 0x02;
        this.burnTimeRemaining  = nbt.getInteger("BurnTimeRemaining");
        this.burnTimeFresh      = nbt.getInteger("BurnTimeFresh");
        this.cookTime           = nbt.getInteger("CookTime");

        this.inputDirty = true;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt)
    {
        super.writeToNBT(nbt);

        byte flags = 0;

        if (this.fastMode)
        {
            flags |= 0x01;
        }
        if (this.outputToEnderChest)
        {
            flags |= 0x02;
        }

        nbt.setByte("Flags", flags);
        nbt.setShort("BurnTimeRemaining", (short)this.burnTimeRemaining);
        nbt.setShort("BurnTimeFresh", (short)this.burnTimeFresh);
        nbt.setShort("CookTime", (short)this.cookTime);

        return nbt;
    }

    @Override
    public NBTTagCompound getUpdatePacketTag(NBTTagCompound nbt)
    {
        nbt = super.getUpdatePacketTag(nbt);

        byte flags = 0;
        // 0x10: is cooking something, 0x20: is burning fuel, 0x40: fast mode active
        if (canSmelt()) { flags |= 0x10; }
        if (isBurning()) { flags |= 0x20; }
        if (this.fastMode) { flags |= 0x40; }
        nbt.setByte("f", flags);

        return nbt;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag)
    {
        byte flags = tag.getByte("f");
        this.isCookingLast = (flags & 0x10) == 0x10;
        this.isBurningLast = (flags & 0x20) == 0x20;
        this.fastMode = (flags & 0x40) == 0x40;

        super.handleUpdateTag(tag);

        this.getWorld().checkLight(this.getPos());
    }

    public boolean isBurning()
    {
        // This returns if the furnace is actually burning fuel at the moment
        return this.burnTimeRemaining > 0;
    }

    /**
     * Updates the cached smelting result for the current input item, if the input has changed since last caching the result.
     */
    private void updateSmeltingResult()
    {
        if (this.inputDirty)
        {
            if (this.getBaseItemHandler().getStackInSlot(SLOT_INPUT).isEmpty() == false)
            {
                this.smeltingResultCache = FurnaceRecipes.instance().getSmeltingResult(this.getBaseItemHandler().getStackInSlot(SLOT_INPUT));
            }
            else
            {
                this.smeltingResultCache = ItemStack.EMPTY;
            }

            this.inputDirty = false;
        }
    }

    /**
     * Updates the cached fuel value for the current item in the fuel slot, if it has changed since last caching it.
     */
    /*private void updateFuelCache()
    {
        if (this.fuelDirty)
        {
            if (this.itemHandler.getStackInSlot(SLOT_FUEL) != null)
            {
                this.fuelBurnTimeCache = 0;
            }
            else
            {
                this.fuelBurnTimeCache = 0;
            }

            this.fuelDirty = false;
        }
    }*/

    @Override
    public void update()
    {
        if (this.getWorld().isRemote)
        {
            return;
        }

        this.updateSmeltingResult();

        boolean dirty = false;
        boolean canSmelt = this.canSmelt();
        boolean hasFuel = this.hasFuelAvailable();
        int cookTimeIncrement = COOKTIME_INC_SLOW;

        if (this.burnTimeRemaining == 0 && hasFuel == false)
        {
            cookTimeIncrement = COOKTIME_INC_NOFUEL;
        }
        else if (this.fastMode)
        {
            cookTimeIncrement = COOKTIME_INC_FAST;
        }

        // The furnace is currently burning fuel
        if (this.burnTimeRemaining > 0)
        {
            int btUse = (this.fastMode ? BURNTIME_USAGE_FAST : BURNTIME_USAGE_SLOW);

            // Not enough fuel burn time remaining for the elapsed tick
            if (btUse > this.burnTimeRemaining)
            {
                if (hasFuel && canSmelt)
                {
                    this.burnTimeRemaining += consumeFuelItem();
                    hasFuel = this.hasFuelAvailable();
                }
                // Running out of fuel, scale the cook progress according to the elapsed burn time
                else
                {
                    cookTimeIncrement = (this.burnTimeRemaining * cookTimeIncrement) / btUse;
                    btUse = this.burnTimeRemaining;
                }
            }

            this.burnTimeRemaining -= btUse;
            dirty = true;
        }
        // Furnace wasn't burning, but it now has fuel and smeltable items, start burning/smelting
        else if (canSmelt && hasFuel)
        {
            this.burnTimeRemaining += this.consumeFuelItem();
            hasFuel = this.hasFuelAvailable();
            dirty = true;
        }

        // Valid items to smelt, room in output
        if (canSmelt)
        {
            this.cookTime += cookTimeIncrement;

            // One item done smelting
            if (this.cookTime >= COOKTIME_DEFAULT)
            {
                this.smeltItem();
                canSmelt = this.canSmelt();

                // We can smelt the next item and we "overcooked" the last one, carry over the extra progress
                if (canSmelt && this.cookTime > COOKTIME_DEFAULT)
                {
                    this.cookTime -= COOKTIME_DEFAULT;
                }
                else // No more items to smelt or didn't overcook
                {
                    this.cookTime = 0;
                }
            }

            // If the current fuel ran out and we still have items to cook, consume the next fuel item
            if (this.burnTimeRemaining == 0 && hasFuel && canSmelt)
            {
                this.burnTimeRemaining += consumeFuelItem();
            }

            dirty = true;
        }
        // Can't smelt anything at the moment, rewind the cooking progress at half the speed of normal cooking
        else if (this.cookTime > 0)
        {
            this.cookTime -= Math.min(this.cookTime, COOKTIME_INC_SLOW / 2);
            dirty = true;
        }

        // Output to Ender Chest enabled
        if (this.outputToEnderChest && this.getBaseItemHandler().getStackInSlot(SLOT_OUTPUT).isEmpty() == false && ++this.timer >= OUTPUT_INTERVAL)
        {
            if (this.moveItemsToEnderChest())
            {
                dirty = true;
            }

            this.timer = 0;
        }

        if (dirty)
        {
            this.markDirty();
        }

        // Check if we need to sync some stuff to the clients
        if (this.isBurningLast != this.isBurning() || this.isCookingLast != canSmelt)
        {
            this.notifyBlockUpdate(this.getPos());
            this.getWorld().checkLight(this.getPos());
        }

        this.isBurningLast = this.isBurning();
        this.isCookingLast = canSmelt;
    }

    /**
     * Turn one item from the furnace input slot into a smelted item in the furnace output buffer.
     */
    public void smeltItem()
    {
        if (this.canSmelt())
        {
            this.getBaseItemHandler().insertItem(SLOT_OUTPUT, this.smeltingResultCache.copy(), false);
            this.getBaseItemHandler().extractItem(SLOT_INPUT, 1, false);

            if (this.getBaseItemHandler().getStackInSlot(SLOT_INPUT).isEmpty())
            {
                this.inputDirty = true;
            }
        }
    }

    /**
     * Consumes one fuel item or one dose of fluid fuel. Sets the burnTimeFresh field to the amount of burn time gained.
     * @return returns the amount of furnace burn time that was gained from the fuel
     */
    private int consumeFuelItem()
    {
        int burnTime = consumeFuelItem(this.getBaseItemHandler(), SLOT_FUEL, false);

        if (burnTime > 0)
        {
            this.burnTimeFresh = burnTime;
        }

        return burnTime;
    }

    public static int consumeFuelItem(IItemHandler fuelInv, int fuelSlot, boolean simulate)
    {
        ItemStack stack = fuelInv.getStackInSlot(fuelSlot);

        if (stack.isEmpty())
        {
            return 0;
        }

        int burnTime = 0;

        if (itemContainsFluidFuel(stack))
        {
            // Can't return the drained container if there is more than one item in the slot...
            if (stack.getCount() > 1)
            {
                return 0;
            }

            stack = fuelInv.extractItem(fuelSlot, 1, simulate);
            burnTime = consumeFluidFuelDosage(stack);
        }
        else
        {
            burnTime = getItemBurnTime(stack);

            if (burnTime == 0 || (stack.getCount() > 1 && stack.getItem().getContainerItem(stack).isEmpty() == false))
            {
                return 0;
            }

            stack = fuelInv.extractItem(fuelSlot, 1, simulate);
            stack = stack.getItem().getContainerItem(stack);
        }

        // Put the fuel/fluid container item back
        if (simulate == false && stack.isEmpty() == false)
        {
            fuelInv.insertItem(fuelSlot, stack, false);
        }

        return burnTime;
    }

    /**
     * Moves as many items from the output slot to the owner's vanilla Ender Chest as possible.
     * Tries to first fill matching stacks to their max, then puts items into the first empty slot.
     * @return true if some items were moved
     */
    private boolean moveItemsToEnderChest()
    {
        if (this.getBaseItemHandler().getStackInSlot(SLOT_OUTPUT).isEmpty() ||
            this.ownerData == null || this.ownerData.getOwnerUUID() == null)
        {
            return false;
        }

        EntityPlayer player = this.getWorld().getPlayerEntityByUUID(this.ownerData.getOwnerUUID());

        if (player == null)
        {
            return false;
        }
        // Player is online

        ItemStack stack = this.getBaseItemHandler().extractItem(SLOT_OUTPUT, 64, false);

        if (stack.isEmpty())
        {
            return false;
        }

        int origSize = stack.getCount();
        IItemHandler inv = new InvWrapper(player.getInventoryEnderChest());
        stack = InventoryUtils.tryInsertItemStackToInventory(inv, stack);

        if (stack.isEmpty())
        {
            return true;
        }

        boolean movedItems = origSize != stack.getCount();
        this.getBaseItemHandler().insertItem(SLOT_OUTPUT, stack, false);

        return movedItems;
    }

    /**
     * Checks if there is a valid fuel item in the fuel slot.
     * @return true if the fuel slot has an item that can be used as fuel
     */
    private boolean hasFuelAvailable()
    {
        return consumeFuelItem(this.getBaseItemHandler(), SLOT_FUEL, true) > 0;
    }

    /**
     * Returns true if the furnace can smelt an item. Checks the input slot for valid smeltable items and the output buffer
     * for an equal item and free space or empty buffer. Does not check the fuel.
     * @return true if input and output item stacks allow the current item to be smelted
     */
    public boolean canSmelt()
    {
        if (this.getBaseItemHandler().getStackInSlot(SLOT_INPUT).isEmpty() || this.smeltingResultCache.isEmpty())
        {
            return false;
        }

        return this.getBaseItemHandler().insertItem(SLOT_OUTPUT, this.smeltingResultCache, true).isEmpty();
    }

    /**
     * Returns the number of ticks that the supplied fuel item will keep the furnace burning, or 0 if the item isn't fuel
     * @param stack
     * @return
     */
    private static int getItemBurnTime(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return 0;
        }

        int burnTime = ForgeEventFactory.getItemBurnTime(stack) * COOKTIME_DEFAULT * 3 / 400;

        if (burnTime >= 0)
        {
            return burnTime;
        }

        Item item = stack.getItem();

        if (item instanceof ItemBlock && Block.getBlockFromItem(item) != Blocks.AIR)
        {
            Block block = Block.getBlockFromItem(item);
            if (block.getDefaultState().getMaterial() == Material.WOOD) { return COOKTIME_DEFAULT * 225 / 100; }
            if (block == Blocks.COAL_BLOCK) { return COOKTIME_DEFAULT * 120; }
            if (block == Blocks.WOODEN_SLAB) { return COOKTIME_DEFAULT * 45 / 40; }
            if (block == Blocks.SAPLING) return COOKTIME_DEFAULT * 3 / 4;
        }
        else
        {
            if (item == Items.COAL) return COOKTIME_DEFAULT * 12;
            if (item == Items.BLAZE_ROD) return COOKTIME_DEFAULT * 18;

            if (item == Items.LAVA_BUCKET) return COOKTIME_DEFAULT * 150;
            if (item == Items.STICK) return COOKTIME_DEFAULT * 3 / 4;
            if (item instanceof ItemTool && ((ItemTool)item).getToolMaterialName().equals("WOOD")) return COOKTIME_DEFAULT * 15 / 10;
            if (item instanceof ItemSword && ((ItemSword)item).getToolMaterialName().equals("WOOD")) return COOKTIME_DEFAULT * 15 / 10;
            if (item instanceof ItemHoe && ((ItemHoe)item).getMaterialName().equals("WOOD")) return COOKTIME_DEFAULT * 15 / 10;

            // Ender Furnace custom fuels
            if (item == Items.BLAZE_POWDER) return COOKTIME_DEFAULT * 9;
            if (item == Items.ENDER_PEARL) { return COOKTIME_DEFAULT * 8; }
            if (item == Items.ENDER_EYE) { return COOKTIME_DEFAULT * 17; }
        }

        return 0;
    }

    /**
     * Uses one dose (1000 mB) of fluid fuel, returns the amount of burn time that was gained from it.
     * @param stack
     * @return burn time gained, in ticks
     */
    private static int consumeFluidFuelDosage(ItemStack stack)
    {
        if (itemContainsFluidFuel(stack) == false)
        {
            return 0;
        }

        // All the null checks happened already in itemContainsFluidFuel()
        IFluidHandler handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);

        // Consume max 1000 mB per use.
        FluidStack fluidStack = handler.drain(new FluidStack(FluidRegistry.LAVA, Fluid.BUCKET_VOLUME), true);

        //System.out.printf("consumeFluidFuelDosageFluidCapability: %s - %d\n", (fluidStack != null ? fluidStack.getFluid().getName() : "null"), fluidStack != null ? fluidStack.amount : 0);
        // 1.5 times vanilla lava fuel value (150 items per bucket)
        return fluidStack != null ? (fluidStack.amount * 15 * COOKTIME_DEFAULT / 100) : 0;
    }

    /**
     * Checks if the given ItemStack contains a valid fluid fuel source for the furnace.
     * Valid fuels are currently just lava.
     * @param stack
     * @return
     */
    private static boolean itemContainsFluidFuel(ItemStack stack)
    {
        if (stack.isEmpty() || stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null) == false)
        {
            return false;
        }

        IFluidHandler handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);

        if (handler == null)
        {
            return false;
        }

        FluidStack fluidStack = handler.drain(Fluid.BUCKET_VOLUME, false);
        //System.out.printf("itemContainsFluidFuelFluidCapability: %s - %d\n", (fluidStack != null ? fluidStack.getFluid().getName() : "null"), fluidStack != null ? fluidStack.amount : 0);

        if (fluidStack == null || fluidStack.amount <= 0)
        {
            return false;
        }

        return fluidStack.getFluid() == FluidRegistry.LAVA;
    }

    /**
     * Check if the given item works as a fuel source in this furnace
     * @param stack
     * @return
     */
    public static boolean isItemFuel(ItemStack stack)
    {
        return itemContainsFluidFuel(stack) || getItemBurnTime(stack) > 0;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing)
    {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
        {
            if (facing == EnumFacing.UP)
            {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.inventoryWrapperSmeltables);
            }
            else
            {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.inventoryWrapperNonSmeltables);
            }
        }

        return super.getCapability(capability, facing);
    }

    private class ItemHandlerWrapperEnderFurnace extends ItemHandlerWrapperSelective
    {
        private final TileEntityEnderFurnace teef;

        public ItemHandlerWrapperEnderFurnace(IItemHandler baseHandler, TileEntityEnderFurnace te)
        {
            super(baseHandler);
            this.teef = te;
        }

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack)
        {
            if (stack.isEmpty())
            {
                return false;
            }

            if (slot == SLOT_INPUT)
            {
                return FurnaceRecipes.instance().getSmeltingResult(stack).isEmpty() == false;
            }

            return slot == SLOT_FUEL && isItemFuel(stack);
        }

        @Override
        public boolean canExtractFromSlot(int slot)
        {
            return (slot == SLOT_FUEL && isItemFuel(this.getStackInSlot(slot)) == false) ||
                   (slot == SLOT_OUTPUT && this.teef.outputToEnderChest == false);
        }
    }

    /**
     * Wrapper inventory for the fuel slot and output slot.
     * The backing inventory is a 3-slot inventory, thus the slot indices are shifted by one here to account for
     * the slot 0 in the backing inventory being the input/smeltables slot.
     */
    private class ItemHandlerWrapperEnderFurnaceNonSmeltables extends ItemHandlerWrapperSelective
    {
        private final TileEntityEnderFurnace teef;

        public ItemHandlerWrapperEnderFurnaceNonSmeltables(IItemHandler baseHandler, TileEntityEnderFurnace te)
        {
            super(baseHandler);
            this.teef = te;
        }

        @Override
        public int getSlots()
        {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot)
        {
            return super.getStackInSlot(slot + 1);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate)
        {
            if (this.isItemValidForSlot(slot, stack))
            {
                return this.baseHandler.insertItem(slot + 1, stack, simulate);
            }

            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate)
        {
            if (this.canExtractFromSlot(slot))
            {
                return this.baseHandler.extractItem(slot + 1, amount, simulate);
            }

            return ItemStack.EMPTY;
        }

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack)
        {
            if (stack.isEmpty())
            {
                return false;
            }

            return slot == 0 && isItemFuel(stack);
        }

        @Override
        public boolean canExtractFromSlot(int slot)
        {
            return (slot == 0 && isItemFuel(this.getStackInSlot(slot)) == false) ||
                   (slot == 1 && this.teef.outputToEnderChest == false);
        }
    }

    private class ItemHandlerWrapperEnderFurnaceSmeltables extends ItemHandlerWrapperSelective
    {
        public ItemHandlerWrapperEnderFurnaceSmeltables(IItemHandler baseHandler, TileEntityEnderFurnace te)
        {
            super(baseHandler);
        }

        @Override
        public int getSlots()
        {
            return 1;
        }

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack)
        {
            return stack.isEmpty() == false && FurnaceRecipes.instance().getSmeltingResult(stack).isEmpty() == false;
        }

        @Override
        public boolean canExtractFromSlot(int slot)
        {
            return false;
        }
    }

    @Override
    public void inventoryChanged(int inventoryId, int slot)
    {
        if (slot == SLOT_INPUT)
        {
            this.inputDirty = true;
        }
    }

    /**
     * Returns an integer between 0 and the passed value representing how close the current item is to being completely cooked
     * @param i
     * @return
     */
    public int getCookProgressScaled(int i)
    {
        return this.cookTime * i / COOKTIME_DEFAULT;
    }

    /**
     * Returns an integer between 0 and the passed value representing how much burn time is left on the current fuel
     * item, where 0 means that the item is exhausted and the passed value means that the item is fresh
     * @param i
     * @return
     */
    public int getBurnTimeRemainingScaled(int i)
    {
        if (this.burnTimeFresh == 0)
        {
            return 0;
        }

        return this.burnTimeRemaining * i / this.burnTimeFresh;
    }

    @Override
    public void performGuiAction(EntityPlayer player, int action, int element)
    {
        // 0: Operating mode (slow/eco vs. fast)
        if (action == 0)
        {
            this.fastMode = ! this.fastMode;
            IBlockState state = this.getWorld().getBlockState(this.getPos());
            this.getWorld().notifyBlockUpdate(this.getPos(), state, state, 3);
        }
        // 1: Output mode (output to Ender Chest OFF/ON)
        else if (action == 1)
        {
            this.outputToEnderChest = ! this.outputToEnderChest;
        }

        this.markDirty();
    }

    @Override
    public ContainerEnderFurnace getContainer(EntityPlayer player)
    {
        return new ContainerEnderFurnace(player, this);
    }

    @Override
    public Object getGui(EntityPlayer player)
    {
        return new GuiEnderFurnace(this.getContainer(player), this);
    }
}
