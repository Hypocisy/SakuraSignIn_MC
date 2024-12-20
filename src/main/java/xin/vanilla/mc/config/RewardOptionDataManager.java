package xin.vanilla.mc.config;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.mc.SakuraSignIn;
import xin.vanilla.mc.enums.ERewardRule;
import xin.vanilla.mc.network.RewardOptionSyncData;
import xin.vanilla.mc.network.RewardOptionSyncPacket;
import xin.vanilla.mc.rewards.Reward;
import xin.vanilla.mc.rewards.RewardList;
import xin.vanilla.mc.util.DateUtils;
import xin.vanilla.mc.util.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RewardOptionDataManager {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();

    public static final String FILE_NAME = "reward_option_data.json";

    private static final Logger LOGGER = LogManager.getLogger();

    @Getter
    @Setter
    @NonNull
    private static RewardOptionData rewardOptionData = new RewardOptionData();
    @Getter
    @Setter
    private static boolean rewardOptionDataChanged = true;

    /**
     * 对 LinkedHashMap 按键排序后替换原内容
     */
    private static void replaceWithSortedMap(LinkedHashMap<String, RewardList> map) {
        LinkedHashMap<String, RewardList> sortedMap = map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(RewardOptionDataManager::keyComparator))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
        // 清空原始 Map 并插入排序后的数据
        map.clear();
        map.putAll(sortedMap);
    }

    /**
     * 自定义排序逻辑，用于比较键
     */
    private static int keyComparator(String key1, String key2) {
        try {
            // 尝试按数字比较
            return Long.compare(Long.parseLong(key1), Long.parseLong(key2));
        } catch (NumberFormatException e) {
            // 如果不是数字，按字母顺序比较
            return key1.compareTo(key2);
        }
    }

    /**
     * 获取配置文件路径
     */
    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(SakuraSignIn.MODID);
    }

    /**
     * 加载 JSON 数据
     */
    public static void loadRewardOption() {
        File file = new File(RewardOptionDataManager.getConfigDirectory().toFile(), FILE_NAME);
        if (file.exists()) {
            try {
                rewardOptionData = RewardOptionDataManager.deserializeRewardOption(new String(Files.readAllBytes(Paths.get(file.getPath()))));
            } catch (IOException e) {
                LOGGER.error("Error loading sign-in data: ", e);
            }
        } else {
            // 如果文件不存在，初始化默认值
            rewardOptionData = RewardOptionData.getDefault();
            RewardOptionDataManager.saveRewardOption();
        }
    }

    /**
     * 保存 JSON 数据
     */
    public static void saveRewardOption() {
        File dir = RewardOptionDataManager.getConfigDirectory().toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, FILE_NAME);
        try (FileWriter writer = new FileWriter(file)) {
            // 格式化输出
            Gson gson = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();
            writer.write(gson.toJson(rewardOptionData.toJsonObject()));
        } catch (IOException e) {
            LOGGER.error("Error saving sign-in data: ", e);
        }
    }

    /**
     * 备份 JSON 数据
     */
    public static void backupRewardOption() {
        RewardOptionDataManager.backupRewardOption(true);
    }

    /**
     * 备份 JSON 数据
     */
    public static void backupRewardOption(boolean save) {
        // 备份文件
        long dateTimeInt = DateUtils.toDateTimeInt(new Date());
        File sourceFolder = FMLPaths.CONFIGDIR.get().resolve(SakuraSignIn.MODID).toFile();
        try {
            File target = new File(new File(sourceFolder, "backups"), String.format("%s_%s.%s", RewardOptionDataManager.FILE_NAME, dateTimeInt, "old"));
            if (target.getParent() != null && !Files.exists(target.getParentFile().toPath())) {
                Files.createDirectories(target.getParentFile().toPath());
            }
            Files.move(new File(sourceFolder, RewardOptionDataManager.FILE_NAME).toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Error moving file: ", e);
        }
        // 备份最新编辑的文件
        if (save) {
            RewardOptionDataManager.saveRewardOption();
            try {
                File target = new File(new File(sourceFolder, "backups"), String.format("%s_%s.%s", RewardOptionDataManager.FILE_NAME, dateTimeInt, "bak"));
                if (target.getParent() != null && !Files.exists(target.getParentFile().toPath())) {
                    Files.createDirectories(target.getParentFile().toPath());
                }
                Files.move(new File(sourceFolder, RewardOptionDataManager.FILE_NAME).toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.error("Error moving file: ", e);
            }
        }
        // 删除旧文件
        try (Stream<Path> pathStream = Files.walk(sourceFolder.toPath())) {
            pathStream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().startsWith(RewardOptionDataManager.FILE_NAME))
                    .sorted((path1, path2) -> {
                        try {
                            return Files.readAttributes(path2, BasicFileAttributes.class).creationTime()
                                    .compareTo(Files.readAttributes(path1, BasicFileAttributes.class).creationTime());
                        } catch (IOException e) {
                            LOGGER.error("Error reading file attributes: ", e);
                            return 0;
                        }
                    })
                    // 跳过最新的20个文件
                    .skip(20)
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            LOGGER.error("Error deleting file: ", e);
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Error walking directory: ", e);
        }
    }

    /**
     * 校验 keyName 是否有效
     *
     * @param rule    规则类型
     * @param keyName 键名
     */
    public static boolean validateKeyName(@NonNull ERewardRule rule, @NonNull String keyName) {
        boolean result;
        switch (rule) {
            case BASE_REWARD:
                throw new IllegalArgumentException("Base reward has no key name");
            case CONTINUOUS_REWARD:
            case CYCLE_REWARD:
                result = StringUtils.toInt(keyName) > 0;
                break;
            case YEAR_REWARD: {
                int anInt = StringUtils.toInt(keyName);
                result = anInt > 0 && anInt <= 366;
            }
            break;
            case MONTH_REWARD: {
                int anInt = StringUtils.toInt(keyName);
                result = anInt > 0 && anInt <= 31;
            }
            break;
            case WEEK_REWARD: {
                int anInt = StringUtils.toInt(keyName);
                result = anInt > 0 && anInt <= 7;
            }
            break;
            case DATE_TIME_REWARD:
                result = !RewardOptionData.parseDateRange(keyName).isEmpty();
                break;
            case CUMULATIVE_REWARD: {
                int anInt = StringUtils.toInt(keyName);
                result = anInt > 0;
            }
            break;
            default:
                result = false;
        }
        return result;
    }

    /**
     * 获取奖励规则
     *
     * @param rule    规则类型
     * @param keyName 规则
     */
    @NonNull
    public static RewardList getKeyName(@NonNull ERewardRule rule, @NonNull String keyName) {
        RewardList result;
        switch (rule) {
            case BASE_REWARD:
                result = rewardOptionData.getBaseRewards();
                break;
            case CONTINUOUS_REWARD:
                result = rewardOptionData.getContinuousRewards().get(keyName);
                break;
            case CYCLE_REWARD:
                result = rewardOptionData.getCycleRewards().get(keyName);
                break;
            case YEAR_REWARD:
                result = rewardOptionData.getYearRewards().get(keyName);
                break;
            case MONTH_REWARD:
                result = rewardOptionData.getMonthRewards().get(keyName);
                break;
            case WEEK_REWARD:
                result = rewardOptionData.getWeekRewards().get(keyName);
                break;
            case DATE_TIME_REWARD:
                result = rewardOptionData.getDateTimeRewards().get(keyName);
                break;
            case CUMULATIVE_REWARD:
                result = rewardOptionData.getCumulativeRewards().get(keyName);
                break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
        return result == null ? new RewardList() : result;
    }

    /**
     * 添加奖励规则
     *
     * @param rule       规则类型
     * @param keyName    规则
     * @param rewardList 奖励列表
     */
    public static void addKeyName(@NonNull ERewardRule rule, @NonNull String keyName, @NonNull RewardList rewardList) {
        switch (rule) {
            case BASE_REWARD:
                rewardOptionData.setBaseRewards(rewardList);
                break;
            case CONTINUOUS_REWARD:
                rewardOptionData.addContinuousRewards(keyName, rewardList);
                break;
            case CYCLE_REWARD:
                rewardOptionData.addCycleRewards(keyName, rewardList);
                break;
            case YEAR_REWARD:
                rewardOptionData.addYearRewards(keyName, rewardList);
                break;
            case MONTH_REWARD:
                rewardOptionData.addMonthRewards(keyName, rewardList);
                break;
            case WEEK_REWARD:
                rewardOptionData.addWeekRewards(keyName, rewardList);
                break;
            case DATE_TIME_REWARD:
                rewardOptionData.addDateTimeRewards(keyName, rewardList);
                break;
            case CUMULATIVE_REWARD:
                rewardOptionData.addCumulativeReward(keyName, rewardList);
                break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
    }

    /**
     * 修改奖励规则的 keyName
     *
     * @param rule       规则类型
     * @param oldKeyName 旧的 keyName
     * @param newKeyName 新的 keyName
     */
    public static void updateKeyName(@NonNull ERewardRule rule, @NonNull String oldKeyName, @NonNull String newKeyName) {
        switch (rule) {
            case BASE_REWARD:
                throw new IllegalArgumentException("Base reward has no key name");
            case CONTINUOUS_REWARD: {
                RewardList remove = rewardOptionData.getContinuousRewards().remove(oldKeyName);
                rewardOptionData.addContinuousRewards(newKeyName, remove);
            }
            break;
            case CYCLE_REWARD: {
                RewardList remove = rewardOptionData.getCycleRewards().remove(oldKeyName);
                rewardOptionData.addCycleRewards(newKeyName, remove);
            }
            break;
            case YEAR_REWARD: {
                RewardList remove = rewardOptionData.getYearRewards().remove(oldKeyName);
                rewardOptionData.addYearRewards(newKeyName, remove);
            }
            break;
            case MONTH_REWARD: {
                RewardList remove = rewardOptionData.getMonthRewards().remove(oldKeyName);
                rewardOptionData.addMonthRewards(newKeyName, remove);
            }
            break;
            case WEEK_REWARD: {
                RewardList remove = rewardOptionData.getWeekRewards().remove(oldKeyName);
                rewardOptionData.addWeekRewards(newKeyName, remove);
            }
            break;
            case DATE_TIME_REWARD: {
                RewardList remove = rewardOptionData.getDateTimeRewards().remove(oldKeyName);
                rewardOptionData.addDateTimeRewards(newKeyName, remove);
            }
            break;
            case CUMULATIVE_REWARD: {
                RewardList remove = rewardOptionData.getCumulativeRewards().remove(oldKeyName);
                rewardOptionData.addCumulativeReward(newKeyName, remove);
            }
            break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
    }

    /**
     * 清空奖励规则 keyName 下的奖励列表
     *
     * @param rule    规则类型
     * @param keyName 规则
     */
    public static void clearKey(@NonNull ERewardRule rule, @NonNull String keyName) {
        switch (rule) {
            case BASE_REWARD:
                rewardOptionData.getBaseRewards().clear();
                break;
            case CONTINUOUS_REWARD:
                rewardOptionData.getContinuousRewards().get(keyName).clear();
                break;
            case CYCLE_REWARD:
                rewardOptionData.getCycleRewards().get(keyName).clear();
                break;
            case YEAR_REWARD:
                rewardOptionData.getYearRewards().get(keyName).clear();
                break;
            case MONTH_REWARD:
                rewardOptionData.getMonthRewards().get(keyName).clear();
                break;
            case WEEK_REWARD:
                rewardOptionData.getWeekRewards().get(keyName).clear();
                break;
            case DATE_TIME_REWARD:
                rewardOptionData.getDateTimeRewards().get(keyName).clear();
                break;
            case CUMULATIVE_REWARD:
                rewardOptionData.getCumulativeRewards().get(keyName).clear();
                break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
    }

    /**
     * 删除奖励规则
     *
     * @param rule    规则类型
     * @param keyName 规则
     */
    public static void deleteKey(@NonNull ERewardRule rule, @NonNull String keyName) {
        switch (rule) {
            case BASE_REWARD:
                throw new IllegalArgumentException("Base reward has no key name");
            case CONTINUOUS_REWARD:
                rewardOptionData.getContinuousRewards().remove(keyName);
                break;
            case CYCLE_REWARD:
                rewardOptionData.getCycleRewards().remove(keyName);
                break;
            case YEAR_REWARD:
                rewardOptionData.getYearRewards().remove(keyName);
                break;
            case MONTH_REWARD:
                rewardOptionData.getMonthRewards().remove(keyName);
                break;
            case WEEK_REWARD:
                rewardOptionData.getWeekRewards().remove(keyName);
                break;
            case DATE_TIME_REWARD:
                rewardOptionData.getDateTimeRewards().remove(keyName);
                break;
            case CUMULATIVE_REWARD:
                rewardOptionData.getCumulativeRewards().remove(keyName);
                break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
    }

    /**
     * 获取奖励规则下的奖励
     *
     * @param rule    规则类型
     * @param keyName 规则
     * @param index   奖励索引
     */
    @NonNull
    public static Reward getReward(ERewardRule rule, String keyName, int index) {
        Reward result;
        try {
            switch (rule) {
                case BASE_REWARD:
                    result = rewardOptionData.getBaseRewards().get(index);
                    break;
                case CONTINUOUS_REWARD:
                    result = rewardOptionData.getContinuousRewards().get(keyName).get(index);
                    break;
                case CYCLE_REWARD:
                    result = rewardOptionData.getCycleRewards().get(keyName).get(index);
                    break;
                case YEAR_REWARD:
                    result = rewardOptionData.getYearRewards().get(keyName).get(index);
                    break;
                case MONTH_REWARD:
                    result = rewardOptionData.getMonthRewards().get(keyName).get(index);
                    break;
                case WEEK_REWARD:
                    result = rewardOptionData.getWeekRewards().get(keyName).get(index);
                    break;
                case DATE_TIME_REWARD:
                    result = rewardOptionData.getDateTimeRewards().get(keyName).get(index);
                    break;
                case CUMULATIVE_REWARD:
                    result = rewardOptionData.getCumulativeRewards().get(keyName).get(index);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown rule: " + rule);
            }
        } catch (Exception ignored) {
            result = new Reward();
        }
        return result == null ? new Reward() : result;
    }

    /**
     * 添加奖励规则下的奖励
     *
     * @param rule    规则类型
     * @param keyName 规则
     * @param reward  奖励
     */
    public static void addReward(ERewardRule rule, String keyName, Reward reward) {
        if (StringUtils.isNullOrEmpty(keyName)) return;
        switch (rule) {
            case BASE_REWARD:
                rewardOptionData.getBaseRewards().add(reward);
                break;
            case CONTINUOUS_REWARD:
                if (!rewardOptionData.getContinuousRewards().containsKey(keyName)) {
                    rewardOptionData.getContinuousRewards().put(keyName, new RewardList());
                }
                rewardOptionData.getContinuousRewards().get(keyName).add(reward);
                break;
            case CYCLE_REWARD:
                if (!rewardOptionData.getCycleRewards().containsKey(keyName)) {
                    rewardOptionData.getCycleRewards().put(keyName, new RewardList());
                }
                rewardOptionData.getCycleRewards().get(keyName).add(reward);
                break;
            case YEAR_REWARD:
                if (!rewardOptionData.getYearRewards().containsKey(keyName)) {
                    rewardOptionData.getYearRewards().put(keyName, new RewardList());
                }
                rewardOptionData.getYearRewards().get(keyName).add(reward);
                break;
            case MONTH_REWARD:
                if (!rewardOptionData.getMonthRewards().containsKey(keyName)) {
                    rewardOptionData.getMonthRewards().put(keyName, new RewardList());
                }
                rewardOptionData.getMonthRewards().get(keyName).add(reward);
                break;
            case WEEK_REWARD:
                if (!rewardOptionData.getWeekRewards().containsKey(keyName)) {
                    rewardOptionData.getWeekRewards().put(keyName, new RewardList());
                }
                rewardOptionData.getWeekRewards().get(keyName).add(reward);
                break;
            case DATE_TIME_REWARD:
                if (!rewardOptionData.getDateTimeRewards().containsKey(keyName)) {
                    rewardOptionData.getDateTimeRewards().put(keyName, new RewardList());
                }
                rewardOptionData.getDateTimeRewards().get(keyName).add(reward);
                break;
            case CUMULATIVE_REWARD:
                if (!rewardOptionData.getCumulativeRewards().containsKey(keyName)) {
                    rewardOptionData.getCumulativeRewards().put(keyName, new RewardList());
                }
                rewardOptionData.getCumulativeRewards().get(keyName).add(reward);
                break;
            default:
                throw new IllegalArgumentException("Unknown rule: " + rule);
        }
    }

    /**
     * 更新奖励规则下的奖励
     *
     * @param rule    规则类型
     * @param keyName 规则
     * @param index   奖励索引
     * @param reward  奖励
     */
    public static void updateReward(ERewardRule rule, String keyName, int index, Reward reward) {
        try {
            switch (rule) {
                case BASE_REWARD:
                    rewardOptionData.getBaseRewards().set(index, reward);
                    break;
                case CONTINUOUS_REWARD:
                    if (!rewardOptionData.getContinuousRewards().containsKey(keyName)) {
                        rewardOptionData.getContinuousRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardOptionData.getContinuousRewards().get(keyName).set(index, reward);
                    break;
                case CYCLE_REWARD:
                    if (!rewardOptionData.getCycleRewards().containsKey(keyName)) {
                        rewardOptionData.getCycleRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardOptionData.getCycleRewards().get(keyName).set(index, reward);
                    break;
                case YEAR_REWARD:
                    if (!rewardOptionData.getYearRewards().containsKey(keyName)) {
                        rewardOptionData.getYearRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardOptionData.getYearRewards().get(keyName).set(index, reward);
                    break;
                case MONTH_REWARD:
                    if (!rewardOptionData.getMonthRewards().containsKey(keyName)) {
                        rewardOptionData.getMonthRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardOptionData.getMonthRewards().get(keyName).set(index, reward);
                    break;
                case WEEK_REWARD:
                    if (!rewardOptionData.getWeekRewards().containsKey(keyName)) {
                        rewardOptionData.getWeekRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardOptionData.getWeekRewards().get(keyName).set(index, reward);
                    break;
                case DATE_TIME_REWARD:
                    if (!rewardOptionData.getDateTimeRewards().containsKey(keyName)) {
                        rewardOptionData.getDateTimeRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardOptionData.getDateTimeRewards().get(keyName).set(index, reward);
                    break;
                case CUMULATIVE_REWARD:
                    if (!rewardOptionData.getCumulativeRewards().containsKey(keyName)) {
                        rewardOptionData.getCumulativeRewards().put(keyName, new RewardList() {{
                            add(reward);
                        }});
                    }
                    rewardOptionData.getCumulativeRewards().get(keyName).set(index, reward);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown rule: " + rule);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 删除奖励规则下的奖励
     *
     * @param rule    规则类型
     * @param keyName 规则
     * @param index   奖励索引
     */
    public static void deleteReward(ERewardRule rule, String keyName, int index) {
        try {
            switch (rule) {
                case BASE_REWARD:
                    rewardOptionData.getBaseRewards().remove(index);
                    break;
                case CONTINUOUS_REWARD:
                    rewardOptionData.getContinuousRewards().get(keyName).remove(index);
                    break;
                case CYCLE_REWARD:
                    rewardOptionData.getCycleRewards().get(keyName).remove(index);
                    break;
                case YEAR_REWARD:
                    rewardOptionData.getYearRewards().get(keyName).remove(index);
                    break;
                case MONTH_REWARD:
                    rewardOptionData.getMonthRewards().get(keyName).remove(index);
                    break;
                case WEEK_REWARD:
                    rewardOptionData.getWeekRewards().get(keyName).remove(index);
                    break;
                case DATE_TIME_REWARD:
                    rewardOptionData.getDateTimeRewards().get(keyName).remove(index);
                    break;
                case CUMULATIVE_REWARD:
                    rewardOptionData.getCumulativeRewards().get(keyName).remove(index);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown rule: " + rule);
            }
        } catch (Exception e) {
            LOGGER.error("Error deleting reward: ", e);
        }
    }

    /**
     * 排序奖励配置
     */
    public static void sortRewards() {
        RewardOptionDataManager.sortRewards(null);
    }

    /**
     * 排序奖励配置
     *
     * @param rule 规则类型
     */
    public static void sortRewards(ERewardRule rule) {
        List<ERewardRule> rules;
        if (rule == null) {
            rules = Arrays.asList(ERewardRule.values());
        } else {
            rules = Collections.singletonList(rule);
        }
        for (ERewardRule rewardRule : rules) {
            switch (rewardRule) {
                case BASE_REWARD:
                    break;
                case CONTINUOUS_REWARD:
                    // 对键排序并替换原始 Map 的内容
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardOptionData.getContinuousRewards());
                    break;
                case CYCLE_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardOptionData.getCycleRewards());
                    break;
                case YEAR_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardOptionData.getYearRewards());
                    break;
                case MONTH_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardOptionData.getMonthRewards());
                    break;
                case WEEK_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardOptionData.getWeekRewards());
                    break;
                case DATE_TIME_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardOptionData.getDateTimeRewards());
                    break;
                case CUMULATIVE_REWARD:
                    replaceWithSortedMap((LinkedHashMap<String, RewardList>) rewardOptionData.getCumulativeRewards());
                    break;
            }
        }
    }

    /**
     * 序列化 RewardOption
     */
    public static String serializeRewardOption(RewardOptionData rewardOptionData) {
        return GSON.toJson(rewardOptionData.toJsonObject());
    }

    /**
     * 反序列化 RewardOption
     */
    public static RewardOptionData deserializeRewardOption(String jsonString) {
        RewardOptionData result = new RewardOptionData();
        if (StringUtils.isNotNullOrEmpty(jsonString)) {
            try {
                JsonObject jsonObject = GSON.fromJson(jsonString, JsonObject.class);
                result.setBaseRewards(GSON.fromJson(jsonObject.get("baseRewards"), new TypeToken<RewardList>() {
                }.getType()));
                result.setContinuousRewards(GSON.fromJson(jsonObject.get("continuousRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
                result.setCycleRewards(GSON.fromJson(jsonObject.get("cycleRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
                result.setYearRewards(GSON.fromJson(jsonObject.get("yearRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
                result.setMonthRewards(GSON.fromJson(jsonObject.get("monthRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
                result.setWeekRewards(GSON.fromJson(jsonObject.get("weekRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
                result.setDateTimeRewards(GSON.fromJson(jsonObject.get("dateTimeRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
                result.setCumulativeRewards(GSON.fromJson(jsonObject.get("cumulativeRewards"), new TypeToken<LinkedHashMap<String, RewardList>>() {
                }.getType()));
            } catch (JsonSyntaxException | JsonIOException e) {
                LOGGER.error("Error loading sign-in data: ", e);
            }
        } else {
            // 如果文件不存在，初始化默认值
            result = RewardOptionData.getDefault();
            RewardOptionDataManager.saveRewardOption();
        }
        return result;
    }

    public static Map<String, RewardList> getRewardMap(ERewardRule rule) {
        Map<String, RewardList> result = new LinkedHashMap<>();
        switch (rule) {
            case BASE_REWARD:
                result.put("base", rewardOptionData.getBaseRewards());
                break;
            case CONTINUOUS_REWARD:
                result = rewardOptionData.getContinuousRewards();
                break;
            case CYCLE_REWARD:
                result = rewardOptionData.getCycleRewards();
                break;
            case YEAR_REWARD:
                result = rewardOptionData.getYearRewards();
                break;
            case MONTH_REWARD:
                result = rewardOptionData.getMonthRewards();
                break;
            case WEEK_REWARD:
                result = rewardOptionData.getWeekRewards();
                break;
            case DATE_TIME_REWARD:
                result = rewardOptionData.getDateTimeRewards();
                break;
            case CUMULATIVE_REWARD:
                result = rewardOptionData.getCumulativeRewards();
                break;
        }
        return result;
    }

    public static void setRewardMap(RewardOptionData data, ERewardRule rule, Map<String, RewardList> map) {
        switch (rule) {
            case BASE_REWARD:
                data.setBaseRewards(map.get("base"));
                break;
            case CONTINUOUS_REWARD:
                data.setContinuousRewards(map);
                break;
            case CYCLE_REWARD:
                data.setCycleRewards(map);
                break;
            case YEAR_REWARD:
                data.setYearRewards(map);
                break;
            case MONTH_REWARD:
                data.setMonthRewards(map);
                break;
            case WEEK_REWARD:
                data.setWeekRewards(map);
                break;
            case DATE_TIME_REWARD:
                data.setDateTimeRewards(map);
                break;
            case CUMULATIVE_REWARD:
                data.setCumulativeRewards(map);
                break;
        }
    }

    public static RewardOptionSyncPacket toSyncPacket() {
        List<RewardOptionSyncData> dataList = new ArrayList<>();
        for (ERewardRule rule : ERewardRule.values()) {
            RewardOptionDataManager.getRewardMap(rule).forEach((key, value) -> {
                List<RewardOptionSyncData> list = value.stream()
                        .map(reward -> new RewardOptionSyncData(rule, key, reward))
                        .collect(Collectors.toList());
                dataList.addAll(list);
            });
        }
        return new RewardOptionSyncPacket(dataList);
    }

    public static RewardOptionData fromSyncPacketList(List<RewardOptionSyncPacket> packetList) {
        RewardOptionData result = new RewardOptionData();
        packetList.stream().flatMap(packet -> packet.getRewardOptionData().stream()).collect(Collectors.groupingBy(RewardOptionSyncData::getRule)).forEach((rule, dataList) -> {
            Map<String, RewardList> rewardMap = new LinkedHashMap<>();
            dataList.forEach(data -> {
                RewardList rewardList = rewardMap.computeIfAbsent(data.getKey(), key -> new RewardList());
                rewardList.add(data.getReward());
            });
            RewardOptionDataManager.setRewardMap(result, rule, rewardMap);
        });
        return result;
    }
}
