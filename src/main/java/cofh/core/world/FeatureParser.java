package cofh.core.world;

import cofh.CoFHCore;
import cofh.core.CoFHProps;
import cofh.core.util.CoreUtils;
import cofh.lib.util.WeightedRandomBlock;
import cofh.lib.util.helpers.MathHelper;
import cofh.lib.world.WorldGenMinableCluster;
import cofh.lib.world.WorldGenSparseMinableCluster;
import cofh.lib.world.feature.FeatureBase;
import cofh.lib.world.feature.FeatureBase.GenRestriction;
import cofh.lib.world.feature.FeatureOreGenNormal;
import cofh.lib.world.feature.FeatureOreGenUniform;
import com.google.common.base.Throwables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.registry.GameRegistry;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.gen.feature.WorldGenerator;

public class FeatureParser {

	private static File worldGenFolder;
	private static File vanillaGen;
	private static final String vanillaGenInternal = "assets/cofh/world/Vanilla.json";
	private static List<WeightedRandomBlock> defaultMaterial;

	private FeatureParser() {

	}

	public static void initialize() {

		worldGenFolder = new File(CoFHProps.configDir, "/cofh/world/");

		if (!worldGenFolder.exists()) {
			try {
				worldGenFolder.mkdir();
			} catch (Throwable t) {
				// pokemon!
			}
		}
		vanillaGen = new File(CoFHProps.configDir, "/cofh/world/Vanilla.json");

		try {
			if (vanillaGen.createNewFile()) {
				CoreUtils.copyFileUsingStream(vanillaGenInternal, vanillaGen);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}

		defaultMaterial = Arrays.asList(new WeightedRandomBlock(new ItemStack(Blocks.stone, 1, 0)));
	}

	private static void addFiles(ArrayList<File> list, File folder) {

		File[] fList = folder.listFiles();

		if (fList == null) {
			CoFHCore.log.error("There are no World Generation files present in " + folder + ".");
			return;
		}
		list.addAll(Arrays.asList(fList));
	}

	public static void parseGenerationFile() {

		JsonParser parser = new JsonParser();

		ArrayList<File> worldGenList = new ArrayList<File>(5);
		addFiles(worldGenList, worldGenFolder);
		for (int i = 0, e = worldGenList.size(); i < e; ++i) {
			File genFile = worldGenList.get(i);
			if (genFile.equals(vanillaGen)) {
				if (!WorldHandler.genReplaceVanilla) {
					worldGenList.remove(i);
				}
				break;
			}
		}

		for (int i = 0; i < worldGenList.size(); ++i) {

			File genFile = worldGenList.get(i);
			if (genFile.isDirectory()) {
				addFiles(worldGenList, genFile);
				continue;
			}

			JsonObject genList;
			try {
				genList = (JsonObject) parser.parse(new FileReader(genFile));
			} catch (Throwable t) {
				CoFHCore.log.error("Critical error reading from a world generation file: " + genFile + " > Please be sure the file is correct!", t);
				continue;
			}

			CoFHCore.log.info("Reading world generation info from: " + genFile + ":");
			for (Entry<String, JsonElement> genEntry : genList.entrySet()) {
				if (parseGenerationEntry(genEntry.getKey(), genEntry.getValue())) {
					CoFHCore.log.debug("Generation entry successfully parsed: \"" + genEntry.getKey() + "\"");
				} else {
					CoFHCore.log.error("Error parsing generation entry: \"" + genEntry.getKey() + "\" > Please check the parameters. It *may* be a duplicate.");
				}
			}
		}
	}

	public static boolean parseGenerationEntry(String featureName, JsonElement genEntry) {

		JsonObject genObject = genEntry.getAsJsonObject();

		Template template = parseTemplate(genObject);
		if (template != null) {
			addGenerationEntry(featureName, genObject, template);
			// TODO: expand parsing for different templates
		}

		return false;
	}

	private static boolean addGenerationEntry(String featureName, JsonObject genObject, Template template) {

		List<WeightedRandomBlock> resList = new ArrayList<WeightedRandomBlock>();

		if (!parseResList(genObject.get("block"), resList)) {
			return false;
		}
		int clusterSize = 0;
		int numClusters = 0;
		boolean retrogen = false;
		GenRestriction biomeRes = GenRestriction.NONE;
		GenRestriction dimRes = GenRestriction.NONE;
		List<WeightedRandomBlock> matList = defaultMaterial;

		if (genObject.has("clusterSize")) {
			clusterSize = genObject.get("clusterSize").getAsInt();
		}
		if (genObject.has("numClusters")) {
			numClusters = genObject.get("numClusters").getAsInt();
		}
		if (clusterSize <= 0 || numClusters <= 0) {
			CoFHCore.log.error("Invalid cluster size or count specified in \"" + featureName + "\"");
			return false;
		}
		if (genObject.has("retrogen")) {
			retrogen = genObject.get("retrogen").getAsBoolean();
		}
		if (genObject.has("biomeRestriction")) {
			String resString = genObject.get("biomeRestriction").getAsString().toLowerCase();

			if (resString.equals("blacklist")) {
				biomeRes = GenRestriction.BLACKLIST;
			}
			if (resString.equals("whitelist")) {
				biomeRes = GenRestriction.WHITELIST;
			}
		}
		if (genObject.has("dimensionRestriction")) {
			String resString = genObject.get("dimensionRestriction").getAsString().toLowerCase();

			if (resString.equals("blacklist")) {
				dimRes = GenRestriction.BLACKLIST;
			}
			if (resString.equals("whitelist")) {
				dimRes = GenRestriction.WHITELIST;
			}
		}
		if (genObject.has("material")) {
			matList = new ArrayList<WeightedRandomBlock>();
			if (!parseResList(genObject.get("material"), matList)) {
				CoFHCore.log.warn("Invalid material list! Using default list.");
				matList = defaultMaterial;
			}
		}
		int minHeight = genObject.get("minHeight").getAsInt();
		int maxHeight = genObject.get("maxHeight").getAsInt();

		if (minHeight >= maxHeight || minHeight < 0) {
			CoFHCore.log.error("Invalid height parameters specified in \"" + featureName + "\"");
			return false;
		}
		FeatureBase feature = template.construct(featureName, resList, clusterSize, matList, numClusters, minHeight, maxHeight, biomeRes, retrogen, dimRes);

		addFeatureRestrictions(feature, genObject);
		return WorldHandler.addFeature(feature);
	}

	public static Block parseBlockName(String blockRaw) {

		String[] blockTokens = blockRaw.split(":", 2);
		int i = 0;
		return GameRegistry.findBlock(blockTokens.length > 1 ? blockTokens[i++] : "minecraft", blockTokens[i]);
	}

	public static WeightedRandomBlock parseBlockEntry(JsonElement genElement) {

		if (genElement.isJsonObject()) {
			JsonObject blockElement = genElement.getAsJsonObject();
			if (!blockElement.has("name")) {
				CoFHCore.log.error("Block entry needs a name!");
				return null;
			}
			Block block = parseBlockName(blockElement.get("name").getAsString());
			if (block == null) {
				CoFHCore.log.error("Invalid block entry!");
				return null;
			}
			int metadata = blockElement.has("metadata") ? MathHelper.clampI(blockElement.get("metadata").getAsInt(), 0, 15) : 0;
			int weight = blockElement.has("weight") ? MathHelper.clampI(blockElement.get("weight").getAsInt(), 1, 1000000) : 100;
			return new WeightedRandomBlock(new ItemStack(block, 1, metadata), weight);
		} else {
			Block block = parseBlockName(genElement.getAsString());
			if (block == null) {
				CoFHCore.log.error("Invalid block entry!");
				return null;
			}
			return new WeightedRandomBlock(new ItemStack(block, 1, 0));
		}
	}

	public static boolean parseResList(JsonElement genElement, List<WeightedRandomBlock> resList) {

		if (genElement.isJsonArray()) {
			JsonArray blockList = genElement.getAsJsonArray();

			for (int i = 0; i < blockList.size(); i++) {
				WeightedRandomBlock entry = parseBlockEntry(blockList.get(i));
				if (entry == null) {
					return false;
				}
				resList.add(entry);
			}
		} else {
			WeightedRandomBlock entry = parseBlockEntry(genElement);
			if (entry == null) {
				return false;
			}
			resList.add(entry);
		}
		return true;
	}

	private static boolean addFeatureRestrictions(FeatureBase feature, JsonObject genObject) {

		if (feature.biomeRestriction != GenRestriction.NONE && genObject.has("biomes")) {
			JsonArray restrictionList = genObject.getAsJsonArray("biomes");
			for (int i = 0; i < restrictionList.size(); i++) {
				feature.addBiome(restrictionList.get(i).getAsString());
			}
		}
		if (feature.dimensionRestriction != GenRestriction.NONE && genObject.has("dimensions")) {
			JsonArray restrictionList = genObject.getAsJsonArray("dimensions");
			for (int i = 0; i < restrictionList.size(); i++) {
				feature.addDimension(restrictionList.get(i).getAsInt());
			}
		}
		return true;
	}

	private static Class<? extends FeatureBase> parseType(String template) {

		// TODO: off-load template types to HashMap<String, Class<? extends FeatureBase>> ?
		// factory-paradigm may be the best option here
		if ("uniform".equals(template)) {
			return FeatureOreGenUniform.class;
		} else if ("normal".equals(template)) {
			return FeatureOreGenNormal.class;
		}
		return null;
	}

	private static Template parseTemplate(JsonObject genObject) {

		JsonElement genElement = genObject.get("template");
		if (genElement.isJsonObject()) {
			genObject = genElement.getAsJsonObject();

			Class<? extends WorldGenerator> gen = null;
			String template = genObject.get("generator").getAsString();
			// TODO: off-load template generators to HashMap<String, Class<? extends WorldGenerator>> ?
			// factory-paradigm may be the best option here
			if ("cluster".equals(template)) {
				gen = WorldGenMinableCluster.class;
			} else if ("sparse-cluster".equals(template)) {
				gen = WorldGenSparseMinableCluster.class;
			} else if ("fractal".equals(template)) {
				// TODO: WorldGenMinableCell
				// gen = WorldGenMinableCell.class;
			}

			template = genObject.get("type").getAsString();
			return new Template(parseType(template), gen);
		} else {
			String template = genElement.getAsString();
			return new Template(parseType(template), WorldGenMinableCluster.class);
		}
	}

	static class Template {

		private final java.lang.reflect.Constructor<? extends FeatureBase> c;
		private final java.lang.reflect.Constructor<? extends WorldGenerator> g;

		Template(Class<? extends FeatureBase> base, Class<? extends WorldGenerator> gen) {

			try {
				Class<?> i = int.class, r = GenRestriction.class;
				c = base.getDeclaredConstructor(String.class, WorldGenerator.class, i, i, i, r, boolean.class, r);
			} catch (Throwable e) {
				CoFHCore.log.error("Invalid template type!");
				throw Throwables.propagate(e);
			}

			try {
				g = gen.getDeclaredConstructor(List.class, int.class, List.class);
			} catch (Throwable e) {
				CoFHCore.log.error("Invalid template generator!");
				throw Throwables.propagate(e);
			}
		}

		public FeatureBase construct(String name, List<WeightedRandomBlock> resources, int clusterSize,
				List<WeightedRandomBlock> material, int count, int meanY, int maxVar, GenRestriction biomeRes,
				boolean regen, GenRestriction dimRes) {

			try {
				WorldGenerator gen = g.newInstance(resources, clusterSize, material);
				return c.newInstance(name, gen, count, meanY, maxVar, biomeRes, regen, dimRes);
			} catch (Throwable e) {
				throw Throwables.propagate(e);
			}
		}

	}

}
