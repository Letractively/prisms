/*
 * WmsCapabilities.java Created Jul 27, 2011 by Andrew Butler, PSL
 */
package prisms.arch.wms;

import java.util.List;

import org.dom4j.Element;

import prisms.util.ArrayUtils;

/** A WMS_Capabilities document is returned in response to a GetCapabilities request made on a WMS. */
public class WmsCapabilities
{
	/** Information about a contact person for the service. */
	public static class Contact
	{
		private String theName;

		private String theOrg;

		private Address theAddress;

		private String theVoicePhone;

		private String theFaxPhone;

		private String theEmail;

		/**
		 * Creates a contact
		 * 
		 * @param name The name of the contact
		 * @param org The name of the organization the contact belongs to
		 */
		public Contact(String name, String org)
		{
			theName = name;
			theOrg = org;
		}

		/** @return This contact's name */
		public String getName()
		{
			return theName;
		}

		/** @param name The name for this contact */
		public void setName(String name)
		{
			theName = name;
		}

		/** @return This contact's organization */
		public String getOrganization()
		{
			return theOrg;
		}

		/** @param org The organization for this contact */
		public void setOrganization(String org)
		{
			theOrg = org;
		}

		/** @return This contact's address */
		public Address getAddress()
		{
			return theAddress;
		}

		/** @param address The address for this contact */
		public void setAddress(Address address)
		{
			theAddress = address;
		}

		/** @return This contact's phone number */
		public String getVoicePhone()
		{
			return theVoicePhone;
		}

		/** @param vp The phone number for this contact */
		public void setVoicePhone(String vp)
		{
			theVoicePhone = vp;
		}

		/** @return This contact's fax number */
		public String getFaxPhone()
		{
			return theFaxPhone;
		}

		/** @param fp The fax number for this contact */
		public void setFaxPhone(String fp)
		{
			theFaxPhone = fp;
		}

		/** @return This contact's email address */
		public String getEmail()
		{
			return theEmail;
		}

		/** @param email The email address for this contact */
		public void setEmail(String email)
		{
			theEmail = email;
		}

		/**
		 * Prints this contact to capability request output
		 * 
		 * @param ret The string builder to print this contact to
		 * @param indent The number of places to indent this item's output
		 */
		public void toXML(StringBuilder ret, int indent)
		{
			StringBuilder indentStr = new StringBuilder();
			for(int i = 0; i < indent; i++)
				indentStr.append('\t');
			ret.append(indentStr).append("<ContactInformation>\n");
			if(theName != null)
			{
				ret.append(indentStr).append("\t<ContactPersonPrimary>\n");
				ret.append(indentStr).append("\t\t<ContactPerson>").append(escape(theName))
					.append("</ContactPerson>\n");
				ret.append(indentStr).append("\t\t<ContactOrganization>").append(escape(theOrg))
					.append("</ContactOrganization>\n");
				ret.append(indentStr).append("\t</ContactPersonPrimary>\n");
			}
			if(theAddress != null)
				theAddress.toXML(ret, indent + 1);
			if(theVoicePhone != null)
				ret.append(indentStr).append("\t<ContactVoiceTelephone>")
					.append(escape(theVoicePhone)).append("</ContactVoiceTelephone>\n");
			if(theFaxPhone != null)
				ret.append(indentStr).append("\t<ContactFacsimileTelephone>")
					.append(escape(theFaxPhone)).append("</ContactFacsimileTelephone>\n");
			if(theEmail != null)
				ret.append(indentStr).append("\t<ContactElectronicMailAddress>")
					.append(escape(theEmail)).append("</ContactElectronicMailAddress>\n");
		}

		/**
		 * Fills this contact's information with the ContactPerson element from a WMS Capabilities
		 * XML document
		 * 
		 * @param el The ContactPerson element from a WMS Capabilities XML document
		 */
		public void parse(Element el)
		{
			Element person = el.element("ContactPersonPrimary");
			theName = person.elementTextTrim("ContactPerson");
			theOrg = person.elementTextTrim("ContactOrganization");
			Element address = el.element("ContactAddress");
			if(address == null)
				theAddress = null;
			else
			{
				if(theAddress == null)
					theAddress = new Address();
				theAddress.parse(address);
			}
			if(person.element("ContactVoiceTelephone") == null)
				theVoicePhone = null;
			else
				theVoicePhone = person.elementTextTrim("ContactVoiceTelephone");
			if(person.element("ContactFacsimileTelephone") == null)
				theFaxPhone = null;
			else
				theFaxPhone = person.elementTextTrim("ContactFacsimileTelephone");
			if(person.element("ContactElectronicMailAddress") == null)
				theEmail = null;
			else
				theEmail = person.elementTextTrim("ContactElectronicMailAddress");
		}
	}

	/** Information on a contact person's address */
	public static class Address
	{
		private String theType;

		private String theAddress;

		private String theCity;

		private String theState;

		private String thePostCode;

		private String theCountry;

		/** @return The type of address this is */
		public String getType()
		{
			return theType;
		}

		/** @param type The type for this address */
		public void setType(String type)
		{
			theType = type;
		}

		/** @return The street address */
		public String getAddress()
		{
			return theAddress;
		}

		/** @param address The street address */
		public void setAddress(String address)
		{
			theAddress = address;
		}

		/** @return This address's city */
		public String getCity()
		{
			return theCity;
		}

		/** @param city The city for this address */
		public void setCity(String city)
		{
			theCity = city;
		}

		/** @return This address's state */
		public String getState()
		{
			return theState;
		}

		/** @param state The state for this address */
		public void setState(String state)
		{
			theState = state;
		}

		/** @return This address's postal code */
		public String getPostCode()
		{
			return thePostCode;
		}

		/** @param postCode The postal code for this address */
		public void setPostCode(String postCode)
		{
			thePostCode = postCode;
		}

		/** @return This address's country */
		public String getCountry()
		{
			return theCountry;
		}

		/** @param country The country for this address */
		public void setCountry(String country)
		{
			theCountry = country;
		}

