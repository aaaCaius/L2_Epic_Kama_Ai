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

import com.l2jfrozen.gameserver.economy.model.NeedDef;

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

	private static EconomyDataTable instance;

	private final List<NeedDef> needs = new ArrayList<>();
	private final Map<Integer, String> tierNames = new HashMap<>();
	private final Map<Integer, List<String>> tierServices = new HashMap<>();

	/** itemId -> the need it serves, built once so lookups on the trade path are cheap. */
	private final Map<Integer, NeedDef> itemToNeed = new HashMap<>();

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

			LOGGER.info("EconomyDataTable: loaded " + needs.size() + " needs over " + itemToNeed.size() + " items, " + tierNames.size() + " tiers.");
		}
		catch (final Exception e)
		{
			LOGGER.error("EconomyDataTable: failed to parse " + FILE, e);
		}
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
