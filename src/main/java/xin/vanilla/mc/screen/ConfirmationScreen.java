package xin.vanilla.mc.screen;

import lombok.NonNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.mc.screen.component.Text;
import xin.vanilla.mc.util.AbstractGuiUtils;
import xin.vanilla.mc.util.I18nUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Supplier;

/**
 * 操作确认 Screen
 */
public class ConfirmationScreen extends Screen {

    /**
     * 父级 Screen
     */
    private final Screen previousScreen;
    /**
     * 标题
     */
    private final Text titleText;
    /**
     * 回调
     */
    private final Runnable onConfirm;
    /**
     * 是否要显示该界面, 若为false则直接关闭当前界面并返回到调用者的 Screen
     */
    private final Supplier<Boolean> shouldClose;


    public ConfirmationScreen(Screen callbackScreen, Text titleText, @NonNull Runnable onConfirm) {
        super(new StringTextComponent("ConfirmationScreen"));
        this.previousScreen = callbackScreen;
        this.onConfirm = onConfirm;
        this.titleText = titleText;
        this.shouldClose = null;
    }

    public ConfirmationScreen(Screen callbackScreen, Text titleText, @NonNull Runnable onConfirm, Supplier<Boolean> shouldClose) {
        super(new StringTextComponent("ConfirmationScreen"));
        this.previousScreen = callbackScreen;
        this.onConfirm = onConfirm;
        this.titleText = titleText;
        this.shouldClose = shouldClose;
    }

    @Override
    protected void init() {
        if (this.shouldClose != null && Boolean.TRUE.equals(this.shouldClose.get()))
            Minecraft.getInstance().setScreen(previousScreen);
        // 创建提交按钮
        Button submitButton = AbstractGuiUtils.newButton(this.width / 2 + 5, this.height / 2 + 10, 95, 20, new StringTextComponent(I18nUtils.getByZh("确认")), button -> {
            onConfirm.run();
            Minecraft.getInstance().setScreen(previousScreen);
        });
        this.addButton(submitButton);
        // 创建取消按钮
        this.addButton(AbstractGuiUtils.newButton(this.width / 2 - 100, this.height / 2 + 10, 95, 20, new StringTextComponent(I18nUtils.getByZh("取消")), button -> {
            // 关闭当前屏幕并返回到调用者的 Screen
            Minecraft.getInstance().setScreen(previousScreen);
        }));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(int mouseX, int mouseY, float delta) {
        this.renderBackground();
        // 绘制背景
        super.render(mouseX, mouseY, delta);
        // 绘制标题
        AbstractGuiUtils.drawString(titleText, this.width / 2.0f - 100, this.height / 2.0f - 33);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            Minecraft.getInstance().setScreen(previousScreen);
            return true;
        } else {
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    /**
     * 重写键盘事件，ESC键关闭当前屏幕并返回到调用者的 Screen
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            Minecraft.getInstance().setScreen(previousScreen);
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
