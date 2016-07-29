package refinedstorage.tile.grid;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import refinedstorage.RefinedStorage;
import refinedstorage.RefinedStorageBlocks;
import refinedstorage.RefinedStorageItems;
import refinedstorage.api.network.IGridHandler;
import refinedstorage.api.storage.CompareUtils;
import refinedstorage.apiimpl.network.NetworkUtils;
import refinedstorage.block.BlockGrid;
import refinedstorage.block.EnumGridType;
import refinedstorage.container.ContainerGrid;
import refinedstorage.inventory.BasicItemHandler;
import refinedstorage.inventory.BasicItemValidator;
import refinedstorage.inventory.GridFilterInGridItemHandler;
import refinedstorage.item.ItemPattern;
import refinedstorage.network.MessageGridSettingsUpdate;
import refinedstorage.tile.TileNode;
import refinedstorage.tile.config.IRedstoneModeConfig;

import java.util.ArrayList;
import java.util.List;

public class TileGrid extends TileNode implements IGrid {
    public static final String NBT_VIEW_TYPE = "ViewType";
    public static final String NBT_SORTING_DIRECTION = "SortingDirection";
    public static final String NBT_SORTING_TYPE = "SortingType";
    public static final String NBT_SEARCH_BOX_MODE = "SearchBoxMode";

    public static final int SORTING_DIRECTION_ASCENDING = 0;
    public static final int SORTING_DIRECTION_DESCENDING = 1;

    public static final int SORTING_TYPE_QUANTITY = 0;
    public static final int SORTING_TYPE_NAME = 1;

    public static final int SEARCH_BOX_MODE_NORMAL = 0;
    public static final int SEARCH_BOX_MODE_NORMAL_AUTOSELECTED = 1;
    public static final int SEARCH_BOX_MODE_JEI_SYNCHRONIZED = 2;
    public static final int SEARCH_BOX_MODE_JEI_SYNCHRONIZED_AUTOSELECTED = 3;

    public static final int VIEW_TYPE_NORMAL = 0;
    public static final int VIEW_TYPE_NON_CRAFTABLES = 1;
    public static final int VIEW_TYPE_CRAFTABLES = 2;

    private Container craftingContainer = new Container() {
        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return false;
        }

