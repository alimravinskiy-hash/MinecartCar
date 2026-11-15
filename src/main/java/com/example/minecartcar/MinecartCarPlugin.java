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
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MinecartCarPlugin extends JavaPlugin implements Listener {

    private static final double MAX_SPEED = 0.70;
    private NamespacedKey carKey;
    private NamespacedKey headlightsKey;
    private int tickCounter = 0;

    private final Map<UUID, Block> activeLightBlocks = new HashMap<>();

    @Override
    public void onEnable() {
        carKey = new NamespacedKey(this, "custom_car");
        headlightsKey = new NamespacedKey(this, "headlights_on");
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCarRecipe();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            tickCounter++;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getVehicle() instanceof Minecart cart && isCar(cart)) {
                    driveMinecart(player, cart);
                }
            }
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        for (Block block : activeLightBlocks.values()) {
            if (block.getType() == Material.LIGHT) {
                block.setType(Material.AIR, false);
            }
        }
        activeLightBlocks.clear();
    }

    private ItemStack createCarItem() {
        ItemStack item = new ItemStack(Material.MINECART);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§6§lАвтомобиль"));
            meta.lore(List.of(
                    Component.text("§7Поставьте на землю и сядьте (ПКМ)"),
                    Component.text("§eW/S — газ/тормоз | F — фары | Пробел — гудок")
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

    private void clearLight(UUID cartId) {
        Block oldBlock = activeLightBlocks.remove(cartId);
        if (oldBlock != null && oldBlock.getType() == Material.LIGHT) {
            oldBlock.setType(Material.AIR, false);
        }
    }

    @EventHandler
    public void onToggleHeadlights(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.getVehicle() instanceof Minecart cart && isCar(cart)) {
            event.setCancelled(true);
            boolean newState = !areHeadlightsOn(cart);
            cart.getPersistentDataContainer().set(headlightsKey, PersistentDataType.BYTE, newState ? (byte) 1 : (byte) 0);

            if (!newState) {
                clearLight(cart.getUniqueId());
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

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onBreakCar(VehicleDestroyEvent event) {
        if (event.getVehicle() instanceof Minecart cart && isCar(cart)) {
            event.setCancelled(true);
            clearLight(cart.getUniqueId());
            cart.remove();
            cart.getWorld().dropItemNaturally(cart.getLocation(), createCarItem());
        }
    }

    @EventHandler
    public void onEnter(VehicleEnterEvent event) {
        if (event.getVehicle() instanceof Minecart cart && isCar(cart) && event.getEntered() instanceof Player player) {
            cart.setSilent(true);
            cart.setDerailedVelocityMod(new Vector(0.95, 0.95, 0.95));
            cart.setMaxSpeed(1.5);
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.8f, 0.6f);
            player.sendActionBar(Component.text("§eW/S — газ/тормоз | F — фары | Пробел — гудок"));
        }
    }

    @EventHandler
    public void onExit(VehicleExitEvent event) {
        if (event.getVehicle() instanceof Minecart cart && isCar(cart)) {
            cart.setVelocity(new Vector(0, 0, 0));
            clearLight(cart.getUniqueId());
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.8f, 0.6f);
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

    private void updateHeadlights(Minecart cart, Vector forward) {
        if (!areHeadlightsOn(cart)) return;

        // Мягкие, аккуратные частицы фар раз в 3 тика, чтобы не перекрывать обзор
        if (tickCounter % 3 == 0) {
            Vector right = new Vector(-forward.getZ(), 0, forward.getX()).multiply(0.32);
            Location frontCenter = cart.getLocation().add(forward.clone().multiply(0.65)).add(0, 0.42, 0);

            Location leftLight = frontCenter.clone().subtract(right);
            Location rightLight = frontCenter.clone().add(right);

            Particle.DustOptions warmLight = new Particle.DustOptions(Color.fromRGB(255, 240, 170), 0.75f);
            cart.getWorld().spawnParticle(Particle.DUST, leftLight, 1, 0.01, 0.01, 0.01, 0.0, warmLight);
            cart.getWorld().spawnParticle(Particle.DUST, rightLight, 1, 0.01, 0.01, 0.01, 0.0, warmLight);

            Vector beamOffset = forward.clone().multiply(1.8);
            cart.getWorld().spawnParticle(Particle.DUST, leftLight.clone().add(beamOffset), 1, 0.05, 0.02, 0.05, 0.0, warmLight);
            cart.getWorld().spawnParticle(Particle.DUST, rightLight.clone().add(beamOffset), 1, 0.05, 0.02, 0.05, 0.0, warmLight);
        }

        // Реальное освещение дороги впереди невидимым блоком света
        if (tickCounter % 2 == 0) {
            Block targetBlock = cart.getLocation().add(forward.clone().multiply(3.5)).add(0, 1.0, 0).getBlock();
            Block prevBlock = activeLightBlocks.get(cart.getUniqueId());

            if (prevBlock != null && !prevBlock.equals(targetBlock) && prevBlock.getType() == Material.LIGHT) {
                prevBlock.setType(Material.AIR, false);
            }

            if (targetBlock.getType() == Material.AIR) {
                targetBlock.setType(Material.LIGHT, false);
                if (targetBlock.getBlockData() instanceof Levelled levelled) {
                    levelled.setLevel(15);
                    targetBlock.setBlockData(levelled, false);
                }
                activeLightBlocks.put(cart.getUniqueId(), targetBlock);
            }
        }
    }

    private void driveMinecart(Player player, Minecart cart) {
        var input = player.getCurrentInput();
        float yaw = player.getEyeLocation().getYaw();
        cart.setRotation(yaw, 0f);

        Vector forward = player.getEyeLocation().getDirection().setY(0).normalize();
        updateHeadlights(cart, forward);

        double speed = 0.0;
        if (input.isForward()) {
            speed = MAX_SPEED;
        } else if (input.isBackward()) {
            speed = -MAX_SPEED * 0.5;
        }

        if (input.isJump() && tickCounter % 8 == 0) {
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.55f);
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.75f);
        }

        Vector velocity = cart.getVelocity();
        boolean onGround = hasGroundTraction(cart);

        if (speed != 0.0) {
            // Плотный выхлопной дым сзади машины оставлен как раньше
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
