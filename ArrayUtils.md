# An overview of the utilities in prisms.util.ArrayUtils #

The ArrayUtils class contains several utility methods useful for manipulating arrays. These included methods to:
  * Add an element or several elements to an array
  * Remove an element or all instances of an element from an array
  * Concatenate several arrays into a single array
  * Merge multiple arrays:
    * including only elements common to all arrays
    * excluding duplicates present in more than one array
  * Search an array for an element
  * Sort an array interactively--performing custom operations each time the given list is reordered. This can be used to sort multiple synchronized lists with a single operation.

However, the most important and useful method in ArrayUtils is:
# adjust #

The **adjust** method is one of the most useful and innovative utilities in the PRISMS architecture.  Its function is to adjust the content of one list using the content of another.  The way this is done and the effects of the operation are completely customizable.  This method compares two arrays, determining elements that are missing in either array or have been moved from one position to another.  These differences are reported to a listener, giving the programmer the opportunity to take any action as a result of each difference.

This method allows programmers to perform some very complicated operations on one-dimensional sets of data using a few very simple and easy to understand methods.  As an example:

Say there is a persistent (in a database or otherwise) property in an application that is an array of people.  Here is our Person class:

```
public class Person
{
    /**
     * The persons identifier--two Person objects represent the same person if and only if
     * the values of this ID in both Person objects are identical.
     */
    public final int theID;
    /**
     * The name of the person--how they are represented in the user interface
     */
    public String theName;
    /**
     * If true, this item can be deleted by setting the persons property to an array
     * value without this element.  If false, this person cannot be deleted.
     */
    public final boolean isDeletable;
}
```

The persistence of the item can be managed with 3 methods: `addPersistedPerson(Person p)`, `removePersistedPerson(Person p)`, and `updatePersistedPerson(Person p)`

In addition, suppose the Person set is represented by a graphical list in a user interface.  Items can be added to the list at a particular index using a method `addPersonToUI(Person person, int index)`.  Items can be removed using `removePersonFromUI(int index)`.  If a person already represented in the UI is modified, this change can be communicated to the graphical list by `updatePersonInUI(int index)`.

Then suppose at some place in the code, a new set of people is passed in to become the new value of the application property.  We can't just set the new property--we have to update the persistence.  In addition note, the isDeletable property in the Person object--some elements cannot be deleted even if they are absent from the new value.  One option is to make sure all non-deletable Persons are in the new array, then clear the persistence and re-write all the new items.  But this is unnecessary.  We can simply use the ArrayUtils.adjust method to update the application state as follows:

```
Person [] appValue=getOldValue();
Person [] newValue=getNewValue();
appValue=ArrayUtils.adjust(appValue, newValue, new ArrayUtils.DifferenceListener<Person, Person>
    {
        /**
         * Compares two persons for identity
         * 
         * @param o1 The person from the appValue
         * @param o2 The person from the newValue
         */
        public boolean identity(Person o1, Person o2)
        {
            return o1.theID==o2.theID;
        }

        /**
         * Called when a value is found in newValue that was not present in appValue
         * 
         * @param o The person from newValue
         * @param mIdx The element's index in the original newValue array
         * @param retIdx The index in the final Person array
         */
        public Person added(Person o, int mIdx, int retIdx)
        {
            addPersistedPerson(o);
            addPersonToUI(o, retIdx);
            return o;
        }

        /**
         * Called when a value is found in appValuethat was not present in newValue
         * 
         * @param o The person from appValue
         * @param oIdx The element's index in the original appValuearray
         * @param incMod The index in the UI array, updated for previous removals,
         *     additions, and index moves
         * @param retIdx The index in the final Person array
         */
        public Person removed(Person o, int oIdx, int incMod, int retIdx)
        {
            if(o.isDeletable)
            {
                removePersistedPerson(o);
                removePersonFromUI(incMod);
                return null; //Signifies that the value should not be in the final property
            }
            else
            {
                //Don't remove the person from UI or persistence
                return o; //Signifies that the value should be in the final property
            }
        }

        /**
         * Called when a value is found in both arrays
         * 
         * @param o1 The person from appValue
         * @param oIdx The element's index in the original appValuearray
         * @param o2 The person from newValue
         * @param mIdx The element's index in the original newValue array
         * @param incMod The index in the UI array, updated for previous removals,
         *     additions, and index moves
         * @param retIdx The index in the final Person array
         */
        public Person set(Person o1, int oIdx, Person o2, int mIdx, int incMod, int retIdx)
        {
            if(!o1.theName.equals(o2.theName))
            {
                o1.theName=o2.theName;
                updatePersistedPerson(o1);
                updatePersonInUI(incMod);
            }
            return o1;
        }
    }
);
setApplicationProperty(appValue);
```

Thus the entire application's state can be updated in only 25 or so easy-to-understand lines of actual code.

In addition to the `removed` method, null can be returned from the `added` and `set` methods as well to signify that the value should not be represented in the returned array or the incMod and retIdx indices.

Also, although this example compared two arrays of `Person`s, the method is capable of comparing arrays of any two non-primitive types whose relationship is defined by the identity method.  Say then that the UI in this example is a list with a poorly-designed data model, and the only way to change the data is to set its items in an array.  Here is the UI list's item class:

```
public class ListItem
{
    public ListItem(Object content)
    {...}
    ...
    /**
     * Gets the custom data object represented by this list item
     */
    public Object getContent()
    {...}
    ...
}
```

So the `getContent()` method returns a `Person` for our usage and the only way to set the content is to create a new `ListItem` with a person in the constructor.  The UI list itself contains `ListItem [] getListItems()` and `void setListItems(ListItem[])` methods for getting and setting its data, respectively.  After updating the set of Persons in the application's value, one could use the adjust method to update the UI as follows:

```
uiList.setListItems(ArrayUtils.adjust(uiList.getListItems(), appValue,
    new ArrayUtils.DifferenceListener<ListItem, Person>()
    {
        public boolean identity(ListItem o1, Person o2)
        {
            return ((Person) o1.getContent()).theID==o2.theID;
        }

        public ListItem added(Person o, int mIdx, int retIdx)
        {
            return new ListItem(o); //Add the item
        }

        public ListItem removed(ListItem o, int oIdx, int incMod, int retIdx)
        {
            return null; //Remove the item
        }

        public ListItem set(ListItem o1, int oIdx, Person o2, int mIdx, int incMod, int retIdx)
        {
            return o1; //Keep the original list item
        }
    }
));
```

Using the adjust method, we have updated the list despite the horrible programmer interface.  The adjust method is also very efficient, enabling us to create as few new `ListItem` objects as possible, while re-using those items whose content is still valid.  It also preserves order from the one of the arrays (the first if reorder operations (calls to `set` where `incMod!=retIdx`) are not followed, the second if they are) in the returned array (and parameter indices).  So if the appropriate argument is sorted and any extra elements that the other argument contains are ignored, the result (the returned array or a UI list, etc.) will be sorted correctly.