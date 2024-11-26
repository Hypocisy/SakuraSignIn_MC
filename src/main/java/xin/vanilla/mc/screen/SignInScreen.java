package xin.vanilla.mc.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.mc.SakuraSignIn;
import xin.vanilla.mc.capability.IPlayerSignInData;
import xin.vanilla.mc.capability.PlayerSignInDataCapability;
import xin.vanilla.mc.config.ClientConfig;
import xin.vanilla.mc.config.ServerConfig;
import xin.vanilla.mc.enums.ESignInStatus;
import xin.vanilla.mc.enums.ESignInType;
import xin.vanilla.mc.event.ClientEventHandler;
import xin.vanilla.mc.network.ModNetworkHandler;
import xin.vanilla.mc.network.SignInPacket;
import xin.vanilla.mc.rewards.RewardList;
import xin.vanilla.mc.rewards.RewardManager;
import xin.vanilla.mc.screen.component.OperationButton;
import xin.vanilla.mc.screen.component.PopupOption;
import xin.vanilla.mc.screen.coordinate.TextureCoordinate;
import xin.vanilla.mc.util.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static xin.vanilla.mc.SakuraSignIn.PNG_CHUNK_NAME;
import static xin.vanilla.mc.screen.SignInScreen.OperationButtonType.*;