        @Override
        public void onCraftMatrixChanged(IInventory inventory) {
            onCraftingMatrixChanged();
        }
    };
    private InventoryCrafting matrix = new InventoryCrafting(craftingContainer, 3, 3);
    private InventoryCraftResult result = new InventoryCraftResult();

    private BasicItemHandler patterns = new BasicItemHandler(2, this, new BasicItemValidator(RefinedStorageItems.PATTERN));
    private List<ItemStack> filteredItems = new ArrayList<ItemStack>();
    private GridFilterInGridItemHandler filter = new GridFilterInGridItemHandler(filteredItems);

    private EnumGridType type;

    private int viewType = VIEW_TYPE_NORMAL;
    private int sortingDirection = SORTING_DIRECTION_DESCENDING;
    private int sortingType = SORTING_TYPE_NAME;
    private int searchBoxMode = SEARCH_BOX_MODE_NORMAL;

    @Override
    public int getEnergyUsage() {
        switch (getType()) {
            case NORMAL:
                return RefinedStorage.INSTANCE.gridUsage;
            case CRAFTING:
                return RefinedStorage.INSTANCE.craftingGridUsage;
            case PATTERN:
                return RefinedStorage.INSTANCE.patternGridUsage;
            default:
                return 0;
        }
    }

    @Override
    public void updateNode() {
    }

    public EnumGridType getType() {
        if (type == null && worldObj.getBlockState(pos).getBlock() == RefinedStorageBlocks.GRID) {
            this.type = (EnumGridType) worldObj.getBlockState(pos).getValue(BlockGrid.TYPE);
        }

        return type == null ? EnumGridType.NORMAL : type;
    }

    @Override
    public BlockPos getNetworkPosition() {
        return network != null ? network.getPosition() : null;
    }

    public void onGridOpened(EntityPlayer player) {
        if (isConnected()) {
            network.sendStorageToClient((EntityPlayerMP) player);
        }
    }

    @Override
    public IGridHandler getGridHandler() {
        return isConnected() ? network.getGridHandler() : null;
    }

    public InventoryCrafting getMatrix() {
        return matrix;
    }

    public InventoryCraftResult getResult() {
        return result;
    }

    public IItemHandler getPatterns() {
        return patterns;
    }

    @Override
    public BasicItemHandler getFilter() {
        return filter;
    }

    @Override
    public List<ItemStack> getFilteredItems() {
        return filteredItems;
    }

    public void onCraftingMatrixChanged() {
        markDirty();

        result.setInventorySlotContents(0, CraftingManager.getInstance().findMatchingRecipe(matrix, worldObj));
    }

    public void onCrafted(EntityPlayer player) {
        ItemStack[] remainder = CraftingManager.getInstance().getRemainingItems(matrix, worldObj);

        for (int i = 0; i < matrix.getSizeInventory(); ++i) {
            ItemStack slot = matrix.getStackInSlot(i);

            if (i < remainder.length && remainder[i] != null) {
                if (slot != null && slot.stackSize > 1) {
                    if (!player.inventory.addItemStackToInventory(remainder[i].copy())) {
                        InventoryHelper.spawnItemStack(player.worldObj, player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ(), remainder[i].copy());
                    }

                    matrix.decrStackSize(i, 1);
                } else {
                    matrix.setInventorySlotContents(i, remainder[i].copy());
                }
            } else {
                if (slot != null) {
                    if (slot.stackSize == 1 && isConnected()) {
                        matrix.setInventorySlotContents(i, NetworkUtils.extractItem(network, slot, 1));
                    } else {
                        matrix.decrStackSize(i, 1);
                    }
                }
            }
        }

        onCraftingMatrixChanged();
    }

    public void onCraftedShift(ContainerGrid container, EntityPlayer player) {
        List<ItemStack> craftedItemsList = new ArrayList<ItemStack>();
        int craftedItems = 0;
        ItemStack crafted = result.getStackInSlot(0);

        while (true) {
            onCrafted(player);

            craftedItemsList.add(crafted.copy());

            craftedItems += crafted.stackSize;

            if (!CompareUtils.compareStack(crafted, result.getStackInSlot(0)) || craftedItems + crafted.stackSize > crafted.getMaxStackSize()) {
                break;
            }
        }

        for (ItemStack craftedItem : craftedItemsList) {
            if (!player.inventory.addItemStackToInventory(craftedItem.copy())) {
                InventoryHelper.spawnItemStack(player.worldObj, player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ(), craftedItem);
            }
        }

        container.sendCraftingSlots();
        container.detectAndSendChanges();
    }

    public void onCreatePattern() {
        if (canCreatePattern()) {
            patterns.extractItem(0, 1, false);

            ItemStack pattern = new ItemStack(RefinedStorageItems.PATTERN);

            for (ItemStack byproduct : CraftingManager.getInstance().getRemainingItems(matrix, worldObj)) {
                if (byproduct != null) {
                    ItemPattern.addByproduct(pattern, byproduct);
                }
            }

            ItemPattern.addOutput(pattern, result.getStackInSlot(0));

            ItemPattern.setProcessing(pattern, false);

            for (int i = 0; i < 9; ++i) {
                ItemStack ingredient = matrix.getStackInSlot(i);

                if (ingredient != null) {
                    ItemPattern.addInput(pattern, ingredient);
                }
            }

            patterns.setStackInSlot(1, pattern);
        }
    }

    public boolean canCreatePattern() {
        return result.getStackInSlot(0) != null && patterns.getStackInSlot(1) == null && patterns.getStackInSlot(0) != null;
    }

    public void onRecipeTransfer(ItemStack[][] recipe) {
        if (isConnected()) {
            for (int i = 0; i < matrix.getSizeInventory(); ++i) {
                ItemStack slot = matrix.getStackInSlot(i);

                if (slot != null) {
                    if (getType() == EnumGridType.CRAFTING) {
                        if (network.insertItem(slot, slot.stackSize, true) != null) {
                            return;
                        } else {
                            network.insertItem(slot, slot.stackSize, false);
                        }
                    }

                    matrix.setInventorySlotContents(i, null);
                }
            }

            for (int i = 0; i < matrix.getSizeInventory(); ++i) {
                if (recipe[i] != null) {
                    ItemStack[] possibilities = recipe[i];

                    if (getType() == EnumGridType.CRAFTING) {
                        for (ItemStack possibility : possibilities) {
                            ItemStack took = NetworkUtils.extractItem(network, possibility, 1);

                            if (took != null) {
                                matrix.setInventorySlotContents(i, took);

                                break;
                            }
                        }
                    } else if (getType() == EnumGridType.PATTERN) {
                        matrix.setInventorySlotContents(i, possibilities[0]);
                    }
                }
            }
        }
    }

    @Override
    public int getViewType() {
        return viewType;
    }

    public void setViewType(int type) {
        this.viewType = type;

        markDirty();
    }

    public int getSortingDirection() {
        return sortingDirection;
    }

    public void setSortingDirection(int sortingDirection) {
        this.sortingDirection = sortingDirection;

        markDirty();
    }

    public int getSortingType() {
        return sortingType;
    }

    public void setSortingType(int sortingType) {
        this.sortingType = sortingType;

        markDirty();
    }

    public int getSearchBoxMode() {
        return searchBoxMode;
    }

    public void setSearchBoxMode(int searchBoxMode) {
        this.searchBoxMode = searchBoxMode;

        markDirty();
    }

    @Override
    public void onViewTypeChanged(int type) {
        RefinedStorage.INSTANCE.network.sendToServer(new MessageGridSettingsUpdate(this, type, sortingDirection, sortingType, searchBoxMode));
    }

    @Override
    public void onSortingTypeChanged(int type) {
        RefinedStorage.INSTANCE.network.sendToServer(new MessageGridSettingsUpdate(this, viewType, sortingDirection, type, searchBoxMode));
    }

    @Override
    public void onSortingDirectionChanged(int direction) {
        RefinedStorage.INSTANCE.network.sendToServer(new MessageGridSettingsUpdate(this, viewType, direction, sortingType, searchBoxMode));
    }

    @Override
    public void onSearchBoxModeChanged(int searchBoxMode) {
        RefinedStorage.INSTANCE.network.sendToServer(new MessageGridSettingsUpdate(this, viewType, sortingDirection, sortingType, searchBoxMode));
    }

    @Override
    public IRedstoneModeConfig getRedstoneModeConfig() {
        return this;
    }

    @Override
    public void read(NBTTagCompound tag) {
        super.read(tag);

        readItemsLegacy(matrix, 0, tag);
        readItems(patterns, 1, tag);
        readItems(filter, 2, tag);

        if (tag.hasKey(NBT_VIEW_TYPE)) {
            viewType = tag.getInteger(NBT_VIEW_TYPE);
        }

        if (tag.hasKey(NBT_SORTING_DIRECTION)) {
            sortingDirection = tag.getInteger(NBT_SORTING_DIRECTION);
        }

        if (tag.hasKey(NBT_SORTING_TYPE)) {
            sortingType = tag.getInteger(NBT_SORTING_TYPE);
        }

        if (tag.hasKey(NBT_SEARCH_BOX_MODE)) {
            searchBoxMode = tag.getInteger(NBT_SEARCH_BOX_MODE);
        }
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);

        writeItemsLegacy(matrix, 0, tag);
        writeItems(patterns, 1, tag);
        writeItems(filter, 2, tag);

        tag.setInteger(NBT_VIEW_TYPE, viewType);
        tag.setInteger(NBT_SORTING_DIRECTION, sortingDirection);
        tag.setInteger(NBT_SORTING_TYPE, sortingType);
        tag.setInteger(NBT_SEARCH_BOX_MODE, searchBoxMode);

        return tag;
    }

    @Override
    public void writeContainerData(ByteBuf buf) {
        super.writeContainerData(buf);

        buf.writeBoolean(isConnected());
        buf.writeInt(viewType);
        buf.writeInt(sortingDirection);
        buf.writeInt(sortingType);
        buf.writeInt(searchBoxMode);
    }

    @Override
    public void readContainerData(ByteBuf buf) {
        super.readContainerData(buf);

        connected = buf.readBoolean();
        viewType = buf.readInt();
        sortingDirection = buf.readInt();
        sortingType = buf.readInt();
        searchBoxMode = buf.readInt();
    }

    @Override
    public Class<? extends Container> getContainer() {
        return ContainerGrid.class;
    }

    @Override
    public IItemHandler getDroppedItems() {
        switch (getType()) {
            case CRAFTING:
                return new InvWrapper(matrix);
            case PATTERN:
                return patterns;
            default:
                return null;
        }
    }

    public static boolean isValidViewType(int type) {
        return type == VIEW_TYPE_NORMAL ||
            type == VIEW_TYPE_CRAFTABLES ||
            type == VIEW_TYPE_NON_CRAFTABLES;
    }

    public static boolean isValidSearchBoxMode(int mode) {
        return mode == SEARCH_BOX_MODE_NORMAL ||
            mode == SEARCH_BOX_MODE_NORMAL_AUTOSELECTED ||
            mode == SEARCH_BOX_MODE_JEI_SYNCHRONIZED ||
            mode == SEARCH_BOX_MODE_JEI_SYNCHRONIZED_AUTOSELECTED;
    }

    public static boolean isSearchBoxModeWithAutoselection(int mode) {
        return mode == SEARCH_BOX_MODE_NORMAL_AUTOSELECTED || mode == TileGrid.SEARCH_BOX_MODE_JEI_SYNCHRONIZED_AUTOSELECTED;
    }

    public static boolean isValidSortingType(int type) {
        return type == SORTING_TYPE_QUANTITY || type == TileGrid.SORTING_TYPE_NAME;
    }

    public static boolean isValidSortingDirection(int direction) {
        return direction == SORTING_DIRECTION_ASCENDING || direction == SORTING_DIRECTION_DESCENDING;
    }
}
