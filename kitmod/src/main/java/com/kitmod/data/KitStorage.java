package com.kitmod.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kitmod.KitMod;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Хранилище китов: {@code config/kitmod/kits/<id>.json}.
 *
 * <p>JSON, а не NBT: файл можно прочитать глазами, закоммитить и передать.</p>
 */
public final class KitStorage {
	private final Path dir;
	private final Map<String, KitData> kits = new LinkedHashMap<>();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public KitStorage(Path dir) {
		this.dir = dir;
	}

	public Path getDir() {
		return this.dir;
	}

	public Collection<KitData> all() {
		return new ArrayList<>(this.kits.values());
	}

	public KitData get(String id) {
		return this.kits.get(id);
	}

	public boolean exists(String id) {
		return this.kits.containsKey(id);
	}

	public int count() {
		return this.kits.size();
	}

	/** Читает все киты с диска. Нужен сервер — для RegistryOps. */
	public void loadAll(MinecraftServer server) {
		this.kits.clear();

		if (!Files.isDirectory(this.dir)) {
			return;
		}

		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
		List<Path> files = new ArrayList<>();

		try (var stream = Files.list(this.dir)) {
			stream.filter(path -> path.toString().endsWith(".json")).sorted().forEach(files::add);
		} catch (IOException e) {
			KitMod.LOGGER.warn("Could not list kit directory {}", this.dir, e);
			return;
		}

		for (Path path : files) {
			try {
				JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
				KitData kit = fromJson(root, ops);
				this.kits.put(kit.getId(), kit);
			} catch (RuntimeException | IOException e) {
				KitMod.LOGGER.warn("Could not read kit {}", path, e);
			}
		}

		KitMod.LOGGER.info("Loaded {} kit(s) from {}", this.kits.size(), this.dir);
	}

	/** Сохраняет кит на диск и обновляет кэш. */
	public void save(KitData kit, MinecraftServer server) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
		JsonObject root = toJson(kit, ops);

		try {
			Files.createDirectories(this.dir);
			Files.writeString(this.dir.resolve(kit.getId() + ".json"), this.gson.toJson(root));
		} catch (IOException e) {
			KitMod.LOGGER.error("Could not save kit {}", kit.getId(), e);
			return;
		}

		this.kits.put(kit.getId(), kit);
	}

	public boolean delete(String id) {
		KitData removed = this.kits.remove(id);
		if (removed == null) {
			return false;
		}

		try {
			Files.deleteIfExists(this.dir.resolve(id + ".json"));
		} catch (IOException e) {
			KitMod.LOGGER.warn("Could not delete kit file {}", id, e);
		}
		return true;
	}

	// ------------------------------------------------------------------
	// (де)сериализация
	// ------------------------------------------------------------------

	private JsonObject toJson(KitData kit, RegistryOps<JsonElement> ops) {
		JsonObject root = new JsonObject();
		root.addProperty("id", kit.getId());
		root.addProperty("name", kit.getDisplayName());
		root.add("icon", stackToJson(kit.getIcon(), ops));
		root.add("offhand", stackToJson(kit.getOffhand(), ops));
		root.add("inventory", stacksToJson(kit.getInventory(), ops));
		root.add("armor", stacksToJson(kit.getArmor(), ops));
		return root;
	}

	private KitData fromJson(JsonObject root, RegistryOps<JsonElement> ops) {
		String id = root.has("id") ? root.get("id").getAsString() : "kit";
		KitData kit = new KitData(id, root.has("name") ? root.get("name").getAsString() : id);

		if (root.has("icon")) {
			kit.setIcon(stackFromJson(root.get("icon"), ops));
		}
		if (root.has("offhand")) {
			kit.setOffhand(stackFromJson(root.get("offhand"), ops));
		}
		if (root.has("inventory")) {
			stacksFromJson(root.getAsJsonArray("inventory"), kit.getInventory(), ops);
		}
		if (root.has("armor")) {
			stacksFromJson(root.getAsJsonArray("armor"), kit.getArmor(), ops);
		}

		return kit;
	}

	private JsonElement stackToJson(ItemStack stack, RegistryOps<JsonElement> ops) {
		return ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).getOrThrow();
	}

	private ItemStack stackFromJson(JsonElement element, RegistryOps<JsonElement> ops) {
		return ItemStack.OPTIONAL_CODEC.parse(ops, element).getOrThrow();
	}

	private JsonArray stacksToJson(ItemStack[] stacks, RegistryOps<JsonElement> ops) {
		JsonArray array = new JsonArray();
		for (ItemStack stack : stacks) {
			array.add(stackToJson(stack == null ? ItemStack.EMPTY : stack, ops));
		}
		return array;
	}

	private void stacksFromJson(JsonArray array, ItemStack[] target, RegistryOps<JsonElement> ops) {
		for (int i = 0; i < target.length; i++) {
			target[i] = i < array.size() ? stackFromJson(array.get(i), ops) : ItemStack.EMPTY;
		}
	}
}
