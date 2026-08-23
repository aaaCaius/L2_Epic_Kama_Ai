package com.l2jfrozen.gameserver.economy.datatables;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.l2jfrozen.gameserver.controllers.TradeController;
import com.l2jfrozen.gameserver.datatables.xml.RecipeTable;
import com.l2jfrozen.gameserver.economy.model.CraftDef;
import com.l2jfrozen.gameserver.economy.model.NeedDef;
import com.l2jfrozen.gameserver.economy.model.ShopDef;
import com.l2jfrozen.gameserver.model.L2TradeList;
import com.l2jfrozen.gameserver.model.actor.instance.L2ItemInstance;
import com.l2jfrozen.gameserver.templates.Recipe;

/**
 * Loads <code>data/economy/economy.xml</code>: the needs, the items that satisfy them, and the tier
 * ladder.<BR>
 * <BR>
 * Follows the parsing shape of {@link com.l2jfrozen.gameserver.datatables.xml.RecipeTable} so it reads
 * like the rest of the datapack loaders. Reloadable, because this is content that will be edited far
 * more often than the code around it.
 */
public class EconomyDataTable
{
	private static final Logger LOGGER = Logger.getLogger(EconomyDataTable.class);

	private static final String FILE = "data/economy/economy.xml";
	private static final String SHOPS = "data/economy/shops.xml";
	private static final String INDUSTRY = "data/economy/industry.xml";

	private static EconomyDataTable instance;

	private final List<NeedDef> needs = new ArrayList<>();
	private final Map<Integer, String> tierNames = new HashMap<>();
	private final Map<Integer, List<String>> tierServices = new HashMap<>();

	/** itemId -> the need it serves, built once so lookups on the trade path are cheap. */
	private final Map<Integer, NeedDef> itemToNeed = new HashMap<>();

	/** npcId -> its catalogue. */
	private final Map<Integer, ShopDef> shops = new HashMap<>();

	/** What the town converts raw materials into, in tier order. */
	private final List<CraftDef> crafts = new ArrayList<>();

	/** Every item any economy shop sells - what the town must keep on its shelves. */
	private final Set<Integer> shopItems = new HashSet<>();

	/** productItemId -> a recipe that makes it, so the town can manufacture what it sells. */
	private final Map<Integer, Recipe> recipeByProduct = new HashMap<>();

	public static EconomyDataTable getInstance()
	{
		if (instance == null)
		{
			instance = new EconomyDataTable();
		}

		return instance;
	}

	private EconomyDataTable()
	{
		load();
	}

	public void reload()
	{
		needs.clear();
		tierNames.clear();
		tierServices.clear();
		itemToNeed.clear();
		shops.clear();
		crafts.clear();
		shopItems.clear();
		recipeByProduct.clear();
		load();
	}

	private void load()
	{
		final File file = new File(FILE);

		if (!file.exists())
		{
			LOGGER.warn("EconomyDataTable: " + FILE + " not found - the economy has no needs defined.");
			return;
		}

		try
		{
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder builder = factory.newDocumentBuilder();
			final Document doc = builder.parse(file);
			doc.getDocumentElement().normalize();

			final NodeList needNodes = doc.getElementsByTagName("need");

			for (int i = 0; i < needNodes.getLength(); i++)
			{
				final Node node = needNodes.item(i);
				final NamedNodeMap attr = node.getAttributes();

				final NeedDef need = new NeedDef(attr.getNamedItem("id").getNodeValue(),
					Integer.parseInt(attr.getNamedItem("unlock").getNodeValue()),
					Double.parseDouble(attr.getNamedItem("weight").getNodeValue()),
					Double.parseDouble(attr.getNamedItem("rate").getNodeValue()));

				for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
				{
					if (!"item".equals(child.getNodeName()))
					{
						continue;
					}

					final int itemId = Integer.parseInt(child.getAttributes().getNamedItem("id").getNodeValue());
					need.addItem(itemId);
					itemToNeed.put(Integer.valueOf(itemId), need);
				}

				needs.add(need);
			}

			final NodeList tierNodes = doc.getElementsByTagName("tier");

			for (int i = 0; i < tierNodes.getLength(); i++)
			{
				final NamedNodeMap attr = tierNodes.item(i).getAttributes();
				final int level = Integer.parseInt(attr.getNamedItem("level").getNodeValue());

				tierNames.put(Integer.valueOf(level), attr.getNamedItem("name").getNodeValue());

				final List<String> services = new ArrayList<>();
				final Node svc = attr.getNamedItem("services");

				if (svc != null)
				{
					for (final String s : svc.getNodeValue().split(","))
					{
						if (s.trim().length() > 0)
						{
							services.add(s.trim());
						}
					}
				}

				tierServices.put(Integer.valueOf(level), services);
			}

			loadShops();
			loadIndustry();
			indexRecipes();

			LOGGER.info("EconomyDataTable: loaded " + needs.size() + " needs over " + itemToNeed.size() + " items, "
				+ tierNames.size() + " tiers, " + shops.size() + " shop(s) selling " + shopItems.size() + " item(s), "
				+ crafts.size() + " craft(s), " + recipeByProduct.size() + " recipe-made good(s).");
		}
		catch (final Exception e)
		{
			LOGGER.error("EconomyDataTable: failed to parse " + FILE, e);
		}
	}

