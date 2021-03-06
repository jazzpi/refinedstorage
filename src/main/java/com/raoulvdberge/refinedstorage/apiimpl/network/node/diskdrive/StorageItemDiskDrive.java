package com.raoulvdberge.refinedstorage.apiimpl.network.node.diskdrive;

import com.raoulvdberge.refinedstorage.RSUtils;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.IStorageDisk;
import com.raoulvdberge.refinedstorage.api.storage.StorageDiskType;
import com.raoulvdberge.refinedstorage.tile.TileDiskDrive;
import com.raoulvdberge.refinedstorage.tile.config.IFilterable;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class StorageItemDiskDrive implements IStorageDisk<ItemStack> {
    private NetworkNodeDiskDrive diskDrive;
    private IStorageDisk<ItemStack> parent;
    private int lastState;

    public StorageItemDiskDrive(NetworkNodeDiskDrive diskDrive, IStorageDisk<ItemStack> parent) {
        this.diskDrive = diskDrive;
        this.parent = parent;
        this.lastState = TileDiskDrive.getDiskState(getStored(), getCapacity());
    }

    @Override
    public int getPriority() {
        return diskDrive.getPriority();
    }

    @Override
    public NonNullList<ItemStack> getStacks() {
        return parent.getStacks();
    }

    @Override
    @Nullable
    public ItemStack insert(@Nonnull ItemStack stack, int size, boolean simulate) {
        if (!IFilterable.canTake(diskDrive.getItemFilters(), diskDrive.getMode(), diskDrive.getCompare(), stack)) {
            return ItemHandlerHelper.copyStackWithSize(stack, size);
        }

        return parent.insert(stack, size, simulate);
    }

    @Nullable
    @Override
    public ItemStack extract(@Nonnull ItemStack stack, int size, int flags, boolean simulate) {
        return parent.extract(stack, size, flags, simulate);
    }

    @Override
    public int getStored() {
        return parent.getStored();
    }

    @Override
    public AccessType getAccessType() {
        return diskDrive.getAccessType();
    }

    @Override
    public int getCacheDelta(int storedPreInsertion, int size, @Nullable ItemStack remainder) {
        return parent.getCacheDelta(storedPreInsertion, size, remainder);
    }

    @Override
    public int getCapacity() {
        return parent.getCapacity();
    }

    @Override
    public boolean isVoiding() {
        return diskDrive.getVoidExcess();
    }

    @Override
    public boolean isValid(ItemStack stack) {
        return parent.isValid(stack);
    }

    @Override
    public void onChanged() {
        parent.onChanged();

        diskDrive.markDirty();

        int currentState = TileDiskDrive.getDiskState(getStored(), getCapacity());

        if (lastState != currentState) {
            lastState = currentState;

            RSUtils.updateBlock(diskDrive.getHolder().world(), diskDrive.getHolder().pos());
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
        return parent.getType();
    }
}