package com.customnpcs.craftingview;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static final List<CategoryDefinition> categoriesMutable = new ArrayList<>();
    public static final List<CategoryDefinition> categories = Collections.unmodifiableList(categoriesMutable);

    public static void load(File configFile) {
        Configuration cfg = new Configuration(configFile);
        try {
            cfg.load();

            // Count how many categories are defined
            int count = 0;
            while (cfg.hasKey("categories", "category_" + count)) {
                count++;
            }

            // If none exist yet, write a default example then parse it
            if (count == 0) {
                cfg.get(
                    "categories",
                    "category_0",
                    "example||Gun Wooden,Gun Stone,Gun Iron,Gun Gold,Gun Diamond,Gun Emerald,Gun Bronze|").comment = "格式: 分类名|recipeId1,recipeId2,...|recipeName1,recipeName2,...  留空表示不过滤";
                count = 1;
            }

            categoriesMutable.clear();
            for (int i = 0; i < count; i++) {
                String raw = cfg.get("categories", "category_" + i, "")
                    .getString();
                CategoryDefinition def = parse(raw);
                if (def != null) categoriesMutable.add(def);
            }
        } finally {
            if (cfg.hasChanged()) cfg.save();
        }
    }

    private static CategoryDefinition parse(String raw) {
        if (raw == null || raw.trim()
            .isEmpty()) return null;
        String[] parts = raw.split("\\|", -1);
        String name = parts.length > 0 ? parts[0].trim() : "";
        if (name.isEmpty()) return null;

        List<Integer> ids = new ArrayList<>();
        if (parts.length > 1 && !parts[1].trim()
            .isEmpty()) {
            for (String s : parts[1].split(",")) {
                try {
                    ids.add(Integer.parseInt(s.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }

        List<String> names = new ArrayList<>();
        if (parts.length > 2 && !parts[2].trim()
            .isEmpty()) {
            for (String s : parts[2].split(",")) {
                String n = s.trim();
                if (!n.isEmpty()) names.add(n.toLowerCase());
            }
        }

        return new CategoryDefinition(name, ids, names);
    }

    public static class CategoryDefinition {

        public final String name;
        public final List<Integer> recipeIds;
        public final List<String> recipeNames;

        // 性能优化：将 recipeNames 转为 HashSet，加速分类匹配（O(1) vs O(n)）
        public final Set<String> recipeNamesSet;

        public CategoryDefinition(String name, List<Integer> recipeIds, List<String> recipeNames) {
            this.name = name;
            this.recipeIds = Collections.unmodifiableList(recipeIds);
            this.recipeNames = Collections.unmodifiableList(recipeNames);
            this.recipeNamesSet = Collections.unmodifiableSet(new HashSet<>(recipeNames));
        }
    }
}