	private void loadShops()
	{
		final File file = new File(SHOPS);

		if (!file.exists())
		{
			return;
		}

		try
		{
			final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
			doc.getDocumentElement().normalize();

			final NodeList list = doc.getElementsByTagName("shop");

			for (int i = 0; i < list.getLength(); i++)
			{
				final Node node = list.item(i);
				final NamedNodeMap attr = node.getAttributes();
				final int npcId = Integer.parseInt(attr.getNamedItem("npc").getNodeValue());
				final Node title = attr.getNamedItem("title");

				final ShopDef shop = new ShopDef(npcId, title == null ? "Shop" : title.getNodeValue());
				final Node inherit = attr.getNamedItem("inherit");

				// inherit="true" takes the catalogue from the shop this NPC already runs. That is the
				// point: an existing merchant keeps selling exactly what it always sold, and only the
				// quantity becomes real. No catalogue anywhere needs re-authoring.
				if (inherit != null && Boolean.parseBoolean(inherit.getNodeValue()))
				{
					inheritCatalogue(shop, npcId);
				}

				for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
				{
					if (!"item".equals(child.getNodeName()))
					{
						continue;
					}

					final NamedNodeMap ia = child.getAttributes();
					final Node ess = ia.getNamedItem("essential");
					shop.add(Integer.parseInt(ia.getNamedItem("id").getNodeValue()),
						ess != null && Boolean.parseBoolean(ess.getNodeValue()));
				}

				shopItems.addAll(shop.getItemIds());
				shops.put(Integer.valueOf(npcId), shop);
			}
		}
		catch (final Exception e)
		{
			LOGGER.error("EconomyDataTable: failed to parse " + SHOPS, e);
		}
	}

	private void loadIndustry()
	{
		final File file = new File(INDUSTRY);

		if (!file.exists())
		{
			return;
		}

		try
		{
			final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
			doc.getDocumentElement().normalize();

			final NodeList list = doc.getElementsByTagName("craft");

			for (int i = 0; i < list.getLength(); i++)
			{
				final Node node = list.item(i);
				final NamedNodeMap attr = node.getAttributes();

				final CraftDef craft = new CraftDef(attr.getNamedItem("id").getNodeValue(),
					Integer.parseInt(attr.getNamedItem("tier").getNodeValue()),
					Integer.parseInt(attr.getNamedItem("capacity").getNodeValue()));

				for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
				{
					final NamedNodeMap ca = child.getAttributes();

					if ("input".equals(child.getNodeName()))
					{
						craft.addInput(Integer.parseInt(ca.getNamedItem("id").getNodeValue()),
							Integer.parseInt(ca.getNamedItem("count").getNodeValue()));
					}
					else if ("output".equals(child.getNodeName()))
					{
						craft.setOutput(Integer.parseInt(ca.getNamedItem("id").getNodeValue()),
							Integer.parseInt(ca.getNamedItem("count").getNodeValue()));
					}
				}

				if (craft.getOutput() != null)
				{
					crafts.add(craft);
				}
			}
		}
		catch (final Exception e)
		{
			LOGGER.error("EconomyDataTable: failed to parse " + INDUSTRY, e);
		}
	}