		/**
		 * Prints this address to capability request output
		 * 
		 * @param ret The string builder to print this contact to
		 * @param indent The number of places to indent this item's output
		 */
		public void toXML(StringBuilder ret, int indent)
		{
			StringBuilder indentStr = new StringBuilder();
			for(int i = 0; i < indent; i++)
				indentStr.append('\t');
			ret.append(indentStr).append("<ContactAddress>\n");
			ret.append(indentStr).append("\t<AddressType>").append(escape(theType))
				.append("</AddressType>\n");
			ret.append(indentStr).append("\t<Address>").append(escape(theAddress))
				.append("</Address>\n");
			ret.append(indentStr).append("\t<City>").append(escape(theCity)).append("</City>\n");
			ret.append(indentStr).append("\t<StateOrProvince>").append(escape(theState))
				.append("</StateOrProvince>\n");
			ret.append(indentStr).append("\t<PostalCode>").append(escape(thePostCode))
				.append("</PostalCode>\n");
			ret.append(indentStr).append("\t<Country>").append(escape(theCountry))
				.append("</Country>\n");
			ret.append(indentStr).append("</ContactAddress>\n");
		}

		/**
		 * Fills this address's information with the ContactAddress element from a WMS Capabilities
		 * XML document
		 * 
		 * @param el The ContactAddress element from a WMS Capabilities XML document
		 */
		public void parse(Element el)
		{
			theType = el.elementTextTrim("AddressType");
			theAddress = el.elementTextTrim("Address");
			theCity = el.elementTextTrim("City");
			theState = el.elementTextTrim("StateOrProvince");
			thePostCode = el.elementTextTrim("PostalCode");
			theCountry = el.elementTextTrim("Country");
		}
	}

	/** Represents a type of operation a WMS server may perform */
	public static class OperationType
	{
		private final String theName;

		private String [] theFormats;

		private String [] theLocations;

		private String [] thePosts;

		/**
		 * Creates an operation
		 * 
		 * @param name The name of the operation
		 */
		public OperationType(String name)
		{
			theName = name;
			theFormats = new String [0];
			theLocations = new String [0];
			thePosts = new String [0];
		}

		/** @return This operation's name */
		public String getName()
		{
			return theName;
		}

		/** @return The formats available for this operation */
		public String [] getFormats()
		{
			return theFormats;
		}

		/** @param format A new format that this operation can support */
		public void addFormat(String format)
		{
			theFormats = ArrayUtils.add(theFormats, format);
		}

		/** @param format The format that this operation cannot support */
		public void removeFormat(String format)
		{
			theFormats = ArrayUtils.remove(theFormats, format);
		}

		/** @return The distributed computing platform locations available for this service */
		public String [] getDCPLocations()
		{
			return theLocations;
		}

		/**
		 * @return The distributed computing platform locations available by the HTTP-POST method
		 *         for this service. The length of this array is the same as
		 *         {@link #getDCPLocations()}, but some elements may be null.
		 */
		public String [] getDCPPosts()
		{
			return thePosts;
		}

		/**
		 * Adds a distributed computing platform location for this operation
		 * 
		 * @param get The URL prefix for the service operation
		 * @param post The URL prefix for the service operation using the POST method--may be null
		 */
		public void addDCPLocation(String get, String post)
		{
			theLocations = ArrayUtils.add(theLocations, get);
			thePosts = ArrayUtils.add(thePosts, post);
		}

		/**
		 * Removes a distributed computing platform location from this operation
		 * 
		 * @param get The URL prefix for the service operation (the HTTP-GET form)
		 */
		public void removeDCPLocation(String get)
		{
			int idx = ArrayUtils.indexOf(theLocations, get);
			if(idx < 0)
				return;
			theLocations = ArrayUtils.remove(theLocations, idx);
			thePosts = ArrayUtils.remove(theLocations, idx);
		}

		/** Clears out this operation's content */
		public void clear()
		{
			theFormats = new String [0];
			theLocations = new String [0];
			thePosts = new String [0];
		}

