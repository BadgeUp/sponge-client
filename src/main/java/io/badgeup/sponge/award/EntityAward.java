package io.badgeup.sponge.award;

import com.flowpowered.math.vector.Vector3d;
import io.badgeup.sponge.BadgeUpSponge;
import io.badgeup.sponge.util.Util;
import org.json.JSONObject;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.type.DyeColor;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;

import java.util.Optional;

public class EntityAward extends Award {

    public EntityAward(BadgeUpSponge plugin, JSONObject award) {
        super(plugin, award);
    }

    @Override
    public boolean awardPlayer(Player player) {
        Optional<String> entityTypeIDOpt = Util.safeGetString(this.data, "entityType");
        if (!entityTypeIDOpt.isPresent()) {
            this.plugin.getLogger().error("No entity type specified. Aborting.");
            return false;
        }

        String entityTypeID = entityTypeIDOpt.get();

        final Optional<EntityType> optType = Sponge.getRegistry().getType(EntityType.class, entityTypeID);
        if (!optType.isPresent()) {
            this.plugin.getLogger().error("Entity type " + entityTypeID + " not found. Aborting.");
            return false;
        }

        // Default to the player's position
        JSONObject rawPosition = Util.safeGetJSONObject(this.data, "position")
                .orElse(new JSONObject().put("x", "~").put("y", "~").put("z", "~"));

        Optional<Vector3d> positionOpt = resolvePosition(rawPosition, player.getLocation().getPosition());
        if (!positionOpt.isPresent()) {
            this.plugin.getLogger().error("Malformed entity position. Aborting.");
            return false;
        }

        Vector3d position = positionOpt.get();
        Entity entity = player.getWorld().createEntity(optType.get(), position);

        Optional<String> colorIdOpt = Util.safeGetString(this.data, "color");
        if (colorIdOpt.isPresent()) {
            Optional<DyeColor> colorOpt = Sponge.getRegistry().getType(DyeColor.class, colorIdOpt.get());
            if (colorOpt.isPresent()) {
                entity.offer(Keys.DYE_COLOR, colorOpt.get());
            } else {
                this.plugin.getLogger().error("Could not retrieve DyeColor with ID " + colorIdOpt.get() + ". Skipping.");
            }
        }

        final Optional<Text> displayNameOpt = Util.deserializeText(Util.safeGet(this.data, "displayName").orElse(null));
        if (displayNameOpt.isPresent()) {
            entity.offer(Keys.DISPLAY_NAME, displayNameOpt.get());
        }

        return player.getWorld().spawnEntity(entity);
    }

    private Optional<Vector3d> resolvePosition(JSONObject raw, Vector3d playerPosition) {
        JSONObject relativePosition = new JSONObject().put("x", playerPosition.getX()).put("y", playerPosition.getY())
                .put("z", playerPosition.getZ());

        JSONObject finalPosition = new JSONObject();

        String[] keys = {"x", "y", "z"};
        for (String key : keys) {
            if (!raw.has(key)) {
                return Optional.empty();
            }
            boolean isRelative = false;
            Optional<Double> coordOpt;
            if (raw.get(key) instanceof String) {
                String coordinateString = raw.getString(key);
                int tildeIndex = coordinateString.indexOf('~');
                if (tildeIndex > -1) {
                    isRelative = true;
                    coordinateString = coordinateString.substring(0, tildeIndex)
                            + coordinateString.substring(tildeIndex + 1);
                }
                // If the string was only ever just "~"
                if (coordinateString.isEmpty()) {
                    coordOpt = Optional.of(0d);
                } else {
                    coordOpt = Util.safeParseDouble(coordinateString);
                }
            } else {
                coordOpt = Util.safeGetDouble(raw, key);
            }

            if (!coordOpt.isPresent()) {
                return Optional.empty();
            }

            if (isRelative) {
                finalPosition.put(key, relativePosition.getDouble(key) + coordOpt.get());
            } else {
                finalPosition.put(key, coordOpt.get());
            }
        }

        return Optional.of(
                new Vector3d(finalPosition.getDouble("x"), finalPosition.getDouble("y"), finalPosition.getDouble("z")));

    }

}
