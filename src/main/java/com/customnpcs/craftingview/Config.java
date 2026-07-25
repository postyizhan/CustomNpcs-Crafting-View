package com.customnpcs.craftingview;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.common.Configuration;

public class Config {

    private static final List<CategoryDefinition> categoriesMutable = new ArrayList();
    private static final List<CategoryDefinition> workbenchCategoriesMutable = new ArrayList();
    public static final List<CategoryDefinition> categories = Collections.unmodifiableList(categoriesMutable);
    public static final List<CategoryDefinition> workbenchCategories = Collections.unmodifiableList(workbenchCategoriesMutable);

    // 开启后，配方查看面板中每个条目显示产物名字，而不是配方名。
    public static boolean useOutputNameAsLabel = false;

    public static void load(File configFile) {
        Configuration cfg = new Configuration(configFile);
        try {
            cfg.load();

            useOutputNameAsLabel = cfg.get(
                "general", "use_output_name_as_label", false,
                "开启后，配方查看面板中每个条目将显示产物的名字，而不是配方名").getBoolean(false);

            categoriesMutable.clear();
            int count = countCategories(cfg, "categories");
            if (count == 0) {
                cfg.get("categories", "category_0", "example||Gun Wooden,Gun Stone,Gun Iron,Gun Gold,Gun Diamond,Gun Emerald,Gun Bronze|").comment =
                    "格式: 分类名|recipeId1,recipeId2,...|recipeName1,recipeName2,...  留空表示不过滤。此分组只用于 CustomNPCs 木工台/铁砧侧栏";
                count = 1;
            }
            loadCategories(cfg, "categories", count, categoriesMutable);

            workbenchCategoriesMutable.clear();
            int workbenchCount = countCategories(cfg, "workbench_categories");
            if (workbenchCount == 0) {
                cfg.get("workbench_categories", "category_0", "example_workbench||Npc Wand,Npc Cloner,Carpentry Bench,Mana|").comment =
                    "格式: 分类名|recipeId1,recipeId2,...|recipeName1,recipeName2,...  留空表示不过滤。此分组只用于暮色拆解台/原版 3x3 工作台配方侧栏";
                workbenchCount = 1;
            }
            loadCategories(cfg, "workbench_categories", workbenchCount, workbenchCategoriesMutable);
        } finally {
            if (cfg.hasChanged()) cfg.save();
        }
    }

    private static int countCategories(Configuration cfg, String section) {
        int count = 0;
        while (cfg.hasKey(section, "category_" + count)) {
            count++;
        }
        return count;
    }

    private static void loadCategories(Configuration cfg, String section, int count, List target) {
        for (int i = 0; i < count; i++) {
            String raw = cfg.get(section, "category_" + i, "").getString();
            CategoryDefinition def = parse(raw);
            if (def != null) target.add(def);
        }
    }

    private static CategoryDefinition parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String[] parts = raw.split("\\|", -1);
        String name = parts.length > 0 ? parts[0].trim() : "";
        if (name.isEmpty()) return null;

        List ids = new ArrayList();
        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
            for (String s : parts[1].split(",")) {
                try { ids.add(Integer.valueOf(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }

        List names = new ArrayList();
        if (parts.length > 2 && !parts[2].trim().isEmpty()) {
            for (String s : parts[2].split(",")) {
                String n = s.trim();
                if (!n.isEmpty()) names.add(n.toLowerCase());
            }
        }

        return new CategoryDefinition(name, ids, names);
    }

    public static class CategoryDefinition {
        public final String name;
        public final List recipeIds;
        public final List recipeNames;

        public CategoryDefinition(String name, List recipeIds, List recipeNames) {
            this.name = name;
            this.recipeIds = Collections.unmodifiableList(recipeIds);
            this.recipeNames = Collections.unmodifiableList(recipeNames);
        }
    }
}
