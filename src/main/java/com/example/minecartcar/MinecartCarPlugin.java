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
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MinecartCarPlugin extends JavaPlugin implements Listener {

    private static final double MAX_SPEED = 0.70;
    private NamespacedKey carKey;
    private NamespacedKey headlightsKey;
    private NamespacedKey engineKey;
    private int tickCounter = 0;

    // Храним активные машины и их блоки света (по 2 блока света на луч: ближний и дальний)
    private final Set<UUID> trackedCars = new HashSet<>();
    private final Map<UUID, List<Block>> activeLightBlocks = new HashMap<>();

    @Override
    public void onEnable() {
        carKey = new NamespacedKey(this, "custom_car");
        headlightsKey = new NamespacedKey(this, "headlights_on");
        engineKey = new NamespacedKey(this, "engine_on");
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCarRecipe();

        // При включении плагина находим уже стоящие в мире машины
        for (World world : Bukkit.getWorlds()) {
            for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
                if (isCar(cart)) {
                    trackedCars.add(cart.getUniqueId());
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
                    it.remove();
                    continue;
                }
                tickCar(cart);
            }
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        for (UUID id : new HashSet<>(activeLightBlocks.keySet())) {
            clearLights(id);
        }
        activeLightBlocks.clear();
        trackedCars.clear();
    }

    private ItemStack createCarItem() {
        ItemStack item = new ItemStack(Material.MINECART);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§6§lАвтомобиль"));
            meta.lore(List.of(
                    Component.text("§7Поставьте на землю и сядьте (ПКМ)"),
                    Component.text("§eПробел — завести/заглушить | F — фары"),
                    Component.text("§eW/S — газ/тормоз | Камера — руль")
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

    // Переключение фар клавишей F
    @EventHandler
    public void onToggleHeadlights(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.getVehicle() instanceof Minecart cart && isCar(cart)) {
            event.setCancelled(true);
            boolean newState = !areHeadlightsOn(cart);
            cart.getPersistentDataContainer().set(headlightsKey, PersistentDataType.BYTE, newState ? (byte) 1 : (byte) 0);

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
        cart.customName(Component.text("§6Автомобиль"));
        cart.setCustomNameVisible(false);
        cart.setSilent(true);
        cart.setDerailedVelocityMod(new Vector(0.95, 0.95, 0.95));
        cart.setMaxSpeed(1.5);
        trackedCars.add(cart.getUniqueId());

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onBreakCar(VehicleDestroyEvent event) {
        if (event.getVehicle() instanceof Minecart cart && isCar(cart)) {
            event.setCancelled(true);
            clearLights(cart.getUniqueId());
            trackedCars.remove(cart.getUniqueId());
            cart.remove();
            cart.getWorld().dropItemNaturally(cart.getLocation(), createCarItem());
        }
    }

    @EventHandler
    public void onEnter(VehicleEnterEvent event) {
        if (event.getVehicle() instanceof Minecart cart && isCar(cart) && event.getEntered() instanceof Player player) {
            trackedCars.add(cart.getUniqueId());
            cart.setSilent(true);
            cart.setDerailedVelocityMod(new Vector(0.95, 0.95, 0.95));
            cart.setMaxSpeed(1.5);
            if (isEngineOn(cart)) {
                player.sendActionBar(Component.text("§aДвигатель работает §7| §eПробел — заглушить | F — фары"));
            } else {
                player.sendActionBar(Component.text("§cДвигатель заглушен §7| §eНажмите Пробел, чтобы завести | F — фары"));
            }
        }
    }

    @EventHandler
    public void onExit(VehicleExitEvent event) {
        if (event.getVehicle() instanceof Minecart cart && isCar(cart)) {
            // При выходе игрока фары НЕ гасятся и продолжают освещать дорогу!
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

    private boolean canStepUp(Minecart cart, Vector moveDir) {
        Vector side = new Vector(-moveDir.getZ(), 0, moveDir.getX()).multiply(0.45);
        Location base = cart.getLocation().add(0, 0.25, 0);

        double[] distances = new double[]{0.75, 1.15};
        for (double dist : distances) {
            Location center = base.clone().add(moveDir.clone().multiply(dist));
            Location[] checkPoints = new Location[]{
                    center,
                    center.clone().add(side),
                    center.clone().subtract(side)
            };

            for (Location pt : checkPoints) {
                Block front = pt.getBlock();
                if (front.isPassable() || front.getType() == Material.DIRT_PATH) {
                    continue;
                }
                if (Tag.FENCES.isTagged(front.getType()) || Tag.WALLS.isTagged(front.getType())) {
                    continue;
                }

                Block above1 = front.getRelative(0, 1, 0);
                Block above2 = front.getRelative(0, 2, 0);

                if (isPassable(above1) && isPassable(above2)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Получаем горизонтальный вектор направления носа машины (работает даже без водителя)
    private Vector getCarForwardVector(Minecart cart) {
        double yawRad = Math.toRadians(cart.getLocation().getYaw());
        return new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
    }

    private void updateHeadlights(Minecart cart, Vector forward) {
        if (!areHeadlightsOn(cart)) return;

        // Аккуратные точки фар на корпусе и легкий след луча впереди
        if (tickCounter % 3 == 0) {
            Vector right = new Vector(-forward.getZ(), 0, forward.getX()).multiply(0.32);
            Location frontCenter = cart.getLocation().add(forward.clone().multiply(0.65)).add(0, 0.42, 0);

            Location leftLight = frontCenter.clone().subtract(right);
            Location rightLight = frontCenter.clone().add(right);

            Particle.DustOptions warmLight = new Particle.DustOptions(Color.fromRGB(255, 240, 170), 0.75f);
            cart.getWorld().spawnParticle(Particle.DUST, leftLight, 1, 0.01, 0.01, 0.01, 0.0, warmLight);
            cart.getWorld().spawnParticle(Particle.DUST, rightLight, 1, 0.01, 0.01, 0.01, 0.0, warmLight);

            Vector beamOffset = forward.clone().multiply(2.8);
            cart.getWorld().spawnParticle(Particle.DUST, leftLight.clone().add(beamOffset), 1, 0.05, 0.02, 0.05, 0.0, warmLight);
            cart.getWorld().spawnParticle(Particle.DUST, rightLight.clone().add(beamOffset), 1, 0.05, 0.02, 0.05, 0.0, warmLight);
        }

        // Дальний свет фар: ставим два невидимых источника света (на 4.5 и 8.5 блоков вперед)
        if (tickCounter % 2 == 0) {
            Block nearBlock = findAirNear(cart.getLocation().add(forward.clone().multiply(4.5)).add(0, 1.0, 0));
            Block farBlock = findAirNear(cart.getLocation().add(forward.clone().multiply(8.5)).add(0, 1.2, 0));

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

        // Фары работают всегда, пока включены (даже если водитель вышел)
        updateHeadlights(cart, forward);

        // Зажигание по нажатию Пробела (с защитой от дребезга раз в 10 тиков)
        if (driver != null) {
            var input = driver.getCurrentInput();
            if (input.isJump() && tickCounter % 10 == 0) {
                boolean newEngineState = !isEngineOn(cart);
                setEngineOn(cart, newEngineState);
                if (newEngineState) {
                    cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.9f, 0.7f);
                    cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.6f, 0.6f);
                    driver.sendActionBar(Component.text("§aДвигатель заведен! §eW/S — газ/тормоз | F — фары"));
                } else {
                    cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.9f, 0.6f);
                    driver.sendActionBar(Component.text("§cДвигатель заглушен. §7Нажмите Пробел, чтобы завести"));
                }
            }
        }

        Vector velocity = cart.getVelocity();
        boolean onGround = hasGroundTraction(cart);

        // Если двигатель заглушен — машина не едет от W/S и не дымит
        if (!isEngineOn(cart)) {
            if (onGround) {
                cart.setVelocity(new Vector(velocity.getX() * 0.8, velocity.getY(), velocity.getZ() * 0.8));
            } else {
                cart.setVelocity(new Vector(velocity.getX() * 0.98, velocity.getY(), velocity.getZ() * 0.98));
            }
            return;
        }

        // Если двигатель заведен, но водителя нет — машина работает на холостом ходу (дымит и урчит)
        if (driver == null) {
            if (onGround) {
                cart.setVelocity(new Vector(velocity.getX() * 0.8, velocity.getY(), velocity.getZ() * 0.8));
            }
            if (tickCounter % 8 == 0) {
                Location exhaust = cart.getLocation().subtract(forward.clone().multiply(0.7)).add(0, 0.25, 0);
                cart.getWorld().spawnParticle(Particle.SMOKE, exhaust, 1, 0.05, 0.05, 0.05, 0.01);
                cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.15f, 0.35f);
            }
            return;
        }

        var input = driver.getCurrentInput();
        double speed = 0.0;
        if (input.isForward()) {
            speed = MAX_SPEED;
        } else if (input.isBackward()) {
            speed = -MAX_SPEED * 0.5;
        }

        if (speed != 0.0) {
            Location exhaust = cart.getLocation().subtract(forward.clone().multiply(0.7)).add(0, 0.25, 0);
            cart.getWorld().spawnParticle(Particle.LARGE_SMOKE, exhaust, 1, 0.08, 0.05, 0.08, 0.01);
            if (tickCounter % 3 == 0) {
                cart.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, exhaust, 1, 0.05, 0.05, 0.05, 0.01);
            }

            if (onGround) {
                Vector moveDir = speed > 0 ? forward.clone() : forward.clone().multiply(-1);
                Vector move = forward.clone().multiply(speed);
                double yVel = velocity.getY();

                if (canStepUp(cart, moveDir)) {
                    yVel = 0.56;
                }

                cart.setVelocity(new Vector(move.getX(), yVel, move.getZ()));

                if (tickCounter % 3 == 0) {
                    cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.35f, 0.45f);
                }
            } else {
                cart.setVelocity(new Vector(velocity.getX() * 0.98, velocity.getY(), velocity.getZ() * 0.98));

                if (tickCounter % 3 == 0) {
                    cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.4f, 0.85f);
                }
            }
        } else {
            if (onGround) {
                cart.setVelocity(new Vector(velocity.getX() * 0.8, velocity.getY(), velocity.getZ() * 0.8));
            } else {
                cart.setVelocity(new Vector(velocity.getX() * 0.98, velocity.getY(), velocity.getZ() * 0.98));
            }

            if (tickCounter % 8 == 0) {
                Location exhaust = cart.getLocation().subtract(forward.clone().multiply(0.7)).add(0, 0.25, 0);
                cart.getWorld().spawnParticle(Particle.SMOKE, exhaust, 1, 0.05, 0.05, 0.05, 0.01);
                cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.15f, 0.35f);
            }
        }
    }
}
