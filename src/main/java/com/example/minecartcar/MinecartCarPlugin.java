package com.example.minecartcar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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

import java.util.List;

public class MinecartCarPlugin extends JavaPlugin implements Listener {

    private static final double MAX_SPEED = 0.70;
    private NamespacedKey carKey;
    private int tickCounter = 0;

    @Override
    public void onEnable() {
        carKey = new NamespacedKey(this, "custom_car");
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

    private ItemStack createCarItem() {
        ItemStack item = new ItemStack(Material.MINECART);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§6§lАвтомобиль"));
            meta.lore(List.of(
                    Component.text("§7Поставьте на землю и сядьте (ПКМ)"),
                    Component.text("§eW/S — газ/тормоз | Пробел — гудок")
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
            player.sendActionBar(Component.text("§eW/S — газ/тормоз | Камера — руль | Пробел — гудок"));
        }
    }

    @EventHandler
    public void onExit(VehicleExitEvent event) {
        if (event.getVehicle() instanceof Minecart cart && isCar(cart)) {
            cart.setVelocity(new Vector(0, 0, 0));
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.8f, 0.6f);
        }
    }

    // Проверяем, стоят ли колеса машины на твердой опоре (включая полублоки и ступеньки)
    private boolean hasGroundTraction(Minecart cart) {
        if (cart.isOnGround()) return true;
        Block below = cart.getLocation().subtract(0, 0.25, 0).getBlock();
        return below.getType().isSolid();
    }

    private void driveMinecart(Player player, Minecart cart) {
        var input = player.getCurrentInput();
        float yaw = player.getEyeLocation().getYaw();
        cart.setRotation(yaw, 0f);

        Vector forward = player.getEyeLocation().getDirection().setY(0).normalize();
        double speed = 0.0;

        if (input.isForward()) {
            speed = MAX_SPEED;
        } else if (input.isBackward()) {
            speed = -MAX_SPEED * 0.5;
        }

        // Клаксон (гудок) по нажатию Пробела
        if (input.isJump() && tickCounter % 8 == 0) {
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.55f);
            cart.getWorld().playSound(cart.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.75f);
        }

        Vector velocity = cart.getVelocity();
        boolean onGround = hasGroundTraction(cart);

        if (speed != 0.0) {
            // Выхлопной дым работает всегда при нажатии газа
            Location exhaust = cart.getLocation().subtract(forward.clone().multiply(0.7)).add(0, 0.25, 0);
            cart.getWorld().spawnParticle(Particle.LARGE_SMOKE, exhaust, 1, 0.08, 0.05, 0.08, 0.01);
            if (tickCounter % 3 == 0) {
                cart.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, exhaust, 1, 0.05, 0.05, 0.05, 0.01);
            }

            if (onGround) {
                // Колеса на земле: разгоняем машину и заезжаем на блоки высотой 1 метр
                Vector move = forward.clone().multiply(speed);
                double yVel = velocity.getY();

                Vector checkDir = speed > 0 ? forward.clone() : forward.clone().multiply(-1);
                Location frontLoc = cart.getLocation().add(checkDir.multiply(0.85));
                Block frontBlock = frontLoc.getBlock();
                Block aboveFront = frontBlock.getRelative(0, 1, 0);

                if (frontBlock.getType().isSolid() && !aboveFront.getType().isSolid()) {
                    Location stepUp = cart.getLocation().add(0, 0.55, 0);
                    cart.teleport(stepUp);
                    yVel = 0.15;
                }

                cart.setVelocity(new Vector(move.getX(), yVel, move.getZ()));

                if (tickCounter % 3 == 0) {
                    cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.35f, 0.45f);
                }
            } else {
                // Машина в воздухе: колесам не от чего отталкиваться, сохраняем только текущую инерцию полета
                cart.setVelocity(new Vector(velocity.getX() * 0.98, velocity.getY(), velocity.getZ() * 0.98));

                // В воздухе мотор крутит колеса вхолостую на повышенных оборотах
                if (tickCounter % 3 == 0) {
                    cart.getWorld().playSound(cart.getLocation(), Sound.ENTITY_MINECART_RIDING, 0.4f, 0.85f);
                }
            }
        } else {
            if (onGround) {
                // На земле без газа плавно тормозим
                cart.setVelocity(new Vector(velocity.getX() * 0.8, velocity.getY(), velocity.getZ() * 0.8));
            } else {
                // В воздухе без газа летим по инерции
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
