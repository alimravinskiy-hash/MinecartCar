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
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
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

    // ==================== ТИПЫ ТРАНСПОРТА И ИХ ХАРАКТЕРИСТИКИ ====================
    // Все цифры для настройки собраны здесь. Старые машины (значение 1 в PDC) остаются CAR.

    private enum CarType {
        CAR(
                (byte) 1,                  // id в PersistentDataContainer
                "car_recipe",              // ключ рецепта
                "§6§lАвтомобиль",          // название предмета
                9, 27,                     // слотов: топливный бак, багажник
                "§8Топливный бак (Уголь)", // заголовок бака
                "§8Багажник автомобиля",   // заголовок багажника
                1.65f, 1.75f,              // хитбокс взаимодействия: ширина, высота
                0.0,                       // второй (задний) хитбокс: смещение назад, 0 = не нужен
                -0.35, 0.42, 0.72, 1.15,   // скорость: R, 1-я, 2-я, 3-я
                1.0f,                      // множитель тона звука двигателя
                2,                         // расход топлива (тиков за тик) при нажатом W
                0.25,                      // разгон (0..1, чем больше — тем резче)
                0.92, 0.65,                // накат без газа, торможение на S (множитель скорости за тик)
                0.35f,                     // отзывчивость руля (0..1, чем меньше — тем неповоротливее)
                1.15, 0.58, 0.64,          // подъём на блоки: макс. высота, мин. скорость (вертикальный импульс больше не используется)
                new double[]{0.55, 0.85, 1.15, 1.40}, // дистанции проверки препятствий впереди
                4.0,                       // делитель урона от падения для водителя
                1.32, 0.56, 0.65,          // фары (частицы): вынос вперёд, разнос по бокам, высота
                1.45                       // вынос выхлопа назад
        ),
        GAZELLE(
                (byte) 2,
                "gazelle_recipe",
                "§b§lГАЗель",
                18, 54,
                "§8Топливный бак ГАЗели (Уголь)",
                "§8Грузовой отсек ГАЗели",
                2.10f, 2.70f,
                2.0,
                -0.28, 0.34, 0.58, 0.90,
                0.85f,
                3,
                0.14,
                0.95, 0.78,
                0.20f,
                1.05, 0.50, 0.60,
                new double[]{0.60, 0.95, 1.30, 1.60},
                3.0,
                1.62, 0.74, 0.73,
                3.50
        );

        final byte id;
        final String recipeKey;
        final String itemName;
        final int tankSize;
        final int trunkSize;
        final String tankTitle;
        final String trunkTitle;
        final float hitboxWidth;
        final float hitboxHeight;
        final double rearHitboxOffset;
        final double speedReverse;
        final double speed1;
        final double speed2;
        final double speed3;
        final float pitchMul;
        final int burnForward;
        final double acceleration;
        final double coastDrag;
        final double brakeDrag;
        final float steering;
        final double maxStepHeight;
        final double stepMinSpeed;
        final double stepBoostCap;
        final double[] stepDistances;
        final double fallDamageDivisor;
        final double headlightFront;
        final double headlightSide;
        final double headlightHeight;
        final double exhaustBack;

        CarType(byte id, String recipeKey, String itemName,
                int tankSize, int trunkSize, String tankTitle, String trunkTitle,
                float hitboxWidth, float hitboxHeight, double rearHitboxOffset,
                double speedReverse, double speed1, double speed2, double speed3,
                float pitchMul, int burnForward,
                double acceleration, double coastDrag, double brakeDrag, float steering,
                double maxStepHeight, double stepMinSpeed, double stepBoostCap, double[] stepDistances,
                double fallDamageDivisor,
                double headlightFront, double headlightSide, double headlightHeight,
                double exhaustBack) {
            this.id = id;
            this.recipeKey = recipeKey;
            this.itemName = itemName;
            this.tankSize = tankSize;
            this.trunkSize = trunkSize;
            this.tankTitle = tankTitle;
            this.trunkTitle = trunkTitle;
            this.hitboxWidth = hitboxWidth;
            this.hitboxHeight = hitboxHeight;
            this.rearHitboxOffset = rearHitboxOffset;
            this.speedReverse = speedReverse;
            this.speed1 = speed1;
            this.speed2 = speed2;
            this.speed3 = speed3;
            this.pitchMul = pitchMul;
            this.burnForward = burnForward;
            this.acceleration = acceleration;
            this.coastDrag = coastDrag;
            this.brakeDrag = brakeDrag;
            this.steering = steering;
            this.maxStepHeight = maxStepHeight;
            this.stepMinSpeed = stepMinSpeed;
            this.stepBoostCap = stepBoostCap;
            this.stepDistances = stepDistances;
            this.fallDamageDivisor = fallDamageDivisor;
            this.headlightFront = headlightFront;
            this.headlightSide = headlightSide;
            this.headlightHeight = headlightHeight;
            this.exhaustBack = exhaustBack;
        }

        static CarType fromId(byte id) {
            for (CarType t : values()) {
                if (t.id == id) return t;
            }
            return CAR;
        }
    }

    private NamespacedKey carKey;
    private NamespacedKey carPartKey;
    private NamespacedKey partTypeKey;
    private NamespacedKey hitboxKey;
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
    private final Map<UUID, Interaction> carHitboxes = new HashMap<>();
    private final Map<UUID, Interaction> carRearHitboxes = new HashMap<>();
    private final Map<UUID, Long> interactionDebounce = new HashMap<>();
    private final Map<UUID, Float> smoothedYaw = new HashMap<>();

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
        hitboxKey = new NamespacedKey(this, "car_hitbox");
        headlightsKey = new NamespacedKey(this, "headlights_on");
        engineKey = new NamespacedKey(this, "engine_on");
        fuelTicksKey = new NamespacedKey(this, "fuel_ticks");
        tankDataKey = new NamespacedKey(this, "fuel_tank_items");
        trunkDataKey = new NamespacedKey(this, "car_trunk_items");
        gearKey = new NamespacedKey(this, "current_gear");

        Bukkit.getPluginManager().registerEvents(this, this);
        registerCarRecipes();

        for (World world : Bukkit.getWorlds()) {
            for (BlockDisplay bd : world.getEntitiesByClass(BlockDisplay.class)) {
                if (bd.getPersistentDataContainer().has(carPartKey, PersistentDataType.STRING)) {
                    bd.remove();
                }
            }
            for (Interaction inter : world.getEntitiesByClass(Interaction.class)) {
                if (inter.getPersistentDataContainer().has(hitboxKey, PersistentDataType.STRING)) {
                    inter.remove();
                }
            }
            for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
                if (isCar(cart)) {
                    cart.setCustomNameVisible(false);
                    trackedCars.add(cart.getUniqueId());
                    spawnCarModel(cart);
                    spawnCarHitbox(cart);
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
                    removeCarHitbox(id);
                    stepCooldowns.remove(id);
                    smoothedYaw.remove(id);
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

        for (UUID id : new HashSet<>(carHitboxes.keySet())) {
            removeCarHitbox(id);
        }
        carHitboxes.clear();
        carRearHitboxes.clear();

        trackedCars.clear();
        stepCooldowns.clear();
        smoothedYaw.clear();
    }

    // ==================== ХИТБОКС ВЗАИМОДЕЙСТВИЯ ====================

    private void spawnCarHitbox(Minecart cart) {
        removeCarHitbox(cart.getUniqueId());
        CarType type = getCarType(cart);
        UUID carId = cart.getUniqueId();

        carHitboxes.put(carId, createHitbox(cart, type, cart.getLocation()));

        // Длинным машинам нужен второй хитбокс сзади, иначе до кузова не достать
        if (type.rearHitboxOffset > 0) {
            float yaw = smoothedYaw.getOrDefault(carId, cart.getLocation().getYaw());
            carRearHitboxes.put(carId, createHitbox(cart, type, getRearHitboxLocation(cart, type, yaw)));
        }
    }

    private Interaction createHitbox(Minecart cart, CarType type, Location at) {
        Interaction hitbox = (Interaction) cart.getWorld().spawnEntity(at, EntityType.INTERACTION);
        hitbox.setInteractionWidth(type.hitboxWidth);
        hitbox.setInteractionHeight(type.hitboxHeight);
        hitbox.setResponsive(true);
        hitbox.getPersistentDataContainer().set(hitboxKey, PersistentDataType.STRING, cart.getUniqueId().toString());
        return hitbox;
    }

    private Location getRearHitboxLocation(Minecart cart, CarType type, float yaw) {
        return cart.getLocation().subtract(getCarForwardVector(yaw).multiply(type.rearHitboxOffset));
    }

    private void updateCarHitbox(Minecart cart, CarType type, float yaw) {
        UUID carId = cart.getUniqueId();
        Interaction hitbox = carHitboxes.get(carId);
        Interaction rear = carRearHitboxes.get(carId);
        boolean needRear = type.rearHitboxOffset > 0;
        if (hitbox == null || !hitbox.isValid() || (needRear && (rear == null || !rear.isValid()))) {
            spawnCarHitbox(cart);
            return;
        }
        // Пока водитель едет, хитбоксы подтягиваем раз в 5 тиков, чтобы не слать лишние пакеты;
        // на стоянке двигаем только при реальном смещении
        boolean driven = getDriver(cart) != null;
        if (driven && tickCounter % 5 != 0) return;

        syncHitbox(hitbox, cart.getLocation(), driven);
        if (needRear) {
            syncHitbox(rear, getRearHitboxLocation(cart, type, yaw), driven);
        }
    }

    private void syncHitbox(Interaction hitbox, Location target, boolean force) {
        if (force || hitbox.getLocation().distanceSquared(target) > 0.01) {
            hitbox.teleport(target);
        }
    }

    private void removeCarHitbox(UUID carId) {
        Interaction hitbox = carHitboxes.remove(carId);
        if (hitbox != null && hitbox.isValid()) {
            hitbox.remove();
        }
        Interaction rear = carRearHitboxes.remove(carId);
        if (rear != null && rear.isValid()) {
            rear.remove();
        }
    }

    private Minecart resolveCarFromEntity(Entity entity) {
        if (entity instanceof Minecart cart && isCar(cart)) {
            return cart;
        }
        String carIdStr = null;
        if (entity instanceof Interaction inter) {
            carIdStr = inter.getPersistentDataContainer().get(hitboxKey, PersistentDataType.STRING);
        } else if (entity instanceof BlockDisplay bd) {
            carIdStr = bd.getPersistentDataContainer().get(carPartKey, PersistentDataType.STRING);
        }
        if (carIdStr != null) {
            try {
                Entity found = Bukkit.getEntity(UUID.fromString(carIdStr));
                if (found instanceof Minecart cart && isCar(cart)) {
                    return cart;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private boolean isDebounced(Player player) {
        long now = System.currentTimeMillis();
        Long last = interactionDebounce.get(player.getUniqueId());
        if (last != null && now - last < 150) {
            return true;
        }
        interactionDebounce.put(player.getUniqueId(), now);
        return false;
    }

    // ==================== 3D-МОДЕЛЬ ТРАНСПОРТА ====================

    private BlockDisplay createModelPart(Minecart cart, Material mat,
                                         float tx, float ty, float tz,
                                         float sx, float sy, float sz,
                                         String partType) {
        World world = cart.getWorld();
        Location origin = cart.getLocation();
        UUID carId = cart.getUniqueId();

        BlockDisplay display = (BlockDisplay) world.spawnEntity(origin, EntityType.BLOCK_DISPLAY);
        display.setBlock(mat.createBlockData());
        display.setTransformation(new Transformation(
                new Vector3f(tx, ty, tz),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(sx, sy, sz),
                new AxisAngle4f(0, 0, 1, 0)
        ));
        display.setTeleportDuration(0);
        // Плавная 1-тиковая интерполяция поворота устраняет рывки кузова при вращении камеры
        display.setInterpolationDuration(1);
        display.setBrightness(new Display.Brightness(15, 15));
        display.getPersistentDataContainer().set(carPartKey, PersistentDataType.STRING, carId.toString());
        if (partType != null) {
            display.getPersistentDataContainer().set(partTypeKey, PersistentDataType.STRING, partType);
        }
        cart.addPassenger(display);
        return display;
    }

    private void spawnCarModel(Minecart cart) {
        removeCarModel(cart.getUniqueId());
        List<BlockDisplay> parts = new ArrayList<>();

        switch (getCarType(cart)) {
            case GAZELLE -> buildGazelleModel(cart, parts);
            default -> buildCarModel(cart, parts);
        }

        carModels.put(cart.getUniqueId(), parts);
    }

    // ---------- Легковой автомобиль (без изменений) ----------

    private void buildCarModel(Minecart cart, List<BlockDisplay> parts) {
        parts.add(createModelPart(cart, Material.GRAY_CONCRETE,
                -0.78f, -0.34f, -1.25f, 1.56f, 0.28f, 2.50f, "chassis"));
        parts.add(createModelPart(cart, Material.RED_CONCRETE,
                -0.80f, -0.06f, -1.30f, 1.60f, 0.58f, 2.60f, "body"));
        parts.add(createModelPart(cart, Material.RED_CONCRETE,
                -0.76f, 0.52f, 0.52f, 1.52f, 0.14f, 0.76f, "hood"));
        parts.add(createModelPart(cart, Material.RED_CONCRETE,
                -0.76f, 0.52f, -1.28f, 1.52f, 0.16f, 0.60f, "trunk"));
        parts.add(createModelPart(cart, Material.RED_CONCRETE,
                -0.72f, 1.48f, -0.68f, 1.44f, 0.14f, 1.22f, "roof"));

        parts.add(createModelPart(cart, Material.TINTED_GLASS,
                -0.68f, 0.52f, 0.50f, 1.36f, 0.96f, 0.06f, "windshield"));
        parts.add(createModelPart(cart, Material.TINTED_GLASS,
                -0.68f, 0.52f, -0.66f, 1.36f, 0.96f, 0.06f, "rear_glass"));
        parts.add(createModelPart(cart, Material.TINTED_GLASS,
                -0.72f, 0.52f, -0.60f, 0.06f, 0.96f, 1.10f, "side_glass_l"));
        parts.add(createModelPart(cart, Material.TINTED_GLASS,
                0.66f, 0.52f, -0.60f, 0.06f, 0.96f, 1.10f, "side_glass_r"));

        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                -0.73f, 0.52f, 0.44f, 0.11f, 0.96f, 0.11f, "pillar_fl"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                0.62f, 0.52f, 0.44f, 0.11f, 0.96f, 0.11f, "pillar_fr"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                -0.73f, 0.52f, -0.68f, 0.11f, 0.96f, 0.11f, "pillar_rl"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                0.62f, 0.52f, -0.68f, 0.11f, 0.96f, 0.11f, "pillar_rr"));

        parts.add(createModelPart(cart, Material.GRAY_CONCRETE,
                -0.82f, -0.18f, 1.24f, 1.64f, 0.24f, 0.14f, "bumper_f"));
        parts.add(createModelPart(cart, Material.GRAY_CONCRETE,
                -0.82f, -0.18f, -1.38f, 1.64f, 0.24f, 0.14f, "bumper_r"));
        parts.add(createModelPart(cart, Material.POLISHED_ANDESITE,
                -0.42f, 0.12f, 1.28f, 0.84f, 0.28f, 0.06f, "grille"));

        Material hlMat = areHeadlightsOn(cart) ? Material.SEA_LANTERN : Material.WHITE_STAINED_GLASS;
        parts.add(createModelPart(cart, hlMat,
                -0.68f, 0.20f, 1.28f, 0.24f, 0.18f, 0.06f, "headlight_l"));
        parts.add(createModelPart(cart, hlMat,
                0.44f, 0.20f, 1.28f, 0.24f, 0.18f, 0.06f, "headlight_r"));

        parts.add(createModelPart(cart, Material.REDSTONE_BLOCK,
                -0.68f, 0.22f, -1.34f, 0.26f, 0.16f, 0.05f, "taillight_l"));
        parts.add(createModelPart(cart, Material.REDSTONE_BLOCK,
                0.42f, 0.22f, -1.34f, 0.26f, 0.16f, 0.05f, "taillight_r"));

        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                -0.90f, -0.35f, 0.62f, 0.18f, 0.52f, 0.52f, "wheel_fl"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                0.72f, -0.35f, 0.62f, 0.18f, 0.52f, 0.52f, "wheel_fr"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                -0.90f, -0.35f, -1.06f, 0.18f, 0.52f, 0.52f, "wheel_rl"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                0.72f, -0.35f, -1.06f, 0.18f, 0.52f, 0.52f, "wheel_rr"));

        parts.add(createModelPart(cart, Material.IRON_BLOCK,
                -0.92f, -0.21f, 0.76f, 0.04f, 0.24f, 0.24f, "hub_fl"));
        parts.add(createModelPart(cart, Material.IRON_BLOCK,
                0.88f, -0.21f, 0.76f, 0.04f, 0.24f, 0.24f, "hub_fr"));
        parts.add(createModelPart(cart, Material.IRON_BLOCK,
                -0.92f, -0.21f, -0.92f, 0.04f, 0.24f, 0.24f, "hub_rl"));
        parts.add(createModelPart(cart, Material.IRON_BLOCK,
                0.88f, -0.21f, -0.92f, 0.04f, 0.24f, 0.24f, "hub_rr"));

        parts.add(createModelPart(cart, Material.NETHERITE_BLOCK,
                0.42f, -0.20f, -1.46f, 0.14f, 0.14f, 0.20f, "exhaust"));
    }

    // ---------- ГАЗель: бортовой грузовик с синим тентом ----------
    // Ось Z: вперёд = +Z. Белая кабина (водитель в точке 0,0) занимает Z -0.80..1.50.
    // Позади кабины серый борт и синий тент: Z -3.25..-0.80, верх тента на Y 2.55.
    // Ширина 2.0 (X ±1.0); нижняя точка колёс Y -0.35 — как у легковушки.

    private void buildGazelleModel(Minecart cart, List<BlockDisplay> parts) {
        // Рама
        parts.add(createModelPart(cart, Material.GRAY_CONCRETE,
                -0.86f, -0.12f, -3.25f, 1.72f, 0.24f, 4.70f, "chassis"));

        // Белая кабина: низ, «носик» капота и крыша
        parts.add(createModelPart(cart, Material.WHITE_CONCRETE,
                -1.00f, 0.12f, -0.80f, 2.00f, 0.50f, 2.30f, "body"));
        parts.add(createModelPart(cart, Material.WHITE_CONCRETE,
                -0.86f, 0.62f, 1.06f, 1.72f, 0.14f, 0.40f, "hood"));
        parts.add(createModelPart(cart, Material.WHITE_CONCRETE,
                -1.00f, 1.70f, -0.80f, 2.00f, 0.14f, 1.90f, "roof"));

        // Стёкла кабины
        parts.add(createModelPart(cart, Material.TINTED_GLASS,
                -0.92f, 0.62f, 1.00f, 1.84f, 1.08f, 0.06f, "windshield"));
        parts.add(createModelPart(cart, Material.TINTED_GLASS,
                -0.94f, 0.62f, -0.80f, 0.06f, 1.08f, 1.80f, "side_glass_l"));
        parts.add(createModelPart(cart, Material.TINTED_GLASS,
                0.88f, 0.62f, -0.80f, 0.06f, 1.08f, 1.80f, "side_glass_r"));

        // Стойки кабины
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                -0.98f, 0.62f, 0.96f, 0.14f, 1.08f, 0.14f, "pillar_fl"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                0.84f, 0.62f, 0.96f, 0.14f, 1.08f, 0.14f, "pillar_fr"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                -0.98f, 0.62f, -0.80f, 0.14f, 1.08f, 0.14f, "pillar_rl"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                0.84f, 0.62f, -0.80f, 0.14f, 1.08f, 0.14f, "pillar_rr"));

        // Грузовая платформа с серыми бортами, синий тент и тёмно-синяя «юбка» тента
        parts.add(createModelPart(cart, Material.LIGHT_GRAY_CONCRETE,
                -1.00f, 0.12f, -3.25f, 2.00f, 0.68f, 2.45f, "cargo_bed"));
        parts.add(createModelPart(cart, Material.BLUE_WOOL,
                -0.98f, 0.80f, -3.20f, 1.96f, 1.75f, 2.40f, "tent"));
        parts.add(createModelPart(cart, Material.BLUE_CONCRETE,
                -1.00f, 0.80f, -3.22f, 2.00f, 0.16f, 2.40f, "tent_skirt"));

        // Бамперы и решётка радиатора
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                -1.04f, -0.14f, 1.50f, 2.08f, 0.34f, 0.16f, "bumper_f"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                -1.04f, -0.14f, -3.41f, 2.08f, 0.34f, 0.16f, "bumper_r"));
        parts.add(createModelPart(cart, Material.POLISHED_ANDESITE,
                -0.40f, 0.20f, 1.50f, 0.80f, 0.32f, 0.05f, "grille"));

        // Фары: тёмные рамки и «лампы» (имена headlight_l / headlight_r нужны для переключения света на F)
        parts.add(createModelPart(cart, Material.GRAY_CONCRETE,
                -0.94f, 0.17f, 1.50f, 0.40f, 0.40f, 0.04f, "headlight_frame_l"));
        parts.add(createModelPart(cart, Material.GRAY_CONCRETE,
                0.54f, 0.17f, 1.50f, 0.40f, 0.40f, 0.04f, "headlight_frame_r"));
        Material hlMat = areHeadlightsOn(cart) ? Material.SEA_LANTERN : Material.WHITE_STAINED_GLASS;
        parts.add(createModelPart(cart, hlMat,
                -0.88f, 0.23f, 1.54f, 0.28f, 0.28f, 0.04f, "headlight_l"));
        parts.add(createModelPart(cart, hlMat,
                0.60f, 0.23f, 1.54f, 0.28f, 0.28f, 0.04f, "headlight_r"));

        // Задние фонари на борту платформы
        parts.add(createModelPart(cart, Material.REDSTONE_BLOCK,
                -0.90f, 0.30f, -3.30f, 0.20f, 0.36f, 0.05f, "taillight_l"));
        parts.add(createModelPart(cart, Material.REDSTONE_BLOCK,
                0.70f, 0.30f, -3.30f, 0.20f, 0.36f, 0.05f, "taillight_r"));

        // Колёса: большие, задние шире (имитация сдвоенных скатов) + диски
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                -1.14f, -0.35f, 0.45f, 0.30f, 0.80f, 0.80f, "wheel_fl"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                0.84f, -0.35f, 0.45f, 0.30f, 0.80f, 0.80f, "wheel_fr"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                -1.16f, -0.35f, -2.75f, 0.44f, 0.80f, 0.80f, "wheel_rl"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                0.72f, -0.35f, -2.75f, 0.44f, 0.80f, 0.80f, "wheel_rr"));

        parts.add(createModelPart(cart, Material.IRON_BLOCK,
                -1.16f, -0.12f, 0.68f, 0.04f, 0.34f, 0.34f, "hub_fl"));
        parts.add(createModelPart(cart, Material.IRON_BLOCK,
                1.12f, -0.12f, 0.68f, 0.04f, 0.34f, 0.34f, "hub_fr"));
        parts.add(createModelPart(cart, Material.IRON_BLOCK,
                -1.18f, -0.12f, -2.52f, 0.04f, 0.34f, 0.34f, "hub_rl"));
        parts.add(createModelPart(cart, Material.IRON_BLOCK,
                1.14f, -0.12f, -2.52f, 0.04f, 0.34f, 0.34f, "hub_rr"));

        // Выхлопная труба сзади справа
        parts.add(createModelPart(cart, Material.NETHERITE_BLOCK,
                0.55f, -0.30f, -3.50f, 0.18f, 0.18f, 0.40f, "exhaust"));

        // Боковые зеркала и подножки кабины
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                -1.18f, 1.00f, 0.92f, 0.22f, 0.40f, 0.12f, "mirror_l"));
        parts.add(createModelPart(cart, Material.BLACK_CONCRETE,
                0.96f, 1.00f, 0.92f, 0.22f, 0.40f, 0.12f, "mirror_r"));
        parts.add(createModelPart(cart, Material.GRAY_CONCRETE,
                -1.08f, 0.02f, -0.50f, 0.12f, 0.10f, 0.90f, "step_l"));
        parts.add(createModelPart(cart, Material.GRAY_CONCRETE,
                0.96f, 0.02f, -0.50f, 0.12f, 0.10f, 0.90f, "step_r"));
    }

    private void updateCarModel(Minecart cart, float yaw) {
        List<BlockDisplay> parts = carModels.get(cart.getUniqueId());
        if (parts == null || parts.isEmpty()) {
            spawnCarModel(cart);
            return;
        }

        boolean anyInvalid = false;
        for (BlockDisplay bd : parts) {
            if (bd == null || !bd.isValid()) {
                anyInvalid = true;
                break;
            }
            if (!cart.getPassengers().contains(bd)) {
                cart.addPassenger(bd);
            }
            // Отправляем пакет поворота только при реальном изменении угла, чтобы не перегружать клиент
            if (Math.abs(bd.getLocation().getYaw() - yaw) > 0.15f) {
                bd.setRotation(yaw, 0f);
            }
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

    // ==================== ПРЕДМЕТЫ И РЕЦЕПТЫ ====================

    private ItemStack createCarItem(CarType type) {
        ItemStack item = new ItemStack(Material.MINECART);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(type.itemName));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7Поставьте на землю (ПКМ) | Сесть: ПКМ"));
            lore.add(Component.text("§eShift + ПКМ снаружи — топливный бак (уголь)"));
            lore.add(Component.text("§eShift + ЛКМ снаружи — багажник (" + type.trunkSize + " слотов)"));
            lore.add(Component.text("§eПробел — завести | F — фары | Ctrl+1/2/3/4/Q — КПП"));
            if (type == CarType.GAZELLE) {
                lore.add(Component.text("§7Бортовой грузовик с тентом: медленнее легковушки, но вместительнее"));
            }
            meta.lore(lore);

            meta.getPersistentDataContainer().set(carKey, PersistentDataType.BYTE, type.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void registerCarRecipes() {
        // Легковой автомобиль (рецепт без изменений)
        ShapedRecipe carRecipe = new ShapedRecipe(
                new NamespacedKey(this, CarType.CAR.recipeKey), createCarItem(CarType.CAR));
        carRecipe.shape(" R ", "ILI", "IFI");
        carRecipe.setIngredient('R', Material.REDSTONE);
        carRecipe.setIngredient('I', Material.IRON_INGOT);
        carRecipe.setIngredient('L', Material.LEATHER);
        carRecipe.setIngredient('F', Material.FURNACE);
        Bukkit.addRecipe(carRecipe);

        // ГАЗель: сундук (грузовой отсек) и два железных блока (рама)
        ShapedRecipe gazelleRecipe = new ShapedRecipe(
                new NamespacedKey(this, CarType.GAZELLE.recipeKey), createCarItem(CarType.GAZELLE));
        gazelleRecipe.shape("IRI", "ICI", "BFB");
        gazelleRecipe.setIngredient('I', Material.IRON_INGOT);
        gazelleRecipe.setIngredient('R', Material.REDSTONE);
        gazelleRecipe.setIngredient('C', Material.CHEST);
        gazelleRecipe.setIngredient('B', Material.IRON_BLOCK);
        gazelleRecipe.setIngredient('F', Material.FURNACE);
        Bukkit.addRecipe(gazelleRecipe);
    }

    private boolean isCar(Minecart cart) {
        return cart.getPersistentDataContainer().has(carKey, PersistentDataType.BYTE);
    }

    private CarType getCarType(Minecart cart) {
        Byte id = cart.getPersistentDataContainer().get(carKey, PersistentDataType.BYTE);
        return id == null ? CarType.CAR : CarType.fromId(id);
    }

    // Возвращает тип транспорта, если предмет — наш автомобиль/ГАЗель, иначе null
    private CarType getItemCarType(ItemStack item) {
        if (item == null || item.getType() != Material.MINECART || !item.hasItemMeta()) return null;
        Byte id = item.getItemMeta().getPersistentDataContainer().get(carKey, PersistentDataType.BYTE);
        return id == null ? null : CarType.fromId(id);
    }

    private boolean isCarItem(ItemStack item) {
        return getItemCarType(item) != null;
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

    private double getGearSpeed(CarType type, int gear) {
        return switch (gear) {
            case GEAR_REVERSE -> type.speedReverse;
            case GEAR_FIRST -> type.speed1;
            case GEAR_SECOND -> type.speed2;
            case GEAR_THIRD -> type.speed3;
            default -> 0.0;
        };
    }

    private float getGearPitch(CarType type, int gear) {
        float base = switch (gear) {
            case GEAR_REVERSE -> 0.40f;
            case GEAR_FIRST -> 0.50f;
            case GEAR_SECOND -> 0.70f;
            case GEAR_THIRD -> 0.95f;
            default -> 0.35f;
        };
        return base * type.pitchMul;
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
        CarType type = getCarType(cart);
        FuelTankHolder holder = new FuelTankHolder(cart.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, type.tankSize, Component.text(type.tankTitle));
        holder.setInventory(inv);
        populateInventoryFromData(cart, tankDataKey, inv);
        return inv;
    }

    private Inventory getOrOpenTank(Minecart cart) {
        return openTanks.computeIfAbsent(cart.getUniqueId(), id -> loadTank(cart));
    }

    private Inventory loadTrunk(Minecart cart) {
        CarType type = getCarType(cart);
        TrunkHolder holder = new TrunkHolder(cart.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, type.trunkSize, Component.text(type.trunkTitle));
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

    private void destroyAndDropCar(Minecart cart) {
        // Тип нужно запомнить до удаления вагонетки, чтобы выпал правильный предмет
        CarType type = getCarType(cart);

        clearLights(cart.getUniqueId());
        removeCarModel(cart.getUniqueId());
        removeCarHitbox(cart.getUniqueId());

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
        smoothedYaw.remove(cart.getUniqueId());
        trackedCars.remove(cart.getUniqueId());

        cart.remove();
        cart.getWorld().dropItemNaturally(cart.getLocation(), createCarItem(type));
    }

    // ==================== ОБРАБОТКА ВЗАИМОДЕЙСТВИЙ ====================

    private Player getDriver(Minecart cart) {
        for (Entity passenger : cart.getPassengers()) {
            if (passenger instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    private void handleCarRightClick(Player player, Minecart cart) {
        if (player.getVehicle() != null) return;
        if (isDebounced(player)) return;

        if (player.isSneaking()) {
            Inventory tank = getOrOpenTank(cart);
            player.openInventory(tank);
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
        } else {
            if (getDriver(cart) == null) {
                cart.addPassenger(player);
            }
        }
    }

    private void handleCarLeftClick(Player player, Minecart cart) {
        if (player.getVehicle() != null) return;
        if (isDebounced(player)) return;

        if (player.isSneaking()) {
            Inventory trunk = getOrOpenTrunk(cart);
            player.openInventory(trunk);
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.8f, 1.0f);
        } else {
            destroyAndDropCar(cart);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Minecart cart = resolveCarFromEntity(event.getRightClicked());
        if (cart == null) return;

        event.setCancelled(true);
        handleCarRightClick(event.getPlayer(), cart);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Minecart cart = resolveCarFromEntity(event.getRightClicked());
        if (cart == null) return;

        event.setCancelled(true);
        handleCarRightClick(event.getPlayer(), cart);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClickEntity(EntityDamageByEntityEvent event) {
        Minecart cart = resolveCarFromEntity(event.getEntity());
        if (cart == null) return;

        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            handleCarLeftClick(player, cart);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart) || !isCar(cart)) return;

        event.setCancelled(true);
        if (event.getAttacker() instanceof Player player) {
            handleCarLeftClick(player, cart);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart) || !isCar(cart)) return;

        event.setCancelled(true);
        if (event.getAttacker() instanceof Player player) {
            handleCarLeftClick(player, cart);
        } else {
            destroyAndDropCar(cart);
        }
    }

    @EventHandler
    public void onPlayerFallInCar(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getVehicle() instanceof Minecart cart && isCar(cart)) {
            if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
                event.setCancelled(true);
            } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                double reducedDamage = event.getDamage() / getCarType(cart).fallDamageDivisor;
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
        CarType type = getItemCarType(item);
        if (type == null) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        event.setCancelled(true);

        Location spawnLoc = clicked.getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
        Minecart cart = (Minecart) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.MINECART);
        // Тип записываем ДО создания модели и хитбокса, чтобы они подхватили нужные размеры
        cart.getPersistentDataContainer().set(carKey, PersistentDataType.BYTE, type.id);
        cart.setCustomNameVisible(false);
        cart.setSilent(true);
        cart.setDerailedVelocityMod(new Vector(0.95, 0.95, 0.95));
        cart.setMaxSpeed(2.5);
        trackedCars.add(cart.getUniqueId());
        spawnCarModel(cart);
        spawnCarHitbox(cart);

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onEnter(VehicleEnterEvent event) {
        if (event.getVehicle() instanceof Minecart cart && isCar(cart) && event.getEntered() instanceof Player player) {
            trackedCars.add(cart.getUniqueId());
            smoothedYaw.put(cart.getUniqueId(), player.getEyeLocation().getYaw());
            cart.setCustomNameVisible(false);
            cart.setSilent(true);
            cart.setDerailedVelocityMod(new Vector(0.95, 0.95, 0.95));
            cart.setMaxSpeed(2.5);
            if (!carModels.containsKey(cart.getUniqueId())) {
                spawnCarModel(cart);
            }
            if (!carHitboxes.containsKey(cart.getUniqueId())) {
                spawnCarHitbox(cart);
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

    private boolean tryStepUp(Minecart cart, CarType type, Vector moveDir, double speed) {
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

        // Над головой должно быть свободно, иначе после подъёма машина врежется в потолок
        Block headroom1 = cart.getLocation().add(0, 1, 0).getBlock();
        Block headroom2 = cart.getLocation().add(0, 2, 0).getBlock();
        if (!isPassable(headroom1) || !isPassable(headroom2)) {
            return false;
        }

        Vector side = new Vector(-moveDir.getZ(), 0, moveDir.getX()).multiply(0.45);
        double[] distances = type.stepDistances;

        // Ищем ближайшее препятствие: перебираем дистанции от ближней к дальней и
        // останавливаемся на первой, где нашлась ступенька. Так машина поднимается
        // ровно перед барьером, а не «заранее» в паре блоков от него.
        for (double dist : distances) {
            Location center = cart.getLocation().add(moveDir.clone().multiply(dist));
            Location[] points = new Location[]{
                    center,
                    center.clone().add(side),
                    center.clone().subtract(side)
            };

            double targetStepY = -1.0;

            for (Location pt : points) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block b = pt.clone().add(0, dy, 0).getBlock();
                    if (b.isPassable()) continue;
                    if (Tag.FENCES.isTagged(b.getType()) || Tag.WALLS.isTagged(b.getType())) continue;

                    double topY = getBlockTopY(b);
                    double diff = topY - currentY;

                    if (diff > 0.04 && diff <= type.maxStepHeight) {
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

            if (targetStepY > currentY + 0.04) {
                // Подъём «как у лошади»: без прыжка по дуге. Мгновенно ставим машину
                // на высоту ступеньки, сохраняя горизонтальный ход — она продолжает
                // ехать вперёд и докатывается на ступеньку.
                double hSpeed = Math.max(Math.abs(speed), type.stepMinSpeed);

                Location target = cart.getLocation().clone();
                target.setY(targetStepY + 0.02);
                // Небольшой сдвиг вперёд, чтобы машина приземлилась на ступеньку,
                // а не обратно на прежний грунт
                target.add(moveDir.clone().multiply(Math.min(0.35, hSpeed * 0.5)));

                cart.teleport(target);
                cart.setVelocity(new Vector(moveDir.getX() * hSpeed, 0, moveDir.getZ() * hSpeed));
                stepCooldowns.put(cart.getUniqueId(), 2);
                return true;
            }
        }
        return false;
    }

    private Vector getCarForwardVector(float yaw) {
        double yawRad = Math.toRadians(yaw);
        return new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
    }

    // Плавная интерполяция угла поворота (рулевое сглаживание без резких рывков камеры)
    private float smoothYaw(UUID carId, float targetYaw, float factor) {
        float current = smoothedYaw.getOrDefault(carId, targetYaw);
        float delta = ((targetYaw - current + 540f) % 360f) - 180f;
        float next = current + delta * factor;
        smoothedYaw.put(carId, next);
        return next;
    }

    private void updateHeadlights(Minecart cart, CarType type, Vector forward) {
        if (!areHeadlightsOn(cart)) return;

        if (tickCounter % 3 == 0) {
            Vector right = new Vector(-forward.getZ(), 0, forward.getX()).multiply(type.headlightSide);
            Location frontCenter = cart.getLocation().add(forward.clone().multiply(type.headlightFront)).add(0, type.headlightHeight, 0);

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
        CarType type = getCarType(cart);
        Player driver = getDriver(cart);

        float carYaw;
        if (driver != null) {
            // Плавный поворот руля за взглядом водителя (коэффициент type.steering: у ГАЗели руль «тяжелее»)
            carYaw = smoothYaw(cart.getUniqueId(), driver.getEyeLocation().getYaw(), type.steering);
            if (Math.abs(cart.getLocation().getYaw() - carYaw) > 0.2f) {
                cart.setRotation(carYaw, 0f);
            }
        } else {
            carYaw = smoothedYaw.getOrDefault(cart.getUniqueId(), cart.getLocation().getYaw());
        }
        Vector forward = getCarForwardVector(carYaw);

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
                cart.setVelocity(new Vector(velocity.getX() * 0.85, velocity.getY(), velocity.getZ() * 0.85));
            } else {
                cart.setVelocity(new Vector(velocity.getX() * 0.98, velocity.getY(), velocity.getZ() * 0.98));
            }
            updateCarModel(cart, carYaw);
            updateCarHitbox(cart, type, carYaw);
            updateHeadlights(cart, type, forward);
            return;
        }

        if (!consumeFuelIfNeeded(cart)) {
            setEngineOn(cart, false);
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.8f);
            if (driver != null) {
                driver.sendActionBar(Component.text("§cТопливо закончилось! Двигатель заглох (Shift+ПКМ — заправить уголь)"));
            }
            updateCarModel(cart, carYaw);
            updateCarHitbox(cart, type, carYaw);
            updateHeadlights(cart, type, forward);
            return;
        }

        if (driver == null) {
            if (tickCounter % 2 == 0) {
                setFuelTicks(cart, getFuelTicks(cart) - 1);
            }
            if (onGround) {
                cart.setVelocity(new Vector(velocity.getX() * 0.85, velocity.getY(), velocity.getZ() * 0.85));
            }
            if (tickCounter % 8 == 0) {
                Location exhaust = cart.getLocation().subtract(forward.clone().multiply(type.exhaustBack)).add(0, 0.25, 0);
                cart.getWorld().spawnParticle(Particle.SMOKE, exhaust, 1, 0.05, 0.05, 0.05, 0.01);
                cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.15f, 0.35f);
            }
            updateCarModel(cart, carYaw);
            updateCarHitbox(cart, type, carYaw);
            updateHeadlights(cart, type, forward);
            return;
        }

        var input = driver.getCurrentInput();
        int currentGear = getGear(cart);
        double targetSpeed = 0.0;

        if (input.isForward() && !input.isBackward()) {
            targetSpeed = getGearSpeed(type, currentGear);
        }

        int burnRate = input.isForward() ? type.burnForward : 1;
        setFuelTicks(cart, getFuelTicks(cart) - burnRate);

        if (tickCounter % 20 == 0) {
            int secLeft = getTotalFuelSeconds(cart);
            String color = secLeft > 30 ? "§a" : (secLeft > 10 ? "§e" : "§c");
            driver.sendActionBar(Component.text("§7КПП: " + formatGearShort(currentGear) + " §7| Топливо: " + color + secLeft + "с §7| Ctrl+1/2/3/4/Q"));
        }

        if (targetSpeed != 0.0) {
            Location exhaust = cart.getLocation().subtract(forward.clone().multiply(type.exhaustBack)).add(0, 0.25, 0);
            cart.getWorld().spawnParticle(Particle.LARGE_SMOKE, exhaust, 1, 0.08, 0.05, 0.08, 0.01);
            if (tickCounter % 3 == 0) {
                cart.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, exhaust, 1, 0.05, 0.05, 0.05, 0.01);
            }

            if (onGround) {
                Vector moveDir = targetSpeed > 0 ? forward.clone() : forward.clone().multiply(-1);

                if (!tryStepUp(cart, type, moveDir, targetSpeed)) {
                    // Плавное ускорение и руление (интерполяция скорости вместо мгновенного скачка)
                    Vector targetVel = forward.clone().multiply(targetSpeed);
                    double smoothX = velocity.getX() + (targetVel.getX() - velocity.getX()) * type.acceleration;
                    double smoothZ = velocity.getZ() + (targetVel.getZ() - velocity.getZ()) * type.acceleration;
                    cart.setVelocity(new Vector(smoothX, velocity.getY(), smoothZ));
                }

                if (tickCounter % 3 == 0) {
                    cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.35f, getGearPitch(type, currentGear));
                }
            } else {
                cart.setVelocity(new Vector(velocity.getX() * 0.98, velocity.getY(), velocity.getZ() * 0.98));

                if (tickCounter % 3 == 0) {
                    cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.4f, 0.95f);
                }
            }
        } else {
            // Плавный накат при отпускании газа и торможение при нажатии S (значения зависят от типа транспорта)
            double drag = input.isBackward() ? type.brakeDrag : type.coastDrag;
            if (onGround) {
                cart.setVelocity(new Vector(velocity.getX() * drag, velocity.getY(), velocity.getZ() * drag));
            } else {
                cart.setVelocity(new Vector(velocity.getX() * 0.98, velocity.getY(), velocity.getZ() * 0.98));
            }

            if (input.isForward() && currentGear == GEAR_NEUTRAL) {
                Location exhaust = cart.getLocation().subtract(forward.clone().multiply(type.exhaustBack)).add(0, 0.25, 0);
                cart.getWorld().spawnParticle(Particle.LARGE_SMOKE, exhaust, 1, 0.06, 0.04, 0.06, 0.01);
                if (tickCounter % 3 == 0) {
                    cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.35f, 0.80f);
                }
            } else if (tickCounter % 8 == 0) {
                Location exhaust = cart.getLocation().subtract(forward.clone().multiply(type.exhaustBack)).add(0, 0.25, 0);
                cart.getWorld().spawnParticle(Particle.SMOKE, exhaust, 1, 0.05, 0.05, 0.05, 0.01);
                cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.15f, 0.35f);
            }
        }

        updateCarModel(cart, carYaw);
        updateCarHitbox(cart, type, carYaw);
        updateHeadlights(cart, type, forward);
    }
}
