# High-Level PRISMS Overview #

The PRISMS framework was created to make from-scratch development of flexible, **stateful** remote-based applications (e.g. web services and web applications) easier.  Any such remote-based application can benefit from PRISMS integration.  PRISMS can be used as a remote interface to code that is already written, but applications benefit most from complete PRISMS integration due to its integrated data management and messaging system.  It is best to consider using PRISMS at the beginning of the development cycle as good integration later may mean significant rewriting and redesigning.


PRISMS excels at flexible, stateful remote applications due to the following features:
  * An application framework conducive to automated data setup
  * An user framework that allows for information hiding based on identity and permissions
  * Several tools to ease information sharing between user sessions

# PRISMS Application Model #
The PRISMS framework is structured in an access hierarchy:
  * The PRISMS Server instantiates an implementation of a user framework as well as any number of applications configured.  It manages these applications and sessions within them.  It also manages authentication and encryption between itself and its clients.
  * A PRISMS application represents a set of functionality.  Data can be shared within an application and between applications.
  * A PRISMS session represents a single client's interaction with an application.  Mechanisms exist to share and pass data between sessions within an application.
  * A PRISMS plugin represents a subset of the functionality and state of a client's session within an application.  A plugin typically represents a widget or small group of widgets on a client's user interface.  All customized remote communication between clients and PRISMS applications is done by implementations of prisms.arch.AppPlugin.  Mechanisms exist for plugins to communicate with each other within a session, between sessions within an application, and with other applications on the server.


# PRISMS Data Model #

The PRISMS framework is designed to be run in a simple servlet container and does not use any Java EE or other third party framework utilities.  In their place, PRISMS provides lightweight substitutes to some of the utilities, such as Java EE topics and queues.

**Properties:** Properties are values accessible to all plugins within a session at all times.  Properties represent those pieces of the application's state that are relevant to more than one plugin.  The value of a property may retrieved at any time.  Changes to properties may be restricted by customized managers.

When a property is changed, a prisms.arch.event.PrismsPCE (property change event) is fired to alert interested plugins and other listeners that the value is changed.  Thus if a plugin determines that a property's value needs to be modified, it simply sets the new value.  Listeners that have been registered by other plugins will alert them to take appropriate action.  Thus the plugin initiating the change does not need to know of every consequence of a property change.

Properties can be session-specific, having independent representations in each session, or they can be application-wide so that all sessions within that application see the same data and property change listeners are broadcast between sessions. Properties may also be shared between applications. Properties can also be shared in more intelligent ways, so that each session sees a subset of the application value according to the session's user.  Any type of property management within or between session is possible.

PRISMS property values are typed to reduce unnecessary type casting and ClassCastExceptions.

**Events:** Events represent messages that may pass between plugins and other listeners.  An event is like a method call on an unknown target.  From the caller plugin's perspective, the event is fired and forgotten.  Plugins and other listeners who care about the named event register listeners within prisms to take appropriate actions when the event occurs.

# Flow #
### Following is the typical flow of control for a user's interaction with a PRISMS web application. Many different kinds of clients and variations on this flow are possible. ###

  1. The user navigates to the page for the web application, typically a plain HTML file or a JSP.
  1. As the web page is loaded, it makes a call to the PRISMS server. The server loads itself (if needed) and the web application and client configuration sent by the web client.
    * The application is loaded with the different kinds of data needed for the application's operation and listeners to keep that data in a consistent state.
    * The client is loaded with data needed for the particular client's view of the application data and listeners needed to keep this session-specific data consistent. It also contains a set of plugins that the client web app communicates with directly in order to represent or manipulate session and application data.
  1. The server then checks its authentication settings and returns a set of data that the client can use to disguise a password in such a way that the password can be recognized as correct by the server, but cannot be derived in clear-text by any means.
    * Other means of authentication besides username/password can be configured, especially single-sign-on within a secure environment.
    * It is assumed that the correct user name was sent on first contact
  1. The client prompts the user for their password. The client uses the server-sent data to hash the user's password and send it to the server.
  1. The server verifies that the data sent to it was created by hashing the correct password. The server creates a new session using the application's data and client configuration's plugins.  The server calls an initiation method on each plugin in the new session. These plugins generate events that give the client the information it needs to represent the initial state of the web app, each event addressed with the name of the plugin. These events are sent back to the client.
  1. The client distributes the events to client plugins by name, each of which is responsible for a small set of widgets on the application.
  1. The user performs an action on the client, such as clicking a button or an item in a list. This causes the client plugin responsible for that widget to make a call to the PRISMS server with an event detailing the action the user took. This even also is addressed with the name of the plugin.
  1. The PRISMS server delegates the event to the appropriate plugin in the session, calling the plugin's processEvent method. The plugin takes an action based on the user's action, which might affect an application-wide property. When the property is modified, it causes a property change event, which is listened for within a different plugin. That plugin acts on this change by modifying its internal data representation and posting an event or set of events for the client that reflect these internal changes.
    * Note that the plugin whose processEvent was called does not need to know about the plugin listening to that property. If a third plugin is added that represents the same property in a different way, neither of the other two plugins need to be modified at all.
  1. The client version of the second plugin uses these events to modify the user's view of the data

This flow illustrates the benefits of the PRISMS architecture:
  * **Data centric:** Representations are of memory objects easily available within the session. Modifications are done to the data and then forgotten. Property change listeners registered from other parts of the application's configuration do all the work.
  * **Modularity:** The different pieces of the program do not need to know of or refer to one another, making addition of new pieces or removal or modification of old pieces much easier.