		/**
		 * Prints this operation to capability request output
		 * 
		 * @param ret The string builder to print this contact to
		 * @param indent The number of places to indent this item's output
		 */
		public void toXML(StringBuilder ret, int indent)
		{
			if(theFormats.length == 0)
				return;
			StringBuilder indentStr = new StringBuilder();
			for(int i = 0; i < indent; i++)
				indentStr.append('\t');
			ret.append(indentStr).append("<").append(escape(theName)).append(">\n");
			for(String f : theFormats)
				ret.append(indentStr).append("\t<Format>").append(escape(f)).append("</Format>\n");
			for(int i = 0; i < theLocations.length; i++)
			{
				ret.append(indentStr).append("\t<DCPType><HTTP>\n");
				ret.append(indentStr).append("\t\t<Get><OnlineResource")
					.append(" xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
					.append(" xlink:type=\"simple\" xlink:href=\"").append(escape(theLocations[i]))
					.append("\"/></Get>");
				if(thePosts[i] != null)
					ret.append(indentStr).append("\t\t<Post><OnlineResource")
						.append(" xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
						.append(" xlink:type=\"simple\" xlink:href=\"").append(escape(thePosts[i]))
						.append("\"/></Post>");
				ret.append(indentStr).append("\t</HTTP></DCPType>\n");
			}
			ret.append(indentStr).append("</").append(escape(theName)).append(">\n");
		}

		/**
		 * Fills this operation's information with an operation element from a WMS Capabilities XML
		 * document
		 * 
		 * @param el An operation element from a WMS Capabilities XML document
		 */
		public void parse(Element el)
		{
			List<Element> fels = el.elements("Format");
			theFormats = new String [fels.size()];
			for(int f = 0; f < theFormats.length; f++)
				theFormats[f] = fels.get(f).getTextTrim();

			List<Element> dcps = el.elements("DCPType");
			theLocations = new String [dcps.size()];
			thePosts = new String [dcps.size()];
			for(int i = 0; i < theLocations.length; i++)
			{
				theLocations[i] = dcps.get(i).element("HTTP").element("GET").attributeValue("href");
				Element post = dcps.get(i).element("HTTP").element("POST");
				if(post != null)
					thePosts[i] = post.attributeValue("href");
			}
		}
	}

	/** Nested list of zero or more map Layers offered by a server */
	public static class Layer
	{
		private String theTitle;

		private boolean isQueryable;

		private boolean isOpaque;

		private boolean isNoSubsets;

		private int theCascaded;

		private int theFixedWidth;

		private int theFixedHeight;

		private String theName;

		private String theAbstract;

		private String [] theKeywords;

		private String [] theCRSs;

		private BoundingBox theGeoBounds;

		private CrsBoundBox [] theCrsBounds;

		private Attribution [] theAttributions;

		private Layer [] theLayers;

		/** @return This layer's title. This value is displayable to a user. */
		public String getTitle()
		{
			return theTitle;
		}

		/** @param title The title for this layer. This value should be displayable to a user. */
		public void setTitle(String title)
		{
			theTitle = title;
		}

		/** @return Whether this layer can be queried for overlays */
		public boolean isQueryable()
		{
			return isQueryable;
		}

		/** @param queryable Whether this layer can be queried for overlays */
		public void setQueryable(boolean queryable)
		{
			this.isQueryable = queryable;
		}

		/** @return Whether this layer is opaque or not */
		public boolean isOpaque()
		{
			return isOpaque;
		}

		/** @param opaque Whether this layer is opaque or not */
		public void setOpaque(boolean opaque)
		{
			this.isOpaque = opaque;
		}

		/** @return The no subsets parameter for this layer */
		public boolean isNoSubsets()
		{
			return isNoSubsets;
		}

		/** @param noSubsets The no subsets parameter for this layer */
		public void setNoSubsets(boolean noSubsets)
		{
			this.isNoSubsets = noSubsets;
		}

		/** @return The cascaded parameter for this layer */
		public int getCascaded()
		{
			return theCascaded;
		}

		/** @param cascaded The cascaded parameter for this layer */
		public void setCascaded(int cascaded)
		{
			theCascaded = cascaded;
		}

		/** @return The fixed width for this layer's image, or 0 if the width is not fixed */
		public int getFixedWidth()
		{
			return theFixedWidth;
		}

		/** @param width The fixed width for this layer's image, or 0 if the width is not fixed */
		public void setFixedWidth(int width)
		{
			theFixedWidth = width;
		}

		/** @return The fixed height for this layer's image, or 0 if the height is not fixed */
		public int getFixedHeight()
		{
			return theFixedHeight;
		}

		/** @param height The fixed height for this layer's image, or 0 if the height is not fixed */
		public void setFixedHeight(int height)
		{
			theFixedHeight = height;
		}

		/**
		 * @return This layer's name. This value is more of an ID and should not generally be
		 *         displayed to a user.
		 */
		public String getName()
		{
			return theName;
		}

		/**
		 * @param name The name for this layer. This value is more of an ID and will not generally
		 *        be displayed to a user.
		 */
		public void setName(String name)
		{
			theName = name;
		}

		/**
		 * @return This layer's abstract. This is a description provided by the vendor to describe
		 *         the content or functionality in this layer.
		 */
		public String getAbstract()
		{
			return theAbstract;
		}

		/**
		 * @param anAbstract The abstract for this layer. This is a description provided by the
		 *        vendor to describe the content or functionality in this layer.
		 */
		public void setAbstract(String anAbstract)
		{
			theAbstract = anAbstract;
		}

		/** @return Describes the geographical extent of this layer's content */
		public BoundingBox getGeoBounds()
		{
			return theGeoBounds;
		}

		/** @param geoBounds The geographical extent for this layer's content */
		public void setGeoBounds(BoundingBox geoBounds)
		{
			theGeoBounds = geoBounds;
		}

		/** @return Keywords chosen by the vendor to make this layer easier to find */
		public String [] getKeywords()
		{
			return theKeywords;
		}

		/** @param keyword The keyword to add to this layer */
		public void addKeyword(String keyword)
		{
			if(!ArrayUtils.contains(theKeywords, keyword))
				theKeywords = ArrayUtils.add(theKeywords, keyword);
		}

		/** @param keyword The keyword to remove this layer */
		public void removeKeyword(String keyword)
		{
			theKeywords = ArrayUtils.remove(theKeywords, keyword);
		}

		/** @return All coordinate reference systems (CRSs) that this layer can be displayed in */
		public String [] getCRSs()
		{
			return theCRSs;
		}

		/** @param crs The coordinate reference system to add to this layer */
		public void addCRS(String crs)
		{
			if(!ArrayUtils.contains(theCRSs, crs))
				theCRSs = ArrayUtils.add(theCRSs, crs);
		}

		/** @param crs The coordinate reference system to remove from this layer */
		public void removeCRS(String crs)
		{
			theCRSs = ArrayUtils.remove(theCRSs, crs);
		}

		/** @return The CRS-specific bounding boxes of this layer */
		public CrsBoundBox [] getCrsBounds()
		{
			return theCrsBounds;
		}

		/** @param bounds The CRS-specific boundary to add to this layer */
		public void addCrsBounds(CrsBoundBox bounds)
		{
			theCrsBounds = ArrayUtils.add(theCrsBounds, bounds);
		}

		/** @param bounds The CRS-specific boundary to remove from this layer */
		public void removeCrsBounds(CrsBoundBox bounds)
		{
			theCrsBounds = ArrayUtils.remove(theCrsBounds, bounds);
		}

		/** @return All attributions of this layer */
		public Attribution [] getAttributions()
		{
			return theAttributions;
		}

		/** @param att The attribution to add to this layer */
		public void addAttribution(Attribution att)
		{
			theAttributions = ArrayUtils.add(theAttributions, att);
		}

		/** @param att The attribution to remove from this layer */
		public void removeAttribution(Attribution att)
		{
			theAttributions = ArrayUtils.remove(theAttributions, att);
		}

		/** @return All layers nested into this layer */
		public Layer [] getLayers()
		{
			return theLayers;
		}

		/** @param layer The layer to nest inside this layer */
		public void addLayer(Layer layer)
		{
			theLayers = ArrayUtils.add(theLayers, layer);
		}

		/** @param layer The layer to remove from this layer's nested layers */
		public void removeLayer(Layer layer)
		{
			theLayers = ArrayUtils.remove(theLayers, layer);
		}

		/** Creates a layer */
		public Layer()
		{
			theKeywords = new String [0];
			theCRSs = new String [0];
			theCrsBounds = new CrsBoundBox [0];
			theAttributions = new Attribution [0];
			theLayers = new Layer [0];
		}

		/**
		 * Prints this layer to capability request output
		 * 
		 * @param ret The string builder to print this contact to
		 * @param indent The number of places to indent this item's output
		 */
		public void toXML(StringBuilder ret, int indent)
		{
			StringBuilder indentStr = new StringBuilder();
			for(int i = 0; i < indent; i++)
				indentStr.append('\t');
			ret.append(indentStr).append("<Layer");
			ret.append(" queryable=\"").append(isQueryable ? 1 : 0).append('"');
			ret.append(" opaque=\"").append(isOpaque ? 1 : 0).append('"');
			ret.append("noSubsets=\"").append(isNoSubsets ? 1 : 0).append('"');
			if(theCascaded > 0)
				ret.append(" cascaded=\"").append(theCascaded).append('"');
			if(theFixedWidth > 0)
				ret.append(" fixedWidth=\"").append(theFixedWidth).append('"');
			if(theFixedHeight > 0)
				ret.append(" fixedHeight=\"").append(theFixedHeight).append('"');
			ret.append(">");
			ret.append(indentStr).append("\t<Title>").append(escape(theTitle)).append("</Title>\n");
			if(theName != null)
				ret.append(indentStr).append("\t<Name>").append(escape(theName))
					.append("</Name>\n");
			if(theAbstract != null)
				ret.append(indentStr).append("\t<Abstract>").append(escape(theAbstract))
					.append("</Abstract>\n");
			if(theKeywords.length > 0)
			{
				ret.append(indentStr).append("\t<KeywordList>\n");
				for(String keyword : theKeywords)
					ret.append(indentStr).append("\t\t<Keyword>").append(escape(keyword))
						.append("</Keyword>\n");
				ret.append(indentStr).append("\t</KeywordList>\n");
			}
			for(String crs : theCRSs)
				ret.append(indentStr).append("\t<CRS>").append(escape(crs)).append("</CRS>\n");
			if(theGeoBounds != null)
			{
				ret.append(indentStr).append("\t<EX_GeographicBoundingBox>\n");
				ret.append(indentStr).append("\t\t<westBoundLongitude>")
					.append(theGeoBounds.minLon).append("</westBoundLongitude>\n");
				ret.append(indentStr).append("\t\t<eastBoundLongitude>")
					.append(theGeoBounds.maxLon).append("</eastBoundLongitude>\n");
				ret.append(indentStr).append("\t\t<southBoundLatitude>")
					.append(theGeoBounds.minLat).append("</southBoundLatitude>\n");
				ret.append(indentStr).append("\t\t<northBoundLatitude>")
					.append(theGeoBounds.maxLat).append("</northBoundLatitude>\n");
				ret.append(indentStr).append("\t</EX_GeographicBoundingBox>\n");
			}
			for(CrsBoundBox box : theCrsBounds)
				box.toXML(ret, indent + 1);
			for(Attribution att : theAttributions)
				att.toXML(ret, indent + 1);
			for(Layer layer : theLayers)
				layer.toXML(ret, indent + 1);
			ret.append(indentStr).append("</Layer>");
		}

		/**
		 * Fills this layer's information with a Layer element from a WMS Capabilities XML document
		 * 
		 * @param el A Layer element from a WMS Capabilities XML document
		 */
		public void parse(Element el)
		{
			theTitle = el.elementTextTrim("Title");
			theName = el.elementTextTrim("Name");
			theAbstract = el.elementTextTrim("Abstract");
			isQueryable = "1".equals(el.attributeValue("queryable"));
			isOpaque = "1".equals(el.attributeValue("opaque"));
			isNoSubsets = "1".equals(el.attributeValue("noSubsets"));
			theCascaded = el.attributeValue("cascaded") == null ? 0 : Integer.parseInt(el
				.attributeValue("cascaded"));
			theFixedWidth = el.attributeValue("fixedWidth") == null ? 0 : Integer.parseInt(el
				.attributeValue("fixedWidth"));
			theFixedHeight = el.attributeValue("fixedHeight") == null ? 0 : Integer.parseInt(el
				.attributeValue("fixedHeight"));
			Element keywordsEl = el.element("KeywordList");
			if(keywordsEl != null)
			{
				List<String> keywords = new java.util.ArrayList<String>();
				for(Element ke : (List<Element>) keywordsEl.elements("Keyword"))
					keywords.add(ke.getTextTrim());
			}
			Element geoBounds = el.element("wms:EX_GeographicBoundingBox");
			if(geoBounds == null)
				theGeoBounds = null;
			else
				theGeoBounds = new BoundingBox(Float.parseFloat(geoBounds
					.elementTextTrim("southBoundLatitude")), Float.parseFloat(geoBounds
					.elementTextTrim("northBoundLatitude")), Float.parseFloat(geoBounds
					.elementTextTrim("westBoundLongitude")), Float.parseFloat(geoBounds
					.elementTextTrim("eastBoundLongitude")));
			List<Element> crsBoundEls = el.elements("BoundingBox");
			theCrsBounds = new CrsBoundBox [crsBoundEls.size()];
			for(int i = 0; i < theCrsBounds.length; i++)
				theCrsBounds[i] = CrsBoundBox.parse(crsBoundEls.get(i));
			theAttributions = ArrayUtils.adjust(theAttributions,
				((List<Element>) el.elements("Attribution")).toArray(new Element [0]),
				new ArrayUtils.DifferenceListener<Attribution, Element>()
				{
					public boolean identity(Attribution o1, Element o2)
					{
						return o1.getTitle() == null ? o2.elementTextTrim("Title") == null : o1
							.getTitle().equals(o2.elementTextTrim("Title"));
					}

					public Attribution added(Element o, int mIdx, int retIdx)
					{
						Attribution ret = new Attribution();
						ret.parse(o);
						return ret;
					}

					public Attribution removed(Attribution o, int oIdx, int incMod, int retIdx)
					{
						return null;
					}

					public Attribution set(Attribution o1, int idx1, int incMod, Element o2,
						int idx2, int retIdx)
					{
						o1.parse(o2);
						return o1;
					}
				});
			theLayers = ArrayUtils.adjust(theLayers,
				((List<Element>) el.elements("Layer")).toArray(new Element [0]),
				new ArrayUtils.DifferenceListener<Layer, Element>()
				{
					public boolean identity(Layer o1, Element o2)
					{
						return o1.getTitle().equals(o2.elementTextTrim("Title"));
					}

					public Layer added(Element o, int mIdx, int retIdx)
					{
						Layer ret = new Layer();
						ret.parse(o);
						return ret;
					}

					public Layer removed(Layer o, int oIdx, int incMod, int retIdx)
					{
						return null;
					}

					public Layer set(Layer o1, int idx1, int incMod, Element o2, int idx2,
						int retIdx)
					{
						o1.parse(o2);
						return o1;
					}
				});
		}
	}

	/**
	 * The BoundingBox attributes indicate the limits of the bounding box in units of the specified
	 * coordinate reference system.
	 */
	public static class CrsBoundBox
	{
		/** The coordinate system that this bounding box is for */
		public final String crs;

		/** The minimum x-value that the layer supports */
		public final double minX;

		/** The minimum y-value that the layer supports */
		public final double minY;

		/** The maximum x-value that the layer supports */
		public final double maxX;

		/** The maximum y-value that the layer supports */
		public final double maxY;

		/** The maximumn x-resolution that the layer supports */
		public final double resX;

		/** The maximum y-resolution that the layer supports */
		public final double resY;

		/**
		 * Creates a bounding box
		 * 
		 * @param _crs The coordinate system that this bounding box is for
		 * @param _minX The minimum x-value that the layer supports
		 * @param _minY The minimum y-value that the layer supports
		 * @param _maxX The maximum x-value that the layer supports
		 * @param _maxY The maximum y-value that the layer supports
		 * @param _resX The maximumn x-resolution that the layer supports
		 * @param _resY The maximum y-resolution that the layer supports
		 */
		public CrsBoundBox(String _crs, double _minX, double _minY, double _maxX, double _maxY,
			double _resX, double _resY)
		{
			crs = _crs;
			minX = _minX;
			minY = _minY;
			maxX = _maxX;
			maxY = _maxY;
			resX = _resX;
			resY = _resY;
		}

		/**
		 * Prints this bounding box to capability request output
		 * 
		 * @param ret The string builder to print this contact to
		 * @param indent The number of places to indent this item's output
		 */
		public void toXML(StringBuilder ret, int indent)
		{
			StringBuilder indentStr = new StringBuilder();
			for(int i = 0; i < indent; i++)
				indentStr.append('\t');
			ret.append(indentStr).append("<BoundingBox");
			ret.append(" CRS=\"").append(escape(crs)).append('"');
			ret.append(" minx=\"").append(minX).append('"');
			ret.append(" miny=\"").append(minX).append('"');
			ret.append(" maxx=\"").append(minX).append('"');
			ret.append(" maxy=\"").append(minX).append('"');
			if(!Double.isNaN(resX))
				ret.append(" resx=\"").append(minX).append('"');
			if(!Double.isNaN(resY))
				ret.append(" resy=\"").append(minX).append('"');
			ret.append(" />\n");
		}

		/**
		 * Parses a coordinate reference system (CRS)-specific bounding box from a BoundingBox
		 * element in a Layer element of a WMS Capabilities document
		 * 
		 * @param el A BoundingBox element in a Layer element of a WMS capabilities document
		 * @return The bounding box represented by the element
		 */
		public static CrsBoundBox parse(Element el)
		{
			String srs = el.attributeValue("CRS");
			double minX = Double.parseDouble(el.attributeValue("minx"));
			double minY = Double.parseDouble(el.attributeValue("miny"));
			double maxX = Double.parseDouble(el.attributeValue("maxx"));
			double maxY = Double.parseDouble(el.attributeValue("maxy"));
			double resX = Double.NaN;
			if(el.attributeValue("resx") != null)
				resX = Double.parseDouble(el.attributeValue("minx"));
			double resY = Double.NaN;
			if(el.attributeValue("resy") != null)
				resY = Double.parseDouble(el.attributeValue("minx"));
			return new CrsBoundBox(srs, minX, minY, maxX, maxY, resX, resY);
		}
	}

	/**
	 * Attribution indicates the provider of a Layer or collection of Layers. The provider's URL,
	 * descriptive title string, and/or logo image URL may be supplied. Client applications may
	 * choose to display one or more of these items. A format element indicates the MIME type of the
	 * logo image located at LogoURL. The logo image's width and height assist client applications
	 * in laying out space to display the logo.
	 */
	public static class Attribution
	{
		private String theTitle;

		private String theURL;

		private String theLogoURL;

		private String theLogoFormat;

		private int theLogoWidth;

		private int theLogoHeight;

		/** @return This attribution's title */
		public String getTitle()
		{
			return theTitle;
		}

		/** @param title A title for this attribution */
		public void setTitle(String title)
		{
			theTitle = title;
		}

		/** @return This attribution's provider URL */
		public String getURL()
		{
			return theURL;
		}

		/** @param url A provider URL for this attribution */
		public void setURL(String url)
		{
			theURL = url;
		}

		/** @return The URL for this attribution's logo image */
		public String getLogoURL()
		{
			return theLogoURL;
		}

		/** @return The format for this attribution's logo image */
		public String getLogoFormat()
		{
			return theLogoFormat;
		}

		/** @return The width of this attribution's logo image */
		public int getLogoWidth()
		{
			return theLogoWidth;
		}

		/** @return The height of this attribution's logo image */
		public int getLogoHeight()
		{
			return theLogoHeight;
		}

		/**
		 * Sets a logo for this attribution
		 * 
		 * @param url The URL of the logo image
		 * @param format The format of the image
		 * @param width The width of the logo
		 * @param height The height of the logo
		 */
		public void setLogo(String url, String format, int width, int height)
		{
			theLogoURL = url;
			theLogoFormat = format;
			theLogoWidth = width;
			theLogoHeight = height;
		}

		/**
		 * Prints this attribution to capability request output
		 * 
		 * @param ret The string builder to print this contact to
		 * @param indent The number of places to indent this item's output
		 */
		public void toXML(StringBuilder ret, int indent)
		{
			StringBuilder indentStr = new StringBuilder();
			for(int i = 0; i < indent; i++)
				indentStr.append('\t');
			ret.append(indentStr).append("<Attribution>\n");
			if(theTitle != null)
				ret.append(indentStr).append("\t<Title>").append(escape(theTitle))
					.append("</Title>\n");
			if(theURL != null)
				ret.append(indentStr)
					.append("\t<OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
					.append(" xlink:type=\"simple\" xlink:href=\"").append(escape(theURL))
					.append("\"/>\n");
			if(theLogoURL != null)
			{
				ret.append(indentStr).append("\t<LogoURL width=\"").append(theLogoWidth)
					.append("\" height=\"").append(theLogoHeight).append("\">\n");
				ret.append(indentStr).append("\t<Format>").append(theLogoFormat)
					.append("</Format>\n");
				ret.append(indentStr)
					.append("\t\t<OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
					.append(" xlink:type=\"simple\" xlink:href=\"").append(escape(theLogoURL))
					.append("\"/>\n");
				ret.append(indentStr).append("\t</LogoURL>\n");
			}
			ret.append(indentStr).append("</Attribution>\n");
		}

		/**
		 * Fills this attribution's information with an Attribution element in a Layer element from
		 * a WMS Capabilities XML document
		 * 
		 * @param el An Attribution element in a Layer element from a WMS Capabilities XML document
		 */
		public void parse(Element el)
		{
			theTitle = el.elementTextTrim("Title");
			Element url = el.element("OnlineResource");
			theURL = url == null ? null : url.attributeValue("href");
			Element logoUrl = el.element("LogoURL");
			if(logoUrl != null)
			{
				theLogoURL = logoUrl.element("OnlineResource").attributeValue("href");
				theLogoFormat = logoUrl.elementTextTrim("Format");
				theLogoWidth = Integer.parseInt(logoUrl.attributeValue("width"));
				theLogoHeight = Integer.parseInt(logoUrl.attributeValue("width"));
			}
			else
			{
				theLogoURL = null;
				theLogoFormat = null;
				theLogoWidth = 0;
				theLogoHeight = 0;
			}
		}
	}

	private String theVersion;

	private String theTitle;

	private String theAbstract;

	private String [] theKeywords;

	private String theLocation;

	private Contact theContact;

	private String theFees;

	private String theAccessConstraints;

	private int theLayerLimit;

	private int theMaxWidth;

	private int theMaxHeight;

	private OperationType theCapabilitiesOp;

	private OperationType theMapOp;

	private OperationType theFeatureInfoOp;

	private OperationType [] theExtraOps;

	private String [] theExceptionFormats;

	private Element [] theExtendedCapabilities;

	private Layer [] theLayers;

	/** Creates a WMS Capabilities document */
	public WmsCapabilities()
	{
		theVersion = "1.1.1";
		theKeywords = new String [0];
		theCapabilitiesOp = new OperationType("GetCapabilities");
		theMapOp = new OperationType("GetMap");
		theFeatureInfoOp = new OperationType("GetFeatureInfo");
		theExtraOps = new OperationType [0];
		theExtendedCapabilities = new Element [0];
		theExceptionFormats = new String [0];
		theLayers = new Layer [0];
	}

	/** @return The version of WMS that the server supports */
	public String getVersion()
	{
		return theVersion;
	}

	/** @param version The version of WMS that the server supports */
	public void setVersion(String version)
	{
		theVersion = version;
	}

	/** @return The title of this capabilities document */
	public String getTitle()
	{
		return theTitle;
	}

	/** @param title The title for this capabilities document */
	public void setTitle(String title)
	{
		theTitle = title;
	}

	/** @return The abstract or description of the WMS server's capabilities */
	public String getAbstract()
	{
		return theAbstract;
	}

	/** @param anAbstract The abstract or description for the WMS server's capabilities */
	public void setAbstract(String anAbstract)
	{
		theAbstract = anAbstract;
	}

	/** @return The URL location of the WMS server */
	public String getLocation()
	{
		return theLocation;
	}

	/** @param location The URL location of the WMS server */
	public void setLocation(String location)
	{
		theLocation = location;
	}

	/** @return The contact information for this capabilities document */
	public Contact getContact()
	{
		return theContact;
	}

	/** @param contact The contact information for this capabilities document */
	public void setContact(Contact contact)
	{
		theContact = contact;
	}

	/** @return The fees for using the WMS server */
	public String getFees()
	{
		return theFees;
	}

	/** @param fees The fees for using the WMS server */
	public void setFees(String fees)
	{
		theFees = fees;
	}

	/** @return The max number of layers that can be requested at once from the WMS server */
	public int getLayerLimit()
	{
		return theLayerLimit;
	}

	/** @param layerLimit The max number of layers that can be requested at once from the WMS server */
	public void setLayerLimit(int layerLimit)
	{
		theLayerLimit = layerLimit;
	}

	/** @return The maximum width of images that will be served */
	public int getMaxWidth()
	{
		return theMaxWidth;
	}

	/** @param maxWidth The maximum width of images that will be served */
	public void setMaxWidth(int maxWidth)
	{
		theMaxWidth = maxWidth;
	}

	/** @return The maximum height of images that will be served */
	public int getMaxHeight()
	{
		return theMaxHeight;
	}

	/** @param maxHeight The maximum height of images that will be served */
	public void setMaxHeight(int maxHeight)
	{
		theMaxHeight = maxHeight;
	}

	/** @return The operation representing a GetCapabilities request */
	public OperationType getCapabilitiesOp()
	{
		return theCapabilitiesOp;
	}

	/** @param capabilitiesOp The operation representing a GetCapabilities request */
	public void setCapabilitiesOp(OperationType capabilitiesOp)
	{
		theCapabilitiesOp = capabilitiesOp;
	}

	/** @return The operation representing a GetMap request */
	public OperationType getMapOp()
	{
		return theMapOp;
	}

	/** @param mapOp The operation representing a GetMap request */
	public void setMapOp(OperationType mapOp)
	{
		theMapOp = mapOp;
	}

	/** @return The operation representing a GetFeatureInfo request */
	public OperationType getFeatureInfoOp()
	{
		return theFeatureInfoOp;
	}

	/** @param featureInfoOp The operation representing a GetFeatureInfo request */
	public void setFeatureInfoType(OperationType featureInfoOp)
	{
		theFeatureInfoOp = featureInfoOp;
	}

	/** @return Extra operation types that the WMS server supports */
	public OperationType [] getExtraOps()
	{
		return theExtraOps;
	}

	/** @param op The extra operation type to add */
	public void addExtraOp(OperationType op)
	{
		if(op.getName().equals("GetCapabilities") || op.getName().equals("GetMap")
			|| op.getName().equals("GetFeatureInfo"))
			throw new IllegalArgumentException(op.getName()
				+ " is a reserved operation type--cannot be duplicated");
		theExtraOps = ArrayUtils.add(theExtraOps, op);
	}

	/** @param op The extra operation type to remove */
	public void removeExtraOp(OperationType op)
	{
		theExtraOps = ArrayUtils.remove(theExtraOps, op);
	}

	/** @return Keywords chosen by the vendor to make the WMS server easier to find */
	public String [] getKeywords()
	{
		return theKeywords;
	}

	/** @param keyword The keyword to add to the WMS server */
	public void addKeyword(String keyword)
	{
		if(!ArrayUtils.contains(theKeywords, keyword))
			theKeywords = ArrayUtils.add(theKeywords, keyword);
	}

	/** @param keyword The keyword to remove the WMS server */
	public void removeKeyword(String keyword)
	{
		theKeywords = ArrayUtils.remove(theKeywords, keyword);
	}

	/** @return Other reasons for which access may be denied to the WMS server */
	public String getAccessConstraints()
	{
		return theAccessConstraints;
	}

	/** @param ac Other reasons for which access may be denied to the WMS server */
	public void setAccessConstraints(String ac)
	{
		theAccessConstraints = ac;
	}

	/** @return All formats of error returning that are supported by the WMS server */
	public String [] getExceptionFormats()
	{
		return theExceptionFormats;
	}

	/** @param f The exception format that the WMS server can support */
	public void addExceptionFormat(String f)
	{
		theExceptionFormats = ArrayUtils.add(theExceptionFormats, f);
	}

	/** @param f The exception format that the WMS server cannot support */
	public void removeExceptionFormat(String f)
	{
		theExceptionFormats = ArrayUtils.remove(theExceptionFormats, f);
	}

	/** @return All extra functionality that the WMS server supports beyond the standard */
	public Element [] getExtendedCapabilities()
	{
		return theExtendedCapabilities;
	}

	/** @param ec The extended capability to add for the WMS server */
	public void addExtendedCapability(Element ec)
	{
		theExtendedCapabilities = ArrayUtils.add(theExtendedCapabilities, ec);
	}

	/** @param ec The extended capability to remove for the WMS server */
	public void removeExtendedCapability(Element ec)
	{
		theExtendedCapabilities = ArrayUtils.remove(theExtendedCapabilities, ec);
	}

	/** @return The layers available from the WMS server */
	public Layer [] getLayers()
	{
		return theLayers;
	}

	/** @param layer The layer to add for the WMS server */
	public void addLayer(Layer layer)
	{
		theLayers = ArrayUtils.add(theLayers, layer);
	}

	/** @param layer The layer to remove for the WMS server */
	public void removeLayer(Layer layer)
	{
		theLayers = ArrayUtils.remove(theLayers, layer);
	}

	/** @return An XML-formatted string representing this WMS capabilities document */
	public String toXML()
	{
		StringBuilder ret = new StringBuilder();

		ret.append("<WMT_MS_Capabilities version=\"").append(theVersion).append("\">\n");
		ret.append("\t<Service>\n");
		ret.append("\t\t<Name>OGC:WMS</Name>\n");
		ret.append("\t\t<Title>").append(escape(theTitle)).append("</Title>\n");
		if(theAbstract != null)
			ret.append("\t\t<Abstract>").append(escape(theAbstract)).append("</Abstract>\n");
		if(theKeywords.length > 0)
		{
			ret.append("\t\t<KeywordList>\n");
			for(String keyword : theKeywords)
				ret.append("\t\t\t<Keyword>").append(escape(keyword)).append("</Keyword>\n");
			ret.append("\t\t</KeywordList>\n");
		}
		if(theLocation != null)
			ret.append("\t\t<OnlineResource xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
				.append(" xlink:type=\"simple\" xlink:href=\"").append(escape(theLocation))
				.append("\"/>\n");
		if(theContact != null)
			theContact.toXML(ret, 2);
		if(theFees != null)
			ret.append("\t\t<Fees>").append(escape(theFees)).append("</Fees>\n");
		else
			ret.append("\t\t<Fees>NONE</Fees>");
		if(theAccessConstraints != null)
			ret.append("\t\t<AccessConstraints>").append(escape(theAccessConstraints))
				.append("</AccessConstraints>\n");
		else
			ret.append("\t\t<AccessConstraints>NONE</AccessConstraints>");
		if(theLayerLimit > 0)
			ret.append("\t\t<LayerLimit>").append(theLayerLimit).append("</LayerLimit>\n");
		else
			ret.append("\t\t<LayerLimit />\n");
		if(theMaxWidth > 0)
			ret.append("\t\t<MaxWidth>").append(theMaxWidth).append("</MaxWidth>\n");
		else
			ret.append("\t\t<MaxWidth />\n");
		if(theMaxHeight > 0)
			ret.append("\t\t<MaxHeight>").append(theMaxHeight).append("</MaxHeight>\n");
		else
			ret.append("\t\t<MaxHeight />\n");
		ret.append("\t</Service>\n");

		ret.append("\t<Capability>\n");
		ret.append("\t<Request>\n");
		theCapabilitiesOp.toXML(ret, 2);
		theMapOp.toXML(ret, 2);
		theFeatureInfoOp.toXML(ret, 2);
		for(OperationType extra : theExtraOps)
			extra.toXML(ret, 2);
		ret.append("\t</Request>\n");

		ret.append("\t<Exception>\n");
		for(String f : theExceptionFormats)
			ret.append("\t\t<Format>").append(escape(f)).append("</Format>\n");
		ret.append("\t</Exception>\n");

		for(Element ex : theExtendedCapabilities)
			ret.append(ex.asXML()).append('\n');

		for(Layer layer : theLayers)
			layer.toXML(ret, 1);

		ret.append("\t</Capability>\n");
		ret.append("</WMT_MS_Capabilities>\n");
		return ret.toString();
	}

	/**
	 * Fills in this capabilities document's content with content from XML
	 * 
	 * @param el The XML-formatted WMS capabilities document
	 */
	public void parse(Element el)
	{
		if(el.attributeValue("version") != null)
			theVersion = el.attributeValue("version");
		Element svc = el.element("Service");
		theTitle = get(svc, "Title", theTitle);
		theAbstract = get(svc, "Abstract", theAbstract);
		Element keywords = svc.element("KeywordList");
		if(keywords != null)
		{
			java.util.List<Element> kes = keywords.elements("Keyword");
			theKeywords = new String [kes.size()];
			for(int k = 0; k < theKeywords.length; k++)
				theKeywords[k] = kes.get(k).getTextTrim();
		}
		Element onlineResource = svc.element("OnlineResource");
		if(onlineResource != null)
			theLocation = onlineResource.attributeValue("href");
		Element contactEl = svc.element("ContactInformation");
		if(contactEl == null)
			theContact = null;
		else
		{
			Element person = contactEl.element("ContactPersonPrimary");
			if(theContact == null)
				theContact = new Contact(person.elementTextTrim("ContactPerson"),
					person.elementTextTrim("ContactOrganization"));
			theContact.parse(contactEl);
		}
		String fees = svc.element("Fees") == null ? null : svc.elementTextTrim("Fees");
		if(fees.equalsIgnoreCase("none"))
			theFees = null;
		else
			theFees = fees;
		String ac = svc.element("AccessConstraints") == null ? null : svc
			.elementTextTrim("AccessConstraints");
		if(ac.equalsIgnoreCase("none"))
			theAccessConstraints = null;
		else
			theAccessConstraints = ac;
		theLayerLimit = svc.elementTextTrim("LayerLimit") == null ? 0 : Integer.parseInt(svc
			.elementTextTrim("LayerLimit"));
		theMaxWidth = svc.elementTextTrim("MaxWidth") == null ? 0 : Integer.parseInt(svc
			.elementTextTrim("MaxWidth"));
		theMaxHeight = svc.elementTextTrim("MaxHeight") == null ? 0 : Integer.parseInt(svc
			.elementTextTrim("MaxHeight"));

		Element cap = el.element("Capability");
		Element req = cap.element("Request");
		theCapabilitiesOp.parse(req.element("GetCapabilities"));
		theMapOp.parse(req.element("GetMap"));
		if(req.element("GetFeatureInfo") == null)
			theFeatureInfoOp.clear();
		else
			theFeatureInfoOp.parse(req.element("GetFeatureInfo"));
		List<Element> extras = new java.util.ArrayList<Element>();
		for(Element opEl : (List<Element>) req.elements())
		{
			if(opEl.getName().equals("GetCapabilities"))
			{}
			else if(opEl.getName().equals("GetMap"))
			{}
			else if(opEl.getName().equals("GetFeatureInfo"))
			{}
			else
				extras.add(opEl);
		}
		theExtraOps = ArrayUtils.adjust(theExtraOps, extras.toArray(new Element [extras.size()]),
			new ArrayUtils.DifferenceListener<OperationType, Element>()
			{
				public boolean identity(OperationType o1, Element o2)
				{
					return o1.getName().equals(o2.getName());
				}

				public OperationType added(Element o, int mIdx, int retIdx)
				{
					OperationType ret = new OperationType(o.getName());
					ret.parse(o);
					return ret;
				}

				public OperationType removed(OperationType o, int oIdx, int incMod, int retIdx)
				{
					return null;
				}

				public OperationType set(OperationType o1, int idx1, int incMod, Element o2,
					int idx2, int retIdx)
				{
					o1.parse(o2);
					return o1;
				}
			});

		theExceptionFormats = new String [0];
		theExtendedCapabilities = new Element [0];
		List<Element> layerEls = new java.util.ArrayList<Element>();
		for(Element exEl : (List<Element>) cap.elements())
		{
			if(exEl.getName().equals("Request"))
			{}
			else if(exEl.getName().equals("Exception"))
				theExceptionFormats = ArrayUtils.add(theExceptionFormats,
					exEl.elementTextTrim("Format"));
			else if(exEl.getName().equals("Layer"))
				layerEls.add(exEl);
			else
				theExtendedCapabilities = ArrayUtils.add(theExtendedCapabilities, exEl);
		}
		theLayers = ArrayUtils.adjust(theLayers, layerEls.toArray(new Element [layerEls.size()]),
			new ArrayUtils.DifferenceListener<Layer, Element>()
			{
				public boolean identity(Layer o1, Element o2)
				{
					return o1.getName().equals(o2.elementTextTrim("Name"));
				}

				public Layer added(Element o, int mIdx, int retIdx)
				{
					Layer ret = new Layer();
					ret.parse(o);
					return ret;
				}

				public Layer removed(Layer o, int oIdx, int incMod, int retIdx)
				{
					return null;
				}

				public Layer set(Layer o1, int idx1, int incMod, Element o2, int idx2, int retIdx)
				{
					o1.parse(o2);
					return o1;
				}
			});
	}

	static String get(Element el, String elName, String def)
	{
		String ret = el.elementTextTrim(elName);
		if(ret == null)
			ret = def;
		return ret;
	}

	/**
	 * Parses a capabilities request from a character stream
	 * 
	 * @param reader The reader to parse the capabilities request from
	 * @return The parsed capabilities request
	 * @throws org.dom4j.DocumentException If the stream could not be parsed into an XML document
	 * @throws prisms.arch.PrismsException If the XML document could not be parsed into a
	 *         capabilities document
	 */
	public static WmsCapabilities parse(java.io.Reader reader) throws org.dom4j.DocumentException,
		prisms.arch.PrismsException
	{
		Element root = new org.dom4j.io.SAXReader().read(reader).getRootElement();
		WmsCapabilities ret = new WmsCapabilities();
		try
		{
			ret.parse(root);
		} catch(RuntimeException e)
		{
			throw new prisms.arch.PrismsException("Could not parse capabilities document", e);
		}
		return ret;
	}

	static String escape(String str)
	{
		boolean needsEscape = false;
		for(int c = 0; !needsEscape && c < str.length(); c++)
		{
			char ch = str.charAt(c);
			if(ch > 0x7f)
				needsEscape = true;
			if(ch == '<' || ch == '>' || ch == '&' || ch == '"' || ch == '\'')
				needsEscape = true;
		}
		if(!needsEscape)
			return str;
		StringBuilder ret = new StringBuilder();
		for(int c = 0; !needsEscape && c < str.length(); c++)
		{
			char ch = str.charAt(c);
			if(ch > 0x7f)
				unicode(ch, ret);
			else if(ch == '<')
				ret.append("&lt;");
			else if(ch == '>')
				ret.append("&gt;");
			else if(ch == '&')
				ret.append("&amp;");
			else if(ch == '"')
				ret.append("&quot;");
			else if(ch == '\'')
				ret.append("&apos;");
			else
				ret.append(ch);
		}
		return ret.toString();
	}

	static void unicode(char ch, StringBuilder ret)
	{
		ret.append("&#x");
		if(ch < 0x100)
			ret.append('0');
		if(ch < 0x1000)
			ret.append('0');
		ret.append(Integer.toHexString(ch));
		ret.append(';');
	}
}
