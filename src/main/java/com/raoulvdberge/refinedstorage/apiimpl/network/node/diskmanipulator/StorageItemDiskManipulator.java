package com.raoulvdberge.refinedstorage.apiimpl.network.node.diskmanipulator;

import com.raoulvdberge.refinedstorage.RSUtils;
import com.raoulvdberge.refinedstorage.api.storage.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.StorageDiskType;
import com.raoulvdberge.refinedstorage.tile.TileDiskDrive;
import com.raoulvdberge.refinedstorage.tile.config.IFilterable;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class StorageItemDiskManipulator implements IStorageDisk<ItemStack> {
    private NetworkNodeDiskManipulator diskManipulator;
    private IStorageDisk<ItemStack> parent;
    private int lastState;

    public StorageItemDiskManipulator(NetworkNodeDiskManipulator diskManipulator, IStorageDisk<ItemStack> parent) {
        this.diskManipulator = diskManipulator;
        this.parent = parent;
        this.lastState = TileDiskDrive.getDiskState(getStored(), getCapacity());
    }

    @Override
    public int getCapacity() {
        return parent.getCapacity();
    }

    @Override
    public boolean isVoiding() {
        return parent.isVoiding();
    }

    @Override
    public boolean isValid(ItemStack stack) {
        return parent.isValid(stack);
    }

    @Override
    public void onChanged() {
        parent.onChanged();

        diskManipulator.markDirty();

        int currentState = TileDiskDrive.getDiskState(getStored(), getCapacity());

        if (lastState != currentState) {
            lastState = currentState;

            RSUtils.updateBlock(diskManipulator.getHolder().world(), diskManipulator.getHolder().pos());
        }
    }

    @Override
    public void readFromNBT() {
        parent.readFromNBT();
    }

    @Override
    public void writeToNBT() {
        parent.writeToNBT();
    }

    @Override
    public StorageDiskType getType() {
        return StorageDiskType.ITEMS;
    }

    @Override
    public NonNullList<ItemStack> getStacks() {
        return parent.getStacks();
    }

    @Override
    @Nullable
    public ItemStack insert(@Nonnull ItemStack stack, int size, boolean simulate) {
        if (!IFilterable.canTake(diskManipulator.getItemFilters(), diskManipulator.getMode(), diskManipulator.getCompare(), stack)) {
            return ItemHandlerHelper.copyStackWithSize(stack, size);
        }

        return parent.insert(stack, size, simulate);
    }

    @Override
    @Nullable
    public ItemStack extract(@Nonnull ItemStack stack, int size, int flags, boolean simulate) {
        if (!IFilterable.canTake(diskManipulator.getItemFilters(), diskManipulator.getMode(), diskManipulator.getCompare(), stack)) {
            return null;
        }

        return parent.extract(stack, size, flags, simulate);
    }

    @Override
    public int getStored() {
        return parent.getStored();
    }

    @Override
    public int getPriority() {
        return parent.getPriority();
    }

    @Override
    public int getCacheDelta(int storedPreInsertion, int size, @Nullable ItemStack remainder) {
        return parent.getCacheDelta(storedPreInsertion, size, remainder);
    }
}
