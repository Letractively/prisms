/*
 * MessageChangeTypes.java Created Jan 6, 2011 by Andrew Butler, PSL
 */
package prisms.message;

/** Changes that can happen to a message or its metadata */
public class MessageChangeTypes
{
	/** Changes that can occur to a message */
	public enum MessageChange implements prisms.records.ChangeType
	{
		/** A change to a message's time. This only happens when the message is sent. */
		time(null, Long.class, false),
		/** Change to the sent status of a message */
		sent(null, Boolean.class, false),
		/** Change to a message's priority */
		priority(null, Message.Priority.class, false),
		/** Change to a message's subject */
		subject(null, String.class, false),
		/** Change of a message's predecessor */
		predecessor(null, Message.class, true),
		/** Change to whether a message overrides its predecessor */
		override(null, Boolean.class, false),
		/** Change to a message's content */
		content(null, String.class, false);

		private final Class<?> theMinorType;

		private final Class<?> theObjectType;

		private final boolean isIdentifiable;

		MessageChange(Class<?> minorType, Class<?> objectType, boolean id)
		{
			theMinorType = minorType;
			theObjectType = objectType;
			isIdentifiable = id;
		}

		public Class<?> getMinorType()
		{
			return theMinorType;
		}

		public Class<?> getObjectType()
		{
			return theObjectType;
		}

		public boolean isObjectIdentifiable()
		{
			return isIdentifiable;
		}