@OnlyIn(Dist.CLIENT)
public class SignInScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    // region 变量定义

    /**
     * 当前按下的按键
     */
    private int keyCode = -1;
    /**
     * 按键的组合键 Shift 1, Ctrl 2, Alt 4
     */
    private int modifiers = -1;

    /**
     * 上月最后offset天
     */
    public static int lastOffset = 6;
    /**
     * 下月开始offset天
     */
    public static int nextOffset = 6;

    /**
     * 日历单元格集合
     */
    private final List<SignInCell> signInCells = new ArrayList<>();
    /**
     * 背景材质
     */
    private ResourceLocation BACKGROUND_TEXTURE;
    /**
     * 背景材质坐标
     */
    public TextureCoordinate textureCoordinate;
    /**
     * 是否使用内置主题特殊图标
     */
    private boolean specialVersion;

    /**
     * 日历表格列数
     */
    private static final int columns = 7;
    /**
     * 日历表格行数
     */
    private static final int rows = 6;

    // 背景渲染坐标大小定义
    private int bgH = Math.max(super.height - 20, 120);
    private int bgW = Math.max(bgH * 5 / 6, 100);
    private int bgX = (super.width - bgW) / 2;
    private int bgY = 0;

    /**
     * UI缩放比例
     */
    private double scale = 1.0F;

    /**
     * 操作按钮集合
     */
    private final Map<Integer, OperationButton> BUTTONS = new HashMap<>();

    /**
     * 弹出层选项
     */
    private PopupOption popupOption;
    /**
     * 主题文件列表
     */
    private List<File> themeFileList;

    // endregion

    /**
     * 操作按钮类型
     */
    @Getter
    enum OperationButtonType {
        LEFT_ARROW(1),
        RIGHT_ARROW(2),
        UP_ARROW(3),
        DOWN_ARROW(4),
        THEME_ORIGINAL_BUTTON(100, "textures/gui/sign_in_calendar_original.png"),
        THEME_SAKURA_BUTTON(101, "textures/gui/sign_in_calendar_sakura.png"),
        THEME_CLOVER_BUTTON(102, "textures/gui/sign_in_calendar_clover.png"),
        THEME_MAPLE_BUTTON(103, "textures/gui/sign_in_calendar_maple.png"),
        THEME_CHAOS_BUTTON(104, "textures/gui/sign_in_calendar_chaos.png");

        final int code;
        final String path;

        OperationButtonType(int code) {
            this.code = code;
            path = "";
        }

        OperationButtonType(int code, String path) {
            this.code = code;
            this.path = path;
        }

        static OperationButtonType valueOf(int code) {
            return Arrays.stream(values()).filter(v -> v.getCode() == code).findFirst().orElse(null);
        }
    }

    public SignInScreen() {
        super(new TranslationTextComponent("screen.sakura_sign_in.sign_in_title"));
    }

    @Override
    protected void init() {
        super.init();
        this.popupOption = PopupOption.init(super.font);
        // 初始化材质及材质坐标信息
        this.updateTextureAndCoordinate();

        this.themeFileList = TextureUtils.getPngFilesInDirectory(TextureUtils.CUSTOM_THEME_DIR);

        // 初始化布局信息
        this.updateLayout();
    }

    /**
     * 更新材质及材质坐标信息
     */
    private void updateTextureAndCoordinate() {
        try {
            BACKGROUND_TEXTURE = TextureUtils.loadCustomTexture(ClientConfig.THEME.get());
            specialVersion = Boolean.TRUE.equals(ClientConfig.SPECIAL_THEME.get());
            InputStream inputStream = Minecraft.getInstance().getResourceManager().getResource(BACKGROUND_TEXTURE).getInputStream();
            textureCoordinate = PNGUtils.readLastPrivateChunk(inputStream, PNG_CHUNK_NAME);
        } catch (IOException | ClassNotFoundException ignored) {
        }
        if (textureCoordinate == null) {
            // 使用默认配置
            textureCoordinate = TextureCoordinate.getDefault();
        }
        // 设置内置主题特殊图标UV的偏移量
        if (specialVersion) {
            textureCoordinate.getNotSignedInUV().setX(320);
            textureCoordinate.getSignedInUV().setX(320);
        } else {
            textureCoordinate.getNotSignedInUV().setX(0);
            textureCoordinate.getSignedInUV().setX(0);
        }
        // 更新按钮信息
        this.updateButtons();
    }

    /**
     * 更新按钮信息
     */
    private void updateButtons() {
        BUTTONS.put(LEFT_ARROW.getCode(), new OperationButton(LEFT_ARROW.getCode(), BACKGROUND_TEXTURE)
                .setCoordinate(textureCoordinate.getLeftArrowCoordinate())
                .setNormal(textureCoordinate.getArrowUV()).setHover(textureCoordinate.getArrowHoverUV()).setTap(textureCoordinate.getArrowTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setFlipHorizontal(true));
        BUTTONS.put(RIGHT_ARROW.getCode(), new OperationButton(RIGHT_ARROW.getCode(), BACKGROUND_TEXTURE)
                .setCoordinate(textureCoordinate.getRightArrowCoordinate())
                .setNormal(textureCoordinate.getArrowUV()).setHover(textureCoordinate.getArrowHoverUV()).setTap(textureCoordinate.getArrowTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight()));
        BUTTONS.put(UP_ARROW.getCode(), new OperationButton(UP_ARROW.getCode(), BACKGROUND_TEXTURE)
                .setCoordinate(textureCoordinate.getUpArrowCoordinate())
                .setNormal(textureCoordinate.getArrowUV()).setHover(textureCoordinate.getArrowHoverUV()).setTap(textureCoordinate.getArrowTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setRotatedAngle(270));
        BUTTONS.put(DOWN_ARROW.getCode(), new OperationButton(DOWN_ARROW.getCode(), BACKGROUND_TEXTURE)
                .setCoordinate(textureCoordinate.getDownArrowCoordinate())
                .setNormal(textureCoordinate.getArrowUV()).setHover(textureCoordinate.getArrowHoverUV()).setTap(textureCoordinate.getArrowTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setRotatedAngle(90).setFlipVertical(true));

        BUTTONS.put(THEME_ORIGINAL_BUTTON.getCode(), new OperationButton(THEME_ORIGINAL_BUTTON.getCode(), BACKGROUND_TEXTURE)
                .setCoordinate(textureCoordinate.getThemeCoordinate())
                .setNormal(textureCoordinate.getThemeUV()).setHover(textureCoordinate.getThemeHoverUV()).setTap(textureCoordinate.getThemeTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight()));
        BUTTONS.put(THEME_SAKURA_BUTTON.getCode(), new OperationButton(THEME_SAKURA_BUTTON.getCode(), BACKGROUND_TEXTURE)
                .setCoordinate(textureCoordinate.getThemeCoordinate())
                .setNormal(textureCoordinate.getThemeUV()).setHover(textureCoordinate.getThemeHoverUV()).setTap(textureCoordinate.getThemeTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight()));
        BUTTONS.put(THEME_CLOVER_BUTTON.getCode(), new OperationButton(THEME_CLOVER_BUTTON.getCode(), BACKGROUND_TEXTURE)
                .setCoordinate(textureCoordinate.getThemeCoordinate())
                .setNormal(textureCoordinate.getThemeUV()).setHover(textureCoordinate.getThemeHoverUV()).setTap(textureCoordinate.getThemeTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight()));
        BUTTONS.put(THEME_MAPLE_BUTTON.getCode(), new OperationButton(THEME_MAPLE_BUTTON.getCode(), BACKGROUND_TEXTURE)
                .setCoordinate(textureCoordinate.getThemeCoordinate())
                .setNormal(textureCoordinate.getThemeUV()).setHover(textureCoordinate.getThemeHoverUV()).setTap(textureCoordinate.getThemeTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight()));
        BUTTONS.put(THEME_CHAOS_BUTTON.getCode(), new OperationButton(THEME_CHAOS_BUTTON.getCode(), BACKGROUND_TEXTURE)
                .setCoordinate(textureCoordinate.getThemeCoordinate())
                .setNormal(textureCoordinate.getThemeUV()).setHover(textureCoordinate.getThemeHoverUV()).setTap(textureCoordinate.getThemeTapUV())
                .setTextureWidth(textureCoordinate.getTotalWidth())
                .setTextureHeight(textureCoordinate.getTotalHeight())
                .setTremblingAmplitude(3.5));
    }

    /**
     * 计算并更新布局信息
     */
    private void updateLayout() {
        // 限制背景高度大于120
        bgH = Math.max(super.height - 20, 120);
        // 限制背景宽度大于100
        bgW = Math.max(bgH * 5 / 6, 100);
        // 使背景水平居中
        bgX = (super.width - bgW) / 2;
        // 更新缩放比例
        this.scale = bgH * 1.0f / textureCoordinate.getBgUV().getVHeight();
        // 创建或更新格子位置
        this.createCalendarCells(SakuraSignIn.getCalendarCurrentDate());
    }

    /**
     * 创建日历格子
     * 此方法用于生成日历控件中的每日格子，包括当前月和上月的末尾几天
     * 它根据当前日期计算出上月和本月的天数以及每周的起始天数，并据此创建相应数量的格子
     */
    private void createCalendarCells(Date current) {
        // 清除原有格子，避免重复添加
        signInCells.clear();

        double startX = bgX + textureCoordinate.getCellCoordinate().getX() * this.scale;
        double startY = bgY + textureCoordinate.getCellCoordinate().getY() * this.scale;
        // 今天的校准日期
        Date compensateDate = RewardManager.getCompensateDate(new Date());
        Date lastMonth = DateUtils.addMonth(current, -1);
        int daysOfLastMonth = DateUtils.getDaysOfMonth(lastMonth);
        int dayOfWeekOfMonthStart = DateUtils.getDayOfWeekOfMonthStart(current);
        int daysOfCurrentMonth = DateUtils.getDaysOfMonth(current);

        // 获取奖励列表
        if (Minecraft.getInstance().player != null) {
            IPlayerSignInData signInData = PlayerSignInDataCapability.getData(Minecraft.getInstance().player);
            Map<Integer, RewardList> monthRewardList = RewardManager.getMonthRewardList(current, signInData, lastOffset, nextOffset);

            boolean allCurrentDaysDisplayed = false;
            boolean showLastReward = ClientConfig.SHOW_LAST_REWARD.get();
            boolean showNextReward = ClientConfig.SHOW_NEXT_REWARD.get();
            for (int row = 0; row < rows; row++) {
                if (allCurrentDaysDisplayed && !showNextReward) break;
                for (int col = 0; col < columns; col++) {
                    // 计算当前格子的索引
                    int itemIndex = row * columns + col;
                    // 检查是否已超过设置显示上限
                    if (itemIndex >= 40) break;
                    double x = startX + col * (textureCoordinate.getCellCoordinate().getWidth() + textureCoordinate.getCellHMargin()) * this.scale;
                    double y = startY + row * (textureCoordinate.getCellCoordinate().getHeight() + textureCoordinate.getCellVMargin()) * this.scale;
                    int year, month, day, status;
                    boolean showIcon, showText, showHover;
                    // 计算本月第一天是第几(0为第一个)个格子
                    int curPoint = (dayOfWeekOfMonthStart - (textureCoordinate.getWeekStart() - 1) + 6) % 7;
                    // 根据itemIndex确定日期和状态
                    if (itemIndex >= curPoint + daysOfCurrentMonth) {
                        // 属于下月的日期
                        year = DateUtils.getYearPart(DateUtils.addMonth(current, 1));
                        month = DateUtils.getMonthOfDate(DateUtils.addMonth(current, 1));
                        day = itemIndex - curPoint - daysOfCurrentMonth + 1;
                        status = ESignInStatus.NO_ACTION.getCode();
                        showIcon = showNextReward && day < lastOffset;
                        showText = true;
                        showHover = showNextReward && day < lastOffset;
                    } else if (itemIndex < curPoint) {
                        // 属于上月的日期
                        year = DateUtils.getYearPart(lastMonth);
                        month = DateUtils.getMonthOfDate(lastMonth);
                        day = daysOfLastMonth - curPoint + itemIndex + 1;
                        status = ESignInStatus.NO_ACTION.getCode();
                        showIcon = showLastReward && day > daysOfLastMonth - lastOffset;
                        showText = true;
                        showHover = showLastReward && day > daysOfLastMonth - lastOffset;
                    } else {
                        // 属于当前月的日期
                        year = DateUtils.getYearPart(current);
                        month = DateUtils.getMonthOfDate(current);
                        day = itemIndex - curPoint + 1;
                        status = ESignInStatus.NO_ACTION.getCode();
                        // 如果是今天，则设置为未签到状态
                        if (year == DateUtils.getYearPart(compensateDate) && day == DateUtils.getDayOfMonth(compensateDate) && month == DateUtils.getMonthOfDate(compensateDate)) {
                            status = ESignInStatus.NOT_SIGNED_IN.getCode();
                        }
                        showIcon = true;
                        showText = true;
                        showHover = true;
                        allCurrentDaysDisplayed = day == daysOfCurrentMonth;
                    }
                    int key = year * 10000 + month * 100 + day;
                    // 当前格子日期
                    Date curDate = DateUtils.getDate(key);

                    RewardList rewards = monthRewardList.getOrDefault(key, new RewardList());
                    // if (CollectionUtils.isNullOrEmpty(rewards)) continue;

                    // 是否能补签
                    if (ServerConfig.SIGN_IN_CARD.get()) {
                        // 最早能补签的日期
                        Date minDate = DateUtils.addDay(compensateDate, -ServerConfig.RE_SIGN_IN_DAYS.get());
                        if (DateUtils.toDateInt(minDate) <= key && key <= DateUtils.toDateInt(compensateDate) && status != ESignInStatus.NOT_SIGNED_IN.getCode()) {
                            status = ESignInStatus.CAN_REPAIR.getCode();
                        }
                    }
                    // 判断是否已领奖
                    if (RewardManager.isRewarded(signInData, curDate, false)) {
                        status = ESignInStatus.REWARDED.getCode();
                    }
                    // 判断是否已签到
                    else if (RewardManager.isSignedIn(signInData, curDate, false)) {
                        status = ESignInStatus.SIGNED_IN.getCode();
                    }

                    // 创建物品格子
                    SignInCell cell = new SignInCell(BACKGROUND_TEXTURE, textureCoordinate, x, y, textureCoordinate.getCellCoordinate().getWidth() * this.scale, textureCoordinate.getCellCoordinate().getHeight() * this.scale, this.scale, rewards, year, month, day, status);
                    cell.setShowIcon(showIcon).setShowText(showText).setShowHover(showHover);
                    // 添加到列表
                    signInCells.add(cell);
                }
            }
        }
    }

    /**
     * 绘制背景纹理
     */
    private void renderBackgroundTexture(MatrixStack matrixStack) {
        // 开启 OpenGL 的混合模式，使得纹理的透明区域渲染生效
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 绘制背景纹理，使用缩放后的宽度和高度
        Minecraft.getInstance().getTextureManager().bind(BACKGROUND_TEXTURE);
        AbstractGuiUtils.blit(matrixStack, bgX, bgY, bgW, bgH, (float) textureCoordinate.getBgUV().getU0(), (float) textureCoordinate.getBgUV().getV0(), (int) textureCoordinate.getBgUV().getUWidth(), (int) textureCoordinate.getBgUV().getVHeight(), textureCoordinate.getTotalWidth(), textureCoordinate.getTotalHeight());
        // 关闭 OpenGL 的混合模式
        RenderSystem.disableBlend();
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        // 绘制背景
        this.renderBackground(matrixStack);
        // 绘制缩放背景纹理
        this.renderBackgroundTexture(matrixStack);

        // 渲染年份
        double yearX = bgX + textureCoordinate.getYearCoordinate().getX() * this.scale;
        double yearY = bgY + textureCoordinate.getYearCoordinate().getY() * this.scale;
        String yearTitle = DateUtils.toLocalStringYear(SakuraSignIn.getCalendarCurrentDate(), Minecraft.getInstance().options.languageCode);
        super.font.draw(matrixStack, yearTitle, (float) yearX, (float) yearY, textureCoordinate.getTextColorDate());

        // 渲染月份
        double monthX = bgX + textureCoordinate.getMonthCoordinate().getX() * this.scale;
        double monthY = bgY + textureCoordinate.getMonthCoordinate().getY() * this.scale;
        String monthTitle = DateUtils.toLocalStringMonth(SakuraSignIn.getCalendarCurrentDate(), Minecraft.getInstance().options.languageCode);
        super.font.draw(matrixStack, monthTitle, (float) monthX, (float) monthY, textureCoordinate.getTextColorDate());

        // 渲染操作按钮
        for (Integer op : BUTTONS.keySet()) {
            OperationButton button = BUTTONS.get(op);
            switch (OperationButtonType.valueOf(op)) {
                case RIGHT_ARROW:
                    // 如果宽度和高度与月份相同，则将大小设置为字体行高
                    if (button.getWidth() == textureCoordinate.getMonthCoordinate().getWidth() && button.getHeight() == textureCoordinate.getMonthCoordinate().getHeight()) {
                        button.setWidth(font.lineHeight / this.scale).setHeight(font.lineHeight / this.scale);
                    }
                    // 如果坐标与月份相同，则将坐标设置为月份右边的位置
                    if (button.getX() == textureCoordinate.getMonthCoordinate().getX() && button.getY() == textureCoordinate.getMonthCoordinate().getY()) {
                        button.setX((monthX - bgX + font.width(monthTitle) + 1) / this.scale);
                    }
                    break;
                case DOWN_ARROW:
                    // 如果宽度和高度与年份相同，则将大小设置为字体行高
                    if (button.getWidth() == textureCoordinate.getYearCoordinate().getWidth() && button.getHeight() == textureCoordinate.getYearCoordinate().getHeight()) {
                        button.setWidth(font.lineHeight / this.scale).setHeight(font.lineHeight / this.scale);
                    }
                    // 如果坐标与年份相同，则将坐标设置为年份右边的位置
                    if (button.getX() == textureCoordinate.getYearCoordinate().getX() && button.getY() == textureCoordinate.getYearCoordinate().getY()) {
                        button.setX((yearX - bgX + font.width(yearTitle) + 1) / this.scale);
                    }
                    break;
                case LEFT_ARROW:
                    // 如果宽度和高度与月份相同，则将大小设置为字体行高
                    if (button.getWidth() == textureCoordinate.getMonthCoordinate().getWidth() && button.getHeight() == textureCoordinate.getMonthCoordinate().getHeight()) {
                        button.setWidth(font.lineHeight / this.scale).setHeight(font.lineHeight / this.scale);
                    }
                    // 如果坐标与月份相同，则将坐标设置为月份左边的位置
                    if (button.getX() == textureCoordinate.getMonthCoordinate().getX() && button.getY() == textureCoordinate.getMonthCoordinate().getY()) {
                        button.setX((monthX - bgX - 1) / this.scale - button.getWidth());
                    }
                    break;
                case UP_ARROW:
                    // 如果宽度和高度与年份相同，则将大小设置为字体行高
                    if (button.getWidth() == textureCoordinate.getYearCoordinate().getWidth() && button.getHeight() == textureCoordinate.getYearCoordinate().getHeight()) {
                        button.setWidth(font.lineHeight / this.scale).setHeight(font.lineHeight / this.scale);
                    }
                    // 如果坐标与年份相同，则将坐标设置为年份左边的位置
                    if (button.getX() == textureCoordinate.getYearCoordinate().getX() && button.getY() == textureCoordinate.getYearCoordinate().getY()) {
                        button.setX((yearX - bgX - 1) / this.scale - button.getWidth());
                    }
                    break;
                case THEME_ORIGINAL_BUTTON:
                case THEME_SAKURA_BUTTON:
                case THEME_CLOVER_BUTTON:
                case THEME_MAPLE_BUTTON:
                case THEME_CHAOS_BUTTON:
                    // 如选中主题为当前主题则设置为鼠标按下(选中)状态
                    if (BACKGROUND_TEXTURE.getPath().equalsIgnoreCase(OperationButtonType.valueOf(op).getPath())) {
                        button.setNormalV(button.getTapV());
                        button.setHoverV(button.getTapV());
                    } else {
                        button.setNormalV(textureCoordinate.getThemeUV().getV0());
                        button.setHoverV(textureCoordinate.getThemeHoverUV().getV0());
                    }
                    button.setNormalU((op - 100) * textureCoordinate.getThemeUV().getUWidth());
                    button.setHoverU((op - 100) * textureCoordinate.getThemeHoverUV().getUWidth());
                    button.setTapU((op - 100) * textureCoordinate.getThemeTapUV().getUWidth());
                    button.setX((op - 100) * (textureCoordinate.getThemeCoordinate().getWidth() + textureCoordinate.getThemeHMargin()) + textureCoordinate.getThemeCoordinate().getX());
            }
            button.setBaseX(bgX);
            button.setBaseY(bgY);
            button.setScale(this.scale);
            button.render(matrixStack, mouseX, mouseY);
        }
        // 渲染操作按钮的弹出提示
        for (Integer op : BUTTONS.keySet()) {
            BUTTONS.get(op).renderPopup(matrixStack, mouseX, mouseY, this.keyCode, this.modifiers);
        }

        // 渲染所有格子
        for (SignInCell cell : signInCells) {
            cell.render(matrixStack, super.font, this.itemRenderer, mouseX, mouseY);
        }
        // 渲染格子弹出层
        for (SignInCell cell : signInCells) {
            if (cell.isShowHover() && cell.isMouseOver(mouseX, mouseY)) {
                cell.renderTooltip(matrixStack, super.font, this.itemRenderer, mouseX, mouseY);
            }
        }

        // 绘制弹出选项
        popupOption.render(matrixStack, mouseX, mouseY);
    }

    /**
     * 检测鼠标点击事件
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 清空弹出选项
        if (!popupOption.isHovered()) {
            popupOption.clear();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                BUTTONS.forEach((key, value) -> {
                    if (value.isHovered()) {
                        value.setPressed(true);
                    }
                });
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 检测鼠标松开事件
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        AtomicBoolean updateLayout = new AtomicBoolean(false);
        AtomicBoolean updateTextureAndCoordinate = new AtomicBoolean(false);
        AtomicBoolean flag = new AtomicBoolean(false);
        if (popupOption.isHovered()) {
            LOGGER.debug("选择了弹出选项:\tIndex: {}\tContent: {}", popupOption.getSelectedIndex(), popupOption.getSelectedString());
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !CollectionUtils.isNullOrEmpty(themeFileList)) {
                ClientPlayerEntity player = Minecraft.getInstance().player;
                String selectedFile = themeFileList.get(popupOption.getSelectedIndex()).getPath();
                if (player != null) {
                    player.sendMessage(new StringTextComponent("已选择主题文件: " + selectedFile), player.getUUID());
                    ResourceLocation resourceLocation = TextureUtils.loadCustomTexture(selectedFile);
                    if (TextureUtils.isTextureAvailable(resourceLocation)) {
                        ClientConfig.THEME.set(selectedFile);
                        updateTextureAndCoordinate.set(true);
                        updateLayout.set(true);
                    }
                }
            } else {
                SakuraSignIn.openFolder(new File(FMLPaths.CONFIGDIR.get().resolve(SakuraSignIn.MODID).toFile(), "themes").toPath());
            }
            popupOption.clear();
        }
        // 左键签到, 右键补签(如果服务器允许且有补签卡), 右键领取奖励(如果是已签到未领取状态)
        else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            ClientPlayerEntity player = Minecraft.getInstance().player;
            // 控制按钮
            BUTTONS.forEach((key, value) -> {
                if (value.isHovered() && value.isPressed()) {
                    this.handleOperation(mouseX, mouseY, button, value, updateLayout, updateTextureAndCoordinate, flag);
                }
                value.setPressed(false);
            });
            if (!flag.get()) {
                // 日历格子
                for (SignInCell cell : signInCells) {
                    if (cell.isShowIcon() && cell.isMouseOver((int) mouseX, (int) mouseY)) {
                        if (player != null) {
                            this.handleSignIn(button, cell, player);
                        }
                        flag.set(true);
                    }
                }

            }
        }
        if (updateTextureAndCoordinate.get()) this.updateTextureAndCoordinate();
        if (updateLayout.get()) this.updateLayout();
        return flag.get() ? flag.get() : super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 处理操作按钮事件
     *
     * @param mouseX        鼠标X坐标
     * @param mouseY        鼠标Y坐标
     * @param button        鼠标按键
     * @param value         操作按钮
     * @param updateLayout  是否更新布局
     * @param updateTexture 是否更新纹理和坐标
     * @param flag          是否处理过事件
     */
    private void handleOperation(double mouseX, double mouseY, int button, OperationButton value, AtomicBoolean updateLayout, AtomicBoolean updateTexture, AtomicBoolean flag) {
        // 上个月
        if (value.getOperation() == LEFT_ARROW.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                SakuraSignIn.setCalendarCurrentDate(DateUtils.addMonth(SakuraSignIn.getCalendarCurrentDate(), -1));
                updateLayout.set(true);
                flag.set(true);
            }
        }
        // 下个月
        else if (value.getOperation() == RIGHT_ARROW.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                SakuraSignIn.setCalendarCurrentDate(DateUtils.addMonth(SakuraSignIn.getCalendarCurrentDate(), 1));
                updateLayout.set(true);
                flag.set(true);
            }
        }
        // 上一年
        else if (value.getOperation() == UP_ARROW.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                SakuraSignIn.setCalendarCurrentDate(DateUtils.addYear(SakuraSignIn.getCalendarCurrentDate(), -1));
                updateLayout.set(true);
                flag.set(true);
            }
        }
        // 下一年
        else if (value.getOperation() == DOWN_ARROW.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                SakuraSignIn.setCalendarCurrentDate(DateUtils.addYear(SakuraSignIn.getCalendarCurrentDate(), 1));
                updateLayout.set(true);
                flag.set(true);
            }
        }
        // 类原版主题
        else if (value.getOperation() == THEME_ORIGINAL_BUTTON.getCode()) {
            specialVersion = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            ClientConfig.THEME.set(THEME_ORIGINAL_BUTTON.getPath());
            ClientConfig.SPECIAL_THEME.set(specialVersion);
            updateLayout.set(true);
            updateTexture.set(true);
            flag.set(true);
        }
        // 樱花粉主题
        else if (value.getOperation() == THEME_SAKURA_BUTTON.getCode()) {
            specialVersion = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            ClientConfig.THEME.set(THEME_SAKURA_BUTTON.getPath());
            ClientConfig.SPECIAL_THEME.set(specialVersion);
            updateLayout.set(true);
            updateTexture.set(true);
            flag.set(true);
        }
        // 四叶草主题
        else if (value.getOperation() == THEME_CLOVER_BUTTON.getCode()) {
            specialVersion = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            ClientConfig.THEME.set(THEME_CLOVER_BUTTON.getPath());
            ClientConfig.SPECIAL_THEME.set(specialVersion);
            updateLayout.set(true);
            updateTexture.set(true);
            flag.set(true);
        }
        // 枫叶主题
        else if (value.getOperation() == THEME_MAPLE_BUTTON.getCode()) {
            specialVersion = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            ClientConfig.THEME.set(THEME_MAPLE_BUTTON.getPath());
            ClientConfig.SPECIAL_THEME.set(specialVersion);
            updateLayout.set(true);
            updateTexture.set(true);
            flag.set(true);
        }
        // 混沌主题
        else if (value.getOperation() == THEME_CHAOS_BUTTON.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                ClientConfig.THEME.set(THEME_CHAOS_BUTTON.getPath());
                updateLayout.set(true);
                updateTexture.set(true);
                flag.set(true);
            } else {
                // 绘制弹出层选项
                popupOption.clear();
                // 若文件夹为空, 绘制提示, 并在点击时打开主题文件夹
                if (CollectionUtils.isNullOrEmpty(themeFileList)) {
                    TranslationTextComponent textComponent = new TranslationTextComponent("screen.sakura_sign_in.theme_selector.empty");
                    popupOption.addOption(StringUtils.replaceLine(textComponent.getString()).split("\n"));
                } else {
                    popupOption.addOption(themeFileList.stream().map(file -> {
                        String name = file.getName();
                        name = name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
                        return name;
                    }).toArray(String[]::new));
                }
                popupOption.setMaxWidth(font.width("cal_text_width"))
                        .setMaxLines(5)
                        .build(super.font, mouseX, mouseY, String.format("主题选择按钮:%s", value.getOperation()));
            }
        }
    }

    private void handleSignIn(int button, SignInCell cell, ClientPlayerEntity player) {
        Date cellDate = DateUtils.getDate(cell.year, cell.month, cell.day);
        if (cell.status == ESignInStatus.NOT_SIGNED_IN.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (RewardManager.getCompensateDateInt() < DateUtils.toDateInt(RewardManager.getCompensateDate(new Date()))) {
                    player.sendMessage(new StringTextComponent("前面的的日期以后再来探索吧。"), player.getUUID());
                } else {
                    cell.status = ClientConfig.AUTO_REWARDED.get() ? ESignInStatus.REWARDED.getCode() : ESignInStatus.SIGNED_IN.getCode();
                    ModNetworkHandler.INSTANCE.sendToServer(new SignInPacket(new Date(), ClientConfig.AUTO_REWARDED.get(), ESignInType.SIGN_IN));
                }
            }
        } else if (cell.status == ESignInStatus.SIGNED_IN.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                player.sendMessage(new StringTextComponent("已经签过到了哦。"), player.getUUID());
            } else {
                if (RewardManager.isRewarded(PlayerSignInDataCapability.getData(player), cellDate, false)) {
                    player.sendMessage(new StringTextComponent("不论怎么点也不会获取俩次奖励吧。"), player.getUUID());
                } else {
                    cell.status = ESignInStatus.REWARDED.getCode();
                    ModNetworkHandler.INSTANCE.sendToServer(new SignInPacket(cellDate, ClientConfig.AUTO_REWARDED.get(), ESignInType.REWARD));
                }
            }
        } else if (cell.status == ESignInStatus.CAN_REPAIR.getCode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (!ServerConfig.SIGN_IN_CARD.get()) {
                    player.sendMessage(new StringTextComponent("服务器未开启补签功能哦。"), player.getUUID());
                } else {
                    if (PlayerSignInDataCapability.getData(player).getSignInCard() <= 0) {
                        player.sendMessage(new StringTextComponent("补签卡不足了哦。"), player.getUUID());
                    } else {
                        cell.status = ClientConfig.AUTO_REWARDED.get() ? ESignInStatus.REWARDED.getCode() : ESignInStatus.SIGNED_IN.getCode();
                        ModNetworkHandler.INSTANCE.sendToServer(new SignInPacket(cellDate, ClientConfig.AUTO_REWARDED.get(), ESignInType.RE_SIGN_IN));
                    }
                }
            }
        } else if (cell.status == ESignInStatus.NO_ACTION.getCode()) {
            if (cellDate.after(RewardManager.getCompensateDate(new Date()))) {
                player.sendMessage(new StringTextComponent("前面的的日期以后再来探索吧。"), player.getUUID());
            } else {
                player.sendMessage(new StringTextComponent("后面的的日期怎么想也回不去了吧。"), player.getUUID());
            }
        } else if (cell.status == ESignInStatus.REWARDED.getCode()) {
            player.sendMessage(new StringTextComponent("不论怎么点也不会获取俩次奖励吧。"), player.getUUID());
        } else {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                player.sendMessage(new StringTextComponent(ESignInStatus.valueOf(cell.status).getDescription() + ": " + DateUtils.toString(cellDate)), player.getUUID());
            }
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        BUTTONS.forEach((key, value) -> value.setHovered(value.isMouseOverEx(mouseX, mouseY)));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!popupOption.addScrollOffset(delta)) {
            // 奖励悬浮层
            for (SignInCell cell : signInCells) {
                if (cell.isShowIcon() && cell.isShowHover() && cell.isMouseOver((int) mouseX, (int) mouseY)) {
                    if (delta > 0) {
                        cell.setTooltipScrollOffset(Math.max(cell.getTooltipScrollOffset() - 1, 0));
                    } else if (delta < 0) {
                        cell.setTooltipScrollOffset(Math.min(cell.getTooltipScrollOffset() + 1, cell.getRewardList().size() - SignInCell.TOOLTIP_MAX_VISIBLE_ITEMS));
                    }
                }
            }
        }
        return true;
    }

    /**
     * 重写keyPressed方法，处理键盘按键事件
     *
     * @param keyCode   按键的键码
     * @param scanCode  按键的扫描码
     * @param modifiers 按键时按下的修饰键（如Shift、Ctrl等）
     * @return boolean 表示是否消耗了该按键事件
     * <p>
     * 此方法主要监听特定的按键事件，当按下SIGN_IN_SCREEN_KEY或E键时，触发onClose方法，执行一些关闭操作
     * 对于其他按键，则交由父类处理
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        this.keyCode = keyCode;
        this.modifiers = modifiers;
        // 当按键等于SIGN_IN_SCREEN_KEY键的值或Inventory键时，调用onClose方法，并返回true，表示该按键事件已被消耗
        if (keyCode == ClientEventHandler.SIGN_IN_SCREEN_KEY.getKey().getValue() || keyCode == Minecraft.getInstance().options.keyInventory.getKey().getValue()) {
            this.onClose();
            return true;
        } else {
            // 对于其他按键，交由父类处理，并返回父类的处理结果
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        this.keyCode = -1;
        this.modifiers = -1;
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            SakuraSignIn.setCalendarCurrentDate(DateUtils.addMonth(SakuraSignIn.getCalendarCurrentDate(), -1));
            updateLayout();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            SakuraSignIn.setCalendarCurrentDate(DateUtils.addMonth(SakuraSignIn.getCalendarCurrentDate(), 1));
            updateLayout();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_UP) {
            SakuraSignIn.setCalendarCurrentDate(DateUtils.addYear(SakuraSignIn.getCalendarCurrentDate(), -1));
            updateLayout();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            SakuraSignIn.setCalendarCurrentDate(DateUtils.addYear(SakuraSignIn.getCalendarCurrentDate(), 1));
            updateLayout();
            return true;
        } else {
            // 对于其他按键，交由父类处理，并返回父类的处理结果
            return super.keyReleased(keyCode, scanCode, modifiers);
        }
    }
    //
    // /**
    //  * 窗口缩放时重新计算布局
    //  */
    // @Override
    // @ParametersAreNonnullByDefault
    // public void resize(Minecraft mc, int width, int height) {
    //     super.resize(mc, width, height);
    //     super.width = width;
    //     super.height = height;
    //     // 在窗口大小变化时更新布局
    //     updateLayout();
    //     LOGGER.debug("{},{}", super.width, super.height);
    // }

    /**
     * 窗口打开时是否暂停游戏
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
