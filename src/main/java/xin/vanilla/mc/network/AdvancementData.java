package xin.vanilla.mc.network;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;

/**
 * 进度信息
 */
@Data
@Accessors(chain = true)
public class AdvancementData {
    @NonNull
    private final ResourceLocation id;
    @NonNull
    private final DisplayInfo displayInfo;

    public AdvancementData(@NonNull ResourceLocation id, DisplayInfo displayInfo) {
        this.id = id;
        if (displayInfo == null) {
            this.displayInfo = emptyDisplayInfo();
        } else {
            this.displayInfo = displayInfo;
        }
    }

    public static AdvancementData fromAdvancement(Advancement advancement) {
        DisplayInfo displayInfo = advancement.getDisplay();
        if (displayInfo == null) {
            return new AdvancementData(advancement.getId(), createDisplayInfo(advancement.getId().toString()));
        }
        return new AdvancementData(advancement.getId(), displayInfo);
    }

    public static AdvancementData readFromBuffer(PacketBuffer buffer) {
        ResourceLocation id = buffer.readResourceLocation();
        return new AdvancementData(id, DisplayInfo.fromNetwork(buffer));
    }

    public static DisplayInfo emptyDisplayInfo() {
        return createDisplayInfo("");
    }

    public static DisplayInfo createDisplayInfo(String title) {
        return createDisplayInfo(title, "", new ItemStack(Items.AIR));
    }

    public static DisplayInfo createDisplayInfo(String title, String description) {
        return createDisplayInfo(title, description, new ItemStack(Items.AIR));
    }

    public static DisplayInfo createDisplayInfo(String title, String description, ItemStack itemStack) {
        return new DisplayInfo(itemStack
                , new StringTextComponent(title), new StringTextComponent(description)
                , new ResourceLocation(""), FrameType.TASK
                , false, false, false);
    }

    public void writeToBuffer(PacketBuffer buffer) {
        buffer.writeResourceLocation(id);
        displayInfo.serializeToNetwork(buffer);
    }
}
