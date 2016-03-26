package com.intellectualcrafters.plot.object;

import com.intellectualcrafters.plot.config.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class Rating {
    /**
     * This is a map of the rating category to the rating value
     */
    private final HashMap<String, Integer> ratingMap;
    private final int initial;
    private boolean changed;

    public Rating(int value) {
        this.initial = value;
        this.ratingMap = new HashMap<>();
        if ((Settings.RATING_CATEGORIES != null) && (Settings.RATING_CATEGORIES.size() > 1)) {
            if (value < 10) {
                for (String ratingCategory : Settings.RATING_CATEGORIES) {
                    this.ratingMap.put(ratingCategory, value);
                }
                this.changed = true;
                return;
            }
            for (String ratingCategory : Settings.RATING_CATEGORIES) {
                this.ratingMap.put(ratingCategory, (value % 10) - 1);
                value = value / 10;
            }
        } else {
            this.ratingMap.put(null, value);
        }
    }

    public List<String> getCategories() {
        if (this.ratingMap.size() == 1) {
            return new ArrayList<>(0);
        }
        return new ArrayList<>(this.ratingMap.keySet());
    }

    public double getAverageRating() {
        double total = 0;
        for (Entry<String, Integer> entry : this.ratingMap.entrySet()) {
            total += entry.getValue();
        }
        return total / this.ratingMap.size();
    }

    public Integer getRating(String category) {
        return this.ratingMap.get(category);
    }

    public boolean setRating(String category, int value) {
        this.changed = true;
        if (!this.ratingMap.containsKey(category)) {
            return false;
        }
        return this.ratingMap.put(category, value) != null;
    }

    public int getAggregate() {
        if (!this.changed) {
            return this.initial;
        }
        if ((Settings.RATING_CATEGORIES != null) && (Settings.RATING_CATEGORIES.size() > 1)) {
            int val = 0;
            for (int i = 0; i < Settings.RATING_CATEGORIES.size(); i++) {
                val += (i + 1) * Math.pow(10, this.ratingMap.get(Settings.RATING_CATEGORIES.get(i)));
            }
            return val;
        } else {
            return this.ratingMap.get(null);
        }

    }
}
