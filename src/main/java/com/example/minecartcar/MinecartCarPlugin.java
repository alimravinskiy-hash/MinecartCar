package com.example.minecartcar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

    private static final double MAX_SPEED = 0.65;
    private NamespacedKey carKey;

    @Override
    public void onEnable() {
        carKey = new NamespacedKey(this, "custom_car");
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCarRecipe();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
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
                    Component.text("§eУправление: W/S — газ/тормоз, камера — руль")
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
        cart.setDerailedVelocityMod(new Vector(0.95, 0.95, 0.95));
        cart.setMaxSpeed(1.5);

        if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
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
            cart.setDerailedVelocityMod(new Vector(0.95, 0.95, 0.95));
            cart.setMaxSpeed(1.5);
            player.sendActionBar(Component.text("§eУправление: W/S — газ/тормоз, камера — поворот"));
        }
    }

    @EventHandler
    public void onExit(VehicleExitEvent event) {
        if (event.getVehicle() instanceof Minecart cart && isCar(cart)) {
            cart.setVelocity(new Vector(0, 0, 0));
        }
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

        Vector velocity = cart.getVelocity();
        if (speed != 0.0) {
            Vector move = forward.multiply(speed);
            double yVel = velocity.getY();

            Location frontLoc = cart.getLocation().add(forward.clone().multiply(0.8));
            Block frontBlock = frontLoc.getBlock();
            if (frontBlock.getType().isSolid() && !frontBlock.getRelative(0, 1, 0).getType().isSolid()) {
                yVel = 0.35;
            }

            cart.setVelocity(new Vector(move.getX(), yVel, move.getZ()));
        } else {
            cart.setVelocity(new Vector(velocity.getX() * 0.8, velocity.getY(), velocity.getZ() * 0.8));
        }
    }
}
