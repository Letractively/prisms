/*
 * CenterInspector.java Created Jul 25, 2011 by Andrew Butler, PSL
 */
package prisms.records;

import java.awt.Color;

import prisms.ui.list.NodeAction;

/** Allows inspection of PRISMS centers from the manager */
public class CenterInspector implements prisms.arch.event.DataInspector
{
	private prisms.arch.event.PrismsProperty<?> theProperty;

	private prisms.util.InspectorUtils theUtils;

	public void configure(prisms.arch.event.PrismsProperty<?> property,
		prisms.arch.PrismsConfig pclConfig, prisms.arch.PrismsConfig inspectorConfig)
	{
		theProperty = property;
		theUtils = new prisms.util.InspectorUtils(property, pclConfig, inspectorConfig);
	}

	public String getPropertyIcon(InspectSession session, NodeController node)
	{
		return "prisms/center";
	}

	public String getPropertyDescrip(InspectSession session, NodeController node)
	{
		return "All data centers for syncing " + node.getApp() + " data";
	}

	public Color getPropertyBackground(InspectSession session, NodeController node)
	{
		return Color.white;
	}

	public Color getPropertyForeground(InspectSession session, NodeController node)
	{
		return Color.black;
	}

	public NodeAction [] getPropertyActions(InspectSession session, NodeController node)
	{
		return new NodeAction [0];
	}

	public void performPropertyAction(InspectSession session, NodeController node, String action)
	{
		throw new IllegalStateException("Unrecognized " + theProperty + " action " + action);
	}

	public String getText(InspectSession session, NodeController node)
	{
		return node.getValue().toString();
	}

	public String getDescrip(InspectSession session, NodeController node)
	{
		if(node.getValue() instanceof PrismsCenter)
		{
			PrismsCenter center = (PrismsCenter) node.getValue();
			StringBuilder ret = new StringBuilder();
			ret.append("ID:");
			if(center.getCenterID() < 0)
				ret.append("unknown");
			else
				ret.append(Integer.toHexString(center.getCenterID()));
			ret.append("\n            ");
			if(center.getServerURL() != null)
			{
				ret.append("URL:").append(center.getServerURL());
				ret.append("\n            ");
				if(center.getServerUserName() != null)
				{
					ret.append("As:").append(center.getServerUserName());
					ret.append("\n            ");
				}
			}
			if(center.getClientUser() != null)
			{
				ret.append("Connect As:").append(center.getClientUser().getName());
				ret.append("\n            ");
			}
			ret.append("Last Successful Export:");
			if(center.getLastExport() >= 0)
				ret.append(prisms.util.PrismsUtils.print(center.getLastExport()));
			else
				ret.append("never");
			ret.append("\n            ");
			ret.append("Last Successful Import:");
			if(center.getLastExport() >= 0)
				ret.append(prisms.util.PrismsUtils.print(center.getLastImport()));
			else
				ret.append("never");
			return ret.toString();
		}
		else
			return null;
	}

	public String getIcon(InspectSession session, NodeController node)
	{
		return "prisms/center";
	}

	public Color getBackground(InspectSession session, NodeController node)
	{
		return Color.white;
	}

	public Color getForeground(InspectSession session, NodeController node)
	{
		return Color.black;
	}

	public NodeAction [] getActions(InspectSession session, NodeController node)
	{
		return new NodeAction [0];
	}

	public void performAction(InspectSession session, NodeController node, String action)
	{
		throw new IllegalStateException("Unrecognized " + theProperty + " node action " + action);
	}

	public String canDescend(InspectSession session, NodeController node, boolean allDescendants)
	{
		return null;
	}

	public String getHideLabel(InspectSession session, NodeController node)
	{
		return null;
	}

	public ItemMetadata getMetadata(NodeController node)
	{
		return null;
	}

	public Object [] getChildren(NodeController node)
	{
		return new Object [0];
	}

	public void registerSessionListener(prisms.arch.PrismsSession session, ChangeListener cl)
	{
		theUtils.registerSessionListener(session, cl);
	}

	public void deregisterSessionListener(ChangeListener cl)
	{
		theUtils.deregisterSessionListener(cl);
	}
}