		public String toString(int additivity)
		{
			switch(this)
			{
			case time:
				if(additivity != 0)
					return null;
				return "Message Sent Time Changed";
			case sent:
				if(additivity != 0)
					return null;
				return "Message Sent";
			case priority:
				if(additivity != 0)
					return null;
				return "Message Priority Changed";
			case subject:
				if(additivity != 0)
					return null;
				return "Message Subject Changed";
			case predecessor:
				if(additivity != 0)
					return null;
				return "Message Predecessor Changed";
			case override:
				if(additivity != 0)
					return null;
				return "Message Override Changed";
			case content:
				if(additivity != 0)
					return null;
				return "Message Content Changed";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject)
		{
			switch(this)
			{
			case time:
				if(additivity != 0)
					return null;
				return "Sent time of message " + ((Message) majorSubject).getSubject() + " changed";
			case sent:
				if(additivity != 0)
					return null;
				return "Message " + ((Message) majorSubject).getSubject() + " sent";
			case priority:
				if(additivity != 0)
					return null;
				return "Priority of message " + ((Message) majorSubject).getSubject() + " changed";
			case subject:
				if(additivity != 0)
					return null;
				return "Subject of message " + ((Message) majorSubject).getSubject() + " changed";
			case predecessor:
				if(additivity != 0)
					return null;
				return "Predecessor of message " + ((Message) majorSubject).getSubject()
					+ " changed";
			case override:
				if(additivity != 0)
					return null;
				return "Override of message " + ((Message) majorSubject).getSubject() + " changed";
			case content:
				if(additivity != 0)
					return null;
				return "Content of message " + ((Message) majorSubject).getSubject() + " changed";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject,
			Object before, Object after)
		{
			switch(this)
			{
			case time:
				if(additivity != 0)
					return null;
				return "Sent time of message " + ((Message) majorSubject).getSubject()
					+ " changed to " + prisms.util.PrismsUtils.print(((Long) after).longValue());
			case sent:
				if(additivity != 0)
					return null;
				return "Message " + ((Message) majorSubject).getSubject() + " sent";
			case priority:
				if(additivity != 0)
					return null;
				return "Priority of message " + ((Message) majorSubject).getSubject()
					+ " changed from " + before + " to " + after;
			case subject:
				if(additivity != 0)
					return null;
				return "Subject of message " + ((Message) majorSubject).getSubject()
					+ " changed from " + before + " to " + after;
			case predecessor:
				if(additivity != 0)
					return null;
				return "Predecessor of message " + ((Message) majorSubject).getSubject()
					+ " changed to " + ((Message) after).getSubject();
			case override:
				if(additivity != 0)
					return null;
				return "Override of message " + ((Message) majorSubject).getSubject()
					+ " changed from " + before + " to " + after;
			case content:
				if(additivity != 0)
					return null;
				return "Content of message " + ((Message) majorSubject).getSubject()
					+ " changed from " + ((String) before).length() + " to "
					+ ((String) after).length() + " characters";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}
	}

	/** Changes that can occur to a recipient of a message */
	public enum RecipientChange implements prisms.records.ChangeType
	{
		/** Change to a recepient's applicability (TO:, CC:, BCC:) */
		applicability(null, Recipient.Applicability.class, false),
		/** Changed when a user first receives and views a message */
		firstViewed(null, Long.class, false),
		/** Changed every time a user views a message */
		lastViewed(null, Long.class, false);

		private final Class<?> theMinorType;

		private final Class<?> theObjectType;

		private final boolean isIdentifiable;

		RecipientChange(Class<?> minorType, Class<?> objectType, boolean id)
		{
			theMinorType = minorType;
			theObjectType = objectType;
			isIdentifiable = id;
		}

		public Class<?> getMinorType()
		{
			return theMinorType;
		}

		public Class<?> getObjectType()
		{
			return theObjectType;
		}

		public boolean isObjectIdentifiable()
		{
			return isIdentifiable;
		}

		public String toString(int additivity)
		{
			switch(this)
			{
			case applicability:
				if(additivity != 0)
					return null;
				return "Recipient Applicability Changed";
			case firstViewed:
				if(additivity != 0)
					return null;
				return "Message Received";
			case lastViewed:
				if(additivity != 0)
					return null;
				return "Message Viewed";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject)
		{
			Recipient receipt = (Recipient) majorSubject;
			switch(this)
			{
			case applicability:
				if(additivity != 0)
					return null;
				return "Applicability of " + majorSubject + " to message "
					+ receipt.getMessage().getSubject() + " changed";
			case firstViewed:
				if(additivity != 0)
					return null;
				return "Message " + receipt.getMessage().getSubject() + " received by "
					+ receipt.getUser();
			case lastViewed:
				if(additivity != 0)
					return null;
				return "Message " + receipt.getMessage().getSubject() + " viewed by "
					+ receipt.getUser();
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject,
			Object before, Object after)
		{
			Recipient receipt = (Recipient) majorSubject;
			switch(this)
			{
			case applicability:
				if(additivity != 0)
					return null;
				return "Applicability of " + majorSubject + " to message "
					+ receipt.getMessage().getSubject() + " changed from " + before + " to "
					+ after;
			case firstViewed:
				if(additivity != 0)
					return null;
				return "Message " + receipt.getMessage().getSubject() + " received by "
					+ receipt.getUser() + " at "
					+ prisms.util.PrismsUtils.print(((Long) after).longValue());
			case lastViewed:
				if(additivity != 0)
					return null;
				return "Message " + receipt.getMessage().getSubject() + " viewed by "
					+ receipt.getUser() + " at "
					+ prisms.util.PrismsUtils.print(((Long) after).longValue());
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}
	}

	/** Changes to an attachment of a message */
	public enum AttachmentChange implements prisms.records.ChangeType
	{
		/** Change to an attachment's name */
		name(null, String.class, false);

		private final Class<?> theMinorType;

		private final Class<?> theObjectType;

		private final boolean isIdentifiable;

		AttachmentChange(Class<?> minorType, Class<?> objectType, boolean id)
		{
			theMinorType = minorType;
			theObjectType = objectType;
			isIdentifiable = id;
		}

		public Class<?> getMinorType()
		{
			return theMinorType;
		}

		public Class<?> getObjectType()
		{
			return theObjectType;
		}

		public boolean isObjectIdentifiable()
		{
			return isIdentifiable;
		}

		public String toString(int additivity)
		{
			switch(this)
			{
			case name:
				return "Name changed";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject)
		{
			switch(this)
			{
			case name:
				return "Name of attachment " + majorSubject + " changed";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject,
			Object before, Object after)
		{
			switch(this)
			{
			case name:
				return "Name of attachment " + majorSubject + " changed from " + before + " to "
					+ after;
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}
	}

	/** Changes to an attachment of a message */
	public enum ActionChange implements prisms.records.ChangeType
	{
		/** Change to an action's description */
		descrip(null, String.class, false);

		private final Class<?> theMinorType;

		private final Class<?> theObjectType;

		private final boolean isIdentifiable;

		ActionChange(Class<?> minorType, Class<?> objectType, boolean id)
		{
			theMinorType = minorType;
			theObjectType = objectType;
			isIdentifiable = id;
		}

		public Class<?> getMinorType()
		{
			return theMinorType;
		}

		public Class<?> getObjectType()
		{
			return theObjectType;
		}

		public boolean isObjectIdentifiable()
		{
			return isIdentifiable;
		}

		public String toString(int additivity)
		{
			switch(this)
			{
			case descrip:
				return "Descrip changed";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject)
		{
			switch(this)
			{
			case descrip:
				return "Descrip of action " + majorSubject + " changed";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject,
			Object before, Object after)
		{
			switch(this)
			{
			case descrip:
				return "Descrip of action " + majorSubject + " changed from " + before + " to "
					+ after;
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}
	}

	/** Changes that may be made to a user's view of a conversation */
	public enum ConversationViewChange implements prisms.records.ChangeType
	{
		/** Changed when a user archives a message or moves it back to the inbox */
		archived(null, Boolean.class, false),
		/** Changed when a user marks a message as starred or unstarred */
		starred(null, Boolean.class, false);

		private final Class<?> theMinorType;

		private final Class<?> theObjectType;

		private final boolean isIdentifiable;

		ConversationViewChange(Class<?> minorType, Class<?> objectType, boolean id)
		{
			theMinorType = minorType;
			theObjectType = objectType;
			isIdentifiable = id;
		}

		public Class<?> getMinorType()
		{
			return theMinorType;
		}

		public Class<?> getObjectType()
		{
			return theObjectType;
		}

		public boolean isObjectIdentifiable()
		{
			return isIdentifiable;
		}

		public String toString(int additivity)
		{
			switch(this)
			{
			case archived:
				if(additivity != 0)
					return null;
				return "Message Archived";
			case starred:
				if(additivity != 0)
					return null;
				return "Message Starred";
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject)
		{
			ConversationView view = (ConversationView) majorSubject;
			switch(this)
			{
			case archived:
				if(additivity != 0)
					return null;
				return "Conversation " + view + " (un)archived by " + view.getViewer();
			case starred:
				if(additivity != 0)
					return null;
				return "Conversation " + view + " (un)starred by " + view.getViewer();
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}

		public String toString(int additivity, Object majorSubject, Object minorSubject,
			Object before, Object after)
		{
			ConversationView view = (ConversationView) majorSubject;
			switch(this)
			{
			case archived:
				if(additivity != 0)
					return null;
				return "Message " + view + (((Boolean) after).booleanValue() ? "" : "un")
					+ " archived by " + view.getViewer();
			case starred:
				if(additivity != 0)
					return null;
				return "Message " + view + (((Boolean) after).booleanValue() ? "" : "un")
					+ " starred by " + view.getViewer();
			}
			throw new IllegalStateException("Unrecognized change type " + name());
		}
	}
}
