/*
 * Searchable.java Created Feb 22, 2011 by Andrew Butler, PSL
 */
package prisms.util;

/**
 * A Searchable API is an API for retrieving a certain type of item that allows the item to be
 * searched for using a custom implementation of {@link Search}.
 * 
 * @param <T> The type of item that this API retrieves
 * @param <F> The type of field that may be sorted by with this API
 * @param <E> The type of exception that this API can throw
 */
public interface SearchableAPI<T, F extends Sorter.Field, E extends Exception>
{
	/**
	 * A prepared search is a search that has been prepared for quick execution.
	 * 
	 * @param <F> The type of sorter field that this search's API uses
	 */
	public interface PreparedSearch<F extends Sorter.Field>
	{
		/** @return The template search that this was prepared for */
		Search getSearch();

		/** @return The sorter that this was prepared for */
		Sorter<F> getSorter();

		/** @return The number of parameters in this prepared search */
		int getParameterCount();

		/**
		 * @param paramIdx The index of the parameter to get the type for
		 * @return The type of the parameter at the given index
		 */
		Class<?> getParameterType(int paramIdx);

		/**
		 * @param paramIdx the index of the parameter to get the parent search for
		 * @return The search that is missing the parameter at the given index that must be supplied
		 *         when the search is executed
		 */
		Search getParentSearch(int paramIdx);
	}

	/**
	 * Executes a search for items within this API's data source
	 * 
	 * @param search The search to execute
	 * @param sorter The sorter to use to sort the results returned
	 * @return The IDs of all items that match the given search
	 * @throws E If the search is invalid or fails for any other reason
	 */
	long [] search(Search search, Sorter<F> sorter) throws E;

	/**
	 * Prepares a search for quick execution. Usage of this method rather than
	 * {@link #search(Search, Sorter)}, when a particular type of search is used frequently, can
	 * potentially save a great deal of time and resources as the API has the opportunity to
	 * optimize the query for the cost of some one-time setup processing.
	 * 
	 * @param search The template search to prepare for. Some of the fields in the search may be
	 *        left blank to be supplied as parameters when the search is executed.
	 * @param sorter The sorter to be used to sort the results returned from
	 *        {@link #execute(PreparedSearch, Object...)}
	 * @return The prepared search for quick execution
	 * @throws E If the search is invalid or the preparation fails for any other reason
	 */
	PreparedSearch<F> prepare(Search search, Sorter<F> sorter) throws E;

	/**
	 * Executes a prepared search for items within this API's data source
	 * 
	 * @param search The prepared search to execute
	 * @param params The parameters to supply to fill in the template parameters that were not
	 *        specified for {@link #prepare(Search, Sorter)}. The parameters must be supplied in
	 *        order as the template parameters are encountered during a depth-first, left-to-right
	 *        search
	 * @return The IDs of all items that match the given search
	 * @throws E If the search fails for any reason
	 */
	long [] execute(PreparedSearch<F> search, Object... params) throws E;

	/**
	 * Releases the resources held by a prepared search. This method should always be called when a
	 * caller is finished using a particular search.
	 * 
	 * @param search The search to release
	 * @throws E If an error occurs releasing the resources
	 */
	void destroy(PreparedSearch<F> search) throws E;

	/**
	 * Gets the items from the data source whose IDs are given
	 * 
	 * @param ids The IDs of the items to get
	 * @return The items in the data source whose IDs are given, in order of the IDs given. If an ID
	 *         is given twice, the item returned twice. If an ID does not match an item, the return
	 *         array will be null at that index.
	 * @throws E If an error occurs getting the data
	 */
	T [] getItems(long... ids) throws E;
}