	/** Pull an NPC's existing SQL buylist in as its economy catalogue. */
	private static void inheritCatalogue(final ShopDef shop, final int npcId)
	{
		try
		{
			final List<L2TradeList> lists = TradeController.getInstance().getBuyListByNpcId(npcId);

			if (lists == null)
			{
				return;
			}

			for (final L2TradeList list : lists)
			{
				for (final L2ItemInstance item : list.getItems())
				{
					shop.add(item.getItemId(), false);
				}
			}
		}
		catch (final Exception e)
		{
			LOGGER.warn("EconomyDataTable: could not inherit the catalogue for npc " + npcId, e);
		}
	}

	/**
	 * Index the datapack's recipes by what they produce.<BR>
	 * <BR>
	 * This is where a town's ability to manufacture comes from. There are already 870 recipes defining
	 * materials to finished goods, so nothing needs authoring per item - if the town holds the materials
	 * and the shop sells the product, the town can make it.
	 */
	private void indexRecipes()
	{
		try
		{
			final Collection<Recipe> all = RecipeTable.getInstance().getAllRecipes();

			if (all == null)
			{
				return;
			}

			for (final Recipe r : all)
			{
				if (r == null || r.getProductItemId() <= 0 || r.getMaterials() == null || r.getMaterials().isEmpty())
				{
					continue;
				}

				// Only index what a shop actually sells - the rest is noise the town never needs.
				if (!shopItems.contains(Integer.valueOf(r.getProductItemId())))
				{
					continue;
				}

				final Recipe existing = recipeByProduct.get(Integer.valueOf(r.getProductItemId()));

				// Prefer the simplest recipe: fewest distinct materials, then lowest level.
				if (existing == null
					|| r.getMaterials().size() < existing.getMaterials().size()
					|| (r.getMaterials().size() == existing.getMaterials().size() && r.getLevel() < existing.getLevel()))
				{
					recipeByProduct.put(Integer.valueOf(r.getProductItemId()), r);
				}
			}
		}
		catch (final Exception e)
		{
			LOGGER.warn("EconomyDataTable: could not index recipes", e);
		}
	}

	public Set<Integer> getShopItems()
	{
		return shopItems;
	}

	/**
	 * @param  itemId a good a shop sells
	 * @return        a recipe the town can use to make it, or null if it must be imported instead
	 */
	public Recipe getRecipeFor(final int itemId)
	{
		return recipeByProduct.get(Integer.valueOf(itemId));
	}

	public ShopDef getShop(final int npcId)
	{
		return shops.get(Integer.valueOf(npcId));
	}

	/** @return the conversions a town at this tier can run */
	public List<CraftDef> getCraftsUpTo(final int tier)
	{
		final List<CraftDef> out = new ArrayList<>();

		for (final CraftDef c : crafts)
		{
			if (c.getTier() <= tier)
			{
				out.add(c);
			}
		}

		return out;
	}

	public List<NeedDef> getNeeds()
	{
		return needs;
	}

	/** @return the needs a town at this tier actually consumes */
	public List<NeedDef> getNeedsUpTo(final int tier)
	{
		final List<NeedDef> out = new ArrayList<>();

		for (final NeedDef n : needs)
		{
			if (n.getUnlockTier() <= tier)
			{
				out.add(n);
			}
		}

		return out;
	}

	/**
	 * @param  itemId any item
	 * @return        the need it serves, or null if the economy does not care about it
	 */
	public NeedDef getNeedForItem(final int itemId)
	{
		return itemToNeed.get(Integer.valueOf(itemId));
	}

	public String getTierName(final int tier)
	{
		final String name = tierNames.get(Integer.valueOf(tier));
		return name == null ? "Tier " + tier : name;
	}

	public List<String> getServices(final int tier)
	{
		final List<String> s = tierServices.get(Integer.valueOf(tier));

		if (s == null)
		{
			return new ArrayList<>();
		}

		return s;
	}

	public boolean hasService(final int tier, final String service)
	{
		return getServices(tier).contains(service);
	}
}
