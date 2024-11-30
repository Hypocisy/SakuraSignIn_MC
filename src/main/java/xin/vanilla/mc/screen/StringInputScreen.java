package xin.vanilla.mc.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.mc.screen.component.Text;
import xin.vanilla.mc.util.AbstractGuiUtils;
import xin.vanilla.mc.util.I18nUtils;
import xin.vanilla.mc.util.StringUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 字符串输入 Screen
 */
public class StringInputScreen extends Screen {

    /**
     * 父级 Screen
     */
    private final Screen previousScreen;
    /**
     * 标题
     */
    private final Text titleText;
    /**
     * 提示
     */
    private final Text messageText;
    /**
     * 输入数据校验
     */
    private final String validator;
    /**
     * 输入数据回调1
     */
    private final Consumer<String> onDataReceived1;
    /**
     * 输入数据回调2
     */
    private final Function<String, String> onDataReceived2;
    /**
     * 输入框
     */
    private TextFieldWidget inputField;
    /**
     * 确认按钮
     */
    private Button submitButton;
    /**
     * 输入框默认值
     */
    private final String defaultValue;
    /**
     * 输入错误提示
     */
    private Text errorText;


    public StringInputScreen(Screen callbackScreen, Text titleText, Text messageText, String validator, Consumer<String> onDataReceived) {
        super(new StringTextComponent("StringInputScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.titleText = titleText;
        this.messageText = messageText;
        this.validator = validator;
        this.defaultValue = "";
    }

    public StringInputScreen(Screen callbackScreen, Text titleText, Text messageText, String validator, String defaultValue, Consumer<String> onDataReceived) {
        super(new StringTextComponent("StringInputScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = onDataReceived;
        this.onDataReceived2 = null;
        this.titleText = titleText;
        this.messageText = messageText;
        this.validator = validator;
        this.defaultValue = defaultValue;
    }

    public StringInputScreen(Screen callbackScreen, Text titleText, Text messageText, String validator, Function<String, String> onDataReceived) {
        super(new StringTextComponent("StringInputScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.titleText = titleText;
        this.messageText = messageText;
        this.validator = validator;
        this.defaultValue = "";
    }

    public StringInputScreen(Screen callbackScreen, Text titleText, Text messageText, String validator, String defaultValue, Function<String, String> onDataReceived) {
        super(new StringTextComponent("StringInputScreen"));
        this.previousScreen = callbackScreen;
        this.onDataReceived1 = null;
        this.onDataReceived2 = onDataReceived;
        this.titleText = titleText;
        this.messageText = messageText;
        this.validator = validator;
        this.defaultValue = defaultValue;
    }

    @Override
    protected void init() {
        // 创建文本输入框
        this.inputField = AbstractGuiUtils.newTextFieldWidget(this.font, this.width / 2 - 100, this.height / 2 - 20, 200, 20
                , AbstractGuiUtils.textToComponent(this.messageText));
        this.inputField.setMaxLength(Integer.MAX_VALUE);
        if (StringUtils.isNotNullOrEmpty(validator)) {
            this.inputField.setFilter(s -> s.matches(validator));
        }
        this.inputField.setValue(defaultValue);
        this.addButton(this.inputField);
        // 创建提交按钮
        this.submitButton = AbstractGuiUtils.newButton(this.width / 2 + 5, this.height / 2 + 10, 95, 20, new StringTextComponent(I18nUtils.getByZh("取消")), button -> {
            String value = this.inputField.getValue();
            if (StringUtils.isNullOrEmpty(value)) {
                // 关闭当前屏幕并返回到调用者的 Screen
                Minecraft.getInstance().setScreen(previousScreen);
            } else {
                // 获取输入的数据，并执行回调
                if (onDataReceived1 != null) {
                    onDataReceived1.accept(value);
                    // 关闭当前屏幕并返回到调用者的 Screen
                    Minecraft.getInstance().setScreen(previousScreen);
                } else if (onDataReceived2 != null) {
                    String result = onDataReceived2.apply(value);
                    if (StringUtils.isNotNullOrEmpty(result)) {
                        this.errorText = Text.literal(result).setColor(0xFFFF0000);
                    } else {
                        // 关闭当前屏幕并返回到调用者的 Screen
                        Minecraft.getInstance().setScreen(previousScreen);
                    }
                }
            }
        });
        this.addButton(this.submitButton);
        // 创建取消按钮
        this.addButton(AbstractGuiUtils.newButton(this.width / 2 - 100, this.height / 2 + 10, 95, 20, new StringTextComponent(I18nUtils.getByZh("取消")), button -> {
            // 关闭当前屏幕并返回到调用者的 Screen
            Minecraft.getInstance().setScreen(previousScreen);
        }));
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrixStack);
        // 绘制背景
        super.render(matrixStack, mouseX, mouseY, delta);
        // 绘制标题
        AbstractGuiUtils.drawString(titleText, this.width / 2.0f - 100, this.height / 2.0f - 33);
        // 绘制错误提示
        if (this.errorText != null) {
            AbstractGuiUtils.drawLimitedText(errorText, this.width / 2.0f - 100, this.height / 2.0f + 2, 200, AbstractGuiUtils.EllipsisPosition.MIDDLE);
        }
        if (StringUtils.isNotNullOrEmpty(this.inputField.getValue())) {
            this.submitButton.setMessage(new StringTextComponent(I18nUtils.getByZh("提交")));
        } else {
            this.submitButton.setMessage(new StringTextComponent(I18nUtils.getByZh("取消")));
        }
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.inputField.isFocused())) {
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
