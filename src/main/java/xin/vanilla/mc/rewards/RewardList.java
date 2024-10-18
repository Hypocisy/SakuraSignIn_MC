package xin.vanilla.mc.rewards;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RewardList extends ArrayList<Reward> implements Serializable, Cloneable {
    public RewardList() {
    }

    public RewardList(Collection<Reward> collection) {
        super(collection);
    }

    @Override
    public RewardList clone() {
        RewardList cloned = (RewardList) super.clone();
        List<Reward> clonedRewards = new ArrayList<>();
        for (Reward reward : this) {
            clonedRewards.add(reward.clone());
        }
        cloned.clear();
        cloned.addAll(clonedRewards);
        return cloned;
    }
}
