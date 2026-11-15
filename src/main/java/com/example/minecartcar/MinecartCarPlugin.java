package com.example.minecartcar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MinecartCarPlugin extends JavaPlugin implements Listener {

    private static final int TICKS_PER_COAL = 1200;
    private static final int TICKS_PER_COAL_BLOCK = 10800;

    // Передачи: -1 = R (Задняя), 0 = N (Нейтраль), 1 = 1-я, 2 = 2-я, 3 = 3-я
    private static final int GEAR_REVERSE = -1;
    private static final int GEAR_NEUTRAL = 0;
    private static final int GEAR_FIRST = 1;
    private static final int GEAR_SECOND = 2;
    private static final int GEAR_THIRD = 3;

    private NamespacedKey carKey;
    private NamespacedKey carPartKey;
    private NamespacedKey partTypeKey;
    private NamespacedKey headlightsKey;
    private NamespacedKey engineKey;
    private NamespacedKey fuelTicksKey;
    private NamespacedKey tankDataKey;
    private NamespacedKey trunkDataKey;
    private NamespacedKey gearKey;
    private int tickCounter = 0;

    private final Set<UUID> trackedCars = new HashSet<>();
    private final Map<UUID, List<Block>> activeLightBlocks = new HashMap<>();
    private final Map<UUID, Inventory> openTanks = new HashMap<>();
    private final Map<UUID, Inventory> openTrunks = new HashMap<>();
    private final Map<UUID, Integer> stepCooldowns = new HashMap<>();
    private final Map<UUID, List<BlockDisplay>> carModels = new HashMap<>();

    private static class FuelTankHolder implements InventoryHolder {
        private final UUID carId;
        private Inventory inventory;

        public FuelTankHolder(UUID carId) {
            this.carId = carId;
        }

        public UUID getCarId() {
            return carId;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static class TrunkHolder implements InventoryHolder {
        private final UUID carId;
        private Inventory inventory;

        public TrunkHolder(UUID carId) {
            this.carId = carId;
        }

        public UUID getCarId() {
            return carId;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void onEnable() {
        carKey = new NamespacedKey(this, "custom_car");
        carPartKey = new NamespacedKey(this, "car_model_part");
        partTypeKey = new NamespacedKey(this, "car_part_type");
        headlightsKey = new NamespacedKey(this, "headlights_on");
        engineKey = new NamespacedKey(this, "engine_on");
        fuelTicksKey = new NamespacedKey(this, "fuel_ticks");
        tankDataKey = new NamespacedKey(this, "fuel_tank_items");
        trunkDataKey = new NamespacedKey(this, "car_trunk_items");
        gearKey = new NamespacedKey(this, "current_gear");

        Bukkit.getPluginManager().registerEvents(this, this);
        registerCarRecipe();

        // Очищаем старые осиротевшие детали 3D-модели и подхватываем существующие машины
        for (World world : Bukkit.getWorlds()) {
            for (BlockDisplay bd : world.getEntitiesByClass(BlockDisplay.class)) {
                if (bd.getPersistentDataContainer().has(carPartKey, PersistentDataType.STRING)) {
                    bd.remove();
                }
            }
            for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
                if (isCar(cart)) {
                    cart.setInvisible(true);
                    cart.setCustomNameVisible(false);
                    trackedCars.add(cart.getUniqueId());
                    spawnCarModel(cart);
                }
            }
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            tickCounter++;
            Iterator<UUID> it = trackedCars.iterator();
            while (it.hasNext()) {
                UUID id = it.next();
                Entity entity = Bukkit.getEntity(id);
                if (!(entity instanceof Minecart cart) || !cart.isValid()) {
                    clearLights(id);
                    removeCarModel(id);
                    stepCooldowns.remove(id);
                    it.remove();
                    continue;
                }
                tickCar(cart);
            }
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        for (UUID id : new HashSet<>(openTanks.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Minecart cart) {
                saveInventory(cart, tankDataKey, openTanks.get(id));
            }
        }
        openTanks.clear();

        for (UUID id : new HashSet<>(openTrunks.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Minecart cart) {
                saveInventory(cart, trunkDataKey, openTrunks.get(id));
            }
        }
        openTrunks.clear();

        for (UUID id : new HashSet<>(activeLightBlocks.keySet())) {
            clearLights(id);
        }
        activeLightBlocks.clear();

        for (UUID id : new HashSet<>(carModels.keySet())) {
            removeCarModel(id);
        }
        carModels.clear();

        trackedCars.clear();
        stepCooldowns.clear();
    }

    // ==================== 3D-МОДЕЛЬ АВТОМОБИЛЯ (BLOCK DISPLAY) ====================
    // Кузов сделан выше (крыша на высоте 1.85 блока), чтобы голова игрока полностью помещалась в салоне,
    // а широкое шасси (1.50 блока) полностью скрывает хитбокс вагонетки.

    private BlockDisplay createModelPart(World world, Location origin, UUID carId, Material mat,
                                         float tx, float ty, float tz,
                                         float sx, float sy, float sz,
                                         String partType) {
        BlockDisplay display = (BlockDisplay) world.spawnEntity(origin, EntityType.BLOCK_DISPLAY);
        display.setBlock(mat.createBlockData());
        display.setTransformation(new Transformation(
                new Vector3f(tx, ty, tz),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(sx, sy, sz),
                new AxisAngle4f(0, 0, 1, 0)
        ));
        display.setTeleportDuration(1);
        display.setInterpolationDuration(1);
        display.setBrightness(new Display.Brightness(15, 15));
        display.getPersistentDataContainer().set(carPartKey, PersistentDataType.STRING, carId.toString());
        if (partType != null) {
            display.getPersistentDataContainer().set(partTypeKey, PersistentDataType.STRING, partType);
        }
        return display;
    }

    private void spawnCarModel(Minecart cart) {
        removeCarModel(cart.getUniqueId());
        World world = cart.getWorld();
        Location loc = cart.getLocation();
        UUID id = cart.getUniqueId();
        List<BlockDisplay> parts = new ArrayList<>();

        // 1. Шасси / днище (шириной 1.50 блока — полностью прячет вагонетку снизу и сбоку)
        parts.add(createModelPart(world, loc, id, Material.GRAY_CONCRETE,
                -0.75f, 0.12f, -1.25f, 1.50f, 0.24f, 2.50f, "chassis"));

        // 2. Нижний основной кузов (высокий клиренс и борта до y = 0.86)
        parts.add(createModelPart(world, loc, id, Material.RED_CONCRETE,
                -0.78f, 0.36f, -1.30f, 1.56f, 0.50f, 2.60f, "body"));

        // 3. Капот (спереди, +Z, чуть приподнятый)
        parts.add(createModelPart(world, loc, id, Material.RED_CONCRETE,
                -0.74f, 0.86f, 0.42f, 1.48f, 0.14f, 0.84f, "hood"));

        // 4. Багажник (сзади, -Z)
        parts.add(createModelPart(world, loc, id, Material.RED_CONCRETE,
                -0.74f, 0.86f, -1.26f, 1.48f, 0.16f, 0.58f, "trunk"));

        // 5. Высокая крыша салона (крыша на высоте 1.78 - 1.92 блока, чтобы голова игрока не торчала!)
        parts.add(createModelPart(world, loc, id, Material.RED_CONCRETE,
                -0.70f, 1.78f, -0.78f, 1.40f, 0.14f, 1.24f, "roof"));

        // 6. Лобовое стекло (высокое тонированное стекло спереди кабины, y = 0.86..1.78)
        parts.add(createModelPart(world, loc, id, Material.TINTED_GLASS,
                -0.66f, 0.86f, 0.40f, 1.32f, 0.92f, 0.08f, "windshield"));

        // 7. Заднее стекло
        parts.add(createModelPart(world, loc, id, Material.TINTED_GLASS,
                -0.66f, 0.86f, -0.76f, 1.32f, 0.92f, 0.08f, "rear_glass"));

        // 8. Боковые стекла (левое и правое, полупрозрачные для обзора)
        parts.add(createModelPart(world, loc, id, Material.TINTED_GLASS,
                -0.70f, 0.86f, -0.68f, 0.06f, 0.92f, 1.08f, "side_glass_l"));
        parts.add(createModelPart(world, loc, id, Material.TINTED_GLASS,
                0.64f, 0.86f, -0.68f, 0.06f, 0.92f, 1.08f, "side_glass_r"));

        // 9. Стойки крыши (4 черные высокие стойки по углам салона, y = 0.86..1.78)
        parts.add(createModelPart(world, loc, id, Material.BLACK_CONCRETE,
                -0.71f, 0.86f, 0.36f, 0.11f, 0.92f, 0.11f, "pillar_fl"));
        parts.add(createModelPart(world, loc, id, Material.BLACK_CONCRETE,
                0.60f, 0.86f, 0.36f, 0.11f, 0.92f, 0.11f, "pillar_fr"));
        parts.add(createModelPart(world, loc, id, Material.BLACK_CONCRETE,
                -0.71f, 0.86f, -0.78f, 0.11f, 0.92f, 0.11f, "pillar_rl"));
        parts.add(createModelPart(world, loc, id, Material.BLACK_CONCRETE,
                0.60f, 0.86f, -0.78f, 0.11f, 0.92f, 0.11f, "pillar_rr"));

        // 10. Передний и задний бамперы
        parts.add(createModelPart(world, loc, id, Material.GRAY_CONCRETE,
                -0.80f, 0.24f, 1.24f, 1.60f, 0.24f, 0.14f, "bumper_f"));
        parts.add(createModelPart(world, loc, id, Material.GRAY_CONCRETE,
                -0.80f, 0.24f, -1.38f, 1.60f, 0.24f, 0.14f, "bumper_r"));

        // 11. Решетка радиатора
        parts.add(createModelPart(world, loc, id, Material.POLISHED_ANDESITE,
                -0.42f, 0.48f, 1.28f, 0.84f, 0.28f, 0.06f, "grille"));

        // 12. Фары (меняют материал при включении клавишей F)
        Material hlMat = areHeadlightsOn(cart) ? Material.SEA_LANTERN : Material.WHITE_STAINED_GLASS;
        parts.add(createModelPart(world, loc, id, hlMat,
                -0.68f, 0.56f, 1.28f, 0.24f, 0.18f, 0.06f, "headlight_l"));
        parts.add(createModelPart(world, loc, id, hlMat,
                0.44f, 0.56f, 1.28f, 0.24f, 0.18f, 0.06f, "headlight_r"));

        // 13. Задние стоп-сигналы
        parts.add(createModelPart(world, loc, id, Material.REDSTONE_BLOCK,
                -0.68f, 0.58f, -1.34f, 0.26f, 0.16f, 0.05f, "taillight_l"));
        parts.add(createModelPart(world, loc, id, Material.REDSTONE_BLOCK,
                0.42f, 0.58f, -1.34f, 0.26f, 0.16f, 0.05f, "taillight_r"));

        // 14. Четыре крупных колеса (шины из черного бетона, диаметр 0.52 блока)
        parts.add(createModelPart(world, loc, id, Material.BLACK_CONCRETE,
                -0.88f, 0.00f, 0.58f, 0.18f, 0.52f, 0.52f, "wheel_fl"));
        parts.add(createModelPart(world, loc, id, Material.BLACK_CONCRETE,
                0.70f, 0.00f, 0.58f, 0.18f, 0.52f, 0.52f, "wheel_fr"));
        parts.add(createModelPart(world, loc, id, Material.BLACK_CONCRETE,
                -0.88f, 0.00f, -1.04f, 0.18f, 0.52f, 0.52f, "wheel_rl"));
        parts.add(createModelPart(world, loc, id, Material.BLACK_CONCRETE,
                0.70f, 0.00f, -1.04f, 0.18f, 0.52f, 0.52f, "wheel_rr"));

        // 15. Диски колес (железные ступицы)
        parts.add(createModelPart(world, loc, id, Material.IRON_BLOCK,
                -0.90f, 0.14f, 0.72f, 0.04f, 0.24f, 0.24f, "hub_fl"));
        parts.add(createModelPart(world, loc, id, Material.IRON_BLOCK,
                0.86f, 0.14f, 0.72f, 0.04f, 0.24f, 0.24f, "hub_fr"));
        parts.add(createModelPart(world, loc, id, Material.IRON_BLOCK,
                -0.90f, 0.14f, -0.90f, 0.04f, 0.24f, 0.24f, "hub_rl"));
        parts.add(createModelPart(world, loc, id, Material.IRON_BLOCK,
                0.86f, 0.14f, -0.90f, 0.04f, 0.24f, 0.24f, "hub_rr"));

        // 16. Выхлопная труба сзади справа
        parts.add(createModelPart(world, loc, id, Material.NETHERITE_BLOCK,
                0.42f, 0.16f, -1.46f, 0.14f, 0.14f, 0.20f, "exhaust"));

        carModels.put(id, parts);
    }

    private void updateCarModel(Minecart cart) {
        List<BlockDisplay> parts = carModels.get(cart.getUniqueId());
        if (parts == null || parts.isEmpty()) {
            spawnCarModel(cart);
            return;
        }

        Location target = cart.getLocation().clone();
        target.setPitch(0f);

        boolean anyInvalid = false;
        for (BlockDisplay bd : parts) {
            if (bd == null || !bd.isValid()) {
                anyInvalid = true;
                break;
            }
            bd.teleport(target);
        }

        if (anyInvalid) {
            spawnCarModel(cart);
        }
    }

    private void updateModelHeadlights(Minecart cart, boolean on) {
        List<BlockDisplay> parts = carModels.get(cart.getUniqueId());
        if (parts == null) return;
        Material mat = on ? Material.SEA_LANTERN : Material.WHITE_STAINED_GLASS;
        for (BlockDisplay bd : parts) {
            if (bd != null && bd.isValid()) {
                String type = bd.getPersistentDataContainer().get(partTypeKey, PersistentDataType.STRING);
                if ("headlight_l".equals(type) || "headlight_r".equals(type)) {
                    bd.setBlock(mat.createBlockData());
                }
            }
        }
    }

    private void removeCarModel(UUID carId) {
        List<BlockDisplay> parts = carModels.remove(carId);
        if (parts != null) {
            for (BlockDisplay bd : parts) {
                if (bd != null && bd.isValid()) {
                    bd.remove();
                }
            }
        }
    }

    // ==================== ПРЕДМЕТ И РЕЦЕПТ ====================

    private ItemStack createCarItem() {
        ItemStack item = new ItemStack(Material.MINECART);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§6§lАвтомобиль"));
            meta.lore(List.of(
                    Component.text("§7Поставьте на землю (ПКМ)"),
                    Component.text("§eShift + ПКМ снаружи — топливный бак (уголь)"),
                    Component.text("§eShift + ЛКМ снаружи — багажник (27 слотов)"),
                    Component.text("§eПробел — завести | F — фары | Ctrl+1/2/3/4/Q — КПП")
            ));
            meta.getPersistentDataContainer().set(carKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void registerCarRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "car_recipe"), createCarItem());
        recipe.shape(" R ", "ILI", "IFI");
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('L', Material.LEATHER);
        recipe.setIngredient('F', Material.FURNACE);
        Bukkit.addRecipe(recipe);
    }

    private boolean isCar(Minecart cart) {
        return cart.getPersistentDataContainer().has(carKey, PersistentDataType.BYTE);
    }

    private boolean isCarItem(ItemStack item) {
        if (item == null || item.getType() != Material.MINECART || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(carKey, PersistentDataType.BYTE);
    }

    private boolean areHeadlightsOn(Minecart cart) {
        return cart.getPersistentDataContainer().getOrDefault(headlightsKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private boolean isEngineOn(Minecart cart) {
        return cart.getPersistentDataContainer().getOrDefault(engineKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private void setEngineOn(Minecart cart, boolean state) {
        cart.getPersistentDataContainer().set(engineKey, PersistentDataType.BYTE, state ? (byte) 1 : (byte) 0);
    }

    private int getGear(Minecart cart) {
        return cart.getPersistentDataContainer().getOrDefault(gearKey, PersistentDataType.INTEGER, GEAR_NEUTRAL);
    }

    private void setGear(Minecart cart, int gear) {
        int clamped = Math.max(GEAR_REVERSE, Math.min(GEAR_THIRD, gear));
        cart.getPersistentDataContainer().set(gearKey, PersistentDataType.INTEGER, clamped);
    }

    private String formatGearName(int gear) {
        return switch (gear) {
            case GEAR_REVERSE -> "§c[R] Задняя";
            case GEAR_FIRST -> "§a[1] Первая";
            case GEAR_SECOND -> "§e[2] Вторая";
            case GEAR_THIRD -> "§6[3] Третья";
            default -> "§7[N] Нейтраль";
        };
    }

    private String formatGearShort(int gear) {
        return switch (gear) {
            case GEAR_REVERSE -> "§c[R]";
            case GEAR_FIRST -> "§a[1]";
            case GEAR_SECOND -> "§e[2]";
            case GEAR_THIRD -> "§6[3]";
            default -> "§7[N]";
        };
    }

    private double getGearSpeed(int gear) {
        return switch (gear) {
            case GEAR_REVERSE -> -0.35;
            case GEAR_FIRST -> 0.42;
            case GEAR_SECOND -> 0.72;
            case GEAR_THIRD -> 1.15;
            default -> 0.0;
        };
    }

    private float getGearPitch(int gear) {
        return switch (gear) {
            case GEAR_REVERSE -> 0.40f;
            case GEAR_FIRST -> 0.50f;
            case GEAR_SECOND -> 0.70f;
            case GEAR_THIRD -> 0.95f;
            default -> 0.35f;
        };
    }

    private int getFuelTicks(Minecart cart) {
        return cart.getPersistentDataContainer().getOrDefault(fuelTicksKey, PersistentDataType.INTEGER, 0);
    }

    private void setFuelTicks(Minecart cart, int ticks) {
        cart.getPersistentDataContainer().set(fuelTicksKey, PersistentDataType.INTEGER, Math.max(0, ticks));
    }

    private boolean isValidFuel(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.COAL || type == Material.CHARCOAL || type == Material.COAL_BLOCK;
    }

    private void saveInventory(Minecart cart, NamespacedKey key, Inventory inv) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(inv.getSize());
            for (int i = 0; i < inv.getSize(); i++) {
                dataOutput.writeObject(inv.getItem(i));
            }
            dataOutput.close();
            String encoded = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            cart.getPersistentDataContainer().set(key, PersistentDataType.STRING, encoded);
        } catch (Exception e) {
            getLogger().warning("Не удалось сохранить инвентарь машины: " + e.getMessage());
        }
    }

    private void populateInventoryFromData(Minecart cart, NamespacedKey key, Inventory inv) {
        String encoded = cart.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (encoded != null && !encoded.isEmpty()) {
            try {
                byte[] data = Base64.getDecoder().decode(encoded);
                BukkitObjectInputStream dataInput = new BukkitObjectInputStream(new ByteArrayInputStream(data));
                int size = dataInput.readInt();
                for (int i = 0; i < size && i < inv.getSize(); i++) {
                    inv.setItem(i, (ItemStack) dataInput.readObject());
                }
                dataInput.close();
            } catch (Exception e) {
                getLogger().warning("Не удалось загрузить инвентарь машины: " + e.getMessage());
            }
        }
    }

    private Inventory loadTank(Minecart cart) {
        FuelTankHolder holder = new FuelTankHolder(cart.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 9, Component.text("§8Топливный бак (Уголь)"));
        holder.setInventory(inv);
        populateInventoryFromData(cart, tankDataKey, inv);
        return inv;
    }

    private Inventory getOrOpenTank(Minecart cart) {
        return openTanks.computeIfAbsent(cart.getUniqueId(), id -> loadTank(cart));
    }

    private Inventory loadTrunk(Minecart cart) {
        TrunkHolder holder = new TrunkHolder(cart.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("§8Багажник автомобиля"));
        holder.setInventory(inv);
        populateInventoryFromData(cart, trunkDataKey, inv);
        return inv;
    }

    private Inventory getOrOpenTrunk(Minecart cart) {
        return openTrunks.computeIfAbsent(cart.getUniqueId(), id -> loadTrunk(cart));
    }

    private boolean consumeFuelIfNeeded(Minecart cart) {
        int currentTicks = getFuelTicks(cart);
        if (currentTicks > 0) {
            return true;
        }

        Inventory inv = getOrOpenTank(cart);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isValidFuel(item)) {
                int addedTicks = (item.getType() == Material.COAL_BLOCK) ? TICKS_PER_COAL_BLOCK : TICKS_PER_COAL;
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    inv.setItem(i, null);
                }
                saveInventory(cart, tankDataKey, inv);
                setFuelTicks(cart, addedTicks);
                cart.getWorld().playSound(cart.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.5f, 0.8f);
                return true;
            }
        }
        return false;
    }

    private int getTotalFuelSeconds(Minecart cart) {
        int totalTicks = getFuelTicks(cart);
        Inventory inv = getOrOpenTank(cart);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isValidFuel(item)) {
                int perItem = (item.getType() == Material.COAL_BLOCK) ? TICKS_PER_COAL_BLOCK : TICKS_PER_COAL;
                totalTicks += perItem * item.getAmount();
            }
        }
        return totalTicks / 20;
    }

    private void clearLights(UUID cartId) {
        List<Block> oldBlocks = activeLightBlocks.remove(cartId);
        if (oldBlocks != null) {
            for (Block b : oldBlocks) {
                if (b != null && b.getType() == Material.LIGHT) {
                    b.setType(Material.AIR, false);
                }
            }
        }
    }

    private boolean isCtrlHeld(Player player) {
        return player.getCurrentInput().isSprint() || player.isSprinting();
    }

    @EventHandler
    public void onPlayerFallInCar(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getVehicle() instanceof Minecart cart && isCar(cart)) {
            if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
                event.setCancelled(true);
            } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                double reducedDamage = event.getDamage() / 4.0;
                if (reducedDamage <= 0.1) {
                    event.setCancelled(true);
                } else {
                    event.setDamage(reducedDamage);
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0));
                cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 0.8f, 0.7f);
            }
        }
    }

    @EventHandler
    public void onCtrlGearSlotChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!(player.getVehicle() instanceof Minecart cart) || !isCar(cart)) return;

        if (!isCtrlHeld(player)) return;

        int slot = event.getNewSlot();
        int newGear = switch (slot) {
            case 0 -> GEAR_FIRST;
            case 1 -> GEAR_SECOND;
            case 2 -> GEAR_THIRD;
            case 3 -> GEAR_NEUTRAL;
            default -> -99;
        };

        if (newGear != -99) {
            event.setCancelled(true);
            if (newGear != getGear(cart)) {
                setGear(cart, newGear);
                cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.8f, 1.3f);
                player.sendActionBar(Component.text("§7Передача: " + formatGearName(newGear)));
            }
        }
    }

    @EventHandler
    public void onCtrlReverseDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!(player.getVehicle() instanceof Minecart cart) || !isCar(cart)) return;

        if (isCtrlHeld(player)) {
            event.setCancelled(true);
            int newGear = (getGear(cart) == GEAR_REVERSE) ? GEAR_NEUTRAL : GEAR_REVERSE;
            setGear(cart, newGear);
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.8f, 0.9f);
            player.sendActionBar(Component.text("§7Передача: " + formatGearName(newGear)));
        }
    }

    @EventHandler
    public void onOpenTank(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Minecart cart) || !isCar(cart)) return;

        Player player = event.getPlayer();
        if (player.isSneaking() && player.getVehicle() == null) {
            event.setCancelled(true);
            Inventory tank = getOrOpenTank(cart);
            player.openInventory(tank);
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
        }
    }

    @EventHandler
    public void onTankClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof FuelTankHolder)) return;

        if (event.getClickedInventory() == event.getInventory()) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR && !isValidFuel(cursor)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage("§cВ топливный бак можно класть только уголь или угольные блоки!");
                }
            }
        } else if (event.isShiftClick()) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() != Material.AIR && !isValidFuel(clickedItem)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage("§cВ топливный бак можно класть только уголь или угольные блоки!");
                }
            }
        }
    }

    @EventHandler
    public void onTankDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof FuelTankHolder)) return;
        ItemStack dragged = event.getOldCursor();
        if (dragged != null && dragged.getType() != Material.AIR && !isValidFuel(dragged)) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof FuelTankHolder tankHolder) {
            Entity entity = Bukkit.getEntity(tankHolder.getCarId());
            if (entity instanceof Minecart cart) {
                saveInventory(cart, tankDataKey, event.getInventory());
                cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.1f);
            }
            if (event.getInventory().getViewers().size() <= 1) {
                openTanks.remove(tankHolder.getCarId());
            }
        } else if (holder instanceof TrunkHolder trunkHolder) {
            Entity entity = Bukkit.getEntity(trunkHolder.getCarId());
            if (entity instanceof Minecart cart) {
                saveInventory(cart, trunkDataKey, event.getInventory());
                cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.8f, 1.0f);
            }
            if (event.getInventory().getViewers().size() <= 1) {
                openTrunks.remove(trunkHolder.getCarId());
            }
        }
    }

    @EventHandler
    public void onToggleHeadlights(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.getVehicle() instanceof Minecart cart && isCar(cart)) {
            event.setCancelled(true);
            boolean newState = !areHeadlightsOn(cart);
            cart.getPersistentDataContainer().set(headlightsKey, PersistentDataType.BYTE, newState ? (byte) 1 : (byte) 0);
            updateModelHeadlights(cart, newState);

            if (!newState) {
                clearLights(cart.getUniqueId());
                player.sendActionBar(Component.text("§7Фары: §cВЫКЛ"));
            } else {
                player.sendActionBar(Component.text("§7Фары: §eВКЛ"));
            }
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.9f, newState ? 1.4f : 1.0f);
        }
    }

    @EventHandler
    public void onPlaceCar(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!isCarItem(item)) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        event.setCancelled(true);

        Location spawnLoc = clicked.getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
        Minecart cart = (Minecart) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.MINECART);
        cart.getPersistentDataContainer().set(carKey, PersistentDataType.BYTE, (byte) 1);
        cart.setInvisible(true); // Делаем саму вагонетку полностью невидимой
        cart.setCustomNameVisible(false);
        cart.setSilent(true);
        cart.setDerailedVelocityMod(new Vector(0.95, 0.95, 0.95));
        cart.setMaxSpeed(2.5);
        trackedCars.add(cart.getUniqueId());
        spawnCarModel(cart);

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onBreakOrOpenTrunk(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart) || !isCar(cart)) return;

        event.setCancelled(true);

        if (event.getAttacker() instanceof Player player && player.isSneaking() && player.getVehicle() == null) {
            Inventory trunk = getOrOpenTrunk(cart);
            player.openInventory(trunk);
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.8f, 1.0f);
            return;
        }

        clearLights(cart.getUniqueId());
        removeCarModel(cart.getUniqueId());

        Inventory tank = getOrOpenTank(cart);
        for (ItemStack item : tank.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                cart.getWorld().dropItemNaturally(cart.getLocation(), item);
            }
        }
        openTanks.remove(cart.getUniqueId());

        Inventory trunk = getOrOpenTrunk(cart);
        for (ItemStack item : trunk.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                cart.getWorld().dropItemNaturally(cart.getLocation(), item);
            }
        }
        openTrunks.remove(cart.getUniqueId());

        stepCooldowns.remove(cart.getUniqueId());
        trackedCars.remove(cart.getUniqueId());

        cart.remove();
        cart.getWorld().dropItemNaturally(cart.getLocation(), createCarItem());
    }

    @EventHandler
    public void onEnter(VehicleEnterEvent event) {
        if (event.getVehicle() instanceof Minecart cart && isCar(cart) && event.getEntered() instanceof Player player) {
            trackedCars.add(cart.getUniqueId());
            cart.setInvisible(true);
            cart.setCustomNameVisible(false);
            cart.setSilent(true);
            cart.setDerailedVelocityMod(new Vector(0.95, 0.95, 0.95));
            cart.setMaxSpeed(2.5);
            if (!carModels.containsKey(cart.getUniqueId())) {
                spawnCarModel(cart);
            }
            int fuelSec = getTotalFuelSeconds(cart);
            String gearStr = formatGearShort(getGear(cart));
            if (isEngineOn(cart)) {
                player.sendActionBar(Component.text("§aМотор ВКЛ §7| КПП: " + gearStr + " §7| §6Топливо: §e" + fuelSec + "с §7| Shift+ПКМ: бак, Shift+ЛКМ: багажник"));
            } else {
                player.sendActionBar(Component.text("§cМотор ВЫКЛ §7| КПП: " + gearStr + " §7| §eПробел — завести | Shift+ЛКМ снаружи — багажник"));
            }
        }
    }

    @EventHandler
    public void onExit(VehicleExitEvent event) {
        if (event.getVehicle() instanceof Minecart cart && isCar(cart)) {
            cart.setVelocity(new Vector(0, 0, 0));
        }
    }

    private boolean isPassable(Block block) {
        if (block.isPassable()) return true;
        Material type = block.getType();
        return type == Material.DIRT_PATH || type == Material.FARMLAND || type == Material.SOUL_SAND || type == Material.MUD;
    }

    private boolean hasGroundTraction(Minecart cart) {
        if (cart.isOnGround()) return true;
        Block below = cart.getLocation().subtract(0, 0.35, 0).getBlock();
        return !below.isPassable();
    }

    private double getBlockTopY(Block block) {
        if (block.isPassable()) return block.getY();
        var box = block.getBoundingBox();
        if (box.getHeight() > 0) {
            return box.getMaxY();
        }
        return block.getY() + 1.0;
    }

    private boolean tryStepUp(Minecart cart, Vector moveDir, double speed) {
        int cd = stepCooldowns.getOrDefault(cart.getUniqueId(), 0);
        if (cd > 0) {
            stepCooldowns.put(cart.getUniqueId(), cd - 1);
            return false;
        }

        double currentY = cart.getLocation().getY();
        Block groundBlock = cart.getLocation().subtract(0, 0.1, 0).getBlock();
        if (!groundBlock.isPassable()) {
            double groundTop = getBlockTopY(groundBlock);
            if (Math.abs(currentY - groundTop) < 0.25) {
                currentY = Math.min(currentY, groundTop);
            }
        }

        Vector side = new Vector(-moveDir.getZ(), 0, moveDir.getX()).multiply(0.45);
        double[] distances = new double[]{0.55, 0.85, 1.15, 1.40};

        double targetStepY = -1.0;

        for (double dist : distances) {
            Location center = cart.getLocation().add(moveDir.clone().multiply(dist));
            Location[] points = new Location[]{
                    center,
                    center.clone().add(side),
                    center.clone().subtract(side)
            };

            for (Location pt : points) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block b = pt.clone().add(0, dy, 0).getBlock();
                    if (b.isPassable()) continue;
                    if (Tag.FENCES.isTagged(b.getType()) || Tag.WALLS.isTagged(b.getType())) continue;

                    double topY = getBlockTopY(b);
                    double diff = topY - currentY;

                    if (diff > 0.04 && diff <= 1.15) {
                        Block above1 = b.getRelative(0, 1, 0);
                        Block above2 = b.getRelative(0, 2, 0);
                        if (isPassable(above1) && isPassable(above2)) {
                            if (topY > targetStepY) {
                                targetStepY = topY;
                            }
                        }
                    }
                }
            }
        }

        if (targetStepY > currentY + 0.04) {
            double heightDiff = targetStepY - currentY;
            double boostY = (heightDiff <= 0.25) ? 0.30 : Math.min(0.64, 0.38 + heightDiff * 0.28);
            Vector stepVel = moveDir.clone().multiply(Math.max(Math.abs(speed), 0.58)).setY(boostY);
            cart.setVelocity(stepVel);
            stepCooldowns.put(cart.getUniqueId(), 3);
            return true;
        }
        return false;
    }

    private Vector getCarForwardVector(Minecart cart) {
        double yawRad = Math.toRadians(cart.getLocation().getYaw());
        return new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
    }

    private void updateHeadlights(Minecart cart, Vector forward) {
        if (!areHeadlightsOn(cart)) return;

        if (tickCounter % 3 == 0) {
            Vector right = new Vector(-forward.getZ(), 0, forward.getX()).multiply(0.56);
            Location frontCenter = cart.getLocation().add(forward.clone().multiply(1.32)).add(0, 0.65, 0);

            Location leftLight = frontCenter.clone().subtract(right);
            Location rightLight = frontCenter.clone().add(right);

            Particle.DustOptions warmLight = new Particle.DustOptions(Color.fromRGB(255, 240, 170), 0.75f);
            cart.getWorld().spawnParticle(Particle.DUST, leftLight, 1, 0.01, 0.01, 0.01, 0.0, warmLight);
            cart.getWorld().spawnParticle(Particle.DUST, rightLight, 1, 0.01, 0.01, 0.01, 0.0, warmLight);

            Vector beamOffset = forward.clone().multiply(2.8);
            cart.getWorld().spawnParticle(Particle.DUST, leftLight.clone().add(beamOffset), 1, 0.05, 0.02, 0.05, 0.0, warmLight);
            cart.getWorld().spawnParticle(Particle.DUST, rightLight.clone().add(beamOffset), 1, 0.05, 0.02, 0.05, 0.0, warmLight);
        }

        if (tickCounter % 2 == 0) {
            Block nearBlock = findAirNear(cart.getLocation().add(forward.clone().multiply(4.8)).add(0, 1.0, 0));
            Block farBlock = findAirNear(cart.getLocation().add(forward.clone().multiply(8.8)).add(0, 1.2, 0));

            List<Block> oldBlocks = activeLightBlocks.get(cart.getUniqueId());
            if (oldBlocks != null) {
                for (Block old : oldBlocks) {
                    if (old != null && !old.equals(nearBlock) && !old.equals(farBlock) && old.getType() == Material.LIGHT) {
                        old.setType(Material.AIR, false);
                    }
                }
            }

            placeLightBlock(nearBlock, 15);
            placeLightBlock(farBlock, 14);
            activeLightBlocks.put(cart.getUniqueId(), List.of(nearBlock, farBlock));
        }
    }

    private Block findAirNear(Location loc) {
        Block b = loc.getBlock();
        if (b.getType() == Material.AIR || b.getType() == Material.LIGHT) return b;
        Block up = b.getRelative(0, 1, 0);
        if (up.getType() == Material.AIR || up.getType() == Material.LIGHT) return up;
        return b;
    }

    private void placeLightBlock(Block block, int level) {
        if (block != null && (block.getType() == Material.AIR || block.getType() == Material.LIGHT)) {
            if (block.getType() != Material.LIGHT) {
                block.setType(Material.LIGHT, false);
            }
            if (block.getBlockData() instanceof Levelled levelled && levelled.getLevel() != level) {
                levelled.setLevel(level);
                block.setBlockData(levelled, false);
            }
        }
    }

    private void tickCar(Minecart cart) {
        if (!cart.isInvisible()) {
            cart.setInvisible(true);
        }

        Player driver = null;
        for (Entity passenger : cart.getPassengers()) {
            if (passenger instanceof Player p) {
                driver = p;
                break;
            }
        }

        Vector forward;
        if (driver != null) {
            float yaw = driver.getEyeLocation().getYaw();
            cart.setRotation(yaw, 0f);
            forward = driver.getEyeLocation().getDirection().setY(0).normalize();
        } else {
            forward = getCarForwardVector(cart);
        }

        updateCarModel(cart);
        updateHeadlights(cart, forward);

        if (driver != null) {
            var input = driver.getCurrentInput();
            if (input.isJump() && tickCounter % 10 == 0) {
                boolean newEngineState = !isEngineOn(cart);
                if (newEngineState) {
                    if (consumeFuelIfNeeded(cart)) {
                        setEngineOn(cart, true);
                        cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.9f, 0.7f);
                        cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.6f, 0.6f);
                        driver.sendActionBar(Component.text("§aМотор заведен! §7КПП: " + formatGearShort(getGear(cart)) + " §7| §6Топливо: §e" + getTotalFuelSeconds(cart) + "с"));
                    } else {
                        cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.9f, 1.2f);
                        driver.sendActionBar(Component.text("§cНет топлива! §7Встаньте и нажмите §eShift + ПКМ §7по машине, чтобы засыпать уголь"));
                    }
                } else {
                    setEngineOn(cart, false);
                    cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.9f, 0.6f);
                    driver.sendActionBar(Component.text("§cДвигатель заглушен."));
                }
            }
        }

        Vector velocity = cart.getVelocity();
        boolean onGround = hasGroundTraction(cart);

        if (!isEngineOn(cart)) {
            if (onGround) {
                cart.setVelocity(new Vector(velocity.getX() * 0.8, velocity.getY(), velocity.getZ() * 0.8));
            } else {
                cart.setVelocity(new Vector(velocity.getX() * 0.98, velocity.getY(), velocity.getZ() * 0.98));
            }
            return;
        }

        if (!consumeFuelIfNeeded(cart)) {
            setEngineOn(cart, false);
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.8f);
            if (driver != null) {
                driver.sendActionBar(Component.text("§cТопливо закончилось! Двигатель заглох (Shift+ПКМ — заправить уголь)"));
            }
            return;
        }

        if (driver == null) {
            if (tickCounter % 2 == 0) {
                setFuelTicks(cart, getFuelTicks(cart) - 1);
            }
            if (onGround) {
                cart.setVelocity(new Vector(velocity.getX() * 0.8, velocity.getY(), velocity.getZ() * 0.8));
            }
            if (tickCounter % 8 == 0) {
                Location exhaust = cart.getLocation().subtract(forward.clone().multiply(1.45)).add(0, 0.25, 0);
                cart.getWorld().spawnParticle(Particle.SMOKE, exhaust, 1, 0.05, 0.05, 0.05, 0.01);
                cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.15f, 0.35f);
            }
            return;
        }

        var input = driver.getCurrentInput();
        int currentGear = getGear(cart);
        double speed = 0.0;

        if (input.isForward() && !input.isBackward()) {
            speed = getGearSpeed(currentGear);
        }

        int burnRate = input.isForward() ? 2 : 1;
        setFuelTicks(cart, getFuelTicks(cart) - burnRate);

        if (tickCounter % 20 == 0) {
            int secLeft = getTotalFuelSeconds(cart);
            String color = secLeft > 30 ? "§a" : (secLeft > 10 ? "§e" : "§c");
            driver.sendActionBar(Component.text("§7КПП: " + formatGearShort(currentGear) + " §7| Топливо: " + color + secLeft + "с §7| Ctrl+1/2/3/4/Q"));
        }

        if (speed != 0.0) {
            Location exhaust = cart.getLocation().subtract(forward.clone().multiply(1.45)).add(0, 0.25, 0);
            cart.getWorld().spawnParticle(Particle.LARGE_SMOKE, exhaust, 1, 0.08, 0.05, 0.08, 0.01);
            if (tickCounter % 3 == 0) {
                cart.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, exhaust, 1, 0.05, 0.05, 0.05, 0.01);
            }

            if (onGround) {
                Vector moveDir = speed > 0 ? forward.clone() : forward.clone().multiply(-1);

                if (!tryStepUp(cart, moveDir, speed)) {
                    Vector move = forward.clone().multiply(speed);
                    cart.setVelocity(new Vector(move.getX(), velocity.getY(), move.getZ()));
                }

                if (tickCounter % 3 == 0) {
                    cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.35f, getGearPitch(currentGear));
                }
            } else {
                cart.setVelocity(new Vector(velocity.getX() * 0.98, velocity.getY(), velocity.getZ() * 0.98));

                if (tickCounter % 3 == 0) {
                    cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.4f, 0.95f);
                }
            }
        } else {
            double drag = input.isBackward() ? 0.55 : 0.85;
            if (onGround) {
                cart.setVelocity(new Vector(velocity.getX() * drag, velocity.getY(), velocity.getZ() * drag));
            } else {
                cart.setVelocity(new Vector(velocity.getX() * 0.98, velocity.getY(), velocity.getZ() * 0.98));
            }

            if (input.isForward() && currentGear == GEAR_NEUTRAL) {
                Location exhaust = cart.getLocation().subtract(forward.clone().multiply(1.45)).add(0, 0.25, 0);
                cart.getWorld().spawnParticle(Particle.LARGE_SMOKE, exhaust, 1, 0.06, 0.04, 0.06, 0.01);
                if (tickCounter % 3 == 0) {
                    cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.35f, 0.80f);
                }
            } else if (tickCounter % 8 == 0) {
                Location exhaust = cart.getLocation().subtract(forward.clone().multiply(1.45)).add(0, 0.25, 0);
                cart.getWorld().spawnParticle(Particle.SMOKE, exhaust, 1, 0.05, 0.05, 0.05, 0.01);
                cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.15f, 0.35f);
            }
        }
    }
}
